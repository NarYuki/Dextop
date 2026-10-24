package moe.n4tsu.dextop.linux

/** Decoration preferences supplied by the client, never guessed from its name. */
internal object LinuxWindowHints {
    fun serverDecorated(motif: IntArray, gtkFrameExtents: IntArray): Boolean {
        if (motif.size >= 3 && motif[0] and 2 != 0) {
            val decorations = motif[2]
            // MWM_DECOR_ALL makes the other bits exclusions.
            return if (decorations and 1 != 0) decorations and 8 == 0 else decorations and 8 != 0
        }
        if (gtkFrameExtents.size == 4 && gtkFrameExtents.all { it >= 0 }) return false
        return true
    }
}
