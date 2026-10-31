package moe.n4tsu.dextop.linux

import android.os.SystemClock
import android.util.Base64
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** StatusNotifier watcher and host on the application session bus (KDE and freedesktop names). */
internal class LinuxStatusNotifier(private val display: Int, private val root: File,
    private val events: (JSONObject) -> Unit) {
    companion object {
        private const val BUS = "org.freedesktop.DBus"
        private const val PROPERTIES = "org.freedesktop.DBus.Properties"
        private const val WATCHER = "org.kde.StatusNotifierWatcher"
        private const val FREE_WATCHER = "org.freedesktop.StatusNotifierWatcher"
        private const val ITEM = "org.kde.StatusNotifierItem"
        private const val FREE_ITEM = "org.freedesktop.StatusNotifierItem"
        private const val PATH = "/StatusNotifierWatcher"
        private const val MENU = "com.canonical.dbusmenu"
    }
    private class Entry(val service: String, val path: String) {
        val key = "sni:$service$path"
        @Volatile var owner = service
        @Volatile var iface = ITEM
        @Volatile var snapshot = ""
        @Volatile var fetching = false
        @Volatile var menu = ""
        @Volatile var itemIsMenu = false
    }
    private val items = ConcurrentHashMap<String, Entry>()
    private val pending = ConcurrentHashMap<Int, (DBusMessage) -> Unit>()
    @Volatile private var running = true
    @Volatile private var connection: DBusConnection? = null

    init {
        Thread(::run, "DextopStatusNotifier-$display").apply { isDaemon = true; start() }
        Thread({ while (running) { items.values.forEach(::refresh); SystemClock.sleep(1_000) } }, "DextopStatusIcons-$display").apply { isDaemon = true; start() }
    }

    private fun run() {
        while (running) {
            val bus = runCatching { DBusConnection.connect("/tmp/dextop-dbus-$display", listOf(0, android.os.Process.myUid())) }.getOrNull()
            if (bus == null) { SystemClock.sleep(500); continue }
            connection = bus
            try {
                bus.send(DBusMessage.METHOD_CALL, path = "/org/freedesktop/DBus", iface = BUS, member = "Hello", destination = BUS)
                listOf(WATCHER, FREE_WATCHER).forEach { watcher ->
                    request(bus, "/org/freedesktop/DBus", BUS, "RequestName", BUS, "su", { string(watcher); u32(4) }) { reply ->
                        if (reply.type == DBusMessage.METHOD_RETURN && reply.reader().u32() !in listOf(1, 4)) attachWatcher(bus, watcher)
                    }
                }
                val host = "org.kde.StatusNotifierHost.Dextop$display"
                bus.send(DBusMessage.METHOD_CALL, path = "/org/freedesktop/DBus", iface = BUS, member = "RequestName", destination = BUS, signature = "su") { string(host); u32(4) }
                listOf(BUS, ITEM, FREE_ITEM, WATCHER, FREE_WATCHER, PROPERTIES).forEach { iface ->
                    bus.send(DBusMessage.METHOD_CALL, path = "/org/freedesktop/DBus", iface = BUS, member = "AddMatch", destination = BUS, signature = "s") {
                        string("type='signal',interface='$iface'")
                    }
                }
                while (running) {
                    val message = bus.receive()
                    runCatching { handle(bus, message) }.onFailure { Log.w("DextopLinuxTray", "StatusNotifier message rejected: ${it.message}") }
                }
            } catch (error: Exception) {
                if (running) Log.w("DextopLinuxTray", "StatusNotifier bus disconnected: ${error.message}")
            } finally {
                connection = null
                bus.close(); pending.clear()
                items.values.toList().forEach(::remove)
            }
            SystemClock.sleep(500)
        }
    }

    private fun request(bus: DBusConnection, path: String, iface: String, member: String, service: String,
        signature: String = "", body: DBusWriter.() -> Unit = {}, reply: (DBusMessage) -> Unit) {
        synchronized(bus) {
            val serial = bus.send(DBusMessage.METHOD_CALL, path = path, iface = iface, member = member,
                destination = service, signature = signature, body = body)
            pending[serial] = reply
        }
    }

    private fun attachWatcher(bus: DBusConnection, watcher: String) {
        bus.send(DBusMessage.METHOD_CALL, path = PATH, iface = watcher, member = "RegisterStatusNotifierHost", destination = watcher, signature = "s") {
            string("org.kde.StatusNotifierHost.Dextop$display")
        }
        request(bus, PATH, PROPERTIES, "Get", watcher, "ss", { string(watcher); string("RegisteredStatusNotifierItems") }) { response ->
            if (response.type == DBusMessage.METHOD_RETURN) (response.reader().value("v") as? List<*>)?.filterIsInstance<String>()?.forEach { register(bus, it, "") }
        }
    }

    private fun register(bus: DBusConnection, value: String, sender: String) {
        val service = if (value.startsWith('/')) sender else value.substringBefore('/')
        val path = if (value.startsWith('/')) value else value.indexOf('/').takeIf { it >= 0 }?.let { value.substring(it) } ?: "/StatusNotifierItem"
        if (service.isEmpty() || !path.matches(Regex("/[A-Za-z0-9_/]*")) || items.size >= 128) return
        val entry = Entry(service, path)
        if (items.putIfAbsent(entry.key, entry) != null) return
        request(bus, "/org/freedesktop/DBus", BUS, "GetNameOwner", BUS, "s", { string(service) }) { response ->
            if (response.type == DBusMessage.METHOD_RETURN) { entry.owner = response.reader().string(); refresh(entry) } else remove(entry)
        }
        listOf(WATCHER, FREE_WATCHER).forEach { iface ->
            bus.send(DBusMessage.SIGNAL, path = PATH, iface = iface, member = "StatusNotifierItemRegistered", signature = "s") { string(service + path) }
        }
    }

    private fun handle(bus: DBusConnection, message: DBusMessage) {
        if (message.type == DBusMessage.METHOD_RETURN || message.type == DBusMessage.ERROR) {
            synchronized(bus) { pending.remove(message.replySerial) }?.invoke(message)
            return
        }
        if (message.type == DBusMessage.SIGNAL) {
            if (message.iface == BUS && message.member == "NameOwnerChanged") {
                val reader = message.reader(); val service = reader.string(); val old = reader.string(); val owner = reader.string()
                if (old.isNotEmpty() && owner != old) items.values.filter { it.service == service || it.owner == service }.forEach(::remove)
                if (owner.isNotEmpty() && service in listOf(WATCHER, FREE_WATCHER) && old.isNotEmpty()) attachWatcher(bus, service)
            } else if (message.iface in listOf(WATCHER, FREE_WATCHER) && message.member == "StatusNotifierItemRegistered") {
                register(bus, message.reader().string(), message.sender.orEmpty())
            } else if (message.iface in listOf(ITEM, FREE_ITEM, PROPERTIES)) {
                items.values.filter { it.owner == message.sender && it.path == message.path }.forEach(::refresh)
            }
            return
        }
        if (message.type != DBusMessage.METHOD_CALL) return
        fun reply(signature: String = "", body: DBusWriter.() -> Unit = {}) = bus.send(DBusMessage.METHOD_RETURN,
            replySerial = message.serial, destination = message.sender, signature = signature, body = body)
        when {
            message.iface in listOf(WATCHER, FREE_WATCHER) && message.member == "RegisterStatusNotifierItem" -> {
                register(bus, message.reader().string(), message.sender.orEmpty()); reply()
            }
            message.iface in listOf(WATCHER, FREE_WATCHER) && message.member == "RegisterStatusNotifierHost" -> {
                reply()
                bus.send(DBusMessage.SIGNAL, path = PATH, iface = message.iface, member = "StatusNotifierHostRegistered")
            }
            message.iface == PROPERTIES && message.member == "Get" -> {
                val reader = message.reader(); reader.string(); val property = reader.string()
                if (property in listOf("RegisteredStatusNotifierItems", "IsStatusNotifierHostRegistered", "ProtocolVersion")) reply("v") { property(property) }
                else bus.send(DBusMessage.ERROR, destination = message.sender, replySerial = message.serial, errorName = "org.freedesktop.DBus.Error.UnknownProperty")
            }
            message.iface == PROPERTIES && message.member == "GetAll" -> reply("a{sv}") {
                array(8) { for (name in listOf("RegisteredStatusNotifierItems", "IsStatusNotifierHostRegistered", "ProtocolVersion")) { align(8); string(name); property(name) } }
            }
            message.member == "Ping" -> reply()
            message.member == "Introspect" -> reply("s") { string(introspection()) }
            else -> bus.send(DBusMessage.ERROR, destination = message.sender, replySerial = message.serial, errorName = "org.freedesktop.DBus.Error.UnknownMethod")
        }
    }

    private fun DBusWriter.property(name: String) {
        when (name) {
            "RegisteredStatusNotifierItems" -> { signature("as"); array(4) { items.values.forEach { string(it.service + it.path) } } }
            "IsStatusNotifierHostRegistered" -> { signature("b"); u32(1) }
            else -> { signature("i"); u32(0) }
        }
    }

    private fun introspection() = "<node>" + listOf(WATCHER, FREE_WATCHER).joinToString("") { iface ->
        "<interface name='$iface'><method name='RegisterStatusNotifierItem'><arg type='s' direction='in'/></method>" +
        "<method name='RegisterStatusNotifierHost'><arg type='s' direction='in'/></method>" +
        "<property name='RegisteredStatusNotifierItems' type='as' access='read'/><property name='IsStatusNotifierHostRegistered' type='b' access='read'/>" +
        "<property name='ProtocolVersion' type='i' access='read'/><signal name='StatusNotifierItemRegistered'><arg type='s'/></signal>" +
        "<signal name='StatusNotifierItemUnregistered'><arg type='s'/></signal><signal name='StatusNotifierHostRegistered'/></interface>"
    } + "<interface name='$PROPERTIES'><method name='Get'><arg type='s' direction='in'/><arg type='s' direction='in'/><arg type='v' direction='out'/></method>" +
        "<method name='GetAll'><arg type='s' direction='in'/><arg type='a{sv}' direction='out'/></method></interface></node>"

    private fun refresh(entry: Entry) {
        val bus = connection ?: return
        synchronized(entry) { if (entry.fetching || items[entry.key] !== entry) return; entry.fetching = true }
        runCatching {
            request(bus, entry.path, PROPERTIES, "GetAll", entry.service, "s", { string(entry.iface) }) { response ->
                entry.fetching = false
                if (items[entry.key] !== entry) return@request
                if (response.type == DBusMessage.ERROR) {
                    if (entry.iface == ITEM) { entry.iface = FREE_ITEM; refresh(entry) }
                    return@request
                }
                val properties = response.reader().value("a{sv}") as Map<*, *>
                if (properties["Status"] == "Passive") {
                    entry.snapshot = ""; events(JSONObject().put("type", "tray-remove").put("key", entry.key)); return@request
                }
                entry.menu = (properties["Menu"] as? String).orEmpty().takeUnless { it == "/" }.orEmpty()
                entry.itemIsMenu = properties["ItemIsMenu"] == true
                val attention = properties["Status"] == "NeedsAttention"
                val prefix = if (attention) "AttentionIcon" else "Icon"
                val encoded = pixmap(properties[prefix + "Pixmap"]) ?: (properties[prefix + "Name"] as? String)?.let {
                    LinuxDesktopEntries.iconPng(root, it)?.let { png -> Base64.encodeToString(png, Base64.NO_WRAP) }
                } ?: pixmap(properties["IconPixmap"]) ?: ""
                val tooltip = properties["ToolTip"] as? List<*>
                val title = (properties["Title"] as? String)?.takeIf { it.isNotBlank() }
                    ?: (tooltip?.getOrNull(2) as? String)?.takeIf { it.isNotBlank() } ?: (properties["Id"] as? String) ?: "Linux"
                val event = JSONObject().put("type", "tray").put("key", entry.key).put("title", title.take(512)).put("icon", encoded)
                val snapshot = event.toString()
                if (entry.snapshot != snapshot) { entry.snapshot = snapshot; events(event) }
            }
        }.onFailure { entry.fetching = false }
    }

    private fun pixmap(value: Any?): String? {
        val images = (value as? List<*>)?.filterIsInstance<List<*>>() ?: return null
        val image = images.filter { it.size == 3 && (it[0] as? Int ?: 0) in 1..512 && (it[1] as? Int ?: 0) in 1..512 }
            .minByOrNull { kotlin.math.abs((it[0] as Int) - 32) } ?: return null
        val w = image[0] as Int; val h = image[1] as Int; val bytes = image[2] as? ByteArray ?: return null
        if (bytes.size != w * h * 4) return null
        val pixels = IntArray(w * h) { i -> val n = i * 4; ((bytes[n].toInt() and 255) shl 24) or ((bytes[n + 1].toInt() and 255) shl 16) or ((bytes[n + 2].toInt() and 255) shl 8) or (bytes[n + 3].toInt() and 255) }
        return Base64.encodeToString(LinuxIconDecoder.argbPng(pixels, w, h), Base64.NO_WRAP)
    }

    fun activate(key: String, button: Int, x: Int, y: Int) {
        val entry = items[key.substringBefore("#menu=")] ?: return
        val bus = connection ?: return
        runCatching {
            if ("#menu=" in key) {
                val id = key.substringAfter("#menu=").toIntOrNull() ?: return
                bus.send(DBusMessage.METHOD_CALL, path = entry.menu, iface = MENU, member = "Event", destination = entry.service, signature = "isvu") {
                    u32(id); string("clicked"); signature("i"); u32(0); u32(SystemClock.uptimeMillis().toInt())
                }
            } else if (entry.menu.isNotEmpty() && (button == 3 || entry.itemIsMenu)) {
                request(bus, entry.menu, MENU, "AboutToShow", entry.service, "i", { u32(0) }) {
                    request(bus, entry.menu, MENU, "GetLayout", entry.service, "iias", { u32(0); u32(-1); array(4) {} }) { response ->
                        if (response.type == DBusMessage.METHOD_RETURN) {
                            val reader = response.reader(); reader.u32()
                            val menu = reader.value("(ia{sv}av)") as List<*>
                            events(JSONObject().put("type", "tray-menu").put("key", entry.key).put("x", x).put("y", y).put("items", menuItems(menu)))
                        }
                    }
                }
            } else bus.send(DBusMessage.METHOD_CALL, path = entry.path, iface = entry.iface,
                member = when (button) { 3 -> "ContextMenu"; 2 -> "SecondaryActivate"; else -> "Activate" }, destination = entry.service, signature = "ii") { u32(x); u32(y) }
        }
    }

    private fun menuItems(layout: List<*>, depth: Int = 0): JSONArray {
        val result = JSONArray()
        if (depth > 10) return result
        (layout.getOrNull(2) as? List<*>)?.take(256)?.filterIsInstance<List<*>>()?.forEach { node ->
            val props = node.getOrNull(1) as? Map<*, *> ?: return@forEach
            if (props["visible"] == false) return@forEach
            val item = JSONObject().put("id", node[0]).put("label", props["label"] ?: "")
                .put("enabled", props["enabled"] != false).put("separator", props["type"] == "separator")
                .put("children", menuItems(node, depth + 1))
            if (props["toggle-type"] in listOf("checkmark", "radio")) item.put("checked", props["toggle-state"] == 1)
            result.put(item)
        }
        return result
    }

    private fun remove(entry: Entry) {
        if (!items.remove(entry.key, entry)) return
        events(JSONObject().put("type", "tray-remove").put("key", entry.key))
        connection?.let { bus -> listOf(WATCHER, FREE_WATCHER).forEach { iface -> runCatching {
            bus.send(DBusMessage.SIGNAL, path = PATH, iface = iface, member = "StatusNotifierItemUnregistered", signature = "s") { string(entry.service + entry.path) }
        } } }
    }
    fun replay() { items.values.filter { it.snapshot.isNotEmpty() }.forEach { events(JSONObject(it.snapshot)) } }
    fun stop() { running = false; connection?.close() }
}
