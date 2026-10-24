package moe.n4tsu.dextop.linux

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LinuxSessionCompatibilityTest {
    @Test fun freshInstallAndRepairCommitPersistentEnvironmentWithTheRuntime() {
        val dir = Files.createTempDirectory("linux-runtime-install").toFile()
        try {
            val root = File(dir, "root")
            val native = File(dir, "native").apply { mkdirs() }
            File(native, LinuxSessionCompatibility.LIBRARY).writeBytes(byteArrayOf(1))
            val env = File(root, "etc/dextop/session.env").apply {
                parentFile!!.mkdirs(); writeText("export LANG=ja_JP.UTF-8\nexport CUSTOM_SETTING=1\n")
            }
            LinuxSessionCompatibility.install(root, native.path, "dextop-cfresh", true)
            val installed = env.readText()
            val policy = File(root, LinuxSessionCompatibility.WSI_CONFIG.removePrefix("/"))
            assertEquals("xvfb\n", policy.readText())
            kotlin.test.assertContains(installed, "export CUSTOM_SETTING=1")
            kotlin.test.assertTrue(File(root, "usr/share/vulkan/implicit_layer.d/dextop-xvfb.json").isFile)
            LinuxSessionCompatibility.install(root, native.path, "dextop-cfresh", true)
            assertEquals(installed, env.readText())
            policy.writeText("invalid\n")
            LinuxSessionCompatibility.install(root, native.path, "dextop-cfresh", true)
            assertEquals("xvfb\n", policy.readText())
            assertEquals(installed, env.readText())
            LinuxSessionCompatibility.install(root, native.path, "dextop-cfresh", false)
            assertEquals("wayland\n", policy.readText())
            assertEquals(installed, env.readText())
        } finally { dir.deleteRecursively() }
    }
    @Test fun upgradesTheSharedLibraryAndPreservesOtherPreloads() {
        val dir = Files.createTempDirectory("linux-preload").toFile()
        try {
            val root = File(dir, "root")
            val native = File(dir, "native").apply { mkdirs() }
            val source = File(native, LinuxSessionCompatibility.LIBRARY).apply { writeBytes(byteArrayOf(1)) }
            val config = File(root, "etc/ld.so.preload").apply {
                parentFile!!.mkdirs()
                writeText("# user's preload\n/usr/local/lib/custom.so\n")
            }
            LinuxSessionCompatibility.stage(root, native.path)
            val vulkanManifest = File(root, "usr/share/vulkan/implicit_layer.d/dextop-xvfb.json").readText()
            kotlin.test.assertFalse("enable_environment" in vulkanManifest)
            kotlin.test.assertContains(vulkanManifest, "disable_environment")
            kotlin.test.assertTrue(File(root, "usr/local/lib/dextop/libdextop_vk_xvfb.so").length() > 0)
            val expected = config.readText()
            source.writeBytes(byteArrayOf(2, 3))
            LinuxSessionCompatibility.stage(root, native.path)
            assertEquals("${LinuxSessionCompatibility.GUEST_PRELOAD}\n${LinuxSessionCompatibility.GUEST_SOCKET_SHIM}\n${LinuxProotFastPath.LIBRARY}\n# user's preload\n/usr/local/lib/custom.so\n", expected)
            assertEquals(expected, config.readText())
            assertContentEquals(byteArrayOf(2, 3), File(root, LinuxSessionCompatibility.GUEST_PRELOAD.removePrefix("/")).readBytes())
        } finally { dir.deleteRecursively() }
    }

    @Test fun missingLibraryDoesNotLeaveAnInvalidPreload() {
        val dir = Files.createTempDirectory("linux-preload-missing").toFile()
        try {
            assertFailsWith<IllegalStateException> { LinuxSessionCompatibility.stage(dir, File(dir, "missing").path) }
            assertEquals(false, File(dir, "etc/ld.so.preload").exists())
        } finally { dir.deleteRecursively() }
    }
}
