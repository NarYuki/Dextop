package moe.n4tsu.dextop.linux

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinuxTerminalStoreTest {
    private fun bytes(text: String) = text.toByteArray()

    @Test fun keepsEverythingBelowTheLimit() {
        val scrollback = LinuxTerminalScrollback(limit = 16)
        scrollback.append(bytes("one\n"), 4)
        scrollback.append(bytes("two\n"), 4)
        assertContentEquals(bytes("one\ntwo\n"), scrollback.snapshot())
    }

    @Test fun droppedOutputStartsAtALine() {
        val scrollback = LinuxTerminalScrollback(limit = 8)
        scrollback.append(bytes("abc\ndef\nghi\n"), 12)
        assertContentEquals(bytes("ghi\n"), scrollback.snapshot())
    }

    @Test fun oversizedChunksKeepTheirEnd() {
        val scrollback = LinuxTerminalScrollback(limit = 4)
        scrollback.append(bytes("0123456789"), 10)
        assertContentEquals(bytes("6789"), scrollback.snapshot())
    }

    @Test fun wrapsAroundTheBuffer() {
        val scrollback = LinuxTerminalScrollback(limit = 6)
        scrollback.append(bytes("ab\ncd"), 5)
        scrollback.append(bytes("ef\ngh"), 5)
        assertContentEquals(bytes("gh"), scrollback.snapshot())
    }

    @Test fun initialOutputIsKept() {
        assertContentEquals(bytes("saved"), LinuxTerminalScrollback(limit = 16, initial = bytes("saved")).snapshot())
    }

    @Test fun stateRoundTrips() {
        val state = LinuxTerminalState(
            listOf(LinuxTerminalTab("t0123456789ab", "bash", "/home/a b"), LinuxTerminalTab("t0123456789ac", "vim", null)),
            "t0123456789ac",
        )
        assertEquals(state, LinuxTerminalState.decode(state.encode()))
    }

    @Test fun theDrawnSizeRoundTrips() {
        val state = LinuxTerminalState(listOf(LinuxTerminalTab("t0123456789ab", "bash", null, 40, 53)), null)
        assertEquals(state, LinuxTerminalState.decode(state.encode()))
    }

    @Test fun unusableSizesAreUnknown() {
        val decoded = LinuxTerminalState.decode("""{"tabs":[{"id":"t0123456789ab","title":"a","rows":1,"cols":100000}]}""")
        assertEquals(0, decoded.tabs.single().rows)
        assertEquals(0, decoded.tabs.single().cols)
    }

    @Test fun unsafeIdsAndUnknownActiveTabsAreDropped() {
        val decoded = LinuxTerminalState.decode("""{"active":"../x","tabs":[{"id":"../x","title":"a"},{"id":"t0123456789ab","title":"b"}]}""")
        assertEquals(listOf("t0123456789ab"), decoded.tabs.map { it.id })
        assertNull(decoded.active)
    }

    @Test fun onlyPlainAbsoluteDirectoriesAreUsed() {
        assertEquals("/home/user", LinuxTerminalState.directory("/home/user"))
        assertNull(LinuxTerminalState.directory("relative"))
        assertNull(LinuxTerminalState.directory("/tmp/a\nrm"))
        assertNull(LinuxTerminalState.directory(null))
    }

    @Test fun storeKeepsTabsAndOutputAcrossInstances() {
        val directory = Files.createTempDirectory("terminal").toFile()
        try {
            val state = LinuxTerminalState(listOf(LinuxTerminalTab("t0123456789ab", "bash", "/root")), "t0123456789ab")
            LinuxTerminalStore(directory).apply { saveState(state); saveOutput("t0123456789ab", bytes("hello")) }
            val again = LinuxTerminalStore(directory)
            assertEquals(state, again.load())
            assertContentEquals(bytes("hello"), again.loadOutput("t0123456789ab"))
            again.remove("t0123456789ab")
            assertEquals(0, again.loadOutput("t0123456789ab").size)
            again.clear()
            assertTrue(again.load().tabs.isEmpty())
        } finally { directory.deleteRecursively() }
    }

    private fun exited(previous: Boolean, text: String) = bytes(text).let { LinuxTerminalExit.update(previous, it, it.size) }
    private val exit = "\u001b]777;dextop-exit\u001b\\"
    private val prompt = "\u001b]7;file://localhost/root\u001b\\"

    @Test fun theShellsExitMarkerEndsTheTab() {
        assertTrue(exited(false, "logout" + exit))
        assertTrue(!exited(false, "exit"))
    }

    @Test fun outputAfterTheMarkerKeepsTheExit() {
        assertTrue(exited(true, "bye from .bash_logout"))
    }

    @Test fun aNestedShellsExitIsClearedByTheOuterPrompt() {
        assertTrue(!exited(false, exit + prompt))
        assertTrue(!exited(true, prompt))
    }

    @Test fun containerFoldersStayInsideTheTerminalFolder() {
        assertEquals("cabc123", LinuxTerminalNames.directory("cabc123"))
        assertEquals("etcpasswd", LinuxTerminalNames.directory("../../etc/passwd"))
        assertEquals("default", LinuxTerminalNames.directory("../"))
    }
}
