package moe.n4tsu.dextop

import android.accessibilityservice.AccessibilityService
import android.app.ActivityOptions
import android.app.KeyguardManager
import android.media.AudioManager
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.content.Intent
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.BitmapFactory
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.Paint
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.input.InputManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Binder
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.transition.ChangeBounds
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.util.Log
import android.util.Base64
import android.view.Gravity
import android.view.DragEvent
import android.view.Display
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.view.WindowInsets
import android.view.animation.PathInterpolator
import android.view.inputmethod.InputMethodManager
import android.view.accessibility.AccessibilityEvent
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.HorizontalScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.animation.ValueAnimator
import android.animation.LayoutTransition
import android.widget.GridLayout
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import moe.shizuku.server.IShizukuService
import kotlin.math.hypot
import org.json.JSONArray
import org.json.JSONObject
import org.lsposed.hiddenapibypass.HiddenApiBypass

class MirrorService : AccessibilityService(), SurfaceHolder.Callback {
    data class Config(
        val width: Int,
        val height: Int,
        val density: Int,
        val secure: Boolean = false,
        val decorations: Boolean = false
    )
    companion object {
        private const val PREFS = "freedextop_input"
        private const val KEY_DIRECT_TOUCH = "direct_touch"
        private const val KEY_ROUTE_MOUSE = "route_physical_mouse"
        private const val KEY_ROUTE_KEYBOARD = "route_physical_keyboard"
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        private const val NOTIFICATION_LAUNCH_WINDOW_MS = 3_000L
        private const val NOTIFICATION_ROUTE_RETRY_DELAY_MS = 140L
        private const val NOTIFICATION_ROUTE_RETRIES = 5
        private const val HOST_DISPLAY_MONITOR_INTERVAL_MS = 1_000L
        private const val LAPTOP_SHIFT = -1
        private const val LAPTOP_CONTROL = -2
        private const val LAPTOP_ALT = -3
        private const val LAPTOP_CAPS = -4
        private const val DEBUG_FORCE_LAPTOP_MODE = false
        private const val STATUS_BAR_INTERFACE = "com.android.internal.statusbar.IStatusBarService"
        private const val PHONE_NAVIGATION_DISABLE_FLAGS =
            0x00200000 or 0x00400000 or 0x01000000
        private var instance: MirrorService? = null
        private var pending: Config? = null
        private var pendingStartResult: ((Result<Map<String, Any>>) -> Unit)? = null
        private var pendingDemo = false
        private var pendingLaptopDemo = false
        private var active = false

        fun launch(
            context: Context,
            width: Int,
            height: Int,
            density: Int,
            secure: Boolean,
            decorations: Boolean,
            completion: (Result<Map<String, Any>>) -> Unit
        ) {
            val running = instance
            if (active && running != null && running.targetDisplayId >= 0 &&
                secure == running.secureDisplay && decorations == running.showSystemDecorations) {
                val requested = Config(width, height, density, secure, decorations)
                running.root?.post {
                    runCatching {
                        val next = running.effectiveConfig(requested)
                        running.resizeActiveDisplay(next, "resolution changed from Android UI")
                        mapOf(
                            "displayId" to running.targetDisplayId,
                            "width" to running.targetWidth,
                            "height" to running.targetHeight,
                            "density" to running.density,
                            "decorations" to running.showSystemDecorations
                        )
                    }.onSuccess { completion(Result.success(it)) }
                        .onFailure { completion(Result.failure(it)) }
                } ?: completion(Result.failure(IllegalStateException("Dextop overlay is unavailable")))
                return
            }
            pendingStartResult?.invoke(Result.failure(IllegalStateException("A Dextop start is already in progress")))
            pendingStartResult = completion
            pending = Config(width, height, density, secure, decorations)
            val component = ComponentName(context, MirrorService::class.java).flattenToString()
            val current = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty()
            val services = current.split(':').filter { it.isNotBlank() }.toMutableSet()
            services.add(component)
            Settings.Secure.putString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                services.joinToString(":")
            )
            Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            instance?.start(pending!!)
        }

        private fun completeStart(value: Result<Map<String, Any>>) {
            val callback = pendingStartResult ?: return
            pendingStartResult = null
            callback(value)
        }

        fun isActive(): Boolean = active

        fun isFoldableDevice(): Boolean = instance?.isFoldableDevice() == true

        fun updateLaptopModeEnabled(enabled: Boolean) {
            instance?.root?.post { instance?.applyFlutterLaptopModeSetting(enabled) }
        }

        /** Shows the real laptop deck on the active virtual-display session. */
        fun showLaptopPreview(): Boolean {
            val service = instance ?: return false
            if (!active || service.root == null) return false
            service.root?.post {
                service.laptopManualOverride = true
                service.setLaptopMode(true)
            }
            return true
        }

        fun showLaptopDemo(context: Context) {
            pendingDemo = true
            pendingLaptopDemo = true
            val component = ComponentName(context, MirrorService::class.java).flattenToString()
            val current = Settings.Secure.getString(context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            Settings.Secure.putString(context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                (current.split(':').filter { it.isNotBlank() } + component).distinct().joinToString(":"))
            Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            instance?.showDemoWindow()
        }

        fun activeDisplayId(): Int = instance?.targetDisplayId ?: -1

        fun launchPackage(packageName: String, bounds: android.graphics.Rect? = null): Boolean = instance?.let { service ->
            runCatching {
                val fittedBounds = bounds?.let {
                    service.workspaceLayoutEngine.fit(
                        service.targetDisplayId,
                        service.targetWidth,
                        service.targetHeight,
                        it
                    )
                }
                AppCatalog(service).launch(
                    packageName,
                    service.targetDisplayId,
                    fittedBounds
                )
                service.launchedAppBounds[packageName] = fittedBounds
                    ?: Rect(0, 0, service.targetWidth, service.targetHeight)
            }.onFailure { Log.e(service.logTag, "app launch failed", it) }.isSuccess
        } ?: false

        fun launchPackageAt(packageName: String, position: String): Boolean = instance?.let { service ->
            val bounds = service.workspaceLayoutEngine.position(
                service.targetDisplayId,
                service.targetWidth,
                service.targetHeight,
                position
            )
            // Re-launching to repair bounds steals focus and can make Samsung's
            // freeform desktop minimize the other workspace windows. Launch
            // exactly once; WorkspaceLayoutEngine has already fitted the bounds.
            launchPackage(packageName, bounds)
        } ?: false

        fun inputMode(): String = instance?.let {
            when {
                it.physicalMouseActive -> "mouse"
                it.directTouch -> "touch"
                else -> "trackpad"
            }
        } ?: "idle"

        fun topologyOverlayDisplayId(): Int = instance?.mirrorDisplayId ?: -1

        fun setPerformanceHud(enabled: Boolean) {
            instance?.performanceHud?.visibility = if (enabled) View.VISIBLE else View.GONE
        }

        fun setKeepAwake(enabled: Boolean) {
            instance?.updateKeepAwake(enabled)
        }

        fun measuredFps(): Double = instance?.performanceHud?.fps() ?: 0.0

        fun stopActive() {
            instance?.stop()
        }

        fun showOverlayDemo(context: Context) {
            pendingDemo = true
            val component = ComponentName(context, MirrorService::class.java).flattenToString()
            val current = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty()
            val services = current.split(':').filter { it.isNotBlank() }.toMutableSet()
            services.add(component)
            Settings.Secure.putString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                services.joinToString(":")
            )
            Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            instance?.showDemoWindow()
        }

