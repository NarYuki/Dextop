package moe.n4tsu.dextop.plasma

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.os.SystemClock
import android.view.MotionEvent
import android.view.InputDevice
import android.view.PointerIcon
import android.view.Surface
import android.view.Gravity
import android.view.TextureView
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import moe.n4tsu.dextop.OwnedVirtualDisplay
import moe.n4tsu.dextop.linux.LinuxWindowViewport

internal enum class V2Edge { LEFT, RIGHT, TOP, BOTTOM, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

/** Keep a frame's mouse stream on its owner instead of hit-testing it again. */
internal open class V2WindowInputRoot(
    context: Context,
    private val capturedWindow: (MotionEvent) -> V2WindowView?,
) : FrameLayout(context) {
    private var controllerPointerUnbuffered = false
    private fun prepareControllerPointer(event: MotionEvent) {
        if (controllerPointerUnbuffered || android.os.Build.VERSION.SDK_INT < 30) return
        if (!event.isFromSource(InputDevice.SOURCE_MOUSE)) return
        val device = event.device ?: return
        // DualShock/compatible pads publish one combined gamepad + pointer.
        // Avoid waiting for the UI frame to deliver their touchpad motion.
        if (!device.supportsSource(InputDevice.SOURCE_GAMEPAD) ||
            !device.supportsSource(InputDevice.SOURCE_TOUCHPAD)) return
        requestUnbufferedDispatch(InputDevice.SOURCE_MOUSE or InputDevice.SOURCE_TOUCHPAD)
        controllerPointerUnbuffered = true
    }
    private fun capturedFrameEvent(event: MotionEvent): Boolean {
        val target = capturedWindow(event) ?: return false
        val copy = MotionEvent.obtain(event)
        try {
            copy.offsetLocation(-target.x, -target.y)
            target.dispatchGenericMotionEvent(copy)
        } finally {
            copy.recycle()
        }
        return true
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        prepareControllerPointer(event)
        return capturedFrameEvent(event) || super.dispatchGenericMotionEvent(event)
    }

    override fun dispatchHoverEvent(event: MotionEvent): Boolean {
        prepareControllerPointer(event)
        return capturedFrameEvent(event) || super.dispatchHoverEvent(event)
    }
}

/** Window-manager callbacks from one composited window. */
internal interface V2WindowHost {
    fun onContentFrame(window: V2Window) {}
    val palette: PlasmaPalette
    fun isActive(window: V2Window): Boolean
    fun activate(window: V2Window)
    fun beginMove(window: V2Window, rawX: Float, rawY: Float)
    fun move(window: V2Window, rawX: Float, rawY: Float)
    fun endMove(window: V2Window, rawX: Float, rawY: Float)
    fun beginResize(window: V2Window, edge: V2Edge, rawX: Float, rawY: Float)
    fun resize(window: V2Window, rawX: Float, rawY: Float)
    fun endResize(window: V2Window)
    fun toggleMaximize(window: V2Window)
    fun minimize(window: V2Window)
    fun close(window: V2Window)
    fun showWindowMenu(window: V2Window, rawX: Float, rawY: Float)
    fun onSurfaceReady(window: V2Window, surface: Surface, width: Int, height: Int)
    fun onSurfaceDestroyed(window: V2Window)
    fun captureLost(window: V2Window)
    fun forwardCapturedInput(window: V2Window, event: MotionEvent)
    fun forwardInput(window: V2Window, event: MotionEvent, source: View)
    /** Dextop Auto draws its panes flat, without the desktop window shadow. */
    val windowShadows: Boolean get() = true
    /** Corner radius of the window outline in pixels, or a negative value for the desktop default. */
    fun windowCornerRadius(): Float = -1f
    /** [window]'s corner radius: Book draws maximized and tiled windows square. */
    fun windowCornerRadius(window: V2Window): Float = windowCornerRadius()
    /** Book: the snap layouts offered from the maximize button, under [anchor] (screen pixels). */
    fun showSnapMenu(window: V2Window, anchor: RectF) {}
    /** Book: maximized and tiled windows lie flat, without a shadow over their neighbours. */
    fun flatWindow(window: V2Window): Boolean = false
    /** Book: each corner's radius (top left, top right, bottom right, bottom left), or null for [windowCornerRadius]. */
    fun windowCorners(window: V2Window): FloatArray? = null
    /** Android application windows get a Back button (Linux windows have their own controls). */
    fun hasBackButton(window: V2Window): Boolean = false
    fun goBack(window: V2Window) {}
    /** Dextop Book: Material 3 title bars (tonal circular buttons, title at the start). */
    val bookStyle: Boolean get() = false
}

/**
 * One application window of the Plasma v2 compositor.
 *
 * The application runs alone on its own virtual display; that display
 * renders into [content], a TextureView inside Dextop's own window.  The
 * frame, title bar, shadow and every animation are therefore ordinary
 * views of Dextop's process and move at full frame rate.
 */
internal class V2Window(
    val id: Int,
    var packageName: String,
    context: Context,
    host: V2WindowHost,
    titleHeight: Int,
    val grip: Int,
) {
    /** Outer frame (title bar + client) in Dextop display pixels. */
    val frame = Rect()
    var restoreFrame: Rect? = null
    var maximized = false
    var tile = TileZone.NONE
    var minimized = false
    var desktop = 0
    var onAllDesktops = false
    var lastActivated = SystemClock.uptimeMillis()
    var title: String = ""
    var icon: android.graphics.drawable.Drawable? = null
    var closing = false

    var display: OwnedVirtualDisplay? = null
    var displayId = -1
    var displayWidth = 0
    var displayHeight = 0
    var surface: Surface? = null
    var hadTask = false
    /** Task currently shown on this window's display, or -1. */
    var taskId = -1
    var emptySince = 0L
    val createdAt = SystemClock.uptimeMillis()
    /** Task to pull onto this window's display once it exists (adopted strays). */
    var pendingAdoption: TaskSnapshot? = null
    /** Intent to start instead of the package's launcher activity (settings screens). */
    var launchIntent: android.content.Intent? = null
    /** The Dextop activity shown (Book's Settings or Widgets), whose icon the window uses. */
    var ownComponent: android.content.ComponentName? = null
    /** Another task seen over this window's own on its display, and since when (uptime). */
    var stackedTask = -1
    var stackedSince = 0L
    var adoptAttempted = false

    /** Container id when this window shows a Linux desktop instead of an Android app. */
    var linuxContainer: String? = null
    /** Size of the Linux X screen drawn into [view]'s content, for input scaling. */
    var linuxWidth = 0
    var linuxHeight = 0
    /** Pointer state of the Linux surface (buttons, gesture remainders). */
    var linuxCaptureRequested = false
    var linuxCaptureSuppressed = false
    var linuxMousePresent = false
    var linuxManualCapture = false
    val linuxRelativeMotion = moe.n4tsu.dextop.linux.LinuxRelativeMotion()
    val linuxPointer = LinuxPointerState()
    /** Container whose Steam Deck Mode this window wraps: gamescope as its content, other Steam windows inside it. */
    var steamMode: String? = null
    /** X window id when this window shows one Linux application window (0: the whole desktop). */
    var linuxXid = 0
    var linuxAwaitingFirstFrame = false
    var linuxWMClass = ""
    /** The Kickoff entry ("linuxapp:…") this Linux window was launched from, when known. */
    var linuxLaunchKey: String? = null
    var linuxWindowIcon: android.graphics.drawable.Drawable? = null
    var linuxClientDecorated = false
    var linuxFullscreen = false
    var linuxFullscreenRestore: Rect? = null
    var linuxMoveResize = -1
    /** Physical host coordinates of the press that initiated a client drag. */
    var linuxPressPosition: android.graphics.PointF? = null
    fun decorationHeight(titleHeight: Int) = if (linuxClientDecorated || linuxFullscreen) 0 else titleHeight
    /** When Dextop last moved or resized the X window, to tell its own ConfigureNotify from the client's. */
    var linuxConfiguredAt = 0L

    val view = V2WindowView(context, host, this, titleHeight, grip)

    /** Model shared with the panel, tooltips and the Alt+Tab switcher. */
    val model = ManagedWindow(
        TaskSnapshot(id, packageName, null, null, Rect(), 1, 1, true, false, null, 0, -1),
    )

    fun syncModel(active: Boolean) {
        model.task = model.task.copy(packageName = packageName, label = title, bounds = Rect(frame), focused = active, displayId = displayId)
        model.frame.set(frame)
        model.minimized = minimized
        model.desktop = desktop
        model.onAllDesktops = onAllDesktops
        model.lastActivated = lastActivated
        model.label = title
        model.icon = icon
        model.maximized = maximized
    }

    fun clientWidth() = frame.width()
    fun clientHeight(titleHeight: Int) = (frame.height() - decorationHeight(titleHeight)).coerceAtLeast(1)
}

/** Breeze window: shadowed rounded chrome with a title bar above the app surface. */
@SuppressLint("ViewConstructor")
internal class V2WindowView(
    context: Context,
    private val host: V2WindowHost,
    private val window: V2Window,
    private val titleHeight: Int,
    private val grip: Int,
) : FrameLayout(context) {
    val chrome = LinearLayout(context)
    val titleBar = V2TitleBar(context, host, window)
    val content = object : TextureView(context) {
        override fun onPointerCaptureChange(hasCapture: Boolean) {
            super.onPointerCaptureChange(hasCapture)
            if (!hasCapture) host.captureLost(window)
        }
        override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
            super.onWindowFocusChanged(hasWindowFocus)
            if (!hasWindowFocus) { releasePointerCapture(); host.captureLost(window) }
        }
    }
    /** Holds [content] and what covers it (status, errors, Steam Deck Mode's screen and windows). */
    val contentFrame = FrameLayout(context)
    /** Auto Deck output is a child SurfaceControl, composed by SurfaceFlinger rather
     * than sampled into the host window by TextureView. The transparent TextureView
     * remains the input target, preserving pointer capture and coordinate mapping. */
    private var deckSurface: android.view.SurfaceView? = null
    val usesDeckSurface get() = deckSurface != null

