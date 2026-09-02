package moe.n4tsu.dextop

import android.content.Context
import android.graphics.Color
import org.json.JSONObject

internal data class LaptopPalette(
    val background: Int,
    val key: Int,
    val keyVariant: Int,
    val border: Int,
    val text: Int,
    val trackpad: Int,
    val trackpadText: Int,
    val selected: Int,
    val radius: Float,
    val opacity: Float = 1f,
    val blur: Float = 0f,
    val keyOpacity: Float = 1f,
    val showTrackpadLabel: Boolean = true,
    val imageBase64: String? = null,
)

/** Reads keyboard themes without coupling persistence and JSON parsing to the service. */
internal class LaptopKeyboardThemeRepository(private val context: Context) {
    fun palette(id: String): LaptopPalette {
        val fallback = builtInPalette(id)
        val raw = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
            .getString("flutter.dextop_keyboard_theme_$id", null) ?: return fallback
        if (id in BUILT_IN_IDS) {
            return runCatching {
                val json = JSONObject(raw)
                fallback.copy(
                    opacity = json.optDouble("opacity", fallback.opacity.toDouble())
                        .toFloat().coerceIn(.1f, 1f),
                    keyOpacity = json.optDouble("keyOpacity", fallback.keyOpacity.toDouble())
                        .toFloat().coerceIn(.1f, 1f),
                    showTrackpadLabel = json.optBoolean("showTrackpadLabel", true),
                )
            }.getOrDefault(fallback)
        }
        return runCatching {
            val json = JSONObject(raw)
            fallback.copy(
                background = color(json.optString("background"), fallback.background),
                key = color(json.optString("key"), fallback.key),
                keyVariant = color(json.optString("keyVariant"), fallback.keyVariant),
                border = color(json.optString("border"), fallback.border),
                text = color(json.optString("text"), fallback.text),
                trackpad = color(json.optString("trackpad"), fallback.trackpad),
                trackpadText = color(json.optString("trackpadText"), fallback.trackpadText),
                selected = color(json.optString("selected"), fallback.selected),
                radius = json.optDouble("radius", fallback.radius.toDouble()).toFloat()
                    .coerceIn(0f, 40f),
                opacity = json.optDouble("opacity", 1.0).toFloat().coerceIn(.1f, 1f),
                blur = json.optDouble("blur", 0.0).toFloat().coerceIn(0f, 30f),
                keyOpacity = json.optDouble("keyOpacity", 1.0).toFloat().coerceIn(.1f, 1f),
                showTrackpadLabel = json.optBoolean("showTrackpadLabel", true),
                imageBase64 = json.optString("imageBase64").takeIf(String::isNotBlank),
            )
        }.getOrDefault(fallback)
    }

    private fun builtInPalette(id: String): LaptopPalette = when (id) {
        "crimson" -> LaptopPalette(
            Color.rgb(89, 14, 14), Color.rgb(110, 27, 27), Color.rgb(81, 10, 11),
            Color.rgb(126, 32, 27), Color.rgb(255, 190, 151), Color.rgb(90, 15, 16),
            Color.rgb(222, 137, 102), Color.rgb(81, 10, 11), 7f,
        )
        "cloud" -> LaptopPalette(
            Color.rgb(220, 235, 255), Color.WHITE, Color.rgb(247, 251, 255),
            Color.rgb(183, 212, 245), Color.rgb(66, 100, 134), Color.rgb(199, 221, 245),
            Color.rgb(82, 120, 159), Color.rgb(190, 218, 248), 16f, opacity = .94f,
        )
        "amoled" -> LaptopPalette(
            Color.BLACK, Color.BLACK, Color.rgb(3, 3, 3),
            Color.rgb(59, 59, 59), Color.rgb(220, 220, 220), Color.BLACK,
            Color.rgb(175, 175, 175), Color.rgb(81, 81, 81), 7f,
        )
        else -> LaptopPalette(
            Color.rgb(18, 18, 22), Color.rgb(48, 46, 54), Color.rgb(48, 46, 54),
            Color.rgb(76, 72, 84), Color.rgb(235, 231, 239), Color.rgb(35, 34, 40),
            Color.rgb(145, 141, 151), Color.rgb(208, 188, 237), 7f,
        )
    }

    private fun color(value: String?, fallback: Int): Int =
        runCatching { Color.parseColor(value.orEmpty()) }.getOrDefault(fallback)

    companion object {
        private val BUILT_IN_IDS = setOf("standard", "crimson", "cloud", "amoled")

        fun contrastingColor(color: Int): Int {
            val luminance = .299 * Color.red(color) +
                .587 * Color.green(color) +
                .114 * Color.blue(color)
            return if (luminance >= 160) Color.rgb(26, 24, 30) else Color.WHITE
        }
    }
}
