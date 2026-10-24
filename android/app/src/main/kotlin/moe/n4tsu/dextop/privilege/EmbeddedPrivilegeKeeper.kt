package moe.n4tsu.dextop.privilege

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.n4tsu.dextop.OperationLog

/**
 * Keeps Dextop's built-in access connected without asking the user again.
 *
 * Every caller (the app opening, an Android Auto session starting, the runtime dying while
 * nothing is on screen) goes through one reconnection: Wireless debugging is switched back on
 * when Dextop holds WRITE_SECURE_SETTINGS, and the stored pairing is retried for minutes while
 * Wireless debugging advertises itself. Only an endpoint that rejects the stored key, i.e. a
 * revoked pairing, ends in [Outcome.REJECTED] and needs the user.
 */
internal object EmbeddedPrivilegeKeeper {
    enum class Outcome { CONNECTED, REJECTED, UNAVAILABLE }

    private const val TAG = "EmbeddedPrivilege"
    private const val RETRY_WINDOW_MS = 180_000L
    private const val REJECTED_DETAIL = "rejected all local connection candidates"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val main = Handler(Looper.getMainLooper())
    private val waiters = mutableListOf<(Outcome) -> Unit>()
    @Volatile private var job: Job? = null
    @Volatile private var appContext: Context? = null

    val reconnecting: Boolean get() = job?.isActive == true

    /** Reconnects if needed; [done] runs on the main thread once the outcome is known. */
    fun ensure(context: Context, reason: String, done: ((Outcome) -> Unit)? = null) {
        val app = context.applicationContext.also { appContext = it }
        if (DistributionPrivilegeRuntime.available) {
            scope.launch { grantSecureSettings(app) }
            done?.let { callback -> main.post { callback(Outcome.CONNECTED) } }
            return
        }
        if (!DistributionPrivilegeBootstrap.included || !DistributionPrivilegeRuntime.enabled ||
            !DistributionPrivilegeBootstrap.hasStoredPairing(app)
        ) {
            done?.let { callback -> main.post { callback(Outcome.UNAVAILABLE) } }
            return
        }
        synchronized(this) {
            done?.let(waiters::add)
            if (job?.isActive == true) return
            job = scope.launch { finish(reconnect(app, reason)) }
        }
    }

    /** The runtime Binder died; reconnect in the background even when no Dextop screen is open. */
    fun onRuntimeDied() {
        appContext?.let { ensure(it, "runtime_died") }
    }

    /** A runtime was accepted: make sure the next reconnection can switch Wireless debugging on. */
    fun onRuntimeAccepted(context: Context) {
        appContext = context.applicationContext
        scope.launch { grantSecureSettings(context.applicationContext) }
    }

    private suspend fun reconnect(context: Context, reason: String): Outcome {
        OperationLog.i(context, TAG, "keeper reconnecting reason=$reason")
        val deadline = SystemClock.elapsedRealtime() + RETRY_WINDOW_MS
        var attempt = 0
        var rejections = 0
        while (SystemClock.elapsedRealtime() < deadline) {
            if (DistributionPrivilegeRuntime.available) return connected(context, attempt)
            enableWirelessDebugging(context)
            val start = runCatching { DistributionPrivilegeBootstrap.connectAndStart(context) }
                .onFailure { OperationLog.i(context, TAG, "keeper attempt ${attempt + 1}: ${it.message ?: it.javaClass.simpleName}") }
                .getOrNull()
            if (start?.started == true || DistributionPrivilegeRuntime.available) return connected(context, attempt)
            // Two refusals in a row: one can be a handshake racing Wireless debugging restarting.
            rejections = if (start?.detail?.contains(REJECTED_DETAIL, ignoreCase = true) == true) rejections + 1 else 0
            if (rejections >= 2) {
                OperationLog.w(context, TAG, "keeper: the stored pairing was rejected; pairing again is required", null)
                DistributionPrivilegeBootstrap.showPairingNotification(context)
                return Outcome.REJECTED
            }
            attempt += 1
            // Wireless debugging needs a few seconds to advertise after it is switched on.
            delay((attempt * 1_000L).coerceAtMost(5_000L))
        }
        OperationLog.w(context, TAG, "keeper gave up for now after $attempt attempts; retrying on the next request", null)
        return Outcome.UNAVAILABLE
    }

    private fun connected(context: Context, attempt: Int): Outcome {
        DistributionPrivilegeBootstrap.dismissPairingNotification(context)
        grantSecureSettings(context)
        OperationLog.i(context, TAG, "keeper reconnected after ${attempt + 1} attempt(s)")
        return Outcome.CONNECTED
    }

    private fun finish(outcome: Outcome) {
        val callbacks = synchronized(this) {
            job = null
            waiters.toList().also { waiters.clear() }
        }
        main.post { callbacks.forEach { it(outcome) } }
    }

    private fun hasSecureSettings(context: Context) =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    private fun enableWirelessDebugging(context: Context) {
        val enabled = runCatching { Settings.Global.getInt(context.contentResolver, "adb_wifi_enabled", 0) == 1 }.getOrDefault(false)
        if (enabled || !hasSecureSettings(context)) return
        if (runCatching { Settings.Global.putInt(context.contentResolver, "adb_wifi_enabled", 1) }.getOrDefault(false)) {
            OperationLog.i(context, TAG, "keeper switched Wireless debugging on for the stored pairing")
        }
    }

    private fun grantSecureSettings(context: Context) {
        if (hasSecureSettings(context) || !DistributionPrivilegeRuntime.available) return
        runCatching {
            DistributionPrivilegeRuntime.execute(arrayOf("pm", "grant", context.packageName, Manifest.permission.WRITE_SECURE_SETTINGS))
        }.onSuccess { OperationLog.i(context, TAG, "keeper granted WRITE_SECURE_SETTINGS for later reconnections") }
    }
}
