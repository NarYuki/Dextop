package moe.n4tsu.dextop.plasma

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout

internal fun t(key: String): String = moe.n4tsu.dextop.NativeStrings.text(key)

/** Rounded Plasma dialog/popup background with the Breeze outline. */
@SuppressLint("ViewConstructor")
internal class PlasmaPopupFrame(
    context: Context,
    private val palette: PlasmaPalette,
    private val radiusDp: Float = 8f,
    var onOutside: (() -> Unit)? = null,
) : FrameLayout(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f }
    private val box = RectF()

    init {
        setWillNotDraw(false)
        clipToOutline = false
    }

    override fun onDraw(canvas: Canvas) {
        val radius = radiusDp * resources.displayMetrics.density
        box.set(.5f, .5f, width - .5f, height - .5f)
        fill.color = palette.popup
        canvas.drawRoundRect(box, radius, radius, fill)
        stroke.color = palette.frameOutline
        canvas.drawRoundRect(box, radius, radius, stroke)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
            onOutside?.invoke()
            return true
        }
        return true
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_ESCAPE && event.action == KeyEvent.ACTION_UP) {
            onOutside?.invoke()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}

/** List item used by Kickoff, KRunner and context menus. */
@SuppressLint("ViewConstructor")
internal class PlasmaRow(
    context: Context,
    private val palette: PlasmaPalette,
    var icon: Drawable?,
    var text: String,
    var subtitle: String? = null,
    private val heightDp: Float = 34f,
    private val iconDp: Float = 22f,
    var checked: Boolean? = null,
    var trailing: String? = null,
    private val onRightClick: ((Float, Float) -> Unit)? = null,
    var onHoverSelect: (() -> Unit)? = null,
    private val onClick: () -> Unit,
) : View(context) {
    private val density = resources.displayMetrics.density
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = PlasmaTheme.sans }
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f }
    private var hover = 0f
    private var animator: ValueAnimator? = null
    var highlighted = false
        set(value) { field = value; invalidate() }
    private val box = RectF()
    private val checkGlyph = GlyphDrawable(Glyph.CHECK, palette.accent, 2f)
    var enabledRow = true

    init {
        isFocusable = true
        setOnHoverListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_HOVER_ENTER -> { animateTo(1f); onHoverSelect?.invoke() }
                MotionEvent.ACTION_HOVER_EXIT -> animateTo(0f)
            }
            false
        }
    }

    private fun animateTo(target: Float) {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(hover, target).apply {
            duration = PlasmaTheme.SHORT_MS
            addUpdateListener { hover = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec),
            ((if (subtitle != null) heightDp + 12f else heightDp) * density).toInt(),
        )
    }

    override fun onDraw(canvas: Canvas) {
        val active = maxOf(hover, if (highlighted) 1f else 0f)
        box.set(density * 2, density, width - density * 2, height - density)
        if (active > 0f && enabledRow) {
            backgroundPaint.color = PlasmaTheme.withAlpha(palette.accent, (0x33 * active).toInt())
            canvas.drawRoundRect(box, density * 4, density * 4, backgroundPaint)
            strokePaint.color = PlasmaTheme.withAlpha(palette.accent, (0xAA * active).toInt())
            canvas.drawRoundRect(box, density * 4, density * 4, strokePaint)
        }
        var x = density * 10
        checked?.let { isChecked ->
            if (isChecked) {
                checkGlyph.setBounds(x.toInt(), (height / 2f - 8 * density).toInt(), (x + 16 * density).toInt(), (height / 2f + 8 * density).toInt())
                checkGlyph.draw(canvas)
            }
            x += density * 22
        }
        icon?.let {
            val size = iconDp * density
            box.set(x, height / 2f - size / 2f, x + size, height / 2f + size / 2f)
            canvas.drawIcon(it, box, if (enabledRow) 255 else 110)
            x += size + density * 10
        }
        textPaint.textSize = 13.5f * density
        textPaint.color = if (enabledRow) palette.text else palette.disabledText
        val trailingWidth = trailing?.let { textPaint.measureText(it) + density * 20 } ?: 0f
        val available = width - x - density * 10 - trailingWidth
        val title = TextUtilsCompat.ellipsize(text, textPaint, available)
        if (subtitle == null) {
            canvas.drawText(title, x, height / 2f - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint)
        } else {
            canvas.drawText(title, x, height / 2f - density * 2, textPaint)
            textPaint.textSize = 11.5f * density
            textPaint.color = palette.inactiveText
            canvas.drawText(TextUtilsCompat.ellipsize(subtitle!!, textPaint, available), x, height / 2f + density * 13, textPaint)
        }
        trailing?.let {
            textPaint.textSize = 12f * density
            textPaint.color = palette.inactiveText
            canvas.drawText(it, width - textPaint.measureText(it) - density * 12, height / 2f - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!enabledRow) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (event.isButtonPressed(MotionEvent.BUTTON_SECONDARY)) {
                    onRightClick?.invoke(event.rawX, event.rawY)
                    return true
                }
                highlighted = true
            }
            MotionEvent.ACTION_UP -> {
                highlighted = false
                if (event.x in 0f..width.toFloat() && event.y in 0f..height.toFloat()) {
                    if (event.eventTime - event.downTime > 600 && onRightClick != null) onRightClick.invoke(event.rawX, event.rawY)
                    else onClick()
                }
            }
            MotionEvent.ACTION_CANCEL -> highlighted = false
        }
        return true
    }

    fun activate() = onClick()
}

