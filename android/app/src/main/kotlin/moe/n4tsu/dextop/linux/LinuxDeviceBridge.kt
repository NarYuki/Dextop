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
 *   "S <device>": a USB serial port run here, framed data / line settings / DTR-RTS;
 *   "H <device>": a security key's FIDO reports (CTAP HID), framed data, for /dev/hidrawN.
 * Only this app's UID (the Android mode's Linux) and, in the full privilege mode, the shell
 * user that runs Linux may connect; the socket name is random and only Linux reads it.
 */
internal object LinuxDeviceBridge {
    private const val TAG = "DextopLinuxDevices"
    private const val FRAME_DATA = 0
    private const val FRAME_CONFIG = 1
    private const val FRAME_CONTROL = 2
    private const val FRAME_BREAK = 3

    @Volatile private var socketName = "dextop-devices-" + UUID.randomUUID().toString().replace("-", "")
    private var server: LocalServerSocket? = null
    @Volatile private var listenerService: android.os.IBinder? = null
    @Volatile private var started: Context? = null
    private val socketOwners = java.util.concurrent.ConcurrentHashMap<LocalSocket, ParcelFileDescriptor>()

    private fun closeSocket(socket: LocalSocket) {
        runCatching { socket.close() }
        runCatching { socketOwners.remove(socket)?.close() }
    }

    fun start(context: Context) {
        if (started != null) return
        val app = context.applicationContext
        started = app
        // Another device may get a departed one's name: security keys are read again.
        LinuxUsb.onChange { hidKeys.clear() }
        val initial = LocalServerSocket(socketName)
        server = initial
        accept(app, initial)
        // Android may have changed the picked folders while Dextop was not running.
        Thread({ runCatching { LinuxFolders.syncAll(app) } }, "DextopLinuxFoldersSync").apply { isDaemon = true; start() }
    }

    private fun accept(app: Context, server: LocalServerSocket) {
        Thread({
            while (true) {
                val socket = try { server.accept() } catch (_: Exception) { break }
                val uid = try { socket.peerCredentials.uid } catch (_: Exception) {
                    runCatching { socket.close() }; continue
                }
                // Other shell-user processes (adb, Shizuku clients) may find the socket too; in the full
                // privilege mode only processes of Dextop's own Linux service are served.
                val allowed = uid == android.os.Process.myUid() || (uid == 2000 && !LinuxExecutionMode.android(app) &&
                    runCatching { LinuxContainerClient.service()?.ownsProcess(socket.peerCredentials.pid) }.getOrNull() == true)
                if (!allowed) { runCatching { socket.close() }; continue }
                Thread({ runCatching { serve(app, socket, uid) }.onFailure { Log.w(TAG, "Device request failed", it) }
                    runCatching { socket.close() } }, "DextopLinuxDevice").apply { isDaemon = true; start() }
            }
        }, "DextopLinuxDevicesAccept").apply { isDaemon = true; start() }
    }

    @Synchronized private fun attachListener(app: Context, linux: ILinuxContainerService) {
        if (LinuxContainerClient.service()?.asBinder() != linux.asBinder()) return
        if (listenerService == linux.asBinder()) return
        val name = "dextop-devices-" + UUID.randomUUID().toString().replace("-", "")
        val androidMode = LinuxExecutionMode.android(app)
        val replacement = if (androidMode) LocalServerSocket(name) else null
        if (!androidMode) linux.startDeviceBridgeListener(name)
        val previous = server
        server = replacement
        listenerService = linux.asBinder()
        if (replacement != null) accept(app, replacement) else acceptFromService(app, linux)
        socketName = name
        runCatching { previous?.close() }
    }

    /** Retire the old generation before its service exits; in-flight accepts discard their FD. */
    @Synchronized fun disconnect() {
        listenerService = null
        runCatching { server?.close() }
        server = null
        socketOwners.keys.toList().forEach(::closeSocket)
    }

