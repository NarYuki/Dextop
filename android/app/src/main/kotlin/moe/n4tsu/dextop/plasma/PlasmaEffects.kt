package moe.n4tsu.dextop.plasma

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import android.view.animation.Interpolator

/** A captured frame of the whole Dextop display, used for thumbnails and effects. */
internal class DesktopFrame(
    val bitmap: Bitmap,
    private val displayWidth: Int,
    private val displayHeight: Int,
) {
    fun crop(rect: Rect): Bitmap? = runCatching {
        val sx = bitmap.width.toFloat() / displayWidth
        val sy = bitmap.height.toFloat() / displayHeight
        val left = (rect.left * sx).toInt().coerceIn(0, bitmap.width - 1)
        val top = (rect.top * sy).toInt().coerceIn(0, bitmap.height - 1)
        val right = (rect.right * sx).toInt().coerceIn(left + 1, bitmap.width)
        val bottom = (rect.bottom * sy).toInt().coerceIn(top + 1, bitmap.height)
        Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }.getOrNull()
}

/**
 * Shell effects drawn in a transparent, non-touchable overlay.  Window
 * open/close/minimize animations belong to the platform; the shell only
 * animates what it owns, such as the virtual-desktop slide.
 */
internal class EffectsView(context: Context, private val paletteProvider: () -> PlasmaPalette) : View(context) {
    abstract class Effect(val duration: Long, val interpolator: Interpolator) {
        val start = SystemClock.uptimeMillis()
        var onEnd: (() -> Unit)? = null
        fun progress(now: Long): Float =
            if (duration <= 0) 1f else ((now - start).toFloat() / duration).coerceIn(0f, 1f)
        abstract fun draw(canvas: Canvas, t: Float, palette: PlasmaPalette)
    }

    private val effects = ArrayList<Effect>()
    var onIdle: (() -> Unit)? = null

    fun add(effect: Effect) {
        effects += effect
        postInvalidateOnAnimation()
    }

    fun hasWork(): Boolean = effects.isNotEmpty()

    override fun onDraw(canvas: Canvas) {
        val palette = paletteProvider()
        val now = SystemClock.uptimeMillis()
        val finished = ArrayList<Effect>()
        effects.toList().forEach { effect ->
            val t = effect.progress(now)
            effect.draw(canvas, effect.interpolator.getInterpolation(t), palette)
            if (t >= 1f) finished += effect
        }
        finished.forEach { effect ->
            effects.remove(effect)
            effect.onEnd?.invoke()
        }
        if (effects.isNotEmpty()) postInvalidateOnAnimation() else onIdle?.invoke()
    }

    /** Virtual desktop "Slide": the previous desktop image leaves the screen. */
    class DesktopSlide(private val image: Bitmap, private val direction: Int, duration: Long) : Effect(duration, PlasmaTheme.inOutCubic) {
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        private val rect = RectF()
        override fun draw(canvas: Canvas, t: Float, palette: PlasmaPalette) {
            val width = canvas.width.toFloat()
            val offset = -direction * width * t
            rect.set(offset, 0f, offset + width, canvas.height.toFloat())
            paint.alpha = (255 * (1f - t * .35f)).toInt()
            canvas.drawBitmap(image, null, rect, paint)
        }
    }

    companion object {
        fun lerp(from: RectF, to: RectF, t: Float, out: RectF) {
            out.set(
                from.left + (to.left - from.left) * t,
                from.top + (to.top - from.top) * t,
                from.right + (to.right - from.right) * t,
                from.bottom + (to.bottom - from.bottom) * t,
            )
        }
    }
}
