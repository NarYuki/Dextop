package moe.n4tsu.dextop.plasma

/** Visible panes are independent of the lifetime of background application displays. */
internal class AutoWorkspace {
    var primary: Int? = null
        private set
    var secondary: Int? = null
        private set
    var focusedPane = 0
        private set
    var split = false
        private set
    var ratio = .5f
        private set

    fun select(id: Int, pane: Int = focusedPane) {
        if (id == primary) { focusedPane = 0; return }
        if (id == secondary && split) { focusedPane = 1; return }
        if (split && pane == 1 && primary != null) { secondary = id; focusedPane = 1 }
        else { primary = id; focusedPane = 0 }
    }
    fun enableSplit() { split = true; focusedPane = if (primary == null) 0 else 1 }
    fun single() {
        if (focusedPane == 1 && secondary != null) primary = secondary
        secondary = null; split = false; focusedPane = 0
    }
    fun focus(pane: Int) { focusedPane = if (split && pane == 1) 1 else 0 }
    fun swap() {
        if (!split || primary == null || secondary == null) return
        val first = primary; primary = secondary; secondary = first; focusedPane = 1 - focusedPane
        ratio = 1f - ratio
    }
    fun resize(value: Float) { if (value.isFinite()) ratio = value.coerceIn(.3f, .7f) }
    fun remove(id: Int) {
        if (primary == id) {
            primary = secondary; secondary = null; focusedPane = 0
        } else if (secondary == id) { secondary = null; focusedPane = 0 }
    }
    fun visible(id: Int) = id == primary || (split && id == secondary)
    fun active(): Int? = if (split && focusedPane == 1) secondary else primary

    data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width get() = right - left
        val height get() = bottom - top
    }
    /** Side by side in landscape, stacked in portrait, with a separate divider hit target. */
    fun bounds(width: Int, height: Int, gap: Int, ratio: Float = this.ratio): List<Bounds> {
        val ratio = if (ratio.isFinite()) ratio.coerceIn(.3f, .7f) else this.ratio
        val w = width.coerceAtLeast(1); val h = height.coerceAtLeast(1)
        if (!split) return listOf(Bounds(0, 0, w, h))
        val landscape = w >= h
        val length = if (landscape) w else h
        val gutter = gap.coerceIn(0, (length - 2).coerceAtLeast(0))
        val available = (length - gutter).coerceAtLeast(2)
        val first = (available * ratio).toInt().coerceIn(1, available - 1)
        return if (landscape) listOf(Bounds(0, 0, first, h), Bounds(first + gutter, 0, w, h))
        else listOf(Bounds(0, 0, w, first), Bounds(0, first + gutter, w, h))
    }
}
