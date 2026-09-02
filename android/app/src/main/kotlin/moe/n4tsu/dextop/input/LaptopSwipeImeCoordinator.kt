package moe.n4tsu.dextop.input

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import java.util.concurrent.Executors
import moe.n4tsu.dextop.PrivilegedAccess

/** Owns the short-lived IME switch used only to commit a completed swipe. */
internal class LaptopSwipeImeCoordinator(
    private val context: Context,
    private val privilegedAccess: PrivilegedAccess,
) {
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "Dextop-swipe-ime").apply { isDaemon = true }
    }
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var generation = 0L
    private var previousIme: String? = null
    private val state = context.getSharedPreferences(STATE_PREFERENCES, Context.MODE_PRIVATE)

    fun commit(
        value: String,
        rememberAutoCommit: Boolean,
        expectedDisplayId: Int,
        appendTrailingSpace: Boolean,
        onCommitted: (String) -> Unit,
        fallback: () -> Unit,
    ) {
        val ownIme = ComponentName(
            context,
            DextopSwipeInputMethodService::class.java,
        ).flattenToShortString()
        val current = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD,
        ).orEmpty().takeIf(String::isNotBlank)
        val ownSelected = current == ownIme ||
            current?.endsWith("/.input.DextopSwipeInputMethodService") == true
        if (!ownSelected) rememberExternalIme(current)
        val restoreIme = if (!ownSelected) current else rememberedExternalIme()
        val revision = ++generation
        if (current == null || restoreIme == null || ownSelected) {
            if (restoreIme != null && current != restoreIme) {
                executor.execute {
                    restoreIme(restoreIme)
                    disableTransientIme()
                }
            } else if (!ownSelected) {
                executor.execute(::disableTransientIme)
            }
            fallback()
            return
        }
        previousIme = restoreIme
        val finish: (Boolean) -> Unit = { committed ->
            if (revision == generation) {
                if (committed && rememberAutoCommit) onCommitted(value)
                generation += 1
                DextopSwipeInputMethodService.cancelPending()
                previousIme = null
                executor.execute {
                    restoreIme(restoreIme)
                    disableTransientIme()
                    handler.post { if (!committed) fallback() }
                }
            }
        }
        DextopSwipeInputMethodService.prepareCommit(
            text = value,
            ensureLeadingSpace = true,
            ensureTrailingSpace = appendTrailingSpace,
            expectedDisplayId = expectedDisplayId,
            callback = finish,
        )
        executor.execute {
            val enabled = privilegedAccess.execute("ime", "enable", ownIme).succeeded
            val selected = enabled && privilegedAccess.execute("ime", "set", ownIme).succeeded
            if (!selected) handler.post { finish(false) }
            else handler.postDelayed({ finish(false) }, 900L)
        }
    }

    fun restore() {
        generation += 1
        DextopSwipeInputMethodService.cancelPending()
        val current = currentImeId()
        val ownSelected = isOwnIme(current)
        val previous = previousIme?.takeIf(String::isNotBlank)
            ?: if (ownSelected) rememberedExternalIme() else null
        previousIme = null
        // Restore only while Dextop still owns the global IME selection. If the
        // user changed to another external IME during the commit, that newer
        // explicit choice wins and must not be overwritten by cleanup.
        if (ownSelected && previous != null && current != previous) {
            executor.execute {
                restoreIme(previous)
                disableTransientIme()
            }
        } else if (!ownSelected) {
            rememberExternalIme(current)
            executor.execute(::disableTransientIme)
        }
    }

    /** Ensures ordinary taps and hardware-key events never keep Dextop selected. */
    fun ensureExternalImeSelected() {
        val current = currentImeId()
        if (!isOwnIme(current)) {
            rememberExternalIme(current)
            return
        }
        generation += 1
        DextopSwipeInputMethodService.cancelPending()
        val target = previousIme ?: rememberedExternalIme() ?: fallbackExternalImeId() ?: return
        previousIme = null
        executor.execute {
            restoreIme(target)
            disableTransientIme()
        }
    }

    fun close() {
        restore()
        handler.removeCallbacksAndMessages(null)
        // Let the queued restore run before the worker exits. Interrupting it
        // here can leave Dextop's transient IME selected after service death.
        executor.shutdown()
    }

    private fun installedGboardImeId(): String? =
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .inputMethodList
            .firstOrNull { it.packageName == GBOARD_PACKAGE }
            ?.id

    private fun fallbackExternalImeId(): String? {
        val manager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        return installedGboardImeId()
            ?: manager.enabledInputMethodList.firstOrNull { !isOwnIme(it.id) }?.id
    }

    private fun rememberExternalIme(id: String?) {
        if (id.isNullOrBlank() || isOwnIme(id)) return
        previousIme = id
        state.edit().putString(KEY_PREVIOUS_IME, id).apply()
    }

    private fun rememberedExternalIme(): String? {
        val remembered = previousIme
            ?: state.getString(KEY_PREVIOUS_IME, null)?.takeIf(String::isNotBlank)
        if (remembered == null || isOwnIme(remembered)) return null
        val manager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        return remembered.takeIf { id -> manager.inputMethodList.any { it.id == id } }
    }

    private fun restoreIme(id: String): Boolean {
        val restored = privilegedAccess.execute("ime", "set", id).succeeded
        if (restored) state.edit().remove(KEY_PREVIOUS_IME).apply()
        return restored
    }

    private fun currentImeId(): String? = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.DEFAULT_INPUT_METHOD,
    ).orEmpty().takeIf(String::isNotBlank)

    private fun isOwnIme(id: String?): Boolean =
        id?.endsWith("/.input.DextopSwipeInputMethodService") == true

    private fun disableTransientIme() {
        privilegedAccess.execute("ime", "disable", ownImeId())
    }

    private fun ownImeId(): String = ComponentName(
        context,
        DextopSwipeInputMethodService::class.java,
    ).flattenToShortString()

    private companion object {
        const val GBOARD_PACKAGE = "com.google.android.inputmethod.latin"
        const val STATE_PREFERENCES = "dextop_swipe_ime_state"
        const val KEY_PREVIOUS_IME = "previous_external_ime"
    }
}
