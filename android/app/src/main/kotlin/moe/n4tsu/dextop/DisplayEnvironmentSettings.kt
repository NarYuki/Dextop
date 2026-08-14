package moe.n4tsu.dextop

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display

/** Stable display settings exposed independently from experimental Samsung options. */
class DisplayEnvironmentSettings(private val context: Context) {
    private val privilegedAccess = PrivilegedAccess("DextopDisplaySettings")
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun read(): Map<String, Any> {
        val includePhoneDisplay = readString("secure", "include_default_display_in_topology")
            ?.toIntOrNull()
            ?: enableTopologyByDefault()
        return linkedMapOf(
            "includePhoneDisplay" to includePhoneDisplay,
            "autoHideTaskbar" to readInt("global", "desktop_windowing_force_hide_taskbar", 0),
            "supportsInternal120Hz" to supportsInternal120Hz(),
            "forceInternal120Hz" to preferences.getBoolean(KEY_FORCE_INTERNAL_120_HZ, false)
        )
    }

    fun write(id: String, enabled: Boolean): Map<String, Any> {
        if (id == "forceInternal120Hz") {
            preferences.edit().putBoolean(KEY_FORCE_INTERNAL_120_HZ, enabled).apply()
            OperationLog.i(context, "DisplayEnvironmentSettings", "$KEY_FORCE_INTERNAL_120_HZ=$enabled")
            return read()
        }
        val target = when (id) {
            "includePhoneDisplay" -> "secure" to "include_default_display_in_topology"
            "autoHideTaskbar" -> "global" to "desktop_windowing_force_hide_taskbar"
            else -> error("Unknown display environment setting: $id")
        }
        check(privilegedAccess.isAvailable()) { NativeStrings.text("nativeShizukuUnavailable") }
        val command = privilegedAccess.execute(
            "settings", "put", target.first, target.second, if (enabled) "1" else "0"
        )
        check(command.succeeded) {
            "Android rejected ${target.second}: ${command.error.ifBlank { command.output }}"
        }
        OperationLog.i(context, "DisplayEnvironmentSettings", "${target.second}=${if (enabled) 1 else 0}")
        return read()
    }

    private fun readInt(namespace: String, key: String, fallback: Int): Int {
        return readString(namespace, key)?.toIntOrNull() ?: fallback
    }

    private fun readString(namespace: String, key: String): String? {
        if (!privilegedAccess.isAvailable()) return null
        return privilegedAccess.execute("settings", "get", namespace, key)
            .takeIf { it.succeeded }
            ?.output
            ?.trim()
            ?.takeUnless { it.isBlank() || it == "null" }
    }

    private fun enableTopologyByDefault(): Int {
        if (!privilegedAccess.isAvailable()) return 1
        val result = privilegedAccess.execute(
            "settings", "put", "secure", "include_default_display_in_topology", "1"
        )
        if (!result.succeeded) return 1
        OperationLog.i(context, "DisplayEnvironmentSettings", "include_default_display_in_topology=1 (default)")
        return 1
    }

    fun supportsInternal120Hz(): Boolean {
        val display = context.getSystemService(DisplayManager::class.java)
            .getDisplay(Display.DEFAULT_DISPLAY) ?: return false
        return display.supportedModes.any { it.refreshRate >= 119f }
    }

    fun forceInternal120HzEnabled(): Boolean =
        preferences.getBoolean(KEY_FORCE_INTERNAL_120_HZ, false)

    fun refreshTopologyIfEnabled() {
        if (readInt("secure", "include_default_display_in_topology", 1) != 1) return
        check(privilegedAccess.isAvailable()) { NativeStrings.text("nativeShizukuUnavailable") }
        listOf("0", "1").forEachIndexed { index, value ->
            val result = privilegedAccess.execute(
                "settings", "put", "secure", "include_default_display_in_topology", value
            )
            check(result.succeeded) {
                "Unable to refresh display topology: ${result.error.ifBlank { result.output }}"
            }
            if (index == 0) android.os.SystemClock.sleep(120)
        }
        OperationLog.i(context, "DisplayEnvironmentSettings", "display topology refreshed 0 -> 1")
    }

    companion object {
        private const val PREFERENCES = "dextop_display_environment"
        private const val KEY_FORCE_INTERNAL_120_HZ = "force_internal_120_hz"
    }
}
