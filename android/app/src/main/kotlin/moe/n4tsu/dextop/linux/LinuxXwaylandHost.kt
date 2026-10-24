package moe.n4tsu.dextop.linux

import android.view.Surface
import java.io.File

/** A rootful Xwayland output. App windows remain managed individually by Dextop's X WM. */
internal class LinuxXwaylandHost private constructor(
    containerId: String, val display: Int, width: Int, height: Int,
) {
    companion object {
        fun displayName(display: Int) = "wayland-x11-$display"
        fun start(containerId: String, display: Int, width: Int, height: Int): LinuxXwaylandHost =
            LinuxXwaylandHost(containerId, display, width, height).also {
                check(it.compositor.running) { it.stop(); "Xwayland host compositor did not start" }
            }
    }

    private val lock = Any()
    private var windowId = 0
    private var surface: Surface? = null
    private var stopped = false
    private var configuration: LinuxDisplayConfiguration? = null
    private val compositor: WaylandCompositor = WaylandCompositor(
        LinuxProot.socketNamespace(containerId) + "/tmp/runtime-root/" + displayName(display), width, height,
        File(LinuxPaths.rootfs(containerId), "tmp/runtime-root/${displayName(display)}.lock"),
    ) { event ->
        synchronized(lock) {
            if (!stopped) when (event.optString("type")) {
                "map" -> {
                    windowId = event.optInt("id")
                    configuration?.let { compositorConfigure(windowId, it) }
                    surface?.let { attachWindow(windowId, it) }
                }
                "unmap" -> if (windowId == event.optInt("id")) windowId = 0
            }
        }
    }

    // Methods avoid referencing the property from inside its initializer.
    private fun attachWindow(id: Int, target: Surface): Unit = compositor.attach(id, target)
    private fun compositorConfigure(id: Int, config: LinuxDisplayConfiguration): Unit = compositor.configure(id, config.width, config.height)

    fun attachSurface(target: Surface): Boolean = synchronized(lock) {
        if (stopped || !compositor.running) return false
        surface = target
        if (windowId != 0) compositor.attach(windowId, target)
        true
    }
    fun detachSurface() = synchronized(lock) {
        surface = null
        if (windowId != 0) compositor.detach(windowId)
    }
    fun updateDisplay(config: LinuxDisplayConfiguration) = synchronized(lock) {
        configuration = config
        compositor.updateOutput(config.width, config.height, config.refreshMilliHz)
        if (windowId != 0) compositorConfigure(windowId, config)
    }
    fun stop() {
        synchronized(lock) { stopped = true; surface = null; windowId = 0 }
        compositor.stop()
    }
}
