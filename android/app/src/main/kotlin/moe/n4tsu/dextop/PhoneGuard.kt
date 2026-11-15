package moe.n4tsu.dextop

import android.content.Context
import android.os.Binder
import android.os.Handler
import android.util.Log
import android.view.Display

/** What [PhoneGuard] needs from the session beyond the shared interfaces. */
internal interface PhoneGuardHost : DesktopSession, OverlayMenu {
    fun displayGeometrySnapshot(reason: String, hostWidth: Int? = null, hostHeight: Int? = null): String
    val hostDisplayMonitorHandler: Handler
    fun rotate180PreferenceKey(portrait: Boolean): String
    fun systemService(name: String, interfaceName: String): Any
}

/** Keeps the phone's own navigation and rotation out of the way while it hosts the desktop, and restores them. */
internal class PhoneGuard(
    private val context: Context,
    private val desk: PhoneGuardHost,
) {
    internal var lastForcedPhoneHalfTurn: Boolean? = null
    internal var lastForcedPhonePortrait: Boolean? = null
    internal var navigationRestoreGeneration = 0
    internal var navigationSuppressedDisplayId = -1
    internal val navigationToken = Binder()
    internal val desktopNavigationToken = Binder()
    internal val phoneRotationController by lazy {
        PhoneRotationController(context, desk.privilegedAccess, desk.sessionJournal)
    }

    internal fun setPhoneNavigationDisabled(disabled: Boolean) {
        val generation = ++navigationRestoreGeneration
        MainActivity.setSessionSystemBarsHidden(disabled)
        applyNavigationDisabled(Display.DEFAULT_DISPLAY, disabled, navigationToken, "phone")
        val desktopDisplayId = if (disabled) {
            desk.targetDisplayId.takeIf { it >= 0 && it != Display.DEFAULT_DISPLAY }?.also {
                navigationSuppressedDisplayId = it
            }
        } else {
            navigationSuppressedDisplayId.takeIf { it >= 0 }
                ?: desk.targetDisplayId.takeIf { it >= 0 && it != Display.DEFAULT_DISPLAY }
        }
        desktopDisplayId?.let {
            applyNavigationDisabled(it, disabled, desktopNavigationToken, "desktop")
        }
        if (!disabled) navigationSuppressedDisplayId = -1
        if (disabled) return

        // SystemUI can recreate its navigation bar after our overlay is
        // removed. Re-submit the zero flags after those asynchronous passes.
        listOf(120L, 450L, 1_200L, 2_400L, 4_000L).forEach { delay ->
            desk.hostDisplayMonitorHandler.postDelayed({
                if (generation != navigationRestoreGeneration || MirrorService.active && !desk.suspendedForLockScreen) {
                    return@postDelayed
                }
                applyNavigationDisabled(Display.DEFAULT_DISPLAY, false, navigationToken, "phone")
                desktopDisplayId?.let {
                    applyNavigationDisabled(it, false, desktopNavigationToken, "desktop")
                }
            }, delay)
        }
    }

    internal fun applyNavigationDisabled(
        displayId: Int,
        disabled: Boolean,
        token: Binder,
        target: String,
    ) {
        // Last line of defence: Android Auto sessions only ever clear these flags.
        if (disabled && desk.autoOnlySession) return
        runCatching {
            val service = desk.systemService("statusbar", MirrorService.STATUS_BAR_INTERFACE)
            val type = Class.forName(MirrorService.STATUS_BAR_INTERFACE)
            val flags = if (disabled) MirrorService.PHONE_NAVIGATION_DISABLE_FLAGS else 0
            val dexBar = target == "desktop"
            val method = if (dexBar) {
                // Samsung exposes separate phone and DeX status-bar channels.
                // The final integer is a bar type (0 phone, 1 DeX), not a
                // framework display ID.
                type.methods.firstOrNull {
                    it.name == "disableForUserToType" && it.parameterTypes.size == 5
                } ?: type.methods.firstOrNull {
                    it.name == "disableToType" && it.parameterTypes.size == 4
                }
            } else {
                type.methods.firstOrNull {
                    it.name == "disable" && it.parameterTypes.size == 3
                } ?: type.methods.firstOrNull {
                    it.name == "disableForUser" && it.parameterTypes.size == 4
                }
            } ?: error(moe.n4tsu.dextop.InlineStrings.format(moe.n4tsu.dextop.localText("uiNoCompatibleStatusBarDisableOperationFor0"), target))
            val integerCount = method.parameterTypes.count { it == Int::class.javaPrimitiveType }
            var integerIndex = 0
            val args: Array<Any?> = method.parameterTypes.map { parameter ->
                when {
                    parameter == Int::class.javaPrimitiveType -> {
                        val value = when {
                            dexBar && integerIndex == integerCount - 1 -> 1

                            method.name.contains("ForUser") &&
                                integerIndex == integerCount - if (dexBar) 2 else 1 ->
                                android.os.Process.myUid() / 100_000

                            else -> flags
                        }
                        integerIndex += 1
                        value
                    }

                    android.os.IBinder::class.java.isAssignableFrom(parameter) -> token
                    parameter == String::class.java -> context.packageName
                    else -> null
                }
            }.toTypedArray()
            method.invoke(service, *args)
            if (!disabled) {
                // This also clears stale shell-owned flags left by an interrupted
                // recovery on vendor SystemUI implementations.
                runCatching {
                    desk.privilegedAccess.execute("cmd", "statusbar", "send-disable-flag", "none")
                }
                MirrorService.restoreSamsungBottomGestureState(context)
            }
            OperationLog.i(
                context,
                "Navigation",
                "target=$target display=$displayId disabled=$disabled " +
                    "method=${method.name}/${method.parameterTypes.size}"
            )
            Log.i(desk.logTag, "$target navigation display=$displayId disabled=$disabled")
        }.onFailure { error ->
            OperationLog.e(
                context,
                "Navigation",
                "target=$target display=$displayId update failed disabled=$disabled",
                error
            )
            Log.e(desk.logTag, "$target navigation display=$displayId failed disabled=$disabled", error)
        }
    }

    internal fun forcePhoneRotation(portrait: Boolean, force: Boolean = false) {
        val halfTurn = context.getSharedPreferences(MirrorService.PREFS, Context.MODE_PRIVATE)
            .getBoolean(desk.rotate180PreferenceKey(portrait), false)
        if (!force && lastForcedPhonePortrait == portrait && lastForcedPhoneHalfTurn == halfTurn) return
        lastForcedPhonePortrait = portrait
        lastForcedPhoneHalfTurn = halfTurn
        runCatching {
            phoneRotationController.force(portrait, halfTurn)
        }.onSuccess {
            OperationLog.i(
                context,
                "Orientation",
                "phone rotation applied portrait=$portrait " + desk.displayGeometrySnapshot("phone_rotation_applied")
            )
        }.onFailure {
            OperationLog.e(context, "Orientation", "phone rotation failed portrait=$portrait", it)
            Log.e(desk.logTag, "phone rotation lock failed", it)
        }
    }

    internal fun releasePhoneRotation(clearSnapshot: Boolean = false) {
        lastForcedPhonePortrait = null
        lastForcedPhoneHalfTurn = null
        runCatching {
            phoneRotationController.restore(clearSnapshot)
        }.onFailure { Log.e(desk.logTag, "phone rotation unlock failed", it) }
    }
}
