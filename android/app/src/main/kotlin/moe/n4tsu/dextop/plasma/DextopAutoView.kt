package moe.n4tsu.dextop.plasma

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.media.AudioManager
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class AutoTask(val id: Int, val label: String, val icon: Drawable?, val packageName: String? = null)

/** A game controller on the phone; [linux] when Steam (Linux) takes it instead of Android. */
internal data class AutoController(val key: String, val name: String, val linux: Boolean)

internal interface AutoUiHost {
    val autoPalette: PlasmaPalette
    val workspace: AutoWorkspace
    fun applications(): List<PlasmaApp>
    fun tasks(): List<AutoTask>
    fun launchAuto(packageName: String, pane: Int?)
    fun selectAuto(id: Int)
    fun focusAuto(pane: Int)
    fun resumeAutoInput()
    fun closeAuto(id: Int)
    fun splitAuto()
    fun singleAuto(pane: Int)
    fun swapAuto()
    fun resizeAuto(ratio: Float, final: Boolean)
    fun autoBack()
    fun autoVolume(direction: Int)
    fun autoMedia(key: Int)
    fun autoControl(item: TrayItem)
    fun autoTheme(value: String)
    /** Zoom of the active application, or null when no application is shown. */
    fun autoScale(): Float?
    fun setAutoScale(scale: Float)
    fun relayoutAuto()
    /** System controls for the Auto quick-control pages; their calls block, so use [autoLoad]. */
    val autoControls: PlasmaSystemControls
    /** Runs [work] off the main thread and delivers its result while [owner] is attached. */
    fun <T> autoLoad(owner: View, work: () -> T, done: (T) -> Unit)
    fun autoOpenNotification(item: DextopNotification)
    fun autoDismissNotification(item: DextopNotification)
    /** Opens an Android settings screen in an Auto pane. */
    fun autoOpenSettings(action: String)
    fun autoTrayChanged()
    /** Tasks running on the phone display; blocks, so call it through [autoLoad]. */
    fun autoPhoneTasks(): List<TaskSnapshot>
    fun autoAdoptPhoneTask(task: TaskSnapshot, pane: Int?)
    /** Connected game controllers when Steam's Deck mode is installed, otherwise null. */
    fun autoControllers(): List<AutoController>?
    fun setAutoControllerForLinux(controller: AutoController, linux: Boolean)
}

/** Car colours: the Plasma accent on a darker, higher-contrast surface than the desktop. */
internal class AutoColors(palette: PlasmaPalette) {
    val dark = palette.dark
    val background = if (dark) 0xFF0C0E10.toInt() else 0xFFECEEF1.toInt()
    val rail = if (dark) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
    val card = if (dark) 0xFF1B1F23.toInt() else 0xFFFFFFFF.toInt()
    val raised = if (dark) 0xFF2A2F35.toInt() else 0xFFDDE1E6.toInt()
    val text = if (dark) 0xFFF4F5F6.toInt() else 0xFF14171A.toInt()
    val secondary = if (dark) 0xFF98A1AB.toInt() else 0xFF5B6570.toInt()
    val accent = palette.accent
    val onAccent = 0xFFFFFFFF.toInt()
    val accentSoft = (palette.accent and 0x00FFFFFF) or 0x40000000
    val pressed = if (dark) 0x33FFFFFF else 0x22000000
    val separator = if (dark) 0x1FFFFFFF else 0x1A000000
    val scrim = 0xA0000000.toInt()
}

/**
 * Car chrome: one compact rail beside the applications, and pages drawn over the app area.
 *
 * Every hit target is at least [MIN_TOUCH] units even where the drawn shape is smaller, so the
 * chrome stays small without falling below the touch sizes expected on a car display. One unit
 * is 1/360 of the Surface height (or 1/800 of its width, whichever is smaller). Empty areas pass
 * input through to the application panes underneath. The host's floating template buttons are
 * not avoided; in full screen the rail is hidden and the application gets the whole Surface.
 */
@SuppressLint("ViewConstructor")
internal class DextopAutoView(context: Context, private val host: AutoUiHost) : FrameLayout(context) {
    private companion object {
        const val RAIL = 68
        const val TOUCH = 52
        const val MIN_TOUCH = 48
        const val STATUS = 64
        const val INSET = 6
        const val GAP = 8
        const val DIVIDER = 14
        const val DIVIDER_GRAB = 6
        const val RESIZE_SETTLE_MS = 350L
        const val CORNER = 14
        const val TRANSITION_MS = 220L
        /** Rail widths offered in the settings, in units. */
        val RAIL_WIDTHS = listOf(56, 68, 84)
        // Status pictogram: the battery ring leaves a gap at the bottom for the signal dots.
        // Angles are in degrees, clockwise from three o'clock.
        const val RING_GAP = 90f
        const val RING_START = 90f + RING_GAP / 2f
        const val RING_SWEEP = 360f - RING_GAP
        const val SIGNAL_DOTS = 4
        val BATTERY_CHARGING = 0xFF34C759.toInt()
        val BATTERY_SAVER = 0xFFFFCC00.toInt()
        val BATTERY_LOW = 0xFFFF3B30.toInt()
        /** Entering motion: quick start, long gentle settle. */
        val ENTER = PathInterpolator(.05f, .7f, .1f, 1f)
        /** Leaving motion: slow start, quick finish. */
        val EXIT = PathInterpolator(.3f, 0f, .8f, .15f)
        val SPRING = OvershootInterpolator(1.6f)
        const val STAGGER_MS = 18L
        const val STAGGER_MAX = 24
        /** Tag of a chrome part fading out; see [appear]. */
        val HIDING = Any()
    }

    private class Geometry(
        val viewport: Rect,
        val rail: Rect,
        val content: Rect,
        val landscape: Boolean,
        val frames: List<Rect>,
        /** Everything beside the rail; pages fill it edge to edge so nothing shows around them. */
        val surface: Rect,
    )

    private val prefs = context.getSharedPreferences("dextop_auto_ui", Context.MODE_PRIVATE)
    private var colors = AutoColors(host.autoPalette)
    private var colorSource: PlasmaPalette? = null
    private val japanese get() = resources.configuration.locales[0].language == "ja"
    private fun tr(ja: String, en: String) = if (japanese) ja else en

    private val unit: Float get() {
        val area = viewport()
        return if (area.width() > 0 && area.height() > 0)
            minOf(area.width() / 800f, area.height() / 360f).coerceAtLeast(.5f)
        else resources.displayMetrics.density
    }
    private fun dp(n: Int) = (n * unit).roundToInt()
    private fun dpf(n: Float) = n * unit

