package moe.n4tsu.dextop.linux

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.GZIPOutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertEquals

class LinuxBackupsTest {
    @Test fun invalidConfigNeverCommitsContainer() {
        val directory = Files.createTempDirectory("restore-validation").toFile()
        val base = LinuxPaths::class.java.getDeclaredField("androidBase").apply { isAccessible = true }
        val previous = base.get(LinuxPaths)
        try {
            base.set(LinuxPaths, directory)
            val bytes = ByteArrayOutputStream()
            TarArchiveOutputStream(GZIPOutputStream(bytes)).use { tar ->
                fun entry(name: String, text: String = "") {
                    val content = text.toByteArray()
                    tar.putArchiveEntry(TarArchiveEntry(name).apply { size = content.size.toLong() })
                    tar.write(content)
                    tar.closeArchiveEntry()
                }
                entry("dextop-backup.json", "{\"format\":1}")
                entry("container/rootfs/")
                entry("container/config.json", "broken JSON")
            }
            assertFails { LinuxBackups.restore({ ByteArrayInputStream(bytes.toByteArray()) }, "cTest", {}, {}) }
            assertFalse(LinuxPaths.container("cTest").exists())
            assertFalse(File(LinuxPaths.containers, ".restore-cTest").exists())
            assertEquals(emptyList(), LinuxPaths.containers.listFiles()!!.toList())
        } finally {
            base.set(LinuxPaths, previous)
            directory.deleteRecursively()
        }
    }
}
