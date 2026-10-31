package moe.n4tsu.dextop.linux

import android.content.Context
import android.hardware.input.InputManager
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyEvent
import android.view.MotionEvent
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * Controllers for Linux in the Android execution mode, as DroidDeck passes them: an app UID
 * cannot read /dev/input, and Android delivers controller input to the focused window anyway.
 * The events Dextop's Linux windows receive are turned into an Xbox 360 pad's evdev events and
 * streamed to the guest, where libdextop_androidproc / libdextop_padgrab present each stream
 * as /dev/input/event200 and up (see linux_android_pads.c for the guest side).
 */
internal object LinuxAndroidPads {
    private const val FIRST_EVENT = 200
    private const val MAX_PADS = 16
    private const val EV_SYN = 0; private const val EV_KEY = 1; private const val EV_ABS = 3
    private const val ABS_X = 0; private const val ABS_Y = 1; private const val ABS_Z = 2
    private const val ABS_RX = 3; private const val ABS_RY = 4; private const val ABS_RZ = 5
    private const val ABS_HAT0X = 16; private const val ABS_HAT0Y = 17
    private val AXES = intArrayOf(ABS_X, ABS_Y, ABS_Z, ABS_RX, ABS_RY, ABS_RZ, ABS_HAT0X, ABS_HAT0Y)

    private val BUTTONS = mapOf(
        KeyEvent.KEYCODE_BUTTON_A to 0x130, KeyEvent.KEYCODE_BUTTON_B to 0x131,
        KeyEvent.KEYCODE_BUTTON_X to 0x133, KeyEvent.KEYCODE_BUTTON_Y to 0x134,
        KeyEvent.KEYCODE_BUTTON_L1 to 0x136, KeyEvent.KEYCODE_BUTTON_R1 to 0x137,
        KeyEvent.KEYCODE_BUTTON_SELECT to 0x13a, KeyEvent.KEYCODE_BUTTON_START to 0x13b,
        KeyEvent.KEYCODE_BUTTON_MODE to 0x13c, KeyEvent.KEYCODE_BUTTON_THUMBL to 0x13d,
        KeyEvent.KEYCODE_BUTTON_THUMBR to 0x13e,
    )

    private class Client(val socket: LocalSocket, val compact: Boolean)
    private class Pad(val container: String, val index: Int, val deviceId: Int, val name: String) {
        val clients = CopyOnWriteArrayList<Client>()
        val values = IntArray(32)
        val digitalTriggers = BooleanArray(2)
    }

    private val socketName = "dextop-pad-" + UUID.randomUUID().toString().replace("-", "")
    private val pads = ConcurrentHashMap<Pair<String, Int>, Pad>()
    private val cleared = ConcurrentHashMap.newKeySet<String>()
    private val writer = Executors.newSingleThreadExecutor { Thread(it, "LinuxAndroidPads").apply { isDaemon = true } }
    @Volatile private var started: Context? = null

    private fun isPad(device: InputDevice) = !device.isVirtual &&
        (device.supportsSource(InputDevice.SOURCE_GAMEPAD) || device.supportsSource(InputDevice.SOURCE_JOYSTICK))

