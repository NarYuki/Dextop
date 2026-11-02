package moe.n4tsu.dextop.plasma

import kotlin.test.Test
import kotlin.test.assertEquals

class LaunchLogTailTest {
    @Test fun repeatedOutputDoesNotFillTheVisibleLog() {
        val log = LaunchLogTail()
        log.append("ready")
        assertEquals("ready\nnext", log.append("ready\nready\nnext\nnext"))
    }

    @Test fun keepsTheLatestFiveLinesAcrossUpdates() {
        val log = LaunchLogTail()
        log.append("1\n2\n3\n4")
        assertEquals("3\n4\n5\n6\n7", log.append("5\n6\n7"))
    }

    @Test fun removesTerminalFormattingAndEmptyLines() {
        assertEquals("ready\nnext", LaunchLogTail().append("\u001B[32mready\u001B[0m\r\n\nnext\u0000"))
    }

    @Test fun boundsLongOutputAndClearsBetweenLaunches() {
        val log = LaunchLogTail()
        assertEquals(512, log.append("x".repeat(10000)).length)
        log.clear()
        assertEquals("new", log.append("new"))
    }
}
