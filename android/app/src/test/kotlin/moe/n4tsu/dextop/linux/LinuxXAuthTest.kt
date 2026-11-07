package moe.n4tsu.dextop.linux

import kotlin.test.Test
import kotlin.test.assertContentEquals

class LinuxXAuthTest {
    @Test fun authorityIsOneWildMitMagicCookieEntry() {
        val cookie = ByteArray(16) { it.toByte() }
        val name = "MIT-MAGIC-COOKIE-1".toByteArray()
        val expected = byteArrayOf(0xff.toByte(), 0xff.toByte(), 0, 0, 0, 0, 0, name.size.toByte()) + name +
            byteArrayOf(0, 16) + cookie
        assertContentEquals(expected, LinuxXAuth.authority(cookie))
    }
    @Test fun replacesOldCookieWithoutAccumulatingRecords() {
        val old = LinuxXAuth.authority(ByteArray(16) { 1 })
        val newCookie = ByteArray(16) { 2 }
        val merged = LinuxXAuth.mergeAuthority(old, newCookie)
        assertContentEquals(LinuxXAuth.authority(newCookie), merged)
        assertContentEquals(merged, LinuxXAuth.mergeAuthority(merged, newCookie))
    }

    @Test fun preservesUserDisplayRecords() {
        val entry = LinuxXAuth.authority(ByteArray(16) { 3 }).also { it[0] = 0; it[1] = 0 }
        val cookie = ByteArray(16) { 4 }
        assertContentEquals(LinuxXAuth.authority(cookie) + entry,
            LinuxXAuth.mergeAuthority(entry + LinuxXAuth.authority(ByteArray(16)), cookie))
    }

    @Test fun rejectsTruncatedAuthority() {
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            LinuxXAuth.mergeAuthority(byteArrayOf(-1, -1, 0, 20), ByteArray(16))
        }
    }
}
