package moe.n4tsu.dextop

import android.app.WallpaperManager
import android.content.Context
import android.provider.Settings
import android.util.Log

internal class SessionJournal(private val context: Context) {
    private val preferences = context.getSharedPreferences("dextop_session_journal", Context.MODE_PRIVATE)

    fun preparing(width: Int, height: Int, density: Int, decorations: Boolean) {
        val editor = preferences.edit()
        if (!preferences.getBoolean("transaction_open", false)) {
            val previousOverlay = Settings.Global.getString(
                context.contentResolver,
                DisplayMirrorBackend.DISPLAY_SPECIFICATION
            )
            editor
                .putBoolean("transaction_open", true)
                .putBoolean("overlay_was_null", previousOverlay == null)
                .putString("overlay_before", previousOverlay.orEmpty())
            val wallpaper = WallpaperManager.getInstance(context)
            val display = context.getSystemService(android.hardware.display.DisplayManager::class.java)
                .getDisplay(android.view.Display.DEFAULT_DISPLAY)
            val mode = display?.mode
            val wallpaperWidth = wallpaper.desiredMinimumWidth.takeIf { it > 0 }
                ?: mode?.physicalWidth ?: context.resources.displayMetrics.widthPixels
            val wallpaperHeight = wallpaper.desiredMinimumHeight.takeIf { it > 0 }
                ?: mode?.physicalHeight ?: context.resources.displayMetrics.heightPixels
            editor
                .putInt("wallpaper_width_before", wallpaperWidth)
                .putInt("wallpaper_height_before", wallpaperHeight)
        }
        check(editor
            .putString("phase", "preparing")
            .putInt("width", width)
            .putInt("height", height)
            .putInt("density", density)
            .putBoolean("decorations", decorations)
            .putLong("updated_at", System.currentTimeMillis())
            .commit()) { "Unable to persist the Dextop recovery journal" }
    }

    fun rememberGlobal(key: String, value: String?) {
        if (preferences.contains("global_present_$key")) return
        check(preferences.edit()
            .putBoolean("global_present_$key", value != null)
            .putString("global_value_$key", value.orEmpty())
            .commit()) { "Unable to persist the original value of $key" }
    }

    fun rememberSystem(key: String, value: String?) {
        if (preferences.contains("system_present_$key")) return
        check(preferences.edit()
            .putBoolean("system_present_$key", value != null)
            .putString("system_value_$key", value.orEmpty())
            .commit()) { "Unable to persist the original value of $key" }
    }

    fun restoreSystemKeys(keys: Collection<String>) {
        val privilegedAccess = PrivilegedAccess("DextopSessionRecovery")
        keys.forEach { key ->
            if (!preferences.contains("system_present_$key")) return@forEach
            check(privilegedAccess.isAvailable()) { NativeStrings.text("nativeShizukuUnavailable") }
            val present = preferences.getBoolean("system_present_$key", false)
            val result = if (present) {
                privilegedAccess.execute(
                    "settings", "put", "system", key,
                    preferences.getString("system_value_$key", "").orEmpty()
                )
            } else {
                privilegedAccess.execute("settings", "delete", "system", key)
            }
            check(result.succeeded) {
                "Unable to restore $key: ${result.error.ifBlank { result.output }}"
            }
        }
    }

    fun discardSystemKeys(keys: Collection<String>) {
        val editor = preferences.edit()
        keys.forEach { key ->
            editor.remove("system_present_$key").remove("system_value_$key")
        }
        check(editor.commit()) { "Unable to discard persisted system-setting snapshots" }
    }

    fun rememberPhoneRotation(frozen: Boolean, rotation: Int) {
        if (preferences.contains("phone_rotation_frozen")) return
        check(preferences.edit()
            .putBoolean("phone_rotation_frozen", frozen)
            .putInt("phone_rotation_value", rotation)
            .commit()) { "Unable to persist the original phone rotation state" }
    }

    fun phoneRotationState(): PhoneRotationState? {
        if (!preferences.contains("phone_rotation_frozen")) return null
        return PhoneRotationState(
            preferences.getBoolean("phone_rotation_frozen", false),
            preferences.getInt("phone_rotation_value", 0)
        )
    }

    fun restoreSystemSettings(): Boolean {
        if (!preferences.getBoolean("transaction_open", false)) return true
        val resolver = context.contentResolver
        val keys = listOf(
            "enable_freeform_support",
            "force_resizable_activities",
            "force_desktop_mode_on_external_displays"
        )
        keys.forEach { key ->
            if (preferences.contains("global_present_$key")) {
                val value = if (preferences.getBoolean("global_present_$key", false)) {
                    preferences.getString("global_value_$key", "")
                } else null
                check(Settings.Global.putString(resolver, key, value)) {
                    "Unable to restore $key"
                }
            }
        }
        restoreSystemKeys(listOf("min_refresh_rate", "peak_refresh_rate"))
        val overlay = if (preferences.getBoolean("overlay_was_null", false)) null
            else preferences.getString("overlay_before", "")
        check(Settings.Global.putString(
            resolver,
            DisplayMirrorBackend.DISPLAY_SPECIFICATION,
            overlay
        )) { "Unable to restore the original overlay display setting" }
        val actual = Settings.Global.getString(resolver, DisplayMirrorBackend.DISPLAY_SPECIFICATION)
        check(actual == overlay) { "The original overlay display setting was not restored" }
        restoreWallpaperDimensions()
        return true
    }

    /**
     * Re-sends the pre-session wallpaper dimensions after a temporary pane or
     * virtual-display size has disappeared. OEM launchers do not always issue
     * this hint themselves, leaving the wallpaper cropped after Dextop exits.
     */
    fun restoreWallpaperDimensions(): Boolean {
        val display = context.getSystemService(android.hardware.display.DisplayManager::class.java)
            .getDisplay(android.view.Display.DEFAULT_DISPLAY)
        val mode = display?.mode
        val width = preferences.getInt("wallpaper_width_before", 0).takeIf { it > 0 }
            ?: mode?.physicalWidth ?: context.resources.displayMetrics.widthPixels
        val height = preferences.getInt("wallpaper_height_before", 0).takeIf { it > 0 }
            ?: mode?.physicalHeight ?: context.resources.displayMetrics.heightPixels
        return runCatching {
            WallpaperManager.getInstance(context).suggestDesiredDimensions(width, height)
            Log.i("DextopSessionJournal", "wallpaper dimensions restored to ${width}x$height")
            true
        }.onFailure {
            Log.e("DextopSessionJournal", "wallpaper dimension restoration failed", it)
        }.getOrDefault(false)
    }

    fun running(displayId: Int) {
        preferences.edit()
            .putString("phase", "running")
            .putInt("display_id", displayId)
            .putLong("updated_at", System.currentTimeMillis())
            .apply()
    }

    fun paused() {
        preferences.edit()
            .putString("phase", "paused")
            .putInt("display_id", -1)
            .putLong("updated_at", System.currentTimeMillis())
            .commit()
    }

    fun snapshot(): Map<String, Any> = mapOf(
        "recoverable" to (preferences.getString("phase", "idle") != "idle"),
        "phase" to preferences.getString("phase", "idle").orEmpty(),
        "width" to preferences.getInt("width", 0),
        "height" to preferences.getInt("height", 0),
        "density" to preferences.getInt("density", 0),
        "decorations" to preferences.getBoolean("decorations", false),
        "transactionOpen" to preferences.getBoolean("transaction_open", false),
        "updatedAt" to preferences.getLong("updated_at", 0L)
    )

    fun clear() {
        preferences.edit().clear().putString("phase", "idle").apply()
    }
}
