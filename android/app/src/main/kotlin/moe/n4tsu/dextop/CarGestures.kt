package moe.n4tsu.dextop

import android.content.Context
import android.graphics.PointF
import android.graphics.Rect
import android.os.Binder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.Surface

/** What [CarGestures] needs from the session beyond the shared interfaces. */
internal interface CarGesturesHost : DesktopSession, OverlayMenu {
    var cursorX: Float
    var cursorY: Float
    val inputDispatcher: InputDispatcher
    val recentsExecutor: java.util.concurrent.ExecutorService
}

/** Pan, fling, pinch and tap gestures arriving from the car screen, replayed on the desktop display. */
internal class CarGestures(
    private val context: Context,
    private val desk: CarGesturesHost,
) {
    var carVisibleArea: FloatArray? = null

    var carPanHeld = false

    private var carPanDrag = false

    private var carPanDownTime = 0L

    private var carPanX = 0f

    private var carPanY = 0f

    private val carPanHandler = Handler(Looper.getMainLooper())

    private val carPanRelease = Runnable { finishCarPan() }

    /**
     * Android Auto reports a swipe only as distances, never where the finger went down. A swipe
     * therefore starts in the middle of the pane being used (the Auto shell's focused pane or open
     * page), not at the last tap, which may be on another pane or on the car chrome.
     */

    private fun carSwipeStart(): android.graphics.PointF =
        desk.plasmaShell?.autoGestureAnchor()?.takeIf { desk.autoOnlySession }
            ?: carDesktopAnchor
            ?: android.graphics.PointF(desk.targetWidth / 2f, desk.targetHeight / 2f)

    /**
     * Android Desktop has no Auto pane: swipes start in the focused window on the Auto display.
     * Task bounds come over Binder, so they are read off the main thread after taps and swipes.
     */


    @Volatile private var carDesktopAnchor: android.graphics.PointF? = null
    private val refreshCarDesktopAnchor = Runnable {
        if (!MirrorService.active || !desk.autoOnlySession || desk.plasmaShell?.autoGestureAnchor() != null) {
            carDesktopAnchor = null
            return@Runnable
        }
        val displayId = desk.targetDisplayId
        val width = desk.targetWidth
        val height = desk.targetHeight
        if (displayId < 0 || width <= 0 || height <= 0) return@Runnable
        desk.recentsExecutor.execute {
            val anchor = runCatching {
                val tasks = moe.n4tsu.dextop.plasma.TaskBridge({ name, type -> desk.privilegedAccess.service(name, type) }, desk.logTag)
                    .tasks(displayId).filter { it.visible && it.activityType == 1 }
                // Task lists are ordered from the top; an empty rectangle is a fullscreen task.
                val task = tasks.firstOrNull { it.focused } ?: tasks.minByOrNull { it.zIndex }
                val bounds = task?.bounds?.takeUnless { it.isEmpty }?.let(::Rect)
                if (bounds == null || !bounds.intersect(0, 0, width, height)) null
                else android.graphics.PointF(bounds.exactCenterX(), bounds.exactCenterY())
            }.getOrNull()
            carPanHandler.post { if (desk.targetDisplayId == displayId) carDesktopAnchor = anchor }
        }
    }

    fun scheduleCarDesktopAnchor() {
        carPanHandler.removeCallbacks(refreshCarDesktopAnchor)
        // Give a tap time to move focus before reading which window is in front.
        carPanHandler.postDelayed(refreshCarDesktopAnchor, 300L)
    }

    /**
     * Tap, then gesture: swipes, flings and pinches that begin within [CAR_TAP_DRAG_MS] of a tap
     * use the tapped point (the anchor the Driving app sends), so sliders, map points and sheets
     * are grabbed where they are. Each anchored gesture renews the window when it ends, so a run
     * of gestures (panning a map several times, zooming in steps) keeps the same point until the
     * user pauses for longer than the window.
     */

    var carTapAt = 0L

    private var carTapAnchor: android.graphics.PointF? = null

    private var carAnchoredGesture = false

    private fun carAnchorActive() =
        carTapAnchor != null && SystemClock.uptimeMillis() - carTapAt <= MirrorService.CAR_TAP_DRAG_MS

    private fun carGestureStart(): android.graphics.PointF {
        carAnchoredGesture = carAnchorActive()
        return carTapAnchor?.takeIf { carAnchoredGesture }?.let { android.graphics.PointF(it.x, it.y) } ?: carSwipeStart()
    }

    /** The Driving app's last tap, in normalised Surface coordinates. */
    fun rememberCarTapAnchor(x: Float, y: Float) {
        if (x in 0f..1f && y in 0f..1f) carTapAnchor = android.graphics.PointF(x * desk.targetWidth, y * desk.targetHeight)
    }

    private fun renewCarAnchor() {
        if (carAnchoredGesture) carTapAt = SystemClock.uptimeMillis()
        carAnchoredGesture = false
    }

    fun carPan(dx: Float, dy: Float, drag: Boolean, release: Boolean) {
        if (release || !dx.isFinite() || !dy.isFinite()) { finishCarPan(); return }
        if (dx == 0f && dy == 0f) return
        finishCarPinch()
        if (carPanHeld && drag != carPanDrag) finishCarPan()
        if (!carPanHeld) {
            carPanDrag = drag
            // Drag mode keeps the tapped pickup point; a swipe starts in the active pane.
            val start = if (drag) android.graphics.PointF(desk.cursorX, desk.cursorY) else carGestureStart()
            carPanX = start.x.coerceIn(0f, desk.targetWidth - 1f)
            carPanY = start.y.coerceIn(0f, desk.targetHeight - 1f)
            carPanHeld = sendCarPanPointer(MotionEvent.ACTION_DOWN)
        }
        if (!carPanHeld) return
        val nextX = (carPanX + dx).coerceIn(0f, desk.targetWidth - 1f)
        val nextY = (carPanY + dy).coerceIn(0f, desk.targetHeight - 1f)
        // A scroll gesture reaching an edge continues with another swipe rather
        // than losing all subsequent deltas against the clamped coordinate.
        if (!drag && (nextX == carPanX && dx != 0f || nextY == carPanY && dy != 0f)) {
            // A long scroll that reaches an edge lifts and continues from its starting point,
            // the tapped anchor when the scroll is anchored.
            val anchor = carTapAnchor?.takeIf { carAnchoredGesture }
            finishCarPan()
            if (anchor != null) carAnchoredGesture = true
            val start = anchor?.let { android.graphics.PointF(it.x, it.y) } ?: carSwipeStart()
            carPanX = start.x.coerceIn(0f, desk.targetWidth - 1f)
            carPanY = start.y.coerceIn(0f, desk.targetHeight - 1f)
            carPanDrag = false
            carPanHeld = sendCarPanPointer(MotionEvent.ACTION_DOWN)
        }
        carPanX = (carPanX + dx).coerceIn(0f, desk.targetWidth - 1f)
        carPanY = (carPanY + dy).coerceIn(0f, desk.targetHeight - 1f)
        if (!sendCarPanPointer(MotionEvent.ACTION_MOVE)) { finishCarPan(); return }
        desk.cursorX = carPanX; desk.cursorY = carPanY
        carPanHandler.removeCallbacks(carPanRelease)
        // Scroll callbacks have no UP callback. A short pause must not split one swipe into
        // several (each restarting from the pane centre), so the finger is lifted only after a
        // long gap; a fling or a tap ends the swipe at once. Drag ends with another tap.
        carPanHandler.postDelayed(carPanRelease, if (drag) 30_000L else MirrorService.CAR_SWIPE_IDLE_MS)
    }

    fun finishCarPan() {
        carPanHandler.removeCallbacks(carPanRelease)
        if (carPanHeld) {
            sendCarPanPointer(MotionEvent.ACTION_UP)
            scheduleCarDesktopAnchor()
            if (!carPanDrag) renewCarAnchor()
        }
        carPanHeld = false
    }

    /**
     * Ends the swipe with a few moves at the host's velocity before lifting, so the application's
     * VelocityTracker sees the fling instead of a finger that stopped 180 ms earlier.
     */

    fun carFling(vx: Float, vy: Float) {
        if (carPanHeld && carPanDrag) return
        finishCarPinch()
        if (!carPanHeld) {
            carPanDrag = false
            val start = carGestureStart()
            carPanX = start.x.coerceIn(0f, desk.targetWidth - 1f)
            carPanY = start.y.coerceIn(0f, desk.targetHeight - 1f)
            carPanHeld = sendCarPanPointer(MotionEvent.ACTION_DOWN)
            if (!carPanHeld) return
        }
        carPanHandler.removeCallbacks(carPanRelease)
        var time = maxOf(SystemClock.uptimeMillis(), carPanEventTime)
        repeat(3) {
            time += 8L
            carPanX = (carPanX + vx * .008f).coerceIn(0f, desk.targetWidth - 1f)
            carPanY = (carPanY + vy * .008f).coerceIn(0f, desk.targetHeight - 1f)
            sendCarPanPointer(MotionEvent.ACTION_MOVE, time)
        }
        sendCarPanPointer(MotionEvent.ACTION_UP, time)
        carPanHeld = false
        desk.cursorX = carPanX; desk.cursorY = carPanY
        renewCarAnchor()
    }

    private var carPanEventTime = 0L

    private var carPinchHeld = false

    private var carPinchDownTime = 0L

    private var carPinchX = 0f

    private var carPinchY = 0f

    private var carPinchSpan = 0f

    private val carPinchRelease = Runnable { finishCarPinch() }

    /**
     * Two synthetic fingers moved apart by each scale factor. Tap, then pinch: a pinch in the
     * tap's window (see [carGestureStart]) zooms around the tapped point; otherwise around the
     * host's focal point, or the active pane when the host gives none.
     */

    fun carScale(x: Float, y: Float, factor: Float, anchorX: Float, anchorY: Float) {
        finishCarPan()
        val limit = minOf(desk.targetWidth, desk.targetHeight).toFloat()
        if (!carPinchHeld) {
            rememberCarTapAnchor(anchorX, anchorY)
            carAnchoredGesture = carAnchorActive()
            val anchor = carTapAnchor
            val focus = when {
                carAnchoredGesture && anchor != null -> android.graphics.PointF(anchor.x, anchor.y)
                x >= 0f && y >= 0f -> android.graphics.PointF(x * desk.targetWidth, y * desk.targetHeight)
                else -> carSwipeStart()
            }
            carPinchX = focus.x.coerceIn(0f, desk.targetWidth - 1f)
            carPinchY = focus.y.coerceIn(0f, desk.targetHeight - 1f)
            carPinchSpan = (limit * .2f).coerceAtLeast(48f)
            carPinchDownTime = SystemClock.uptimeMillis()
            carPinchHeld = sendCarPinch(MotionEvent.ACTION_DOWN, 1) &&
                sendCarPinch(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2)
            if (!carPinchHeld) return
        }
        carPinchSpan = (carPinchSpan * factor).coerceIn(24f, limit * .95f)
        sendCarPinch(MotionEvent.ACTION_MOVE, 2)
        carPanHandler.removeCallbacks(carPinchRelease)
        carPanHandler.postDelayed(carPinchRelease, 220L)
    }

    fun finishCarPinch() {
        carPanHandler.removeCallbacks(carPinchRelease)
        if (!carPinchHeld) return
        sendCarPinch(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2)
        sendCarPinch(MotionEvent.ACTION_UP, 1)
        carPinchHeld = false
        renewCarAnchor()
    }

    private fun sendCarPinch(action: Int, pointers: Int): Boolean {
        val properties = Array(pointers) { index ->
            MotionEvent.PointerProperties().apply { id = index; toolType = MotionEvent.TOOL_TYPE_FINGER }
        }
        val coords = Array(pointers) { index ->
            MotionEvent.PointerCoords().apply {
                val offset = carPinchSpan / 2f * if (index == 0) -1f else 1f
                x = (carPinchX + offset).coerceIn(0f, desk.targetWidth - 1f)
                y = carPinchY
                pressure = if (action == MotionEvent.ACTION_UP) 0f else 1f
                size = 1f
            }
        }
        val event = MotionEvent.obtain(carPinchDownTime, SystemClock.uptimeMillis(), action, pointers, properties, coords,
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        return try { desk.inputDispatcher.send(event, desk.targetDisplayId) } finally { event.recycle() }
    }

    private fun sendCarPanPointer(action: Int, eventTime: Long = SystemClock.uptimeMillis()): Boolean {
        val now = maxOf(eventTime, carPanEventTime)
        carPanEventTime = now
        if (action == MotionEvent.ACTION_DOWN) carPanDownTime = now
        val properties = MotionEvent.PointerProperties().apply {
            id = 0
            toolType = if (carPanDrag) MotionEvent.TOOL_TYPE_MOUSE else MotionEvent.TOOL_TYPE_FINGER
        }
        val coords = MotionEvent.PointerCoords().apply {
            x = carPanX; y = carPanY
            pressure = if (action == MotionEvent.ACTION_UP) 0f else 1f
            size = 1f
        }
        val event = MotionEvent.obtain(carPanDownTime, now, action, 1, arrayOf(properties), arrayOf(coords),
            0, if (carPanDrag && action != MotionEvent.ACTION_UP) MotionEvent.BUTTON_PRIMARY else 0,
            1f, 1f, 0, 0, if (carPanDrag) InputDevice.SOURCE_MOUSE else InputDevice.SOURCE_TOUCHSCREEN, 0)
        return try { desk.inputDispatcher.send(event, desk.targetDisplayId) } finally { event.recycle() }
    }
}
