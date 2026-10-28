package moe.n4tsu.dextop.linux

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LinuxSessionCompatibilityTest {
    @Test fun systemServicesUseTheSharedBusAndKeepPackageKitOutsideFakeroot() {
        val root = Files.createTempDirectory("linux-system-services").toFile()
        try {
            val sources = File(root, "usr/share/dbus-1/system-services").apply { mkdirs() }
            File(sources, "packagekit.service").writeText("[D-BUS Service]\nName=org.freedesktop.PackageKit\nExec=/usr/libexec/packagekitd\nUser=root\n")
            File(sources, "polkit.service").writeText("[D-BUS Service]\nName=org.freedesktop.PolicyKit1\nExec=/usr/lib/polkit-1/polkitd --no-debug\nUser=root\n")
            LinuxSessionCompatibility.stageSystemServices(root)
            val target = File(root, "etc/dextop/system-services")
            val pkg = File(target, "packagekit.service").readText()
            kotlin.test.assertContains(pkg, "DBUS_SYSTEM_BUS_ADDRESS=${LinuxCatalog.SYSTEM_BUS_ADDRESS}")
            kotlin.test.assertFalse("fakeroot" in pkg)
            kotlin.test.assertContains(File(target, "polkit.service").readText(), "fakeroot-tcp /usr/lib/polkit-1/polkitd")
            LinuxSessionCompatibility.stageSystemServices(root)
            assertEquals(pkg, File(target, "packagekit.service").readText())
        } finally { root.deleteRecursively() }
    }
    @Test fun securityPromptsUseX11WithoutReplacingUserActivationOverrides() {
        val dir = Files.createTempDirectory("linux-security-prompts").toFile()
        try {
            val root = File(dir, "root")
            val native = File(dir, "native").apply { mkdirs() }
            File(native, LinuxSessionCompatibility.LIBRARY).writeBytes(byteArrayOf(1))
            val system = File(root, "usr/share/dbus-1/services/org.gnome.keyring.SystemPrompter.service")
            system.parentFile.mkdirs()
            system.writeText("[D-BUS Service]\nName=org.gnome.keyring.SystemPrompter\nExec=/usr/libexec/gcr-prompter\n")
            LinuxSessionCompatibility.stage(root, native.path)
            val target = File(root, "root/.local/share/dbus-1/services/org.gnome.keyring.SystemPrompter.service")
            kotlin.test.assertContains(target.readText(), "Exec=/usr/bin/env GDK_BACKEND=x11 /usr/libexec/gcr-prompter")
            val managed = target.readText()
            LinuxSessionCompatibility.stage(root, native.path)
            assertEquals(managed, target.readText())
            target.writeText("[D-BUS Service]\nName=org.gnome.keyring.SystemPrompter\nExec=/custom/prompt\n")
            val custom = target.readText()
            LinuxSessionCompatibility.stage(root, native.path)
            assertEquals(custom, target.readText())
        } finally { dir.deleteRecursively() }
    }
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
            val audioConfigs = listOf("client.conf.d", "client-rt.conf.d").map {
                File(root, "etc/pipewire/$it/99-dextop-session.conf")
            }
            val audioPolicy = audioConfigs.first().readText()
            kotlin.test.assertContains(audioPolicy, "rtportal.enabled = false")
            audioConfigs.forEach { assertEquals(audioPolicy, it.readText()) }
            val policy = File(root, LinuxSessionCompatibility.WSI_CONFIG.removePrefix("/"))
            assertEquals("xvfb\n", policy.readText())
            kotlin.test.assertContains(installed, "export CUSTOM_SETTING=1")
            kotlin.test.assertTrue(File(root, "usr/local/share/vulkan/implicit_layer.d/dextop-xvfb.json").isFile)
            LinuxSessionCompatibility.install(root, native.path, "dextop-cfresh", true)
            assertEquals(installed, env.readText())
            policy.writeText("invalid\n")
            audioConfigs.first().writeText("module.rt.args = { rtportal.enabled = true }\n")
            LinuxSessionCompatibility.install(root, native.path, "dextop-cfresh", true)
            assertEquals("xvfb\n", policy.readText())
            audioConfigs.forEach { assertEquals(audioPolicy, it.readText()) }
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
            val vulkanManifest = File(root, "usr/local/share/vulkan/implicit_layer.d/dextop-xvfb.json").readText()
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
