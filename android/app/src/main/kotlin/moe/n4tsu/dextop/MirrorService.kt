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
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.text.InputType
import android.transition.ChangeBounds
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.util.Log
import android.util.Base64
import android.view.Gravity
import android.view.DragEvent
import android.view.Display
import android.view.HapticFeedbackConstants
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewConfiguration
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
import android.widget.ProgressBar
import android.animation.ValueAnimator
import android.animation.LayoutTransition
import android.widget.GridLayout
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.SessionManagerListener
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import moe.n4tsu.dextop.input.PrivilegedInputClient
import moe.n4tsu.dextop.input.PrivilegedInputProtocol
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject
import org.lsposed.hiddenapibypass.HiddenApiBypass

class MirrorService : AccessibilityService(), SurfaceHolder.Callback {
    data class Config(
        val width: Int,
        val height: Int,
        val density: Int,
        val secure: Boolean = false,
        val decorations: Boolean = false,
        /**
         * Creates only the Dextop overlay display.  No accessibility host is
         * added to the phone display; Android Auto attaches its own recording
         * VirtualDisplay to that overlay instead.
         */
        val autoOnly: Boolean = false
    )

    /**
     * Samsung's special-size Fold8 exposes the laptop hinge and orientation
     * differently from the normal-size Fold family. Fold8 Ultra and Fold7
     * therefore use the normal-size posture gate, while the special Fold8
     * keeps its dedicated handling.
     */
    private enum class LaptopFoldProfile {
        FOLD8,
        STANDARD_FOLDABLE
    }

