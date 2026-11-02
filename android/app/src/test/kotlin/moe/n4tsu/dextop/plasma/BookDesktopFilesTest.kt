package moe.n4tsu.dextop.plasma

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class BookDesktopFilesTest {
    @Test fun snapshotRoundTripPreservesNamesAndEmptyDirectories() {
        val files = listOf("folder 日本語\n@; '" to true, "z file" to false)
        assertEquals(files, BookDesktopFiles.parse(BookDesktopFiles.encode(files)))
        assertEquals(emptyList(), BookDesktopFiles.parse(BookDesktopFiles.encode(emptyList())))
    }

    @Test fun preservesUnicodeAndRecordCharactersInNames() {
        val name = "日本語\nfolder ' & space"
        val encoded = Base64.getEncoder().encodeToString(name.toByteArray())
        assertEquals(listOf(name to true), BookDesktopFiles.parse("d/$encoded\nDEXTOP_DESKTOP_END\n"))
    }

    @Test fun failedOrPartialReadsCannotClearTheDesktop() {
        assertNull(BookDesktopFiles.parse("permission denied"))
        assertNull(BookDesktopFiles.parse("f/YQ==\n"))
        assertNull(BookDesktopFiles.parse("f/!invalid\nDEXTOP_DESKTOP_END\n"))
        assertEquals(emptyList(), BookDesktopFiles.parse("DEXTOP_DESKTOP_END\n"))
    }

    @Test fun rejectsPathsOutsideTheDesktop() {
        listOf("", ".", "..", "a/b", "a\u0000b").forEach { assertFalse(BookDesktopFiles.validName(it)) }
        assertNull(BookDesktopFiles.parse("d/Li4=\nDEXTOP_DESKTOP_END\n"))
    }
}
