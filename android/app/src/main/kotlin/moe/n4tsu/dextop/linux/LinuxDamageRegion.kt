package moe.n4tsu.dextop.linux

/** Bounded damage list: merge nearby updates, retain distant updates separately. */
internal class LinuxDamageRegion {
    data class Area(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val size get() = (right - left).toLong() * (bottom - top)
        fun union(other: Area) = Area(minOf(left, other.left), minOf(top, other.top), maxOf(right, other.right), maxOf(bottom, other.bottom))
    }
    private val areas = ArrayList<Area>()
    val isEmpty get() = areas.isEmpty()
    fun add(left: Int, top: Int, right: Int, bottom: Int) {
        if (right <= left || bottom <= top) return
        var next = Area(left, top, right, bottom)
        var index = 0
        while (index < areas.size) {
            val old = areas[index]
            val union = next.union(old)
            if (union.size * 4 <= (next.size + old.size) * 5) {
                next = union; areas.removeAt(index); index = 0
            } else index++
        }
        areas += next
        if (areas.size > 8) {
            val combined = areas.reduce { a, b -> a.union(b) }
            areas.clear(); areas += combined
        }
    }
    fun drain(): List<Area> = areas.toList().also { areas.clear() }
}
