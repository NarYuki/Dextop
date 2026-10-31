package moe.n4tsu.dextop.linux

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import moe.n4tsu.dextop.MirrorService
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Keeps Dextop running while a Linux container runs, so its applications go on in the
 * background, with an ongoing notification of the running containers and applications.
 * Linux desktop notifications become Android notifications here when no Plasma desktop
 * shows them.
 */
class LinuxSessionService : Service(), LinuxContainerClient.Listener {
    companion object {
        private const val CHANNEL_RUNNING = "linux_running"
        private const val CHANNEL_APPS = "linux_notifications"
        private const val ONGOING_ID = 0x4c78
        private const val POLL_MS = 5_000L

        /** Starts the service (from a visible screen); it stops itself once nothing runs. */
        fun start(context: Context) {
            runCatching { context.startForegroundService(Intent(context, LinuxSessionService::class.java)) }
        }

        @Volatile private var instance: LinuxSessionService? = null

        /** Refreshes the ongoing notification, e.g. after an application window opened or closed. */
        fun changed() {
            instance?.let { service -> service.main.post { service.poll() } }
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "DextopLinuxSession") }
    private val ticker = object : Runnable {
        override fun run() {
            poll()
            main.postDelayed(this, POLL_MS)
        }
    }
    private var runningContainers = emptyList<String>()
    private var polledOnce = false
    /** Application icons by container and lower-case name, for notifications. */
    private val icons = ConcurrentHashMap<String, Map<String, Bitmap>>()

