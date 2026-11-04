package moe.n4tsu.dextop.linux

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LinuxMacosSettingsTest {
    @Test fun bundlePathsRemainOneShellArgument() {
        assertEquals("python3 /usr/local/lib/dextop/macos/launch_app.py --register --bundle '/root/It'\\''s; App.app'",
            LinuxMacosSettings.command("register", "/root/It's; App.app"))
    }

    @Test fun registrationRemovalCannotBecomeAShellCommand() {
        for (id in listOf("../file", "0".repeat(24) + ";true", "A".repeat(24))) {
            assertFailsWith<IllegalArgumentException> { LinuxMacosSettings.command("unregister", id) }
        }
        for (path in listOf("relative.app", "/root/a\u0000.app", "/root/a\n.app")) {
            assertFailsWith<IllegalArgumentException> { LinuxMacosSettings.command("register", path) }
        }
    }
}
