@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)

package moe.n4tsu.dextop.plasma

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.ui.platform.LocalView
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

/** What Book's chrome asks of the compositor beyond the panel and launcher hosts. */
internal interface BookChrome {
    /** Opens (or, when it is open, closes) one of Book's panels next to [anchor]. */
    fun openBookPanel(kind: String, anchor: RectF)
    /** The first screen, which has the bars, the taskbar and the desktop. */
    fun primaryArea(): Rect
    /** A shown window reaches into the taskbar's strip (an auto-hiding taskbar then hides). */
    fun dockCovered(): Boolean
    fun notifications(): List<DextopNotification>
    /** Book's own windows (Settings, Widgets) take the keyboard while one is open. */
    fun setBookFocus(wanted: Boolean)
    /** Lets Dextop bind app widgets (through the privileged shell). */
    fun grantWidgetBinding()
    /** An installed Linux container, or null. */
    fun installedContainer(): String?
    /** The installed Linux container whose ~/Desktop the desktop shows, or null (the feature is off). */
    fun desktopContainer(): String?
    /** Runs [script] in that container; its output, or null when it failed or there is none. */
    fun linuxDesktop(script: String, onDone: (String?) -> Unit)
    /** Opens a file or folder of ~/Desktop with the container's own application for it. */
    fun openLinuxDesktopItem(name: String)
    fun log(message: String)
}

/** The Alt+Tab switcher, Plasma's or Book's. */
internal interface TaskSwitcher {
    var index: Int
    fun selectedWindow(): ManagedWindow?
    fun desiredSize(maxWidth: Int): Pair<Int, Int>
    val view: View
}

/** Something being dragged onto the desktop or the taskbar. */
internal sealed class BookDragItem {
    data class App(val app: PlasmaApp) : BookDragItem()
    data class Shortcut(val app: PlasmaApp, val from: GridSpot) : BookDragItem()
    data class Widget(val appWidgetId: Int, val from: GridSpot, val snapshot: ImageBitmap?) : BookDragItem()
    data class NewWidget(val info: AppWidgetProviderInfo, val preview: Drawable?) : BookDragItem()
}

/** Gives a composable Book's drag gesture for one item (the widget picker's previews). */
internal fun interface BookDragSource {
    fun modifier(item: BookDragItem, onTap: () -> Unit): Modifier
}

/** One of Book's own windows: Settings or the widget picker. */
private class BookWindow(val kind: String, frame: Rect) {
    var frame by mutableStateOf(frame)
    var maximized by mutableStateOf(false)
    var minimized by mutableStateOf(false)
    /** Its dock icon on screen: where it goes when minimized and comes back from. */
    var dockIcon: RectF? = null
}

private data class DragState(
    val item: BookDragItem,
    /** Pointer on screen. */
    val x: Float,
    val y: Float,
    /** Pointer position within the dragged thing (its top left is pointer minus this). */
    val grab: Offset,
)

/**
 * The Compose half of Dextop Book: the status bar, the taskbar, the desktop grid with shortcuts
 * and Android widgets, Book's own windows, and every menu and indicator. The shell calls
 * [refresh] whenever tasks or status change.
 */
