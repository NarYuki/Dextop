package moe.n4tsu.dextop

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import moe.n4tsu.dextop.input.PrivilegedInputClient
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** What [VirtualPointer] needs from the session beyond the shared interfaces. */
internal interface VirtualPointerHost : DesktopSession, OverlayMenu {
    fun buildPrivilegedInputConfig(profile: String): IntArray
    fun cancelDesktopTouchStream()
    val carCompanionPhoneTrackpadSurface: View?
    val coverTrackpadActive: Boolean
    val desktopEnvironment: DesktopEnvironment
    val directTouch: Boolean
    fun displayGeometrySnapshot(reason: String, hostWidth: Int? = null, hostHeight: Int? = null): String
    val externalOutput: MirrorService.ExternalOutput?
    fun fallbackToSoftwarePointer(reason: String)
    val laptopKeyboardRequested: Boolean
    fun logSuppressedRelativeTouchpadEvent(kind: String)
    val physicalInputRouter: PhysicalInputRouter
    val privilegedInputClient: PrivilegedInputClient
    var privilegedInputStarting: Boolean
    fun refreshPrivilegedInputConfig(reason: String)
    val root: TouchRoutingFrame?
    fun startRawTouchscreenReaderIfEligible()
    fun stopRawTouchscreenReader(reason: String)
    fun updateVirtualCursorVisibility()
    val virtualTouchpad: VirtualTouchpad
}

