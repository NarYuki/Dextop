package moe.n4tsu.dextop.linux

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class LinuxDisplayReservationsTest {
    @Test fun concurrentProbesAndSessionsCannotReserveTheSameDisplay() {
        val reservations = LinuxDisplayReservations()
        val pool = Executors.newFixedThreadPool(8)
        try {
            val displays = pool.invokeAll((1..32).map {
                Callable { reservations.reserve(setOf(10), "") }
            }).map { it.get() }
            assertEquals(32, displays.toSet().size)
            displays.forEach { assertNotEquals(10, it) }
            reservations.release(displays.min())
            assertEquals(displays.min(), reservations.reserve(setOf(10), ""))
        } finally { pool.shutdownNow() }
    }

    @Test fun skipsDisplaysHeldWhereSocketListsCannotShowThem() {
        assertEquals(12, LinuxRuntime.freeDisplay(emptySet(), "", available = { it >= 12 }))
    }

    @Test fun respectsServersOutsideThisService() {
        val reservations = LinuxDisplayReservations()
        assertEquals(12, reservations.reserve(setOf(11), "000 @/tmp/.X11-unix/X10"))
    }
}
