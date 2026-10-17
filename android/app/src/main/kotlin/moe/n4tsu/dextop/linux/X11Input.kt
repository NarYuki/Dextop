package moe.n4tsu.dextop.linux

import android.os.SystemClock
import android.util.Log

/** Pointer, wheel, key and IME text input through XTEST, shared by desktop and app sessions. */
internal class X11Input(private val x: X11Client) {
    companion object {
        private const val TAG = "DextopX11Input"
        private const val XK_UNICODE = 0x01000000
    }

    private val spareKeycodes = ArrayDeque<Int>()

    init {
        runCatching {
            val count = x.maxKeycode - x.minKeycode + 1
            val (perKeycode, keysyms) = x.keyboardMapping(x.minKeycode, count)
            for (index in count - 1 downTo 0) {
                val unused = (0 until perKeycode).all { keysyms[index * perKeycode + it] == 0 }
                if (unused) spareKeycodes.addLast(x.minKeycode + index)
                if (spareKeycodes.size == 8) break
            }
        }.onFailure { Log.w(TAG, "keyboard mapping unavailable; IME text input disabled", it) }
        Log.i(TAG, "spare keycodes for IME text: $spareKeycodes")
    }

    /** Moves to root coordinates ([rootX], [rootY]); [action] 1 presses and 2 releases [button]. */
    fun pointer(action: Int, rootX: Int, rootY: Int, button: Int) {
        x.fakeInput(X11Client.MOTION_NOTIFY, 0, rootX, rootY)
        when (action) {
            1 -> x.fakeInput(X11Client.BUTTON_PRESS, button)
            2 -> x.fakeInput(X11Client.BUTTON_RELEASE, button)
        }
    }

    fun scroll(dx: Int, dy: Int) {
        fun click(button: Int, times: Int) = repeat(times) {
            x.fakeInput(X11Client.BUTTON_PRESS, button)
            x.fakeInput(X11Client.BUTTON_RELEASE, button)
        }
        if (dy > 0) click(5, dy) else if (dy < 0) click(4, -dy)
        if (dx > 0) click(7, dx) else if (dx < 0) click(6, -dx)
    }

    /** X keycodes are evdev codes + 8 with Xvfb's default (evdev) keymap. */
    fun key(evdevCode: Int, down: Boolean) {
        val keycode = evdevCode + 8
        if (keycode !in x.minKeycode..x.maxKeycode) return
        x.fakeInput(if (down) X11Client.KEY_PRESS else X11Client.KEY_RELEASE, keycode)
    }

    /**
     * Types text the IME committed: each character's keysym is bound to a
     * spare keycode, which is then pressed (the technique xdotool uses).
     */
    @Synchronized
    fun text(text: String) {
        if (spareKeycodes.isEmpty()) return
        text.codePoints().forEach { codePoint ->
            when (codePoint) {
                '\n'.code -> { key(28, true); key(28, false); return@forEach }
                '\t'.code -> { key(15, true); key(15, false); return@forEach }
            }
            val keysym = if (codePoint in 0x20..0x7e || codePoint in 0xa0..0xff) codePoint else XK_UNICODE or codePoint
            val keycode = spareKeycodes.removeFirst().also(spareKeycodes::addLast)
            x.changeKeyboardMapping(keycode, intArrayOf(keysym, keysym))
            // Clients learn the new mapping from MappingNotify; let it land before the press.
            x.sync()
            SystemClock.sleep(8)
            x.fakeInput(X11Client.KEY_PRESS, keycode)
            x.fakeInput(X11Client.KEY_RELEASE, keycode)
        }
        x.sync()
    }
}
