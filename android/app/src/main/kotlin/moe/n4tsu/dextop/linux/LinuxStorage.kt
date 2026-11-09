package moe.n4tsu.dextop.linux

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/**
 * Where a container's storage goes, and the caches that can be removed without losing anything
 * the user installed or made: package downloads, ~/.cache, temporary files and Steam's web and
 * download caches.
 */
internal object LinuxStorage {
    /** A part of the container, by its guest path; [cache] parts are what [clean] removes. */
    data class Part(val id: String, val path: String, val cache: Boolean = false)

    /** The parts, most specific first (a file counts in the first part that holds it). */
    fun parts(home: String): List<Part> = listOf(
        Part("apt", "/var/cache/apt/archives", cache = true),
        Part("pacman", "/var/cache/pacman/pkg", cache = true),
        Part("dnf", "/var/cache/dnf", cache = true),
        Part("steamCache", "$home/.local/share/Steam/appcache/httpcache", cache = true),
        Part("steamDownloads", "$home/.local/share/Steam/steamapps/downloading", cache = true),
        Part("steamShaders", "$home/.local/share/Steam/steamapps/shadercache", cache = true),
        Part("userCache", "$home/.cache", cache = true),
        Part("tmp", "/tmp", cache = true),
        Part("varTmp", "/var/tmp", cache = true),
        Part("steamGames", "$home/.local/share/Steam/steamapps/common"),
        Part("steam", "$home/.local/share/Steam"),
        Part("flatpak", "$home/.local/share/flatpak"),
        Part("flatpakSystem", "/var/lib/flatpak"),
        Part("appImages", "$home/Applications"),
        Part("home", home),
        Part("gaming", "/usr/local/lib/dextop-gaming"),
        Part("opt", "/opt"),
        Part("fonts", "/usr/share/fonts"),
        Part("usr", "/usr"),
        Part("var", "/var"),
        Part("other", "/"),
    )

