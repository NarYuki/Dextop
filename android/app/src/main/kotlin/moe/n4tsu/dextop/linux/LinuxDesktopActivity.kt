package moe.n4tsu.dextop.linux

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import moe.n4tsu.dextop.plasma.LinuxPointerForwarder
import moe.n4tsu.dextop.plasma.LinuxPointerState
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * A container's Linux desktop as an Android window of its own: on the phone, in DeX or any
 * desktop mode, without Plasma v2. The X screen follows the window's size; touch, mouse and
 * keyboard go to Linux as in Plasma's desktop windows.
 */
class LinuxDesktopActivity : Activity() {
    companion object {
        private const val EXTRA_CONTAINER = "container"
        private const val EXTRA_NAME = "name"

        /**
         * Opens [container]'s desktop. A DroidDeck container's desktop is its Plasma application,
         * opened like any Linux application; the others get this window, one per container.
         */
        fun open(context: Context, container: String, name: String) {
            LinuxContainerClient.call(context, { linux ->
                JSONObject(linux.linuxSettings()).optJSONObject("containers")?.optJSONObject(container)?.optString("wmEffective")
            }, { wm ->
                if (wm == LinuxWindowManager.DROIDDECK.id) {
                    LinuxAndroidWindows.launch(context, container, LinuxSteam.DROIDDECK_DESKTOP_ENTRY, name)
                } else start(context, container, name)
            }) { start(context, container, name) }
        }

        private fun start(context: Context, container: String, name: String) {
            context.startActivity(Intent(context, LinuxDesktopActivity::class.java)
                // One task per container: opening it again brings that desktop back.
                .setData(Uri.parse("dextop-linux-desktop://$container"))
                .putExtra(EXTRA_CONTAINER, container)
                .putExtra(EXTRA_NAME, name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
                    (if (context is Activity) 0 else Intent.FLAG_ACTIVITY_NEW_TASK)))
        }
    }

    private lateinit var container: String
    private lateinit var screen: DesktopSurface
    private lateinit var status: TextView
    private val pointer = LinuxPointerState()
    private var screenWidth = 0
    private var screenHeight = 0
    private var generation = 0

