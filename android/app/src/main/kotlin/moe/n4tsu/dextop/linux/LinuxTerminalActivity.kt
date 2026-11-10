package moe.n4tsu.dextop.linux

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.ViewGroup
import android.text.InputType
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.FrameLayout

/**
 * The shells of a Linux container in one Android window, as tabs: each runs on a pseudo-terminal
 * in the shell-side service and xterm.js (assets/terminal) draws it. The shells belong to
 * [LinuxTerminalSessions], so they outlive this window.
 */
internal class LinuxTerminalActivity : Activity() {
    companion object {
        private const val EXTRA_CONTAINER = "container"
        private const val EXTRA_NAME = "name"
        /** Base64 output is sent to the page in pieces of this many bytes. */
        private const val CHUNK = 48 * 1024
        private const val PREFERENCES = "linux_terminal"
        private const val FONT_SIZE = "font_size"
        private const val DEFAULT_FONT_SIZE = 12

        /** Opens the container's terminal window, or brings it forward if it is already open. */
        fun open(context: Context, container: String, name: String?) {
            context.startActivity(Intent(context, LinuxTerminalActivity::class.java)
                .setData(Uri.fromParts("dextop-terminal", container, null))
                .putExtra(EXTRA_CONTAINER, container)
                .putExtra(EXTRA_NAME, name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
                    (if (context is Activity) 0 else Intent.FLAG_ACTIVITY_NEW_TASK)))
        }
    }

    private lateinit var web: WebView
    private lateinit var group: LinuxTerminalSessions.Group
    private var name = ""
    @Volatile private var selecting = false
    private var pageReady = false

    private val ime = LinuxTerminalIme({ type(it) }, { compose(it) })

    private val sink = object : LinuxTerminalSessions.Sink {
        override fun output(id: String, bytes: ByteArray, count: Int) {
            var offset = 0
            while (offset < count) {
                val length = minOf(CHUNK, count - offset)
                val chunk = Base64.encodeToString(bytes, offset, length, Base64.NO_WRAP)
                offset += length
                runOnUiThread { script("dextopWrite(${quote(id)},'$chunk')") }
            }
        }

        override fun notice(id: String, message: String) {
            runOnUiThread { script("dextopNotice(${quote(id)},${quote(message)})") }
        }

        override fun replayStart(id: String, rows: Int, cols: Int) {
            runOnUiThread { script("dextopReplayStart(${quote(id)},$rows,$cols)") }
        }

        override fun replayEnd(id: String, restored: String?) {
            runOnUiThread { script("dextopReplayEnd(${quote(id)},${restored?.let(::quote) ?: "null"})") }
        }

        override fun ended(id: String, remove: Boolean) = runOnUiThread {
            if (!remove) {
                script("dextopNotice(${quote(id)},${quote(moe.n4tsu.dextop.localText("uiTheShellEndedPressAKeyToStartANewOne"))})")
            } else if (group.tabs().isEmpty()) {
                finishAndRemoveTask()
            } else {
                script("dextopRemoveTab(${quote(id)})")
            }
        }
    }

    private fun quote(text: String) = org.json.JSONObject.quote(text)

    private fun script(code: String) {
        if (!isDestroyed) web.evaluateJavascript(code, null)
    }

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = intent.getStringExtra(EXTRA_CONTAINER).orEmpty()
        name = intent.getStringExtra(EXTRA_NAME) ?: container
        if (container.isEmpty()) return finish()
        group = LinuxTerminalSessions.group(applicationContext, container)
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

    private fun addTab(session: LinuxTerminalSessions.Session, activate: Boolean) {
        script("dextopAddTab(${quote(session.id)},${quote(session.title.ifBlank { name })},$activate)")
    }

