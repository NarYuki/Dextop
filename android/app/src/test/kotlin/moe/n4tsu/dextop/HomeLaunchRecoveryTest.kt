package moe.n4tsu.dextop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeLaunchRecoveryTest {
    private val firmware = "samsung/pa3qksx/pa3q:16/BP4A.251205.006/build:user/release-keys"

    @Test fun reportsNoWorkaroundBeforeAnyHomeLaunchFailure() {
        assertEquals(
            HomeLaunchRecovery.DecorationWorkaround.NONE,
            HomeLaunchRecovery.classify(null, firmware, platformManaged = true)
        )
        assertEquals(
            HomeLaunchRecovery.DecorationWorkaround.NONE,
            HomeLaunchRecovery.classify("", firmware, platformManaged = true)
        )
    }

    @Test fun reportsAppliedForTheRunningFirmware() {
        assertEquals(
            HomeLaunchRecovery.DecorationWorkaround.APPLIED,
            HomeLaunchRecovery.classify(firmware, firmware, platformManaged = true)
        )
    }

    @Test fun separatesAStaleRecordFromNoRecord() {
        // A firmware update leaves the old record in place until the next
        // session start drops it, and that difference is what the report has
        // to show: the next start retries without decorations.
        assertEquals(
            HomeLaunchRecovery.DecorationWorkaround.STALE_PENDING_RETRY,
            HomeLaunchRecovery.classify("$firmware.old", firmware, platformManaged = true)
        )
    }

    @Test fun environmentsThatNeverApplyTheWorkaroundReportNotApplicable() {
        assertEquals(
            HomeLaunchRecovery.DecorationWorkaround.NOT_APPLICABLE,
            HomeLaunchRecovery.classify(firmware, firmware, platformManaged = false)
        )
        assertEquals(
            HomeLaunchRecovery.DecorationWorkaround.NOT_APPLICABLE,
            HomeLaunchRecovery.classify(null, firmware, platformManaged = false)
        )
    }

    @Test fun onlyTheRunningFirmwareForcesSystemDecorations() {
        // A stale record must not read as "sessions start decorated": the next
        // start drops it and retries the undecorated configuration.
        assertTrue(HomeLaunchRecovery.DecorationWorkaround.APPLIED.forcesSystemDecorations)
        assertFalse(HomeLaunchRecovery.DecorationWorkaround.STALE_PENDING_RETRY.forcesSystemDecorations)
        assertFalse(HomeLaunchRecovery.DecorationWorkaround.NONE.forcesSystemDecorations)
        assertFalse(HomeLaunchRecovery.DecorationWorkaround.NOT_APPLICABLE.forcesSystemDecorations)
    }

    @Test fun reportValuesAreStableIdentifiers() {
        assertEquals("none", HomeLaunchRecovery.DecorationWorkaround.NONE.report)
        assertEquals("applied", HomeLaunchRecovery.DecorationWorkaround.APPLIED.report)
        assertEquals(
            "stale_pending_retry",
            HomeLaunchRecovery.DecorationWorkaround.STALE_PENDING_RETRY.report
        )
        assertEquals(
            "not_applicable",
            HomeLaunchRecovery.DecorationWorkaround.NOT_APPLICABLE.report
        )
    }
}
