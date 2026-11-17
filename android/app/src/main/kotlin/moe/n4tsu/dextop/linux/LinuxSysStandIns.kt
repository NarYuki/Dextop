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
 *  - /proc/stat, /proc/uptime, /proc/loadavg: denied to apps; Dextop's own CPU time, uptime and
 *    run queue (LinuxCpuSampler), every second.
 *  - /proc/net/dev: denied to apps; the interfaces above as the kernel would list them, read
 *    through the guest library (linux_android_proc.c) since /proc/net is a per-process link.
 *  - /sys/class/hwmon: denied; one sensor per thermal zone Android lets apps read, every 2 s.
 *  - Directory listings of /sys (/sys, /sys/devices, /sys/class…): denied while what is inside
 *    stays readable by path. A directory of links to the entries that exist stands in for the
 *    listing (DIR_GUEST); the guest library opens it when the real one is refused.
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
    private val proc get() = File(base, "proc")
    private val hwmon get() = File(base, "hwmon")
    private val dirs get() = File(base, "dirs")

    /** Where the guest sees [base]: the guest library reads /proc/net/dev and listings from here. */
    const val GUEST = "/run/dextop-standins"

    /** Denied /sys directories and the entries probed in each (only those that exist are linked). */
    private val LISTINGS: Map<String, List<String>> = mapOf(
        "/sys" to listOf("block", "bus", "class", "dev", "devices", "firmware", "fs", "hypervisor", "kernel", "module", "power"),
        "/sys/devices" to listOf("system", "virtual", "platform", "software", "tracepoint", "breakpoint", "uprobe", "kprobe", "armv8_pmuv3", "soc0"),
        "/sys/devices/system" to listOf("cpu", "memory", "node", "clocksource", "clockevents", "container", "soc", "workqueue"),
        "/sys/devices/virtual" to listOf("thermal", "net", "misc", "tty", "mem", "block", "input", "powercap", "workqueue", "sound", "dmi"),
        "/sys/class" to listOf("net", "thermal", "power_supply", "hwmon", "block", "drm", "input", "tty", "misc", "mem", "sound",
            "graphics", "backlight", "leds", "rtc", "kgsl", "devfreq", "regulator", "remoteproc", "gpio", "pwm", "powercap",
            "watchdog", "bluetooth", "ieee80211", "rfkill", "video4linux", "media", "mmc_host", "scsi_host", "scsi_disk",
            "scsi_device", "dma_heap", "typec", "udc", "usb_role", "i2c-dev", "spi_master", "dmi", "nvme", "iommu"),
        "/sys/bus" to listOf("platform", "cpu", "usb", "pci", "i2c", "spi", "scsi", "mmc", "virtio", "nvmem", "soc", "workqueue",
            "clockevents", "clocksource", "event_source", "node", "memory", "container", "gpio", "iio"),
        "/sys/kernel" to listOf("mm", "debug", "tracing", "security", "config", "irq", "slab", "kexec_loaded", "osrelease"),
        "/sys/fs" to listOf("cgroup", "bpf", "ext4", "f2fs", "fuse", "pstore", "selinux"),
    )

    /** Set by the service in the Android mode, for the battery and traffic APIs. */
    @Volatile var context: android.content.Context? = null

    @Volatile private var refresher: Thread? = null
    @Volatile private var netLive = false
    @Volatile private var powerLive = false
    @Volatile private var procLive = false
    @Volatile private var netDevLive = false
    @Volatile private var hwmonZones: List<File> = emptyList()
    private val cpu by lazy { LinuxCpuSampler() }

    private fun readable(path: String) = runCatching { File(path).inputStream().use { it.read() } }.isSuccess
    private fun listable(path: String) = File(path).list() != null

    /** The proot binds ("stand-in:path") for what is denied or missing here. */
    @Synchronized
    fun binds(): List<String> = buildList {
        netLive = !readable("/sys/class/net/lo/statistics/rx_bytes")
        if (netLive && runCatching { writeNet() }.isSuccess) add("${net.absolutePath}:/sys/class/net")
        powerLive = !listable("/sys/class/power_supply") || File("/sys/class/power_supply").list().isNullOrEmpty()
        if (powerLive && runCatching { writePower() }.isSuccess) add("${power.absolutePath}:/sys/class/power_supply")
        procLive = !readable("/proc/stat")
        if (procLive && runCatching { writeProc() }.isSuccess) {
            for (name in listOf("stat", "uptime", "loadavg")) add("${File(proc, name).absolutePath}:/proc/$name")
        }
        netDevLive = !readable("/proc/net/dev")
        if (netDevLive) runCatching { writeNetDev() }
        if (!listable("/sys/class/hwmon")) {
            hwmonZones = runCatching { thermalZones() }.getOrDefault(emptyList())
            if (hwmonZones.isNotEmpty() && runCatching { writeHwmon() }.isSuccess) add("${hwmon.absolutePath}:/sys/class/hwmon")
        }
        runCatching { writeListings() }.onFailure { android.util.Log.w("DextopSysStandIns", "Directory stand-ins unavailable", it) }
        runCatching { writeDevice() }
        add("${base.absolutePath}:$GUEST")
        static("/sys/firmware/devicetree/base/model", "model", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\u0000")
        static("/proc/swaps", "swaps", "Filename\t\t\t\tType\t\tSize\t\tUsed\t\tPriority\n")
        static("/proc/sys/kernel/hostname", "hostname", "localhost\n")
        if (netLive || powerLive || procLive || netDevLive || hwmonZones.isNotEmpty()) start()
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
                if (netDevLive) runCatching { writeNetDev() }
                if (procLive) runCatching { writeProc() }
                if (hwmonZones.isNotEmpty() && tick % 2 == 0) runCatching { writeHwmon() }
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
        writeNetwork(net, interfaces()).forEach { (directory, error) ->
            android.util.Log.w("DextopSysStandIns", "Cannot remove stale network interface ${directory.name}; keeping current counters", error)
        }
    }

    /** Cleanup is optional: current counters must remain usable when an old interface disappears. */
    @Synchronized
    internal fun writeNetwork(root: File, all: Map<String, List<String>>,
                              removeStale: (File) -> Unit = LinuxRootfsArchive::deleteTree): List<Pair<File, Throwable>> {
        root.mkdirs()
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
        return root.listFiles().orEmpty().filter { it.isDirectory && it.name !in all }.mapNotNull { directory ->
            runCatching { removeStale(directory) }.exceptionOrNull()?.let { directory to it }
        }
    }
    // endregion

    /** /proc/net/dev in the kernel's layout. */
    internal fun netDev(all: Map<String, List<String>>): String = buildString {
        append("Inter-|   Receive                                                |  Transmit\n")
        append(" face |bytes    packets errs drop fifo frame compressed multicast|bytes    packets errs drop fifo colls carrier compressed\n")
        all.forEach { (name, values) -> append(name.padStart(6)).append(": ").append(values.joinToString(" ")).append('\n') }
    }

    private fun writeNetDev() {
        proc.mkdirs()
        replace(File(proc, "net_dev"), netDev(interfaces()))
    }
    // endregion

    // region cpu
    private fun writeProc() {
        val uptime = android.os.SystemClock.elapsedRealtime() / 1000.0
        cpu.sample(uptime, cpu.processes())
        proc.mkdirs()
        replace(File(proc, "stat"), cpu.stat((System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime()) / 1000))
        replace(File(proc, "uptime"), cpu.uptime(uptime))
        replace(File(proc, "loadavg"), cpu.loadavg())
    }
    // endregion

    // region sensors
    /** Thermal zones with a readable temperature, the CPU, GPU and battery ones first, one per kind. */
    private fun thermalZones(): List<File> {
        val zones = File("/sys/class/thermal").listFiles().orEmpty().filter { it.name.startsWith("thermal_zone") }
            .filter { readable("${it.path}/temp") && readable("${it.path}/type") }
        val seen = HashSet<String>()
        fun rank(type: String) = listOf("cpu", "gpu", "battery", "skin", "soc", "ddr").indexOfFirst { type.contains(it, true) }.let { if (it < 0) 99 else it }
        return zones.map { it to runCatching { File(it, "type").readText().trim() }.getOrDefault("") }
            .filter { (_, type) -> type.isNotEmpty() && seen.add(type) }
            .sortedWith(compareBy({ rank(it.second) }, { it.first.name.removePrefix("thermal_zone").toIntOrNull() ?: 0 }))
            .take(32).map { it.first }
    }

    private fun writeHwmon() {
        hwmonZones.forEachIndexed { index, zone ->
            val dir = File(hwmon, "hwmon$index").apply { mkdirs() }
            val type = runCatching { File(zone, "type").readText().trim() }.getOrDefault("thermal")
            val temp = runCatching { File(zone, "temp").readText().trim().toLong() }.getOrNull() ?: return@forEachIndexed
            replace(File(dir, "name"), type.replace(Regex("[^A-Za-z0-9_]"), "_") + "\n")
            replace(File(dir, "temp1_label"), "$type\n")
            // Zones report millidegrees, a few report degrees.
            replace(File(dir, "temp1_input"), "${if (temp in -100..200) temp * 1000 else temp}\n")
        }
    }
    // endregion

    private fun writeDevice() {
        val tablet = context?.resources?.configuration?.smallestScreenWidthDp?.let { it >= 600 } == true
        replace(File(base, "device.json"), org.json.JSONObject()
            .put("manufacturer", android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() })
            .put("model", android.os.Build.MODEL)
            .put("android", android.os.Build.VERSION.RELEASE)
            .put("chassis", if (tablet) "tablet" else "handset")
            .toString() + "\n")
    }

    // region listings
    private fun writeListings() {
        if (dirs.exists()) LinuxRootfsArchive.deleteTree(dirs)
        File(dirs, ".empty").mkdirs()
        LISTINGS.forEach { (path, names) ->
            if (listable(path) || !File(path).exists()) return@forEach
            // One flat directory per listing ("/sys/class" → "sys!class"): a listing's entries are links,
            // so a nested layout would put /sys/class's listing where /sys lists its "class" link.
            val dir = File(dirs, path.removePrefix("/").replace('/', '!')).apply { mkdirs() }
            names.filter { File(path, it).exists() }.forEach { name ->
                java.nio.file.Files.createSymbolicLink(File(dir, name).toPath(), java.nio.file.Paths.get("$path/$name"))
            }
        }
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
        try {
            staged.writeText(text)
            staged.setReadable(true, false)
            java.nio.file.Files.move(staged.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        } finally {
            staged.delete()
        }
    }
}
