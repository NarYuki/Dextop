package moe.n4tsu.dextop.linux

import org.junit.Assert.assertEquals
import org.junit.Test

class LinuxExecutionModeTest {
    @Test fun defaultAndLegacyModesUseAppPermissions() {
        for (stored in listOf(null, "android", "hybrid", "", "unknown")) {
            assertEquals(LinuxExecutionMode.ANDROID, LinuxExecutionMode.resolve(stored))
        }
    }

    @Test fun explicitPrivilegedChoiceIsPreserved() {
        assertEquals(LinuxExecutionMode.PRIVILEGED, LinuxExecutionMode.resolve("privileged", privilegedAllowed = true))
        assertEquals(LinuxExecutionMode.ANDROID, LinuxExecutionMode.resolve("privileged", privilegedAllowed = false))
    }
}
