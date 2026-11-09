package moe.n4tsu.dextop.linux

/** The Wayland compositor could not serve a session; its container then switches to Xvfb. */
internal class LinuxWaylandUnavailable(cause: Throwable? = null) : IllegalStateException(
    moe.n4tsu.dextop.localText("uiTheSelectedWaylandCompositorDidNotStartSeeTheLinuxSessionLog"), cause,
)

internal fun startXwaylandHost(containerId: String, display: Int, width: Int, height: Int): LinuxXwaylandHost = try {
    LinuxXwaylandHost.start(containerId, display, width, height)
} catch (error: Exception) {
    throw LinuxWaylandUnavailable(error)
}
