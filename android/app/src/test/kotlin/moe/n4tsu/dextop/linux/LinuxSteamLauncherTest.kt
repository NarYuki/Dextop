package moe.n4tsu.dextop.linux

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LinuxSteamLauncherTest {
    @Test fun reopeningForwardsTheRequestAfterRuntimeEnvironmentIsReady() {
        val script = LinuxSteam.launcherScript
        assertTrue(script.indexOf("export LD_LIBRARY_PATH=") < script.indexOf("flock -n -E 73"))
        assertTrue(script.contains("set -- steam://open/main"))
        assertFalse(script.contains("Steam is already starting or running."))
    }
}
