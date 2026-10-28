package moe.n4tsu.dextop.linux

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LinuxPointerGrabTargetTest {
    private val root = 1
    private val game = 10
    private val child = 11
    private val helper = 20
    private val other = 30
    private val parents = mapOf(game to root, child to game, helper to root, other to root)
    private fun matches(grab: Int, focus: Int = game, helperIsInput: Boolean = true) =
        LinuxPointerGrabTarget.matches(grab, game, root, focus, { parents[it] ?: 0 },
            { it == helper && helperIsInput })

    @Test fun wineRootHelperCapturesForFocusedGame() {
        assertTrue(matches(helper))
        assertTrue(matches(helper, child))
    }

    @Test fun wineHelperDoesNotCaptureForUnfocusedGame() {
        assertFalse(matches(helper, other))
        assertFalse(matches(helper, 0))
        assertFalse(matches(helper, root))
    }

    @Test fun ordinarySiblingWindowIsNotTreatedAsHelper() {
        assertFalse(matches(helper, helperIsInput = false))
        assertFalse(matches(other))
    }

    @Test fun directChildAndRootGrabsRemainSupported() {
        assertTrue(matches(game))
        assertTrue(matches(child))
        assertTrue(matches(root))
        assertFalse(matches(0))
    }

    @Test fun malformedTreeCannotLoopForever() {
        assertFalse(LinuxPointerGrabTarget.matches(40, game, root, other, { 40 }, { true }))
    }
}
