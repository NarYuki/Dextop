package moe.n4tsu.dextop

import android.content.Context
import android.util.Log
import android.view.MotionEvent
import android.view.View
import moe.n4tsu.dextop.input.PrivilegedInputClient
import moe.n4tsu.dextop.input.PrivilegedInputProtocol

/** What [PrivilegedInputLink] needs from the session beyond the shared interfaces. */
internal interface PrivilegedInputLinkHost : DesktopSession, OverlayMenu {
    val carCompanion: CarCompanionInput
    val coverButtons: CoverBackButtons
    fun displayGeometrySnapshot(reason: String, hostWidth: Int? = null, hostHeight: Int? = null): String
    val gamepad: VirtualGamepad
    val haptics: Haptics
    val imeRegion: ImeRegionProbe
    val keyboardDeck: KeyboardDeck
    val rawTouchscreen: RawTouchscreenBridge
    val touchSurface: TouchSurface
    val virtualPointer: VirtualPointer
    /** Written here, not only read. */
    override var directTouch: Boolean
}

/** Keeps the privileged input process configured for the current session and falls back to the software pointer when it fails. */
internal class PrivilegedInputLink(
    internal val context: Context,
    internal val desk: PrivilegedInputLinkHost,
) {
    internal var privilegedInputStarting = false

    internal var privilegedPointerFallbackInProgress = false

    internal var privilegedPointerFallbackActive = false

    internal var privilegedInputConfigGeneration = 0

    internal var lastPrivilegedInputSemanticConfig: IntArray? = null

    internal val privilegedInputClientDelegate: Lazy<PrivilegedInputClient> = lazy {
        PrivilegedInputClient(context, object : PrivilegedInputClient.Listener {
            override fun onInputState(category: String, message: String) {
                Log.i(desk.logTag, "privileged input [$category] $message")
                when (category) {
                    "uinput_created" -> {
                        privilegedInputClient.acknowledgeEngineStarted()
                        privilegedInputStarting = false
                        val generation = desk.virtualPointer.virtualMouseGeneration
                        val profile = desk.virtualPointer.virtualPointerRegisteredProfile
                        if (profile.isNotBlank()) {
                            desk.virtualPointer.scheduleVirtualMouseReadyCheck(generation, profile, 0)
                        }
                    }

                    "device_discovered" -> {
                        desk.rawTouchscreen.touchscreenReaderRunning = true
                        // A readable EventHub node is only a candidate.  Do
                        // not suppress the overlay's MotionEvent path until
                        // the native reader has received an actual contact.
                        desk.rawTouchscreen.touchscreenReaderReady = false
                        desk.rawTouchscreen.touchscreenReaderCandidateCount =
                            desk.rawTouchscreen.touchscreenReaderCandidateCount.coerceAtLeast(1)
                        desk.rawTouchscreen.touchscreenReaderDevice = message.substringAfter("path=", "")
                            .substringBefore(' ')
                    }

                    "source_selected" -> {
                        desk.rawTouchscreen.touchscreenReaderRunning = true
                        desk.rawTouchscreen.touchscreenReaderReady = true
                        desk.virtualPointer.virtualPointerOutputObserved = true
                        desk.rawTouchscreen.touchscreenReaderCandidateCount =
                            desk.rawTouchscreen.touchscreenReaderCandidateCount.coerceAtLeast(1)
                        desk.rawTouchscreen.touchscreenReaderDevice = message.substringAfter("path=", "")
                            .substringBefore(' ')
                        desk.desktopCursor.updateVirtualCursorVisibility()
                    }

                    "device_waiting" -> {
                        desk.rawTouchscreen.touchscreenReaderRunning = true
                        desk.rawTouchscreen.touchscreenReaderReady = false
                        desk.rawTouchscreen.touchscreenReaderCandidateCount = 0
                    }

                    "keyboard_created" -> {
                        desk.keyboardDeck.scheduleLaptopKeyboardReadyCheck(desk.keyboardDeck.laptopKeyboardGeneration, 0)
                    }

                    "gamepad_created" -> desk.gamepad.replayVirtualGamepadState()

                    "keyboard_destroyed" -> desk.keyboardDeck.clearLaptopKeyboardPublication("native_destroyed")

                    "stopped" -> {
                        if (!privilegedInputClient.isEngineRunning()) {
                            privilegedInputStarting = false
                            desk.virtualPointer.virtualMouseReady = false
                            desk.virtualPointer.virtualPointerOutputObserved = false
                            desk.rawTouchscreen.touchscreenReaderRunning = false
                            desk.rawTouchscreen.touchscreenReaderReady = false
                            desk.rawTouchscreen.touchscreenReaderCandidateCount = 0
                            desk.desktopCursor.updateVirtualCursorVisibility()
                        }
                    }

                    "disconnected", "worker_exited" -> {
                        privilegedInputClient.acknowledgeEngineStopped()
                        privilegedInputStarting = false
                        desk.virtualPointer.virtualMouseReady = false
                        desk.virtualPointer.virtualPointerOutputObserved = false
                        desk.rawTouchscreen.touchscreenReaderRunning = false
                        desk.rawTouchscreen.touchscreenReaderReady = false
                        desk.rawTouchscreen.touchscreenReaderCandidateCount = 0
                        fallbackToSoftwarePointer("$category:$message")
                    }

                    "uinput_destroyed" -> {
                        desk.virtualPointer.virtualMouseReady = false
                        desk.virtualPointer.virtualPointerOutputObserved = false
                        privilegedInputClient.setOutputReady(false)
                        desk.desktopCursor.updateVirtualCursorVisibility()
                    }

                    "native_error", "client_error" -> {
                        privilegedInputStarting = false
                        val pointerOutputFailed = category == "client_error" ||
                                (message.contains("uinput", ignoreCase = true) &&
                                        !message.contains("keyboard", ignoreCase = true))
                        if (pointerOutputFailed) {
                            fallbackToSoftwarePointer("$category:$message")
                        }
                        OperationLog.w(
                            context,
                            "PrivilegedInput",
                            "$category: $message"
                        )
                    }
                }
                if (category != "raw_event" && category != "uinput_event") {
                    OperationLog.i(
                        context,
                        "PrivilegedInput",
                        "$category: $message"
                    )
                }
            }

            override fun onThreeFingerGesture() {
                OperationLog.i(context, "PrivilegedInput", "three-finger action")
                desk.touchSurface.performConfiguredGesture()
            }

            override fun onHaptic(strong: Boolean) {
                desk.haptics.performLaptopHaptic(desk.keyboardDeck.laptopTrackpadView, strong, HapticSource.POINTER)
            }
        })
    }

    internal val privilegedInputClient: PrivilegedInputClient by privilegedInputClientDelegate

    internal fun buildPrivilegedInputConfig(profile: String): IntArray {
        val windowBounds = desk.windowManager?.currentWindowMetrics?.bounds
        val hostWidth = desk.root?.width?.takeIf { it > 0 }
            ?: desk.surfaceView?.width?.takeIf { it > 0 }
            ?: windowBounds?.width()
            ?: context.resources.displayMetrics.widthPixels
        val hostHeight = desk.root?.height?.takeIf { it > 0 }
            ?: desk.surfaceView?.height?.takeIf { it > 0 }
            ?: windowBounds?.height()
            ?: context.resources.displayMetrics.heightPixels
        val nativeProfile = if (desk.desktopCursor.isMenuOverlayVisible()) {
            PrivilegedInputProtocol.PROFILE_DISABLED
        } else {
            when (profile) {
                "touchpad" -> PrivilegedInputProtocol.PROFILE_TOUCHPAD
                "mouse" -> PrivilegedInputProtocol.PROFILE_MOUSE
                else -> PrivilegedInputProtocol.PROFILE_DISABLED
            }
        }
        val fullscreenBounds = desk.rawTouchscreen.rawTouchscreenViewBounds(desk.surfaceView)
        val trackpadBounds = desk.rawTouchscreen.rawTouchscreenViewBounds(desk.keyboardDeck.laptopTrackpadView)
        // A touchpad reports relative movement from its absolute contact
        // range.  Keep that range proportional to the actual input region;
        // the former fixed landscape 1839×1199 range made portrait input
        // horizontally over-sensitive and vertically sluggish.
        // In laptop mode the trackpad rectangle is only the gesture's entry
        // gate. Once a contact starts there, it may continue through the
        // keyboard and the upper display area. Publish a host-sized absolute
        // range so the native bridge does not pin that contact to the
        // trackpad's top/side edges while it remains down.
        val coverSurface = desk.coverButtons.coverTrackpadSurface
        val companionSurface = desk.carCompanion.carCompanionPhoneTrackpadSurface?.takeIf {
            desk.autoOnlySession && it.isAttachedToWindow
        }
        val touchpadMaxX = if (desk.carCompanion.carCompanionPhoneUsesNativeTouchpad &&
            (companionSurface?.width ?: 0) > 0
        ) {
            checkNotNull(companionSurface).width
        } else if (desk.coverButtons.coverTrackpadActive && (coverSurface?.width ?: 0) > 0) {
            checkNotNull(coverSurface).width
        } else if (desk.laptopModeActive) {
            hostWidth.coerceAtLeast(1)
        } else {
            hostWidth.coerceAtLeast(1)
        }
        val touchpadMaxY = if (desk.carCompanion.carCompanionPhoneUsesNativeTouchpad &&
            (companionSurface?.height ?: 0) > 0
        ) {
            checkNotNull(companionSurface).height
        } else if (desk.coverButtons.coverTrackpadActive && (coverSurface?.height ?: 0) > 0) {
            checkNotNull(coverSurface).height
        } else if (desk.laptopModeActive) {
            hostHeight.coerceAtLeast(1)
        } else {
            hostHeight.coerceAtLeast(1)
        }
        val candidate = PrivilegedInputProtocol.buildConfig(
            profile = nativeProfile,
            rotation = desk.rawTouchscreen.rawTouchscreenDisplayRotation(),
            hostWidth = hostWidth,
            hostHeight = hostHeight,
            fullscreen = fullscreenBounds,
            trackpad = trackpadBounds,
            directTouch = desk.directTouch,
            laptopMode = desk.laptopModeActive,
            touchpadMaxX = touchpadMaxX,
            touchpadMaxY = touchpadMaxY,
            touchpadResolution = MirrorService.VIRTUAL_TOUCHPAD_RESOLUTION,
            debugAllEvents = BuildConfig.DEBUG,
            naturalScroll = desk.virtualPointer.virtualMouseNaturalScroll(),
            mouseSensitivity = 1f,
            generation = 0,
            imeTouch = desk.imeRegion.imeTouchBoundsOnHost()
        )
        val previous = lastPrivilegedInputSemanticConfig
        val changed = previous == null ||
                (0 until PrivilegedInputProtocol.CONFIG_SIZE).any { index ->
                    index != PrivilegedInputProtocol.CONFIG_GENERATION &&
                            previous[index] != candidate[index]
                }
        if (changed) {
            privilegedInputConfigGeneration += 1
            candidate[PrivilegedInputProtocol.CONFIG_GENERATION] = privilegedInputConfigGeneration
            lastPrivilegedInputSemanticConfig = candidate.copyOf()
            Log.i(
                desk.logTag,
                "privileged input semantic config changed generation=$privilegedInputConfigGeneration " +
                        "profile=$profile rotation=${candidate[PrivilegedInputProtocol.CONFIG_ROTATION]} " +
                        "host=${candidate[PrivilegedInputProtocol.CONFIG_HOST_WIDTH]}x" +
                        candidate[PrivilegedInputProtocol.CONFIG_HOST_HEIGHT]
            )
        } else {
            candidate[PrivilegedInputProtocol.CONFIG_GENERATION] =
                checkNotNull(previous)[PrivilegedInputProtocol.CONFIG_GENERATION]
        }
        return candidate
    }

    internal fun refreshPrivilegedInputConfig(reason: String) {
        val profile = desk.virtualPointer.virtualPointerRegisteredProfile.ifBlank { desk.virtualPointer.activeVirtualPointerProfile() }
        if (profile != "touchpad" && profile != "mouse") return
        privilegedInputClient.updateConfig(buildPrivilegedInputConfig(profile))
        Log.i(
            desk.logTag,
            "privileged input geometry updated reason=$reason " +
                    desk.displayGeometrySnapshot("privileged_input_config")
        )
    }

    /**
     * Registers the selected kernel-backed pointer. The mouse profile reports
     * relative motion; the touchpad profile exposes Linux MT Type-B contacts
     * and lets Android's TouchpadInputMapper own acceleration and gestures.
     */

    internal fun fallbackToSoftwarePointer(reason: String) {
        if (privilegedPointerFallbackInProgress || privilegedPointerFallbackActive) return
        if (!MirrorService.active || desk.directTouch && !desk.laptopModeActive && !desk.coverButtons.coverTrackpadActive) {
            privilegedInputStarting = false
            desk.desktopCursor.updateVirtualCursorVisibility()
            return
        }
        if (desk.desktopCursor.isMenuDisplayedForCursor()) {
            // Menu input must remain owned by the menu overlay. A delayed
            // native error during the menu animation is not permission to
            // put the white software cursor on top of the controls.
            privilegedInputStarting = false
            desk.desktopCursor.cursorView?.visibility = View.GONE
            OperationLog.i(
                context,
                "InputRouting",
                "software cursor fallback suppressed while operation menu is displayed reason=$reason"
            )
            return
        }
        privilegedPointerFallbackInProgress = true
        try {
            desk.virtualPointer.stopVirtualMouse()
            desk.virtualPointer.virtualPointerRuntimeProfile = "software"
            privilegedPointerFallbackActive = true
            privilegedInputStarting = false
            desk.virtualPointer.virtualMouseReady = false
            desk.virtualPointer.virtualPointerOutputObserved = false
            desk.desktopCursor.cursorView?.apply {
                visibility = if (desk.directTouch) View.GONE else View.VISIBLE
                if (!desk.directTouch) bringToFront()
            }
            desk.desktopCursor.updateVirtualCursorVisibility()
            val message = "privileged pointer unavailable; using session software cursor reason=$reason"
            OperationLog.w(context, "InputRouting", message)
            Log.w(desk.logTag, message)
        } finally {
            privilegedPointerFallbackInProgress = false
        }
    }
}
