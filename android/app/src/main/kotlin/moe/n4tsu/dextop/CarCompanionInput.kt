package moe.n4tsu.dextop

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Handler
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.hypot
import kotlin.math.min

/** What [CarCompanionInput] needs from the session beyond the shared interfaces. */
internal interface CarCompanionInputHost : DesktopSession, OverlayMenu {
    fun cancelDesktopTouchStream()
    var cursorX: Float
    var cursorY: Float
    val inputGestureHandler: Handler
    fun refreshPrivilegedInputConfig(reason: String)
    val virtualPointer: VirtualPointer
}

/** Cursor and trackpad surfaces the car companion draws on the phone, and the key filter they need. */
internal class CarCompanionInput(
    private val context: Context,
    private val desk: CarCompanionInputHost,
) {
    internal var carCompanionCursorView: CursorView? = null

    private var carCompanionCursorWindowManager: WindowManager? = null

    private var carCompanionCursorDisplayId = -1

    internal var carCompanionPhoneTrackpadSurface: View? = null

    internal var carCompanionPhoneUsesNativeTouchpad = false

    internal var carCompanionPhonePreviousRuntimeProfile: String? = null

    private var carMirrorPointerX = 0f

    private var carMirrorPointerY = 0f

    private var carMirrorDownX = 0f

    private var carMirrorDownY = 0f

    private var carMirrorDownAt = 0L

    private var carMirrorMoved = false

    private var carMirrorDragging = false

    private var carMirrorHoldRunnable: Runnable? = null

    internal fun applyCarCompanionKeyFilter() {
        val info = desk.accessibilityService.serviceInfo ?: return
        val filter = android.accessibilityservice.AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        val next = if (MirrorService.carCompanionInputActive) info.flags and filter.inv() else info.flags or filter
        if (next == info.flags) return
        info.flags = next
        desk.accessibilityService.serviceInfo = info
        Log.i(desk.logTag, "Car Companion keyboard filter enabled=${!MirrorService.carCompanionInputActive}")
    }

    internal fun attachCarCompanionCursor(displayId: Int) {
        if (displayId < 0) return
        if (carCompanionCursorDisplayId == displayId && carCompanionCursorView != null) return
        detachCarCompanionCursor()
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(displayId) ?: return
        val displayContext = context.createDisplayContext(display)
        val manager = displayContext.getSystemService(WindowManager::class.java)
        val cursor = CursorView(displayContext)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "Dextop Car Companion cursor"
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            fitInsetsTypes = 0
            setFitInsetsIgnoringVisibility(true)
        }
        runCatching { manager.addView(cursor, params) }
            .onSuccess {
                carCompanionCursorWindowManager = manager
                carCompanionCursorView = cursor
                carCompanionCursorDisplayId = displayId
                OperationLog.i(context, "CarCompanion", "software cursor attached display=$displayId")
            }
            .onFailure { OperationLog.w(context, "CarCompanion", "software cursor attach failed display=$displayId", it) }
    }

    internal fun attachCarCompanionPhoneTrackpad(surface: View, nativeTouchpad: Boolean) {
        if (!desk.autoOnlySession) return
        if (carCompanionPhoneTrackpadSurface === surface &&
            carCompanionPhoneUsesNativeTouchpad == nativeTouchpad
        ) return
        carCompanionPhoneTrackpadSurface?.let(::detachCarCompanionPhoneTrackpad)
        carCompanionPhonePreviousRuntimeProfile = desk.virtualPointer.virtualPointerRuntimeProfile
        carCompanionPhoneTrackpadSurface = surface
        carCompanionPhoneUsesNativeTouchpad = nativeTouchpad
        desk.cancelDesktopTouchStream()
        desk.virtualPointer.stopVirtualMouse()
        // The plain trackpad forwards the complete MT Type-B contact stream
        // and lets Android's TouchpadInputMapper own two-finger scrolling,
        // dragging, acceleration and gesture continuity.  The mirrored view
        // remains a mouse because it maps touches to absolute target pixels.
        val profile = if (nativeTouchpad) "touchpad" else "mouse"
        desk.virtualPointer.virtualPointerRuntimeProfile = profile
        desk.virtualPointer.startVirtualMouse(profile)
        surface.post { desk.refreshPrivilegedInputConfig("car_companion_phone_trackpad_attached") }
        OperationLog.i(
            context,
            "CarCompanion",
            "phone surface attached to Dextop uinput profile=$profile"
        )
    }

    /**
     * Treat the phone mirror as Electron DEX treats its mirrored trackpad:
     * the Activity consumes the phone touch stream and this method emits only
     * display-bound uinput mouse events.  In particular, never turn a drag
     * into an absolute injected touch; that can follow focus back to display 0
     * and move the phone's own window instead of the Auto desktop window.
     */

    internal fun handleCarCompanionPhoneMirror(surface: View, event: MotionEvent) {
        if (carCompanionPhoneTrackpadSurface !== surface ||
            surface.width <= 0 || surface.height <= 0 ||
            desk.targetWidth <= 0 || desk.targetHeight <= 0
        ) return

        val scale = min(
            surface.width.toFloat() / desk.targetWidth.toFloat(),
            surface.height.toFloat() / desk.targetHeight.toFloat(),
        ).takeIf { it.isFinite() && it > 0f } ?: return
        val contentWidth = desk.targetWidth * scale
        val contentHeight = desk.targetHeight * scale
        val contentLeft = (surface.width - contentWidth) / 2f
        val contentTop = (surface.height - contentHeight) / 2f
        val mappedX = ((event.x - contentLeft) / scale).coerceIn(0f, desk.targetWidth - 1f)
        val mappedY = ((event.y - contentTop) / scale).coerceIn(0f, desk.targetHeight - 1f)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                resetCarMirrorGesture(releaseButton = true)
                if (!desk.virtualPointer.virtualPointerInputActive(true)) return
                carMirrorPointerX = mappedX
                carMirrorPointerY = mappedY
                carMirrorDownX = mappedX
                carMirrorDownY = mappedY
                carMirrorDownAt = event.eventTime
                carMirrorMoved = false

                // Electron first clamps the relative pointer at the origin,
                // then moves it to the touched pixel in the mirrored display.
                desk.virtualPointer.virtualMouseMove(-40000f, -40000f, true)
                desk.virtualPointer.virtualMouseMove(mappedX, mappedY, true)
                desk.cursorX = mappedX
                desk.cursorY = mappedY

                carMirrorHoldRunnable = Runnable {
                    if (carCompanionPhoneTrackpadSurface === surface &&
                        !carMirrorMoved && !carMirrorDragging &&
                        desk.virtualPointer.virtualPointerInputActive(true)
                    ) {
                        carMirrorDragging = desk.virtualPointer.virtualMouseButton("BTN_LEFT", true, true)
                    }
                }.also { desk.inputGestureHandler.postDelayed(it, 450L) }
            }

            MotionEvent.ACTION_MOVE -> {
                if (!desk.virtualPointer.virtualPointerInputActive(true)) return
                val totalDistance = hypot(mappedX - carMirrorDownX, mappedY - carMirrorDownY)
                if (!carMirrorMoved && totalDistance > 12f) {
                    carMirrorMoved = true
                    carMirrorHoldRunnable?.let(desk.inputGestureHandler::removeCallbacks)
                    carMirrorHoldRunnable = null
                }
                val dx = mappedX - carMirrorPointerX
                val dy = mappedY - carMirrorPointerY
                carMirrorPointerX = mappedX
                carMirrorPointerY = mappedY
                desk.cursorX = (desk.cursorX + dx).coerceIn(0f, desk.targetWidth - 1f)
                desk.cursorY = (desk.cursorY + dy).coerceIn(0f, desk.targetHeight - 1f)
                desk.virtualPointer.virtualMouseMove(dx, dy, true)
            }

            MotionEvent.ACTION_UP -> {
                carMirrorHoldRunnable?.let(desk.inputGestureHandler::removeCallbacks)
                carMirrorHoldRunnable = null
                if (carMirrorDragging) {
                    desk.virtualPointer.virtualMouseButton("BTN_LEFT", false, true)
                } else if (!carMirrorMoved && event.eventTime - carMirrorDownAt < 450L &&
                    desk.virtualPointer.virtualPointerInputActive(true)
                ) {
                    if (desk.virtualPointer.virtualMouseButton("BTN_LEFT", true, true)) {
                        desk.inputGestureHandler.postDelayed(
                            { desk.virtualPointer.virtualMouseButton("BTN_LEFT", false, true) },
                            45L,
                        )
                    }
                }
                resetCarMirrorGesture(releaseButton = false)
            }

            MotionEvent.ACTION_CANCEL -> resetCarMirrorGesture(releaseButton = true)
        }
    }

    private fun resetCarMirrorGesture(releaseButton: Boolean) {
        carMirrorHoldRunnable?.let(desk.inputGestureHandler::removeCallbacks)
        carMirrorHoldRunnable = null
        if (releaseButton && carMirrorDragging) {
            desk.virtualPointer.virtualMouseButton("BTN_LEFT", false, true)
        }
        carMirrorDragging = false
        carMirrorMoved = false
        carMirrorDownAt = 0L
    }

    internal fun detachCarCompanionPhoneTrackpad(surface: View) {
        if (carCompanionPhoneTrackpadSurface !== surface) return
        resetCarMirrorGesture(releaseButton = true)
        carCompanionPhoneTrackpadSurface = null
        carCompanionPhoneUsesNativeTouchpad = false
        desk.virtualPointer.stopVirtualMouse()
        desk.virtualPointer.virtualPointerRuntimeProfile = carCompanionPhonePreviousRuntimeProfile
        carCompanionPhonePreviousRuntimeProfile = null
        if (desk.virtualPointer.activeVirtualPointerProfile() != "software") desk.virtualPointer.startVirtualMouse()
        OperationLog.i(context, "CarCompanion", "phone uinput trackpad detached")
    }

    internal fun detachCarCompanionCursor() {
        val cursor = carCompanionCursorView
        val manager = carCompanionCursorWindowManager
        if (cursor != null && manager != null) runCatching { manager.removeViewImmediate(cursor) }
        carCompanionCursorView = null
        carCompanionCursorWindowManager = null
        carCompanionCursorDisplayId = -1
    }
}
