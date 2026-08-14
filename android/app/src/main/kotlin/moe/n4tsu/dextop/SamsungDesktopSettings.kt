package moe.n4tsu.dextop

import android.content.Context
import android.os.Build
import android.provider.Settings
import org.json.JSONObject

/** A narrow, validated bridge to settings used by Samsung's DeX settings UI. */
class SamsungDesktopSettings(private val context: Context) {
    private enum class Namespace { GLOBAL, SYSTEM, SECURE }
    private enum class ValueType { INT, LONG, FLOAT, STRING }
    private data class Entry(val namespace: Namespace, val type: ValueType, val default: String)
    private val privilegedAccess = PrivilegedAccess("DextopSamsungSettings")

    private val entries = linkedMapOf(
        "resolution" to Entry(Namespace.GLOBAL, ValueType.STRING, "FHD"),
        "screenZoom" to Entry(Namespace.GLOBAL, ValueType.INT, "160"),
        "fontScale" to Entry(Namespace.GLOBAL, ValueType.FLOAT, "1.0"),
        "screenTimeout" to Entry(Namespace.GLOBAL, ValueType.LONG, "600000"),
        "audioOutput" to Entry(Namespace.GLOBAL, ValueType.INT, "0"),
        "displayArrangement" to Entry(Namespace.GLOBAL, ValueType.INT, "2"),
        "displayOrientation" to Entry(Namespace.GLOBAL, ValueType.INT, "0"),
        "spenInputMode" to Entry(Namespace.GLOBAL, ValueType.INT, "1"),
        "autorunTouchpad" to Entry(Namespace.GLOBAL, ValueType.INT, "0"),
        "touchpadScrollDirection" to Entry(Namespace.SYSTEM, ValueType.INT, "0"),
        "touchKeyboard" to Entry(Namespace.GLOBAL, ValueType.INT, "0"),
        "keyboardDex" to Entry(Namespace.GLOBAL, ValueType.INT, "0"),
        "threeFingerGesture" to Entry(Namespace.GLOBAL, ValueType.INT, "4"),
        "fourFingerGesture" to Entry(Namespace.GLOBAL, ValueType.INT, "1"),
        "autoHideTaskbar" to Entry(Namespace.GLOBAL, ValueType.INT, "0"),
        "dexCommandArrow" to Entry(Namespace.SYSTEM, ValueType.INT, "0"),
        "includePhoneDisplay" to Entry(Namespace.SECURE, ValueType.INT, "0"),
        "mirrorPhoneDisplay" to Entry(Namespace.SECURE, ValueType.INT, "0")
    )

    private val platformKeys = mapOf(
        "resolution" to "external_display_resolution",
        "screenZoom" to "screen_zoom_on_external_desktop_display",
        "fontScale" to "font_scale_on_external_desktop_display",
        "screenTimeout" to "external_display_screen_timeout",
        "audioOutput" to "external_display_audio_output",
        "displayArrangement" to "external_display_arrangement",
        "displayOrientation" to "external_display_orientation",
        "spenInputMode" to "SPEN_INPUT_MODE_DEX",
        "autorunTouchpad" to "autorun_touchpad",
        "touchpadScrollDirection" to "touchpad_scrolling_direction",
        "touchKeyboard" to "touch_keyboard",
        "keyboardDex" to "keyboard_dex",
        "threeFingerGesture" to "three_finger_gesture",
        "fourFingerGesture" to "four_finger_gesture",
        "autoHideTaskbar" to "desktop_windowing_force_hide_taskbar",
        "dexCommandArrow" to "dex_cmd_arrow",
        "includePhoneDisplay" to "include_default_display_in_topology",
        "mirrorPhoneDisplay" to "mirror_built_in_display"
    )

    fun read(): Map<String, Any> {
        val values = entries.mapValues { (id, entry) -> readValue(platformKeys.getValue(id), entry) }
        return linkedMapOf<String, Any>(
            "supported" to isSamsungDevice(),
            "manufacturer" to Build.MANUFACTURER,
            "backupAvailable" to context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .contains(KEY_BACKUP),
            "values" to values
        )
    }

