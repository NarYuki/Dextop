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
    private val epochs = LinuxConnectionEpoch()
    @Volatile private var activeConnection: RuntimeConnection? = null
    private val controlCalls = Executors.newFixedThreadPool(2) { Thread(it, "DextopLinuxModeSwitch") }
    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else mainHandler.post { action() }
    }
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

    private fun runtimeCallback(attempt: LinuxConnectionEpoch.Attempt) = object : ILinuxContainerCallback.Stub() {
        override fun onProgress(containerId: String, phase: String, percent: Int, message: String) =
            dispatch(attempt) { it.onProgress(containerId, phase, percent, message) }

        override fun onLog(containerId: String, line: String) = dispatch(attempt) { it.onLog(containerId, line) }

        override fun onContainersChanged() {
            if (!epochs.owns(attempt)) return
            refreshAudio()
            dispatch(attempt) { it.onContainersChanged() }
        }

        override fun onClipboard(containerId: String, text: String) = dispatch(attempt) { it.onClipboard(containerId, text) }

        override fun onNotification(containerId: String, json: String) {
            val event = runCatching { org.json.JSONObject(json) }.getOrNull() ?: return
            dispatch(attempt) { it.onNotification(containerId, event) }
        }

        /** passwd changed the account password: the keyring follows it in "account" mode, as on a desktop. */
        override fun onAccountPassword(containerId: String, password: String) {
            // Preserve hand-over ordering without retaining a plaintext String in the work queue.
            val protected = LinuxSealedSecret.seal(password.toByteArray(Charsets.UTF_8))
            calls.execute {
                if (!epochs.owns(attempt)) return@execute
                val context = appContext ?: return@execute
                val service = remote ?: activeConnection?.candidate ?: return@execute
                runCatching {
                    protected.useBytes { bytes ->
                        val transient = String(bytes, Charsets.UTF_8)
                        if (LinuxKeyring.mode(context, containerId) == LinuxKeyring.ACCOUNT) {
                            LinuxKeyring.setup(context, containerId, LinuxKeyring.ACCOUNT, transient, service)
                        }
                        service.acknowledgeAccountPassword(containerId, transient)
                    }
                }.onFailure { Log.w(TAG, "Cannot keep the changed account password of $containerId", it) }
            }
        }

        override fun onWindowEvent(containerId: String, json: String) {
            val event = runCatching { org.json.JSONObject(json) }.getOrNull() ?: return
            dispatch(attempt) { it.onWindowEvent(containerId, event) }
        }
    }

    private class RuntimeConnection(val attempt: LinuxConnectionEpoch.Attempt) : ServiceConnection {
        @Volatile var candidate: ILinuxContainerService? = null
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) = onMain {
            if (!epochs.owns(attempt)) return@onMain
            val service = binder?.takeIf { it.isBinderAlive }?.let(ILinuxContainerService.Stub::asInterface)
            if (service == null) {
                failConnection(this, IllegalStateException("Linux service binder is unavailable"))
                return@onMain
            }
            candidate = service
            val context = appContext ?: return@onMain
            calls.execute {
                if (!epochs.owns(attempt)) return@execute
                runCatching {
                    val version = service.protocolVersion()
                    val code = service.codePath()
                    val report = service.probe()
                    val uid = Regex("uid=(\\d+)").find(report)?.groupValues?.get(1)?.toIntOrNull()
                    check(if (attempt.android) uid == android.os.Process.myUid() else uid in setOf(0, 2000)) {
                        "Linux execution UID does not match the selected mode"
                    }
                    check(epochs.owns(attempt)) { "Linux connection changed during initialization" }
                    val stale = version != LinuxContainerService.PROTOCOL_VERSION || !code.contains(context.applicationInfo.sourceDir)
                    if (stale) {
                        if (protocolRestarts++ == 0) {
                            runCatching { service.destroy() }
                            onMain {
                                if (epochs.abandon(attempt)) {
                                    activeConnection = null
                                    binding = false
                                    if (embeddedBinder === binder) embeddedBinder = null
                                    releaseBinding(context, this)
                                    mainHandler.postDelayed({ connect(context) }, 500)
                                }
                            }
                            return@execute
                        }
                        error("Linux service protocol or APK version does not match; reconnect Dextop")
                    }
                    service.initialize(context.applicationInfo.nativeLibraryDir, runtimeCallback(attempt))
                    check(epochs.owns(attempt)) { "Linux connection changed during initialization" }
                    runCatching { LinuxKeyring.publish(context, service) }
                        .onFailure { Log.w(TAG, "Cannot hand over keyring passwords", it) }
                }.onSuccess {
                    onMain {
                        if (!epochs.owns(attempt)) return@onMain
                        if (LinuxExecutionMode.android(context) != attempt.android) {
                            failConnection(this, IllegalStateException("Linux execution mode changed; reconnect"))
                            return@onMain
                        }
                        binding = false
                        protocolRestarts = 0
                        remote = service
                        usingAndroid = attempt.android
                        usingEmbedded = attempt.embedded
                        runCatching {
                            LinuxNetworkMonitor.start(context, scanRequested = {
                                remote === service && service.consumeNetworkScanRequests()
                            }) { snapshot ->
                                calls.execute {
                                    if (remote === service) runCatching { service.setNetworkLink(snapshot) }
                                        .onFailure { Log.w(TAG, "Cannot publish Linux network link", it) }
                                }
                            }
                        }.onFailure { Log.w(TAG, "Cannot observe Android network link", it) }
                        refreshAudio()
                        Log.i(TAG, "connected android=${attempt.android} via=${if (attempt.embedded) "embedded" else "platform"}")
                        dispatch { it.onConnectionChanged(true) }
                        pending.toList().also { pending.clear() }.forEach { it.run(service) }
                    }
                }.onFailure { error -> onMain { failConnection(this, error) } }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) = onMain {
            if (!epochs.abandon(attempt)) return@onMain
            remote = null
            LinuxDeviceBridge.disconnect()
            binding = false
            activeConnection = null
            appContext?.let { releaseBinding(it, this) }
            failPending(IllegalStateException("Linux runtime disconnected; retry the operation"))
            dispatch { it.onConnectionChanged(false) }
        }
        override fun onBindingDied(name: ComponentName?) = onServiceDisconnected(name)
        override fun onNullBinding(name: ComponentName?) = onMain {
            failConnection(this, IllegalStateException("Linux runtime returned an empty binding"))
        }
    }

    private fun releaseBinding(context: Context, connection: RuntimeConnection, remove: Boolean = false) {
        if (connection.attempt.android) runCatching { context.applicationContext.unbindService(connection) }
        else if (!connection.attempt.embedded) {
            if (Looper.myLooper() == Looper.getMainLooper()) controlCalls.execute {
                runCatching { Shizuku.unbindUserService(args, connection, remove) }
            } else runCatching { Shizuku.unbindUserService(args, connection, remove) }
        }
    }

    private fun failConnection(connection: RuntimeConnection, error: Throwable) {
        if (!epochs.abandon(connection.attempt)) return
        remote = null
        binding = false
        activeConnection = null
        appContext?.let { releaseBinding(it, connection) }
        Log.e(TAG, "Linux service connection failed", error)
        failPending(error)
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
            if (remote !== service || epochs.switching) return@execute
            synchronized(players) {
                if (remote !== service || epochs.switching) return@synchronized
                players.entries.removeAll { (id, player) ->
                    (ports[id] != player.generation).also { stale -> if (stale) player.stop() }
                }
                ports.forEach { (id, port) -> if (id !in players) {
                    Log.i(TAG, "Connecting Linux audio $id generation=$port")
                    val burst = appContext?.getSystemService(android.media.AudioManager::class.java)
                        ?.getProperty(android.media.AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
                    players[id] = LinuxAudioPlayer(id, port, burst) {
                        check(remote === service && !epochs.switching) { "Linux service disconnected" }
                        service.openAudioStream(id)
                    }
                } }
            }
        }
    }

    private fun dispatch(attempt: LinuxConnectionEpoch.Attempt? = null, action: (Listener) -> Unit) {
        mainHandler.post {
            if (attempt != null && !epochs.owns(attempt)) return@post
            listeners.forEach(action)
        }
    }

    private fun failPending(error: Throwable) {
        pending.toList().also { pending.clear() }.forEach { it.fail(error) }
    }

    /** Only the shell-only provider calls this, after validating the daemon's APK path. */
    fun acceptEmbeddedService(context: Context, binder: IBinder) = onMain {
        if (LinuxExecutionMode.android(context)) return@onMain
        appContext = context.applicationContext
        embeddedBinder = binder
        runCatching {
            binder.linkToDeath({
                if (embeddedBinder === binder) {
                    embeddedBinder = null
                    mainHandler.post {
                        activeConnection?.takeIf { it.attempt.embedded && it.candidate?.asBinder() == binder }?.onServiceDisconnected(null)
                    }
                }
            }, 0)
        }
        mainHandler.post {
            activeConnection?.takeIf { it.attempt.embedded && binding }?.onServiceConnected(null, binder)
        }
    }

    fun addListener(listener: Listener) { listeners += listener }
    fun removeListener(listener: Listener) { listeners -= listener }
    fun onPrivilegeChanged() = dispatch { it.onConnectionChanged(connected) }

    val connected: Boolean get() = service() != null

    /** The connected service, for oneway input calls that may run on the main thread. */
    fun service(): ILinuxContainerService? = remote?.takeIf {
        !epochs.switching && it.asBinder().isBinderAlive && (!usingEmbedded || DistributionPrivilegeRuntime.enabled)
    }

    fun privilegedService(): ILinuxContainerService? = service()?.takeUnless { usingAndroid }

    /** Dextop's built-in runtime is sufficient; external providers remain optional. */
    fun privilegeAvailable(context: Context? = appContext): Boolean = context?.let(LinuxExecutionMode::android) == true ||
        DistributionPrivilegeRuntime.available ||
            (DistributionPrivilegeRuntime.enabled && embeddedBinder?.isBinderAlive == true) ||
            runCatching { Shizuku.pingBinder() && !Shizuku.isPreV11() }.getOrDefault(false)

    fun connect(context: Context): Unit = onMain {
        appContext = context.applicationContext
        if (epochs.switching || connected || binding || !privilegeAvailable(context)) return@onMain
        val androidMode = LinuxExecutionMode.android(context)
        val embedded = !androidMode && (DistributionPrivilegeRuntime.available ||
            (DistributionPrivilegeRuntime.enabled && embeddedBinder?.isBinderAlive == true))
        val previous = activeConnection
        val attempt = epochs.begin(androidMode, embedded) ?: return@onMain
        remote = null
        previous?.let { releaseBinding(context, it) }
        val connection = RuntimeConnection(attempt)
        activeConnection = connection
        binding = true
        usingAndroid = androidMode
        usingEmbedded = embedded
        // Covers every provider and the entire handshake, including rejected/late callbacks.
        mainHandler.postDelayed({
            if (epochs.owns(attempt) && binding) failConnection(connection,
                IllegalStateException("Linux runtime did not connect within 15 seconds; retry the operation"))
        }, 15_000)
        when {
            androidMode -> runCatching {
                check(context.applicationContext.bindService(
                    android.content.Intent(context, LinuxRuntimeService::class.java), connection,
                    Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT or Context.BIND_INCLUDE_CAPABILITIES
                )) { "Cannot bind Android Linux runtime" }
            }.onFailure { failConnection(connection, it) }
            embedded -> {
                val binder = embeddedBinder?.takeIf { it.isBinderAlive }
                if (binder != null) connection.onServiceConnected(null, binder)
                else calls.execute {
                    if (!epochs.owns(attempt)) return@execute
                    runCatching {
                        val command = LinuxServiceHost.startCommand(context.packageName,
                            context.applicationInfo.sourceDir, context.applicationInfo.nativeLibraryDir,
                            moe.n4tsu.dextop.privilege.PublisherKeys.issue(context, moe.n4tsu.dextop.privilege.PublisherKeys.LINUX))
                        val result = DistributionPrivilegeRuntime.execute(arrayOf("/system/bin/sh", "-c", command))
                            ?: error("Dextop built-in access is not running")
                        check(result.exitCode == 0) { "Linux service launch failed: ${result.error}" }
                    }.onFailure { error -> onMain { failConnection(connection, error) } }
                }
            }
            else -> runCatching { Shizuku.bindUserService(args, connection) }
                .onFailure { failConnection(connection, it) }
        }
    }

    /** Explicit mode changes stop sessions only after the user confirms. Storage is never removed. */
    fun switchExecution(context: Context, mode: String, stop: Boolean, done: () -> Unit, failed: (Throwable) -> Unit): Unit = onMain {
        if (mode !in setOf(LinuxExecutionMode.PRIVILEGED, LinuxExecutionMode.ANDROID)) {
            failed(IllegalArgumentException("Unknown Linux execution mode")); return@onMain
        }
        if (epochs.switching) {
            failed(IllegalStateException("Linux execution mode is already changing")); return@onMain
        }
        if (mode == LinuxExecutionMode.read(context)) { done(); return@onMain }
        val oldConnection = activeConnection
        val current = remote ?: oldConnection?.candidate ?: embeddedBinder
            ?.takeIf { !LinuxExecutionMode.android(context) && it.isBinderAlive }
            ?.let(ILinuxContainerService.Stub::asInterface)
        val switchId = epochs.beginSwitch() ?: return@onMain
        remote = null
        binding = false
        activeConnection = null
        failPending(IllegalStateException("Linux execution mode is changing; retry the operation"))
        // A blocked Binder call must not leave the settings page loading forever.
        mainHandler.postDelayed({
            if (epochs.finishSwitch(switchId)) {
                oldConnection?.let { releaseBinding(context, it) }
                failed(IllegalStateException("Stopping the previous Linux runtime timed out; the execution mode was not changed"))
                dispatch { it.onConnectionChanged(false) }
            }
        }, 45_000)
        controlCalls.execute {
            runCatching {
                fun currentSwitch() = check(epochs.ownsSwitch(switchId)) { "Linux mode switch was cancelled or timed out" }
                currentSwitch()
                check(!LinuxStoreJobs.hasActiveJobs()) { "Wait for Store operations to finish before switching Linux execution mode" }
                if (current != null) {
                    val list = org.json.JSONArray(current.listContainers())
                    val active = (0 until list.length()).map(list::getJSONObject).filter {
                        it.optString("runtimeState") in setOf("starting", "running") || it.optBoolean("appsRunning") ||
                            it.optBoolean("integrationsRunning") || it.optString("state") in setOf("installing", "updating")
                    }
                    check(active.none { it.optString("state") in setOf("installing", "updating") }) {
                        "Wait for Linux installation or repair to finish before changing execution mode"
                    }
                    check(active.isEmpty() || stop) { "Linux sessions are running; confirm stopping them before switching modes" }
                    (if (stop) (0 until list.length()).map(list::getJSONObject) else active).forEach { item ->
                        currentSwitch()
                        current.resetSession(item.getString("id"), true)
                    }
                    currentSwitch()
                    // Remote shell runtimes exit during this transaction, so DeadObjectException is expected.
                    LinuxDeviceBridge.disconnect()
                    try { current.destroy() } catch (_: android.os.DeadObjectException) { }
                }
                currentSwitch()
                oldConnection?.let { releaseBinding(context, it, remove = true) }
                currentSwitch()
                synchronized(players) { players.values.forEach { it.stop() }; players.clear() }
            }.onSuccess {
                onMain {
                    if (!epochs.ownsSwitch(switchId)) return@onMain
                    runCatching { LinuxExecutionMode.write(context, mode) }.onSuccess {
                        // All old state is retired before publishing the new mode. Never reset the
                        // new connection's mode flags from a delayed teardown callback.
                        usingAndroid = mode == LinuxExecutionMode.ANDROID
                        usingEmbedded = false
                        embeddedBinder = null
                        epochs.finishSwitch(switchId)
                        dispatch { it.onConnectionChanged(false) }
                        done()
                        connect(context)
                    }.onFailure { error ->
                        epochs.finishSwitch(switchId)
                        failed(error)
                        dispatch { it.onConnectionChanged(false) }
                    }
                }
            }.onFailure { error -> onMain {
                if (epochs.finishSwitch(switchId)) {
                    oldConnection?.let { releaseBinding(context, it) }
                    failed(error)
                    dispatch { it.onConnectionChanged(false) }
                }
            } }
        }
    }

    /** Runs [action] on a background thread once connected; [onError] runs on the main thread. */
    fun <T> call(context: Context, action: (ILinuxContainerService) -> T, onResult: (T) -> Unit, onError: (Throwable) -> Unit): Unit = onMain {
        if (epochs.switching) {
            onError(IllegalStateException("Linux execution mode is changing; retry the operation"))
            return@onMain
        }
        val wrapped: (ILinuxContainerService) -> Unit = { service ->
            calls.execute {
                runCatching {
                    check(LinuxContainerClient.service() === service) { "Linux execution mode or connection changed; retry the operation" }
                    action(service)
                }.onSuccess { result -> mainHandler.post {
                    LinuxCallResult.deliver(result, LinuxContainerClient.service() === service, onResult) {
                        onError(IllegalStateException("Linux execution mode or connection changed; retry the operation"))
                    }
                } }.onFailure { error -> mainHandler.post { onError(error) } }
            }
        }
        val service = service()
        if (service != null) { wrapped(service); return@onMain }
        if (!privilegeAvailable(context)) {
            onError(IllegalStateException("Complete Dextop setup to enable built-in access"))
            return@onMain
        }
        pending += PendingCall(wrapped, onError)
        connect(context)
    }
}
