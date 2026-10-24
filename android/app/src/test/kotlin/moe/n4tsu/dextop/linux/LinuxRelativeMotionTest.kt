package moe.n4tsu.dextop.linux
import kotlin.test.Test
import kotlin.test.assertEquals
class LinuxRelativeMotionTest {
    @Test fun fractionalMotionAccumulatesInBothDirections() {
        val motion = LinuxRelativeMotion()
        assertEquals(0 to 0, motion.add(.25f, -.25f))
        assertEquals(0 to 0, motion.add(.25f, -.25f))
        assertEquals(0 to 0, motion.add(.25f, -.25f))
        assertEquals(1 to -1, motion.add(.25f, -.25f))
    }
    @Test fun reversalPreservesNetPhysicalMovement() {
        val motion = LinuxRelativeMotion()
        assertEquals(7 to -3, motion.add(7.5f, -3.5f))
        assertEquals(-2 to 4, motion.add(-3f, 5f))
        assertEquals(0 to 0, motion.add(.5f, -.5f))
    }
    @Test fun focusLossDiscardsRemainder() {
        val motion = LinuxRelativeMotion()
        motion.add(.75f, -.75f); motion.reset()
        assertEquals(0 to 0, motion.add(.5f, -.5f))
    }
    @Test fun invalidInputDoesNotPoisonFollowingEvents() {
        val motion = LinuxRelativeMotion()
        assertEquals(0 to 0, motion.add(Float.NaN, Float.POSITIVE_INFINITY))
        assertEquals(4 to -5, motion.add(4f, -5f))
    }
}
