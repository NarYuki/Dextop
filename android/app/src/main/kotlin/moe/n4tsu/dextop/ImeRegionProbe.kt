package moe.n4tsu.dextop

import android.content.Context
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.SystemClock
import android.util.Log
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityWindowInfo
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/** What [ImeRegionProbe] needs from the session beyond the shared interfaces. */
internal interface ImeRegionProbeHost : DesktopSession, OverlayMenu {
    fun rawTouchscreenViewBounds(view: View?): Rect?
    fun regionFor(view: View?): RectF
}

internal data class ImeTouchRegion(
    val displayId: Int,
    val bounds: Rect,
    val observedAt: Long,
)

/** Tracks where the on-screen keyboard sits on the desktop display so touches there go to it directly. */
internal class ImeRegionProbe(
    private val context: Context,
    private val desk: ImeRegionProbeHost,
) {
    /** WindowManager/Accessibility window enumeration never runs on the input thread. */
    val imeRegionProbeExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "Dextop-ime-region").apply { isDaemon = true }
    }

    private val imeRegionProbeQueued = AtomicBoolean(false)

    val imeRegionProbeFollowUp = AtomicBoolean(false)

    var imeRegionSessionGeneration = 0L

    private var imeRegionLastProbeRequestedAt = 0L

    @Volatile
    var imeRegionProbePending = false

    @Volatile
    var imeTouchRegion: ImeTouchRegion? = null

    fun requestImeRegionProbe(reason: String, force: Boolean = false) {
        if (!MirrorService.active || desk.autoOnlySession || desk.targetDisplayId < 0 || imeRegionProbeExecutor.isShutdown) return
        val now = SystemClock.uptimeMillis()
        if (!force && now - imeRegionLastProbeRequestedAt < MirrorService.IME_REGION_PROBE_MIN_INTERVAL_MS) return
        imeRegionLastProbeRequestedAt = now
        imeRegionProbePending = true
        if (!imeRegionProbeQueued.compareAndSet(false, true)) {
            imeRegionProbeFollowUp.set(true)
            return
        }

        val sessionGeneration = imeRegionSessionGeneration
        val displayId = desk.targetDisplayId
        runCatching {
            imeRegionProbeExecutor.execute {
                val result = runCatching { queryImeTouchRegion(displayId) }
                Handler(context.mainLooper).post {
                    imeRegionProbeQueued.set(false)
                    val followUp = imeRegionProbeFollowUp.getAndSet(false)
                    if (!MirrorService.active || desk.autoOnlySession ||
                        sessionGeneration != imeRegionSessionGeneration ||
                        desk.targetDisplayId != displayId
                    ) return@post

                    result.onSuccess { next ->
                        val previous = imeTouchRegion
                        imeTouchRegion = next
                        imeRegionProbePending = false
                        if (previous?.displayId != next?.displayId || previous?.bounds != next?.bounds) {
                            val message = "IME region ${next?.bounds ?: "hidden"} display=$displayId " +
                                    "reason=$reason observedAt=${next?.observedAt ?: 0L}"
                            OperationLog.i(context, "ImeTouchRouting", message)
                            Log.i(desk.logTag, message)
                            // The native EventHub reader uses the same cached
                            // rectangle to suppress its duplicate stream.
                            desk.privilegedInput.refreshPrivilegedInputConfig("ime_region:$reason")
                        }
                    }.onFailure { error ->
                        imeRegionProbePending = false
                        OperationLog.w(context, "ImeTouchRouting", "IME window query failed reason=$reason", error)
                        Log.w(desk.logTag, "IME window query failed reason=$reason", error)
                    }
                    if (followUp && MirrorService.active && !desk.autoOnlySession && desk.targetDisplayId == displayId) {
                        requestImeRegionProbe("coalesced_window_change", force = true)
                    }
                }
            }
        }.onFailure { error ->
            imeRegionProbeQueued.set(false)
            imeRegionProbePending = false
            OperationLog.w(context, "ImeTouchRouting", "IME region probe could not be scheduled", error)
        }
    }

    private fun queryImeTouchRegion(displayId: Int): ImeTouchRegion? {
        val candidates = mutableListOf<Rect>()
        val windows = desk.accessibilityService.getWindowsOnAllDisplays().get(displayId).orEmpty()
        for (window in windows) {
            try {
                // The per-display list exposes the currently visible
                // interactive windows; AccessibilityWindowInfo has no
                // visibility flag.
                if (window.type != AccessibilityWindowInfo.TYPE_INPUT_METHOD ||
                    window.displayId != displayId
                ) continue
                val bounds = Rect()
                window.getBoundsInScreen(bounds)
                if (!bounds.isEmpty) candidates += Rect(bounds)
            } finally {
                window.recycle()
            }
        }
        var selected: Rect? = null
        for (candidate in candidates) {
            val current = selected
            if (current == null || candidate.bottom > current.bottom ||
                (candidate.bottom == current.bottom &&
                    candidate.width().toLong() * candidate.height() >
                    current.width().toLong() * current.height())
            ) {
                selected = candidate
            }
        }
        return selected?.let {
            ImeTouchRegion(displayId, it, SystemClock.uptimeMillis())
        }
    }

    /** Converts the cached target-display IME bounds into host-surface space. */
    fun imeTouchBoundsOnHost(): Rect? {
        val region = imeTouchRegion ?: return null
        if (region.displayId != desk.targetDisplayId) return null
        val surfaceBounds = desk.rawTouchscreenViewBounds(desk.surfaceView) ?: return null
        val displayWidth = desk.targetWidth.coerceAtLeast(1)
        val displayHeight = desk.targetHeight.coerceAtLeast(1)
        val clipped = Rect(region.bounds)
        if (!clipped.intersect(0, 0, displayWidth, displayHeight)) return null
        val left = surfaceBounds.left +
                (clipped.left.toFloat() / displayWidth * surfaceBounds.width()).roundToInt()
        val top = surfaceBounds.top +
                (clipped.top.toFloat() / displayHeight * surfaceBounds.height()).roundToInt()
        val right = surfaceBounds.left +
                (clipped.right.toFloat() / displayWidth * surfaceBounds.width()).roundToInt()
        val bottom = surfaceBounds.top +
                (clipped.bottom.toFloat() / displayHeight * surfaceBounds.height()).roundToInt()
        return Rect(left, top, right, bottom).takeIf { !it.isEmpty }
    }

    fun imeTouchRegionContainsSurfacePoint(x: Float, y: Float): Boolean {
        val region = imeTouchRegion ?: return false
        if (region.displayId != desk.targetDisplayId) return false
        val view = desk.surfaceView ?: return false
        if (view.width <= 0 || view.height <= 0) return false
        val shown = desk.regionFor(view)
        val targetX = shown.left + x / view.width * shown.width()
        val targetY = shown.top + y / view.height * shown.height()
        return targetX >= region.bounds.left && targetX < region.bounds.right &&
                targetY >= region.bounds.top && targetY < region.bounds.bottom
    }
}
