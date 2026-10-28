package moe.n4tsu.dextop.linux

import android.util.Log
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.Surface
import androidx.annotation.Keep
import org.json.JSONObject
import java.io.File

/**
 * Kotlin side of Dextop's Wayland compositor (cpp/wayland/compositor.c),
 * listening on the abstract socket [socketName]. [onEvent] receives window
 * events as JSON on the compositor thread: {"type": "map" | "configure" |
 * "title" | "unmap" | "move" | "minimize", "id", "popup", "parent", "x", "y",
 * "w", "h", "title", "class"}.
 */
internal class WaylandCompositor(
    socketName: String, width: Int, height: Int,
    /** The guest's wayland-0.lock, held like libwayland does so guest compositors take another name. */
    lockFile: File? = null,
    private val onEvent: (JSONObject) -> Unit,
) {
    companion object {
        private const val TAG = "DextopWayland"
        /** Mesa Turnip for Android, shipped in the APK's native libraries. */
        const val GPU_DRIVER = "libvulkan_dextop_turnip.so"

        init {
            val bridge = System.getProperty(LinuxServiceHost.LIBRARY_PROPERTY)
            if (bridge != null) System.load(File(File(bridge).parentFile, "libdextop_wayland.so").absolutePath)
            else System.loadLibrary("dextop_wayland")
        }

        @JvmStatic private external fun nativeStart(socketName: String, width: Int, height: Int, callback: Callback): Long
        @JvmStatic private external fun nativeStop(handle: Long)
        @JvmStatic private external fun nativeAttach(handle: Long, id: Int, surface: Surface, deckDirect: Boolean, deck30: Boolean, noVsync: Boolean)
        @JvmStatic private external fun nativeDetach(handle: Long, id: Int)
        @JvmStatic private external fun nativeOutput(handle: Long, width: Int, height: Int, refreshMilliHz: Int)
        @JvmStatic private external fun nativeDeckVsync(handle: Long, frameTimeNanos: Long)
        @JvmStatic private external fun nativeConfigure(handle: Long, id: Int, width: Int, height: Int)
        @JvmStatic private external fun nativeActivate(handle: Long, id: Int)
        @JvmStatic private external fun nativeSetState(handle: Long, id: Int, maximized: Boolean)
        @JvmStatic private external fun nativeClose(handle: Long, id: Int)
        @JvmStatic private external fun nativePointer(handle: Long, id: Int, action: Int, x: Int, y: Int, button: Int)
        @JvmStatic private external fun nativeAxis(handle: Long, dx: Int, dy: Int)
        @JvmStatic private external fun nativeKey(handle: Long, key: Int, down: Boolean)
        @JvmStatic private external fun nativeText(handle: Long, text: String)
        @JvmStatic private external fun nativeClipboard(handle: Long, text: String)
        @JvmStatic private external fun nativeLockName(path: String): Int
        @JvmStatic private external fun nativeUnlockName(fd: Int)
    }

    /** Called by the native compositor thread. */
    @Keep
    inner class Callback {
        @Keep
        fun onEvent(json: String) {
            runCatching {
                val event = JSONObject(json)
                if (event.optBoolean("deck") || event.optString("type") == "unmap") {
                    frameHandler?.post {
                        val id = event.optInt("id")
                        if (event.optString("type") == "unmap") deckWindows.remove(id)
                        else if (event.optString("type") == "map") deckWindows.add(id)
                        if (deckWindows.isNotEmpty() && !frameScheduled && running) {
                            Log.i(TAG, "Deck VSync started")
                            frameScheduled = true
                            Choreographer.getInstance().postFrameCallback(deckFrame)
                        }
                    }
                }
                onEvent(event)
            }.onFailure { Log.w(TAG, "window event failed: $json", it) }
        }
    }

    private val frameHandler = Looper.getMainLooper()?.let { Handler(it) }
    private val deckWindows = mutableSetOf<Int>()
    private var frameScheduled = false
    private val deckFrame = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            frameScheduled = false
            synchronized(this@WaylandCompositor) {
                if (!running || deckWindows.isEmpty()) return
                nativeDeckVsync(handle, frameTimeNanos)
                frameScheduled = true
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
    }

    private var handle = nativeStart(socketName, width, height, Callback())

    private var lockFd = lockFile?.let { file ->
        file.parentFile?.mkdirs()
        nativeLockName(file.path).also { if (it < 0) Log.w(TAG, "could not lock ${file.path} (errno ${-it})") }
    } ?: -1

    @get:Synchronized
    val running get() = handle != 0L

    /**
     * Surfaces of attached windows, released on detach: an unreleased Surface keeps its
     * producer connected, and the same Android surface attached again would be refused.
     */
    private val surfaces = java.util.concurrent.ConcurrentHashMap<Int, Surface>()

    @Synchronized
    fun attach(id: Int, surface: Surface, deckDirect: Boolean = false, deck30: Boolean = false, noVsync: Boolean = false) {
        if (!running) return
        nativeAttach(handle, id, surface, deckDirect, deck30, noVsync)
        surfaces.put(id, surface)?.takeIf { it !== surface }?.release()
    }
    @Synchronized
    fun detach(id: Int) {
        if (running) nativeDetach(handle, id)
        surfaces.remove(id)?.release()
    }
    @Synchronized
    fun updateOutput(width: Int, height: Int, refreshMilliHz: Int) { if (running) nativeOutput(handle, width, height, refreshMilliHz) }
    @Synchronized
    fun configure(id: Int, width: Int, height: Int) { if (running) nativeConfigure(handle, id, width, height) }
    @Synchronized
    fun activate(id: Int) { if (running) nativeActivate(handle, id) }
    @Synchronized
    fun setState(id: Int, maximized: Boolean) { if (running) nativeSetState(handle, id, maximized) }
    @Synchronized
    fun close(id: Int) { if (running) nativeClose(handle, id) }
    @Synchronized
    fun pointer(id: Int, action: Int, x: Int, y: Int, button: Int) { if (running) nativePointer(handle, id, action, x, y, button) }
    @Synchronized
    fun scroll(dx: Int, dy: Int) { if (running) nativeAxis(handle, dx, dy) }
    @Synchronized
    fun key(evdevCode: Int, down: Boolean) { if (running) nativeKey(handle, evdevCode, down) }
    @Synchronized
    fun text(text: String) { if (running) nativeText(handle, text) }

    /** Offers [text] (copied in an X application or Android) as Wayland clients' clipboard. */
    fun setClipboard(text: String) { if (running) nativeClipboard(handle, text) }
    @Synchronized
    fun stop() {
        val current = handle
        handle = 0L
        frameHandler?.post {
            deckWindows.clear()
            frameScheduled = false
            Choreographer.getInstance().removeFrameCallback(deckFrame)
        }
        if (current != 0L) nativeStop(current)
        if (lockFd >= 0) nativeUnlockName(lockFd)
        lockFd = -1
    }
}
