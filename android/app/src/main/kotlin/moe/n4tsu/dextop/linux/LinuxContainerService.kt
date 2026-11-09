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
import java.io.InputStream
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
class LinuxContainerService(context: android.content.Context? = null) : ILinuxContainerService.Stub() {
    // Shizuku/Stellar can also supply a Context to a shell-owned UserService.
    // Only an ordinary Android UID selects the in-app runtime.
    private val androidContext = context?.takeUnless { Os.getuid() in setOf(0, 2000) }
    companion object {
        private const val TAG = "DextopLinuxService"
        // Includes APK-approved macOS runtime installation (AIDL transaction 65).
        const val PROTOCOL_VERSION = 44
        /** An application ending this soon after its start with SIGSEGV/SIGABRT is retried in software. */
        private const val GPU_CRASH_WINDOW_MS = 20_000L
        /** [GPU_CRASH_LIMIT] GPU crashes within this long turn the GPU off for the session. */
        private const val GPU_CRASH_PERIOD_MS = 10 * 60_000L
        private const val GPU_CRASH_LIMIT = 3
        private val GUEST_LOG = Regex("""[A-Za-z0-9._-]+\.log""")
        private val GUEST_STATE = Regex("""dextop-[a-z0-9-]+\.conf""")
        private const val BUFFER = 256 * 1024
    }

    @Volatile private var closed = false
    @Volatile private var proot: LinuxProot? = null
    @Volatile private var audioClientUid = -1
    @Volatile private var deviceListener: android.net.LocalServerSocket? = null
    @Synchronized override fun startDeviceBridgeListener(name: String) {
        check(android.os.Binder.getCallingUid() == audioClientUid) { moe.n4tsu.dextop.localText("uiDeviceCallerIsNotTheConnectedApplication") }
        check(!closed) { moe.n4tsu.dextop.localText("uiLinuxServiceIsClosed") }
        require(name.matches(Regex("dextop-devices-[a-f0-9]{32}")))
        val listener = android.net.LocalServerSocket(name)
        deviceListener?.close()
        deviceListener = listener
    }
    override fun acceptDeviceBridge(): android.os.ParcelFileDescriptor {
        check(android.os.Binder.getCallingUid() == audioClientUid) { moe.n4tsu.dextop.localText("uiDeviceCallerIsNotTheConnectedApplication") }
        val listener = checkNotNull(deviceListener) { moe.n4tsu.dextop.localText("uiDeviceListenerIsNotRunning") }
        while (!closed && deviceListener === listener) {
            listener.accept().use { socket ->
                val peer = socket.peerCredentials
                if (peer.uid == audioClientUid || (peer.uid == Os.getuid() && ownsProcess(peer.pid))) {
                    return android.os.ParcelFileDescriptor.dup(socket.fileDescriptor)
                }
            }
        }
        error(moe.n4tsu.dextop.localText("uiDeviceListenerWasReplaced"))
    }
    override fun openMicrophoneStream(containerId: String): android.os.ParcelFileDescriptor {
        check(android.os.Binder.getCallingUid() == audioClientUid) { moe.n4tsu.dextop.localText("uiAudioCallerIsNotTheConnectedApplication") }
        return checkNotNull(audio) { moe.n4tsu.dextop.localText("uiAudioUnavailable") }.openMicrophone(containerId)
    }
    override fun microphoneWanted(containerId: String): Boolean {
        check(android.os.Binder.getCallingUid() == audioClientUid) { moe.n4tsu.dextop.localText("uiAudioCallerIsNotTheConnectedApplication") }
        return audio?.microphoneWanted(containerId) == true
    }
    override fun openAudioStream(containerId: String): android.os.ParcelFileDescriptor {
        check(android.os.Binder.getCallingUid() == audioClientUid) { moe.n4tsu.dextop.localText("uiAudioCallerIsNotTheConnectedApplication") }
        return checkNotNull(audio) { moe.n4tsu.dextop.localText("uiAudioUnavailable") }.openStream(containerId)
    }
    @Volatile private var callback: ILinuxContainerCallback? = null
    private val installer = Executors.newSingleThreadExecutor { Thread(it, "DextopLinuxInstall") }
    private val sizeWorker = Executors.newSingleThreadExecutor { Thread(it, "DextopLinuxSize").apply { isDaemon = true } }
    private val containerSizes = LinuxContainerSizes(sizeWorker, { id ->
        val directory = LinuxPaths.container(id)
        check(directory.isDirectory)
        val process = ProcessBuilder("/system/bin/du", "-sk", directory.absolutePath)
            .redirectError(File("/dev/null")).start()
        try {
            check(process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) { moe.n4tsu.dextop.localText("uiContainerSizeMeasurementTimedOut") }
            // Bound-device placeholders can be unreadable. du still prints the accessible
            // container's total with exit 1, as finish() has always used; other failures fail.
            check(process.exitValue() in 0..1)
            Math.multiplyExact(process.inputStream.bufferedReader().use { it.readText() }.trim().substringBefore('\t').substringBefore(' ').toLong(), 1024L)
        } finally { if (process.isAlive) process.destroyForcibly() }
    }, { if (!closed) notifyChanged() },
        restore = { id, fallback -> LinuxContainerSizeSnapshot.read(LinuxPaths.container(id), fallback) },
        persist = { id, bytes -> LinuxContainerSizeSnapshot.write(LinuxPaths.container(id), bytes) })
    private val cancelled = ConcurrentHashMap.newKeySet<String>()
    private val activeProcesses = ConcurrentHashMap<String, Process>()

    private val integrations by lazy { LinuxIntegration(
        { requireProot() },
        { id -> LinuxCatalog.find(readConfig(id)?.optString("distro").orEmpty())?.family ?: error(moe.n4tsu.dextop.localText("uiUnknownDistribution")) },
        { id -> startDesktop(id, 1280, 720, 160) },
        { id, message -> log(id, message) },
        { notifyChanged() },
    ) }
    private fun integrationCaller(id: String) {
        check(android.os.Binder.getCallingUid() == audioClientUid) { moe.n4tsu.dextop.localText("uiIntegrationCallerIsNotTheConnectedApplication") }
        check(readConfig(id)?.optString("state") == "installed") { moe.n4tsu.dextop.localText("uiLinuxEnvironmentIsNotInstalled") }
    }
    override fun integrationStatus(containerId: String): String {
        integrationCaller(containerId)
        return integrations.status(containerId)
    }
    override fun integrationAction(containerId: String, json: String): String {
        integrationCaller(containerId)
        val result = integrations.action(containerId, json)
        val request = JSONObject(json)
        if (request.optString("feature") == "ssh" && request.optString("op") == "start") {
            val family = readConfig(containerId)?.optString("distro")?.let(LinuxCatalog::find)?.family
            audio?.ensure(containerId, family)
        }
        return result
    }
    override fun openIntegrationPrint(containerId: String, job: String): android.os.ParcelFileDescriptor {
        integrationCaller(containerId)
        return integrations.openPrint(containerId, job)
    }
    override fun filterIntegrationPrint(containerId: String, job: String, ranges: String): android.os.ParcelFileDescriptor {
        integrationCaller(containerId)
        return integrations.filterPrint(containerId, job, ranges)
    }

    override fun protocolVersion(): Int = PROTOCOL_VERSION

    override fun containerSharing(containerId: String): String = LinuxSharing.read(containerId).toString()

    override fun setContainerSharing(containerId: String, policy: String) {
        check(readConfig(containerId) != null)
        val next = LinuxSharing.normalize(JSONObject(policy))
        val previous = LinuxSharing.read(containerId)
        // Existing PRoot mounts and already-open descriptors cannot be withdrawn in place.
        if (previous.optBoolean("wholeStorage") != next.optBoolean("wholeStorage")) {
            resetSession(containerId, true)
        }
        LinuxSharing.write(containerId, next)
        notifyChanged()
    }

    override fun processContainer(pid: Int): String =
        LinuxRuntime.containerOf(pid).orEmpty()

    override fun ownsProcess(pid: Int): Boolean {
        val self = android.os.Process.myPid()
        var current = pid
        repeat(256) {
            if (current == self) return true
            if (current <= 1) return false
            val status = runCatching { java.io.File("/proc/$current/status").readText() }.getOrNull() ?: return false
            current = Regex("(?m)^PPid:\\s+(\\d+)").find(status)?.groupValues?.get(1)?.toIntOrNull() ?: return false
        }
        return false
    }

    override fun resources(): String =
        LinuxResources.status(graphics, containerIds().mapNotNull(::readConfig)).toString()

    override fun stopProcess(pid: Int, start: String): Boolean = LinuxResources.stop(pid, start)

    override fun storeState(containerId: String): String {
        val root = LinuxPaths.rootfs(containerId)
        val installed = LinkedHashSet<String>()
        // dpkg's own database: a paragraph per package, "Status: install ok installed" when installed.
        File(root, "var/lib/dpkg/status").takeIf { it.isFile }?.bufferedReader()?.use { reader ->
            var name: String? = null
            var ok = false
            fun end() { name?.takeIf { ok }?.let(installed::add); name = null; ok = false }
            reader.forEachLine { line ->
                when {
                    line.isEmpty() -> end()
                    line.startsWith("Package: ") -> name = line.substring(9).trim()
                    line.startsWith("Status: ") -> ok = line.trim() == "Status: install ok installed"
                }
            }
            end()
        }
        val safe = Regex("[A-Za-z0-9][A-Za-z0-9._+-]{0,127}")
        val external = ArrayList<String>()
        // deb-get installs recorded by dextop-store, still installed in dpkg.
        File(root, "etc/dextop-deb-get/installed").takeIf { it.isFile }?.readLines()?.forEach { line ->
            val name = line.trim().split(Regex("\\s+")).firstOrNull().orEmpty()
            if (name.matches(safe) && name in installed) external += "deb-get:$name"
        }
        // Flatpak apps of the user installation that came from Flathub.
        val flatpak = File(root, LinuxUser.home(root).removePrefix("/") + "/.local/share/flatpak")
        File(flatpak, "app").listFiles()?.sortedBy { it.name }?.forEach { app ->
            if (app.name.matches(safe) && File(app, "current").exists() &&
                File(flatpak, "repo/refs/remotes/flathub/app/${app.name}").exists()) external += "flathub:${app.name}"
        }
        val flathub = File(root, "usr/bin/flatpak").exists() &&
            File(flatpak, "repo/config").takeIf { it.isFile }?.readText()?.contains("[remote \"flathub\"]") == true
        return JSONObject()
            .put("installed", org.json.JSONArray(installed))
            .put("external", org.json.JSONArray(external))
            .put("ready", JSONObject().put("deb-get", File(root, "opt/dextop/deb-get/revision").isFile).put("flathub", flathub))
            .toString()
    }

