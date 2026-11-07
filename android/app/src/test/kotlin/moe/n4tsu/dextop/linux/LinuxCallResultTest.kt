package moe.n4tsu.dextop.linux

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LinuxCallResultTest {
    private class Resource(private val failClose: Boolean = false) : AutoCloseable {
        var closes = 0
        override fun close() { closes++; if (failClose) error("Disconnected descriptor") }
    }
    @Test fun switchedConnectionClosesResultBeforeReportingFailure() {
        val result = Resource()
        var failed = false
        LinuxCallResult.deliver(result, false, { error("Stale result was delivered") }) {
            assertEquals(1, result.closes)
            failed = true
        }
        assertTrue(failed)
    }
    @Test fun currentConnectionTransfersOwnershipToCaller() {
        val result = Resource()
        var delivered = false
        LinuxCallResult.deliver(result, true, { assertSame(result, it); delivered = true }) { error("Current result was discarded") }
        assertTrue(delivered)
        assertEquals(0, result.closes)
    }
    @Test fun failedCleanupStillReportsTheConnectionChange() {
        val result = Resource(true)
        var failed = false
        LinuxCallResult.deliver(result, false, { error("Stale result was delivered") }) { failed = true }
        assertEquals(1, result.closes)
        assertTrue(failed)
    }
    @Test fun ordinaryAndEmptyResultsCanAlsoBeDiscarded() {
        var delivered = false
        var failures = 0
        LinuxCallResult.deliver("old list", false, { delivered = true }) { failures++ }
        LinuxCallResult.deliver(null, false, { delivered = true }) { failures++ }
        assertFalse(delivered)
        assertEquals(2, failures)
    }
}
