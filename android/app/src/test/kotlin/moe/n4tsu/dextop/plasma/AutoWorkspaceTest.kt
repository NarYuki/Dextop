package moe.n4tsu.dextop.plasma

import kotlin.test.*

class AutoWorkspaceTest {
    @Test fun selectingSecondAppKeepsFirstAndDoesNotDuplicateAnExistingApp() {
        val w = AutoWorkspace(); w.select(1); w.enableSplit(); w.select(2)
        assertEquals(1, w.primary); assertEquals(2, w.secondary)
        w.select(1, 1)
        assertEquals(1, w.primary); assertEquals(2, w.secondary); assertEquals(0, w.focusedPane)
    }
    @Test fun closingPrimaryPromotesSecondaryAndClosingBackgroundDoesNothing() {
        val w = AutoWorkspace(); w.select(1); w.enableSplit(); w.select(2)
        w.remove(99); assertEquals(1, w.primary)
        w.remove(1); assertEquals(2, w.primary); assertNull(w.secondary); assertEquals(0, w.focusedPane)
    }
    @Test fun fullscreenPreservesFocusedAppAndSwappingPreservesFocusAndItsSpace() {
        val w = AutoWorkspace(); w.select(1); w.enableSplit(); w.select(2); w.resize(.3f)
        w.swap(); assertEquals(2, w.primary); assertEquals(0, w.focusedPane); assertEquals(.7f, w.ratio)
        w.focus(1); w.single(); assertEquals(1, w.primary); assertFalse(w.split); assertNull(w.secondary)
    }
    @Test fun panesCoverLandscapeAndPortraitWithoutOverlapAtAllRatios() {
        val w = AutoWorkspace(); w.enableSplit()
        for ((width,height) in listOf(1920 to 720, 720 to 1280)) {
            for (ratio in listOf(.3f,.5f,.7f)) {
                w.resize(ratio); val (a,b) = w.bounds(width,height,24)
                assertTrue(a.width > 0 && a.height > 0 && b.width > 0 && b.height > 0)
                if (width >= height) {
                    assertEquals(24,b.left-a.right); assertEquals(width,b.right); assertEquals(height,a.bottom)
                } else {
                    assertEquals(24,b.top-a.bottom); assertEquals(height,b.bottom); assertEquals(width,a.right)
                }
            }
        }
    }
    @Test fun invalidRatiosAndTinyViewportsDoNotCrash() {
        val w = AutoWorkspace(); w.enableSplit(); w.resize(Float.NaN); assertEquals(.5f,w.ratio)
        w.resize(2f); assertEquals(.7f,w.ratio)
        w.bounds(1,1,30); w.bounds(0,0,30)
    }
}
