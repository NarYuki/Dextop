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
                check(it.compositor.running) { it.stop(); moe.n4tsu.dextop.localText("uiXwaylandHostCompositorDidNotStart") }
            }
    }

    private val lock = Any()
    private var windowId = 0
    private var surface: Surface? = null
    private var stopped = false
    private var configuration: LinuxDisplayConfiguration? = null
    /** The size until the first [updateDisplay]. */
    private val initialWidth = width
    private val initialHeight = height
    /** Room kept for fixed-size application windows ([ensureInputExtent]); it never sets the size by itself,
     * or a desktop opened large would keep its large X screen in a smaller window (shown scaled down,
     * while input follows the window's size). */
    private var inputWidth = 0
    private var inputHeight = 0
    private val compositor: WaylandCompositor = WaylandCompositor(
        LinuxProot.socketNamespace(containerId) + "/tmp/runtime-root/" + displayName(display), width, height,
        File(LinuxPaths.rootfs(containerId), "tmp/runtime-root/${displayName(display)}.lock"),
    ) { event ->
        synchronized(lock) {
            if (!stopped) when (event.optString("type")) {
                "map" -> {
                    windowId = event.optInt("id")
                    compositorConfigure(windowId, effectiveConfiguration())
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
        applyConfiguration()
    }

    /** Fixed-size clients may be larger than their scaled Android viewport.
     * XTEST is clipped by the X root, so retain room for their original pixels. */
    fun ensureInputExtent(right: Int, bottom: Int) = synchronized(lock) {
        val width = right.coerceIn(1, 7680)
        val height = bottom.coerceIn(1, 7680)
        if (width <= inputWidth && height <= inputHeight) return
        inputWidth = maxOf(inputWidth, width)
        inputHeight = maxOf(inputHeight, height)
        applyConfiguration()
    }

    private fun effectiveConfiguration(): LinuxDisplayConfiguration {
        val config = configuration ?: LinuxDisplayConfiguration.create(initialWidth, initialHeight, 160, 60f)
        return config.copy(width = maxOf(config.width, inputWidth), height = maxOf(config.height, inputHeight))
    }

    private fun applyConfiguration() {
        val config = effectiveConfiguration()
        compositor.updateOutput(config.width, config.height, config.refreshMilliHz)
        if (windowId != 0) compositorConfigure(windowId, config)
    }
    fun stop() {
        synchronized(lock) { stopped = true; surface = null; windowId = 0 }
        compositor.stop()
    }
}
