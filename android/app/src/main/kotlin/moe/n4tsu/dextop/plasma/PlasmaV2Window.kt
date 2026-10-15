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
import android.view.PointerIcon
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.LinearLayout
import moe.n4tsu.dextop.OwnedVirtualDisplay

internal enum class V2Edge { LEFT, RIGHT, TOP, BOTTOM, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

/** Window-manager callbacks from one composited window. */
internal interface V2WindowHost {
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
    fun forwardInput(window: V2Window, event: MotionEvent, source: View)
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
    var emptySince = 0L
    val createdAt = SystemClock.uptimeMillis()
    /** Task to pull onto this window's display once it exists (adopted strays). */
    var pendingAdoption: TaskSnapshot? = null
    /** Intent to start instead of the package's launcher activity (settings screens). */
    var launchIntent: android.content.Intent? = null
    var adoptAttempted = false

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
    fun clientHeight(titleHeight: Int) = (frame.height() - titleHeight).coerceAtLeast(1)
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
    val content = TextureView(context)
    private var resizing = false
    private val density = resources.displayMetrics.density

    init {
        clipChildren = false
        clipToPadding = false
        setPadding(grip, grip, grip, grip)
        chrome.orientation = LinearLayout.VERTICAL
        chrome.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, 6 * density)
            }
        }
        chrome.clipToOutline = true
        chrome.setBackgroundColor(host.palette.windowBackground)
        chrome.addView(titleBar, LinearLayout.LayoutParams(-1, titleHeight))
        chrome.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        addView(chrome, LayoutParams(-1, -1))
        content.isOpaque = true
        content.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                val surface = Surface(texture)
                window.surface = surface
                host.onSurfaceReady(window, surface, width, height)
            }

            override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit

            override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                host.onSurfaceDestroyed(window)
                window.surface?.release()
                window.surface = null
                return true
            }

            override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
        }
        content.setOnTouchListener { view, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) host.activate(window)
            host.forwardInput(window, event, view)
            true
        }
        content.setOnGenericMotionListener { view, event ->
            host.forwardInput(window, event, view)
            true
        }
        content.setOnHoverListener { view, event ->
            host.forwardInput(window, event, view)
            true
        }
        refreshElevation(active = true)
    }

    fun refreshElevation(active: Boolean) {
        chrome.elevation = if (active) 22 * density else 9 * density
        titleBar.invalidate()
    }

    private fun edgeAt(x: Float, y: Float): V2Edge? {
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

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean =
        event.actionMasked == MotionEvent.ACTION_DOWN && edgeAt(event.x, event.y) != null

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val edge = edgeAt(event.x, event.y) ?: return false
                host.activate(window)
                resizing = true
                host.beginResize(window, edge, event.rawX, event.rawY)
            }
            MotionEvent.ACTION_MOVE -> if (resizing) host.resize(window, event.rawX, event.rawY)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (resizing) host.endResize(window)
                resizing = false
            }
        }
        return true
    }

    override fun onResolvePointerIcon(event: MotionEvent, pointerIndex: Int): PointerIcon? {
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
