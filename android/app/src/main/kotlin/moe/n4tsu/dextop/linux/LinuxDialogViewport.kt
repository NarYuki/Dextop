package moe.n4tsu.dextop.linux

import kotlin.math.roundToInt

/**
 * Fit a client that keeps its own size into its window: as large as the window allows with
 * its aspect ratio kept, so only the leftover side shows margins.
 */
internal object LinuxDialogViewport {
    data class Size(val width: Int, val height: Int)

    fun fit(width: Int, height: Int, availableWidth: Int, availableHeight: Int): Size {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        val maxW = availableWidth.coerceAtLeast(1)
        val maxH = availableHeight.coerceAtLeast(1)
        val scale = minOf(maxW.toDouble() / w, maxH.toDouble() / h)
        return Size((w * scale).roundToInt().coerceIn(1, maxW),
            (h * scale).roundToInt().coerceIn(1, maxH))
    }

    fun coordinate(value: Float, viewport: Int, client: Int): Int =
        (value * client.coerceAtLeast(1).toDouble() / viewport.coerceAtLeast(1)).roundToInt()
}
