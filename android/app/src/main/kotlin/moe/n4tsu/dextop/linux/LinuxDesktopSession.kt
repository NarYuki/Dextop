package moe.n4tsu.dextop.linux

import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import java.io.File

/**
 * An IceWM desktop on either Xvfb or a rootful Xwayland Wayland output.
 * Xvfb copies damaged framebuffer regions; Xwayland presents through its host
 * compositor. Both use the same X connection for input and clipboard.
 */
internal class LinuxDesktopSession(
    val containerId: String,
    val display: Int,
    private val process: Process,
    private val x: X11Client,
    private val framebuffer: XvfbFramebuffer?,
    private val xwaylandHost: LinuxXwaylandHost? = null,
    initialWidth: Int = framebuffer?.width ?: 640,
    initialHeight: Int = framebuffer?.height ?: 480,
) {
    companion object {
        private const val TAG = "DextopLinuxDesktop"
        /** Area around the pointer redrawn on motion; covers large (HiDPI) cursor themes. */
        private const val CURSOR_EXTENT = 96
        /** Frames to keep redrawing the cursor area after a move, in case Xvfb draws it late. */
        private const val CURSOR_FRAMES = 2
        /** Give up waiting for the window manager after this long and show the screen anyway. */
        private const val WM_TIMEOUT_MS = 30_000L

        /** Launches the selected X server and IceWM, then connects once the X socket is up. */
        fun start(proot: LinuxProot, containerId: String, display: Int, width: Int, height: Int, dpi: Int, xwayland: Boolean = false): LinuxDesktopSession {
            val host = if (xwayland) LinuxXwaylandHost.start(containerId, display, width, height) else null
            var startedProcess: Process? = null
            try {
                val fbFile = File(LinuxPaths.rootfs(containerId), "tmp/dextop-fb-$display/Xvfb_screen0")
                fbFile.delete()
                val process = proot.guest(containerId, LinuxCatalog.desktopScript(display, width, height, dpi, "icewm", xwayland))
                    .redirectErrorStream(true).start()
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
                val deadline = SystemClock.uptimeMillis() + 15_000
                var client: X11Client? = null
                var framebuffer: XvfbFramebuffer? = null
                while (SystemClock.uptimeMillis() < deadline && (client == null || (!xwayland && framebuffer == null) || !startupReady.get())) {
                    if (!process.isAlive) {
                        client?.close()
                        error("X server/session exited (exit ${process.exitValue()})")
                    }
                    if (!xwayland && framebuffer == null) framebuffer = runCatching { XvfbFramebuffer.open(fbFile) }.getOrNull()
                    if (client == null) client = runCatching { X11Client.connect(display) }.getOrNull()
                    if (client == null || (!xwayland && framebuffer == null) || !startupReady.get()) SystemClock.sleep(150)
                }
                if (client == null || (!xwayland && framebuffer == null) || !startupReady.get()) {
                    client?.close()
                    LinuxRuntime.terminate(process)
                    error("X display :$display did not start")
                }
                return LinuxDesktopSession(containerId, display, process, client, framebuffer, host, if (xwayland) width else framebuffer!!.width, if (xwayland) height else framebuffer!!.height).also {
                    it.viewportWidth = width; it.viewportHeight = height; it.begin()
                }
            } catch (error: Throwable) {
                startedProcess?.let(LinuxRuntime::terminate)
                host?.stop()
                throw error
            }
        }
    }

    private val lock = Object()
    /** Guards [surfaceHandle] for the whole of a blit, so detach cannot free it mid-copy. */
    private val surfaceLock = Any()
    private val dirty = Rect()
    private var surfaceHandle = 0L
    @Volatile private var running = true
    private var damage = 0
    private val input = X11Input(x)
    private var renderer: Thread? = null
    // Xvfb draws the cursor into the framebuffer without reporting DAMAGE,
    // so the area around the old and new pointer positions is redrawn here.
    private val cursorArea = Rect()
    private var cursorFrames = 0
    private var pointerX = 0
    private var pointerY = 0

    @Volatile private var viewportWidth = initialWidth
    @Volatile private var viewportHeight = initialHeight
    @Volatile private var frameIntervalNanos = 16_666_667L
    @Volatile var displayConfiguration: LinuxDisplayConfiguration? = null; private set
    val usesXwayland get() = xwaylandHost != null
    val width get() = viewportWidth
    val height get() = viewportHeight
    val alive get() = running && process.isAlive
    /** True once a window manager owns the ICCCM WM_S0 selection (or the wait timed out). */
    @Volatile var windowManagerReady = false; private set


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
        if (x.damageOpcode >= 0) damage = x.damageCreate(x.root)
        x.onEvent = { code, event ->
            if (clipboard?.offer(code, event) != true) {
                if (damage != 0 && code == x.damageEventBase) {
                    val area = Rect(
                        event.getShort(16).toInt(), event.getShort(18).toInt(),
                        event.getShort(16).toInt() + (event.getShort(20).toInt() and 0xffff),
                        event.getShort(18).toInt() + (event.getShort(22).toInt() and 0xffff),
                    )
                    markDirty(area)
                }
            }
        }
        x.onClosed = { stop() }
        if (framebuffer != null) renderer = Thread(::renderLoop, "DextopLinuxRender-$display").apply { isDaemon = true; start() }
        Thread(::awaitWindowManager, "DextopLinuxWm-$display").apply { isDaemon = true; start() }
    }

    private fun awaitWindowManager() {
        val deadline = SystemClock.uptimeMillis() + WM_TIMEOUT_MS
        runCatching {
            val selection = x.internAtom("WM_S0")
            while (running && SystemClock.uptimeMillis() < deadline && x.selectionOwner(selection) == 0) {
                SystemClock.sleep(200)
            }
        }.onFailure { Log.w(TAG, "window manager check failed", it) }
        if (!running) return
        val ready = runCatching { x.selectionOwner(x.internAtom("WM_S0")) != 0 }.getOrDefault(false)
        if (!ready) {
            Log.e(TAG, "[$display] IceWM did not become ready within 30 seconds")
            runCatching { File(LinuxPaths.rootfs(containerId), "tmp/session-$display.log").appendText("\nIceWM startup timed out after 30 seconds\n") }
            stop()
            return
        }
        Log.i(TAG, "[$display] window manager ready")
        windowManagerReady = true
        markDirty(Rect(0, 0, viewportWidth, viewportHeight))
    }

    private fun markDirty(area: Rect) = synchronized(lock) {
        if (dirty.isEmpty) dirty.set(area) else dirty.union(area)
        lock.notifyAll()
    }

    private fun renderLoop() {
        val framebuffer = framebuffer ?: return
        val region = Rect()
        var nextFrame = System.nanoTime()
        var failures = 0
        while (running) {
            synchronized(lock) {
                // Without DAMAGE, poll the whole screen at the frame rate.
                if (dirty.isEmpty && cursorFrames == 0 && damage != 0) lock.wait(250)
                if (damage == 0) dirty.set(0, 0, viewportWidth, viewportHeight)
                if (cursorFrames > 0) {
                    if (dirty.isEmpty) dirty.set(cursorArea) else dirty.union(cursorArea)
                    if (--cursorFrames == 0) cursorArea.setEmpty()
                }
                region.set(dirty)
                dirty.setEmpty()
            }
            if (!region.isEmpty) {
                val ok = synchronized(surfaceLock) {
                    val handle = surfaceHandle
                    handle == 0L || LinuxFrameBridge.nativeBlit(
                        handle, framebuffer.buffer, framebuffer.pixelOffset, framebuffer.stride,
                        viewportWidth, viewportHeight, region.left, region.top, region.width(), region.height(),
                    )
                }
                if (!ok && running) {
                    failures = (failures + 1).coerceAtMost(6)
                    markDirty(region)
                    if (failures == 1) Log.w(TAG, "[$display] Surface write failed; scheduling redraw")
                    java.util.concurrent.locks.LockSupport.parkNanos(minOf(1_000L, 16L shl failures) * 1_000_000L)
                } else failures = 0
            }
            nextFrame = maxOf(nextFrame + frameIntervalNanos, System.nanoTime())
            java.util.concurrent.locks.LockSupport.parkNanos((nextFrame - System.nanoTime()).coerceAtLeast(0))
        }
    }

    fun updateDisplay(config: LinuxDisplayConfiguration) {
        xwaylandHost?.updateDisplay(config)
        require(framebuffer == null || config.width <= framebuffer.width && config.height <= framebuffer.height) { "Xvfb framebuffer is too small; restart this Linux desktop" }
        synchronized(surfaceLock) {
            viewportWidth = config.width; viewportHeight = config.height
            frameIntervalNanos = config.frameIntervalNanos; displayConfiguration = config
            x.updateScreenSize(config.width, config.height)
            if (surfaceHandle != 0L) LinuxFrameBridge.nativeResize(surfaceHandle, config.width, config.height)
        }
        markDirty(Rect(0, 0, config.width, config.height))
    }

    fun attachSurface(surface: Surface): Boolean {
        xwaylandHost?.let { return it.attachSurface(surface) }
        detachSurface()
        val handle = LinuxFrameBridge.nativeAttach(surface, viewportWidth, viewportHeight)
        if (handle == 0L) return false
        synchronized(surfaceLock) { surfaceHandle = handle }
        markDirty(Rect(0, 0, viewportWidth, viewportHeight))
        return true
    }

    fun detachSurface() {
        xwaylandHost?.detachSurface()
        synchronized(surfaceLock) {
            val handle = surfaceHandle
            surfaceHandle = 0L
            if (handle != 0L) LinuxFrameBridge.nativeDetach(handle)
        }
    }

    private val pointerGrabs = LinuxPointerGrabs(display)
    fun pointerCaptureRequested(): Boolean {
        x.sync()
        return pointerGrabs.requested
    }
    fun relativePointer(action: Int, dx: Int, dy: Int, button: Int) {
        if (dx != 0 || dy != 0) x.relativeMotion(dx, dy)
        if (action == 1 || action == 2) x.fakeInput(if (action == 1) X11Client.BUTTON_PRESS else X11Client.BUTTON_RELEASE, button)
    }
    // region input
    fun pointer(action: Int, px: Int, py: Int, button: Int) {
        val cx = px.coerceIn(0, viewportWidth - 1)
        val cy = py.coerceIn(0, viewportHeight - 1)
        input.pointer(action, cx, cy, button)
        synchronized(lock) {
            val area = Rect(pointerX - CURSOR_EXTENT, pointerY - CURSOR_EXTENT, pointerX + CURSOR_EXTENT, pointerY + CURSOR_EXTENT)
            area.union(cx - CURSOR_EXTENT, cy - CURSOR_EXTENT, cx + CURSOR_EXTENT, cy + CURSOR_EXTENT)
            if (cursorArea.isEmpty) cursorArea.set(area) else cursorArea.union(area)
            cursorFrames = CURSOR_FRAMES
            pointerX = cx
            pointerY = cy
            lock.notifyAll()
        }
    }

    fun scroll(dx: Int, dy: Int) = input.scroll(dx, dy)

    fun key(evdevCode: Int, down: Boolean) = input.key(evdevCode, down)

    fun text(text: String) = input.text(text)
    // endregion

    fun stop() {
        if (!running) return
        pointerGrabs.stop()
        running = false
        synchronized(lock) { lock.notifyAll() }
        detachSurface()
        clipboard?.stop()
        x.close()
        LinuxRuntime.terminate(process)
        xwaylandHost?.stop()
    }
}
