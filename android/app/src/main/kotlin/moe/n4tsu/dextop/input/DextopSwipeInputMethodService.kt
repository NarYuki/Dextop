package moe.n4tsu.dextop.input

import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.inputmethod.EditorInfo

/**
 * A headless IME used only for committing a completed laptop swipe.
 *
 * Dextop switches to this service for the commit and immediately restores the
 * user's previous IME. Ordinary key taps never pass through this service and
 * therefore keep all Gboard/current-IME behaviour.
 */
class DextopSwipeInputMethodService : InputMethodService() {
    override fun onCreate() {
        super.onCreate()
        synchronized(lock) { activeInstance = this }
        flushPendingCommit(0)
    }

    override fun onDestroy() {
        synchronized(lock) {
            if (activeInstance === this) activeInstance = null
        }
        super.onDestroy()
    }

    override fun onEvaluateInputViewShown(): Boolean = false

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        flushPendingCommit(0)
    }

    override fun onBindInput() {
        super.onBindInput()
        flushPendingCommit(0)
    }

    private fun flushPendingCommit(attempt: Int) {
        val request = synchronized(lock) { pending } ?: return
        val connection = currentInputConnection
        if (connection == null) {
            if (attempt < MAX_CONNECTION_ATTEMPTS) {
                mainHandler.postDelayed({ flushPendingCommit(attempt + 1) }, 25L)
            } else {
                finish(request, false)
            }
            return
        }
        val boundDisplayId = display?.displayId ?: -1
        if (request.expectedDisplayId >= 0 && boundDisplayId != request.expectedDisplayId) {
            // Never type into display 0 when the swipe originated on Dextop's
            // virtual display. Some Samsung builds bind a newly selected IME
            // to the phone display first; the caller will use its display-local
            // accessibility fallback instead.
            finish(request, false)
            return
        }
        val committed = runCatching {
            val before = connection.getTextBeforeCursor(1, 0)
            val separator = if (
                request.ensureLeadingSpace &&
                !before.isNullOrEmpty() &&
                !before.last().isWhitespace()
            ) " " else ""
            val after = connection.getTextAfterCursor(1, 0)
            val trailing = if (
                request.ensureTrailingSpace &&
                (after.isNullOrEmpty() || !after.first().isWhitespace())
            ) " " else ""
            connection.commitText(separator + request.text + trailing, 1)
        }.getOrDefault(false)
        finish(request, committed)
    }

    private fun finish(request: CommitRequest, committed: Boolean) {
        val callback = synchronized(lock) {
            if (pending !== request) return
            pending = null
            request.callback
        }
        mainHandler.post { callback(committed) }
    }

    private data class CommitRequest(
        val text: String,
        val ensureLeadingSpace: Boolean,
        val ensureTrailingSpace: Boolean,
        val expectedDisplayId: Int,
        val callback: (Boolean) -> Unit,
    )

    companion object {
        private const val MAX_CONNECTION_ATTEMPTS = 24
        private val lock = Any()
        private val mainHandler = Handler(Looper.getMainLooper())
        private var pending: CommitRequest? = null
        private var activeInstance: DextopSwipeInputMethodService? = null

        fun prepareCommit(
            text: String,
            ensureLeadingSpace: Boolean = false,
            ensureTrailingSpace: Boolean = false,
            expectedDisplayId: Int = -1,
            callback: (Boolean) -> Unit,
        ) {
            val displaced = synchronized(lock) {
                val previous = pending
                pending = CommitRequest(
                    text,
                    ensureLeadingSpace,
                    ensureTrailingSpace,
                    expectedDisplayId,
                    callback,
                )
                previous
            }
            displaced?.let { mainHandler.post { it.callback(false) } }
            mainHandler.post {
                synchronized(lock) { activeInstance }?.flushPendingCommit(0)
            }
        }

        fun cancelPending() {
            val request = synchronized(lock) {
                val value = pending
                pending = null
                value
            }
            request?.let { mainHandler.post { it.callback(false) } }
        }
    }
}
