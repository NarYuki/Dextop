package moe.n4tsu.dextop.linux

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LinuxWindowIconTest {
    private fun icons(vararg sizes: Int): ByteArray {
        val bytes = ByteBuffer.allocate(sizes.sumOf { 8 + it * it * 4 }).order(ByteOrder.LITTLE_ENDIAN)
        for (size in sizes) {
            bytes.putInt(size).putInt(size)
            repeat(size * size) { bytes.putInt(0x80ff1234.toInt()) }
        }
        return bytes.array()
    }

    @Test fun selectsAnAppropriateImageAndPreservesArgbAlpha() {
        val icon = LinuxWindowIcon.read(icons(16, 256, 128, 32))!!
        assertEquals(128, icon.width)
        assertEquals(128, icon.height)
        assertEquals(0x80ff1234.toInt(), icon.argb.last())
    }

    @Test fun handlesSmallIconsAndAnIncompleteLaterImage() {
        assertEquals(32, LinuxWindowIcon.read(icons(16, 32))?.width)
        val partial = icons(32) + ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(256).putInt(256).array()
        assertEquals(32, LinuxWindowIcon.read(partial)?.width)
    }

    @Test fun rejectsOverflowTruncationAndOversizedProperties() {
        assertNull(LinuxWindowIcon.read(ByteArray(7)))
        assertNull(LinuxWindowIcon.read(ByteArray(LinuxWindowIcon.MAX_PROPERTY_BYTES + 4)))
        for ((w, h) in listOf(Int.MAX_VALUE to Int.MAX_VALUE, -1 to -1, 0 to 32, 512 to 512)) {
            val bytes = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(w).putInt(h).array()
            assertNull(LinuxWindowIcon.read(bytes))
        }
    }

    @Test fun extractsOnlyBoundedEmbeddedPngImagesFromIco() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 13, 10, 26, 10)
        val ico = ByteBuffer.allocate(22 + png.size).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(0).putShort(1).putShort(1)
            .put(0).put(0).put(0).put(0).putShort(1).putShort(32)
            .putInt(png.size).putInt(22).put(png).array()
        assertEquals(png.toList(), LinuxIconDecoder.embeddedIcoPng(ico)?.toList())
        ByteBuffer.wrap(ico).order(ByteOrder.LITTLE_ENDIAN).putInt(18, -1)
        assertNull(LinuxIconDecoder.embeddedIcoPng(ico))
    }
}