internal class BookUi(
    private val context: Context,
    private val panel: PanelHost,
    private val launcher: LauncherHost,
    private val host: BookHost,
    private val chrome: BookChrome,
    displayId: Int,
) {
    private val owner = BookOwner()
    private val density = context.resources.displayMetrics.density
    private val handler = Handler(Looper.getMainLooper())
    private var tasks by mutableStateOf(emptyList<PanelTask>())
    private var tray by mutableStateOf(panel.tray())
    /** Bumped on every refresh: the desktop re-reads the app list (Linux apps arrive late). */
    private var appsTick by mutableIntStateOf(0)
    /** The files and folders of the Linux container's ~/Desktop (name to is-folder). */
    private val initialDesktopCache = BookStore.cachedLinuxDesktop(context)
        ?.takeIf { BookStore.flag(context, BookStore.LINUX_DESKTOP_FOLDER, false) }
    private var linuxFilesContainer = initialDesktopCache?.first
    private var linuxFiles by mutableStateOf(initialDesktopCache?.second.orEmpty())
    /** The ~/Desktop item being renamed, and its new name as typed. */
    private var renaming by mutableStateOf<String?>(null)
    private var linuxFolderOn = false
    private var notifications by mutableStateOf(emptyList<DextopNotification>())
    private var shown by mutableStateOf(true)
    private var scheme by mutableStateOf(BookTheme.scheme(context, panel.palette.dark))
    var launcherOpen by mutableStateOf(false)
    /** The panel open now (quick, notifications, clock, media), to light its status-bar button. */
    var openPanel by mutableStateOf<String?>(null)
    private var dnd by mutableStateOf(false)
    private var alwaysShowTaskbar by mutableStateOf(BookStore.flag(context, BookStore.ALWAYS_SHOW_TASKBAR, true))
    private var desktopIcons by mutableStateOf(BookStore.flag(context, BookStore.DESKTOP_ICONS, true))
    private var liveActivity by mutableStateOf(BookStore.flag(context, BookStore.LIVE_ACTIVITY, true))
    private var notificationIcons by mutableStateOf(BookStore.flag(context, BookStore.NOTIFICATION_ICONS, true))
    private var shortcuts by mutableStateOf(BookStore.shortcuts(context))
    private var widgets by mutableStateOf(BookStore.widgets(context))
    private var dockRevealed by mutableStateOf(false)
    private var dockCovered by mutableStateOf(false)
    private var area by mutableStateOf(Rect(0, 0, 1, 1))
    private var drag by mutableStateOf<DragState?>(null)
    private var resizing by mutableStateOf<Int?>(null)
    /** The cells a widget being resized covers now (saved when a handle is released). */
    private var resizeSpot by mutableStateOf<GridSpot?>(null)
    /** Where a dropped item lands from (its top left on screen), to animate into its cell. */
    private val landing = HashMap<Any, IntOffset>()
    private val iconRects = HashMap<String, RectF>()
    private var launcherRect: RectF? = null
    private var dockRect = RectF()
    private var launcherBounds = RectF()
    private var launcherHeightDp by mutableIntStateOf(640)
    private val bookWindows = mutableStateListOf<BookWindow>()
    private var tooltip by mutableStateOf<Pair<String, RectF>?>(null)
    private var volume by mutableStateOf<Pair<Float, Boolean>?>(null)
    val panels = BookPanels(context, host)
    val appWidgets = BookAppWidgets(context, displayId, chrome::log)
    private val settingsContent = BookSettingsContent(context, host, appWidgets, { removeWidget(it) }, { chrome.installedContainer() != null })
    private val picker = BookWidgetPicker(appWidgets, add = { addWidget(it, null) }, drag = { item, onTap ->
        Modifier.bookDrag(item = { item }, onTap = onTap, onMenu = null, holdFirst = false, initialPass = false,
            grab = { local -> Offset(local.x, local.y) })
    })

    val topInset: Int get() = (TOP_BAR.value * density).toInt()
    /** The taskbar's height, kept free for windows unless it hides on its own. */
    val bottomInset: Int get() = if (alwaysShowTaskbar) dockHeight else 0
    private val dockHeight: Int get() = (DOCK.value * density).toInt()

    private var statusBar: View? = null
    private var dockHolder: FrameLayout? = null
    private var desktop: View? = null
    private var windowLayer: View? = null
    private var revealStrip: View? = null
    private var overlay: ComposeView? = null
    private var root: FrameLayout? = null

    private val storeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> handler.post { reloadStore() } }

    fun install(root: FrameLayout, desktopIndex: Int, windowsLayerView: View) {
        this.root = root
        root.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                place(chrome.primaryArea())
            }
        }
        BookContext.app = context.applicationContext
        root.setViewTreeLifecycleOwner(owner)
        root.setViewTreeSavedStateRegistryOwner(owner)
        root.setViewTreeViewModelStoreOwner(owner)
        chrome.grantWidgetBinding()
        appWidgets.start()
        // Shortcuts and widgets sit on the wallpaper, under the windows.
        desktop = compose { Desktop() }.also { root.addView(it, desktopIndex, FrameLayout.LayoutParams(-1, -1)) }
        // Book's own windows sit just above the application windows.
        windowLayer = compose { BookWindows() }.also {
            root.addView(it, root.indexOfChild(windowsLayerView) + 1, FrameLayout.LayoutParams(-1, -1))
        }
        statusBar = compose { StatusBar() }.also {
            root.addView(it, FrameLayout.LayoutParams(-1, topInset, android.view.Gravity.TOP or android.view.Gravity.START))
        }
        // The taskbar spans its screen; the holder is that screen's width.
        dockHolder = FrameLayout(context).also { holder ->
            holder.clipChildren = false
            holder.addView(compose { Dock() }, FrameLayout.LayoutParams(-1, -2, android.view.Gravity.BOTTOM))
            root.addView(holder, FrameLayout.LayoutParams(-1, -2, android.view.Gravity.BOTTOM or android.view.Gravity.START))
        }
        // A hidden taskbar comes back when the pointer touches the bottom edge.
        revealStrip = View(context).apply {
            setOnHoverListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_HOVER_ENTER) revealDock()
                false
            }
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) revealDock()
                false
            }
        }.also { root.addView(it, FrameLayout.LayoutParams(-1, (3 * density).toInt(), android.view.Gravity.BOTTOM or android.view.Gravity.START)) }
        BookStore.listen(context, storeListener)
        handler.post(poll)
        handler.post(linuxDesktopPoll)
        refresh()
    }

    /** On an extended desktop: the bars and the taskbar belong to [output] (the first screen). */
    fun place(output: Rect) {
        area = Rect(output)
        listOfNotNull(statusBar, dockHolder, revealStrip).forEach { view ->
            (view.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                params.width = output.width()
                params.leftMargin = output.left
                if (view === statusBar) {
                    params.topMargin = output.top
                } else {
                    params.bottomMargin = ((root?.height ?: output.bottom) - output.bottom).coerceAtLeast(0)
                }
                view.layoutParams = params
            }
        }
    }

    fun destroy() {
        handler.removeCallbacksAndMessages(null)
        BookStore.unlisten(context, storeListener)
        appWidgets.stop()
        overlay?.let { (it.parent as? FrameLayout)?.removeView(it) }
        owner.destroy()
    }

    fun refresh() {
        tasks = panel.panelEntries()
        appsTick++
        tray = panel.tray()
        notifications = chrome.notifications()
        area = chrome.primaryArea()
        dockCovered = chrome.dockCovered()
        BookLive.battery = tray.battery
        BookLive.charging = tray.charging
    }

    fun recolor() {
        scheme = BookTheme.scheme(context, panel.palette.dark)
    }

    /** Hidden while a Linux app is full screen. */
    fun setVisible(value: Boolean) {
        shown = value
    }

    fun iconRect(packageName: String): RectF? = iconRects[packageName]?.let(::RectF)
    fun launcherRect(): RectF? = launcherRect?.let(::RectF)

    private fun reloadStore() {
        val wasAlways = alwaysShowTaskbar
        alwaysShowTaskbar = BookStore.flag(context, BookStore.ALWAYS_SHOW_TASKBAR, true)
        desktopIcons = BookStore.flag(context, BookStore.DESKTOP_ICONS, true)
        liveActivity = BookStore.flag(context, BookStore.LIVE_ACTIVITY, true)
        notificationIcons = BookStore.flag(context, BookStore.NOTIFICATION_ICONS, true)
        shortcuts = BookStore.shortcuts(context)
        widgets = BookStore.widgets(context)
        settingsContent.changed()
        if (wasAlways != alwaysShowTaskbar) onInsetsChanged?.invoke()
        // The experimental desktop folder was switched on or off.
        val folder = BookStore.flag(context, BookStore.LINUX_DESKTOP_FOLDER, false)
        if (folder != linuxFolderOn) { linuxFolderOn = folder; refreshLinuxDesktop() }
    }

    /** The compositor re-lays its maximized windows when the taskbar starts or stops hiding. */
    var onInsetsChanged: (() -> Unit)? = null

    /** Media and Do Not Disturb change without a broadcast Dextop receives; they are read every few seconds. */
    private val poll = object : Runnable {
        override fun run() {
            host.background {
                val media = runCatching { host.controls.nowPlaying() }.getOrNull()
                val art = if (media == null) null else runCatching { mediaArt(media.packageName) }.getOrNull()
                val silent = runCatching { host.controls.doNotDisturb() }.getOrDefault(false)
                handler.post {
                    if (art?.sameAs(BookLive.mediaArt) != true) BookLive.mediaArt = art
                    if (media?.packageName != BookLive.media?.packageName) {
                        BookLive.mediaIcon = media?.packageName?.let { runCatching { context.packageManager.getApplicationIcon(it) }.getOrNull() }
                    }
                    BookLive.media = media
                    dnd = silent
                }
            }
            handler.postDelayed(this, 4_000L)
        }
    }

    /**
     * The artwork of [packageName]'s media session (album art, else its art or icon). Dextop's
     * notification listener lets it read the active sessions.
     */
    private fun mediaArt(packageName: String): Bitmap? {
        val sessions = context.getSystemService(android.media.session.MediaSessionManager::class.java)
            .getActiveSessions(DextopNotificationListener.component(context))
        val controller = sessions.firstOrNull { it.packageName == packageName }
            ?: sessions.firstOrNull { it.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING } ?: return null
        val metadata = controller.metadata ?: return null
        return metadata.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART)
            ?: metadata.getBitmap(android.media.MediaMetadata.METADATA_KEY_DISPLAY_ICON)
    }

    private fun compose(content: @Composable () -> Unit) = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setContent { MaterialExpressiveTheme(colorScheme = scheme, motionScheme = MotionScheme.expressive()) { content() } }
    }

    /** The topmost layer (drag ghost, tooltip, volume): added while something is on it. */
    private fun showOverlay() {
        val parent = root ?: return
        val view = overlay ?: compose { Overlay() }.also { overlay = it; parent.addView(it, FrameLayout.LayoutParams(-1, -1)) }
        view.bringToFront()
    }

    private fun hideOverlaySoon() {
        handler.postDelayed({
            if (drag == null && tooltip == null && volume == null) {
                overlay?.let { (it.parent as? FrameLayout)?.removeView(it) }
                overlay = null
            }
        }, 400L)
    }

    private val hideDock = Runnable { dockRevealed = false }

    private fun revealDock() {
        if (alwaysShowTaskbar || dockRevealed) return
        dockRevealed = true
    }

    // region panels, menus and indicators

    fun launcherView(maxHeightPx: Int): View {
        launcherHeightDp = (maxHeightPx / density).toInt().coerceAtLeast(320)
        return compose { Launcher() }
    }

    /** The content of one of Book's panels, or null for an unknown [kind]. */
    fun panelView(kind: String): View? = when (kind) {
        "quick" -> { panels.loadQuickSettings(); compose { Shadowed { panels.QuickSettings() } } }
        "internet" -> { panels.loadQuickSettings("internet"); compose { Shadowed { panels.QuickSettings() } } }
        "notifications" -> compose { Shadowed { panels.Notifications(notifications) } }
        "clock" -> { panels.resetCalendar(); compose { Shadowed { panels.CalendarPanel() } } }
        "media" -> compose { Shadowed { panels.MediaPanel() } }
        "rename" -> compose { Shadowed { RenamePanel() } }
        "taskbar" -> compose {
            Shadowed {
                panels.TaskbarMenu(alwaysShowTaskbar) { value ->
                    BookStore.setFlag(context, BookStore.ALWAYS_SHOW_TASKBAR, value)
                    host.closePanel()
                }
            }
        }
        else -> null
    }.also { if (it != null) openPanel = kind }

    /** A context menu in Book's style. */
    fun menuView(items: List<MenuItem>, dismiss: () -> Unit): View = compose { Shadowed { BookMenu(items, dismiss) } }

    /** The snap layouts offered from a window's maximize button. */
    fun snapView(maximized: Boolean, tiled: Boolean, onMaximize: () -> Unit, onRestore: () -> Unit,
                 onLeft: () -> Unit, onRight: () -> Unit): View =
        compose { Shadowed { SnapMenu(maximized, tiled, onMaximize, onRestore, onLeft, onRight) } }

    @Composable
    private fun Shadowed(content: @Composable () -> Unit) = Box(Modifier.padding(PANEL_SHADOW)) { content() }

    val panelShadowPx: Int get() = (PANEL_SHADOW.value * density).toInt()

    fun showTooltip(text: String, anchor: RectF) {
        tooltip = text to anchor
        showOverlay()
    }

    fun hideTooltip() {
        if (tooltip == null) return
        tooltip = null
        hideOverlaySoon()
    }

    private val hideVolume = Runnable { volume = null; hideOverlaySoon() }

    fun showVolume(level: Float, muted: Boolean) {
        volume = level to muted
        showOverlay()
        handler.removeCallbacks(hideVolume)
        handler.postDelayed(hideVolume, 1_600L)
    }

    fun switcher(windows: List<ManagedWindow>, thumbnails: List<Bitmap?>): TaskSwitcher = BookSwitcher(windows, thumbnails)

    @Composable
    private fun BookMenu(items: List<MenuItem>, dismiss: () -> Unit) {
        val colors = MaterialTheme.colorScheme
        Surface(Modifier.widthIn(min = 220.dp, max = 340.dp).blockPointer(), shape = RoundedCornerShape(16.dp), color = colors.surfaceContainer,
            shadowElevation = 0.dp) {
            Column(Modifier.padding(vertical = 8.dp)) {
                items.forEach { item ->
                    if (item.separatorBefore) HorizontalDivider(Modifier.padding(vertical = 6.dp, horizontal = 12.dp), color = colors.outlineVariant)
                    if (item.header) {
                        Text(item.text, style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        return@forEach
                    }
                    var hovered by remember { mutableStateOf(false) }
                    Row(
                        Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 6.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (hovered && item.enabled) colors.onSurface.copy(alpha = .08f) else Color.Transparent)
                            .pointerButtons(onHover = { hovered = it })
                            .clickable(enabled = item.enabled) { dismiss(); item.action() }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val tint = if (item.enabled) colors.onSurface else colors.outline
                        if (item.glyph != null) GlyphIconCompose(item.glyph, colors.onSurfaceVariant, 1f, 20.dp) else Spacer(Modifier.size(20.dp))
                        Text(item.text, style = MaterialTheme.typography.bodyLarge, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f))
                        if (item.checked == true) Sym("check", colors.primary, 20.dp)
                    }
                }
            }
        }
    }

    @Composable
    private fun SnapMenu(maximized: Boolean, tiled: Boolean, onMaximize: () -> Unit, onRestore: () -> Unit,
                         onLeft: () -> Unit, onRight: () -> Unit) {
        val colors = MaterialTheme.colorScheme
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(Modifier.blockPointer(), shape = RoundedCornerShape(24.dp), color = colors.surfaceContainer) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SnapCard(if (maximized || tiled) tr("元に戻す", "Restore") else tr("最大化", "Maximize")) { hover ->
                        Box(Modifier.size(width = 108.dp, height = 64.dp).clip(RoundedCornerShape(10.dp))
                            .background(colors.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                            Box(Modifier.size(width = if (maximized || tiled) 72.dp else 100.dp, height = if (maximized || tiled) 40.dp else 56.dp)
                                .clip(RoundedCornerShape(6.dp)).background(if (hover) colors.primary else colors.outline)
                                .clickable { if (maximized || tiled) onRestore() else onMaximize() })
                        }
                    }
                    SnapCard(tr("分割", "Resize")) { _ ->
                        Row(Modifier.size(width = 108.dp, height = 64.dp).clip(RoundedCornerShape(10.dp)).background(colors.surfaceContainerHighest)
                            .padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(onLeft, onRight).forEach { action ->
                                var hovered by remember { mutableStateOf(false) }
                                Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(6.dp))
                                    .background(if (hovered) colors.primary else colors.outline)
                                    .pointerButtons(onHover = { hovered = it }).clickable(onClick = action))
                            }
                        }
                    }
                }
            }
            Surface(Modifier.blockPointer(), shape = CircleShape, color = colors.surfaceContainer) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    BookIconButton("crop_square", onMaximize, selected = maximized, tint = colors.onSurface)
                    BookIconButton("splitscreen_left", onLeft, selected = tiled, tint = colors.onSurface)
                    BookIconButton("filter_none", onRestore, selected = !maximized && !tiled, tint = colors.onSurface)
                }
            }
        }
    }

    @Composable
    private fun SnapCard(label: String, preview: @Composable (Boolean) -> Unit) {
        var hovered by remember { mutableStateOf(false) }
        val colors = MaterialTheme.colorScheme
        Column(Modifier.clip(RoundedCornerShape(16.dp)).background(if (hovered) colors.surfaceContainerHigh else Color.Transparent)
            .pointerButtons(onHover = { hovered = it }).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            preview(hovered)
            Text(label, style = MaterialTheme.typography.labelLarge, color = colors.onSurface, modifier = Modifier.padding(top = 8.dp))
        }
    }

    /** Tooltip, volume and the dragged item, over everything. */
    @Composable
    private fun Overlay() {
        val px = LocalDensity.current
        val colors = MaterialTheme.colorScheme
        Box(Modifier.fillMaxSize()) {
            tooltip?.let { (text, anchor) ->
                var width by remember(text) { mutableIntStateOf(0) }
                Text(text, style = MaterialTheme.typography.labelLarge, color = colors.inverseOnSurface,
                    modifier = Modifier.offset { IntOffset((anchor.centerX() - width / 2f).roundToInt(), (anchor.top - with(px) { 40.dp.toPx() }).roundToInt()) }
                        .onGloballyPositioned { width = it.size.width }
                        .clip(RoundedCornerShape(8.dp)).background(colors.inverseSurface).padding(horizontal = 10.dp, vertical = 6.dp))
            }
            AnimatedVisibility(volume != null, Modifier.align(Alignment.BottomCenter).padding(bottom = DOCK + 24.dp),
                enter = fadeIn() + scaleIn(initialScale = .9f), exit = fadeOut() + scaleOut(targetScale = .9f)) {
                val (level, muted) = volume ?: (0f to false)
                Surface(shape = CircleShape, color = colors.surfaceContainerHigh) {
                    Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Sym(if (muted || level <= 0f) "volume_off" else "volume_up", colors.onSurface, 22.dp, filled = true)
                        LinearProgressIndicator(progress = { if (muted) 0f else level }, modifier = Modifier.width(220.dp).height(8.dp),
                            color = colors.primary, trackColor = colors.surfaceContainerHighest, drawStopIndicator = {})
                        Text(if (muted) "0" else (level * 100).roundToInt().toString(), style = MaterialTheme.typography.labelLarge,
                            color = colors.onSurface, modifier = Modifier.width(28.dp), textAlign = TextAlign.End)
                    }
                }
            }
            drag?.let { DragLayer(it) }
        }
    }
    // endregion

    // region status bar

    @Composable
    private fun StatusBar() {
        if (!shown) return
        val now = rememberNow()
        // Over the wallpaper: light text with a soft shadow.
        val style = MaterialTheme.typography.labelLarge.copy(color = Color.White,
            shadow = Shadow(Color(0x80000000), Offset(0f, 1f), 4f))
        var clock = RectF()
        var bell = RectF()
        var status = RectF()
        var live = RectF()
        val colors = MaterialTheme.colorScheme
        Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.clip(CircleShape)
                    .background(if (openPanel == "clock") Color.White.copy(alpha = .18f) else Color.Transparent)
                    .onScreen { clock = it }
                    .clickable { chrome.openBookPanel("clock", clock) }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(DateFormat.getTimeFormat(context).format(now), style = style)
                Text(DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMMd"), now).toString(), style = style)
            }
            val apps = notifications.distinctBy { it.owner }.take(3)
            if (notificationIcons && apps.isNotEmpty()) {
                var anchor = RectF()
                Row(Modifier.clip(CircleShape).onScreen { anchor = it }.clickable { chrome.openBookPanel("notifications", anchor) }
                    .padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    apps.forEach { item ->
                        if (item.icon != null) DrawableIcon(item.icon, 16.dp) else Sym("notifications", Color.White, 16.dp)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            val media = BookLive.media
            AnimatedVisibility(liveActivity && media != null, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                Row(
                    Modifier.padding(end = 6.dp).height(28.dp).widthIn(max = 240.dp).clip(CircleShape)
                        .background(Color(0xE6101418))
                        .onScreen { live = it }
                        .clickable { chrome.openBookPanel("media", live) }
                        .padding(start = 4.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.size(20.dp).clip(CircleShape).background(colors.primary), contentAlignment = Alignment.Center) {
                        val art = BookLive.mediaArt
                        val icon = BookLive.mediaIcon
                        when {
                            art != null -> Image(remember(art) { art.asImageBitmap() }, null, Modifier.size(20.dp),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                            icon != null -> DrawableIcon(icon, 20.dp)
                            else -> Sym(if (media?.playing == true) "music_note" else "pause", colors.onPrimary, 14.dp)
                        }
                    }
                    Text(listOfNotNull(media?.title, media?.artist).joinToString(" · "), style = MaterialTheme.typography.labelLarge,
                        color = Color.White, maxLines = 1, modifier = Modifier.basicMarquee())
                }
            }
            Box(
                Modifier.size(32.dp).clip(CircleShape)
                    .background(if (openPanel == "notifications") Color.White.copy(alpha = .18f) else Color.Transparent)
                    .onScreen { bell = it }
                    .clickable { chrome.openBookPanel("notifications", bell) },
                contentAlignment = Alignment.Center,
            ) { Sym(if (dnd) "notifications_off" else "notifications", Color.White, 18.dp, filled = notifications.isNotEmpty()) }
            Row(
                Modifier.padding(start = 4.dp).clip(CircleShape)
                    .background(if (openPanel == "quick" || openPanel == "internet") colors.primaryContainer else Color.Transparent)
                    .onScreen { status = it }
                    .clickable { chrome.openBookPanel("quick", status) }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val tint = if (openPanel == "quick" || openPanel == "internet") colors.onPrimaryContainer else Color.White
                if (dnd) Sym("do_not_disturb_on", tint, 16.dp, filled = true)
                if (tray.cellularVisible) GlyphIconCompose(Glyph.CELLULAR, tint, tray.cellularLevel)
                GlyphIconCompose(tray.networkGlyph, tint, tray.networkLevel)
                GlyphIconCompose(if (tray.charging) Glyph.BATTERY_CHARGING else Glyph.BATTERY, tint, tray.battery)
                if (tray.batteryPercentVisible) Text(percentText(tray.battery),
                    style = style.copy(color = tint, shadow = if (tint == Color.White) style.shadow else null))
            }
        }
    }

    @Composable
    private fun GlyphIconCompose(glyph: Glyph, tint: Color, level: Float, size: Dp = 16.dp) {
        val drawable = remember(glyph) { GlyphDrawable(glyph, 0, 1.6f, true) }
        androidx.compose.foundation.Canvas(Modifier.size(size)) {
            drawable.color = tint.toArgb()
            drawable.level01 = level
            drawIntoCanvas { canvas ->
                drawable.setBounds(0, 0, this.size.width.toInt(), this.size.height.toInt())
                drawable.draw(canvas.nativeCanvas)
            }
        }
    }
    // endregion

    // region taskbar

    @Composable
    private fun Dock() {
        if (!shown) return
        val colors = MaterialTheme.colorScheme
        val hidden = !alwaysShowTaskbar && dockCovered && !dockRevealed && !launcherOpen && openPanel != "taskbar" && drag == null
        val offset by animateDpAsState(if (hidden) DOCK + 8.dp else 0.dp, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "dock")
        var bar = RectF()
        val pinTarget = drag?.let { it.item is BookDragItem.App || it.item is BookDragItem.Shortcut } == true &&
            dockRect.contains(drag!!.x, drag!!.y)
        Box(
            Modifier.fillMaxWidth().height(DOCK).offset { IntOffset(0, offset.roundToPx()) }
                .background(if (pinTarget) colors.primaryContainer else colors.surfaceContainer.copy(alpha = .94f))
                .onScreen { bar = it; dockRect = it }
                .pointerButtons(onHover = { inside ->
                    handler.removeCallbacks(hideDock)
                    if (!inside && dockRevealed) handler.postDelayed(hideDock, 700L)
                })
                .pointerInput(Unit) {
                    // The bar's own menu: a right click or a long press on the bar itself (the
                    // icons take theirs first and consume the press).
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = true)
                        val x = bar.left + down.position.x
                        if (currentEvent.buttons.isSecondaryPressed) {
                            chrome.openBookPanel("taskbar", RectF(x - 1, bar.top, x + 1, bar.top))
                            return@awaitEachGesture
                        }
                        val released = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            while (awaitPointerEvent().changes.any { it.pressed }) Unit
                            true
                        }
                        if (released == null) chrome.openBookPanel("taskbar", RectF(x - 1, bar.top, x + 1, bar.top))
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                LauncherButton()
                VerticalDivider(Modifier.height(24.dp).padding(horizontal = 8.dp), color = colors.outlineVariant)
                val pinned = tasks.filter { it.pinned }
                val others = tasks.filter { !it.pinned }
                pinned.forEach { entry -> DockItem(entry) }
                if (pinned.isNotEmpty() && others.isNotEmpty()) {
                    VerticalDivider(Modifier.height(24.dp).padding(horizontal = 8.dp), color = colors.outlineVariant)
                }
                others.forEach { entry -> DockItem(entry) }
                if (bookWindows.isNotEmpty()) {
                    if (tasks.isNotEmpty()) VerticalDivider(Modifier.height(24.dp).padding(horizontal = 8.dp), color = colors.outlineVariant)
                    DockBookWindows()
                }
            }
        }
    }

    @Composable
    private fun LauncherButton() {
        val colors = MaterialTheme.colorScheme
        val shape = if (launcherOpen) MaterialShapes.Cookie9Sided.toShape() else CircleShape
        val scale by animateFloatAsState(if (launcherOpen) 1.08f else 1f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "launcher")
        var anchor = RectF()
        var hovered by remember { mutableStateOf(false) }
        Box(
            Modifier.size(44.dp).scale(scale).clip(shape)
                .background(if (launcherOpen) colors.primary else if (hovered) colors.onSurface.copy(alpha = .1f) else Color.Transparent)
                .onScreen { anchor = it; launcherRect = it }
                .pointerButtons(onHover = { hovered = it })
                .clickable { panel.onLauncherClicked(anchor) },
            contentAlignment = Alignment.Center,
        ) {
            val tint = if (launcherOpen) colors.onPrimary else colors.onSurface
            Box(Modifier.size(26.dp)) {
                Sym("apps", tint, 22.dp)
                Sym("search", tint, 14.dp, modifier = Modifier.align(Alignment.BottomEnd).background(
                    if (launcherOpen) colors.primary else colors.surfaceContainer, CircleShape))
            }
        }
    }

    @Composable
    private fun DockItem(entry: PanelTask) {
        val colors = MaterialTheme.colorScheme
        val running = entry.windows.isNotEmpty()
        var anchor = RectF()
        var hovered by remember { mutableStateOf(false) }
        val indicator by animateDpAsState(when {
            entry.active -> 16.dp
            running -> 6.dp
            else -> 0.dp
        }, MaterialTheme.motionScheme.fastSpatialSpec(), label = "indicator")
        val lift by animateFloatAsState(if (hovered) 1.08f else 1f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "lift")
        Column(Modifier.padding(horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (hovered || entry.active) colors.onSurface.copy(alpha = if (entry.active) .12f else .08f) else Color.Transparent)
                    .onScreen { anchor = it; iconRects[entry.packageName] = it }
                    .pointerButtons(
                        onSecondary = { hideTooltip(); panel.onTaskContextMenu(entry, anchor) },
                        onTertiary = { panel.onTaskMiddleClicked(entry) },
                        onHover = { inside ->
                            hovered = inside
                            if (inside) showTooltip(entry.label, anchor) else hideTooltip()
                        },
                    )
                    .combinedClickable(
                        onLongClick = { hideTooltip(); panel.onTaskContextMenu(entry, anchor) },
                        onClick = { hideTooltip(); panel.onTaskClicked(entry, anchor) },
                    ),
                contentAlignment = Alignment.Center,
            ) { DrawableIcon(entry.icon, 34.dp, if (running || entry.pinned) 255 else 160, Modifier.scale(lift)) }
            Box(Modifier.padding(top = 2.dp).height(3.dp).width(indicator).clip(CircleShape)
                .background(if (entry.active) colors.primary else colors.onSurfaceVariant))
        }
    }
    // endregion

    // region launcher

    @Composable
    private fun Launcher() {
        val colors = MaterialTheme.colorScheme
        var query by remember { mutableStateOf("") }
        var expanded by remember { mutableStateOf(BookStore.flag(context, BookStore.LAUNCHER_EXPANDED, false)) }
        val gridState = rememberLazyGridState()
        val apps = remember(query) {
            if (query.isBlank()) launcher.apps().sortedBy { it.label.lowercase() } else PlasmaSearch.apps(launcher, query)
        }
        fun open(app: PlasmaApp) {
            launcher.dismissPopup()
            launcher.launch(app.packageName)
        }
        val d = drag
        val away = d != null && !launcherBounds.contains(d.x, d.y)
        val fade by animateFloatAsState(if (away) 0f else 1f, label = "launcher-fade")
        // The popup spans the screen; the launcher sits centred in it, expanded or not.
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        val availableWidth = (maxWidth - PANEL_SHADOW * 2).coerceAtLeast(0.dp)
        val targetWidth = if (expanded) minOf(820.dp, availableWidth)
            else minOf(470.dp, availableWidth * .82f)
        val launcherWidth by animateDpAsState(targetWidth,
            MaterialTheme.motionScheme.defaultSpatialSpec(), label = "launcher-width")
        Box(Modifier.padding(PANEL_SHADOW).alpha(fade)) {
            Surface(
                onClick = {},
                modifier = Modifier.width(launcherWidth).height(launcherHeightDp.dp - PANEL_SHADOW * 2)
                    .onScreen { launcherBounds = it },
                shape = RoundedCornerShape(28.dp),
                color = colors.surfaceContainer,
            ) {
                Column(Modifier.fillMaxSize().padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = CircleShape,
                            placeholder = { Text(tr("スマートフォンのアプリを検索", "Search apps from your phone")) },
                            leadingIcon = { Sym("search", colors.onSurfaceVariant, 22.dp) },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { apps.firstOrNull()?.let(::open) }),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = colors.surfaceContainerHighest,
                                unfocusedContainerColor = colors.surfaceContainerHighest,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                        )
                        BookIconButton(if (expanded) "expand_more" else "expand_less", {
                            expanded = !expanded
                            BookStore.setFlag(context, BookStore.LAUNCHER_EXPANDED, expanded)
                        }, size = 48.dp,
                            container = colors.primaryContainer, tint = colors.onPrimaryContainer)
                    }
                    if (apps.isEmpty()) {
                        Text(tr("一致するアプリはありません", "No matching apps"), color = colors.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(24.dp))
                    }
                    // Recreate gesture handlers when the layout changes, retaining the scroll
                    // position. A press from the old cell layout must not survive the toggle.
                    key(expanded) {
                        LazyVerticalGrid(GridCells.Adaptive(100.dp), Modifier.fillMaxWidth().weight(1f).padding(top = 12.dp), state = gridState) {
                            items(apps, key = { it.packageName }) { app -> AppCell(app) { open(app) } }
                        }
                    }
                }
            }
        }
        }
    }

    private fun appMenu(app: PlasmaApp, x: Float, y: Float) {
        val pinned = launcher.isPinned(app.packageName)
        val onDesktop = shortcuts.any { it.packageName == app.packageName }
        launcher.showMenu(listOf(
            MenuItem(app.label, header = true),
            MenuItem(tr("開く", "Open"), Glyph.OPEN_EXTERNAL) { launcher.launch(app.packageName) },
            MenuItem(if (pinned) tr("タスクバーから固定を外す", "Unpin from taskbar") else tr("タスクバーに固定", "Pin to taskbar"), Glyph.PIN) {
                launcher.setPinned(app.packageName, !pinned)
                refresh()
            },
            MenuItem(if (onDesktop) tr("デスクトップから削除", "Remove from desktop") else tr("デスクトップに追加", "Add to desktop"), Glyph.DESKTOP) {
                if (onDesktop) BookStore.removeShortcut(context, app.packageName) else addShortcut(app.packageName, null)
            },
            MenuItem(tr("アプリ情報", "App info"), Glyph.INFO, separatorBefore = true) { launcher.openAppInfo(app.packageName) },
        ), x, y)
    }

    @Composable
    private fun AppCell(app: PlasmaApp, open: () -> Unit) {
        var hovered by remember { mutableStateOf(false) }
        val colors = MaterialTheme.colorScheme
        val dragged = (drag?.item as? BookDragItem.App)?.app?.packageName == app.packageName
        Column(
            Modifier.padding(4.dp).clip(RoundedCornerShape(20.dp))
                .background(if (hovered) colors.onSurface.copy(alpha = .08f) else Color.Transparent)
                .alpha(if (dragged) .35f else 1f)
                .pointerButtons(onHover = { hovered = it })
                .bookDrag(item = { BookDragItem.App(app) }, onTap = open, onMenu = { x, y -> appMenu(app, x, y) },
                    holdFirst = false, initialPass = false, grab = { appGrab() })
                .padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DrawableIcon(app.icon, 52.dp)
            Text(app.label, style = MaterialTheme.typography.labelLarge, color = colors.onSurface, maxLines = 2,
                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        }
    }
    // endregion

    // region desktop grid

    private val cell get() = CELL.value * density
    private fun gridLeft() = area.left + GRID_MARGIN.value * density
    private fun gridTop() = area.top + topInset + 8 * density
    private fun gridColumns() = ((area.width() - 2 * GRID_MARGIN.value * density) / cell).toInt().coerceAtLeast(1)
    private fun gridRows() = ((area.bottom - dockHeight - 8 * density - gridTop()) / cell).toInt().coerceAtLeast(1)
    private fun spotOrigin(spot: GridSpot) = IntOffset((gridLeft() + spot.column * cell).roundToInt(), (gridTop() + spot.row * cell).roundToInt())

    /** The grid cell whose area holds a screen point (clamped to the grid). */
    private fun cellAt(x: Float, y: Float) = GridSpot(
        ((x - gridLeft()) / cell).toInt().coerceIn(0, gridColumns() - 1),
        ((y - gridTop()) / cell).toInt().coerceIn(0, gridRows() - 1),
    )

    /** The pointer holds an app icon at its centre, a little above the label. */
    private fun appGrab() = Offset(cell / 2f, cell * .4f)

    private fun spanOf(info: AppWidgetProviderInfo): Pair<Int, Int> {
        val cellDp = CELL.value
        val w = if (info.targetCellWidth > 0) info.targetCellWidth else ceil((info.minWidth / density + 16) / cellDp).toInt()
        val h = if (info.targetCellHeight > 0) info.targetCellHeight else ceil((info.minHeight / density + 16) / cellDp).toInt()
        return w.coerceIn(1, gridColumns()) to h.coerceIn(1, gridRows())
    }

    private fun minSpanOf(info: AppWidgetProviderInfo): Pair<Int, Int> {
        val cellDp = CELL.value
        val w = ceil(((if (info.minResizeWidth > 0) info.minResizeWidth else info.minWidth) / density + 16) / cellDp).toInt()
        val h = ceil(((if (info.minResizeHeight > 0) info.minResizeHeight else info.minHeight) / density + 16) / cellDp).toInt()
        return w.coerceAtLeast(1) to h.coerceAtLeast(1)
    }

    private fun addShortcut(packageName: String, at: GridSpot?) {
        if (!desktopIcons) BookStore.setFlag(context, BookStore.DESKTOP_ICONS, true)
        val wanted = at ?: GridSpot(gridColumns() - 1, 0)
        val spot = BookStore.freeSpot(wanted, BookStore.taken(context, packageName), gridColumns(), gridRows()) ?: return
        BookStore.placeShortcut(context, packageName, spot)
    }

    /** Binds [info] and puts it at [at] (or the first free spot); opens its configuration when it needs one. */
    fun addWidget(info: AppWidgetProviderInfo, at: GridSpot?) {
        val (columns, rows) = spanOf(info)
        val wanted = at?.copy(columns = columns, rows = rows) ?: GridSpot(0, 0, columns, rows)
        val spot = BookStore.freeSpot(wanted, BookStore.taken(context), gridColumns(), gridRows()) ?: run {
            chrome.log("no room for widget ${info.provider}")
            return
        }
        val id = appWidgets.bind(info) ?: return
        BookStore.placeWidget(context, id, spot)
        if (appWidgets.needsConfiguration(info)) appWidgets.configure(id)
    }

    private fun removeWidget(id: Int) {
        BookStore.removeWidget(context, id)
        appWidgets.delete(id)
    }

    @Composable
    private fun Desktop() {
        if (!shown) return
        // Read again with the task list: Linux applications arrive after Android's.
        val colors = MaterialTheme.colorScheme
        val apps = remember(shortcuts, appsTick, linuxFiles) {
            launcher.apps().associateBy { it.packageName } + linuxFiles.associate { (name, folder) ->
                val key = LINUX_DESKTOP_PREFIX + name
                key to PlasmaApp(key, name, SymbolDrawable(if (folder) "folder" else "draft",
                    colors.onPrimaryContainer.toArgb(), colors.primaryContainer.toArgb()), 0)
            }
        }
        Box(Modifier.fillMaxSize()) {
            // While a widget is being resized, a click anywhere else on the desktop ends it.
            if (resizing != null) Box(Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures { resizing = null; resizeSpot = null }
            })
            widgets.forEach { widget -> androidx.compose.runtime.key("w${widget.appWidgetId}") { DesktopWidgetItem(widget) } }
            if (desktopIcons) shortcuts.forEach { shortcut ->
                val app = apps[shortcut.packageName] ?: return@forEach
                androidx.compose.runtime.key("s${shortcut.packageName}") { DesktopIcon(app, shortcut) }
            }
        }
    }

    /** An item's position, springing to [target] (and from where it was dropped). */
    @Composable
    private fun animatedOrigin(key: Any, target: IntOffset): IntOffset {
        val anim = remember(key) { Animatable(landing.remove(key) ?: target, IntOffset.VectorConverter) }
        LaunchedEffect(target) {
            landing.remove(key)?.let { anim.snapTo(it) }
            anim.animateTo(target, spring(dampingRatio = .82f, stiffness = Spring.StiffnessMediumLow))
        }
        return anim.value
    }

    @Composable
    private fun DesktopIcon(app: PlasmaApp, shortcut: DesktopShortcut) {
        var hovered by remember { mutableStateOf(false) }
        val origin = animatedOrigin(shortcut.packageName, spotOrigin(shortcut.spot))
        val dragged = (drag?.item as? BookDragItem.Shortcut)?.app?.packageName == app.packageName
        Column(
            Modifier.offset { origin }.size(CELL)
                .alpha(if (dragged) 0f else 1f)
                .padding(4.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (hovered) Color.White.copy(alpha = .14f) else Color.Transparent)
                .pointerButtons(onHover = { hovered = it })
                .bookDrag(item = { BookDragItem.Shortcut(app, shortcut.spot) }, onTap = { launcher.launch(app.packageName) },
                    onMenu = { x, y -> shortcutMenu(app, x, y) }, holdFirst = false, initialPass = false, grab = { appGrab() })
                .padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DrawableIcon(app.icon, 52.dp)
            WallpaperText(app.label, Modifier.padding(top = 4.dp))
        }
    }

    private fun shortcutMenu(app: PlasmaApp, x: Float, y: Float) {
        if (app.packageName.startsWith(LINUX_DESKTOP_PREFIX)) {
            val name = app.packageName.removePrefix(LINUX_DESKTOP_PREFIX)
            launcher.showMenu(listOf(
                MenuItem(name, header = true),
                MenuItem(tr("開く", "Open"), Glyph.OPEN_EXTERNAL) { chrome.openLinuxDesktopItem(name) },
                MenuItem(tr("名前を変更", "Rename"), Glyph.SETTINGS) { rename(name, RectF(x, y, x + 1, y + 1)) },
                MenuItem(tr("ゴミ箱に移動", "Move to Trash"), Glyph.CLOSE, separatorBefore = true) {
                    chrome.linuxDesktop(desktopScript("""P="${'$'}D/${'$'}(n ${b64(name)})"
                        if command -v gio >/dev/null 2>&1; then gio trash -- "${'$'}P"
                        else
                            T="${'$'}{HOME:-/root}/.local/share/Trash"; mkdir -p "${'$'}T/files" "${'$'}T/info" || exit 1
                            K="${'$'}(date +%s)-${'$'}${'$'}-${'$'}(n ${b64(name)})"
                            [ ! -e "${'$'}T/files/${'$'}K" ] && [ ! -L "${'$'}T/files/${'$'}K" ] || exit 1
                            mv -T -n -- "${'$'}P" "${'$'}T/files/${'$'}K" || exit 1
                            printf '[Trash Info]\nPath=%s\nDeletionDate=%s\n' "${'$'}P" "${'$'}(date +%Y-%m-%dT%H:%M:%S)" > "${'$'}T/info/${'$'}K.trashinfo"
                        fi""")) { refreshLinuxDesktop() }
                },
            ), x, y)
            return
        }
        val pinned = launcher.isPinned(app.packageName)
        launcher.showMenu(listOf(
            MenuItem(app.label, header = true),
            MenuItem(tr("開く", "Open"), Glyph.OPEN_EXTERNAL) { launcher.launch(app.packageName) },
            MenuItem(if (pinned) tr("タスクバーから固定を外す", "Unpin from taskbar") else tr("タスクバーに固定", "Pin to taskbar"), Glyph.PIN) {
                launcher.setPinned(app.packageName, !pinned)
                refresh()
            },
            MenuItem(tr("デスクトップから削除", "Remove from desktop"), Glyph.CLOSE) { BookStore.removeShortcut(context, app.packageName) },
            MenuItem(tr("アプリ情報", "App info"), Glyph.INFO, separatorBefore = true) { launcher.openAppInfo(app.packageName) },
        ), x, y)
    }

    @Composable
    private fun DesktopWidgetItem(widget: DesktopWidget) {
        val view = remember(widget.appWidgetId) { appWidgets.view(widget.appWidgetId) }
        val spot = (if (resizing == widget.appWidgetId) resizeSpot else null) ?: widget.spot
        val origin = animatedOrigin(widget.appWidgetId, spotOrigin(spot))
        // The size springs from cell to cell while a handle is dragged.
        val widthDp by animateDpAsState(CELL * spot.columns, spring(dampingRatio = .85f, stiffness = Spring.StiffnessMedium), label = "w")
        val heightDp by animateDpAsState(CELL * spot.rows, spring(dampingRatio = .85f, stiffness = Spring.StiffnessMedium), label = "h")
        LaunchedEffect(spot.columns, spot.rows) {
            appWidgets.resize(widget.appWidgetId, CELL.value * spot.columns - 16, CELL.value * spot.rows - 16)
        }
        val dragged = (drag?.item as? BookDragItem.Widget)?.appWidgetId == widget.appWidgetId
        val colors = MaterialTheme.colorScheme
        Box(
            Modifier.offset { origin }.size(widthDp, heightDp).alpha(if (dragged) 0f else 1f)
                .bookDrag(item = { BookDragItem.Widget(widget.appWidgetId, widget.spot, view?.let(::snapshot)) }, onTap = null,
                    onMenu = { x, y -> widgetMenu(widget, x, y) }, holdFirst = true, initialPass = true,
                    grab = { local -> local })
                .padding(8.dp),
        ) {
            if (view != null) {
                AndroidView(factory = { (view.parent as? android.view.ViewGroup)?.removeView(view); view },
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)))
            } else {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(colors.surfaceContainer), contentAlignment = Alignment.Center) {
                    Text(tr("ウィジェットを読み込めません", "Can't load widget"), color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
        }
        if (resizing == widget.appWidgetId) ResizeFrame(widget, origin, widthDp, heightDp)
    }

    /**
     * Resizing a widget: an outline with a handle in the middle of each edge. Dragging a handle
     * moves that edge cell by cell (within the widget's minimum, the grid and free cells).
     */
    @Composable
    private fun ResizeFrame(widget: DesktopWidget, origin: IntOffset, width: Dp, height: Dp) {
        val colors = MaterialTheme.colorScheme
        val info = remember(widget.appWidgetId) { appWidgets.info(widget.appWidgetId) }
        val (minW, minH) = remember(info) { info?.let(::minSpanOf) ?: (1 to 1) }
        Box(Modifier.offset { origin }.size(width, height)) {
            Box(Modifier.fillMaxSize().padding(4.dp).border(2.dp, colors.primary, RoundedCornerShape(26.dp)))
            @Composable
            fun handle(alignment: Alignment, horizontal: Boolean, dl: Int, dt: Int, dr: Int, db: Int) {
                var moved by remember { mutableStateOf(Offset.Zero) }
                var start by remember { mutableStateOf<GridSpot?>(null) }
                Box(
                    Modifier.align(alignment).size(40.dp).pointerInput(widget.appWidgetId) {
                        detectDragGestures(
                            onDragStart = { start = resizeSpot ?: widget.spot; moved = Offset.Zero },
                            onDragEnd = {
                                resizeSpot?.let { BookStore.placeWidget(context, widget.appWidgetId, it) }
                                start = null
                            },
                        ) { change, amount ->
                            change.consume()
                            moved += amount
                            val from = start ?: return@detectDragGestures
                            val dx = (moved.x / cell).roundToInt()
                            val dy = (moved.y / cell).roundToInt()
                            var c = from.column; var r = from.row; var w = from.columns; var h = from.rows
                            if (dl != 0) { val d = dx.coerceIn(-from.column, from.columns - minW); c += d; w -= d }
                            if (dr != 0) w = (from.columns + dx).coerceIn(minW, gridColumns() - from.column)
                            if (dt != 0) { val d = dy.coerceIn(-from.row, from.rows - minH); r += d; h -= d }
                            if (db != 0) h = (from.rows + dy).coerceIn(minH, gridRows() - from.row)
                            val next = GridSpot(c, r, w, h)
                            if (BookStore.taken(context, widget.appWidgetId).none { it.overlaps(next) }) resizeSpot = next
                        }
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(if (horizontal) 36.dp else 10.dp, if (horizontal) 10.dp else 36.dp).clip(CircleShape)
                        .background(colors.primary).border(2.dp, colors.onPrimary, CircleShape))
                }
            }
            handle(Alignment.CenterStart, false, 1, 0, 0, 0)
            handle(Alignment.CenterEnd, false, 0, 0, 1, 0)
            handle(Alignment.TopCenter, true, 0, 1, 0, 0)
            handle(Alignment.BottomCenter, true, 0, 0, 0, 1)
        }
    }

    private fun snapshot(view: View): ImageBitmap? = runCatching {
        val bitmap = Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        bitmap.asImageBitmap()
    }.getOrNull()

    private fun widgetMenu(widget: DesktopWidget, x: Float, y: Float) {
        val info = appWidgets.info(widget.appWidgetId)
        launcher.showMenu(buildList {
            add(MenuItem(info?.let(appWidgets::label) ?: tr("ウィジェット", "Widget"), header = true))
            add(MenuItem(tr("サイズを変更", "Resize"), Glyph.FULLSCREEN_EXIT) { resizeSpot = null; resizing = widget.appWidgetId })
            if (info != null && appWidgets.canReconfigure(info)) add(MenuItem(tr("ウィジェットの設定", "Widget settings"), Glyph.SETTINGS) {
                appWidgets.configure(widget.appWidgetId)
            })
            add(MenuItem(tr("ウィジェットを追加", "Add widgets"), Glyph.PLUS) { host.openWidgetGallery() })
            add(MenuItem(tr("削除", "Remove"), Glyph.CLOSE, separatorBefore = true) { removeWidget(widget.appWidgetId) })
        }, x, y)
    }
    // endregion

    // region the Linux container's ~/Desktop

    private fun b64(text: String) = android.util.Base64.encodeToString(text.toByteArray(), android.util.Base64.NO_WRAP)

    private fun desktopScript(body: String) = BookDesktopFiles.directoryScript + "\n" + body
    private var desktopReadPending = false

    /** Read the guest's actual desktop. A failed or obsolete request must not erase icons. */
    fun refreshLinuxDesktop() {
        val container = chrome.desktopContainer()
        if (container == null) {
            val cached = BookStore.cachedLinuxDesktop(context)
                ?.takeIf { BookStore.flag(context, BookStore.LINUX_DESKTOP_FOLDER, false) }
            linuxFilesContainer = cached?.first
            linuxFiles = cached?.second.orEmpty()
            return
        }
        if (linuxFilesContainer != container) {
            linuxFilesContainer = container
            linuxFiles = BookStore.cachedLinuxDesktop(context, container)?.second.orEmpty()
        }
        if (desktopReadPending) return
        desktopReadPending = true
        chrome.linuxDesktop(desktopScript(BookDesktopFiles.listingScript)) { output ->
            desktopReadPending = false
            if (container != chrome.desktopContainer()) {
                refreshLinuxDesktop()
                return@linuxDesktop
            }
            val files = output?.let(BookDesktopFiles::parse) ?: return@linuxDesktop
            BookStore.cacheLinuxDesktop(context, container, files)
            linuxFiles = files
            val names = files.map { LINUX_DESKTOP_PREFIX + it.first }.toSet()
            val stale = BookStore.shortcuts(context).filter { it.packageName.startsWith(LINUX_DESKTOP_PREFIX) && it.packageName !in names }
            stale.forEach { BookStore.removeShortcut(context, it.packageName) }
            val placed = BookStore.shortcuts(context).map { it.packageName }.toSet()
            names.filter { it !in placed }.forEach { key ->
                BookStore.freeSpot(GridSpot(0, 0), BookStore.taken(context), gridColumns(), gridRows())
                    ?.let { BookStore.placeShortcut(context, key, it) }
            }
        }
    }

    private val linuxDesktopPoll = object : Runnable {
        override fun run() {
            refreshLinuxDesktop()
            handler.postDelayed(this, 3_000L)
        }
    }

    /** Makes a folder in ~/Desktop ("New folder", "New folder 2"…) and asks for its name. */
    fun newLinuxFolder(at: RectF) {
        if (chrome.desktopContainer() == null) return
        val base = tr("新しいフォルダ", "New folder")
        val taken = linuxFiles.map { it.first }.toSet()
        val name = generateSequence(1) { it + 1 }.map { if (it == 1) base else "$base $it" }.first { it !in taken }
        chrome.linuxDesktop(desktopScript("""mkdir -- "${'$'}D/${'$'}(n ${b64(name)})" && echo ok""")) { output ->
            refreshLinuxDesktop()
            if (output?.trim() == "ok") rename(name, at)
        }
    }

    private fun rename(name: String, at: RectF) {
        renaming = name
        chrome.openBookPanel("rename", at)
    }

    @Composable
    private fun RenamePanel() {
        val colors = MaterialTheme.colorScheme
        val old = renaming ?: return
        var text by remember(old) { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(old,
            androidx.compose.ui.text.TextRange(0, old.length))) }
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        fun done() {
            val next = text.text.trim()
            host.closePanel()
            renaming = null
            if (!BookDesktopFiles.validName(next) || next == old) return
            chrome.linuxDesktop(desktopScript("""P="${'$'}D/${'$'}(n ${b64(next)})"; [ ! -e "${'$'}P" ] && [ ! -L "${'$'}P" ] && mv -T -n -- "${'$'}D/${'$'}(n ${b64(old)})" "${'$'}P" && [ ! -e "${'$'}D/${'$'}(n ${b64(old)})" ] && [ ! -L "${'$'}D/${'$'}(n ${b64(old)})" ] && echo ok""")) { ok ->
                if (ok?.trim() == "ok") BookStore.shortcuts(context).firstOrNull { it.packageName == LINUX_DESKTOP_PREFIX + old }?.let {
                    BookStore.removeShortcut(context, it.packageName)
                    BookStore.placeShortcut(context, LINUX_DESKTOP_PREFIX + next, it.spot)
                }
                refreshLinuxDesktop()
            }
        }
        BookPanelSurface(360.dp) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(tr("名前を変更", "Rename"), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { done() }),
                    colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    Text(tr("キャンセル", "Cancel"), style = MaterialTheme.typography.labelLarge, color = colors.primary,
                        modifier = Modifier.clip(CircleShape).clickable { host.closePanel(); renaming = null }
                            .padding(horizontal = 16.dp, vertical = 10.dp))
                    Text(tr("変更", "Rename"), style = MaterialTheme.typography.labelLarge, color = colors.onPrimary,
                        modifier = Modifier.clip(CircleShape).background(colors.primary).clickable { done() }
                            .padding(horizontal = 20.dp, vertical = 10.dp))
                }
            }
        }
    }
    // endregion

    // region drag and drop

    /** Where the dragged item would land now, or null (over the launcher, or no room). */
    private fun dropTarget(d: DragState): GridSpot? {
        if (d.item is BookDragItem.App && launcherOpen && launcherBounds.contains(d.x, d.y)) return null
        if ((d.item is BookDragItem.App || d.item is BookDragItem.Shortcut) && dockRect.contains(d.x, d.y)) return null
        val (columns, rows, except) = when (val item = d.item) {
            is BookDragItem.App -> Triple(1, 1, item.app.packageName)
            is BookDragItem.Shortcut -> Triple(1, 1, item.app.packageName)
            is BookDragItem.Widget -> Triple(item.from.columns, item.from.rows, item.appWidgetId)
            is BookDragItem.NewWidget -> spanOf(item.info).let { Triple(it.first, it.second, null) }
        }
        // The cell under the item's top-left corner, rounded to the nearest.
        val left = d.x - d.grab.x + cell / 2f
        val top = d.y - d.grab.y + cell / 2f
        val corner = cellAt(left, top)
        return BookStore.freeSpot(GridSpot(corner.column, corner.row, columns, rows), BookStore.taken(context, except), gridColumns(), gridRows())
    }

    private fun startDrag(item: BookDragItem, x: Float, y: Float, grab: Offset) {
        hideTooltip()
        drag = DragState(item, x, y, grab)
        showOverlay()
    }

    private fun moveDrag(x: Float, y: Float) {
        drag = drag?.copy(x = x, y = y)
    }

    private fun endDrag(x: Float, y: Float, cancel: Boolean = false) {
        val d = drag?.copy(x = x, y = y) ?: return
        drag = null
        hideOverlaySoon()
        if (cancel) return
        val target = dropTarget(d)
        val from = IntOffset((d.x - d.grab.x).roundToInt(), (d.y - d.grab.y).roundToInt())
        when (val item = d.item) {
            is BookDragItem.App -> {
                when {
                    dockRect.contains(d.x, d.y) -> { launcher.setPinned(item.app.packageName, true); refresh() }
                    target != null -> { landing[item.app.packageName] = from; addShortcut(item.app.packageName, target) }
                }
                if (target != null || dockRect.contains(d.x, d.y)) launcher.dismissPopup()
            }
            is BookDragItem.Shortcut -> when {
                dockRect.contains(d.x, d.y) -> { launcher.setPinned(item.app.packageName, true); refresh() }
                target != null -> { landing[item.app.packageName] = from; BookStore.placeShortcut(context, item.app.packageName, target) }
            }
            is BookDragItem.Widget -> if (target != null) {
                landing[item.appWidgetId] = from
                BookStore.placeWidget(context, item.appWidgetId, target)
            }
            is BookDragItem.NewWidget -> if (target != null) addWidget(item.info, target)
        }
    }

    /** The target cells and the dragged item under the pointer. */
    @Composable
    private fun DragLayer(d: DragState) {
        val colors = MaterialTheme.colorScheme
        val px = LocalDensity.current
        val target = dropTarget(d)
        if (target != null) {
            val origin = spotOrigin(target)
            val slide = remember { Animatable(origin, IntOffset.VectorConverter) }
            LaunchedEffect(origin) { slide.animateTo(origin, spring(stiffness = Spring.StiffnessMedium)) }
            Box(Modifier.offset { slide.value }.size(CELL * target.columns, CELL * target.rows).padding(4.dp)
                .clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = .14f))
                .border(2.dp, Color.White.copy(alpha = .9f), RoundedCornerShape(18.dp)))
        }
        val lift = remember { Animatable(1f) }
        LaunchedEffect(Unit) { lift.animateTo(1.08f, spring(dampingRatio = .55f, stiffness = Spring.StiffnessMedium)) }
        val topLeft = IntOffset((d.x - d.grab.x).roundToInt(), (d.y - d.grab.y).roundToInt())
        Box(Modifier.offset { topLeft }.scale(lift.value)) {
            when (val item = d.item) {
                is BookDragItem.App, is BookDragItem.Shortcut -> {
                    val app = (item as? BookDragItem.App)?.app ?: (item as BookDragItem.Shortcut).app
                    Column(Modifier.size(CELL).padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) {
                            DrawableIcon(app.icon, 56.dp)
                        }
                        if (dockRect.contains(d.x, d.y)) Text(tr("タスクバーに固定", "Pin to taskbar"), style = MaterialTheme.typography.labelMedium,
                            color = colors.onPrimary, modifier = Modifier.padding(top = 6.dp).clip(CircleShape).background(colors.primary)
                                .padding(horizontal = 10.dp, vertical = 4.dp))
                    }
                }
                is BookDragItem.Widget -> Box(Modifier.size(CELL * item.from.columns, CELL * item.from.rows).padding(8.dp)
                    .clip(RoundedCornerShape(24.dp)).alpha(.92f)) {
                    item.snapshot?.let { Image(it, null, Modifier.fillMaxSize()) }
                }
                is BookDragItem.NewWidget -> {
                    val (columns, rows) = spanOf(item.info)
                    Box(Modifier.size(CELL * columns, CELL * rows).padding(8.dp)
                        .clip(RoundedCornerShape(24.dp)).background(colors.surfaceContainer), contentAlignment = Alignment.Center) {
                        DrawableIcon(item.preview, with(px) { (minOf(columns, rows) * CELL.value).dp } - 24.dp)
                    }
                }
            }
        }
    }

    /**
     * Book's pointer gesture for something that opens, has a menu and can be dragged. A mouse
     * drags at once and has its menu on the right button; a finger (or, with [holdFirst], any
     * pointer) holds first: the item lifts, then follows, or shows its menu if it was not moved.
     * With [initialPass] the gesture watches before the content (Android widget views), which
     * keeps its own taps.
     */
    private fun Modifier.bookDrag(
        item: () -> BookDragItem,
        onTap: (() -> Unit)?,
        onMenu: ((Float, Float) -> Unit)?,
        holdFirst: Boolean,
        initialPass: Boolean,
        grab: (Offset) -> Offset,
    ): Modifier = composed {
        val tap by rememberUpdatedState(onTap)
        val menu by rememberUpdatedState(onMenu)
        val itemOf by rememberUpdatedState(item)
        val grabOf by rememberUpdatedState(grab)
        var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
        this.onGloballyPositioned { coords = it }.pointerInput(Unit) {
            val pass = if (initialPass) PointerEventPass.Initial else PointerEventPass.Main
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = pass)
                // The root of every Book layer is at the screen's origin.
                fun screen(p: Offset) = coords?.takeIf { it.isAttached }?.localToWindow(p) ?: p
                if (down.type == PointerType.Mouse && currentEvent.buttons.isSecondaryPressed) {
                    down.consume()
                    val s = screen(down.position)
                    menu?.invoke(s.x, s.y)
                    return@awaitEachGesture
                }
                val mouse = down.type == PointerType.Mouse
                var lifted = false
                if (holdFirst || !mouse) {
                    val released = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        while (true) {
                            val e = awaitPointerEvent(pass)
                            val c = e.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull false
                            if (!c.pressed) return@withTimeoutOrNull true
                            if ((c.position - down.position).getDistance() > viewConfiguration.touchSlop) return@withTimeoutOrNull false
                        }
                        @Suppress("UNREACHABLE_CODE") false
                    }
                    when (released) {
                        true -> { if (!initialPass) { down.consume(); tap?.invoke() }; return@awaitEachGesture }
                        false -> return@awaitEachGesture
                        null -> {
                            lifted = true
                            val s = screen(down.position)
                            startDrag(itemOf(), s.x, s.y, grabOf(down.position))
                        }
                    }
                }
                var dragging = lifted
                var moved = false
                var last = down.position
                while (true) {
                    val e = awaitPointerEvent(pass)
                    val c = e.changes.firstOrNull { it.id == down.id } ?: break
                    if (!c.pressed) break
                    if (mouse && !e.buttons.isPrimaryPressed) break
                    last = c.position
                    val far = (c.position - down.position).getDistance() > viewConfiguration.touchSlop
                    if (far) moved = true
                    if (!dragging && far) {
                        dragging = true
                        val s = screen(down.position)
                        startDrag(itemOf(), s.x, s.y, grabOf(down.position))
                    }
                    if (dragging) {
                        c.consume()
                        val s = screen(c.position)
                        moveDrag(s.x, s.y)
                    }
                }
                val s = screen(last)
                when {
                    lifted && !moved -> { endDrag(s.x, s.y, cancel = true); menu?.invoke(s.x, s.y) }
                    dragging -> endDrag(s.x, s.y)
                    !initialPass -> tap?.invoke()
                }
            }
        }
    }
    // endregion

    // region Book's own windows

    fun openWindow(kind: String, page: String? = null) {
        if (page != null) settingsContent.page = page
        bookWindows.firstOrNull { it.kind == kind }?.let { existing ->
            existing.minimized = false
            raiseWindow(existing)
            return
        }
        val work = workArea()
        val w = minOf(work.width() * .72f, 1080 * density).roundToInt()
        val h = minOf(work.height() * .82f, 720 * density).roundToInt()
        val step = (32 * density).roundToInt() * bookWindows.size
        val left = work.left + (work.width() - w) / 2 + step
        val top = work.top + (work.height() - h) / 2 + step
        bookWindows.add(BookWindow(kind, Rect(left, top, left + w, top + h)))
        updateBookFocus()
    }

    private fun workArea() = Rect(area.left, area.top + topInset, area.right, area.bottom - bottomInset)

    private fun raiseWindow(window: BookWindow) {
        if (bookWindows.lastOrNull() !== window) {
            bookWindows.remove(window)
            bookWindows.add(window)
        }
        updateBookFocus()
    }

    private fun closeWindow(window: BookWindow) {
        bookWindows.remove(window)
        updateBookFocus()
    }

    private fun minimizeWindow(window: BookWindow) {
        window.minimized = true
        updateBookFocus()
    }

    /** Book's windows take the keyboard while one of them is shown. */
    private fun updateBookFocus() = chrome.setBookFocus(bookWindows.any { !it.minimized })

    val hasWindows: Boolean get() = bookWindows.any { !it.minimized }

    private fun windowTitle(kind: String) =
        if (kind == "settings") tr("Dextop Book の設定", "Dextop Book settings") else tr("ウィジェット", "Widgets")

    private fun windowSymbol(kind: String) = if (kind == "settings") "settings" else "widgets"

    @Composable
    private fun BookWindows() {
        Box(Modifier.fillMaxSize()) {
            bookWindows.forEach { window -> androidx.compose.runtime.key(window.kind) { BookWindowFrame(window) } }
        }
    }

    @Composable
    private fun BookWindowFrame(window: BookWindow) {
        val colors = MaterialTheme.colorScheme
        val px = LocalDensity.current
        val work = workArea()
        // Maximizing blends the frame into the work area; dragging moves the frame directly.
        val toMax by animateFloatAsState(if (window.maximized) 1f else 0f,
            spring(dampingRatio = .9f, stiffness = Spring.StiffnessMediumLow), label = "maximize")
        val shown by animateFloatAsState(if (window.minimized) 0f else 1f,
            spring(dampingRatio = .9f, stiffness = Spring.StiffnessMedium), label = "minimize")
        val appear = remember { Animatable(0f) }
        LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = .85f, stiffness = Spring.StiffnessMediumLow)) }
        if (shown <= .01f && window.minimized) return
        val f = window.frame
        fun lerp(a: Int, b: Int) = (a + (b - a) * toMax).roundToInt()
        val left = lerp(f.left, work.left)
        val top = lerp(f.top, work.top)
        val width = lerp(f.width(), work.width())
        val height = lerp(f.height(), work.height())
        val corner = 16.dp * (1f - toMax)
        val active = bookWindows.lastOrNull() === window
        // Minimizing shrinks into its dock icon (the window's centre moves onto the icon's), and
        // restoring grows out of it again. The dock reports screen coordinates.
        val origin = IntArray(2).also(LocalView.current::getLocationOnScreen)
        val icon = window.dockIcon
        val centerX = left + width / 2f
        val centerY = top + height / 2f
        val targetX = icon?.let { it.centerX() - origin[0] } ?: centerX
        val targetY = icon?.let { it.centerY() - origin[1] } ?: area.bottom.toFloat()
        val away = 1f - shown
        Box(
            Modifier.offset { IntOffset((left + away * (targetX - centerX)).roundToInt(), (top + away * (targetY - centerY)).roundToInt()) }
                .size(with(px) { width.toDp() }, with(px) { height.toDp() })
                .scale((.94f + .06f * appear.value) * (.08f + .92f * shown)).alpha(appear.value * shown),
        ) {
            Surface(
                Modifier.fillMaxSize().blockPointer()
                    .pointerInput(window) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial); raiseWindow(window) } },
                shape = RoundedCornerShape(corner),
                color = colors.surfaceContainerLow,
                shadowElevation = if (window.maximized) 0.dp else if (active) 10.dp else 4.dp,
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().height(44.dp).background(if (active) colors.surfaceContainerHigh else colors.surfaceContainer)
                            .pointerInput(window) {
                                detectTapGestures(onDoubleTap = { window.maximized = !window.maximized })
                            }
                            .pointerInput(window) {
                                detectDragGestures(
                                    onDragStart = { start ->
                                        if (window.maximized) {
                                            // Out of maximized: the restored frame follows the pointer.
                                            val ratio = (start.x / width).coerceIn(0f, 1f)
                                            val w = window.frame.width()
                                            val l = (left + start.x - w * ratio).roundToInt()
                                            window.frame = Rect(l, work.top, l + w, work.top + window.frame.height())
                                            window.maximized = false
                                        }
                                    },
                                ) { change, amount ->
                                    change.consume()
                                    val r = Rect(window.frame)
                                    r.offset(amount.x.roundToInt(), amount.y.roundToInt())
                                    // The title bar stays reachable below the status bar.
                                    if (r.top < work.top) r.offsetTo(r.left, work.top)
                                    window.frame = r
                                }
                            }
                            .padding(start = 12.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(Modifier.size(24.dp).clip(CircleShape).background(colors.primary), contentAlignment = Alignment.Center) {
                            Sym(windowSymbol(window.kind), colors.onPrimary, 16.dp, filled = true)
                        }
                        Text(windowTitle(window.kind), style = MaterialTheme.typography.titleSmall,
                            color = if (active) colors.onSurface else colors.onSurfaceVariant, modifier = Modifier.weight(1f))
                        BookIconButton("remove", { minimizeWindow(window) }, size = 32.dp, tint = colors.onSurfaceVariant)
                        BookIconButton(if (window.maximized) "filter_none" else "crop_square", { window.maximized = !window.maximized },
                            size = 32.dp, tint = colors.onSurfaceVariant)
                        BookIconButton("close", { closeWindow(window) }, size = 32.dp, tint = colors.onSurfaceVariant)
                    }
                    Box(Modifier.fillMaxSize()) {
                        if (window.kind == "settings") settingsContent.Content() else picker.Content()
                    }
                }
            }
            if (!window.maximized) ResizeEdges(window, work)
        }
    }

    /** Invisible grips along the edges and corners that resize the window. */
    @Composable
    private fun androidx.compose.foundation.layout.BoxScope.ResizeEdges(window: BookWindow, work: Rect) {
        val grip = 8.dp
        val minW = (480 * density).roundToInt()
        val minH = (320 * density).roundToInt()
        @Composable
        fun edge(alignment: Alignment, modifier: Modifier, dl: Int, dt: Int, dr: Int, db: Int) {
            Box(modifier.align(alignment).pointerInput(window) {
                detectDragGestures { change, amount ->
                    change.consume()
                    val r = Rect(window.frame)
                    val dx = amount.x.roundToInt()
                    val dy = amount.y.roundToInt()
                    if (dl != 0) r.left = (r.left + dx).coerceIn(work.left, r.right - minW)
                    if (dr != 0) r.right = (r.right + dx).coerceIn(r.left + minW, work.right)
                    if (dt != 0) r.top = (r.top + dy).coerceIn(work.top, r.bottom - minH)
                    if (db != 0) r.bottom = (r.bottom + dy).coerceIn(r.top + minH, work.bottom)
                    window.frame = r
                }
            })
        }
        edge(Alignment.CenterStart, Modifier.width(grip).fillMaxHeight().padding(vertical = 16.dp), 1, 0, 0, 0)
        edge(Alignment.CenterEnd, Modifier.width(grip).fillMaxHeight().padding(vertical = 16.dp), 0, 0, 1, 0)
        edge(Alignment.TopCenter, Modifier.height(grip / 2).fillMaxWidth().padding(horizontal = 16.dp), 0, 1, 0, 0)
        edge(Alignment.BottomCenter, Modifier.height(grip).fillMaxWidth().padding(horizontal = 16.dp), 0, 0, 0, 1)
        edge(Alignment.TopStart, Modifier.size(16.dp), 1, 1, 0, 0)
        edge(Alignment.TopEnd, Modifier.size(16.dp), 0, 1, 1, 0)
        edge(Alignment.BottomStart, Modifier.size(16.dp), 1, 0, 0, 1)
        edge(Alignment.BottomEnd, Modifier.size(16.dp), 0, 0, 1, 1)
    }

    /** Book's own windows on the taskbar, after the apps. */
    @Composable
    private fun DockBookWindows() {
        val colors = MaterialTheme.colorScheme
        bookWindows.forEach { window ->
            var hovered by remember { mutableStateOf(false) }
            val active = !window.minimized && bookWindows.lastOrNull() === window
            var anchor = RectF()
            Column(Modifier.padding(horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (hovered || active) colors.onSurface.copy(alpha = if (active) .12f else .08f) else Color.Transparent)
                        .onScreen { anchor = it; window.dockIcon = it }
                        .pointerButtons(onHover = { inside ->
                            hovered = inside
                            if (inside) showTooltip(windowTitle(window.kind), anchor) else hideTooltip()
                        })
                        .clickable {
                            hideTooltip()
                            when {
                                window.minimized -> { window.minimized = false; raiseWindow(window) }
                                active -> minimizeWindow(window)
                                else -> raiseWindow(window)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(32.dp).clip(CircleShape).background(colors.primary), contentAlignment = Alignment.Center) {
                        Sym(windowSymbol(window.kind), colors.onPrimary, 20.dp, filled = true)
                    }
                }
                Box(Modifier.padding(top = 2.dp).height(3.dp).width(if (active) 16.dp else 6.dp).clip(CircleShape)
                    .background(if (active) colors.primary else colors.onSurfaceVariant))
            }
        }
    }
    // endregion

    /** Book's Alt+Tab: the windows as cards, the chosen one outlined. */
    private inner class BookSwitcher(private val windows: List<ManagedWindow>, private val thumbnails: List<Bitmap?>) : TaskSwitcher {
        private var selected by mutableIntStateOf(if (windows.size > 1) 1 else 0)
        override var index: Int
            get() = selected
            set(value) { selected = value }
        override fun selectedWindow(): ManagedWindow? = windows.getOrNull(selected)
        override fun desiredSize(maxWidth: Int): Pair<Int, Int> {
            val card = (236 * density).roundToInt()
            return minOf(maxWidth, card * windows.size + (40 * density).roundToInt()) to (230 * density).roundToInt()
        }
        override val view: View = compose { Content() }

        @Composable
        private fun Content() {
            val colors = MaterialTheme.colorScheme
            Surface(Modifier.fillMaxSize(), shape = RoundedCornerShape(28.dp), color = colors.surfaceContainer.copy(alpha = .96f)) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    windows.forEachIndexed { i, window ->
                        val on = i == selected
                        Column(Modifier.width(220.dp).clip(RoundedCornerShape(20.dp))
                            .background(if (on) colors.secondaryContainer else Color.Transparent)
                            .border(if (on) 3.dp else 0.dp, if (on) colors.primary else Color.Transparent, RoundedCornerShape(20.dp))
                            .padding(10.dp)) {
                            Box(Modifier.fillMaxWidth().height(130.dp).clip(RoundedCornerShape(12.dp)).background(colors.surfaceContainerHighest),
                                contentAlignment = Alignment.Center) {
                                val bitmap = thumbnails.getOrNull(i)
                                if (bitmap != null) Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize())
                                else DrawableIcon(window.icon, 56.dp)
                            }
                            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                DrawableIcon(window.icon, 20.dp)
                                Text(window.title, style = MaterialTheme.typography.labelLarge, color = colors.onSurface, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        /** Desktop items of the Linux container's ~/Desktop: this prefix and the item's name. */
        const val LINUX_DESKTOP_PREFIX = "linuxdesk:"
        private val TOP_BAR = 36.dp
        private val DOCK = 60.dp
        private val CELL = 96.dp
        private val GRID_MARGIN = 16.dp
        private val PANEL_SHADOW = 12.dp
    }
}
