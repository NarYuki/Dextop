package moe.n4tsu.dextop.linux

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The containers of both execution modes, for the container list.
 *
 * Only the selected mode's service is connected. The other mode's containers come from its
 * storage when the app may read it (the Android mode's storage always), otherwise from the
 * list last read while that mode was selected. They are listed for display only.
 */
internal object LinuxModeContainers {
    private const val PREFS = "linux-container-cache"
    private val KEPT = listOf("id", "name", "distro", "state", "created", "error")

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun other(mode: String) =
        if (mode == LinuxExecutionMode.ANDROID) LinuxExecutionMode.PRIVILEGED else LinuxExecutionMode.ANDROID

    private fun storage(context: Context, mode: String) = File(
        if (mode == LinuxExecutionMode.ANDROID) File(context.noBackupFilesDir, "linux-android")
        else File("/data/local/tmp/dextop/linux"), "containers")

    private fun summary(config: JSONObject) = JSONObject().apply {
        KEPT.forEach { key -> if (config.has(key) && !config.isNull(key)) put(key, config.get(key)) }
    }

    /** Remembers the selected mode's list, read from its connected service. */
    fun remember(context: Context, mode: String, json: String) {
        val list = JSONArray(json)
        val kept = JSONArray()
        for (i in 0 until list.length()) list.optJSONObject(i)?.let { kept.put(summary(it)) }
        prefs(context).edit().putString(mode, kept.toString()).apply()
    }

    /** The containers of the mode that is not selected, read from its storage or the remembered list. */
    fun inactive(context: Context, selected: String): List<JSONObject> {
        val mode = other(selected)
        val read = runCatching {
            val dir = storage(context, mode)
            val entries = dir.listFiles() ?: return@runCatching null
            entries.filter { it.isDirectory && it.name.matches(Regex("c[a-zA-Z0-9]+")) }.mapNotNull { entry ->
                runCatching { summary(JSONObject(File(entry, "config.json").readText())) }.getOrNull()
            }
        }.getOrNull()
        val list = read ?: runCatching {
            val array = JSONArray(prefs(context).getString(mode, "[]"))
            (0 until array.length()).mapNotNull(array::optJSONObject)
        }.getOrDefault(emptyList())
        if (read != null) prefs(context).edit().putString(mode, JSONArray(read).toString()).apply()
        return list.filter { it.optString("id").isNotEmpty() }
            .sortedBy { it.optLong("created") }
            .onEach { it.put("inactiveMode", mode).put("runtimeState", "stopped").put("running", false) }
    }

    fun clear(context: Context) { prefs(context).edit().clear().commit() }
}