    /** Shows every tab once the page can draw them; a container without tabs gets its first. */
    private fun showTabs() {
        if (pageReady) return
        pageReady = true
        group.attach(sink)
        if (group.tabs().isEmpty()) group.add(name)
        val active = group.active
        group.tabs().forEach { addTab(it, it.id == active) }
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
            ime.reset()
            event.characters?.let { type(it.replace("\n", "\r")) }
            return true
        }
        if (event.action == KeyEvent.ACTION_DOWN && !KeyEvent.isModifierKey(event.keyCode)) {
            ime.key(if (event.isCtrlPressed || event.isAltPressed) 0 else event.unicodeChar, event.keyCode == KeyEvent.KEYCODE_DEL)
        }
        if (event.action == KeyEvent.ACTION_DOWN && event.isCtrlPressed && event.keyCode == KeyEvent.KEYCODE_TAB) {
            web.evaluateJavascript("dextopCycleTab(${if (event.isShiftPressed) -1 else 1})", null)
            return true
        }
        if (event.action == KeyEvent.ACTION_DOWN && event.isCtrlPressed && event.isShiftPressed) {
            // Ctrl+Shift shortcuts, as in desktop terminals.
            when (event.keyCode) {
                KeyEvent.KEYCODE_C -> { web.evaluateJavascript("dextopCopy()", null); return true }
                KeyEvent.KEYCODE_V -> { web.evaluateJavascript("dextopPaste()", null); return true }
                KeyEvent.KEYCODE_T -> { web.evaluateJavascript("dextopNewTab()", null); return true }
                KeyEvent.KEYCODE_W -> { web.evaluateJavascript("dextopCloseTab()", null); return true }
            }
        }
        if (event.keyCode == KeyEvent.KEYCODE_INSERT && (event.isCtrlPressed || event.isShiftPressed)) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                web.evaluateJavascript(if (event.isShiftPressed) "dextopPaste()" else "dextopCopy()", null)
            }
            return true
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
        private var downX = 0f
        private var downY = 0f
        private var downTime = 0L
        private var multiTouch = false

        override fun onCheckIsTextEditor() = true

        override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
            outAttrs.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN or
                EditorInfo.IME_ACTION_NONE
            return object : BaseInputConnection(this, false) {
                override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
                    ime.commit(text)
                    return true
                }

                override fun setComposingText(text: CharSequence, newCursorPosition: Int): Boolean {
                    ime.compose(text)
                    return true
                }

                override fun setComposingRegion(start: Int, end: Int): Boolean {
                    ime.region(start, end)
                    return true
                }

                override fun finishComposingText(): Boolean {
                    ime.finish()
                    return true
                }

                override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                    ime.delete(beforeLength)
                    return true
                }

                override fun sendKeyEvent(event: KeyEvent): Boolean = key(event)

                override fun performContextMenuAction(id: Int): Boolean = when (id) {
                    android.R.id.copy -> { web.evaluateJavascript("dextopCopy()", null); true }
                    android.R.id.paste -> { web.evaluateJavascript("dextopPaste()", null); true }
                    android.R.id.selectAll -> { web.evaluateJavascript("dextopSelectAll()", null); true }
                    else -> super.performContextMenuAction(id)
                }

                override fun performEditorAction(actionCode: Int): Boolean {
                    ime.reset()
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
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y; downTime = event.eventTime; multiTouch = false }
                MotionEvent.ACTION_POINTER_DOWN -> multiTouch = true
                MotionEvent.ACTION_UP -> {
                    // Only a tap brings up the keyboard: after a scroll it would resize the terminal.
                    val slop = ViewConfiguration.get(context).scaledTouchSlop
                    val tap = Math.hypot((event.x - downX).toDouble(), (event.y - downY).toDouble()) <= slop &&
                        event.eventTime - downTime < ViewConfiguration.getLongPressTimeout()
                    if (tap && !multiTouch && !selecting && !event.isFromSource(android.view.InputDevice.SOURCE_MOUSE)) {
                        ime.reset()
                        requestFocus()
                        context.getSystemService(InputMethodManager::class.java)?.showSoftInput(this, 0)
                    }
                }
            }
            return handled
        }
    }

    private fun compose(text: String) {
        web.evaluateJavascript("dextopCompose(${org.json.JSONObject.quote(text)})", null)
    }

    private inner class Bridge {
        @JavascriptInterface
        fun translate(key: String): String = moe.n4tsu.dextop.localText(key, resources.configuration.locales[0])

        @JavascriptInterface
        fun ready() = runOnUiThread { showTabs() }

        /** The page has drawn a tab at this size: its output and shell can follow. */
        @JavascriptInterface
        fun opened(id: String, rows: Int, cols: Int) = runOnUiThread {
            group.session(id)?.let { group.open(this@LinuxTerminalActivity, it, rows, cols) }
        }

        @JavascriptInterface
        fun newTab() = runOnUiThread { addTab(group.add(name), true) }

        @JavascriptInterface
        fun closeTab(id: String) = runOnUiThread {
            if (group.close(id)) finishAndRemoveTask() else script("dextopRemoveTab(${quote(id)})")
        }

        @JavascriptInterface
        fun activate(id: String) = runOnUiThread {
            group.activate(id)
            group.session(id)?.let { describe(it.title.ifBlank { name }) }
        }

        @JavascriptInterface
        fun input(id: String, data: String) {
            val session = group.session(id) ?: return
            if (session.pty == null) runOnUiThread { group.restart(this@LinuxTerminalActivity, session) }
            else session.write(data.toByteArray(Charsets.UTF_8))
        }

        @JavascriptInterface
        fun binary(id: String, data: String) { group.session(id)?.write(ByteArray(data.length) { data[it].code.toByte() }) }

        @JavascriptInterface
        fun resize(id: String, rows: Int, cols: Int) { group.session(id)?.resize(rows, cols) }

        @JavascriptInterface
        fun title(id: String, value: String) = runOnUiThread {
            group.rename(id, value)
            if (group.active == id) describe(value.ifBlank { name })
        }

        @JavascriptInterface
        fun directory(id: String, path: String) = group.directory(id, path)

        @JavascriptInterface
        fun fontSize(): Int = getSharedPreferences(PREFERENCES, MODE_PRIVATE).getInt(FONT_SIZE, DEFAULT_FONT_SIZE)

        @JavascriptInterface
        fun setFontSize(size: Int) {
            getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit().putInt(FONT_SIZE, size.coerceIn(6, 32)).apply()
        }

        @JavascriptInterface
        fun copy(text: String) {
            if (text.isEmpty()) return
            runOnUiThread {
                getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(moe.n4tsu.dextop.localText("uiLinuxTerminal"), text))
            }
        }

        @JavascriptInterface
        fun selecting(enabled: Boolean) { selecting = enabled }

        @JavascriptInterface
        fun lineChanged() = runOnUiThread { ime.reset() }

        @JavascriptInterface
        fun openLink(url: String) {
            val uri = android.net.Uri.parse(url)
            if (uri.scheme?.lowercase() !in setOf("http", "https")) return
            runOnUiThread { runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) } }
        }

        @JavascriptInterface
        fun paste() = runOnUiThread {
            val text = runCatching {
                getSystemService(ClipboardManager::class.java)?.primaryClip
                    ?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this@LinuxTerminalActivity)?.toString()
            }.getOrNull().orEmpty()
            script("dextopPasteText(${quote(text)})")
        }
    }

    override fun onBackPressed() {
        // Back keeps the shell, like any app in the background.
        moveTaskToBack(true)
    }

    override fun onStop() {
        super.onStop()
        if (::group.isInitialized) group.saveNow()
    }

    override fun onDestroy() {
        // The shells stay with LinuxTerminalSessions; only this window's view of them ends.
        if (::group.isInitialized) group.detach(sink)
        if (::web.isInitialized) web.destroy()
        super.onDestroy()
    }
}
