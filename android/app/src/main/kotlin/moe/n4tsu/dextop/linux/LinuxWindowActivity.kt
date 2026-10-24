package moe.n4tsu.dextop.linux

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.Surface
import android.view.TextureView
import android.graphics.SurfaceTexture
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * One Linux application window as an Android task. Its surface shows the X window, which is
 * kept at this window's place and size on the screen, so menus open where they belong.
 */
internal open class LinuxWindowActivity : Activity() {
    companion object {
        const val EXTRA_CONTAINER = "container"
        const val EXTRA_DESKTOP = "desktop"
        const val EXTRA_LABEL = "label"
        const val EXTRA_XID = "xid"
        private val BUTTONS = listOf(
            MotionEvent.BUTTON_PRIMARY to 1, MotionEvent.BUTTON_TERTIARY to 2, MotionEvent.BUTTON_SECONDARY to 3,
            MotionEvent.BUTTON_BACK to 8, MotionEvent.BUTTON_FORWARD to 9,
        )
        private const val KEY_BACKSPACE = 14
        private const val KEY_ENTER = 28
    }

    var container = ""
        private set
    private var xid = 0
    private var closedByLinux = false
    private lateinit var root: FrameLayout
    private lateinit var content: LinuxSurface
    private lateinit var status: LinearLayout
    private lateinit var statusText: TextView
    private val popupViews = HashMap<Int, LinuxTexture>()
    private val popupBounds = HashMap<Int, Rect>()
    /** Open dialogs, bottom to top, with the size each asked for. */
    private val dialogViews = LinkedHashMap<Int, LinuxTexture>()
    private val dialogRequests = HashMap<Int, Rect>()
    private val dialogPlaced = HashMap<Int, Rect>()
    /** Light grey veil right under the topmost dialog; it keeps touches from the windows below. */
    private lateinit var scrim: View
    private val lastPlace = Rect()

