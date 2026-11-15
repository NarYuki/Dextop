package moe.n4tsu.dextop

import android.accessibilityservice.AccessibilityService
import android.app.ActivityOptions
import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.content.Intent
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Point
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.BitmapFactory
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ColorDrawable
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
import android.provider.Settings
import android.text.TextUtils
import android.transition.ChangeBounds
import android.transition.TransitionManager
import android.util.Log
import android.util.Base64
import android.view.Gravity
import android.view.DragEvent
import android.view.Display
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.WindowInsets
import android.view.ViewGroup
import android.view.animation.PathInterpolator
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
import android.widget.Toast
import android.animation.ValueAnimator
import android.animation.LayoutTransition
import android.widget.GridLayout
import androidx.window.layout.FoldingFeature
import java.util.concurrent.Executors
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import moe.n4tsu.dextop.input.PrivilegedInputClient
import moe.n4tsu.dextop.input.PrivilegedInputProtocol
import moe.n4tsu.dextop.input.SwipeObservingKeyboardLayout
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import org.json.JSONObject
import org.lsposed.hiddenapibypass.HiddenApiBypass

class MirrorService : AccessibilityService(), SurfaceHolder.Callback {
    private val session = object : DesktopSession {
        override val accessibilityService: AccessibilityService get() = this@MirrorService
        override val logTag: String get() = this@MirrorService.logTag
        override val targetDisplayId: Int get() = this@MirrorService.targetDisplayId
        override val targetWidth: Int get() = this@MirrorService.targetWidth
        override val targetHeight: Int get() = this@MirrorService.targetHeight
        override val density: Int get() = this@MirrorService.density
        override val demoMode: Boolean get() = this@MirrorService.demoMode
        override val autoOnlySession: Boolean get() = this@MirrorService.autoOnlySession
        override val laptopModeActive: Boolean get() = this@MirrorService.laptopModeActive
        override val privilegedAccess: PrivilegedAccess get() = this@MirrorService.privilegedAccess
        override val plasmaShell: moe.n4tsu.dextop.plasma.DesktopShell? get() = this@MirrorService.plasmaShell
        override fun dp(value: Int): Int = this@MirrorService.dp(value)
    }

