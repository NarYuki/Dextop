package moe.n4tsu.dextop.linux

import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LinuxUserTest {
    @Test fun relocatesOnlyRootsHomeNotPathsEndingInRoot() {
        val script = "HOME=/root bash x\nmkdir -p /root/.local/share\nln -sfn \"\$steam_root\" \"\$HOME/.steam/root\"\n" +
            "cp a /rootfs/b; cd /root; echo '/root'"
        assertEquals("HOME=/home/me bash x\nmkdir -p /home/me/.local/share\nln -sfn \"\$steam_root\" \"\$HOME/.steam/root\"\n" +
            "cp a /rootfs/b; cd /home/me; echo '/home/me'", LinuxUser.relocateHome(script, "/home/me"))
        assertEquals(script, LinuxUser.relocateHome(script, "/root"))
    }

    @Test fun validatesCredentialsAndNeverSerializesPassword() {
        val json = JSONObject().put("username", "yuuki").put("accountPassword", "a test password")
        assertEquals("yuuki" to "a test password", LinuxUser.credentials(json))
        val persisted = LinuxSetupOptions.fromJson(json).toJson()
        assertEquals("yuuki", persisted.getString("username"))
        assertFalse(persisted.has("accountPassword"))
        assertFalse(persisted.toString().contains("a test password"))
        for (name in listOf("root", "nobody", "../outside", "a;id", "UPPER", "", "-option")) {
            assertFailsWith<IllegalArgumentException> { LinuxUser.validate(name) }
        }
        for (password in listOf("short", "password\nsecond", "password\u0000")) {
            assertFailsWith<IllegalArgumentException> {
                LinuxUser.credentials(JSONObject().put("username", "yuuki").put("accountPassword", password))
            }
        }
    }

    @Test fun newAccountUsesItsOwnDesktopEntriesAndHome() {
        val root = Files.createTempDirectory("linux-user").toFile()
        try {
            assertEquals("/root", LinuxUser.home(root))
            File(root, "etc/dextop/username").apply { parentFile.mkdirs(); writeText("yuuki\n") }
            assertEquals("/home/yuuki", LinuxUser.home(root))
            File(root, "home/yuuki/.local/share/applications/example.desktop").apply {
                parentFile.mkdirs()
                writeText("[Desktop Entry]\nType=Application\nName=User app\nExec=example\n")
            }
            File(root, "root/.local/share/applications/root-only.desktop").apply {
                parentFile.mkdirs()
                writeText("[Desktop Entry]\nType=Application\nName=Root app\nExec=example\n")
            }
            assertEquals("User app", LinuxDesktopEntries.find(root, "example.desktop")?.name)
            assertEquals(null, LinuxDesktopEntries.find(root, "root-only.desktop"))
            val launch = LinuxCatalog.launchScript(10, "example")
            assertTrue("cd \"\$HOME\"" in launch)
        } finally { root.deleteRecursively() }
    }
}
