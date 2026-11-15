package moe.n4tsu.dextop

import android.accessibilityservice.AccessibilityService
import android.view.SurfaceView
import android.view.WindowManager
import moe.n4tsu.dextop.input.PrivilegedInputClient

/**
 * What every part of a desktop session may read about it: where the desktop is
 * shown, how large it is, and the privileged channels it was started with.
 */
internal interface DesktopSession {
    /** For the few parts that query windows or dispatch global actions. */
    val accessibilityService: AccessibilityService
    val logTag: String
    val targetDisplayId: Int
    val targetWidth: Int
    val targetHeight: Int
    val density: Int
    val demoMode: Boolean
    val autoOnlySession: Boolean
    val laptopModeActive: Boolean
    val privilegedAccess: PrivilegedAccess
    val plasmaShell: moe.n4tsu.dextop.plasma.DesktopShell?
    /** The overlay window on the phone, null while no session is shown. */
    val root: TouchRoutingFrame?
    val surfaceView: SurfaceView?
    val windowManager: WindowManager?
    val secureDisplay: Boolean
    val showSystemDecorations: Boolean
    /** Touches go straight to the desktop instead of moving the cursor. */
    val directTouch: Boolean
    val suspendedForLockScreen: Boolean
    val desktopEnvironment: DesktopEnvironment
    val privilegedInputClient: PrivilegedInputClient
    val sessionJournal: SessionJournal
    val desktopCursor: DesktopCursor
    val deviceDisplays: DeviceDisplays
    val privilegedInput: PrivilegedInputLink
    val requestedPortrait: Boolean
    val stopping: Boolean
    fun dp(value: Int): Int
}
