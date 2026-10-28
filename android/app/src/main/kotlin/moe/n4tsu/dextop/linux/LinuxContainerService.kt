package moe.n4tsu.dextop.linux

import android.os.RemoteException
import android.system.Os
import android.util.Log
import androidx.annotation.Keep
import org.json.JSONArray
import org.json.JSONObject
import org.tukaani.xz.XZInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.system.exitProcess

/**
 * Shizuku UserService that owns the Linux containers.
 *
 * Shizuku loads this class into a shell app_process, so it runs as the shell
 * user and may execute files under /data/local/tmp and the APK's
 * nativeLibraryDir; the app process (targetSdk 36) may not. No Android
 * Context is available here.
 */
@Keep
class LinuxContainerService : ILinuxContainerService.Stub() {
    companion object {
        private const val TAG = "DextopLinuxService"
        // Includes guest state files (AIDL transaction 47).
        const val PROTOCOL_VERSION = 23
        /** An application ending this soon after its start with SIGSEGV/SIGABRT is retried in software. */
        private const val GPU_CRASH_WINDOW_MS = 20_000L
        private val GUEST_LOG = Regex("""[A-Za-z0-9._-]+\.log""")
        private val GUEST_STATE = Regex("""dextop-[a-z0-9-]+\.conf""")
        private const val BUFFER = 256 * 1024
    }

    @Volatile private var proot: LinuxProot? = null
    @Volatile private var audioClientUid = -1
    override fun openAudioStream(containerId: String): android.os.ParcelFileDescriptor {
        check(android.os.Binder.getCallingUid() == audioClientUid) { "Audio caller is not the connected application" }
        return checkNotNull(audio) { "Audio unavailable" }.openStream(containerId)
    }
    @Volatile private var callback: ILinuxContainerCallback? = null
    private val installer = Executors.newSingleThreadExecutor { Thread(it, "DextopLinuxInstall") }
    private val cancelled = ConcurrentHashMap.newKeySet<String>()
    private val activeProcesses = ConcurrentHashMap<String, Process>()

    override fun protocolVersion(): Int = PROTOCOL_VERSION

