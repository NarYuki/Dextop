package moe.n4tsu.dextop.linux

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarConstants
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files

class LinuxRootfsArchiveTest {
    private fun archive(vararg entries: Pair<TarArchiveEntry, ByteArray>): ByteArrayInputStream {
        val data = ByteArrayOutputStream()
        TarArchiveOutputStream(data).use { tar ->
            entries.forEach { (entry, bytes) ->
                entry.size = bytes.size.toLong()
                tar.putArchiveEntry(entry)
                tar.write(bytes)
                tar.closeArchiveEntry()
            }
        }
        return ByteArrayInputStream(data.toByteArray())
    }

    @Test fun extractsFilesAndSkipsHostDeviceNodes() {
        val root = Files.createTempDirectory("dextop-rootfs").toFile()
        try {
            val input = archive(TarArchiveEntry("./usr/bin/sh") to "shell".toByteArray(),
                TarArchiveEntry("./dev/null", TarConstants.LF_CHR) to byteArrayOf())
            LinuxRootfsArchive.extract(input, root)
            assertEquals("shell", root.resolve("usr/bin/sh").readText())
            assertFalse(root.resolve("dev/null").exists())
        } finally { LinuxRootfsArchive.deleteTree(root) }
    }

    @Test fun preservesHardlinksEvenWhenTargetComesLater() {
        val root = Files.createTempDirectory("dextop-hardlink").toFile()
        try {
            val link = TarArchiveEntry("usr/bin/alias", TarConstants.LF_LINK).apply { linkName = "./usr/bin/target" }
            LinuxRootfsArchive.extract(archive(link to byteArrayOf(), TarArchiveEntry("usr/bin/target") to "data".toByteArray()), root)
            assertTrue(Files.isSameFile(root.resolve("usr/bin/alias").toPath(), root.resolve("usr/bin/target").toPath()))
        } finally { LinuxRootfsArchive.deleteTree(root) }
    }

    @Test fun rejectsTraversalInsteadOfWritingOutsideRootfs() {
        val root = Files.createTempDirectory("dextop-traversal").toFile()
        try {
            assertThrows(IllegalArgumentException::class.java) {
                LinuxRootfsArchive.extract(archive(TarArchiveEntry("../outside") to "bad".toByteArray()), root)
            }
        } finally { LinuxRootfsArchive.deleteTree(root) }
    }

    @Test fun rejectsSymlinksEscapingTheGuestRoot() {
        val root = Files.createTempDirectory("dextop-link-escape").toFile()
        try {
            val link = TarArchiveEntry("escape", TarConstants.LF_SYMLINK).apply { linkName = "../outside" }
            assertThrows(java.io.IOException::class.java) { LinuxRootfsArchive.extract(archive(link to byteArrayOf()), root) }
        } finally { LinuxRootfsArchive.deleteTree(root) }
    }

    @Test fun cancellationRemainsCancellation() {
        val root = Files.createTempDirectory("dextop-cancel").toFile()
        try {
            assertThrows(InterruptedException::class.java) {
                LinuxRootfsArchive.extract(archive(TarArchiveEntry("file") to "data".toByteArray()), root,
                    { throw InterruptedException("Installation cancelled") })
            }
        } finally { LinuxRootfsArchive.deleteTree(root) }
    }

    @Test fun truncatedFileReportsTheEntryThatFailed() {
        val root = Files.createTempDirectory("dextop-truncated").toFile()
        try {
            val complete = archive(TarArchiveEntry("usr/bin/sh") to ByteArray(8192)).readBytes()
            val error = assertThrows(java.io.IOException::class.java) {
                LinuxRootfsArchive.extract(ByteArrayInputStream(complete.copyOf(1024)), root)
            }
            assertTrue(error.message.orEmpty().contains("usr/bin/sh"))
        } finally { LinuxRootfsArchive.deleteTree(root) }
    }
}
