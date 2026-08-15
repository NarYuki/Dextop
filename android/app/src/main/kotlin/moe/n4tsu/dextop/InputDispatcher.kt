package moe.n4tsu.dextop

import android.view.InputEvent

internal class InputDispatcher(
    private val privilegedAccess: PrivilegedAccess,
    private val observer: ((InputEvent, Int, Boolean, Throwable?) -> Unit)? = null
) {
    private val service by lazy {
        privilegedAccess.service("input", "android.hardware.input.IInputManager")
    }
    private val injectMethod by lazy {
        Class.forName("android.hardware.input.IInputManager")
            .getMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType)
    }
    private val setDisplayMethod by lazy {
        InputEvent::class.java.getMethod("setDisplayId", Int::class.javaPrimitiveType)
    }

    fun send(event: InputEvent, displayId: Int): Boolean {
        var accepted = false
        var failure: Throwable? = null
        try {
            setDisplayMethod.invoke(event, displayId)
            // WAIT_FOR_RESULT makes the returned value useful for diagnostics:
            // Samsung's InputManager can reject an event for a display even
            // when the binder call itself succeeds.
            accepted = injectMethod.invoke(service, event, INJECT_WAIT_FOR_RESULT) as Boolean
        } catch (error: Throwable) {
            failure = error
        }
        runCatching { observer?.invoke(event, displayId, accepted, failure) }
        return accepted
    }

    private companion object {
        const val INJECT_WAIT_FOR_RESULT = 1
    }
}
