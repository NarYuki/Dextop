package moe.n4tsu.dextop.linux

import android.system.Os
import android.system.OsConstants
import java.io.File

/** Shell-owned runtime diagnostics and recovery; never removes container data. */
internal object LinuxRuntime {
    fun logs(id: String): String = buildString {
        val files = listOf(File(LinuxPaths.container(id), "runtime.log")) +
            File(LinuxPaths.rootfs(id), "tmp").listFiles().orEmpty()
                .filter { it.isFile && it.extension == "log" }.sortedByDescending { it.lastModified() } + listOf(LinuxPaths.log(id))
        for (file in files) {
            val tail = runCatching {
                java.io.RandomAccessFile(file, "r").use { input ->
                    val size = minOf(input.length(), 12_000L).toInt()
                    input.seek(input.length() - size)
                    readable(ByteArray(size).also { input.readFully(it) }.toString(Charsets.UTF_8))
                }
            }.getOrDefault("")
            if (tail.isNotBlank()) appendLine("=== ${file.name} ===\n$tail")
            if (length > 100_000) break
        }
    }

    internal fun readable(text: String): String = text.filter { it == '\n' || it == '\t' || !it.isISOControl() }

    private data class Entry(val pid: Int, val parent: Int, val command: List<String>)
    fun forceStop(id: String): Int {
        val root = LinuxPaths.rootfs(id).absolutePath
        val entries = File("/proc").listFiles().orEmpty().mapNotNull { dir ->
            val pid = dir.name.toIntOrNull() ?: return@mapNotNull null
            runCatching {
                val status = File(dir, "status").readText()
                val uid = Regex("(?m)^Uid:\\s+(\\d+)").find(status)?.groupValues?.get(1)?.toInt()
                if (uid != Os.getuid()) return@mapNotNull null
                val parent = Regex("(?m)^PPid:\\s+(\\d+)").find(status)!!.groupValues[1].toInt()
                Entry(pid, parent, File(dir, "cmdline").readText().split('\u0000').filter { it.isNotEmpty() })
            }.getOrNull()
        }
        val roots = entries.filter { it.command.firstOrNull()?.substringAfterLast('/') == "libproot.so" && root in it.command }
        val targets = roots.map { it.pid }.toMutableSet()
        val displays = roots.flatMap { entry ->
            Regex("(?:D=|DISPLAY=:)(\\d+)").findAll(entry.command.joinToString(" ")).map { it.groupValues[1] }.toList()
        }.toSet()
        entries.filter { entry -> entry.command.firstOrNull()?.substringAfterLast('/') == "dbus-daemon" &&
            displays.any { "--address=unix:abstract=/tmp/dextop-dbus-$it" in entry.command } }.forEach { targets += it.pid }
        do {
            val added = entries.filter { it.parent in targets && it.pid !in targets }.map { it.pid }
            targets.addAll(added)
        } while (added.isNotEmpty())
        targets.forEach { runCatching { Os.kill(it, OsConstants.SIGKILL) } }
        return targets.size
    }

    fun freeDisplay(used: Set<Int>, sockets: String = File("/proc/net/unix").readText()): Int {
        return (10..63).firstOrNull { display -> display !in used &&
            !Regex("(?m)(?:@)?/tmp/\\.X11-unix/X$display$").containsMatchIn(sockets) }
            ?: error("No free X display (10–63); stop stale Linux sessions")
    }
}
