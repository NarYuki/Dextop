package moe.n4tsu.dextop.linux

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** freedesktop system-tray / XEmbed manager, shared by Wine and native X11 apps. */
internal class LinuxX11Tray(private val x: X11Client, private val events: (JSONObject) -> Unit,
    private val forgetWindow: (Int) -> Unit) {
    private data class Icon(val id: Int, val host: Int, var width: Int, var height: Int, var pixmap: Int = 0, var visible: Boolean = true, var snapshot: String = "")
    private val icons = LinkedHashMap<Int, Icon>()
    private val selection = x.internAtom("_NET_SYSTEM_TRAY_S0")
    private val opcode = x.internAtom("_NET_SYSTEM_TRAY_OPCODE")
    private val embed = x.internAtom("_XEMBED")
    private val info = x.internAtom("_XEMBED_INFO")
    private val name = x.internAtom("_NET_WM_NAME")
    private var owner = 0
    @Volatile private var running = true

    fun start() {
        if (x.selectionOwner(selection) != 0) return
        owner = x.createHiddenWindow()
        x.selectInput(owner, X11Client.EVENT_SUBSTRUCTURE_NOTIFY)
        x.changeProperty(owner, x.internAtom("_NET_SYSTEM_TRAY_ORIENTATION"), 6, 32, ByteArray(4))
        x.setSelectionOwner(owner, selection)
        x.sync()
        check(x.selectionOwner(selection) == owner) { "Cannot own X11 system tray" }
        if (x.argbVisual != 0) x.changeProperty(owner, x.internAtom("_NET_SYSTEM_TRAY_VISUAL"), 32, 32,
            ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(x.argbVisual).array())
        val event = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN)
            .put(33).put(32).putShort(0).putInt(x.root).putInt(x.internAtom("MANAGER"))
            .putInt(0).putInt(selection).putInt(owner).putInt(0).putInt(0)
        x.sendEvent(x.root, X11Client.EVENT_STRUCTURE_NOTIFY, event)
        Thread({ while (running) { runCatching { refresh() }; SystemClock.sleep(250) } }, "DextopLinuxTray").apply { isDaemon = true; start() }
    }

    @Synchronized fun contains(id: Int) = id == owner || id in icons || icons.values.any { it.host == id }

    @Synchronized fun offer(code: Int, event: ByteBuffer): Boolean {
        if (owner == 0) return false
        if (code == 29 && event.getInt(12) == selection) { stop(); return true }
        if (code == 33 && event.get(1).toInt() == 32 && event.getInt(4) == owner && event.getInt(8) == opcode) {
            if (event.getInt(16) == 0) dock(event.getInt(20))
            return true
        }
        val id = when (code) {
            X11Client.PROPERTY_NOTIFY -> event.getInt(4)
            X11Client.MAP_REQUEST, X11Client.MAP_NOTIFY, X11Client.UNMAP_NOTIFY,
            X11Client.DESTROY_NOTIFY, X11Client.CONFIGURE_REQUEST, X11Client.CONFIGURE_NOTIFY, 21 -> event.getInt(8)
            else -> return false
        }
        if (!contains(id)) return false
        if (id == owner || icons.values.any { it.host == id }) return true
        when (code) {
            X11Client.DESTROY_NOTIFY -> remove(id)
            21 -> if (event.getInt(12) != icons[id]?.host) remove(id)
            X11Client.PROPERTY_NOTIFY -> if (event.getInt(8) == info) mapped(icons.getValue(id))
            X11Client.CONFIGURE_REQUEST -> icons[id]?.let(::size)
            X11Client.MAP_REQUEST -> if (icons[id]?.visible == true) x.mapWindow(id)
        }
        return true
    }

    private fun size(icon: Icon) {
        x.configureWindow(icon.host, X11Client.CONFIG_WIDTH or X11Client.CONFIG_HEIGHT, intArrayOf(icon.width, icon.height))
        x.configureWindow(icon.id, X11Client.CONFIG_X or X11Client.CONFIG_Y or X11Client.CONFIG_WIDTH or X11Client.CONFIG_HEIGHT or X11Client.CONFIG_BORDER,
            intArrayOf(0, 0, icon.width, icon.height, 0))
    }

    private fun dock(id: Int) {
        val attributes = x.windowAttributes(id) ?: return
        if (id == 0 || contains(id) || icons.size >= 128 || !attributes.inputOutput) return
        val geometry = x.geometry(id) ?: return
        val host = x.createTrayWindow(attributes.visual)
        val icon = Icon(id, host, geometry.width().coerceIn(1, 128), geometry.height().coerceIn(1, 128))
        // Separate hosts prevent sibling pixels and ParentRelative backgrounds from leaking between icons.
        x.configureWindow(host, X11Client.CONFIG_X or X11Client.CONFIG_Y, intArrayOf(((icons.size % 8) * 64).coerceAtMost((x.screenWidth - icon.width).coerceAtLeast(0)), ((icons.size / 8) * 64).coerceAtMost((x.screenHeight - icon.height).coerceAtLeast(0))))
        icons[id] = icon
        forgetWindow(id)
        x.selectInput(host, X11Client.EVENT_SUBSTRUCTURE_NOTIFY)
        x.redirectSubwindows(host)
        x.selectInput(id, X11Client.EVENT_PROPERTY_CHANGE or X11Client.EVENT_STRUCTURE_NOTIFY)
        x.saveWindow(id)
        x.mapWindow(host)
        x.reparentWindow(id, host)
        size(icon)
        x.clientMessage(id, embed, intArrayOf(0, 0, 0, host, 0))
        x.clientMessage(id, embed, intArrayOf(0, 1, 0, 0, 0))
        mapped(icon)
        // Proton may use a custom WM_CLASS (for example steam_proton).
        // Inspect the process environment rather than matching application names.
        val pid = x.propertyAtoms(id, x.internAtom("_NET_WM_PID")).firstOrNull() ?: 0
        if (pid > 0) events(JSONObject().put("type", "tray-wine-metadata").put("pid", pid))
    }

    private fun mapped(icon: Icon) {
        val hints = x.propertyAtoms(icon.id, info)
        val visible = hints.size < 2 || hints[1] and 1 != 0
        icon.visible = visible
        if (visible) {
            x.mapWindow(icon.id)
            x.sync()
            if (icon.pixmap != 0) x.freePixmap(icon.pixmap)
            icon.pixmap = x.nameWindowPixmap(icon.id)
        } else {
            x.unmapWindow(icon.id)
            events(JSONObject().put("type", "tray-remove").put("key", "x11:${icon.id}"))
            icon.snapshot = ""
        }
    }

    @Synchronized private fun refresh() {
        icons.values.filter { it.visible && it.pixmap != 0 }.forEach { icon ->
            val geometry = x.geometry(icon.id) ?: return@forEach
            if (geometry.width() != icon.width || geometry.height() != icon.height) {
                icon.width = geometry.width().coerceIn(1, 128); icon.height = geometry.height().coerceIn(1, 128)
                size(icon); x.sync()
                x.freePixmap(icon.pixmap); icon.pixmap = x.nameWindowPixmap(icon.id)
            }
            // GetImage on the embedded window honors its visual and shape.
            // Its named backing pixmap can include opaque parent background.
            val image = x.image(icon.id, Rect(0, 0, icon.width, icon.height)) ?: return@forEach
            if (image.capacity() < 32 + icon.width * icon.height * 4) return@forEach
            val depth = image.get(1).toInt() and 255
            val pixels = IntArray(icon.width * icon.height) { n ->
                val argb = image.getInt(32 + n * 4)
                if (depth != 32) argb or (255 shl 24) else {
                    val a = argb ushr 24
                    if (a == 0) 0 else (a shl 24) or
                        (((argb ushr 16 and 255) * 255 / a).coerceAtMost(255) shl 16) or
                        (((argb ushr 8 and 255) * 255 / a).coerceAtMost(255) shl 8) or
                        ((argb and 255) * 255 / a).coerceAtMost(255)
                }
            }
            val bitmap = Bitmap.createBitmap(pixels, icon.width, icon.height, Bitmap.Config.ARGB_8888)
            val png = try { ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it); Base64.encodeToString(it.toByteArray(), Base64.NO_WRAP) } } finally { bitmap.recycle() }
            val title = sequenceOf(name, X11Client.ATOM_WM_NAME, X11Client.ATOM_WM_CLASS).mapNotNull { atom ->
                x.property(icon.id, atom)?.third?.toString(Charsets.UTF_8)?.replace('\u0000', ' ')?.trim()?.takeIf { it.isNotBlank() }
            }.firstOrNull() ?: "Linux"
            val event = JSONObject().put("type", "tray").put("key", "x11:${icon.id}").put("title", title.take(512)).put("icon", png)
            val snapshot = event.toString()
            if (snapshot != icon.snapshot) { icon.snapshot = snapshot; events(event) }
        }
    }

    @Synchronized fun replay() { icons.values.filter { it.visible && it.snapshot.isNotEmpty() }.forEach { events(JSONObject(it.snapshot)) } }

    @Synchronized fun click(id: Int, button: Int, rootX: Int, rootY: Int) {
        val icon = icons[id]?.takeIf { it.visible } ?: return
        if (button !in 1..5) return
        val left = rootX.coerceIn(0, (x.screenWidth - icon.width).coerceAtLeast(0))
        val top = rootY.coerceIn(0, (x.screenHeight - icon.height).coerceAtLeast(0))
        x.configureWindow(icon.host, X11Client.CONFIG_X or X11Client.CONFIG_Y, intArrayOf(left, top))
        x.raise(icon.host); x.raise(id)
        x.fakeInput(X11Client.MOTION_NOTIFY, 0, left + icon.width / 2, top + icon.height / 2)
        x.fakeInput(X11Client.BUTTON_PRESS, button)
        x.fakeInput(X11Client.BUTTON_RELEASE, button)
    }

    private fun remove(id: Int) {
        val icon = icons.remove(id) ?: return
        if (icon.pixmap != 0) runCatching { x.freePixmap(icon.pixmap) }
        runCatching { x.destroyWindow(icon.host) }
        events(JSONObject().put("type", "tray-remove").put("key", "x11:$id"))
    }

    @Synchronized fun stop() {
        running = false
        icons.keys.toList().forEach { id -> runCatching { x.reparentWindow(id, x.root) }; remove(id) }
        if (owner != 0) {
            runCatching { if (x.selectionOwner(selection) == owner) x.setSelectionOwner(0, selection) }
            runCatching { x.destroyWindow(owner) }; owner = 0
        }
    }
}