    private fun acceptFromService(app: Context, linux: ILinuxContainerService) {
        Thread({
            while (listenerService == linux.asBinder()) {
                val descriptor: ParcelFileDescriptor? = try { linux.acceptDeviceBridge() } catch (error: Exception) {
                    Log.w(TAG, "Linux device accept stopped", error); break
                }
                // A remote runtime can disappear while its synchronous accept is returning.
                if (descriptor == null) break
                if (listenerService != linux.asBinder()) {
                    runCatching { descriptor.close() }; break
                }
                val socket = try {
                    org.lsposed.hiddenapibypass.HiddenApiBypass.newInstance(LocalSocket::class.java, descriptor.fileDescriptor) as LocalSocket
                } catch (error: Exception) { runCatching { descriptor.close() }; Log.w(TAG, "Cannot adopt Linux device connection", error); continue }
                synchronized(this) {
                    if (listenerService == linux.asBinder()) socketOwners[socket] = descriptor
                }
                if (socketOwners[socket] !== descriptor) {
                    runCatching { socket.close() }; runCatching { descriptor.close() }; break
                }
                // The service checked SO_PEERCRED and process ancestry before passing this FD.
                Thread({ runCatching { serve(app, socket, socket.peerCredentials.uid) }
                    .onFailure { Log.w(TAG, "Device request failed", it) }
                    // Also releases externally supplied descriptors, including on USB disconnect.
                    closeSocket(socket)
                }, "DextopLinuxDevice").apply { isDaemon = true; start() }
            }
        }, "DextopLinuxDeviceBinderAccept").apply { isDaemon = true; start() }
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
        // Security keys, numbered as hidraw nodes; LinuxHidSysfs lists them for udev.
        var hidraw = 0
        manager(context)?.deviceList.orEmpty().values.sortedBy { it.deviceName }.forEach { device ->
            if (!LinuxUsb.enabled(context, device) || manager(context)?.hasPermission(device) != true) return@forEach
            val key = hidKey(context, device) ?: return@forEach
            val name = listOfNotNull(device.manufacturerName, device.productName).joinToString(" ").ifBlank { "Security key" }
            append("hid hidraw${hidraw++} ${device.deviceName} %04x %04x ".format(device.vendorId, device.productId))
            append(key.descriptor.joinToString("") { "%02x".format(it) })
            append(' ').append(android.util.Base64.encodeToString(name.toByteArray(), android.util.Base64.NO_WRAP)).append('\n')
        }
    }

    /** A security key's FIDO interface and its HID report descriptor. */
    private class HidKey(val interfaceIndex: Int, val descriptor: ByteArray)

    /** Read once per plugged-in device (by Android device name); empty when it is no security key. */
    private val hidKeys = java.util.concurrent.ConcurrentHashMap<String, java.util.Optional<HidKey>>()

    /**
     * The FIDO interface of [device]: a HID interface whose report descriptor has the FIDO
     * Alliance usage page (0xF1D0), read from the device itself.
     */
    private fun hidKey(context: Context, device: UsbDevice): HidKey? {
        hidKeys[device.deviceName]?.let { return it.orElse(null) }
        // Only a security key maker's FIDO interface is ever claimed: claiming takes the interface
        // from Android's own HID driver, which would take a mouse or keyboard away from Android.
        val fido = LinuxUsb.fidoInterface(device)
        val candidates = (0 until device.interfaceCount).filter { index -> device.getInterface(index) == fido }
        if (candidates.isEmpty()) {
            hidKeys[device.deviceName] = java.util.Optional.empty()
            return null
        }
        val connection = manager(context)?.openDevice(device) ?: run {
            Log.w(TAG, "Security key ${device.deviceName}: cannot open the device")
            return null
        }
        val index = candidates.first()
        val usb = device.getInterface(index)
        try {
            // Android's own HID driver may hold it; Dextop takes it over while Linux uses the key.
            // A failure (busy while a relay holds it) is not remembered: the next look tries again.
            if (!connection.claimInterface(usb, true)) {
                Log.w(TAG, "Security key ${device.deviceName}: interface ${usb.id} is busy")
                return null
            }
            try {
                val buffer = ByteArray(1024)
                // GET_DESCRIPTOR (report) from the interface.
                val n = connection.controlTransfer(0x81, 0x06, 0x2200, usb.id, buffer, buffer.size, 1000)
                if (n <= 0) {
                    Log.w(TAG, "Security key ${device.deviceName}: report descriptor unreadable ($n)")
                    return null
                }
                val descriptor = buffer.copyOf(n)
                val found = descriptor.takeIf(::fidoUsagePage)?.let { HidKey(index, it) }
                Log.i(TAG, "Security key ${device.deviceName}: descriptor $n bytes, FIDO=${found != null}")
                hidKeys[device.deviceName] = java.util.Optional.ofNullable(found)
                return found
            } finally {
                connection.releaseInterface(usb)
            }
        } finally {
            connection.close()
        }
    }