        fun hideOverlayDemo() {
            instance?.hideDemoWindow()
            pendingDemo = false
        }


    }

    private val logTag = "DextopMirror"
    private val desktopEnvironment by lazy { DesktopEnvironmentRegistry.current() }
    private val privilegedAccess by lazy { PrivilegedAccess(logTag) }
    private val desktopModeConfigurator by lazy {
        DesktopModeConfigurator(this, contentResolver, privilegedAccess, desktopEnvironment, sessionJournal)
    }
    private val phoneRotationController by lazy {
        PhoneRotationController(this, privilegedAccess, sessionJournal)
    }
    private val workspaceLayoutEngine by lazy {
        WorkspaceLayoutEngine(this, desktopEnvironment, logTag)
    }
    private val resolutionRepository by lazy { ResolutionRepository(this, logTag) }
    private val inputDispatcher by lazy { InputDispatcher(privilegedAccess) }
    private val physicalInputRouter by lazy { PhysicalInputRouter(this, privilegedAccess) }
    private val externalDisplayDetector by lazy { ExternalDisplayDetector(this) }
    private val sessionJournal by lazy { SessionJournal(this) }
    private val internalRefreshRateController by lazy {
        InternalRefreshRateController(this, sessionJournal)
    }
    private val displayBackend by lazy {
        DisplayMirrorBackend(
            this,
            contentResolver,
            getSystemService(DisplayManager::class.java),
            privilegedAccess,
            desktopEnvironment
        )
    }
    private var windowManager: WindowManager? = null
    private var root: TouchRoutingFrame? = null
    private var rootWindowParams: WindowManager.LayoutParams? = null
    private var surfaceView: SurfaceView? = null
    private var cursorView: CursorView? = null
    private var menu: LinearLayout? = null
    private var menuPrimary: LinearLayout? = null
    private var workspaceExpanded = false
    private var pendingPausedWorkspace: JSONObject? = null
    private var overlayLayoutEditing = false
    private val launchedAppBounds = linkedMapOf<String, Rect>()
    private var workspaceSaveError: String? = null
    private var pausedForAndroid = false
    private var stopping = false
    private var menuScrim: View? = null
    private var demoMode = false
    private var demoInfoView: TextView? = null
    private var performanceHud: PerformanceHud? = null
    private var laptopContent: LinearLayout? = null
    private var laptopDeck: View? = null
    private var laptopDeckContent: LinearLayout? = null
    private var laptopSettingsVisible = false
    private var laptopFunctionRowVisible = false
    private var laptopKeyboardView: LinearLayout? = null
    private var laptopFnButton: TextView? = null
    private var laptopModeActive = false
    private var laptopManualOverride = false
    private var laptopAutoActivated = false
    private var laptopHardwareKeyboardProcess: moe.shizuku.server.IRemoteProcess? = null
    private var laptopHardwareKeyboardInput: OutputStream? = null
    private var laptopHostUniqueId: String? = null
    private var laptopShift = false
    private var laptopControl = false
    private var laptopAlt = false
    private var laptopCapsLock = false
    private val laptopTypeface: Typeface by lazy {
        Typeface.createFromAsset(assets, "fonts/HarmonyOS_Sans_Medium.ttf")
    }
    private val materialIconTypeface: Typeface by lazy {
        Typeface.createFromAsset(assets, "flutter_assets/fonts/MaterialIcons-Regular.otf")
    }
    private val laptopModifierButtons = mutableMapOf<Int, TextView>()
    private val laptopShortcutButtons = mutableMapOf<Int, TextView>()
    private val laptopLegendButtons = mutableListOf<Pair<LaptopKeyTextView, String>>()
    private var targetDisplayId = -1
    private var targetWidth = 1920
    private var targetHeight = 1080
    private var density = 240
    private var secureDisplay = false
    private var showSystemDecorations = false
    private var mirrorDisplayId = -1
    private var displayCreationInProgress = false
    private var overlayTextInputActive = false
    private var cursorX = 960f
    private var cursorY = 540f
    private var lastX = 0f
    private var lastY = 0f
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var downTime = 0L
    private var injectedDownTime = 0L
    private var moved = false
    private var maxPointers = 0
    private var twoFinger = false
    private var threeFinger = false
    private var scrolling = false
    private var lastScrollY = 0f
    private var scrollY = 0f
    private var dragHeld = false
    private var directTouch = false
    private var directTouchHeld = false
    private var injectedDirectTouchActive = false
    private var directInjectionDownTime = 0L
    private var directSourceDownTime = 0L
    private var lastInjectedDirectTouch: MotionEvent? = null
    private var experimentalMultiTouch = false
    private var threeFingerEdgeSwipe = false
    private var edgeMenuTriggered = false
    private var edgeGestureLeadX = 0f
    private var edgeGestureLeadY = 0f
    private var physicalMouseActive = false
    private var routePhysicalMouseToDextop = true
    private var routePhysicalKeyboardToDextop = true
    private var notificationLaunchArmedUntil = 0L
    private var notificationRouteGeneration = 0
    private var mouseActuallyRouted = false
    private var keyboardActuallyRouted = false
    private var physicalExternalDisplayConnected = false
    private var refreshRateReapplyGeneration = 0
    private var topologyReapplyGeneration = 0
    private val physicalInputRoutingSupported: Boolean
        get() = !Build.MANUFACTURER.equals("samsung", ignoreCase = true)
    private var mouseReaderProcess: moe.shizuku.server.IRemoteProcess? = null
    @Volatile private var mouseReaderRunning = false
    private var inputManager: InputManager? = null
    private var sensorManager: SensorManager? = null
    private var hingeAngle: Float? = null
    private var filteredHingeAngle: Float? = null
    private var pendingLaptopMode: Boolean? = null
    private var pendingLaptopModeSince = 0L
    private val laptopModeDebounceMs = 280L
    private val hingeListener = object : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

        override fun onSensorChanged(event: SensorEvent?) {
            val angle = event?.values?.firstOrNull() ?: return
            hingeAngle = angle
            Log.d(logTag, "hinge angle=$angle laptop=$laptopModeActive")
            updateLaptopModeForHinge(angle)
        }
    }
    private val inputDeviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = refreshPhysicalInputState()
        override fun onInputDeviceRemoved(deviceId: Int) = refreshPhysicalInputState()
        override fun onInputDeviceChanged(deviceId: Int) = refreshPhysicalInputState()
    }
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            refreshExternalDisplayState()
            scheduleInternal120HzReapply(requireExternalDisplay = true)
            scheduleTopologyReapplyAfterReconnect()
            if (active && displayId == Display.DEFAULT_DISPLAY) {
                scheduleHostDisplayReconfiguration("default display added")
            }
        }
        override fun onDisplayRemoved(displayId: Int) {
            refreshExternalDisplayState()
            scheduleInternal120HzReapply(requireExternalDisplay = false)
            if (active && displayId == Display.DEFAULT_DISPLAY) {
                scheduleHostDisplayReconfiguration("default display removed")
            }
        }
        override fun onDisplayChanged(displayId: Int) {
            refreshExternalDisplayState()
            scheduleInternal120HzReapply(requireExternalDisplay = true)
            if (active && displayId == Display.DEFAULT_DISPLAY) {
                leaveLaptopModeOnCoverDisplay()
                scheduleHostDisplayReconfiguration("default display changed")
            } else if (active && displayId == targetDisplayId && mirrorDisplayId >= 0) {
                scheduleMirrorRefresh("source display changed")
            }
        }
    }

    private fun scheduleInternal120HzReapply(requireExternalDisplay: Boolean) {
        if (!active) return
        if (requireExternalDisplay && !externalDisplayDetector.snapshot().connected) return
        val generation = ++refreshRateReapplyGeneration
        android.os.Handler(mainLooper).postDelayed({
            if (generation != refreshRateReapplyGeneration || !active) return@postDelayed
            if (requireExternalDisplay && !externalDisplayDetector.snapshot().connected) {
                return@postDelayed
            }
            runCatching { internalRefreshRateController.applyIfEnabled() }
                .onFailure { Log.e(logTag, "120 Hz reapply after display change failed", it) }
        }, 650)
    }

    private fun scheduleTopologyReapplyAfterReconnect() {
        if (!active) return
        val generation = ++topologyReapplyGeneration
        android.os.Handler(mainLooper).postDelayed({
            if (generation != topologyReapplyGeneration || !active) return@postDelayed
            runCatching {
                DisplayEnvironmentSettings(this).activateTopologyIfEnabled(mirrorDisplayId)
            }
                .onFailure { Log.e(logTag, "display topology reapply after reconnect failed", it) }
        }, 750)
    }
    private val navigationToken = Binder()
    private var navigationRestoreGeneration = 0
    private var screenReceiverRegistered = false
    private var suspendedForLockScreen = false
    private var suspendedConfig: Config? = null
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!active) return
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> suspendForLockScreen()
                Intent.ACTION_SCREEN_ON -> waitForConfirmedUnlock()
                Intent.ACTION_USER_PRESENT -> resumeAfterUnlock()
            }
        }
    }
    private var longPressTriggered = false
    private var longPressRunnable: Runnable? = null
    private var mirrorHostWidth = 0
    private var mirrorHostHeight = 0
    private var mirrorRefreshGeneration = 0
    private var hostReconfigurationGeneration = 0
    private val hostDisplayMonitorHandler by lazy { android.os.Handler(mainLooper) }
    private var observedHostWidth = 0
    private var observedHostHeight = 0
    private var observedHostDensity = 0
    private val hostDisplayMonitor = object : Runnable {
        override fun run() {
            if (!active || suspendedForLockScreen) return
            if (laptopModeActive &&
                !isDebugLaptopModeForced() &&
                laptopHostUniqueId != null &&
                laptopHostUniqueId != defaultDisplayUniqueId()) {
                // A different internal panel became the default display. This
                // is the reliable cover-display signal; failure to enumerate
                // the inactive inner panel must never dismiss laptop mode.
                laptopManualOverride = false
                setLaptopMode(false)
                hostDisplayMonitorHandler.postDelayed(this, HOST_DISPLAY_MONITOR_INTERVAL_MS)
                return
            }
            val bounds = windowManager?.currentWindowMetrics?.bounds
            val host = surfaceView
            val width = host?.width?.takeIf { it > 0 } ?: bounds?.width() ?: 0
            val height = host?.height?.takeIf { it > 0 } ?: bounds?.height() ?: 0
            val hostDensity = resources.configuration.densityDpi
            if (width >= 480 && height >= 480) {
                val changed = observedHostWidth > 0 &&
                    (width != observedHostWidth || height != observedHostHeight ||
                        hostDensity != observedHostDensity)
                observedHostWidth = width
                observedHostHeight = height
                observedHostDensity = hostDensity
                if (shouldFollowHostDisplay() && hostSizeDiffersFromTarget(width, height)) {
                    scheduleHostDisplayReconfiguration(
                        "periodic host geometry check", width, height, hostDensity
                    )
                } else if (changed && mirrorDisplayId >= 0) {
                    scheduleMirrorRefresh("periodic host geometry check", width, height)
                }
            }
            hostDisplayMonitorHandler.postDelayed(this, HOST_DISPLAY_MONITOR_INTERVAL_MS)
        }
    }

    override fun onServiceConnected() {
        HiddenApiBypass.addHiddenApiExemptions("")
        directTouch = getSharedPreferences(PREFS, MODE_PRIVATE)
            .getBoolean(KEY_DIRECT_TOUCH, false)
        routePhysicalMouseToDextop = getSharedPreferences(PREFS, MODE_PRIVATE)
            .getBoolean(KEY_ROUTE_MOUSE, true)
        routePhysicalKeyboardToDextop = getSharedPreferences(PREFS, MODE_PRIVATE)
            .getBoolean(KEY_ROUTE_KEYBOARD, true)
        experimentalMultiTouch = true
        instance = this
        windowManager = getSystemService(WindowManager::class.java)
        inputManager = getSystemService(InputManager::class.java).also {
            it.registerInputDeviceListener(inputDeviceListener, null)
        }
        sensorManager = getSystemService(SensorManager::class.java).also { manager ->
            manager.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)?.let { sensor ->
                manager.registerListener(hingeListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                Log.i(logTag, "hinge-angle sensor registered: ${sensor.name}")
            } ?: Log.i(logTag, "hinge-angle sensor unavailable")
        }
        getSystemService(DisplayManager::class.java).registerDisplayListener(displayListener, null)
        physicalExternalDisplayConnected = physicalInputRoutingSupported && externalDisplayDetector.snapshot().connected
        if (!physicalInputRoutingSupported) runCatching { physicalInputRouter.restore() }
        physicalMouseActive = false
        if (!screenReceiverRegistered) {
            registerReceiver(
                screenReceiver,
                IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_OFF)
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_USER_PRESENT)
                },
                RECEIVER_NOT_EXPORTED
            )
            screenReceiverRegistered = true
        }
        Log.i(logTag, "accessibility connected")
        if (pendingDemo) {
            showDemoWindow()
        } else pending?.let { start(it) } ?: run {
            // Restore only settings owned by an interrupted Dextop transaction.
            // Never delete an arbitrary overlay configured by the user or another app.
            if (sessionJournal.snapshot()["transactionOpen"] == true) {
                runCatching { DisplayEnvironmentSettings(this).restoreTopology() }
                    .onFailure { Log.e(logTag, "interrupted topology restoration failed", it) }
                runCatching { sessionJournal.restoreSystemSettings() }
                    .onSuccess { Log.i(logTag, "restored settings from interrupted Dextop session") }
                    .onFailure { Log.e(logTag, "interrupted session restoration failed", it) }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!active || targetDisplayId < 0 || event == null) return
        val eventPackage = event.packageName?.toString().orEmpty()
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                if (eventPackage != SYSTEM_UI_PACKAGE || event.displayId != targetDisplayId) return
                notificationLaunchArmedUntil = SystemClock.uptimeMillis() + NOTIFICATION_LAUNCH_WINDOW_MS
                notificationRouteGeneration += 1
                Log.d(logTag, "SystemUI click observed on desktop display; waiting for notification launch")
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (SystemClock.uptimeMillis() > notificationLaunchArmedUntil) return
                if (eventPackage.isBlank() || eventPackage == SYSTEM_UI_PACKAGE || eventPackage == packageName) return
                if (event.displayId == targetDisplayId) {
                    notificationLaunchArmedUntil = 0L
                    return
                }
                notificationLaunchArmedUntil = 0L
                val generation = notificationRouteGeneration
                routeNotificationTask(eventPackage, generation, 0)
            }
        }
    }

    override fun onInterrupt() = Unit

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!active || suspendedForLockScreen) return
        leaveLaptopModeOnCoverDisplay()
        scheduleHostDisplayReconfiguration(
            "configuration changed",
            densityDpi = newConfig.densityDpi
        )
    }

    override fun onKeyEvent(event: KeyEvent): Boolean = forwardKeyEvent(event)

    override fun onMotionEvent(event: MotionEvent) {
        if (!active || !routePhysicalMouseToDextop || !event.isFromSource(InputDevice.SOURCE_MOUSE)) return
        val relativeX = event.getAxisValue(MotionEvent.AXIS_RELATIVE_X)
        val relativeY = event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y)
        when {
            relativeX != 0f || relativeY != 0f -> {
                activatePhysicalMouse()
                movePhysicalPointer(relativeX, relativeY)
            }
            event.actionMasked == MotionEvent.ACTION_HOVER_MOVE ||
                event.actionMasked == MotionEvent.ACTION_MOVE -> {
                activatePhysicalMouse()
                val view = surfaceView ?: return
                if (view.width > 0 && view.height > 0) {
                    cursorX = (event.x / view.width * targetWidth).coerceIn(0f, targetWidth - 1f)
                    cursorY = (event.y / view.height * targetHeight).coerceIn(0f, targetHeight - 1f)
                }
            }
        }
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (!pausedForAndroid) stop()
        if (screenReceiverRegistered) unregisterReceiver(screenReceiver)
        inputManager?.unregisterInputDeviceListener(inputDeviceListener)
        sensorManager?.unregisterListener(hingeListener)
        getSystemService(DisplayManager::class.java).unregisterDisplayListener(displayListener)
        inputManager = null
        sensorManager = null
        screenReceiverRegistered = false
        instance = null
        return super.onUnbind(intent)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val phoneTaskStillPresent = runCatching {
            val phoneTaskId = MainActivity.phoneTaskId()
            phoneTaskId >= 0 &&
            getSystemService(android.app.ActivityManager::class.java).appTasks.any { task ->
                task.taskInfo.taskId == phoneTaskId
            }
        }.getOrDefault(false)
        if (active && phoneTaskStillPresent) {
            OperationLog.i(this, "Lifecycle", "ignored removal of secondary Dextop task")
            Log.i(logTag, "ignored removal of secondary Dextop task; phone task remains")
            super.onTaskRemoved(rootIntent)
            return
        }
        OperationLog.i(
            this,
            "Lifecycle",
            "launcher task removed active=$active pausedForAndroid=$pausedForAndroid"
        )
        Log.i(logTag, "launcher task removed; closing Dextop session safely")

        // Removing Dextop from Android Recents is an explicit session exit. Do
        // the restoration synchronously while the process and Shizuku binder
        // are still alive; waiting for onUnbind/onDestroy is too late on vendor
        // launchers which kill the task process immediately after this callback.
        if (active || pausedForAndroid || sessionJournal.snapshot()["transactionOpen"] == true) {
            stop()
        }
        super.onTaskRemoved(rootIntent)
    }

    private fun start(config: Config) {
        laptopModeActive = false
        laptopHostUniqueId = null
        filteredHingeAngle = null
        pendingLaptopMode = null
        pendingLaptopModeSince = 0L
        val effectiveConfig = effectiveConfig(config)
        OperationLog.beginSession(
            this,
            "environment=${desktopEnvironment.id} sdk=${Build.VERSION.SDK_INT} " +
                "display=${effectiveConfig.width}x${effectiveConfig.height}/${effectiveConfig.density} " +
                "secure=${effectiveConfig.secure} decorations=${effectiveConfig.decorations}"
        )
        if (!privilegedAccess.isAvailable()) {
            pending = null
            active = false
            val error = IllegalStateException(NativeStrings.text("nativeShizukuUnavailable"))
            completeStart(Result.failure(error))
            Log.e(logTag, "start rejected: Shizuku binder is unavailable", error)
            return
        }
        val cleanupPreferences = getSharedPreferences("dextop_cleanup_state", MODE_PRIVATE)
        pendingPausedWorkspace = cleanupPreferences
            .takeIf { it.getBoolean("paused_by_user", false) }
            ?.getString("paused_workspace", null)
            ?.let { serialized -> runCatching { JSONObject(serialized) }.getOrNull() }
        pausedForAndroid = false
        cleanupPreferences.edit()
            .putBoolean("cleanup_pending", true)
            .putBoolean("paused_by_user", false)
            .putLong("started_at", System.currentTimeMillis())
            .commit()
        suspendedForLockScreen = false
        suspendedConfig = null
        experimentalMultiTouch = true
        sessionJournal.preparing(
            effectiveConfig.width,
            effectiveConfig.height,
            effectiveConfig.density,
            effectiveConfig.decorations
        )
        runCatching { internalRefreshRateController.applyIfEnabled() }
            .onFailure { Log.e(logTag, "120 Hz override failed", it) }
        mirrorRefreshGeneration += 1
        hostReconfigurationGeneration += 1
        mirrorHostWidth = 0
        mirrorHostHeight = 0
        targetDisplayId = -1
        targetWidth = effectiveConfig.width
        targetHeight = effectiveConfig.height
        density = effectiveConfig.density
        secureDisplay = effectiveConfig.secure
        showSystemDecorations = effectiveConfig.decorations
        // Dextop orientation is controlled exclusively by its overlay action.
        // Never thaw this to the phone's accelerometer while a session is live.
        forcePhoneRotation(targetHeight > targetWidth)
        cursorX = targetWidth / 2f
        cursorY = targetHeight / 2f
        removeWindow()
        addWindow()
        pending = null
        active = true
        startHostDisplayMonitor()
        setPhoneNavigationDisabled(true)
        Log.i(logTag, "start direct ${targetWidth}x$targetHeight/$density")
    }

    private fun effectiveConfig(config: Config): Config {
        // Laptop mode is applied only after its two panes have completed layout.
        // Pre-halving a recovered configuration attaches half-height content to
        // a still-full-height Surface and produces a narrow centred strip.
        return config
    }

    private fun addWindow() {
        val frame = TouchRoutingFrame(this).apply {
            // The accessibility window is translucent so it can host the
            // mirrored SurfaceView, but uncovered pixels must never reveal
            // the Android screen during a laptop-pane resize.
            setBackgroundColor(Color.BLACK)
            // Keep every pointer in one touch stream. Splitting the stream lets
            // the mirrored surface consume fingers before the edge recognizer.
            isMotionEventSplittingEnabled = false
            // The desktop surface owns input whenever the operation overlay is
            // closed. Opening the overlay explicitly disables this route.
            routeTouchesToSurface = true
        }
        val surface = SurfaceView(this).apply {
            holder.addCallback(this@MirrorService)
            isFocusable = true
            isFocusableInTouchMode = true
            setOnTouchListener { _, event ->
                if (event.isFromSource(InputDevice.SOURCE_MOUSE)) {
                    if (event.actionMasked == MotionEvent.ACTION_MOVE ||
                        event.actionMasked == MotionEvent.ACTION_HOVER_MOVE) activatePhysicalMouse()
                    forwardMouseEvent(event, this)
                } else {
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) activateTouchInput()
                    trackpad(event)
                }
            }
            setOnGenericMotionListener { _, event ->
                handlePhysicalMouseEvent(event, this)
            }
            setOnHoverListener { _, event -> handlePhysicalMouseEvent(event, this) }
            setOnCapturedPointerListener { _, event -> handleCapturedMouseEvent(event) }
            requestFocus()
        }
        val cursor = CursorView(this)
        // A connected mouse alone must not hide the touchpad cursor. Switch the
        // visual cursor only after input from that mouse is actually observed.
        cursor.visibility = if (directTouch) View.GONE else View.VISIBLE
        val controls = buildMenu()
        val scrim = View(this).apply {
            setBackgroundColor(Color.argb(105, 0, 0, 0))
            visibility = View.GONE
            setOnClickListener { toggleMenu() }
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            addView(surface, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        frame.addView(content, FrameLayout.LayoutParams(-1, -1, Gravity.TOP))
        frame.addView(scrim, FrameLayout.LayoutParams(-1, -1))
        frame.addView(controls, menuLayoutParams())
        val hud = PerformanceHud(this) { inputMode() }.apply {
            visibility = if (getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
                    .getBoolean("flutter.performance_hud", false)) View.VISIBLE else View.GONE
        }
        frame.addView(
            hud,
            FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply {
                topMargin = dp(18)
                rightMargin = dp(18)
            }
        )
        surface.post { surface.requestFocus() }
        controls.bringToFront()
        val params = WindowManager.LayoutParams(
            -1,
            -1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            fitInsetsTypes = 0
            setFitInsetsIgnoringVisibility(true)
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            if (getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
                    .getBoolean("flutter.keep_awake_during_session", false)) {
                flags = flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            }
        }
        root = frame
        rootWindowParams = params
        surfaceView = surface
        laptopContent = content
        cursorView = cursor
        cursor.contentHeightFraction = 1f
        menu = controls
        menuScrim = scrim
        performanceHud = hud
        windowManager?.addView(frame, params)
        frame.post {
            val autoStart = isLaptopAutoDetectionEnabled() &&
                hingeAngle?.let(::isLaptopHingeAngle) == true
            val startInLaptopMode = isDebugLaptopModeForced() || laptopManualOverride || autoStart
            laptopAutoActivated = autoStart && !laptopManualOverride
            if (startInLaptopMode) setLaptopMode(true)
        }
        if (experimentalMultiTouch) {
            frame.post {
                val exclusion = if (targetWidth >= targetHeight) {
                    val width = dp(120).coerceAtMost(frame.width / 3)
                    listOf(Rect(0, 0, width, frame.height))
                } else {
                    val height = dp(120).coerceAtMost(frame.height / 3)
                    listOf(Rect(0, 0, frame.width, height))
                }
                frame.systemGestureExclusionRects = exclusion
                surface.systemGestureExclusionRects = exclusion
            }
        }
        val cursorParams = WindowManager.LayoutParams(
            -1,
            -1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            fitInsetsTypes = 0
            setFitInsetsIgnoringVisibility(true)
        }
        windowManager?.addView(cursor, cursorParams)
        cursor.update(cursorX / targetWidth, cursorY / targetHeight)
        Log.i(logTag, "fullscreen accessibility overlay added")
    }

    private data class LaptopKey(val label: String, val code: Int, val weight: Float = 1f)

    private fun buildLaptopDeck(): View {
        laptopModifierButtons.clear()
        laptopShortcutButtons.clear()
        laptopLegendButtons.clear()
        laptopShift = false
        laptopControl = false
        laptopAlt = false
        laptopCapsLock = false
        val palette = laptopPalette()
        val deck = FrameLayout(this).apply {
            setBackgroundColor(palette.background)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        palette.imageBase64?.let { encoded ->
            runCatching {
                val bytes = Base64.decode(encoded, Base64.DEFAULT)
                ImageView(this).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    alpha = palette.opacity
                    setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && palette.blur > 0f) {
                        setRenderEffect(RenderEffect.createBlurEffect(
                            palette.blur, palette.blur, Shader.TileMode.CLAMP
                        ))
                    }
                }
            }.getOrNull()?.let { image ->
                deck.addView(image, FrameLayout.LayoutParams(-1, -1))
            }
        }
        deck.addView(content, FrameLayout.LayoutParams(-1, -1))
        val trackpad = TextView(this).apply {
            text = "TRACKPAD"
            typeface = laptopTypeface
            textSize = 11f
            letterSpacing = .12f
            gravity = Gravity.CENTER
            setTextColor(palette.trackpadText)
            background = GradientDrawable().apply {
                setColor(palette.trackpad)
                setStroke(dp(1), palette.border)
                cornerRadius = dp(palette.radius.toInt()).toFloat()
            }
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) activateLaptopTrackpad()
                trackpad(event, forceCursorMode = true)
            }
        }
        val keyboard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        laptopKeyboardView = keyboard
        laptopKeyboardRows(laptopFunctionRowVisible).forEachIndexed { index, keys ->
            val row = buildLaptopKeyboardRow(keys)
            if (laptopFunctionRowVisible && index == 0) row.tag = "laptop_function_row"
            keyboard.addView(row, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        content.addView(keyboard, LinearLayout.LayoutParams(-1, 0, .66f).apply {
            bottomMargin = dp(7)
        })
        val trackpadArea = FrameLayout(this).apply {
            addView(trackpad, FrameLayout.LayoutParams(-1, -1))
            addView(TextView(this@MirrorService).apply {
                text = "FN"
                typeface = laptopTypeface
                textSize = 10f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(235, 231, 239))
                background = laptopKeyBackground(laptopFunctionRowVisible, LAPTOP_ALT)
                setOnClickListener {
                    setLaptopFunctionRowVisible(!laptopFunctionRowVisible)
                }
                laptopFnButton = this
            }, FrameLayout.LayoutParams(dp(58), dp(42), Gravity.BOTTOM or Gravity.START).apply {
                leftMargin = dp(10)
                bottomMargin = dp(10)
            })
            addView(TextView(this@MirrorService).apply {
                text = "MENU"
                typeface = laptopTypeface
                textSize = 10f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(235, 231, 239))
                background = laptopKeyBackground(false, LAPTOP_ALT)
                setOnClickListener { if (!demoMode) toggleMenu() }
            }, FrameLayout.LayoutParams(dp(58), dp(42), Gravity.BOTTOM or Gravity.END).apply {
                rightMargin = dp(10)
                bottomMargin = dp(10)
            })
        }
        content.addView(trackpadArea, LinearLayout.LayoutParams(-1, 0, .34f))
        laptopDeck = deck
        laptopDeckContent = content
        return deck
    }

    private fun setLaptopFunctionRowVisible(visible: Boolean) {
        if (visible == laptopFunctionRowVisible) return
        val keyboard = laptopKeyboardView ?: return
        laptopFunctionRowVisible = visible
        laptopFnButton?.background = laptopKeyBackground(visible, LAPTOP_ALT)
        if (visible) {
            TransitionManager.beginDelayedTransition(
                keyboard,
                ChangeBounds().apply { duration = 200 }
            )
            val row = buildLaptopKeyboardRow(laptopKeyboardRows(true).first()).apply {
                tag = "laptop_function_row"
                alpha = 0f
                translationY = -dp(24).toFloat()
            }
            keyboard.addView(row, 0, LinearLayout.LayoutParams(-1, 0, 1f))
            row.animate().alpha(1f).translationY(0f).setDuration(200).start()
        } else {
            val row = keyboard.findViewWithTag<View>("laptop_function_row") ?: return
            row.animate().alpha(0f).translationY(-dp(24).toFloat()).setDuration(170)
                .withEndAction {
                    TransitionManager.beginDelayedTransition(
                        keyboard,
                        ChangeBounds().apply { duration = 190 }
                    )
                    keyboard.removeView(row)
                }.start()
        }
    }

    private fun buildLaptopKeyboardRow(keys: List<LaptopKey>): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            keys.forEach { key ->
                addView(
                    laptopKeyButton(key),
                    LinearLayout.LayoutParams(0, -1, key.weight).apply {
                        setMargins(dp(2), dp(2), dp(2), dp(2))
                    }
                )
            }
        }

    private fun setLaptopMode(enabled: Boolean) {
        if (enabled == laptopModeActive) {
            if (enabled) startLaptopHardwareKeyboard()
            return
        }
        if (enabled && !isDebugLaptopModeForced() && !laptopManualOverride &&
            hingeAngle?.let(::isLaptopHingeAngle) != true && !isFoldableMainDisplay()) return
        val frame = root ?: return
        val content = laptopContent ?: return
        val surface = surfaceView ?: return
        laptopModeActive = enabled
        if (!enabled) laptopAutoActivated = false
        laptopHostUniqueId = if (enabled) defaultDisplayUniqueId() else null
        if (enabled) {
            startLaptopHardwareKeyboard()
            val deck = buildLaptopDeck().apply {
                alpha = 0f
                translationY = dp(28).toFloat()
                scaleY = .94f
            }
            content.addView(deck, LinearLayout.LayoutParams(-1, 0, 1f))
            deck.post {
                deck.pivotY = deck.height.toFloat()
                deck.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .scaleY(1f)
                    .setInterpolator(PathInterpolator(.22f, 1f, .36f, 1f))
                    .setDuration(360L)
                    .start()
            }
            cursorView?.contentHeightFraction = .5f
            menuScrim?.bringToFront()
            menu?.bringToFront()
            performanceHud?.bringToFront()
        } else {
            stopLaptopHardwareKeyboard()
            val deck = laptopDeck
            laptopDeck = null
            deck?.animate()
                ?.cancel()
            deck?.animate()
                ?.alpha(0f)
                ?.translationY(dp(28).toFloat())
                ?.scaleY(.94f)
                ?.setInterpolator(PathInterpolator(.55f, 0f, .78f, 0f))
                ?.setDuration(300L)
                ?.withEndAction {
                    if (!laptopModeActive) runCatching { content.removeView(deck) }
                }
                ?.start()
            cursorView?.contentHeightFraction = 1f
        }
        surface.requestLayout()
        frame.requestLayout()
        applyLaptopGeometryWhenLaidOut(enabled)
    }

    /**
     * Registers a real external keyboard with Android's input stack while the
     * laptop deck is visible. Key events remain display-targeted through
     * InputDispatcher, but IMEs now use their normal physical-keyboard policy:
     * Gboard is hidden by default and remains available when the user enables
     * "Show virtual keyboard" for connected physical keyboards.
     */
    private fun startLaptopHardwareKeyboard() {
        if (laptopHardwareKeyboardProcess?.alive() == true) return
        stopLaptopHardwareKeyboard()
        val binder = rikka.shizuku.Shizuku.getBinder() ?: return
        runCatching {
            val remote = IShizukuService.Stub.asInterface(binder)
                .newProcess(arrayOf("uinput", "-"), null, null)
            val output = android.os.ParcelFileDescriptor.AutoCloseOutputStream(remote.outputStream)
            laptopHardwareKeyboardProcess = remote
            laptopHardwareKeyboardInput = output
            val supportedKeys = (1..127).joinToString(",")
            val registration = """
                {
                  "id": 413,
                  "command": "register",
                  "name": "Dextop Laptop Keyboard",
                  "vid": 6353,
                  "pid": 5417,
                  "bus": "usb",
                  "configuration": [
                    {"type":"UI_SET_EVBIT","data":["EV_KEY","EV_SYN"]},
                    {"type":"UI_SET_KEYBIT","data":[$supportedKeys]}
                  ]
                }
            """.trimIndent() + "\n"
            output.write(registration.toByteArray(Charsets.UTF_8))
            output.flush()
            drainLaptopKeyboardPipe(remote.inputStream, "stdout")
            drainLaptopKeyboardPipe(remote.errorStream, "stderr")
            OperationLog.i(
                this,
                "LaptopMode",
                "registered external keyboard device; IME follows physical-keyboard preference"
            )
        }.onFailure { error ->
            stopLaptopHardwareKeyboard()
            OperationLog.w(this, "LaptopMode", "external keyboard registration failed", error)
            Log.e(logTag, "laptop hardware keyboard registration failed", error)
        }
    }

    private fun drainLaptopKeyboardPipe(
        descriptor: android.os.ParcelFileDescriptor,
        streamName: String
    ) {
        Thread {
            runCatching {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor)
                    .bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            if (line.isNotBlank()) Log.d(logTag, "laptop keyboard $streamName: $line")
                        }
                    }
            }
        }.apply {
            name = "DextopLaptopKeyboard-$streamName"
            isDaemon = true
            start()
        }
    }

    private fun stopLaptopHardwareKeyboard() {
        laptopHardwareKeyboardInput?.let { runCatching { it.close() } }
        laptopHardwareKeyboardInput = null
        laptopHardwareKeyboardProcess?.let { runCatching { it.destroy() } }
        laptopHardwareKeyboardProcess = null
    }

    private fun leaveLaptopModeOnCoverDisplay(): Boolean {
        if (!laptopModeActive || isDebugLaptopModeForced()) return false
        val originalHost = laptopHostUniqueId ?: return false
        if (originalHost == defaultDisplayUniqueId()) return false
        laptopManualOverride = false
        laptopAutoActivated = false
        OperationLog.i(
            this,
            "LaptopMode",
            "cover display detected; removing keyboard and restoring full display geometry"
        )
        setLaptopMode(false)
        return true
    }

    private fun isLaptopHingeAngle(angle: Float): Boolean = if (laptopModeActive) {
        angle in 45f..155f
    } else {
        angle in 55f..145f
    }

    private fun updateLaptopModeForHinge(angle: Float) {
        if (!active || root == null || suspendedForLockScreen) return
        if (!isLaptopAutoDetectionEnabled()) return
        // Hinge sensors on real devices are noisy around the flex posture
        // thresholds.  Filter small jumps and require the candidate state to
        // remain stable briefly before rebuilding the laptop deck; otherwise
        // the deck repeatedly fades in/out and the virtual display is resized
        // on every sensor fluctuation.
        val filtered = filteredHingeAngle?.let { previous ->
            previous + (angle - previous) * 0.25f
        } ?: angle
        filteredHingeAngle = filtered
        hingeAngle = filtered
        // A half-open hinge is itself authoritative: inactive inner panels are
        // omitted from DisplayManager on several foldables and the emulator.
        val mainDisplay = isLaptopHingeAngle(filtered) || isFoldableMainDisplay()
        if (!mainDisplay) {
            laptopManualOverride = false
            laptopAutoActivated = false
        }
        val shouldShow = mainDisplay && (laptopManualOverride || isLaptopHingeAngle(filtered))
        if (shouldShow == laptopModeActive) {
            pendingLaptopMode = null
            pendingLaptopModeSince = 0L
            return
        }
        val now = SystemClock.uptimeMillis()
        if (pendingLaptopMode != shouldShow) {
            pendingLaptopMode = shouldShow
            pendingLaptopModeSince = now
            return
        }
        if (now - pendingLaptopModeSince >= laptopModeDebounceMs) {
            pendingLaptopMode = null
            pendingLaptopModeSince = 0L
            laptopAutoActivated = shouldShow && !laptopManualOverride
            OperationLog.i(
                this,
                "LaptopMode",
                "hinge angle=$filtered raw=$angle manual=$laptopManualOverride main=$mainDisplay show=$shouldShow"
            )
            setLaptopMode(shouldShow)
        }
    }

    private fun applyFlutterLaptopModeSetting(enabled: Boolean) {
        if (!enabled) {
            if (laptopAutoActivated) setLaptopMode(false)
            return
        }
        hingeAngle?.let(::updateLaptopModeForHinge)
    }

    private fun applyLaptopGeometryWhenLaidOut(enabled: Boolean, attempt: Int = 0) {
        val content = laptopContent ?: return
        val surface = surfaceView ?: return
        content.postDelayed({
            if (laptopModeActive != enabled) return@postDelayed
            if (!active) {
                if (attempt < 20) applyLaptopGeometryWhenLaidOut(enabled, attempt + 1)
                return@postDelayed
            }
            val expectedHeight = if (enabled) content.height / 2 else content.height
            if ((surface.width <= 0 || kotlin.math.abs(surface.height - expectedHeight) > 2) &&
                attempt < 20) {
                applyLaptopGeometryWhenLaidOut(enabled, attempt + 1)
                return@postDelayed
            }
            val reason = if (enabled) "laptop mode enabled" else "laptop mode disabled"
            // While the laptop deck is visible the desktop must always match
            // the measured upper pane. A custom/recovered profile would
            // otherwise be letterboxed inside that pane.
            val next = configForHostGeometry(
                Config(targetWidth, targetHeight, density, secureDisplay, showSystemDecorations),
                surface.width,
                surface.height,
                resources.configuration.densityDpi
            )
            resizeActiveDisplay(next, "$reason after measured layout")
            scheduleMirrorRefresh(
                "$reason; resize VirtualDisplay output to pane",
                surface.width,
                surface.height,
                forceVirtualDisplay = true
            )
            if (enabled) refreshLaptopVirtualDisplayAfterLayout()
            OperationLog.i(
                this,
                "LaptopMode",
                "$reason host=${surface.width}x${surface.height} forcedToPane=true"
            )
            menuPrimary?.let(::showMainMenu)
        }, if (attempt == 0) 0 else 32)
    }

    private fun refreshLaptopVirtualDisplayAfterLayout(attempt: Int = 0) {
        val expectedMode = laptopModeActive
        root?.postDelayed({
            if (!active || !expectedMode || !laptopModeActive || targetDisplayId < 0) {
                if (attempt < 4 && laptopModeActive) {
                    refreshLaptopVirtualDisplayAfterLayout(attempt + 1)
                }
                return@postDelayed
            }
            val surface = surfaceView ?: return@postDelayed
            if (surface.width <= 0 || surface.height <= 0 || !surface.holder.surface.isValid) {
                if (attempt < 4) refreshLaptopVirtualDisplayAfterLayout(attempt + 1)
                return@postDelayed
            }
            val paneConfig = configForHostGeometry(
                Config(targetWidth, targetHeight, density, secureDisplay, showSystemDecorations),
                surface.width,
                surface.height,
                resources.configuration.densityDpi
            )
            if (paneConfig.width != targetWidth || paneConfig.height != targetHeight ||
                paneConfig.density != density) {
                resizeActiveDisplay(paneConfig, "laptop pane final synchronization")
            }
            runCatching { attachMirror(surface.width, surface.height, "virtual_display") }
                .onSuccess {
                    mirrorDisplayId = targetDisplayId
                    Log.i(
                        logTag,
                        "laptop VirtualDisplay refreshed attempt=$attempt " +
                            "host=${surface.width}x${surface.height} content=${targetWidth}x$targetHeight"
                    )
                    OperationLog.i(
                        this,
                        "LaptopMode",
                        "VirtualDisplay recreated for pane=${surface.width}x${surface.height} " +
                            "content=${targetWidth}x$targetHeight"
                    )
                }
                .onFailure {
                    Log.e(logTag, "laptop VirtualDisplay refresh failed attempt=$attempt", it)
                    OperationLog.e(this, "LaptopMode", "VirtualDisplay pane refresh failed", it)
                    if (attempt < 4) refreshLaptopVirtualDisplayAfterLayout(attempt + 1)
                }
        }, 700L + attempt * 350L)
    }

    private fun defaultDisplayUniqueId(): String? = runCatching {
        Display::class.java.getMethod("getUniqueId")
            .invoke(getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)) as String
    }.getOrNull()

    private fun isDebugLaptopModeForced(): Boolean =
        DEBUG_FORCE_LAPTOP_MODE &&
            applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0

    private fun isFoldableMainDisplay(): Boolean {
        val manager = getSystemService(DisplayManager::class.java)
        val internalDisplays = internalDisplays(manager)
        if (internalDisplays.size < 2) return false
        fun area(display: Display): Long {
            val metrics = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            display.getRealMetrics(metrics)
            return metrics.widthPixels.toLong() * metrics.heightPixels
        }
        val current = manager.getDisplay(Display.DEFAULT_DISPLAY) ?: return false
        return area(current) >= internalDisplays.maxOf(::area)
    }

    private fun isFoldableDevice(): Boolean =
        internalDisplays(getSystemService(DisplayManager::class.java)).size >= 2 ||
            getSystemService(SensorManager::class.java)
                .getDefaultSensor(Sensor.TYPE_HINGE_ANGLE) != null

    private fun internalDisplays(manager: DisplayManager): List<Display> =
        manager.displays.filter { display ->
            runCatching { Display::class.java.getMethod("getType").invoke(display) as Int == 1 }
                .getOrDefault(display.displayId == Display.DEFAULT_DISPLAY)
        }

    private fun laptopKeyboardRows(showFunctionRow: Boolean): List<List<LaptopKey>> {
        val rows = mutableListOf<List<LaptopKey>>()
        if (showFunctionRow) rows +=
        listOf(
            LaptopKey("ESC", KeyEvent.KEYCODE_ESCAPE, 1.15f),
            LaptopKey("F1", KeyEvent.KEYCODE_F1), LaptopKey("F2", KeyEvent.KEYCODE_F2),
            LaptopKey("F3", KeyEvent.KEYCODE_F3), LaptopKey("F4", KeyEvent.KEYCODE_F4),
            LaptopKey("F5", KeyEvent.KEYCODE_F5), LaptopKey("F6", KeyEvent.KEYCODE_F6),
            LaptopKey("F7", KeyEvent.KEYCODE_F7), LaptopKey("F8", KeyEvent.KEYCODE_F8),
            LaptopKey("F9", KeyEvent.KEYCODE_F9), LaptopKey("F10", KeyEvent.KEYCODE_F10),
            LaptopKey("F11", KeyEvent.KEYCODE_F11), LaptopKey("F12", KeyEvent.KEYCODE_F12),
            LaptopKey("DEL", KeyEvent.KEYCODE_FORWARD_DEL, 1.15f)
        )
        rows += listOf(
        listOf(
            LaptopKey("`", KeyEvent.KEYCODE_GRAVE),
            LaptopKey("1", KeyEvent.KEYCODE_1), LaptopKey("2", KeyEvent.KEYCODE_2),
            LaptopKey("3", KeyEvent.KEYCODE_3), LaptopKey("4", KeyEvent.KEYCODE_4),
            LaptopKey("5", KeyEvent.KEYCODE_5), LaptopKey("6", KeyEvent.KEYCODE_6),
            LaptopKey("7", KeyEvent.KEYCODE_7), LaptopKey("8", KeyEvent.KEYCODE_8),
            LaptopKey("9", KeyEvent.KEYCODE_9), LaptopKey("0", KeyEvent.KEYCODE_0),
            LaptopKey("-", KeyEvent.KEYCODE_MINUS), LaptopKey("=", KeyEvent.KEYCODE_EQUALS),
            LaptopKey("⌫", KeyEvent.KEYCODE_DEL, 1.55f)
        ),
        listOf(
            LaptopKey("TAB", KeyEvent.KEYCODE_TAB, 1.35f),
            LaptopKey("Q", KeyEvent.KEYCODE_Q), LaptopKey("W", KeyEvent.KEYCODE_W),
            LaptopKey("E", KeyEvent.KEYCODE_E), LaptopKey("R", KeyEvent.KEYCODE_R),
            LaptopKey("T", KeyEvent.KEYCODE_T), LaptopKey("Y", KeyEvent.KEYCODE_Y),
            LaptopKey("U", KeyEvent.KEYCODE_U), LaptopKey("I", KeyEvent.KEYCODE_I),
            LaptopKey("O", KeyEvent.KEYCODE_O), LaptopKey("P", KeyEvent.KEYCODE_P),
            LaptopKey("[", KeyEvent.KEYCODE_LEFT_BRACKET),
            LaptopKey("]", KeyEvent.KEYCODE_RIGHT_BRACKET),
            LaptopKey("\\", KeyEvent.KEYCODE_BACKSLASH, 1.35f)
        ),
        listOf(
            LaptopKey("CAPS", LAPTOP_CAPS, 1.65f),
            LaptopKey("A", KeyEvent.KEYCODE_A), LaptopKey("S", KeyEvent.KEYCODE_S),
            LaptopKey("D", KeyEvent.KEYCODE_D), LaptopKey("F", KeyEvent.KEYCODE_F),
            LaptopKey("G", KeyEvent.KEYCODE_G), LaptopKey("H", KeyEvent.KEYCODE_H),
            LaptopKey("J", KeyEvent.KEYCODE_J), LaptopKey("K", KeyEvent.KEYCODE_K),
            LaptopKey("L", KeyEvent.KEYCODE_L), LaptopKey(";", KeyEvent.KEYCODE_SEMICOLON),
            LaptopKey("'", KeyEvent.KEYCODE_APOSTROPHE),
            LaptopKey("ENTER", KeyEvent.KEYCODE_ENTER, 1.75f)
        ),
        listOf(
            LaptopKey("SHIFT", LAPTOP_SHIFT, 2.15f),
            LaptopKey("Z", KeyEvent.KEYCODE_Z), LaptopKey("X", KeyEvent.KEYCODE_X),
            LaptopKey("C", KeyEvent.KEYCODE_C), LaptopKey("V", KeyEvent.KEYCODE_V),
            LaptopKey("B", KeyEvent.KEYCODE_B), LaptopKey("N", KeyEvent.KEYCODE_N),
            LaptopKey("M", KeyEvent.KEYCODE_M), LaptopKey(",", KeyEvent.KEYCODE_COMMA),
            LaptopKey(".", KeyEvent.KEYCODE_PERIOD), LaptopKey("/", KeyEvent.KEYCODE_SLASH),
            LaptopKey("SHIFT", LAPTOP_SHIFT, 2.15f)
        ),
        listOf(
            LaptopKey("CTRL", LAPTOP_CONTROL, 1.25f),
            LaptopKey("", KeyEvent.KEYCODE_META_LEFT),
            LaptopKey("ALT", LAPTOP_ALT, 1.15f),
            LaptopKey("SPACE", KeyEvent.KEYCODE_SPACE, 5f),
            LaptopKey("ALT", LAPTOP_ALT, 1.15f),
            LaptopKey("←", KeyEvent.KEYCODE_DPAD_LEFT),
            LaptopKey("↑", KeyEvent.KEYCODE_DPAD_UP),
            LaptopKey("↓", KeyEvent.KEYCODE_DPAD_DOWN),
            LaptopKey("→", KeyEvent.KEYCODE_DPAD_RIGHT)
        ))
        return rows
    }

    private val laptopShortcutLabels = mapOf(
        KeyEvent.KEYCODE_C to "Copy",
        KeyEvent.KEYCODE_V to "Paste",
        KeyEvent.KEYCODE_X to "Cut",
        KeyEvent.KEYCODE_A to "Sel all",
        KeyEvent.KEYCODE_Z to "Undo",
        KeyEvent.KEYCODE_S to "Save",
        KeyEvent.KEYCODE_N to "New",
        KeyEvent.KEYCODE_P to "Print",
        KeyEvent.KEYCODE_F to "Find",
        KeyEvent.KEYCODE_W to "Close"
    )

    private fun laptopKeyButton(key: LaptopKey): TextView = LaptopKeyTextView(
        this,
        key.code == KeyEvent.KEYCODE_F || key.code == KeyEvent.KEYCODE_J,
        laptopPalette().text
    ).apply {
        text = key.label
        if (key.code != KeyEvent.KEYCODE_META_LEFT) {
            laptopLegendButtons += this to key.label
        }
        typeface = laptopTypeface
        textSize = if (key.label.length > 2) 9f else 12f
        gravity = Gravity.CENTER
        setTextColor(laptopPalette().text)
        background = laptopKeyBackground(false, key.code)
        if (key.code == KeyEvent.KEYCODE_META_LEFT) {
            text = "\uE859"
            typeface = materialIconTypeface
            textSize = 22f
        }
        if (key.code == KeyEvent.KEYCODE_META_LEFT) {
            setOnLongClickListener {
                if (!demoMode) showLaptopKeyboardSettings()
                true
            }
        }
        if (key.code < 0) laptopModifierButtons.putIfAbsent(key.code, this)
        if (key.code in laptopShortcutLabels.keys) laptopShortcutButtons[key.code] = this
        setOnClickListener { handleLaptopKey(key.code) }
        setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    // Modifiers are latched on press, not release, so a
                    // second finger can press C/V/etc. while Ctrl is still
                    // held. This also makes real multi-touch chords work.
                    if (key.code < 0) handleLaptopKey(key.code)
                    view.animate()
                    .scaleX(.92f).scaleY(.92f).alpha(.72f)
                    .setDuration(55).start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.animate()
                    .scaleX(1f).scaleY(1f).alpha(1f)
                    .setDuration(110).start()
            }
            key.code < 0
        }
    }

    private fun laptopKeyBackground(selected: Boolean, keyCode: Int? = null) = GradientDrawable().apply {
        val palette = laptopPalette()
        val functionKey = keyCode != null &&
            (keyCode == KeyEvent.KEYCODE_ESCAPE || keyCode in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12 ||
                keyCode == KeyEvent.KEYCODE_FORWARD_DEL)
        val modifierKey = keyCode != null &&
            (keyCode < 0 || keyCode == KeyEvent.KEYCODE_META_LEFT ||
                keyCode == KeyEvent.KEYCODE_ALT_LEFT || keyCode == KeyEvent.KEYCODE_ALT_RIGHT)
        val specialKey = keyCode in setOf(
            KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT
        )
        setColor(
            if (selected) {
                palette.selected
            } else {
                when {
                    functionKey || modifierKey || specialKey -> palette.keyVariant
                    else -> palette.key
                }
            }
        )
        setStroke(
            dp(1),
            if (selected) {
                palette.text
            } else {
                palette.border
            }
        )
        cornerRadius = dp(palette.radius.toInt()).toFloat()
    }

    private fun laptopKeyboardTheme(): String =
        getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
            .getString("flutter.laptop_keyboard_theme", null)
            ?: getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString("laptop_keyboard_theme", "standard") ?: "standard"

    private data class LaptopPalette(
        val background: Int,
        val key: Int,
        val keyVariant: Int,
        val border: Int,
        val text: Int,
        val trackpad: Int,
        val trackpadText: Int,
        val selected: Int,
        val radius: Float,
        val opacity: Float = 1f,
        val blur: Float = 0f,
        val imageBase64: String? = null,
    )

    private fun paletteColor(value: String?, fallback: Int): Int = runCatching {
        Color.parseColor(value ?: "")
    }.getOrDefault(fallback)

    private fun laptopPalette(): LaptopPalette = laptopPaletteFor(laptopKeyboardTheme())

    /** Resolve a palette for previews as well as the active keyboard. */
    private fun laptopPaletteFor(id: String): LaptopPalette {
        val crimson = id == "crimson"
        val cloud = id == "cloud"
        val fallback = if (crimson) LaptopPalette(
            Color.rgb(89, 14, 14), Color.rgb(110, 27, 27), Color.rgb(81, 10, 11),
            Color.rgb(126, 32, 27), Color.rgb(255, 190, 151), Color.rgb(90, 15, 16),
            Color.rgb(222, 137, 102), Color.rgb(81, 10, 11), 7f
        ) else if (cloud) LaptopPalette(
            Color.rgb(220, 235, 255), Color.WHITE, Color.rgb(247, 251, 255),
            Color.rgb(183, 212, 245), Color.rgb(66, 100, 134), Color.rgb(199, 221, 245),
            Color.rgb(82, 120, 159), Color.rgb(190, 218, 248), 16f
        ) else LaptopPalette(
            Color.rgb(18, 18, 22), Color.rgb(48, 46, 54), Color.rgb(48, 46, 54),
            Color.rgb(76, 72, 84), Color.rgb(235, 231, 239), Color.rgb(35, 34, 40),
            Color.rgb(145, 141, 151), Color.rgb(208, 188, 237), 7f
        )
        // Built-in palettes are authoritative. Older Flutter preferences may
        // contain a normalized copy where keyVariant was incorrectly replaced
        // with key, which destroys the decorative-key contrast.
        if (id == "standard" || id == "crimson" || id == "cloud") return fallback
        val raw = getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
            .getString("flutter.dextop_keyboard_theme_$id", null) ?: return fallback
        return runCatching {
            val json = JSONObject(raw)
            fallback.copy(
                background = paletteColor(json.optString("background"), fallback.background),
                key = paletteColor(json.optString("key"), fallback.key),
                keyVariant = paletteColor(json.optString("keyVariant"), fallback.keyVariant),
                border = paletteColor(json.optString("border"), fallback.border),
                text = paletteColor(json.optString("text"), fallback.text),
                trackpad = paletteColor(json.optString("trackpad"), fallback.trackpad),
                trackpadText = paletteColor(json.optString("trackpadText"), fallback.trackpadText),
                selected = paletteColor(json.optString("selected"), fallback.selected),
                radius = json.optDouble("radius", fallback.radius.toDouble()).toFloat()
                    .coerceIn(0f, 40f),
                opacity = json.optDouble("opacity", 1.0).toFloat().coerceIn(.1f, 1f),
                blur = json.optDouble("blur", 0.0).toFloat().coerceIn(0f, 30f),
                imageBase64 = json.optString("imageBase64").takeIf { it.isNotBlank() }
            )
        }.getOrDefault(fallback)
    }

    private fun showLaptopKeyboardSettings() {
        val deck = laptopDeckContent ?: return
        laptopSettingsVisible = true
        TransitionManager.beginDelayedTransition(deck, AutoTransition().apply { duration = 240 })
        deck.removeAllViews()
        val crimson = laptopKeyboardTheme() == "crimson"
        deck.background = if (crimson) GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.rgb(25, 2, 4), Color.rgb(57, 7, 8), Color.rgb(34, 3, 5))
        ) else GradientDrawable().apply { setColor(Color.rgb(18, 18, 22)) }
        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(10), dp(20), dp(8))
        }
        header.addView(ImageButton(this).apply {
            contentDescription = "Back"
            setImageDrawable(KeyboardGlyphDrawable(KeyboardGlyphDrawable.BACK, Color.WHITE))
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setOnClickListener { rebuildLaptopDeck() }
        }, LinearLayout.LayoutParams(dp(52), dp(52)).apply { rightMargin = dp(8) })
        header.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@MirrorService).apply {
                text = "Keyboard settings"
                typeface = laptopTypeface
                textSize = 19f
                setTextColor(Color.WHITE)
            })
            addView(TextView(this@MirrorService).apply {
                text = "Customize the appearance of the laptop keyboard"
                typeface = laptopTypeface
                textSize = 11f
                setTextColor(if (crimson) Color.rgb(207, 132, 101) else Color.rgb(170, 165, 177))
            })
        }, LinearLayout.LayoutParams(0, -2, 1f))
        deck.addView(header, LinearLayout.LayoutParams(-1, -2))
        deck.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(28), dp(4), dp(28), dp(4))
            addView(ImageView(this@MirrorService).apply {
                setImageDrawable(KeyboardGlyphDrawable(
                    KeyboardGlyphDrawable.PALETTE,
                    if (crimson) Color.rgb(236, 145, 101) else Color.rgb(208, 188, 237)
                ))
            }, LinearLayout.LayoutParams(dp(28), dp(28)).apply { rightMargin = dp(12) })
            addView(TextView(this@MirrorService).apply {
                text = "Theme"
                typeface = laptopTypeface
                textSize = 15f
                setTextColor(Color.WHITE)
            })
        }, LinearLayout.LayoutParams(-1, dp(42)))
        val choices = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(8), dp(28), dp(24))
        }
        laptopThemeChoices().forEachIndexed { index, (id, label) ->
            choices.addView(laptopThemeChoice(id, label), LinearLayout.LayoutParams(dp(220), -1).apply {
                if (index > 0) leftMargin = dp(6)
                if (index < laptopThemeChoices().lastIndex) rightMargin = dp(6)
            })
        }
        deck.addView(HorizontalScrollView(this).apply {
            isFillViewport = false
            isHorizontalScrollBarEnabled = true
            addView(choices, LinearLayout.LayoutParams(-2, -1))
        }, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    private fun laptopThemeChoices(): List<Pair<String, String>> {
        val choices = mutableListOf("standard" to "Standard", "crimson" to "Crimson", "cloud" to "Cloud Pop")
        val raw = getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
            .getString("flutter.dextop_laptop_keyboard_themes", null)
        runCatching {
            val list = JSONArray(raw ?: "[]")
            for (index in 0 until list.length()) {
                val item = list.optJSONObject(index) ?: continue
                val id = item.optString("id")
                val name = item.optString("name")
                if (id.isNotBlank() && name.isNotBlank()) choices += id to name
            }
        }
        return choices
    }

    private fun laptopThemeChoice(id: String, label: String): View {
        val selected = laptopKeyboardTheme() == id
        val palette = laptopPaletteFor(id)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(14))
            background = GradientDrawable().apply {
                setColor(palette.background)
                setStroke(dp(if (selected) 2 else 1), if (selected)
                    palette.selected else palette.border)
                cornerRadius = dp(18).toFloat()
            }
            addView(LinearLayout(this@MirrorService).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(this@MirrorService).apply {
                    text = label
                    typeface = laptopTypeface
                    textSize = 16f
                    setTextColor(Color.WHITE)
                }, LinearLayout.LayoutParams(0, -2, 1f))
                if (selected) addView(ImageView(this@MirrorService).apply {
                    setImageDrawable(KeyboardGlyphDrawable(
                        KeyboardGlyphDrawable.CHECK,
                        palette.selected
                    ))
                }, LinearLayout.LayoutParams(dp(24), dp(24)))
            }, LinearLayout.LayoutParams(-1, -2))
            addView(LinearLayout(this@MirrorService).apply {
                gravity = Gravity.CENTER
                repeat(7) { index ->
                    addView(View(this@MirrorService).apply {
                        background = GradientDrawable().apply {
                            setColor(if (index == 0 || index == 6) palette.keyVariant else palette.key)
                            cornerRadius = dp(3).toFloat()
                        }
                    }, LinearLayout.LayoutParams(0, dp(35), 1f).apply {
                        setMargins(dp(2), 0, dp(2), 0)
                    })
                }
            }, LinearLayout.LayoutParams(-1, 0, 1f).apply {
                topMargin = dp(14)
                bottomMargin = dp(8)
            })
            addView(TextView(this@MirrorService).apply {
                text = when (id) {
                    "crimson" -> "Warm crimson inspired by foldable PCs"
                    "cloud" -> "Soft cloud blue keyboard"
                    "standard" -> "Neutral dark keyboard"
                    else -> "Custom keyboard theme"
                }
                typeface = laptopTypeface
                textSize = 11f
                setTextColor(palette.trackpadText)
            })
            setOnClickListener {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putString("laptop_keyboard_theme", id).apply()
                // Keep the deck attached while changing its contents. Removing
                // it exposes the Android display behind the accessibility layer.
                showLaptopKeyboardSettings()
            }
        }
    }

    private fun rebuildLaptopDeck(showSettings: Boolean = false) {
        val content = laptopContent ?: return
        // Do not fade the whole lower pane: it briefly exposes Android behind
        // the overlay. The replacement is immediate, then the new deck slides in.
        laptopDeck?.let { content.removeView(it) }
        laptopSettingsVisible = false
        val nextDeck = buildLaptopDeck().apply {
            alpha = 0f
            translationY = dp(28).toFloat()
            scaleY = .94f
        }
        content.addView(nextDeck, LinearLayout.LayoutParams(-1, 0, 1f))
        nextDeck.post {
            nextDeck.pivotY = nextDeck.height.toFloat()
            nextDeck.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleY(1f)
                .setInterpolator(PathInterpolator(.22f, 1f, .36f, 1f))
                .setDuration(320L)
                .start()
        }
        content.requestLayout()
        if (showSettings) content.post { showLaptopKeyboardSettings() }
    }

    private fun handleLaptopKey(keyCode: Int) {
        when (keyCode) {
            LAPTOP_SHIFT -> laptopShift = !laptopShift
            LAPTOP_CONTROL -> laptopControl = !laptopControl
            LAPTOP_ALT -> laptopAlt = !laptopAlt
            LAPTOP_CAPS -> laptopCapsLock = !laptopCapsLock
            else -> {
                val isLetter = keyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z
                val shifted = if (isLetter) laptopShift.xor(laptopCapsLock) else laptopShift
                var metaState = 0
                if (shifted) metaState = metaState or KeyEvent.META_SHIFT_ON
                if (laptopControl) metaState = metaState or KeyEvent.META_CTRL_ON
                if (laptopAlt) metaState = metaState or KeyEvent.META_ALT_ON
                injectKey(keyCode, metaState)
                laptopShift = false
                laptopControl = false
                laptopAlt = false
            }
        }
        refreshLaptopModifierKeys()
    }

    private fun refreshLaptopModifierKeys() {
        laptopModifierButtons[LAPTOP_SHIFT]?.background =
            laptopKeyBackground(laptopShift, LAPTOP_SHIFT)
        laptopModifierButtons[LAPTOP_CONTROL]?.background =
            laptopKeyBackground(laptopControl, LAPTOP_CONTROL)
        laptopModifierButtons[LAPTOP_ALT]?.background =
            laptopKeyBackground(laptopAlt, LAPTOP_ALT)
        laptopModifierButtons[LAPTOP_CAPS]?.background =
            laptopKeyBackground(laptopCapsLock, LAPTOP_CAPS)
        refreshLaptopKeyLegends()
        refreshLaptopShortcutLabels()
    }

    private fun refreshLaptopKeyLegends() {
        val shifted = laptopShift
        laptopLegendButtons.forEach { (button, base) ->
            val symbol = if (shifted) shiftedLaptopLegend(base) else base
            if (button.text.toString() != symbol) button.text = symbol
        }
    }

    private fun shiftedLaptopLegend(base: String): String = when (base) {
        "`" -> "~"
        "1" -> "!"
        "2" -> "@"
        "3" -> "#"
        "4" -> "\$"
        "5" -> "%"
        "6" -> "^"
        "7" -> "&"
        "8" -> "*"
        "9" -> "("
        "0" -> ")"
        "-" -> "_"
        "=" -> "+"
        "[" -> "{"
        "]" -> "}"
        "\\" -> "|"
        ";" -> ":"
        "'" -> "\""
        "," -> "<"
        "." -> ">"
        "/" -> "?"
        else -> base
    }

    private fun refreshLaptopShortcutLabels() {
        val secondaryColor = if (laptopKeyboardTheme() == "crimson") {
            Color.rgb(230, 143, 105)
        } else Color.rgb(170, 165, 177)
        laptopShortcutButtons.forEach { (keyCode, button) ->
            val primary = KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")
            button.text = primary
            (button as? LaptopKeyTextView)?.setSecondaryLabel(
                laptopShortcutLabels[keyCode].takeIf { laptopControl },
                secondaryColor
            )
        }
    }

    private fun menuLayoutParams(): FrameLayout.LayoutParams {
        return if (targetWidth >= targetHeight) {
            FrameLayout.LayoutParams(dp(if (workspaceExpanded) 680 else 340), -2, Gravity.START).apply {
                topMargin = dp(18)
                bottomMargin = dp(18)
                leftMargin = dp(18)
            }
        } else {
            FrameLayout.LayoutParams(-1, -2, Gravity.TOP).apply {
                leftMargin = dp(12)
                rightMargin = dp(12)
                topMargin = dp(12)
            }
        }
    }

    private fun buildMenu(): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply {
                setColor(Color.rgb(33, 31, 38))
                cornerRadius = dp(28).toFloat()
            }
            elevation = dp(12).toFloat()
            visibility = View.GONE
        }
        val primary = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), 0, dp(18), dp(14))
            setOnHierarchyChangeListener(object : android.view.ViewGroup.OnHierarchyChangeListener {
                override fun onChildViewAdded(parent: View?, child: View?) = scheduleMenuHeightUpdate()
                override fun onChildViewRemoved(parent: View?, child: View?) = scheduleMenuHeightUpdate()
            })
        }
        container.addView(ScrollView(this).apply {
            isFillViewport = true
            addView(primary, FrameLayout.LayoutParams(-1, -2))
        }, LinearLayout.LayoutParams(if (targetWidth >= targetHeight) dp(340) else 0, -1,
            if (targetWidth >= targetHeight) 0f else 1f))
        menuPrimary = primary
        showMainMenu(primary)
        container.post { scheduleMenuHeightUpdate() }
        return container
    }

    private fun showDemoWindow() {
        if (active) return
        hideDemoWindow()
        pendingDemo = false
        demoMode = true
        val bounds = windowManager?.currentWindowMetrics?.bounds
        targetWidth = bounds?.width()?.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        targetHeight = bounds?.height()?.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
        density = resources.displayMetrics.densityDpi
        val frame = TouchRoutingFrame(this).apply { setBackgroundColor(Color.TRANSPARENT) }
        val scrim = View(this).apply {
            setBackgroundColor(Color.argb(105, 0, 0, 0))
            setOnClickListener { hideDemoWindow() }
        }
        val controls = buildMenu().apply { visibility = View.VISIBLE }
        val laptopDemo = pendingLaptopDemo
        pendingLaptopDemo = false
        val info = TextView(this).apply {
            text = NativeStrings.text("nativeTheThreeFingerGestureIsAnEssential")
            textSize = 14f
            setTextColor(Color.rgb(230, 225, 229))
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.rgb(50, 47, 55))
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), Color.rgb(73, 69, 79))
            }
            elevation = dp(14).toFloat()
        }
        // The laptop keyboard demo already occupies the interaction surface;
        // do not place the generic three-finger gesture hint over it.
        info.visibility = if (laptopDemo) View.GONE else View.VISIBLE
        frame.addView(scrim, FrameLayout.LayoutParams(-1, -1))
        if (laptopDemo) {
            val deck = buildLaptopDeck().apply {
                alpha = 0f
                translationY = dp(28).toFloat()
            }
            laptopModeActive = true
            frame.addView(deck, FrameLayout.LayoutParams(
                -1, (resources.displayMetrics.heightPixels * .5f).toInt(), Gravity.BOTTOM
            ))
            deck.animate().alpha(1f).translationY(0f).setDuration(320L).start()
        }
        // A laptop keyboard demo is a focused keyboard test surface. It must
        // not include the normal Dextop controls or the setup hint; those are
        // reserved for the initial setup/demo surface.
        if (!laptopDemo) {
            frame.addView(controls, menuLayoutParams())
            frame.addView(info, if (targetWidth >= targetHeight) {
                FrameLayout.LayoutParams(dp(340), -2, Gravity.BOTTOM or Gravity.END).apply {
                    rightMargin = dp(18)
                    bottomMargin = dp(18)
                }
            } else {
                FrameLayout.LayoutParams(-1, -2, Gravity.TOP).apply {
                    leftMargin = dp(12)
                    rightMargin = dp(12)
                    topMargin = dp(24)
                }
            })
        }
        if (!laptopDemo && targetWidth < targetHeight) {
            controls.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, _ ->
                val layout = info.layoutParams as FrameLayout.LayoutParams
                val wantedTop = bottom + dp(12)
                if (layout.topMargin != wantedTop) {
                    layout.topMargin = wantedTop
                    info.layoutParams = layout
                }
            }
        }
        frame.setOnApplyWindowInsetsListener { _, insets ->
            if (laptopDemo) return@setOnApplyWindowInsetsListener insets
            val safe = insets.getInsetsIgnoringVisibility(
                WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout()
            )
            val layout = info.layoutParams as FrameLayout.LayoutParams
            if (targetWidth >= targetHeight) {
                layout.rightMargin = dp(18) + safe.right
                layout.bottomMargin = dp(18) + safe.bottom
            } else {
                layout.leftMargin = dp(12) + safe.left
                layout.rightMargin = dp(12) + safe.right
            }
            info.layoutParams = layout
            insets
        }
        root = frame
        menu = controls
        menuScrim = scrim
        demoInfoView = info
        val params = WindowManager.LayoutParams(
            -1,
            -1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            fitInsetsTypes = 0
            setFitInsetsIgnoringVisibility(true)
        }
        rootWindowParams = params
        windowManager?.addView(frame, params)
        frame.requestApplyInsets()
        if (!laptopDemo) {
            controls.alpha = 0f
            controls.scaleX = .94f
            controls.scaleY = .94f
            controls.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(240).start()
        }
    }

    private fun hideDemoWindow() {
        if (!demoMode) return
        removeWindow()
        laptopModeActive = false
        demoMode = false
    }

    private fun demoExplanation(message: String) {
        demoInfoView?.apply {
            animate().cancel()
            alpha = 0f
            text = message
            visibility = View.VISIBLE
            bringToFront()
            animate().alpha(1f).setDuration(180).start()
        }
    }

    private fun scheduleMenuHeightUpdate() {
        val container = menu ?: return
        container.removeCallbacks(menuHeightUpdate)
        container.post(menuHeightUpdate)
    }

    private val menuHeightUpdate = Runnable {
        val container = menu ?: return@Runnable
        val availableWidth = if (targetWidth >= targetHeight) dp(340) else
            (root?.width ?: resources.displayMetrics.widthPixels) - dp(24)
        var wanted = 0
        for (index in 0 until container.childCount) {
            val scroll = container.getChildAt(index) as? ScrollView ?: continue
            val content = scroll.getChildAt(0) ?: continue
            content.measure(
                View.MeasureSpec.makeMeasureSpec(availableWidth.coerceAtLeast(1), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            wanted = maxOf(wanted, content.measuredHeight)
        }
        if (wanted <= 0) return@Runnable
        val screenHeight = root?.height?.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
        val verticalMargins = if (targetWidth >= targetHeight) dp(36) else dp(24)
        val targetHeight = wanted.coerceAtMost((screenHeight - verticalMargins).coerceAtLeast(dp(220)))
        val params = container.layoutParams as? FrameLayout.LayoutParams ?: return@Runnable
        if (params.height == targetHeight) return@Runnable
        val startHeight = container.height.takeIf { it > 0 } ?: targetHeight
        ValueAnimator.ofInt(startHeight, targetHeight).apply {
            duration = 220
            addUpdateListener { animator ->
                container.layoutParams = (container.layoutParams as FrameLayout.LayoutParams).apply {
                    height = animator.animatedValue as Int
                }
            }
            start()
        }
    }

    private fun showMainMenu(panel: LinearLayout) {
        animateMenuResize(panel)
        endOverlayTextInput()
        setOverlayFocusable(false)
        panel.removeAllViews()
        panel.addView(menuTitle(
            "Dextop",
            if (workspaceExpanded) "‹" else "›",
            "workspace_toggle"
        ) {
            if (demoMode) demoExplanation(NativeStrings.text("nativeViewSavedWorkspacesAndSaveCurrentArrangement"))
            toggleWorkspacePanel()
        })
        addCustomControls(panel, overlayButtonOrder())
        panel.addView(actionButton(
            if (overlayLayoutEditing) R.drawable.ic_chevron else R.drawable.ic_edit,
            if (overlayLayoutEditing) NativeStrings.text("nativeCompletePlacementEdit") else NativeStrings.text("nativeEditPlacement")
        ) {
            overlayLayoutEditing = !overlayLayoutEditing
            if (demoMode) demoExplanation(NativeStrings.text("nativeYouCanRearrangeTheLayoutInThe"))
            showMainMenu(panel)
        })
    }

    private fun inputModesView(): View {
        val modes = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun refreshModes() {
            modes.removeAllViews()
            modes.addView(choiceButton(NativeStrings.text("nativeCursor"), !directTouch) {
                setSavedTouchMode(false)
                if (demoMode) demoExplanation(NativeStrings.text("nativeUseTheScreenAsATrackpadTo"))
                refreshModes()
            }, LinearLayout.LayoutParams(0, dp(44), 1f))
            modes.addView(choiceButton(NativeStrings.text("nativeTap"), directTouch) {
                setSavedTouchMode(true)
                if (demoMode) demoExplanation(NativeStrings.text("nativeSendsTheTouchedPositionDirectlyToDextop"))
                refreshModes()
            }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { leftMargin = dp(4) })
        }
        refreshModes()
        return modes.apply {
            layoutParams = LinearLayout.LayoutParams(-1, dp(44)).apply { bottomMargin = dp(8) }
        }
    }

    private fun toggleWorkspacePanel() {
        val container = menu ?: return
        if (targetWidth < targetHeight) {
            showWorkspaceMenu(menuPrimary ?: return)
            return
        }
        workspaceExpanded = !workspaceExpanded
        (menuPrimary?.findViewWithTag<View>("workspace_toggle") as? TextView)?.text =
            if (workspaceExpanded) "‹" else "›"
        val startWidth = container.width.coerceAtLeast(dp(340))
        val endWidth = if (targetWidth >= targetHeight && workspaceExpanded) dp(680) else dp(340)
        if (workspaceExpanded) {
            val workspacePanel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), 0, dp(14), dp(14))
                tag = "workspace_panel"
                setOnHierarchyChangeListener(object : android.view.ViewGroup.OnHierarchyChangeListener {
                    override fun onChildViewAdded(parent: View?, child: View?) = scheduleMenuHeightUpdate()
                    override fun onChildViewRemoved(parent: View?, child: View?) = scheduleMenuHeightUpdate()
                })
            }
            workspacePanel.addView(menuTitle(NativeStrings.text("nativeWorkSpace")))
            workspacePanel.addView(actionButton(R.drawable.ic_add, NativeStrings.text("nativeAddCurrentAppPlacement")) {
                if (demoMode) demoExplanation(NativeStrings.text("nativeSaveTheCurrentAppArrangementAsA"))
                else {
                    workspaceSaveError = saveCurrentWorkspace()
                    rebuildWorkspacePanel(workspacePanel)
                }
            })
            rebuildWorkspacePanel(workspacePanel)
            container.addView(ScrollView(this).apply {
                tag = "workspace_scroll"
                isFillViewport = true
                addView(workspacePanel, FrameLayout.LayoutParams(-1, -2))
            }, LinearLayout.LayoutParams(if (targetWidth >= targetHeight) dp(340) else 0, -1,
                if (targetWidth >= targetHeight) 0f else 1f))
        } else {
            container.findViewWithTag<View>("workspace_scroll")?.apply {
                isClickable = false
                postDelayed({ container.removeView(this) }, 240)
            }
        }
        if (targetWidth >= targetHeight) {
            ValueAnimator.ofInt(startWidth, endWidth).apply {
                duration = 240
                addUpdateListener { animator ->
                    container.layoutParams = (container.layoutParams as FrameLayout.LayoutParams).apply {
                        width = animator.animatedValue as Int
                    }
                }
                start()
            }
        }
        scheduleMenuHeightUpdate()
    }

    private fun showWorkspaceMenu(panel: LinearLayout) {
        animateMenuResize(panel)
        panel.removeAllViews()
        panel.addView(menuTitle(NativeStrings.text("nativeWorkSpace"), NativeStrings.text("nativeReturn")) {
            workspaceExpanded = false
            showMainMenu(panel)
        })
        panel.addView(actionButton(R.drawable.ic_add, NativeStrings.text("nativeAddCurrentAppPlacement")) {
            if (demoMode) demoExplanation(NativeStrings.text("nativeSaveTheCurrentAppArrangementAsA"))
            else {
                workspaceSaveError = saveCurrentWorkspace()
                rebuildWorkspacePanel(panel)
            }
        })
        rebuildWorkspacePanel(panel)
    }

    private fun rebuildWorkspacePanel(panel: LinearLayout) {
        while (panel.childCount > 2) panel.removeViewAt(2)
        val items = workspaceJson()
        if (items.length() == 0) {
            panel.addView(sectionLabel(NativeStrings.text("nativeNoSavedWorkspaces")))
            workspaceSaveError?.let { reason ->
                panel.addView(TextView(this).apply {
                    text = reason
                    textSize = 12f
                    setTextColor(Color.rgb(255, 180, 171))
                    setPadding(dp(8), dp(6), dp(8), dp(10))
                })
            }
            return
        }
        for (index in 0 until items.length()) {
            val workspace = items.optJSONObject(index) ?: continue
            panel.addView(workspaceButton(workspace))
        }
        workspaceSaveError?.let { reason ->
            panel.addView(TextView(this).apply {
                text = reason
                textSize = 12f
                setTextColor(Color.rgb(255, 180, 171))
                setPadding(dp(8), dp(6), dp(8), dp(10))
            })
        }
    }

    private fun workspaceButton(workspace: JSONObject): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(14), 0, dp(14), 0)
        background = GradientDrawable().apply {
            setColor(Color.rgb(50, 47, 55))
            cornerRadius = dp(16).toFloat()
        }
        val name = workspace.optString("name", NativeStrings.text("nativeWorkSpace"))
        addView(TextView(this@MirrorService).apply {
            text = name
            textSize = 15f
            setTextColor(Color.rgb(230, 225, 229))
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1
            isSelected = true
            setHorizontallyScrolling(true)
            gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(0, dp(50), 1f).apply { rightMargin = dp(8) })
        val apps = workspace.optJSONArray("apps") ?: JSONArray()
        val icons = LinearLayout(this@MirrorService).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            textDirection = View.TEXT_DIRECTION_LTR
            gravity = Gravity.CENTER_VERTICAL or Gravity.LEFT
        }
        for (index in 0 until minOf(apps.length(), 4)) {
            val packageName = apps.optString(index)
            val icon = runCatching { packageManager.getApplicationIcon(packageName) }.getOrNull()
            icons.addView(ImageView(this@MirrorService).apply {
                setImageDrawable(icon)
                contentDescription = packageName
            }, LinearLayout.LayoutParams(dp(28), dp(28)).apply {
                if (index > 0) leftMargin = dp(4)
                gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL
            })
        }
        // The icon column itself is anchored to the right. Icons inside that
        // fixed four-slot column always start from its left edge.
        addView(icons, LinearLayout.LayoutParams(dp(4 * 28 + 3 * 4), dp(50)))
        isClickable = true
        isFocusable = true
        setOnClickListener {
            if (demoMode) demoExplanation(NativeStrings.text("nativeOpenDextopWithYourSavedAppPlacement"))
            else launchOverlayWorkspace(workspace)
        }
    }.also { it.layoutParams = LinearLayout.LayoutParams(-1, dp(50)).apply { bottomMargin = dp(8) } }

    private fun saveCurrentWorkspace(): String? {
        val captured = captureCurrentWorkspace()
            ?: return NativeStrings.text("nativeFailedToSaveUnableToRetrieveRunning")
        val all = workspaceJson()
        all.put(captured.apply {
            put("id", System.currentTimeMillis().toString())
            put("name", "${NativeStrings.text("nativeWorkSpace")} ${all.length() + 1}")
        })
        val saved = getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE).edit()
            .putString("flutter.workspaces", all.toString()).commit()
        return if (saved) null else NativeStrings.text("nativeFailedToSaveFailedToWriteTo")
    }

    private fun captureCurrentWorkspace(): JSONObject? {
        val apps = JSONArray()
        val bounds = JSONObject()
        val currentTasks = currentDisplayTasks()
        currentTasks.forEach { (packageName, rect) ->
            if (!isWorkspaceApp(packageName)) return@forEach
            apps.put(packageName)
            bounds.put(packageName, JSONArray(listOf(rect.left, rect.top, rect.right, rect.bottom)))
        }
        if (apps.length() == 0) {
            windows.orEmpty().forEach { window ->
                if (Build.VERSION.SDK_INT >= 30 && window.displayId != targetDisplayId) return@forEach
                val packageName = window.root?.packageName?.toString() ?: return@forEach
                if (!isWorkspaceApp(packageName)) return@forEach
                val rect = Rect()
                window.getBoundsInScreen(rect)
                if (rect.width() < dp(80) || rect.height() < dp(80) || bounds.has(packageName)) return@forEach
                apps.put(packageName)
                bounds.put(packageName, JSONArray(listOf(rect.left, rect.top, rect.right, rect.bottom)))
            }
        }
        if (apps.length() == 0) {
            launchedAppBounds.forEach { (packageName, rect) ->
                if (!isWorkspaceApp(packageName)) return@forEach
                apps.put(packageName)
                bounds.put(packageName, JSONArray(listOf(rect.left, rect.top, rect.right, rect.bottom)))
            }
        }
        if (apps.length() == 0) {
            Log.w(logTag, "no app windows available to save")
            return null
        }
        return JSONObject().apply {
            put("apps", apps)
            put("positions", JSONObject())
            put("bounds", bounds)
            put("layout", "captured")
        }
    }

    private fun currentDisplayTasks(): Map<String, Rect> {
        if (targetDisplayId < 0) return emptyMap()
        val result = privilegedAccess.execute("sh", "-c", "dumpsys activity activities")
        if (!result.succeeded) {
            Log.w(logTag, "task query failed: ${result.error}")
            return emptyMap()
        }
        val displayHeader = Regex("Display: mDisplayId=$targetDisplayId(?:\\s|\\()")
        val packagePattern = Regex("(?:A=\\d+:|I=)([A-Za-z0-9._]+)")
        val boundsPattern = Regex("bounds=\\[(-?\\d+),(-?\\d+)]\\[(-?\\d+),(-?\\d+)]")
        val found = linkedMapOf<String, Rect>()
        var inDisplay = false
        var pendingPackage: String? = null
        result.output.lineSequence().forEach { line ->
            if (line.contains("Display: mDisplayId=")) {
                inDisplay = displayHeader.containsMatchIn(line)
                pendingPackage = null
                return@forEach
            }
            if (!inDisplay) return@forEach
            packagePattern.find(line)?.groupValues?.getOrNull(1)?.let { candidate ->
                pendingPackage = candidate.takeIf {
                    !line.contains("type=home") && isWorkspaceApp(it)
                }
            }
            val match = boundsPattern.find(line) ?: return@forEach
            val packageName = pendingPackage ?: return@forEach
            val values = match.groupValues.drop(1).mapNotNull(String::toIntOrNull)
            if (values.size == 4 && values[2] > values[0] && values[3] > values[1]) {
                found.putIfAbsent(packageName, Rect(values[0], values[1], values[2], values[3]))
                pendingPackage = null
            }
        }
        OperationLog.i(this, "Workspace", "task query count=${found.size} display=$targetDisplayId")
        return found
    }

    /**
     * A captured workspace must contain only packages Dextop can restore. Task
     * dumps also include System UI surfaces, launchers, providers and transient
     * activities; those have no meaningful launcher icon and previously became
     * transparent entries in the workspace UI.
     */
    private fun isWorkspaceApp(candidate: String): Boolean {
        if (candidate.isBlank() || candidate == packageName() || candidate == "com.android.systemui") return false
        val launchIntent = packageManager.getLaunchIntentForPackage(candidate) ?: return false
        val activity = launchIntent.resolveActivity(packageManager) ?: return false
        val info = runCatching {
            packageManager.getApplicationInfo(candidate, 0)
        }.getOrNull() ?: return false
        if (!info.enabled) return false

        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val homePackages = packageManager.queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY)
            .asSequence().map { it.activityInfo.packageName }.toSet()
        return candidate !in homePackages && activity.packageName == candidate
    }

    private fun launchOverlayWorkspace(workspace: JSONObject, closeMenu: Boolean = true) {
        val apps = workspace.optJSONArray("apps") ?: return
        val positions = workspace.optJSONObject("positions") ?: JSONObject()
        val bounds = workspace.optJSONObject("bounds") ?: JSONObject()
        for (index in 0 until apps.length()) {
            val packageName = apps.optString(index)
            val rawBounds = bounds.optJSONArray(packageName)
            val position = positions.optString(packageName).takeIf { it.isNotEmpty() }
            android.os.Handler(mainLooper).postDelayed({
                when {
                    rawBounds != null && rawBounds.length() == 4 -> launchPackage(
                        packageName,
                        Rect(rawBounds.optInt(0), rawBounds.optInt(1), rawBounds.optInt(2), rawBounds.optInt(3))
                    )
                    position != null -> launchPackageAt(packageName, position)
                    else -> launchPackage(packageName)
                }
            }, index * 350L)
        }
        if (closeMenu) toggleMenu()
    }

    private fun workspaceJson(): JSONArray = runCatching {
        JSONArray(getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
            .getString("flutter.workspaces", "[]"))
    }.getOrDefault(JSONArray())

    private fun packageName(): String = applicationContext.packageName

    private fun sliderRow(label: String, value: Int, maximum: Int, changed: (Int) -> Unit): View =
        FrameLayout(this).apply {
            val levelIcon = LevelIconView(this@MirrorService, label == NativeStrings.text("nativeVolume")).apply {
                level = value.toFloat() / maximum.coerceAtLeast(1)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            addView(SeekBar(this@MirrorService).apply {
                max = maximum
                progress = value.coerceIn(0, maximum)
                splitTrack = false
                setPadding(0, 0, 0, 0)
                progressDrawable = controlCenterTrack()
                thumb = ColorDrawable(Color.TRANSPARENT)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(view: SeekBar?, progress: Int, fromUser: Boolean) {
                        levelIcon.level = progress.toFloat() / maximum.coerceAtLeast(1)
                        if (fromUser) changed(progress)
                    }
                    override fun onStartTrackingTouch(view: SeekBar?) = Unit
                    override fun onStopTrackingTouch(view: SeekBar?) = Unit
                })
            }, FrameLayout.LayoutParams(-1, dp(54), Gravity.CENTER_VERTICAL))
            addView(levelIcon, FrameLayout.LayoutParams(dp(40), dp(32), Gravity.START or Gravity.CENTER_VERTICAL).apply {
                leftMargin = dp(10)
            })
            contentDescription = label
        }.also { it.layoutParams = LinearLayout.LayoutParams(-1, dp(54)).apply { bottomMargin = dp(8) } }

    private fun controlCenterTrack(): LayerDrawable {
        val background = GradientDrawable().apply {
            setColor(Color.rgb(92, 90, 97))
            cornerRadius = dp(16).toFloat()
        }
        val progress = ClipDrawable(GradientDrawable().apply {
            setColor(Color.rgb(242, 240, 244))
            cornerRadius = dp(16).toFloat()
        }, Gravity.START, ClipDrawable.HORIZONTAL)
        return LayerDrawable(arrayOf(background, progress)).apply {
            setId(0, android.R.id.background)
            setId(1, android.R.id.progress)
            setLayerHeight(0, dp(54))
            setLayerHeight(1, dp(54))
            setLayerGravity(0, Gravity.CENTER_VERTICAL)
            setLayerGravity(1, Gravity.CENTER_VERTICAL)
        }
    }

    private fun systemVolume(): Int {
        val audio = getSystemService(AudioManager::class.java)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max
    }

    private fun setSystemVolume(percent: Int) {
        val audio = getSystemService(AudioManager::class.java)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, max * percent / 100, 0)
    }

    private fun currentBrightness(): Int {
        val explicit = rootWindowParams?.screenBrightness ?: -1f
        if (explicit >= 0f) return (explicit * 100).toInt()
        return runCatching { Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS) * 100 / 255 }
            .getOrDefault(50)
    }

    private fun setOverlayBrightness(percent: Int) {
        val params = rootWindowParams ?: return
        params.screenBrightness = percent.coerceIn(1, 100) / 100f
        root?.let { windowManager?.updateViewLayout(it, params) }
    }

    private fun temporarilyReturnToAndroid() {
        val pausedWorkspace = captureCurrentWorkspace()
        pausedForAndroid = true
        active = false
        stopHostDisplayMonitor()
        sessionJournal.paused()
        getSharedPreferences("dextop_cleanup_state", MODE_PRIVATE).edit()
            .putBoolean("cleanup_pending", true)
            .putBoolean("paused_by_user", true)
            .putLong("paused_at", System.currentTimeMillis())
            .apply {
                if (pausedWorkspace == null) remove("paused_workspace")
                else putString("paused_workspace", pausedWorkspace.toString())
            }
            .commit()
        overlayLayoutEditing = false
        suspendedForLockScreen = false
        suspendedConfig = null
        setPhoneNavigationDisabled(false)
        releasePhoneRotation(clearSnapshot = true)
        // Detach the accessibility host first. Surface destruction normally
        // releases the VirtualDisplay immediately, which makes One UI try to
        // unregister gesture-exclusion listeners from an already removed
        // display. That WindowManager exception leaves Back and Circle to
        // Search broken until SystemUI is restarted.
        detachHostWindow()
        android.os.Handler(mainLooper).postDelayed({
            runCatching { DisplayEnvironmentSettings(this).restoreTopology() }
                .onFailure { Log.e(logTag, "topology restoration failed", it) }
            releaseMirror()
            runCatching { displayBackend.clearRequest() }
            targetDisplayId = -1
            desktopModeConfigurator.restore()
            runCatching { internalRefreshRateController.restore() }
                .onFailure { Log.e(logTag, "refresh-rate restoration failed", it) }
            MainActivity.restoreOrientation()
            runCatching {
                val home = Intent(this, MainActivity::class.java)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                val options = ActivityOptions.makeBasic().setLaunchDisplayId(0)
                startActivity(home, options.toBundle())
            }.onFailure { Log.e(logTag, "unable to return to Dextop home", it) }
            // Keep SessionJournal intact: the app shows its explicit recovery card.
            android.os.Handler(mainLooper).postDelayed({ disableSelf() }, 180)
        }, 320)
        Log.i(logTag, "session paused; returned to Dextop home for explicit recovery")
    }

    private fun overlayButtonOrder(): MutableList<String> {
        val saved = getSharedPreferences(PREFS, MODE_PRIVATE)
            .getString("overlay_button_order", "modes,volume,brightness,resolution,android,stop,reconnect,orientation,mouse_route,keyboard_route").orEmpty()
            .replace("actions", "stop,reconnect,orientation")
            .split(',').filter { it in allControlIds }.toMutableList()
        // Migrate the original routing-button default. Explicit user ordering
        // remains untouched unless the two controls are still in that exact
        // legacy position.
        if (saved.indexOf("mouse_route") + 1 == saved.indexOf("keyboard_route") &&
            saved.indexOf("keyboard_route") < saved.indexOf("stop")
        ) {
            saved.remove("mouse_route")
            saved.remove("keyboard_route")
            val insertion = (saved.indexOf("orientation") + 1).coerceAtLeast(0)
            saved.add(insertion, "mouse_route")
            saved.add(insertion + 1, "keyboard_route")
        }
        saved.removeAll { it !in controlIds }
        controlIds.forEach { if (it !in saved) saved.add(it) }
        saved.remove("laptop")
        if ("laptop" in controlIds) {
            saved.add((saved.indexOf("android") + 1).coerceAtLeast(0), "laptop")
        }
        return saved
    }

    private val allControlIds get() = listOf(
        "modes", "volume", "brightness", "resolution", "android", "laptop",
        "stop", "reconnect", "orientation", "mouse_route", "keyboard_route"
    )
    private val controlIds get() = allControlIds.filter {
        when (it) {
            // Demo mode documents every available control even on a
            // non-foldable phone; the action only performs work on a
            // supported foldable when the demo is not active.
            // Keep the manual control available whenever laptop posture
            // detection is enabled.  The service menu is hosted on the
            // virtual/overlay display, and during a fold or display handoff
            // the default display can briefly report the wrong panel (or no
            // foldable main panel at all).  Gating this only on
            // isFoldableMainDisplay() made the button disappear exactly when
            // automatic laptop mode was enabled.
            "laptop" -> demoMode || isDebugLaptopModeForced() ||
                isLaptopAutoDetectionEnabled() || isFoldableDevice() || isFoldableMainDisplay()
            "mouse_route", "keyboard_route" ->
                demoMode || physicalInputRoutingSupported && physicalExternalDisplayConnected
            else -> true
        }
    }

    private fun addCustomControls(panel: LinearLayout, order: List<String>) {
        val mutableOrder = order.toMutableList()
        val views = linkedMapOf<String, View>()
        val columns = if (demoMode || physicalExternalDisplayConnected) 5 else 3
        val squareIds = setOf("mouse_route", "keyboard_route", "stop", "reconnect", "orientation")
        val grid = GridLayout(this).apply {
            columnCount = columns
            alignmentMode = GridLayout.ALIGN_BOUNDS
            layoutTransition = LayoutTransition().apply {
                setDuration(LayoutTransition.CHANGING, 180)
                setDuration(LayoutTransition.APPEARING, 0)
                setDuration(LayoutTransition.DISAPPEARING, 0)
                enableTransitionType(LayoutTransition.CHANGING)
            }
        }
        fun applyGridPositions() {
            var row = 0
            var column = 0
            mutableOrder.forEach { id ->
                val view = views[id] ?: return@forEach
                val span = if (id in squareIds) 1 else columns
                if (span == columns && column != 0) { row++; column = 0 }
                if (span == 1 && column + span > columns) { row++; column = 0 }
                view.layoutParams = GridLayout.LayoutParams(
                    GridLayout.spec(row),
                    GridLayout.spec(column, span, 1f)
                ).apply {
                    width = 0
                    height = dp(54)
                    bottomMargin = dp(8)
                    if (column > 0) leftMargin = dp(6)
                }
                if (span == columns) { row++; column = 0 } else {
                    column++
                    if (column == columns) { row++; column = 0 }
                }
            }
            grid.requestLayout()
            scheduleMenuHeightUpdate()
        }
        fun control(id: String): View = when (id) {
            "modes" -> inputModesView()
            "volume" -> sliderRow(NativeStrings.text("nativeVolume"), systemVolume(), 100) {
                setSystemVolume(it)
                if (demoMode) demoExplanation(NativeStrings.text("nativeAdjustTheVolumeOfPlaybackOnDextop"))
            }
            "brightness" -> sliderRow(NativeStrings.text("nativeScreenBrightness"), currentBrightness(), 100) {
                setOverlayBrightness(it)
                if (demoMode) demoExplanation(NativeStrings.text("nativeAdjustTheBrightnessOfTheDesktopDisplay"))
            }
            "resolution" -> actionButton(R.drawable.ic_monitor, "${NativeStrings.text("nativeResolution")}   ${targetWidth} × ${targetHeight}") {
                if (demoMode) demoExplanation(NativeStrings.text("nativeSwitchDextopResolutionAndDpi"))
                showResolutionMenu(panel)
            }
            "android" -> actionButton(R.drawable.ic_smartphone, NativeStrings.text("nativeTemporarilyReturnToAndroid")) {
                if (demoMode) demoExplanation(NativeStrings.text("nativePauseDextopAndReturnYourAndroidTo"))
                else temporarilyReturnToAndroid()
            }
            "laptop" -> actionButton(
                R.drawable.ic_keyboard,
                NativeStrings.text("nativeLaptopMode")
            ) {
                if (demoMode) demoExplanation(NativeStrings.text("nativeLaptopModeDescription"))
                else {
                    val enableManually = !laptopModeActive
                    laptopManualOverride = enableManually
                    laptopAutoActivated = false
                    setLaptopMode(enableManually)
                }
            }
            else -> squareControl(id)
        }
        mutableOrder.forEach { id ->
            val view = control(id).apply { tag = id }
            views[id] = view
            grid.addView(view)
            if (overlayLayoutEditing) {
                view.setOnLongClickListener {
                    view.startDragAndDrop(null, View.DragShadowBuilder(view), id, 0)
                    true
                }
                view.setOnDragListener { target, event ->
                    val dragged = event.localState as? String
                    val destination = target.tag as? String
                    when (event.action) {
                        DragEvent.ACTION_DRAG_ENTERED -> {
                            if (dragged != null && destination != null && dragged != destination) {
                                val from = mutableOrder.indexOf(dragged)
                                val to = mutableOrder.indexOf(destination)
                                if (from >= 0 && to >= 0) {
                                    mutableOrder.add(to, mutableOrder.removeAt(from))
                                    applyGridPositions()
                                }
                            }
                            true
                        }
                        DragEvent.ACTION_DROP -> {
                            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                                .putString("overlay_button_order", mutableOrder.joinToString(",")).apply()
                            true
                        }
                        else -> true
                    }
                }
            }
        }
        applyGridPositions()
        panel.addView(grid, LinearLayout.LayoutParams(-1, -2))
    }

    private fun squareControl(id: String): ImageButton = when (id) {
        "mouse_route" -> routingAction(
            R.drawable.ic_mouse,
            NativeStrings.text("nativePhysicalMouse"),
            mouseActuallyRouted
        ) {
            if (demoMode) demoExplanation(NativeStrings.text("nativePhysicalMouseDemo"))
            else setPhysicalInputRouting(mouse = !mouseActuallyRouted)
        }
        "keyboard_route" -> routingAction(
            R.drawable.ic_keyboard,
            NativeStrings.text("nativePhysicalKeyboard"),
            keyboardActuallyRouted
        ) {
            if (demoMode) demoExplanation(NativeStrings.text("nativePhysicalKeyboardDemo"))
            else setPhysicalInputRouting(keyboard = !keyboardActuallyRouted)
        }
        "stop" -> squareAction(R.drawable.ic_stop, NativeStrings.text("nativeEnd"), true) {
            if (demoMode) demoExplanation(NativeStrings.text("nativeTerminateYourDextopSession")) else stop()
        }
        "reconnect" -> squareAction(R.drawable.ic_reload, NativeStrings.text("nativeReconnect")) {
            if (demoMode) demoExplanation(NativeStrings.text("nativeReconnectIfYouHaveDisplayOrConnection"))
            else start(Config(targetWidth, targetHeight, density, secureDisplay, showSystemDecorations))
        }
        else -> squareAction(
            if (targetWidth >= targetHeight) R.drawable.ic_portrait else R.drawable.ic_landscape,
            if (targetWidth >= targetHeight) NativeStrings.text("nativeVerticalHolding") else NativeStrings.text("nativeHorizontalHolding")
        ) {
            if (demoMode) demoExplanation(NativeStrings.text("nativeSwitchBetweenPortraitAndLandscapeOrientationOf"))
            else changeOrientation()
        }
    }

    private fun squareAction(icon: Int, description: String, destructive: Boolean = false, action: () -> Unit) =
        ImageButton(this).apply {
            setImageResource(icon)
            contentDescription = description
            setColorFilter(if (destructive) Color.rgb(255, 180, 171) else Color.rgb(230, 225, 229))
            background = GradientDrawable().apply {
                setColor(if (destructive) Color.rgb(73, 37, 35) else Color.rgb(50, 47, 55))
                cornerRadius = dp(16).toFloat()
            }
            setOnClickListener { action() }
        }

    private fun routingAction(icon: Int, description: String, enabled: Boolean, action: () -> Unit) =
        squareAction(icon, description, action = action).apply {
            setColorFilter(if (enabled) Color.rgb(226, 196, 255) else Color.rgb(202, 196, 208))
            background = GradientDrawable().apply {
                setColor(if (enabled) Color.rgb(79, 55, 111) else Color.rgb(50, 47, 55))
                cornerRadius = dp(16).toFloat()
            }
            isSelected = enabled
        }

    private fun setPhysicalInputRouting(mouse: Boolean? = null, keyboard: Boolean? = null) {
        if (!physicalInputRoutingSupported) {
            runCatching { physicalInputRouter.restore() }
            return
        }
        routePhysicalMouseToDextop = mouse ?: routePhysicalMouseToDextop
        routePhysicalKeyboardToDextop = keyboard ?: routePhysicalKeyboardToDextop
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putBoolean(KEY_ROUTE_MOUSE, routePhysicalMouseToDextop)
            .putBoolean(KEY_ROUTE_KEYBOARD, routePhysicalKeyboardToDextop)
            .apply()
        val display = getSystemService(DisplayManager::class.java).getDisplay(targetDisplayId)
        if (display != null) runCatching {
            physicalInputRouter.apply(display, routePhysicalMouseToDextop, routePhysicalKeyboardToDextop)
        }.onFailure { OperationLog.w(this, "InputRouting", "overlay routing change failed", it) }
        if (!routePhysicalMouseToDextop) activateTouchInput()
        root?.postDelayed({
            refreshActualRoutingState(display)
            menuPrimary?.let(::showMainMenu)
        }, 350)
    }

    private fun refreshExternalDisplayState() {
        root?.post {
            val connected = physicalInputRoutingSupported && externalDisplayDetector.snapshot().connected
            if (connected == physicalExternalDisplayConnected) return@post
            physicalExternalDisplayConnected = connected
            val display = getSystemService(DisplayManager::class.java).getDisplay(targetDisplayId)
            if (connected && active && display != null) {
                runCatching {
                    physicalInputRouter.apply(display, routePhysicalMouseToDextop, routePhysicalKeyboardToDextop)
                }.onFailure { OperationLog.w(this, "InputRouting", "external display routing failed", it) }
                if (routePhysicalMouseToDextop) startRawMouseReader()
            } else {
                runCatching { physicalInputRouter.restore() }
                    .onFailure { OperationLog.w(this, "InputRouting", "external display disconnect restoration failed", it) }
                stopRawMouseReader()
                activateTouchInput()
                mouseActuallyRouted = false
                keyboardActuallyRouted = false
            }
            refreshActualRoutingState(display)
            menuPrimary?.let(::showMainMenu)
            Log.i(logTag, "physical external display connected=$connected")
        }
    }

    private fun refreshActualRoutingState(display: Display?) {
        if (display == null || !physicalExternalDisplayConnected) {
            mouseActuallyRouted = false
            keyboardActuallyRouted = false
            return
        }
        mouseActuallyRouted = physicalInputRouter.isMouseRouted(display)
        keyboardActuallyRouted = physicalInputRouter.isKeyboardRouted(display)
        OperationLog.i(
            this,
            "InputRouting",
            "verified display=${display.displayId} mouse=$mouseActuallyRouted keyboard=$keyboardActuallyRouted"
        )
    }

    private fun showResolutionMenu(panel: LinearLayout) {
        animateMenuResize(panel)
        panel.removeAllViews()
        panel.addView(menuTitle(NativeStrings.text("nativeResolution"), NativeStrings.text("nativeReturn")) { showMainMenu(panel) })
        val portrait = targetWidth < targetHeight
        fun oriented(width: Int, height: Int): Pair<Int, Int> =
            if (portrait) height to width else width to height
        resolutionProfiles().forEach { item ->
            val size = oriented(item.width, item.height)
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(actionButton(
                if (item.device) R.drawable.ic_smartphone else R.drawable.ic_monitor,
                "${if (item.device) NativeStrings.text("nativeDevice") else "${item.width} × ${item.height}"}   ${item.density} dpi"
            ) {
                if (demoMode) demoExplanation("${size.first} × ${size.second} / ${item.density} ${NativeStrings.text("nativeSwitchToResolutionSuffix")}")
                else {
                    saveSelectedResolution(item.id)
                    resizeActiveDisplay(
                        effectiveConfig(Config(size.first, size.second, item.density, secureDisplay, showSystemDecorations)),
                        "resolution selected from overlay"
                    )
                }
            }, LinearLayout.LayoutParams(0, dp(50), 1f))
            row.addView(editButton { showResolutionEditor(panel, item) }, LinearLayout.LayoutParams(dp(50), dp(50)).apply { leftMargin = dp(6) })
            panel.addView(row, LinearLayout.LayoutParams(-1, dp(50)).apply { bottomMargin = dp(8) })
        }
        panel.addView(actionButton(R.drawable.ic_add, NativeStrings.text("nativeAddCustomResolution")) {
            showResolutionEditor(panel, null)
        })
    }

    private fun showResolutionEditor(panel: LinearLayout, existing: ResolutionProfile?) {
        animateMenuResize(panel)
        panel.removeAllViews()
        panel.addView(menuTitle(if (existing == null) NativeStrings.text("nativeAddResolution") else NativeStrings.text("nativeEditResolution"), NativeStrings.text("nativeReturn")) { showResolutionMenu(panel) })
        val width = numberField((existing?.width ?: targetWidth).toString(), NativeStrings.text("nativeWidth")).apply { isEnabled = existing?.device != true }
        val height = numberField((existing?.height ?: targetHeight).toString(), NativeStrings.text("nativeHeight")).apply { isEnabled = existing?.device != true }
        val dpi = numberField((existing?.density ?: density).toString(), "dpi")
        val fields = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fields.addView(width, LinearLayout.LayoutParams(0, dp(52), 1f))
        fields.addView(height, LinearLayout.LayoutParams(0, dp(52), 1f).apply { leftMargin = dp(6) })
        fields.addView(dpi, LinearLayout.LayoutParams(0, dp(52), 1f).apply { leftMargin = dp(6) })
        panel.addView(fields, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(8) })
        panel.addView(actionButton(R.drawable.ic_chevron, if (existing == null) NativeStrings.text("nativeAddAndApply") else NativeStrings.text("nativeSaveAndApply")) {
            val w = width.text.toString().toIntOrNull()
            val h = height.text.toString().toIntOrNull()
            val d = dpi.text.toString().toIntOrNull()
            if (w != null && h != null && d != null && w in 480..7680 && h in 480..7680 && d in 80..640) {
                val updated = ResolutionProfile(existing?.id ?: "custom_${System.currentTimeMillis()}", w, h, d, existing?.device == true)
                saveResolutionProfile(updated)
                saveSelectedResolution(updated.id)
                val portrait = targetWidth < targetHeight
                resizeActiveDisplay(
                    effectiveConfig(Config(
                        if (portrait) h else w,
                        if (portrait) w else h,
                        d,
                        secureDisplay,
                        showSystemDecorations
                    )),
                    "custom resolution applied from overlay"
                )
            }
        })
        if (existing != null && !existing.device) panel.addView(actionButton(R.drawable.ic_stop, NativeStrings.text("nativeRemoveThisResolution"), true) {
            deleteResolutionProfile(existing.id)
            showResolutionMenu(panel)
        })
    }

    private fun editButton(action: () -> Unit): ImageButton = ImageButton(this).apply {
        setImageResource(R.drawable.ic_edit)
        setColorFilter(Color.rgb(230, 225, 229))
        contentDescription = NativeStrings.text("nativeEdit")
        background = GradientDrawable().apply { setColor(Color.rgb(50, 47, 55)); cornerRadius = dp(16).toFloat() }
        setOnClickListener { action() }
    }

    private fun resolutionProfiles(): List<ResolutionProfile> =
        resolutionRepository.profiles(density)

    private fun saveResolutionProfile(profile: ResolutionProfile) =
        resolutionRepository.save(profile, density)

    private fun deleteResolutionProfile(id: String) =
        resolutionRepository.delete(id, density)

    private fun saveSelectedResolution(id: String) = resolutionRepository.select(id)

    private fun menuTitle(
        title: String,
        action: String? = null,
        actionTag: String? = null,
        onAction: (() -> Unit)? = null
    ): View =
        LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@MirrorService).apply {
                text = title
                textSize = 22f
                setTextColor(Color.rgb(230, 225, 229))
                gravity = Gravity.CENTER_VERTICAL
            }, LinearLayout.LayoutParams(0, dp(54), 1f))
            if (action != null) addView(TextView(this@MirrorService).apply {
                text = action
                textSize = if (actionTag == "workspace_toggle") 28f else 14f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(208, 188, 255))
                tag = actionTag
                if (actionTag == "workspace_toggle") {
                    contentDescription = NativeStrings.text("nativeExpandWorkspace")
                }
                setOnClickListener { onAction?.invoke() }
            }, LinearLayout.LayoutParams(
                if (actionTag == "workspace_toggle") dp(52) else dp(64),
                dp(52)
            ))
        }

    private fun animateMenuResize(panel: LinearLayout) {
        if (!panel.isLaidOut) return
        TransitionManager.beginDelayedTransition(panel.parent as? FrameLayout ?: panel, ChangeBounds().apply {
            duration = 260
        })
    }

    private fun sectionLabel(label: String): TextView = TextView(this).apply {
        text = label
        textSize = 12f
        setTextColor(Color.rgb(202, 196, 208))
        setPadding(dp(4), dp(8), 0, dp(8))
    }

    private fun actionButton(icon: Int = R.drawable.ic_chevron, label: String, destructive: Boolean = false, action: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = 15f
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
            setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0)
            compoundDrawablePadding = dp(14)
            setTextColor(if (destructive) Color.rgb(255, 180, 171) else Color.rgb(230, 225, 229))
            background = GradientDrawable().apply {
                setColor(if (destructive) Color.rgb(73, 37, 35) else Color.rgb(50, 47, 55))
                cornerRadius = dp(16).toFloat()
            }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                animate().scaleX(.97f).scaleY(.97f).setDuration(70).withEndAction {
                    animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    action()
                }.start()
            }
        }.also { it.layoutParams = LinearLayout.LayoutParams(-1, dp(50)).apply { bottomMargin = dp(8) } }

    private fun choiceButton(label: String, selected: Boolean, action: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(if (selected) Color.rgb(45, 38, 52) else Color.rgb(230, 225, 229))
            background = GradientDrawable().apply {
                setColor(if (selected) Color.rgb(232, 222, 248) else Color.rgb(50, 47, 55))
                cornerRadius = dp(14).toFloat()
            }
            setOnClickListener { action() }
        }

    private fun numberField(value: String, label: String): EditText = EditText(this).apply {
        setText(value)
        hint = label
        inputType = 2
        textSize = 14f
        gravity = Gravity.CENTER
        setTextColor(Color.rgb(230, 225, 229))
        setHintTextColor(Color.rgb(147, 143, 153))
        background = GradientDrawable().apply {
            setColor(Color.rgb(50, 47, 55))
            setStroke(dp(1), Color.rgb(147, 143, 153))
            cornerRadius = dp(14).toFloat()
        }
        isFocusableInTouchMode = true
        setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                beginOverlayTextInput(view as EditText)
            }
        }
        setOnClickListener {
            beginOverlayTextInput(this)
        }
    }

    private fun trackpad(event: MotionEvent, forceCursorMode: Boolean = false): Boolean {
        val useDirectTouch = directTouch && !forceCursorMode
        maxPointers = maxOf(maxPointers, event.pointerCount)
        if (experimentalMultiTouch && handleExperimentalEdgeGesture(event)) return true
        if (useDirectTouch && experimentalMultiTouch) {
            injectDirectTouch(event)
            return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downTime = SystemClock.uptimeMillis()
                lastX = event.x
                lastY = event.y
                touchStartX = event.x
                touchStartY = event.y
                moved = false
                twoFinger = false
                threeFinger = false
                scrolling = false
                longPressTriggered = false
                if (useDirectTouch) {
                    moveCursorToTouch(event.x, event.y)
                    injectTouch(MotionEvent.ACTION_DOWN, cursorX, cursorY)
                    directTouchHeld = true
                    return true
                }
                longPressRunnable?.let { root?.removeCallbacks(it) }
                longPressRunnable = Runnable {
                    if (!moved && !twoFinger && maxPointers == 1) {
                        longPressTriggered = true
                        performLongPressGesture()
                    }
                }.also { root?.postDelayed(it, 450) }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (directTouchHeld) {
                    injectTouch(MotionEvent.ACTION_UP, cursorX, cursorY)
                    directTouchHeld = false
                }
                longPressRunnable?.let { root?.removeCallbacks(it) }
                if (event.pointerCount >= 3) {
                    threeFinger = true
                    if (scrolling) injectTouch(MotionEvent.ACTION_UP, cursorX, scrollY)
                    scrolling = false
                    return true
                }
                if (event.pointerCount == 2) {
                    twoFinger = true
                    lastScrollY = event.getY(0)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (directTouchHeld && event.pointerCount == 1) {
                    moveCursorToTouch(event.x, event.y)
                    injectTouch(MotionEvent.ACTION_MOVE, cursorX, cursorY)
                    moved = true
                } else if (threeFinger) {
                    moved = true
                } else if (event.pointerCount >= 2) {
                    if (!scrolling) {
                        scrolling = true
                        scrollY = cursorY
                        injectTouch(MotionEvent.ACTION_DOWN, cursorX, scrollY)
                    }
                    val y = event.getY(0)
                    scrollY = (scrollY + (y - lastScrollY) * 2f).coerceIn(0f, targetHeight - 1f)
                    lastScrollY = y
                    injectTouch(MotionEvent.ACTION_MOVE, cursorX, scrollY)
                    moved = true
                } else if (!twoFinger) {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    lastX = event.x
                    lastY = event.y
                    if (hypot(event.x - touchStartX, event.y - touchStartY) > dp(4)) {
                        moved = true
                        if (!longPressTriggered) longPressRunnable?.let { root?.removeCallbacks(it) }
                    }
                    moveCursor(dx * 2.2f, dy * 2.2f)
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (!threeFinger && scrolling) {
                    injectTouch(MotionEvent.ACTION_UP, cursorX, scrollY)
                    scrolling = false
                }
            }
            MotionEvent.ACTION_UP -> {
                longPressRunnable?.let { root?.removeCallbacks(it) }
                val completedDirectTouch = directTouchHeld
                if (completedDirectTouch) {
                    moveCursorToTouch(event.x, event.y)
                    injectTouch(MotionEvent.ACTION_UP, cursorX, cursorY)
                    directTouchHeld = false
                } else if (scrolling) injectTouch(MotionEvent.ACTION_UP, cursorX, scrollY)
                if (completedDirectTouch) {
                    Unit
                } else if (threeFinger || maxPointers >= 3) {
                    if (!experimentalMultiTouch) performConfiguredGesture()
                } else if (longPressTriggered && dragHeld) {
                    injectTouch(MotionEvent.ACTION_UP, cursorX, cursorY)
                    dragHeld = false
                } else if (twoFinger && !moved && !scrolling) {
                    performTwoFingerGesture()
                } else if (!twoFinger && !moved && !dragHeld && SystemClock.uptimeMillis() - downTime < 250) {
                    if (useDirectTouch) moveCursorToTouch(event.x, event.y)
                    leftClick()
                }
                maxPointers = 0
                threeFinger = false
                scrolling = false
            }
            MotionEvent.ACTION_CANCEL -> {
                longPressRunnable?.let { root?.removeCallbacks(it) }
                if (directTouchHeld) injectTouch(MotionEvent.ACTION_UP, cursorX, cursorY)
                directTouchHeld = false
                if (scrolling) injectTouch(MotionEvent.ACTION_UP, cursorX, scrollY)
                if (dragHeld) injectTouch(MotionEvent.ACTION_UP, cursorX, cursorY)
                dragHeld = false
                maxPointers = 0
                threeFinger = false
                scrolling = false
            }
        }
        return true
    }

    private fun performConfiguredGesture() {
        when (getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
            .getString("flutter.gesture_three_finger", "menu")) {
            "home" -> launchHome()
            "rotate" -> changeOrientation()
            "stop" -> stop()
            else -> toggleMenu()
        }
    }

    private fun handleExperimentalEdgeGesture(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.x
                touchStartY = event.y
                threeFingerEdgeSwipe = false
                edgeMenuTriggered = false
                edgeGestureLeadX = 0f
                edgeGestureLeadY = 0f
            }
            MotionEvent.ACTION_POINTER_DOWN -> if (event.pointerCount >= 3) {
                var minimumX = Float.MAX_VALUE
                var minimumY = Float.MAX_VALUE
                for (index in 0 until event.pointerCount) {
                    val x = event.getX(index)
                    val y = event.getY(index)
                    minimumX = minOf(minimumX, x)
                    minimumY = minOf(minimumY, y)
                }
                val portrait = targetHeight > targetWidth
                if (portrait) {
                    // A 120dp strip forces all three fingers against the top
                    // bezel on tall phones. Extend the pickup region downward
                    // while keeping it proportional on foldables and tablets.
                    val startLimit = minOf(
                        (surfaceView?.height?.times(0.28f) ?: dp(240).toFloat()),
                        dp(240).toFloat()
                    )
                    if (minimumY > startLimit && touchStartY > startLimit) return false
                } else if (minimumX > dp(120) && touchStartX > dp(120)) return false
                threeFingerEdgeSwipe = true
                edgeGestureLeadX = minimumX
                edgeGestureLeadY = minimumY
                if (directTouch) cancelInjectedDirectTouch()
                return true
            }
            MotionEvent.ACTION_MOVE -> if (threeFingerEdgeSwipe) {
                // Keep consuming the intercepted stream, but never complete a
                // three-finger gesture after one of the fingers has lifted.
                if (event.pointerCount < 3) return true
                var minimumX = Float.MAX_VALUE
                var minimumY = Float.MAX_VALUE
                for (index in 0 until event.pointerCount) {
                    minimumX = minOf(minimumX, event.getX(index))
                    minimumY = minOf(minimumY, event.getY(index))
                }
                val distance = if (targetHeight > targetWidth) {
                    minimumY - edgeGestureLeadY
                } else minimumX - edgeGestureLeadX
                val triggerDistance = if (targetHeight > targetWidth) dp(20) else dp(28)
                if (!edgeMenuTriggered && distance >= triggerDistance) {
                    edgeMenuTriggered = true
                    toggleMenu()
                }
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> if (threeFingerEdgeSwipe) return true
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (threeFingerEdgeSwipe) {
                threeFingerEdgeSwipe = false
                edgeMenuTriggered = false
                edgeGestureLeadX = 0f
                edgeGestureLeadY = 0f
                return true
            }
        }
        return false
    }

    private fun performTwoFingerGesture() {
        val action = getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
            .getString("flutter.gesture_two_finger", "right_click")
        when (action) {
            "home" -> launchHome()
            "menu" -> toggleMenu()
            else -> rightClick()
        }
    }

    private fun performLongPressGesture() {
        val action = getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
            .getString("flutter.gesture_long_press", "drag")
        when (action) {
            "right_click" -> rightClick()
            "menu" -> toggleMenu()
            else -> {
                dragHeld = true
                injectTouch(MotionEvent.ACTION_DOWN, cursorX, cursorY)
                cursorView?.pulse()
            }
        }
    }

    private fun moveCursorToTouch(x: Float, y: Float) {
        val view = surfaceView ?: return
        if (view.width <= 0 || view.height <= 0) return
        cursorX = x / view.width * targetWidth
        cursorY = y / view.height * targetHeight
        if (!directTouch) cursorView?.update(cursorX / targetWidth, cursorY / targetHeight)
    }

    private fun setSavedTouchMode(useTapPosition: Boolean) {
        directTouch = useTapPosition
        physicalMouseActive = false
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putBoolean(KEY_DIRECT_TOUCH, useTapPosition)
            .apply()
        cursorView?.visibility = if (useTapPosition) View.GONE else View.VISIBLE
    }

    private fun activateTouchInput() {
        if (overlayTextInputActive) return
        surfaceView?.releasePointerCapture()
        setOverlayFocusable(false)
        physicalMouseActive = false
        // ACTION_DOWN on the phone display hands cursor ownership back to the
        // touchpad immediately, even if the mouse remains connected.
        cursorView?.visibility = if (directTouch) View.GONE else View.VISIBLE
        if (!directTouch) cursorView?.update(cursorX / targetWidth, cursorY / targetHeight)
    }

    private fun activateLaptopTrackpad() {
        if (overlayTextInputActive) return
        surfaceView?.releasePointerCapture()
        setOverlayFocusable(false)
        physicalMouseActive = false
        cursorView?.visibility = View.VISIBLE
        cursorView?.update(cursorX / targetWidth, cursorY / targetHeight)
    }

    private fun activatePhysicalMouse() {
        if (overlayTextInputActive) return
        physicalMouseActive = true
        // Physical mice use Android's pointer only. Dextop's cursor is reserved
        // exclusively for touch-panel trackpad mode.
        cursorView?.visibility = View.GONE
        setOverlayFocusable(true)
        surfaceView?.post {
            surfaceView?.requestFocus()
            surfaceView?.requestPointerCapture()
        }
    }

    private fun handlePhysicalMouseEvent(event: MotionEvent, view: View): Boolean {
        if (!event.isFromSource(InputDevice.SOURCE_MOUSE)) return false
        if (event.actionMasked == MotionEvent.ACTION_MOVE ||
            event.actionMasked == MotionEvent.ACTION_HOVER_MOVE) activatePhysicalMouse()
        return forwardMouseEvent(event, view)
    }

    private fun handleCapturedMouseEvent(event: MotionEvent): Boolean {
        if (!event.isFromSource(InputDevice.SOURCE_MOUSE)) return false
        val dx = event.getAxisValue(MotionEvent.AXIS_RELATIVE_X)
        val dy = event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y)
        if (dx != 0f || dy != 0f) {
            activatePhysicalMouse()
            movePhysicalPointer(dx, dy)
        }
        return forwardCapturedMouseButtonsAndWheel(event)
    }

    private fun forwardCapturedMouseButtonsAndWheel(source: MotionEvent): Boolean {
        if (targetDisplayId < 0) return false
        return runCatching {
            val event = MotionEvent.obtain(source)
            event.offsetLocation(cursorX - event.x, cursorY - event.y)
            check(inputDispatcher.send(event, targetDisplayId))
            event.recycle()
            true
        }.onFailure { Log.e(logTag, "captured mouse forwarding failed", it) }
            .getOrDefault(false)
    }

    private fun setOverlayFocusable(focusable: Boolean) {
        val frame = root ?: return
        val params = rootWindowParams ?: return
        val wanted = if (focusable) {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        if (wanted == params.flags) return
        params.flags = wanted
        runCatching { windowManager?.updateViewLayout(frame, params) }
            .onFailure { Log.e(logTag, "overlay focus update failed", it) }
    }

    private fun setDextopImeLocal(local: Boolean) {
        if (targetDisplayId < 0) return
        runCatching {
            val service = systemService("window", "android.view.IWindowManager")
            Class.forName("android.view.IWindowManager")
                .getMethod("setDisplayImePolicy", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                .invoke(service, targetDisplayId, if (local) 0 else 1)
        }.onFailure { Log.e(logTag, "IME policy update failed", it) }
    }

    private fun beginOverlayTextInput(field: EditText) {
        overlayTextInputActive = true
        surfaceView?.releasePointerCapture()
        setDextopImeLocal(false)
        setOverlayFocusable(true)
        field.postDelayed({
            field.requestFocus()
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
        }, 160)
    }

    private fun endOverlayTextInput() {
        if (!overlayTextInputActive) return
        overlayTextInputActive = false
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(root?.windowToken, 0)
        setDextopImeLocal(true)
        setOverlayFocusable(false)
    }

    private fun refreshPhysicalMouseState() {
        root?.post {
            val connected = hasPhysicalMouse()
            if (!connected && physicalMouseActive) {
                physicalMouseActive = false
                cursorView?.visibility = if (directTouch) View.GONE else View.VISIBLE
            }
        }
    }

    private fun refreshPhysicalInputState() {
        refreshPhysicalMouseState()
        if (!physicalInputRoutingSupported || !physicalExternalDisplayConnected) return
        val id = targetDisplayId
        if (!active || id < 0) return
        val display = getSystemService(DisplayManager::class.java).getDisplay(id) ?: return
        root?.post {
            val routed = runCatching {
                physicalInputRouter.refresh(display, routePhysicalMouseToDextop, routePhysicalKeyboardToDextop)
            }
                .onFailure { OperationLog.w(this, "InputRouting", "input routing refresh failed", it) }
                .getOrDefault(0)
            Log.i(logTag, "physical input routing refreshed count=$routed display=$id")
            root?.postDelayed({
                refreshActualRoutingState(display)
                menuPrimary?.let(::showMainMenu)
            }, 350)
        }
    }

    private fun hasPhysicalMouse(): Boolean = InputDevice.getDeviceIds().any { id ->
        InputDevice.getDevice(id)?.let { device ->
            device.sources and InputDevice.SOURCE_MOUSE == InputDevice.SOURCE_MOUSE
        } == true
    }

    private fun updateKeepAwake(enabled: Boolean) {
        val params = rootWindowParams ?: return
        params.flags = if (enabled) {
            params.flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        } else {
            params.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON.inv()
        }
        root?.let { windowManager?.updateViewLayout(it, params) }
    }

    private fun suspendForLockScreen() {
        if (suspendedForLockScreen) return
        suspendedConfig = Config(targetWidth, targetHeight, density, secureDisplay, showSystemDecorations)
        suspendedForLockScreen = true
        stopHostDisplayMonitor()
        setPhoneNavigationDisabled(false)
        releasePhoneRotation()
        runCatching { desktopModeConfigurator.restore() }
            .onFailure { Log.e(logTag, "lock-screen settings restoration failed", it) }
        removeWindow()
        targetDisplayId = -1
        Log.i(logTag, "session suspended; all display and overlay windows removed")
    }

    private fun resumeAfterUnlock() {
        if (!suspendedForLockScreen) return
        val previous = suspendedConfig ?: return
        val bounds = windowManager?.currentWindowMetrics?.bounds
        val config = if (shouldFollowHostDisplay() && bounds != null &&
            bounds.width() >= 480 && bounds.height() >= 480) {
            configForHostGeometry(
                previous,
                bounds.width(),
                bounds.height(),
                resources.configuration.densityDpi
            )
        } else previous
        // USER_PRESENT is emitted only after credential/biometric unlock, so
        // Dextop is never recreated over the lock screen or biometric UI.
        root?.postDelayed({ start(config) }, 250) ?: android.os.Handler(mainLooper)
            .postDelayed({ start(config) }, 250)
        Log.i(logTag, "unlock confirmed; session recreation scheduled")
    }

    private fun waitForConfirmedUnlock(attempt: Int = 0) {
        if (!suspendedForLockScreen) return
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (!keyguard.isDeviceLocked && !keyguard.isKeyguardLocked) {
            Log.i(logTag, "keyguard reports unlocked after attempt=$attempt")
            resumeAfterUnlock()
            return
        }
        if (attempt < 120) {
            android.os.Handler(mainLooper).postDelayed(
                { waitForConfirmedUnlock(attempt + 1) },
                250
            )
        } else {
            Log.w(logTag, "unlock wait timed out; leaving session suspended")
        }
    }

    private fun toggleMenu() {
        val panel = menu ?: return
        val frame = root ?: return
        panel.animate().cancel()
        menuScrim?.animate()?.cancel()
        if (panel.visibility != View.VISIBLE) {
            cancelDesktopTouchStream()
            frame.routeTouchesToSurface = false
            menuScrim?.apply {
                isClickable = true
                visibility = View.VISIBLE
                alpha = 0f
                animate().alpha(1f).setDuration(240).start()
            }
            panel.visibility = View.VISIBLE
            panel.alpha = 0f
            panel.scaleX = .94f
            panel.scaleY = .94f
            panel.post {
                if (targetWidth >= targetHeight) {
                    panel.translationX = -panel.width.toFloat()
                } else {
                    panel.translationY = -panel.height.toFloat()
                }
                panel.animate()
                    .translationX(0f)
                    .translationY(0f)
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(240)
                    .start()
            }
        } else {
            // Finish any gesture owned by the overlay before handing input back
            // to the desktop. The next finger must begin with a clean DOWN.
            cancelDesktopTouchStream()
            // The closing visuals may remain for 180 ms, but input must return
            // to Dextop immediately and must never hit the scrim's reopen click.
            frame.routeTouchesToSurface = true
            menuScrim?.isClickable = false
            menuScrim?.animate()?.alpha(0f)?.setDuration(180)?.start()
            val animation = if (targetWidth >= targetHeight) {
                panel.animate().translationX(-panel.width.toFloat())
            } else {
                panel.animate().translationY(-panel.height.toFloat())
            }
            animation.alpha(0f).scaleX(.94f).scaleY(.94f).setDuration(180).withEndAction {
                endOverlayTextInput()
                if (overlayLayoutEditing) {
                    overlayLayoutEditing = false
                    menuPrimary?.let(::showMainMenu)
                }
                setOverlayFocusable(false)
                panel.visibility = View.GONE
                menuScrim?.visibility = View.GONE
                // Keep direct routing enabled after the closing animation. The
                // transparent overlay hierarchy must never regain input while
                // it is hidden.
                frame.routeTouchesToSurface = true
                panel.translationX = 0f
                panel.translationY = 0f
                panel.alpha = 1f
                panel.scaleX = 1f
                panel.scaleY = 1f
                surfaceView?.requestFocus()
            }.start()
        }
    }

    private fun moveCursor(dx: Float, dy: Float) {
        cursorX = (cursorX + dx).coerceIn(0f, targetWidth - 1f)
        cursorY = (cursorY + dy).coerceIn(0f, targetHeight - 1f)
        cursorView?.update(cursorX / targetWidth, cursorY / targetHeight)
        if (dragHeld) injectTouch(MotionEvent.ACTION_MOVE, cursorX, cursorY)
    }

    /** Tracks the injection position without involving Dextop's touch cursor. */
    private fun movePhysicalPointer(dx: Float, dy: Float) {
        cursorX = (cursorX + dx).coerceIn(0f, targetWidth - 1f)
        cursorY = (cursorY + dy).coerceIn(0f, targetHeight - 1f)
    }

    private fun leftClick() {
        if (dragHeld) return
        injectTouch(MotionEvent.ACTION_DOWN, cursorX, cursorY)
        injectTouch(MotionEvent.ACTION_UP, cursorX, cursorY)
        if (!directTouch) cursorView?.pulse()
    }

    private fun rightClick() {
        if (dragHeld || targetDisplayId < 0) return
        runCatching {
            val properties = MotionEvent.PointerProperties().apply {
                id = 0
                toolType = MotionEvent.TOOL_TYPE_MOUSE
            }
            val coordinates = MotionEvent.PointerCoords().apply {
                x = cursorX
                y = cursorY
                pressure = 1f
                size = 1f
            }
            val now = SystemClock.uptimeMillis()
            listOf(
                MotionEvent.ACTION_DOWN to MotionEvent.BUTTON_SECONDARY,
                MotionEvent.ACTION_BUTTON_PRESS to MotionEvent.BUTTON_SECONDARY,
                MotionEvent.ACTION_BUTTON_RELEASE to 0,
                MotionEvent.ACTION_UP to 0
            ).forEach { (action, buttons) ->
                val event = MotionEvent.obtain(
                    now, SystemClock.uptimeMillis(), action, 1,
                    arrayOf(properties), arrayOf(coordinates), 0, buttons,
                    1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0
                )
                runCatching {
                    MotionEvent::class.java.getMethod("setActionButton", Int::class.javaPrimitiveType)
                        .invoke(event, MotionEvent.BUTTON_SECONDARY)
                }
                check(inputDispatcher.send(event, targetDisplayId))
                event.recycle()
            }
            cursorView?.pulse()
        }.onFailure { Log.e(logTag, "right click failed", it) }
    }

    private fun longPress() {
        if (dragHeld) return
        injectTouch(MotionEvent.ACTION_DOWN, cursorX, cursorY)
        root?.postDelayed({ injectTouch(MotionEvent.ACTION_UP, cursorX, cursorY) }, 600)
    }

    private fun toggleDrag() {
        dragHeld = !dragHeld
        injectTouch(if (dragHeld) MotionEvent.ACTION_DOWN else MotionEvent.ACTION_UP, cursorX, cursorY)
    }

    private fun changeOrientation() {
        val portrait = targetWidth >= targetHeight
        MainActivity.setDisplayOrientation(portrait)
        forcePhoneRotation(portrait)
        val config = Config(targetHeight, targetWidth, density, secureDisplay, showSystemDecorations)
        root?.postDelayed({ start(config) }, 350)
    }

    private fun injectKey(keyCode: Int, metaState: Int = 0) {
        if (targetDisplayId < 0) return
        runCatching {
            val now = SystemClock.uptimeMillis()
            listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP).forEach { action ->
                val event = KeyEvent(
                    now,
                    SystemClock.uptimeMillis(),
                    action,
                    keyCode,
                    0,
                    metaState
                )
                check(inputDispatcher.send(event, targetDisplayId))
            }
        }.onFailure { Log.e(logTag, "key injection failed", it) }
    }

    /** Forwards physical-keyboard input while preserving modifiers and repeat state. */
    private fun forwardKeyEvent(source: KeyEvent): Boolean {
        if (targetDisplayId < 0 || !routePhysicalKeyboardToDextop) return false
        return runCatching {
            val event = KeyEvent(source)
            inputDispatcher.send(event, targetDisplayId)
        }.onFailure { Log.e(logTag, "keyboard forwarding failed", it) }
            .getOrDefault(false)
    }

    /** Forwards physical mouse movement, buttons and wheel input to the desktop display. */
    private fun forwardMouseEvent(source: MotionEvent, view: View): Boolean {
        if (!routePhysicalMouseToDextop || targetDisplayId < 0 || view.width <= 0 || view.height <= 0) return false
        return runCatching {
            val event = MotionEvent.obtain(source)
            event.transform(Matrix().apply {
                setScale(targetWidth.toFloat() / view.width, targetHeight.toFloat() / view.height)
            })
            check(inputDispatcher.send(event, targetDisplayId))

            cursorX = event.x.coerceIn(0f, targetWidth - 1f)
            cursorY = event.y.coerceIn(0f, targetHeight - 1f)
            event.recycle()
            true
        }.onFailure { Log.e(logTag, "mouse forwarding failed", it) }
            .getOrDefault(false)
    }

    private fun injectTouch(action: Int, x: Float, y: Float) {
        if (targetDisplayId < 0) return
        runCatching {
            val properties = MotionEvent.PointerProperties().apply {
                id = 0
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
            val coordinates = MotionEvent.PointerCoords().apply {
                this.x = x
                this.y = y
                pressure = if (action == MotionEvent.ACTION_UP) 0f else 1f
                size = 1f
            }
            val now = SystemClock.uptimeMillis()
            if (action == MotionEvent.ACTION_DOWN) injectedDownTime = now
            val event = MotionEvent.obtain(
                injectedDownTime,
                now,
                action,
                1,
                arrayOf(properties),
                arrayOf(coordinates),
                0,
                0,
                1f,
                1f,
                0,
                0,
                InputDevice.SOURCE_TOUCHSCREEN,
                0
            )
            check(inputDispatcher.send(event, targetDisplayId))
            event.recycle()
        }.onFailure { Log.e(logTag, "input injection failed", it) }
    }

    private fun injectDirectTouch(source: MotionEvent, actionOverride: Int? = null) {
        if (targetDisplayId < 0) return
        val view = surfaceView ?: return
        if (view.width <= 0 || view.height <= 0) return
        runCatching {
            val action = actionOverride ?: source.action
            val actionMasked = action and MotionEvent.ACTION_MASK
            if (actionMasked == MotionEvent.ACTION_DOWN) {
                directInjectionDownTime = SystemClock.uptimeMillis()
                directSourceDownTime = source.downTime
            }
            if (directInjectionDownTime == 0L) {
                // Never send MOVE/UP without a synthetic DOWN identity.
                directInjectionDownTime = SystemClock.uptimeMillis()
                directSourceDownTime = source.downTime
            }
            // Keep the synthetic stream identity required by Samsung
            // InputManager, but preserve the source gesture's relative timing.
            // Replacing every eventTime with "now" collapses batched MOVE
            // events onto the same timestamp, so VelocityTracker sees almost
            // no release velocity and scrolling stops abruptly.
            val now = SystemClock.uptimeMillis()
            fun syntheticTime(sourceEventTime: Long): Long {
                val relative =
                    (sourceEventTime - directSourceDownTime).coerceAtLeast(0L)
                return (directInjectionDownTime + relative).coerceAtMost(now)
            }
            val scaleX = targetWidth.toFloat() / view.width
            val scaleY = targetHeight.toFloat() / view.height
            val properties = Array(source.pointerCount) { index ->
                MotionEvent.PointerProperties().apply {
                    source.getPointerProperties(index, this)
                    toolType = MotionEvent.TOOL_TYPE_FINGER
                }
            }
            fun scaledCoordinates(historyIndex: Int? = null) =
                Array(source.pointerCount) { index ->
                    MotionEvent.PointerCoords().apply {
                        if (historyIndex == null) {
                            source.getPointerCoords(index, this)
                        } else {
                            source.getHistoricalPointerCoords(index, historyIndex, this)
                        }
                        x *= scaleX
                        y *= scaleY
                    }
                }
            val hasMoveHistory =
                actionMasked == MotionEvent.ACTION_MOVE && source.historySize > 0
            val firstEventTime = if (hasMoveHistory) {
                syntheticTime(source.getHistoricalEventTime(0))
            } else {
                syntheticTime(source.eventTime)
            }
            val firstCoordinates =
                if (hasMoveHistory) scaledCoordinates(0) else scaledCoordinates()
            // deviceId=0 makes this a synthetic stream. Reusing the phone's
            // physical touchscreen device ID on another display causes Samsung
            // InputManager to reject subsequent one-finger events.
            val event = MotionEvent.obtain(
                directInjectionDownTime,
                firstEventTime,
                action,
                source.pointerCount,
                properties,
                firstCoordinates,
                source.metaState,
                source.buttonState,
                source.xPrecision * scaleX,
                source.yPrecision * scaleY,
                0,
                source.edgeFlags,
                InputDevice.SOURCE_TOUCHSCREEN,
                source.flags
            )
            if (hasMoveHistory) {
                for (historyIndex in 1 until source.historySize) {
                    event.addBatch(
                        syntheticTime(source.getHistoricalEventTime(historyIndex)),
                        scaledCoordinates(historyIndex),
                        source.metaState
                    )
                }
                event.addBatch(
                    syntheticTime(source.eventTime),
                    scaledCoordinates(),
                    source.metaState
                )
            }
            try {
                check(inputDispatcher.send(event, targetDisplayId)) {
                    "InputManager rejected multi-touch event action=${event.actionMasked}"
                }
                injectedDirectTouchActive = when (event.actionMasked) {
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> false
                    else -> true
                }
                lastInjectedDirectTouch?.recycle()
                lastInjectedDirectTouch = if (injectedDirectTouchActive) MotionEvent.obtain(event) else null
                if (!injectedDirectTouchActive) {
                    directInjectionDownTime = 0L
                    directSourceDownTime = 0L
                }
            } finally {
                event.recycle()
            }
        }.onFailure { error ->
            Log.e(logTag, "multi-touch injection failed", error)
        }
    }

    /**
     * Cancels the injected stream with exactly the pointer IDs and pointer count
     * accepted by the target display. A gesture intercepted when its third finger
     * arrives must not forward that new three-pointer shape as CANCEL: the target
     * has only seen the preceding one/two-pointer stream and rejects the mismatch.
     */
    private fun cancelInjectedDirectTouch() {
        val previous = lastInjectedDirectTouch ?: return
        if (targetDisplayId < 0) return
        val properties = Array(previous.pointerCount) { index ->
            MotionEvent.PointerProperties().also { previous.getPointerProperties(index, it) }
        }
        val coordinates = Array(previous.pointerCount) { index ->
            MotionEvent.PointerCoords().also { previous.getPointerCoords(index, it) }
        }
        val cancel = MotionEvent.obtain(
            previous.downTime,
            SystemClock.uptimeMillis(),
            MotionEvent.ACTION_CANCEL,
            previous.pointerCount,
            properties,
            coordinates,
            previous.metaState,
            previous.buttonState,
            previous.xPrecision,
            previous.yPrecision,
            0,
            previous.edgeFlags,
            InputDevice.SOURCE_TOUCHSCREEN,
            previous.flags
        )
        try {
            if (!inputDispatcher.send(cancel, targetDisplayId)) {
                Log.w(logTag, "InputManager rejected exact direct-touch cancel pointers=${cancel.pointerCount}")
            }
        } finally {
            cancel.recycle()
            previous.recycle()
            lastInjectedDirectTouch = null
            injectedDirectTouchActive = false
            directInjectionDownTime = 0L
            directSourceDownTime = 0L
        }
    }

    /** Terminates both local and injected gesture state at an overlay boundary. */
    private fun cancelDesktopTouchStream() {
        longPressRunnable?.let { root?.removeCallbacks(it) }
        longPressRunnable = null

        if (injectedDirectTouchActive) cancelInjectedDirectTouch()
        if (targetDisplayId >= 0 && (directTouchHeld || scrolling || dragHeld)) {
            injectTouch(MotionEvent.ACTION_CANCEL, cursorX, cursorY)
        }
        injectedDirectTouchActive = false
        directTouchHeld = false
        scrolling = false
        dragHeld = false
        moved = false
        twoFinger = false
        threeFinger = false
        maxPointers = 0
        longPressTriggered = false
        threeFingerEdgeSwipe = false
        edgeMenuTriggered = false
        edgeGestureLeadX = 0f
        edgeGestureLeadY = 0f
        injectedDownTime = 0L
        directInjectionDownTime = 0L
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        val view = surfaceView ?: return
        if (view.width <= 0 || view.height <= 0) return
        if (targetDisplayId >= 0 &&
            getSystemService(DisplayManager::class.java).getDisplay(targetDisplayId) != null) {
            reattachExistingDisplay(view.width, view.height)
        } else {
            createDisplay(holder.surface, view.width, view.height)
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        if (mirrorDisplayId < 0) {
            if (targetDisplayId >= 0 &&
                getSystemService(DisplayManager::class.java).getDisplay(targetDisplayId) != null) {
                reattachExistingDisplay(width, height)
            } else {
                createDisplay(holder.surface, width, height)
            }
        } else if (shouldFollowHostDisplay() && hostSizeDiffersFromTarget(width, height)) {
            scheduleHostDisplayReconfiguration("host surface resized", width, height)
        } else if (width != mirrorHostWidth || height != mirrorHostHeight) {
            scheduleMirrorRefresh("host surface changed", width, height)
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        releaseMirror()
    }

    /** Reconnects a recreated host Surface without removing the desktop display or its tasks. */
    private fun reattachExistingDisplay(width: Int, height: Int) {
        if (displayCreationInProgress || mirrorDisplayId >= 0 || targetDisplayId < 0) return
        displayCreationInProgress = true
        val configuredBackend = getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
            .getString("flutter.mirror_backend", "virtual_display") ?: "virtual_display"
        val strategyOverride = configuredBackend.takeUnless { it == "auto" }
        runCatching {
            attachMirror(width, height, strategyOverride)
            mirrorDisplayId = targetDisplayId
        }.onSuccess {
            displayCreationInProgress = false
            scheduleHostDisplayReconfiguration("host Surface recreated", width, height)
            scheduleTopologyReapplyAfterReconnect()
            OperationLog.i(
                this,
                "DisplayBackend",
                "reattached host Surface to display=$targetDisplayId host=${width}x$height; tasks retained"
            )
        }.onFailure { error ->
            displayCreationInProgress = false
            OperationLog.e(this, "DisplayBackend", "existing display reattach failed", error)
            Log.e(logTag, "existing display reattach failed", error)
        }
    }

    private fun createDisplay(surface: Surface, width: Int, height: Int) {
        if (mirrorDisplayId >= 0 || displayCreationInProgress) return
        displayCreationInProgress = true
        CapabilityProbe(this, privilegedAccess).run().forEach { (name, probe) ->
            OperationLog.i(this, "CapabilityProbe", "$name supported=${probe.supported} detail=${probe.detail}")
        }
        runCatching {
            val existing = displayBackend.currentDisplayIds()
            desktopModeConfigurator.applyForCurrentDevice()
            displayBackend.clearRequest()
            root?.postDelayed({
                displayBackend.requestDisplay(
                    targetWidth,
                    targetHeight,
                    density,
                    secureDisplay,
                    showSystemDecorations
                )
                waitForOverlay(existing, width, height, 0)
            }, 150)
        }.onFailure { error ->
            displayCreationInProgress = false
            OperationLog.e(this, "MirrorService", "display creation failed", error)
            Log.e(logTag, "display creation failed", error)
            completeStart(Result.failure(error))
            stop()
        }
    }

    private fun waitForOverlay(existing: Set<Int>, width: Int, height: Int, attempt: Int) {
        val display = displayBackend.findCreatedDisplay(existing)
        if (display == null) {
            if (attempt < 40) root?.postDelayed({ waitForOverlay(existing, width, height, attempt + 1) }, 100)
            else {
                val error = IllegalStateException("Overlay display creation timed out")
                displayCreationInProgress = false
                completeStart(Result.failure(error))
                Log.e(logTag, "overlay display creation timed out", error)
                stop()
            }
            return
        }
        runCatching {
            check(privilegedAccess.isAvailable()) {
                NativeStrings.text("nativeShizukuUnavailable")
            }
            targetDisplayId = display.displayId
            clearInheritedDisplayOverrides(targetDisplayId)
            configureDisplay()
            val configuredBackend = getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
                .getString("flutter.mirror_backend", "virtual_display") ?: "virtual_display"
            val strategyOverride = configuredBackend.takeUnless { it == "auto" }
            attachMirror(width, height, strategyOverride)
            mirrorDisplayId = targetDisplayId
            DisplayEnvironmentSettings(this).activateTopologyIfEnabled(mirrorDisplayId)
            displayCreationInProgress = false
            sessionJournal.running(targetDisplayId)
            val externalDisplays = externalDisplayDetector.snapshot()
            physicalExternalDisplayConnected = physicalInputRoutingSupported && externalDisplays.connected
            val routedInputCount = if (physicalExternalDisplayConnected) runCatching {
                physicalInputRouter.routeConnectedDevices(
                    display,
                    routePhysicalMouseToDextop,
                    routePhysicalKeyboardToDextop
                )
            }
                .onFailure { OperationLog.w(this, "InputRouting", "input routing unavailable", it) }
                .getOrDefault(0) else 0
            OperationLog.i(
                this,
                "DisplayRouting",
                "externalConnected=${externalDisplays.connected} externalIds=${externalDisplays.displayIds} routedInputs=$routedInputCount"
            )
            if (physicalExternalDisplayConnected && routePhysicalMouseToDextop) startRawMouseReader() else stopRawMouseReader()
            root?.postDelayed({
                refreshActualRoutingState(display)
                menuPrimary?.let(::showMainMenu)
            }, 350)
            menuPrimary?.let(::showMainMenu)
            cursorX = targetWidth / 2f
            cursorY = targetHeight / 2f
            cursorView?.update(.5f, .5f)
            check(launchHome()) { "The desktop HOME activity could not be launched" }
            pendingPausedWorkspace?.let { workspace ->
                pendingPausedWorkspace = null
                root?.postDelayed({
                    if (!active || targetDisplayId < 0) return@postDelayed
                    launchOverlayWorkspace(workspace, closeMenu = false)
                    getSharedPreferences("dextop_cleanup_state", MODE_PRIVATE).edit()
                        .remove("paused_workspace")
                        .apply()
                    OperationLog.i(
                        this,
                        "Workspace",
                        "restored paused workspace apps=${workspace.optJSONArray("apps")?.length() ?: 0}"
                    )
                }, 900)
            }
            // One UI may migrate the foreground phone task to a newly created
            // desktop display. Move the phone control activity back explicitly
            // before a separate DesktopActivity can be launched there.
            root?.postDelayed({ ensurePhoneControlOnDefaultDisplay() }, 500)
            completeStart(Result.success(mapOf(
                "displayId" to targetDisplayId,
                "width" to targetWidth,
                "height" to targetHeight,
                "density" to density,
                "decorations" to showSystemDecorations
            )))
            Log.i(logTag, "Dextop layer attached target=$targetDisplayId ${width}x$height")
        }.onFailure { error ->
            OperationLog.e(this, "MirrorService", "all mirror strategies failed", error)
            Log.e(logTag, "display mirror attachment failed; stopping safely", error)
            displayCreationInProgress = false
            completeStart(Result.failure(error))
            stop()
        }
    }

    /**
     * Samsung persists forced metrics by the overlay unique id (overlay:1),
     * not by its newly allocated display id. Without clearing them, a newly
     * created 2340x1080 overlay can inherit the previous 1080x2340 override.
     * This is used only for a new overlay; live fold resizing keeps its override.
     */
    private fun clearInheritedDisplayOverrides(displayId: Int) {
        val service = systemService("window", "android.view.IWindowManager")
        val type = Class.forName("android.view.IWindowManager")
        val userId = android.os.UserHandle::class.java
            .getMethod("myUserId").invoke(null) as Int
        runCatching {
            type.getMethod("clearForcedDisplaySize", Int::class.javaPrimitiveType)
                .invoke(service, displayId)
        }.onFailure { Log.w(logTag, "inherited display size clear failed display=$displayId", it) }
        runCatching {
            type.getMethod(
                "clearForcedDisplayDensityForUser",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            ).invoke(service, displayId, userId)
        }.onFailure { Log.w(logTag, "inherited display density clear failed display=$displayId", it) }
        OperationLog.i(this, "DisplayBackend", "cleared inherited metrics display=$displayId")
    }

    private fun configureDisplay() {
        val service = systemService("window", "android.view.IWindowManager")
        val type = Class.forName("android.view.IWindowManager")
        // Forced display width/height already define the requested orientation.
        // Rotating the target as well applies the change twice and restores the
        // old orientation, so the overlay display itself always stays at zero.
        val rotation = 0
        desktopModeConfigurator.configureDisplay(targetDisplayId)
        runCatching {
            type.getMethod(
                "setIgnoreOrientationRequest",
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType
            ).invoke(service, targetDisplayId, true)
        }.onFailure { Log.e(logTag, "orientation request lock failed", it) }
        runCatching {
            type.getMethod(
                "setFixedToUserRotation",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            ).invoke(service, targetDisplayId, 2)
        }.onFailure { Log.e(logTag, "fixed rotation failed", it) }
        runCatching {
            val method = type.methods.first {
                it.name == "freezeDisplayRotation" && it.parameterTypes.size >= 2
            }
            val args = method.parameterTypes.mapIndexed { index, parameter ->
                when {
                    index == 0 -> targetDisplayId
                    index == 1 -> rotation
                    parameter == String::class.java -> packageName
                    parameter == Boolean::class.javaPrimitiveType -> true
                    else -> null
                }
            }.toTypedArray()
            method.invoke(service, *args)
        }.onFailure { Log.e(logTag, "display rotation lock failed", it) }
        runCatching {
            type.getMethod("setShouldShowSystemDecors", Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
                .invoke(service, targetDisplayId, showSystemDecorations)
        }
        runCatching {
            type.getMethod("setDisplayImePolicy", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                .invoke(service, targetDisplayId, 0)
        }
        Log.i(logTag, "Dextop display configured display=$targetDisplayId rotation=$rotation")
    }

    private fun launchHome(): Boolean = runCatching {
            val intent = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val options = ActivityOptions.makeBasic().setLaunchDisplayId(targetDisplayId)
            startActivity(intent, options.toBundle())
            Log.i(logTag, "home launched display=$targetDisplayId")
        }.onFailure { Log.e(logTag, "home launch failed", it) }.isSuccess

    private fun ensurePhoneControlOnDefaultDisplay() {
        runCatching {
            val intent = Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            val options = ActivityOptions.makeBasic().setLaunchDisplayId(Display.DEFAULT_DISPLAY)
            startActivity(intent, options.toBundle())
            Log.i(logTag, "phone control activity pinned to default display")
        }.onFailure { Log.e(logTag, "unable to pin phone control activity", it) }
    }

    private fun releaseMirror() {
        mirrorRefreshGeneration += 1
        stopRawMouseReader()
        displayBackend.releaseLayer()
        mirrorDisplayId = -1
        mirrorHostWidth = 0
        mirrorHostHeight = 0
        displayCreationInProgress = false
    }

    private fun attachMirror(hostWidth: Int, hostHeight: Int, strategyOverride: String? = null) {
        val host = surfaceView ?: error(NativeStrings.text("nativeMirrorSurfaceUnavailable"))
        displayBackend.attach(
            targetDisplayId,
            host,
            hostWidth,
            hostHeight,
            targetWidth,
            targetHeight,
            density,
            strategyOverride
        )
        mirrorHostWidth = hostWidth
        mirrorHostHeight = hostHeight
    }

    /**
     * One UI can replace transition/SystemUI layers when recents, fold state, or
     * the host surface changes. Recreating only the mirror attachment keeps the
     * desktop tasks and physical-input routing alive while acquiring that new
     * layer tree.
     */
    private fun scheduleMirrorRefresh(
        reason: String,
        width: Int? = null,
        height: Int? = null,
        forceVirtualDisplay: Boolean = false
    ) {
        // A content-recording VirtualDisplay follows changes on its mirrored
        // display without being recreated. Releasing/recreating it in response
        // to DisplayListener or configuration callbacks races Samsung
        // WindowManager/SystemUI: the old display is removed while windows are
        // already being attached to the replacement. In particular this is
        // triggered when MainActivity is opened on the desktop display.
        // A destroyed Surface is handled separately by surfaceDestroyed /
        // surfaceCreated, and an actual profile change goes through start().
        if (displayBackend.activeStrategy == "virtual_display" && !forceVirtualDisplay) {
            OperationLog.i(
                this,
                "DisplayBackend",
                "mirror refresh skipped strategy=virtual_display reason=$reason"
            )
            return
        }
        val generation = ++mirrorRefreshGeneration
        root?.postDelayed({
            if (!active || targetDisplayId < 0 || generation != mirrorRefreshGeneration) {
                return@postDelayed
            }
            val host = surfaceView ?: return@postDelayed
            val nextWidth: Int = width?.takeIf { it > 0 } ?: host.width
            val nextHeight: Int = height?.takeIf { it > 0 } ?: host.height
            if (nextWidth <= 0 || nextHeight <= 0 || !host.holder.surface.isValid) {
                return@postDelayed
            }
            val configuredBackend = getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
                .getString("flutter.mirror_backend", "virtual_display") ?: "virtual_display"
            val strategyOverride = configuredBackend.takeUnless { it == "auto" }
            runCatching {
                attachMirror(nextWidth, nextHeight, strategyOverride)
                mirrorDisplayId = targetDisplayId
            }.onSuccess {
                OperationLog.i(
                    this,
                    "DisplayBackend",
                    "mirror refreshed reason=$reason host=${nextWidth}x$nextHeight " +
                        "content=${targetWidth}x$targetHeight/$density"
                )
            }.onFailure { error ->
                OperationLog.e(this, "DisplayBackend", "mirror refresh failed reason=$reason", error)
                Log.e(logTag, "mirror refresh failed reason=$reason", error)
            }
        }, 180)
    }

    private fun shouldFollowHostDisplay(): Boolean {
        val preferences = getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
        val selectedDeviceResolution =
            preferences.getString("flutter.selected_resolution_id", "device") == "device"
        return laptopModeActive || selectedDeviceResolution &&
            (isFoldableDevice() || preferences.getBoolean("flutter.foldable_auto", false) ||
                desktopEnvironment.autoResizeWithHostDisplay)
    }

    private fun isLaptopAutoDetectionEnabled(): Boolean {
        val preferences = getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
        // Foldables default to posture detection on first use. Once the user
        // changes the switch, the explicit preference always wins.
        return if (preferences.contains("flutter.foldable_laptop_mode")) {
            preferences.getBoolean("flutter.foldable_laptop_mode", false)
        } else {
            isFoldableDevice()
        }
    }

    private fun hostSizeDiffersFromTarget(width: Int, height: Int): Boolean {
        val hostLong = maxOf(width, height)
        val hostShort = minOf(width, height)
        val targetLong = maxOf(targetWidth, targetHeight)
        val targetShort = minOf(targetWidth, targetHeight)
        return hostLong != targetLong || hostShort != targetShort
    }

    /**
     * Fold state callbacks vary by vendor: some send Configuration, some only
     * resize the accessibility Surface, and others only change display metrics.
     * All three paths converge here and are debounced until the panel geometry
     * has settled. Custom profiles never enter this path.
     */
    private fun scheduleHostDisplayReconfiguration(
        reason: String,
        width: Int? = null,
        height: Int? = null,
        densityDpi: Int? = null
    ) {
        if (!shouldFollowHostDisplay()) return
        val generation = ++hostReconfigurationGeneration
        root?.postDelayed({
            if (!active || suspendedForLockScreen || generation != hostReconfigurationGeneration ||
                !shouldFollowHostDisplay()) return@postDelayed
            val bounds = windowManager?.currentWindowMetrics?.bounds
            val measuredWidth = width?.takeIf { it > 0 } ?: bounds?.width() ?: return@postDelayed
            val measuredHeight = height?.takeIf { it > 0 } ?: bounds?.height() ?: return@postDelayed
            if (measuredWidth < 480 || measuredHeight < 480) return@postDelayed

            val systemDensity = densityDpi ?: resources.configuration.densityDpi
            val next = configForHostGeometry(
                Config(targetWidth, targetHeight, density, secureDisplay, showSystemDecorations),
                measuredWidth,
                measuredHeight,
                systemDensity
            )
            if (next.width == targetWidth && next.height == targetHeight && next.density == density) {
                return@postDelayed
            }
            OperationLog.i(
                this,
                "DisplayBackend",
                "host reconfiguration reason=$reason " +
                    "${targetWidth}x$targetHeight/$density -> ${next.width}x${next.height}/${next.density}"
            )
            resizeActiveDisplay(next, reason)
        }, 320)
    }

    /** Changes the logical size without destroying the virtual display or its tasks. */
    private fun resizeActiveDisplay(next: Config, reason: String) {
        if (targetDisplayId < 0) return
        cancelDesktopTouchStream()
        val oldWidth = targetWidth.coerceAtLeast(1)
        val oldHeight = targetHeight.coerceAtLeast(1)
        val normalizedCursorX = cursorX / oldWidth
        val normalizedCursorY = cursorY / oldHeight
        val service = systemService("window", "android.view.IWindowManager")
        val type = Class.forName("android.view.IWindowManager")
        try {
            val userId = android.os.UserHandle::class.java
                .getMethod("myUserId").invoke(null) as Int
            type.getMethod(
                "setForcedDisplaySize",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            ).invoke(service, targetDisplayId, next.width, next.height)
            type.getMethod(
                "setForcedDisplayDensityForUser",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            ).invoke(service, targetDisplayId, next.density, userId)
        } catch (error: Throwable) {
            OperationLog.e(this, "DisplayBackend", "live resize failed reason=$reason", error)
            Log.e(logTag, "live display resize failed reason=$reason", error)
            throw error
        }
        targetWidth = next.width
        targetHeight = next.height
        density = next.density
        // A live size change can cross the natural-orientation boundary. WMS
        // also drops projected-display freeform policy on some Pixel builds,
        // so synchronize rotation and desktop policy after every resize.
        // Preserve the orientation selected from the Dextop gesture menu across
        // fold, cover-display and physical-display geometry changes.
        forcePhoneRotation(targetHeight > targetWidth)
        configureDisplay()
        cursorX = (normalizedCursorX * targetWidth).coerceIn(0f, targetWidth - 1f)
        cursorY = (normalizedCursorY * targetHeight).coerceIn(0f, targetHeight - 1f)
        cursorView?.update(cursorX / targetWidth, cursorY / targetHeight)
        surfaceView?.let { surface ->
            if (surface.width > 0 && surface.height > 0) {
                // Rotation-driven Surface replacement reattaches the mirror in
                // surfaceCreated(). Do not create extra recording displays here.
                scheduleMirrorRefresh("$reason; live logical resize", surface.width, surface.height)
            }
        }
        scheduleTopologyReapplyAfterReconnect()
        OperationLog.i(
            this,
            "DisplayBackend",
            "live resized display=$targetDisplayId to ${next.width}x${next.height}/${next.density}; tasks retained"
        )
    }

    private fun configForHostGeometry(
        base: Config,
        hostWidth: Int,
        hostHeight: Int,
        systemDensity: Int
    ): Config {
        val automaticDensity = (160 + systemDensity / 160f * 24)
            .toInt().coerceIn(160, 320)
        val portrait = base.height > base.width
        val hostLong = maxOf(hostWidth, hostHeight)
        val hostShort = minOf(hostWidth, hostHeight)
        return base.copy(
            // The panel size is dynamic, but its orientation is not. Only the
            // explicit portrait/landscape action may change this state.
            width = if (portrait) hostShort else hostLong,
            height = if (portrait) hostLong else hostShort,
            density = automaticDensity
        )
    }

    private fun startHostDisplayMonitor() {
        stopHostDisplayMonitor()
        observedHostWidth = 0
        observedHostHeight = 0
        observedHostDensity = 0
        hostDisplayMonitorHandler.post(hostDisplayMonitor)
    }

    private fun stopHostDisplayMonitor() {
        hostDisplayMonitorHandler.removeCallbacks(hostDisplayMonitor)
    }

    private fun startRawMouseReader() {
        if (!routePhysicalMouseToDextop) return
        stopRawMouseReader()
        val binder = rikka.shizuku.Shizuku.getBinder() ?: return
        runCatching {
            val command = "while true; do " +
                "dev=\$(getevent -pl 2>/dev/null | awk '" +
                "/^add device/{d=\$NF} /name:.*Mouse/{print d; exit}'); " +
                "if [ -n \"\$dev\" ]; then getevent -lt \"\$dev\"; fi; " +
                "sleep 1; done"
            val remote = IShizukuService.Stub.asInterface(binder)
                .newProcess(arrayOf("sh", "-c", command), null, null)
            mouseReaderProcess = remote
            mouseReaderRunning = true
            Thread {
                val reader = BufferedReader(InputStreamReader(
                    android.os.ParcelFileDescriptor.AutoCloseInputStream(remote.inputStream)
                ))
                var pendingX = 0f
                var pendingY = 0f
                var pendingWheel = 0f
                reader.useLines { lines ->
                    lines.takeWhile { mouseReaderRunning }.forEach { line ->
                        val fields = line.trim().split(Regex("\\s+"))
                        if (fields.size < 3) return@forEach
                        val type = fields[fields.size - 3]
                        val code = fields[fields.size - 2]
                        val raw = fields.last().toLongOrNull(16) ?: return@forEach
                        val signed = raw.toInt().toFloat()
                        when {
                            type == "0002" && code == "0000" -> pendingX += signed
                            type == "0002" && code == "0001" -> pendingY += signed
                            type == "0002" && code == "0008" -> pendingWheel += signed
                            type == "0001" && code in setOf("0110", "0111", "0112") -> {
                                val button = when (code) {
                                    "0110" -> MotionEvent.BUTTON_PRIMARY
                                    "0111" -> MotionEvent.BUTTON_SECONDARY
                                    else -> MotionEvent.BUTTON_TERTIARY
                                }
                                val pressed = raw != 0L
                                root?.post {
                                    activatePhysicalMouse()
                                    injectRoutedMouseButton(button, pressed)
                                }
                            }
                            type == "0000" && code == "0000" &&
                                (pendingX != 0f || pendingY != 0f || pendingWheel != 0f) -> {
                                val dx = pendingX
                                val dy = pendingY
                                val wheel = pendingWheel
                                pendingX = 0f
                                pendingY = 0f
                                pendingWheel = 0f
                                root?.post {
                                    activatePhysicalMouse()
                                    if (dx != 0f || dy != 0f) movePhysicalPointer(dx, dy)
                                    if (wheel != 0f) injectRoutedMouseScroll(wheel)
                                }
                            }
                        }
                    }
                }
            }.apply { name = "DextopMouseReader"; isDaemon = true }.start()
            Log.i(logTag, "raw mouse reader started")
        }.onFailure { Log.e(logTag, "raw mouse reader failed", it) }
    }

    private fun injectRoutedMouseButton(button: Int, pressed: Boolean) {
        if (targetDisplayId < 0) return
        val now = SystemClock.uptimeMillis()
        val action = if (pressed) MotionEvent.ACTION_BUTTON_PRESS else MotionEvent.ACTION_BUTTON_RELEASE
        val state = if (pressed) button else 0
        val properties = arrayOf(MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_MOUSE
        })
        val coordinates = arrayOf(MotionEvent.PointerCoords().apply {
            x = cursorX
            y = cursorY
        })
        val event = MotionEvent.obtain(
            now, now, action, 1, properties, coordinates,
            0, state, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0
        )
        runCatching {
            MotionEvent::class.java.getMethod("setActionButton", Int::class.javaPrimitiveType)
                .invoke(event, button)
        }
        try {
            check(inputDispatcher.send(event, targetDisplayId))
        } finally {
            event.recycle()
        }
    }

    private fun injectRoutedMouseScroll(wheel: Float) {
        if (targetDisplayId < 0) return
        val now = SystemClock.uptimeMillis()
        val properties = arrayOf(MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_MOUSE
        })
        val coordinates = arrayOf(MotionEvent.PointerCoords().apply {
            x = cursorX
            y = cursorY
            setAxisValue(MotionEvent.AXIS_VSCROLL, wheel)
        })
        val event = MotionEvent.obtain(
            now, now, MotionEvent.ACTION_SCROLL, 1, properties, coordinates,
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0
        )
        try {
            check(inputDispatcher.send(event, targetDisplayId))
        } finally {
            event.recycle()
        }
    }

    private fun stopRawMouseReader() {
        mouseReaderRunning = false
        mouseReaderProcess?.let { runCatching { it.destroy() } }
        mouseReaderProcess = null
    }

    private fun systemService(name: String, interfaceName: String): Any {
        return privilegedAccess.service(name, interfaceName)
    }

    private fun routeNotificationTask(packageName: String, generation: Int, attempt: Int) {
        root?.postDelayed({
            if (!active || targetDisplayId < 0 || generation != notificationRouteGeneration) return@postDelayed
            val query = privilegedAccess.execute("sh", "-c", "dumpsys activity activities")
            val taskId = if (query.succeeded) {
                NotificationTaskLocator.findSystemUiLaunchedTask(query.output, packageName, targetDisplayId)
            } else null
            if (taskId == null) {
                if (attempt < NOTIFICATION_ROUTE_RETRIES) {
                    routeNotificationTask(packageName, generation, attempt + 1)
                } else {
                    Log.w(logTag, "notification task not found package=$packageName")
                }
                return@postDelayed
            }
            val moved = privilegedAccess.execute(
                "am", "display", "move-stack", taskId.toString(), targetDisplayId.toString()
            )
            if (moved.succeeded) {
                OperationLog.i(this, "NotificationRouting", "moved package=$packageName task=$taskId display=$targetDisplayId")
                Log.i(logTag, "notification task moved package=$packageName task=$taskId display=$targetDisplayId")
            } else if (attempt < NOTIFICATION_ROUTE_RETRIES) {
                routeNotificationTask(packageName, generation, attempt + 1)
            } else {
                Log.e(logTag, "notification task move failed package=$packageName task=$taskId error=${moved.error}")
            }
        }, NOTIFICATION_ROUTE_RETRY_DELAY_MS)
    }

    private fun setPhoneNavigationDisabled(disabled: Boolean) {
        val generation = ++navigationRestoreGeneration
        applyPhoneNavigationDisabled(disabled)
        if (disabled) return

        // SystemUI can recreate its navigation bar after our overlay is
        // removed. Re-submit the zero flags after those asynchronous passes.
        listOf(120L, 450L, 1_200L).forEach { delay ->
            hostDisplayMonitorHandler.postDelayed({
                if (generation != navigationRestoreGeneration || active && !suspendedForLockScreen) {
                    return@postDelayed
                }
                applyPhoneNavigationDisabled(false)
            }, delay)
        }
    }

    private fun applyPhoneNavigationDisabled(disabled: Boolean) {
        runCatching {
            val service = systemService("statusbar", STATUS_BAR_INTERFACE)
            val type = Class.forName(STATUS_BAR_INTERFACE)
            val flags = if (disabled) PHONE_NAVIGATION_DISABLE_FLAGS else 0
            val method = type.methods.firstOrNull {
                it.name == "disable" && it.parameterTypes.size == 4
            } ?: type.methods.firstOrNull {
                it.name == "disable" && it.parameterTypes.size == 3
            } ?: type.methods.firstOrNull {
                it.name == "disableForUser" && it.parameterTypes.size == 5
            } ?: error("No compatible StatusBar disable operation")
            val integerCount = method.parameterTypes.count { it == Int::class.javaPrimitiveType }
            var integerIndex = 0
            val args: Array<Any?> = method.parameterTypes.map { parameter ->
                when {
                    parameter == Int::class.javaPrimitiveType -> {
                        val value = when {
                            method.name == "disableForUser" && integerIndex == integerCount - 1 ->
                                android.os.Process.myUid() / 100_000
                            integerCount >= 2 && integerIndex == 0 -> Display.DEFAULT_DISPLAY
                            else -> flags
                        }
                        integerIndex += 1
                        value
                    }
                    android.os.IBinder::class.java.isAssignableFrom(parameter) -> navigationToken
                    parameter == String::class.java -> packageName
                    else -> null
                }
            }.toTypedArray()
            method.invoke(service, *args)
            if (!disabled) {
                // This also clears stale shell-owned flags left by an interrupted
                // recovery on vendor SystemUI implementations.
                runCatching {
                    privilegedAccess.execute("cmd", "statusbar", "send-disable-flag", "none")
                }
            }
            OperationLog.i(this, "PhoneNavigation", "disabled=$disabled method=${method.name}/${method.parameterTypes.size}")
            Log.i(logTag, "phone navigation disabled=$disabled")
        }.onFailure { error ->
            OperationLog.e(this, "PhoneNavigation", "state update failed disabled=$disabled", error)
            Log.e(logTag, "phone navigation state failed disabled=$disabled", error)
        }
    }

    private fun forcePhoneRotation(portrait: Boolean) {
        runCatching {
            phoneRotationController.force(portrait)
        }.onFailure { Log.e(logTag, "phone rotation lock failed", it) }
    }

    private fun releasePhoneRotation(clearSnapshot: Boolean = false) {
        runCatching {
            phoneRotationController.restore(clearSnapshot)
        }.onFailure { Log.e(logTag, "phone rotation unlock failed", it) }
    }

    private fun stop() {
        if (stopping) return
        stopping = true
        val wasActive = active
        stopHostDisplayMonitor()
        suspendedForLockScreen = false
        suspendedConfig = null
        if (dragHeld) toggleDrag()
        runCatching { physicalInputRouter.restore() }
            .onFailure { Log.e(logTag, "physical input restoration failed", it) }
        setPhoneNavigationDisabled(false)
        releasePhoneRotation(clearSnapshot = true)
        runCatching { DisplayEnvironmentSettings(this).restoreTopology() }
            .onFailure { Log.e(logTag, "topology restoration failed", it) }
        runCatching { displayBackend.clearRequest() }
        removeWindow()
        targetDisplayId = -1
        active = false
        MainActivity.restoreOrientation()
        val keepInternal120Hz = internalRefreshRateController.isEnabledAndSupported() &&
            !externalDisplayDetector.snapshot().connected
        if (keepInternal120Hz) {
            runCatching { internalRefreshRateController.keepCurrentValue() }
                .onFailure { Log.e(logTag, "unable to preserve 120 Hz after disconnect", it) }
        }
        val restored = runCatching {
            desktopModeConfigurator.restore()
            sessionJournal.restoreSystemSettings()
        }.onFailure { Log.e(logTag, "settings restoration failed", it) }.isSuccess
        if (restored) sessionJournal.clear()
        if (restored) {
            getSharedPreferences("dextop_cleanup_state", MODE_PRIVATE).edit()
                .putBoolean("cleanup_pending", false)
                .putBoolean("paused_by_user", false)
                .remove("paused_workspace")
                .putLong("verified_at", System.currentTimeMillis())
                .commit()
        }
        if (wasActive) OperationLog.finishSession(this, restored)
        if (wasActive) {
            // Detach the input-filtering accessibility service so Android's
            // back gesture and Circle to Search regain their normal handlers.
            disableDextopAccessibilityService()
            disableSelf()
        }
        completeStart(Result.failure(IllegalStateException("Dextop was stopped before startup completed")))
        Log.i(logTag, "stopped")
    }

    private fun disableDextopAccessibilityService() {
        runCatching {
            val own = ComponentName(this, MirrorService::class.java)
            val remaining = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty().split(':').filter { raw ->
                raw.isNotBlank() && ComponentName.unflattenFromString(raw)?.let { component ->
                    component != own
                } != false
            }
            check(Settings.Secure.putString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                remaining.joinToString(":")
            )) { "Unable to detach the Dextop accessibility service" }
            if (remaining.isEmpty()) {
                Settings.Secure.putInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
            }
            Log.i(logTag, "Dextop accessibility service detached; remaining=${remaining.size}")
        }.onFailure { Log.e(logTag, "accessibility service detachment failed", it) }
    }

    private fun removeWindow() {
        releaseMirror()
        detachHostWindow()
    }

    private fun detachHostWindow() {
        stopLaptopHardwareKeyboard()
        // Prevent surfaceDestroyed() from releasing the mirrored display before
        // WindowManager has removed this host and its gesture registrations.
        surfaceView?.holder?.removeCallback(this)
        cursorView?.let { runCatching { windowManager?.removeView(it) } }
        root?.let { runCatching { windowManager?.removeView(it) } }
        root = null
        rootWindowParams = null
        surfaceView = null
        laptopContent = null
        cursorView = null
        menu = null
        menuScrim = null
        performanceHud = null
        laptopDeck = null
        laptopDeckContent = null
        demoInfoView = null
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private class LaptopKeyTextView(
        context: Context,
        private val showHomePosition: Boolean,
        markColor: Int
    ) : TextView(context) {
        private var secondaryLabel: String? = null
        private val markPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = markColor
            strokeWidth = context.resources.displayMetrics.density * 1.6f
            strokeCap = Paint.Cap.ROUND
        }
        private val secondaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = context.resources.displayMetrics.scaledDensity * 6.5f
        }

        fun setSecondaryLabel(label: String?, color: Int) {
            secondaryLabel = label
            secondaryPaint.color = color
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val density = resources.displayMetrics.density
            val currentLayout = layout
            if (currentLayout != null && currentLayout.lineCount > 0) {
                val layoutTop = (height - currentLayout.height) / 2f
                val primaryBaseline = layoutTop + currentLayout.getLineBaseline(0)
                if (showHomePosition) {
                    // Keep the tactile home-position mark below the key legend.
                    // When Ctrl shortcut hints are visible, place it below the
                    // secondary label as well so the two never overlap.
                    val y = primaryBaseline + if (secondaryLabel == null) {
                        6f * density
                    } else {
                        13f * density
                    }
                    val halfWidth = 6f * density
                    canvas.drawLine(width / 2f - halfWidth, y, width / 2f + halfWidth, y, markPaint)
                }
                secondaryLabel?.let { label ->
                    secondaryPaint.typeface = typeface
                    canvas.drawText(label, width / 2f, primaryBaseline + 8f * density, secondaryPaint)
                }
            }
        }
    }

    private class KeyboardGlyphDrawable(
        private val kind: Int,
        private val glyphColor: Int
    ) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = glyphColor
            style = Paint.Style.FILL
            strokeCap = Paint.Cap.ROUND
        }

        override fun draw(canvas: Canvas) {
            paint.color = glyphColor
            val b = bounds
            val scale = minOf(b.width() / 24f, b.height() / 20f)
            val offsetX = (b.width() - 24f * scale) / 2f
            val offsetY = (b.height() - 20f * scale) / 2f
            canvas.save()
            canvas.translate(b.left + offsetX, b.top + offsetY)
            canvas.scale(scale, scale)
            paint.strokeWidth = 1.5f
            when (kind) {
                ANDROID -> {
                    canvas.drawLine(6.5f, 4f, 4.5f, 1.2f, paint)
                    canvas.drawLine(17.5f, 4f, 19.5f, 1.2f, paint)
                    canvas.drawRoundRect(RectF(4f, 4f, 20f, 10f), 5f, 5f, paint)
                    canvas.drawRoundRect(RectF(4f, 8f, 20f, 17f), 1.5f, 1.5f, paint)
                    canvas.drawRoundRect(RectF(1.5f, 8f, 3.5f, 16f), 1f, 1f, paint)
                    canvas.drawRoundRect(RectF(20.5f, 8f, 22.5f, 16f), 1f, 1f, paint)
                    canvas.drawRoundRect(RectF(7f, 15f, 10f, 20f), 1f, 1f, paint)
                    canvas.drawRoundRect(RectF(14f, 15f, 17f, 20f), 1f, 1f, paint)
                }
                BACK -> {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 2.2f
                    canvas.drawLine(18f, 10f, 6f, 10f, paint)
                    canvas.drawLine(6f, 10f, 11f, 5f, paint)
                    canvas.drawLine(6f, 10f, 11f, 15f, paint)
                    paint.style = Paint.Style.FILL
                }
                PALETTE -> {
                    canvas.drawOval(RectF(2f, 2f, 22f, 18f), paint)
                    paint.color = Color.rgb(35, 33, 39)
                    canvas.drawCircle(17.5f, 14f, 3.5f, paint)
                    canvas.drawCircle(7f, 7f, 1.5f, paint)
                    canvas.drawCircle(12f, 5f, 1.5f, paint)
                    canvas.drawCircle(17f, 7.5f, 1.5f, paint)
                }
                CHECK -> {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 2.4f
                    canvas.drawCircle(12f, 10f, 8f, paint)
                    canvas.drawLine(7.5f, 10f, 10.5f, 13f, paint)
                    canvas.drawLine(10.5f, 13f, 16.5f, 7f, paint)
                    paint.style = Paint.Style.FILL
                }
            }
            canvas.restore()
        }

        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(filter: android.graphics.ColorFilter?) {
            paint.colorFilter = filter
        }
        @Suppress("DEPRECATION")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

        companion object {
            const val ANDROID = 1
            const val BACK = 2
            const val PALETTE = 3
            const val CHECK = 4
        }
    }

    private class TouchRoutingFrame(context: Context) : FrameLayout(context) {
        var routeTouchesToSurface = false

        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            if (routeTouchesToSurface) {
                val surface = getChildAt(0)
                if (surface != null) {
                    val handled = surface.dispatchTouchEvent(event)
                    return handled
                }
            }
            return super.dispatchTouchEvent(event)
        }
    }

    private class LevelIconView(context: Context, private val volume: Boolean) : View(context) {
        private val density = resources.displayMetrics.density
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(62, 62, 66)
            style = Paint.Style.STROKE
            strokeWidth = 1.9f * density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        var level = 0f
            set(value) {
                field = value.coerceIn(0f, 1f)
                invalidate()
            }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (volume) drawVolume(canvas) else drawSun(canvas)
        }

        private fun drawSun(canvas: Canvas) {
            val cx = width / 2f
            val cy = height / 2f
            val base = minOf(width, height).toFloat()
            val coreRadius = base * (.12f + level * .045f)
            paint.style = Paint.Style.FILL
            canvas.drawCircle(cx, cy, coreRadius, paint)
            paint.style = Paint.Style.STROKE
            val rayStart = base * .25f
            val rayLength = base * (.055f + .16f * level)
            paint.alpha = (125 + 130 * level).toInt()
            for (index in 0 until 8) {
                val angle = Math.PI * index / 4.0
                val cos = kotlin.math.cos(angle).toFloat()
                val sin = kotlin.math.sin(angle).toFloat()
                canvas.drawLine(
                    cx + cos * rayStart,
                    cy + sin * rayStart,
                    cx + cos * (rayStart + rayLength),
                    cy + sin * (rayStart + rayLength),
                    paint
                )
            }
            paint.alpha = 255
        }

        private fun drawVolume(canvas: Canvas) {
            val base = minOf(width, height).toFloat()
            // Keep the combined speaker + waves visually centered: the speaker
            // starts centered while muted, then shifts left as waves expand.
            val cx = width * (.50f - .22f * level)
            val cy = height / 2f
            val speaker = Path().apply {
                moveTo(cx - base * .24f, cy - base * .11f)
                lineTo(cx - base * .10f, cy - base * .11f)
                lineTo(cx + base * .08f, cy - base * .27f)
                lineTo(cx + base * .08f, cy + base * .27f)
                lineTo(cx - base * .10f, cy + base * .11f)
                lineTo(cx - base * .24f, cy + base * .11f)
                close()
            }
            paint.style = Paint.Style.FILL
            paint.alpha = 255
            canvas.drawPath(speaker, paint)
            paint.style = Paint.Style.STROKE
            val thresholds = floatArrayOf(.02f, .34f, .67f)
            val waveCount = thresholds.count { level >= it }
            for (index in 0 until waveCount) {
                val threshold = thresholds[index]
                val local = ((level - threshold) / (1f - threshold)).coerceIn(0f, 1f)
                val baseRadius = when (index) {
                    0 -> .17f
                    1 -> .29f
                    else -> .41f
                }
                val radius = base * baseRadius * (.78f + .22f * local)
                paint.alpha = (105 + 150 * local).toInt()
                val rect = android.graphics.RectF(
                    cx - radius * .15f,
                    cy - radius,
                    cx + radius * 1.85f,
                    cy + radius
                )
                canvas.drawArc(rect, -47f, 94f, false, paint)
            }
            paint.alpha = 255
        }
    }

    private class CursorView(context: Context) : View(context) {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        private var normalizedX = .5f
        private var normalizedY = .5f
        private var radius = 13f
        var contentHeightFraction = 1f
        fun update(x: Float, y: Float) {
            normalizedX = x
            normalizedY = y
            invalidate()
        }

        fun pulse() {
            radius = 19f
            invalidate()
            postDelayed({ radius = 13f; invalidate() }, 100)
        }

        override fun onDraw(canvas: Canvas) {
            val x = normalizedX * width
            val y = normalizedY * height * contentHeightFraction
            canvas.drawCircle(x, y, radius, fill)
            canvas.drawCircle(x, y, radius, stroke)
        }
    }
}
