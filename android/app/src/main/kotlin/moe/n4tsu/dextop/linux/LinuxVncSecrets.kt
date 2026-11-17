package moe.n4tsu.dextop.linux

import android.content.Context
import org.json.JSONObject

/**
 * Each environment's VNC password, kept by the app encrypted with Android Keystore
 * ([LinuxKeyring]'s key). The Linux service holds it only in memory: it never reaches the
 * container's files, its backups or snapshots.
 */
internal object LinuxVncSecrets {
    private const val PREFS = "linux-vnc"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Keeps the password an integration status reports for [containerId]. */
    fun remember(context: Context, containerId: String, status: String) {
        val secret = runCatching { JSONObject(status).optJSONObject("vnc")?.optString("secret") }.getOrNull()
        if (secret.isNullOrEmpty()) return
        val stored = prefs(context).getString(containerId, null)
        val same = stored != null && runCatching { LinuxKeyring.decrypt(stored) }.getOrNull()?.let { bytes ->
            try { String(bytes, Charsets.UTF_8) == secret } finally { bytes.fill(0) }
        } == true
        if (!same) prefs(context).edit().putString(containerId, LinuxKeyring.encrypt(secret)).apply()
    }

    /** Hands the stored passwords to a newly connected Linux service. */
    fun publish(context: Context, service: ILinuxContainerService) {
        prefs(context).all.forEach { (id, stored) ->
            val secret = runCatching { LinuxKeyring.decrypt(stored as String) }.getOrNull() ?: return@forEach
            try {
                service.integrationAction(id, JSONObject().put("op", "vnc-secret")
                    .put("secret", String(secret, Charsets.UTF_8)).toString())
            } catch (_: Exception) {} finally { secret.fill(0) }
        }
    }

    /** Forgets [containerId]'s VNC password (the environment was removed). */
    fun forget(context: Context, containerId: String) {
        prefs(context).edit().remove(containerId).apply()
    }
}