    /** Whether a report descriptor declares the FIDO usage page (Usage Page item 0x06 0xD0 0xF1). */
    private fun fidoUsagePage(descriptor: ByteArray): Boolean = (0 until descriptor.size - 2).any {
        descriptor[it] == 0x06.toByte() && descriptor[it + 1] == 0xd0.toByte() && descriptor[it + 2] == 0xf1.toByte()
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
                    attachListener(app, linux)
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
                            .onFailure { Log.w(TAG, "Cannot publish Linux device state", it) }
                    }
                }.onFailure { Log.w(TAG, "Cannot prepare Linux device bridge", it) }
                try { Thread.sleep(3000) } catch (_: InterruptedException) { return@Thread }
            }
        }, "DextopLinuxDevicesPublish").apply { isDaemon = true; start() }
    }

    private fun readLine(input: DataInputStream, socket: LocalSocket): String {
        val bytes = java.io.ByteArrayOutputStream()
        val deadline = android.os.SystemClock.elapsedRealtime() + 5000
        val poll = android.system.StructPollfd().apply {
            fd = socket.fileDescriptor
            events = android.system.OsConstants.POLLIN.toShort()
        }
        while (bytes.size() < 8192) {
            val remaining = (deadline - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0).toInt()
            if (remaining == 0 || android.system.Os.poll(arrayOf(poll), remaining) == 0)
                throw java.net.SocketTimeoutException("Device handshake timed out")
            val b = input.read()
            if (b < 0 || b == '\n'.code) break
            bytes.write(b)
        }
        return bytes.toString(Charsets.UTF_8.name())
    }

    private fun serve(context: Context, socket: LocalSocket, uid: Int) {
        val input = DataInputStream(socket.inputStream)
        // A Binder-passed shell socket can be read/written by the app, but SELinux
        // denies setsockopt. Bound the handshake with poll instead of SO_RCVTIMEO.
        val request = readLine(input, socket)
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
            request.startsWith("H ") -> hid(context, socket, input, device)
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
            runCatching { socket.inputStream.read() }
        } finally {
            connection.close()
        }
    }

    /** A security key's reports: from Linux to its OUT endpoint (or SET_REPORT), and from its IN endpoint back. */
    private fun hid(context: Context, socket: LocalSocket, input: DataInputStream, device: UsbDevice) {
        val key = hidKey(context, device) ?: run { socket.outputStream.write(1); return }
        val connection = manager(context)?.openDevice(device) ?: run { socket.outputStream.write(1); return }
        val usb = device.getInterface(key.interfaceIndex)
        try {
            if (!connection.claimInterface(usb, true)) { socket.outputStream.write(1); return }
            val endpoints = (0 until usb.endpointCount).map(usb::getEndpoint)
                .filter { it.type == android.hardware.usb.UsbConstants.USB_ENDPOINT_XFER_INT }
            val inbound = endpoints.firstOrNull { it.direction == android.hardware.usb.UsbConstants.USB_DIR_IN }
                ?: run { socket.outputStream.write(1); return }
            val outbound = endpoints.firstOrNull { it.direction == android.hardware.usb.UsbConstants.USB_DIR_OUT }
            socket.outputStream.write(0)
            val output = socket.outputStream
            val open = java.util.concurrent.atomic.AtomicBoolean(true)
            val reader = Thread({
                val buffer = ByteArray(inbound.maxPacketSize.coerceAtLeast(64))
                try {
                    while (open.get()) {
                        val n = connection.bulkTransfer(inbound, buffer, buffer.size, 500)
                        if (n > 0) synchronized(output) {
                            output.write(byteArrayOf(FRAME_DATA.toByte(), (n and 0xff).toByte(), (n shr 8).toByte()))
                            output.write(buffer, 0, n)
                            output.flush()
                        }
                    }
                } catch (_: Exception) {
                    closeSocket(socket)
                }
            }, "DextopLinuxHidRead").apply { isDaemon = true; start() }
            try {
                while (true) {
                    val type = input.read()
                    if (type < 0) break
                    val length = input.readUnsignedByte() or (input.readUnsignedByte() shl 8)
                    val payload = ByteArray(length).also(input::readFully)
                    if (type != FRAME_DATA) continue
                    if (outbound != null) {
                        // Reports are of the endpoint's size; CTAP HID pads short ones with zeros.
                        val report = payload.copyOf(maxOf(payload.size, outbound.maxPacketSize))
                        connection.bulkTransfer(outbound, report, report.size, 2000)
                    } else {
                        // SET_REPORT (output) on the control pipe.
                        connection.controlTransfer(0x21, 0x09, 0x0200, usb.id, payload, payload.size, 2000)
                    }
                }
            } finally {
                open.set(false)
                reader.join(1000)
            }
        } finally {
            runCatching { connection.releaseInterface(usb) }
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
                    closeSocket(socket)
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
