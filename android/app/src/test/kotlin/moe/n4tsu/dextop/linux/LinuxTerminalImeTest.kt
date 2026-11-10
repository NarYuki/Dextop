package moe.n4tsu.dextop.linux

import kotlin.test.Test
import kotlin.test.assertEquals

class LinuxTerminalImeTest {
    private val sent = StringBuilder()
    private var shown = ""
    private val ime = LinuxTerminalIme({ sent.append(it) }, { shown = it })
    private val backspace = "\u007f"

    private fun typeLetters(word: String) = word.forEach { ime.commit(it.toString()) }

    @Test fun anAutocorrectionReplacesTheWordInsteadOfAddingToIt() {
        typeLetters("claude")
        ime.region(0, 6)
        ime.commit("Claude")
        assertEquals("claude" + backspace.repeat(6) + "Claude", sent.toString())
    }

    @Test fun onlyTheCorrectedWordIsReplaced() {
        typeLetters("git statsu")
        ime.region(4, 10)
        ime.commit("status")
        assertEquals("git statsu" + backspace.repeat(6) + "status", sent.toString())
    }

    @Test fun aRegionFromAnEarlierLineMeansTheEndOfTheCurrentOne() {
        typeLetters("ls")
        ime.commit("\n")
        typeLetters("claude")
        ime.region(3, 9)
        ime.commit("Claude")
        assertEquals("ls\rclaude" + backspace.repeat(6) + "Claude", sent.toString())
    }

    @Test fun aRegionNeverRemovesMoreThanTheKeyboardTyped() {
        typeLetters("ab")
        ime.region(0, 40)
        ime.commit("x")
        assertEquals("abx", sent.toString())
    }

    @Test fun aRegionBeforeTheCursorIsLeftAlone() {
        typeLetters("abcd")
        ime.region(0, 2)
        ime.commit("x")
        assertEquals("abcdx", sent.toString())
    }

    @Test fun composingIsOnlySentWhenCommitted() {
        ime.compose("にほん")
        assertEquals("", sent.toString())
        assertEquals("にほん", shown)
        ime.commit("日本")
        assertEquals("日本", sent.toString())
        assertEquals("", shown)
    }

    @Test fun finishingKeepsTheComposingText() {
        ime.compose("hello")
        ime.finish()
        assertEquals("hello", sent.toString())
        ime.finish()
        assertEquals("hello", sent.toString())
    }

    @Test fun recomposingAWordReplacesItWhenFinished() {
        typeLetters("teh")
        ime.region(0, 3)
        ime.compose("the")
        ime.finish()
        assertEquals("teh" + backspace.repeat(3) + "the", sent.toString())
    }

    @Test fun deletionCountsCharactersNotCodeUnits() {
        ime.commit("a😀")
        ime.delete(2)
        ime.region(0, 1)
        ime.commit("b")
        assertEquals("a😀$backspace${backspace}b", sent.toString())
    }

    @Test fun deletionPastWhatWasTypedStillPressesBackspace() {
        ime.delete(3)
        assertEquals(backspace.repeat(3), sent.toString())
    }

    @Test fun anEnterEndsTheLine() {
        typeLetters("ls")
        ime.commit("\n")
        ime.region(0, 2)
        ime.commit("x")
        assertEquals("ls\rx", sent.toString())
    }

    @Test fun keysKeepTheLineInStep() {
        typeLetters("ab")
        ime.key('c'.code, backspace = false)
        ime.key(0, backspace = true)
        ime.region(0, 2)
        ime.commit("x")
        assertEquals("ab" + backspace.repeat(2) + "x", sent.toString())
        ime.key(0, backspace = false)
        ime.region(0, 1)
        ime.commit("y")
        assertEquals("ab" + backspace.repeat(2) + "xy", sent.toString())
    }
}
