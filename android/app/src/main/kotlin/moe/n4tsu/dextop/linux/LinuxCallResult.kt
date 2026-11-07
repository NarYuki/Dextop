package moe.n4tsu.dextop.linux

/** A stale Binder result still owns its resources even though its UI no longer accepts it. */
internal object LinuxCallResult {
    fun <T> deliver(result: T, current: Boolean, accepted: (T) -> Unit, stale: () -> Unit) {
        if (current) accepted(result)
        else {
            runCatching { (result as? AutoCloseable)?.close() }
            stale()
        }
    }
}
