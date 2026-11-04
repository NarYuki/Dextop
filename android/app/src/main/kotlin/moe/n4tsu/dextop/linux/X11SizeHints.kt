package moe.n4tsu.dextop.linux

/** ICCCM WM_SIZE_HINTS uses 32-bit fields, including on 64-bit clients. */
internal object X11SizeHints {
    fun fixedSize(hints: IntArray): Boolean {
        if (hints.size < 9 || hints[0] and ((1 shl 4) or (1 shl 5)) != ((1 shl 4) or (1 shl 5))) return false
        return hints[5] > 0 && hints[6] > 0 && hints[5] == hints[7] && hints[6] == hints[8]
    }
}
