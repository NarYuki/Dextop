package moe.n4tsu.dextop

import android.content.Context
import android.graphics.Rect
import android.util.Log
import android.view.Display
import android.view.Surface
import android.view.View

/** What [RawTouchscreenBridge] needs from the session beyond the shared interfaces. */
internal interface RawTouchscreenBridgeHost : DesktopSession, OverlayMenu {
    fun buildPrivilegedInputConfig(profile: String): IntArray
    val imeDirectTouchHeld: Boolean
    val imeRegion: ImeRegionProbe
    val laptopTrackpadView: View?
    fun refreshPrivilegedInputConfig(reason: String)
    fun updateVirtualCursorVisibility()
    val virtualPointer: VirtualPointer
}

/** Reads the phone touchscreen directly so its touches reach the desktop as a real touch device. */
internal class RawTouchscreenBridge(
    private val context: Context,
    private val desk: RawTouchscreenBridgeHost,
) {
    @Volatile
    internal var touchscreenReaderRunning = false

    @Volatile
    internal var touchscreenReaderReady = false

    private var touchscreenReaderGeneration = 0L

    internal var touchscreenReaderDevice = ""

    internal var touchscreenReaderCandidateCount = 0

    private var rawTouchscreenTopologyRefreshGeneration = 0L
    /**
     * Display 0 can emit change callbacks without a corresponding input
     * geometry change on recent Samsung builds.  Re-publishing the native
     * EventHub reader for each of those callbacks interrupts an otherwise
     * healthy pointer stream, so retain the last configuration that actually
     * reached the reader.  Input-device callbacks still force a refresh.
     */

    private var appliedRawTouchscreenTopologyFingerprint: String? = null

    private fun rawTouchscreenBridgeEligible(): Boolean {
        val profile = desk.virtualPointer.activeVirtualPointerProfile()
        return MirrorService.active && (desk.laptopModeActive || !desk.directTouch) &&
                (profile == "touchpad" || profile == "mouse") &&
                desk.virtualPointer.virtualPointerRegisteredProfile == profile &&
                desk.virtualPointer.virtualMouseReady && desk.virtualPointer.virtualMouseProcessAlive()
    }

    internal fun rawTouchscreenBridgeConsumesTouchSurface(sourceView: View? = null): Boolean {
        if (!touchscreenReaderRunning || !touchscreenReaderReady ||
            touchscreenReaderCandidateCount <= 0 ||
            !rawTouchscreenBridgeEligible()
        ) return false
        // The native reader has the same rectangle in its config, so this
        // stream is deliberately left to the accessibility overlay when its
        // ACTION_DOWN landed in the IME.
        if (sourceView === desk.surfaceView && desk.imeDirectTouchHeld) return false
        return when {
            sourceView == null -> true
            sourceView === desk.laptopTrackpadView -> desk.laptopModeActive
            sourceView === desk.surfaceView -> !desk.directTouch
            else -> false
        }
    }

    internal fun scheduleRawTouchscreenTopologyRefresh(reason: String, force: Boolean = false) {
        val refreshGeneration = ++rawTouchscreenTopologyRefreshGeneration
        desk.root?.postDelayed({
            if (refreshGeneration != rawTouchscreenTopologyRefreshGeneration || !MirrorService.active) {
                return@postDelayed
            }
            if (rawTouchscreenBridgeEligible()) {
                val fingerprint = rawTouchscreenTopologyFingerprint()
                if (!force && fingerprint == appliedRawTouchscreenTopologyFingerprint) {
                    Log.d(desk.logTag, "native input topology unchanged; skipping refresh reason=$reason")
                    return@postDelayed
                }
                desk.refreshPrivilegedInputConfig("topology_refresh:$reason")
                startRawTouchscreenReaderIfEligible()
                appliedRawTouchscreenTopologyFingerprint = fingerprint
            }
            val message = "native input topology refresh reason=$reason " +
                    "running=$touchscreenReaderRunning eligible=${rawTouchscreenBridgeEligible()}"
            OperationLog.i(context, "InputRouting", message)
            Log.i(desk.logTag, message)
        }, 220L)
    }

    private fun rawTouchscreenTopologyFingerprint(): String = listOf(
        "profile=${desk.virtualPointer.virtualPointerRegisteredProfile}",
        "target=${desk.targetDisplayId}:${desk.targetWidth}:${desk.targetHeight}",
        "rotation=${rawTouchscreenDisplayRotation()}",
        "surface=${rawTouchscreenViewBounds(desk.surfaceView)}",
        "trackpad=${rawTouchscreenViewBounds(desk.laptopTrackpadView)}",
        "ime=${desk.imeRegion.imeTouchBoundsOnHost()}",
        "laptop=${desk.laptopModeActive}",
        "direct=${desk.directTouch}"
    ).joinToString(";")

    internal fun startRawTouchscreenReaderIfEligible() {
        if (!rawTouchscreenBridgeEligible()) return
        val profile = desk.virtualPointer.virtualPointerRegisteredProfile
        touchscreenReaderGeneration += 1
        touchscreenReaderRunning = true
        desk.privilegedInputClient.updateConfig(desk.buildPrivilegedInputConfig(profile))
        val message = "native EventHub reader requested generation=$touchscreenReaderGeneration " +
                "profile=$profile rotation=${rawTouchscreenDisplayRotation()} " +
                "fullscreen=${rawTouchscreenViewBounds(desk.surfaceView)} " +
                "trackpad=${rawTouchscreenViewBounds(desk.laptopTrackpadView)} " +
                "ime=${desk.imeRegion.imeTouchBoundsOnHost()}"
        OperationLog.i(context, "InputRouting", message)
        Log.i(desk.logTag, message)
        appliedRawTouchscreenTopologyFingerprint = rawTouchscreenTopologyFingerprint()
    }

    internal fun rawTouchscreenViewBounds(view: View?): Rect? {
        val target = view ?: return null
        val host = desk.root ?: return null
        if (!target.isAttachedToWindow || target.width <= 0 || target.height <= 0 ||
            !host.isAttachedToWindow || host.width <= 0 || host.height <= 0
        ) return null

        // The native input bridge first maps a physical touchscreen contact to
        // hostWidth × hostHeight.  Keep the target rectangles in that same
        // root-local coordinate space.  getLocationOnScreen() alone is an
        // absolute display coordinate and diverges from root coordinates when
        // portrait locks, insets, or a rotated foldable layout are involved.
        val targetLocation = IntArray(2)
        val hostLocation = IntArray(2)
        target.getLocationOnScreen(targetLocation)
        host.getLocationOnScreen(hostLocation)
        val left = targetLocation[0] - hostLocation[0]
        val top = targetLocation[1] - hostLocation[1]
        return Rect(left, top, left + target.width, top + target.height)
    }

    internal fun rawTouchscreenDisplayRotation(): Int =
        desk.root?.display?.rotation ?: desk.surfaceView?.display?.rotation ?: Surface.ROTATION_0

    internal fun stopRawTouchscreenReader(reason: String) {
        val wasRunning = touchscreenReaderRunning || touchscreenReaderReady
        touchscreenReaderGeneration += 1
        touchscreenReaderRunning = false
        touchscreenReaderReady = false
        touchscreenReaderCandidateCount = 0
        touchscreenReaderDevice = ""
        appliedRawTouchscreenTopologyFingerprint = null
        if (wasRunning) {
            val message = "native EventHub routing state cleared reason=$reason"
            OperationLog.i(context, "InputRouting", message)
            Log.i(desk.logTag, message)
        }
    }

    private fun fallbackFromRawTouchscreen(reason: String) {
        val profile = desk.virtualPointer.activeVirtualPointerProfile()
        if (!MirrorService.active || (!desk.laptopModeActive && desk.directTouch) ||
            (profile != "touchpad" && profile != "mouse")
        ) return
        val message = "native EventHub input unavailable reason=$reason profile=$profile"
        OperationLog.w(context, "InputRouting", message)
        Log.w(desk.logTag, message)
        stopRawTouchscreenReader("fallback:$reason")
        desk.updateVirtualCursorVisibility()
    }
}
