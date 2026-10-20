package moe.n4tsu.dextop.plasma

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.CopyOnWriteArrayList

/** One entry of the Plasma notification list. */
internal data class DextopNotification(
    val key: String,
    val linux: Boolean,
    val app: String,
    val title: String,
    val text: String,
    val time: Long,
    val icon: Drawable?,
    /** Android package, or the Linux container id. */
    val owner: String,
    val clearable: Boolean,
)

/** Android and Linux notifications shown by Plasma's notification tray. */
internal object NotificationCenter {
    private val handler = Handler(Looper.getMainLooper())
    private val items = LinkedHashMap<String, DextopNotification>()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun addListener(listener: () -> Unit) { listeners += listener }
    fun removeListener(listener: () -> Unit) { listeners -= listener }

    /** Newest first. */
    fun all(): List<DextopNotification> = synchronized(items) { items.values.sortedByDescending { it.time } }

    fun count(): Int = synchronized(items) { items.size }

    fun put(item: DextopNotification) {
        synchronized(items) { items[item.key] = item }
        changed()
    }

    fun remove(key: String) {
        val removed = synchronized(items) { items.remove(key) != null }
        if (removed) changed()
    }

    fun removeWhere(predicate: (DextopNotification) -> Boolean) {
        val removed = synchronized(items) { items.values.removeAll(predicate) }
        if (removed) changed()
    }

    private fun changed() = handler.post { listeners.forEach { it() } }
}

/**
 * Reads Android's notifications for Plasma. Plasma grants itself the
 * notification-listener permission through the privileged shell.
 */
class DextopNotificationListener : NotificationListenerService() {
    companion object {
        @Volatile private var instance: DextopNotificationListener? = null

        fun component(context: Context) = ComponentName(context, DextopNotificationListener::class.java)

        /** Dismisses an Android notification from Plasma's list. */
        fun cancel(key: String) {
            runCatching { instance?.cancelNotification(key.removePrefix("android:")) }
        }

        /** The notification's tap action, if it is still posted. */
        internal fun contentIntent(key: String) = runCatching {
            instance?.activeNotifications?.firstOrNull { "android:${it.key}" == key }?.notification?.contentIntent
        }.getOrNull()
    }

    override fun onListenerConnected() {
        instance = this
        runCatching { activeNotifications }.getOrNull()?.forEach(::publish)
    }

    override fun onListenerDisconnected() {
        instance = null
        NotificationCenter.removeWhere { !it.linux }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = publish(sbn)

    override fun onNotificationRemoved(sbn: StatusBarNotification) = NotificationCenter.remove("android:${sbn.key}")

    private fun publish(sbn: StatusBarNotification) {
        val notification = sbn.notification
        if (sbn.packageName == packageName) return
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return
        val label = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        }.getOrDefault(sbn.packageName)
        val icon = runCatching { packageManager.getApplicationIcon(sbn.packageName) }.getOrNull()
        NotificationCenter.put(DextopNotification(
            key = "android:${sbn.key}", linux = false, app = label, title = title, text = text,
            time = notification.`when`.takeIf { it > 0 } ?: sbn.postTime, icon = icon,
            owner = sbn.packageName, clearable = sbn.isClearable,
        ))
    }
}