    fun backupIfNeeded(): Map<String, Any> {
        check(isSamsungDevice()) { "Samsung desktop settings are only available on Samsung devices" }
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!preferences.contains(KEY_BACKUP)) {
            val backup = JSONObject()
            entries.forEach { (id, entry) ->
                val raw = readRaw(platformKeys.getValue(id), entry)
                backup.put(id, raw ?: JSONObject.NULL)
            }
            preferences.edit().putString(KEY_BACKUP, backup.toString()).commit()
            OperationLog.i(context, "SamsungDesktopSettings", "original settings backed up")
        }
        return read()
    }

    fun restoreBackup(): Map<String, Any> {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val serialized = preferences.getString(KEY_BACKUP, null)
            ?: error("No Samsung desktop settings backup is available")
        val backup = JSONObject(serialized)
        check(privilegedAccess.isAvailable()) { NativeStrings.text("nativeShizukuUnavailable") }
        entries.forEach { (id, entry) ->
            val namespace = entry.namespace.name.lowercase()
            val key = platformKeys.getValue(id)
            val command = if (backup.isNull(id)) {
                privilegedAccess.execute("settings", "delete", namespace, key)
            } else {
                privilegedAccess.execute("settings", "put", namespace, key, backup.getString(id))
            }
            check(command.succeeded) { "Unable to restore Samsung desktop setting $key" }
        }
        preferences.edit().remove(KEY_BACKUP).commit()
        OperationLog.i(context, "SamsungDesktopSettings", "original settings restored")
        return read()
    }

    fun write(id: String, value: Any?) {
        check(isSamsungDevice()) {
            "Samsung desktop settings are only available on Samsung devices"
        }
        val entry = entries[id] ?: error("Unknown Samsung desktop setting: $id")
        val serialized = normalize(entry.type, value)
        val key = platformKeys.getValue(id)
        check(privilegedAccess.isAvailable()) { NativeStrings.text("nativeShizukuUnavailable") }
        val namespace = entry.namespace.name.lowercase()
        val command = privilegedAccess.execute("settings", "put", namespace, key, serialized)
        check(command.succeeded) {
            "Android rejected Samsung desktop setting $key: ${command.error.ifBlank { command.output }}"
        }
        OperationLog.i(context, "SamsungDesktopSettings", "$key=$serialized")
    }

    private fun readValue(key: String, entry: Entry): Any {
        val raw = readRaw(key, entry) ?: entry.default
        return when (entry.type) {
            ValueType.INT -> raw.toIntOrNull() ?: entry.default.toInt()
            ValueType.LONG -> raw.toLongOrNull() ?: entry.default.toLong()
            ValueType.FLOAT -> raw.toDoubleOrNull() ?: entry.default.toDouble()
            ValueType.STRING -> raw
        }
    }

    private fun readRaw(key: String, entry: Entry): String? {
        val resolver = context.contentResolver
        // Some One UI builds protect individual keys even from otherwise
        // readable Settings namespaces. Reading through the same shell identity
        // also avoids Android's per-process NameValueCache returning the value
        // from before a Shizuku write.
        val namespace = entry.namespace.name.lowercase()
        val privilegedValue = if (privilegedAccess.isAvailable()) {
            privilegedAccess.execute("settings", "get", namespace, key)
                .takeIf { it.succeeded }
                ?.output
                ?.takeUnless { it.isBlank() || it == "null" }
        } else null
        return privilegedValue ?: runCatching {
            when (entry.namespace) {
                Namespace.GLOBAL -> Settings.Global.getString(resolver, key)
                Namespace.SYSTEM -> Settings.System.getString(resolver, key)
                Namespace.SECURE -> Settings.Secure.getString(resolver, key)
            }
        }.getOrNull()
    }

    private fun isSamsungDevice(): Boolean =
        sequenceOf(Build.MANUFACTURER, Build.BRAND, Build.PRODUCT, Build.DEVICE)
            .any { it.contains("samsung", ignoreCase = true) } ||
            runCatching {
                Build.VERSION::class.java.getField("SEM_PLATFORM_INT").getInt(null) > 0
            }.getOrDefault(false)

    private fun normalize(type: ValueType, value: Any?): String = when (type) {
        ValueType.INT -> (value as? Number)?.toInt()?.toString()
            ?: value?.toString()?.toIntOrNull()?.toString()
            ?: error("An integer value is required")
        ValueType.LONG -> (value as? Number)?.toLong()?.toString()
            ?: value?.toString()?.toLongOrNull()?.toString()
            ?: error("A long value is required")
        ValueType.FLOAT -> (value as? Number)?.toDouble()?.toString()
            ?: value?.toString()?.toDoubleOrNull()?.toString()
            ?: error("A decimal value is required")
        ValueType.STRING -> value?.toString()?.takeIf { it.isNotBlank() }
            ?: error("A non-empty value is required")
    }

    companion object {
        private const val PREFS = "samsung_desktop_settings_backup"
        private const val KEY_BACKUP = "original_values_v1"
    }
}
