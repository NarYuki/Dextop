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
    private val KEPT = listOf("id", "name", "distro", "state", "created", "error", "bytes")

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun rememberSettings(context: Context, mode: String, json: String) {
        prefs(context).edit().putString("$mode-default", JSONObject(json).optString("defaultContainer")).apply()
    }

    fun preferred(context: Context, mode: String): String = prefs(context).getString("$mode-default", "").orEmpty()

    private fun other(mode: String) =
        if (mode == LinuxExecutionMode.ANDROID) LinuxExecutionMode.PRIVILEGED else LinuxExecutionMode.ANDROID

    private fun storage(context: Context, mode: String) = File(
        if (mode == LinuxExecutionMode.ANDROID) File(context.noBackupFilesDir, "linux-android")
        else File("/data/local/tmp/dextop/linux"), "containers")

    private fun summary(config: JSONObject) = JSONObject().apply {
        KEPT.forEach { key -> if (config.has(key) && !config.isNull(key)) put(key, config.get(key)) }
    }

    /** Whether [mode]'s last known list held any container, so that it may have left data. */
    fun hadContainers(context: Context, mode: String): Boolean =
        runCatching { JSONArray(prefs(context).getString(mode, "[]")).length() > 0 }.getOrDefault(false)

    /** Remembers the selected mode's list, read from its connected service. */
    fun remember(context: Context, mode: String, json: String) {
        val list = JSONArray(json)
        val kept = JSONArray()
        for (i in 0 until list.length()) list.optJSONObject(i)?.let { kept.put(summary(it)) }
        prefs(context).edit().putString(mode, kept.toString()).apply()
        val ids = (0 until kept.length()).map { kept.getJSONObject(it).getString("id") }.toSet()
        appDirectory(context, mode).listFiles()?.filter { it.extension == "json" && it.nameWithoutExtension !in ids }
            ?.forEach { it.delete() }
    }

    private fun appDirectory(context: Context, mode: String) =
        File(context.noBackupFilesDir, "linux-app-list-$mode")

    /** Only display metadata, never launch commands or credentials. */
    fun rememberApps(context: Context, mode: String, container: String, json: String) {
        if (!container.matches(Regex("c[a-zA-Z0-9]+"))) return
        val entries = JSONArray(json)
        val kept = JSONArray()
        for (i in 0 until entries.length()) entries.optJSONObject(i)?.let { entry ->
            kept.put(JSONObject().apply {
                for (key in listOf("id", "name", "categories", "icon", "iconFile")) {
                    if (entry.has(key) && !entry.isNull(key)) put(key, entry.get(key))
                }
                put("container", container)
            })
        }
        val directory = appDirectory(context, mode).apply { mkdirs() }
        val file = android.util.AtomicFile(File(directory, "$container.json"))
        val stream = file.startWrite()
        try {
            stream.write(kept.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (e: Exception) {
            file.failWrite(stream)
            throw e
        }
    }

    /** Works even when the selected mode's binder and storage cannot be reached. */
    fun offlineOverview(context: Context, mode: String): Map<String, List<JSONObject>> {
        val cached = runCatching { JSONArray(prefs(context).getString(mode, "[]")) }
            .getOrDefault(JSONArray())
        val active = (0 until cached.length()).mapNotNull(cached::optJSONObject)
            .filter { it.optString("id").matches(Regex("c[a-zA-Z0-9]+")) }
            .onEach { it.put("runtimeState", "stopped") }
        val ids = active.map { it.optString("id") }.toSet()
        val apps = active.flatMap { container ->
            runCatching {
                val file = android.util.AtomicFile(File(appDirectory(context, mode), "${container.getString("id")}.json"))
                val array = file.openRead().bufferedReader(Charsets.UTF_8).use { JSONArray(it.readText()) }
                (0 until array.length()).mapNotNull(array::optJSONObject)
            }.getOrDefault(emptyList())
        }
        return mapOf(
            "containers" to active + inactive(context, mode).filter { it.optString("id") !in ids },
            "apps" to apps,
        )
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

    fun clear(context: Context) {
        prefs(context).edit().clear().commit()
        for (mode in listOf(LinuxExecutionMode.ANDROID, LinuxExecutionMode.PRIVILEGED)) {
            appDirectory(context, mode).listFiles()?.forEach { it.delete() }
        }
    }
}
