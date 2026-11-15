package moe.n4tsu.dextop

import android.content.Context
import android.graphics.Matrix
import android.hardware.input.InputManager
import android.os.Handler
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

/** What [TouchSurface] needs from the session beyond the shared interfaces. */
internal interface TouchSurfaceHost : DesktopSession, OverlayMenu {
    val carCompanion: CarCompanionInput
    fun changeOrientation()
    val externalOutputs: ExternalDisplays
    val haptics: Haptics
    val imeRegion: ImeRegionProbe
    val inputDispatcher: InputDispatcher
    val keyboardDeck: KeyboardDeck
    val launcher: DesktopLauncher
    val physicalInput: PhysicalInput
    val rawTouchscreen: RawTouchscreenBridge
    fun stop()
    val virtualPointer: VirtualPointer
    val virtualTouchpad: VirtualTouchpad
    val coverButtons: CoverBackButtons
    fun currentInputMode(): String
    val diagnostics: InputDiagnostics
    fun displayGeometrySnapshot(reason: String, hostWidth: Int? = null, hostHeight: Int? = null): String
    /** Written here, not only read. */
    override var directTouch: Boolean
}

/** Turns touches on the phone surface into cursor movement, clicks, gestures or direct desktop touches. */
internal class TouchSurface(
    private val context: Context,
    private val desk: TouchSurfaceHost,
) {
    internal var threeFingerEdgeSwipe = false

    internal var edgeMenuTriggered = false

    internal fun dedicatedTrackpadInputActive(): Boolean =
        (desk.laptopModeActive || desk.coverButtons.coverTrackpadActive || desk.carCompanion.carCompanionPhoneTrackpadSurface != null) &&
                desk.virtualPointer.virtualPointerInputActive(true)

    internal fun recordTouchRouting(event: MotionEvent, direct: Boolean) {
        val now = SystemClock.uptimeMillis()
        // See recordInputDispatch(): session logging must never become part of
        // the touch rendering path. One geometry sample per 750 ms is enough
        // for a useful report without synchronous file churn.
        if (now - desk.diagnostics.lastTouchDiagnosticAt < 750L) return
        desk.diagnostics.lastTouchDiagnosticAt = now
        val view = desk.surfaceView
        val mappedX = if (view != null && view.width > 0) {
            (event.x / view.width * desk.targetWidth).coerceIn(0f, desk.targetWidth - 1f)
        } else 0f
        val mappedY = if (view != null && view.height > 0) {
            (event.y / view.height * desk.targetHeight).coerceIn(0f, desk.targetHeight - 1f)
        } else 0f
        OperationLog.i(
            context,
            "TouchRouting",
            "action=${event.actionMasked} pointers=${event.pointerCount} source=${event.source} " +
                    "pointX:${event.x} pointY:${event.y} mappedX:${mappedX} mappedY:${mappedY} " +
                    "directTouch=$direct inputMode=${desk.currentInputMode()} " +
                    desk.displayGeometrySnapshot("touch_event")
        )
    }

    /** Latches native MT routing at ACTION_DOWN so readiness cannot switch mid-stream. */
    internal var nativeTouchpadGestureActive = false

    private var lastX = 0f

    private var lastY = 0f

    private var touchStartX = 0f

    private var touchStartY = 0f

    private var downTime = 0L

    private var injectedDownTime = 0L

    private var moved = false

    private var maxPointers = 0

    private var twoFinger = false

    private var twoFingerTravelX = 0f

    private var twoFingerTravelY = 0f

    private var threeFinger = false

    private var scrolling = false

    private var lastScrollX = 0f

    private var lastScrollY = 0f

    private var scrollX = 0f

    private var scrollY = 0f

    internal var dragHeld = false

    private var directTouchHeld = false

    /** Latches an IME direct-touch route from ACTION_DOWN through UP/CANCEL. */
    internal var imeDirectTouchHeld = false

    private var injectedDirectTouchActive = false

    private var directInjectionDownTime = 0L

    private var directSourceDownTime = 0L

    private var lastInjectedDirectTouch: MotionEvent? = null

    internal var experimentalMultiTouch = false

    private var edgeGesturePrimaryPointerId = MotionEvent.INVALID_POINTER_ID

    private var edgeGestureLeadX = 0f

    private var edgeGestureLeadY = 0f

    private var longPressTriggered = false

    private var longPressRunnable: Runnable? = null

    internal val inputGestureHandler by lazy(LazyThreadSafetyMode.NONE) { Handler(context.mainLooper) }

    internal fun trackpad(
        event: MotionEvent,
        sourceView: View,
        forceCursorMode: Boolean = false,
        allowVirtualPointer: Boolean = false,
        hapticView: View? = null
    ): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            imeDirectTouchHeld = !forceCursorMode && !desk.directTouch &&
                    sourceView === desk.surfaceView &&
                    desk.imeRegion.imeTouchRegionContainsSurfacePoint(event.x, event.y)
            if (imeDirectTouchHeld) {
                Log.i(desk.logTag, "IME direct-touch stream latched at ${event.x}:${event.y} region=${desk.imeRegion.imeTouchRegion?.bounds}")
            }
        }
        val imeDirectTouch = !forceCursorMode && !desk.directTouch &&
                sourceView === desk.surfaceView && imeDirectTouchHeld
        val useDirectTouch = (desk.directTouch && !forceCursorMode) || imeDirectTouch
        val useVirtualMouse = (if (allowVirtualPointer) {
            dedicatedTrackpadInputActive()
        } else {
            desk.virtualPointer.virtualMouseInputActive()
        }) && !useDirectTouch
        recordTouchRouting(event, useDirectTouch)
        maxPointers = maxOf(maxPointers, event.pointerCount)
        val rawBridgeOwnsSource = sourceView === desk.surfaceView ||
                sourceView === desk.keyboardDeck.laptopTrackpadView
        if (rawBridgeOwnsSource &&
            desk.rawTouchscreen.rawTouchscreenBridgeConsumesTouchSurface(sourceView)
        ) {
            // InputDispatcher may cancel this accessibility-window stream as
            // soon as the virtual touchpad moves the system pointer. The raw
            // EventHub stream remains continuous, so it is the sole producer
            // for this gesture and the overlay must never duplicate frames.
            if (event.actionMasked == MotionEvent.ACTION_DOWN ||
                event.actionMasked == MotionEvent.ACTION_CANCEL
            ) {
                Log.i(
                    desk.logTag,
                    "overlay touch ignored by raw bridge action=${MotionEvent.actionToString(event.action)} " +
                            "pointers=${event.pointerCount} readerReady=${desk.rawTouchscreen.touchscreenReaderReady} " +
                            "surface=${if (sourceView === desk.keyboardDeck.laptopTrackpadView) "laptop_trackpad" else "fullscreen_surface"}"
                )
            }
            return true
        }
        val nativeTouchpadAvailable = useVirtualMouse &&
                desk.virtualPointer.virtualPointerRegisteredProfile == "touchpad"
        if (experimentalMultiTouch && !nativeTouchpadAvailable && !imeDirectTouch &&
            handleExperimentalEdgeGesture(event)
        ) {
            if (nativeTouchpadGestureActive || desk.virtualTouchpad.virtualTouchpadActiveContactCount() > 0) {
                desk.virtualTouchpad.finishVirtualTouchpadGesture(
                    "dextop_edge_gesture_intercept",
                    allowVirtualPointer
                )
            }
            return true
        }
        if (useDirectTouch && experimentalMultiTouch) {
            injectDirectTouch(event)
            if (imeDirectTouch && (event.actionMasked == MotionEvent.ACTION_UP ||
                    event.actionMasked == MotionEvent.ACTION_CANCEL)
            ) {
                imeDirectTouchHeld = false
            }
            return true
        }
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            nativeTouchpadGestureActive = nativeTouchpadAvailable
            if (nativeTouchpadGestureActive) {
                moved = false
                twoFinger = false
                threeFinger = false
                scrolling = false
                maxPointers = 1
                longPressTriggered = false
                longPressRunnable?.let(inputGestureHandler::removeCallbacks)
                longPressRunnable = null
            }
        }
        if (nativeTouchpadGestureActive) {
            // Dextop's configured three-finger command remains an application
            // gesture. End the two-contact stream before intercepting the
            // third finger so InputReader never retains a ghost contact.
            if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN &&
                event.pointerCount >= 3
            ) {
                threeFinger = true
                moved = true
                desk.virtualTouchpad.finishVirtualTouchpadGesture("dextop_three_finger_intercept", allowVirtualPointer)
                OperationLog.i(
                    context,
                    "InputRouting",
                    "native touchpad stream handed to Dextop three-finger gesture"
                )
                Log.i(desk.logTag, "native touchpad stream handed to Dextop three-finger gesture")
                return true
            }
            // Tap, pointer acceleration, and two-finger scrolling are all
            // interpreted by Android's TouchpadInputMapper from these contacts.
            // Do not run Dextop's click or REL wheel paths as well.
            desk.virtualTouchpad.virtualTouchpadMotionEvent(event, sourceView, allowVirtualPointer)
            return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downTime = SystemClock.uptimeMillis()
                lastX = event.x
                lastY = event.y
                touchStartX = event.x
                touchStartY = event.y
                moved = false
                twoFinger = false
                twoFingerTravelX = 0f
                twoFingerTravelY = 0f
                threeFinger = false
                scrolling = false
                longPressTriggered = false
                if (useDirectTouch) {
                    moveCursorToTouch(event.x, event.y)
                    injectTouch(MotionEvent.ACTION_DOWN, desk.desktopCursor.cursorX, desk.desktopCursor.cursorY)
                    directTouchHeld = true
                    return true
                }
                longPressRunnable?.let(inputGestureHandler::removeCallbacks)
                longPressRunnable = Runnable {
                    if (!moved && !twoFinger && maxPointers == 1) {
                        longPressTriggered = true
                        desk.haptics.performLaptopHaptic(hapticView, strong = true, source = HapticSource.POINTER)
                        performLongPressGesture(allowVirtualPointer)
                    }
                }.also { inputGestureHandler.postDelayed(it, 450) }
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (directTouchHeld) {
                    injectTouch(MotionEvent.ACTION_UP, desk.desktopCursor.cursorX, desk.desktopCursor.cursorY)
                    directTouchHeld = false
                }
                longPressRunnable?.let(inputGestureHandler::removeCallbacks)
                if (event.pointerCount >= 3) {
                    threeFinger = true
                    if (scrolling) injectTouch(MotionEvent.ACTION_UP, scrollX, scrollY)
                    scrolling = false
                    return true
                }
                if (event.pointerCount == 2) {
                    twoFinger = true
                    twoFingerTravelX = 0f
                    twoFingerTravelY = 0f
                    lastScrollX = event.getX(0)
                    lastScrollY = event.getY(0)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (directTouchHeld && event.pointerCount == 1) {
                    moveCursorToTouch(event.x, event.y)
                    injectTouch(MotionEvent.ACTION_MOVE, desk.desktopCursor.cursorX, desk.desktopCursor.cursorY)
                    moved = true
                } else if (threeFinger) {
                    moved = true
                } else if (event.pointerCount >= 2) {
                    val x = event.getX(0)
                    val y = event.getY(0)
                    val dx = x - lastScrollX
                    val dy = y - lastScrollY
                    twoFingerTravelX += dx
                    twoFingerTravelY += dy
                    val thresholdReached = scrolling ||
                            hypot(twoFingerTravelX, twoFingerTravelY) >= desk.dp(10)
                    if (thresholdReached) {
                        if (useVirtualMouse) {
                            scrolling = true
                            desk.virtualPointer.virtualMouseHorizontalScroll(dx, allowVirtualPointer)
                            desk.virtualPointer.virtualMouseScroll(dy, allowVirtualPointer)
                        } else {
                            if (!scrolling) {
                                scrolling = true
                                scrollX = desk.desktopCursor.cursorX
                                scrollY = desk.desktopCursor.cursorY
                                injectTouch(MotionEvent.ACTION_DOWN, scrollX, scrollY)
                            }
                            scrollX = (scrollX + dx * 2f).coerceIn(0f, desk.targetWidth - 1f)
                            scrollY = (scrollY + dy * 2f).coerceIn(0f, desk.targetHeight - 1f)
                            injectTouch(MotionEvent.ACTION_MOVE, scrollX, scrollY)
                        }
                        moved = true
                    }
                    lastScrollX = x
                    lastScrollY = y
                } else if (!twoFinger) {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    lastX = event.x
                    lastY = event.y
                    if (hypot(event.x - touchStartX, event.y - touchStartY) > desk.dp(4)) {
                        moved = true
                        if (!longPressTriggered) longPressRunnable?.let(inputGestureHandler::removeCallbacks)
                    }
                    moveCursor(dx * 1.1f, dy * 1.1f, allowVirtualPointer)
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                if (!threeFinger && scrolling) {
                    if (!useVirtualMouse) injectTouch(MotionEvent.ACTION_UP, scrollX, scrollY)
                    scrolling = false
                }
            }

            MotionEvent.ACTION_UP -> {
                longPressRunnable?.let(inputGestureHandler::removeCallbacks)
                val completedDirectTouch = directTouchHeld
                if (completedDirectTouch) {
                    moveCursorToTouch(event.x, event.y)
                    injectTouch(MotionEvent.ACTION_UP, desk.desktopCursor.cursorX, desk.desktopCursor.cursorY)
                    directTouchHeld = false
                } else if (scrolling && !useVirtualMouse) injectTouch(MotionEvent.ACTION_UP, scrollX, scrollY)
                if (completedDirectTouch) {
                    Unit
                } else if (threeFinger || maxPointers >= 3) {
                    // A real touchpad profile has already bypassed the legacy
                    // edge recognizer above. Always retain Dextop's configured
                    // three-finger action as the emergency menu/exit path.
                    if (!experimentalMultiTouch || nativeTouchpadAvailable) {
                        performConfiguredGesture()
                    }
                } else if (longPressTriggered && dragHeld) {
                    if (useVirtualMouse) {
                        desk.virtualPointer.virtualMouseButton("BTN_LEFT", false, allowVirtualPointer)
                    } else if (!(allowVirtualPointer && desk.carCompanion.carCompanionPhoneTrackpadSurface === sourceView)) {
                        injectTouch(MotionEvent.ACTION_UP, desk.desktopCursor.cursorX, desk.desktopCursor.cursorY)
                    }
                    dragHeld = false
                } else if (twoFinger && !moved && !scrolling) {
                    desk.haptics.performLaptopHaptic(hapticView, strong = true, source = HapticSource.POINTER)
                    performTwoFingerGesture(allowVirtualPointer)
                } else if (!twoFinger && !moved && !dragHeld && SystemClock.uptimeMillis() - downTime < 250) {
                    if (useDirectTouch) moveCursorToTouch(event.x, event.y)
                    desk.haptics.performLaptopHaptic(hapticView, source = HapticSource.POINTER)
                    leftClick(allowVirtualPointer)
                }
                maxPointers = 0
                twoFingerTravelX = 0f
                twoFingerTravelY = 0f
                twoFinger = false
                threeFinger = false
                scrolling = false
                imeDirectTouchHeld = false
            }

            MotionEvent.ACTION_CANCEL -> {
                longPressRunnable?.let(inputGestureHandler::removeCallbacks)
                if (directTouchHeld) injectTouch(MotionEvent.ACTION_UP, desk.desktopCursor.cursorX, desk.desktopCursor.cursorY)
                directTouchHeld = false
                if (scrolling && !useVirtualMouse) injectTouch(MotionEvent.ACTION_UP, scrollX, scrollY)
                if (dragHeld) {
                    if (useVirtualMouse) {
                        desk.virtualPointer.virtualMouseButton("BTN_LEFT", false, allowVirtualPointer)
                    } else if (!(allowVirtualPointer && desk.carCompanion.carCompanionPhoneTrackpadSurface === sourceView)) {
                        injectTouch(MotionEvent.ACTION_UP, desk.desktopCursor.cursorX, desk.desktopCursor.cursorY)
                    }
                }
                dragHeld = false
                maxPointers = 0
                twoFingerTravelX = 0f
                twoFingerTravelY = 0f
                twoFinger = false
                threeFinger = false
                scrolling = false
                imeDirectTouchHeld = false
            }
        }
        return true
    }

    /**
     * Use Android's own haptic policy for the laptop deck.  Calling
     * View.performHapticFeedback keeps this compatible with OEM vibration
     * settings and avoids injecting a separate vibration permission or a
     * device-specific amplitude. The predefined heavy click is deliberately
     * stronger than KEYBOARD_TAP on Samsung and Pixel devices while still
     * following the system's vibrator policy. The keyboard demo is
     * interactive, so it uses the same feedback as the live laptop deck.
     */

    internal fun performConfiguredGesture() {
        when (context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
            .getString("flutter.gesture_three_finger", "menu")) {
            "home" -> desk.launcher.launchHome()
            "rotate" -> desk.changeOrientation()
            "stop" -> desk.stop()
            else -> desk.toggleMenu()
        }
    }

    private fun handleExperimentalEdgeGesture(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.x
                touchStartY = event.y
                threeFingerEdgeSwipe = false
                edgeMenuTriggered = false
                edgeGesturePrimaryPointerId = event.getPointerId(0)
                edgeGestureLeadX = 0f
                edgeGestureLeadY = 0f
            }

            MotionEvent.ACTION_POINTER_DOWN -> if (event.pointerCount >= 3) {
                var minimumX = Float.MAX_VALUE
                var minimumY = Float.MAX_VALUE
                for (index in 0 until event.pointerCount) {
                    val x = event.getX(index)
                    val y = event.getY(index)
                    minimumX = minOf(minimumX, x)
                    minimumY = minOf(minimumY, y)
                }
                val portrait = desk.targetHeight > desk.targetWidth
                if (portrait) {
                    // A 120dp strip forces all three fingers against the top
                    // bezel on tall phones. Extend the pickup region downward
                    // while keeping it proportional on foldables and tablets.
                    val startLimit = minOf(
                        (desk.surfaceView?.height?.times(0.28f) ?: desk.dp(240).toFloat()),
                        desk.dp(240).toFloat()
                    )
                    if (minimumY > startLimit && touchStartY > startLimit) return false
                } else if (minimumX > desk.dp(120) && touchStartX > desk.dp(120)) return false
                threeFingerEdgeSwipe = true
                // Preserve the first contact's edge position. Resetting the
                // origin when the third finger landed discarded the travel
                // made while placing the other contacts, so Tap mode often
                // required an extra swipe before opening the menu.
                edgeGestureLeadX = touchStartX
                edgeGestureLeadY = touchStartY
                if (desk.directTouch) cancelInjectedDirectTouch()
                maybeTriggerEdgeMenu(event)
                return true
            }

            MotionEvent.ACTION_MOVE -> if (threeFingerEdgeSwipe) {
                // Keep consuming the intercepted stream, but never complete a
                // three-finger gesture after one of the fingers has lifted.
                if (event.pointerCount < 3) return true
                maybeTriggerEdgeMenu(event)
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> if (threeFingerEdgeSwipe) return true
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (threeFingerEdgeSwipe) {
                threeFingerEdgeSwipe = false
                edgeMenuTriggered = false
                edgeGesturePrimaryPointerId = MotionEvent.INVALID_POINTER_ID
                edgeGestureLeadX = 0f
                edgeGestureLeadY = 0f
                return true
            }
        }
        return false
    }

    private fun maybeTriggerEdgeMenu(event: MotionEvent) {
        val primaryIndex = event.findPointerIndex(edgeGesturePrimaryPointerId)
        if (primaryIndex < 0) return
        val portrait = desk.targetHeight > desk.targetWidth
        val distance = if (portrait) {
            event.getY(primaryIndex) - edgeGestureLeadY
        } else {
            event.getX(primaryIndex) - edgeGestureLeadX
        }
        val triggerDistance = if (portrait) desk.dp(20) else desk.dp(28)
        if (!edgeMenuTriggered && distance >= triggerDistance) {
            edgeMenuTriggered = true
            desk.toggleMenu()
        }
    }

    private fun performTwoFingerGesture(allowVirtualPointer: Boolean = false) {
        val action = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
            .getString("flutter.gesture_two_finger", "right_click")
        when (action) {
            "home" -> desk.launcher.launchHome()
            "menu" -> desk.toggleMenu()
            else -> rightClick(allowVirtualPointer)
        }
    }

    private fun performLongPressGesture(allowVirtualPointer: Boolean = false) {
        val action = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
            .getString("flutter.gesture_long_press", "drag")
        when (action) {
            "right_click" -> rightClick(allowVirtualPointer)
            "menu" -> desk.toggleMenu()
            else -> {
                dragHeld = true
                if (!desk.virtualPointer.virtualMouseButton("BTN_LEFT", true, allowVirtualPointer)) {
                    if (allowVirtualPointer && desk.carCompanion.carCompanionPhoneTrackpadSurface != null) {
                        dragHeld = false
                        OperationLog.w(
                            context,
                            "CarCompanion",
                            "uinput drag start rejected; touch fallback suppressed",
                            null,
                        )
                    } else {
                        injectTouch(MotionEvent.ACTION_DOWN, desk.desktopCursor.cursorX, desk.desktopCursor.cursorY)
                    }
                }
                desk.desktopCursor.cursorView?.pulse()
            }
        }
    }

    private fun moveCursorToTouch(x: Float, y: Float) {
        val view = desk.surfaceView ?: return
        if (view.width <= 0 || view.height <= 0) return
        val region = desk.externalOutputs.regionFor(view)
        desk.desktopCursor.cursorX = region.left + x / view.width * region.width()
        desk.desktopCursor.cursorY = region.top + y / view.height * region.height()
        if (!desk.directTouch && !desk.virtualPointer.virtualMouseInputActive()) {
            desk.desktopCursor.updateCursorPosition()
        }
    }

    private fun moveCursor(
        dx: Float,
        dy: Float,
        allowVirtualPointer: Boolean = false
    ) {
        val useVirtualPointer = desk.virtualPointer.virtualPointerInputActive(allowVirtualPointer)
        // Pointer sensitivity is deliberately fixed.  Older releases exposed
        // DPI and acceleration preferences, but those values could persist in
        // SharedPreferences and make input unusable.  Always use raw deltas.
        val effectiveDx = dx
        val effectiveDy = dy
        desk.desktopCursor.cursorX = (desk.desktopCursor.cursorX + effectiveDx).coerceIn(0f, desk.targetWidth - 1f)
        desk.desktopCursor.cursorY = (desk.desktopCursor.cursorY + effectiveDy).coerceIn(0f, desk.targetHeight - 1f)
        if (useVirtualPointer) {
            desk.virtualPointer.virtualMouseMove(effectiveDx, effectiveDy, allowVirtualPointer)
        } else if (allowVirtualPointer && desk.carCompanion.carCompanionPhoneTrackpadSurface != null) {
            // This surface owns a display-bound uinput mouse. Falling back to
            // absolute touch while BTN_LEFT is held can route the stream to
            // display 0 and make an Auto window jump between drag targets.
            Unit
        } else {
            desk.desktopCursor.updateCursorPosition()
            if (dragHeld) injectTouch(MotionEvent.ACTION_MOVE, desk.desktopCursor.cursorX, desk.desktopCursor.cursorY)
        }
    }

    private fun leftClick(allowVirtualPointer: Boolean = false) {
        if (dragHeld) return
        if (desk.virtualPointer.virtualPointerInputActive(allowVirtualPointer)) {
            desk.virtualPointer.virtualMouseButton("BTN_LEFT", true, allowVirtualPointer)
            desk.virtualPointer.virtualMouseButton("BTN_LEFT", false, allowVirtualPointer)
        } else {
            injectTouch(MotionEvent.ACTION_DOWN, desk.desktopCursor.cursorX, desk.desktopCursor.cursorY)
            injectTouch(MotionEvent.ACTION_UP, desk.desktopCursor.cursorX, desk.desktopCursor.cursorY)
            if (!desk.directTouch) desk.desktopCursor.cursorView?.pulse()
        }
    }

    private fun rightClick(allowVirtualPointer: Boolean = false) {
        if (dragHeld || desk.targetDisplayId < 0) return
        if (desk.virtualPointer.virtualPointerInputActive(allowVirtualPointer)) {
            desk.virtualPointer.virtualMouseButton("BTN_RIGHT", true, allowVirtualPointer)
            desk.virtualPointer.virtualMouseButton("BTN_RIGHT", false, allowVirtualPointer)
            return
        }
        runCatching {
            val properties = MotionEvent.PointerProperties().apply {
                id = 0
                toolType = MotionEvent.TOOL_TYPE_MOUSE
            }
            val coordinates = MotionEvent.PointerCoords().apply {
                x = desk.desktopCursor.cursorX
                y = desk.desktopCursor.cursorY
                pressure = 1f
                size = 1f
            }
            val now = SystemClock.uptimeMillis()
            listOf(
                MotionEvent.ACTION_DOWN to MotionEvent.BUTTON_SECONDARY,
                MotionEvent.ACTION_BUTTON_PRESS to MotionEvent.BUTTON_SECONDARY,
                MotionEvent.ACTION_BUTTON_RELEASE to 0,
                MotionEvent.ACTION_UP to 0
            ).forEach { (action, buttons) ->
                val event = MotionEvent.obtain(
                    now, SystemClock.uptimeMillis(), action, 1,
                    arrayOf(properties), arrayOf(coordinates), 0, buttons,
                    1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0
                )
                runCatching {
                    MotionEvent::class.java.getMethod("setActionButton", Int::class.javaPrimitiveType)
                        .invoke(event, MotionEvent.BUTTON_SECONDARY)
                }
                check(desk.inputDispatcher.send(event, desk.targetDisplayId))
                event.recycle()
            }
            desk.desktopCursor.cursorView?.pulse()
        }.onFailure { Log.e(desk.logTag, "right click failed", it) }
    }

    private fun longPress() {
        if (dragHeld) return
        if (desk.virtualPointer.virtualMouseInputActive()) {
            desk.virtualPointer.virtualMouseButton("BTN_LEFT", true)
            desk.root?.postDelayed({ desk.virtualPointer.virtualMouseButton("BTN_LEFT", false) }, 600)
        } else {
            injectTouch(MotionEvent.ACTION_DOWN, desk.desktopCursor.cursorX, desk.desktopCursor.cursorY)
            desk.root?.postDelayed({ injectTouch(MotionEvent.ACTION_UP, desk.desktopCursor.cursorX, desk.desktopCursor.cursorY) }, 600)
        }
    }

    internal fun toggleDrag() {
        dragHeld = !dragHeld
        if (desk.virtualPointer.virtualMouseInputActive()) {
            desk.virtualPointer.virtualMouseButton("BTN_LEFT", dragHeld)
        } else {
            injectTouch(if (dragHeld) MotionEvent.ACTION_DOWN else MotionEvent.ACTION_UP, desk.desktopCursor.cursorX, desk.desktopCursor.cursorY)
        }
    }

    internal fun injectKey(keyCode: Int, metaState: Int = 0) {
        if (!desk.keyboardDeck.injectLaptopKeyboardEvent(keyCode, KeyEvent.ACTION_DOWN, metaState, 0)) return
        desk.keyboardDeck.injectLaptopKeyboardEvent(keyCode, KeyEvent.ACTION_UP, metaState, 0)
    }

    /** Forwards physical-keyboard input while preserving modifiers and repeat state. */
    internal fun forwardKeyEvent(source: KeyEvent): Boolean {
        if (desk.targetDisplayId < 0 || !desk.physicalInput.routePhysicalKeyboardToDextop) return false
        return runCatching {
            val event = KeyEvent(source)
            desk.inputDispatcher.send(event, desk.targetDisplayId)
        }.onFailure { Log.e(desk.logTag, "keyboard forwarding failed", it) }
            .getOrDefault(false)
    }

    /** Forwards physical mouse movement, buttons and wheel input to the desktop display. */
    internal fun forwardMouseEvent(source: MotionEvent, view: View): Boolean {
        if (!desk.physicalInput.routePhysicalMouseToDextop || desk.targetDisplayId < 0 || view.width <= 0 || view.height <= 0) return false
        return runCatching {
            val event = MotionEvent.obtain(source)
            val region = desk.externalOutputs.regionFor(view)
            event.transform(Matrix().apply {
                setScale(region.width() / view.width, region.height() / view.height)
                postTranslate(region.left, region.top)
            })
            check(desk.inputDispatcher.send(event, desk.targetDisplayId))

            desk.desktopCursor.cursorX = event.x.coerceIn(0f, desk.targetWidth - 1f)
            desk.desktopCursor.cursorY = event.y.coerceIn(0f, desk.targetHeight - 1f)
            event.recycle()
            true
        }.onFailure { Log.e(desk.logTag, "mouse forwarding failed", it) }
            .getOrDefault(false)
    }

    private fun injectTouch(action: Int, x: Float, y: Float) {
        if (desk.targetDisplayId < 0) return
        runCatching {
            val properties = MotionEvent.PointerProperties().apply {
                id = 0
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
            val coordinates = MotionEvent.PointerCoords().apply {
                this.x = x
                this.y = y
                pressure = if (action == MotionEvent.ACTION_UP) 0f else 1f
                size = 1f
            }
            val now = SystemClock.uptimeMillis()
            if (action == MotionEvent.ACTION_DOWN) injectedDownTime = now
            val event = MotionEvent.obtain(
                injectedDownTime,
                now,
                action,
                1,
                arrayOf(properties),
                arrayOf(coordinates),
                0,
                0,
                1f,
                1f,
                0,
                0,
                InputDevice.SOURCE_TOUCHSCREEN,
                0
            )
            check(desk.inputDispatcher.send(event, desk.targetDisplayId))
            event.recycle()
        }.onFailure { Log.e(desk.logTag, "input injection failed", it) }
    }

    private fun injectDirectTouch(source: MotionEvent, actionOverride: Int? = null) {
        if (desk.targetDisplayId < 0) return
        val view = desk.surfaceView ?: return
        if (view.width <= 0 || view.height <= 0) return
        runCatching {
            val action = actionOverride ?: source.action
            val actionMasked = action and MotionEvent.ACTION_MASK
            if (actionMasked == MotionEvent.ACTION_DOWN) {
                directInjectionDownTime = SystemClock.uptimeMillis()
                directSourceDownTime = source.downTime
            }
            if (directInjectionDownTime == 0L) {
                // Never send MOVE/UP without a synthetic DOWN identity.
                directInjectionDownTime = SystemClock.uptimeMillis()
                directSourceDownTime = source.downTime
            }
            // Keep the synthetic stream identity required by Samsung
            // InputManager, but preserve the source gesture's relative timing.
            // Replacing every eventTime with "now" collapses batched MOVE
            // events onto the same timestamp, so VelocityTracker sees almost
            // no release velocity and scrolling stops abruptly.
            val now = SystemClock.uptimeMillis()
            fun syntheticTime(sourceEventTime: Long): Long {
                val relative =
                    (sourceEventTime - directSourceDownTime).coerceAtLeast(0L)
                return (directInjectionDownTime + relative).coerceAtMost(now)
            }

            val region = desk.externalOutputs.regionFor(view)
            val scaleX = region.width() / view.width
            val scaleY = region.height() / view.height
            val properties = Array(source.pointerCount) { index ->
                MotionEvent.PointerProperties().apply {
                    source.getPointerProperties(index, this)
                    toolType = MotionEvent.TOOL_TYPE_FINGER
                }
            }

            fun scaledCoordinates(historyIndex: Int? = null) =
                Array(source.pointerCount) { index ->
                    MotionEvent.PointerCoords().apply {
                        if (historyIndex == null) {
                            source.getPointerCoords(index, this)
                        } else {
                            source.getHistoricalPointerCoords(index, historyIndex, this)
                        }
                        x = region.left + x * scaleX
                        y = region.top + y * scaleY
                    }
                }

            val hasMoveHistory =
                actionMasked == MotionEvent.ACTION_MOVE && source.historySize > 0
            val firstEventTime = if (hasMoveHistory) {
                syntheticTime(source.getHistoricalEventTime(0))
            } else {
                syntheticTime(source.eventTime)
            }
            val firstCoordinates =
                if (hasMoveHistory) scaledCoordinates(0) else scaledCoordinates()
            // deviceId=0 makes this a synthetic stream. Reusing the phone's
            // physical touchscreen device ID on another display causes Samsung
            // InputManager to reject subsequent one-finger events.
            val event = MotionEvent.obtain(
                directInjectionDownTime,
                firstEventTime,
                action,
                source.pointerCount,
                properties,
                firstCoordinates,
                source.metaState,
                source.buttonState,
                source.xPrecision * scaleX,
                source.yPrecision * scaleY,
                0,
                source.edgeFlags,
                InputDevice.SOURCE_TOUCHSCREEN,
                source.flags
            )
            if (hasMoveHistory) {
                for (historyIndex in 1 until source.historySize) {
                    event.addBatch(
                        syntheticTime(source.getHistoricalEventTime(historyIndex)),
                        scaledCoordinates(historyIndex),
                        source.metaState
                    )
                }
                event.addBatch(
                    syntheticTime(source.eventTime),
                    scaledCoordinates(),
                    source.metaState
                )
            }
            try {
                check(desk.inputDispatcher.send(event, desk.targetDisplayId)) {
                    moe.n4tsu.dextop.InlineStrings.format(moe.n4tsu.dextop.localText("uiInputManagerRejectedMultiTouchEventAction0"), event.actionMasked)
                }
                injectedDirectTouchActive = when (event.actionMasked) {
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> false
                    else -> true
                }
                lastInjectedDirectTouch?.recycle()
                lastInjectedDirectTouch = if (injectedDirectTouchActive) MotionEvent.obtain(event) else null
                if (!injectedDirectTouchActive) {
                    directInjectionDownTime = 0L
                    directSourceDownTime = 0L
                }
            } finally {
                event.recycle()
            }
        }.onFailure { error ->
            Log.e(desk.logTag, "multi-touch injection failed", error)
        }
    }

    /**
     * Cancels the injected stream with exactly the pointer IDs and pointer count
     * accepted by the target display. A gesture intercepted when its third finger
     * arrives must not forward that new three-pointer shape as CANCEL: the target
     * has only seen the preceding one/two-pointer stream and rejects the mismatch.
     */

    private fun cancelInjectedDirectTouch() {
        val previous = lastInjectedDirectTouch ?: return
        if (desk.targetDisplayId < 0) return
        val properties = Array(previous.pointerCount) { index ->
            MotionEvent.PointerProperties().also { previous.getPointerProperties(index, it) }
        }
        val coordinates = Array(previous.pointerCount) { index ->
            MotionEvent.PointerCoords().also { previous.getPointerCoords(index, it) }
        }
        val cancel = MotionEvent.obtain(
            previous.downTime,
            SystemClock.uptimeMillis(),
            MotionEvent.ACTION_CANCEL,
            previous.pointerCount,
            properties,
            coordinates,
            previous.metaState,
            previous.buttonState,
            previous.xPrecision,
            previous.yPrecision,
            0,
            previous.edgeFlags,
            InputDevice.SOURCE_TOUCHSCREEN,
            previous.flags
        )
        try {
            if (!desk.inputDispatcher.send(cancel, desk.targetDisplayId)) {
                Log.w(desk.logTag, "InputManager rejected exact direct-touch cancel pointers=${cancel.pointerCount}")
            }
        } finally {
            cancel.recycle()
            previous.recycle()
            lastInjectedDirectTouch = null
            injectedDirectTouchActive = false
            directInjectionDownTime = 0L
            directSourceDownTime = 0L
        }
    }

    /** Terminates both local and injected gesture state at an overlay boundary. */
    internal fun cancelDesktopTouchStream() {
        longPressRunnable?.let(inputGestureHandler::removeCallbacks)
        longPressRunnable = null

        if (desk.virtualTouchpad.virtualTouchpadActiveContactCount() > 0) {
            desk.virtualTouchpad.finishVirtualTouchpadGesture("desktop_touch_stream_cancelled", allowDirectTouch = true)
        }
        if (injectedDirectTouchActive) cancelInjectedDirectTouch()
        if (desk.targetDisplayId >= 0 && (directTouchHeld || scrolling || dragHeld) &&
            !desk.virtualPointer.virtualMouseInputActive()
        ) {
            injectTouch(MotionEvent.ACTION_CANCEL, desk.desktopCursor.cursorX, desk.desktopCursor.cursorY)
        }
        if (desk.virtualPointer.virtualMouseInputActive() && dragHeld) desk.virtualPointer.virtualMouseButton("BTN_LEFT", false)
        injectedDirectTouchActive = false
        directTouchHeld = false
        imeDirectTouchHeld = false
        scrolling = false
        dragHeld = false
        moved = false
        twoFinger = false
        twoFingerTravelX = 0f
        twoFingerTravelY = 0f
        threeFinger = false
        maxPointers = 0
        longPressTriggered = false
        threeFingerEdgeSwipe = false
        edgeMenuTriggered = false
        edgeGesturePrimaryPointerId = MotionEvent.INVALID_POINTER_ID
        edgeGestureLeadX = 0f
        edgeGestureLeadY = 0f
        injectedDownTime = 0L
        directInjectionDownTime = 0L
    }
}
