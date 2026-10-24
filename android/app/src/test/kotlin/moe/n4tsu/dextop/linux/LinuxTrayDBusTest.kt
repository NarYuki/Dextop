package moe.n4tsu.dextop.linux

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class LinuxTrayDBusTest {
    private fun reader(writer: DBusWriter) = DBusReader(ByteBuffer.wrap(writer.bytes()).order(ByteOrder.LITTLE_ENDIAN))
    @Test fun readsAlignedStatusPropertiesAndArgbPixmapArrays() {
        val pixels = byteArrayOf(0x80.toByte(), 0xff.toByte(), 0, 0, 0xff.toByte(), 0, 0xff.toByte(), 0)
        val writer = DBusWriter().apply {
            array(8) {
                align(8); string("Title"); signature("s"); string("\u65e5\u672c\u8a9e")
                align(8); string("ItemIsMenu"); signature("b"); u32(1)
                align(8); string("IconPixmap"); signature("a(iiay)")
                array(8) { align(8); u32(2); u32(1); array(1) { pixels.forEach { byte(it.toInt() and 255) } } }
            }
        }
        val properties = reader(writer).value("a{sv}") as Map<*, *>
        assertEquals("\u65e5\u672c\u8a9e", properties["Title"])
        assertEquals(true, properties["ItemIsMenu"])
        val image = (properties["IconPixmap"] as List<*>).single() as List<*>
        assertEquals(2, image[0]); assertEquals(1, image[1])
        assertContentEquals(pixels, image[2] as ByteArray)
    }
    @Test fun readsNestedDbusMenuVariantsWithoutLosingAlignment() {
        val writer = DBusWriter().apply {
            u32(7)
            align(8); u32(0); array(8) {}
            array(1) {
                signature("(ia{sv}av)"); align(8); u32(42)
                array(8) { align(8); string("label"); signature("s"); string("_Open"); align(8); string("enabled"); signature("b"); u32(1) }
                array(1) {}
            }
        }
        val reader = reader(writer)
        assertEquals(7, reader.u32())
        val root = reader.value("(ia{sv}av)") as List<*>
        val child = (root[2] as List<*>).single() as List<*>
        assertEquals(42, child[0])
        assertEquals("_Open", (child[1] as Map<*, *>)["label"])
        assertEquals(emptyList<Any>(), child[2])
    }
    @Test fun rejectsOversizedAndTruncatedArrays() {
        assertFailsWith<IllegalArgumentException> { reader(DBusWriter().apply { u32(4_194_305) }).value("ay") }
        assertFailsWith<IllegalArgumentException> { reader(DBusWriter().apply { u32(8); byte(1) }).value("ay") }
    }
}
