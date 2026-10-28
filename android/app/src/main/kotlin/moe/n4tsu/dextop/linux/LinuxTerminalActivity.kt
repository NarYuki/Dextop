package moe.n4tsu.dextop.linux

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Base64
import android.util.Log
import android.view.ViewGroup
import android.text.InputType
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.FrameLayout
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.Executors

/**
 * An interactive shell of a Linux container, in its own Android window: the shell runs on a
 * pseudo-terminal in the shell-side service, and xterm.js (assets/terminal) draws it.
 */
internal class LinuxTerminalActivity : Activity() {
    companion object {
        private const val TAG = "DextopLinuxTerminal"
        private const val EXTRA_CONTAINER = "container"
        private const val EXTRA_NAME = "name"

        fun open(context: Context, container: String, name: String?) {
            context.startActivity(Intent(context, LinuxTerminalActivity::class.java)
                .putExtra(EXTRA_CONTAINER, container)
                .putExtra(EXTRA_NAME, name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                    (if (context is Activity) 0 else Intent.FLAG_ACTIVITY_NEW_TASK)))
        }
    }

    private lateinit var web: WebView
    private var container = ""
    private var name = ""
    private var pty: ParcelFileDescriptor? = null
    private var output: FileOutputStream? = null
    private val writer = Executors.newSingleThreadExecutor()
    @Volatile private var closed = false

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        container = intent.getStringExtra(EXTRA_CONTAINER).orEmpty()
        name = intent.getStringExtra(EXTRA_NAME) ?: container
        if (container.isEmpty()) return finish()
        describe(name)
        web = TerminalView(this).apply {
            setBackgroundColor(Color.rgb(0x10, 0x14, 0x18))
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            addJavascriptInterface(Bridge(), "Dextop")
        }
        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(0x10, 0x14, 0x18)) }
        root.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars() or
                android.view.WindowInsets.Type.displayCutout() or android.view.WindowInsets.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(root)
        web.loadUrl("file:///android_asset/terminal/index.html")
    }

    private fun describe(title: String) {
        @Suppress("DEPRECATION")
        setTaskDescription(ActivityManager.TaskDescription(title))
        this.title = title
    }

    /** Opens the shell once the page knows its size. */
    private fun start(rows: Int, cols: Int) {
        LinuxContainerClient.connect(this)
        LinuxContainerClient.call(this, { it.openTerminal(container, rows, cols) }, { descriptor ->
            if (closed) { descriptor.close(); return@call }
            pty = descriptor
            output = FileOutputStream(descriptor.fileDescriptor)
            Thread(::read, "DextopTerminalRead").apply { isDaemon = true; start() }
        }) { error ->
            notice(error.message ?: error.toString())
        }
    }

    private fun read() {
        val descriptor = pty ?: return
        val buffer = ByteArray(16 * 1024)
        runCatching {
            FileInputStream(descriptor.fileDescriptor).use { input ->
                while (!closed) {
                    val count = input.read(buffer)
                    if (count <= 0) break
                    val chunk = Base64.encodeToString(buffer, 0, count, Base64.NO_WRAP)
                    runOnUiThread { web.evaluateJavascript("dextopWrite('$chunk')", null) }
                }
            }
        }.onFailure { if (!closed) Log.i(TAG, "terminal ended: ${it.message}") }
        // The shell exited (EIO on the master side).
        if (!closed) runOnUiThread {
            notice(if (java.util.Locale.getDefault().language == "ja") "シェルが終了しました" else "The shell exited")
        }
    }

    /** Types [keys] into the terminal (xterm.js picks the cursor-mode variant and sends it on). */
    private fun type(keys: LinuxTerminalKeys.Keys) {
        val normal = org.json.JSONObject.quote(keys.normal)
        val application = keys.application?.let(org.json.JSONObject::quote) ?: "null"
        web.evaluateJavascript("dextopKey($normal,$application)", null)
    }

    private fun type(text: String) = type(LinuxTerminalKeys.Keys(text))

    /** Handles a key for the terminal; false for keys Android keeps (Back, volume…). */
    private fun key(event: KeyEvent): Boolean {
        when (event.keyCode) {
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_APP_SWITCH -> return false
        }
        if (event.action == KeyEvent.ACTION_MULTIPLE) {
            event.characters?.let { type(it.replace("\n", "\r")) }
            return true
        }
        if (event.action == KeyEvent.ACTION_DOWN && event.isCtrlPressed && event.isShiftPressed) {
            // Ctrl+Shift+C / V copy and paste, as in desktop terminals.
            when (event.keyCode) {
                KeyEvent.KEYCODE_C -> { web.evaluateJavascript("dextopCopy()", null); return true }
                KeyEvent.KEYCODE_V -> { web.evaluateJavascript("dextopPaste()", null); return true }
            }
        }
        LinuxTerminalKeys.encode(event)?.let(::type)
        // Modifier and unmapped keys are consumed too, so the page never sees them.
        return true
    }

    /**
     * The WebView draws the terminal but takes no text itself: keys and the IME's text come
     * here and are typed into xterm.js, so composing input is never sent twice.
     */
    @SuppressLint("ViewConstructor")
    private inner class TerminalView(context: Context) : WebView(context) {
        override fun onCheckIsTextEditor() = true

        override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
            outAttrs.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN or
                EditorInfo.IME_ACTION_NONE
            return object : BaseInputConnection(this, false) {
                override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
                    compose("")
                    if (text.isNotEmpty()) type(text.toString().replace("\n", "\r"))
                    return true
                }

                override fun setComposingText(text: CharSequence, newCursorPosition: Int): Boolean {
                    compose(text.toString())
                    return true
                }

                override fun finishComposingText(): Boolean = true

                override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                    if (beforeLength > 0) type("\u007f".repeat(beforeLength.coerceAtMost(256)))
                    return true
                }

                override fun sendKeyEvent(event: KeyEvent): Boolean = key(event)

                override fun performEditorAction(actionCode: Int): Boolean {
                    type("\r")
                    return true
                }
            }
        }

        override fun dispatchKeyEventPreIme(event: KeyEvent): Boolean {
            // A hardware keyboard types straight into the terminal, never through the IME.
            val physical = event.deviceId != KeyCharacterMap.VIRTUAL_KEYBOARD && event.device?.isVirtual == false
            return (physical && key(event)) || super.dispatchKeyEventPreIme(event)
        }

        override fun dispatchKeyEvent(event: KeyEvent): Boolean = key(event) || super.dispatchKeyEvent(event)

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            val handled = super.onTouchEvent(event)
            // A tap with a finger brings up the soft keyboard.
            if (event.actionMasked == MotionEvent.ACTION_UP && !event.isFromSource(android.view.InputDevice.SOURCE_MOUSE)) {
                requestFocus()
                context.getSystemService(InputMethodManager::class.java)?.showSoftInput(this, 0)
            }
            return handled
        }
    }

    private fun compose(text: String) {
        web.evaluateJavascript("dextopCompose(${org.json.JSONObject.quote(text)})", null)
    }

    private fun notice(message: String) {
        val quoted = org.json.JSONObject.quote(message)
        web.evaluateJavascript("dextopNotice($quoted)", null)
    }

    private fun send(bytes: ByteArray) {
        writer.execute { runCatching { output?.write(bytes) } }
    }

    private inner class Bridge {
        @JavascriptInterface
        fun ready(rows: Int, cols: Int) = runOnUiThread { if (pty == null) start(rows, cols) }

        @JavascriptInterface
        fun input(data: String) = send(data.toByteArray(Charsets.UTF_8))

        @JavascriptInterface
        fun binary(data: String) = send(ByteArray(data.length) { data[it].code.toByte() })

        @JavascriptInterface
        fun resize(rows: Int, cols: Int) {
            val fd = pty?.fd ?: return
            if (rows > 0 && cols > 0) LinuxPty.nativeResize(fd, rows, cols)
        }

        @JavascriptInterface
        fun title(value: String) = runOnUiThread { describe(value.ifBlank { name }) }

        @JavascriptInterface
        fun fontSize(): Int = 14

        @JavascriptInterface
        fun copy(text: String) {
            if (text.isEmpty()) return
            getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Linux terminal", text))
        }

        @JavascriptInterface
        fun paste(): String = runCatching {
            getSystemService(ClipboardManager::class.java)?.primaryClip
                ?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this@LinuxTerminalActivity)?.toString()
        }.getOrNull().orEmpty()
    }

    override fun onBackPressed() {
        // Back keeps the shell, like any app in the background.
        moveTaskToBack(true)
    }

    override fun onDestroy() {
        closed = true
        // Closing the master side hangs up the shell.
        runCatching { pty?.close() }
        writer.shutdown()
        web.destroy()
        super.onDestroy()
    }
}
