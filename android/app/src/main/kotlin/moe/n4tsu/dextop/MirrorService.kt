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
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.Paint
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.input.InputManager
import android.os.Binder
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.transition.ChangeBounds
import android.transition.TransitionManager
import android.util.Log
import android.view.Gravity
import android.view.DragEvent
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.view.accessibility.AccessibilityEvent
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.animation.ValueAnimator
import android.animation.LayoutTransition
import android.widget.GridLayout
import java.io.BufferedReader
import java.io.InputStreamReader
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
        private var instance: MirrorService? = null
        private var pending: Config? = null
        private var pendingStartResult: ((Result<Map<String, Any>>) -> Unit)? = null
        private var pendingDemo = false
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
    private val workspaceLayoutEngine by lazy {
        WorkspaceLayoutEngine(this, desktopEnvironment, logTag)
    }
    private val resolutionRepository by lazy { ResolutionRepository(this, logTag) }
    private val inputDispatcher by lazy { InputDispatcher(privilegedAccess) }
    private val sessionJournal by lazy { SessionJournal(this) }
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
    private var overlayLayoutEditing = false
    private val launchedAppBounds = linkedMapOf<String, Rect>()
    private var workspaceSaveError: String? = null
    private var pausedForAndroid = false
    private var menuScrim: View? = null
    private var demoMode = false
    private var demoInfoView: TextView? = null
    private var performanceHud: PerformanceHud? = null
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
    private var experimentalMultiTouch = false
    private var threeFingerEdgeSwipe = false
    private var edgeMenuTriggered = false
    private var edgeGestureOriginX = 0f
    private var physicalMouseActive = false
    private var mouseReaderProcess: moe.shizuku.server.IRemoteProcess? = null
    @Volatile private var mouseReaderRunning = false
    private var inputManager: InputManager? = null
    private val inputDeviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = refreshPhysicalMouseState()
        override fun onInputDeviceRemoved(deviceId: Int) = refreshPhysicalMouseState()
        override fun onInputDeviceChanged(deviceId: Int) = refreshPhysicalMouseState()
    }
    private val navigationToken = Binder()
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

    override fun onServiceConnected() {
        HiddenApiBypass.addHiddenApiExemptions("")
        directTouch = getSharedPreferences(PREFS, MODE_PRIVATE)
            .getBoolean(KEY_DIRECT_TOUCH, false)
        experimentalMultiTouch = getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
            .getBoolean("flutter.experimental_multitouch", false)
        instance = this
        windowManager = getSystemService(WindowManager::class.java)
        inputManager = getSystemService(InputManager::class.java).also {
            it.registerInputDeviceListener(inputDeviceListener, null)
        }
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
                runCatching { sessionJournal.restoreSystemSettings() }
                    .onSuccess { Log.i(logTag, "restored settings from interrupted Dextop session") }
                    .onFailure { Log.e(logTag, "interrupted session restoration failed", it) }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!active || suspendedForLockScreen) return
        val preferences = getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
        if (!preferences.getBoolean("flutter.foldable_auto", false)) return
        val bounds = windowManager?.currentWindowMetrics?.bounds ?: return
        val measuredWidth = maxOf(bounds.width(), bounds.height())
        val measuredHeight = minOf(bounds.width(), bounds.height())
        if (measuredWidth < 480 || measuredHeight < 480) return
        val scale = newConfig.densityDpi / 160f
        val measuredDensity = (160 + scale * 24).toInt().coerceIn(160, 320)
        val next = Config(measuredWidth, measuredHeight, measuredDensity, secureDisplay, showSystemDecorations)
        if (next.width != targetWidth || next.height != targetHeight || next.density != density) {
            root?.postDelayed({ start(next) }, 250)
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean = forwardKeyEvent(event)

    override fun onMotionEvent(event: MotionEvent) {
        if (!active || !event.isFromSource(InputDevice.SOURCE_MOUSE)) return
        activatePhysicalMouse()
        val relativeX = event.getAxisValue(MotionEvent.AXIS_RELATIVE_X)
        val relativeY = event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y)
        when {
            relativeX != 0f || relativeY != 0f -> moveCursor(relativeX, relativeY)
            event.actionMasked == MotionEvent.ACTION_HOVER_MOVE ||
                event.actionMasked == MotionEvent.ACTION_MOVE -> {
                val view = surfaceView ?: return
                if (view.width > 0 && view.height > 0) {
                    cursorX = (event.x / view.width * targetWidth).coerceIn(0f, targetWidth - 1f)
                    cursorY = (event.y / view.height * targetHeight).coerceIn(0f, targetHeight - 1f)
                    cursorView?.update(cursorX / targetWidth, cursorY / targetHeight)
                }
            }
        }
        Log.v(logTag, "mouse motion action=${event.actionMasked} rel=$relativeX,$relativeY pos=$cursorX,$cursorY")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (!pausedForAndroid) stop()
        if (screenReceiverRegistered) unregisterReceiver(screenReceiver)
        inputManager?.unregisterInputDeviceListener(inputDeviceListener)
        inputManager = null
        screenReceiverRegistered = false
        instance = null
        return super.onUnbind(intent)
    }

    private fun start(config: Config) {
        OperationLog.i(this, "MirrorService", "start requested environment=${desktopEnvironment.id} config=$config")
        if (!privilegedAccess.isAvailable()) {
            pending = null
            active = false
            val error = IllegalStateException(NativeStrings.text("nativeShizukuUnavailable"))
            completeStart(Result.failure(error))
            Log.e(logTag, "start rejected: Shizuku binder is unavailable", error)
            return
        }
        pausedForAndroid = false
        getSharedPreferences("dextop_cleanup_state", MODE_PRIVATE).edit()
            .putBoolean("cleanup_pending", true)
            .putBoolean("paused_by_user", false)
            .putLong("started_at", System.currentTimeMillis())
            .commit()
        suspendedForLockScreen = false
        suspendedConfig = null
        experimentalMultiTouch = getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
            .getBoolean("flutter.experimental_multitouch", false)
        sessionJournal.preparing(config.width, config.height, config.density, config.decorations)
        targetDisplayId = -1
        targetWidth = config.width
        targetHeight = config.height
        density = config.density
        secureDisplay = config.secure
        showSystemDecorations = config.decorations
        forcePhoneRotation(targetHeight > targetWidth)
        cursorX = targetWidth / 2f
        cursorY = targetHeight / 2f
        removeWindow()
        addWindow()
        pending = null
        active = true
        setPhoneNavigationDisabled(true)
        Log.i(logTag, "start direct ${targetWidth}x$targetHeight/$density")
    }

    private fun addWindow() {
        val frame = TouchRoutingFrame(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            // Keep every pointer in one touch stream. Splitting the stream lets
            // the mirrored surface consume fingers before the edge recognizer.
            isMotionEventSplittingEnabled = false
        }
        val surface = SurfaceView(this).apply {
            holder.addCallback(this@MirrorService)
            isFocusable = true
            isFocusableInTouchMode = true
            setOnTouchListener { _, event ->
                if (event.isFromSource(InputDevice.SOURCE_MOUSE)) {
                    activatePhysicalMouse()
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
        cursor.visibility = if (directTouch || hasPhysicalMouse()) View.GONE else View.VISIBLE
        val controls = buildMenu()
        val scrim = View(this).apply {
            setBackgroundColor(Color.argb(105, 0, 0, 0))
            visibility = View.GONE
            setOnClickListener { toggleMenu() }
        }
        frame.addView(surface, FrameLayout.LayoutParams(-1, -1))
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
        cursorView = cursor
        menu = controls
        menuScrim = scrim
        performanceHud = hud
        windowManager?.addView(frame, params)
        if (experimentalMultiTouch) {
            frame.post {
                val exclusionWidth = dp(120).coerceAtMost(frame.width / 3)
                val exclusion = listOf(Rect(0, 0, exclusionWidth, frame.height))
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

    private fun menuLayoutParams(): FrameLayout.LayoutParams {
        return if (targetWidth >= targetHeight) {
            FrameLayout.LayoutParams(dp(if (workspaceExpanded) 680 else 340), -2, Gravity.START).apply {
                topMargin = dp(18)
                bottomMargin = dp(18)
                leftMargin = dp(18)
            }
        } else {
            FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
                leftMargin = dp(12)
                rightMargin = dp(12)
                bottomMargin = dp(12)
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
        targetWidth = resources.displayMetrics.widthPixels
        targetHeight = resources.displayMetrics.heightPixels
        density = resources.displayMetrics.densityDpi
        val frame = TouchRoutingFrame(this).apply { setBackgroundColor(Color.TRANSPARENT) }
        val scrim = View(this).apply {
            setBackgroundColor(Color.argb(105, 0, 0, 0))
            setOnClickListener { hideDemoWindow() }
        }
        val controls = buildMenu().apply { visibility = View.VISIBLE }
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
        frame.addView(scrim, FrameLayout.LayoutParams(-1, -1))
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
                topMargin = dp(12)
            }
        })
        frame.setOnApplyWindowInsetsListener { _, insets ->
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
                layout.topMargin = dp(12) + safe.top
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
        controls.alpha = 0f
        controls.scaleX = .94f
        controls.scaleY = .94f
        controls.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(240).start()
    }

    private fun hideDemoWindow() {
        if (!demoMode) return
        removeWindow()
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
        val apps = JSONArray()
        val bounds = JSONObject()
        val currentTasks = currentDisplayTasks()
        currentTasks.forEach { (packageName, rect) ->
            apps.put(packageName)
            bounds.put(packageName, JSONArray(listOf(rect.left, rect.top, rect.right, rect.bottom)))
        }
        if (apps.length() == 0) {
            windows.orEmpty().forEach { window ->
                if (Build.VERSION.SDK_INT >= 30 && window.displayId != targetDisplayId) return@forEach
                val packageName = window.root?.packageName?.toString() ?: return@forEach
                if (packageName == packageName() || packageName == "com.android.systemui") return@forEach
                val rect = Rect()
                window.getBoundsInScreen(rect)
                if (rect.width() < dp(80) || rect.height() < dp(80) || bounds.has(packageName)) return@forEach
                apps.put(packageName)
                bounds.put(packageName, JSONArray(listOf(rect.left, rect.top, rect.right, rect.bottom)))
            }
        }
        if (apps.length() == 0) {
            launchedAppBounds.forEach { (packageName, rect) ->
                apps.put(packageName)
                bounds.put(packageName, JSONArray(listOf(rect.left, rect.top, rect.right, rect.bottom)))
            }
        }
        if (apps.length() == 0) {
            Log.w(logTag, "no app windows available to save")
            return NativeStrings.text("nativeFailedToSaveUnableToRetrieveRunning")
        }
        val all = workspaceJson()
        all.put(JSONObject().apply {
            put("id", System.currentTimeMillis().toString())
            put("name", "${NativeStrings.text("nativeWorkSpace")} ${all.length() + 1}")
            put("apps", apps)
            put("positions", JSONObject())
            put("bounds", bounds)
            put("layout", "captured")
        })
        val saved = getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE).edit()
            .putString("flutter.workspaces", all.toString()).commit()
        return if (saved) null else NativeStrings.text("nativeFailedToSaveFailedToWriteTo")
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
                if (candidate != applicationContext.packageName &&
                    candidate != "com.android.systemui" &&
                    !line.contains("type=home")
                ) pendingPackage = candidate
            }
            val match = boundsPattern.find(line) ?: return@forEach
            val packageName = pendingPackage ?: return@forEach
            val values = match.groupValues.drop(1).mapNotNull(String::toIntOrNull)
            if (values.size == 4 && values[2] > values[0] && values[3] > values[1]) {
                found.putIfAbsent(packageName, Rect(values[0], values[1], values[2], values[3]))
                pendingPackage = null
            }
        }
        Log.i(logTag, "current task query found ${found.size} apps on display=$targetDisplayId packages=${found.keys}")
        return found
    }

    private fun launchOverlayWorkspace(workspace: JSONObject) {
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
        toggleMenu()
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
        pausedForAndroid = true
        sessionJournal.paused()
        getSharedPreferences("dextop_cleanup_state", MODE_PRIVATE).edit()
            .putBoolean("cleanup_pending", true)
            .putBoolean("paused_by_user", true)
            .putLong("paused_at", System.currentTimeMillis())
            .commit()
        overlayLayoutEditing = false
        suspendedForLockScreen = false
        suspendedConfig = null
        setPhoneNavigationDisabled(false)
        releasePhoneRotation()
        removeWindow()
        targetDisplayId = -1
        active = false
        desktopModeConfigurator.restore()
        MainActivity.restoreOrientation()
        runCatching {
            val home = Intent(this, MainActivity::class.java)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK
                )
            val options = ActivityOptions.makeBasic().setLaunchDisplayId(0)
            startActivity(home, options.toBundle())
        }.onFailure { Log.e(logTag, "unable to return to Dextop home", it) }
        // Keep SessionJournal intact: the app shows its explicit recovery card.
        android.os.Handler(mainLooper).postDelayed({ disableSelf() }, 180)
        Log.i(logTag, "session paused; returned to Dextop home for explicit recovery")
    }

    private fun overlayButtonOrder(): MutableList<String> {
        val saved = getSharedPreferences(PREFS, MODE_PRIVATE)
            .getString("overlay_button_order", "modes,volume,brightness,resolution,android,stop,reconnect,orientation").orEmpty()
            .replace("actions", "stop,reconnect,orientation")
            .split(',').filter { it in controlIds }.toMutableList()
        controlIds.forEach { if (it !in saved) saved.add(it) }
        return saved
    }

    private val controlIds get() = listOf(
        "modes", "volume", "brightness", "resolution", "android",
        "stop", "reconnect", "orientation"
    )

    private fun addCustomControls(panel: LinearLayout, order: List<String>) {
        val mutableOrder = order.toMutableList()
        val views = linkedMapOf<String, View>()
        val squareIds = setOf("stop", "reconnect", "orientation")
        val grid = GridLayout(this).apply {
            columnCount = 3
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
                val span = if (id in squareIds) 1 else 3
                if (span == 3 && column != 0) { row++; column = 0 }
                if (span == 1 && column + span > 3) { row++; column = 0 }
                view.layoutParams = GridLayout.LayoutParams(
                    GridLayout.spec(row),
                    GridLayout.spec(column, span, 1f)
                ).apply {
                    width = 0
                    height = dp(54)
                    bottomMargin = dp(8)
                    if (column > 0) leftMargin = dp(6)
                }
                if (span == 3) { row++; column = 0 } else {
                    column++
                    if (column == 3) { row++; column = 0 }
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
                    start(Config(size.first, size.second, item.density, secureDisplay, showSystemDecorations))
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
                start(Config(if (portrait) h else w, if (portrait) w else h, d, secureDisplay, showSystemDecorations))
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

    private fun trackpad(event: MotionEvent): Boolean {
        maxPointers = maxOf(maxPointers, event.pointerCount)
        if (experimentalMultiTouch && handleExperimentalEdgeGesture(event)) return true
        if (directTouch && experimentalMultiTouch) {
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
                if (directTouch) {
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
                        Log.i(logTag, "scroll start x=$cursorX y=$scrollY display=$targetDisplayId")
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
                    Log.i(logTag, "scroll end x=$cursorX y=$scrollY display=$targetDisplayId")
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
                    if (directTouch) moveCursorToTouch(event.x, event.y)
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
                threeFingerEdgeSwipe = false
                edgeMenuTriggered = false
                edgeGestureOriginX = 0f
            }
            MotionEvent.ACTION_POINTER_DOWN -> if (event.pointerCount >= 3) {
                var minimumX = Float.MAX_VALUE
                var totalX = 0f
                for (index in 0 until event.pointerCount) {
                    val x = event.getX(index)
                    minimumX = minOf(minimumX, x)
                    totalX += x
                }
                if (minimumX > dp(120) && touchStartX > dp(120)) return false
                threeFingerEdgeSwipe = true
                edgeGestureOriginX = totalX / event.pointerCount
                if (directTouch) injectDirectTouch(event, MotionEvent.ACTION_CANCEL)
                return true
            }
            MotionEvent.ACTION_MOVE -> if (threeFingerEdgeSwipe) {
                var totalX = 0f
                for (index in 0 until event.pointerCount) totalX += event.getX(index)
                if (!edgeMenuTriggered &&
                    totalX / event.pointerCount - edgeGestureOriginX >= dp(48)
                ) {
                    edgeMenuTriggered = true
                    toggleMenu()
                }
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> if (threeFingerEdgeSwipe) return true
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (threeFingerEdgeSwipe) {
                threeFingerEdgeSwipe = false
                edgeMenuTriggered = false
                edgeGestureOriginX = 0f
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
        if (!physicalMouseActive) return
        physicalMouseActive = false
        cursorView?.visibility = if (directTouch) View.GONE else View.VISIBLE
    }

    private fun activatePhysicalMouse() {
        if (overlayTextInputActive) return
        physicalMouseActive = true
        cursorView?.visibility = View.VISIBLE
        cursorView?.update(cursorX / targetWidth, cursorY / targetHeight)
        setOverlayFocusable(true)
        surfaceView?.post {
            surfaceView?.requestFocus()
            surfaceView?.requestPointerCapture()
        }
    }

    private fun handlePhysicalMouseEvent(event: MotionEvent, view: View): Boolean {
        if (!event.isFromSource(InputDevice.SOURCE_MOUSE)) return false
        activatePhysicalMouse()
        return forwardMouseEvent(event, view)
    }

    private fun handleCapturedMouseEvent(event: MotionEvent): Boolean {
        if (!event.isFromSource(InputDevice.SOURCE_MOUSE)) return false
        activatePhysicalMouse()
        val dx = event.getAxisValue(MotionEvent.AXIS_RELATIVE_X)
        val dy = event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y)
        if (dx != 0f || dy != 0f) moveCursor(dx, dy)
        return forwardCapturedMouseButtonsAndWheel(event)
    }

    private fun forwardCapturedMouseButtonsAndWheel(source: MotionEvent): Boolean {
        if (targetDisplayId < 0) return false
        return runCatching {
            val event = MotionEvent.obtain(source)
            event.offsetLocation(cursorX - event.x, cursorY - event.y)
            check(inputDispatcher.send(event, targetDisplayId))
            if (source.actionMasked == MotionEvent.ACTION_BUTTON_PRESS) cursorView?.pulse()
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
            if (connected) {
                activatePhysicalMouse()
            } else if (physicalMouseActive) {
                physicalMouseActive = false
                cursorView?.visibility = if (directTouch) View.GONE else View.VISIBLE
            }
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
        val config = suspendedConfig ?: return
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
                    panel.translationY = panel.height.toFloat()
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
            // The closing visuals may remain for 180 ms, but input must return
            // to Dextop immediately and must never hit the scrim's reopen click.
            frame.routeTouchesToSurface = true
            menuScrim?.isClickable = false
            menuScrim?.animate()?.alpha(0f)?.setDuration(180)?.start()
            val animation = if (targetWidth >= targetHeight) {
                panel.animate().translationX(-panel.width.toFloat())
            } else {
                panel.animate().translationY(panel.height.toFloat())
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
                frame.routeTouchesToSurface = false
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

    private fun leftClick() {
        if (dragHeld) return
        injectTouch(MotionEvent.ACTION_DOWN, cursorX, cursorY)
        injectTouch(MotionEvent.ACTION_UP, cursorX, cursorY)
        if (!directTouch) cursorView?.pulse()
        Log.i(logTag, "click x=$cursorX y=$cursorY display=$targetDisplayId")
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
            Log.i(logTag, "right click x=$cursorX y=$cursorY display=$targetDisplayId")
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
        Log.i(logTag, "drag=$dragHeld x=$cursorX y=$cursorY display=$targetDisplayId")
    }

    private fun changeOrientation() {
        val portrait = targetWidth >= targetHeight
        MainActivity.setDisplayOrientation(portrait)
        forcePhoneRotation(portrait)
        val config = Config(targetHeight, targetWidth, density, secureDisplay, showSystemDecorations)
        root?.postDelayed({ start(config) }, 350)
    }

    private fun injectKey(keyCode: Int) {
        if (targetDisplayId < 0) return
        runCatching {
            val now = SystemClock.uptimeMillis()
            listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP).forEach { action ->
                val event = KeyEvent(now, SystemClock.uptimeMillis(), action, keyCode, 0)
                check(inputDispatcher.send(event, targetDisplayId))
            }
        }.onFailure { Log.e(logTag, "key injection failed", it) }
    }

    /** Forwards physical-keyboard input while preserving modifiers and repeat state. */
    private fun forwardKeyEvent(source: KeyEvent): Boolean {
        if (targetDisplayId < 0) return false
        return runCatching {
            val event = KeyEvent(source)
            inputDispatcher.send(event, targetDisplayId)
        }.onFailure { Log.e(logTag, "keyboard forwarding failed", it) }
            .getOrDefault(false)
    }

    /** Forwards physical mouse movement, buttons and wheel input to the desktop display. */
    private fun forwardMouseEvent(source: MotionEvent, view: View): Boolean {
        if (targetDisplayId < 0 || view.width <= 0 || view.height <= 0) return false
        return runCatching {
            val event = MotionEvent.obtain(source)
            event.transform(Matrix().apply {
                setScale(targetWidth.toFloat() / view.width, targetHeight.toFloat() / view.height)
            })
            check(inputDispatcher.send(event, targetDisplayId))

            cursorX = event.x.coerceIn(0f, targetWidth - 1f)
            cursorY = event.y.coerceIn(0f, targetHeight - 1f)
            cursorView?.update(cursorX / targetWidth, cursorY / targetHeight)
            if (source.actionMasked == MotionEvent.ACTION_BUTTON_PRESS) cursorView?.pulse()
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
        val sourceWidth = view.width.coerceAtLeast(1).toFloat()
        val sourceHeight = view.height.coerceAtLeast(1).toFloat()
        runCatching {
            val properties = Array(source.pointerCount) { index ->
                MotionEvent.PointerProperties().apply {
                    source.getPointerProperties(index, this)
                    toolType = MotionEvent.TOOL_TYPE_FINGER
                }
            }
            val coordinates = Array(source.pointerCount) { index ->
                MotionEvent.PointerCoords().apply {
                    source.getPointerCoords(index, this)
                    x = source.getX(index) / sourceWidth * targetWidth
                    y = source.getY(index) / sourceHeight * targetHeight
                    pressure = if (source.actionMasked == MotionEvent.ACTION_UP) 0f else 1f
                    size = 1f
                }
            }
            val event = MotionEvent.obtain(
                source.downTime,
                source.eventTime,
                actionOverride ?: source.action,
                source.pointerCount,
                properties,
                coordinates,
                source.metaState,
                source.buttonState,
                source.xPrecision,
                source.yPrecision,
                source.deviceId,
                source.edgeFlags,
                InputDevice.SOURCE_TOUCHSCREEN,
                source.flags
            )
            check(inputDispatcher.send(event, targetDisplayId))
            event.recycle()
        }.onFailure { Log.e(logTag, "multi-touch injection failed", it) }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        val view = surfaceView ?: return
        if (view.width > 0 && view.height > 0) createDisplay(holder.surface, view.width, view.height)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (mirrorDisplayId < 0 && width > 0 && height > 0) createDisplay(holder.surface, width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        releaseMirror()
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
            configureDisplay()
            displayBackend.attach(
                targetDisplayId,
                surfaceView ?: error(NativeStrings.text("nativeMirrorSurfaceUnavailable")),
                width,
                height,
                targetWidth,
                targetHeight
            )
            mirrorDisplayId = targetDisplayId
            displayCreationInProgress = false
            sessionJournal.running(targetDisplayId)
            startRawMouseReader()
            cursorX = targetWidth / 2f
            cursorY = targetHeight / 2f
            cursorView?.update(.5f, .5f)
            check(launchHome()) { "The desktop HOME activity could not be launched" }
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

    private fun configureDisplay() {
        val service = systemService("window", "android.view.IWindowManager")
        val type = Class.forName("android.view.IWindowManager")
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

    private fun releaseMirror() {
        stopRawMouseReader()
        displayBackend.releaseLayer()
        mirrorDisplayId = -1
        displayCreationInProgress = false
    }

    private fun startRawMouseReader() {
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
                            type == "0000" && code == "0000" && (pendingX != 0f || pendingY != 0f) -> {
                                val dx = pendingX
                                val dy = pendingY
                                pendingX = 0f
                                pendingY = 0f
                                root?.post {
                                    activatePhysicalMouse()
                                    moveCursor(dx, dy)
                                }
                            }
                        }
                    }
                }
            }.apply { name = "DextopMouseReader"; isDaemon = true }.start()
            Log.i(logTag, "raw mouse reader started")
        }.onFailure { Log.e(logTag, "raw mouse reader failed", it) }
    }

    private fun stopRawMouseReader() {
        mouseReaderRunning = false
        mouseReaderProcess?.let { runCatching { it.destroy() } }
        mouseReaderProcess = null
    }

    private fun systemService(name: String, interfaceName: String): Any {
        return privilegedAccess.service(name, interfaceName)
    }

    private fun setPhoneNavigationDisabled(disabled: Boolean) {
        runCatching {
            val service = systemService("statusbar", "com.android.internal.statusbar.IStatusBarService")
            val type = Class.forName("com.android.internal.statusbar.IStatusBarService")
            val flags = if (disabled) 0x00200000 or 0x00400000 or 0x01000000 else 0
            val method = type.methods.firstOrNull {
                it.name == "disable" && it.parameterTypes.size == 4
            }
            if (method != null) {
                method.invoke(service, 0, flags, navigationToken, packageName)
            } else {
                type.getMethod(
                    "disable",
                    Int::class.javaPrimitiveType,
                    android.os.IBinder::class.java,
                    String::class.java
                ).invoke(service, flags, navigationToken, packageName)
            }
            Log.i(logTag, "phone navigation disabled=$disabled")
        }.onFailure { Log.e(logTag, "phone navigation state failed", it) }
    }

    private fun forcePhoneRotation(portrait: Boolean) {
        runCatching {
            val service = systemService("window", "android.view.IWindowManager")
            val type = Class.forName("android.view.IWindowManager")
            val method = type.methods.first {
                it.name == "freezeDisplayRotation" && it.parameterTypes.size >= 2
            }
            val args = method.parameterTypes.mapIndexed { index, parameter ->
                when {
                    index == 0 -> 0
                    index == 1 -> if (portrait) 0 else 1
                    parameter == String::class.java -> packageName
                    parameter == Boolean::class.javaPrimitiveType -> true
                    else -> null
                }
            }.toTypedArray()
            method.invoke(service, *args)
        }.onFailure { Log.e(logTag, "phone rotation lock failed", it) }
    }

    private fun releasePhoneRotation() {
        runCatching {
            val service = systemService("window", "android.view.IWindowManager")
            val type = Class.forName("android.view.IWindowManager")
            val method = type.methods.firstOrNull {
                it.name == "thawDisplayRotation" && it.parameterTypes.isNotEmpty()
            } ?: type.methods.firstOrNull {
                it.name == "thawRotation"
            } ?: error(NativeStrings.text("nativeRotationReleaseUnsupported"))
            val args = method.parameterTypes.mapIndexed { index, parameter ->
                when {
                    index == 0 -> 0
                    parameter == String::class.java -> packageName
                    else -> null
                }
            }.toTypedArray()
            method.invoke(service, *args)
        }.onFailure { Log.e(logTag, "phone rotation unlock failed", it) }
    }

    private fun stop() {
        val wasActive = active
        suspendedForLockScreen = false
        suspendedConfig = null
        if (dragHeld) toggleDrag()
        runCatching { displayBackend.clearRequest() }
        setPhoneNavigationDisabled(false)
        releasePhoneRotation()
        removeWindow()
        targetDisplayId = -1
        active = false
        MainActivity.restoreOrientation()
        val restored = runCatching {
            desktopModeConfigurator.restore()
            sessionJournal.restoreSystemSettings()
        }.onFailure { Log.e(logTag, "settings restoration failed", it) }.isSuccess
        if (restored) sessionJournal.clear()
        if (restored) {
            getSharedPreferences("dextop_cleanup_state", MODE_PRIVATE).edit()
                .putBoolean("cleanup_pending", false)
                .putBoolean("paused_by_user", false)
                .putLong("verified_at", System.currentTimeMillis())
                .commit()
        }
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
        cursorView?.let { runCatching { windowManager?.removeView(it) } }
        root?.let { runCatching { windowManager?.removeView(it) } }
        root = null
        rootWindowParams = null
        surfaceView = null
        cursorView = null
        menu = null
        menuScrim = null
        performanceHud = null
        demoInfoView = null
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private class TouchRoutingFrame(context: Context) : FrameLayout(context) {
        var routeTouchesToSurface = false

        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            if (routeTouchesToSurface) {
                val surface = getChildAt(0)
                if (surface != null) return surface.dispatchTouchEvent(event)
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
            val y = normalizedY * height
            canvas.drawCircle(x, y, radius, fill)
            canvas.drawCircle(x, y, radius, stroke)
        }
    }
}
