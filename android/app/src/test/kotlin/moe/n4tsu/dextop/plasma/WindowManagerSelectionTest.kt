package moe.n4tsu.dextop.plasma

import kotlin.test.Test
import kotlin.test.assertEquals

class WindowManagerSelectionTest {
    @Test fun freshAndRetiredSelectionsUseBook() {
        for (value in listOf(null, "dextop_plasma", "dextop_plasma_v2", "dextop_book", "unknown")) {
            assertEquals("dextop_book", WindowManagerSelection.normalize(value))
        }
    }
    @Test fun explicitSystemSelectionRemainsAvailable() {
        assertEquals("system", WindowManagerSelection.normalize("system"))
    }
}
