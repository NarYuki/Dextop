package moe.n4tsu.cardex

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarCompanionLifecyclePolicyTest {
    @Test
    fun configurationRecreationIsGraceful() {
        assertTrue(CarCompanionLifecyclePolicy.isGracefulActivityDestruction(true))
    }

    @Test
    fun unexpectedHostDestructionRequiresRecovery() {
        assertFalse(CarCompanionLifecyclePolicy.isGracefulActivityDestruction(false))
    }
}
