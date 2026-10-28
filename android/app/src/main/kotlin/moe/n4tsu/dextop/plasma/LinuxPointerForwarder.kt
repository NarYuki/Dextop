package moe.n4tsu.dextop.plasma

import android.view.MotionEvent
import android.view.InputDevice
import moe.n4tsu.dextop.linux.LinuxRelativeMotion

/**
 * Pointer input for Linux windows, shared by the Plasma desktop and Linux applications shown
 * as Android windows.
 */
internal object LinuxPointerForwarder {
    // MotionEvent classifications of touchpad gestures (API 34).
    private const val CLASSIFICATION_TWO_FINGER_SWIPE = 3
    private const val CLASSIFICATION_MULTI_FINGER_SWIPE = 4
    private const val CLASSIFICATION_PINCH = 5

    /** Finger travel per wheel step for scroll gestures. */
    private const val LINUX_SCROLL_STEP_DP = 18

    /** MotionEvent button bits to X button numbers (left, middle, right, back, forward). */
    val BUTTONS = listOf(
        MotionEvent.BUTTON_PRIMARY to 1, MotionEvent.BUTTON_TERTIARY to 2, MotionEvent.BUTTON_SECONDARY to 3,
        MotionEvent.BUTTON_BACK to 8, MotionEvent.BUTTON_FORWARD to 9,
    )

    fun cancel(state: LinuxPointerState, sendRelease: (button: Int) -> Unit) {
        for ((bit, button) in BUTTONS) {
            if (state.buttons and bit != 0) sendRelease(button)
        }
        if (state.touchPressed && state.buttons and MotionEvent.BUTTON_PRIMARY == 0) sendRelease(1)
        state.buttons = 0
        state.endTouch()
    }

