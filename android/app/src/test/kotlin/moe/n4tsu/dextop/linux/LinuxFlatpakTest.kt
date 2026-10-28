package moe.n4tsu.dextop.linux

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LinuxFlatpakTest {
    @Test fun executableCopyKeepsAStableResourcePrefixAndReusesItsInode() {
        val root = Files.createTempDirectory("flatpak-executable").toFile()
        try {
            val app = File(root, "deployment/files").apply { mkdirs() }
            val source = File(app, "bin/example").apply {
                parentFile.mkdirs()
                writeBytes(byteArrayOf(127, 69, 76, 70, 1, 2, 3))
            }
            val first = LinuxFlatpak.executableBinds(root, "org.example.App", app.parentFile, app)
            val copy = File(first.single().substringBefore(":/app/bin/"))
            assertTrue(copy.readBytes().contentEquals(source.readBytes()))
            assertTrue(!Files.isSymbolicLink(copy.toPath()))
            val modified = copy.lastModified()
            assertEquals(first, LinuxFlatpak.executableBinds(root, "org.example.App", app.parentFile, app))
            assertEquals(modified, copy.lastModified())
            assertTrue(first.single().endsWith(":/app/bin/example"))
        } finally { root.deleteRecursively() }
    }

    @Test fun rejectsInvalidIdsAndForeignArchitectures() {
        val root = Files.createTempDirectory("flatpak-ids").toFile()
        try {
            assertFailsWith<IllegalArgumentException> { LinuxFlatpak.plan(root, "../../etc") }
            val app = File(root, "root/.local/share/flatpak/app/org.example.App/current/active").apply { mkdirs() }
            File(app, "metadata").writeText("[Application]\nruntime=org.example.Platform/x86_64/1\ncommand=example\n")
            assertFailsWith<IllegalArgumentException> { LinuxFlatpak.plan(root, "org.example.App") }
        } finally { root.deleteRecursively() }
    }

    @Test fun mapsDeployedRuntimeAndAppAndKeepsDextopHooks() {
        val root = Files.createTempDirectory("flatpak-plan").toFile()
        try {
            val base = File(root, "root/.local/share/flatpak")
            val app = File(base, "app/org.example.App/current/active").apply { mkdirs() }
            File(app, "files").mkdirs()
            File(app, "metadata").writeText("[Application]\nruntime=org.example.Platform/aarch64/1\ncommand=example\n")
            val runtime = File(base, "runtime/org.example.Platform/aarch64/1/active").apply { mkdirs() }
            File(runtime, "files").mkdirs()
            File(runtime, "files/etc/fonts").mkdirs()
            File(runtime, "files/lib/aarch64-linux-gnu/libopenal.so.1").apply { parentFile.mkdirs(); writeText("runtime-openal") }
            File(root, "usr/share/fonts").mkdirs()
            File(runtime, "metadata").writeText("[Runtime]\nname=org.example.Platform\n")
            val plan = LinuxFlatpak.plan(root, "org.example.App")
            assertTrue(plan.binds.any { it.replace('\\', '/').endsWith("/files:/usr") })
            assertTrue(plan.binds.any { it.replace('\\', '/').endsWith("/files:/app") })
            assertTrue(plan.binds.any { it.replace('\\', '/').endsWith("/usr/local:/usr/local") })
            assertTrue(plan.command.endsWith("'/app/bin/example'"))
            assertTrue(plan.environment.contains("DEXTOP_FLATPAK_COMPAT=1"))
            assertTrue(plan.environment.contains("FONTCONFIG_FILE=/etc/fonts/fonts.conf"))
            assertTrue(plan.environment.any { it.startsWith("LD_LIBRARY_PATH=") && "/usr/local/lib/dextop-flatpak/org.example.App/aliases" in it })
            assertEquals("/usr/lib/aarch64-linux-gnu/libopenal.so.1",
                Files.readSymbolicLink(File(root, "usr/local/lib/dextop-flatpak/org.example.App/aliases/libopenal.so").toPath()).toString().replace('\\', '/'))
            assertTrue(plan.binds.any { it.replace('\\', '/').endsWith("/files/etc/fonts:/etc/fonts") })
            assertTrue(plan.binds.any { it.replace('\\', '/').endsWith("/usr/share/fonts:/run/host/fonts") })
            assertTrue(plan.binds.any { it.endsWith(":/run/host/font-dirs.xml") })
            assertEquals(true, File(root, "root/.var/app/org.example.App/config").isDirectory)
        } finally { root.deleteRecursively() }
    }
}
