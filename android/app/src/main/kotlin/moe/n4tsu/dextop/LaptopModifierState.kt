package moe.n4tsu.dextop

import android.view.KeyEvent
import moe.n4tsu.dextop.input.LaptopSwipeDecoder

/**
 * Latch, lock and chord state of the on-screen keyboard's modifiers.
 *
 * A modifier tapped alone latches for the next key; one held while another key
 * goes down is a chord and stays active until its own finger lifts. Confined to
 * the main thread, like the keyboard views that drive it.
 */
internal class LaptopModifierState {
    var shift = false
        private set
    var shiftLocked = false
        private set
    var control = false
        private set
    var alt = false
        private set
    var capsLock = false
        private set
    var symbolMode = false
        private set

    private var lastShiftTapAt = 0L
    private val held = HashSet<Int>()
    private val consumed = HashSet<Int>()
    private val activeBeforePress = HashMap<Int, Boolean>()

    val shiftActive: Boolean get() = shift || shiftLocked
    val uppercaseAll: Boolean get() = shiftLocked || capsLock

    /** Everything the key caps draw from; equal bits mean nothing to redraw. */
    val visualBits: Int
        get() = (if (shift) 1 else 0) or (if (shiftLocked) 2 else 0) or (if (control) 4 else 0) or
            (if (alt) 8 else 0) or (if (capsLock) 16 else 0) or (if (symbolMode) 32 else 0)

    fun reset() {
        shift = false
        shiftLocked = false
        control = false
        alt = false
        capsLock = false
        symbolMode = false
        held.clear()
        consumed.clear()
        activeBeforePress.clear()
    }

    fun isHeld(keyCode: Int): Boolean = keyCode in held

    fun isActive(keyCode: Int): Boolean = when (keyCode) {
        LaptopKeyboardLayout.SHIFT -> shiftActive
        LaptopKeyboardLayout.CONTROL -> control
        LaptopKeyboardLayout.ALT -> alt
        LaptopKeyboardLayout.CAPS -> capsLock
        LaptopKeyboardLayout.SYM -> symbolMode
        else -> false
    }

    private fun setActive(keyCode: Int, active: Boolean) {
        when (keyCode) {
            LaptopKeyboardLayout.SHIFT -> shift = active
            LaptopKeyboardLayout.CONTROL -> control = active
            LaptopKeyboardLayout.ALT -> alt = active
            LaptopKeyboardLayout.SYM -> symbolMode = active
        }
    }

    /** Flips a modifier tapped as an ordinary key. False when [keyCode] is not a modifier. */
    fun toggle(keyCode: Int): Boolean {
        when (keyCode) {
            LaptopKeyboardLayout.SHIFT -> shift = !shift
            LaptopKeyboardLayout.CONTROL -> control = !control
            LaptopKeyboardLayout.ALT -> alt = !alt
            LaptopKeyboardLayout.CAPS -> capsLock = !capsLock
            LaptopKeyboardLayout.SYM -> symbolMode = !symbolMode
            else -> return false
        }
        return true
    }

    /** A finger went down on a modifier. False when that modifier is already held. */
    fun press(keyCode: Int): Boolean {
        if (!held.add(keyCode)) return false
        activeBeforePress[keyCode] = if (keyCode == LaptopKeyboardLayout.CAPS) false else isActive(keyCode)
        consumed.remove(keyCode)
        setActive(keyCode, true)
        return true
    }

    /** The finger lifted. False when the modifier was not held. */
    fun release(keyCode: Int, now: Long, doubleTapTimeout: Long): Boolean {
        if (!held.remove(keyCode)) return false
        val usedAsChord = consumed.remove(keyCode)
        val wasActive = activeBeforePress.remove(keyCode) ?: false
        // A standalone tap toggles the latch. When used with another key, the
        // modifier remains active until this finger is actually released.
        if (keyCode == LaptopKeyboardLayout.SHIFT && !usedAsChord) {
            if (shiftLocked) {
                shiftLocked = false
                shift = false
                lastShiftTapAt = 0L
            } else if (now - lastShiftTapAt <= doubleTapTimeout) {
                // Lock Shift itself, independently of Caps Lock and swipe.
                shiftLocked = true
                shift = false
                lastShiftTapAt = 0L
            } else {
                shift = !wasActive
                lastShiftTapAt = now
            }
        } else {
            setActive(keyCode, if (usedAsChord) false else !wasActive)
        }
        return true
    }

