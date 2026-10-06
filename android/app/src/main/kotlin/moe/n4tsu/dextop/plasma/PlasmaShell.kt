package moe.n4tsu.dextop.plasma

import android.app.ActivityOptions
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
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
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/**
 * Dextop's own desktop shell ("Dextop Plasma").
 *
 * Like YoukiDex, application windows stay ordinary Android freeform tasks:
 * the platform draws their caption and performs dragging and resizing at
 * compositor speed.  The shell replaces the desktop around them with a
 * Plasma 6 style desktop, floating panel, task manager, system tray,
 * Kickoff, KRunner, Overview, Alt+Tab and virtual desktops, and performs
 * window operations (tile, maximize, minimize, focus, close) with single
 * privileged task calls.
 */
internal class PlasmaShell(
    private val service: Context,
    val displayId: Int,
    displayWidth: Int,
    displayHeight: Int,
    private val host: Host,
) : PanelHost, LauncherHost, OverviewHost {

    interface Host {
        fun privilegedService(name: String, interfaceName: String): Any
        fun privilegedExecute(vararg arguments: String): Boolean
        fun captureDisplay(callback: (DesktopFrame?) -> Unit)
        /** Starts the desktop layer while the display still accepts HOME activities. */
        fun launchDesktopHome(intent: Intent): Boolean
        fun stopSession()
        fun switchToSystemWindowManager()
        fun restartShell()
        fun log(message: String)
    }

    companion object {
        private const val TAG = "DextopPlasma"
        private const val PREFS = "dextop_plasma"
        private const val POLL_INTERVAL_MS = 400L
        private const val EXTERNAL_BOUNDS_GRACE_MS = 900L
        private const val HIDE_GRACE_MS = 1_200L
        private const val PARK_CHECK_MS = 700L

        @Volatile var current: PlasmaShell? = null
            private set
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
    private val wmExecutor = Executors.newSingleThreadExecutor { Thread(it, "DextopPlasmaWM") }
    private val pollQueued = AtomicBoolean(false)

    private var width = displayWidth
    private var height = displayHeight
    override var settings: PlasmaSettings = PlasmaSettings.load(service)
        private set
    override var palette: PlasmaPalette = PlasmaTheme.resolve(service, settings)
        private set

    private val windows = LinkedHashMap<Int, ManagedWindow>()
    private var zOrder: List<ManagedWindow> = emptyList()
    private var activeTaskId = -1
    private var currentDesktop = 0
    private var running = false
    private var desktopActivity: PlasmaDesktopActivity? = null
    private var peeking = false
    private var peekHidden: List<Int> = emptyList()
    /** Tasks the shell hid on purpose (minimize, other desktop, peek) and when. */
    private val hiddenAt = HashMap<Int, Long>()
    private val lastBoundsPush = HashMap<Int, Long>()
    private val pendingLaunches = HashMap<String, Pair<Rect?, Long>>()
    private val parkedOffscreen = HashSet<Int>()
    private var trayState = TrayState(.5f, false, 1f, false, true)

    private lateinit var panel: PlasmaPanelView
    private lateinit var effects: EffectsView
    private var hotCorner: View? = null
    private var panelFloat = 1f
    private var panelFloatTarget = 1f
    private var panelAnimator: android.animation.ValueAnimator? = null

    private var popup: PlasmaPopupFrame? = null
    private var popupKind: String? = null
    private var popupBounds: Rect? = null
    private var popupDismissedAt = 0L
    private var popupDismissedKind: String? = null
    private var kickoff: KickoffView? = null
    private var krunner: KRunnerView? = null
    private var tooltip: TooltipView? = null
    private var overview: OverviewView? = null
    private var switcher: TaskSwitcherView? = null
    private var leaveScreen: LeaveScreen? = null

    // keyboard
    private var metaDown = false
    private var metaCombo = false
    private var altDown = false
    private var ctrlDown = false
    private var shiftDown = false
    private val swallowedKeys = HashSet<Int>()
    private var lastHotCornerAt = 0L
    // endregion

    // region lifecycle
    fun start() {
        if (running) return
        running = true
        current = this
        appRepository.refresh { panel.invalidate() }
        ensureDefaults()
        effects = EffectsView(displayContext) { palette }
        effects.onIdle = { handler.post { if (!effects.hasWork()) layer.hide(effects) } }
        panel = PlasmaPanelView(displayContext, this)
        panelFloat = if (settings.floatingPanel) 1f else 0f
        panelFloatTarget = panelFloat
        panel.floatFraction = panelFloat
        layoutPanel()
        installHotCorner()
        launchDesktop()
        refreshTray()
        schedulePoll(0L)
        handler.postDelayed(trayTicker, 30_000L)
        host.log("Plasma shell started display=$displayId ${width}x$height organizer=${bridge.supportsOrganizer}")
    }

    fun stop(restoreWindows: Boolean = true) {
        if (!running) return
        running = false
        handler.removeCallbacksAndMessages(null)
        dismissPopupImmediately()
        hideTooltip()
        overview?.let { layer.hide(it) }
        overview = null
        switcher?.let { layer.hide(it) }
        switcher = null
        leaveScreen?.let { layer.hide(it) }
        leaveScreen = null
        layer.clear()
        val snapshot = windows.values.toList()
        val parked = HashSet(parkedOffscreen)
        if (restoreWindows) {
            // Hand every window back to the platform desktop visible and on-screen.
            wmExecutor.execute {
                snapshot.forEach { window ->
                    runCatching {
                        if (window.taskId in parked) bridge.setBounds(window.task, window.frame)
                        if (window.minimized || window.taskId in parked || window.desktop != currentDesktop) {
                            bridge.focus(window.task)
                        }
                    }
                }
            }
        }
        desktopActivity?.finish()
        desktopActivity = null
        wmExecutor.shutdown()
        appRepository.shutdown()
        windows.clear()
        if (current === this) current = null
        host.log("Plasma shell stopped display=$displayId")
    }

    fun onDisplayResized(newWidth: Int, newHeight: Int) {
        if (newWidth == width && newHeight == height) return
        width = newWidth
        height = newHeight
        layoutPanel()
        windows.values.forEach { window ->
            when {
                window.maximized -> applyFrame(window, workArea())
                window.tile != TileZone.NONE -> applyFrame(window, tileRect(window.tile))
            }
        }
    }

    fun onWindowsChanged() = schedulePoll(60L)

    fun reloadSettings() {
        val previousDesktops = settings.virtualDesktops
        settings = PlasmaSettings.load(service)
        palette = PlasmaTheme.resolve(service, settings)
        desktopActivity?.applySettings()
        if (settings.virtualDesktops < previousDesktops) {
            val last = settings.virtualDesktops - 1
            windows.values.filter { it.desktop > last }.forEach { it.desktop = last }
            if (currentDesktop > last) switchDesktop(last, animate = false)
        }
        hotCorner?.let { corner ->
            if (settings.hotCorner) layer.show(corner, Rect(0, 0, layer.dp(2), layer.dp(2)), title = "Dextop Plasma hot corner")
            else layer.hide(corner)
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
        if (!prefs.contains("favorites")) {
            val candidates = listOf(
                "com.android.chrome", "org.mozilla.firefox", "com.sec.android.app.sbrowser",
                "com.google.android.apps.nbu.files", "com.sec.android.app.myfiles", "com.google.android.documentsui",
                "com.android.settings", "com.android.vending", "com.google.android.youtube",
                "com.google.android.gm", "com.google.android.calendar", "com.sec.android.app.popupcalculator",
                "com.google.android.calculator", "com.google.android.apps.photos", "com.sec.android.gallery3d",
                "com.google.android.keep", "com.samsung.android.app.notes",
            ).filter { runCatching { service.packageManager.getLaunchIntentForPackage(it) != null }.getOrDefault(false) }
            prefs.edit()
                .putString("favorites", candidates.take(12).joinToString(","))
                .putString("pinned", candidates.filter {
                    it in setOf("com.android.settings", "com.android.vending", "com.android.chrome", "org.mozilla.firefox",
                        "com.sec.android.app.sbrowser", "com.sec.android.app.myfiles", "com.google.android.apps.nbu.files",
                        "com.google.android.documentsui")
                }.take(4).joinToString(","))
                .apply()
        }
    }
    // endregion

    // region desktop activity
    /**
     * The desktop layer is a secondary HOME on the Dextop display, so the
     * platform keeps it below every freeform window and in place of the DeX /
     * Android launcher.  The host opens the HOME window for the launch; if
     * the platform still refuses, the platform launcher simply stays.
     */
    private fun launchDesktop() {
        val home = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_SECONDARY_HOME)
            .setPackage(service.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!host.launchDesktopHome(home)) host.log("Plasma desktop HOME launch refused display=$displayId")
    }

    /** Returns false when the activity landed on a display the shell does not own. */
    internal fun onDesktopActivityCreated(activity: PlasmaDesktopActivity): Boolean {
        val activityDisplay = activity.display?.displayId
        if (activityDisplay != displayId) {
            host.log("Plasma desktop rejected on display=$activityDisplay (shell display=$displayId)")
            return false
        }
        desktopActivity = activity
        return true
    }

    internal fun onDesktopActivityDestroyed(activity: PlasmaDesktopActivity) {
        if (desktopActivity === activity) desktopActivity = null
    }

    internal fun onDesktopClicked() {
        dismissPopup()
        hideTooltip()
    }

    internal fun onDesktopKey(event: KeyEvent): Boolean = handleKey(event)

    internal fun showDesktopMenu(rawX: Float, rawY: Float) {
        showMenu(
            listOf(
                MenuItem(t("nativePlasmaDesktop"), header = true),
                MenuItem(t("nativePlasmaOverview"), Glyph.OVERVIEW) { toggleOverview() },
                MenuItem(t("nativePlasmaRunCommand"), Glyph.SEARCH) { showKRunner() },
                MenuItem(if (peeking) t("nativePlasmaStopPeeking") else t("nativePlasmaPeekAtDesktop"), Glyph.SHOW_DESKTOP) { togglePeek() },
                MenuItem(t("nativePlasmaConfigureDesktop"), Glyph.WALLPAPER, separatorBefore = true) { openDesktopSettings() },
                MenuItem(t("nativePlasmaLeave"), Glyph.LOGOUT, separatorBefore = true) { leave(LeaveAction.SHOW_DIALOG) },
            ),
            rawX, rawY,
        )
    }
    // endregion

    // region task synchronisation
    private fun schedulePoll(delay: Long) {
        if (!running) return
        if (delay <= 0L) {
            if (pollQueued.compareAndSet(false, true)) runCatching { wmExecutor.execute(::pollNow) }
                .onFailure { pollQueued.set(false) }
            return
        }
        handler.postDelayed({ schedulePoll(0L) }, delay)
    }

    private fun pollNow() {
        pollQueued.set(false)
        if (!running) return
        val tasks = runCatching { bridge.tasks(displayId) }
            .onFailure { Log.w(TAG, "task query failed", it) }
            .getOrNull()
        handler.post {
            if (!running) return@post
            tasks?.let(::sync)
            handler.removeCallbacks(pollRunnable)
            handler.postDelayed(pollRunnable, POLL_INTERVAL_MS)
        }
    }

    private val pollRunnable = Runnable { schedulePoll(0L) }

    private fun isDesktopTask(task: TaskSnapshot) =
        task.packageName == service.packageName &&
            task.component?.className == PlasmaDesktopActivity::class.java.name

    private fun isManageable(task: TaskSnapshot): Boolean =
        task.packageName.isNotEmpty() &&
            !isDesktopTask(task) &&
            task.activityType in setOf(TaskBridge.ACTIVITY_TYPE_UNDEFINED, TaskBridge.ACTIVITY_TYPE_STANDARD) &&
            task.windowingMode != TaskBridge.WINDOWING_MODE_PINNED

    private fun sync(tasks: List<TaskSnapshot>) {
        val manageable = tasks.filter(::isManageable)
        val seen = HashSet<Int>()
        val now = SystemClock.uptimeMillis()
        manageable.forEach { task ->
            seen += task.taskId
            val existing = windows[task.taskId]
            if (existing == null) adopt(task) else {
                existing.task = task
                reconcile(existing, task, now)
            }
        }
        windows.keys.filter { it !in seen }.forEach { id -> windows[id]?.let(::onWindowGone) }
        zOrder = manageable.mapNotNull { windows[it.taskId] }
        val focusedTask = manageable.firstOrNull { it.focused }?.taskId
        val nextActive = focusedTask?.takeIf { id -> windows[id]?.let(::isShown) == true }
            ?: zOrder.firstOrNull { isShown(it) }?.taskId ?: -1
        if (nextActive != activeTaskId) {
            activeTaskId = nextActive
            windows[nextActive]?.lastActivated = now
        }
        updatePanelFloat()
        panel.invalidate()
    }

    /** Mirrors changes made through the platform caption (drag, resize, minimize, maximize). */
    private fun reconcile(window: ManagedWindow, task: TaskSnapshot, now: Long) {
        val recentlyPushed = now - (lastBoundsPush[window.taskId] ?: 0L) < EXTERNAL_BOUNDS_GRACE_MS
        val parked = window.taskId in parkedOffscreen
        if (!recentlyPushed && !parked && !task.bounds.isEmpty) {
            if (task.windowingMode == TaskBridge.WINDOWING_MODE_FULLSCREEN) {
                window.frame.set(0, 0, width, height)
                window.maximized = true
            } else if (task.bounds != window.frame) {
                window.frame.set(task.bounds)
                window.maximized = task.bounds == workArea()
                window.tile = TileZone.NONE
            }
        }
        val hiddenRecently = now - (hiddenAt[window.taskId] ?: 0L) < HIDE_GRACE_MS
        val belongsHere = window.onAllDesktops || window.desktop == currentDesktop
        when {
            // Reordering behind the desktop did not hide it (for example inside
            // an Android 16 "Desk"); park it off-screen instead.
            hiddenRecently && task.visible && !parked && (window.minimized || !belongsHere || window.taskId in peekHidden) &&
                now - (hiddenAt[window.taskId] ?: 0L) > PARK_CHECK_MS -> park(window)
            hiddenRecently -> Unit
            // Activated from outside the shell (notification, intent, recents).
            task.visible && task.zIndex == 0 && task.focused && !parked &&
                (window.minimized || !belongsHere) -> {
                window.minimized = false
                if (!belongsHere) switchDesktop(window.desktop)
            }
            // Minimized or restored with the platform caption buttons.
            belongsHere && !parked && window.taskId !in peekHidden -> window.minimized = !task.visible
        }
        if (window.label.isEmpty()) window.label = appRepository.label(window.packageName)
    }

    private fun adopt(task: TaskSnapshot) {
        val window = ManagedWindow(task).apply {
            label = appRepository.label(task.packageName)
            icon = appRepository.icon(task.packageName)
            desktop = currentDesktop
            lastActivated = SystemClock.uptimeMillis()
            frame.set(task.bounds)
            minimized = !task.visible
        }
        windows[task.taskId] = window
        val pending = pendingLaunches.remove(task.packageName)?.takeIf { SystemClock.uptimeMillis() - it.second < 8_000L }
        if (task.windowingMode == TaskBridge.WINDOWING_MODE_FULLSCREEN && pending != null) {
            // We asked for a window: some apps still start fullscreen, so put
            // them into freeform once, exactly like a caption "window" button.
            val target = pending.first ?: smartPlacement()
            window.frame.set(target)
            lastBoundsPush[task.taskId] = SystemClock.uptimeMillis()
            wmExecutor.execute {
                bridge.setWindowingMode(task, TaskBridge.WINDOWING_MODE_FREEFORM)
                bridge.setBounds(task, target)
            }
        }
        if (task.visible) activeTaskId = task.taskId
        host.log("Plasma window task=${task.taskId} pkg=${task.packageName} mode=${task.windowingMode} bounds=${task.bounds}")
    }

    private fun onWindowGone(window: ManagedWindow) {
        windows.remove(window.taskId)
        hiddenAt.remove(window.taskId)
        lastBoundsPush.remove(window.taskId)
        parkedOffscreen.remove(window.taskId)
        if (activeTaskId == window.taskId) activeTaskId = -1
    }
    // endregion

    // region geometry
    private fun workArea(): Rect = Rect(0, 0, width, height - panel.thickness)

    private fun smartPlacement(): Rect {
        val area = workArea()
        val w = (area.width() * .62f).roundToInt().coerceAtLeast(minOf(area.width(), layer.dp(480)))
        val h = (area.height() * .72f).roundToInt().coerceAtLeast(minOf(area.height(), layer.dp(360)))
        val cascade = windows.values.count { isShown(it) } % 6
        val step = layer.dp(28)
        val left = (area.left + (area.width() - w) / 2 + (cascade - 2) * step).coerceIn(area.left, area.right - w)
        val top = (area.top + (area.height() - h) / 2 + (cascade - 2) * step).coerceIn(area.top, area.bottom - h)
        return Rect(left, top, left + w, top + h)
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

    private fun isShown(window: ManagedWindow) =
        !window.minimized && (window.onAllDesktops || window.desktop == currentDesktop) && !peekHidden.contains(window.taskId)

    /** One privileged resize per operation; the platform animates the change. */
    private fun applyFrame(window: ManagedWindow, target: Rect) {
        window.frame.set(target)
        lastBoundsPush[window.taskId] = SystemClock.uptimeMillis()
        val task = window.task
        val bounds = Rect(target)
        runCatching {
            wmExecutor.execute {
                if (task.windowingMode == TaskBridge.WINDOWING_MODE_FULLSCREEN) {
                    bridge.setWindowingMode(task, TaskBridge.WINDOWING_MODE_FREEFORM)
                }
                bridge.setBounds(task, bounds)
            }
        }
        updatePanelFloat()
    }
    // endregion

    // region window operations
    fun activate(window: ManagedWindow) {
        dismissPopup()
        hideTooltip()
        if (peeking) {
            peeking = false
            peekHidden.mapNotNull { windows[it] }.filter { it !== window }.forEach(::showTask)
            peekHidden = emptyList()
        }
        if (!window.onAllDesktops && window.desktop != currentDesktop) switchDesktop(window.desktop, animate = false)
        window.minimized = false
        activeTaskId = window.taskId
        window.lastActivated = SystemClock.uptimeMillis()
        zOrder = listOf(window) + zOrder.filter { it !== window }
        showTask(window)
        panel.invalidate()
    }

    fun toggleMaximize(window: ManagedWindow) {
        if (window.maximized) {
            val target = window.restoreFrame ?: smartPlacement()
            window.maximized = false
            window.tile = TileZone.NONE
            window.restoreFrame = null
            applyFrame(window, target)
        } else {
            if (window.tile == TileZone.NONE) window.restoreFrame = Rect(window.frame)
            window.maximized = true
            window.tile = TileZone.NONE
            applyFrame(window, workArea())
        }
        activate(window)
    }

    fun tile(window: ManagedWindow, zone: TileZone) {
        if (zone == TileZone.MAXIMIZE) {
            if (!window.maximized) toggleMaximize(window)
            return
        }
        if (!window.maximized && window.tile == TileZone.NONE) window.restoreFrame = Rect(window.frame)
        window.maximized = false
        window.tile = zone
        applyFrame(window, tileRect(zone))
        activate(window)
    }

    fun minimize(window: ManagedWindow) {
        if (window.minimized) return
        window.minimized = true
        hideTask(window)
        if (activeTaskId == window.taskId) {
            activeTaskId = -1
            zOrder.firstOrNull { it !== window && isShown(it) }?.let { next ->
                activeTaskId = next.taskId
                val task = next.task
                wmExecutor.execute { bridge.focus(task) }
            }
        }
        updatePanelFloat()
        panel.invalidate()
    }

    /** Moves a task behind the desktop; a later poll parks it off-screen if that did not hide it. */
    private fun hideTask(window: ManagedWindow) {
        hiddenAt[window.taskId] = SystemClock.uptimeMillis()
        val task = window.task
        runCatching { wmExecutor.execute { bridge.sendToBack(task) } }
    }

    private fun park(window: ManagedWindow) {
        if (!parkedOffscreen.add(window.taskId)) return
        hiddenAt[window.taskId] = SystemClock.uptimeMillis()
        val task = window.task
        val offscreen = Rect(window.frame).apply { offsetTo(width + layer.dp(64), top.coerceAtLeast(0)) }
        lastBoundsPush[window.taskId] = SystemClock.uptimeMillis()
        runCatching { wmExecutor.execute { bridge.setBounds(task, offscreen) } }
    }

    private fun showTask(window: ManagedWindow) {
        hiddenAt.remove(window.taskId)
        val task = window.task
        val restore = if (parkedOffscreen.remove(window.taskId)) Rect(window.frame) else null
        if (restore != null) lastBoundsPush[window.taskId] = SystemClock.uptimeMillis()
        runCatching {
            wmExecutor.execute {
                restore?.let { bridge.setBounds(task, it) }
                bridge.focus(task)
            }
        }
    }

    fun close(window: ManagedWindow) {
        val task = window.task
        runCatching { wmExecutor.execute { bridge.close(task) } }
        schedulePoll(250L)
    }

    fun showWindowMenu(window: ManagedWindow, rawX: Float, rawY: Float) {
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
                items += MenuItem(
                    t("nativePlasmaMoveToDesktopN").format(index + 1),
                    Glyph.DESKTOP, checked = !window.onAllDesktops && window.desktop == index,
                ) { moveWindowToDesktop(window, index) }
            }
        }
        items += MenuItem(t("nativePlasmaClose"), Glyph.CLOSE, separatorBefore = true) { close(window) }
        showMenu(items, rawX, rawY)
    }
    // endregion

    // region virtual desktops
    override fun currentDesktop(): Int = currentDesktop
    override fun desktopCount(): Int = settings.virtualDesktops

    fun switchDesktop(index: Int, animate: Boolean = true) {
        if (index == currentDesktop || index !in 0 until desktopCount()) return
        val direction = if (index > currentDesktop) 1 else -1
        val leaving = windows.values.filter { !it.onAllDesktops && it.desktop == currentDesktop && !it.minimized }
        val arriving = windows.values.filter { !it.onAllDesktops && it.desktop == index && !it.minimized }
            .sortedBy { it.lastActivated }
        val apply = {
            currentDesktop = index
            peeking = false
            peekHidden = emptyList()
            leaving.forEach(::hideTask)
            arriving.forEach(::showTask)
            activeTaskId = arriving.lastOrNull()?.taskId ?: windows.values.firstOrNull { it.onAllDesktops && !it.minimized }?.taskId ?: -1
            updatePanelFloat()
            panel.invalidate()
        }
        if (animate && settings.animationSpeed > 0f) {
            host.captureDisplay { frame ->
                frame?.let {
                    cacheThumbnails(it)
                    showEffect(EffectsView.DesktopSlide(it.bitmap, direction, settings.duration(PlasmaTheme.VERY_LONG_MS - 50)))
                }
                apply()
            }
        } else apply()
    }

    override fun moveWindowToDesktop(window: ManagedWindow, desktop: Int) {
        window.onAllDesktops = false
        if (window.desktop == desktop) return
        window.desktop = desktop
        if (desktop != currentDesktop) {
            hideTask(window)
            if (activeTaskId == window.taskId) activeTaskId = -1
        } else if (!window.minimized) showTask(window)
        panel.invalidate()
    }

    private fun togglePeek() {
        if (peeking) {
            val restore = peekHidden.mapNotNull { windows[it] }.sortedBy { it.lastActivated }
            peeking = false
            peekHidden = emptyList()
            restore.forEach(::showTask)
            activeTaskId = restore.lastOrNull()?.taskId ?: -1
        } else {
            val hide = windows.values.filter { isShown(it) }
            peekHidden = hide.map { it.taskId }
            peeking = true
            hide.forEach(::hideTask)
            activeTaskId = -1
        }
        updatePanelFloat()
        panel.invalidate()
    }
    // endregion

    // region panel host
    private fun layoutPanel() {
        val margin = (panel.floatMargin * panelFloat).roundToInt()
        val panelHeight = panel.thickness + margin * 2
        layer.show(panel, Rect(0, height - panelHeight, width, height), title = "Dextop Plasma panel")
    }

    /** Plasma 6 panels "defloat" when a window touches them. */
    private fun updatePanelFloat(force: Boolean = false) {
        if (!running) return
        val floatZone = height - panel.thickness - panel.floatMargin * 2
        val touching = windows.values.any { isShown(it) && (it.maximized || it.frame.bottom > floatZone) }
        val target = if (settings.floatingPanel && !touching) 1f else 0f
        if (!force && panelFloatTarget == target) return
        panelFloatTarget = target
        panelAnimator?.cancel()
        panelAnimator = android.animation.ValueAnimator.ofFloat(panelFloat, target).apply {
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
        val pinned = pinnedList()
        val shown = windows.values.filter { it.onAllDesktops || it.desktop == currentDesktop }
        val byPackage = shown.groupBy { it.packageName }
        val order = LinkedHashSet<String>()
        order += pinned
        windows.values.sortedBy { it.openedAt }.forEach { if (byPackage.containsKey(it.packageName)) order += it.packageName }
        return order.map { pkg ->
            val group = byPackage[pkg].orEmpty()
            PanelTask(
                packageName = pkg,
                label = appRepository.label(pkg),
                icon = appRepository.icon(pkg),
                windows = group,
                pinned = pkg in pinned,
                active = group.any { it.taskId == activeTaskId && !it.minimized },
            )
        }
    }

    override fun pagerWindows(desktop: Int): List<Pair<Rect, Boolean>> =
        windows.values.filter { !it.minimized && (it.onAllDesktops || it.desktop == desktop) }
            .sortedBy { it.lastActivated }
            .map { it.frame to (it.taskId == activeTaskId) }

    override fun displaySize(): Pair<Int, Int> = width to height
    override fun tray(): TrayState = trayState

    override fun onLauncherClicked(anchor: RectF) {
        if (recentlyDismissed("kickoff")) return
        toggleKickoff()
    }

    override fun onTaskClicked(entry: PanelTask, anchor: RectF) {
        hideTooltip()
        val group = entry.windows
        when {
            group.isEmpty() -> launch(entry.packageName)
            group.size == 1 -> {
                val window = group.first()
                if (window.taskId == activeTaskId && !window.minimized) minimize(window) else activate(window)
            }
            else -> {
                if (recentlyDismissed("group:${entry.packageName}")) return
                val items = group.map { window ->
                    MenuItem(window.title, if (window.minimized) Glyph.MINIMIZE else Glyph.WINDOW, checked = window.taskId == activeTaskId) {
                        activate(window)
                    }
                }
                showMenu(listOf(MenuItem(entry.label, header = true)) + items, anchor.left, anchor.top, kind = "group:${entry.packageName}", above = true)
            }
        }
    }

    override fun onTaskMiddleClicked(entry: PanelTask) = launch(entry.packageName, newInstance = true)

    override fun onTaskContextMenu(entry: PanelTask, anchor: RectF) {
        hideTooltip()
        val items = ArrayList<MenuItem>()
        items += MenuItem(entry.label, header = true)
        entry.windows.forEach { window ->
            items += MenuItem(window.title, Glyph.WINDOW, checked = window.taskId == activeTaskId) { activate(window) }
        }
        items += MenuItem(t("nativePlasmaNewInstance"), Glyph.PLUS, separatorBefore = entry.windows.isNotEmpty()) {
            launch(entry.packageName, newInstance = true)
        }
        items += MenuItem(if (entry.pinned) t("nativePlasmaUnpinFromTaskManager") else t("nativePlasmaPinToTaskManager"), Glyph.PIN) {
            setPinned(entry.packageName, !entry.pinned)
        }
        if (entry.windows.size == 1 && desktopCount() > 1) {
            val window = entry.windows.first()
            for (index in 0 until desktopCount()) {
                items += MenuItem(t("nativePlasmaMoveToDesktopN").format(index + 1), Glyph.DESKTOP,
                    checked = window.desktop == index) { moveWindowToDesktop(window, index) }
            }
        }
        if (entry.windows.isNotEmpty()) {
            items += MenuItem(
                if (entry.windows.size > 1) t("nativePlasmaCloseAll") else t("nativePlasmaClose"),
                Glyph.CLOSE, separatorBefore = true,
            ) { entry.windows.toList().forEach(::close) }
        }
        showMenu(items, anchor.left, anchor.top, above = true)
    }

    private var tooltipRunnable: Runnable? = null

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
                VolumeApplet(
                    displayContext, palette, trayState.volume, trayState.muted,
                    onVolume = { value ->
                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (value * max).roundToInt(), 0)
                        refreshTray()
                    },
                    onToggleMute = {
                        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0)
                        refreshTray()
                        dismissPopup()
                    },
                    onOpenSettings = { dismissPopup(); launchIntent(Intent(Settings.ACTION_SOUND_SETTINGS)) },
                )
            }
            TrayItem.NETWORK -> {
                val (name, detail) = networkDescription()
                InfoApplet(displayContext, palette, t("nativePlasmaNetworks"), Glyph.NETWORK, name, detail,
                    actionLabel = t("nativePlasmaConfigureNetwork")) {
                    dismissPopup(); launchIntent(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                }
            }
            TrayItem.BATTERY -> {
                InfoApplet(displayContext, palette, t("nativePlasmaPowerAndBattery"),
                    if (trayState.charging) Glyph.BATTERY_CHARGING else Glyph.BATTERY,
                    percentText(trayState.battery),
                    if (trayState.charging) t("nativePlasmaCharging") else t("nativePlasmaDischarging"),
                    level = trayState.battery,
                    actionLabel = t("nativePlasmaConfigurePower")) {
                    dismissPopup(); launchIntent(Intent(Intent.ACTION_POWER_USAGE_SUMMARY))
                }
            }
            TrayItem.EXPAND -> TrayOverflowApplet(displayContext, palette, listOf(
                MenuItem(t("nativePlasmaOverview"), Glyph.OVERVIEW) { toggleOverview() },
                MenuItem(t("nativePlasmaRunCommand"), Glyph.SEARCH) { showKRunner() },
                MenuItem(t("nativePlasmaConfigureDesktop"), Glyph.WALLPAPER) { openDesktopSettings() },
                MenuItem(t("nativePlasmaDextopSettings"), Glyph.SETTINGS) { launch(service.packageName) },
                MenuItem(t("nativePlasmaLeave"), Glyph.LOGOUT) { leave(LeaveAction.SHOW_DIALOG) },
            )) { dismissPopup() }
        }
        val popupWidth = layer.dp(if (item == TrayItem.EXPAND) 300 else 360)
        showAnchoredPopup(kind, view, anchor, popupWidth)
    }

    override fun onClockClicked(anchor: RectF) {
        if (recentlyDismissed("clock")) return
        showAnchoredPopup("clock", CalendarApplet(displayContext, palette), anchor, layer.dp(520), layer.dp(310))
    }

    override fun onShowDesktopClicked() = togglePeek()

    override fun onPanelContextMenu(rawX: Float, rawY: Float) {
        showMenu(
            listOf(
                MenuItem(t("nativePlasmaPanel"), header = true),
                MenuItem(if (settings.floatingPanel) t("nativePlasmaDocked") else t("nativePlasmaFloating"), Glyph.DESKTOP) {
                    applySettings(settings.copy(floatingPanel = !settings.floatingPanel))
                },
                MenuItem(t("nativePlasmaConfigureDesktop"), Glyph.SETTINGS) { openDesktopSettings() },
                MenuItem(t("nativePlasmaLeave"), Glyph.LOGOUT, separatorBefore = true) { leave(LeaveAction.SHOW_DIALOG) },
            ),
            rawX, rawY, above = true,
        )
    }

    private fun refreshTray() {
        val audio = service.getSystemService(AudioManager::class.java)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val battery = service.getSystemService(BatteryManager::class.java)
        val capacity = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 } ?: 100
        val connectivity = service.getSystemService(ConnectivityManager::class.java)
        val connected = runCatching {
            connectivity.getNetworkCapabilities(connectivity.activeNetwork)
                ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        }.getOrDefault(true)
        trayState = TrayState(
            volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat(),
            muted = audio.isStreamMute(AudioManager.STREAM_MUSIC),
            battery = capacity / 100f,
            charging = battery.isCharging,
            networkConnected = connected,
        )
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

    // region popups
    private fun recentlyDismissed(kind: String) =
        popupDismissedKind == kind && SystemClock.uptimeMillis() - popupDismissedAt < 300L

    private fun showPopup(kind: String, content: View, bounds: Rect, focusable: Boolean = false, fromBelow: Boolean = true) {
        dismissPopupImmediately()
        hideTooltip()
        val frame = PlasmaPopupFrame(displayContext, palette, onOutside = { dismissPopup() })
        frame.addView(content, FrameLayout.LayoutParams(-1, -1))
        popup = frame
        popupKind = kind
        popupBounds = Rect(bounds)
        layer.show(frame, bounds, focusable = focusable, watchOutside = true, title = "Dextop Plasma $kind")
        frame.alpha = 0f
        frame.translationY = if (fromBelow) layer.dp(12).toFloat() else -layer.dp(12).toFloat()
        frame.animate().alpha(1f).translationY(0f)
            .setDuration(settings.duration(PlasmaTheme.LONG_MS))
            .setInterpolator(PlasmaTheme.outCubic).start()
    }

    private fun showAnchoredPopup(kind: String, content: View, anchor: RectF, popupWidth: Int, fixedHeight: Int? = null) {
        val h = fixedHeight ?: run {
            content.measure(
                View.MeasureSpec.makeMeasureSpec(popupWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.AT_MOST),
            )
            content.measuredHeight
        }
        val panelTop = height - panel.thickness - (panel.floatMargin * panelFloat).roundToInt() * 2
        val left = (anchor.centerX() - popupWidth / 2f).roundToInt().coerceIn(layer.dp(6), width - popupWidth - layer.dp(6))
        val top = (panelTop - h - layer.dp(6)).coerceAtLeast(layer.dp(6))
        showPopup(kind, content, Rect(left, top, left + popupWidth, top + h))
    }

    fun showMenu(items: List<MenuItem>, rawX: Float, rawY: Float, kind: String = "menu", above: Boolean = false) {
        val view = PlasmaMenuView(displayContext, palette, items) { dismissPopup() }
        val (w, h) = view.desiredSize()
        var left = rawX.roundToInt()
        var top = if (above) (height - panel.thickness - (panel.floatMargin * panelFloat).roundToInt() * 2 - h - layer.dp(6)) else rawY.roundToInt()
        if (left + w > width) left = width - w - layer.dp(4)
        if (top + h > height) top = (rawY - h).roundToInt()
        top = top.coerceAtLeast(layer.dp(4))
        left = left.coerceAtLeast(layer.dp(4))
        showPopup(kind, view, Rect(left, top, left + w, top + h), fromBelow = above)
    }

    override fun showMenu(items: List<MenuItem>, rawX: Float, rawY: Float) = showMenu(items, rawX, rawY, "menu", false)

    override fun dismissPopup() {
        val frame = popup ?: return
        popupDismissedKind = popupKind
        popupDismissedAt = SystemClock.uptimeMillis()
        popup = null
        popupKind = null
        popupBounds = null
        kickoff = null
        krunner = null
        frame.animate().alpha(0f).translationY(layer.dp(8).toFloat())
            .setDuration(settings.duration(PlasmaTheme.SHORT_MS + 40))
            .withEndAction { layer.hide(frame) }
            .start()
    }

    private fun dismissPopupImmediately() {
        popup?.let(layer::hide)
        popup = null
        popupKind = null
        popupBounds = null
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
        val w = minOf(layer.dp(680), (width * .9f).roundToInt())
        val h = minOf(layer.dp(560), (height * .8f).roundToInt())
        val anchor = panel.launcherRect()
        val left = ((anchor?.left ?: 0f).roundToInt()).coerceIn(layer.dp(6), width - w - layer.dp(6))
        val panelTop = height - panel.thickness - (panel.floatMargin * panelFloat).roundToInt() * 2
        val top = (panelTop - h - layer.dp(6)).coerceAtLeast(layer.dp(6))
        showPopup("kickoff", view, Rect(left, top, left + w, top + h), focusable = true)
        view.post { view.focusSearch() }
    }

    fun showKRunner() {
        if (popupKind == "krunner") {
            dismissPopup()
            return
        }
        val view = KRunnerView(displayContext, this)
        krunner = view
        val w = minOf(layer.dp(620), (width * .9f).roundToInt())
        val left = (width - w) / 2
        showPopup("krunner", view, Rect(left, 0, left + w, view.desiredHeight()), focusable = true, fromBelow = false)
        view.onResultsChanged = {
            val bounds = Rect(left, 0, left + w, minOf(view.desiredHeight(), height / 2))
            popup?.let { layer.show(it, bounds, focusable = true, watchOutside = true) }
            popupBounds = bounds
        }
        view.post { view.search.requestFocus() }
    }

    override fun openDesktopSettings() {
        val view = DesktopSettingsApplet(displayContext, palette, settings) { next -> applySettings(next) }
        val w = layer.dp(460)
        view.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.AT_MOST),
        )
        val h = view.measuredHeight
        val left = (width - w) / 2
        val top = ((height - panel.thickness - h) / 2).coerceAtLeast(layer.dp(8))
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
        if (paletteChanged && popupKind == "settings") {
            // Rebuild the dialog so it follows the new colour scheme immediately.
            handler.post { openDesktopSettings() }
        }
    }

    private fun showTooltip(entry: PanelTask, anchor: RectF) {
        if (popup != null) return
        val view = tooltip ?: TooltipView(displayContext, palette).also { tooltip = it }
        view.bind(entry)
        val (w, h) = view.desiredSize()
        val panelTop = height - panel.thickness - (panel.floatMargin * panelFloat).roundToInt() * 2
        val left = (anchor.centerX() - w / 2f).roundToInt().coerceIn(layer.dp(4), width - w - layer.dp(4))
        val top = panelTop - h - layer.dp(6)
        val wasVisible = layer.isAttached(view)
        layer.show(view, Rect(left, top, left + w, top + h), touchable = false, title = "Dextop Plasma tooltip")
        if (!wasVisible) {
            view.alpha = 0f
            view.animate().alpha(1f).setDuration(settings.duration(PlasmaTheme.SHORT_MS + 50)).start()
        }
        view.invalidate()
    }

    private fun hideTooltip() {
        tooltipRunnable?.let(handler::removeCallbacks)
        tooltip?.let(layer::hide)
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

    private fun csv(key: String): List<String> =
        prefs.getString(key, "").orEmpty().split(',').filter { it.isNotBlank() }

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
        val intent = service.packageManager.getLaunchIntentForPackage(packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        if (newInstance) intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
        // Android would only bring an existing task forward; do that through
        // the shell so minimized windows and other desktops are handled too.
        if (!newInstance) {
            windows.values.filter { it.packageName == packageName }.maxByOrNull { it.lastActivated }?.let {
                activate(it)
                return
            }
        }
        val frame = smartPlacement()
        pendingLaunches[packageName] = Rect(frame) to SystemClock.uptimeMillis()
        panel.bounceLaunch(packageName)
        launchIntent(intent, frame)
        schedulePoll(200L)
    }

    /** Launches an intent onto the Dextop display as a freeform window (like YoukiDex). */
    fun launchIntent(intent: Intent, bounds: Rect? = null) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(displayId)
        runCatching {
            ActivityOptions::class.java.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                .invoke(options, TaskBridge.WINDOWING_MODE_FREEFORM)
        }
        if (bounds != null) options.launchBounds = bounds
        runCatching { service.startActivity(intent, options.toBundle()) }
            .recoverCatching {
                // Some builds reject an explicit windowing mode; retry plainly.
                val plain = ActivityOptions.makeBasic().setLaunchDisplayId(displayId)
                if (bounds != null) plain.launchBounds = bounds
                service.startActivity(intent, plain.toBundle())
            }
            .onFailure { host.log("Plasma launch failed ${intent.component ?: intent.`package`}: ${it.message}") }
    }

    override fun openAppInfo(packageName: String) {
        dismissPopup()
        launchIntent(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
    }

    override fun copyToClipboard(text: String) {
        runCatching {
            service.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Dextop", text))
        }
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
        leaveScreen?.let(layer::hide)
        val screen = LeaveScreen(displayContext, palette, LeaveAction.SHUTDOWN) { choice ->
            leaveScreen?.let(layer::hide)
            leaveScreen = null
            choice?.let(::leave)
        }
        leaveScreen = screen
        layer.show(screen, Rect(0, 0, width, height), focusable = true, title = "Dextop Plasma leave")
    }
    // endregion

    // region overview & switcher
    override fun overviewWindows(): List<ManagedWindow> =
        zOrder.filter { isShown(it) }.reversed().ifEmpty { windows.values.filter { isShown(it) } }

    override fun thumbnail(window: ManagedWindow): Bitmap? = window.thumbnail
    override fun desktopWindows(desktop: Int): List<ManagedWindow> =
        windows.values.filter { !it.minimized && (it.onAllDesktops || it.desktop == desktop) }.sortedBy { it.lastActivated }

    fun toggleOverview() {
        overview?.let {
            exitOverview()
            return
        }
        dismissPopupImmediately()
        hideTooltip()
        host.captureDisplay { frame ->
            if (!running || overview != null) return@captureDisplay
            frame?.let(::cacheThumbnails)
            val view = OverviewView(displayContext, this, frame?.bitmap)
            overview = view
            layer.show(view, Rect(0, 0, width, height), focusable = true, title = "Dextop Plasma overview")
        }
    }

    override fun activateFromOverview(window: ManagedWindow) {
        exitOverview { activate(window) }
    }

    override fun closeFromOverview(window: ManagedWindow) {
        val task = window.task
        wmExecutor.execute { bridge.close(task) }
        windows.remove(window.taskId)
        zOrder = zOrder.filter { it !== window }
        overview?.remove(window)
    }

    override fun switchDesktopFromOverview(index: Int) = switchDesktop(index, animate = false)

    override fun exitOverview() = exitOverview(null)

    private fun exitOverview(after: (() -> Unit)?) {
        val view = overview ?: return
        view.exit {
            layer.hide(view)
            if (overview === view) overview = null
            after?.invoke()
        }
    }

    override fun searchHost(): LauncherHost = this

    private fun cacheThumbnails(frame: DesktopFrame) {
        val shown = zOrder.filter { isShown(it) }
        val covered = ArrayList<Rect>()
        popupBounds?.let(covered::add)
        shown.forEach { window ->
            if (covered.none { Rect.intersects(it, window.frame) }) {
                frame.crop(window.frame)?.let { window.thumbnail = it }
            }
            covered += Rect(window.frame)
        }
    }

    private var switcherRequested = false

    private fun switcherCandidates() = windows.values.filter { it.onAllDesktops || it.desktop == currentDesktop }
        .sortedByDescending { it.lastActivated }

    private fun showSwitcher(reverse: Boolean) {
        val candidates = switcherCandidates()
        if (candidates.isEmpty()) return
        switcherRequested = true
        host.captureDisplay { frame ->
            if (!running || !altDown || !switcherRequested) return@captureDisplay
            frame?.let(::cacheThumbnails)
            val view = TaskSwitcherView(displayContext, palette, candidates, candidates.associate { it.taskId to it.thumbnail })
            if (reverse) view.index = candidates.lastIndex
            switcher = view
            val (w, h) = view.desiredSize((width * .9f).roundToInt())
            layer.show(view, Rect((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2), touchable = false, title = "Dextop Plasma task switcher")
            view.alpha = 0f
            view.animate().alpha(1f).setDuration(settings.duration(PlasmaTheme.SHORT_MS)).start()
        }
    }

    private fun commitSwitcher() {
        switcherRequested = false
        val view = switcher ?: run {
            // A quick Alt+Tab released before the switcher appeared flips
            // to the previously used window, like KWin.
            switcherCandidates().getOrNull(1)?.let(::activate)
            return
        }
        switcher = null
        view.selectedWindow()?.let(::activate)
        layer.hide(view)
    }
    // endregion

    // region effects
    private fun showEffectsLayer() {
        if (!layer.isAttached(effects)) {
            layer.show(effects, Rect(0, 0, width, height), touchable = false, title = "Dextop Plasma effects")
        }
    }

    private fun showEffect(effect: EffectsView.Effect) {
        if (!running) return
        // Effects start above everything that was stacked before them.
        if (layer.isAttached(effects)) layer.raise(effects) else showEffectsLayer()
        effects.add(effect)
    }

    private fun installHotCorner() {
        val corner = object : View(displayContext) {}
        corner.setOnHoverListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_HOVER_ENTER) triggerHotCorner()
            true
        }
        corner.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) triggerHotCorner()
            true
        }
        hotCorner = corner
        if (settings.hotCorner) layer.show(corner, Rect(0, 0, layer.dp(2), layer.dp(2)), title = "Dextop Plasma hot corner")
    }

    private fun triggerHotCorner() {
        val now = SystemClock.uptimeMillis()
        if (!settings.hotCorner || now - lastHotCornerAt < 900L ) return
        lastHotCornerAt = now
        toggleOverview()
    }
    // endregion

    // region keyboard
    /**
     * KDE Plasma default global shortcuts.  Returns true when the shell
     * consumed the key so it is not delivered to the focused application.
     */
    fun handleKey(event: KeyEvent): Boolean {
        if (!running) return false
        val down = event.action == KeyEvent.ACTION_DOWN
        val code = event.keyCode
        if (!down && swallowedKeys.remove(code)) return true
        when (code) {
            KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_META_RIGHT -> {
                if (down) {
                    if (event.repeatCount == 0) { metaDown = true; metaCombo = false }
                } else {
                    val openLauncher = metaDown && !metaCombo
                    metaDown = false
                    if (openLauncher) toggleKickoff()
                }
                return true
            }
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> {
                altDown = down
                if (!down && (switcher != null || switcherRequested)) commitSwitcher()
                return false
            }
            KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT -> { ctrlDown = down; return false }
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> { shiftDown = down; return false }
        }
        if (!down) return false
        val meta = metaDown || event.isMetaPressed
        val alt = altDown || event.isAltPressed
        val ctrl = ctrlDown || event.isCtrlPressed
        val shift = shiftDown || event.isShiftPressed
        if (meta) metaCombo = true
        val active = windows[activeTaskId]
        val handled: Boolean = when {
            alt && code == KeyEvent.KEYCODE_TAB -> {
                val view = switcher
                if (view == null) {
                    if (!switcherRequested) showSwitcher(shift)
                } else {
                    val count = windows.values.count { it.onAllDesktops || it.desktop == currentDesktop }.coerceAtLeast(1)
                    view.index = (view.index + if (shift) count - 1 else 1) % count
                }
                true
            }
            (switcher != null || switcherRequested) && code == KeyEvent.KEYCODE_ESCAPE -> {
                switcher?.let(layer::hide); switcher = null; switcherRequested = false; true
            }
            alt && code == KeyEvent.KEYCODE_F4 -> { active?.let(::close); true }
            alt && code == KeyEvent.KEYCODE_F3 -> {
                active?.let { showWindowMenu(it, it.frame.left.toFloat(), (it.frame.top + layer.dp(30)).toFloat()) }; true
            }
            alt && code == KeyEvent.KEYCODE_F1 -> { toggleKickoff(); true }
            alt && (code == KeyEvent.KEYCODE_SPACE || code == KeyEvent.KEYCODE_F2) -> { showKRunner(); true }
            meta && code == KeyEvent.KEYCODE_W -> { toggleOverview(); true }
            meta && code == KeyEvent.KEYCODE_G -> { toggleOverview(); true }
            meta && code == KeyEvent.KEYCODE_D -> { togglePeek(); true }
            meta && code == KeyEvent.KEYCODE_PAGE_UP -> { active?.let(::toggleMaximize); true }
            meta && code == KeyEvent.KEYCODE_PAGE_DOWN -> { active?.let(::minimize); true }
            meta && ctrl && code == KeyEvent.KEYCODE_DPAD_LEFT -> { onDesktopScroll(-1); true }
            meta && ctrl && code == KeyEvent.KEYCODE_DPAD_RIGHT -> { onDesktopScroll(1); true }
            meta && code == KeyEvent.KEYCODE_DPAD_LEFT -> { active?.let { quickTile(it, TileZone.LEFT) }; true }
            meta && code == KeyEvent.KEYCODE_DPAD_RIGHT -> { active?.let { quickTile(it, TileZone.RIGHT) }; true }
            meta && code == KeyEvent.KEYCODE_DPAD_UP -> { active?.let { quickTile(it, TileZone.TOP) }; true }
            meta && code == KeyEvent.KEYCODE_DPAD_DOWN -> { active?.let { quickTile(it, TileZone.BOTTOM) }; true }
            meta && code in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9 -> {
                panelEntries().getOrNull(code - KeyEvent.KEYCODE_1)?.let { entry ->
                    onTaskClicked(entry, panel.taskIconRect(entry.packageName) ?: RectF())
                }
                true
            }
            ctrl && alt && code == KeyEvent.KEYCODE_DPAD_LEFT -> { onDesktopScroll(-1); true }
            ctrl && alt && code == KeyEvent.KEYCODE_DPAD_RIGHT -> { onDesktopScroll(1); true }
            ctrl && alt && code == KeyEvent.KEYCODE_DEL -> { leave(LeaveAction.SHOW_DIALOG); true }
            ctrl && code in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F4 -> {
                switchDesktop(code - KeyEvent.KEYCODE_F1); true
            }
            code == KeyEvent.KEYCODE_ESCAPE && overview != null -> { exitOverview(); true }
            code == KeyEvent.KEYCODE_ESCAPE && popup != null && popupKind != "kickoff" && popupKind != "krunner" -> {
                dismissPopup(); true
            }
            else -> {
                // Typing while Kickoff is open searches even if focus stayed on the app.
                val view = kickoff
                val character = event.unicodeChar
                if (view != null && !view.search.isFocused && character > 0 && !ctrl && !alt && !meta) {
                    view.appendSearch(String(Character.toChars(character)))
                    true
                } else false
            }
        }
        if (handled) swallowedKeys += code
        return handled
    }

    /** KWin quick tiling: repeating the shortcut combines halves into quarters. */
    private fun quickTile(window: ManagedWindow, direction: TileZone) {
        val next = when (window.tile to direction) {
            TileZone.LEFT to TileZone.TOP, TileZone.TOP to TileZone.LEFT -> TileZone.TOP_LEFT
            TileZone.LEFT to TileZone.BOTTOM, TileZone.BOTTOM to TileZone.LEFT -> TileZone.BOTTOM_LEFT
            TileZone.RIGHT to TileZone.TOP, TileZone.TOP to TileZone.RIGHT -> TileZone.TOP_RIGHT
            TileZone.RIGHT to TileZone.BOTTOM, TileZone.BOTTOM to TileZone.RIGHT -> TileZone.BOTTOM_RIGHT
            TileZone.TOP_LEFT to TileZone.BOTTOM, TileZone.BOTTOM_LEFT to TileZone.TOP -> TileZone.LEFT
            TileZone.TOP_RIGHT to TileZone.BOTTOM, TileZone.BOTTOM_RIGHT to TileZone.TOP -> TileZone.RIGHT
            TileZone.TOP_LEFT to TileZone.RIGHT, TileZone.TOP_RIGHT to TileZone.LEFT -> TileZone.TOP
            TileZone.BOTTOM_LEFT to TileZone.RIGHT, TileZone.BOTTOM_RIGHT to TileZone.LEFT -> TileZone.BOTTOM
            else -> direction
        }
        if (window.tile == next) {
            // Pressing the same direction again restores, like KWin.
            val restore = window.restoreFrame ?: smartPlacement()
            window.tile = TileZone.NONE
            window.restoreFrame = null
            applyFrame(window, restore)
            activate(window)
        } else tile(window, next)
    }
    // endregion

    /** Plasma tooltip for task-manager entries: titles plus live thumbnails. */
    private class TooltipView(context: Context, private val palette: PlasmaPalette) : View(context) {
        private var entry: PanelTask? = null
        private val density = resources.displayMetrics.density
        private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)
        private val text = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { typeface = PlasmaTheme.sans }

        fun bind(value: PanelTask) { entry = value }

        fun desiredSize(): Pair<Int, Int> {
            val count = entry?.windows?.size ?: 0
            return if (count == 0) {
                text.textSize = 13 * density
                ((text.measureText(entry?.label.orEmpty()) + 56 * density).toInt()) to (40 * density).toInt()
            } else {
                ((count.coerceAtMost(4) * 200 + 12) * density).toInt() to (166 * density).toInt()
            }
        }

        override fun onDraw(canvas: android.graphics.Canvas) {
            val value = entry ?: return
            val box = RectF(.5f, .5f, width - .5f, height - .5f)
            paint.style = android.graphics.Paint.Style.FILL
            paint.color = palette.popup
            canvas.drawRoundRect(box, 8 * density, 8 * density, paint)
            paint.style = android.graphics.Paint.Style.STROKE
            paint.color = palette.frameOutline
            canvas.drawRoundRect(box, 8 * density, 8 * density, paint)
            paint.style = android.graphics.Paint.Style.FILL
            text.textSize = 13 * density
            text.color = palette.text
            if (value.windows.isEmpty()) {
                canvas.drawIcon(value.icon, RectF(10 * density, 10 * density, 30 * density, 30 * density))
                canvas.drawText(value.label, 40 * density, height / 2f - (text.descent() + text.ascent()) / 2f, text)
                return
            }
            value.windows.take(4).forEachIndexed { index, window ->
                val left = (6 + index * 200) * density
                val cell = RectF(left + 6 * density, 8 * density, left + 194 * density, height - 8 * density)
                canvas.drawIcon(window.icon, RectF(cell.left, cell.top, cell.left + 16 * density, cell.top + 16 * density))
                val title = TextUtilsCompat.ellipsize(window.title, text, cell.width() - 22 * density)
                canvas.drawText(title, cell.left + 22 * density, cell.top + 13 * density, text)
                val thumbArea = RectF(cell.left, cell.top + 24 * density, cell.right, cell.bottom)
                val frame = window.frame
                val scale = minOf(thumbArea.width() / frame.width().coerceAtLeast(1), thumbArea.height() / frame.height().coerceAtLeast(1))
                val thumb = RectF(
                    thumbArea.centerX() - frame.width() * scale / 2, thumbArea.centerY() - frame.height() * scale / 2,
                    thumbArea.centerX() + frame.width() * scale / 2, thumbArea.centerY() + frame.height() * scale / 2,
                )
                val bitmap = window.thumbnail
                if (bitmap != null && !bitmap.isRecycled) canvas.drawBitmap(bitmap, null, thumb, paint)
                else {
                    paint.color = palette.windowBackground
                    canvas.drawRect(thumb, paint)
                    val s = minOf(thumb.width(), thumb.height()) * .4f
                    canvas.drawIcon(window.icon, RectF(thumb.centerX() - s / 2, thumb.centerY() - s / 2, thumb.centerX() + s / 2, thumb.centerY() + s / 2))
                }
                if (window.minimized) {
                    paint.color = PlasmaTheme.withAlpha(palette.popup, 0x88)
                    canvas.drawRect(thumb, paint)
                }
            }
        }
    }
}
