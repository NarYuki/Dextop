package moe.n4tsu.dextop.linux

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LinuxAppContainersTest {
    @Test fun preferredContainerTakesPriorityOverListOrder() {
        assertEquals("c2", LinuxAppContainers.select(listOf("c1", "c2", "c3"), "c2"))
    }
    @Test fun missingAndRemovedPreferencesChooseOnlyTheFirstInstalledContainer() {
        assertEquals("c1", LinuxAppContainers.select(listOf("c1", "c2"), ""))
        assertEquals("c1", LinuxAppContainers.select(listOf("c1", "c2"), "removed"))
        assertNull(LinuxAppContainers.select(emptyList(), "c2"))
    }
}
