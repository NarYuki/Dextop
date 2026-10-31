package moe.n4tsu.dextop.linux

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.os.ParcelFileDescriptor
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import java.io.DataInputStream
import java.util.UUID

/**
 * Serves the USB devices the user allowed for Linux (Linux settings → Compatibility and
 * permissions) to the guest's preload (linux_android_usb.c):
 *   "U <device>": the device's usbfs descriptor, for libusb and other direct USB users;
 *   "S <device>": a USB serial port run here, framed data / line settings / DTR-RTS.
 * Only this app's UID (the Android mode's Linux) and, in the full privilege mode, the shell
 * user that runs Linux may connect; the socket name is random and only Linux reads it.
 */
internal object LinuxDeviceBridge {
    private const val TAG = "DextopLinuxDevices"
    private const val FRAME_DATA = 0
    private const val FRAME_CONFIG = 1
    private const val FRAME_CONTROL = 2
    private const val FRAME_BREAK = 3

    private val socketName = "dextop-devices-" + UUID.randomUUID().toString().replace("-", "")
    @Volatile private var started: Context? = null

    fun start(context: Context) {
        if (started != null) return
        val app = context.applicationContext
        started = app
        val server = LocalServerSocket(socketName)
        Thread({
            while (true) {
                val socket = try { server.accept() } catch (_: Exception) { break }
                val uid = socket.peerCredentials.uid
                // Other shell-user processes (adb, Shizuku clients) may find the socket too; in the full
                // privilege mode only processes of Dextop's own Linux service are served.
                val allowed = uid == android.os.Process.myUid() || (uid == 2000 && !LinuxExecutionMode.android(app) &&
                    runCatching { LinuxContainerClient.service()?.ownsProcess(socket.peerCredentials.pid) }.getOrNull() == true)
                if (!allowed) { socket.close(); continue }
                Thread({ runCatching { serve(app, socket, uid) }.onFailure { Log.w(TAG, "Device request failed", it) }
                    runCatching { socket.close() } }, "DextopLinuxDevice").apply { isDaemon = true; start() }
            }
        }, "DextopLinuxDevicesAccept").apply { isDaemon = true; start() }
        // Android may have changed the picked folders while Dextop was not running.
        Thread({ runCatching { LinuxFolders.syncAll(app) } }, "DextopLinuxFoldersSync").apply { isDaemon = true; start() }
    }

    private fun manager(context: Context) = context.getSystemService(UsbManager::class.java)

    /** A device Linux may use now: allowed by the user and granted by Android. */
    private fun usable(context: Context, name: String): UsbDevice? {
        val device = manager(context)?.deviceList?.get(name) ?: return null
        return device.takeIf { LinuxUsb.enabled(context, it) && manager(context)?.hasPermission(it) == true }
    }

    /** What Linux reads from /tmp/dextop-devices.conf; ports are named as Linux would name them. */
    fun state(context: Context): String = buildString {
        append("socket $socketName\n")
        if (LinuxFolders.active(context)) append("folders\n")
        append(LinuxCameraBridge.state(context))
        var usb = 0
        var acm = 0
        manager(context)?.deviceList.orEmpty().values.sortedBy { it.deviceName }.forEach { device ->
            if (!LinuxUsb.enabled(context, device) || LinuxUsb.kind(device) != "serial") return@forEach
            val acmPort = UsbSerialProber.getDefaultProber().probeDevice(device)?.javaClass?.simpleName == "CdcAcmSerialDriver"
            val tty = if (acmPort) "ttyACM${acm++}" else "ttyUSB${usb++}"
            append("serial $tty ${device.deviceName}\n")
        }
    }

    @Volatile private var publishing = false

    /**
     * Keeps /tmp/dextop-devices.conf of every installed container current: devices come and go,
     * and a container's /tmp may be emptied when its session starts.
     */
    fun keepPublished(context: Context, service: () -> ILinuxContainerService?) {
        start(context)
        if (publishing) return
        publishing = true
        val app = context.applicationContext
        Thread({
            // Written when it changes, and every 30 seconds in case a session emptied /tmp.
            val written = HashMap<String, Pair<String, Long>>()
            while (true) {
                runCatching {
                    val linux = service() ?: return@runCatching
                    val list = org.json.JSONArray(linux.listContainers())
                    val text = state(app)
                    val now = android.os.SystemClock.elapsedRealtime()
                    for (i in 0 until list.length()) {
                        val item = list.getJSONObject(i)
                        if (item.optString("state") != "installed") continue
                        val id = item.getString("id")
                        // A session starting (it may empty /tmp) changes these too.
                        val key = "$text${item.optBoolean("running")}${item.optBoolean("appsRunning")}${item.optInt("audioGeneration")}"
                        val last = written[id]
                        if (last != null && last.first == key && now - last.second < 30_000) continue
                        runCatching { linux.setGuestState(id, "dextop-devices.conf", text) }
                            .onSuccess { written[id] = key to now }
                    }
                }
                try { Thread.sleep(3000) } catch (_: InterruptedException) { return@Thread }
            }
        }, "DextopLinuxDevicesPublish").apply { isDaemon = true; start() }
    }

