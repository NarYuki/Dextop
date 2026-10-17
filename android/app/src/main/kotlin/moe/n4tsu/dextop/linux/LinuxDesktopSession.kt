package moe.n4tsu.dextop.linux

import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import java.io.File

/**
 * One running X display of a container: Xvfb plus the session (IceWM in
 * desktop mode), the X connection used for input, and the renderer that
 * copies damaged regions of the framebuffer into the attached Surface.
 */
internal class LinuxDesktopSession(
    val containerId: String,
    val display: Int,
    private val process: Process,
    private val x: X11Client,
    private val framebuffer: XvfbFramebuffer,
) {
    companion object {
        private const val TAG = "DextopLinuxDesktop"
        private const val FRAME_INTERVAL_MS = 16L
        /** Area around the pointer redrawn on motion; covers large (HiDPI) cursor themes. */
        private const val CURSOR_EXTENT = 96
        /** Frames to keep redrawing the cursor area after a move, in case Xvfb draws it late. */
        private const val CURSOR_FRAMES = 2
        /** Give up waiting for the window manager after this long and show the screen anyway. */
        private const val WM_TIMEOUT_MS = 30_000L

        /** Launches Xvfb + IceWM through [proot] and connects once the X socket is up. */
        fun start(proot: LinuxProot, containerId: String, display: Int, width: Int, height: Int, dpi: Int): LinuxDesktopSession {
            val fbFile = File(LinuxPaths.rootfs(containerId), "tmp/dextop-fb-$display/Xvfb_screen0")
            fbFile.delete()
            val process = proot.guest(containerId, LinuxCatalog.desktopScript(display, width, height, dpi, "icewm"))
                .redirectErrorStream(true).start()
            process.outputStream.close()
            Thread {
                process.inputStream.bufferedReader().forEachLine { Log.i(TAG, "[$display] $it") }
            }.apply { isDaemon = true; start() }
            val deadline = SystemClock.uptimeMillis() + 15_000
            var client: X11Client? = null
            var framebuffer: XvfbFramebuffer? = null
            while (SystemClock.uptimeMillis() < deadline && (client == null || framebuffer == null)) {
                if (!process.isAlive) {
                    client?.close()
                    error("Xvfb/session exited (exit ${process.exitValue()})")
                }
                if (framebuffer == null) framebuffer = runCatching { XvfbFramebuffer.open(fbFile) }.getOrNull()
                if (client == null) client = runCatching { X11Client.connect(display) }.getOrNull()
                if (client == null || framebuffer == null) SystemClock.sleep(150)
            }
            if (client == null || framebuffer == null) {
                client?.close()
                process.destroyForcibly()
                error("X display :$display did not start")
            }
            return LinuxDesktopSession(containerId, display, process, client, framebuffer).also { it.begin() }
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

    val width get() = framebuffer.width
    val height get() = framebuffer.height
    val alive get() = running && process.isAlive
    /** True once a window manager owns the ICCCM WM_S0 selection (or the wait timed out). */
    @Volatile var windowManagerReady = false; private set

    private fun begin() {
        if (x.damageOpcode >= 0) {
            damage = x.damageCreate(x.root)
            x.onEvent = { code, event ->
                if (code == x.damageEventBase) {
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
        renderer = Thread(::renderLoop, "DextopLinuxRender-$display").apply { isDaemon = true; start() }
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
        markDirty(Rect(0, 0, framebuffer.width, framebuffer.height))
    }

    private fun markDirty(area: Rect) = synchronized(lock) {
        if (dirty.isEmpty) dirty.set(area) else dirty.union(area)
        lock.notifyAll()
    }

    private fun renderLoop() {
        val region = Rect()
        while (running) {
            synchronized(lock) {
                // Without DAMAGE, poll the whole screen at the frame rate.
                if (dirty.isEmpty && cursorFrames == 0 && damage != 0) lock.wait(250)
                if (damage == 0) dirty.set(0, 0, framebuffer.width, framebuffer.height)
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
                        framebuffer.width, framebuffer.height, region.left, region.top, region.width(), region.height(),
                    )
                }
                if (!ok) detachSurface()
            }
            SystemClock.sleep(FRAME_INTERVAL_MS)
        }
    }

    fun attachSurface(surface: Surface): Boolean {
        detachSurface()
        val handle = LinuxFrameBridge.nativeAttach(surface, framebuffer.width, framebuffer.height)
        if (handle == 0L) return false
        synchronized(surfaceLock) { surfaceHandle = handle }
        markDirty(Rect(0, 0, framebuffer.width, framebuffer.height))
        return true
    }

    fun detachSurface() {
        synchronized(surfaceLock) {
            val handle = surfaceHandle
            surfaceHandle = 0L
            if (handle != 0L) LinuxFrameBridge.nativeDetach(handle)
        }
    }

    // region input
    fun pointer(action: Int, px: Int, py: Int, button: Int) {
        val cx = px.coerceIn(0, framebuffer.width - 1)
        val cy = py.coerceIn(0, framebuffer.height - 1)
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
        running = false
        synchronized(lock) { lock.notifyAll() }
        detachSurface()
        x.close()
        process.destroyForcibly()
    }
}
