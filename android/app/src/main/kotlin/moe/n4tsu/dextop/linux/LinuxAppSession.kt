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
    /** Null for a Wayland-only display (DroidDeck's runtime: KWin brings its own Xwayland). */
    private val xClient: X11Client?,
    private val events: (JSONObject) -> Unit,
) {
    private val x: X11Client get() = checkNotNull(xClient) { "Wayland-only display has no X server" }

    companion object {
        private const val TAG = "DextopLinuxApps"
        private const val DEFAULT_FRAME_INTERVAL_NS = 16_666_667L

        fun start(
            proot: LinuxProot, containerId: String, display: Int, width: Int, height: Int, dpi: Int,
            waylandOnly: Boolean = false, requireWayland: Boolean = false, xwayland: Boolean = false, events: (JSONObject) -> Unit,
        ): LinuxAppSession {
            val host = if (xwayland) LinuxXwaylandHost.start(containerId, display, width, height) else null
            var startedProcess: Process? = null
            try {
                val script = if (waylandOnly) LinuxCatalog.waylandAppsScript(display) else LinuxCatalog.appsScript(display, width, height, dpi, xwayland)
                val process = proot.guest(containerId, script).redirectErrorStream(true).start()
                startedProcess = process
                process.outputStream.close()
                val startupReady = java.util.concurrent.atomic.AtomicBoolean(false)
                Thread {
                    // Destroying the process interrupts this read; that must not crash the service.
                    runCatching { process.inputStream.bufferedReader().forEachLine {
                        if (it == "dextop-x-ready") startupReady.set(true)
                        Log.i(TAG, "[$display] $it")
                    } }
                }.apply { isDaemon = true; start() }
                if (waylandOnly) return LinuxAppSession(containerId, display, process, null, events).also {
                    it.screenWidth = width
                    it.screenHeight = height
                    it.waylandFocused = true
                    it.startWayland(width, height, proot.nativeLibraryDir)
                    check(it.wayland != null) { LinuxRuntime.terminate(process); "Wayland compositor did not start" }
                }
                val deadline = SystemClock.uptimeMillis() + 15_000
                var client: X11Client? = null
                while (SystemClock.uptimeMillis() < deadline && (client == null || !startupReady.get())) {
                    check(process.isAlive) { "X server exited (exit ${process.exitValue()})" }
                    if (client == null) client = runCatching { X11Client.connect(display) }.getOrNull()
                    if (client == null || !startupReady.get()) SystemClock.sleep(150)
                }
                if (client == null || !startupReady.get()) {
                    client?.close()
                    LinuxRuntime.terminate(process)
                    error("X display :$display did not start")
                }
                check(client.compositeOpcode >= 0 && client.damageOpcode >= 0) {
                    client.close()
                    LinuxRuntime.terminate(process)
                    "X server lacks Composite or DAMAGE"
                }
                return LinuxAppSession(containerId, display, process, client, events).also {
                    it.screenWidth = width
                    it.screenHeight = height
                    it.xwaylandHost = host
                    it.begin()
                    it.startWayland(width, height, proot.nativeLibraryDir)
                    check(!requireWayland || it.wayland?.running == true) {
                        it.stop()
                        "The selected Wayland compositor did not start; see the Linux session log"
                    }
                }
            } catch (error: Throwable) {
                startedProcess?.let(LinuxRuntime::terminate)
                host?.stop()
                throw error
            }
        }

        /** Window ids of Wayland toplevels and popups, apart from X window ids (below 0x20000000). */
        const val WAYLAND_ID = 0x60000000
        /** WAYLAND_DISPLAY inside the container; the socket shim maps it to the abstract socket. */
        const val WAYLAND_DISPLAY = "wayland-0"

        fun isWayland(id: Int) = id and WAYLAND_ID == WAYLAND_ID
    }

    /** The X screen's size, which DroidDeck's desktop takes for KWin's output. */
    @Volatile var screenWidth = 0; private set
    @Volatile var screenHeight = 0; private set
    @Volatile private var frameIntervalNanos = DEFAULT_FRAME_INTERVAL_NS
    @Volatile var displayConfiguration: LinuxDisplayConfiguration? = null; private set

    private var xwaylandHost: LinuxXwaylandHost? = null
    val usesXwayland get() = xwaylandHost != null

    // region Wayland
    /** Dextop's Wayland compositor for this display's container, or null when it did not start. */
    @Volatile var wayland: WaylandCompositor? = null; private set
    /** Display positions of mapped Wayland windows, to place their popups. */
    private val waylandBounds = ConcurrentHashMap<Int, Rect>()
    private val waylandWindows = ConcurrentHashMap<Int, String>()
    private val waylandPointerPositions = ConcurrentHashMap<Int, Pair<Int, Int>>()
    /** Keyboard input goes to Wayland while a Wayland window is the active one. */
    @Volatile private var waylandFocused = false

    private fun startWayland(width: Int, height: Int, nativeLibraryDir: String) {
        statusTray = LinuxStatusNotifier(display, LinuxPaths.rootfs(containerId), events)
        val socket = LinuxProot.socketNamespace(containerId) + "/tmp/runtime-root/" + WAYLAND_DISPLAY
        // GPU presentation: the APK's Turnip under the system Vulkan loader, through adrenotools' hooks.
        if (File(nativeLibraryDir, WaylandCompositor.GPU_DRIVER).isFile) runCatching {
            android.system.Os.setenv("DEXTOP_LINUX_GPU_DRIVER", "$nativeLibraryDir/", true)
            android.system.Os.setenv("DEXTOP_LINUX_GPU_LIBRARY", WaylandCompositor.GPU_DRIVER, true)
            android.system.Os.setenv("DEXTOP_LINUX_GPU_HOOKS", "$nativeLibraryDir/", true)
        }
        wayland = runCatching {
            WaylandCompositor(socket, width, height, File(LinuxPaths.rootfs(containerId), "tmp/runtime-root/$WAYLAND_DISPLAY.lock"), ::onWaylandEvent).takeIf { it.running }
        }.onFailure { Log.w(TAG, "Wayland compositor unavailable", it) }.getOrNull()
        Log.i(TAG, "[$display] Wayland compositor ${if (wayland != null) "listening on @$socket" else "not started"}")
    }

    @Volatile private var waylandPointerCaptured = false
    private fun onWaylandEvent(event: JSONObject) {
        if (event.optString("type") == "pointer-capture") {
            waylandPointerCaptured = event.optBoolean("capture")
            return
        }
        if (event.optString("type") == "error") {
            Log.e(TAG, event.optString("message"))
            events(event)
            return
        }
        val xid = WAYLAND_ID or event.optInt("id")
        val popup = event.optBoolean("popup")
        val type = event.optString("type")
        if (type in setOf("move", "moveresize", "minimize", "maximize")) {
            val control = JSONObject().put("xid", xid).put("type", if (type == "move") "moveresize" else type)
            if (type == "maximize") control.put("action", event.optInt("action", 2))
            if (type == "move" || type == "moveresize") {
                val position = waylandPointerPositions[xid] ?: (0 to 0)
                val bounds = waylandBounds[xid]
                control.put("direction", if (type == "move") 8 else event.optInt("direction", -1))
                    .put("x", (bounds?.left ?: 0) + position.first).put("y", (bounds?.top ?: 0) + position.second)
            }
            events(control)
            return
        }
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
            .put("deck", event.optBoolean("deck"))
            .put("transientFor", parent ?: 0)
            .put("decorated", event.optBoolean("decorated", true))
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
        val dirty = LinuxDamageRegion()
        var damage = 0
        /** The window's Composite pixmap, read by the renderer; renamed after every resize. */
        @Volatile var pixmap = 0
        var surfaceHandle = 0L
        var frameFailures = 0
        var retryAt = 0L
        var title = ""
        var wmClass = ""
        var decorated = true
        var icon = ""
    }

    private val windows = ConcurrentHashMap<Int, Win>()
    private val queue = LinkedBlockingQueue<Pair<Int, ByteBuffer>>()
    private val renderLock = Object()
    private val input by lazy { X11Input(x) }
    @Volatile private var running = true
    private var atomNetWmName = 0
    private var atomNetWmIcon = 0
    private var atomUtf8 = 0
    private var atomProtocols = 0
    private var atomDelete = 0
    private var atomMotif = 0
    private var atomGtkFrame = 0
    private var atomWmState = 0
    private var atomNetState = 0
    private var atomMaxH = 0
    private var atomMaxV = 0
    private var atomFullscreen = 0
    private var atomMoveResize = 0
    private var atomClose = 0
    private var atomActive = 0

    val alive get() = running && process.isAlive

    private var tray: LinuxX11Tray? = null
    private var statusTray: LinuxStatusNotifier? = null
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
        atomNetWmIcon = x.internAtom("_NET_WM_ICON")
        atomUtf8 = x.internAtom("UTF8_STRING")
        atomProtocols = x.internAtom("WM_PROTOCOLS")
        atomDelete = x.internAtom("WM_DELETE_WINDOW")
        atomMotif = x.internAtom("_MOTIF_WM_HINTS")
        atomGtkFrame = x.internAtom("_GTK_FRAME_EXTENTS")
        atomWmState = x.internAtom("WM_CHANGE_STATE")
        atomNetState = x.internAtom("_NET_WM_STATE")
        atomMaxH = x.internAtom("_NET_WM_STATE_MAXIMIZED_HORZ")
        atomMaxV = x.internAtom("_NET_WM_STATE_MAXIMIZED_VERT")
        atomFullscreen = x.internAtom("_NET_WM_STATE_FULLSCREEN")
        atomMoveResize = x.internAtom("_NET_WM_MOVERESIZE")
        atomClose = x.internAtom("_NET_CLOSE_WINDOW")
        atomActive = x.internAtom("_NET_ACTIVE_WINDOW")
        val supported = intArrayOf(atomNetState, atomMaxH, atomMaxV, atomFullscreen, atomMoveResize, atomClose, atomActive, atomNetWmIcon)
        val bytes = ByteBuffer.allocate(supported.size * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        supported.forEach(bytes::putInt)
        x.changeProperty(x.root, x.internAtom("_NET_SUPPORTED"), 4, 32, bytes.array())
        x.onEvent = { code, event -> if (clipboard?.offer(code, event) != true) queue.offer(code to event) }
        x.onClosed = { stop() }
        x.redirectSubwindows(x.root)
        x.selectInput(x.root, X11Client.EVENT_SUBSTRUCTURE_REDIRECT or X11Client.EVENT_SUBSTRUCTURE_NOTIFY)
        x.sync()
        // We redirect and present the root's children ourselves. Advertise that
        // compositor so X11 clients retain layered-window alpha instead of
        // flattening transparent pixels onto a black non-composited background.
        val compositing = x.internAtom("_NET_WM_CM_S0")
        if (x.selectionOwner(compositing) == 0) {
            val owner = x.createHiddenWindow()
            x.setSelectionOwner(owner, compositing)
            val announcement = ByteBuffer.allocate(32).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .put(33).put(32).putShort(0).putInt(x.root).putInt(x.internAtom("MANAGER"))
                .putInt(0).putInt(compositing).putInt(owner).putInt(0).putInt(0)
            x.sendEvent(x.root, X11Client.EVENT_STRUCTURE_NOTIFY, announcement)
            x.sync()
        }
        pointerGrabs = LinuxPointerGrabs(display)
        tray = LinuxX11Tray(x, events, ::forget).also { it.start() }
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
        if (tray?.offer(code, event) == true) return
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
            33 -> clientRequest(event)
            X11Client.MAP_REQUEST -> manage(event.getInt(8))
            X11Client.MAP_NOTIFY -> {
                val id = event.getInt(8)
                // Override-redirect windows (menus, tooltips) map without asking.
                if (event.getInt(4) == x.root && event.get(12).toInt() != 0 && !windows.containsKey(id)) track(id, popup = true)
            }
            X11Client.CONFIGURE_REQUEST -> configureRequest(event)
            X11Client.CONFIGURE_NOTIFY -> {
                val id = event.getInt(8)
                if (id == x.root) {
                    x.updateScreenSize(event.getShort(20).toInt() and 65535, event.getShort(22).toInt() and 65535)
                    return
                }
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
                if (atom == atomNetWmIcon && !window.popup) {
                    val icon = readIcon(window.id)
                    if (icon != window.icon) {
                        window.icon = icon
                        events(JSONObject().put("type", "icon").put("xid", window.id).put("icon", icon))
                    }
                }
                if (atom == atomNetState && !window.popup) {
                    events(JSONObject().put("type", "fullscreen").put("xid", window.id)
                        .put("enabled", atomFullscreen in x.propertyAtoms(window.id, atomNetState)))
                }
                if (atom == atomMotif || atom == atomGtkFrame) {
                    val decorated = readDecorated(window.id)
                    if (decorated != window.decorated) {
                        window.decorated = decorated
                        events(JSONObject().put("type", "decorations").put("xid", window.id).put("decorated", decorated))
                    }
                }
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
        if (x.windowAttributes(id)?.inputOutput != true) { x.mapWindow(id); return }
        // No borders: the Plasma window draws the frame.
        x.configureWindow(id, X11Client.CONFIG_BORDER, intArrayOf(0))
        x.selectInput(id, X11Client.EVENT_PROPERTY_CHANGE)
        x.mapWindow(id)
        if (!windows.containsKey(id)) track(id, popup = false)
    }

    private fun track(id: Int, popup: Boolean) {
        if (tray?.contains(id) == true) return
        // Wine's invisible pointer-confinement windows must never become Android input surfaces.
        if (x.windowAttributes(id)?.inputOutput != true) return
        val bounds = x.geometry(id) ?: return
        val window = Win(id, popup)
        window.bounds.set(bounds)
        window.damage = x.damageCreate(id)
        window.pixmap = x.nameWindowPixmap(id)
        if (!popup) {
            window.title = readTitle(id)
            window.wmClass = readClass(id)
            window.decorated = readDecorated(id)
            window.icon = readIcon(id)
        }
        windows[id] = window
        val transientFor = if (popup) 0 else x.propertyAtoms(id, X11Client.ATOM_WM_TRANSIENT_FOR).firstOrNull() ?: 0
        events(JSONObject()
            .put("type", "map").put("xid", id).put("popup", popup)
            .put("title", window.title).put("class", window.wmClass).put("transientFor", transientFor)
            .put("decorated", window.decorated)
            .put("icon", window.icon)
            .put("fullscreen", !popup && atomFullscreen in x.propertyAtoms(id, atomNetState))
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
        if (mask != 0) {
            val window = windows[id]
            if (window != null && !window.popup) {
                val b = window.bounds
                val left = if (mask and X11Client.CONFIG_X != 0) event.getShort(16).toInt() else b.left
                val top = if (mask and X11Client.CONFIG_Y != 0) event.getShort(18).toInt() else b.top
                val width = if (mask and X11Client.CONFIG_WIDTH != 0) event.getShort(20).toInt() and 65535 else b.width()
                val height = if (mask and X11Client.CONFIG_HEIGHT != 0) event.getShort(22).toInt() and 65535 else b.height()
                events(JSONObject().put("type", "client-configure").put("xid", id).put("x", left).put("y", top).put("w", width).put("h", height))
            }
            x.configureWindow(id, mask, values.toIntArray())
        }
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
        events(JSONObject().put("type", "configure").put("geometryOnly", true).put("xid", window.id).put("popup", window.popup)
            .put("x", bounds.left).put("y", bounds.top).put("w", bounds.width()).put("h", bounds.height()))
    }

    private fun readTitle(id: Int): String {
        x.property(id, atomNetWmName, atomUtf8)?.let { return String(it.third, Charsets.UTF_8) }
        return x.property(id, X11Client.ATOM_WM_NAME)?.let { String(it.third, Charsets.ISO_8859_1) }.orEmpty()
    }

    private fun readIcon(id: Int): String = runCatching {
        val (_, format, bytes) = x.property(id, atomNetWmIcon, 6, LinuxWindowIcon.MAX_PROPERTY_BYTES)
            ?: return ""
        if (format != 32) return ""
        LinuxIconDecoder.windowPng(bytes)?.let { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) }.orEmpty()
    }.getOrDefault("")

    private fun readDecorated(id: Int) = LinuxWindowHints.serverDecorated(
        x.propertyAtoms(id, atomMotif), x.propertyAtoms(id, atomGtkFrame),
    )

    private fun clientRequest(event: ByteBuffer) {
        if (event.get(1).toInt() != 32) return
        val id = event.getInt(4)
        if (windows[id]?.popup != false) return
        val type = event.getInt(8)
        val message = JSONObject().put("xid", id)
        when (type) {
            atomWmState -> if (event.getInt(12) == 3) events(message.put("type", "minimize"))
            atomNetState -> {
                val atoms = intArrayOf(event.getInt(16), event.getInt(20))
                if (atomFullscreen in atoms) {
                    val states = x.propertyAtoms(id, atomNetState).toMutableList()
                    val enabled = when (event.getInt(12)) {
                        0 -> false
                        1 -> true
                        2 -> atomFullscreen !in states
                        else -> return
                    }
                    states.removeAll { it == atomFullscreen }
                    if (enabled) states += atomFullscreen
                    val data = ByteBuffer.allocate(states.size * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                    states.forEach(data::putInt)
                    x.changeProperty(id, atomNetState, 4, 32, data.array())
                    events(JSONObject().put("xid", id).put("type", "fullscreen").put("enabled", enabled))
                }
                if (atomMaxH in atoms || atomMaxV in atoms)
                    events(message.put("type", "maximize").put("action", event.getInt(12)))
            }
            atomMoveResize -> events(message.put("type", "moveresize")
                .put("x", event.getInt(12)).put("y", event.getInt(16)).put("direction", event.getInt(20)))
            atomClose -> close(id)
            atomActive -> events(message.put("type", "activate"))
        }
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
        window.dirty.add(area.left, area.top, area.right, area.bottom)
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
        var nextFrame = System.nanoTime()
        var lastFailure = 0L
        try {
            while (running) {
                val work = synchronized(renderLock) {
                    var ready = windows.values.filter { it.surfaceHandle != 0L && !it.dirty.isEmpty && SystemClock.uptimeMillis() >= it.retryAt }
                    if (ready.isEmpty()) {
                        renderLock.wait(250)
                        ready = windows.values.filter { it.surfaceHandle != 0L && !it.dirty.isEmpty && SystemClock.uptimeMillis() >= it.retryAt }
                    }
                    ready.map { Triple(it, it.pixmap, it.dirty.drain()) }
                }
                if (!running) break
                work.forEach { (window, pixmap, regions) ->
                    var failed = false
                    for (region in regions) {
                        val dirty = Rect(region.left, region.top, region.right, region.bottom)
                        if (!dirty.intersect(0, 0, window.bounds.width(), window.bounds.height())) continue
                        val bytes = dirty.width().toLong() * dirty.height() * 4
                        var captured = false
                        var rejectedShm: X11SharedImage? = null
                        val shm = sharedImage(bytes)
                        if (shm != null) {
                            if (runCatching { x.shmGetImage(pixmap, dirty, shm.segment) }.getOrDefault(false)) {
                                captured = synchronized(renderLock) {
                                    val handle = window.surfaceHandle
                                    handle == 0L || window.pixmap != pixmap || LinuxFrameBridge.nativeStageAddress(
                                        handle, shm.address, shm.size, dirty.width() * 4, dirty.left, dirty.top, dirty.width(), dirty.height())
                                }
                            } else {
                                rejectedShm = shm
                            }
                        }
                        if (!captured) {
                            val pixels = runCatching { x.image(pixmap, dirty) }.getOrNull()
                            // If both paths fail this may just be an old pixmap during
                            // resize. Disable SHM only when GetImage proves the drawable valid.
                            if (pixels != null && rejectedShm != null) {
                                shared = null; sharedFailed = true; rejectedShm.close()
                                Log.w(TAG, "[$display] shared image failed; using X GetImage")
                            }
                            captured = pixels != null && synchronized(renderLock) {
                                val handle = window.surfaceHandle
                                handle == 0L || window.pixmap != pixmap || LinuxFrameBridge.nativeStagePixels(
                                    handle, pixels.array(), 32, dirty.width() * 4, dirty.left, dirty.top, dirty.width(), dirty.height())
                            }
                        }
                        if (!captured) { failed = true; break }
                    }
                    if (!failed) failed = synchronized(renderLock) {
                        val handle = window.surfaceHandle
                        handle != 0L && window.pixmap == pixmap && !LinuxFrameBridge.nativePresent(handle)
                    }
                    if (failed && running && windows[window.id] === window) {
                        synchronized(renderLock) {
                            window.frameFailures = (window.frameFailures + 1).coerceAtMost(6)
                            window.retryAt = SystemClock.uptimeMillis() + minOf(1_000L, 16L shl window.frameFailures)
                            markDirty(window, Rect(0, 0, window.bounds.width(), window.bounds.height()))
                        }
                        val now = SystemClock.uptimeMillis()
                        if (now - lastFailure > 5_000) {
                            lastFailure = now
                            Log.w(TAG, "[$display] frame failed for ${window.id}; scheduling full redraw")
                        }
                    } else if (!failed) synchronized(renderLock) { window.frameFailures = 0; window.retryAt = 0 }
                }
                nextFrame = maxOf(nextFrame + frameIntervalNanos, System.nanoTime())
                java.util.concurrent.locks.LockSupport.parkNanos((nextFrame - System.nanoTime()).coerceAtLeast(0))
            }
        } finally {
            // The render thread owns the mapping until its last X11 read completes.
            shared?.close(); shared = null
        }
    }

    fun attachSurface(id: Int, surface: Surface, deckDirect: Boolean = false, deck30: Boolean = false, noVsync: Boolean = false): Boolean {
        if (isWayland(id)) return wayland?.takeIf { it.running }?.let {
            it.attach(id and WAYLAND_ID.inv(), surface, deckDirect, deck30, noVsync)
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

    private var pointerGrabs: LinuxPointerGrabs? = null
    fun pointerCaptureRequested(): Boolean {
        if (waylandFocused) return waylandPointerCaptured
        // Flush RECORD's buffered disconnect notification even when rendering is idle.
        xClient?.sync()
        return pointerGrabs?.requested == true
    }
    fun relativePointer(id: Int, action: Int, dx: Int, dy: Int, button: Int) {
        if (isWayland(id)) { wayland?.pointer(id and WAYLAND_ID.inv(), action + 10, dx, dy, button); return }
        xClient?.let { x ->
            if (dx != 0 || dy != 0) x.relativeMotion(dx, dy)
            if (action == 1 || action == 2) x.fakeInput(if (action == 1) X11Client.BUTTON_PRESS else X11Client.BUTTON_RELEASE, button)
        }
    }
    fun activate(id: Int) {
        if (id == 0) {
            // Leaving a Linux app must send FocusOut so Wine can release ClipCursor/grabs.
            wayland?.activate(0)
            waylandFocused = false
            xClient?.let { it.setInputFocus(it.root) }
            return
        }
        waylandFocused = isWayland(id)
        if (isWayland(id)) { wayland?.activate(id and WAYLAND_ID.inv()); return }
        if (xClient == null) { waylandFocused = true; return }
        if (!windows.containsKey(id)) return
        x.raise(id)
        x.setInputFocus(id)
    }

    fun setWindowState(id: Int, maximized: Boolean, minimized: Boolean) {
        if (isWayland(id)) { wayland?.setState(id and WAYLAND_ID.inv(), maximized); return }
        if (xClient == null || !windows.containsKey(id)) return
        val hidden = x.internAtom("_NET_WM_STATE_HIDDEN")
        val states = x.propertyAtoms(id, atomNetState).filterNot { it == atomMaxH || it == atomMaxV || it == hidden }.toMutableList()
        if (maximized) { states += atomMaxH; states += atomMaxV }
        if (minimized) states += hidden
        val data = ByteBuffer.allocate(states.size * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        states.forEach(data::putInt)
        x.changeProperty(id, atomNetState, 4, 32, data.array())
        val wmState = x.internAtom("WM_STATE")
        x.changeProperty(id, wmState, wmState, 32,
            ByteBuffer.allocate(8).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(if (minimized) 3 else 1).putInt(0).array())
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
            waylandPointerPositions[id] = localX to localY
            wayland?.pointer(id and WAYLAND_ID.inv(), action, localX, localY, button)
            return
        }
        if (xClient == null) return
        if (action == 1) waylandFocused = false
        val window = windows[id] ?: return
        input.pointer(action, window.bounds.left + localX, window.bounds.top + localY, button)
    }

    fun scroll(dx: Int, dy: Int) { wayland?.takeIf { waylandFocused }?.scroll(dx, dy) ?: xClient?.let { input.scroll(dx, dy) } }
    fun key(evdevCode: Int, down: Boolean) { wayland?.takeIf { waylandFocused }?.key(evdevCode, down) ?: xClient?.let { input.key(evdevCode, down) } }
    fun text(text: String) { wayland?.takeIf { waylandFocused }?.text(text) ?: xClient?.let { input.text(text) } }

    /** Map events for every window, for a Plasma session that (re)connects. */
    fun updateDisplay(config: LinuxDisplayConfiguration) {
        xwaylandHost?.updateDisplay(config)
        screenWidth = config.width; screenHeight = config.height
        displayConfiguration = config; frameIntervalNanos = config.frameIntervalNanos
        xClient?.updateScreenSize(config.width, config.height)
        wayland?.updateOutput(config.width, config.height, config.refreshMilliHz)
    }

    fun trayAction(key: String, button: Int, x: Int, y: Int) {
        if (key.startsWith("x11:")) key.substringAfter(":").toIntOrNull()?.let { tray?.click(it, button, x, y) }
        else statusTray?.activate(key, button, x, y)
    }

    fun replay() {
        tray?.replay()
        statusTray?.replay()
        waylandWindows.values.toList().forEach { events(JSONObject(it).put("type", "map")) }
        windows.values.forEach { window ->
            val b = window.bounds
            events(JSONObject()
                .put("type", "map").put("xid", window.id).put("popup", window.popup)
                .put("title", window.title).put("class", window.wmClass).put("transientFor", 0)
                .put("decorated", window.decorated)
                .put("icon", window.icon)
                .put("fullscreen", !window.popup && atomFullscreen in x.propertyAtoms(window.id, atomNetState))
                .put("x", b.left).put("y", b.top).put("w", b.width()).put("h", b.height()))
        }
    }
    // endregion

    fun stop() {
        if (!running) return
        running = false
        pointerGrabs?.stop()
        synchronized(renderLock) {
            windows.values.forEach { if (it.surfaceHandle != 0L) LinuxFrameBridge.nativeDetach(it.surfaceHandle); it.surfaceHandle = 0L }
            renderLock.notifyAll()
        }
        queue.offer(-1 to ByteBuffer.allocate(0))
        wayland?.stop()
        waylandWindows.clear()
        waylandBounds.clear()
        waylandPointerPositions.clear()
        tray?.stop()
        statusTray?.stop()
        clipboard?.stop()
        xClient?.close()
        LinuxRuntime.terminate(process)
        xwaylandHost?.stop(); xwaylandHost = null
    }
}
