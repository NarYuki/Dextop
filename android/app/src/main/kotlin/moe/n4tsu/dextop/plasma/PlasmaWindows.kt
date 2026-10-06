package moe.n4tsu.dextop.plasma

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.SystemClock

internal enum class TileZone { NONE, MAXIMIZE, LEFT, RIGHT, TOP, BOTTOM, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

/** Shell-side state of one platform freeform window. */
internal class ManagedWindow(var task: TaskSnapshot) {
    val taskId: Int get() = task.taskId
    /** Task bounds as last reported by, or requested from, the platform. */
    val frame = Rect()
    var restoreFrame: Rect? = null
    var maximized = false
    var tile = TileZone.NONE
    var minimized = false
    var desktop = 0
    var onAllDesktops = false
    var label: String = ""
    var icon: Drawable? = null
    var lastActivated = 0L
    var thumbnail: Bitmap? = null
    val openedAt = SystemClock.uptimeMillis()

    val packageName: String get() = task.packageName
    val title: String get() = task.label?.takeIf { it.isNotBlank() } ?: label
}
