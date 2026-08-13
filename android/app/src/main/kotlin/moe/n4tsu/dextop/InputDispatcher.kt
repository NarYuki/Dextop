package moe.n4tsu.dextop

import android.view.InputEvent

internal class InputDispatcher(private val privilegedAccess: PrivilegedAccess) {
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

    fun send(event: InputEvent, displayId: Int): Boolean = runCatching {
        setDisplayMethod.invoke(event, displayId)
        // ASYNC keeps MOVE latency low. The binder result still reports whether
        // the event was accepted; never turn a rejected gesture into success.
        injectMethod.invoke(service, event, INJECT_WAIT_FOR_RESULT) as Boolean
    }.getOrDefault(false)

    private companion object {
        const val INJECT_WAIT_FOR_RESULT = 1
    }
}