    private val overlayMenu = object : OverlayMenu {
        override val menu: LinearLayout? get() = this@MirrorService.menu
        override val menuPrimary: LinearLayout? get() = this@MirrorService.menuPrimary
        override fun actionButton(icon: Int, label: String, destructive: Boolean, action: () -> Unit): TextView = this@MirrorService.menuWidgets.actionButton(icon, label, destructive, action)
        override fun menuTitle(title: String, action: String?, actionTag: String?, onAction: (() -> Unit)?): View = this@MirrorService.menuWidgets.menuTitle(title, action, actionTag, onAction)
        override fun menuHint(textValue: String): View = this@MirrorService.menuWidgets.menuHint(textValue)
        override fun sectionLabel(label: String): TextView = this@MirrorService.menuWidgets.sectionLabel(label)
        override fun choiceButton(label: String, selected: Boolean, action: () -> Unit): TextView = this@MirrorService.menuWidgets.choiceButton(label, selected, action)
        override fun animateMenuResize(panel: LinearLayout) = this@MirrorService.menuWidgets.animateMenuResize(panel)
        override fun scheduleMenuHeightUpdate() = this@MirrorService.scheduleMenuHeightUpdate()
        override fun showMainMenu(panel: LinearLayout) = this@MirrorService.showMainMenu(panel)
        override fun toggleMenu() = this@MirrorService.toggleMenu()
        override fun demoExplanation(message: String) = this@MirrorService.demoExplanation(message)
    }

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
        val autoOnly: Boolean = false,
        /** Force freeform on an app-owned Auto display, including Samsung DeX. */
        val forceFreeform: Boolean = false
    )

    /**
     * Samsung's special-size Fold8 exposes the laptop hinge and orientation
     * differently from the normal-size Fold family. Fold8 Ultra and Fold7
     * therefore use the normal-size posture gate, while the special Fold8
     * keeps its dedicated handling.
     */
    private val posture: LaptopPosture = LaptopPosture(this, object : LaptopPostureHost, DesktopSession by session, OverlayMenu by overlayMenu {
        override val externalInputDeck: Boolean get() = this@MirrorService.externalInputDeck
        override var hingeAngle: Float?
            get() = this@MirrorService.hingeAngle
            set(value) { this@MirrorService.hingeAngle = value }
        override fun isFoldableMainDisplay(): Boolean = this@MirrorService.isFoldableMainDisplay()
        override fun isLaptopAutoDetectionEnabled(): Boolean = this@MirrorService.isLaptopAutoDetectionEnabled()
        override fun isLaptopAutoOrientationEligible(): Boolean = this@MirrorService.isLaptopAutoOrientationEligible()
        override var laptopAutoActivated: Boolean
            get() = this@MirrorService.laptopAutoActivated
            set(value) { this@MirrorService.laptopAutoActivated = value }
        override var laptopAutoSuppressedByUser: Boolean
            get() = this@MirrorService.laptopAutoSuppressedByUser
            set(value) { this@MirrorService.laptopAutoSuppressedByUser = value }
        override fun laptopHostIsFullHeight(): Boolean = this@MirrorService.laptopHostIsFullHeight()
        override val laptopManualOverride: Boolean get() = this@MirrorService.laptopManualOverride
        override val laptopModeDebounceMs: Long get() = this@MirrorService.laptopModeDebounceMs
        override var laptopModeEvaluationGeneration: Long
            get() = this@MirrorService.laptopModeEvaluationGeneration
            set(value) { this@MirrorService.laptopModeEvaluationGeneration = value }
        override var pendingLaptopMode: Boolean?
            get() = this@MirrorService.pendingLaptopMode
            set(value) { this@MirrorService.pendingLaptopMode = value }
        override var pendingLaptopModeSince: Long
            get() = this@MirrorService.pendingLaptopModeSince
            set(value) { this@MirrorService.pendingLaptopModeSince = value }
        override val root: TouchRoutingFrame? get() = this@MirrorService.root
        override fun setLaptopMode(enabled: Boolean, requestedStyle: KeyboardDeckStyle) = this@MirrorService.setLaptopMode(enabled, requestedStyle)
        override val suspendedForLockScreen: Boolean get() = this@MirrorService.suspendedForLockScreen
    })


    companion object {
        internal const val PREFS = "freedextop_input"
        private const val KEY_DIRECT_TOUCH = "direct_touch"
        private const val KEY_ROUTE_MOUSE = "route_physical_mouse"
        private const val KEY_ROUTE_KEYBOARD = "route_physical_keyboard"

        /** Explicit compatibility switch for the previous software cursor. */
        internal const val KEY_SOFTWARE_CURSOR_FALLBACK = "software_cursor_fallback"

        /** Persisted three-way pointer profile; old installs use the fallback key. */
        internal const val KEY_VIRTUAL_POINTER_PROFILE = "virtual_pointer_profile"
        private const val KEY_ROTATE_180_LANDSCAPE = "rotate_180_landscape"
        private const val KEY_ROTATE_180_PORTRAIT = "rotate_180_portrait"
        private const val KEY_BLACKBERRY_HEIGHT_MANUAL = "blackberry_height_manual"
        private const val KEY_BLACKBERRY_HEIGHT_PERCENT = "blackberry_height_percent"
        internal const val VIRTUAL_MOUSE_NAME = "Dextop Virtual Mouse"
        internal const val VIRTUAL_TOUCHPAD_NAME = "Dextop Virtual Touchpad"
        private const val LAPTOP_KEYBOARD_NAME = "Dextop Laptop Keyboard"
        private const val LAPTOP_KEYBOARD_VENDOR_ID = 6353
        private const val LAPTOP_KEYBOARD_PRODUCT_ID = 5417
        internal const val VIRTUAL_TOUCHPAD_MAX_SLOTS = 5
        private const val VIRTUAL_TOUCHPAD_MAX_X = 1839
        private const val VIRTUAL_TOUCHPAD_MAX_Y = 1199

        /** Lower resolution makes Android's touchpad acceleration cover more distance. */
        private const val VIRTUAL_TOUCHPAD_RESOLUTION = 12
        internal const val VIRTUAL_TOUCHPAD_TOUCH_MAJOR = 20
        internal const val VIRTUAL_TOUCHPAD_PRESSURE = 40
        internal const val VIRTUAL_TOUCHPAD_MOVE_LOG_INTERVAL_MS = 250L
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        private const val NOTIFICATION_LAUNCH_WINDOW_MS = 3_000L
        /** How soon after a car tap a swipe still starts at the tapped point. */
        internal const val CAR_TAP_DRAG_MS = 1_500L
        /** HyperOS / MIUI device profile, see DeviceProfiles. */
        internal const val XIAOMI_PROFILE = "xiaomi_hyperos"
        /** Scales a screen of the extended desktop can take (percent; 100 is the recommended size). */
        private val EXTENDED_SCALES = listOf(75, 100, 125, 150, 175, 200)
        /** Without new scroll deltas for this long, a car swipe is treated as lifted. */
        internal const val CAR_SWIPE_IDLE_MS = 700L
        private const val NOTIFICATION_ROUTE_RETRY_DELAY_MS = 140L
        private const val NOTIFICATION_ROUTE_RETRIES = 5
        private const val HOST_DISPLAY_MONITOR_INTERVAL_MS = 1_000L
        private const val HOME_DECORATION_RETRY_DELAY_MS = 180L
        internal const val IME_REGION_PROBE_MIN_INTERVAL_MS = 250L
        private const val DEBUG_FORCE_LAPTOP_MODE = false
        internal val FOLD8_SPECIAL_MODEL_IDS = setOf(
            "SMF971", "SMF971B", "SMF971U", "SMF971U1", "SMF971W", "SMF9710",
            "SMF971N", "SMF971Q", "SMF971Z", "SMF971C", "SCG41", "SC57G"
        )
        internal val FOLD8_ULTRA_MODEL_IDS = setOf(
            "SMF976", "SMF976B", "SMF976U", "SMF976U1", "SMF976W", "SMF9760",
            "SMF976N", "SMF976Q", "SMF976Z", "SMF976C", "SCG39", "SC56G"
        )
        internal const val FOLD8_SPECIAL_DEVICE_PREFIX = "H8Q"
        internal const val FOLD8_ULTRA_DEVICE_PREFIX = "Q8Q"
        internal const val STATUS_BAR_INTERFACE = "com.android.internal.statusbar.IStatusBarService"
        internal const val PHONE_NAVIGATION_DISABLE_FLAGS =
            0x00200000 or 0x00400000 or 0x01000000
        private var instance: MirrorService? = null
        @JvmField internal var carCompanionInputActive = false

        /** Samsung's accessibility input filter cancels alternating phone/virtual touch streams. */
        internal fun setCarCompanionInputActive(value: Boolean) {
            carCompanionInputActive = value
            Handler(Looper.getMainLooper()).post { instance?.carCompanion?.applyCarCompanionKeyFilter() }
        }
        private var pending: Config? = null
        private var pendingAutoSurface: Surface? = null
        private var pendingStartResult: ((Result<Map<String, Any>>) -> Unit)? = null
        private var pendingDemo = false
        private var pendingLaptopDemo = false
        private var pendingLaptopPreviewThemeId: String? = null
        internal var active = false

        fun launch(
            context: Context,
            width: Int,
            height: Int,
            density: Int,
            secure: Boolean,
            decorations: Boolean,
            autoOnly: Boolean = false,
            autoSurface: Surface? = null,
            forceFreeform: Boolean = false,
            completion: (Result<Map<String, Any>>) -> Unit
        ) {
            val running = instance
            if (running?.stopping == true) {
                completion(Result.failure(IllegalStateException(moe.n4tsu.dextop.localText("uiDextopIsStillFinishingItsPreviousSession"))))
                return
            }
            // Dextop Auto's display never has system decorations (createDirectAutoDisplay), so a
            // request for it compares as undecorated and reuses the running session instead of
            // rebuilding it for every new car Surface.
            val dextopAuto = autoOnly && context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
                .getString("flutter.android_auto_shell", "dextop") == "dextop"
            val effectiveDecorations = if (dextopAuto) false else running?.let {
                decorations || it.shouldUsePersistedSystemDecorations()
            } ?: decorations
            if (active && running != null && running.targetDisplayId >= 0 &&
                secure == running.secureDisplay && effectiveDecorations == running.showSystemDecorations &&
                autoOnly == running.autoOnlySession && forceFreeform == running.forceFreeformSession
            ) {
                val requested = Config(width, height, density, secure, effectiveDecorations, autoOnly, forceFreeform)
                Handler(Looper.getMainLooper()).post {
                    runCatching {
                        check(instance === running && !running.serviceDestroyed) { moe.n4tsu.dextop.localText("uiDextopServiceChangedBeforeTheDisplayCouldBeUpdated") }
                        if (autoOnly) running.replaceAutoDestinationSurface(autoSurface)
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
                }
                return
            }
            val component = ComponentName(context, MirrorService::class.java).flattenToString()
            val current = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty()
            val services = current.split(':').filter { it.isNotBlank() }.toMutableSet()
            val enabled = services.any {
                ComponentName.unflattenFromString(it)?.flattenToString() == component
            }
            if (BuildConfig.DISTRIBUTION_CHANNEL == "github") {
                pendingStartResult?.invoke(Result.failure(IllegalStateException(moe.n4tsu.dextop.localText("uiADextopStartIsAlreadyInProgress"))))
                pendingStartResult = completion
                pending = Config(width, height, density, secure, effectiveDecorations, autoOnly, forceFreeform)
                pendingAutoSurface = autoSurface?.takeIf { autoOnly }
                services.add(component)
                if (instance == null && enabled) {
                    val withoutDextop = services.filterNot { it == component }
                    Settings.Secure.putString(
                        context.contentResolver,
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                        withoutDextop.joinToString(":")
                    )
                    Handler(Looper.getMainLooper()).postDelayed({
                        Settings.Secure.putString(
                            context.contentResolver,
                            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                            services.joinToString(":")
                        )
                        Settings.Secure.putInt(
                            context.contentResolver,
                            Settings.Secure.ACCESSIBILITY_ENABLED,
                            1
                        )
                    }, 180L)
                } else {
                    Settings.Secure.putString(
                        context.contentResolver,
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                        services.joinToString(":")
                    )
                    Settings.Secure.putInt(
                        context.contentResolver,
                        Settings.Secure.ACCESSIBILITY_ENABLED,
                        1
                    )
                    instance?.start(pending!!)
                }
                return
            }
            if (!enabled) {
                completion(Result.failure(IllegalStateException(
                    moe.n4tsu.dextop.localText("uiEnableDextopInAndroidAccessibilitySettingsBeforeStarting")
                )))
                return
            }
            val connected = instance
            if (connected == null) {
                completion(Result.failure(IllegalStateException(
                    moe.n4tsu.dextop.localText("uiDextopAccessibilityIsEnabledButNotConnectedOpenAccessibilitySettingsAndReconnectDextop")
                )))
                return
            }
            pendingStartResult?.invoke(Result.failure(IllegalStateException(moe.n4tsu.dextop.localText("uiADextopStartIsAlreadyInProgress"))))
            pendingStartResult = completion
            pending = Config(width, height, density, secure, effectiveDecorations, autoOnly, forceFreeform)
            pendingAutoSurface = autoSurface?.takeIf { autoOnly }
            connected.start(pending!!)
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
                service.phoneGuard.forcePhoneRotation(portrait)
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
            instance?.phoneGuard?.setPhoneNavigationDisabled(false)
            // Samsung SystemUI keeps a separate recovery marker for the
            // bottom-gesture navigation bar.  Clearing the status-bar disable
            // mask alone leaves this marker at 0 after an interrupted Dextop
            // session, so SystemUI can continue treating the gesture bar as
            // suspended even though the bar is visible again.
            context?.let { restoreSamsungBottomGestureState(it) }
        }

        internal fun restoreSamsungBottomGestureState(context: Context) {
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

        /**
         * A cover session is only exposed on devices for which at least one
         * hardware foldability signal is available.  While the accessibility
         * service is connected the WindowManager folding API is also queried;
         * before that, a hinge sensor or multiple internal panels is enough to
         * identify a foldable without relying on a model-name allowlist.
         */
        fun isFoldableDevice(context: Context? = null): Boolean {
            instance?.let { if (it.isFoldableDevice()) return true }
            val resolved = context ?: return false
            val displays = resolved.getSystemService(DisplayManager::class.java)
            val internalPanels = displays.displays.count { display ->
                runCatching {
                    Display::class.java.getMethod("getType").invoke(display) as Int == 1
                }.getOrDefault(display.displayId == Display.DEFAULT_DISPLAY)
            }
            val hasHinge = resolved.getSystemService(SensorManager::class.java)
                .getDefaultSensor(Sensor.TYPE_HINGE_ANGLE) != null
            return internalPanels >= 2 || hasHinge
        }

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
                if (service.laptopSettingsVisible) service.laptopTheme.showLaptopKeyboardSettings()
                else service.rebuildLaptopDeck()
            }
        }

        /** Apply swipe settings immediately to an already visible keyboard. */
        fun updateLaptopSwipeSettings(enabled: Boolean?) {
            val service = instance ?: return
            service.root?.post {
                val swipeEnabled = enabled ?: service.swipeTyping.isLaptopSwipeEnabled()
                // Do not leave a completed decode, an auto-commit, or a
                // candidate row alive after the user turns swipe input off.
                // Rebuilding also cancels the observer's deferred DOWN stream,
                // so the next ordinary key press starts with a balanced event.
                if (!swipeEnabled) {
                    service.swipeTyping.laptopSwipeGeneration += 1
                    service.swipeTyping.hideLaptopSwipeCandidates()
                    service.laptopKeyboardView?.cancelSwipeRecognition()
                }
                val enabled = service.swipeTyping.laptopSwipeLanguageChoices().map { it.first }.toSet()
                if (service.swipeTyping.laptopSwipeLanguage() !in enabled) {
                    service.getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
                        .edit().putString("flutter.laptop_swipe_language", "en").apply()
                }
                val languagePopupWasVisible = service.laptopLanguagePopup != null
                service.laptopTheme.hideLaptopLanguagePopup()
                // Swipe commits are display-local accessibility edits.  Never
                // replace the user's IME just because the language changed.
                if (service.laptopSettingsVisible) service.laptopTheme.showLaptopKeyboardSettings()
                else if (service.laptopModeActive) service.rebuildLaptopDeck()
                if (languagePopupWasVisible) {
                    service.laptopMenuButton?.post {
                        service.laptopMenuButton?.let(service.laptopTheme::showLaptopLanguagePopup)
                    }
                }
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

        /** Shows the desktop on the external screen as the user chose in the app (see setExternalDisplay). */
        fun setExternalDisplay(mode: String, done: (String) -> Unit) {
            val service = instance ?: return done("no_session")
            Handler(Looper.getMainLooper()).post { done(runCatching { service.setExternalDisplay(mode) }.getOrDefault("failed")) }
        }

        /** Whether an external screen that can take the desktop is connected, and how it is used now. */
        /** A connected screen the desktop can be shown on, found without a running session. */
        fun externalOutputDisplay(context: Context): Display? {
            val manager = context.getSystemService(DisplayManager::class.java)
            return ExternalDisplayDetector(context).snapshot().displayIds.mapNotNull(manager::getDisplay)
                .firstOrNull { it.flags and Display.FLAG_PRESENTATION != 0 }
        }

        /** The extended desktop's screens for the arrangement editor, or null when not extended. */
        fun extendedDisplayArrangement(): Map<String, Any>? = instance?.extendedArrangement()

        /** Applies the arrangement editor's positions to the extended desktop. */
        fun arrangeExtendedDisplays(positions: Map<*, *>, done: (Boolean) -> Unit) {
            val service = instance ?: return done(false)
            Handler(Looper.getMainLooper()).post { done(runCatching { service.arrangeExtendedFromEditor(positions) }.getOrDefault(false)) }
        }

        /** Sets one screen's scale on the extended desktop. */
        fun setExtendedDisplayScale(displayId: Int, percent: Int, done: (Boolean) -> Unit) {
            val service = instance ?: return done(false)
            Handler(Looper.getMainLooper()).post { done(runCatching { service.setExtendedScale(displayId, percent) }.getOrDefault(false)) }
        }

        fun externalDisplayState(context: Context): Map<String, Any?> {
            val service = instance
            val display = externalOutputDisplay(context)
            return mapOf(
                "connected" to (display != null),
                "name" to display?.name,
                "running" to (active && service != null),
                "mode" to (service?.externalOutput?.mode ?:
                    context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
                        .getString("flutter.external_display_mode", "off")),
            )
        }

        /** Applies the "window manager" display setting to a running session. */
        fun updateWindowManager() {
            val service = instance ?: return
            Handler(Looper.getMainLooper()).post { service.applyWindowManagerSelection("settings_changed") }
        }

        /** Re-reads the Dextop Plasma appearance settings written by Flutter. */
        fun reloadPlasmaSettings() {
            val service = instance ?: return
            Handler(Looper.getMainLooper()).post { service.plasmaShell?.reloadSettings() }
        }

        fun activeWindowManager(): String =
            when (instance?.plasmaShell) {
                is moe.n4tsu.dextop.plasma.PlasmaV2Shell -> moe.n4tsu.dextop.plasma.WindowManagerSelection.DEXTOP_PLASMA_V2
                is moe.n4tsu.dextop.plasma.BookShell -> moe.n4tsu.dextop.plasma.WindowManagerSelection.DEXTOP_BOOK
                null -> moe.n4tsu.dextop.plasma.WindowManagerSelection.SYSTEM
                else -> moe.n4tsu.dextop.plasma.WindowManagerSelection.DEXTOP_PLASMA
            }

        fun activeDisplayGeometry(): IntArray? = instance?.let { service ->
            if (!active || service.targetDisplayId < 0 ||
                service.targetWidth <= 0 || service.targetHeight <= 0
            ) {
                null
            } else {
                intArrayOf(
                    service.targetDisplayId,
                    service.targetWidth,
                    service.targetHeight,
                    service.density.coerceAtLeast(1),
                )
            }
        }

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
                        service.workspaces.workspaceLayoutEngine.fit(
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
                    service.diagnostics.scheduleWindowLaunchDiagnostics("after_app_launch")
                }.onFailure {
                    Log.e(service.logTag, "app launch failed", it)
                    OperationLog.e(
                        service,
                        "AppLaunch",
                        "startActivity failed package=$packageName display=${service.targetDisplayId}",
                        it
                    )
                    service.diagnostics.scheduleWindowLaunchDiagnostics("app_launch_failure", delayMs = 0L)
                }.isSuccess
            } ?: false

        fun launchPackageAt(packageName: String, position: String): Boolean = instance?.let { service ->
            val bounds = service.workspaces.workspaceLayoutEngine.position(
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

        /** Opens a Linux container's desktop in the running Plasma v2 shell; false without one. */
        /**
         * Opens a Linux application in the running Plasma v2 desktop; false when no Plasma
         * desktop runs, so the caller shows it as Android windows instead.
         */
        /** The composited desktop (Plasma v2 or Book) Linux applications open in, if one runs. */
        private val compositedDesktop: moe.n4tsu.dextop.plasma.CompositedWindowShell?
            get() = (instance?.plasmaShell as? moe.n4tsu.dextop.plasma.CompositedWindowShell)
                ?.takeIf { it !is moe.n4tsu.dextop.plasma.DextopAutoShell }

        /** Book's display scale changed: the external desktop takes it now. */
        fun displayScaleChanged() {
            instance?.let { service -> Handler(Looper.getMainLooper()).post { service.reapplyExternalScale() } }
        }

        fun launchLinuxAppInPlasma(containerId: String, desktopId: String): Boolean {
            val shell = compositedDesktop ?: return false
            Handler(Looper.getMainLooper()).post {
                shell.launch("${moe.n4tsu.dextop.plasma.LINUX_APP_PREFIX}$containerId:$desktopId")
            }
            return true
        }

        val plasmaDesktopRunning: Boolean
            get() = compositedDesktop != null

        fun openLinuxDesktop(containerId: String, name: String): Boolean {
            val shell = compositedDesktop ?: return false
            shell.openLinuxDesktop(containerId, name)
            return true
        }

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

        /** Called by the physical cover-display button activity. */
        fun coverBackButtonsActivityCreated() {
            val service = instance ?: return
            Handler(Looper.getMainLooper()).post {
                if (instance === service) service.coverButtons.enableCoverBackButtons()
            }
        }

        fun coverBackButtonsActivityDestroyed() {
            val service = instance ?: return
            Handler(Looper.getMainLooper()).post {
                if (instance === service) service.coverButtons.disableCoverBackButtons()
            }
        }

        fun injectCoverBackButton(code: Int, pressed: Boolean) {
            val service = instance ?: return
            Handler(Looper.getMainLooper()).post {
                if (instance === service) service.coverButtons.injectCoverBackButtonInternal(code, pressed)
            }
        }

        fun coverTrackpadActivityCreated(surface: View) {
            val service = instance ?: return
            Handler(Looper.getMainLooper()).post {
                if (instance === service && active) service.coverButtons.attachCoverTrackpad(surface)
            }
        }

        fun coverTrackpadActivityDestroyed(surface: View) {
            val service = instance ?: return
            Handler(Looper.getMainLooper()).post {
                if (instance === service) service.coverButtons.detachCoverTrackpad(surface)
            }
        }

        fun dispatchCoverTrackpadEvent(surface: View, source: MotionEvent) {
            val service = instance ?: return
            val event = MotionEvent.obtain(source)
            Handler(Looper.getMainLooper()).post {
                try {
                    if (instance === service && active) {
                        service.coverButtons.handleCoverTrackpadEvent(surface, event)
                    }
                } finally {
                    event.recycle()
                }
            }
        }

        fun showCarCompanionCursor(displayId: Int) {
            val service = instance ?: return
            Handler(Looper.getMainLooper()).post {
                if (instance === service) service.carCompanion.attachCarCompanionCursor(displayId)
            }
        }

        fun updateCarCompanionCursor(displayId: Int, x: Float, y: Float, pulse: Boolean = false) {
            val service = instance ?: return
            Handler(Looper.getMainLooper()).post {
                if (instance !== service) return@post
                service.carCompanion.attachCarCompanionCursor(displayId)
                service.carCompanion.carCompanionCursorView?.apply {
                    update(x, y)
                    if (pulse) pulse()
                }
            }
        }

        fun hideCarCompanionCursor() {
            val service = instance ?: return
            Handler(Looper.getMainLooper()).post {
                if (instance === service) service.carCompanion.detachCarCompanionCursor()
            }
        }

        fun setCarVisibleArea(left: Float, top: Float, right: Float, bottom: Float) {
            if (listOf(left,top,right,bottom).any { !it.isFinite() } || left<0f || top<0f || right>1f || bottom>1f || right<=left || bottom<=top) return
            val service = instance ?: return
            Handler(Looper.getMainLooper()).post {
                service.carGestures.carVisibleArea = floatArrayOf(left,top,right,bottom)
                service.plasmaShell?.setAutoViewport(left,top,right,bottom)
            }
        }

        fun dispatchCarPan(dx: Float, dy: Float, drag: Boolean, release: Boolean, anchorX: Float, anchorY: Float) {
            val service = instance ?: return
            Handler(Looper.getMainLooper()).post {
                if (instance === service && active && service.autoOnlySession) {
                    if (!service.carGestures.carPanHeld && anchorX.isFinite() && anchorY.isFinite()) {
                        service.cursorX = anchorX.coerceIn(0f, 1f) * (service.targetWidth - 1)
                        service.cursorY = anchorY.coerceIn(0f, 1f) * (service.targetHeight - 1)
                        if (!drag) service.carGestures.rememberCarTapAnchor(anchorX, anchorY)
                    }
                    service.carGestures.carPan(dx * service.targetWidth, dy * service.targetHeight, drag, release)
                }
            }
        }

        /** A tap on the car screen; a swipe that follows soon after starts where it was tapped. */
        fun noteCarTap() {
            val service = instance ?: return
            Handler(Looper.getMainLooper()).post {
                service.carGestures.carTapAt = SystemClock.uptimeMillis()
                service.carGestures.scheduleCarDesktopAnchor()
            }
        }

        fun releaseCarPan() {
            val service = instance ?: return
            Handler(Looper.getMainLooper()).post { service.carGestures.finishCarPan(); service.carGestures.finishCarPinch() }
        }

        /** Android Auto fling velocity, in Surface widths/heights per second. */
        fun dispatchCarFling(vx: Float, vy: Float) {
            if (!vx.isFinite() || !vy.isFinite()) return
            val service = instance ?: return
            Handler(Looper.getMainLooper()).post {
                if (instance === service && active && service.autoOnlySession)
                    service.carGestures.carFling(vx * service.targetWidth, vy * service.targetHeight)
            }
        }

        /**
         * Android Auto pinch or double tap; a negative focus means none was supplied.
         * The anchor is the last tap, used when the pinch follows a tap.
         */
        fun dispatchCarScale(x: Float, y: Float, factor: Float, anchorX: Float = -1f, anchorY: Float = -1f) {
            if (!x.isFinite() || !y.isFinite() || !factor.isFinite() || factor <= 0f) return
            val service = instance ?: return
            Handler(Looper.getMainLooper()).post {
                if (instance === service && active && service.autoOnlySession) service.carGestures.carScale(x, y, factor, anchorX, anchorY)
            }
        }

        fun attachCarCompanionPhoneTrackpad(surface: View, nativeTouchpad: Boolean) {
            val service = instance ?: return
            Handler(Looper.getMainLooper()).post {
                if (instance === service && active) {
                    service.carCompanion.attachCarCompanionPhoneTrackpad(surface, nativeTouchpad)
                }
            }
        }

        fun dispatchCarCompanionPhoneTrackpad(surface: View, source: MotionEvent) {
            val service = instance ?: return
            val event = MotionEvent.obtain(source)
            Handler(Looper.getMainLooper()).post {
                try {
                    if (instance === service && active && service.carCompanion.carCompanionPhoneTrackpadSurface === surface) {
                        service.trackpad(
                            event,
                            surface,
                            forceCursorMode = true,
                            allowVirtualPointer = true,
                            hapticView = surface,
                        )
                    }
                } finally {
                    event.recycle()
                }
            }
        }

        fun dispatchCarCompanionPhoneMirror(surface: View, source: MotionEvent) {
            val service = instance ?: return
            val event = MotionEvent.obtain(source)
            Handler(Looper.getMainLooper()).post {
                try {
                    if (instance === service && active &&
                        service.carCompanion.carCompanionPhoneTrackpadSurface === surface
                    ) {
                        service.carCompanion.handleCarCompanionPhoneMirror(surface, event)
                    }
                } finally {
                    event.recycle()
                }
            }
        }

        fun detachCarCompanionPhoneTrackpad(surface: View) {
            val service = instance ?: return
            Handler(Looper.getMainLooper()).post {
                if (instance === service) service.carCompanion.detachCarCompanionPhoneTrackpad(surface)
            }
        }

        fun updateAutoDestinationSurface(surface: Surface) {
            val service = instance ?: return
            Handler(Looper.getMainLooper()).post {
                if (instance === service && active && service.autoOnlySession) {
                    service.replaceAutoDestinationSurface(surface)
                }
            }
        }

        /** Global navigation requested by the signature-protected CARDEX relay. */
        fun performCardexAction(action: String): Boolean {
            val service = instance ?: return false
            if ((action == "home" || action == "recents") && service.autoOnlySession &&
                service.plasmaShell?.showHome() == true) return true
            if (action == "recents") return service.recents.showRecentsOnTargetDisplay()
            val globalAction = when (action) {
                "back" -> GLOBAL_ACTION_BACK
                "home" -> GLOBAL_ACTION_HOME
                else -> return false
            }
            return service.performGlobalAction(globalAction)
        }

        fun cardexWorkspaces(): String = instance?.workspaces?.workspaceJson()?.toString() ?: "[]"

        fun saveCardexWorkspace(): String? = instance?.workspaces?.saveCurrentWorkspace()
            ?: "Dextop session is unavailable"

        fun launchCardexWorkspace(id: String): Boolean {
            val service = instance ?: return false
            val workspaces = service.workspaces.workspaceJson()
            for (index in 0 until workspaces.length()) {
                val workspace = workspaces.optJSONObject(index) ?: continue
                if (workspace.optString("id") == id) {
                    service.workspaces.launchOverlayWorkspace(workspace, closeMenu = false)
                    return true
                }
            }
            return false
        }

        fun showOverlayDemo(context: Context) {
            pendingDemo = true
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
                if (instance === service) service.virtualPointer.applyVirtualPointerProfile(profile)
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
    private val workspaces: Workspaces = Workspaces(this, object : WorkspacesHost, DesktopSession by session, OverlayMenu by overlayMenu {
        override fun currentDisplayTasks(): Map<String, Rect> = this@MirrorService.currentDisplayTasks()
        override val desktopEnvironment: DesktopEnvironment get() = this@MirrorService.desktopEnvironment
        override val launchedAppBounds: MutableMap<String, Rect> get() = this@MirrorService.launchedAppBounds
        override fun menuUsesLandscapeLayout(): Boolean = this@MirrorService.menuUsesLandscapeLayout()
        override fun packageName(): String = this@MirrorService.packageName()
        override fun placePackageTaskThroughBinder(displayId: Int, packageName: String, wanted: Rect): Boolean = this@MirrorService.placePackageTaskThroughBinder(displayId, packageName, wanted)
        override val recentsExecutor: java.util.concurrent.ExecutorService get() = this@MirrorService.recentsExecutor
        override fun usesDecoratedDirectAutoDisplay(): Boolean = this@MirrorService.usesDecoratedDirectAutoDisplay()
    })

    private val resolutionRepository by lazy { ResolutionRepository(this, logTag) }
    private val laptopTheme: LaptopThemeSettings = LaptopThemeSettings(this, object : LaptopThemeSettingsHost, DesktopSession by session, OverlayMenu by overlayMenu {
        override val laptopDeckContent: LinearLayout? get() = this@MirrorService.laptopDeckContent
        override var laptopLanguagePopup: View?
            get() = this@MirrorService.laptopLanguagePopup
            set(value) { this@MirrorService.laptopLanguagePopup = value }
        override val laptopPreviewThemeId: String? get() = this@MirrorService.laptopPreviewThemeId
        override var laptopSettingsVisible: Boolean
            get() = this@MirrorService.laptopSettingsVisible
            set(value) { this@MirrorService.laptopSettingsVisible = value }
        override val laptopTypeface: Typeface get() = this@MirrorService.laptopTypeface
        override fun rebuildLaptopDeck(showSettings: Boolean): Unit = this@MirrorService.rebuildLaptopDeck(showSettings)
        override val root: TouchRoutingFrame? get() = this@MirrorService.root
        override val swipeTyping: SwipeTyping get() = this@MirrorService.swipeTyping
    })

    private val inputDispatcher by lazy {
        InputDispatcher(privilegedAccess) { event, displayId, accepted, failure ->
            recordInputDispatch(event, displayId, accepted, failure)
        }
    }
    private val physicalInputRouter by lazy { PhysicalInputRouter(this, privilegedAccess) }
    private val externalDisplayDetector by lazy { ExternalDisplayDetector(this) }
    private val coverDisplayController by lazy { CoverDisplaySessionController(this) }
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
    /** Dextop's own window manager; null while the platform desktop owns the display. */
    private var plasmaShell: moe.n4tsu.dextop.plasma.DesktopShell? = null
    private var serviceDestroyed = false
    private var cursorView: CursorView? = null
    private val carCompanion: CarCompanionInput = CarCompanionInput(this, object : CarCompanionInputHost, DesktopSession by session, OverlayMenu by overlayMenu {
        override fun cancelDesktopTouchStream(): Unit = this@MirrorService.cancelDesktopTouchStream()
        override var cursorX: Float
            get() = this@MirrorService.cursorX
            set(value) { this@MirrorService.cursorX = value }
        override var cursorY: Float
            get() = this@MirrorService.cursorY
            set(value) { this@MirrorService.cursorY = value }
        override val inputGestureHandler: Handler get() = this@MirrorService.inputGestureHandler
        override fun refreshPrivilegedInputConfig(reason: String): Unit = this@MirrorService.refreshPrivilegedInputConfig(reason)
        override val virtualPointer: VirtualPointer get() = this@MirrorService.virtualPointer
    })

    private var menu: LinearLayout? = null
    private var menuPrimary: LinearLayout? = null
    private var overlayLayoutEditing = false
    private val launchedAppBounds = linkedMapOf<String, Rect>()
    private var pausedForAndroid = false
    private val pauseLifecycleHandler by lazy { Handler(mainLooper) }
    private var pauseDisableSelfRunnable: Runnable? = null
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
    private var laptopKeyboardView: SwipeObservingKeyboardLayout? = null
    private val swipeTyping: SwipeTyping = SwipeTyping(this, object : SwipeTypingHost, DesktopSession by session, OverlayMenu by overlayMenu {
        override val haptics: Haptics get() = this@MirrorService.haptics
        override fun injectLaptopKeyboardEvent(keyCode: Int, action: Int, metaState: Int, repeatCount: Int): Boolean = this@MirrorService.injectLaptopKeyboardEvent(keyCode, action, metaState, repeatCount)
        override val laptopKeyboardReady: Boolean get() = this@MirrorService.laptopKeyboardReady
        override val laptopKeyboardView: SwipeObservingKeyboardLayout? get() = this@MirrorService.laptopKeyboardView
        override var laptopLastEditorBounds: Rect?
            get() = this@MirrorService.laptopLastEditorBounds
            set(value) { this@MirrorService.laptopLastEditorBounds = value }
        override var laptopLastEditorBoundsAt: Long
            get() = this@MirrorService.laptopLastEditorBoundsAt
            set(value) { this@MirrorService.laptopLastEditorBoundsAt = value }
        override val laptopLegendButtons: MutableList<Pair<LaptopKeyTextView, String>> get() = this@MirrorService.laptopLegendButtons
        override fun laptopPalette(): LaptopPalette = this@MirrorService.laptopTheme.laptopPalette()
        override val laptopTypeface: Typeface get() = this@MirrorService.laptopTypeface
        override fun opacityColor(color: Int, opacity: Float): Int = this@MirrorService.laptopTheme.opacityColor(color, opacity)
        override fun opaqueColor(color: Int): Int = this@MirrorService.laptopTheme.opaqueColor(color)
        override val privilegedInputClient: PrivilegedInputClient get() = this@MirrorService.privilegedInputClient
        override val root: TouchRoutingFrame? get() = this@MirrorService.root
    })

    private var laptopLanguagePopup: View? = null
    private val laptopInputExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "Dextop-laptop-input").apply { isDaemon = true }
    }
    private val laptopKeyExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "Dextop-laptop-keys").apply { isDaemon = true }
    }
    private var laptopLastEditorBounds: Rect? = null
    private var laptopLastEditorBoundsAt = 0L
    private val imeRegion: ImeRegionProbe = ImeRegionProbe(this, object : ImeRegionProbeHost, DesktopSession by session, OverlayMenu by overlayMenu {
        override fun rawTouchscreenViewBounds(view: View?): Rect? = this@MirrorService.rawTouchscreenViewBounds(view)
        override fun refreshPrivilegedInputConfig(reason: String) = this@MirrorService.refreshPrivilegedInputConfig(reason)
        override fun regionFor(view: View?): RectF = this@MirrorService.regionFor(view)
        override val surfaceView: SurfaceView? get() = this@MirrorService.surfaceView
    })

    private var laptopFnButton: TextView? = null
    private var laptopMenuButton: TextView? = null
    private var laptopTrackpadView: View? = null
    internal enum class KeyboardDeckStyle { LAPTOP, BLACKBERRY, GAMEPAD, GAMEBOY }
    private var keyboardDeckStyle = KeyboardDeckStyle.LAPTOP
    private val gamepad: VirtualGamepad = VirtualGamepad(this, object : VirtualGamepadHost, DesktopSession by session, OverlayMenu by overlayMenu {
        override fun blackBerryActionRow(mainAction: View, edit: () -> Unit): LinearLayout = this@MirrorService.blackBerryActionRow(mainAction, edit)
        override val cast: CastController get() = this@MirrorService.cast
        override val coverBackButtonsActive: Boolean get() = this@MirrorService.coverBackButtonsActive
        override val haptics: Haptics get() = this@MirrorService.haptics
        override fun hasKeyboardStyleChoice(): Boolean = this@MirrorService.hasKeyboardStyleChoice()
        override fun isBlackBerryModeAvailable(): Boolean = this@MirrorService.isBlackBerryModeAvailable()
        override fun isLaptopStyleAvailable(): Boolean = this@MirrorService.isLaptopStyleAvailable()
        override val keyboardDeckStyle: MirrorService.KeyboardDeckStyle get() = this@MirrorService.keyboardDeckStyle
        override val laptopTypeface: Typeface get() = this@MirrorService.laptopTypeface
        override fun opacityColor(color: Int, opacity: Float): Int = this@MirrorService.laptopTheme.opacityColor(color, opacity)
        override val privilegedInputClient: PrivilegedInputClient get() = this@MirrorService.privilegedInputClient
        override fun setLaptopMode(enabled: Boolean, requestedStyle: KeyboardDeckStyle) = this@MirrorService.setLaptopMode(enabled, requestedStyle)
        override fun showKeyboardStyleMenu(panel: LinearLayout) = this@MirrorService.showKeyboardStyleMenu(panel)
    })

    private var coverBackButtonsActive = false
    private val coverBackButtonsPressed = linkedSetOf<Int>()
    private var laptopModeActive = false
    private var suspendedKeyboardDeckStyle: KeyboardDeckStyle? = null
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
    /** Full-height host Surface measured in physical view pixels before the deck is added. */
    private var laptopFullHostHeightPx = 0
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
    private val virtualPointer: VirtualPointer = VirtualPointer(this, object : VirtualPointerHost, DesktopSession by session, OverlayMenu by overlayMenu {
        override fun buildPrivilegedInputConfig(profile: String): IntArray = this@MirrorService.buildPrivilegedInputConfig(profile)
        override fun cancelDesktopTouchStream(): Unit = this@MirrorService.cancelDesktopTouchStream()
        override val carCompanionPhoneTrackpadSurface: View? get() = this@MirrorService.carCompanion.carCompanionPhoneTrackpadSurface
        override val coverTrackpadActive: Boolean get() = this@MirrorService.coverTrackpadActive
        override val desktopEnvironment: DesktopEnvironment get() = this@MirrorService.desktopEnvironment
        override val directTouch: Boolean get() = this@MirrorService.directTouch
        override fun displayGeometrySnapshot(reason: String, hostWidth: Int?, hostHeight: Int?): String = this@MirrorService.displayGeometrySnapshot(reason, hostWidth, hostHeight)
        override val externalOutput: ExternalOutput? get() = this@MirrorService.externalOutput
        override fun fallbackToSoftwarePointer(reason: String): Unit = this@MirrorService.fallbackToSoftwarePointer(reason)
        override val laptopKeyboardRequested: Boolean get() = this@MirrorService.laptopKeyboardRequested
        override fun logSuppressedRelativeTouchpadEvent(kind: String): Unit = this@MirrorService.logSuppressedRelativeTouchpadEvent(kind)
        override val physicalInputRouter: PhysicalInputRouter get() = this@MirrorService.physicalInputRouter
        override val privilegedInputClient: PrivilegedInputClient get() = this@MirrorService.privilegedInputClient
        override var privilegedInputStarting: Boolean
            get() = this@MirrorService.privilegedInputStarting
            set(value) { this@MirrorService.privilegedInputStarting = value }
        override fun refreshPrivilegedInputConfig(reason: String): Unit = this@MirrorService.refreshPrivilegedInputConfig(reason)
        override val root: TouchRoutingFrame? get() = this@MirrorService.root
        override fun startRawTouchscreenReaderIfEligible(): Unit = this@MirrorService.startRawTouchscreenReaderIfEligible()
        override fun stopRawTouchscreenReader(reason: String): Unit = this@MirrorService.stopRawTouchscreenReader(reason)
        override fun updateVirtualCursorVisibility(): Unit = this@MirrorService.updateVirtualCursorVisibility()
        override val virtualTouchpad: VirtualTouchpad get() = this@MirrorService.virtualTouchpad
    })


    private var coverTrackpadSurface: View? = null
    private var coverTrackpadActive = false
    private var coverTrackpadPreviousRuntimeProfile: String? = null

    private val virtualTouchpad: VirtualTouchpad = VirtualTouchpad(this, object : VirtualTouchpadHost, DesktopSession by session, OverlayMenu by overlayMenu {
        override var nativeTouchpadGestureActive: Boolean
            get() = this@MirrorService.nativeTouchpadGestureActive
            set(value) { this@MirrorService.nativeTouchpadGestureActive = value }
        override fun virtualMouseEvents(events: List<Any>, allowDirectTouch: Boolean): Boolean = this@MirrorService.virtualPointer.virtualMouseEvents(events, allowDirectTouch)
        override fun virtualMouseProcessAlive(): Boolean = this@MirrorService.virtualPointer.virtualMouseProcessAlive()
        override val virtualMouseReady: Boolean get() = this@MirrorService.virtualPointer.virtualMouseReady
        override fun virtualPointerInputActive(allowDirectTouch: Boolean): Boolean = this@MirrorService.virtualPointer.virtualPointerInputActive(allowDirectTouch)
        override val virtualPointerRegisteredProfile: String get() = this@MirrorService.virtualPointer.virtualPointerRegisteredProfile
    })

    /** Latches native MT routing at ACTION_DOWN so readiness cannot switch mid-stream. */
    private var nativeTouchpadGestureActive = false
    private var laptopHostUniqueId: String? = null
    private val laptopModifiers = LaptopModifierState()
    /** Modifier state and palette the key caps were last drawn for; -1 forces a redraw. */
    private var drawnLaptopModifierBits = -1
    private var drawnLaptopModifierPalette: LaptopPalette? = null
    private val laptopTypeface: Typeface by lazy {
        Typeface.createFromAsset(assets, "fonts/HarmonyOS_Sans_Medium.ttf")
    }
    private val laptopModifierButtons = mutableMapOf<Int, MutableList<TextView>>()
    private val laptopShortcutButtons = mutableMapOf<Int, TextView>()
    private val laptopKeyPresses = mutableMapOf<View, LaptopKeyPressState>()
    private var blackBerryNavigationRepeater: Runnable? = null
    private val laptopLegendButtons = mutableListOf<Pair<LaptopKeyTextView, String>>()
    private var targetDisplayId = -1
    private var targetWidth = 1920
    private var targetHeight = 1080
    private var density = 240
    private var secureDisplay = false
    private var showSystemDecorations = false
    private var autoOnlySession = false
    private var forceFreeformSession = false
    private val diagnostics: InputDiagnostics = InputDiagnostics(this, object : InputDiagnosticsHost, DesktopSession by session, OverlayMenu by overlayMenu {
        override fun postMainDelayed(delayMs: Long, action: () -> Unit) = this@MirrorService.postMainDelayed(delayMs, action)
    })

    private val recentsExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "Dextop-recents-task").apply { isDaemon = true }
    }
    private val recentsGeneration = AtomicInteger()
    private var autoDestinationSurface: Surface? = null
    private var autoOwnedDisplay: OwnedVirtualDisplay? = null

    /** True after the one-time Samsung HOME/decorations recovery has been used. */
    private var homeDecorationRetryUsed = false
    private var mirrorDisplayId = -1
    /** Phone desktop drawn directly into the host Surface; see DesktopEnvironment.directPlasmaDisplay. */
    private var phoneOwnedDisplay: OwnedVirtualDisplay? = null
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
    /** Latches an IME direct-touch route from ACTION_DOWN through UP/CANCEL. */
    private var imeDirectTouchHeld = false
    private var injectedDirectTouchActive = false
    private var directInjectionDownTime = 0L
    private var directSourceDownTime = 0L
    private var lastInjectedDirectTouch: MotionEvent? = null
    private var experimentalMultiTouch = false
    private var threeFingerEdgeSwipe = false
    private var edgeMenuTriggered = false
    private var edgeGesturePrimaryPointerId = MotionEvent.INVALID_POINTER_ID
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
    // Keep the last physical-display inventory independently from input
    // routing.  Samsung leaves physical mouse routing disabled, but its
    // external panels still participate in the display-topology transaction.
    // Once DisplayManager reports removal the panel is no longer returned by
    // snapshot(), so this is the only reliable way to identify that callback
    // as an external-display disconnect.
    @Volatile
    private var knownPhysicalExternalDisplayIds: Set<Int> = emptySet()
    private var lastForcedPhonePortrait: Boolean? = null
    private var lastForcedPhoneHalfTurn: Boolean? = null
    private val cast: CastController = CastController(this, object : CastControllerHost, DesktopSession by session, OverlayMenu by overlayMenu {
    })

    private var refreshRateReapplyGeneration = 0
    private var topologyReapplyGeneration = 0
    private val physicalInputRoutingSupported: Boolean
        get() = !Build.MANUFACTURER.equals("samsung", ignoreCase = true)
    /**
     * An unassociated mouse follows Android's focused display. A Plasma window's
     * own display takes focus when its app opens, so the cursor moved into that
     * window's display and could not leave it. Plasma forwards the pointer to its
     * windows itself, so physical mice stay on the desktop display.
     */
    private val routePhysicalMice: Boolean
        get() = routePhysicalMouseToDextop ||
            ((phoneOwnedDisplay != null || desktopEnvironment.pinPhysicalMouseToDesktop) && !autoOnlySession)
    @Volatile
    private var touchscreenReaderRunning = false
    @Volatile
    private var touchscreenReaderReady = false
    private var touchscreenReaderGeneration = 0L
    private var touchscreenReaderDevice = ""
    private var touchscreenReaderCandidateCount = 0
    private var rawTouchscreenTopologyRefreshGeneration = 0L
    /**
     * Display 0 can emit change callbacks without a corresponding input
     * geometry change on recent Samsung builds.  Re-publishing the native
     * EventHub reader for each of those callbacks interrupts an otherwise
     * healthy pointer stream, so retain the last configuration that actually
     * reached the reader.  Input-device callbacks still force a refresh.
     */
    private var appliedRawTouchscreenTopologyFingerprint: String? = null
    private val privilegedInputClientDelegate: Lazy<PrivilegedInputClient> = lazy {
        PrivilegedInputClient(this, object : PrivilegedInputClient.Listener {
            override fun onInputState(category: String, message: String) {
                Log.i(logTag, "privileged input [$category] $message")
                when (category) {
                    "uinput_created" -> {
                        privilegedInputClient.acknowledgeEngineStarted()
                        privilegedInputStarting = false
                        val generation = virtualPointer.virtualMouseGeneration
                        val profile = virtualPointer.virtualPointerRegisteredProfile
                        if (profile.isNotBlank()) {
                            virtualPointer.scheduleVirtualMouseReadyCheck(generation, profile, 0)
                        }
                    }

                    "device_discovered" -> {
                        touchscreenReaderRunning = true
                        // A readable EventHub node is only a candidate.  Do
                        // not suppress the overlay's MotionEvent path until
                        // the native reader has received an actual contact.
                        touchscreenReaderReady = false
                        touchscreenReaderCandidateCount =
                            touchscreenReaderCandidateCount.coerceAtLeast(1)
                        touchscreenReaderDevice = message.substringAfter("path=", "")
                            .substringBefore(' ')
                    }

                    "source_selected" -> {
                        touchscreenReaderRunning = true
                        touchscreenReaderReady = true
                        virtualPointer.virtualPointerOutputObserved = true
                        touchscreenReaderCandidateCount =
                            touchscreenReaderCandidateCount.coerceAtLeast(1)
                        touchscreenReaderDevice = message.substringAfter("path=", "")
                            .substringBefore(' ')
                        updateVirtualCursorVisibility()
                    }

                    "device_waiting" -> {
                        touchscreenReaderRunning = true
                        touchscreenReaderReady = false
                        touchscreenReaderCandidateCount = 0
                    }

                    "keyboard_created" -> {
                        scheduleLaptopKeyboardReadyCheck(laptopKeyboardGeneration, 0)
                    }

                    "gamepad_created" -> gamepad.replayVirtualGamepadState()

                    "keyboard_destroyed" -> clearLaptopKeyboardPublication("native_destroyed")

                    "stopped" -> {
                        if (!privilegedInputClient.isEngineRunning()) {
                            privilegedInputStarting = false
                            virtualPointer.virtualMouseReady = false
                            virtualPointer.virtualPointerOutputObserved = false
                            touchscreenReaderRunning = false
                            touchscreenReaderReady = false
                            touchscreenReaderCandidateCount = 0
                            updateVirtualCursorVisibility()
                        }
                    }

                    "disconnected", "worker_exited" -> {
                        privilegedInputClient.acknowledgeEngineStopped()
                        privilegedInputStarting = false
                        virtualPointer.virtualMouseReady = false
                        virtualPointer.virtualPointerOutputObserved = false
                        touchscreenReaderRunning = false
                        touchscreenReaderReady = false
                        touchscreenReaderCandidateCount = 0
                        fallbackToSoftwarePointer("$category:$message")
                    }

                    "uinput_destroyed" -> {
                        virtualPointer.virtualMouseReady = false
                        virtualPointer.virtualPointerOutputObserved = false
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
                haptics.performLaptopHaptic(laptopTrackpadView, strong, HapticSource.POINTER)
            }
        })
    }
    private val privilegedInputClient: PrivilegedInputClient by privilegedInputClientDelegate
    private var inputManager: InputManager? = null
    private var sensorManager: SensorManager? = null
    private var hingeAngle: Float? = null
    private var pendingLaptopMode: Boolean? = null
    private var pendingLaptopModeSince = 0L
    private var laptopModeEvaluationGeneration = 0L
    private val laptopModeDebounceMs = 420L

    private fun currentInputMode(): String = when {
        physicalMouseActive -> "physical_mouse"
        coverTrackpadActive && virtualPointer.virtualPointerInputActive(true) -> "cover_virtual_touchpad"
        rawTouchscreenBridgeConsumesTouchSurface() ->
            "raw_touchscreen_${virtualPointer.activeVirtualPointerProfile()}"

        laptopTrackpadInputActive() && virtualPointer.activeVirtualPointerProfile() == "touchpad" -> "virtual_touchpad"
        laptopTrackpadInputActive() -> "virtual_mouse"
        virtualPointer.virtualMouseInputActive() && virtualPointer.activeVirtualPointerProfile() == "touchpad" -> "virtual_touchpad"
        virtualPointer.virtualMouseInputActive() -> "virtual_mouse"
        directTouch -> "direct_touch"
        else -> "cursor_touchpad"
    }

    private fun laptopTrackpadInputActive(): Boolean =
        laptopModeActive && virtualPointer.virtualPointerInputActive(true)

    private fun dedicatedTrackpadInputActive(): Boolean =
        (laptopModeActive || coverTrackpadActive || carCompanion.carCompanionPhoneTrackpadSurface != null) &&
                virtualPointer.virtualPointerInputActive(true)

    private fun updateVirtualCursorVisibility() {
        val cursor = cursorView ?: return
        // The operation menu is handled by the accessibility overlay itself.
        // Never let a delayed uinput failure expose the white software cursor
        // over that menu while its touch controls are on screen.
        if (isMenuDisplayedForCursor()) {
            cursor.visibility = View.GONE
            return
        }
        // Device publication alone does not guarantee that InputReader has
        // rendered a pointer.  The software cursor remains authoritative
        // until the native EventHub stream or an overlay touch frame has
        // produced an actual virtual-pointer output.
        val systemPointerActive = virtualPointer.virtualMouseReady && virtualPointer.virtualPointerOutputObserved
        cursor.visibility = if (directTouch || systemPointerActive) {
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
        virtualPointer.stopVirtualMouse()
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
        updateVirtualCursorVisibility()
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
        // Diagnostics are written synchronously to the session report. Logging
        // every accepted down/up/move frame on the overlay's main thread makes
        // pointer motion visibly hitch on high-refresh Samsung panels. Keep all
        // rejected events, but sample successful input as a whole.
        if (accepted && now - diagnostics.lastInputDiagnosticAt < 750L) return
        diagnostics.lastInputDiagnosticAt = now
        diagnostics.inputDiagnosticSequence += 1
        val detail = buildString {
            append("seq=$diagnostics.inputDiagnosticSequence displayId=$displayId ")
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
        // See recordInputDispatch(): session logging must never become part of
        // the touch rendering path. One geometry sample per 750 ms is enough
        // for a useful report without synchronous file churn.
        if (now - diagnostics.lastTouchDiagnosticAt < 750L) return
        diagnostics.lastTouchDiagnosticAt = now
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
            posture.refreshFoldingApiState("hinge_sensor")
            Log.d(logTag, "hinge angle=$angle laptop=$laptopModeActive")
            OperationLog.i(
                this@MirrorService,
                "FoldState",
                "hinge sensor angle=$angle apiFoldable=$posture.foldingApiFoldable " +
                        "apiHalfOpened=$posture.foldingApiLaptopPosture apiHorizontal=$posture.foldingApiHorizontalHinge"
            )
            scheduleCoverDisplayLifecycleCheck("hinge_sensor")
            posture.updateLaptopModeForHinge(angle)
        }
    }
    private val inputDeviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) {
            refreshPhysicalInputState()
            scheduleRawTouchscreenTopologyRefresh("input_device_added_$deviceId", force = true)
            if (laptopKeyboardRequested) {
                scheduleLaptopKeyboardReadyCheck(laptopKeyboardGeneration, 0)
            }
        }

        override fun onInputDeviceRemoved(deviceId: Int) {
            refreshPhysicalInputState()
                scheduleRawTouchscreenTopologyRefresh("input_device_removed_$deviceId", force = true)
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
            scheduleRawTouchscreenTopologyRefresh("input_device_changed_$deviceId", force = true)
            if (laptopKeyboardRequested &&
                (deviceId == laptopKeyboardDeviceId || laptopKeyboardDeviceId < 0)
            ) {
                scheduleLaptopKeyboardReadyCheck(laptopKeyboardGeneration, 0)
            }
        }
    }
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            posture.refreshFoldingApiState("display_added", force = true)
            scheduleCoverDisplayLifecycleCheck("display_added_$displayId")
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
            val removedPhysicalExternalDisplay = displayId in knownPhysicalExternalDisplayIds
            posture.refreshFoldingApiState("display_removed", force = true)
            scheduleCoverDisplayLifecycleCheck("display_removed_$displayId")
            if (active) OperationLog.i(
                this@MirrorService,
                "DisplayGeometry",
                displayGeometrySnapshot("display_removed_$displayId")
            )
            if (active) refreshMenuGeometryAfterDisplayChange()
            refreshExternalDisplayState()
            if (removedPhysicalExternalDisplay) {
                scheduleExternalDisplayDisconnectTopologyReconcile(displayId)
            }
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
            posture.refreshFoldingApiState("display_changed", force = true)
            scheduleCoverDisplayLifecycleCheck("display_changed_$displayId")
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

    private var coverLifecycleCheckGeneration = 0

    private fun scheduleCoverDisplayLifecycleCheck(reason: String) {
        val coverState = coverDisplayController.state()
        if (!coverState.desktopActive && !coverState.backButtonsActive) return
        val generation = ++coverLifecycleCheckGeneration
        Handler(mainLooper).postDelayed({
            if (generation != coverLifecycleCheckGeneration) return@postDelayed
            posture.refreshFoldingApiState("cover_lifecycle", force = true)
            coverDisplayController.reconcileLifecycle(reason, coverDisplayLifecycleToken())
        }, 300L)
    }

    private fun coverDisplayLifecycleToken(): String {
        val manager = getSystemService(DisplayManager::class.java)
        val display = manager.getDisplay(Display.DEFAULT_DISPLAY)
        val mode = display?.mode
        val internalIds = internalDisplays(manager).map { it.displayId }.sorted()
        return buildString {
            append(display?.name.orEmpty())
            append('|').append(mode?.physicalWidth ?: 0)
            append('x').append(mode?.physicalHeight ?: 0)
            append("|internal=").append(internalIds.joinToString(","))
            append("|foldable=").append(isFoldableDevice())
            append("|half=").append(posture.foldingApiLaptopPosture)
        }
    }

    private fun refreshMenuGeometryAfterDisplayChange() {
        val panel = menu ?: return
        val frame = root ?: return
        frame.post {
            if ((!active && !demoMode) || menu !== panel) return@post
            val landscape = menuUsesLandscapeLayout()
            // The outer menu is resized after rotation, but each ScrollView
            // must also discard the width/weight selected when it was built.
            // Keeping those old child parameters creates a blank strip beside
            // the controls after switching between portrait and landscape.
            if (!landscape && workspaces.workspaceExpanded && panel.childCount > 1) {
                workspaces.workspaceExpanded = false
                while (panel.childCount > 1) panel.removeViewAt(panel.childCount - 1)
                menuPrimary?.let(workspaces::showWorkspaceMenu)
            }
            for (index in 0 until panel.childCount) {
                val scroll = panel.getChildAt(index) as? ScrollView ?: continue
                scroll.layoutParams = LinearLayout.LayoutParams(
                    if (landscape) dp(340) else 0,
                    -1,
                    if (landscape) 0f else 1f
                )
            }
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
            activateTopologyForIndependentDisplays("display_reconnect")
        }, 750)
    }

    /**
     * A topology write includes every currently connected physical panel.  On
     * unplug, the removed panel vanishes from DisplayManager before this
     * callback arrives, so merely refreshing the input routes leaves a stale
     * node in the system topology.  Rebuild from the surviving displays after
     * the adapter has settled; do not stop the phone-side Dextop session.
     */
    private fun scheduleExternalDisplayDisconnectTopologyReconcile(removedDisplayId: Int) {
        if (!active || stopping || removedDisplayId == mirrorDisplayId) return
        val generation = ++topologyReapplyGeneration
        Handler(mainLooper).postDelayed({
            if (generation != topologyReapplyGeneration || !active || stopping) return@postDelayed
            val manager = getSystemService(DisplayManager::class.java)
            if (manager.getDisplay(removedDisplayId) != null) return@postDelayed
            if (mirrorDisplayId < 0 || manager.getDisplay(mirrorDisplayId) == null) return@postDelayed
            activateTopologyForIndependentDisplays("external_display_removed")
            OperationLog.i(
                this,
                "DisplayTopology",
                "external display removed id=$removedDisplayId; rebuilt topology from surviving displays"
            )
        }, 300L)
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
        if (!active || suspendedForLockScreen || autoOnlySession) return
        val generation = ++posture.laptopPostureReevaluationGeneration
        android.os.Handler(mainLooper).postDelayed({
            if (generation != posture.laptopPostureReevaluationGeneration ||
                !active || suspendedForLockScreen
            ) return@postDelayed
            if (!isLaptopAutoDetectionEnabled()) return@postDelayed
            posture.refreshFoldingApiState("$reason settled", force = true)
            val angle = posture.filteredHingeAngle ?: hingeAngle
            if (angle != null) {
                posture.updateLaptopModeForHinge(angle)
            } else {
                posture.updateLaptopModeFromCurrentPosture(reason)
            }
        }, 240L)
    }

    private val navigationToken = Binder()
    private val desktopNavigationToken = Binder()
    private var navigationRestoreGeneration = 0
    private var navigationSuppressedDisplayId = -1
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
            // Android Auto keeps running with the phone asleep. The lock-screen suspend is for the
            // phone's own desktop: on an Auto-only session it removed the car display and, after
            // unlock, recreated the session as a phone desktop that opened on the phone.
            if (autoOnlySession) return
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> if (externalOutput?.mode == "external") {
                    OperationLog.i(this@MirrorService, "ExternalOutput", "phone screen off; the external desktop stays")
                } else suspendForLockScreen()
                Intent.ACTION_SCREEN_ON -> waitForConfirmedUnlock(screenLifecycleGeneration)
                Intent.ACTION_USER_PRESENT -> resumeAfterUnlock("user_present")
            }
        }
    }
    private var longPressTriggered = false
    private var longPressRunnable: Runnable? = null
    private val inputGestureHandler by lazy(LazyThreadSafetyMode.NONE) { Handler(mainLooper) }
    private var mirrorHostWidth = 0
    private var mirrorHostHeight = 0
    private val desktopWallpaperController by lazy(LazyThreadSafetyMode.NONE) {
        DesktopWallpaperController(applicationContext)
    }
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
                posture.refreshFoldingApiState("posture_monitor", force = true)
                posture.updateLaptopModeFromCurrentPosture("posture monitor")
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
        if (serviceDestroyed) {
            Log.w(logTag, "Ignoring accessibility connection delivered after service destruction")
            return
        }
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
        carCompanion.applyCarCompanionKeyFilter()
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
        val externalState = externalDisplayDetector.snapshot()
        knownPhysicalExternalDisplayIds = externalState.displayIds.toSet()
        physicalExternalDisplayConnected = physicalInputRoutingSupported && externalState.connected
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
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            // The query is coalesced and performed off the service/input
            // thread. It is intentionally driven by window changes rather
            // than by every MotionEvent.
            imeRegion.requestImeRegionProbe("accessibility:${event.eventType}")
            if (event.displayId == targetDisplayId) plasmaShell?.onWindowsChanged()
        }
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val windowClass = event.className?.toString().orEmpty()
            if (windowClass.contains("RecentsActivity", ignoreCase = true) ||
                windowClass.contains("recents", ignoreCase = true)
            ) {
                recents.scheduleRecentsFullscreenCorrection()
            }
        }
        event.source?.let { source ->
            if (event.displayId == targetDisplayId && source.isEditable && !source.isPassword) {
                val bounds = Rect().also(source::getBoundsInScreen)
                if (!bounds.isEmpty) {
                    laptopLastEditorBounds = bounds
                    laptopLastEditorBoundsAt = SystemClock.uptimeMillis()
                }
            }
        }
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

    private val recents: RecentsController = RecentsController(this, object : RecentsControllerHost, DesktopSession by session, OverlayMenu by overlayMenu {
        override val recentsExecutor: java.util.concurrent.ExecutorService get() = this@MirrorService.recentsExecutor
        override val recentsGeneration: AtomicInteger get() = this@MirrorService.recentsGeneration
    })

    private var autoOverlapCorrectionGeneration = 0
    private val carGestures: CarGestures = CarGestures(this, object : CarGesturesHost, DesktopSession by session, OverlayMenu by overlayMenu {
        override var cursorX: Float
            get() = this@MirrorService.cursorX
            set(value) { this@MirrorService.cursorX = value }
        override var cursorY: Float
            get() = this@MirrorService.cursorY
            set(value) { this@MirrorService.cursorY = value }
        override val inputDispatcher: InputDispatcher get() = this@MirrorService.inputDispatcher
        override val recentsExecutor: java.util.concurrent.ExecutorService get() = this@MirrorService.recentsExecutor
    })


    private fun startAutoOverlapCorrection() {
        val generation = ++autoOverlapCorrectionGeneration
        val displayId = targetDisplayId
        val handler = Handler(mainLooper)
        val autoTasks = mutableMapOf<Int, moe.n4tsu.dextop.plasma.TaskSnapshot>()
        var lastSplitSnapshot = ""
        // One bridge for the whole session: making one looks up the task service each time.
        val bridge by lazy {
            moe.n4tsu.dextop.plasma.TaskBridge({ name, type -> privilegedAccess.service(name, type) }, logTag)
        }
        val check = object : Runnable {
            override fun run() {
                if (!active || !autoOnlySession || targetDisplayId != displayId ||
                    generation != autoOverlapCorrectionGeneration) return
                val prefs = getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
                val splitCorrection = prefs.getBoolean("flutter.android_auto_correct_split_transfer", false)
                val overlapCorrection = prefs.getBoolean("flutter.android_auto_correct_fullscreen_overlap", false)
                if (forceFreeformSession) {
                    recentsExecutor.execute {
                        runCatching {
                            if (!active || targetDisplayId != displayId ||
                                generation != autoOverlapCorrectionGeneration) return@runCatching
                            val current = bridge.tasks(displayId).filter { it.activityType == 1 }
                            current.forEach { autoTasks[it.taskId] = it }
                            val enabled = splitCorrection
                            // The phone's tasks matter only to the correction.
                            val phoneTasks = if (enabled) bridge.tasks(0) else emptyList()
                            val snapshot = "enabled=$enabled display=$displayId auto=${current.map { it.taskId }} known=${autoTasks.keys} phoneSplit=${phoneTasks.filter { it.windowingMode == 6 }.map { it.taskId }}"
                            if (snapshot != lastSplitSnapshot) {
                                lastSplitSnapshot = snapshot
                                OperationLog.i(this@MirrorService, "CarCompanion", "split correction monitor $snapshot")
                            }
                            if (!enabled) return@runCatching
                            val transferred = phoneTasks.filter {
                                it.taskId in autoTasks && it.visible && it.windowingMode == 6
                            }
                            if (transferred.isNotEmpty()) {
                                val area = workspaces.workspaceLayoutEngine.usableArea(displayId, targetWidth, targetHeight)
                                val left = Rect(area.left, area.top, area.centerX(), area.bottom)
                                val right = Rect(area.centerX(), area.top, area.right, area.bottom)
                                for (task in transferred) {
                                    AutoSplitPlacement.begin(this@MirrorService, bridge, task, displayId, area)
                                    OperationLog.i(this@MirrorService, "CarCompanion",
                                        "split transfer corrected task=${task.taskId} display=$displayId bounds=$left")
                                }
                            }
                        }.onFailure { Log.w(logTag, "Auto split transfer correction failed", it) }
                    }
                }
                if (overlapCorrection) {
                    val full = Rect(0, 0, targetWidth, targetHeight)
                    val usable = runCatching {
                        workspaces.workspaceLayoutEngine.usableArea(displayId, targetWidth, targetHeight)
                    }.getOrNull()
                    if (usable != null && !usable.isEmpty && usable != full) {
                        recentsExecutor.execute {
                            runCatching {
                                if (!active || targetDisplayId != displayId ||
                                    generation != autoOverlapCorrectionGeneration) return@runCatching
                                for (task in bridge.tasks(displayId)) {
                                    if (task.displayId != displayId || task.activityType != 1 || !task.visible) continue
                                    val bounds = task.bounds
                                    // Do not constrain ordinary dragging or intentionally small windows.
                                    if (bounds.width() < usable.width() - 2 || bounds.height() < usable.height() - 2 ||
                                        usable.contains(bounds)) continue
                                    val id = task.taskId
                                    check(bridge.setWindowingMode(task, 5)) { moe.n4tsu.dextop.InlineStrings.format(moe.n4tsu.dextop.localText("uiUnableToChangeTaskWindowingModeTask0"), id) }
                                    check(bridge.setBounds(task, Rect(usable))) { moe.n4tsu.dextop.InlineStrings.format(moe.n4tsu.dextop.localText("uiUnableToResizeTask0"), id) }
                                    OperationLog.i(this@MirrorService, "CarCompanion",
                                        "fullscreen overlap corrected task=$id display=$displayId from=$bounds to=$usable")
                                }
                            }.onFailure { Log.w(logTag, "Auto fullscreen overlap correction failed", it) }
                        }
                    }
                }
                // Corrections must follow a transfer at once; with both off this only tracks the car's tasks.
                handler.postDelayed(this, if (splitCorrection || overlapCorrection) 300L else 1_000L)
            }
        }
        handler.postDelayed(check, 1000L)
    }

    override fun onInterrupt() = Unit

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshMenuGeometryAfterDisplayChange()
        if (!active || suspendedForLockScreen) return
        posture.refreshFoldingApiState("configuration_changed", force = true)
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

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (active && moe.n4tsu.dextop.plasma.PlasmaVolumeKeys.handle(this, event)) {
            if (event.action == KeyEvent.ACTION_DOWN) plasmaShell?.onVolumeChanged()
            return true
        }
        return plasmaShell?.handleKey(event) == true || forwardKeyEvent(event)
    }

    override fun onMotionEvent(event: MotionEvent) {
        // Events emitted by our uinput device already travel through
        // InputReader/InputDispatcher (and therefore display topology). Do
        // not feed them back through Dextop's target-display injector or they
        // would be delivered twice and the pointer would fight itself.
        if (virtualPointer.isVirtualMouseEvent(event)) return
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
                    val region = regionFor(view)
                    cursorX = (region.left + event.x / view.width * region.width()).coerceIn(0f, targetWidth - 1f)
                    cursorY = (region.top + event.y / view.height * region.height()).coerceIn(0f, targetHeight - 1f)
                }
            }
        }
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        // A completed stop sets active=false before the delayed accessibility
        // detach runs. Do not start a second teardown from that unbind; doing
        // so used to leave the service's stopping latch set forever.
        if (instance === this && !pausedForAndroid && (active || stopping || pending != null)) stop()
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
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        serviceDestroyed = true
        stopPlasmaShell(restoreWindows = false)
        runCatching { autoOwnedDisplay?.attachment?.release() }
        autoOwnedDisplay = null
        if (instance === this) instance = null
        cast.endCastSession("service_destroyed")
        if (coverDisplayController.state().backButtonsActive) {
            CoverBackButtonsActivity.finishActive()
            coverDisplayController.stopBackButtonMode { }
        }
        if (coverDisplayController.state().trackpadActive) {
            CoverTrackpadActivity.finishActive()
            coverDisplayController.stopTrackpadMode { }
        }
        diagnostics.windowDiagnosticGeneration.incrementAndGet()
        diagnostics.windowDiagnosticExecutor.shutdownNow()
        recentsGeneration.incrementAndGet()
        recentsExecutor.shutdownNow()
        imeRegion.imeRegionSessionGeneration += 1
        imeRegion.imeRegionProbeFollowUp.set(false)
        imeRegion.imeRegionProbeExecutor.shutdownNow()
        swipeTyping.laptopSwipeGeneration += 1
        swipeTyping.laptopSwipeImeCoordinator.close()
        swipeTyping.laptopSwipeExecutor.shutdownNow()
        laptopInputExecutor.shutdownNow()
        laptopKeyExecutor.shutdownNow()
        desktopWallpaperController.cancel()
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
        if (serviceDestroyed || instance !== this) {
            Log.w(logTag, "Ignoring session start on an obsolete accessibility service")
            return
        }
        // Do not let the delayed detach from "temporarily return to Android"
        // tear down a session that has already been resumed.
        pauseDisableSelfRunnable?.let(pauseLifecycleHandler::removeCallbacks)
        pauseDisableSelfRunnable = null
        // A touchpad-to-mouse compatibility fallback is scoped to one session;
        // every new session retries the user's selected profile.
        virtualPointer.virtualPointerRuntimeProfile = null
        privilegedPointerFallbackInProgress = false
        privilegedPointerFallbackActive = false
        laptopModeActive = false
        laptopBaseConfig = null
        laptopFullHostHeightPx = 0
        laptopManualOverride = false
        laptopAutoSuppressedByUser = false
        laptopAutoActivated = false
        laptopHostUniqueId = null
        posture.filteredHingeAngle = null
        pendingLaptopMode = null
        pendingLaptopModeSince = 0L
        laptopModeEvaluationGeneration += 1
        posture.laptopPostureReevaluationGeneration += 1
        posture.laptopHostMismatchSince = 0L
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
        diagnostics.lastInputDiagnosticAt = 0L
        diagnostics.lastTouchDiagnosticAt = 0L
        diagnostics.inputDiagnosticSequence = 0L
        if (!privilegedAccess.isAvailable()) {
            pending = null
            active = false
            val error = IllegalStateException(NativeStrings.text("nativeShizukuUnavailable"))
            completeStart(Result.failure(error))
            Log.e(logTag, "start rejected: Shizuku binder is unavailable", error)
            return
        }
        val cleanupPreferences = getSharedPreferences("dextop_cleanup_state", MODE_PRIVATE)
        workspaces.pendingPausedWorkspace = cleanupPreferences
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
        imeRegion.imeRegionSessionGeneration += 1
        imeRegion.imeRegionProbeFollowUp.set(false)
        imeRegion.imeTouchRegion = null
        imeRegion.imeRegionProbePending = false
        targetWidth = effectiveConfig.width
        targetHeight = effectiveConfig.height
        density = effectiveConfig.density
        secureDisplay = effectiveConfig.secure
        showSystemDecorations = effectiveConfig.decorations
        autoOnlySession = effectiveConfig.autoOnly
        AutoSplitPlacement.clear()
        // A phone controller belongs to its Auto session, never to a later
        // standalone session (its Activity may deliver onDestroy late).
        if (carCompanion.carCompanionPhoneTrackpadSurface != null) {
            virtualPointer.virtualPointerRuntimeProfile = carCompanion.carCompanionPhonePreviousRuntimeProfile
        }
        carCompanion.carCompanionPhoneTrackpadSurface = null
        carCompanion.carCompanionPhoneUsesNativeTouchpad = false
        carCompanion.carCompanionPhonePreviousRuntimeProfile = null
        lastPrivilegedInputSemanticConfig = null
        forceFreeformSession = effectiveConfig.forceFreeform
        // removeWindow() below releases the previous mirror backend and clears
        // autoDestinationSurface. Retain the newly requested host Surface in
        // a local until that cleanup has completed, otherwise every fresh Car
        // Companion session reaches createDirectAutoDisplay() with null.
        val requestedAutoDestinationSurface = if (autoOnlySession) {
            pendingAutoSurface?.takeIf { it.isValid }
                ?: CardexRelayService.activeDestinationSurface()
        } else {
            null
        }
        autoDestinationSurface = null
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
            phoneGuard.forcePhoneRotation(portrait)
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
            autoDestinationSurface = requestedAutoDestinationSurface?.takeIf { it.isValid }
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
        phoneGuard.setPhoneNavigationDisabled(true)
        Log.i(logTag, "start direct ${targetWidth}x$targetHeight/$density")
    }

    private val plasmaHost = object : moe.n4tsu.dextop.plasma.PlasmaShell.Host {
        override fun privilegedService(name: String, interfaceName: String): Any =
            privilegedAccess.service(name, interfaceName)

        override fun privilegedExecute(vararg arguments: String): Boolean {
            Thread({ privilegedAccess.execute(*arguments) }, "DextopPlasmaCommand").start()
            return true
        }

        override fun privilegedCommand(vararg arguments: String): String? =
            privilegedAccess.execute(*arguments).takeIf { it.succeeded }?.output

        override fun captureDisplay(callback: (moe.n4tsu.dextop.plasma.DesktopFrame?) -> Unit) {
            val view = surfaceView
            val surface = view?.holder?.surface
            val width = targetWidth
            val height = targetHeight
            if (view == null || surface == null || !surface.isValid || view.width <= 0 || view.height <= 0 ||
                width <= 0 || height <= 0
            ) {
                callback(null)
                return
            }
            // Thumbnails and effects never need more than ~1600px; keep the copy cheap.
            val scale = (1600f / maxOf(width, height)).coerceAtMost(1f)
            val bitmap = android.graphics.Bitmap.createBitmap(
                (width * scale).toInt().coerceAtLeast(1),
                (height * scale).toInt().coerceAtLeast(1),
                android.graphics.Bitmap.Config.ARGB_8888,
            )
            runCatching {
                android.view.PixelCopy.request(view, bitmap, { result ->
                    callback(
                        if (result == android.view.PixelCopy.SUCCESS) {
                            moe.n4tsu.dextop.plasma.DesktopFrame(bitmap, width, height)
                        } else null
                    )
                }, Handler(mainLooper))
            }.onFailure {
                Log.w(logTag, "desktop capture failed", it)
                callback(null)
            }
        }

        override fun launchDesktopHome(intent: Intent): Boolean {
            val displayId = targetDisplayId
            if (displayId < 0) return false
            // HOME may only be placed on a display that advertises system
            // decorations. Samsung sessions withdraw them right after their
            // own HOME launch, so re-advertise them just for this launch and
            // withdraw them again exactly like the regular attach path.
            val withdrawAgain = showSystemDecorations && desktopEnvironment.platformManaged && !forceFreeformSession
            if (withdrawAgain) setDesktopSystemDecorationsVisible(displayId, true)
            val launched = runCatching {
                startActivity(intent, ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle())
            }.onFailure {
                OperationLog.w(this@MirrorService, "PlasmaShell", "desktop HOME launch failed display=$displayId", it)
            }.isSuccess
            if (withdrawAgain) {
                listOf(250L, 700L, 1_500L).forEach { delay ->
                    root?.postDelayed({
                        if (active && targetDisplayId == displayId) setDesktopSystemDecorationsVisible(displayId, false)
                    }, delay)
                }
            }
            return launched
        }

        override fun stopSession() = stop()

        override fun switchToSystemWindowManager() {
            getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE).edit()
                .putString("flutter.window_manager", moe.n4tsu.dextop.plasma.WindowManagerSelection.SYSTEM)
                .apply()
            applyWindowManagerSelection("plasma_logout")
        }

        override fun restartShell() {
            stopPlasmaShell(restoreWindows = false)
            root?.postDelayed({ applyWindowManagerSelection("plasma_restart") }, 300L)
        }

        override fun log(message: String) {
            OperationLog.i(this@MirrorService, "PlasmaShell", message)
            Log.i(logTag, message)
        }
    }

    /**
     * Starts or stops Dextop's own window manager according to the display
     * setting.  The system desktop (Samsung DeX / Android desktop windowing)
     * remains the default and is restored whenever the shell stops.
     */
    private fun applyWindowManagerSelection(reason: String): Boolean {
        if (serviceDestroyed || instance !== this) return false
        val carShell = autoOnlySession && getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
            .getString("flutter.android_auto_shell", "dextop") == "dextop"
        if (!active || (autoOnlySession && !carShell) || targetDisplayId < 0) return false
        val selection = moe.n4tsu.dextop.plasma.WindowManagerSelection.current(this)
        val wantPlasma = carShell || selection != moe.n4tsu.dextop.plasma.WindowManagerSelection.SYSTEM
        val wantBook = !carShell
        val wantV2 = true
        val existing = plasmaShell
        if (!wantPlasma) {
            if (existing != null) {
                stopPlasmaShell(restoreWindows = true)
                launchHome()
                OperationLog.i(this, "PlasmaShell", "switched to system window manager reason=$reason")
            }
            return true
        }
        val existingIsV2 = existing is moe.n4tsu.dextop.plasma.CompositedWindowShell
        val existingIsAuto = existing is moe.n4tsu.dextop.plasma.DextopAutoShell
        val existingIsBook = existing is moe.n4tsu.dextop.plasma.BookShell
        if (existing != null && existing.displayId == targetDisplayId && existingIsV2 == wantV2 &&
            existingIsAuto == carShell && existingIsBook == wantBook) return true
        // Switching between v1, v2 and Book hands windows back to the display first.
        if (existing != null) stopPlasmaShell(restoreWindows = existingIsV2 != wantV2 || existingIsBook != wantBook)
        return runCatching {
            (if (carShell) {
                moe.n4tsu.dextop.plasma.DextopAutoShell(
                    this, targetDisplayId, targetWidth, targetHeight, density, plasmaHost,
                    fullscreenAppDisplays = true,
                    passiveShellFocus = desktopEnvironment.passivePlasmaShellFocus,
                    maxRateAppDisplays = desktopEnvironment.maxRateApplicationDisplays,
                    applicationOverlay = desktopEnvironment.plasmaApplicationOverlay && phoneOwnedDisplay == null,
                    privateAppDisplays = desktopEnvironment.privateApplicationDisplays,
                )
            } else {
                moe.n4tsu.dextop.plasma.BookShell(
                    this, targetDisplayId, targetWidth, targetHeight, density, plasmaHost,
                    fullscreenAppDisplays = desktopEnvironment.fullscreenApplicationDisplays,
                    passiveShellFocus = desktopEnvironment.passivePlasmaShellFocus,
                    maxRateAppDisplays = desktopEnvironment.maxRateApplicationDisplays,
                    applicationOverlay = desktopEnvironment.plasmaApplicationOverlay && phoneOwnedDisplay == null,
                    privateAppDisplays = desktopEnvironment.privateApplicationDisplays,
                )
            }).also {
                    plasmaShell = it
                    it.start()
                    if (carShell) carGestures.carVisibleArea?.let { r -> it.setAutoViewport(r[0],r[1],r[2],r[3]) }
                }
        }.onSuccess {
            OperationLog.i(this, "PlasmaShell", "Dextop window manager active display=$targetDisplayId reason=$reason")
        }.onFailure {
            plasmaShell = null
            OperationLog.e(this, "PlasmaShell", "Dextop window manager failed to start reason=$reason", it)
        }.isSuccess
    }

    /**
     * Starts exactly one desktop shell.  Plasma is a replacement HOME, not a
     * layer to be added after Samsung's SecondaryLauncher has already created
     * a DeX desktop and taskbar on the same display.
     */
    /** Starts the Dextop Auto shell again with growing pauses, e.g. while Dextop's access reconnects. */
    private fun retryDextopAutoShell(displayId: Int, attempt: Int) {
        if (attempt >= 10) {
            OperationLog.w(this, "PlasmaShell", "Dextop Auto shell did not start on display $displayId", null)
            return
        }
        postMainDelayed((500L shl attempt.coerceAtMost(4)).coerceAtMost(8_000L)) {
            if (!active || !autoOnlySession || targetDisplayId != displayId ||
                plasmaShell is moe.n4tsu.dextop.plasma.DextopAutoShell) return@postMainDelayed
            if (!PrivilegedAccess(logTag).isAvailable()) {
                moe.n4tsu.dextop.privilege.EmbeddedPrivilegeKeeper.ensure(this, "dextop_auto_shell")
            }
            if (!applyWindowManagerSelection("dextop_auto_retry_${attempt + 1}")) retryDextopAutoShell(displayId, attempt + 1)
            else OperationLog.i(this, "PlasmaShell", "Dextop Auto shell started on retry ${attempt + 1}")
        }
    }

    private fun launchSelectedDesktopHome(reason: String): Boolean {
        val plasmaSelected = if (autoOnlySession) {
            getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
                .getString("flutter.android_auto_shell", "dextop") == "dextop"
        } else moe.n4tsu.dextop.plasma.WindowManagerSelection.isPlasma(this)
        return if (plasmaSelected) {
            applyWindowManagerSelection(reason)
        } else {
            launchHome()
        }
    }

    private fun stopPlasmaShell(restoreWindows: Boolean) {
        val shell = plasmaShell ?: return
        plasmaShell = null
        runCatching { shell.stop(restoreWindows) }
            .onFailure { OperationLog.w(this, "PlasmaShell", "shell shutdown failed", it) }
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
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        // Touching the desktop abandons the pending swipe
                        // choice, but must not tear down the laptop keyboard.
                        // The pointer and keyboard share one native engine.
                        if (laptopModeActive) swipeTyping.hideLaptopSwipeCandidates()
                        activateTouchInput()
                    }
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
        cursor.visibility = if (directTouch || virtualPointer.virtualMouseInputActive()) View.GONE else View.VISIBLE
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
                    .getBoolean("flutter.keep_awake_during_session", true)
            ) {
                flags = flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            }
        }
        // Started for an external screen only: the desktop never appears on the phone first.
        externalStartPending = !autoOnlySession &&
            externalDisplayPreference("external_display_mode", "off") == "external" &&
            externalOutputDisplay() != null
        if (externalStartPending) {
            params.alpha = 0f
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            frame.postDelayed({ revealPhoneWindow() }, 6_000L)
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
            posture.refreshFoldingApiState("window_added", force = true)
            OperationLog.i(
                this,
                "FoldState",
                "laptop profile=${posture.laptopFoldProfile()} model=${Build.MODEL} " +
                        "device=${Build.DEVICE} requestedPortrait=$requestedPortrait " +
                        "apiHalfOpened=$posture.foldingApiLaptopPosture apiHorizontal=$posture.foldingApiHorizontalHinge"
            )
            val autoStart = isLaptopAutoDetectionEnabled() &&
                    isLaptopAutoOrientationEligible() && posture.currentLaptopPosture() == true
            val blackBerryAutoStart = requestedPortrait &&
                    !isFoldableDevice() &&
                    isBlackBerryModeEnabled() &&
                    getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
                        .getBoolean("flutter.blackberry_auto_start", false)
            val startInLaptopMode = isDebugLaptopModeForced() || laptopManualOverride || autoStart
            laptopAutoActivated = autoStart && !laptopManualOverride && !blackBerryAutoStart
            if (blackBerryAutoStart) {
                setLaptopMode(true, KeyboardDeckStyle.BLACKBERRY)
            } else if (startInLaptopMode) {
                setLaptopMode(true)
            }
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

    private fun blackBerryDeckFraction(): Float {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (prefs.getBoolean(KEY_BLACKBERRY_HEIGHT_MANUAL, false)) {
            return prefs.getInt(KEY_BLACKBERRY_HEIGHT_PERCENT, 34).coerceIn(30, 50) / 100f
        }
        val frame = laptopContent
        val portrait = (frame?.height ?: resources.displayMetrics.heightPixels) >
                (frame?.width ?: resources.displayMetrics.widthPixels)
        // BlackBerry mode has a permanently reserved control/function row in
        // addition to its four-row keyboard. Keep the original four-row key
        // area intact and add enough height for that fifth row.
        return if (portrait) .34f else .375f
    }

    private fun keyboardDeckWeight(): Float {
        if (keyboardDeckStyle != KeyboardDeckStyle.BLACKBERRY) return 1f
        val fraction = blackBerryDeckFraction().coerceIn(.18f, .45f)
        return fraction / (1f - fraction)
    }

    private fun desktopPaneFraction(): Float = 1f / (1f + keyboardDeckWeight())

    private data class LaptopKeyPressState(
        val keyCode: Int,
        val metaState: Int,
        var repeatCount: Int = 0,
        var repeater: Runnable? = null
    )

    private fun buildLaptopDeck(): View {
        // The internal IME is a transient swipe-commit bridge only. A service
        // restart or interrupted commit must not leave it selected while the
        // user performs ordinary key taps.
        if (keyboardDeckStyle != KeyboardDeckStyle.GAMEPAD &&
            keyboardDeckStyle != KeyboardDeckStyle.GAMEBOY
        ) {
            swipeTyping.laptopSwipeImeCoordinator.ensureExternalImeSelected()
        }
        swipeTyping.hideLaptopSwipeCandidates()
        laptopModifierButtons.clear()
        laptopShortcutButtons.clear()
        laptopLegendButtons.clear()
        laptopModifiers.reset()
        drawnLaptopModifierBits = -1
        val palette = laptopTheme.laptopPalette()
        val deck = FrameLayout(this).apply {
            setBackgroundColor(laptopTheme.opaqueColor(palette.background))
            // The deck is an opaque interaction surface.  Without a handler
            // on its empty areas, a tap between keys can fall through to the
            // mirrored Android surface underneath the keyboard.
            isClickable = true
            setOnTouchListener { _, _ -> true }
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            if (keyboardDeckStyle == KeyboardDeckStyle.BLACKBERRY) {
                setPadding(0, 0, 0, dp(8))
            } else {
                setPadding(dp(8), dp(8), dp(8), dp(8))
            }
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
        content.setBackgroundColor(laptopTheme.opacityColor(palette.background, palette.opacity))
        if (keyboardDeckStyle == KeyboardDeckStyle.GAMEPAD ||
            keyboardDeckStyle == KeyboardDeckStyle.GAMEBOY
        ) {
            laptopKeyboardView = null
            swipeTyping.laptopCandidateBar = null
            laptopTrackpadView = null
            content.setPadding(dp(10), dp(8), dp(10), dp(8))
            if (keyboardDeckStyle == KeyboardDeckStyle.GAMEBOY) {
                gamepad.buildVirtualGameboyDeck(content, palette)
            } else {
                gamepad.buildVirtualGamepadDeck(content, palette)
            }
            laptopDeck = deck
            laptopDeckContent = content
            return deck
        }
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
                setColor(laptopTheme.opacityColor(palette.trackpad, palette.opacity))
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
        val candidates = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(dp(4), dp(2), dp(4), dp(2))
        }
        swipeTyping.laptopCandidateBar = candidates
        content.addView(candidates, LinearLayout.LayoutParams(-1, 0).apply {
            bottomMargin = dp(3)
        })
        val keyboard = SwipeObservingKeyboardLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            swipeEnabled = swipeTyping.isLaptopSwipeEnabled()
            twoFingerNavigationEnabled = keyboardDeckStyle == KeyboardDeckStyle.BLACKBERRY
            swipeTrailColor = palette.selected
            // Empty space between keys is part of the theme background. It
            // must use the same opacity slider as the rest of the keyboard
            // area, while the opaque deck underneath prevents Android from
            // showing through or receiving those touches.
            setBackgroundColor(Color.TRANSPARENT)
            isClickable = true
            setOnTouchListener { _, _ -> true }
            listener = object : SwipeObservingKeyboardLayout.Listener {
                override fun onSwipeStarted(firstKeyCode: Int, provisionalKeySent: Boolean) {
                    // A deliberately slow swipe can outlive the short deferred
                    // DOWN window. Only that uncommon path needs cleanup;
                    // normal swipes never inject a provisional character.
                    if (provisionalKeySent) injectKey(KeyEvent.KEYCODE_DEL)
                    swipeTyping.hideLaptopSwipeCandidates()
                }

                override fun onSwipeFinished(points: List<PointF>) {
                    val uppercaseAll = laptopModifiers.uppercaseAll
                    val capitalizeFirst = laptopModifiers.shift && !uppercaseAll
                    swipeTyping.decodeLaptopSwipe(points, capitalizeFirst, uppercaseAll)
                    // A tapped Shift is one-shot. A Shift finger physically
                    // held during the swipe remains active until its ACTION_UP.
                    laptopModifiers.releaseLatchedShift()
                    refreshLaptopModifierKeys()
                }

                override fun onSwipeCancelled() {
                    swipeTyping.hideLaptopSwipeCandidates()
                }

                override fun onTwoFingerNavigationStarted(firstKeyCode: Int) {
                    // The first finger's DOWN already reached its child key.
                    // Remove that provisional printable character when the
                    // second finger turns the sequence into navigation.
                    if (firstKeyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z ||
                        firstKeyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9
                    ) {
                        injectKey(KeyEvent.KEYCODE_DEL)
                    }
                }

                override fun onTwoFingerDirectionChanged(keyCode: Int) {
                    if (keyCode == KeyEvent.KEYCODE_UNKNOWN) {
                        stopBlackBerryNavigationRepeat()
                    } else {
                        startBlackBerryNavigationRepeat(keyCode)
                    }
                }

                override fun onTwoFingerNavigationFinished() {
                    stopBlackBerryNavigationRepeat()
                }
            }
        }
        laptopKeyboardView = keyboard
        val keyboardRows = if (keyboardDeckStyle == KeyboardDeckStyle.BLACKBERRY) {
            LaptopKeyboardLayout.blackBerryRows(laptopFunctionRowVisible)
        } else {
            LaptopKeyboardLayout.laptopRows(laptopFunctionRowVisible)
        }
        keyboardRows.forEachIndexed { index, keys ->
            val row = buildLaptopKeyboardRow(keys)
            if (keyboardDeckStyle == KeyboardDeckStyle.BLACKBERRY) {
                row.setPadding(dp(8), 0, dp(8), 0)
                if (index == 0) {
                    row.tag = "blackberry_control_row"
                    styleBlackBerryTopRow(row, management = !laptopFunctionRowVisible)
                }
            }
            if (keyboardDeckStyle == KeyboardDeckStyle.LAPTOP &&
                laptopFunctionRowVisible && index == 0
            ) row.tag = "laptop_function_row"
            val rowWeight = if (
                keyboardDeckStyle == KeyboardDeckStyle.BLACKBERRY && index == 0
            ) .7f else 1f
            keyboard.addView(row, LinearLayout.LayoutParams(-1, 0, rowWeight))
        }
        content.addView(
            keyboard,
            LinearLayout.LayoutParams(
                -1,
                0,
                if (keyboardDeckStyle == KeyboardDeckStyle.BLACKBERRY) 1f else .66f,
            ).apply {
                bottomMargin = if (keyboardDeckStyle == KeyboardDeckStyle.BLACKBERRY) 0 else dp(7)
            },
        )
        if (swipeTyping.isLaptopSwipeEnabled()) keyboard.post { swipeTyping.prewarmLaptopSwipeDecoder() }
        if (keyboardDeckStyle == KeyboardDeckStyle.BLACKBERRY) {
            laptopDeck = deck
            laptopDeckContent = content
            return deck
        }
        val trackpadArea = FrameLayout(this).apply {
            addView(trackpad, FrameLayout.LayoutParams(-1, -1))
            // These are deck controls, not keyboard keys.  Keeping them as
            // siblings of the raw-touchpad surface preserves their original
            // lower-corner positions and prevents the EventHub bridge from
            // treating their taps as pointer gestures.
            addView(TextView(this@MirrorService).apply {
                text = localText("uiKeyFn")
                typeface = laptopTypeface
                textSize = 10f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(235, 231, 239))
                background = laptopKeyBackground(laptopFunctionRowVisible, LaptopKeyboardLayout.ALT)
                setOnLongClickListener {
                    if (demoMode) return@setOnLongClickListener false
                    laptopTheme.showLaptopKeyboardSettings()
                    true
                }
                setOnClickListener {
                    haptics.performLaptopHaptic(this)
                    setLaptopFunctionRowVisible(!laptopFunctionRowVisible)
                }
                setOnTouchListener { view, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> view.animate()
                            .scaleX(.92f).scaleY(.92f).alpha(.72f)
                            .setDuration(55).start()
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.animate()
                            .scaleX(1f).scaleY(1f).alpha(1f)
                            .setDuration(110).start()
                    }
                    false
                }
                laptopFnButton = this
            }, FrameLayout.LayoutParams(dp(58), dp(42), Gravity.BOTTOM or Gravity.START).apply {
                leftMargin = dp(10)
                bottomMargin = dp(10)
            })
            addView(TextView(this@MirrorService).apply {
                text = localText("uiKeyMenu")
                typeface = laptopTypeface
                textSize = 10f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(235, 231, 239))
                background = laptopKeyBackground(false, LaptopKeyboardLayout.ALT)
                setOnClickListener {
                    if (!demoMode) {
                        haptics.performLaptopHaptic(this)
                        toggleMenu()
                    }
                }
                setOnLongClickListener {
                    if (demoMode) return@setOnLongClickListener false
                    haptics.performLaptopHaptic(this, strong = true)
                    laptopTheme.showLaptopLanguagePopup(this)
                    true
                }
                laptopMenuButton = this
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

    private val coverButtons: CoverBackButtons = CoverBackButtons(this, object : CoverBackButtonsHost, DesktopSession by session, OverlayMenu by overlayMenu {
        override fun activateTopologyForIndependentDisplays(reason: String): Unit = this@MirrorService.activateTopologyForIndependentDisplays(reason)
        override fun cancelDesktopTouchStream(): Unit = this@MirrorService.cancelDesktopTouchStream()
        override val cast: CastController get() = this@MirrorService.cast
        override var coverBackButtonsActive: Boolean
            get() = this@MirrorService.coverBackButtonsActive
            set(value) { this@MirrorService.coverBackButtonsActive = value }
        override val coverBackButtonsPressed: MutableSet<Int> get() = this@MirrorService.coverBackButtonsPressed
        override val coverDisplayController: CoverDisplaySessionController get() = this@MirrorService.coverDisplayController
        override fun coverDisplayLifecycleToken(): String = this@MirrorService.coverDisplayLifecycleToken()
        override var coverTrackpadActive: Boolean
            get() = this@MirrorService.coverTrackpadActive
            set(value) { this@MirrorService.coverTrackpadActive = value }
        override var coverTrackpadPreviousRuntimeProfile: String?
            get() = this@MirrorService.coverTrackpadPreviousRuntimeProfile
            set(value) { this@MirrorService.coverTrackpadPreviousRuntimeProfile = value }
        override var coverTrackpadSurface: View?
            get() = this@MirrorService.coverTrackpadSurface
            set(value) { this@MirrorService.coverTrackpadSurface = value }
        override val directTouch: Boolean get() = this@MirrorService.directTouch
        override val gamepad: VirtualGamepad get() = this@MirrorService.gamepad
        override fun isFoldableDevice(): Boolean = this@MirrorService.isFoldableDevice()
        override val menuWidgets: MenuWidgets get() = this@MirrorService.menuWidgets
        override val privilegedInputClient: PrivilegedInputClient get() = this@MirrorService.privilegedInputClient
        override fun refreshPrivilegedInputConfig(reason: String): Unit = this@MirrorService.refreshPrivilegedInputConfig(reason)
        override fun showCoverDisplayFailure(error: Throwable): Unit = this@MirrorService.showCoverDisplayFailure(error)
        override fun showCoverDisplayMenu(panel: LinearLayout): Unit = this@MirrorService.showCoverDisplayMenu(panel)
        override fun trackpad(event: MotionEvent, sourceView: View, forceCursorMode: Boolean, allowVirtualPointer: Boolean, hapticView: View?): Boolean = this@MirrorService.trackpad(event, sourceView, forceCursorMode, allowVirtualPointer, hapticView)
        override val virtualPointer: VirtualPointer get() = this@MirrorService.virtualPointer
        override val virtualTouchpad: VirtualTouchpad get() = this@MirrorService.virtualTouchpad
    })

    private fun setLaptopFunctionRowVisible(visible: Boolean) {
        if (visible == laptopFunctionRowVisible) return
        val keyboard = laptopKeyboardView ?: return
        laptopFunctionRowVisible = visible
        laptopFnButton?.background = laptopKeyBackground(visible, LaptopKeyboardLayout.FN)
        if (keyboardDeckStyle == KeyboardDeckStyle.BLACKBERRY) {
            val previous = keyboard.findViewWithTag<View>("blackberry_control_row") ?: return
            val index = keyboard.indexOfChild(previous).coerceAtLeast(0)
            val replacement = buildLaptopKeyboardRow(LaptopKeyboardLayout.blackBerryTopRow(laptopFunctionRowVisible)).apply {
                tag = "blackberry_control_row"
                setPadding(dp(8), 0, dp(8), 0)
                styleBlackBerryTopRow(this, management = !visible)
                alpha = 0f
                translationY = -dp(10).toFloat()
            }
            keyboard.removeView(previous)
            keyboard.addView(replacement, index, LinearLayout.LayoutParams(-1, 0, .7f))
            replacement.animate().alpha(1f).translationY(0f).setDuration(160).start()
            refreshLaptopModifierKeys()
            return
        }
        if (visible) {
            TransitionManager.beginDelayedTransition(
                keyboard,
                ChangeBounds().apply { duration = 200 }
            )
            val row = buildLaptopKeyboardRow(LaptopKeyboardLayout.laptopRows(true).first()).apply {
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

    private fun setLaptopMode(
        enabled: Boolean,
        requestedStyle: KeyboardDeckStyle = KeyboardDeckStyle.LAPTOP,
    ) {
        // Laptop decks belong to the phone screen, which an Auto-only session does not use.
        if (enabled && autoOnlySession) return
        if (enabled && !demoMode &&
            (requestedStyle == KeyboardDeckStyle.GAMEPAD || requestedStyle == KeyboardDeckStyle.GAMEBOY) &&
            !gamepad.isExperimentalGamepadEnabled()
        ) return
        if (enabled && laptopModeActive && keyboardDeckStyle != requestedStyle) {
            switchActiveKeyboardStyle(requestedStyle)
            return
        }
        if (enabled == laptopModeActive) {
            if (enabled) {
                if (requestedStyle == KeyboardDeckStyle.GAMEPAD ||
                    requestedStyle == KeyboardDeckStyle.GAMEBOY
                ) {
                    stopLaptopHardwareKeyboard()
                    gamepad.setVirtualGamepadVisible(true)
                } else {
                    gamepad.setVirtualGamepadVisible(false)
                    startLaptopHardwareKeyboard()
                }
                virtualPointer.startVirtualMouse()
                startRawTouchscreenReaderIfEligible()
                coverButtons.syncCoverInputForKeyboardStyle()
            }
            return
        }
        if (enabled && !demoMode && !externalInputDeck && requestedStyle == KeyboardDeckStyle.LAPTOP &&
            !isLaptopCapableDevice()
        ) {
            OperationLog.i(
                this,
                "LaptopMode",
                "ignored request on a non-foldable phone-sized display"
            )
            return
        }
        if (enabled) keyboardDeckStyle = requestedStyle
        val frame = root ?: return
        val content = laptopContent ?: return
        val surface = surfaceView ?: return
        // Keep the full-screen logical profile so leaving laptop mode restores
        // the user's original resolution instead of the half-height pane.
        val restoreConfig = if (!enabled) laptopBaseConfig else null
        if (enabled && laptopBaseConfig == null && !externalInputDeck) {
            laptopBaseConfig = Config(
                targetWidth,
                targetHeight,
                density,
                secureDisplay,
                showSystemDecorations
            )
        }
        if (enabled && laptopFullHostHeightPx <= 0) {
            // targetHeight/laptopBaseConfig are logical desktop pixels and can
            // be much smaller when workspace magnification is enabled. Keep
            // this baseline in the same physical View coordinate space as
            // surface.height so a magnified upper pane is never mistaken for
            // the restored full-height host.
            laptopFullHostHeightPx = maxOf(surface.height, content.height)
        }
        laptopModeActive = enabled
        if (!enabled) laptopAutoActivated = false
        laptopHostUniqueId = if (enabled) defaultDisplayUniqueId() else null
        if (!enabled) {
            posture.laptopHostMismatchSince = 0L
            laptopModeEvaluationGeneration += 1
        }
        if (enabled) {
            // The lower laptop pane contains real Android Views (keys,
            // candidates, FN/MENU). Bypassing the hierarchy and dispatching
            // every event straight to SurfaceView makes all of them inert.
            root?.routeTouchesToSurface = false
            if (requestedStyle == KeyboardDeckStyle.GAMEPAD ||
                requestedStyle == KeyboardDeckStyle.GAMEBOY
            ) {
                stopLaptopHardwareKeyboard()
                gamepad.setVirtualGamepadVisible(true)
            } else {
                gamepad.setVirtualGamepadVisible(false)
                startLaptopHardwareKeyboard()
            }
            virtualPointer.startVirtualMouse()
            startRawTouchscreenReaderIfEligible()
            val deck = buildLaptopDeck().apply {
                alpha = 0f
                translationY = dp(28).toFloat()
            }
            content.addView(deck, LinearLayout.LayoutParams(-1, 0, keyboardDeckWeight()))
            deck.post {
                deck.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setInterpolator(PathInterpolator(.22f, 1f, .36f, 1f))
                    .setDuration(360L)
                    .start()
            }
            cursorView?.contentHeightFraction = desktopPaneFraction()
            menuScrim?.bringToFront()
            menu?.bringToFront()
            performanceHud?.bringToFront()
        } else {
            swipeTyping.restoreDextopSwipeIme()
            // Style is disabled from inside the visible operation overlay.
            // Routing directly to the desktop here bypasses that still-open
            // overlay (including its close button and edge recognizer), which
            // makes the whole screen appear frozen. Keep hierarchy routing
            // until toggleMenu() actually closes the overlay.
            root?.routeTouchesToSurface = !isMenuOverlayVisible()
            finishAllLaptopKeyPresses()
            // The raw reader also owns the normal full-screen pointer surface.
            // Keep it alive when leaving the deck unless the restored surface
            // is direct-touch, in which case the physical touchscreen must
            // continue to behave as a touchscreen instead of a touchpad.
            if (directTouch) stopRawTouchscreenReader("laptop_mode_disabled_direct_touch")
            stopLaptopHardwareKeyboard()
            gamepad.setVirtualGamepadVisible(false)
            // In tap/direct-touch mode the pointer belongs only to the
            // laptop trackpad while the deck is visible. Remove it as soon
            // as the deck is closed so Android cannot retain its pointer
            // focus for the next app on the phone surface.
            if (directTouch) {
                cancelDesktopTouchStream()
                virtualPointer.stopVirtualMouse()
            }
            val deck = laptopDeck
            laptopDeck = null
            laptopTheme.hideLaptopLanguagePopup()
            laptopTrackpadView = null
            laptopFnButton = null
            laptopMenuButton = null
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
            // Do not leave the native reader using its laptop-only trackpad
            // bounds while waiting for the asynchronous full-screen layout.
            // The visible overlay intentionally publishes PROFILE_DISABLED;
            // closing it will publish the normal full-screen pointer profile.
            refreshPrivilegedInputConfig("laptop_mode_disabled_immediate")
        }
        surface.requestLayout()
        frame.requestLayout()
        // The external screen keeps its own size; only a foldable's desktop shrinks to the upper half.
        if (!externalInputDeck) applyLaptopGeometryWhenLaidOut(enabled, baseOverride = restoreConfig)
        if (!enabled) {
            laptopBaseConfig = null
            laptopFullHostHeightPx = 0
        }
        coverButtons.syncCoverInputForKeyboardStyle()
    }

    private fun switchActiveKeyboardStyle(style: KeyboardDeckStyle) {
        if (!laptopModeActive || keyboardDeckStyle == style) return
        val content = laptopContent ?: return
        finishAllLaptopKeyPresses()
        if (style == KeyboardDeckStyle.GAMEPAD || style == KeyboardDeckStyle.GAMEBOY) {
            stopLaptopHardwareKeyboard()
        } else {
            gamepad.setVirtualGamepadVisible(false)
            startLaptopHardwareKeyboard()
        }
        laptopTheme.hideLaptopLanguagePopup()
        swipeTyping.hideLaptopSwipeCandidates()
        laptopDeck?.animate()?.cancel()
        laptopDeck?.let(content::removeView)
        laptopDeck = null
        laptopTrackpadView = null
        laptopFnButton = null
        laptopMenuButton = null
        keyboardDeckStyle = style
        val deck = buildLaptopDeck().apply {
            alpha = 0f
            translationY = dp(20).toFloat()
        }
        content.addView(deck, LinearLayout.LayoutParams(-1, 0, keyboardDeckWeight()))
        cursorView?.contentHeightFraction = desktopPaneFraction()
        deck.animate()
            .alpha(1f)
            .translationY(0f)
            .setInterpolator(PathInterpolator(.22f, 1f, .36f, 1f))
            .setDuration(280L)
            .start()
        content.requestLayout()
        surfaceView?.requestLayout()
        coverButtons.syncCoverInputForKeyboardStyle()
        applyLaptopGeometryWhenLaidOut(true)
        OperationLog.i(this, "KeyboardStyle", "switched to ${style.name.lowercase()}")
    }

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

    /** True while the operation menu is visible, including its closing animation. */
    private fun isMenuDisplayedForCursor(): Boolean {
        return menu?.visibility == View.VISIBLE || menuScrim?.visibility == View.VISIBLE
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
        val fullscreenBounds = rawTouchscreenViewBounds(surfaceView)
        val trackpadBounds = rawTouchscreenViewBounds(laptopTrackpadView)
        // A touchpad reports relative movement from its absolute contact
        // range.  Keep that range proportional to the actual input region;
        // the former fixed landscape 1839×1199 range made portrait input
        // horizontally over-sensitive and vertically sluggish.
        // In laptop mode the trackpad rectangle is only the gesture's entry
        // gate. Once a contact starts there, it may continue through the
        // keyboard and the upper display area. Publish a host-sized absolute
        // range so the native bridge does not pin that contact to the
        // trackpad's top/side edges while it remains down.
        val coverSurface = coverTrackpadSurface
        val companionSurface = carCompanion.carCompanionPhoneTrackpadSurface?.takeIf {
            autoOnlySession && it.isAttachedToWindow
        }
        val touchpadMaxX = if (carCompanion.carCompanionPhoneUsesNativeTouchpad &&
            (companionSurface?.width ?: 0) > 0
        ) {
            checkNotNull(companionSurface).width
        } else if (coverTrackpadActive && (coverSurface?.width ?: 0) > 0) {
            checkNotNull(coverSurface).width
        } else if (laptopModeActive) {
            hostWidth.coerceAtLeast(1)
        } else {
            hostWidth.coerceAtLeast(1)
        }
        val touchpadMaxY = if (carCompanion.carCompanionPhoneUsesNativeTouchpad &&
            (companionSurface?.height ?: 0) > 0
        ) {
            checkNotNull(companionSurface).height
        } else if (coverTrackpadActive && (coverSurface?.height ?: 0) > 0) {
            checkNotNull(coverSurface).height
        } else if (laptopModeActive) {
            hostHeight.coerceAtLeast(1)
        } else {
            hostHeight.coerceAtLeast(1)
        }
        val candidate = PrivilegedInputProtocol.buildConfig(
            profile = nativeProfile,
            rotation = rawTouchscreenDisplayRotation(),
            hostWidth = hostWidth,
            hostHeight = hostHeight,
            fullscreen = fullscreenBounds,
            trackpad = trackpadBounds,
            directTouch = directTouch,
            laptopMode = laptopModeActive,
            touchpadMaxX = touchpadMaxX,
            touchpadMaxY = touchpadMaxY,
            touchpadResolution = VIRTUAL_TOUCHPAD_RESOLUTION,
            debugAllEvents = BuildConfig.DEBUG,
            naturalScroll = virtualPointer.virtualMouseNaturalScroll(),
            mouseSensitivity = 1f,
            generation = 0,
            imeTouch = imeRegion.imeTouchBoundsOnHost()
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
        val profile = virtualPointer.virtualPointerRegisteredProfile.ifBlank { virtualPointer.activeVirtualPointerProfile() }
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

    private fun fallbackToSoftwarePointer(reason: String) {
        if (privilegedPointerFallbackInProgress || privilegedPointerFallbackActive) return
        if (!active || directTouch && !laptopModeActive && !coverTrackpadActive) {
            privilegedInputStarting = false
            updateVirtualCursorVisibility()
            return
        }
        if (isMenuDisplayedForCursor()) {
            // Menu input must remain owned by the menu overlay. A delayed
            // native error during the menu animation is not permission to
            // put the white software cursor on top of the controls.
            privilegedInputStarting = false
            cursorView?.visibility = View.GONE
            OperationLog.i(
                this,
                "InputRouting",
                "software cursor fallback suppressed while operation menu is displayed reason=$reason"
            )
            return
        }
        privilegedPointerFallbackInProgress = true
        try {
            virtualPointer.stopVirtualMouse()
            virtualPointer.virtualPointerRuntimeProfile = "software"
            privilegedPointerFallbackActive = true
            privilegedInputStarting = false
            virtualPointer.virtualMouseReady = false
            virtualPointer.virtualPointerOutputObserved = false
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

    private fun logSuppressedRelativeTouchpadEvent(kind: String) {
        val now = SystemClock.uptimeMillis()
        if (now - virtualPointer.virtualPointerLastUnsupportedEventLogAt < 1_000L) return
        virtualPointer.virtualPointerLastUnsupportedEventLogAt = now
        val message = "suppressed $kind for native touchpad profile; MT contacts must own motion and scrolling"
        OperationLog.w(this, "InputRouting", message)
        Log.w(logTag, message)
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
            posture.laptopHostMismatchSince = 0L
            return false
        }
        val now = SystemClock.uptimeMillis()
        if (posture.laptopHostMismatchSince == 0L) {
            posture.laptopHostMismatchSince = now
            return false
        }
        return now - posture.laptopHostMismatchSince >= posture.laptopHostMismatchDebounceMs
    }

    private fun laptopHostIsFullHeight(): Boolean {
        val fullHeight = laptopFullHostHeightPx
        val hostHeight = surfaceView?.height ?: return false
        if (fullHeight <= 0 || hostHeight <= 0) return false
        return hostHeight >= (fullHeight * 0.78f).toInt()
    }

    private fun applyFlutterLaptopModeSetting(enabled: Boolean) {
        // The deck belongs to the external screen now; the hinge and the setting do not move it.
        if (externalInputDeck) return
        if (!enabled) {
            if (laptopAutoActivated && !laptopManualOverride) setLaptopMode(false)
            return
        }
        hingeAngle?.let(posture::updateLaptopModeForHinge)
    }

    private fun applyLaptopGeometryWhenLaidOut(
        enabled: Boolean,
        attempt: Int = 0,
        baseOverride: Config? = null,
        preserveMenu: Boolean = false,
    ) {
        val content = laptopContent ?: return
        val surface = surfaceView ?: return
        content.postDelayed({
            if (laptopModeActive != enabled) return@postDelayed
            if (!active) {
                if (attempt < 20) applyLaptopGeometryWhenLaidOut(
                    enabled, attempt + 1, baseOverride, preserveMenu
                )
                return@postDelayed
            }
            val expectedHeight = if (enabled) {
                (content.height * desktopPaneFraction()).toInt()
            } else content.height
            if ((surface.width <= 0 || kotlin.math.abs(surface.height - expectedHeight) > 2) &&
                attempt < 20
            ) {
                applyLaptopGeometryWhenLaidOut(enabled, attempt + 1, baseOverride, preserveMenu)
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
            if (!enabled) {
                // Restoring only the virtual-display metrics is insufficient
                // on One UI/Pixel Launcher: WallpaperService can retain the
                // former upper-pane dimensions until the next reboot.
                sessionJournal.restoreWallpaperDimensions()
            }
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
            if (!preserveMenu) menuPrimary?.let(::showMainMenu)
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

    private fun isFoldableDevice(): Boolean {
        posture.refreshFoldingApiState("capability", force = true)
        // Fold8 can expose only its active internal panel while the session
        // starts, and a few One UI builds do not publish TYPE_HINGE_ANGLE to
        // third-party services.  Its known model/profile is therefore a
        // stable hardware capability signal, not an automatic-posture signal.
        // Without it, even the explicit overlay action is rejected as a
        // phone-sized non-foldable before the laptop deck is created.
        if (posture.laptopFoldProfile() == LaptopFoldProfile.FOLD8) return true
        // A fold transition can make WindowManager briefly publish an empty
        // feature list while the inactive panel is being attached.  That is
        // not proof that the device stopped being foldable. Keep the positive
        // API result, but fall back to stable hardware signals before ever
        // returning false so a transient result cannot disable auto detection.
        if (posture.foldingApiFoldable == true) return true
        val manager = getSystemService(DisplayManager::class.java)
        val hardwareFoldable = internalDisplays(manager).size >= 2 ||
                getSystemService(SensorManager::class.java)
                    .getDefaultSensor(Sensor.TYPE_HINGE_ANGLE) != null
        return hardwareFoldable || posture.foldingApiFoldable == true
    }

    /**
     * Laptop mode needs enough surface for two usable panes. Foldables are
     * explicitly supported even when one panel is narrower than a tablet;
     * otherwise require a tablet-class display (600dp smallest width).
     */
    private fun isLaptopCapableDevice(): Boolean {
        if (getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
                .getBoolean("flutter.experimental_force_laptop_mode", false)) return true
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

    private fun isBlackBerryModeEnabled(): Boolean =
        getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
            .getBoolean("flutter.experimental_blackberry_mode", false)

    private fun isCoverDisplayFeatureEnabled(): Boolean {
        val preferences = getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
        return if (preferences.contains("flutter.cover_display_enabled")) {
            preferences.getBoolean("flutter.cover_display_enabled", false)
        } else if (preferences.contains("flutter.experimental_cover_display")) {
            // Preserve an explicit selection made before this became a formal
            // Foldable feature.
            preferences.getBoolean("flutter.experimental_cover_display", false)
        } else {
            // The feature is enabled by default on supported foldables.
            true
        }
    }

    private fun isCurrentFoldableCoverDisplay(): Boolean {
        if (!isFoldableDevice()) return false
        val displays = internalDisplays(getSystemService(DisplayManager::class.java))
        return displays.size >= 2 && !isFoldableMainDisplay()
    }

    private fun isBlackBerryModeAvailable(): Boolean {
        return isBlackBerryModeEnabled()
    }

    private fun internalDisplays(manager: DisplayManager): List<Display> =
        manager.displays.filter { display ->
            runCatching { Display::class.java.getMethod("getType").invoke(display) as Int == 1 }
                .getOrDefault(display.displayId == Display.DEFAULT_DISPLAY)
        }

    private val laptopShortcutLabels = mapOf(
        KeyEvent.KEYCODE_C to localText("uiCopy"),
        KeyEvent.KEYCODE_V to moe.n4tsu.dextop.localText("uiPaste"),
        KeyEvent.KEYCODE_X to moe.n4tsu.dextop.localText("uiCut"),
        KeyEvent.KEYCODE_A to localText("uiSelectAll"),
        KeyEvent.KEYCODE_Z to moe.n4tsu.dextop.localText("uiUndo"),
        KeyEvent.KEYCODE_S to localText("uiSave"),
        KeyEvent.KEYCODE_N to moe.n4tsu.dextop.localText("uiNew"),
        KeyEvent.KEYCODE_P to localText("uiPrint"),
        KeyEvent.KEYCODE_F to moe.n4tsu.dextop.localText("uiFind"),
        KeyEvent.KEYCODE_W to localText("uiClose")
    )

    private fun laptopKeyButton(key: LaptopKey): TextView = LaptopKeyTextView(
        this,
        keyboardDeckStyle != KeyboardDeckStyle.BLACKBERRY &&
                (key.code == KeyEvent.KEYCODE_F || key.code == KeyEvent.KEYCODE_J),
        laptopTheme.laptopPalette().text
    ).apply {
        val palette = laptopTheme.laptopPalette()
        // A new key cap has not been drawn for the current modifier state yet.
        drawnLaptopModifierBits = -1
        val blackBerryLegend = if (
            keyboardDeckStyle == KeyboardDeckStyle.BLACKBERRY && '\n' in key.label
        ) key.label.split('\n', limit = 2) else null
        val displayedLabel = if (blackBerryLegend != null) {
            // Keep the primary letter on the exact same baseline as every
            // other key. The printed symbol is an independent overlay above
            // it, not a second TextView line that shifts both lines down.
            blackBerryLegend[1]
        } else if (keyboardDeckStyle == KeyboardDeckStyle.BLACKBERRY) {
            key.label
        } else {
            swipeTyping.laptopSwipeSymbol(key.code, swipeTyping.laptopSwipeLanguage())?.uppercase() ?: key.label
        }
        text = displayedLabel
        tag = key.code
        val usesCustomGlyph = (keyboardDeckStyle == KeyboardDeckStyle.BLACKBERRY &&
                key.code == LaptopKeyboardLayout.SHIFT) ||
                (keyboardDeckStyle == KeyboardDeckStyle.BLACKBERRY &&
                        (key.code == LaptopKeyboardLayout.BACK || key.code == KeyEvent.KEYCODE_ENTER))
        if (key.code != KeyEvent.KEYCODE_META_LEFT && !usesCustomGlyph) {
            laptopLegendButtons += this to displayedLabel
        }
        typeface = laptopTypeface
        textSize = if (displayedLabel.length > 2) 9f else 12f
        if (keyboardDeckStyle == KeyboardDeckStyle.BLACKBERRY && key.code == LaptopKeyboardLayout.MENU) {
            textSize = 10.8f
        }
        gravity = Gravity.CENTER
        setTextColor(palette.text)
        setTopSecondaryLabel(blackBerryLegend?.firstOrNull(), palette.text)
        background = laptopKeyBackground(false, key.code)
        if (key.code == LaptopKeyboardLayout.SPACER) {
            visibility = View.INVISIBLE
            isClickable = false
            return@apply
        }
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
                    palette.text
                ),
                if (keyboardDeckStyle == KeyboardDeckStyle.BLACKBERRY) .552f else .46f,
            )
        }
        if (keyboardDeckStyle == KeyboardDeckStyle.BLACKBERRY && key.code == LaptopKeyboardLayout.BACK) {
            text = ""
            setCustomGlyph(
                KeyboardGlyphDrawable(KeyboardGlyphDrawable.BACK, palette.text),
                .552f,
            )
        }
        if (keyboardDeckStyle == KeyboardDeckStyle.BLACKBERRY &&
            key.code == KeyEvent.KEYCODE_ENTER
        ) {
            text = ""
            setCustomGlyph(
                KeyboardGlyphDrawable(KeyboardGlyphDrawable.ENTER, palette.text),
                .62f,
            )
        }
        if (LaptopModifierState.showsActiveState(key.code)) {
            laptopModifierButtons.getOrPut(key.code) { mutableListOf() } += this
        }
        if (keyboardDeckStyle == KeyboardDeckStyle.BLACKBERRY && key.code == LaptopKeyboardLayout.SHIFT) {
            text = ""
            setCustomGlyph(
                KeyboardGlyphDrawable(KeyboardGlyphDrawable.SHIFT, palette.text),
                .5f,
            )
        }
        if (key.code == LaptopKeyboardLayout.FN) {
            laptopFnButton = this
            background = laptopKeyBackground(laptopFunctionRowVisible, LaptopKeyboardLayout.FN)
            setOnClickListener {
                haptics.performLaptopHaptic(this)
                setLaptopFunctionRowVisible(!laptopFunctionRowVisible)
            }
            setOnLongClickListener {
                if (demoMode) return@setOnLongClickListener false
                haptics.performLaptopHaptic(this)
                laptopTheme.showLaptopKeyboardSettings()
                true
            }
        } else if (key.code == LaptopKeyboardLayout.MENU) {
            setOnClickListener {
                if (!demoMode) {
                    haptics.performLaptopHaptic(this)
                    toggleMenu()
                }
            }
            setOnLongClickListener {
                if (demoMode) return@setOnLongClickListener false
                haptics.performLaptopHaptic(this, strong = true)
                laptopTheme.showLaptopLanguagePopup(this)
                true
            }
        }
        if (key.code in laptopShortcutLabels.keys) laptopShortcutButtons[key.code] = this
        setOnTouchListener { view, event ->
            if (key.code == LaptopKeyboardLayout.FN || key.code == LaptopKeyboardLayout.MENU) {
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
                            haptics.performLaptopHaptic(view)
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
                    haptics.performLaptopHaptic(view)
                    when {
                        LaptopModifierState.latchesOnPress(key.code) -> pressLaptopModifier(key.code)
                        key.code < 0 -> handleLaptopKey(key.code)
                        else -> startLaptopKeyPress(view, key.code)
                    }
                    view.animate()
                        .scaleX(.92f).scaleY(.92f).alpha(.72f)
                        .setDuration(55).start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (LaptopModifierState.latchesOnPress(key.code)) {
                        releaseLaptopModifier(key.code)
                    } else if (key.code >= 0) finishLaptopKeyPress(view)
                    view.animate()
                        .scaleX(1f).scaleY(1f).alpha(1f)
                        .setDuration(110).start()
                }
            }
            true
        }
    }

    private val LAPTOP_SPECIAL_KEYS = setOf(
        KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT
    )

    private fun laptopKeyBackground(selected: Boolean, keyCode: Int? = null) = GradientDrawable().apply {
        val palette = laptopTheme.laptopPalette()
        val lockedShift = keyCode == LaptopKeyboardLayout.SHIFT && laptopModifiers.shiftLocked
        val functionKey = keyCode != null &&
                (keyCode == KeyEvent.KEYCODE_ESCAPE || keyCode in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12 ||
                        keyCode == KeyEvent.KEYCODE_FORWARD_DEL)
        val modifierKey = keyCode != null &&
                (keyCode < 0 || keyCode == KeyEvent.KEYCODE_META_LEFT ||
                        keyCode == KeyEvent.KEYCODE_ALT_LEFT || keyCode == KeyEvent.KEYCODE_ALT_RIGHT)
        val specialKey = keyCode in LAPTOP_SPECIAL_KEYS
        val fill = if (lockedShift) {
            palette.selected
        } else if (selected) {
            palette.selected
        } else {
            when {
                functionKey || modifierKey || specialKey -> palette.keyVariant
                else -> palette.key
            }
        }
        setColor(laptopTheme.opacityColor(fill, palette.keyOpacity))
        setStroke(
            dp(1),
            if (selected || lockedShift) {
                palette.text
            } else {
                palette.border
            }
        )
        cornerRadius = dp(palette.radius.toInt()).toFloat()
    }

    private fun styleBlackBerryTopRow(row: LinearLayout, management: Boolean) {
        val palette = laptopTheme.laptopPalette()
        row.background = if (management) {
            BottomDividerDrawable(
                palette.border,
                dp(1).coerceAtLeast(1),
                dp(2),
            )
        } else {
            null
        }
        for (index in 0 until row.childCount) {
            val child = row.getChildAt(index)
            (child.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
                if (management) {
                    // Divider directly below the management keys, followed by
                    // 2 dp of space before QWERTY. The divider sits at the top
                    // edge of that space, not in its center.
                    params.topMargin = 0
                    params.bottomMargin = dp(2)
                } else {
                    // The FN row has no divider. Split the normal 4 dp
                    // inter-row spacing evenly so the F keys retain the same
                    // visual gap as every other keyboard row.
                    params.topMargin = dp(2)
                    params.bottomMargin = dp(2)
                }
                child.layoutParams = params
            }
            if (management) child.background = ColorDrawable(Color.TRANSPARENT)
        }
    }

    private fun rebuildLaptopDeck(showSettings: Boolean = false) {
        val content = laptopContent ?: return
        // Do not fade the whole lower pane: it briefly exposes Android behind
        // the overlay. The replacement is immediate, then the new deck slides in.
        finishAllLaptopKeyPresses()
        laptopTheme.hideLaptopLanguagePopup()
        swipeTyping.hideLaptopSwipeCandidates()
        laptopDeck?.let { content.removeView(it) }
        laptopSettingsVisible = false
        val nextDeck = buildLaptopDeck().apply {
            alpha = 0f
            translationY = dp(28).toFloat()
        }
        content.addView(nextDeck, LinearLayout.LayoutParams(-1, 0, keyboardDeckWeight()))
        nextDeck.post {
            nextDeck.animate()
                .alpha(1f)
                .translationY(0f)
                .setInterpolator(PathInterpolator(.22f, 1f, .36f, 1f))
                .setDuration(320L)
                .start()
        }
        content.requestLayout()
        if (showSettings) content.post { laptopTheme.showLaptopKeyboardSettings() }
    }

    private fun handleLaptopKey(keyCode: Int) {
        if (!laptopModifiers.toggle(keyCode)) {
            if (keyCode == LaptopKeyboardLayout.BACK) {
                injectTargetedBack()
            } else {
                injectKey(keyCode, laptopModifiers.metaState(keyCode))
                laptopModifiers.clearLatched()
            }
        }
        refreshLaptopModifierKeys()
    }

    private fun pressLaptopModifier(keyCode: Int) {
        if (laptopModifiers.press(keyCode)) refreshLaptopModifierKeys()
    }

    private fun releaseLaptopModifier(keyCode: Int) {
        val released = laptopModifiers.release(
            keyCode,
            SystemClock.uptimeMillis(),
            ViewConfiguration.getDoubleTapTimeout().toLong(),
        )
        if (released) refreshLaptopModifierKeys()
    }

    private fun injectTargetedBack() {
        // Binder/input dispatch can block on some One UI builds. Never make a
        // key animation or the rest of the overlay wait for that IPC.
        laptopInputExecutor.execute {
            if (laptopKeyboardReady &&
                injectLaptopKeyboardEvent(KeyEvent.KEYCODE_BACK, KeyEvent.ACTION_DOWN, 0, 0)
            ) {
                injectLaptopKeyboardEvent(KeyEvent.KEYCODE_BACK, KeyEvent.ACTION_UP, 0, 0)
                return@execute
            }
            if (targetDisplayId < 0) return@execute
            val now = SystemClock.uptimeMillis()
            val down = KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0)
            val up = KeyEvent(now, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, 0)
            runCatching {
                check(inputDispatcher.send(down, targetDisplayId))
                check(inputDispatcher.send(up, targetDisplayId))
            }.onFailure { Log.e(logTag, "BlackBerry back injection failed", it) }
        }
    }

    private fun startLaptopKeyPress(view: View, keyCode: Int) {
        swipeTyping.laptopSwipeImeCoordinator.ensureExternalImeSelected()
        finishLaptopKeyPress(view)
        val symbolKey = if (
            keyboardDeckStyle == KeyboardDeckStyle.BLACKBERRY && laptopModifiers.symbolMode
        ) LaptopKeyLegends.blackBerrySymbolKey(keyCode) else null
        val effectiveKeyCode = symbolKey?.first ?: keyCode
        var metaState = laptopModifiers.metaState(effectiveKeyCode)
        if (symbolKey?.second == true) metaState = metaState or KeyEvent.META_SHIFT_ON
        if (!injectLaptopKeyboardEvent(
                effectiveKeyCode, KeyEvent.ACTION_DOWN, metaState, 0
            )) return

        // A held modifier is part of a real multi-touch chord and must remain
        // active for every repeat until its own finger is released. A latched
        // modifier that was tapped alone remains one-shot.
        laptopModifiers.consumeForKeyPress(usedSymbol = symbolKey != null)
        refreshLaptopModifierKeys()

        val state = LaptopKeyPressState(effectiveKeyCode, metaState)
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
        stopBlackBerryNavigationRepeat()
    }

    private fun startBlackBerryNavigationRepeat(keyCode: Int) {
        stopBlackBerryNavigationRepeat()
        emitBlackBerryNavigationKey(keyCode)
        val repeat = object : Runnable {
            override fun run() {
                if (blackBerryNavigationRepeater !== this) return
                emitBlackBerryNavigationKey(keyCode)
                swipeTyping.laptopSwipeHandler.postDelayed(
                    this,
                    ViewConfiguration.getKeyRepeatDelay().toLong(),
                )
            }
        }
        blackBerryNavigationRepeater = repeat
        swipeTyping.laptopSwipeHandler.postDelayed(
            repeat,
            ViewConfiguration.getKeyRepeatTimeout().toLong(),
        )
    }

    private fun emitBlackBerryNavigationKey(keyCode: Int) {
        if (laptopKeyboardReady &&
            injectLaptopKeyboardEvent(keyCode, KeyEvent.ACTION_DOWN, 0, 0)
        ) {
            injectLaptopKeyboardEvent(keyCode, KeyEvent.ACTION_UP, 0, 0)
        } else {
            injectKey(keyCode)
        }
    }

    private fun stopBlackBerryNavigationRepeat() {
        blackBerryNavigationRepeater?.let(swipeTyping.laptopSwipeHandler::removeCallbacks)
        blackBerryNavigationRepeater = null
    }

    /**
     * Redraws the key caps that show modifier state. Every key press ends
     * here, and most leave the modifiers as they were, so an unchanged state
     * and palette skip the pass.
     */
    private fun refreshLaptopModifierKeys() {
        val palette = laptopTheme.laptopPalette()
        val bits = laptopModifiers.visualBits
        if (bits == drawnLaptopModifierBits && palette === drawnLaptopModifierPalette) return
        drawnLaptopModifierBits = bits
        drawnLaptopModifierPalette = palette
        laptopModifierButtons.forEach { (code, buttons) ->
            val selected = laptopModifiers.isActive(code)
            buttons.forEach { it.background = laptopKeyBackground(selected, code) }
        }
        if (keyboardDeckStyle == KeyboardDeckStyle.BLACKBERRY) {
            laptopModifierButtons[LaptopKeyboardLayout.SHIFT].orEmpty().forEach { button ->
                (button as? LaptopKeyTextView)?.apply {
                    text = ""
                    setCustomGlyph(
                        KeyboardGlyphDrawable(
                            if (laptopModifiers.shiftLocked) KeyboardGlyphDrawable.SHIFT_LOCKED
                            else KeyboardGlyphDrawable.SHIFT,
                            palette.text,
                        ),
                        .5f,
                    )
                }
            }
        }
        refreshLaptopKeyLegends()
        refreshLaptopShortcutLabels()
    }

    private fun refreshLaptopKeyLegends() {
        laptopLegendButtons.forEach { (button, base) ->
            val symbol = LaptopKeyLegends.displayed(base, laptopModifiers)
            if (!TextUtils.equals(button.text, symbol)) button.text = symbol
        }
    }

    private fun refreshLaptopShortcutLabels() {
        val secondaryColor = if (laptopTheme.laptopKeyboardTheme() == "crimson") {
            Color.rgb(230, 143, 105)
        } else Color.rgb(170, 165, 177)
        laptopShortcutButtons.forEach { (keyCode, button) ->
            val primary = KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")
            if (!TextUtils.equals(button.text, primary)) button.text = primary
            (button as? LaptopKeyTextView)?.setSecondaryLabel(
                laptopShortcutLabels[keyCode].takeIf { laptopModifiers.control },
                secondaryColor
            )
        }
    }

    private fun menuLayoutParams(): FrameLayout.LayoutParams {
        return if (menuUsesLandscapeLayout()) {
            FrameLayout.LayoutParams(dp(if (workspaces.workspaceExpanded) 680 else 340), -2, Gravity.START).apply {
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
        cast.stopCastRouteDiscovery()
        menuWidgets.animateMenuResize(panel)
        endOverlayTextInput()
        setOverlayFocusable(false)
        panel.removeAllViews()
        panel.addView(
            menuWidgets.menuTitle(
                "Dextop",
                if (workspaces.workspaceExpanded) "‹" else "›",
                "workspace_toggle"
            ) {
                if (demoMode) demoExplanation(NativeStrings.text("nativeViewSavedWorkspacesAndSaveCurrentArrangement"))
                workspaces.toggleWorkspacePanel()
            })
        addCustomControls(panel, overlayButtonOrder())
        panel.addView(
            menuWidgets.actionButton(
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
            modes.addView(menuWidgets.choiceButton(NativeStrings.text("nativeCursor"), !directTouch) {
                setSavedTouchMode(false)
                if (demoMode) demoExplanation(NativeStrings.text("nativeUseTheScreenAsATrackpadTo"))
                refreshModes()
            }, LinearLayout.LayoutParams(0, dp(44), 1f))
            modes.addView(menuWidgets.choiceButton(NativeStrings.text("nativeTap"), directTouch) {
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
                    !line.contains("type=home") && workspaces.isWorkspaceApp(it)
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

    private fun usesDecoratedDirectAutoDisplay(): Boolean =
        autoOnlySession && forceFreeformSession && autoOwnedDisplay != null && targetDisplayId >= 0

    /**
     * ActivityOptions launch bounds are ignored when the package already has
     * a task. The Electron implementation solves that by finding the task,
     * forcing it back to freeform, then resizing it. Do the same only for the
     * decorated direct Auto display; regular Samsung DeX continues to manage
     * its own windows.
     */

    private fun placePackageTaskThroughBinder(
        displayId: Int,
        packageName: String,
        wanted: Rect,
    ): Boolean = runCatching {
        val service = privilegedAccess.service("activity_task", "android.app.IActivityTaskManager")
        val taskManagerInterface = Class.forName("android.app.IActivityTaskManager")
        val getTasks = taskManagerInterface.methods.firstOrNull {
            it.name == "getTasks" && it.parameterTypes.contentEquals(
                arrayOf(
                    Int::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                )
            )
        } ?: error(moe.n4tsu.dextop.localText("uiIActivityTaskManagerGetTasksIntBooleanBooleanIntUnavailable"))
        val tasks = getTasks.invoke(service, 128, true, false, displayId) as? List<*>
            ?: return@runCatching false
        val task = tasks.firstOrNull { candidate ->
            candidate != null && recents.intField(candidate, "displayId") == displayId &&
                taskPackageName(candidate) == packageName
        } ?: return@runCatching false
        val taskId = recents.intField(task, "taskId")
        taskManagerInterface.methods.firstOrNull {
            it.name == "setTaskWindowingMode" && it.parameterTypes.size == 3
        }?.invoke(service, taskId, 5, false)
        val resizeTask = taskManagerInterface.methods.firstOrNull {
            it.name == "resizeTask" && it.parameterTypes.size == 3
        } ?: error(moe.n4tsu.dextop.localText("uiIActivityTaskManagerResizeTaskUnavailable"))
        resizeTask.invoke(service, taskId, Rect(wanted), 0)
        OperationLog.i(
            this,
            "Workspace",
            "decorated Auto task placed package=$packageName task=$taskId display=$displayId bounds=$wanted",
        )
        true
    }.onFailure {
        OperationLog.w(
            this,
            "Workspace",
            "decorated Auto placement failed package=$packageName display=$displayId",
            it,
        )
    }.getOrDefault(false)

    private fun taskPackageName(task: Any): String? {
        for (fieldName in arrayOf("topActivity", "baseActivity", "origActivity")) {
            val component = runCatching {
                task.javaClass.getField(fieldName).get(task) as? ComponentName
            }.getOrNull()
            if (component != null) return component.packageName
        }
        return null
    }

    private fun packageName(): String = applicationContext.packageName

    private val controlCenter: ControlCenter = ControlCenter(this, object : ControlCenterHost, DesktopSession by session, OverlayMenu by overlayMenu {
        override val root: TouchRoutingFrame? get() = this@MirrorService.root
        override val rootWindowParams: WindowManager.LayoutParams? get() = this@MirrorService.rootWindowParams
        override val windowManager: WindowManager? get() = this@MirrorService.windowManager
    })

    private fun clearOverlayDisplayRequestTwice(reason: String) {
        val preservedSpecs = AndroidAutoMirrorActivity.autoOverlaySpecs() +
            coverDisplayController.ownedSpecs()
        repeat(2) { pass ->
            runCatching { displayBackend.clearRequestPreserving(preservedSpecs) }
                .onSuccess {
                    OperationLog.i(
                        this,
                        "DisplayBackend",
                        "clear request issued reason=$reason pass=${pass + 1}/2 " +
                                "preservedIndependent=${preservedSpecs.isNotEmpty()}"
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
        val pausedWorkspace = workspaces.captureCurrentWorkspace()
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
        phoneGuard.setPhoneNavigationDisabled(false)
        phoneGuard.releasePhoneRotation(clearSnapshot = true)
        // Detach the accessibility host first. Surface destruction normally
        // releases the VirtualDisplay immediately, which makes One UI try to
        // unregister gesture-exclusion listeners from an already removed
        // display. That WindowManager exception leaves Back and Circle to
        // Search broken until SystemUI is restarted.
        detachHostWindow()
        android.os.Handler(mainLooper).postDelayed({
            val autoSessionActive = AndroidAutoMirrorActivity.isAutoSessionActive()
            val coverSessionActive = coverDisplayController.state().desktopActive
            runCatching {
                if (autoSessionActive || coverSessionActive) {
                    DisplayEnvironmentSettings(this).activateTopologyForOverlays(
                        AndroidAutoMirrorActivity.autoOverlayDisplayIds() +
                            coverDisplayController.ownedDisplayIds()
                    )
                } else {
                    DisplayEnvironmentSettings(this).restoreTopology()
                }
            }.onFailure { Log.e(logTag, "topology restoration failed", it) }
            releaseMirror()
            releasePhoneOwnedDisplay("temporary_android_return")
            clearOverlayDisplayRequestTwice("temporary_android_return")
            targetDisplayId = -1
            sessionJournal.restoreWallpaperDimensions()
            if (!autoSessionActive && !coverSessionActive) desktopModeConfigurator.restore()
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
            val detach = Runnable {
                pauseDisableSelfRunnable = null
                if (pausedForAndroid && !active && !stopping) {
                    disableSelf()
                } else {
                    Log.i(
                        logTag,
                        "skipped stale pause detach active=$active " +
                                "paused=$pausedForAndroid stopping=$stopping"
                    )
                }
            }
            pauseDisableSelfRunnable?.let(pauseLifecycleHandler::removeCallbacks)
            pauseDisableSelfRunnable = detach
            pauseLifecycleHandler.postDelayed(detach, 4_500L)
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
        saved.remove("cover")
        if ("cover" in controlIds) {
            saved.add((saved.indexOf("android") + 1).coerceAtLeast(0), "cover")
        }
        saved.remove("laptop")
        if ("laptop" in controlIds) {
            val insertAfter = saved.indexOf("cover").takeIf { it >= 0 }
                ?: saved.indexOf("android")
            saved.add((insertAfter + 1).coerceAtLeast(0), "laptop")
        }
        return saved
    }

    private val allControlIds
        get() = listOf(
            "modes", "volume", "brightness", "resolution", "android", "cover", "laptop",
            "stop", "reconnect", "orientation", "rotate_180", "cast"
        )
    private val controlIds
        get() = allControlIds.filter {
            when (it) {
                // Laptop mode is available on foldables and tablet-class displays
                // without requiring a hinge sensor, but is hidden on clearly
                // phone-sized devices where the two-pane surface is unusable.
                "laptop" -> demoMode || isLaptopCapableDevice() || isBlackBerryModeAvailable() ||
                        (laptopModeActive && (
                            keyboardDeckStyle == KeyboardDeckStyle.BLACKBERRY ||
                                    keyboardDeckStyle == KeyboardDeckStyle.GAMEPAD ||
                                    keyboardDeckStyle == KeyboardDeckStyle.GAMEBOY
                            ))
                // This is deliberately independent from the Fold8/Ultra laptop
                // profile. Any concrete folding signal qualifies the formal
                // cover-display session control on foldable devices.
                "cover" -> demoMode || (isFoldableDevice() &&
                        isCoverDisplayFeatureEnabled())
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
            "volume" -> controlCenter.sliderRow(NativeStrings.text("nativeVolume"), controlCenter.systemVolume(), 100) {
                controlCenter.setSystemVolume(it)
                if (demoMode) demoExplanation(NativeStrings.text("nativeAdjustTheVolumeOfPlaybackOnDextop"))
            }

            "brightness" -> controlCenter.sliderRow(NativeStrings.text("nativeScreenBrightness"), controlCenter.currentBrightness(), 100) {
                controlCenter.setOverlayBrightness(it)
                if (demoMode) demoExplanation(NativeStrings.text("nativeAdjustTheBrightnessOfTheDesktopDisplay"))
            }

            "resolution" -> menuWidgets.actionButton(
                R.drawable.ic_monitor,
                "${NativeStrings.text("nativeResolution")}   ${targetWidth} × ${targetHeight}"
            ) {
                if (demoMode) demoExplanation(NativeStrings.text("nativeSwitchDextopResolutionAndDpi"))
                resolutionMenu.showResolutionMenu(panel)
            }

            "android" -> menuWidgets.actionButton(
                R.drawable.ic_smartphone,
                NativeStrings.text("nativeTemporarilyReturnToAndroid")
            ) {
                if (demoMode) demoExplanation(NativeStrings.text("nativePauseDextopAndReturnYourAndroidTo"))
                else temporarilyReturnToAndroid()
            }

            "cover" -> menuWidgets.actionButton(
                R.drawable.ic_smartphone,
                NativeStrings.text("nativeCoverDisplay"),
            ) {
                if (demoMode) showCoverDisplayDemoMenu(panel)
                else showCoverDisplayMenu(panel)
            }

            "laptop" -> {
                val laptopAvailable = isLaptopStyleAvailable()
                val blackBerryAvailable = isBlackBerryStyleAvailable()
                val gamepadAvailable = gamepad.isGamepadStyleAvailable()
                val hasStyleChoice = hasKeyboardStyleChoice()
                val mainAction = menuWidgets.actionButton(
                    R.drawable.ic_keyboard,
                    NativeStrings.text(
                        if (hasStyleChoice) "nativeKeyboardStyle"
                        else if (blackBerryAvailable) "nativeBlackBerryMode"
                        else if (gamepadAvailable) "nativeVirtualGamepad"
                        else "nativeLaptopMode"
                    )
                ) {
                    when {
                        hasStyleChoice -> showKeyboardStyleMenu(panel)
                        blackBerryAvailable -> toggleKeyboardStyle(KeyboardDeckStyle.BLACKBERRY)
                        gamepadAvailable -> toggleKeyboardStyle(KeyboardDeckStyle.GAMEPAD)
                        else -> toggleKeyboardStyle(KeyboardDeckStyle.LAPTOP)
                    }
                }
                if (blackBerryAvailable && !hasStyleChoice) {
                    blackBerryActionRow(mainAction) { showBlackBerryLayoutEditor(panel) }
                } else {
                    mainAction
                }
            }

            else -> menuWidgets.squareControl(id)
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

    private fun showCoverDisplayMenu(panel: LinearLayout) {
        cast.stopCastRouteDiscovery()
        menuWidgets.animateMenuResize(panel)
        panel.removeAllViews()
        panel.addView(menuWidgets.menuTitle(
            NativeStrings.text("nativeCoverDisplay"),
            NativeStrings.text("nativeReturn"),
        ) { showMainMenu(panel) })

        val state = coverDisplayController.state()
        panel.addView(menuWidgets.actionButton(
            R.drawable.ic_smartphone,
            NativeStrings.text(
                if (state.androidVisible) "nativeStopCoverAndroid" else "nativeOpenCoverAndroid",
            ),
        ) {
            coverDisplayController.setAndroidVisible(!state.androidVisible) { result ->
                result.onFailure { showCoverDisplayFailure(it) }
                showCoverDisplayMenu(panel)
            }
        })
        panel.addView(menuWidgets.actionButton(
            R.drawable.ic_monitor,
            NativeStrings.text(
                if (state.desktopActive) "nativeStopCoverDextop" else "nativeStartCoverDextop",
            ),
        ) {
            if (state.desktopActive) {
                coverDisplayController.stopDesktop { result ->
                    result.onFailure { showCoverDisplayFailure(it) }
                    showCoverDisplayMenu(panel)
                }
            } else {
                coverDisplayController.startDesktop(
                    targetWidth,
                    targetHeight,
                    density,
                    secureDisplay,
                    ::coverDisplayLifecycleToken,
                ) { result ->
                    result.onSuccess {
                        activateTopologyForIndependentDisplays("cover_started")
                    }.onFailure { showCoverDisplayFailure(it) }
                    showCoverDisplayMenu(panel)
                }
            }
        })
        if (!gamepad.isGamepadDeckActive()) {
            panel.addView(menuWidgets.actionButton(
                R.drawable.ic_mouse,
                NativeStrings.text("nativeInputTrackpad") + if (state.trackpadActive) "  ✓" else "",
            ) {
                if (state.trackpadActive) {
                    coverDisplayController.stopTrackpadMode { result ->
                        result.onFailure { showCoverDisplayFailure(it) }
                        showCoverDisplayMenu(panel)
                    }
                } else {
                    coverDisplayController.startTrackpadMode(::coverDisplayLifecycleToken) { result ->
                        result.onSuccess { activateTopologyForIndependentDisplays("cover_trackpad_started") }
                            .onFailure { showCoverDisplayFailure(it) }
                        showCoverDisplayMenu(panel)
                    }
                }
            })
        }
        scheduleMenuHeightUpdate()
    }

    private fun isLaptopStyleAvailable(): Boolean = demoMode ||
            (isLaptopCapableDevice() && !isCurrentFoldableCoverDisplay())

    private fun isBlackBerryStyleAvailable(): Boolean = demoMode ||
            isBlackBerryModeAvailable() ||
            (laptopModeActive && keyboardDeckStyle == KeyboardDeckStyle.BLACKBERRY)

    private fun hasKeyboardStyleChoice(): Boolean {
        val availableStyleCount = listOf(
            isLaptopStyleAvailable(),
            isBlackBerryStyleAvailable(),
            gamepad.isGamepadStyleAvailable(),
        ).count { it }
        return availableStyleCount >= 2
    }

    private fun showKeyboardStyleMenu(panel: LinearLayout) {
        // A style chooser is valid only when both destinations are genuinely
        // available. In particular, returning from the BlackBerry height
        // editor on a phone must not expose Laptop mode through a route that
        // was absent from the root overlay.
        if (!hasKeyboardStyleChoice()) {
            showMainMenu(panel)
            return
        }
        cast.stopCastRouteDiscovery()
        menuWidgets.animateMenuResize(panel)
        panel.removeAllViews()
        panel.addView(menuWidgets.menuTitle(
            NativeStrings.text("nativeKeyboardStyle"),
            NativeStrings.text("nativeReturn"),
        ) { showMainMenu(panel) })
        if (isLaptopStyleAvailable()) {
            panel.addView(menuWidgets.actionButton(
                R.drawable.ic_keyboard,
                NativeStrings.text("nativeLaptopMode") +
                        if (laptopModeActive && keyboardDeckStyle == KeyboardDeckStyle.LAPTOP) "  ✓" else "",
            ) {
                if (demoMode) {
                    demoExplanation(NativeStrings.text("nativeLaptopModeDescription"))
                } else {
                    toggleKeyboardStyle(KeyboardDeckStyle.LAPTOP)
                    showMainMenu(panel)
                }
            })
        }
        if (isBlackBerryStyleAvailable()) {
            val blackBerryAction = menuWidgets.actionButton(
                R.drawable.ic_keyboard,
                NativeStrings.text("nativeBlackBerryMode") +
                        if (laptopModeActive && keyboardDeckStyle == KeyboardDeckStyle.BLACKBERRY) "  ✓" else "",
            ) {
                if (demoMode) {
                    demoExplanation(NativeStrings.text("nativeBlackBerryModeDescription"))
                } else {
                    toggleKeyboardStyle(KeyboardDeckStyle.BLACKBERRY)
                    showMainMenu(panel)
                }
            }
            panel.addView(blackBerryActionRow(blackBerryAction) {
                showBlackBerryLayoutEditor(panel)
            })
        }
        if (gamepad.isGamepadStyleAvailable()) {
            val gamepadAction = menuWidgets.actionButton(
                R.drawable.ic_gamepad,
                NativeStrings.text("nativeVirtualGamepad") +
                        if (laptopModeActive && (
                            keyboardDeckStyle == KeyboardDeckStyle.GAMEPAD ||
                                    keyboardDeckStyle == KeyboardDeckStyle.GAMEBOY
                            )) "  ✓" else "",
            ) {
                if (demoMode) {
                    demoExplanation(NativeStrings.text("nativeVirtualGamepadDescription"))
                } else {
                    toggleKeyboardStyle(KeyboardDeckStyle.GAMEPAD)
                    showMainMenu(panel)
                }
            }
            panel.addView(gamepad.gamepadActionRow(gamepadAction) {
                gamepad.showGamepadLayoutEditor(panel)
            })
        }
        scheduleMenuHeightUpdate()
    }

    private fun blackBerryActionRow(mainAction: View, edit: () -> Unit): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            mainAction.layoutParams = LinearLayout.LayoutParams(0, dp(50), 1f).apply {
                marginEnd = dp(8)
            }
            addView(mainAction)
            addView(menuWidgets.editButton(edit), LinearLayout.LayoutParams(dp(50), dp(50)))
        }.also {
            it.layoutParams = LinearLayout.LayoutParams(-1, dp(58)).apply { bottomMargin = dp(8) }
        }

    private fun showBlackBerryLayoutEditor(panel: LinearLayout) {
        if (!demoMode && (!laptopModeActive || keyboardDeckStyle != KeyboardDeckStyle.BLACKBERRY)) {
            // Opening the editor is also an explicit request to keep the
            // BlackBerry deck visible. Automatic fold-posture evaluation must
            // not replace or dismiss it while the user is editing the layout.
            laptopManualOverride = true
            laptopAutoActivated = false
            pendingLaptopMode = null
            pendingLaptopModeSince = 0L
            laptopModeEvaluationGeneration += 1
            setLaptopMode(true, KeyboardDeckStyle.BLACKBERRY)
        }
        cast.stopCastRouteDiscovery()
        menuWidgets.animateMenuResize(panel)
        panel.removeAllViews()
        panel.addView(menuWidgets.menuTitle(
            NativeStrings.text("nativeBlackBerryLayout"),
            NativeStrings.text("nativeReturn"),
        ) {
            if (hasKeyboardStyleChoice()) showKeyboardStyleMenu(panel)
            else showMainMenu(panel)
        })

        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val manual = prefs.getBoolean(KEY_BLACKBERRY_HEIGHT_MANUAL, false)
        panel.addView(menuWidgets.choiceButton(
            NativeStrings.text("nativeAutomaticLayout"),
            !manual,
        ) {
            prefs.edit().putBoolean(KEY_BLACKBERRY_HEIGHT_MANUAL, false).apply()
            applyBlackBerryDeckHeight()
            showBlackBerryLayoutEditor(panel)
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(8) })
        panel.addView(menuWidgets.choiceButton(
            NativeStrings.text("nativeManualHeight"),
            manual,
        ) {
            prefs.edit().putBoolean(KEY_BLACKBERRY_HEIGHT_MANUAL, true).apply()
            applyBlackBerryDeckHeight()
            showBlackBerryLayoutEditor(panel)
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(8) })

        if (manual) {
            val percent = prefs.getInt(KEY_BLACKBERRY_HEIGHT_PERCENT, 34).coerceIn(30, 50)
            val valueLabel = TextView(this).apply {
                text = "${NativeStrings.text("nativeKeyboardHeight")}  $percent%"
                textSize = 14f
                setTextColor(Color.rgb(230, 225, 229))
                setPadding(dp(4), dp(8), 0, dp(4))
            }
            panel.addView(valueLabel)
            panel.addView(SeekBar(this).apply {
                max = 20
                progress = percent - 30
                splitTrack = false
                progressTintList = android.content.res.ColorStateList.valueOf(Color.rgb(208, 188, 255))
                progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(73, 69, 79))
                thumbTintList = android.content.res.ColorStateList.valueOf(Color.rgb(208, 188, 255))
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(view: SeekBar?, value: Int, fromUser: Boolean) {
                        if (!fromUser) return
                        val next = value + 30
                        valueLabel.text = "${NativeStrings.text("nativeKeyboardHeight")}  $next%"
                        prefs.edit().putInt(KEY_BLACKBERRY_HEIGHT_PERCENT, next).apply()
                        applyBlackBerryDeckHeight(commitGeometry = false)
                    }
                    override fun onStartTrackingTouch(view: SeekBar?) = Unit
                    override fun onStopTrackingTouch(view: SeekBar?) {
                        applyBlackBerryDeckHeight(commitGeometry = true)
                    }
                })
            }, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(8) })
        }
        scheduleMenuHeightUpdate()
    }

    private fun applyBlackBerryDeckHeight(commitGeometry: Boolean = true) {
        if (!laptopModeActive || keyboardDeckStyle != KeyboardDeckStyle.BLACKBERRY) return
        val deck = laptopDeck ?: return
        val params = deck.layoutParams as? LinearLayout.LayoutParams ?: return
        params.weight = keyboardDeckWeight()
        deck.layoutParams = params
        cursorView?.contentHeightFraction = desktopPaneFraction()
        laptopContent?.requestLayout()
        if (commitGeometry) {
            laptopContent?.post { applyLaptopGeometryWhenLaidOut(true, preserveMenu = true) }
        }
    }

    private fun toggleKeyboardStyle(style: KeyboardDeckStyle) {
        if (demoMode) {
            demoExplanation(NativeStrings.text(
                if (style == KeyboardDeckStyle.LAPTOP) {
                    "nativeLaptopModeDescription"
                } else if (style == KeyboardDeckStyle.GAMEPAD ||
                    style == KeyboardDeckStyle.GAMEBOY
                ) {
                    "nativeVirtualGamepadDescription"
                } else {
                    "nativeBlackBerryModeDescription"
                }
            ))
            return
        }
        if (style == KeyboardDeckStyle.BLACKBERRY && !isBlackBerryModeAvailable() &&
            !(laptopModeActive && keyboardDeckStyle == KeyboardDeckStyle.BLACKBERRY)
        ) return
        if ((style == KeyboardDeckStyle.GAMEPAD || style == KeyboardDeckStyle.GAMEBOY) &&
            !gamepad.isGamepadStyleAvailable() &&
            !(laptopModeActive && (
                keyboardDeckStyle == KeyboardDeckStyle.GAMEPAD ||
                        keyboardDeckStyle == KeyboardDeckStyle.GAMEBOY
                ))
        ) return
        val disable = laptopModeActive && keyboardDeckStyle == style
        if (disable) {
            laptopAutoSuppressedByUser = style == KeyboardDeckStyle.LAPTOP
            laptopManualOverride = false
            pendingLaptopMode = null
            pendingLaptopModeSince = 0L
            laptopModeEvaluationGeneration += 1
            laptopAutoActivated = false
            setLaptopMode(false)
            OperationLog.i(this, "KeyboardStyle", "overlay disabled ${style.name.lowercase()}")
            return
        }
        laptopAutoSuppressedByUser = false
        // Laptop and BlackBerry are both manually persistent keyboard modes.
        // In particular, a subsequent fold-posture callback must not dismiss
        // or replace a BlackBerry deck that the user explicitly selected.
        laptopManualOverride = style == KeyboardDeckStyle.LAPTOP ||
                style == KeyboardDeckStyle.BLACKBERRY
        laptopAutoActivated = false
        // Invalidate a posture decision that may already be waiting for its
        // debounce timer when the user makes the manual selection.
        pendingLaptopMode = null
        pendingLaptopModeSince = 0L
        laptopModeEvaluationGeneration += 1
        setLaptopMode(true, style)
        OperationLog.i(this, "KeyboardStyle", "overlay enabled ${style.name.lowercase()}")
    }

    /**
     * The setup overlay must show the same destination menu as the live
     * cover-display control, without attempting a vendor device-state change.
     */
    private fun showCoverDisplayDemoMenu(panel: LinearLayout) {
        cast.stopCastRouteDiscovery()
        menuWidgets.animateMenuResize(panel)
        panel.removeAllViews()
        panel.addView(menuWidgets.menuTitle(
            NativeStrings.text("nativeCoverDisplay"),
            NativeStrings.text("nativeReturn"),
        ) { showMainMenu(panel) })
        panel.addView(menuWidgets.menuHint(NativeStrings.text("nativeCoverDisplayDescription")))
        panel.addView(menuWidgets.actionButton(
            R.drawable.ic_smartphone,
            NativeStrings.text("nativeOpenCoverAndroid"),
        ) {
            demoExplanation(NativeStrings.text("nativeCoverAndroidDescription"))
        })
        panel.addView(menuWidgets.actionButton(
            R.drawable.ic_monitor,
            NativeStrings.text("nativeStartCoverDextop"),
        ) {
            demoExplanation(NativeStrings.text("nativeCoverDextopDescription"))
        })
        if (gamepad.isExperimentalGamepadEnabled()) {
            panel.addView(menuWidgets.actionButton(
                R.drawable.ic_gamepad,
                NativeStrings.text("nativeStartBackButtons"),
            ) {
                demoExplanation(NativeStrings.text("nativeBackButtonsDescription"))
            })
        }
        scheduleMenuHeightUpdate()
    }

    private fun showCoverDisplayFailure(error: Throwable) {
        OperationLog.e(this, "CoverDisplay", "session update failed", error)
        Toast.makeText(
            this,
            error.message ?: NativeStrings.text("nativeCoverDisplayFailed"),
            Toast.LENGTH_LONG,
        ).show()
    }

    private fun activateTopologyForIndependentDisplays(reason: String) {
        runCatching {
            DisplayEnvironmentSettings(this).activateTopologyForOverlays(buildSet {
                if (mirrorDisplayId >= 0) add(mirrorDisplayId)
                addAll(AndroidAutoMirrorActivity.autoOverlayDisplayIds())
                addAll(coverDisplayController.ownedDisplayIds())
            })
        }.onFailure {
            OperationLog.w(this, "DisplayTopology", "topology activation skipped reason=$reason", it)
        }
    }

    private val menuWidgets: MenuWidgets = MenuWidgets(this, object : MenuWidgetsHost, DesktopSession by session, OverlayMenu by overlayMenu {
        override fun beginOverlayTextInput(field: EditText): Unit = this@MirrorService.beginOverlayTextInput(field)
        override val cast: CastController get() = this@MirrorService.cast
        override fun changeOrientation(): Unit = this@MirrorService.changeOrientation()
        override val requestedPortrait: Boolean get() = this@MirrorService.requestedPortrait
        override val secureDisplay: Boolean get() = this@MirrorService.secureDisplay
        override val showSystemDecorations: Boolean get() = this@MirrorService.showSystemDecorations
        override fun start(config: Config): Unit = this@MirrorService.start(config)
        override fun stop(): Unit = this@MirrorService.stop()
        override fun toggleDisplayRotation180(): Unit = this@MirrorService.toggleDisplayRotation180()
    })

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
            physicalInputRouter.apply(display, routePhysicalMice, routePhysicalKeyboardToDextop)
        }.onFailure { OperationLog.w(this, "InputRouting", "overlay routing change failed", it) }
        if (!routePhysicalMouseToDextop) activateTouchInput()
        root?.postDelayed({
            refreshActualRoutingState(display)
            menuPrimary?.let(::showMainMenu)
        }, 350)
    }

    // region external display output
    /**
     * Shows the Dextop desktop on a connected external screen, only when the user asks for it
     * from the app ([setExternalDisplay]). On phones whose external display only takes
     * presentations (HyperOS: no desktop windowing, so no app can open there) this is the way
     * to use it: a full-screen window on that display hosts the desktop's image.
     * - "external": that window becomes the desktop's host Surface and the desktop takes the
     *   screen's size; the phone keeps its own input mode.
     * - "mirror": the phone keeps showing the desktop and the external screen shows it too.
     * - "extend": one desktop spans both screens side by side (the external one on the side
     *   chosen in the app); each shows its part, and Plasma treats them as two outputs.
     * Unplugging the screen returns to the phone alone.
     */
    private var externalOutput: ExternalOutput? = null
    private val externalBackend by lazy {
        DisplayMirrorBackend(this, contentResolver, getSystemService(DisplayManager::class.java),
            privilegedAccess, desktopEnvironment)
    }

    internal inner class ExternalOutput(
        val mode: String,
        val displayId: Int,
        val windowManager: WindowManager,
        val frame: View,
        val surface: SurfaceView,
        val phoneSurface: SurfaceView,
        val phoneConfig: Config,
    ) : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) = attachExternalMirror()
        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = attachExternalMirror()
        override fun surfaceDestroyed(holder: SurfaceHolder) = externalBackend.releaseLayer()
    }

    /** The external screen that can take the desktop, if one is connected. */
    private fun externalOutputDisplay(): Display? = externalOutputDisplay(this)

    /** In "extend": the parts of the desktop the phone and the external screen show. */
    private var phoneRegion: Rect? = null
    private var externalRegion: Rect? = null
    private var externalCursor: CursorView? = null

    /** The part of the desktop (in its own pixels) that [view] shows. */
    private fun regionFor(view: View?): RectF {
        val output = externalOutput
        val region = when {
            output == null -> null
            view === output.surface -> externalRegion
            else -> phoneRegion
        }
        return region?.let(::RectF) ?: RectF(0f, 0f, targetWidth.toFloat(), targetHeight.toFloat())
    }

    /** Desktop logical density for a screen's real one (as for a host Surface). */
    private fun desktopDensityFor(screenDpi: Int) = (160 + screenDpi / 160f * 24).toInt().coerceIn(160, 320)

    /**
     * The desktop density for an external screen: the scale chosen in Book's settings, else from
     * the screen's physical size as a desktop OS does (96 dpi is 100 %, in 25 % steps), else from
     * Android's density for it. Android reports a high density for most monitors (213 for a
     * 31-inch 1440p one), which made every window too large.
     */
    private fun externalDensityFor(metrics: android.util.DisplayMetrics): Int {
        val chosen = moe.n4tsu.dextop.plasma.BookStore.displayScale(this)
        if (chosen > 0) return (160 * chosen / 100).coerceIn(120, 480)
        val physical = (metrics.xdpi + metrics.ydpi) / 2f
        if (physical in 50f..400f) {
            val scale = ((physical / 96f) * 4).roundToInt().coerceAtLeast(4) / 4f
            return (160 * scale).roundToInt()
        }
        return desktopDensityFor(metrics.densityDpi)
    }

    /** Applies a changed display scale to the desktop on the external screen. */
    private fun reapplyExternalScale() {
        val output = externalOutput?.takeIf { it.mode == "external" } ?: return
        val display = getSystemService(DisplayManager::class.java).getDisplay(output.displayId) ?: return
        val metrics = android.util.DisplayMetrics().also {
            @Suppress("DEPRECATION") display.getRealMetrics(it)
        }
        runCatching {
            resizeActiveDisplay(Config(metrics.widthPixels and -2, metrics.heightPixels and -2,
                externalDensityFor(metrics), secureDisplay, showSystemDecorations), "display scale")
        }.onFailure { OperationLog.e(this, "ExternalOutput", "display scale change failed", it) }
    }

    /** The user's choice from the app: "off", "external", "mirror" or "extend". */
    fun setExternalDisplay(mode: String): String {
        require(mode in setOf("off", "external", "mirror", "extend"))
        if (!active || autoOnlySession || targetDisplayId < 0 || surfaceView == null) return "no_session"
        // The next session (or the next time the screen is plugged in) starts the same way.
        getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE).edit()
            .putString("flutter.external_display_mode", mode).apply()
        if (mode == "extend" && externalOutput?.mode == "extend") {
            rearrangeExtended()
            return "ok"
        }
        if (mode == "external" && externalOutput?.mode == "external") {
            externalOutput?.let(::applyExternalPhoneRole)
            return "ok"
        }
        externalOutput?.let { hideExternalOutput("user") }
        if (mode == "off") return "ok"
        val display = externalOutputDisplay() ?: return "no_display"
        return if (showExternalOutput(display, mode)) "ok" else "failed"
    }

    private fun showExternalOutput(display: Display, mode: String): Boolean {
        val phone = surfaceView ?: return false
        return runCatching {
            val context = createDisplayContext(display)
            val manager = context.getSystemService(WindowManager::class.java)
            val surface = SurfaceView(context).apply {
                // Physical mice on the external screen act on the desktop like the phone's surface.
                setOnGenericMotionListener { _, event -> handlePhysicalMouseEvent(event, this) }
                setOnHoverListener { _, event -> handlePhysicalMouseEvent(event, this) }
            }
            val frame = FrameLayout(context).apply {
                setBackgroundColor(Color.BLACK)
                addView(surface, FrameLayout.LayoutParams(-1, -1))
            }
            val params = WindowManager.LayoutParams(
                -1, -1,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.OPAQUE,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                fitInsetsTypes = 0
                title = "Dextop External"
            }
            val output = ExternalOutput(mode, display.displayId, manager, frame, surface, phone,
                Config(targetWidth, targetHeight, density, secureDisplay, showSystemDecorations))
            externalOutput = output
            val metrics = android.util.DisplayMetrics().also {
                @Suppress("DEPRECATION") display.getRealMetrics(it)
            }
            if (mode == "external") {
                // The external window becomes the host Surface; the phone's stops driving the desktop.
                phone.holder.removeCallback(this)
                releaseMirror()
                surfaceView = surface
                surface.holder.addCallback(this)
            } else {
                surface.holder.addCallback(output)
            }
            if (mode == "extend") {
                arrangeExtended(output, metrics)
                externalCursor = CursorView(context).also { cursor ->
                    cursor.visibility = cursorView?.visibility ?: View.GONE
                    frame.addView(cursor, FrameLayout.LayoutParams(-1, -1))
                }
            }
            manager.addView(frame, params)
            updateCursorPosition()
            when (mode) {
                // The desktop takes the external screen's size and pixel density.
                "external" -> {
                    runCatching {
                        resizeActiveDisplay(Config(metrics.widthPixels and -2, metrics.heightPixels and -2,
                            externalDensityFor(metrics), secureDisplay, showSystemDecorations), "external display")
                    }.onFailure { OperationLog.e(this, "ExternalOutput", "external resize failed", it) }
                    applyExternalPhoneRole(output)
                }
                "extend" -> {
                    runCatching {
                        resizeActiveDisplay(extendedDesktopConfig(), "extended desktop")
                    }.onFailure { OperationLog.e(this, "ExternalOutput", "extended resize failed", it) }
                    // The phone shows only its part now.
                    releaseMirror()
                    surfaceView?.takeIf { it.width > 0 }?.let { reattachExistingDisplay(it.width, it.height) }
                    applyDesktopOutputs()
                }
            }
            OperationLog.i(this, "ExternalOutput", "desktop $mode on display=${display.displayId}")
        }.onFailure { error ->
            OperationLog.e(this, "ExternalOutput", "external display output failed", error)
            hideExternalOutput("failed")
        }.isSuccess
    }

    /** Mirror and extend: the external window shows the desktop, or its part of it. */
    private fun attachExternalMirror() {
        val output = externalOutput?.takeIf { it.mode == "mirror" || it.mode == "extend" } ?: return
        val surface = output.surface
        if (!surface.holder.surface.isValid || surface.width <= 0 || targetDisplayId < 0) return
        runCatching {
            externalBackend.attach(targetDisplayId, surface, surface.width, surface.height,
                targetWidth, targetHeight, density, configuredMirrorStrategyOverride(), rebuild = true,
                crop = if (output.mode == "extend") externalRegion else null)
        }.onFailure { OperationLog.e(this, "ExternalOutput", "external mirror failed", it) }
    }

    private fun externalDisplayPreference(key: String, fallback: String): String =
        getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE).getString("flutter.$key", fallback) ?: fallback

    /** A screen's size in the desktop at its "scale" percentage (100 = [base] as is). */
    private fun extendedScreenSize(base: Point, screen: Int): Point {
        val percent = (externalDisplayPreference("external_display_scale_$screen", "100").toIntOrNull() ?: 100)
            .coerceIn(EXTENDED_SCALES.first(), EXTENDED_SCALES.last())
        return Point((base.x * 100f / percent).roundToInt() and -2, (base.y * 100f / percent).roundToInt() and -2)
    }

    /**
     * Places each screen's part of the desktop, as arranged in the app: the external screen
     * touches one side of the phone's ("external_display_position": left/right/top/bottom),
     * shifted along it by "external_display_along" desktop pixels. Each part is as large as
     * its screen at its own scale, so both screens show the desktop at their own size; the
     * desktop is the box around both (any corner outside them shows nothing).
     */
    private fun arrangeExtended(output: ExternalOutput, metrics: android.util.DisplayMetrics) {
        val phone = extendedScreenSize(Point(output.phoneConfig.width, output.phoneConfig.height), 1)
        // At 100 % the external screen looks as it does when it shows the desktop alone.
        val externalDensity = externalDensityFor(metrics).toFloat()
        val external = extendedScreenSize(Point(
            (metrics.widthPixels * output.phoneConfig.density / externalDensity).roundToInt(),
            (metrics.heightPixels * output.phoneConfig.density / externalDensity).roundToInt()), 2)
        val side = externalDisplayPreference("external_display_position", "right")
        val vertical = side == "top" || side == "bottom"
        // At least a quarter of the shorter shared edge stays in contact, so the pointer can cross.
        val contact = if (vertical) minOf(phone.x, external.x) / 4 else minOf(phone.y, external.y) / 4
        val along = (externalDisplayPreference("external_display_along", "0").toIntOrNull() ?: 0).coerceIn(
            -(if (vertical) external.x else external.y) + contact,
            (if (vertical) phone.x else phone.y) - contact)
        val externalRect = when (side) {
            "left" -> Rect(-external.x, along, 0, along + external.y)
            "top" -> Rect(along, -external.y, along + external.x, 0)
            "bottom" -> Rect(along, phone.y, along + external.x, phone.y + external.y)
            else -> Rect(phone.x, along, phone.x + external.x, along + external.y)
        }
        val phoneRect = Rect(0, 0, phone.x, phone.y)
        val dx = -minOf(0, externalRect.left)
        val dy = -minOf(0, externalRect.top)
        phoneRect.offset(dx, dy)
        externalRect.offset(dx, dy)
        phoneRegion = phoneRect
        externalRegion = externalRect
    }

    /** The extended desktop: the box around both screens' parts. */
    private fun extendedDesktopConfig(): Config {
        val phone = phoneRegion ?: return Config(targetWidth, targetHeight, density, secureDisplay, showSystemDecorations)
        val external = externalRegion ?: phone
        val box = Rect(phone).apply { union(external) }
        return Config(box.right and -2, box.bottom and -2, externalOutput?.phoneConfig?.density ?: density,
            secureDisplay, showSystemDecorations)
    }

    /** The arrangement, a scale or the main display changed while extended. */
    private fun rearrangeExtended() {
        val output = externalOutput?.takeIf { it.mode == "extend" } ?: return
        val display = getSystemService(DisplayManager::class.java).getDisplay(output.displayId) ?: return
        val metrics = android.util.DisplayMetrics().also {
            @Suppress("DEPRECATION") display.getRealMetrics(it)
        }
        arrangeExtended(output, metrics)
        runCatching { resizeActiveDisplay(extendedDesktopConfig(), "extended desktop rearranged") }
            .onFailure { OperationLog.e(this, "ExternalOutput", "extended rearrange resize failed", it) }
        releaseMirror()
        surfaceView?.takeIf { it.width > 0 }?.let { reattachExistingDisplay(it.width, it.height) }
        attachExternalMirror()
        applyDesktopOutputs()
        updateCursorPosition()
        OperationLog.i(this, "ExternalOutput", "extended desktop rearranged phone=$phoneRegion external=$externalRegion")
    }

    /**
     * The extended desktop for the app's arrangement editor, in place of Android's topology
     * (which these phones do not apply to the pointer): one entry per screen, in desktop pixels.
     */
    fun extendedArrangement(): Map<String, Any>? {
        val output = externalOutput?.takeIf { it.mode == "extend" } ?: return null
        val phone = phoneRegion ?: return null
        val external = externalRegion ?: return null
        val manager = getSystemService(DisplayManager::class.java)
        val phoneMain = externalDisplayPreference("external_display_main", "2") == "1"
        fun screen(screen: Int, displayId: Int, region: Rect, primary: Boolean): Map<String, Any> {
            val display = manager.getDisplay(displayId)
            val metrics = android.util.DisplayMetrics().also {
                @Suppress("DEPRECATION") display?.getRealMetrics(it)
            }
            return linkedMapOf(
                "id" to displayId,
                "name" to (display?.name ?: moe.n4tsu.dextop.InlineStrings.format(moe.n4tsu.dextop.localText("uiDisplay0"), displayId)),
                "x" to region.left.toDouble(),
                "y" to region.top.toDouble(),
                "widthDp" to region.width().toDouble(),
                "heightDp" to region.height().toDouble(),
                "widthPx" to metrics.widthPixels,
                "heightPx" to metrics.heightPixels,
                "densityDpi" to metrics.densityDpi,
                "activeModeId" to (display?.mode?.modeId ?: -1),
                "refreshRate" to (display?.refreshRate?.toDouble() ?: 0.0),
                // The phone's own resolution belongs to Dextop's resolution settings.
                "supportedModes" to if (screen == 1) emptyList() else display?.supportedModes?.map { mode ->
                    linkedMapOf<String, Any>("id" to mode.modeId, "width" to mode.physicalWidth,
                        "height" to mode.physicalHeight, "refreshRate" to mode.refreshRate.toDouble())
                }.orEmpty(),
                "primary" to primary,
                "dextopOverlay" to false,
                "extendedScreen" to screen,
                "scalePercent" to (externalDisplayPreference("external_display_scale_$screen", "100").toIntOrNull() ?: 100),
                "scalePercents" to EXTENDED_SCALES,
            )
        }
        return linkedMapOf(
            "supported" to true,
            "extended" to true,
            "displays" to listOf(
                screen(1, Display.DEFAULT_DISPLAY, phone, phoneMain),
                screen(2, output.displayId, external, !phoneMain),
            ),
        )
    }

    /** Takes positions from the arrangement editor: where the external screen touches the phone's. */
    fun arrangeExtendedFromEditor(positions: Map<*, *>): Boolean {
        val output = externalOutput?.takeIf { it.mode == "extend" } ?: return false
        val phone = phoneRegion ?: return false
        val external = externalRegion ?: return false
        fun position(id: Int): PointF? = (positions["$id"] as? Map<*, *>)?.let {
            PointF((it["x"] as? Number)?.toFloat() ?: return null, (it["y"] as? Number)?.toFloat() ?: return null)
        }
        val p = position(Display.DEFAULT_DISPLAY) ?: return false
        val e = position(output.displayId) ?: return false
        val x = (e.x - p.x).roundToInt()
        val y = (e.y - p.y).roundToInt()
        val (side, along) = when {
            x >= phone.width() - 1 -> "right" to y
            x + external.width() <= 1 -> "left" to y
            y >= phone.height() - 1 -> "bottom" to x
            else -> "top" to x
        }
        getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE).edit()
            .putString("flutter.external_display_position", side)
            .putString("flutter.external_display_along", along.toString())
            .apply()
        rearrangeExtended()
        return true
    }

    /** A screen's scale (percent, 100 = recommended) on the extended desktop. */
    fun setExtendedScale(displayId: Int, percent: Int): Boolean {
        val output = externalOutput?.takeIf { it.mode == "extend" } ?: return false
        val screen = when (displayId) {
            Display.DEFAULT_DISPLAY -> 1
            output.displayId -> 2
            else -> return false
        }
        getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE).edit()
            .putString("flutter.external_display_scale_$screen",
                percent.coerceIn(EXTENDED_SCALES.first(), EXTENDED_SCALES.last()).toString())
            .apply()
        rearrangeExtended()
        return true
    }

    /** Tells Dextop's window manager where each screen is: the main one (its taskbar) first. */
    private fun applyDesktopOutputs() {
        val phoneMain = externalDisplayPreference("external_display_main", "2") == "1"
        val outputs = if (phoneMain) listOfNotNull(phoneRegion, externalRegion) else listOfNotNull(externalRegion, phoneRegion)
        plasmaShell?.setOutputs(outputs)
    }

    private fun hideExternalOutput(reason: String) {
        val output = externalOutput ?: return
        externalOutput = null
        output.surface.holder.removeCallback(output)
        externalBackend.releaseLayer()
        if (output.mode == "external") {
            output.surface.holder.removeCallback(this)
            releaseMirror()
        }
        runCatching { output.windowManager.removeView(output.frame) }
        externalCursor = null
        if (output.mode == "external") releaseExternalPhoneRole(output)
        if (output.mode == "external") {
            if (surfaceView === output.surface) surfaceView = output.phoneSurface
            runCatching { resizeActiveDisplay(output.phoneConfig, "external display removed") }
            val phone = output.phoneSurface
            phone.holder.addCallback(this)
            if (phone.holder.surface.isValid) surfaceCreated(phone.holder)
        }
        if (output.mode == "extend") {
            phoneRegion = null
            externalRegion = null
            releaseMirror()
            runCatching { resizeActiveDisplay(output.phoneConfig, "extended desktop ended") }
                .onFailure { OperationLog.e(this, "ExternalOutput", "extended desktop shrink failed", it) }
            plasmaShell?.setOutputs(emptyList())
            surfaceView?.takeIf { it.width > 0 }?.let { reattachExistingDisplay(it.width, it.height) }
            cursorX = cursorX.coerceIn(0f, targetWidth - 1f)
            cursorY = cursorY.coerceIn(0f, targetHeight - 1f)
            updateCursorPosition()
        }
        OperationLog.i(this, "ExternalOutput", "external output ${output.mode} ended reason=$reason")
    }
    // endregion

    /**
     * "External display only": the phone either stays a phone (Dextop's window on it shrinks
     * away, rotation and navigation return to Android) or becomes the desktop's trackpad and
     * keyboard (the laptop deck over the whole phone screen), as chosen in the app
     * ("external_display_phone": "phone" / "touchpad").
     */
    private var externalPhoneRole: String? = null
    /** The laptop deck is shown for the external screen, not as a foldable's laptop half. */
    private var externalInputDeck = false

    private fun applyExternalPhoneRole(output: ExternalOutput) {
        val role = externalDisplayPreference("external_display_phone", "phone")
        if (role == externalPhoneRole) return
        releaseExternalPhoneRole(output)
        externalPhoneRole = role
        output.phoneSurface.visibility = View.GONE
        if (role == "phone") {
            setPhoneFreeForExternalOutput(true)
        } else {
            externalInputDeck = true
            setLaptopMode(true, KeyboardDeckStyle.LAPTOP)
        }
        OperationLog.i(this, "ExternalOutput", "phone role while external only: $role")
    }

    private fun releaseExternalPhoneRole(output: ExternalOutput) {
        when (externalPhoneRole) {
            "phone" -> setPhoneFreeForExternalOutput(false)
            "touchpad" -> if (laptopModeActive) setLaptopMode(false)
        }
        externalInputDeck = false
        externalPhoneRole = null
        output.phoneSurface.visibility = View.VISIBLE
    }

    /** Shrinks Dextop's phone window to nothing and gives the phone back to Android, or undoes it. */
    private fun setPhoneFreeForExternalOutput(free: Boolean) {
        val frame = root ?: return
        val params = rootWindowParams ?: return
        val untouchable = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        if (free) {
            params.width = 1
            params.height = 1
            params.flags = (params.flags or untouchable) and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON.inv()
        } else {
            params.width = -1
            params.height = -1
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            if (getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
                    .getBoolean("flutter.keep_awake_during_session", true)
            ) params.flags = params.flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        }
        runCatching { windowManager?.updateViewLayout(frame, params) }
            .onFailure { OperationLog.w(this, "ExternalOutput", "phone window update failed free=$free", it) }
        phoneGuard.setPhoneNavigationDisabled(!free)
        if (free) {
            phoneGuard.releasePhoneRotation()
            MainActivity.restoreOrientation()
        } else {
            applyHostDisplayOrientation(requestedPortrait)
            phoneGuard.forcePhoneRotation(requestedPortrait, force = true)
        }
    }

    /** The external screen last chosen in the app, tried once per connected screen. */
    private var externalAutoAttemptedFor = -1

    /** Shows the desktop on a connected external screen as last chosen ("extend", "mirror", "external"). */
    private fun restoreExternalDisplayMode(reason: String) {
        if (!active || autoOnlySession || externalOutput != null || targetDisplayId < 0 || surfaceView == null) return
        val display = externalOutputDisplay() ?: run { externalAutoAttemptedFor = -1; return }
        if (display.displayId == externalAutoAttemptedFor) return
        val mode = externalDisplayPreference("external_display_mode", "off")
        if (mode !in setOf("extend", "mirror", "external")) return
        externalAutoAttemptedFor = display.displayId
        val shown = showExternalOutput(display, mode)
        revealPhoneWindow()
        OperationLog.i(this, "ExternalOutput", "restored $mode on display=${display.displayId} shown=$shown reason=$reason")
    }

    /** The phone window was hidden while the session started for an external screen only. */
    private var externalStartPending = false

    private fun revealPhoneWindow() {
        if (!externalStartPending) return
        externalStartPending = false
        val frame = root ?: return
        val params = rootWindowParams ?: return
        params.alpha = 1f
        // The phone role keeps the window untouchable (see setPhoneFreeForExternalOutput).
        if (externalPhoneRole != "phone") params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        runCatching { windowManager?.updateViewLayout(frame, params) }
    }

    private fun refreshExternalDisplayState() {
        root?.post {
            externalOutput?.let { output ->
                if (getSystemService(DisplayManager::class.java).getDisplay(output.displayId) == null) hideExternalOutput("unplugged")
            }
            if (externalOutput == null && active) {
                if (externalOutputDisplay() == null) externalAutoAttemptedFor = -1
                else root?.postDelayed({ restoreExternalDisplayMode("display_connected") }, 800)
            }
            val externalState = externalDisplayDetector.snapshot()
            knownPhysicalExternalDisplayIds = externalState.displayIds.toSet()
            val connected = physicalInputRoutingSupported && externalState.connected
            if (connected == physicalExternalDisplayConnected) return@post
            physicalExternalDisplayConnected = connected
            val display = getSystemService(DisplayManager::class.java).getDisplay(targetDisplayId)
            if (connected && active && display != null) {
                runCatching {
                    physicalInputRouter.apply(display, routePhysicalMice, routePhysicalKeyboardToDextop)
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
                // Unplugging must not hand the mice back to the focused window display.
                if (active && display != null && routePhysicalMice) refreshPhysicalInputState()
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

    private val resolutionMenu: ResolutionMenu = ResolutionMenu(this, object : ResolutionMenuHost, DesktopSession by session, OverlayMenu by overlayMenu {
        override fun editButton(action: () -> Unit): ImageButton = this@MirrorService.menuWidgets.editButton(action)
        override fun effectiveConfig(config: Config): Config = this@MirrorService.effectiveConfig(config)
        override fun numberField(value: String, label: String): EditText = this@MirrorService.menuWidgets.numberField(value, label)
        override fun resizeActiveDisplay(next: Config, reason: String) = this@MirrorService.resizeActiveDisplay(next, reason)
        override fun resolutionProfiles(): List<ResolutionProfile> = this@MirrorService.resolutionProfiles()
        override val resolutionRepository: ResolutionRepository get() = this@MirrorService.resolutionRepository
        override val secureDisplay: Boolean get() = this@MirrorService.secureDisplay
        override val showSystemDecorations: Boolean get() = this@MirrorService.showSystemDecorations
    })

    private fun rotate180PreferenceKey(portrait: Boolean): String =
        if (portrait) KEY_ROTATE_180_PORTRAIT else KEY_ROTATE_180_LANDSCAPE

    private fun applyHostDisplayOrientation(portrait: Boolean) {
        val reverse = getSharedPreferences(PREFS, MODE_PRIVATE)
            .getBoolean(rotate180PreferenceKey(portrait), false)
        MainActivity.setDisplayOrientation(portrait, reverse)
    }

    private fun displayRotationFor(@Suppress("UNUSED_PARAMETER") portrait: Boolean = requestedPortrait): Int = 0

    private fun updateCursorPosition(x: Float = cursorX, y: Float = cursorY) {
        // Each screen draws the cursor while it is over its part of the desktop.
        fun place(cursor: CursorView?, region: RectF) {
            cursor ?: return
            val nx = (x - region.left) / region.width().coerceAtLeast(1f)
            val ny = (y - region.top) / region.height().coerceAtLeast(1f)
            cursor.alpha = if (nx in 0f..1f && ny in 0f..1f) 1f else 0f
            cursor.update(nx.coerceIn(0f, 1f), ny.coerceIn(0f, 1f))
        }
        // External only: the phone shows no desktop, and the system pointer is drawn on the external screen.
        val output = externalOutput
        if (output?.mode == "external") cursorView?.alpha = 0f
        else place(cursorView, regionFor(output?.phoneSurface ?: surfaceView))
        output?.let { place(externalCursor, regionFor(it.surface)) }
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
        phoneGuard.forcePhoneRotation(portrait, force = true)
        OperationLog.i(
            this,
            "Orientation",
            "180-degree rotation changed portrait=$portrait enabled=$enabled display=$targetDisplayId"
        )
    }

    private fun resolutionProfiles(): List<ResolutionProfile> =
        resolutionRepository.profiles(density)

    private fun trackpad(
        event: MotionEvent,
        sourceView: View,
        forceCursorMode: Boolean = false,
        allowVirtualPointer: Boolean = false,
        hapticView: View? = null
    ): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            imeDirectTouchHeld = !forceCursorMode && !directTouch &&
                    sourceView === surfaceView &&
                    imeRegion.imeTouchRegionContainsSurfacePoint(event.x, event.y)
            if (imeDirectTouchHeld) {
                Log.i(logTag, "IME direct-touch stream latched at ${event.x}:${event.y} region=${imeRegion.imeTouchRegion?.bounds}")
            }
        }
        val imeDirectTouch = !forceCursorMode && !directTouch &&
                sourceView === surfaceView && imeDirectTouchHeld
        val useDirectTouch = (directTouch && !forceCursorMode) || imeDirectTouch
        val useVirtualMouse = (if (allowVirtualPointer) {
            dedicatedTrackpadInputActive()
        } else {
            virtualPointer.virtualMouseInputActive()
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
                virtualPointer.virtualPointerRegisteredProfile == "touchpad"
        if (experimentalMultiTouch && !nativeTouchpadAvailable && !imeDirectTouch &&
            handleExperimentalEdgeGesture(event)
        ) {
            if (nativeTouchpadGestureActive || virtualTouchpad.virtualTouchpadActiveContactCount() > 0) {
                virtualTouchpad.finishVirtualTouchpadGesture(
                    "dextop_edge_gesture_intercept",
                    allowVirtualPointer
                )
            }
            return true
        }
        if (useDirectTouch && experimentalMultiTouch) {
            injectDirectTouch(event)
            if (imeDirectTouch && (event.actionMasked == MotionEvent.ACTION_UP ||
                    event.actionMasked == MotionEvent.ACTION_CANCEL)
            ) {
                imeDirectTouchHeld = false
            }
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
                longPressRunnable?.let(inputGestureHandler::removeCallbacks)
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
                virtualTouchpad.finishVirtualTouchpadGesture("dextop_three_finger_intercept", allowVirtualPointer)
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
            virtualTouchpad.virtualTouchpadMotionEvent(event, sourceView, allowVirtualPointer)
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
                longPressRunnable?.let(inputGestureHandler::removeCallbacks)
                longPressRunnable = Runnable {
                    if (!moved && !twoFinger && maxPointers == 1) {
                        longPressTriggered = true
                        haptics.performLaptopHaptic(hapticView, strong = true, source = HapticSource.POINTER)
                        performLongPressGesture(allowVirtualPointer)
                    }
                }.also { inputGestureHandler.postDelayed(it, 450) }
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (directTouchHeld) {
                    injectTouch(MotionEvent.ACTION_UP, cursorX, cursorY)
                    directTouchHeld = false
                }
                longPressRunnable?.let(inputGestureHandler::removeCallbacks)
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
                            virtualPointer.virtualMouseHorizontalScroll(dx, allowVirtualPointer)
                            virtualPointer.virtualMouseScroll(dy, allowVirtualPointer)
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
                        if (!longPressTriggered) longPressRunnable?.let(inputGestureHandler::removeCallbacks)
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
                longPressRunnable?.let(inputGestureHandler::removeCallbacks)
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
                        virtualPointer.virtualMouseButton("BTN_LEFT", false, allowVirtualPointer)
                    } else if (!(allowVirtualPointer && carCompanion.carCompanionPhoneTrackpadSurface === sourceView)) {
                        injectTouch(MotionEvent.ACTION_UP, cursorX, cursorY)
                    }
                    dragHeld = false
                } else if (twoFinger && !moved && !scrolling) {
                    haptics.performLaptopHaptic(hapticView, strong = true, source = HapticSource.POINTER)
                    performTwoFingerGesture(allowVirtualPointer)
                } else if (!twoFinger && !moved && !dragHeld && SystemClock.uptimeMillis() - downTime < 250) {
                    if (useDirectTouch) moveCursorToTouch(event.x, event.y)
                    haptics.performLaptopHaptic(hapticView, source = HapticSource.POINTER)
                    leftClick(allowVirtualPointer)
                }
                maxPointers = 0
                twoFingerTravelX = 0f
                twoFingerTravelY = 0f
                twoFinger = false
                threeFinger = false
                scrolling = false
                imeDirectTouchHeld = false
            }

            MotionEvent.ACTION_CANCEL -> {
                longPressRunnable?.let(inputGestureHandler::removeCallbacks)
                if (directTouchHeld) injectTouch(MotionEvent.ACTION_UP, cursorX, cursorY)
                directTouchHeld = false
                if (scrolling && !useVirtualMouse) injectTouch(MotionEvent.ACTION_UP, scrollX, scrollY)
                if (dragHeld) {
                    if (useVirtualMouse) {
                        virtualPointer.virtualMouseButton("BTN_LEFT", false, allowVirtualPointer)
                    } else if (!(allowVirtualPointer && carCompanion.carCompanionPhoneTrackpadSurface === sourceView)) {
                        injectTouch(MotionEvent.ACTION_UP, cursorX, cursorY)
                    }
                }
                dragHeld = false
                maxPointers = 0
                twoFingerTravelX = 0f
                twoFingerTravelY = 0f
                twoFinger = false
                threeFinger = false
                scrolling = false
                imeDirectTouchHeld = false
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
    private val haptics: Haptics = Haptics(this, object : HapticsHost, DesktopSession by session, OverlayMenu by overlayMenu {
    })


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
                edgeGesturePrimaryPointerId = event.getPointerId(0)
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
                // Preserve the first contact's edge position. Resetting the
                // origin when the third finger landed discarded the travel
                // made while placing the other contacts, so Tap mode often
                // required an extra swipe before opening the menu.
                edgeGestureLeadX = touchStartX
                edgeGestureLeadY = touchStartY
                if (directTouch) cancelInjectedDirectTouch()
                maybeTriggerEdgeMenu(event)
                return true
            }

            MotionEvent.ACTION_MOVE -> if (threeFingerEdgeSwipe) {
                // Keep consuming the intercepted stream, but never complete a
                // three-finger gesture after one of the fingers has lifted.
                if (event.pointerCount < 3) return true
                maybeTriggerEdgeMenu(event)
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> if (threeFingerEdgeSwipe) return true
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (threeFingerEdgeSwipe) {
                threeFingerEdgeSwipe = false
                edgeMenuTriggered = false
                edgeGesturePrimaryPointerId = MotionEvent.INVALID_POINTER_ID
                edgeGestureLeadX = 0f
                edgeGestureLeadY = 0f
                return true
            }
        }
        return false
    }

    private fun maybeTriggerEdgeMenu(event: MotionEvent) {
        val primaryIndex = event.findPointerIndex(edgeGesturePrimaryPointerId)
        if (primaryIndex < 0) return
        val portrait = targetHeight > targetWidth
        val distance = if (portrait) {
            event.getY(primaryIndex) - edgeGestureLeadY
        } else {
            event.getX(primaryIndex) - edgeGestureLeadX
        }
        val triggerDistance = if (portrait) dp(20) else dp(28)
        if (!edgeMenuTriggered && distance >= triggerDistance) {
            edgeMenuTriggered = true
            toggleMenu()
        }
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
                if (!virtualPointer.virtualMouseButton("BTN_LEFT", true, allowVirtualPointer)) {
                    if (allowVirtualPointer && carCompanion.carCompanionPhoneTrackpadSurface != null) {
                        dragHeld = false
                        OperationLog.w(
                            this,
                            "CarCompanion",
                            "uinput drag start rejected; touch fallback suppressed",
                            null,
                        )
                    } else {
                        injectTouch(MotionEvent.ACTION_DOWN, cursorX, cursorY)
                    }
                }
                cursorView?.pulse()
            }
        }
    }

    private fun moveCursorToTouch(x: Float, y: Float) {
        val view = surfaceView ?: return
        if (view.width <= 0 || view.height <= 0) return
        val region = regionFor(view)
        cursorX = region.left + x / view.width * region.width()
        cursorY = region.top + y / view.height * region.height()
        if (!directTouch && !virtualPointer.virtualMouseInputActive()) {
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
            virtualPointer.stopVirtualMouse()
            updateVirtualCursorVisibility()
        } else {
            virtualPointer.startVirtualMouse()
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
            if (laptopModeActive && laptopKeyboardRequested) {
                virtualPointer.suspendVirtualMouseForLaptopKeyboard()
            } else {
                virtualPointer.stopVirtualMouse()
            }
            updateVirtualCursorVisibility()
            OperationLog.i(
                this,
                "InputRouting",
                "phone touch surface claimed input; virtual pointer disconnected"
            )
        } else {
            // Reconnect lazily if the device was removed while the session
            // was in tap mode or during a display/posture transition.
            virtualPointer.startVirtualMouse()
        }
        // ACTION_DOWN on the phone display hands cursor ownership back to the
        // touchpad immediately, even if the mouse remains connected.
        updateVirtualCursorVisibility()
        if (!directTouch && !virtualPointer.virtualMouseInputActive()) updateCursorPosition()
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
        virtualPointer.startVirtualMouse()
        updateVirtualCursorVisibility()
        OperationLog.i(
            this,
            "InputRouting",
            "laptop trackpad claimed input directTouch=$directTouch " +
                    "pointerProfile=${virtualPointer.activeVirtualPointerProfile()} " +
                    "pointerReady=${laptopTrackpadInputActive()}"
        )
        if (!directTouch && !virtualPointer.virtualMouseInputActive()) {
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
            val shell = plasmaShell
            val capture = if (shell is moe.n4tsu.dextop.plasma.CompositedWindowShell) shell.hostPointerCaptureRequested()
                else moe.n4tsu.dextop.plasma.WindowManagerSelection.current(this) == moe.n4tsu.dextop.plasma.WindowManagerSelection.SYSTEM
            surfaceView?.let { surface ->
                if (capture && !overlayTextInputActive) {
                    surface.requestFocus()
                    if (!surface.hasPointerCapture()) surface.requestPointerCapture()
                } else if (surface.hasPointerCapture()) surface.releasePointerCapture()
            }
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
        if (virtualPointer.isVirtualMouseEvent(event)) return true
        if (!event.isFromSource(InputDevice.SOURCE_MOUSE)) return false
        if (event.actionMasked == MotionEvent.ACTION_MOVE ||
            event.actionMasked == MotionEvent.ACTION_HOVER_MOVE
        ) activatePhysicalMouse()
        return forwardMouseEvent(event, view)
    }

    private fun handleCapturedMouseEvent(event: MotionEvent): Boolean {
        if (virtualPointer.isVirtualMouseEvent(event)) return true
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
        if (!physicalInputRoutingSupported || !(physicalExternalDisplayConnected || routePhysicalMice)) return
        val id = targetDisplayId
        if (!active || id < 0) return
        val display = getSystemService(DisplayManager::class.java).getDisplay(id) ?: return
        root?.post {
            val routed = runCatching {
                physicalInputRouter.refresh(display, routePhysicalMice, routePhysicalKeyboardToDextop)
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
        suspendedKeyboardDeckStyle = keyboardDeckStyle.takeIf { laptopModeActive }
        suspendedForLockScreen = true
        stopHostDisplayMonitor()
        phoneGuard.setPhoneNavigationDisabled(false)
        phoneGuard.releasePhoneRotation()
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
        // Never turn an Auto-only session into a phone desktop.
        if (autoOnlySession) { suspendedForLockScreen = false; return }
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
            val styleToRestore = suspendedKeyboardDeckStyle
            start(config)
            if (styleToRestore != null) restoreKeyboardDeckAfterUnlock(styleToRestore)
        }, 250)
        Log.i(
            logTag,
            "unlock confirmed; session recreation scheduled reason=$reason " +
                    "screenGeneration=$generation"
        )
    }

    private fun restoreKeyboardDeckAfterUnlock(style: KeyboardDeckStyle, attempt: Int = 0) {
        Handler(mainLooper).postDelayed({
            if (!active || root == null || laptopContent == null) {
                if (attempt < 30) restoreKeyboardDeckAfterUnlock(style, attempt + 1)
                return@postDelayed
            }
            suspendedKeyboardDeckStyle = null
            setLaptopMode(true, style)
            OperationLog.i(
                this,
                "LaptopMode",
                "restored ${style.name.lowercase()} deck after unlock attempt=$attempt",
            )
        }, if (attempt == 0) 280L else 100L)
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
            updateVirtualCursorVisibility()
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
            frame.routeTouchesToSurface = !laptopModeActive
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
                frame.routeTouchesToSurface = !laptopModeActive
                panel.translationX = 0f
                panel.translationY = 0f
                panel.alpha = 1f
                panel.scaleX = 1f
                panel.scaleY = 1f
                surfaceView?.requestFocus()
                updateVirtualCursorVisibility()
            }.start()
        }
    }

    private fun moveCursor(
        dx: Float,
        dy: Float,
        allowVirtualPointer: Boolean = false
    ) {
        val useVirtualPointer = virtualPointer.virtualPointerInputActive(allowVirtualPointer)
        // Pointer sensitivity is deliberately fixed.  Older releases exposed
        // DPI and acceleration preferences, but those values could persist in
        // SharedPreferences and make input unusable.  Always use raw deltas.
        val effectiveDx = dx
        val effectiveDy = dy
        cursorX = (cursorX + effectiveDx).coerceIn(0f, targetWidth - 1f)
        cursorY = (cursorY + effectiveDy).coerceIn(0f, targetHeight - 1f)
        if (useVirtualPointer) {
            virtualPointer.virtualMouseMove(effectiveDx, effectiveDy, allowVirtualPointer)
        } else if (allowVirtualPointer && carCompanion.carCompanionPhoneTrackpadSurface != null) {
            // This surface owns a display-bound uinput mouse. Falling back to
            // absolute touch while BTN_LEFT is held can route the stream to
            // display 0 and make an Auto window jump between drag targets.
            Unit
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
        if (virtualPointer.virtualPointerInputActive(allowVirtualPointer)) {
            virtualPointer.virtualMouseButton("BTN_LEFT", true, allowVirtualPointer)
            virtualPointer.virtualMouseButton("BTN_LEFT", false, allowVirtualPointer)
        } else {
            injectTouch(MotionEvent.ACTION_DOWN, cursorX, cursorY)
            injectTouch(MotionEvent.ACTION_UP, cursorX, cursorY)
            if (!directTouch) cursorView?.pulse()
        }
    }

    private fun rightClick(allowVirtualPointer: Boolean = false) {
        if (dragHeld || targetDisplayId < 0) return
        if (virtualPointer.virtualPointerInputActive(allowVirtualPointer)) {
            virtualPointer.virtualMouseButton("BTN_RIGHT", true, allowVirtualPointer)
            virtualPointer.virtualMouseButton("BTN_RIGHT", false, allowVirtualPointer)
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
        if (virtualPointer.virtualMouseInputActive()) {
            virtualPointer.virtualMouseButton("BTN_LEFT", true)
            root?.postDelayed({ virtualPointer.virtualMouseButton("BTN_LEFT", false) }, 600)
        } else {
            injectTouch(MotionEvent.ACTION_DOWN, cursorX, cursorY)
            root?.postDelayed({ injectTouch(MotionEvent.ACTION_UP, cursorX, cursorY) }, 600)
        }
    }

    private fun toggleDrag() {
        dragHeld = !dragHeld
        if (virtualPointer.virtualMouseInputActive()) {
            virtualPointer.virtualMouseButton("BTN_LEFT", dragHeld)
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
        phoneGuard.forcePhoneRotation(portrait)
        val config = Config(base.height, base.width, base.density, base.secure, base.decorations)
        // Keep the existing virtual display and its task stack. Re-entering
        // start() here used to remove the overlay display and create another
        // one, so Android could discard the open windows and their placement.
        // A laptop deck needs the same live resize, but its logical desktop is
        // the measured upper pane rather than the full-screen profile.
        if (laptopModeActive) laptopBaseConfig = config
        root?.postDelayed({
            runCatching {
                if (!active || targetDisplayId < 0) {
                    error(moe.n4tsu.dextop.localText("uiTheActiveDisplayIsUnavailableForOrientationChange"))
                }
                if (laptopModeActive) {
                    applyLaptopGeometryWhenLaidOut(
                        enabled = true,
                        baseOverride = config,
                        preserveMenu = true,
                    )
                } else {
                    resizeActiveDisplay(config, "manual orientation change")
                }
            }
                .onSuccess {
                    OperationLog.i(
                        this,
                        "Orientation",
                        "live resize requested portrait=$portrait target=${config.width}x${config.height} " +
                                displayGeometrySnapshot("orientation_live_resize_requested")
                    )
                }
                .onFailure {
                    OperationLog.e(this, "Orientation", "live resize failed portrait=$portrait", it)
                }
        }, 350)
    }

    private fun injectKey(keyCode: Int, metaState: Int = 0) {
        if (!injectLaptopKeyboardEvent(keyCode, KeyEvent.ACTION_DOWN, metaState, 0)) return
        injectLaptopKeyboardEvent(keyCode, KeyEvent.ACTION_UP, metaState, 0)
    }

    private val laptopFallbackKeyDownTimes = ConcurrentHashMap<Int, Long>()

    private fun injectLaptopKeyboardEvent(
        keyCode: Int,
        action: Int,
        metaState: Int,
        repeatCount: Int
    ): Boolean {
        if (desktopEnvironment.directDeckKeyboardToApp &&
            plasmaShell is moe.n4tsu.dextop.plasma.CompositedWindowShell) {
            return injectLaptopKeyboardFallback(keyCode, action, metaState, repeatCount)
        }
        // Once a key-down used the targeted path, keep repeats and key-up on
        // that same virtual device even if uinput becomes ready mid-press.
        if (laptopFallbackKeyDownTimes.containsKey(keyCode)) {
            return injectLaptopKeyboardFallback(keyCode, action, metaState, repeatCount)
        }
        if (!laptopKeyboardReady) {
            Log.w(
                logTag,
                "native laptop keyboard unavailable; using targeted fallback " +
                        "action=$action repeat=$repeatCount deviceId=$laptopKeyboardDeviceId"
            )
            scheduleLaptopKeyboardReadyCheck(laptopKeyboardGeneration, 0)
            return injectLaptopKeyboardFallback(keyCode, action, metaState, repeatCount)
        }
        val injected = privilegedInputClient.injectKeyboard(keyCode, action, metaState, repeatCount)
        if (!injected) {
            Log.w(
                logTag,
                "native laptop keyboard injection failed; using targeted fallback " +
                        "action=$action repeat=$repeatCount deviceId=$laptopKeyboardDeviceId"
            )
            return injectLaptopKeyboardFallback(keyCode, action, metaState, repeatCount)
        }
        return true
    }

    /**
     * Keeps the on-screen keyboard usable while a foldable panel hand-off is
     * republishing or re-associating the uinput keyboard. The event is sent
     * directly to Dextop's display instead of depending on global focus.
     */
    private fun injectLaptopKeyboardFallback(
        keyCode: Int,
        action: Int,
        metaState: Int,
        repeatCount: Int,
    ): Boolean {
        val displayId = if (desktopEnvironment.directDeckKeyboardToApp) {
            (plasmaShell as? moe.n4tsu.dextop.plasma.CompositedWindowShell)?.hostKeyboardDisplayId() ?: targetDisplayId
        } else targetDisplayId
        if (displayId < 0) return false
        val eventTime = SystemClock.uptimeMillis()
        val downTime = when (action) {
            KeyEvent.ACTION_DOWN -> laptopFallbackKeyDownTimes.computeIfAbsent(keyCode) { eventTime }
            KeyEvent.ACTION_UP -> laptopFallbackKeyDownTimes.remove(keyCode) ?: eventTime
            else -> eventTime
        }
        val event = KeyEvent(
            downTime,
            eventTime,
            action,
            keyCode,
            repeatCount,
            metaState,
            KeyCharacterMap.VIRTUAL_KEYBOARD,
            0,
            0,
            InputDevice.SOURCE_KEYBOARD,
        )
        if (desktopEnvironment.directDeckKeyboardToApp) {
            // WAIT_FOR_RESULT on the UI thread can wait for this service's own
            // accessibility key callback. Keep DOWN/repeats/UP ordered off-thread.
            return runCatching {
                laptopKeyExecutor.execute {
                    if (!inputDispatcher.send(event, displayId, waitForResult = false)) {
                        Log.e(logTag, "deck key submission failed display=$displayId action=$action")
                    }
                }
                true
            }.getOrDefault(false)
        }
        val accepted = inputDispatcher.send(event, displayId)
        if (!accepted) {
            Log.e(
                logTag,
                "targeted laptop keyboard fallback rejected display=$displayId " +
                        "keyCode=$keyCode action=$action repeat=$repeatCount"
            )
        }
        return accepted
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
            val region = regionFor(view)
            event.transform(Matrix().apply {
                setScale(region.width() / view.width, region.height() / view.height)
                postTranslate(region.left, region.top)
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

            val region = regionFor(view)
            val scaleX = region.width() / view.width
            val scaleY = region.height() / view.height
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
                        x = region.left + x * scaleX
                        y = region.top + y * scaleY
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
                    moe.n4tsu.dextop.InlineStrings.format(moe.n4tsu.dextop.localText("uiInputManagerRejectedMultiTouchEventAction0"), event.actionMasked)
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
        longPressRunnable?.let(inputGestureHandler::removeCallbacks)
        longPressRunnable = null

        if (virtualTouchpad.virtualTouchpadActiveContactCount() > 0) {
            virtualTouchpad.finishVirtualTouchpadGesture("desktop_touch_stream_cancelled", allowDirectTouch = true)
        }
        if (injectedDirectTouchActive) cancelInjectedDirectTouch()
        if (targetDisplayId >= 0 && (directTouchHeld || scrolling || dragHeld) &&
            !virtualPointer.virtualMouseInputActive()
        ) {
            injectTouch(MotionEvent.ACTION_CANCEL, cursorX, cursorY)
        }
        if (virtualPointer.virtualMouseInputActive() && dragHeld) virtualPointer.virtualMouseButton("BTN_LEFT", false)
        injectedDirectTouchActive = false
        directTouchHeld = false
        imeDirectTouchHeld = false
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
        edgeGesturePrimaryPointerId = MotionEvent.INVALID_POINTER_ID
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
            virtualPointer.startVirtualMouse()
            // releaseMirror() stopped the physical mouse reader.
            if (physicalExternalDisplayConnected && routePhysicalMouseToDextop) startRawMouseReader()
            imeRegion.requestImeRegionProbe("surface_reattached", force = true)
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
        if (useDirectPlasmaDisplay()) {
            createDirectPhoneDisplay(surface, width, height)
            return
        }
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

    private fun useDirectPlasmaDisplay(): Boolean =
        desktopEnvironment.directPlasmaDisplay && !autoOnlySession && !laptopModeActive &&
            moe.n4tsu.dextop.plasma.WindowManagerSelection.current(this) in setOf(
                moe.n4tsu.dextop.plasma.WindowManagerSelection.DEXTOP_PLASMA_V2,
                moe.n4tsu.dextop.plasma.WindowManagerSelection.DEXTOP_BOOK)

    /**
     * Creates the phone desktop as a public VirtualDisplay that renders
     * straight into the host Surface (no overlay display, no recording
     * mirror). Everything after creation is shared with the overlay path.
     */
    private fun createDirectPhoneDisplay(surface: Surface, width: Int, height: Int) {
        displayCreationInProgress = true
        runCatching {
            check(surface.isValid) { NativeStrings.text("nativeMirrorSurfaceUnavailable") }
            CapabilityProbe(this, privilegedAccess).run().forEach { (name, probe) ->
                OperationLog.i(this, "CapabilityProbe", "$name supported=${probe.supported} detail=${probe.detail}")
            }
            desktopModeConfigurator.applyForCurrentDevice()
            clearOverlayDisplayRequestTwice("before_direct_phone_display_create")
            val platform = VirtualDisplayPlatform.inspect()
            val service = privilegedAccess.service("display", VirtualDisplayPlatform.MANAGER_INTERFACE)
            phoneOwnedDisplay?.attachment?.release()
            val owned = platform.openOwned(
                this,
                service,
                surface,
                targetWidth,
                targetHeight,
                density,
                showSystemDecorations,
                configureBeforePublish = { displayId ->
                    if (desktopEnvironment.id == XIAOMI_PROFILE) applyXiaomiDesktopDensity(displayId, density)
                },
                publicDisplay = true,
                name = "Dextop Desktop",
            )
            phoneOwnedDisplay = owned
            val display = getSystemService(DisplayManager::class.java).getDisplay(owned.displayId)
                ?: error(moe.n4tsu.dextop.InlineStrings.format(moe.n4tsu.dextop.localText("uiTheDirectDextopDisplay0IsUnavailable"), owned.displayId))
            OperationLog.i(
                this,
                "DisplayBackend",
                "direct Plasma display created display=${owned.displayId} ${targetWidth}x$targetHeight/$density " +
                    "host=${width}x$height"
            )
            display
        }.onSuccess { display ->
            attachDesktopDisplay(display, width, height)
        }.onFailure { error ->
            displayCreationInProgress = false
            runCatching { phoneOwnedDisplay?.attachment?.release() }
            phoneOwnedDisplay = null
            OperationLog.e(this, "MirrorService", "direct Plasma display creation failed", error)
            Log.e(logTag, "direct Plasma display creation failed", error)
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
            ?: error(moe.n4tsu.dextop.localText("uiTheDextopCarCompanionDestinationSurfaceIsUnavailable"))
        val platform = VirtualDisplayPlatform.inspect()
        val service = privilegedAccess.service(
            "display",
            VirtualDisplayPlatform.MANAGER_INTERFACE
        )
        autoOwnedDisplay?.attachment?.release()
        val dextopAutoSelected = getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
            .getString("flutter.android_auto_shell", "dextop") == "dextop"
        // Dextop Auto draws the whole car display and keeps each app on a display of its own.
        // With system decorations or desktop windowing Android puts its own desktop here (DeX on
        // Samsung, HyperOS's desktop on Xiaomi), which shows in the car whenever Dextop Auto is
        // not drawn over it. The display stays public and trusted so Dextop's windows can use it.
        val displayDecorations = decorations && !dextopAutoSelected
        val (outputWidth, outputHeight) = autoDestinationSize()
        val owned = platform.openOwned(
            this,
            service,
            destination,
            outputWidth,
            outputHeight,
            density,
            displayDecorations,
            configureBeforePublish = { displayId ->
                if (forceFreeformSession && !dextopAutoSelected) {
                    desktopModeConfigurator.configureDisplay(displayId, forceFreeform = true)
                }
            },
            publicDisplay = dextopAutoSelected,
        )
        autoOwnedDisplay = owned
        targetDisplayId = owned.displayId
        mirrorDisplayId = owned.displayId
        showSystemDecorations = displayDecorations
        // An Android Auto session never disables navigation or gestures: the phone stays
        // fully usable, and the car display has no phone navigation to suppress.
        clearInheritedDisplayOverrides(targetDisplayId)
        applyAutoLogicalSize()
        configureDisplay(skipWindowing = forceFreeformSession || dextopAutoSelected)
        if (!dextopAutoSelected) startAutoOverlapCorrection()
        if (dextopAutoSelected) {
            // Dextop Auto never falls back to a decorated platform desktop (DeX's desktop on
            // Samsung); a shell that cannot start yet is started again on the same display.
            if (!applyWindowManagerSelection("auto_display_created")) retryDextopAutoShell(owned.displayId, 0)
        } else if (!launchSelectedDesktopHome("auto_display_created")) {
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
            error(moe.n4tsu.dextop.localText("uiTheDesktopHOMEActivityCouldNotBeLaunched"))
        }
        if (showSystemDecorations && desktopEnvironment.platformManaged && !forceFreeformSession) {
            listOf(250L, 700L, 1_500L).forEach { delay ->
                val decoratedDisplayId = targetDisplayId
                postMainDelayed(delay) {
                    if (active && targetDisplayId == decoratedDisplayId) {
                        setDesktopSystemDecorationsVisible(decoratedDisplayId, false)
                    }
                }
            }
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

    private fun autoDestinationSize(): Pair<Int, Int> =
        CardexRelayService.activeDestinationSize() ?: (targetWidth to targetHeight)

    /** Keep the car's physical buffer fixed; WMS fits the scaled desktop into it. */
    private fun applyAutoLogicalSize() {
        val service = systemService("window", "android.view.IWindowManager")
        val type = Class.forName("android.view.IWindowManager")
        type.getMethod("setForcedDisplaySize", Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            .invoke(service, targetDisplayId, targetWidth, targetHeight)
    }

    private fun replaceAutoDestinationSurface(nextSurface: Surface?) {
        if (!autoOnlySession || nextSurface == null || !nextSurface.isValid) return
        autoDestinationSurface = nextSurface
        val (outputWidth, outputHeight) = autoDestinationSize()
        val updated = autoOwnedDisplay?.resize(
            nextSurface,
            outputWidth,
            outputHeight,
            density,
        ) == true
        check(updated) { moe.n4tsu.dextop.localText("uiUnableToMoveTheAndroidAutoDesktopToTheNewHostSurface") }
        applyAutoLogicalSize()
        OperationLog.i(
            this,
            "CarCompanion",
            "Auto-owned display moved to replacement host surface display=$targetDisplayId",
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
        val independentDisplayIds = AndroidAutoMirrorActivity.autoOverlayDisplayIds() +
            coverDisplayController.ownedDisplayIds()
        val remaining = displayBackend.overlayDisplayIds()
            .filterNot { it in independentDisplayIds }
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
                moe.n4tsu.dextop.localText("uiOverlayDisplayCleanupTimedOutRefusingToCreateADuplicateDisplay") +
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
                AndroidAutoMirrorActivity.autoOverlaySpecs() +
                    coverDisplayController.ownedSpecs(),
                ownContentOnly = !autoOnlySession &&
                    moe.n4tsu.dextop.plasma.WindowManagerSelection.isPlasma(this),
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
        val independentDisplayIds = AndroidAutoMirrorActivity.autoOverlayDisplayIds() +
            coverDisplayController.ownedDisplayIds()
        val display = displayBackend.findCreatedDisplay(existing, independentDisplayIds)
        if (display == null) {
            if (attempt < 40) postMainDelayed(100L) {
                waitForOverlay(existing, width, height, attempt + 1)
            }
            else {
                val error = IllegalStateException(moe.n4tsu.dextop.localText("uiOverlayDisplayCreationTimedOut"))
                displayCreationInProgress = false
                completeStart(Result.failure(error))
                Log.e(logTag, "overlay display creation timed out", error)
                stop()
            }
            return
        }
        attachDesktopDisplay(display, width, height)
    }

    /** Publishes [display] as the Dextop desktop: navigation, mirror/host Surface, input, HOME and menus. */
    private fun attachDesktopDisplay(display: Display, width: Int, height: Int) {
        runCatching {
            check(privilegedAccess.isAvailable()) {
                NativeStrings.text("nativeShizukuUnavailable")
            }
            targetDisplayId = display.displayId
            // The session-level navigation request is made before the asynchronous
            // overlay display exists. Apply it again now that SystemUI has created
            // a per-display navigation bar for the actual desktop display.
            if (active && !autoOnlySession && targetDisplayId != Display.DEFAULT_DISPLAY) {
                navigationSuppressedDisplayId = targetDisplayId
                phoneGuard.applyNavigationDisabled(
                    targetDisplayId,
                    true,
                    desktopNavigationToken,
                    "desktop"
                )
            }
            imeRegion.requestImeRegionProbe("display_attached", force = true)
            clearInheritedDisplayOverrides(targetDisplayId)
            if (desktopEnvironment.id == XIAOMI_PROFILE) applyXiaomiDesktopDensity(targetDisplayId, density)
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
            activateTopologyForIndependentDisplays("main_display_attached")
            displayCreationInProgress = false
            sessionJournal.running(targetDisplayId)
            synchronizeDesktopWallpaperDimensions(
                targetWidth,
                targetHeight,
                "display attached",
            )
            val externalDisplays = externalDisplayDetector.snapshot()
            physicalExternalDisplayConnected = physicalInputRoutingSupported && externalDisplays.connected
            val routedInputCount = if (!autoOnlySession && (physicalExternalDisplayConnected || routePhysicalMice)) runCatching {
                physicalInputRouter.routeConnectedDevices(
                    display,
                    routePhysicalMice,
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
            if (!autoOnlySession) virtualPointer.startVirtualMouse()
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
            if (!launchSelectedDesktopHome("display_attached")) {
                // One UI 8 rejects HOME launches on Samsung-owned virtual
                // displays that do not advertise system decorations. Rebuild
                // the display once with that flag instead of tearing down a
                // session which otherwise mirrored successfully.
                if (retryHomeLaunchWithSystemDecorations(width, height)) {
                    return@runCatching
                }
                error(moe.n4tsu.dextop.localText("uiTheDesktopHOMEActivityCouldNotBeLaunched"))
            }
            // Some Samsung builds require the display to advertise system
            // decorations while HOME is launched, then create both their DeX
            // taskbar and Android's regular NavigationBar. Once HOME has been
            // accepted, withdraw only the framework decorations so the DeX
            // taskbar remains as the sole bottom bar.
            if (showSystemDecorations && desktopEnvironment.platformManaged && !forceFreeformSession) {
                listOf(250L, 700L, 1_500L).forEach { delay ->
                    val decoratedDisplayId = targetDisplayId
                    root?.postDelayed({
                        if (active && targetDisplayId == decoratedDisplayId) {
                            setDesktopSystemDecorationsVisible(decoratedDisplayId, false)
                        }
                    }, delay)
                }
            }
            workspaces.pendingPausedWorkspace?.takeUnless { autoOnlySession }?.let { workspace ->
                workspaces.pendingPausedWorkspace = null
                root?.postDelayed({
                    if (!active || targetDisplayId < 0) return@postDelayed
                    workspaces.launchOverlayWorkspace(workspace, closeMenu = false)
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
            if (!autoOnlySession) {
                externalAutoAttemptedFor = -1
                root?.postDelayed({ restoreExternalDisplayMode("session_started") }, if (externalStartPending) 150L else 1_500L)
            }
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
            Log.i(
                logTag,
                "Dextop layer attached target=$targetDisplayId ${targetWidth}x$targetHeight " +
                        "autoOnly=$autoOnlySession"
            )
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
        // HyperOS keeps reporting the phone's size and density for a new virtual
        // display after the clear, so the desktop draws a shrunken portrait phone
        // layout. Setting the display's own metrics explicitly does stick.
        if (desktopEnvironment.id == XIAOMI_PROFILE && displayId == targetDisplayId && targetWidth > 0 && targetHeight > 0) {
            runCatching {
                type.getMethod("setForcedDisplaySize", Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                    .invoke(service, displayId, targetWidth, targetHeight)
                type.getMethod("setForcedDisplayDensityForUser", Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                    .invoke(service, displayId, density, userId)
            }.onFailure { Log.w(logTag, "Xiaomi display metrics pin failed display=$displayId", it) }
        }
        OperationLog.i(this, "DisplayBackend", "cleared inherited metrics display=$displayId")
    }

    private fun applyXiaomiDesktopDensity(displayId: Int, dpi: Int) {
        val service = systemService("window", "android.view.IWindowManager")
        val userId = android.os.UserHandle::class.java.getMethod("myUserId").invoke(null) as Int
        Class.forName("android.view.IWindowManager").getMethod(
            "setForcedDisplayDensityForUser", Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
        ).invoke(service, displayId, dpi, userId)
        Log.i(logTag, "Xiaomi desktop density applied display=$displayId dpi=$dpi")
    }

    private fun configureDisplay(skipWindowing: Boolean = false) {
        val service = systemService("window", "android.view.IWindowManager")
        val type = Class.forName("android.view.IWindowManager")
        // Width/height define portrait or landscape; an optional persisted
        // half-turn is then applied independently for that orientation.
        val rotation = displayRotationFor()
        if (!skipWindowing) {
            desktopModeConfigurator.configureDisplay(targetDisplayId, forceFreeformSession)
        }
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
                // A headless Android Auto display has nowhere useful to draw
                // its local IME. Route it to the phone so Gboard (or the
                // selected phone IME) appears there while the focused editor
                // remains on the Dextop display.
                .invoke(service, targetDisplayId, if (autoOnlySession) 1 else 0)
        }
        Log.i(logTag, "Dextop display configured display=$targetDisplayId rotation=$rotation")
    }

    private fun setDesktopSystemDecorationsVisible(displayId: Int, visible: Boolean) {
        runCatching {
            val service = systemService("window", "android.view.IWindowManager")
            Class.forName("android.view.IWindowManager").getMethod(
                "setShouldShowSystemDecors",
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType
            ).invoke(service, displayId, visible)
        }.onSuccess {
            OperationLog.i(
                this,
                "SystemDecorations",
                "display=$displayId visible=$visible"
            )
            Log.i(logTag, "desktop system decorations display=$displayId visible=$visible")
        }.onFailure {
            OperationLog.e(
                this,
                "SystemDecorations",
                "display=$displayId update failed visible=$visible",
                it
            )
        }
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

    private fun launchHome(): Boolean {
        val intent = if (desktopEnvironment.id == "samsung_dex") {
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_SECONDARY_HOME)
                .setComponent(
                    ComponentName(
                        "com.sec.android.app.launcher",
                        "com.honeyspace.dexservice.SecondaryLauncher"
                    )
                )
        } else {
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(targetDisplayId)
        val direct = runCatching { startActivity(intent, options.toBundle()) }
        if (direct.isFailure && intent.component != null && privilegedAccess.isAvailable()) {
            val component = intent.component!!.flattenToShortString()
            val category = if (desktopEnvironment.id == "samsung_dex") {
                Intent.CATEGORY_SECONDARY_HOME
            } else {
                Intent.CATEGORY_HOME
            }
            val result = privilegedAccess.execute(
                "am", "start",
                "--display", targetDisplayId.toString(),
                "-a", Intent.ACTION_MAIN,
                "-c", category,
                "-n", component,
            )
            if (result.succeeded) {
                OperationLog.i(
                    this,
                    "DesktopHome",
                    "HOME launched through privileged fallback display=$targetDisplayId component=$component"
                )
                Log.i(logTag, "home launched through privileged fallback display=$targetDisplayId")
                return true
            }
            val failure = IllegalStateException(
                moe.n4tsu.dextop.InlineStrings.format(moe.n4tsu.dextop.localText("uiPrivilegedHOMELaunchFailed0"), result.error.ifBlank { result.output }),
                direct.exceptionOrNull(),
            )
            OperationLog.e(this, "DesktopHome", "HOME launch failed display=$targetDisplayId", failure)
            Log.e(logTag, "home launch failed", failure)
            return false
        }
        return direct.onSuccess {
            Log.i(
                logTag,
                "home launched display=$targetDisplayId component=${intent.component} " +
                    "decorations=$showSystemDecorations"
            )
        }.onFailure {
            OperationLog.e(
                this,
                "DesktopHome",
                "HOME launch failed display=$targetDisplayId decorations=$showSystemDecorations",
                it
            )
            Log.e(logTag, "home launch failed", it)
        }.isSuccess
    }

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
        if (desktopEnvironment.id == "samsung_dex") {
            // Samsung's SECONDARY_HOME can be launched explicitly on an
            // undecorated display. Old builds persisted decorations after the
            // generic HOME intent was rejected; keeping that workaround would
            // recreate the redundant Android NavigationBar forever.
            preferences.edit().remove("firmware_fingerprint").apply()
            return false
        }
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

    /**
     * Where the direct display renders while the desktop is extended: nobody looks at it, the
     * screens mirror their parts. Frames are dropped as they arrive.
     */
    private var extendSink: android.media.ImageReader? = null

    private fun directDisplaySurface(): Surface {
        extendSink?.takeIf { it.width == targetWidth && it.height == targetHeight }?.let { return it.surface }
        closeExtendSink()
        val sink = android.media.ImageReader.newInstance(targetWidth, targetHeight, android.graphics.PixelFormat.RGBA_8888, 2)
        sink.setOnImageAvailableListener({ reader -> runCatching { reader.acquireLatestImage()?.close() } }, Handler(mainLooper))
        extendSink = sink
        return sink.surface
    }

    private fun closeExtendSink() {
        val sink = extendSink ?: return
        extendSink = null
        runCatching { sink.close() }
    }

    private fun releasePhoneOwnedDisplay(reason: String) {
        val owned = phoneOwnedDisplay ?: return
        phoneOwnedDisplay = null
        runCatching { owned.attachment.release() }
            .onFailure { Log.e(logTag, "direct Plasma display release failed", it) }
        closeExtendSink()
        OperationLog.i(this, "DisplayBackend", "direct Plasma display released display=${owned.displayId} reason=$reason")
    }

    private fun releaseMirror() {
        mirrorRefreshGeneration += 1
        stopRawMouseReader()
        displayBackend.releaseLayer()
        // The direct display is the desktop: keep it (and its tasks) while
        // the host Surface is gone; attachMirror() rebinds the next Surface.
        runCatching { autoOwnedDisplay?.attachment?.release() }
        autoOwnedDisplay = null
        autoDestinationSurface = null
        mirrorDisplayId = -1
        mirrorHostWidth = 0
        mirrorHostHeight = 0
        displayCreationInProgress = false
    }

    private fun attachMirror(
        hostWidth: Int,
        hostHeight: Int,
        strategyOverride: String? = null,
        rebuild: Boolean = false,
    ) {
        val host = surfaceView ?: error(NativeStrings.text("nativeMirrorSurfaceUnavailable"))
        phoneOwnedDisplay?.let { owned ->
            if (phoneRegion != null) {
                // Extended: the display renders off screen and the phone mirrors its part,
                // as the external screen does.
                check(owned.resize(directDisplaySurface(), targetWidth, targetHeight, density)) {
                    moe.n4tsu.dextop.localText("uiUnableToMoveTheDirectDextopDisplayOffScreen")
                }
                OperationLog.i(this, "DisplayBackend", "direct Plasma display off screen for extended desktop " +
                    "desktop=${targetWidth}x$targetHeight phone=$phoneRegion")
                return@let
            }
            closeExtendSink()
            val surface = host.holder.surface
            check(surface.isValid) { NativeStrings.text("nativeMirrorSurfaceUnavailable") }
            check(owned.resize(surface, targetWidth, targetHeight, density)) {
                moe.n4tsu.dextop.localText("uiUnableToBindTheDirectDextopDisplayToTheHostSurface")
            }
            mirrorHostWidth = hostWidth
            mirrorHostHeight = hostHeight
            OperationLog.i(
                this,
                "DisplayBackend",
                "direct Plasma display bound host=${hostWidth}x$hostHeight " +
                    "desktop=${targetWidth}x$targetHeight/$density"
            )
            return
        }
        displayBackend.attach(
            targetDisplayId,
            host,
            hostWidth,
            hostHeight,
            targetWidth,
            targetHeight,
            density,
            strategyOverride,
            rebuild,
            crop = phoneRegion,
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
        forceVirtualDisplay: Boolean = false,
        /** Recreate the recording display; see DesktopEnvironment.rebuildMirrorAfterLiveResize. */
        rebuild: Boolean = false,
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
                attachMirror(nextWidth, nextHeight, strategyOverride, rebuild)
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
        // The car display has its own size; folding or rotating the phone must not resize it.
        if (autoOnlySession) return false
        // Nor does the external screen while it alone shows the desktop.
        if (externalOutput?.mode == "external") return false
        val preferences = getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
        val selectedDeviceResolution =
            preferences.getString("flutter.selected_resolution_id", "device") == "device"
        return laptopModeActive || selectedDeviceResolution &&
                (isFoldableDevice() || preferences.getBoolean("flutter.foldable_auto", false) ||
                        desktopEnvironment.autoResizeWithHostDisplay)
    }

    private fun isLaptopAutoDetectionEnabled(): Boolean {
        val preferences = getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
        // Automatic laptop mode is opt-in. Once the user changes the switch,
        // the explicit preference always wins.
        return if (preferences.contains("flutter.foldable_laptop_mode")) {
            preferences.getBoolean("flutter.foldable_laptop_mode", false)
        } else {
            false
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
        return when (posture.laptopFoldProfile()) {
            LaptopFoldProfile.FOLD8 -> {
                // Fold8 laptop posture is the top/bottom (horizontal hinge)
                // layout. Keep its portrait-only automatic gate, but do not
                // reject the session when One UI reports a transient vertical
                // hinge orientation during the panel transition.
                requestedPortrait
            }

            LaptopFoldProfile.STANDARD_FOLDABLE -> {
                // On the normal-size Fold family the usable top/bottom
                // laptop layout is reached after rotating the phone 90° from
                // its natural portrait orientation. In that posture the
                // FoldingFeature hinge runs horizontally, so automatic mode
                // is intentionally landscape-only. Manual overlay activation
                // still works in portrait for testing and recovery.
                !requestedPortrait && posture.foldingApiLaptopPosture != false
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
        if (externalOutput?.mode == "external" || externalOutput?.mode == "extend") return
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
            if (sizeChanged && !(autoOnlySession && autoOwnedDisplay != null) && phoneOwnedDisplay == null) {
                type.getMethod(
                    "setForcedDisplaySize",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                ).invoke(service, targetDisplayId, next.width, next.height)
            }
            if (densityChanged || desktopEnvironment.id == XIAOMI_PROFILE) {
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
        if (densityChanged && plasmaShell is moe.n4tsu.dextop.plasma.CompositedWindowShell) {
            // Plasma and its application displays keep the density they were started with
            // (the external screen alone would show the desktop at the phone's scale): start it
            // again at the new one; its windows are handed back to the display and adopted.
            root?.post {
                if (!active || plasmaShell == null) return@post
                stopPlasmaShell(restoreWindows = true)
                applyWindowManagerSelection("desktop density changed")
                if (externalOutput?.mode == "extend") applyDesktopOutputs()
            }
        }
        attachExternalMirror()
        if (sizeChanged) {
            synchronizeDesktopWallpaperDimensions(targetWidth, targetHeight, reason)
            plasmaShell?.onDisplayResized(targetWidth, targetHeight)
        }
        phoneOwnedDisplay?.takeIf { sizeChanged || densityChanged }?.let { owned ->
            val surface = if (phoneRegion != null) directDisplaySurface()
                else surfaceView?.holder?.surface?.takeIf { it.isValid }
            // A host Surface still being created (the external screen's window just added, or
            // the phone's coming back) binds at the new size when it arrives (attachMirror);
            // the forced size below must still change now.
            val updated = surface == null || owned.resize(surface, targetWidth, targetHeight, density)
            check(updated) { moe.n4tsu.dextop.localText("uiUnableToResizeTheDirectDextopDisplay") }
            OperationLog.i(
                this,
                "DisplayBackend",
                "direct Plasma display resized applied=$updated desktop=${targetWidth}x$targetHeight/$density"
            )
            if (desktopEnvironment.id == XIAOMI_PROFILE) {
                // HyperOS falls back to the phone's portrait size once the
                // override is cleared, which shrinks the pointer viewport to a
                // strip of the desktop. Pin the new size as at creation.
                type.getMethod("setForcedDisplaySize", Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                    .invoke(service, targetDisplayId, targetWidth, targetHeight)
            } else {
                type.getMethod("clearForcedDisplaySize", Int::class.javaPrimitiveType)
                    .invoke(service, targetDisplayId)
            }
        }
        if (autoOnlySession && sizeChanged) {
            val destination = autoDestinationSurface
            val (outputWidth, outputHeight) = autoDestinationSize()
            val updated = destination?.takeIf { it.isValid }?.let {
                autoOwnedDisplay?.resize(it, outputWidth, outputHeight, density)
            } == true
            OperationLog.i(
                this,
                "CarCompanion",
                "Auto-owned display buffer resize applied=$updated desktop=${targetWidth}x${targetHeight}/$density"
            )
            if (!updated) {
                throw IllegalStateException(moe.n4tsu.dextop.localText("uiUnableToResizeTheAndroidAutoDesktopBuffer"))
            }
            applyAutoLogicalSize()
        }
        if (sizeChanged) {
            // A live size change can cross the natural-orientation boundary.
            // WMS also drops projected-display freeform policy on some Pixel
            // builds, so synchronize rotation and desktop policy after a size
            // change.  DPI-only edits deliberately skip this path so Samsung
            // DeX's taskbar is not forced through an unnecessary rebuild.
            // Headless Car Companion sessions have no phone-side host. Their
            // logical desktop may resize for the connected car display, but
            // that must never rotate or otherwise alter the phone UI.
            if (!autoOnlySession && externalPhoneRole != "phone") {
                applyHostDisplayOrientation(requestedPortrait)
                phoneGuard.forcePhoneRotation(requestedPortrait)
            }
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
                    forceVirtualDisplay = sizeChanged,
                    rebuild = sizeChanged && desktopEnvironment.rebuildMirrorAfterLiveResize,
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
        if (desktopEnvironment.id == XIAOMI_PROFILE && sizeChanged &&
            virtualPointer.virtualPointerRegisteredProfile == "touchpad" && virtualPointer.virtualMouseProcessAlive()) {
            virtualPointer.virtualMouseReady = false
            privilegedInputClient.setOutputReady(false)
            virtualPointer.startVirtualMouse()
        }
    }

    private fun synchronizeDesktopWallpaperDimensions(
        width: Int,
        height: Int,
        reason: String,
    ) {
        val displayId = targetDisplayId
        desktopWallpaperController.synchronize(displayId, width, height, reason) {
            active && targetDisplayId == displayId
        }
    }

    private fun configForHostGeometry(
        base: Config,
        hostWidth: Int,
        hostHeight: Int,
        systemDensity: Int
    ): Config {
        val automaticDensity = (160 + systemDensity / 160f * 24)
            .toInt().coerceIn(160, 320)
        if (laptopModeActive) {
            // The keyboard deck only reduces the height of the host Surface.
            // Keep the logical scale selected when the desktop was started
            // (including workspace magnification and custom DPI) instead of
            // replacing it with the physical panel metrics. Replacing both
            // size and density here makes One UI detach the desktop taskbar
            // and its task stack, leaving only WallpaperService visible.
            val logicalScale = base.width.toFloat() / hostWidth.coerceAtLeast(1)
            val paneWidth = base.width.coerceIn(480, 7680) and -2
            val paneHeight = (hostHeight * logicalScale)
                .roundToInt()
                .coerceIn(480, 7680) and -2
            return base.copy(
                width = paneWidth,
                height = paneHeight,
                density = base.density.coerceIn(72, 960)
            )
        }
        // A magnified device profile already has a matching physical overlay
        // mode. Do not replace only its logical metrics during a Fold panel
        // hand-off; the overlay adapter cannot resize its physical mode in
        // place, and the two geometries would diverge again.
        // Do not infer magnification only from a DPI difference: when
        // "apply scale to windows" is enabled the requested behavior is to
        // retain the original DPI, so density can legitimately equal the
        // automatic value even though width and height are magnified.
        if (isWorkspaceMagnificationActive() || base.density != automaticDensity) {
            return base
        }
        // An unscaled device profile can continue to follow the complete host
        // panel geometry as before.
        val portrait = base.height > base.width
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

    private fun isWorkspaceMagnificationActive(): Boolean {
        val value = getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
            .all["flutter.desktop_workspace_magnification_percent"]
        return (value as? Number)?.toInt()?.let { it > 100 } == true
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
        val profile = virtualPointer.activeVirtualPointerProfile()
        return active && (laptopModeActive || !directTouch) &&
                (profile == "touchpad" || profile == "mouse") &&
                virtualPointer.virtualPointerRegisteredProfile == profile &&
                virtualPointer.virtualMouseReady && virtualPointer.virtualMouseProcessAlive()
    }

    private fun rawTouchscreenBridgeConsumesTouchSurface(sourceView: View? = null): Boolean {
        if (!touchscreenReaderRunning || !touchscreenReaderReady ||
            touchscreenReaderCandidateCount <= 0 ||
            !rawTouchscreenBridgeEligible()
        ) return false
        // The native reader has the same rectangle in its config, so this
        // stream is deliberately left to the accessibility overlay when its
        // ACTION_DOWN landed in the IME.
        if (sourceView === surfaceView && imeDirectTouchHeld) return false
        return when {
            sourceView == null -> true
            sourceView === laptopTrackpadView -> laptopModeActive
            sourceView === surfaceView -> !directTouch
            else -> false
        }
    }

    private fun scheduleRawTouchscreenTopologyRefresh(reason: String, force: Boolean = false) {
        val refreshGeneration = ++rawTouchscreenTopologyRefreshGeneration
        root?.postDelayed({
            if (refreshGeneration != rawTouchscreenTopologyRefreshGeneration || !active) {
                return@postDelayed
            }
            if (rawTouchscreenBridgeEligible()) {
                val fingerprint = rawTouchscreenTopologyFingerprint()
                if (!force && fingerprint == appliedRawTouchscreenTopologyFingerprint) {
                    Log.d(logTag, "native input topology unchanged; skipping refresh reason=$reason")
                    return@postDelayed
                }
                refreshPrivilegedInputConfig("topology_refresh:$reason")
                startRawTouchscreenReaderIfEligible()
                appliedRawTouchscreenTopologyFingerprint = fingerprint
            }
            val message = "native input topology refresh reason=$reason " +
                    "running=$touchscreenReaderRunning eligible=${rawTouchscreenBridgeEligible()}"
            OperationLog.i(this, "InputRouting", message)
            Log.i(logTag, message)
        }, 220L)
    }

    private fun rawTouchscreenTopologyFingerprint(): String = listOf(
        "profile=$virtualPointer.virtualPointerRegisteredProfile",
        "target=$targetDisplayId:$targetWidth:$targetHeight",
        "rotation=${rawTouchscreenDisplayRotation()}",
        "surface=${rawTouchscreenViewBounds(surfaceView)}",
        "trackpad=${rawTouchscreenViewBounds(laptopTrackpadView)}",
        "ime=${imeRegion.imeTouchBoundsOnHost()}",
        "laptop=$laptopModeActive",
        "direct=$directTouch"
    ).joinToString(";")

    private fun startRawTouchscreenReaderIfEligible() {
        if (!rawTouchscreenBridgeEligible()) return
        val profile = virtualPointer.virtualPointerRegisteredProfile
        touchscreenReaderGeneration += 1
        touchscreenReaderRunning = true
        privilegedInputClient.updateConfig(buildPrivilegedInputConfig(profile))
        val message = "native EventHub reader requested generation=$touchscreenReaderGeneration " +
                "profile=$profile rotation=${rawTouchscreenDisplayRotation()} " +
                "fullscreen=${rawTouchscreenViewBounds(surfaceView)} " +
                "trackpad=${rawTouchscreenViewBounds(laptopTrackpadView)} " +
                "ime=${imeRegion.imeTouchBoundsOnHost()}"
        OperationLog.i(this, "InputRouting", message)
        Log.i(logTag, message)
        appliedRawTouchscreenTopologyFingerprint = rawTouchscreenTopologyFingerprint()
    }

    private fun rawTouchscreenViewBounds(view: View?): Rect? {
        val target = view ?: return null
        val host = root ?: return null
        if (!target.isAttachedToWindow || target.width <= 0 || target.height <= 0 ||
            !host.isAttachedToWindow || host.width <= 0 || host.height <= 0
        ) return null

        // The native input bridge first maps a physical touchscreen contact to
        // hostWidth × hostHeight.  Keep the target rectangles in that same
        // root-local coordinate space.  getLocationOnScreen() alone is an
        // absolute display coordinate and diverges from root coordinates when
        // portrait locks, insets, or a rotated foldable layout are involved.
        val targetLocation = IntArray(2)
        val hostLocation = IntArray(2)
        target.getLocationOnScreen(targetLocation)
        host.getLocationOnScreen(hostLocation)
        val left = targetLocation[0] - hostLocation[0]
        val top = targetLocation[1] - hostLocation[1]
        return Rect(left, top, left + target.width, top + target.height)
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
        appliedRawTouchscreenTopologyFingerprint = null
        if (wasRunning) {
            val message = "native EventHub routing state cleared reason=$reason"
            OperationLog.i(this, "InputRouting", message)
            Log.i(logTag, message)
        }
    }

    private fun fallbackFromRawTouchscreen(reason: String) {
        val profile = virtualPointer.activeVirtualPointerProfile()
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

    private val phoneGuard: PhoneGuard = PhoneGuard(this, object : PhoneGuardHost, DesktopSession by session, OverlayMenu by overlayMenu {
        override val desktopNavigationToken: Binder get() = this@MirrorService.desktopNavigationToken
        override fun displayGeometrySnapshot(reason: String, hostWidth: Int?, hostHeight: Int?): String = this@MirrorService.displayGeometrySnapshot(reason, hostWidth, hostHeight)
        override val hostDisplayMonitorHandler: Handler get() = this@MirrorService.hostDisplayMonitorHandler
        override var lastForcedPhoneHalfTurn: Boolean?
            get() = this@MirrorService.lastForcedPhoneHalfTurn
            set(value) { this@MirrorService.lastForcedPhoneHalfTurn = value }
        override var lastForcedPhonePortrait: Boolean?
            get() = this@MirrorService.lastForcedPhonePortrait
            set(value) { this@MirrorService.lastForcedPhonePortrait = value }
        override var navigationRestoreGeneration: Int
            get() = this@MirrorService.navigationRestoreGeneration
            set(value) { this@MirrorService.navigationRestoreGeneration = value }
        override var navigationSuppressedDisplayId: Int
            get() = this@MirrorService.navigationSuppressedDisplayId
            set(value) { this@MirrorService.navigationSuppressedDisplayId = value }
        override val navigationToken: Binder get() = this@MirrorService.navigationToken
        override val phoneRotationController: PhoneRotationController get() = this@MirrorService.phoneRotationController
        override fun rotate180PreferenceKey(portrait: Boolean): String = this@MirrorService.rotate180PreferenceKey(portrait)
        override val suspendedForLockScreen: Boolean get() = this@MirrorService.suspendedForLockScreen
        override fun systemService(name: String, interfaceName: String): Any = this@MirrorService.systemService(name, interfaceName)
    })

    private fun stop() {
        if (stopping) return
        stopping = true
        if (coverDisplayController.state().backButtonsActive) {
            coverDisplayController.stopBackButtonMode { }
        }
        if (coverDisplayController.state().trackpadActive) {
            coverDisplayController.stopTrackpadMode { }
        }
        val cleanupGeneration = ++stopCleanupGeneration
        val wasActive = active
        val displayBeingRemoved = targetDisplayId
        if (wasActive) {
            OperationLog.i(this, "DisplayGeometry", displayGeometrySnapshot("session_stopping"))
        }
        cast.endCastSession("dextop_stopped")
        stopHostDisplayMonitor()
        suspendedForLockScreen = false
        suspendedConfig = null
        screenLifecycleGeneration += 1
        unlockResumeScheduled = false
        unlockCandidateSince = 0L
        if (dragHeld) toggleDrag()
        runCatching { physicalInputRouter.restore() }
            .onFailure { Log.e(logTag, "physical input restoration failed", it) }
        swipeTyping.restoreDextopSwipeIme()

        stopPlasmaShell(restoreWindows = false)

        // Tear down the host first.  In particular, remove the SurfaceView's
        // callback before releasing the mirror layer; clearing the overlay
        // setting first makes One UI deliver surface/display callbacks while
        // the accessibility host is still registered, which can leave Back,
        // Circle to Search, or the desktop task in a broken state.
        removeWindow()
        targetDisplayId = -1
        imeRegion.imeRegionSessionGeneration += 1
        imeRegion.imeRegionProbeFollowUp.set(false)
        imeRegion.imeTouchRegion = null
        imeRegion.imeRegionProbePending = false
        active = false
        laptopModeActive = false
        laptopBaseConfig = null
        laptopFullHostHeightPx = 0
        laptopManualOverride = false
        laptopAutoSuppressedByUser = false
        laptopAutoActivated = false
        pendingLaptopMode = null
        pendingLaptopModeSince = 0L
        laptopModeEvaluationGeneration += 1
        posture.laptopPostureReevaluationGeneration += 1
        posture.laptopHostMismatchSince = 0L

        // Settings.Global is only a request to OverlayDisplayAdapter.  Do not
        // restore the phone/DeX environment or mark the session finished until
        // the display manager has observed the requested display disappear.
        releasePhoneOwnedDisplay("session_stop")
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
            phoneGuard.setPhoneNavigationDisabled(false)
            phoneGuard.releasePhoneRotation(clearSnapshot = true)
        }
        val autoSessionActive = AndroidAutoMirrorActivity.isAutoSessionActive()
        val coverSessionActive = coverDisplayController.state().desktopActive
        runCatching {
            if (autoSessionActive || coverSessionActive) {
                DisplayEnvironmentSettings(this).activateTopologyForOverlays(
                    AndroidAutoMirrorActivity.autoOverlayDisplayIds() +
                        coverDisplayController.ownedDisplayIds()
                )
            } else {
                DisplayEnvironmentSettings(this).restoreTopology()
            }
        }.onFailure { Log.e(logTag, "topology restoration failed", it) }
        if (!autoOnlySession) MainActivity.restoreOrientation()
        if (!autoOnlySession) {
            // Restore this independently of the shared Auto/cover transaction:
            // ending the phone-side laptop/BlackBerry session must restore the
            // phone launcher's wallpaper even if another virtual display stays.
            sessionJournal.restoreWallpaperDimensions()
        }
        val keepInternal120Hz = internalRefreshRateController.isEnabledAndSupported() &&
                !externalDisplayDetector.snapshot().connected
        if (keepInternal120Hz) {
            runCatching { internalRefreshRateController.keepCurrentValue() }
                .onFailure { Log.e(logTag, "unable to preserve 120 Hz after disconnect", it) }
        }
        val restored = if (autoSessionActive || coverSessionActive) {
            // An independent Auto or cover overlay owns the shared desktop
            // transaction until it stops. Restoring here would remove the
            // still-running independent session.
            true
        } else {
            runCatching {
                desktopModeConfigurator.restore()
                sessionJournal.restoreSystemSettings()
            }.onFailure { Log.e(logTag, "settings restoration failed", it) }.isSuccess
        }
        if (restored && !autoSessionActive && !coverSessionActive) sessionJournal.clear()
        if (restored) {
            getSharedPreferences("dextop_cleanup_state", MODE_PRIVATE).edit()
                .putBoolean("cleanup_pending", false)
                .putBoolean("paused_by_user", false)
                .remove("paused_workspace")
                .putLong("verified_at", System.currentTimeMillis())
                .commit()
        }
        if (wasActive) OperationLog.finishSession(this, restored)
        // All display and system restoration is complete at this point. Clear
        // the latch before returning so a new start can reuse this service.
        stopping = false
        carGestures.finishCarPan()
        autoOnlySession = false
        AutoSplitPlacement.clear()
        completeStart(Result.failure(IllegalStateException(moe.n4tsu.dextop.localText("uiDextopWasStoppedBeforeStartupCompleted"))))
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
            ) { moe.n4tsu.dextop.localText("uiUnableToDetachTheDextopAccessibilityService") }
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
        cast.stopCastRouteDiscovery()
        finishAllLaptopKeyPresses()
        stopLaptopHardwareKeyboard()
        virtualPointer.stopVirtualMouse()
        // Prevent surfaceDestroyed() from releasing the mirrored display before
        // WindowManager has removed this host and its gesture registrations.
        surfaceView?.holder?.removeCallback(this)
        externalOutput?.let { output ->
            externalOutput = null
            output.surface.holder.removeCallback(this)
            output.surface.holder.removeCallback(output)
            externalBackend.releaseLayer()
            runCatching { output.windowManager.removeView(output.frame) }
        }
        // The next session's window is new; a role left from this one would skip shrinking it.
        externalPhoneRole = null
        externalInputDeck = false
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

}
