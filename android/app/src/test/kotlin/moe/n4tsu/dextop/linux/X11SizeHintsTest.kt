package moe.n4tsu.dextop.linux

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class X11SizeHintsTest {
    private fun hints() = intArrayOf(48, 0, 0, 0, 0, 179, 255, 179, 255)

    @Test fun fixedSizeRequiresBothPositiveMatchingLimits() {
        assertTrue(X11SizeHints.fixedSize(hints()))
        assertFalse(X11SizeHints.fixedSize(hints().also { it[7] = 500 }))
        assertFalse(X11SizeHints.fixedSize(hints().also { it[6] = 0; it[8] = 0 }))
        assertFalse(X11SizeHints.fixedSize(hints().also { it[0] = 16 }))
        assertFalse(X11SizeHints.fixedSize(hints().also { it[0] = 32 }))
        assertFalse(X11SizeHints.fixedSize(intArrayOf()))
        assertFalse(X11SizeHints.fixedSize(hints().copyOf(8)))
        assertTrue(X11SizeHints.fixedSize(hints().copyOf(18).also { it[0] = 48 or 1 }))
    }
}
