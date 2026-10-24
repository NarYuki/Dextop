package moe.n4tsu.dextop.linux

import android.os.SystemClock
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** How Linux applications render OpenGL. */
internal enum class LinuxGpuMode(val id: String) {
    /** Turnip (Vulkan on the Adreno GPU through KGSL) with Zink for OpenGL; built by the container setup. */
    TURNIP("turnip"),

    /** VirGL: Mesa's virpipe driver in the container, the device GPU through virgl_server. */
    HARDWARE("virgl"),

    /** llvmpipe: Mesa's CPU renderer. */
    SOFTWARE("software");

    companion object {
        fun of(id: String?) = entries.firstOrNull { it.id == id } ?: TURNIP
    }
}

/** How a container's windows reach Plasma. */
internal enum class LinuxWindowManager(val id: String) {
    /** Xvfb with Dextop's X window manager (per-app windows) and IceWM for the desktop. */
    DEXTOP("dextop"),

    /**
     * DroidDeck's layout: Dextop's Wayland compositor as the host, KDE
     * Plasma's KWin nested in it with Xwayland for X applications, and
     * gamescope windows for GPU applications (droiddeck-desktop, droiddeck-gpu).
     */
    DROIDDECK("droiddeck");

    companion object {
        fun of(id: String?) = entries.firstOrNull { it.id == id } ?: DEXTOP
    }
}

/**
 * Linux settings, kept by the shell-side service next to the containers:
 * global defaults ({"gpu", "defaultContainer"}, "" meaning every container)
 * and per-container overrides ({"gpu"}) in each container's settings.json,
 * apart from config.json, which the installer rewrites.
 */
internal object LinuxSettings {
    private val file get() = File(LinuxPaths.base, "settings.json")
    private fun containerFile(id: String) = File(LinuxPaths.container(id), "settings.json")

    private fun load(file: File) = runCatching { JSONObject(file.readText()) }.getOrDefault(JSONObject())

    private fun save(file: File, json: JSONObject) {
        file.parentFile?.mkdirs()
        val temp = File(file.path + ".tmp")
        temp.writeText(json.toString(2))
        temp.renameTo(file)
    }

    @Synchronized
    fun read(): JSONObject = load(file).also {
        if (!it.has("gpu")) it.put("gpu", LinuxGpuMode.TURNIP.id)
        if (!it.has("defaultContainer")) it.put("defaultContainer", "")
    }

    @Synchronized
    fun write(update: JSONObject): JSONObject {
        val merged = read()
        update.optString("gpu").takeIf { it.isNotEmpty() }?.let { merged.put("gpu", LinuxGpuMode.of(it).id) }
        if (update.has("defaultContainer")) {
            val id = update.optString("defaultContainer")
            merged.put("defaultContainer", id.takeIf { runCatching { LinuxPaths.container(it).isDirectory }.getOrDefault(false) } ?: "")
        }
        save(file, merged)
        return merged
    }

    /** [id]'s own settings; a missing "gpu" follows the global default. */
    @Synchronized
    fun readContainer(id: String): JSONObject = load(containerFile(id))

    /** Merges [update] into [id]'s settings; "gpu": "" returns it to the global default. */
    @Synchronized
    fun writeContainer(id: String, update: JSONObject): JSONObject {
        val merged = readContainer(id)
        if (update.has("gpu")) {
            val gpu = update.optString("gpu")
            if (gpu.isEmpty()) merged.remove("gpu") else merged.put("gpu", LinuxGpuMode.of(gpu).id)
        }
        if (update.has("wm")) {
            val wm = update.optString("wm")
            if (wm.isEmpty()) merged.remove("wm") else merged.put("wm", LinuxWindowManager.of(wm).id)
        }
        save(containerFile(id), merged)
        return merged
    }

    /** [id]'s window manager: its own choice, else DroidDeck's for the DroidDeck runtime. */
    fun windowManager(id: String): LinuxWindowManager =
        readContainer(id).optString("wm").takeIf { it.isNotEmpty() }?.let(LinuxWindowManager::of)
            ?: if (load(LinuxPaths.config(id)).optString("distro") == LinuxCatalog.DROIDDECK_RUNTIME) LinuxWindowManager.DROIDDECK
            else LinuxWindowManager.DEXTOP

    val gpuMode: LinuxGpuMode get() = LinuxGpuMode.of(read().optString("gpu"))

    fun gpuMode(id: String): LinuxGpuMode =
        readContainer(id).optString("gpu").takeIf { it.isNotEmpty() }?.let(LinuxGpuMode::of) ?: gpuMode
}

/**
 * One VirGL renderer per container: virglrenderer's vtest server, run by
 * the shell user with the device's OpenGL ES driver. It listens on the
 * abstract socket "@dextop-<id>/tmp/.virgl_test"; Mesa in the container
 * connects to /tmp/.virgl_test, which the socket shim maps to that name.
 */
internal class LinuxGraphics(private val nativeLibraryDir: String) {
    companion object {
        private const val TAG = "DextopLinuxGpu"
        const val GUEST_SOCKET = "/tmp/.virgl_test"
        private const val START_TIMEOUT_MS = 4_000L

        private fun adrenoModel(): String? =
            runCatching { File("/sys/class/kgsl/kgsl-3d0/gpu_model").readText().trim() }.getOrNull()
    }

