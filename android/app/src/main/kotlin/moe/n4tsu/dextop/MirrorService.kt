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
        override val root: TouchRoutingFrame? get() = this@MirrorService.root
        override val surfaceView: SurfaceView? get() = this@MirrorService.surfaceView
        override val windowManager: WindowManager? get() = this@MirrorService.windowManager
        override val secureDisplay: Boolean get() = this@MirrorService.secureDisplay
        override val showSystemDecorations: Boolean get() = this@MirrorService.showSystemDecorations
        override val directTouch: Boolean get() = this@MirrorService.directTouch
        override val suspendedForLockScreen: Boolean get() = this@MirrorService.lockScreen.suspendedForLockScreen
        override val desktopEnvironment: DesktopEnvironment get() = this@MirrorService.desktopEnvironment
        override val privilegedInputClient: PrivilegedInputClient get() = this@MirrorService.privilegedInputClient
        override val sessionJournal: SessionJournal get() = this@MirrorService.sessionJournal
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
        override val externalInputDeck: Boolean get() = this@MirrorService.externalOutputs.externalInputDeck
        override fun isFoldableMainDisplay(): Boolean = this@MirrorService.isFoldableMainDisplay()
        override fun isLaptopAutoDetectionEnabled(): Boolean = this@MirrorService.laptopModeSwitch.isLaptopAutoDetectionEnabled()
        override fun isLaptopAutoOrientationEligible(): Boolean = this@MirrorService.laptopModeSwitch.isLaptopAutoOrientationEligible()
        override fun laptopHostIsFullHeight(): Boolean = this@MirrorService.laptopModeSwitch.laptopHostIsFullHeight()
        override fun setLaptopMode(enabled: Boolean, requestedStyle: KeyboardDeckStyle) = this@MirrorService.laptopModeSwitch.setLaptopMode(enabled, requestedStyle)
    })


    companion object {
        internal const val PREFS = "freedextop_input"
        private const val KEY_DIRECT_TOUCH = "direct_touch"
        internal const val KEY_ROUTE_MOUSE = "route_physical_mouse"
        internal const val KEY_ROUTE_KEYBOARD = "route_physical_keyboard"

        /** Explicit compatibility switch for the previous software cursor. */
        internal const val KEY_SOFTWARE_CURSOR_FALLBACK = "software_cursor_fallback"

        /** Persisted three-way pointer profile; old installs use the fallback key. */
        internal const val KEY_VIRTUAL_POINTER_PROFILE = "virtual_pointer_profile"
        private const val KEY_ROTATE_180_LANDSCAPE = "rotate_180_landscape"
        private const val KEY_ROTATE_180_PORTRAIT = "rotate_180_portrait"
        internal const val KEY_BLACKBERRY_HEIGHT_MANUAL = "blackberry_height_manual"
        internal const val KEY_BLACKBERRY_HEIGHT_PERCENT = "blackberry_height_percent"
        internal const val VIRTUAL_MOUSE_NAME = "Dextop Virtual Mouse"
        internal const val VIRTUAL_TOUCHPAD_NAME = "Dextop Virtual Touchpad"
        internal const val LAPTOP_KEYBOARD_NAME = "Dextop Laptop Keyboard"
        internal const val LAPTOP_KEYBOARD_VENDOR_ID = 6353
        internal const val LAPTOP_KEYBOARD_PRODUCT_ID = 5417
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
        internal val EXTENDED_SCALES = listOf(75, 100, 125, 150, 175, 200)
        /** Without new scroll deltas for this long, a car swipe is treated as lifted. */
        internal const val CAR_SWIPE_IDLE_MS = 700L
        internal const val NOTIFICATION_ROUTE_RETRY_DELAY_MS = 140L
        internal const val NOTIFICATION_ROUTE_RETRIES = 5
        internal const val HOST_DISPLAY_MONITOR_INTERVAL_MS = 1_000L
        internal const val HOME_DECORATION_RETRY_DELAY_MS = 180L
        internal const val IME_REGION_PROBE_MIN_INTERVAL_MS = 250L
        internal const val DEBUG_FORCE_LAPTOP_MODE = false
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
        internal var instance: MirrorService? = null
        @JvmField internal var carCompanionInputActive = false

        /** Samsung's accessibility input filter cancels alternating phone/virtual touch streams. */
        internal fun setCarCompanionInputActive(value: Boolean) {
            carCompanionInputActive = value
            Handler(Looper.getMainLooper()).post { instance?.carCompanion?.applyCarCompanionKeyFilter() }
        }
        private var pending: Config? = null
        private var pendingAutoSurface: Surface? = null
        private var pendingStartResult: ((Result<Map<String, Any>>) -> Unit)? = null
        internal var pendingDemo = false
        internal var pendingLaptopDemo = false
        internal var pendingLaptopPreviewThemeId: String? = null
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
                decorations || it.launcher.shouldUsePersistedSystemDecorations()
            } ?: decorations
            if (active && running != null && running.targetDisplayId >= 0 &&
                secure == running.secureDisplay && effectiveDecorations == running.showSystemDecorations &&
                autoOnly == running.autoOnlySession && forceFreeform == running.forceFreeformSession
            ) {
                val requested = Config(width, height, density, secure, effectiveDecorations, autoOnly, forceFreeform)
                Handler(Looper.getMainLooper()).post {
                    runCatching {
                        check(instance === running && !running.serviceDestroyed) { moe.n4tsu.dextop.localText("uiDextopServiceChangedBeforeTheDisplayCouldBeUpdated") }
                        if (autoOnly) running.displayFactory.replaceAutoDestinationSurface(autoSurface)
                        val next = running.effectiveConfig(requested)
                        running.hostFollower.resizeActiveDisplay(next, "resolution changed from Android UI")
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

        internal fun completeStart(value: Result<Map<String, Any>>) {
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
                instance?.androidPause?.pausedForAndroid == true ||
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
            instance?.root?.post { instance?.laptopModeSwitch?.applyFlutterLaptopModeSetting(enabled) }
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
                if (service.keyboardDeck.laptopSettingsVisible) service.laptopTheme.showLaptopKeyboardSettings()
                else service.keyboardDeck.rebuildLaptopDeck()
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
                    service.keyboardDeck.laptopKeyboardView?.cancelSwipeRecognition()
                }
                val enabled = service.swipeTyping.laptopSwipeLanguageChoices().map { it.first }.toSet()
                if (service.swipeTyping.laptopSwipeLanguage() !in enabled) {
                    service.getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
                        .edit().putString("flutter.laptop_swipe_language", "en").apply()
                }
                val languagePopupWasVisible = service.keyboardDeck.laptopLanguagePopup != null
                service.laptopTheme.hideLaptopLanguagePopup()
                // Swipe commits are display-local accessibility edits.  Never
                // replace the user's IME just because the language changed.
                if (service.keyboardDeck.laptopSettingsVisible) service.laptopTheme.showLaptopKeyboardSettings()
                else if (service.laptopModeActive) service.keyboardDeck.rebuildLaptopDeck()
                if (languagePopupWasVisible) {
                    service.keyboardDeck.laptopMenuButton?.post {
                        service.keyboardDeck.laptopMenuButton?.let(service.laptopTheme::showLaptopLanguagePopup)
                    }
                }
            }
        }

        /** Shows the real laptop deck on the active virtual-display session. */
        fun showLaptopPreview(themeId: String? = null): Boolean {
            val service = instance ?: return false
            if (!active || service.root == null) return false
            service.keyboardDeck.laptopPreviewThemeId = themeId
            service.root?.post {
                service.posture.laptopManualOverride = true
                service.laptopModeSwitch.setLaptopMode(true)
            }
            return true
        }

        fun showLaptopDemo(context: Context) {
            pendingDemo = true
            pendingLaptopDemo = true
            instance?.demoWindow?.showDemoWindow()
        }

        fun setPendingLaptopPreviewTheme(themeId: String?) {
            pendingLaptopPreviewThemeId = themeId
            instance?.keyboardDeck?.laptopPreviewThemeId = themeId
        }

        fun hideLaptopDemo() {
            instance?.demoWindow?.hideDemoWindow()
            instance?.keyboardDeck?.laptopPreviewThemeId = null
            pendingDemo = false
            pendingLaptopDemo = false
            pendingLaptopPreviewThemeId = null
        }

        fun exitLaptopPreview() {
            instance?.root?.post {
                instance?.keyboardDeck?.laptopPreviewThemeId = null
                instance?.posture?.laptopManualOverride = false
                instance?.posture?.laptopAutoSuppressedByUser = false
                instance?.posture?.laptopAutoActivated = false
                instance?.laptopModeSwitch?.setLaptopMode(false)
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
            Handler(Looper.getMainLooper()).post { done(runCatching { service.externalOutputs.setExternalDisplay(mode) }.getOrDefault("failed")) }
        }

        /** Whether an external screen that can take the desktop is connected, and how it is used now. */
        /** A connected screen the desktop can be shown on, found without a running session. */
        fun externalOutputDisplay(context: Context): Display? {
            val manager = context.getSystemService(DisplayManager::class.java)
            return ExternalDisplayDetector(context).snapshot().displayIds.mapNotNull(manager::getDisplay)
                .firstOrNull { it.flags and Display.FLAG_PRESENTATION != 0 }
        }

        /** The extended desktop's screens for the arrangement editor, or null when not extended. */
        fun extendedDisplayArrangement(): Map<String, Any>? = instance?.externalOutputs?.extendedArrangement()

        /** Applies the arrangement editor's positions to the extended desktop. */
        fun arrangeExtendedDisplays(positions: Map<*, *>, done: (Boolean) -> Unit) {
            val service = instance ?: return done(false)
            Handler(Looper.getMainLooper()).post { done(runCatching { service.externalOutputs.arrangeExtendedFromEditor(positions) }.getOrDefault(false)) }
        }

        /** Sets one screen's scale on the extended desktop. */
        fun setExtendedDisplayScale(displayId: Int, percent: Int, done: (Boolean) -> Unit) {
            val service = instance ?: return done(false)
            Handler(Looper.getMainLooper()).post { done(runCatching { service.externalOutputs.setExtendedScale(displayId, percent) }.getOrDefault(false)) }
        }

        fun externalDisplayState(context: Context): Map<String, Any?> {
            val service = instance
            val display = externalOutputDisplay(context)
            return mapOf(
                "connected" to (display != null),
                "name" to display?.name,
                "running" to (active && service != null),
                "mode" to (service?.externalOutputs?.externalOutput?.mode ?:
                    context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
                        .getString("flutter.external_display_mode", "off")),
            )
        }

        /** Applies the "window manager" display setting to a running session. */
        fun updateWindowManager() {
            val service = instance ?: return
            Handler(Looper.getMainLooper()).post { service.launcher.applyWindowManagerSelection("settings_changed") }
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
                    service.taskRouting.launchedAppBounds[packageName] = fittedBounds
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
                it.physicalInput.physicalMouseActive -> "mouse"
                it.directTouch -> "touch"
                else -> "trackpad"
            }
        } ?: "idle"

        fun topologyOverlayDisplayId(): Int = instance?.mirror?.mirrorDisplayId ?: -1

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
            instance?.let { service -> Handler(Looper.getMainLooper()).post { service.externalOutputs.reapplyExternalScale() } }
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
                        service.touchSurface.trackpad(
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
                    service.displayFactory.replaceAutoDestinationSurface(surface)
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
            instance?.demoWindow?.showDemoWindow()
        }

        fun hideOverlayDemo() {
            instance?.demoWindow?.hideDemoWindow()
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
    private val mirror: MirrorLink = MirrorLink(this, object : MirrorLinkHost, DesktopSession by session, OverlayMenu by overlayMenu {
        override var autoDestinationSurface: Surface?
            get() = this@MirrorService.displayFactory.autoDestinationSurface
            set(value) { this@MirrorService.displayFactory.autoDestinationSurface = value }
        override var autoOwnedDisplay: OwnedVirtualDisplay?
            get() = this@MirrorService.displayFactory.autoOwnedDisplay
            set(value) { this@MirrorService.displayFactory.autoOwnedDisplay = value }
        override fun closeExtendSink(): Unit = this@MirrorService.externalOutputs.closeExtendSink()
        override val cursorView: CursorView? get() = this@MirrorService.cursorView
        override val cursorX: Float get() = this@MirrorService.cursorX
        override val cursorY: Float get() = this@MirrorService.cursorY
        override fun directDisplaySurface(): Surface = this@MirrorService.displayFactory.directDisplaySurface()
        override val displayBackend: DisplayMirrorBackend get() = this@MirrorService.displayBackend
        override var displayCreationInProgress: Boolean
            get() = this@MirrorService.displayFactory.displayCreationInProgress
            set(value) { this@MirrorService.displayFactory.displayCreationInProgress = value }
        override fun displayGeometrySnapshot(reason: String, hostWidth: Int?, hostHeight: Int?): String = this@MirrorService.displayGeometrySnapshot(reason, hostWidth, hostHeight)
        override val phoneOwnedDisplay: OwnedVirtualDisplay? get() = this@MirrorService.displayFactory.phoneOwnedDisplay
        override val phoneRegion: Rect? get() = this@MirrorService.externalOutputs.phoneRegion
        override fun stopRawMouseReader(): Unit = this@MirrorService.physicalInput.stopRawMouseReader()
        override fun updateVirtualCursorVisibility(): Unit = this@MirrorService.updateVirtualCursorVisibility()
        override val virtualPointer: VirtualPointer get() = this@MirrorService.virtualPointer
    })

    private val desktopEnvironment by lazy {
        if (mirror.useLegacyPixelMirrorProfile) {
            DesktopEnvironmentRegistry.legacyPixelAosp(DeviceIdentity.current())
        } else {
            DesktopEnvironmentRegistry.current()
        }
    }
    private val privilegedAccess by lazy { PrivilegedAccess(logTag) }
    private val desktopModeConfigurator by lazy {
        DesktopModeConfigurator(this, contentResolver, privilegedAccess, desktopEnvironment, sessionJournal)
    }
    private val workspaces: Workspaces = Workspaces(this, object : WorkspacesHost, DesktopSession by session, OverlayMenu by overlayMenu {
        override fun currentDisplayTasks(): Map<String, Rect> = this@MirrorService.taskRouting.currentDisplayTasks()
        override val launchedAppBounds: MutableMap<String, Rect> get() = this@MirrorService.taskRouting.launchedAppBounds
        override fun menuUsesLandscapeLayout(): Boolean = this@MirrorService.menuUsesLandscapeLayout()
        override fun packageName(): String = this@MirrorService.packageName()
        override fun placePackageTaskThroughBinder(displayId: Int, packageName: String, wanted: Rect): Boolean = this@MirrorService.taskRouting.placePackageTaskThroughBinder(displayId, packageName, wanted)
        override val recentsExecutor: java.util.concurrent.ExecutorService get() = this@MirrorService.recentsExecutor
        override fun usesDecoratedDirectAutoDisplay(): Boolean = this@MirrorService.taskRouting.usesDecoratedDirectAutoDisplay()
    })

    private val resolutionRepository by lazy { ResolutionRepository(this, logTag) }
    private val laptopTheme: LaptopThemeSettings = LaptopThemeSettings(this, object : LaptopThemeSettingsHost, DesktopSession by session, OverlayMenu by overlayMenu {
        override val laptopDeckContent: LinearLayout? get() = this@MirrorService.keyboardDeck.laptopDeckContent
        override var laptopLanguagePopup: View?
            get() = this@MirrorService.keyboardDeck.laptopLanguagePopup
            set(value) { this@MirrorService.keyboardDeck.laptopLanguagePopup = value }
        override val laptopPreviewThemeId: String? get() = this@MirrorService.keyboardDeck.laptopPreviewThemeId
        override var laptopSettingsVisible: Boolean
            get() = this@MirrorService.keyboardDeck.laptopSettingsVisible
            set(value) { this@MirrorService.keyboardDeck.laptopSettingsVisible = value }
        override val laptopTypeface: Typeface get() = this@MirrorService.keyboardDeck.laptopTypeface
        override fun rebuildLaptopDeck(showSettings: Boolean): Unit = this@MirrorService.keyboardDeck.rebuildLaptopDeck(showSettings)
        override val swipeTyping: SwipeTyping get() = this@MirrorService.swipeTyping
    })

    private val inputDispatcher by lazy {
        InputDispatcher(privilegedAccess) { event, displayId, accepted, failure ->
            recordInputDispatch(event, displayId, accepted, failure)
        }
    }
    private val physicalInput: PhysicalInput = PhysicalInput(this, object : PhysicalInputHost, DesktopSession by session, OverlayMenu by overlayMenu {
        override fun activateTouchInput(): Unit = this@MirrorService.activateTouchInput()
        override fun clearLaptopKeyboardPublication(reason: String): Unit = this@MirrorService.keyboardDeck.clearLaptopKeyboardPublication(reason)
        override fun currentInputMode(): String = this@MirrorService.currentInputMode()
        override val cursorView: CursorView? get() = this@MirrorService.cursorView
        override var cursorX: Float
            get() = this@MirrorService.cursorX
            set(value) { this@MirrorService.cursorX = value }
        override var cursorY: Float
            get() = this@MirrorService.cursorY
            set(value) { this@MirrorService.cursorY = value }
        override fun displayGeometrySnapshot(reason: String, hostWidth: Int?, hostHeight: Int?): String = this@MirrorService.displayGeometrySnapshot(reason, hostWidth, hostHeight)
        override fun finishAllLaptopKeyPresses(): Unit = this@MirrorService.keyboardDeck.finishAllLaptopKeyPresses()
        override fun forwardMouseEvent(source: MotionEvent, view: View): Boolean = this@MirrorService.touchSurface.forwardMouseEvent(source, view)
        override val inputDispatcher: InputDispatcher get() = this@MirrorService.inputDispatcher
        override val laptopKeyboardDeviceId: Int get() = this@MirrorService.keyboardDeck.laptopKeyboardDeviceId
        override val laptopKeyboardGeneration: Long get() = this@MirrorService.keyboardDeck.laptopKeyboardGeneration
        override val laptopKeyboardRequested: Boolean get() = this@MirrorService.keyboardDeck.laptopKeyboardRequested
        override val overlayTextInputActive: Boolean get() = this@MirrorService.textInput.overlayTextInputActive
        override val phoneOwnedDisplay: OwnedVirtualDisplay? get() = this@MirrorService.displayFactory.phoneOwnedDisplay
        override val physicalExternalDisplayConnected: Boolean get() = this@MirrorService.externalOutputs.physicalExternalDisplayConnected
        override val rawTouchscreen: RawTouchscreenBridge get() = this@MirrorService.rawTouchscreen
        override fun scheduleLaptopKeyboardReadyCheck(generation: Long, attempt: Int): Unit = this@MirrorService.keyboardDeck.scheduleLaptopKeyboardReadyCheck(generation, attempt)
        override fun setOverlayFocusable(focusable: Boolean): Unit = this@MirrorService.textInput.setOverlayFocusable(focusable)
        override fun updateVirtualCursorVisibility(): Unit = this@MirrorService.updateVirtualCursorVisibility()
        override val virtualPointer: VirtualPointer get() = this@MirrorService.virtualPointer
    })

    private val externalOutputs: ExternalDisplays = ExternalDisplays(this, object : ExternalDisplaysHost, DesktopSession by session, OverlayMenu by overlayMenu {
        override fun activateTouchInput(): Unit = this@MirrorService.activateTouchInput()
        override fun applyHostDisplayOrientation(portrait: Boolean): Unit = this@MirrorService.applyHostDisplayOrientation(portrait)
        override val cursorView: CursorView? get() = this@MirrorService.cursorView
        override var cursorX: Float
            get() = this@MirrorService.cursorX
            set(value) { this@MirrorService.cursorX = value }
        override var cursorY: Float
            get() = this@MirrorService.cursorY
            set(value) { this@MirrorService.cursorY = value }
        override fun externalOutputDisplay(): Display? = this@MirrorService.externalOutputDisplay()
        override val mirror: MirrorLink get() = this@MirrorService.mirror
        override val phoneGuard: PhoneGuard get() = this@MirrorService.phoneGuard
        override val physicalInput: PhysicalInput get() = this@MirrorService.physicalInput
        override fun reattachExistingDisplay(width: Int, height: Int): Unit = this@MirrorService.displayFactory.reattachExistingDisplay(width, height)
        override val requestedPortrait: Boolean get() = this@MirrorService.requestedPortrait
        override fun resizeActiveDisplay(next: Config, reason: String): Unit = this@MirrorService.hostFollower.resizeActiveDisplay(next, reason)
        override val rootWindowParams: WindowManager.LayoutParams? get() = this@MirrorService.rootWindowParams
        override fun setLaptopMode(enabled: Boolean, requestedStyle: KeyboardDeckStyle): Unit = this@MirrorService.laptopModeSwitch.setLaptopMode(enabled, requestedStyle)
        override val surfaceCallback: SurfaceHolder.Callback get() = this@MirrorService.surfaceCallback
        override fun surfaceCreated(holder: SurfaceHolder): Unit = this@MirrorService.surfaceCreated(holder)
        override fun updateCursorPosition(x: Float, y: Float): Unit = this@MirrorService.updateCursorPosition(x, y)
        override var surfaceView: SurfaceView?
            get() = this@MirrorService.surfaceView
            set(value) { this@MirrorService.surfaceView = value }
    })

    private val coverMenu: CoverDisplayMenu = CoverDisplayMenu(this, object : CoverDisplayMenuHost, DesktopSession by session, OverlayMenu by overlayMenu {
        override val cast: CastController get() = this@MirrorService.cast
        override val gamepad: VirtualGamepad get() = this@MirrorService.gamepad
        override val hostFollower: HostDisplayFollower get() = this@MirrorService.hostFollower
        override fun internalDisplays(manager: DisplayManager): List<Display> = this@MirrorService.internalDisplays(manager)
        override fun isFoldableDevice(): Boolean = this@MirrorService.isFoldableDevice()
        override fun isFoldableMainDisplay(): Boolean = this@MirrorService.isFoldableMainDisplay()
        override val menuWidgets: MenuWidgets get() = this@MirrorService.menuWidgets
        override val posture: LaptopPosture get() = this@MirrorService.posture
    })

    private val sessionJournal by lazy { SessionJournal(this) }
    private val hostFollower: HostDisplayFollower = HostDisplayFollower(this, object : HostDisplayFollowerHost, DesktopSession by session, OverlayMenu by overlayMenu {
        override fun applyHostDisplayOrientation(portrait: Boolean): Unit = this@MirrorService.applyHostDisplayOrientation(portrait)
        override val coverDisplayController: CoverDisplaySessionController get() = this@MirrorService.coverMenu.coverDisplayController
        override var cursorX: Float
            get() = this@MirrorService.cursorX
            set(value) { this@MirrorService.cursorX = value }
        override var cursorY: Float
            get() = this@MirrorService.cursorY
            set(value) { this@MirrorService.cursorY = value }
        override fun defaultDisplayUniqueId(): String? = this@MirrorService.defaultDisplayUniqueId()
        override val displayFactory: DisplayFactory get() = this@MirrorService.displayFactory
        override fun displayGeometrySnapshot(reason: String, hostWidth: Int?, hostHeight: Int?): String = this@MirrorService.displayGeometrySnapshot(reason, hostWidth, hostHeight)
        override val externalOutputs: ExternalDisplays get() = this@MirrorService.externalOutputs
        override fun isFoldableDevice(): Boolean = this@MirrorService.isFoldableDevice()
        override fun isFoldableMainDisplay(): Boolean = this@MirrorService.isFoldableMainDisplay()
        override fun isWorkspaceMagnificationActive(): Boolean = this@MirrorService.isWorkspaceMagnificationActive()
        override val keyboardDeck: KeyboardDeck get() = this@MirrorService.keyboardDeck
        override val laptopModeSwitch: LaptopMode get() = this@MirrorService.laptopModeSwitch
        override val launcher: DesktopLauncher get() = this@MirrorService.launcher
        override val lockScreen: LockScreenGuard get() = this@MirrorService.lockScreen
        override fun menuLayoutParams(): FrameLayout.LayoutParams = this@MirrorService.menuLayoutParams()
        override fun menuUsesLandscapeLayout(): Boolean = this@MirrorService.menuUsesLandscapeLayout()
        override val mirror: MirrorLink get() = this@MirrorService.mirror
        override val phoneGuard: PhoneGuard get() = this@MirrorService.phoneGuard
        override val posture: LaptopPosture get() = this@MirrorService.posture
        override val rawTouchscreen: RawTouchscreenBridge get() = this@MirrorService.rawTouchscreen
        override val requestedPortrait: Boolean get() = this@MirrorService.requestedPortrait
        override fun scheduleCoverDisplayLifecycleCheck(reason: String): Unit = this@MirrorService.coverMenu.scheduleCoverDisplayLifecycleCheck(reason)
        override val stopping: Boolean get() = this@MirrorService.stopping
        override fun systemService(name: String, interfaceName: String): Any = this@MirrorService.systemService(name, interfaceName)
        override val touchSurface: TouchSurface get() = this@MirrorService.touchSurface
        override fun updateCursorPosition(x: Float, y: Float): Unit = this@MirrorService.updateCursorPosition(x, y)
        override val virtualPointer: VirtualPointer get() = this@MirrorService.virtualPointer
        override val workspaces: Workspaces get() = this@MirrorService.workspaces
        override var density: Int
            get() = this@MirrorService.density
            set(value) { this@MirrorService.density = value }
        override var targetHeight: Int
            get() = this@MirrorService.targetHeight
            set(value) { this@MirrorService.targetHeight = value }
        override var targetWidth: Int
            get() = this@MirrorService.targetWidth
            set(value) { this@MirrorService.targetWidth = value }
    })

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
        override fun cancelDesktopTouchStream(): Unit = this@MirrorService.touchSurface.cancelDesktopTouchStream()
        override var cursorX: Float
            get() = this@MirrorService.cursorX
            set(value) { this@MirrorService.cursorX = value }
        override var cursorY: Float
            get() = this@MirrorService.cursorY
            set(value) { this@MirrorService.cursorY = value }
        override val inputGestureHandler: Handler get() = this@MirrorService.touchSurface.inputGestureHandler
        override fun refreshPrivilegedInputConfig(reason: String): Unit = this@MirrorService.refreshPrivilegedInputConfig(reason)
        override val virtualPointer: VirtualPointer get() = this@MirrorService.virtualPointer
    })

    private var menu: LinearLayout? = null
    private var menuPrimary: LinearLayout? = null
    private var overlayLayoutEditing = false
    private val taskRouting: TaskRouting = TaskRouting(this, object : TaskRoutingHost, DesktopSession by session, OverlayMenu by overlayMenu {
        override val displayFactory: DisplayFactory get() = this@MirrorService.displayFactory
        override val forceFreeformSession: Boolean get() = this@MirrorService.forceFreeformSession
        override val recents: RecentsController get() = this@MirrorService.recents
        override val recentsExecutor: java.util.concurrent.ExecutorService get() = this@MirrorService.recentsExecutor
        override val workspaces: Workspaces get() = this@MirrorService.workspaces
    })

    private val androidPause: AndroidPause = AndroidPause(this, object : AndroidPauseHost, DesktopSession by session, OverlayMenu by overlayMenu {
        override val coverMenu: CoverDisplayMenu get() = this@MirrorService.coverMenu
        override val desktopModeConfigurator: DesktopModeConfigurator get() = this@MirrorService.desktopModeConfigurator
        override fun detachHostWindow(): Unit = this@MirrorService.detachHostWindow()
        override val displayFactory: DisplayFactory get() = this@MirrorService.displayFactory
        override val hostFollower: HostDisplayFollower get() = this@MirrorService.hostFollower
        override val lockScreen: LockScreenGuard get() = this@MirrorService.lockScreen
        override val mirror: MirrorLink get() = this@MirrorService.mirror
        override var overlayLayoutEditing: Boolean
            get() = this@MirrorService.overlayLayoutEditing
            set(value) { this@MirrorService.overlayLayoutEditing = value }
        override val phoneGuard: PhoneGuard get() = this@MirrorService.phoneGuard
        override var stopping: Boolean
            get() = this@MirrorService.stopping
            set(value) { this@MirrorService.stopping = value }
        override val workspaces: Workspaces get() = this@MirrorService.workspaces
        override var targetDisplayId: Int
            get() = this@MirrorService.targetDisplayId
            set(value) { this@MirrorService.targetDisplayId = value }
    })

    private var stopping = false

    /**
     * Teardown is deliberately split into two phases.  Samsung's overlay
     * adapter removes the display asynchronously; restoring phone/DeX state
     * before that removal has completed races WindowManager/SystemUI.
     */
    private var stopCleanupGeneration = 0L
    private var menuScrim: View? = null
    private var demoMode = false
    private val demoWindow: DemoWindow = DemoWindow(this, object : DemoWindowHost, DesktopSession by session, OverlayMenu by overlayMenu {
        override fun buildMenu(): LinearLayout = this@MirrorService.buildMenu()
        override val keyboardDeck: KeyboardDeck get() = this@MirrorService.keyboardDeck
        override fun menuLayoutParams(): FrameLayout.LayoutParams = this@MirrorService.menuLayoutParams()
        override var menuScrim: View?
            get() = this@MirrorService.menuScrim
            set(value) { this@MirrorService.menuScrim = value }
        override fun menuUsesLandscapeLayout(): Boolean = this@MirrorService.menuUsesLandscapeLayout()
        override fun removeWindow(): Unit = this@MirrorService.removeWindow()
        override var rootWindowParams: WindowManager.LayoutParams?
            get() = this@MirrorService.rootWindowParams
            set(value) { this@MirrorService.rootWindowParams = value }
        override var demoMode: Boolean
            get() = this@MirrorService.demoMode
            set(value) { this@MirrorService.demoMode = value }
        override var density: Int
            get() = this@MirrorService.density
            set(value) { this@MirrorService.density = value }
        override var laptopModeActive: Boolean
            get() = this@MirrorService.laptopModeActive
            set(value) { this@MirrorService.laptopModeActive = value }
        override var menu: LinearLayout?
            get() = this@MirrorService.menu
            set(value) { this@MirrorService.menu = value }
        override var root: TouchRoutingFrame?
            get() = this@MirrorService.root
            set(value) { this@MirrorService.root = value }
        override var targetHeight: Int
            get() = this@MirrorService.targetHeight
            set(value) { this@MirrorService.targetHeight = value }
        override var targetWidth: Int
            get() = this@MirrorService.targetWidth
            set(value) { this@MirrorService.targetWidth = value }
    })

    private var performanceHud: PerformanceHud? = null
    private val keyboardDeck: KeyboardDeck = KeyboardDeck(this, object : KeyboardDeckHost, DesktopSession by session, OverlayMenu by overlayMenu {
        override fun activateLaptopTrackpad(): Unit = this@MirrorService.laptopModeSwitch.activateLaptopTrackpad()
        override fun applyLaptopGeometryWhenLaidOut(enabled: Boolean, attempt: Int, baseOverride: Config?, preserveMenu: Boolean): Unit = this@MirrorService.laptopModeSwitch.applyLaptopGeometryWhenLaidOut(enabled, attempt, baseOverride, preserveMenu)
        override val cast: CastController get() = this@MirrorService.cast
        override fun configForHostGeometry(base: Config, hostWidth: Int, hostHeight: Int, systemDensity: Int): Config = this@MirrorService.hostFollower.configForHostGeometry(base, hostWidth, hostHeight, systemDensity)
        override val cursorView: CursorView? get() = this@MirrorService.cursorView
        override val gamepad: VirtualGamepad get() = this@MirrorService.gamepad
        override val haptics: Haptics get() = this@MirrorService.haptics
        override fun hasKeyboardStyleChoice(): Boolean = this@MirrorService.laptopModeSwitch.hasKeyboardStyleChoice()
        override fun injectKey(keyCode: Int, metaState: Int): Unit = this@MirrorService.touchSurface.injectKey(keyCode, metaState)
        override val inputDispatcher: InputDispatcher get() = this@MirrorService.inputDispatcher
        override val keyboardDeckStyle: MirrorService.KeyboardDeckStyle get() = this@MirrorService.keyboardDeckStyle
        override val laptopModifiers: LaptopModifierState get() = this@MirrorService.laptopModifiers
        override val laptopTheme: LaptopThemeSettings get() = this@MirrorService.laptopTheme
        override val menuWidgets: MenuWidgets get() = this@MirrorService.menuWidgets
        override val mirror: MirrorLink get() = this@MirrorService.mirror
        override val physicalInput: PhysicalInput get() = this@MirrorService.physicalInput
        override val posture: LaptopPosture get() = this@MirrorService.posture
        override fun resizeActiveDisplay(next: Config, reason: String): Unit = this@MirrorService.hostFollower.resizeActiveDisplay(next, reason)
        override fun setLaptopMode(enabled: Boolean, requestedStyle: KeyboardDeckStyle): Unit = this@MirrorService.laptopModeSwitch.setLaptopMode(enabled, requestedStyle)
        override fun showKeyboardStyleMenu(panel: LinearLayout): Unit = this@MirrorService.laptopModeSwitch.showKeyboardStyleMenu(panel)
        override val swipeTyping: SwipeTyping get() = this@MirrorService.swipeTyping
        override fun trackpad(event: MotionEvent, sourceView: View, forceCursorMode: Boolean, allowVirtualPointer: Boolean, hapticView: View?): Boolean = this@MirrorService.touchSurface.trackpad(event, sourceView, forceCursorMode, allowVirtualPointer, hapticView)
    })

    private val swipeTyping: SwipeTyping = SwipeTyping(this, object : SwipeTypingHost, DesktopSession by session, OverlayMenu by overlayMenu {
        override val haptics: Haptics get() = this@MirrorService.haptics
        override fun injectLaptopKeyboardEvent(keyCode: Int, action: Int, metaState: Int, repeatCount: Int): Boolean = this@MirrorService.keyboardDeck.injectLaptopKeyboardEvent(keyCode, action, metaState, repeatCount)
        override val laptopKeyboardReady: Boolean get() = this@MirrorService.keyboardDeck.laptopKeyboardReady
        override val laptopKeyboardView: SwipeObservingKeyboardLayout? get() = this@MirrorService.keyboardDeck.laptopKeyboardView
        override val laptopLegendButtons: MutableList<Pair<LaptopKeyTextView, String>> get() = this@MirrorService.keyboardDeck.laptopLegendButtons
        override fun laptopPalette(): LaptopPalette = this@MirrorService.laptopTheme.laptopPalette()
        override val laptopTypeface: Typeface get() = this@MirrorService.keyboardDeck.laptopTypeface
        override fun opacityColor(color: Int, opacity: Float): Int = this@MirrorService.laptopTheme.opacityColor(color, opacity)
        override fun opaqueColor(color: Int): Int = this@MirrorService.laptopTheme.opaqueColor(color)
    })

    private val imeRegion: ImeRegionProbe = ImeRegionProbe(this, object : ImeRegionProbeHost, DesktopSession by session, OverlayMenu by overlayMenu {
        override fun rawTouchscreenViewBounds(view: View?): Rect? = this@MirrorService.rawTouchscreen.rawTouchscreenViewBounds(view)
        override fun refreshPrivilegedInputConfig(reason: String) = this@MirrorService.refreshPrivilegedInputConfig(reason)
        override fun regionFor(view: View?): RectF = this@MirrorService.externalOutputs.regionFor(view)
    })

    internal enum class KeyboardDeckStyle { LAPTOP, BLACKBERRY, GAMEPAD, GAMEBOY }
    private var keyboardDeckStyle = KeyboardDeckStyle.LAPTOP
    private val gamepad: VirtualGamepad = VirtualGamepad(this, object : VirtualGamepadHost, DesktopSession by session, OverlayMenu by overlayMenu {
        override fun blackBerryActionRow(mainAction: View, edit: () -> Unit): LinearLayout = this@MirrorService.keyboardDeck.blackBerryActionRow(mainAction, edit)
        override val cast: CastController get() = this@MirrorService.cast
        override val coverBackButtonsActive: Boolean get() = this@MirrorService.coverButtons.coverBackButtonsActive
        override val haptics: Haptics get() = this@MirrorService.haptics
        override fun hasKeyboardStyleChoice(): Boolean = this@MirrorService.laptopModeSwitch.hasKeyboardStyleChoice()
        override fun isBlackBerryModeAvailable(): Boolean = this@MirrorService.laptopModeSwitch.isBlackBerryModeAvailable()
        override fun isLaptopStyleAvailable(): Boolean = this@MirrorService.laptopModeSwitch.isLaptopStyleAvailable()
        override val keyboardDeckStyle: MirrorService.KeyboardDeckStyle get() = this@MirrorService.keyboardDeckStyle
        override val laptopTypeface: Typeface get() = this@MirrorService.keyboardDeck.laptopTypeface
        override fun opacityColor(color: Int, opacity: Float): Int = this@MirrorService.laptopTheme.opacityColor(color, opacity)
        override fun setLaptopMode(enabled: Boolean, requestedStyle: KeyboardDeckStyle) = this@MirrorService.laptopModeSwitch.setLaptopMode(enabled, requestedStyle)
        override fun showKeyboardStyleMenu(panel: LinearLayout) = this@MirrorService.laptopModeSwitch.showKeyboardStyleMenu(panel)
    })

    private var laptopModeActive = false
    private val lockScreen: LockScreenGuard = LockScreenGuard(this, object : LockScreenGuardHost, DesktopSession by session, OverlayMenu by overlayMenu {
        override fun configForHostGeometry(base: Config, hostWidth: Int, hostHeight: Int, systemDensity: Int): Config = this@MirrorService.hostFollower.configForHostGeometry(base, hostWidth, hostHeight, systemDensity)
        override val desktopModeConfigurator: DesktopModeConfigurator get() = this@MirrorService.desktopModeConfigurator
        override val externalOutput: ExternalOutput? get() = this@MirrorService.externalOutputs.externalOutput
        override val keyboardDeckStyle: MirrorService.KeyboardDeckStyle get() = this@MirrorService.keyboardDeckStyle
        override val laptopContent: LinearLayout? get() = this@MirrorService.keyboardDeck.laptopContent
        override val phoneGuard: PhoneGuard get() = this@MirrorService.phoneGuard
        override fun removeWindow(): Unit = this@MirrorService.removeWindow()
        override fun setLaptopMode(enabled: Boolean, requestedStyle: KeyboardDeckStyle): Unit = this@MirrorService.laptopModeSwitch.setLaptopMode(enabled, requestedStyle)
        override fun shouldFollowHostDisplay(): Boolean = this@MirrorService.hostFollower.shouldFollowHostDisplay()
        override fun start(config: Config): Unit = this@MirrorService.start(config)
        override fun stopHostDisplayMonitor(): Unit = this@MirrorService.hostFollower.stopHostDisplayMonitor()
        override var targetDisplayId: Int
            get() = this@MirrorService.targetDisplayId
            set(value) { this@MirrorService.targetDisplayId = value }
    })


    /** Dextop orientation choice, independent from the laptop pane geometry. */
    private var requestedPortrait = false
    private val laptopModeSwitch: LaptopMode = LaptopMode(this, object : LaptopModeHost, DesktopSession by session, OverlayMenu by overlayMenu {
        override val cast: CastController get() = this@MirrorService.cast
        override fun configForHostGeometry(base: Config, hostWidth: Int, hostHeight: Int, systemDensity: Int): Config = this@MirrorService.hostFollower.configForHostGeometry(base, hostWidth, hostHeight, systemDensity)
        override val coverButtons: CoverBackButtons get() = this@MirrorService.coverButtons
        override val cursorView: CursorView? get() = this@MirrorService.cursorView
        override val cursorX: Float get() = this@MirrorService.cursorX
        override val cursorY: Float get() = this@MirrorService.cursorY
        override fun defaultDisplayUniqueId(): String? = this@MirrorService.defaultDisplayUniqueId()
        override val externalOutputs: ExternalDisplays get() = this@MirrorService.externalOutputs
        override val gamepad: VirtualGamepad get() = this@MirrorService.gamepad
        override fun isCurrentFoldableCoverDisplay(): Boolean = this@MirrorService.coverMenu.isCurrentFoldableCoverDisplay()
        override fun isFoldableDevice(): Boolean = this@MirrorService.isFoldableDevice()
        override fun isMenuOverlayVisible(): Boolean = this@MirrorService.isMenuOverlayVisible()
        override val keyboardDeck: KeyboardDeck get() = this@MirrorService.keyboardDeck
        override var keyboardDeckStyle: MirrorService.KeyboardDeckStyle
            get() = this@MirrorService.keyboardDeckStyle
            set(value) { this@MirrorService.keyboardDeckStyle = value }
        override val laptopTheme: LaptopThemeSettings get() = this@MirrorService.laptopTheme
        override fun laptopTrackpadInputActive(): Boolean = this@MirrorService.laptopTrackpadInputActive()
        override val lockScreen: LockScreenGuard get() = this@MirrorService.lockScreen
        override val menuScrim: View? get() = this@MirrorService.menuScrim
        override val menuWidgets: MenuWidgets get() = this@MirrorService.menuWidgets
        override val mirror: MirrorLink get() = this@MirrorService.mirror
        override val overlayTextInputActive: Boolean get() = this@MirrorService.textInput.overlayTextInputActive
        override val performanceHud: PerformanceHud? get() = this@MirrorService.performanceHud
        override val physicalInput: PhysicalInput get() = this@MirrorService.physicalInput
        override val posture: LaptopPosture get() = this@MirrorService.posture
        override val rawTouchscreen: RawTouchscreenBridge get() = this@MirrorService.rawTouchscreen
        override fun refreshPrivilegedInputConfig(reason: String): Unit = this@MirrorService.refreshPrivilegedInputConfig(reason)
        override val requestedPortrait: Boolean get() = this@MirrorService.requestedPortrait
        override fun resizeActiveDisplay(next: Config, reason: String): Unit = this@MirrorService.hostFollower.resizeActiveDisplay(next, reason)
        override fun setOverlayFocusable(focusable: Boolean): Unit = this@MirrorService.textInput.setOverlayFocusable(focusable)
        override val swipeTyping: SwipeTyping get() = this@MirrorService.swipeTyping
        override val touchSurface: TouchSurface get() = this@MirrorService.touchSurface
        override fun updateCursorPosition(x: Float, y: Float): Unit = this@MirrorService.updateCursorPosition(x, y)
        override fun updateVirtualCursorVisibility(): Unit = this@MirrorService.updateVirtualCursorVisibility()
        override val virtualPointer: VirtualPointer get() = this@MirrorService.virtualPointer
        override var directTouch: Boolean
            get() = this@MirrorService.directTouch
            set(value) { this@MirrorService.directTouch = value }
        override var laptopModeActive: Boolean
            get() = this@MirrorService.laptopModeActive
            set(value) { this@MirrorService.laptopModeActive = value }
    })

    private var privilegedInputStarting = false
    private var privilegedPointerFallbackInProgress = false
    private var privilegedPointerFallbackActive = false
    private var privilegedInputConfigGeneration = 0
    private var lastPrivilegedInputSemanticConfig: IntArray? = null
    private val virtualPointer: VirtualPointer = VirtualPointer(this, object : VirtualPointerHost, DesktopSession by session, OverlayMenu by overlayMenu {
        override fun buildPrivilegedInputConfig(profile: String): IntArray = this@MirrorService.buildPrivilegedInputConfig(profile)
        override fun cancelDesktopTouchStream(): Unit = this@MirrorService.touchSurface.cancelDesktopTouchStream()
        override val carCompanionPhoneTrackpadSurface: View? get() = this@MirrorService.carCompanion.carCompanionPhoneTrackpadSurface
        override val coverTrackpadActive: Boolean get() = this@MirrorService.coverButtons.coverTrackpadActive
        override fun displayGeometrySnapshot(reason: String, hostWidth: Int?, hostHeight: Int?): String = this@MirrorService.displayGeometrySnapshot(reason, hostWidth, hostHeight)
        override val externalOutput: ExternalOutput? get() = this@MirrorService.externalOutputs.externalOutput
        override fun fallbackToSoftwarePointer(reason: String): Unit = this@MirrorService.fallbackToSoftwarePointer(reason)
        override val laptopKeyboardRequested: Boolean get() = this@MirrorService.keyboardDeck.laptopKeyboardRequested
        override fun logSuppressedRelativeTouchpadEvent(kind: String): Unit = this@MirrorService.logSuppressedRelativeTouchpadEvent(kind)
        override val physicalInputRouter: PhysicalInputRouter get() = this@MirrorService.physicalInput.physicalInputRouter
        override var privilegedInputStarting: Boolean
            get() = this@MirrorService.privilegedInputStarting
            set(value) { this@MirrorService.privilegedInputStarting = value }
        override fun refreshPrivilegedInputConfig(reason: String): Unit = this@MirrorService.refreshPrivilegedInputConfig(reason)
        override fun startRawTouchscreenReaderIfEligible(): Unit = this@MirrorService.rawTouchscreen.startRawTouchscreenReaderIfEligible()
        override fun stopRawTouchscreenReader(reason: String): Unit = this@MirrorService.rawTouchscreen.stopRawTouchscreenReader(reason)
        override fun updateVirtualCursorVisibility(): Unit = this@MirrorService.updateVirtualCursorVisibility()
        override val virtualTouchpad: VirtualTouchpad get() = this@MirrorService.virtualTouchpad
    })



    private val virtualTouchpad: VirtualTouchpad = VirtualTouchpad(this, object : VirtualTouchpadHost, DesktopSession by session, OverlayMenu by overlayMenu {
        override var nativeTouchpadGestureActive: Boolean
            get() = this@MirrorService.touchSurface.nativeTouchpadGestureActive
            set(value) { this@MirrorService.touchSurface.nativeTouchpadGestureActive = value }
        override fun virtualMouseEvents(events: List<Any>, allowDirectTouch: Boolean): Boolean = this@MirrorService.virtualPointer.virtualMouseEvents(events, allowDirectTouch)
        override fun virtualMouseProcessAlive(): Boolean = this@MirrorService.virtualPointer.virtualMouseProcessAlive()
        override val virtualMouseReady: Boolean get() = this@MirrorService.virtualPointer.virtualMouseReady
        override fun virtualPointerInputActive(allowDirectTouch: Boolean): Boolean = this@MirrorService.virtualPointer.virtualPointerInputActive(allowDirectTouch)
        override val virtualPointerRegisteredProfile: String get() = this@MirrorService.virtualPointer.virtualPointerRegisteredProfile
    })

    private val touchSurface: TouchSurface = TouchSurface(this, object : TouchSurfaceHost, DesktopSession by session, OverlayMenu by overlayMenu {
        override val carCompanion: CarCompanionInput get() = this@MirrorService.carCompanion
        override fun changeOrientation(): Unit = this@MirrorService.changeOrientation()
        override val cursorView: CursorView? get() = this@MirrorService.cursorView
        override var cursorX: Float
            get() = this@MirrorService.cursorX
            set(value) { this@MirrorService.cursorX = value }
        override var cursorY: Float
            get() = this@MirrorService.cursorY
            set(value) { this@MirrorService.cursorY = value }
        override fun dedicatedTrackpadInputActive(): Boolean = this@MirrorService.dedicatedTrackpadInputActive()
        override var edgeMenuTriggered: Boolean
            get() = this@MirrorService.edgeMenuTriggered
            set(value) { this@MirrorService.edgeMenuTriggered = value }
        override val externalOutputs: ExternalDisplays get() = this@MirrorService.externalOutputs
        override val haptics: Haptics get() = this@MirrorService.haptics
        override val imeRegion: ImeRegionProbe get() = this@MirrorService.imeRegion
        override val inputDispatcher: InputDispatcher get() = this@MirrorService.inputDispatcher
        override val keyboardDeck: KeyboardDeck get() = this@MirrorService.keyboardDeck
        override val launcher: DesktopLauncher get() = this@MirrorService.launcher
        override val physicalInput: PhysicalInput get() = this@MirrorService.physicalInput
        override val rawTouchscreen: RawTouchscreenBridge get() = this@MirrorService.rawTouchscreen
        override fun recordTouchRouting(event: MotionEvent, direct: Boolean): Unit = this@MirrorService.recordTouchRouting(event, direct)
        override fun stop(): Unit = this@MirrorService.stop()
        override var threeFingerEdgeSwipe: Boolean
            get() = this@MirrorService.threeFingerEdgeSwipe
            set(value) { this@MirrorService.threeFingerEdgeSwipe = value }
        override fun updateCursorPosition(x: Float, y: Float): Unit = this@MirrorService.updateCursorPosition(x, y)
        override val virtualPointer: VirtualPointer get() = this@MirrorService.virtualPointer
        override val virtualTouchpad: VirtualTouchpad get() = this@MirrorService.virtualTouchpad
    })

    private val laptopModifiers = LaptopModifierState()
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
    private val displayFactory: DisplayFactory = DisplayFactory(this, object : DisplayFactoryHost, DesktopSession by session, OverlayMenu by overlayMenu {
        override fun activateTopologyForIndependentDisplays(reason: String): Unit = this@MirrorService.hostFollower.activateTopologyForIndependentDisplays(reason)
        override fun applyLaptopGeometryWhenLaidOut(enabled: Boolean, attempt: Int, baseOverride: Config?, preserveMenu: Boolean): Unit = this@MirrorService.laptopModeSwitch.applyLaptopGeometryWhenLaidOut(enabled, attempt, baseOverride, preserveMenu)
        override val coverDisplayController: CoverDisplaySessionController get() = this@MirrorService.coverMenu.coverDisplayController
        override var cursorX: Float
            get() = this@MirrorService.cursorX
            set(value) { this@MirrorService.cursorX = value }
        override var cursorY: Float
            get() = this@MirrorService.cursorY
            set(value) { this@MirrorService.cursorY = value }
        override val desktopModeConfigurator: DesktopModeConfigurator get() = this@MirrorService.desktopModeConfigurator
        override val displayBackend: DisplayMirrorBackend get() = this@MirrorService.displayBackend
        override fun displayGeometrySnapshot(reason: String, hostWidth: Int?, hostHeight: Int?): String = this@MirrorService.displayGeometrySnapshot(reason, hostWidth, hostHeight)
        override val externalOutputs: ExternalDisplays get() = this@MirrorService.externalOutputs
        override val forceFreeformSession: Boolean get() = this@MirrorService.forceFreeformSession
        override val imeRegion: ImeRegionProbe get() = this@MirrorService.imeRegion
        override val launcher: DesktopLauncher get() = this@MirrorService.launcher
        override val mirror: MirrorLink get() = this@MirrorService.mirror
        override val phoneGuard: PhoneGuard get() = this@MirrorService.phoneGuard
        override val physicalInput: PhysicalInput get() = this@MirrorService.physicalInput
        override fun postMainDelayed(delayMs: Long, action: () -> Unit): Unit = this@MirrorService.postMainDelayed(delayMs, action)
        override val requestedPortrait: Boolean get() = this@MirrorService.requestedPortrait
        override fun scheduleHostDisplayReconfiguration(reason: String, width: Int?, height: Int?, densityDpi: Int?): Unit = this@MirrorService.hostFollower.scheduleHostDisplayReconfiguration(reason, width, height, densityDpi)
        override fun scheduleTopologyReapplyAfterReconnect(): Unit = this@MirrorService.hostFollower.scheduleTopologyReapplyAfterReconnect()
        override fun startAutoOverlapCorrection(): Unit = this@MirrorService.taskRouting.startAutoOverlapCorrection()
        override fun stop(): Unit = this@MirrorService.stop()
        override val stopping: Boolean get() = this@MirrorService.stopping
        override fun synchronizeDesktopWallpaperDimensions(width: Int, height: Int, reason: String): Unit = this@MirrorService.hostFollower.synchronizeDesktopWallpaperDimensions(width, height, reason)
        override fun systemService(name: String, interfaceName: String): Any = this@MirrorService.systemService(name, interfaceName)
        override fun updateCursorPosition(x: Float, y: Float): Unit = this@MirrorService.updateCursorPosition(x, y)
        override fun updateVirtualCursorVisibility(): Unit = this@MirrorService.updateVirtualCursorVisibility()
        override val virtualPointer: VirtualPointer get() = this@MirrorService.virtualPointer
        override val workspaces: Workspaces get() = this@MirrorService.workspaces
        override var showSystemDecorations: Boolean
            get() = this@MirrorService.showSystemDecorations
            set(value) { this@MirrorService.showSystemDecorations = value }
        override var targetDisplayId: Int
            get() = this@MirrorService.targetDisplayId
            set(value) { this@MirrorService.targetDisplayId = value }
    })


    private val launcher: DesktopLauncher = DesktopLauncher(this, object : DesktopLauncherHost, DesktopSession by session, OverlayMenu by overlayMenu {
        override val carGestures: CarGestures get() = this@MirrorService.carGestures
        override fun clearOverlayDisplayRequestTwice(reason: String): Unit = this@MirrorService.displayFactory.clearOverlayDisplayRequestTwice(reason)
        override val displayBackend: DisplayMirrorBackend get() = this@MirrorService.displayBackend
        override var displayCreationInProgress: Boolean
            get() = this@MirrorService.displayFactory.displayCreationInProgress
            set(value) { this@MirrorService.displayFactory.displayCreationInProgress = value }
        override val mirror: MirrorLink get() = this@MirrorService.mirror
        override val phoneOwnedDisplay: OwnedVirtualDisplay? get() = this@MirrorService.displayFactory.phoneOwnedDisplay
        override val plasmaHost: moe.n4tsu.dextop.plasma.PlasmaShell.Host get() = this@MirrorService.plasmaHost
        override fun postMainDelayed(delayMs: Long, action: () -> Unit): Unit = this@MirrorService.postMainDelayed(delayMs, action)
        override val serviceDestroyed: Boolean get() = this@MirrorService.serviceDestroyed
        override fun stop(): Unit = this@MirrorService.stop()
        override val stopping: Boolean get() = this@MirrorService.stopping
        override fun systemService(name: String, interfaceName: String): Any = this@MirrorService.systemService(name, interfaceName)
        override fun waitForOverlayRequestCleared(existing: Set<Int>, staleOverlays: Set<Int>, width: Int, height: Int, attempt: Int, decorations: Boolean): Unit = this@MirrorService.displayFactory.waitForOverlayRequestCleared(existing, staleOverlays, width, height, attempt, decorations)
        override var plasmaShell: moe.n4tsu.dextop.plasma.DesktopShell?
            get() = this@MirrorService.plasmaShell
            set(value) { this@MirrorService.plasmaShell = value }
        override var showSystemDecorations: Boolean
            get() = this@MirrorService.showSystemDecorations
            set(value) { this@MirrorService.showSystemDecorations = value }
        override var targetDisplayId: Int
            get() = this@MirrorService.targetDisplayId
            set(value) { this@MirrorService.targetDisplayId = value }
    })

    private val textInput: OverlayTextInput = OverlayTextInput(this, object : OverlayTextInputHost, DesktopSession by session, OverlayMenu by overlayMenu {
        override val rootWindowParams: WindowManager.LayoutParams? get() = this@MirrorService.rootWindowParams
        override fun systemService(name: String, interfaceName: String): Any = this@MirrorService.systemService(name, interfaceName)
    })

    private var cursorX = 960f
    private var cursorY = 540f
    private var directTouch = false
    private var threeFingerEdgeSwipe = false
    private var edgeMenuTriggered = false
    private val cast: CastController = CastController(this, object : CastControllerHost, DesktopSession by session, OverlayMenu by overlayMenu {
    })

    private val rawTouchscreen: RawTouchscreenBridge = RawTouchscreenBridge(this, object : RawTouchscreenBridgeHost, DesktopSession by session, OverlayMenu by overlayMenu {
        override fun buildPrivilegedInputConfig(profile: String): IntArray = this@MirrorService.buildPrivilegedInputConfig(profile)
        override val imeDirectTouchHeld: Boolean get() = this@MirrorService.touchSurface.imeDirectTouchHeld
        override val imeRegion: ImeRegionProbe get() = this@MirrorService.imeRegion
        override val laptopTrackpadView: View? get() = this@MirrorService.keyboardDeck.laptopTrackpadView
        override fun refreshPrivilegedInputConfig(reason: String): Unit = this@MirrorService.refreshPrivilegedInputConfig(reason)
        override fun updateVirtualCursorVisibility(): Unit = this@MirrorService.updateVirtualCursorVisibility()
        override val virtualPointer: VirtualPointer get() = this@MirrorService.virtualPointer
    })

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
                        rawTouchscreen.touchscreenReaderRunning = true
                        // A readable EventHub node is only a candidate.  Do
                        // not suppress the overlay's MotionEvent path until
                        // the native reader has received an actual contact.
                        rawTouchscreen.touchscreenReaderReady = false
                        rawTouchscreen.touchscreenReaderCandidateCount =
                            rawTouchscreen.touchscreenReaderCandidateCount.coerceAtLeast(1)
                        rawTouchscreen.touchscreenReaderDevice = message.substringAfter("path=", "")
                            .substringBefore(' ')
                    }

                    "source_selected" -> {
                        rawTouchscreen.touchscreenReaderRunning = true
                        rawTouchscreen.touchscreenReaderReady = true
                        virtualPointer.virtualPointerOutputObserved = true
                        rawTouchscreen.touchscreenReaderCandidateCount =
                            rawTouchscreen.touchscreenReaderCandidateCount.coerceAtLeast(1)
                        rawTouchscreen.touchscreenReaderDevice = message.substringAfter("path=", "")
                            .substringBefore(' ')
                        updateVirtualCursorVisibility()
                    }

                    "device_waiting" -> {
                        rawTouchscreen.touchscreenReaderRunning = true
                        rawTouchscreen.touchscreenReaderReady = false
                        rawTouchscreen.touchscreenReaderCandidateCount = 0
                    }

                    "keyboard_created" -> {
                        keyboardDeck.scheduleLaptopKeyboardReadyCheck(keyboardDeck.laptopKeyboardGeneration, 0)
                    }

                    "gamepad_created" -> gamepad.replayVirtualGamepadState()

                    "keyboard_destroyed" -> keyboardDeck.clearLaptopKeyboardPublication("native_destroyed")

                    "stopped" -> {
                        if (!privilegedInputClient.isEngineRunning()) {
                            privilegedInputStarting = false
                            virtualPointer.virtualMouseReady = false
                            virtualPointer.virtualPointerOutputObserved = false
                            rawTouchscreen.touchscreenReaderRunning = false
                            rawTouchscreen.touchscreenReaderReady = false
                            rawTouchscreen.touchscreenReaderCandidateCount = 0
                            updateVirtualCursorVisibility()
                        }
                    }

                    "disconnected", "worker_exited" -> {
                        privilegedInputClient.acknowledgeEngineStopped()
                        privilegedInputStarting = false
                        virtualPointer.virtualMouseReady = false
                        virtualPointer.virtualPointerOutputObserved = false
                        rawTouchscreen.touchscreenReaderRunning = false
                        rawTouchscreen.touchscreenReaderReady = false
                        rawTouchscreen.touchscreenReaderCandidateCount = 0
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
                touchSurface.performConfiguredGesture()
            }

            override fun onHaptic(strong: Boolean) {
                haptics.performLaptopHaptic(keyboardDeck.laptopTrackpadView, strong, HapticSource.POINTER)
            }
        })
    }
    private val privilegedInputClient: PrivilegedInputClient by privilegedInputClientDelegate
    private var inputManager: InputManager? = null
    private var sensorManager: SensorManager? = null

    private fun currentInputMode(): String = when {
        physicalInput.physicalMouseActive -> "physical_mouse"
        coverButtons.coverTrackpadActive && virtualPointer.virtualPointerInputActive(true) -> "cover_virtual_touchpad"
        rawTouchscreen.rawTouchscreenBridgeConsumesTouchSurface() ->
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
        (laptopModeActive || coverButtons.coverTrackpadActive || carCompanion.carCompanionPhoneTrackpadSurface != null) &&
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
                "mirrorDisplayId=${mirror.mirrorDisplayId} " +
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
            append("seq=${diagnostics.inputDiagnosticSequence} displayId=$displayId ")
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
            posture.hingeAngle = angle
            posture.refreshFoldingApiState("hinge_sensor")
            Log.d(logTag, "hinge angle=$angle laptop=$laptopModeActive")
            OperationLog.i(
                this@MirrorService,
                "FoldState",
                "hinge sensor angle=$angle apiFoldable=${posture.foldingApiFoldable} " +
                        "apiHalfOpened=${posture.foldingApiLaptopPosture} apiHorizontal=${posture.foldingApiHorizontalHinge}"
            )
            coverMenu.scheduleCoverDisplayLifecycleCheck("hinge_sensor")
            posture.updateLaptopModeForHinge(angle)
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
        physicalInput.routePhysicalMouseToDextop = false
        physicalInput.routePhysicalKeyboardToDextop = false
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .remove(KEY_ROUTE_MOUSE)
            .remove(KEY_ROUTE_KEYBOARD)
            .apply()
        runCatching { physicalInput.physicalInputRouter.restore() }
        touchSurface.experimentalMultiTouch = true
        instance = this
        carCompanion.applyCarCompanionKeyFilter()
        windowManager = getSystemService(WindowManager::class.java)
        inputManager = getSystemService(InputManager::class.java).also {
            it.registerInputDeviceListener(physicalInput.inputDeviceListener, null)
        }
        sensorManager = getSystemService(SensorManager::class.java).also { manager ->
            manager.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)?.let { sensor ->
                manager.registerListener(hingeListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                Log.i(logTag, "hinge-angle sensor registered: ${sensor.name}")
            } ?: Log.i(logTag, "hinge-angle sensor unavailable")
        }
        getSystemService(DisplayManager::class.java).registerDisplayListener(hostFollower.displayListener, null)
        val externalState = externalOutputs.externalDisplayDetector.snapshot()
        externalOutputs.knownPhysicalExternalDisplayIds = externalState.displayIds.toSet()
        externalOutputs.physicalExternalDisplayConnected = physicalInput.physicalInputRoutingSupported && externalState.connected
        if (!physicalInput.physicalInputRoutingSupported) runCatching { physicalInput.physicalInputRouter.restore() }
        physicalInput.physicalMouseActive = false
        if (!lockScreen.screenReceiverRegistered) {
            registerReceiver(
                lockScreen.screenReceiver,
                IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_OFF)
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_USER_PRESENT)
                },
                RECEIVER_NOT_EXPORTED
            )
            lockScreen.screenReceiverRegistered = true
        }
        Log.i(logTag, "accessibility connected")
        if (pendingDemo) {
            demoWindow.showDemoWindow()
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
                    swipeTyping.laptopLastEditorBounds = bounds
                    swipeTyping.laptopLastEditorBoundsAt = SystemClock.uptimeMillis()
                }
            }
        }
        val eventPackage = event.packageName?.toString().orEmpty()
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                if (eventPackage != SYSTEM_UI_PACKAGE || event.displayId != targetDisplayId) return
                taskRouting.notificationLaunchArmedUntil = SystemClock.uptimeMillis() + NOTIFICATION_LAUNCH_WINDOW_MS
                taskRouting.notificationRouteGeneration += 1
                Log.d(logTag, "SystemUI click observed on desktop display; waiting for notification launch")
            }

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (SystemClock.uptimeMillis() > taskRouting.notificationLaunchArmedUntil) return
                if (eventPackage.isBlank() || eventPackage == SYSTEM_UI_PACKAGE || eventPackage == packageName) return
                if (event.displayId == targetDisplayId) {
                    taskRouting.notificationLaunchArmedUntil = 0L
                    return
                }
                taskRouting.notificationLaunchArmedUntil = 0L
                val generation = taskRouting.notificationRouteGeneration
                taskRouting.routeNotificationTask(eventPackage, generation, 0)
            }
        }
    }

    private val recents: RecentsController = RecentsController(this, object : RecentsControllerHost, DesktopSession by session, OverlayMenu by overlayMenu {
        override val recentsExecutor: java.util.concurrent.ExecutorService get() = this@MirrorService.recentsExecutor
        override val recentsGeneration: AtomicInteger get() = this@MirrorService.recentsGeneration
    })

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


    override fun onInterrupt() = Unit

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        hostFollower.refreshMenuGeometryAfterDisplayChange()
        if (!active || lockScreen.suspendedForLockScreen) return
        posture.refreshFoldingApiState("configuration_changed", force = true)
        laptopModeSwitch.scheduleLaptopModeReevaluation("configuration_changed")
        OperationLog.i(
            this,
            "Orientation",
            "configuration changed orientation=${newConfig.orientation} density=${newConfig.densityDpi} " +
                    displayGeometrySnapshot("configuration_changed")
        )
        laptopModeSwitch.leaveLaptopModeOnCoverDisplay()
        hostFollower.scheduleHostDisplayReconfiguration(
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
        return plasmaShell?.handleKey(event) == true || touchSurface.forwardKeyEvent(event)
    }

    override fun onMotionEvent(event: MotionEvent) {
        // Events emitted by our uinput device already travel through
        // InputReader/InputDispatcher (and therefore display topology). Do
        // not feed them back through Dextop's target-display injector or they
        // would be delivered twice and the pointer would fight itself.
        if (virtualPointer.isVirtualMouseEvent(event)) return
        if (!active || !physicalInput.routePhysicalMouseToDextop || !event.isFromSource(InputDevice.SOURCE_MOUSE)) return
        val relativeX = event.getAxisValue(MotionEvent.AXIS_RELATIVE_X)
        val relativeY = event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y)
        when {
            relativeX != 0f || relativeY != 0f -> {
                physicalInput.activatePhysicalMouse()
                physicalInput.movePhysicalPointer(relativeX, relativeY)
            }

            event.actionMasked == MotionEvent.ACTION_HOVER_MOVE ||
                    event.actionMasked == MotionEvent.ACTION_MOVE -> {
                physicalInput.activatePhysicalMouse()
                val view = surfaceView ?: return
                if (view.width > 0 && view.height > 0) {
                    val region = externalOutputs.regionFor(view)
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
        if (instance === this && !androidPause.pausedForAndroid && (active || stopping || pending != null)) stop()
        if (lockScreen.screenReceiverRegistered) unregisterReceiver(lockScreen.screenReceiver)
        inputManager?.unregisterInputDeviceListener(physicalInput.inputDeviceListener)
        sensorManager?.unregisterListener(hingeListener)
        getSystemService(DisplayManager::class.java).unregisterDisplayListener(hostFollower.displayListener)
        inputManager = null
        sensorManager = null
        lockScreen.screenReceiverRegistered = false
        if (privilegedInputClientDelegate.isInitialized()) {
            privilegedInputClient.release("accessibility_unbound")
        }
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        serviceDestroyed = true
        launcher.stopPlasmaShell(restoreWindows = false)
        runCatching { displayFactory.autoOwnedDisplay?.attachment?.release() }
        displayFactory.autoOwnedDisplay = null
        if (instance === this) instance = null
        cast.endCastSession("service_destroyed")
        if (coverMenu.coverDisplayController.state().backButtonsActive) {
            CoverBackButtonsActivity.finishActive()
            coverMenu.coverDisplayController.stopBackButtonMode { }
        }
        if (coverMenu.coverDisplayController.state().trackpadActive) {
            CoverTrackpadActivity.finishActive()
            coverMenu.coverDisplayController.stopTrackpadMode { }
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
        keyboardDeck.laptopInputExecutor.shutdownNow()
        keyboardDeck.laptopKeyExecutor.shutdownNow()
        hostFollower.desktopWallpaperController.cancel()
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
            "launcher task removed active=$active pausedForAndroid=${androidPause.pausedForAndroid}"
        )
        Log.i(logTag, "launcher task removed; closing Dextop session safely")

        // Removing Dextop from Android Recents is an explicit session exit. Do
        // the restoration synchronously while the process and Shizuku binder
        // are still alive; waiting for onUnbind/onDestroy is too late on vendor
        // launchers which kill the task process immediately after this callback.
        if (active || androidPause.pausedForAndroid || sessionJournal.snapshot()["transactionOpen"] == true) {
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
        androidPause.pauseDisableSelfRunnable?.let(androidPause.pauseLifecycleHandler::removeCallbacks)
        androidPause.pauseDisableSelfRunnable = null
        // A touchpad-to-mouse compatibility fallback is scoped to one session;
        // every new session retries the user's selected profile.
        virtualPointer.virtualPointerRuntimeProfile = null
        privilegedPointerFallbackInProgress = false
        privilegedPointerFallbackActive = false
        laptopModeActive = false
        laptopModeSwitch.laptopBaseConfig = null
        laptopModeSwitch.laptopFullHostHeightPx = 0
        posture.laptopManualOverride = false
        posture.laptopAutoSuppressedByUser = false
        posture.laptopAutoActivated = false
        laptopModeSwitch.laptopHostUniqueId = null
        posture.filteredHingeAngle = null
        posture.pendingLaptopMode = null
        posture.pendingLaptopModeSince = 0L
        posture.laptopModeEvaluationGeneration += 1
        posture.laptopPostureReevaluationGeneration += 1
        posture.laptopHostMismatchSince = 0L
        val persistedDecorations = launcher.shouldUsePersistedSystemDecorations()
        val requestedConfig = if (persistedDecorations && !config.decorations) {
            OperationLog.i(
                this,
                "DesktopHome",
                "using persisted system decorations for firmware=${launcher.firmwareIdentity()}"
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
        androidPause.pausedForAndroid = false
        cleanupPreferences.edit()
            .putBoolean("cleanup_pending", true)
            .putBoolean("paused_by_user", false)
            .putLong("started_at", System.currentTimeMillis())
            .commit()
        lockScreen.suspendedForLockScreen = false
        lockScreen.suspendedConfig = null
        lockScreen.keyguardLockObservedSinceScreenOff = false
        lockScreen.unlockCandidateSince = 0L
        lockScreen.unlockResumeScheduled = false
        touchSurface.experimentalMultiTouch = true
        sessionJournal.preparing(
            effectiveConfig.width,
            effectiveConfig.height,
            effectiveConfig.density,
            effectiveConfig.decorations
        )
        runCatching { hostFollower.internalRefreshRateController.applyIfEnabled() }
            .onFailure { Log.e(logTag, "120 Hz override failed", it) }
        mirror.mirrorRefreshGeneration += 1
        hostFollower.hostReconfigurationGeneration += 1
        mirror.mirrorHostWidth = 0
        mirror.mirrorHostHeight = 0
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
        displayFactory.autoDestinationSurface = null
        pendingAutoSurface = null
        launcher.homeDecorationRetryUsed = false
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
            displayFactory.autoDestinationSurface = requestedAutoDestinationSurface?.takeIf { it.isValid }
            // There is deliberately no SurfaceView on the phone in this mode.
            // The Auto activity creates the only recording VirtualDisplay and
            // attaches it to the head-unit surface.
            pending = null
            active = true
            displayFactory.createHeadlessDisplay()
            return
        }
        addWindow()
        pending = null
        active = true
        hostFollower.startHostDisplayMonitor()
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
            if (withdrawAgain) launcher.setDesktopSystemDecorationsVisible(displayId, true)
            val launched = runCatching {
                startActivity(intent, ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle())
            }.onFailure {
                OperationLog.w(this@MirrorService, "PlasmaShell", "desktop HOME launch failed display=$displayId", it)
            }.isSuccess
            if (withdrawAgain) {
                listOf(250L, 700L, 1_500L).forEach { delay ->
                    root?.postDelayed({
                        if (active && targetDisplayId == displayId) launcher.setDesktopSystemDecorationsVisible(displayId, false)
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
            launcher.applyWindowManagerSelection("plasma_logout")
        }

        override fun restartShell() {
            launcher.stopPlasmaShell(restoreWindows = false)
            root?.postDelayed({ launcher.applyWindowManagerSelection("plasma_restart") }, 300L)
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
                    ) physicalInput.activatePhysicalMouse()
                    touchSurface.forwardMouseEvent(event, this)
                } else {
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        // Touching the desktop abandons the pending swipe
                        // choice, but must not tear down the laptop keyboard.
                        // The pointer and keyboard share one native engine.
                        if (laptopModeActive) swipeTyping.hideLaptopSwipeCandidates()
                        activateTouchInput()
                    }
                    touchSurface.trackpad(event, sourceView = view)
                }
            }
            setOnGenericMotionListener { _, event ->
                physicalInput.handlePhysicalMouseEvent(event, this)
            }
            setOnHoverListener { _, event -> physicalInput.handlePhysicalMouseEvent(event, this) }
            setOnCapturedPointerListener { _, event -> physicalInput.handleCapturedMouseEvent(event) }
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
        externalOutputs.externalStartPending = !autoOnlySession &&
            externalOutputs.externalDisplayPreference("external_display_mode", "off") == "external" &&
            externalOutputDisplay() != null
        if (externalOutputs.externalStartPending) {
            params.alpha = 0f
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            frame.postDelayed({ externalOutputs.revealPhoneWindow() }, 6_000L)
        }
        root = frame
        rootWindowParams = params
        surfaceView = surface
        keyboardDeck.laptopContent = content
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
                        "apiHalfOpened=${posture.foldingApiLaptopPosture} apiHorizontal=${posture.foldingApiHorizontalHinge}"
            )
            val autoStart = laptopModeSwitch.isLaptopAutoDetectionEnabled() &&
                    laptopModeSwitch.isLaptopAutoOrientationEligible() && posture.currentLaptopPosture() == true
            val blackBerryAutoStart = requestedPortrait &&
                    !isFoldableDevice() &&
                    laptopModeSwitch.isBlackBerryModeEnabled() &&
                    getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
                        .getBoolean("flutter.blackberry_auto_start", false)
            val startInLaptopMode = laptopModeSwitch.isDebugLaptopModeForced() || posture.laptopManualOverride || autoStart
            posture.laptopAutoActivated = autoStart && !posture.laptopManualOverride && !blackBerryAutoStart
            if (blackBerryAutoStart) {
                laptopModeSwitch.setLaptopMode(true, KeyboardDeckStyle.BLACKBERRY)
            } else if (startInLaptopMode) {
                laptopModeSwitch.setLaptopMode(true)
            }
        }
        if (touchSurface.experimentalMultiTouch) {
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

    private val coverButtons: CoverBackButtons = CoverBackButtons(this, object : CoverBackButtonsHost, DesktopSession by session, OverlayMenu by overlayMenu {
        override fun activateTopologyForIndependentDisplays(reason: String): Unit = this@MirrorService.hostFollower.activateTopologyForIndependentDisplays(reason)
        override fun cancelDesktopTouchStream(): Unit = this@MirrorService.touchSurface.cancelDesktopTouchStream()
        override val cast: CastController get() = this@MirrorService.cast
        override val coverDisplayController: CoverDisplaySessionController get() = this@MirrorService.coverMenu.coverDisplayController
        override fun coverDisplayLifecycleToken(): String = this@MirrorService.coverMenu.coverDisplayLifecycleToken()
        override val gamepad: VirtualGamepad get() = this@MirrorService.gamepad
        override fun isFoldableDevice(): Boolean = this@MirrorService.isFoldableDevice()
        override val menuWidgets: MenuWidgets get() = this@MirrorService.menuWidgets
        override fun refreshPrivilegedInputConfig(reason: String): Unit = this@MirrorService.refreshPrivilegedInputConfig(reason)
        override fun showCoverDisplayFailure(error: Throwable): Unit = this@MirrorService.coverMenu.showCoverDisplayFailure(error)
        override fun showCoverDisplayMenu(panel: LinearLayout): Unit = this@MirrorService.coverMenu.showCoverDisplayMenu(panel)
        override fun trackpad(event: MotionEvent, sourceView: View, forceCursorMode: Boolean, allowVirtualPointer: Boolean, hapticView: View?): Boolean = this@MirrorService.touchSurface.trackpad(event, sourceView, forceCursorMode, allowVirtualPointer, hapticView)
        override val virtualPointer: VirtualPointer get() = this@MirrorService.virtualPointer
        override val virtualTouchpad: VirtualTouchpad get() = this@MirrorService.virtualTouchpad
    })

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
        val fullscreenBounds = rawTouchscreen.rawTouchscreenViewBounds(surfaceView)
        val trackpadBounds = rawTouchscreen.rawTouchscreenViewBounds(keyboardDeck.laptopTrackpadView)
        // A touchpad reports relative movement from its absolute contact
        // range.  Keep that range proportional to the actual input region;
        // the former fixed landscape 1839×1199 range made portrait input
        // horizontally over-sensitive and vertically sluggish.
        // In laptop mode the trackpad rectangle is only the gesture's entry
        // gate. Once a contact starts there, it may continue through the
        // keyboard and the upper display area. Publish a host-sized absolute
        // range so the native bridge does not pin that contact to the
        // trackpad's top/side edges while it remains down.
        val coverSurface = coverButtons.coverTrackpadSurface
        val companionSurface = carCompanion.carCompanionPhoneTrackpadSurface?.takeIf {
            autoOnlySession && it.isAttachedToWindow
        }
        val touchpadMaxX = if (carCompanion.carCompanionPhoneUsesNativeTouchpad &&
            (companionSurface?.width ?: 0) > 0
        ) {
            checkNotNull(companionSurface).width
        } else if (coverButtons.coverTrackpadActive && (coverSurface?.width ?: 0) > 0) {
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
        } else if (coverButtons.coverTrackpadActive && (coverSurface?.height ?: 0) > 0) {
            checkNotNull(coverSurface).height
        } else if (laptopModeActive) {
            hostHeight.coerceAtLeast(1)
        } else {
            hostHeight.coerceAtLeast(1)
        }
        val candidate = PrivilegedInputProtocol.buildConfig(
            profile = nativeProfile,
            rotation = rawTouchscreen.rawTouchscreenDisplayRotation(),
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
        if (!active || directTouch && !laptopModeActive && !coverButtons.coverTrackpadActive) {
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

    private fun defaultDisplayUniqueId(): String? = runCatching {
        Display::class.java.getMethod("getUniqueId")
            .invoke(getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)) as String
    }.getOrNull()

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

    private fun internalDisplays(manager: DisplayManager): List<Display> =
        manager.displays.filter { display ->
            runCatching { Display::class.java.getMethod("getType").invoke(display) as Int == 1 }
                .getOrDefault(display.displayId == Display.DEFAULT_DISPLAY)
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

    private fun demoExplanation(message: String) {
        demoWindow.demoInfoView?.apply {
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
        textInput.endOverlayTextInput()
        textInput.setOverlayFocusable(false)
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

    private fun packageName(): String = applicationContext.packageName

    private val controlCenter: ControlCenter = ControlCenter(this, object : ControlCenterHost, DesktopSession by session, OverlayMenu by overlayMenu {
        override val rootWindowParams: WindowManager.LayoutParams? get() = this@MirrorService.rootWindowParams
    })

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
                "laptop" -> demoMode || laptopModeSwitch.isLaptopCapableDevice() || laptopModeSwitch.isBlackBerryModeAvailable() ||
                        (laptopModeActive && (
                            keyboardDeckStyle == KeyboardDeckStyle.BLACKBERRY ||
                                    keyboardDeckStyle == KeyboardDeckStyle.GAMEPAD ||
                                    keyboardDeckStyle == KeyboardDeckStyle.GAMEBOY
                            ))
                // This is deliberately independent from the Fold8/Ultra laptop
                // profile. Any concrete folding signal qualifies the formal
                // cover-display session control on foldable devices.
                "cover" -> demoMode || (isFoldableDevice() &&
                        coverMenu.isCoverDisplayFeatureEnabled())
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
                else androidPause.temporarilyReturnToAndroid()
            }

            "cover" -> menuWidgets.actionButton(
                R.drawable.ic_smartphone,
                NativeStrings.text("nativeCoverDisplay"),
            ) {
                if (demoMode) coverMenu.showCoverDisplayDemoMenu(panel)
                else coverMenu.showCoverDisplayMenu(panel)
            }

            "laptop" -> {
                val laptopAvailable = laptopModeSwitch.isLaptopStyleAvailable()
                val blackBerryAvailable = laptopModeSwitch.isBlackBerryStyleAvailable()
                val gamepadAvailable = gamepad.isGamepadStyleAvailable()
                val hasStyleChoice = laptopModeSwitch.hasKeyboardStyleChoice()
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
                        hasStyleChoice -> laptopModeSwitch.showKeyboardStyleMenu(panel)
                        blackBerryAvailable -> laptopModeSwitch.toggleKeyboardStyle(KeyboardDeckStyle.BLACKBERRY)
                        gamepadAvailable -> laptopModeSwitch.toggleKeyboardStyle(KeyboardDeckStyle.GAMEPAD)
                        else -> laptopModeSwitch.toggleKeyboardStyle(KeyboardDeckStyle.LAPTOP)
                    }
                }
                if (blackBerryAvailable && !hasStyleChoice) {
                    keyboardDeck.blackBerryActionRow(mainAction) { keyboardDeck.showBlackBerryLayoutEditor(panel) }
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

    private val menuWidgets: MenuWidgets = MenuWidgets(this, object : MenuWidgetsHost, DesktopSession by session, OverlayMenu by overlayMenu {
        override fun beginOverlayTextInput(field: EditText): Unit = this@MirrorService.textInput.beginOverlayTextInput(field)
        override val cast: CastController get() = this@MirrorService.cast
        override fun changeOrientation(): Unit = this@MirrorService.changeOrientation()
        override val requestedPortrait: Boolean get() = this@MirrorService.requestedPortrait
        override fun start(config: Config): Unit = this@MirrorService.start(config)
        override fun stop(): Unit = this@MirrorService.stop()
        override fun toggleDisplayRotation180(): Unit = this@MirrorService.toggleDisplayRotation180()
    })

    internal class ExternalOutput(
        val mode: String,
        val displayId: Int,
        val windowManager: WindowManager,
        val frame: View,
        val surface: SurfaceView,
        val phoneSurface: SurfaceView,
        val phoneConfig: Config,
        private val onSurfaceReady: () -> Unit,
        private val onSurfaceGone: () -> Unit,
    ) : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) = onSurfaceReady()
        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = onSurfaceReady()
        override fun surfaceDestroyed(holder: SurfaceHolder) = onSurfaceGone()
    }

    private val surfaceCallback: SurfaceHolder.Callback get() = this

    /** The external screen that can take the desktop, if one is connected. */
    private fun externalOutputDisplay(): Display? = externalOutputDisplay(this)

    private val resolutionMenu: ResolutionMenu = ResolutionMenu(this, object : ResolutionMenuHost, DesktopSession by session, OverlayMenu by overlayMenu {
        override fun editButton(action: () -> Unit): ImageButton = this@MirrorService.menuWidgets.editButton(action)
        override fun effectiveConfig(config: Config): Config = this@MirrorService.effectiveConfig(config)
        override fun numberField(value: String, label: String): EditText = this@MirrorService.menuWidgets.numberField(value, label)
        override fun resizeActiveDisplay(next: Config, reason: String) = this@MirrorService.hostFollower.resizeActiveDisplay(next, reason)
        override fun resolutionProfiles(): List<ResolutionProfile> = this@MirrorService.resolutionProfiles()
        override val resolutionRepository: ResolutionRepository get() = this@MirrorService.resolutionRepository
    })

    private fun rotate180PreferenceKey(portrait: Boolean): String =
        if (portrait) KEY_ROTATE_180_PORTRAIT else KEY_ROTATE_180_LANDSCAPE

    private fun applyHostDisplayOrientation(portrait: Boolean) {
        val reverse = getSharedPreferences(PREFS, MODE_PRIVATE)
            .getBoolean(rotate180PreferenceKey(portrait), false)
        MainActivity.setDisplayOrientation(portrait, reverse)
    }

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
        val output = externalOutputs.externalOutput
        if (output?.mode == "external") cursorView?.alpha = 0f
        else place(cursorView, externalOutputs.regionFor(output?.phoneSurface ?: surfaceView))
        output?.let { place(externalOutputs.externalCursor, externalOutputs.regionFor(it.surface)) }
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
        displayFactory.applyDisplayRotation(0)
        phoneGuard.forcePhoneRotation(portrait, force = true)
        OperationLog.i(
            this,
            "Orientation",
            "180-degree rotation changed portrait=$portrait enabled=$enabled display=$targetDisplayId"
        )
    }

    private fun resolutionProfiles(): List<ResolutionProfile> =
        resolutionRepository.profiles(density)

    private val haptics: Haptics = Haptics(this, object : HapticsHost, DesktopSession by session, OverlayMenu by overlayMenu {
    })


    private fun setSavedTouchMode(useTapPosition: Boolean) {
        // Finish the previous gesture before changing the ownership of the
        // input stream.  In particular, release a held mouse button before
        // removing the uinput device so the next app cannot inherit it.
        touchSurface.cancelDesktopTouchStream()
        directTouch = useTapPosition
        physicalInput.physicalMouseActive = false
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
        if (textInput.overlayTextInputActive) return
        surfaceView?.releasePointerCapture()
        textInput.setOverlayFocusable(false)
        physicalInput.physicalMouseActive = false
        if (directTouch) {
            // A stale virtual pointer can survive a mode change until the
            // InputReader removal callback arrives. Enforce the tap-mode
            // boundary when the first phone-surface touch is received as
            // well, including a device that was attached by the laptop deck.
            touchSurface.cancelDesktopTouchStream()
            if (laptopModeActive && keyboardDeck.laptopKeyboardRequested) {
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

    private fun updateKeepAwake(enabled: Boolean) {
        val params = rootWindowParams ?: return
        params.flags = if (enabled) {
            params.flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        } else {
            params.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON.inv()
        }
        root?.let { windowManager?.updateViewLayout(it, params) }
    }

    private fun toggleMenu() {
        val panel = menu ?: return
        val frame = root ?: return
        panel.animate().cancel()
        menuScrim?.animate()?.cancel()
        if (panel.visibility != View.VISIBLE) {
            touchSurface.cancelDesktopTouchStream()
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
            touchSurface.cancelDesktopTouchStream()
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
                textInput.endOverlayTextInput()
                if (overlayLayoutEditing) {
                    overlayLayoutEditing = false
                    menuPrimary?.let(::showMainMenu)
                }
                textInput.setOverlayFocusable(false)
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

    private fun changeOrientation() {
        // targetWidth/targetHeight are the live pane dimensions in laptop
        // mode, not the user's full-screen orientation. Toggle the explicit
        // Dextop choice and swap the saved pre-laptop profile instead.
        val portrait = !requestedPortrait
        val base = laptopModeSwitch.laptopBaseConfig ?: Config(
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
        if (laptopModeActive) laptopModeSwitch.laptopBaseConfig = config
        root?.postDelayed({
            runCatching {
                if (!active || targetDisplayId < 0) {
                    error(moe.n4tsu.dextop.localText("uiTheActiveDisplayIsUnavailableForOrientationChange"))
                }
                if (laptopModeActive) {
                    laptopModeSwitch.applyLaptopGeometryWhenLaidOut(
                        enabled = true,
                        baseOverride = config,
                        preserveMenu = true,
                    )
                } else {
                    hostFollower.resizeActiveDisplay(config, "manual orientation change")
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

    override fun surfaceCreated(holder: SurfaceHolder) {
        val view = surfaceView ?: return
        if (view.width <= 0 || view.height <= 0) return
        OperationLog.i(this, "DisplayGeometry", displayGeometrySnapshot("surface_created", view.width, view.height))
        if (menu != null) hostFollower.refreshMenuGeometryAfterDisplayChange()
        if (targetDisplayId >= 0 &&
            getSystemService(DisplayManager::class.java).getDisplay(targetDisplayId) != null
        ) {
            displayFactory.reattachExistingDisplay(view.width, view.height)
        } else {
            displayFactory.createDisplay(holder.surface, view.width, view.height)
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        OperationLog.i(this, "DisplayGeometry", displayGeometrySnapshot("surface_changed", width, height))
        if (menu != null) hostFollower.refreshMenuGeometryAfterDisplayChange()
        if (mirror.mirrorDisplayId < 0) {
            if (targetDisplayId >= 0 &&
                getSystemService(DisplayManager::class.java).getDisplay(targetDisplayId) != null
            ) {
                displayFactory.reattachExistingDisplay(width, height)
            } else {
                displayFactory.createDisplay(holder.surface, width, height)
            }
        } else if (hostFollower.shouldFollowHostDisplay() && hostFollower.hostSizeDiffersFromTarget(width, height)) {
            hostFollower.scheduleHostDisplayReconfiguration("host surface resized", width, height)
        } else if (width != mirror.mirrorHostWidth || height != mirror.mirrorHostHeight) {
            mirror.scheduleMirrorRefresh("host surface changed", width, height)
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        OperationLog.i(this, "DisplayGeometry", displayGeometrySnapshot("surface_destroyed"))
        mirror.releaseMirror()
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

    private fun isWorkspaceMagnificationActive(): Boolean {
        val value = getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
            .all["flutter.desktop_workspace_magnification_percent"]
        return (value as? Number)?.toInt()?.let { it > 100 } == true
    }

    private fun systemService(name: String, interfaceName: String): Any {
        return privilegedAccess.service(name, interfaceName)
    }

    private val phoneGuard: PhoneGuard = PhoneGuard(this, object : PhoneGuardHost, DesktopSession by session, OverlayMenu by overlayMenu {
        override fun displayGeometrySnapshot(reason: String, hostWidth: Int?, hostHeight: Int?): String = this@MirrorService.displayGeometrySnapshot(reason, hostWidth, hostHeight)
        override val hostDisplayMonitorHandler: Handler get() = this@MirrorService.hostFollower.hostDisplayMonitorHandler
        override fun rotate180PreferenceKey(portrait: Boolean): String = this@MirrorService.rotate180PreferenceKey(portrait)
        override fun systemService(name: String, interfaceName: String): Any = this@MirrorService.systemService(name, interfaceName)
    })

    private fun stop() {
        if (stopping) return
        stopping = true
        if (coverMenu.coverDisplayController.state().backButtonsActive) {
            coverMenu.coverDisplayController.stopBackButtonMode { }
        }
        if (coverMenu.coverDisplayController.state().trackpadActive) {
            coverMenu.coverDisplayController.stopTrackpadMode { }
        }
        val cleanupGeneration = ++stopCleanupGeneration
        val wasActive = active
        val displayBeingRemoved = targetDisplayId
        if (wasActive) {
            OperationLog.i(this, "DisplayGeometry", displayGeometrySnapshot("session_stopping"))
        }
        cast.endCastSession("dextop_stopped")
        hostFollower.stopHostDisplayMonitor()
        lockScreen.suspendedForLockScreen = false
        lockScreen.suspendedConfig = null
        lockScreen.screenLifecycleGeneration += 1
        lockScreen.unlockResumeScheduled = false
        lockScreen.unlockCandidateSince = 0L
        if (touchSurface.dragHeld) touchSurface.toggleDrag()
        runCatching { physicalInput.physicalInputRouter.restore() }
            .onFailure { Log.e(logTag, "physical input restoration failed", it) }
        swipeTyping.restoreDextopSwipeIme()

        launcher.stopPlasmaShell(restoreWindows = false)

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
        laptopModeSwitch.laptopBaseConfig = null
        laptopModeSwitch.laptopFullHostHeightPx = 0
        posture.laptopManualOverride = false
        posture.laptopAutoSuppressedByUser = false
        posture.laptopAutoActivated = false
        posture.pendingLaptopMode = null
        posture.pendingLaptopModeSince = 0L
        posture.laptopModeEvaluationGeneration += 1
        posture.laptopPostureReevaluationGeneration += 1
        posture.laptopHostMismatchSince = 0L

        // Settings.Global is only a request to OverlayDisplayAdapter.  Do not
        // restore the phone/DeX environment or mark the session finished until
        // the display manager has observed the requested display disappear.
        displayFactory.releasePhoneOwnedDisplay("session_stop")
        displayFactory.clearOverlayDisplayRequestTwice("session_stop display=$displayBeingRemoved")
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
        val coverSessionActive = coverMenu.coverDisplayController.state().desktopActive
        runCatching {
            if (autoSessionActive || coverSessionActive) {
                DisplayEnvironmentSettings(this).activateTopologyForOverlays(
                    AndroidAutoMirrorActivity.autoOverlayDisplayIds() +
                        coverMenu.coverDisplayController.ownedDisplayIds()
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
        val keepInternal120Hz = hostFollower.internalRefreshRateController.isEnabledAndSupported() &&
                !externalOutputs.externalDisplayDetector.snapshot().connected
        if (keepInternal120Hz) {
            runCatching { hostFollower.internalRefreshRateController.keepCurrentValue() }
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
        mirror.releaseMirror()
    }

    private fun detachHostWindow() {
        cast.stopCastRouteDiscovery()
        keyboardDeck.finishAllLaptopKeyPresses()
        keyboardDeck.stopLaptopHardwareKeyboard()
        virtualPointer.stopVirtualMouse()
        // Prevent surfaceDestroyed() from releasing the mirrored display before
        // WindowManager has removed this host and its gesture registrations.
        surfaceView?.holder?.removeCallback(this)
        externalOutputs.externalOutput?.let { output ->
            externalOutputs.externalOutput = null
            output.surface.holder.removeCallback(this)
            output.surface.holder.removeCallback(output)
            externalOutputs.externalBackend.releaseLayer()
            runCatching { output.windowManager.removeView(output.frame) }
        }
        // The next session's window is new; a role left from this one would skip shrinking it.
        externalOutputs.externalPhoneRole = null
        externalOutputs.externalInputDeck = false
        cursorView?.let { runCatching { windowManager?.removeView(it) } }
        root?.let { runCatching { windowManager?.removeView(it) } }
        root = null
        rootWindowParams = null
        surfaceView = null
        keyboardDeck.laptopContent = null
        cursorView = null
        menu = null
        menuScrim = null
        performanceHud = null
        keyboardDeck.laptopDeck = null
        keyboardDeck.laptopDeckContent = null
        demoWindow.demoInfoView = null
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

}
