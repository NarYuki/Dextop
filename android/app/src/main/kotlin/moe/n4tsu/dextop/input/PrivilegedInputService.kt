package moe.n4tsu.dextop.input

import android.os.IBinder
import android.system.Os
import android.util.Log
import androidx.annotation.Keep
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

/**
 * Shizuku UserService owning every high-frequency evdev and uinput operation.
 *
 * This is deliberately an AIDL Binder rather than an Android Service. Shizuku
 * loads the class into a shell/root app_process, so no application Context or
 * main-process singleton may be used here.
 */
@Keep
class PrivilegedInputService : IPrivilegedInputService.Stub() {
    companion object {
        private const val TAG = "DextopInputUserService"

        init {
            val embeddedLibrary = System.getProperty("moe.n4tsu.dextop.embedded_input_library")
            if (embeddedLibrary.isNullOrBlank()) {
                System.loadLibrary("dextop_input")
            } else {
                System.load(embeddedLibrary)
            }
        }
    }

    private val running = AtomicBoolean(false)
    @Volatile
    private var callback: IPrivilegedInputCallback? = null
    private val callbackDeath = IBinder.DeathRecipient {
        Log.w(TAG, "client callback binder died; releasing native input engine")
        callback = null
        stopNative("client_binder_died")
    }

    override fun protocolVersion(): Int = PrivilegedInputProtocol.VERSION

    override fun probe(): String {
        val seContext = runCatching {
            File("/proc/self/attr/current").readText().trim()
        }.getOrElse { "unavailable:${it.javaClass.simpleName}" }
        return "protocol=${PrivilegedInputProtocol.VERSION} pid=${Os.getpid()} uid=${Os.getuid()} " +
                "secontext=$seContext native=${nativeProbe()}"
    }

    override fun configure(config: IntArray) {
        require(config.size == PrivilegedInputProtocol.CONFIG_SIZE) {
            "Unexpected input config size=${config.size}"
        }
        require(config[PrivilegedInputProtocol.CONFIG_VERSION] == PrivilegedInputProtocol.VERSION) {
            "Input config protocol=${config[PrivilegedInputProtocol.CONFIG_VERSION]} " +
                    "service=${PrivilegedInputProtocol.VERSION}"
        }
        nativeConfigure(config)
    }

    @Synchronized
    override fun start(newCallback: IPrivilegedInputCallback): Boolean {
        callback?.asBinder()?.let { runCatching { it.unlinkToDeath(callbackDeath, 0) } }
        callback = newCallback
        runCatching { newCallback.asBinder().linkToDeath(callbackDeath, 0) }
            .onFailure { Log.w(TAG, "unable to monitor client callback binder", it) }
        val started = nativeStart()
        running.set(started)
        emitState("service", "start requested result=$started ${probe()}")
        return started
    }

    override fun setOutputReady(ready: Boolean) {
        nativeSetOutputReady(ready)
    }

    override fun inject(events: IntArray) {
        require(events.size % 3 == 0) { "Input event array must contain type/code/value triples" }
        nativeInject(events)
    }

    override fun injectKeyboard(
        keyCode: Int,
        action: Int,
        metaState: Int,
        repeatCount: Int
    ): Boolean = nativeInjectKeyboard(keyCode, action, metaState, repeatCount)

    override fun setKeyboardVisible(visible: Boolean) {
        nativeSetKeyboardVisible(visible)
    }

    override fun stop(reason: String) {
        stopNative(reason)
    }

    override fun destroy() {
        Log.i(TAG, "destroy requested")
        stopNative("destroy")
        callback?.asBinder()?.let { runCatching { it.unlinkToDeath(callbackDeath, 0) } }
        callback = null
        exitProcess(0)
    }

    @Keep
    private fun onNativeState(category: String, message: String) {
        emitState(category, message)
    }

    @Keep
    private fun onNativeThreeFinger() {
        runCatching { callback?.onThreeFingerGesture() }
            .onFailure { Log.w(TAG, "three-finger callback failed", it) }
    }

    @Keep
    private fun onNativeHaptic(strong: Boolean) {
        runCatching { callback?.onHaptic(strong) }
            .onFailure { Log.w(TAG, "haptic callback failed", it) }
    }

    private fun emitState(category: String, message: String) {
        Log.i(TAG, "$category: $message")
        runCatching { callback?.onInputState(category, message) }
            .onFailure { Log.w(TAG, "state callback failed category=$category", it) }
    }

    @Synchronized
    private fun stopNative(reason: String) {
        if (running.getAndSet(false)) {
            nativeStop(reason)
        } else {
            // nativeStop is idempotent and still releases a partially-created
            // uinput device after a failed start.
            nativeStop(reason)
        }
    }

    private external fun nativeProbe(): String
    private external fun nativeConfigure(config: IntArray)
    private external fun nativeStart(): Boolean
    private external fun nativeSetOutputReady(ready: Boolean)
    private external fun nativeInject(events: IntArray)
    private external fun nativeInjectKeyboard(
        keyCode: Int,
        action: Int,
        metaState: Int,
        repeatCount: Int
    ): Boolean
    private external fun nativeSetKeyboardVisible(visible: Boolean)
    private external fun nativeStop(reason: String)
}