    private val input get() = "$container#apps"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        container = intent.getStringExtra(EXTRA_CONTAINER).orEmpty()
        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        content = LinuxSurface(this) { xid }
        root.addView(content, FrameLayout.LayoutParams(-1, -1))
        scrim = View(this).apply {
            setBackgroundColor(Color.argb(110, 190, 190, 190))
            visibility = View.GONE
            isClickable = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        root.addView(scrim, FrameLayout.LayoutParams(-1, -1))
        status = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(ProgressBar(context))
            statusText = TextView(context).apply {
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                gravity = Gravity.CENTER
                setPadding(48, 24, 48, 0)
            }
            addView(statusText)
        }
        root.addView(status, FrameLayout.LayoutParams(-1, -1))
        root.addView(ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_edit)
            alpha = .55f
            setBackgroundColor(0x66000000)
            contentDescription = "Keyboard"
            setOnClickListener { toggleKeyboard() }
        }, FrameLayout.LayoutParams(dp(44), dp(44), Gravity.BOTTOM or Gravity.END).apply {
            setMargins(0, 0, dp(12), dp(12))
        })
        setContentView(root)
        // Android draws apps edge to edge; the X window stays clear of the bars, the cutout and the keyboard.
        root.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars() or
                android.view.WindowInsets.Type.displayCutout() or android.view.WindowInsets.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        root.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            // A resized window first offers the application all of its room again.
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                val params = content.layoutParams as FrameLayout.LayoutParams
                if (params.width != -1 || params.height != -1) {
                    params.width = -1
                    params.height = -1
                    content.layoutParams = params
                }
            }
            place()
        }

        val label = intent.getStringExtra(EXTRA_LABEL)
        statusText.text = label.orEmpty()
        label?.let { setTaskDescription(ActivityManager.TaskDescription.Builder().setLabel(it).build()) }
        val requested = savedInstanceState?.getInt(EXTRA_XID, 0)
            ?.takeIf { it != 0 } ?: intent.getIntExtra(EXTRA_XID, 0)
        val desktop = intent.getStringExtra(EXTRA_DESKTOP)
        when {
            requested != 0 -> LinuxAndroidWindows.restore(this, container, requested)
            desktop != null && savedInstanceState == null -> LinuxAndroidWindows.startApplication(this, container, desktop)
            else -> finish()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(EXTRA_XID, xid)
        super.onSaveInstanceState(outState)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    /** A dialog whose parent window is gone, shown on its own as a floating window. */
    protected open val floating = false

    /** Takes over the X window [info]; called once the application maps it. */
    fun bind(info: LinuxAndroidWindows.Info) {
        xid = info.xid
        // Keep the window identity even if Android recreates this task.
        intent.putExtra(EXTRA_XID, xid)
        LinuxAndroidWindows.attached(this, container, xid)
        describe(info)
        status.visibility = View.GONE
        if (floating) {
            // The window takes the dialog's own size (within the screen), so it is never stretched.
            val screen = windowManager.currentWindowMetrics.bounds
            val margin = dp(24)
            window.setLayout(
                info.bounds.width().coerceIn(dp(160), (screen.width() - margin * 2).coerceAtLeast(dp(160))),
                info.bounds.height().coerceIn(dp(80), (screen.height() - margin * 2).coerceAtLeast(dp(80))),
            )
        }
        content.surfaceReady()?.let { attach(content.holder.surface, xid) }
        place()
    }

    /**
     * The X window's actual size. An application that keeps a fixed size is shown at that
     * size in the middle of this window instead of being stretched to fill it.
     */
    fun windowSized(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val availableWidth = root.width - root.paddingLeft - root.paddingRight
        val availableHeight = root.height - root.paddingTop - root.paddingBottom
        if (availableWidth <= 0 || availableHeight <= 0) return
        val params = content.layoutParams as FrameLayout.LayoutParams
        val fills = width >= availableWidth - 1 && height >= availableHeight - 1
        val wantWidth = if (fills) -1 else width.coerceAtMost(availableWidth)
        val wantHeight = if (fills) -1 else height.coerceAtMost(availableHeight)
        if (params.width == wantWidth && params.height == wantHeight) return
        params.width = wantWidth
        params.height = wantHeight
        params.gravity = Gravity.CENTER
        content.layoutParams = params
    }

    fun describe(info: LinuxAndroidWindows.Info) {
        @Suppress("DEPRECATION")
        setTaskDescription(ActivityManager.TaskDescription(info.title.ifBlank { info.wmClass.ifBlank { "Linux" } }, info.icon))
    }

    fun showError(message: String) {
        status.visibility = View.VISIBLE
        status.getChildAt(0).visibility = View.GONE
        statusText.text = message
    }

    fun closedByLinux() {
        closedByLinux = true
        finishAndRemoveTask()
    }

    /** Where this window's content is on the screen, in X screen coordinates. */
    fun screenBounds(): Rect {
        val location = IntArray(2)
        content.getLocationOnScreen(location)
        return Rect(location[0], location[1], location[0] + content.width, location[1] + content.height)
    }

    /** Keeps the X window exactly under this window's content. */
    private fun place() {
        if (xid == 0 || content.width <= 0 || content.height <= 0) return
        val bounds = screenBounds()
        if (bounds == lastPlace) return
        lastPlace.set(bounds)
        LinuxContainerClient.service()?.runCatching {
            configureWindow(container, xid, bounds.left, bounds.top, bounds.width(), bounds.height())
        }
        dialogRequests.forEach { (dialog, rect) -> fitDialog(dialog, rect) }
        popupBounds.forEach { (popup, rect) -> placePopup(popup, rect) }
    }

    private fun attach(surface: android.view.Surface?, target: Int) {
        surface ?: return
        LinuxContainerClient.call(this, { it.attachWindow(container, target, surface) }, {}) { }
    }

    fun showPopup(popup: Int, bounds: Rect) {
        val view = popupViews.getOrPut(popup) {
            LinuxTexture(this) { popup }.also { root.addView(it, FrameLayout.LayoutParams(1, 1)) }
        }
        view.visibility = View.VISIBLE
        view.bringToFront()
        placePopup(popup, bounds)
    }

    /** Opens a dialog of this application over it, at most as large as this window allows. */
    fun showDialog(dialog: Int, requested: Rect) {
        if (dialogViews.containsKey(dialog)) return fitDialog(dialog, requested)
        val view = LinuxTexture(this) { dialog }
        root.addView(view, FrameLayout.LayoutParams(1, 1))
        dialogViews[dialog] = view
        fitDialog(dialog, requested)
        restack()
        view.alpha = 0f
        view.scaleX = .94f; view.scaleY = .94f
        view.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180).start()
        LinuxContainerClient.service()?.runCatching { activateWindow(container, dialog) }
    }

    /** Centres the dialog in this window, shrunk to leave a margin when it asks for more room. */
    fun fitDialog(dialog: Int, requested: Rect) {
        dialogRequests[dialog] = Rect(requested)
        val view = dialogViews[dialog] ?: return
        val area = screenBounds()
        if (area.width() <= 0 || area.height() <= 0) return
        val margin = dp(24)
        val maxWidth = (area.width() - margin * 2).coerceAtLeast(dp(120))
        val maxHeight = (area.height() - margin * 2).coerceAtLeast(dp(120))
        val width = (if (requested.width() > 0) requested.width() else area.width() * 7 / 10).coerceIn(1, maxWidth)
        val height = (if (requested.height() > 0) requested.height() else area.height() * 7 / 10).coerceIn(1, maxHeight)
        val left = (area.width() - width) / 2
        val top = (area.height() - height) / 2
        val params = view.layoutParams as FrameLayout.LayoutParams
        params.width = width
        params.height = height
        params.leftMargin = left
        params.topMargin = top
        params.gravity = Gravity.TOP or Gravity.START
        view.layoutParams = params
        val placed = Rect(area.left + left, area.top + top, area.left + left + width, area.top + top + height)
        if (dialogPlaced[dialog] == placed) return
        dialogPlaced[dialog] = placed
        LinuxContainerClient.service()?.runCatching { configureWindow(container, dialog, placed.left, placed.top, width, height) }
    }

    fun closeDialog(dialog: Int) {
        dialogRequests.remove(dialog)
        dialogPlaced.remove(dialog)
        val view = dialogViews.remove(dialog) ?: return
        LinuxContainerClient.service()?.runCatching { detachWindow(container, dialog) }
        view.animate().alpha(0f).scaleX(.96f).scaleY(.96f).setDuration(140).withEndAction { root.removeView(view) }.start()
        restack()
        val next = dialogViews.keys.lastOrNull() ?: xid
        if (next != 0) LinuxContainerClient.service()?.runCatching { activateWindow(container, next) }
    }

    /** Puts the veil right under the topmost dialog, and menus above everything. */
    private fun restack() {
        val top = dialogViews.values.lastOrNull()
        if (top == null) {
            if (scrim.visibility == View.VISIBLE) scrim.animate().alpha(0f).setDuration(140).withEndAction { scrim.visibility = View.GONE }.start()
        } else {
            root.removeView(scrim)
            root.addView(scrim, root.indexOfChild(top), FrameLayout.LayoutParams(-1, -1))
            if (scrim.visibility != View.VISIBLE) {
                scrim.alpha = 0f
                scrim.visibility = View.VISIBLE
            }
            scrim.animate().alpha(1f).setDuration(160).start()
        }
        popupViews.values.forEach(View::bringToFront)
    }

    fun placePopup(popup: Int, bounds: Rect) {
        popupBounds[popup] = Rect(bounds)
        val view = popupViews[popup] ?: return
        val origin = screenBounds()
        val params = view.layoutParams as FrameLayout.LayoutParams
        params.width = bounds.width().coerceAtLeast(1)
        params.height = bounds.height().coerceAtLeast(1)
        params.leftMargin = bounds.left - origin.left
        params.topMargin = bounds.top - origin.top
        params.gravity = Gravity.TOP or Gravity.START
        view.layoutParams = params
    }

    fun removePopup(popup: Int) {
        popupBounds.remove(popup)
        popupViews.remove(popup)?.let { view ->
            LinuxContainerClient.service()?.runCatching { detachWindow(container, popup) }
            root.removeView(view)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || xid == 0) return
        LinuxAndroidWindows.focused(this)
        LinuxContainerClient.service()?.runCatching { activateWindow(container, xid) }
        content.requestFocus()
    }

    override fun onBackPressed() {
        // Back leaves the window open, like any other application in the background.
        moveTaskToBack(true)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode in setOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN,
                KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_APP_SWITCH) || xid == 0)
            return super.dispatchKeyEvent(event)
        val code = LinuxKeymap.evdev(event) ?: return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_MULTIPLE) return super.dispatchKeyEvent(event)
        LinuxContainerClient.service()?.runCatching { key(input, code, event.action == KeyEvent.ACTION_DOWN) }
        return true
    }

    private fun toggleKeyboard() {
        content.requestFocus()
        getSystemService(InputMethodManager::class.java)?.toggleSoftInput(0, 0)
    }

    override fun onDestroy() {
        LinuxAndroidWindows.gone(this)
        popupViews.keys.toList().forEach(::removePopup)
        dialogViews.keys.forEach { dialog -> LinuxContainerClient.service()?.runCatching { detachWindow(container, dialog) } }
        if (xid != 0) LinuxContainerClient.service()?.runCatching {
            detachWindow(container, xid)
            // Closing the task closes the window; one the application closed itself is gone already.
            if (isFinishing && !closedByLinux) closeWindow(container, xid)
        }
        super.onDestroy()
    }

    /** Pointer and wheel input for one X window, in that window's own coordinates. */
    private inner class PointerInput(private val target: () -> Int) {
        private var buttons = 0
        private var pressed = false
        private var scrolling = false
        private var lastX = 0f
        private var lastY = 0f
        private var scrollX = 0f
        private var scrollY = 0f
        private val step = 48 * resources.displayMetrics.density

        private fun send(action: Int, x: Float, y: Float, button: Int) {
            val id = target()
            if (id == 0) return
            LinuxContainerClient.service()?.runCatching { windowPointer(container, id, action, x.roundToInt(), y.roundToInt(), button) }
        }

        private fun wheel(dx: Int, dy: Int) {
            if (dx == 0 && dy == 0) return
            LinuxContainerClient.service()?.runCatching { scroll(input, dx, dy) }
        }

        fun touch(event: MotionEvent): Boolean {
            if (event.isFromSource(InputDevice.SOURCE_MOUSE)) return mouse(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    scrolling = false
                    lastX = event.x; lastY = event.y
                    send(0, event.x, event.y, 0)
                    send(1, event.x, event.y, 1)
                    pressed = true
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    // A second finger turns the touch into scrolling.
                    if (pressed) { send(2, lastX, lastY, 1); pressed = false }
                    scrolling = true
                    scrollX = 0f; scrollY = 0f
                    lastX = event.getX(0); lastY = event.getY(0)
                }
                MotionEvent.ACTION_MOVE -> if (scrolling) {
                    scrollX += (lastX - event.getX(0)) / step
                    scrollY += (lastY - event.getY(0)) / step
                    lastX = event.getX(0); lastY = event.getY(0)
                    val dx = scrollX.toInt()
                    val dy = scrollY.toInt()
                    scrollX -= dx; scrollY -= dy
                    wheel(dx, dy)
                } else {
                    lastX = event.x; lastY = event.y
                    send(0, event.x, event.y, 0)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (pressed) send(2, event.x, event.y, 1)
                    pressed = false
                    scrolling = false
                }
            }
            return true
        }

        private fun mouse(event: MotionEvent): Boolean {
            send(0, event.x, event.y, 0)
            val current = if (event.actionMasked == MotionEvent.ACTION_CANCEL) 0 else event.buttonState
            for ((bit, button) in BUTTONS) if ((buttons and bit != 0) != (current and bit != 0))
                send(if (current and bit != 0) 1 else 2, event.x, event.y, button)
            buttons = current
            return true
        }

        fun generic(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_HOVER_MOVE -> send(0, event.x, event.y, 0)
                MotionEvent.ACTION_SCROLL -> {
                    val dy = -event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                    val dx = event.getAxisValue(MotionEvent.AXIS_HSCROLL)
                    wheel(if (abs(dx) >= 1) dx.toInt() else 0,
                        if (abs(dy) >= .5f) (if (dy > 0) 1 else -1) * maxOf(1, abs(dy).toInt()) else 0)
                }
                else -> return false
            }
            return true
        }
    }

    /** The main window: a surface showing the X window, taking pointer, wheel and text input. */
    @SuppressLint("ViewConstructor")
    private inner class LinuxSurface(context: Context, private val target: () -> Int) :
        SurfaceView(context), SurfaceHolder.Callback {
        private var ready = false
        private val pointer = PointerInput(target)

        init {
            holder.addCallback(this)
            isFocusable = true
            isFocusableInTouchMode = true
        }

        fun surfaceReady(): SurfaceView? = this.takeIf { ready }

        override fun surfaceCreated(holder: SurfaceHolder) { ready = true }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            ready = true
            val id = target()
            if (id != 0) attach(holder.surface, id)
            place()
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            ready = false
            val id = target()
            if (id != 0) LinuxContainerClient.service()?.runCatching { detachWindow(container, id) }
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) requestFocus()
            return pointer.touch(event)
        }

        override fun onGenericMotionEvent(event: MotionEvent) = pointer.generic(event) || super.onGenericMotionEvent(event)

        override fun onCheckIsTextEditor() = true

        override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
            outAttrs.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
            return object : BaseInputConnection(this, false) {
                override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
                    if (text.toString() == "\n") tap(KEY_ENTER)
                    else LinuxContainerClient.service()?.runCatching { text(input, text.toString()) }
                    return true
                }

                override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                    repeat(beforeLength.coerceAtMost(64)) { tap(KEY_BACKSPACE) }
                    return true
                }

                override fun sendKeyEvent(event: KeyEvent): Boolean {
                    val code = LinuxKeymap.evdev(event) ?: return false
                    LinuxContainerClient.service()?.runCatching { key(input, code, event.action == KeyEvent.ACTION_DOWN) }
                    return true
                }

                override fun performEditorAction(actionCode: Int): Boolean { tap(KEY_ENTER); return true }
            }
        }

        private fun tap(code: Int) {
            LinuxContainerClient.service()?.runCatching { key(input, code, true); key(input, code, false) }
        }
    }

    /**
     * A dialog or menu drawn inside the view hierarchy, so it stacks above the main window and
     * the veil like any other view.
     */
    @SuppressLint("ViewConstructor")
    private inner class LinuxTexture(context: Context, private val target: () -> Int) :
        TextureView(context), TextureView.SurfaceTextureListener {
        private var surface: Surface? = null
        private val pointer = PointerInput(target)

        init { surfaceTextureListener = this }

        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
            surface = Surface(texture)
            attach(surface, target())
        }

        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
            attach(surface, target())
        }

        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
            LinuxContainerClient.service()?.runCatching { detachWindow(container, target()) }
            surface?.release()
            surface = null
            return true
        }

        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent) = pointer.touch(event)

        override fun onGenericMotionEvent(event: MotionEvent) = pointer.generic(event) || super.onGenericMotionEvent(event)
    }
}

/** A Linux dialog shown on its own (its parent window is gone), as a floating window. */
internal class LinuxDialogActivity : LinuxWindowActivity() {
    override val floating = true
}
