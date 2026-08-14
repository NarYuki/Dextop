package moe.n4tsu.dextop

import android.content.Context
import android.provider.Settings

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
        val overlay = if (preferences.getBoolean("overlay_was_null", false)) null
            else preferences.getString("overlay_before", "")
        check(Settings.Global.putString(
            resolver,
            DisplayMirrorBackend.DISPLAY_SPECIFICATION,
            overlay
        )) { "Unable to restore the original overlay display setting" }
        val actual = Settings.Global.getString(resolver, DisplayMirrorBackend.DISPLAY_SPECIFICATION)
        check(actual == overlay) { "The original overlay display setting was not restored" }
        return true
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
