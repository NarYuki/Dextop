package moe.n4tsu.dextop.linux

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class LinuxDocumentMoveTest {
    private class Provider {
        val files = mutableMapOf("old/source" to "new data", "new/target" to "old data")
        var failMove = false
        var failRename = false
        var failRestore = false
        var throwMove = false
        fun relocate(from: String, to: String): String {
            check(!files.containsKey(to))
            files[to] = checkNotNull(files.remove(from))
            return to
        }
        fun replace(): Int = LinuxDocumentMove.replace(
            source = "old/source", destination = "new/target",
            backup = { relocate(it, "new/backup") },
            move = {
                if (throwMove) error("provider unavailable")
                if (failMove) null else relocate(it, "new/source")
            },
            rename = { if (failRename) null else relocate(it, "new/target") },
            undoMove = { relocate(it, "old/source") },
            restore = { if (failRestore) error("restore refused") else relocate(it, "new/target") },
            discard = {
                assertEquals("new data", files["new/target"])
                files.remove(it)
            },
        )
    }

    @Test fun failedMoveRetainsBothFiles() {
        val provider = Provider().apply { failMove = true }
        assertEquals(18, provider.replace())
        assertEquals(mapOf("old/source" to "new data", "new/target" to "old data"), provider.files)
    }

    @Test fun failedRenameRollsSourceBackBeforeRestoringDestination() {
        val provider = Provider().apply { failRename = true }
        assertEquals(5, provider.replace())
        assertEquals(mapOf("old/source" to "new data", "new/target" to "old data"), provider.files)
    }

    @Test fun exceptionAlsoRestoresDestination() {
        val provider = Provider().apply { throwMove = true }
        assertFailsWith<IllegalStateException> { provider.replace() }
        assertEquals(mapOf("old/source" to "new data", "new/target" to "old data"), provider.files)
    }

    @Test fun rollbackFailureRetainsBackupData() {
        val provider = Provider().apply { failRename = true; failRestore = true }
        assertEquals(5, provider.replace())
        assertEquals("old data", provider.files["new/backup"])
        assertEquals("new data", provider.files["old/source"])
    }

    @Test fun successDeletesBackupOnlyAfterCommit() {
        val provider = Provider()
        assertEquals(0, provider.replace())
        assertEquals(mapOf("new/target" to "new data"), provider.files)
        assertFalse(provider.files.containsKey("new/backup"))
    }
}
