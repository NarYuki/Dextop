package moe.n4tsu.dextop.linux

import android.content.Context

/**
 * Host-owned choice; existing installations keep the privileged backend.
 *
 * [PRIVILEGED] runs Linux in the shell-user service, as before. [ANDROID] runs it under
 * Dextop's own app UID and uses nothing privileged.
 */
internal object LinuxExecutionMode {
    const val PRIVILEGED = "privileged"
    const val ANDROID = "android"
    /** Earlier development builds stored the Android mode as "hybrid". */
    private const val LEGACY_ANDROID = "hybrid"
    private const val PREFS = "linux-execution"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun read(context: Context): String =
        if (prefs(context).getString("mode", PRIVILEGED) in setOf(ANDROID, LEGACY_ANDROID)) ANDROID else PRIVILEGED

    fun android(context: Context) = read(context) == ANDROID

    fun write(context: Context, mode: String) {
        require(mode == PRIVILEGED || mode == ANDROID)
        check(prefs(context).edit().putString("mode", mode).commit())
    }

    /** Whether Dextop's privileged service may serve Linux: never in the Android mode. */
    fun privilegeAllowed(context: Context): Boolean = !android(context)
}
