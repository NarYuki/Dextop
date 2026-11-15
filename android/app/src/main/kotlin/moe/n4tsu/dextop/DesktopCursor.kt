package moe.n4tsu.dextop

import android.content.Context
import android.graphics.RectF
import android.view.View
import android.view.WindowManager

/** What [DesktopCursor] needs from the session beyond the shared interfaces. */
internal interface DesktopCursorHost : DesktopSession, OverlayMenu {
    val externalOutputs: ExternalDisplays
    val menuScrim: View?
    val virtualPointer: VirtualPointer
}

/** The software cursor drawn over the desktop: its position, its view and when it is shown. */
internal class DesktopCursor(
    internal val context: Context,
    internal val desk: DesktopCursorHost,
) {
    internal var cursorView: CursorView? = null

    internal var cursorX = 960f

    internal var cursorY = 540f

    internal fun updateVirtualCursorVisibility() {
        val cursor = cursorView ?: return
        // The operation menu is handled by the accessibility overlay itself.
        // Never let a delayed uinput failure expose the white software cursor
        // over that menu while its touch controls are on screen.
        if (isMenuDisplayedForCursor()) {
            cursor.visibility = View.GONE
            return
        }
        // Device publication alone does not guarantee that InputReader has
        // rendered a pointer.  The software cursor remains authoritative
        // until the native EventHub stream or an overlay touch frame has
        // produced an actual virtual-pointer output.
        val systemPointerActive = desk.virtualPointer.virtualMouseReady && desk.virtualPointer.virtualPointerOutputObserved
        cursor.visibility = if (desk.directTouch || systemPointerActive) {
            View.GONE
        } else {
            View.VISIBLE
        }
    }

    /**
     * A mirror backend can fail after the input device has already hidden the
     * software pointer (for example when a vendor rejects WindowManager or
     * SurfaceControl mirroring).  Keep the failed backend from leaving the
     * pointer in an invisible state: tear down the virtual pointer and make
     * the accessibility cursor authoritative again.
     */

    internal fun isMenuOverlayVisible(): Boolean {
        return desk.menu?.visibility == View.VISIBLE && desk.menuScrim?.isClickable == true
    }

    /** True while the operation menu is visible, including its closing animation. */
    internal fun isMenuDisplayedForCursor(): Boolean {
        return desk.menu?.visibility == View.VISIBLE || desk.menuScrim?.visibility == View.VISIBLE
    }

    internal fun updateCursorPosition(x: Float = cursorX, y: Float = cursorY) {
        // Each screen draws the cursor while it is over its part of the desktop.
        fun place(cursor: CursorView?, region: RectF) {
            cursor ?: return
            val nx = (x - region.left) / region.width().coerceAtLeast(1f)
            val ny = (y - region.top) / region.height().coerceAtLeast(1f)
            cursor.alpha = if (nx in 0f..1f && ny in 0f..1f) 1f else 0f
            cursor.update(nx.coerceIn(0f, 1f), ny.coerceIn(0f, 1f))
        }
        // External only: the phone shows no desktop, and the system pointer is drawn on the external screen.
        val output = desk.externalOutputs.externalOutput
        if (output?.mode == "external") cursorView?.alpha = 0f
        else place(cursorView, desk.externalOutputs.regionFor(output?.phoneSurface ?: desk.surfaceView))
        output?.let { place(desk.externalOutputs.externalCursor, desk.externalOutputs.regionFor(it.surface)) }
    }
}