    private val servers = ConcurrentHashMap<String, Process>()
    private val server = File(nativeLibraryDir, "libvirgl_server.so")

    val available: Boolean get() = server.canExecute()

    /** The Turnip Dextop builds (Mesa 25.0) drives Adreno 6xx and 7xx; newer GPUs such as the 840 use VirGL. */
    val turnipSupported: Boolean by lazy { LinuxCatalog.turnipSupports(adrenoModel()) }

    private fun socketName(containerId: String) = "${LinuxProot.socketNamespace(containerId)}$GUEST_SOCKET"

    private fun listening(containerId: String): Boolean = runCatching {
        File("/proc/net/unix").readText().contains("@${socketName(containerId)}")
    }.getOrDefault(false)

    fun running(containerId: String): Boolean = servers[containerId]?.isAlive == true && listening(containerId)

    fun turnipInstalled(containerId: String) = turnipSupported &&
        File(LinuxPaths.rootfs(containerId), LinuxCatalog.TURNIP_ICD.removePrefix("/")).isFile

    fun turnipWaylandInstalled(containerId: String) = turnipInstalled(containerId) &&
        File(LinuxPaths.rootfs(containerId), LinuxCatalog.TURNIP_WAYLAND_READY.removePrefix("/")).isFile

    /** Turnip is selected and built for [containerId]. */
    private fun usesTurnip(containerId: String) =
        LinuxSettings.gpuMode(containerId) == LinuxGpuMode.TURNIP && turnipInstalled(containerId)

    /**
     * Starts the renderer of [containerId] when hardware rendering is
     * selected. Returns whether VirGL is in use; on failure applications
     * fall back to llvmpipe.
     */
    @Synchronized
    fun ensure(containerId: String, log: (String) -> Unit): Boolean {
        val mode = LinuxSettings.gpuMode(containerId)
        if (usesTurnip(containerId)) {
            // Applications drive the GPU themselves; no renderer process.
            stop(containerId)
            return true
        }
        // Turnip not built for this container: VirGL is the next best.
        if (mode == LinuxGpuMode.SOFTWARE) {
            stop(containerId)
            return false
        }
        if (running(containerId)) return true
        stop(containerId)
        if (!available) {
            log("VirGL renderer is not packaged; using software rendering")
            return false
        }
        val logFile = File(LinuxPaths.container(containerId), "virgl.log")
        val process = runCatching {
            ProcessBuilder(
                server.absolutePath,
                "--use-egl-surfaceless", "--use-gles",
                "--socket-path", "@${socketName(containerId)}",
            ).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(logFile)).start()
        }.onFailure { Log.w(TAG, "VirGL renderer did not start", it) }.getOrNull() ?: return false
        process.outputStream.close()
        val deadline = SystemClock.uptimeMillis() + START_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline && process.isAlive && !listening(containerId)) SystemClock.sleep(50)
        if (!process.isAlive || !listening(containerId)) {
            LinuxRuntime.terminate(process)
            log("VirGL renderer failed to start; using software rendering\n${logFile.takeIf { it.isFile }?.readText()?.takeLast(2000).orEmpty()}")
            return false
        }
        servers[containerId] = process
        log("VirGL renderer started for $containerId")
        return true
    }

    fun stop(containerId: String) {
        servers.remove(containerId)?.let(LinuxRuntime::terminate)
    }

    fun stopAll() = servers.keys.toList().forEach(::stop)

    /** Environment of container processes: Turnip + Zink, virpipe while the renderer runs, otherwise llvmpipe. */
    fun environment(containerId: String): List<String> = if (usesTurnip(containerId)) listOf(
        "VK_ICD_FILENAMES=${LinuxCatalog.TURNIP_ICD}",
        "GALLIUM_DRIVER=zink",
        // Xvfb has no DRI3; present through CPU-visible images (XPutImage).
        "MESA_VK_WSI_DEBUG=sw",
        "LIBGL_KOPPER_DRI2=true",
    ) else if (running(containerId)) listOf(
        "GALLIUM_DRIVER=virpipe",
        // virgl on a GLES host offers a compatibility profile below what
        // desktop applications ask for; the core features are there.
        "MESA_GL_VERSION_OVERRIDE=4.3COMPAT",
        "MESA_GLSL_VERSION_OVERRIDE=430",
        "MESA_GLES_VERSION_OVERRIDE=3.2",
    ) else listOf("GALLIUM_DRIVER=llvmpipe")

    /**
     * Settings plus the live renderer state, for the Linux settings screens:
     * "containers" maps each container id to its own settings and its
     * effective "gpuEffective".
     */
    fun status(): JSONObject = LinuxSettings.read()
        .put("containers", JSONObject().apply {
            LinuxPaths.containers.listFiles().orEmpty().filter { it.isDirectory }.forEach { dir ->
                runCatching {
                    put(dir.name, LinuxSettings.readContainer(dir.name).put("gpuEffective", LinuxSettings.gpuMode(dir.name).id)
                        .put("wmEffective", LinuxSettings.windowManager(dir.name).id))
                }
            }
        })
        .put("hardwareAvailable", available)
        .put("rendererRunning", org.json.JSONArray(servers.keys.filter(::running)))
        .put("turnipInstalled", org.json.JSONArray(
            LinuxPaths.containers.listFiles().orEmpty().map { it.name }.filter { runCatching { turnipInstalled(it) }.getOrDefault(false) }))
}
