package moe.n4tsu.dextop.linux

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager

/**
 * Developer test hooks, reachable only from adb (the receiver requires android.permission.DUMP,
 * which the shell user holds and apps cannot get):
 *   am broadcast -n moe.n4tsu.dextop/.linux.LinuxTestReceiver --es op usb --ez enabled true
 *     turns every connected USB storage device on (or off) for Linux;
 *   am broadcast -n moe.n4tsu.dextop/.linux.LinuxTestReceiver --es op run --es container <id> --es name <n> --es command <sh>
 *     runs a command in the container through the Linux service; its output goes to
 *     /var/lib/dextop/test/<n>.done in the container, ending with "exit=<status>".
 */
class LinuxTestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        Thread({
            try {
                when (intent.getStringExtra("op")) {
                    "usb" -> {
                        ensurePrivilege(app)
                        val enabled = intent.getBooleanExtra("enabled", true)
                        val devices = app.getSystemService(UsbManager::class.java)?.deviceList.orEmpty().values
                            .filter { LinuxUsbDisk.storageInterface(it) != null }
                        devices.forEach { runCatching { LinuxUsb.setForLinux(app, it.deviceName, enabled) } }
                        pending.resultData = "usb storage ${devices.joinToString { it.deviceName }} enabled=$enabled"
                    }
                    "run" -> {
                        val container = intent.getStringExtra("container").orEmpty()
                        val name = intent.getStringExtra("name").orEmpty().filter { it.isLetterOrDigit() || it == '-' }.ifEmpty { "test" }
                        val command = intent.getStringExtra("command").orEmpty()
                        ensurePrivilege(app)
                        LinuxContainerClient.connect(app)
                        val deadline = System.currentTimeMillis() + 20_000
                        while (LinuxContainerClient.service() == null && System.currentTimeMillis() < deadline) Thread.sleep(200)
                        val service = LinuxContainerClient.service()
                        if (service == null) { pending.resultData = "no Linux service"; return@Thread }
                        val directory = "/var/lib/dextop/test"
                        val script = "mkdir -p $directory; { $command\n} > $directory/$name.out 2>&1; echo \"exit=\$?\" >> $directory/$name.out; mv $directory/$name.out $directory/$name.done"
                        // Long commands outlive the broadcast: they run on, and their result is the file.
                        Thread({ runCatching { service.runCommand(container, script, 3600) } }, "DextopLinuxTest").start()
                        pending.resultData = "started $name"
                    }
                    else -> pending.resultData = "unknown op"
                }
            } catch (error: Exception) {
                pending.resultData = "failed: $error"
            } finally {
                pending.finish()
            }
        }, "DextopLinuxTestReceiver").start()
    }

    /** The built-in privileged runtime reconnects through wireless debugging when it is down. */
    private fun ensurePrivilege(app: Context) {
        if (LinuxExecutionMode.android(app) || LinuxContainerClient.privilegeAvailable(app)) return
        moe.n4tsu.dextop.privilege.EmbeddedPrivilegeKeeper.ensure(app, "test")
        val deadline = System.currentTimeMillis() + 60_000
        while (!LinuxContainerClient.privilegeAvailable(app) && System.currentTimeMillis() < deadline) Thread.sleep(300)
    }
}
