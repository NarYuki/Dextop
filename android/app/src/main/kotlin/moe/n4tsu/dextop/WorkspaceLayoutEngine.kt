package moe.n4tsu.dextop

import android.content.Context
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.view.WindowInsets
import android.view.WindowManager

internal class WorkspaceLayoutEngine(
    private val context: Context,
    private val environment: DesktopEnvironment,
    private val tag: String
) {
    fun fit(displayId: Int, displayWidth: Int, displayHeight: Int, source: Rect): Rect {
        val usable = usableArea(displayId, displayWidth, displayHeight)
        val left = source.left.coerceIn(usable.left, usable.right - 1)
        val right = source.right.coerceIn(left + 1, usable.right)
        val top = source.top.coerceIn(usable.top, usable.bottom - 1)
        val bottom = source.bottom.coerceIn(top + 1, usable.bottom)
        return Rect(left, top, right, bottom)
    }

    fun position(
        displayId: Int,
        displayWidth: Int,
        displayHeight: Int,
        position: String
    ): Rect {
        val usable = usableArea(displayId, displayWidth, displayHeight)
        val centerX = usable.left + usable.width() / 2
        val centerY = usable.top + usable.height() / 2
        val third = usable.width() / 3
        return when (position) {
            "left" -> Rect(usable.left, usable.top, usable.left + third, usable.bottom)
            "right" -> Rect(usable.left + third * 2, usable.top, usable.right, usable.bottom)
            "half_left" -> Rect(usable.left, usable.top, centerX, usable.bottom)
            "half_right" -> Rect(centerX, usable.top, usable.right, usable.bottom)
            "top_left" -> Rect(usable.left, usable.top, centerX, centerY)
            "top_right" -> Rect(centerX, usable.top, usable.right, centerY)
            "bottom_half" -> Rect(usable.left, centerY, usable.right, usable.bottom)
            "top_half" -> Rect(usable.left, usable.top, usable.right, centerY)
            "grid_top_left" -> Rect(usable.left, usable.top, centerX, centerY)
            "grid_top_right" -> Rect(centerX, usable.top, usable.right, centerY)
            "grid_bottom_left" -> Rect(usable.left, centerY, centerX, usable.bottom)
            "grid_bottom_right" -> Rect(centerX, centerY, usable.right, usable.bottom)
            "wide_left" -> Rect(usable.left, usable.top, usable.left + third * 2, usable.bottom)
            "narrow_right" -> Rect(usable.left + third * 2, usable.top, usable.right, usable.bottom)
            "narrow_left" -> Rect(usable.left, usable.top, usable.left + third, usable.bottom)
            "wide_right" -> Rect(usable.left + third, usable.top, usable.right, usable.bottom)
            "right_top" -> Rect(usable.left + third * 2, usable.top, usable.right, centerY)
            "right_bottom" -> Rect(usable.left + third * 2, centerY, usable.right, usable.bottom)
            else -> Rect(usable.left + third, usable.top, usable.left + third * 2, usable.bottom)
        }
    }

    private fun usableArea(displayId: Int, width: Int, height: Int): Rect {
        if (!environment.supportsLaunchBounds) return Rect(0, 0, width, height)
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(displayId)
            ?: return Rect(0, 0, width, height)
        val displayContext = context.createDisplayContext(display)
        val metrics = displayContext.getSystemService(WindowManager::class.java).maximumWindowMetrics
        val types = WindowInsets.Type.navigationBars() or WindowInsets.Type.tappableElement()
        val insets = metrics.windowInsets.getInsetsIgnoringVisibility(types)
        val area = Rect(
            insets.left.coerceAtMost(width - 1),
            insets.top.coerceAtMost(height - 1),
            (width - insets.right).coerceAtLeast(1),
            (height - insets.bottom).coerceAtLeast(1)
        )
        return area
    }
}
