package moe.n4tsu.dextop.plasma

import kotlin.test.*

class SteamModeProgressTest {
    @Test fun stageSummaryContainsOnlySessionStepsAndCoalescesDuplicates() {
        val progress = assertNotNull(SteamModeProgress.parse("== STEP 00:00:01 stage A\nprivate log text\n== STEP 00:00:02 stage A\n== STEP 00:00:03 stage B"))
        assertEquals(listOf("stage A", "stage B"), progress.stages)
    }

    @Test fun readySurvivesWhenLogTailHasLostAllStepLines() {
        val progress = assertNotNull(SteamModeProgress.parse("ordinary client log\n== READY 16:58:04\nmore client log"))
        assertTrue(progress.ready)
        assertNull(progress.exit)
    }
    @Test fun lastStepAndExitOverrideAnEarlierReady() {
        val progress = assertNotNull(SteamModeProgress.parse("== READY 16:58:04\n== STEP 16:59:00 Starting the Steam client\n== EXIT 7"))
        assertFalse(progress.ready)
        assertEquals(7, progress.exit)
    }
    @Test fun ordinaryTailWithoutSessionMarkersIsNotAnUpdate() {
        assertNull(SteamModeProgress.parse("ordinary client log\nmore client log"))
    }
}
