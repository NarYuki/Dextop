package moe.n4tsu.dextop.linux

/** PCM queue limits independent of Android's conservative normal-mixer minimum. */
internal object LinuxAudioBuffering {
    const val RATE = 48_000
    const val FRAME_BYTES = 4 // stereo s16le
    const val READ_BYTES = RATE * FRAME_BYTES * 5 / 1000
    const val RECEIVE_BYTES = RATE * FRAME_BYTES * 20 / 1000
    const val SOCKET_BUFFER_BYTES = RATE * FRAME_BYTES * 80 / 1000
    private const val MAX_BACKLOG_BYTES = RATE * FRAME_BYTES * 60 / 1000

    fun burstFrames(property: String?): Int = property?.toIntOrNull()?.takeIf { it in 48..1920 } ?: 240
    fun targetFrames(burst: Int): Int = (burst * 2).coerceAtLeast(RATE / 100)
    fun capacityFrames(burst: Int): Int = maxOf(burst * 4, targetFrames(burst))

    /** Drop only whole frames, preserving channel/sample alignment after TCP partial reads. */
    fun staleBytes(available: Int): Int = if (available > MAX_BACKLOG_BYTES)
        (available - RECEIVE_BYTES) / FRAME_BYTES * FRAME_BYTES else 0
}
