package moe.n4tsu.dextop.linux

import java.util.ArrayDeque
import java.util.concurrent.Executor
import kotlin.test.Test
import kotlin.test.assertEquals

class LinuxContainerSizesTest {
    private class Fixture {
        var time = 0L
        var measured = 200L
        var notifications = 0
        var fail = false
        val tasks = ArrayDeque<Runnable>()
        val cache = LinuxContainerSizes(Executor { tasks.add(it) }, { if (fail) error("failed") else measured }, { notifications++ }, { time })
        fun run() = tasks.removeFirst().run()
    }
    @Test fun listingReturnsImmediatelyAndCoalescesMeasurements() {
        val f = Fixture()
        repeat(10) { f.cache.request("c1", 100); assertEquals(100, f.cache.read("c1", 100)) }
        assertEquals(1, f.tasks.size)
        f.run()
        assertEquals(200, f.cache.read("c1", 100))
        assertEquals(1, f.notifications)
        f.time = 5_000
        f.cache.read("c1", 100)
        assertEquals(0, f.tasks.size)
    }
    @Test fun pollingNeverStartsMeasurementsEvenAfterCacheExpires() {
        val f = Fixture()
        repeat(10) { f.cache.read("c1", 100); f.time += 60_000 }
        assertEquals(0, f.tasks.size)
        f.cache.request("c1", 100); f.run()
        f.time += 24 * 60 * 60_000
        repeat(100) { assertEquals(200, f.cache.read("c1", 100)) }
        assertEquals(0, f.tasks.size)
    }
    @Test fun openingListReusesFiveMinuteCacheAndRefreshesWhenOld() {
        val f = Fixture()
        f.cache.request("c1", 100); f.run()
        f.time = 299_999
        f.cache.request("c1", 100)
        assertEquals(0, f.tasks.size)
        f.time = 300_000
        f.measured = 350
        f.cache.request("c1", 100); f.run()
        assertEquals(350, f.cache.read("c1", 100))
    }
    @Test fun commandCompletionRefreshesSoonerButLimitsRepeatedScans() {
        val f = Fixture()
        f.cache.request("c1", 100); f.run()
        f.measured = 150
        f.cache.invalidate("c1")
        f.time = 4_999; f.cache.read("c1", 100)
        assertEquals(0, f.tasks.size)
        f.time = 5_000; f.cache.request("c1", 100); f.run()
        assertEquals(150, f.cache.read("c1", 100))
    }
    @Test fun aChangeDuringMeasurementIsNotForgotten() {
        val f = Fixture()
        f.cache.request("c1", 100)
        f.cache.invalidate("c1")
        f.run()
        f.time = 5_000; f.cache.read("c1", 100)
        assertEquals(1, f.tasks.size)
    }
    @Test fun failedMeasurementKeepsLastGoodValue() {
        val f = Fixture()
        f.cache.request("c1", 100); f.run()
        f.fail = true; f.time = 300_000
        f.cache.request("c1", 100); f.run()
        assertEquals(200, f.cache.read("c1", 100))
        assertEquals(1, f.notifications)
        f.time += 60_000
        f.cache.read("c1", 100)
        assertEquals(0, f.tasks.size)
    }
    @Test fun deletedContainersAndClosedServicesIgnoreLateResults() {
        val f = Fixture()
        f.cache.request("c1", 100); f.cache.forget("c1"); f.run()
        assertEquals(0, f.notifications)
        f.cache.request("c1", 100); f.cache.close(); f.run()
        assertEquals(0, f.notifications)
        assertEquals(100, f.cache.read("c1", 100))
        assertEquals(0, f.tasks.size)
    }
}
