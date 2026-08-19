package moe.n4tsu.dextop

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardCompanionSessionPolicyTest {
    @Test
    fun stopsOnlyTheDirectSessionOwnedByCompanion() {
        assertTrue(CardCompanionSessionPolicy.shouldStopDirectSession(true, true))
        assertFalse(CardCompanionSessionPolicy.shouldStopDirectSession(true, false))
        assertFalse(CardCompanionSessionPolicy.shouldStopDirectSession(false, true))
        assertFalse(CardCompanionSessionPolicy.shouldStopDirectSession(false, false))
    }
}