    override fun installLauncher(apk: android.os.ParcelFileDescriptor, size: Long, installerPackage: String): String {
        require(installerPackage == "moe.n4tsu.dextop" || installerPackage == "moe.n4tsu.gpdextop")
        require(size in 1..(32L * 1024 * 1024))
        // PackageManager cannot open an APK in /sdcard/Android/data under its SELinux
        // domain. Pass an already-open descriptor, then feed pm through stdin instead.
        val process = ProcessBuilder("/system/bin/pm", "install", "-r", "-i", installerPackage,
            "--user", "current", "-S", size.toString()).redirectErrorStream(true).start()
        val output = StringBuilder()
        val reader = Thread {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line -> synchronized(output) { if (output.length < 64 * 1024) output.appendLine(line) } }
            }
        }.apply { isDaemon = true; start() }
        try {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(apk).use { input ->
                process.outputStream.use { stream -> check(input.copyTo(stream) == size) { "Incomplete launcher APK" } }
            }
            check(process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS)) { "Launcher install timed out" }
            reader.join(2_000)
            val message = synchronized(output) { output.toString().trim() }
            check(process.exitValue() == 0 && message.lineSequence().any { it.trim() == "Success" }) {
                "Launcher install failed: $message"
            }
            return message
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }
    private var fifoServer: LinuxFifoServer? = null
    private var bluetoothBridge: LinuxBluetoothBridge? = null
    @Synchronized override fun attachFifoBridge(bridge: ILinuxFifoBridge): String {
        val server = fifoServer?.apply { endpoint = bridge } ?: LinuxFifoServer(bridge).also { fifoServer = it }
        return server.namespace
    }

    override fun codePath(): String = javaClass.classLoader.toString()

    override fun probe(): String =
        "protocol=$PROTOCOL_VERSION pid=${Os.getpid()} uid=${Os.getuid()} proot=${proot?.available()}"

    @Volatile private var graphics: LinuxGraphics? = null
    @Volatile private var audio: LinuxAudio? = null
    @Volatile private var networkLink: String? = null
    @Volatile private var networkResolver: String? = null
    @Volatile private var networkSnapshot: String? = null

    @Synchronized override fun consumeNetworkScanRequests(): Boolean {
        var requested = false
        containerIds().forEach { id ->
            val marker = java.io.File(LinuxPaths.rootfs(id), "etc/droiddeck-wifi-scan-request")
            if (marker.isFile && marker.delete()) requested = true
        }
        return requested
    }

    @Synchronized override fun setNetworkLink(json: String) {
        val description = LinuxNetworkLink.describe(json)
        val resolver = LinuxNetworkLink.resolver(json)
        networkLink = description
        networkResolver = resolver
        networkSnapshot = json.takeIf { JSONObject(it).has("connected") }
        containerIds().filter { readConfig(it)?.optString("state") == "installed" }
            .forEach {
                LinuxNetworkLink.stage(LinuxPaths.rootfs(it), description)
                networkSnapshot?.let { snapshot -> LinuxNetworkLink.stageSnapshot(LinuxPaths.rootfs(it), snapshot) }
                resolver?.let { dns -> LinuxNetworkLink.stageResolver(LinuxPaths.rootfs(it), dns) }
            }
    }

    @Synchronized
    override fun initialize(nativeLibraryDir: String, callback: ILinuxContainerCallback?) {
        val caller = android.os.Binder.getCallingUid()
        check(audioClientUid == -1 || audioClientUid == caller) { "Linux service already belongs to another application" }
        audioClientUid = caller
        LinuxServiceHost.initializeFonts()
        // Rebinding the app must retain the running servers and their audio ports.
        // APK changes are handled by the client's code-path/version check.
        this.callback = callback
        if (proot != null) return
        val renderer = LinuxGraphics(nativeLibraryDir)
        graphics = renderer
        val sound = LinuxAudio({ requireProot() }, { id, line -> log(id, line) }) { notifyChanged() }
        audio = sound
        proot = LinuxProot(nativeLibraryDir).apply {
            guestEnvironment = { id -> renderer.environment(id) + sound.environment(id) }
        }
        this.callback = callback
        LinuxPaths.containers.mkdirs()
        // An install interrupted by a reboot or a killed service never completes.
        containerIds().forEach { id ->
            val config = readConfig(id) ?: return@forEach
            if (config.optString("state") == "installed") runCatching {
                LinuxSessionCompatibility.install(LinuxPaths.rootfs(id), nativeLibraryDir, LinuxProot.socketNamespace(id),
                    xvfb = config.optString("distro") != LinuxCatalog.DROIDDECK_RUNTIME)
            }.onFailure { log(id, "Linux compatibility setup failed: ${it.message}") }
            if (config.optString("state") == "installing") writeConfig(id, config.put("state", "failed"))
            if (config.optString("state") == "updating") writeConfig(id, config.put("state", "installed").put("error", "Setup was interrupted; run setup again"))
        }
        // Sessions of an earlier service process (an APK update) keep their displays, buses and
        // Firefox's profile lock, so a new session's Firefox reports "already running".
        if (sessions.isEmpty() && appSessions.isEmpty()) {
            val killed = runCatching { LinuxRuntime.forceStopAll() }.getOrDefault(0)
            if (killed > 0) Log.i("DextopLinux", "Stopped $killed processes left by an earlier Linux service")
        }
        bluetoothBridge = LinuxBluetoothBridge {
            containerIds().filter { readConfig(it)?.optString("state") == "installed" }.map(LinuxPaths::rootfs)
        }.also { it.start() }
    }

    override fun listContainers(): String = JSONArray().apply {
        containerIds().mapNotNull(::readConfig).sortedBy { it.optLong("created") }.forEach { config ->
            val id = config.optString("id")
            put(config.put("running", sessions[id]?.alive == true)
                .put("appsRunning", appSessions[id]?.alive == true)
                .put("audioGeneration", audio?.generation(id) ?: 0)
                .put("runtimeState", when {
                    runtimeStates[id] == "failed" -> "failed"
                    sessions[id]?.alive == true && sessions[id]?.windowManagerReady != true -> "starting"
                    sessions[id]?.alive == true || appSessions[id]?.alive == true -> "running"
                    else -> runtimeStates[id]?.takeIf { it == "starting" } ?: "stopped"
                })
                .put("runtimeError", runtimeErrors[id]))
            // The current install phase, for a Linux page opened while installing.
            if (config.optString("state") in setOf("installing", "updating")) {
                currentProgress[id]?.let { (phase, percent, message) ->
                    config.put("phase", phase).put("percent", percent).put("message", message)
                }
            }
        }
    }.toString()

    override fun catalog(): String = LinuxCatalog.toJson()

    override fun install(name: String, distroId: String): String = installWithOptions(name, distroId, "{}")

    override fun installWithOptions(name: String, distroId: String, optionsJson: String): String {
        val distro = LinuxCatalog.find(distroId) ?: throw IllegalArgumentException("Unknown distribution $distroId")
        val options = LinuxSetupOptions.fromJson(JSONObject(optionsJson)).validate(distro)
        val id = "c" + UUID.randomUUID().toString().replace("-", "").take(12)
        LinuxPaths.container(id).mkdirs()
        writeConfig(id, JSONObject()
            .put("id", id)
            .put("name", name.ifBlank { distro.displayName })
            .put("distro", distro.id)
            .put("setupOptions", options.toJson())
            .put("state", "installing")
            .put("created", System.currentTimeMillis()))
        notifyChanged()
        installer.execute { runInstall(id, distro) }
        return id
    }

    override fun cancelInstall(containerId: String): Boolean {
        cancelled += containerId
        activeProcesses[containerId]?.let(LinuxRuntime::terminate)
        return true
    }

    override fun remove(containerId: String): Boolean {
        cancelInstall(containerId)
        sessions.remove(containerId)?.stop()
        stopApps(containerId)
        LinuxRuntime.forceStop(containerId)
        val dir = LinuxPaths.container(containerId)
        if (!dir.exists()) return false
        // Files may be read-only inside the rootfs; let the shell remove everything.
        val result = runCatching {
            ProcessBuilder("/system/bin/rm", "-rf", dir.absolutePath).redirectErrorStream(true).start().waitFor()
        }.getOrDefault(-1)
        cancelled -= containerId
        notifyChanged()
        return result == 0 && !dir.exists()
    }

    override fun repair(containerId: String): Boolean {
        val config = readConfig(containerId) ?: return false
        return setup(containerId, config.optJSONObject("setupOptions")?.toString() ?: "{}")
    }

    @Synchronized
    override fun setup(containerId: String, optionsJson: String): Boolean {
        val config = readConfig(containerId) ?: return false
        if (config.optString("state") in setOf("installing", "updating")) return false
        val distro = LinuxCatalog.find(config.optString("distro")) ?: return false
        val options = LinuxSetupOptions.fromJson(JSONObject(optionsJson)).validate(distro)
        val imageReady = config.optBoolean("imageReady") || config.optString("state") == "installed"
        cancelled -= containerId
        config.put("setupOptions", options.toJson())
        config.put("imageReady", imageReady)
        config.put("state", if (config.optString("state") == "installed") "updating" else "installing")
        writeConfig(containerId, config)
        notifyChanged()
        installer.execute {
            if (!imageReady) {
                runInstall(containerId, distro)
                return@execute
            }
            val ok = runCatching { configure(containerId, distro) }.onFailure { fail(containerId, it) }.isSuccess
            if (ok) finish(containerId)
        }
        return true
    }

    override fun runCommand(containerId: String, command: String, timeoutSeconds: Int): String {
        val runner = requireProot()
        check(LinuxPaths.rootfs(containerId).isDirectory) { "Container $containerId is not installed" }
        val result = runner.run(runner.guest(containerId, command, fakeRoot = true), timeoutSeconds.toLong().coerceIn(1, 3_600))
        return JSONObject().put("exitCode", result.exitCode).put("output", result.output).toString()
    }

    override fun openTerminal(containerId: String, rows: Int, cols: Int): android.os.ParcelFileDescriptor {
        val runner = requireProot()
        check(LinuxPaths.rootfs(containerId).isDirectory) { "Container $containerId is not installed" }
        // Root, as for package management, so apt works from the terminal.
        val builder = runner.guest(containerId,
            "cd /root 2>/dev/null; if command -v bash >/dev/null 2>&1; then exec bash -l; else exec sh -l; fi",
            fakeRoot = true)
        val pid = IntArray(1)
        val master = LinuxPty.nativeSpawn(builder.command().toTypedArray(),
            builder.environment().map { (name, value) -> "$name=$value" }.toTypedArray(),
            "/", rows.coerceIn(2, 500), cols.coerceIn(2, 1000), pid)
        check(master >= 0) { "Could not open a terminal (errno ${-master})" }
        Thread({ log(containerId, "Terminal ${pid[0]} ended: ${LinuxPty.nativeWaitFor(pid[0])}") }, "DextopTerminalWait").apply {
            isDaemon = true
            start()
        }
        log(containerId, "Terminal ${pid[0]} opened")
        // Writing the result closes this process's copy; the shell ends when the app closes its side.
        return android.os.ParcelFileDescriptor.adoptFd(master)
    }

    private val runtimeStates = ConcurrentHashMap<String, String>()
    private val runtimeErrors = ConcurrentHashMap<String, String>()
    private val runtimeGenerations = ConcurrentHashMap<String, Int>()

    override fun runtimeLogs(containerId: String): String = LinuxRuntime.logs(containerId)

    @Synchronized
    override fun resetSession(containerId: String, force: Boolean): Boolean {
        check(readConfig(containerId)?.optString("state") !in setOf("installing", "updating")) { "Setup is running; cancel it first" }
        runtimeGenerations.merge(containerId, 1, Int::plus)
        // Kill orphan trees before losing their display identities.
        val killed = if (force) LinuxRuntime.forceStop(containerId) else 0
        stopDesktop(containerId)
        stopApps(containerId)
        runtimeStates[containerId] = "stopped"
        runtimeErrors.remove(containerId)
        log(containerId, "Session reset (force=$force, killed=$killed); container data preserved")
        callback(containerId) { it.onWindowEvent(containerId, JSONObject().put("type", "reset").toString()) }
        notifyChanged()
        return true
    }

    private fun runtimeFailure(id: String, message: String, target: String = "session", desktop: String? = null) {
        runtimeStates[id] = "failed"
        runtimeErrors[id] = message.take(16000)
        runCatching { File(LinuxPaths.container(id), "runtime.log").appendText(message + "\n") }
        log(id, message)
        callback(id) { it.onWindowEvent(id, JSONObject().put("type", "error").put("target", target)
            .put("desktop", desktop).put("message", message.take(24000)).toString()) }
        notifyChanged()
    }

    private fun <T> runtimeStart(id: String, start: () -> T): T {
        runtimeStates[id] = "starting"
        runtimeErrors.remove(id)
        notifyChanged()
        return try {
            start().also { runtimeStates[id] = "running" }
        } catch (error: Throwable) {
            val message = "${error.message ?: error.javaClass.simpleName}\n${runtimeLogs(id)}"
            runtimeFailure(id, message)
            throw IllegalStateException(message.take(24000), error)
        }
    }

    private fun monitor(id: String, alive: () -> Boolean, owned: () -> Boolean) {
        val generation = runtimeGenerations[id] ?: 0
        Thread {
            while (alive()) android.os.SystemClock.sleep(500)
            if (owned() && (runtimeGenerations[id] ?: 0) == generation) {
                runtimeFailure(id, "Linux session terminated.\n${runtimeLogs(id)}")
            }
        }.apply { isDaemon = true; start() }
    }

    // region desktop sessions
    private val sessions = ConcurrentHashMap<String, LinuxDesktopSession>()

    @Synchronized
    override fun startDesktop(containerId: String, width: Int, height: Int, dpi: Int): Int {
        sessions[containerId]?.takeIf { it.alive }?.let { return it.display }
        sessions.remove(containerId)?.stop()
        check(readConfig(containerId)?.optString("state") == "installed") { "Container $containerId is not installed" }
        val display = LinuxRuntime.freeDisplay(usedDisplays())
        startRenderer(containerId)
        val session = runtimeStart(containerId) { LinuxDesktopSession.start(
            requireProot(), containerId, display,
            width.coerceIn(320, 7680), height.coerceIn(240, 4320), dpi.coerceIn(72, 480),
            xwayland = readConfig(containerId)?.optJSONObject("setupOptions")?.optString("displayBackend") == "wayland",
        )
        }
        sessions[containerId] = session
        session.onClipboard = { text -> shareClipboard(containerId, text) }
        startNotifications(containerId, display)
        monitor(containerId, { session.alive }) { sessions[containerId] === session }
        notifyChanged()
        return display
    }

    override fun stopDesktop(containerId: String) {
        sessions.remove(containerId)?.let { notifiers.remove(it.display)?.stop(); it.stop() }
        stopRendererIfIdle(containerId)
        notifyChanged()
    }

    override fun attachSurface(containerId: String, surface: android.view.Surface?): Boolean =
        surface != null && sessions[containerId]?.attachSurface(surface) == true

    override fun desktopState(containerId: String): Int {
        val session = sessions[containerId]?.takeIf { it.alive } ?: return 0
        return if (session.windowManagerReady) 2 else 1
    }

    override fun detachSurface(containerId: String) {
        sessions[containerId]?.detachSurface()
    }

    override fun pointer(containerId: String, action: Int, x: Int, y: Int, button: Int) {
        sessions[containerId]?.runCatching { pointer(action, x, y, button) }
    }

    override fun key(containerId: String, evdevCode: Int, down: Boolean) {
        appTarget(containerId)?.let { it.runCatching { key(evdevCode, down) }; return }
        sessions[containerId]?.runCatching { key(evdevCode, down) }
    }

    override fun text(containerId: String, text: String) {
        appTarget(containerId)?.let { it.runCatching { text(text) }; return }
        sessions[containerId]?.runCatching { text(text) }
    }

    override fun scroll(containerId: String, dx: Int, dy: Int) {
        appTarget(containerId)?.let { it.runCatching { scroll(dx, dy) }; return }
        sessions[containerId]?.runCatching { scroll(dx, dy) }
    }
    // endregion

    // region application windows
    private val appSessions = ConcurrentHashMap<String, LinuxAppSession>()
    private val appProcesses = ConcurrentHashMap<String, MutableList<Process>>()

    private fun appTarget(target: String): LinuxAppSession? =
        if (target.endsWith("#apps")) appSessions[target.removeSuffix("#apps")] else null

    private fun usedDisplays(): Set<Int> =
        sessions.values.map { it.display }.toSet() + appSessions.values.map { it.display }

    override fun guestLog(containerId: String, name: String, maxBytes: Int, truncate: Boolean): String {
        require(GUEST_LOG.matches(name)) { "Invalid guest log $name" }
        val file = File(LinuxPaths.rootfs(containerId), "tmp/$name")
        if (truncate) { file.writeText(""); return "" }
        if (!file.isFile) return ""
        return java.io.RandomAccessFile(file, "r").use { input ->
            val size = minOf(input.length(), maxBytes.coerceIn(0, 256 * 1024).toLong()).toInt()
            input.seek(input.length() - size)
            LinuxRuntime.readable(ByteArray(size).also { input.readFully(it) }.toString(Charsets.UTF_8))
        }
    }

    override fun setGuestState(containerId: String, name: String, content: String) {
        require(GUEST_STATE.matches(name) && content.length <= 16 * 1024) { "Invalid guest state $name" }
        val directory = File(LinuxPaths.rootfs(containerId), "tmp")
        check(directory.isDirectory) { "Container $containerId is not installed" }
        // Readers see either the old or the new file, never a partial one.
        val staged = File(directory, ".$name.staged")
        staged.writeText(content)
        check(staged.renameTo(File(directory, name))) { "Cannot write guest state $name" }
    }

    override fun listApps(containerId: String, locale: String): String {
        val rootfs = LinuxPaths.rootfs(containerId)
        if (File(rootfs, ".dextop-dd-stock-experiment").isFile) LinuxSteam.stage(rootfs)
        runCatching { LinuxSteam.stageIcon(rootfs) }.onFailure { log(containerId, "Steam icon setup failed: ${it.message}") }
        return LinuxDesktopEntries.toJson(rootfs, LinuxDesktopEntries.list(rootfs, locale))
    }

    @Synchronized
    override fun startApps(containerId: String, width: Int, height: Int, dpi: Int): Int {
        appSessions[containerId]?.takeIf { it.alive }?.let { session ->
            session.replay()
            return session.display
        }
        appSessions.remove(containerId)?.stop()
        check(readConfig(containerId)?.optString("state") == "installed") { "Container $containerId is not installed" }
        val display = LinuxRuntime.freeDisplay(usedDisplays())
        startRenderer(containerId)
        // DroidDeck's runtime has no Xvfb: its X clients go through KWin's Xwayland.
        val runtime = readConfig(containerId)?.optString("distro") == LinuxCatalog.DROIDDECK_RUNTIME
        if (runtime) stageDroidDeckFiles(containerId)
        val selectedWayland = readConfig(containerId)?.optJSONObject("setupOptions")?.optString("displayBackend") == "wayland"
        val session = runtimeStart(containerId) { LinuxAppSession.start(
            requireProot(), containerId, display,
            width.coerceIn(320, 7680), height.coerceIn(240, 4320), dpi.coerceIn(72, 640), waylandOnly = runtime, requireWayland = selectedWayland, xwayland = selectedWayland && !runtime,
        ) { event ->
            if (event.optString("type") == "tray-wine-metadata") {
                Thread({ runCatching { wineTrayMetadata.ensure(containerId, event.optInt("pid"), requireProot()) }
                    .onFailure { log(containerId, "Wine tray metadata unavailable: ${it.message}") } }, "DextopWineTraySetup").apply { isDaemon = true; start() }
            } else callback(containerId) { it.onWindowEvent(containerId, event.toString()) }
        }
        }
        appSessions[containerId] = session
        session.onClipboard = { text -> shareClipboard(containerId, text) }
        startNotifications(containerId, display)
        monitor(containerId, { session.alive }) { appSessions[containerId] === session }
        notifyChanged()
        return display
    }

    override fun launchApp(containerId: String, desktopId: String) = launchApp(containerId, desktopId, software = false)

    /**
     * Starts [desktopId]. One that crashes right away in the GPU driver (SIGSEGV or SIGABRT
     * within [GPU_CRASH_WINDOW_MS]) is started once more with software rendering, so an
     * application the GPU path cannot run still opens.
     */
    private fun launchApp(containerId: String, desktopId: String, software: Boolean, x11: Boolean = false) {
        val session = appSessions[containerId]?.takeIf { it.alive } ?: error("Application display is not running")
        val entry = LinuxDesktopEntries.find(LinuxPaths.rootfs(containerId), desktopId) ?: error("Unknown application $desktopId")
        val droidDeck = LinuxSettings.windowManager(containerId) == LinuxWindowManager.DROIDDECK
        // DroidDeck's desktop and the Steam mode size their nested output from the session's screen.
        val sized = entry.id == LinuxSteam.DROIDDECK_DESKTOP_ENTRY || entry.id == LinuxSteam.STEAM_MODE_ENTRY
        val flatpak = if (entry.flatpakId.isNotEmpty() && LinuxSettings.read().optBoolean("flatpakCompatibility")) {
            log(containerId, "Flatpak compatibility mode: no Flatpak sandbox or portal permission enforcement")
            LinuxFlatpak.plan(LinuxPaths.rootfs(containerId), entry.flatpakId)
        } else null
        val command = (if (software) "env DEXTOP_SOFTWARE_RENDERING=1 LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe " else "") +
            (if (sized) "DEXTOP_WIDTH=${session.screenWidth} DEXTOP_HEIGHT=${session.screenHeight} " else "") +
            (flatpak?.command ?: LinuxDesktopEntries.command(entry))
        val started = android.os.SystemClock.elapsedRealtime()
        callback(containerId) {
            it.onWindowEvent(containerId, JSONObject().put("type", "launch-start").put("desktop", entry.id)
                .put("startupWMClass", entry.startupWMClass).toString())
        }
        log(containerId, "launch ${entry.id}: $command")
        val backend = readConfig(containerId)?.optJSONObject("setupOptions")?.optString("displayBackend", "") ?: ""
        check(backend != "wayland" || session.wayland?.running == true) { "The selected Wayland compositor is unavailable; restart the Linux application session" }
        val gpuMode = LinuxSettings.gpuMode(containerId)
        val waylandGpu = gpuMode == LinuxGpuMode.TURNIP && graphics?.turnipWaylandInstalled(containerId) == true
        // Turnip's X11 package does not include the Wayland EGL/WSI driver.
        // Its distro EGL can still create windows and submit empty buffers, so
        // process/connection success cannot trigger the existing failure retry.
        // Keep GPU rendering through Xwayland until the Wayland driver is installed.
        val toolkitAvailable = droidDeck || gpuMode != LinuxGpuMode.TURNIP || waylandGpu
        val wayland = !x11 && entry.displayBackend != "x11" && toolkitAvailable && session.wayland?.running == true &&
            (droidDeck || backend == "wayland" || backend.isEmpty() && waylandGpu)
        if (!toolkitAvailable && !x11) log(containerId, "${entry.name}: using X11 + Zink; native Wayland GPU driver is not installed")
        val appLog = "app-${entry.id.replace(Regex("[^A-Za-z0-9._-]"), "_")}.log"
        val process = requireProot().guest(containerId, LinuxCatalog.launchScript(session.display, command, wayland = wayland,
            waylandGpu = droidDeck || waylandGpu, appLog = appLog),
            extraBinds = flatpak?.binds.orEmpty(), extraEnvironment = flatpak?.environment.orEmpty())
            .redirectErrorStream(true).start()
        process.outputStream.close()
        appProcesses.getOrPut(containerId) { java.util.Collections.synchronizedList(ArrayList()) }.apply {
            removeAll { !it.isAlive }
            add(process)
        }
        Thread {
            val output = StringBuilder()
            var exitCode: Int? = null
            var reported = 0L
            fun status(type: String, extra: JSONObject.() -> Unit) = callback(containerId) {
                it.onWindowEvent(containerId, JSONObject().put("type", type).put("desktop", entry.id).apply(extra).toString())
            }
            fun ended(code: Int) {
                log(containerId, "launch-exit ${entry.id}: code=$code elapsedMs=${android.os.SystemClock.elapsedRealtime() - started} log=/tmp/$appLog")
                val current = appProcesses[containerId]?.contains(process) == true
                val chromiumGpuFailure = LinuxLaunchFailure.chromiumGpu(code, output)
                val gpuCrash = LinuxLaunchFailure.gpu(code, output) &&
                    android.os.SystemClock.elapsedRealtime() - started < GPU_CRASH_WINDOW_MS
                val displayFailure = code != 0 && android.os.SystemClock.elapsedRealtime() - started < GPU_CRASH_WINDOW_MS &&
                    listOf("could not connect to display", "failed to connect to wayland", "could not load the qt platform plugin")
                        .any { output.contains(it, ignoreCase = true) }
                if (current && wayland && !droidDeck && !chromiumGpuFailure && (gpuCrash || displayFailure)) {
                    log(containerId, "${entry.name} failed on Wayland (exit $code); retrying on X11 with GPU rendering preserved")
                    status("launch-status") { put("line", "Wayland startup failed; retrying on X11") }
                    runCatching { launchApp(containerId, desktopId, software = software, x11 = true) }
                        .onFailure { log(containerId, "X11 retry of ${entry.id} failed: ${it.message}") }
                    return
                }
                if (current && gpuCrash && !software && gpuMode != LinuxGpuMode.SOFTWARE) {
                    log(containerId, "${entry.name}: GPU startup failed (exit $code); preserving the selected hardware renderer")
                    status("launch-status") { put("line", "GPU startup failed (exit $code). See application diagnostics.") }
                }
                status("launch-exit") { put("code", code) }
                if (code != 0 && current) runtimeFailure(containerId, "${entry.name} exited (exit $code)\n$output", "apps", entry.id)
            }
            runCatching { process.inputStream.bufferedReader().useLines { lines -> lines.forEach { line ->
                if (line.startsWith(LinuxCatalog.LAUNCH_EXIT_MARKER)) {
                    // The command ended (programs it left running may still write): a
                    // single-instance application handed the launch to its running instance,
                    // or the application quit or failed.
                    if (exitCode == null) {
                        exitCode = line.removePrefix(LinuxCatalog.LAUNCH_EXIT_MARKER).trim().toIntOrNull() ?: 0
                        ended(exitCode!!)
                    }
                    return@forEach
                }
                if (line.startsWith("proot warning") || line.startsWith("proot info")) return@forEach
                output.appendLine(line)
                if (output.length > 12000) output.delete(0, output.length - 12000)
                // What the application prints while it starts, a few times a second at most.
                val now = android.os.SystemClock.elapsedRealtime()
                if (exitCode == null && line.isNotBlank() && now - reported > 300) {
                    reported = now
                    status("launch-status") { put("line", line.take(300)) }
                }
            } } }
            // Without the marker the launch itself was killed (session stopped, out of memory).
            if (exitCode == null) ended(process.waitFor())
        }
            .apply { isDaemon = true; start() }

    }

    override fun stopApps(containerId: String) {
        wineTrayMetadata.stop(containerId)
        appProcesses.remove(containerId)?.toList()?.forEach { runCatching { LinuxRuntime.terminate(it) } }
        appSessions.remove(containerId)?.let { notifiers.remove(it.display)?.stop(); it.stop() }
        stopRendererIfIdle(containerId)
    }

    // region notifications
    /** Notification servers by X display (each display has its own session bus). */
    private val wineTrayMetadata = LinuxWineTrayMetadata()

    private val notifiers = ConcurrentHashMap<Int, LinuxNotificationServer>()

    private fun startNotifications(containerId: String, display: Int) {
        notifiers.remove(display)?.stop()
        notifiers[display] = LinuxNotificationServer(display) { event ->
            callback(containerId) { it.onNotification(containerId, event.toString()) }
        }
    }

    override fun notificationAction(containerId: String, key: String, action: String) {
        val display = key.substringBefore(':').toIntOrNull() ?: return
        val id = key.substringAfter(':').toIntOrNull() ?: return
        val server = notifiers[display] ?: return
        if (action == "dismiss") server.dismiss(id) else server.invoke(id, action)
    }
    // endregion

    // region clipboard
    /** One clipboard per container: a copy on either display reaches the other and Android. */
    private fun shareClipboard(containerId: String, text: String) {
        sessions[containerId]?.runCatching { setClipboard(text) }
        appSessions[containerId]?.runCatching { setClipboard(text) }
        callback(containerId) { it.onClipboard(containerId, text) }
    }

    override fun setClipboard(containerId: String, text: String) {
        sessions[containerId]?.runCatching { setClipboard(text) }
        appSessions[containerId]?.runCatching { setClipboard(text) }
    }
    // endregion

    // region graphics
    /** Starts the selected hardware renderer; software rendering must be explicitly selected. */
    private fun startRenderer(containerId: String) {
        networkLink?.let { LinuxNetworkLink.stage(LinuxPaths.rootfs(containerId), it) }
        networkSnapshot?.let { LinuxNetworkLink.stageSnapshot(LinuxPaths.rootfs(containerId), it) }
        networkResolver?.let { LinuxNetworkLink.stageResolver(LinuxPaths.rootfs(containerId), it) }
        LinuxSessionCompatibility.stage(LinuxPaths.rootfs(containerId), requireProot().nativeLibraryDir, LinuxProot.socketNamespace(containerId))
        val distro = readConfig(containerId)?.optString("distro")?.let(LinuxCatalog::find)
        // DroidDeck's runtime is used as DroidDeck ships it: Turnip on KGSL, no VirGL,
        // and pacman never runs (its own builds are not in pacman's database).
        if (distro?.id == LinuxCatalog.DROIDDECK_RUNTIME) {
            audio?.ensure(containerId, distro.family, install = false)
            return
        }
        if (distro?.family == LinuxPackageFamily.APT && LinuxSettings.gpuMode(containerId) == LinuxGpuMode.TURNIP &&
            graphics?.turnipInstalled(containerId) == true) {
            LinuxGpuRuntime.prepare(LinuxPaths.rootfs(containerId)) { script ->
                val result = requireProot().run(requireProot().guest(containerId, script), timeoutSeconds = 60)
                check(result.exitCode == 0) { "GPU runtime setup failed: ${result.output.takeLast(2000)}" }
                if (result.output.isNotBlank()) log(containerId, result.output)
            }
        }
        runCatching { LinuxSteam.stage(LinuxPaths.rootfs(containerId)) }
            .onFailure { log(containerId, "Steam launcher setup failed: ${it.message}") }
        val hardware = graphics?.ensure(containerId) { line -> log(containerId, line) } == true
        check(hardware || LinuxSettings.gpuMode(containerId) == LinuxGpuMode.SOFTWARE) {
            "The selected GPU renderer is unavailable. Repair the graphics runtime or select another hardware renderer in Linux settings."
        }
        ensureFakeroot(containerId, distro?.family)
        audio?.ensure(containerId, distro?.family)
    }

    /** Containers set up before real-uid sessions lack fakeroot, which sessions use for apt and sudo. */
    private fun ensureFakeroot(containerId: String, family: LinuxPackageFamily?) {
        if (File(LinuxPaths.rootfs(containerId), "usr/bin/fakeroot").exists()) return
        val install = when (family ?: LinuxPackageFamily.APT) {
            LinuxPackageFamily.APT -> "export DEBIAN_FRONTEND=noninteractive\n" +
                "apt-get install -y --no-install-recommends fakeroot || " +
                "{ apt-get update && apt-get install -y --no-install-recommends fakeroot; }\n"
            LinuxPackageFamily.PACMAN -> "pacman -S --noconfirm --needed fakeroot\n"
        }
        log(containerId, "Installing fakeroot for package management in sessions")
        val runner = proot ?: return
        runCatching { runner.run(runner.guest(containerId, install, fakeRoot = true), 600) { line -> log(containerId, line) } }
    }

    private fun stopRendererIfIdle(containerId: String) {
        if (sessions[containerId]?.alive != true && appSessions[containerId]?.alive != true) {
            graphics?.stop(containerId)
            audio?.stop(containerId)
            notifyChanged()
        }
    }

    override fun linuxSettings(): String = (graphics?.status() ?: LinuxSettings.read()).toString()

    /**
     * Saves settings. A changed renderer applies to applications started
     * afterwards; running sessions keep theirs until restarted.
     */
    override fun setLinuxSettings(json: String): String {
        val update = JSONObject(json)
        val active = (sessions.keys + appSessions.keys).toSet()
        val before = active.associateWith { LinuxSettings.gpuMode(it) }
        // {"container": id, ...} changes that container's settings; otherwise the global defaults.
        val container = update.optString("container").takeIf { it.isNotEmpty() && readConfig(it) != null }
        if (container != null) LinuxSettings.writeContainer(container, update) else LinuxSettings.write(update)
        active.filter { LinuxSettings.gpuMode(it) != before[it] }.forEach(::startRenderer)
        return linuxSettings()
    }
    // endregion

    override fun attachWindow(containerId: String, xid: Int, surface: android.view.Surface?): Boolean =
        surface != null && appSessions[containerId]?.attachSurface(xid, surface) == true

    override fun attachDeckWindow(containerId: String, xid: Int, surface: android.view.Surface?): Boolean =
        surface != null && appSessions[containerId]?.attachSurface(xid, surface, deckDirect = true,
            deck30 = runCatching { File(LinuxPaths.rootfs(containerId), "tmp/${LinuxSteam.STEAM_MODE_RATE}").readText().trim() == "30" }.getOrDefault(false),
            noVsync = runCatching { File(LinuxPaths.rootfs(containerId), "tmp/${LinuxSteam.STEAM_MODE_VSYNC}").readText().trim() == "0" }.getOrDefault(false)) == true

    override fun detachWindow(containerId: String, xid: Int) {
        appSessions[containerId]?.detachSurface(xid)
    }

    override fun configureWindow(containerId: String, xid: Int, x: Int, y: Int, width: Int, height: Int) {
        appSessions[containerId]?.runCatching { configure(xid, x, y, width, height) }
    }

    override fun activateWindow(containerId: String, xid: Int) {
        appSessions[containerId]?.runCatching { activate(xid) }
    }

    override fun setWindowState(containerId: String, xid: Int, maximized: Boolean, minimized: Boolean) {
        appSessions[containerId]?.runCatching { setWindowState(xid, maximized, minimized) }
    }

    @Synchronized override fun updateDisplay(containerId: String, apps: Boolean, width: Int, height: Int, dpi: Int, refreshRate: Float) {
        val config = LinuxDisplayConfiguration.create(width, height, dpi, refreshRate)
        val application = if (apps) appSessions[containerId] else null
        val desktop = if (!apps) sessions[containerId] else null
        val display = application?.display ?: desktop?.display ?: return
        if ((application?.displayConfiguration ?: desktop?.displayConfiguration) == config) return
        runCatching {
            if (application?.wayland != null && readConfig(containerId)?.optString("distro") == LinuxCatalog.DROIDDECK_RUNTIME) {
                application.updateDisplay(config)
            } else if (application?.usesXwayland == true || desktop?.usesXwayland == true) {
                application?.updateDisplay(config); desktop?.updateDisplay(config)
            } else {
                val result = requireProot().run(requireProot().guest(containerId, LinuxCatalog.updateDisplayScript(display, config, apps)), 15)
                check(result.exitCode == 0) { "Linux display update failed: ${result.output.takeLast(2000)}" }
                application?.updateDisplay(config); desktop?.updateDisplay(config)
            }
            callback(containerId) { it.onWindowEvent(containerId, JSONObject().put("type", "display-config").put("apps", apps)
                .put("w", config.width).put("h", config.height).put("refreshMilliHz", config.refreshMilliHz).toString()) }
        }.onFailure { log(containerId, "Linux display update failed: ${it.message}") }
    }

    override fun relativePointer(containerId: String, xid: Int, action: Int, dx: Int, dy: Int, button: Int) {
        if (xid == 0) sessions[containerId]?.relativePointer(action, dx, dy, button)
        else appSessions[containerId]?.relativePointer(xid, action, dx, dy, button)
    }
    override fun pointerCaptureRequested(containerId: String, xid: Int): Boolean =
        if (xid == 0) sessions[containerId]?.pointerCaptureRequested() == true
        else appSessions[containerId]?.pointerCaptureRequested(xid) == true

    override fun trayAction(containerId: String, key: String, button: Int, x: Int, y: Int) {
        appSessions[containerId]?.trayAction(key, button, x, y)
    }

    override fun closeWindow(containerId: String, xid: Int) {
        appSessions[containerId]?.runCatching { close(xid) }
    }

    override fun windowPointer(containerId: String, xid: Int, action: Int, x: Int, y: Int, button: Int) {
        appSessions[containerId]?.runCatching { pointer(xid, action, x, y, button) }
    }
    // endregion

    override fun destroy() {
        sessions.values.forEach { runCatching { it.stop() } }
        appSessions.keys.toList().forEach { runCatching { stopApps(it) } }
        graphics?.stopAll()
        audio?.stopAll()
        wineTrayMetadata.stopAll()
        notifiers.values.forEach { it.stop() }
        activeProcesses.values.forEach { runCatching { LinuxRuntime.terminate(it) } }
        bluetoothBridge?.close()
        installer.shutdownNow()
        // A killed proot cannot take its tracees (Firefox, the session bus) down with it.
        runCatching { LinuxRuntime.forceStopAll() }
        exitProcess(0)
    }

    // region install pipeline
    private fun runInstall(id: String, distro: LinuxDistro) {
        distro.image?.let { image ->
            runCatching {
                progress(id, "prepare", -1, image.url.substringAfterLast('/'))
                val tarball = File(LinuxPaths.container(id), image.url.substringAfterLast('/'))
                val actual = fetch(id, image, tarball)
                progress(id, "verify", -1, actual.take(16))
                check(actual.equals(image.sha256, ignoreCase = true)) { "SHA-256 mismatch: expected ${image.sha256}, got $actual" }
                log(id, "SHA-256 verified $actual (${image.url})")
                extract(id, tarball)
                tarball.delete()
                if (distro.id == LinuxCatalog.DROIDDECK_RUNTIME) {
                    // DroidDeck's markers: droiddeck-session reports /.version, the desktop package its tag.
                    File(LinuxPaths.rootfs(id), ".version").writeText(image.release())
                    installOverlay(id, LinuxCatalog.DROIDDECK_DESKTOP)
                    File(LinuxPaths.rootfs(id), ".droiddeck-pkg-desktop-kde").writeText(LinuxCatalog.DROIDDECK_DESKTOP.release())
                    stageDroidDeckFiles(id)
                }
                readConfig(id)?.let { writeConfig(id, it.put("imageReady", true)) }
                configure(id, distro)
            }.onSuccess { finish(id) }.onFailure { fail(id, it) }
            return
        }
        runCatching {
            progress(id, "prepare", -1, "${distro.imageDistro}/${distro.imageRelease}")
            val build = latestBuild(distro)
            log(id, "image ${distro.imageDistro}/${distro.imageRelease}/${LinuxCatalog.ARCHITECTURE} build $build")
            val base = "${LinuxCatalog.IMAGE_SERVER}/images/${distro.imageDistro}/${distro.imageRelease}/" +
                "${LinuxCatalog.ARCHITECTURE}/${LinuxCatalog.VARIANT}/$build"
            val expected = text("$base/SHA256SUMS").lineSequence()
                .map { it.trim().split(Regex("\\s+")) }
                .firstOrNull { it.size == 2 && it[1].trimStart('*') == "rootfs.tar.xz" }?.get(0)
                ?: error("rootfs.tar.xz is missing from SHA256SUMS")
            val tarball = File(LinuxPaths.container(id), "rootfs.tar.xz")
            val actual = download(id, "$base/rootfs.tar.xz", tarball)
            progress(id, "verify", -1, actual.take(16))
            check(actual.equals(expected, ignoreCase = true)) { "SHA-256 mismatch: expected $expected, got $actual" }
            log(id, "SHA-256 verified $actual")
            extract(id, tarball)
            readConfig(id)?.let { writeConfig(id, it.put("imageReady", true)) }
            tarball.delete()
            configure(id, distro)
        }.onSuccess { finish(id) }.onFailure { fail(id, it) }
    }

    private fun checkCancelled(id: String) {
        if (id in cancelled) throw InterruptedException("Installation cancelled")
    }

    private fun latestBuild(distro: LinuxDistro): String {
        val prefix = "${distro.imageDistro};${distro.imageRelease};${LinuxCatalog.ARCHITECTURE};${LinuxCatalog.VARIANT};"
        val line = text(LinuxCatalog.IMAGE_INDEX).lineSequence().firstOrNull { it.startsWith(prefix) }
            ?: error("No ${LinuxCatalog.ARCHITECTURE} image for ${distro.imageDistro} ${distro.imageRelease}")
        return line.split(';')[4]
    }

    private fun connect(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 20_000
        readTimeout = 60_000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "Dextop-Linux/$PROTOCOL_VERSION")
    }

    private fun text(url: String): String = connect(url).run {
        try {
            check(responseCode == 200) { "HTTP $responseCode for $url" }
            inputStream.bufferedReader().readText()
        } finally {
            disconnect()
        }
    }

    /** Downloads [url] to [target] and returns its SHA-256. */
    /**
     * Like DroidDeck's installer, an archive left by an earlier attempt is kept
     * and reused when it is complete and matches, instead of downloading it again.
     */
    private fun fetch(id: String, image: LinuxImage, target: File): String {
        if (target.isFile && target.length() == image.size) {
            progress(id, "verify", -1, target.name)
            val digest = MessageDigest.getInstance("SHA-256")
            target.inputStream().use { input ->
                val buffer = ByteArray(BUFFER)
                while (true) {
                    checkCancelled(id)
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (actual.equals(image.sha256, ignoreCase = true)) {
                log(id, "reusing the downloaded ${target.name}")
                return actual
            }
        }
        return download(id, image.url, target)
    }

    private fun download(id: String, url: String, target: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val connection = connect(url)
        try {
            check(connection.responseCode == 200) { "HTTP ${connection.responseCode} for $url" }
            val total = connection.contentLengthLong
            var received = 0L
            var lastPercent = -1
            connection.inputStream.use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(BUFFER)
                    while (true) {
                        checkCancelled(id)
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        received += read
                        val percent = if (total > 0) (received * 100 / total).toInt() else -1
                        if (percent != lastPercent) {
                            lastPercent = percent
                            progress(id, "download", percent, "$received/$total")
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Staged Java extraction avoids Samsung's rejection of traced Android executables. */
    private fun extract(id: String, tarball: File) {
        val rootfs = LinuxPaths.rootfs(id)
        val staging = File(LinuxPaths.container(id), "rootfs-extract")
        LinuxRootfsArchive.deleteTree(staging)
        try {
            unpack(id, tarball, staging)
            check(File(staging, "bin/sh").exists() || File(staging, "usr/bin/sh").exists()) {
                "Root filesystem extraction completed without a shell"
            }
            checkCancelled(id)
            LinuxRootfsArchive.deleteTree(rootfs)
            check(staging.renameTo(rootfs)) { "Cannot commit extracted root filesystem" }
            File(rootfs, "dev").mkdirs()
            log(id, "extracted (in-process tar; no traced Android executable)")
        } catch (error: Exception) {
            log(id, "Root filesystem extraction failed: ${error.message}")
            throw error
        } finally {
            LinuxRootfsArchive.deleteTree(staging)
        }
    }

    /** Extracts [tarball] into the empty [staging]: .tar.zst through libzstd_cat.so (nativeLibraryDir), .tar.xz through XZ for Java. */
    private fun unpack(id: String, tarball: File, staging: File) {
        progress(id, "extract", -1, "")
        val decompressor = if (tarball.name.endsWith(".zst")) {
            val tool = File(requireProot().nativeLibraryDir, "libzstd_cat.so")
            check(tool.canExecute()) { "zstd decompressor is not packaged" }
            ProcessBuilder(tool.absolutePath).redirectInput(tarball).redirectError(ProcessBuilder.Redirect.INHERIT).start()
        } else null
        try {
            (decompressor?.inputStream?.buffered(BUFFER) ?: XZInputStream(BufferedInputStream(FileInputStream(tarball), BUFFER))).use { input ->
                var lastMiB = 0L
                LinuxRootfsArchive.extract(input, staging, { checkCancelled(id) }) { written ->
                    val mib = written / 1_048_576
                    if (mib / 32 > lastMiB / 32) progress(id, "extract", -1, "$mib MiB")
                    lastMiB = mib
                }
            }
            decompressor?.let { check(it.waitFor() == 0) { "zstd decompression failed (exit ${it.exitValue()})" } }
        } finally {
            decompressor?.destroy()
        }
    }

    /** DroidDeck stages its session files before every session; so does Dextop. */
    private fun stageDroidDeckFiles(id: String) {
        LinuxDroidDeckFiles.stage(LinuxPaths.rootfs(id), requireProot().nativeLibraryDir) { log(id, it) }
        LinuxSteam.writeDroidDeckDesktop(LinuxPaths.rootfs(id))
    }

    /** Downloads [image], checks it and extracts it over [id]'s root filesystem (DroidDeck's desktop package). */
    private fun installOverlay(id: String, image: LinuxImage) {
        val name = image.url.substringAfterLast('/')
        progress(id, "prepare", -1, name)
        val tarball = File(LinuxPaths.container(id), name)
        try {
            val actual = fetch(id, image, tarball)
            progress(id, "verify", -1, actual.take(16))
            check(actual.equals(image.sha256, ignoreCase = true)) { "SHA-256 mismatch: expected ${image.sha256}, got $actual" }
            log(id, "SHA-256 verified $actual (${image.url})")
            val staging = File(LinuxPaths.container(id), "overlay-extract")
            LinuxRootfsArchive.deleteTree(staging)
            try {
                unpack(id, tarball, staging)
                LinuxRootfsArchive.merge(staging, LinuxPaths.rootfs(id))
                log(id, "installed $name over the root filesystem")
            } finally {
                LinuxRootfsArchive.deleteTree(staging)
            }
            tarball.delete()
        } catch (error: Exception) {
            // A complete, matching download is reused by the next attempt.
            if (tarball.length() != image.size) tarball.delete()
            throw error
        }
    }

    private fun configure(id: String, distro: LinuxDistro) {
        val runner = requireProot()
        checkCancelled(id)
        val options = LinuxSetupOptions.fromJson(readConfig(id)?.optJSONObject("setupOptions")).validate(distro)
        progress(id, "configure", -1, "")
        val runtime = distro.id == LinuxCatalog.DROIDDECK_RUNTIME
        val configureScript = if (runtime) LinuxCatalog.runtimeConfigureScript() else LinuxCatalog.configureScript(distro.family)
        val configured = runner.run(runner.guest(id, configureScript, fakeRoot = true), 600, onProcess = { activeProcesses[id] = it }) { log(id, it) }
        check(configured.exitCode == 0) { "Configuration failed (exit ${configured.exitCode})" }
        checkCancelled(id)
        // DroidDeck's runtime and desktop package bring everything; pacman would replace its own builds.
        if (!runtime) provisionPackages(id, distro, runner)
        setupSteps(id, distro, options, runner)
    }

    private fun provisionPackages(id: String, distro: LinuxDistro, runner: LinuxProot) {
        progress(id, "refresh", -1, "")
        val refreshed = runner.run(runner.guest(id, LinuxCatalog.refreshScript(distro.family), fakeRoot = true), 1_800, onProcess = { activeProcesses[id] = it }) { line ->
            log(id, line)
            // apt: "Get:12 http://deb.debian.org/debian trixie/main arm64 Packages [9,670 kB]".
            if (line.startsWith("Get:") || line.startsWith("Hit:")) progress(id, "refresh", -1, line.substringAfter(' ').take(80))
        }
        check(refreshed.exitCode == 0) { "Package index refresh failed (exit ${refreshed.exitCode})" }
        checkCancelled(id)
        progress(id, "provision", -1, "")
        var total = 0
        var done = 0
        val pacmanStep = Regex("^\\((\\d+)/(\\d+)\\) installing (\\S+)")
        val provisioned = runner.run(runner.guest(id, LinuxCatalog.provisionScript(distro.family), fakeRoot = true), 3_600, onProcess = { activeProcesses[id] = it }) { line ->
            log(id, line)
            // apt: "0 upgraded, 312 newly installed, ..." then one "Setting up" per package
            // (each package is unpacked first, so unpacking counts as the first half).
            // pacman: "(12/200) installing foo".
            Regex("(\\d+) newly installed").find(line)?.let { total = it.groupValues[1].toInt() * 2 }
            val pacman = pacmanStep.find(line)
            when {
                pacman != null -> {
                    val (step, count, name) = pacman.destructured
                    progress(id, "provision", step.toInt() * 100 / count.toInt().coerceAtLeast(1), name)
                }
                line.startsWith("Unpacking ") || line.startsWith("Setting up ") -> {
                    done++
                    val percent = if (total > 0) (done * 100 / total).coerceAtMost(99) else -1
                    progress(id, "provision", percent, line.substringBefore(" (").take(80))
                }
            }
        }
        check(provisioned.exitCode == 0) { "Package installation failed (exit ${provisioned.exitCode})" }
    }

    private fun setupSteps(id: String, distro: LinuxDistro, options: LinuxSetupOptions, runner: LinuxProot) {
        for (step in LinuxSetupPlan.steps(distro, options)) {
            checkCancelled(id)
            // Steam's FEX preflight needs socket emulation before finish() can run.
            if (step.phase == "steam") {
                LinuxSessionCompatibility.stage(LinuxPaths.rootfs(id), runner.nativeLibraryDir, LinuxProot.socketNamespace(id))
            }
            progress(id, step.phase, -1, "")
            val result = try {
                LinuxSetupScript.run(LinuxPaths.rootfs(id), step.script) { command ->
                    runner.run(runner.guest(id, command, fakeRoot = true), 3_600, onProcess = { activeProcesses[id] = it }) { line ->
                        log(id, line)
                        if (line.startsWith("Unpacking ") || line.startsWith("Setting up ")) progress(id, step.phase, -1, line.take(100))
                    }
                }
            } finally { activeProcesses.remove(id) }
            checkCancelled(id)
            check(result.exitCode == 0) { "${step.phase} failed (exit ${result.exitCode}); see installation log" }
        }
    }

    private fun finish(id: String) {
        val config = readConfig(id) ?: return
        LinuxSessionCompatibility.install(LinuxPaths.rootfs(id), requireProot().nativeLibraryDir,
            LinuxProot.socketNamespace(id), xvfb = config.optString("distro") != LinuxCatalog.DROIDDECK_RUNTIME)
        activeProcesses.remove(id)
        progress(id, "finish", -1, "")
        val bytes = runCatching {
            ProcessBuilder("/system/bin/du", "-sk", LinuxPaths.container(id).absolutePath).start()
                .inputStream.bufferedReader().readText().trim().split(Regex("\\s+")).first().toLong() * 1024
        }.getOrDefault(-1L)
        config.remove("error")
        writeConfig(id, config.put("state", "installed").put("bytes", bytes))
        progress(id, "done", 100, "")
        notifyChanged()
    }

    private fun fail(id: String, error: Throwable) {
        activeProcesses.remove(id)
        Log.e(TAG, "container $id install failed", error)
        log(id, "error: ${error.message}")
        cancelled -= id
        readConfig(id)?.let {
            val state = if (it.optString("state") == "updating") "installed" else "failed"
            writeConfig(id, it.put("state", state).put("error", error.message.orEmpty()))
        }
        progress(id, "error", -1, error.message.orEmpty())
        notifyChanged()
    }
    // endregion

    // region state
    private fun requireProot(): LinuxProot =
        proot?.takeIf { it.available() } ?: error("proot is unavailable; initialize() was not called")

    private fun containerIds(): List<String> =
        LinuxPaths.containers.listFiles()?.filter { File(it, "config.json").isFile }?.map { it.name }.orEmpty()

    private fun readConfig(id: String): JSONObject? =
        runCatching { JSONObject(LinuxPaths.config(id).readText()) }.getOrNull()

    @Synchronized
    private fun writeConfig(id: String, config: JSONObject) {
        val file = LinuxPaths.config(id)
        val temp = File(file.parentFile, "config.json.tmp")
        temp.writeText(config.toString(2))
        temp.renameTo(file)
    }

    private fun log(id: String, line: String) {
        runCatching { LinuxPaths.log(id).appendText(line + "\n") }
        callback(id) { it.onLog(id, line) }
    }

    /** Last progress of each container being installed or repaired. */
    private val currentProgress = ConcurrentHashMap<String, Triple<String, Int, String>>()

    private fun progress(id: String, phase: String, percent: Int, message: String) {
        if (phase == "done" || phase == "error") currentProgress.remove(id) else currentProgress[id] = Triple(phase, percent, message)
        callback(id) { it.onProgress(id, phase, percent, message) }
    }

    private fun notifyChanged() = callback(null) { it.onContainersChanged() }

    private fun callback(id: String?, action: (ILinuxContainerCallback) -> Unit) {
        val target = callback ?: return
        try {
            action(target)
        } catch (error: RemoteException) {
            Log.w(TAG, "callback failed for ${id ?: "list"}; client is gone", error)
            callback = null
        }
    }
    // endregion
}