    private fun text(ja: String, en: String) = moe.n4tsu.dextop.localText(ja, en)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        container = intent.getStringExtra(EXTRA_CONTAINER)?.takeIf { it.matches(Regex("c[a-zA-Z0-9]+")) } ?: return finish()
        val name = intent.getStringExtra(EXTRA_NAME).orEmpty().ifBlank { "Linux" }
        @Suppress("DEPRECATION")
        setTaskDescription(ActivityManager.TaskDescription(name))
        title = name
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        screen = DesktopSurface(this)
        root.addView(screen, FrameLayout.LayoutParams(-1, -1))
        status = TextView(this).apply {
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            text = text("Linux のデスクトップを起動しています", "Starting the Linux desktop")
        }
        root.addView(status, FrameLayout.LayoutParams(-1, -1))
        val density = resources.displayMetrics.density
        val keyboard = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_edit)
            setBackgroundColor(0x66000000)
            contentDescription = text("キーボード", "Keyboard")
            alpha = .75f
            setOnClickListener { screen.showKeyboard() }
        }
        root.addView(keyboard, FrameLayout.LayoutParams((44 * density).roundToInt(), (44 * density).roundToInt(),
            Gravity.BOTTOM or Gravity.END).apply { setMargins(0, 0, (12 * density).roundToInt(), (12 * density).roundToInt()) })
        // Clear of the system bars and the keyboard, as other Linux windows are.
        root.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars() or
                android.view.WindowInsets.Type.displayCutout() or android.view.WindowInsets.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(root)
    }

    override fun onDestroy() {
        if (::container.isInitialized && isFinishing) {
            LinuxContainerClient.service()?.runCatching { detachSurface(container) }
        }
        super.onDestroy()
    }

    /**
     * Starts the desktop for a window of [width] x [height] view pixels (or resizes it) and
     * draws it into [surface]. A phone has 2–3 device pixels per dp: an X screen at that full
     * resolution makes IceWM, its icons and every fixed-size bitmap tiny while text follows
     * the DPI. The X screen is the window's size divided by a whole scale instead (about
     * 1.5 device pixels per X pixel at most), its DPI the same density in X pixels, and the
     * surface keeps the X screen's pixels for SurfaceFlinger to scale up evenly.
     */
    private fun show(surface: Surface, width: Int, height: Int) {
        val density = resources.displayMetrics.density
        val scale = (density / 1.5f).roundToInt().coerceAtLeast(1)
        val w = (width / scale).coerceAtLeast(320)
        val h = (height / scale).coerceAtLeast(240)
        val dpi = (96 * density / scale).roundToInt().coerceIn(72, 480)
        screen.holder.setFixedSize(w, h)
        val refresh = display?.refreshRate ?: 60f
        val attempt = ++generation
        LinuxContainerClient.call(this, { linux ->
            linux.startDesktop(container, w, h, dpi)
            linux.updateDisplay(container, false, w, h, dpi, refresh)
            check(linux.attachSurface(container, surface)) { text("Linux のデスクトップを表示できませんでした", "Unable to draw the Linux desktop") }
        }, {
            if (attempt != generation) return@call
            screenWidth = w
            screenHeight = h
            status.visibility = View.GONE
        }) { error ->
            if (attempt != generation) return@call
            status.visibility = View.VISIBLE
            status.text = error.message ?: error.toString()
        }
    }

    private fun forward(event: MotionEvent): Boolean {
        val linux = LinuxContainerClient.service() ?: return false
        if (screenWidth <= 0 || screen.width <= 0) return true
        fun x(value: Float) = (value * screenWidth / screen.width).roundToInt().coerceIn(0, screenWidth - 1)
        fun y(value: Float) = (value * screenHeight / screen.height).roundToInt().coerceIn(0, screenHeight - 1)
        if (event.actionMasked == MotionEvent.ACTION_MOVE || event.actionMasked == MotionEvent.ACTION_HOVER_MOVE) {
            for (sample in 0 until event.historySize) {
                linux.pointer(container, 0, x(event.getHistoricalX(sample)), y(event.getHistoricalY(sample)), 0)
            }
        }
        LinuxPointerForwarder.forward(event, x(event.x), y(event.y), pointer, resources.displayMetrics.density,
            send = { action, px, py, button -> linux.pointer(container, action, px, py, button) },
            scroll = { dx, dy -> linux.scroll(container, dx, dy) },
            key = { code, down -> linux.key(container, code, down) })
        return true
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Back stays Android's; everything else is the desktop's.
        if (event.keyCode == KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(event)
        if (LinuxAndroidPads.dispatch(this, container, event)) return true
        val linux = LinuxContainerClient.service() ?: return super.dispatchKeyEvent(event)
        val down = event.action == KeyEvent.ACTION_DOWN
        val code = LinuxKeymap.evdev(event)
        runCatching {
            when {
                code != null -> linux.key(container, code, down)
                down && event.unicodeChar > 0 -> linux.text(container, String(Character.toChars(event.unicodeChar)))
                event.action == KeyEvent.ACTION_MULTIPLE && !event.characters.isNullOrEmpty() -> linux.text(container, event.characters)
                else -> return super.dispatchKeyEvent(event)
            }
        }
        return true
    }

    @SuppressLint("ViewConstructor", "ClickableViewAccessibility")
    private inner class DesktopSurface(context: Context) : SurfaceView(context), SurfaceHolder.Callback {
        init {
            holder.addCallback(this)
            isFocusable = true
            isFocusableInTouchMode = true
            setOnTouchListener { _, event -> forward(event) }
            setOnGenericMotionListener { _, event -> forward(event) }
        }

        fun showKeyboard() {
            requestFocus()
            getSystemService(InputMethodManager::class.java).showSoftInput(this, 0)
        }

        override fun onCheckIsTextEditor() = true

        /** The Android keyboard types into the desktop: text as text, deletions as Backspace. */
        override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
            outAttrs.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            return object : BaseInputConnection(this, false) {
                override fun commitText(value: CharSequence, newCursorPosition: Int): Boolean {
                    LinuxContainerClient.service()?.let { linux -> runCatching { linux.text(container, value.toString()) } }
                    return true
                }

                override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                    // KEY_BACKSPACE
                    LinuxContainerClient.service()?.let { linux ->
                        runCatching { repeat(beforeLength.coerceIn(1, 64)) { linux.key(container, 14, true); linux.key(container, 14, false) } }
                    }
                    return true
                }

                override fun sendKeyEvent(event: KeyEvent): Boolean = dispatchKeyEvent(event)
            }
        }

        override fun surfaceCreated(holder: SurfaceHolder) {}

        // With a fixed buffer, a resized window (rotation, DeX) does not change the surface by itself.
        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            if (w > 0 && h > 0) holder.surface?.takeIf { it.isValid }?.let { show(it, w, h) }
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            // The view's size decides the X screen; [width] x [height] is the fixed buffer set from it.
            if (this.width <= 0 || this.height <= 0) return
            val density = resources.displayMetrics.density
            val scale = (density / 1.5f).roundToInt().coerceAtLeast(1)
            if (width == (this.width / scale).coerceAtLeast(320) && height == (this.height / scale).coerceAtLeast(240) &&
                screenWidth == width && screenHeight == height) return
            show(holder.surface, this.width, this.height)
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            generation++
            LinuxContainerClient.service()?.runCatching { detachSurface(container) }
        }
    }
}
