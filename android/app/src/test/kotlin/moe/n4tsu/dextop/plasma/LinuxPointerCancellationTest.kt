package moe.n4tsu.dextop.plasma

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class LinuxPointerCancellationTest {
    @Test fun focusLossReleasesAllMouseButtonsOnlyOnce() {
        val state = LinuxPointerState().apply { buttons = 1 or 2 or 4 or 8 or 16 }
        val released = mutableListOf<Int>()
        LinuxPointerForwarder.cancel(state, released::add)
        LinuxPointerForwarder.cancel(state, released::add)
        assertEquals(listOf(1, 2, 3, 8, 9), released)
        assertEquals(0, state.buttons)
    }

    @Test fun focusLossReleasesTouchDragAndCancelsPendingTap() {
        val state = LinuxPointerState().apply { startTouch(10, 20); touchPressed = true }
        val released = mutableListOf<Int>()
        LinuxPointerForwarder.cancel(state, released::add)
        assertEquals(listOf(1), released)
        assertFalse(state.touchPressed)
        assertFalse(state.touchPending)
        state.startTouch(30, 40)
        released.clear()
        LinuxPointerForwarder.cancel(state, released::add)
        assertEquals(emptyList(), released)
    }
}
