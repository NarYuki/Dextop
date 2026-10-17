package moe.n4tsu.dextop.linux

import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import org.json.JSONObject
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue

/**
 * The X display of a container's application windows. There is no window
 * manager inside the container: this session is the window manager. Every
 * top-level window is redirected offscreen (Composite), shown as its own
 * Plasma window, and drawn from GetImage of its damaged areas.
 *
 * The X screen has the size of the Plasma display and each X window sits
 * at the position of its Plasma window's content, so menus and other
 * override-redirect popups open at the right place on the display.
 */
internal class LinuxAppSession(
    val containerId: String,
    val display: Int,
    private val process: Process,
    private val x: X11Client,
    private val events: (JSONObject) -> Unit,
) {
    companion object {
        private const val TAG = "DextopLinuxApps"
        private const val FRAME_INTERVAL_MS = 8L

        fun start(
            proot: LinuxProot, containerId: String, display: Int, width: Int, height: Int, dpi: Int,
            events: (JSONObject) -> Unit,
        ): LinuxAppSession {
            val process = proot.guest(containerId, LinuxCatalog.appsScript(display, width, height, dpi))
                .redirectErrorStream(true).start()
            process.outputStream.close()
            Thread {
                // Destroying the process interrupts this read; that must not crash the service.
                runCatching { process.inputStream.bufferedReader().forEachLine { Log.i(TAG, "[$display] $it") } }
            }.apply { isDaemon = true; start() }
            val deadline = SystemClock.uptimeMillis() + 15_000
            var client: X11Client? = null
            while (SystemClock.uptimeMillis() < deadline && client == null) {
                check(process.isAlive) { "Xvfb exited (exit ${process.exitValue()})" }
                client = runCatching { X11Client.connect(display) }.getOrNull()
                if (client == null) SystemClock.sleep(150)
            }
            if (client == null) {
                process.destroyForcibly()
                error("X display :$display did not start")
            }
            check(client.compositeOpcode >= 0 && client.damageOpcode >= 0) {
                client.close()
                process.destroyForcibly()
                "Xvfb lacks Composite or DAMAGE"
            }
            return LinuxAppSession(containerId, display, process, client, events).also { it.begin() }
        }
    }

    private class Win(val id: Int, val popup: Boolean) {
        val bounds = Rect()
        val dirty = Rect()
        var damage = 0
        /** The window's Composite pixmap, read by the renderer; renamed after every resize. */
        @Volatile var pixmap = 0
        var surfaceHandle = 0L
        var title = ""
        var wmClass = ""
    }

    private val windows = ConcurrentHashMap<Int, Win>()
    private val queue = LinkedBlockingQueue<Pair<Int, ByteBuffer>>()
    private val renderLock = Object()
    private val input = X11Input(x)
    @Volatile private var running = true
    private var atomNetWmName = 0
    private var atomUtf8 = 0
    private var atomProtocols = 0
    private var atomDelete = 0

    val alive get() = running && process.isAlive

    private fun begin() {
        atomNetWmName = x.internAtom("_NET_WM_NAME")
        atomUtf8 = x.internAtom("UTF8_STRING")
        atomProtocols = x.internAtom("WM_PROTOCOLS")
        atomDelete = x.internAtom("WM_DELETE_WINDOW")
        x.onEvent = { code, event -> queue.offer(code to event) }
        x.onClosed = { stop() }
        x.redirectSubwindows(x.root)
        x.selectInput(x.root, X11Client.EVENT_SUBSTRUCTURE_REDIRECT or X11Client.EVENT_SUBSTRUCTURE_NOTIFY)
        x.sync()
        Thread(::eventLoop, "DextopLinuxWm-$display").apply { isDaemon = true; start() }
        Thread(::renderLoop, "DextopLinuxAppRender-$display").apply { isDaemon = true; start() }
    }

    // region window management
    private fun eventLoop() {
        while (running) {
            val (code, event) = runCatching { queue.take() }.getOrNull() ?: break
            runCatching { handle(code, event) }.onFailure { Log.w(TAG, "event $code failed", it) }
        }
    }

    private fun handle(code: Int, event: ByteBuffer) {
        if (code == x.damageEventBase) {
            val damage = event.getInt(8)
            val window = windows.values.firstOrNull { it.damage == damage } ?: return
            val area = Rect(
                event.getShort(16).toInt(), event.getShort(18).toInt(),
                event.getShort(16).toInt() + (event.getShort(20).toInt() and 0xffff),
                event.getShort(18).toInt() + (event.getShort(22).toInt() and 0xffff),
            )
            markDirty(window, area)
            return
        }
        when (code) {
            X11Client.MAP_REQUEST -> manage(event.getInt(8))
            X11Client.MAP_NOTIFY -> {
                val id = event.getInt(8)
                // Override-redirect windows (menus, tooltips) map without asking.
                if (event.getInt(4) == x.root && event.get(12).toInt() != 0 && !windows.containsKey(id)) track(id, popup = true)
            }
            X11Client.CONFIGURE_REQUEST -> configureRequest(event)
            X11Client.CONFIGURE_NOTIFY -> {
                val id = event.getInt(8)
                val window = windows[id] ?: return
                val x0 = event.getShort(16).toInt()
                val y0 = event.getShort(18).toInt()
                val bounds = Rect(x0, y0, x0 + (event.getShort(20).toInt() and 0xffff), y0 + (event.getShort(22).toInt() and 0xffff))
                updateBounds(window, bounds)
            }
            X11Client.UNMAP_NOTIFY, X11Client.DESTROY_NOTIFY -> {
                val id = event.getInt(8)
                if (event.getInt(4) != x.root && code == X11Client.UNMAP_NOTIFY) return
                forget(id)
            }
            X11Client.PROPERTY_NOTIFY -> {
                val window = windows[event.getInt(4)] ?: return
                val atom = event.getInt(8)
                if (atom == X11Client.ATOM_WM_NAME || atom == atomNetWmName) {
                    val title = readTitle(window.id)
                    if (title != window.title) {
                        window.title = title
                        events(JSONObject().put("type", "title").put("xid", window.id).put("title", title))
                    }
                }
                if (atom == X11Client.ATOM_WM_CLASS) {
                    window.wmClass = readClass(window.id)
                    events(JSONObject().put("type", "class").put("xid", window.id).put("class", window.wmClass))
                }
            }
        }
    }

    private fun manage(id: Int) {
        // No borders: the Plasma window draws the frame.
        x.configureWindow(id, X11Client.CONFIG_BORDER, intArrayOf(0))
        x.selectInput(id, X11Client.EVENT_PROPERTY_CHANGE)
        x.mapWindow(id)
        if (!windows.containsKey(id)) track(id, popup = false)
    }

    private fun track(id: Int, popup: Boolean) {
        val bounds = x.geometry(id) ?: return
        val window = Win(id, popup)
        window.bounds.set(bounds)
        window.damage = x.damageCreate(id)
        window.pixmap = x.nameWindowPixmap(id)
        if (!popup) {
            window.title = readTitle(id)
            window.wmClass = readClass(id)
        }
        windows[id] = window
        val transientFor = if (popup) 0 else x.propertyAtoms(id, X11Client.ATOM_WM_TRANSIENT_FOR).firstOrNull() ?: 0
        events(JSONObject()
            .put("type", "map").put("xid", id).put("popup", popup)
            .put("title", window.title).put("class", window.wmClass).put("transientFor", transientFor)
            .put("x", bounds.left).put("y", bounds.top).put("w", bounds.width()).put("h", bounds.height()))
        Log.i(TAG, "[$display] ${if (popup) "popup" else "window"} 0x${id.toString(16)} $bounds '${window.title}' ${window.wmClass}")
    }

    private fun forget(id: Int) {
        val window = windows.remove(id) ?: return
        synchronized(renderLock) {
            if (window.surfaceHandle != 0L) LinuxFrameBridge.nativeDetach(window.surfaceHandle)
            window.surfaceHandle = 0L
        }
        runCatching { x.damageDestroy(window.damage) }
        runCatching { x.freePixmap(window.pixmap) }
        events(JSONObject().put("type", "unmap").put("xid", id).put("popup", window.popup))
    }

    /** Clients may move and resize themselves (the Plasma window follows); stacking stays Dextop's. */
    private fun configureRequest(event: ByteBuffer) {
        val id = event.getInt(8)
        val mask = (event.getShort(26).toInt() and 0xffff) and
            (X11Client.CONFIG_X or X11Client.CONFIG_Y or X11Client.CONFIG_WIDTH or X11Client.CONFIG_HEIGHT)
        val values = ArrayList<Int>()
        if (mask and X11Client.CONFIG_X != 0) values += event.getShort(16).toInt()
        if (mask and X11Client.CONFIG_Y != 0) values += event.getShort(18).toInt()
        if (mask and X11Client.CONFIG_WIDTH != 0) values += event.getShort(20).toInt() and 0xffff
        if (mask and X11Client.CONFIG_HEIGHT != 0) values += event.getShort(22).toInt() and 0xffff
        if (mask != 0) x.configureWindow(id, mask, values.toIntArray())
    }

    private fun updateBounds(window: Win, bounds: Rect) {
        if (bounds == window.bounds) return
        val resized = bounds.width() != window.bounds.width() || bounds.height() != window.bounds.height()
        window.bounds.set(bounds)
        if (resized) {
            val old = window.pixmap
            window.pixmap = x.nameWindowPixmap(window.id)
            x.freePixmap(old)
            synchronized(renderLock) {
                if (window.surfaceHandle != 0L) LinuxFrameBridge.nativeResize(window.surfaceHandle, bounds.width(), bounds.height())
            }
            markDirty(window, Rect(0, 0, bounds.width(), bounds.height()))
        }
        events(JSONObject().put("type", "configure").put("xid", window.id).put("popup", window.popup)
            .put("x", bounds.left).put("y", bounds.top).put("w", bounds.width()).put("h", bounds.height()))
    }

    private fun readTitle(id: Int): String {
        x.property(id, atomNetWmName, atomUtf8)?.let { return String(it.third, Charsets.UTF_8) }
        return x.property(id, X11Client.ATOM_WM_NAME)?.let { String(it.third, Charsets.ISO_8859_1) }.orEmpty()
    }

    /** The class part of WM_CLASS ("instance\0Class\0"). */
    private fun readClass(id: Int): String {
        val bytes = x.property(id, X11Client.ATOM_WM_CLASS)?.third ?: return ""
        val parts = String(bytes, Charsets.ISO_8859_1).split('\u0000').filter { it.isNotEmpty() }
        return parts.getOrNull(1) ?: parts.firstOrNull().orEmpty()
    }
    // endregion

    // region rendering
    private fun markDirty(window: Win, area: Rect) = synchronized(renderLock) {
        if (window.dirty.isEmpty) window.dirty.set(area) else window.dirty.union(area)
        renderLock.notifyAll()
    }

    private fun renderLoop() {
        val area = Rect()
        while (running) {
            val work = synchronized(renderLock) {
                var ready = windows.values.filter { it.surfaceHandle != 0L && !it.dirty.isEmpty }
                if (ready.isEmpty()) {
                    renderLock.wait(250)
                    ready = windows.values.filter { it.surfaceHandle != 0L && !it.dirty.isEmpty }
                }
                ready.map { window ->
                    area.set(window.dirty)
                    window.dirty.setEmpty()
                    window to Rect(area)
                }
            }
            work.forEach { (window, dirty) ->
                if (!dirty.intersect(0, 0, window.bounds.width(), window.bounds.height())) return@forEach
                val pixels = runCatching { x.image(window.pixmap, dirty) }.getOrNull() ?: return@forEach
                synchronized(renderLock) {
                    val handle = window.surfaceHandle
                    if (handle != 0L && !LinuxFrameBridge.nativeBlitPixels(
                            handle, pixels.array(), 32, dirty.width() * 4, dirty.left, dirty.top, dirty.width(), dirty.height())
                    ) {
                        LinuxFrameBridge.nativeDetach(handle)
                        window.surfaceHandle = 0L
                    }
                }
            }
            SystemClock.sleep(FRAME_INTERVAL_MS)
        }
    }

    fun attachSurface(id: Int, surface: Surface): Boolean {
        val window = windows[id] ?: return false
        detachSurface(id)
        val handle = LinuxFrameBridge.nativeAttach(surface, window.bounds.width().coerceAtLeast(1), window.bounds.height().coerceAtLeast(1))
        if (handle == 0L) return false
        synchronized(renderLock) { window.surfaceHandle = handle }
        markDirty(window, Rect(0, 0, window.bounds.width(), window.bounds.height()))
        return true
    }

    fun detachSurface(id: Int) {
        val window = windows[id] ?: return
        synchronized(renderLock) {
            val handle = window.surfaceHandle
            window.surfaceHandle = 0L
            if (handle != 0L) LinuxFrameBridge.nativeDetach(handle)
        }
    }
    // endregion

    // region requests from Plasma
    /** Moves and resizes window [id] to the Plasma window's content area (display coordinates). */
    fun configure(id: Int, left: Int, top: Int, width: Int, height: Int) {
        if (!windows.containsKey(id)) return
        x.configureWindow(
            id, X11Client.CONFIG_X or X11Client.CONFIG_Y or X11Client.CONFIG_WIDTH or X11Client.CONFIG_HEIGHT,
            intArrayOf(left, top, width.coerceAtLeast(1), height.coerceAtLeast(1)),
        )
    }

    fun activate(id: Int) {
        if (!windows.containsKey(id)) return
        x.raise(id)
        x.setInputFocus(id)
    }

    /** Asks the client to close [id] (WM_DELETE_WINDOW), or disconnects it when it does not support that. */
    fun close(id: Int) {
        if (!windows.containsKey(id)) return
        if (atomDelete in x.propertyAtoms(id, atomProtocols)) x.clientMessage(id, atomProtocols, intArrayOf(atomDelete, 0))
        else x.killClient(id)
    }

    /** Pointer input at ([localX], [localY]) inside window [id]; [action] 0 move, 1 press, 2 release. */
    fun pointer(id: Int, action: Int, localX: Int, localY: Int, button: Int) {
        val window = windows[id] ?: return
        input.pointer(action, window.bounds.left + localX, window.bounds.top + localY, button)
    }

    fun scroll(dx: Int, dy: Int) = input.scroll(dx, dy)
    fun key(evdevCode: Int, down: Boolean) = input.key(evdevCode, down)
    fun text(text: String) = input.text(text)

    /** Map events for every window, for a Plasma session that (re)connects. */
    fun replay() {
        windows.values.forEach { window ->
            val b = window.bounds
            events(JSONObject()
                .put("type", "map").put("xid", window.id).put("popup", window.popup)
                .put("title", window.title).put("class", window.wmClass).put("transientFor", 0)
                .put("x", b.left).put("y", b.top).put("w", b.width()).put("h", b.height()))
        }
    }
    // endregion

    fun stop() {
        if (!running) return
        running = false
        synchronized(renderLock) {
            windows.values.forEach { if (it.surfaceHandle != 0L) LinuxFrameBridge.nativeDetach(it.surfaceHandle); it.surfaceHandle = 0L }
            renderLock.notifyAll()
        }
        queue.offer(-1 to ByteBuffer.allocate(0))
        x.close()
        process.destroyForcibly()
    }
}
