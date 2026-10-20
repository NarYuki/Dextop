package moe.n4tsu.dextop.linux

import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import org.json.JSONObject
import java.io.File
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
            return LinuxAppSession(containerId, display, process, client, events).also {
                it.begin()
                it.startWayland(width, height, proot.nativeLibraryDir)
            }
        }

        /** Window ids of Wayland toplevels and popups, apart from X window ids (below 0x20000000). */
        const val WAYLAND_ID = 0x60000000
        /** WAYLAND_DISPLAY inside the container; the socket shim maps it to the abstract socket. */
        const val WAYLAND_DISPLAY = "wayland-0"

        fun isWayland(id: Int) = id and WAYLAND_ID == WAYLAND_ID
    }

    // region Wayland
    /** Dextop's Wayland compositor for this display's container, or null when it did not start. */
    @Volatile var wayland: WaylandCompositor? = null; private set
    /** Display positions of mapped Wayland windows, to place their popups. */
    private val waylandBounds = ConcurrentHashMap<Int, Rect>()
    private val waylandWindows = ConcurrentHashMap<Int, String>()
    /** Keyboard input goes to Wayland while a Wayland window is the active one. */
    @Volatile private var waylandFocused = false

    private fun startWayland(width: Int, height: Int, nativeLibraryDir: String) {
        val socket = LinuxProot.socketNamespace(containerId) + "/tmp/runtime-root/" + WAYLAND_DISPLAY
        // GPU presentation: the APK's Turnip under the system Vulkan loader, through adrenotools' hooks.
        if (File(nativeLibraryDir, WaylandCompositor.GPU_DRIVER).isFile) runCatching {
            android.system.Os.setenv("DEXTOP_LINUX_GPU_DRIVER", "$nativeLibraryDir/", true)
            android.system.Os.setenv("DEXTOP_LINUX_GPU_LIBRARY", WaylandCompositor.GPU_DRIVER, true)
            android.system.Os.setenv("DEXTOP_LINUX_GPU_HOOKS", "$nativeLibraryDir/", true)
        }
        wayland = runCatching {
            WaylandCompositor(socket, width, height, ::onWaylandEvent).takeIf { it.running }
        }.onFailure { Log.w(TAG, "Wayland compositor unavailable", it) }.getOrNull()
        Log.i(TAG, "[$display] Wayland compositor ${if (wayland != null) "listening on @$socket" else "not started"}")
    }

    private fun onWaylandEvent(event: JSONObject) {
        if (event.optString("type") == "error") {
            Log.e(TAG, event.optString("message"))
            events(event)
            return
        }
        val xid = WAYLAND_ID or event.optInt("id")
        val popup = event.optBoolean("popup")
        val type = event.optString("type")
        if (type == "move" || type == "minimize") return
        val parent = event.optInt("parent").takeIf { it != 0 }?.let { WAYLAND_ID or it }
        // Popups come relative to their parent's window; Plasma places windows in display coordinates.
        val origin = parent?.let(waylandBounds::get)
        val left = (origin?.left ?: 0) + event.optInt("x")
        val top = (origin?.top ?: 0) + event.optInt("y")
        val width = event.optInt("w")
        val height = event.optInt("h")
        when (type) {
            "unmap" -> waylandBounds.remove(xid)
            else -> if (popup) waylandBounds[xid] = Rect(left, top, left + width, top + height)
                else waylandBounds.putIfAbsent(xid, Rect(0, 0, width, height))
        }
        val translated = JSONObject()
            .put("type", type).put("xid", xid).put("popup", popup)
            .put("title", event.optString("title")).put("class", event.optString("class"))
            .put("transientFor", parent ?: 0)
            .put("x", left).put("y", top).put("w", width).put("h", height)
        when (type) {
            "map" -> waylandWindows[xid] = translated.toString()
            "unmap" -> waylandWindows.remove(xid)
            else -> waylandWindows.computeIfPresent(xid) { _, snapshot ->
                val cached = JSONObject(snapshot)
                translated.keys().forEach { key -> if (key != "type") cached.put(key, translated.get(key)) }
                cached.toString()
            }
        }
        events(translated)
    }
    // endregion

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

    private var clipboard: X11Clipboard? = null
    /** Text an X client copied to CLIPBOARD. */
    @Volatile var onClipboard: (String) -> Unit = {}

    /** Puts Android's clipboard text on this display's CLIPBOARD. */
    fun setClipboard(text: String) {
        clipboard?.setText(text)
    }

    private fun begin() {
        clipboard = runCatching { X11Clipboard(x) { onClipboard(it) } }
            .onFailure { Log.w(TAG, "clipboard unavailable", it) }.getOrNull()
        atomNetWmName = x.internAtom("_NET_WM_NAME")
        atomUtf8 = x.internAtom("UTF8_STRING")
        atomProtocols = x.internAtom("WM_PROTOCOLS")
        atomDelete = x.internAtom("WM_DELETE_WINDOW")
        x.onEvent = { code, event -> if (clipboard?.offer(code, event) != true) queue.offer(code to event) }
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

    /** MIT-SHM capture buffer, grown to the largest area read so far; null without MIT-SHM. */
    private var shared: X11SharedImage? = null
    private var sharedFailed = false

    /** A shared buffer for [bytes] bytes, or null to read through the socket. */
    private fun sharedImage(bytes: Long): X11SharedImage? {
        if (sharedFailed) return null
        shared?.let { if (it.size >= bytes) return it; it.close() }
        // Room for the whole screen avoids regrowing for every larger window.
        val size = maxOf(bytes, x.screenWidth.toLong() * x.screenHeight * 4)
        shared = X11SharedImage.create(x, size)
        if (shared == null) sharedFailed = true
        Log.i(TAG, "[$display] window capture: ${if (shared != null) "MIT-SHM ($size bytes)" else "X socket (no MIT-SHM)"}")
        return shared
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
                val bytes = dirty.width().toLong() * dirty.height() * 4
                val shm = sharedImage(bytes)
                if (shm != null) {
                    if (!runCatching { x.shmGetImage(window.pixmap, dirty, shm.segment) }.getOrDefault(false)) return@forEach
                    synchronized(renderLock) {
                        val handle = window.surfaceHandle
                        if (handle != 0L && !LinuxFrameBridge.nativeBlitAddress(
                                handle, shm.address, shm.size, dirty.width() * 4, dirty.left, dirty.top, dirty.width(), dirty.height())
                        ) {
                            LinuxFrameBridge.nativeDetach(handle)
                            window.surfaceHandle = 0L
                        }
                    }
                    return@forEach
                }
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
        if (isWayland(id)) return wayland?.takeIf { it.running }?.let {
            it.attach(id and WAYLAND_ID.inv(), surface)
            true
        } ?: false
        val window = windows[id] ?: return false
        detachSurface(id)
        val handle = LinuxFrameBridge.nativeAttach(surface, window.bounds.width().coerceAtLeast(1), window.bounds.height().coerceAtLeast(1))
        if (handle == 0L) return false
        synchronized(renderLock) { window.surfaceHandle = handle }
        markDirty(window, Rect(0, 0, window.bounds.width(), window.bounds.height()))
        return true
    }

    fun detachSurface(id: Int) {
        if (isWayland(id)) { wayland?.detach(id and WAYLAND_ID.inv()); return }
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
        if (isWayland(id)) {
            waylandBounds[id] = Rect(left, top, left + width, top + height)
            wayland?.configure(id and WAYLAND_ID.inv(), width, height)
            return
        }
        if (!windows.containsKey(id)) return
        x.configureWindow(
            id, X11Client.CONFIG_X or X11Client.CONFIG_Y or X11Client.CONFIG_WIDTH or X11Client.CONFIG_HEIGHT,
            intArrayOf(left, top, width.coerceAtLeast(1), height.coerceAtLeast(1)),
        )
    }

    fun activate(id: Int) {
        waylandFocused = isWayland(id)
        if (isWayland(id)) { wayland?.activate(id and WAYLAND_ID.inv()); return }
        if (!windows.containsKey(id)) return
        x.raise(id)
        x.setInputFocus(id)
    }

    /** Asks the client to close [id] (WM_DELETE_WINDOW), or disconnects it when it does not support that. */
    fun close(id: Int) {
        if (isWayland(id)) { wayland?.close(id and WAYLAND_ID.inv()); return }
        if (!windows.containsKey(id)) return
        if (atomDelete in x.propertyAtoms(id, atomProtocols)) x.clientMessage(id, atomProtocols, intArrayOf(atomDelete, 0))
        else x.killClient(id)
    }

    /** Pointer input at ([localX], [localY]) inside window [id]; [action] 0 move, 1 press, 2 release. */
    fun pointer(id: Int, action: Int, localX: Int, localY: Int, button: Int) {
        if (isWayland(id)) {
            if (action == 1) waylandFocused = true
            wayland?.pointer(id and WAYLAND_ID.inv(), action, localX, localY, button)
            return
        }
        if (action == 1) waylandFocused = false
        val window = windows[id] ?: return
        input.pointer(action, window.bounds.left + localX, window.bounds.top + localY, button)
    }

    fun scroll(dx: Int, dy: Int) = wayland?.takeIf { waylandFocused }?.scroll(dx, dy) ?: input.scroll(dx, dy)
    fun key(evdevCode: Int, down: Boolean) = wayland?.takeIf { waylandFocused }?.key(evdevCode, down) ?: input.key(evdevCode, down)
    fun text(text: String) = wayland?.takeIf { waylandFocused }?.text(text) ?: input.text(text)

    /** Map events for every window, for a Plasma session that (re)connects. */
    fun replay() {
        waylandWindows.values.toList().forEach { events(JSONObject(it).put("type", "map")) }
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
        wayland?.stop()
        waylandWindows.clear()
        waylandBounds.clear()
        clipboard?.stop()
        shared?.close()
        x.close()
        process.destroyForcibly()
    }
}
