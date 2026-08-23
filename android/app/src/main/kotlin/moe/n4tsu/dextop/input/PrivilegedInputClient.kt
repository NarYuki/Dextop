package moe.n4tsu.dextop.input

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import moe.n4tsu.dextop.BuildConfig
import moe.n4tsu.dextop.MirrorService
import rikka.shizuku.Shizuku

/** Main-process controller for the privileged input UserService. */
internal class PrivilegedInputClient(
    context: Context,
    private val listener: Listener
) {
    interface Listener {
        fun onInputState(category: String, message: String)
        fun onThreeFingerGesture()
        fun onHaptic(strong: Boolean)
    }

    companion object {
        private const val TAG = "DextopInputClient"
    }

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val args = Shizuku.UserServiceArgs(
        ComponentName(appContext, PrivilegedInputService::class.java)
    )
        .processNameSuffix("dextop_input")
        .tag("dextop-input-v${PrivilegedInputProtocol.VERSION}")
        .version(PrivilegedInputProtocol.VERSION)
        .debuggable(BuildConfig.DEBUG)
        .daemon(false)

    @Volatile
    private var remote: IPrivilegedInputService? = null
    @Volatile
    private var binding = false
    @Volatile
    private var engineRunning = false
    @Volatile
    private var released = false
    private var fatalFailurePosted = false
    private var pendingConfig: IntArray? = null
    private var keyboardVisible = false

    private val callback = object : IPrivilegedInputCallback.Stub() {
        override fun onInputState(category: String, message: String) {
            mainHandler.post {
                listener.onInputState(category, message)
                if (category == "worker_exited") {
                    failSession("native input worker exited: $message")
                }
            }
        }

        override fun onThreeFingerGesture() {
            mainHandler.post { listener.onThreeFingerGesture() }
        }

        override fun onHaptic(strong: Boolean) {
            mainHandler.post { listener.onHaptic(strong) }
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            binding = false
            if (released) {
                runCatching { Shizuku.unbindUserService(args, this, true) }
                return
            }
            val service = IPrivilegedInputService.Stub.asInterface(binder)
            val protocol = runCatching { service.protocolVersion() }.getOrDefault(-1)
            if (protocol != PrivilegedInputProtocol.VERSION) {
                val message = "protocol mismatch client=${PrivilegedInputProtocol.VERSION} service=$protocol"
                Log.e(TAG, message)
                listener.onInputState("client_error", message)
                runCatching { Shizuku.unbindUserService(args, this, true) }
                return
            }
            remote = service
            fatalFailurePosted = false
            val probe = runCatching { service.probe() }
                .getOrElse { "probe_failed:${it.javaClass.simpleName}:${it.message}" }
            listener.onInputState("connected", probe)
            pendingConfig?.let { config ->
                runCatching {
                    service.configure(config)
                    engineRunning = service.start(callback)
                }.onFailure { error ->
                    engineRunning = false
                    listener.onInputState(
                        "client_error",
                        "engine start failed ${error.javaClass.simpleName}:${error.message}"
                    )
                }
            }
            runCatching { service.setKeyboardVisible(keyboardVisible) }
                .onFailure(::handleRemoteFailure)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            binding = false
            remote = null
            engineRunning = false
            val message = "privileged input UserService disconnected"
            listener.onInputState("disconnected", message)
            failSession(message)
        }
    }

    fun start(config: IntArray) {
        released = false
        fatalFailurePosted = false
        val configChanged = !sameSemanticConfig(pendingConfig, config)
        pendingConfig = config.copyOf()
        val service = remote
        if (service != null) {
            runCatching {
                if (configChanged || !engineRunning) {
                    service.configure(config)
                } else {
                    Log.d(
                        TAG,
                        "suppressed duplicate start config generation=" +
                                config[PrivilegedInputProtocol.CONFIG_GENERATION]
                    )
                }
                if (!engineRunning) engineRunning = service.start(callback)
            }.onFailure(::handleRemoteFailure)
            return
        }
        bind()
    }

    fun updateConfig(config: IntArray) {
        if (sameSemanticConfig(pendingConfig, config)) {
            Log.d(
                TAG,
                "suppressed duplicate config generation=" +
                        config[PrivilegedInputProtocol.CONFIG_GENERATION]
            )
            return
        }
        pendingConfig = config.copyOf()
        remote?.let { service ->
            runCatching { service.configure(config) }.onFailure(::handleRemoteFailure)
        }
    }

    fun setOutputReady(ready: Boolean) {
        remote?.let { service ->
            runCatching { service.setOutputReady(ready) }.onFailure(::handleRemoteFailure)
        }
    }

    fun inject(events: IntArray): Boolean {
        val service = remote ?: return false
        return runCatching {
            service.inject(events)
            true
        }.onFailure(::handleRemoteFailure).getOrDefault(false)
    }

    fun injectKeyboard(
        keyCode: Int,
        action: Int,
        metaState: Int,
        repeatCount: Int
    ): Boolean {
        val service = remote ?: return false
        return runCatching {
            service.injectKeyboard(keyCode, action, metaState, repeatCount)
        }.onFailure(::handleRemoteFailure).getOrDefault(false)
    }

    fun setKeyboardVisible(visible: Boolean) {
        keyboardVisible = visible
        val service = remote
        if (service != null) {
            runCatching { service.setKeyboardVisible(visible) }.onFailure(::handleRemoteFailure)
        } else if (visible) {
            released = false
            bind()
        }
    }

    fun stopEngine(reason: String) {
        engineRunning = false
        remote?.let { service ->
            runCatching { service.stop(reason) }.onFailure(::handleRemoteFailure)
        }
    }

    fun snapshot(): String =
        "connected=${isConnected()} engineRunning=$engineRunning binding=$binding pending=${pendingConfig != null}"

    fun isConnected(): Boolean = remote?.asBinder()?.isBinderAlive == true

    fun isEngineRunning(): Boolean = engineRunning && isConnected()

    fun acknowledgeEngineStopped() {
        engineRunning = false
    }

    fun acknowledgeEngineStarted() {
        engineRunning = isConnected()
    }

    fun release(reason: String) {
        released = true
        fatalFailurePosted = false
        pendingConfig = null
        keyboardVisible = false
        stopEngine(reason)
        remote = null
        binding = false
        runCatching { Shizuku.unbindUserService(args, connection, true) }
            .onFailure { Log.w(TAG, "UserService unbind failed reason=$reason", it) }
    }

    private fun bind() {
        if (binding || released) return
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            listener.onInputState("client_error", "Shizuku-compatible binder unavailable")
            return
        }
        if (runCatching { Shizuku.isPreV11() }.getOrDefault(true)) {
            listener.onInputState("client_error", "Shizuku UserService requires server v11+")
            return
        }
        binding = true
        runCatching { Shizuku.bindUserService(args, connection) }
            .onFailure { error ->
                binding = false
                listener.onInputState(
                    "client_error",
                    "UserService bind failed ${error.javaClass.simpleName}:${error.message}"
                )
            }
    }

    private fun handleRemoteFailure(error: Throwable) {
        Log.e(TAG, "privileged input Binder call failed", error)
        remote = null
        engineRunning = false
        val message = "Binder call failed ${error.javaClass.simpleName}:${error.message}"
        listener.onInputState("client_error", message)
        failSession(message)
    }

    private fun failSession(message: String) {
        if (released || fatalFailurePosted) return
        fatalFailurePosted = true
        Log.e(TAG, "fatal privileged input failure: $message")
        mainHandler.post {
            if (!released) MirrorService.stopActive()
        }
    }

    /**
     * Generation is diagnostic metadata, not input geometry. Rebuilding an
     * otherwise identical frame used to cancel the active kernel gesture every
     * time the overlay observed ACTION_DOWN.
     */
    private fun sameSemanticConfig(previous: IntArray?, next: IntArray): Boolean {
        if (previous == null || previous.size != next.size ||
            next.size != PrivilegedInputProtocol.CONFIG_SIZE
        ) return false
        for (index in 0 until PrivilegedInputProtocol.CONFIG_SIZE) {
            if (index == PrivilegedInputProtocol.CONFIG_GENERATION) continue
            if (previous[index] != next[index]) return false
        }
        return true
    }
}
