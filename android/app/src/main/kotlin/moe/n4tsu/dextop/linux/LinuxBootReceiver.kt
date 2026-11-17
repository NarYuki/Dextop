package moe.n4tsu.dextop.linux

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.json.JSONObject
import java.io.File

class LinuxBootReceiver : BroadcastReceiver() {
    companion object {
        fun startConfigured(context: Context, reason: String) {
            if (!LinuxExecutionMode.android(context)) {
                startPrivileged(context.applicationContext, reason)
                return
            }
            val containers = File(context.noBackupFilesDir, "linux-android/containers")
            val ids = containers.listFiles().orEmpty().filter { directory ->
                directory.name.matches(Regex("c[a-zA-Z0-9]+")) && runCatching {
                    JSONObject(File(directory, "integration/settings.json").readText()).optJSONObject("services")?.optBoolean("atBoot") == true
                }.getOrDefault(false)
            }.map { it.name }
            android.util.Log.i("DextopServices", "$reason: starting services of ${ids.ifEmpty { listOf("none") }.joinToString()}")
            if (ids.isEmpty()) return
            runCatching {
                context.startForegroundService(Intent(context, LinuxRuntimeService::class.java)
                    .setAction(LinuxRuntimeService.ACTION_BOOT)
                    .putExtra(LinuxRuntimeService.EXTRA_CONTAINERS, ids.toTypedArray()))
            }.onFailure { android.util.Log.w("DextopServices", "Cannot start Linux services ($reason)", it) }
        }

        private fun startPrivileged(context: Context, reason: String) {
            Thread({
                val end = System.currentTimeMillis() + 120_000
                var service = LinuxContainerClient.service()
                var attempt = 0
                while (service == null && System.currentTimeMillis() < end) {
                    if (attempt++ % 6 == 0) LinuxContainerClient.connect(context)
                    Thread.sleep(500)
                    service = LinuxContainerClient.service()
                }
                if (service == null) {
                    android.util.Log.i("DextopServices", "$reason: the privileged Linux runtime did not connect")
                    return@Thread
                }
                runCatching {
                    val list = org.json.JSONArray(service.listContainers())
                    for (index in 0 until list.length()) {
                        val id = list.getJSONObject(index).optString("id")
                        if (id.isEmpty() || list.getJSONObject(index).optString("state") != "installed") continue
                        runCatching { service.integrationAction(id, JSONObject().put("op", "autostart").toString()) }
                            .onFailure { android.util.Log.w("DextopServices", "$reason: autostart of $id failed", it) }
                    }
                    android.util.Log.i("DextopServices", "$reason: privileged containers checked")
                }.onFailure { android.util.Log.w("DextopServices", "$reason: cannot list containers", it) }
            }, "DextopPrivilegedAutostart").apply { isDaemon = true; start() }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        startConfigured(context, intent.action.orEmpty())
    }
}
