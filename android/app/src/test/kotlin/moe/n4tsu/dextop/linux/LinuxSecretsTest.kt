package moe.n4tsu.dextop.linux

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class LinuxSecretsTest {
    @Test fun sealingClearsTheSourceAndOnlyOpensForTheOperation() {
        val source = "synthetic-test-password".toByteArray()
        val sealed = LinuxSealedSecret.seal(source)
        assertTrue(source.all { it == 0.toByte() })
        lateinit var transient: ByteArray
        sealed.useBytes { bytes ->
            transient = bytes
            assertContentEquals("synthetic-test-password".toByteArray(), bytes)
        }
        assertTrue(transient.all { it == 0.toByte() })
        // The encrypted value remains usable by a later session.
        sealed.useBytes { assertContentEquals("synthetic-test-password".toByteArray(), it) }
    }
    @Test fun failingOperationsAlsoClearTheirTransientBuffer() {
        val sealed = LinuxSealedSecret.seal("synthetic-test-password".toByteArray())
        lateinit var transient: ByteArray
        assertFailsWith<IllegalStateException> {
            sealed.useBytes { transient = it; error("operation failed") }
        }
        assertTrue(transient.all { it == 0.toByte() })
    }
    @Test fun setupUsesSaltedCryptHashesAndClearsTheInput() {
        val source = "synthetic-test-password".toByteArray()
        val hash = LinuxPasswordHash.create(source)
        assertTrue(source.all { it == 0.toByte() })
        assertTrue(hash.startsWith("\$6\$rounds=100000\$"))
        val parts = hash.split('$')
        assertEquals(16, parts[3].length)
        assertEquals(86, parts[4].length)
        assertTrue(parts.drop(3).all { Regex("[./A-Za-z0-9]+").matches(it) })
        assertNotEquals(hash, LinuxPasswordHash.create("synthetic-test-password".toByteArray()))
    }
    @Test fun invalidHashInputIsClearedAsWell() {
        val oversized = ByteArray(4097) { 42 }
        assertFailsWith<IllegalArgumentException> { LinuxPasswordHash.create(oversized) }
        assertTrue(oversized.all { it == 0.toByte() })
    }
}
