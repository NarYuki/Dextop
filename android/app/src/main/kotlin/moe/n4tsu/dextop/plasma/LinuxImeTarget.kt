package moe.n4tsu.dextop.plasma

import android.content.Context
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection

/**
 * Invisible editor that receives the Android IME for a Linux window. Text
 * the IME commits goes to the container as typed characters; Backspace,
 * Enter and other keys go as evdev key presses.
 */
internal class LinuxImeTarget(
    context: Context,
    private val onText: (String) -> Unit,
    private val onKey: (KeyEvent) -> Unit,
) : View(context) {
    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun onCheckIsTextEditor() = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN or
            EditorInfo.IME_ACTION_NONE
        return object : BaseInputConnection(this, false) {
            override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
                if (text.isNotEmpty()) onText(text.toString())
                return true
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                repeat(beforeLength.coerceAtMost(64)) { tap(KeyEvent.KEYCODE_DEL) }
                repeat(afterLength.coerceAtMost(64)) { tap(KeyEvent.KEYCODE_FORWARD_DEL) }
                return true
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                onKey(event)
                return true
            }

            override fun performEditorAction(actionCode: Int): Boolean {
                tap(KeyEvent.KEYCODE_ENTER)
                return true
            }
        }
    }

    private fun tap(keyCode: Int) {
        onKey(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        onKey(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }
}
