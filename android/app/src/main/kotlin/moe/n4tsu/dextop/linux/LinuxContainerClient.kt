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
                runCatching {
                    check(service.protocolVersion() == LinuxContainerService.PROTOCOL_VERSION) { "protocol mismatch" }
                    service.initialize(context.applicationInfo.nativeLibraryDir, callback)
                }.onSuccess {
                    binding = false
                    protocolRestarts = 0
                    remote = service
                    refreshAudio()
                    Log.i(TAG, "connected via=${if (usingEmbedded) "embedded" else "external"}: ${runCatching { service.probe() }.getOrNull()}")
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

    private fun refreshAudio() {
        calls.execute {
            val service = remote ?: return@execute
            val ports = runCatching {
                val list = org.json.JSONArray(service.listContainers())
                (0 until list.length()).map(list::getJSONObject)
                    .associate { it.optString("id") to it.optInt("audioPort") }
                    .filterValues { it > 0 }
            }.getOrNull() ?: return@execute
            synchronized(players) {
                players.entries.removeAll { (id, player) ->
                    (ports[id] != player.port).also { stale -> if (stale) player.stop() }
                }
                ports.forEach { (id, port) -> if (id !in players) players[id] = LinuxAudioPlayer(id, port) }
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

    /** Dextop's built-in runtime is sufficient; external providers remain optional. */
    fun privilegeAvailable(): Boolean =
        DistributionPrivilegeRuntime.available ||
            (DistributionPrivilegeRuntime.enabled && embeddedBinder?.isBinderAlive == true) ||
            runCatching { Shizuku.pingBinder() && !Shizuku.isPreV11() }.getOrDefault(false)

    fun connect(context: Context) {
        appContext = context.applicationContext
        if (connected || binding || !privilegeAvailable()) return
        binding = true
        val generation = ++bindGeneration
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
    }

    /** Runs [action] on a background thread once connected; [onError] runs on the main thread. */
    fun <T> call(context: Context, action: (ILinuxContainerService) -> T, onResult: (T) -> Unit, onError: (Throwable) -> Unit) {
        val wrapped: (ILinuxContainerService) -> Unit = { service ->
            calls.execute {
                runCatching { action(service) }
                    .onSuccess { result -> mainHandler.post { onResult(result) } }
                    .onFailure { error -> mainHandler.post { onError(error) } }
            }
        }
        val service = service()
        if (service != null) {
            wrapped(service)
            return
        }
        if (!privilegeAvailable()) {
            mainHandler.post { onError(IllegalStateException("Complete Dextop setup to enable built-in access")) }
            return
        }
        pending += PendingCall(wrapped) { error -> mainHandler.post { onError(error) } }
        connect(context)
    }
}
