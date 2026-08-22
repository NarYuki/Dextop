package moe.n4tsu.dextop

import android.content.Context
import android.os.Build

/**
 * Keeps Pixel builds which lack VirtualDisplayConfig#setDisplayIdToMirror on
 * the pre-native AOSP desktop path. The decision is persisted for exactly one
 * firmware fingerprint so an OTA gets one clean capability probe again.
 */
internal object PixelMirrorFallback {
    private const val PREFS = "dextop_pixel_mirror_fallback"
    private const val FIRMWARE_KEY = "firmware_fingerprint"

    fun shouldUse(context: Context): Boolean {
        if (!isPixel()) return false
        val firmware = firmwareIdentity()
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = preferences.getString(FIRMWARE_KEY, null)
        if (stored == firmware) return true
        if (stored != null) {
            preferences.edit().remove(FIRMWARE_KEY).apply()
            OperationLog.i(context, "PixelMirror", "firmware changed; probing native Pixel mirror backend")
        }
        if (hasDisplayMirrorTargetApi()) return false
        preferences.edit().putString(FIRMWARE_KEY, firmware).apply()
        OperationLog.w(
            context,
            "PixelMirror",
            "setDisplayIdToMirror unavailable; using legacy Pixel/AOSP profile for this firmware"
        )
        return true
    }

    private fun isPixel(): Boolean =
        Build.MANUFACTURER.equals("google", ignoreCase = true) ||
            Build.BRAND.equals("google", ignoreCase = true)

    private fun firmwareIdentity(): String = Build.FINGERPRINT.ifBlank {
        "${Build.BRAND}/${Build.DEVICE}/${Build.ID}/${Build.VERSION.INCREMENTAL}"
    }

    private fun hasDisplayMirrorTargetApi(): Boolean = runCatching {
        Class.forName("android.hardware.display.VirtualDisplayConfig\$Builder")
            .getMethod("setDisplayIdToMirror", Int::class.javaPrimitiveType)
    }.isSuccess
}
