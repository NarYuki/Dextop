package moe.n4tsu.dextop.linux

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LinuxSetupScriptTest {
    @Test fun fullSteamSetupUsesAFileInsteadOfAnOversizedArgument() {
        val root = Files.createTempDirectory("linux-setup-script").toFile()
        try {
            val script = LinuxSteam.installScript(apt = true) { "" }
            assertTrue(script.toByteArray().size > 131072)
            val result = LinuxSetupScript.run(root, script) { command ->
                assertTrue(command.toByteArray().size < 256)
                val guestPath = command.removePrefix("exec /bin/sh ")
                assertEquals(script, File(root, guestPath.removePrefix("/")).readText())
                42
            }
            assertEquals(42, result)
            assertTrue(File(root, "tmp").listFiles()!!.isEmpty())
        } finally { root.deleteRecursively() }
    }

    @Test fun failedExecutionCleansUpWithoutMaskingTheFailure() {
        val root = Files.createTempDirectory("linux-setup-failed").toFile()
        try {
            val error = assertFailsWith<IllegalStateException> {
                LinuxSetupScript.run(root, "exit 1\n") { throw IllegalStateException("setup failed") }
            }
            assertEquals("setup failed", error.message)
            assertTrue(File(root, "tmp").listFiles()!!.isEmpty())
        } finally { root.deleteRecursively() }
    }
}
