package moe.n4tsu.dextop.linux

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class LinuxProotFastPathTest {
    @Test fun orderedMountsDetermineMappingAndPolicyUsesCanonicalSources() {
        val dir = Files.createTempDirectory("landlock-policy").toFile()
        try {
            val root = File(dir, "root").apply { mkdirs() }
            val native = File(dir, "native").apply { mkdirs() }
            val shared = File(dir, "shared").apply { mkdirs() }
            val mounts = listOf("${shared.path}:/share", "${native.path}:/native")
            val first = LinuxProotFastPath.create(root, mounts, native.path)!!
            assertEquals(first, LinuxProotFastPath.create(root, mounts, native.path))
            assertNotEquals(first.key, LinuxProotFastPath.create(root, mounts.reversed(), native.path)!!.key)
            assertEquals(listOf(root.canonicalPath, native.canonicalPath, shared.canonicalPath), first.landlockPaths)
            assertEquals("PROOT_FP_KEY=${first.key}", first.guestEnvironment.last())
            assertEquals("PROOT_FP_BINDS=${mounts.joinToString("|")}", first.guestEnvironment[1])
        } finally { dir.deleteRecursively() }
    }
    @Test fun invalidOrBroadPoliciesDisableFastPath() {
        val dir = Files.createTempDirectory("landlock-invalid").toFile()
        try {
            for (mounts in listOf(listOf("/a|b:/c"), List(257) { "/mount$it" },
                listOf("relative:/guest"), listOf("${dir.path}:/"), listOf("/.:/host"),
                listOf("${dir.path}:/guest!"), listOf("${dir.path}:/guest/"),
                listOf("${dir.path}:/guest/../alias"), listOf("${dir.path}/missing:/guest"))) {
                assertNull(LinuxProotFastPath.create(dir, mounts, dir.path))
            }
            assertNull(LinuxProotFastPath.create(File("/root\nother"), emptyList(), dir.path))
            assertNull(LinuxProotFastPath.create(File("/"), emptyList(), dir.path))
        } finally { dir.deleteRecursively() }
    }
    @Test fun stagingReplacesOldLibraryWithoutGlobalPreload() {
        val dir = Files.createTempDirectory("landlock-staging").toFile()
        try {
            val target = File(dir, LinuxProotFastPath.LIBRARY.removePrefix("/"))
            target.parentFile!!.mkdirs()
            target.writeText("retired")
            assertEquals(true, LinuxProotFastPath.stage(dir))
            val bytes = target.readBytes()
            assertEquals(listOf<Byte>(0x7f, 0x45, 0x4c, 0x46), bytes.take(4))
            assertEquals(false, File(dir, "etc/ld.so.preload").exists())
        } finally { dir.deleteRecursively() }
    }
    @Test fun unprotectedModeUsesLegacyLibraryWithoutLandlockPolicy() {
        val dir = Files.createTempDirectory("fast-mode-legacy").toFile()
        try {
            val config = LinuxProotFastPath.create(dir, listOf("/:/host"), dir.path, LinuxFastMode.UNPROTECTED)!!
            assertEquals(LinuxFastMode.UNPROTECTED, config.mode)
            assertEquals(emptyList(), config.landlockPaths)
            assertEquals(true, LinuxProotFastPath.stage(dir, config.mode))
            val legacy = File(dir, config.mode.library.removePrefix("/"))
            assertEquals(listOf<Byte>(0x7f, 0x45, 0x4c, 0x46), legacy.readBytes().take(4))
            assertEquals(false, File(dir, LinuxFastMode.PROTECTED.library.removePrefix("/")).exists())
        } finally { dir.deleteRecursively() }
    }
    @Test fun modesStageIndependentlyAndUnknownModeDefaultsToProtected() {
        val dir = Files.createTempDirectory("fast-mode-both").toFile()
        try {
            for (mode in LinuxFastMode.entries) assertEquals(true, LinuxProotFastPath.stage(dir, mode))
            val protected = File(dir, LinuxFastMode.PROTECTED.library.removePrefix("/")).readBytes()
            val legacy = File(dir, LinuxFastMode.UNPROTECTED.library.removePrefix("/")).readBytes()
            assertEquals(false, protected.contentEquals(legacy))
            assertEquals(LinuxFastMode.PROTECTED, LinuxFastMode.from(null))
            assertEquals(LinuxFastMode.PROTECTED, LinuxFastMode.from("invalid"))
            assertEquals(LinuxFastMode.UNPROTECTED, LinuxFastMode.from("unprotected"))
        } finally { dir.deleteRecursively() }
    }

}
