package moe.n4tsu.dextop.linux

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import moe.n4tsu.dextop.PrivilegedAccess
import moe.n4tsu.dextop.plasma.PlasmaSystemControls
import org.json.JSONArray
import org.json.JSONObject
import java.net.NetworkInterface

internal object LinuxNetworkMonitor {
    private var callback: ConnectivityManager.NetworkCallback? = null
    private var publish: ((String) -> Unit)? = null
    private var active: Network? = null
    private var context: Context? = null
    private val worker = java.util.concurrent.Executors.newSingleThreadScheduledExecutor { Thread(it, "LinuxNetworkState") }
    private var polling = false
    private var scanRequested: () -> Boolean = { false }
    private var lastScanAt = 0L
    // Worker thread only: the last scan results read, and the last state published.
    private var scanPoints: List<LinuxWifiScan.AccessPoint>? = null
    private var scanReadAt = 0L
    private var lastPublished: String? = null
    /** The newest state to fill in and publish; a queued job takes whichever is newest when it runs. */
    private var pendingValue: Pair<JSONObject, Network?>? = null

    @Synchronized fun start(context: Context, scanRequested: () -> Boolean = { false }, publish: (String) -> Unit) {
        this.publish = publish
        // A new listener has seen nothing yet.
        worker.execute { lastPublished = null }
        this.scanRequested = scanRequested
        this.context = context.applicationContext
        val manager = context.getSystemService(ConnectivityManager::class.java)
        if (callback == null) {
            val observer = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = synchronized(this@LinuxNetworkMonitor) { active = network }
                override fun onLinkPropertiesChanged(network: Network, properties: LinkProperties) {
                    synchronized(this@LinuxNetworkMonitor) { if (network == active) send(properties) }
                }
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    synchronized(this@LinuxNetworkMonitor) { if (network == active) send(manager.getLinkProperties(network)) }
                }
                override fun onLost(network: Network) {
                    synchronized(this@LinuxNetworkMonitor) { if (network == active) { active = null; send(null) } }
                }
            }
            manager.registerDefaultNetworkCallback(observer)
            callback = observer
        }
        active = manager.activeNetwork
        send(active?.let(manager::getLinkProperties))
        if (!polling) {
            polling = true
            worker.scheduleWithFixedDelay({ runCatching {
                synchronized(this@LinuxNetworkMonitor) { send(active?.let(manager::getLinkProperties)) }
            } }, 5, 5, java.util.concurrent.TimeUnit.SECONDS)
        }
    }

    private fun send(properties: LinkProperties?) {
        val name = properties?.interfaceName.orEmpty()
        val index = runCatching { NetworkInterface.getByName(name)?.index }.getOrNull()?.takeIf { it > 1 } ?: 2
        val value = JSONObject().put("name", name).put("index", index).put("mtu", properties?.mtu ?: 1500)
            .put("addresses", JSONArray().apply {
                properties?.linkAddresses.orEmpty().take(16).forEach {
                    put(JSONObject().put("address", it.address.hostAddress?.substringBefore('%')).put("prefix", it.prefixLength))
                }
            }).put("gateways", JSONArray().apply {
                properties?.routes.orEmpty().filter { it.isDefaultRoute }.mapNotNull { it.gateway }
                    .filterNot { it.isAnyLocalAddress }.distinctBy { it is java.net.Inet4Address }
                    .take(2).forEach { put(it.hostAddress?.substringBefore('%')) }
            }).put("dns", JSONArray().apply {
                properties?.dnsServers.orEmpty().filterNot { it.isLinkLocalAddress || it.isAnyLocalAddress }
                    .forEach { put(it.hostAddress?.substringBefore('%')) }
            })
        val context = context ?: return
        // Callbacks arrive in bursts (each signal change is one): one job per burst, and it
        // fills in the newest state.
        val queued = pendingValue != null
        pendingValue = value to active
        if (queued) return
        worker.execute {
            val (value, network) = synchronized(this@LinuxNetworkMonitor) {
                pendingValue.also { pendingValue = null }
            } ?: return@execute
            val interfaceName = value.optString("name")
            runCatching inner@{
                val manager = context.getSystemService(ConnectivityManager::class.java)
                if (network != manager.activeNetwork) return@inner
                val access = PrivilegedAccess("LinuxNetworkState")
                // Android mode without the privileged service: only what ordinary app APIs report.
                val privileged = LinuxExecutionMode.privilegeAllowed(context)
                fun run(vararg args: String) = if (privileged) access.execute(*args) else null
                var wifiStatus: String? = null
                val controls = PlasmaSystemControls(context) { args ->
                    run(*args)?.takeIf { it.succeeded }?.output.also {
                        if (args.toList() == listOf("cmd", "wifi", "status")) wifiStatus = it
                    }
                }
                val status = controls.networkStatus()
                val wifi = if (status.transport == PlasmaSystemControls.Transport.WIFI) controls.wifiConnection() else null
                val cellular = status.transport == PlasmaSystemControls.Transport.CELLULAR
                val wireless = cellular || status.transport == PlasmaSystemControls.Transport.WIFI
                val caps = network?.let(manager::getNetworkCapabilities)
                val rssi = Regex("RSSI: (-?\\d+)").find(wifiStatus.orEmpty())?.groupValues?.get(1)?.toIntOrNull()
                val dataSub = SubscriptionManager.getDefaultDataSubscriptionId()
                val phone = runCatching { context.getSystemService(TelephonyManager::class.java).createForSubscriptionId(dataSub) }.getOrNull()
                val carrier = if (cellular) runCatching { phone?.networkOperatorName?.takeIf { it.isNotBlank() } }.getOrNull() ?: status.carrierName else null
                val mobileLevel = if (cellular) runCatching { phone?.signalStrength?.level?.div(4f) }.getOrNull()
                    ?: run {
                        val slot = SubscriptionManager.getSlotIndex(dataSub)
                        val registry = run("sh", "-c", "dumpsys telephony.registry | grep -E 'Phone Id=|mSignalStrength='")
                        if (registry?.succeeded == true) LinuxMobileSignal.level(registry.output, slot) else null
                    } else null
                val level = if (cellular) mobileLevel else rssi?.let { ((it + 100) / 45f).coerceIn(0f, 1f) }
                    ?: caps?.signalStrength?.takeIf { it != NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED }
                        ?.let { ((it + 100) / 45f).coerceIn(0f, 1f) }
                val scanRequest = runCatching { scanRequested() }.getOrDefault(false)
                val now = android.os.SystemClock.elapsedRealtime()
                if (status.wifiEnabled && (scanRequest || now - lastScanAt > 30_000) && now - lastScanAt > 5_000) {
                    if (run("cmd", "wifi", "start-scan")?.succeeded == true) lastScanAt = now
                }
                // Results change only after a scan: read them for a while after one, else reuse them.
                val readScan = status.wifiEnabled && (scanPoints == null || scanRequest || now - lastScanAt < 15_000 || now - scanReadAt > 60_000)
                val scan = if (readScan) run("cmd", "wifi", "list-scan-results") else null
                if (!status.wifiEnabled) scanPoints = null
                else if (scan != null) {
                    scanPoints = if (scan.succeeded) LinuxWifiScan.parse(scan.output) else null
                    scanReadAt = now
                }
                val points = scanPoints.orEmpty()
                val bssid = if (cellular) "02:00:00:00:00:01" else
                    Regex("BSSID: ([0-9a-fA-F:]{17})").find(wifiStatus.orEmpty())?.groupValues?.get(1)
                value.put("accessPoints", JSONArray().apply {
                    points.take(24).forEach { ap ->
                        put(JSONObject().put("ssid", ap.ssid).put("bssid", ap.bssid)
                            .put("frequency", ap.frequency).put("strength", ap.strength).put("capabilities", ap.capabilities))
                    }
                }).put("lastScan", lastScanAt).put("bssid", bssid)
                value.put("interface", interfaceName).put("connected", network != null)
                    .put("transport", if (wireless) "wifi" else if (network == null) "none" else "ethernet")
                    .put("sourceTransport", status.transport.name.lowercase())
                    .put("validated", status.internet).put("metered", caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == false)
                    .put("captivePortal", caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) == true)
                    .put("wifiEnabled", wireless || status.wifiEnabled).put("scanAllowed", status.wifiEnabled && scanPoints != null)
                    .put("ssid", if (cellular) carrier ?: moe.n4tsu.dextop.localText("uiMobileNetwork") else wifi?.ssid ?: "Wi-Fi")
                    .put("strength", ((level ?: 0f) * 100).toInt().coerceIn(0, 100))
                    .put("signalKnown", level != null).put("frequency", wifi?.frequencyMhz ?: 0)
                    .put("bitrate", (wifi?.linkSpeedMbps ?: 0) * 1000)
                // Unchanged states are not sent again: the periodic check mostly finds nothing new.
                val text = value.toString()
                if (text == lastPublished) return@inner
                synchronized(this@LinuxNetworkMonitor) {
                    if (active == network) {
                        publish?.invoke(text)
                        lastPublished = text
                    }
                }
            }.onFailure { android.util.Log.w("LinuxNetworkState", "Cannot publish Android network status", it) }
        }
    }
}