    private val rail = LinearLayout(context)
    private val status = StatusCell()
    private val focusRing = View(context)
    private val divider = DividerHandle()
    private val emptyPane = FrameLayout(context)
    /** Split ratio while the divider is dragged; the applications are resized only on release. */
    private var previewRatio: Float? = null
    private var previewHoldUntil = 0L
    private val previews = arrayOf(FrameLayout(context), FrameLayout(context))
    /** Covers every pane while resizing, so nothing of the old app frames shows around the cards. */
    private val previewBackdrop = View(context)
    private val previewPercent = arrayOf(TextView(context), TextView(context))
    private var page: View? = null
    val pageDiagnostic get() = if (page == null) "none" else pageKind
    private var pageKind = "home"
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val handled = super.dispatchTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL) {
            android.util.Log.d("DextopAutoInput", "touch action=${event.actionMasked} page=$pageDiagnostic handled=$handled")
        }
        return handled
    }
    private var pickingPane: Int? = null
    private var filter = "all"
    private var fullscreen = prefs.getBoolean("fullscreen", false)
    private val immersive get() = fullscreen && host.workspace.primary != null
    private val restore = Tap(tr("全画面表示を終了", "Exit full screen"), shape = 0, iconPx = 0, action = { setFullscreen(false) })
    private var railSignature = ""
    private var lastTaskSignature = ""
    private var mode = ""
    private var media: PlasmaSystemControls.MediaInfo? = null
    private var tray: TrayState? = null

    // Live parts of the home page, updated without rebuilding it.
    private var mediaTitle: TextView? = null
    private var mediaArtist: TextView? = null
    private var mediaSource: TextView? = null
    private var playButton: Tap? = null
    private var volumeBar: LevelBar? = null
    private var mediaArt: android.widget.ImageView? = null
    private var artwork: android.graphics.Bitmap? = null

    private val notificationsChanged = {
        if (page != null && pageKind == "qs:notifications") showPage(pageKind)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        NotificationCenter.addListener(notificationsChanged)
        context.getSystemService(android.hardware.input.InputManager::class.java)?.registerInputDeviceListener(controllerWatcher, handler)
    }

    override fun onDetachedFromWindow() {
        NotificationCenter.removeListener(notificationsChanged)
        context.getSystemService(android.hardware.input.InputManager::class.java)?.unregisterInputDeviceListener(controllerWatcher)
        super.onDetachedFromWindow()
    }

    init {
        clipChildren = true
        addView(focusRing)
        addView(emptyPane)
        addView(divider)
        addView(previewBackdrop.apply { visibility = GONE; isClickable = true })
        previews.forEach { addView(it.apply { visibility = GONE }) }
        addView(rail)
        addView(restore)
        focusRing.isClickable = false
        focusRing.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    // region geometry
    private fun viewport(): Rect = Rect(0, 0, width, height)

    private fun geometry(ratio: Float = previewRatio ?: host.workspace.ratio): Geometry {
        val v = viewport()
        val landscape = v.width() >= v.height()
        val size = dp(railWidth).coerceAtMost((if (landscape) v.width() else v.height()) / 4)
        val inset = if (appGaps) dp(INSET) else 0
        val rail: Rect
        val content: Rect
        if (immersive) {
            rail = Rect(v.left, v.top, v.left, v.top)
            content = Rect(v)
        } else if (landscape) {
            rail = Rect(v.left, v.top, v.left + size, v.bottom)
            content = Rect(rail.right + inset, v.top + inset, v.right - inset, v.bottom - inset)
        } else {
            rail = Rect(v.left, v.bottom - size, v.right, v.bottom)
            content = Rect(v.left + inset, v.top + inset, v.right - inset, rail.top - inset)
        }
        if (content.width() < 1) content.right = content.left + 1
        if (content.height() < 1) content.bottom = content.top + 1
        val frames = host.workspace.bounds(content.width(), content.height(), dp(DIVIDER), ratio).map {
            Rect(it.left + content.left, it.top + content.top, it.right + content.left, it.bottom + content.top)
        }
        val surface = when {
            immersive -> Rect(v)
            landscape -> Rect(rail.right, v.top, v.right, v.bottom)
            else -> Rect(v.left, v.top, v.right, rail.top)
        }
        return Geometry(v, rail, content, landscape, frames, surface)
    }

    fun visibleBounds(): Rect = viewport()

    /** Corner radius shared by the panes, cards and pages. */
    fun cornerRadius(): Float = if (appGaps) dpf(CORNER.toFloat()) else 0f
    private val railWidth get() = prefs.getInt("rail_width", RAIL).takeIf { it in RAIL_WIDTHS } ?: RAIL
    /** Space around the application panes and at the screen edges; the divider between panes always stays. */
    private val appGaps get() = prefs.getBoolean("app_gaps", true)

    /** Centre of the open page, or of the focused application pane. */
    fun gestureAnchor(): android.graphics.PointF {
        val g = geometry()
        val area = if (page != null) pageBounds(g) else g.frames.getOrNull(host.workspace.focusedPane) ?: g.content
        return android.graphics.PointF(area.exactCenterX(), area.exactCenterY())
    }
    fun appBounds(): List<Rect> = geometry(host.workspace.ratio).frames

    private fun place(view: View, rect: Rect) {
        val params = view.layoutParams as? LayoutParams ?: LayoutParams(1, 1)
        params.width = rect.width().coerceAtLeast(1)
        params.height = rect.height().coerceAtLeast(1)
        params.leftMargin = rect.left
        params.topMargin = rect.top
        params.gravity = Gravity.TOP or Gravity.START
        view.layoutParams = params
    }

    private fun pageBounds(g: Geometry): Rect {
        val pane = pickingPane
        return if (pane != null && host.workspace.split && pane in g.frames.indices && pageKind == "apps")
            g.frames[pane] else g.surface
    }

    private fun paneName(index: Int, landscape: Boolean) = when {
        landscape && index == 0 -> tr("左", "left")
        landscape -> tr("右", "right")
        index == 0 -> tr("上", "top")
        else -> tr("下", "bottom")
    }
    // endregion

    // region chrome
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        railSignature = ""
        refreshChrome()
        if (page != null || pageKind.isNotEmpty()) showPage(pageKind.ifEmpty { "home" }, pickingPane)
    }

    private fun setFullscreen(value: Boolean) {
        if (fullscreen == value) return
        val landscape = width >= height
        fun commit() {
            fullscreen = value
            prefs.edit().putBoolean("fullscreen", value).apply()
            railSignature = ""
            host.relayoutAuto()
            refreshChrome()
        }
        if (value && rail.visibility == VISIBLE && rail.width > 0) {
            // The rail slides away first, then the panes take the whole Surface.
            rail.animate().translationX(if (landscape) -rail.width.toFloat() else 0f)
                .translationY(if (landscape) 0f else rail.height.toFloat())
                .setDuration(TRANSITION_MS).setInterpolator(PlasmaTheme.inCubic)
                .withEndAction { rail.translationX = 0f; rail.translationY = 0f; commit() }.start()
            return
        }
        commit()
        if (!value) {
            rail.translationX = if (landscape) -dpf(railWidth.toFloat()) else 0f
            rail.translationY = if (landscape) 0f else dpf(railWidth.toFloat())
            rail.animate().translationX(0f).translationY(0f).setDuration(TRANSITION_MS)
                .setInterpolator(PlasmaTheme.outCubic).start()
        } else {
            restore.alpha = 0f
            restore.animate().alpha(1f).setDuration(TRANSITION_MS).start()
        }
    }

    private fun syncColors() {
        val palette = host.autoPalette
        if (palette === colorSource) return
        colorSource = palette
        colors = AutoColors(palette)
        railSignature = ""
    }

    /** Repositions everything; the rail is only rebuilt when what it shows has changed. */
    fun refreshChrome() {
        if (width <= 0 || height <= 0) return
        syncColors()
        val g = geometry()
        val ws = host.workspace
        rail.visibility = if (immersive) GONE else VISIBLE
        restore.visibility = if (immersive) VISIBLE else GONE
        if (immersive) {
            val size = dp(MIN_TOUCH)
            val margin = dp(INSET)
            place(restore, Rect(g.viewport.left + margin, g.viewport.bottom - margin - size,
                g.viewport.left + margin + size, g.viewport.bottom - margin))
            restore.shape = dp(40)
            restore.iconPx = dp(22)
            restore.glyph = Glyph.FULLSCREEN_EXIT
            restore.fill = (colors.rail and 0x00FFFFFF) or 0xB0000000.toInt()
            restore.tint = colors.text
            restore.bringToFront()
        }
        place(rail, g.rail)
        val signature = listOf(
            g.landscape, g.rail.width(), g.rail.height(), host.tasks().joinToString { "${it.id}" },
            ws.primary, ws.secondary, ws.split, ws.focusedPane, if (page != null) pageKind else "", japanese,
        ).joinToString("|")
        if (signature != railSignature) {
            railSignature = signature
            buildRail(g)
        }

        val split = ws.split && g.frames.size > 1
        appear(divider, split)
        if (split) {
            val a = g.frames[0]
            val b = g.frames[1]
            val grab = dp(DIVIDER_GRAB)
            divider.vertical = g.content.width() >= g.content.height()
            place(divider, if (divider.vertical) Rect(a.right - grab, g.content.top, b.left + grab, g.content.bottom)
                else Rect(g.content.left, a.bottom - grab, g.content.right, b.top + grab))
        }

        val previewing = split && (previewRatio != null || SystemClock.uptimeMillis() < previewHoldUntil)
        previewBackdrop.visibility = if (previewing) VISIBLE else GONE
        if (previewing) {
            previewBackdrop.setBackgroundColor(colors.background)
            place(previewBackdrop, g.content)
            previewBackdrop.bringToFront()
        }
        previews.forEachIndexed { index, view ->
            view.visibility = if (previewing && index < g.frames.size) VISIBLE else GONE
            if (view.visibility == VISIBLE) {
                place(view, g.frames[index])
                val total = g.frames.sumOf { if (divider.vertical) it.width() else it.height() }.coerceAtLeast(1)
                val share = (if (divider.vertical) g.frames[index].width() else g.frames[index].height()) * 100f / total
                previewPercent[index].text = "${share.roundToInt()}%"
                view.bringToFront()
            }
        }

        val focused = g.frames.getOrNull(ws.focusedPane)
        val ringShown = split && focused != null && page == null && !previewing
        val ringMoved = ringShown && focusRing.visibility == VISIBLE && lastFocusPane != ws.focusedPane
        appear(focusRing, ringShown)
        lastFocusPane = if (ringShown) ws.focusedPane else -1
        if (ringMoved) {
            // The ring jumps to the other pane and settles onto it.
            focusRing.animate().cancel()
            focusRing.alpha = 0f; focusRing.scaleX = 1.03f; focusRing.scaleY = 1.03f
            focusRing.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(TRANSITION_MS).setInterpolator(ENTER).start()
        }
        if (focusRing.visibility == VISIBLE && focused != null) {
            // In the gap around the pane, or along its inside edge when there is no gap.
            val ring = if (appGaps) dp(3) else 0
            focusRing.background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                setStroke(dp(2), this@DextopAutoView.colors.accent)
                cornerRadius = if (appGaps) dpf(CORNER + 3f) else 0f
            }
            place(focusRing, Rect(focused.left - ring, focused.top - ring, focused.right + ring, focused.bottom + ring))
        }

        val showEmpty = split && ws.secondary == null
        val emptyArrives = showEmpty && emptyPane.visibility != VISIBLE
        appear(emptyPane, showEmpty)
        if (showEmpty) {
            place(emptyPane, g.frames[1])
            buildEmptyPane(g)
            if (emptyArrives) {
                emptyPane.scaleX = .96f; emptyPane.scaleY = .96f
                emptyPane.animate().scaleX(1f).scaleY(1f).setDuration(TRANSITION_MS + 80).setInterpolator(ENTER).start()
            }
        }

        page?.let { place(it, pageBounds(g)); it.bringToFront() }
        if (previewing) divider.bringToFront()
        rail.bringToFront()
        if (immersive) restore.bringToFront()
        bubble?.bringToFront()
    }

    private var lastFocusPane = -1

    /** Shows or hides a chrome part with a short fade instead of popping. */
    private fun appear(view: View, shown: Boolean) {
        val visible = view.visibility == VISIBLE && view.tag != HIDING
        if (shown == visible) return
        view.animate().cancel()
        if (shown) {
            view.tag = null
            if (view.visibility != VISIBLE) view.alpha = 0f
            view.visibility = VISIBLE
            view.animate().alpha(1f).setDuration(TRANSITION_MS).setInterpolator(ENTER).start()
        } else {
            view.tag = HIDING
            view.animate().alpha(0f).setDuration(TRANSITION_MS * 2 / 3).setInterpolator(EXIT).withEndAction {
                if (view.tag === HIDING) { view.visibility = GONE; view.alpha = 1f; view.tag = null }
            }.start()
        }
    }

    /** Opaque cards over both panes while resizing, so stretched app frames are never shown. */
    private fun buildPreviews() {
        val ws = host.workspace
        val tasks = host.tasks().associateBy { it.id }
        previews.forEachIndexed { index, view ->
            view.removeAllViews()
            view.background = card(colors.card, 12)
            val task = tasks[if (index == 0) ws.primary else ws.secondary]
            val box = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
            }
            box.addView(Tap(task?.label ?: "", shape = dp(48), iconPx = dp(44), circle = false, action = {}).apply {
                drawable = task?.icon?.let(::copyIcon)
                if (drawable == null) glyph = if (task == null) Glyph.PLUS else Glyph.WINDOW
                isClickable = false
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(48), dp(48)))
            box.addView(label(task?.label ?: tr("アプリを選ぶ", "Choose an app"), 14f, bold = true).apply {
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(8) })
            val percent = previewPercent[index]
            (percent.parent as? LinearLayout)?.removeView(percent)
            percent.setTextSize(TypedValue.COMPLEX_UNIT_PX, 22f * unit)
            percent.setTextColor(colors.accent)
            percent.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            box.addView(percent, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(4) })
            view.addView(box, LayoutParams(-1, -1))
        }
    }

    /** Bumped by every drag and release, so a release's delayed fade cannot hide a newer drag's cards. */
    private var previewGeneration = 0

    private fun previewResize(ratio: Float) {
        if (previewRatio == null) {
            previewGeneration++
            (previews.toList() + previewBackdrop).forEach { it.animate().cancel(); it.alpha = 1f }
            previewHoldUntil = 0L
            buildPreviews()
            previewRatio = ratio.coerceIn(.3f, .7f)
            refreshChrome()
            // The cards come up quickly over the panes as the drag starts.
            (previews.toList() + previewBackdrop).forEach {
                it.alpha = 0f
                it.animate().alpha(1f).setDuration(TRANSITION_MS / 2).setInterpolator(ENTER).start()
            }
            previews.forEach { card ->
                card.scaleX = .97f; card.scaleY = .97f
                card.animate().scaleX(1f).scaleY(1f).setDuration(TRANSITION_MS).setInterpolator(ENTER).start()
            }
            return
        }
        previewRatio = ratio.coerceIn(.3f, .7f)
        refreshChrome()
    }

    /** Applies the dragged ratio once; the cards stay briefly while the applications redraw. */
    private fun commitResize() {
        val ratio = previewRatio ?: return
        previewRatio = null
        previewHoldUntil = SystemClock.uptimeMillis() + RESIZE_SETTLE_MS
        host.resizeAuto(ratio, true)
        val generation = ++previewGeneration
        postDelayed({
            if (generation != previewGeneration) return@postDelayed
            (previews.toList() + previewBackdrop).forEach { it.animate().alpha(0f).setDuration(TRANSITION_MS).setInterpolator(EXIT).start() }
            postDelayed({
                if (generation != previewGeneration) return@postDelayed
                previewHoldUntil = 0L
                refreshChrome()
                (previews.toList() + previewBackdrop).forEach { it.alpha = 1f }
            }, TRANSITION_MS)
        }, RESIZE_SETTLE_MS)
    }

    /** What the rail showed last time, so a rebuild only animates what changed. */
    private var railBuilt = false
    private var railSelectedBefore = emptySet<String>()
    private val railSelectedNow = HashSet<String>()
    private var railTasksBefore = emptySet<Int>()

    private fun buildRail(g: Geometry) {
        rail.removeAllViews()
        railSelectedNow.clear()
        rail.orientation = if (g.landscape) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        rail.gravity = Gravity.CENTER
        rail.setBackgroundColor(colors.rail)
        val pad = dp(4)
        if (g.landscape) rail.setPadding(0, pad, 0, pad) else rail.setPadding(pad, 0, pad, 0)
        val cell = dp(TOUCH)
        fun add(view: View, size: Int = cell, weight: Float = 0f) {
            rail.addView(view, if (g.landscape) LinearLayout.LayoutParams(-1, size, weight)
                else LinearLayout.LayoutParams(size, -1, weight))
        }
        val ws = host.workspace
        val pageOpen = page != null

        status.setOnClickListener { showPage("settings") }
        status.contentDescription = tray?.let(::statusDescription) ?: tr("時刻と状態。押すと設定", "Time and status. Opens settings")
        (status.parent as? LinearLayout)?.removeView(status)
        add(status, if (g.landscape) dp(STATUS) else dp(STATUS + 16))

        add(railTap(Glyph.HOME, tr("ホーム。もう一度押すとアプリ一覧", "Home. Press again for all apps"),
            selected = pageOpen && (pageKind == "home" || pageKind == "apps"), key = "home") {
            if (page != null && pageKind == "home") showPage("apps") else showPage("home")
        })

        // Applications in a stable order so their places do not move under the driver's finger.
        val length = if (g.landscape) g.rail.height() else g.rail.width()
        val fixed = (if (g.landscape) dp(STATUS) else dp(STATUS + 16)) + cell * 3 + pad * 2
        val slots = ((length - fixed) / cell).coerceAtLeast(0)
        val tasks = host.tasks()
        val chosen = if (tasks.size <= slots) tasks else {
            val keep = (slots - 1).coerceAtLeast(0)
            (tasks.filter { ws.visible(it.id) } + tasks.filterNot { ws.visible(it.id) }).take(keep)
        }
        for (task in chosen.sortedBy { it.id }) {
            val tap = taskTap(task, pageOpen)
            add(tap)
            if (railBuilt && task.id !in railTasksBefore) {
                // A newly opened application pops into its place.
                tap.alpha = 0f; tap.scaleX = .4f; tap.scaleY = .4f
                tap.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(TRANSITION_MS + 120)
                    .setInterpolator(SPRING).start()
            }
        }
        if (tasks.size > chosen.size && slots > 0) {
            add(Tap(tr("ほかの使用中アプリ ${tasks.size - chosen.size}件", "${tasks.size - chosen.size} more running apps"),
                shape = dp(44), iconPx = dp(18), circle = false, action = { showPage("tasks") }).apply {
                text = "+${tasks.size - chosen.size}"
                tint = colors.secondary
                markSelected(this, "tasks", pageOpen && pageKind == "tasks")
            })
        }
        add(View(context), 0, 1f)

        add(railTap(Glyph.TILE, tr("画面: 1画面・2画面、拡大率、全画面表示、すべて閉じる", "Screen: split, zoom, full screen and close all"),
            selected = ws.split || (page != null && pageKind == "display"), key = "display") { showPage("display") })
        add(railTap(Glyph.BACK, tr("戻る", "Back")) {
            if (page != null && host.workspace.primary != null) hidePage() else host.autoBack()
        })
        railSelectedBefore = railSelectedNow.toSet()
        railTasksBefore = tasks.map { it.id }.toSet()
        railBuilt = true
    }

    /** Fills a selected rail item; one that was not selected before fades its highlight in. */
    private fun markSelected(tap: Tap, key: String, selected: Boolean) {
        tap.fill = if (selected) colors.accentSoft else 0
        if (!selected) return
        railSelectedNow += key
        if (railBuilt && key !in railSelectedBefore) tap.fadeFillIn()
    }

    private fun railTap(glyph: Glyph, description: String, selected: Boolean = false, key: String? = null, action: () -> Unit) =
        Tap(description, shape = dp(44), iconPx = dp(24), circle = false, action = action).apply {
            this.glyph = glyph
            tint = if (selected) colors.accent else colors.text
            markSelected(this, key ?: glyph.name, selected)
        }

    private fun taskTap(task: AutoTask, pageOpen: Boolean): Tap {
        val ws = host.workspace
        val visible = ws.visible(task.id)
        val other = if (ws.split && ws.secondary == task.id) 0 else 1
        var tap: Tap? = null
        return Tap(
            if (visible && !pageOpen) tr("${task.label}。表示中。押すとメニュー", "${task.label}. On screen. Opens its menu")
            else tr("${task.label}。長押しでもう一方の画面に表示", "${task.label}. Long press to show on the other side"),
            shape = dp(44), iconPx = dp(30), circle = false,
            action = {
                val anchor = tap
                if (page == null && ws.visible(task.id) && anchor != null) showAppBubble(task, anchor)
                else {
                    host.selectAuto(task.id)
                    hidePage()
                }
            },
            long = {
                if (!ws.split) host.splitAuto()
                host.focusAuto(other)
                host.selectAuto(task.id)
                hidePage()
            },
        ).apply {
            drawable = task.icon?.let(::copyIcon)
            if (drawable == null) glyph = Glyph.WINDOW
            markSelected(this, "task:${task.id}", !pageOpen && ws.active() == task.id)
            dot = visible
            tap = this
        }
    }

    private fun buildEmptyPane(g: Geometry) {
        emptyPane.removeAllViews()
        emptyPane.background = card(colors.card, 12)
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        box.addView(Tap(tr("${paneName(1, g.landscape)}の画面にアプリを表示", "Choose an app for the ${paneName(1, g.landscape)} side"),
            shape = dp(56), iconPx = dp(28), action = { showPage("apps", 1) }).apply {
            glyph = Glyph.PLUS
            fill = colors.accent
            tint = colors.onAccent
        }, LinearLayout.LayoutParams(dp(64), dp(64)))
        box.addView(label(tr("アプリを選ぶ", "Choose an app"), 14f, colors.secondary).apply {
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(6) })
        emptyPane.addView(box, LayoutParams(-1, -1))
        pressable(emptyPane, tr("アプリを選ぶ", "Choose an app"), 12) { showPage("apps", 1) }
    }

    fun updateStatus(mode: String, media: PlasmaSystemControls.MediaInfo?, artwork: android.graphics.Bitmap? = null) {
        this.mode = mode
        this.media = media
        this.artwork = artwork
        status.invalidate()
        bindMedia()
    }

    fun updateTray(tray: TrayState) {
        this.tray = tray
        status.contentDescription = statusDescription(tray)
        status.invalidate()
        volumeBar?.animateTo(if (tray.muted) 0f else tray.volume)
    }

    private fun statusDescription(t: TrayState): String {
        val battery = percentText(t.battery) + when {
            t.charging -> tr("、充電中", ", charging")
            t.batterySaver -> tr("、省電力", ", battery saver")
            else -> ""
        }
        val network = when {
            t.wifiConnected -> "Wi-Fi"
            t.networkGlyph == Glyph.ETHERNET -> "Ethernet"
            t.cellularVisible && !t.cellularNoService && t.networkConnected -> t.cellularType ?: tr("モバイル", "Mobile")
            else -> tr("未接続", "Offline")
        }
        return tr("電池 $battery、$network。押すと設定", "Battery $battery, $network. Opens settings")
    }

    fun updateTasks() {
        // Icons count too: Linux windows learn theirs after they open.
        val signature = host.tasks().joinToString { "${it.id}:${it.label}:${System.identityHashCode(it.icon)}" }
        if (signature == lastTaskSignature) return
        lastTaskSignature = signature
        if (bubbleTask != null && host.tasks().none { it.id == bubbleTask }) dismissBubble()
        refreshChrome()
        if (page != null && pageKind == "tasks") showPage("tasks")
    }
    // endregion

    // region pages
    fun hidePage() {
        dismissBubble()
        if (host.workspace.primary == null) {
            if (page == null || pageKind != "home") showHome()
            return
        }
        page?.let { animateOut(it, if (pageKind == "display") Motion.SHEET else Motion.RISE) }
        page = null
        pageKind = ""
        pickingPane = null
        clearLiveViews()
        refreshChrome()
        host.resumeAutoInput()
    }

    fun showHome() = showPage("home")

    /** Rebuilds the open page, for example after the application list or theme changed. */
    fun refreshPage() {
        if (page != null && pageKind != "display" && !pageKind.startsWith("qs:")) showPage(pageKind, pickingPane) else refreshChrome()
    }

    fun showPage(kind: String, pane: Int? = null) {
        dismissBubble()
        val previous = page
        val previousKind = if (previous != null) pageKind else null
        if (previousKind != kind) android.util.Log.d("DextopAutoInput", "page $previousKind -> $kind size=${width}x$height")
        val motion = if (previous == null) motionFor(null, kind)
            else if (previousKind == kind && pickingPane == pane) Motion.NONE else motionFor(previousKind, kind)
        previous?.let { if (motion == Motion.NONE) removeView(it) else animateOut(it, motion) }
        page = null
        clearLiveViews()
        pageKind = kind
        pickingPane = pane
        if (width <= 0 || height <= 0) return
        if (immersive) {
            // Pages need the rail to leave them again.
            fullscreen = false
            prefs.edit().putBoolean("fullscreen", false).apply()
            railSignature = ""
            host.relayoutAuto()
        }
        syncColors()
        val g = geometry()
        val area = pageBounds(g)
        val view = when (kind) {
            "apps" -> appsPage(area, pane, g.landscape)
            "favorites" -> appsPage(area, null, g.landscape, pickFavorites = true)
            "tasks" -> tasksPage(area)
            "settings" -> settingsPage(area)
            "qs:network" -> networkPage()
            "qs:sound" -> soundPage()
            "qs:battery" -> batteryPage()
            "qs:notifications" -> notificationsPage()
            "qs:controllers" -> controllersPage()
            "display" -> displaySheet(area, g.landscape)
            else -> homePage(area)
        }
        page = view
        addView(view)
        place(view, area)
        refreshChrome()
        animateIn(view, motion)
        if (motion == Motion.RISE || motion == Motion.FORWARD || motion == Motion.BACK) stagger(view)
    }

    private fun clearLiveViews() {
        mediaTitle = null
        mediaArtist = null
        mediaSource = null
        mediaArt = null
        playButton = null
        volumeBar = null
    }

    /** A page covers the whole surface beside the rail in the shell's own background, so it has no edges. */
    private fun pageFrame(content: View, scroll: Boolean): View {
        val inner = if (scroll) ScrollView(context).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(content)
        } else content
        return FrameLayout(context).apply {
            setBackgroundColor(colors.background)
            isClickable = true
            val inset = dp(INSET)
            setPadding(inset, inset, inset, inset)
            clipToPadding = false
            addView(inner, LayoutParams(-1, -1))
        }
    }

    private enum class Motion { RISE, FORWARD, BACK, SHEET, NONE }

    private fun motionFor(from: String?, to: String): Motion = when {
        from == to -> Motion.NONE
        to == "display" -> Motion.SHEET
        to.startsWith("qs:") && (from == "settings" || from?.startsWith("qs:") == true) -> Motion.FORWARD
        from?.startsWith("qs:") == true && to == "settings" -> Motion.BACK
        else -> Motion.RISE
    }

    private fun animateIn(view: View, motion: Motion) {
        val shift = dpf(14f)
        when (motion) {
            Motion.NONE -> return
            Motion.FORWARD -> { view.alpha = 0f; view.translationX = shift * 2 }
            Motion.BACK -> { view.alpha = 0f; view.translationX = -shift * 2 }
            Motion.RISE -> { view.alpha = 0f; view.translationY = shift; view.scaleX = .985f; view.scaleY = .985f }
            Motion.SHEET -> {
                // The scrim fades while the sheet rises from just below and grows into place.
                view.alpha = 0f
                (view as? ViewGroup)?.getChildAt(0)?.let { sheet ->
                    sheet.scaleX = .92f; sheet.scaleY = .92f; sheet.translationY = dpf(28f)
                    sheet.animate().scaleX(1f).scaleY(1f).translationY(0f).setDuration(TRANSITION_MS + 100)
                        .setInterpolator(ENTER).start()
                }
            }
        }
        view.animate().alpha(1f).translationX(0f).translationY(0f).scaleX(1f).scaleY(1f)
            .setDuration(TRANSITION_MS + 60).setInterpolator(ENTER).start()
    }

    /** Fades a page out while the next one comes in; it takes no input meanwhile. */
    private fun animateOut(view: View, motion: Motion) {
        view.isEnabled = false
        view.setOnTouchListener { _, _ -> true }
        val shift = dpf(14f)
        val animator = view.animate().alpha(0f).setDuration(TRANSITION_MS * 2 / 3).setInterpolator(EXIT)
        when (motion) {
            Motion.FORWARD -> animator.translationX(-shift * 2)
            Motion.BACK -> animator.translationX(shift * 2)
            Motion.SHEET -> (view as? ViewGroup)?.getChildAt(0)?.animate()?.scaleX(.94f)?.scaleY(.94f)
                ?.translationY(dpf(16f))?.setDuration(TRANSITION_MS * 2 / 3)?.setInterpolator(EXIT)?.start()
            else -> animator.translationY(shift / 2).scaleX(.99f).scaleY(.99f)
        }
        animator.withEndAction { removeView(view) }.start()
    }

    /** Brings a new page's blocks in one after another, so the eye follows the layout. */
    private fun stagger(page: View) {
        var root = (page as? ViewGroup)?.getChildAt(0)
        if (root is ScrollView) root = root.getChildAt(0)
        val targets = ArrayList<View>()
        fun collect(view: View, depth: Int) {
            if (targets.size >= STAGGER_MAX || view.visibility != VISIBLE) return
            // Plain containers are opened up; cards, controls and text move as one block.
            val container = view is GridLayout || (view is ViewGroup && view !is ScrollView &&
                view.background == null && !view.isClickable && depth < 3)
            if (container && view is ViewGroup) {
                for (index in 0 until view.childCount) collect(view.getChildAt(index), depth + 1)
            } else targets += view
        }
        val group = root as? ViewGroup ?: return
        for (index in 0 until group.childCount) collect(group.getChildAt(index), 1)
        val shift = dpf(10f)
        targets.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = shift
            view.animate().alpha(1f).translationY(0f).setStartDelay(40L + index * STAGGER_MS)
                .setDuration(TRANSITION_MS + 60).setInterpolator(ENTER)
                .withEndAction { view.animate().startDelay = 0L }.start()
        }
    }

    private fun header(title: String, trailing: View? = null): LinearLayout = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(label(title, 18f, bold = true), LinearLayout.LayoutParams(0, -2, 1f))
        trailing?.let { addView(it, LinearLayout.LayoutParams(-2, dp(MIN_TOUCH))) }
    }

    private fun homePage(area: Rect): View {
        val gap = dp(GAP)
        val wide = area.width() >= area.height() * 1.3f
        val body = LinearLayout(context).apply {
            orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        }
        val side = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val sideWidth = if (wide) ((area.width() - gap) / 2.1f).roundToInt() else area.width()

        val shortcuts = LinearLayout(context)
        val maps = resolveMaps()
        val music = resolveMusic()
        fun shortcut(title: String, app: PlasmaApp?, glyph: Glyph, filter: String, index: Int) {
            val tile = tile(title, app?.label ?: tr("アプリを選ぶ", "Choose an app"),
                app?.icon?.let(::copyIcon), glyph,
                action = { if (app != null) open(app.packageName, null) else showApps(filter) },
                long = { showApps(filter) })
            shortcuts.addView(tile, LinearLayout.LayoutParams(0, -1, 1f).apply { if (index > 0) leftMargin = gap })
        }
        shortcut(tr("地図", "Maps"), maps, Glyph.NETWORK, "maps", 0)
        shortcut(tr("音楽", "Music"), music, Glyph.MUSIC, "audio", 1)
        shortcuts.addView(tile(tr("アプリ", "Apps"), tr("すべて表示", "Show all"), null, Glyph.APPS,
            action = { showApps("all") }), LinearLayout.LayoutParams(0, -1, 1f).apply { leftMargin = gap })

        val favorites = favoritesCard(sideWidth)
        if (wide) {
            side.addView(shortcuts, LinearLayout.LayoutParams(-1, 0, 1f))
            side.addView(favorites, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = gap })
            body.addView(mediaCard(), LinearLayout.LayoutParams(0, -1, 1.1f))
            body.addView(side, LinearLayout.LayoutParams(0, -1, 1f).apply { leftMargin = gap })
            return pageFrame(body, scroll = false)
        }
        side.addView(shortcuts, LinearLayout.LayoutParams(-1, dp(92)))
        side.addView(favorites, LinearLayout.LayoutParams(-1, dp(112)).apply { topMargin = gap })
        body.addView(mediaCard(), LinearLayout.LayoutParams(-1, dp(220)))
        body.addView(side, LinearLayout.LayoutParams(-1, -2).apply { topMargin = gap })
        return pageFrame(body, scroll = true)
    }

    private fun mediaCard(): View {
        // The artwork fills the card faintly behind the controls; the card's rounded outline clips it.
        val frame = FrameLayout(context).apply {
            background = card(colors.card, CORNER)
            clipToOutline = true
        }
        val art = android.widget.ImageView(context).apply {
            scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            alpha = 0f
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        frame.addView(art, LayoutParams(-1, -1))
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(12), dp(8))
        }
        frame.addView(box, LayoutParams(-1, -1))
        val source = label("", 12f, colors.secondary)
        val title = label("", 20f, bold = true, lines = 2)
        val artist = label("", 14f, colors.secondary)
        val info = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(source, LinearLayout.LayoutParams(-1, -2))
            addView(View(context), LinearLayout.LayoutParams(-1, 0, 1f))
            addView(title, LinearLayout.LayoutParams(-1, -2))
            addView(artist, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
            addView(View(context), LinearLayout.LayoutParams(-1, 0, 1f))
        }
        pressable(info, tr("再生中のアプリを開く", "Open the playing app"), 12) {
            val target = media?.packageName ?: resolveMusic()?.packageName
            if (target != null) open(target, null) else showApps("audio")
        }
        box.addView(info, LinearLayout.LayoutParams(-1, 0, 1f))

        val controls = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        fun control(glyph: Glyph, description: String, key: Int, primary: Boolean = false): Tap =
            Tap(description, shape = dp(if (primary) 56 else 48), iconPx = dp(if (primary) 30 else 28),
                action = { host.autoMedia(key) }).apply {
                this.glyph = glyph
                if (primary) { fill = colors.accent; tint = colors.onAccent }
            }
        controls.addView(control(Glyph.SKIP_PREVIOUS, tr("前の曲", "Previous"), KeyEvent.KEYCODE_MEDIA_PREVIOUS),
            LinearLayout.LayoutParams(dp(TOUCH), dp(60)))
        val play = control(Glyph.PLAY, tr("再生／一時停止", "Play or pause"), KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, primary = true)
        controls.addView(play, LinearLayout.LayoutParams(dp(64), dp(60)))
        controls.addView(control(Glyph.SKIP_NEXT, tr("次の曲", "Next"), KeyEvent.KEYCODE_MEDIA_NEXT),
            LinearLayout.LayoutParams(dp(TOUCH), dp(60)))
        controls.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        fun volume(text: String, description: String, direction: Int) =
            Tap(description, shape = dp(44), iconPx = dp(22), action = { host.autoVolume(direction) }).apply {
                this.text = text
                fill = colors.raised
            }
        controls.addView(volume("−", tr("音量を下げる", "Volume down"), AudioManager.ADJUST_LOWER),
            LinearLayout.LayoutParams(dp(TOUCH), dp(60)))
        val bar = LevelBar()
        controls.addView(bar, LinearLayout.LayoutParams(dp(44), dp(60)))
        controls.addView(volume("+", tr("音量を上げる", "Volume up"), AudioManager.ADJUST_RAISE),
            LinearLayout.LayoutParams(dp(TOUCH), dp(60)))
        box.addView(controls, LinearLayout.LayoutParams(-1, dp(60)))

        mediaTitle = title
        mediaArtist = artist
        mediaSource = source
        playButton = play
        volumeBar = bar
        mediaArt = art
        bar.level = tray?.let { if (it.muted) 0f else it.volume } ?: 0f
        bindMedia()
        return frame
    }

    private fun bindMedia() {
        val info = media
        val app = info?.let { m -> host.applications().firstOrNull { it.packageName == m.packageName } }
        swapText(mediaSource, app?.label ?: tr("メディア", "Media"))
        swapText(mediaTitle, info?.title ?: tr("再生していません", "Nothing playing"))
        swapText(mediaArtist, info?.artist ?: if (info == null) tr("押すと音楽アプリを開きます", "Tap to open a music app") else "", delay = 40L)
        playButton?.let { play ->
            val glyph = if (info?.playing == true) Glyph.PAUSE else Glyph.PLAY
            if (play.glyph != glyph) {
                val first = play.glyph == null
                play.glyph = glyph
                if (!first && play.isAttachedToWindow) {
                    // Play and pause trade places with a small bounce.
                    play.animate().cancel()
                    play.scaleX = .8f; play.scaleY = .8f
                    play.animate().scaleX(1f).scaleY(1f).setStartDelay(0L).setDuration(TRANSITION_MS + 80).setInterpolator(SPRING).start()
                }
            }
        }
        mediaArt?.let { art ->
            val bitmap = artwork?.takeIf { info != null }
            val target = if (bitmap == null) 0f else if (colors.dark) .28f else .22f
            if (bitmap != null && art.tag !== bitmap) {
                art.tag = bitmap
                art.setImageBitmap(bitmap)
                art.alpha = 0f
            }
            if (art.alpha != target) art.animate().alpha(target).setDuration(TRANSITION_MS * 2).start()
        }
    }

    /** Replaces a text by sliding the old one up and out and the new one up and in. */
    private fun swapText(view: TextView?, value: CharSequence, delay: Long = 0L) {
        view ?: return
        if (view.text.toString() == value.toString()) return
        if (view.text.isNullOrEmpty() || !view.isAttachedToWindow) { view.text = value; return }
        val shift = dpf(6f)
        view.animate().cancel()
        view.animate().alpha(0f).translationY(-shift).setStartDelay(delay).setDuration(TRANSITION_MS / 2)
            .setInterpolator(EXIT).withEndAction {
                view.text = value
                view.translationY = shift
                view.animate().alpha(1f).translationY(0f).setStartDelay(0L).setDuration(TRANSITION_MS + 40)
                    .setInterpolator(ENTER).start()
            }.start()
    }

    private fun favoritesCard(width: Int): View {
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = card(colors.card, 16)
            setPadding(dp(12), dp(8), dp(8), dp(4))
        }
        box.addView(label(tr("お気に入り", "Favorites"), 12f, colors.secondary), LinearLayout.LayoutParams(-1, -2))
        val apps = host.applications().filter { it.packageName in favorites() }.sortedBy { it.label.lowercase() }
        val row = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        fun addCell() = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(Tap(tr("お気に入りを追加", "Add favourites"), shape = dp(40), iconPx = dp(22), action = {}).apply {
                glyph = Glyph.PLUS
                fill = colors.raised
                isClickable = false
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(44), dp(44)))
            addView(label(tr("追加", "Add"), 11f, colors.secondary).apply { gravity = Gravity.CENTER },
                LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(5) })
            pressable(this, tr("お気に入りを追加", "Add favourites"), 14) { showPage("favorites") }
        }
        if (apps.isEmpty()) {
            row.addView(label(tr("＋からアプリを追加できます", "Add apps with +"),
                13f, colors.secondary, lines = 2), LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(addCell(), LinearLayout.LayoutParams(dp(68), -1))
        } else {
            // The last place is always the + cell.
            val count = ((width - dp(20)) / dp(68)).coerceAtLeast(2)
            val shown = apps.take(count - 1)
            for (app in shown) {
                row.addView(appCell(app, iconSize = 34, textSize = 11f) { open(app.packageName, null) },
                    LinearLayout.LayoutParams(0, -1, 1f))
            }
            row.addView(addCell(), LinearLayout.LayoutParams(0, -1, 1f))
            repeat(count - 1 - shown.size) { row.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f)) }
        }
        box.addView(row, LinearLayout.LayoutParams(-1, 0, 1f))
        return box
    }

    /** All applications; with [pickFavorites] a tap adds or removes a favourite instead of opening it. */
    private fun appsPage(area: Rect, pane: Int?, landscape: Boolean, pickFavorites: Boolean = false): View {
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(2), dp(4), dp(8))
        }
        val title = when {
            pickFavorites -> tr("お気に入りを選ぶ", "Choose favourites")
            pane != null && host.workspace.split -> tr("${paneName(pane, landscape)}に表示", "Show on the ${paneName(pane, landscape)}")
            else -> tr("アプリ", "Apps")
        }
        val hasLinux = host.applications().any { it.category == LINUX_APP_CATEGORY }
        if (!hasLinux && filter == "linux") filter = "all"
        val chips = segmented(listOfNotNull(
            "all" to tr("すべて", "All"), "favorites" to tr("お気に入り", "Favorites"),
            "maps" to tr("地図", "Maps"), "audio" to tr("音楽", "Music"),
            ("linux" to "Linux").takeIf { hasLinux },
        ), filter) { filter = it; showPage(pageKind, pickingPane) }
        // The categories sit in the middle of the page; the title keeps its place on the left.
        val centred = { LinearLayout.LayoutParams(-2, dp(MIN_TOUCH)).apply { gravity = Gravity.CENTER_HORIZONTAL } }
        if (pickFavorites) {
            body.addView(header(title, textButton(tr("完了", "Done")) { showHome() }), LinearLayout.LayoutParams(-1, dp(56)))
            body.addView(chips, centred())
        } else if (area.width() >= dp(560)) {
            val row = FrameLayout(context)
            row.addView(label(title, 18f, bold = true).apply { gravity = Gravity.CENTER_VERTICAL },
                LayoutParams(-2, -1, Gravity.START or Gravity.CENTER_VERTICAL))
            row.addView(chips, LayoutParams(-2, dp(MIN_TOUCH), Gravity.CENTER))
            body.addView(row, LinearLayout.LayoutParams(-1, dp(56)))
        } else {
            body.addView(header(title), LinearLayout.LayoutParams(-1, dp(36)))
            body.addView(chips, centred())
        }
        val phone = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        body.addView(phone, LinearLayout.LayoutParams(-1, -2))
        if (!pickFavorites) host.autoLoad(phone, { host.autoPhoneTasks() }) { tasks ->
            if (tasks.isEmpty()) return@autoLoad
            val known = host.applications().associateBy { it.packageName }
            val row = LinearLayout(context)
            val count = ((area.width() - dp(8)) / dp(88)).coerceIn(2, 9)
            for (task in tasks.take(count)) {
                val app = known[task.packageName] ?: continue
                row.addView(appCell(app.copy(label = task.label?.takeIf { it.isNotBlank() } ?: app.label), iconSize = 42, textSize = 12f) {
                    host.autoAdoptPhoneTask(task, pane)
                    hidePage()
                }, LinearLayout.LayoutParams(0, dp(92), 1f))
            }
            if (row.childCount == 0) return@autoLoad
            repeat(count - row.childCount) { row.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f)) }
            phone.addView(label(tr("スマホで起動中", "Running on the phone"), 13f, colors.secondary, bold = true).apply {
                setPadding(dp(6), dp(8), 0, dp(2))
            })
            phone.addView(row, LinearLayout.LayoutParams(-1, -2))
        }
        val favorites = favorites()
        val apps = host.applications().filter { app ->
            when (filter) {
                "favorites" -> app.packageName in favorites
                "maps" -> isMaps(app)
                "audio" -> isMusic(app)
                "linux" -> app.category == LINUX_APP_CATEGORY
                else -> true
            }
        }.sortedBy { it.label.lowercase() }
        if (apps.isEmpty()) {
            body.addView(label(if (filter == "favorites") tr("アプリを長押しするとお気に入りに追加できます", "Long press an app to add it to favorites")
                else tr("該当するアプリはありません", "No matching apps"), 14f, colors.secondary, lines = 2).apply {
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(-1, dp(120)))
            return pageFrame(body, scroll = true)
        }
        val columns = ((area.width() - dp(8)) / dp(88)).coerceIn(2, 9)
        val grid = GridLayout(context).apply { columnCount = columns }
        for (app in apps) {
            val cell = if (pickFavorites) appCell(app, iconSize = 42, textSize = 12f, favorite = app.packageName in favorites) {
                toggleFavorite(app.packageName)
            } else appCell(app, iconSize = 42, textSize = 12f, favorite = app.packageName in favorites,
                long = { toggleFavorite(app.packageName) }) { open(app.packageName, pane) }
            grid.addView(cell, GridLayout.LayoutParams().apply {
                width = 0
                height = dp(92)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            })
        }
        body.addView(grid, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        return pageFrame(body, scroll = true)
    }

    private fun tasksPage(area: Rect): View {
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(2), dp(4), dp(8))
        }
        val tasks = host.tasks()
        val rows = ArrayList<View>()
        val closeAll = if (tasks.isEmpty()) null else textButton(tr("すべて閉じる", "Close all")) {
            rows.forEachIndexed { index, row -> leave(row, index * 40L) }
            postDelayed({ closeAllApps(tasks) }, TRANSITION_MS)
        }
        body.addView(header(tr("使用中のアプリ", "Running apps"), closeAll), LinearLayout.LayoutParams(-1, dp(56)))
        if (tasks.isEmpty()) {
            body.addView(label(tr("使用中のアプリはありません", "No apps are running"), 14f, colors.secondary).apply {
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(-1, dp(120)))
            return pageFrame(body, scroll = true)
        }
        val columns = (area.width() / dp(240)).coerceIn(1, 4)
        val grid = GridLayout(context).apply { columnCount = columns }
        for (task in tasks.sortedBy { it.id }) {
            val visible = host.workspace.visible(task.id)
            val row = LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                background = card(if (visible) colors.raised else colors.card, 14)
                setPadding(dp(12), 0, dp(2), 0)
            }
            val icon = Tap(task.label, shape = dp(36), iconPx = dp(34), circle = false, action = {}).apply {
                drawable = task.icon?.let(::copyIcon)
                if (drawable == null) glyph = Glyph.WINDOW
                isClickable = false
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            row.addView(icon, LinearLayout.LayoutParams(dp(36), dp(36)))
            val text = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), 0, dp(4), 0)
                addView(label(task.label, 14f, bold = true))
                addView(label(if (visible) tr("表示中", "On screen") else tr("バックグラウンド", "In background"), 11f, colors.secondary))
            }
            row.addView(text, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(Tap(tr("${task.label}を閉じる", "Close ${task.label}"), shape = dp(36), iconPx = dp(20),
                action = { leave(row) { host.closeAuto(task.id) } }).apply { glyph = Glyph.CLOSE; fill = colors.background },
                LinearLayout.LayoutParams(dp(TOUCH), dp(TOUCH)))
            rows += row
            pressable(row, task.label, 14) { host.selectAuto(task.id); hidePage() }
            grid.addView(row, GridLayout.LayoutParams().apply {
                width = 0
                height = dp(64)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(4), dp(4), dp(4), dp(4))
            })
        }
        body.addView(grid, LinearLayout.LayoutParams(-1, -2))
        return pageFrame(body, scroll = true)
    }

    /** Shrinks and fades a card away, then runs [done]. */
    private fun leave(view: View, delay: Long = 0L, done: (() -> Unit)? = null) {
        view.isEnabled = false
        view.animate().cancel()
        view.animate().alpha(0f).scaleX(.9f).scaleY(.9f).setStartDelay(delay).setDuration(TRANSITION_MS * 3 / 4)
            .setInterpolator(EXIT).withEndAction { done?.invoke() }.start()
    }

    /** Closes [tasks] one shortly after another, so their panes fold away in turn. */
    private fun closeAllApps(tasks: List<AutoTask> = host.tasks()) {
        val visible = tasks.filter { host.workspace.visible(it.id) }
        val order = tasks.filterNot { host.workspace.visible(it.id) } + visible
        order.forEachIndexed { index, task -> postDelayed({ host.closeAuto(task.id) }, index * 70L) }
    }

    // region settings and quick controls
    private fun column(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(4), dp(2), dp(4), dp(12))
    }

    private fun section(body: LinearLayout, title: String, trailing: View? = null) {
        val row = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL or Gravity.BOTTOM }
        row.addView(label(title, 13f, colors.secondary, bold = true).apply { setPadding(dp(6), 0, 0, dp(6)) },
            LinearLayout.LayoutParams(0, -2, 1f))
        trailing?.let { row.addView(it, LinearLayout.LayoutParams(-2, dp(MIN_TOUCH))) }
        body.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
    }

    /** A rounded group of rows separated by hairlines. */
    private fun group(body: LinearLayout, rows: List<View>) {
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = card(colors.card, CORNER)
            clipToOutline = true
        }
        rows.forEachIndexed { index, row ->
            if (index > 0) box.addView(View(context).apply { setBackgroundColor(colors.separator) },
                LinearLayout.LayoutParams(-1, 1).apply { leftMargin = dp(16) })
            box.addView(row, LinearLayout.LayoutParams(-1, -2))
        }
        body.addView(box, LinearLayout.LayoutParams(-1, -2))
    }

    /** Title and value on the left, a control or a chevron on the right; at least one touch target tall. */
    private fun settingRow(
        title: String,
        value: String? = null,
        glyph: Glyph? = null,
        glyphLevel: Float = 1f,
        control: View? = null,
        enabled: Boolean = true,
        action: (() -> Unit)? = null,
    ): View {
        val row = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(56)
            setPadding(dp(16), dp(6), dp(10), dp(6))
            alpha = if (enabled) 1f else .45f
        }
        if (glyph != null) row.addView(Tap(title, shape = dp(28), iconPx = dp(22), circle = false, action = {}).apply {
            this.glyph = glyph
            tint = colors.accent
            isClickable = false
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            setGlyphLevel(glyphLevel)
        }, LinearLayout.LayoutParams(dp(28), dp(28)).apply { rightMargin = dp(12) })
        val text = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        text.addView(label(title, 15f))
        if (!value.isNullOrEmpty()) text.addView(label(value, 12f, colors.secondary, lines = 2),
            LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(2) })
        row.addView(text, LinearLayout.LayoutParams(0, -2, 1f))
        if (control != null) row.addView(control, LinearLayout.LayoutParams(-2, dp(MIN_TOUCH)).apply { leftMargin = dp(8) })
        else if (action != null) row.addView(Tap("", shape = dp(24), iconPx = dp(18), circle = false, action = {}).apply {
            this.glyph = Glyph.CHEVRON_RIGHT
            tint = colors.secondary
            isClickable = false
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(24), dp(24)))
        if (action != null && enabled) pressable(row, if (value.isNullOrEmpty()) title else "$title, $value", 0, action = action)
        return row
    }

    /** Header of a quick-control page, with a way back to the settings. */
    private fun subHeader(body: LinearLayout, title: String, trailing: View? = null) {
        val row = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(Tap(tr("設定に戻る", "Back to settings"), shape = dp(40), iconPx = dp(22), action = { showPage("settings") }).apply {
            glyph = Glyph.BACK
            fill = colors.card
        }, LinearLayout.LayoutParams(dp(MIN_TOUCH), dp(MIN_TOUCH)))
        row.addView(label(title, 18f, bold = true).apply { setPadding(dp(8), 0, 0, 0) }, LinearLayout.LayoutParams(0, -2, 1f))
        trailing?.let { row.addView(it, LinearLayout.LayoutParams(-2, dp(MIN_TOUCH))) }
        body.addView(row, LinearLayout.LayoutParams(-1, dp(56)))
    }

    private fun loadingRow() = label(tr("読み込み中…", "Loading…"), 13f, colors.secondary).apply {
        gravity = Gravity.CENTER
        setPadding(0, dp(18), 0, dp(18))
    }

    private fun settingsPage(area: Rect): View {
        val gap = dp(GAP)
        val body = column()
        val source = when (mode.lowercase()) {
            "" -> ""
            "driving" -> tr("接続元: Driving", "Connected from Driving")
            "parking" -> tr("接続元: Parking", "Connected from Parking")
            else -> tr("接続元: $mode", "Connected from $mode")
        }
        body.addView(header(tr("設定", "Settings"), if (source.isEmpty()) null
            else label(source, 12f, colors.secondary).apply { gravity = Gravity.CENTER_VERTICAL }),
            LinearLayout.LayoutParams(-1, dp(52)))

        val t = tray
        val network = when {
            t == null -> ""
            t.wifiConnected -> "Wi-Fi"
            t.networkGlyph == Glyph.ETHERNET -> "Ethernet"
            !t.networkConnected -> tr("未接続", "Offline")
            else -> t.cellularType ?: tr("モバイル", "Mobile")
        }
        val volume = t?.let { if (it.muted) tr("ミュート", "Muted") else percentText(it.volume) } ?: ""
        val battery = t?.let { percentText(it.battery) + if (it.charging) tr("・充電中", " · charging") else "" } ?: ""
        val unread = NotificationCenter.all().size
        val tiles = listOf(
            Triple(tr("ネットワーク", "Network"), network, if (t?.wifiConnected == true) Glyph.WIFI else Glyph.NETWORK) to "qs:network",
            Triple(tr("サウンド", "Sound"), volume, Glyph.VOLUME) to "qs:sound",
            Triple(tr("バッテリー", "Battery"), battery, if (t?.charging == true) Glyph.BATTERY_CHARGING else Glyph.BATTERY) to "qs:battery",
            Triple(tr("通知", "Notifications"), if (unread > 0) tr("${unread}件", "$unread") else tr("なし", "None"), Glyph.NOTIFICATIONS) to "qs:notifications",
        ) + listOfNotNull(host.autoControllers()?.let { pads ->
            val linux = pads.count { it.linux }
            Triple(tr("コントローラー", "Controllers"), tr("Linux ${linux}台・Android ${pads.size - linux}台",
                "Linux $linux · Android ${pads.size - linux}"), Glyph.GAMEPAD) to "qs:controllers"
        })
        section(body, tr("クイックコントロール", "Quick controls"))
        val columns = if (area.width() >= dp(520)) 4 else 2
        val grid = GridLayout(context).apply { columnCount = columns }
        for ((info, kind) in tiles) {
            grid.addView(tile(info.first, info.second, null, info.third, action = { showPage(kind) }),
                GridLayout.LayoutParams().apply {
                    width = 0
                    height = dp(84)
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(gap / 2, gap / 2, gap / 2, gap / 2)
                })
        }
        body.addView(grid, LinearLayout.LayoutParams(-1, -2))

        section(body, tr("表示", "Display"))
        group(body, listOf(
            settingRow(tr("テーマ", "Theme"), glyph = Glyph.DARK_MODE, control = segmented(listOf(
                "dark" to tr("ダーク", "Dark"), "light" to tr("ライト", "Light"), "system" to tr("端末", "System")),
                prefs.getString("theme", "dark") ?: "dark") { host.autoTheme(it) }),
            settingRow(tr("時計", "Clock"), glyph = Glyph.SCHEDULE, control = segmented(listOf(
                "system" to tr("端末", "System"), "12" to tr("12時間", "12-hour"), "24" to tr("24時間", "24-hour")),
                prefs.getString("clock", "system") ?: "system") {
                prefs.edit().putString("clock", it).apply()
                status.invalidate()
                showPage("settings")
            }),
            settingRow(tr("サイドバーの幅", "Sidebar width"), glyph = Glyph.TILE, control = segmented(
                RAIL_WIDTHS.zip(listOf(tr("狭い", "Narrow"), tr("標準", "Standard"), tr("広い", "Wide"))).map { (w, name) -> "$w" to name },
                "$railWidth") {
                prefs.edit().putInt("rail_width", it.toInt()).apply()
                railSignature = ""
                host.relayoutAuto()
                showPage("settings")
            }),
        ))

        section(body, tr("画面", "Screen"))
        group(body, listOf(
            settingRow(tr("1画面・2画面と拡大率", "Split and zoom"), tr("比率、入れ替え、アプリごとの拡大率", "Ratio, swap and per-app zoom"),
                Glyph.MAXIMIZE) { showPage("display") },
            settingRow(tr("アプリの余白", "Space around apps"), tr("画面の端に余白を入れて角を丸める（区切りは常に表示）", "Leave space at the screen edges and round the corners (the divider always stays)"),
                Glyph.TILE, control = Toggle(appGaps, tr("アプリの余白", "Space around apps")) { on ->
                    prefs.edit().putBoolean("app_gaps", on).apply()
                    host.relayoutAuto()
                    refreshChrome()
                }),
            settingRow(tr("全画面表示", "Full screen"), tr("サイドバーを隠してアプリに画面全体を使う", "Hide the sidebar and give apps the whole screen"),
                Glyph.MAXIMIZE, control = Toggle(fullscreen, tr("全画面表示", "Full screen")) { on ->
                    if (host.workspace.primary == null) {
                        fullscreen = on
                        prefs.edit().putBoolean("fullscreen", on).apply()
                    } else if (on) { hidePage(); setFullscreen(true) } else setFullscreen(false)
                }),
        ))

        section(body, "Android Auto")
        group(body, listOf(
            settingRow(tr("ドラッグモードのボタン", "Drag mode button"),
                tr("Drivingの地図ボタン。メニューのボタンはAndroid Autoの仕様で常に表示されます", "Driving's map button. The menu button is required by Android Auto"),
                Glyph.TILE, control = Toggle(prefs.getBoolean("show_drag_button", true), tr("ドラッグモードのボタン", "Drag mode button")) { on ->
                    prefs.edit().putBoolean("show_drag_button", on).apply()
                    moe.n4tsu.dextop.CardexRelayService.pushClientSettings()
                }),
        ))

        section(body, tr("情報", "About"))
        group(body, listOf(
            settingRow(tr("接続元", "Connected from"), mode.ifEmpty { "—" }, Glyph.COMPUTER),
            settingRow("Dextop", moe.n4tsu.dextop.BuildConfig.VERSION_NAME, Glyph.INFO),
            settingRow(tr("端末", "Device"), "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}", Glyph.COMPUTER),
        ))
        return pageFrame(body, scroll = true)
    }

    private fun networkPage(): View {
        val body = column()
        subHeader(body, tr("ネットワーク", "Network"))
        val toggles = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; addView(loadingRow()) }
        body.addView(toggles, LinearLayout.LayoutParams(-1, -2))
        val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val rescan = textButton(tr("再スキャン", "Scan")) {}
        section(body, tr("Wi-Fiネットワーク", "Wi-Fi networks"), rescan)
        body.addView(list, LinearLayout.LayoutParams(-1, -2))
        section(body, "")
        group(body, listOf(settingRow(tr("Androidのネットワーク設定", "Android network settings"), glyph = Glyph.SETTINGS) {
            host.autoOpenSettings(android.provider.Settings.ACTION_WIRELESS_SETTINGS)
        }))
        val controls = host.autoControls
        fun reloadSoon() = postDelayed({ if (page != null && pageKind == "qs:network") showPage("qs:network") }, 1_500L)
        fun change(work: () -> Unit) = host.autoLoad(body, work) { host.autoTrayChanged(); reloadSoon() }
        fun showNetworks(networks: List<PlasmaSystemControls.WifiNetwork>) {
            list.removeAllViews()
            val rows = networks.sortedWith(compareByDescending<PlasmaSystemControls.WifiNetwork> { it.connected }
                .thenByDescending { it.saved }.thenByDescending { it.level }).take(12).map { network ->
                val joinable = network.connected || network.saved || !network.secured
                val state = when {
                    network.connected -> tr("接続中", "Connected")
                    network.saved -> tr("保存済み", "Saved")
                    network.secured -> tr("パスワードはスマホで入力してください", "Enter the password on the phone")
                    else -> tr("オープン", "Open")
                }
                settingRow(network.ssid, state, Glyph.WIFI, network.level, enabled = joinable) {
                    if (!network.connected) change { controls.connectWifi(network, null) }
                }
            }
            if (rows.isEmpty()) list.addView(label(tr("見つかりません", "No networks found"), 13f, colors.secondary).apply {
                gravity = Gravity.CENTER; setPadding(0, dp(14), 0, dp(14))
            }) else group(list, rows)
        }
        list.addView(loadingRow())
        rescan.setOnClickListener {
            list.removeAllViews(); list.addView(loadingRow())
            host.autoLoad(body, { controls.scanWifi(true) }, ::showNetworks)
        }
        host.autoLoad(body, { Triple(controls.networkStatus(), controls.wifiConnection(), controls.scanWifi(false)) }) { (net, wifi, networks) ->
            toggles.removeAllViews()
            group(toggles, listOf(
                settingRow("Wi-Fi", wifi?.ssid ?: if (net.wifiEnabled) tr("未接続", "Not connected") else tr("オフ", "Off"),
                    if (net.wifiEnabled) Glyph.WIFI else Glyph.WIFI_OFF, net.wifiLevel ?: 0f,
                    control = Toggle(net.wifiEnabled, "Wi-Fi") { on -> change { controls.setWifiEnabled(on) } }),
                settingRow(tr("モバイルデータ", "Mobile data"), net.carrierName ?: tray?.cellularType,
                    Glyph.CELLULAR, net.cellularLevel ?: 0f,
                    control = Toggle(net.mobileDataEnabled, tr("モバイルデータ", "Mobile data")) { on -> change { controls.setMobileData(on) } }),
                settingRow(tr("機内モード", "Airplane mode"), glyph = Glyph.AIRPLANE,
                    control = Toggle(net.airplaneMode, tr("機内モード", "Airplane mode")) { on -> change { controls.setAirplaneMode(on) } }),
                settingRow("Bluetooth", glyph = if (net.bluetoothEnabled) Glyph.BLUETOOTH else Glyph.BLUETOOTH_OFF,
                    control = Toggle(net.bluetoothEnabled, "Bluetooth") { on -> change { controls.setBluetooth(on) } }),
            ))
            showNetworks(networks)
        }
        return pageFrame(body, scroll = true)
    }

    /** Which controllers Steam (Linux) takes; the rest stay with Android and Android Auto. */
    private fun controllersPage(): View {
        val body = column()
        subHeader(body, tr("コントローラー", "Controllers"))
        val pads = host.autoControllers().orEmpty()
        if (pads.isEmpty()) {
            body.addView(label(tr("コントローラーが接続されていません", "No controllers connected"), 15f, colors.secondary).apply {
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(-1, dp(160)))
            return pageFrame(body, scroll = true)
        }
        section(body, tr("Linux（Steam）で使うコントローラー", "Controllers for Linux (Steam)"))
        group(body, pads.map { pad ->
            settingRow(pad.name, if (pad.linux) tr("Steamが使用（Androidには送りません）", "Used by Steam (not sent to Android)")
                else tr("Androidが使用", "Used by Android"), Glyph.GAMEPAD,
                control = Toggle(pad.linux, pad.name) { on ->
                    host.setAutoControllerForLinux(pad, on)
                    showPage("qs:controllers")
                })
        })
        body.addView(label(tr("切り替えは、コントローラーをつなぎ直すかSteam Deck Modeを開き直すと反映されます。",
            "A change applies when the controller reconnects or Steam Deck Mode opens again."), 12f, colors.secondary, lines = 3).apply {
            setPadding(dp(4), dp(10), dp(4), 0)
        })
        return pageFrame(body, scroll = true)
    }

    private val controllerWatcher = object : android.hardware.input.InputManager.InputDeviceListener {
        private fun reload() {
            if (page != null && (pageKind == "qs:controllers" || pageKind == "settings")) showPage(pageKind)
        }
        override fun onInputDeviceAdded(deviceId: Int) = reload()
        override fun onInputDeviceRemoved(deviceId: Int) = reload()
        override fun onInputDeviceChanged(deviceId: Int) = Unit
    }

    private fun soundPage(): View {
        val body = column()
        subHeader(body, tr("サウンド", "Sound"))
        val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; addView(loadingRow()) }
        body.addView(content, LinearLayout.LayoutParams(-1, -2))
        val controls = host.autoControls
        val names = mapOf(
            AudioManager.STREAM_MUSIC to (tr("メディア", "Media") to Glyph.MUSIC),
            AudioManager.STREAM_RING to (tr("着信音", "Ringtone") to Glyph.RINGTONE),
            AudioManager.STREAM_NOTIFICATION to (tr("通知", "Notifications") to Glyph.NOTIFICATIONS),
            AudioManager.STREAM_ALARM to (tr("アラーム", "Alarm") to Glyph.ALARM),
        )
        host.autoLoad(body, { controls.streams.map(controls::streamVolume) to controls.mediaOutput()?.productName?.toString() }) { (streams, output) ->
            content.removeAllViews()
            group(content, streams.mapNotNull { volume ->
                val (name, glyph) = names[volume.stream] ?: return@mapNotNull null
                volumeRow(name, glyph, volume)
            })
            section(content, "")
            group(content, listOf(
                settingRow(tr("出力先", "Output"), output ?: tr("このスマホ", "This phone"), Glyph.SPEAKER),
                settingRow(tr("Androidのサウンド設定", "Android sound settings"), glyph = Glyph.SETTINGS) {
                    host.autoOpenSettings(android.provider.Settings.ACTION_SOUND_SETTINGS)
                },
            ))
        }
        return pageFrame(body, scroll = true)
    }

    /** One stream: name and level above, then − slider + so it works without dragging too. */
    private fun volumeRow(name: String, glyph: Glyph, volume: PlasmaSystemControls.StreamVolume): View {
        val controls = host.autoControls
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(10), dp(8))
            alpha = if (volume.adjustable) 1f else .45f
        }
        val percent = label(if (volume.muted) tr("ミュート", "Muted") else percentText(volume.level), 13f, colors.secondary)
        val top = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(Tap(name, shape = dp(28), iconPx = dp(22), circle = false, action = {}).apply {
            this.glyph = glyph; tint = colors.accent; isClickable = false
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(28), dp(28)).apply { rightMargin = dp(12) })
        top.addView(label(name, 15f), LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(percent)
        box.addView(top)
        val slider = Slider(if (volume.muted) 0f else volume.level, name) { level, final ->
            percent.text = percentText(level)
            if (final) host.autoLoad(box, { controls.setStreamVolume(volume.stream, level) }) { host.autoTrayChanged() }
        }
        fun step(by: Float) {
            slider.level = slider.level + by
            percent.text = percentText(slider.level)
            host.autoLoad(box, { controls.setStreamVolume(volume.stream, slider.level) }) { host.autoTrayChanged() }
        }
        val line = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        line.addView(Tap(tr("${name}を下げる", "Lower $name"), shape = dp(40), iconPx = dp(20), action = { step(-1f / 15f) }).apply {
            text = "−"; fill = colors.raised
        }, LinearLayout.LayoutParams(dp(MIN_TOUCH), dp(MIN_TOUCH)))
        line.addView(slider, LinearLayout.LayoutParams(0, dp(MIN_TOUCH), 1f))
        line.addView(Tap(tr("${name}を上げる", "Raise $name"), shape = dp(40), iconPx = dp(20), action = { step(1f / 15f) }).apply {
            text = "+"; fill = colors.raised
        }, LinearLayout.LayoutParams(dp(MIN_TOUCH), dp(MIN_TOUCH)))
        box.addView(line, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        if (!volume.adjustable) box.isEnabled = false
        return box
    }

    private fun batteryPage(): View {
        val body = column()
        subHeader(body, tr("バッテリー", "Battery"))
        val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; addView(loadingRow()) }
        body.addView(content, LinearLayout.LayoutParams(-1, -2))
        val controls = host.autoControls
        host.autoLoad(body, { controls.powerStatus() }) { power ->
            content.removeAllViews()
            val color = when {
                power.charging -> BATTERY_CHARGING
                power.level <= .15f -> BATTERY_LOW
                power.batterySaver -> BATTERY_SAVER
                else -> colors.text
            }
            val hero = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = card(colors.card, CORNER)
                setPadding(dp(18), dp(14), dp(18), dp(14))
            }
            hero.addView(label(percentText(power.level), 40f, color, bold = true))
            val state = when {
                power.full -> tr("充電完了", "Fully charged")
                power.charging -> tr("充電中", "Charging") + when (power.source) {
                    PlasmaSystemControls.PowerSource.AC -> tr("（AC）", " (AC)")
                    PlasmaSystemControls.PowerSource.USB -> tr("（USB）", " (USB)")
                    PlasmaSystemControls.PowerSource.WIRELESS -> tr("（ワイヤレス）", " (wireless)")
                    else -> ""
                }
                else -> tr("バッテリーで動作中", "On battery")
            }
            hero.addView(label(state, 14f, colors.secondary), LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(2) })
            val bar = LevelBar().apply { level = power.level; fillColor = color }
            hero.addView(bar, LinearLayout.LayoutParams(-1, dp(18)).apply { topMargin = dp(10) })
            content.addView(hero, LinearLayout.LayoutParams(-1, -2))
            val rows = mutableListOf<View>()
            power.chargeTimeRemainingMs?.takeIf { power.charging && it > 0 }?.let { ms ->
                val minutes = (ms / 60_000L).toInt()
                rows += settingRow(tr("充電完了まで", "Until full"),
                    if (minutes >= 60) tr("${minutes / 60}時間${minutes % 60}分", "${minutes / 60} h ${minutes % 60} min") else tr("${minutes}分", "$minutes min"),
                    Glyph.SCHEDULE)
            }
            power.temperatureCelsius?.let { rows += settingRow(tr("温度", "Temperature"), "%.1f °C".format(it), Glyph.THERMOMETER) }
            rows += settingRow(tr("省電力モード", "Battery saver"), tr("オンの間は残量表示が黄色になります", "The battery ring turns yellow while on"),
                Glyph.BATTERY_SAVER, control = Toggle(power.batterySaver, tr("省電力モード", "Battery saver")) { on ->
                    host.autoLoad(body, { controls.setBatterySaver(on) }) {
                        host.autoTrayChanged()
                        postDelayed({ if (page != null && pageKind == "qs:battery") showPage("qs:battery") }, 800L)
                    }
                })
            rows += settingRow(tr("Androidのバッテリー設定", "Android battery settings"), glyph = Glyph.SETTINGS) {
                host.autoOpenSettings(android.content.Intent.ACTION_POWER_USAGE_SUMMARY)
            }
            section(content, "")
            group(content, rows)
        }
        return pageFrame(body, scroll = true)
    }

    private fun notificationsPage(): View {
        val body = column()
        val items = NotificationCenter.all()
        val clearable = items.filter { it.clearable }
        subHeader(body, tr("通知", "Notifications"), if (clearable.isEmpty()) null
            else textButton(tr("すべて消去", "Clear all")) { clearable.forEach(host::autoDismissNotification) })
        if (items.isEmpty()) {
            body.addView(label(tr("通知はありません", "No notifications"), 15f, colors.secondary).apply {
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(-1, dp(160)))
            return pageFrame(body, scroll = true)
        }
        for (item in items.take(30)) {
            val entry = LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                background = card(colors.card, CORNER)
                setPadding(dp(14), dp(10), dp(4), dp(10))
            }
            entry.addView(Tap(item.app, shape = dp(36), iconPx = dp(34), circle = false, action = {}).apply {
                drawable = item.icon?.let(::copyIcon)
                if (drawable == null) glyph = Glyph.NOTIFICATIONS
                isClickable = false
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(36), dp(36)).apply { rightMargin = dp(12) })
            val text = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            val time = android.text.format.DateUtils.getRelativeTimeSpanString(item.time).toString()
            text.addView(label("${item.app} · $time", 12f, colors.secondary))
            if (item.title.isNotEmpty()) text.addView(label(item.title, 15f, bold = true), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
            if (item.text.isNotEmpty()) text.addView(label(item.text, 13f, colors.secondary, lines = 2), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
            entry.addView(text, LinearLayout.LayoutParams(0, -2, 1f))
            if (item.clearable) entry.addView(Tap(tr("通知を消去", "Dismiss"), shape = dp(36), iconPx = dp(18),
                action = { host.autoDismissNotification(item) }).apply { glyph = Glyph.CLOSE },
                LinearLayout.LayoutParams(dp(TOUCH), dp(TOUCH)))
            pressable(entry, listOf(item.app, item.title, item.text).filter { it.isNotEmpty() }.joinToString(", "), CORNER) {
                host.autoOpenNotification(item)
            }
            body.addView(entry, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(GAP) })
        }
        return pageFrame(body, scroll = true)
    }
    // endregion

    /** Screen controls as one sheet of large buttons: split, zoom of the active app, full screen. */
    private fun displaySheet(area: Rect, landscape: Boolean): View {
        val ws = host.workspace
        val gap = dp(GAP)
        val scrim = FrameLayout(context).apply {
            setBackgroundColor(colors.scrim)
            isClickable = true
            setOnClickListener { hidePage() }
        }
        val sheet = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = card(colors.card, 20)
            setPadding(dp(14), dp(8), dp(14), dp(14))
            isClickable = true
        }
        sheet.addView(header(tr("画面", "Screen"), Tap(tr("閉じる", "Close"), shape = dp(36), iconPx = dp(20),
            action = { hidePage() }).apply { glyph = Glyph.CLOSE; fill = colors.raised }),
            LinearLayout.LayoutParams(-1, dp(MIN_TOUCH)))

        fun caption(text: String) = label(text, 12f, colors.secondary).apply { setPadding(dp(4), 0, 0, 0) }
        fun actions(vararg items: Triple<String, Glyph, () -> Unit>): LinearLayout = LinearLayout(context).apply {
            items.forEachIndexed { index, (title, glyph, action) ->
                addView(tile(title, "", null, glyph, compact = true, action = action),
                    LinearLayout.LayoutParams(0, -1, 1f).apply { if (index > 0) leftMargin = gap })
            }
        }
        val layout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        layout.addView(caption(tr("配置", "Layout")), LinearLayout.LayoutParams(-1, -2))
        layout.addView(segmented(listOf("single" to tr("1画面", "One app"), "split" to tr("2画面", "Two apps")),
            if (ws.split) "split" else "single", fill = true) {
            if (it == "single") { host.singleAuto(ws.focusedPane); hidePage() } else host.splitAuto()
        }, LinearLayout.LayoutParams(-1, dp(TOUCH)).apply { topMargin = dp(4) })
        if (ws.split) {
            val ratio = when {
                abs(ws.ratio - .33f) < .05f -> "1:2"
                abs(ws.ratio - .5f) < .05f -> "1:1"
                abs(ws.ratio - .67f) < .05f -> "2:1"
                else -> ""
            }
            layout.addView(segmented(listOf("1:2" to "1 : 2", "1:1" to "1 : 1", "2:1" to "2 : 1"), ratio, fill = true) {
                host.resizeAuto(when (it) { "1:2" -> .33f; "2:1" -> .67f; else -> .5f }, true)
                showPage("display")
            }, LinearLayout.LayoutParams(-1, dp(TOUCH)).apply { topMargin = gap })
            val first = paneName(0, landscape)
            val second = paneName(1, landscape)
            layout.addView(actions(
                Triple(tr("入れ替え", "Swap"), Glyph.REFRESH, { host.swapAuto(); hidePage() }),
                Triple(tr("${first}を変更", "Change $first"), Glyph.APPS, { showPage("apps", 0) }),
                Triple(tr("${second}を変更", "Change $second"), Glyph.APPS, { showPage("apps", 1) }),
            ), LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = gap })
        }

        val view = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val active = host.tasks().firstOrNull { it.id == ws.active() }
        val scale = host.autoScale()
        view.addView(caption(if (active != null) tr("拡大率 · ${active.label}", "Zoom · ${active.label}") else tr("拡大率", "Zoom")),
            LinearLayout.LayoutParams(-1, -2))
        if (active != null && scale != null) {
            val steps = listOf(.75f, .9f, 1f, 1.15f, 1.3f, 1.5f)
            val nearest = steps.minByOrNull { abs(it - scale) } ?: 1f
            view.addView(segmented(steps.map { "$it" to "${(it * 100).roundToInt()}%" }, "$nearest", fill = true) {
                host.setAutoScale(it.toFloat())
                showPage("display")
            }, LinearLayout.LayoutParams(-1, dp(TOUCH)).apply { topMargin = dp(4) })
        } else {
            view.addView(label(tr("アプリを開くと変更できます", "Open an app to change its zoom"), 13f, colors.secondary).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(4), 0, 0, 0)
            }, LinearLayout.LayoutParams(-1, dp(TOUCH)))
        }
        val all = host.tasks()
        val hidden = all.filterNot { ws.visible(it.id) }
        if (ws.primary != null) view.addView(actions(Triple(tr("全画面表示", "Full screen"), Glyph.MAXIMIZE, { hidePage(); setFullscreen(true) })),
            LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = gap })
        if (all.isNotEmpty()) {
            view.addView(caption(tr("使用中のアプリ ${all.size}件", "${all.size} running apps")),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
            val closing = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            if (hidden.isNotEmpty()) closing.addView(tile(tr("表示中以外を閉じる", "Close hidden"), "", null, Glyph.CLOSE, compact = true) {
                closeAllApps(hidden)
                hidePage()
            }, LinearLayout.LayoutParams(-1, dp(TOUCH)).apply { bottomMargin = gap })
            // Closing everything takes a second press, so a stray touch while driving cannot do it.
            var armed = false
            lateinit var closeAll: View
            closeAll = tile(tr("すべてのアプリを閉じる", "Close all apps"), "", null, Glyph.CLOSE, compact = true) {
                val title = ((closeAll as ViewGroup).getChildAt(1) as TextView)
                if (!armed) {
                    armed = true
                    swapText(title, tr("もう一度押して閉じる", "Press again to close"))
                    title.setTextColor(BATTERY_LOW)
                    closeAll.animate().scaleX(1.03f).scaleY(1.03f).setDuration(TRANSITION_MS / 2).withEndAction {
                        closeAll.animate().scaleX(1f).scaleY(1f).setDuration(TRANSITION_MS).setInterpolator(SPRING).start()
                    }.start()
                    postDelayed({
                        if (armed && closeAll.isAttachedToWindow) {
                            armed = false
                            swapText(title, tr("すべてのアプリを閉じる", "Close all apps"))
                            title.setTextColor(colors.text)
                        }
                    }, 3_000L)
                } else {
                    armed = false
                    hidePage()
                    closeAllApps()
                }
            }
            closing.addView(closeAll, LinearLayout.LayoutParams(-1, dp(TOUCH)))
            view.addView(closing, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        }

        if (area.width() >= dp(620)) {
            val columns = LinearLayout(context)
            columns.addView(layout, LinearLayout.LayoutParams(0, -2, 1f))
            columns.addView(view, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(14) })
            sheet.addView(columns, LinearLayout.LayoutParams(-1, -2))
        } else {
            sheet.addView(layout, LinearLayout.LayoutParams(-1, -2))
            sheet.addView(view, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        }
        if (ws.split) sheet.addView(label(tr("中央の仕切りをドラッグして比率を変更、長押しで1 : 1", "Drag the divider to resize, long press for 1 : 1"),
            11f, colors.secondary).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = gap })

        val sheetWidth = minOf(area.width() - gap * 2, dp(720))
        val scroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            addView(sheet)
        }
        scrim.addView(scroll, LayoutParams(sheetWidth, -2, Gravity.CENTER))
        return scrim
    }
    // endregion

    // region application bubble
    private var bubble: View? = null
    private var bubbleTask: Int? = null

    private fun dismissBubble(animated: Boolean = true) {
        val view = bubble ?: return
        bubble = null
        bubbleTask = null
        if (!animated) { removeView(view); return }
        view.setOnTouchListener { _, _ -> true }
        val group = view as ViewGroup
        for (index in 0 until group.childCount) {
            group.getChildAt(index).animate().alpha(0f).scaleX(.9f).scaleY(.9f).setStartDelay(0L)
                .setDuration(TRANSITION_MS * 2 / 3).setInterpolator(EXIT).start()
        }
        postDelayed({ removeView(view) }, TRANSITION_MS * 2 / 3)
    }

    /** A speech bubble beside an on-screen application's rail icon with what can be done to it. */
    private fun showAppBubble(task: AutoTask, anchor: View) {
        dismissBubble(animated = false)
        val ws = host.workspace
        val g = geometry()
        val at = Rect(0, 0, anchor.width, anchor.height)
        offsetDescendantRectToMyCoords(anchor, at)
        // Tapping an application on screen also gives it the focus.
        if (ws.active() != task.id) host.selectAuto(task.id)

        val catcher = FrameLayout(context).apply {
            isClickable = true
            setOnClickListener { dismissBubble() }
        }
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = card(colors.card, 18)
            elevation = dpf(12f)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            isClickable = true
        }
        val head = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(8))
        }
        head.addView(Tap(task.label, shape = dp(32), iconPx = dp(30), circle = false, action = {}).apply {
            drawable = task.icon?.let(::copyIcon)
            if (drawable == null) glyph = Glyph.WINDOW
            isClickable = false
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(32), dp(32)))
        head.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, 0, 0)
            addView(label(task.label, 15f, bold = true))
            addView(label(tr("表示中", "On screen"), 12f, colors.secondary), LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(2) })
        }, LinearLayout.LayoutParams(0, -2, 1f))
        panel.addView(head, LinearLayout.LayoutParams(-1, -2))
        panel.addView(View(context).apply { setBackgroundColor(colors.separator) },
            LinearLayout.LayoutParams(-1, maxOf(1, dp(1))).apply { bottomMargin = dp(4) })

        fun item(title: String, glyph: Glyph, color: Int = colors.text, action: () -> Unit) {
            val row = LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), 0, dp(12), 0)
            }
            row.addView(Tap(title, shape = dp(24), iconPx = dp(22), circle = false, action = {}).apply {
                this.glyph = glyph
                tint = color
                isClickable = false
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(24), dp(24)))
            row.addView(label(title, 15f, color), LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(12) })
            pressable(row, title, 12) { action() }
            panel.addView(row, LinearLayout.LayoutParams(-1, dp(TOUCH)))
        }
        val packageName = task.packageName?.takeIf { name -> host.applications().any { it.packageName == name } }
        if (packageName != null) {
            val pinned = packageName in favorites()
            item(if (pinned) tr("ホーム画面から外す", "Unpin from Home") else tr("ホーム画面に固定", "Pin to Home"),
                if (pinned) Glyph.CLOSE else Glyph.PIN) {
                toggleFavorite(packageName, refresh = false)
                dismissBubble()
            }
        }
        val pane = when (task.id) { ws.primary -> 0; ws.secondary -> 1; else -> null }
        if (ws.split && pane != null) item(tr("このアプリだけ表示", "Show only this app"), Glyph.MAXIMIZE) {
            dismissBubble()
            host.singleAuto(pane)
        } else item(tr("全画面表示", "Full screen"), Glyph.MAXIMIZE) {
            dismissBubble()
            setFullscreen(true)
        }
        item(tr("アプリを終了", "Quit app"), Glyph.CLOSE, BATTERY_LOW) {
            dismissBubble()
            host.closeAuto(task.id)
        }

        val width = dp(250)
        panel.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val height = panel.measuredHeight
        val gap = dp(12)
        val margin = dp(INSET)
        val v = g.viewport
        val box: Rect
        val tip: android.graphics.PointF
        if (g.landscape) {
            val left = g.rail.right + gap
            val top = (at.centerY() - height / 2).coerceIn(v.top + margin, (v.bottom - margin - height).coerceAtLeast(v.top + margin))
            box = Rect(left, top, left + width, top + height)
            tip = android.graphics.PointF(left.toFloat(), at.exactCenterY())
        } else {
            val bottom = g.rail.top - gap
            val left = (at.centerX() - width / 2).coerceIn(v.left + margin, (v.right - margin - width).coerceAtLeast(v.left + margin))
            box = Rect(left, bottom - height, left + width, bottom)
            tip = android.graphics.PointF(at.exactCenterX(), bottom.toFloat())
        }
        // The bubble's tail: a turned square half hidden under the card, pointing at the icon.
        val tail = View(context).apply {
            background = card(colors.card, 3)
            rotation = 45f
            elevation = dpf(12f)
        }
        val tailSize = dp(16)
        catcher.addView(tail, LayoutParams(tailSize, tailSize).apply {
            gravity = Gravity.TOP or Gravity.START
            leftMargin = (tip.x - tailSize / 2f).roundToInt()
            topMargin = (tip.y - tailSize / 2f).roundToInt()
        })
        catcher.addView(panel, LayoutParams(width, height).apply {
            gravity = Gravity.TOP or Gravity.START
            leftMargin = box.left
            topMargin = box.top
        })
        addView(catcher, LayoutParams(-1, -1))
        bubble = catcher
        bubbleTask = task.id

        // It grows out of the icon, then its items follow one by one.
        panel.pivotX = tip.x - box.left
        panel.pivotY = tip.y - box.top
        panel.alpha = 0f; panel.scaleX = .6f; panel.scaleY = .6f
        panel.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(TRANSITION_MS + 80).setInterpolator(ENTER).start()
        tail.alpha = 0f
        tail.animate().alpha(1f).setStartDelay(TRANSITION_MS / 3).setDuration(TRANSITION_MS).start()
        for (index in 1 until panel.childCount) {
            val child = panel.getChildAt(index)
            child.alpha = 0f
            child.translationX = if (g.landscape) -dpf(8f) else 0f
            child.translationY = if (g.landscape) 0f else dpf(8f)
            child.animate().alpha(1f).translationX(0f).translationY(0f).setStartDelay(60L + index * 30L)
                .setDuration(TRANSITION_MS).setInterpolator(ENTER).start()
        }
        catcher.bringToFront()
    }
    // endregion

    // region applications
    private fun favorites() = prefs.getStringSet("favorites", emptySet()).orEmpty().toSet()

    private fun toggleFavorite(packageName: String, refresh: Boolean = true) {
        val next = favorites().toMutableSet()
        if (!next.add(packageName)) next.remove(packageName)
        prefs.edit().putStringSet("favorites", next).apply()
        if (refresh) showPage(pageKind, pickingPane)
    }

    private fun showApps(filter: String) {
        this.filter = filter
        showPage("apps")
    }

    private fun open(packageName: String, pane: Int?) {
        host.launchAuto(packageName, pane)
        hidePage()
    }

    private fun isMaps(app: PlasmaApp) = app.category == ApplicationInfo.CATEGORY_MAPS ||
        app.packageName.contains("maps", true) || app.packageName.contains("navi", true) ||
        app.packageName == "com.waze"

    private fun isMusic(app: PlasmaApp) = app.category == ApplicationInfo.CATEGORY_AUDIO ||
        app.packageName in setOf("com.spotify.music", "com.google.android.apps.youtube.music",
            "com.amazon.mp3", "com.apple.android.music", "jp.linecorp.linemusic.android")

    /** A favourite wins, then a well-known app, so the shortcut opens one app without a list. */
    private fun resolve(candidates: List<PlasmaApp>, preferred: List<String>): PlasmaApp? {
        val favorites = favorites()
        return candidates.firstOrNull { it.packageName in favorites }
            ?: preferred.firstNotNullOfOrNull { name -> candidates.firstOrNull { it.packageName == name } }
            ?: candidates.minByOrNull { it.label.lowercase() }
    }

    private fun resolveMaps() = resolve(host.applications().filter(::isMaps),
        listOf("com.google.android.apps.maps", "com.waze", "jp.co.yahoo.android.apps.navi"))

    private fun resolveMusic(): PlasmaApp? {
        val apps = host.applications()
        media?.packageName?.let { name -> apps.firstOrNull { it.packageName == name }?.let { return it } }
        return resolve(apps.filter(::isMusic),
            listOf("com.google.android.apps.youtube.music", "com.spotify.music", "com.amazon.mp3"))
    }
    // endregion

    // region building blocks
    private fun copyIcon(icon: Drawable): Drawable = (icon.constantState?.newDrawable() ?: icon).mutate()

    private fun card(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun pressable(view: View, description: String, radius: Int, long: (() -> Unit)? = null, action: () -> Unit) {
        view.isClickable = true
        view.isFocusable = true
        view.contentDescription = description
        view.foreground = RippleDrawable(ColorStateList.valueOf(colors.pressed), null, card(Color.WHITE, radius))
        view.setOnClickListener { action() }
        if (long != null) view.setOnLongClickListener { long(); true }
        pressMotion(view, .97f)
    }

    /** A pressed control sinks a little and springs back when it is let go. */
    private fun pressMotion(view: View, depth: Float) {
        fun scale(to: Float, ms: Long, curve: android.animation.TimeInterpolator) = android.animation.AnimatorSet().apply {
            playTogether(android.animation.ObjectAnimator.ofFloat(view, View.SCALE_X, to),
                android.animation.ObjectAnimator.ofFloat(view, View.SCALE_Y, to))
            duration = ms
            interpolator = curve
        }
        view.stateListAnimator = android.animation.StateListAnimator().apply {
            addState(intArrayOf(android.R.attr.state_pressed), scale(depth, 90L, PlasmaTheme.outCubic))
            addState(intArrayOf(), scale(1f, TRANSITION_MS + 60, SPRING))
        }
    }

    private fun label(value: CharSequence, size: Float, color: Int = colors.text, bold: Boolean = false, lines: Int = 1) =
        TextView(context).apply {
            text = value
            setTextSize(TypedValue.COMPLEX_UNIT_PX, size * unit)
            setTextColor(color)
            typeface = if (bold) Typeface.create("sans-serif-medium", Typeface.NORMAL) else Typeface.DEFAULT
            maxLines = lines
            ellipsize = TextUtils.TruncateAt.END
            includeFontPadding = false
        }

    private fun textButton(title: String, action: () -> Unit) = label(title, 14f, colors.accent, bold = true).apply {
        gravity = Gravity.CENTER
        setPadding(dp(14), 0, dp(14), 0)
        minWidth = dp(MIN_TOUCH)
        pressable(this, title, 12, action = action)
    }

    /** A card with an icon above a title; the whole card is the touch target. */
    private fun tile(
        title: String,
        subtitle: String,
        icon: Drawable?,
        glyph: Glyph,
        compact: Boolean = false,
        long: (() -> Unit)? = null,
        action: () -> Unit,
    ): View {
        val box = LinearLayout(context).apply {
            orientation = if (compact) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            gravity = if (compact) Gravity.CENTER else Gravity.START or Gravity.BOTTOM
            background = card(if (compact) colors.raised else colors.card, 14)
            setPadding(dp(12), dp(10), dp(10), dp(10))
        }
        val iconView = Tap(title, shape = dp(30), iconPx = dp(if (icon != null) 30 else 24), circle = false, action = {}).apply {
            if (icon != null) drawable = icon else this.glyph = glyph
            tint = colors.accent
            isClickable = false
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        box.addView(iconView, LinearLayout.LayoutParams(dp(30), dp(30)))
        if (compact) {
            box.addView(label(title, 13f, bold = true), LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })
        } else {
            box.addView(View(context), LinearLayout.LayoutParams(1, 0, 1f))
            box.addView(label(title, 15f, bold = true), LinearLayout.LayoutParams(-1, -2))
            if (subtitle.isNotEmpty()) box.addView(label(subtitle, 11f, colors.secondary),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
        }
        pressable(box, if (subtitle.isEmpty()) title else "$title, $subtitle", 14, long, action)
        return box
    }

    private fun appCell(
        app: PlasmaApp,
        iconSize: Int,
        textSize: Float,
        favorite: Boolean = false,
        long: (() -> Unit)? = null,
        action: () -> Unit,
    ): View {
        val cell = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(2), dp(4), dp(2), dp(4))
        }
        val icon = Tap(app.label, shape = dp(iconSize), iconPx = dp(iconSize), circle = false, action = {}).apply {
            drawable = copyIcon(app.icon)
            star = favorite
            isClickable = false
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        cell.addView(icon, LinearLayout.LayoutParams(dp(iconSize + 20), dp(iconSize)))
        cell.addView(label(app.label, textSize).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
        val description = if (long == null) app.label
            else app.label + tr("。長押しでお気に入り${if (favorite) "から削除" else "に追加"}",
                if (favorite) ". Long press to remove from favorites" else ". Long press to add to favorites")
        pressable(cell, description, 14, long, action)
        return cell
    }

    /** Pill-shaped options; each option keeps a full-height touch target. */
    private fun segmented(options: List<Pair<String, String>>, selected: String, fill: Boolean = false, choose: (String) -> Unit): LinearLayout =
        Segmented(options, selected, fill, choose)

    /** The highlight slides to the chosen option, and the choice is applied once it arrives. */
    private inner class Segmented(
        options: List<Pair<String, String>>,
        selected: String,
        fill: Boolean,
        private val choose: (String) -> Unit,
    ) : LinearLayout(context) {
        private val chips = ArrayList<TextView>()
        private var from = -1
        private var to = options.indexOfFirst { it.first == selected }
        private var progress = 1f
        private var moving = false
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val box = RectF()

        init {
            setWillNotDraw(false)
            gravity = Gravity.CENTER_VERTICAL
            background = card(colors.raised, 24)
            val pad = dp(3)
            setPadding(pad, pad, pad, pad)
            options.forEachIndexed { index, (key, title) ->
                val on = index == to
                val chip = label(title, 13f, if (on) colors.onAccent else colors.text, bold = on).apply {
                    gravity = Gravity.CENTER
                    setPadding(dp(14), 0, dp(14), 0)
                    minWidth = dp(MIN_TOUCH)
                }
                pressable(chip, title, 21) { if (index != to && !moving) select(index, key) }
                chips += chip
                addView(chip, if (fill) LayoutParams(0, -1, 1f) else LayoutParams(-2, -1))
            }
        }

        private fun select(index: Int, key: String) {
            moving = true
            from = to
            to = index
            android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
                duration = TRANSITION_MS
                interpolator = ENTER
                addUpdateListener {
                    progress = it.animatedValue as Float
                    chips.getOrNull(from)?.setTextColor(blend(colors.onAccent, colors.text, progress))
                    chips.getOrNull(to)?.setTextColor(blend(colors.text, colors.onAccent, progress))
                    invalidate()
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        moving = false
                        choose(key)
                    }
                })
                start()
            }
        }

        override fun dispatchDraw(canvas: Canvas) {
            val target = chips.getOrNull(to)
            if (target != null) {
                val start = chips.getOrNull(from)
                val p = if (start == null) 1f else progress
                fun lerp(a: Int, b: Int) = a + (b - a) * p
                box.set(lerp(start?.left ?: target.left, target.left), lerp(start?.top ?: target.top, target.top),
                    lerp(start?.right ?: target.right, target.right), lerp(start?.bottom ?: target.bottom, target.bottom))
                paint.color = colors.accent
                val r = dpf(21f).coerceAtMost(box.height() / 2f)
                canvas.drawRoundRect(box, r, r, paint)
            }
            super.dispatchDraw(canvas)
        }
    }

    /** Draws a round or rounded shape smaller than its bounds; the bounds are the hit target. */
    private inner class Tap(
        description: String,
        var shape: Int,
        var iconPx: Int,
        private val circle: Boolean = true,
        action: () -> Unit,
        long: (() -> Unit)? = null,
    ) : View(context) {
        var glyph: Glyph? = null
            set(value) { field = value; invalidate() }
        var drawable: Drawable? = null
        var text: String? = null
        var fill = 0
        var tint = colors.text
        var dot = false
        var star = false
        /** How much of [fill] is drawn; animated by [fadeFillIn]. */
        private var fillLevel = 1f
        private var glyphLevel = 1f
        fun setGlyphLevel(level: Float) { glyphLevel = level.coerceIn(0f, 1f); invalidate() }
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        private val rect = RectF()

        init {
            isClickable = true
            isFocusable = true
            contentDescription = description
            setOnClickListener { action() }
            if (long != null) setOnLongClickListener { long(); true }
            pressMotion(this, .9f)
        }

        fun fadeFillIn() {
            android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
                duration = TRANSITION_MS + 60
                interpolator = ENTER
                addUpdateListener { fillLevel = it.animatedValue as Float; invalidate() }
                start()
            }
        }

        override fun drawableStateChanged() {
            super.drawableStateChanged()
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f
            val cy = height / 2f
            val half = minOf(shape.toFloat(), width.toFloat(), height.toFloat()) / 2f
            val radius = if (circle) half else dpf(12f).coerceAtMost(half)
            rect.set(cx - half, cy - half, cx + half, cy + half)
            if (fill != 0 && fillLevel > 0f) {
                paint.color = fill
                paint.alpha = (Color.alpha(fill) * fillLevel).roundToInt()
                if (fillLevel < 1f) {
                    // The highlight grows out from the icon as it fades in.
                    val grow = .7f + .3f * fillLevel
                    canvas.save()
                    canvas.scale(grow, grow, cx, cy)
                    canvas.drawRoundRect(rect, radius, radius, paint)
                    canvas.restore()
                } else canvas.drawRoundRect(rect, radius, radius, paint)
            }
            if (isPressed && isClickable) {
                paint.color = colors.pressed
                canvas.drawRoundRect(rect, radius, radius, paint)
            }
            val s = iconPx / 2f
            val icon = glyph?.let { GlyphDrawable(it, tint).apply { level01 = glyphLevel } } ?: drawable
            if (icon != null) {
                icon.setBounds((cx - s).roundToInt(), (cy - s).roundToInt(), (cx + s).roundToInt(), (cy + s).roundToInt())
                icon.draw(canvas)
            } else text?.let {
                textPaint.color = tint
                textPaint.textSize = iconPx.toFloat()
                canvas.drawText(it, cx, cy - (textPaint.ascent() + textPaint.descent()) / 2f, textPaint)
            }
            if (dot) {
                paint.color = colors.accent
                canvas.drawCircle(cx, rect.bottom - dpf(2f), dpf(2.5f), paint)
            }
            if (star) {
                val size = dpf(16f)
                paint.color = colors.card
                canvas.drawCircle(cx + s, cy - s + dpf(2f), size / 2f + dpf(1f), paint)
                GlyphDrawable(Glyph.STAR, colors.accent, filled = true).apply {
                    setBounds((cx + s - size / 2).roundToInt(), (cy - s + dpf(2f) - size / 2).roundToInt(),
                        (cx + s + size / 2).roundToInt(), (cy - s + dpf(2f) + size / 2).roundToInt())
                    draw(canvas)
                }
            }
        }
    }

    /** Clock with network and battery icons; the whole cell opens the settings page. */
    private inner class StatusCell : View(context) {
        private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val arc = RectF()
        private val tick = object : Runnable {
            override fun run() {
                invalidate()
                postDelayed(this, 10_000L)
            }
        }

        init {
            isClickable = true
            isFocusable = true
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            removeCallbacks(tick)
            post(tick)
        }

        override fun onDetachedFromWindow() {
            removeCallbacks(tick)
            super.onDetachedFromWindow()
        }

        override fun drawableStateChanged() {
            super.drawableStateChanged()
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            if (isPressed) {
                paint.color = colors.pressed
                val r = dpf(12f)
                canvas.drawRoundRect(dpf(4f), dpf(2f), width - dpf(4f), height - dpf(2f), r, r, paint)
            }
            val time = when (prefs.getString("clock", "system")) {
                "24" -> java.text.SimpleDateFormat("H:mm", java.util.Locale.getDefault())
                "12" -> java.text.SimpleDateFormat("h:mm", java.util.Locale.getDefault())
                else -> android.text.format.DateFormat.getTimeFormat(context)
            }.format(java.util.Date())
            textPaint.color = colors.text
            textPaint.textSize = dpf(17f)
            val maxWidth = width - dpf(8f)
            val measured = textPaint.measureText(time)
            if (measured > maxWidth) textPaint.textSize *= maxWidth / measured
            val cx = width / 2f
            canvas.drawText(time, cx, height * .30f, textPaint)
            tray?.let { drawPictogram(canvas, it, cx, height * .68f, dpf(12f)) }
        }

        /**
         * One status pictogram: the open ring is the battery (green while charging, red when low,
         * yellow in battery saver), the centre is Wi-Fi or else the mobile network type, and the
         * dots in the ring's gap are the mobile signal.
         */
        private fun drawPictogram(canvas: Canvas, t: TrayState, cx: Float, cy: Float, radius: Float) {
            val stroke = dpf(2.2f)
            arc.set(cx - radius, cy - radius, cx + radius, cy + radius)
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeWidth = stroke
            paint.color = (colors.secondary and 0x00FFFFFF) or 0x55000000
            canvas.drawArc(arc, RING_START, RING_SWEEP, false, paint)
            paint.color = when {
                t.charging -> BATTERY_CHARGING
                t.battery <= .15f -> BATTERY_LOW
                t.batterySaver -> BATTERY_SAVER
                else -> colors.text
            }
            val level = t.battery.coerceIn(0f, 1f)
            if (level > 0f) canvas.drawArc(arc, RING_START, RING_SWEEP * level, false, paint)
            paint.style = Paint.Style.FILL

            // Centre: Wi-Fi when it carries the connection, otherwise what does.
            val inner = radius * 1.05f
            val cellular = t.cellularVisible && !t.cellularNoService && t.networkConnected
            when {
                t.wifiConnected -> drawGlyph(canvas, Glyph.WIFI, t.networkLevel, colors.text, cx, cy, inner)
                t.networkGlyph == Glyph.ETHERNET -> drawGlyph(canvas, Glyph.ETHERNET, 1f, colors.text, cx, cy, inner)
                cellular && t.cellularType != null -> {
                    textPaint.color = colors.text
                    textPaint.textSize = radius * .82f
                    val label = t.cellularType.orEmpty()
                    val fit = radius * 1.5f
                    val w = textPaint.measureText(label)
                    if (w > fit) textPaint.textSize *= fit / w
                    canvas.drawText(label, cx, cy - (textPaint.ascent() + textPaint.descent()) / 2f, textPaint)
                }
                cellular -> drawGlyph(canvas, Glyph.CELLULAR, t.cellularLevel, colors.text, cx, cy, inner)
                t.networkGlyph == Glyph.AIRPLANE -> drawGlyph(canvas, Glyph.AIRPLANE, 1f, colors.secondary, cx, cy, inner)
                else -> drawGlyph(canvas, Glyph.WIFI_OFF, 1f, colors.secondary, cx, cy, inner)
            }

            // Mobile signal in the ring's gap, left to right like signal bars.
            if (!t.cellularVisible) return
            val lit = if (t.cellularNoService) 0 else (t.cellularLevel.coerceIn(0f, 1f) * SIGNAL_DOTS).roundToInt()
            val dot = dpf(1.3f)
            for (index in 0 until SIGNAL_DOTS) {
                val angle = Math.toRadians((RING_START - (index + 1) * RING_GAP / (SIGNAL_DOTS + 1)).toDouble())
                paint.color = if (index < lit) colors.text else (colors.secondary and 0x00FFFFFF) or 0x66000000
                canvas.drawCircle(cx + radius * kotlin.math.cos(angle).toFloat(), cy + radius * kotlin.math.sin(angle).toFloat(), dot, paint)
            }
        }

        private fun drawGlyph(canvas: Canvas, glyph: Glyph, level: Float, color: Int, cx: Float, cy: Float, size: Float) {
            val half = size / 2f
            GlyphDrawable(glyph, color).apply {
                level01 = level
                setBounds((cx - half).roundToInt(), (cy - half).roundToInt(), (cx + half).roundToInt(), (cy + half).roundToInt())
                draw(canvas)
            }
        }
    }

    private fun blend(from: Int, to: Int, t: Float): Int {
        fun channel(shift: Int) = (((from shr shift) and 0xFF) + (((to shr shift) and 0xFF) - ((from shr shift) and 0xFF)) * t).roundToInt() shl shift
        return channel(24) or channel(16) or channel(8) or channel(0)
    }

    /** On/off switch whose thumb slides; the whole cell is the touch target. */
    private inner class Toggle(on: Boolean, description: String, private val change: (Boolean) -> Unit) : View(context) {
        var on = on
            private set
        private var position = if (on) 1f else 0f
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        init {
            isClickable = true
            isFocusable = true
            contentDescription = description
            setOnClickListener {
                this.on = !this.on
                android.animation.ValueAnimator.ofFloat(position, if (this.on) 1f else 0f).apply {
                    duration = TRANSITION_MS * 3 / 4
                    interpolator = PlasmaTheme.outCubic
                    addUpdateListener { position = it.animatedValue as Float; invalidate() }
                    start()
                }
                change(this.on)
            }
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) =
            setMeasuredDimension(dp(64), View.getDefaultSize(dp(MIN_TOUCH), heightMeasureSpec))

        override fun onDraw(canvas: Canvas) {
            val w = dpf(48f)
            val h = dpf(28f)
            val left = (width - w) / 2f
            val top = (height - h) / 2f
            paint.color = blend(colors.raised, colors.accent, position)
            canvas.drawRoundRect(left, top, left + w, top + h, h / 2, h / 2, paint)
            paint.color = Color.WHITE
            val r = h / 2 - dpf(3f)
            canvas.drawCircle(left + h / 2 + (w - h) * position, top + h / 2, r, paint)
        }
    }

    /** Horizontal level the user can tap or drag; reports live changes and the final value. */
    private inner class Slider(level: Float, description: String, private val change: (Float, Boolean) -> Unit) : View(context) {
        var level = level.coerceIn(0f, 1f)
            set(value) { field = value.coerceIn(0f, 1f); invalidate() }
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        init {
            isClickable = true
            isFocusable = true
            contentDescription = description
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            val pad = dpf(14f)
            val value = ((event.x - pad) / (width - pad * 2).coerceAtLeast(1f)).coerceIn(0f, 1f)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { parent?.requestDisallowInterceptTouchEvent(true); level = value; change(value, false) }
                MotionEvent.ACTION_MOVE -> { level = value; change(value, false) }
                MotionEvent.ACTION_UP -> { level = value; change(value, true) }
                MotionEvent.ACTION_CANCEL -> change(level, true)
            }
            return true
        }

        override fun onDraw(canvas: Canvas) {
            val pad = dpf(14f)
            val h = dpf(6f)
            val cy = height / 2f
            paint.color = colors.raised
            canvas.drawRoundRect(pad, cy - h / 2, width - pad, cy + h / 2, h / 2, h / 2, paint)
            val x = pad + (width - pad * 2) * level
            paint.color = colors.accent
            canvas.drawRoundRect(pad, cy - h / 2, x, cy + h / 2, h / 2, h / 2, paint)
            canvas.drawCircle(x, cy, dpf(10f), paint)
            paint.color = Color.WHITE
            canvas.drawCircle(x, cy, dpf(4f), paint)
        }
    }

    /** A thin level bar, used beside the volume buttons and for the battery. */
    private inner class LevelBar : View(context) {
        var level = 0f
            set(value) { field = value.coerceIn(0f, 1f); invalidate() }
        var fillColor: Int? = null
            set(value) { field = value; invalidate() }
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var animator: android.animation.ValueAnimator? = null

        init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

        /** Glides to [target] instead of jumping, e.g. while the volume keys are held. */
        fun animateTo(target: Float) {
            val to = target.coerceIn(0f, 1f)
            if (abs(to - level) < .001f) return
            animator?.cancel()
            if (!isAttachedToWindow) { level = to; return }
            animator = android.animation.ValueAnimator.ofFloat(level, to).apply {
                duration = TRANSITION_MS
                interpolator = ENTER
                addUpdateListener { level = it.animatedValue as Float }
                start()
            }
        }

        override fun onDraw(canvas: Canvas) {
            val w = dpf(28f).coerceAtMost(width.toFloat())
            val h = dpf(6f)
            val left = (width - w) / 2f
            val top = (height - h) / 2f
            paint.color = colors.raised
            canvas.drawRoundRect(left, top, left + w, top + h, h / 2, h / 2, paint)
            paint.color = fillColor ?: colors.accent
            if (level > 0f) canvas.drawRoundRect(left, top, left + w * level, top + h, h / 2, h / 2, paint)
        }
    }

    /** The draggable split handle; it reaches a little into both panes so it is easy to grab. */
    private inner class DividerHandle : View(context) {
        var vertical = true
        private var start = 0f
        private var startRatio = .5f
        private var dragging = false
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        init {
            isClickable = true
            contentDescription = tr("分割比率を変更。長押しで1 : 1", "Resize split. Long press for 1 : 1")
            setOnLongClickListener {
                if (!dragging) host.resizeAuto(.5f, true)
                true
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            val coordinate = if (vertical) event.rawX else event.rawY
            val length = (geometry().content.let { if (vertical) it.width() else it.height() }).coerceAtLeast(1)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    start = coordinate
                    startRatio = host.workspace.ratio
                    dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!dragging && abs(coordinate - start) > dpf(6f)) {
                        dragging = true
                        cancelLongPress()
                        invalidate()
                    }
                    if (dragging) previewResize(startRatio + (coordinate - start) / length)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (dragging) {
                    dragging = false
                    invalidate()
                    commitResize()
                }
            }
            return super.onTouchEvent(event) || true
        }

        override fun onDraw(canvas: Canvas) {
            paint.color = if (dragging) colors.accent else colors.secondary
            val long = dpf(40f)
            val thick = dpf(5f)
            val cx = width / 2f
            val cy = height / 2f
            if (vertical) canvas.drawRoundRect(cx - thick / 2, cy - long / 2, cx + thick / 2, cy + long / 2, thick, thick, paint)
            else canvas.drawRoundRect(cx - long / 2, cy - thick / 2, cx + long / 2, cy + thick / 2, thick, thick, paint)
        }
    }
    // endregion
}
