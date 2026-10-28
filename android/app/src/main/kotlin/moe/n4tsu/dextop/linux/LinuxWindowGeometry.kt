package moe.n4tsu.dextop.linux

/** Wayland positions belong to the host; only popup positions are client-relative. */
internal class LinuxWindowGeometry {
    data class Bounds(val left: Int, val top: Int, val width: Int, val height: Int)
    private data class Window(var bounds: Bounds, val popup: Boolean, val parent: Int, val x: Int, val y: Int)
    private val windows = HashMap<Int, Window>()

    @Synchronized operator fun get(id: Int): Bounds? = windows[id]?.bounds

    @Synchronized fun report(id: Int, parent: Int, popup: Boolean, x: Int, y: Int, width: Int, height: Int): Bounds {
        val old = windows[id]?.bounds
        val origin = windows[parent]?.bounds
        val bounds = if (popup) Bounds((origin?.left ?: 0) + x, (origin?.top ?: 0) + y, width, height)
            else Bounds(old?.left ?: 0, old?.top ?: 0, width, height)
        windows[id] = Window(bounds, popup, parent, x, y)
        return bounds
    }

    /** Moving a parent moves nested menus too, without waiting for another client commit. */
    @Synchronized fun place(id: Int, x: Int, y: Int, width: Int, height: Int): Map<Int, Bounds> {
        val old = windows[id]
        if (old == null) windows[id] = Window(Bounds(x, y, width, height), false, 0, 0, 0)
        else old.bounds = old.bounds.copy(left = x, top = y)
        val moved = LinkedHashMap<Int, Bounds>()
        fun children(parent: Int, depth: Int) {
            if (depth > 64) return
            val origin = windows[parent]?.bounds ?: return
            windows.filterValues { it.popup && it.parent == parent }.forEach { (child, window) ->
                window.bounds = window.bounds.copy(left = origin.left + window.x, top = origin.top + window.y)
                moved[child] = window.bounds
                children(child, depth + 1)
            }
        }
        children(id, 0)
        return moved
    }

    @Synchronized fun remove(id: Int) { windows.remove(id) }
    @Synchronized fun clear() { windows.clear() }
}
