package moe.n4tsu.dextop.linux

import kotlin.math.roundToInt

/** The same viewport is used for texture placement and inverse pointer mapping. */
internal data class LinuxWindowViewport(
    val bufferWidth: Int, val bufferHeight: Int,
    val viewWidth: Int, val viewHeight: Int, val preserveAspect: Boolean,
) {
    private val fit = minOf(viewWidth.toFloat() / bufferWidth, viewHeight.toFloat() / bufferHeight)
    val width = if (preserveAspect) bufferWidth * fit else viewWidth.toFloat()
    val height = if (preserveAspect) bufferHeight * fit else viewHeight.toFloat()
    val left = (viewWidth - width) / 2f
    val top = (viewHeight - height) / 2f
    fun bufferX(x: Float) = ((x - left) * bufferWidth / width).roundToInt().coerceIn(0, bufferWidth - 1)
    fun bufferY(y: Float) = ((y - top) * bufferHeight / height).roundToInt().coerceIn(0, bufferHeight - 1)
}