/** Kickoff "Favorites" grid tile: large icon with a two-line label. */
@SuppressLint("ViewConstructor")
internal class PlasmaGridTile(
    context: Context,
    private val palette: PlasmaPalette,
    private val icon: Drawable?,
    private val label: String,
    private val onRightClick: (Float, Float) -> Unit,
    private val onClick: () -> Unit,
) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = PlasmaTheme.sans }
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f }
    private var hover = 0f
    private var animator: ValueAnimator? = null
    private val box = RectF()

    init {
        setOnHoverListener { _, event ->
            val target = if (event.actionMasked == MotionEvent.ACTION_HOVER_EXIT) 0f else 1f
            if (target != hover) {
                animator?.cancel()
                animator = ValueAnimator.ofFloat(hover, target).apply {
                    duration = PlasmaTheme.SHORT_MS
                    addUpdateListener { hover = it.animatedValue as Float; invalidate() }
                    start()
                }
            }
            false
        }
    }

    override fun onDraw(canvas: Canvas) {
        box.set(density * 3, density * 3, width - density * 3, height - density * 3)
        if (hover > 0f) {
            bg.color = PlasmaTheme.withAlpha(palette.accent, (0x33 * hover).toInt())
            canvas.drawRoundRect(box, density * 5, density * 5, bg)
            stroke.color = PlasmaTheme.withAlpha(palette.accent, (0xAA * hover).toInt())
            canvas.drawRoundRect(box, density * 5, density * 5, stroke)
        }
        val size = density * 48
        box.set(width / 2f - size / 2f, density * 10, width / 2f + size / 2f, density * 10 + size)
        canvas.drawIcon(icon, box)
        paint.textSize = density * 12
        paint.color = palette.text
        val lines = TextUtilsCompat.wrapTwoLines(label, paint, width - density * 10)
        lines.forEachIndexed { index, line ->
            canvas.drawText(line, width / 2f, density * (74 + index * 15), paint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> if (event.isButtonPressed(MotionEvent.BUTTON_SECONDARY)) {
                onRightClick(event.rawX, event.rawY); return true
            }
            MotionEvent.ACTION_UP -> if (event.x in 0f..width.toFloat() && event.y in 0f..height.toFloat()) {
                if (event.eventTime - event.downTime > 600) onRightClick(event.rawX, event.rawY) else onClick()
            }
        }
        return true
    }
}

internal object TextUtilsCompat {
    fun ellipsize(text: String, paint: Paint, width: Float): String {
        if (width <= 0f) return ""
        if (paint.measureText(text) <= width) return text
        var end = text.length
        val dots = paint.measureText("…")
        while (end > 0 && paint.measureText(text, 0, end) + dots > width) end--
        return text.substring(0, end) + "…"
    }

    fun wrapTwoLines(text: String, paint: Paint, width: Float): List<String> {
        if (paint.measureText(text) <= width) return listOf(text)
        var split = text.length
        while (split > 0 && paint.measureText(text, 0, split) > width) split--
        val space = text.lastIndexOf(' ', split).takeIf { it > 0 } ?: split
        return listOf(text.substring(0, space).trim(), ellipsize(text.substring(space).trim(), paint, width))
    }
}

/** Plasma search field: rounded, with the search glyph and an accent focus ring. */
@SuppressLint("ViewConstructor")
internal class PlasmaSearchField(
    context: Context,
    private val palette: PlasmaPalette,
    hint: String,
) : EditText(context) {
    private val density = resources.displayMetrics.density
    private val frame = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glyph = GlyphDrawable(Glyph.SEARCH, palette.inactiveText, 1.7f)
    private val box = RectF()

    init {
        background = null
        this.hint = hint
        setHintTextColor(palette.inactiveText)
        setTextColor(palette.text)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
        typeface = PlasmaTheme.sans
        isSingleLine = true
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        imeOptions = EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        gravity = Gravity.CENTER_VERTICAL
        setPadding((34 * density).toInt(), 0, (10 * density).toInt(), 0)
        highlightColor = PlasmaTheme.withAlpha(palette.accent, 0x66)
    }

    override fun onDraw(canvas: Canvas) {
        box.set(.5f, .5f, width - .5f, height - .5f)
        box.offset(scrollX.toFloat(), 0f)
        frame.color = palette.viewBackground
        canvas.drawRoundRect(box, density * 4, density * 4, frame)
        outline.strokeWidth = if (isFocused) density * 1.5f else 1f
        outline.color = if (isFocused) palette.accent else palette.separator
        canvas.drawRoundRect(box, density * 4, density * 4, outline)
        val size = (16 * density).toInt()
        val left = scrollX + (10 * density).toInt()
        glyph.setBounds(left, height / 2 - size / 2, left + size, height / 2 + size / 2)
        glyph.draw(canvas)
        super.onDraw(canvas)
    }
}

