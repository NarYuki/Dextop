package moe.n4tsu.dextop.linux

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log

/**
 * Android kills an app's child processes beyond 32 and CPU-heavy ones in the background (the
 * phantom process monitor). Linux in the Android execution mode is exactly such a set of child
 * processes; a desktop with a browser passes 32 at once. As DroidDeck does, the monitor is turned
 * off through its global setting ("Disable child process restrictions" in developer options),
 * which persists. Dextop sets it itself when it holds WRITE_SECURE_SETTINGS (granted for
 * Wireless debugging); otherwise the user is shown where to turn it off.
 */
internal object LinuxPhantomProcesses {
    private const val TAG = "DextopLinux"
    private const val SETTING = "settings_enable_monitor_phantom_procs"
    private const val CHANNEL = "linux_security"
    private const val NOTIFICATION = 0x4c50

    fun limited(context: Context): Boolean =
        runCatching { Settings.Global.getString(context.contentResolver, SETTING) }.getOrNull() != "false"

    /** Lifts the limit if possible; true when Linux processes are no longer subject to it. */
    fun ensureLifted(context: Context): Boolean {
        if (!limited(context)) return true
        val granted = context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
        if (granted && runCatching { Settings.Global.putString(context.contentResolver, SETTING, "false") }.getOrDefault(false) &&
            !limited(context)) {
            Log.i(TAG, "child process restrictions turned off for the Android execution mode")
            return true
        }
        notifyLimited(context)
        return false
    }

    private fun notifyLimited(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val ja = java.util.Locale.getDefault().language == "ja"
        manager.createNotificationChannel(NotificationChannel(CHANNEL,
            moe.n4tsu.dextop.localText("Linux セキュリティ", "Linux security"), NotificationManager.IMPORTANCE_DEFAULT))
        val settings = PendingIntent.getActivity(context, NOTIFICATION,
            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE)
        val text = moe.n4tsu.dextop.localText("Android が Linux のプロセスを 32 個まで等に制限し、超えると強制終了します。開発者向けオプションの「子プロセスの制限を無効にする」をオンにしてください。", "Android limits Linux to 32 processes and kills the rest. Turn on \"Disable child process restrictions\" in Developer options.")
        runCatching {
            manager.notify(NOTIFICATION, Notification.Builder(context, CHANNEL)
                .setSmallIcon(moe.n4tsu.dextop.R.drawable.ic_qs_dextop)
                .setContentTitle(moe.n4tsu.dextop.localText("Linux のプロセスが終了される可能性があります", "Linux processes may be killed"))
                .setContentText(text).setStyle(Notification.BigTextStyle().bigText(text))
                .setContentIntent(settings).setAutoCancel(true).build())
        }
    }
}
