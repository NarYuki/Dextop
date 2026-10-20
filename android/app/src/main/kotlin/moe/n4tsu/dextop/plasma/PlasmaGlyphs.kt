package moe.n4tsu.dextop.plasma

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.core.graphics.PathParser

/**
 * Symbolic icons for the Plasma chrome.
 *
 * Most glyphs are Material Symbols Rounded ([MaterialSymbols]). The KDE
 * launcher and the Breeze window-decoration buttons stay hand-drawn on a
 * 24-unit grid so windows keep their Plasma look.
 */
internal enum class Glyph {
    KDE_LAUNCHER, MINIMIZE, MAXIMIZE, RESTORE, CLOSE, KEEP_ABOVE, SEARCH, POWER, RESTART,
    LOCK, LOGOUT, SLEEP, NETWORK, VOLUME, VOLUME_MUTED, BATTERY, BATTERY_CHARGING, OVERVIEW,
    DESKTOP, SETTINGS, APPS, STAR, HISTORY, CHEVRON_RIGHT, CHEVRON_UP, CHEVRON_DOWN, PIN,
    NOTIFICATIONS, CLIPBOARD, SHOW_DESKTOP, WINDOW, BACK, COMPUTER, KEYBOARD, PLUS, CHECK,
    WALLPAPER, TILE, BRIGHTNESS, INFO,

    // Status and system controls. WIFI, CELLULAR, VOLUME_LEVEL, BATTERY and
    // BATTERY_CHARGING pick their variant from GlyphDrawable.level01.
    WIFI, WIFI_OFF, CELLULAR, CELLULAR_OFF, AIRPLANE, BLUETOOTH, BLUETOOTH_CONNECTED,
    BLUETOOTH_OFF, ETHERNET, HOTSPOT, VOLUME_LEVEL, BATTERY_SAVER, BOLT, THERMOMETER,
    BRIGHTNESS_AUTO, BRIGHTNESS_LOW, BRIGHTNESS_HIGH, DARK_MODE, PLAY, PAUSE, SKIP_NEXT,
    SKIP_PREVIOUS, MUSIC, RINGTONE, ALARM, HEADPHONES, SPEAKER, NOTIFICATIONS_OFF, REFRESH,
    SCHEDULE, OPEN_EXTERNAL, LOCK_OPEN, KEY,
}

