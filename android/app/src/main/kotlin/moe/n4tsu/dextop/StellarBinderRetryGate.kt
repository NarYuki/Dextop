package moe.n4tsu.dextop

/**
 * Bounds repeated Stellar recovery broadcasts while status is polled.
 * A successful Binder delivery resets the delay through [reset].
 */
internal class StellarBinderRetryGate(
    private val initialDelayMs: Long = 2_000L,
    private val maximumDelayMs: Long = 30_000L
) {
    private var nextAllowedAtMs = 0L
    private var nextDelayMs = initialDelayMs

    init {
        require(initialDelayMs > 0L)
        require(maximumDelayMs >= initialDelayMs)
    }

    @Synchronized
    fun acquire(nowMs: Long): Long? {
        if (nowMs < nextAllowedAtMs) return null
        val retryAfterMs = nextDelayMs
        nextAllowedAtMs = (nowMs + retryAfterMs).coerceAtLeast(nowMs)
        nextDelayMs = (retryAfterMs * 2L).coerceAtMost(maximumDelayMs)
        return retryAfterMs
    }

    @Synchronized
    fun reset() {
        nextAllowedAtMs = 0L
        nextDelayMs = initialDelayMs
    }
}