    private fun readLine(input: DataInputStream): String {
        val bytes = java.io.ByteArrayOutputStream()
        while (bytes.size() < 8192) {
            val b = input.read()
            if (b < 0 || b == '\n'.code) break
            bytes.write(b)
        }
        return bytes.toString(Charsets.UTF_8.name())
    }

    private fun serve(context: Context, socket: LocalSocket, uid: Int) {
        socket.soTimeout = 5000
        val input = DataInputStream(socket.inputStream)
        val request = readLine(input)
        // Picked folders (LinuxFolders) belong to the Android mode, which runs as this app.
        if (request.startsWith("F ")) {
            if (uid == android.os.Process.myUid()) LinuxFolders.serve(context, socket, request)
            return
        }
        // Cameras are not USB devices; Linux sees each one as /dev/videoN.
        if (request.startsWith("C ")) {
            if (context.checkSelfPermission(android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED)
                LinuxCameraBridge.serve(context, socket, input, request.drop(2))
            return
        }
        val name = request.drop(2)
        val device = usable(context, name)
        when {
            device == null -> socket.outputStream.write(1)
            request.startsWith("U ") -> passthrough(context, socket, device)
            request.startsWith("S ") -> serial(context, socket, input, device)
            else -> socket.outputStream.write(1)
        }
    }

    private fun passthrough(context: Context, socket: LocalSocket, device: UsbDevice) {
        val connection = manager(context)?.openDevice(device) ?: run { socket.outputStream.write(1); return }
        try {
            ParcelFileDescriptor.fromFd(connection.fileDescriptor).use { copy ->
                socket.setFileDescriptorsForSend(arrayOf(copy.fileDescriptor))
                socket.outputStream.write(0)
                socket.outputStream.flush()
                socket.setFileDescriptorsForSend(null)
            }
            // The guest holds its own reference to the device now; wait until it hangs up.
            runCatching { socket.soTimeout = 0; socket.inputStream.read() }
        } finally {
            connection.close()
        }
    }

    private fun serial(context: Context, socket: LocalSocket, input: DataInputStream, device: UsbDevice) {
        val driver = UsbSerialProber.getDefaultProber().probeDevice(device) ?: run { socket.outputStream.write(1); return }
        val connection = manager(context)?.openDevice(device) ?: run { socket.outputStream.write(1); return }
        val port: UsbSerialPort = driver.ports.first()
        try {
            port.open(connection)
            port.setParameters(9600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            runCatching { port.dtr = true; port.rts = true }
            socket.outputStream.write(0)
            socket.soTimeout = 0
            val output = socket.outputStream
            val reader = Thread({
                val buffer = ByteArray(4096)
                try {
                    while (true) {
                        val n = port.read(buffer, 200)
                        if (n > 0) synchronized(output) {
                            output.write(byteArrayOf(FRAME_DATA.toByte(), (n and 0xff).toByte(), (n shr 8).toByte()))
                            output.write(buffer, 0, n)
                            output.flush()
                        }
                    }
                } catch (_: Exception) {
                    runCatching { socket.shutdownInput() }
                }
            }, "DextopLinuxSerialRead").apply { isDaemon = true; start() }
            while (true) {
                val type = input.read()
                if (type < 0) break
                val length = input.readUnsignedByte() or (input.readUnsignedByte() shl 8)
                val payload = ByteArray(length).also(input::readFully)
                when (type) {
                    FRAME_DATA -> port.write(payload, 2000)
                    FRAME_CONFIG -> if (length >= 7) {
                        val baud = (payload[0].toInt() and 0xff) or ((payload[1].toInt() and 0xff) shl 8) or
                            ((payload[2].toInt() and 0xff) shl 16) or ((payload[3].toInt() and 0xff) shl 24)
                        val stop = if (payload[5].toInt() == 2) UsbSerialPort.STOPBITS_2 else UsbSerialPort.STOPBITS_1
                        val parity = when (payload[6].toInt()) { 1 -> UsbSerialPort.PARITY_ODD; 2 -> UsbSerialPort.PARITY_EVEN; else -> UsbSerialPort.PARITY_NONE }
                        runCatching { port.setParameters(baud, payload[4].toInt(), stop, parity) }
                    }
                    FRAME_CONTROL -> if (length >= 2) runCatching {
                        port.dtr = payload[0].toInt() != 0
                        port.rts = payload[1].toInt() != 0
                    }
                    FRAME_BREAK -> runCatching { port.setBreak(true); Thread.sleep(250); port.setBreak(false) }
                }
            }
            reader.interrupt()
        } finally {
            runCatching { port.close() }
            connection.close()
        }
    }
}
