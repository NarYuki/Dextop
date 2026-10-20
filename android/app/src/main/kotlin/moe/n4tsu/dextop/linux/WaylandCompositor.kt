package moe.n4tsu.dextop.linux

import android.util.Log
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
internal class WaylandCompositor(socketName: String, width: Int, height: Int, private val onEvent: (JSONObject) -> Unit) {
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
        @JvmStatic private external fun nativeAttach(handle: Long, id: Int, surface: Surface)
        @JvmStatic private external fun nativeDetach(handle: Long, id: Int)
        @JvmStatic private external fun nativeConfigure(handle: Long, id: Int, width: Int, height: Int)
        @JvmStatic private external fun nativeActivate(handle: Long, id: Int)
        @JvmStatic private external fun nativeClose(handle: Long, id: Int)
        @JvmStatic private external fun nativePointer(handle: Long, id: Int, action: Int, x: Int, y: Int, button: Int)
        @JvmStatic private external fun nativeAxis(handle: Long, dx: Int, dy: Int)
        @JvmStatic private external fun nativeKey(handle: Long, key: Int, down: Boolean)
        @JvmStatic private external fun nativeText(handle: Long, text: String)
    }

    /** Called by the native compositor thread. */
    @Keep
    inner class Callback {
        @Keep
        fun onEvent(json: String) {
            runCatching { onEvent(JSONObject(json)) }.onFailure { Log.w(TAG, "window event failed: $json", it) }
        }
    }

    private var handle = nativeStart(socketName, width, height, Callback())

    @get:Synchronized
    val running get() = handle != 0L

    @Synchronized
    fun attach(id: Int, surface: Surface) { if (running) nativeAttach(handle, id, surface) }
    @Synchronized
    fun detach(id: Int) { if (running) nativeDetach(handle, id) }
    @Synchronized
    fun configure(id: Int, width: Int, height: Int) { if (running) nativeConfigure(handle, id, width, height) }
    @Synchronized
    fun activate(id: Int) { if (running) nativeActivate(handle, id) }
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
    @Synchronized
    fun stop() {
        val current = handle
        handle = 0L
        if (current != 0L) nativeStop(current)
    }
}
