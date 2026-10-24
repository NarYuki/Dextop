package moe.n4tsu.dextop.linux

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LinuxWindowHintsTest {
    @Test fun undecoratedClientDoesNotGetASecondTitleBar() {
        assertFalse(LinuxWindowHints.serverDecorated(intArrayOf(2, 0, 0, 0, 0), intArrayOf()))
        assertFalse(LinuxWindowHints.serverDecorated(intArrayOf(), intArrayOf(10, 10, 10, 10)))
    }
    @Test fun ordinaryAndMalformedClientsKeepTheirControls() {
        assertTrue(LinuxWindowHints.serverDecorated(intArrayOf(), intArrayOf()))
        assertTrue(LinuxWindowHints.serverDecorated(intArrayOf(2), intArrayOf(1)))
        assertTrue(LinuxWindowHints.serverDecorated(intArrayOf(0, 0, 0), intArrayOf()))
        assertTrue(LinuxWindowHints.serverDecorated(intArrayOf(2, 0, 8), intArrayOf(0, 0, 0, 0)))
    }
    @Test fun motifAllTreatsOtherBitsAsExclusions() {
        assertTrue(LinuxWindowHints.serverDecorated(intArrayOf(2, 0, 1), intArrayOf()))
        assertFalse(LinuxWindowHints.serverDecorated(intArrayOf(2, 0, 1 or 8), intArrayOf()))
    }
}