/** The privileged virtual mouse: publishing it, waiting for the system to route it, and sending its events. */
internal class VirtualPointer(
    private val context: Context,
    private val desk: VirtualPointerHost,
) {
    @Volatile
    internal var virtualMouseReady = false
    /**
     * InputReader can publish the uinput device before it has rendered a
     * system cursor.  Keep the accessibility cursor visible until a frame has
     * actually reached the virtual pointer; otherwise a registered-but-idle
     * touchpad leaves the desktop with no visible pointer.
     */

    @Volatile
    internal var virtualPointerOutputObserved = false

    private var virtualMouseDeviceId = -1

    private var virtualMouseDescriptor: String? = null

    private var virtualPointerDisplayId = -1

    private var virtualPointerAssociationPending = false

    internal var virtualPointerRegisteredProfile = ""

    /** Runtime-only fallback when a vendor InputReader cannot expose touchpad. */
    internal var virtualPointerRuntimeProfile: String? = null

    internal var virtualMouseGeneration = 0L

    private var virtualMouseFractionX = 0f

    private var virtualMouseFractionY = 0f

    private var virtualMouseWheelFractionX = 0f

    private var virtualMouseWheelFractionY = 0f

    internal var virtualPointerLastUnsupportedEventLogAt = 0L

    private fun persistedVirtualPointerProfile(): String {
        val input = context.getSharedPreferences(MirrorService.PREFS, Context.MODE_PRIVATE)
        if (input.contains(MirrorService.KEY_VIRTUAL_POINTER_PROFILE)) {
            return input.getString(MirrorService.KEY_VIRTUAL_POINTER_PROFILE, "touchpad")
                .orEmpty().lowercase().let(::normalizeVirtualPointerProfile)
        }
        val flutter = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
        val stored = flutter.getString("flutter.virtual_pointer_profile", null)
        if (!stored.isNullOrBlank()) return normalizeVirtualPointerProfile(stored)
        // Preserve the old switch for upgrades.  A missing switch now means
        // the new true-touchpad profile, while an explicitly enabled fallback
        // still selects the software cursor.
        val display = context.getSharedPreferences("dextop_display_environment", Context.MODE_PRIVATE)
        return if (input.getBoolean(
                MirrorService.KEY_SOFTWARE_CURSOR_FALLBACK,
                display.getBoolean(MirrorService.KEY_SOFTWARE_CURSOR_FALLBACK, false)
            )
        ) {
            "software"
        } else {
            "touchpad"
        }
    }

    private fun normalizeVirtualPointerProfile(value: String): String = when (value) {
        "touchpad", "touch_pad", "source_touchpad" -> "touchpad"
        "mouse", "virtual_mouse" -> "mouse"
        "software", "software_cursor", "cursor" -> "software"
        else -> "touchpad"
    }

    internal fun activeVirtualPointerProfile(): String =
        normalizeVirtualPointerProfile(virtualPointerRuntimeProfile ?: persistedVirtualPointerProfile())

    private fun virtualPointerDeviceName(profile: String): String =
        if (profile == "touchpad") MirrorService.VIRTUAL_TOUCHPAD_NAME else MirrorService.VIRTUAL_MOUSE_NAME

    private fun virtualMouseInputEnabled(): Boolean = activeVirtualPointerProfile() != "software"

    /**
     * Returns whether the kernel pointer is ready for the requested surface.
     * The phone surface owns the device exclusively in cursor mode, but the
     * laptop deck is a separate physical surface: its trackpad must be able
     * to use the device even when the phone display is configured for tap
     * (direct-touch) input.
     */

    internal fun virtualPointerInputActive(allowDirectTouch: Boolean): Boolean =
        !desk.demoMode && virtualMouseInputEnabled() &&
                virtualPointerRegisteredProfile == activeVirtualPointerProfile() &&
                virtualMouseReady &&
                virtualMouseProcessAlive() &&
                (allowDirectTouch || !desk.directTouch)

    internal fun virtualMouseInputActive(): Boolean = virtualPointerInputActive(false)

    internal fun virtualMouseProcessAlive(): Boolean =
        desk.privilegedInputClient.isEngineRunning()

    internal fun virtualMouseNaturalScroll(): Boolean =
        context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
            .getBoolean("flutter.virtual_mouse_natural_scroll", true)

    internal fun startVirtualMouse(profileOverride: String? = null) {
        val profile = normalizeVirtualPointerProfile(profileOverride ?: activeVirtualPointerProfile())
        // Never leave a kernel pointer behind for the phone surface in tap
        // mode. The laptop deck is an explicit exception: its trackpad is a
        // separate input surface and may attach the pointer while the phone
        // surface remains in direct-touch mode. This guard is intentionally
        // checked at the connection boundary (rather than only in
        // virtualMouseInputActive()) so switching modes actually removes the
        // device from InputReader/InputDispatcher.
        if (!MirrorService.active || desk.demoMode || profile == "software" ||
            (desk.directTouch && !desk.laptopModeActive && !desk.coverTrackpadActive &&
                    desk.carCompanionPhoneTrackpadSurface == null)
        ) {
            desk.updateVirtualCursorVisibility()
            return
        }
        if (virtualMouseProcessAlive() && virtualPointerRegisteredProfile == profile &&
            virtualMouseReady && virtualPointerDisplayId == desk.targetDisplayId
        ) {
            desk.privilegedInputClient.updateConfig(desk.buildPrivilegedInputConfig(profile))
            desk.updateVirtualCursorVisibility()
            return
        }
        if (virtualMouseProcessAlive() && virtualPointerRegisteredProfile == profile) {
            virtualMouseGeneration += 1
            virtualMouseReady = false
            virtualPointerAssociationPending = false
            desk.privilegedInputClient.updateConfig(desk.buildPrivilegedInputConfig(profile))
            scheduleVirtualMouseReadyCheck(virtualMouseGeneration, profile, 0)
            desk.updateVirtualCursorVisibility()
            return
        }
        if (virtualMouseProcessAlive() && desk.laptopKeyboardRequested) {
            // The laptop keyboard and pointer are outputs of the same native
            // engine. Reconfigure the pointer in place after a phone-surface
            // tap instead of stopEngine(), which would remove the keyboard as
            // well and leave every key inert until the service is restarted.
            virtualMouseGeneration += 1
            virtualMouseReady = false
            virtualPointerOutputObserved = false
            virtualMouseDeviceId = -1
            virtualMouseDescriptor = null
            virtualPointerDisplayId = -1
            virtualPointerAssociationPending = false
            virtualPointerRegisteredProfile = profile
            desk.privilegedInputStarting = true
            desk.privilegedInputClient.updateConfig(desk.buildPrivilegedInputConfig(profile))
            scheduleVirtualMouseReadyCheck(virtualMouseGeneration, profile, 0)
            desk.updateVirtualCursorVisibility()
            return
        }
        stopVirtualMouse()
        virtualMouseReady = false
        virtualPointerOutputObserved = false
        virtualPointerRegisteredProfile = profile
        desk.privilegedInputStarting = true
        virtualMouseFractionX = 0f
        virtualMouseFractionY = 0f
        desk.updateVirtualCursorVisibility()
        desk.privilegedInputClient.start(desk.buildPrivilegedInputConfig(profile))
        val message = "binding native privileged input profile=$profile generation=$virtualMouseGeneration"
        OperationLog.i(context, "InputRouting", message)
        Log.i(desk.logTag, message)
    }

    internal fun stopVirtualMouse() {
        val stoppedProfile = virtualPointerRegisteredProfile
        val stoppedDeviceId = virtualMouseDeviceId
        val activeContacts = desk.virtualTouchpad.virtualTouchpadActiveContactCount()
        virtualMouseDescriptor?.let(desk.physicalInputRouter::restoreDeviceDescriptor)
        desk.stopRawTouchscreenReader("virtual_pointer_stopped")
        if (stoppedProfile.isNotBlank()) {
            Log.i(
                desk.logTag,
                "stopping virtual pointer profile=$stoppedProfile deviceId=$stoppedDeviceId " +
                        "ready=$virtualMouseReady activeContacts=$activeContacts generation=$virtualMouseGeneration"
            )
        }
        virtualMouseGeneration += 1
        virtualMouseReady = false
        virtualPointerOutputObserved = false
        virtualMouseDeviceId = -1
        virtualMouseDescriptor = null
        virtualPointerDisplayId = -1
        virtualPointerAssociationPending = false
        virtualPointerRegisteredProfile = ""
        desk.privilegedInputStarting = false
        desk.privilegedInputClient.setOutputReady(false)
        desk.privilegedInputClient.stopEngine("virtual_pointer_stopped")
        virtualMouseFractionX = 0f
        virtualMouseFractionY = 0f
        virtualMouseWheelFractionX = 0f
        virtualMouseWheelFractionY = 0f
        desk.virtualTouchpad.resetVirtualTouchpadState("pointer_stopped", logSummary = activeContacts > 0)
    }

    /** Removes only the pointer output while preserving the laptop keyboard. */
    internal fun suspendVirtualMouseForLaptopKeyboard() {
        val activeContacts = desk.virtualTouchpad.virtualTouchpadActiveContactCount()
        desk.stopRawTouchscreenReader("virtual_pointer_suspended")
        virtualMouseGeneration += 1
        virtualMouseReady = false
        virtualPointerOutputObserved = false
        virtualMouseDeviceId = -1
        virtualMouseDescriptor?.let(desk.physicalInputRouter::restoreDeviceDescriptor)
        virtualMouseDescriptor = null
        virtualPointerDisplayId = -1
        virtualPointerAssociationPending = false
        virtualPointerRegisteredProfile = ""
        desk.privilegedInputStarting = false
        desk.privilegedInputClient.setOutputReady(false)
        desk.privilegedInputClient.updateConfig(desk.buildPrivilegedInputConfig("software"))
        desk.virtualTouchpad.resetVirtualTouchpadState("pointer_suspended", logSummary = activeContacts > 0)
        desk.updateVirtualCursorVisibility()
        OperationLog.i(
            context,
            "InputRouting",
            "virtual pointer suspended; native laptop keyboard preserved"
        )
    }

    /**
     * A Shizuku-compatible binder does not guarantee that UserService or
     * uinput is available on every provider/firmware. Keep the session usable
     * when the privileged path cannot start: use the existing MotionEvent
     * software cursor for this session without overwriting the user's saved
     * pointer preference. The next Dextop session probes the preferred native
     * profile again.
     */

    internal fun scheduleVirtualMouseReadyCheck(generation: Long, profile: String, attempt: Int) {
        val check = Runnable {
            if (generation != virtualMouseGeneration || !MirrorService.active ||
                activeVirtualPointerProfile() != profile
            ) return@Runnable
            if (!virtualMouseProcessAlive()) {
                OperationLog.w(context, "InputRouting", "virtual mouse process exited before InputReader registration")
                desk.fallbackToSoftwarePointer("native_process_unavailable_before_publication")
                return@Runnable
            }
            val device = findVirtualPointerDevice(profile)
            val display = desk.targetDisplayId.takeIf { it >= 0 }?.let {
                context.getSystemService(DisplayManager::class.java).getDisplay(it)
            }
            if (device != null && display != null) {
                // Electron DEX does not enable pointer output merely because
                // InputReader published the uinput node. It binds the device
                // descriptor to the target display's unique id first. Without
                // that association Android can retarget REL/BTN events to
                // display 0 when focus changes during a window drag.
                if (!desk.physicalInputRouter.isDeviceRouted(device, display)) {
                    if (!virtualPointerAssociationPending) {
                        virtualPointerAssociationPending =
                            desk.physicalInputRouter.routeDevice(device, display)
                        OperationLog.i(
                            context,
                            "InputRouting",
                            "virtual pointer association requested profile=$profile " +
                                "deviceId=${device.id} displayId=${display.displayId}",
                        )
                    }
                    if (attempt < 30) {
                        scheduleVirtualMouseReadyCheck(generation, profile, attempt + 1)
                    } else {
                        OperationLog.w(
                            context,
                            "InputRouting",
                            "virtual pointer association timed out profile=$profile " +
                                "deviceId=${device.id} displayId=${display.displayId}",
                        )
                        desk.fallbackToSoftwarePointer("display_association_timeout:$profile")
                    }
                    return@Runnable
                }
                virtualMouseDeviceId = device.id
                virtualMouseDescriptor = device.descriptor
                virtualPointerDisplayId = display.displayId
                virtualPointerAssociationPending = false
                if (desk.desktopEnvironment.id == MirrorService.XIAOMI_PROFILE && profile == "touchpad") {
                    val x = device.getMotionRange(MotionEvent.AXIS_X, InputDevice.SOURCE_MOUSE)
                    val y = device.getMotionRange(MotionEvent.AXIS_Y, InputDevice.SOURCE_MOUSE)
                    val matches = x != null && y != null &&
                        kotlin.math.abs(x.max - (desk.targetWidth - 1)) <= 1f &&
                        kotlin.math.abs(y.max - (desk.targetHeight - 1)) <= 1f
                    if (!matches) {
                        // An extended desktop never matches the phone's touchpad range; go relative at once.
                        if (attempt < 30 && desk.externalOutput?.mode != "extend") {
                            scheduleVirtualMouseReadyCheck(generation, profile, attempt + 1)
                        } else {
                            Log.w(desk.logTag, "Xiaomi touchpad viewport does not match desktop; retrying as relative mouse")
                            stopVirtualMouse()
                            virtualPointerRuntimeProfile = "mouse"
                            startVirtualMouse("mouse")
                        }
                        return@Runnable
                    }
                }
                virtualMouseReady = true
                desk.privilegedInputClient.setOutputReady(true)
                desk.refreshPrivilegedInputConfig("input_reader_ready")
                desk.updateVirtualCursorVisibility()
                val deviceDetails = virtualPointerDeviceDetails(device)
                OperationLog.i(
                    context,
                    "InputRouting",
                    "virtual pointer ready profile=$profile deviceId=${device.id}; framework routing active " +
                            desk.displayGeometrySnapshot("virtual_mouse_ready") + " native=" +
                            desk.privilegedInputClient.snapshot()
                )
                Log.i(desk.logTag, "virtual pointer ready profile=$profile $deviceDetails")
                desk.startRawTouchscreenReaderIfEligible()
                return@Runnable
            }
            if (attempt == 0 || attempt == 5 || attempt == 10 || attempt == 15) {
                val candidates = virtualPointerPublicationCandidates(profile)
                Log.i(
                    desk.logTag,
                    "waiting for InputReader profile=$profile attempt=$attempt/30 generation=$generation " +
                            "candidates=$candidates"
                )
            }
            if (attempt < 30) {
                scheduleVirtualMouseReadyCheck(generation, profile, attempt + 1)
            } else {
                // Some OEM InputReader builds accept the uinput touchpad node
                // but never publish it with SOURCE_TOUCHPAD. Retry this
                // session as a relative mouse before falling back to the
                // software cursor. The stored user preference is untouched,
                // so a later firmware update retries true touchpad input.
                if (profile == "touchpad" && virtualPointerRuntimeProfile != "mouse") {
                    OperationLog.w(
                        context,
                        "InputRouting",
                        "SOURCE_TOUCHPAD was not published; retrying with virtual mouse"
                    )
                    Log.w(
                        desk.logTag,
                        "uinput touchpad was not published by InputReader; retrying as virtual mouse"
                    )
                    stopVirtualMouse()
                    virtualPointerRuntimeProfile = "mouse"
                    startVirtualMouse("mouse")
                    return@Runnable
                }
                OperationLog.w(
                    context,
                    "InputRouting",
                    "uinput profile=$profile was not published by InputReader after ${attempt + 1} probes"
                )
                Log.w(
                    desk.logTag,
                    "uinput profile=$profile was not published by InputReader; " +
                            "candidates=${virtualPointerPublicationCandidates(profile)}"
                )
                desk.fallbackToSoftwarePointer("input_reader_publication_timeout:$profile")
            }
        }
        // During the first display setup the root window may not have been
        // attached yet. Always keep the readiness probe alive on the main
        // looper so startup ordering cannot leave the cursor permanently in a
        // half-initialized state.
        desk.root?.postDelayed(check, if (attempt == 0) 120L else 100L)
            ?: Handler(context.mainLooper).postDelayed(check, if (attempt == 0) 120L else 100L)
    }

    private fun findVirtualPointerDevice(profile: String): InputDevice? = InputDevice.getDeviceIds()
        .asSequence()
        .mapNotNull { InputDevice.getDevice(it) }
        .firstOrNull { device ->
            device.name == virtualPointerDeviceName(profile) && when (profile) {
                "touchpad" -> device.sources and InputDevice.SOURCE_TOUCHPAD == InputDevice.SOURCE_TOUCHPAD
                else -> device.sources and InputDevice.SOURCE_MOUSE == InputDevice.SOURCE_MOUSE
            }
        }

    private fun virtualPointerPublicationCandidates(profile: String): String {
        val expectedName = virtualPointerDeviceName(profile)
        val candidates = InputDevice.getDeviceIds().asSequence()
            .mapNotNull { id ->
                InputDevice.getDevice(id)?.takeIf { device ->
                    device.name == expectedName || device.name.startsWith("Dextop Virtual")
                }
            }
            .toList()
        return if (candidates.isEmpty()) {
            "none"
        } else {
            candidates.joinToString(prefix = "[", postfix = "]") { device ->
                "id=${device.id},name=${device.name},sources=0x${device.sources.toString(16)}"
            }
        }
    }

    private fun virtualPointerDeviceDetails(device: InputDevice): String {
        val ranges = device.motionRanges.joinToString(prefix = "[", postfix = "]") { range ->
            "axis=${MotionEvent.axisToString(range.axis)},min=${range.min},max=${range.max}," +
                    "resolution=${range.resolution},source=0x${range.source.toString(16)}"
        }
        return "deviceId=${device.id} name=${device.name} sources=0x${device.sources.toString(16)} " +
                "external=${device.isExternal} ranges=$ranges"
    }

    internal fun applyVirtualPointerProfile(requested: String) {
        val profile = normalizeVirtualPointerProfile(requested)
        context.getSharedPreferences(MirrorService.PREFS, Context.MODE_PRIVATE).edit()
            .putString(MirrorService.KEY_VIRTUAL_POINTER_PROFILE, profile)
            .putBoolean(MirrorService.KEY_SOFTWARE_CURSOR_FALLBACK, profile == "software")
            .apply()
        virtualPointerRuntimeProfile = null
        if (profile != "software") {
            desk.cancelDesktopTouchStream()
            stopVirtualMouse()
            startVirtualMouse()
            desk.updateVirtualCursorVisibility()
        } else {
            desk.cancelDesktopTouchStream()
            stopVirtualMouse()
            desk.updateVirtualCursorVisibility()
        }
        OperationLog.i(
            context,
            "InputRouting",
            "virtual pointer profile=$profile active=${virtualMouseInputActive()}"
        )
        desk.menuPrimary?.let(desk::showMainMenu)
    }

    internal fun virtualMouseEvents(
        events: List<Any>,
        allowDirectTouch: Boolean = false
    ): Boolean {
        if (!virtualPointerInputActive(allowDirectTouch)) return false
        if (events.size % 3 != 0) {
            Log.e(desk.logTag, "invalid primitive event frame size=${events.size}")
            return false
        }
        val encoded = IntArray(events.size)
        for (index in events.indices step 3) {
            val typeName = events[index] as? String ?: return false
            val codeName = events[index + 1] as? String ?: return false
            val value = events[index + 2] as? Number ?: return false
            encoded[index] = when (typeName) {
                "EV_SYN" -> 0
                "EV_KEY" -> 1
                "EV_REL" -> 2
                "EV_ABS" -> 3
                else -> return false
            }
            encoded[index + 1] = when (codeName) {
                "SYN_REPORT" -> 0
                "REL_X" -> 0
                "REL_Y" -> 1
                "REL_HWHEEL" -> 6
                "REL_WHEEL" -> 8
                "BTN_LEFT" -> 272
                "BTN_RIGHT" -> 273
                "BTN_TOOL_FINGER" -> 325
                "BTN_TOOL_QUINTTAP" -> 328
                "BTN_TOUCH" -> 330
                "BTN_TOOL_DOUBLETAP" -> 333
                "BTN_TOOL_TRIPLETAP" -> 334
                "BTN_TOOL_QUADTAP" -> 335
                "ABS_MT_SLOT" -> 47
                "ABS_MT_TOUCH_MAJOR" -> 48
                "ABS_MT_POSITION_X" -> 53
                "ABS_MT_POSITION_Y" -> 54
                "ABS_MT_TRACKING_ID" -> 57
                "ABS_MT_PRESSURE" -> 58
                else -> return false
            }
            encoded[index + 2] = value.toInt()
        }
        val sent = desk.privilegedInputClient.inject(encoded)
        if (!sent) {
            Log.w(desk.logTag, "privileged primitive injection rejected events=${events.size / 3}")
        } else if (!virtualPointerOutputObserved) {
            virtualPointerOutputObserved = true
            desk.updateVirtualCursorVisibility()
            OperationLog.i(context, "InputRouting", "virtual pointer output observed; software cursor hidden")
        }
        return sent
    }

    internal fun virtualMouseMove(
        dx: Float,
        dy: Float,
        allowDirectTouch: Boolean = false
    ): Boolean {
        if (!virtualPointerInputActive(allowDirectTouch)) return false
        if (virtualPointerRegisteredProfile == "touchpad") {
            desk.logSuppressedRelativeTouchpadEvent("REL_X/REL_Y movement")
            return false
        }
        virtualMouseFractionX += dx
        virtualMouseFractionY += dy
        val x = virtualMouseFractionX.toInt()
        val y = virtualMouseFractionY.toInt()
        if (x == 0 && y == 0) return true
        virtualMouseFractionX -= x
        virtualMouseFractionY -= y
        val events = mutableListOf<Any>()
        if (x != 0) {
            events += "EV_REL"; events += "REL_X"; events += x
        }
        if (y != 0) {
            events += "EV_REL"; events += "REL_Y"; events += y
        }
        events += "EV_SYN"; events += "SYN_REPORT"; events += 0
        return virtualMouseEvents(events, allowDirectTouch)
    }

    internal fun virtualMouseButton(
        button: String,
        pressed: Boolean,
        allowDirectTouch: Boolean = false
    ): Boolean =
        virtualMouseEvents(
            listOf(
                "EV_KEY", button, if (pressed) 1 else 0,
                "EV_SYN", "SYN_REPORT", 0
            ),
            allowDirectTouch
        )

    internal fun virtualMouseScroll(delta: Float, allowDirectTouch: Boolean = false): Boolean {
        if (virtualPointerRegisteredProfile == "touchpad") {
            desk.logSuppressedRelativeTouchpadEvent("REL_WHEEL scrolling")
            return false
        }
        val direction = if (virtualMouseNaturalScroll()) 1f else -1f
        // Keep the proven wheel quantization.  Sending steps too frequently
        // makes Android render the scroll as visible bursts rather than a
        // steady gesture.
        virtualMouseWheelFractionY += -delta * direction / desk.dp(12).toFloat()
        val wheel = virtualMouseWheelFractionY.toInt().coerceIn(-12, 12)
        if (wheel == 0) return true
        virtualMouseWheelFractionY -= wheel
        return virtualMouseEvents(
            listOf(
                "EV_REL", "REL_WHEEL", wheel,
                "EV_SYN", "SYN_REPORT", 0
            ),
            allowDirectTouch
        )
    }

    internal fun virtualMouseHorizontalScroll(
        delta: Float,
        allowDirectTouch: Boolean = false
    ): Boolean {
        if (virtualPointerRegisteredProfile == "touchpad") {
            desk.logSuppressedRelativeTouchpadEvent("REL_HWHEEL scrolling")
            return false
        }
        val direction = if (virtualMouseNaturalScroll()) 1f else -1f
        // REL_HWHEEL follows the same selected direction as vertical scroll.
        virtualMouseWheelFractionX += delta * direction / desk.dp(12).toFloat()
        val wheel = virtualMouseWheelFractionX.toInt().coerceIn(-12, 12)
        if (wheel == 0) return true
        virtualMouseWheelFractionX -= wheel
        return virtualMouseEvents(
            listOf(
                "EV_REL", "REL_HWHEEL", wheel,
                "EV_SYN", "SYN_REPORT", 0
            ),
            allowDirectTouch
        )
    }

    internal fun isVirtualMouseEvent(event: MotionEvent): Boolean {
        if (event.deviceId < 0) return false
        return when (InputDevice.getDevice(event.deviceId)?.name) {
            MirrorService.VIRTUAL_MOUSE_NAME, MirrorService.VIRTUAL_TOUCHPAD_NAME -> true
            else -> false
        }
    }
}