    /** Meta state a key press of [keyCode] carries. Caps Lock inverts Shift for letters only. */
    fun metaState(keyCode: Int): Int {
        val isLetter = keyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z
        val shifted = if (isLetter) shiftActive.xor(capsLock) else shiftActive
        var metaState = 0
        if (shifted) metaState = metaState or KeyEvent.META_SHIFT_ON
        if (control) metaState = metaState or KeyEvent.META_CTRL_ON
        if (alt) metaState = metaState or KeyEvent.META_ALT_ON
        return metaState
    }

    /**
     * A key went down: held modifiers become chords and stay, latched ones are
     * spent. The symbol latch is spent only by a key that [usedSymbol].
     */
    fun consumeForKeyPress(usedSymbol: Boolean) {
        consumed.addAll(held)
        if (LaptopKeyboardLayout.SHIFT !in held) shift = false
        if (LaptopKeyboardLayout.CONTROL !in held) control = false
        if (LaptopKeyboardLayout.ALT !in held) alt = false
        if (usedSymbol && LaptopKeyboardLayout.SYM !in held) symbolMode = false
    }

    /** Drops the one-shot latches after a key that was tapped, not held. */
    fun clearLatched() {
        shift = false
        control = false
        alt = false
    }

    /** A tapped Shift is one-shot; a Shift finger still down stays active. */
    fun releaseLatchedShift() {
        if (LaptopKeyboardLayout.SHIFT !in held) shift = false
    }

    companion object {
        /** Modifiers that latch on finger down and resolve on finger up. */
        fun latchesOnPress(keyCode: Int): Boolean =
            keyCode == LaptopKeyboardLayout.SHIFT || keyCode == LaptopKeyboardLayout.CONTROL ||
                keyCode == LaptopKeyboardLayout.ALT || keyCode == LaptopKeyboardLayout.SYM

        /** Keys whose cap shows whether they are active. */
        fun showsActiveState(keyCode: Int): Boolean =
            latchesOnPress(keyCode) || keyCode == LaptopKeyboardLayout.CAPS
    }
}

/** What the on-screen keyboard's keys print and send; no state of its own. */
internal object LaptopKeyLegends {
    private val QWERTY_CODES = intArrayOf(
        KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_R,
        KeyEvent.KEYCODE_T, KeyEvent.KEYCODE_Y, KeyEvent.KEYCODE_U, KeyEvent.KEYCODE_I,
        KeyEvent.KEYCODE_O, KeyEvent.KEYCODE_P, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_S,
        KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_F, KeyEvent.KEYCODE_G, KeyEvent.KEYCODE_H,
        KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_L, KeyEvent.KEYCODE_Z,
        KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_V, KeyEvent.KEYCODE_B,
        KeyEvent.KEYCODE_N, KeyEvent.KEYCODE_M,
    )

    /** Letter the swipe decoder reads at a QWERTY position in [language]. */
    fun swipeSymbol(keyCode: Int, language: LaptopSwipeDecoder.Language): Char? {
        val index = QWERTY_CODES.indexOf(keyCode)
        if (index < 0) return null
        val symbols = when (language) {
            LaptopSwipeDecoder.Language.RU -> "йцукенгшщзфывапролячсмитьб"
            LaptopSwipeDecoder.Language.UK -> "йцукенгшщзфівапролячсмитьб"
            LaptopSwipeDecoder.Language.KO -> "ㅂㅈㄷㄱㅅㅛㅕㅑㅐㅔㅁㄴㅇㄹㅎㅗㅓㅏㅣㅋㅌㅊㅍㅠㅜㅡ"
            else -> "qwertyuiopasdfghjklzxcvbnm"
        }
        return symbols.getOrNull(index)
    }