    private val japanese get() = Locale.getDefault().language == "ja"
    private fun t(ja: String, en: String) = moe.n4tsu.dextop.localText(ja, en)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_RUNNING,
            t("Linux の実行状態", "Linux status"), NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
        manager.createNotificationChannel(NotificationChannel(CHANNEL_APPS,
            t("Linux アプリの通知", "Linux app notifications"), NotificationManager.IMPORTANCE_DEFAULT))
        LinuxContainerClient.addListener(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(ONGOING_ID, ongoing(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        main.removeCallbacks(ticker)
        main.post(ticker)
        return START_STICKY
    }

    override fun onDestroy() {
        main.removeCallbacks(ticker)
        LinuxContainerClient.removeListener(this)
        worker.shutdownNow()
        if (instance === this) instance = null
        super.onDestroy()
    }

    /** Reads which containers run, then updates the notification or stops when nothing runs. */
    private fun poll() {
        val linux = LinuxContainerClient.service()
        if (linux == null) {
            LinuxContainerClient.connect(this)
            update()
            return
        }
        worker.execute {
            val running = runCatching {
                val list = JSONArray(linux.listContainers())
                (0 until list.length()).map(list::getJSONObject).filter {
                    it.optString("runtimeState") == "running" || it.optBoolean("appsRunning")
                }.map { it.optString("name").ifBlank { it.optString("id") } }
            }.getOrDefault(runningContainers)
            main.post {
                runningContainers = running
                polledOnce = true
                update()
            }
        }
    }

    private fun update() {
        if (polledOnce && runningContainers.isEmpty() && !LinuxAndroidWindows.busy()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        getSystemService(NotificationManager::class.java).notify(ONGOING_ID, ongoing())
    }

    private fun ongoing(): Notification {
        val apps = LinuxAndroidWindows.runningApps()
        val containers = runningContainers
        val title = when (containers.size) {
            0 -> t("Linux コンテナ実行中", "Linux container running")
            1 -> moe.n4tsu.dextop.InlineStrings.format(t("Linux コンテナ実行中・{0}", "Linux container running · {0}"), containers[0])
            else -> moe.n4tsu.dextop.InlineStrings.format(t("Linux コンテナ {0} 個が実行中", "{0} Linux containers running"), containers.size)
        }
        val text = if (apps.isEmpty()) t("実行中のアプリはありません", "No apps running")
            else moe.n4tsu.dextop.InlineStrings.format(t("実行中のアプリ {0} 個: ", "{0} apps running: "), apps.size) + apps.joinToString(", ")
        val open = PendingIntent.getActivity(this, 0,
            packageManager.getLaunchIntentForPackage(packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ?: Intent(), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, CHANNEL_RUNNING)
            .setSmallIcon(moe.n4tsu.dextop.R.drawable.ic_qs_dextop)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    // region Linux notifications

    override fun onNotification(containerId: String, event: JSONObject) {
        // A running Plasma desktop lists them in its own notification centre.
        if (MirrorService.plasmaDesktopRunning) return
        val key = event.optString("key")
        val id = "linux:$containerId:$key".hashCode()
        val manager = getSystemService(NotificationManager::class.java)
        when (event.optString("type")) {
            "close" -> manager.cancel(id)
            "notify" -> {
                val app = event.optString("app").ifBlank { "Linux" }
                appIcon(containerId, app) { icon -> manager.notify(id, linuxNotification(containerId, key, id, app, icon, event)) }
            }
        }
    }

    private fun linuxNotification(container: String, key: String, id: Int, app: String, icon: Bitmap?, event: JSONObject): Notification {
        fun action(name: String, request: Int) = PendingIntent.getBroadcast(this, id * 8 + request,
            Intent(this, NotificationAction::class.java)
                .putExtra("container", container).putExtra("key", key).putExtra("action", name).putExtra("id", id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = Notification.Builder(this, CHANNEL_APPS)
            .setSmallIcon(moe.n4tsu.dextop.R.drawable.ic_qs_dextop)
            .setContentTitle(event.optString("summary").ifBlank { app })
            .setContentText(event.optString("body"))
            .setStyle(Notification.BigTextStyle().bigText(event.optString("body")))
            .setSubText(app)
            .setWhen(event.optLong("time", System.currentTimeMillis()))
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(action("default", 0))
            .setDeleteIntent(action("dismiss", 1))
        icon?.let(builder::setLargeIcon)
        // Actions come as key/label pairs; "default" is the click on the notification itself.
        val actions = event.optJSONArray("actions") ?: JSONArray()
        var slot = 2
        var index = 0
        while (index + 1 < actions.length() && slot < 5) {
            val name = actions.optString(index)
            val label = actions.optString(index + 1)
            if (name != "default" && label.isNotBlank()) {
                builder.addAction(Notification.Action.Builder(null, label, action(name, slot)).build())
                slot++
            }
            index += 2
        }
        return builder.build()
    }

    /** The icon of the application named [app] in [container], from its desktop entries. */
    private fun appIcon(container: String, app: String, done: (Bitmap?) -> Unit) {
        LinuxAndroidWindows.iconFor(container, app)?.let { done(it); return }
        icons[container]?.let { done(it[app.lowercase()]); return }
        val linux = LinuxContainerClient.service() ?: return done(null)
        worker.execute {
            val loaded = runCatching {
                val list = JSONArray(linux.listApps(container, Locale.getDefault().toLanguageTag()))
                (0 until list.length()).map(list::getJSONObject).mapNotNull { entry ->
                    val encoded = entry.optString("icon").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                    val bytes = android.util.Base64.decode(encoded, android.util.Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@mapNotNull null
                    entry.optString("name").lowercase() to bitmap
                }.toMap()
            }.getOrDefault(emptyMap())
            icons[container] = loaded
            main.post { done(loaded[app.lowercase()]) }
        }
    }

    /** Sends the click, an action or the dismissal of a Linux notification back to the container. */
    class NotificationAction : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val container = intent.getStringExtra("container") ?: return
            val key = intent.getStringExtra("key") ?: return
            val action = intent.getStringExtra("action") ?: return
            if (action != "dismiss") context.getSystemService(NotificationManager::class.java).cancel(intent.getIntExtra("id", 0))
            Thread { runCatching { LinuxContainerClient.service()?.notificationAction(container, key, action) } }.start()
        }
    }
    // endregion
}
