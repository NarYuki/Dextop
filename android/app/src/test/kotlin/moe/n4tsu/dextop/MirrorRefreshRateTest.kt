package moe.n4tsu.dextop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MirrorRefreshRateTest {
    @Test fun emptyListHasNoRate() {
        assertNull(chooseMirrorRefreshRate(emptyList()))
    }

    @Test fun picksHighestOfSixtyAndOneTwenty() {
        assertEquals(120f, chooseMirrorRefreshRate(listOf(60f, 120f)))
        assertEquals(120f, chooseMirrorRefreshRate(listOf(120f, 60f, 90f)))
    }

    @Test fun singleRateIsReturned() {
        assertEquals(60f, chooseMirrorRefreshRate(listOf(60f)))
    }

    @Test fun zeroAndNegativeRatesAreIgnored() {
        assertEquals(60f, chooseMirrorRefreshRate(listOf(0f, -120f, 60f)))
        assertNull(chooseMirrorRefreshRate(listOf(0f, -1f)))
    }

    @Test fun nonFiniteRatesAreIgnored() {
        assertEquals(90f, chooseMirrorRefreshRate(listOf(Float.NaN, Float.POSITIVE_INFINITY, 90f)))
        assertNull(chooseMirrorRefreshRate(listOf(Float.NaN)))
    }
}
