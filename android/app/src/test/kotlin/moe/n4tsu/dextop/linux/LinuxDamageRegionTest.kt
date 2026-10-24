package moe.n4tsu.dextop.linux

import kotlin.test.*

class LinuxDamageRegionTest {
    @Test fun distantChangesDoNotFetchPixelsBetweenThem() {
        val dirty = LinuxDamageRegion()
        dirty.add(0, 0, 10, 10); dirty.add(1000, 1000, 1010, 1010)
        val areas = dirty.drain()
        assertEquals(2, areas.size); assertEquals(200, areas.sumOf { it.size })
        assertTrue(dirty.isEmpty)
    }
    @Test fun overlappingAndExcessiveDamageRemainBoundedAndCovered() {
        val dirty = LinuxDamageRegion()
        dirty.add(0, 0, 10, 10); dirty.add(5, 0, 15, 10)
        assertEquals(listOf(LinuxDamageRegion.Area(0, 0, 15, 10)), dirty.drain())
        repeat(100) { dirty.add(it * 100, it * 100, it * 100 + 10, it * 100 + 10) }
        val areas = dirty.drain(); assertTrue(areas.size <= 8)
        repeat(100) { i -> assertTrue(areas.any { it.left <= i * 100 && it.top <= i * 100 && it.right >= i * 100 + 10 && it.bottom >= i * 100 + 10 }) }
    }
}
