package moe.n4tsu.dextop

import android.app.ActivityManager
import android.app.UiModeManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.util.DisplayMetrics
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal class DiagnosticReport(private val context: Context) {
    private fun line(name: String, value: Any?) = "$name: ${value ?: "unknown"}\n"

    fun build(): String {
        val identity = DeviceIdentity.current()
        val environment = DesktopEnvironmentRegistry.resolve(identity)
        val diagnostics = DeviceDiagnostics(context).report()
        val activity = context.getSystemService(ActivityManager::class.java)
        val memory = ActivityManager.MemoryInfo().also(activity::getMemoryInfo)
        val displayManager = context.getSystemService(DisplayManager::class.java)
        val storage = StatFs(Environment.getDataDirectory().absolutePath)
        val battery = context.getSystemService(BatteryManager::class.java)
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val features = context.packageManager.systemAvailableFeatures
            .mapNotNull { feature ->
                feature.name ?: feature.reqGlEsVersion
                    .takeIf { it != 0 }
                    ?.let { version -> "gles=0x${version.toString(16)}" }
            }
            .sorted()
        val inputDevices = android.view.InputDevice.getDeviceIds().toList().mapNotNull { id ->
            android.view.InputDevice.getDevice(id)?.let {
                "sources=0x${it.sources.toString(16)} keyboard=${it.keyboardType} " +
                    "external=${it.isExternal} virtual=${it.isVirtual}"
            }
        }
        val sb = StringBuilder()
        sb.append("DEXTOP DIAGNOSTIC REPORT\n")
        sb.append(line("generated", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(Date())))
        sb.append(line("app", "${context.packageName} ${packageInfo.versionName} (${packageInfo.longVersionCode})"))
        sb.append(line("geometryInvariant", GeometryInvariant.discriminator(2)))
        sb.append("\n[DEVICE]\n")
        listOf(
            "manufacturer" to Build.MANUFACTURER, "brand" to Build.BRAND, "model" to Build.MODEL,
            "device" to Build.DEVICE, "product" to Build.PRODUCT, "board" to Build.BOARD,
            "hardware" to Build.HARDWARE, "bootloader" to Build.BOOTLOADER,
            "fingerprint" to Build.FINGERPRINT, "buildId" to Build.ID,
            "displayBuild" to Build.DISPLAY, "type" to Build.TYPE, "tags" to Build.TAGS,
            "supportedAbis" to Build.SUPPORTED_ABIS.joinToString(), "radio" to Build.getRadioVersion()
        ).forEach { sb.append(line(it.first, it.second)) }
        sb.append("\n[ANDROID]\n")
        sb.append(line("release", Build.VERSION.RELEASE))
        sb.append(line("sdk", Build.VERSION.SDK_INT))
        sb.append(line("incremental", Build.VERSION.INCREMENTAL))
        sb.append(line("securityPatch", Build.VERSION.SECURITY_PATCH))
        sb.append(line("baseOs", Build.VERSION.BASE_OS))
        sb.append(line("previewSdk", Build.VERSION.PREVIEW_SDK_INT))
        sb.append(line("uiModeType", context.getSystemService(UiModeManager::class.java).currentModeType))
        sb.append("\n[DEXTOP COMPATIBILITY]\n")
        val externalDisplays = ExternalDisplayDetector(context).snapshot()
        sb.append(line("externalDisplayConnected", externalDisplays.connected))
        sb.append(line("externalDisplayIds", externalDisplays.displayIds.joinToString()))
        sb.append(line("externalDisplayNames", externalDisplays.names.joinToString()))
        sb.append(line("privilegeProvider", diagnostics["privilegeProviderName"]))
        sb.append(line("stellarInstalled", diagnostics["stellarInstalled"]))
        sb.append(line("shizukuInstalled", diagnostics["originalShizukuInstalled"]))
        sb.append(line("environmentId", environment.id))
        sb.append(line("environmentName", environment.displayName))
        sb.append(line("platformManaged", environment.platformManaged))
        val decorationWorkaround = HomeLaunchRecovery.classify(context, environment.platformManaged)
        sb.append(line("homeDecorationWorkaround", decorationWorkaround.report))
        sb.append(line("systemDecorationsAtSessionStart", decorationWorkaround.forcesSystemDecorations))
        sb.append(line("displayStrategies", environment.displayCreationStrategies.joinToString()))
        sb.append(line("mirrorStrategies", environment.mirrorStrategies.joinToString()))
        sb.append(line("windowingStrategies", environment.windowingStrategies.joinToString()))
        diagnostics.forEach { (key, value) -> sb.append(line("capability.$key", value)) }
        CapabilityProbe(context, PrivilegedAccess("DextopProbe")).run().forEach { (key, value) ->
            sb.append(line("probe.$key", "supported=${value.supported} detail=${value.detail}"))
        }
        sb.append("\n[CPU / MEMORY / STORAGE]\n")
        sb.append(line("processors", Runtime.getRuntime().availableProcessors()))
        sb.append(line("memoryTotalBytes", memory.totalMem))
        sb.append(line("memoryAvailableBytes", memory.availMem))
        sb.append(line("memoryLow", memory.lowMemory))
        sb.append(line("memoryThresholdBytes", memory.threshold))
        sb.append(line("heapClassMb", activity.memoryClass))
        sb.append(line("largeHeapClassMb", activity.largeMemoryClass))
        sb.append(line("storageTotalBytes", storage.totalBytes))
        sb.append(line("storageAvailableBytes", storage.availableBytes))
        readFile("/proc/cpuinfo", 80_000)?.let { sb.append("cpuInfo:\n$it\n") }
        readFile("/proc/meminfo", 20_000)?.let { sb.append("memInfo:\n$it\n") }
        sb.append("\n[DISPLAYS]\n")
        displayManager.displays.forEach { display ->
            val metrics = DisplayMetrics().also { @Suppress("DEPRECATION") display.getRealMetrics(it) }
            sb.append(line("display.${display.displayId}",
                "name=${display.name} state=${display.state} rotation=${display.rotation} " +
                    "${metrics.widthPixels}x${metrics.heightPixels}/${metrics.densityDpi}dpi " +
                    "refresh=${display.refreshRate} modes=${display.supportedModes.joinToString { "${it.physicalWidth}x${it.physicalHeight}@${it.refreshRate}" }}"))
        }
        sb.append("\n[BATTERY / INPUT]\n")
        sb.append(line("batteryPercent", battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)))
        sb.append(line("batteryChargeCounterUah", battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)))
        sb.append(line("batteryCurrentNowUa", battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)))
        inputDevices.forEach { sb.append(line("input", it)) }
        sb.append("\n[SYSTEM FEATURES]\n")
        features.forEach { sb.append("$it\n") }
        sb.append("\n[IMPORTANT SETTINGS]\n")
        listOf("overlay_display_devices", "enable_freeform_support", "force_resizable_activities",
            "force_desktop_mode_on_external_displays", "adb_wifi_enabled").forEach {
            sb.append(line(it, Settings.Global.getString(context.contentResolver, it)))
        }
        sb.append("\n[LAST COMPLETED DEXTOP SESSION]\n")
        sb.append(OperationLog.readLastSession(context).ifBlank { "(empty)\n" })
        return sb.toString()
    }

    private fun readFile(path: String, limit: Int): String? = runCatching {
        File(path).takeIf { it.canRead() }?.readText()?.take(limit)
    }.getOrNull()
}
