package moe.n4tsu.dextop

import android.content.Context

/** Temporarily pins the built-in panel to 120 Hz for the lifetime of a Dextop session. */
internal class InternalRefreshRateController(
    private val context: Context,
    private val sessionJournal: SessionJournal
) {
    private val privilegedAccess = PrivilegedAccess("DextopRefreshRate")
    private val settings = DisplayEnvironmentSettings(context)

    fun applyIfEnabled() {
        if (!settings.forceInternal120HzEnabled() || !settings.supportsInternal120Hz()) return
        check(privilegedAccess.isAvailable()) { NativeStrings.text("nativeShizukuUnavailable") }
        KEYS.forEach { key ->
            val current = read(key)
            sessionJournal.rememberSystem(key, current)
            if (current?.toFloatOrNull()?.let { kotlin.math.abs(it - 120f) < 0.1f } == true) {
                return@forEach
            }
            val result = privilegedAccess.execute("settings", "put", "system", key, "120.0")
            check(result.succeeded) {
                "Android rejected $key: ${result.error.ifBlank { result.output }}"
            }
        }
        OperationLog.i(context, "InternalRefreshRate", "built-in display pinned to 120 Hz")
    }

    fun restore() {
        sessionJournal.restoreSystemKeys(KEYS)
    }

    fun keepCurrentValue() {
        applyIfEnabled()
        sessionJournal.discardSystemKeys(KEYS)
        OperationLog.i(context, "InternalRefreshRate", "kept 120 Hz after external display disconnect")
    }

    fun isEnabledAndSupported(): Boolean =
        settings.forceInternal120HzEnabled() && settings.supportsInternal120Hz()

    private fun read(key: String): String? = privilegedAccess
        .execute("settings", "get", "system", key)
        .takeIf { it.succeeded }
        ?.output
        ?.trim()
        ?.takeUnless { it.isBlank() || it == "null" }

    companion object {
        private val KEYS = listOf("min_refresh_rate", "peak_refresh_rate")
    }
}
