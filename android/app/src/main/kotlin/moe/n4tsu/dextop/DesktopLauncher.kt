package moe.n4tsu.dextop

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/** What [DesktopLauncher] needs from the session beyond the shared interfaces. */
internal interface DesktopLauncherHost : DesktopSession, OverlayMenu {
    val carGestures: CarGestures
    fun clearOverlayDisplayRequestTwice(reason: String)
    val displayBackend: DisplayMirrorBackend
    var displayCreationInProgress: Boolean
    val mirror: MirrorLink
    val phoneOwnedDisplay: OwnedVirtualDisplay?
    val plasmaHost: moe.n4tsu.dextop.plasma.PlasmaShell.Host
    fun postMainDelayed(delayMs: Long, action: () -> Unit)
    val serviceDestroyed: Boolean
    fun stop()
    fun systemService(name: String, interfaceName: String): Any
    fun waitForOverlayRequestCleared(existing: Set<Int>, staleOverlays: Set<Int>, width: Int, height: Int, attempt: Int, decorations: Boolean)
    /** Written here, not only read. */
    override var plasmaShell: moe.n4tsu.dextop.plasma.DesktopShell?
    /** Written here, not only read. */
    override var showSystemDecorations: Boolean
    /** Written here, not only read. */
    override var targetDisplayId: Int
}