    companion object {
        private const val PREFS = "freedextop_input"
        private const val KEY_DIRECT_TOUCH = "direct_touch"
        private const val KEY_ROUTE_MOUSE = "route_physical_mouse"
        private const val KEY_ROUTE_KEYBOARD = "route_physical_keyboard"

        /** Explicit compatibility switch for the previous software cursor. */
        private const val KEY_SOFTWARE_CURSOR_FALLBACK = "software_cursor_fallback"

        /** Persisted three-way pointer profile; old installs use the fallback key. */
        private const val KEY_VIRTUAL_POINTER_PROFILE = "virtual_pointer_profile"
        private const val KEY_ROTATE_180_LANDSCAPE = "rotate_180_landscape"
        private const val KEY_ROTATE_180_PORTRAIT = "rotate_180_portrait"
        private const val VIRTUAL_MOUSE_NAME = "Dextop Virtual Mouse"
        private const val VIRTUAL_TOUCHPAD_NAME = "Dextop Virtual Touchpad"
        private const val LAPTOP_KEYBOARD_NAME = "Dextop Laptop Keyboard"
        private const val LAPTOP_KEYBOARD_VENDOR_ID = 6353
        private const val LAPTOP_KEYBOARD_PRODUCT_ID = 5417
        private const val VIRTUAL_TOUCHPAD_MAX_SLOTS = 5
        private const val VIRTUAL_TOUCHPAD_MAX_X = 1839
        private const val VIRTUAL_TOUCHPAD_MAX_Y = 1199

        /** Lower resolution makes Android's touchpad acceleration cover more distance. */
        private const val VIRTUAL_TOUCHPAD_RESOLUTION = 12
        private const val VIRTUAL_TOUCHPAD_TOUCH_MAJOR = 20
        private const val VIRTUAL_TOUCHPAD_PRESSURE = 40
        private const val VIRTUAL_TOUCHPAD_MOVE_LOG_INTERVAL_MS = 250L
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        private const val NOTIFICATION_LAUNCH_WINDOW_MS = 3_000L
        private const val NOTIFICATION_ROUTE_RETRY_DELAY_MS = 140L
        private const val NOTIFICATION_ROUTE_RETRIES = 5
        private const val HOST_DISPLAY_MONITOR_INTERVAL_MS = 1_000L
        private const val HOME_DECORATION_RETRY_DELAY_MS = 180L
        private const val LAPTOP_SHIFT = -1
        private const val LAPTOP_CONTROL = -2
        private const val LAPTOP_ALT = -3
        private const val LAPTOP_CAPS = -4
        private const val LAPTOP_FN = -5
        private const val LAPTOP_MENU = -6
        private const val DEBUG_FORCE_LAPTOP_MODE = false
        private val FOLD8_SPECIAL_MODEL_IDS = setOf(
            "SMF971", "SMF971B", "SMF971U", "SMF971U1", "SMF971W", "SMF9710",
            "SMF971N", "SMF971Q", "SMF971Z", "SMF971C", "SCG41", "SC57G"
        )
        private val FOLD8_ULTRA_MODEL_IDS = setOf(
            "SMF976", "SMF976B", "SMF976U", "SMF976U1", "SMF976W", "SMF9760",
            "SMF976N", "SMF976Q", "SMF976Z", "SMF976C", "SCG39", "SC56G"
        )
        private const val FOLD8_SPECIAL_DEVICE_PREFIX = "H8Q"
        private const val FOLD8_ULTRA_DEVICE_PREFIX = "Q8Q"
        private const val STATUS_BAR_INTERFACE = "com.android.internal.statusbar.IStatusBarService"
        private const val PHONE_NAVIGATION_DISABLE_FLAGS =
            0x00200000 or 0x00400000 or 0x01000000
        private var instance: MirrorService? = null
        private var pending: Config? = null
        private var pendingAutoSurface: Surface? = null
        private var pendingStartResult: ((Result<Map<String, Any>>) -> Unit)? = null
        private var pendingDemo = false
        private var pendingLaptopDemo = false
        private var pendingLaptopPreviewThemeId: String? = null
        private var active = false

        fun launch(
            context: Context,
            width: Int,
            height: Int,
            density: Int,
            secure: Boolean,
            decorations: Boolean,
            autoOnly: Boolean = false,
            autoSurface: Surface? = null,
            completion: (Result<Map<String, Any>>) -> Unit
        ) {
            val running = instance
            if (running?.stopping == true) {
                completion(Result.failure(IllegalStateException("Dextop is still finishing its previous session")))
                return
            }
            val effectiveDecorations = running?.let {
                decorations || it.shouldUsePersistedSystemDecorations()
            } ?: decorations
            if (active && running != null && running.targetDisplayId >= 0 &&
                secure == running.secureDisplay && effectiveDecorations == running.showSystemDecorations &&
                autoOnly == running.autoOnlySession
            ) {
                val requested = Config(width, height, density, secure, effectiveDecorations, autoOnly)
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
            pending = Config(width, height, density, secure, effectiveDecorations, autoOnly)
            pendingAutoSurface = autoSurface?.takeIf { autoOnly }
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

        /** True only when the active accessibility session is owned by Car Companion. */
        fun isAutoOnlySessionActive(): Boolean = active && instance?.autoOnlySession == true

        /** Source display selected for the Android Auto parked-app mirror. */
        fun androidAutoSourceDisplayId(): Int = instance?.targetDisplayId
            ?.takeIf { active && it >= 0 }
            ?: android.view.Display.DEFAULT_DISPLAY

        /**
         * Aligns the phone-side Dextop orientation with the Android Auto host
         * only when the user explicitly selected the phone-side mirror mode.
         * Auto-only sessions never call this method and therefore never rotate
         * or resize the phone UI.
         */
        fun alignPhoneMirrorToAndroidAuto(width: Int, height: Int) {
            val service = instance ?: return
            service.root?.post {
                if (!active || service.autoOnlySession || width <= 0 || height <= 0) return@post
                val portrait = height > width
                service.applyHostDisplayOrientation(portrait)
                service.forcePhoneRotation(portrait)
                OperationLog.i(
                    service,
                    "AndroidAuto",
                    "phone-side mirror orientation aligned portrait=$portrait host=${width}x$height"
                )
            }
        }

        /**
         * True while the previous session is still tearing down its display,
         * input filters, and phone UI state. The Flutter home screen uses this
         * to keep the start action disabled until cleanup has completed.
         */
        fun isStopping(): Boolean = instance?.stopping == true

        /** True while a phone-side session is active or temporarily paused. */
        fun ownsPhoneSession(): Boolean = (active && instance?.autoOnlySession != true) ||
                instance?.pausedForAndroid == true ||
                (instance?.stopping == true && instance?.autoOnlySession != true) ||
                pending?.autoOnly == false

        /**
         * Re-submit the phone navigation restore when the Dextop home activity
         * becomes visible again.  This covers vendor SystemUI implementations
         * that reapply the disable flags while the accessibility window is
         * being removed.
         */
        fun restorePhoneNavigation(context: Context? = null) {
            instance?.setPhoneNavigationDisabled(false)
            // Samsung SystemUI keeps a separate recovery marker for the
            // bottom-gesture navigation bar.  Clearing the status-bar disable
            // mask alone leaves this marker at 0 after an interrupted Dextop
            // session, so SystemUI can continue treating the gesture bar as
            // suspended even though the bar is visible again.
            context?.let { restoreSamsungBottomGestureState(it) }
        }

        private fun restoreSamsungBottomGestureState(context: Context) {
            if (!Build.MANUFACTURER.equals("samsung", ignoreCase = true)) return
            runCatching {
                Settings.Secure.putInt(
                    context.contentResolver,
                    "sem_bottom_gesture_restored",
                    1
                )
            }.onFailure {
                Log.w("DextopMirror", "unable to restore Samsung bottom gesture marker", it)
            }
        }

        fun isFoldableDevice(): Boolean = instance?.isFoldableDevice() == true

        fun updateLaptopModeEnabled(enabled: Boolean) {
            instance?.root?.post { instance?.applyFlutterLaptopModeSetting(enabled) }
        }

        /** Apply a theme change to an already visible laptop keyboard. */
        fun updateLaptopTheme(themeId: String) {
            val service = instance ?: return
            service.getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
                .edit()
                .putString("flutter.laptop_keyboard_theme", themeId)
                .apply()
            service.getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString("laptop_keyboard_theme", themeId)
                .apply()
            service.root?.post {
                if (!isActive() || !service.laptopModeActive || service.demoMode) return@post
                if (service.laptopSettingsVisible) service.showLaptopKeyboardSettings()
                else service.rebuildLaptopDeck()
            }
        }

        /** Shows the real laptop deck on the active virtual-display session. */
        fun showLaptopPreview(themeId: String? = null): Boolean {
            val service = instance ?: return false
            if (!active || service.root == null) return false
            service.laptopPreviewThemeId = themeId
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
            val current = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty()
            Settings.Secure.putString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                (current.split(':').filter { it.isNotBlank() } + component).distinct().joinToString(":"))
            Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            instance?.showDemoWindow()
        }

        fun setPendingLaptopPreviewTheme(themeId: String?) {
            pendingLaptopPreviewThemeId = themeId
            instance?.laptopPreviewThemeId = themeId
        }

        fun hideLaptopDemo() {
            instance?.hideDemoWindow()
            instance?.laptopPreviewThemeId = null
            pendingDemo = false
            pendingLaptopDemo = false
            pendingLaptopPreviewThemeId = null
        }

        fun exitLaptopPreview() {
            instance?.root?.post {
                instance?.laptopPreviewThemeId = null
                instance?.laptopManualOverride = false
                instance?.laptopAutoSuppressedByUser = false
                instance?.laptopAutoActivated = false
                instance?.setLaptopMode(false)
            }
        }

        fun setOverlayHiddenForSettings(hidden: Boolean) {
            instance?.root?.post {
                instance?.root?.visibility = if (hidden) View.GONE else View.VISIBLE
            }
        }

        fun activeDisplayId(): Int = instance?.targetDisplayId ?: -1

        /**
         * Display ids currently owned by the phone-side session.  Android
         * Auto has its own overlay owner; callers creating a second overlay
         * must exclude this id even while the phone session is resizing or
         * before its Flutter status has caught up.
         */
        fun phoneOverlayDisplayIds(): Set<Int> = instance?.targetDisplayId
            ?.takeIf { ownsPhoneSession() && it >= 0 }
            ?.let(::setOf)
            ?: emptySet()

        /** True only when the phone owner uses this exact overlay entry. */
        fun ownsOverlaySpec(spec: String): Boolean {
            val service = instance ?: return false
            if (!ownsPhoneSession() || service.targetWidth <= 0 || service.targetHeight <= 0) return false
            val flags = buildList {
                if (service.secureDisplay) add("secure")
                if (service.showSystemDecorations) add("should_show_system_decorations")
            }
            val phoneSpec = "${service.targetWidth}x${service.targetHeight}/${service.density}" +
                    flags.joinToString(separator = ",", prefix = if (flags.isEmpty()) "" else ",")
            return phoneSpec == spec
        }

        fun launchPackage(packageName: String, bounds: android.graphics.Rect? = null): Boolean =
            instance?.let { service ->
                runCatching {
                    val fittedBounds = bounds?.let {
                        service.workspaceLayoutEngine.fit(
                            service.targetDisplayId,
                            service.targetWidth,
                            service.targetHeight,
                            it
                        )
                    }
                    OperationLog.i(
                        service,
                        "AppLaunch",
                        "requested package=$packageName display=${service.targetDisplayId} " +
                                "windowingMode=freeform bounds=${fittedBounds ?: "default"} " +
                                "decorations=${service.showSystemDecorations} environment=${service.desktopEnvironment.id}"
                    )
                    AppCatalog(service).launch(
                        packageName,
                        service.targetDisplayId,
                        fittedBounds
                    )
                    service.launchedAppBounds[packageName] = fittedBounds
                        ?: Rect(0, 0, service.targetWidth, service.targetHeight)
                    OperationLog.i(
                        service,
                        "AppLaunch",
                        "startActivity accepted package=$packageName display=${service.targetDisplayId}"
                    )
                    service.scheduleWindowLaunchDiagnostics("after_app_launch")
                }.onFailure {
                    Log.e(service.logTag, "app launch failed", it)
                    OperationLog.e(
                        service,
                        "AppLaunch",
                        "startActivity failed package=$packageName display=${service.targetDisplayId}",
                        it
                    )
                    service.scheduleWindowLaunchDiagnostics("app_launch_failure", delayMs = 0L)
                }.isSuccess
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

        /** Global navigation requested by the signature-protected CARDEX relay. */
        fun performCardexAction(action: String): Boolean {
            val service = instance ?: return false
            val globalAction = when (action) {
                "back" -> GLOBAL_ACTION_BACK
                "home" -> GLOBAL_ACTION_HOME
                "recents" -> GLOBAL_ACTION_RECENTS
                else -> return false
            }
            return service.performGlobalAction(globalAction)
        }

        fun cardexWorkspaces(): String = instance?.workspaceJson()?.toString() ?: "[]"

        fun saveCardexWorkspace(): String? = instance?.saveCurrentWorkspace()
            ?: "Dextop session is unavailable"

        fun launchCardexWorkspace(id: String): Boolean {
            val service = instance ?: return false
            val workspaces = service.workspaceJson()
            for (index in 0 until workspaces.length()) {
                val workspace = workspaces.optJSONObject(index) ?: continue
                if (workspace.optString("id") == id) {
                    service.launchOverlayWorkspace(workspace, closeMenu = false)
                    return true
                }
            }
            return false
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

        fun setSoftwareCursorFallbackEnabled(enabled: Boolean) {
            setVirtualPointerProfile(if (enabled) "software" else "touchpad")
        }

        fun setVirtualPointerProfile(profile: String) {
            val service = instance ?: return
            // Display settings are written by MainActivity's worker thread,
            // while the input device and overlay belong to the service main
            // looper.  Calling this directly left the old virtual mouse
            // connected (or updated the cursor view off-thread), making the
            // switch appear ineffective.
            Handler(Looper.getMainLooper()).post {
                if (instance === service) service.applyVirtualPointerProfile(profile)
            }
        }


    }

    private val logTag = "DextopMirror"
    private val useLegacyPixelMirrorProfile by lazy { PixelMirrorFallback.shouldUse(this) }
    private val desktopEnvironment by lazy {
        if (useLegacyPixelMirrorProfile) {
            DesktopEnvironmentRegistry.legacyPixelAosp(DeviceIdentity.current())
        } else {
            DesktopEnvironmentRegistry.current()
        }
    }
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
    private val inputDispatcher by lazy {
        InputDispatcher(privilegedAccess) { event, displayId, accepted, failure ->
            recordInputDispatch(event, displayId, accepted, failure)
        }
    }
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

    /**
     * Teardown is deliberately split into two phases.  Samsung's overlay
     * adapter removes the display asynchronously; restoring phone/DeX state
     * before that removal has completed races WindowManager/SystemUI.
     */
    private var stopCleanupGeneration = 0L
    private var menuScrim: View? = null
    private var demoMode = false
    private var demoInfoView: TextView? = null
    private var performanceHud: PerformanceHud? = null
    private var laptopContent: LinearLayout? = null
    private var laptopDeck: View? = null
    private var laptopDeckContent: LinearLayout? = null
    private var laptopPreviewThemeId: String? = null
    private var laptopSettingsVisible = false
    private var laptopFunctionRowVisible = false
    private var laptopKeyboardView: LinearLayout? = null
    private var laptopFnButton: TextView? = null
    private var laptopTrackpadView: View? = null
    private var laptopModeActive = false
    private var laptopManualOverride = false
    private var laptopAutoActivated = false

    /**
     * Blocks automatic reactivation after the user dismisses an automatically
     * shown deck. It is cleared only after a flat posture is observed or the
     * user explicitly enables laptop mode again.
     */
    private var laptopAutoSuppressedByUser = false

    /** Dextop orientation choice, independent from the laptop pane geometry. */
    private var requestedPortrait = false
    private var laptopBaseConfig: Config? = null
    private var laptopKeyboardRequested = false
    private var laptopKeyboardReady = false
    private var laptopKeyboardAssociationPending = false
    private var laptopKeyboardDeviceId = -1
    private var laptopKeyboardDisplayId = -1
    private var laptopKeyboardDisplayUniqueId: String? = null
    private var laptopKeyboardDescriptor: String? = null
    private var laptopKeyboardGeneration = 0L
    private var privilegedInputStarting = false
    private var privilegedPointerFallbackInProgress = false
    private var privilegedPointerFallbackActive = false
    private var privilegedInputConfigGeneration = 0
    private var lastPrivilegedInputSemanticConfig: IntArray? = null
    @Volatile
    private var virtualMouseReady = false
    private var virtualMouseDeviceId = -1
    private var virtualPointerRegisteredProfile = ""

    /** Runtime-only fallback when a vendor InputReader cannot expose touchpad. */
    private var virtualPointerRuntimeProfile: String? = null
    private var virtualMouseGeneration = 0L
    private var virtualMouseFractionX = 0f
    private var virtualMouseFractionY = 0f
    private var virtualMouseWheelFractionX = 0f
    private var virtualMouseWheelFractionY = 0f

    /** Android pointer ids currently occupying Linux multitouch Type-B slots. */
    private val virtualTouchpadSlotPointerIds =
        IntArray(VIRTUAL_TOUCHPAD_MAX_SLOTS) { -1 }
    private val virtualTouchpadSlotTrackingIds =
        IntArray(VIRTUAL_TOUCHPAD_MAX_SLOTS) { -1 }
    private var virtualTouchpadNextTrackingId = 1
    private var virtualTouchpadGestureSequence = 0L
    private var virtualTouchpadGestureStartedAt = 0L
    private var virtualTouchpadFrameCount = 0
    private var virtualTouchpadContactUpdateCount = 0
    private var virtualTouchpadLastMoveLogAt = 0L
    private var virtualPointerLastUnsupportedEventLogAt = 0L

    /** Latches native MT routing at ACTION_DOWN so readiness cannot switch mid-stream. */
    private var nativeTouchpadGestureActive = false
    private var laptopHostUniqueId: String? = null
    private var laptopShift = false
    private var laptopControl = false
    private var laptopAlt = false
    private var laptopCapsLock = false
    private val laptopTypeface: Typeface by lazy {
        Typeface.createFromAsset(assets, "fonts/HarmonyOS_Sans_Medium.ttf")
    }
    private val laptopModifierButtons = mutableMapOf<Int, TextView>()
    private val laptopShortcutButtons = mutableMapOf<Int, TextView>()
    private val laptopKeyPresses = mutableMapOf<View, LaptopKeyPressState>()
    private val laptopLegendButtons = mutableListOf<Pair<LaptopKeyTextView, String>>()
    private var targetDisplayId = -1
    private var targetWidth = 1920
    private var targetHeight = 1080
    private var density = 240
    private var secureDisplay = false
    private var showSystemDecorations = false
    private var autoOnlySession = false
    private val windowDiagnosticExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "Dextop-window-diagnostics").apply { isDaemon = true }
    }
    private val windowDiagnosticGeneration = AtomicInteger()
    private var autoDestinationSurface: Surface? = null
    private var autoOwnedDisplay: OwnedVirtualDisplay? = null

    /** True after the one-time Samsung HOME/decorations recovery has been used. */
    private var homeDecorationRetryUsed = false
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
    private var twoFingerTravelX = 0f
    private var twoFingerTravelY = 0f
    private var threeFinger = false
    private var scrolling = false
    private var lastScrollX = 0f
    private var lastScrollY = 0f
    private var scrollX = 0f
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
    private var lastInputDiagnosticAt = 0L
    private var lastTouchDiagnosticAt = 0L
    private var inputDiagnosticSequence = 0L
    private var orientationRebuildInProgress = false
    private var lastForcedPhonePortrait: Boolean? = null
    private var lastForcedPhoneHalfTurn: Boolean? = null
    private var castMediaRouter: MediaRouter? = null
    private var castRouteCallback: MediaRouter.Callback? = null
    private var castSessionListener: SessionManagerListener<CastSession>? = null
    private var castCompatibilityStreamer: CastCompatibilityStreamer? = null
    private var refreshRateReapplyGeneration = 0
    private var topologyReapplyGeneration = 0
    private val physicalInputRoutingSupported: Boolean
        get() = !Build.MANUFACTURER.equals("samsung", ignoreCase = true)
    @Volatile
    private var touchscreenReaderRunning = false
    @Volatile
    private var touchscreenReaderReady = false
    private var touchscreenReaderGeneration = 0L
    private var touchscreenReaderDevice = ""
    private var touchscreenReaderCandidateCount = 0
    private var rawTouchscreenTopologyRefreshGeneration = 0L
    private val privilegedInputClientDelegate: Lazy<PrivilegedInputClient> = lazy {
        PrivilegedInputClient(this, object : PrivilegedInputClient.Listener {
            override fun onInputState(category: String, message: String) {
                Log.i(logTag, "privileged input [$category] $message")
                when (category) {
                    "uinput_created" -> {
                        privilegedInputClient.acknowledgeEngineStarted()
                        privilegedInputStarting = false
                        val generation = virtualMouseGeneration
                        val profile = virtualPointerRegisteredProfile
                        if (profile.isNotBlank()) {
                            scheduleVirtualMouseReadyCheck(generation, profile, 0)
                        }
                    }

                    "device_discovered", "source_selected" -> {
                        touchscreenReaderRunning = true
                        touchscreenReaderReady = true
                        touchscreenReaderCandidateCount =
                            touchscreenReaderCandidateCount.coerceAtLeast(1)
                        touchscreenReaderDevice = message.substringAfter("path=", "")
                            .substringBefore(' ')
                    }

                    "device_waiting" -> {
                        touchscreenReaderRunning = true
                        touchscreenReaderReady = false
                        touchscreenReaderCandidateCount = 0
                    }

                    "keyboard_created" -> {
                        scheduleLaptopKeyboardReadyCheck(laptopKeyboardGeneration, 0)
                    }

                    "keyboard_destroyed" -> clearLaptopKeyboardPublication("native_destroyed")

                    "stopped" -> {
                        if (!privilegedInputClient.isEngineRunning()) {
                            privilegedInputStarting = false
                            virtualMouseReady = false
                            touchscreenReaderRunning = false
                            touchscreenReaderReady = false
                            touchscreenReaderCandidateCount = 0
                            updateVirtualCursorVisibility()
                        }
                    }

                    "disconnected", "worker_exited" -> {
                        privilegedInputClient.acknowledgeEngineStopped()
                        privilegedInputStarting = false
                        virtualMouseReady = false
                        touchscreenReaderRunning = false
                        touchscreenReaderReady = false
                        touchscreenReaderCandidateCount = 0
                        fallbackToSoftwarePointer("$category:$message")
                    }

                    "uinput_destroyed" -> {
                        virtualMouseReady = false
                        privilegedInputClient.setOutputReady(false)
                        updateVirtualCursorVisibility()
                    }

                    "native_error", "client_error" -> {
                        privilegedInputStarting = false
                        val pointerOutputFailed = category == "client_error" ||
                                (message.contains("uinput", ignoreCase = true) &&
                                        !message.contains("keyboard", ignoreCase = true))
                        if (pointerOutputFailed) {
                            fallbackToSoftwarePointer("$category:$message")
                        }
                        OperationLog.w(
                            this@MirrorService,
                            "PrivilegedInput",
                            "$category: $message"
                        )
                    }
                }
                if (category != "raw_event" && category != "uinput_event") {
                    OperationLog.i(
                        this@MirrorService,
                        "PrivilegedInput",
                        "$category: $message"
                    )
                }
            }

            override fun onThreeFingerGesture() {
                OperationLog.i(this@MirrorService, "PrivilegedInput", "three-finger action")
                performConfiguredGesture()
            }

            override fun onHaptic(strong: Boolean) {
                laptopTrackpadView?.performHapticFeedback(
                    if (strong) HapticFeedbackConstants.LONG_PRESS
                    else HapticFeedbackConstants.KEYBOARD_TAP
                )
            }
        })
    }
    private val privilegedInputClient: PrivilegedInputClient by privilegedInputClientDelegate
    private var inputManager: InputManager? = null
    private var sensorManager: SensorManager? = null
    private var hingeAngle: Float? = null
    private var filteredHingeAngle: Float? = null
    private var pendingLaptopMode: Boolean? = null
    private var pendingLaptopModeSince = 0L
    private var laptopModeEvaluationGeneration = 0L
    private var laptopPostureReevaluationGeneration = 0L
    private var laptopHostMismatchSince = 0L
    private var foldingApiFoldable: Boolean? = null
    private var foldingApiLaptopPosture: Boolean? = null
    private var foldingApiHorizontalHinge: Boolean? = null
    private var foldingApiLastProbeAt = 0L
    private var foldingApiLastSuccessAt = 0L
    private val foldingApiFailureGraceMs = 1_500L
    private val laptopModeDebounceMs = 420L
    private val laptopHostMismatchDebounceMs = 1_200L

    private fun persistedVirtualPointerProfile(): String {
        val input = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (input.contains(KEY_VIRTUAL_POINTER_PROFILE)) {
            return input.getString(KEY_VIRTUAL_POINTER_PROFILE, "touchpad")
                .orEmpty().lowercase().let(::normalizeVirtualPointerProfile)
        }
        val flutter = getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
        val stored = flutter.getString("flutter.virtual_pointer_profile", null)
        if (!stored.isNullOrBlank()) return normalizeVirtualPointerProfile(stored)
        // Preserve the old switch for upgrades.  A missing switch now means
        // the new true-touchpad profile, while an explicitly enabled fallback
        // still selects the software cursor.
        val display = getSharedPreferences("dextop_display_environment", MODE_PRIVATE)
        return if (input.getBoolean(
                KEY_SOFTWARE_CURSOR_FALLBACK,
                display.getBoolean(KEY_SOFTWARE_CURSOR_FALLBACK, false)
            )
        ) {
            "software"
        } else {
            "touchpad"
        }
    }

    private fun normalizeVirtualPointerProfile(value: String): String = when (value) {
        "touchpad", "touch_pad", "source_touchpad" -> "touchpad"
        "mouse", "virtual_mouse" -> "mouse"
        "software", "software_cursor", "cursor" -> "software"
        else -> "touchpad"
    }

    private fun activeVirtualPointerProfile(): String =
        normalizeVirtualPointerProfile(virtualPointerRuntimeProfile ?: persistedVirtualPointerProfile())

    private fun virtualPointerDeviceName(profile: String): String =
        if (profile == "touchpad") VIRTUAL_TOUCHPAD_NAME else VIRTUAL_MOUSE_NAME

    private fun currentInputMode(): String = when {
        physicalMouseActive -> "physical_mouse"
        rawTouchscreenBridgeConsumesTouchSurface() ->
            "raw_touchscreen_${activeVirtualPointerProfile()}"

        laptopTrackpadInputActive() && activeVirtualPointerProfile() == "touchpad" -> "virtual_touchpad"
        laptopTrackpadInputActive() -> "virtual_mouse"
        virtualMouseInputActive() && activeVirtualPointerProfile() == "touchpad" -> "virtual_touchpad"
        virtualMouseInputActive() -> "virtual_mouse"
        directTouch -> "direct_touch"
        else -> "cursor_touchpad"
    }

    private fun virtualMouseInputEnabled(): Boolean = activeVirtualPointerProfile() != "software"

    /**
     * Returns whether the kernel pointer is ready for the requested surface.
     * The phone surface owns the device exclusively in cursor mode, but the
     * laptop deck is a separate physical surface: its trackpad must be able
     * to use the device even when the phone display is configured for tap
     * (direct-touch) input.
     */
    private fun virtualPointerInputActive(allowDirectTouch: Boolean): Boolean =
        !demoMode && virtualMouseInputEnabled() &&
                virtualPointerRegisteredProfile == activeVirtualPointerProfile() &&
                virtualMouseReady &&
                virtualMouseProcessAlive() &&
                (allowDirectTouch || !directTouch)

    private fun virtualMouseInputActive(): Boolean = virtualPointerInputActive(false)

    private fun laptopTrackpadInputActive(): Boolean =
        laptopModeActive && virtualPointerInputActive(true)

    private fun virtualMouseProcessAlive(): Boolean =
        privilegedInputClient.isEngineRunning()

    private fun virtualMouseNaturalScroll(): Boolean =
        getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
            .getBoolean("flutter.virtual_mouse_natural_scroll", true)

    private fun updateVirtualCursorVisibility() {
        val cursor = cursorView ?: return
        // Hide while uinput is starting as well as after it is ready.  Showing
        // the software cursor during that short window creates a distracting
        // white flash when the user changes from tap to cursor mode.
        val virtualMouseStarting = privilegedInputStarting
        cursor.visibility = if (directTouch || virtualMouseReady || virtualMouseStarting) {
            View.GONE
        } else {
            View.VISIBLE
        }
    }

    /**
     * A mirror backend can fail after the input device has already hidden the
     * software pointer (for example when a vendor rejects WindowManager or
     * SurfaceControl mirroring).  Keep the failed backend from leaving the
     * pointer in an invisible state: tear down the virtual pointer and make
     * the accessibility cursor authoritative again.
     */
    private fun restoreSoftwareCursorAfterMirrorFailure(reason: String) {
        stopVirtualMouse()
        if (!directTouch) {
            cursorView?.apply {
                visibility = View.VISIBLE
                bringToFront()
                update(
                    (cursorX / targetWidth.coerceAtLeast(1)).coerceIn(0f, 1f),
                    (cursorY / targetHeight.coerceAtLeast(1)).coerceIn(0f, 1f)
                )
            }
        } else {
            cursorView?.visibility = View.GONE
        }
        OperationLog.w(
            this,
            "InputRouting",
            "software cursor restored after mirror failure reason=$reason"
        )
    }

    /**
     * A compact runtime snapshot is attached to the session log whenever the
     * host surface or logical display changes. This is intentionally separate
     * from Logcat so a shared diagnostic report contains the geometry that was
     * actually used for coordinate conversion.
     */
    private fun displayGeometrySnapshot(reason: String, hostWidth: Int? = null, hostHeight: Int? = null): String {
        val host = surfaceView
        val width = hostWidth ?: host?.width ?: 0
        val height = hostHeight ?: host?.height ?: 0
        val display = targetDisplayId.takeIf { it >= 0 }?.let {
            getSystemService(DisplayManager::class.java).getDisplay(it)
        }
        val metrics = display?.let {
            runCatching {
                android.util.DisplayMetrics().also { display.getRealMetrics(it) }
            }.getOrNull()
        }
        val bounds = runCatching { windowManager?.currentWindowMetrics?.bounds }.getOrNull()
        return "reason=$reason displayId=$targetDisplayId targetDisplayId=$targetDisplayId " +
                "mirrorDisplayId=$mirrorDisplayId " +
                "surfaceWidth=$width surfaceHeight=$height " +
                "windowWidth=${bounds?.width() ?: 0} windowHeight=${bounds?.height() ?: 0} " +
                "displayWidth=${metrics?.widthPixels ?: 0} displayHeight=${metrics?.heightPixels ?: 0} " +
                "targetWidth=$targetWidth targetHeight=$targetHeight density=$density " +
                "displayDensity=${metrics?.densityDpi ?: 0} rotation=${display?.rotation ?: -1} " +
                "configOrientation=${resources.configuration.orientation} " +
                "directTouch=$directTouch inputMode=${currentInputMode()}"
    }

    private fun recordInputDispatch(
        event: InputEvent,
        displayId: Int,
        accepted: Boolean,
        failure: Throwable?
    ) {
        val now = SystemClock.uptimeMillis()
        val motion = event as? MotionEvent
        val action = motion?.actionMasked ?: (event as? KeyEvent)?.action ?: -1
        val move = motion != null && (
                action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_HOVER_MOVE
                )
        // MOVE/HOVER events are sampled to keep the report useful instead of
        // filling the bounded session file. Rejections are always retained.
        if (move && accepted && now - lastInputDiagnosticAt < 250L) return
        lastInputDiagnosticAt = now
        inputDiagnosticSequence += 1
        val detail = buildString {
            append("seq=$inputDiagnosticSequence displayId=$displayId ")
            append("injectInputEvent=${if (accepted) "accepted" else "rejected"} ")
            append("accepted=$accepted action=$action source=${event.source} ")
            if (motion != null) {
                append("pointers=${motion.pointerCount} pointX:${motion.x} pointY:${motion.y} ")
            } else if (event is KeyEvent) {
                append("keyCode=${event.keyCode} repeat=${event.repeatCount} ")
            }
            append(displayGeometrySnapshot("input_dispatch"))
            failure?.let { append(" failure=${it.javaClass.simpleName}") }
        }
        if (accepted) OperationLog.i(this, "InputDispatch", detail)
        else OperationLog.w(this, "InputDispatch", detail)
    }

    private fun recordTouchRouting(event: MotionEvent, direct: Boolean) {
        val now = SystemClock.uptimeMillis()
        val move = event.actionMasked == MotionEvent.ACTION_MOVE ||
                event.actionMasked == MotionEvent.ACTION_HOVER_MOVE
        if (move && now - lastTouchDiagnosticAt < 250L) return
        lastTouchDiagnosticAt = now
        val view = surfaceView
        val mappedX = if (view != null && view.width > 0) {
            (event.x / view.width * targetWidth).coerceIn(0f, targetWidth - 1f)
        } else 0f
        val mappedY = if (view != null && view.height > 0) {
            (event.y / view.height * targetHeight).coerceIn(0f, targetHeight - 1f)
        } else 0f
        OperationLog.i(
            this,
            "TouchRouting",
            "action=${event.actionMasked} pointers=${event.pointerCount} source=${event.source} " +
                    "pointX:${event.x} pointY:${event.y} mappedX:${mappedX} mappedY:${mappedY} " +
                    "directTouch=$direct inputMode=${currentInputMode()} " +
                    displayGeometrySnapshot("touch_event")
        )
    }

    private val hingeListener = object : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

        override fun onSensorChanged(event: SensorEvent?) {
            val angle = event?.values?.firstOrNull() ?: return
            hingeAngle = angle
            refreshFoldingApiState("hinge_sensor")
            Log.d(logTag, "hinge angle=$angle laptop=$laptopModeActive")
            OperationLog.i(
                this@MirrorService,
                "FoldState",
                "hinge sensor angle=$angle apiFoldable=$foldingApiFoldable " +
                        "apiHalfOpened=$foldingApiLaptopPosture apiHorizontal=$foldingApiHorizontalHinge"
            )
            updateLaptopModeForHinge(angle)
        }
    }
    private val inputDeviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) {
            refreshPhysicalInputState()
            scheduleRawTouchscreenTopologyRefresh("input_device_added_$deviceId")
            if (laptopKeyboardRequested) {
                scheduleLaptopKeyboardReadyCheck(laptopKeyboardGeneration, 0)
            }
        }

        override fun onInputDeviceRemoved(deviceId: Int) {
            refreshPhysicalInputState()
            scheduleRawTouchscreenTopologyRefresh("input_device_removed_$deviceId")
            if (deviceId == laptopKeyboardDeviceId) {
                finishAllLaptopKeyPresses()
                clearLaptopKeyboardPublication("input_device_removed")
                if (laptopKeyboardRequested) {
                    scheduleLaptopKeyboardReadyCheck(laptopKeyboardGeneration, 0)
                }
            }
        }

        override fun onInputDeviceChanged(deviceId: Int) {
            refreshPhysicalInputState()
            scheduleRawTouchscreenTopologyRefresh("input_device_changed_$deviceId")
            if (laptopKeyboardRequested &&
                (deviceId == laptopKeyboardDeviceId || laptopKeyboardDeviceId < 0)
            ) {
                scheduleLaptopKeyboardReadyCheck(laptopKeyboardGeneration, 0)
            }
        }
    }
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            refreshFoldingApiState("display_added", force = true)
            if (active) OperationLog.i(
                this@MirrorService,
                "DisplayGeometry",
                displayGeometrySnapshot("display_added_$displayId")
            )
            if (active) refreshMenuGeometryAfterDisplayChange()
            refreshExternalDisplayState()
            scheduleInternal120HzReapply(requireExternalDisplay = true)
            scheduleTopologyReapplyAfterReconnect()
            scheduleLaptopModeReevaluation("display_added")
            if (laptopKeyboardRequested) {
                scheduleLaptopKeyboardReadyCheck(laptopKeyboardGeneration, 0)
            }
            if (active && displayId == Display.DEFAULT_DISPLAY) {
                scheduleHostDisplayReconfiguration("default display added")
            }
        }

        override fun onDisplayRemoved(displayId: Int) {
            refreshFoldingApiState("display_removed", force = true)
            if (active) OperationLog.i(
                this@MirrorService,
                "DisplayGeometry",
                displayGeometrySnapshot("display_removed_$displayId")
            )
            if (active) refreshMenuGeometryAfterDisplayChange()
            refreshExternalDisplayState()
            scheduleInternal120HzReapply(requireExternalDisplay = false)
            scheduleLaptopModeReevaluation("display_removed")
            if (displayId == laptopKeyboardDisplayId) {
                finishAllLaptopKeyPresses()
                clearLaptopKeyboardPublication("associated_display_removed")
            }
            if (active && displayId == Display.DEFAULT_DISPLAY) {
                scheduleHostDisplayReconfiguration("default display removed")
            }
        }

        override fun onDisplayChanged(displayId: Int) {
            // Preserve the edge walk before refreshing a changed source display.
            refreshFoldingApiState("display_changed", force = true)
            if (active) OperationLog.i(
                this@MirrorService,
                "DisplayGeometry",
                displayGeometrySnapshot("display_changed_$displayId")
            )
            if (active) refreshMenuGeometryAfterDisplayChange()
            refreshExternalDisplayState()
            scheduleInternal120HzReapply(requireExternalDisplay = true)
            scheduleLaptopModeReevaluation("display_changed")
            if (laptopKeyboardRequested && displayId == targetDisplayId) {
                scheduleLaptopKeyboardReadyCheck(laptopKeyboardGeneration, 0)
            }
            if (active && displayId == Display.DEFAULT_DISPLAY) {
                scheduleRawTouchscreenTopologyRefresh("default_display_changed")
                leaveLaptopModeOnCoverDisplay()
                scheduleHostDisplayReconfiguration("default display changed")
            } else if (active && displayId == targetDisplayId && mirrorDisplayId >= 0) {
                scheduleMirrorRefresh("source display changed")
            }
        }
    }

    private fun refreshMenuGeometryAfterDisplayChange() {
        val panel = menu ?: return
        val frame = root ?: return
        frame.post {
            if (!active || menu !== panel) return@post
            panel.layoutParams = menuLayoutParams()
            panel.requestLayout()
            scheduleMenuHeightUpdate()
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
                val topologyOverlays = buildSet {
                    if (mirrorDisplayId >= 0) add(mirrorDisplayId)
                    AndroidAutoMirrorActivity.autoOverlayDisplayIds().forEach(::add)
                }
                DisplayEnvironmentSettings(this).activateTopologyForOverlays(topologyOverlays)
            }
                .onFailure { Log.e(logTag, "display topology reapply after reconnect failed", it) }
        }, 750)
    }

    /**
     * Foldable vendors do not all deliver a hinge sensor event for the panel
     * hand-off.  Samsung can instead publish only a display/configuration
     * change, and its folding API may briefly report an empty feature list
     * while the new panel is attached.  Re-evaluate after that churn settles
     * so opening a session flat and then entering flex posture is handled the
     * same as starting the session half-open.
     */
    private fun scheduleLaptopModeReevaluation(reason: String) {
        if (!active || suspendedForLockScreen) return
        val generation = ++laptopPostureReevaluationGeneration
        android.os.Handler(mainLooper).postDelayed({
            if (generation != laptopPostureReevaluationGeneration ||
                !active || suspendedForLockScreen
            ) return@postDelayed
            if (!isLaptopAutoDetectionEnabled()) return@postDelayed
            refreshFoldingApiState("$reason settled", force = true)
            val angle = filteredHingeAngle ?: hingeAngle
            if (angle != null) {
                updateLaptopModeForHinge(angle)
            } else {
                updateLaptopModeFromCurrentPosture(reason)
            }
        }, 240L)
    }

    private val navigationToken = Binder()
    private var navigationRestoreGeneration = 0
    private var screenReceiverRegistered = false
    private var suspendedForLockScreen = false
    private var suspendedConfig: Config? = null
    private var screenLifecycleGeneration = 0
    private var keyguardLockObservedSinceScreenOff = false
    private var unlockCandidateSince = 0L
    private var unlockResumeScheduled = false
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!active) return
            Log.i(
                logTag,
                "screen lifecycle broadcast action=${intent?.action} " +
                        "suspended=$suspendedForLockScreen generation=$screenLifecycleGeneration"
            )
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> suspendForLockScreen()
                Intent.ACTION_SCREEN_ON -> waitForConfirmedUnlock(screenLifecycleGeneration)
                Intent.ACTION_USER_PRESENT -> resumeAfterUnlock("user_present")
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
            // WindowManager folding callbacks are not delivered consistently
            // during Samsung panel hand-off. Poll the posture while a session
            // is active so a flat/half-open transition is still observed even
            // when neither a display nor a hinge event is emitted.
            if (isLaptopAutoDetectionEnabled()) {
                refreshFoldingApiState("posture_monitor", force = true)
                updateLaptopModeFromCurrentPosture("posture monitor")
            }
            if (laptopModeActive &&
                !isDebugLaptopModeForced() &&
                laptopHostUniqueId != null &&
                defaultDisplayUniqueId()?.let { it != laptopHostUniqueId } == true &&
                hasStableLaptopHostMismatch() &&
                !isFoldableMainDisplay()
            ) {
                // A different internal panel became the default display. This
                // is only a cover-display signal when the new default is
                // actually the smaller panel. Fold8 can publish a temporary
                // unique-id change while the large panel is being re-laid out;
                // dismissing the deck for that transient event made the
                // keyboard appear briefly and then disappear.
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
        // These preferences belonged to the removed DPI/acceleration
        // controls. Clear them here as well as in Flutter startup because the
        // accessibility service can be started directly by an overlay.
        getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE).edit()
            .remove("flutter.virtual_mouse_dpi")
            .remove("flutter.virtual_mouse_acceleration")
            .apply()
        directTouch = getSharedPreferences(PREFS, MODE_PRIVATE)
            .getBoolean(KEY_DIRECT_TOUCH, false)
        // The overlay routing controls were retired. Leave physical devices
        // under Android's normal display routing instead of changing them.
        routePhysicalMouseToDextop = false
        routePhysicalKeyboardToDextop = false
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .remove(KEY_ROUTE_MOUSE)
            .remove(KEY_ROUTE_KEYBOARD)
            .apply()
        runCatching { physicalInputRouter.restore() }
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
        refreshFoldingApiState("configuration_changed", force = true)
        scheduleLaptopModeReevaluation("configuration_changed")
        OperationLog.i(
            this,
            "Orientation",
            "configuration changed orientation=${newConfig.orientation} density=${newConfig.densityDpi} " +
                    displayGeometrySnapshot("configuration_changed")
        )
        leaveLaptopModeOnCoverDisplay()
        scheduleHostDisplayReconfiguration(
            "configuration changed",
            densityDpi = newConfig.densityDpi
        )
        root?.postDelayed({
            if (active) refreshPrivilegedInputConfig("configuration_changed")
        }, 120L)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean = forwardKeyEvent(event)

    override fun onMotionEvent(event: MotionEvent) {
        // Events emitted by our uinput device already travel through
        // InputReader/InputDispatcher (and therefore display topology). Do
        // not feed them back through Dextop's target-display injector or they
        // would be delivered twice and the pointer would fight itself.
        if (isVirtualMouseEvent(event)) return
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
        // A completed stop sets active=false before the delayed accessibility
        // detach runs. Do not start a second teardown from that unbind; doing
        // so used to leave the service's stopping latch set forever.
        if (!pausedForAndroid && (active || stopping || pending != null)) stop()
        if (screenReceiverRegistered) unregisterReceiver(screenReceiver)
        inputManager?.unregisterInputDeviceListener(inputDeviceListener)
        sensorManager?.unregisterListener(hingeListener)
        getSystemService(DisplayManager::class.java).unregisterDisplayListener(displayListener)
        inputManager = null
        sensorManager = null
        screenReceiverRegistered = false
        if (privilegedInputClientDelegate.isInitialized()) {
            privilegedInputClient.release("accessibility_unbound")
        }
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        endCastSession("service_destroyed")
        windowDiagnosticGeneration.incrementAndGet()
        windowDiagnosticExecutor.shutdownNow()
        if (privilegedInputClientDelegate.isInitialized()) {
            privilegedInputClient.release("service_destroyed")
        }
        super.onDestroy()
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
        // A touchpad-to-mouse compatibility fallback is scoped to one session;
        // every new session retries the user's selected profile.
        virtualPointerRuntimeProfile = null
        privilegedPointerFallbackInProgress = false
        privilegedPointerFallbackActive = false
        laptopModeActive = false
        laptopBaseConfig = null
        laptopManualOverride = false
        laptopAutoSuppressedByUser = false
        laptopAutoActivated = false
        laptopHostUniqueId = null
        filteredHingeAngle = null
        pendingLaptopMode = null
        pendingLaptopModeSince = 0L
        laptopModeEvaluationGeneration += 1
        laptopPostureReevaluationGeneration += 1
        laptopHostMismatchSince = 0L
        val persistedDecorations = shouldUsePersistedSystemDecorations()
        val requestedConfig = if (persistedDecorations && !config.decorations) {
            OperationLog.i(
                this,
                "DesktopHome",
                "using persisted system decorations for firmware=${firmwareIdentity()}"
            )
            config.copy(decorations = true)
        } else {
            config
        }
        val effectiveConfig = effectiveConfig(requestedConfig)
        OperationLog.beginSession(
            this,
            "environment=${desktopEnvironment.id} sdk=${Build.VERSION.SDK_INT} " +
                    "display=${effectiveConfig.width}x${effectiveConfig.height}/${effectiveConfig.density} " +
                    "secure=${effectiveConfig.secure} decorations=${effectiveConfig.decorations}"
        )
        lastInputDiagnosticAt = 0L
        lastTouchDiagnosticAt = 0L
        inputDiagnosticSequence = 0L
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
        keyguardLockObservedSinceScreenOff = false
        unlockCandidateSince = 0L
        unlockResumeScheduled = false
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
        autoOnlySession = effectiveConfig.autoOnly
        autoDestinationSurface = if (autoOnlySession) {
            pendingAutoSurface?.takeIf { it.isValid }
                ?: CardexRelayService.activeDestinationSurface()
        } else {
            null
        }
        pendingAutoSurface = null
        homeDecorationRetryUsed = false
        // Dextop orientation is controlled exclusively by its overlay action.
        // Lock both the activity configuration and the framework rotation. On
        // foldables the service can be created while the phone is physically
        // portrait; locking only WMS leaves MainActivity's SurfaceView at
        // 1848x2448 while the desktop target is 2448x1848, so the first mirror
        // frame is permanently letterboxed until another configuration event.
        val portrait = targetHeight > targetWidth
        requestedPortrait = portrait
        if (!autoOnlySession) {
            applyHostDisplayOrientation(portrait)
            forcePhoneRotation(portrait)
        } else {
            OperationLog.i(
                this,
                "AndroidAuto",
                "starting headless Auto display ${targetWidth}x${targetHeight}/$density; phone orientation unchanged"
            )
        }
        cursorX = targetWidth / 2f
        cursorY = targetHeight / 2f
        OperationLog.i(this, "DisplayGeometry", displayGeometrySnapshot("session_configured"))
        removeWindow()
        if (autoOnlySession) {
            // There is deliberately no SurfaceView on the phone in this mode.
            // The Auto activity creates the only recording VirtualDisplay and
            // attaches it to the head-unit surface.
            pending = null
            active = true
            createHeadlessDisplay()
            return
        }
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
            setOnTouchListener { view, event ->
                if (event.isFromSource(InputDevice.SOURCE_MOUSE)) {
                    if (event.actionMasked == MotionEvent.ACTION_MOVE ||
                        event.actionMasked == MotionEvent.ACTION_HOVER_MOVE
                    ) activatePhysicalMouse()
                    forwardMouseEvent(event, this)
                } else {
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) activateTouchInput()
                    trackpad(event, sourceView = view)
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
        // The helper also keeps the cursor hidden while uinput is registering,
        // avoiding a one-frame white flash when changing modes.
        cursor.visibility = if (directTouch || virtualMouseInputActive()) View.GONE else View.VISIBLE
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
                    .getBoolean("flutter.performance_hud", false)
            ) View.VISIBLE else View.GONE
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
                    .getBoolean("flutter.keep_awake_during_session", false)
            ) {
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
            refreshFoldingApiState("window_added", force = true)
            OperationLog.i(
                this,
                "FoldState",
                "laptop profile=${laptopFoldProfile()} model=${Build.MODEL} " +
                        "device=${Build.DEVICE} requestedPortrait=$requestedPortrait " +
                        "apiHalfOpened=$foldingApiLaptopPosture apiHorizontal=$foldingApiHorizontalHinge"
            )
            val autoStart = isLaptopAutoDetectionEnabled() &&
                    isLaptopAutoOrientationEligible() && currentLaptopPosture() == true
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
        updateCursorPosition()
        Log.i(logTag, "fullscreen accessibility overlay added")
    }

    private data class LaptopKey(val label: String, val code: Int, val weight: Float = 1f)

    private data class LaptopKeyPressState(
        val keyCode: Int,
        val metaState: Int,
        var repeatCount: Int = 0,
        var repeater: Runnable? = null
    )

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
            setBackgroundColor(opaqueColor(palette.background))
            // The deck is an opaque interaction surface.  Without a handler
            // on its empty areas, a tap between keys can fall through to the
            // mirrored Android surface underneath the keyboard.
            isClickable = true
            setOnTouchListener { _, _ -> true }
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
                    // The theme opacity controls the keyboard-area veil below
                    // the keys. Keep the image itself opaque so changing the
                    // slider actually changes the gaps between keys instead
                    // of fading the entire image twice.
                    alpha = 1f
                    setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && palette.blur > 0f) {
                        setRenderEffect(
                            RenderEffect.createBlurEffect(
                                palette.blur, palette.blur, Shader.TileMode.CLAMP
                            )
                        )
                    }
                }
            }.getOrNull()?.let { image ->
                deck.addView(image, FrameLayout.LayoutParams(-1, -1))
            }
        }
        deck.addView(content, FrameLayout.LayoutParams(-1, -1))
        content.setBackgroundColor(opacityColor(palette.background, palette.opacity))
        val trackpad = TextView(this).apply {
            // The label is a per-theme preference. The entire surface remains
            // an input area whether the label is visible or not.
            text = if (palette.showTrackpadLabel) "TRACKPAD" else ""
            gravity = Gravity.CENTER
            typeface = laptopTypeface
            textSize = 11f
            letterSpacing = .15f
            setTextColor(palette.trackpadText)
            background = GradientDrawable().apply {
                setColor(opacityColor(palette.trackpad, palette.opacity))
                setStroke(dp(1), palette.border)
                cornerRadius = dp(palette.radius.toInt()).toFloat()
            }
            setOnTouchListener { view, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) activateLaptopTrackpad()
                // The laptop deck is an independent input surface.  It must
                // keep using the kernel touchpad even while the phone surface
                // is in tap/direct-touch mode; only the phone surface is
                // allowed to disconnect the pointer in that mode.
                trackpad(
                    event,
                    sourceView = view,
                    forceCursorMode = true,
                    allowVirtualPointer = true,
                    hapticView = view
                )
            }
        }
        laptopTrackpadView = trackpad
        val keyboard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            // Empty space between keys is part of the theme background. It
            // must use the same opacity slider as the rest of the keyboard
            // area, while the opaque deck underneath prevents Android from
            // showing through or receiving those touches.
            setBackgroundColor(Color.TRANSPARENT)
            isClickable = true
            setOnTouchListener { _, _ -> true }
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
            if (enabled) {
                startLaptopHardwareKeyboard()
                startVirtualMouse()
                startRawTouchscreenReaderIfEligible()
            }
            return
        }
        if (enabled && !demoMode && !isLaptopCapableDevice()) {
            OperationLog.i(
                this,
                "LaptopMode",
                "ignored request on a non-foldable phone-sized display"
            )
            return
        }
        val frame = root ?: return
        val content = laptopContent ?: return
        val surface = surfaceView ?: return
        // Keep the full-screen logical profile so leaving laptop mode restores
        // the user's original resolution instead of the half-height pane.
        val restoreConfig = if (!enabled) laptopBaseConfig else null
        if (enabled && laptopBaseConfig == null) {
            laptopBaseConfig = Config(
                targetWidth,
                targetHeight,
                density,
                secureDisplay,
                showSystemDecorations
            )
        }
        laptopModeActive = enabled
        if (!enabled) laptopAutoActivated = false
        laptopHostUniqueId = if (enabled) defaultDisplayUniqueId() else null
        if (!enabled) {
            laptopHostMismatchSince = 0L
            laptopModeEvaluationGeneration += 1
        }
        if (enabled) {
            startLaptopHardwareKeyboard()
            startVirtualMouse()
            startRawTouchscreenReaderIfEligible()
            val deck = buildLaptopDeck().apply {
                alpha = 0f
                translationY = dp(28).toFloat()
            }
            content.addView(deck, LinearLayout.LayoutParams(-1, 0, 1f))
            deck.post {
                deck.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setInterpolator(PathInterpolator(.22f, 1f, .36f, 1f))
                    .setDuration(360L)
                    .start()
            }
            cursorView?.contentHeightFraction = .5f
            menuScrim?.bringToFront()
            menu?.bringToFront()
            performanceHud?.bringToFront()
        } else {
            finishAllLaptopKeyPresses()
            // The raw reader also owns the normal full-screen pointer surface.
            // Keep it alive when leaving the deck unless the restored surface
            // is direct-touch, in which case the physical touchscreen must
            // continue to behave as a touchscreen instead of a touchpad.
            if (directTouch) stopRawTouchscreenReader("laptop_mode_disabled_direct_touch")
            stopLaptopHardwareKeyboard()
            // In tap/direct-touch mode the pointer belongs only to the
            // laptop trackpad while the deck is visible. Remove it as soon
            // as the deck is closed so Android cannot retain its pointer
            // focus for the next app on the phone surface.
            if (directTouch) {
                cancelDesktopTouchStream()
                stopVirtualMouse()
            }
            val deck = laptopDeck
            laptopDeck = null
            laptopTrackpadView = null
            laptopFnButton = null
            deck?.animate()
                ?.cancel()
            deck?.animate()
                ?.alpha(0f)
                ?.translationY(dp(28).toFloat())
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
        applyLaptopGeometryWhenLaidOut(enabled, baseOverride = restoreConfig)
        if (!enabled) laptopBaseConfig = null
    }

    /**
     * Registers a real external keyboard with Android's input stack while the
     * laptop deck is visible. Its descriptor is associated with the Dextop
     * display before key output is enabled, so native uinput events follow the
     * physical-keyboard IME path without relying on global keyboard focus.
     */
    private fun startLaptopHardwareKeyboard() {
        if (!laptopKeyboardRequested) {
            laptopKeyboardRequested = true
            laptopKeyboardGeneration += 1
        }
        privilegedInputClient.setKeyboardVisible(true)
        scheduleLaptopKeyboardReadyCheck(laptopKeyboardGeneration, 0)
        OperationLog.i(
            this,
            "LaptopMode",
            "requested native external keyboard; waiting for InputReader publication"
        )
    }

    private fun scheduleLaptopKeyboardReadyCheck(generation: Long, attempt: Int) {
        val check = Runnable {
            if (generation != laptopKeyboardGeneration || !laptopKeyboardRequested || !active) {
                return@Runnable
            }
            val displayId = targetDisplayId
            val display = if (displayId >= 0) {
                getSystemService(DisplayManager::class.java).getDisplay(displayId)
            } else {
                null
            }
            val device = findLaptopKeyboardDevice()
            if (device != null && display != null) {
                val displayUniqueId = runCatching {
                    Display::class.java.getMethod("getUniqueId").invoke(display) as String
                }.getOrNull()
                if (laptopKeyboardReady && laptopKeyboardDeviceId == device.id &&
                    laptopKeyboardDisplayId == displayId &&
                    laptopKeyboardDisplayUniqueId == displayUniqueId &&
                    physicalInputRouter.isDeviceRouted(device, display)
                ) {
                    return@Runnable
                }
                if (physicalInputRouter.isDeviceRouted(device, display)) {
                    laptopKeyboardReady = true
                    laptopKeyboardAssociationPending = false
                    laptopKeyboardDeviceId = device.id
                    laptopKeyboardDisplayId = displayId
                    laptopKeyboardDisplayUniqueId = displayUniqueId
                    laptopKeyboardDescriptor = device.descriptor
                    val message = "native keyboard ready deviceId=${device.id} displayId=$displayId " +
                            "vendor=${device.vendorId} product=${device.productId} " +
                            "sources=0x${device.sources.toString(16)}"
                    Log.i(logTag, "$message descriptor=${device.descriptor}")
                    OperationLog.i(this, "LaptopKeyboard", message)
                    return@Runnable
                }
                laptopKeyboardReady = false
                if (!laptopKeyboardAssociationPending) {
                    laptopKeyboardAssociationPending = physicalInputRouter.routeDevice(device, display)
                }
            }
            if (attempt == 0 || attempt == 5 || attempt == 10 || attempt == 20) {
                Log.i(
                    logTag,
                    "waiting for native laptop keyboard attempt=$attempt/20 " +
                            "displayId=$displayId candidates=${laptopKeyboardPublicationCandidates()}"
                )
            }
            if (attempt < 20) {
                scheduleLaptopKeyboardReadyCheck(generation, attempt + 1)
            } else {
                OperationLog.w(
                    this,
                    "LaptopKeyboard",
                    "native keyboard was not associated with display=$displayId after ${attempt + 1} probes"
                )
            }
        }
        root?.postDelayed(check, if (attempt == 0) 40L else 100L)
            ?: Handler(mainLooper).postDelayed(check, if (attempt == 0) 40L else 100L)
    }

    private fun findLaptopKeyboardDevice(): InputDevice? = InputDevice.getDeviceIds()
        .asSequence()
        .mapNotNull(InputDevice::getDevice)
        .firstOrNull { device ->
            device.name == LAPTOP_KEYBOARD_NAME &&
                    device.vendorId == LAPTOP_KEYBOARD_VENDOR_ID &&
                    device.productId == LAPTOP_KEYBOARD_PRODUCT_ID &&
                    device.sources and InputDevice.SOURCE_KEYBOARD == InputDevice.SOURCE_KEYBOARD
        }

    private fun laptopKeyboardPublicationCandidates(): String {
        val candidates = InputDevice.getDeviceIds().asSequence()
            .mapNotNull(InputDevice::getDevice)
            .filter { it.name == LAPTOP_KEYBOARD_NAME || it.name.startsWith("Dextop Laptop") }
            .toList()
        return if (candidates.isEmpty()) {
            "none"
        } else {
            candidates.joinToString(prefix = "[", postfix = "]") { device ->
                "id=${device.id},name=${device.name},vendor=${device.vendorId}," +
                        "product=${device.productId},sources=0x${device.sources.toString(16)}"
            }
        }
    }

    private fun clearLaptopKeyboardPublication(reason: String) {
        val descriptor = laptopKeyboardDescriptor
        if (descriptor != null) physicalInputRouter.restoreDeviceDescriptor(descriptor)
        if (laptopKeyboardDeviceId >= 0 || laptopKeyboardReady) {
            Log.i(
                logTag,
                "native laptop keyboard cleared reason=$reason deviceId=$laptopKeyboardDeviceId " +
                        "displayId=$laptopKeyboardDisplayId"
            )
        }
        laptopKeyboardReady = false
        laptopKeyboardAssociationPending = false
        laptopKeyboardDeviceId = -1
        laptopKeyboardDisplayId = -1
        laptopKeyboardDisplayUniqueId = null
        laptopKeyboardDescriptor = null
    }

    private fun isMenuOverlayVisible(): Boolean {
        return menu?.visibility == View.VISIBLE && menuScrim?.isClickable == true
    }

    private fun buildPrivilegedInputConfig(profile: String): IntArray {
        val windowBounds = windowManager?.currentWindowMetrics?.bounds
        val hostWidth = root?.width?.takeIf { it > 0 }
            ?: surfaceView?.width?.takeIf { it > 0 }
            ?: windowBounds?.width()
            ?: resources.displayMetrics.widthPixels
        val hostHeight = root?.height?.takeIf { it > 0 }
            ?: surfaceView?.height?.takeIf { it > 0 }
            ?: windowBounds?.height()
            ?: resources.displayMetrics.heightPixels
        val nativeProfile = if (isMenuOverlayVisible()) {
            PrivilegedInputProtocol.PROFILE_DISABLED
        } else {
            when (profile) {
                "touchpad" -> PrivilegedInputProtocol.PROFILE_TOUCHPAD
                "mouse" -> PrivilegedInputProtocol.PROFILE_MOUSE
                else -> PrivilegedInputProtocol.PROFILE_DISABLED
            }
        }
        val candidate = PrivilegedInputProtocol.buildConfig(
            profile = nativeProfile,
            rotation = rawTouchscreenDisplayRotation(),
            hostWidth = hostWidth,
            hostHeight = hostHeight,
            fullscreen = rawTouchscreenViewBounds(surfaceView),
            trackpad = rawTouchscreenViewBounds(laptopTrackpadView),
            directTouch = directTouch,
            laptopMode = laptopModeActive,
            touchpadMaxX = VIRTUAL_TOUCHPAD_MAX_X,
            touchpadMaxY = VIRTUAL_TOUCHPAD_MAX_Y,
            touchpadResolution = VIRTUAL_TOUCHPAD_RESOLUTION,
            debugAllEvents = BuildConfig.DEBUG,
            naturalScroll = virtualMouseNaturalScroll(),
            mouseSensitivity = 1f,
            generation = 0
        )
        val previous = lastPrivilegedInputSemanticConfig
        val changed = previous == null ||
                (0 until PrivilegedInputProtocol.CONFIG_SIZE).any { index ->
                    index != PrivilegedInputProtocol.CONFIG_GENERATION &&
                            previous[index] != candidate[index]
                }
        if (changed) {
            privilegedInputConfigGeneration += 1
            candidate[PrivilegedInputProtocol.CONFIG_GENERATION] = privilegedInputConfigGeneration
            lastPrivilegedInputSemanticConfig = candidate.copyOf()
            Log.i(
                logTag,
                "privileged input semantic config changed generation=$privilegedInputConfigGeneration " +
                        "profile=$profile rotation=${candidate[PrivilegedInputProtocol.CONFIG_ROTATION]} " +
                        "host=${candidate[PrivilegedInputProtocol.CONFIG_HOST_WIDTH]}x" +
                        candidate[PrivilegedInputProtocol.CONFIG_HOST_HEIGHT]
            )
        } else {
            candidate[PrivilegedInputProtocol.CONFIG_GENERATION] =
                checkNotNull(previous)[PrivilegedInputProtocol.CONFIG_GENERATION]
        }
        return candidate
    }

    private fun refreshPrivilegedInputConfig(reason: String) {
        val profile = virtualPointerRegisteredProfile.ifBlank { activeVirtualPointerProfile() }
        if (profile != "touchpad" && profile != "mouse") return
        privilegedInputClient.updateConfig(buildPrivilegedInputConfig(profile))
        Log.i(
            logTag,
            "privileged input geometry updated reason=$reason " +
                    displayGeometrySnapshot("privileged_input_config")
        )
    }

    /**
     * Registers the selected kernel-backed pointer. The mouse profile reports
     * relative motion; the touchpad profile exposes Linux MT Type-B contacts
     * and lets Android's TouchpadInputMapper own acceleration and gestures.
     */
    private fun startVirtualMouse(profileOverride: String? = null) {
        val profile = normalizeVirtualPointerProfile(profileOverride ?: activeVirtualPointerProfile())
        // Never leave a kernel pointer behind for the phone surface in tap
        // mode. The laptop deck is an explicit exception: its trackpad is a
        // separate input surface and may attach the pointer while the phone
        // surface remains in direct-touch mode. This guard is intentionally
        // checked at the connection boundary (rather than only in
        // virtualMouseInputActive()) so switching modes actually removes the
        // device from InputReader/InputDispatcher.
        if (!active || demoMode || profile == "software" ||
            (directTouch && !laptopModeActive)
        ) {
            updateVirtualCursorVisibility()
            return
        }
        if (virtualMouseProcessAlive() && virtualPointerRegisteredProfile == profile) {
            privilegedInputClient.updateConfig(buildPrivilegedInputConfig(profile))
            updateVirtualCursorVisibility()
            return
        }
        // Hide first, before tearing down a previous registration.  Without
        // this ordering the software cursor can be drawn for one frame in the
        // gap between stopVirtualMouse() and assigning the new uinput process.
        cursorView?.visibility = View.GONE
        stopVirtualMouse()
        virtualMouseReady = false
        virtualPointerRegisteredProfile = profile
        privilegedInputStarting = true
        virtualMouseFractionX = 0f
        virtualMouseFractionY = 0f
        updateVirtualCursorVisibility()
        privilegedInputClient.start(buildPrivilegedInputConfig(profile))
        val message = "binding native privileged input profile=$profile generation=$virtualMouseGeneration"
        OperationLog.i(this, "InputRouting", message)
        Log.i(logTag, message)
    }

    private fun stopVirtualMouse() {
        val stoppedProfile = virtualPointerRegisteredProfile
        val stoppedDeviceId = virtualMouseDeviceId
        val activeContacts = virtualTouchpadActiveContactCount()
        stopRawTouchscreenReader("virtual_pointer_stopped")
        if (stoppedProfile.isNotBlank()) {
            Log.i(
                logTag,
                "stopping virtual pointer profile=$stoppedProfile deviceId=$stoppedDeviceId " +
                        "ready=$virtualMouseReady activeContacts=$activeContacts generation=$virtualMouseGeneration"
            )
        }
        virtualMouseGeneration += 1
        virtualMouseReady = false
        virtualMouseDeviceId = -1
        virtualPointerRegisteredProfile = ""
        privilegedInputStarting = false
        privilegedInputClient.setOutputReady(false)
        privilegedInputClient.stopEngine("virtual_pointer_stopped")
        virtualMouseFractionX = 0f
        virtualMouseFractionY = 0f
        virtualMouseWheelFractionX = 0f
        virtualMouseWheelFractionY = 0f
        resetVirtualTouchpadState("pointer_stopped", logSummary = activeContacts > 0)
    }

    /**
     * A Shizuku-compatible binder does not guarantee that UserService or
     * uinput is available on every provider/firmware. Keep the session usable
     * when the privileged path cannot start: use the existing MotionEvent
     * software cursor for this session without overwriting the user's saved
     * pointer preference. The next Dextop session probes the preferred native
     * profile again.
     */
    private fun fallbackToSoftwarePointer(reason: String) {
        if (privilegedPointerFallbackInProgress || privilegedPointerFallbackActive) return
        if (!active || directTouch && !laptopModeActive) {
            privilegedInputStarting = false
            updateVirtualCursorVisibility()
            return
        }
        privilegedPointerFallbackInProgress = true
        try {
            stopVirtualMouse()
            virtualPointerRuntimeProfile = "software"
            privilegedPointerFallbackActive = true
            privilegedInputStarting = false
            virtualMouseReady = false
            cursorView?.apply {
                visibility = if (directTouch) View.GONE else View.VISIBLE
                if (!directTouch) bringToFront()
            }
            updateVirtualCursorVisibility()
            val message = "privileged pointer unavailable; using session software cursor reason=$reason"
            OperationLog.w(this, "InputRouting", message)
            Log.w(logTag, message)
        } finally {
            privilegedPointerFallbackInProgress = false
        }
    }

    private fun scheduleVirtualMouseReadyCheck(generation: Long, profile: String, attempt: Int) {
        val check = Runnable {
            if (generation != virtualMouseGeneration || !active ||
                activeVirtualPointerProfile() != profile
            ) return@Runnable
            if (!virtualMouseProcessAlive()) {
                OperationLog.w(this, "InputRouting", "virtual mouse process exited before InputReader registration")
                fallbackToSoftwarePointer("native_process_unavailable_before_publication")
                return@Runnable
            }
            val device = findVirtualPointerDevice(profile)
            if (device != null) {
                virtualMouseDeviceId = device.id
                virtualMouseReady = true
                privilegedInputClient.setOutputReady(true)
                refreshPrivilegedInputConfig("input_reader_ready")
                updateVirtualCursorVisibility()
                val deviceDetails = virtualPointerDeviceDetails(device)
                OperationLog.i(
                    this,
                    "InputRouting",
                    "virtual pointer ready profile=$profile deviceId=${device.id}; framework routing active " +
                            displayGeometrySnapshot("virtual_mouse_ready") + " native=" +
                            privilegedInputClient.snapshot()
                )
                Log.i(logTag, "virtual pointer ready profile=$profile $deviceDetails")
                startRawTouchscreenReaderIfEligible()
                return@Runnable
            }
            if (attempt == 0 || attempt == 5 || attempt == 10 || attempt == 15) {
                val candidates = virtualPointerPublicationCandidates(profile)
                Log.i(
                    logTag,
                    "waiting for InputReader profile=$profile attempt=$attempt/15 generation=$generation " +
                            "candidates=$candidates"
                )
            }
            if (attempt < 15) {
                scheduleVirtualMouseReadyCheck(generation, profile, attempt + 1)
            } else {
                OperationLog.w(
                    this,
                    "InputRouting",
                    "uinput profile=$profile was not published by InputReader after ${attempt + 1} probes"
                )
                Log.w(
                    logTag,
                    "uinput profile=$profile was not published by InputReader; " +
                            "candidates=${virtualPointerPublicationCandidates(profile)}"
                )
                fallbackToSoftwarePointer("input_reader_publication_timeout:$profile")
            }
        }
        // During the first display setup the root window may not have been
        // attached yet. Always keep the readiness probe alive on the main
        // looper so startup ordering cannot leave the cursor permanently in a
        // half-initialized state.
        root?.postDelayed(check, if (attempt == 0) 120L else 100L)
            ?: Handler(mainLooper).postDelayed(check, if (attempt == 0) 120L else 100L)
    }

    private fun findVirtualPointerDevice(profile: String): InputDevice? = InputDevice.getDeviceIds()
        .asSequence()
        .mapNotNull { InputDevice.getDevice(it) }
        .firstOrNull { device ->
            device.name == virtualPointerDeviceName(profile) && when (profile) {
                "touchpad" -> device.sources and InputDevice.SOURCE_TOUCHPAD == InputDevice.SOURCE_TOUCHPAD
                else -> device.sources and InputDevice.SOURCE_MOUSE == InputDevice.SOURCE_MOUSE
            }
        }

    private fun virtualPointerPublicationCandidates(profile: String): String {
        val expectedName = virtualPointerDeviceName(profile)
        val candidates = InputDevice.getDeviceIds().asSequence()
            .mapNotNull { id ->
                InputDevice.getDevice(id)?.takeIf { device ->
                    device.name == expectedName || device.name.startsWith("Dextop Virtual")
                }
            }
            .toList()
        return if (candidates.isEmpty()) {
            "none"
        } else {
            candidates.joinToString(prefix = "[", postfix = "]") { device ->
                "id=${device.id},name=${device.name},sources=0x${device.sources.toString(16)}"
            }
        }
    }

    private fun virtualPointerDeviceDetails(device: InputDevice): String {
        val ranges = device.motionRanges.joinToString(prefix = "[", postfix = "]") { range ->
            "axis=${MotionEvent.axisToString(range.axis)},min=${range.min},max=${range.max}," +
                    "resolution=${range.resolution},source=0x${range.source.toString(16)}"
        }
        return "deviceId=${device.id} name=${device.name} sources=0x${device.sources.toString(16)} " +
                "external=${device.isExternal} ranges=$ranges"
    }

    private fun applyVirtualPointerProfile(requested: String) {
        val profile = normalizeVirtualPointerProfile(requested)
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putString(KEY_VIRTUAL_POINTER_PROFILE, profile)
            .putBoolean(KEY_SOFTWARE_CURSOR_FALLBACK, profile == "software")
            .apply()
        virtualPointerRuntimeProfile = null
        if (profile != "software") {
            cancelDesktopTouchStream()
            stopVirtualMouse()
            startVirtualMouse()
            updateVirtualCursorVisibility()
        } else {
            cancelDesktopTouchStream()
            stopVirtualMouse()
            updateVirtualCursorVisibility()
        }
        OperationLog.i(
            this,
            "InputRouting",
            "virtual pointer profile=$profile active=${virtualMouseInputActive()}"
        )
        menuPrimary?.let(::showMainMenu)
    }

    private fun virtualTouchpadActiveContactCount(): Int =
        virtualTouchpadSlotPointerIds.count { it >= 0 }

    private fun resetVirtualTouchpadState(reason: String, logSummary: Boolean = false) {
        if (logSummary) {
            val duration = if (virtualTouchpadGestureStartedAt > 0L) {
                (SystemClock.uptimeMillis() - virtualTouchpadGestureStartedAt).coerceAtLeast(0L)
            } else {
                0L
            }
            Log.i(
                logTag,
                "touchpad gesture reset reason=$reason sequence=$virtualTouchpadGestureSequence " +
                        "durationMs=$duration frames=$virtualTouchpadFrameCount " +
                        "contactUpdates=$virtualTouchpadContactUpdateCount " +
                        "activeContacts=${virtualTouchpadActiveContactCount()}"
            )
        }
        virtualTouchpadSlotPointerIds.fill(-1)
        virtualTouchpadSlotTrackingIds.fill(-1)
        virtualTouchpadGestureStartedAt = 0L
        virtualTouchpadFrameCount = 0
        virtualTouchpadContactUpdateCount = 0
        virtualTouchpadLastMoveLogAt = 0L
        nativeTouchpadGestureActive = false
    }

    private fun allocateVirtualTouchpadSlot(pointerId: Int): Int? {
        val existing = virtualTouchpadSlotPointerIds.indexOf(pointerId)
        if (existing >= 0) return existing
        val slot = virtualTouchpadSlotPointerIds.indexOfFirst { it < 0 }
        if (slot < 0) {
            OperationLog.w(
                this,
                "InputRouting",
                "touchpad slot allocation failed pointerId=$pointerId maxSlots=$VIRTUAL_TOUCHPAD_MAX_SLOTS"
            )
            Log.w(
                logTag,
                "touchpad slot allocation failed pointerId=$pointerId " +
                        "slots=${virtualTouchpadSlotPointerIds.contentToString()}"
            )
            return null
        }
        val trackingId = virtualTouchpadNextTrackingId
        virtualTouchpadNextTrackingId = if (trackingId >= 65534) 1 else trackingId + 1
        virtualTouchpadSlotPointerIds[slot] = pointerId
        virtualTouchpadSlotTrackingIds[slot] = trackingId
        return slot
    }

    private fun virtualTouchpadPosition(
        event: MotionEvent,
        pointerIndex: Int,
        sourceView: View
    ): Pair<Int, Int>? {
        if (sourceView.width <= 0 || sourceView.height <= 0) {
            OperationLog.w(
                this,
                "InputRouting",
                "touchpad event dropped because source surface has invalid size " +
                        "width=${sourceView.width} height=${sourceView.height}"
            )
            Log.w(
                logTag,
                "touchpad event dropped: invalid source size ${sourceView.width}x${sourceView.height}"
            )
            return null
        }
        val x = (event.getX(pointerIndex) / sourceView.width.toFloat() * VIRTUAL_TOUCHPAD_MAX_X)
            .roundToInt().coerceIn(0, VIRTUAL_TOUCHPAD_MAX_X)
        val y = (event.getY(pointerIndex) / sourceView.height.toFloat() * VIRTUAL_TOUCHPAD_MAX_Y)
            .roundToInt().coerceIn(0, VIRTUAL_TOUCHPAD_MAX_Y)
        return x to y
    }

    private fun appendVirtualTouchpadContact(
        events: MutableList<Any>,
        event: MotionEvent,
        pointerIndex: Int,
        sourceView: View,
        includeTrackingId: Boolean
    ): Boolean {
        val pointerId = event.getPointerId(pointerIndex)
        val slot = allocateVirtualTouchpadSlot(pointerId) ?: return false
        val position = virtualTouchpadPosition(event, pointerIndex, sourceView) ?: return false
        events += "EV_ABS"; events += "ABS_MT_SLOT"; events += slot
        if (includeTrackingId) {
            events += "EV_ABS"; events += "ABS_MT_TRACKING_ID"
            events += virtualTouchpadSlotTrackingIds[slot]
        }
        events += "EV_ABS"; events += "ABS_MT_POSITION_X"; events += position.first
        events += "EV_ABS"; events += "ABS_MT_POSITION_Y"; events += position.second
        events += "EV_ABS"; events += "ABS_MT_TOUCH_MAJOR"; events += VIRTUAL_TOUCHPAD_TOUCH_MAJOR
        events += "EV_ABS"; events += "ABS_MT_PRESSURE"; events += VIRTUAL_TOUCHPAD_PRESSURE
        virtualTouchpadContactUpdateCount += 1
        return true
    }

    private fun finishVirtualTouchpadGesture(
        reason: String,
        allowDirectTouch: Boolean,
        sendToDevice: Boolean = true
    ): Boolean {
        val activeSlots = virtualTouchpadSlotPointerIds.indices
            .filter { virtualTouchpadSlotPointerIds[it] >= 0 }
        val sequence = virtualTouchpadGestureSequence
        val frames = virtualTouchpadFrameCount
        val updates = virtualTouchpadContactUpdateCount
        val startedAt = virtualTouchpadGestureStartedAt
        val events = mutableListOf<Any>()
        activeSlots.forEach { slot ->
            events += "EV_ABS"; events += "ABS_MT_SLOT"; events += slot
            events += "EV_ABS"; events += "ABS_MT_TRACKING_ID"; events += -1
        }
        if (activeSlots.isNotEmpty()) {
            events += "EV_KEY"; events += "BTN_TOUCH"; events += 0
            events += "EV_SYN"; events += "SYN_REPORT"; events += 0
        }
        val sent = activeSlots.isEmpty() || !sendToDevice ||
                virtualTouchpadEvents(events, allowDirectTouch)
        resetVirtualTouchpadState(reason)
        val duration = if (startedAt > 0L) {
            (SystemClock.uptimeMillis() - startedAt).coerceAtLeast(0L)
        } else {
            0L
        }
        val summary = "touchpad gesture finished reason=$reason sequence=$sequence durationMs=$duration " +
                "frames=$frames contactUpdates=$updates releasedSlots=${activeSlots.joinToString()} sent=$sent"
        OperationLog.i(this, "InputRouting", summary)
        Log.i(logTag, summary)
        return sent
    }

    private fun virtualTouchpadEvents(
        events: List<Any>,
        allowDirectTouch: Boolean
    ): Boolean {
        if (virtualPointerRegisteredProfile != "touchpad") {
            Log.w(
                logTag,
                "touchpad frame rejected: registeredProfile=$virtualPointerRegisteredProfile " +
                        "eventTriples=${events.size / 3}"
            )
            return false
        }
        return virtualMouseEvents(events, allowDirectTouch)
    }

    /** Bridges an Android MotionEvent to a Linux multitouch Type-B frame. */
    private fun virtualTouchpadMotionEvent(
        event: MotionEvent,
        sourceView: View,
        allowDirectTouch: Boolean
    ): Boolean {
        if (!virtualPointerInputActive(allowDirectTouch) ||
            virtualPointerRegisteredProfile != "touchpad"
        ) {
            Log.w(
                logTag,
                "touchpad event unavailable action=${MotionEvent.actionToString(event.action)} " +
                        "ready=$virtualMouseReady processAlive=${virtualMouseProcessAlive()} " +
                        "registeredProfile=$virtualPointerRegisteredProfile"
            )
            resetVirtualTouchpadState("pointer_unavailable", logSummary = true)
            return false
        }
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_CANCEL) {
            return finishVirtualTouchpadGesture("action_cancel", allowDirectTouch)
        }
        if (action == MotionEvent.ACTION_DOWN) {
            if (virtualTouchpadActiveContactCount() > 0) {
                finishVirtualTouchpadGesture("unexpected_action_down", allowDirectTouch)
            }
            virtualTouchpadGestureSequence += 1
            virtualTouchpadGestureStartedAt = SystemClock.uptimeMillis()
            virtualTouchpadFrameCount = 0
            virtualTouchpadContactUpdateCount = 0
        }

        val events = mutableListOf<Any>()
        var finishReason: String? = null
        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val wasEmpty = virtualTouchpadActiveContactCount() == 0
                val actionIndex = event.actionIndex
                if (!appendVirtualTouchpadContact(
                        events,
                        event,
                        actionIndex,
                        sourceView,
                        includeTrackingId = true
                    )
                ) {
                    finishVirtualTouchpadGesture("contact_down_failed", allowDirectTouch)
                    return false
                }
                if (wasEmpty) {
                    events += "EV_KEY"; events += "BTN_TOUCH"; events += 1
                }
                val pointerId = event.getPointerId(actionIndex)
                val slot = virtualTouchpadSlotPointerIds.indexOf(pointerId)
                val position = virtualTouchpadPosition(event, actionIndex, sourceView)
                val message = "touchpad contact down sequence=$virtualTouchpadGestureSequence " +
                        "pointerId=$pointerId slot=$slot trackingId=${virtualTouchpadSlotTrackingIds[slot]} " +
                        "position=$position pointers=${event.pointerCount} source=${sourceView.width}x${sourceView.height}"
                OperationLog.i(
                    this,
                    "InputRouting",
                    "touchpad contact down sequence=$virtualTouchpadGestureSequence pointerId=$pointerId " +
                            "slot=$slot trackingId=${virtualTouchpadSlotTrackingIds[slot]} " +
                            "pointers=${event.pointerCount}"
                )
                Log.i(logTag, message)
            }

            MotionEvent.ACTION_MOVE -> {
                for (index in 0 until event.pointerCount) {
                    val pointerId = event.getPointerId(index)
                    val isNewContact = virtualTouchpadSlotPointerIds.indexOf(pointerId) < 0
                    if (!appendVirtualTouchpadContact(
                            events,
                            event,
                            index,
                            sourceView,
                            includeTrackingId = isNewContact
                        )
                    ) {
                        finishVirtualTouchpadGesture("contact_move_failed", allowDirectTouch)
                        return false
                    }
                    if (isNewContact) {
                        Log.w(logTag, "touchpad recovered missing contact pointerId=$pointerId during MOVE")
                    }
                }
            }

            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                val actionIndex = event.actionIndex
                for (index in 0 until event.pointerCount) {
                    if (index == actionIndex) continue
                    if (!appendVirtualTouchpadContact(
                            events,
                            event,
                            index,
                            sourceView,
                            includeTrackingId = false
                        )
                    ) {
                        finishVirtualTouchpadGesture("contact_up_update_failed", allowDirectTouch)
                        return false
                    }
                }
                val pointerId = event.getPointerId(actionIndex)
                val slot = virtualTouchpadSlotPointerIds.indexOf(pointerId)
                if (slot >= 0) {
                    events += "EV_ABS"; events += "ABS_MT_SLOT"; events += slot
                    events += "EV_ABS"; events += "ABS_MT_TRACKING_ID"; events += -1
                    virtualTouchpadSlotPointerIds[slot] = -1
                    virtualTouchpadSlotTrackingIds[slot] = -1
                } else {
                    Log.w(logTag, "touchpad contact up missing pointerId=$pointerId")
                }
                if (virtualTouchpadActiveContactCount() == 0) {
                    events += "EV_KEY"; events += "BTN_TOUCH"; events += 0
                    finishReason = if (action == MotionEvent.ACTION_UP) "action_up" else "last_pointer_up"
                }
                OperationLog.i(
                    this,
                    "InputRouting",
                    "touchpad contact up sequence=$virtualTouchpadGestureSequence pointerId=$pointerId " +
                            "slot=$slot remaining=${virtualTouchpadActiveContactCount()}"
                )
                Log.i(
                    logTag,
                    "touchpad contact up sequence=$virtualTouchpadGestureSequence pointerId=$pointerId " +
                            "slot=$slot remaining=${virtualTouchpadActiveContactCount()}"
                )
            }

            else -> return true
        }
        events += "EV_SYN"; events += "SYN_REPORT"; events += 0
        val sent = virtualTouchpadEvents(events, allowDirectTouch)
        if (!sent) {
            Log.w(
                logTag,
                "touchpad frame send failed action=${MotionEvent.actionToString(event.action)} " +
                        "sequence=$virtualTouchpadGestureSequence triples=${events.size / 3}"
            )
            resetVirtualTouchpadState("frame_send_failed", logSummary = true)
            return false
        }
        virtualTouchpadFrameCount += 1
        if (action == MotionEvent.ACTION_MOVE) {
            val now = SystemClock.uptimeMillis()
            if (now - virtualTouchpadLastMoveLogAt >= VIRTUAL_TOUCHPAD_MOVE_LOG_INTERVAL_MS) {
                virtualTouchpadLastMoveLogAt = now
                val contacts = (0 until event.pointerCount).joinToString(prefix = "[", postfix = "]") { index ->
                    val pointerId = event.getPointerId(index)
                    val slot = virtualTouchpadSlotPointerIds.indexOf(pointerId)
                    val position = virtualTouchpadPosition(event, index, sourceView)
                    "pointerId=$pointerId,slot=$slot,position=$position"
                }
                Log.d(
                    logTag,
                    "touchpad move sequence=$virtualTouchpadGestureSequence frame=$virtualTouchpadFrameCount " +
                            "pointers=${event.pointerCount} contacts=$contacts"
                )
            }
        }
        if (finishReason != null) {
            val sequence = virtualTouchpadGestureSequence
            val frames = virtualTouchpadFrameCount
            val updates = virtualTouchpadContactUpdateCount
            val duration = (SystemClock.uptimeMillis() - virtualTouchpadGestureStartedAt)
                .coerceAtLeast(0L)
            resetVirtualTouchpadState(finishReason)
            val summary = "touchpad gesture finished reason=$finishReason sequence=$sequence " +
                    "durationMs=$duration frames=$frames contactUpdates=$updates releasedSlots=event sent=true"
            OperationLog.i(this, "InputRouting", summary)
            Log.i(logTag, summary)
        }
        return true
    }

    private fun logSuppressedRelativeTouchpadEvent(kind: String) {
        val now = SystemClock.uptimeMillis()
        if (now - virtualPointerLastUnsupportedEventLogAt < 1_000L) return
        virtualPointerLastUnsupportedEventLogAt = now
        val message = "suppressed $kind for native touchpad profile; MT contacts must own motion and scrolling"
        OperationLog.w(this, "InputRouting", message)
        Log.w(logTag, message)
    }

    private fun virtualMouseEvents(
        events: List<Any>,
        allowDirectTouch: Boolean = false
    ): Boolean {
        if (!virtualPointerInputActive(allowDirectTouch)) return false
        if (events.size % 3 != 0) {
            Log.e(logTag, "invalid primitive event frame size=${events.size}")
            return false
        }
        val encoded = IntArray(events.size)
        for (index in events.indices step 3) {
            val typeName = events[index] as? String ?: return false
            val codeName = events[index + 1] as? String ?: return false
            val value = events[index + 2] as? Number ?: return false
            encoded[index] = when (typeName) {
                "EV_SYN" -> 0
                "EV_KEY" -> 1
                "EV_REL" -> 2
                "EV_ABS" -> 3
                else -> return false
            }
            encoded[index + 1] = when (codeName) {
                "SYN_REPORT" -> 0
                "REL_X" -> 0
                "REL_Y" -> 1
                "REL_HWHEEL" -> 6
                "REL_WHEEL" -> 8
                "BTN_LEFT" -> 272
                "BTN_RIGHT" -> 273
                "BTN_TOUCH" -> 330
                "ABS_MT_SLOT" -> 47
                "ABS_MT_TOUCH_MAJOR" -> 48
                "ABS_MT_POSITION_X" -> 53
                "ABS_MT_POSITION_Y" -> 54
                "ABS_MT_TRACKING_ID" -> 57
                "ABS_MT_PRESSURE" -> 58
                else -> return false
            }
            encoded[index + 2] = value.toInt()
        }
        val sent = privilegedInputClient.inject(encoded)
        if (!sent) Log.w(logTag, "privileged primitive injection rejected events=${events.size / 3}")
        return sent
    }

    private fun virtualMouseMove(
        dx: Float,
        dy: Float,
        allowDirectTouch: Boolean = false
    ): Boolean {
        if (!virtualPointerInputActive(allowDirectTouch)) return false
        if (virtualPointerRegisteredProfile == "touchpad") {
            logSuppressedRelativeTouchpadEvent("REL_X/REL_Y movement")
            return false
        }
        virtualMouseFractionX += dx
        virtualMouseFractionY += dy
        val x = virtualMouseFractionX.toInt()
        val y = virtualMouseFractionY.toInt()
        if (x == 0 && y == 0) return true
        virtualMouseFractionX -= x
        virtualMouseFractionY -= y
        val events = mutableListOf<Any>()
        if (x != 0) {
            events += "EV_REL"; events += "REL_X"; events += x
        }
        if (y != 0) {
            events += "EV_REL"; events += "REL_Y"; events += y
        }
        events += "EV_SYN"; events += "SYN_REPORT"; events += 0
        return virtualMouseEvents(events, allowDirectTouch)
    }

    private fun virtualMouseButton(
        button: String,
        pressed: Boolean,
        allowDirectTouch: Boolean = false
    ): Boolean =
        virtualMouseEvents(
            listOf(
                "EV_KEY", button, if (pressed) 1 else 0,
                "EV_SYN", "SYN_REPORT", 0
            ),
            allowDirectTouch
        )

    private fun virtualMouseScroll(delta: Float, allowDirectTouch: Boolean = false): Boolean {
        if (virtualPointerRegisteredProfile == "touchpad") {
            logSuppressedRelativeTouchpadEvent("REL_WHEEL scrolling")
            return false
        }
        val direction = if (virtualMouseNaturalScroll()) 1f else -1f
        // Keep the proven wheel quantization.  Sending steps too frequently
        // makes Android render the scroll as visible bursts rather than a
        // steady gesture.
        virtualMouseWheelFractionY += -delta * direction / dp(12).toFloat()
        val wheel = virtualMouseWheelFractionY.toInt().coerceIn(-12, 12)
        if (wheel == 0) return true
        virtualMouseWheelFractionY -= wheel
        return virtualMouseEvents(
            listOf(
                "EV_REL", "REL_WHEEL", wheel,
                "EV_SYN", "SYN_REPORT", 0
            ),
            allowDirectTouch
        )
    }

    private fun virtualMouseHorizontalScroll(
        delta: Float,
        allowDirectTouch: Boolean = false
    ): Boolean {
        if (virtualPointerRegisteredProfile == "touchpad") {
            logSuppressedRelativeTouchpadEvent("REL_HWHEEL scrolling")
            return false
        }
        val direction = if (virtualMouseNaturalScroll()) 1f else -1f
        // REL_HWHEEL follows the same selected direction as vertical scroll.
        virtualMouseWheelFractionX += delta * direction / dp(12).toFloat()
        val wheel = virtualMouseWheelFractionX.toInt().coerceIn(-12, 12)
        if (wheel == 0) return true
        virtualMouseWheelFractionX -= wheel
        return virtualMouseEvents(
            listOf(
                "EV_REL", "REL_HWHEEL", wheel,
                "EV_SYN", "SYN_REPORT", 0
            ),
            allowDirectTouch
        )
    }

    private fun stopLaptopHardwareKeyboard() {
        finishAllLaptopKeyPresses()
        laptopKeyboardRequested = false
        laptopKeyboardGeneration += 1
        clearLaptopKeyboardPublication("laptop_keyboard_stopped")
        privilegedInputClient.setKeyboardVisible(false)
    }

    private fun leaveLaptopModeOnCoverDisplay(): Boolean {
        if (!laptopModeActive || isDebugLaptopModeForced()) return false
        val originalHost = laptopHostUniqueId ?: return false
        val currentHost = defaultDisplayUniqueId() ?: return false
        if (originalHost == currentHost || !hasStableLaptopHostMismatch()) return false
        laptopManualOverride = false
        laptopAutoSuppressedByUser = false
        laptopAutoActivated = false
        OperationLog.i(
            this,
            "LaptopMode",
            "cover display detected; removing keyboard and restoring full display geometry"
        )
        setLaptopMode(false)
        return true
    }

    /**
     * Fold/unfold transitions can publish a temporary default display while
     * the new panel is being attached. Do not tear down the laptop deck until
     * the host identity has remained different for a full handoff window.
     */
    private fun hasStableLaptopHostMismatch(): Boolean {
        val original = laptopHostUniqueId ?: return false
        val current = defaultDisplayUniqueId() ?: return false
        if (current == original) {
            laptopHostMismatchSince = 0L
            return false
        }
        val now = SystemClock.uptimeMillis()
        if (laptopHostMismatchSince == 0L) {
            laptopHostMismatchSince = now
            return false
        }
        return now - laptopHostMismatchSince >= laptopHostMismatchDebounceMs
    }

    private fun isLaptopHingeAngle(angle: Float): Boolean = if (laptopModeActive) {
        angle in 45f..155f
    } else {
        angle in 55f..145f
    }

    /**
     * Resolve posture through the device-specific path. Fold8 keeps the
     * geometry fallback that protects it from stale WindowManager updates;
     * normal-size Fold devices use the FoldingFeature half-open state directly
     * because their hinge orientation flag is not reliable for this layout.
     */
    private fun currentLaptopPosture(): Boolean? {
        val angle = filteredHingeAngle ?: hingeAngle
        return when (laptopFoldProfile()) {
            LaptopFoldProfile.FOLD8 -> currentFold8LaptopPosture(angle)
            LaptopFoldProfile.STANDARD_FOLDABLE -> currentStandardFoldPosture(angle)
        }
    }

    private fun currentFold8LaptopPosture(angle: Float?): Boolean? {
        // WindowManager is the stable posture source on Samsung foldables.
        // The public hinge sensor can deliver one stale 180° sample while the
        // inner panel is being attached; letting that sample override a
        // confirmed HALF_OPENED feature breaks first-start detection. Use the
        // sensor only when the folding API is unavailable.
        if (foldingApiLaptopPosture == true) {
            // Once the laptop deck is active, a stale HALF_OPENED result must
            // not survive a real return to the full-height host. Samsung can
            // omit the API transition, but the host geometry still exposes
            // that the lower pane has been restored.
            // While the user-dismissal latch is active, the Samsung hinge
            // sensor is not allowed to clear it: this device can keep a stale
            // 180-degree sample while FoldingFeature still says HALF_OPENED.
            // Only a confirmed API transition to flat (the branch below) may
            // release the latch. The geometry fallback remains for an already
            // visible deck whose API transition is delayed.
            if (laptopModeActive && angle != null &&
                !isLaptopHingeAngle(angle) && laptopHostIsFullHeight()
            ) {
                return false
            }
            return true
        }
        foldingApiLaptopPosture?.let { return it }
        if (angle != null) {
            return isLaptopHingeAngle(angle)
        }
        return null
    }

    private fun currentStandardFoldPosture(angle: Float?): Boolean? {
        // Fold7 and earlier normal-size Folds can report a vertical
        // FoldingFeature orientation while the device is already in the
        // supported half-open posture. The posture state itself is reliable,
        // so prefer it and only use the sensor when the API is unavailable.
        foldingApiLaptopPosture?.let { return it }
        if (angle != null) return isLaptopHingeAngle(angle)
        return null
    }

    private fun laptopHostIsFullHeight(): Boolean {
        val fullHeight = laptopBaseConfig?.height ?: return false
        val hostHeight = surfaceView?.height ?: return false
        if (fullHeight <= 0 || hostHeight <= 0) return false
        return hostHeight >= (fullHeight * 0.78f).toInt()
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
            // TYPE_HINGE_ANGLE is on-change on Samsung. A complete open/close
            // can therefore arrive as a single large jump; smoothing that
            // jump by 25% leaves it on the old side of the threshold forever.
            // Snap large posture changes, while still filtering small sensor
            // noise around the flex boundary.
            if (abs(angle - previous) >= 12f) angle
            else previous + (angle - previous) * 0.25f
        } ?: angle
        filteredHingeAngle = filtered
        hingeAngle = angle
        refreshFoldingApiState("hinge_candidate")
        evaluateLaptopModeForPosture(
            currentLaptopPosture() ?: false,
            "hinge angle=$filtered raw=$angle"
        )
    }

    /** Re-evaluate from the folding API when no new sensor sample was sent. */
    private fun updateLaptopModeFromCurrentPosture(reason: String) {
        if (!active || root == null || suspendedForLockScreen) return
        if (!isLaptopAutoDetectionEnabled()) return
        val posture = currentLaptopPosture() ?: return
        evaluateLaptopModeForPosture(posture, reason)
    }

    private fun evaluateLaptopModeForPosture(laptopPosture: Boolean, source: String) {
        // A manual overlay disable is scoped to the current posture. Once the
        // hinge is flat again, the next flex transition may be auto-detected
        // normally. Clear this before computing shouldShow so the same sample
        // cannot re-enable a deck the user just dismissed.
        if (!laptopPosture && laptopAutoSuppressedByUser) {
            laptopAutoSuppressedByUser = false
            OperationLog.i(this, "LaptopMode", "$source flat posture clears manual auto-suppression")
        }
        // A half-open hinge is itself authoritative: inactive inner panels are
        // omitted from DisplayManager on several foldables and the emulator.
        val mainDisplay = laptopPosture || isFoldableMainDisplay()
        if (!mainDisplay) laptopAutoActivated = false
        // Automatic laptop mode is limited to portrait holding orientation on
        // Fold8-style devices. Landscape sessions can still be enabled from
        // the overlay; that explicit manual flag is preserved here.
        val autoShouldShow = !laptopAutoSuppressedByUser &&
                isLaptopAutoOrientationEligible() && laptopPosture
        // Posture owns only automatically activated laptop mode. An explicit
        // user enable remains authoritative until the user disables it or the
        // host really moves to the cover display (handled by the independent
        // stable host-mismatch guard). Foldables can transiently omit their
        // inactive inner panel while unfolding, which must not revoke intent.
        val shouldShow = laptopManualOverride || (mainDisplay && autoShouldShow)
        if (shouldShow == laptopModeActive) {
            pendingLaptopMode = null
            pendingLaptopModeSince = 0L
            return
        }
        val now = SystemClock.uptimeMillis()
        if (pendingLaptopMode != shouldShow) {
            pendingLaptopMode = shouldShow
            pendingLaptopModeSince = now
            val generation = ++laptopModeEvaluationGeneration
            android.os.Handler(mainLooper).postDelayed({
                if (generation != laptopModeEvaluationGeneration || !active ||
                    suspendedForLockScreen || pendingLaptopMode != shouldShow
                ) return@postDelayed
                val stableAngle = filteredHingeAngle ?: hingeAngle
                val stablePosture = currentLaptopPosture() ?: return@postDelayed
                if (stablePosture != shouldShow && !laptopManualOverride) {
                    pendingLaptopMode = null
                    pendingLaptopModeSince = 0L
                    return@postDelayed
                }
                pendingLaptopMode = null
                pendingLaptopModeSince = 0L
                laptopAutoActivated = shouldShow && !laptopManualOverride
                OperationLog.i(
                    this,
                    "LaptopMode",
                    "$source timer angle=$stableAngle manual=$laptopManualOverride " +
                            "main=$mainDisplay show=$shouldShow"
                )
                setLaptopMode(shouldShow)
            }, laptopModeDebounceMs)
            return
        }
        if (now - pendingLaptopModeSince >= laptopModeDebounceMs) {
            pendingLaptopMode = null
            pendingLaptopModeSince = 0L
            laptopAutoActivated = shouldShow && !laptopManualOverride
            OperationLog.i(
                this,
                "LaptopMode",
                "$source manual=$laptopManualOverride main=$mainDisplay show=$shouldShow"
            )
            setLaptopMode(shouldShow)
        }
    }

    private fun applyFlutterLaptopModeSetting(enabled: Boolean) {
        if (!enabled) {
            if (laptopAutoActivated && !laptopManualOverride) setLaptopMode(false)
            return
        }
        hingeAngle?.let(::updateLaptopModeForHinge)
    }

    private fun applyLaptopGeometryWhenLaidOut(
        enabled: Boolean,
        attempt: Int = 0,
        baseOverride: Config? = null
    ) {
        val content = laptopContent ?: return
        val surface = surfaceView ?: return
        content.postDelayed({
            if (laptopModeActive != enabled) return@postDelayed
            if (!active) {
                if (attempt < 20) applyLaptopGeometryWhenLaidOut(enabled, attempt + 1, baseOverride)
                return@postDelayed
            }
            val expectedHeight = if (enabled) content.height / 2 else content.height
            if ((surface.width <= 0 || kotlin.math.abs(surface.height - expectedHeight) > 2) &&
                attempt < 20
            ) {
                applyLaptopGeometryWhenLaidOut(enabled, attempt + 1, baseOverride)
                return@postDelayed
            }
            val reason = if (enabled) "laptop mode enabled" else "laptop mode disabled"
            // While the laptop deck is visible the desktop must always match
            // the measured upper pane. A custom/recovered profile would
            // otherwise be letterboxed inside that pane.
            val next = configForHostGeometry(
                baseOverride ?: Config(targetWidth, targetHeight, density, secureDisplay, showSystemDecorations),
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
            refreshPrivilegedInputConfig("laptop_layout_measured")
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
                paneConfig.density != density
            ) {
                resizeActiveDisplay(paneConfig, "laptop pane final synchronization")
            }
            // The pane-layout pass already schedules a mirror update. Avoid a
            // second attach of WindowManager/SurfaceControl mirrors when the
            // measured host has not changed; recreating that layer is visible
            // as a one-frame flash behind the keyboard deck.
            if (mirrorDisplayId == targetDisplayId &&
                mirrorHostWidth == surface.width && mirrorHostHeight == surface.height
            ) {
                return@postDelayed
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

    /**
     * Keep the special-size Fold8 orientation handling isolated from the
     * normal-size Fold family. The regional model suffix is not sufficient on
     * its own (carrier models use SCG/SC identifiers in Japan), so all known
     * retail identifiers are matched after removing punctuation. This also
     * allows Samsung's longer SKU strings (for example SM-F971BZ...) to match
     * their regional base model.
     *
     * Fold8 (special-size / h8q):
     *   SM-F971B (global), SM-F971U/U1 (US), SM-F971W (Canada),
     *   SM-F9710 (China), SM-F971N (Korea), SM-F971Q (Japan SIM-free),
     *   SM-F971Z (SoftBank), SM-F971C (Rakuten), SCG41 (au), SC-57G (Docomo)
     * Fold8 Ultra (normal-size / q8q):
     *   SM-F976B (global), SM-F976U/U1 (US), SM-F976W (Canada),
     *   SM-F9760 (China), SM-F976N (Korea), SM-F976Q (Japan SIM-free),
     *   SM-F976Z (SoftBank), SM-F976C (Rakuten), SCG39 (au), SC-56G (Docomo)
     */
    private fun laptopFoldProfile(): LaptopFoldProfile {
        // Build.MODEL may contain a market suffix and Build.DEVICE/PRODUCT may
        // contain an OEM suffix. Normalize all three before matching so that
        // SM-F971, SM-F971N, SM-F971NZ... and SC-57G are handled consistently.
        val modelValue = Build.MODEL.uppercase().filter(Char::isLetterOrDigit)
        val metadataValues = listOf(Build.DEVICE, Build.PRODUCT)
            .map { value -> value.uppercase().filter(Char::isLetterOrDigit) }
        val buildValues = listOf(modelValue) + metadataValues

        fun matchesModel(ids: Set<String>, values: List<String> = buildValues): Boolean = values.any { value ->
            ids.any { id -> value.startsWith(id) }
        }

        fun matchesDevice(prefix: String): Boolean = metadataValues.any { value ->
            value.startsWith(prefix)
        }

        // Check Ultra first. It is the normal-size path, and an explicit q8q /
        // SM-F976 match must never be mistaken for the special-size Fold8.
        return when {
            matchesModel(FOLD8_ULTRA_MODEL_IDS, listOf(modelValue)) ->
                LaptopFoldProfile.STANDARD_FOLDABLE

            matchesModel(FOLD8_SPECIAL_MODEL_IDS, listOf(modelValue)) ->
                LaptopFoldProfile.FOLD8

            matchesModel(FOLD8_ULTRA_MODEL_IDS) || matchesDevice(FOLD8_ULTRA_DEVICE_PREFIX) ->
                LaptopFoldProfile.STANDARD_FOLDABLE

            matchesModel(FOLD8_SPECIAL_MODEL_IDS) || matchesDevice(FOLD8_SPECIAL_DEVICE_PREFIX) ->
                LaptopFoldProfile.FOLD8

            else -> LaptopFoldProfile.STANDARD_FOLDABLE
        }
    }

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

    /**
     * WindowManager's folding API is authoritative when the OEM extension is
     * present. Older devices may not expose it, so the legacy display/sensor
     * probe remains a fallback rather than being treated as a second signal.
     */
    private fun refreshFoldingApiState(reason: String, force: Boolean = false) {
        val now = SystemClock.uptimeMillis()
        if (!force && now - foldingApiLastProbeAt < 180L) return
        foldingApiLastProbeAt = now
        runCatching {
            // The WindowManager extension is window-scoped. Query it with the
            // live control Activity when available; an AccessibilityService
            // context has no window token and an empty result there must not
            // be mistaken for a confirmed non-foldable device.
            val activity = MainActivity.currentActivity()
                ?: throw IllegalStateException("no activity window for folding API")
            val info = WindowInfoTracker.getOrCreate(activity)
                .getCurrentWindowLayoutInfo(activity)
            val features = info.displayFeatures.filterIsInstance<FoldingFeature>()
            val halfOpenedFeature = features.firstOrNull {
                it.state == FoldingFeature.State.HALF_OPENED
            }
            Triple(
                features,
                halfOpenedFeature != null,
                halfOpenedFeature?.orientation == FoldingFeature.Orientation.HORIZONTAL
            )
        }.onSuccess { (features, halfOpened, horizontalHinge) ->
            foldingApiLastSuccessAt = now
            val foldable = features.isNotEmpty()
            if (foldingApiFoldable != foldable ||
                foldingApiLaptopPosture != halfOpened ||
                foldingApiHorizontalHinge != horizontalHinge
            ) {
                OperationLog.i(
                    this,
                    "FoldState",
                    "WindowManager folding API available=true foldable=$foldable " +
                            "halfOpened=$halfOpened hingeHorizontal=$horizontalHinge reason=$reason"
                )
            }
            foldingApiFoldable = foldable
            foldingApiLaptopPosture = halfOpened
            foldingApiHorizontalHinge = horizontalHinge
        }.onFailure {
            // Extension versions before current-window-layout support throw
            // here. Keep the last positive HALF_OPENED result for a short
            // hand-off window: during a fold transition the control Activity
            // can briefly lose its window token, and treating that exception
            // as a flat posture hides the keyboard a few hundred ms after it
            // was shown. Once the grace period expires, the hinge sensor is a
            // valid fallback again.
            if (now - foldingApiLastSuccessAt > foldingApiFailureGraceMs) {
                foldingApiFoldable = null
                foldingApiLaptopPosture = null
                foldingApiHorizontalHinge = null
            }
            if (force) Log.d(logTag, "WindowManager folding API unavailable reason=$reason", it)
        }
    }

    private fun isFoldableDevice(): Boolean {
        refreshFoldingApiState("capability", force = true)
        // A fold transition can make WindowManager briefly publish an empty
        // feature list while the inactive panel is being attached.  That is
        // not proof that the device stopped being foldable. Keep the positive
        // API result, but fall back to stable hardware signals before ever
        // returning false so a transient result cannot disable auto detection.
        if (foldingApiFoldable == true) return true
        val manager = getSystemService(DisplayManager::class.java)
        val hardwareFoldable = internalDisplays(manager).size >= 2 ||
                getSystemService(SensorManager::class.java)
                    .getDefaultSensor(Sensor.TYPE_HINGE_ANGLE) != null
        return hardwareFoldable || foldingApiFoldable == true
    }

    /**
     * Laptop mode needs enough surface for two usable panes. Foldables are
     * explicitly supported even when one panel is narrower than a tablet;
     * otherwise require a tablet-class display (600dp smallest width).
     */
    private fun isLaptopCapableDevice(): Boolean {
        if (isFoldableDevice()) return true
        if (resources.configuration.smallestScreenWidthDp >= 600) return true
        val display = getSystemService(DisplayManager::class.java)
            .getDisplay(Display.DEFAULT_DISPLAY) ?: return false
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        display.getRealMetrics(metrics)
        val densityDpi = metrics.densityDpi.takeIf { it > 0 } ?: 160
        val minDp = minOf(metrics.widthPixels, metrics.heightPixels) * 160f / densityDpi
        return minDp >= 600f
    }

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
                LaptopKey("FN", LAPTOP_FN, 1f),
                LaptopKey("", KeyEvent.KEYCODE_META_LEFT),
                LaptopKey("ALT", LAPTOP_ALT, 1.15f),
                LaptopKey("SPACE", KeyEvent.KEYCODE_SPACE, 4.35f),
                LaptopKey("ALT", LAPTOP_ALT, 1.15f),
                LaptopKey("←", KeyEvent.KEYCODE_DPAD_LEFT),
                LaptopKey("↑", KeyEvent.KEYCODE_DPAD_UP),
                LaptopKey("↓", KeyEvent.KEYCODE_DPAD_DOWN),
                LaptopKey("→", KeyEvent.KEYCODE_DPAD_RIGHT),
                LaptopKey("MENU", LAPTOP_MENU, 1.15f)
            )
        )
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
            // Do not use a private-use Material Icons code point here.  The
            // Flutter font is not guaranteed to be available to the native
            // overlay on every packaging/runtime combination, and Android's
            // fallback font can render the code point as an unrelated CJK
            // glyph.  The simple 3x3 grid is a deterministic app-launcher
            // affordance and remains legible on every OEM font stack.
            text = ""
            setCustomGlyph(
                KeyboardGlyphDrawable(
                    KeyboardGlyphDrawable.APP_GRID,
                    laptopPalette().text
                )
            )
        }
        if (key.code in setOf(LAPTOP_SHIFT, LAPTOP_CONTROL, LAPTOP_ALT, LAPTOP_CAPS)) {
            laptopModifierButtons.putIfAbsent(key.code, this)
        }
        if (key.code == LAPTOP_FN) {
            laptopFnButton = this
            background = laptopKeyBackground(laptopFunctionRowVisible, LAPTOP_FN)
            setOnClickListener {
                performLaptopHaptic(this)
                setLaptopFunctionRowVisible(!laptopFunctionRowVisible)
            }
            setOnLongClickListener {
                if (demoMode) return@setOnLongClickListener false
                performLaptopHaptic(this)
                showLaptopKeyboardSettings()
                true
            }
        } else if (key.code == LAPTOP_MENU) {
            setOnClickListener {
                if (!demoMode) {
                    performLaptopHaptic(this)
                    toggleMenu()
                }
            }
        }
        if (key.code in laptopShortcutLabels.keys) laptopShortcutButtons[key.code] = this
        setOnTouchListener { view, event ->
            if (key.code == LAPTOP_FN || key.code == LAPTOP_MENU) {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> view.animate()
                        .scaleX(.92f).scaleY(.92f).alpha(.72f)
                        .setDuration(55).start()

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.animate()
                        .scaleX(1f).scaleY(1f).alpha(1f)
                        .setDuration(110).start()
                }
                // Let TextView dispatch click/long-click. These are Dextop
                // internal actions and must never enter the uinput key path.
                return@setOnTouchListener false
            }
            if (key.code == KeyEvent.KEYCODE_META_LEFT) {
                // Meta is a normal modifier again.  Theme settings are
                // intentionally owned by FN long-press so Meta can be used
                // without an accidental settings transition.
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> view.animate().scaleX(.92f).scaleY(.92f).alpha(.72f)
                        .setDuration(55).start()

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        view.animate().scaleX(1f).scaleY(1f).alpha(1f)
                            .setDuration(110).start()
                        if (event.actionMasked == MotionEvent.ACTION_UP) {
                            performLaptopHaptic(view)
                            handleLaptopKey(key.code)
                        }
                    }
                }
                return@setOnTouchListener true
            }
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    // Modifiers are latched on press, not release, so a
                    // second finger can press C/V/etc. while Ctrl is still
                    // held. This also makes real multi-touch chords work.
                    if (key.code < 0) {
                        performLaptopHaptic(view)
                        handleLaptopKey(key.code)
                    } else {
                        performLaptopHaptic(view)
                        startLaptopKeyPress(view, key.code)
                    }
                    view.animate()
                        .scaleX(.92f).scaleY(.92f).alpha(.72f)
                        .setDuration(55).start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (key.code >= 0) finishLaptopKeyPress(view)
                    view.animate()
                        .scaleX(1f).scaleY(1f).alpha(1f)
                        .setDuration(110).start()
                }
            }
            true
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
        val fill = if (selected) {
            palette.selected
        } else {
            when {
                functionKey || modifierKey || specialKey -> palette.keyVariant
                else -> palette.key
            }
        }
        setColor(opacityColor(fill, palette.keyOpacity))
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
        laptopPreviewThemeId ?: getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
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
        val keyOpacity: Float = 1f,
        val showTrackpadLabel: Boolean = true,
        val imageBase64: String? = null,
    )

    private fun opacityColor(color: Int, opacity: Float): Int = Color.argb(
        (Color.alpha(color) * opacity.coerceIn(.1f, 1f)).toInt(),
        Color.red(color),
        Color.green(color),
        Color.blue(color)
    )

    /** Keep transparent theme imports from exposing the mirrored display. */
    private fun opaqueColor(color: Int): Int = Color.rgb(
        Color.red(color),
        Color.green(color),
        Color.blue(color)
    )

    private fun paletteColor(value: String?, fallback: Int): Int = runCatching {
        Color.parseColor(value ?: "")
    }.getOrDefault(fallback)

    private fun contrastingColor(color: Int): Int {
        val luminance = .299 * Color.red(color) +
                .587 * Color.green(color) +
                .114 * Color.blue(color)
        return if (luminance >= 160) Color.rgb(26, 24, 30) else Color.WHITE
    }

    private fun laptopPalette(): LaptopPalette = laptopPaletteFor(laptopKeyboardTheme())

    /** Resolve a palette for previews as well as the active keyboard. */
    private fun laptopPaletteFor(id: String): LaptopPalette {
        val crimson = id == "crimson"
        val cloud = id == "cloud"
        val amoled = id == "amoled"
        val fallback = if (crimson) LaptopPalette(
            Color.rgb(89, 14, 14), Color.rgb(110, 27, 27), Color.rgb(81, 10, 11),
            Color.rgb(126, 32, 27), Color.rgb(255, 190, 151), Color.rgb(90, 15, 16),
            Color.rgb(222, 137, 102), Color.rgb(81, 10, 11), 7f
        ) else if (cloud) LaptopPalette(
            Color.rgb(220, 235, 255), Color.WHITE, Color.rgb(247, 251, 255),
            Color.rgb(183, 212, 245), Color.rgb(66, 100, 134), Color.rgb(199, 221, 245),
            Color.rgb(82, 120, 159), Color.rgb(190, 218, 248), 16f, opacity = .94f
        ) else if (amoled) LaptopPalette(
            Color.BLACK, Color.BLACK, Color.rgb(3, 3, 3),
            Color.rgb(59, 59, 59), Color.rgb(220, 220, 220), Color.BLACK,
            Color.rgb(175, 175, 175), Color.rgb(81, 81, 81), 7f
        ) else LaptopPalette(
            Color.rgb(18, 18, 22), Color.rgb(48, 46, 54), Color.rgb(48, 46, 54),
            Color.rgb(76, 72, 84), Color.rgb(235, 231, 239), Color.rgb(35, 34, 40),
            Color.rgb(145, 141, 151), Color.rgb(208, 188, 237), 7f
        )
        val raw = getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
            .getString("flutter.dextop_keyboard_theme_$id", null) ?: return fallback
        // Built-in colors remain authoritative so older normalized preference
        // copies cannot destroy their decorative-key contrast.  Opacity is a
        // safe per-theme override, however, and is intentionally read back so
        // the editor's unified-opacity and key-opacity sliders also work for
        // built-ins.
        if (id == "standard" || id == "crimson" || id == "cloud" || id == "amoled") {
            return runCatching {
                val json = JSONObject(raw)
                fallback.copy(
                    opacity = json.optDouble("opacity", fallback.opacity.toDouble())
                        .toFloat().coerceIn(.1f, 1f),
                    keyOpacity = json.optDouble("keyOpacity", fallback.keyOpacity.toDouble())
                        .toFloat().coerceIn(.1f, 1f),
                    showTrackpadLabel = json.optBoolean("showTrackpadLabel", true),
                )
            }.getOrDefault(fallback)
        }
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
                keyOpacity = json.optDouble("keyOpacity", 1.0).toFloat().coerceIn(.1f, 1f),
                showTrackpadLabel = json.optBoolean("showTrackpadLabel", true),
                imageBase64 = json.optString("imageBase64").takeIf { it.isNotBlank() }
            )
        }.getOrDefault(fallback)
    }

    private fun showLaptopKeyboardSettings() {
        val deck = laptopDeckContent ?: return
        laptopSettingsVisible = true
        TransitionManager.beginDelayedTransition(deck, AutoTransition().apply { duration = 240 })
        deck.removeAllViews()
        val currentTheme = laptopKeyboardTheme()
        val crimson = currentTheme == "crimson"
        val amoled = currentTheme == "amoled"
        val themeAccent = when {
            crimson -> Color.rgb(236, 145, 101)
            amoled -> Color.rgb(210, 210, 210)
            else -> Color.rgb(208, 188, 237)
        }
        deck.background = if (crimson) GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.rgb(25, 2, 4), Color.rgb(57, 7, 8), Color.rgb(34, 3, 5))
        ) else GradientDrawable().apply {
            setColor(if (amoled) Color.BLACK else Color.rgb(18, 18, 22))
        }
        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(10), dp(20), dp(8))
        }
        header.addView(ImageButton(this).apply {
            contentDescription = NativeStrings.text("nativeReturn")
            setImageDrawable(KeyboardGlyphDrawable(KeyboardGlyphDrawable.BACK, Color.WHITE))
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setOnClickListener { rebuildLaptopDeck() }
        }, LinearLayout.LayoutParams(dp(52), dp(52)).apply { rightMargin = dp(8) })
        header.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@MirrorService).apply {
                text = NativeStrings.text("nativeKeyboardSettings")
                typeface = laptopTypeface
                textSize = 19f
                setTextColor(Color.WHITE)
            })
            addView(TextView(this@MirrorService).apply {
                text = NativeStrings.text("nativeKeyboardSettingsDescription")
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
                setImageDrawable(
                    KeyboardGlyphDrawable(
                        KeyboardGlyphDrawable.PALETTE,
                        themeAccent
                    )
                )
            }, LinearLayout.LayoutParams(dp(28), dp(28)).apply { rightMargin = dp(12) })
            addView(TextView(this@MirrorService).apply {
                text = NativeStrings.text("nativeTheme")
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
        val choices = mutableListOf(
            "standard" to NativeStrings.text("nativeKeyboardThemeStandard"),
            "crimson" to NativeStrings.text("nativeKeyboardThemeCrimson"),
            "cloud" to NativeStrings.text("nativeKeyboardThemeCloud"),
            "amoled" to NativeStrings.text("nativeKeyboardThemeAmoled")
        )
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
                setStroke(
                    dp(if (selected) 2 else 1), if (selected)
                        palette.selected else palette.border
                )
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
                    // Keep the selected state legible on both the dark
                    // Crimson palette and the light Cloud Pop palette. The
                    // old check used the same accent as the card background.
                    val checkColor = contrastingColor(palette.selected)
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(palette.selected)
                        setStroke(dp(1), checkColor)
                    }
                    setPadding(dp(3), dp(3), dp(3), dp(3))
                    setImageDrawable(
                        KeyboardGlyphDrawable(
                            KeyboardGlyphDrawable.CHECK,
                            checkColor
                        )
                    )
                }, LinearLayout.LayoutParams(dp(30), dp(30)))
            }, LinearLayout.LayoutParams(-1, -2))
            addView(LinearLayout(this@MirrorService).apply {
                gravity = Gravity.CENTER
                repeat(7) { index ->
                    addView(View(this@MirrorService).apply {
                        background = GradientDrawable().apply {
                            val fill = if (index == 0 || index == 6) {
                                palette.keyVariant
                            } else {
                                palette.key
                            }
                            setColor(opacityColor(fill, palette.keyOpacity))
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
                    "crimson" -> NativeStrings.text("nativeKeyboardThemeCrimsonDescription")
                    "cloud" -> NativeStrings.text("nativeKeyboardThemeCloudDescription")
                    "amoled" -> NativeStrings.text("nativeKeyboardThemeAmoledDescription")
                    "standard" -> NativeStrings.text("nativeKeyboardThemeStandardDescription")
                    else -> NativeStrings.text("nativeKeyboardThemeCustomDescription")
                }
                typeface = laptopTypeface
                textSize = 11f
                setTextColor(palette.trackpadText)
            })
            setOnClickListener {
                getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE).edit()
                    .putString("flutter.laptop_keyboard_theme", id).apply()
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
        finishAllLaptopKeyPresses()
        laptopDeck?.let { content.removeView(it) }
        laptopSettingsVisible = false
        val nextDeck = buildLaptopDeck().apply {
            alpha = 0f
            translationY = dp(28).toFloat()
        }
        content.addView(nextDeck, LinearLayout.LayoutParams(-1, 0, 1f))
        nextDeck.post {
            nextDeck.animate()
                .alpha(1f)
                .translationY(0f)
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

    private fun laptopMetaState(keyCode: Int): Int {
        var metaState = 0
        val isLetter = keyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z
        val shifted = if (isLetter) laptopShift.xor(laptopCapsLock) else laptopShift
        if (shifted) metaState = metaState or KeyEvent.META_SHIFT_ON
        if (laptopControl) metaState = metaState or KeyEvent.META_CTRL_ON
        if (laptopAlt) metaState = metaState or KeyEvent.META_ALT_ON
        return metaState
    }

    private fun startLaptopKeyPress(view: View, keyCode: Int) {
        finishLaptopKeyPress(view)
        val metaState = laptopMetaState(keyCode)
        if (!injectLaptopKeyboardEvent(keyCode, KeyEvent.ACTION_DOWN, metaState, 0)) return

        // Shift/Ctrl/Alt remain one-shot modifiers, but their captured state is
        // retained for every repeat generated by this physical press.
        laptopShift = false
        laptopControl = false
        laptopAlt = false
        refreshLaptopModifierKeys()

        val state = LaptopKeyPressState(keyCode, metaState)
        val repeater = object : Runnable {
            override fun run() {
                if (laptopKeyPresses[view] !== state) return
                state.repeatCount += 1
                injectLaptopKeyboardEvent(
                    state.keyCode,
                    KeyEvent.ACTION_DOWN,
                    state.metaState,
                    state.repeatCount
                )
                view.postDelayed(this, ViewConfiguration.getKeyRepeatDelay().toLong())
            }
        }
        state.repeater = repeater
        laptopKeyPresses[view] = state
        view.postDelayed(repeater, ViewConfiguration.getKeyRepeatTimeout().toLong())
    }

    private fun finishLaptopKeyPress(view: View) {
        val state = laptopKeyPresses.remove(view) ?: return
        state.repeater?.let(view::removeCallbacks)
        injectLaptopKeyboardEvent(state.keyCode, KeyEvent.ACTION_UP, state.metaState, 0)
    }

    private fun finishAllLaptopKeyPresses() {
        laptopKeyPresses.keys.toList().forEach(::finishLaptopKeyPress)
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
        return if (menuUsesLandscapeLayout()) {
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

    /**
     * During a fold/cover hand-off targetWidth/targetHeight can describe the
     * previous panel for a few frames.  Menu geometry must follow the host
     * Surface (the panel that actually receives touches), not that stale
     * logical display profile.
     */
    private fun menuUsesLandscapeLayout(): Boolean {
        val surface = surfaceView
        val surfaceWidth = surface?.width ?: 0
        val surfaceHeight = surface?.height ?: 0
        if (surfaceWidth >= 480 && surfaceHeight >= 480) {
            return surfaceWidth >= surfaceHeight
        }
        val bounds = runCatching { windowManager?.currentWindowMetrics?.bounds }.getOrNull()
        val width = bounds?.width() ?: 0
        val height = bounds?.height() ?: 0
        if (width >= 480 && height >= 480) return width >= height
        return targetWidth >= targetHeight
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
        container.addView(
            ScrollView(this).apply {
                isFillViewport = true
                isVerticalScrollBarEnabled = true
                isScrollbarFadingEnabled = false
                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                addView(primary, FrameLayout.LayoutParams(-1, -2))
            }, LinearLayout.LayoutParams(
                if (menuUsesLandscapeLayout()) dp(340) else 0, -1,
                if (menuUsesLandscapeLayout()) 0f else 1f
            )
        )
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
        laptopPreviewThemeId = pendingLaptopPreviewThemeId
        pendingLaptopPreviewThemeId = null
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
            frame.addView(
                deck, FrameLayout.LayoutParams(
                    -1, (resources.displayMetrics.heightPixels * .5f).toInt(), Gravity.BOTTOM
                )
            )
            deck.animate().alpha(1f).translationY(0f).setDuration(320L).start()
        }
        // A laptop keyboard demo is a focused keyboard test surface. It must
        // not include the normal Dextop controls or the setup hint; those are
        // reserved for the initial setup/demo surface.
        if (!laptopDemo) {
            frame.addView(controls, menuLayoutParams())
            frame.addView(
                info, if (menuUsesLandscapeLayout()) {
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
            if (menuUsesLandscapeLayout()) {
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
        val availableWidth = if (menuUsesLandscapeLayout()) dp(340) else
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
        // The accessibility overlay may briefly retain the unfolded metrics
        // while the cover Surface is already smaller.  Use the smallest valid
        // host/WindowManager height so the ScrollView receives the remaining
        // viewport instead of being clipped below the visible panel.
        val screenHeight = listOf(
            root?.height ?: 0,
            windowManager?.currentWindowMetrics?.bounds?.height() ?: 0,
            resources.displayMetrics.heightPixels
        ).filter { it > 0 }.minOrNull() ?: resources.displayMetrics.heightPixels
        val verticalMargins = if (menuUsesLandscapeLayout()) dp(36) else dp(24)
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
        stopCastRouteDiscovery()
        animateMenuResize(panel)
        endOverlayTextInput()
        setOverlayFocusable(false)
        panel.removeAllViews()
        panel.addView(
            menuTitle(
                "Dextop",
                if (workspaceExpanded) "‹" else "›",
                "workspace_toggle"
            ) {
                if (demoMode) demoExplanation(NativeStrings.text("nativeViewSavedWorkspacesAndSaveCurrentArrangement"))
                toggleWorkspacePanel()
            })
        addCustomControls(panel, overlayButtonOrder())
        panel.addView(
            actionButton(
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
        val endWidth = if (menuUsesLandscapeLayout() && workspaceExpanded) dp(680) else dp(340)
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
            container.addView(
                ScrollView(this).apply {
                    tag = "workspace_scroll"
                    isFillViewport = true
                    addView(workspacePanel, FrameLayout.LayoutParams(-1, -2))
                }, LinearLayout.LayoutParams(
                    if (menuUsesLandscapeLayout()) dp(340) else 0, -1,
                    if (menuUsesLandscapeLayout()) 0f else 1f
                )
            )
        } else {
            container.findViewWithTag<View>("workspace_scroll")?.apply {
                isClickable = false
                postDelayed({ container.removeView(this) }, 240)
            }
        }
        if (menuUsesLandscapeLayout()) {
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
        JSONArray(
            getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
                .getString("flutter.workspaces", "[]")
        )
    }.getOrDefault(JSONArray())

    private fun packageName(): String = applicationContext.packageName

    /** Samples the system state that decides whether a launch became a desktop window. */
    private fun scheduleWindowLaunchDiagnostics(reason: String, delayMs: Long = 450L) {
        val diagnosticDisplayId = targetDisplayId
        val generation = windowDiagnosticGeneration.incrementAndGet()
        postMainDelayed(delayMs) {
            windowDiagnosticExecutor.execute {
                if (generation != windowDiagnosticGeneration.get()) return@execute
                collectWindowLaunchDiagnostics(reason, diagnosticDisplayId)
            }
        }
    }

    private fun collectWindowLaunchDiagnostics(reason: String, displayId: Int) {
        if (displayId < 0) return
        val taskCommand =
            "dumpsys activity activities | " +
                    "grep -E 'Display: mDisplayId=$displayId|mResumedActivity|topResumedActivity|" +
                    "windowingMode=|bounds=' | tail -n 64"
        logDiagnosticCommand("ActivityTaskManager", reason, displayId, taskCommand)

        val windowCommand =
            "dumpsys window displays | " +
                    "grep -E 'Display: mDisplayId=$displayId|mDisplayId=$displayId|mCurrentFocus|" +
                    "mFocusedApp|windowingMode=|mBounds=' | tail -n 64"
        logDiagnosticCommand("WindowManager", reason, displayId, windowCommand)

        val systemLogCommand =
            "logcat -d -v brief -t 160 ActivityTaskManager:I WindowManager:I " +
                    "ShellTaskOrganizer:I DesktopMode:I DesktopTasksController:I '*:S' | tail -n 80"
        logDiagnosticCommand("TaskOrganizer", reason, displayId, systemLogCommand)
    }

    private fun logDiagnosticCommand(
        component: String,
        reason: String,
        displayId: Int,
        command: String
    ) {
        val result = privilegedAccess.execute("sh", "-c", command)
        if (!result.succeeded) {
            OperationLog.w(
                this,
                component,
                "diagnostic unavailable reason=$reason display=$displayId exit=${result.exitCode} " +
                        "detail=${result.error.take(240)}"
            )
            return
        }
        val compact = result.output.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .joinToString(" | ")
            .ifEmpty { "no matching system records" }
        OperationLog.i(
            this,
            component,
            "diagnostic reason=$reason display=$displayId $compact"
        )
    }

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
            addView(
                levelIcon,
                FrameLayout.LayoutParams(dp(40), dp(32), Gravity.START or Gravity.CENTER_VERTICAL).apply {
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

    /**
     * OverlayDisplayAdapter treats the global display specification as a
     * request, so a stale vendor callback can leave the last overlay alive
     * after the first clear.  Always issue the empty/None request twice at
     * teardown, even when the setting is already empty.  Each pass is
     * independent so a transient failure cannot skip the second write.
     */
    private fun clearOverlayDisplayRequestTwice(reason: String) {
        val preservedAutoSpecs = AndroidAutoMirrorActivity.autoOverlaySpecs()
        repeat(2) { pass ->
            runCatching { displayBackend.clearRequestPreserving(preservedAutoSpecs) }
                .onSuccess {
                    OperationLog.i(
                        this,
                        "DisplayBackend",
                        "clear request issued reason=$reason pass=${pass + 1}/2 " +
                                "preservedAuto=${preservedAutoSpecs.isNotEmpty()}"
                    )
                }
                .onFailure {
                    Log.e(
                        logTag,
                        "unable to clear overlay display request reason=$reason pass=${pass + 1}/2",
                        it
                    )
                }
        }
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
        screenLifecycleGeneration += 1
        unlockResumeScheduled = false
        unlockCandidateSince = 0L
        setPhoneNavigationDisabled(false)
        releasePhoneRotation(clearSnapshot = true)
        // Detach the accessibility host first. Surface destruction normally
        // releases the VirtualDisplay immediately, which makes One UI try to
        // unregister gesture-exclusion listeners from an already removed
        // display. That WindowManager exception leaves Back and Circle to
        // Search broken until SystemUI is restarted.
        detachHostWindow()
        android.os.Handler(mainLooper).postDelayed({
            val autoSessionActive = AndroidAutoMirrorActivity.isAutoSessionActive()
            runCatching {
                if (autoSessionActive) {
                    DisplayEnvironmentSettings(this).activateTopologyForOverlays(
                        AndroidAutoMirrorActivity.autoOverlayDisplayIds()
                    )
                } else {
                    DisplayEnvironmentSettings(this).restoreTopology()
                }
            }.onFailure { Log.e(logTag, "topology restoration failed", it) }
            releaseMirror()
            clearOverlayDisplayRequestTwice("temporary_android_return")
            targetDisplayId = -1
            if (!autoSessionActive) desktopModeConfigurator.restore()
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
            // Leave the service alive while the delayed navigation restores run;
            // some vendor SystemUI builds reapply the flags several seconds
            // after the accessibility window is removed.
            android.os.Handler(mainLooper).postDelayed({ disableSelf() }, 4_500L)
        }, 320)
        Log.i(logTag, "session paused; returned to Dextop home for explicit recovery")
    }

    private fun overlayButtonOrder(): MutableList<String> {
        val saved = getSharedPreferences(PREFS, MODE_PRIVATE)
            .getString(
                "overlay_button_order",
                "modes,volume,brightness,resolution,android,stop,reconnect,orientation,rotate_180,cast"
            ).orEmpty()
            .replace("actions", "stop,reconnect,orientation")
            .split(',').filter { it in allControlIds }.toMutableList()
        saved.removeAll { it !in controlIds }
        controlIds.forEach { if (it !in saved) saved.add(it) }
        saved.remove("laptop")
        if ("laptop" in controlIds) {
            saved.add((saved.indexOf("android") + 1).coerceAtLeast(0), "laptop")
        }
        return saved
    }

    private val allControlIds
        get() = listOf(
            "modes", "volume", "brightness", "resolution", "android", "laptop",
            "stop", "reconnect", "orientation", "rotate_180", "cast"
        )
    private val controlIds
        get() = allControlIds.filter {
            when (it) {
                // Laptop mode is available on foldables and tablet-class displays
                // without requiring a hinge sensor, but is hidden on clearly
                // phone-sized devices where the two-pane surface is unusable.
                "laptop" -> demoMode || isLaptopCapableDevice()
                else -> true
            }
        }

    private fun addCustomControls(panel: LinearLayout, order: List<String>) {
        val mutableOrder = order.toMutableList()
        val views = linkedMapOf<String, View>()
        val columns = 5
        val squareIds = setOf("stop", "reconnect", "orientation", "rotate_180", "cast")
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
                if (span == columns && column != 0) {
                    row++; column = 0
                }
                if (span == 1 && column + span > columns) {
                    row++; column = 0
                }
                view.layoutParams = GridLayout.LayoutParams(
                    GridLayout.spec(row),
                    GridLayout.spec(column, span, 1f)
                ).apply {
                    width = 0
                    height = dp(54)
                    bottomMargin = dp(8)
                    if (column > 0) leftMargin = dp(6)
                }
                if (span == columns) {
                    row++; column = 0
                } else {
                    column++
                    if (column == columns) {
                        row++; column = 0
                    }
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

            "resolution" -> actionButton(
                R.drawable.ic_monitor,
                "${NativeStrings.text("nativeResolution")}   ${targetWidth} × ${targetHeight}"
            ) {
                if (demoMode) demoExplanation(NativeStrings.text("nativeSwitchDextopResolutionAndDpi"))
                showResolutionMenu(panel)
            }

            "android" -> actionButton(
                R.drawable.ic_smartphone,
                NativeStrings.text("nativeTemporarilyReturnToAndroid")
            ) {
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
                    if (enableManually) {
                        // An explicit enable always wins over a previous
                        // manual dismissal and becomes a true manual mode.
                        laptopAutoSuppressedByUser = false
                        laptopManualOverride = true
                        OperationLog.i(this, "LaptopMode", "overlay manual enable")
                    } else {
                        // Keep the automatic detector muted until the user
                        // returns the hinge to a flat posture. Without this
                        // latch the posture poller sees HALF_OPENED again and
                        // immediately turns the deck back on.
                        laptopAutoSuppressedByUser = true
                        laptopManualOverride = false
                        pendingLaptopMode = null
                        pendingLaptopModeSince = 0L
                        laptopModeEvaluationGeneration += 1
                        OperationLog.i(
                            this,
                            "LaptopMode",
                            "overlay manual disable; auto detection suppressed until flat posture"
                        )
                    }
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

    private fun squareControl(id: String): View = when (id) {
        "rotate_180" -> squareTextAction("180°", NativeStrings.text("nativeRotate180")) {
            if (demoMode) demoExplanation(NativeStrings.text("nativeRotate180"))
            else toggleDisplayRotation180()
        }

        "cast" -> squareAction(R.drawable.ic_cast, NativeStrings.text("nativeCast")) {
            if (demoMode) demoExplanation(NativeStrings.text("nativeCastDescription"))
            else openCastPicker()
        }

        "stop" -> squareAction(R.drawable.ic_stop, NativeStrings.text("nativeEnd"), true) {
            if (demoMode) demoExplanation(NativeStrings.text("nativeTerminateYourDextopSession")) else stop()
        }

        "reconnect" -> squareAction(R.drawable.ic_reload, NativeStrings.text("nativeReconnect")) {
            if (demoMode) demoExplanation(NativeStrings.text("nativeReconnectIfYouHaveDisplayOrConnection"))
            else start(Config(targetWidth, targetHeight, density, secureDisplay, showSystemDecorations))
        }

        else -> squareAction(
            // The live target can be the landscape upper pane while the
            // selected Dextop orientation is portrait. Show the action for
            // the next orientation from the explicit selection instead.
            if (requestedPortrait) R.drawable.ic_landscape else R.drawable.ic_portrait,
            if (requestedPortrait) NativeStrings.text("nativeHorizontalHolding")
            else NativeStrings.text("nativeVerticalHolding")
        ) {
            if (demoMode) demoExplanation(NativeStrings.text("nativeSwitchBetweenPortraitAndLandscapeOrientationOf"))
            else changeOrientation()
        }
    }

    private fun squareTextAction(label: String, description: String, action: () -> Unit) =
        TextView(this).apply {
            text = label
            textSize = 16f
            gravity = Gravity.CENTER
            contentDescription = description
            setTextColor(Color.rgb(230, 225, 229))
            background = GradientDrawable().apply {
                setColor(Color.rgb(50, 47, 55))
                cornerRadius = dp(16).toFloat()
            }
            setOnClickListener { action() }
        }

    private fun openCastPicker() {
        menuPrimary?.let(::showCastRouteMenu)
    }

    private fun showCastRouteMenu(panel: LinearLayout) {
        stopCastRouteDiscovery()
        animateMenuResize(panel)
        panel.removeAllViews()
        panel.addView(
            menuTitle(
                NativeStrings.text("nativeCast"),
                NativeStrings.text("nativeReturn")
            ) { showMainMenu(panel) })

        val selector = runCatching {
            // Initialize CAF, but do not create a detached MediaRouteButton from
            // the accessibility-service context. That path requires an Activity
            // theme and throws IllegalArgumentException on Samsung builds.
            val castContext = CastContext.getSharedInstance(this)
            val castMode = getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
                .getString("flutter.cast_mode", "simple") ?: "simple"
            val receiverAppId = if (castMode == "receiver") {
                BuildConfig.CAST_RECEIVER_APP_ID
            } else {
                CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID
            }
            // CastContext is process-global and OptionsProvider is evaluated only
            // once. Without updating it here, changing the mode after the first
            // Cast scan leaves discovery pinned to the previous receiver ID.
            castContext.setReceiverApplicationId(receiverAppId)
            val category = if (castMode == "receiver") {
                CastMediaControlIntent.categoryForCast(
                    BuildConfig.CAST_RECEIVER_APP_ID,
                    listOf(DextopCastProtocol.NAMESPACE)
                )
            } else {
                CastMediaControlIntent.categoryForCast(
                    CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID
                )
            }
            OperationLog.i(
                this,
                "Cast",
                "creating route selector mode=$castMode receiverAppId=$receiverAppId"
            )
            MediaRouteSelector.Builder()
                .addControlCategory(category)
                .build()
        }.getOrElse { error ->
            OperationLog.e(this, "Cast", "unable to create Google Cast route selector", error)
            panel.addView(menuHint(NativeStrings.text("nativeCastUnavailable")))
            return
        }
        val router = MediaRouter.getInstance(this)
        castMediaRouter = router
        val sessionManager = CastContext.getSharedInstance(this).sessionManager
        lateinit var renderRoutes: () -> Unit
        lateinit var routeCallback: MediaRouter.Callback
        var scanning = true
        var scanGeneration = 0L

        fun finishCastConnection(session: CastSession) {
            val castMode = getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
                .getString("flutter.cast_mode", "simple") ?: "simple"
            if (castMode == "receiver") {
                runCatching {
                    session.sendMessage(
                        DextopCastProtocol.NAMESPACE,
                        "{\"type\":\"status\",\"text\":\"Dextop connected\"}"
                    )
                }
            } else {
                runCatching {
                    castCompatibilityStreamer?.stop()
                    val streamer = CastCompatibilityStreamer(
                        this,
                        privilegedAccess,
                        targetDisplayId,
                        targetWidth,
                        targetHeight,
                        density
                    )
                    castCompatibilityStreamer = streamer
                    val streamUrl = streamer.start()
                    val media = MediaInfo.Builder(streamUrl)
                        .setStreamType(MediaInfo.STREAM_TYPE_LIVE)
                        .setContentType("video/mp4")
                        .build()
                    val request = MediaLoadRequestData.Builder()
                        .setMediaInfo(media)
                        .setAutoplay(true)
                        .build()
                    session.remoteMediaClient?.load(request)
                        ?: error("Default Media Receiver media client is unavailable")
                    OperationLog.i(this, "Cast", "compatibility video load requested url=$streamUrl")
                    // The accessibility control panel must not remain in the
                    // active render/input path while a second recording
                    // VirtualDisplay starts consuming the desktop. Close it
                    // automatically instead of requiring another gesture.
                    menu?.postDelayed({
                        if (menu?.visibility == View.VISIBLE) toggleMenu()
                    }, 120L)
                }.onFailure { error ->
                    castCompatibilityStreamer?.stop()
                    castCompatibilityStreamer = null
                    OperationLog.e(this, "Cast", "unable to start compatibility video", error)
                }
            }
            OperationLog.i(this, "Cast", "Cast session connected receiver=${session.castDevice?.friendlyName}")
        }

        castSessionListener?.let { sessionManager.removeSessionManagerListener(it, CastSession::class.java) }
        val sessionListener = object : SessionManagerListener<CastSession> {
            override fun onSessionStarted(session: CastSession, sessionId: String) {
                finishCastConnection(session)
                renderRoutes()
            }

            override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
                finishCastConnection(session)
                renderRoutes()
            }

            override fun onSessionStartFailed(session: CastSession, error: Int) {
                OperationLog.e(this@MirrorService, "Cast", "Cast session start failed code=$error")
                panel.addView(menuHint("${NativeStrings.text("nativeCastUnavailable")} ($error)"))
                scheduleMenuHeightUpdate()
            }

            override fun onSessionEnded(session: CastSession, error: Int) {
                OperationLog.i(this@MirrorService, "Cast", "Cast session ended code=$error")
                renderRoutes()
            }

            override fun onSessionEnding(session: CastSession) = Unit
            override fun onSessionResumeFailed(session: CastSession, error: Int) = Unit
            override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
            override fun onSessionStarting(session: CastSession) = Unit
            override fun onSessionSuspended(session: CastSession, reason: Int) = Unit
        }
        castSessionListener = sessionListener
        sessionManager.addSessionManagerListener(sessionListener, CastSession::class.java)

        renderRoutes = renderRoutes@{
            if (menuPrimary !== panel || castMediaRouter !== router) return@renderRoutes
            while (panel.childCount > 1) panel.removeViewAt(1)
            val routes = router.routes.filter {
                !it.isDefault && it.isEnabled && it.matchesSelector(selector)
            }
            OperationLog.i(
                this,
                "Cast",
                "route menu refresh total=${router.routes.size} eligible=${routes.size} " +
                        "routes=${routes.joinToString { it.name.toString() }}"
            )
            val activeSession = sessionManager.currentCastSession
            val activeDeviceName = activeSession?.castDevice?.friendlyName
            val scanRow = FrameLayout(this).apply {
                val scanButton = actionButton(
                    R.drawable.ic_reload,
                    NativeStrings.text("nativeScanAgain")
                ) {
                    scanning = true
                    val generation = ++scanGeneration
                    renderRoutes()
                    router.removeCallback(routeCallback)
                    router.addCallback(
                        selector,
                        routeCallback,
                        MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN
                    )
                    OperationLog.i(this@MirrorService, "Cast", "manual active scan requested")
                    postDelayed({
                        if (generation == scanGeneration) {
                            scanning = false
                            renderRoutes()
                        }
                    }, 2_500L)
                }
                addView(scanButton, FrameLayout.LayoutParams(-1, dp(50)))
                if (scanning) {
                    addView(ProgressBar(this@MirrorService).apply {
                        isIndeterminate = true
                        contentDescription = NativeStrings.text("nativeScanning")
                    }, FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER_VERTICAL or Gravity.END).apply {
                        rightMargin = dp(16)
                    })
                }
            }
            panel.addView(scanRow, LinearLayout.LayoutParams(-1, dp(58)).apply {
                bottomMargin = dp(2)
            })
            if (routes.isEmpty()) {
                panel.addView(
                    menuHint(
                        if (scanning) {
                            NativeStrings.text("nativeScanning")
                        } else {
                            NativeStrings.text("nativeNoCastDevices")
                        }
                    )
                )
            } else {
                routes.forEach { route ->
                    val isActiveRoute = activeSession != null &&
                            (router.selectedRoute.id == route.id || activeDeviceName == route.name.toString())
                    val label = if (isActiveRoute) {
                        "✓ ${route.name}  ·  ${NativeStrings.text("nativeCasting")}"
                    } else {
                        route.name.toString()
                    }
                    panel.addView(actionButton(R.drawable.ic_cast, label) {
                        if (activeSession != null) return@actionButton
                        runCatching { router.selectRoute(route) }
                            .onSuccess {
                                OperationLog.i(this, "Cast", "selected receiver=${route.name}")
                                // Keep discovery alive until CAF confirms that
                                // the receiver application has actually started.
                                while (panel.childCount > 1) panel.removeViewAt(1)
                                panel.addView(menuHint("${NativeStrings.text("nativeCast")}…"))
                                scheduleMenuHeightUpdate()
                                panel.postDelayed({
                                    if (menuPrimary === panel &&
                                        CastContext.getSharedInstance(this).sessionManager.currentCastSession == null
                                    ) {
                                        OperationLog.e(
                                            this,
                                            "Cast",
                                            "Cast receiver launch timed out route=${route.name}"
                                        )
                                        showCastRouteMenu(panel)
                                    }
                                }, 15_000L)
                            }
                            .onFailure { error ->
                                OperationLog.e(this, "Cast", "unable to select receiver=${route.name}", error)
                            }
                    }.apply {
                        isEnabled = activeSession == null
                        alpha = if (activeSession == null) 1f else 0.45f
                    })
                }
            }
            if (activeSession != null) {
                panel.addView(
                    actionButton(
                        R.drawable.ic_stop,
                        NativeStrings.text("nativeStopCasting")
                    ) {
                        endCastSession("user")
                        renderRoutes()
                    })
            }
            scheduleMenuHeightUpdate()
        }

        routeCallback = object : MediaRouter.Callback() {
            override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) = renderRoutes()
            override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) = renderRoutes()
            override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) = renderRoutes()
        }
        castRouteCallback = routeCallback
        router.addCallback(selector, routeCallback, MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN)
        renderRoutes()
        // Google Play services publishes mDNS results asynchronously and some
        // Samsung MediaRouter builds do not deliver the first provider-change
        // callback to a service-owned router. Refresh after both discovery
        // windows so the overlay cannot remain stuck on the initial empty list.
        panel.postDelayed({ renderRoutes() }, 500L)
        panel.postDelayed({
            scanning = false
            renderRoutes()
        }, 1_500L)
    }

    private fun stopCastRouteDiscovery() {
        val router = castMediaRouter
        val callback = castRouteCallback
        if (router != null && callback != null) router.removeCallback(callback)
        castMediaRouter = null
        castRouteCallback = null
        castSessionListener?.let { listener ->
            runCatching {
                CastContext.getSharedInstance(this).sessionManager
                    .removeSessionManagerListener(listener, CastSession::class.java)
            }
        }
        castSessionListener = null
    }

    private fun endCastSession(reason: String) {
        castCompatibilityStreamer?.stop()
        castCompatibilityStreamer = null
        runCatching {
            val castContext = CastContext.getSharedInstance(this)
            val hadSession = castContext.sessionManager.currentCastSession != null
            castContext.sessionManager.endCurrentSession(true)
            castMediaRouter?.unselect(MediaRouter.UNSELECT_REASON_STOPPED)
            OperationLog.i(this, "Cast", "Cast stop requested reason=$reason active=$hadSession")
        }.onFailure { error ->
            OperationLog.e(this, "Cast", "unable to stop Cast reason=$reason", error)
        }
    }

    private fun menuHint(textValue: String): View = TextView(this).apply {
        text = textValue
        textSize = 14f
        setTextColor(Color.rgb(202, 196, 208))
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
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
                    .onFailure {
                        OperationLog.w(
                            this,
                            "InputRouting",
                            "external display disconnect restoration failed",
                            it
                        )
                    }
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
        panel.addView(
            menuTitle(
                NativeStrings.text("nativeResolution"),
                NativeStrings.text("nativeReturn")
            ) { showMainMenu(panel) })
        val portrait = targetWidth < targetHeight
        fun oriented(width: Int, height: Int): Pair<Int, Int> =
            if (portrait) height to width else width to height
        resolutionProfiles().forEach { item ->
            val size = oriented(item.width, item.height)
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(
                actionButton(
                if (item.device) R.drawable.ic_smartphone else R.drawable.ic_monitor,
                "${if (item.device) NativeStrings.text("nativeDevice") else "${item.width} × ${item.height}"}   ${item.density} dpi"
            ) {
                if (demoMode) demoExplanation(
                    "${size.first} × ${size.second} / ${item.density} ${
                        NativeStrings.text(
                            "nativeSwitchToResolutionSuffix"
                        )
                    }"
                )
                else {
                    saveSelectedResolution(item.id)
                    resizeActiveDisplay(
                        effectiveConfig(
                            Config(
                                size.first,
                                size.second,
                                item.density,
                                secureDisplay,
                                showSystemDecorations
                            )
                        ),
                        "resolution selected from overlay"
                    )
                }
            }, LinearLayout.LayoutParams(0, dp(50), 1f)
            )
            row.addView(
                editButton { showResolutionEditor(panel, item) },
                LinearLayout.LayoutParams(dp(50), dp(50)).apply { leftMargin = dp(6) })
            panel.addView(row, LinearLayout.LayoutParams(-1, dp(50)).apply { bottomMargin = dp(8) })
        }
        panel.addView(actionButton(R.drawable.ic_add, NativeStrings.text("nativeAddCustomResolution")) {
            showResolutionEditor(panel, null)
        })
    }

    private fun rotate180PreferenceKey(portrait: Boolean): String =
        if (portrait) KEY_ROTATE_180_PORTRAIT else KEY_ROTATE_180_LANDSCAPE

    private fun applyHostDisplayOrientation(portrait: Boolean) {
        val reverse = getSharedPreferences(PREFS, MODE_PRIVATE)
            .getBoolean(rotate180PreferenceKey(portrait), false)
        MainActivity.setDisplayOrientation(portrait, reverse)
    }

    private fun displayRotationFor(@Suppress("UNUSED_PARAMETER") portrait: Boolean = requestedPortrait): Int = 0

    private fun updateCursorPosition(x: Float = cursorX, y: Float = cursorY) {
        val normalizedX = x / targetWidth.coerceAtLeast(1)
        val normalizedY = y / targetHeight.coerceAtLeast(1)
        cursorView?.update(normalizedX, normalizedY)
    }

    private fun toggleDisplayRotation180() {
        val portrait = requestedPortrait
        val key = rotate180PreferenceKey(portrait)
        val preferences = getSharedPreferences(PREFS, MODE_PRIVATE)
        val enabled = !preferences.getBoolean(key, false)
        preferences.edit().putBoolean(key, enabled).apply()
        // Rotate the physical host display. WindowManager then transforms the
        // Surface, overlay controls, hit regions, and gesture coordinates as
        // one coherent display instead of leaving input in the old geometry.
        MainActivity.setDisplayOrientation(portrait, enabled)
        applyDisplayRotation(0)
        forcePhoneRotation(portrait, force = true)
        OperationLog.i(
            this,
            "Orientation",
            "180-degree rotation changed portrait=$portrait enabled=$enabled display=$targetDisplayId"
        )
    }

    private fun showResolutionEditor(panel: LinearLayout, existing: ResolutionProfile?) {
        animateMenuResize(panel)
        panel.removeAllViews()
        panel.addView(
            menuTitle(
                if (existing == null) NativeStrings.text("nativeAddResolution") else NativeStrings.text(
                    "nativeEditResolution"
                ), NativeStrings.text("nativeReturn")
            ) { showResolutionMenu(panel) })
        val width = numberField(
            (existing?.width ?: targetWidth).toString(),
            NativeStrings.text("nativeWidth")
        ).apply { isEnabled = existing?.device != true }
        val height = numberField(
            (existing?.height ?: targetHeight).toString(),
            NativeStrings.text("nativeHeight")
        ).apply { isEnabled = existing?.device != true }
        val dpi = numberField((existing?.density ?: density).toString(), "dpi")
        val fields = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fields.addView(width, LinearLayout.LayoutParams(0, dp(52), 1f))
        fields.addView(height, LinearLayout.LayoutParams(0, dp(52), 1f).apply { leftMargin = dp(6) })
        fields.addView(dpi, LinearLayout.LayoutParams(0, dp(52), 1f).apply { leftMargin = dp(6) })
        panel.addView(fields, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(8) })
        panel.addView(
            actionButton(
                R.drawable.ic_chevron,
                if (existing == null) NativeStrings.text("nativeAddAndApply") else NativeStrings.text("nativeSaveAndApply")
            ) {
                val w = width.text.toString().toIntOrNull()
                val h = height.text.toString().toIntOrNull()
                val d = dpi.text.toString().toIntOrNull()
                if (w != null && h != null && d != null && w in 480..7680 && h in 480..7680 && d in 80..640) {
                    val updated = ResolutionProfile(
                        existing?.id ?: "custom_${System.currentTimeMillis()}",
                        w,
                        h,
                        d,
                        existing?.device == true
                    )
                    saveResolutionProfile(updated)
                    saveSelectedResolution(updated.id)
                    val portrait = targetWidth < targetHeight
                    resizeActiveDisplay(
                        effectiveConfig(
                            Config(
                                if (portrait) h else w,
                                if (portrait) w else h,
                                d,
                                secureDisplay,
                                showSystemDecorations
                            )
                        ),
                        "custom resolution applied from overlay"
                    )
                }
            })
        if (existing != null && !existing.device) panel.addView(
            actionButton(
                R.drawable.ic_stop,
                NativeStrings.text("nativeRemoveThisResolution"),
                true
            ) {
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
            if (action != null) addView(
                TextView(this@MirrorService).apply {
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
                )
            )
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

    private fun actionButton(
        icon: Int = R.drawable.ic_chevron,
        label: String,
        destructive: Boolean = false,
        action: () -> Unit
    ): TextView =
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
        // Use the public numeric input type so Samsung IME and hardware
        // keyboards both deliver editable text to the quick-menu fields.
        // The previous literal was interpreted as a non-editable field by
        // some vendor builds.
        inputType = InputType.TYPE_CLASS_NUMBER
        isSingleLine = true
        setSelectAllOnFocus(false)
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

    private fun trackpad(
        event: MotionEvent,
        sourceView: View,
        forceCursorMode: Boolean = false,
        allowVirtualPointer: Boolean = false,
        hapticView: View? = null
    ): Boolean {
        val useDirectTouch = directTouch && !forceCursorMode
        val useVirtualMouse = (if (allowVirtualPointer) {
            laptopTrackpadInputActive()
        } else {
            virtualMouseInputActive()
        }) && !useDirectTouch
        recordTouchRouting(event, useDirectTouch)
        maxPointers = maxOf(maxPointers, event.pointerCount)
        val rawBridgeOwnsSource = sourceView === surfaceView ||
                sourceView === laptopTrackpadView
        if (rawBridgeOwnsSource &&
            rawTouchscreenBridgeConsumesTouchSurface(sourceView)
        ) {
            // InputDispatcher may cancel this accessibility-window stream as
            // soon as the virtual touchpad moves the system pointer. The raw
            // EventHub stream remains continuous, so it is the sole producer
            // for this gesture and the overlay must never duplicate frames.
            if (event.actionMasked == MotionEvent.ACTION_DOWN ||
                event.actionMasked == MotionEvent.ACTION_CANCEL
            ) {
                Log.i(
                    logTag,
                    "overlay touch ignored by raw bridge action=${MotionEvent.actionToString(event.action)} " +
                            "pointers=${event.pointerCount} readerReady=$touchscreenReaderReady " +
                            "surface=${if (sourceView === laptopTrackpadView) "laptop_trackpad" else "fullscreen_surface"}"
                )
            }
            return true
        }
        val nativeTouchpadAvailable = useVirtualMouse &&
                virtualPointerRegisteredProfile == "touchpad"
        if (experimentalMultiTouch && !nativeTouchpadAvailable &&
            handleExperimentalEdgeGesture(event)
        ) {
            if (nativeTouchpadGestureActive || virtualTouchpadActiveContactCount() > 0) {
                finishVirtualTouchpadGesture(
                    "dextop_edge_gesture_intercept",
                    allowVirtualPointer
                )
            }
            return true
        }
        if (useDirectTouch && experimentalMultiTouch) {
            injectDirectTouch(event)
            return true
        }
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            nativeTouchpadGestureActive = nativeTouchpadAvailable
            if (nativeTouchpadGestureActive) {
                moved = false
                twoFinger = false
                threeFinger = false
                scrolling = false
                maxPointers = 1
                longPressTriggered = false
                longPressRunnable?.let { root?.removeCallbacks(it) }
                longPressRunnable = null
            }
        }
        if (nativeTouchpadGestureActive) {
            // Dextop's configured three-finger command remains an application
            // gesture. End the two-contact stream before intercepting the
            // third finger so InputReader never retains a ghost contact.
            if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN &&
                event.pointerCount >= 3
            ) {
                threeFinger = true
                moved = true
                finishVirtualTouchpadGesture("dextop_three_finger_intercept", allowVirtualPointer)
                OperationLog.i(
                    this,
                    "InputRouting",
                    "native touchpad stream handed to Dextop three-finger gesture"
                )
                Log.i(logTag, "native touchpad stream handed to Dextop three-finger gesture")
                return true
            }
            // Tap, pointer acceleration, and two-finger scrolling are all
            // interpreted by Android's TouchpadInputMapper from these contacts.
            // Do not run Dextop's click or REL wheel paths as well.
            virtualTouchpadMotionEvent(event, sourceView, allowVirtualPointer)
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
                twoFingerTravelX = 0f
                twoFingerTravelY = 0f
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
                        performLaptopHaptic(hapticView, strong = true)
                        performLongPressGesture(allowVirtualPointer)
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
                    if (scrolling) injectTouch(MotionEvent.ACTION_UP, scrollX, scrollY)
                    scrolling = false
                    return true
                }
                if (event.pointerCount == 2) {
                    twoFinger = true
                    twoFingerTravelX = 0f
                    twoFingerTravelY = 0f
                    lastScrollX = event.getX(0)
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
                    val x = event.getX(0)
                    val y = event.getY(0)
                    val dx = x - lastScrollX
                    val dy = y - lastScrollY
                    twoFingerTravelX += dx
                    twoFingerTravelY += dy
                    val thresholdReached = scrolling ||
                            hypot(twoFingerTravelX, twoFingerTravelY) >= dp(10)
                    if (thresholdReached) {
                        if (useVirtualMouse) {
                            scrolling = true
                            virtualMouseHorizontalScroll(dx, allowVirtualPointer)
                            virtualMouseScroll(dy, allowVirtualPointer)
                        } else {
                            if (!scrolling) {
                                scrolling = true
                                scrollX = cursorX
                                scrollY = cursorY
                                injectTouch(MotionEvent.ACTION_DOWN, scrollX, scrollY)
                            }
                            scrollX = (scrollX + dx * 2f).coerceIn(0f, targetWidth - 1f)
                            scrollY = (scrollY + dy * 2f).coerceIn(0f, targetHeight - 1f)
                            injectTouch(MotionEvent.ACTION_MOVE, scrollX, scrollY)
                        }
                        moved = true
                    }
                    lastScrollX = x
                    lastScrollY = y
                } else if (!twoFinger) {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    lastX = event.x
                    lastY = event.y
                    if (hypot(event.x - touchStartX, event.y - touchStartY) > dp(4)) {
                        moved = true
                        if (!longPressTriggered) longPressRunnable?.let { root?.removeCallbacks(it) }
                    }
                    moveCursor(dx * 1.1f, dy * 1.1f, allowVirtualPointer)
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                if (!threeFinger && scrolling) {
                    if (!useVirtualMouse) injectTouch(MotionEvent.ACTION_UP, scrollX, scrollY)
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
                } else if (scrolling && !useVirtualMouse) injectTouch(MotionEvent.ACTION_UP, scrollX, scrollY)
                if (completedDirectTouch) {
                    Unit
                } else if (threeFinger || maxPointers >= 3) {
                    // A real touchpad profile has already bypassed the legacy
                    // edge recognizer above. Always retain Dextop's configured
                    // three-finger action as the emergency menu/exit path.
                    if (!experimentalMultiTouch || nativeTouchpadAvailable) {
                        performConfiguredGesture()
                    }
                } else if (longPressTriggered && dragHeld) {
                    if (useVirtualMouse) {
                        virtualMouseButton("BTN_LEFT", false, allowVirtualPointer)
                    } else injectTouch(MotionEvent.ACTION_UP, cursorX, cursorY)
                    dragHeld = false
                } else if (twoFinger && !moved && !scrolling) {
                    performLaptopHaptic(hapticView, strong = true)
                    performTwoFingerGesture(allowVirtualPointer)
                } else if (!twoFinger && !moved && !dragHeld && SystemClock.uptimeMillis() - downTime < 250) {
                    if (useDirectTouch) moveCursorToTouch(event.x, event.y)
                    performLaptopHaptic(hapticView)
                    leftClick(allowVirtualPointer)
                }
                maxPointers = 0
                twoFingerTravelX = 0f
                twoFingerTravelY = 0f
                twoFinger = false
                threeFinger = false
                scrolling = false
            }

            MotionEvent.ACTION_CANCEL -> {
                longPressRunnable?.let { root?.removeCallbacks(it) }
                if (directTouchHeld) injectTouch(MotionEvent.ACTION_UP, cursorX, cursorY)
                directTouchHeld = false
                if (scrolling && !useVirtualMouse) injectTouch(MotionEvent.ACTION_UP, scrollX, scrollY)
                if (dragHeld) {
                    if (useVirtualMouse) {
                        virtualMouseButton("BTN_LEFT", false, allowVirtualPointer)
                    } else injectTouch(MotionEvent.ACTION_UP, cursorX, cursorY)
                }
                dragHeld = false
                maxPointers = 0
                twoFingerTravelX = 0f
                twoFingerTravelY = 0f
                twoFinger = false
                threeFinger = false
                scrolling = false
            }
        }
        return true
    }

    /**
     * Use Android's own haptic policy for the laptop deck.  Calling
     * View.performHapticFeedback keeps this compatible with OEM vibration
     * settings and avoids injecting a separate vibration permission or a
     * device-specific amplitude. The predefined heavy click is deliberately
     * stronger than KEYBOARD_TAP on Samsung and Pixel devices while still
     * following the system's vibrator policy. The keyboard demo is
     * interactive, so it uses the same feedback as the live laptop deck.
     */
    private fun performLaptopHaptic(view: View?, strong: Boolean = false) {
        if (!laptopModeActive) return
        // Only the dedicated laptop surfaces opt into haptics. The upper
        // mirrored phone surface remains a normal touch target and must not
        // vibrate merely because the deck is visible.
        val target = view ?: return
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        if (vibrator?.hasVibrator() == true) {
            val effect = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                } else {
                    @Suppress("DEPRECATION")
                    VibrationEffect.createOneShot(
                        if (strong) 42L else 30L,
                        if (strong) 230 else 180
                    )
                }
            }.getOrElse {
                VibrationEffect.createOneShot(
                    if (strong) 42L else 30L,
                    if (strong) 230 else 180
                )
            }
            vibrator.vibrate(effect)
        } else {
            // Keep a visual/input-device fallback for devices without a
            // vibrator, or for environments where the service cannot access
            // the vibrator manager.
            val constant = if (strong) {
                HapticFeedbackConstants.CONTEXT_CLICK
            } else {
                HapticFeedbackConstants.KEYBOARD_TAP
            }
            target.performHapticFeedback(constant)
        }
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

    private fun performTwoFingerGesture(allowVirtualPointer: Boolean = false) {
        val action = getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
            .getString("flutter.gesture_two_finger", "right_click")
        when (action) {
            "home" -> launchHome()
            "menu" -> toggleMenu()
            else -> rightClick(allowVirtualPointer)
        }
    }

    private fun performLongPressGesture(allowVirtualPointer: Boolean = false) {
        val action = getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
            .getString("flutter.gesture_long_press", "drag")
        when (action) {
            "right_click" -> rightClick(allowVirtualPointer)
            "menu" -> toggleMenu()
            else -> {
                dragHeld = true
                if (!virtualMouseButton("BTN_LEFT", true, allowVirtualPointer)) {
                    injectTouch(MotionEvent.ACTION_DOWN, cursorX, cursorY)
                }
                cursorView?.pulse()
            }
        }
    }

    private fun moveCursorToTouch(x: Float, y: Float) {
        val view = surfaceView ?: return
        if (view.width <= 0 || view.height <= 0) return
        cursorX = x / view.width * targetWidth
        cursorY = y / view.height * targetHeight
        if (!directTouch && !virtualMouseInputActive()) {
            updateCursorPosition()
        }
    }

    private fun setSavedTouchMode(useTapPosition: Boolean) {
        // Finish the previous gesture before changing the ownership of the
        // input stream.  In particular, release a held mouse button before
        // removing the uinput device so the next app cannot inherit it.
        cancelDesktopTouchStream()
        directTouch = useTapPosition
        physicalMouseActive = false
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putBoolean(KEY_DIRECT_TOUCH, useTapPosition)
            .apply()
        if (useTapPosition) {
            // Tap mode must not merely ignore virtual mouse events: remove
            // the device from the system so Android cannot keep pointer focus
            // on the last mouse-controlled application.
            stopVirtualMouse()
            updateVirtualCursorVisibility()
        } else {
            startVirtualMouse()
            updateVirtualCursorVisibility()
        }
        OperationLog.i(
            this,
            "InputRouting",
            "touch mode changed directTouch=$directTouch inputMode=${currentInputMode()} " +
                    displayGeometrySnapshot("touch_mode_changed")
        )
    }

    private fun activateTouchInput() {
        if (overlayTextInputActive) return
        surfaceView?.releasePointerCapture()
        setOverlayFocusable(false)
        physicalMouseActive = false
        if (directTouch) {
            // A stale virtual pointer can survive a mode change until the
            // InputReader removal callback arrives. Enforce the tap-mode
            // boundary when the first phone-surface touch is received as
            // well, including a device that was attached by the laptop deck.
            cancelDesktopTouchStream()
            stopVirtualMouse()
            updateVirtualCursorVisibility()
            OperationLog.i(
                this,
                "InputRouting",
                "phone touch surface claimed input; virtual pointer disconnected"
            )
        } else {
            // Reconnect lazily if the device was removed while the session
            // was in tap mode or during a display/posture transition.
            startVirtualMouse()
        }
        // ACTION_DOWN on the phone display hands cursor ownership back to the
        // touchpad immediately, even if the mouse remains connected.
        updateVirtualCursorVisibility()
        if (!directTouch && !virtualMouseInputActive()) updateCursorPosition()
        OperationLog.i(
            this,
            "InputRouting",
            "touch input activated directTouch=$directTouch inputMode=${currentInputMode()} " +
                    displayGeometrySnapshot("touch_input_activated")
        )
    }

    private fun activateLaptopTrackpad() {
        if (overlayTextInputActive) return
        surfaceView?.releasePointerCapture()
        setOverlayFocusable(false)
        physicalMouseActive = false
        // Cursor mode may have been selected while the laptop deck was
        // already visible. Conversely, tap mode on the phone surface must
        // not prevent the laptop trackpad from attaching its system pointer.
        // Connect lazily on the first trackpad touch so a mode change never
        // races uinput registration.
        startVirtualMouse()
        updateVirtualCursorVisibility()
        OperationLog.i(
            this,
            "InputRouting",
            "laptop trackpad claimed input directTouch=$directTouch " +
                    "pointerProfile=${activeVirtualPointerProfile()} " +
                    "pointerReady=${laptopTrackpadInputActive()}"
        )
        if (!directTouch && !virtualMouseInputActive()) {
            updateCursorPosition()
        }
    }

    private fun activatePhysicalMouse() {
        if (overlayTextInputActive) return
        val wasActive = physicalMouseActive
        physicalMouseActive = true
        // Physical mice use Android's pointer only. Dextop's cursor is reserved
        // exclusively for touch-panel trackpad mode.
        cursorView?.visibility = View.GONE
        setOverlayFocusable(true)
        surfaceView?.post {
            surfaceView?.requestFocus()
            surfaceView?.requestPointerCapture()
        }
        if (!wasActive) {
            OperationLog.i(
                this,
                "InputRouting",
                "physical mouse activated inputMode=${currentInputMode()} " +
                        displayGeometrySnapshot("physical_mouse_activated")
            )
        }
    }

    private fun handlePhysicalMouseEvent(event: MotionEvent, view: View): Boolean {
        if (isVirtualMouseEvent(event)) return true
        if (!event.isFromSource(InputDevice.SOURCE_MOUSE)) return false
        if (event.actionMasked == MotionEvent.ACTION_MOVE ||
            event.actionMasked == MotionEvent.ACTION_HOVER_MOVE
        ) activatePhysicalMouse()
        return forwardMouseEvent(event, view)
    }

    private fun handleCapturedMouseEvent(event: MotionEvent): Boolean {
        if (isVirtualMouseEvent(event)) return true
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
            field.setSelection(field.text?.length ?: 0)
            val input = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            input.restartInput(field)
            input.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
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
                updateVirtualCursorVisibility()
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
            device.name != VIRTUAL_MOUSE_NAME && device.name != VIRTUAL_TOUCHPAD_NAME &&
                    device.sources and InputDevice.SOURCE_MOUSE == InputDevice.SOURCE_MOUSE
        } == true
    }

    private fun isVirtualMouseEvent(event: MotionEvent): Boolean {
        if (event.deviceId < 0) return false
        return when (InputDevice.getDevice(event.deviceId)?.name) {
            VIRTUAL_MOUSE_NAME, VIRTUAL_TOUCHPAD_NAME -> true
            else -> false
        }
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
        if (suspendedForLockScreen) {
            screenLifecycleGeneration += 1
            keyguardLockObservedSinceScreenOff = false
            unlockCandidateSince = 0L
            unlockResumeScheduled = false
            Log.i(
                logTag,
                "screen off reaffirmed while suspended; cancelled pending unlock " +
                        "screenGeneration=$screenLifecycleGeneration"
            )
            return
        }
        screenLifecycleGeneration += 1
        keyguardLockObservedSinceScreenOff = false
        unlockCandidateSince = 0L
        unlockResumeScheduled = false
        suspendedConfig = Config(targetWidth, targetHeight, density, secureDisplay, showSystemDecorations)
        suspendedForLockScreen = true
        stopHostDisplayMonitor()
        setPhoneNavigationDisabled(false)
        releasePhoneRotation()
        MainActivity.restoreOrientation()
        runCatching { desktopModeConfigurator.restore() }
            .onFailure { Log.e(logTag, "lock-screen settings restoration failed", it) }
        removeWindow()
        targetDisplayId = -1
        Log.i(
            logTag,
            "session suspended; all display and overlay windows removed " +
                    "screenGeneration=$screenLifecycleGeneration"
        )
    }

    private fun resumeAfterUnlock(reason: String) {
        if (!suspendedForLockScreen || unlockResumeScheduled) return
        val previous = suspendedConfig ?: return
        val bounds = windowManager?.currentWindowMetrics?.bounds
        val config = if (shouldFollowHostDisplay() && bounds != null &&
            bounds.width() >= 480 && bounds.height() >= 480
        ) {
            configForHostGeometry(
                previous,
                bounds.width(),
                bounds.height(),
                resources.configuration.densityDpi
            )
        } else previous
        val generation = screenLifecycleGeneration
        unlockResumeScheduled = true
        Handler(mainLooper).postDelayed({
            if (!suspendedForLockScreen || !unlockResumeScheduled ||
                generation != screenLifecycleGeneration
            ) return@postDelayed
            Log.i(logTag, "unlock resume executing reason=$reason screenGeneration=$generation")
            start(config)
        }, 250)
        Log.i(
            logTag,
            "unlock confirmed; session recreation scheduled reason=$reason " +
                    "screenGeneration=$generation"
        )
    }

    private fun waitForConfirmedUnlock(generation: Int, attempt: Int = 0) {
        if (!suspendedForLockScreen || unlockResumeScheduled ||
            generation != screenLifecycleGeneration
        ) return
        val keyguard = getSystemService(KeyguardManager::class.java)
        val locked = keyguard.isDeviceLocked || keyguard.isKeyguardLocked
        val secure = keyguard.isDeviceSecure
        val interactive = getSystemService(PowerManager::class.java).isInteractive
        val now = SystemClock.elapsedRealtime()
        if (locked) {
            if (!keyguardLockObservedSinceScreenOff) {
                Log.i(
                    logTag,
                    "keyguard lock observed after screen off attempt=$attempt " +
                            "screenGeneration=$generation"
                )
            }
            keyguardLockObservedSinceScreenOff = true
            unlockCandidateSince = 0L
        } else if (interactive && (!secure || keyguardLockObservedSinceScreenOff)) {
            if (unlockCandidateSince == 0L) {
                unlockCandidateSince = now
                Log.i(
                    logTag,
                    "unlock candidate observed attempt=$attempt secure=$secure " +
                            "lockObserved=$keyguardLockObservedSinceScreenOff " +
                            "screenGeneration=$generation"
                )
            }
            val stableFor = now - unlockCandidateSince
            if (stableFor >= 750L) {
                Log.i(
                    logTag,
                    "keyguard reports stably unlocked attempt=$attempt stableForMs=$stableFor " +
                            "screenGeneration=$generation"
                )
                resumeAfterUnlock("stable_keyguard_confirmation")
                return
            }
        } else {
            unlockCandidateSince = 0L
            if (attempt == 0 || attempt == 4 || attempt == 20) {
                Log.i(
                    logTag,
                    "unlock confirmation waiting attempt=$attempt interactive=$interactive " +
                            "secure=$secure locked=$locked " +
                            "lockObserved=$keyguardLockObservedSinceScreenOff " +
                            "screenGeneration=$generation"
                )
            }
        }
        if (attempt < 120) {
            Handler(mainLooper).postDelayed(
                { waitForConfirmedUnlock(generation, attempt + 1) },
                250
            )
        } else {
            Log.w(
                logTag,
                "unlock wait timed out; leaving session suspended " +
                        "secure=$secure lockObserved=$keyguardLockObservedSinceScreenOff " +
                        "screenGeneration=$generation"
            )
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
            refreshPrivilegedInputConfig("menu_opened")
            panel.alpha = 0f
            panel.scaleX = .94f
            panel.scaleY = .94f
            panel.post {
                if (menuUsesLandscapeLayout()) {
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
            refreshPrivilegedInputConfig("menu_closed")
            menuScrim?.animate()?.alpha(0f)?.setDuration(180)?.start()
            val animation = if (menuUsesLandscapeLayout()) {
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

    private fun moveCursor(
        dx: Float,
        dy: Float,
        allowVirtualPointer: Boolean = false
    ) {
        val useVirtualPointer = virtualPointerInputActive(allowVirtualPointer)
        // Pointer sensitivity is deliberately fixed.  Older releases exposed
        // DPI and acceleration preferences, but those values could persist in
        // SharedPreferences and make input unusable.  Always use raw deltas.
        val effectiveDx = dx
        val effectiveDy = dy
        cursorX = (cursorX + effectiveDx).coerceIn(0f, targetWidth - 1f)
        cursorY = (cursorY + effectiveDy).coerceIn(0f, targetHeight - 1f)
        if (useVirtualPointer) {
            virtualMouseMove(effectiveDx, effectiveDy, allowVirtualPointer)
        } else {
            updateCursorPosition()
            if (dragHeld) injectTouch(MotionEvent.ACTION_MOVE, cursorX, cursorY)
        }
    }

    /** Tracks the injection position without involving Dextop's touch cursor. */
    private fun movePhysicalPointer(dx: Float, dy: Float) {
        cursorX = (cursorX + dx).coerceIn(0f, targetWidth - 1f)
        cursorY = (cursorY + dy).coerceIn(0f, targetHeight - 1f)
    }

    private fun leftClick(allowVirtualPointer: Boolean = false) {
        if (dragHeld) return
        if (virtualPointerInputActive(allowVirtualPointer)) {
            virtualMouseButton("BTN_LEFT", true, allowVirtualPointer)
            virtualMouseButton("BTN_LEFT", false, allowVirtualPointer)
        } else {
            injectTouch(MotionEvent.ACTION_DOWN, cursorX, cursorY)
            injectTouch(MotionEvent.ACTION_UP, cursorX, cursorY)
            if (!directTouch) cursorView?.pulse()
        }
    }

    private fun rightClick(allowVirtualPointer: Boolean = false) {
        if (dragHeld || targetDisplayId < 0) return
        if (virtualPointerInputActive(allowVirtualPointer)) {
            virtualMouseButton("BTN_RIGHT", true, allowVirtualPointer)
            virtualMouseButton("BTN_RIGHT", false, allowVirtualPointer)
            return
        }
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
        if (virtualMouseInputActive()) {
            virtualMouseButton("BTN_LEFT", true)
            root?.postDelayed({ virtualMouseButton("BTN_LEFT", false) }, 600)
        } else {
            injectTouch(MotionEvent.ACTION_DOWN, cursorX, cursorY)
            root?.postDelayed({ injectTouch(MotionEvent.ACTION_UP, cursorX, cursorY) }, 600)
        }
    }

    private fun toggleDrag() {
        dragHeld = !dragHeld
        if (virtualMouseInputActive()) {
            virtualMouseButton("BTN_LEFT", dragHeld)
        } else {
            injectTouch(if (dragHeld) MotionEvent.ACTION_DOWN else MotionEvent.ACTION_UP, cursorX, cursorY)
        }
    }

    private fun changeOrientation() {
        // targetWidth/targetHeight are the live pane dimensions in laptop
        // mode, not the user's full-screen orientation. Toggle the explicit
        // Dextop choice and swap the saved pre-laptop profile instead.
        val portrait = !requestedPortrait
        val base = laptopBaseConfig ?: Config(
            targetWidth,
            targetHeight,
            density,
            secureDisplay,
            showSystemDecorations
        )
        OperationLog.i(
            this,
            "Orientation",
            "requested portrait=$portrait from=${targetWidth}x$targetHeight " +
                    displayGeometrySnapshot("orientation_requested")
        )
        applyHostDisplayOrientation(portrait)
        requestedPortrait = portrait
        forcePhoneRotation(portrait)
        val config = Config(base.height, base.width, base.density, base.secure, base.decorations)
        orientationRebuildInProgress = true
        root?.postDelayed({
            runCatching { start(config) }
                .onSuccess {
                    OperationLog.i(
                        this,
                        "Orientation",
                        "rebuild started portrait=$portrait target=${config.width}x${config.height} " +
                                displayGeometrySnapshot("orientation_rebuild_started")
                    )
                }
                .onFailure {
                    OperationLog.e(this, "Orientation", "rebuild failed portrait=$portrait", it)
                }
        }, 350)
    }

    private fun injectKey(keyCode: Int, metaState: Int = 0) {
        if (!injectLaptopKeyboardEvent(keyCode, KeyEvent.ACTION_DOWN, metaState, 0)) return
        injectLaptopKeyboardEvent(keyCode, KeyEvent.ACTION_UP, metaState, 0)
    }

    private fun injectLaptopKeyboardEvent(
        keyCode: Int,
        action: Int,
        metaState: Int,
        repeatCount: Int
    ): Boolean {
        if (!laptopKeyboardReady) {
            Log.w(
                logTag,
                "native laptop keyboard event rejected before publication " +
                        "action=$action repeat=$repeatCount deviceId=$laptopKeyboardDeviceId"
            )
            scheduleLaptopKeyboardReadyCheck(laptopKeyboardGeneration, 0)
            return false
        }
        val injected = privilegedInputClient.injectKeyboard(keyCode, action, metaState, repeatCount)
        if (!injected) {
            Log.e(
                logTag,
                "native laptop keyboard injection failed action=$action repeat=$repeatCount " +
                        "deviceId=$laptopKeyboardDeviceId"
            )
        }
        return injected
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

        if (virtualTouchpadActiveContactCount() > 0) {
            finishVirtualTouchpadGesture("desktop_touch_stream_cancelled", allowDirectTouch = true)
        }
        if (injectedDirectTouchActive) cancelInjectedDirectTouch()
        if (targetDisplayId >= 0 && (directTouchHeld || scrolling || dragHeld) &&
            !virtualMouseInputActive()
        ) {
            injectTouch(MotionEvent.ACTION_CANCEL, cursorX, cursorY)
        }
        if (virtualMouseInputActive() && dragHeld) virtualMouseButton("BTN_LEFT", false)
        injectedDirectTouchActive = false
        directTouchHeld = false
        scrolling = false
        dragHeld = false
        moved = false
        twoFinger = false
        twoFingerTravelX = 0f
        twoFingerTravelY = 0f
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
        OperationLog.i(this, "DisplayGeometry", displayGeometrySnapshot("surface_created", view.width, view.height))
        if (menu != null) refreshMenuGeometryAfterDisplayChange()
        if (targetDisplayId >= 0 &&
            getSystemService(DisplayManager::class.java).getDisplay(targetDisplayId) != null
        ) {
            reattachExistingDisplay(view.width, view.height)
        } else {
            createDisplay(holder.surface, view.width, view.height)
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        OperationLog.i(this, "DisplayGeometry", displayGeometrySnapshot("surface_changed", width, height))
        if (menu != null) refreshMenuGeometryAfterDisplayChange()
        if (mirrorDisplayId < 0) {
            if (targetDisplayId >= 0 &&
                getSystemService(DisplayManager::class.java).getDisplay(targetDisplayId) != null
            ) {
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
        OperationLog.i(this, "DisplayGeometry", displayGeometrySnapshot("surface_destroyed"))
        releaseMirror()
    }

    /** Reconnects a recreated host Surface without removing the desktop display or its tasks. */
    private fun reattachExistingDisplay(width: Int, height: Int) {
        if (displayCreationInProgress || mirrorDisplayId >= 0 || targetDisplayId < 0) return
        displayCreationInProgress = true
        val strategyOverride = configuredMirrorStrategyOverride()
        runCatching {
            attachMirror(width, height, strategyOverride)
            mirrorDisplayId = targetDisplayId
        }.onSuccess {
            displayCreationInProgress = false
            startVirtualMouse()
            updateVirtualCursorVisibility()
            scheduleHostDisplayReconfiguration("host Surface recreated", width, height)
            // A Surface recreation can happen during a fold/half-open handoff
            // while the laptop deck is already visible.  The first geometry
            // pass may have run before the recreated surface was attached;
            // synchronize the measured upper pane again after the mirror is
            // connected so the desktop is not left at the full-panel size.
            if (laptopModeActive) {
                root?.postDelayed({
                    if (active && laptopModeActive && targetDisplayId >= 0) {
                        applyLaptopGeometryWhenLaidOut(true)
                    }
                }, 80L)
                OperationLog.i(
                    this,
                    "LaptopMode",
                    "host Surface recreated while laptop mode active; queued pane geometry synchronization"
                )
            }
            scheduleTopologyReapplyAfterReconnect()
            OperationLog.i(
                this,
                "DisplayBackend",
                "reattached host Surface to display=$targetDisplayId host=${width}x$height; tasks retained"
            )
            OperationLog.i(this, "DisplayGeometry", displayGeometrySnapshot("surface_reattached", width, height))
        }.onFailure { error ->
            displayCreationInProgress = false
            mirrorDisplayId = -1
            restoreSoftwareCursorAfterMirrorFailure("existing display reattach")
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
            val staleOverlays = displayBackend.overlayDisplayIds()
            desktopModeConfigurator.applyForCurrentDevice()
            clearOverlayDisplayRequestTwice("before_display_create")
            root?.postDelayed({
                waitForOverlayRequestCleared(
                    existing,
                    staleOverlays,
                    width,
                    height,
                    0,
                    showSystemDecorations
                )
            }, 150)
        }.onFailure { error ->
            displayCreationInProgress = false
            OperationLog.e(this, "MirrorService", "display creation failed", error)
            Log.e(logTag, "display creation failed", error)
            completeStart(Result.failure(error))
            stop()
        }
    }

    /**
     * Creates the Dextop overlay without adding a host window to the phone.
     * Android Auto owns the destination Surface in this mode and attaches a
     * separate recording VirtualDisplay to the overlay once the display id is
     * published. This keeps the phone display untouched.
     */
    private fun createHeadlessDisplay() {
        if (targetDisplayId >= 0 || displayCreationInProgress) return
        displayCreationInProgress = true
        CapabilityProbe(this, privilegedAccess).run().forEach { (name, probe) ->
            OperationLog.i(this, "CapabilityProbe", "$name supported=${probe.supported} detail=${probe.detail}")
        }
        runCatching {
            desktopModeConfigurator.applyForCurrentDevice()
            // Remove a stale OverlayDisplayAdapter request left by an older
            // CARDEX build, then create an app-owned display directly on the
            // head-unit Surface. No phone-side Overlay window is involved.
            clearOverlayDisplayRequestTwice("before_hidden_auto_display_create")
            createDirectAutoDisplay(showSystemDecorations)
        }.onFailure { error ->
            displayCreationInProgress = false
            OperationLog.e(this, "AndroidAuto", "headless display creation failed", error)
            Log.e(logTag, "headless Auto display creation failed: ${error.message}", error)
            completeStart(Result.failure(error))
            stop()
        }
    }

    private fun createDirectAutoDisplay(decorations: Boolean) {
        val destination = autoDestinationSurface
            ?: error("The Dextop Car Companion destination surface is unavailable")
        val platform = VirtualDisplayPlatform.inspect()
        val service = privilegedAccess.service(
            "display",
            VirtualDisplayPlatform.MANAGER_INTERFACE
        )
        autoOwnedDisplay?.attachment?.release()
        val owned = platform.openOwned(
            service,
            destination,
            targetWidth,
            targetHeight,
            density,
            decorations
        )
        autoOwnedDisplay = owned
        targetDisplayId = owned.displayId
        mirrorDisplayId = owned.displayId
        showSystemDecorations = decorations
        clearInheritedDisplayOverrides(targetDisplayId)
        configureDisplay()
        if (!launchHome()) {
            if (!decorations) {
                OperationLog.w(
                    this,
                    "DesktopHome",
                    "hidden Auto HOME rejected; retrying with system decorations",
                    null
                )
                owned.attachment.release()
                autoOwnedDisplay = null
                targetDisplayId = -1
                mirrorDisplayId = -1
                createDirectAutoDisplay(true)
                return
            }
            error("The desktop HOME activity could not be launched")
        }
        displayCreationInProgress = false
        sessionJournal.running(targetDisplayId)
        runCatching {
            DisplayEnvironmentSettings(this).activateTopologyForOverlays(setOf(targetDisplayId))
        }.onFailure { OperationLog.w(this, "DisplayTopology", "Auto topology activation skipped", it) }
        completeStart(
            Result.success(
                mapOf(
                    "displayId" to targetDisplayId,
                    "width" to targetWidth,
                    "height" to targetHeight,
                    "density" to density,
                    "decorations" to showSystemDecorations
                )
            )
        )
        OperationLog.i(
            this,
            "AndroidAuto",
            "hidden direct Auto display ready display=$targetDisplayId ${targetWidth}x$targetHeight/$density"
        )
    }

    private fun postMainDelayed(delayMs: Long, action: () -> Unit) {
        android.os.Handler(mainLooper).postDelayed(action, delayMs)
    }

    /**
     * Settings.Global.overlay_display_devices is a request, not a synchronous
     * create/destroy API.  Issuing a new request while OverlayDisplayAdapter
     * is still removing the previous overlay allocates another display id on
     * several vendor builds.  Wait until every overlay that existed before
     * the clear has disappeared; if it does not, fail the start instead of
     * multiplying displays indefinitely.
     */
    private fun waitForOverlayRequestCleared(
        existing: Set<Int>,
        staleOverlays: Set<Int>,
        width: Int,
        height: Int,
        attempt: Int,
        decorations: Boolean
    ) {
        if (!active || stopping) return
        // No overlay request is active during this wait. Treat *any* overlay
        // still reported by DisplayManager as stale, including one whose id
        // was published just after the initial inventory snapshot.
        val autoOverlayIds = AndroidAutoMirrorActivity.autoOverlayDisplayIds()
        val remaining = displayBackend.overlayDisplayIds()
            .filterNot { it in autoOverlayIds }
            .toSet()
        if (remaining.isNotEmpty()) {
            if (attempt < 60) {
                postMainDelayed(100L) {
                    waitForOverlayRequestCleared(
                        existing,
                        staleOverlays,
                        width,
                        height,
                        attempt + 1,
                        decorations
                    )
                }
                return
            }
            val error = IllegalStateException(
                "Overlay display cleanup timed out; refusing to create a duplicate display " +
                        "ids=${remaining.sorted()} knownBeforeClear=${staleOverlays.sorted()}"
            )
            displayCreationInProgress = false
            OperationLog.e(this, "DisplayBackend", error.message ?: "overlay cleanup timed out", error)
            completeStart(Result.failure(error))
            stop()
            return
        }
        runCatching {
            displayBackend.requestDisplay(
                targetWidth,
                targetHeight,
                density,
                secureDisplay,
                decorations,
                AndroidAutoMirrorActivity.autoOverlaySpecs()
            )
            waitForOverlay(existing, width, height, 0)
        }.onFailure { error ->
            displayCreationInProgress = false
            OperationLog.e(this, "MirrorService", "display request failed", error)
            completeStart(Result.failure(error))
            stop()
        }
    }

    private fun waitForOverlay(existing: Set<Int>, width: Int, height: Int, attempt: Int) {
        // A phone and Auto session may request the same logical spec. Never
        // attach the phone Surface to the Auto-owned display merely because
        // that display was allocated after the phone inventory snapshot.
        val autoDisplayIds = AndroidAutoMirrorActivity.autoOverlayDisplayIds()
        val display = displayBackend.findCreatedDisplay(existing, autoDisplayIds)
        if (display == null) {
            if (attempt < 40) postMainDelayed(100L) {
                waitForOverlay(existing, width, height, attempt + 1)
            }
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
            if (!autoOnlySession) {
                val strategyOverride = configuredMirrorStrategyOverride()
                attachMirror(width, height, strategyOverride)
            } else {
                OperationLog.i(
                    this,
                    "AndroidAuto",
                    "overlay display created without phone host display=$targetDisplayId"
                )
            }
            mirrorDisplayId = targetDisplayId
            // Auto laptop detection can run from addWindow() before the
            // overlay display exists. In that case the first layout pass has
            // already split the host Surface, but applyLaptopGeometry...()
            // correctly deferred because targetDisplayId was still -1. Run
            // the pane synchronization once the display is attached so the
            // logical resolution and VirtualDisplay buffer use the measured
            // upper pane instead of the original full-screen profile.
            if (!autoOnlySession && laptopModeActive) {
                root?.postDelayed({
                    if (active && laptopModeActive && targetDisplayId >= 0) {
                        applyLaptopGeometryWhenLaidOut(true)
                    }
                }, 80L)
                OperationLog.i(
                    this,
                    "LaptopMode",
                    "display attached while laptop mode active; queued pane geometry synchronization"
                )
            }
            // Topology is an optional enhancement. A framework with a vendor
            // IDisplayManager fork may reject its hidden transaction (for
            // example with RESTRICT_DISPLAY_MODES); that must not abort an
            // otherwise usable VirtualDisplay session.
            runCatching {
                val topologyOverlays = buildSet {
                    if (mirrorDisplayId >= 0) add(mirrorDisplayId)
                    AndroidAutoMirrorActivity.autoOverlayDisplayIds().forEach(::add)
                }
                DisplayEnvironmentSettings(this).activateTopologyForOverlays(topologyOverlays)
            }.onFailure {
                OperationLog.w(this, "DisplayTopology", "topology activation skipped", it)
                Log.w(logTag, "topology activation skipped; mirroring remains active", it)
            }
            displayCreationInProgress = false
            sessionJournal.running(targetDisplayId)
            val externalDisplays = externalDisplayDetector.snapshot()
            physicalExternalDisplayConnected = physicalInputRoutingSupported && externalDisplays.connected
            val routedInputCount = if (!autoOnlySession && physicalExternalDisplayConnected) runCatching {
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
            OperationLog.i(this, "DisplayGeometry", displayGeometrySnapshot("mirror_attached", width, height))
            if (!autoOnlySession) startVirtualMouse()
            if (!autoOnlySession && physicalExternalDisplayConnected && routePhysicalMouseToDextop) {
                startRawMouseReader()
            } else {
                stopRawMouseReader()
            }
            root?.postDelayed({
                refreshActualRoutingState(display)
                menuPrimary?.let(::showMainMenu)
            }, 350)
            if (!autoOnlySession) menuPrimary?.let(::showMainMenu)
            cursorX = targetWidth / 2f
            cursorY = targetHeight / 2f
            if (!autoOnlySession) updateCursorPosition(targetWidth / 2f, targetHeight / 2f)
            if (!launchHome()) {
                // One UI 8 rejects HOME launches on Samsung-owned virtual
                // displays that do not advertise system decorations. Rebuild
                // the display once with that flag instead of tearing down a
                // session which otherwise mirrored successfully.
                if (retryHomeLaunchWithSystemDecorations(width, height)) {
                    return@runCatching
                }
                error("The desktop HOME activity could not be launched")
            }
            pendingPausedWorkspace?.takeUnless { autoOnlySession }?.let { workspace ->
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
            if (!autoOnlySession) root?.postDelayed({ ensurePhoneControlOnDefaultDisplay() }, 500)
            completeStart(
                Result.success(
                    mapOf(
                        "displayId" to targetDisplayId,
                        "width" to targetWidth,
                        "height" to targetHeight,
                        "density" to density,
                        "decorations" to showSystemDecorations
                    )
                )
            )
            if (orientationRebuildInProgress) {
                orientationRebuildInProgress = false
                OperationLog.i(
                    this,
                    "Orientation",
                    "rebuild completed target=${targetWidth}x$targetHeight/$density " +
                            displayGeometrySnapshot("orientation_rebuild_completed", width, height)
                )
            }
            Log.i(
                logTag,
                "Dextop layer attached target=$targetDisplayId ${targetWidth}x$targetHeight " +
                        "autoOnly=$autoOnlySession"
            )
        }.onFailure { error ->
            if (orientationRebuildInProgress) {
                orientationRebuildInProgress = false
                OperationLog.e(this, "Orientation", "rebuild failed during display attachment", error)
            }
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
        // Width/height define portrait or landscape; an optional persisted
        // half-turn is then applied independently for that orientation.
        val rotation = displayRotationFor()
        desktopModeConfigurator.configureDisplay(targetDisplayId)
        runCatching {
            type.getMethod(
                "setIgnoreOrientationRequest",
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType
            ).invoke(service, targetDisplayId, true)
        }.onSuccess {
            OperationLog.i(this, "Orientation", "ignore orientation request applied display=$targetDisplayId")
        }.onFailure {
            OperationLog.e(this, "Orientation", "orientation request lock failed display=$targetDisplayId", it)
            Log.e(logTag, "orientation request lock failed", it)
        }
        runCatching {
            type.getMethod(
                "setFixedToUserRotation",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            ).invoke(service, targetDisplayId, 2)
        }.onSuccess {
            OperationLog.i(this, "Orientation", "fixed rotation applied display=$targetDisplayId value=2")
        }.onFailure {
            OperationLog.e(this, "Orientation", "fixed rotation failed display=$targetDisplayId", it)
            Log.e(logTag, "fixed rotation failed", it)
        }
        applyDisplayRotation(rotation, service, type)
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

    private fun applyDisplayRotation(
        rotation: Int,
        service: Any? = null,
        type: Class<*>? = null
    ) {
        if (targetDisplayId < 0) return
        val windowService = service ?: systemService("window", "android.view.IWindowManager")
        val windowType = type ?: Class.forName("android.view.IWindowManager")
        runCatching {
            val method = windowType.methods.first {
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
            method.invoke(windowService, *args)
        }.onSuccess {
            OperationLog.i(
                this,
                "Orientation",
                "display rotation lock applied display=$targetDisplayId rotation=$rotation"
            )
        }.onFailure {
            OperationLog.e(this, "Orientation", "display rotation lock failed display=$targetDisplayId", it)
            Log.e(logTag, "display rotation lock failed", it)
        }
    }

    private fun launchHome(): Boolean = runCatching {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(targetDisplayId)
        startActivity(intent, options.toBundle())
        Log.i(logTag, "home launched display=$targetDisplayId")
    }.onFailure {
        OperationLog.e(
            this,
            "DesktopHome",
            "HOME launch failed display=$targetDisplayId decorations=$showSystemDecorations",
            it
        )
        Log.e(logTag, "home launch failed", it)
    }.isSuccess

    /**
     * Samsung records whether a firmware needed system decorations for HOME
     * launches. The record is keyed by the full build fingerprint: after an
     * OTA we optimistically try the original configuration again, and only
     * persist the workaround again if that firmware still rejects HOME.
     */
    private fun firmwareIdentity(): String = Build.FINGERPRINT.ifBlank {
        listOf(Build.DISPLAY, Build.VERSION.INCREMENTAL, Build.VERSION.SECURITY_PATCH)
            .joinToString("/")
    }

    private fun shouldUsePersistedSystemDecorations(): Boolean {
        if (!desktopEnvironment.platformManaged) return false
        val preferences = getSharedPreferences("dextop_home_launch_recovery", MODE_PRIVATE)
        val stored = preferences.getString("firmware_fingerprint", null) ?: return false
        if (stored == firmwareIdentity()) return true
        // Firmware changed. Remove the old workaround so this build gets a
        // clean first attempt; a failed HOME launch will store the new one.
        preferences.edit().remove("firmware_fingerprint").apply()
        OperationLog.i(
            this,
            "DesktopHome",
            "firmware changed; retrying HOME without persisted system decorations"
        )
        return false
    }

    private fun rememberSystemDecorationsForFirmware() {
        if (!desktopEnvironment.platformManaged) return
        getSharedPreferences("dextop_home_launch_recovery", MODE_PRIVATE)
            .edit()
            .putString("firmware_fingerprint", firmwareIdentity())
            .apply()
    }

    /**
     * Recreates the overlay with system decorations and lets the normal
     * attach path retry HOME. The existing surface/window stays alive, so the
     * caller's session is not dropped while OverlayDisplayAdapter replaces
     * the display instance.
     */
    private fun retryHomeLaunchWithSystemDecorations(hostWidth: Int, hostHeight: Int): Boolean {
        if (showSystemDecorations || !desktopEnvironment.platformManaged || homeDecorationRetryUsed) {
            return false
        }
        homeDecorationRetryUsed = true
        rememberSystemDecorationsForFirmware()
        val previousDisplayId = targetDisplayId
        val existingDisplays = displayBackend.currentDisplayIds()
        val staleOverlays = displayBackend.overlayDisplayIds()
        OperationLog.w(
            this,
            "DesktopHome",
            "HOME launch denied on display=$previousDisplayId; retrying with system decorations"
        )
        displayBackend.releaseLayer()
        mirrorDisplayId = -1
        targetDisplayId = -1
        showSystemDecorations = true
        sessionJournal.preparing(targetWidth, targetHeight, density, decorations = true)
        runCatching { clearOverlayDisplayRequestTwice("before_home_decoration_retry") }
            .onFailure {
                displayCreationInProgress = false
                OperationLog.e(this, "DesktopHome", "unable to clear display before HOME retry", it)
                completeStart(Result.failure(it))
                stop()
                return true
            }
        postMainDelayed(HOME_DECORATION_RETRY_DELAY_MS) {
            if (active && !stopping) {
                waitForOverlayRequestCleared(
                    existingDisplays,
                    staleOverlays,
                    hostWidth,
                    hostHeight,
                    0,
                    decorations = true
                )
            }
        }
        return true
    }

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
        runCatching { autoOwnedDisplay?.attachment?.release() }
        autoOwnedDisplay = null
        autoDestinationSurface = null
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

    private fun configuredMirrorStrategyOverride(): String? {
        // A persisted legacy Pixel profile must be allowed to walk its older
        // backend order even when the UI still contains the default
        // "virtual_display" selection from a previous build.
        if (useLegacyPixelMirrorProfile) return null
        val configuredBackend = getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
            .getString("flutter.mirror_backend", "virtual_display") ?: "virtual_display"
        return configuredBackend.takeUnless { it == "auto" }
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
        // A recording VirtualDisplay can follow content changes without being
        // recreated, but its destination Surface still has to be rebound when
        // the host window changes geometry. Foldables commonly create the
        // service window in portrait and rotate it to landscape a moment
        // later; skipping that update leaves the virtual display attached to
        // the old portrait buffer and produces a black/offset desktop.
        val hostGeometryChanged = width != null && height != null &&
                (width != mirrorHostWidth || height != mirrorHostHeight)
        if (displayBackend.activeStrategy == "virtual_display" &&
            !forceVirtualDisplay && !hostGeometryChanged
        ) {
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
            val strategyOverride = configuredMirrorStrategyOverride()
            runCatching {
                attachMirror(nextWidth, nextHeight, strategyOverride)
                mirrorDisplayId = targetDisplayId
            }.onSuccess {
                OperationLog.i(
                    this,
                    "DisplayBackend",
                    "mirror refreshed reason=$reason host=${nextWidth}x$nextHeight " +
                            "content=${targetWidth}x$targetHeight/$density " +
                            displayGeometrySnapshot("mirror_refresh_completed", nextWidth, nextHeight)
                )
            }.onFailure { error ->
                mirrorDisplayId = -1
                restoreSoftwareCursorAfterMirrorFailure(reason)
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

    /**
     * The foldable upper pane is often landscape even when the device is held
     * vertically. Use the Dextop orientation selection for auto detection,
     * rather than the measured pane width, so posture changes do not cause a
     * laptop deck flicker. Landscape remains available through the overlay's
     * explicit manual action.
     */
    private fun isLaptopAutoOrientationEligible(): Boolean {
        if (!isFoldableDevice()) return false
        return when (laptopFoldProfile()) {
            LaptopFoldProfile.FOLD8 -> {
                // Fold8 laptop posture is the top/bottom (horizontal hinge)
                // layout. Keep its existing gate: an explicit landscape
                // orientation must remain manual-only.
                foldingApiHorizontalHinge?.let { it && requestedPortrait }
                    ?: requestedPortrait
            }

            LaptopFoldProfile.STANDARD_FOLDABLE -> {
                // On the normal-size Fold family the usable top/bottom
                // laptop layout is reached after rotating the phone 90° from
                // its natural portrait orientation. In that posture the
                // FoldingFeature hinge runs horizontally, so automatic mode
                // is intentionally landscape-only. Manual overlay activation
                // still works in portrait for testing and recovery.
                !requestedPortrait && foldingApiLaptopPosture != false
            }
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
                !shouldFollowHostDisplay()
            ) return@postDelayed
            val bounds = windowManager?.currentWindowMetrics?.bounds
            // During laptop mode the window metrics still describe the whole
            // unfolded panel, while the mirror Surface is split to the upper
            // pane. A default-display callback must not feed that full-panel
            // size back into the logical display or it immediately undoes the
            // pane resize and makes the keyboard appear to disappear. The
            // measured Surface is the source of truth while the deck is live.
            val host = surfaceView
            val measuredWidth = if (laptopModeActive && (host?.width ?: 0) > 0) {
                host!!.width
            } else {
                width?.takeIf { it > 0 } ?: bounds?.width() ?: return@postDelayed
            }
            val measuredHeight = if (laptopModeActive && (host?.height ?: 0) > 0) {
                host!!.height
            } else {
                height?.takeIf { it > 0 } ?: bounds?.height() ?: return@postDelayed
            }
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
        val sizeChanged = next.width != targetWidth || next.height != targetHeight
        val densityChanged = next.density != density
        if (!sizeChanged && !densityChanged) return
        OperationLog.i(
            this,
            "DisplayBackend",
            "live metric change reason=$reason sizeChanged=$sizeChanged densityChanged=$densityChanged " +
                    "from=${targetWidth}x$targetHeight/$density to=${next.width}x${next.height}/${next.density}"
        )
        val service = systemService("window", "android.view.IWindowManager")
        val type = Class.forName("android.view.IWindowManager")
        try {
            val userId = android.os.UserHandle::class.java
                .getMethod("myUserId").invoke(null) as Int
            // A DPI-only edit must not look like a full display replacement to
            // Samsung DeX.  Re-sending the same forced size followed by a
            // rotation/windowing reconfiguration makes One UI's taskbar tear
            // down its desktop window.  Update only the metric that changed.
            if (sizeChanged) {
                type.getMethod(
                    "setForcedDisplaySize",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                ).invoke(service, targetDisplayId, next.width, next.height)
            }
            if (densityChanged) {
                type.getMethod(
                    "setForcedDisplayDensityForUser",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                ).invoke(service, targetDisplayId, next.density, userId)
            }
        } catch (error: Throwable) {
            OperationLog.e(this, "DisplayBackend", "live resize failed reason=$reason", error)
            Log.e(logTag, "live display resize failed reason=$reason", error)
            throw error
        }
        targetWidth = next.width
        targetHeight = next.height
        density = next.density
        if (sizeChanged) {
            // A live size change can cross the natural-orientation boundary.
            // WMS also drops projected-display freeform policy on some Pixel
            // builds, so synchronize rotation and desktop policy after a size
            // change.  DPI-only edits deliberately skip this path so Samsung
            // DeX's taskbar is not forced through an unnecessary rebuild.
            applyHostDisplayOrientation(requestedPortrait)
            forcePhoneRotation(requestedPortrait)
            configureDisplay()
        }
        cursorX = (normalizedCursorX * targetWidth).coerceIn(0f, targetWidth - 1f)
        cursorY = (normalizedCursorY * targetHeight).coerceIn(0f, targetHeight - 1f)
        updateCursorPosition()
        surfaceView?.let { surface ->
            if (surface.width > 0 && surface.height > 0) {
                // Rotation-driven Surface replacement reattaches the mirror in
                // surfaceCreated(). Do not create extra recording displays here.
                // A logical size change can leave the host Surface geometry
                // unchanged (the old letterboxed profile and the new full
                // profile use the same panel). Force the VirtualDisplay
                // attachment update in that case so its source crop/scale is
                // rebuilt instead of retaining the old black bars and offset.
                scheduleMirrorRefresh(
                    "$reason; live logical resize",
                    surface.width,
                    surface.height,
                    forceVirtualDisplay = sizeChanged
                )
            }
        }
        scheduleTopologyReapplyAfterReconnect()
        OperationLog.i(
            this,
            "DisplayBackend",
            "live resized display=$targetDisplayId to ${next.width}x${next.height}/${next.density}; " +
                    "tasks retained " + displayGeometrySnapshot("live_resize_applied")
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
        // In laptop mode the virtual display must fill the measured upper
        // pane. That pane can be landscape while the phone remains portrait;
        // requestedPortrait is applied separately to the physical device.
        val portrait = if (laptopModeActive) hostHeight > hostWidth else base.height > base.width
        val hostLong = maxOf(hostWidth, hostHeight)
        val hostShort = minOf(hostWidth, hostHeight)
        return base.copy(
            // The panel size is dynamic; the selected device orientation is
            // preserved separately by requestedPortrait.
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

    /**
     * Pointer profiles use EventHub as their primary physical input stream.
     * Direct touch deliberately remains on Android MotionEvent so framework
     * transforms, accessibility semantics, and application touch behavior are
     * unchanged. The raw reader is bound from the MotionEvent's exact
     * EventHub capabilities; no MotionEvent device identity, model, or vendor
     * device name is assumed.
     */
    private fun rawTouchscreenBridgeEligible(): Boolean {
        val profile = activeVirtualPointerProfile()
        return active && (laptopModeActive || !directTouch) &&
                (profile == "touchpad" || profile == "mouse") &&
                virtualPointerRegisteredProfile == profile &&
                virtualMouseReady && virtualMouseProcessAlive()
    }

    private fun rawTouchscreenBridgeConsumesTouchSurface(sourceView: View? = null): Boolean {
        if (!touchscreenReaderRunning || !touchscreenReaderReady ||
            touchscreenReaderCandidateCount <= 0 ||
            !rawTouchscreenBridgeEligible()
        ) return false
        return when {
            sourceView == null -> true
            sourceView === laptopTrackpadView -> laptopModeActive
            sourceView === surfaceView -> !directTouch
            else -> false
        }
    }

    private fun scheduleRawTouchscreenTopologyRefresh(reason: String) {
        val refreshGeneration = ++rawTouchscreenTopologyRefreshGeneration
        root?.postDelayed({
            if (refreshGeneration != rawTouchscreenTopologyRefreshGeneration || !active) {
                return@postDelayed
            }
            if (rawTouchscreenBridgeEligible()) {
                refreshPrivilegedInputConfig("topology_refresh:$reason")
                startRawTouchscreenReaderIfEligible()
            }
            val message = "native input topology refresh reason=$reason " +
                    "running=$touchscreenReaderRunning eligible=${rawTouchscreenBridgeEligible()}"
            OperationLog.i(this, "InputRouting", message)
            Log.i(logTag, message)
        }, 220L)
    }

    private fun startRawTouchscreenReaderIfEligible() {
        if (!rawTouchscreenBridgeEligible()) return
        val profile = virtualPointerRegisteredProfile
        touchscreenReaderGeneration += 1
        touchscreenReaderRunning = true
        privilegedInputClient.updateConfig(buildPrivilegedInputConfig(profile))
        val message = "native EventHub reader requested generation=$touchscreenReaderGeneration " +
                "profile=$profile rotation=${rawTouchscreenDisplayRotation()} " +
                "fullscreen=${rawTouchscreenViewBounds(surfaceView)} " +
                "trackpad=${rawTouchscreenViewBounds(laptopTrackpadView)}"
        OperationLog.i(this, "InputRouting", message)
        Log.i(logTag, message)
    }

    private fun rawTouchscreenViewBounds(view: View?): Rect? {
        val target = view ?: return null
        if (!target.isAttachedToWindow || target.width <= 0 || target.height <= 0) return null
        val location = IntArray(2)
        target.getLocationOnScreen(location)
        return Rect(location[0], location[1], location[0] + target.width, location[1] + target.height)
    }

    private fun rawTouchscreenDisplayRotation(): Int =
        root?.display?.rotation ?: surfaceView?.display?.rotation ?: Surface.ROTATION_0

    private fun stopRawTouchscreenReader(reason: String) {
        val wasRunning = touchscreenReaderRunning || touchscreenReaderReady
        touchscreenReaderGeneration += 1
        touchscreenReaderRunning = false
        touchscreenReaderReady = false
        touchscreenReaderCandidateCount = 0
        touchscreenReaderDevice = ""
        if (wasRunning) {
            val message = "native EventHub routing state cleared reason=$reason"
            OperationLog.i(this, "InputRouting", message)
            Log.i(logTag, message)
        }
    }

    private fun fallbackFromRawTouchscreen(reason: String) {
        val profile = activeVirtualPointerProfile()
        if (!active || (!laptopModeActive && directTouch) ||
            (profile != "touchpad" && profile != "mouse")
        ) return
        val message = "native EventHub input unavailable reason=$reason profile=$profile"
        OperationLog.w(this, "InputRouting", message)
        Log.w(logTag, message)
        stopRawTouchscreenReader("fallback:$reason")
        updateVirtualCursorVisibility()
    }

    private fun startRawMouseReader() {
        Log.i(logTag, "physical mouse stays under Android InputReader routing")
    }

    private fun stopRawMouseReader() {
        // Physical mice remain owned by Android's normal InputReader path.
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
                OperationLog.i(
                    this,
                    "NotificationRouting",
                    "moved package=$packageName task=$taskId display=$targetDisplayId"
                )
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
        listOf(120L, 450L, 1_200L, 2_400L, 4_000L).forEach { delay ->
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
                restoreSamsungBottomGestureState(this)
            }
            OperationLog.i(
                this,
                "PhoneNavigation",
                "disabled=$disabled method=${method.name}/${method.parameterTypes.size}"
            )
            Log.i(logTag, "phone navigation disabled=$disabled")
        }.onFailure { error ->
            OperationLog.e(this, "PhoneNavigation", "state update failed disabled=$disabled", error)
            Log.e(logTag, "phone navigation state failed disabled=$disabled", error)
        }
    }

    private fun forcePhoneRotation(portrait: Boolean, force: Boolean = false) {
        val halfTurn = getSharedPreferences(PREFS, MODE_PRIVATE)
            .getBoolean(rotate180PreferenceKey(portrait), false)
        if (!force && lastForcedPhonePortrait == portrait && lastForcedPhoneHalfTurn == halfTurn) return
        lastForcedPhonePortrait = portrait
        lastForcedPhoneHalfTurn = halfTurn
        runCatching {
            phoneRotationController.force(portrait, halfTurn)
        }.onSuccess {
            OperationLog.i(
                this,
                "Orientation",
                "phone rotation applied portrait=$portrait " + displayGeometrySnapshot("phone_rotation_applied")
            )
        }.onFailure {
            OperationLog.e(this, "Orientation", "phone rotation failed portrait=$portrait", it)
            Log.e(logTag, "phone rotation lock failed", it)
        }
    }

    private fun releasePhoneRotation(clearSnapshot: Boolean = false) {
        lastForcedPhonePortrait = null
        lastForcedPhoneHalfTurn = null
        runCatching {
            phoneRotationController.restore(clearSnapshot)
        }.onFailure { Log.e(logTag, "phone rotation unlock failed", it) }
    }

    private fun stop() {
        if (stopping) return
        stopping = true
        val cleanupGeneration = ++stopCleanupGeneration
        val wasActive = active
        val displayBeingRemoved = targetDisplayId
        if (wasActive) {
            OperationLog.i(this, "DisplayGeometry", displayGeometrySnapshot("session_stopping"))
        }
        endCastSession("dextop_stopped")
        stopHostDisplayMonitor()
        suspendedForLockScreen = false
        suspendedConfig = null
        screenLifecycleGeneration += 1
        unlockResumeScheduled = false
        unlockCandidateSince = 0L
        if (dragHeld) toggleDrag()
        runCatching { physicalInputRouter.restore() }
            .onFailure { Log.e(logTag, "physical input restoration failed", it) }

        // Tear down the host first.  In particular, remove the SurfaceView's
        // callback before releasing the mirror layer; clearing the overlay
        // setting first makes One UI deliver surface/display callbacks while
        // the accessibility host is still registered, which can leave Back,
        // Circle to Search, or the desktop task in a broken state.
        removeWindow()
        targetDisplayId = -1
        active = false
        laptopModeActive = false
        laptopBaseConfig = null
        laptopManualOverride = false
        laptopAutoSuppressedByUser = false
        laptopAutoActivated = false
        pendingLaptopMode = null
        pendingLaptopModeSince = 0L
        laptopModeEvaluationGeneration += 1
        laptopPostureReevaluationGeneration += 1
        laptopHostMismatchSince = 0L

        // Settings.Global is only a request to OverlayDisplayAdapter.  Do not
        // restore the phone/DeX environment or mark the session finished until
        // the display manager has observed the requested display disappear.
        clearOverlayDisplayRequestTwice("session_stop display=$displayBeingRemoved")
        awaitStoppedDisplay(
            displayBeingRemoved,
            wasActive,
            cleanupGeneration,
            attempt = 0
        )
    }

    /**
     * Wait for OverlayDisplayAdapter/VirtualDisplayAdapter to finish tearing
     * down the target display.  The wait is bounded so a vendor display
     * service that does not emit a removal callback cannot keep the service
     * alive forever; in that case the journal remains the fallback recovery
     * path and the warning is included in the session report.
     */
    private fun awaitStoppedDisplay(
        displayId: Int,
        wasActive: Boolean,
        generation: Long,
        attempt: Int
    ) {
        if (generation != stopCleanupGeneration) return
        val stillPresent = displayId >= 0 &&
                getSystemService(DisplayManager::class.java).getDisplay(displayId) != null
        if (stillPresent && attempt < 40) {
            android.os.Handler(mainLooper).postDelayed({
                awaitStoppedDisplay(displayId, wasActive, generation, attempt + 1)
            }, 50L)
            return
        }
        if (stillPresent) {
            OperationLog.w(
                this,
                "DisplayBackend",
                "display removal timed out display=$displayId; continuing system restore"
            )
        } else if (displayId >= 0) {
            OperationLog.i(this, "DisplayBackend", "display removed display=$displayId")
        }
        finishStop(wasActive, generation)
    }

    private fun finishStop(wasActive: Boolean, generation: Long) {
        if (generation != stopCleanupGeneration) return
        // Keep navigation and rotation locked until the host/display teardown
        // is complete.  Restoring them earlier is the race observed on One UI.
        if (!autoOnlySession) {
            setPhoneNavigationDisabled(false)
            releasePhoneRotation(clearSnapshot = true)
        }
        val autoSessionActive = AndroidAutoMirrorActivity.isAutoSessionActive()
        runCatching {
            if (autoSessionActive) {
                DisplayEnvironmentSettings(this).activateTopologyForOverlays(
                    AndroidAutoMirrorActivity.autoOverlayDisplayIds()
                )
            } else {
                DisplayEnvironmentSettings(this).restoreTopology()
            }
        }.onFailure { Log.e(logTag, "topology restoration failed", it) }
        if (!autoOnlySession) MainActivity.restoreOrientation()
        val keepInternal120Hz = internalRefreshRateController.isEnabledAndSupported() &&
                !externalDisplayDetector.snapshot().connected
        if (keepInternal120Hz) {
            runCatching { internalRefreshRateController.keepCurrentValue() }
                .onFailure { Log.e(logTag, "unable to preserve 120 Hz after disconnect", it) }
        }
        val restored = if (autoSessionActive) {
            // The Auto overlay owns the shared desktop transaction until it
            // stops. Restoring here would remove the still-running Auto
            // display and would make the phone Stop button terminate both
            // sessions.
            true
        } else {
            runCatching {
                desktopModeConfigurator.restore()
                sessionJournal.restoreSystemSettings()
            }.onFailure { Log.e(logTag, "settings restoration failed", it) }.isSuccess
        }
        if (restored && !autoSessionActive) sessionJournal.clear()
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
            // Delay both operations until the navigation restore retries have
            // completed; disabling the service immediately can terminate the
            // process before SystemUI accepts the final zero-disable request.
            val disableAfterCleanup = object : Runnable {
                override fun run() {
                    // A new session may have started during the grace period.
                    // In that case the old delayed detach must not tear down
                    // the newly active service.
                    if (generation != stopCleanupGeneration || active || stopping || pending != null) return
                    disableDextopAccessibilityService()
                    disableSelf()
                }
            }
            android.os.Handler(mainLooper).postDelayed(disableAfterCleanup, 4_500L)
        }
        // All display and system restoration is complete at this point. Clear
        // the latch before returning so a new start can reuse this service
        // instance; the delayed detach above is guarded by active/stopping.
        stopping = false
        autoOnlySession = false
        completeStart(Result.failure(IllegalStateException("Dextop was stopped before startup completed")))
        Log.i(logTag, "stopped; cleanup ready for a new session")
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
            check(
                Settings.Secure.putString(
                    contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    remaining.joinToString(":")
                )
            ) { "Unable to detach the Dextop accessibility service" }
            if (remaining.isEmpty()) {
                Settings.Secure.putInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
            }
            Log.i(logTag, "Dextop accessibility service detached; remaining=${remaining.size}")
        }.onFailure { Log.e(logTag, "accessibility service detachment failed", it) }
    }

    private fun removeWindow() {
        // detachHostWindow removes the SurfaceHolder callback before the
        // mirror layer is released.  This ordering is important on Samsung:
        // releasing the layer first can make One UI unregister listeners from
        // a display whose host window is still present.
        detachHostWindow()
        releaseMirror()
    }

    private fun detachHostWindow() {
        stopCastRouteDiscovery()
        finishAllLaptopKeyPresses()
        stopLaptopHardwareKeyboard()
        stopVirtualMouse()
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
        private var customGlyph: Drawable? = null
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

        /**
         * Renders a key icon without relying on a private-use font glyph.
         * This is used for the Meta/Android key so OEM font fallback cannot
         * turn the icon into an unrelated character.
         */
        fun setCustomGlyph(drawable: Drawable?) {
            customGlyph = drawable
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            customGlyph?.let { drawable ->
                val size = minOf(width, height) * .46f
                val left = ((width - size) / 2f).toInt()
                val top = ((height - size) / 2f).toInt()
                drawable.setBounds(left, top, (left + size).toInt(), (top + size).toInt())
                drawable.draw(canvas)
            }
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
                APP_GRID -> {
                    val tile = 4f
                    val gap = 2f
                    val startX = (24f - tile * 3f - gap * 2f) / 2f
                    val startY = (20f - tile * 3f - gap * 2f) / 2f
                    for (row in 0 until 3) {
                        for (column in 0 until 3) {
                            val left = startX + column * (tile + gap)
                            val top = startY + row * (tile + gap)
                            canvas.drawRect(
                                left,
                                top,
                                left + tile,
                                top + tile,
                                paint
                            )
                        }
                    }
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

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
        }

        override fun setColorFilter(filter: android.graphics.ColorFilter?) {
            paint.colorFilter = filter
        }

        @Suppress("DEPRECATION")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

        companion object {
            const val APP_GRID = 1
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
