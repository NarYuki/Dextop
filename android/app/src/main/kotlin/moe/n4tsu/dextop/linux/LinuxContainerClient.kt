package moe.n4tsu.dextop.linux

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import moe.n4tsu.dextop.BuildConfig
import moe.n4tsu.dextop.privilege.DistributionPrivilegeRuntime
import rikka.shizuku.Shizuku
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * App-side connection to [LinuxContainerService] (a Shizuku/Stellar
 * UserService). One instance serves the Linux page and Plasma v2.
 */
internal object LinuxContainerClient {
    private const val TAG = "DextopLinuxClient"

    interface Listener {
        fun onProgress(containerId: String, phase: String, percent: Int, message: String) {}
        fun onLog(containerId: String, line: String) {}
        fun onContainersChanged() {}
        fun onConnectionChanged(connected: Boolean) {}
        fun onWindowEvent(containerId: String, event: org.json.JSONObject) {}
        fun onClipboard(containerId: String, text: String) {}
        fun onNotification(containerId: String, event: org.json.JSONObject) {}
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val calls = Executors.newSingleThreadExecutor { Thread(it, "DextopLinuxClient") }
    private val listeners = CopyOnWriteArrayList<Listener>()
    private data class PendingCall(val run: (ILinuxContainerService) -> Unit, val fail: (Throwable) -> Unit)
    private val pending = CopyOnWriteArrayList<PendingCall>()
    private var protocolRestarts = 0
    @Volatile private var remote: ILinuxContainerService? = null
    @Volatile private var binding = false
    @Volatile private var usingAndroid = false
    @Volatile private var usingEmbedded = false
    @Volatile private var embeddedBinder: IBinder? = null
    private var bindGeneration = 0
    private var appContext: Context? = null

    private val args by lazy {
        val context = appContext!!
        // The service is a daemon: Shizuku/Stellar reuse a running instance
        // whose version matches. Derive the version from the install time so
        // an updated APK always replaces the service running the old code.
        val installed = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime / 1000
        }.getOrDefault(0L)
        Shizuku.UserServiceArgs(ComponentName(context, LinuxContainerService::class.java))
            .processNameSuffix("dextop_linux")
            .tag("dextop-linux")
            .version((installed % Int.MAX_VALUE).toInt())
            .debuggable(BuildConfig.DEBUG)
            .daemon(true)
    }

