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
        const val PROTOCOL_VERSION = 6
        private const val BUFFER = 256 * 1024
    }

    @Volatile private var proot: LinuxProot? = null
    @Volatile private var callback: ILinuxContainerCallback? = null
    private val installer = Executors.newSingleThreadExecutor { Thread(it, "DextopLinuxInstall") }
    private val cancelled = ConcurrentHashMap.newKeySet<String>()
    private val activeProcesses = ConcurrentHashMap<String, Process>()

    override fun protocolVersion(): Int = PROTOCOL_VERSION

    override fun probe(): String =
        "protocol=$PROTOCOL_VERSION pid=${Os.getpid()} uid=${Os.getuid()} proot=${proot?.available()}"

    override fun initialize(nativeLibraryDir: String, callback: ILinuxContainerCallback?) {
        proot = LinuxProot(nativeLibraryDir)
        this.callback = callback
        LinuxPaths.containers.mkdirs()
        // An install interrupted by a reboot or a killed service never completes.
        containerIds().forEach { id ->
            val config = readConfig(id) ?: return@forEach
            if (config.optString("state") == "installing") writeConfig(id, config.put("state", "failed"))
            if (config.optString("state") == "updating") writeConfig(id, config.put("state", "installed").put("error", "Setup was interrupted; run setup again"))
        }
    }

    override fun listContainers(): String = JSONArray().apply {
        containerIds().mapNotNull(::readConfig).sortedBy { it.optLong("created") }.forEach { config ->
            val id = config.optString("id")
            put(config.put("running", sessions[id]?.alive == true)
                .put("appsRunning", appSessions[id]?.alive == true)
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
        activeProcesses[containerId]?.destroyForcibly()
        return true
    }

    override fun remove(containerId: String): Boolean {
        cancelInstall(containerId)
        sessions.remove(containerId)?.stop()
        stopApps(containerId)
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
        val result = runner.run(runner.guest(containerId, command), timeoutSeconds.toLong().coerceIn(1, 3_600))
        return JSONObject().put("exitCode", result.exitCode).put("output", result.output).toString()
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

    private fun runtimeFailure(id: String, message: String, target: String = "session") {
        runtimeStates[id] = "failed"
        runtimeErrors[id] = message.take(16000)
        runCatching { File(LinuxPaths.container(id), "runtime.log").appendText(message + "\n") }
        log(id, message)
        callback(id) { it.onWindowEvent(id, JSONObject().put("type", "error").put("target", target).put("message", message.take(24000)).toString()) }
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
        val session = runtimeStart(containerId) { LinuxDesktopSession.start(
            requireProot(), containerId, display,
            width.coerceIn(320, 7680), height.coerceIn(240, 4320), dpi.coerceIn(72, 480),
        )
        }
        sessions[containerId] = session
        monitor(containerId, { session.alive }) { sessions[containerId] === session }
        notifyChanged()
        return display
    }

    override fun stopDesktop(containerId: String) {
        sessions.remove(containerId)?.stop()
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

    override fun listApps(containerId: String, locale: String): String {
        val rootfs = LinuxPaths.rootfs(containerId)
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
        val session = runtimeStart(containerId) { LinuxAppSession.start(
            requireProot(), containerId, display,
            width.coerceIn(320, 7680), height.coerceIn(240, 4320), dpi.coerceIn(72, 640),
        ) { event -> callback(containerId) { it.onWindowEvent(containerId, event.toString()) } }
        }
        appSessions[containerId] = session
        monitor(containerId, { session.alive }) { appSessions[containerId] === session }
        notifyChanged()
        return display
    }

    override fun launchApp(containerId: String, desktopId: String) {
        val session = appSessions[containerId]?.takeIf { it.alive } ?: error("Application display is not running")
        val entry = LinuxDesktopEntries.find(LinuxPaths.rootfs(containerId), desktopId) ?: error("Unknown application $desktopId")
        val command = LinuxDesktopEntries.command(entry)
        log(containerId, "launch ${entry.id}: $command")
        val process = requireProot().guest(containerId, LinuxCatalog.launchScript(session.display, command))
            .redirectErrorStream(true).start()
        process.outputStream.close()
        appProcesses.getOrPut(containerId) { java.util.Collections.synchronizedList(ArrayList()) }.apply {
            removeAll { !it.isAlive }
            add(process)
        }
        Thread {
            val output = StringBuilder()
            runCatching { process.inputStream.bufferedReader().useLines { lines -> lines.forEach { line ->
                output.appendLine(line)
                if (output.length > 12000) output.delete(0, output.length - 12000)
            } } }
            val code = process.waitFor()
            if (code != 0 && appProcesses[containerId]?.contains(process) == true) runtimeFailure(containerId, "${entry.name} exited (exit $code)\n$output\n${runtimeLogs(containerId)}", "apps")
        }
            .apply { isDaemon = true; start() }

    }

    override fun stopApps(containerId: String) {
        appProcesses.remove(containerId)?.toList()?.forEach { runCatching { it.destroyForcibly() } }
        appSessions.remove(containerId)?.stop()
    }

    override fun attachWindow(containerId: String, xid: Int, surface: android.view.Surface?): Boolean =
        surface != null && appSessions[containerId]?.attachSurface(xid, surface) == true

    override fun detachWindow(containerId: String, xid: Int) {
        appSessions[containerId]?.detachSurface(xid)
    }

    override fun configureWindow(containerId: String, xid: Int, x: Int, y: Int, width: Int, height: Int) {
        appSessions[containerId]?.runCatching { configure(xid, x, y, width, height) }
    }

    override fun activateWindow(containerId: String, xid: Int) {
        appSessions[containerId]?.runCatching { activate(xid) }
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
        activeProcesses.values.forEach { runCatching { it.destroyForcibly() } }
        installer.shutdownNow()
        exitProcess(0)
    }

    // region install pipeline
    private fun runInstall(id: String, distro: LinuxDistro) {
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
        progress(id, "extract", -1, "")
        LinuxRootfsArchive.deleteTree(staging)
        try {
            XZInputStream(BufferedInputStream(FileInputStream(tarball), BUFFER)).use { input ->
                var lastMiB = 0L
                LinuxRootfsArchive.extract(input, staging, { checkCancelled(id) }) { written ->
                    val mib = written / 1_048_576
                    if (mib / 32 > lastMiB / 32) progress(id, "extract", -1, "$mib MiB")
                    lastMiB = mib
                }
            }
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

    private fun configure(id: String, distro: LinuxDistro) {
        val runner = requireProot()
        checkCancelled(id)
        val options = LinuxSetupOptions.fromJson(readConfig(id)?.optJSONObject("setupOptions")).validate(distro)
        progress(id, "configure", -1, "")
        val configured = runner.run(runner.guest(id, LinuxCatalog.configureScript(distro.family)), 600, onProcess = { activeProcesses[id] = it }) { log(id, it) }
        check(configured.exitCode == 0) { "Configuration failed (exit ${configured.exitCode})" }
        checkCancelled(id)
        progress(id, "refresh", -1, "")
        val refreshed = runner.run(runner.guest(id, LinuxCatalog.refreshScript(distro.family)), 1_800, onProcess = { activeProcesses[id] = it }) { line ->
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
        val provisioned = runner.run(runner.guest(id, LinuxCatalog.provisionScript(distro.family)), 3_600, onProcess = { activeProcesses[id] = it }) { line ->
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
        for (step in LinuxSetupPlan.steps(distro, options)) {
            checkCancelled(id)
            progress(id, step.phase, -1, "")
            val result = try {
                runner.run(runner.guest(id, step.script), 3_600, onProcess = { activeProcesses[id] = it }) { line ->
                    log(id, line)
                    if (line.startsWith("Unpacking ") || line.startsWith("Setting up ")) progress(id, step.phase, -1, line.take(100))
                }
            } finally { activeProcesses.remove(id) }
            checkCancelled(id)
            check(result.exitCode == 0) { "${step.phase} failed (exit ${result.exitCode}); see installation log" }
        }
    }

    private fun finish(id: String) {
        activeProcesses.remove(id)
        val config = readConfig(id) ?: return
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
