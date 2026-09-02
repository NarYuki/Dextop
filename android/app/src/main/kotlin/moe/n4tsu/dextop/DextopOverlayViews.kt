package moe.n4tsu.dextop

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView

internal class BottomDividerDrawable(
    color: Int,
    private val thickness: Int,
    private val bottomInset: Int = 0,
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }

    override fun draw(canvas: Canvas) {
        canvas.drawRect(
            bounds.left.toFloat(),
            (bounds.bottom - bottomInset - thickness).toFloat(),
            bounds.right.toFloat(),
            (bounds.bottom - bottomInset).toFloat(),
            paint,
        )
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
        paint.colorFilter = colorFilter
    }
    @Deprecated("Deprecated in Android")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

internal class LaptopKeyTextView(
    context: Context,
    private val showHomePosition: Boolean,
    markColor: Int
) : TextView(context) {
    private var customGlyph: Drawable? = null
    private var customGlyphScale = .46f
    private var secondaryLabel: String? = null
    private var topSecondaryLabel: String? = null
    private val markPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = markColor
        strokeWidth = context.resources.displayMetrics.density * 1.6f
        strokeCap = Paint.Cap.ROUND
    }
    private val secondaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = context.resources.displayMetrics.scaledDensity * 7f
    }

    fun setSecondaryLabel(label: String?, color: Int) {
        secondaryLabel = label
        secondaryPaint.color = color
        invalidate()
    }

    fun setTopSecondaryLabel(label: String?, color: Int) {
        topSecondaryLabel = label
        secondaryPaint.color = color
        invalidate()
    }

    /**
     * Renders a key icon without relying on a private-use font glyph.
     * This is used for the Meta/Android key so OEM font fallback cannot
     * turn the icon into an unrelated character.
     */
    fun setCustomGlyph(drawable: Drawable?, scale: Float = .46f) {
        customGlyph = drawable
        customGlyphScale = scale
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        customGlyph?.let { drawable ->
            val size = minOf(width, height) * customGlyphScale
            val left = ((width - size) / 2f).toInt()
            val top = ((height - size) / 2f).toInt()
            drawable.setBounds(left, top, (left + size).toInt(), (top + size).toInt())
            drawable.draw(canvas)
        }
        val density = resources.displayMetrics.density
        val currentLayout = layout
        if (currentLayout != null && currentLayout.lineCount > 0) {
            val layoutTop = (height - currentLayout.height) / 2f
            val primaryBaseline = layoutTop + currentLayout.getLineBaseline(0)
            if (showHomePosition) {
                // Keep the tactile home-position mark below the key legend.
                // When Ctrl shortcut hints are visible, place it below the
                // secondary label as well so the two never overlap.
                val y = primaryBaseline + if (secondaryLabel == null) {
                    6f * density
                } else {
                    13f * density
                }
                val halfWidth = 6f * density
                canvas.drawLine(width / 2f - halfWidth, y, width / 2f + halfWidth, y, markPaint)
            }
            secondaryLabel?.let { label ->
                secondaryPaint.typeface = typeface
                canvas.drawText(label, width / 2f, primaryBaseline + 8f * density, secondaryPaint)
            }
            topSecondaryLabel?.let { label ->
                secondaryPaint.typeface = typeface
                canvas.drawText(label, width / 2f, primaryBaseline - 12f * density, secondaryPaint)
            }
        }
    }
}

internal class KeyboardGlyphDrawable(
    private val kind: Int,
    private val glyphColor: Int
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = glyphColor
        style = Paint.Style.FILL
        strokeCap = Paint.Cap.ROUND
    }

    override fun draw(canvas: Canvas) {
        paint.color = glyphColor
        val b = bounds
        val scale = minOf(b.width() / 24f, b.height() / 20f)
        val offsetX = (b.width() - 24f * scale) / 2f
        val offsetY = (b.height() - 20f * scale) / 2f
        canvas.save()
        canvas.translate(b.left + offsetX, b.top + offsetY)
        canvas.scale(scale, scale)
        paint.strokeWidth = 1.5f
        when (kind) {
            APP_GRID -> {
                val tile = 4f
                val gap = 2f
                val startX = (24f - tile * 3f - gap * 2f) / 2f
                val startY = (20f - tile * 3f - gap * 2f) / 2f
                for (row in 0 until 3) {
                    for (column in 0 until 3) {
                        val left = startX + column * (tile + gap)
                        val top = startY + row * (tile + gap)
                        canvas.drawRect(
                            left,
                            top,
                            left + tile,
                            top + tile,
                            paint
                        )
                    }
                }
            }

            BACK -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2.2f
                canvas.drawLine(18f, 10f, 6f, 10f, paint)
                canvas.drawLine(6f, 10f, 11f, 5f, paint)
                canvas.drawLine(6f, 10f, 11f, 15f, paint)
                paint.style = Paint.Style.FILL
            }

            ENTER -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2.25f
                paint.strokeJoin = Paint.Join.ROUND
                val path = Path().apply {
                    moveTo(18.5f, 4.5f)
                    lineTo(18.5f, 8.5f)
                    cubicTo(18.5f, 10.2f, 17.2f, 11.5f, 15.5f, 11.5f)
                    lineTo(6f, 11.5f)
                    moveTo(6f, 11.5f)
                    lineTo(10.5f, 7f)
                    moveTo(6f, 11.5f)
                    lineTo(10.5f, 16f)
                }
                canvas.drawPath(path, paint)
                paint.style = Paint.Style.FILL
            }

            SHIFT, SHIFT_LOCKED -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2.15f
                paint.strokeJoin = Paint.Join.ROUND
                val arrow = Path().apply {
                    moveTo(12f, 3.5f)
                    lineTo(6.5f, 9f)
                    lineTo(9.3f, 9f)
                    lineTo(9.3f, 15f)
                    lineTo(14.7f, 15f)
                    lineTo(14.7f, 9f)
                    lineTo(17.5f, 9f)
                    close()
                }
                canvas.drawPath(arrow, paint)
                if (kind == SHIFT_LOCKED) {
                    canvas.drawLine(8.5f, 18f, 15.5f, 18f, paint)
                }
                paint.style = Paint.Style.FILL
            }

            PALETTE -> {
                canvas.drawOval(RectF(2f, 2f, 22f, 18f), paint)
                paint.color = Color.rgb(35, 33, 39)
                canvas.drawCircle(17.5f, 14f, 3.5f, paint)
                canvas.drawCircle(7f, 7f, 1.5f, paint)
                canvas.drawCircle(12f, 5f, 1.5f, paint)
                canvas.drawCircle(17f, 7.5f, 1.5f, paint)
            }

            CHECK -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2.4f
                canvas.drawCircle(12f, 10f, 8f, paint)
                canvas.drawLine(7.5f, 10f, 10.5f, 13f, paint)
                canvas.drawLine(10.5f, 13f, 16.5f, 7f, paint)
                paint.style = Paint.Style.FILL
            }
        }
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(filter: android.graphics.ColorFilter?) {
        paint.colorFilter = filter
    }

    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    companion object {
        const val APP_GRID = 1
        const val BACK = 2
        const val PALETTE = 3
        const val CHECK = 4
        const val ENTER = 5
        const val SHIFT = 6
        const val SHIFT_LOCKED = 7
    }
}

