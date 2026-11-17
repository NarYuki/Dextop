package moe.n4tsu.dextop.linux

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/** Same APK, ordinary Android UID. It never starts or attaches a privilege provider. */
class LinuxRuntimeService : Service() {
    companion object {
        const val ACTION_KEEP = "moe.n4tsu.dextop.linux.KEEP_SERVICES"
        const val ACTION_BOOT = "moe.n4tsu.dextop.linux.BOOT_SERVICES"
        const val EXTRA_CONTAINERS = "containers"
        private const val CHANNEL = "linux_services"
        private const val NOTIFICATION_ID = 0x4c79
        private const val CHECK_MS = 30_000L

        fun keep(context: Context) {
            runCatching { context.startForegroundService(Intent(context, LinuxRuntimeService::class.java).setAction(ACTION_KEEP)) }
        }
    }

    private lateinit var runtime: LinuxContainerService
    private val main = Handler(Looper.getMainLooper())
    private var foreground = false
    private val booting = java.util.concurrent.atomic.AtomicInteger()
    private val check = object : Runnable {
        override fun run() {
            if (!foreground) return
            if (booting.get() > 0 || runtime.servicesRunning()) {
                main.postDelayed(this, CHECK_MS)
            } else {
                foreground = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        check(android.os.Process.myUid() !in setOf(0, 2000))
        LinuxPaths.initializeAndroid(this)
        LinuxPhantomProcesses.ensureLifted(this)
        runtime = LinuxContainerService(applicationContext)
    }

    override fun onBind(intent: Intent?): IBinder = runtime

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        foreground = true
        if (intent?.action == ACTION_BOOT) {
            val ids = intent.getStringArrayExtra(EXTRA_CONTAINERS).orEmpty()
            booting.incrementAndGet()
            Thread({
                try {
                    for (id in ids) runCatching { runtime.bootServices(id) }
                        .onSuccess { android.util.Log.i("DextopServices", "Services of $id started") }
                        .onFailure { android.util.Log.w("DextopServices", "Starting services of $id failed", it) }
                } finally {
                    booting.decrementAndGet()
                }
            }, "DextopBootServices").apply { isDaemon = true; start() }
        }
        main.removeCallbacks(check)
        main.postDelayed(check, CHECK_MS)
        return START_NOT_STICKY
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, moe.n4tsu.dextop.localText("uiLinuxServices"),
            NotificationManager.IMPORTANCE_MIN).apply { setShowBadge(false) })
        val open = PendingIntent.getActivity(this, 0,
            packageManager.getLaunchIntentForPackage(packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: Intent(),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(moe.n4tsu.dextop.R.drawable.ic_qs_dextop)
            .setContentTitle(moe.n4tsu.dextop.localText("uiLinuxServicesAreRunning"))
            .setOngoing(true)
            .setContentIntent(open)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        runtime.close()
        super.onDestroy()
    }
}
