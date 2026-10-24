package moe.n4tsu.dextop.linux

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.AttributionSource
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

/** Android owns the radio; guest BlueZ clients exchange bounded requests and snapshots. */
internal class LinuxBluetoothBridge(private val roots: () -> List<File>) {
    private val worker = Executors.newSingleThreadScheduledExecutor { Thread(it, "LinuxBluetooth") }
    private var controller: AndroidBluetooth? = null
    private var retryAt = 0L
    fun start() {
        worker.scheduleWithFixedDelay({
            runCatching {
                val installed = roots().filter { File(it, "usr/local/lib/dextop-gaming/dextop-bluez").isFile }
                if (installed.isEmpty()) return@runCatching
                if (controller == null && SystemClock.elapsedRealtime() >= retryAt) {
                    retryAt = SystemClock.elapsedRealtime() + 30_000
                    runCatching { AndroidBluetooth() }.onSuccess { controller = it }.onFailure { error ->
                        val unavailable = JSONObject().put("available", false).put("error", error.cause?.message ?: error.message).toString()
                        installed.forEach { write(File(it, "etc/dextop-bluetooth.json"), unavailable) }
                        Log.w("LinuxBluetooth", "Android Bluetooth access unavailable", error)
                    }
                }
                val backend = controller ?: return@runCatching
                installed.forEach { root ->
                    val directory = File(root, "etc/dextop-bluetooth-requests")
                    directory.mkdirs()
                    directory.listFiles().orEmpty().filter { it.name.matches(Regex("[a-f0-9]{32}[.]json")) }.take(32).forEach { request ->
                        val result = runCatching {
                            require(!Files.isSymbolicLink(request.toPath())) { "Bluetooth request must be a regular file" }
                            require(request.length() <= 8192) { "Bluetooth request is too large" }
                            val value = JSONObject(request.readText())
                            require(value.optLong("deadline") > SystemClock.elapsedRealtime()) { "Bluetooth request expired" }
                            backend.action(value)
                        }.getOrElse { error ->
                            JSONObject().put("ok", false).put("error", error.cause?.message ?: error.message ?: "Bluetooth request failed")
                                .put("errorName", if (error is SecurityException || error.cause is SecurityException) "NotAuthorized" else "Failed")
                        }
                        write(File(directory, request.name.removeSuffix(".json") + ".result"), result.toString())
                        request.delete()
                    }
                    // The caller normally removes results; also remove orphaned replies.
                    directory.listFiles().orEmpty().filter { it.name.endsWith(".result") && System.currentTimeMillis() - it.lastModified() > 180_000 }.forEach { it.delete() }
                }
                val state = backend.snapshot().toString()
                installed.forEach { write(File(it, "etc/dextop-bluetooth.json"), state) }
            }.onFailure { error -> Log.w("LinuxBluetooth", "Bluetooth bridge unavailable", error) }
        }, 0, 1, TimeUnit.SECONDS)
    }
    fun close() { worker.shutdownNow(); controller?.close() }
    private fun write(file: File, value: String) {
        if (file.isFile && file.readText() == value) return
        file.parentFile?.mkdirs()
        val temporary = File.createTempFile(".dextop-bt-", ".staged", file.parentFile)
        try {
            temporary.writeText(value)
            check(temporary.setReadable(true, false))
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally { temporary.delete() }
    }
}

/** Runs only in the shell service, using shell attribution and its Bluetooth permissions. */
@androidx.annotation.Keep
@android.annotation.SuppressLint("MissingPermission")
private class AndroidBluetooth {
    private fun <T> mainCall(block: () -> T): T {
        val main = Looper.getMainLooper() ?: error("No Android main looper")
        if (Looper.myLooper() == main) return block()
        val task = FutureTask(block)
        Handler(main).post(task)
        return task.get(8, TimeUnit.SECONDS)
    }
    private val context: Context = mainCall {
        val type = Class.forName("android.app.ActivityThread")
        val thread = type.getMethod("currentActivityThread").invoke(null) ?: type.getMethod("systemMain").invoke(null)
        val system = type.getMethod("getSystemContext").invoke(thread) as Context
        val shell = system.createPackageContext("com.android.shell", Context.CONTEXT_IGNORE_SECURITY)
        if (Build.VERSION.SDK_INT >= 31) {
            val source = AttributionSource.Builder(android.os.Process.myUid()).setPackageName("com.android.shell").build()
            object : ContextWrapper(shell) {
                override fun getOpPackageName() = "com.android.shell"
                override fun getAttributionSource() = source
                override fun createDeviceContext(deviceId: Int): Context = this
            }
        } else shell
    }
    private val adapter: BluetoothAdapter = mainCall {
        // app_process does not receive the Bluetooth module initializer used by app startup.
        if (Build.VERSION.SDK_INT >= 31) try {
            val initializer = Class.forName("android.bluetooth.BluetoothFrameworkInitializer")
            val manager = Class.forName("android.os.BluetoothServiceManager")
            if (initializer.getMethod("getBluetoothServiceManager").invoke(null) == null) {
                initializer.getMethod("setBluetoothServiceManager", manager).invoke(null, manager.getConstructor().newInstance())
            }
        } catch (_: ClassNotFoundException) {
            // Older Android releases initialize Bluetooth directly in the adapter.
        } catch (_: NoSuchMethodException) {
            // The module initializer is absent on some older vendor frameworks.
        }
        val result = if (Build.VERSION.SDK_INT >= 31) {
            // Recent Bluetooth modules take Context, earlier modules take AttributionSource.
            try {
                BluetoothAdapter::class.java.getMethod("createAdapter", Context::class.java).invoke(null, context)
            } catch (_: NoSuchMethodException) {
                BluetoothAdapter::class.java.getMethod("createAdapter", AttributionSource::class.java).invoke(null, context.attributionSource)
            }
        } else context.getSystemService(BluetoothManager::class.java).adapter
        requireNotNull(result as? BluetoothAdapter) { "Android Bluetooth adapter unavailable" }
    }
    private val discovered = ConcurrentHashMap<String, BluetoothDevice>()
    private val signal = ConcurrentHashMap<String, Int>()
    private val names = ConcurrentHashMap<String, String>()
    @Volatile private var leScanning = false
    private var classicOwned = false
    private fun attributed(device: BluetoothDevice): BluetoothDevice {
        return runCatching {
            val type = device.javaClass.getMethod("getAddressType").invoke(device) as Int
            require(type == 0 || type == 1)
            adapter.javaClass.getMethod("getRemoteLeDevice", String::class.java, Int::class.javaPrimitiveType)
                .invoke(adapter, device.address, type) as BluetoothDevice
        }.getOrElse { adapter.getRemoteDevice(device.address) }
    }
    private val leCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val address = result.device.address
            if (discovered.size < 128 || discovered.containsKey(address)) discovered[address] = attributed(result.device)
            signal[address] = result.rssi
            result.scanRecord?.deviceName?.let { names[address] = it }
        }
        override fun onBatchScanResults(results: MutableList<ScanResult>) { results.forEach { onScanResult(0, it) } }
        override fun onScanFailed(errorCode: Int) { leScanning = false; Log.w("LinuxBluetooth", "BLE discovery failed: $errorCode") }
    }
    private fun stopDiscovery() {
        if (classicOwned) adapter.cancelDiscovery()
        classicOwned = false
        if (leScanning) adapter.bluetoothLeScanner?.stopScan(leCallback)
        leScanning = false
    }
    private val ownedPairs = ConcurrentHashMap.newKeySet<String>()
    private val agentPairs = ConcurrentHashMap.newKeySet<String>()
    private val pairing = ConcurrentHashMap<String, JSONObject>()
    private val errors = ConcurrentHashMap<String, String>()
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            @Suppress("DEPRECATION")
            val received = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
            val address = received.address
            val device = attributed(received)
            if (discovered.size < 128 || discovered.containsKey(address)) discovered[address] = adapter.getRemoteDevice(address)
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> signal[address] = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                "android.bluetooth.device.action.PAIRING_REQUEST" -> if (address in agentPairs) {
                    pairing[address] = JSONObject().put("variant", intent.getIntExtra("android.bluetooth.device.extra.PAIRING_VARIANT", -1))
                        .put("key", intent.getIntExtra("android.bluetooth.device.extra.PAIRING_KEY", 0))
                    if (isOrderedBroadcast) abortBroadcast()
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    if (device.bondState != BluetoothDevice.BOND_BONDING) {
                        if (device.bondState == BluetoothDevice.BOND_NONE && address in ownedPairs) errors[address] = "Pairing was cancelled or rejected"
                        ownedPairs.remove(address); agentPairs.remove(address); pairing.remove(address)
                    }
                }
            }
        }
    }
    init {
        mainCall {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                addAction("android.bluetooth.device.action.PAIRING_REQUEST")
                priority = 1000
            }
            if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, null, Handler(Looper.getMainLooper()), Context.RECEIVER_EXPORTED)
            else context.registerReceiver(receiver, filter, null, Handler(Looper.getMainLooper()))
        }
    }
    fun close() {
        agentPairs.forEach { address -> runCatching { adapter.getRemoteDevice(address).javaClass.getMethod("cancelBondProcess").invoke(adapter.getRemoteDevice(address)) } }
        runCatching { stopDiscovery() }
        runCatching { context.unregisterReceiver(receiver) }
    }
    fun snapshot(): JSONObject {
        if (!adapter.isEnabled) { leScanning = false; classicOwned = false }
        val devices = LinkedHashMap<String, BluetoothDevice>()
        adapter.bondedDevices.orEmpty().forEach { devices[it.address] = it }
        discovered.values.take(64).forEach { devices.putIfAbsent(it.address, it) }
        return JSONObject().put("available", true).put("powered", adapter.isEnabled)
            .put("discovering", adapter.isDiscovering || leScanning).put("address", adapter.address)
            .put("name", adapter.name ?: "Android Bluetooth").put("devices", JSONArray().apply {
                devices.values.forEach { device ->
                    val connected = runCatching { device.javaClass.getMethod("isConnected").invoke(device) as Boolean }.getOrDefault(false)
                    val value = JSONObject().put("address", device.address).put("name", device.name ?: names[device.address] ?: device.address)
                        .put("alias", if (Build.VERSION.SDK_INT >= 30) device.alias ?: device.name ?: device.address else device.name ?: device.address)
                        .put("paired", device.bondState == BluetoothDevice.BOND_BONDED).put("bondState", device.bondState)
                        .put("connected", connected).put("class", device.bluetoothClass?.deviceClass ?: 0)
                        .put("uuids", JSONArray().apply { device.uuids.orEmpty().forEach { put(it.uuid.toString()) } })
                    signal[device.address]?.takeIf { it != Short.MIN_VALUE.toInt() }?.let { value.put("rssi", it) }
                    pairing[device.address]?.let { value.put("pairing", it) }
                    errors[device.address]?.let { value.put("pairingError", it) }
                    put(value)
                }
            })
    }
    fun action(request: JSONObject): JSONObject {
        fun success(wait: String? = null) = JSONObject().put("ok", true).apply { wait?.let { put("waitFor", it) } }
        when (val action = request.getString("action")) {
            "power" -> {
                val enable = request.getBoolean("value")
                val accepted = adapter.javaClass.getMethod(if (enable) "enable" else "disable").invoke(adapter) as Boolean
                check(accepted) { "Android rejected Bluetooth power change" }; return success()
            }
            "startDiscovery" -> {
                val classic = adapter.isDiscovering || adapter.startDiscovery().also { classicOwned = it }
                var lowEnergy = leScanning
                if (!leScanning) runCatching {
                    val scanner = adapter.bluetoothLeScanner ?: error("Bluetooth LE scanner unavailable")
                    leScanning = true
                    scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build(), leCallback)
                    lowEnergy = true
                }.onFailure { leScanning = false; Log.w("LinuxBluetooth", "Cannot start BLE discovery", it) }
                check(classic || lowEnergy) { "Android could not start discovery" }
                return success()
            }
            "stopDiscovery" -> { stopDiscovery(); return success() }
            "adapterAlias" -> { check(adapter.setName(request.getString("value").take(80))) { "Android rejected adapter name" }; return success() }
            else -> {
                val address = request.getString("address").uppercase()
                require(BluetoothAdapter.checkBluetoothAddress(address)) { "Invalid Bluetooth address" }
                val device = discovered[address] ?: adapter.getRemoteDevice(address)
                discovered[address] = device
                when (action) {
                    "pair" -> {
                        if (device.bondState == BluetoothDevice.BOND_BONDED) return success()
                        errors.remove(address); ownedPairs.add(address)
                        if (request.optBoolean("agent")) agentPairs.add(address)
                        if (!device.createBond()) { ownedPairs.remove(address); agentPairs.remove(address); error("Android rejected pairing") }
                        return success("paired")
                    }
                    "connect", "disconnect" -> {
                        val status = device.javaClass.getMethod(action).invoke(device)
                        check(status !is Int || status == 0) { "Android Bluetooth $action failed ($status)" }
                        return success(if (action == "connect") "connected" else "disconnected")
                    }
                    "remove" -> { check(device.javaClass.getMethod("removeBond").invoke(device) == true) { "Android rejected removing the bond" }; return success("unpaired") }
                    "cancelPairing" -> { device.javaClass.getMethod("cancelBondProcess").invoke(device); return success() }
                    "confirm" -> { check(device.javaClass.getMethod("setPairingConfirmation", Boolean::class.javaPrimitiveType).invoke(device, request.getBoolean("value")) == true); return success() }
                    "pin" -> { check(device.javaClass.getMethod("setPin", ByteArray::class.java).invoke(device, request.getString("value").toByteArray(Charsets.UTF_8)) == true); return success() }
                    "passkey" -> { check(device.javaClass.getMethod("setPasskey", Int::class.javaPrimitiveType).invoke(device, request.getInt("value")) == true); return success() }
                    "trust" -> { check(request.getBoolean("value") && device.bondState == BluetoothDevice.BOND_BONDED) { "Android trusts bonded devices; unpair to revoke trust" }; return success() }
                    "alias" -> { val result = device.javaClass.getMethod("setAlias", String::class.java).invoke(device, request.getString("value").take(80)); check(result !is Int || result == 0); return success() }
                    else -> error("Unsupported Bluetooth action: $action")
                }
            }
        }
    }
}
