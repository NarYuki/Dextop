package moe.n4tsu.dextop

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import moe.n4tsu.dextop.input.PrivilegedInputProtocol
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/** One-piece Xbox-style controller surface with true multi-touch input. */
internal class XboxGamepadView(
    context: Context,
    private val onButtonChanged: (code: Int, pressed: Boolean) -> Unit,
    private val onAxisChanged: (code: Int, value: Int) -> Unit,
    private val onHaptic: () -> Unit,
) : View(context) {
    private enum class Control { LEFT_STICK, RIGHT_STICK, DPAD, A, B, X, Y, VIEW, MENU, HOME, NONE }
    private data class PointerState(
        val control: Control,
        val downX: Float,
        val downY: Float,
        var moved: Boolean = false,
        var directions: Set<Int> = emptySet(),
    )

    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val pointers = linkedMapOf<Int, PointerState>()
    private val pressedButtons = linkedSetOf<Int>()
    private val pressedDirections = linkedSetOf<Int>()
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(8, 8, 9) }
    private val bevelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(25, 25, 27) }
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(54, 54, 58); style = Paint.Style.STROKE; strokeWidth = 1.5f * density
    }
    private val darkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(18, 18, 20) }
    private val pressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(56, 56, 61) }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(83, 83, 88); style = Paint.Style.STROKE; strokeWidth = 2.4f * density
    }
    private val ridgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(112, 112, 116); style = Paint.Style.STROKE; strokeWidth = 1f * density
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
    }
    private val centers = mutableMapOf<Control, Pair<Float, Float>>()
    private var unit = 1f
    private var leftStickX = 0f
    private var leftStickY = 0f
    private var rightStickX = 0f
    private var rightStickY = 0f

    init {
        isClickable = true
        isFocusable = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val availableW = width - paddingLeft - paddingRight
        val availableH = height - paddingTop - paddingBottom
        unit = minOf(availableW / 100f, availableH / 65f).coerceAtLeast(1f)
        val ox = paddingLeft + (availableW - 100f * unit) / 2f
        val oy = paddingTop + (availableH - 65f * unit) / 2f
        fun x(v: Float) = ox + v * unit
        fun y(v: Float) = oy + v * unit
        fun center(c: Control, px: Float, py: Float) { centers[c] = x(px) to y(py) }
        center(Control.LEFT_STICK, 25f, 25f); center(Control.DPAD, 36f, 46f)
        center(Control.RIGHT_STICK, 63f, 47f); center(Control.Y, 79f, 20f)
        center(Control.X, 73f, 27f); center(Control.B, 85f, 27f); center(Control.A, 79f, 34f)
        center(Control.HOME, 50f, 15f); center(Control.VIEW, 44f, 28f); center(Control.MENU, 56f, 28f)

        val body = Path().apply {
            moveTo(x(18f), y(10f)); cubicTo(x(25f), y(5f), x(35f), y(10f), x(40f), y(12f))
            cubicTo(x(45f), y(13f), x(55f), y(13f), x(60f), y(12f))
            cubicTo(x(67f), y(7f), x(80f), y(7f), x(87f), y(14f))
            cubicTo(x(94f), y(22f), x(101f), y(47f), x(96f), y(57f))
            cubicTo(x(93f), y(63f), x(88f), y(64f), x(84f), y(59f))
            lineTo(x(72f), y(48f)); cubicTo(x(68f), y(45f), x(65f), y(57f), x(60f), y(61f))
            cubicTo(x(56f), y(64f), x(44f), y(64f), x(40f), y(61f))
            cubicTo(x(35f), y(57f), x(32f), y(45f), x(28f), y(48f)); lineTo(x(16f), y(59f))
            cubicTo(x(11f), y(64f), x(6f), y(62f), x(4f), y(56f))
            cubicTo(x(0f), y(45f), x(7f), y(22f), x(13f), y(14f)); close()
        }
        canvas.drawPath(body, bodyPaint); canvas.drawPath(body, edgePaint)
        val topBevel = Path().apply {
            moveTo(x(18f), y(10f)); lineTo(x(39f), y(12f)); lineTo(x(44f), y(21f))
            cubicTo(x(47f), y(24f), x(53f), y(24f), x(56f), y(21f)); lineTo(x(61f), y(12f))
            lineTo(x(82f), y(10f)); lineTo(x(70f), y(16f)); lineTo(x(30f), y(16f)); close()
        }
        canvas.drawPath(topBevel, bevelPaint)

        drawStick(canvas, Control.LEFT_STICK, leftStickX, leftStickY)
        drawDpad(canvas)
        drawStick(canvas, Control.RIGHT_STICK, rightStickX, rightStickY)
        drawFaceButton(canvas, Control.Y, "Y", Color.rgb(255, 229, 32))
        drawFaceButton(canvas, Control.X, "X", Color.rgb(24, 145, 220))
        drawFaceButton(canvas, Control.B, "B", Color.rgb(226, 45, 55))
        drawFaceButton(canvas, Control.A, "A", Color.rgb(35, 183, 84))
        drawCenterButton(canvas, Control.VIEW, "▣", 2.7f)
        drawCenterButton(canvas, Control.MENU, "≡", 2.7f)
        drawCenterButton(canvas, Control.HOME, "X", 4.8f, true)
    }

    private fun drawStick(canvas: Canvas, control: Control, vx: Float, vy: Float) {
        val (cx, cy) = centers.getValue(control); val r = 8.3f * unit
        canvas.drawCircle(cx, cy, r, darkPaint); canvas.drawCircle(cx, cy, r, edgePaint)
        for (i in 0 until 4) canvas.drawCircle(cx, cy, r * (.62f + i * .045f), ridgePaint)
        val travel = r * .42f
        canvas.drawCircle(cx + vx * travel, cy + vy * travel, r * .48f, if (isActive(control)) pressedPaint else bevelPaint)
        canvas.drawCircle(cx + vx * travel, cy + vy * travel, r * .48f, ringPaint)
    }

    private fun drawDpad(canvas: Canvas) {
        val (cx, cy) = centers.getValue(Control.DPAD); val arm = 3.1f * unit; val span = 13.5f * unit
        val path = Path().apply {
            moveTo(cx - arm, cy - span / 2); lineTo(cx + arm, cy - span / 2); lineTo(cx + arm, cy - arm)
            lineTo(cx + span / 2, cy - arm); lineTo(cx + span / 2, cy + arm); lineTo(cx + arm, cy + arm)
            lineTo(cx + arm, cy + span / 2); lineTo(cx - arm, cy + span / 2); lineTo(cx - arm, cy + arm)
            lineTo(cx - span / 2, cy + arm); lineTo(cx - span / 2, cy - arm); lineTo(cx - arm, cy - arm); close()
        }
        canvas.drawCircle(cx, cy, 8.5f * unit, darkPaint)
        canvas.drawPath(path, if (pressedDirections.isEmpty()) bevelPaint else pressedPaint); canvas.drawPath(path, edgePaint)
        canvas.drawCircle(cx, cy, 1.5f * unit, darkPaint)
    }

    private fun drawFaceButton(canvas: Canvas, control: Control, label: String, color: Int) {
        val (cx, cy) = centers.getValue(control); val r = 4.1f * unit
        canvas.drawCircle(cx, cy, r, if (isActive(control)) pressedPaint else darkPaint); canvas.drawCircle(cx, cy, r, edgePaint)
        labelPaint.color = color; labelPaint.textSize = 5f * unit
        canvas.drawText(label, cx, cy - (labelPaint.ascent() + labelPaint.descent()) / 2f, labelPaint)
    }

    private fun drawCenterButton(canvas: Canvas, control: Control, label: String, radius: Float, home: Boolean = false) {
        val (cx, cy) = centers.getValue(control); val r = radius * unit
        if (home) { labelPaint.color = Color.WHITE; canvas.drawCircle(cx, cy, r * 1.25f, ColorPaint) }
        canvas.drawCircle(cx, cy, r, if (isActive(control)) pressedPaint else darkPaint); canvas.drawCircle(cx, cy, r, edgePaint)
        labelPaint.color = if (home) Color.WHITE else Color.LTGRAY; labelPaint.textSize = r * if (home) 1.15f else 1.05f
        canvas.drawText(label, cx, cy - (labelPaint.ascent() + labelPaint.descent()) / 2f, labelPaint)
    }

    private val ColorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(235, 235, 235) }
    private fun isActive(control: Control) = pointers.values.any { it.control == control }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> addPointer(event, event.actionIndex)
            MotionEvent.ACTION_MOVE -> for (i in 0 until event.pointerCount) updatePointer(event.getPointerId(i), event.getX(i), event.getY(i))
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> removePointer(event.getPointerId(event.actionIndex), event.actionMasked == MotionEvent.ACTION_UP)
            MotionEvent.ACTION_CANCEL -> cancelAll()
        }
        invalidate(); return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    private fun addPointer(event: MotionEvent, index: Int) {
        val id = event.getPointerId(index); val px = event.getX(index); val py = event.getY(index)
        val control = hitTest(px, py); val state = PointerState(control, px, py)
        pointers[id] = state
        when (control) {
            Control.DPAD -> { state.directions = directionsAt(px, py); updateDirections() }
            Control.LEFT_STICK, Control.RIGHT_STICK -> updateStick(control, px, py)
            else -> buttonCode(control)?.let { pressButton(it) }
        }
        if (control != Control.NONE) onHaptic()
    }

    private fun updatePointer(id: Int, px: Float, py: Float) {
        val state = pointers[id] ?: return
        if (hypot((px - state.downX).toDouble(), (py - state.downY).toDouble()) > touchSlop) state.moved = true
        when (state.control) {
            Control.DPAD -> { state.directions = directionsAt(px, py); updateDirections() }
            Control.LEFT_STICK, Control.RIGHT_STICK -> updateStick(state.control, px, py)
            else -> Unit
        }
    }

    private fun removePointer(id: Int, click: Boolean) {
        val state = pointers.remove(id) ?: return
        when (state.control) {
            Control.DPAD -> updateDirections()
            Control.LEFT_STICK -> { resetStick(true); if (!state.moved) pulse(PrivilegedInputProtocol.GAMEPAD_BUTTON_L3) }
            Control.RIGHT_STICK -> { resetStick(false); if (!state.moved) pulse(PrivilegedInputProtocol.GAMEPAD_BUTTON_R3) }
            else -> buttonCode(state.control)?.let { releaseButtonIfUnused(it) }
        }
        if (click) performClick()
    }

    private fun cancelAll() {
        pressedButtons.toList().forEach { onButtonChanged(it, false) }; pressedButtons.clear()
        pressedDirections.toList().forEach { onButtonChanged(it, false) }; pressedDirections.clear()
        pointers.clear(); resetStick(true); resetStick(false)
    }

    private fun hitTest(px: Float, py: Float): Control {
        val order = listOf(Control.A, Control.B, Control.X, Control.Y, Control.HOME, Control.VIEW, Control.MENU,
            Control.LEFT_STICK, Control.RIGHT_STICK, Control.DPAD)
        return order.firstOrNull { c ->
            val (cx, cy) = centers[c] ?: return@firstOrNull false
            val radius = when (c) { Control.LEFT_STICK, Control.RIGHT_STICK, Control.DPAD -> 10f; else -> 5.4f } * unit
            hypot((px - cx).toDouble(), (py - cy).toDouble()) <= radius
        } ?: Control.NONE
    }

    private fun directionsAt(px: Float, py: Float): Set<Int> {
        val (cx, cy) = centers.getValue(Control.DPAD); val dx = px - cx; val dy = py - cy; val dead = 1.5f * unit
        if (hypot(dx.toDouble(), dy.toDouble()) < dead) return emptySet()
        val h = if (dx < -dead) PrivilegedInputProtocol.GAMEPAD_BUTTON_DPAD_LEFT else if (dx > dead) PrivilegedInputProtocol.GAMEPAD_BUTTON_DPAD_RIGHT else null
        val v = if (dy < -dead) PrivilegedInputProtocol.GAMEPAD_BUTTON_DPAD_UP else if (dy > dead) PrivilegedInputProtocol.GAMEPAD_BUTTON_DPAD_DOWN else null
        val diagonal = abs(dx) > abs(dy) * .42f && abs(dy) > abs(dx) * .42f
        return if (diagonal) setOfNotNull(h, v) else if (abs(dx) >= abs(dy)) setOfNotNull(h) else setOfNotNull(v)
    }

    private fun updateDirections() {
        val next = pointers.values.flatMap { it.directions }.toSet()
        (pressedDirections - next).forEach { onButtonChanged(it, false) }
        (next - pressedDirections).forEach { onButtonChanged(it, true) }
        pressedDirections.clear(); pressedDirections.addAll(next)
    }

    private fun updateStick(control: Control, px: Float, py: Float) {
        val (cx, cy) = centers.getValue(control); val range = 7f * unit
        var vx = ((px - cx) / range).coerceIn(-1f, 1f); var vy = ((py - cy) / range).coerceIn(-1f, 1f)
        val length = hypot(vx.toDouble(), vy.toDouble()).toFloat()
        if (length > 1f) { vx /= length; vy /= length }
        if (length < .08f) { vx = 0f; vy = 0f }
        if (control == Control.LEFT_STICK) { leftStickX = vx; leftStickY = vy } else { rightStickX = vx; rightStickY = vy }
        val xCode = if (control == Control.LEFT_STICK) PrivilegedInputProtocol.GAMEPAD_AXIS_LEFT_X else PrivilegedInputProtocol.GAMEPAD_AXIS_RIGHT_X
        val yCode = if (control == Control.LEFT_STICK) PrivilegedInputProtocol.GAMEPAD_AXIS_LEFT_Y else PrivilegedInputProtocol.GAMEPAD_AXIS_RIGHT_Y
        onAxisChanged(xCode, (vx * 32767f).roundToInt()); onAxisChanged(yCode, (vy * 32767f).roundToInt())
    }

    private fun resetStick(left: Boolean) {
        if (left) { leftStickX = 0f; leftStickY = 0f; onAxisChanged(PrivilegedInputProtocol.GAMEPAD_AXIS_LEFT_X, 0); onAxisChanged(PrivilegedInputProtocol.GAMEPAD_AXIS_LEFT_Y, 0) }
        else { rightStickX = 0f; rightStickY = 0f; onAxisChanged(PrivilegedInputProtocol.GAMEPAD_AXIS_RIGHT_X, 0); onAxisChanged(PrivilegedInputProtocol.GAMEPAD_AXIS_RIGHT_Y, 0) }
    }

    private fun buttonCode(control: Control) = when (control) {
        Control.A -> PrivilegedInputProtocol.GAMEPAD_BUTTON_A; Control.B -> PrivilegedInputProtocol.GAMEPAD_BUTTON_B
        Control.X -> PrivilegedInputProtocol.GAMEPAD_BUTTON_X; Control.Y -> PrivilegedInputProtocol.GAMEPAD_BUTTON_Y
        Control.VIEW -> PrivilegedInputProtocol.GAMEPAD_BUTTON_SELECT; Control.MENU -> PrivilegedInputProtocol.GAMEPAD_BUTTON_START
        Control.HOME -> PrivilegedInputProtocol.GAMEPAD_BUTTON_HOME; else -> null
    }
    private fun pressButton(code: Int) { if (pressedButtons.add(code)) onButtonChanged(code, true) }
    private fun releaseButtonIfUnused(code: Int) {
        if (pointers.values.none { buttonCode(it.control) == code } && pressedButtons.remove(code)) onButtonChanged(code, false)
    }
    private fun pulse(code: Int) { onButtonChanged(code, true); onButtonChanged(code, false) }
}

