package moe.n4tsu.dextop.plasma

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.telephony.TelephonyManager
import android.view.KeyEvent

/**
 * Reads and changes the phone's network, audio and power state for the
 * Plasma system tray.
 *
 * Reads use public APIs where Android allows it. Changes Android reserves
 * for the system (Wi-Fi, airplane mode, mobile data, Bluetooth, battery
 * saver, brightness) go through [command], the privileged shell. Every
 * method that may run a command blocks, so call those off the main thread.
 */
internal class PlasmaSystemControls(
    private val context: Context,
    /** Runs a privileged shell command; returns its output, or null on failure. */
    private val command: (Array<String>) -> String?,
) {
    private val resolver get() = context.contentResolver
    private val audio = context.getSystemService(AudioManager::class.java)
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)
    private val battery = context.getSystemService(BatteryManager::class.java)

    private fun run(vararg arguments: String): String? = command(arrayOf(*arguments))
    private fun globalInt(name: String) = runCatching { Settings.Global.getInt(resolver, name) }.getOrDefault(0)

    // region network
    enum class Transport { NONE, WIFI, CELLULAR, ETHERNET, OTHER }

    data class NetworkStatus(
        val transport: Transport,
        val internet: Boolean,
        val wifiEnabled: Boolean,
        /** 0..1, or null when not on Wi-Fi. */
        val wifiLevel: Float?,
        val cellularLevel: Float?,
        val mobileDataEnabled: Boolean,
        val airplaneMode: Boolean,
        val bluetoothEnabled: Boolean,
        val vpn: Boolean,
        /** A SIM is inserted and ready in at least one slot. */
        val simPresent: Boolean = false,
        /** Name of the network the default SIM is registered on, e.g. "KDDI". */
        val carrierName: String? = null,
    )

    data class WifiNetwork(
        val ssid: String,
        val level: Float,
        /** connect-network security keyword: open, owe, wpa2, wpa3 or wep. */
        val security: String,
        val saved: Boolean,
        val connected: Boolean,
    ) {
        val secured: Boolean get() = security != "open" && security != "owe"
    }

    data class WifiConnection(val ssid: String?, val ipAddress: String?, val linkSpeedMbps: Int?, val frequencyMhz: Int?)

    fun networkStatus(): NetworkStatus {
        val active = runCatching { connectivity.activeNetwork }.getOrNull()
        val caps = runCatching { connectivity.getNetworkCapabilities(active) }.getOrNull()
        val transport = when {
            caps == null -> Transport.NONE
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Transport.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Transport.CELLULAR
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Transport.ETHERNET
            else -> Transport.OTHER
        }
        val wifiLevel = if (transport == Transport.WIFI) {
            caps?.signalStrength?.takeIf { it != NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED }?.let(::rssiLevel) ?: 1f
        } else {
            null
        }
        val telephony = runCatching { context.getSystemService(TelephonyManager::class.java) }.getOrNull()
        val cellularLevel = runCatching { telephony?.signalStrength?.level?.let { it / 4f } }.getOrNull()
        val simPresent = runCatching {
            val slots = telephony?.activeModemCount ?: 0
            (0 until slots).any { telephony?.getSimState(it) == TelephonyManager.SIM_STATE_READY }
        }.getOrDefault(false)
        val carrier = runCatching {
            telephony?.networkOperatorName?.takeIf { it.isNotBlank() } ?: telephony?.simOperatorName?.takeIf { it.isNotBlank() }
        }.getOrNull()
        return NetworkStatus(
            transport = transport,
            internet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
            wifiEnabled = transport == Transport.WIFI ||
                runCatching { wifi.isWifiEnabled }.getOrNull() ?: (globalInt(Settings.Global.WIFI_ON) != 0),
            wifiLevel = wifiLevel,
            cellularLevel = cellularLevel,
            mobileDataEnabled = globalInt("mobile_data") == 1,
            airplaneMode = globalInt(Settings.Global.AIRPLANE_MODE_ON) == 1,
            bluetoothEnabled = globalInt(Settings.Global.BLUETOOTH_ON) == 1,
            vpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true,
            simPresent = simPresent,
            carrierName = carrier,
        )
    }

    /** Radio technology of the default SIM ("5G", "LTE", "3G"…), read from the telephony properties. */
    fun cellularNetworkType(): String? {
        val raw = run("getprop", "gsm.network.type")?.split(',')?.map { it.trim() }
            ?.firstOrNull { it.isNotEmpty() && !it.equals("Unknown", ignoreCase = true) } ?: return null
        return when (raw.uppercase()) {
            "NR", "NR_SA", "NR_NSA" -> "5G"
            "LTE", "LTE_CA" -> "LTE"
            "HSPAP", "HSPA", "HSDPA", "HSUPA", "UMTS", "TD_SCDMA", "EVDO_0", "EVDO_A", "EVDO_B", "EHRPD" -> "3G"
            "EDGE", "GPRS", "GSM", "CDMA", "1XRTT", "IDEN" -> "2G"
            "IWLAN" -> "Wi-Fi Calling"
            else -> raw
        }
    }

    private fun rssiLevel(rssi: Int): Float {
        val max = wifi.maxSignalLevel.coerceAtLeast(1)
        return wifi.calculateSignalLevel(rssi).coerceIn(0, max) / max.toFloat()
    }

    /** SSID, address and link details of the current Wi-Fi connection. */
    fun wifiConnection(): WifiConnection? {
        val status = run("cmd", "wifi", "status") ?: return null
        val info = status.lineSequence().firstOrNull { it.trimStart().startsWith("WifiInfo:") } ?: return null
        fun field(name: String) = Regex("$name: ([^,]+)").find(info)?.groupValues?.get(1)?.trim()
        val ssid = field("SSID")?.removeSurrounding("\"")?.takeIf { it.isNotEmpty() && it != "<unknown ssid>" }
        return WifiConnection(
            ssid = ssid,
            ipAddress = field("IP")?.removePrefix("/"),
            linkSpeedMbps = field("Link speed")?.removeSuffix("Mbps")?.toIntOrNull(),
            frequencyMhz = field("Frequency")?.removeSuffix("MHz")?.toIntOrNull(),
        )
    }

    /** Requests a scan, then returns visible networks strongest first, one row per SSID. */
    fun scanWifi(waitForResults: Boolean = true): List<WifiNetwork> {
        run("cmd", "wifi", "start-scan")
        if (waitForResults) SystemClock.sleep(2_500L)
        val connected = wifiConnection()?.ssid
        val saved = run("cmd", "wifi", "list-networks").orEmpty().lineSequence().drop(1).mapNotNull { line ->
            Regex("^\\s*\\d+\\s+(.+?)\\s{2,}\\S+\\s*$").find(line)?.groupValues?.get(1)
        }.toSet()
        val results = run("cmd", "wifi", "list-scan-results").orEmpty().lineSequence().drop(1).mapNotNull { line ->
            // BSSID  Frequency  RSSI(...)  Age  SSID  [Flags]
            val match = Regex("^\\s*\\S+\\s+\\d+\\s+(-?\\d+)\\S*\\s+[\\d.]+\\s+(.*?)\\s+(\\[.*)$").find(line)
                ?: return@mapNotNull null
            val ssid = match.groupValues[2].trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val rssi = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            WifiNetwork(ssid, rssiLevel(rssi), security(match.groupValues[3]), ssid in saved, ssid == connected)
        }
        return results.groupBy { it.ssid }.values.map { group -> group.maxBy { it.level } }
            .sortedWith(compareByDescending<WifiNetwork> { it.connected }.thenByDescending { it.saved }.thenByDescending { it.level })
    }

    private fun security(flags: String): String = when {
        "SAE" in flags -> "wpa3"
        "PSK" in flags -> "wpa2"
        "OWE" in flags -> "owe"
        "WEP" in flags -> "wep"
        "EAP" in flags -> "wpa2"
        else -> "open"
    }

    fun connectWifi(network: WifiNetwork, passphrase: String?): Boolean {
        val arguments = mutableListOf("cmd", "wifi", "connect-network", network.ssid, network.security)
        if (network.secured && !passphrase.isNullOrEmpty()) arguments += passphrase
        return run(*arguments.toTypedArray())?.contains("failed", ignoreCase = true) == false
    }

    fun setWifiEnabled(enabled: Boolean) = run("cmd", "wifi", "set-wifi-enabled", if (enabled) "enabled" else "disabled") != null
    fun setAirplaneMode(enabled: Boolean) = run("cmd", "connectivity", "airplane-mode", if (enabled) "enable" else "disable") != null
    fun setMobileData(enabled: Boolean) = run("svc", "data", if (enabled) "enable" else "disable") != null
    fun setBluetooth(enabled: Boolean) = run("cmd", "bluetooth_manager", if (enabled) "enable" else "disable") != null
    // endregion

    // region audio
    data class StreamVolume(val stream: Int, val level: Float, val muted: Boolean, val adjustable: Boolean)

    data class MediaInfo(val packageName: String, val title: String, val artist: String?, val playing: Boolean)

    val streams = listOf(
        AudioManager.STREAM_MUSIC, AudioManager.STREAM_RING, AudioManager.STREAM_NOTIFICATION, AudioManager.STREAM_ALARM,
    )

    fun streamVolume(stream: Int): StreamVolume {
        val max = audio.getStreamMaxVolume(stream).coerceAtLeast(1)
        val min = runCatching { audio.getStreamMinVolume(stream) }.getOrDefault(0)
        val current = audio.getStreamVolume(stream)
        return StreamVolume(
            stream,
            ((current - min) / (max - min).coerceAtLeast(1).toFloat()).coerceIn(0f, 1f),
            audio.isStreamMute(stream),
            !audio.isVolumeFixed,
        )
    }

    fun setStreamVolume(stream: Int, level: Float): Boolean = runCatching {
        val max = audio.getStreamMaxVolume(stream)
        val min = runCatching { audio.getStreamMinVolume(stream) }.getOrDefault(0)
        audio.setStreamVolume(stream, (min + level.coerceIn(0f, 1f) * (max - min)).toInt(), 0)
        true
    }.getOrDefault(false)

    fun toggleMute(stream: Int): Boolean = runCatching {
        audio.adjustStreamVolume(stream, AudioManager.ADJUST_TOGGLE_MUTE, 0)
        true
    }.getOrDefault(false)

    /** Name of the device media currently plays through, e.g. "Speaker" or a headset. */
    fun mediaOutput(): AudioDeviceInfo? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            audio.getAudioDevicesForAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()
            ).firstOrNull()?.let { device ->
                audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.type == device.type }
            }
        } else {
            null
        }
    }.getOrNull()

    /** The most relevant media session, read from `dumpsys media_session`. */
    fun nowPlaying(): MediaInfo? {
        val dump = run("dumpsys", "media_session") ?: return null
        val sessions = dump.split(Regex("\\n\\s*package=")).drop(1).mapNotNull { block ->
            val packageName = block.lineSequence().first().trim()
            val description = Regex("metadata: size=\\d+, description=(.*)").find(block)?.groupValues?.get(1)
                ?.split(", ")?.map { it.trim() }?.filter { it.isNotEmpty() && it != "null" }
                ?: return@mapNotNull null
            if (description.isEmpty()) return@mapNotNull null
            val state = Regex("state=PlaybackState \\{state=(?:[A-Z_]+\\()?(\\d+)").find(block)?.groupValues?.get(1)?.toIntOrNull()
            MediaInfo(packageName, description[0], description.getOrNull(1), state == 3)
        }
        return sessions.firstOrNull { it.playing } ?: sessions.firstOrNull()
    }

    fun mediaKey(keyCode: Int) {
        val now = SystemClock.uptimeMillis()
        runCatching {
            audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
            audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
        }
    }
    // endregion

    // region power
    enum class PowerSource { NONE, AC, USB, WIRELESS, DOCK }

    data class PowerStatus(
        val level: Float,
        val charging: Boolean,
        val full: Boolean,
        val source: PowerSource,
        val temperatureCelsius: Float?,
        val chargeTimeRemainingMs: Long?,
        val batterySaver: Boolean,
        val brightness: Float,
        val autoBrightness: Boolean,
    )

    fun powerStatus(): PowerStatus {
        val sticky = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        val scale = sticky?.getIntExtra(BatteryManager.EXTRA_SCALE, 100)?.coerceAtLeast(1) ?: 100
        val level = sticky?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)?.takeIf { it >= 0 }?.let { it / scale.toFloat() }
            ?: (battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100) / 100f)
        val status = sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val source = when (sticky?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
            BatteryManager.BATTERY_PLUGGED_AC -> PowerSource.AC
            BatteryManager.BATTERY_PLUGGED_USB -> PowerSource.USB
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> PowerSource.WIRELESS
            BatteryManager.BATTERY_PLUGGED_DOCK -> PowerSource.DOCK
            else -> PowerSource.NONE
        }
        val temperature = sticky?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            ?.takeIf { it != Int.MIN_VALUE }?.let { it / 10f }
        val brightness = runCatching { Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS) }.getOrDefault(128)
        return PowerStatus(
            level = level.coerceIn(0f, 1f),
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING || battery.isCharging,
            full = status == BatteryManager.BATTERY_STATUS_FULL,
            source = source,
            temperatureCelsius = temperature,
            chargeTimeRemainingMs = battery.computeChargeTimeRemaining().takeIf { it > 0 },
            batterySaver = power.isPowerSaveMode,
            brightness = (brightness / 255f).coerceIn(0f, 1f),
            autoBrightness = runCatching {
                Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE)
            }.getOrDefault(0) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC,
        )
    }

    fun setBatterySaver(enabled: Boolean) = run("cmd", "power", "set-mode", if (enabled) "1" else "0") != null

    fun setBrightness(level: Float) =
        run("settings", "put", "system", Settings.System.SCREEN_BRIGHTNESS, (level.coerceIn(0f, 1f) * 255).toInt().coerceAtLeast(1).toString()) != null

    fun setAutoBrightness(enabled: Boolean) =
        run("settings", "put", "system", Settings.System.SCREEN_BRIGHTNESS_MODE, if (enabled) "1" else "0") != null
    // endregion
}
