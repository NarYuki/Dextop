package moe.n4tsu.dextop.linux

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LinuxAppImageTest {
    /** An ELF64 header whose section headers end at [offset], then [magic] there. */
    private fun image(dir: File, machine: Int, offset: Int = 4096, magic: String = "hsqs"): File {
        val header = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN)
        header.put(0x7f).put('E'.code.toByte()).put('L'.code.toByte()).put('F'.code.toByte()).put(2).put(1)
        header.position(18); header.putShort(machine.toShort())
        // e_shoff + e_shentsize * e_shnum = offset
        header.position(40); header.putLong((offset - 64 * 10).toLong())
        header.position(58); header.putShort(64); header.putShort(10)
        val bytes = ByteArray(offset + 64)
        System.arraycopy(header.array(), 0, bytes, 0, 64)
        System.arraycopy(magic.toByteArray(), 0, bytes, offset, magic.length)
        return File(dir, "test.AppImage").apply { writeBytes(bytes) }
    }

    @Test fun findsTheSquashfsAfterTheRuntime() {
        val dir = Files.createTempDirectory("appimage").toFile()
        try {
            assertEquals(4096L, LinuxAppImage.inspect(image(dir, 183)).offset)
        } finally { dir.deleteRecursively() }
    }

    @Test fun refusesX86AndOtherFormats() {
        val dir = Files.createTempDirectory("appimage-bad").toFile()
        try {
            assertTrue(assertFailsWith<IllegalArgumentException> { LinuxAppImage.inspect(image(dir, 62)) }.message!!.contains("x86-64"))
            assertFailsWith<IllegalArgumentException> { LinuxAppImage.inspect(image(dir, 183, magic = "CD00")) }
            File(dir, "plain").writeText("not an elf at all, padding padding padding padding padding padding")
            assertFailsWith<IllegalArgumentException> { LinuxAppImage.inspect(File(dir, "plain")) }
        } finally { dir.deleteRecursively() }
    }

    @Test fun slugsAreSafeNames() {
        assertEquals("Krita-5.2.6-aarch64", LinuxAppImage.slug("Krita 5.2.6-aarch64.AppImage"))
        assertEquals("app", LinuxAppImage.slug("../.AppImage"))
        assertEquals("a-b", LinuxAppImage.slug("/x/y/a;b.AppImage"))
    }

    @Test fun desktopEntryRunsAppRunWithTheProgramsFieldCodes() {
        val root = Files.createTempDirectory("appimage-root").toFile()
        try {
            val dir = File(root, "home/me/Applications/.Krita").apply { mkdirs() }
            File(dir, "org.kde.krita.desktop").writeText("[Desktop Entry]\nName=Krita\nExec=krita %F\nIcon=krita\nCategories=Graphics;\n[Desktop Action New]\nName=New\n")
            File(dir, "krita.png").writeBytes(byteArrayOf(1))
            File(dir, "chrome-sandbox").writeText("")
            val (name, contents, label) = LinuxAppImage.desktopEntry(root, "/home/me/Applications/.Krita", "/home/me/Applications/Krita.AppImage", "Krita")
            assertEquals("dextop-appimage-Krita.desktop", name)
            assertEquals("Krita", label)
            assertTrue("Exec=env APPDIR=/home/me/Applications/.Krita APPIMAGE=/home/me/Applications/Krita.AppImage /home/me/Applications/.Krita/AppRun --no-sandbox %F" in contents)
            assertTrue("Icon=/home/me/Applications/.Krita/krita.png" in contents)
            assertTrue("Categories=Graphics;" in contents)
        } finally { root.deleteRecursively() }
    }
}
