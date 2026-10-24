package moe.n4tsu.dextop.linux

/** One source for RandR modes, Wayland output timing and capture pacing. */
internal data class LinuxDisplayConfiguration(val width: Int, val height: Int, val dpi: Int, val refreshMilliHz: Int) {
    val refreshRate get() = refreshMilliHz / 1000f
    val frameIntervalNanos get() = 1_000_000_000_000L / refreshMilliHz
    companion object {
        fun create(width: Int, height: Int, dpi: Int, refreshRate: Float) = LinuxDisplayConfiguration(
            width.coerceIn(1, 7680), height.coerceIn(1, 7680), dpi.coerceIn(72, 640),
            (if (refreshRate.isFinite() && refreshRate > 0f) refreshRate.coerceIn(1f, 360f) * 1000 else 60000f).toInt())
    }
}
