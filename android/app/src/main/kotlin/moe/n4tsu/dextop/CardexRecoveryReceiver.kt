package moe.n4tsu.dextop

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CardexRecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_MARK_INTERRUPTED) return
        markInterrupted(context, intent.getStringExtra(EXTRA_REASON).orEmpty())
    }

    companion object {
        const val ACTION_MARK_INTERRUPTED = "moe.n4tsu.dextop.action.CARDEX_INTERRUPTED"
        const val EXTRA_REASON = "reason"
        const val PREFERENCES = "dextop_cleanup_state"
        const val KEY_REPAIR_REQUIRED = "cardex_repair_required"

        fun markInterrupted(context: Context, reason: String) {
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_REPAIR_REQUIRED, true)
                .putBoolean("cleanup_pending", true)
                .putString("cardex_interruption_reason", reason)
                .putLong("cardex_interrupted_at", System.currentTimeMillis())
                .commit()
            OperationLog.w(
                context,
                "CarCompanion",
                "unexpected disconnect; Android repair required reason=${reason.ifBlank { "unknown" }}",
                null
            )
        }
    }
}
