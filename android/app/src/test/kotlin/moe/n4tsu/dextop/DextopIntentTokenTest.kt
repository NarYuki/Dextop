package moe.n4tsu.dextop

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DextopIntentTokenTest {
    @Test
    fun acceptsOnlyTheExactToken() {
        assertTrue(DextopIntentToken.matches("secret", "secret"))
        assertFalse(DextopIntentToken.matches("secret", "Secret"))
        assertFalse(DextopIntentToken.matches("secret", "secret2"))
    }

    @Test
    fun rejectsIntentsWithoutAToken() {
        assertFalse(DextopIntentToken.matches("secret", null))
        assertFalse(DextopIntentToken.matches("secret", ""))
    }
}
