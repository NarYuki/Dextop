package moe.n4tsu.dextop.linux

import android.content.Context

/**
 * Host-owned choice; Android is the default and explicitly saved choices are retained.
 *
 * [PRIVILEGED] runs Linux in the shell-user service, as before. [ANDROID] runs it under
 * Dextop's own app UID and uses nothing privileged.
 */
internal object LinuxExecutionMode {
    const val PRIVILEGED = "privileged"
    const val ANDROID = "android"
    private const val PREFS = "linux-execution"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun read(context: Context): String =
        resolve(prefs(context).getString("mode", null))

    /** The Google Play build runs Linux with ordinary app permissions only. */
    val privilegedSupported: Boolean get() = moe.n4tsu.dextop.BuildConfig.DISTRIBUTION_CHANNEL != "play"

    /** Missing, legacy Android ("hybrid"), unknown and unsupported values use ordinary app permissions. */
    fun resolve(stored: String?, privilegedAllowed: Boolean = privilegedSupported): String =
        if (stored == PRIVILEGED && privilegedAllowed) PRIVILEGED else ANDROID

    fun android(context: Context) = read(context) == ANDROID

    fun write(context: Context, mode: String) {
        require(mode == ANDROID || (mode == PRIVILEGED && privilegedSupported))
        check(prefs(context).edit().putString("mode", mode).commit())
    }

    /** Whether Dextop's privileged service may serve Linux: never in the Android mode. */
    fun privilegeAllowed(context: Context): Boolean = !android(context)
}
