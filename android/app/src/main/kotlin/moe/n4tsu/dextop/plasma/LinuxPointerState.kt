package moe.n4tsu.dextop.plasma

import kotlin.math.abs
import kotlin.math.ln

/** Per-surface pointer state for Linux input: buttons held, gesture remainders, touch mode. */
internal class LinuxPointerState {
    /** MotionEvent.BUTTON_* bits last reported to X. */
    var buttons = 0
    var lastX = 0f
    var lastY = 0f
    var touchDownX = 0
    var touchDownY = 0
    /** A finger is down and its press has not been sent yet. */
    var touchPending = false
    var touchPressed = false
    var touchScrolling = false
    private var scrollX = 0f
    private var scrollY = 0f
    private var pinch = 0.0

    fun resetGesture(x: Float, y: Float) {
        lastX = x
        lastY = y
        scrollX = 0f
        scrollY = 0f
        pinch = 0.0
    }

    /**
     * Adds wheel movement in steps and returns the whole steps to send.
     * With [wheel], a single fractional event (a high-resolution wheel or
     * touchpad) still produces at least one step once a step's worth adds up.
     */
    fun addScroll(dx: Float, dy: Float, wheel: Boolean = false): Pair<Int, Int> {
        scrollX += dx
        scrollY += dy
        // A plain wheel click is ±1 per event; send it immediately.
        if (wheel && abs(dx) >= 1f) scrollX = dx
        if (wheel && abs(dy) >= 1f) scrollY = dy
        val stepsX = scrollX.toInt()
        val stepsY = scrollY.toInt()
        scrollX -= stepsX
        scrollY -= stepsY
        return stepsX.coerceIn(-10, 10) to stepsY.coerceIn(-10, 10)
    }

    /** Adds a pinch scale factor; returns zoom steps (positive zooms in, 10% per step). */
    fun addPinch(factor: Float): Int {
        if (factor <= 0f) return 0
        pinch += ln(factor.toDouble())
        val steps = (pinch / ln(1.1)).toInt()
        pinch -= steps * ln(1.1)
        return steps
    }

    fun startTouch(x: Int, y: Int) {
        touchDownX = x
        touchDownY = y
        touchPending = true
        touchPressed = false
        touchScrolling = false
    }

    fun movedBeyond(x: Int, y: Int, slop: Int) = abs(x - touchDownX) > slop || abs(y - touchDownY) > slop

    fun endTouch() {
        touchPending = false
        touchPressed = false
        touchScrolling = false
    }
}
