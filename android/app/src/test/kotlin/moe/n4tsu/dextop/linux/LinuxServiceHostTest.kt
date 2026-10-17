package moe.n4tsu.dextop.linux

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class LinuxServiceHostTest {
    @Test fun launchUsesBundledClassesAndQuotesPathsWithoutExternalManager() {
        val command = LinuxServiceHost.startCommand("moe.n4tsu.dextop", "/data/app/test's app/base.apk", "/data/app/native libs")
        assertContains(command, "CLASSPATH='/data/app/test'\\''s app/base.apk'")
        assertContains(command, "app_process /system/bin --nice-name='moe.n4tsu.dextop:dextop_linux_embedded'")
        assertContains(command, "moe.n4tsu.dextop.linux.LinuxServiceHost")
        assertContains(command, "'/data/app/native libs'")
        assertFalse(command.contains("shizuku", ignoreCase = true))
    }
}
