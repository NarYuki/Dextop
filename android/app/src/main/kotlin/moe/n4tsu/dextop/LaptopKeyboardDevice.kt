package moe.n4tsu.dextop

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.util.Log
import android.view.Display
import android.view.InputDevice
import moe.n4tsu.dextop.input.PrivilegedInputClient

/**
 * The on-screen keyboard's native uinput device and its link to the desktop display.
 *
 * One place owns the whole lifecycle. The device exists from [start] to [stop],
 * independently of the pointer engine, and keys are delivered only while it is
 * associated with the display the desktop currently lives on. Every outside
 * event (device added, display changed, native state) funnels into
 * [reconcile], which compares what is wanted with what the system reports and
 * takes the one missing step. Nothing else may flip the state by hand.
 *
 * Confined to the main thread, apart from [inject], which only reads.
 */
internal class LaptopKeyboardDevice(
    private val context: Context,
    private val client: () -> PrivilegedInputClient,
    private val router: () -> PhysicalInputRouter,
    private val targetDisplayId: () -> Int,
    private val sessionActive: () -> Boolean,
    /** Held keys must be released before the device they were sent through goes away. */
    private val onDeviceLost: () -> Unit,
    private val logTag: String,
) {
    private val handler = Handler(context.mainLooper)
    private var pendingProbe: Runnable? = null
    private var probesLeft = 0

    /** True between [start] and [stop]. */
    var requested = false
        private set

    /** Input device id while published, -1 otherwise. */
    var deviceId = -1
        private set

    /** Display the device is associated with, -1 until the association took effect. */
    var displayId = -1
        private set

    private var displayUniqueId: String? = null
    private var descriptor: String? = null

    /** The (device, display) pair the last association request was made for. */
    private var routeRequestedFor: Pair<Int, String?>? = null

    @Volatile
    private var associated = false

    /** Keys can be delivered: the device is up and bound to the current desktop display. */
    val ready: Boolean get() = associated && displayId == targetDisplayId()

    fun start() {
        if (!requested) {
            requested = true
            OperationLog.i(context, "LaptopKeyboard", "native keyboard requested")
        }
        client().setKeyboardVisible(true)
        refresh("start")
    }

    fun stop() {
        if (requested || deviceId >= 0) onDeviceLost()
        requested = false
        cancelProbe()
        forget("stopped")
        client().setKeyboardVisible(false)
    }

    /** Sends one key. Dropped while the device is not [ready]; there is no second route. */
    fun inject(keyCode: Int, action: Int, metaState: Int, repeatCount: Int): Boolean {
        if (!ready) {
            // A key that finds the device missing is a cheap reason to look again,
            // but never more than the single probe that may already be waiting.
            if (requested && pendingProbe == null) handler.post { refresh("key_while_unready") }
            return false
        }
        return client().injectKeyboard(keyCode, action, metaState, repeatCount)
    }

    /** Something that can change publication or association happened; look again soon. */
    fun refresh(reason: String) {
        if (!requested) return
        probesLeft = MAX_PROBES
        scheduleProbe(FIRST_PROBE_DELAY_MS, reason)
    }

    fun onNativeCreated() = refresh("native_created")

    fun onNativeDestroyed() {
        if (deviceId >= 0) onDeviceLost()
        forget("native_destroyed")
        // The native side removes the device only when told to. If it is gone
        // while still wanted (a restarted helper process, a stale stop that
        // overtook a start), ask again instead of waiting for someone to notice.
        if (requested) {
            client().setKeyboardVisible(true)
            refresh("native_destroyed")
        }
    }

    fun onInputDeviceRemoved(removedId: Int) {
        if (removedId != deviceId) return
        onDeviceLost()
        forget("input_device_removed")
        refresh("input_device_removed")
    }

    fun onDisplayRemoved(removedId: Int) {
        if (removedId != displayId) return
        onDeviceLost()
        forget("associated_display_removed")
        refresh("associated_display_removed")
    }

    private fun scheduleProbe(delayMs: Long, reason: String) {
        cancelProbe()
        val probe = Runnable {
            pendingProbe = null
            reconcile(reason)
        }
        pendingProbe = probe
        handler.postDelayed(probe, delayMs)
    }

    private fun cancelProbe() {
        pendingProbe?.let(handler::removeCallbacks)
        pendingProbe = null
    }

    private fun reconcile(reason: String) {
        if (!requested || !sessionActive()) return
        val wantedDisplayId = targetDisplayId()
        val display = if (wantedDisplayId >= 0) {
            context.getSystemService(DisplayManager::class.java).getDisplay(wantedDisplayId)
        } else {
            null
        }
        val device = findDevice()
        if (device == null || display == null) {
            // Not published yet, or the desktop display is being recreated.
            if (associated) forget("lost:$reason")
            retry(reason, "device=${device?.id} display=${display?.displayId}")
            return
        }
        val uniqueId = uniqueIdOf(display)
        if (router().isDeviceRouted(device, display)) {
            val changed = !associated || deviceId != device.id || displayId != wantedDisplayId ||
                displayUniqueId != uniqueId
            associated = true
            deviceId = device.id
            displayId = wantedDisplayId
            displayUniqueId = uniqueId
            descriptor = device.descriptor
            routeRequestedFor = null
            if (changed) {
                val message = "native keyboard ready deviceId=${device.id} displayId=$wantedDisplayId " +
                    "reason=$reason"
                Log.i(logTag, "$message descriptor=${device.descriptor}")
                OperationLog.i(context, "LaptopKeyboard", message)
            }
            return
        }
        // Published but bound elsewhere (or nowhere). Keys would land on the
        // wrong display, so hold them until the association takes effect.
        associated = false
        deviceId = device.id
        descriptor = device.descriptor
        val wanted = device.id to uniqueId
        if (routeRequestedFor != wanted) {
            routeRequestedFor = if (router().routeDevice(device, display)) wanted else null
        }
        retry(reason, "association pending deviceId=${device.id} displayId=$wantedDisplayId")
    }

    private fun retry(reason: String, detail: String) {
        if (probesLeft > 0) {
            probesLeft -= 1
            scheduleProbe(PROBE_INTERVAL_MS, reason)
            return
        }
        // Stop polling, but forget the association request so the next event
        // (or key press) starts over with a fresh one rather than waiting on a
        // request the system evidently dropped.
        routeRequestedFor = null
        OperationLog.w(
            context,
            "LaptopKeyboard",
            "native keyboard still not usable after $MAX_PROBES probes: $detail reason=$reason"
        )
    }

    private fun forget(reason: String) {
        descriptor?.let { router().restoreDeviceDescriptor(it) }
        if (deviceId >= 0 || associated) {
            Log.i(logTag, "native laptop keyboard cleared reason=$reason deviceId=$deviceId displayId=$displayId")
        }
        associated = false
        deviceId = -1
        displayId = -1
        displayUniqueId = null
        descriptor = null
        routeRequestedFor = null
    }

    private fun findDevice(): InputDevice? = InputDevice.getDeviceIds()
        .asSequence()
        .mapNotNull(InputDevice::getDevice)
        .firstOrNull { device ->
            device.name == NAME && device.vendorId == VENDOR_ID && device.productId == PRODUCT_ID &&
                device.sources and InputDevice.SOURCE_KEYBOARD == InputDevice.SOURCE_KEYBOARD
        }

    private fun uniqueIdOf(display: Display): String? = runCatching {
        Display::class.java.getMethod("getUniqueId").invoke(display) as String
    }.getOrNull()

    companion object {
        const val NAME = "Dextop Laptop Keyboard"
        const val VENDOR_ID = 0x18d1
        const val PRODUCT_ID = 0x1529
        private const val FIRST_PROBE_DELAY_MS = 40L
        private const val PROBE_INTERVAL_MS = 100L
        private const val MAX_PROBES = 30
    }
}
