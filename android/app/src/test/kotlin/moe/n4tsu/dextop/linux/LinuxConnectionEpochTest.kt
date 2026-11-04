package moe.n4tsu.dextop.linux

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinuxConnectionEpochTest {
    @Test fun oldPrivilegedDisconnectCannotCancelNewAndroidConnection() {
        val state = LinuxConnectionEpoch()
        val privileged = state.begin(android = false, embedded = true)!!
        val switch = state.beginSwitch()!!
        assertNull(state.begin(android = true, embedded = false))
        assertFalse(state.abandon(privileged))
        assertTrue(state.finishSwitch(switch))
        val android = state.begin(android = true, embedded = false)!!
        assertFalse(state.abandon(privileged))
        assertTrue(state.owns(android))
        assertTrue(android.android)
        assertFalse(android.embedded)
    }
    @Test fun oldTimeoutAndInitializationReplyCannotReplaceRetry() {
        val state = LinuxConnectionEpoch()
        val timedOut = state.begin(true, false)!!
        assertTrue(state.abandon(timedOut))
        val retry = state.begin(true, false)!!
        assertFalse(state.owns(timedOut))
        assertFalse(state.abandon(timedOut))
        assertTrue(state.owns(retry))
    }
    @Test fun timedOutSwitchCannotCommitLaterOrFinishAnotherSwitch() {
        val state = LinuxConnectionEpoch()
        val timedOut = state.beginSwitch()!!
        assertTrue(state.finishSwitch(timedOut))
        val next = state.beginSwitch()!!
        assertFalse(state.ownsSwitch(timedOut))
        assertFalse(state.finishSwitch(timedOut))
        assertTrue(state.ownsSwitch(next))
        assertTrue(state.finishSwitch(next))
    }
    @Test fun reverseSwitchRetiresAndroidCallbacksAndKeepsCapturedModes() {
        val state = LinuxConnectionEpoch()
        val android = state.begin(true, false)!!
        val switch = state.beginSwitch()!!
        assertFalse(state.owns(android))
        assertNull(state.begin(false, true))
        assertTrue(state.finishSwitch(switch))
        val privileged = state.begin(false, true)!!
        assertTrue(android.android)
        assertFalse(privileged.android)
        assertFalse(state.abandon(android))
        assertTrue(state.owns(privileged))
    }
    @Test fun rejectedSwitchAllowsConnectionInUnchangedMode() {
        val state = LinuxConnectionEpoch()
        val switch = state.beginSwitch()!!
        assertNull(state.beginSwitch())
        assertTrue(state.finishSwitch(switch))
        assertFalse(state.finishSwitch(switch))
        assertTrue(state.owns(state.begin(false, false)!!))
    }
}
