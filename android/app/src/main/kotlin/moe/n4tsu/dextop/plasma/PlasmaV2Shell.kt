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
import android.view.Surface
import android.view.View
import android.widget.FrameLayout
import moe.n4tsu.dextop.VirtualDisplayPlatform
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
internal class PlasmaV2Shell(
    private val service: Context,
    override val displayId: Int,
    displayWidth: Int,
    displayHeight: Int,
    private val densityDpi: Int,
    private val host: PlasmaShell.Host,
) : DesktopShell, V2WindowHost, PanelHost, LauncherHost {

    companion object {
        private const val TAG = "DextopPlasmaV2"
        private const val PREFS = "dextop_plasma"
        private const val POLL_INTERVAL_MS = 500L
        private const val EMPTY_CLOSE_MS = 900L
        private const val LAUNCH_TIMEOUT_MS = 7_000L
        private const val INJECT_ASYNC = 0
        private const val DISPLAY_IME_POLICY_LOCAL = 0
    }

    // region state
    private val handler = Handler(Looper.getMainLooper())
    private val displayContext: Context = service.createDisplayContext(
        service.getSystemService(DisplayManager::class.java).getDisplay(displayId)
            ?: error("Dextop display $displayId is unavailable"),
    )
    private val layer = OverlayLayer(displayContext, TAG)
    private val bridge = TaskBridge(host::privilegedService, TAG)
    private val appRepository = PlasmaAppRepository(service)
    private val prefs = service.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val wmExecutor = Executors.newSingleThreadExecutor { Thread(it, "DextopPlasmaV2WM") }
    private val inputExecutor = Executors.newSingleThreadExecutor { Thread(it, "DextopPlasmaV2Input") }
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
    override var settings: PlasmaSettings = PlasmaSettings.load(service)
        private set
    override var palette: PlasmaPalette = PlasmaTheme.resolve(service, settings)
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
    private lateinit var wallpaper: WallpaperView
    private lateinit var dim: View
    private lateinit var windowsLayer: FrameLayout
    private lateinit var snapPreview: SnapPreviewView
    private lateinit var overviewChrome: FrameLayout
    private lateinit var panel: PlasmaPanelView
    private lateinit var popupLayer: FrameLayout
    private var panelFloat = 1f
    private var panelFloatTarget = 1f
    private var panelAnimator: ValueAnimator? = null

    private var popup: View? = null
    private var popupKind: String? = null
    private var popupDismissedAt = 0L
    private var popupDismissedKind: String? = null
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
    private val titleHeight get() = dp(30)
    private val grip get() = dp(7)
    // endregion

    // region lifecycle
    override fun start() {
        if (running) return
        running = true
        appRepository.refresh { panel.invalidate() }
        ensureDefaults()
        buildRoot()
        refreshTray()
        handler.postDelayed(pollRunnable, POLL_INTERVAL_MS)
        handler.postDelayed(trayTicker, 30_000L)
        host.log("Plasma v2 compositor started display=$displayId ${width}x$height/$densityDpi")
    }

    override fun stop(restoreWindows: Boolean) {
        if (!running) return
        running = false
        handler.removeCallbacksAndMessages(null)
        val snapshot = windows.toList()
        windows.clear()
        // Applications move back to the Dextop display so the platform
        // desktop shows them after the compositor goes away.
        wmExecutor.execute {
            snapshot.forEach { window ->
                if (window.displayId >= 0) {
                    runCatching {
                        bridge.tasks(window.displayId).forEach { task ->
                            if (restoreWindows) bridge.moveTaskToDisplay(task, displayId) else bridge.close(task)
                        }
                    }
                }
                runCatching { window.display?.attachment?.release() }
            }
        }
        wmExecutor.shutdown()
        inputExecutor.shutdown()
        layer.clear()
        appRepository.shutdown()
        host.log("Plasma v2 compositor stopped display=$displayId")
    }

    override fun onDisplayResized(newWidth: Int, newHeight: Int) {
        if (newWidth == width && newHeight == height) return
        width = newWidth
        height = newHeight
        layer.show(root, Rect(0, 0, width, height), focusable = true, title = "Dextop Plasma v2")
        wallpaper.refresh()
        layoutPanel()
        windows.forEach { window ->
            when {
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
        palette = PlasmaTheme.resolve(service, settings)
        if (settings.virtualDesktops < previousDesktops) {
            val last = settings.virtualDesktops - 1
            windows.filter { it.desktop > last }.forEach { it.desktop = last }
            if (currentDesktop > last) switchDesktop(last, animate = false)
        }
        wallpaper.refresh()
        windows.forEach {
            it.view.chrome.setBackgroundColor(palette.windowBackground)
            it.view.titleBar.invalidate()
        }
        updatePanelFloat(force = true)
        panel.invalidate()
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
        root = FrameLayout(displayContext).apply {
            clipChildren = false
            isFocusable = true
            isFocusableInTouchMode = true
        }
        wallpaper = WallpaperView(displayContext)
        root.addView(wallpaper, FrameLayout.LayoutParams(-1, -1))
        dim = View(displayContext).apply {
            setBackgroundColor(0xB0000000.toInt())
            alpha = 0f
            visibility = View.GONE
        }
        root.addView(dim, FrameLayout.LayoutParams(-1, -1))
        windowsLayer = FrameLayout(displayContext).apply { clipChildren = false }
        root.addView(windowsLayer, FrameLayout.LayoutParams(-1, -1))
        snapPreview = SnapPreviewView(displayContext) { palette }
        root.addView(snapPreview, FrameLayout.LayoutParams(-1, -1))
        overviewChrome = FrameLayout(displayContext).apply { visibility = View.GONE }
        root.addView(overviewChrome, FrameLayout.LayoutParams(-1, -1))
        panel = PlasmaPanelView(displayContext, this)
        panelFloat = if (settings.floatingPanel) 1f else 0f
        panelFloatTarget = panelFloat
        panel.floatFraction = panelFloat
        root.addView(panel, FrameLayout.LayoutParams(-1, panel.thickness + panel.floatMargin * 2, Gravity.BOTTOM))
        popupLayer = FrameLayout(displayContext)
        root.addView(popupLayer, FrameLayout.LayoutParams(-1, -1))
        // Top-left hot corner: a 2px hover target above everything.
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
        layer.show(root, Rect(0, 0, width, height), focusable = true, title = "Dextop Plasma v2")
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
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        val existing = window.display
        if (existing != null) {
            wmExecutor.execute { runCatching { existing.resize(surface, window.displayWidth, window.displayHeight, densityDpi) } }
            return
        }
        window.displayWidth = w
        window.displayHeight = h
        wmExecutor.execute {
            val created = runCatching {
                displayPlatform.openApplicationDisplay(service, displayService, "DextopWindow-${window.id}", surface, w, h, densityDpi)
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
                wmExecutor.execute { showImeInside(created.displayId) }
                host.log("Plasma v2 window ${window.id} display=${created.displayId} ${w}x$h pkg=${window.packageName}")
                val adopt = window.pendingAdoption
                if (adopt != null) {
                    wmExecutor.execute { bridge.moveTaskToDisplay(adopt, created.displayId) }
                } else {
                    startOnDisplay(window, newInstance = false)
                }
            }
        }
    }

    override fun onSurfaceDestroyed(window: V2Window) {
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
        panel.invalidate()
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
            val moved = candidate != null && bridge.moveTaskToDisplay(candidate, display)
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
        window.closing = true
        windows.remove(window)
        if (activeId == window.id) activeId = windows.filter { isShown(it) }.maxByOrNull { it.lastActivated }?.id ?: -1
        window.view.animate().alpha(0f).scaleX(.85f).scaleY(.85f)
            .setDuration(settings.duration(PlasmaTheme.LONG_MS))
            .setInterpolator(PlasmaTheme.inCubic)
            .withEndAction { removeWindowView(window) }
            .start()
        updatePanelFloat()
        panel.invalidate()
    }

    private fun removeWindowView(window: V2Window) {
        windows.remove(window)
        windowsLayer.removeView(window.view)
        val display = window.display
        window.display = null
        if (display != null) runCatching { wmExecutor.execute { runCatching { display.attachment.release() } } }
        panel.invalidate()
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
    private fun workArea(): Rect = Rect(0, 0, width, height - panel.thickness)

    private fun smartPlacement(): Rect {
        val area = workArea()
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
        !window.minimized && !window.closing && (window.onAllDesktops || window.desktop == currentDesktop)

    /** Places the window view; the application display follows when [resizeDisplay] is set. */
    private fun setFrame(window: V2Window, frame: Rect, resizeDisplay: Boolean) {
        window.frame.set(frame)
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
        if (resizeDisplay) resizeWindowDisplay(window)
    }

    private fun resizeWindowDisplay(window: V2Window) {
        val display = window.display ?: return
        val surface = window.surface ?: return
        val w = window.clientWidth().coerceAtLeast(1)
        val h = window.clientHeight(titleHeight)
        if (w == window.displayWidth && h == window.displayHeight) return
        window.displayWidth = w
        window.displayHeight = h
        runCatching { window.view.content.surfaceTexture?.setDefaultBufferSize(w, h) }
        wmExecutor.execute { runCatching { display.resize(surface, w, h, densityDpi) } }
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
        peeking = false
        val previous = activeId
        activeId = window.id
        window.lastActivated = SystemClock.uptimeMillis()
        window.view.bringToFront()
        if (previous != window.id) {
            windows.forEach { it.view.refreshElevation(it.id == activeId) }
            focusWindowDisplay(window)
        }
        panel.invalidate()
    }

    override fun beginMove(window: V2Window, rawX: Float, rawY: Float) {
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
        if (zone != TileZone.NONE) tile(window, zone)
        updatePanelFloat()
    }

    override fun beginResize(window: V2Window, edge: V2Edge, rawX: Float, rawY: Float) {
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
        activate(window)
    }

    fun tile(window: V2Window, zone: TileZone) {
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
        if (window.minimized) return
        window.minimized = true
        val icon = panel.taskIconRect(window.packageName)
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
        panel.invalidate()
    }

    private fun restoreFromMinimize(window: V2Window) {
        window.minimized = false
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
        panel.invalidate()
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
        panel.invalidate()
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
                PlasmaSearch.apps(this@PlasmaV2Shell, query).take(6).forEach { app ->
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
            val w = h * this@PlasmaV2Shell.width / this@PlasmaV2Shell.height
            val total = desktopCount() * (w + 12 * density) - 12 * density
            var x = width / 2f - total / 2f
            for (index in 0 until desktopCount()) {
                val r = RectF(x, 16 * density, x + w, 16 * density + h)
                rects += r
                val current = index == currentDesktop
                paint.style = Paint.Style.FILL
                paint.color = if (current) PlasmaTheme.withAlpha(palette.accent, 0x55) else 0x66000000
                canvas.drawRoundRect(r, 6 * density, 6 * density, paint)
                val sx = r.width() / this@PlasmaV2Shell.width
                val sy = r.height() / this@PlasmaV2Shell.height
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
            PanelTask(pkg, appRepository.label(pkg), appRepository.icon(pkg), group.map { it.model }, pkg in pinned,
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
            TrayItem.VOLUME -> {
                val audio = service.getSystemService(AudioManager::class.java)
                val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                VolumeApplet(displayContext, palette, trayState.volume, trayState.muted,
                    onVolume = { value -> audio.setStreamVolume(AudioManager.STREAM_MUSIC, (value * max).roundToInt(), 0); refreshTray() },
                    onToggleMute = {
                        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0)
                        refreshTray(); dismissPopup()
                    },
                    onOpenSettings = { dismissPopup(); launchIntentInWindow(Intent(Settings.ACTION_SOUND_SETTINGS)) },
                )
            }
            TrayItem.NETWORK -> {
                val (name, detail) = networkDescription()
                InfoApplet(displayContext, palette, t("nativePlasmaNetworks"), Glyph.NETWORK, name, detail,
                    actionLabel = t("nativePlasmaConfigureNetwork")) { dismissPopup(); launchIntentInWindow(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }
            }
            TrayItem.BATTERY -> InfoApplet(displayContext, palette, t("nativePlasmaPowerAndBattery"),
                if (trayState.charging) Glyph.BATTERY_CHARGING else Glyph.BATTERY, percentText(trayState.battery),
                if (trayState.charging) t("nativePlasmaCharging") else t("nativePlasmaDischarging"), level = trayState.battery,
                actionLabel = t("nativePlasmaConfigurePower")) { dismissPopup(); launchIntentInWindow(Intent(Intent.ACTION_POWER_USAGE_SUMMARY)) }
            TrayItem.EXPAND -> TrayOverflowApplet(displayContext, palette, listOf(
                MenuItem(t("nativePlasmaOverview"), Glyph.OVERVIEW) { toggleOverview() },
                MenuItem(t("nativePlasmaRunCommand"), Glyph.SEARCH) { showKRunner() },
                MenuItem(t("nativePlasmaConfigureDesktop"), Glyph.WALLPAPER) { openDesktopSettings() },
                MenuItem(t("nativePlasmaLeave"), Glyph.LOGOUT) { leave(LeaveAction.SHOW_DIALOG) },
            )) { dismissPopup() }
        }
        showAnchoredPopup(kind, view, anchor, dp(if (item == TrayItem.EXPAND) 300 else 360))
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

    private fun refreshTray() {
        val audio = service.getSystemService(AudioManager::class.java)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val battery = service.getSystemService(BatteryManager::class.java)
        val capacity = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 } ?: 100
        val connectivity = service.getSystemService(ConnectivityManager::class.java)
        val connected = runCatching {
            connectivity.getNetworkCapabilities(connectivity.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        }.getOrDefault(true)
        trayState = TrayState(audio.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat(),
            audio.isStreamMute(AudioManager.STREAM_MUSIC), capacity / 100f, battery.isCharging, connected)
        if (::panel.isInitialized) panel.invalidate()
    }

    private fun networkDescription(): Pair<String, String> {
        val connectivity = service.getSystemService(ConnectivityManager::class.java)
        val caps = runCatching { connectivity.getNetworkCapabilities(connectivity.activeNetwork) }.getOrNull()
        return when {
            caps == null -> t("nativePlasmaDisconnected") to t("nativePlasmaNoActiveNetwork")
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi" to t("nativePlasmaConnected")
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> t("nativePlasmaMobileData") to t("nativePlasmaConnected")
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet" to t("nativePlasmaConnected")
            else -> t("nativePlasmaConnected") to ""
        }
    }
    // endregion

    // region popups (children of the compositor, never extra windows)
    private fun recentlyDismissed(kind: String) =
        popupDismissedKind == kind && SystemClock.uptimeMillis() - popupDismissedAt < 300L

    @SuppressLint("ClickableViewAccessibility")
    private fun showPopup(kind: String, content: View, bounds: Rect, fromBelow: Boolean = true) {
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
        frame.alpha = 0f
        frame.translationY = if (fromBelow) dp(12).toFloat() else -dp(12).toFloat()
        frame.animate().alpha(1f).translationY(0f).setDuration(settings.duration(PlasmaTheme.LONG_MS))
            .setInterpolator(PlasmaTheme.outCubic).start()
    }

    private fun showAnchoredPopup(kind: String, content: View, anchor: RectF, popupWidth: Int, fixedHeight: Int? = null) {
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
        val w = dp(460)
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.AT_MOST))
        val h = view.measuredHeight
        val left = (width - w) / 2
        val top = ((height - panel.thickness - h) / 2).coerceAtLeast(dp(8))
        showPopup("settings", view, Rect(left, top, left + w, top + h))
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
    override fun apps(): List<PlasmaApp> = appRepository.apps
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
        panel.invalidate()
    }

    override fun launch(packageName: String) = launch(packageName, newInstance = false)

    fun launch(packageName: String, newInstance: Boolean) {
        dismissPopup()
        editCsv("recent") { list -> list.remove(packageName); list.add(0, packageName); while (list.size > 12) list.removeAt(list.lastIndex) }
        if (!newInstance) {
            windows.filter { it.packageName == packageName && !it.closing }.maxByOrNull { it.lastActivated }?.let {
                activate(it)
                return
            }
        }
        if (service.packageManager.getLaunchIntentForPackage(packageName) == null) return
        panel.bounceLaunch(packageName)
        createWindow(packageName, smartPlacement())
    }

    /** Opens a settings screen (or any intent) in a new composited window. */
    private fun launchIntentInWindow(intent: Intent) {
        val component = intent.resolveActivity(service.packageManager) ?: return
        createWindow(component.packageName, smartPlacement(), intent = intent)
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
            choice?.let(::leave)
        }
        leaveScreen = screen
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
        if (!settings.hotCorner || now - lastHotCornerAt < 900L || moving != null) return
        lastHotCornerAt = now
        toggleOverview()
    }

    /** Typing goes to Dextop's own text fields when one is open. */
    private fun shellTextInputActive(): Boolean =
        kickoff != null || krunner != null || leaveScreen != null ||
            (overviewActive && overviewSearch != null) || popupKind == "settings"

    override fun handleKey(event: KeyEvent): Boolean {
        if (!running) return false
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
                    if (open) toggleKickoff()
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
            val handled = when {
                alt && code == KeyEvent.KEYCODE_TAB -> {
                    val view = switcher
                    if (view == null) {
                        if (!switcherRequested) showSwitcher(shift)
                    } else {
                        val count = switcherCandidates().size.coerceAtLeast(1)
                        view.index = (view.index + if (shift) count - 1 else 1) % count
                    }
                    true
                }
                alt && code == KeyEvent.KEYCODE_F4 -> { active?.let(::close); true }
                alt && code == KeyEvent.KEYCODE_F3 -> { active?.let { showWindowMenu(it, it.frame.left.toFloat(), (it.frame.top + titleHeight).toFloat()) }; true }
                alt && code == KeyEvent.KEYCODE_F1 -> { toggleKickoff(); true }
                alt && (code == KeyEvent.KEYCODE_SPACE || code == KeyEvent.KEYCODE_F2) -> { showKRunner(); true }
                meta && (code == KeyEvent.KEYCODE_W || code == KeyEvent.KEYCODE_G) -> { toggleOverview(); true }
                meta && code == KeyEvent.KEYCODE_D -> { togglePeek(); true }
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
                ctrl && alt && code == KeyEvent.KEYCODE_DPAD_LEFT -> { onDesktopScroll(-1); true }
                ctrl && alt && code == KeyEvent.KEYCODE_DPAD_RIGHT -> { onDesktopScroll(1); true }
                ctrl && alt && code == KeyEvent.KEYCODE_DEL -> { leave(LeaveAction.SHOW_DIALOG); true }
                ctrl && code in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F4 -> { switchDesktop(code - KeyEvent.KEYCODE_F1); true }
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
