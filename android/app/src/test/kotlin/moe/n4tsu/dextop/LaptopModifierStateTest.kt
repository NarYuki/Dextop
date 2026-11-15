package moe.n4tsu.dextop

import android.view.KeyEvent
import moe.n4tsu.dextop.input.LaptopSwipeDecoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LaptopModifierStateTest {
    private val shift = LaptopKeyboardLayout.SHIFT
    private val control = LaptopKeyboardLayout.CONTROL
    private val timeout = 300L

    private fun LaptopModifierState.tap(keyCode: Int, at: Long) {
        assertTrue(press(keyCode))
        assertTrue(release(keyCode, at, timeout))
    }

    @Test fun tappedShiftLatchesForOneKey() {
        val state = LaptopModifierState()
        state.tap(shift, 1_000)
        assertTrue(state.shift)
        assertEquals(KeyEvent.META_SHIFT_ON, state.metaState(KeyEvent.KEYCODE_A))
        state.consumeForKeyPress(usedSymbol = false)
        assertFalse(state.shiftActive)
        assertEquals(0, state.metaState(KeyEvent.KEYCODE_A))
    }

    @Test fun doubleTapLocksShiftAndThirdTapUnlocks() {
        val state = LaptopModifierState()
        state.tap(shift, 1_000)
        state.tap(shift, 1_200)
        assertTrue(state.shiftLocked)
        state.consumeForKeyPress(usedSymbol = false)
        assertTrue(state.shiftActive)
        state.tap(shift, 5_000)
        assertFalse(state.shiftActive)
    }

    @Test fun slowSecondTapUnlatchesInsteadOfLocking() {
        val state = LaptopModifierState()
        state.tap(shift, 1_000)
        state.tap(shift, 2_000)
        assertFalse(state.shiftActive)
    }

    @Test fun heldModifierSurvivesKeysAndClearsOnRelease() {
        val state = LaptopModifierState()
        assertTrue(state.press(control))
        assertFalse(state.press(control))
        state.consumeForKeyPress(usedSymbol = false)
        state.consumeForKeyPress(usedSymbol = false)
        assertEquals(KeyEvent.META_CTRL_ON, state.metaState(KeyEvent.KEYCODE_C))
        assertTrue(state.release(control, 1_000, timeout))
        assertFalse(state.control)
        assertFalse(state.release(control, 1_000, timeout))
    }

    @Test fun capsLockInvertsShiftForLettersOnly() {
        val state = LaptopModifierState()
        assertTrue(state.toggle(LaptopKeyboardLayout.CAPS))
        assertEquals(KeyEvent.META_SHIFT_ON, state.metaState(KeyEvent.KEYCODE_A))
        assertEquals(0, state.metaState(KeyEvent.KEYCODE_1))
        state.tap(shift, 1_000)
        assertEquals(0, state.metaState(KeyEvent.KEYCODE_A))
        assertEquals(KeyEvent.META_SHIFT_ON, state.metaState(KeyEvent.KEYCODE_1))
    }

    @Test fun symbolLatchIsSpentOnlyByASymbolKey() {
        val state = LaptopModifierState()
        state.tap(LaptopKeyboardLayout.SYM, 1_000)
        state.consumeForKeyPress(usedSymbol = false)
        assertTrue(state.symbolMode)
        state.consumeForKeyPress(usedSymbol = true)
        assertFalse(state.symbolMode)
    }

    @Test fun visualBitsChangeOnlyWithVisibleState() {
        val state = LaptopModifierState()
        val idle = state.visualBits
        state.consumeForKeyPress(usedSymbol = false)
        assertEquals(idle, state.visualBits)
        state.tap(shift, 1_000)
        val latched = state.visualBits
        assertFalse(idle == latched)
        state.tap(shift, 1_100)
        assertFalse(latched == state.visualBits)
        state.reset()
        assertEquals(idle, state.visualBits)
    }

    @Test fun nonModifierIsNotToggled() {
        val state = LaptopModifierState()
        assertFalse(state.toggle(LaptopKeyboardLayout.BACK))
        assertFalse(state.toggle(KeyEvent.KEYCODE_A))
        assertEquals(0, state.visualBits)
    }

    @Test fun legendsFollowShiftAndCaps() {
        val state = LaptopModifierState()
        assertEquals("a", LaptopKeyLegends.displayed("a", state))
        state.toggle(LaptopKeyboardLayout.CAPS)
        assertEquals("A", LaptopKeyLegends.displayed("a", state))
        assertEquals("1", LaptopKeyLegends.displayed("1", state))
        state.tap(shift, 1_000)
        assertEquals("!", LaptopKeyLegends.displayed("1", state))
        assertEquals("Tab", LaptopKeyLegends.displayed("Tab", state))
    }

    @Test fun swipeSymbolsFollowTheLanguageLayout() {
        assertEquals('q', LaptopKeyLegends.swipeSymbol(KeyEvent.KEYCODE_Q, LaptopSwipeDecoder.Language.EN))
        assertEquals('й', LaptopKeyLegends.swipeSymbol(KeyEvent.KEYCODE_Q, LaptopSwipeDecoder.Language.RU))
        assertEquals('і', LaptopKeyLegends.swipeSymbol(KeyEvent.KEYCODE_S, LaptopSwipeDecoder.Language.UK))
        assertEquals('ㅡ', LaptopKeyLegends.swipeSymbol(KeyEvent.KEYCODE_M, LaptopSwipeDecoder.Language.KO))
        assertNull(LaptopKeyLegends.swipeSymbol(KeyEvent.KEYCODE_1, LaptopSwipeDecoder.Language.EN))
    }

    @Test fun blackBerrySymbolsCarryTheirShiftState() {
        assertEquals(KeyEvent.KEYCODE_3 to true, LaptopKeyLegends.blackBerrySymbolKey(KeyEvent.KEYCODE_Q))
        assertEquals(KeyEvent.KEYCODE_PERIOD to false, LaptopKeyLegends.blackBerrySymbolKey(KeyEvent.KEYCODE_M))
        assertNull(LaptopKeyLegends.blackBerrySymbolKey(KeyEvent.KEYCODE_SPACE))
    }
}
