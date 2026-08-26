package moe.n4tsu.dextop.privilege

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import kotlinx.coroutines.runBlocking

/** Notification state for Dextop's self-contained wireless pairing flow. */
internal object EmbeddedPairingNotification {
    private const val CHANNEL_ID = "dextop_embedded_pairing"
    const val notificationId = 0xD37
    const val ACTION_PAIR = "moe.n4tsu.dextop.action.PAIR_EMBEDDED_BINDER"
    const val ACTION_RETRY = "moe.n4tsu.dextop.action.RETRY_EMBEDDED_BINDER_PAIRING"
    const val REMOTE_INPUT_CODE = "pairing_code"

    @Volatile
    var state: String = "idle"
        private set

    private fun manager(context: Context): NotificationManager =
        context.getSystemService(NotificationManager::class.java)

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        manager(context).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(moe.n4tsu.dextop.R.string.embedded_pairing_channel),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
    }

    private fun base(context: Context): Notification.Builder =
        Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(moe.n4tsu.dextop.R.drawable.ic_qs_dextop)
            .setOngoing(true)
            .setAutoCancel(false)

    fun showSearching(context: Context) {
        state = "searching"
        ensureChannel(context)
        manager(context).notify(notificationId, searching(context))
    }

    fun searching(context: Context): Notification {
        state = "searching"
        ensureChannel(context)
        return base(context)
            .setContentTitle(context.getString(moe.n4tsu.dextop.R.string.embedded_pairing_searching))
            .setContentText(context.getString(moe.n4tsu.dextop.R.string.embedded_pairing_notification_text))
            .build()
    }

    fun showCodeInput(context: Context) {
        state = "waiting_for_code"
        ensureChannel(context)
        manager(context).notify(notificationId, codeInput(context))
    }

    /**
     * Builds the actionable code notification without changing state.  The
     * foreground service must use this after discovery so it does not replace
     * the RemoteInput action with its bootstrap "searching" notification.
     */
    fun codeInput(context: Context): Notification {
        ensureChannel(context)
        val input = RemoteInput.Builder(REMOTE_INPUT_CODE)
            .setLabel(context.getString(moe.n4tsu.dextop.R.string.embedded_pairing_code))
            .build()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, EmbeddedPairingReceiver::class.java).setAction(ACTION_PAIR),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val action = Notification.Action.Builder(
            null,
            context.getString(moe.n4tsu.dextop.R.string.embedded_pairing_enter_code),
            pendingIntent,
        ).addRemoteInput(input).build()
        return base(context)
            .setContentTitle(context.getString(moe.n4tsu.dextop.R.string.embedded_pairing_service_found))
            .setContentText(context.getString(moe.n4tsu.dextop.R.string.embedded_pairing_enter_code))
            .addAction(action)
            .build()
    }

    fun showUnavailable(context: Context) {
        state = "service_not_found"
        ensureChannel(context)
        val retry = PendingIntent.getBroadcast(
            context,
            1,
            Intent(context, EmbeddedPairingReceiver::class.java).setAction(ACTION_RETRY),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        manager(context).notify(
            notificationId,
            base(context)
                .setContentTitle(context.getString(moe.n4tsu.dextop.R.string.embedded_pairing_service_not_found))
                .setContentText(context.getString(moe.n4tsu.dextop.R.string.embedded_pairing_notification_text))
                .addAction(
                    Notification.Action.Builder(
                        null,
                        context.getString(moe.n4tsu.dextop.R.string.embedded_pairing_retry),
                        retry,
                    ).build(),
                )
                .build(),
        )
    }

    fun pairing(context: Context) {
        state = "pairing"
        ensureChannel(context)
        manager(context).notify(
            notificationId,
            base(context)
                .setContentTitle(context.getString(moe.n4tsu.dextop.R.string.embedded_pairing_in_progress))
                .build(),
        )
    }

    fun dismiss(context: Context) {
        state = "idle"
        manager(context).cancel(notificationId)
    }

    fun finish(context: Context, success: Boolean, detail: String?) {
        state = if (success) "connected" else "failed"
        ensureChannel(context)
        manager(context).notify(
            notificationId,
            base(context)
                .setContentTitle(
                    context.getString(
                        if (success) moe.n4tsu.dextop.R.string.embedded_pairing_success
                        else moe.n4tsu.dextop.R.string.embedded_pairing_failed,
                    ),
                )
                .setContentText(
                    if (success) {
                        context.getString(moe.n4tsu.dextop.R.string.embedded_pairing_success_message)
                    } else {
                        detail
                    },
                )
                .setOngoing(false)
                .setAutoCancel(true)
                .build(),
        )
    }
}

class EmbeddedPairingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("DextopEmbeddedPair", "pairing notification action=${intent.action}")
        when (intent.action) {
            EmbeddedPairingNotification.ACTION_RETRY -> {
                DistributionPrivilegeBootstrap.showPairingNotification(context)
                return
            }
            EmbeddedPairingNotification.ACTION_PAIR -> Unit
            else -> return
        }
        val code = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(EmbeddedPairingNotification.REMOTE_INPUT_CODE)
            ?.toString()
            ?.trim()
            .orEmpty()
        if (!code.matches(Regex("[0-9]{6}"))) {
            EmbeddedPairingNotification.finish(
                context,
                false,
                context.getString(moe.n4tsu.dextop.R.string.embedded_pairing_invalid_code),
            )
            DistributionPrivilegeBootstrap.showPairingNotification(context)
            return
        }
        EmbeddedPairingNotification.pairing(context)
        val pending = goAsync()
        Thread {
            val outcome = runCatching {
                runBlocking {
                    val pairing = DistributionPrivilegeBootstrap.pair(context, code)
                    check(pairing.paired) { pairing.detail ?: "Pairing failed" }
                    val start = DistributionPrivilegeBootstrap.connectAndStart(context)
                    check(start.started) { start.detail ?: "The embedded service did not start" }
                }
            }
            Log.i(
                "DextopEmbeddedPair",
                "pairing flow completed success=${outcome.isSuccess} " +
                    "error=${outcome.exceptionOrNull()?.javaClass?.simpleName ?: "none"}",
            )
            EmbeddedPairingNotification.finish(context, outcome.isSuccess, outcome.exceptionOrNull()?.message)
            EmbeddedPairingService.finish(context)
            context.sendBroadcast(Intent("moe.n4tsu.dextop.action.EMBEDDED_BINDER_STATUS").setPackage(context.packageName))
            pending.finish()
        }.start()
    }
}
