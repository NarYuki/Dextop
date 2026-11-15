package moe.n4tsu.dextop

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.util.Log
import android.view.Surface
import android.view.WindowManager

/** What [AndroidPause] needs from the session beyond the shared interfaces. */
internal interface AndroidPauseHost : DesktopSession, OverlayMenu {
    val coverMenu: CoverDisplayMenu
    val desktopModeConfigurator: DesktopModeConfigurator
    fun detachHostWindow()
    val displayFactory: DisplayFactory
    val hostFollower: HostDisplayFollower
    val lockScreen: LockScreenGuard
    val mirror: MirrorLink
    var overlayLayoutEditing: Boolean
    val phoneGuard: PhoneGuard
    /** Written here, not only read. */
    override var stopping: Boolean
    val workspaces: Workspaces
    /** Written here, not only read. */
    override var targetDisplayId: Int
}

/** Temporarily returns the phone to Android and restores the session afterwards. */
internal class AndroidPause(
    private val context: Context,
    private val desk: AndroidPauseHost,
) {
    internal var pausedForAndroid = false

    internal val pauseLifecycleHandler by lazy { Handler(context.mainLooper) }

    internal var pauseDisableSelfRunnable: Runnable? = null

    internal fun temporarilyReturnToAndroid() {
        val pausedWorkspace = desk.workspaces.captureCurrentWorkspace()
        pausedForAndroid = true
        MirrorService.active = false
        desk.hostFollower.stopHostDisplayMonitor()
        desk.sessionJournal.paused()
        context.getSharedPreferences("dextop_cleanup_state", Context.MODE_PRIVATE).edit()
            .putBoolean("cleanup_pending", true)
            .putBoolean("paused_by_user", true)
            .putLong("paused_at", System.currentTimeMillis())
            .apply {
                if (pausedWorkspace == null) remove("paused_workspace")
                else putString("paused_workspace", pausedWorkspace.toString())
            }
            .commit()
        desk.overlayLayoutEditing = false
        desk.lockScreen.suspendedForLockScreen = false
        desk.lockScreen.suspendedConfig = null
        desk.lockScreen.screenLifecycleGeneration += 1
        desk.lockScreen.unlockResumeScheduled = false
        desk.lockScreen.unlockCandidateSince = 0L
        desk.phoneGuard.setPhoneNavigationDisabled(false)
        desk.phoneGuard.releasePhoneRotation(clearSnapshot = true)
        // Detach the accessibility host first. Surface destruction normally
        // releases the VirtualDisplay immediately, which makes One UI try to
        // unregister gesture-exclusion listeners from an already removed
        // display. That WindowManager exception leaves Back and Circle to
        // Search broken until SystemUI is restarted.
        desk.detachHostWindow()
        android.os.Handler(context.mainLooper).postDelayed({
            val autoSessionActive = AndroidAutoMirrorActivity.isAutoSessionActive()
            val coverSessionActive = desk.coverMenu.coverDisplayController.state().desktopActive
            runCatching {
                if (autoSessionActive || coverSessionActive) {
                    DisplayEnvironmentSettings(context).activateTopologyForOverlays(
                        AndroidAutoMirrorActivity.autoOverlayDisplayIds() +
                            desk.coverMenu.coverDisplayController.ownedDisplayIds()
                    )
                } else {
                    DisplayEnvironmentSettings(context).restoreTopology()
                }
            }.onFailure { Log.e(desk.logTag, "topology restoration failed", it) }
            desk.mirror.releaseMirror()
            desk.displayFactory.releasePhoneOwnedDisplay("temporary_android_return")
            desk.displayFactory.clearOverlayDisplayRequestTwice("temporary_android_return")
            desk.targetDisplayId = -1
            desk.sessionJournal.restoreWallpaperDimensions()
            if (!autoSessionActive && !coverSessionActive) desk.desktopModeConfigurator.restore()
            runCatching { desk.hostFollower.internalRefreshRateController.restore() }
                .onFailure { Log.e(desk.logTag, "refresh-rate restoration failed", it) }
            MainActivity.restoreOrientation()
            runCatching {
                val home = Intent(context, MainActivity::class.java)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                val options = ActivityOptions.makeBasic().setLaunchDisplayId(0)
                context.startActivity(home, options.toBundle())
            }.onFailure { Log.e(desk.logTag, "unable to return to Dextop home", it) }
            // Keep SessionJournal intact: the app shows its explicit recovery card.
            // Leave the service alive while the delayed navigation restores run;
            // some vendor SystemUI builds reapply the flags several seconds
            // after the accessibility window is removed.
            val detach = Runnable {
                pauseDisableSelfRunnable = null
                if (pausedForAndroid && !MirrorService.active && !desk.stopping) {
                    desk.accessibilityService.disableSelf()
                } else {
                    Log.i(
                        desk.logTag,
                        "skipped stale pause detach active=${MirrorService.active} " +
                                "paused=$pausedForAndroid stopping=${desk.stopping}"
                    )
                }
            }
            pauseDisableSelfRunnable?.let(pauseLifecycleHandler::removeCallbacks)
            pauseDisableSelfRunnable = detach
            pauseLifecycleHandler.postDelayed(detach, 4_500L)
        }, 320)
        Log.i(desk.logTag, "session paused; returned to Dextop home for explicit recovery")
    }
}
