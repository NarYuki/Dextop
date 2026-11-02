package moe.n4tsu.dextop.linux

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LinuxPointerCursorTest {
    private fun image(vararg pixels: Int): ByteBuffer = ByteBuffer.allocate(32 + pixels.size * 4)
        .order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(12, pixels.size.toShort()); putShort(14, 1)
            pixels.forEachIndexed { i, value -> putInt(32 + i * 4, value) }
        }
    @Test fun ordinaryAndPartlyVisibleCursorsDoNotCapture() {
        assertFalse(LinuxPointerCursor.hidden(image(0xff000000.toInt())))
        assertFalse(LinuxPointerCursor.hidden(image(0, 0x01000000)))
    }
    @Test fun TransparentGameCursorAllowsCapture() {
        assertTrue(LinuxPointerCursor.hidden(image(0, 0, 0)))
    }
    @Test fun missingAndTruncatedImagesFailWithoutCapture() {
        assertFalse(LinuxPointerCursor.hidden(ByteBuffer.allocate(4)))
        assertFalse(LinuxPointerCursor.hidden(image()))
        assertFalse(LinuxPointerCursor.hidden(image(0).apply { putShort(12, 20) }))
    }
}
