package moe.n4tsu.dextop

import android.content.Context
import android.util.Log
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText

/** What [OverlayTextInput] needs from the session beyond the shared interfaces. */
internal interface OverlayTextInputHost : DesktopSession, OverlayMenu {
    val rootWindowParams: WindowManager.LayoutParams?
    fun systemService(name: String, interfaceName: String): Any
}

/** Lets text fields inside the overlay take focus and the keyboard. */
internal class OverlayTextInput(
    private val context: Context,
    private val desk: OverlayTextInputHost,
) {
    internal var overlayTextInputActive = false

    internal fun setOverlayFocusable(focusable: Boolean) {
        val frame = desk.root ?: return
        val params = desk.rootWindowParams ?: return
        val wanted = if (focusable) {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        if (wanted == params.flags) return
        params.flags = wanted
        runCatching { desk.windowManager?.updateViewLayout(frame, params) }
            .onFailure { Log.e(desk.logTag, "overlay focus update failed", it) }
    }

    private fun setDextopImeLocal(local: Boolean) {
        if (desk.targetDisplayId < 0) return
        runCatching {
            val service = desk.systemService("window", "android.view.IWindowManager")
            Class.forName("android.view.IWindowManager")
                .getMethod("setDisplayImePolicy", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                .invoke(service, desk.targetDisplayId, if (local) 0 else 1)
        }.onFailure { Log.e(desk.logTag, "IME policy update failed", it) }
    }

    internal fun beginOverlayTextInput(field: EditText) {
        overlayTextInputActive = true
        desk.surfaceView?.releasePointerCapture()
        setDextopImeLocal(false)
        setOverlayFocusable(true)
        field.postDelayed({
            field.requestFocus()
            field.setSelection(field.text?.length ?: 0)
            val input = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            input.restartInput(field)
            input.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
        }, 160)
    }

    internal fun endOverlayTextInput() {
        if (!overlayTextInputActive) return
        overlayTextInputActive = false
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(desk.root?.windowToken, 0)
        setDextopImeLocal(true)
        setOverlayFocusable(false)
    }
}
