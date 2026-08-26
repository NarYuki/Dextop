package moe.n4tsu.dextop.privilege

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Keeps Dextop's own wireless-pairing search alive while the user moves to
 * Android's Wireless debugging screen. The service owns lifecycle only; the
 * pairing protocol remains in DistributionPrivilegeBootstrap.
 */
class EmbeddedPairingService : Service() {
    companion object {
        private const val ACTION_START = "moe.n4tsu.dextop.action.START_EMBEDDED_PAIRING"

        fun start(context: Context) {
            val intent = Intent(context, EmbeddedPairingService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        }

        fun finish(context: Context) {
            context.stopService(Intent(context, EmbeddedPairingService::class.java))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Discovery can finish before Android invokes onStartCommand.  Preserve
        // the code-entry action in that case; calling searching() here would
        // overwrite it because both states share the foreground notification ID.
        val notification = if (EmbeddedPairingNotification.state == "waiting_for_code") {
            EmbeddedPairingNotification.codeInput(this)
        } else {
            EmbeddedPairingNotification.searching(this)
        }
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                EmbeddedPairingNotification.notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(EmbeddedPairingNotification.notificationId, notification)
        }
        Log.i("DextopEmbeddedPair", "foreground pairing service started")
        DistributionPrivilegeBootstrap.beginPairingDiscovery(applicationContext)
        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        DistributionPrivilegeBootstrap.cancelPairingDiscovery()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
