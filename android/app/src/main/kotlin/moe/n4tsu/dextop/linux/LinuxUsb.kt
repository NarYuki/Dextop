package moe.n4tsu.dextop.linux

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager

/**
 * USB devices for Linux: which ones Linux may use and Android's access to them.
 *
 * Android owns USB; even the shell user cannot open /dev/bus/usb. Dextop holds the device
 * through UsbManager and hands it to Linux. Android's permission is given either by the
 * privileged service (shell holds MANAGE_USB: every device at once, or each one when it is
 * plugged in, as the user chooses) or, in the Android mode, by Android's own dialog.
 */
internal object LinuxUsb {
    private const val PREFS = "linux-usb"
    private const val ACTION_PERMISSION = "moe.n4tsu.dextop.linux.USB_PERMISSION"
    const val POLICY_ALL = "all"
    const val POLICY_EACH = "each"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun manager(context: Context) = context.getSystemService(UsbManager::class.java)

    /** A device's identity across plugging: vendor, product and serial when Android shows it. */
    fun key(device: UsbDevice): String = "%04x:%04x:%s".format(device.vendorId, device.productId,
        runCatching { device.serialNumber }.getOrNull().orEmpty())

    /** Privileged mode: grant every device ("all") or ask when each is plugged in ("each"). */
    fun policy(context: Context): String = prefs(context).getString("policy", POLICY_EACH) ?: POLICY_EACH
    fun setPolicy(context: Context, value: String) {
        require(value == POLICY_ALL || value == POLICY_EACH)
        prefs(context).edit().putString("policy", value).apply()
        if (value == POLICY_ALL && privileged(context)) devices(context).values.forEach { grant(context, it) }
    }

    fun enabled(context: Context, device: UsbDevice) = key(device) in prefs(context).getStringSet("enabled", emptySet()).orEmpty()
    private fun setEnabled(context: Context, device: UsbDevice, value: Boolean) {
        val set = prefs(context).getStringSet("enabled", emptySet()).orEmpty().toMutableSet()
        if (value) set += key(device) else set -= key(device)
        prefs(context).edit().putStringSet("enabled", set).apply()
    }

    private fun privileged(context: Context) = !LinuxExecutionMode.android(context) && LinuxContainerClient.privilegeAvailable(context)
    private fun devices(context: Context): Map<String, UsbDevice> = manager(context)?.deviceList.orEmpty()

    /** What the settings list shows for each connected device. */
    fun list(context: Context): List<Map<String, Any?>> = devices(context).values.map { device ->
        mapOf(
            "id" to device.deviceName,
            "name" to (listOfNotNull(device.manufacturerName, device.productName).joinToString(" ").ifBlank {
                "USB %04x:%04x".format(device.vendorId, device.productId) }),
            "ids" to "%04x:%04x".format(device.vendorId, device.productId),
            "kind" to kind(device),
            "permission" to (manager(context)?.hasPermission(device) == true),
            "enabled" to enabled(context, device),
        )
    }

    /** "serial" for USB-serial adapters (CDC-ACM and the common converter chips), otherwise "usb". */
    fun kind(device: UsbDevice): String {
        val serialVendors = setOf(0x0403 /* FTDI */, 0x10c4 /* Silicon Labs */, 0x1a86 /* WCH */, 0x067b /* Prolific */)
        val acm = (0 until device.interfaceCount).any { device.getInterface(it).interfaceClass == UsbConstants.USB_CLASS_COMM }
        return if (acm || device.vendorId in serialVendors) "serial" else "usb"
    }

    /** Linux may use the device ([enabled]); access is asked for or granted as the mode allows. */
    fun setForLinux(context: Context, id: String, value: Boolean) {
        val device = devices(context)[id] ?: throw IllegalStateException("The USB device is no longer connected")
        setEnabled(context, device, value)
        // Turning it on in settings is the user's consent: no further dialog where privilege allows.
        if (value && manager(context)?.hasPermission(device) != true) grant(context, device, silent = true)
    }

    /**
     * Android's access to [device]: through the privileged service when [silent] and the mode
     * has privilege, otherwise Android's dialog ("each" policy, Android mode).
     */
    fun grant(context: Context, device: UsbDevice, silent: Boolean = policy(context) == POLICY_ALL) {
        val manager = manager(context) ?: return
        if (manager.hasPermission(device)) return
        if (silent && privileged(context) && runCatching {
                val usb = moe.n4tsu.dextop.PrivilegedAccess("DextopLinuxUsb").service("usb", "android.hardware.usb.IUsbManager")
                usb.javaClass.getMethod("grantDevicePermission", UsbDevice::class.java, Int::class.javaPrimitiveType)
                    .invoke(usb, device, android.os.Process.myUid())
            }.isSuccess && manager.hasPermission(device)) return
        val intent = PendingIntent.getBroadcast(context, device.deviceId,
            Intent(ACTION_PERMISSION).setPackage(context.packageName), PendingIntent.FLAG_MUTABLE)
        manager.requestPermission(device, intent)
    }

    @Volatile private var watching = false

    /** Devices plugged in later: granted at once with the "all" policy, asked about with "each". */
    /** Called when a device is connected or removed (Linux settings refreshes its list then). */
    private val listeners = java.util.concurrent.CopyOnWriteArraySet<() -> Unit>()

    fun onChange(listener: () -> Unit) { listeners += listener }

    fun watch(context: Context) {
        if (watching) return
        watching = true
        val app = context.applicationContext
        app.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java) ?: return
                if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
                    if (privileged(app) && policy(app) == POLICY_ALL) {
                        setEnabled(app, device, true)
                        // Granting through the privileged service blocks.
                        Thread { runCatching { grant(app, device) }; listeners.forEach { it() } }.start()
                    } else if (enabled(app, device)) {
                        // "Each": Android asks whenever the device comes back.
                        grant(app, device, silent = false)
                    }
                }
                listeners.forEach { it() }
            }
        }, IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }, Context.RECEIVER_NOT_EXPORTED)
    }
}