/** Fallback for devices which hide signal strength from ordinary app processes. */
internal object LinuxMobileSignal {
    fun level(registry: String, dataSlot: Int): Float? {
        if (dataSlot < 0) return null
        var slot = -1
        for (line in registry.lineSequence()) {
            Regex("Phone Id=(\\d+)").find(line)?.let { slot = it.groupValues[1].toInt() }
            if (slot != dataSlot || !line.contains("mSignalStrength=")) continue
            val primary = Regex("primary=(CellSignalStrength[A-Za-z]+)").find(line)?.groupValues?.get(1) ?: return null
            val entry = line.substringAfter("$primary:", "").substringBefore(",m")
            val level = Regex("(?:^| )level=([0-4])(?: |$)").find(entry)?.groupValues?.get(1)?.toInt() ?: return null
            return level / 4f
        }
        return null
    }
}

internal object LinuxWifiScan {
    data class AccessPoint(val bssid: String, val ssid: String, val frequency: Int, val strength: Int, val capabilities: String)
    fun parse(text: String): List<AccessPoint> = text.lineSequence().mapNotNull { line ->
        val match = Regex("""^\s*([0-9a-fA-F:]{17})\s+(\d+)\s+(-?\d+)\S*\s+>?[\d.]+\s+(.*?)\s+(\[.*)$""").find(line) ?: return@mapNotNull null
        val ssid = match.groupValues[4].trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        val frequency = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
        val rssi = match.groupValues[3].toIntOrNull() ?: return@mapNotNull null
        AccessPoint(match.groupValues[1].lowercase(), ssid, frequency,
            (((rssi + 100) / 45f).coerceIn(0f, 1f) * 100).toInt(), match.groupValues[5])
    }.distinctBy { it.bssid }.sortedByDescending { it.strength }.toList()
}
