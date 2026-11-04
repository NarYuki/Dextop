package moe.n4tsu.dextop.linux

import java.io.File

/**
 * System information Android keeps from Linux programs, rebuilt from what it does allow, for
 * every program that reads it (system monitors such as btop, htop and glances, fastfetch and
 * neofetch, Python's psutil, upower clients…), in both execution modes.
 *
 *  - /sys/class/net: SELinux denies every file to the shell and to apps. Each interface's
 *    counters come from /proc/net/dev where it is readable (the privileged mode), else the
 *    device's totals from Android's TrafficStats as one interface. Refreshed every second.
 *  - /sys/class/power_supply: denied to both. BAT0 and AC from Android's battery state
 *    (BatteryManager in the Android mode, `dumpsys battery` in the privileged one), every 10 s.
 *  - /sys/firmware/devicetree/base/model: denied; the device's name.
 *  - /proc/swaps and /proc/sys/kernel/hostname: missing; a header-only swap list and a name.
 *
 * Only what the running process cannot read is replaced; proot binds the stand-ins over the
 * real paths, so programs that bypass libc see them too.
 */
internal object LinuxSysStandIns {
    private val INTERFACE = Regex("[A-Za-z0-9_.:@-]{1,32}")
    private val COUNTERS = listOf("rx_bytes", "rx_packets", "rx_errors", "rx_dropped", "rx_fifo_errors", "rx_frame_errors",
        "rx_compressed", "multicast", "tx_bytes", "tx_packets", "tx_errors", "tx_dropped", "tx_fifo_errors", "collisions",
        "tx_carrier_errors", "tx_compressed")

    private val base: File get() = File(LinuxPaths.base, "sys-standins")
    private val net get() = File(base, "net")
    private val power get() = File(base, "power_supply")

    /** Set by the service in the Android mode, for the battery and traffic APIs. */
    @Volatile var context: android.content.Context? = null

    @Volatile private var refresher: Thread? = null
    @Volatile private var netLive = false
    @Volatile private var powerLive = false

    private fun readable(path: String) = runCatching { File(path).inputStream().use { it.read() } }.isSuccess
    private fun listable(path: String) = File(path).list() != null

