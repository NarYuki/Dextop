package moe.n4tsu.dextop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StellarBinderRetryGateTest {
    @Test
    fun `backs off repeated recovery requests and caps the delay`() {
        val gate = StellarBinderRetryGate(initialDelayMs = 2_000L, maximumDelayMs = 8_000L)

        assertEquals(2_000L, gate.acquire(0L))
        assertNull(gate.acquire(1_999L))
        assertEquals(4_000L, gate.acquire(2_000L))
        assertNull(gate.acquire(5_999L))
        assertEquals(8_000L, gate.acquire(6_000L))
        assertEquals(8_000L, gate.acquire(14_000L))
    }

    @Test
    fun `binder delivery resets the recovery delay`() {
        val gate = StellarBinderRetryGate(initialDelayMs = 2_000L, maximumDelayMs = 30_000L)

        assertEquals(2_000L, gate.acquire(10_000L))
        gate.reset()
        assertEquals(2_000L, gate.acquire(10_001L))
    }
}
