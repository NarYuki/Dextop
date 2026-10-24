package moe.n4tsu.dextop.linux

import java.util.concurrent.Executor

/** One worker and only the latest pending geometry per Linux display. */
internal class LinuxDisplayUpdates(private val executor: Executor, private val apply: (String, Boolean, LinuxDisplayConfiguration) -> Unit) {
    private val pending = LinkedHashMap<Pair<String, Boolean>, LinuxDisplayConfiguration>()
    private var scheduled = false
    private var closed = false
    @Synchronized fun offer(id: String, apps: Boolean, config: LinuxDisplayConfiguration) {
        if (closed) return
        pending[id to apps] = config
        if (scheduled) return
        scheduled = true
        try { executor.execute(::drain) } catch (error: java.util.concurrent.RejectedExecutionException) {
            scheduled = false; pending.clear()
        }
    }
    private fun drain() {
        while (true) {
            val item = synchronized(this) {
                if (closed || pending.isEmpty()) { scheduled = false; return }
                val entry = pending.entries.first()
                (entry.key to entry.value).also { pending.remove(entry.key) }
            }
            try { apply(item.first.first, item.first.second, item.second) }
            catch (_: Exception) { /* The owner reports command errors; keep the worker available. */ }
        }
    }
    @Synchronized fun close() { closed = true; pending.clear() }
}
