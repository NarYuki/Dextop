package moe.n4tsu.dextop.plasma

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.view.animation.PathInterpolator

/**
 * Breeze colour scheme and Kirigami metrics used by Plasma 6.
 *
 * Values follow the stock "Breeze Dark" / "Breeze Light" colour schemes that
 * ship with Plasma 6 so the Dextop desktop reads as the real thing.
 */
internal data class PlasmaPalette(
    val dark: Boolean,
    val windowBackground: Int,
    val viewBackground: Int,
    val headerActive: Int,
    val headerInactive: Int,
    val text: Int,
    val inactiveText: Int,
    val disabledText: Int,
    val accent: Int,
    val accentText: Int,
    val negative: Int,
    val positive: Int,
    val neutral: Int,
    val panel: Int,
    val popup: Int,
    val separator: Int,
    val hover: Int,
    val pressed: Int,
    val frameOutline: Int,
    val shadow: Int,
)

internal object PlasmaTheme {
    const val ACCENT_BREEZE = 0xFF3DAEE9.toInt()

    val breezeDark = PlasmaPalette(
        dark = true,
        windowBackground = 0xFF202326.toInt(),
        viewBackground = 0xFF141618.toInt(),
        headerActive = 0xFF2A2E32.toInt(),
        headerInactive = 0xFF202326.toInt(),
        text = 0xFFFCFCFC.toInt(),
        inactiveText = 0xFFA1A9B1.toInt(),
        disabledText = 0xFF6E7175.toInt(),
        accent = ACCENT_BREEZE,
        accentText = 0xFFFCFCFC.toInt(),
        negative = 0xFFDA4453.toInt(),
        positive = 0xFF27AE60.toInt(),
        neutral = 0xFFF67400.toInt(),
        panel = 0xF0202326.toInt(),
        popup = 0xF5202326.toInt(),
        separator = 0x33FCFCFC,
        hover = 0x333DAEE9,
        pressed = 0x663DAEE9,
        frameOutline = 0x40000000,
        shadow = 0x8C000000.toInt(),
    )

    val breezeLight = PlasmaPalette(
        dark = false,
        windowBackground = 0xFFEFF0F1.toInt(),
        viewBackground = 0xFFFFFFFF.toInt(),
        headerActive = 0xFFDEE0E2.toInt(),
        headerInactive = 0xFFEFF0F1.toInt(),
        text = 0xFF232629.toInt(),
        inactiveText = 0xFF707D8A.toInt(),
        disabledText = 0xFFA0A5AA.toInt(),
        accent = ACCENT_BREEZE,
        accentText = 0xFFFFFFFF.toInt(),
        negative = 0xFFDA4453.toInt(),
        positive = 0xFF27AE60.toInt(),
        neutral = 0xFFF67400.toInt(),
        panel = 0xF2EFF0F1.toInt(),
        popup = 0xF8FFFFFF.toInt(),
        separator = 0x26232629,
        hover = 0x333DAEE9,
        pressed = 0x663DAEE9,
        frameOutline = 0x33000000,
        shadow = 0x59000000,
    )

    /** Kirigami.Units.longDuration and the Plasma "OutCubic" easing. */
    const val SHORT_MS = 100L
    const val LONG_MS = 200L
    const val VERY_LONG_MS = 400L
    val outCubic = PathInterpolator(0.33f, 1f, 0.68f, 1f)
    val inOutCubic = PathInterpolator(0.65f, 0f, 0.35f, 1f)
    val inCubic = PathInterpolator(0.32f, 0f, 0.67f, 0f)

    val sans: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    val sansMedium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    val sansLight: Typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)

    fun resolve(context: Context, settings: PlasmaSettings): PlasmaPalette {
        val dark = when (settings.colorScheme) {
            "light" -> false
            "dark" -> true
            else -> context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        }
        val base = if (dark) breezeDark else breezeLight
        val accent = settings.accentColor
        return if (accent == ACCENT_BREEZE) base else base.copy(
            accent = accent,
            hover = withAlpha(accent, 0x33),
            pressed = withAlpha(accent, 0x66),
        )
    }

    fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    fun blend(from: Int, to: Int, fraction: Float): Int {
        val t = fraction.coerceIn(0f, 1f)
        return Color.argb(
            (Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * t).toInt(),
            (Color.red(from) + (Color.red(to) - Color.red(from)) * t).toInt(),
            (Color.green(from) + (Color.green(to) - Color.green(from)) * t).toInt(),
            (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t).toInt(),
        )
    }
}

/** User preferences for the Dextop window manager, stored by Flutter. */
internal data class PlasmaSettings(
    val colorScheme: String = "dark",
    val accentColor: Int = PlasmaTheme.ACCENT_BREEZE,
    val floatingPanel: Boolean = true,
    val virtualDesktops: Int = 4,
    val animationSpeed: Float = 1f,
    val hotCorner: Boolean = true,
    val wallpaper: String = "plasma",
) {
    /** Returns a duration scaled by the animation slider; 0 disables animation. */
    fun duration(base: Long): Long = if (animationSpeed <= 0f) 0L else (base / animationSpeed).toLong()

    companion object {
        private const val PREFS = "FlutterSharedPreferences"

        fun load(context: Context): PlasmaSettings {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            fun long(key: String, fallback: Long) = runCatching { prefs.getLong(key, fallback) }.getOrDefault(fallback)
            fun double(key: String, fallback: Double): Double = runCatching {
                // shared_preferences stores doubles as encoded strings.
                prefs.getString(key, null)?.removePrefix(DOUBLE_PREFIX)?.toDouble() ?: fallback
            }.getOrDefault(fallback)
            return PlasmaSettings(
                colorScheme = prefs.getString("flutter.plasma_color_scheme", "dark") ?: "dark",
                accentColor = long("flutter.plasma_accent_color", PlasmaTheme.ACCENT_BREEZE.toLong() and 0xFFFFFFFFL).toInt(),
                floatingPanel = prefs.getBoolean("flutter.plasma_floating_panel", true),
                virtualDesktops = long("flutter.plasma_virtual_desktops", 4L).toInt().coerceIn(1, 6),
                animationSpeed = double("flutter.plasma_animation_speed", 1.0).toFloat().coerceIn(0f, 3f),
                hotCorner = prefs.getBoolean("flutter.plasma_hot_corner", true),
                wallpaper = prefs.getString("flutter.plasma_wallpaper", "plasma") ?: "plasma",
            )
        }

        /** Prefix used by the shared_preferences Android plugin for doubles. */
        private const val DOUBLE_PREFIX = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBEb3VibGUu"
    }
}

/** Which window manager owns the Dextop display. */
internal object WindowManagerSelection {
    const val SYSTEM = "system"
    const val DEXTOP_PLASMA = "dextop_plasma"
    private const val KEY = "flutter.window_manager"
    private const val EXPERIMENTAL_ENABLED_KEY = "flutter.experimental_dextop_window_manager"

    fun current(context: Context): String {
        val preferences = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
        if (!preferences.getBoolean(EXPERIMENTAL_ENABLED_KEY, false)) return SYSTEM
        return preferences.getString(KEY, SYSTEM) ?: SYSTEM
    }

    fun isPlasma(context: Context): Boolean = current(context) == DEXTOP_PLASMA
}
