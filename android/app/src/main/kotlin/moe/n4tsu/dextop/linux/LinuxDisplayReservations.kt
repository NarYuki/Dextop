package moe.n4tsu.dextop.linux

/** Includes sessions still starting and short-lived GPU probes, not just running sessions. */
internal class LinuxDisplayReservations {
    private val pending = HashSet<Int>()

    @Synchronized fun reserve(used: Set<Int>, sockets: String? = null): Int {
        val display = if (sockets == null) LinuxRuntime.freeDisplay(used + pending)
            else LinuxRuntime.freeDisplay(used + pending, sockets)
        pending.add(display)
        return display
    }

    @Synchronized fun release(display: Int) { pending.remove(display) }
}
