package moe.n4tsu.dextop

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.input.InputManager
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.InputDevice
import android.view.MotionEvent
import android.view.Surface
import android.view.View

/** What [PhysicalInput] needs from the session beyond the shared interfaces. */
internal interface PhysicalInputHost : DesktopSession, OverlayMenu {
    fun activateTouchInput()
    fun clearLaptopKeyboardPublication(reason: String)
    fun currentInputMode(): String
    fun displayGeometrySnapshot(reason: String, hostWidth: Int? = null, hostHeight: Int? = null): String
    fun finishAllLaptopKeyPresses()
    fun forwardMouseEvent(source: MotionEvent, view: View): Boolean
    val inputDispatcher: InputDispatcher
    val laptopKeyboardDeviceId: Int
    val laptopKeyboardGeneration: Long
    val laptopKeyboardRequested: Boolean
    val overlayTextInputActive: Boolean
    val phoneOwnedDisplay: OwnedVirtualDisplay?
    val physicalExternalDisplayConnected: Boolean
    val rawTouchscreen: RawTouchscreenBridge
    fun scheduleLaptopKeyboardReadyCheck(generation: Long, attempt: Int)
    fun setOverlayFocusable(focusable: Boolean)
    val virtualPointer: VirtualPointer
}

/** Routes physical mice and keyboards between the phone and the desktop display. */
internal class PhysicalInput(
    private val context: Context,
    private val desk: PhysicalInputHost,
) {
    internal val physicalInputRouter by lazy { PhysicalInputRouter(context, desk.privilegedAccess) }

    internal var physicalMouseActive = false

    internal var routePhysicalMouseToDextop = true

    internal var routePhysicalKeyboardToDextop = true

    internal var mouseActuallyRouted = false

    internal var keyboardActuallyRouted = false

    internal val physicalInputRoutingSupported: Boolean
        get() = !Build.MANUFACTURER.equals("samsung", ignoreCase = true)
    /**
     * An unassociated mouse follows Android's focused display. A Plasma window's
     * own display takes focus when its app opens, so the cursor moved into that
     * window's display and could not leave it. Plasma forwards the pointer to its
     * windows itself, so physical mice stay on the desktop display.
     */

    internal val routePhysicalMice: Boolean
        get() = routePhysicalMouseToDextop ||
            ((desk.phoneOwnedDisplay != null || desk.desktopEnvironment.pinPhysicalMouseToDesktop) && !desk.autoOnlySession)

    internal val inputDeviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) {
            refreshPhysicalInputState()
            desk.rawTouchscreen.scheduleRawTouchscreenTopologyRefresh("input_device_added_$deviceId", force = true)
            if (desk.laptopKeyboardRequested) {
                desk.scheduleLaptopKeyboardReadyCheck(desk.laptopKeyboardGeneration, 0)
            }
        }

        override fun onInputDeviceRemoved(deviceId: Int) {
            refreshPhysicalInputState()
                desk.rawTouchscreen.scheduleRawTouchscreenTopologyRefresh("input_device_removed_$deviceId", force = true)
            if (deviceId == desk.laptopKeyboardDeviceId) {
                desk.finishAllLaptopKeyPresses()
                desk.clearLaptopKeyboardPublication("input_device_removed")
                if (desk.laptopKeyboardRequested) {
                    desk.scheduleLaptopKeyboardReadyCheck(desk.laptopKeyboardGeneration, 0)
                }
            }
        }

        override fun onInputDeviceChanged(deviceId: Int) {
            refreshPhysicalInputState()
            desk.rawTouchscreen.scheduleRawTouchscreenTopologyRefresh("input_device_changed_$deviceId", force = true)
            if (desk.laptopKeyboardRequested &&
                (deviceId == desk.laptopKeyboardDeviceId || desk.laptopKeyboardDeviceId < 0)
            ) {
                desk.scheduleLaptopKeyboardReadyCheck(desk.laptopKeyboardGeneration, 0)
            }
        }
    }

    private fun setPhysicalInputRouting(mouse: Boolean? = null, keyboard: Boolean? = null) {
        if (!physicalInputRoutingSupported) {
            runCatching { physicalInputRouter.restore() }
            return
        }
        routePhysicalMouseToDextop = mouse ?: routePhysicalMouseToDextop
        routePhysicalKeyboardToDextop = keyboard ?: routePhysicalKeyboardToDextop
        context.getSharedPreferences(MirrorService.PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(MirrorService.KEY_ROUTE_MOUSE, routePhysicalMouseToDextop)
            .putBoolean(MirrorService.KEY_ROUTE_KEYBOARD, routePhysicalKeyboardToDextop)
            .apply()
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(desk.targetDisplayId)
        if (display != null) runCatching {
            physicalInputRouter.apply(display, routePhysicalMice, routePhysicalKeyboardToDextop)
        }.onFailure { OperationLog.w(context, "InputRouting", "overlay routing change failed", it) }
        if (!routePhysicalMouseToDextop) desk.activateTouchInput()
        desk.root?.postDelayed({
            refreshActualRoutingState(display)
            desk.menuPrimary?.let(desk::showMainMenu)
        }, 350)
    }

    // region external display output
    /**
     * Shows the Dextop desktop on a connected external screen, only when the user asks for it
     * from the app ([setExternalDisplay]). On phones whose external display only takes
     * presentations (HyperOS: no desktop windowing, so no app can open there) this is the way
     * to use it: a full-screen window on that display hosts the desktop's image.
     * - "external": that window becomes the desktop's host Surface and the desktop takes the
     *   screen's size; the phone keeps its own input mode.
     * - "mirror": the phone keeps showing the desktop and the external screen shows it too.
     * - "extend": one desktop spans both screens side by side (the external one on the side
     *   chosen in the app); each shows its part, and Plasma treats them as two outputs.
     * Unplugging the screen returns to the phone alone.
     */

    internal fun refreshActualRoutingState(display: Display?) {
        if (display == null || !desk.physicalExternalDisplayConnected) {
            mouseActuallyRouted = false
            keyboardActuallyRouted = false
            return
        }
        mouseActuallyRouted = physicalInputRouter.isMouseRouted(display)
        keyboardActuallyRouted = physicalInputRouter.isKeyboardRouted(display)
        OperationLog.i(
            context,
            "InputRouting",
            "verified display=${display.displayId} mouse=$mouseActuallyRouted keyboard=$keyboardActuallyRouted"
        )
    }

    internal fun activatePhysicalMouse() {
        if (desk.overlayTextInputActive) return
        val wasActive = physicalMouseActive
        physicalMouseActive = true
        // Physical mice use Android's pointer only. Dextop's cursor is reserved
        // exclusively for touch-panel trackpad mode.
        desk.desktopCursor.cursorView?.visibility = View.GONE
        desk.setOverlayFocusable(true)
        desk.surfaceView?.post {
            val shell = desk.plasmaShell
            val capture = if (shell is moe.n4tsu.dextop.plasma.CompositedWindowShell) shell.hostPointerCaptureRequested()
                else moe.n4tsu.dextop.plasma.WindowManagerSelection.current(context) == moe.n4tsu.dextop.plasma.WindowManagerSelection.SYSTEM
            desk.surfaceView?.let { surface ->
                if (capture && !desk.overlayTextInputActive) {
                    surface.requestFocus()
                    if (!surface.hasPointerCapture()) surface.requestPointerCapture()
                } else if (surface.hasPointerCapture()) surface.releasePointerCapture()
            }
        }
        if (!wasActive) {
            OperationLog.i(
                context,
                "InputRouting",
                "physical mouse activated inputMode=${desk.currentInputMode()} " +
                        desk.displayGeometrySnapshot("physical_mouse_activated")
            )
        }
    }

    internal fun handlePhysicalMouseEvent(event: MotionEvent, view: View): Boolean {
        if (desk.virtualPointer.isVirtualMouseEvent(event)) return true
        if (!event.isFromSource(InputDevice.SOURCE_MOUSE)) return false
        if (event.actionMasked == MotionEvent.ACTION_MOVE ||
            event.actionMasked == MotionEvent.ACTION_HOVER_MOVE
        ) activatePhysicalMouse()
        return desk.forwardMouseEvent(event, view)
    }

    internal fun handleCapturedMouseEvent(event: MotionEvent): Boolean {
        if (desk.virtualPointer.isVirtualMouseEvent(event)) return true
        if (!event.isFromSource(InputDevice.SOURCE_MOUSE)) return false
        val dx = event.getAxisValue(MotionEvent.AXIS_RELATIVE_X)
        val dy = event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y)
        if (dx != 0f || dy != 0f) {
            activatePhysicalMouse()
            movePhysicalPointer(dx, dy)
        }
        return forwardCapturedMouseButtonsAndWheel(event)
    }

    private fun forwardCapturedMouseButtonsAndWheel(source: MotionEvent): Boolean {
        if (desk.targetDisplayId < 0) return false
        return runCatching {
            val event = MotionEvent.obtain(source)
            event.offsetLocation(desk.desktopCursor.cursorX - event.x, desk.desktopCursor.cursorY - event.y)
            check(desk.inputDispatcher.send(event, desk.targetDisplayId))
            event.recycle()
            true
        }.onFailure { Log.e(desk.logTag, "captured mouse forwarding failed", it) }
            .getOrDefault(false)
    }

    private fun refreshPhysicalMouseState() {
        desk.root?.post {
            val connected = hasPhysicalMouse()
            if (!connected && physicalMouseActive) {
                physicalMouseActive = false
                desk.desktopCursor.updateVirtualCursorVisibility()
            }
        }
    }

    internal fun refreshPhysicalInputState() {
        refreshPhysicalMouseState()
        if (!physicalInputRoutingSupported || !(desk.physicalExternalDisplayConnected || routePhysicalMice)) return
        val id = desk.targetDisplayId
        if (!MirrorService.active || id < 0) return
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(id) ?: return
        desk.root?.post {
            val routed = runCatching {
                physicalInputRouter.refresh(display, routePhysicalMice, routePhysicalKeyboardToDextop)
            }
                .onFailure { OperationLog.w(context, "InputRouting", "input routing refresh failed", it) }
                .getOrDefault(0)
            Log.i(desk.logTag, "physical input routing refreshed count=$routed display=$id")
            desk.root?.postDelayed({
                refreshActualRoutingState(display)
                desk.menuPrimary?.let(desk::showMainMenu)
            }, 350)
        }
    }

    private fun hasPhysicalMouse(): Boolean = InputDevice.getDeviceIds().any { id ->
        InputDevice.getDevice(id)?.let { device ->
            device.name != MirrorService.VIRTUAL_MOUSE_NAME && device.name != MirrorService.VIRTUAL_TOUCHPAD_NAME &&
                    device.sources and InputDevice.SOURCE_MOUSE == InputDevice.SOURCE_MOUSE
        } == true
    }

    /** Tracks the injection position without involving Dextop's touch cursor. */
    internal fun movePhysicalPointer(dx: Float, dy: Float) {
        desk.desktopCursor.cursorX = (desk.desktopCursor.cursorX + dx).coerceIn(0f, desk.targetWidth - 1f)
        desk.desktopCursor.cursorY = (desk.desktopCursor.cursorY + dy).coerceIn(0f, desk.targetHeight - 1f)
    }

    internal fun startRawMouseReader() {
        Log.i(desk.logTag, "physical mouse stays under Android InputReader routing")
    }

    internal fun stopRawMouseReader() {
        // Physical mice remain owned by Android's normal InputReader path.
    }
}
