package moe.n4tsu.dextop.plasma

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.InputEvent
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.InputDevice
import android.view.Surface
import android.view.View
import android.widget.FrameLayout
import moe.n4tsu.dextop.DisplayEnvironmentSettings
import moe.n4tsu.dextop.linux.LinuxContainerClient
import moe.n4tsu.dextop.linux.LinuxSteam
import moe.n4tsu.dextop.linux.LinuxKeymap
import moe.n4tsu.dextop.linux.LinuxWindowViewport
import org.json.JSONArray
import org.json.JSONObject
import moe.n4tsu.dextop.VirtualDisplayPlatform
import moe.n4tsu.dextop.chooseMirrorRefreshRate
import java.util.concurrent.Executors
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Dextop Plasma v2: a compositing window manager.
 *
 * The whole Dextop display is covered by one Dextop-drawn surface (desktop,
 * windows, panel, popups).  Every application runs on its own private
 * virtual display whose output is shown in a TextureView inside a Breeze
 * window; touches, mouse and keys are injected into that application
 * display.  Because the windows are Dextop's own views, dragging, resizing
 * and every KWin-style animation run at full frame rate with live content.
 */
internal abstract class CompositedWindowShell(
    private val service: Context,
    override val displayId: Int,
    displayWidth: Int,
    displayHeight: Int,
    private val densityDpi: Int,
    private val host: PlasmaShell.Host,
    /** See DesktopEnvironment.fullscreenApplicationDisplays. */
    private val fullscreenAppDisplays: Boolean = false,
    /** See DesktopEnvironment.passivePlasmaShellFocus. */
    private val passiveShellFocus: Boolean = false,
    /** See DesktopEnvironment.maxRateApplicationDisplays. */
    private val maxRateAppDisplays: Boolean = false,
    /** See DesktopEnvironment.plasmaApplicationOverlay. */
    private val applicationOverlay: Boolean = false,
    /** See DesktopEnvironment.privateApplicationDisplays. */
    private val privateAppDisplays: Boolean = false,
    private val autoMode: Boolean,
) : DesktopShell, V2WindowHost, PanelHost, LauncherHost, AutoUiHost {

    companion object {
        private const val TAG = "DextopPlasmaV2"
        private const val PREFS = "dextop_plasma"
        private const val POLL_INTERVAL_MS = 500L
        private const val EMPTY_CLOSE_MS = 900L
        private const val LAUNCH_TIMEOUT_MS = 7_000L
        private const val INJECT_ASYNC = 0
        private const val DISPLAY_IME_POLICY_LOCAL = 0
        /** MotionEvent button bits to X button numbers (left, middle, right, back, forward). */
        // MotionEvent classifications of touchpad gestures (API 34).
        private const val CLASSIFICATION_TWO_FINGER_SWIPE = 3
        private const val CLASSIFICATION_MULTI_FINGER_SWIPE = 4
        private const val CLASSIFICATION_PINCH = 5

        /** Finger travel per wheel step for scroll gestures. */
        private const val LINUX_SCROLL_STEP_DP = 18

        private val LINUX_BUTTONS = listOf(
            MotionEvent.BUTTON_PRIMARY to 1, MotionEvent.BUTTON_TERTIARY to 2, MotionEvent.BUTTON_SECONDARY to 3,
            MotionEvent.BUTTON_BACK to 8, MotionEvent.BUTTON_FORWARD to 9,
        )
    }

    // region state
    private val handler = Handler(Looper.getMainLooper())
    private val displayContext: Context = service.createDisplayContext(
        service.getSystemService(DisplayManager::class.java).getDisplay(displayId)
            ?: error("Dextop display $displayId is unavailable"),
    )
    private val layer = OverlayLayer(
        displayContext,
        TAG,
        if (applicationOverlay) {
            android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        },
        displayOverlayService = if (autoMode) service as? android.accessibilityservice.AccessibilityService else null,
    )
    private val bridge = TaskBridge(host::privilegedService, TAG)
    private val appRepository = PlasmaAppRepository(service)
    private val prefs = service.getSharedPreferences(if (autoMode) "dextop_auto_runtime" else PREFS, Context.MODE_PRIVATE)
    private val wmExecutor = Executors.newSingleThreadExecutor { Thread(it, "DextopPlasmaV2WM") }
    private val linuxDisplayExecutor = Executors.newSingleThreadExecutor { Thread(it, "DextopLinuxDisplay") }
    private val linuxDisplayUpdates = moe.n4tsu.dextop.linux.LinuxDisplayUpdates(linuxDisplayExecutor) { id, apps, config ->
        runCatching {
            checkNotNull(LinuxContainerClient.service()) { "Linux service is unavailable" }
                .updateDisplay(id, apps, config.width, config.height, config.dpi, config.refreshMilliHz / 1000f)
        }.onFailure { error ->
            host.log("Linux display update failed: ${error.message}")
            root.post { linuxDisplayRate = 0f }
        }
    }
    private fun queueLinuxDisplay(id: String, apps: Boolean, w: Int, h: Int, dpi: Int, rate: Float) {
        linuxDisplayUpdates.offer(id, apps, moe.n4tsu.dextop.linux.LinuxDisplayConfiguration.create(w, h, dpi, rate))
    }
    private val inputExecutor = Executors.newSingleThreadExecutor { Thread(it, "DextopPlasmaV2Input") }
    private val systemExecutor = Executors.newSingleThreadExecutor { Thread(it, "DextopPlasmaV2System") }
    private val systemControls by lazy { PlasmaSystemControls(service) { arguments -> host.privilegedCommand(*arguments) } }
    private val trayWorker by lazy { TrayWorker(systemExecutor, handler) }
    /** Keeps the tray icons current between popups. */
    private val trayReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (running) handler.post {
                refreshTray()
                if (intent.action == "android.media.VOLUME_CHANGED_ACTION" &&
                    intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1) == AudioManager.STREAM_MUSIC) {
                    onVolumeChanged()
                }
            }
        }
    }
    private val displayPlatform by lazy { VirtualDisplayPlatform.inspect() }
    private val displayService by lazy { host.privilegedService("display", VirtualDisplayPlatform.MANAGER_INTERFACE) }
    private val inputService by lazy { host.privilegedService("input", "android.hardware.input.IInputManager") }
    private val injectMethod by lazy {
        Class.forName("android.hardware.input.IInputManager")
            .getMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType)
    }
    private val setEventDisplay by lazy { InputEvent::class.java.getMethod("setDisplayId", Int::class.javaPrimitiveType) }
    private val windowService by lazy { host.privilegedService("window", "android.view.IWindowManager") }

    private var width = displayWidth
    private var height = displayHeight
    final override var settings: PlasmaSettings = PlasmaSettings.load(service)
        private set
    final override var palette: PlasmaPalette = resolveAutoPalette()
        private set

    private val windows = ArrayList<V2Window>()
    private var nextWindowId = 1
    private var activeId = -1
    private var currentDesktop = 0
    private var running = false
    private var peeking = false
    private var trayState = TrayState(.5f, false, 1f, false, true)
    /** Tasks already pulled (or attempted) into a window; never retried. */
    private val adoptedTaskIds = HashSet<Int>()

    private lateinit var root: FrameLayout
    private val wallpaper by lazy { WallpaperView(displayContext) }
    private lateinit var dim: View
    private lateinit var windowsLayer: FrameLayout
    private lateinit var snapPreview: SnapPreviewView
    private lateinit var overviewChrome: FrameLayout
    private lateinit var panel: PlasmaPanelView
    private lateinit var popupLayer: FrameLayout
    private var panelFloat = 1f
    private var panelFloatTarget = 1f
    private var panelAnimator: ValueAnimator? = null

    private var volumeOsd: android.widget.TextView? = null
    private val hideVolumeOsd = Runnable {
        volumeOsd?.let { root.removeView(it) }
        volumeOsd = null
    }

    override fun onVolumeChanged() {
        if (!running || !::root.isInitialized) return
        handler.removeCallbacks(hideVolumeOsd)
        val audio = service.getSystemService(AudioManager::class.java)
        val level = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() /
            audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val label = volumeOsd ?: android.widget.TextView(displayContext).apply {
            gravity = Gravity.CENTER
            textSize = 18f
            setTextColor(palette.text)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(palette.popup)
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), palette.separator)
            }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            isFocusable = false
            isClickable = false
            root.addView(this, FrameLayout.LayoutParams(dp(260), dp(64), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                bottomMargin = if (autoMode) dp(24) else panel.thickness + dp(40)
            })
            volumeOsd = this
        }
        label.text = t("nativeVolume") + "  " +
            if (audio.isStreamMute(AudioManager.STREAM_MUSIC)) "0%" else percentText(level)
        label.bringToFront()
        refreshTray()
        handler.postDelayed(hideVolumeOsd, 1800L)
    }

    private var autoUi: DextopAutoView? = null
    override val workspace = AutoWorkspace().apply {
        resize(service.getSharedPreferences("dextop_auto_ui",Context.MODE_PRIVATE).getFloat("split_ratio",.5f))
    }
    override val autoPalette get() = palette
    private var autoMediaInfo: PlasmaSystemControls.MediaInfo? = null
    private var autoStatusBusy = false
    private var pendingAutoPane: Int? = null
    private var autoRelayoutPending = false
    private val autoStatusTicker = object : Runnable {
        override fun run() {
            if (!running || !autoMode) return
            updateAutoStatus()
            handler.postDelayed(this, 10_000L)
        }
    }

    override fun showHome(): Boolean {
        if (!autoMode) return false
        autoUi?.showHome()
        return true
    }
    private fun resolveAutoPalette(): PlasmaPalette {
        if (!autoMode) return PlasmaTheme.resolve(service, settings)
        val scheme = service.getSharedPreferences("dextop_auto_ui", Context.MODE_PRIVATE).getString("theme","dark") ?: "dark"
        return PlasmaTheme.resolve(service, settings.copy(colorScheme=scheme))
    }
    private fun buildAutoUi() {
        val view = DextopAutoView(displayContext,this)
        autoUi = view
        root.addView(view,FrameLayout.LayoutParams(-1,-1))
        // Give the shell a moment to lay out before the previous applications come back.
        handler.postDelayed({ restoreAutoSession() }, 400L)
        view.addOnLayoutChangeListener { _,_,_,_,_,_,_,_,_ ->
            if (!autoRelayoutPending) {
                autoRelayoutPending = true
                handler.post {
                    autoRelayoutPending = false
                    if (running) layoutAutoWorkspace()
                }
            }
        }
        refreshTray()
        view.showHome()
        handler.post(autoStatusTicker)
    }
    /** Android applications and the Linux containers' applications; internal Linux placeholders are left out. */
    override fun applications(): List<PlasmaApp> = apps().filter {
        !it.packageName.startsWith("linux") || it.packageName.startsWith(LINUX_APP_PREFIX)
    }
    override fun tasks(): List<AutoTask> = windows.filter { !it.closing }.sortedByDescending { it.lastActivated }
        .map { AutoTask(it.id,it.title,it.icon,it.packageName) }
    override fun launchAuto(packageName: String,pane: Int?) {
        if (pane != null) workspace.focus(pane)
        pendingAutoPane = pane ?: workspace.focusedPane
        launch(packageName)
        pendingAutoPane = null
        layoutAutoWorkspace()
    }
    override fun selectAuto(id: Int) {
        windows.firstOrNull { it.id==id }?.let(::activate)
        layoutAutoWorkspace()
    }
    override fun focusAuto(pane: Int) {
        workspace.focus(pane)
        workspace.active()?.let { id -> windows.firstOrNull { it.id==id }?.let(::activate) }
        autoUi?.refreshChrome()
    }
    override fun closeAuto(id: Int) { windows.firstOrNull { it.id==id }?.let(::close) }
    override fun splitAuto() {
        if (!workspace.split) workspace.enableSplit()
        layoutAutoWorkspace()
        if (workspace.secondary==null) autoUi?.showPage("apps",if(workspace.primary==null) 0 else 1)
        else autoUi?.hidePage()
    }
    override fun singleAuto(pane: Int) {
        workspace.focus(pane);workspace.single();layoutAutoWorkspace()
        workspace.active()?.let(::selectAuto)
    }
    override fun swapAuto() { workspace.swap();layoutAutoWorkspace() }
    override fun resizeAuto(ratio: Float,final: Boolean) {
        workspace.resize(ratio)
        if (final) service.getSharedPreferences("dextop_auto_ui",Context.MODE_PRIVATE).edit().putFloat("split_ratio",workspace.ratio).apply()
        layoutAutoWorkspace(resizeDisplays=final)
    }
    override fun autoBack() {
        val target = workspace.active()?.let { id -> windows.firstOrNull { it.id==id }?.displayId } ?: return
        if (target>=0) host.privilegedExecute("input","-d",target.toString(),"keyevent","4")
    }
    override fun autoVolume(direction: Int) {
        service.getSystemService(AudioManager::class.java).adjustStreamVolume(AudioManager.STREAM_MUSIC,direction,0)
        onVolumeChanged()
    }
    override fun autoMedia(key: Int) { systemControls.mediaKey(key);handler.postDelayed({ updateAutoStatus() },400L) }
    /** Auto draws over the whole Surface; the host's floating template buttons may overlap it. */
    override fun setAutoViewport(left: Float, top: Float, right: Float, bottom: Float) {}
    /**
     * The Auto workspace as it is now: each application's task and pane, the split and the
     * focused pane. Saved on every change, so even a crashed Dextop process can be restored.
     */
    private var autoRestorePending = true

    private fun saveAutoSession(snapshot: List<V2Window> = windows) {
        // Until the previous session is back, an empty start must not overwrite it.
        if (!autoMode || autoRestorePending) return
        val apps = org.json.JSONArray()
        snapshot.filter { !it.closing && it.linuxContainer == null && !it.packageName.startsWith("linux") }
            .sortedBy { it.lastActivated }
            .forEach { window ->
                apps.put(org.json.JSONObject()
                    .put("package", window.packageName)
                    .put("task", window.taskId)
                    .put("pane", when (window.id) { workspace.primary -> 0; workspace.secondary -> 1; else -> -1 }))
            }
        val state = org.json.JSONObject().put("apps", apps).put("split", workspace.split).put("focus", workspace.focusedPane)
        prefs.edit().putString("auto_session", state.toString()).apply()
    }

    /** Reopens the previous Auto workspace: running tasks are taken over, missing ones relaunched. */
    private fun restoreAutoSession() {
        if (!autoMode || !running) return
        if (windows.isNotEmpty()) { autoRestorePending = false; return }
        val state = runCatching { org.json.JSONObject(prefs.getString("auto_session", null).orEmpty()) }.getOrNull()
        val saved = state?.optJSONArray("apps")
        if (state == null || saved == null) { autoRestorePending = false; return }
        val entries = (0 until saved.length()).mapNotNull { saved.optJSONObject(it) }
            .map { Triple(it.optString("package"), it.optInt("task", -1), it.optInt("pane", -1)) }
            .filter { it.first.isNotEmpty() && service.packageManager.getLaunchIntentForPackage(it.first) != null }
        if (entries.isEmpty()) { autoRestorePending = false; return }
        wmExecutor.execute {
            val manager = service.getSystemService(DisplayManager::class.java)
            val alive = manager.displays.flatMap { display -> runCatching { bridge.tasks(display.displayId) }.getOrDefault(emptyList()) }
                .filter { it.activityType == TaskBridge.ACTIVITY_TYPE_STANDARD }
            handler.post {
                autoRestorePending = false
                if (!running || windows.isNotEmpty()) return@post
                // Background apps first, then the panes, so the panes end up as saved.
                for ((packageName, taskId, pane) in entries.sortedBy { it.third }) {
                    if (pane == 1) workspace.enableSplit()
                    pendingAutoPane = pane.takeIf { it >= 0 }
                    val task = alive.firstOrNull { it.taskId == taskId }
                        ?: alive.firstOrNull { it.packageName == packageName && it.taskId !in adoptedTaskIds }
                    if (task != null) {
                        adoptedTaskIds += task.taskId
                        createWindow(packageName, smartPlacement(), adoptTask = task)
                    } else launch(packageName)
                    pendingAutoPane = null
                }
                if (!state.optBoolean("split") && workspace.split) workspace.single()
                workspace.focus(state.optInt("focus", 0))
                layoutAutoWorkspace()
                // Connecting always lands on Home; the restored apps wait behind it in their panes.
                autoUi?.showHome()
                autoUi?.updateTasks()
                saveAutoSession()
            }
        }
    }

    override fun relayoutAuto() {
        // The pane corner radius follows the gap setting; outlines are otherwise cached until a resize.
        windows.forEach { it.view.chrome.invalidateOutline() }
        layoutAutoWorkspace()
    }
    /** Applications already running on the phone, which Auto can take over into a pane. */
    override fun autoPhoneTasks(): List<TaskSnapshot> = runCatching { bridge.tasks(android.view.Display.DEFAULT_DISPLAY) }.getOrDefault(emptyList())
        .filter {
            it.activityType == TaskBridge.ACTIVITY_TYPE_STANDARD && it.packageName.isNotEmpty() &&
                it.packageName != service.packageName && it.taskId !in adoptedTaskIds &&
                windows.none { window -> window.packageName == it.packageName && !window.closing }
        }
    override fun autoAdoptPhoneTask(task: TaskSnapshot, pane: Int?) {
        if (pane != null) workspace.focus(pane)
        pendingAutoPane = pane ?: workspace.focusedPane
        adoptedTaskIds += task.taskId
        createWindow(task.packageName, smartPlacement(), adoptTask = task)
        pendingAutoPane = null
        layoutAutoWorkspace()
    }
    private val controllerPrefs get() = service.getSharedPreferences("dextop_linux_controllers", Context.MODE_PRIVATE)
    private fun steamModeContainers() = linuxApps.filter { it.packageName.endsWith(":${LinuxSteam.STEAM_MODE_ENTRY}") }
        .map { it.packageName.removePrefix(LINUX_APP_PREFIX).substringBefore(':') }.distinct()
    /** Matches the guest's view of the device: evdev vendor, product and name. */
    private fun controllerKey(device: android.view.InputDevice) = "%04x %04x %s".format(device.vendorId, device.productId, device.name)
    /** Controllers kept for Android, as the guest's dextop-pads.conf lists them. */
    private fun controllerState() = controllerPrefs.getStringSet("android", emptySet()).orEmpty().sorted().joinToString("") { "$it\n" }

    override fun autoControllers(): List<AutoController>? {
        if (steamModeContainers().isEmpty()) return null
        val kept = controllerPrefs.getStringSet("android", emptySet()).orEmpty()
        return android.view.InputDevice.getDeviceIds().toList().mapNotNull { android.view.InputDevice.getDevice(it) }.filter { device ->
            !device.isVirtual && (device.supportsSource(android.view.InputDevice.SOURCE_GAMEPAD) ||
                device.supportsSource(android.view.InputDevice.SOURCE_JOYSTICK))
        }.distinctBy { controllerKey(it) }.map { device ->
            val key = controllerKey(device)
            AutoController(key, device.name, key !in kept)
        }
    }

    override fun setAutoControllerForLinux(controller: AutoController, linux: Boolean) {
        val kept = controllerPrefs.getStringSet("android", emptySet()).orEmpty().toMutableSet()
        if (linux) kept -= controller.key else kept += controller.key
        controllerPrefs.edit().putStringSet("android", kept).apply()
        val state = controllerState()
        val containers = steamModeContainers()
        LinuxContainerClient.call(service, { linux ->
            containers.forEach { id -> runCatching { linux.setGuestState(id, LinuxSteam.STEAM_PADS_STATE, state) } }
        }, {}) { error -> host.log("Controller selection not sent to Linux: ${error.message}") }
    }

    override val windowShadows get() = !autoMode
    override fun windowCornerRadius(): Float = if (autoMode) autoUi?.cornerRadius() ?: -1f else -1f
    override val autoControls get() = systemControls
    override fun <T> autoLoad(owner: View, work: () -> T, done: (T) -> Unit) = trayWorker.load(owner, work, done)
    override fun autoOpenNotification(item: DextopNotification) { openNotification(item); autoUi?.hidePage() }
    override fun autoDismissNotification(item: DextopNotification) = dismissNotification(item)
    override fun autoOpenSettings(action: String) = launchIntentInWindow(Intent(action))
    override fun autoTrayChanged() = refreshTray()
    override fun autoGestureAnchor(): android.graphics.PointF? = if (autoMode) autoUi?.gestureAnchor() else null
    private val autoUiPrefs get() = service.getSharedPreferences("dextop_auto_ui", Context.MODE_PRIVATE)
    private val windowDensity = HashMap<Int, Int>()
    private fun activeAutoWindow() = workspace.active()?.let { id -> windows.firstOrNull { it.id == id && !it.closing } }
    override fun resumeAutoInput() {
        val window = activeAutoWindow() ?: return
        activate(window)
        window.view.content.requestFocus()
    }
    /** Applications in Auto keep their own zoom, applied as the density of their display. */
    private fun densityFor(window: V2Window): Int {
        if (!autoMode || window.packageName.startsWith("linux")) return densityDpi
        return (densityDpi * autoUiPrefs.getFloat("scale:${window.packageName}", 1f)).roundToInt().coerceAtLeast(72)
    }
    override fun autoScale(): Float? = activeAutoWindow()?.let { autoUiPrefs.getFloat("scale:${it.packageName}", 1f) }
    override fun setAutoScale(scale: Float) {
        val window = activeAutoWindow() ?: return
        if (!scale.isFinite()) return
        autoUiPrefs.edit().putFloat("scale:${window.packageName}", scale.coerceIn(.5f, 2f)).apply()
        resizeWindowDisplay(window)
    }
    override fun autoControl(item: TrayItem) {
        if (item==TrayItem.EXPAND) { autoUi?.showPage("settings");return }
        onTrayClicked(item,RectF(width/2f,height.toFloat(),width/2f+1,height.toFloat()))
    }
    override fun autoTheme(value: String) {
        if (value !in setOf("dark","light","system")) return
        service.getSharedPreferences("dextop_auto_ui",Context.MODE_PRIVATE).edit().putString("theme",value).apply()
        palette = resolveAutoPalette()
        root.setBackgroundColor(AutoColors(palette).background)
        autoUi?.showPage("settings")
        windows.forEach { it.view.refreshDecorations() }
    }
    private fun layoutAutoWorkspace(resizeDisplays: Boolean = true) {
        if (!autoMode || !running) return
        val view = autoUi ?: return
        saveAutoSession()
        if (view.width<=0 || view.height<=0) return
        val bounds = view.appBounds()
        windows.filter { !it.closing }.forEach { window ->
            val pane = when(window.id) { workspace.primary -> 0; workspace.secondary -> if(workspace.split) 1 else -1; else -> -1 }
            val shown = pane in bounds.indices && !window.minimized
            val before = autoPanes.put(window.id, if (shown) pane else -1) ?: -1
            val v = window.view
            if (pane in bounds.indices) {
                val fromX = v.x
                val fromY = v.y
                setFrame(window,bounds[pane],resizeDisplays)
                window.view.titleBar.visibility=View.GONE
                if (shown && before < 0) {
                    // Comes into its pane: rises and fades in (continuing an open effect already running).
                    if (v.visibility != View.VISIBLE || v.alpha >= 1f) {
                        v.alpha = 0f; v.scaleX = .94f; v.scaleY = .94f
                    }
                    v.visibility = View.VISIBLE
                    v.animate().alpha(1f).scaleX(1f).scaleY(1f)
                        .setDuration(settings.duration(PlasmaTheme.LONG_MS + 80))
                        .setInterpolator(PlasmaTheme.outCubic).start()
                } else if (shown && before != pane && resizeDisplays) {
                    // Moves to the other pane (swap): it slides there.
                    val toX = v.x
                    val toY = v.y
                    v.x = fromX; v.y = fromY
                    v.animate().x(toX).y(toY).alpha(1f).scaleX(1f).scaleY(1f)
                        .setDuration(settings.duration(PlasmaTheme.LONG_MS + 80))
                        .setInterpolator(PlasmaTheme.inOutCubic).start()
                } else if (shown && v.visibility != View.VISIBLE) {
                    v.visibility = View.VISIBLE
                }
            }
            if (!shown && v.visibility == View.VISIBLE) {
                if (before >= 0) {
                    // Leaves its pane: fades out under the application taking its place.
                    v.animate().alpha(0f).scaleX(.97f).scaleY(.97f)
                        .setDuration(settings.duration(PlasmaTheme.LONG_MS))
                        .setInterpolator(PlasmaTheme.inCubic)
                        .withEndAction {
                            if (autoPanes[window.id] == -1 && !window.closing) {
                                v.visibility = View.INVISIBLE
                                v.alpha = 1f; v.scaleX = 1f; v.scaleY = 1f
                            }
                        }.start()
                } else v.visibility = View.INVISIBLE
            }
        }
        view.refreshChrome()
    }
    /** The pane each Auto window was last laid out in (-1 when hidden), to animate what changed. */
    private val autoPanes = HashMap<Int, Int>()
    private var autoArtworkKey: String? = null
    private var autoArtwork: android.graphics.Bitmap? = null

    private fun updateAutoStatus() {
        if (!autoMode || autoStatusBusy || !running) return
        autoStatusBusy=true
        systemExecutor.execute {
            val media=runCatching { systemControls.nowPlaying() }.getOrNull()
            // Artwork comes from the media session itself; fetch it again only when the track changes.
            val key = media?.let { "${it.packageName}|${it.title}|${it.artist}" }
            val artwork = if (key == autoArtworkKey) autoArtwork else media?.let { mediaArtwork(it.packageName) }
            handler.post {
                autoStatusBusy=false
                if (!running) return@post
                autoMediaInfo=media
                autoArtworkKey = key
                autoArtwork = artwork
                autoUi?.updateTray(trayState)
                autoUi?.updateStatus(moe.n4tsu.dextop.CardexRelayService.activeMode(),media,artwork)
            }
        }
    }

    /** Album art of the playing session; needs Dextop's notification access, otherwise null. */
    private fun mediaArtwork(packageName: String): android.graphics.Bitmap? = runCatching {
        val sessions = service.getSystemService(android.media.session.MediaSessionManager::class.java)
            .getActiveSessions(DextopNotificationListener.component(service))
        val metadata = (sessions.firstOrNull { it.packageName == packageName } ?: sessions.firstOrNull())?.metadata
            ?: return@runCatching null
        listOf(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART, android.media.MediaMetadata.METADATA_KEY_ART,
            android.media.MediaMetadata.METADATA_KEY_DISPLAY_ICON).firstNotNullOfOrNull(metadata::getBitmap)
    }.getOrNull()

    private var popup: View? = null
    private var popupKind: String? = null
    private var popupDismissedAt = 0L
    private var popupDismissedKind: String? = null
    /** Whether the shell overlay may take input focus; see [updateShellFocus]. */
    private var shellFocusable = !passiveShellFocus
    private var kickoff: KickoffView? = null
    private var krunner: KRunnerView? = null
    private var tooltip: PanelTooltipView? = null
    private var tooltipRunnable: Runnable? = null
    private var switcher: TaskSwitcherView? = null
    private var switcherRequested = false
    private var leaveScreen: LeaveScreen? = null

    // interaction
    private var moving: V2Window? = null
    private val moveOffset = android.graphics.PointF()
    private var snapZone = TileZone.NONE
    private var resizing: V2Window? = null
    private var resizeEdge = V2Edge.BOTTOM
    private val resizeStartFrame = Rect()
    private val resizeStart = android.graphics.PointF()
    private var resizeDisplayPending = false

    // overview
    private var overviewActive = false
    private var overviewSearch: PlasmaSearchField? = null
    private val overviewTargets = HashMap<Int, RectF>()

    // keyboard
    private var metaDown = false
    private var metaCombo = false
    private var altDown = false
    private var ctrlDown = false
    private var shiftDown = false
    private val swallowedKeys = HashSet<Int>()
    private var lastHotCornerAt = 0L

    private fun dp(value: Int) = layer.dp(value)
    private val shellTitle get() = if (autoMode) "Dextop Auto" else "Dextop Plasma v2"
    private val titleHeight get() = if (autoMode) 0 else dp(30)
    private val grip get() = if (autoMode) 0 else dp(7)
    // endregion

    private fun invalidateDesktopPanel() {
        if (::panel.isInitialized) panel.invalidate()
        // Auto's task bar shows the same titles and icons.
        if (autoMode) autoUi?.updateTasks()
    }

    // region lifecycle
    override fun start() {
        if (running) return
        running = true
        if (applicationOverlay && !Settings.canDrawOverlays(service)) {
            // The privileged shell may grant Dextop's own overlay app-op.
            host.privilegedCommand("appops", "set", service.packageName, "SYSTEM_ALERT_WINDOW", "allow")
            host.log("Plasma v2 overlay permission granted=${Settings.canDrawOverlays(service)}")
        }
        appRepository.refresh { invalidateDesktopPanel(); if (autoMode) autoUi?.refreshPage() }
        if (!autoMode) ensureDefaults()
        buildRoot()
        LinuxContainerClient.addListener(linuxListener)
        // Auto lists the Linux applications but does not start every container's apps on connect.
        refreshLinuxApps(resumeWindows = !autoMode)
        NotificationCenter.addListener(notificationsChanged)
        enableNotificationAccess()
        refreshTray()
        handler.postDelayed(pollRunnable, POLL_INTERVAL_MS)
        handler.post(capturePollRunnable)
        handler.postDelayed(trayTicker, 30_000L)
        registerTrayReceiver()
        host.log("Plasma v2 compositor started display=$displayId ${width}x$height/$densityDpi")
    }

    override fun stop(restoreWindows: Boolean) {
        handler.removeCallbacks(hideVolumeOsd)
        hideVolumeOsd.run()
        windows.forEach { it.view.content.releasePointerCapture(); captureLost(it) }
        if (!running) return
        running = false
        handler.removeCallbacksAndMessages(null)
        // Linux applications keep running; the next session shows their windows again.
        LinuxContainerClient.removeListener(linuxListener)
        NotificationCenter.removeListener(notificationsChanged)
        val snapshot = windows.toList()
        windows.clear()
        // Applications move back to the Dextop display so the platform
        // desktop shows them after the compositor goes away.
        if (autoMode) saveAutoSession(snapshot)
        wmExecutor.execute {
            snapshot.forEach { window ->
                if (window.displayId >= 0) {
                    runCatching {
                        bridge.tasks(window.displayId).forEach { task ->
                            when {
                                // When Android Auto drops, the car's applications are kept, behind the
                                // phone's home screen, so the next connection reopens them as they were.
                                autoMode -> if (bridge.moveTaskToDisplay(task, android.view.Display.DEFAULT_DISPLAY)) bridge.sendToBack(task)
                                restoreWindows -> bridge.moveTaskToDisplay(task, displayId)
                                else -> bridge.close(task)
                            }
                        }
                    }
                }
                runCatching { window.display?.attachment?.release() }
            }
        }
        wmExecutor.shutdown()
        linuxDisplayUpdates.close()
        linuxDisplayExecutor.shutdown()
        inputExecutor.shutdown()
        systemExecutor.shutdown()
        runCatching { service.unregisterReceiver(trayReceiver) }
        layer.clear()
        appRepository.shutdown()
        host.log("Plasma v2 compositor stopped display=$displayId")
    }

    override fun onDisplayResized(newWidth: Int, newHeight: Int) {
        if (newWidth == width && newHeight == height) { syncLinuxDisplays(); return }
        width = newWidth
        height = newHeight
        layer.show(root, Rect(0, 0, width, height), focusable = shellFocusable, title = shellTitle)
        if (autoMode) root.setBackgroundColor(AutoColors(palette).background) else wallpaper.refresh()
        layoutPanel()
        syncLinuxDisplays(force = true)
        if (autoMode) { handler.post { layoutAutoWorkspace() }; return }
        windows.forEach { window ->
            when {
                window.linuxFullscreen -> setFrame(window, Rect(0, 0, width, height), resizeDisplay = true)
                window.maximized -> setFrame(window, workArea(), resizeDisplay = true)
                window.tile != TileZone.NONE -> setFrame(window, tileRect(window.tile), resizeDisplay = true)
                else -> setFrame(window, clampFrame(Rect(window.frame)), resizeDisplay = true)
            }
        }
    }

    override fun onWindowsChanged() {
        handler.removeCallbacks(pollRunnable)
        handler.postDelayed(pollRunnable, 80L)
    }

    override fun reloadSettings() {
        val previousDesktops = settings.virtualDesktops
        settings = PlasmaSettings.load(service)
        palette = resolveAutoPalette()
        if (settings.virtualDesktops < previousDesktops) {
            val last = settings.virtualDesktops - 1
            windows.filter { it.desktop > last }.forEach { it.desktop = last }
            if (currentDesktop > last) switchDesktop(last, animate = false)
        }
        if (autoMode) root.setBackgroundColor(AutoColors(palette).background) else wallpaper.refresh()
        windows.forEach {
            it.view.refreshDecorations()
            it.view.titleBar.invalidate()
        }
        updatePanelFloat(force = true)
        invalidateDesktopPanel()
        if (autoMode) { layoutAutoWorkspace();autoUi?.refreshPage() }
    }

    private val trayTicker = object : Runnable {
        override fun run() {
            if (!running) return
            refreshTray()
            handler.postDelayed(this, 30_000L)
        }
    }

    private fun ensureDefaults() {
        if (prefs.contains("favorites")) return
        val candidates = listOf(
            "com.android.chrome", "org.mozilla.firefox", "com.sec.android.app.sbrowser",
            "com.google.android.apps.nbu.files", "com.sec.android.app.myfiles", "com.google.android.documentsui",
            "com.android.settings", "com.android.vending", "com.google.android.youtube",
            "com.google.android.gm", "com.google.android.calendar", "com.sec.android.app.popupcalculator",
            "com.google.android.calculator", "com.google.android.apps.photos", "com.sec.android.gallery3d",
        ).filter { runCatching { service.packageManager.getLaunchIntentForPackage(it) != null }.getOrDefault(false) }
        prefs.edit()
            .putString("favorites", candidates.take(12).joinToString(","))
            .putString("pinned", candidates.filter {
                it in setOf("com.android.settings", "com.android.vending", "com.android.chrome", "org.mozilla.firefox",
                    "com.sec.android.app.sbrowser", "com.sec.android.app.myfiles", "com.google.android.apps.nbu.files")
            }.take(4).joinToString(","))
            .apply()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildRoot() {
        root = object : V2WindowInputRoot(displayContext, { event ->
            windows.lastOrNull { isShown(it) && it.view.ownsFramePointer(event) }?.view
        }) {
            // Keys normally arrive through the accessibility service. Some
            // devices (Samsung) deliver hardware keys to this focused overlay
            // instead; route them to the Linux window like the service does.
            override fun dispatchKeyEvent(event: KeyEvent): Boolean =
                ((event.keyCode in setOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN,
                    KeyEvent.KEYCODE_VOLUME_MUTE) || activeLinuxContainer() != null) && handleKey(event)) ||
                    super.dispatchKeyEvent(event)
        }.apply {
            clipChildren = false
            isFocusable = true
            isFocusableInTouchMode = true
        }
        if (autoMode) {
            // Auto owns the entire Surface, including the host's reserved margins.
            // Only application composition is shared with the desktop shell.
            root.setBackgroundColor(AutoColors(palette).background)
        } else {
            root.addView(wallpaper, FrameLayout.LayoutParams(-1, -1))
        }
        dim = View(displayContext).apply {
            setBackgroundColor(0xB0000000.toInt())
            alpha = 0f
            visibility = View.GONE
        }
        root.addView(dim, FrameLayout.LayoutParams(-1, -1))
        windowsLayer = FrameLayout(displayContext).apply { clipChildren = false }
        root.addView(windowsLayer, FrameLayout.LayoutParams(-1, -1))
        popupLayer = FrameLayout(displayContext)
        root.addView(popupLayer, FrameLayout.LayoutParams(-1, -1))
        linuxPopupLayer = LinuxPopupLayer(displayContext)
        root.addView(linuxPopupLayer, FrameLayout.LayoutParams(-1, -1))
        linuxIme = LinuxImeTarget(
            displayContext,
            onText = { text -> activeLinuxContainer()?.let { id -> LinuxContainerClient.service()?.text(id, text) } },
            onKey = { event -> activeLinuxContainer()?.let { id -> forwardLinuxKey(id, event) } },
        )
        root.addView(linuxIme, FrameLayout.LayoutParams(1, 1))
        if (autoMode) {
            buildAutoUi()
        } else {
            buildDesktopChrome()
        }
        layer.show(root, Rect(0, 0, width, height), focusable = shellFocusable, title = shellTitle)
    }

    /** Constructed exclusively for the Plasma desktop, never for the car shell. */
    private fun buildDesktopChrome() {
        snapPreview = SnapPreviewView(displayContext) { palette }
        root.addView(snapPreview, FrameLayout.LayoutParams(-1, -1))
        overviewChrome = FrameLayout(displayContext).apply { visibility = View.GONE }
        root.addView(overviewChrome, FrameLayout.LayoutParams(-1, -1))
        panel = PlasmaPanelView(displayContext, this)
        panelFloat = if (settings.floatingPanel) 1f else 0f
        panelFloatTarget = panelFloat
        panel.floatFraction = panelFloat
        root.addView(panel, FrameLayout.LayoutParams(-1, panel.thickness + panel.floatMargin * 2, Gravity.BOTTOM))
        // Desktop chrome stays behind transient menus and Linux popups.
        popupLayer.bringToFront()
        linuxPopupLayer.bringToFront()
        val corner = View(displayContext)
        corner.setOnHoverListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_HOVER_ENTER) triggerHotCorner()
            true
        }
        corner.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) triggerHotCorner()
            false
        }
        root.addView(corner, FrameLayout.LayoutParams(dp(2), dp(2)))
        layoutPanel()
    }
    // endregion

    // region windows: creation, displays and tasks
    private fun createWindow(
        packageName: String,
        frame: Rect,
        adoptTask: TaskSnapshot? = null,
        intent: Intent? = null,
    ): V2Window {
        val window = V2Window(nextWindowId++, packageName, displayContext, this, titleHeight, grip)
        window.launchIntent = intent
        window.title = appRepository.label(packageName)
        window.icon = appRepository.icon(packageName)
        window.desktop = currentDesktop
        window.pendingAdoption = adoptTask
        window.frame.set(frame)
        if (autoMode) {
            window.maximized = true
            window.view.titleBar.visibility = View.GONE
        }
        windows += window
        val params = FrameLayout.LayoutParams(frame.width() + grip * 2, frame.height() + grip * 2)
        windowsLayer.addView(window.view, params)
        window.view.x = (frame.left - grip).toFloat()
        window.view.y = (frame.top - grip).toFloat()
        // KWin "Scale" open effect, with live content.
        window.view.alpha = 0f
        window.view.scaleX = .86f
        window.view.scaleY = .86f
        window.view.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(settings.duration(PlasmaTheme.LONG_MS + 60))
            .setInterpolator(PlasmaTheme.outCubic).start()
        activate(window)
        return window
    }

    override fun onSurfaceReady(window: V2Window, surface: Surface, width: Int, height: Int) {
        if (window.packageName.startsWith("linux-launch:") || window.packageName.startsWith("linux-error:")) return
        // Steam Deck Mode shows its cover until gamescope maps.
        if (window.steamMode != null && window.linuxXid == 0) return
        if (window.linuxXid != 0) {
            attachLinuxWindow(window, surface)
            return
        }
        window.linuxContainer?.let { id ->
            attachLinuxDesktop(window, id, surface, width, height)
            return
        }
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        val existing = window.display
        if (existing != null) {
            val dpi = densityFor(window)
            windowDensity[window.id] = dpi
            wmExecutor.execute { runCatching { existing.resize(surface, window.displayWidth, window.displayHeight, dpi) } }
            return
        }
        window.displayWidth = w
        window.displayHeight = h
        val dpi = densityFor(window)
        windowDensity[window.id] = dpi
        wmExecutor.execute {
            val created = runCatching {
                displayPlatform.openApplicationDisplay(
                    service, displayService, "DextopWindow-${window.id}", surface, w, h, dpi,
                    requestedRefreshRate = applicationDisplayRefreshRate(),
                    publicDisplay = !privateAppDisplays,
                )
            }.onFailure { host.log("Plasma v2 window display failed: ${it.message}") }.getOrNull()
            handler.post {
                if (created == null) {
                    removeWindowView(window)
                    return@post
                }
                if (window.closing || !running) {
                    wmExecutor.execute { runCatching { created.attachment.release() } }
                    return@post
                }
                window.display = created
                window.displayId = created.displayId
                wmExecutor.execute {
                    showImeInside(created.displayId)
                    if (fullscreenAppDisplays) useFullscreenWindowing(created.displayId)
                }
                host.log("Plasma v2 window ${window.id} display=${created.displayId} ${w}x$h pkg=${window.packageName}")
                val adopt = window.pendingAdoption
                if (adopt != null) {
                    wmExecutor.execute { moveTaskIntoWindow(adopt, created.displayId) }
                } else if (fullscreenAppDisplays) {
                    // Launch only after the display's windowing mode is set.
                    wmExecutor.execute {
                        handler.post { if (running && !window.closing) startOnDisplay(window, newInstance = false) }
                    }
                } else {
                    startOnDisplay(window, newInstance = false)
                }
            }
        }
    }

    override fun onSurfaceDestroyed(window: V2Window) {
        window.linuxContainer?.let { id ->
            LinuxContainerClient.service()?.runCatching {
                if (window.linuxXid != 0) detachWindow(id, window.linuxXid) else detachSurface(id)
            }
        }
        // Detaching only happens when the window is removed; the display is released there.
    }

    private fun startOnDisplay(window: V2Window, newInstance: Boolean) {
        val intent = window.launchIntent?.let(::Intent)
            ?: service.packageManager.getLaunchIntentForPackage(window.packageName) ?: run {
                closeWindowNow(window)
                return
            }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        if (newInstance) intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
        startIntentOnDisplay(intent, window.displayId)
    }

    private fun startIntentOnDisplay(intent: Intent, targetDisplay: Int) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(targetDisplay)
        val direct = runCatching { service.startActivity(intent, options.toBundle()) }
        if (direct.isFailure) {
            // The window display is owned by the privileged runtime; launch as
            // its owner when the platform refuses Dextop's own uid.
            val component = intent.component ?: intent.resolveActivity(service.packageManager)
            if (component != null) {
                host.privilegedExecute(
                    "am", "start", "--display", targetDisplay.toString(), "--windowingMode", "1",
                    "-n", component.flattenToShortString(), "-f", intent.flags.toString(),
                )
            }
            host.log("Plasma v2 direct launch refused (${direct.exceptionOrNull()?.message}); used privileged launch")
        }
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            pollTasks()
            syncLinuxDisplays()
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    /** Tracks tasks per window display and adopts tasks that appear on the Dextop display itself. */
    private fun pollTasks() {
        val targets = windows.filter { it.displayId >= 0 }.map { it.id to it.displayId }
        wmExecutor.execute {
            val perWindow = targets.associate { (id, display) ->
                id to runCatching { bridge.tasks(display) }.getOrNull()
            }
            val strays = runCatching {
                bridge.tasks(displayId).filter { task ->
                    task.packageName.isNotEmpty() && task.packageName != service.packageName &&
                        task.activityType in setOf(TaskBridge.ACTIVITY_TYPE_UNDEFINED, TaskBridge.ACTIVITY_TYPE_STANDARD)
                }
            }.getOrDefault(emptyList())
            handler.post { if (running) applyTasks(perWindow, strays) }
        }
    }

    private fun applyTasks(perWindow: Map<Int, List<TaskSnapshot>?>, strays: List<TaskSnapshot>) {
        val now = SystemClock.uptimeMillis()
        windows.toList().forEach { window ->
            val tasks = perWindow[window.id] ?: return@forEach
            val top = tasks.firstOrNull { it.packageName.isNotEmpty() }
            if (top != null) {
                window.hadTask = true
                if (window.taskId != top.taskId) {
                    window.taskId = top.taskId
                    if (autoMode) saveAutoSession()
                }
                window.emptySince = 0L
                if (top.packageName != window.packageName) {
                    window.packageName = top.packageName
                    window.icon = appRepository.icon(top.packageName)
                }
                val label = top.label?.takeIf { it.isNotBlank() } ?: appRepository.label(top.packageName)
                if (label != window.title) {
                    window.title = label
                    window.view.titleBar.invalidate()
                }
            } else if (window.hadTask) {
                if (window.emptySince == 0L) window.emptySince = now
                if (now - window.emptySince > EMPTY_CLOSE_MS) closeWindowNow(window)
            } else if (now - window.createdAt > LAUNCH_TIMEOUT_MS) {
                // The launch went elsewhere (task already alive on another
                // display); pull it into this window, or give up.
                adoptRunningTask(window)
            }
        }
        strays.filter { it.taskId !in adoptedTaskIds }.forEach { task ->
            adoptedTaskIds += task.taskId
            val frame = if (task.windowingMode == TaskBridge.WINDOWING_MODE_FREEFORM && !task.bounds.isEmpty) {
                clampFrame(Rect(task.bounds.left, task.bounds.top - titleHeight, task.bounds.right, task.bounds.bottom))
            } else smartPlacement()
            createWindow(task.packageName, frame, adoptTask = task)
        }
        invalidateDesktopPanel()
        if (autoMode) autoUi?.updateTasks()
    }

    private fun adoptRunningTask(window: V2Window) {
        if (window.pendingAdoption != null || window.adoptAttempted || window.displayId < 0) {
            closeWindowNow(window)
            return
        }
        window.adoptAttempted = true
        val display = window.displayId
        val pkg = window.packageName
        wmExecutor.execute {
            val manager = service.getSystemService(DisplayManager::class.java)
            val candidate = manager.displays.asSequence()
                .filter { it.displayId != display }
                .flatMap { runCatching { bridge.tasks(it.displayId) }.getOrDefault(emptyList()).asSequence() }
                .firstOrNull { it.packageName == pkg && it.activityType == TaskBridge.ACTIVITY_TYPE_STANDARD }
            val moved = candidate != null && moveTaskIntoWindow(candidate, display)
            handler.post {
                if (!moved && running) {
                    host.log("Plasma v2 could not place $pkg in window ${window.id}")
                    closeWindowNow(window)
                }
            }
        }
    }

    private fun closeWindowNow(window: V2Window) {
        if (window.closing) return
        window.steamMode?.let(LinuxContainerClient::releaseDeckAudio)
        window.view.content.releasePointerCapture(); captureLost(window)
        window.closing = true
        windows.remove(window)
        steamFrameCounts.remove(window.id)
        steamDiagnosticAt.remove(window.id)
        steamReady.remove(window.id)
        steamCovers.remove(window.id)
        steamLayers.remove(window.id)
        steamStarting -= window.id
        if (autoMode) {
            workspace.remove(window.id)
            layoutAutoWorkspace()
            autoUi?.updateTasks()
            if (workspace.primary==null) autoUi?.showHome()
        }
        if (activeId == window.id) activeId = windows.filter { isShown(it) }.maxByOrNull { it.lastActivated }?.id ?: -1
        window.view.animate().alpha(0f).scaleX(.85f).scaleY(.85f)
            .setDuration(settings.duration(PlasmaTheme.LONG_MS))
            .setInterpolator(PlasmaTheme.inCubic)
            .withEndAction { removeWindowView(window) }
            .start()
        updatePanelFloat()
        invalidateDesktopPanel()
    }

    private fun removeWindowView(window: V2Window) {
        windows.remove(window)
        windowDensity.remove(window.id)
        autoPanes.remove(window.id)
        windowsLayer.removeView(window.view)
        if (autoMode) { workspace.remove(window.id);layoutAutoWorkspace();autoUi?.updateTasks() }
        val display = window.display
        window.display = null
        if (display != null) runCatching { wmExecutor.execute { runCatching { display.attachment.release() } } }
        invalidateDesktopPanel()
    }
    // endregion

    // region input
    /**
     * Shows the keyboard on the application display itself, so it is drawn
     * inside the window like on a tablet.  The default for a display without
     * system decorations is the phone screen, which Dextop covers.
     */
    private fun showImeInside(appDisplay: Int) {
        val type = Class.forName("android.view.IWindowManager")
        runCatching {
            type.getMethod("setDisplayImePolicy", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                .invoke(windowService, appDisplay, DISPLAY_IME_POLICY_LOCAL)
        }.recoverCatching {
            type.getMethod("setShouldShowIme", Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
                .invoke(windowService, appDisplay, true)
        }.onFailure { host.log("Plasma v2 IME policy failed display=$appDisplay: ${it.message}") }
    }

    /**
     * The phone panel's highest rate for its current resolution when the
     * maximum-refresh-rate option is on; null keeps the 60 Hz default.
     */
    private fun applicationDisplayRefreshRate(): Float? {
        if (!maxRateAppDisplays || !DisplayEnvironmentSettings(service).maximumMirrorRefreshRateEnabled()) return null
        val panel = service.getSystemService(DisplayManager::class.java)
            .getDisplay(android.view.Display.DEFAULT_DISPLAY) ?: return null
        val mode = panel.mode
        return chooseMirrorRefreshRate(
            panel.supportedModes
                .filter { it.physicalWidth == mode.physicalWidth && it.physicalHeight == mode.physicalHeight }
                .map { it.refreshRate }
        )
    }

    // region Linux application windows
    /** Kickoff entries for the desktop entries of every installed container. */
    @Volatile private var linuxApps: List<PlasmaApp> = emptyList()
    private lateinit var linuxPopupLayer: LinuxPopupLayer
    private var linuxDisplayRate = 0f
    private fun linuxRefreshRate(): Float = service.getSystemService(DisplayManager::class.java)
        .getDisplay(displayId)?.refreshRate?.takeIf { it.isFinite() && it > 0f } ?: 60f

    private fun syncLinuxDisplays(force: Boolean = false) {
        val rate = linuxRefreshRate()
        if (!force && kotlin.math.abs(rate - linuxDisplayRate) < .01f) return
        val linux = LinuxContainerClient.service() ?: return
        linuxDisplayRate = rate
        val containers = windows.filter { it.linuxXid != 0 }.mapNotNull { it.linuxContainer } + linuxTrayItems.values.map { it.container }
        containers.distinct().forEach { id -> queueLinuxDisplay(id, true, width, height, densityDpi, rate) }
        windows.filter { it.linuxContainer != null && it.linuxXid == 0 }.forEach { window ->
            queueLinuxDisplay(window.linuxContainer!!, false, window.clientWidth(), window.clientHeight(titleHeight), densityDpi, rate)
        }
    }

    private val linuxTrayItems = LinkedHashMap<Pair<String, String>, PlasmaLinuxTrayItem>()
    private var linuxTrayOverflow: TrayOverflowApplet? = null

    private fun updateLinuxTray() {
        if (popupKind == "tray:${TrayItem.EXPAND}") linuxTrayOverflow?.updateLinuxItems(linuxTrayItems.values.toList())
        invalidateDesktopPanel()
    }

    private fun invokeLinuxTray(item: PlasmaLinuxTrayItem, button: Int, x: Float, y: Float) {
        inputExecutor.execute { LinuxContainerClient.service()?.runCatching { trayAction(item.container, item.key, button, x.roundToInt(), y.roundToInt()) } }
    }

    private fun showLinuxTrayMenu(container: String, key: String, entries: JSONArray, x: Float, y: Float) {
        var separator = false
        val menu = buildList {
            for (i in 0 until entries.length()) {
                val item = entries.optJSONObject(i) ?: continue
                if (item.optBoolean("separator")) { separator = true; continue }
                val children = item.optJSONArray("children") ?: JSONArray()
                val label = item.optString("label").replace(Regex("_(.)"), "$1")
                add(MenuItem(label + if (children.length() > 0) " ›" else "",
                    checked = if (item.has("checked")) item.optBoolean("checked") else null,
                    enabled = item.optBoolean("enabled", true), separatorBefore = separator) {
                    if (children.length() > 0) showLinuxTrayMenu(container, key, children, x, y)
                    else invokeLinuxTray(PlasmaLinuxTrayItem(container, "$key#menu=${item.optInt("id")}", label, null), 1, x, y)
                })
                separator = false
            }
        }
        if (menu.isNotEmpty()) showMenu(menu, x, y, kind = "linux-tray-menu", above = true)
    }


    private val linuxListener = object : LinuxContainerClient.Listener {
        override fun onConnectionChanged(connected: Boolean) {
            if (!connected) { linuxTrayItems.clear(); updateLinuxTray() }
        }

        override fun onWindowEvent(containerId: String, event: JSONObject) {
            if (running) handleLinuxWindowEvent(containerId, event)
        }

        override fun onContainersChanged() {
            if (running) refreshLinuxApps(resumeWindows = false)
        }

        override fun onNotification(containerId: String, event: JSONObject) {
            if (!running) return
            val key = "linux:$containerId:${event.optString("key")}"
            when (event.optString("type")) {
                "notify" -> NotificationCenter.put(DextopNotification(
                    key = key, linux = true,
                    app = event.optString("app").ifBlank { "Linux" },
                    title = event.optString("summary"), text = event.optString("body"),
                    time = event.optLong("time", System.currentTimeMillis()),
                    icon = linuxApps.firstOrNull { app ->
                        app.packageName.startsWith("$LINUX_APP_PREFIX$containerId:") &&
                            app.label.equals(event.optString("app"), ignoreCase = true)
                    }?.icon,
                    owner = containerId, clearable = true,
                ))
                "close" -> NotificationCenter.remove(key)
            }
        }

        override fun onClipboard(containerId: String, text: String) {
            if (!running) return
            lastLinuxClipboard = text
            runCatching {
                service.getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("Linux", text))
            }.onFailure { host.log("Plasma v2 Linux clipboard to Android failed: ${it.message}") }
        }
    }

    private val notificationsChanged: () -> Unit = { invalidateDesktopPanel() }

    /** Lets Dextop read Android's notifications for the tray (granted through the privileged shell). */
    private fun enableNotificationAccess() {
        val component = DextopNotificationListener.component(service).flattenToString()
        systemExecutor.execute {
            runCatching { host.privilegedCommand("cmd", "notification", "allow_listener", component) }
        }
    }

    private fun openNotification(item: DextopNotification) {
        dismissPopup()
        if (item.linux) {
            val (containerId, key) = item.key.removePrefix("linux:").split(':', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
            LinuxContainerClient.service()?.runCatching { notificationAction(containerId, key, "default") }
            windows.filter { it.linuxContainer == containerId && !it.closing }.maxByOrNull { it.lastActivated }?.let(::activate)
            NotificationCenter.remove(item.key)
        } else {
            // The app opens in (or raises) its Plasma window.
            launch(item.owner)
        }
    }

    private fun dismissNotification(item: DextopNotification) {
        if (item.linux) {
            val (containerId, key) = item.key.removePrefix("linux:").split(':', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
            LinuxContainerClient.service()?.runCatching { notificationAction(containerId, key, "dismiss") }
            NotificationCenter.remove(item.key)
        } else {
            DextopNotificationListener.cancel(item.key)
        }
    }

    /** Clipboard text last exchanged with Linux, so it is not sent back and forth. */
    private var lastLinuxClipboard: String? = null

    /**
     * Gives Linux the Android clipboard before a paste. Android lets Dextop
     * read it here because a Dextop window has input focus.
     */
    private fun syncClipboardToLinux(containerId: String) {
        val text = runCatching {
            service.getSystemService(ClipboardManager::class.java).primaryClip
                ?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(service)?.toString()
        }.getOrNull() ?: return
        if (text.isEmpty() || text == lastLinuxClipboard) return
        lastLinuxClipboard = text
        LinuxContainerClient.service()?.runCatching { setClipboard(containerId, text) }
    }

    private fun linuxEnabled(): Boolean =
        service.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE).getBoolean("flutter.linux_enabled", false)

    /**
     * Loads the applications of every installed container for Kickoff. With
     * [resumeWindows], application displays still running from an earlier
     * Plasma session send their windows again.
     */
    private fun refreshLinuxApps(resumeWindows: Boolean) {
        if (!linuxEnabled() || !LinuxContainerClient.privilegeAvailable()) {
            linuxApps = emptyList()
            return
        }
        val locale = java.util.Locale.getDefault().toLanguageTag()
        val resources = service.resources
        val fallback = palette.text
        val screenWidth = width
        val screenHeight = height
        val dpi = densityDpi
        LinuxContainerClient.call(service, { linux ->
            val containers = JSONArray(linux.listContainers())
            val installed = (0 until containers.length()).map(containers::getJSONObject)
                .filter { it.optString("state") == "installed" }
            if (resumeWindows) {
                installed.filter { it.optBoolean("appsRunning") }.forEach { container ->
                    runCatching {
                        val id = container.getString("id")
                        linux.startApps(id, screenWidth, screenHeight, dpi)
                        linux.updateDisplay(id, true, screenWidth, screenHeight, dpi, linuxRefreshRate())
                    }
                }
            }
            // The default container chosen in the Linux settings; "" lists every container.
            val preferred = runCatching { JSONObject(linux.linuxSettings()).optString("defaultContainer") }.getOrDefault("")
            val shown = installed.filter { preferred.isEmpty() || it.optString("id") == preferred }
                .ifEmpty { installed }
            val multiple = shown.size > 1
            shown.flatMap { container ->
                val id = container.getString("id")
                val entries = runCatching { JSONArray(linux.listApps(id, locale)) }.getOrNull() ?: return@flatMap emptyList()
                (0 until entries.length()).map(entries::getJSONObject).map { entry ->
                    val name = entry.optString("name")
                    val icon = entry.optString("icon").takeIf { it.isNotEmpty() }?.let { encoded ->
                        runCatching {
                            val bytes = android.util.Base64.decode(encoded, android.util.Base64.DEFAULT)
                            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                ?.let { android.graphics.drawable.BitmapDrawable(resources, it) }
                        }.getOrNull()
                    } ?: GlyphDrawable(Glyph.WINDOW, fallback)
                    PlasmaApp(
                        "$LINUX_APP_PREFIX$id:${entry.optString("id")}",
                        if (multiple) "$name (${container.optString("name")})" else name,
                        icon, LINUX_APP_CATEGORY, entry.optString("startupWMClass"),
                    )
                }
            }
        }, { apps ->
            linuxApps = apps
            if (autoMode) autoUi?.refreshPage()
            windows.filter { it.linuxXid != 0 }.forEach { window ->
                window.icon = window.linuxWindowIcon ?: linuxIcon(window.linuxContainer ?: return@forEach, window.linuxWMClass)
                window.view.titleBar.invalidate()
            }
            invalidateDesktopPanel()
        }) { error -> host.log("Plasma v2 Linux applications unavailable: ${error.message}") }
    }

    private fun launchLinuxApp(packageName: String) {
        val (containerId, desktopId) = packageName.removePrefix(LINUX_APP_PREFIX).split(':', limit = 2)
            .takeIf { it.size == 2 } ?: return
        if (desktopId == LinuxSteam.STEAM_MODE_ENTRY) {
            launchSteamMode(containerId, packageName)
            return
        }
        val placeholder = createWindow("linux-launch:$containerId:$desktopId", Rect(workArea()).apply { inset(width() / 12, height() / 12) })
        placeholder.title = linuxApps.firstOrNull { it.packageName == packageName }?.label ?: desktopId
        placeholder.view.titleBar.invalidate()
        placeholder.view.showStatus(t("nativeLinuxDesktopStartingContainer"))
        root.postDelayed({
            if (!placeholder.closing && windows.contains(placeholder)) {
                LinuxContainerClient.call(service, { it.runtimeLogs(containerId) }, { logs ->
                    if (!placeholder.closing) placeholder.view.showError("Application did not open a window within 30 seconds.\n$logs")
                }) { error -> if (!placeholder.closing) placeholder.view.showError(error.message ?: error.toString()) }
            }
        }, 30_000)
        val screenWidth = width
        val screenHeight = height
        val dpi = densityDpi
        LinuxContainerClient.call(service, { linux ->
            linux.startApps(containerId, screenWidth, screenHeight, dpi)
            linux.updateDisplay(containerId, true, screenWidth, screenHeight, dpi, linuxRefreshRate())
            linux.launchApp(containerId, desktopId)
        }, {}) { error -> showLinuxError(containerId, error.message ?: error.toString(), false) }
    }

    private fun showLinuxError(id: String, message: String, desktop: Boolean = true) {
        root.post {
            if (!running) return@post
            val steamMode = steamModeWindow(id)
            if (!desktop && steamMode != null) {
                steamCovers[steamMode.id]?.fail(t("nativeSteamModeFailed"), message)
                return@post
            }
            val existing = windows.firstOrNull { (it.packageName.startsWith("linux-launch:$id:") || it.packageName == "linux-error:$id") && !it.closing }
                ?: windows.firstOrNull { desktop && it.linuxContainer == id && it.linuxXid == 0 && !it.closing }
            val window = existing ?: createWindow("linux-error:$id", Rect(workArea()).apply { inset(width() / 12, height() / 12) }).also {
                it.title = "Linux"
                it.view.titleBar.invalidate()
            }
            window.view.showError(message)
        }
    }

    // Steam Deck Mode: one Dextop window holds gamescope and every other Steam window of the
    // session, behind DroidDeck-style start and end screens, so nothing of it opens loose.
    private val steamCovers = HashMap<Int, SteamModeCover>()
    private val steamLayers = HashMap<Int, SteamModeLayer>()
    /** Steam Deck Mode windows whose launcher is being (re)started; their old log is not read. */
    private val steamStarting = HashSet<Int>()
    private val steamReady = HashSet<Int>()
    private val steamFrameCounts = HashMap<Int, Long>()
    private val steamDiagnosticAt = HashMap<Int, Long>()
    override fun onContentFrame(window: V2Window) {
        if (window.steamMode == null) return
        steamReady += window.id
        steamCovers[window.id]?.takeIf { !it.ended }?.dismiss()
        val count = (steamFrameCounts[window.id] ?: 0L) + 1
        steamFrameCounts[window.id] = count
        if (count == 1L || count == 8L) {
            // A bounded pixel summary diagnoses black buffers without recording window contents.
            window.view.content.getBitmap(16, 16)?.let { bitmap ->
                val pixels = IntArray(256)
                bitmap.getPixels(pixels, 0, 16, 0, 0, 16, 16)
                val colored = pixels.count { (it and 0x00ffffff) > 0x00080808 }
                val opaque = pixels.count { (it ushr 24) > 240 }
                bitmap.recycle()
                android.util.Log.i(TAG, "Steam buffer id=${window.id} frame=$count colored=$colored/256 opaque=$opaque/256")
            }
        }
    }
    private fun logSteamPresentation(window: V2Window) {
        val now = SystemClock.uptimeMillis()
        if (now - (steamDiagnosticAt[window.id] ?: 0L) < 5000L) return
        steamDiagnosticAt[window.id] = now
        val content = window.view.content
        android.util.Log.i(TAG, "Steam presentation id=${window.id} container=${window.steamMode} xid=${window.linuxXid} " +
            "shown=${window.view.isShown} alpha=${window.view.alpha} frame=${window.frame} " +
            "output=${if (window.view.usesDeckSurface) "SurfaceControl" else "TextureView"} texture=${content.isAvailable}/${content.width}x${content.height} " +
            "surface=${window.surface?.isValid} consumed=${steamFrameCounts[window.id] ?: 0L} " +
            "cover=${steamCovers[window.id]?.showing} ready=${window.id in steamReady} " +
            "autoPage=${autoUi?.pageDiagnostic} active=$activeId " +
            "coverView=${steamCovers[window.id]?.visibility}/${steamCovers[window.id]?.alpha} " +
            "children=${steamLayers[window.id]?.diagnostic()}")
    }

    private fun steamModeWindow(containerId: String) = windows.firstOrNull { it.steamMode == containerId && !it.closing }

    private fun launchSteamMode(containerId: String, packageName: String) {
        steamModeWindow(containerId)?.let { activate(it); return }
        val icon = linuxApps.firstOrNull { it.packageName == packageName }?.icon
        val window = createWindow("linux-steammode:$containerId", Rect(workArea()))
        window.steamMode = containerId
        if (autoMode) window.view.enableAutoDeckSurface()
        window.title = t("nativeSteamModeTitle")
        window.icon = icon
        window.view.titleBar.invalidate()
        val cover = SteamModeCover(window.view.context, icon, onRestart = { restartSteamMode(window) }, onClose = { close(window) })
        val layer = SteamModeLayer(window.view.context, containerId)
        window.view.contentFrame.addView(cover, FrameLayout.LayoutParams(-1, -1))
        window.view.contentFrame.addView(layer, FrameLayout.LayoutParams(-1, -1))
        steamCovers[window.id] = cover
        steamLayers[window.id] = layer
        startSteamMode(window, stopFirst = false)
        pollSteamMode(window)
        invalidateDesktopPanel()
    }

    private fun startSteamMode(window: V2Window, stopFirst: Boolean) {
        val containerId = window.steamMode ?: return
        val screenWidth = width
        val screenHeight = height
        val dpi = densityDpi
        val padState = controllerState()
        val deckRate = if (autoMode && service.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
                .getBoolean("flutter.android_auto_deck_30fps", false)) "30\n" else "0\n"
        val deckVsync = if (autoMode && !service.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
                .getBoolean("flutter.android_auto_deck_vsync", true)) "0\n" else "1\n"
        // gamescope and its games take the window's own size (even, as video modes are).
        val contentWidth = window.view.content.width.takeIf { it > 0 } ?: window.clientWidth()
        val contentHeight = window.view.content.height.takeIf { it > 0 } ?: window.clientHeight(titleHeight)
        val size = "${contentWidth.coerceAtLeast(640) and 1.inv()} ${contentHeight.coerceAtLeast(360) and 1.inv()}\n"
        steamReady -= window.id
        steamStarting += window.id
        LinuxContainerClient.call(service, { linux ->
            if (stopFirst) linux.runCommand(containerId, LinuxSteam.STEAM_MODE_STOP, 20)
            linux.guestLog(containerId, LinuxSteam.STEAM_MODE_LOG, 0, true)
            runCatching { linux.setGuestState(containerId, LinuxSteam.STEAM_PADS_STATE, padState) }
            runCatching { linux.setGuestState(containerId, LinuxSteam.STEAM_MODE_SIZE, size) }
            linux.setGuestState(containerId, LinuxSteam.STEAM_MODE_RATE, deckRate)
            linux.setGuestState(containerId, LinuxSteam.STEAM_MODE_VSYNC, deckVsync)
            LinuxContainerClient.prepareDeckAudio(linux, containerId)
            linux.startApps(containerId, screenWidth, screenHeight, dpi)
            linux.updateDisplay(containerId, true, screenWidth, screenHeight, dpi, linuxRefreshRate())
            linux.launchApp(containerId, LinuxSteam.STEAM_MODE_ENTRY)
        }, { steamStarting -= window.id }) { error ->
            LinuxContainerClient.releaseDeckAudio(containerId)
            steamStarting -= window.id
            steamCovers[window.id]?.fail(t("nativeSteamModeFailed"), error.message ?: error.toString())
        }
    }

    /** Follows the launcher's log until the window goes: steps on the cover, then the Deck interface. */
    private fun pollSteamMode(window: V2Window) {
        val containerId = window.steamMode ?: return
        if (window.closing || window !in windows) return
        val next = { delay: Long -> root.postDelayed({ pollSteamMode(window) }, delay) }
        if (window.id in steamStarting) { next(500L); return }
        LinuxContainerClient.call(service, { it.guestLog(containerId, LinuxSteam.STEAM_MODE_LOG, 48 * 1024, false) }, { text ->
            val cover = steamCovers[window.id] ?: return@call
            val progress = if (window.id in steamStarting) null else SteamModeProgress.parse(text)
            if (progress?.ready == true) steamReady += window.id
            when {
                progress == null && window.id in steamReady && window.linuxXid != 0 -> cover.dismiss()
                progress == null -> Unit
                // Shut Down in Steam's power menu.
                progress.exit == 0 -> { closeWindowNow(window); return@call }
                progress.exit != null -> cover.fail(t("nativeSteamModeFailed"), progress.tail)
                window.id in steamReady && window.linuxXid != 0 -> cover.dismiss()
                else -> cover.update(progress)
            }
            logSteamPresentation(window)
            next(if (cover.showing) 700L else 2000L)
        }) { next(2000L) }
    }

    private fun restartSteamMode(window: V2Window) {
        steamCovers[window.id]?.restart()
        startSteamMode(window, stopFirst = true)
    }

    private fun stopSteamMode(window: V2Window) {
        val containerId = window.steamMode ?: return
        LinuxContainerClient.call(service, { it.runCommand(containerId, LinuxSteam.STEAM_MODE_STOP, 20) }, {}) {}
        closeWindowNow(window)
    }

    /** gamescope becomes the wrapper's content; any other Steam window is shown inside the wrapper. */
    private fun adoptSteamModeClient(wrapper: V2Window, containerId: String, xid: Int, event: JSONObject, bounds: Rect): Boolean {
        val name = (event.optString("class") + " " + event.optString("title")).trim().lowercase()
        if (wrapper.linuxXid == xid && wrapper.linuxContainer == containerId) return true
        val gamescope = event.optBoolean("deck") || "gamescope" in name || wrapper.linuxXid == 0 && name.isEmpty()
        if (!gamescope && "steam" !in name) return false
        if (gamescope && wrapper.linuxXid == 0) {
            // A late app_id can promote a previously unknown Steam child.
            steamLayers[wrapper.id]?.remove(xid)
            android.util.Log.i(TAG, "Deck primary attached wrapper=${wrapper.id} container=$containerId xid=$xid class=${event.optString("class")} authenticated=${event.optBoolean("deck")}")
            wrapper.linuxContainer = containerId
            wrapper.linuxXid = xid
            wrapper.linuxWMClass = event.optString("class")
            wrapper.linuxWidth = bounds.width()
            wrapper.linuxHeight = bounds.height()
            wrapper.view.refreshDecorations()
            wrapper.surface?.let { attachLinuxWindow(wrapper, it) }
            LinuxContainerClient.service()?.runCatching { activateWindow(containerId, xid) }
            if (autoMode && activeId == wrapper.id && autoUi?.pageDiagnostic == "none") resumeAutoInput()
        } else {
            steamLayers[wrapper.id]?.show(xid, bounds)
        }
        return true
    }

    /** gamescope went away: the cover stays up until the log says how the mode ended. */
    private fun steamModeContentGone(window: V2Window) {
        val containerId = window.linuxContainer ?: return
        LinuxContainerClient.service()?.runCatching { detachWindow(containerId, window.linuxXid) }
        window.linuxXid = 0
        window.linuxContainer = null
        steamReady -= window.id
        steamCovers[window.id]?.stopping()
    }

    /** Events of Steam windows drawn inside a Steam Deck Mode window; false for every other window. */
    private fun steamModeChildEvent(containerId: String, xid: Int, type: String, bounds: Rect): Boolean {
        val layer = steamLayers.values.firstOrNull { it.containerId == containerId && it.has(xid) } ?: return false
        when (type) {
            "configure", "client-configure" -> layer.place(xid, bounds)
            "activate" -> layer.raise(xid)
            "unmap" -> layer.remove(xid)
        }
        return true
    }

    /** Steam windows besides gamescope (updaters, dialogs), drawn inside their Steam Deck Mode window, centred. */
    private inner class SteamModeLayer(context: Context, val containerId: String) : FrameLayout(context) {
        private inner class Client(val xid: Int) : android.view.TextureView(context) {
            val pointer = LinuxPointerState()
            var surface: Surface? = null
            var bufferWidth = 1
            var bufferHeight = 1

            init {
                isOpaque = false
                surfaceTextureListener = object : SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(texture: android.graphics.SurfaceTexture, w: Int, h: Int) {
                        val created = Surface(texture)
                        surface = created
                        LinuxContainerClient.call(service, { it.attachWindow(containerId, xid, created) }, {}) {}
                    }

                    override fun onSurfaceTextureSizeChanged(texture: android.graphics.SurfaceTexture, w: Int, h: Int) = Unit

                    override fun onSurfaceTextureDestroyed(texture: android.graphics.SurfaceTexture): Boolean {
                        LinuxContainerClient.service()?.runCatching { detachWindow(containerId, xid) }
                        surface?.release()
                        surface = null
                        return true
                    }

                    override fun onSurfaceTextureUpdated(texture: android.graphics.SurfaceTexture) = Unit
                }
                val forward = { event: MotionEvent ->
                    val linux = LinuxContainerClient.service()
                    if (linux != null && width > 0 && height > 0) {
                        forwardLinuxPointer(
                            event, (event.x * bufferWidth / width).roundToInt(), (event.y * bufferHeight / height).roundToInt(), pointer,
                            send = { action, x, y, button -> linux.windowPointer(containerId, xid, action, x, y, button) },
                            scroll = { dx, dy -> linux.scroll("$containerId#apps", dx, dy) },
                            key = { code, down -> linux.key("$containerId#apps", code, down) },
                        )
                    }
                    true
                }
                setOnTouchListener { _, event -> forward(event) }
                setOnHoverListener { _, event -> forward(event) }
                setOnGenericMotionListener { _, event -> forward(event) }
            }
        }

        private val clients = LinkedHashMap<Int, Client>()

        fun diagnostic() = clients.values.joinToString { "${it.xid}:${it.width}x${it.height}:${it.isShown}" }
        fun has(xid: Int) = xid in clients

        fun show(xid: Int, bounds: Rect) {
            val client = clients.getOrPut(xid) { Client(xid).also { addView(it, LayoutParams(1, 1, Gravity.CENTER)) } }
            place(xid, bounds)
            client.bringToFront()
        }

        fun place(xid: Int, bounds: Rect) {
            val client = clients[xid] ?: return
            client.bufferWidth = bounds.width().coerceAtLeast(1)
            client.bufferHeight = bounds.height().coerceAtLeast(1)
            fit(client)
        }

        fun raise(xid: Int) { clients[xid]?.bringToFront() }

        fun remove(xid: Int) { clients.remove(xid)?.let(::removeView) }

        /** Native size, shrunk to fit inside the window with a margin. */
        private fun fit(client: Client) {
            if (width <= 0 || height <= 0) { post { if (clients[client.xid] === client) fit(client) }; return }
            val scale = minOf(1f, width * .92f / client.bufferWidth, height * .92f / client.bufferHeight)
            val params = client.layoutParams as LayoutParams
            val w = (client.bufferWidth * scale).roundToInt().coerceAtLeast(1)
            val h = (client.bufferHeight * scale).roundToInt().coerceAtLeast(1)
            if (params.width != w || params.height != h) {
                params.width = w
                params.height = h
                client.layoutParams = params
            }
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            clients.values.forEach(::fit)
        }
    }

    private fun linuxWindow(containerId: String, xid: Int) =
        windows.firstOrNull { it.linuxContainer == containerId && it.linuxXid == xid && !it.closing }

    private fun handleLinuxWindowEvent(containerId: String, event: JSONObject) {
        val xid = event.optInt("xid")
        val popup = event.optBoolean("popup")
        val bounds = Rect(event.optInt("x"), event.optInt("y"), event.optInt("x") + event.optInt("w"), event.optInt("y") + event.optInt("h"))
        if (event.optString("type") in setOf("map", "class", "unmap")) {
            steamModeWindow(containerId)?.let { wrapper ->
                android.util.Log.i(TAG, "Deck window event wrapper=${wrapper.id} container=$containerId xid=$xid type=${event.optString("type")} class=${event.optString("class")} authenticated=${event.optBoolean("deck")} primary=${wrapper.linuxXid}")
            }
        }
        // xdg_toplevel app_id may arrive after map. Do not swallow this as a
        // child-property event: the native peer authenticated it as our Deck.
        if (!popup && event.optBoolean("deck") && event.optString("type") in setOf("map", "class", "title")) {
            steamModeWindow(containerId)?.let { wrapper ->
                if (adoptSteamModeClient(wrapper, containerId, xid, event, bounds)) return
            }
        }
        if (!popup && event.optString("type") != "map" && steamModeChildEvent(containerId, xid, event.optString("type"), bounds)) return
        when (event.optString("type")) {
            "frame" -> linuxWindow(containerId, xid)?.takeIf { it.steamMode != null }?.let { window ->
                steamReady += window.id
                steamCovers[window.id]?.takeIf { !it.ended }?.dismiss()
                android.util.Log.i(TAG, "Deck first frame submitted container=$containerId xid=$xid cover dismissed")
            }
            "tray" -> {
                val key = event.optString("key")
                linuxTrayItems[containerId to key] = PlasmaLinuxTrayItem(containerId, key, event.optString("title").ifBlank { "Linux" }, decodeLinuxWindowIcon(event.optString("icon")))
                updateLinuxTray()
            }
            "tray-remove" -> { linuxTrayItems.remove(containerId to event.optString("key")); updateLinuxTray() }
            "tray-menu" -> showLinuxTrayMenu(containerId, event.optString("key"), event.optJSONArray("items") ?: JSONArray(), event.optInt("x").toFloat(), event.optInt("y").toFloat())
            "error" -> showLinuxError(containerId, event.optString("message"), event.optString("target") != "apps")
            "reset" -> {
                linuxTrayItems.keys.removeAll { it.first == containerId }; updateLinuxTray()
                windows.filter { it.linuxContainer == containerId || it.steamMode == containerId || it.packageName.startsWith("linux-launch:$containerId:") || it.packageName == "linux-error:$containerId" }.toList().forEach(::closeWindowNow)
            }
            "map" -> {
                val steamMode = if (popup) null else steamModeWindow(containerId)
                if (steamMode != null && adoptSteamModeClient(steamMode, containerId, xid, event, bounds)) return
                if (!popup) windows.filter { it.packageName.startsWith("linux-launch:$containerId:") }.toList().forEach(::closeWindowNow)
                if (popup) linuxPopupLayer.show(containerId, xid, bounds) else mapLinuxWindow(containerId, xid, event, bounds)
            }
            "display-config" -> if (!event.optBoolean("apps")) {
                windows.filter { it.linuxContainer == containerId && it.linuxXid == 0 }.forEach { window ->
                    window.linuxWidth = event.optInt("w"); window.linuxHeight = event.optInt("h")
                }
            }
            "client-configure" -> linuxWindow(containerId, xid)?.let { clientConfiguredLinuxWindow(it, bounds, updateBuffer = false) }
            "configure" -> if (popup) linuxPopupLayer.place(containerId, xid, bounds) else linuxWindow(containerId, xid)?.let { window ->
                if (event.optBoolean("geometryOnly")) {
                    window.linuxWidth = bounds.width(); window.linuxHeight = bounds.height(); window.view.refreshLinuxViewport()
                } else clientConfiguredLinuxWindow(window, bounds)
            }
            "title" -> linuxWindow(containerId, xid)?.takeIf { it.steamMode == null }?.let { window ->
                window.title = event.optString("title").ifBlank { window.title }
                window.view.titleBar.invalidate()
                invalidateDesktopPanel()
            }
            "class" -> linuxWindow(containerId, xid)?.takeIf { it.steamMode == null }?.let { window ->
                window.linuxWMClass = event.optString("class")
                window.packageName = "linuxwin:$containerId:${window.linuxWMClass.lowercase()}"
                window.icon = window.linuxWindowIcon ?: linuxIcon(containerId, window.linuxWMClass)
                window.view.titleBar.invalidate()
                invalidateDesktopPanel()
            }
            "icon" -> linuxWindow(containerId, xid)?.takeIf { it.steamMode == null }?.let { window ->
                window.linuxWindowIcon = decodeLinuxWindowIcon(event.optString("icon"))
                window.icon = window.linuxWindowIcon ?: linuxIcon(containerId, window.linuxWMClass)
                window.view.titleBar.invalidate()
                invalidateDesktopPanel()
            }
            "decorations" -> linuxWindow(containerId, xid)?.takeIf { it.steamMode == null }?.let { window ->
                val oldHeight = window.decorationHeight(titleHeight)
                window.linuxClientDecorated = !event.optBoolean("decorated", true)
                window.view.refreshDecorations()
                val frame = Rect(window.frame)
                frame.bottom += window.decorationHeight(titleHeight) - oldHeight
                setFrame(window, if (window.linuxFullscreen) Rect(0, 0, width, height) else clampFrame(frame), resizeDisplay = true)
            }
            "minimize" -> linuxWindow(containerId, xid)?.let(::minimize)
            "maximize" -> linuxWindow(containerId, xid)?.let { window ->
                if (window.linuxFullscreen) return@let
                val action = event.optInt("action", 2)
                if (action == 2 || action == 1 && !window.maximized || action == 0 && window.maximized)
                    toggleMaximize(window)
            }
            "fullscreen" -> linuxWindow(containerId, xid)?.let { setLinuxFullscreen(it, event.optBoolean("enabled")) }
            "activate" -> linuxWindow(containerId, xid)?.let(::activate)
            "moveresize" -> linuxWindow(containerId, xid)?.let { window ->
                val direction = event.optInt("direction", -1)
                if (direction == 11) {
                    if (window.linuxMoveResize == 8) endMove(window, 0f, 0f) else endResize(window)
                    window.linuxMoveResize = -1
                } else if (direction in 0..8) {
                    window.linuxMoveResize = direction
                    val x = event.optInt("x").toFloat()
                    val y = event.optInt("y").toFloat()
                    if (direction == 8) beginMove(window, x, y) else beginResize(window,
                        arrayOf(V2Edge.TOP_LEFT, V2Edge.TOP, V2Edge.TOP_RIGHT, V2Edge.RIGHT,
                            V2Edge.BOTTOM_RIGHT, V2Edge.BOTTOM, V2Edge.BOTTOM_LEFT, V2Edge.LEFT)[direction], x, y)
                }
            }
            "unmap" -> if (popup) linuxPopupLayer.remove(containerId, xid) else linuxWindow(containerId, xid)?.let { window ->
                if (window.steamMode != null) steamModeContentGone(window) else closeWindowNow(window)
            }
        }
    }

    private fun mapLinuxWindow(containerId: String, xid: Int, event: JSONObject, bounds: Rect) {
        if (linuxWindow(containerId, xid) != null) return
        val area = workArea()
        val w = bounds.width().coerceIn(dp(160), area.width())
        val clientDecorated = !event.optBoolean("decorated", true)
        val h = (bounds.height() + if (clientDecorated) 0 else titleHeight).coerceIn(dp(100), area.height())
        val parent = event.optInt("transientFor").takeIf { it != 0 }?.let { linuxWindow(containerId, it) }
        val origin = if (parent != null) {
            Rect(parent.frame.centerX() - w / 2, parent.frame.centerY() - h / 2, 0, 0)
        } else {
            smartPlacement()
        }
        val frame = clampFrame(Rect(origin.left, origin.top, origin.left + w, origin.top + h))
        val wmClass = event.optString("class")
        val window = createWindow("linuxwin:$containerId:${wmClass.lowercase()}", frame)
        window.linuxContainer = containerId
        window.linuxXid = xid
        window.linuxWMClass = wmClass
        window.linuxClientDecorated = clientDecorated
        window.linuxWidth = bounds.width()
        window.linuxHeight = bounds.height()
        window.view.refreshDecorations()
        window.title = event.optString("title").ifBlank { wmClass.ifBlank { "Linux" } }
        window.linuxWindowIcon = decodeLinuxWindowIcon(event.optString("icon"))
        window.icon = window.linuxWindowIcon ?: linuxIcon(containerId, wmClass)
        window.view.titleBar.invalidate()
        if (event.optBoolean("fullscreen")) setLinuxFullscreen(window, true)
        LinuxContainerClient.service()?.runCatching { activateWindow(containerId, xid) }
        invalidateDesktopPanel()
    }

    private fun decodeLinuxWindowIcon(encoded: String): android.graphics.drawable.Drawable? = runCatching {
        if (encoded.isEmpty() || encoded.length > 256 * 1024) return null
        val bytes = android.util.Base64.decode(encoded, android.util.Base64.DEFAULT)
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?.let { android.graphics.drawable.BitmapDrawable(service.resources, it) }
    }.getOrNull()

    /** The Kickoff icon of the application whose desktop entry or name matches [wmClass]. */
    private fun linuxIcon(containerId: String, wmClass: String): android.graphics.drawable.Drawable {
        val key = wmClass.lowercase()
        val prefix = "$LINUX_APP_PREFIX$containerId:"
        val matchingClass = linuxApps.firstOrNull {
            it.packageName.startsWith(prefix) && key.isNotEmpty() && it.startupWMClass.equals(wmClass, ignoreCase = true)
        }
        return (matchingClass ?: linuxApps.firstOrNull { app ->
            app.packageName.startsWith(prefix) && key.isNotEmpty() &&
                (app.packageName.removePrefix(prefix).removeSuffix(".desktop").lowercase().let { it == key || it.endsWith(".$key") || it.endsWith("-$key") } ||
                    app.label.lowercase().startsWith(key))
        })?.icon ?: GlyphDrawable(Glyph.WINDOW, palette.text)
    }

    private fun attachLinuxWindow(window: V2Window, surface: Surface) {
        val id = window.linuxContainer ?: return
        val xid = window.linuxXid
        syncLinuxWindow(window)
        LinuxContainerClient.call(service, { linux -> if (window.view.usesDeckSurface) linux.attachDeckWindow(id, xid, surface)
            else linux.attachWindow(id, xid, surface) }, { attached ->
            if (!attached) host.log("Plasma v2 Linux window 0x${xid.toString(16)} could not be drawn")
        }) { error -> host.log("Plasma v2 Linux window attach failed: ${error.message}") }
    }

    /** Puts the X window exactly under the Plasma window's content. */
    private fun syncLinuxWindow(window: V2Window) {
        val id = window.linuxContainer ?: return
        if (window.linuxXid == 0) return
        syncLinuxWindowState(window)
        val w = (if (window.linuxFullscreen) window.linuxWidth else window.clientWidth()).coerceAtLeast(1)
        val h = (if (window.linuxFullscreen) window.linuxHeight else window.clientHeight(titleHeight)).coerceAtLeast(1)
        window.linuxConfiguredAt = SystemClock.uptimeMillis()
        LinuxContainerClient.service()?.runCatching {
            configureWindow(id, window.linuxXid, window.frame.left, window.frame.top + window.decorationHeight(titleHeight), w, h)
        }
    }

    private fun syncLinuxWindowState(window: V2Window) {
        val id = window.linuxContainer ?: return
        if (window.linuxXid != 0) LinuxContainerClient.service()?.runCatching {
            setWindowState(id, window.linuxXid, window.maximized, window.minimized)
        }
    }

    /** Client-decorated applications can drag themselves with ConfigureWindow requests. */
    private fun clientConfiguredLinuxWindow(window: V2Window, bounds: Rect, updateBuffer: Boolean = true) {
        if (updateBuffer) {
            window.linuxWidth = bounds.width(); window.linuxHeight = bounds.height()
            window.view.refreshLinuxViewport()
        }
        if (window.linuxFullscreen) return
        if (resizing === window || moving === window) return
        if (window.maximized || window.tile != TileZone.NONE) {
            if (bounds.width() != window.clientWidth() || bounds.height() != window.clientHeight(titleHeight)) syncLinuxWindow(window)
            return
        }
        val contentWidth = window.clientWidth()
        val contentHeight = window.clientHeight(titleHeight)
        val left = if (window.linuxClientDecorated) bounds.left else window.frame.left
        val top = if (window.linuxClientDecorated) bounds.top else window.frame.top
        if (bounds.width() != contentWidth || bounds.height() != contentHeight ||
            left != window.frame.left || top != window.frame.top) {
            val frame = clampFrame(Rect(left, top,
                left + bounds.width(), top + bounds.height() + window.decorationHeight(titleHeight)))
            window.maximized = false
            window.tile = TileZone.NONE
            setFrame(window, frame, resizeDisplay = false)
        }
        if (bounds.left != window.frame.left || bounds.top != window.frame.top + window.decorationHeight(titleHeight) ||
            bounds.width() != window.clientWidth() || bounds.height() != window.clientHeight(titleHeight)) {
            syncLinuxWindow(window)
        }
    }

    private fun setLinuxFullscreen(window: V2Window, enabled: Boolean) {
        if (window.linuxFullscreen == enabled) return
        if (enabled) window.linuxFullscreenRestore = Rect(window.frame)
        window.linuxFullscreen = enabled
        window.view.refreshDecorations()
        setFrame(window, if (enabled) Rect(0, 0, width, height)
            else clampFrame(window.linuxFullscreenRestore ?: window.frame), resizeDisplay = true)
        if (!enabled) window.linuxFullscreenRestore = null
        updatePanelFloat()
    }

    /**
     * Override-redirect X windows (menus, tooltips, drop-downs) drawn at
     * their X position, which is a display position because every X window
     * sits under its Plasma window's content.
     */
    private inner class LinuxPopupLayer(context: Context) : FrameLayout(context) {
        private inner class Popup(val containerId: String, val xid: Int) : android.view.TextureView(context) {
            val pointer = LinuxPointerState()
            var surface: Surface? = null

            init {
                isOpaque = true
                surfaceTextureListener = object : SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(texture: android.graphics.SurfaceTexture, w: Int, h: Int) {
                        val created = Surface(texture)
                        surface = created
                        LinuxContainerClient.call(service, { it.attachWindow(containerId, xid, created) }, {}) {}
                    }

                    override fun onSurfaceTextureSizeChanged(texture: android.graphics.SurfaceTexture, w: Int, h: Int) = Unit

                    override fun onSurfaceTextureDestroyed(texture: android.graphics.SurfaceTexture): Boolean {
                        LinuxContainerClient.service()?.runCatching { detachWindow(containerId, xid) }
                        surface?.release()
                        surface = null
                        return true
                    }

                    override fun onSurfaceTextureUpdated(texture: android.graphics.SurfaceTexture) = Unit
                }
                val forward = { event: MotionEvent ->
                    val linux = LinuxContainerClient.service()
                    if (linux != null) {
                        forwardLinuxPointer(
                            event, event.x.roundToInt(), event.y.roundToInt(), pointer,
                            send = { action, x, y, button -> linux.windowPointer(containerId, xid, action, x, y, button) },
                            scroll = { dx, dy -> linux.scroll("$containerId#apps", dx, dy) },
                            key = { code, down -> linux.key("$containerId#apps", code, down) },
                        )
                    }
                    true
                }
                setOnTouchListener { _, event -> forward(event) }
                setOnHoverListener { _, event -> forward(event) }
                setOnGenericMotionListener { _, event -> forward(event) }
            }
        }

        private val popups = LinkedHashMap<String, Popup>()

        init {
            clipChildren = false
        }

        fun show(containerId: String, xid: Int, bounds: Rect) {
            val key = "$containerId:$xid"
            if (key in popups) return place(containerId, xid, bounds)
            val popup = Popup(containerId, xid)
            popups[key] = popup
            addView(popup, LayoutParams(bounds.width().coerceAtLeast(1), bounds.height().coerceAtLeast(1)))
            popup.x = bounds.left.toFloat()
            popup.y = bounds.top.toFloat()
        }

        fun place(containerId: String, xid: Int, bounds: Rect) {
            val popup = popups["$containerId:$xid"] ?: return
            val params = popup.layoutParams
            if (params.width != bounds.width() || params.height != bounds.height()) {
                params.width = bounds.width().coerceAtLeast(1)
                params.height = bounds.height().coerceAtLeast(1)
                popup.layoutParams = params
            }
            popup.x = bounds.left.toFloat()
            popup.y = bounds.top.toFloat()
        }

        fun remove(containerId: String, xid: Int) {
            popups.remove("$containerId:$xid")?.let(::removeView)
        }

        /**
         * A menu holds the X pointer grab until the next click; a click
         * outside every Linux surface would never reach X, so it is passed
         * on as an X click (then handled by Plasma as usual).
         */
        override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN && popups.isNotEmpty()) {
                val x = event.x.roundToInt()
                val y = event.y.roundToInt()
                val onPopup = popups.values.any { x >= it.x && y >= it.y && x < it.x + it.width && y < it.y + it.height }
                val anchor = popups.values.last()
                val onLinuxWindow = windows.any { window ->
                    window.linuxContainer == anchor.containerId && window.linuxXid != 0 && isShown(window) &&
                        window.frame.contains(x, y) && y >= window.frame.top + window.decorationHeight(titleHeight)
                }
                if (!onPopup && !onLinuxWindow) {
                    LinuxContainerClient.service()?.runCatching {
                        val localX = x - anchor.x.roundToInt()
                        val localY = y - anchor.y.roundToInt()
                        windowPointer(anchor.containerId, anchor.xid, 1, localX, localY, 1)
                        windowPointer(anchor.containerId, anchor.xid, 2, localX, localY, 1)
                    }
                }
            }
            return false
        }
    }
    // endregion

    // region Linux windows
    private lateinit var linuxIme: LinuxImeTarget
    private var linuxKeyboardShown = false

    /** Input target of the active window: "<container>" for a desktop, "<container>#apps" for an application window. */
    private fun activeLinuxContainer(): String? =
        windows.firstOrNull { it.id == activeId && !it.minimized }?.let { window ->
            window.linuxContainer?.let { if (window.linuxXid != 0) "$it#apps" else it }
        }

    /** Opens (or raises) the Linux desktop of [containerId] as a Plasma window. */
    fun openLinuxDesktop(containerId: String, name: String) {
        if (!running) return
        windows.firstOrNull { it.linuxContainer == containerId && it.linuxXid == 0 && !it.closing }?.let { existing ->
            if (existing.minimized) restoreFromMinimize(existing) else activate(existing)
            return
        }
        // DroidDeck's layout: Plasma's KWin runs as a Wayland window of the application display.
        LinuxContainerClient.call(service, { linux ->
            JSONObject(linux.linuxSettings()).optJSONObject("containers")?.optJSONObject(containerId)?.optString("wmEffective")
        }, { wm ->
            if (wm == moe.n4tsu.dextop.linux.LinuxWindowManager.DROIDDECK.id) {
                launchLinuxApp("$LINUX_APP_PREFIX$containerId:${moe.n4tsu.dextop.linux.LinuxSteam.DROIDDECK_DESKTOP_ENTRY}")
            } else openXDesktop(containerId, name)
        }) { openXDesktop(containerId, name) }
    }

    private fun openXDesktop(containerId: String, name: String) {
        val area = workArea()
        val frame = Rect(area).apply { inset(area.width() / 12, area.height() / 12) }
        val window = createWindow("linux:$containerId", frame)
        window.linuxContainer = containerId
        window.title = name
        window.icon = GlyphDrawable(Glyph.COMPUTER, palette.text)
        window.view.titleBar.invalidate()
        invalidateDesktopPanel()
    }

    /** Starts the container's X display at the window's content size and draws it into [surface]. */
    private fun attachLinuxDesktop(window: V2Window, id: String, surface: Surface, width: Int, height: Int) {
        val w = width.coerceAtLeast(320)
        val h = height.coerceAtLeast(240)
        fun status(key: String?) = root.post { if (!window.closing) window.view.showStatus(key?.let(::t)) }
        status(if (LinuxContainerClient.connected) "nativeLinuxDesktopStartingContainer" else "nativeLinuxDesktopConnecting")
        LinuxContainerClient.call(service, { linux ->
            status("nativeLinuxDesktopStartingContainer")
            linux.startDesktop(id, w, h, densityDpi)
            linux.updateDisplay(id, false, w, h, densityDpi, linuxRefreshRate())
            check(linux.attachSurface(id, surface)) { "Unable to draw the Linux desktop" }
        }, {
            window.linuxWidth = w
            window.linuxHeight = h
            status("nativeLinuxDesktopStartingWm")
            // Poll off the client's call thread so other Linux calls are not held up.
            Thread({
                while (!window.closing) {
                    val linux = LinuxContainerClient.service()
                    val state = runCatching { linux?.desktopState(id) }.getOrNull() ?: 0
                    if (state == 0) {
                        val logs = runCatching { linux?.runtimeLogs(id) }.getOrNull().orEmpty()
                        root.post { if (!window.closing) window.view.showError("Linux session stopped.\n$logs") }
                        break
                    }
                    if (state == 2) status(null)
                    android.os.SystemClock.sleep(500)
                }
            }, "DextopLinuxWmWait").apply { isDaemon = true; start() }
        }) { error ->
            host.log("Plasma v2 Linux desktop failed container=$id: ${error.message}")
            window.view.showError(error.message ?: error.toString())
        }
    }

    private val capturePollRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            pollLinuxPointerCapture()
            handler.postDelayed(this, 100)
        }
    }
    private var capturePollBusy = false
    private fun pollLinuxPointerCapture() {
        if (capturePollBusy) return
        val window = windows.firstOrNull { it.id == activeId && it.linuxContainer != null && !it.minimized } ?: return
        val linux = LinuxContainerClient.service() ?: return
        capturePollBusy = true
        inputExecutor.execute {
            val requested = runCatching { linux.pointerCaptureRequested(window.linuxContainer, window.linuxXid) }.getOrDefault(false)
            root.post {
                capturePollBusy = false
                if (!running || window.closing || window.id != activeId) return@post
                if (!requested) window.linuxCaptureSuppressed = false
                window.linuxCaptureRequested = requested
                if ((requested || window.linuxManualCapture) && !window.linuxCaptureSuppressed && window.linuxMousePresent && window.view.content.hasWindowFocus()) {
                    window.view.content.requestFocus()
                    window.view.content.requestPointerCapture()
                } else if (!requested && !window.linuxManualCapture && window.view.content.hasPointerCapture()) window.view.content.releasePointerCapture()
            }
        }
    }

    override fun captureLost(window: V2Window) {
        val buttons = window.linuxPointer.buttons
        window.linuxPointer.buttons = 0
        window.linuxRelativeMotion.reset()
        val id = window.linuxContainer ?: return
        val linux = LinuxContainerClient.service() ?: return
        for ((bit, button) in LINUX_BUTTONS) if (buttons and bit != 0)
            linux.relativePointer(id, window.linuxXid, 2, 0, 0, button)
    }

    override fun forwardCapturedInput(window: V2Window, event: MotionEvent) {
        if (window.id != activeId || window.closing) { window.view.content.releasePointerCapture(); return }
        val id = window.linuxContainer ?: return
        val linux = LinuxContainerClient.service() ?: return
        if (event.actionMasked == MotionEvent.ACTION_MOVE || event.actionMasked == MotionEvent.ACTION_HOVER_MOVE) {
            // Android capture coordinates are physical deltas, not content positions.
            for (sample in 0..event.historySize) {
                val dx = if (sample == event.historySize) event.x else event.getHistoricalX(sample)
                val dy = if (sample == event.historySize) event.y else event.getHistoricalY(sample)
                val (x, y) = window.linuxRelativeMotion.add(dx, dy)
                if (x != 0 || y != 0) linux.relativePointer(id, window.linuxXid, 0, x, y, 0)
            }
        }
        if (event.actionMasked == MotionEvent.ACTION_SCROLL) {
            val (dx, dy) = window.linuxPointer.addScroll(event.getAxisValue(MotionEvent.AXIS_HSCROLL), -event.getAxisValue(MotionEvent.AXIS_VSCROLL), wheel = true)
            if (dx != 0 || dy != 0) linux.scroll(if (window.linuxXid == 0) id else "$id#apps", dx, dy)
        }
        val previous = window.linuxPointer.buttons
        val current = if (event.actionMasked == MotionEvent.ACTION_CANCEL) 0 else event.buttonState
        for ((bit, button) in LINUX_BUTTONS) if ((previous and bit != 0) != (current and bit != 0))
            linux.relativePointer(id, window.linuxXid, if (current and bit != 0) 1 else 2, 0, 0, button)
        window.linuxPointer.buttons = current
    }

    private fun forwardLinuxInput(window: V2Window, event: MotionEvent, source: View) {
        val id = window.linuxContainer ?: return
        val linux = LinuxContainerClient.service() ?: return
        if (event.isFromSource(android.view.InputDevice.SOURCE_MOUSE)) {
            window.linuxMousePresent = true
            if (window.linuxCaptureRequested && !window.linuxCaptureSuppressed) {
                source.requestFocus(); source.requestPointerCapture()
            }
        }
        if (source.width <= 0 || source.height <= 0) return
        val xid = window.linuxXid
        if (xid != 0) {
            if (window.linuxMoveResize >= 0) {
                val moving = window.linuxMoveResize == 8
                when (event.actionMasked) {
                    MotionEvent.ACTION_MOVE, MotionEvent.ACTION_HOVER_MOVE ->
                        if (moving) move(window, event.rawX, event.rawY) else resize(window, event.rawX, event.rawY)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_BUTTON_RELEASE, MotionEvent.ACTION_CANCEL -> {
                        if (moving) endMove(window, event.rawX, event.rawY) else endResize(window)
                        window.linuxMoveResize = -1
                        // Release the XTEST button even when the WM consumed the drag.
                        forwardLinuxPointer(event, event.x.roundToInt(), event.y.roundToInt(), window.linuxPointer,
                            send = { action, x, y, button -> linux.windowPointer(id, xid, action, x, y, button) },
                            scroll = { _, _ -> }, key = { _, _ -> })
                    }
                }
                return
            }
            // Fullscreen and client-selected modes may have a different pixel size from the view.
            val viewport = LinuxWindowViewport(window.linuxWidth.coerceAtLeast(1), window.linuxHeight.coerceAtLeast(1),
                source.width, source.height, window.linuxFullscreen)
            if (event.isFromSource(InputDevice.SOURCE_MOUSE) && event.classification == 0 &&
                event.actionMasked in setOf(MotionEvent.ACTION_MOVE, MotionEvent.ACTION_HOVER_MOVE)) {
                for (sample in 0 until event.historySize) {
                    linux.windowPointer(id, xid, 0, viewport.bufferX(event.getHistoricalX(sample)),
                        viewport.bufferY(event.getHistoricalY(sample)), 0)
                }
            }
            forwardLinuxPointer(
                event, viewport.bufferX(event.x), viewport.bufferY(event.y), window.linuxPointer,
                send = { action, x, y, button -> linux.windowPointer(id, xid, action, x, y, button) },
                scroll = { dx, dy -> linux.scroll("$id#apps", dx, dy) },
                key = { code, down -> linux.key("$id#apps", code, down) },
            )
            return
        }
        if (window.linuxWidth <= 0) return
        if (event.isFromSource(InputDevice.SOURCE_MOUSE) && event.classification == 0 &&
            event.actionMasked in setOf(MotionEvent.ACTION_MOVE, MotionEvent.ACTION_HOVER_MOVE)) {
            for (sample in 0 until event.historySize) {
                linux.pointer(id, 0, (event.getHistoricalX(sample) * window.linuxWidth / source.width).roundToInt(),
                    (event.getHistoricalY(sample) * window.linuxHeight / source.height).roundToInt(), 0)
            }
        }
        forwardLinuxPointer(
            event,
            (event.x * window.linuxWidth / source.width).roundToInt(),
            (event.y * window.linuxHeight / source.height).roundToInt(),
            window.linuxPointer,
            send = { action, x, y, button -> linux.pointer(id, action, x, y, button) },
            scroll = { dx, dy -> linux.scroll(id, dx, dy) },
            key = { code, down -> linux.key(id, code, down) },
        )
    }

    /**
     * Turns [event] (at X position [x], [y]) into X input: pointer and
     * buttons through [send], wheel steps through [scroll], keys through [key].
     *
     * Gestures become what Linux applications expect: a touchpad's two-finger
     * swipe and two fingers on a touch screen scroll, a touchpad pinch zooms
     * (Ctrl + wheel), and fine-grained wheels add up to whole steps.
     */
    private fun forwardLinuxPointer(
        event: MotionEvent, x: Int, y: Int, state: LinuxPointerState,
        send: (action: Int, x: Int, y: Int, button: Int) -> Unit,
        scroll: (dx: Int, dy: Int) -> Unit,
        key: (evdevCode: Int, down: Boolean) -> Unit,
    ) {
        runCatching {
            val step = dp(LINUX_SCROLL_STEP_DP).toFloat()
            fun scrollBy(fingerDx: Float, fingerDy: Float) {
                // Content follows the fingers: moving them up scrolls down.
                val (dx, dy) = state.addScroll(-fingerDx / step, -fingerDy / step)
                if (dx != 0 || dy != 0) scroll(dx, dy)
            }
            val classification = if (android.os.Build.VERSION.SDK_INT >= 34) event.classification else 0
            when (classification) {
                CLASSIFICATION_TWO_FINGER_SWIPE -> {
                    // Android reports the swipe as one pointer that moves with the fingers.
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) state.resetGesture(event.x, event.y)
                    else if (event.actionMasked == MotionEvent.ACTION_MOVE) {
                        scrollBy(event.x - state.lastX, event.y - state.lastY)
                        state.lastX = event.x
                        state.lastY = event.y
                    }
                    return
                }
                CLASSIFICATION_MULTI_FINGER_SWIPE -> return
                CLASSIFICATION_PINCH -> {
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) state.resetGesture(event.x, event.y)
                    if (event.actionMasked == MotionEvent.ACTION_MOVE) {
                        val factor = event.getAxisValue(MotionEvent.AXIS_GESTURE_PINCH_SCALE_FACTOR)
                        val steps = state.addPinch(factor)
                        if (steps != 0) {
                            key(29, true) // KEY_LEFTCTRL
                            scroll(0, -steps)
                            key(29, false)
                        }
                    }
                    return
                }
            }
            if (event.actionMasked == MotionEvent.ACTION_SCROLL) {
                send(0, x, y, 0)
                val (dx, dy) = state.addScroll(
                    event.getAxisValue(MotionEvent.AXIS_HSCROLL), -event.getAxisValue(MotionEvent.AXIS_VSCROLL),
                    wheel = true,
                )
                if (dx != 0 || dy != 0) scroll(dx, dy)
                return
            }
            if (event.isFromSource(android.view.InputDevice.SOURCE_MOUSE)) {
                // Report every change of the button state; Android sends both
                // ACTION_DOWN and ACTION_BUTTON_PRESS for one mouse click.
                send(0, x, y, 0)
                val previous = state.buttons
                val current = if (event.actionMasked == MotionEvent.ACTION_CANCEL) 0 else event.buttonState
                for ((bit, button) in LINUX_BUTTONS) {
                    val was = previous and bit != 0
                    val now = current and bit != 0
                    if (was != now) send(if (now) 1 else 2, x, y, button)
                }
                state.buttons = current
                return
            }
            // Touch: the press waits until the finger moves or lifts, so a
            // second finger can still turn the touch into a scroll.
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    state.startTouch(x, y)
                    send(0, x, y, 0)
                }
                MotionEvent.ACTION_POINTER_DOWN -> if (!state.touchPressed) {
                    state.touchPending = false
                    state.touchScrolling = true
                    state.resetGesture(centroidX(event), centroidY(event))
                }
                MotionEvent.ACTION_MOVE -> when {
                    state.touchScrolling -> if (event.pointerCount >= 2) {
                        val cx = centroidX(event)
                        val cy = centroidY(event)
                        scrollBy(cx - state.lastX, cy - state.lastY)
                        state.lastX = cx
                        state.lastY = cy
                    }
                    state.touchPending -> if (state.movedBeyond(x, y, dp(6))) {
                        send(1, state.touchDownX, state.touchDownY, 1)
                        state.touchPending = false
                        state.touchPressed = true
                        send(0, x, y, 0)
                    }
                    else -> send(0, x, y, 0)
                }
                MotionEvent.ACTION_UP -> {
                    if (state.touchPending) send(1, x, y, 1)
                    if (state.touchPending || state.touchPressed) send(2, x, y, 1)
                    state.endTouch()
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (state.touchPressed) send(2, x, y, 1)
                    state.endTouch()
                }
            }
        }
    }

    private fun centroidX(event: MotionEvent) = (0 until event.pointerCount).map(event::getX).average().toFloat()
    private fun centroidY(event: MotionEvent) = (0 until event.pointerCount).map(event::getY).average().toFloat()

    private var keyLoggedFor: String? = null

    private fun forwardLinuxKey(id: String, event: KeyEvent) {
        val linux = LinuxContainerClient.service() ?: return
        val down = event.action == KeyEvent.ACTION_DOWN
        val paste = (event.isCtrlPressed && event.keyCode == KeyEvent.KEYCODE_V) ||
            (event.isShiftPressed && event.keyCode == KeyEvent.KEYCODE_INSERT)
        if (down && paste && event.repeatCount == 0) syncClipboardToLinux(id.removeSuffix("#apps"))
        val code = LinuxKeymap.evdev(event)
        if (down && keyLoggedFor != id) {
            keyLoggedFor = id
            Log.i(TAG, "Linux keys -> $id (first: keyCode=${event.keyCode} scan=${event.scanCode} evdev=$code device=${event.device?.name})")
        }
        runCatching {
            when {
                code != null -> linux.key(id, code, down)
                down && event.unicodeChar > 0 -> linux.text(id, String(Character.toChars(event.unicodeChar)))
                event.action == KeyEvent.ACTION_MULTIPLE && !event.characters.isNullOrEmpty() -> linux.text(id, event.characters)
            }
        }
    }

    private fun showLinuxKeyboard() {
        linuxKeyboardShown = true
        updateShellFocus()
        linuxIme.post {
            linuxIme.requestFocus()
            displayContext.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                ?.showSoftInput(linuxIme, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun hideLinuxKeyboard() {
        if (!linuxKeyboardShown) return
        linuxKeyboardShown = false
        displayContext.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(linuxIme.windowToken, 0)
        linuxIme.clearFocus()
        updateShellFocus()
    }

    private fun hideLinuxKeyboardIfOtherWindow(window: V2Window) {
        if (linuxKeyboardShown && window.linuxContainer == null) hideLinuxKeyboard()
    }
    // endregion

    /** Makes tasks on [appDisplay] fill it instead of opening as freeform windows. */
    private fun useFullscreenWindowing(appDisplay: Int) {
        runCatching {
            Class.forName("android.view.IWindowManager")
                .getMethod("setWindowingMode", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                .invoke(windowService, appDisplay, TaskBridge.WINDOWING_MODE_FULLSCREEN)
        }.onFailure { host.log("Plasma v2 fullscreen windowing failed display=$appDisplay: ${it.message}") }
    }

    /**
     * An adopted task keeps the freeform mode and bounds it had on the Dextop
     * display, so it must be switched to fullscreen explicitly after the move.
     */
    private fun moveTaskIntoWindow(task: TaskSnapshot, appDisplay: Int): Boolean {
        val moved = bridge.moveTaskToDisplay(task, appDisplay)
        if (moved && fullscreenAppDisplays) {
            bridge.setWindowingMode(task, TaskBridge.WINDOWING_MODE_FULLSCREEN)
        }
        return moved
    }

    /** Gives the window's display input focus, which the IME requires to open. */
    private fun focusWindowDisplay(window: V2Window) {
        val appDisplay = window.displayId
        if (appDisplay < 0) return
        runCatching {
            wmExecutor.execute {
                runCatching { bridge.tasks(appDisplay).firstOrNull()?.let(bridge::focus) }
            }
        }
    }

    override fun forwardInput(window: V2Window, event: MotionEvent, source: View) {
        if (window.linuxContainer != null) {
            if (!overviewActive) forwardLinuxInput(window, event, source)
            return
        }
        val target = window.displayId
        if (target < 0 || source.width <= 0 || source.height <= 0 || overviewActive) return
        val copy = MotionEvent.obtain(event)
        val matrix = android.graphics.Matrix()
        matrix.setScale(window.displayWidth / source.width.toFloat(), window.displayHeight / source.height.toFloat())
        copy.transform(matrix)
        injectAsync(copy, target)
    }

    private fun injectAsync(event: InputEvent, target: Int) {
        runCatching {
            inputExecutor.execute {
                runCatching {
                    setEventDisplay.invoke(event, target)
                    injectMethod.invoke(inputService, event, INJECT_ASYNC)
                }.onFailure { Log.w(TAG, "input injection failed", it) }
                if (event is MotionEvent) event.recycle()
            }
        }
    }
    // endregion

    // region geometry
    private fun workArea(): Rect = if (autoMode) {
        autoUi?.takeIf { it.width>0 && it.height>0 }?.appBounds()?.firstOrNull() ?: Rect(0,0,width,height)
    } else Rect(0,0,width,(height-panel.thickness).coerceAtLeast(1))

    private fun smartPlacement(): Rect {
        val area = workArea()
        if (autoMode) return area
        val w = (area.width() * .62f).roundToInt().coerceAtLeast(minOf(area.width(), dp(480)))
        val h = (area.height() * .74f).roundToInt().coerceAtLeast(minOf(area.height(), dp(360)))
        val cascade = windows.count { isShown(it) } % 6
        val step = dp(28)
        val left = (area.left + (area.width() - w) / 2 + (cascade - 2) * step).coerceIn(area.left, (area.right - w).coerceAtLeast(area.left))
        val top = (area.top + (area.height() - h) / 2 + (cascade - 2) * step).coerceIn(area.top, (area.bottom - h).coerceAtLeast(area.top))
        return Rect(left, top, left + w, top + h)
    }

    private fun clampFrame(frame: Rect): Rect {
        val area = workArea()
        if (frame.width() > area.width()) frame.right = frame.left + area.width()
        if (frame.height() > area.height()) frame.bottom = frame.top + area.height()
        if (frame.top < area.top) frame.offset(0, area.top - frame.top)
        if (frame.top > area.bottom - titleHeight) frame.offset(0, area.bottom - titleHeight - frame.top)
        val keep = dp(64)
        if (frame.right < keep) frame.offset(keep - frame.right, 0)
        if (frame.left > width - keep) frame.offset(width - keep - frame.left, 0)
        return frame
    }

    private fun tileRect(zone: TileZone): Rect {
        val a = workArea()
        val cx = a.centerX()
        val cy = a.centerY()
        return when (zone) {
            TileZone.MAXIMIZE, TileZone.NONE -> Rect(a)
            TileZone.LEFT -> Rect(a.left, a.top, cx, a.bottom)
            TileZone.RIGHT -> Rect(cx, a.top, a.right, a.bottom)
            TileZone.TOP -> Rect(a.left, a.top, a.right, cy)
            TileZone.BOTTOM -> Rect(a.left, cy, a.right, a.bottom)
            TileZone.TOP_LEFT -> Rect(a.left, a.top, cx, cy)
            TileZone.TOP_RIGHT -> Rect(cx, a.top, a.right, cy)
            TileZone.BOTTOM_LEFT -> Rect(a.left, cy, cx, a.bottom)
            TileZone.BOTTOM_RIGHT -> Rect(cx, cy, a.right, a.bottom)
        }
    }

    private fun isShown(window: V2Window) =
        !window.minimized && !window.closing && (window.onAllDesktops || window.desktop == currentDesktop) &&
            (!autoMode || workspace.visible(window.id))

    /** Places the window view; the application display follows when [resizeDisplay] is set. */
    private fun setFrame(window: V2Window, frame: Rect, resizeDisplay: Boolean) {
        window.frame.set(frame)
        if (autoMode) {
            window.maximized = true
            window.view.titleBar.visibility = View.GONE
        }
        val view = window.view
        val params = view.layoutParams as FrameLayout.LayoutParams
        val w = frame.width() + grip * 2
        val h = frame.height() + grip * 2
        if (params.width != w || params.height != h) {
            params.width = w
            params.height = h
            view.layoutParams = params
        }
        view.x = (frame.left - grip).toFloat()
        view.y = (frame.top - grip).toFloat()
        if (window.linuxClientDecorated) view.invalidate()
        if (resizeDisplay) resizeWindowDisplay(window)
    }

    private fun resizeWindowDisplay(window: V2Window) {
        if (window.linuxXid != 0) {
            syncLinuxWindow(window)
            return
        }
        if (window.linuxContainer != null) {
            queueLinuxDisplay(window.linuxContainer!!, false, window.clientWidth(), window.clientHeight(titleHeight), densityDpi, linuxRefreshRate())
            return
        }
        val display = window.display ?: return
        val surface = window.surface ?: return
        val w = window.clientWidth().coerceAtLeast(1)
        val h = window.clientHeight(titleHeight)
        val dpi = densityFor(window)
        val previousDpi = windowDensity[window.id]
        if (w == window.displayWidth && h == window.displayHeight && dpi == previousDpi) return
        window.displayWidth = w
        window.displayHeight = h
        windowDensity[window.id] = dpi
        // The display resize keeps its density for DeX; the zoom is a WMS density override instead.
        if (previousDpi != null && previousDpi != dpi && window.displayId >= 0)
            host.privilegedExecute("wm", "density", dpi.toString(), "-d", window.displayId.toString())
        runCatching { window.view.content.surfaceTexture?.setDefaultBufferSize(w, h) }
        wmExecutor.execute { runCatching { display.resize(surface, w, h, dpi) } }
    }

    /** Animates live window content between two frames, then resizes the application display. */
    private fun animateFrame(window: V2Window, target: Rect) {
        val from = Rect(window.frame)
        val to = Rect(target)
        val duration = settings.duration(PlasmaTheme.LONG_MS + 60)
        if (duration <= 0L) {
            setFrame(window, to, resizeDisplay = true)
            return
        }
        ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            interpolator = PlasmaTheme.inOutCubic
            addUpdateListener {
                val t = it.animatedValue as Float
                setFrame(window, Rect(
                    (from.left + (to.left - from.left) * t).roundToInt(),
                    (from.top + (to.top - from.top) * t).roundToInt(),
                    (from.right + (to.right - from.right) * t).roundToInt(),
                    (from.bottom + (to.bottom - from.bottom) * t).roundToInt(),
                ), resizeDisplay = false)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    setFrame(window, to, resizeDisplay = true)
                    updatePanelFloat()
                }
            })
            start()
        }
    }
    // endregion

    // region V2WindowHost
    override fun isActive(window: V2Window) = window.id == activeId

    override fun activate(window: V2Window) {
        dismissPopup()
        hideTooltip()
        if (window.minimized) {
            restoreFromMinimize(window)
            return
        }
        if (!window.onAllDesktops && window.desktop != currentDesktop) switchDesktop(window.desktop)
        if (autoMode) workspace.select(window.id,pendingAutoPane ?: workspace.focusedPane)
        peeking = false
        val previous = activeId
        windows.firstOrNull { it.id == previous && it.id != window.id }?.let { old ->
            old.view.content.releasePointerCapture(); captureLost(old)
        }
        windows.firstOrNull { it.id == previous && it.linuxXid != 0 }?.let { old ->
            if (old.linuxContainer != window.linuxContainer || window.linuxXid == 0) {
                old.linuxContainer?.let { id -> LinuxContainerClient.service()?.runCatching { activateWindow(id, 0) } }
            }
        }
        activeId = window.id
        window.lastActivated = SystemClock.uptimeMillis()
        window.view.bringToFront()
        if (previous != window.id) {
            windows.forEach { it.view.refreshElevation(it.id == activeId) }
            focusWindowDisplay(window)
        }
        if (window.linuxXid != 0) {
            window.linuxContainer?.let { id -> LinuxContainerClient.service()?.runCatching { activateWindow(id, window.linuxXid) } }
        }
        // Menus and middle-click paste do not go through a key; offer the clipboard on focus too.
        window.linuxContainer?.let(::syncClipboardToLinux)
        updatePanelFloat()
        invalidateDesktopPanel()
        if (autoMode) { layoutAutoWorkspace();autoUi?.updateTasks() }
    }

    override fun beginMove(window: V2Window, rawX: Float, rawY: Float) {
        if (autoMode) return
        moving = window
        if (window.maximized || window.tile != TileZone.NONE) {
            val restore = window.restoreFrame ?: smartPlacement()
            val ratio = ((rawX - window.frame.left) / window.frame.width().coerceAtLeast(1)).coerceIn(0f, 1f)
            val left = (rawX - restore.width() * ratio).roundToInt()
            window.maximized = false
            window.tile = TileZone.NONE
            window.restoreFrame = null
            setFrame(window, Rect(left, window.frame.top, left + restore.width(), window.frame.top + restore.height()), resizeDisplay = true)
            window.view.titleBar.invalidate()
        }
        moveOffset.set(rawX - window.frame.left, rawY - window.frame.top)
        snapZone = TileZone.NONE
    }

    override fun move(window: V2Window, rawX: Float, rawY: Float) {
        if (moving !== window) return
        val w = window.frame.width()
        val h = window.frame.height()
        var left = (rawX - moveOffset.x).roundToInt()
        var top = (rawY - moveOffset.y).roundToInt().coerceIn(0, height - panel.thickness - titleHeight)
        val area = workArea()
        val snap = dp(10)
        if (kotlin.math.abs(left - area.left) < snap) left = area.left
        if (kotlin.math.abs(left + w - area.right) < snap) left = area.right - w
        if (kotlin.math.abs(top + h - area.bottom) < snap) top = area.bottom - h
        window.frame.set(left, top, left + w, top + h)
        window.view.x = (left - grip).toFloat()
        window.view.y = (top - grip).toFloat()
        if (window.linuxClientDecorated) window.view.invalidate()
        run {
            val edge = dp(3).toFloat()
            val corner = height * .25f
            snapZone = when {
                rawY <= edge -> TileZone.MAXIMIZE
                rawX <= edge && rawY < corner -> TileZone.TOP_LEFT
                rawX <= edge && rawY > height - corner -> TileZone.BOTTOM_LEFT
                rawX <= edge -> TileZone.LEFT
                rawX >= width - 1 - edge && rawY < corner -> TileZone.TOP_RIGHT
                rawX >= width - 1 - edge && rawY > height - corner -> TileZone.BOTTOM_RIGHT
                rawX >= width - 1 - edge -> TileZone.RIGHT
                else -> TileZone.NONE
            }
            snapPreview.show(if (snapZone == TileZone.NONE) null else RectF(tileRect(snapZone)).apply { inset(dp(6).toFloat(), dp(6).toFloat()) })
        }
        updatePanelFloat()
    }

    override fun endMove(window: V2Window, rawX: Float, rawY: Float) {
        if (moving !== window) return
        moving = null
        snapPreview.show(null)
        val zone = snapZone
        snapZone = TileZone.NONE
        if (zone != TileZone.NONE) tile(window, zone) else if (window.linuxXid != 0) syncLinuxWindow(window)
        updatePanelFloat()
    }

    override fun beginResize(window: V2Window, edge: V2Edge, rawX: Float, rawY: Float) {
        if (autoMode) return
        resizing = window
        resizeEdge = edge
        resizeStartFrame.set(window.frame)
        resizeStart.set(rawX, rawY)
        window.maximized = false
        window.tile = TileZone.NONE
    }

    override fun resize(window: V2Window, rawX: Float, rawY: Float) {
        if (resizing !== window) return
        val dx = (rawX - resizeStart.x).roundToInt()
        val dy = (rawY - resizeStart.y).roundToInt()
        val minW = dp(300)
        val minH = dp(180) + titleHeight
        val f = Rect(resizeStartFrame)
        when (resizeEdge) {
            V2Edge.LEFT, V2Edge.TOP_LEFT, V2Edge.BOTTOM_LEFT -> f.left = minOf(f.left + dx, f.right - minW)
            V2Edge.RIGHT, V2Edge.TOP_RIGHT, V2Edge.BOTTOM_RIGHT -> f.right = maxOf(f.right + dx, f.left + minW)
            else -> Unit
        }
        when (resizeEdge) {
            V2Edge.TOP, V2Edge.TOP_LEFT, V2Edge.TOP_RIGHT -> f.top = minOf(f.top + dy, f.bottom - minH).coerceAtLeast(0)
            V2Edge.BOTTOM, V2Edge.BOTTOM_LEFT, V2Edge.BOTTOM_RIGHT -> f.bottom = maxOf(f.bottom + dy, f.top + minH)
            else -> Unit
        }
        setFrame(window, f, resizeDisplay = false)
        // The application relayouts a few times per second while the frame
        // itself follows the pointer every frame (content scales meanwhile).
        if (!resizeDisplayPending) {
            resizeDisplayPending = true
            handler.postDelayed({
                resizeDisplayPending = false
                resizing?.let(::resizeWindowDisplay)
            }, 120L)
        }
    }

    override fun endResize(window: V2Window) {
        if (resizing !== window) return
        resizing = null
        resizeWindowDisplay(window)
        updatePanelFloat()
    }

    override fun toggleMaximize(window: V2Window) {
        if (autoMode) return
        if (window.linuxFullscreen) return
        if (window.maximized) {
            val target = window.restoreFrame ?: smartPlacement()
            window.maximized = false
            window.tile = TileZone.NONE
            window.restoreFrame = null
            animateFrame(window, target)
        } else {
            if (window.tile == TileZone.NONE) window.restoreFrame = Rect(window.frame)
            window.maximized = true
            window.tile = TileZone.NONE
            animateFrame(window, workArea())
        }
        window.view.titleBar.invalidate()
        syncLinuxWindowState(window)
        activate(window)
    }

    fun tile(window: V2Window, zone: TileZone) {
        if (autoMode) return
        if (zone == TileZone.MAXIMIZE) {
            if (!window.maximized) toggleMaximize(window)
            return
        }
        if (!window.maximized && window.tile == TileZone.NONE) window.restoreFrame = Rect(window.frame)
        window.maximized = false
        window.tile = zone
        animateFrame(window, tileRect(zone))
        activate(window)
    }

    /** Squash minimize: the live window shrinks into its task-manager icon. */
    override fun minimize(window: V2Window) {
        window.view.content.releasePointerCapture(); captureLost(window)
        if (window.minimized) return
        if (activeId == window.id && window.linuxXid != 0) {
            window.linuxContainer?.let { id -> LinuxContainerClient.service()?.runCatching { activateWindow(id, 0) } }
        }
        window.minimized = true
        if (autoMode) { workspace.remove(window.id);layoutAutoWorkspace();autoUi?.updateTasks() }
        syncLinuxWindowState(window)
        val icon = if (autoMode) null else panel.taskIconRect(window.packageName)
        val view = window.view
        val targetX = icon?.centerX() ?: (width / 2f)
        val targetY = icon?.centerY() ?: height.toFloat()
        view.pivotX = 0f
        view.pivotY = 0f
        val scale = .08f
        view.animate()
            .x(targetX - view.width * scale / 2f)
            .y(targetY - view.height * scale / 2f)
            .scaleX(scale).scaleY(scale).alpha(0f)
            .setDuration(settings.duration(PlasmaTheme.LONG_MS + 120))
            .setInterpolator(PlasmaTheme.inOutCubic)
            .withEndAction { if (window.minimized) view.visibility = View.INVISIBLE }
            .start()
        if (activeId == window.id) {
            activeId = windows.filter { isShown(it) }.maxByOrNull { it.lastActivated }?.id ?: -1
            windows.forEach { it.view.refreshElevation(it.id == activeId) }
        }
        updatePanelFloat()
        invalidateDesktopPanel()
    }

    private fun restoreFromMinimize(window: V2Window) {
        window.minimized = false
        syncLinuxWindowState(window)
        if (!window.onAllDesktops && window.desktop != currentDesktop) switchDesktop(window.desktop)
        val view = window.view
        view.visibility = View.VISIBLE
        view.animate()
            .x((window.frame.left - grip).toFloat()).y((window.frame.top - grip).toFloat())
            .scaleX(1f).scaleY(1f).alpha(1f)
            .setDuration(settings.duration(PlasmaTheme.LONG_MS + 80))
            .setInterpolator(PlasmaTheme.outCubic)
            .start()
        activate(window)
    }

    override fun close(window: V2Window) {
        if (window.steamMode != null) {
            stopSteamMode(window)
            return
        }
        if (window.linuxXid != 0) {
            // The client may ask to save first; the window goes when X unmaps it.
            window.linuxContainer?.let { id -> LinuxContainerClient.service()?.runCatching { closeWindow(id, window.linuxXid) } }
            return
        }
        window.linuxContainer?.let { id ->
            hideLinuxKeyboard()
            LinuxContainerClient.service()?.runCatching { stopDesktop(id) }
        }
        val display = window.displayId
        if (display >= 0) {
            wmExecutor.execute { runCatching { bridge.tasks(display).forEach(bridge::close) } }
        }
        closeWindowNow(window)
    }

    override fun showWindowMenu(window: V2Window, rawX: Float, rawY: Float) {
        val items = ArrayList<MenuItem>()
        items += MenuItem(window.title, header = true)
        items += MenuItem(t("nativePlasmaMinimize"), Glyph.MINIMIZE) { minimize(window) }
        items += MenuItem(if (window.maximized) t("nativePlasmaRestore") else t("nativePlasmaMaximize"), Glyph.MAXIMIZE) { toggleMaximize(window) }
        items += MenuItem(t("nativePlasmaTileLeft"), Glyph.TILE) { tile(window, TileZone.LEFT) }
        items += MenuItem(t("nativePlasmaTileRight"), Glyph.TILE) { tile(window, TileZone.RIGHT) }
        if (desktopCount() > 1) {
            items += MenuItem(t("nativePlasmaAllDesktops"), Glyph.PIN, checked = window.onAllDesktops, separatorBefore = true) {
                window.onAllDesktops = !window.onAllDesktops
                if (!window.onAllDesktops) window.desktop = currentDesktop
            }
            for (index in 0 until desktopCount()) {
                items += MenuItem(t("nativePlasmaMoveToDesktopN").format(index + 1), Glyph.DESKTOP,
                    checked = !window.onAllDesktops && window.desktop == index) { moveWindowToDesktop(window, index) }
            }
        }
        if (window.linuxContainer != null) {
            items += MenuItem(t("nativePlasmaShowKeyboard"), Glyph.KEYBOARD, separatorBefore = true) {
                activate(window)
                showLinuxKeyboard()
            }
        }
        items += MenuItem(t("nativePlasmaClose"), Glyph.CLOSE, separatorBefore = true) { close(window) }
        showMenu(items, rawX, rawY)
    }
    // endregion

    // region virtual desktops
    override fun currentDesktop(): Int = currentDesktop
    override fun desktopCount(): Int = settings.virtualDesktops

    /** Plasma "Slide": both desktops' live windows slide past each other. */
    fun switchDesktop(index: Int, animate: Boolean = true) {
        if (index == currentDesktop || index !in 0 until desktopCount()) return
        val direction = if (index > currentDesktop) 1 else -1
        val leaving = windows.filter { !it.onAllDesktops && it.desktop == currentDesktop && !it.minimized }
        val arriving = windows.filter { !it.onAllDesktops && it.desktop == index && !it.minimized }
        currentDesktop = index
        peeking = false
        val duration = if (animate) settings.duration(PlasmaTheme.VERY_LONG_MS - 50) else 0L
        val distance = width.toFloat() * direction
        leaving.forEach { window ->
            val view = window.view
            view.animate().translationX(-distance).setDuration(duration).setInterpolator(PlasmaTheme.inOutCubic)
                .withEndAction { if (!isShown(window)) { view.visibility = View.INVISIBLE; view.translationX = 0f } }.start()
        }
        arriving.forEach { window ->
            val view = window.view
            view.visibility = View.VISIBLE
            view.alpha = 1f
            view.translationX = distance
            view.animate().translationX(0f).setDuration(duration).setInterpolator(PlasmaTheme.inOutCubic).start()
        }
        activeId = arriving.maxByOrNull { it.lastActivated }?.id ?: windows.firstOrNull { it.onAllDesktops && !it.minimized }?.id ?: -1
        arriving.maxByOrNull { it.lastActivated }?.view?.bringToFront()
        windows.forEach { it.view.refreshElevation(it.id == activeId) }
        updatePanelFloat()
        invalidateDesktopPanel()
    }

    private fun moveWindowToDesktop(window: V2Window, desktop: Int) {
        window.onAllDesktops = false
        if (window.desktop == desktop) return
        window.desktop = desktop
        if (desktop != currentDesktop) {
            window.view.animate().alpha(0f).setDuration(settings.duration(PlasmaTheme.LONG_MS))
                .withEndAction { if (!isShown(window)) window.view.visibility = View.INVISIBLE; window.view.alpha = 1f }.start()
            if (activeId == window.id) activeId = -1
        }
        invalidateDesktopPanel()
    }

    private fun togglePeek() {
        peeking = !peeking
        windows.filter { isShown(it) }.forEach { window ->
            val view = window.view
            if (peeking) {
                view.animate().alpha(0f).setDuration(settings.duration(PlasmaTheme.LONG_MS))
                    .withEndAction { if (peeking) view.visibility = View.INVISIBLE }.start()
            } else {
                view.visibility = View.VISIBLE
                view.animate().alpha(1f).setDuration(settings.duration(PlasmaTheme.LONG_MS)).start()
            }
        }
        updatePanelFloat()
    }
    // endregion

    // region overview (live window grid)
    fun toggleOverview() {
        if (overviewActive) exitOverview(null) else enterOverview()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun enterOverview() {
        dismissPopup()
        hideTooltip()
        val shown = windows.filter { isShown(it) }.sortedBy { it.id }
        overviewActive = true
        updateShellFocus()
        overviewTargets.clear()
        val top = dp(170).toFloat()
        val area = RectF(dp(48).toFloat(), top, width - dp(48).toFloat(), height - dp(70).toFloat())
        if (shown.isNotEmpty()) {
            val columns = ceil(sqrt(shown.size * area.width() / area.height().toDouble())).toInt().coerceIn(1, shown.size)
            val rows = ceil(shown.size / columns.toDouble()).toInt()
            val cellW = area.width() / columns
            val cellH = area.height() / rows
            shown.forEachIndexed { index, window ->
                val row = index / columns
                val inRow = if (row == rows - 1) shown.size - row * columns else columns
                val column = index % columns
                val offset = (columns - inRow) * cellW / 2f
                val slot = RectF(area.left + offset + column * cellW, area.top + row * cellH,
                    area.left + offset + (column + 1) * cellW, area.top + (row + 1) * cellH)
                slot.inset(dp(18).toFloat(), dp(16).toFloat())
                slot.bottom -= dp(26)
                val scale = minOf(slot.width() / window.frame.width(), slot.height() / window.frame.height(), 1f)
                val w = window.frame.width() * scale
                val h = window.frame.height() * scale
                overviewTargets[window.id] = RectF(slot.centerX() - w / 2f, slot.centerY() - h / 2f, slot.centerX() + w / 2f, slot.centerY() + h / 2f)
            }
        }
        val duration = settings.duration(PlasmaTheme.VERY_LONG_MS)
        dim.visibility = View.VISIBLE
        dim.animate().alpha(1f).setDuration(duration).start()
        windows.forEach { window ->
            val target = overviewTargets[window.id]
            val view = window.view
            if (target == null) {
                if (!window.minimized && isShown(window)) view.animate().alpha(0f).setDuration(duration).start()
                return@forEach
            }
            view.pivotX = grip.toFloat()
            view.pivotY = grip.toFloat()
            val scale = target.width() / window.frame.width()
            view.animate().x(target.left - grip).y(target.top - grip).scaleX(scale).scaleY(scale)
                .setDuration(duration).setInterpolator(PlasmaTheme.inOutCubic).start()
        }
        buildOverviewChrome()
        overviewChrome.visibility = View.VISIBLE
        overviewChrome.alpha = 0f
        overviewChrome.animate().alpha(1f).setDuration(duration).start()
        overviewChrome.bringToFront()
        panel.bringToFront()
        popupLayer.bringToFront()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildOverviewChrome() {
        overviewChrome.removeAllViews()
        val strip = DesktopStripView(displayContext)
        overviewChrome.addView(strip, FrameLayout.LayoutParams(-1, dp(118)))
        val search = PlasmaSearchField(displayContext, palette, t("nativePlasmaOverviewSearch"))
        overviewSearch = search
        overviewChrome.addView(search, FrameLayout.LayoutParams(dp(420), dp(38), Gravity.CENTER_HORIZONTAL or Gravity.TOP).apply {
            topMargin = dp(122)
        })
        val results = verticalLayout(displayContext).apply {
            background = roundedBackground(palette.popup, dp(8).toFloat(), palette.frameOutline, 1)
            visibility = View.GONE
        }
        overviewChrome.addView(results, FrameLayout.LayoutParams(dp(480), -2, Gravity.CENTER_HORIZONTAL or Gravity.TOP).apply {
            topMargin = dp(166)
        })
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString().orEmpty()
                results.removeAllViews()
                PlasmaSearch.calculate(query)?.let { answer ->
                    results.addView(PlasmaRow(displayContext, palette, GlyphDrawable(Glyph.TILE, palette.accent), answer,
                        subtitle = t("nativePlasmaCalculatorCopy"), heightDp = 40f) { copyToClipboard(answer); exitOverview(null) })
                }
                PlasmaSearch.apps(this@CompositedWindowShell, query).take(6).forEach { app ->
                    results.addView(PlasmaRow(displayContext, palette, app.icon, app.label, subtitle = t("nativePlasmaApplication"),
                        heightDp = 38f, iconDp = 26f) { exitOverview { launch(app.packageName) } })
                }
                results.visibility = if (query.isNotBlank() && results.childCount > 0) View.VISIBLE else View.GONE
            }
        })
        // Transparent catcher: windows are hit-tested against their grid slots.
        val catcher = object : View(displayContext) {}
        catcher.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                val hit = windows.firstOrNull { overviewTargets[it.id]?.contains(event.x, event.y) == true }
                if (hit != null) exitOverview { activate(hit) }
                else if (event.y > dp(170)) exitOverview(null)
            }
            true
        }
        overviewChrome.addView(catcher, 0, FrameLayout.LayoutParams(-1, -1))
    }

    private fun exitOverview(after: (() -> Unit)?) {
        if (!overviewActive) {
            after?.invoke()
            return
        }
        overviewActive = false
        updateShellFocus()
        val duration = settings.duration(PlasmaTheme.VERY_LONG_MS - 80)
        dim.animate().alpha(0f).setDuration(duration).withEndAction { dim.visibility = View.GONE }.start()
        overviewChrome.animate().alpha(0f).setDuration(duration).withEndAction {
            overviewChrome.visibility = View.GONE
            overviewChrome.removeAllViews()
            overviewSearch = null
        }.start()
        windows.forEach { window ->
            val view = window.view
            if (window.minimized || !isShown(window)) return@forEach
            view.animate().x((window.frame.left - grip).toFloat()).y((window.frame.top - grip).toFloat())
                .scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(duration).setInterpolator(PlasmaTheme.inOutCubic).start()
        }
        handler.postDelayed({ after?.invoke() }, duration / 2)
    }

    private inner class DesktopStripView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = PlasmaTheme.sans }
        private val rects = ArrayList<RectF>()

        override fun onDraw(canvas: Canvas) {
            rects.clear()
            val density = resources.displayMetrics.density
            val h = 72 * density
            val w = h * this@CompositedWindowShell.width / this@CompositedWindowShell.height
            val total = desktopCount() * (w + 12 * density) - 12 * density
            var x = width / 2f - total / 2f
            for (index in 0 until desktopCount()) {
                val r = RectF(x, 16 * density, x + w, 16 * density + h)
                rects += r
                val current = index == currentDesktop
                paint.style = Paint.Style.FILL
                paint.color = if (current) PlasmaTheme.withAlpha(palette.accent, 0x55) else 0x66000000
                canvas.drawRoundRect(r, 6 * density, 6 * density, paint)
                val sx = r.width() / this@CompositedWindowShell.width
                val sy = r.height() / this@CompositedWindowShell.height
                windows.filter { !it.minimized && (it.onAllDesktops || it.desktop == index) }.forEach { window ->
                    paint.color = PlasmaTheme.withAlpha(palette.windowBackground, 0xDD)
                    canvas.drawRect(r.left + window.frame.left * sx, r.top + window.frame.top * sy,
                        r.left + window.frame.right * sx, r.top + window.frame.bottom * sy, paint)
                }
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = if (current) 2 * density else 1f
                paint.color = if (current) palette.accent else 0x66FFFFFF
                canvas.drawRoundRect(r, 6 * density, 6 * density, paint)
                text.textSize = 11.5f * density
                text.color = 0xFFFCFCFC.toInt()
                canvas.drawText(t("nativePlasmaDesktopN").format(index + 1), r.centerX(), r.bottom + 15 * density, text)
                x += w + 12 * density
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                val index = rects.indexOfFirst { it.contains(event.x, event.y) }
                if (index >= 0) {
                    exitOverview(null)
                    switchDesktop(index)
                }
            }
            return true
        }
    }
    // endregion

    // region panel host
    private fun layoutPanel() {
        if (autoMode) return
        val margin = (panel.floatMargin * panelFloat).roundToInt()
        val params = panel.layoutParams as FrameLayout.LayoutParams
        val wanted = panel.thickness + margin * 2
        if (params.height != wanted) {
            params.height = wanted
            panel.layoutParams = params
        }
    }

    private fun updatePanelFloat(force: Boolean = false) {
        if (!running) return
        if (autoMode) return
        panel.visibility = if (!overviewActive && !peeking && kickoff == null && krunner == null &&
            windows.any { it.id == activeId && it.linuxFullscreen && isShown(it) }) View.INVISIBLE else View.VISIBLE
        val floatZone = height - panel.thickness - panel.floatMargin * 2
        val touching = windows.any { isShown(it) && (it.maximized || it.frame.bottom > floatZone) }
        val target = if (settings.floatingPanel && !touching && !peeking) 1f else 0f
        if (!force && panelFloatTarget == target) return
        panelFloatTarget = target
        panelAnimator?.cancel()
        panelAnimator = ValueAnimator.ofFloat(panelFloat, target).apply {
            duration = settings.duration(PlasmaTheme.LONG_MS)
            interpolator = PlasmaTheme.outCubic
            addUpdateListener {
                panelFloat = it.animatedValue as Float
                panel.floatFraction = panelFloat
                layoutPanel()
            }
            start()
        }
    }

    override fun panelEntries(): List<PanelTask> {
        windows.forEach { it.syncModel(it.id == activeId) }
        val pinned = pinnedList()
        val shown = windows.filter { !it.closing && (it.onAllDesktops || it.desktop == currentDesktop) }
        val byPackage = shown.groupBy { it.packageName }
        val order = LinkedHashSet<String>()
        order += pinned
        windows.sortedBy { it.id }.forEach { if (byPackage.containsKey(it.packageName)) order += it.packageName }
        return order.map { pkg ->
            val group = byPackage[pkg].orEmpty()
            val linuxWindow = group.filter { it.linuxContainer != null }.maxWithOrNull(
                compareBy<V2Window>({ it.id == activeId && !it.minimized }, { it.lastActivated }))
            val linuxApp = linuxApps.firstOrNull { it.packageName == pkg }
            PanelTask(pkg, linuxWindow?.title ?: linuxApp?.label ?: appRepository.label(pkg), linuxWindow?.icon ?: linuxApp?.icon ?: appRepository.icon(pkg),
                group.map { it.model }, pkg in pinned,
                group.any { it.id == activeId && !it.minimized })
        }
    }

    private fun windowFor(model: ManagedWindow) = windows.firstOrNull { it.model === model }

    override fun pagerWindows(desktop: Int): List<Pair<Rect, Boolean>> =
        windows.filter { !it.minimized && !it.closing && (it.onAllDesktops || it.desktop == desktop) }
            .sortedBy { it.lastActivated }.map { Rect(it.frame) to (it.id == activeId) }

    override fun displaySize(): Pair<Int, Int> = width to height
    override fun tray(): TrayState = trayState

    override fun onLauncherClicked(anchor: RectF) {
        if (recentlyDismissed("kickoff")) return
        toggleKickoff()
    }

    override fun onTaskClicked(entry: PanelTask, anchor: RectF) {
        hideTooltip()
        val group = entry.windows.mapNotNull(::windowFor)
        when {
            group.isEmpty() -> launch(entry.packageName)
            group.size == 1 -> {
                val window = group.first()
                if (window.id == activeId && !window.minimized) minimize(window) else activate(window)
            }
            else -> {
                if (recentlyDismissed("group:${entry.packageName}")) return
                showMenu(listOf(MenuItem(entry.label, header = true)) + group.map { window ->
                    MenuItem(window.title, if (window.minimized) Glyph.MINIMIZE else Glyph.WINDOW, checked = window.id == activeId) { activate(window) }
                }, anchor.left, anchor.top, kind = "group:${entry.packageName}", above = true)
            }
        }
    }

    override fun onTaskMiddleClicked(entry: PanelTask) = launch(entry.packageName, newInstance = true)

    override fun onTaskContextMenu(entry: PanelTask, anchor: RectF) {
        hideTooltip()
        val group = entry.windows.mapNotNull(::windowFor)
        val items = ArrayList<MenuItem>()
        items += MenuItem(entry.label, header = true)
        group.forEach { window -> items += MenuItem(window.title, Glyph.WINDOW, checked = window.id == activeId) { activate(window) } }
        items += MenuItem(t("nativePlasmaNewInstance"), Glyph.PLUS, separatorBefore = group.isNotEmpty()) { launch(entry.packageName, newInstance = true) }
        items += MenuItem(if (entry.pinned) t("nativePlasmaUnpinFromTaskManager") else t("nativePlasmaPinToTaskManager"), Glyph.PIN) {
            setPinned(entry.packageName, !entry.pinned)
        }
        if (group.size == 1 && desktopCount() > 1) {
            val window = group.first()
            for (index in 0 until desktopCount()) {
                items += MenuItem(t("nativePlasmaMoveToDesktopN").format(index + 1), Glyph.DESKTOP,
                    checked = window.desktop == index) { moveWindowToDesktop(window, index) }
            }
        }
        if (group.isNotEmpty()) {
            items += MenuItem(if (group.size > 1) t("nativePlasmaCloseAll") else t("nativePlasmaClose"), Glyph.CLOSE,
                separatorBefore = true) { group.forEach(::close) }
        }
        showMenu(items, anchor.left, anchor.top, above = true)
    }

    override fun onTaskHover(entry: PanelTask?, anchor: RectF?) {
        tooltipRunnable?.let(handler::removeCallbacks)
        if (entry == null || anchor == null || popup != null) {
            hideTooltip()
            return
        }
        val delay = if (tooltip != null) 0L else 550L
        tooltipRunnable = Runnable { showTooltip(entry, anchor) }.also { handler.postDelayed(it, delay) }
    }

    override fun onDesktopClicked(index: Int) = switchDesktop(index)

    override fun onDesktopScroll(delta: Int) {
        val next = currentDesktop + delta
        if (next in 0 until desktopCount()) switchDesktop(next)
    }

    override fun onTrayClicked(item: TrayItem, anchor: RectF) {
        val kind = "tray:$item"
        if (recentlyDismissed(kind)) return
        refreshTray()
        val view: View = when (item) {
            TrayItem.VOLUME -> AudioApplet(displayContext, palette, systemControls, trayWorker,
                onChanged = { refreshTray() },
                onOpenSettings = { dismissPopup(); launchIntentInWindow(Intent(Settings.ACTION_SOUND_SETTINGS)) })
            TrayItem.NETWORK, TrayItem.CELLULAR -> NetworkApplet(displayContext, palette, systemControls, trayWorker,
                onChanged = { refreshTray() },
                onOpenSettings = { dismissPopup(); launchIntentInWindow(Intent(Settings.ACTION_WIRELESS_SETTINGS)) })
            TrayItem.BATTERY -> PowerApplet(displayContext, palette, systemControls, trayWorker,
                onChanged = { refreshTray() },
                onOpenSettings = { dismissPopup(); launchIntentInWindow(Intent(Intent.ACTION_POWER_USAGE_SUMMARY)) })
            TrayItem.NOTIFICATIONS -> NotificationApplet(displayContext, palette,
                onOpen = ::openNotification, onDismiss = ::dismissNotification)
            TrayItem.EXPAND -> TrayOverflowApplet(displayContext, palette, listOf(
                MenuItem(t("nativePlasmaOverview"), Glyph.OVERVIEW) { toggleOverview() },
                MenuItem(t("nativePlasmaRunCommand"), Glyph.SEARCH) { showKRunner() },
                MenuItem(t("nativePlasmaConfigureDesktop"), Glyph.WALLPAPER) { openDesktopSettings() },
                MenuItem(t("nativePlasmaLeave"), Glyph.LOGOUT) { leave(LeaveAction.SHOW_DIALOG) },
            ), linuxItems = linuxTrayItems.values.toList(), onLinuxAction = ::invokeLinuxTray) { dismissPopup() }.also { linuxTrayOverflow = it }
        }
        val popupWidth = dp(if (item == TrayItem.EXPAND) 300 else 400)
        // The network list fills in after a scan, so its popup keeps a fixed height.
        val fixedHeight = if (item == TrayItem.NETWORK || item == TrayItem.CELLULAR) {
            minOf(dp(600), (height * .85f).roundToInt())
        } else {
            null
        }
        showAnchoredPopup(kind, view, anchor, popupWidth, fixedHeight)
    }

    override fun onClockClicked(anchor: RectF) {
        if (recentlyDismissed("clock")) return
        showAnchoredPopup("clock", CalendarApplet(displayContext, palette), anchor, dp(520), dp(310))
    }

    override fun onShowDesktopClicked() = togglePeek()

    override fun onPanelContextMenu(rawX: Float, rawY: Float) {
        showMenu(listOf(
            MenuItem(t("nativePlasmaPanel"), header = true),
            MenuItem(if (settings.floatingPanel) t("nativePlasmaDocked") else t("nativePlasmaFloating"), Glyph.DESKTOP) {
                applySettings(settings.copy(floatingPanel = !settings.floatingPanel))
            },
            MenuItem(t("nativePlasmaConfigureDesktop"), Glyph.SETTINGS) { openDesktopSettings() },
            MenuItem(t("nativePlasmaLeave"), Glyph.LOGOUT, separatorBefore = true) { leave(LeaveAction.SHOW_DIALOG) },
        ), rawX, rawY, above = true)
    }

    private var cellularType: String? = null
    private var cellularTypePending = false
    private var cellularTypeCheckedAt = 0L

    private fun refreshTray() {
        if (!cellularTypePending && SystemClock.uptimeMillis() - cellularTypeCheckedAt > 5000L) {
            cellularTypePending = true
            systemExecutor.execute {
                val type = runCatching { systemControls.cellularNetworkType() }.getOrNull()
                handler.post {
                    cellularTypePending = false
                    cellularTypeCheckedAt = SystemClock.uptimeMillis()
                    cellularType = type
                    if (running) refreshTray()
                }
            }
        }
        val audio = service.getSystemService(AudioManager::class.java)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val network = runCatching { systemControls.networkStatus() }.getOrNull()
        val power = runCatching { systemControls.powerStatus() }.getOrNull()
        val (networkGlyph, networkLevel) = when {
            network == null -> Glyph.NETWORK to 1f
            network.airplaneMode && network.transport == PlasmaSystemControls.Transport.NONE -> Glyph.AIRPLANE to 1f
            network.transport == PlasmaSystemControls.Transport.WIFI -> Glyph.WIFI to (network.wifiLevel ?: 1f)
            // With a SIM the antenna has its own tray icon; this one then shows the Wi-Fi state.
            network.transport == PlasmaSystemControls.Transport.CELLULAR && network.simPresent ->
                (if (network.wifiEnabled) Glyph.WIFI else Glyph.WIFI_OFF) to 0f
            network.transport == PlasmaSystemControls.Transport.CELLULAR -> Glyph.CELLULAR to (network.cellularLevel ?: 1f)
            network.transport == PlasmaSystemControls.Transport.ETHERNET -> Glyph.ETHERNET to 1f
            network.transport == PlasmaSystemControls.Transport.OTHER -> Glyph.NETWORK to 1f
            network.wifiEnabled -> Glyph.WIFI to 0f
            else -> Glyph.WIFI_OFF to 1f
        }
        trayState = TrayState(
            volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat(),
            muted = audio.isStreamMute(AudioManager.STREAM_MUSIC),
            battery = power?.level ?: 1f,
            charging = power?.charging == true,
            networkConnected = network == null || network.transport != PlasmaSystemControls.Transport.NONE,
            networkGlyph = networkGlyph,
            networkLevel = networkLevel,
            batterySaver = power?.batterySaver == true,
            cellularVisible = network?.simPresent == true && !network.airplaneMode,
            cellularLevel = network?.cellularLevel ?: 0f,
            cellularNoService = network?.cellularLevel == null,
            cellularType = cellularType,
            wifiConnected = network?.transport == PlasmaSystemControls.Transport.WIFI,
        )
        if (::panel.isInitialized) invalidateDesktopPanel()
        if (autoMode) autoUi?.updateTray(trayState)
    }

    private fun registerTrayReceiver() {
        val filter = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction(android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction("android.net.conn.CONNECTIVITY_CHANGE")
            addAction(android.net.wifi.WifiManager.RSSI_CHANGED_ACTION)
            addAction(android.net.wifi.WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction("android.intent.action.SERVICE_STATE")
            addAction("android.intent.action.ANY_DATA_STATE")
            addAction("android.media.VOLUME_CHANGED_ACTION")
            addAction(AudioManager.ACTION_HEADSET_PLUG)
        }
        runCatching {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                service.registerReceiver(trayReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                service.registerReceiver(trayReceiver, filter)
            }
        }.onFailure { host.log("Plasma v2 tray receiver failed: ${it.message}") }
    }
    // endregion

    // region popups (children of the compositor, never extra windows)
    private fun recentlyDismissed(kind: String) =
        popupDismissedKind == kind && SystemClock.uptimeMillis() - popupDismissedAt < 300L

    @SuppressLint("ClickableViewAccessibility")
    private fun showPopup(kind: String, content: View, bounds: Rect, fromBelow: Boolean = true) {
        if (autoMode) popupLayer.bringToFront()
        dismissPopupImmediately()
        hideTooltip()
        val scrim = View(displayContext)
        scrim.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) dismissPopup()
            true
        }
        popupLayer.addView(scrim, FrameLayout.LayoutParams(-1, -1))
        val frame = PlasmaPopupFrame(displayContext, palette, onOutside = { dismissPopup() })
        frame.addView(content, FrameLayout.LayoutParams(-1, -1))
        popupLayer.addView(frame, FrameLayout.LayoutParams(bounds.width(), bounds.height()).apply {
            leftMargin = bounds.left
            topMargin = bounds.top
        })
        popupLayer.bringToFront()
        popup = frame
        popupKind = kind
        updateShellFocus()
        frame.alpha = 0f
        frame.translationY = if (fromBelow) dp(12).toFloat() else -dp(12).toFloat()
        frame.animate().alpha(1f).translationY(0f).setDuration(settings.duration(PlasmaTheme.LONG_MS))
            .setInterpolator(PlasmaTheme.outCubic).start()
    }

    private fun showAnchoredPopup(kind: String, content: View, anchor: RectF, popupWidth: Int, fixedHeight: Int? = null) {
        if (autoMode) {
            val area = autoUi?.visibleBounds() ?: Rect(0,0,width,height)
            val w = popupWidth.coerceAtMost((area.width()-dp(12)).coerceAtLeast(1))
            val maxHeight = (area.height()-dp(12)).coerceAtLeast(1)
            val scroll = android.widget.ScrollView(displayContext).apply { addView(content) }
            scroll.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(maxHeight,View.MeasureSpec.AT_MOST))
            val h = scroll.measuredHeight.coerceIn(1,maxHeight)
            val left = area.left+(area.width()-w)/2
            val top = area.top+(area.height()-h)/2
            showPopup(kind,scroll,Rect(left,top,left+w,top+h))
            return
        }
        val h = fixedHeight ?: run {
            content.measure(View.MeasureSpec.makeMeasureSpec(popupWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.AT_MOST))
            content.measuredHeight
        }
        val panelTop = height - panel.thickness - (panel.floatMargin * panelFloat).roundToInt() * 2
        val left = (anchor.centerX() - popupWidth / 2f).roundToInt().coerceIn(dp(6), width - popupWidth - dp(6))
        val top = (panelTop - h - dp(6)).coerceAtLeast(dp(6))
        showPopup(kind, content, Rect(left, top, left + popupWidth, top + h))
    }

    fun showMenu(items: List<MenuItem>, rawX: Float, rawY: Float, kind: String = "menu", above: Boolean = false) {
        val view = PlasmaMenuView(displayContext, palette, items) { dismissPopup() }
        val (w, h) = view.desiredSize()
        var left = rawX.roundToInt()
        var top = if (above) height - panel.thickness - (panel.floatMargin * panelFloat).roundToInt() * 2 - h - dp(6) else rawY.roundToInt()
        if (left + w > width) left = width - w - dp(4)
        if (top + h > height) top = (rawY - h).roundToInt()
        showPopup(kind, view, Rect(left.coerceAtLeast(dp(4)), top.coerceAtLeast(dp(4)), left.coerceAtLeast(dp(4)) + w, top.coerceAtLeast(dp(4)) + h), fromBelow = above)
    }

    override fun showMenu(items: List<MenuItem>, rawX: Float, rawY: Float) = showMenu(items, rawX, rawY, "menu", false)

    override fun dismissPopup() {
        val frame = popup ?: return
        popupDismissedKind = popupKind
        popupDismissedAt = SystemClock.uptimeMillis()
        popup = null
        popupKind = null
        kickoff = null
        krunner = null
        updateShellFocus()
        frame.animate().alpha(0f).translationY(dp(8).toFloat()).setDuration(settings.duration(PlasmaTheme.SHORT_MS + 40))
            .withEndAction { popupLayer.removeAllViews() }.start()
        // Remove the scrim immediately so the next click reaches the desktop.
        (0 until popupLayer.childCount).map { popupLayer.getChildAt(it) }.filter { it !== frame }.forEach(popupLayer::removeView)
    }

    private fun dismissPopupImmediately() {
        popupLayer.removeAllViews()
        popup = null
        popupKind = null
        kickoff = null
        krunner = null
    }

    /**
     * With [passiveShellFocus], the overlay is focusable only while a Plasma
     * text field (popup, overview search or leave screen) is open. Otherwise
     * touches would move focus off the application display and close its
     * keyboard; shortcuts still arrive through the accessibility service.
     */
    private fun updateShellFocus() {
        if (!passiveShellFocus || !::root.isInitialized) return
        val focusable = popup != null || overviewActive || leaveScreen != null || linuxKeyboardShown
        if (focusable == shellFocusable) return
        shellFocusable = focusable
        layer.show(root, Rect(0, 0, width, height), focusable = focusable, title = shellTitle)
        if (!focusable) windows.firstOrNull { it.id == activeId && !it.minimized }?.let(::focusWindowDisplay)
    }

    fun toggleKickoff() {
        if (popupKind == "kickoff") {
            dismissPopup()
            return
        }
        val view = KickoffView(displayContext, this)
        kickoff = view
        val w = minOf(dp(680), (width * .9f).roundToInt())
        val h = minOf(dp(560), (height * .8f).roundToInt())
        val anchor = panel.launcherRect()
        val left = (anchor?.left ?: 0f).roundToInt().coerceIn(dp(6), width - w - dp(6))
        val panelTop = height - panel.thickness - (panel.floatMargin * panelFloat).roundToInt() * 2
        showPopup("kickoff", view, Rect(left, (panelTop - h - dp(6)).coerceAtLeast(dp(6)), left + w, (panelTop - h - dp(6)).coerceAtLeast(dp(6)) + h))
        view.post { view.focusSearch() }
    }

    fun showKRunner() {
        if (popupKind == "krunner") {
            dismissPopup()
            return
        }
        val view = KRunnerView(displayContext, this)
        krunner = view
        val w = minOf(dp(620), (width * .9f).roundToInt())
        val left = (width - w) / 2
        showPopup("krunner", view, Rect(left, 0, left + w, height / 2), fromBelow = false)
        view.post { view.search.requestFocus() }
    }

    override fun openDesktopSettings() {
        val view = DesktopSettingsApplet(displayContext, palette, settings) { next -> applySettings(next) }
        val scroll = android.widget.ScrollView(displayContext).apply {
            isFillViewport = true
            addView(view)
        }
        val w = dp(560).coerceAtMost((width - dp(24)).coerceAtLeast(1))
        scroll.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((height - panel.thickness - dp(40)).coerceAtLeast(1), View.MeasureSpec.AT_MOST))
        val h = scroll.measuredHeight
        val left = (width - w) / 2
        val top = ((height - panel.thickness - h) / 2).coerceAtLeast(dp(8))
        showPopup("settings", scroll, Rect(left, top, left + w, top + h))
    }

    private fun applySettings(next: PlasmaSettings) {
        service.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE).edit()
            .putString("flutter.plasma_color_scheme", next.colorScheme)
            .putLong("flutter.plasma_accent_color", next.accentColor.toLong() and 0xFFFFFFFFL)
            .putBoolean("flutter.plasma_floating_panel", next.floatingPanel)
            .putLong("flutter.plasma_virtual_desktops", next.virtualDesktops.toLong())
            .putString("flutter.plasma_animation_speed", "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBEb3VibGUu${next.animationSpeed.toDouble()}")
            .putString("flutter.plasma_wallpaper", next.wallpaper)
            .apply()
        val paletteChanged = next.colorScheme != settings.colorScheme || next.accentColor != settings.accentColor
        reloadSettings()
        if (paletteChanged && popupKind == "settings") handler.post { openDesktopSettings() }
    }

    private fun showTooltip(entry: PanelTask, anchor: RectF) {
        if (popup != null) return
        // Live thumbnails straight from each window's TextureView.
        entry.windows.mapNotNull(::windowFor).forEach { window ->
            val content = window.view.content
            if (content.isAvailable && content.width > 0) {
                window.model.thumbnail = runCatching { content.getBitmap(content.width / 3, content.height / 3) }.getOrNull()
            }
        }
        val view = tooltip ?: PanelTooltipView(displayContext, palette).also {
            tooltip = it
            root.addView(it, FrameLayout.LayoutParams(1, 1))
        }
        view.bind(entry)
        val (w, h) = view.desiredSize()
        val panelTop = height - panel.thickness - (panel.floatMargin * panelFloat).roundToInt() * 2
        val params = view.layoutParams as FrameLayout.LayoutParams
        params.width = w
        params.height = h
        params.leftMargin = (anchor.centerX() - w / 2f).roundToInt().coerceIn(dp(4), width - w - dp(4))
        params.topMargin = panelTop - h - dp(6)
        view.layoutParams = params
        view.bringToFront()
        view.invalidate()
    }

    private fun hideTooltip() {
        tooltipRunnable?.let(handler::removeCallbacks)
        tooltip?.let(root::removeView)
        tooltip = null
    }
    // endregion

    // region launcher host
    override fun apps(): List<PlasmaApp> = if (linuxApps.isEmpty()) appRepository.apps else appRepository.apps + linuxApps
    override fun favorites(): List<String> = csv("favorites")
    override fun recent(): List<String> = csv("recent")
    override fun isFavorite(packageName: String) = packageName in favorites()
    override fun isPinned(packageName: String) = packageName in pinnedList()
    private fun pinnedList() = csv("pinned")
    private fun csv(key: String): List<String> = prefs.getString(key, "").orEmpty().split(',').filter { it.isNotBlank() }

    private fun editCsv(key: String, transform: (MutableList<String>) -> Unit) {
        val list = csv(key).toMutableList()
        transform(list)
        prefs.edit().putString(key, list.joinToString(",")).apply()
    }

    override fun setFavorite(packageName: String, favorite: Boolean) =
        editCsv("favorites") { list -> list.remove(packageName); if (favorite) list += packageName }

    override fun setPinned(packageName: String, pinned: Boolean) {
        editCsv("pinned") { list -> list.remove(packageName); if (pinned) list += packageName }
        invalidateDesktopPanel()
    }

    override fun launch(packageName: String) = launch(packageName, newInstance = false)

    fun launch(packageName: String, newInstance: Boolean) {
        if (autoMode) autoUi?.hidePage()
        dismissPopup()
        editCsv("recent") { list -> list.remove(packageName); list.add(0, packageName); while (list.size > 12) list.removeAt(list.lastIndex) }
        if (packageName.startsWith(LINUX_APP_PREFIX)) {
            launchLinuxApp(packageName)
            return
        }
        if (packageName.startsWith("linux")) return
        if (!newInstance) {
            windows.filter { it.packageName == packageName && !it.closing }.maxByOrNull { it.lastActivated }?.let {
                activate(it)
                return
            }
        }
        if (service.packageManager.getLaunchIntentForPackage(packageName) == null) return
        if (!autoMode) panel.bounceLaunch(packageName)
        createWindow(packageName, smartPlacement())
    }

    /** Opens a settings screen (or any intent) in a new composited window. */
    private fun launchIntentInWindow(intent: Intent) {
        val component = intent.resolveActivity(service.packageManager) ?: return
        createWindow(component.packageName, smartPlacement(), intent = intent)
        if (autoMode) autoUi?.hidePage()
    }

    override fun openAppInfo(packageName: String) {
        dismissPopup()
        launchIntentInWindow(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
    }

    override fun copyToClipboard(text: String) {
        runCatching { service.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Dextop", text)) }
    }

    override fun leave(action: LeaveAction) {
        dismissPopup()
        when (action) {
            LeaveAction.SHOW_DIALOG -> showLeaveScreen()
            LeaveAction.SLEEP, LeaveAction.LOCK -> host.privilegedExecute("input", "keyevent", "KEYCODE_SLEEP")
            LeaveAction.RESTART -> host.restartShell()
            LeaveAction.SHUTDOWN -> host.stopSession()
            LeaveAction.LOGOUT -> host.switchToSystemWindowManager()
        }
    }

    private fun showLeaveScreen() {
        leaveScreen?.let(root::removeView)
        val screen = LeaveScreen(displayContext, palette, LeaveAction.SHUTDOWN) { choice ->
            leaveScreen?.let(root::removeView)
            leaveScreen = null
            updateShellFocus()
            choice?.let(::leave)
        }
        leaveScreen = screen
        updateShellFocus()
        root.addView(screen, FrameLayout.LayoutParams(-1, -1))
        screen.requestFocus()
    }
    // endregion

    // region switcher & keyboard
    private fun switcherCandidates() = windows.filter { !it.closing && (it.onAllDesktops || it.desktop == currentDesktop) }
        .sortedByDescending { it.lastActivated }

    private fun showSwitcher(reverse: Boolean) {
        val candidates = switcherCandidates()
        if (candidates.isEmpty()) return
        switcherRequested = true
        candidates.forEach { it.syncModel(it.id == activeId) }
        val thumbnails = candidates.associate { window ->
            val content = window.view.content
            window.id to (if (content.isAvailable && content.width > 0) runCatching {
                content.getBitmap(content.width / 3, content.height / 3)
            }.getOrNull() else null)
        }
        val view = TaskSwitcherView(displayContext, palette, candidates.map { it.model }, thumbnails)
        if (reverse) view.index = candidates.lastIndex
        switcher = view
        val (w, h) = view.desiredSize((width * .9f).roundToInt())
        root.addView(view, FrameLayout.LayoutParams(w, h, Gravity.CENTER))
        view.alpha = 0f
        view.animate().alpha(1f).setDuration(settings.duration(PlasmaTheme.SHORT_MS)).start()
    }

    private fun commitSwitcher() {
        switcherRequested = false
        val view = switcher ?: run {
            switcherCandidates().getOrNull(1)?.let(::activate)
            return
        }
        switcher = null
        view.selectedWindow()?.let(::windowFor)?.let(::activate)
        root.removeView(view)
    }

    private fun triggerHotCorner() {
        val now = SystemClock.uptimeMillis()
        if (autoMode || !settings.hotCorner || now - lastHotCornerAt < 900L || moving != null) return
        lastHotCornerAt = now
        toggleOverview()
    }

    /** Typing goes to Dextop's own text fields when one is open. */
    private fun shellTextInputActive(): Boolean =
        kickoff != null || krunner != null || leaveScreen != null ||
            (overviewActive && overviewSearch != null) || popupKind == "settings"

    override fun handleKey(event: KeyEvent): Boolean {
        if (!running) return false
        if (PlasmaVolumeKeys.handle(service, event)) {
            if (event.action == KeyEvent.ACTION_DOWN) onVolumeChanged()
            return true
        }
        if (autoMode) {
            if (event.keyCode == KeyEvent.KEYCODE_META_LEFT || event.keyCode == KeyEvent.KEYCODE_META_RIGHT) {
                if (event.action == KeyEvent.ACTION_UP) showHome()
                return true
            }
            if (event.keyCode == KeyEvent.KEYCODE_ESCAPE && event.action == KeyEvent.ACTION_DOWN && popup != null) {
                dismissPopup()
                return true
            }
            if (popup != null) return false
            if (autoUi?.pageDiagnostic != "none") return false
            activeLinuxContainer()?.let { id ->
                forwardLinuxKey(id, event)
                return true
            }
            val target = windows.firstOrNull { it.id == activeId && isShown(it) }?.displayId ?: return false
            if (target < 0) return false
            injectAsync(KeyEvent(event), target)
            return true
        }
        val down = event.action == KeyEvent.ACTION_DOWN
        val code = event.keyCode
        if (!down && swallowedKeys.remove(code)) return true
        when (code) {
            KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_META_RIGHT -> {
                if (down) {
                    if (event.repeatCount == 0) { metaDown = true; metaCombo = false }
                } else {
                    val open = metaDown && !metaCombo
                    metaDown = false
                    if (switcher != null || switcherRequested) commitSwitcher() else if (open) toggleKickoff()
                }
                return true
            }
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> {
                altDown = down
                if (!down && (switcher != null || switcherRequested)) commitSwitcher()
            }
            KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT -> ctrlDown = down
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> shiftDown = down
        }
        if (down) {
            val meta = metaDown || event.isMetaPressed
            val alt = altDown || event.isAltPressed
            val ctrl = ctrlDown || event.isCtrlPressed
            val shift = shiftDown || event.isShiftPressed
            if (meta) metaCombo = true
            val active = windows.firstOrNull { it.id == activeId }
            // Every key combination goes to the active window, Linux or Android,
            // exactly as typed. Plasma only reacts to the Meta (Dextop) key.
            val handled = when {
                meta && code == KeyEvent.KEYCODE_ESCAPE && active?.view?.content?.hasPointerCapture() == true -> {
                    active.linuxManualCapture = false
                    active.linuxCaptureSuppressed = true
                    active.view.content.releasePointerCapture(); captureLost(active)
                    active.linuxContainer?.let { id -> LinuxContainerClient.service()?.activateWindow(id, 0) }
                    true
                }
                meta && code == KeyEvent.KEYCODE_M && active?.linuxContainer != null -> {
                    active.linuxCaptureSuppressed = active.view.content.hasPointerCapture()
                    active.linuxManualCapture = !active.linuxCaptureSuppressed
                    if (active.linuxCaptureSuppressed) { active.view.content.releasePointerCapture(); captureLost(active) }
                    else { active.view.content.requestFocus(); active.view.content.requestPointerCapture() }
                    true
                }
                meta && code == KeyEvent.KEYCODE_TAB -> {
                    val view = switcher
                    if (view == null) {
                        if (!switcherRequested) showSwitcher(shift)
                    } else {
                        val count = switcherCandidates().size.coerceAtLeast(1)
                        view.index = (view.index + if (shift) count - 1 else 1) % count
                    }
                    true
                }
                meta && code == KeyEvent.KEYCODE_SPACE -> { showKRunner(); true }
                meta && (code == KeyEvent.KEYCODE_W || code == KeyEvent.KEYCODE_G) -> { toggleOverview(); true }
                meta && code == KeyEvent.KEYCODE_D -> { togglePeek(); true }
                meta && code == KeyEvent.KEYCODE_ESCAPE && active?.linuxFullscreen == true -> { minimize(active); true }
                meta && code == KeyEvent.KEYCODE_PAGE_UP -> { active?.let(::toggleMaximize); true }
                meta && code == KeyEvent.KEYCODE_PAGE_DOWN -> { active?.let(::minimize); true }
                meta && ctrl && code == KeyEvent.KEYCODE_DPAD_LEFT -> { onDesktopScroll(-1); true }
                meta && ctrl && code == KeyEvent.KEYCODE_DPAD_RIGHT -> { onDesktopScroll(1); true }
                meta && code == KeyEvent.KEYCODE_DPAD_LEFT -> { active?.let { tile(it, TileZone.LEFT) }; true }
                meta && code == KeyEvent.KEYCODE_DPAD_RIGHT -> { active?.let { tile(it, TileZone.RIGHT) }; true }
                meta && code == KeyEvent.KEYCODE_DPAD_UP -> { active?.let { tile(it, TileZone.TOP) }; true }
                meta && code == KeyEvent.KEYCODE_DPAD_DOWN -> { active?.let { tile(it, TileZone.BOTTOM) }; true }
                meta && code in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9 -> {
                    panelEntries().getOrNull(code - KeyEvent.KEYCODE_1)?.let { onTaskClicked(it, panel.taskIconRect(it.packageName) ?: RectF()) }
                    true
                }
                code == KeyEvent.KEYCODE_ESCAPE && overviewActive -> { exitOverview(null); true }
                code == KeyEvent.KEYCODE_ESCAPE && switcher != null -> { switcher?.let(root::removeView); switcher = null; switcherRequested = false; true }
                code == KeyEvent.KEYCODE_ESCAPE && popup != null && !shellTextInputActive() -> { dismissPopup(); true }
                else -> false
            }
            if (handled) {
                swallowedKeys += code
                return true
            }
        }
        if (shellTextInputActive() || popup != null) {
            // Kickoff/KRunner may not hold focus yet; type into their field.
            val field = kickoff?.search ?: krunner?.search ?: overviewSearch
            if (field != null && down && !field.isFocused && event.unicodeChar > 0 && !event.isCtrlPressed && !event.isMetaPressed) {
                field.requestFocus()
                field.append(String(Character.toChars(event.unicodeChar)))
                return true
            }
            return false
        }
        // Everything else belongs to the active application window.
        activeLinuxContainer()?.let { id ->
            forwardLinuxKey(id, event)
            return true
        }
        val target = windows.firstOrNull { it.id == activeId && !it.minimized }?.displayId ?: return false
        if (target < 0) return false
        injectAsync(KeyEvent(event), target)
        return true
    }
    // endregion

    /** Plasma wallpaper; also the desktop click/right-click target. */
    private inner class WallpaperView(context: Context) : View(context) {
        private var bitmap: Bitmap? = null
        private val detector = android.view.GestureDetector(context, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onSingleTapUp(e: MotionEvent): Boolean { dismissPopup(); return true }
            override fun onLongPress(e: MotionEvent) = showDesktopMenu(e.rawX, e.rawY)
        })

        fun refresh() {
            bitmap?.recycle()
            bitmap = null
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            if (settings.wallpaper == "system") {
                canvas.drawColor(0xFF101418.toInt())
            }
            val current = bitmap ?: PlasmaWallpaper.render(width.coerceAtLeast(1), height.coerceAtLeast(1)).also { bitmap = it }
            if (settings.wallpaper != "system") canvas.drawBitmap(current, 0f, 0f, null)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN && event.isButtonPressed(MotionEvent.BUTTON_SECONDARY)) {
                showDesktopMenu(event.rawX, event.rawY)
                return true
            }
            return detector.onTouchEvent(event)
        }
    }

    private fun showDesktopMenu(rawX: Float, rawY: Float) {
        showMenu(listOf(
            MenuItem(t("nativePlasmaDesktop"), header = true),
            MenuItem(t("nativePlasmaOverview"), Glyph.OVERVIEW) { toggleOverview() },
            MenuItem(t("nativePlasmaRunCommand"), Glyph.SEARCH) { showKRunner() },
            MenuItem(if (peeking) t("nativePlasmaStopPeeking") else t("nativePlasmaPeekAtDesktop"), Glyph.SHOW_DESKTOP) { togglePeek() },
            MenuItem(t("nativePlasmaConfigureDesktop"), Glyph.WALLPAPER, separatorBefore = true) { openDesktopSettings() },
            MenuItem(t("nativePlasmaLeave"), Glyph.LOGOUT, separatorBefore = true) { leave(LeaveAction.SHOW_DIALOG) },
        ), rawX, rawY)
    }

    /** KWin quick-tile outline shown while dragging a window to a screen edge. */
    private class SnapPreviewView(context: Context, private val paletteProvider: () -> PlasmaPalette) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var target: RectF? = null
        private val current = RectF()
        private var animator: ValueAnimator? = null
        private var alphaValue = 0f
        private val density = resources.displayMetrics.density

        fun show(next: RectF?) {
            if (next == target || (next != null && target != null && next == target)) return
            val from = RectF(current)
            val fromAlpha = alphaValue
            target = next
            animator?.cancel()
            if (next != null && fromAlpha == 0f) from.set(next.centerX(), next.centerY(), next.centerX(), next.centerY())
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 200L
                interpolator = PlasmaTheme.outCubic
                addUpdateListener {
                    val t = it.animatedValue as Float
                    val to = next ?: from
                    current.set(from.left + (to.left - from.left) * t, from.top + (to.top - from.top) * t,
                        from.right + (to.right - from.right) * t, from.bottom + (to.bottom - from.bottom) * t)
                    alphaValue = fromAlpha + ((if (next == null) 0f else 1f) - fromAlpha) * t
                    invalidate()
                }
                start()
            }
        }

        override fun onDraw(canvas: Canvas) {
            if (alphaValue <= 0f) return
            val palette = paletteProvider()
            paint.style = Paint.Style.FILL
            paint.color = PlasmaTheme.withAlpha(palette.accent, (0x40 * alphaValue).toInt())
            canvas.drawRoundRect(current, 8 * density, 8 * density, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2 * density
            paint.color = PlasmaTheme.withAlpha(palette.accent, (0xDD * alphaValue).toInt())
            canvas.drawRoundRect(current, 8 * density, 8 * density, paint)
        }
    }
}