    private val callback = object : ILinuxContainerCallback.Stub() {
        override fun onProgress(containerId: String, phase: String, percent: Int, message: String) =
            dispatch { it.onProgress(containerId, phase, percent, message) }

        override fun onLog(containerId: String, line: String) = dispatch { it.onLog(containerId, line) }

        override fun onContainersChanged() {
            refreshAudio()
            dispatch { it.onContainersChanged() }
        }

        override fun onClipboard(containerId: String, text: String) = dispatch { it.onClipboard(containerId, text) }

        override fun onNotification(containerId: String, json: String) {
            val event = runCatching { org.json.JSONObject(json) }.getOrNull() ?: return
            dispatch { it.onNotification(containerId, event) }
        }

        override fun onWindowEvent(containerId: String, json: String) {
            val event = runCatching { org.json.JSONObject(json) }.getOrNull() ?: return
            dispatch { it.onWindowEvent(containerId, event) }
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = binder?.takeIf { it.pingBinder() }?.let(ILinuxContainerService.Stub::asInterface) ?: run {
                binding = false
                failPending(IllegalStateException("Linux service binder is unavailable"))
                return
            }
            val context = appContext ?: return
            calls.execute {
                val version = runCatching { service.protocolVersion() }.getOrNull()
                // A daemon kept across an APK update still runs the old code, whatever its protocol.
                val code = runCatching { service.codePath() }.getOrNull()
                val stale = version != null && (version != LinuxContainerService.PROTOCOL_VERSION ||
                    code?.contains(context.applicationInfo.sourceDir) != true)
                if (stale && protocolRestarts++ == 0) {
                    Log.i(TAG, "restarting Linux service protocol=$version expected=${LinuxContainerService.PROTOCOL_VERSION} code=$code")
                    // Some compatible privilege servers reuse a daemon after an APK update.
                    // The old process holds the old dex; it must exit before loading the new API.
                    runCatching { service.destroy() }
                    remote = null
                    binding = false
                    mainHandler.postDelayed({ connect(context) }, 500)
                    return@execute
                }
                // A privilege provider can restart the other mode's runtime after a mode switch stopped
                // it, and its connection arrives late. It is not ours: drop it and keep waiting.
                val probedUid = runCatching { Regex("uid=(\\d+)").find(service.probe())?.groupValues?.get(1)?.toIntOrNull() }.getOrNull()
                if (probedUid != null && (probedUid == android.os.Process.myUid()) != usingAndroid) {
                    Log.i(TAG, "ignoring a Linux runtime of the other execution mode uid=$probedUid")
                    if (!usingAndroid) return@execute
                    runCatching { service.destroy() }
                    return@execute
                }
                runCatching {
                    check(service.protocolVersion() == LinuxContainerService.PROTOCOL_VERSION) { "protocol mismatch" }
                    val report = service.probe()
                    val uid = Regex("uid=(\\d+)").find(report)?.groupValues?.get(1)?.toIntOrNull()
                    check(if (usingAndroid) uid == android.os.Process.myUid() else uid in setOf(0, 2000)) {
                        "Linux execution UID does not match the selected mode"
                    }
                    service.initialize(context.applicationInfo.nativeLibraryDir, callback)
                }.onSuccess {
                    binding = false
                    protocolRestarts = 0
                    remote = service
                    runCatching {
                        LinuxNetworkMonitor.start(context, scanRequested = { remote?.consumeNetworkScanRequests() == true }) { snapshot ->
                            calls.execute {
                                runCatching { remote?.setNetworkLink(snapshot) }
                                    .onFailure { Log.w(TAG, "Cannot publish Linux network link", it) }
                            }
                        }
                    }.onFailure { Log.w(TAG, "Cannot observe Android network link", it) }
                    refreshAudio()
                    Log.i(TAG, "connected android=$usingAndroid via=${if (usingEmbedded) "embedded" else "external"}: ${runCatching { service.probe() }.getOrNull()}")
                    dispatch { it.onConnectionChanged(true) }
                    pending.toList().also { pending.clear() }.forEach { it.run(service) }
                }.onFailure {
                    binding = false
                    Log.e(TAG, "Linux service initialization failed", it)
                    failPending(it)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            remote = null
            binding = false
            dispatch { it.onConnectionChanged(false) }
        }
    }

    /** Sound players by container, following the containers' PulseAudio ports. */
    private val players = HashMap<String, LinuxAudioPlayer>()
    private val audioCalls = Executors.newSingleThreadExecutor { Thread(it, "DextopLinuxAudioRefresh") }

    fun prepareDeckAudio(service: ILinuxContainerService, containerId: String) {
        val port = runCatching { LinuxDeckAudio.ensure(checkNotNull(appContext), containerId) }
            .onFailure { Log.w(TAG, "Deck DirectAudio unavailable; using container audio", it) }.getOrDefault(0)
        val cookie = if (port > 0) android.util.Base64.encodeToString(LinuxDeckAudio.cookie(), android.util.Base64.NO_WRAP) else ""
        service.setGuestState(containerId, LinuxDeckAudio.STATE, if (port > 0) "$port\n$cookie\n" else "0\n")
    }

    fun releaseDeckAudio(containerId: String) { calls.execute { LinuxDeckAudio.release(containerId) } }

    private fun refreshAudio() {
        // The microphone follows the same containers as sound output.
        appContext?.let { context ->
            LinuxMicrophone.start(context, { remote }, { synchronized(players) { players.keys.toList() } })
            LinuxDeviceBridge.keepPublished(context) { remote }
        }
        audioCalls.execute {
            val service = remote ?: return@execute
            val ports = runCatching {
                val list = org.json.JSONArray(service.listContainers())
                (0 until list.length()).map(list::getJSONObject)
                    .associate { it.optString("id") to it.optInt("audioGeneration") }
                    .filterValues { it > 0 }
            }.onFailure { Log.w(TAG, "Cannot refresh Linux audio ports", it) }.getOrNull() ?: return@execute
            synchronized(players) {
                players.entries.removeAll { (id, player) ->
                    (ports[id] != player.generation).also { stale -> if (stale) player.stop() }
                }
                ports.forEach { (id, port) -> if (id !in players) {
                    Log.i(TAG, "Connecting Linux audio $id generation=$port")
                    val burst = appContext?.getSystemService(android.media.AudioManager::class.java)
                        ?.getProperty(android.media.AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
                    players[id] = LinuxAudioPlayer(id, port, burst) { checkNotNull(remote) { "Linux service disconnected" }.openAudioStream(id) }
                } }
            }
        }
    }

    private fun dispatch(action: (Listener) -> Unit) {
        mainHandler.post { listeners.forEach(action) }
    }

    private fun failPending(error: Throwable) {
        pending.toList().also { pending.clear() }.forEach { it.fail(error) }
    }

    /** Only the shell-only provider calls this, after validating the daemon's APK path. */
    fun acceptEmbeddedService(context: Context, binder: IBinder) {
        if (LinuxExecutionMode.android(context)) return
        appContext = context.applicationContext
        embeddedBinder = binder
        runCatching {
            binder.linkToDeath({
                if (embeddedBinder === binder) {
                    embeddedBinder = null
                    mainHandler.post {
                        if (usingEmbedded && remote?.asBinder() == binder) connection.onServiceDisconnected(null)
                    }
                }
            }, 0)
        }
        mainHandler.post {
            if (usingEmbedded && binding) connection.onServiceConnected(null, binder)
        }
    }

    fun addListener(listener: Listener) { listeners += listener }
    fun removeListener(listener: Listener) { listeners -= listener }
    fun onPrivilegeChanged() = dispatch { it.onConnectionChanged(connected) }

    val connected: Boolean get() = service() != null

    /** The connected service, for oneway input calls that may run on the main thread. */
    fun service(): ILinuxContainerService? = remote?.takeIf {
        it.asBinder().isBinderAlive && (!usingEmbedded || DistributionPrivilegeRuntime.enabled)
    }

    fun privilegedService(): ILinuxContainerService? = service()?.takeUnless { usingAndroid }

    /** Dextop's built-in runtime is sufficient; external providers remain optional. */
    fun privilegeAvailable(context: Context? = appContext): Boolean = context?.let(LinuxExecutionMode::android) == true ||
        DistributionPrivilegeRuntime.available ||
            (DistributionPrivilegeRuntime.enabled && embeddedBinder?.isBinderAlive == true) ||
            runCatching { Shizuku.pingBinder() && !Shizuku.isPreV11() }.getOrDefault(false)

    fun connect(context: Context) {
        appContext = context.applicationContext
        if (connected || binding || !privilegeAvailable()) return
        binding = true
        val generation = ++bindGeneration
        usingAndroid = LinuxExecutionMode.android(context)
        if (usingAndroid) {
            usingEmbedded = false
            mainHandler.post {
                runCatching {
                    check(context.applicationContext.bindService(
                        android.content.Intent(context, LinuxRuntimeService::class.java), connection,
                        // As important as Dextop itself: an on-screen Dextop gives Linux the top-app cores.
                        Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT or Context.BIND_INCLUDE_CAPABILITIES
                    )) { "Cannot bind Android Linux runtime" }
                }.onFailure { binding = false; failPending(it) }
            }
            mainHandler.postDelayed({
                if (bindGeneration == generation && binding && usingAndroid && !connected) {
                    binding = false
                    runCatching { context.applicationContext.unbindService(connection) }
                    failPending(IllegalStateException("Android Linux runtime did not connect"))
                }
            }, 15_000)
            return
        }
        if (DistributionPrivilegeRuntime.available ||
            (DistributionPrivilegeRuntime.enabled && embeddedBinder?.isBinderAlive == true)) {
            usingEmbedded = true
            embeddedBinder?.takeIf { it.isBinderAlive }?.let {
                connection.onServiceConnected(null, it)
                return
            }
            calls.execute {
                runCatching {
                    val command = LinuxServiceHost.startCommand(context.packageName,
                        context.applicationInfo.sourceDir, context.applicationInfo.nativeLibraryDir)
                    val result = DistributionPrivilegeRuntime.execute(arrayOf("/system/bin/sh", "-c", command))
                        ?: error("Dextop built-in access is not running")
                    check(result.exitCode == 0) { "Linux service launch failed: ${result.error}" }
                }.onFailure {
                    binding = false
                    Log.e(TAG, "Built-in Linux service launch failed", it)
                    failPending(it)
                }
            }
            mainHandler.postDelayed({
                if (bindGeneration == generation && binding && usingEmbedded && !connected) {
                    binding = false
                    failPending(IllegalStateException("Dextop's Linux service did not connect; retry Dextop setup"))
                }
            }, 15_000)
            return
        }
        usingEmbedded = false
        runCatching { Shizuku.bindUserService(args, connection) }
            .onFailure {
                binding = false
                Log.e(TAG, "Linux UserService bind failed", it)
                failPending(it)
            }
        // A user service that never answers (its provider restarted, Dextop updated under it)
        // would otherwise leave every call waiting with no error and no way to connect again.
        mainHandler.postDelayed({
            if (bindGeneration == generation && binding && !usingAndroid && !usingEmbedded && !connected) {
                binding = false
                Log.w(TAG, "Linux UserService did not connect within 15 s")
                failPending(IllegalStateException("Dextop's Linux service did not connect; try again"))
            }
        }, 15_000)
    }

    /** Explicit mode changes stop sessions only after the user confirms. Storage is never removed. */
    fun switchExecution(context: Context, mode: String, stop: Boolean, done: () -> Unit, failed: (Throwable) -> Unit) {
        calls.execute {
            runCatching {
                require(mode in setOf(LinuxExecutionMode.PRIVILEGED, LinuxExecutionMode.ANDROID))
                if (mode == LinuxExecutionMode.read(context)) return@runCatching
                check(!LinuxStoreJobs.hasActiveJobs()) { "Wait for Store operations to finish before switching Linux execution mode" }
                val current = remote
                if (current != null) {
                    val list = org.json.JSONArray(current.listContainers())
                    val active = (0 until list.length()).map(list::getJSONObject).filter {
                        it.optString("runtimeState") == "running" || it.optBoolean("appsRunning") ||
                        it.optString("state") in setOf("installing", "updating")
                    }
                    check(active.none { it.optString("state") in setOf("installing", "updating") }) {
                        "Wait for Linux installation or repair to finish before changing execution mode"
                    }
                    check(active.isEmpty() || stop) { "Linux sessions are running; confirm stopping them before switching modes" }
                    (if (stop) (0 until list.length()).map(list::getJSONObject) else active).forEach { item ->
                        val id = item.getString("id")
                        current.resetSession(id, true)
                    }
                    // The other mode's runtime must not stay behind: a shell-user service would keep
                    // running with privilege after the user chose the Android mode, and vice versa.
                    runCatching { current.destroy() }
                }
                val wasAndroid = usingAndroid
                val wasEmbedded = usingEmbedded
                remote = null
                binding = false
                ++bindGeneration
                synchronized(players) { players.values.forEach { it.stop() }; players.clear() }
                LinuxExecutionMode.write(context, mode)
                mainHandler.post {
                    if (wasAndroid) runCatching { context.applicationContext.unbindService(connection) }
                    else if (!wasEmbedded) runCatching { Shizuku.unbindUserService(args, connection, false) }
                    usingAndroid = false
                    usingEmbedded = false
                    dispatch { it.onConnectionChanged(false) }
                    done()
                }
            }.onFailure { mainHandler.post { failed(it) } }
        }
    }

    /** Runs [action] on a background thread once connected; [onError] runs on the main thread. */
    fun <T> call(context: Context, action: (ILinuxContainerService) -> T, onResult: (T) -> Unit, onError: (Throwable) -> Unit) {
        val wrapped: (ILinuxContainerService) -> Unit = { service ->
            calls.execute {
                runCatching {
                    check(LinuxContainerClient.service() === service) { "Linux execution mode or connection changed; retry the operation" }
                    action(service)
                }
                    .onSuccess { result -> mainHandler.post { onResult(result) } }
                    .onFailure { error -> mainHandler.post { onError(error) } }
            }
        }
        val service = service()
        if (service != null) {
            wrapped(service)
            return
        }
        if (!privilegeAvailable(context)) {
            mainHandler.post { onError(IllegalStateException("Complete Dextop setup to enable built-in access")) }
            return
        }
        pending += PendingCall(wrapped) { error -> mainHandler.post { onError(error) } }
        connect(context)
    }
}
