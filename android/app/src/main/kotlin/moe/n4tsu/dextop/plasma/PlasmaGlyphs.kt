package moe.n4tsu.dextop.plasma

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/**
 * Breeze-style symbolic icons drawn on a 24-unit grid.
 *
 * Plasma uses monochrome "-symbolic" icons for its chrome; drawing them as
 * paths keeps them crisp at every Dextop density without shipping SVGs.
 */
internal enum class Glyph {
    KDE_LAUNCHER, MINIMIZE, MAXIMIZE, RESTORE, CLOSE, KEEP_ABOVE, SEARCH, POWER, RESTART,
    LOCK, LOGOUT, SLEEP, NETWORK, VOLUME, VOLUME_MUTED, BATTERY, BATTERY_CHARGING, OVERVIEW,
    DESKTOP, SETTINGS, APPS, STAR, HISTORY, CHEVRON_RIGHT, CHEVRON_UP, CHEVRON_DOWN, PIN,
    NOTIFICATIONS, CLIPBOARD, SHOW_DESKTOP, WINDOW, BACK, COMPUTER, KEYBOARD, PLUS, CHECK,
    WALLPAPER, TILE, BRIGHTNESS, INFO,
}

internal class GlyphDrawable(
    private val glyph: Glyph,
    var color: Int,
    private val strokeUnits: Float = 1.6f,
) : Drawable() {
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    var level01: Float = 1f

    override fun draw(canvas: Canvas) {
        val box = bounds
        if (box.isEmpty) return
        val unit = minOf(box.width(), box.height()) / 24f
        canvas.save()
        canvas.translate(box.exactCenterX() - 12f * unit, box.exactCenterY() - 12f * unit)
        canvas.scale(unit, unit)
        stroke.color = color
        stroke.strokeWidth = strokeUnits
        fill.color = color
        GlyphPainter.paint(canvas, glyph, stroke, fill, level01)
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) {
        stroke.alpha = alpha
        fill.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        stroke.colorFilter = colorFilter
        fill.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

internal object GlyphPainter {
    private val path = Path()
    private val oval = RectF()

    fun paint(canvas: Canvas, glyph: Glyph, stroke: Paint, fill: Paint, level: Float) {
        path.reset()
        when (glyph) {
            Glyph.KDE_LAUNCHER -> {
                // Plasma's "start-here-kde" gear-and-K silhouette, simplified.
                val teeth = 8
                for (i in 0 until teeth) {
                    canvas.save()
                    canvas.rotate(i * 360f / teeth, 12f, 12f)
                    oval.set(10.6f, 1.6f, 13.4f, 5.2f)
                    canvas.drawRoundRect(oval, 0.8f, 0.8f, fill)
                    canvas.restore()
                }
                val cutout = Paint(stroke).apply {
                    color = 0xFF000000.toInt()
                    xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
                    strokeWidth = 2.1f
                }
                val layer = canvas.saveLayer(null, null)
                oval.set(3.8f, 3.8f, 20.2f, 20.2f)
                canvas.drawOval(oval, fill)
                canvas.drawLine(9f, 7.5f, 9f, 16.5f, cutout)
                canvas.drawLine(15.2f, 7.5f, 10f, 12.2f, cutout)
                canvas.drawLine(10.5f, 11.6f, 15.4f, 16.5f, cutout)
                canvas.restoreToCount(layer)
            }
            Glyph.MINIMIZE -> {
                path.moveTo(7f, 10f); path.lineTo(12f, 15f); path.lineTo(17f, 10f)
                canvas.drawPath(path, stroke)
            }
            Glyph.MAXIMIZE -> {
                path.moveTo(7f, 14f); path.lineTo(12f, 9f); path.lineTo(17f, 14f)
                canvas.drawPath(path, stroke)
            }
            Glyph.RESTORE -> {
                path.moveTo(12f, 6.5f); path.lineTo(17.5f, 12f); path.lineTo(12f, 17.5f); path.lineTo(6.5f, 12f); path.close()
                canvas.drawPath(path, stroke)
            }
            Glyph.CLOSE -> {
                canvas.drawLine(7.5f, 7.5f, 16.5f, 16.5f, stroke)
                canvas.drawLine(16.5f, 7.5f, 7.5f, 16.5f, stroke)
            }
            Glyph.KEEP_ABOVE -> {
                path.moveTo(7f, 13f); path.lineTo(12f, 8f); path.lineTo(17f, 13f)
                canvas.drawPath(path, stroke)
                canvas.drawLine(7f, 17f, 17f, 17f, stroke)
            }
            Glyph.SEARCH -> {
                oval.set(4.5f, 4.5f, 15f, 15f); canvas.drawOval(oval, stroke)
                canvas.drawLine(13.6f, 13.6f, 19.5f, 19.5f, stroke)
            }
            Glyph.POWER -> {
                oval.set(5f, 5.5f, 19f, 19.5f)
                canvas.drawArc(oval, -60f, 300f, false, stroke)
                canvas.drawLine(12f, 3.5f, 12f, 11.5f, stroke)
            }
            Glyph.RESTART -> {
                oval.set(5f, 5f, 19f, 19f)
                canvas.drawArc(oval, -80f, 300f, false, stroke)
                path.moveTo(12.5f, 2.8f); path.lineTo(15.6f, 5.2f); path.lineTo(12.3f, 7.4f)
                canvas.drawPath(path, stroke)
            }
            Glyph.LOCK -> {
                oval.set(6f, 11f, 18f, 20f); canvas.drawRoundRect(oval, 1.5f, 1.5f, stroke)
                oval.set(8.5f, 4.5f, 15.5f, 14.5f); canvas.drawArc(oval, 180f, 180f, false, stroke)
                canvas.drawLine(8.5f, 9.5f, 8.5f, 11f, stroke); canvas.drawLine(15.5f, 9.5f, 15.5f, 11f, stroke)
            }
            Glyph.LOGOUT -> {
                path.moveTo(13f, 5f); path.lineTo(6f, 5f); path.lineTo(6f, 19f); path.lineTo(13f, 19f)
                canvas.drawPath(path, stroke)
                canvas.drawLine(10f, 12f, 19.5f, 12f, stroke)
                path.reset(); path.moveTo(16.5f, 9f); path.lineTo(19.5f, 12f); path.lineTo(16.5f, 15f)
                canvas.drawPath(path, stroke)
            }
            Glyph.SLEEP -> {
                path.moveTo(15.5f, 4.5f)
                path.cubicTo(9f, 5f, 6f, 13f, 10f, 17.5f)
                path.cubicTo(13f, 20.5f, 18f, 19.5f, 19.5f, 16f)
                path.cubicTo(14f, 16.5f, 11.5f, 9.5f, 15.5f, 4.5f)
                canvas.drawPath(path, stroke)
            }
            Glyph.NETWORK -> {
                for (i in 0 until 3) {
                    val inset = 3f + i * 3.4f
                    oval.set(inset, inset + 2.5f, 24f - inset, 24f - inset + 2.5f)
                    canvas.drawArc(oval, 225f, 90f, false, stroke)
                }
                oval.set(10.7f, 16.2f, 13.3f, 18.8f); canvas.drawOval(oval, fill)
            }
            Glyph.VOLUME, Glyph.VOLUME_MUTED -> {
                path.moveTo(4f, 9.5f); path.lineTo(7.5f, 9.5f); path.lineTo(12f, 5.5f); path.lineTo(12f, 18.5f)
                path.lineTo(7.5f, 14.5f); path.lineTo(4f, 14.5f); path.close()
                canvas.drawPath(path, stroke)
                if (glyph == Glyph.VOLUME_MUTED) {
                    canvas.drawLine(15f, 9.5f, 20f, 14.5f, stroke); canvas.drawLine(20f, 9.5f, 15f, 14.5f, stroke)
                } else {
                    oval.set(10f, 8f, 18f, 16f); canvas.drawArc(oval, -50f, 100f, false, stroke)
                    if (level > .5f) { oval.set(9f, 5f, 21f, 19f); canvas.drawArc(oval, -50f, 100f, false, stroke) }
                }
            }
            Glyph.BATTERY, Glyph.BATTERY_CHARGING -> {
                oval.set(3.5f, 7.5f, 19f, 16.5f); canvas.drawRoundRect(oval, 1.6f, 1.6f, stroke)
                oval.set(19.5f, 10f, 21f, 14f); canvas.drawRect(oval, fill)
                val width = 13.5f * level.coerceIn(0f, 1f)
                oval.set(5f, 9f, 5f + width, 15f); canvas.drawRoundRect(oval, .6f, .6f, fill)
                if (glyph == Glyph.BATTERY_CHARGING) {
                    val bolt = Paint(stroke).apply { strokeWidth = 1.4f; color = 0xFF27AE60.toInt() }
                    path.reset(); path.moveTo(12.5f, 8.2f); path.lineTo(9.5f, 12.4f); path.lineTo(12.6f, 12.4f); path.lineTo(10.5f, 15.8f)
                    canvas.drawPath(path, bolt)
                }
            }
            Glyph.OVERVIEW -> {
                listOf(RectF(3.5f, 5f, 11f, 11f), RectF(13f, 5f, 20.5f, 11f), RectF(3.5f, 13f, 11f, 19f), RectF(13f, 13f, 20.5f, 19f))
                    .forEach { canvas.drawRoundRect(it, 1.2f, 1.2f, stroke) }
            }
            Glyph.DESKTOP, Glyph.COMPUTER -> {
                oval.set(3.5f, 4.5f, 20.5f, 15.5f); canvas.drawRoundRect(oval, 1.5f, 1.5f, stroke)
                canvas.drawLine(12f, 15.5f, 12f, 19f, stroke); canvas.drawLine(8f, 19.5f, 16f, 19.5f, stroke)
            }
            Glyph.SETTINGS -> {
                oval.set(8.5f, 8.5f, 15.5f, 15.5f); canvas.drawOval(oval, stroke)
                for (i in 0 until 8) {
                    canvas.save(); canvas.rotate(i * 45f, 12f, 12f)
                    canvas.drawLine(12f, 3.5f, 12f, 6.5f, stroke)
                    canvas.restore()
                }
            }
            Glyph.APPS -> {
                for (row in 0 until 3) for (column in 0 until 3) {
                    oval.set(4f + column * 6.2f, 4f + row * 6.2f, 7.6f + column * 6.2f, 7.6f + row * 6.2f)
                    canvas.drawRoundRect(oval, .8f, .8f, fill)
                }
            }
            Glyph.STAR -> {
                for (i in 0 until 5) {
                    val angle = Math.toRadians((-90 + i * 144).toDouble())
                    val x = 12f + 8.5f * Math.cos(angle).toFloat()
                    val y = 12.8f + 8.5f * Math.sin(angle).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close(); canvas.drawPath(path, stroke)
            }
            Glyph.HISTORY -> {
                oval.set(4f, 4f, 20f, 20f); canvas.drawOval(oval, stroke)
                path.moveTo(12f, 7.5f); path.lineTo(12f, 12f); path.lineTo(15f, 14f); canvas.drawPath(path, stroke)
            }
            Glyph.CHEVRON_RIGHT -> { path.moveTo(10f, 7f); path.lineTo(15f, 12f); path.lineTo(10f, 17f); canvas.drawPath(path, stroke) }
            Glyph.CHEVRON_UP -> { path.moveTo(7f, 14f); path.lineTo(12f, 9f); path.lineTo(17f, 14f); canvas.drawPath(path, stroke) }
            Glyph.CHEVRON_DOWN -> { path.moveTo(7f, 10f); path.lineTo(12f, 15f); path.lineTo(17f, 10f); canvas.drawPath(path, stroke) }
            Glyph.BACK -> { path.moveTo(14f, 7f); path.lineTo(9f, 12f); path.lineTo(14f, 17f); canvas.drawPath(path, stroke) }
            Glyph.PIN -> {
                path.moveTo(9f, 4f); path.lineTo(15f, 4f); path.lineTo(14f, 10f); path.lineTo(17f, 13.5f)
                path.lineTo(7f, 13.5f); path.lineTo(10f, 10f); path.close()
                canvas.drawPath(path, stroke); canvas.drawLine(12f, 13.5f, 12f, 20f, stroke)
            }
            Glyph.NOTIFICATIONS -> {
                path.moveTo(6f, 16.5f); path.lineTo(6f, 11f)
                path.cubicTo(6f, 4f, 18f, 4f, 18f, 11f); path.lineTo(18f, 16.5f); path.close()
                canvas.drawPath(path, stroke)
                oval.set(10f, 17.5f, 14f, 20.5f); canvas.drawArc(oval, 0f, 180f, false, stroke)
            }
            Glyph.CLIPBOARD -> {
                oval.set(5.5f, 5f, 18.5f, 20.5f); canvas.drawRoundRect(oval, 1.5f, 1.5f, stroke)
                oval.set(9f, 3.5f, 15f, 6.5f); canvas.drawRoundRect(oval, 1f, 1f, fill)
            }
            Glyph.SHOW_DESKTOP -> {
                oval.set(3.5f, 4.5f, 20.5f, 17.5f); canvas.drawRoundRect(oval, 1.5f, 1.5f, stroke)
                canvas.drawLine(3.5f, 14f, 20.5f, 14f, stroke)
            }
            Glyph.WINDOW -> {
                oval.set(3.5f, 5f, 20.5f, 19f); canvas.drawRoundRect(oval, 1.5f, 1.5f, stroke)
                canvas.drawLine(3.5f, 8.5f, 20.5f, 8.5f, stroke)
            }
            Glyph.KEYBOARD -> {
                oval.set(3f, 6.5f, 21f, 17.5f); canvas.drawRoundRect(oval, 1.5f, 1.5f, stroke)
                for (column in 0 until 5) oval.set(5.5f + column * 2.8f, 9f, 6.9f + column * 2.8f, 10.4f).also { canvas.drawRect(oval, fill) }
                canvas.drawLine(8f, 14.5f, 16f, 14.5f, stroke)
            }
            Glyph.PLUS -> { canvas.drawLine(12f, 5f, 12f, 19f, stroke); canvas.drawLine(5f, 12f, 19f, 12f, stroke) }
            Glyph.CHECK -> { path.moveTo(5.5f, 12.5f); path.lineTo(10f, 17f); path.lineTo(18.5f, 7.5f); canvas.drawPath(path, stroke) }
            Glyph.WALLPAPER -> {
                oval.set(3.5f, 5f, 20.5f, 19f); canvas.drawRoundRect(oval, 1.5f, 1.5f, stroke)
                path.moveTo(3.8f, 17f); path.lineTo(9.5f, 11f); path.lineTo(13f, 14.5f); path.lineTo(15.5f, 12f); path.lineTo(20.2f, 16.5f)
                canvas.drawPath(path, stroke)
                oval.set(14f, 7f, 17f, 10f); canvas.drawOval(oval, fill)
            }
            Glyph.TILE -> {
                oval.set(3.5f, 5f, 20.5f, 19f); canvas.drawRoundRect(oval, 1.5f, 1.5f, stroke)
                canvas.drawLine(12f, 5f, 12f, 19f, stroke); canvas.drawLine(12f, 12f, 20.5f, 12f, stroke)
            }
            Glyph.BRIGHTNESS -> {
                oval.set(8f, 8f, 16f, 16f); canvas.drawOval(oval, stroke)
                for (i in 0 until 8) {
                    canvas.save(); canvas.rotate(i * 45f, 12f, 12f)
                    canvas.drawLine(12f, 2.8f, 12f, 4.8f, stroke); canvas.restore()
                }
            }
            Glyph.INFO -> {
                oval.set(3.5f, 3.5f, 20.5f, 20.5f); canvas.drawOval(oval, stroke)
                canvas.drawLine(12f, 11f, 12f, 16.5f, stroke)
                oval.set(11f, 7f, 13f, 9f); canvas.drawOval(oval, fill)
            }
        }
    }
}
