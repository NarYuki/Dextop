package moe.n4tsu.dextop.linux

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files

/**
 * Reads the container's freedesktop.org desktop entries
 * (/usr/share/applications) straight from the rootfs, for Kickoff.
 */
internal object LinuxDesktopEntries {
    private const val MAX_ICON_BYTES = 256 * 1024
    private val ICON_SIZES = listOf("48x48", "64x64", "96x96", "128x128", "32x32", "256x256", "512x512")
    private val FIELD_CODE = Regex("%[fFuUdDnNickvm]")

    data class Entry(val id: String, val name: String, val exec: String, val icon: String, val terminal: Boolean, val categories: String, val startupWMClass: String = "")

    fun list(rootfs: File, locale: String): List<Entry> {
        val files = linkedMapOf<String, File>()
        // XDG user entries override distribution entries, including Hidden=true.
        for (path in listOf("/usr/share/applications", "/usr/local/share/applications", "/root/.local/share/applications")) {
            resolve(rootfs, path)?.listFiles { file -> file.name.endsWith(".desktop") }.orEmpty()
                .forEach { files[it.name] = it }
        }
        return files.values.mapNotNull { file -> parse(rootfs, file, locale) }
            .sortedBy { it.name.lowercase() }
    }

    fun find(rootfs: File, id: String): Entry? {
        require('/' !in id && '\\' !in id) { "Invalid desktop entry id" }
        for (path in listOf("/root/.local/share/applications", "/usr/local/share/applications", "/usr/share/applications")) {
            resolve(rootfs, "$path/$id")?.let { return parse(rootfs, it, "") }
        }
        return null
    }

    fun toJson(rootfs: File, entries: List<Entry>): String = JSONArray().apply {
        entries.forEach { entry ->
            put(JSONObject()
                .put("id", entry.id)
                .put("name", entry.name)
                .put("categories", entry.categories)
                .put("startupWMClass", entry.startupWMClass)
                .put("icon", iconPng(rootfs, entry.icon)?.let { Base64.encodeToString(it, Base64.NO_WRAP) }.orEmpty()))
        }
    }.toString()

    /** The shell command for [entry]: field codes removed, terminal apps wrapped in xterm. */
    fun command(entry: Entry): String {
        val exec = FIELD_CODE.replace(entry.exec, "").replace("%%", "%").trim()
        return if (entry.terminal) "xterm -e $exec" else exec
    }

    private fun parse(rootfs: File, file: File, locale: String): Entry? {
        val values = HashMap<String, String>()
        var inEntry = false
        runCatching { (resolveFile(rootfs, file) ?: return null).readLines() }.getOrNull()?.forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("[") -> inEntry = line == "[Desktop Entry]"
                inEntry && '=' in line -> values.putIfAbsent(line.substringBefore('=').trim(), line.substringAfter('=').trim())
            }
        } ?: return null
        if (values["Type"] != "Application") return null
        if (values["NoDisplay"] == "true" || values["Hidden"] == "true") return null
        if (values["OnlyShowIn"] != null) return null
        val exec = values["Exec"]?.takeIf { it.isNotBlank() } ?: return null
        val language = locale.substringBefore('-').substringBefore('_')
        val name = values["Name[${locale.replace('-', '_')}]"] ?: values["Name[$language]"] ?: values["Name"] ?: return null
        return Entry(file.name, name, exec, values["Icon"].orEmpty(), values["Terminal"] == "true", values["Categories"].orEmpty(), values["StartupWMClass"].orEmpty())
    }

    private fun iconPng(rootfs: File, icon: String): ByteArray? {
        if (icon.isBlank()) return null
        for (path in iconCandidates(rootfs, icon)) {
            val file = resolve(rootfs, path) ?: continue
            if (!file.isFile || file.length() !in 1..MAX_ICON_BYTES) continue
            val png = runCatching { LinuxIconDecoder.png(file) }.getOrNull()
            if (png != null) return png
        }
        for (file in sizedPixmaps(rootfs, icon)) {
            if (file.length() !in 1..MAX_ICON_BYTES) continue
            val png = runCatching { LinuxIconDecoder.png(file) }.getOrNull()
            if (png != null) return png
        }
        return null
    }

    /**
     * Pixmaps named after the icon with a size suffix, largest first: Debian's
     * XTerm entry says Icon=mini.xterm and ships mini.xterm_48x48.xpm.
     */
    internal fun sizedPixmaps(rootfs: File, icon: String): List<File> {
        if (icon.isBlank() || icon.startsWith("/")) return emptyList()
        val pixmaps = resolve(rootfs, "/usr/share/pixmaps") ?: return emptyList()
        val pattern = Regex("^" + Regex.escape(icon) + "[_-](\\d+)(?:x\\d+)?\\.(png|svg|xpm)$", RegexOption.IGNORE_CASE)
        return pixmaps.listFiles().orEmpty()
            .mapNotNull { file -> pattern.matchEntire(file.name)?.let { file to it.groupValues[1].toInt() } }
            // Prefer about 48 px: large enough for Kickoff, small enough to decode quickly.
            .sortedWith(compareBy({ if (it.second >= 48) 0 else 1 }, { if (it.second >= 48) it.second else -it.second }))
            .mapNotNull { (file, _) -> resolveFile(rootfs, file)?.takeIf { it.isFile } }
    }

    internal fun iconCandidates(rootfs: File, icon: String): Sequence<String> =
        if (icon.startsWith("/")) sequenceOf(icon) else sequence {
            val names = if (icon.substringAfterLast('.') in listOf("png", "svg", "xpm")) listOf(icon)
                else listOf("$icon.png", "$icon.svg", "$icon.xpm")
            val themes = linkedSetOf("hicolor", "Adwaita", "gnome")
            resolve(rootfs, "/usr/share/icons")?.listFiles()?.filter { it.isDirectory }?.forEach { themes.add(it.name) }
            for (theme in themes) {
                val base = "/usr/share/icons/$theme"
                val directories = linkedSetOf<String>()
                ICON_SIZES.forEach { directories.add("$it/apps") }
                directories.add("scalable/apps")
                // Themes may use directories such as 48/apps, apps/48 or symbolic/apps.
                resolve(rootfs, "$base/index.theme")?.takeIf { it.isFile }?.readLines()?.forEach { line ->
                    if (line.startsWith("Directories=") || line.startsWith("ScaledDirectories=")) {
                        line.substringAfter('=').split(',').map(String::trim).filter { it.isNotEmpty() }.forEach(directories::add)
                    }
                }
                for (directory in directories) for (name in names) yield("$base/$directory/$name")
            }
            for (name in names) yield("/usr/share/pixmaps/$name")
        }

    /** [path] inside the container, following symbolic links (absolute targets point into the rootfs). */
    private fun resolve(rootfs: File, path: String): File? = resolveFile(rootfs, File(rootfs, path.removePrefix("/")))

    private fun resolveFile(rootfs: File, start: File): File? {
        var file = start
        repeat(16) {
            val nio = file.toPath()
            if (!Files.isSymbolicLink(nio)) return file.takeIf { it.exists() }
            val target = Files.readSymbolicLink(nio).toString()
            file = if (target.startsWith("/")) {
                // proot's --link2symlink stores hard links as links to host paths under the rootfs.
                if (target.startsWith(rootfs.path)) File(target) else File(rootfs, target.removePrefix("/"))
            } else {
                File(file.parentFile, target)
            }
        }
        return null
    }
}