    /** Sends [event] to Linux [container] when it comes from a controller; true when it was taken. */
    fun dispatch(context: Context, container: String, event: InputEvent): Boolean {
        if (!LinuxExecutionMode.android(context)) return false
        val device = event.device ?: return false
        if (!isPad(device)) return false
        when (event) {
            is KeyEvent -> {
                val code = event.keyCode
                val dpad = code in KeyEvent.KEYCODE_DPAD_UP..KeyEvent.KEYCODE_DPAD_RIGHT
                if (code !in BUTTONS && !dpad && code != KeyEvent.KEYCODE_BUTTON_L2 && code != KeyEvent.KEYCODE_BUTTON_R2) return false
                if (event.action == KeyEvent.ACTION_MULTIPLE || event.repeatCount > 0) return true
                val pad = ensure(context, container.removeSuffix("#apps"), device)
                val down = event.action == KeyEvent.ACTION_DOWN
                val changes = ArrayList<IntArray>()
                when {
                    code in BUTTONS -> changes += intArrayOf(EV_KEY, BUTTONS.getValue(code), if (down) 1 else 0)
                    code == KeyEvent.KEYCODE_BUTTON_L2 || code == KeyEvent.KEYCODE_BUTTON_R2 -> {
                        // Pads without analog triggers press them as buttons.
                        val left = code == KeyEvent.KEYCODE_BUTTON_L2
                        pad.digitalTriggers[if (left) 0 else 1] = true
                        changes += axis(pad, if (left) ABS_Z else ABS_RZ, if (down) 255 else 0)
                    }
                    else -> {
                        val horizontal = code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT
                        val value = if (!down) 0 else if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_UP) -1 else 1
                        changes += axis(pad, if (horizontal) ABS_HAT0X else ABS_HAT0Y, value)
                    }
                }
                send(pad, changes)
                return true
            }
            is MotionEvent -> {
                if (!event.isFromSource(InputDevice.SOURCE_JOYSTICK) || event.actionMasked != MotionEvent.ACTION_MOVE) return false
                val pad = ensure(context, container.removeSuffix("#apps"), device)
                fun stick(value: Float) = (value.coerceIn(-1f, 1f) * 32767).roundToInt()
                fun trigger(a: Int, b: Int) = (maxOf(event.getAxisValue(a), event.getAxisValue(b)).coerceIn(0f, 1f) * 255).roundToInt()
                val changes = ArrayList<IntArray>()
                changes += axis(pad, ABS_X, stick(event.getAxisValue(MotionEvent.AXIS_X)))
                changes += axis(pad, ABS_Y, stick(event.getAxisValue(MotionEvent.AXIS_Y)))
                changes += axis(pad, ABS_RX, stick(event.getAxisValue(MotionEvent.AXIS_Z)))
                changes += axis(pad, ABS_RY, stick(event.getAxisValue(MotionEvent.AXIS_RZ)))
                if (!pad.digitalTriggers[0]) changes += axis(pad, ABS_Z, trigger(MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_BRAKE))
                if (!pad.digitalTriggers[1]) changes += axis(pad, ABS_RZ, trigger(MotionEvent.AXIS_RTRIGGER, MotionEvent.AXIS_GAS))
                if (device.getMotionRange(MotionEvent.AXIS_HAT_X) != null) {
                    changes += axis(pad, ABS_HAT0X, event.getAxisValue(MotionEvent.AXIS_HAT_X).roundToInt().coerceIn(-1, 1))
                    changes += axis(pad, ABS_HAT0Y, event.getAxisValue(MotionEvent.AXIS_HAT_Y).roundToInt().coerceIn(-1, 1))
                }
                send(pad, changes.filter { it.isNotEmpty() })
                return true
            }
        }
        return false
    }

    /** An axis change, or nothing when [value] is the one already sent. */
    private fun axis(pad: Pad, code: Int, value: Int): IntArray {
        if (pad.values[code] == value) return IntArray(0)
        pad.values[code] = value
        return intArrayOf(EV_ABS, code, value)
    }

    private fun send(pad: Pad, changes: List<IntArray>) {
        val events = changes.filter { it.isNotEmpty() }
        if (events.isEmpty() || pad.clients.isEmpty()) return
        writer.execute { pad.clients.forEach { client -> write(pad, client, events) } }
    }

    private fun write(pad: Pad, client: Client, events: List<IntArray>) {
        val now = System.currentTimeMillis()
        val size = if (client.compact) 16 else 24
        val buffer = ByteBuffer.allocate(size * (events.size + 1)).order(ByteOrder.LITTLE_ENDIAN)
        for (event in events + listOf(intArrayOf(EV_SYN, 0, 0))) {
            if (client.compact) buffer.putInt((now / 1000).toInt()).putInt((now % 1000 * 1000).toInt())
            else buffer.putLong(now / 1000).putLong(now % 1000 * 1000)
            buffer.putShort(event[0].toShort()).putShort(event[1].toShort()).putInt(event[2])
        }
        runCatching { client.socket.outputStream.write(buffer.array()) }.onFailure {
            pad.clients.remove(client)
            runCatching { client.socket.close() }
        }
    }

    private fun directory(context: Context, container: String) =
        File(context.noBackupFilesDir, "linux-android/containers/$container/runtime/input")

    @Synchronized private fun ensure(context: Context, container: String, device: InputDevice): Pad {
        start(context.applicationContext)
        pads[container to device.id]?.let { return it }
        val dir = directory(context, container).apply { mkdirs() }
        // Placeholders of an earlier Dextop process point at a socket that is gone.
        if (cleared.add(container)) dir.listFiles()?.forEach { it.delete() }
        val used = pads.values.map { it.index }.toSet()
        val index = (0 until MAX_PADS).first { it !in used }
        val pad = Pad(container, index, device.id, "Xbox 360 Controller (${device.name})".replace('\n', ' ').take(100))
        pads[container to device.id] = pad
        writeManifest(context, container)
        File(dir, "event${FIRST_EVENT + index}").createNewFile()
        return pad
    }

    private fun writeManifest(context: Context, container: String) {
        val dir = directory(context, container)
        val text = buildString {
            append("socket $socketName\n")
            pads.values.filter { it.container == container }.sortedBy { it.index }
                .forEach { append("event${FIRST_EVENT + it.index} ${it.index} ${it.name}\n") }
        }
        val staged = File(dir, ".dextop-pads.staged")
        staged.writeText(text)
        staged.renameTo(File(dir, ".dextop-pads"))
    }

    private fun start(context: Context) {
        if (started != null) return
        started = context
        val server = LocalServerSocket(socketName)
        Thread({
            while (true) {
                val socket = try { server.accept() } catch (_: Exception) { break }
                if (socket.peerCredentials.uid != android.os.Process.myUid()) { socket.close(); continue }
                writer.execute { accept(socket) }
            }
        }, "LinuxAndroidPadsAccept").apply { isDaemon = true; start() }
        context.getSystemService(InputManager::class.java).registerInputDeviceListener(object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) {}
            override fun onInputDeviceChanged(deviceId: Int) {}
            override fun onInputDeviceRemoved(deviceId: Int) = remove(context, deviceId)
        }, Handler(Looper.getMainLooper()))
    }

    private fun accept(socket: LocalSocket) {
        val header = runCatching {
            val bytes = ByteArray(4)
            socket.soTimeout = 2000
            var read = 0
            while (read < 4) { val n = socket.inputStream.read(bytes, read, 4 - read); if (n < 0) error("closed"); read += n }
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).int
        }.getOrElse { socket.close(); return }
        val pad = pads.values.firstOrNull { it.index == header and 0x7fffffff } ?: run { socket.close(); return }
        val client = Client(socket, header < 0)
        pad.clients += client
        // A new reader starts from the pad's current state, as EVIOCGABS would give it.
        write(pad, client, AXES.map { intArrayOf(EV_ABS, it, pad.values[it]) })
        Thread({ rumbleFrom(pad, socket) }, "LinuxAndroidPadRumble").apply { isDaemon = true; start() }
    }

    /** Rumble requests from the guest (linux_android_pads.c): strong, weak, milliseconds. */
    private fun rumbleFrom(pad: Pad, socket: LocalSocket) {
        runCatching {
            socket.soTimeout = 0
            val input = java.io.DataInputStream(socket.inputStream)
            val frame = ByteArray(6)
            while (true) {
                input.readFully(frame)
                val values = ByteBuffer.wrap(frame).order(ByteOrder.LITTLE_ENDIAN)
                rumble(pad.deviceId, values.short.toInt() and 0xffff, values.short.toInt() and 0xffff, values.short.toInt() and 0xffff)
            }
        }
        // The guest closed the pad: nothing keeps shaking.
        rumble(pad.deviceId, 0, 0, 0)
    }

    private fun rumble(deviceId: Int, strong: Int, weak: Int, milliseconds: Int) {
        val manager = InputDevice.getDevice(deviceId)?.vibratorManager ?: return
        val ids = manager.vibratorIds
        if (ids.isEmpty()) return
        if (strong == 0 && weak == 0) { manager.cancel(); return }
        // An effect without a length lasts until stopped; Android needs one, stops come anyway.
        val duration = (if (milliseconds == 0) 10_000 else milliseconds).toLong()
        fun effect(magnitude: Int) = android.os.VibrationEffect.createOneShot(duration, (magnitude / 257).coerceIn(1, 255))
        val combined = if (ids.size >= 2) {
            android.os.CombinedVibration.startParallel()
                .addVibrator(ids[0], effect(maxOf(strong, 1))).addVibrator(ids[1], effect(maxOf(weak, 1))).combine()
        } else android.os.CombinedVibration.createParallel(effect(maxOf(strong, weak)))
        runCatching { manager.vibrate(combined) }
    }

    @Synchronized private fun remove(context: Context, deviceId: Int) {
        pads.entries.filter { it.key.second == deviceId }.forEach { (key, pad) ->
            pads.remove(key)
            pad.clients.forEach { runCatching { it.socket.close() } }
            File(directory(context, pad.container), "event${FIRST_EVENT + pad.index}").delete()
            writeManifest(context, pad.container)
        }
    }
}
