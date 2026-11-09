package moe.n4tsu.dextop.linux

import org.json.JSONObject
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LinuxSharingTest {
    @Test fun savedGrantsSurviveReloadAndNeverChangeAnotherContainer() {
        val root = Files.createTempDirectory("dextop-sharing").toFile()
        val first = root.resolve("c1").apply { mkdir() }
        val second = root.resolve("c2").apply { mkdir() }
        try {
            LinuxSharing.write(first, JSONObject().put("wholeStorage", true).put("camera", true))
            assertTrue(LinuxSharing.read(first).getBoolean("wholeStorage"))
            assertFalse(LinuxSharing.read(second).getBoolean("wholeStorage"))
            LinuxSharing.write(first, JSONObject().put("microphone", true))
            assertFalse(LinuxSharing.read(first).getBoolean("camera"))
            assertTrue(LinuxSharing.read(first).getBoolean("microphone"))
            assertFalse(LinuxSharing.read(second).getBoolean("microphone"))
            first.resolve("sharing.json").writeText("broken")
            LinuxSharing.kinds.forEach { assertFalse(LinuxSharing.read(first).getBoolean(it)) }
        } finally { root.deleteRecursively() }
    }

    @Test fun missingAndLegacyGlobalGrantsDoNotGrantContainerAccess() {
        val policy = LinuxSharing.normalize(JSONObject())
        LinuxSharing.kinds.forEach { assertFalse(policy.getBoolean(it)) }
        assertEquals(3, policy.length())
    }

    @Test fun grantsAreIndependentAndUnknownFieldsAreDiscarded() {
        val first = LinuxSharing.normalize(JSONObject().put("camera", true).put("hostShell", true))
        val second = LinuxSharing.normalize(JSONObject())
        assertTrue(first.getBoolean("camera"))
        assertFalse(first.getBoolean("microphone"))
        assertFalse(first.getBoolean("wholeStorage"))
        assertFalse(second.getBoolean("camera"))
        assertFalse(first.has("hostShell"))
    }
}
