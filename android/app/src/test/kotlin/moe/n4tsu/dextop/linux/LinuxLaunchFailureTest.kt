package moe.n4tsu.dextop.linux

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LinuxLaunchFailureTest {
    @Test fun recognizesChromiumFatalGpuExitWithoutTreatingAllTrapsAsGpuFailures() {
        assertTrue(LinuxLaunchFailure.gpu(133, "GPU process isn't usable. Goodbye."))
        assertTrue(LinuxLaunchFailure.chromiumGpu(1, "GPU process isn't usable. Goodbye."))
        assertFalse(LinuxLaunchFailure.gpu(133, "Unrelated application assertion"))
        assertFalse(LinuxLaunchFailure.gpu(0, "GPU process isn't usable"))
        assertTrue(LinuxLaunchFailure.gpu(139, ""))
        assertTrue(LinuxLaunchFailure.gpu(134, ""))
    }
}
