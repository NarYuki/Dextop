package moe.n4tsu.dextop.linux

import android.os.SystemClock
import android.util.Log
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

/**
 * org.freedesktop.Notifications on a container display's session bus:
 * notifications Linux applications send (notify-send, browsers, mail
 * clients) go to Dextop's notification list instead of a Linux daemon.
 */
internal class LinuxNotificationServer(
    private val display: Int,
    private val events: (JSONObject) -> Unit,
) {
    companion object {
        private const val TAG = "DextopLinuxNotify"
        private const val NAME = "org.freedesktop.Notifications"
        private const val PATH = "/org/freedesktop/Notifications"
        private const val BUS = "org.freedesktop.DBus"
        /** Proot reports root inside the container; the kernel sees the shell user. */
        private val UIDS = listOf(0, 2000)
    }

    @Volatile private var running = true
    @Volatile private var connection: DBusConnection? = null
    private val ids = AtomicInteger(0)

    init {
        Thread(::run, "DextopLinuxNotify-$display").apply { isDaemon = true; start() }
    }

    private fun run() {
        // The session bus starts with the display; keep retrying until it is up.
        while (running) {
            val bus = runCatching { DBusConnection.connect("/tmp/dextop-dbus-$display", UIDS) }.getOrNull()
            if (bus == null) {
                SystemClock.sleep(1_000)
                continue
            }
            connection = bus
            try {
                bus.send(DBusMessage.METHOD_CALL, path = "/org/freedesktop/DBus", iface = BUS, member = "Hello", destination = BUS)
                // REPLACE_EXISTING | DO_NOT_QUEUE: Dextop's list replaces a Linux notification daemon.
                bus.send(DBusMessage.METHOD_CALL, path = "/org/freedesktop/DBus", iface = BUS, member = "RequestName",
                    destination = BUS, signature = "su") { string(NAME); u32(6) }
                Log.i(TAG, "[$display] notification server registered")
                while (running) handle(bus, bus.receive())
            } catch (error: Throwable) {
                if (running) Log.w(TAG, "[$display] session bus connection lost", error)
            } finally {
                bus.close()
                connection = null
            }
            SystemClock.sleep(1_000)
        }
    }

    private fun handle(bus: DBusConnection, message: DBusMessage) {
        if (message.type != DBusMessage.METHOD_CALL) return
        val sender = message.sender
        fun reply(signature: String = "", body: DBusWriter.() -> Unit = {}) =
            bus.send(DBusMessage.METHOD_RETURN, replySerial = message.serial, destination = sender, signature = signature, body = body)
        when (message.iface to message.member) {
            NAME to "GetCapabilities" -> reply("as") {
                array(4) { listOf("body", "actions", "persistence").forEach(::string) }
            }
            NAME to "GetServerInformation" -> reply("ssss") {
                string("Dextop"); string("Dextop"); string("1.0"); string("1.2")
            }
            NAME to "Notify" -> {
                val body = message.reader()
                val app = body.string()
                val replaces = body.u32()
                val icon = body.string()
                val summary = body.string()
                val text = body.string()
                val actions = body.strings()
                val id = if (replaces != 0) replaces else ids.incrementAndGet()
                reply("u") { u32(id) }
                events(JSONObject()
                    .put("type", "notify").put("key", "$display:$id")
                    .put("app", app).put("icon", icon).put("summary", summary).put("body", stripMarkup(text))
                    .put("actions", org.json.JSONArray(actions)).put("time", System.currentTimeMillis()))
            }
            NAME to "CloseNotification" -> {
                val id = message.reader().u32()
                reply()
                closed(id, 3)
                events(JSONObject().put("type", "close").put("key", "$display:$id"))
            }
            "org.freedesktop.DBus.Peer" to "Ping" -> reply()
            "org.freedesktop.DBus.Introspectable" to "Introspect" -> reply("s") {
                string("<node><interface name=\"$NAME\"/></node>")
            }
            else -> bus.send(DBusMessage.ERROR, replySerial = message.serial, destination = sender,
                errorName = "org.freedesktop.DBus.Error.UnknownMethod", signature = "s") {
                string("Unknown method ${message.member}")
            }
        }
    }

    /** Notification bodies may carry a small HTML subset; the list shows plain text. */
    private fun stripMarkup(text: String) = text.replace(Regex("<[^>]+>"), "")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")

    private fun closed(id: Int, reason: Int) {
        runCatching {
            connection?.send(DBusMessage.SIGNAL, path = PATH, iface = NAME, member = "NotificationClosed", signature = "uu") {
                u32(id); u32(reason)
            }
        }
    }

    /** The user clicked notification [id] ("default") or one of its actions. */
    fun invoke(id: Int, action: String) {
        runCatching {
            connection?.send(DBusMessage.SIGNAL, path = PATH, iface = NAME, member = "ActionInvoked", signature = "us") {
                u32(id); string(action)
            }
        }
        closed(id, 2)
    }

    /** The user dismissed notification [id]. */
    fun dismiss(id: Int) = closed(id, 2)

    fun stop() {
        running = false
        connection?.close()
    }
}
