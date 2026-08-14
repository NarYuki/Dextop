package moe.n4tsu.dextop

import android.content.Context
import android.view.Display

internal data class PhoneRotationState(val frozen: Boolean, val rotation: Int)

/** Restores the default display's exact pre-session rotation policy. */
internal class PhoneRotationController(
    private val context: Context,
    private val privilegedAccess: PrivilegedAccess,
    private val sessionJournal: SessionJournal
) {
    private var state: PhoneRotationState? = null

    fun force(portrait: Boolean) {
        if (state == null) {
            state = readState().also {
                sessionJournal.rememberPhoneRotation(it.frozen, it.rotation)
            }
        }
        setFrozen(if (portrait) 0 else 1)
    }

    fun restore(clear: Boolean = false) {
        val original = state ?: sessionJournal.phoneRotationState() ?: return
        restoreState(original)
        if (clear) state = null
    }

    private fun readState(): PhoneRotationState {
        val type = Class.forName(WINDOW_MANAGER_INTERFACE)
        val service = privilegedAccess.service("window", WINDOW_MANAGER_INTERFACE)
        val frozen = type.methods.firstOrNull {
            it.name == "isDisplayRotationFrozen" && it.parameterTypes.size == 1
        }?.invoke(service, Display.DEFAULT_DISPLAY) as? Boolean
            ?: type.methods.firstOrNull {
                it.name == "isRotationFrozen" && it.parameterTypes.isEmpty()
            }?.invoke(service) as? Boolean
            ?: false
        val rotation = context.getSystemService(android.hardware.display.DisplayManager::class.java)
            .getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: 0
        return PhoneRotationState(frozen, rotation)
    }

    private fun restoreState(original: PhoneRotationState) {
        if (original.frozen) setFrozen(original.rotation) else thaw()
        OperationLog.i(
            context,
            "PhoneRotation",
            "restored frozen=${original.frozen} rotation=${original.rotation}"
        )
    }

    private fun setFrozen(rotation: Int) {
        val type = Class.forName(WINDOW_MANAGER_INTERFACE)
        val service = privilegedAccess.service("window", WINDOW_MANAGER_INTERFACE)
        val method = type.methods.first {
            it.name == "freezeDisplayRotation" && it.parameterTypes.size >= 2
        }
        val args: Array<Any?> = method.parameterTypes.mapIndexed { index, parameter ->
            when {
                index == 0 -> Display.DEFAULT_DISPLAY
                index == 1 -> rotation
                parameter == String::class.java -> context.packageName
                parameter == Boolean::class.javaPrimitiveType -> true
                else -> null
            }
        }.toTypedArray()
        method.invoke(service, *args)
    }

    private fun thaw() {
        val type = Class.forName(WINDOW_MANAGER_INTERFACE)
        val service = privilegedAccess.service("window", WINDOW_MANAGER_INTERFACE)
        val method = type.methods.firstOrNull {
            it.name == "thawDisplayRotation" && it.parameterTypes.isNotEmpty()
        } ?: type.methods.firstOrNull {
            it.name == "thawRotation"
        } ?: error(NativeStrings.text("nativeRotationReleaseUnsupported"))
        val args: Array<Any?> = method.parameterTypes.mapIndexed { index, parameter ->
            when {
                index == 0 && method.name == "thawDisplayRotation" -> Display.DEFAULT_DISPLAY
                parameter == String::class.java -> context.packageName
                else -> null
            }
        }.toTypedArray()
        method.invoke(service, *args)
    }

    companion object {
        private const val WINDOW_MANAGER_INTERFACE = "android.view.IWindowManager"

        fun restoreRecorded(
            context: Context,
            privilegedAccess: PrivilegedAccess,
            sessionJournal: SessionJournal
        ) {
            PhoneRotationController(context, privilegedAccess, sessionJournal).restore(clear = true)
        }
    }
}
