package moe.n4tsu.dextop.linux

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LinuxRuntimeTest {
    @Test fun sparseLogFilesDoNotHideErrorsBehindNullCharacters() {
        assertEquals("\nSingletonSocket: Permission denied\n", LinuxRuntime.readable("\u0000\u0000\nSingletonSocket: Permission denied\n\u0000"))
    }
    @Test fun avoidsAbstractAndFilesystemSocketsLeftByOldServices() {
        val sockets = "0000: 00000002 00000000 00010000 0001 01 123 @/tmp/.X11-unix/X10\n" +
            "0000: 00000002 00000000 00010000 0001 01 124 /tmp/.X11-unix/X11\n"
        assertEquals(13, LinuxRuntime.freeDisplay(setOf(12), sockets))
    }
    @Test fun socketNumbersAreMatchedExactly() {
        assertEquals(10, LinuxRuntime.freeDisplay(emptySet(), "000: @/tmp/.X11-unix/X100\n"))
    }
    @Test fun reportsDisplayExhaustion() {
        assertThrows(IllegalStateException::class.java) { LinuxRuntime.freeDisplay((10..63).toSet(), "") }
    }
}
