package moe.n4tsu.dextop.linux

/** Preserve subpixel hardware deltas instead of dropping slow mouse movements. */
internal class LinuxRelativeMotion {
    private var x = 0.0
    private var y = 0.0
    fun add(dx: Float, dy: Float): Pair<Int, Int> {
        if (!dx.isFinite() || !dy.isFinite()) return 0 to 0
        x += dx; y += dy
        val wholeX = x.toInt(); val wholeY = y.toInt()
        x -= wholeX; y -= wholeY
        return wholeX to wholeY
    }
    fun reset() { x = 0.0; y = 0.0 }
}
