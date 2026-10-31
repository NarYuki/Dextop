package moe.n4tsu.dextop.linux

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.Image
import android.media.ImageReader
import android.net.LocalSocket
import android.os.Handler
import android.os.HandlerThread
import android.util.Range
import java.io.DataInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Android cameras for Linux (linux_android_camera.c presents them as /dev/videoN): Camera2
 * records while a Linux application streams, and each frame it asks for goes out as YUYV.
 */
internal object LinuxCameraBridge {
    private val SIZES = listOf(1280 to 720, 640 to 480, 320 to 240)

    private fun manager(context: Context) = context.getSystemService(CameraManager::class.java)

    /** "camera video0 <id> <sizes> <label>" lines: front cameras first, as webcams. */
    fun state(context: Context): String = buildString {
        if (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return@buildString
        val manager = manager(context) ?: return@buildString
        val ids = runCatching { manager.cameraIdList.toList() }.getOrDefault(emptyList())
        val ordered = ids.sortedBy {
            if (runCatching { manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) }.getOrNull() ==
                CameraCharacteristics.LENS_FACING_FRONT) 0 else 1
        }
        ordered.forEachIndexed { index, id ->
            val characteristics = runCatching { manager.getCameraCharacteristics(id) }.getOrNull() ?: return@forEachIndexed
            val available = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?.getOutputSizes(ImageFormat.YUV_420_888).orEmpty().map { it.width to it.height }.toSet()
            val sizes = SIZES.filter { it in available }.ifEmpty { return@forEachIndexed }
            val label = when (characteristics.get(CameraCharacteristics.LENS_FACING)) {
                CameraCharacteristics.LENS_FACING_FRONT -> "Android Front Camera"
                CameraCharacteristics.LENS_FACING_BACK -> "Android Back Camera"
                else -> "Android Camera $id"
            }
            append("camera video$index $id ${sizes.joinToString(",") { "${it.first}x${it.second}" }} $label\n")
        }
    }

    private class Stream(val width: Int, val height: Int) {
        val lock = Object()
        var frame: ByteArray? = null
        var sequence = 0L
        var camera: CameraDevice? = null
        var session: CameraCaptureSession? = null
        var reader: ImageReader? = null
        val thread = HandlerThread("DextopLinuxCamera").apply { start() }
        fun close() {
            runCatching { session?.close() }
            runCatching { camera?.close() }
            runCatching { reader?.close() }
            thread.quitSafely()
        }
    }

    /** Serves one opened /dev/videoN: "S w h" starts, "N" asks for a frame, "E" stops. */
    fun serve(context: Context, socket: LocalSocket, input: DataInputStream, id: String) {
        val manager = manager(context) ?: run { socket.outputStream.write(1); return }
        if (id !in runCatching { manager.cameraIdList.toList() }.getOrDefault(emptyList())) return
        socket.soTimeout = 0
        var stream: Stream? = null
        var sent = 0L
        try {
            while (true) {
                val line = readLine(input) ?: break
                when {
                    line.startsWith("S ") -> {
                        val (w, h) = line.drop(2).split(' ').map { it.toInt() }
                        stream?.close()
                        stream = open(context, manager, id, w, h)
                        sent = 0
                    }
                    line == "N" -> {
                        val current = stream ?: continue
                        val frame = synchronized(current.lock) {
                            val deadline = System.currentTimeMillis() + 3000
                            while (current.sequence == sent && System.currentTimeMillis() < deadline) current.lock.wait(200)
                            sent = current.sequence
                            current.frame
                        } ?: ByteArray(current.width * current.height * 2) { if (it % 2 == 1) 128.toByte() else 0 }
                        val header = byteArrayOf(frame.size.toByte(), (frame.size shr 8).toByte(),
                            (frame.size shr 16).toByte(), (frame.size shr 24).toByte())
                        socket.outputStream.write(header)
                        socket.outputStream.write(frame)
                    }
                    line == "E" -> { stream?.close(); stream = null }
                }
            }
        } finally {
            stream?.close()
        }
    }

    private fun readLine(input: DataInputStream): String? {
        val bytes = java.io.ByteArrayOutputStream()
        while (bytes.size() < 128) {
            val b = input.read()
            if (b < 0) return null
            if (b == '\n'.code) break
            bytes.write(b)
        }
        return bytes.toString(Charsets.UTF_8.name())
    }

    @SuppressLint("MissingPermission") // Cameras are listed to Linux only with the permission.
    private fun open(context: Context, manager: CameraManager, id: String, width: Int, height: Int): Stream {
        val stream = Stream(width, height)
        val handler = Handler(stream.thread.looper)
        val reader = ImageReader.newInstance(width, height, ImageFormat.YUV_420_888, 3)
        stream.reader = reader
        reader.setOnImageAvailableListener({ source ->
            val image = runCatching { source.acquireLatestImage() }.getOrNull() ?: return@setOnImageAvailableListener
            val yuyv = image.use { toYuyv(it) }
            synchronized(stream.lock) {
                stream.frame = yuyv
                stream.sequence++
                stream.lock.notifyAll()
            }
        }, handler)
        val opened = CountDownLatch(1)
        check(context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) { "No camera permission" }
        manager.openCamera(id, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                stream.camera = camera
                @Suppress("DEPRECATION")
                camera.createCaptureSession(listOf(reader.surface), object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        stream.session = session
                        val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                            addTarget(reader.surface)
                            set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(15, 30))
                        }.build()
                        runCatching { session.setRepeatingRequest(request, null, handler) }
                        opened.countDown()
                    }
                    override fun onConfigureFailed(session: CameraCaptureSession) { opened.countDown() }
                }, handler)
            }
            override fun onDisconnected(camera: CameraDevice) { camera.close(); opened.countDown() }
            override fun onError(camera: CameraDevice, error: Int) { camera.close(); opened.countDown() }
        }, handler)
        opened.await(5, TimeUnit.SECONDS)
        return stream
    }

    /** YUV 4:2:0 from Camera2 to packed YUYV 4:2:2, as V4L2 webcams deliver it. */
    private fun toYuyv(image: Image): ByteArray {
        val width = image.width
        val height = image.height
        val (yPlane, uPlane, vPlane) = image.planes
        val y = yPlane.buffer
        val u = uPlane.buffer
        val v = vPlane.buffer
        val yStride = yPlane.rowStride
        val uvStride = uPlane.rowStride
        val uvPixel = uPlane.pixelStride
        val out = ByteArray(width * height * 2)
        var o = 0
        for (row in 0 until height) {
            val yRow = row * yStride
            val uvRow = (row / 2) * uvStride
            var x = 0
            while (x < width) {
                val uv = uvRow + (x / 2) * uvPixel
                out[o] = y.get(yRow + x)
                out[o + 1] = u.get(uv)
                out[o + 2] = y.get(yRow + x + 1)
                out[o + 3] = v.get(uv)
                o += 4
                x += 2
            }
        }
        return out
    }
}
