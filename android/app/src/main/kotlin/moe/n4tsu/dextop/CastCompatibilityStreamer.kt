package moe.n4tsu.dextop

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.view.Surface
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.muxer.BufferInfo
import androidx.media3.muxer.FragmentedMp4Muxer
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.Channels
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/**
 * Produces a live fragmented-MP4 stream for the Google Default Media Receiver.
 * The source display is mirrored into a MediaCodec input surface, so casting
 * never captures the phone overlay or requires MediaProjection consent.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class CastCompatibilityStreamer(
    private val context: Context,
    private val privilegedAccess: PrivilegedAccess,
    private val sourceDisplayId: Int,
    sourceWidth: Int,
    sourceHeight: Int,
    private val density: Int
) {
    private val running = AtomicBoolean(false)
    private val executor = Executors.newCachedThreadPool { task ->
        Thread(task, "Dextop-cast-compatibility").apply { isDaemon = true }
    }
    private val outputFile = File(context.cacheDir, "dextop-cast-${System.nanoTime()}.mp4")
    private val backend = DisplayMirrorBackend(
        context,
        context.contentResolver,
        context.getSystemService(DisplayManager::class.java),
        privilegedAccess,
        DesktopEnvironmentRegistry.current()
    )
    private val width: Int
    private val height: Int
    private var codec: MediaCodec? = null
    private var codecSurface: Surface? = null
    private var muxer: FragmentedMp4Muxer? = null
    private var server: ServerSocket? = null

    init {
        // Compatibility receivers buffer fragmented MP4 internally. Keeping
        // the encode size modest lets the decoder catch up instead of growing
        // an ever longer queue on Wi-Fi-constrained Cast devices.
        val maxLong = 1280f
        val scale = (maxLong / maxOf(sourceWidth, sourceHeight).coerceAtLeast(1)).coerceAtMost(1f)
        width = even((sourceWidth * scale).roundToInt()).coerceAtLeast(320)
        height = even((sourceHeight * scale).roundToInt()).coerceAtLeast(240)
    }

    fun start(): String {
        check(running.compareAndSet(false, true)) { "Compatibility stream is already running" }
        val socket = ServerSocket(0).also { server = it }
        executor.execute { acceptClients(socket) }
        startEncoder()
        val address = localIpv4Address() ?: error("No local IPv4 address is available for Google Cast")
        val url = "http://$address:${socket.localPort}/live.mp4"
        OperationLog.i(context, "Cast", "compatibility video stream ready url=$url size=${width}x$height")
        return url
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        runCatching { server?.close() }
        runCatching { backend.releaseLayer() }
        runCatching { codec?.signalEndOfInputStream() }
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        runCatching { codecSurface?.release() }
        runCatching { muxer?.close() }
        executor.shutdownNow()
        outputFile.delete()
        OperationLog.i(context, "Cast", "compatibility video stream stopped")
    }

    private fun startEncoder() {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 6_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            setFloat(MediaFormat.KEY_I_FRAME_INTERVAL, 0.25f)
            setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)
            setInteger(MediaFormat.KEY_PRIORITY, 0)
            setInteger(MediaFormat.KEY_MAX_B_FRAMES, 0)
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                setInteger(MediaFormat.KEY_LATENCY, 0)
            }
        }
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val input = encoder.createInputSurface()
        codec = encoder
        codecSurface = input
        encoder.start()
        backend.attachSurface(
            sourceDisplayId,
            input,
            width,
            height,
            width,
            height,
            density.coerceAtLeast(1)
        )
        executor.execute { drainEncoder(encoder) }
    }

    private fun drainEncoder(encoder: MediaCodec) {
        val nativeInfo = MediaCodec.BufferInfo()
        var trackId = -1
        try {
            while (running.get()) {
                when (val index = encoder.dequeueOutputBuffer(nativeInfo, 10_000)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outputFormat = encoder.outputFormat
                        val initialData = listOfNotNull(
                            outputFormat.getByteBuffer("csd-0")?.let(::copyBuffer),
                            outputFormat.getByteBuffer("csd-1")?.let(::copyBuffer)
                        )
                        val streamMuxer = FragmentedMp4Muxer.Builder(
                            Channels.newChannel(FileOutputStream(outputFile, false))
                        ).setFragmentDurationMs(250).build()
                        trackId = streamMuxer.addTrack(
                            Format.Builder()
                                .setSampleMimeType(MimeTypes.VIDEO_H264)
                                .setWidth(width)
                                .setHeight(height)
                                .setFrameRate(30f)
                                .setInitializationData(initialData)
                                .build()
                        )
                        muxer = streamMuxer
                    }
                    else -> if (index >= 0) {
                        val buffer = encoder.getOutputBuffer(index)
                        if (buffer != null && nativeInfo.size > 0 && trackId >= 0 &&
                            nativeInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            buffer.position(nativeInfo.offset)
                            buffer.limit(nativeInfo.offset + nativeInfo.size)
                            val sample = buffer.slice()
                            var flags = 0
                            if (nativeInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0) {
                                flags = flags or C.BUFFER_FLAG_KEY_FRAME
                            }
                            if (nativeInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                flags = flags or C.BUFFER_FLAG_END_OF_STREAM
                            }
                            muxer?.writeSampleData(
                                trackId,
                                sample,
                                BufferInfo(nativeInfo.presentationTimeUs, nativeInfo.size, flags)
                            )
                        }
                        encoder.releaseOutputBuffer(index, false)
                    }
                }
            }
        } catch (error: Throwable) {
            if (running.get()) OperationLog.e(context, "Cast", "compatibility encoder failed", error)
        }
    }

    private fun acceptClients(socket: ServerSocket) {
        while (running.get()) {
            val client = runCatching { socket.accept() }.getOrNull() ?: break
            executor.execute { serve(client) }
        }
    }

    private fun serve(socket: Socket) = socket.use { client ->
        runCatching {
            client.tcpNoDelay = true
            val input = client.getInputStream().bufferedReader()
            val request = input.readLine().orEmpty()
            while (!input.readLine().isNullOrEmpty()) Unit
            if (!request.contains("/live.mp4")) {
                client.getOutputStream().write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n".toByteArray())
                return@use
            }
            val output = client.getOutputStream().buffered()
            output.write(
                ("HTTP/1.1 200 OK\r\n" +
                    "Content-Type: video/mp4\r\n" +
                    "Transfer-Encoding: chunked\r\n" +
                    "Cache-Control: no-store\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Connection: close\r\n\r\n").toByteArray()
            )
            output.flush()
            var position = 0L
            // Small chunks are intentional: waiting for a 64 KiB block added
            // visible latency before each fMP4 fragment reached the receiver.
            val bytes = ByteArray(8 * 1024)
            RandomAccessFile(outputFile, "r").use { file ->
                while (running.get()) {
                    val available = file.length() - position
                    if (available <= 0) {
                        Thread.sleep(20)
                        continue
                    }
                    file.seek(position)
                    val count = file.read(bytes, 0, minOf(bytes.size.toLong(), available).toInt())
                    if (count <= 0) continue
                    output.write(count.toString(16).toByteArray())
                    output.write("\r\n".toByteArray())
                    output.write(bytes, 0, count)
                    output.write("\r\n".toByteArray())
                    output.flush()
                    position += count
                }
            }
        }.onFailure { error ->
            if (running.get()) OperationLog.w(context, "Cast", "compatibility stream client disconnected", error)
        }
    }

    private fun localIpv4Address(): String? = NetworkInterface.getNetworkInterfaces().toList()
        .asSequence()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.toList().asSequence() }
        .filterIsInstance<Inet4Address>()
        .firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }
        ?.hostAddress

    private fun copyBuffer(source: java.nio.ByteBuffer): ByteArray {
        val copy = source.duplicate()
        val bytes = ByteArray(copy.remaining())
        copy.get(bytes)
        return bytes
    }

    private fun even(value: Int): Int = value and 1.inv()
}