    /** The proot binds ("stand-in:path") for what is denied or missing here. */
    @Synchronized
    fun binds(): List<String> = buildList {
        netLive = !readable("/sys/class/net/lo/statistics/rx_bytes")
        if (netLive && runCatching { writeNet() }.isSuccess) add("${net.absolutePath}:/sys/class/net")
        powerLive = !listable("/sys/class/power_supply") || File("/sys/class/power_supply").list().isNullOrEmpty()
        if (powerLive && runCatching { writePower() }.isSuccess) add("${power.absolutePath}:/sys/class/power_supply")
        static("/sys/firmware/devicetree/base/model", "model", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\u0000")
        static("/proc/swaps", "swaps", "Filename\t\t\t\tType\t\tSize\t\tUsed\t\tPriority\n")
        static("/proc/sys/kernel/hostname", "hostname", "localhost\n")
        if (netLive || powerLive) start()
    }

    /** A fixed stand-in for [path] when it cannot be read. */
    private fun MutableList<String>.static(path: String, name: String, text: String) {
        if (readable(path)) return
        val file = File(base, "static/$name")
        runCatching { file.parentFile?.mkdirs(); replace(file, text) }.onSuccess { add("${file.absolutePath}:$path") }
    }

    private fun start() {
        if (refresher?.isAlive == true) return
        refresher = Thread({
            var tick = 0
            while (true) {
                if (netLive) runCatching { writeNet() }
                if (powerLive && tick % 10 == 0) runCatching { writePower() }
                tick++
                Thread.sleep(1_000)
            }
        }, "DextopSysStandIns").apply { isDaemon = true; start() }
    }

    // region network
    /** /proc/net/dev: interface → its 16 counters, in [COUNTERS] order. */
    fun parseNetDev(text: String): Map<String, List<String>> = text.lineSequence().drop(2).mapNotNull { line ->
        val name = line.substringBefore(':').trim()
        val values = line.substringAfter(':', "").trim().split(Regex("\\s+"))
        if (!INTERFACE.matches(name) || values.size < COUNTERS.size) null else name to values.take(COUNTERS.size)
    }.toMap()

    private fun interfaces(): Map<String, List<String>> {
        if (readable("/proc/net/dev")) return parseNetDev(File("/proc/net/dev").readText())
        // The Android mode: the device's totals, as one interface.
        val zero = List(COUNTERS.size) { "0" }.toMutableList()
        val rx = android.net.TrafficStats.getTotalRxBytes().coerceAtLeast(0)
        val tx = android.net.TrafficStats.getTotalTxBytes().coerceAtLeast(0)
        val rxp = android.net.TrafficStats.getTotalRxPackets().coerceAtLeast(0)
        val txp = android.net.TrafficStats.getTotalTxPackets().coerceAtLeast(0)
        zero[0] = rx.toString(); zero[1] = rxp.toString(); zero[8] = tx.toString(); zero[9] = txp.toString()
        return mapOf("lo" to List(COUNTERS.size) { "0" }, "wlan0" to zero)
    }

    private fun writeNet() {
        val all = interfaces()
        val root = net.apply { mkdirs() }
        all.forEach { (name, values) ->
            val dir = File(root, name)
            val statistics = File(dir, "statistics").apply { mkdirs() }
            COUNTERS.forEachIndexed { index, counter -> replace(File(statistics, counter), values[index] + "\n") }
            if (!File(dir, "operstate").isFile) {
                replace(File(dir, "operstate"), if (name == "lo") "unknown\n" else "up\n")
                replace(File(dir, "carrier"), "1\n")
                replace(File(dir, "address"), "00:00:00:00:00:00\n")
                replace(File(dir, "mtu"), if (name == "lo") "65536\n" else "1500\n")
                replace(File(dir, "type"), if (name == "lo") "772\n" else "1\n")
                replace(File(dir, "flags"), "0x1003\n")
            }
        }
        root.listFiles().orEmpty().filter { it.isDirectory && it.name !in all }.forEach { LinuxRootfsArchive.deleteTree(it) }
    }
    // endregion

    // region battery
    /** Android's battery state: level (0–100), status (BatteryManager codes), plugged, temperature (0.1 °C), voltage (mV), health. */
    data class Battery(val level: Int, val status: Int, val plugged: Boolean, val temperature: Int, val voltage: Int, val health: Int, val present: Boolean)

    /** `dumpsys battery` (the privileged mode) as a [Battery]. */
    fun parseDumpsys(text: String): Battery? {
        fun value(key: String) = Regex("(?m)^\\s*${Regex.escape(key)}:\\s*(.+)$").find(text)?.groupValues?.get(1)?.trim()
        val level = value("level")?.toIntOrNull() ?: return null
        val scale = value("scale")?.toIntOrNull()?.takeIf { it > 0 } ?: 100
        val plugged = listOf("AC powered", "USB powered", "Wireless powered", "Dock powered").any { value(it) == "true" }
        return Battery(level * 100 / scale, value("status")?.toIntOrNull() ?: 1, plugged, value("temperature")?.toIntOrNull() ?: 0,
            value("voltage")?.toIntOrNull() ?: 0, value("health")?.toIntOrNull() ?: 1, value("present") != "false")
    }

    private fun battery(): Battery? {
        context?.let { context ->
            val intent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)) ?: return null
            val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100).takeIf { it > 0 } ?: 100
            return Battery(intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, 0) * 100 / scale,
                intent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, 1),
                intent.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) != 0,
                intent.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0),
                intent.getIntExtra(android.os.BatteryManager.EXTRA_VOLTAGE, 0),
                intent.getIntExtra(android.os.BatteryManager.EXTRA_HEALTH, 1),
                intent.getBooleanExtra(android.os.BatteryManager.EXTRA_PRESENT, true))
        }
        val process = ProcessBuilder("/system/bin/dumpsys", "battery").redirectErrorStream(true).start()
        val text = process.inputStream.bufferedReader().readText()
        process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
        return parseDumpsys(text)
    }

    private fun writePower() {
        val state = battery() ?: return
        val status = when (state.status) {
            2 -> "Charging"; 3 -> "Discharging"; 4 -> "Not charging"; 5 -> "Full"; else -> "Unknown"
        }
        val health = when (state.health) {
            2 -> "Good"; 3 -> "Overheat"; 4 -> "Dead"; 5 -> "Over voltage"; 7 -> "Cold"; else -> "Unknown"
        }
        val bat = File(power, "BAT0").apply { mkdirs() }
        replace(File(bat, "type"), "Battery\n")
        replace(File(bat, "present"), if (state.present) "1\n" else "0\n")
        replace(File(bat, "status"), "$status\n")
        replace(File(bat, "capacity"), "${state.level.coerceIn(0, 100)}\n")
        replace(File(bat, "health"), "$health\n")
        replace(File(bat, "technology"), "Li-ion\n")
        replace(File(bat, "temp"), "${state.temperature}\n")
        replace(File(bat, "voltage_now"), "${state.voltage * 1000L}\n")
        replace(File(bat, "scope"), "System\n")
        replace(File(bat, "uevent"), "POWER_SUPPLY_NAME=BAT0\nPOWER_SUPPLY_TYPE=Battery\nPOWER_SUPPLY_STATUS=$status\n" +
            "POWER_SUPPLY_PRESENT=${if (state.present) 1 else 0}\nPOWER_SUPPLY_CAPACITY=${state.level.coerceIn(0, 100)}\n" +
            "POWER_SUPPLY_HEALTH=$health\nPOWER_SUPPLY_TECHNOLOGY=Li-ion\nPOWER_SUPPLY_TEMP=${state.temperature}\n" +
            "POWER_SUPPLY_VOLTAGE_NOW=${state.voltage * 1000L}\n")
        val ac = File(power, "AC").apply { mkdirs() }
        replace(File(ac, "type"), "Mains\n")
        replace(File(ac, "online"), if (state.plugged) "1\n" else "0\n")
        replace(File(ac, "uevent"), "POWER_SUPPLY_NAME=AC\nPOWER_SUPPLY_TYPE=Mains\nPOWER_SUPPLY_ONLINE=${if (state.plugged) 1 else 0}\n")
    }
    // endregion

    private fun replace(file: File, text: String) {
        if (file.isFile && file.length() == text.toByteArray().size.toLong() && file.readText() == text) return
        val staged = File(file.parentFile, ".${file.name}.staged")
        staged.writeText(text)
        staged.setReadable(true, false)
        if (!staged.renameTo(file)) staged.delete()
    }
}
