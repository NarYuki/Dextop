package moe.n4tsu.dextop.linux

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import com.caverock.androidsvg.SVG
import java.io.ByteArrayOutputStream
import java.io.File

/** Normalize guest icons to a small PNG before sending them over Binder. */
internal object LinuxIconDecoder {
    private const val SIZE = 96

    fun windowPng(bytes: ByteArray): ByteArray? {
        val image = LinuxWindowIcon.read(bytes) ?: return null
        return png(Bitmap.createBitmap(image.argb, image.width, image.height, Bitmap.Config.ARGB_8888))
    }

    fun png(file: File): ByteArray? {
        val bitmap = when (file.extension.lowercase()) {
            "svg" -> Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888).also { bitmap ->
                file.inputStream().use { input ->
                    val svg = SVG.getFromInputStream(input)
                    if (svg.documentViewBox == null && svg.documentWidth > 0 && svg.documentHeight > 0) {
                        svg.setDocumentViewBox(0f, 0f, svg.documentWidth, svg.documentHeight)
                    }
                    svg.setDocumentWidth("100%")
                    svg.setDocumentHeight("100%")
                    svg.renderToCanvas(Canvas(bitmap))
                }
            }
            "xpm" -> xpm(file.readText()) ?: return null
            "ico" -> {
                val bytes = embeddedIcoPng(file.readBytes()) ?: return null
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                if (options.outWidth <= 0 || options.outHeight <= 0) return null
                options.inSampleSize = 1
                while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > SIZE * 2) options.inSampleSize *= 2
                options.inJustDecodeBounds = false
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
            }
            else -> {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, options)
                if (options.outWidth <= 0 || options.outHeight <= 0) return null
                options.inSampleSize = 1
                while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > SIZE * 2) options.inSampleSize *= 2
                options.inJustDecodeBounds = false
                BitmapFactory.decodeFile(file.path, options) ?: return null
            }
        }
        return png(bitmap)
    }

    internal fun argbPng(pixels: IntArray, width: Int, height: Int): ByteArray =
        png(Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888))

    private fun png(bitmap: Bitmap): ByteArray {
        try {
            val scale = minOf(1f, SIZE.toFloat() / maxOf(bitmap.width, bitmap.height))
            val output = if (scale < 1) Bitmap.createScaledBitmap(bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true) else bitmap
            try {
                return ByteArrayOutputStream().use { bytes ->
                    output.compress(Bitmap.CompressFormat.PNG, 100, bytes)
                    bytes.toByteArray()
                }
            } finally { if (output !== bitmap) output.recycle() }
        } finally { bitmap.recycle() }
    }

    /** Modern ICOs contain a PNG for their larger sizes; keep its original alpha. */
    internal fun embeddedIcoPng(bytes: ByteArray): ByteArray? {
        if (bytes.size < 6 || bytes.size > 2 * 1024 * 1024) return null
        val data = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        if (data.short.toInt() != 0 || data.short.toInt() != 1) return null
        val count = data.short.toInt() and 0xffff
        if (count !in 1..256 || 6 + count * 16 > bytes.size) return null
        val signature = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 13, 10, 26, 10)
        var best: ByteArray? = null
        var bestArea = 0
        repeat(count) { index ->
            val entry = 6 + index * 16
            val width = (bytes[entry].toInt() and 0xff).takeIf { it != 0 } ?: 256
            val height = (bytes[entry + 1].toInt() and 0xff).takeIf { it != 0 } ?: 256
            val size = data.getInt(entry + 8).toLong() and 0xffffffffL
            val offset = data.getInt(entry + 12).toLong() and 0xffffffffL
            if (size >= 8 && offset >= 6 + count * 16 && offset + size <= bytes.size &&
                width * height > bestArea && signature.indices.all { bytes[offset.toInt() + it] == signature[it] }) {
                best = bytes.copyOfRange(offset.toInt(), (offset + size).toInt())
                bestArea = width * height
            }
        }
        return best
    }

    private fun xpm(text: String): Bitmap? {
        val lines = Regex("\"([^\"\\r\\n]*)\"").findAll(text).map { it.groupValues[1] }.toList()
        val header = lines.firstOrNull()?.trim()?.split(Regex("\\s+"))?.map { it.toIntOrNull() ?: return null } ?: return null
        if (header.size < 4) return null
        val (width, height, colors, cpp) = header
        if (width !in 1..1024 || height !in 1..1024 || colors !in 1..4096 || cpp !in 1..8 || lines.size < 1 + colors + height) return null
        val palette = HashMap<String, Int>()
        for (line in lines.drop(1).take(colors)) {
            if (line.length < cpp) return null
            val value = Regex("(?:^|\\s)c\\s+(.+?)(?=\\s+(?:s|m|g4|g)\\s+|$)")
                .find(line.substring(cpp))?.groupValues?.get(1)?.trim() ?: return null
            palette[line.take(cpp)] = if (value.equals("None", true)) Color.TRANSPARENT else runCatching {
                val gray = Regex("gr[ae]y(\\d{1,3})", RegexOption.IGNORE_CASE).matchEntire(value)
                    ?.groupValues?.get(1)?.toInt()?.takeIf { it in 0..100 }
                // X11 also allows 16 bits per channel.
                if (gray != null) (gray * 255 / 100).let { Color.rgb(it, it, it) }
                else Color.parseColor(when {
                    value.startsWith("#") && value.length == 13 -> "#${value.substring(1, 3)}${value.substring(5, 7)}${value.substring(9, 11)}"
                    value.startsWith("#") && value.length == 4 -> "#${value[1]}${value[1]}${value[2]}${value[2]}${value[3]}${value[3]}"
                    else -> value
                })
            }.getOrNull() ?: return null
        }
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val row = lines[1 + colors + y]
            if (row.length < width * cpp) return null
            for (x in 0 until width) pixels[y * width + x] = palette[row.substring(x * cpp, (x + 1) * cpp)] ?: return null
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }
}
