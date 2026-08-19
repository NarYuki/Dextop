package moe.n4tsu.dextop

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display

/** Stable display settings exposed independently from experimental Samsung options. */
class DisplayEnvironmentSettings(private val context: Context) {
    private val privilegedAccess = PrivilegedAccess("DextopDisplaySettings")
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun read(): Map<String, Any> {
        migrateLegacyTopologyFlag()
        return linkedMapOf(
            "includePhoneDisplay" to if (topologyEnabled()) 1 else 0,
            // This card controls Android's desktop-mode taskbar only. Samsung
            // DeX has its own launcher-owned DexTaskbarWindow setting exposed
            // through SamsungDesktopSettings.
            "autoHideTaskbar" to readInt(
                "global",
                ANDROID_TASKBAR_FORCE_HIDE_KEY,
                0
            ),
            "supportsInternal120Hz" to supportsInternal120Hz(),
            "forceInternal120Hz" to preferences.getBoolean(KEY_FORCE_INTERNAL_120_HZ, false),
            "softwareCursorFallback" to preferences.getBoolean(KEY_SOFTWARE_CURSOR_FALLBACK, false),
            // Shizuku/Stellar is the capability gate for the platform uinput
            // command. Runtime registration may still fail on a vendor build;
            // MirrorService then falls back to its software cursor.
            "virtualMouseAvailable" to privilegedAccess.isAvailable()
        )
    }

    fun write(id: String, enabled: Boolean): Map<String, Any> {
        if (id == "includePhoneDisplay") {
            preferences.edit().putBoolean(KEY_DEXTOP_TOPOLOGY, enabled).apply()
            if (MirrorService.isActive()) {
                val overlayDisplayId = MirrorService.topologyOverlayDisplayId()
                if (enabled && overlayDisplayId >= 0) {
                    activateTopologyIfEnabled(overlayDisplayId)
                } else if (!enabled) {
                    restoreTopology()
                }
            }
            OperationLog.i(context, "DisplayEnvironmentSettings", "$KEY_DEXTOP_TOPOLOGY=$enabled")
            return read()
        }
        if (id == "forceInternal120Hz") {
            preferences.edit().putBoolean(KEY_FORCE_INTERNAL_120_HZ, enabled).apply()
            OperationLog.i(context, "DisplayEnvironmentSettings", "$KEY_FORCE_INTERNAL_120_HZ=$enabled")
            return read()
        }
        if (id == "softwareCursorFallback") {
            preferences.edit().putBoolean(KEY_SOFTWARE_CURSOR_FALLBACK, enabled).apply()
            MirrorService.setSoftwareCursorFallbackEnabled(enabled)
            OperationLog.i(context, "DisplayEnvironmentSettings", "$KEY_SOFTWARE_CURSOR_FALLBACK=$enabled")
            return read()
        }
        val target = when (id) {
            "autoHideTaskbar" -> "global" to ANDROID_TASKBAR_FORCE_HIDE_KEY
            else -> error("Unknown display environment setting: $id")
        }
        check(privilegedAccess.isAvailable()) { NativeStrings.text("nativeShizukuUnavailable") }
        val command = privilegedAccess.execute(
            "settings", "put", target.first, target.second, if (enabled) "1" else "0"
        )
        check(command.succeeded) {
            "Android rejected ${target.second}: ${command.error.ifBlank { command.output }}"
        }
        OperationLog.i(
            context,
            "DisplayEnvironmentSettings",
            "autoHideTaskbar backend=android_desktop_mode key=${target.second} value=${if (enabled) 1 else 0}"
        )
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

    fun supportsInternal120Hz(): Boolean {
        val display = context.getSystemService(DisplayManager::class.java)
            .getDisplay(Display.DEFAULT_DISPLAY) ?: return false
        return display.supportedModes.any { it.refreshRate >= 119f }
    }

    fun forceInternal120HzEnabled(): Boolean =
        preferences.getBoolean(KEY_FORCE_INTERNAL_120_HZ, false)

    fun activateTopologyIfEnabled(overlayDisplayId: Int) {
        if (overlayDisplayId < 0) return
        activateTopologyForOverlays(setOf(overlayDisplayId))
    }

    fun activateTopologyForOverlays(overlayDisplayIds: Set<Int>) {
        val active = overlayDisplayIds.filter { it >= 0 }.toSet()
        if (!topologyEnabled() || active.isEmpty()) return
        DisplayTopologyController(context).activateDextopTopology(active)
    }

    fun restoreTopology() {
        DisplayTopologyController(context).restoreDextopTopology()
    }

    fun prepareInactiveState() {
        migrateLegacyTopologyFlag()
        restoreTopology()
    }

    private fun topologyEnabled(): Boolean =
        preferences.getBoolean(KEY_DEXTOP_TOPOLOGY, true)

    private fun migrateLegacyTopologyFlag() {
        if (preferences.getBoolean(KEY_LEGACY_TOPOLOGY_MIGRATED, false)) return
        if (!privilegedAccess.isAvailable()) return
        val result = privilegedAccess.execute(
                "settings", "delete", "secure", "include_default_display_in_topology"
            )
        if (result.succeeded) {
            preferences.edit().putBoolean(KEY_LEGACY_TOPOLOGY_MIGRATED, true).apply()
        }
    }

    companion object {
        private const val PREFERENCES = "dextop_display_environment"
        private const val ANDROID_TASKBAR_FORCE_HIDE_KEY =
            "desktop_windowing_force_hide_taskbar"
        private const val KEY_FORCE_INTERNAL_120_HZ = "force_internal_120_hz"
        private const val KEY_SOFTWARE_CURSOR_FALLBACK = "software_cursor_fallback"
        private const val KEY_DEXTOP_TOPOLOGY = "include_dextop_topology"
        private const val KEY_LEGACY_TOPOLOGY_MIGRATED = "legacy_topology_migrated"
    }
}
