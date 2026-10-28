package moe.n4tsu.dextop.linux

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LinuxAudioBufferingTest {
    @Test fun readsDoNotWaitForTheNormalMixerMinimum() {
        assertEquals(960, LinuxAudioBuffering.READ_BYTES) // 5ms, not the old 120ms read
        assertEquals(0, LinuxAudioBuffering.READ_BYTES % LinuxAudioBuffering.FRAME_BYTES)
    }
    @Test fun deviceBurstUsesASmallTargetAndBoundedAdaptationCapacity() {
        val burst = LinuxAudioBuffering.burstFrames("192")
        assertEquals(480, LinuxAudioBuffering.targetFrames(burst))
        assertEquals(768, LinuxAudioBuffering.capacityFrames(burst))
        assertTrue(LinuxAudioBuffering.capacityFrames(burst) < 48_000 / 50)
        for (property in listOf(null, "", "invalid", "0", "-1", "99999999"))
            assertEquals(240, LinuxAudioBuffering.burstFrames(property))
    }
    @Test fun normalQueuedAudioIsPreserved() {
        assertEquals(0, LinuxAudioBuffering.staleBytes(3840))
        assertEquals(0, LinuxAudioBuffering.staleBytes(11520))
    }
    @Test fun staleAudioDropsOnlyWholeStereoFrames() {
        assertEquals(7680, LinuxAudioBuffering.staleBytes(11521))
        assertEquals(7680, LinuxAudioBuffering.staleBytes(11523))
        assertEquals(0, LinuxAudioBuffering.staleBytes(11523) % 4)
    }
}
