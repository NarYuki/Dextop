package moe.n4tsu.dextop

import android.accessibilityservice.AccessibilityService

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
    fun dp(value: Int): Int
}
