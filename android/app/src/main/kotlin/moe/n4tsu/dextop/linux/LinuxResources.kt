package moe.n4tsu.dextop.linux

import android.system.Os
import android.system.OsConstants
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.ConcurrentHashMap

/**
 * Linux settings → Resources, on the service side (it runs as Linux's own user, so it sees
 * Linux's processes): Linux processes with their CPU and memory use, the GPU's load,
 * containers' disk use and the GPU drivers installed in them.
 */
internal object LinuxResources {
    private val ticksPerSecond = runCatching { Os.sysconf(OsConstants._SC_CLK_TCK) }.getOrDefault(100L).coerceAtLeast(1)
    private val pageSize = runCatching { Os.sysconf(OsConstants._SC_PAGESIZE) }.getOrDefault(4096L)
    /** CPU ticks of each process at the previous call, for its use since then. */
    private val previous = ConcurrentHashMap<Int, Pair<Long, Long>>()
    private val sizes = ConcurrentHashMap<String, Pair<Long, Long>>()
    private val measuring = ConcurrentHashMap.newKeySet<String>()
    /** The virglrenderer Dextop ships (third_party/virglrenderer). */
    private const val VIRGL_VERSION = "virglrenderer 1.7.0"

    /** An installed package's version from the container's dpkg database; empty when absent. */
    private fun packageVersion(root: File, name: String): String = runCatching {
        File(root, "var/lib/dpkg/status").bufferedReader().use { reader ->
            var current = ""; var version = ""; var installed = false
            reader.lineSequence().forEach { line ->
                when {
                    line.isEmpty() -> { if (current == name && installed) return@use version; current = ""; version = ""; installed = false }
                    line.startsWith("Package: ") -> current = line.substring(9).trim()
                    line.startsWith("Version: ") -> version = line.substring(9).trim()
                    line.startsWith("Status: ") -> installed = line.trim() == "Status: install ok installed"
                }
            }
            if (current == name && installed) version else ""
        }
    }.getOrDefault("")

    private fun stat(pid: Int): List<String>? = runCatching {
        File("/proc/$pid/stat").readText().substringAfterLast(") ").split(' ')
    }.getOrNull()

    fun status(graphics: LinuxGraphics?, containers: List<JSONObject>): JSONObject {
        val now = android.os.SystemClock.elapsedRealtime()
        val processes = JSONArray()
        var cpuTotal = 0.0
        var memoryTotal = 0L
        val seen = HashSet<Int>()
        for ((entry, container) in LinuxRuntime.linuxProcesses()) {
            val fields = stat(entry.pid) ?: continue
            val ticks = (fields.getOrNull(11)?.toLongOrNull() ?: 0) + (fields.getOrNull(12)?.toLongOrNull() ?: 0)
            val rss = (fields.getOrNull(21)?.toLongOrNull() ?: 0) * pageSize
            val last = previous[entry.pid]
            val cpu = if (last != null && now > last.second)
                (ticks - last.first).coerceAtLeast(0).toDouble() / ticksPerSecond / ((now - last.second) / 1000.0) * 100 else 0.0
            previous[entry.pid] = ticks to now
            seen += entry.pid
            cpuTotal += cpu
            memoryTotal += rss
            val command = entry.command
            // PRoot itself is Linux's plumbing; the guest command is what the user knows.
            val name = command.firstOrNull()?.substringAfterLast('/').orEmpty().ifEmpty { "?" }
            processes.put(JSONObject()
                .put("pid", entry.pid)
                .put("start", entry.start)
                .put("container", container)
                .put("name", name)
                .put("command", command.joinToString(" ").take(300))
                .put("cpu", cpu)
                .put("memory", rss)
                .put("plumbing", name == "libproot.so"))
        }
        previous.keys.retainAll(seen)
        val gpuBusy = runCatching {
            File("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage").readText().trim().removeSuffix("%").trim().toInt()
        }.getOrDefault(-1)
        val containerInfo = JSONArray()
        for (config in containers) {
            val id = config.optString("id")
            val root = runCatching { LinuxPaths.rootfs(id) }.getOrNull() ?: continue
            containerInfo.put(JSONObject()
                .put("id", id)
                .put("size", size(id))
                .put("turnipInstalled", graphics?.turnipInstalled(id) == true)
                .put("turnipWayland", graphics?.turnipWaylandInstalled(id) == true)
                .put("mesaDirect", LinuxGpuRuntime.installed(root))
                .put("rendererRunning", graphics?.running(id) == true)
                .put("gpuMode", LinuxSettings.gpuMode(id).id)
                .put("mesa", packageVersion(root, "libgl1-mesa-dri"))
                .put("windowManager", LinuxSettings.windowManager(id).id))
        }
        val data = LinuxPaths.base
        return JSONObject()
            .put("processes", processes)
            .put("cpu", cpuTotal)
            .put("memory", memoryTotal)
            .put("gpuBusy", gpuBusy)
            .put("gpuModel", runCatching { File("/sys/class/kgsl/kgsl-3d0/gpu_model").readText().trim() }.getOrDefault(""))
            .put("turnipSupported", graphics?.turnipSupported == true)
            .put("turnipPackage", LinuxCatalog.GPU_PACKAGE.name)
            .put("virglAvailable", graphics?.available == true)
            .put("virglVersion", VIRGL_VERSION)
            .put("containers", containerInfo)
            .put("storageFree", runCatching { android.os.StatFs(data.absolutePath).availableBytes }.getOrDefault(-1L))
            .put("storageTotal", runCatching { android.os.StatFs(data.absolutePath).totalBytes }.getOrDefault(-1L))
    }

    /** Bytes used by [id], measured in the background and kept for a minute; -1 until known. */
    private fun size(id: String): Long {
        val known = sizes[id]
        val now = android.os.SystemClock.elapsedRealtime()
        if ((known == null || now - known.second > 60_000) && measuring.add(id)) {
            Thread({
                var total = 0L
                runCatching {
                    Files.walkFileTree(LinuxPaths.container(id).toPath(), object : SimpleFileVisitor<Path>() {
                        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                            total += attrs.size(); return FileVisitResult.CONTINUE
                        }
                        override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = FileVisitResult.CONTINUE
                    })
                }
                sizes[id] = total to android.os.SystemClock.elapsedRealtime()
                measuring.remove(id)
            }, "DextopLinuxSize").apply { isDaemon = true; priority = Thread.MIN_PRIORITY; start() }
        }
        return known?.first ?: -1
    }

    /** Ends one Linux process (and what it started); only Linux's own, as the snapshot saw it. */
    fun stop(pid: Int, start: String): Boolean {
        val (entry, _) = LinuxRuntime.linuxProcesses().firstOrNull { it.first.pid == pid && it.first.start == start } ?: return false
        return LinuxRuntime.stopTree(entry) > 0
    }
}