    fun enableAutoDeckSurface() {
        check(window.steamMode != null)
        if (deckSurface != null) return
        host.onSurfaceDestroyed(window)
        window.surface?.release()
        window.surface = null
        val output = android.view.SurfaceView(context)
        deckSurface = output
        output.setZOrderOnTop(false)
        output.holder.setFormat(android.graphics.PixelFormat.OPAQUE)
        output.holder.addCallback(object : android.view.SurfaceHolder.Callback {
            override fun surfaceCreated(holder: android.view.SurfaceHolder) = Unit
            override fun surfaceChanged(holder: android.view.SurfaceHolder, format: Int, width: Int, height: Int) {
                // Holder owns this Surface; never release it from the TextureView path.
                window.surface = holder.surface
                holder.surface.setFrameRate(display?.refreshRate ?: 60f, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
                host.onSurfaceReady(window, holder.surface, width, height)
                android.util.Log.i("DextopPlasmaV2", "Deck SurfaceControl ready id=${window.id} ${width}x$height")
            }
            override fun surfaceDestroyed(holder: android.view.SurfaceHolder) {
                host.onSurfaceDestroyed(window)
                window.surface = null
            }
        })
        contentFrame.addView(output, 0, LayoutParams(-1, -1))
        content.alpha = 0f
    }

    private var status: View? = null
    private var statusText: TextView? = null
    private var statusLogText: TextView? = null
    private var statusLogScroll: android.widget.ScrollView? = null
    private val statusLog = LaunchLogTail()
    private var contentFramePosted = false
    private var resizing = false
    private var contentMoving = false
    private var gripMoving = false
    /** Mouse button/hover events are a separate stream from Android touch. */
    var capturesFramePointer = false
        private set
    private var framePointerDevice = -1
    fun ownsFramePointer(event: MotionEvent) = capturesFramePointer && framePointerDevice == event.deviceId &&
        event.isFromSource(InputDevice.SOURCE_MOUSE)
    private val density = resources.displayMetrics.density

    init {
        clipChildren = false
        setWillNotDraw(false)
        clipToPadding = false
        setPadding(grip, grip, grip, grip)
        chrome.orientation = LinearLayout.VERTICAL
        chrome.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val corners = clientCorners?.takeIf { window.linuxClientDecorated && !window.linuxFullscreen } ?: shownCorners
                if (corners != null && corners.any { it != corners[0] }) {
                    // Different corners (a split window keeps its inner ones round): a path outline.
                    cornerPath.reset()
                    cornerPath.addRoundRect(0f, 0f, view.width.toFloat(), view.height.toFloat(), floatArrayOf(
                        corners[0], corners[0], corners[1], corners[1], corners[2], corners[2], corners[3], corners[3],
                    ), android.graphics.Path.Direction.CW)
                    outline.setPath(cornerPath)
                    return
                }
                val radius = corners?.get(0) ?: host.windowCornerRadius(window)
                outline.setRoundRect(0, 0, view.width, view.height, if (radius >= 0f) radius else 6 * density)
            }
        }
        chrome.clipToOutline = true
        chrome.setBackgroundColor(host.palette.windowBackground)
        chrome.addView(titleBar, LinearLayout.LayoutParams(-1, titleHeight))
        contentFrame.addView(content, LayoutParams(-1, -1))
        chrome.addView(contentFrame, LinearLayout.LayoutParams(-1, 0, 1f))
        addView(chrome, LayoutParams(-1, -1))
        content.isOpaque = window.linuxXid == 0
        content.isFocusable = true
        content.isFocusableInTouchMode = true
        content.setOnCapturedPointerListener { _, event -> host.forwardCapturedInput(window, event); true }
        content.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                if (usesDeckSurface) return
                val surface = Surface(texture)
                window.surface = surface
                host.onSurfaceReady(window, surface, width, height)
                refreshLinuxViewport()
            }

            override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
                scheduleCornerProbe()
                refreshLinuxViewport()
            }

            override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                if (usesDeckSurface) return true
                host.onSurfaceDestroyed(window)
                window.surface?.release()
                window.surface = null
                return true
            }

            override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {
                if (usesDeckSurface) return
                // TextureView invokes this while ViewGroup is drawing its children. The
                // first-frame handler removes the launch cover: defer that tree mutation.
                if (!contentFramePosted) {
                    contentFramePosted = true
                    post {
                        contentFramePosted = false
                        if (isAttachedToWindow && !window.closing) host.onContentFrame(window)
                    }
                }
                if (cornerProbeDue) scheduleCornerProbe()
            }
        }
        content.setOnTouchListener { view, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) host.activate(window)
            if (!moveClientWindow(event)) host.forwardInput(window, event, view)
            true
        }
        content.setOnGenericMotionListener { view, event ->
            if (!moveClientWindow(event)) host.forwardInput(window, event, view)
            true
        }
        content.setOnHoverListener { view, event ->
            if (!moveClientWindow(event)) host.forwardInput(window, event, view)
            true
        }
        refreshElevation(active = true)
    }

    private var steamCaptionPress: android.graphics.PointF? = null

    /** Alt+drag also moves clients that expose no usable title-bar drag area. */
    private fun moveClientWindow(event: MotionEvent): Boolean {
        if (!window.linuxClientDecorated || window.linuxXid == 0 || window.linuxFullscreen) return false
        val down = event.actionMasked == MotionEvent.ACTION_DOWN ||
            event.actionMasked == MotionEvent.ACTION_BUTTON_PRESS && event.actionButton == MotionEvent.BUTTON_PRIMARY
        if (down) {
            val bx = event.x * window.linuxWidth / content.width.coerceAtLeast(1)
            val by = event.y * window.linuxHeight / content.height.coerceAtLeast(1)
            steamCaptionPress = if (LinuxClientCaption.steam(window.linuxWMClass, window.title,
                    window.linuxWidth, bx, by)) android.graphics.PointF(event.rawX, event.rawY) else null
        }
        val press = steamCaptionPress
        if (!contentMoving && window.linuxMoveResize < 0 && press != null &&
            event.actionMasked in setOf(MotionEvent.ACTION_MOVE, MotionEvent.ACTION_HOVER_MOVE) &&
            kotlin.math.hypot(event.rawX - press.x, event.rawY - press.y) > android.view.ViewConfiguration.get(context).scaledTouchSlop) {
            // Steam's custom caption does not always issue _NET_WM_MOVERESIZE.
            // Let clicks through, but take a real drag over its blank caption
            // region. Release the guest press before the host owns the drag.
            val cancel = MotionEvent.obtain(event)
            try { cancel.action = MotionEvent.ACTION_CANCEL; host.forwardInput(window, cancel, content) }
            finally { cancel.recycle() }
            contentMoving = true
            host.activate(window)
            host.beginMove(window, press.x, press.y)
            steamCaptionPress = null
        }
        if (event.actionMasked in setOf(MotionEvent.ACTION_UP, MotionEvent.ACTION_BUTTON_RELEASE, MotionEvent.ACTION_CANCEL)) {
            steamCaptionPress = null
        }
        if (!contentMoving && event.metaState and android.view.KeyEvent.META_ALT_ON != 0 &&
            (event.actionMasked == MotionEvent.ACTION_DOWN ||
                event.actionMasked == MotionEvent.ACTION_BUTTON_PRESS && event.actionButton == MotionEvent.BUTTON_PRIMARY)) {
            contentMoving = true
            host.activate(window)
            host.beginMove(window, event.rawX, event.rawY)
            return true
        }
        if (!contentMoving) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_HOVER_MOVE -> host.move(window, event.rawX, event.rawY)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_BUTTON_RELEASE, MotionEvent.ACTION_CANCEL -> {
                host.endMove(window, event.rawX, event.rawY)
                contentMoving = false
            }
        }
        return true
    }

    /** Shows [text] with a spinner over the content (for example while a Linux desktop starts); null hides it. */
    fun showStatus(text: String?) {
        if (text == null) {
            status?.let(contentFrame::removeView)
            status = null
            statusText = null
            statusLogText = null
            statusLogScroll = null
            statusLog.clear()
            return
        }
        statusText?.let { it.text = text; return }
        status?.let(contentFrame::removeView)
        statusLog.clear()
        val label = TextView(context).apply {
            this.text = text
            setTextColor(host.palette.text)
            textSize = 14f
            gravity = Gravity.CENTER
        }
        val spinner = ProgressBar(context).apply {
            isIndeterminate = true
            indeterminateTintList = android.content.res.ColorStateList.valueOf(host.palette.accent)
        }
        val details = TextView(context).apply {
            setTextColor(host.palette.text)
            alpha = .7f
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setPadding((16 * density).toInt(), 0, (16 * density).toInt(), 0)
        }
        val logs = object : android.widget.ScrollView(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val limit = details.lineHeight * 5 + details.compoundPaddingTop + details.compoundPaddingBottom
                val available = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) limit
                    else minOf(limit, MeasureSpec.getSize(heightMeasureSpec))
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST))
            }
        }.apply {
            addView(details, LayoutParams(-1, -2))
            visibility = View.GONE
        }
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(host.palette.windowBackground)
            addView(spinner, LinearLayout.LayoutParams((40 * density).toInt(), (40 * density).toInt()))
            addView(label, LinearLayout.LayoutParams(-2, -2).apply { topMargin = (12 * density).toInt() })
            addView(logs, LinearLayout.LayoutParams(-1, -2).apply { topMargin = (12 * density).toInt() })
            // Input goes to the desktop only once it is shown.
            isClickable = true
        }
        contentFrame.addView(panel, LayoutParams(-1, -1))
        status = panel
        statusText = label
        statusLogText = details
        statusLogScroll = logs
    }

    fun appendStatusLog(line: String) {
        val label = statusLogText ?: return
        val text = statusLog.append(line)
        if (label.text == text) return
        label.text = text
        statusLogScroll?.let { scroll ->
            scroll.visibility = View.VISIBLE
            scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    /** Keep failures visible and allow selecting/copying diagnostics. */
    fun showError(message: String) {
        showStatus(null)
        val label = TextView(context).apply {
            text = message
            setTextColor(host.palette.text)
            textSize = 13f
            setTextIsSelectable(true)
            setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
        }
        val panel = android.widget.ScrollView(context).apply {
            setBackgroundColor(host.palette.windowBackground)
            addView(label)
            isClickable = true
        }
        contentFrame.addView(panel, LayoutParams(-1, -1))
        status = panel
    }

    /** The corners follow the window's state (square while maximized or tiled in Book). */
    private val cornerPath = android.graphics.Path()

    /**
     * The corners a Linux app with its own frame draws (LinuxCornerProbe), in view pixels; the
     * window clips to them so the capture outside its round corners does not show. Measured
     * once its frames arrive and again when its size changes (maximized apps square them).
     */
    private var clientCorners: FloatArray? = null
    private var cornerProbeDue = true
    private val cornerProbe = Runnable {
        if (!window.linuxClientDecorated || window.linuxFullscreen || content.width <= 0 || content.height <= 0) return@Runnable
        cornerProbeDue = false
        val bitmap = runCatching { content.getBitmap(content.width, content.height) }.getOrNull() ?: return@Runnable
        val radii = try { LinuxCornerProbe.radii(bitmap, (48 * density).toInt()) } finally { bitmap.recycle() }
        if (clientCorners?.contentEquals(radii) == true) return@Runnable
        clientCorners = radii
        chrome.clipToOutline = true
        chrome.invalidateOutline()
    }

    private fun scheduleCornerProbe() {
        if (!window.linuxClientDecorated || window.linuxFullscreen) return
        cornerProbeDue = true
        removeCallbacks(cornerProbe)
        postDelayed(cornerProbe, 250L)
    }
    /** The corner radii drawn now, animated towards the host's; null without per-corner radii. */
    private var shownCorners: FloatArray? = null
    private var cornerTarget: FloatArray? = null
    private var cornerAnimator: ValueAnimator? = null

    /** The corners follow the window's state; a change rounds or sharpens them smoothly. */
    fun refreshCorners() {
        val target = host.windowCorners(window)
        if (target == null) { shownCorners = null; chrome.invalidateOutline(); return }
        val current = shownCorners
        if (current == null) { shownCorners = target.copyOf(); cornerTarget = target; chrome.invalidateOutline(); return }
        if (cornerTarget?.contentEquals(target) == true) { chrome.invalidateOutline(); return }
        cornerTarget = target
        val from = current.copyOf()
        cornerAnimator?.cancel()
        cornerAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 280L
            interpolator = android.view.animation.PathInterpolator(.2f, 0f, 0f, 1f)
            addUpdateListener { animator ->
                val t = animator.animatedValue as Float
                shownCorners = FloatArray(4) { i -> from[i] + (target[i] - from[i]) * t }
                chrome.invalidateOutline()
            }
            start()
        }
    }

    private var shadowActive: Boolean? = null
    private var shadowFlat: Boolean? = null

    fun refreshElevation(active: Boolean) {
        val flat = !host.windowShadows || host.flatWindow(window)
        if (host.bookStyle) {
            // Book: a soft Material shadow; the active window rises a little, smoothly.
            if (active == shadowActive && flat == shadowFlat) return
            val first = shadowActive == null
            shadowActive = active
            shadowFlat = flat
            chrome.outlineAmbientShadowColor = 0x40000000
            chrome.outlineSpotShadowColor = 0x59000000
            chrome.elevation = if (flat) 0f else 4 * density
            val lift = if (flat || !active) 0f else 6 * density
            if (first) chrome.translationZ = lift
            else chrome.animate().translationZ(lift).setDuration(220L).start()
            titleBar.invalidate()
            return
        }
        chrome.elevation = if (flat) 0f else if (active) 22 * density else 9 * density
        titleBar.invalidate()
    }

    fun refreshDecorations() {
        content.isOpaque = window.linuxXid == 0
        titleBar.visibility = if (window.linuxClientDecorated || window.linuxFullscreen) View.GONE else View.VISIBLE
        chrome.clipToOutline = !window.linuxFullscreen && (!window.linuxClientDecorated || clientCorners != null)
        if (window.linuxClientDecorated) scheduleCornerProbe()
        chrome.setBackgroundColor(if (window.linuxFullscreen) android.graphics.Color.BLACK
            else if (window.linuxClientDecorated) android.graphics.Color.TRANSPARENT else host.palette.windowBackground)
        refreshLinuxViewport()
        requestLayout()
        invalidate()
    }

    fun refreshLinuxViewport() {
        if (window.linuxXid == 0 || content.width <= 0 || content.height <= 0) return
        val viewport = LinuxWindowViewport(window.linuxWidth.coerceAtLeast(1), window.linuxHeight.coerceAtLeast(1),
            content.width, content.height, window.linuxFullscreen)
        content.setTransform(android.graphics.Matrix().apply {
            setScale(viewport.width / content.width, viewport.height / content.height,
                content.width / 2f, content.height / 2f)
        })
    }

    /**
     * Apps that draw their own frame are moved by it (_NET_WM_MOVERESIZE), or with Alt+drag
     * ([moveClientWindow]); Dextop adds no bar of its own above them.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun isMoveGrip(x: Float, y: Float) = false

    private fun edgeAt(x: Float, y: Float): V2Edge? {
        if (window.linuxFullscreen) return null
        val w = width.toFloat()
        val h = height.toFloat()
        val corner = grip + 14 * density
        val left = x < grip
        val right = x > w - grip
        val top = y < grip
        val bottom = y > h - grip
        return when {
            (left && y < corner) || (top && x < corner) -> V2Edge.TOP_LEFT
            (right && y < corner) || (top && x > w - corner) -> V2Edge.TOP_RIGHT
            (left && y > h - corner) || (bottom && x < corner) -> V2Edge.BOTTOM_LEFT
            (right && y > h - corner) || (bottom && x > w - corner) -> V2Edge.BOTTOM_RIGHT
            left -> V2Edge.LEFT
            right -> V2Edge.RIGHT
            top -> V2Edge.TOP
            bottom -> V2Edge.BOTTOM
            else -> null
        }
    }

    private fun isFramePoint(x: Float, y: Float): Boolean =
        isMoveGrip(x, y) || edgeAt(x, y) != null ||
            (!window.linuxClientDecorated && y >= grip && y < grip + titleHeight)

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            capturesFramePointer = isFramePoint(event.x, event.y)
            framePointerDevice = event.deviceId
        }
        super.dispatchTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            capturesFramePointer = false
        }
        // Empty chrome/padding is still part of the front window. Returning
        // false lets ViewGroup deliver the same event to a window underneath.
        return true
    }

    private fun frameMouseEvent(event: MotionEvent): Boolean {
        if (!event.isFromSource(InputDevice.SOURCE_MOUSE)) return false
        if (capturesFramePointer && !ownsFramePointer(event)) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_BUTTON_PRESS -> if (event.actionButton == MotionEvent.BUTTON_PRIMARY &&
                isFramePoint(event.x, event.y)) {
                // A normal mouse also sends ACTION_DOWN; do not start twice.
                if (!capturesFramePointer) frameMouseAsTouch(event, MotionEvent.ACTION_DOWN)
                return true
            }
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_HOVER_MOVE -> if (capturesFramePointer) {
                // Relayed mice may omit buttonState on motion. Only an
                // explicit up/release/cancel ends the owned gesture.
                frameMouseAsTouch(event, MotionEvent.ACTION_MOVE)
                return true
            }
            MotionEvent.ACTION_BUTTON_RELEASE -> if (capturesFramePointer && event.actionButton == MotionEvent.BUTTON_PRIMARY) {
                frameMouseAsTouch(event, MotionEvent.ACTION_UP)
                return true
            }
            MotionEvent.ACTION_CANCEL -> if (capturesFramePointer) {
                frameMouseAsTouch(event, MotionEvent.ACTION_CANCEL)
                return true
            }
        }
        return capturesFramePointer
    }

    private fun frameMouseAsTouch(event: MotionEvent, action: Int) {
        val copy = MotionEvent.obtain(event)
        try {
            copy.action = action
            dispatchTouchEvent(copy)
        } finally {
            copy.recycle()
        }
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (frameMouseEvent(event)) return true
        super.dispatchGenericMotionEvent(event)
        return true
    }

    override fun dispatchHoverEvent(event: MotionEvent): Boolean {
        if (frameMouseEvent(event)) return true
        super.dispatchHoverEvent(event)
        return true
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean =
        event.actionMasked == MotionEvent.ACTION_DOWN && (isMoveGrip(event.x, event.y) || edgeAt(event.x, event.y) != null)

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (isMoveGrip(event.x, event.y)) {
                    gripMoving = true
                    host.activate(window)
                    host.beginMove(window, event.rawX, event.rawY)
                    return true
                }
                val edge = edgeAt(event.x, event.y) ?: return true
                host.activate(window)
                resizing = true
                host.beginResize(window, edge, event.rawX, event.rawY)
            }
            MotionEvent.ACTION_MOVE -> if (gripMoving) host.move(window, event.rawX, event.rawY)
                else if (resizing) host.resize(window, event.rawX, event.rawY)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (gripMoving) host.endMove(window, event.rawX, event.rawY)
                gripMoving = false
                if (resizing) host.endResize(window)
                resizing = false
            }
        }
        return true
    }

    override fun onResolvePointerIcon(event: MotionEvent, pointerIndex: Int): PointerIcon? {
        if (isMoveGrip(event.getX(pointerIndex), event.getY(pointerIndex)))
            return PointerIcon.getSystemIcon(context, PointerIcon.TYPE_GRAB)
        val type = when (edgeAt(event.getX(pointerIndex), event.getY(pointerIndex))) {
            V2Edge.LEFT, V2Edge.RIGHT -> PointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW
            V2Edge.TOP, V2Edge.BOTTOM -> PointerIcon.TYPE_VERTICAL_DOUBLE_ARROW
            V2Edge.TOP_LEFT, V2Edge.BOTTOM_RIGHT -> PointerIcon.TYPE_TOP_LEFT_DIAGONAL_DOUBLE_ARROW
            V2Edge.TOP_RIGHT, V2Edge.BOTTOM_LEFT -> PointerIcon.TYPE_TOP_RIGHT_DIAGONAL_DOUBLE_ARROW
            null -> return super.onResolvePointerIcon(event, pointerIndex)
        }
        return PointerIcon.getSystemIcon(context, type)
    }
}

/** Breeze title bar drawn inside Dextop's own window. */
@SuppressLint("ViewConstructor")
internal class V2TitleBar(
    context: Context,
    private val host: V2WindowHost,
    private val window: V2Window,
) : View(context) {
    private enum class Button { BACK, MINIMIZE, MAXIMIZE, CLOSE }

    /** The buttons this window shows: Back only for Android applications. */
    private val buttons get() = Button.entries.filter { it != Button.BACK || host.hasBackButton(window) }

    private val density = resources.displayMetrics.density
    private fun dp(value: Float) = value * density
    private val background = Paint(Paint.ANTI_ALIAS_FLAG)
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = PlasmaTheme.sans; textAlign = Paint.Align.CENTER }
    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint().apply { strokeWidth = 1f }
    private val glyphs = mapOf(
        Button.MINIMIZE to GlyphDrawable(Glyph.MINIMIZE, 0, 1.5f),
        Button.MAXIMIZE to GlyphDrawable(Glyph.MAXIMIZE, 0, 1.5f),
        Button.CLOSE to GlyphDrawable(Glyph.CLOSE, 0, 1.5f),
        Button.BACK to GlyphDrawable(Glyph.BACK, 0, 1.5f),
    )
    private val restoreGlyph = GlyphDrawable(Glyph.RESTORE, 0, 1.4f)
    private var hovered: Button? = null
    private var pressed: Button? = null
    private val hoverProgress = HashMap<Button, Float>()
    private var hoverAnimator: ValueAnimator? = null
    private var activeFraction = 1f
    private var lastActive: Boolean? = null
    private var activeAnimator: ValueAnimator? = null
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var lastClickAt = 0L
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val rect = RectF()
    private val path = Path()

    init {
        setOnHoverListener { _, event ->
            val next = if (event.actionMasked == MotionEvent.ACTION_HOVER_EXIT) null else buttonAt(event.x, event.y)
            if (next != hovered) {
                hovered = next
                animateHover()
                removeCallbacks(snapMenu)
                if (next == Button.MAXIMIZE && host.bookStyle) postDelayed(snapMenu, 450L)
            }
            true
        }
    }

    /** Book: resting on (or holding) the maximize button offers the snap layouts. */
    private val snapMenu = Runnable {
        val r = buttonRect(Button.MAXIMIZE)
        val origin = IntArray(2).also(::getLocationOnScreen)
        pressed = null
        invalidate()
        host.showSnapMenu(window, RectF(origin[0] + r.left, origin[1] + r.top, origin[0] + r.right, origin[1] + r.bottom))
    }

    private fun buttonRect(button: Button): RectF {
        val book = host.bookStyle
        val size = dp(if (book) 32f else 20f)
        val gap = dp(if (book) 4f else 6f)
        val index = when (button) { Button.CLOSE -> 0; Button.MAXIMIZE -> 1; Button.MINIMIZE -> 2; Button.BACK -> 3 }
        val cx = width - dp(if (book) 10f else 7f) - size / 2f - index * (size + gap)
        val cy = height / 2f
        return RectF(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f)
    }

    private fun buttonAt(x: Float, y: Float): Button? = buttons.firstOrNull { button ->
        buttonRect(button).apply { inset(-dp(3f), -dp(4f)) }.contains(x, y)
    }

    private fun animateHover() {
        hoverAnimator?.cancel()
        val start = HashMap(hoverProgress)
        hoverAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = PlasmaTheme.SHORT_MS
            addUpdateListener { animator ->
                val t = animator.animatedValue as Float
                Button.entries.forEach { button ->
                    val from = start[button] ?: 0f
                    hoverProgress[button] = from + ((if (button == hovered) 1f else 0f) - from) * t
                }
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val palette = host.palette
        val active = host.isActive(window)
        if (lastActive == null) activeFraction = if (active) 1f else 0f
        else if (lastActive != active) {
            activeAnimator?.cancel()
            activeAnimator = ValueAnimator.ofFloat(activeFraction, if (active) 1f else 0f).apply {
                duration = PlasmaTheme.LONG_MS
                interpolator = PlasmaTheme.outCubic
                addUpdateListener { activeFraction = it.animatedValue as Float; invalidate() }
                start()
            }
        }
        lastActive = active
        val w = width.toFloat()
        val h = height.toFloat()
        if (host.bookStyle) {
            drawBook(canvas, palette, active, w, h)
            return
        }
        background.color = PlasmaTheme.blend(palette.headerInactive, palette.headerActive, activeFraction)
        canvas.drawRect(0f, 0f, w, h, background)
        line.color = PlasmaTheme.withAlpha(palette.text, 0x18)
        canvas.drawLine(0f, h - .5f, w, h - .5f, line)
        val iconSize = dp(16f)
        rect.set(dp(8f), (h - iconSize) / 2f, dp(8f) + iconSize, (h + iconSize) / 2f)
        canvas.drawIcon(window.icon, rect, if (active) 255 else 170)
        titlePaint.textSize = dp(13f)
        titlePaint.color = PlasmaTheme.blend(palette.inactiveText, palette.text, activeFraction)
        val leftLimit = dp(32f)
        val rightLimit = w - dp(buttons.size * 26f + 10f)
        val title = TextUtilsCompat.ellipsize(window.title, titlePaint, (rightLimit - leftLimit).coerceAtLeast(0f))
        val half = titlePaint.measureText(title) / 2f
        val x = (w / 2f).coerceIn(leftLimit + half, (rightLimit - half).coerceAtLeast(leftLimit + half))
        canvas.drawText(title, x, h / 2f - (titlePaint.descent() + titlePaint.ascent()) / 2f, titlePaint)
        buttons.forEach { button ->
            val r = buttonRect(button)
            val hover = hoverProgress[button] ?: 0f
            val isPressed = pressed == button
            val glyph = if (button == Button.MAXIMIZE && window.maximized) restoreGlyph else glyphs.getValue(button)
            val baseColor = PlasmaTheme.blend(palette.inactiveText, palette.text, activeFraction)
            if (button == Button.CLOSE) {
                buttonPaint.color = PlasmaTheme.withAlpha(palette.negative, ((if (isPressed) 1f else hover) * 255).toInt())
                glyph.color = PlasmaTheme.blend(baseColor, 0xFFFFFFFF.toInt(), if (isPressed) 1f else hover)
            } else {
                buttonPaint.color = PlasmaTheme.withAlpha(palette.text, ((if (isPressed) .38f else hover * .2f) * 255).toInt())
                glyph.color = baseColor
            }
            canvas.drawOval(r, buttonPaint)
            glyph.setBounds(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt())
            glyph.draw(canvas)
        }
        path.reset()
    }

    /** Material 3: one tonal surface with the window, title at the start, tonal circular buttons. */
    private val chevron = GlyphDrawable(Glyph.CHEVRON_DOWN, 0, 1.6f, true)
    private val bookGlyph = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val restoreStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.7f
        strokeJoin = Paint.Join.ROUND
    }

    /** Android's "restore" glyph on the 24-unit grid: a window with another behind it. */
    private fun drawRestore(canvas: Canvas) {
        restoreStroke.color = bookGlyph.color
        rect.set(5f, 8.5f, 15.5f, 19f)
        canvas.drawRoundRect(rect, 1.6f, 1.6f, restoreStroke)
        path.reset()
        path.moveTo(8.5f, 8.5f)
        path.lineTo(8.5f, 6.6f)
        path.quadTo(8.5f, 5f, 10.1f, 5f)
        path.lineTo(17.4f, 5f)
        path.quadTo(19f, 5f, 19f, 6.6f)
        path.lineTo(19f, 13.9f)
        path.quadTo(19f, 15.5f, 17.4f, 15.5f)
        path.lineTo(15.5f, 15.5f)
        canvas.drawPath(path, restoreStroke)
    }
    /** Right edge of Book's icon, name and chevron, which open the window menu. */
    private var menuRight = 0f

    private fun drawBook(canvas: Canvas, palette: PlasmaPalette, active: Boolean, w: Float, h: Float) {
        background.color = PlasmaTheme.blend(palette.headerInactive, palette.headerActive, activeFraction)
        canvas.drawRect(0f, 0f, w, h, background)
        val iconSize = dp(20f)
        rect.set(dp(12f), (h - iconSize) / 2f, dp(12f) + iconSize, (h + iconSize) / 2f)
        canvas.drawIcon(window.icon, rect, if (active) 255 else 170)
        titlePaint.textAlign = Paint.Align.LEFT
        titlePaint.typeface = PlasmaTheme.sansMedium
        titlePaint.textSize = dp(14f)
        titlePaint.color = PlasmaTheme.blend(palette.inactiveText, palette.text, activeFraction)
        val left = dp(42f)
        val right = buttonRect(buttons.first()).left - dp(28f)
        val title = TextUtilsCompat.ellipsize(window.title, titlePaint, (right - left).coerceAtLeast(0f))
        canvas.drawText(title, left, h / 2f - (titlePaint.descent() + titlePaint.ascent()) / 2f, titlePaint)
        // The name and its chevron open the window menu, as on a Pixel laptop.
        val chevronLeft = left + titlePaint.measureText(title) + dp(4f)
        val chevronSize = dp(18f)
        chevron.color = titlePaint.color
        chevron.setBounds(chevronLeft.toInt(), ((h - chevronSize) / 2f).toInt(), (chevronLeft + chevronSize).toInt(), ((h + chevronSize) / 2f).toInt())
        chevron.draw(canvas)
        menuRight = chevronLeft + chevronSize + dp(4f)
        titlePaint.textAlign = Paint.Align.CENTER
        titlePaint.typeface = PlasmaTheme.sans
        buttons.forEach { button ->
            val r = buttonRect(button)
            val hover = hoverProgress[button] ?: 0f
            val isPressed = pressed == button
            // As in Android's desktop windowing: plain outline glyphs, a soft circle under the pointer.
            buttonPaint.color = PlasmaTheme.withAlpha(palette.text, ((if (isPressed) .2f else hover * .12f) * 255).toInt())
            canvas.drawOval(r, buttonPaint)
            bookGlyph.color = PlasmaTheme.blend(palette.inactiveText, palette.text, activeFraction)
            val tiled = window.maximized || window.tile != TileZone.NONE
            val name = when (button) {
                Button.BACK -> "arrow_back"
                Button.MINIMIZE -> "remove"
                Button.MAXIMIZE -> if (tiled) null else "crop_square"
                Button.CLOSE -> "close"
            }
            val size = r.width() * .6f
            canvas.save()
            canvas.translate(r.centerX() - size / 2f, r.centerY() - size / 2f)
            canvas.scale(size / 24f, size / 24f)
            if (name != null) GlyphPainter.symbolPath(name, false)?.let { canvas.drawPath(it, bookGlyph) }
            else drawRestore(canvas)
            canvas.restore()
        }
    }

    /** The release of a right-click belongs to the window menu, not to a title-bar button. */
    private var secondaryGesture = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (secondaryGesture) {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) secondaryGesture = false
            return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                host.activate(window)
                if (event.isButtonPressed(MotionEvent.BUTTON_SECONDARY)) {
                    secondaryGesture = true
                    host.showWindowMenu(window, event.rawX, event.rawY)
                    return true
                }
                if (event.isButtonPressed(MotionEvent.BUTTON_TERTIARY)) {
                    host.minimize(window)
                    return true
                }
                pressed = buttonAt(event.x, event.y)
                if (pressed == Button.MAXIMIZE && host.bookStyle) postDelayed(snapMenu, ViewConfiguration.getLongPressTimeout().toLong())
                downX = event.rawX
                downY = event.rawY
                dragging = false
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (pressed != null) {
                    if (buttonAt(event.x, event.y) != pressed) { pressed = null; invalidate() }
                    return true
                }
                if (!dragging && (kotlin.math.abs(event.rawX - downX) > slop || kotlin.math.abs(event.rawY - downY) > slop)) {
                    dragging = true
                    host.beginMove(window, downX, downY)
                }
                if (dragging) host.move(window, event.rawX, event.rawY)
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(snapMenu)
                val button = pressed
                pressed = null
                invalidate()
                when {
                    button != null && buttonAt(event.x, event.y) == button -> when (button) {
                        Button.BACK -> host.goBack(window)
                        Button.MINIMIZE -> host.minimize(window)
                        Button.MAXIMIZE -> host.toggleMaximize(window)
                        Button.CLOSE -> host.close(window)
                    }
                    dragging -> host.endMove(window, event.rawX, event.rawY)
                    event.x < (if (host.bookStyle) menuRight else dp(30f)) -> host.showWindowMenu(window, event.rawX, event.rawY)
                    event.eventTime - lastClickAt < ViewConfiguration.getDoubleTapTimeout() -> {
                        host.toggleMaximize(window)
                        lastClickAt = 0L
                    }
                    else -> lastClickAt = event.eventTime
                }
                dragging = false
            }
            MotionEvent.ACTION_CANCEL -> {
                if (dragging) host.endMove(window, event.rawX, event.rawY)
                pressed = null
                dragging = false
                invalidate()
            }
        }
        return true
    }
}
