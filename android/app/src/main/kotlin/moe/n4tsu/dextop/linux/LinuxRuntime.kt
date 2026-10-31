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

    internal data class Entry(val pid: Int, val parent: Int, val command: List<String>, val start: String = "", val tracer: Int = 0)

    private fun snapshot(): List<Entry> = File("/proc").listFiles().orEmpty().mapNotNull { dir ->
        val pid = dir.name.toIntOrNull() ?: return@mapNotNull null
        runCatching {
            val status = File(dir, "status").readText()
            if (Regex("(?m)^Uid:\\s+(\\d+)").find(status)?.groupValues?.get(1)?.toInt() != Os.getuid()) return@mapNotNull null
            val parent = Regex("(?m)^PPid:\\s+(\\d+)").find(status)!!.groupValues[1].toInt()
            Entry(pid, parent, File(dir, "cmdline").readText().split('\u0000').filter { it.isNotEmpty() },
                File(dir, "stat").readText().substringAfterLast(") ").split(' ')[19],
                Regex("(?m)^TracerPid:\\s+(\\d+)").find(status)?.groupValues?.get(1)?.toInt() ?: 0)
        }.getOrNull()
    }

    /** Every process of every container (PRoot and what runs under it), with its container id. */
    internal fun linuxProcesses(): List<Pair<Entry, String>> {
        val entries = snapshot()
        return entries.mapNotNull { entry -> rootOf(entry)?.let { entry to it } }.flatMap { (root, path) ->
            val container = path.removeSuffix("/rootfs").substringAfterLast('/')
            descendants(entries, setOf(root.pid)).map { it to container }
        }.distinctBy { it.first.pid }
    }

    /** Kills [entry] and everything it started, as of now. */
    internal fun stopTree(entry: Entry): Int = kill(descendants(snapshot(), setOf(entry.pid)))

    internal fun rootOf(entry: Entry): String? {
        if (entry.command.firstOrNull()?.substringAfterLast('/') != "libproot.so") return null
        val index = entry.command.indexOf("-r")
        val root = entry.command.getOrNull(index + 1).takeIf { index >= 0 } ?: return null
        val base = LinuxPaths.containers.absolutePath + "/"
        return root.takeIf { it.startsWith(base) && it.removePrefix(base).matches(Regex("c[a-zA-Z0-9]+/rootfs")) }
    }

    internal fun descendants(entries: List<Entry>, roots: Set<Int>): List<Entry> {
        val selected = roots.toMutableSet()
        val ordered = entries.filter { it.pid in roots }.toMutableList()
        do {
            val added = entries.filter { (it.parent in selected || it.tracer in selected) && it.pid !in selected }
            ordered.addAll(added); selected.addAll(added.map { it.pid })
        } while (added.isNotEmpty())
        return ordered.asReversed() // terminate tracees before their tracer
    }

    private fun kill(entries: List<Entry>): Int = entries.count { entry ->
        runCatching {
            // A snapshot must never send a signal to a subsequently reused PID.
            val now = File("/proc/${entry.pid}/stat").readText().substringAfterLast(") ").split(' ')[19]
            if (now != entry.start) false else { Os.kill(entry.pid, OsConstants.SIGKILL); true }
        }.getOrDefault(false)
    }

    private fun stopMatching(root: String?): Int {
        val entries = snapshot()
        val roots = entries.filter { rootOf(it)?.let { path -> root == null || path == root } == true }
        val namespaces = roots.mapNotNull { rootOf(it)?.removeSuffix("/rootfs")?.substringAfterLast('/') }
            .map { LinuxProot.socketNamespace(it) }.toSet()
        // Forked session buses may already have been reparented to init. Match the
        // container namespace, rather than a display number shared with another container.
        val buses = entries.filter { it.command.firstOrNull()?.substringAfterLast('/') == "dbus-daemon" &&
            runCatching { File("/proc/${it.pid}/environ").readText().split('\u0000')
                .any { value -> value.removePrefix("DEXTOP_SOCKET_NS=") in namespaces && value.startsWith("DEXTOP_SOCKET_NS=") } }.getOrDefault(false) }
        return kill(descendants(entries, (roots + buses).map { it.pid }.toSet()))
    }

    fun forceStop(id: String): Int = stopMatching(LinuxPaths.rootfs(id).absolutePath)

    /** Includes deleted containers, which no longer appear in containerIds(). */
    fun forceStopAll(): Int = stopMatching(null)

    fun terminate(process: Process) {
        if (!process.isAlive) return
        val entries = snapshot()
        // Android's Process API does not expose pid(); its process description does.
        val pid = Regex("pid=(\\d+)").find(process.toString())?.groupValues?.get(1)?.toIntOrNull()
        if (pid != null && process.isAlive) kill(descendants(entries, setOf(pid)))
        else android.util.Log.w("DextopLinux", "Cannot identify process tree: $process")
        if (process.isAlive) process.destroyForcibly()
    }

    fun freeDisplay(used: Set<Int>, sockets: String = runCatching { File("/proc/net/unix").readText() }.getOrDefault("")): Int {
        return (10..63).firstOrNull { display -> display !in used &&
            !Regex("(?m)(?:@)?/tmp/\\.X11-unix/X$display$").containsMatchIn(sockets) }
            ?: error("No free X display (10–63); stop stale Linux sessions")
    }
}
