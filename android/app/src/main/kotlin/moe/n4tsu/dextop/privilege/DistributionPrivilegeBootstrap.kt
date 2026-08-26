package moe.n4tsu.dextop.privilege

import android.content.Context
import android.util.Log
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.flyfishxu.kadb.Kadb
import com.flyfishxu.kadb.cert.KadbCert
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.net.NetworkInterface
import kotlin.coroutines.resume

/** Dextop's built-in ADB transport. It intentionally has no dependency on external apps. */
object DistributionPrivilegeBootstrap : EmbeddedPrivilegeContract {
    override val included: Boolean = true

    private const val PREFS = "dextop_embedded_privilege"
    private const val KEY_PAIRED = "paired"
    private const val LOG_TAG = "DextopEmbeddedPair"
    @Volatile private var identityConfigured = false
    private val pairingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var pairingDiscoveryGeneration = 0
    @Volatile private var pendingPairingEndpoint: Endpoint? = null
    @Volatile private var pairingDiscoveryActive = false

    fun hasStoredPairing(context: Context): Boolean {
        if (!AndroidKeystorePrivateKeyStore(context).hasStoredIdentity()) return false
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_PAIRED, false)
    }

    private fun configurePersistentIdentity(context: Context) {
        if (identityConfigured) return
        synchronized(this) {
            if (identityConfigured) return
            KadbCert.configure(AndroidKeystorePrivateKeyStore(context))
            identityConfigured = true
        }
    }

    fun showPairingNotification(context: Context) {
        val appContext = context.applicationContext
        // Do not overwrite the RemoteInput action after the pairing-code service
        // has been discovered. startForeground()/onStartCommand() may arrive
        // after discovery on One UI, so treating every request as a fresh search
        // used to replace "Enter code" with the non-interactive searching state.
        if (pendingPairingEndpoint != null ||
            EmbeddedPairingNotification.state == "waiting_for_code"
        ) {
            EmbeddedPairingNotification.showCodeInput(appContext)
        } else if (EmbeddedPairingNotification.state != "pairing") {
            // Reflect the state in both the setup UI and the notification before
            // Android schedules the foreground service. Starting discovery here
            // also removes the several-second FGS scheduling gap on One UI.
            EmbeddedPairingNotification.showSearching(appContext)
            beginPairingDiscovery(appContext)
        }
        EmbeddedPairingService.start(appContext)
    }

    fun beginPairingDiscovery(context: Context) {
        // Keep the discovered endpoint and its RemoteInput notification stable.
        // This method is called from both the setup screen and the foreground
        // service, which can be delivered in either order.
        if (pendingPairingEndpoint != null ||
            EmbeddedPairingNotification.state == "waiting_for_code" ||
            EmbeddedPairingNotification.state == "pairing"
        ) return
        if (pairingDiscoveryActive) return
        pairingDiscoveryActive = true
        val appContext = context.applicationContext
        val generation = synchronized(this) { ++pairingDiscoveryGeneration }
        EmbeddedPairingNotification.showSearching(appContext)
        pairingScope.launch {
            try {
                // Android advertises this endpoint only after the user opens the
                // pairing-code sheet. Keep looking while that sheet is being
                // opened instead of making the setup depend on a short race.
                // Bound this foreground discovery session.  Leaving the pairing
                // sheet unopened must not retain a foreground service for minutes.
                val pairingServiceFound = (1..12).any {
                    if (generation != pairingDiscoveryGeneration) return@launch
                    runCatching {
                        discover(appContext, "_adb-tls-pairing._tcp", timeoutMillis = 4_000)
                    }.onSuccess { endpoint ->
                        pendingPairingEndpoint = endpoint
                        Log.i(LOG_TAG, "pairing endpoint discovered ${endpoint.label()}")
                    }.onFailure { error ->
                        Log.d(LOG_TAG, "pairing endpoint not available yet: ${error.javaClass.simpleName}")
                    }.isSuccess
                }
                if (generation != pairingDiscoveryGeneration) return@launch
                if (pairingServiceFound) {
                    EmbeddedPairingNotification.showCodeInput(appContext)
                } else {
                    EmbeddedPairingNotification.showUnavailable(appContext)
                    EmbeddedPairingService.finish(appContext)
                }
            } finally {
                pairingDiscoveryActive = false
            }
        }
    }

    fun dismissPairingNotification(context: Context) {
        cancelPairingDiscovery()
        EmbeddedPairingService.finish(context.applicationContext)
        EmbeddedPairingNotification.dismiss(context)
    }

    fun cancelPairingDiscovery() {
        synchronized(this) { ++pairingDiscoveryGeneration }
        pendingPairingEndpoint = null
        pairingDiscoveryActive = false
    }

    fun pairingState(): String = EmbeddedPairingNotification.state

    override suspend fun pair(
        context: Context,
        code: String,
    ): EmbeddedPrivilegeContract.PairingResult {
        require(code.matches(Regex("[0-9]{6}"))) { "A six-digit pairing code is required" }
        configurePersistentIdentity(context)
        // Keep the endpoint that enabled the notification. Re-discovering at
        // code-submit time races Android closing the short-lived pairing ad.
        val endpoint = pendingPairingEndpoint ?: discover(context, "_adb-tls-pairing._tcp")
        Log.i(LOG_TAG, "submitting pairing code to ${endpoint.label()}")
        var pairingHost: String? = null
        var lastFailure: Throwable? = null
        for (candidate in endpoint.connectionHosts()) {
            try {
                Kadb.pair(candidate, endpoint.port, code, "Dextop")
                pairingHost = candidate
                Log.i(LOG_TAG, "pairing accepted by $candidate:${endpoint.port}")
                break
            } catch (error: Throwable) {
                lastFailure = error
                Log.d(LOG_TAG, "pairing rejected by $candidate:${endpoint.port}", error)
            }
        }
        check(pairingHost != null) {
            lastFailure?.message ?: "The pairing endpoint rejected all local connection candidates"
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_PAIRED, true).apply()
        pendingPairingEndpoint = null
        return EmbeddedPrivilegeContract.PairingResult(
            paired = true,
            endpoint = "$pairingHost:${endpoint.port}",
        )
    }

    /** Checks whether the saved key can still authenticate to Wireless debugging. */
    suspend fun canConnect(context: Context): Boolean {
        if (!hasStoredPairing(context)) return false
        configurePersistentIdentity(context)
        val endpoint = runCatching {
            discover(context, "_adb-tls-connect._tcp")
        }.getOrElse { error ->
            Log.i(LOG_TAG, "saved pairing has no advertised connect endpoint: ${error.javaClass.simpleName}")
            return false
        }
        return endpoint.connectionHosts().any { host ->
            Kadb.tryConnection(host, endpoint.port)?.use { true } ?: false
        }.also { connected ->
            Log.i(LOG_TAG, "saved pairing connection probe connected=$connected endpoint=${endpoint.label()}")
        }
    }

    override suspend fun connectAndStart(
        context: Context,
    ): EmbeddedPrivilegeContract.StartResult {
        configurePersistentIdentity(context)
        if (DistributionPrivilegeRuntime.available) {
            return EmbeddedPrivilegeContract.StartResult(started = true)
        }
        val endpoint = discover(context, "_adb-tls-connect._tcp")
        val candidates = endpoint.connectionHosts()
        var connectedHost: String? = null
        val connection = candidates.firstNotNullOfOrNull { host ->
            Kadb.tryConnection(host, endpoint.port)?.also { connectedHost = host }
        } ?: return EmbeddedPrivilegeContract.StartResult(
            started = false,
            endpoint = endpoint.label(),
            detail = "The paired ADB endpoint rejected all local connection candidates",
        )
        connection.use {
            // The server launcher is supplied by the isolated Play runtime.
            // Do not silently run an external-manager command here.
            val command = EmbeddedServerPayload.startCommand(context)
            val response = it.shell(command)
            if (response.exitCode == 0) {
                // app_process is detached through nohup/setsid.  On slower Samsung
                // builds it can take several seconds before the Binder is published;
                // treating the successful pair as a failure after three seconds made
                // the setup appear to fail even though the server came up shortly
                // afterwards.
                repeat(100) {
                    if (DistributionPrivilegeRuntime.available) {
                        return EmbeddedPrivilegeContract.StartResult(
                            started = true,
                            endpoint = "$connectedHost:${endpoint.port}",
                        )
                    }
                    delay(100)
                }
            }
            return EmbeddedPrivilegeContract.StartResult(
                started = false,
                endpoint = endpoint.label(),
                detail = response.allOutput.trim().ifEmpty {
                    if (response.exitCode == 0) "The embedded service did not publish its Binder" else null
                },
            )
        }
    }

    private suspend fun discover(
        context: Context,
        serviceType: String,
        timeoutMillis: Long = 15_000,
    ): Endpoint {
        return withTimeout(timeoutMillis) {
            suspendCancellableCoroutine { continuation ->
                val manager = context.applicationContext.getSystemService(NsdManager::class.java)
                var stopped = false
                lateinit var listener: NsdManager.DiscoveryListener
                fun stop() {
                    if (stopped) return
                    stopped = true
                    runCatching { manager.stopServiceDiscovery(listener) }
                }
                listener = object : NsdManager.DiscoveryListener {
                    override fun onDiscoveryStarted(type: String) = Unit
                    override fun onDiscoveryStopped(type: String) = Unit
                    override fun onServiceLost(service: NsdServiceInfo) = Unit
                    override fun onStartDiscoveryFailed(type: String, errorCode: Int) {
                        stop()
                        if (continuation.isActive) continuation.resumeWith(Result.failure(
                            IllegalStateException("Wireless debugging discovery failed ($errorCode)"),
                        ))
                    }
                    override fun onStopDiscoveryFailed(type: String, errorCode: Int) = Unit
                    override fun onServiceFound(service: NsdServiceInfo) {
                        manager.resolveService(service, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) = Unit
                            @Suppress("DEPRECATION")
                            override fun onServiceResolved(info: NsdServiceInfo) {
                                if (info.port !in 1..65535 || !continuation.isActive) return
                                // Pairing endpoints are deliberately not connected until the
                                // code is supplied. A TCP probe here makes some OEM builds
                                // look absent even though mDNS correctly found the service.
                                stop()
                                continuation.resume(Endpoint(info.host?.hostAddress, info.port))
                            }
                        })
                    }
                }
                continuation.invokeOnCancellation { stop() }
                manager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
            }
        }
    }

    private data class Endpoint(val resolvedHost: String?, val port: Int) {
        fun connectionHosts(): List<String> = buildList {
            add("127.0.0.1")
            resolvedHost?.takeIf { it.isNotBlank() }?.let(::add)
            runCatching { NetworkInterface.getNetworkInterfaces().toList() }
                .getOrDefault(emptyList())
                .asSequence()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList().asSequence() }
                .map { it.hostAddress }
                .filter { it.isNotBlank() && !it.contains(':') }
                .forEach(::add)
        }.distinct()

        fun label(): String = "${resolvedHost ?: "local"}:$port"
    }
}
