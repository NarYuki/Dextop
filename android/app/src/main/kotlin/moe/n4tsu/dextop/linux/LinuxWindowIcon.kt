package moe.n4tsu.dextop.linux

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** EWMH CARDINAL icons are width, height, then unpremultiplied ARGB pixels. */
internal object LinuxWindowIcon {
    const val MAX_PROPERTY_BYTES = 2 * 1024 * 1024
    data class Image(val width: Int, val height: Int, val argb: IntArray)

    fun read(bytes: ByteArray, preferredSize: Int = 96): Image? {
        if (bytes.size > MAX_PROPERTY_BYTES || bytes.size % 4 != 0) return null
        val data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var bestOffset = -1
        var bestWidth = 0
        var bestHeight = 0
        fun rank(size: Int) = if (size >= preferredSize) size - preferredSize else 10000 - size
        while (data.remaining() >= 8) {
            val width = data.int
            val height = data.int
            val pixels = width.toLong() * height
            if (width <= 0 || height <= 0 || pixels > data.remaining() / 4) break
            if (width <= 512 && height <= 512 &&
                (bestOffset < 0 || rank(maxOf(width, height)) < rank(maxOf(bestWidth, bestHeight)))) {
                bestOffset = data.position()
                bestWidth = width
                bestHeight = height
            }
            data.position(data.position() + pixels.toInt() * 4)
        }
        if (bestOffset < 0) return null
        data.position(bestOffset)
        return Image(bestWidth, bestHeight, IntArray(bestWidth * bestHeight) { data.int })
    }
}
