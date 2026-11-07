package moe.n4tsu.dextop.linux

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class XvfbFramebufferTest {
    @Test fun rejectsMalformedLayoutsAndOverflow() {
        val directory = Files.createTempDirectory("xwd-validation").toFile()
        try {
            fun frame(headerSize: Int = 100, colors: Int = 0, stride: Int = 16, width: Int = 4, height: Int = 2): File {
                val bytes = ByteBuffer.allocate(132).order(ByteOrder.BIG_ENDIAN)
                bytes.putInt(0, headerSize).putInt(8, 2).putInt(16, width).putInt(20, height)
                bytes.putInt(44, 32).putInt(48, stride).putInt(76, colors)
                return File(directory, "frame-${directory.list()!!.size}").apply { writeBytes(bytes.array()) }
            }
            assertNotNull(XvfbFramebuffer.open(frame()))
            assertNull(XvfbFramebuffer.open(frame(headerSize = -16)))
            assertNull(XvfbFramebuffer.open(frame(colors = -1)))
            assertNull(XvfbFramebuffer.open(frame(colors = Int.MAX_VALUE)))
            assertNull(XvfbFramebuffer.open(frame(stride = -16)))
            assertNull(XvfbFramebuffer.open(frame(stride = 8)))
            assertNull(XvfbFramebuffer.open(frame(width = Int.MAX_VALUE)))
            assertNull(XvfbFramebuffer.open(frame(height = Int.MAX_VALUE)))
        } finally {
            directory.deleteRecursively()
        }
    }
}
