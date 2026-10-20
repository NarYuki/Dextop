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
