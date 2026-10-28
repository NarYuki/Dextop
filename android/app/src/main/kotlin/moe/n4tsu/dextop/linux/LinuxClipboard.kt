package moe.n4tsu.dextop.linux

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log

/**
 * Shares the clipboard between Android and Linux applications (CLIPBOARD, and PRIMARY for
 * text from Android), unless turned off in the Linux settings.
 */
internal object LinuxClipboard {
    private const val TAG = "DextopLinuxClipboard"
    private const val PREFS = "linux_android_settings"
    private const val KEY = "clipboard_sync"

    /** Text last exchanged in either direction, so it is not sent back and forth. */
    @Volatile private var last: String? = null

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, enabled).apply()
    }

    /** Text a Linux application copied; Android may write the clipboard from the background. */
    fun toAndroid(context: Context, text: String) {
        if (!enabled(context) || text.isEmpty() || text == last) return
        last = text
        runCatching {
            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Linux", text))
        }.onFailure { Log.w(TAG, "Linux clipboard to Android failed", it) }
    }

    /**
     * Gives [container] Android's clipboard text. Android lets Dextop read it only while one of
     * its windows has input focus, so this runs on focus and right before a paste.
     */
    fun toLinux(context: Context, container: String) {
        if (!enabled(context)) return
        val text = runCatching {
            context.getSystemService(ClipboardManager::class.java).primaryClip
                ?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
        }.getOrNull() ?: return
        if (text.isEmpty() || text == last) return
        last = text
        LinuxContainerClient.service()?.runCatching { setClipboard(container, text) }
    }
}
