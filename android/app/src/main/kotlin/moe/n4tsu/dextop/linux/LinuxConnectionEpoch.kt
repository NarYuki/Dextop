package moe.n4tsu.dextop.linux

/** Immutable bind identity: old callbacks cannot change a newer connection or mode switch. */
internal class LinuxConnectionEpoch {
    data class Attempt(val id: Long, val android: Boolean, val embedded: Boolean)
    private var generation = 0L
    @Volatile private var active: Attempt? = null
    @Volatile var switching = false
        private set

    @Synchronized fun begin(android: Boolean, embedded: Boolean): Attempt? {
        if (switching) return null
        return Attempt(++generation, android, embedded).also { active = it }
    }
    @Synchronized fun owns(attempt: Attempt): Boolean = !switching && active === attempt
    @Synchronized fun abandon(attempt: Attempt): Boolean {
        if (!owns(attempt)) return false
        active = null
        generation++
        return true
    }
    @Synchronized fun beginSwitch(): Long? {
        if (switching) return null
        switching = true
        active = null
        return ++generation
    }
    @Synchronized fun ownsSwitch(id: Long): Boolean = switching && generation == id
    @Synchronized fun finishSwitch(id: Long): Boolean {
        if (!ownsSwitch(id)) return false
        switching = false
        return true
    }
}