    /** Captured events contain hardware deltas; never treat them as window coordinates. */
    fun captured(event: MotionEvent, state: LinuxPointerState, motion: LinuxRelativeMotion,
                 send: (action: Int, dx: Int, dy: Int, button: Int) -> Unit,
                 scroll: (dx: Int, dy: Int) -> Unit) {
        if (event.actionMasked == MotionEvent.ACTION_MOVE || event.actionMasked == MotionEvent.ACTION_HOVER_MOVE) {
            val touchpad = event.isFromSource(InputDevice.SOURCE_TOUCHPAD) &&
                !event.isFromSource(InputDevice.SOURCE_MOUSE_RELATIVE) && !event.isFromSource(InputDevice.SOURCE_MOUSE)
            for (sample in 0..event.historySize) {
                val current = sample == event.historySize
                val axisX = if (current) event.getAxisValue(MotionEvent.AXIS_RELATIVE_X)
                    else event.getHistoricalAxisValue(MotionEvent.AXIS_RELATIVE_X, sample)
                val axisY = if (current) event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y)
                    else event.getHistoricalAxisValue(MotionEvent.AXIS_RELATIVE_Y, sample)
                // DeX virtual/combined devices can identify as both mouse and touchpad.
                // Prefer explicit relative axes when supplied; otherwise captured mouse X/Y
                // already are deltas. A pure touchpad's X/Y remain absolute pad positions.
                val axes = touchpad || axisX != 0f || axisY != 0f
                val dx = if (axes) axisX else if (current) event.x else event.getHistoricalX(sample)
                val dy = if (axes) axisY else if (current) event.y else event.getHistoricalY(sample)
                val (x, y) = motion.add(dx, dy)
                if (x != 0 || y != 0) send(0, x, y, 0)
            }
        }
        if (event.actionMasked == MotionEvent.ACTION_SCROLL) {
            val (x, y) = state.addScroll(event.getAxisValue(MotionEvent.AXIS_HSCROLL),
                -event.getAxisValue(MotionEvent.AXIS_VSCROLL), wheel = true)
            if (x != 0 || y != 0) scroll(x, y)
        }
        val buttons = if (event.actionMasked == MotionEvent.ACTION_CANCEL) 0 else event.buttonState
        for ((bit, button) in BUTTONS) if ((state.buttons and bit != 0) != (buttons and bit != 0))
            send(if (buttons and bit != 0) 1 else 2, 0, 0, button)
        state.buttons = buttons
    }

    /**
     * Turns [event] (at X position [x], [y]) into X input: pointer and
     * buttons through [send], wheel steps through [scroll], keys through [key].
     *
     * Gestures become what Linux applications expect: a touchpad's two-finger
     * swipe and two fingers on a touch screen scroll, a touchpad pinch zooms
     * (Ctrl + wheel), and fine-grained wheels add up to whole steps.
     */
    fun forward(
        event: MotionEvent, x: Int, y: Int, state: LinuxPointerState, density: Float,
        send: (action: Int, x: Int, y: Int, button: Int) -> Unit,
        scroll: (dx: Int, dy: Int) -> Unit,
        key: (evdevCode: Int, down: Boolean) -> Unit,
    ) {
        runCatching {
            val step = LINUX_SCROLL_STEP_DP * density
            fun scrollBy(fingerDx: Float, fingerDy: Float) {
                // Content follows the fingers: moving them up scrolls down.
                val (dx, dy) = state.addScroll(-fingerDx / step, -fingerDy / step)
                if (dx != 0 || dy != 0) scroll(dx, dy)
            }
            val classification = if (android.os.Build.VERSION.SDK_INT >= 34) event.classification else 0
            when (classification) {
                CLASSIFICATION_TWO_FINGER_SWIPE -> {
                    // Android reports the swipe as one pointer that moves with the fingers.
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) state.resetGesture(event.x, event.y)
                    else if (event.actionMasked == MotionEvent.ACTION_MOVE) {
                        scrollBy(event.x - state.lastX, event.y - state.lastY)
                        state.lastX = event.x
                        state.lastY = event.y
                    }
                    return
                }
                CLASSIFICATION_MULTI_FINGER_SWIPE -> return
                CLASSIFICATION_PINCH -> {
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) state.resetGesture(event.x, event.y)
                    if (event.actionMasked == MotionEvent.ACTION_MOVE) {
                        val factor = event.getAxisValue(MotionEvent.AXIS_GESTURE_PINCH_SCALE_FACTOR)
                        val steps = state.addPinch(factor)
                        if (steps != 0) {
                            key(29, true) // KEY_LEFTCTRL
                            scroll(0, -steps)
                            key(29, false)
                        }
                    }
                    return
                }
            }
            if (event.actionMasked == MotionEvent.ACTION_SCROLL) {
                send(0, x, y, 0)
                val (dx, dy) = state.addScroll(
                    event.getAxisValue(MotionEvent.AXIS_HSCROLL), -event.getAxisValue(MotionEvent.AXIS_VSCROLL),
                    wheel = true,
                )
                if (dx != 0 || dy != 0) scroll(dx, dy)
                return
            }
            if (event.isFromSource(android.view.InputDevice.SOURCE_MOUSE)) {
                // Report every change of the button state; Android sends both
                // ACTION_DOWN and ACTION_BUTTON_PRESS for one mouse click.
                send(0, x, y, 0)
                val previous = state.buttons
                val current = if (event.actionMasked == MotionEvent.ACTION_CANCEL) 0 else event.buttonState
                for ((bit, button) in BUTTONS) {
                    val was = previous and bit != 0
                    val now = current and bit != 0
                    if (was != now) send(if (now) 1 else 2, x, y, button)
                }
                state.buttons = current
                return
            }
            // Touch: the press waits until the finger moves or lifts, so a
            // second finger can still turn the touch into a scroll.
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    state.startTouch(x, y)
                    send(0, x, y, 0)
                }
                // A second finger always turns the touch into scrolling (up, down, left and
                // right); a drag the first finger had started is let go first.
                MotionEvent.ACTION_POINTER_DOWN -> if (!state.touchScrolling) {
                    if (state.touchPressed) send(2, x, y, 1)
                    state.touchPending = false
                    state.touchPressed = false
                    state.touchScrolling = true
                    state.resetGesture(centroidX(event), centroidY(event))
                }
                MotionEvent.ACTION_MOVE -> when {
                    state.touchScrolling -> if (event.pointerCount >= 2) {
                        val cx = centroidX(event)
                        val cy = centroidY(event)
                        scrollBy(cx - state.lastX, cy - state.lastY)
                        state.lastX = cx
                        state.lastY = cy
                    }
                    state.touchPending -> if (state.movedBeyond(x, y, (6 * density).toInt())) {
                        send(1, state.touchDownX, state.touchDownY, 1)
                        state.touchPending = false
                        state.touchPressed = true
                        send(0, x, y, 0)
                    }
                    else -> send(0, x, y, 0)
                }
                MotionEvent.ACTION_UP -> {
                    if (state.touchPending) send(1, x, y, 1)
                    if (state.touchPending || state.touchPressed) send(2, x, y, 1)
                    state.endTouch()
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (state.touchPressed) send(2, x, y, 1)
                    state.endTouch()
                }
            }
        }
    }

    private fun centroidX(event: MotionEvent) = (0 until event.pointerCount).map(event::getX).average().toFloat()
    private fun centroidY(event: MotionEvent) = (0 until event.pointerCount).map(event::getY).average().toFloat()
}
