package moe.n4tsu.dextop

import android.Manifest
import android.content.Intent
import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.ParcelFileDescriptor
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
import java.io.InputStream
import java.io.OutputStream
import moe.shizuku.server.IRemoteProcess
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku

open class MainActivity : FlutterActivity() {
    companion object {
        private const val STELLAR_PACKAGE = "roro.stellar.manager"
        private const val STELLAR_REQUEST_BINDER_ACTION = "roro.stellar.intent.action.REQUEST_BINDER"

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

        fun setDisplayOrientation(portrait: Boolean) {
            instance?.requestedOrientation = if (portrait) {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
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

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        stellarBinderRetryGate.reset()
        refreshBinderAvailability()
        Log.i(logTag, "privilege binder received alive=$shizukuBinderAvailable")
        flutterChannel?.invokeMethod("shizukuStatusChanged", null)
    }
    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        val provider = selectedPrivilegeProvider(isStellarInstalled(), isShizukuInstalled())
        refreshBinderAvailability(provider, requestIfMissing = true)
        Log.w(logTag, "privilege binder death reported alive=$shizukuBinderAvailable provider=$provider")
        flutterChannel?.invokeMethod("shizukuStatusChanged", null)
    }
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == permissionRequestCode) {
            permissionHandler.removeCallbacks(permissionTimeout)
            Log.i(logTag, "Shizuku permission result=$grantResult")
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
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
        if (display?.displayId == android.view.Display.DEFAULT_DISPLAY) {
            instance = this
            phoneTaskId = taskId
            if (!MirrorService.isActive()) {
                runCatching { DisplayEnvironmentSettings(this).prepareInactiveState() }
                    .onFailure { Log.w(logTag, "inactive topology cleanup deferred", it) }
            }
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
        Shizuku.addRequestPermissionResultListener(permissionListener)
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        flutterChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, channelName)
        flutterChannel?.setMethodCallHandler { call, result ->
            when (call.method) {
                "status" -> result.success(status())
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
                "openWirelessDebugging" -> openSettings("android.settings.WIRELESS_DEBUGGING_SETTINGS", result)
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
        super.cleanUpFlutterEngine(flutterEngine)
    }

    private fun status(): Map<String, Any> {
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val shizukuInstalled = isShizukuInstalled()
        val stellarInstalled = isStellarInstalled()
        val savedProvider = getSharedPreferences("dextop_privilege_provider", MODE_PRIVATE)
            .getString("selected", null)
        val provider = selectedPrivilegeProvider(stellarInstalled, shizukuInstalled)
        val installed = if (provider == "stellar") stellarInstalled else shizukuInstalled
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
        val binderAlive = installed && refreshBinderAvailability(
            provider,
            requestIfMissing = true
        )
        // The guided setup uses wireless debugging. A live/stale binder alone is
        // not enough to mark that setup as completed.
        // The service may be started through wireless debugging, wired ADB, or
        // root. Binder liveness is the authoritative running-state signal.
        val running = binderAlive
        val granted = running && runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        val status = mapOf(
            "active" to MirrorService.isActive(),
            "privileged" to hasSecureSettingsPermission(),
            "shizukuInstalled" to installed,
            "stellarInstalled" to stellarInstalled,
            "originalShizukuInstalled" to shizukuInstalled,
            "bothPrivilegeProvidersInstalled" to (stellarInstalled && shizukuInstalled),
            "privilegeProviderSelectionRequired" to
                (stellarInstalled && shizukuInstalled && savedProvider !in setOf("stellar", "shizuku")),
            "privilegeProvider" to provider,
            "privilegeProviderName" to if (provider == "stellar") "Stellar" else "Shizuku",
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
                .onSuccess { result.success(true) }
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
        val launch = if (provider == "stellar") {
            packageManager.getLaunchIntentForPackage("roro.stellar.manager")
        } else {
            packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                ?: packageManager.getLaunchIntentForPackage("moe.shizuku.manager")
        }
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

    private fun selectedPrivilegeProvider(stellarInstalled: Boolean, shizukuInstalled: Boolean): String {
        val preferences = getSharedPreferences("dextop_privilege_provider", MODE_PRIVATE)
        val saved = preferences.getString("selected", null)
        val selected = when {
            stellarInstalled && shizukuInstalled && saved in setOf("stellar", "shizuku") -> saved!!
            stellarInstalled -> "stellar"
            shizukuInstalled -> "shizuku"
            else -> "stellar"
        }
        // The explicit choice remains valid only while both managers stay
        // installed. Once either is removed, discard it so a later coexistence
        // requires a fresh conflict-avoidance choice.
        if (!(stellarInstalled && shizukuInstalled) && saved != null) {
            preferences.edit().remove("selected").apply()
        }
        return selected
    }

    private fun isStellarInstalled(): Boolean = runCatching {
        packageManager.getPackageInfo(STELLAR_PACKAGE, 0)
    }.isSuccess

    private fun isShizukuInstalled(): Boolean = runCatching {
        packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
    }.isSuccess || runCatching {
        packageManager.getPackageInfo("moe.shizuku.manager", 0)
    }.isSuccess

    private fun refreshBinderAvailability(
        provider: String? = null,
        requestIfMissing: Boolean = false
    ): Boolean {
        val alive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        shizukuBinderAvailable = alive
        if (!alive && requestIfMissing && provider == "stellar") {
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
        val stellarInstalled = isStellarInstalled()
        val shizukuInstalled = isShizukuInstalled()
        if (provider !in setOf("stellar", "shizuku") ||
            (provider == "stellar" && !stellarInstalled) ||
            (provider == "shizuku" && !shizukuInstalled)) {
            result.error("provider_unavailable", "The selected privilege provider is not installed", null)
            return
        }
        getSharedPreferences("dextop_privilege_provider", MODE_PRIVATE)
            .edit().putString("selected", provider).commit()
        OperationLog.i(this, "MainActivity", "privilege provider selected=$provider")
        flutterChannel?.invokeMethod("shizukuStatusChanged", null)
        result.success(status())
    }

    private fun startDisplay(arguments: Map<*, *>?, result: MethodChannel.Result) {
        val width = (arguments?.get("width") as? Number)?.toInt() ?: 1920
        val height = (arguments?.get("height") as? Number)?.toInt() ?: 1080
        val density = (arguments?.get("density") as? Number)?.toInt() ?: 240
        val secure = arguments?.get("secure") == true
        val decorations = (arguments?.get("decorations") as? Boolean)
            ?: !Build.MANUFACTURER.equals("samsung", ignoreCase = true)
        if (width !in 480..7680 || height !in 480..7680 || density !in 80..640) {
            result.error("profile", NativeStrings.text("nativeDisplayProfileIsOutOfRange"), null)
            return
        }
        val provider = selectedPrivilegeProvider(isStellarInstalled(), isShizukuInstalled())
        val binderReady = refreshBinderAvailability(provider, requestIfMissing = true)
        val permissionGranted = binderReady && runCatching {
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
        val pausedByUser = cleanupPreferences.getBoolean("paused_by_user", false)
        val recovery = SessionJournal(this).snapshot()
        val transactionOpen = recovery["transactionOpen"] == true
        val pausedSession = recovery["recoverable"] == true && recovery["phase"] == "paused"
        return mapOf(
            // Accessibility can legitimately remain enabled during setup/demo.
            // A user-paused session is also intentional and must be resumed or
            // discarded through the recovery card, never treated as corruption.
            "required" to (!MirrorService.isActive() &&
                !pausedByUser && !pausedSession &&
                (transactionOpen || cleanupPending)),
            "accessibilityResidual" to enabled,
            "displayResidual" to transactionOpen,
            "cleanupPending" to cleanupPending,
            "pausedByUser" to pausedByUser,
            "pausedSession" to pausedSession
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
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false) ||
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED
        ) {
            result.error("permission", NativeStrings.text("nativeRequiresConnectionToShizukuAndPermissions"), null)
            return
        }
        runCatching {
            Log.i(logTag, "writeOverlaySetting through Shizuku value=$value")
            val process = remoteProcess(
                arrayOf("settings", "put", "global", "overlay_display_devices", value)
            )
            val error = process.errorStream.bufferedReader().use { it.readText() }
            val exit = process.waitFor()
            check(exit == 0) { error.ifBlank { "settings command failed" } }
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

    private fun hasSecureSettingsPermission(): Boolean {
        return checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
    }

    private fun remoteProcess(command: Array<String>): Process {
        val binder = Shizuku.getBinder()
            ?: error(NativeStrings.text("nativeShizukuBinderUnavailable"))
        val remote = IShizukuService.Stub.asInterface(binder).newProcess(command, null, null)
        return BinderProcess(remote)
    }

    private fun grantSecureSettings() {
        if (hasSecureSettingsPermission()) return
        Log.i(logTag, "grantSecureSettings package=$packageName")
        val process = remoteProcess(
            arrayOf(
                "pm",
                "grant",
                packageName,
                Manifest.permission.WRITE_SECURE_SETTINGS
            )
        )
        val error = process.errorStream.bufferedReader().use { it.readText() }
        val exit = process.waitFor()
        check(exit == 0) { error.ifBlank { "WRITE_SECURE_SETTINGS grant failed" } }
        Log.i(logTag, "grantSecureSettings complete")
    }

    private class BinderProcess(private val remote: IRemoteProcess) : Process() {
        private val input by lazy { ParcelFileDescriptor.AutoCloseInputStream(remote.inputStream) }
        private val output by lazy { ParcelFileDescriptor.AutoCloseOutputStream(remote.outputStream) }
        private val error by lazy { ParcelFileDescriptor.AutoCloseInputStream(remote.errorStream) }

        override fun getInputStream(): InputStream = input
        override fun getOutputStream(): OutputStream = output
        override fun getErrorStream(): InputStream = error
        override fun waitFor(): Int = remote.waitFor()
        override fun exitValue(): Int = remote.exitValue()
        override fun destroy() = remote.destroy()
        override fun destroyForcibly(): Process = apply { destroy() }
        override fun isAlive(): Boolean = remote.alive()
    }
}
