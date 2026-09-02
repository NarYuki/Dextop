package moe.n4tsu.dextop.input

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.LinearLayout
import kotlin.math.hypot

/**
 * Observes a one-finger path across laptop keys without changing normal taps,
 * long presses, repeats, or multi-touch modifier chords.
 */
internal class SwipeObservingKeyboardLayout(context: Context) : LinearLayout(context) {
    interface Listener {
        fun onSwipeStarted(firstKeyCode: Int, provisionalKeySent: Boolean)
        fun onSwipeFinished(points: List<PointF>)
        fun onSwipeCancelled()
        fun onTwoFingerNavigationStarted(firstKeyCode: Int) {}
        fun onTwoFingerDirectionChanged(keyCode: Int) {}
        fun onTwoFingerNavigationFinished() {}
    }

    var listener: Listener? = null
    var swipeEnabled: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (!value) cancelSwipeRecognition()
        }
    var twoFingerNavigationEnabled: Boolean = false
    var swipeTrailColor: Int = Color.rgb(190, 160, 255)
    private val trace = ArrayList<PointF>(64)
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = resources.displayMetrics.density * 4.5f
    }
    private val trailGlowPaint = Paint(trailPaint).apply {
        strokeWidth = resources.displayMetrics.density * 10f
    }
    private val trailPath = Path()
    private val slop = ViewConfiguration.get(context).scaledTouchSlop * 1.8f
    private val twoFingerSlop = ViewConfiguration.get(context).scaledTouchSlop * .65f
    private var firstKeyCode = KeyEvent.KEYCODE_UNKNOWN
    private var swiping = false
    private var cancelledChild = false
    private var deferredDown: MotionEvent? = null
    private var deferredDownSent = false
    private val dispatchDeferredDown = Runnable {
        val down = deferredDown ?: return@Runnable
        if (!swiping && !twoFingerNavigation) {
            super.dispatchTouchEvent(down)
            deferredDownSent = true
        }
    }
    private var trailAlpha = 0f
    private var trailFade: ValueAnimator? = null
    private var twoFingerNavigation = false
    private var twoFingerStartX = 0f
    private var twoFingerStartY = 0f
    private var twoFingerDirection = KeyEvent.KEYCODE_UNKNOWN
    /** 0=undecided, 1=horizontal, 2=vertical. Locked until all fingers lift. */
    private var twoFingerAxis = 0
    private val navigationOverlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 0, 0, 0)
        style = Paint.Style.FILL
    }
    private val navigationArrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    init {
        setWillNotDraw(false)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (!swipeEnabled && !twoFingerNavigationEnabled) return super.dispatchTouchEvent(event)
        // Some OEM ViewGroups split the POINTER_DOWN before the child stream
        // reaches this layout. Recover from the first two-pointer MOVE too.
        if (twoFingerNavigationEnabled && !twoFingerNavigation &&
            event.actionMasked == MotionEvent.ACTION_MOVE && event.pointerCount >= 2
        ) {
            val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
            super.dispatchTouchEvent(cancel)
            cancel.recycle()
            twoFingerNavigation = true
            val historical = event.historySize - 1
            twoFingerStartX = if (historical >= 0) {
                (event.getHistoricalX(0, historical) + event.getHistoricalX(1, historical)) / 2f
            } else (event.getX(0) + event.getX(1)) / 2f
            twoFingerStartY = if (historical >= 0) {
                (event.getHistoricalY(0, historical) + event.getHistoricalY(1, historical)) / 2f
            } else (event.getY(0) + event.getY(1)) / 2f
            twoFingerDirection = KeyEvent.KEYCODE_UNKNOWN
            twoFingerAxis = 0
            listener?.onTwoFingerNavigationStarted(firstKeyCode)
            reset()
        }
        // BlackBerry two-finger navigation can remain enabled independently
        // of swipe typing. In that configuration a single finger must be a
        // completely ordinary key stream: do not collect a trace, defer DOWN,
        // cancel the child, or consume MOVE/UP events.
        if (!swipeEnabled && !twoFingerNavigation && event.pointerCount < 2) {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                firstKeyCode = keyCodeAt(event.x, event.y)
            }
            return super.dispatchTouchEvent(event)
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                removeCallbacks(dispatchDeferredDown)
                deferredDown?.recycle()
                deferredDown = null
                deferredDownSent = false
                trailFade?.cancel()
                trace.clear()
                trace += PointF(event.x, event.y)
                trailAlpha = 0f
                firstKeyCode = keyCodeAt(event.x, event.y)
                swiping = false
                cancelledChild = false
                if (swipeEnabled && firstKeyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z) {
                    deferredDown = MotionEvent.obtain(event)
                    // Most intentional swipes cross touch slop within one or
                    // two frames.  This removes the provisional character
                    // without adding latency to quick taps (UP replays DOWN).
                    postDelayed(dispatchDeferredDown, 90L)
                    return true
                }
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (twoFingerNavigationEnabled && event.pointerCount == 2) {
                    if (swiping) listener?.onSwipeCancelled()
                    val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                    super.dispatchTouchEvent(cancel)
                    cancel.recycle()
                    twoFingerNavigation = true
                    twoFingerStartX = (event.getX(0) + event.getX(1)) / 2f
                    twoFingerStartY = (event.getY(0) + event.getY(1)) / 2f
                    twoFingerDirection = KeyEvent.KEYCODE_UNKNOWN
                    twoFingerAxis = 0
                    listener?.onTwoFingerNavigationStarted(firstKeyCode)
                    reset()
                    invalidate()
                    return true
                }
                if (swiping) listener?.onSwipeCancelled()
                reset()
                return super.dispatchTouchEvent(event)
            }

            MotionEvent.ACTION_MOVE -> {
                if (twoFingerNavigation) {
                    if (event.pointerCount < 2) {
                        finishTwoFingerNavigation()
                        return true
                    }
                    val x = (event.getX(0) + event.getX(1)) / 2f
                    val y = (event.getY(0) + event.getY(1)) / 2f
                    val dx = x - twoFingerStartX
                    val dy = y - twoFingerStartY
                    if (hypot(dx, dy) >= twoFingerSlop) {
                        if (twoFingerAxis == 0) {
                            twoFingerAxis = if (kotlin.math.abs(dx) >= kotlin.math.abs(dy)) 1 else 2
                        }
                        val parallel = if (twoFingerAxis == 1) kotlin.math.abs(dx) else kotlin.math.abs(dy)
                        val perpendicular = if (twoFingerAxis == 1) kotlin.math.abs(dy) else kotlin.math.abs(dx)
                        // Once an axis is chosen, perpendicular movement is
                        // neutral. Never animate or repeat a key from the
                        // other axis, and never silently keep the old key held.
                        val direction = if (parallel < twoFingerSlop || perpendicular > parallel) {
                            KeyEvent.KEYCODE_UNKNOWN
                        } else if (twoFingerAxis == 1) {
                            if (dx >= 0f) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
                        } else {
                            if (dy >= 0f) KeyEvent.KEYCODE_DPAD_DOWN else KeyEvent.KEYCODE_DPAD_UP
                        }
                        if (direction != twoFingerDirection) {
                            twoFingerDirection = direction
                            listener?.onTwoFingerDirectionChanged(direction)
                            invalidate()
                        }
                    }
                    return true
                }
                if (!swipeEnabled || event.pointerCount != 1 ||
                    firstKeyCode !in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z
                ) {
                    return super.dispatchTouchEvent(event)
                }
                val point = PointF(event.x, event.y)
                val previous = trace.lastOrNull()
                if (previous == null || hypot(point.x - previous.x, point.y - previous.y) >= 3f) {
                    trace += point
                }
                if (!swiping && trace.firstOrNull()?.let {
                        hypot(point.x - it.x, point.y - it.y) >= slop
                    } == true
                ) {
                    swiping = true
                    removeCallbacks(dispatchDeferredDown)
                    trailAlpha = 1f
                    invalidate()
                    // The child key has already received DOWN. Cancel it first so
                    // its virtual-hardware key stream always remains balanced.
                    if (deferredDownSent) {
                        val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                        super.dispatchTouchEvent(cancel)
                        cancel.recycle()
                        cancelledChild = true
                    }
                    listener?.onSwipeStarted(firstKeyCode, deferredDownSent)
                }
                if (swiping) {
                    invalidate()
                    return true
                }
                if (deferredDown != null && !deferredDownSent) return true
            }

            MotionEvent.ACTION_UP -> {
                if (twoFingerNavigation) {
                    finishTwoFingerNavigation()
                    return true
                }
                if (swiping) {
                    trace += PointF(event.x, event.y)
                    listener?.onSwipeFinished(trace.toList())
                    startTrailFade()
                    reset(clearTrace = false)
                    return true
                }
                deferredDown?.let { down ->
                    removeCallbacks(dispatchDeferredDown)
                    if (!deferredDownSent) super.dispatchTouchEvent(down)
                    super.dispatchTouchEvent(event)
                    down.recycle()
                    deferredDown = null
                    deferredDownSent = false
                    reset()
                    return true
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                if (twoFingerNavigation) {
                    finishTwoFingerNavigation()
                    return true
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                if (twoFingerNavigation) {
                    finishTwoFingerNavigation()
                    return true
                }
                if (swiping) listener?.onSwipeCancelled()
                if (swiping) startTrailFade() else clearTrail()
                reset(clearTrace = false)
                if (cancelledChild) return true
            }
        }
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (twoFingerNavigation && twoFingerDirection != KeyEvent.KEYCODE_UNKNOWN) {
            drawNavigationOverlay(canvas)
        }
        if (!swipeEnabled || trailAlpha <= 0f || trace.size < 2) return
        rebuildTrailPath()
        trailGlowPaint.color = Color.argb(
            (52 * trailAlpha).toInt(),
            Color.red(swipeTrailColor),
            Color.green(swipeTrailColor),
            Color.blue(swipeTrailColor),
        )
        trailPaint.color = Color.argb(
            (225 * trailAlpha).toInt(),
            Color.red(swipeTrailColor),
            Color.green(swipeTrailColor),
            Color.blue(swipeTrailColor),
        )
        canvas.drawPath(trailPath, trailGlowPaint)
        canvas.drawPath(trailPath, trailPaint)
    }

    private fun finishTwoFingerNavigation() {
        if (!twoFingerNavigation) return
        twoFingerNavigation = false
        twoFingerDirection = KeyEvent.KEYCODE_UNKNOWN
        twoFingerAxis = 0
        listener?.onTwoFingerNavigationFinished()
        invalidate()
    }

    private fun drawNavigationOverlay(canvas: Canvas) {
        canvas.drawRoundRect(
            RectF(0f, 0f, width.toFloat(), height.toFloat()),
            resources.displayMetrics.density * 18f,
            resources.displayMetrics.density * 18f,
            navigationOverlayPaint,
        )
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(width, height) * .24f
        val length = radius * .58f
        val shaft = radius * .18f
        val head = radius * .48f
        val rotation = when (twoFingerDirection) {
            KeyEvent.KEYCODE_DPAD_RIGHT -> 0f
            KeyEvent.KEYCODE_DPAD_DOWN -> 90f
            KeyEvent.KEYCODE_DPAD_LEFT -> 180f
            else -> -90f
        }
        val path = Path().apply {
            moveTo(cx - length, cy - shaft)
            lineTo(cx + length * .15f, cy - shaft)
            lineTo(cx + length * .15f, cy - head)
            lineTo(cx + length, cy)
            lineTo(cx + length * .15f, cy + head)
            lineTo(cx + length * .15f, cy + shaft)
            lineTo(cx - length, cy + shaft)
            close()
        }
        canvas.save()
        canvas.rotate(rotation, cx, cy)
        canvas.drawPath(path, navigationArrowPaint)
        canvas.restore()
    }

    /** A midpoint spline keeps the finger trace fluid instead of visibly angular. */
    private fun rebuildTrailPath() {
        trailPath.reset()
        val first = trace.first()
        trailPath.moveTo(first.x, first.y)
        for (index in 1 until trace.size) {
            val previous = trace[index - 1]
            val current = trace[index]
            val middleX = (previous.x + current.x) / 2f
            val middleY = (previous.y + current.y) / 2f
            trailPath.quadTo(previous.x, previous.y, middleX, middleY)
        }
        trace.lastOrNull()?.let { trailPath.lineTo(it.x, it.y) }
    }

    private fun startTrailFade() {
        trailFade?.cancel()
        trailFade = ValueAnimator.ofFloat(trailAlpha, 0f).apply {
            duration = 240L
            addUpdateListener {
                trailAlpha = it.animatedValue as Float
                invalidate()
                if (trailAlpha <= 0f) trace.clear()
            }
            start()
        }
    }

    private fun clearTrail() {
        trailFade?.cancel()
        trailAlpha = 0f
        trace.clear()
        invalidate()
    }

    /** Clears every observer-owned event without touching normal key input. */
    fun cancelSwipeRecognition() {
        if (swiping) listener?.onSwipeCancelled()
        removeCallbacks(dispatchDeferredDown)
        deferredDown?.let { down ->
            if (deferredDownSent || cancelledChild) {
                val cancel = MotionEvent.obtain(down).apply { action = MotionEvent.ACTION_CANCEL }
                super.dispatchTouchEvent(cancel)
                cancel.recycle()
            }
        }
        clearTrail()
        reset()
    }

    private fun keyCodeAt(x: Float, y: Float): Int {
        for (rowIndex in 0 until childCount) {
            val row = getChildAt(rowIndex) as? LinearLayout ?: continue
            val rowX = x - row.left
            val rowY = y - row.top
            if (rowY < 0 || rowY >= row.height) continue
            for (keyIndex in 0 until row.childCount) {
                val key = row.getChildAt(keyIndex)
                if (rowX >= key.left && rowX < key.right && rowY >= key.top && rowY < key.bottom) {
                    return key.tag as? Int ?: KeyEvent.KEYCODE_UNKNOWN
                }
            }
        }
        return KeyEvent.KEYCODE_UNKNOWN
    }

    private fun reset(clearTrace: Boolean = true) {
        removeCallbacks(dispatchDeferredDown)
        deferredDown?.recycle()
        deferredDown = null
        deferredDownSent = false
        if (clearTrace) trace.clear()
        firstKeyCode = KeyEvent.KEYCODE_UNKNOWN
        swiping = false
        cancelledChild = false
    }

}