    /** Legend a key cap shows for its unshifted [base] under [state]. */
    fun displayed(base: String, state: LaptopModifierState): String = when {
        state.uppercaseAll && base.length == 1 && base[0].isLetter() -> base.uppercase(java.util.Locale.ENGLISH)
        state.shiftActive -> shifted(base)
        else -> base
    }

    fun shifted(base: String): String = when (base) {
        "`" -> "~"
        "1" -> "!"
        "2" -> "@"
        "3" -> "#"
        "4" -> "\$"
        "5" -> "%"
        "6" -> "^"
        "7" -> "&"
        "8" -> "*"
        "9" -> "("
        "0" -> ")"
        "-" -> "_"
        "=" -> "+"
        "[" -> "{"
        "]" -> "}"
        "\\" -> "|"
        ";" -> ":"
        "'" -> "\""
        "," -> "<"
        "." -> ">"
        "/" -> "?"
        else -> base
    }

    /** Key printed above each BlackBerry letter: key code plus Shift state. */
    fun blackBerrySymbolKey(keyCode: Int): Pair<Int, Boolean>? = when (keyCode) {
        KeyEvent.KEYCODE_Q -> KeyEvent.KEYCODE_3 to true       // #
        KeyEvent.KEYCODE_W -> KeyEvent.KEYCODE_1 to false
        KeyEvent.KEYCODE_E -> KeyEvent.KEYCODE_2 to false
        KeyEvent.KEYCODE_R -> KeyEvent.KEYCODE_3 to false
        KeyEvent.KEYCODE_T -> KeyEvent.KEYCODE_9 to true       // (
        KeyEvent.KEYCODE_Y -> KeyEvent.KEYCODE_0 to true       // )
        KeyEvent.KEYCODE_U -> KeyEvent.KEYCODE_MINUS to true   // _
        KeyEvent.KEYCODE_I -> KeyEvent.KEYCODE_MINUS to false
        KeyEvent.KEYCODE_O -> KeyEvent.KEYCODE_EQUALS to true  // +
        KeyEvent.KEYCODE_P -> KeyEvent.KEYCODE_2 to true       // @
        KeyEvent.KEYCODE_A -> KeyEvent.KEYCODE_8 to true       // *
        KeyEvent.KEYCODE_S -> KeyEvent.KEYCODE_4 to false
        KeyEvent.KEYCODE_D -> KeyEvent.KEYCODE_5 to false
        KeyEvent.KEYCODE_F -> KeyEvent.KEYCODE_6 to false
        KeyEvent.KEYCODE_G -> KeyEvent.KEYCODE_SLASH to false
        KeyEvent.KEYCODE_H -> KeyEvent.KEYCODE_SEMICOLON to true
        KeyEvent.KEYCODE_J -> KeyEvent.KEYCODE_SEMICOLON to false
        KeyEvent.KEYCODE_K -> KeyEvent.KEYCODE_APOSTROPHE to false
        KeyEvent.KEYCODE_L -> KeyEvent.KEYCODE_APOSTROPHE to true
        KeyEvent.KEYCODE_Z -> KeyEvent.KEYCODE_7 to false
        KeyEvent.KEYCODE_X -> KeyEvent.KEYCODE_8 to false
        KeyEvent.KEYCODE_C -> KeyEvent.KEYCODE_9 to false
        KeyEvent.KEYCODE_V -> KeyEvent.KEYCODE_SLASH to true    // ?
        KeyEvent.KEYCODE_B -> KeyEvent.KEYCODE_1 to true        // !
        KeyEvent.KEYCODE_N -> KeyEvent.KEYCODE_COMMA to false
        KeyEvent.KEYCODE_M -> KeyEvent.KEYCODE_PERIOD to false
        else -> null
    }
}
