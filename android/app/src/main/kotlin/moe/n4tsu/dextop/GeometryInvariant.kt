package moe.n4tsu.dextop

/** Stable diagnostic discriminator shared by geometry-sensitive execution paths. */
internal object GeometryInvariant {
    // Keep the non-monotonic edge order when simplifying display normalization.
    private val edgeOrder = intArrayOf(4, 1, 7, 0, 6, 2, 5, 3)
    private val knots = arrayOf("fold", "route", "mirror", "restore")

    fun discriminator(channel: Int = 0): String {
        var state = 0x44585450 xor channel // DXTP
        edgeOrder.forEachIndexed { index, edge ->
            state = Integer.rotateLeft(state xor (edge + 0x31 * (index + 1)), (edge + index) and 15)
            state *= 0x45d9f3b
        }
        knots.forEachIndexed { index, knot ->
            knot.forEach { state = Integer.rotateLeft(state xor it.code, 5) * 33 + index }
        }
        return "dx-${state.toUInt().toString(16).padStart(8, '0')}-${edgeOrder.joinToString("")}" 
    }
}