/**
 * A four-way D-pad that also exposes diagonal chords. A single finger can
 * select two neighboring directions, and multiple fingers are merged into
 * the same directional state. The native gamepad writer turns those states
 * into both DPAD key events and HAT_X/HAT_Y values.
 */
internal class GamepadDpadView(
    context: Context,
    private val onDirectionChanged: (code: Int, pressed: Boolean) -> Unit,
    private val upCode: Int,
    private val downCode: Int,
    private val leftCode: Int,
    private val rightCode: Int,
    private val baseColor: Int,
    private val selectedColor: Int,
    private val ringColor: Int,
    private val textColor: Int,
) : View(context) {
    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val pointerDirections = linkedMapOf<Int, Set<Int>>()
    private val pressedDirections = linkedSetOf<Int>()

    init {
        isClickable = true
        basePaint.color = baseColor
        selectedPaint.color = selectedColor
        ringPaint.color = ringColor
        ringPaint.style = Paint.Style.STROKE
        ringPaint.strokeWidth = resources.displayMetrics.density * 1.5f
        textPaint.color = textColor
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = minOf(width, height).toFloat()
        val centerX = width / 2f
        val centerY = height / 2f
        val armWidth = size * .29f
        val span = size * .82f
        val radius = resources.displayMetrics.density * 9f
        val vertical = RectF(centerX - armWidth / 2f, centerY - span / 2f, centerX + armWidth / 2f, centerY + span / 2f)
        val horizontal = RectF(centerX - span / 2f, centerY - armWidth / 2f, centerX + span / 2f, centerY + armWidth / 2f)

        canvas.drawRoundRect(vertical, radius, radius, basePaint)
        canvas.drawRoundRect(horizontal, radius, radius, basePaint)
        canvas.drawRoundRect(vertical, radius, radius, ringPaint)
        canvas.drawRoundRect(horizontal, radius, radius, ringPaint)

        fun arm(bounds: RectF, code: Int, label: String, verticalArm: Boolean) {
            val selectedBounds = if (verticalArm) {
                if (code == upCode) RectF(bounds.left, bounds.top, bounds.right, centerY)
                else RectF(bounds.left, centerY, bounds.right, bounds.bottom)
            } else {
                if (code == leftCode) RectF(bounds.left, bounds.top, centerX, bounds.bottom)
                else RectF(centerX, bounds.top, bounds.right, bounds.bottom)
            }
            if (code in pressedDirections) {
                canvas.drawRoundRect(selectedBounds, radius, radius, selectedPaint)
            }
            val baseline = selectedBounds.centerY() - (textPaint.ascent() + textPaint.descent()) / 2f
            canvas.drawText(label, selectedBounds.centerX(), baseline, textPaint)
        }
        arm(vertical, upCode, "▲", true)
        arm(horizontal, leftCode, "◀", false)
        arm(horizontal, rightCode, "▶", false)
        arm(vertical, downCode, "▼", true)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pointerDirections[event.getPointerId(0)] = directionsAt(event.x, event.y)
                updatePressedDirections()
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN,
            MotionEvent.ACTION_MOVE -> {
                for (index in 0 until event.pointerCount) {
                    pointerDirections[event.getPointerId(index)] =
                        directionsAt(event.getX(index), event.getY(index))
                }
                updatePressedDirections()
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                pointerDirections.remove(event.getPointerId(event.actionIndex))
                updatePressedDirections()
                return true
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                pointerDirections.clear()
                updatePressedDirections()
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun directionsAt(x: Float, y: Float): Set<Int> {
        val centerX = width / 2f
        val centerY = height / 2f
        val dx = x - centerX
        val dy = y - centerY
        val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        val deadZone = minOf(width, height) * .12f
        if (distance <= deadZone) return emptySet()
        val horizontal = when {
            dx < -deadZone -> leftCode
            dx > deadZone -> rightCode
            else -> null
        }
        val vertical = when {
            dy < -deadZone -> upCode
            dy > deadZone -> downCode
            else -> null
        }
        // Preserve both components around the diagonal sectors. This makes
        // one-finger up-left/up-right/down-left/down-right input possible.
        val ratio = .45f
        val diagonal = kotlin.math.abs(dx) > kotlin.math.abs(dy) * ratio &&
                kotlin.math.abs(dy) > kotlin.math.abs(dx) * ratio
        return if (diagonal) {
            setOfNotNull(horizontal, vertical)
        } else if (kotlin.math.abs(dx) >= kotlin.math.abs(dy)) {
            setOfNotNull(horizontal)
        } else {
            setOfNotNull(vertical)
        }
    }

    private fun updatePressedDirections() {
        val next = pointerDirections.values.flatten().toSet()
        (pressedDirections - next).forEach { onDirectionChanged(it, false) }
        (next - pressedDirections).forEach { onDirectionChanged(it, true) }
        pressedDirections.clear()
        pressedDirections.addAll(next)
        invalidate()
    }
}

/** A compact analog stick that reports a normalized -1..1 pair. */
internal class GamepadStickView(
    context: Context,
    private val onChange: (x: Float, y: Float) -> Unit,
    private val onRelease: () -> Unit,
    private val baseColor: Int,
    private val ringColor: Int,
    private val knobColor: Int,
) : View(context) {
    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var xValue = 0f
    private var yValue = 0f
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var downX = 0f
    private var downY = 0f
    private var movedBeyondTapSlop = false
    private val tapSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    init {
        isClickable = true
        basePaint.color = baseColor
        ringPaint.color = ringColor
        ringPaint.style = Paint.Style.STROKE
        ringPaint.strokeWidth = resources.displayMetrics.density * 2f
        knobPaint.color = knobColor
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val radius = (width.coerceAtMost(height) / 2f).coerceAtLeast(1f)
        val centerX = width / 2f
        val centerY = height / 2f
        canvas.drawCircle(centerX, centerY, radius * .82f, basePaint)
        canvas.drawCircle(centerX, centerY, radius * .82f, ringPaint)
        val knobRadius = radius * .34f
        val travel = radius * .47f
        canvas.drawCircle(centerX + xValue * travel, centerY + yValue * travel, knobRadius, knobPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                downX = event.x
                downY = event.y
                movedBeyondTapSlop = false
                updateValue(event.x, event.y)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(activePointerId)
                if (index >= 0) {
                    val x = event.getX(index)
                    val y = event.getY(index)
                    if (hypot((x - downX).toDouble(), (y - downY).toDouble()) > tapSlop) {
                        movedBeyondTapSlop = true
                    }
                    updateValue(x, y)
                }
                return true
            }

            // A parent may deliver a second finger's event to the original
            // child. Do not reinterpret that finger's coordinates as stick
            // movement (this was the source of L2-area -> R3 ghost motion).
            MotionEvent.ACTION_POINTER_DOWN -> return true

            MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) == activePointerId) {
                    activePointerId = MotionEvent.INVALID_POINTER_ID
                    xValue = 0f
                    yValue = 0f
                    onRelease()
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL
            -> {
                activePointerId = MotionEvent.INVALID_POINTER_ID
                xValue = 0f
                yValue = 0f
                onRelease()
                invalidate()
                if (event.actionMasked == MotionEvent.ACTION_UP && !movedBeyondTapSlop) performClick()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun updateValue(x: Float, y: Float) {
        val centerX = width / 2f
        val centerY = height / 2f
        val travel = (width.coerceAtMost(height) / 2f * .62f).coerceAtLeast(1f)
        var nextX = ((x - centerX) / travel).coerceIn(-1f, 1f)
        var nextY = ((y - centerY) / travel).coerceIn(-1f, 1f)
        val length = hypot(nextX.toDouble(), nextY.toDouble()).toFloat()
        if (length < .08f) {
            nextX = 0f
            nextY = 0f
        }
        xValue = nextX
        yValue = nextY
        onChange(nextX, nextY)
        invalidate()
    }
}

/** A horizontal or vertical analog trigger reporting 0..1023. */
internal class GamepadTriggerView(
    context: Context,
    private val horizontal: Boolean,
    private val onChange: (value: Int) -> Unit,
    private val onRelease: () -> Unit,
    private val baseColor: Int,
    private val fillColor: Int,
    private val ringColor: Int,
) : View(context) {
    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var value = 0
    private var activePointerId = MotionEvent.INVALID_POINTER_ID

    init {
        isClickable = true
        basePaint.color = baseColor
        fillPaint.color = fillColor
        ringPaint.color = ringColor
        ringPaint.style = Paint.Style.STROKE
        ringPaint.strokeWidth = resources.displayMetrics.density * 2f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val inset = resources.displayMetrics.density * 2f
        val bounds = RectF(inset, inset, width - inset, height - inset)
        canvas.drawRoundRect(bounds, height / 2f, height / 2f, basePaint)
        if (value > 0) {
            if (horizontal) {
                canvas.drawRoundRect(
                    RectF(bounds.left, bounds.top, bounds.left + bounds.width() * value / 1023f, bounds.bottom),
                    height / 2f,
                    height / 2f,
                    fillPaint,
                )
            } else {
                canvas.drawRoundRect(
                    RectF(bounds.left, bounds.bottom - bounds.height() * value / 1023f, bounds.right, bounds.bottom),
                    width / 2f,
                    width / 2f,
                    fillPaint,
                )
            }
        }
        canvas.drawRoundRect(bounds, height / 2f, height / 2f, ringPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                updateValue(event.x, event.y)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(activePointerId)
                if (index >= 0) updateValue(event.getX(index), event.getY(index))
                return true
            }

            // Ignore additional fingers delivered to this child by the
            // containing ViewGroup; only the trigger's original pointer may
            // change its value.
            MotionEvent.ACTION_POINTER_DOWN -> return true

            MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) == activePointerId) {
                    activePointerId = MotionEvent.INVALID_POINTER_ID
                    value = 0
                    onRelease()
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                activePointerId = MotionEvent.INVALID_POINTER_ID
                value = 0
                onRelease()
                invalidate()
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun updateValue(x: Float, y: Float) {
        value = if (horizontal) {
            (x / width.coerceAtLeast(1) * 1023f).toInt()
        } else {
            ((1f - y / height.coerceAtLeast(1)) * 1023f).toInt()
        }.coerceIn(0, 1023)
        onChange(value)
        invalidate()
    }
}
