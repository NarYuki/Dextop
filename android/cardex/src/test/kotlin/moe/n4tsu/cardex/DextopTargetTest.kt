package moe.n4tsu.cardex

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DextopTargetTest {
    private val play = "moe.n4tsu.gpdextop"
    private val github = "moe.n4tsu.dextop"

    @Test fun supportsEachDistribution() {
        for (target in listOf(play, github)) {
            assertEquals(target, DextopTarget.choose({ it == target }, { it == target }))
        }
    }

    @Test fun prefersPlayButFallsBackToWorkingRelay() {
        assertEquals(play, DextopTarget.choose({ true }, { true }))
        assertEquals(github, DextopTarget.choose({ true }, { it == github }))
    }

    @Test fun distinguishesMissingAppFromOldAppWithoutRelay() {
        assertNull(DextopTarget.choose({ false }, { true }))
        assertEquals(play, DextopTarget.choose({ it == play }, { false }))
    }
}
