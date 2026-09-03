package moe.n4tsu.dextop

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ComponentName
import android.content.pm.PackageManager
import android.hardware.input.InputManager
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import android.view.InputDevice
import android.view.accessibility.AccessibilityManager
import moe.n4tsu.dextop.privilege.DistributionPrivilegeBootstrap
import moe.n4tsu.dextop.privilege.DistributionPrivilegeRuntime
import rikka.shizuku.Shizuku

internal class DeviceDiagnostics(private val context: Context) {
    fun report(): Map<String, Any> {
        val stellarInstalled = runCatching {
            context.packageManager.getPackageInfo("roro.stellar.manager", 0)
        }.isSuccess
        val originalShizukuInstalled = runCatching {
            context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
        }.isSuccess || runCatching {
            context.packageManager.getPackageInfo("moe.shizuku.manager", 0)
        }.isSuccess
        @Suppress("DEPRECATION")
        val compatibleProviders = runCatching {
            context.packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS)
                .filter { info ->
                    info.packageName != context.packageName && (
                        info.packageName == "roro.stellar.manager" ||
                            info.packageName == "com.hamondev.shevery" ||
                            info.packageName == "moe.shizuku.privileged.api" ||
                            info.packageName == "moe.shizuku.manager" ||
                            info.permissions?.any {
                                it.name == "moe.shizuku.manager.permission.API_V23"
                            } == true
                        )
                }
        }.getOrDefault(emptyList())
        val savedProvider = context.getSharedPreferences(
            "dextop_privilege_provider",
            Context.MODE_PRIVATE
        ).getString("selected", null)
        val embeddedSelected = DistributionPrivilegeBootstrap.included && DistributionPrivilegeRuntime.enabled
        val selectedExternal = compatibleProviders.firstOrNull { it.packageName == savedProvider }
            ?: compatibleProviders.firstOrNull()
        val privilegeProvider = if (embeddedSelected) {
            "embedded"
        } else if (selectedExternal?.packageName == "roro.stellar.manager") {
            "stellar"
        } else {
            "shizuku"
        }
        val managerEnabled = context.getSystemService(AccessibilityManager::class.java)
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == context.packageName }
        val component = ComponentName(context, MirrorService::class.java).flattenToString()
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty().split(':')
        val accessibility = managerEnabled || enabledServices.any {
            ComponentName.unflattenFromString(it)?.let { name ->
                name.packageName == context.packageName && name.className == MirrorService::class.java.name
            } == true || it == component
        }
        val secureSettings = context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        val embeddedRuntime = DistributionPrivilegeRuntime.available
        val shizuku = !embeddedRuntime && runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        val shizukuGranted = shizuku && runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        val serviceDeclared = runCatching {
            val info = if (Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getServiceInfo(
                    ComponentName(context, MirrorService::class.java),
                    PackageManager.ComponentInfoFlags.of(PackageManager.GET_META_DATA.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getServiceInfo(
                    ComponentName(context, MirrorService::class.java),
                    PackageManager.GET_META_DATA
                )
            }
            info.permission == android.Manifest.permission.BIND_ACCESSIBILITY_SERVICE &&
                info.metaData != null
        }.getOrDefault(false)
        val canManageAccessibility = secureSettings || shizukuGranted || embeddedRuntime
        val accessibilityCapable = serviceDeclared && (accessibility || canManageAccessibility)
        val overlayCapable = accessibilityCapable
        val displayCapable = Build.VERSION.SDK_INT >= 29 && canManageAccessibility
        val sessionCapable = accessibilityCapable && displayCapable
        val hasMouse = InputDevice.getDeviceIds().any { id ->
            InputDevice.getDevice(id)?.sources?.and(InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE
        }
        val hasKeyboard = InputDevice.getDeviceIds().any { id ->
            InputDevice.getDevice(id)?.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC
        }
        val externalDisplays = ExternalDisplayDetector(context).snapshot()
        val configuration = context.resources.configuration
        val embeddedIncluded = DistributionPrivilegeBootstrap.included
        val embeddedPaired = embeddedIncluded &&
            DistributionPrivilegeBootstrap.hasStoredPairing(context)
        val embeddedConnected = embeddedSelected && embeddedRuntime
        val embeddedNotificationsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        return mapOf(
            "shizuku" to shizukuGranted,
            "privilegeProvider" to privilegeProvider,
            "privilegeProviderName" to when (privilegeProvider) {
                "embedded" -> "Dextop"
                "stellar" -> "Stellar"
                else -> selectedExternal?.applicationInfo?.let {
                    context.packageManager.getApplicationLabel(it).toString()
                }.orEmpty().ifBlank { "Shizuku" }
            },
            "stellarInstalled" to stellarInstalled,
            "originalShizukuInstalled" to originalShizukuInstalled,
            "compatiblePrivilegeProviders" to compatibleProviders.map { it.packageName },
            "secureSettings" to secureSettings,
            "accessibility" to accessibilityCapable,
            "accessibilityEnabled" to accessibility,
            "overlayWritable" to overlayCapable,
            "mouse" to hasMouse,
            "keyboard" to hasKeyboard,
            "externalDisplayConnected" to externalDisplays.connected,
            "externalDisplayIds" to externalDisplays.displayIds.joinToString(),
            "externalDisplayNames" to externalDisplays.names.joinToString(),
            "secondaryIme" to (Build.VERSION.SDK_INT >= 31),
            "desktopMode" to (Build.VERSION.SDK_INT >= 29),
            "sessionActive" to sessionCapable,
            "virtualDisplay" to displayCapable,
            "appLauncher" to (context.packageManager.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                0
            ).isNotEmpty()),
            "quickSettingsTile" to (Build.VERSION.SDK_INT >= 24),
            "foldableLayout" to (configuration.smallestScreenWidthDp >= 600),
            "embeddedBinderIncluded" to embeddedIncluded,
            "embeddedBinderSelected" to embeddedSelected,
            "embeddedBinderPaired" to embeddedPaired,
            "embeddedBinderConnected" to embeddedConnected,
            "embeddedBinderNotifications" to embeddedNotificationsGranted,
            "sdk" to Build.VERSION.SDK_INT
        )
    }

    fun metrics(inputMode: String, fps: Double): Map<String, Any> {
        val memory = ActivityManager.MemoryInfo().also {
            context.getSystemService(ActivityManager::class.java).getMemoryInfo(it)
        }
        val battery = context.getSystemService(BatteryManager::class.java)
        val batteryPercent = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val currentMicroamps = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val voltageMillivolts = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val powerWatts = if (currentMicroamps != Int.MIN_VALUE && voltageMillivolts > 0) {
            kotlin.math.abs(currentMicroamps.toDouble()) * voltageMillivolts / 1_000_000_000.0
        } else 0.0
        val runtime = Runtime.getRuntime()
        return mapOf(
            "memoryMb" to ((runtime.totalMemory() - runtime.freeMemory()) / 1048576),
            "availableMemoryMb" to (memory.availMem / 1048576),
            "batteryPercent" to batteryPercent,
            "powerWatts" to String.format("%.2f", powerWatts),
            "cpuTemperature" to CpuTemperature.formatted(),
            "inputMode" to inputMode,
            "refreshRate" to context.display?.refreshRate?.toDouble().orEmpty(),
            "fps" to fps
        )
    }

    private fun Double?.orEmpty(): Double = this ?: 0.0
}
