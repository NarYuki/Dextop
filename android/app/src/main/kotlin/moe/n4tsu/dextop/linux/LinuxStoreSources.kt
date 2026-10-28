package moe.n4tsu.dextop.linux

import android.content.Context
import android.util.Base64
import org.json.JSONObject

/** User trust choices govern Store operations, including jobs already in the queue.
 * This is Store policy, not a sandbox for commands entered in a terminal. */
internal object LinuxStoreSources {
    val ids = setOf("apt", "deb-get", "flathub")
    private fun prefs(context: Context) = context.getSharedPreferences("linux-store-sources", Context.MODE_PRIVATE)
    fun enabled(context: Context, source: String) = source in ids && prefs(context).getBoolean(source, source == "apt")
    fun read(context: Context) = ids.associateWith { enabled(context, it) }
    fun write(context: Context, source: String, enabled: Boolean): Map<String, Boolean> {
        require(source in ids) { "Unknown Store source" }
        check(prefs(context).edit().putBoolean(source, enabled).commit()) { "Cannot save Store sources" }
        return read(context)
    }

    fun requireEnabled(context: Context, source: String) {
        check(enabled(context, source)) { "Enable $source in Linux settings → Trusted Store sources first" }
    }

    /** Only Debian/Ubuntu are in scope; never send apt commands into Arch. */
    fun requireApt(service: ILinuxContainerService, container: String) {
        val list = org.json.JSONArray(service.listContainers())
        val distro = (0 until list.length()).map { list.getJSONObject(it) }
            .firstOrNull { it.optString("id") == container }?.optString("distro")
        require(distro in setOf("debian", "ubuntu")) { "Store supports Debian and Ubuntu only" }
    }

    fun helper(): String {
        val bytes = checkNotNull(LinuxSessionCompatibility.script("dextop-store"))
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return "mkdir -p /usr/local/bin; printf '%s' '$encoded' | base64 -d > /usr/local/bin/dextop-store; " +
            "chmod 755 /usr/local/bin/dextop-store; "
    }

    fun result(json: String): String {
        val result = JSONObject(json)
        check(result.optInt("exitCode", 1) == 0) { result.optString("output").takeLast(3000) }
        // Helpers put their protocol on the last line; provisioning output stays diagnostic.
        return result.optString("output").trim().lineSequence().lastOrNull().orEmpty()
    }
}
