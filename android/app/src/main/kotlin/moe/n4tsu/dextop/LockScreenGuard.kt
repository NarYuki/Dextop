package moe.n4tsu.dextop

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.BroadcastReceiver
import android.os.Handler
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.widget.LinearLayout

/** What [LockScreenGuard] needs from the session beyond the shared interfaces. */
internal interface LockScreenGuardHost : DesktopSession, OverlayMenu {
    fun configForHostGeometry(base: MirrorService.Config, hostWidth: Int, hostHeight: Int, systemDensity: Int): MirrorService.Config
    val desktopModeConfigurator: DesktopModeConfigurator
    val externalOutput: MirrorService.ExternalOutput?
    val keyboardDeckStyle: MirrorService.KeyboardDeckStyle
    val laptopContent: LinearLayout?
    val phoneGuard: PhoneGuard
    fun removeWindow()
    fun setLaptopMode(enabled: Boolean, requestedStyle: MirrorService.KeyboardDeckStyle = MirrorService.KeyboardDeckStyle.LAPTOP)
    fun shouldFollowHostDisplay(): Boolean
    fun start(config: MirrorService.Config)
    fun stopHostDisplayMonitor()
    /** Written here, not only read. */
    override var targetDisplayId: Int
}

/** Suspends the session when the phone locks and resumes it once the unlock is confirmed. */
internal class LockScreenGuard(
    private val context: Context,
    private val desk: LockScreenGuardHost,
) {
    private var suspendedKeyboardDeckStyle: MirrorService.KeyboardDeckStyle? = null

    internal var screenReceiverRegistered = false

    internal var suspendedForLockScreen = false

    internal var suspendedConfig: MirrorService.Config? = null

    internal var screenLifecycleGeneration = 0

    internal var keyguardLockObservedSinceScreenOff = false

    internal var unlockCandidateSince = 0L

    internal var unlockResumeScheduled = false

    internal val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!MirrorService.active) return
            Log.i(
                desk.logTag,
                "screen lifecycle broadcast action=${intent?.action} " +
                        "suspended=$suspendedForLockScreen generation=$screenLifecycleGeneration"
            )
            // Android Auto keeps running with the phone asleep. The lock-screen suspend is for the
            // phone's own desktop: on an Auto-only session it removed the car display and, after
            // unlock, recreated the session as a phone desktop that opened on the phone.
            if (desk.autoOnlySession) return
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> if (desk.externalOutput?.mode == "external") {
                    OperationLog.i(this@LockScreenGuard.context, "ExternalOutput", "phone screen off; the external desktop stays")
                } else suspendForLockScreen()
                Intent.ACTION_SCREEN_ON -> waitForConfirmedUnlock(screenLifecycleGeneration)
                Intent.ACTION_USER_PRESENT -> resumeAfterUnlock("user_present")
            }
        }
    }

    private fun suspendForLockScreen() {
        if (suspendedForLockScreen) {
            screenLifecycleGeneration += 1
            keyguardLockObservedSinceScreenOff = false
            unlockCandidateSince = 0L
            unlockResumeScheduled = false
            Log.i(
                desk.logTag,
                "screen off reaffirmed while suspended; cancelled pending unlock " +
                        "screenGeneration=$screenLifecycleGeneration"
            )
            return
        }
        screenLifecycleGeneration += 1
        keyguardLockObservedSinceScreenOff = false
        unlockCandidateSince = 0L
        unlockResumeScheduled = false
        suspendedConfig = MirrorService.Config(desk.targetWidth, desk.targetHeight, desk.density, desk.secureDisplay, desk.showSystemDecorations)
        suspendedKeyboardDeckStyle = desk.keyboardDeckStyle.takeIf { desk.laptopModeActive }
        suspendedForLockScreen = true
        desk.stopHostDisplayMonitor()
        desk.phoneGuard.setPhoneNavigationDisabled(false)
        desk.phoneGuard.releasePhoneRotation()
        MainActivity.restoreOrientation()
        runCatching { desk.desktopModeConfigurator.restore() }
            .onFailure { Log.e(desk.logTag, "lock-screen settings restoration failed", it) }
        desk.removeWindow()
        desk.targetDisplayId = -1
        Log.i(
            desk.logTag,
            "session suspended; all display and overlay windows removed " +
                    "screenGeneration=$screenLifecycleGeneration"
        )
    }

    private fun resumeAfterUnlock(reason: String) {
        if (!suspendedForLockScreen || unlockResumeScheduled) return
        // Never turn an Auto-only session into a phone desktop.
        if (desk.autoOnlySession) { suspendedForLockScreen = false; return }
        val previous = suspendedConfig ?: return
        val bounds = desk.windowManager?.currentWindowMetrics?.bounds
        val config = if (desk.shouldFollowHostDisplay() && bounds != null &&
            bounds.width() >= 480 && bounds.height() >= 480
        ) {
            desk.configForHostGeometry(
                previous,
                bounds.width(),
                bounds.height(),
                context.resources.configuration.densityDpi
            )
        } else previous
        val generation = screenLifecycleGeneration
        unlockResumeScheduled = true
        Handler(context.mainLooper).postDelayed({
            if (!suspendedForLockScreen || !unlockResumeScheduled ||
                generation != screenLifecycleGeneration
            ) return@postDelayed
            Log.i(desk.logTag, "unlock resume executing reason=$reason screenGeneration=$generation")
            val styleToRestore = suspendedKeyboardDeckStyle
            desk.start(config)
            if (styleToRestore != null) restoreKeyboardDeckAfterUnlock(styleToRestore)
        }, 250)
        Log.i(
            desk.logTag,
            "unlock confirmed; session recreation scheduled reason=$reason " +
                    "screenGeneration=$generation"
        )
    }

    private fun restoreKeyboardDeckAfterUnlock(style: MirrorService.KeyboardDeckStyle, attempt: Int = 0) {
        Handler(context.mainLooper).postDelayed({
            if (!MirrorService.active || desk.root == null || desk.laptopContent == null) {
                if (attempt < 30) restoreKeyboardDeckAfterUnlock(style, attempt + 1)
                return@postDelayed
            }
            suspendedKeyboardDeckStyle = null
            desk.setLaptopMode(true, style)
            OperationLog.i(
                context,
                "LaptopMode",
                "restored ${style.name.lowercase()} deck after unlock attempt=$attempt",
            )
        }, if (attempt == 0) 280L else 100L)
    }

    private fun waitForConfirmedUnlock(generation: Int, attempt: Int = 0) {
        if (!suspendedForLockScreen || unlockResumeScheduled ||
            generation != screenLifecycleGeneration
        ) return
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        val locked = keyguard.isDeviceLocked || keyguard.isKeyguardLocked
        val secure = keyguard.isDeviceSecure
        val interactive = context.getSystemService(PowerManager::class.java).isInteractive
        val now = SystemClock.elapsedRealtime()
        if (locked) {
            if (!keyguardLockObservedSinceScreenOff) {
                Log.i(
                    desk.logTag,
                    "keyguard lock observed after screen off attempt=$attempt " +
                            "screenGeneration=$generation"
                )
            }
            keyguardLockObservedSinceScreenOff = true
            unlockCandidateSince = 0L
        } else if (interactive && (!secure || keyguardLockObservedSinceScreenOff)) {
            if (unlockCandidateSince == 0L) {
                unlockCandidateSince = now
                Log.i(
                    desk.logTag,
                    "unlock candidate observed attempt=$attempt secure=$secure " +
                            "lockObserved=$keyguardLockObservedSinceScreenOff " +
                            "screenGeneration=$generation"
                )
            }
            val stableFor = now - unlockCandidateSince
            if (stableFor >= 750L) {
                Log.i(
                    desk.logTag,
                    "keyguard reports stably unlocked attempt=$attempt stableForMs=$stableFor " +
                            "screenGeneration=$generation"
                )
                resumeAfterUnlock("stable_keyguard_confirmation")
                return
            }
        } else {
            unlockCandidateSince = 0L
            if (attempt == 0 || attempt == 4 || attempt == 20) {
                Log.i(
                    desk.logTag,
                    "unlock confirmation waiting attempt=$attempt interactive=$interactive " +
                            "secure=$secure locked=$locked " +
                            "lockObserved=$keyguardLockObservedSinceScreenOff " +
                            "screenGeneration=$generation"
                )
            }
        }
        if (attempt < 120) {
            Handler(context.mainLooper).postDelayed(
                { waitForConfirmedUnlock(generation, attempt + 1) },
                250
            )
        } else {
            Log.w(
                desk.logTag,
                "unlock wait timed out; leaving session suspended " +
                        "secure=$secure lockObserved=$keyguardLockObservedSinceScreenOff " +
                        "screenGeneration=$generation"
            )
        }
    }
}
