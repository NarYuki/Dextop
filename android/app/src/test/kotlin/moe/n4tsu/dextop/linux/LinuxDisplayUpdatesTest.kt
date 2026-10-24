package moe.n4tsu.dextop.linux

import java.util.concurrent.Executor
import kotlin.test.*

class LinuxDisplayUpdatesTest {
    @Test fun coalescesRequestsAndNeverRunsDisplayCommandsOnCaller() {
        val jobs = ArrayList<Runnable>(); val calls = ArrayList<Pair<String, Int>>()
        val updates = LinuxDisplayUpdates(Executor { jobs += it }) { id, _, config -> calls += id to config.width }
        updates.offer("a", true, LinuxDisplayConfiguration.create(800, 600, 160, 60f))
        updates.offer("a", true, LinuxDisplayConfiguration.create(1200, 800, 160, 120f))
        updates.offer("b", false, LinuxDisplayConfiguration.create(600, 800, 160, 60f))
        assertTrue(calls.isEmpty()); assertEquals(1, jobs.size)
        jobs.single().run()
        assertEquals(listOf("a" to 1200, "b" to 600), calls)
    }
    @Test fun updatesArrivingDuringACommandReplacePendingGeometry() {
        val jobs = ArrayList<Runnable>(); val sizes = ArrayList<Int>()
        lateinit var updates: LinuxDisplayUpdates
        updates = LinuxDisplayUpdates(Executor { jobs += it }) { id, apps, config ->
            sizes += config.width
            if (config.width == 800) {
                updates.offer(id, apps, LinuxDisplayConfiguration.create(900, 600, 160, 60f))
                updates.offer(id, apps, LinuxDisplayConfiguration.create(1000, 600, 160, 60f))
            }
        }
        updates.offer("a", true, LinuxDisplayConfiguration.create(800, 600, 160, 60f))
        jobs.single().run(); assertEquals(listOf(800, 1000), sizes)
    }
    @Test fun closeDropsQueuedUpdates() {
        val jobs = ArrayList<Runnable>()
        var called = false
        val updates = LinuxDisplayUpdates(Executor { jobs += it }) { _, _, _ -> called = true }
        updates.offer("a", true, LinuxDisplayConfiguration.create(800, 600, 160, 60f))
        updates.close(); jobs.single().run(); assertFalse(called)
    }
}
