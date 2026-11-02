package moe.n4tsu.dextop.plasma

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs

/**
 * The corner radii a Linux window draws itself (client-side decorations, menus), measured from
 * its pixels. Outside a round corner the capture still holds pixels the app did not mean to
 * show (black, or what the app left there); clipping to these radii hides them.
 *
 * Along each corner's diagonal the pixels keep the corner's own colour until the rounded edge;
 * a quarter circle of radius r meets the diagonal r·(1 − 1/√2) from the corner.
 */
internal object LinuxCornerProbe {
    private const val DIAGONAL_FACTOR = 3.4142135f // 1 / (1 − 1/√2)

    /** Radii in [bitmap]'s pixels, clockwise from the top left; 0 for square corners. */
    fun radii(bitmap: Bitmap, maximum: Int): FloatArray {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 16 || h < 16) return FloatArray(4)
        val limit = minOf(maximum, w / 4, h / 4)
        fun radius(x0: Int, y0: Int, dx: Int, dy: Int): Float {
            val corner = bitmap.getPixel(x0, y0)
            // Only what a capture leaves outside a round corner: nothing (transparent) or black.
            // Any other colour there is the app's own content, which must not be cut.
            val leftOver = Color.alpha(corner) < 16 ||
                (Color.red(corner) < 8 && Color.green(corner) < 8 && Color.blue(corner) < 8)
            if (!leftOver) return 0f
            // Square when the corner pixel continues along both edges into the window.
            var d = 0
            while (d < limit && near(bitmap.getPixel(x0 + dx * d, y0 + dy * d), corner)) d++
            if (d == 0 || d >= limit) return 0f
            // The edges next to the corner must be the corner's colour too (outside the arc),
            // otherwise the diagonal only crossed the app's own content.
            val r = (d * DIAGONAL_FACTOR)
            val along = (r / 3).toInt().coerceAtLeast(1)
            if (!near(bitmap.getPixel(x0 + dx * along, y0), corner) || !near(bitmap.getPixel(x0, y0 + dy * along), corner)) return 0f
            return r
        }
        return floatArrayOf(
            radius(0, 0, 1, 1),
            radius(w - 1, 0, -1, 1),
            radius(w - 1, h - 1, -1, -1),
            radius(0, h - 1, 1, -1),
        )
    }

    private fun near(a: Int, b: Int): Boolean =
        abs(Color.red(a) - Color.red(b)) + abs(Color.green(a) - Color.green(b)) +
            abs(Color.blue(a) - Color.blue(b)) + abs(Color.alpha(a) - Color.alpha(b)) <= 24
}
