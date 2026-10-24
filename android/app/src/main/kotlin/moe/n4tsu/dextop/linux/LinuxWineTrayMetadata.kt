package moe.n4tsu.dextop.linux

import android.util.Log
import java.io.File
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap

/** One Wine UI metadata agent per prefix, shared by every Windows application. */
internal class LinuxWineTrayMetadata {
    private val agents = ConcurrentHashMap<String, Process>()
    @Synchronized fun ensure(id: String, pid: Int, proot: LinuxProot) {
        if (pid <= 0) return
        val root = LinuxPaths.rootfs(id).canonicalFile
        val environmentFile = File("/proc/$pid/environ")
        if (!environmentFile.isFile || environmentFile.length() > 256 * 1024) return
        val environment = environmentFile.readText().split('\u0000').mapNotNull { entry ->
            val split = entry.indexOf('='); if (split > 0) entry.substring(0, split) to entry.substring(split + 1) else null
        }.toMap()
        val prefix = environment["WINEPREFIX"] ?: "/root/.wine"
        fun guestFile(path: String): File? {
            if (!path.startsWith('/')) return null
            val file = File(root, path.removePrefix("/")).canonicalFile
            return file.takeIf { it.path.startsWith(root.path + File.separator) }
        }
        if (guestFile(prefix)?.isDirectory != true) {
            if ("WINEPREFIX" in environment) Log.w("DextopWineTray", "Wine prefix is unavailable for pid $pid")
            return
        }
        val key = "$id:$prefix"
        if (agents[key]?.isAlive == true) return
        val executable = environment["WINELOADER"]?.takeIf { guestFile(it)?.isFile == true }
            ?: environment["WINESERVER"]?.let { server ->
                if (guestFile(server)?.isFile != true) return@let null
                listOf("wine", "wine64").map { File(File(server).parentFile, it).path }
                    .firstOrNull { guestFile(it)?.isFile == true }
            } ?: run {
            // Under proot, /proc/PID/exe may name libproot_loader.so rather
            // than the guest executable. Find Wine's mapped ELF in that case.
            val host = Files.readSymbolicLink(File("/proc/$pid/exe").toPath()).toString()
            val mapped = File("/proc/$pid/maps").readLines().mapNotNull { line ->
                line.trim().split(Regex("\\s+"), limit = 6).getOrNull(5)?.removeSuffix(" (deleted)")
            }
            (listOf(host) + mapped).asSequence().map { it.removeSuffix("-preloader") }
                .filter { it.startsWith(root.path + "/") }.map { it.removePrefix(root.path) }
                .firstOrNull { File(it).name in setOf("wine", "wine64", "wine32") && guestFile(it)?.isFile == true }
 ?: run {
                    Log.w("DextopWineTray", "Wine loader is unavailable for pid $pid")
                    return
                }
        }
        fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"
        val allow = setOf("LD_PRELOAD", "DEXTOP_SOCKET_NS", "WINESERVER", "WINEDLLPATH", "LD_LIBRARY_PATH", "WINEESYNC", "WINEFSYNC", "PROTON_NO_ESYNC", "PROTON_NO_FSYNC", "DISPLAY", "XDG_RUNTIME_DIR", "DBUS_SESSION_BUS_ADDRESS")
        val script = buildString {
            environment.filterKeys { it in allow || it.startsWith("PROTON_") || it.startsWith("BL_") || it.startsWith("DEXTOP_") || it.startsWith("FEX_") }.forEach { (name, value) -> appendLine("export $name=${quote(value)}") }
            appendLine("export WINEPREFIX=${quote(prefix)} WINEDEBUG=-all")
            appendLine("exec ${quote(executable)} /usr/local/lib/dextop/dextop-wine-tray.exe")
        }
        Log.i("DextopWineTray", "Starting metadata agent for pid $pid")
        val agent = proot.guest(id, script).redirectErrorStream(true).start()
        agent.outputStream.close(); agents[key] = agent
        Thread({
            runCatching {
                val tail = StringBuilder()
                agent.inputStream.bufferedReader().forEachLine { line ->
                    tail.appendLine(line); if (tail.length > 4096) tail.delete(0, tail.length - 4096)
                }
                val exit = agent.waitFor()
                if (exit != 0) Log.w("DextopWineTray", "Metadata agent exited $exit: $tail")
            }.onFailure { Log.w("DextopWineTray", "Metadata agent failed", it) }
            agents.remove(key, agent)
        }, "DextopWineTrayMetadata").apply { isDaemon = true; start() }
    }
    fun stop(id: String) { agents.keys.filter { it.startsWith("$id:") }.forEach { agents.remove(it)?.let(LinuxRuntime::terminate) } }
    fun stopAll() { agents.values.forEach { LinuxRuntime.terminate(it) }; agents.clear() }
}