    override fun uninstallLauncher(packageName: String) {
        if (androidContext != null) throw SecurityException(moe.n4tsu.dextop.localText("uiPackageRemovalBelongsToTheExplicitPrivilegeBroker"))
        require(packageName.matches(Regex(Regex.escape(LinuxShortcutInstaller.PACKAGE_PREFIX) + "a[0-9a-f]{20}")))
        val process = ProcessBuilder("/system/bin/pm", "uninstall", "--user", "current", packageName).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText().trim()
        check(process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS) && process.exitValue() == 0) { moe.n4tsu.dextop.InlineStrings.format(moe.n4tsu.dextop.localText("uiLauncherRemovalFailed0"), output) }
    }

    override fun installLauncher(apk: android.os.ParcelFileDescriptor, size: Long, installerPackage: String): String {
        if (androidContext != null) {
            apk.close()
            throw SecurityException(moe.n4tsu.dextop.localText("uiSilentPackageInstallationBelongsToTheExplicitPrivilegeBroker"))
        }
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
                process.outputStream.use { stream -> check(input.copyTo(stream) == size) { moe.n4tsu.dextop.localText("uiIncompleteLauncherAPK") } }
            }
            check(process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS)) { moe.n4tsu.dextop.localText("uiLauncherInstallTimedOut") }
            reader.join(2_000)
            val message = synchronized(output) { output.toString().trim() }
            check(process.exitValue() == 0 && message.lineSequence().any { it.trim() == "Success" }) {
                moe.n4tsu.dextop.InlineStrings.format(moe.n4tsu.dextop.localText("uiLauncherInstallFailed0"), message)
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
                (LinuxSettings.customResolver() ?: resolver)?.let { dns -> LinuxNetworkLink.stageResolver(LinuxPaths.rootfs(it), dns) }
            }
    }

    @Synchronized
    override fun initialize(nativeLibraryDir: String, callback: ILinuxContainerCallback?) {
        check(!closed) { moe.n4tsu.dextop.localText("uiLinuxRuntimeIsClosedReconnect") }
        val caller = android.os.Binder.getCallingUid()
        // Wayland clients name the compositor after the process serving their socket (fastfetch's WM).
        runCatching {
            android.os.Process::class.java.getMethod("setArgV0", String::class.java).invoke(null, LinuxAppSession.WM_NAME)
        }
        check(audioClientUid == -1 || audioClientUid == caller) { moe.n4tsu.dextop.localText("uiLinuxServiceAlreadyBelongsToAnotherApplication") }
        audioClientUid = caller
        if (androidContext != null) {
            LinuxSysStandIns.context = androidContext
            check(caller == Os.getuid() && Os.getuid() !in setOf(0, 2000))
            require(nativeLibraryDir == androidContext.applicationInfo.nativeLibraryDir)
        } else LinuxServiceHost.initializeFonts()
        // Rebinding the app must retain the running servers and their audio ports.
        // APK changes are handled by the client's code-path/version check.
        this.callback = callback
        deliverAccountPasswords()
        if (proot != null) return
        val renderer = LinuxGraphics(nativeLibraryDir)
        graphics = renderer
        val sound = LinuxAudio({ requireProot() }, { id, line -> log(id, line) }) { notifyChanged() }
        audio = sound
        proot = LinuxProot(nativeLibraryDir).apply {
            androidMode = androidContext != null
            guestEnvironment = { id -> renderer.environment(id) + sound.environment(id) }
        }
        this.callback = callback
        LinuxPaths.containers.mkdirs()
        // Everything Linux keeps (containers, snapshots, stand-ins, cookies) stays its owner's:
        // closed at the top, the world-writable modes some tools leave inside reach nobody else.
        runCatching { Os.chmod(LinuxPaths.base.path, "700".toInt(8)) }
            .onFailure { Log.w(TAG, "Cannot restrict ${LinuxPaths.base}: ${it.message}") }
        // An install interrupted by a reboot or a killed service never completes.
        containerIds().forEach { id ->
            val config = readConfig(id) ?: return@forEach
            if (config.optString("state") == "installed") runCatching {
                LinuxSessionCompatibility.install(LinuxPaths.rootfs(id), nativeLibraryDir, LinuxProot.socketNamespace(id),
                    xvfb = true)
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
        if (androidContext == null) bluetoothBridge = LinuxBluetoothBridge {
            containerIds().filter { readConfig(it)?.optString("state") == "installed" }.map(LinuxPaths::rootfs)
        }.also { it.start() }
    }

    override fun listContainers(): String = JSONArray().apply {
        containerIds().mapNotNull(::readConfig).sortedBy { it.optLong("created") }.forEach { config ->
            val id = config.optString("id")
            if (config.optString("state") == "installed") config.put("bytes", containerSizes.read(id, config.optLong("bytes", -1L)))
            put(config.put("running", sessions[id]?.alive == true)
                .put("appsRunning", appSessions[id]?.alive == true)
                .put("audioGeneration", audio?.generation(id) ?: 0)
                .put("integrationsRunning", integrations.running(id))
                .put("runtimeState", when {
                    runtimeStates[id] == "failed" -> "failed"
                    sessions[id]?.alive == true && sessions[id]?.windowManagerReady != true -> "starting"
                    sessions[id]?.alive == true || appSessions[id]?.alive == true || integrations.running(id) -> "running"
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

    override fun refreshContainerSize(containerId: String, force: Boolean) {
        val config = readConfig(containerId) ?: return
        if (config.optString("state") == "installed") containerSizes.request(containerId, config.optLong("bytes", -1L), force)
    }

    override fun catalog(): String = LinuxCatalog.toJson()

    override fun install(name: String, distroId: String): String = installWithOptions(name, distroId, "{}")

    override fun installWithOptions(name: String, distroId: String, optionsJson: String): String {
        val distro = LinuxCatalog.find(distroId) ?: throw IllegalArgumentException(moe.n4tsu.dextop.InlineStrings.format(moe.n4tsu.dextop.localText("uiUnknownDistribution0"), distroId))
        val requested = JSONObject(optionsJson)
        val (_, password) = LinuxUser.credentials(requested)
        val passwordHash = LinuxPasswordHash.create(password.toByteArray(Charsets.UTF_8))
        requested.remove("accountPassword")
        val options = LinuxSetupOptions.fromJson(requested).validate(distro)
        val id = "c" + UUID.randomUUID().toString().replace("-", "").take(12)
        LinuxPaths.container(id).mkdirs()
        writeConfig(id, JSONObject()
            .put("id", id)
            .put("name", name.ifBlank { distro.displayName })
            .put("distro", distro.id)
            .put("setupOptions", options.toJson())
            .put("state", "installing")
            .put("created", System.currentTimeMillis()))
        pendingAccountHashes[id] = passwordHash
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
        containerSizes.forget(containerId)
        cancelInstall(containerId)
        stopAccountPasswordListener(containerId)
        integrations.stop(containerId)
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
        return applySetup(containerId, config.optJSONObject("setupOptions")?.toString() ?: "{}", repair = true)
    }

    override fun setup(containerId: String, optionsJson: String): Boolean = applySetup(containerId, optionsJson, repair = false)

    /** [repair] reinstalls what the image already held; adding features only runs what they need. */
    @Synchronized
    private fun applySetup(containerId: String, optionsJson: String, repair: Boolean): Boolean {
        val config = readConfig(containerId) ?: return false
        if (config.optString("state") in setOf("installing", "updating")) return false
        val distro = LinuxCatalog.find(config.optString("distro")) ?: return false
        val requested = JSONObject(optionsJson)
        val previousName = config.optJSONObject("setupOptions")?.optString("username", "").orEmpty()
        if (previousName.isNotEmpty()) {
            require(!requested.has("username") || requested.optString("username") == previousName) { moe.n4tsu.dextop.localText("uiChangingAnExistingLinuxAccountIsNotSupported") }
            requested.put("username", previousName)
        }
        val options = LinuxSetupOptions.fromJson(requested).validate(distro)
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
            val ok = runCatching { configure(containerId, distro, repair) }.onFailure { fail(containerId, it) }.isSuccess
            if (ok) finish(containerId)
        }
        return true
    }

    override fun runCommand(containerId: String, command: String, timeoutSeconds: Int): String {
        val runner = requireProot()
        check(LinuxPaths.rootfs(containerId).isDirectory) { moe.n4tsu.dextop.InlineStrings.format(moe.n4tsu.dextop.localText("uiContainer0IsNotInstalled"), containerId) }
        // Store jobs: an Android-mode container set up before these steps gets DNS and apt fixed first.
        prepareAndroid(containerId)
        val result = runner.run(runner.guest(containerId, command, fakeRoot = true, extraEnvironment = listOf("HOME=${LinuxUser.home(containerId)}")), timeoutSeconds.toLong().coerceIn(1, 3_600))
        return JSONObject().put("exitCode", result.exitCode).put("output", result.output).toString()
    }

    override fun installMacosRuntime(containerId: String): String = synchronized(appLifecycleLocks.getOrPut(containerId) { Any() }) {
        val approval = checkNotNull(LinuxMacosRuntimeBundle.approval()) { moe.n4tsu.dextop.localText("uiThisAPKHasNoApprovedMacOSRuntimeBundle") }
        val runner = requireProot()
        val root = LinuxPaths.rootfs(containerId)
        check(root.isDirectory) { moe.n4tsu.dextop.localText("uiLinuxContainerIsNotInstalled") }
        val base = File(LinuxPaths.container(containerId), "runtime").apply { check(isDirectory || mkdirs()) }
        val temporary = java.nio.file.Files.createTempDirectory(base.toPath(), "macos-install-").toFile()
        val archive = File(temporary, "runtime.tar.gz")
        try {
            LinuxMacosRuntimeBundle.copyArchive(archive, approval)
            prepareAndroid(containerId)
            LinuxSessionCompatibility.stage(root, runner.nativeLibraryDir, LinuxProot.socketNamespace(containerId))
            val preparation = runner.run(runner.guest(containerId, LinuxMacosRuntimeBundle.PREPARE, fakeRoot = true), 600)
            check(preparation.exitCode == 0) { preparation.output.takeLast(4000).ifBlank { moe.n4tsu.dextop.localText("uiMacOSRuntimePreparationFailed") } }
            val result = runner.run(runner.guest(containerId, LinuxMacosRuntimeBundle.command(approval),
                extraBinds = listOf("${archive.absolutePath}:/run/dextop-macos-runtime.tar.gz")), 600)
            check(result.exitCode == 0) { result.output.takeLast(4000).ifBlank { moe.n4tsu.dextop.localText("uiMacOSRuntimeInstallationFailed") } }
            val json = result.output.lineSequence().lastOrNull { it.trimStart().startsWith('{') }
                ?: error(moe.n4tsu.dextop.localText("uiMacOSInstallationDidNotReturnAResult"))
            JSONObject(json).toString()
        } finally { archive.delete(); temporary.delete() }
    }

    override fun openTerminal(containerId: String, rows: Int, cols: Int): android.os.ParcelFileDescriptor {
        val runner = requireProot()
        check(LinuxPaths.rootfs(containerId).isDirectory) { moe.n4tsu.dextop.InlineStrings.format(moe.n4tsu.dextop.localText("uiContainer0IsNotInstalled"), containerId) }
        integrations.restore(containerId)
        val family = readConfig(containerId)?.optString("distro")?.let(LinuxCatalog::find)?.family
        audio?.ensure(containerId, family)
        val builder = runner.guest(containerId,
            "cd \"${'$'}HOME\" 2>/dev/null; if command -v bash >/dev/null 2>&1; then exec bash -l; else exec sh -l; fi",
            fakeRoot = LinuxUser.name(LinuxPaths.rootfs(containerId)) == null)
        val pid = IntArray(1)
        val master = LinuxPty.nativeSpawn(builder.command().toTypedArray(),
            builder.environment().map { (name, value) -> "$name=$value" }.toTypedArray(),
            "/", rows.coerceIn(2, 500), cols.coerceIn(2, 1000), pid)
        check(master >= 0) { moe.n4tsu.dextop.InlineStrings.format(moe.n4tsu.dextop.localText("uiCouldNotOpenATerminalErrno0"), -master) }
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
        check(readConfig(containerId)?.optString("state") !in setOf("installing", "updating")) { moe.n4tsu.dextop.localText("uiSetupIsRunningCancelItFirst") }
        runtimeGenerations.merge(containerId, 1, Int::plus)
        integrations.stop(containerId)
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
            if (error is LinuxWaylandUnavailable) useXvfb(id)
            val message = "${error.message ?: error.javaClass.simpleName}\n${runtimeLogs(id)}"
            runtimeFailure(id, message)
            throw IllegalStateException(message.take(24000), error)
        }
    }

    /** Wayland is not selectable, so a device or session that cannot run it needs this way out. */
    private fun useXvfb(id: String) {
        val config = readConfig(id) ?: return
        val options = config.optJSONObject("setupOptions") ?: return
        if (options.optString("displayBackend") == "xvfb") return
        writeConfig(id, config.put("setupOptions", options.put("displayBackend", "xvfb")))
        runCatching { File(LinuxPaths.rootfs(id), "etc/dextop/display-backend").writeText("xvfb\n") }
        log(id, "Wayland is unavailable; this container now uses Xvfb")
        notifyChanged()
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
        check(readConfig(containerId)?.optString("state") == "installed") { moe.n4tsu.dextop.InlineStrings.format(moe.n4tsu.dextop.localText("uiContainer0IsNotInstalled"), containerId) }
        return withDisplay { display ->
        startRenderer(containerId)
        val keyring = takeKeyring(containerId)
        val session = try { runtimeStart(containerId) { LinuxDesktopSession.start(
            requireProot(), containerId, display,
            width.coerceIn(320, 7680), height.coerceIn(240, 4320), dpi.coerceIn(72, 480),
            xwayland = readConfig(containerId)?.optJSONObject("setupOptions")?.optString("displayBackend") == "wayland",
            keyring = keyring,
        )
        }
        } finally { keyring?.fill(0) }
        sessions[containerId] = session
        session.onClipboard = { text -> shareClipboard(containerId, text) }
        integrations.restore(containerId)
        startNotifications(containerId, display)
        monitor(containerId, { session.alive }) { sessions[containerId] === session }
        notifyChanged()
        display
        }
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
    private val appLifecycleLocks = ConcurrentHashMap<String, Any>()
    private val macosLifecycle = LinuxMacosLifecycle()

    private fun appTarget(target: String): LinuxAppSession? =
        if (target.endsWith("#apps")) appSessions[target.removeSuffix("#apps")] else null

    private val displayReservations = LinuxDisplayReservations()

    private inline fun <T> withDisplay(work: (Int) -> T): T {
        val display = displayReservations.reserve(usedDisplays())
        try { return work(display) }
        finally {
            if (display !in usedDisplays()) LinuxXAuth.forget(display)
            displayReservations.release(display)
        }
    }

    private fun usedDisplays(): Set<Int> =
        sessions.values.map { it.display }.toSet() + appSessions.values.map { it.display }

    override fun guestLog(containerId: String, name: String, maxBytes: Int, truncate: Boolean): String {
        require(GUEST_LOG.matches(name)) { moe.n4tsu.dextop.InlineStrings.format(moe.n4tsu.dextop.localText("uiInvalidGuestLog0"), name) }
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
        require(GUEST_STATE.matches(name) && content.length <= 16 * 1024) { moe.n4tsu.dextop.InlineStrings.format(moe.n4tsu.dextop.localText("uiInvalidGuestState0"), name) }
        val directory = File(LinuxPaths.rootfs(containerId), "tmp")
        check(directory.isDirectory) { moe.n4tsu.dextop.InlineStrings.format(moe.n4tsu.dextop.localText("uiContainer0IsNotInstalled"), containerId) }
        // Readers see either the old or the new file, never a partial one.
        val staged = File(directory, ".$name.staged")
        staged.writeText(content)
        check(staged.renameTo(File(directory, name))) { moe.n4tsu.dextop.InlineStrings.format(moe.n4tsu.dextop.localText("uiCannotWriteGuestState0"), name) }
        // Security keys in it get sysfs entries, for udev to find their hidraw nodes.
        if (name == "dextop-devices.conf") runCatching { LinuxHidSysfs.update(containerId, content) }
            .onFailure { log(containerId, "Security key entries failed: ${it.message}") }
        // USB storage in it gets block-device sysfs entries and /proc/partitions lines.
        if (name == "dextop-devices.conf") runCatching { LinuxDiskSysfs.update(containerId, content) }
            .onFailure { log(containerId, "USB storage entries failed: ${it.message}") }
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
        check(readConfig(containerId)?.optString("state") == "installed") { moe.n4tsu.dextop.InlineStrings.format(moe.n4tsu.dextop.localText("uiContainer0IsNotInstalled"), containerId) }
        return withDisplay { display ->
        startRenderer(containerId)
        val selectedWayland = readConfig(containerId)?.optJSONObject("setupOptions")?.optString("displayBackend") == "wayland"
        val keyring = takeKeyring(containerId)
        val session = try { runtimeStart(containerId) { LinuxAppSession.start(
            requireProot(), containerId, display,
            width.coerceIn(320, 7680), height.coerceIn(240, 4320), dpi.coerceIn(72, 640), requireWayland = selectedWayland, xwayland = selectedWayland,
            keyring = keyring,
        ) { event ->
            if (event.optString("type") == "tray-wine-metadata") {
                Thread({ runCatching { wineTrayMetadata.ensure(containerId, event.optInt("pid"), requireProot()) }
                    .onFailure { log(containerId, "Wine tray metadata unavailable: ${it.message}") } }, "DextopWineTraySetup").apply { isDaemon = true; start() }
            } else callback(containerId) { it.onWindowEvent(containerId, event.toString()) }
        }
        }
        } finally { keyring?.fill(0) }
        appSessions[containerId] = session
        // A new session tries the GPU again.
        gpuSessionOff.remove(containerId)
        gpuCrashes.remove(containerId)
        session.onClipboard = { text -> shareClipboard(containerId, text) }
        integrations.restore(containerId)
        startNotifications(containerId, display)
        monitor(containerId, { session.alive }) { appSessions[containerId] === session }
        notifyChanged()
        runCatching { maybeProbeGpu(containerId) }
        display
        }
    }

    override fun launchApp(containerId: String, desktopId: String) = launchApp(containerId, desktopId, software = false)
    override fun launchFramedApp(containerId: String, desktopId: String) =
        launchApp(containerId, desktopId, software = false, framed = true)

    /**
     * Starts [desktopId]. One that crashes right away in the GPU driver (SIGSEGV or SIGABRT
     * within [GPU_CRASH_WINDOW_MS]) is started once more with software rendering, so an
     * application the GPU path cannot run still opens.
     */
    private fun launchApp(containerId: String, desktopId: String, software: Boolean, x11: Boolean = false, framed: Boolean = false) {
        synchronized(appLifecycleLocks.getOrPut(containerId) { Any() }) {
            launchAppLocked(containerId, desktopId, software, x11, framed)
        }
    }

    private fun launchAppLocked(containerId: String, desktopId: String, software: Boolean, x11: Boolean, framed: Boolean) {
        val session = appSessions[containerId]?.takeIf { it.alive } ?: error(moe.n4tsu.dextop.localText("uiApplicationDisplayIsNotRunning"))
        val entry = LinuxDesktopEntries.find(LinuxPaths.rootfs(containerId), desktopId) ?: error(moe.n4tsu.dextop.InlineStrings.format(moe.n4tsu.dextop.localText("uiUnknownApplication0"), desktopId))
        if (session.restoreHiddenApplication(entry.startupWMClass)) {
            log(containerId, "Restored hidden application window: ${entry.id}")
            return
        }
        // Steam mode sizes its nested output from the session's screen.
        val sized = entry.id == LinuxSteam.STEAM_MODE_ENTRY
        val flatpak = if (entry.flatpakId.isNotEmpty() && LinuxSettings.read().optBoolean("flatpakCompatibility")) {
            log(containerId, "Flatpak compatibility mode: no Flatpak sandbox or portal permission enforcement")
            LinuxFlatpak.plan(LinuxPaths.rootfs(containerId), entry.flatpakId)
        } else null
        // The application's own choices (its app menu â†’ How it runs).
        val mode = LinuxSettings.appMode(containerId, entry.id)
        // ~/.config/dextop/gpu-apps ("software NAME" / "gpu NAME") wins over Dextop's own fallback.
        val listed = LinuxGpuApps.choice(LinuxPaths.rootfs(containerId), entry.id, LinuxDesktopEntries.command(entry))
        val softwareRendering = software || mode.optString("renderer") == "software" || listed == "software" ||
            (listed != "gpu" && containerId in gpuSessionOff)
        val glOverride = if (mode.optString("gl") == "override" && !softwareRendering) {
            if (LinuxSettings.gpuMode(containerId) == LinuxGpuMode.TURNIP) "MESA_GL_VERSION_OVERRIDE=4.6 MESA_GLSL_VERSION_OVERRIDE=460 "
            else "MESA_GL_VERSION_OVERRIDE=4.3 MESA_GLSL_VERSION_OVERRIDE=430 "
        } else ""
        val gpuMode = LinuxSettings.gpuMode(containerId)
        val waylandGpu = gpuMode == LinuxGpuMode.TURNIP && graphics?.turnipWaylandInstalled(containerId) == true
        // A GPU window (gamescope): the X11 program's frames stay on the GPU. An X11 retry leaves it.
        val gpuWindow = mode.optString("display") == LinuxGpuWindow.MODE && !x11 && !softwareRendering
        if (gpuWindow) LinuxGpuWindow.prepare(LinuxPaths.rootfs(containerId), waylandGpu, session.wayland?.running == true)
        val program = (if (softwareRendering) "env DEXTOP_SOFTWARE_RENDERING=1 LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe "
            else if (glOverride.isNotEmpty()) "env $glOverride" else "") +
            (if (sized) "DEXTOP_WIDTH=${session.screenWidth} DEXTOP_HEIGHT=${session.screenHeight} " else "") +
            (flatpak?.command ?: LinuxDesktopEntries.command(entry))
        val command = if (gpuWindow) LinuxGpuWindow.command(minOf(session.screenWidth, 1920).takeIf { it > 0 } ?: 1280,
            minOf(session.screenHeight, 1080).takeIf { it > 0 } ?: 720, program) else program
        val started = android.os.SystemClock.elapsedRealtime()
        callback(containerId) {
            it.onWindowEvent(containerId, JSONObject().put("type", "launch-start").put("desktop", entry.id)
                .put("startupWMClass", entry.startupWMClass).toString())
        }
        log(containerId, "launch ${entry.id}: $command")
        val backend = readConfig(containerId)?.optJSONObject("setupOptions")?.optString("displayBackend", "") ?: ""
        if (backend == "wayland" && session.wayland?.running != true) {
            useXvfb(containerId)
            throw LinuxWaylandUnavailable()
        }
        // Turnip's X11 package does not include the Wayland EGL/WSI driver.
        // Its distro EGL can still create windows and submit empty buffers, so
        // process/connection success cannot trigger the existing failure retry.
        // Keep GPU rendering through Xwayland until the Wayland driver is installed.
        val toolkitAvailable = gpuMode != LinuxGpuMode.TURNIP || waylandGpu
        val display = mode.optString("display")
        val wayland = gpuWindow || !x11 && display != "x11" && session.wayland?.running == true && (display == "wayland" ||
            entry.displayBackend != "x11" && toolkitAvailable && (backend == "wayland" || backend.isEmpty() && waylandGpu))
        if (!toolkitAvailable && !x11) log(containerId, "${entry.name}: using X11 + Zink; native Wayland GPU driver is not installed")
        val appLog = "app-${entry.id.replace(Regex("[^A-Za-z0-9._-]"), "_")}.log"
        val macos = if (entry.runtime == "macos") macosLifecycle.prepare(File(LinuxPaths.container(containerId), "runtime/macos-control")) else null
        val process = try { requireProot().guest(containerId, LinuxCatalog.launchScript(session.display, command, wayland = wayland,
            waylandGpu = waylandGpu, appLog = appLog, androidWindows = !framed, uiScale = LinuxSettings.uiScale(containerId, entry.id)),
            extraBinds = flatpak?.binds.orEmpty() + macos?.binds.orEmpty(),
            extraEnvironment = flatpak?.environment.orEmpty() + macos?.environment.orEmpty())
            .redirectErrorStream(true).start()
        } catch (error: Exception) { macos?.cleanup(); throw error }
        if (macos != null) macosLifecycle.attach(process, macos)
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
                val current = appProcesses[containerId]?.contains(process) == true && macos?.stop?.isFile != true
                val chromiumGpuFailure = LinuxLaunchFailure.chromiumGpu(code, output)
                val gpuCrash = LinuxLaunchFailure.gpu(code, output) &&
                    android.os.SystemClock.elapsedRealtime() - started < GPU_CRASH_WINDOW_MS
                val displayFailure = code != 0 && android.os.SystemClock.elapsedRealtime() - started < GPU_CRASH_WINDOW_MS &&
                    listOf("could not connect to display", "failed to connect to wayland", "could not load the qt platform plugin")
                        .any { output.contains(it, ignoreCase = true) }
                if (current && wayland && !chromiumGpuFailure && (gpuCrash || displayFailure)) {
                    log(containerId, "${entry.name} failed on Wayland (exit $code); retrying on X11 with GPU rendering preserved")
                    status("launch-status") { put("line", "Wayland startup failed; retrying on X11") }
                    runCatching { launchApp(containerId, desktopId, software = software, x11 = true, framed = framed) }
                        .onFailure { log(containerId, "X11 retry of ${entry.id} failed: ${it.message}") }
                    return
                }
                if (current && gpuCrash && !software && !softwareRendering && gpuMode != LinuxGpuMode.SOFTWARE) {
                    log(containerId, "${entry.name}: GPU startup failed (exit $code); preserving the selected hardware renderer")
                    status("launch-status") { put("line", "GPU startup failed (exit $code). See application diagnostics.") }
                    if (gpuCrashed(containerId, entry.name)) status("launch-status") {
                        put("line", "GPU turned off for this session after repeated crashes; applications opened from now on draw in software")
                    }
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
            process.waitFor()
            macosLifecycle.finished(process)
        }
            .apply { isDaemon = true; start() }

    }

    override fun stopApps(containerId: String) {
        synchronized(appLifecycleLocks.getOrPut(containerId) { Any() }) { stopAppsLocked(containerId) }
    }

    private fun stopAppsLocked(containerId: String) {
        val processes = appProcesses.remove(containerId)?.let { synchronized(it) { it.toList() } }.orEmpty()
        try {
            macosLifecycle.stop(processes)
        } catch (error: Exception) {
            appProcesses[containerId] = java.util.Collections.synchronizedList(processes.filter { it.isAlive }.toMutableList())
            throw error
        }
        wineTrayMetadata.stop(containerId)
        gpuSessionOff.remove(containerId)
        gpuCrashes.remove(containerId)
        processes.forEach { runCatching { LinuxRuntime.terminate(it) } }
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
        (LinuxSettings.customResolver() ?: networkResolver)?.let { LinuxNetworkLink.stageResolver(LinuxPaths.rootfs(containerId), it) }
        LinuxSessionCompatibility.stage(LinuxPaths.rootfs(containerId), requireProot().nativeLibraryDir, LinuxProot.socketNamespace(containerId))
        listenForAccountPassword(containerId)
        val distro = readConfig(containerId)?.optString("distro")?.let(LinuxCatalog::find)
        if (distro?.family == LinuxPackageFamily.APT && LinuxSettings.gpuMode(containerId) == LinuxGpuMode.TURNIP &&
            graphics?.turnipInstalled(containerId) == true) {
            LinuxGpuRuntime.prepare(LinuxPaths.rootfs(containerId)) { script ->
                val result = requireProot().run(requireProot().guest(containerId, script), timeoutSeconds = 60)
                check(result.exitCode == 0) { moe.n4tsu.dextop.InlineStrings.format(moe.n4tsu.dextop.localText("uiGPURuntimeSetupFailed0"), result.output.takeLast(2000)) }
                if (result.output.isNotBlank()) log(containerId, result.output)
            }
        }
        runCatching { LinuxSteam.stage(LinuxPaths.rootfs(containerId)) }
            .onFailure { log(containerId, "Steam launcher setup failed: ${it.message}") }
        val hardware = graphics?.ensure(containerId) { line -> log(containerId, line) } == true
        check(hardware || LinuxSettings.gpuMode(containerId) == LinuxGpuMode.SOFTWARE) {
            moe.n4tsu.dextop.localText("uiTheSelectedGPURendererIsUnavailableRepairTheGraphicsRuntimeOrSelectAnotherHardwareRendererInLinuxSet")
        }
        prepareAndroid(containerId)
        ensureFakeroot(containerId, distro?.family)
        ensureAndroidTools(containerId, distro?.family)
        audio?.ensure(containerId, distro?.family)
    }

    /**
     * Android execution mode: what the shell-UID setup got from the device, an app UID must be given.
     * Images ship /etc/resolv.conf as a link to systemd's resolver, which does not run here, and apt's
     * _apt sandbox user cannot drop privileges under fake root.
     */
    private fun prepareAndroid(id: String) {
        if (androidContext == null) return
        val root = LinuxPaths.rootfs(id)
        val apt = File(root, "etc/apt/apt.conf.d")
        if (apt.isDirectory) {
            val sandbox = File(apt, "00dextop-android")
            val content = "APT::Sandbox::User \"root\";\n"
            // Every Store command passes here; write only when it is missing or differs.
            if (sandbox.length() != content.length.toLong() || runCatching { sandbox.readText() }.getOrNull() != content) sandbox.writeText(content)
        }
        val resolv = File(root, "etc/resolv.conf")
        if (java.nio.file.Files.isSymbolicLink(resolv.toPath()) && !resolv.exists()) resolv.delete()
        if (!resolv.isFile || resolv.readText().lines().none { it.startsWith("nameserver") }) {
            LinuxNetworkLink.stageResolver(root, LinuxSettings.customResolver() ?: networkResolver ?: androidResolver())
        }
    }

    private fun androidResolver(): String {
        val servers = runCatching {
            val manager = androidContext!!.getSystemService(android.net.ConnectivityManager::class.java)
            manager.getLinkProperties(manager.activeNetwork)?.dnsServers.orEmpty().mapNotNull { it.hostAddress }
        }.getOrDefault(emptyList()).ifEmpty { listOf("1.1.1.1", "8.8.8.8") }
        return servers.joinToString("") { "nameserver ${it.substringBefore('%')}\n" }
    }

    /** xz for tar -J (the Store and Steam), missing from minimal images set up before it was provisioned. */
    private fun ensureAndroidTools(containerId: String, family: LinuxPackageFamily?) {
        if (java.nio.file.Files.exists(File(LinuxPaths.rootfs(containerId), "usr/bin/xz").toPath(),
                java.nio.file.LinkOption.NOFOLLOW_LINKS)) return
        val install = when (family ?: LinuxPackageFamily.APT) {
            LinuxPackageFamily.APT -> "export DEBIAN_FRONTEND=noninteractive\n" +
                "apt-get install -y --no-install-recommends xz-utils || " +
                "{ apt-get update && apt-get install -y --no-install-recommends xz-utils; }\n"
            LinuxPackageFamily.PACMAN -> "pacman -S --noconfirm --needed xz\n"
        }
        val runner = proot ?: return
        runCatching { runner.run(runner.guest(containerId, install, fakeRoot = true), 600) { line -> log(containerId, line) } }
    }

    /** Containers set up before real-uid sessions lack fakeroot, which sessions use for apt and sudo. */
    private fun ensureFakeroot(containerId: String, family: LinuxPackageFamily?) {
        // Debian's fakeroot is an alternatives link to an absolute guest path, which File.exists()
        // would follow on Android's side and miss, so apt ran at every session start.
        if (java.nio.file.Files.exists(File(LinuxPaths.rootfs(containerId), "usr/bin/fakeroot").toPath(),
                java.nio.file.LinkOption.NOFOLLOW_LINKS)) return
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

    override fun linuxSettings(): String = (graphics?.status() ?: LinuxSettings.read())
        .put("gpuProbeRunning", gpuProbeRunning).toString()

    // region GPU test
    @Volatile private var gpuProbeRunning = false
    /** Tried once per service process by itself, so a device that cannot finish it is not retried at every start. */
    @Volatile private var gpuProbeAttempted = false

    /** Whether [containerId] can run the device test: installed, with the graphics tools. */
    private fun probeReady(containerId: String) = readConfig(containerId)?.optString("state") == "installed" &&
        File(LinuxPaths.rootfs(containerId), "usr/bin/glxgears").isFile

    /** The device test, by itself, when no renderer was chosen and this device has no current result. */
    private fun maybeProbeGpu(containerId: String) {
        if (gpuProbeAttempted || gpuProbeRunning || LinuxSettings.read().optBoolean("gpuChosen")) return
        val previous = LinuxSettings.probe()
        if (previous != null && previous.optInt("version") >= LinuxGpuProbe.VERSION &&
            previous.optString("device") == android.os.Build.FINGERPRINT) return
        if (!probeReady(containerId)) return
        gpuProbeAttempted = true
        // After the session's own start, so the test does not slow the first application down.
        startGpuProbe(containerId, delayMs = 15_000L)
    }

    /** Runs the device test on [requested] (or the default / first ready container) in the background. */
    private fun startGpuProbe(requested: String?, delayMs: Long = 0L) {
        if (gpuProbeRunning) return
        val containerId = requested?.takeIf(::probeReady)
            ?: LinuxSettings.read().optString("defaultContainer").takeIf { it.isNotEmpty() && probeReady(it) }
            ?: containerIds().firstOrNull(::probeReady)
        if (containerId == null) {
            LinuxSettings.writeProbe(JSONObject().put("version", LinuxGpuProbe.VERSION).put("at", System.currentTimeMillis())
                .put("device", android.os.Build.FINGERPRINT).put("state", "unavailable")
                .put("error", "No installed container has the graphics tools; run container setup with graphics"))
            notifyChanged()
            return
        }
        gpuProbeRunning = true
        notifyChanged()
        Thread({
            runCatching {
                if (delayMs > 0) Thread.sleep(delayMs)
                val result = runGpuProbe(containerId)
                LinuxSettings.writeProbe(result.put("container", containerId).put("device", android.os.Build.FINGERPRINT))
                log(containerId, "GPU test: ${result.optString("state")} ${result.optString("renderer")} " +
                    "gpu=${result.optDouble("gpuFps", -1.0)} cpu=${result.optDouble("cpuFps", -1.0)} fps " +
                    "â†’ ${LinuxSettings.read().optString("gpu")}${result.optString("error").takeIf { it.isNotEmpty() }?.let { " ($it)" }.orEmpty()}")
            }.onFailure { error ->
                log(containerId, "GPU test failed to run: ${error.message}")
                LinuxSettings.writeProbe(JSONObject().put("version", LinuxGpuProbe.VERSION).put("at", System.currentTimeMillis())
                    .put("device", android.os.Build.FINGERPRINT).put("container", containerId)
                    .put("state", "unavailable").put("error", error.message ?: error.javaClass.simpleName))
            }
            gpuProbeRunning = false
            // Running sessions follow the recommendation for applications started from now on.
            (sessions.keys + appSessions.keys).toSet().forEach { id -> runCatching { startRenderer(id) } }
            if (sessions[containerId]?.alive != true && appSessions[containerId]?.alive != true) graphics?.stop(containerId)
            notifyChanged()
        }, "DextopGpuProbe").apply { isDaemon = true; start() }
    }

    private fun runGpuProbe(containerId: String): JSONObject {
        val graphics = checkNotNull(graphics) { moe.n4tsu.dextop.localText("uiLinuxGraphicsAreNotInitialized") }
        val candidate = graphics.candidate(containerId)
            ?: return JSONObject().put("version", LinuxGpuProbe.VERSION).put("at", System.currentTimeMillis())
                .put("state", "unavailable").put("recommended", LinuxGpuMode.SOFTWARE.id)
                .put("error", "This device has no GPU path for Linux applications")
        val proot = requireProot()
        if (candidate == LinuxGpuMode.TURNIP) LinuxGpuRuntime.prepare(LinuxPaths.rootfs(containerId)) { script ->
            val result = proot.run(proot.guest(containerId, script), timeoutSeconds = 60)
            check(result.exitCode == 0) { moe.n4tsu.dextop.InlineStrings.format(moe.n4tsu.dextop.localText("uiGPURuntimeSetupFailed0"), result.output.takeLast(2000)) }
        }
        if (candidate == LinuxGpuMode.HARDWARE) check(graphics.ensureFor(containerId) { line -> log(containerId, line) }) {
            moe.n4tsu.dextop.localText("uiTheVirGLRendererDidNotStart")
        }
        val variables = (graphics.environmentFor(containerId, LinuxGpuMode.TURNIP) +
            graphics.environmentFor(containerId, LinuxGpuMode.HARDWARE)).map { it.substringBefore('=') }
        return withDisplay { display ->
        LinuxXAuth.prepare(containerId, display)
        val script = LinuxGpuProbe.script(display, graphics.environmentFor(containerId, candidate),
            graphics.environmentFor(containerId, LinuxGpuMode.SOFTWARE) + "LIBGL_ALWAYS_SOFTWARE=1", variables)
        val run = proot.run(proot.guest(containerId, script), timeoutSeconds = 120)
        LinuxGpuProbe.parse(candidate, run.output)
        }
    }

    /** GPU crashes at application start by container, for [gpuCrashed]. */
    private val gpuCrashes = ConcurrentHashMap<String, MutableList<Long>>()
    /** Containers whose applications draw in software for the rest of their application session. */
    private val gpuSessionOff = ConcurrentHashMap.newKeySet<String>()

    /** Three GPU crashes within ten minutes turn the GPU off for the rest of the session. */
    private fun gpuCrashed(containerId: String, name: String): Boolean {
        val now = android.os.SystemClock.elapsedRealtime()
        val crashes = gpuCrashes.getOrPut(containerId) { java.util.Collections.synchronizedList(ArrayList()) }
        synchronized(crashes) {
            crashes.removeAll { now - it > GPU_CRASH_PERIOD_MS }
            crashes += now
            if (crashes.size < GPU_CRASH_LIMIT || !gpuSessionOff.add(containerId)) return false
        }
        log(containerId, "GPU turned off for this session: $GPU_CRASH_LIMIT applications crashed on it within 10 minutes (last: $name)")
        return true
    }
    // endregion

    // region Snapshots, backups and AppImages
    /**
     * The one long file job at a time (snapshot, restore, export, import, AppImage), run off the
     * binder thread; [listSnapshots] reports it: {type, container, state, bytes, error, result}.
     */
    @Volatile private var fileJob: JSONObject? = null

    private fun containerBusy(id: String) = sessions[id]?.alive == true || appSessions[id]?.alive == true ||
        integrations.running(id) || readConfig(id)?.optString("state") in setOf("installing", "updating")

    @Synchronized
    private fun startFileJob(type: String, container: String, work: (JSONObject) -> Any?) {
        check(fileJob?.let { synchronized(it) { it.optString("state") } } != "running") { moe.n4tsu.dextop.localText("uiAnotherBackupOrAppImageJobIsRunning") }
        val job = JSONObject().put("type", type).put("container", container).put("state", "running").put("bytes", 0L)
        fileJob = job
        notifyChanged()
        Thread({
            runCatching { work(job) }
                .onSuccess { result -> synchronized(job) { job.put("state", "done"); if (result != null) job.put("result", result) } }
                .onFailure { error ->
                    synchronized(job) { job.put("state", "failed").put("error", error.message ?: error.javaClass.simpleName) }
                    if (container.isNotEmpty()) log(container, "$type failed: ${error.message}")
                }
            notifyChanged()
        }, "DextopLinuxFileJob").apply { isDaemon = true; start() }
    }

    /** Progress of [job], reported a few times a second at most. */
    private fun bytes(job: JSONObject): (Long) -> Unit {
        var reported = 0L
        return { count ->
            synchronized(job) { job.put("bytes", count) }
            val now = android.os.SystemClock.elapsedRealtime()
            if (now - reported > 500) { reported = now; notifyChanged() }
        }
    }

    override fun createSnapshot(containerId: String) {
        check(readConfig(containerId)?.optString("state") == "installed") { moe.n4tsu.dextop.InlineStrings.format(moe.n4tsu.dextop.localText("uiContainer0IsNotInstalled"), containerId) }
        check(!containerBusy(containerId)) { moe.n4tsu.dextop.localText("uiStopTheContainerSSessionsAndApplicationsFirst") }
        startFileJob("snapshot", containerId) { job ->
            val file = LinuxBackups.create(containerId, bytes(job))
            log(containerId, "Snapshot saved: ${file.name} (${file.length() / 1_048_576} MiB)")
            file.name
        }
    }

    override fun listSnapshots(): String = JSONObject()
        .put("snapshots", runCatching { LinuxBackups.list() }.getOrDefault(org.json.JSONArray()))
        .put("job", fileJob?.let { synchronized(it) { JSONObject(it.toString()) } } ?: JSONObject.NULL)
        .toString()

    private fun newContainerId() = "c" + UUID.randomUUID().toString().replace("-", "").take(12)

    /** Unpacks a backup as a new container and sets up what a session needs again. */
    private fun restoreAs(job: JSONObject, open: () -> InputStream): String {
        val id = newContainerId()
        job.put("container", id)
        val config = LinuxBackups.restore(open, id, {}, bytes(job))
        log(id, "Restored from a backup of ${config.optString("restoredFrom")}")
        runCatching {
            LinuxSessionCompatibility.install(LinuxPaths.rootfs(id), requireProot().nativeLibraryDir, LinuxProot.socketNamespace(id),
                xvfb = true)
        }.onFailure { log(id, "Linux compatibility setup failed: ${it.message}") }
        notifyChanged()
        return id
    }

    override fun restoreSnapshot(file: String) {
        val snapshot = LinuxBackups.file(file)
        check(snapshot.isFile) { moe.n4tsu.dextop.InlineStrings.format(moe.n4tsu.dextop.localText("uiSnapshot0DoesNotExist"), file) }
        startFileJob("restore", "") { job -> restoreAs(job) { snapshot.inputStream() } }
    }

    @Synchronized
    override fun deleteSnapshot(file: String): Boolean {
        check(fileJob?.let { synchronized(it) { it.optString("state") } } != "running") { moe.n4tsu.dextop.localText("uiABackupJobIsRunning") }
        return LinuxBackups.file(file).delete().also { notifyChanged() }
    }

    override fun exportSnapshot(file: String, output: android.os.ParcelFileDescriptor) {
        val snapshot = LinuxBackups.file(file)
        if (!snapshot.isFile) { output.close(); error(moe.n4tsu.dextop.InlineStrings.format(moe.n4tsu.dextop.localText("uiSnapshot0DoesNotExist"), file)) }
        startFileJob("export", file.substringBefore('-')) { job ->
            val progress = bytes(job)
            android.os.ParcelFileDescriptor.AutoCloseOutputStream(output).use { stream ->
                snapshot.inputStream().use { input ->
                    val buffer = ByteArray(1 shl 20)
                    var copied = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        stream.write(buffer, 0, count)
                        copied += count
                        progress(copied)
                    }
                }
            }
            file
        }
    }

    override fun importBackup(input: android.os.ParcelFileDescriptor) {
        startFileJob("import", "") { job ->
            // A document may not be readable twice: it is copied next to the snapshots first.
            LinuxBackups.directory.mkdirs()
            val copy = File(LinuxBackups.directory, ".import-${System.currentTimeMillis()}.tmp")
            try {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(input).use { stream ->
                    copy.outputStream().use { out -> stream.copyTo(out, 1 shl 20) }
                }
                restoreAs(job) { copy.inputStream() }
            } finally {
                copy.delete()
            }
        }
    }

    override fun installAppImage(containerId: String, file: android.os.ParcelFileDescriptor, fileName: String): String {
        if (readConfig(containerId)?.optString("state") != "installed") { file.close(); error(moe.n4tsu.dextop.InlineStrings.format(moe.n4tsu.dextop.localText("uiContainer0IsNotInstalled"), containerId)) }
        val slug = LinuxAppImage.slug(fileName)
        startFileJob("appimage", containerId) { job ->
            val root = LinuxPaths.rootfs(containerId)
            val home = LinuxUser.home(root)
            val guestFile = "$home/Applications/$slug.AppImage"
            val guestDir = "$home/Applications/.$slug"
            val hostFile = File(root, guestFile.removePrefix("/"))
            hostFile.parentFile?.mkdirs()
            hostFile.parentFile?.apply { setWritable(true, false); setReadable(true, false); setExecutable(true, false) }
            val staged = File(hostFile.parentFile, ".$slug.AppImage.part")
            val progress = bytes(job)
            try {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(file).use { input ->
                    staged.outputStream().use { out ->
                        val buffer = ByteArray(1 shl 20)
                        var copied = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            out.write(buffer, 0, count)
                            copied += count
                            progress(copied)
                        }
                    }
                }
                val image = LinuxAppImage.inspect(staged)
                check(staged.renameTo(hostFile)) { moe.n4tsu.dextop.localText("uiCannotSaveTheAppImage") }
                hostFile.setReadable(true, false); hostFile.setExecutable(true, false)
                val proot = requireProot()
                log(containerId, "AppImage $slug: unpacking (image at byte ${image.offset})")
                val tools = proot.run(proot.guest(containerId, LinuxAppImage.TOOLS_SCRIPT, fakeRoot = true), 900) { log(containerId, it) }
                check(tools.exitCode == 0) { moe.n4tsu.dextop.InlineStrings.format(moe.n4tsu.dextop.localText("uiSquashfsToolsCouldNotBeInstalledExit0"), tools.exitCode) }
                val unpacked = proot.run(proot.guest(containerId, LinuxAppImage.unpackScript(guestFile, image.offset, guestDir)), 900) { log(containerId, it) }
                check(unpacked.exitCode == 0) { moe.n4tsu.dextop.InlineStrings.format(moe.n4tsu.dextop.localText("uiTheAppImageCouldNotBeUnpackedExit01"), unpacked.exitCode, unpacked.output.takeLast(500)) }
                val (entryName, contents, name) = LinuxAppImage.desktopEntry(root, guestDir, guestFile, slug)
                val applications = File(root, home.removePrefix("/") + "/.local/share/applications")
                applications.mkdirs()
                File(applications, entryName).writeText(contents)
                log(containerId, "AppImage installed: $name ($entryName)")
                containerSizes.invalidate(containerId)
                notifyChanged()
                JSONObject().put("desktopId", entryName).put("name", name)
            } finally {
                staged.delete()
            }
        }
        return JSONObject().put("started", true).put("slug", slug).toString()
    }

    override fun removeAppImage(containerId: String, desktopId: String): Boolean {
        require(desktopId.startsWith(LinuxAppImage.ENTRY_PREFIX) && desktopId.endsWith(".desktop") && '/' !in desktopId) { moe.n4tsu.dextop.localText("uiNotAnAppImageEntry") }
        val root = LinuxPaths.rootfs(containerId)
        val home = LinuxUser.home(root)
        val entry = File(root, home.removePrefix("/") + "/.local/share/applications/$desktopId")
        if (!entry.isFile) return false
        val slug = desktopId.removePrefix(LinuxAppImage.ENTRY_PREFIX).removeSuffix(".desktop")
        require(slug == LinuxAppImage.slug(slug)) { moe.n4tsu.dextop.localText("uiInvalidAppImageEntry") }
        val applications = File(root, home.removePrefix("/") + "/Applications")
        LinuxRootfsArchive.deleteTree(File(applications, ".$slug"))
        File(applications, "$slug.AppImage").delete()
        entry.delete()
        containerSizes.invalidate(containerId)
        log(containerId, "AppImage removed: $slug")
        notifyChanged()
        return true
    }

    override fun containerUsage(containerId: String) {
        check(readConfig(containerId) != null) { moe.n4tsu.dextop.InlineStrings.format(moe.n4tsu.dextop.localText("uiUnknownContainer0"), containerId) }
        startFileJob("usage", containerId) { job ->
            val root = LinuxPaths.rootfs(containerId)
            LinuxStorage.usage(root, LinuxUser.home(root), bytes(job))
                .put("snapshotBytes", LinuxBackups.directory.listFiles().orEmpty()
                    .filter { it.name.startsWith("$containerId-") && it.name.endsWith(LinuxBackups.EXTENSION) }.sumOf { it.length() })
        }
    }

    override fun storageBreakdown(containerId: String, category: String, path: String, offset: Int): String {
        check(readConfig(containerId)?.optString("state") == "installed") { moe.n4tsu.dextop.localText("uiContainerIsNotInstalled") }
        val root = LinuxPaths.rootfs(containerId)
        return LinuxStorage.breakdown(root, LinuxUser.home(root), category, path, offset).toString()
    }

    override fun appUninstallTarget(containerId: String, desktopId: String): String {
        val root = LinuxPaths.rootfs(containerId)
        val entry = LinuxDesktopEntries.find(root, desktopId) ?: error(moe.n4tsu.dextop.localText("uiApplicationWasNotFound"))
        if (entry.flatpakId.isNotEmpty()) return JSONObject().put("source", "flathub").put("package", entry.flatpakId).toString()
        val family = LinuxCatalog.find(readConfig(containerId)?.optString("distro").orEmpty())?.family
        val paths = listOf("/usr/share/applications/$desktopId", "/usr/local/share/applications/$desktopId",
            "${LinuxUser.home(root)}/.local/share/applications/$desktopId").joinToString(" ") { LinuxCatalog.shellQuote(it) }
        val script = when (family) {
            LinuxPackageFamily.APT -> "dpkg-query -S -- $paths 2>/dev/null | head -n 1"
            LinuxPackageFamily.PACMAN -> "pacman -Qoq -- $paths 2>/dev/null | head -n 1"
            else -> error(moe.n4tsu.dextop.localText("uiThisContainerSPackageManagerIsNotSupported"))
        }
        val result = JSONObject(runCommand(containerId, script, 30))
        val output = result.optString("output").trim().lineSequence().firstOrNull().orEmpty()
        val pkg = if (family == LinuxPackageFamily.APT) output.substringBefore(": ").substringBefore(',') else output
        require(pkg.matches(Regex("[A-Za-z0-9][A-Za-z0-9+._:-]*"))) { moe.n4tsu.dextop.localText("uiNoInstalledPackageOwnsThisApplicationItMayHaveBeenAddedManually") }
        val records = File(root, "etc/dextop-deb-get/installed")
        val debget = records.isFile && records.useLines { lines -> lines.any { it.substringBefore(' ') == pkg } }
        return JSONObject().put("source", if (family == LinuxPackageFamily.PACMAN) "pacman" else if (debget) "deb-get" else "apt")
            .put("package", pkg).toString()
    }

    override fun cleanContainer(containerId: String) {
        check(readConfig(containerId)?.optString("state") == "installed") { moe.n4tsu.dextop.InlineStrings.format(moe.n4tsu.dextop.localText("uiContainer0IsNotInstalled"), containerId) }
        // /tmp holds the sessions' sockets and display files: only while nothing runs.
        check(!containerBusy(containerId)) { moe.n4tsu.dextop.localText("uiStopTheContainerSSessionsAndApplicationsFirst") }
        startFileJob("clean", containerId) { _ ->
            val root = LinuxPaths.rootfs(containerId)
            val before = LinuxStorage.usage(root, LinuxUser.home(root)).optLong("total")
            val proot = requireProot()
            proot.run(proot.guest(containerId, LinuxStorage.PACKAGE_CACHE_SCRIPT, fakeRoot = true), 300) { log(containerId, it) }
            LinuxStorage.clean(root, LinuxUser.home(root))
            File(root, "tmp").apply { mkdirs(); setWritable(true, false); setReadable(true, false); setExecutable(true, false) }
            val after = LinuxStorage.usage(root, LinuxUser.home(root)).optLong("total")
            val freed = (before - after).coerceAtLeast(0)
            containerSizes.invalidate(containerId)
            log(containerId, "Cleaned caches: ${freed / 1_048_576} MiB freed")
            notifyChanged()
            JSONObject().put("freed", freed).put("total", after)
        }
    }
    // endregion

    /**
     * Saves settings. A changed renderer applies to applications started
     * afterwards; running sessions keep theirs until restarted.
     */
    override fun resetSettings() = LinuxSettings.reset()

    override fun setLinuxSettings(json: String): String {
        val update = JSONObject(json)
        // {"gpuProbe": true} runs the device test (Settings > Graphics > Test this device).
        if (update.optBoolean("gpuProbe")) {
            startGpuProbe(update.optString("container").takeIf { it.isNotEmpty() })
            return linuxSettings()
        }
        val active = (sessions.keys + appSessions.keys).toSet()
        val before = active.associateWith { LinuxSettings.gpuMode(it) }
        // {"container": id, ...} changes that container's settings; otherwise the global defaults.
        val container = update.optString("container").takeIf { it.isNotEmpty() && readConfig(it) != null }
        if (container != null) LinuxSettings.writeContainer(container, update) else LinuxSettings.write(update)
        if (update.has("dnsServers")) {
            val resolver = LinuxSettings.customResolver() ?: networkResolver ?: androidResolver()
            containerIds().filter { readConfig(it)?.optString("state") == "installed" }.forEach {
                LinuxNetworkLink.stageResolver(LinuxPaths.rootfs(it), resolver)
            }
        }
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
            if (application?.usesXwayland == true || desktop?.usesXwayland == true) {
                val previousDpi = (application?.displayConfiguration ?: desktop?.displayConfiguration)?.dpi
                application?.updateDisplay(config); desktop?.updateDisplay(config)
                // Xwayland takes its size from the compositor, but its DPI (the desktop's scale on
                // the external screen) only from the X resources: applications started from now on
                // follow it.
                if (previousDpi != config.dpi) {
                    val result = requireProot().run(requireProot().guest(containerId, LinuxCatalog.updateDpiScript(display, config.dpi)), 15)
                    if (result.exitCode != 0) log(containerId, "Linux DPI update failed: ${result.output.takeLast(500)}")
                }
            } else {
                val began = android.os.SystemClock.uptimeMillis()
                val result = requireProot().run(requireProot().guest(containerId, LinuxCatalog.updateDisplayScript(display, config, apps)), 15)
                android.util.Log.i("DextopLaunch", "updateDisplay script ${config.width}x${config.height}@${config.refreshMilliHz} " +
                    "took ${android.os.SystemClock.uptimeMillis() - began}ms")
                check(result.exitCode == 0) { moe.n4tsu.dextop.InlineStrings.format(moe.n4tsu.dextop.localText("uiLinuxDisplayUpdateFailed0"), result.output.takeLast(2000)) }
                application?.updateDisplay(config); desktop?.updateDisplay(config)
            }
            callback(containerId) { it.onWindowEvent(containerId, JSONObject().put("type", "display-config").put("apps", apps)
                .put("w", config.width).put("h", config.height).put("refreshMilliHz", config.refreshMilliHz).toString()) }
        }.onFailure { log(containerId, moe.n4tsu.dextop.InlineStrings.format(moe.n4tsu.dextop.localText("uiLinuxDisplayUpdateFailed0"), it.message)) }
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

    /** Only encrypted keyring values wait for later sessions; setup retains one-way hashes. */
    private val keyringSecrets = ConcurrentHashMap<String, Pair<LinuxSealedSecret, Boolean>>()
    private val pendingAccountHashes = ConcurrentHashMap<String, String>()

    @Synchronized
    override fun setKeyringSecret(containerId: String, secret: String, persistent: Boolean) {
        check(!closed) { moe.n4tsu.dextop.localText("uiLinuxServiceIsClosed") }
        LinuxPaths.container(containerId)
        if (secret.isEmpty()) keyringSecrets.remove(containerId) else {
            keyringSecrets[containerId] = LinuxSealedSecret.seal(secret.toByteArray(Charsets.UTF_8)) to persistent
            // Resume an interrupted fresh setup using a hash, never the cached plaintext.
            val root = LinuxPaths.rootfs(containerId)
            if (LinuxUser.name(root) == null && readConfig(containerId)?.optJSONObject("setupOptions")?.optString("username").orEmpty().isNotEmpty()) {
                pendingAccountHashes[containerId] = LinuxPasswordHash.create(secret.toByteArray(Charsets.UTF_8))
            }
        }
    }

    /** Containers whose account password socket is open ([listenForAccountPassword]). */
    private val passwordServers = ConcurrentHashMap<String, android.net.LocalServerSocket>()
    private val passwordClients = ConcurrentHashMap<String, android.net.LocalSocket>()
    // A socket message must not create an unbounded number of PAM processes/threads.
    private val passwordChecks = java.util.concurrent.ThreadPoolExecutor(
        1, 1, 0L, java.util.concurrent.TimeUnit.MILLISECONDS,
        java.util.concurrent.ArrayBlockingQueue<Runnable>(8),
        java.util.concurrent.ThreadFactory { Thread(it, "dextop-account-password-check").apply { isDaemon = true } },
    )
    /** Encrypted updates waiting for the app; plaintext is opened only during authentication or delivery. */
    private val pendingAccountPasswords = ConcurrentHashMap<String, LinuxSealedSecret>()

    /**
     * Opens [containerId]'s account password socket (LinuxPam.PASSWORD_SOCKET), where the passwd
     * PAM stack's dextop-passwd-sync hands over a changed password. It is kept only when PAM
     * accepts it for the container's account, so a process of the container can never make
     * Dextop keep anything but the real account password.
     */
    @Synchronized
    private fun listenForAccountPassword(containerId: String) {
        if (closed || passwordServers.containsKey(containerId)) return
        val server = runCatching {
            android.net.LocalServerSocket(LinuxProot.socketNamespace(containerId) + LinuxPam.PASSWORD_SOCKET)
        }.getOrElse {
            Log.w(TAG, "account password socket of $containerId is unavailable", it)
            return
        }
        passwordServers[containerId] = server
        Thread({
            while (!closed) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                synchronized(this@LinuxContainerService) {
                    if (closed || passwordServers[containerId] !== server) {
                        runCatching { client.close() }
                        return@Thread
                    }
                    passwordClients[containerId] = client
                }
                runCatching {
                    client.use { socket ->
                        if (socket.peerCredentials.uid != Os.getuid()) return@use
                        socket.soTimeout = 3000
                        val message = ByteArray(4200)
                        try {
                            val count = socket.inputStream.readNBytes(message, 0, message.size)
                            socket.outputStream.write("ok\n".toByteArray())
                            val separator = message.indexOf(0)
                            if (separator <= 0 || separator >= count || count - separator - 1 !in 1..4096) return@use
                            val user = String(message, 0, separator, Charsets.UTF_8)
                            val password = LinuxSealedSecret.seal(message.copyOfRange(separator + 1, count))
                            // Only ciphertext can wait behind another PAM authentication.
                            passwordChecks.execute { acceptAccountPassword(containerId, user, password, server) }
                        } finally { message.fill(0) }
                    }
                }
                passwordClients.remove(containerId, client)
            }
            runCatching { server.close() }
            passwordServers.remove(containerId, server)
        }, "dextop-account-password-$containerId").apply { isDaemon = true }.start()
    }

    @Synchronized
    private fun stopAccountPasswordListener(containerId: String) {
        passwordServers.remove(containerId)?.let { runCatching { it.close() } }
        passwordClients.remove(containerId)?.let { runCatching { it.close() } }
        pendingAccountPasswords.remove(containerId)
        keyringSecrets.remove(containerId)
        pendingAccountHashes.remove(containerId)
    }

    private fun acceptAccountPassword(containerId: String, user: String, password: LinuxSealedSecret, server: android.net.LocalServerSocket) {
        if (closed || passwordServers[containerId] !== server) return
        val runner = proot ?: return
        if (user != LinuxUser.name(LinuxPaths.rootfs(containerId))) return
        // The account's own PAM check, as its login does; never the shell's output, which is not logged.
        val check = runCatching {
            password.useBytes { bytes ->
                runner.run(runner.guest(containerId, "exec python3 ${LinuxPam.LOGIN} --check"), 20, inputBytes = bytes)
            }
        }.getOrNull()
        if (check?.exitCode != 0) {
            log(containerId, "Dextop: a changed account password was not accepted by PAM; the keyring password stays")
            return
        }
        synchronized(this) {
            if (closed || passwordServers[containerId] !== server) return
            pendingAccountPasswords[containerId] = password
        }
        deliverAccountPasswords()
    }

    private fun deliverAccountPasswords() {
        val target = callback ?: return
        for ((id, password) in pendingAccountPasswords) {
            try {
                password.useBytes { target.onAccountPassword(id, String(it, Charsets.UTF_8)) }
                // This is a oneway callback: successful enqueue is not successful
                // persistence. Keep it for reconnect until the app confirms it.
            } catch (error: RemoteException) {
                Log.w(TAG, "account password of $id waits for the app", error)
                return
            }
        }
    }

    override fun acknowledgeAccountPassword(containerId: String, password: String) {
        check(android.os.Binder.getCallingUid() == audioClientUid) { moe.n4tsu.dextop.localText("uiPasswordCallerIsNotTheConnectedApplication") }
        val current = pendingAccountPasswords[containerId] ?: return
        val matches = current.useBytes { saved ->
            val received = password.toByteArray(Charsets.UTF_8)
            try { java.security.MessageDigest.isEqual(saved, received) } finally { received.fill(0) }
        }
        if (matches) pendingAccountPasswords.remove(containerId, current)
    }

    /**
     * The keyring password for the session about to start, or null. It reaches the session
     * through its standard input (a pipe no other process can read), never through a file.
     */
    private fun takeKeyring(containerId: String): ByteArray? {
        val (secret, persistent) = keyringSecrets[containerId] ?: return null
        if (!persistent) keyringSecrets.remove(containerId)
        return secret.open()
    }

    override fun windowScroll(containerId: String, xid: Int, x: Int, y: Int, dx: Int, dy: Int) {
        appSessions[containerId]?.runCatching { scrollAt(xid, x, y, dx, dy) }
    }
    // endregion

    override fun destroy() {
        close()
        if (androidContext == null) exitProcess(0)
        androidContext.stopService(android.content.Intent(androidContext, LinuxRuntimeService::class.java))
    }

    @Synchronized internal fun close() {
        if (closed) return
        closed = true
        containerSizes.close()
        sizeWorker.shutdownNow()
        runCatching { deviceListener?.close() }
        deviceListener = null
        passwordServers.values.forEach { runCatching { it.close() } }
        passwordServers.clear()
        passwordClients.values.forEach { runCatching { it.close() } }
        passwordClients.clear()
        passwordChecks.shutdownNow()
        pendingAccountPasswords.clear()
        keyringSecrets.clear()
        pendingAccountHashes.clear()
        integrations.stopAll()
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
    }

    // region install pipeline
    private fun runInstall(id: String, distro: LinuxDistro) {
        runCatching {
            progress(id, "prepare", -1, "${distro.imageDistro}/${distro.imageRelease}")
            val prebuilt = prebuiltImage(id, distro)
            val tarball = if (prebuilt != null) downloadPrebuilt(id, prebuilt) else downloadLinuxContainers(id, distro)
            extract(id, tarball)
            readConfig(id)?.let { writeConfig(id, it.put("imageReady", true)) }
            tarball.delete()
            if (prebuilt != null) check(File(LinuxPaths.rootfs(id), LinuxImageManifest.MARKER).isFile) { "Prebuilt image has no marker" }
            configure(id, distro)
        }.onSuccess { finish(id) }.onFailure { fail(id, it) }
    }

    /**
     * The signed prebuilt Debian image. A missing or invalid manifest fails the installation; only
     * `setprop debug.dextop.linux_image linuxcontainers` selects the full setup, for debugging.
     */
    private fun prebuiltImage(id: String, distro: LinuxDistro): LinuxPrebuiltImage? {
        if (distro.id != "debian") return null
        if (LinuxImageManifest.linuxContainersForced()) {
            log(id, "prebuilt image disabled by debug.dextop.linux_image")
            return null
        }
        return LinuxImageManifest.parse(text(LinuxImageManifest.URL), protocol = PROTOCOL_VERSION)
    }

    private fun downloadPrebuilt(id: String, image: LinuxPrebuiltImage): File {
        log(id, "prebuilt image ${image.version}")
        // Named by content and shared between installations, so a later attempt continues this one.
        val cache = File(LinuxPaths.base, "image-cache").apply { mkdirs() }
        val part = File(cache, "${image.sha256}.part")
        cache.listFiles()?.filter { it != part }?.forEach { it.delete() }
        var lastPercent = -1
        LinuxResumableDownload.download(
            part, image.size,
            connect = { offset -> connect(image.url).apply { if (offset > 0) setRequestProperty("Range", "bytes=$offset-") } },
            checkCancelled = { checkCancelled(id) },
            onProgress = { received ->
                val percent = (received * 100 / image.size).toInt()
                if (percent != lastPercent) {
                    lastPercent = percent
                    progress(id, "download", percent, "$received/${image.size}")
                }
            },
        )
        progress(id, "verify", -1, "")
        val actual = sha256(id, part)
        if (!actual.equals(image.sha256, ignoreCase = true)) {
            part.delete()
            error(moe.n4tsu.dextop.InlineStrings.format(moe.n4tsu.dextop.localText("uiSHA256MismatchExpected0Got1"), image.sha256, actual))
        }
        log(id, "SHA-256 verified $actual")
        val tarball = File(LinuxPaths.container(id), "rootfs.tar.zst")
        check(part.renameTo(tarball)) { "Cannot move the downloaded image" }
        return tarball
    }

    private fun sha256(id: String, file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(BUFFER)
            while (true) {
                checkCancelled(id)
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** The original path, kept for debugging: stock image plus the complete setup on the device. */
    private fun downloadLinuxContainers(id: String, distro: LinuxDistro): File {
        val build = latestBuild(distro)
        log(id, "image ${distro.imageDistro}/${distro.imageRelease}/${LinuxCatalog.ARCHITECTURE} build $build")
        val base = "${LinuxCatalog.IMAGE_SERVER}/images/${distro.imageDistro}/${distro.imageRelease}/" +
            "${LinuxCatalog.ARCHITECTURE}/${LinuxCatalog.VARIANT}/$build"
        val expected = text("$base/SHA256SUMS").lineSequence()
            .map { it.trim().split(Regex("\\s+")) }
            .firstOrNull { it.size == 2 && it[1].trimStart('*') == "rootfs.tar.xz" }?.get(0)
            ?: error(moe.n4tsu.dextop.localText("uiRootfsTarXzIsMissingFromSHA256SUMS"))
        val tarball = File(LinuxPaths.container(id), "rootfs.tar.xz")
        val actual = download(id, "$base/rootfs.tar.xz", tarball)
        progress(id, "verify", -1, actual.take(16))
        check(actual.equals(expected, ignoreCase = true)) { moe.n4tsu.dextop.InlineStrings.format(moe.n4tsu.dextop.localText("uiSHA256MismatchExpected0Got1"), expected, actual) }
        log(id, "SHA-256 verified $actual")
        return tarball
    }

    private fun checkCancelled(id: String) {
        if (id in cancelled) throw InterruptedException(moe.n4tsu.dextop.localText("uiInstallationCancelled"))
    }

    private fun latestBuild(distro: LinuxDistro): String {
        val prefix = "${distro.imageDistro};${distro.imageRelease};${LinuxCatalog.ARCHITECTURE};${LinuxCatalog.VARIANT};"
        val line = text(LinuxCatalog.IMAGE_INDEX).lineSequence().firstOrNull { it.startsWith(prefix) }
            ?: error(moe.n4tsu.dextop.InlineStrings.format(moe.n4tsu.dextop.localText("uiNo0ImageFor12"), LinuxCatalog.ARCHITECTURE, distro.imageDistro, distro.imageRelease))
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
            check(responseCode == 200) { moe.n4tsu.dextop.InlineStrings.format(moe.n4tsu.dextop.localText("uiHTTP0For1"), responseCode, url) }
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
            check(connection.responseCode == 200) { moe.n4tsu.dextop.InlineStrings.format(moe.n4tsu.dextop.localText("uiHTTP0For1"), connection.responseCode, url) }
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
                moe.n4tsu.dextop.localText("uiRootFilesystemExtractionCompletedWithoutAShell")
            }
            checkCancelled(id)
            LinuxRootfsArchive.deleteTree(rootfs)
            check(staging.renameTo(rootfs)) { moe.n4tsu.dextop.localText("uiCannotCommitExtractedRootFilesystem") }
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
            check(tool.canExecute()) { moe.n4tsu.dextop.localText("uiZstdDecompressorIsNotPackaged") }
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
            decompressor?.let { check(it.waitFor() == 0) { moe.n4tsu.dextop.InlineStrings.format(moe.n4tsu.dextop.localText("uiZstdDecompressionFailedExit0"), it.exitValue()) } }
        } finally {
            decompressor?.destroy()
        }
    }

    private fun configure(id: String, distro: LinuxDistro, repair: Boolean = false) {
        val runner = requireProot()
        val prebuilt = !repair && File(LinuxPaths.rootfs(id), LinuxImageManifest.MARKER).isFile
        checkCancelled(id)
        val options = LinuxSetupOptions.fromJson(readConfig(id)?.optJSONObject("setupOptions")).validate(distro)
        progress(id, "configure", -1, "")
        prepareAndroid(id)
        val configureScript = if (prebuilt) "true\n" else LinuxCatalog.configureScript(distro.family)
        val configured = runner.run(runner.guest(id, configureScript, fakeRoot = true), 600, onProcess = { activeProcesses[id] = it }) { log(id, it) }
        check(configured.exitCode == 0) { moe.n4tsu.dextop.InlineStrings.format(moe.n4tsu.dextop.localText("uiConfigurationFailedExit0"), configured.exitCode) }
        checkCancelled(id)
        provisionPackages(id, distro, runner, baked = prebuilt)
        if (options.username.isNotEmpty() && LinuxUser.name(LinuxPaths.rootfs(id)) == null) {
            progress(id, "account", -1, "")
            LinuxSessionCompatibility.stage(LinuxPaths.rootfs(id), runner.nativeLibraryDir, LinuxProot.socketNamespace(id))
            val passwordHash = pendingAccountHashes[id] ?: error(moe.n4tsu.dextop.localText("uiReopenDextopToUnlockTheAccountSetupPasswordThenRetrySetup"))
            val uid = android.system.Os.getuid().takeIf { it > 0 } ?: 1000
            val script = "exec python3 /usr/local/libexec/dextop-user-setup ${options.username} $uid"
            val result = runner.run(runner.guest(id, script, fakeRoot = true), 600,
                onProcess = { activeProcesses[id] = it }, input = passwordHash) { log(id, it) }
            check(result.exitCode == 0) { moe.n4tsu.dextop.InlineStrings.format(moe.n4tsu.dextop.localText("uiLinuxAccountSetupFailedExit0SeeInstallationLog"), result.exitCode) }
            pendingAccountHashes.remove(id)
        }
        setupSteps(id, distro, options, runner, prebuilt)
    }

    private fun provisionPackages(id: String, distro: LinuxDistro, runner: LinuxProot, baked: Boolean = false) {
        progress(id, "refresh", -1, "")
        val refreshed = runner.run(runner.guest(id, LinuxCatalog.refreshScript(distro.family), fakeRoot = true), 1_800, onProcess = { activeProcesses[id] = it }) { line ->
            log(id, line)
            // apt: "Get:12 http://deb.debian.org/debian trixie/main arm64 Packages [9,670 kB]".
            if (line.startsWith("Get:") || line.startsWith("Hit:")) progress(id, "refresh", -1, line.substringAfter(' ').take(80))
        }
        check(refreshed.exitCode == 0) { moe.n4tsu.dextop.InlineStrings.format(moe.n4tsu.dextop.localText("uiPackageIndexRefreshFailedExit0"), refreshed.exitCode) }
        checkCancelled(id)
        if (baked) return
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
        check(provisioned.exitCode == 0) { moe.n4tsu.dextop.InlineStrings.format(moe.n4tsu.dextop.localText("uiPackageInstallationFailedExit0"), provisioned.exitCode) }
    }

    private fun setupSteps(id: String, distro: LinuxDistro, options: LinuxSetupOptions, runner: LinuxProot, prebuilt: Boolean = false) {
        for (step in LinuxSetupPlan.steps(distro, options).filterNot { prebuilt && it.phase in LinuxImageManifest.BAKED_PHASES }) {
            checkCancelled(id)
            // Steam's FEX preflight needs socket emulation before finish() can run.
            if (step.phase == "steam") {
                LinuxSessionCompatibility.stage(LinuxPaths.rootfs(id), runner.nativeLibraryDir, LinuxProot.socketNamespace(id))
            }
            progress(id, step.phase, -1, "")
            val result = try {
                LinuxSetupScript.run(LinuxPaths.rootfs(id), LinuxUser.relocateHome(step.script, LinuxUser.home(id))) { command ->
                    runner.run(runner.guest(id, command, fakeRoot = true, extraEnvironment = listOf("HOME=${LinuxUser.home(id)}")), 3_600, onProcess = { activeProcesses[id] = it }) { line ->
                        log(id, line)
                        if (line.startsWith("Unpacking ") || line.startsWith("Setting up ")) progress(id, step.phase, -1, line.take(100))
                    }
                }
            } finally { activeProcesses.remove(id) }
            checkCancelled(id)
            check(result.exitCode == 0) { moe.n4tsu.dextop.InlineStrings.format(moe.n4tsu.dextop.localText("ui0FailedExit1SeeInstallationLog"), step.phase, result.exitCode) }
        }
    }

    private fun finish(id: String) {
        val config = readConfig(id) ?: return
        LinuxSessionCompatibility.install(LinuxPaths.rootfs(id), requireProot().nativeLibraryDir,
            LinuxProot.socketNamespace(id), xvfb = true)
        activeProcesses.remove(id)
        progress(id, "finish", -1, "")
        val bytes = runCatching {
            ProcessBuilder("/system/bin/du", "-sk", LinuxPaths.container(id).absolutePath).start()
                .inputStream.bufferedReader().readText().trim().split(Regex("\\s+")).first().toLong() * 1024
        }.getOrDefault(-1L)
        config.remove("error")
        writeConfig(id, config.put("state", "installed").put("bytes", bytes))
        containerSizes.invalidate(id)
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
        proot?.takeIf { it.available() } ?: error(moe.n4tsu.dextop.localText("uiProotIsUnavailableInitializeWasNotCalled"))

    private fun containerIds(): List<String> =
        LinuxPaths.containers.listFiles()?.filter { File(it, "config.json").isFile }?.map { it.name }.orEmpty()

    private fun readConfig(id: String): JSONObject? =
        runCatching { JSONObject(LinuxPaths.config(id).readText()) }.getOrNull()

    @Synchronized
    private fun writeConfig(id: String, config: JSONObject) {
        val file = LinuxPaths.config(id)
        val temp = File(file.parentFile, "config.json.tmp")
        temp.writeText(config.toString(2))
        check(temp.renameTo(file)) { moe.n4tsu.dextop.localText("uiCannotSaveLinuxContainerConfiguration") }
    }

    private fun log(id: String, line: String) {
        runCatching { LinuxPaths.log(id).appendText(line + "\n") }
        // Also in logcat: the Android mode keeps install.log in app-private storage, out of adb's reach.
        Log.i("DextopLinuxLog", "[$id] ${line.take(3000)}")
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
