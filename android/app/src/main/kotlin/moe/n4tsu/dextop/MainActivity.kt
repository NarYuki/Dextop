package moe.n4tsu.dextop

import android.Manifest
import android.content.Intent
import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.util.DisplayMetrics
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import moe.n4tsu.dextop.privilege.DistributionPrivilegeBootstrap
import moe.n4tsu.dextop.privilege.DistributionPrivilegeRuntime
import rikka.shizuku.Shizuku

open class MainActivity : FlutterActivity() {
    companion object {
        private const val STELLAR_PACKAGE = "roro.stellar.manager"
        private const val SHEVERY_PACKAGE = "com.hamondev.shevery"
        private const val CAR_COMPANION_PACKAGE = "moe.n4tsu.dextop.cardex"
        private const val SHIZUKU_PERMISSION = "moe.shizuku.manager.permission.API_V23"
        private const val STELLAR_REQUEST_BINDER_ACTION = "roro.stellar.intent.action.REQUEST_BINDER"
        private const val EMBEDDED_NOTIFICATION_PERMISSION_REQUEST = 8104

        private var instance: MainActivity? = null
        private var phoneTaskId = -1
        private var orientationBeforeSession = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        private var physicalOrientationBeforeSession = android.content.res.Configuration.ORIENTATION_UNDEFINED

        fun rememberOrientation() {
            instance?.let {
                orientationBeforeSession = it.requestedOrientation
                physicalOrientationBeforeSession = it.resources.configuration.orientation
            }
        }

        fun setDisplayOrientation(portrait: Boolean, reverse: Boolean = false) {
            instance?.requestedOrientation = when {
                portrait && reverse -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                portrait -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                reverse -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                else -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
        }

        fun restoreOrientation() {
            instance?.let { activity ->
                activity.requestedOrientation = orientationBeforeSession
                if (orientationBeforeSession == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
                    activity.requestedOrientation = when (physicalOrientationBeforeSession) {
                        android.content.res.Configuration.ORIENTATION_PORTRAIT ->
                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                        android.content.res.Configuration.ORIENTATION_LANDSCAPE ->
                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                    activity.window.decorView.postDelayed({
                        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }, 500)
                }
            }
        }

        fun phoneTaskId(): Int = phoneTaskId

        fun currentActivity(): MainActivity? = instance
    }

    private val channelName = "app.freedextop/display"
    private val logTag = "Dextop"
    private val permissionRequestCode = 0x4458
    private var pendingPermissionResult: MethodChannel.Result? = null
    private var pendingStartResult: MethodChannel.Result? = null
    private var shizukuBinderAvailable = false
    private val stellarBinderRetryGate = StellarBinderRetryGate()
    private val permissionHandler = Handler(Looper.getMainLooper())
    private val permissionTimeout = Runnable {
        pendingPermissionResult?.error("permission_timeout", NativeStrings.text("nativeShizukuPermissionCheckTimedOut"), null)
        pendingPermissionResult = null
    }
    private val startTimeout = Runnable {
        val pendingResult = pendingStartResult ?: return@Runnable
        pendingStartResult = null
        MirrorService.stopActive()
        pendingResult.error(
            "start_timeout",
            "Dextop did not finish creating and attaching the display in time",
            null
        )
    }
    private var flutterChannel: MethodChannel? = null
    private var pendingWirelessDebuggingResult: MethodChannel.Result? = null
    private var pendingNotificationPermissionResult: MethodChannel.Result? = null
    private val distributionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    // Embedded ADB restore must survive the launcher activity replacing its Flutter engine.
    private val embeddedPrivilegeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var embeddedRestoreInProgress = false
    @Volatile private var embeddedRestoreNeedsSetup = false

    /** Flutter's binary messenger is main-thread only, including Binder callbacks. */
    private fun notifyFlutterPrivilegeStatus() {
        runOnUiThread {
            flutterChannel?.invokeMethod("shizukuStatusChanged", null)
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        stellarBinderRetryGate.reset()
        refreshBinderAvailability()
        Log.i(logTag, "privilege binder received alive=$shizukuBinderAvailable")
        notifyFlutterPrivilegeStatus()
    }
    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        val provider = selectedPrivilegeProvider(installedPrivilegeProviders())
        refreshBinderAvailability(provider, requestIfMissing = true)
        Log.w(logTag, "privilege binder death reported alive=$shizukuBinderAvailable provider=$provider")
        notifyFlutterPrivilegeStatus()
    }
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == permissionRequestCode) {
            permissionHandler.removeCallbacks(permissionTimeout)
            Log.i(logTag, "Shizuku permission result=$grantResult")
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                rememberPrivilegeMode("external")
                runCatching { grantSecureSettings() }
                    .onSuccess { pendingPermissionResult?.success(true) }
                    .onFailure { pendingPermissionResult?.error("grant", it.message, null) }
            } else if (grantResult == PackageManager.PERMISSION_DENIED) {
                pendingPermissionResult?.error(
                    "permission_denied",
                    NativeStrings.text("nativePermissionDeniedToShizuku"),
                    null
                )
            } else {
                pendingPermissionResult?.error(
                    "permission_unknown",
                    "${NativeStrings.text("nativeUnknownPermissionResult")}: $grantResult",
                    null
                )
            }
            pendingPermissionResult = null
        }
    }

    override fun onStart() {
        super.onStart()
        DistributionPrivilegeRuntime.onRuntimeDied = {
            currentActivity()?.runOnUiThread {
                currentActivity()?.restoreEmbeddedPrivilegeIfPossible("runtime_died")
            }
        }
        restoreEmbeddedPrivilegeIfPossible("activity_started")
        if (display?.displayId == android.view.Display.DEFAULT_DISPLAY) {
            instance = this
            phoneTaskId = taskId
            if (!MirrorService.isActive()) {
                MirrorService.restorePhoneNavigation(this)
                runCatching { DisplayEnvironmentSettings(this).prepareInactiveState() }
                    .onFailure { Log.w(logTag, "inactive topology cleanup deferred", it) }
            }
        }
    }

    private fun restoreEmbeddedPrivilegeIfPossible(reason: String) {
        updatePrivilegeRuntimePolicy()
        val installedProviders = installedPrivilegeProviders()
        val storedPairing = DistributionPrivilegeBootstrap.hasStoredPairing(applicationContext)
        Log.i(
            logTag,
            "EmbeddedPrivilege restore check reason=$reason included=${DistributionPrivilegeBootstrap.included} " +
                "providers=${installedProviders.map { it.packageName }} runtime=${DistributionPrivilegeRuntime.available} " +
                "inProgress=$embeddedRestoreInProgress storedPairing=$storedPairing enabled=${DistributionPrivilegeRuntime.enabled}",
        )
        if (
            !DistributionPrivilegeBootstrap.included ||
            !DistributionPrivilegeRuntime.enabled ||
            DistributionPrivilegeRuntime.available ||
            embeddedRestoreInProgress ||
            !storedPairing
        ) return
        var wirelessDebuggingEnabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            runCatching {
                Settings.Global.getInt(contentResolver, "adb_wifi_enabled", 0) == 1
            }.getOrDefault(false)
        if (!wirelessDebuggingEnabled && hasSecureSettingsPermission()) {
            wirelessDebuggingEnabled = runCatching {
                Settings.Global.putInt(contentResolver, "adb_wifi_enabled", 1)
            }.getOrDefault(false)
            if (wirelessDebuggingEnabled) {
                OperationLog.i(
                    this,
                    "EmbeddedPrivilege",
                    "wireless debugging enabled for stored pairing",
                )
            }
        }
        if (!wirelessDebuggingEnabled) return

        embeddedRestoreInProgress = true
        embeddedRestoreNeedsSetup = false
        OperationLog.i(this, "EmbeddedPrivilege", "restoring stored pairing reason=$reason")
        embeddedPrivilegeScope.launch {
            var restored = false
            var lastDetail: String? = null
            repeat(4) { attempt ->
                if (restored) return@repeat
                val start = runCatching {
                    DistributionPrivilegeBootstrap.connectAndStart(applicationContext)
                }.onFailure { error ->
                    lastDetail = error.message ?: error.javaClass.simpleName
                }.getOrNull()
                if (start?.started == true) {
                    restored = true
                    DistributionPrivilegeBootstrap.dismissPairingNotification(applicationContext)
                    rememberPrivilegeMode("embedded")
                    OperationLog.i(
                        this@MainActivity,
                        "EmbeddedPrivilege",
                        "stored pairing restored endpoint=${start.endpoint ?: "unknown"} attempt=${attempt + 1}",
                    )
                    notifyFlutterPrivilegeStatus()
                    return@repeat
                }
                lastDetail = start?.detail ?: lastDetail
                if (!restored && attempt < 3) delay(1_000)
            }
            if (!restored) {
                embeddedRestoreNeedsSetup = true
                // Only an advertised ADB connect endpoint that rejects every
                // saved-key attempt is evidence that the pairing was revoked.
                // A missing endpoint is normal while Wireless debugging starts
                // and must not trigger a needless re-pairing notification.
                val pairingRevoked = lastDetail
                    ?.contains("rejected all local connection candidates", ignoreCase = true) == true
                if (pairingRevoked) {
                    DistributionPrivilegeBootstrap.showPairingNotification(applicationContext)
                } else {
                    DistributionPrivilegeBootstrap.dismissPairingNotification(applicationContext)
                }
                OperationLog.w(
                    this@MainActivity,
                    "EmbeddedPrivilege",
                    "stored pairing could not reconnect after retries revoked=$pairingRevoked detail=${lastDetail ?: "unknown"}",
                )
                notifyFlutterPrivilegeStatus()
            }
            embeddedRestoreInProgress = false
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onDestroy() {
        if (instance === this) {
            instance = null
            phoneTaskId = -1
        }
        super.onDestroy()
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        updatePrivilegeRuntimePolicy()
        Shizuku.addRequestPermissionResultListener(permissionListener)
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        flutterChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, channelName)
        flutterChannel?.setMethodCallHandler { call, result ->
            when (call.method) {
                "status" -> result.success(status())
                "sessionState" -> result.success(mapOf(
                    "active" to MirrorService.isActive(),
                    "stopping" to MirrorService.isStopping(),
                    "autoConnected" to AndroidAutoMirrorActivity.isAutoConnected(),
                    "autoActive" to (
                        AndroidAutoMirrorActivity.isAutoSessionActive() ||
                            MirrorService.isAutoOnlySessionActive() ||
                            CardexRelayService.isRelaySessionActive()
                        )
                ))
                "launchContext" -> {
                    val launchDisplayId = display?.displayId ?: android.view.Display.DEFAULT_DISPLAY
                    result.success(mapOf(
                        "displayId" to launchDisplayId,
                        "desktopWindow" to (
                            MirrorService.isActive() &&
                                launchDisplayId != android.view.Display.DEFAULT_DISPLAY
                            )
                    ))
                }
                "requestShizuku" -> requestShizuku(result)
                "openShizuku" -> openShizuku(result)
                "embeddedPrivilegeInfo" -> result.success(
                    mapOf("included" to DistributionPrivilegeBootstrap.included),
                )
                "pairEmbeddedPrivilege" -> pairEmbeddedPrivilege(
                    call.argument<String>("code").orEmpty(),
                    result,
                )
                "startEmbeddedPrivilege" -> startEmbeddedPrivilege(result)
                "selectPrivilegeProvider" -> selectPrivilegeProvider(call.argument<String>("provider"), result)
                "showOverlayDemo" -> {
                    MirrorService.showOverlayDemo(this)
                    result.success(true)
                }
                "hideOverlayDemo" -> {
                    MirrorService.hideOverlayDemo()
                    result.success(null)
                }
                "foldableLaptopMode" -> {
                    MirrorService.updateLaptopModeEnabled(call.argument<Boolean>("enabled") == true)
                    result.success(null)
                }
                "laptopKeyboardTheme" -> {
                    val themeId = call.argument<String>("themeId")
                    if (themeId.isNullOrBlank()) {
                        result.error("invalid_theme", "A keyboard theme id is required", null)
                    } else {
                        MirrorService.updateLaptopTheme(themeId)
                        result.success(null)
                    }
                }
                "laptopSwipeLanguagesChanged" -> {
                    MirrorService.updateLaptopSwipeSettings(
                        call.argument<Boolean>("enabled")
                    )
                    result.success(null)
                }
                "previewLaptopOverlay" -> {
                    val themeId = call.argument<String>("themeId")
                    MirrorService.setPendingLaptopPreviewTheme(themeId)
                    if (MirrorService.showLaptopPreview(themeId)) result.success(true)
                    else {
                        MirrorService.showLaptopDemo(this)
                        result.success(true)
                    }
                }
                "hideLaptopDemo" -> {
                    MirrorService.hideLaptopDemo()
                    result.success(null)
                }
                "exitLaptopPreview" -> {
                    MirrorService.exitLaptopPreview()
                    result.success(null)
                }
                "hideOverlayForSettings" -> {
                    MirrorService.setOverlayHiddenForSettings(true)
                    result.success(null)
                }
                "restoreOverlayAfterSettings" -> {
                    MirrorService.setOverlayHiddenForSettings(false)
                    result.success(null)
                }
                "isFoldableDevice" -> result.success(MirrorService.isFoldableDevice(this))
                "start" -> startDisplay(call.arguments as? Map<*, *>, result)
                "currentDeviceDisplayProfile" -> {
                    val builtIn = getSystemService(DisplayManager::class.java)
                        .getDisplay(android.view.Display.DEFAULT_DISPLAY)
                    val metrics = DisplayMetrics()
                    @Suppress("DEPRECATION")
                    builtIn?.getRealMetrics(metrics)
                    val width = metrics.widthPixels.takeIf { it > 0 }
                        ?: resources.displayMetrics.widthPixels
                    val height = metrics.heightPixels.takeIf { it > 0 }
                        ?: resources.displayMetrics.heightPixels
                    val densityScale = metrics.density.takeIf { it > 0f }
                        ?: resources.displayMetrics.density
                    val automaticDensity = (160 + densityScale * 24).toInt().coerceIn(160, 320)
                    result.success(mapOf(
                        "width" to maxOf(width, height),
                        "height" to minOf(width, height),
                        "density" to automaticDensity
                    ))
                }
                "stop" -> stopDisplay(result)
                "stopAuto" -> {
                    AndroidAutoMirrorActivity.stopAutoSessionFromPhone()
                    result.success(null)
                }
                "apps" -> Thread {
                    val apps = AppCatalog(this).launchableApps()
                    runOnUiThread { result.success(apps) }
                }.start()
                "appsMetadata" -> Thread {
                    val apps = AppCatalog(this).launchableApps(includeIcons = false)
                    runOnUiThread { result.success(apps) }
                }.start()
                "appIcons" -> Thread {
                    val icons = AppCatalog(this).launchableAppIcons()
                    runOnUiThread { result.success(icons) }
                }.start()
                "launchApp" -> launchApp(call.arguments as? Map<*, *>, result)
                "diagnostics" -> result.success(DeviceDiagnostics(this).report())
                "samsungDesktopSettings" -> runCatching {
                    SamsungDesktopSettings(this).read()
                }.onSuccess(result::success).onFailure {
                    Log.e(logTag, "Samsung desktop settings read failed", it)
                    result.error("SAMSUNG_SETTINGS_READ", it.message, null)
                }
                "displayTopology" -> Thread {
                    val state = DisplayTopologyController(this).read()
                    runOnUiThread { result.success(state) }
                }.start()
                "displayModeDisplays" -> Thread {
                    val state = DisplayTopologyController(this).availableDisplays()
                    runOnUiThread { result.success(state) }
                }.start()
                "displayEnvironmentSettings" -> Thread {
                    val state = DisplayEnvironmentSettings(this).read()
                    runOnUiThread { result.success(state) }
                }.start()
                "setDisplayEnvironmentSetting" -> Thread {
                    runCatching {
                        val id = call.argument<String>("id") ?: error("A setting id is required")
                        val enabled = call.argument<Boolean>("enabled")
                            ?: error("A boolean value is required")
                        DisplayEnvironmentSettings(this).write(id, enabled)
                    }.onSuccess { state ->
                        runOnUiThread { result.success(state) }
                    }.onFailure { error ->
                        runOnUiThread {
                            result.error("DISPLAY_ENVIRONMENT_WRITE", error.message, null)
                        }
                    }
                }.start()
                "setVirtualPointerProfile" -> runCatching {
                    val profile = call.argument<String>("profile")
                        ?: error("A virtual pointer profile is required")
                    val normalized = profile.lowercase().let {
                        when (it) {
                            "touchpad", "mouse", "software" -> it
                            else -> error("Unknown virtual pointer profile: $profile")
                        }
                    }
                    MirrorService.setVirtualPointerProfile(normalized)
                    mapOf("virtualPointerProfile" to normalized)
                }.onSuccess(result::success).onFailure {
                    result.error("VIRTUAL_POINTER_PROFILE_WRITE", it.message, null)
                }
                "setDisplayTopology" -> Thread {
                    runCatching {
                        val positions = call.argument<Map<*, *>>("positions")
                            ?: error("Display positions are required")
                        DisplayTopologyController(this).rearrange(positions)
                    }.onSuccess { state ->
                        runOnUiThread { result.success(state) }
                    }.onFailure { error ->
                        Log.e(logTag, "Display topology update failed", error)
                        runOnUiThread {
                            result.error("DISPLAY_TOPOLOGY_WRITE", error.cause?.message ?: error.message, null)
                        }
                    }
                }.start()
                "setDisplayPreferredMode" -> Thread {
                    runCatching {
                        val displayId = call.argument<Int>("displayId")
                            ?: error("A display id is required")
                        val width = call.argument<Int>("width")
                            ?: error("A display width is required")
                        val height = call.argument<Int>("height")
                            ?: error("A display height is required")
                        val refreshRate = (call.argument<Number>("refreshRate")
                            ?: error("A refresh rate is required")).toFloat()
                        DisplayTopologyController(this).setPreferredMode(
                            displayId = displayId,
                            width = width,
                            height = height,
                            refreshRate = refreshRate
                        )
                    }.onSuccess { state ->
                        runOnUiThread { result.success(state) }
                    }.onFailure { error ->
                        Log.e(logTag, "Display mode update failed", error)
                        runOnUiThread {
                            result.error(
                                "DISPLAY_MODE_WRITE",
                                error.cause?.message ?: error.message,
                                null
                            )
                        }
                    }
                }.start()
                "setSamsungDesktopSetting" -> runCatching {
                    val id = call.argument<String>("id") ?: error("A setting id is required")
                    SamsungDesktopSettings(this).write(id, call.argument<Any>("value"))
                    SamsungDesktopSettings(this).read()
                }.onSuccess(result::success).onFailure {
                    Log.e(logTag, "Samsung desktop setting write failed", it)
                    result.error("SAMSUNG_SETTINGS_WRITE", it.message, null)
                }
                "backupSamsungDesktopSettings" -> runCatching {
                    SamsungDesktopSettings(this).backupIfNeeded()
                }.onSuccess(result::success).onFailure {
                    result.error("SAMSUNG_SETTINGS_BACKUP", it.message, null)
                }
                "restoreSamsungDesktopSettings" -> runCatching {
                    SamsungDesktopSettings(this).restoreBackup()
                }.onSuccess(result::success).onFailure {
                    result.error("SAMSUNG_SETTINGS_RESTORE", it.message, null)
                }
                "lastSessionLog" -> result.success(OperationLog.readLastSession(this))
                "deviceReportIdentity" -> result.success(mapOf(
                    "manufacturer" to Build.MANUFACTURER,
                    "brand" to Build.BRAND,
                    "model" to Build.MODEL,
                    "device" to Build.DEVICE,
                    "product" to Build.PRODUCT,
                    "android" to Build.VERSION.RELEASE,
                    "sdk" to Build.VERSION.SDK_INT,
                    "incremental" to Build.VERSION.INCREMENTAL,
                    "buildId" to Build.ID,
                    "fingerprint" to Build.FINGERPRINT,
                    "securityPatch" to Build.VERSION.SECURITY_PATCH,
                    "displayBuild" to Build.DISPLAY,
                    "appVersion" to packageManager.getPackageInfo(packageName, 0).versionName,
                    "buildNumber" to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        packageManager.getPackageInfo(packageName, 0).longVersionCode
                    } else {
                        @Suppress("DEPRECATION") packageManager.getPackageInfo(packageName, 0).versionCode
                    }
                ))
                "sendDeviceReportEmail" -> {
                    val recipient = "dextop-device@n4t.su"
                    val subject = call.argument<String>("subject").orEmpty()
                    val body = call.argument<String>("body").orEmpty()
                    // Keep the recipient in the literal mailto path. Some mail
                    // clients ignore an address stored only in EXTRA_EMAIL or
                    // in an opaque Uri.Builder part.
                    val uri = Uri.parse(
                        "mailto:$recipient" +
                            "?to=${Uri.encode(recipient)}" +
                            "&subject=${Uri.encode(subject)}" +
                            "&body=${Uri.encode(body)}"
                    )
                    val email = Intent(Intent.ACTION_SENDTO, uri).apply {
                        putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
                        putExtra("android.intent.extra.EMAIL", arrayOf(recipient))
                        putExtra(Intent.EXTRA_SUBJECT, subject)
                        putExtra(Intent.EXTRA_TEXT, body)
                    }
                    runCatching { startActivity(email) }
                        .onSuccess { result.success(null) }
                        .onFailure { result.error("EMAIL_UNAVAILABLE", it.message, null) }
                }
                "repairState" -> result.success(repairState())
                "repairAndroid" -> repairAndroid(result)
                "restartApp" -> restartApp(result)
                "consumeTileAction" -> {
                    val requested = intent?.action == DextopTileService.ACTION_OPEN_LAST_WORKSPACE
                    if (requested) intent?.action = null
                    result.success(requested)
                }
                "metrics" -> result.success(DeviceDiagnostics(this).metrics(MirrorService.inputMode(), MirrorService.measuredFps()))
                "diagnosticReport" -> Thread {
                    OperationLog.i(this, "MainActivity", "diagnostic report requested")
                    val report = DiagnosticReport(this).build()
                    runOnUiThread { result.success(report) }
                }.start()
                "clearDiagnosticLog" -> {
                    OperationLog.clear(this)
                    result.success(null)
                }
                "shareDiagnosticReport" -> Thread {
                    val report = DiagnosticReport(this).build()
                    runOnUiThread {
                        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Dextop diagnostic report")
                            putExtra(Intent.EXTRA_TEXT, report)
                        }, "Share diagnostic report"))
                        result.success(null)
                    }
                }.start()
                "performanceHud" -> {
                    MirrorService.setPerformanceHud(call.argument<Boolean>("enabled") == true)
                    result.success(null)
                }
                "keepAwake" -> {
                    MirrorService.setKeepAwake(call.argument<Boolean>("enabled") == true)
                    result.success(null)
                }
                "recovery" -> result.success(SessionJournal(this).snapshot())
                "clearRecovery" -> discardRecovery(result)
                "openAccessibility" -> openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS, result)
                "openWirelessDebugging" -> openWirelessDebuggingSettings(result)
                "openWirelessDebuggingSettingsOnly" -> openSystemWirelessDebuggingSettings(result)
                "requestEmbeddedNotificationPermission" -> requestEmbeddedNotificationPermission(result)
                "prepareEmbeddedPairingNotification" -> prepareEmbeddedPairingNotification(result)
                "openUrl" -> {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(call.argument<String>("url"))))
                    result.success(null)
                }
                else -> result.notImplemented()
            }
        }
    }

    override fun cleanUpFlutterEngine(flutterEngine: FlutterEngine) {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        flutterChannel = null
        permissionHandler.removeCallbacks(permissionTimeout)
        permissionHandler.removeCallbacks(startTimeout)
        pendingPermissionResult = null
        pendingStartResult = null
        distributionScope.cancel()
        super.cleanUpFlutterEngine(flutterEngine)
    }

    private fun pairEmbeddedPrivilege(code: String, result: MethodChannel.Result) {
        if (!DistributionPrivilegeBootstrap.included) {
            result.error("embedded_unavailable", "The built-in access service is unavailable", null)
            return
        }
        distributionScope.launch {
            runCatching { DistributionPrivilegeBootstrap.pair(applicationContext, code) }
                .onSuccess { pairing ->
                    result.success(
                        mapOf(
                            "paired" to pairing.paired,
                            "endpoint" to pairing.endpoint,
                            "detail" to pairing.detail,
                        ),
                    )
                }
                .onFailure { error ->
                    result.error("embedded_pairing", error.message, null)
                }
        }
    }

    private fun startEmbeddedPrivilege(result: MethodChannel.Result) {
        if (!DistributionPrivilegeBootstrap.included) {
            result.error("embedded_unavailable", "The built-in access service is unavailable", null)
            return
        }
        distributionScope.launch {
            runCatching { DistributionPrivilegeBootstrap.connectAndStart(applicationContext) }
                .onSuccess { start ->
                    result.success(
                        mapOf(
                            "started" to start.started,
                            "endpoint" to start.endpoint,
                            "detail" to start.detail,
                        ),
                    )
                }
                .onFailure { error ->
                    result.error("embedded_start", error.message, null)
                }
        }
    }

    private fun status(): Map<String, Any> {
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val providers = installedPrivilegeProviders()
        val stellarInstalled = providers.any { it.kind == "stellar" }
        val shizukuInstalled = providers.any { it.kind == "shizuku" }
        val savedProvider = getSharedPreferences("dextop_privilege_provider", MODE_PRIVATE)
            .getString("selected", null)
        val useEmbedded = shouldUseEmbeddedRuntime(providers)
        DistributionPrivilegeRuntime.enabled = useEmbedded
        val selected = if (useEmbedded) null else selectedPrivilegeProvider(providers)
        val provider = if (useEmbedded) "embedded" else selected?.kind ?: "shizuku"
        val installed = useEmbedded || selected != null
        // A binder from an earlier Shizuku session can remain visible briefly even
        // after the manager has been removed.  Installation is therefore a hard
        // prerequisite for both the service and permission states.
        val wirelessDebuggingEnabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            runCatching {
                Settings.Global.getInt(contentResolver, "adb_wifi_enabled", 0) == 1
            }.getOrDefault(false)
        // Listener callbacks can be stale when Shizuku and Stellar have both
        // supplied a binder in the same app process. Always verify the binder
        // itself, and ask Stellar to resend it when the selected provider is
        // still installed but the shared Shizuku API state has lost the binder.
        val binderAlive = DistributionPrivilegeRuntime.available ||
            (installed && !useEmbedded && refreshBinderAvailability(selected, requestIfMissing = true))
        // The guided setup uses wireless debugging. A live/stale binder alone is
        // not enough to mark that setup as completed.
        // The service may be started through wireless debugging, wired ADB, or
        // root. Binder liveness is the authoritative running-state signal.
        val running = binderAlive
        val granted = running && (DistributionPrivilegeRuntime.available || runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false))
        if (granted) rememberPrivilegeMode(if (useEmbedded) "embedded" else "external")
        val previousMode = getSharedPreferences("dextop_privilege_provider", MODE_PRIVATE)
            .getString("last_mode", null)
        // Installing a compatible manager must not tear down a live embedded
        // session. Ask once before switching that session to an external one.
        val externalAuthorizationRequired = DistributionPrivilegeBootstrap.included &&
            useEmbedded && providers.isNotEmpty()
        val embeddedPairingStored = DistributionPrivilegeBootstrap.included &&
            DistributionPrivilegeBootstrap.hasStoredPairing(applicationContext)
        val embeddedSetupRequired = DistributionPrivilegeBootstrap.included && useEmbedded && !granted &&
            ((!embeddedPairingStored && previousMode == "external") || embeddedRestoreNeedsSetup)
        val notificationGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val carCompanionInstalled = runCatching {
            packageManager.getPackageInfo(CAR_COMPANION_PACKAGE, 0)
        }.isSuccess
        val status = mapOf(
            "active" to MirrorService.isActive(),
            "stopping" to MirrorService.isStopping(),
            "autoConnected" to AndroidAutoMirrorActivity.isAutoConnected(),
            "autoActive" to (
                AndroidAutoMirrorActivity.isAutoSessionActive() ||
                    MirrorService.isAutoOnlySessionActive() ||
                    CardexRelayService.isRelaySessionActive()
                ),
            "carCompanionInstalled" to carCompanionInstalled,
            "privileged" to hasSecureSettingsPermission(),
            "shizukuInstalled" to installed,
            "stellarInstalled" to stellarInstalled,
            "originalShizukuInstalled" to shizukuInstalled,
            "bothPrivilegeProvidersInstalled" to (providers.size > 1),
            "embeddedPrivilegeIncluded" to DistributionPrivilegeBootstrap.included,
            "embeddedPrivilegeSelected" to useEmbedded,
            "embeddedPrivilegePaired" to embeddedPairingStored,
            "embeddedPairingState" to DistributionPrivilegeBootstrap.pairingState(),
            "privilegeProviderSelectionRequired" to
                (!useEmbedded && providers.size > 1 && providers.none { it.packageName == savedProvider }),
            "privilegeProvider" to provider,
            "privilegeProviderId" to (selected?.packageName ?: provider),
            "privilegeProviderName" to if (useEmbedded) "Dextop" else selected?.displayName ?: "Shizuku",
            "privilegeProviders" to providers.map {
                mapOf("id" to it.packageName, "name" to it.displayName, "kind" to it.kind)
            },
            "externalPrivilegeAuthorizationRequired" to externalAuthorizationRequired,
            "embeddedPrivilegeSetupRequired" to embeddedSetupRequired,
            "embeddedNotificationGranted" to notificationGranted,
            "wirelessDebuggingEnabled" to wirelessDebuggingEnabled,
            "shizukuBinderAlive" to binderAlive,
            "shizukuRunning" to running,
            "shizukuGranted" to granted
            ,"manufacturer" to Build.MANUFACTURER
            ,"model" to Build.MODEL
            ,"androidVersion" to Build.VERSION.RELEASE
            ,"sdk" to Build.VERSION.SDK_INT
            ,"appVersion" to packageInfo.versionName.orEmpty()
            ,"desktopMode" to DesktopEnvironmentRegistry.current().displayName
        )
        return status
    }

    private fun requestShizuku(result: MethodChannel.Result) {
        updatePrivilegeRuntimePolicy()
        if (DistributionPrivilegeRuntime.available) {
            runCatching { grantSecureSettings() }
                .onSuccess {
                    rememberPrivilegeMode("embedded")
                    result.success(true)
                }
                .onFailure { result.error("grant", it.message, null) }
            return
        }
        val current = status()
        val installed = current["shizukuInstalled"] == true
        if (!installed) {
            result.error("shizuku_missing", NativeStrings.text("nativePleaseInstallShizuku"), null)
            return
        }
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            result.error("shizuku_offline", NativeStrings.text("nativePleaseStartShizuku"), null)
            return
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            runCatching { grantSecureSettings() }
                .onSuccess {
                    rememberPrivilegeMode("external")
                    result.success(true)
                }
                .onFailure { result.error("grant", it.message, null) }
            return
        }
        if (pendingPermissionResult != null) {
            result.error("permission_busy", NativeStrings.text("nativePermissionRequestInProgress"), null)
            return
        }
        pendingPermissionResult = result
        permissionHandler.postDelayed(permissionTimeout, 15_000)
        runCatching { Shizuku.requestPermission(permissionRequestCode) }.onFailure {
            permissionHandler.removeCallbacks(permissionTimeout)
            pendingPermissionResult = null
            result.error("permission", it.message, null)
        }
    }

    private fun openShizuku(result: MethodChannel.Result) {
        val current = status()
        val provider = current["privilegeProvider"] as String
        val providerId = current["privilegeProviderId"] as? String
        val launch = providerId?.let(packageManager::getLaunchIntentForPackage)
        if (launch != null) {
            startActivity(launch)
            result.success(null)
            return
        }
        runCatching {
            val url = if (provider == "stellar") {
                "https://github.com/roro2239/Stellar/releases"
            } else if (Build.VERSION.SDK_INT >= 36) {
                "https://github.com/RikkaApps/Shizuku/releases"
            } else {
                "https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api"
            }
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onSuccess {
            result.success(null)
        }.onFailure {
            result.error("shizuku", it.message, null)
        }
    }

    private data class PrivilegeProvider(
        val packageName: String,
        val displayName: String,
        val kind: String,
    )

    private fun updatePrivilegeRuntimePolicy() {
        DistributionPrivilegeRuntime.enabled = shouldUseEmbeddedRuntime(installedPrivilegeProviders())
    }

    /**
     * An already selected bundled session remains active until the user picks an
     * external provider. This prevents an app install from disconnecting input
     * in the middle of a Dextop session.
     */
    private fun shouldUseEmbeddedRuntime(providers: List<PrivilegeProvider>): Boolean {
        if (!DistributionPrivilegeBootstrap.included) return false
        if (providers.isEmpty()) return true
        val preferences = getSharedPreferences("dextop_privilege_provider", MODE_PRIVATE)
        val lastMode = preferences.getString("last_mode", null)
        val selected = preferences.getString("selected", null)
        return lastMode == "embedded" && selected == null
    }

    private fun rememberPrivilegeMode(mode: String) {
        getSharedPreferences("dextop_privilege_provider", MODE_PRIVATE)
            .edit().putString("last_mode", mode).apply()
    }

    @Suppress("DEPRECATION")
    private fun installedPrivilegeProviders(): List<PrivilegeProvider> {
        val flags = PackageManager.GET_PERMISSIONS
        return runCatching { packageManager.getInstalledPackages(flags) }
            .getOrDefault(emptyList())
            .asSequence()
            .filter { info ->
                val packageName = info.packageName
                packageName != this.packageName && (
                    packageName == STELLAR_PACKAGE ||
                        packageName == SHEVERY_PACKAGE ||
                        packageName == "moe.shizuku.privileged.api" ||
                        packageName == "moe.shizuku.manager" ||
                        info.permissions?.any { it.name == SHIZUKU_PERMISSION } == true
                    )
            }
            .map { info ->
                PrivilegeProvider(
                    packageName = info.packageName,
                    displayName = runCatching {
                        packageManager.getApplicationLabel(info.applicationInfo!!).toString()
                    }.getOrDefault(
                        if (info.packageName == STELLAR_PACKAGE) "Stellar" else "Shizuku",
                    ),
                    kind = if (info.packageName == STELLAR_PACKAGE) "stellar" else "shizuku",
                )
            }
            .distinctBy { it.packageName }
            .sortedWith(compareBy<PrivilegeProvider> { it.kind == "stellar" }.thenBy { it.displayName })
            .toList()
    }

    private fun selectedPrivilegeProvider(providers: List<PrivilegeProvider>): PrivilegeProvider? {
        val preferences = getSharedPreferences("dextop_privilege_provider", MODE_PRIVATE)
        val saved = preferences.getString("selected", null)
        val selected = providers.firstOrNull { it.packageName == saved }
            ?: providers.firstOrNull { saved == "stellar" && it.kind == "stellar" }
            ?: providers.firstOrNull { saved == "shizuku" && it.kind == "shizuku" }
            ?: providers.singleOrNull()
            ?: providers.firstOrNull { it.kind == "shizuku" }
            ?: providers.firstOrNull()
        if (saved != null && providers.none { it.packageName == saved } && saved !in setOf("stellar", "shizuku")) {
            preferences.edit().remove("selected").apply()
        }
        return selected
    }

    private fun isStellarInstalled(): Boolean = runCatching {
        packageManager.getPackageInfo(STELLAR_PACKAGE, 0)
    }.isSuccess

    private fun isShizukuInstalled(): Boolean = installedPrivilegeProviders().any { it.kind == "shizuku" }

    private fun refreshBinderAvailability(
        provider: PrivilegeProvider? = null,
        requestIfMissing: Boolean = false
    ): Boolean {
        val alive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        shizukuBinderAvailable = alive
        if (!alive && requestIfMissing && provider?.kind == "stellar") {
            requestStellarBinder()
        }
        return alive
    }

    private fun requestStellarBinder() {
        if (!isStellarInstalled()) return
        val now = SystemClock.elapsedRealtime()
        val retryAfterMs = stellarBinderRetryGate.acquire(now) ?: return
        runCatching {
            sendBroadcast(Intent(STELLAR_REQUEST_BINDER_ACTION).setPackage(STELLAR_PACKAGE))
        }.onSuccess {
            Log.i(logTag, "requested Stellar recovery retryAfterMs=$retryAfterMs")
        }.onFailure { error ->
            Log.w(logTag, "failed to request Stellar recovery retryAfterMs=$retryAfterMs", error)
        }
    }

    private fun selectPrivilegeProvider(provider: String?, result: MethodChannel.Result) {
        val providers = installedPrivilegeProviders()
        val selected = providers.firstOrNull { it.packageName == provider }
        if (selected == null) {
            result.error("provider_unavailable", "The selected privilege provider is not installed", null)
            return
        }
        getSharedPreferences("dextop_privilege_provider", MODE_PRIVATE)
            .edit().putString("selected", provider).putString("last_mode", "external").commit()
        updatePrivilegeRuntimePolicy()
        OperationLog.i(this, "MainActivity", "privilege provider selected=${selected.packageName}")
        notifyFlutterPrivilegeStatus()
        result.success(status())
    }

    private fun startDisplay(arguments: Map<*, *>?, result: MethodChannel.Result) {
        // Auto-only Dextop owns its own virtual display. Keep the phone-side
        // companion start disabled for now; otherwise both sessions race on
        // the shared overlay specification and can attach to the wrong HOME.
        if (AndroidAutoMirrorActivity.ownsAutoSession()) {
            result.error(
                "auto_session_active",
                "Stop the Android Auto Dextop session before starting Dextop on this phone",
                null
            )
            return
        }
        val width = (arguments?.get("width") as? Number)?.toInt() ?: 1920
        val height = (arguments?.get("height") as? Number)?.toInt() ?: 1080
        val density = (arguments?.get("density") as? Number)?.toInt() ?: 240
        val secure = arguments?.get("secure") == true
        val desktopEnvironment = DesktopEnvironmentRegistry.current()
        val decorations = (arguments?.get("decorations") as? Boolean)
            ?: (desktopEnvironment.displayChromeMode == DisplayChromeMode.VISIBLE)
        if (width !in 480..7680 || height !in 480..7680 || density !in 80..640) {
            result.error("profile", NativeStrings.text("nativeDisplayProfileIsOutOfRange"), null)
            return
        }
        updatePrivilegeRuntimePolicy()
        val provider = selectedPrivilegeProvider(installedPrivilegeProviders())
        val binderReady = DistributionPrivilegeRuntime.available ||
            refreshBinderAvailability(provider, requestIfMissing = true)
        val permissionGranted = DistributionPrivilegeRuntime.available || binderReady && runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        if (!permissionGranted) {
            shizukuBinderAvailable = binderReady
            result.error(
                "shizuku_offline",
                NativeStrings.text("nativePleaseStartShizuku"),
                null
            )
            return
        }
        if (pendingStartResult != null) {
            result.error("start_busy", "A Dextop start is already in progress", null)
            return
        }
        Log.i(logTag, "startDisplay ${width}x$height/$density decorations=$decorations secure=$secure")
        rememberOrientation()
        requestedOrientation = if (height > width) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        pendingStartResult = result
        permissionHandler.postDelayed(startTimeout, 12_000)
        MirrorService.launch(this, width, height, density, secure, decorations) { outcome ->
            runOnUiThread {
                val pendingResult = pendingStartResult ?: return@runOnUiThread
                pendingStartResult = null
                permissionHandler.removeCallbacks(startTimeout)
                outcome.onSuccess { pendingResult.success(it) }
                    .onFailure { pendingResult.error("start_failed", it.message, null) }
            }
        }
    }

    private fun stopDisplay(result: MethodChannel.Result) {
        Log.i(logTag, "stopDisplay")
        permissionHandler.removeCallbacks(startTimeout)
        pendingStartResult?.error("start_cancelled", "Dextop startup was cancelled", null)
        pendingStartResult = null
        MirrorService.stopActive()
        restoreOrientation()
        result.success(null)
    }

    private fun discardRecovery(result: MethodChannel.Result) {
        Log.i(logTag, "discardRecovery")
        MirrorService.stopActive()
        runCatching {
            val journal = SessionJournal(this)
            runCatching { DisplayEnvironmentSettings(this).restoreTopology() }
                .onFailure { Log.e(logTag, "discard recovery topology restoration failed", it) }
            runCatching {
                PhoneRotationController.restoreRecorded(this, PrivilegedAccess(logTag), journal)
            }.onFailure { Log.e(logTag, "discard recovery rotation restoration failed", it) }
            journal.restoreSystemSettings()
            val own = ComponentName(this, MirrorService::class.java)
            val remaining = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty().split(':').filter { raw ->
                raw.isNotBlank() && ComponentName.unflattenFromString(raw)?.let { name ->
                    !(name.packageName == packageName && name.className == own.className)
                } != false
            }
            Settings.Secure.putString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                remaining.joinToString(":")
            )
            Settings.Secure.putInt(
                contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                if (remaining.isEmpty()) 0 else 1
            )
            runCatching {
                PrivilegedAccess(logTag).execute(
                    "cmd", "statusbar", "send-disable-flag", "none"
                )
            }
            journal.clear()
            restoreOrientation()
            getSharedPreferences("dextop_cleanup_state", MODE_PRIVATE).edit()
                .putBoolean("cleanup_pending", false)
                .putBoolean("paused_by_user", false)
                .remove("paused_workspace")
                .putLong("verified_at", System.currentTimeMillis())
                .commit()
        }.onSuccess {
            result.success(null)
        }.onFailure { error ->
            Log.e(logTag, "discard recovery failed", error)
            result.error("recovery", error.message, null)
        }
    }

    private fun repairState(): Map<String, Any> {
        val component = ComponentName(this, MirrorService::class.java)
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty().split(':').any {
            ComponentName.unflattenFromString(it)?.let { name ->
                name.packageName == packageName && name.className == component.className
            } == true
        }
        val cleanupPreferences = getSharedPreferences("dextop_cleanup_state", MODE_PRIVATE)
        val cleanupPending = cleanupPreferences.getBoolean("cleanup_pending", false)
        val cardexRepairRequired = cleanupPreferences.getBoolean(
            CardexRecoveryReceiver.KEY_REPAIR_REQUIRED,
            false
        )
        val pausedByUser = cleanupPreferences.getBoolean("paused_by_user", false)
        val recovery = SessionJournal(this).snapshot()
        val transactionOpen = recovery["transactionOpen"] == true
        val pausedSession = recovery["recoverable"] == true && recovery["phase"] == "paused"
        val autoSessionActive = AndroidAutoMirrorActivity.isAutoSessionActive() ||
            MirrorService.isAutoOnlySessionActive() ||
            CardexRelayService.isRelaySessionActive()
        val phoneSessionOwned = MirrorService.ownsPhoneSession()
        return mapOf(
            // Accessibility can legitimately remain enabled during setup/demo.
            // A user-paused session is also intentional and must be resumed or
            // discarded through the recovery card, never treated as corruption.
            // Auto owns the shared journal while the phone session is absent;
            // that is a valid independent session, not a recovery condition.
            "required" to (cardexRepairRequired ||
                (!phoneSessionOwned && !autoSessionActive &&
                    !pausedByUser && !pausedSession &&
                    (transactionOpen || cleanupPending))),
            "accessibilityResidual" to enabled,
            "displayResidual" to (cardexRepairRequired || (transactionOpen && !autoSessionActive)),
            "cleanupPending" to cleanupPending,
            "cardexInterrupted" to cardexRepairRequired,
            "pausedByUser" to pausedByUser,
            "pausedSession" to pausedSession,
            "autoActive" to autoSessionActive,
            "phoneSession" to phoneSessionOwned
        )
    }

    private fun repairAndroid(result: MethodChannel.Result) {
        runCatching {
            MirrorService.stopActive()
            val journal = SessionJournal(this)
            runCatching { DisplayEnvironmentSettings(this).restoreTopology() }
                .onFailure { Log.e(logTag, "repair topology restoration failed", it) }
            runCatching {
                PhoneRotationController.restoreRecorded(this, PrivilegedAccess(logTag), journal)
            }.onFailure { Log.e(logTag, "repair rotation restoration failed", it) }
            journal.restoreSystemSettings()
            val own = ComponentName(this, MirrorService::class.java)
            val remaining = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty().split(':').filter { raw ->
                raw.isNotBlank() && ComponentName.unflattenFromString(raw)?.let { name ->
                    !(name.packageName == packageName && name.className == own.className)
                } != false
            }
            Settings.Secure.putString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                remaining.joinToString(":")
            )
            Settings.Secure.putInt(
                contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                if (remaining.isEmpty()) 0 else 1
            )
            // Explicitly clear any disable flags associated with Dextop's
            // previous accessibility binder token. Binder death normally does
            // this, but vendor SystemUI implementations can retain the state.
            runCatching {
                val access = PrivilegedAccess(logTag)
                access.execute("cmd", "statusbar", "send-disable-flag", "none")
            }
            journal.clear()
            restoreOrientation()
            getSharedPreferences("dextop_cleanup_state", MODE_PRIVATE).edit()
                .putBoolean("cleanup_pending", false)
                .putBoolean("paused_by_user", false)
                .putBoolean(CardexRecoveryReceiver.KEY_REPAIR_REQUIRED, false)
                .remove("cardex_interruption_reason")
                .remove("cardex_interrupted_at")
                .putLong("verified_at", System.currentTimeMillis())
                .commit()
        }.onSuccess { result.success(null) }
            .onFailure { error -> result.error("repair", error.message, null) }
    }

    private fun restartApp(result: MethodChannel.Result) {
        result.success(null)
        val launch = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        } ?: return
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(launch)
            finishAffinity()
        }, 120)
    }

    private fun launchApp(arguments: Map<*, *>?, result: MethodChannel.Result) {
        val packageName = arguments?.get("package") as? String
        if (packageName.isNullOrBlank()) {
            result.error("app", NativeStrings.text("nativeNoAppSelected"), null)
            return
        }
        if (!MirrorService.isActive()) {
            result.error("display", NativeStrings.text("nativePleaseStartDextopFirst"), null)
            return
        }
        val bounds = (arguments["bounds"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }
            ?.takeIf { it.size == 4 }
            ?.let { android.graphics.Rect(it[0], it[1], it[2], it[3]) }
        val position = arguments["position"] as? String
        fun attempt(remaining: Int) {
            val launched = if (position != null) {
                MirrorService.launchPackageAt(packageName, position)
            } else {
                MirrorService.launchPackage(packageName, bounds)
            }
            if (launched) result.success(null)
            else if (remaining > 0 && MirrorService.isActive()) {
                Handler(Looper.getMainLooper()).postDelayed({ attempt(remaining - 1) }, 150)
            } else result.error("app", NativeStrings.text("nativeCouldNotStartApp"), null)
        }
        attempt(20)
    }

    private fun writeOverlaySetting(
        value: String,
        result: MethodChannel.Result,
        onSuccess: () -> Unit = {}
    ) {
        if (hasSecureSettingsPermission()) {
            Log.i(logTag, "writeOverlaySetting direct value=$value")
            runCatching {
                Settings.Global.putString(contentResolver, "overlay_display_devices", value)
            }.onSuccess {
                onSuccess()
                result.success(null)
            }.onFailure {
                Log.e(logTag, "writeOverlaySetting failed", it)
                result.error("settings", it.message, null)
            }
            return
        }
        if (!PrivilegedAccess(logTag).isAvailable()) {
            result.error("permission", NativeStrings.text("nativeRequiresConnectionToShizukuAndPermissions"), null)
            return
        }
        runCatching {
            Log.i(logTag, "writeOverlaySetting through Shizuku value=$value")
            val command = PrivilegedAccess(logTag).execute(
                "settings", "put", "global", "overlay_display_devices", value
            )
            check(command.succeeded) { command.error.ifBlank { "settings command failed" } }
        }.onSuccess {
            onSuccess()
            result.success(null)
        }.onFailure {
            Log.e(logTag, "Shizuku settings command failed", it)
            result.error("settings", it.message, null)
        }
    }

    private fun openSettings(action: String, result: MethodChannel.Result) {
        runCatching {
            startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onSuccess {
            result.success(null)
        }.onFailure {
            result.error("settings", it.message, null)
        }
    }

    private fun openWirelessDebuggingSettings(result: MethodChannel.Result) {
        if (
            DistributionPrivilegeBootstrap.included &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            if (pendingWirelessDebuggingResult != null) {
                result.error("notification_permission_busy", "Notification permission is already being requested", null)
                return
            }
            pendingWirelessDebuggingResult = result
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                EMBEDDED_NOTIFICATION_PERMISSION_REQUEST,
            )
            return
        }
        launchWirelessDebuggingSettings(result)
    }

    private fun requestEmbeddedNotificationPermission(result: MethodChannel.Result) {
        if (!DistributionPrivilegeBootstrap.included) {
            result.success(false)
            return
        }
        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            result.success(true)
            return
        }
        if (pendingNotificationPermissionResult != null || pendingWirelessDebuggingResult != null) {
            result.error("notification_permission_busy", "Notification permission is already being requested", null)
            return
        }
        pendingNotificationPermissionResult = result
        requestPermissions(
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            EMBEDDED_NOTIFICATION_PERMISSION_REQUEST,
        )
    }

    /**
     * Starts the embedded pairing flow before the user opens Wireless
     * debugging. The Play bootstrap first discovers the pairing endpoint, then
     * publishes the RemoteInput action only when Android exposes a usable
     * pairing service.
     */
    private fun prepareEmbeddedPairingNotification(result: MethodChannel.Result) {
        if (!DistributionPrivilegeBootstrap.included) {
            result.success(false)
            return
        }
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            result.error("notification_permission_required", "Notification permission is required for pairing", null)
            return
        }
        DistributionPrivilegeBootstrap.showPairingNotification(applicationContext)
        result.success(true)
    }

    private fun launchWirelessDebuggingSettings(result: MethodChannel.Result) {
        val hasStoredPairing = DistributionPrivilegeBootstrap.included &&
            DistributionPrivilegeBootstrap.hasStoredPairing(applicationContext)
        if (DistributionPrivilegeBootstrap.included && !hasStoredPairing) {
            DistributionPrivilegeBootstrap.showPairingNotification(applicationContext)
            openSystemWirelessDebuggingSettings(result)
            return
        }
        if (hasStoredPairing) {
            DistributionPrivilegeBootstrap.dismissPairingNotification(applicationContext)
            val enabledByApp = hasSecureSettingsPermission() && runCatching {
                val alreadyEnabled = Settings.Global.getInt(
                    contentResolver,
                    "adb_wifi_enabled",
                    0,
                ) == 1
                if (!alreadyEnabled) {
                    Settings.Global.putInt(contentResolver, "adb_wifi_enabled", 1)
                }
                Settings.Global.getInt(contentResolver, "adb_wifi_enabled", 0) == 1
            }.onFailure {
                OperationLog.w(
                    this,
                    "EmbeddedPrivilege",
                    "app-side wireless debugging enable failed: ${it.javaClass.simpleName}",
                )
            }.getOrDefault(false)
            if (enabledByApp) {
                distributionScope.launch {
                    val connected = runCatching {
                        withTimeoutOrNull(4_000) {
                            DistributionPrivilegeBootstrap.canConnect(applicationContext)
                        }
                    }.onFailure {
                        Log.w(logTag, "Embedded Binder connection failed after app-side enable", it)
                    }.getOrDefault(false) ?: false
                    runOnUiThread {
                        if (connected) {
                            distributionScope.launch {
                                val started = runCatching {
                                    DistributionPrivilegeBootstrap.connectAndStart(applicationContext).started
                                }.getOrDefault(false)
                                runOnUiThread {
                                    if (started) {
                                        DistributionPrivilegeBootstrap.dismissPairingNotification(applicationContext)
                                        rememberPrivilegeMode("embedded")
                                        notifyFlutterPrivilegeStatus()
                                        result.success(null)
                                    } else {
                                        DistributionPrivilegeBootstrap.dismissPairingNotification(applicationContext)
                                        openSystemWirelessDebuggingSettings(result)
                                    }
                                }
                            }
                        } else {
                            DistributionPrivilegeBootstrap.dismissPairingNotification(applicationContext)
                            OperationLog.w(
                                this@MainActivity,
                                "EmbeddedPrivilege",
                                "app-side enable did not produce a Binder; opening system settings",
                            )
                            openSystemWirelessDebuggingSettings(result)
                        }
                    }
                }
                return
            }
            OperationLog.w(
                this,
                "EmbeddedPrivilege",
                "app-side wireless debugging enable unavailable; opening system settings",
            )
            DistributionPrivilegeBootstrap.dismissPairingNotification(applicationContext)
        }
        openSystemWirelessDebuggingSettings(result)
    }

    private fun openSystemWirelessDebuggingSettings(result: MethodChannel.Result) {
        val actions = listOf(
            "android.settings.WIRELESS_DEBUGGING_SETTINGS",
            Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS,
        )
        val launched = actions.firstNotNullOfOrNull { action ->
            val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(packageManager) == null) return@firstNotNullOfOrNull null
            runCatching { startActivity(intent) }.getOrNull()?.let { action }
        }
        if (launched != null) {
            OperationLog.i(this, "MainActivity", "opened wireless debugging settings action=$launched")
            retryEmbeddedPrivilegeWhileSettingsVisible()
            result.success(null)
        } else {
            result.error(
                "settings_unavailable",
                "Wireless debugging settings are unavailable on this device",
                null,
            )
        }
    }

    private fun retryEmbeddedPrivilegeWhileSettingsVisible() {
        if (
            !DistributionPrivilegeBootstrap.included ||
            !DistributionPrivilegeBootstrap.hasStoredPairing(applicationContext) ||
            installedPrivilegeProviders().isNotEmpty()
        ) return
        distributionScope.launch {
            repeat(8) { attempt ->
                if (DistributionPrivilegeRuntime.available) return@launch
                val connected = runCatching {
                    withTimeoutOrNull(3_500) {
                        DistributionPrivilegeBootstrap.canConnect(applicationContext)
                    }
                }.getOrDefault(false) ?: false
                if (!connected) {
                    // The saved public key may have been removed from Android's
                    // Wireless debugging screen.  This is the explicit
                    // re-pairing path: keep the stored identity, but restart
                    // pairing-service discovery so a newly shown device code
                    // receives its RemoteInput notification just like first
                    // setup.  Previously this branch dismissed the notification
                    // and made re-pairing impossible.
                    embeddedRestoreNeedsSetup = true
                    DistributionPrivilegeBootstrap.showPairingNotification(applicationContext)
                    OperationLog.i(
                        this@MainActivity,
                        "EmbeddedPrivilege",
                        "stored pairing unavailable; started re-pairing discovery",
                    )
                    notifyFlutterPrivilegeStatus()
                    return@launch
                }
                val started = runCatching {
                    DistributionPrivilegeBootstrap.connectAndStart(applicationContext).started
                }.getOrDefault(false)
                if (started) {
                    DistributionPrivilegeBootstrap.dismissPairingNotification(applicationContext)
                    rememberPrivilegeMode("embedded")
                    OperationLog.i(
                        this@MainActivity,
                        "EmbeddedPrivilege",
                        "Binder restored while wireless settings visible attempt=${attempt + 1}",
                    )
                    notifyFlutterPrivilegeStatus()
                    return@launch
                }
                delay(750)
            }
            OperationLog.w(
                this@MainActivity,
                "EmbeddedPrivilege",
                "Binder was not restored while wireless settings were open",
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != EMBEDDED_NOTIFICATION_PERMISSION_REQUEST) return
        val permissionGranted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        pendingNotificationPermissionResult?.let { result ->
            pendingNotificationPermissionResult = null
            if (permissionGranted && DistributionPrivilegeBootstrap.included &&
                !DistributionPrivilegeBootstrap.hasStoredPairing(applicationContext)
            ) {
                DistributionPrivilegeBootstrap.showPairingNotification(applicationContext)
            }
            result.success(permissionGranted)
            return
        }
        val result = pendingWirelessDebuggingResult ?: return
        pendingWirelessDebuggingResult = null
        if (permissionGranted) {
            launchWirelessDebuggingSettings(result)
        } else {
            result.error(
                "notification_permission_denied",
                "Notification permission is required to enter the pairing code while Wireless debugging is open",
                null,
            )
        }
    }

    private fun hasSecureSettingsPermission(): Boolean {
        return checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
    }

    private fun grantSecureSettings() {
        if (hasSecureSettingsPermission()) return
        Log.i(logTag, "grantSecureSettings package=$packageName")
        val command = PrivilegedAccess(logTag).execute(
            "pm", "grant", packageName, Manifest.permission.WRITE_SECURE_SETTINGS
        )
        check(command.succeeded) {
            command.error.ifBlank { "WRITE_SECURE_SETTINGS grant failed" }
        }
        Log.i(logTag, "grantSecureSettings complete")
    }

}
