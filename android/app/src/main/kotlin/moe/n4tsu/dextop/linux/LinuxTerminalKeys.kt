package moe.n4tsu.dextop.linux

import android.view.KeyCharacterMap
import android.view.KeyEvent

/**
 * Android key events to the bytes an xterm-compatible terminal sends, so keys never pass
 * through the WebView's text field (where IME composition doubles characters).
 */
internal object LinuxTerminalKeys {
    /** What one key sends: [normal], or [application] while the cursor keys are in application mode. */
    data class Keys(val normal: String, val application: String? = null)

    private const val ESC = "\u001b"

    fun encode(event: KeyEvent): Keys? {
        if (event.action != KeyEvent.ACTION_DOWN) return null
        val shift = event.isShiftPressed
        val alt = event.isAltPressed
        val ctrl = event.isCtrlPressed
        // xterm's modifier parameter: 1 + shift + 2·alt + 4·ctrl.
        val modifier = 1 + (if (shift) 1 else 0) + (if (alt) 2 else 0) + (if (ctrl) 4 else 0)

        fun cursor(final: Char): Keys =
            if (modifier > 1) Keys("$ESC[1;$modifier$final") else Keys("$ESC[$final", "${ESC}O$final")
        fun tilde(number: Int) = Keys(if (modifier > 1) "$ESC[$number;$modifier~" else "$ESC[$number~")
        fun function(final: Char) = Keys(if (modifier > 1) "$ESC[1;$modifier$final" else "${ESC}O$final")
        fun withAlt(text: String) = Keys(if (alt) ESC + text else text)

        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> return cursor('A')
            KeyEvent.KEYCODE_DPAD_DOWN -> return cursor('B')
            KeyEvent.KEYCODE_DPAD_RIGHT -> return cursor('C')
            KeyEvent.KEYCODE_DPAD_LEFT -> return cursor('D')
            KeyEvent.KEYCODE_MOVE_HOME -> return cursor('H')
            KeyEvent.KEYCODE_MOVE_END -> return cursor('F')
            KeyEvent.KEYCODE_PAGE_UP -> return tilde(5)
            KeyEvent.KEYCODE_PAGE_DOWN -> return tilde(6)
            KeyEvent.KEYCODE_INSERT -> return tilde(2)
            KeyEvent.KEYCODE_FORWARD_DEL -> return tilde(3)
            KeyEvent.KEYCODE_F1 -> return function('P')
            KeyEvent.KEYCODE_F2 -> return function('Q')
            KeyEvent.KEYCODE_F3 -> return function('R')
            KeyEvent.KEYCODE_F4 -> return function('S')
            KeyEvent.KEYCODE_F5 -> return tilde(15)
            KeyEvent.KEYCODE_F6 -> return tilde(17)
            KeyEvent.KEYCODE_F7 -> return tilde(18)
            KeyEvent.KEYCODE_F8 -> return tilde(19)
            KeyEvent.KEYCODE_F9 -> return tilde(20)
            KeyEvent.KEYCODE_F10 -> return tilde(21)
            KeyEvent.KEYCODE_F11 -> return tilde(23)
            KeyEvent.KEYCODE_F12 -> return tilde(24)
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> return withAlt("\r")
            KeyEvent.KEYCODE_TAB -> return if (shift) Keys("$ESC[Z") else withAlt("\t")
            KeyEvent.KEYCODE_ESCAPE -> return Keys(ESC)
            KeyEvent.KEYCODE_DEL -> return withAlt(if (ctrl) "\b" else "\u007f")
            KeyEvent.KEYCODE_SPACE -> if (ctrl) return withAlt("\u0000")
        }

        // Printable keys: the character the layout gives with Shift (and Caps Lock), then
        // Ctrl and Alt applied as a terminal does.
        val unicode = event.getUnicodeChar(event.metaState and
            (KeyEvent.META_CTRL_MASK or KeyEvent.META_ALT_MASK or KeyEvent.META_META_MASK).inv())
        if (unicode == 0 || unicode and KeyCharacterMap.COMBINING_ACCENT != 0) return null
        if (event.isMetaPressed) return null
        var text = String(Character.toChars(unicode))
        if (ctrl) text = control(unicode) ?: return null
        return withAlt(text)
    }

    /** Ctrl + [char], as terminals send it; null for keys Ctrl does not change. */
    private fun control(char: Int): String? {
        val code = when (val c = char.toChar()) {
            in 'a'..'z' -> c - 'a' + 1
            in 'A'..'Z' -> c - 'A' + 1
            '@', ' ', '2' -> 0
            '[', '3' -> 27
            '\\', '4' -> 28
            ']', '5' -> 29
            '^', '6' -> 30
            '_', '-', '7', '/' -> 31
            '?', '8' -> 127
            else -> return null
        }
        return code.toChar().toString()
    }
}
