package moe.n4tsu.dextop.privilege

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Keys that Dextop gives the privileged processes it starts, so the provider that receives
 * their Binders can tell them from other processes running as the shell user (which, with full
 * privilege, includes every Linux program). A new key is made each time Dextop starts such a
 * process; it is kept in Dextop's private storage, which the shell cannot read.
 */
internal object PublisherKeys {
    const val RUNTIME = "runtime"
    const val LINUX = "linux"
    const val EXTRA = "publisherKey"
    private const val PREFS = "privilege-publishers"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** A new key for the [kind] of process about to be started; the previous one stops working. */
    fun issue(context: Context, kind: String): String {
        val key = Base64.encodeToString(ByteArray(24).also(SecureRandom()::nextBytes),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        check(prefs(context).edit().putString(kind, key).remove("$kind-pid").commit()) { "Cannot store the privileged process key" }
        return key
    }

    /** PID returned over the paired ADB connection, never supplied by the publisher. */
    fun bindRuntime(context: Context, output: String): Boolean {
        val pid = Regex("(?m)^DEXTOP_RUNTIME_PID=(\\d+)\\r?$").find(output)
            ?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 } ?: return false
        return prefs(context).edit().putInt("$RUNTIME-pid", pid).commit()
    }

    fun runtimePid(context: Context): Int = prefs(context).getInt("$RUNTIME-pid", -1)

    /** Whether [presented] is the current key for [kind]. */
    fun matches(context: Context, kind: String, presented: String?): Boolean {
        val expected = prefs(context).getString(kind, null) ?: return false
        return presented != null && MessageDigest.isEqual(expected.toByteArray(), presented.toByteArray())
    }
}
