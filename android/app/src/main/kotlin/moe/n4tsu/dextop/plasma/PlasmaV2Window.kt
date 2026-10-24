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
    var linuxWMClass = ""
    var linuxWindowIcon: android.graphics.drawable.Drawable? = null
    var linuxClientDecorated = false
    var linuxFullscreen = false
    var linuxFullscreenRestore: Rect? = null
    var linuxMoveResize = -1
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
    private var resizing = false
    private var contentMoving = false
    private var gripMoving = false
    /** Mouse button/hover events are a separate stream from Android touch. */
    var capturesFramePointer = false
        private set
    private var framePointerDevice = -1
    fun ownsFramePointer(event: MotionEvent) = capturesFramePointer && framePointerDevice == event.deviceId &&
        event.isFromSource(InputDevice.SOURCE_MOUSE)
    private val moveGripPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density

    init {
        clipChildren = false
        setWillNotDraw(false)
        clipToPadding = false
        setPadding(grip, grip, grip, grip)
        chrome.orientation = LinearLayout.VERTICAL
        chrome.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val radius = host.windowCornerRadius()
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
                refreshLinuxViewport()
            }

            override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                if (usesDeckSurface) return true
                host.onSurfaceDestroyed(window)
                window.surface?.release()
                window.surface = null
                return true
            }

            override fun onSurfaceTextureUpdated(texture: SurfaceTexture) { if (!usesDeckSurface) host.onContentFrame(window) }
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

    /** Alt+drag also moves clients that expose no usable title-bar drag area. */
    private fun moveClientWindow(event: MotionEvent): Boolean {
        if (!window.linuxClientDecorated || window.linuxXid == 0 || window.linuxFullscreen) return false
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
            return
        }
        statusText?.let { it.text = text; return }
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
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(host.palette.windowBackground)
            addView(spinner, LinearLayout.LayoutParams((40 * density).toInt(), (40 * density).toInt()))
            addView(label, LinearLayout.LayoutParams(-2, -2).apply { topMargin = (12 * density).toInt() })
            // Input goes to the desktop only once it is shown.
            isClickable = true
        }
        contentFrame.addView(panel, LayoutParams(-1, -1))
        status = panel
        statusText = label
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

    fun refreshElevation(active: Boolean) {
        chrome.elevation = if (!host.windowShadows) 0f else if (active) 22 * density else 9 * density
        titleBar.invalidate()
    }

    fun refreshDecorations() {
        content.isOpaque = window.linuxXid == 0
        titleBar.visibility = if (window.linuxClientDecorated || window.linuxFullscreen) View.GONE else View.VISIBLE
        chrome.clipToOutline = !window.linuxClientDecorated && !window.linuxFullscreen
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

    private val moveGripTop get() = (grip - window.frame.top).coerceAtLeast(0).toFloat()
    private fun isMoveGrip(x: Float, y: Float) = window.linuxClientDecorated && !window.linuxFullscreen &&
        y >= moveGripTop && y < moveGripTop + grip && x > grip + 14 * density && x < width - grip - 14 * density

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (!window.linuxClientDecorated || window.linuxFullscreen) return
        moveGripPaint.color = host.palette.inactiveText
        val half = 24 * density
        canvas.drawRoundRect(width / 2f - half, moveGripTop + grip * .25f, width / 2f + half, moveGripTop + grip * .75f,
            grip.toFloat(), grip.toFloat(), moveGripPaint)
    }

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
    private enum class Button { MINIMIZE, MAXIMIZE, CLOSE }

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
            }
            true
        }
    }

    private fun buttonRect(button: Button): RectF {
        val size = dp(20f)
        val gap = dp(6f)
        val index = when (button) { Button.CLOSE -> 0; Button.MAXIMIZE -> 1; Button.MINIMIZE -> 2 }
        val cx = width - dp(7f) - size / 2f - index * (size + gap)
        val cy = height / 2f
        return RectF(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f)
    }

    private fun buttonAt(x: Float, y: Float): Button? = Button.entries.firstOrNull { button ->
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
        val rightLimit = w - dp(3 * 26f + 10f)
        val title = TextUtilsCompat.ellipsize(window.title, titlePaint, (rightLimit - leftLimit).coerceAtLeast(0f))
        val half = titlePaint.measureText(title) / 2f
        val x = (w / 2f).coerceIn(leftLimit + half, (rightLimit - half).coerceAtLeast(leftLimit + half))
        canvas.drawText(title, x, h / 2f - (titlePaint.descent() + titlePaint.ascent()) / 2f, titlePaint)
        Button.entries.forEach { button ->
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

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                host.activate(window)
                if (event.isButtonPressed(MotionEvent.BUTTON_SECONDARY)) {
                    host.showWindowMenu(window, event.rawX, event.rawY)
                    return true
                }
                if (event.isButtonPressed(MotionEvent.BUTTON_TERTIARY)) {
                    host.minimize(window)
                    return true
                }
                pressed = buttonAt(event.x, event.y)
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
                val button = pressed
                pressed = null
                invalidate()
                when {
                    button != null && buttonAt(event.x, event.y) == button -> when (button) {
                        Button.MINIMIZE -> host.minimize(window)
                        Button.MAXIMIZE -> host.toggleMaximize(window)
                        Button.CLOSE -> host.close(window)
                    }
                    dragging -> host.endMove(window, event.rawX, event.rawY)
                    event.x < dp(30f) -> host.showWindowMenu(window, event.rawX, event.rawY)
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
