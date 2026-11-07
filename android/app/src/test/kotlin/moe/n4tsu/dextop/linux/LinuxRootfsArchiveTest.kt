package moe.n4tsu.dextop.linux

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarConstants
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission

class LinuxRootfsArchiveTest {
    @Test fun legacyBackupRebasesProotHostAbsoluteLinks() {
        val root = Files.createTempDirectory("dextop-proot-backup").toFile()
        try {
            val source = "/data/local/tmp/dextop/linux/containers/cold/rootfs"
            val link = TarArchiveEntry("container/rootfs/var/lib/dpkg/status-old", TarConstants.LF_SYMLINK)
                .apply { linkName = "$source/var/lib/dpkg/.l2s.status0001" }
            LinuxRootfsArchive.extractBackup(archive(link to byteArrayOf()), root, {}, {}, source)
            val path = root.resolve("container/rootfs/var/lib/dpkg/status-old").toPath()
            assertEquals(".l2s.status0001", Files.readSymbolicLink(path).toString())
            val sourcePath = root.toPath().resolve("source").toAbsolutePath()
            assertEquals("/var/lib/dpkg/.l2s.status0001", LinuxBackups.portableLink(
                sourcePath, sourcePath.resolve("var/lib/dpkg/.l2s.status0001")))
            val unrelated = sourcePath.parent.resolve("other")
            assertEquals(unrelated.toString(), LinuxBackups.portableLink(sourcePath, unrelated))
        } finally { LinuxRootfsArchive.deleteTree(root) }
    }

    @Test fun restoresDirectorySelfLinksAsDotInsteadOfAnEmptyTarget() {
        val root = Files.createTempDirectory("dextop-dot-link").toFile()
        try {
            val link = TarArchiveEntry("usr/bin/X11", TarConstants.LF_SYMLINK).apply { linkName = "." }
            LinuxRootfsArchive.extract(archive(link to byteArrayOf()), root)
            assertEquals(".", Files.readSymbolicLink(root.resolve("usr/bin/X11").toPath()).toString())
        } finally { LinuxRootfsArchive.deleteTree(root) }
    }

    @Test fun backupAbsoluteLinksStayInsideTheNestedGuestRoot() {
        val root = Files.createTempDirectory("dextop-backup-link").toFile()
        try {
            val link = TarArchiveEntry("container/rootfs/home/user/link", TarConstants.LF_SYMLINK)
                .apply { linkName = "/etc/example" }
            LinuxRootfsArchive.extractBackup(archive(link to byteArrayOf()), root, {}, {})
            val file = root.resolve("container/rootfs/home/user/link").toPath()
            assertEquals(root.resolve("container/rootfs/etc/example").toPath(),
                file.parent.resolve(Files.readSymbolicLink(file)).normalize())
        } finally { LinuxRootfsArchive.deleteTree(root) }
    }

    @Test fun deletesReadOnlyDirectoriesWithoutFollowingSymlinks() {
        val root = Files.createTempDirectory("dextop-delete").toFile()
        val outside = Files.createTempDirectory("dextop-delete-outside").toFile()
        try {
            outside.resolve("keep").writeText("untouched")
            val directory = root.resolve("nested").apply { mkdirs() }
            directory.resolve("file").writeText("remove")
            Files.createSymbolicLink(directory.resolve("outside").toPath(), outside.toPath())
            Files.getFileAttributeView(directory.toPath(), PosixFileAttributeView::class.java)?.setPermissions(
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE))
            LinuxRootfsArchive.deleteTree(root)
            assertFalse(root.exists())
            assertEquals("untouched", outside.resolve("keep").readText())
        } finally {
            LinuxRootfsArchive.deleteTree(root)
            LinuxRootfsArchive.deleteTree(outside)
        }
    }

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

    @Test fun keepsSymlinksEscapingTheGuestRootInsideIt() {
        val root = Files.createTempDirectory("dextop-link-escape").toFile()
        try {
            val link = TarArchiveEntry("escape", TarConstants.LF_SYMLINK).apply { linkName = "../outside" }
            LinuxRootfsArchive.extract(archive(link to byteArrayOf()), root)
            // ".." stops at the guest's /, as it does inside the container.
            assertEquals("outside", Files.readSymbolicLink(File(root, "escape").toPath()).toString())
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