/** Breeze slider: thin groove, accent fill and a round handle. */
@SuppressLint("ViewConstructor")
internal class PlasmaSlider(
    context: Context,
    private val palette: PlasmaPalette,
    var value: Float,
    private val onChange: (Float, Boolean) -> Unit,
) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (28 * density).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val left = density * 10
        val right = width - density * 10
        val cy = height / 2f
        paint.color = PlasmaTheme.withAlpha(palette.text, 0x33)
        canvas.drawRoundRect(left, cy - density * 2, right, cy + density * 2, density * 2, density * 2, paint)
        val x = left + (right - left) * value.coerceIn(0f, 1f)
        paint.color = palette.accent
        canvas.drawRoundRect(left, cy - density * 2, x, cy + density * 2, density * 2, density * 2, paint)
        paint.color = palette.viewBackground
        canvas.drawCircle(x, cy, density * 8, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = density * 1.2f
        paint.color = palette.accent
        canvas.drawCircle(x, cy, density * 8, paint)
        paint.style = Paint.Style.FILL
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val left = density * 10
        val right = width - density * 10
        value = ((event.x - left) / (right - left)).coerceIn(0f, 1f)
        invalidate()
        val finished = event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL
        onChange(value, finished)
        return true
    }
}

/** Simple push button styled like a Breeze QtQuick button. */
@SuppressLint("ViewConstructor")
internal class PlasmaButton(
    context: Context,
    private val palette: PlasmaPalette,
    private val glyph: Glyph?,
    private val label: String,
    private val flat: Boolean = true,
    private val vertical: Boolean = false,
    private val onClick: () -> Unit,
) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = PlasmaTheme.sans }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f }
    private val icon = glyph?.let { GlyphDrawable(it, palette.text, 1.6f) }
    private var hover = 0f
    private var pressed = false
    private var animator: ValueAnimator? = null
    private val box = RectF()
    var focusedButton = false
        set(value) { field = value; invalidate() }

    init {
        setOnHoverListener { _, event ->
            val target = if (event.actionMasked == MotionEvent.ACTION_HOVER_EXIT) 0f else 1f
            animator?.cancel()
            animator = ValueAnimator.ofFloat(hover, target).apply {
                duration = PlasmaTheme.SHORT_MS
                addUpdateListener { hover = it.animatedValue as Float; invalidate() }
                start()
            }
            false
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        paint.textSize = 13f * density
        val textWidth = paint.measureText(label)
        val width = if (vertical) maxOf(textWidth + 24 * density, 96 * density)
        else textWidth + (if (icon != null) 42 else 24) * density
        val height = if (vertical) 104 * density else 32 * density
        setMeasuredDimension(
            resolveSize(width.toInt(), widthMeasureSpec),
            resolveSize(height.toInt(), heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        box.set(1f, 1f, width - 1f, height - 1f)
        val radius = if (vertical) density * 8 else density * 4
        val fillAlpha = when {
            pressed -> 0x66
            !flat -> 0x18 + (0x1B * hover).toInt()
            else -> (0x33 * hover).toInt()
        }
        paint.color = if (flat || pressed || hover > 0f) PlasmaTheme.withAlpha(palette.accent, fillAlpha)
        else PlasmaTheme.withAlpha(palette.text, fillAlpha)
        canvas.drawRoundRect(box, radius, radius, paint)
        if (!flat || hover > 0f || focusedButton) {
            stroke.color = if (hover > 0f || focusedButton) palette.accent else palette.separator
            canvas.drawRoundRect(box, radius, radius, stroke)
        }
        paint.color = palette.text
        paint.textSize = 13f * density
        if (vertical) {
            val size = 44 * density
            icon?.setBounds((width / 2f - size / 2f).toInt(), (18 * density).toInt(), (width / 2f + size / 2f).toInt(), (18 * density + size).toInt())
            icon?.draw(canvas)
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(label, width / 2f, height - 18 * density, paint)
        } else {
            var x = 12 * density
            icon?.let {
                val size = 16 * density
                it.setBounds(x.toInt(), (height / 2f - size / 2f).toInt(), (x + size).toInt(), (height / 2f + size / 2f).toInt())
                it.draw(canvas)
                x += size + 8 * density
            }
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText(label, x, height / 2f - (paint.descent() + paint.ascent()) / 2f, paint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { pressed = true; invalidate() }
            MotionEvent.ACTION_UP -> {
                pressed = false
                invalidate()
                if (event.x in 0f..width.toFloat() && event.y in 0f..height.toFloat()) onClick()
            }
            MotionEvent.ACTION_CANCEL -> { pressed = false; invalidate() }
        }
        return true
    }

    fun click() = onClick()
}

internal fun verticalLayout(context: Context) = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
internal fun horizontalLayout(context: Context) = LinearLayout(context).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
}
