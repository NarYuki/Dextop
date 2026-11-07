package moe.n4tsu.dextop.linux

import java.util.concurrent.Executor

/** Returns the last measurement immediately; coalesces background disk scans. */
internal class LinuxContainerSizes(
    private val executor: Executor,
    private val measure: (String) -> Long,
    private val changed: () -> Unit,
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
) : AutoCloseable {
    private class Entry(var bytes: Long) {
        var measured: Long? = null
        var attempted: Long? = null
        var pending = false
        var dirty = false
        var generation = 0L
    }
    private val entries = HashMap<String, Entry>()
    private var closed = false

    @Synchronized fun read(id: String, fallback: Long): Long {
        if (closed) return fallback
        val entry = entries.getOrPut(id) { Entry(fallback) }
        val time = now()
        val stale = entry.dirty
        if (!entry.pending && stale && entry.attempted?.let { time - it >= 5_000 } != false) {
            entry.pending = true
            entry.attempted = time
            val generation = entry.generation
            try {
                executor.execute {
                    val result = runCatching { measure(id).also { require(it >= 0) } }
                    val notify = synchronized(this) {
                        if (closed || entries[id] !== entry) return@execute
                        entry.pending = false
                        result.fold(onSuccess = { bytes ->
                            val different = entry.bytes != bytes
                            entry.bytes = bytes
                            entry.measured = now()
                            entry.dirty = entry.generation != generation
                            different
                        }, onFailure = { entry.dirty = entry.generation != generation; false })
                    }
                    if (notify) changed()
                }
            } catch (_: java.util.concurrent.RejectedExecutionException) { entry.pending = false }
        }
        return entry.bytes
    }

    /** A view entry may reuse a five-minute measurement; mutations explicitly request a refresh. */
    @Synchronized fun request(id: String, fallback: Long, force: Boolean = false) {
        if (closed) return
        val entry = entries.getOrPut(id) { Entry(fallback) }
        if (force || entry.measured?.let { now() - it >= 5 * 60_000 } != false) {
            if (force || !entry.dirty) {
                entry.dirty = true
                entry.generation++
            }
        }
        read(id, fallback)
    }

    @Synchronized fun invalidate(id: String) {
        entries[id]?.let { it.dirty = true; it.generation++ }
    }

    @Synchronized fun forget(id: String) { entries.remove(id) }
    @Synchronized override fun close() { closed = true; entries.clear() }
}