    /** {total, parts: [{id, path, bytes, cache}], cacheBytes} for [root], [home] being the account's. */
    fun usage(root: File, home: String, onBytes: (Long) -> Unit = {}): JSONObject {
        val parts = parts(home)
        val base = root.toPath().toAbsolutePath()
        val prefixes = parts.map { part -> part to base.resolve(part.path.removePrefix("/")).normalize() }
        val totals = LongArray(parts.size)
        var total = 0L
        Files.walkFileTree(base, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult =
                // /dev, /proc and /sys are Android's own, bound at session start.
                if (dir != base && base.relativize(dir).toString() in setOf("dev", "proc", "sys")) FileVisitResult.SKIP_SUBTREE
                else FileVisitResult.CONTINUE
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (!attrs.isRegularFile) return FileVisitResult.CONTINUE
                val size = attrs.size()
                val index = prefixes.indexOfFirst { (_, prefix) -> file.startsWith(prefix) }.takeIf { it >= 0 } ?: parts.lastIndex
                totals[index] += size
                total += size
                onBytes(total)
                return FileVisitResult.CONTINUE
            }
            override fun visitFileFailed(file: Path, exc: java.io.IOException): FileVisitResult = FileVisitResult.CONTINUE
        })
        return JSONObject().put("total", total)
            .put("cacheBytes", parts.indices.filter { parts[it].cache }.sumOf { totals[it] })
            .put("parts", JSONArray().apply {
                parts.indices.filter { totals[it] > 0 }.sortedByDescending { totals[it] }.forEach { i ->
                    put(JSONObject().put("id", parts[i].id).put("path", parts[i].path).put("bytes", totals[i]).put("cache", parts[i].cache))
                }
            })
    }

    /** Uses the same category precedence and logical byte sizes as usage(). Never follows links. */
    fun breakdown(root: File, home: String, category: String, path: String, offset: Int = 0): JSONObject {
        val parts = parts(home)
        val index = parts.indexOfFirst { it.id == category }
        require(index >= 0 && offset >= 0 && offset <= 1_000_000)
        val base = root.toPath().toAbsolutePath().normalize()
        val prefixes = parts.map { base.resolve(it.path.removePrefix("/")).normalize() }
        require(path.startsWith('/') && path.length <= 4096 && '\u0000' !in path)
        require(path.split('/').none { it == ".." || it == "." }) { moe.n4tsu.dextop.localText("uiInvalidStoragePath") }
        val start = base.resolve(path.removePrefix("/")).normalize()
        require(start.startsWith(prefixes[index]) && start.startsWith(base)) { moe.n4tsu.dextop.localText("uiPathIsOutsideThisCategory") }
        var ancestor = base
        for (component in base.relativize(start)) {
            ancestor = ancestor.resolve(component)
            require(!Files.isSymbolicLink(ancestor)) { moe.n4tsu.dextop.localText("uiSymbolicLinksCannotBeBrowsed") }
        }
        require(Files.isDirectory(start, java.nio.file.LinkOption.NOFOLLOW_LINKS)) { moe.n4tsu.dextop.localText("uiStorageDirectoryIsUnavailable") }
        val sizes = HashMap<Path, Long>()
        Files.walkFileTree(start, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (base.relativize(dir).toString() in setOf("dev", "proc", "sys")) return FileVisitResult.SKIP_SUBTREE
                val owner = prefixes.indexOfFirst { dir.startsWith(it) }
                // A parent can contain subcategories; descend only when this category may own files.
                return if (owner < index) FileVisitResult.SKIP_SUBTREE else FileVisitResult.CONTINUE
            }
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (attrs.isRegularFile && prefixes.indexOfFirst { file.startsWith(it) } == index) {
                    val child = start.resolve(start.relativize(file).getName(0))
                    sizes[child] = (sizes[child] ?: 0L) + attrs.size()
                }
                return FileVisitResult.CONTINUE
            }
            override fun visitFileFailed(file: Path, exc: java.io.IOException) = FileVisitResult.CONTINUE
        })
        val sorted = sizes.entries.sortedWith(compareByDescending<Map.Entry<Path, Long>> { it.value }.thenBy { it.key.fileName.toString() })
        val page = sorted.drop(offset).take(200)
        return JSONObject().put("path", path).put("total", sizes.values.sum()).put("count", sorted.size)
            .put("nextOffset", offset + page.size).put("entries", JSONArray().apply {
                page.forEach { (child, bytes) ->
                    put(JSONObject().put("name", child.fileName.toString())
                        .put("path", "/" + base.relativize(child).joinToString("/"))
                        .put("directory", Files.isDirectory(child, java.nio.file.LinkOption.NOFOLLOW_LINKS)).put("bytes", bytes))
                }
            })
    }

    /** Package managers' own cleaning, run as root in the container. */
    const val PACKAGE_CACHE_SCRIPT = """
        if command -v apt-get >/dev/null 2>&1; then apt-get clean; fi
        if command -v pacman >/dev/null 2>&1; then pacman -Scc --noconfirm >/dev/null; fi
        if command -v dnf >/dev/null 2>&1; then dnf clean packages >/dev/null; fi
        exit 0
    """

    /**
     * Removes the cache parts' contents in [root] (the directories stay). Package caches are
     * cleaned by their package managers first ([PACKAGE_CACHE_SCRIPT]); this removes what is
     * left. Returns the bytes removed.
     */
    fun clean(root: File, home: String): Long {
        var removed = 0L
        parts(home).filter { it.cache }.forEach { part ->
            val dir = File(root, part.path.removePrefix("/"))
            if (!Files.isDirectory(dir.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)) return@forEach
            dir.listFiles().orEmpty().forEach { child ->
                // apt's lock and partial directory stay; X sockets and locks in /tmp belong to no session now.
                if (part.id == "apt" && child.name in setOf("lock", "partial")) return@forEach
                removed += size(child.toPath())
                LinuxRootfsArchive.deleteTree(child)
            }
        }
        return removed
    }

    private fun size(path: Path): Long {
        var bytes = 0L
        runCatching {
            Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (attrs.isRegularFile) bytes += attrs.size()
                    return FileVisitResult.CONTINUE
                }
                override fun visitFileFailed(file: Path, exc: java.io.IOException) = FileVisitResult.CONTINUE
            })
        }
        return bytes
    }

    /** apt removes each package it downloaded once it is installed (Debian keeps them for apt-get). */
    const val APT_NO_CACHE = "etc/apt/apt.conf.d/99dextop-no-package-cache"
    const val APT_NO_CACHE_TEXT = "// Dextop: downloaded packages are removed once installed (they are kept by default for apt-get).\n" +
        "APT::Keep-Downloaded-Packages \"false\";\n"
}