internal class TouchRoutingFrame(context: Context) : FrameLayout(context) {
    var routeTouchesToSurface = false

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (routeTouchesToSurface) {
            val surface = getChildAt(0)
            if (surface != null) {
                val handled = surface.dispatchTouchEvent(event)
                return handled
            }
        }
        return super.dispatchTouchEvent(event)
    }
}

internal class LevelIconView(context: Context, private val volume: Boolean) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(62, 62, 66)
        style = Paint.Style.STROKE
        strokeWidth = 1.9f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    var level = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (volume) drawVolume(canvas) else drawSun(canvas)
    }

    private fun drawSun(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val base = minOf(width, height).toFloat()
        val coreRadius = base * (.12f + level * .045f)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, coreRadius, paint)
        paint.style = Paint.Style.STROKE
        val rayStart = base * .25f
        val rayLength = base * (.055f + .16f * level)
        paint.alpha = (125 + 130 * level).toInt()
        for (index in 0 until 8) {
            val angle = Math.PI * index / 4.0
            val cos = kotlin.math.cos(angle).toFloat()
            val sin = kotlin.math.sin(angle).toFloat()
            canvas.drawLine(
                cx + cos * rayStart,
                cy + sin * rayStart,
                cx + cos * (rayStart + rayLength),
                cy + sin * (rayStart + rayLength),
                paint
            )
        }
        paint.alpha = 255
    }

    private fun drawVolume(canvas: Canvas) {
        val base = minOf(width, height).toFloat()
        // Keep the combined speaker + waves visually centered: the speaker
        // starts centered while muted, then shifts left as waves expand.
        val cx = width * (.50f - .22f * level)
        val cy = height / 2f
        val speaker = Path().apply {
            moveTo(cx - base * .24f, cy - base * .11f)
            lineTo(cx - base * .10f, cy - base * .11f)
            lineTo(cx + base * .08f, cy - base * .27f)
            lineTo(cx + base * .08f, cy + base * .27f)
            lineTo(cx - base * .10f, cy + base * .11f)
            lineTo(cx - base * .24f, cy + base * .11f)
            close()
        }
        paint.style = Paint.Style.FILL
        paint.alpha = 255
        canvas.drawPath(speaker, paint)
        paint.style = Paint.Style.STROKE
        val thresholds = floatArrayOf(.02f, .34f, .67f)
        val waveCount = thresholds.count { level >= it }
        for (index in 0 until waveCount) {
            val threshold = thresholds[index]
            val local = ((level - threshold) / (1f - threshold)).coerceIn(0f, 1f)
            val baseRadius = when (index) {
                0 -> .17f
                1 -> .29f
                else -> .41f
            }
            val radius = base * baseRadius * (.78f + .22f * local)
            paint.alpha = (105 + 150 * local).toInt()
            val rect = android.graphics.RectF(
                cx - radius * .15f,
                cy - radius,
                cx + radius * 1.85f,
                cy + radius
            )
            canvas.drawArc(rect, -47f, 94f, false, paint)
        }
        paint.alpha = 255
    }
}

internal class CursorView(context: Context) : View(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private var normalizedX = .5f
    private var normalizedY = .5f
    private var radius = 13f
    var contentHeightFraction = 1f
    fun update(x: Float, y: Float) {
        normalizedX = x
        normalizedY = y
        invalidate()
    }

    fun pulse() {
        radius = 19f
        invalidate()
        postDelayed({ radius = 13f; invalidate() }, 100)
    }

    override fun onDraw(canvas: Canvas) {
        val x = normalizedX * width
        val y = normalizedY * height * contentHeightFraction
        canvas.drawCircle(x, y, radius, fill)
        canvas.drawCircle(x, y, radius, stroke)
    }
}

