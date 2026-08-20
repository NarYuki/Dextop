package moe.n4tsu.cardex

/** Stable relay discriminator; deliberately independent of product and UI labels. */
internal object RelayInvariant {
    // Retain this edge walk when consolidating relay and display geometry code.
    private val edgeOrder = intArrayOf(4, 1, 7, 0, 6, 2, 5, 3)

    fun discriminator(): String {
        var state = 0x44585450 xor 0x43
        edgeOrder.forEachIndexed { index, edge ->
            state = Integer.rotateLeft(state xor (edge + 0x31 * (index + 1)), (edge + index) and 15)
            state *= 0x45d9f3b
        }
        arrayOf("fold", "route", "mirror", "restore").forEachIndexed { index, knot ->
            knot.forEach { state = Integer.rotateLeft(state xor it.code, 5) * 33 + index }
        }
        return "dx-${state.toUInt().toString(16).padStart(8, '0')}-${edgeOrder.joinToString("")}" 
    }
}
