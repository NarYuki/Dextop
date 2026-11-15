package moe.n4tsu.dextop

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import moe.n4tsu.dextop.input.PrivilegedInputClient

/** What [CoverBackButtons] needs from the session beyond the shared interfaces. */
internal interface CoverBackButtonsHost : DesktopSession, OverlayMenu {
    fun activateTopologyForIndependentDisplays(reason: String)
    fun cancelDesktopTouchStream()
    val cast: CastController
    var coverBackButtonsActive: Boolean
    val coverBackButtonsPressed: MutableSet<Int>
    val coverDisplayController: CoverDisplaySessionController
    fun coverDisplayLifecycleToken(): String
    var coverTrackpadActive: Boolean
    var coverTrackpadPreviousRuntimeProfile: String?
    var coverTrackpadSurface: View?
    val directTouch: Boolean
    val gamepad: VirtualGamepad
    fun isFoldableDevice(): Boolean
    val menuWidgets: MenuWidgets
    val privilegedInputClient: PrivilegedInputClient
    fun refreshPrivilegedInputConfig(reason: String)
    fun showCoverDisplayFailure(error: Throwable)
    fun showCoverDisplayMenu(panel: LinearLayout)
    fun trackpad(event: MotionEvent, sourceView: View, forceCursorMode: Boolean = false, allowVirtualPointer: Boolean = false, hapticView: View? = null): Boolean
    val virtualPointer: VirtualPointer
    val virtualTouchpad: VirtualTouchpad
}

/** Back-button and trackpad surfaces shown on a foldable's cover display. */
internal class CoverBackButtons(
    private val context: Context,
    private val desk: CoverBackButtonsHost,
) {
    internal fun enableCoverBackButtons() {
        if (!desk.gamepad.isExperimentalGamepadEnabled()) {
            desk.coverDisplayController.stopBackButtonMode { }
            return
        }
        desk.coverBackButtonsActive = true
        desk.privilegedInputClient.setGamepadVisible(true)
    }

    internal fun disableCoverBackButtons() {
        releaseCoverBackButtonInputs()
        desk.coverBackButtonsActive = false
        if (!desk.gamepad.virtualGamepadVisible) desk.privilegedInputClient.setGamepadVisible(false)
    }

    internal fun injectCoverBackButtonInternal(code: Int, pressed: Boolean) {
        if (!desk.coverBackButtonsActive) return
        if (pressed) desk.coverBackButtonsPressed.add(code) else desk.coverBackButtonsPressed.remove(code)
        desk.privilegedInputClient.injectGamepad(code, if (pressed) 1 else 0)
    }

    private fun releaseCoverBackButtonInputs() {
        desk.coverBackButtonsPressed.toList().forEach { code ->
            desk.privilegedInputClient.injectGamepad(code, 0)
        }
        desk.coverBackButtonsPressed.clear()
    }

    private fun showCoverBackButtonsConfirmation(panel: LinearLayout) {
        desk.cast.stopCastRouteDiscovery()
        desk.menuWidgets.animateMenuResize(panel)
        panel.removeAllViews()
        panel.addView(desk.menuWidgets.menuTitle(
            NativeStrings.text("nativeBackButtonsConfirmTitle"),
            NativeStrings.text("nativeReturn"),
        ) { desk.showCoverDisplayMenu(panel) })
        panel.addView(desk.menuWidgets.menuHint(NativeStrings.text("nativeBackButtonsConfirmMessage")))
        panel.addView(desk.menuWidgets.actionButton(
            R.drawable.ic_gamepad,
            NativeStrings.text("nativeOk"),
        ) { startCoverBackButtonMode(panel) })
        panel.addView(desk.menuWidgets.actionButton(
            R.drawable.ic_chevron,
            NativeStrings.text("nativeCancel"),
        ) { desk.showCoverDisplayMenu(panel) })
        desk.scheduleMenuHeightUpdate()
    }

    private fun startCoverBackButtonMode(panel: LinearLayout) {
        if (!desk.gamepad.isExperimentalGamepadEnabled()) {
            desk.showCoverDisplayMenu(panel)
            return
        }
        desk.coverDisplayController.startBackButtonMode(desk::coverDisplayLifecycleToken) { result ->
            result.onSuccess {
                desk.activateTopologyForIndependentDisplays("cover_back_buttons_started")
            }.onFailure { desk.showCoverDisplayFailure(it) }
            desk.showCoverDisplayMenu(panel)
        }
    }

    internal fun syncCoverInputForKeyboardStyle() {
        if (desk.demoMode || !desk.isFoldableDevice()) return
        val shouldUseBackButtons = desk.gamepad.isGamepadDeckActive() && desk.gamepad.isExperimentalGamepadEnabled()
        val state = desk.coverDisplayController.state()
        if (shouldUseBackButtons && !state.backButtonsActive && !state.busy) {
            desk.coverDisplayController.startBackButtonMode(desk::coverDisplayLifecycleToken) { result ->
                result.onSuccess { desk.activateTopologyForIndependentDisplays("cover_back_buttons_auto_started") }
                    .onFailure { desk.showCoverDisplayFailure(it) }
            }
        } else if (!shouldUseBackButtons && state.backButtonsActive && !state.busy) {
            desk.coverDisplayController.stopBackButtonMode { result ->
                result.onFailure { desk.showCoverDisplayFailure(it) }
            }
        }
    }

    internal fun attachCoverTrackpad(surface: View) {
        if (desk.coverTrackpadSurface === surface && desk.coverTrackpadActive) return
        if (!desk.coverTrackpadActive) {
            desk.coverTrackpadPreviousRuntimeProfile = desk.virtualPointer.virtualPointerRuntimeProfile
        }
        desk.coverTrackpadSurface = surface
        desk.coverTrackpadActive = true
        desk.cancelDesktopTouchStream()
        desk.virtualPointer.stopVirtualMouse()
        desk.virtualPointer.virtualPointerRuntimeProfile = "touchpad"
        desk.virtualPointer.startVirtualMouse("touchpad")
        surface.post { desk.refreshPrivilegedInputConfig("cover_trackpad_attached") }
        OperationLog.i(context, "InputRouting", "cover display attached to uinput touchpad")
    }

    internal fun handleCoverTrackpadEvent(surface: View, event: MotionEvent) {
        if (!desk.coverTrackpadActive || desk.coverTrackpadSurface !== surface) return
        desk.trackpad(
            event,
            surface,
            forceCursorMode = true,
            allowVirtualPointer = true,
            hapticView = surface,
        )
    }

    internal fun detachCoverTrackpad(surface: View) {
        if (desk.coverTrackpadSurface !== surface) return
        if (desk.virtualTouchpad.virtualTouchpadActiveContactCount() > 0) {
            desk.virtualTouchpad.finishVirtualTouchpadGesture("cover_trackpad_detached", allowDirectTouch = true)
        }
        desk.coverTrackpadSurface = null
        desk.coverTrackpadActive = false
        desk.virtualPointer.stopVirtualMouse()
        desk.virtualPointer.virtualPointerRuntimeProfile = desk.coverTrackpadPreviousRuntimeProfile
        desk.coverTrackpadPreviousRuntimeProfile = null
        if (MirrorService.active && (!desk.directTouch || desk.laptopModeActive)) desk.virtualPointer.startVirtualMouse()
        OperationLog.i(context, "InputRouting", "cover display detached from uinput touchpad")
    }
}
