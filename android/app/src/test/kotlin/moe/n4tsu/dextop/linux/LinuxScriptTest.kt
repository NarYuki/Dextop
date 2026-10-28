package moe.n4tsu.dextop.linux

import java.nio.file.Files
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LinuxScriptTest {
    @Test fun packagedSteamSetupAlsoNormalizesBase64EmbeddedHelpers() {
        val setup = LinuxSteam.installScript(apt = true) { "" }
        assertTrue('\r' !in setup)
        var scripts = 0
        Regex("printf '%s' '([A-Za-z0-9+/=]+)' ").findAll(setup).forEach { match ->
            val bytes = java.util.Base64.getDecoder().decode(match.groupValues[1])
            if (bytes.size >= 2 && bytes[0] == 35.toByte() && bytes[1] == 33.toByte()) {
                scripts++
                assertTrue('\r' !in bytes.toString(Charsets.UTF_8))
            }
        }
        assertTrue(scripts > 0)
    }

    @Test fun mixedResourceCollectionsNormalizeScriptsAndPreserveBinaries() {
        val script = "#!/bin/bash\r\nset -e\r\nif true; then\r\n echo ok\r\nfi\r\n"
        assertEquals(script.replace("\r\n", "\n"), LinuxScript.resource(script.toByteArray()).toString(Charsets.UTF_8))
        val binary = byteArrayOf(127, 69, 76, 70, 0, 13, 10, -1)
        assertTrue(binary.contentEquals(LinuxScript.resource(binary)))
    }

    @Test fun setupFileNormalizesEmbeddedScriptsBeforeExecution() {
        val root = Files.createTempDirectory("setup-crlf").toFile()
        try {
            LinuxSetupScript.run(root, "set -e\r\nprintf ok\r\n") { command ->
                val file = File(root, command.removePrefix("exec /bin/sh /").replace('/', File.separatorChar))
                assertEquals("set -e\nprintf ok\n", file.readText())
            }
        } finally { root.deleteRecursively() }
    }
}
