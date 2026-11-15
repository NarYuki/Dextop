package moe.n4tsu.dextop

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.View
import kotlin.math.roundToInt

/** What [VirtualTouchpad] needs from the session beyond the shared interfaces. */
internal interface VirtualTouchpadHost : DesktopSession, OverlayMenu {
    var nativeTouchpadGestureActive: Boolean
    fun virtualMouseEvents(events: List<Any>, allowDirectTouch: Boolean = false): Boolean
    fun virtualMouseProcessAlive(): Boolean
    val virtualMouseReady: Boolean
    fun virtualPointerInputActive(allowDirectTouch: Boolean): Boolean
    val virtualPointerRegisteredProfile: String
}

/** Turns touches on the trackpad surface into multi-touch contacts for the privileged virtual touchpad. */
internal class VirtualTouchpad(
    private val context: Context,
    private val desk: VirtualTouchpadHost,
) {
    /** Android pointer ids currently occupying Linux multitouch Type-B slots. */
    private val virtualTouchpadSlotPointerIds =
        IntArray(MirrorService.VIRTUAL_TOUCHPAD_MAX_SLOTS) { -1 }

    private val virtualTouchpadSlotTrackingIds =
        IntArray(MirrorService.VIRTUAL_TOUCHPAD_MAX_SLOTS) { -1 }

    private var virtualTouchpadNextTrackingId = 1

    private var virtualTouchpadGestureSequence = 0L

    private var virtualTouchpadGestureStartedAt = 0L

    private var virtualTouchpadFrameCount = 0

    private var virtualTouchpadContactUpdateCount = 0

    private var virtualTouchpadLastMoveLogAt = 0L

    fun virtualTouchpadActiveContactCount(): Int =
        virtualTouchpadSlotPointerIds.count { it >= 0 }

    fun resetVirtualTouchpadState(reason: String, logSummary: Boolean = false) {
        if (logSummary) {
            val duration = if (virtualTouchpadGestureStartedAt > 0L) {
                (SystemClock.uptimeMillis() - virtualTouchpadGestureStartedAt).coerceAtLeast(0L)
            } else {
                0L
            }
            Log.i(
                desk.logTag,
                "touchpad gesture reset reason=$reason sequence=$virtualTouchpadGestureSequence " +
                        "durationMs=$duration frames=$virtualTouchpadFrameCount " +
                        "contactUpdates=$virtualTouchpadContactUpdateCount " +
                        "activeContacts=${virtualTouchpadActiveContactCount()}"
            )
        }
        virtualTouchpadSlotPointerIds.fill(-1)
        virtualTouchpadSlotTrackingIds.fill(-1)
        virtualTouchpadGestureStartedAt = 0L
        virtualTouchpadFrameCount = 0
        virtualTouchpadContactUpdateCount = 0
        virtualTouchpadLastMoveLogAt = 0L
        desk.nativeTouchpadGestureActive = false
    }

    private fun allocateVirtualTouchpadSlot(pointerId: Int): Int? {
        val existing = virtualTouchpadSlotPointerIds.indexOf(pointerId)
        if (existing >= 0) return existing
        val slot = virtualTouchpadSlotPointerIds.indexOfFirst { it < 0 }
        if (slot < 0) {
            OperationLog.w(
                context,
                "InputRouting",
                "touchpad slot allocation failed pointerId=$pointerId maxSlots=${MirrorService.VIRTUAL_TOUCHPAD_MAX_SLOTS}"
            )
            Log.w(
                desk.logTag,
                "touchpad slot allocation failed pointerId=$pointerId " +
                        "slots=${virtualTouchpadSlotPointerIds.contentToString()}"
            )
            return null
        }
        val trackingId = virtualTouchpadNextTrackingId
        virtualTouchpadNextTrackingId = if (trackingId >= 65534) 1 else trackingId + 1
        virtualTouchpadSlotPointerIds[slot] = pointerId
        virtualTouchpadSlotTrackingIds[slot] = trackingId
        return slot
    }

    private fun virtualTouchpadPosition(
        event: MotionEvent,
        pointerIndex: Int,
        sourceView: View
    ): Pair<Int, Int>? {
        if (sourceView.width <= 0 || sourceView.height <= 0) {
            OperationLog.w(
                context,
                "InputRouting",
                "touchpad event dropped because source surface has invalid size " +
                        "width=${sourceView.width} height=${sourceView.height}"
            )
            Log.w(
                desk.logTag,
                "touchpad event dropped: invalid source size ${sourceView.width}x${sourceView.height}"
            )
            return null
        }
        // Match the uinput contact range to the source region.  This keeps
        // horizontal and vertical relative travel balanced in portrait too.
        val maxX = sourceView.width.coerceAtLeast(1)
        val maxY = sourceView.height.coerceAtLeast(1)
        val x = (event.getX(pointerIndex) / sourceView.width.toFloat() * maxX)
            .roundToInt().coerceIn(0, maxX)
        val y = (event.getY(pointerIndex) / sourceView.height.toFloat() * maxY)
            .roundToInt().coerceIn(0, maxY)
        return x to y
    }

    private fun appendVirtualTouchpadContact(
        events: MutableList<Any>,
        event: MotionEvent,
        pointerIndex: Int,
        sourceView: View,
        includeTrackingId: Boolean
    ): Boolean {
        val pointerId = event.getPointerId(pointerIndex)
        val slot = allocateVirtualTouchpadSlot(pointerId) ?: return false
        val position = virtualTouchpadPosition(event, pointerIndex, sourceView) ?: return false
        events += "EV_ABS"; events += "ABS_MT_SLOT"; events += slot
        if (includeTrackingId) {
            events += "EV_ABS"; events += "ABS_MT_TRACKING_ID"
            events += virtualTouchpadSlotTrackingIds[slot]
        }
        events += "EV_ABS"; events += "ABS_MT_POSITION_X"; events += position.first
        events += "EV_ABS"; events += "ABS_MT_POSITION_Y"; events += position.second
        events += "EV_ABS"; events += "ABS_MT_TOUCH_MAJOR"; events += MirrorService.VIRTUAL_TOUCHPAD_TOUCH_MAJOR
        events += "EV_ABS"; events += "ABS_MT_PRESSURE"; events += MirrorService.VIRTUAL_TOUCHPAD_PRESSURE
        virtualTouchpadContactUpdateCount += 1
        return true
    }

    fun finishVirtualTouchpadGesture(
        reason: String,
        allowDirectTouch: Boolean,
        sendToDevice: Boolean = true
    ): Boolean {
        val activeSlots = virtualTouchpadSlotPointerIds.indices
            .filter { virtualTouchpadSlotPointerIds[it] >= 0 }
        val sequence = virtualTouchpadGestureSequence
        val frames = virtualTouchpadFrameCount
        val updates = virtualTouchpadContactUpdateCount
        val startedAt = virtualTouchpadGestureStartedAt
        val events = mutableListOf<Any>()
        activeSlots.forEach { slot ->
            events += "EV_ABS"; events += "ABS_MT_SLOT"; events += slot
            events += "EV_ABS"; events += "ABS_MT_TRACKING_ID"; events += -1
        }
        if (activeSlots.isNotEmpty()) {
            events += "EV_KEY"; events += "BTN_TOUCH"; events += 0
            appendVirtualTouchpadTools(events, 0)
            events += "EV_SYN"; events += "SYN_REPORT"; events += 0
        }
        val sent = activeSlots.isEmpty() || !sendToDevice ||
                virtualTouchpadEvents(events, allowDirectTouch)
        resetVirtualTouchpadState(reason)
        val duration = if (startedAt > 0L) {
            (SystemClock.uptimeMillis() - startedAt).coerceAtLeast(0L)
        } else {
            0L
        }
        val summary = "touchpad gesture finished reason=$reason sequence=$sequence durationMs=$duration " +
                "frames=$frames contactUpdates=$updates releasedSlots=${activeSlots.joinToString()} sent=$sent"
        OperationLog.i(context, "InputRouting", summary)
        Log.i(desk.logTag, summary)
        return sent
    }

    private fun virtualTouchpadEvents(
        events: List<Any>,
        allowDirectTouch: Boolean
    ): Boolean {
        if (desk.virtualPointerRegisteredProfile != "touchpad") {
            Log.w(
                desk.logTag,
                "touchpad frame rejected: registeredProfile=${desk.virtualPointerRegisteredProfile} " +
                        "eventTriples=${events.size / 3}"
            )
            return false
        }
        return desk.virtualMouseEvents(events, allowDirectTouch)
    }

    private fun appendVirtualTouchpadTools(events: MutableList<Any>, contacts: Int) {
        listOf("BTN_TOOL_FINGER", "BTN_TOOL_DOUBLETAP", "BTN_TOOL_TRIPLETAP", "BTN_TOOL_QUADTAP", "BTN_TOOL_QUINTTAP")
            .forEachIndexed { index, key ->
                events += "EV_KEY"; events += key; events += if (contacts == index + 1) 1 else 0
            }
    }

    /** Bridges an Android MotionEvent to a Linux multitouch Type-B frame. */
    fun virtualTouchpadMotionEvent(
        event: MotionEvent,
        sourceView: View,
        allowDirectTouch: Boolean
    ): Boolean {
        if (!desk.virtualPointerInputActive(allowDirectTouch) ||
            desk.virtualPointerRegisteredProfile != "touchpad"
        ) {
            Log.w(
                desk.logTag,
                "touchpad event unavailable action=${MotionEvent.actionToString(event.action)} " +
                        "ready=${desk.virtualMouseReady} processAlive=${desk.virtualMouseProcessAlive()} " +
                        "registeredProfile=${desk.virtualPointerRegisteredProfile}"
            )
            resetVirtualTouchpadState("pointer_unavailable", logSummary = true)
            return false
        }
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_CANCEL) {
            return finishVirtualTouchpadGesture("action_cancel", allowDirectTouch)
        }
        if (action == MotionEvent.ACTION_DOWN) {
            if (virtualTouchpadActiveContactCount() > 0) {
                finishVirtualTouchpadGesture("unexpected_action_down", allowDirectTouch)
            }
            virtualTouchpadGestureSequence += 1
            virtualTouchpadGestureStartedAt = SystemClock.uptimeMillis()
            virtualTouchpadFrameCount = 0
            virtualTouchpadContactUpdateCount = 0
        }

        val events = mutableListOf<Any>()
        var finishReason: String? = null
        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val wasEmpty = virtualTouchpadActiveContactCount() == 0
                val actionIndex = event.actionIndex
                if (!appendVirtualTouchpadContact(
                        events,
                        event,
                        actionIndex,
                        sourceView,
                        includeTrackingId = true
                    )
                ) {
                    finishVirtualTouchpadGesture("contact_down_failed", allowDirectTouch)
                    return false
                }
                if (wasEmpty) {
                    events += "EV_KEY"; events += "BTN_TOUCH"; events += 1
                }
                val pointerId = event.getPointerId(actionIndex)
                val slot = virtualTouchpadSlotPointerIds.indexOf(pointerId)
                val position = virtualTouchpadPosition(event, actionIndex, sourceView)
                val message = "touchpad contact down sequence=$virtualTouchpadGestureSequence " +
                        "pointerId=$pointerId slot=$slot trackingId=${virtualTouchpadSlotTrackingIds[slot]} " +
                        "position=$position pointers=${event.pointerCount} source=${sourceView.width}x${sourceView.height}"
                OperationLog.i(
                    context,
                    "InputRouting",
                    "touchpad contact down sequence=$virtualTouchpadGestureSequence pointerId=$pointerId " +
                            "slot=$slot trackingId=${virtualTouchpadSlotTrackingIds[slot]} " +
                            "pointers=${event.pointerCount}"
                )
                Log.i(desk.logTag, message)
            }

            MotionEvent.ACTION_MOVE -> {
                for (index in 0 until event.pointerCount) {
                    val pointerId = event.getPointerId(index)
                    val isNewContact = virtualTouchpadSlotPointerIds.indexOf(pointerId) < 0
                    if (!appendVirtualTouchpadContact(
                            events,
                            event,
                            index,
                            sourceView,
                            includeTrackingId = isNewContact
                        )
                    ) {
                        finishVirtualTouchpadGesture("contact_move_failed", allowDirectTouch)
                        return false
                    }
                    if (isNewContact) {
                        Log.w(desk.logTag, "touchpad recovered missing contact pointerId=$pointerId during MOVE")
                    }
                }
            }

            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                val actionIndex = event.actionIndex
                for (index in 0 until event.pointerCount) {
                    if (index == actionIndex) continue
                    if (!appendVirtualTouchpadContact(
                            events,
                            event,
                            index,
                            sourceView,
                            includeTrackingId = false
                        )
                    ) {
                        finishVirtualTouchpadGesture("contact_up_update_failed", allowDirectTouch)
                        return false
                    }
                }
                val pointerId = event.getPointerId(actionIndex)
                val slot = virtualTouchpadSlotPointerIds.indexOf(pointerId)
                if (slot >= 0) {
                    events += "EV_ABS"; events += "ABS_MT_SLOT"; events += slot
                    events += "EV_ABS"; events += "ABS_MT_TRACKING_ID"; events += -1
                    virtualTouchpadSlotPointerIds[slot] = -1
                    virtualTouchpadSlotTrackingIds[slot] = -1
                } else {
                    Log.w(desk.logTag, "touchpad contact up missing pointerId=$pointerId")
                }
                if (virtualTouchpadActiveContactCount() == 0) {
                    events += "EV_KEY"; events += "BTN_TOUCH"; events += 0
                    finishReason = if (action == MotionEvent.ACTION_UP) "action_up" else "last_pointer_up"
                }
                OperationLog.i(
                    context,
                    "InputRouting",
                    "touchpad contact up sequence=$virtualTouchpadGestureSequence pointerId=$pointerId " +
                            "slot=$slot remaining=${virtualTouchpadActiveContactCount()}"
                )
                Log.i(
                    desk.logTag,
                    "touchpad contact up sequence=$virtualTouchpadGestureSequence pointerId=$pointerId " +
                            "slot=$slot remaining=${virtualTouchpadActiveContactCount()}"
                )
            }

            else -> return true
        }
        appendVirtualTouchpadTools(events, virtualTouchpadActiveContactCount())
        events += "EV_SYN"; events += "SYN_REPORT"; events += 0
        val sent = virtualTouchpadEvents(events, allowDirectTouch)
        if (!sent) {
            Log.w(
                desk.logTag,
                "touchpad frame send failed action=${MotionEvent.actionToString(event.action)} " +
                        "sequence=$virtualTouchpadGestureSequence triples=${events.size / 3}"
            )
            resetVirtualTouchpadState("frame_send_failed", logSummary = true)
            return false
        }
        virtualTouchpadFrameCount += 1
        if (action == MotionEvent.ACTION_MOVE) {
            val now = SystemClock.uptimeMillis()
            if (now - virtualTouchpadLastMoveLogAt >= MirrorService.VIRTUAL_TOUCHPAD_MOVE_LOG_INTERVAL_MS) {
                virtualTouchpadLastMoveLogAt = now
                val contacts = (0 until event.pointerCount).joinToString(prefix = "[", postfix = "]") { index ->
                    val pointerId = event.getPointerId(index)
                    val slot = virtualTouchpadSlotPointerIds.indexOf(pointerId)
                    val position = virtualTouchpadPosition(event, index, sourceView)
                    "pointerId=$pointerId,slot=$slot,position=$position"
                }
                Log.d(
                    desk.logTag,
                    "touchpad move sequence=$virtualTouchpadGestureSequence frame=$virtualTouchpadFrameCount " +
                            "pointers=${event.pointerCount} contacts=$contacts"
                )
            }
        }
        if (finishReason != null) {
            val sequence = virtualTouchpadGestureSequence
            val frames = virtualTouchpadFrameCount
            val updates = virtualTouchpadContactUpdateCount
            val duration = (SystemClock.uptimeMillis() - virtualTouchpadGestureStartedAt)
                .coerceAtLeast(0L)
            resetVirtualTouchpadState(finishReason)
            val summary = "touchpad gesture finished reason=$finishReason sequence=$sequence " +
                    "durationMs=$duration frames=$frames contactUpdates=$updates releasedSlots=event sent=true"
            OperationLog.i(context, "InputRouting", summary)
            Log.i(desk.logTag, summary)
        }
        return true
    }
}