/** Starts the desktop home or window manager on the target display and settles its system decorations. */
internal class DesktopLauncher(
    private val context: Context,
    private val desk: DesktopLauncherHost,
) {
    /** True after the one-time Samsung HOME/decorations recovery has been used. */
    internal var homeDecorationRetryUsed = false

    internal fun applyWindowManagerSelection(reason: String): Boolean {
        if (desk.serviceDestroyed || MirrorService.instance !== desk.accessibilityService) return false
        val carShell = desk.autoOnlySession && context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
            .getString("flutter.android_auto_shell", "dextop") == "dextop"
        if (!MirrorService.active || (desk.autoOnlySession && !carShell) || desk.targetDisplayId < 0) return false
        val selection = moe.n4tsu.dextop.plasma.WindowManagerSelection.current(context)
        val wantPlasma = carShell || selection != moe.n4tsu.dextop.plasma.WindowManagerSelection.SYSTEM
        val wantBook = !carShell
        val wantV2 = true
        val existing = desk.plasmaShell
        if (!wantPlasma) {
            if (existing != null) {
                stopPlasmaShell(restoreWindows = true)
                launchHome()
                OperationLog.i(context, "PlasmaShell", "switched to system window manager reason=$reason")
            }
            return true
        }
        val existingIsV2 = existing is moe.n4tsu.dextop.plasma.CompositedWindowShell
        val existingIsAuto = existing is moe.n4tsu.dextop.plasma.DextopAutoShell
        val existingIsBook = existing is moe.n4tsu.dextop.plasma.BookShell
        if (existing != null && existing.displayId == desk.targetDisplayId && existingIsV2 == wantV2 &&
            existingIsAuto == carShell && existingIsBook == wantBook) return true
        // Switching between v1, v2 and Book hands windows back to the display first.
        if (existing != null) stopPlasmaShell(restoreWindows = existingIsV2 != wantV2 || existingIsBook != wantBook)
        return runCatching {
            (if (carShell) {
                moe.n4tsu.dextop.plasma.DextopAutoShell(
                    context, desk.targetDisplayId, desk.targetWidth, desk.targetHeight, desk.density, desk.plasmaHost,
                    fullscreenAppDisplays = true,
                    passiveShellFocus = desk.desktopEnvironment.passivePlasmaShellFocus,
                    maxRateAppDisplays = desk.desktopEnvironment.maxRateApplicationDisplays,
                    applicationOverlay = desk.desktopEnvironment.plasmaApplicationOverlay && desk.phoneOwnedDisplay == null,
                    privateAppDisplays = desk.desktopEnvironment.privateApplicationDisplays,
                )
            } else {
                moe.n4tsu.dextop.plasma.BookShell(
                    context, desk.targetDisplayId, desk.targetWidth, desk.targetHeight, desk.density, desk.plasmaHost,
                    fullscreenAppDisplays = desk.desktopEnvironment.fullscreenApplicationDisplays,
                    passiveShellFocus = desk.desktopEnvironment.passivePlasmaShellFocus,
                    maxRateAppDisplays = desk.desktopEnvironment.maxRateApplicationDisplays,
                    applicationOverlay = desk.desktopEnvironment.plasmaApplicationOverlay && desk.phoneOwnedDisplay == null,
                    privateAppDisplays = desk.desktopEnvironment.privateApplicationDisplays,
                )
            }).also {
                    desk.plasmaShell = it
                    it.start()
                    if (carShell) desk.carGestures.carVisibleArea?.let { r -> it.setAutoViewport(r[0],r[1],r[2],r[3]) }
                }
        }.onSuccess {
            OperationLog.i(context, "PlasmaShell", "Dextop window manager active display=${desk.targetDisplayId} reason=$reason")
        }.onFailure {
            desk.plasmaShell = null
            OperationLog.e(context, "PlasmaShell", "Dextop window manager failed to start reason=$reason", it)
        }.isSuccess
    }

    /**
     * Starts exactly one desktop shell.  Plasma is a replacement HOME, not a
     * layer to be added after Samsung's SecondaryLauncher has already created
     * a DeX desktop and taskbar on the same display.
     */

    /** Starts the Dextop Auto shell again with growing pauses, e.g. while Dextop's access reconnects. */
    internal fun retryDextopAutoShell(displayId: Int, attempt: Int) {
        if (attempt >= 10) {
            OperationLog.w(context, "PlasmaShell", "Dextop Auto shell did not start on display $displayId", null)
            return
        }
        desk.postMainDelayed((500L shl attempt.coerceAtMost(4)).coerceAtMost(8_000L)) {
            if (!MirrorService.active || !desk.autoOnlySession || desk.targetDisplayId != displayId ||
                desk.plasmaShell is moe.n4tsu.dextop.plasma.DextopAutoShell) return@postMainDelayed
            if (!PrivilegedAccess(desk.logTag).isAvailable()) {
                moe.n4tsu.dextop.privilege.EmbeddedPrivilegeKeeper.ensure(context, "dextop_auto_shell")
            }
            if (!applyWindowManagerSelection("dextop_auto_retry_${attempt + 1}")) retryDextopAutoShell(displayId, attempt + 1)
            else OperationLog.i(context, "PlasmaShell", "Dextop Auto shell started on retry ${attempt + 1}")
        }
    }

    internal fun launchSelectedDesktopHome(reason: String): Boolean {
        val plasmaSelected = if (desk.autoOnlySession) {
            context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
                .getString("flutter.android_auto_shell", "dextop") == "dextop"
        } else moe.n4tsu.dextop.plasma.WindowManagerSelection.isPlasma(context)
        return if (plasmaSelected) {
            applyWindowManagerSelection(reason)
        } else {
            launchHome()
        }
    }

    internal fun stopPlasmaShell(restoreWindows: Boolean) {
        val shell = desk.plasmaShell ?: return
        desk.plasmaShell = null
        runCatching { shell.stop(restoreWindows) }
            .onFailure { OperationLog.w(context, "PlasmaShell", "shell shutdown failed", it) }
    }

    internal fun setDesktopSystemDecorationsVisible(displayId: Int, visible: Boolean) {
        runCatching {
            val service = desk.systemService("window", "android.view.IWindowManager")
            Class.forName("android.view.IWindowManager").getMethod(
                "setShouldShowSystemDecors",
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType
            ).invoke(service, displayId, visible)
        }.onSuccess {
            OperationLog.i(
                context,
                "SystemDecorations",
                "display=$displayId visible=$visible"
            )
            Log.i(desk.logTag, "desktop system decorations display=$displayId visible=$visible")
        }.onFailure {
            OperationLog.e(
                context,
                "SystemDecorations",
                "display=$displayId update failed visible=$visible",
                it
            )
        }
    }

    internal fun launchHome(): Boolean {
        val intent = if (desk.desktopEnvironment.id == "samsung_dex") {
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_SECONDARY_HOME)
                .setComponent(
                    ComponentName(
                        "com.sec.android.app.launcher",
                        "com.honeyspace.dexservice.SecondaryLauncher"
                    )
                )
        } else {
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(desk.targetDisplayId)
        val direct = runCatching { context.startActivity(intent, options.toBundle()) }
        if (direct.isFailure && intent.component != null && desk.privilegedAccess.isAvailable()) {
            val component = intent.component!!.flattenToShortString()
            val category = if (desk.desktopEnvironment.id == "samsung_dex") {
                Intent.CATEGORY_SECONDARY_HOME
            } else {
                Intent.CATEGORY_HOME
            }
            val result = desk.privilegedAccess.execute(
                "am", "start",
                "--display", desk.targetDisplayId.toString(),
                "-a", Intent.ACTION_MAIN,
                "-c", category,
                "-n", component,
            )
            if (result.succeeded) {
                OperationLog.i(
                    context,
                    "DesktopHome",
                    "HOME launched through privileged fallback display=${desk.targetDisplayId} component=$component"
                )
                Log.i(desk.logTag, "home launched through privileged fallback display=${desk.targetDisplayId}")
                return true
            }
            val failure = IllegalStateException(
                moe.n4tsu.dextop.InlineStrings.format(moe.n4tsu.dextop.localText("uiPrivilegedHOMELaunchFailed0"), result.error.ifBlank { result.output }),
                direct.exceptionOrNull(),
            )
            OperationLog.e(context, "DesktopHome", "HOME launch failed display=${desk.targetDisplayId}", failure)
            Log.e(desk.logTag, "home launch failed", failure)
            return false
        }
        return direct.onSuccess {
            Log.i(
                desk.logTag,
                "home launched display=${desk.targetDisplayId} component=${intent.component} " +
                    "decorations=${desk.showSystemDecorations}"
            )
        }.onFailure {
            OperationLog.e(
                context,
                "DesktopHome",
                "HOME launch failed display=${desk.targetDisplayId} decorations=${desk.showSystemDecorations}",
                it
            )
            Log.e(desk.logTag, "home launch failed", it)
        }.isSuccess
    }

    /**
     * Samsung records whether a firmware needed system decorations for HOME
     * launches. The record is keyed by the full build fingerprint: after an
     * OTA we optimistically try the original configuration again, and only
     * persist the workaround again if that firmware still rejects HOME.
     */

    internal fun firmwareIdentity(): String = Build.FINGERPRINT.ifBlank {
        listOf(Build.DISPLAY, Build.VERSION.INCREMENTAL, Build.VERSION.SECURITY_PATCH)
            .joinToString("/")
    }

    internal fun shouldUsePersistedSystemDecorations(): Boolean {
        if (!desk.desktopEnvironment.platformManaged) return false
        val preferences = context.getSharedPreferences("dextop_home_launch_recovery", Context.MODE_PRIVATE)
        if (desk.desktopEnvironment.id == "samsung_dex") {
            // Samsung's SECONDARY_HOME can be launched explicitly on an
            // undecorated display. Old builds persisted decorations after the
            // generic HOME intent was rejected; keeping that workaround would
            // recreate the redundant Android NavigationBar forever.
            preferences.edit().remove("firmware_fingerprint").apply()
            return false
        }
        val stored = preferences.getString("firmware_fingerprint", null) ?: return false
        if (stored == firmwareIdentity()) return true
        // Firmware changed. Remove the old workaround so this build gets a
        // clean first attempt; a failed HOME launch will store the new one.
        preferences.edit().remove("firmware_fingerprint").apply()
        OperationLog.i(
            context,
            "DesktopHome",
            "firmware changed; retrying HOME without persisted system decorations"
        )
        return false
    }

    private fun rememberSystemDecorationsForFirmware() {
        if (!desk.desktopEnvironment.platformManaged) return
        context.getSharedPreferences("dextop_home_launch_recovery", Context.MODE_PRIVATE)
            .edit()
            .putString("firmware_fingerprint", firmwareIdentity())
            .apply()
    }

    /**
     * Recreates the overlay with system decorations and lets the normal
     * attach path retry HOME. The existing surface/window stays alive, so the
     * caller's session is not dropped while OverlayDisplayAdapter replaces
     * the display instance.
     */

    internal fun retryHomeLaunchWithSystemDecorations(hostWidth: Int, hostHeight: Int): Boolean {
        if (desk.showSystemDecorations || !desk.desktopEnvironment.platformManaged || homeDecorationRetryUsed) {
            return false
        }
        homeDecorationRetryUsed = true
        rememberSystemDecorationsForFirmware()
        val previousDisplayId = desk.targetDisplayId
        val existingDisplays = desk.displayBackend.currentDisplayIds()
        val staleOverlays = desk.displayBackend.overlayDisplayIds()
        OperationLog.w(
            context,
            "DesktopHome",
            "HOME launch denied on display=$previousDisplayId; retrying with system decorations"
        )
        desk.displayBackend.releaseLayer()
        desk.mirror.mirrorDisplayId = -1
        desk.targetDisplayId = -1
        desk.showSystemDecorations = true
        desk.sessionJournal.preparing(desk.targetWidth, desk.targetHeight, desk.density, decorations = true)
        runCatching { desk.clearOverlayDisplayRequestTwice("before_home_decoration_retry") }
            .onFailure {
                desk.displayCreationInProgress = false
                OperationLog.e(context, "DesktopHome", "unable to clear display before HOME retry", it)
                MirrorService.completeStart(Result.failure(it))
                desk.stop()
                return true
            }
        desk.postMainDelayed(MirrorService.HOME_DECORATION_RETRY_DELAY_MS) {
            if (MirrorService.active && !desk.stopping) {
                desk.waitForOverlayRequestCleared(
                    existingDisplays,
                    staleOverlays,
                    hostWidth,
                    hostHeight,
                    0,
                    decorations = true
                )
            }
        }
        return true
    }
}