internal class GlyphDrawable(
    private val glyph: Glyph,
    var color: Int,
    private val strokeUnits: Float = 1.6f,
    /** Use the filled Material Symbol variant where one exists. */
    var filled: Boolean = false,
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
        GlyphPainter.paint(canvas, glyph, stroke, fill, level01, filled)
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
    private val symbolCache = HashMap<String, Path>()
    /** Maps the 960-unit Material viewport (y from -960 to 0) onto the 24-unit grid. */
    private val symbolMatrix = Matrix().apply {
        setTranslate(0f, 960f)
        postScale(24f / 960f, 24f / 960f)
    }

    fun paint(canvas: Canvas, glyph: Glyph, stroke: Paint, fill: Paint, level: Float, filled: Boolean = false) {
        val symbol = symbolName(glyph, level.coerceIn(0f, 1f))
        if (symbol != null) {
            symbolPath(symbol, filled)?.let { canvas.drawPath(it, fill) }
            return
        }
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
            else -> Unit
        }
    }

    private fun symbolPath(name: String, filled: Boolean): Path? {
        val key = if (filled) "$name#fill" else name
        symbolCache[key]?.let { return it }
        val data = MaterialSymbols.path(name, filled) ?: return null
        val parsed = runCatching { PathParser.createPathFromPathData(data) }.getOrNull() ?: return null
        parsed.transform(symbolMatrix)
        symbolCache[key] = parsed
        return parsed
    }

    private val batteryBars = listOf(
        "battery_0_bar", "battery_1_bar", "battery_2_bar", "battery_3_bar",
        "battery_4_bar", "battery_5_bar", "battery_6_bar", "battery_full",
    )

    /** Material Symbol for [glyph]; null for the hand-drawn Plasma glyphs. */
    fun symbolName(glyph: Glyph, level: Float): String? = when (glyph) {
        Glyph.KDE_LAUNCHER, Glyph.MINIMIZE, Glyph.MAXIMIZE, Glyph.RESTORE, Glyph.CLOSE, Glyph.KEEP_ABOVE -> null
        Glyph.SEARCH -> "search"
        Glyph.POWER -> "power_settings_new"
        Glyph.RESTART -> "restart_alt"
        Glyph.LOCK -> "lock"
        Glyph.LOCK_OPEN -> "lock_open"
        Glyph.LOGOUT -> "logout"
        Glyph.SLEEP -> "bedtime"
        Glyph.NETWORK -> "wifi"
        Glyph.WIFI -> when {
            level <= 0f -> "signal_wifi_0_bar"
            level < .34f -> "wifi_1_bar"
            level < .67f -> "wifi_2_bar"
            else -> "wifi"
        }
        Glyph.WIFI_OFF -> "wifi_off"
        Glyph.CELLULAR -> when {
            level < .34f -> "signal_cellular_alt_1_bar"
            level < .67f -> "signal_cellular_alt_2_bar"
            else -> "signal_cellular_alt"
        }
        Glyph.CELLULAR_OFF -> "signal_cellular_off"
        Glyph.AIRPLANE -> "airplanemode_active"
        Glyph.BLUETOOTH -> "bluetooth"
        Glyph.BLUETOOTH_CONNECTED -> "bluetooth_connected"
        Glyph.BLUETOOTH_OFF -> "bluetooth_disabled"
        Glyph.ETHERNET -> "lan"
        Glyph.HOTSPOT -> "wifi_tethering"
        Glyph.VOLUME -> "volume_up"
        Glyph.VOLUME_MUTED -> "volume_off"
        Glyph.VOLUME_LEVEL -> when {
            level <= 0f -> "volume_mute"
            level < .5f -> "volume_down"
            else -> "volume_up"
        }
        Glyph.BATTERY -> batteryBars[(level * (batteryBars.size - 1) + .5f).toInt().coerceIn(0, batteryBars.size - 1)]
        Glyph.BATTERY_CHARGING -> when {
            level < .25f -> "battery_charging_20"
            level < .4f -> "battery_charging_30"
            level < .55f -> "battery_charging_50"
            level < .7f -> "battery_charging_60"
            level < .85f -> "battery_charging_80"
            level < .95f -> "battery_charging_90"
            else -> "battery_charging_full"
        }
        Glyph.BATTERY_SAVER -> "battery_saver"
        Glyph.BOLT -> "bolt"
        Glyph.THERMOMETER -> "thermostat"
        Glyph.OVERVIEW -> "overview_key"
        Glyph.DESKTOP, Glyph.SHOW_DESKTOP -> "desktop_windows"
        Glyph.SETTINGS -> "settings"
        Glyph.APPS -> "apps"
        Glyph.STAR -> "star"
        Glyph.HISTORY -> "history"
        Glyph.CHEVRON_RIGHT -> "chevron_right"
        Glyph.CHEVRON_UP -> "expand_less"
        Glyph.CHEVRON_DOWN -> "expand_more"
        Glyph.PIN -> "keep"
        Glyph.NOTIFICATIONS -> "notifications"
        Glyph.NOTIFICATIONS_OFF -> "notifications_off"
        Glyph.CLIPBOARD -> "content_paste"
        Glyph.WINDOW -> "web_asset"
        Glyph.BACK -> "arrow_back"
        Glyph.COMPUTER -> "computer"
        Glyph.KEYBOARD -> "keyboard"
        Glyph.PLUS -> "add"
        Glyph.CHECK -> "check"
        Glyph.WALLPAPER -> "wallpaper"
        Glyph.TILE -> "dashboard"
        Glyph.BRIGHTNESS -> "brightness_medium"
        Glyph.BRIGHTNESS_AUTO -> "brightness_auto"
        Glyph.BRIGHTNESS_LOW -> "brightness_low"
        Glyph.BRIGHTNESS_HIGH -> "brightness_high"
        Glyph.DARK_MODE -> "dark_mode"
        Glyph.INFO -> "info"
        Glyph.PLAY -> "play_arrow"
        Glyph.PAUSE -> "pause"
        Glyph.SKIP_NEXT -> "skip_next"
        Glyph.SKIP_PREVIOUS -> "skip_previous"
        Glyph.MUSIC -> "music_note"
        Glyph.RINGTONE -> "ring_volume"
        Glyph.ALARM -> "alarm"
        Glyph.HEADPHONES -> "headphones"
        Glyph.SPEAKER -> "speaker"
        Glyph.REFRESH -> "refresh"
        Glyph.SCHEDULE -> "schedule"
        Glyph.OPEN_EXTERNAL -> "open_in_new"
        Glyph.KEY -> "vpn_key"
    }
}
