package moe.n4tsu.dextop

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Handler
import android.util.Log
import android.view.Display
import android.view.Surface

/** What [DisplayFactory] needs from the session beyond the shared interfaces. */
internal interface DisplayFactoryHost : DesktopSession, OverlayMenu {
    fun activateTopologyForIndependentDisplays(reason: String)
    fun applyLaptopGeometryWhenLaidOut(enabled: Boolean, attempt: Int = 0, baseOverride: MirrorService.Config? = null, preserveMenu: Boolean = false)
    val coverDisplayController: CoverDisplaySessionController
    var cursorX: Float
    var cursorY: Float
    val desktopModeConfigurator: DesktopModeConfigurator
    val displayBackend: DisplayMirrorBackend
    fun displayGeometrySnapshot(reason: String, hostWidth: Int? = null, hostHeight: Int? = null): String
    val externalOutputs: ExternalDisplays
    val forceFreeformSession: Boolean
    val imeRegion: ImeRegionProbe
    val launcher: DesktopLauncher
    val mirror: MirrorLink
    val phoneGuard: PhoneGuard
    val physicalInput: PhysicalInput
    fun postMainDelayed(delayMs: Long, action: () -> Unit)
    val requestedPortrait: Boolean
    fun scheduleHostDisplayReconfiguration(reason: String, width: Int? = null, height: Int? = null, densityDpi: Int? = null)
    fun scheduleTopologyReapplyAfterReconnect()
    fun startAutoOverlapCorrection()
    fun stop()
    val stopping: Boolean
    fun synchronizeDesktopWallpaperDimensions(width: Int, height: Int, reason: String)
    fun systemService(name: String, interfaceName: String): Any
    fun updateCursorPosition(x: Float = cursorX, y: Float = cursorY)
    fun updateVirtualCursorVisibility()
    val virtualPointer: VirtualPointer
    val workspaces: Workspaces
    /** Written here, not only read. */
    override var showSystemDecorations: Boolean
    /** Written here, not only read. */
    override var targetDisplayId: Int
}

/** Creates the desktop display for the session, attaches it and configures it. */
internal class DisplayFactory(
    private val context: Context,
    private val desk: DisplayFactoryHost,
) {
    internal var autoDestinationSurface: Surface? = null

    internal var autoOwnedDisplay: OwnedVirtualDisplay? = null

    /** Phone desktop drawn directly into the host Surface; see DesktopEnvironment.directPlasmaDisplay. */
    internal var phoneOwnedDisplay: OwnedVirtualDisplay? = null

    internal var displayCreationInProgress = false

    internal fun clearOverlayDisplayRequestTwice(reason: String) {
        val preservedSpecs = AndroidAutoMirrorActivity.autoOverlaySpecs() +
            desk.coverDisplayController.ownedSpecs()
        repeat(2) { pass ->
            runCatching { desk.displayBackend.clearRequestPreserving(preservedSpecs) }
                .onSuccess {
                    OperationLog.i(
                        context,
                        "DisplayBackend",
                        "clear request issued reason=$reason pass=${pass + 1}/2 " +
                                "preservedIndependent=${preservedSpecs.isNotEmpty()}"
                    )
                }
                .onFailure {
                    Log.e(
                        desk.logTag,
                        "unable to clear overlay display request reason=$reason pass=${pass + 1}/2",
                        it
                    )
                }
        }
    }

    private fun displayRotationFor(@Suppress("UNUSED_PARAMETER") portrait: Boolean = desk.requestedPortrait): Int = 0

    /** Reconnects a recreated host Surface without removing the desktop display or its tasks. */
    internal fun reattachExistingDisplay(width: Int, height: Int) {
        if (displayCreationInProgress || desk.mirror.mirrorDisplayId >= 0 || desk.targetDisplayId < 0) return
        displayCreationInProgress = true
        val strategyOverride = desk.mirror.configuredMirrorStrategyOverride()
        runCatching {
            desk.mirror.attachMirror(width, height, strategyOverride)
            desk.mirror.mirrorDisplayId = desk.targetDisplayId
        }.onSuccess {
            displayCreationInProgress = false
            desk.virtualPointer.startVirtualMouse()
            // releaseMirror() stopped the physical mouse reader.
            if (desk.externalOutputs.physicalExternalDisplayConnected && desk.physicalInput.routePhysicalMouseToDextop) desk.physicalInput.startRawMouseReader()
            desk.imeRegion.requestImeRegionProbe("surface_reattached", force = true)
            desk.updateVirtualCursorVisibility()
            desk.scheduleHostDisplayReconfiguration("host Surface recreated", width, height)
            // A Surface recreation can happen during a fold/half-open handoff
            // while the laptop deck is already visible.  The first geometry
            // pass may have run before the recreated surface was attached;
            // synchronize the measured upper pane again after the mirror is
            // connected so the desktop is not left at the full-panel size.
            if (desk.laptopModeActive) {
                desk.root?.postDelayed({
                    if (MirrorService.active && desk.laptopModeActive && desk.targetDisplayId >= 0) {
                        desk.applyLaptopGeometryWhenLaidOut(true)
                    }
                }, 80L)
                OperationLog.i(
                    context,
                    "LaptopMode",
                    "host Surface recreated while laptop mode active; queued pane geometry synchronization"
                )
            }
            desk.scheduleTopologyReapplyAfterReconnect()
            OperationLog.i(
                context,
                "DisplayBackend",
                "reattached host Surface to display=${desk.targetDisplayId} host=${width}x$height; tasks retained"
            )
            OperationLog.i(context, "DisplayGeometry", desk.displayGeometrySnapshot("surface_reattached", width, height))
        }.onFailure { error ->
            displayCreationInProgress = false
            desk.mirror.mirrorDisplayId = -1
            desk.mirror.restoreSoftwareCursorAfterMirrorFailure("existing display reattach")
            OperationLog.e(context, "DisplayBackend", "existing display reattach failed", error)
            Log.e(desk.logTag, "existing display reattach failed", error)
        }
    }

    internal fun createDisplay(surface: Surface, width: Int, height: Int) {
        if (desk.mirror.mirrorDisplayId >= 0 || displayCreationInProgress) return
        if (useDirectPlasmaDisplay()) {
            createDirectPhoneDisplay(surface, width, height)
            return
        }
        displayCreationInProgress = true
        CapabilityProbe(context, desk.privilegedAccess).run().forEach { (name, probe) ->
            OperationLog.i(context, "CapabilityProbe", "$name supported=${probe.supported} detail=${probe.detail}")
        }
        runCatching {
            val existing = desk.displayBackend.currentDisplayIds()
            val staleOverlays = desk.displayBackend.overlayDisplayIds()
            desk.desktopModeConfigurator.applyForCurrentDevice()
            clearOverlayDisplayRequestTwice("before_display_create")
            desk.root?.postDelayed({
                waitForOverlayRequestCleared(
                    existing,
                    staleOverlays,
                    width,
                    height,
                    0,
                    desk.showSystemDecorations
                )
            }, 150)
        }.onFailure { error ->
            displayCreationInProgress = false
            OperationLog.e(context, "MirrorService", "display creation failed", error)
            Log.e(desk.logTag, "display creation failed", error)
            MirrorService.completeStart(Result.failure(error))
            desk.stop()
        }
    }

    private fun useDirectPlasmaDisplay(): Boolean =
        desk.desktopEnvironment.directPlasmaDisplay && !desk.autoOnlySession && !desk.laptopModeActive &&
            moe.n4tsu.dextop.plasma.WindowManagerSelection.current(context) in setOf(
                moe.n4tsu.dextop.plasma.WindowManagerSelection.DEXTOP_PLASMA_V2,
                moe.n4tsu.dextop.plasma.WindowManagerSelection.DEXTOP_BOOK)

    /**
     * Creates the phone desktop as a public VirtualDisplay that renders
     * straight into the host Surface (no overlay display, no recording
     * mirror). Everything after creation is shared with the overlay path.
     */

    private fun createDirectPhoneDisplay(surface: Surface, width: Int, height: Int) {
        displayCreationInProgress = true
        runCatching {
            check(surface.isValid) { NativeStrings.text("nativeMirrorSurfaceUnavailable") }
            CapabilityProbe(context, desk.privilegedAccess).run().forEach { (name, probe) ->
                OperationLog.i(context, "CapabilityProbe", "$name supported=${probe.supported} detail=${probe.detail}")
            }
            desk.desktopModeConfigurator.applyForCurrentDevice()
            clearOverlayDisplayRequestTwice("before_direct_phone_display_create")
            val platform = VirtualDisplayPlatform.inspect()
            val service = desk.privilegedAccess.service("display", VirtualDisplayPlatform.MANAGER_INTERFACE)
            phoneOwnedDisplay?.attachment?.release()
            val owned = platform.openOwned(
                context,
                service,
                surface,
                desk.targetWidth,
                desk.targetHeight,
                desk.density,
                desk.showSystemDecorations,
                configureBeforePublish = { displayId ->
                    if (desk.desktopEnvironment.id == MirrorService.XIAOMI_PROFILE) applyXiaomiDesktopDensity(displayId, desk.density)
                },
                publicDisplay = true,
                name = "Dextop Desktop",
            )
            phoneOwnedDisplay = owned
            val display = context.getSystemService(DisplayManager::class.java).getDisplay(owned.displayId)
                ?: error(moe.n4tsu.dextop.InlineStrings.format(moe.n4tsu.dextop.localText("uiTheDirectDextopDisplay0IsUnavailable"), owned.displayId))
            OperationLog.i(
                context,
                "DisplayBackend",
                "direct Plasma display created display=${owned.displayId} ${desk.targetWidth}x${desk.targetHeight}/${desk.density} " +
                    "host=${width}x$height"
            )
            display
        }.onSuccess { display ->
            attachDesktopDisplay(display, width, height)
        }.onFailure { error ->
            displayCreationInProgress = false
            runCatching { phoneOwnedDisplay?.attachment?.release() }
            phoneOwnedDisplay = null
            OperationLog.e(context, "MirrorService", "direct Plasma display creation failed", error)
            Log.e(desk.logTag, "direct Plasma display creation failed", error)
            MirrorService.completeStart(Result.failure(error))
            desk.stop()
        }
    }

    /**
     * Creates the Dextop overlay without adding a host window to the phone.
     * Android Auto owns the destination Surface in this mode and attaches a
     * separate recording VirtualDisplay to the overlay once the display id is
     * published. This keeps the phone display untouched.
     */

    internal fun createHeadlessDisplay() {
        if (desk.targetDisplayId >= 0 || displayCreationInProgress) return
        displayCreationInProgress = true
        CapabilityProbe(context, desk.privilegedAccess).run().forEach { (name, probe) ->
            OperationLog.i(context, "CapabilityProbe", "$name supported=${probe.supported} detail=${probe.detail}")
        }
        runCatching {
            desk.desktopModeConfigurator.applyForCurrentDevice()
            // Remove a stale OverlayDisplayAdapter request left by an older
            // CARDEX build, then create an app-owned display directly on the
            // head-unit Surface. No phone-side Overlay window is involved.
            clearOverlayDisplayRequestTwice("before_hidden_auto_display_create")
            createDirectAutoDisplay(desk.showSystemDecorations)
        }.onFailure { error ->
            displayCreationInProgress = false
            OperationLog.e(context, "AndroidAuto", "headless display creation failed", error)
            Log.e(desk.logTag, "headless Auto display creation failed: ${error.message}", error)
            MirrorService.completeStart(Result.failure(error))
            desk.stop()
        }
    }

    private fun createDirectAutoDisplay(decorations: Boolean) {
        val destination = autoDestinationSurface
            ?: error(moe.n4tsu.dextop.localText("uiTheDextopCarCompanionDestinationSurfaceIsUnavailable"))
        val platform = VirtualDisplayPlatform.inspect()
        val service = desk.privilegedAccess.service(
            "display",
            VirtualDisplayPlatform.MANAGER_INTERFACE
        )
        autoOwnedDisplay?.attachment?.release()
        val dextopAutoSelected = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
            .getString("flutter.android_auto_shell", "dextop") == "dextop"
        // Dextop Auto draws the whole car display and keeps each app on a display of its own.
        // With system decorations or desktop windowing Android puts its own desktop here (DeX on
        // Samsung, HyperOS's desktop on Xiaomi), which shows in the car whenever Dextop Auto is
        // not drawn over it. The display stays public and trusted so Dextop's windows can use it.
        val displayDecorations = decorations && !dextopAutoSelected
        val (outputWidth, outputHeight) = autoDestinationSize()
        val owned = platform.openOwned(
            context,
            service,
            destination,
            outputWidth,
            outputHeight,
            desk.density,
            displayDecorations,
            configureBeforePublish = { displayId ->
                if (desk.forceFreeformSession && !dextopAutoSelected) {
                    desk.desktopModeConfigurator.configureDisplay(displayId, forceFreeform = true)
                }
            },
            publicDisplay = dextopAutoSelected,
        )
        autoOwnedDisplay = owned
        desk.targetDisplayId = owned.displayId
        desk.mirror.mirrorDisplayId = owned.displayId
        desk.showSystemDecorations = displayDecorations
        // An Android Auto session never disables navigation or gestures: the phone stays
        // fully usable, and the car display has no phone navigation to suppress.
        clearInheritedDisplayOverrides(desk.targetDisplayId)
        applyAutoLogicalSize()
        configureDisplay(skipWindowing = desk.forceFreeformSession || dextopAutoSelected)
        if (!dextopAutoSelected) desk.startAutoOverlapCorrection()
        if (dextopAutoSelected) {
            // Dextop Auto never falls back to a decorated platform desktop (DeX's desktop on
            // Samsung); a shell that cannot start yet is started again on the same display.
            if (!desk.launcher.applyWindowManagerSelection("auto_display_created")) desk.launcher.retryDextopAutoShell(owned.displayId, 0)
        } else if (!desk.launcher.launchSelectedDesktopHome("auto_display_created")) {
            if (!decorations) {
                OperationLog.w(
                    context,
                    "DesktopHome",
                    "hidden Auto HOME rejected; retrying with system decorations",
                    null
                )
                owned.attachment.release()
                autoOwnedDisplay = null
                desk.targetDisplayId = -1
                desk.mirror.mirrorDisplayId = -1
                createDirectAutoDisplay(true)
                return
            }
            error(moe.n4tsu.dextop.localText("uiTheDesktopHOMEActivityCouldNotBeLaunched"))
        }
        if (desk.showSystemDecorations && desk.desktopEnvironment.platformManaged && !desk.forceFreeformSession) {
            listOf(250L, 700L, 1_500L).forEach { delay ->
                val decoratedDisplayId = desk.targetDisplayId
                desk.postMainDelayed(delay) {
                    if (MirrorService.active && desk.targetDisplayId == decoratedDisplayId) {
                        desk.launcher.setDesktopSystemDecorationsVisible(decoratedDisplayId, false)
                    }
                }
            }
        }
        displayCreationInProgress = false
        desk.sessionJournal.running(desk.targetDisplayId)
        runCatching {
            DisplayEnvironmentSettings(context).activateTopologyForOverlays(setOf(desk.targetDisplayId))
        }.onFailure { OperationLog.w(context, "DisplayTopology", "Auto topology activation skipped", it) }
        MirrorService.completeStart(
            Result.success(
                mapOf(
                    "displayId" to desk.targetDisplayId,
                    "width" to desk.targetWidth,
                    "height" to desk.targetHeight,
                    "density" to desk.density,
                    "decorations" to desk.showSystemDecorations
                )
            )
        )
        OperationLog.i(
            context,
            "AndroidAuto",
            "hidden direct Auto display ready display=${desk.targetDisplayId} ${desk.targetWidth}x${desk.targetHeight}/${desk.density}"
        )
    }

    internal fun autoDestinationSize(): Pair<Int, Int> =
        CardexRelayService.activeDestinationSize() ?: (desk.targetWidth to desk.targetHeight)

    /** Keep the car's physical buffer fixed; WMS fits the scaled desktop into it. */
    internal fun applyAutoLogicalSize() {
        val service = desk.systemService("window", "android.view.IWindowManager")
        val type = Class.forName("android.view.IWindowManager")
        type.getMethod("setForcedDisplaySize", Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            .invoke(service, desk.targetDisplayId, desk.targetWidth, desk.targetHeight)
    }

    internal fun replaceAutoDestinationSurface(nextSurface: Surface?) {
        if (!desk.autoOnlySession || nextSurface == null || !nextSurface.isValid) return
        autoDestinationSurface = nextSurface
        val (outputWidth, outputHeight) = autoDestinationSize()
        val updated = autoOwnedDisplay?.resize(
            nextSurface,
            outputWidth,
            outputHeight,
            desk.density,
        ) == true
        check(updated) { moe.n4tsu.dextop.localText("uiUnableToMoveTheAndroidAutoDesktopToTheNewHostSurface") }
        applyAutoLogicalSize()
        OperationLog.i(
            context,
            "CarCompanion",
            "Auto-owned display moved to replacement host surface display=${desk.targetDisplayId}",
        )
    }

    internal fun waitForOverlayRequestCleared(
        existing: Set<Int>,
        staleOverlays: Set<Int>,
        width: Int,
        height: Int,
        attempt: Int,
        decorations: Boolean
    ) {
        if (!MirrorService.active || desk.stopping) return
        // No overlay request is active during this wait. Treat *any* overlay
        // still reported by DisplayManager as stale, including one whose id
        // was published just after the initial inventory snapshot.
        val independentDisplayIds = AndroidAutoMirrorActivity.autoOverlayDisplayIds() +
            desk.coverDisplayController.ownedDisplayIds()
        val remaining = desk.displayBackend.overlayDisplayIds()
            .filterNot { it in independentDisplayIds }
            .toSet()
        if (remaining.isNotEmpty()) {
            if (attempt < 60) {
                desk.postMainDelayed(100L) {
                    waitForOverlayRequestCleared(
                        existing,
                        staleOverlays,
                        width,
                        height,
                        attempt + 1,
                        decorations
                    )
                }
                return
            }
            val error = IllegalStateException(
                moe.n4tsu.dextop.localText("uiOverlayDisplayCleanupTimedOutRefusingToCreateADuplicateDisplay") +
                        "ids=${remaining.sorted()} knownBeforeClear=${staleOverlays.sorted()}"
            )
            displayCreationInProgress = false
            OperationLog.e(context, "DisplayBackend", error.message ?: "overlay cleanup timed out", error)
            MirrorService.completeStart(Result.failure(error))
            desk.stop()
            return
        }
        runCatching {
            desk.displayBackend.requestDisplay(
                desk.targetWidth,
                desk.targetHeight,
                desk.density,
                desk.secureDisplay,
                decorations,
                AndroidAutoMirrorActivity.autoOverlaySpecs() +
                    desk.coverDisplayController.ownedSpecs(),
                ownContentOnly = !desk.autoOnlySession &&
                    moe.n4tsu.dextop.plasma.WindowManagerSelection.isPlasma(context),
            )
            waitForOverlay(existing, width, height, 0)
        }.onFailure { error ->
            displayCreationInProgress = false
            OperationLog.e(context, "MirrorService", "display request failed", error)
            MirrorService.completeStart(Result.failure(error))
            desk.stop()
        }
    }

    private fun waitForOverlay(existing: Set<Int>, width: Int, height: Int, attempt: Int) {
        // A phone and Auto session may request the same logical spec. Never
        // attach the phone Surface to the Auto-owned display merely because
        // that display was allocated after the phone inventory snapshot.
        val independentDisplayIds = AndroidAutoMirrorActivity.autoOverlayDisplayIds() +
            desk.coverDisplayController.ownedDisplayIds()
        val display = desk.displayBackend.findCreatedDisplay(existing, independentDisplayIds)
        if (display == null) {
            if (attempt < 40) desk.postMainDelayed(100L) {
                waitForOverlay(existing, width, height, attempt + 1)
            }
            else {
                val error = IllegalStateException(moe.n4tsu.dextop.localText("uiOverlayDisplayCreationTimedOut"))
                displayCreationInProgress = false
                MirrorService.completeStart(Result.failure(error))
                Log.e(desk.logTag, "overlay display creation timed out", error)
                desk.stop()
            }
            return
        }
        attachDesktopDisplay(display, width, height)
    }

    /** Publishes [display] as the Dextop desktop: navigation, mirror/host Surface, input, HOME and menus. */
    private fun attachDesktopDisplay(display: Display, width: Int, height: Int) {
        runCatching {
            check(desk.privilegedAccess.isAvailable()) {
                NativeStrings.text("nativeShizukuUnavailable")
            }
            desk.targetDisplayId = display.displayId
            // The session-level navigation request is made before the asynchronous
            // overlay display exists. Apply it again now that SystemUI has created
            // a per-display navigation bar for the actual desktop display.
            if (MirrorService.active && !desk.autoOnlySession && desk.targetDisplayId != Display.DEFAULT_DISPLAY) {
                desk.phoneGuard.navigationSuppressedDisplayId = desk.targetDisplayId
                desk.phoneGuard.applyNavigationDisabled(
                    desk.targetDisplayId,
                    true,
                    desk.phoneGuard.desktopNavigationToken,
                    "desktop"
                )
            }
            desk.imeRegion.requestImeRegionProbe("display_attached", force = true)
            clearInheritedDisplayOverrides(desk.targetDisplayId)
            if (desk.desktopEnvironment.id == MirrorService.XIAOMI_PROFILE) applyXiaomiDesktopDensity(desk.targetDisplayId, desk.density)
            configureDisplay()
            if (!desk.autoOnlySession) {
                val strategyOverride = desk.mirror.configuredMirrorStrategyOverride()
                desk.mirror.attachMirror(width, height, strategyOverride)
            } else {
                OperationLog.i(
                    context,
                    "AndroidAuto",
                    "overlay display created without phone host display=${desk.targetDisplayId}"
                )
            }
            desk.mirror.mirrorDisplayId = desk.targetDisplayId
            // Auto laptop detection can run from addWindow() before the
            // overlay display exists. In that case the first layout pass has
            // already split the host Surface, but applyLaptopGeometry...()
            // correctly deferred because targetDisplayId was still -1. Run
            // the pane synchronization once the display is attached so the
            // logical resolution and VirtualDisplay buffer use the measured
            // upper pane instead of the original full-screen profile.
            if (!desk.autoOnlySession && desk.laptopModeActive) {
                desk.root?.postDelayed({
                    if (MirrorService.active && desk.laptopModeActive && desk.targetDisplayId >= 0) {
                        desk.applyLaptopGeometryWhenLaidOut(true)
                    }
                }, 80L)
                OperationLog.i(
                    context,
                    "LaptopMode",
                    "display attached while laptop mode active; queued pane geometry synchronization"
                )
            }
            // Topology is an optional enhancement. A framework with a vendor
            // IDisplayManager fork may reject its hidden transaction (for
            // example with RESTRICT_DISPLAY_MODES); that must not abort an
            // otherwise usable VirtualDisplay session.
            desk.activateTopologyForIndependentDisplays("main_display_attached")
            displayCreationInProgress = false
            desk.sessionJournal.running(desk.targetDisplayId)
            desk.synchronizeDesktopWallpaperDimensions(
                desk.targetWidth,
                desk.targetHeight,
                "display attached",
            )
            val externalDisplays = desk.externalOutputs.externalDisplayDetector.snapshot()
            desk.externalOutputs.physicalExternalDisplayConnected = desk.physicalInput.physicalInputRoutingSupported && externalDisplays.connected
            val routedInputCount = if (!desk.autoOnlySession && (desk.externalOutputs.physicalExternalDisplayConnected || desk.physicalInput.routePhysicalMice)) runCatching {
                desk.physicalInput.physicalInputRouter.routeConnectedDevices(
                    display,
                    desk.physicalInput.routePhysicalMice,
                    desk.physicalInput.routePhysicalKeyboardToDextop
                )
            }
                .onFailure { OperationLog.w(context, "InputRouting", "input routing unavailable", it) }
                .getOrDefault(0) else 0
            OperationLog.i(
                context,
                "DisplayRouting",
                "externalConnected=${externalDisplays.connected} externalIds=${externalDisplays.displayIds} routedInputs=$routedInputCount"
            )
            OperationLog.i(context, "DisplayGeometry", desk.displayGeometrySnapshot("mirror_attached", width, height))
            if (!desk.autoOnlySession) desk.virtualPointer.startVirtualMouse()
            if (!desk.autoOnlySession && desk.externalOutputs.physicalExternalDisplayConnected && desk.physicalInput.routePhysicalMouseToDextop) {
                desk.physicalInput.startRawMouseReader()
            } else {
                desk.physicalInput.stopRawMouseReader()
            }
            desk.root?.postDelayed({
                desk.physicalInput.refreshActualRoutingState(display)
                desk.menuPrimary?.let(desk::showMainMenu)
            }, 350)
            if (!desk.autoOnlySession) desk.menuPrimary?.let(desk::showMainMenu)
            desk.cursorX = desk.targetWidth / 2f
            desk.cursorY = desk.targetHeight / 2f
            if (!desk.autoOnlySession) desk.updateCursorPosition(desk.targetWidth / 2f, desk.targetHeight / 2f)
            if (!desk.launcher.launchSelectedDesktopHome("display_attached")) {
                // One UI 8 rejects HOME launches on Samsung-owned virtual
                // displays that do not advertise system decorations. Rebuild
                // the display once with that flag instead of tearing down a
                // session which otherwise mirrored successfully.
                if (desk.launcher.retryHomeLaunchWithSystemDecorations(width, height)) {
                    return@runCatching
                }
                error(moe.n4tsu.dextop.localText("uiTheDesktopHOMEActivityCouldNotBeLaunched"))
            }
            // Some Samsung builds require the display to advertise system
            // decorations while HOME is launched, then create both their DeX
            // taskbar and Android's regular NavigationBar. Once HOME has been
            // accepted, withdraw only the framework decorations so the DeX
            // taskbar remains as the sole bottom bar.
            if (desk.showSystemDecorations && desk.desktopEnvironment.platformManaged && !desk.forceFreeformSession) {
                listOf(250L, 700L, 1_500L).forEach { delay ->
                    val decoratedDisplayId = desk.targetDisplayId
                    desk.root?.postDelayed({
                        if (MirrorService.active && desk.targetDisplayId == decoratedDisplayId) {
                            desk.launcher.setDesktopSystemDecorationsVisible(decoratedDisplayId, false)
                        }
                    }, delay)
                }
            }
            desk.workspaces.pendingPausedWorkspace?.takeUnless { desk.autoOnlySession }?.let { workspace ->
                desk.workspaces.pendingPausedWorkspace = null
                desk.root?.postDelayed({
                    if (!MirrorService.active || desk.targetDisplayId < 0) return@postDelayed
                    desk.workspaces.launchOverlayWorkspace(workspace, closeMenu = false)
                    context.getSharedPreferences("dextop_cleanup_state", Context.MODE_PRIVATE).edit()
                        .remove("paused_workspace")
                        .apply()
                    OperationLog.i(
                        context,
                        "Workspace",
                        "restored paused workspace apps=${workspace.optJSONArray("apps")?.length() ?: 0}"
                    )
                }, 900)
            }
            // One UI may migrate the foreground phone task to a newly created
            // desktop display. Move the phone control activity back explicitly
            // before a separate DesktopActivity can be launched there.
            if (!desk.autoOnlySession) desk.root?.postDelayed({ ensurePhoneControlOnDefaultDisplay() }, 500)
            if (!desk.autoOnlySession) {
                desk.externalOutputs.externalAutoAttemptedFor = -1
                desk.root?.postDelayed({ desk.externalOutputs.restoreExternalDisplayMode("session_started") }, if (desk.externalOutputs.externalStartPending) 150L else 1_500L)
            }
            MirrorService.completeStart(
                Result.success(
                    mapOf(
                        "displayId" to desk.targetDisplayId,
                        "width" to desk.targetWidth,
                        "height" to desk.targetHeight,
                        "density" to desk.density,
                        "decorations" to desk.showSystemDecorations
                    )
                )
            )
            Log.i(
                desk.logTag,
                "Dextop layer attached target=${desk.targetDisplayId} ${desk.targetWidth}x${desk.targetHeight} " +
                        "autoOnly=${desk.autoOnlySession}"
            )
        }.onFailure { error ->
            OperationLog.e(context, "MirrorService", "all mirror strategies failed", error)
            Log.e(desk.logTag, "display mirror attachment failed; stopping safely", error)
            displayCreationInProgress = false
            MirrorService.completeStart(Result.failure(error))
            desk.stop()
        }
    }

    /**
     * Samsung persists forced metrics by the overlay unique id (overlay:1),
     * not by its newly allocated display id. Without clearing them, a newly
     * created 2340x1080 overlay can inherit the previous 1080x2340 override.
     * This is used only for a new overlay; live fold resizing keeps its override.
     */

    private fun clearInheritedDisplayOverrides(displayId: Int) {
        val service = desk.systemService("window", "android.view.IWindowManager")
        val type = Class.forName("android.view.IWindowManager")
        val userId = android.os.UserHandle::class.java
            .getMethod("myUserId").invoke(null) as Int
        runCatching {
            type.getMethod("clearForcedDisplaySize", Int::class.javaPrimitiveType)
                .invoke(service, displayId)
        }.onFailure { Log.w(desk.logTag, "inherited display size clear failed display=$displayId", it) }
        runCatching {
            type.getMethod(
                "clearForcedDisplayDensityForUser",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            ).invoke(service, displayId, userId)
        }.onFailure { Log.w(desk.logTag, "inherited display density clear failed display=$displayId", it) }
        // HyperOS keeps reporting the phone's size and density for a new virtual
        // display after the clear, so the desktop draws a shrunken portrait phone
        // layout. Setting the display's own metrics explicitly does stick.
        if (desk.desktopEnvironment.id == MirrorService.XIAOMI_PROFILE && displayId == desk.targetDisplayId && desk.targetWidth > 0 && desk.targetHeight > 0) {
            runCatching {
                type.getMethod("setForcedDisplaySize", Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                    .invoke(service, displayId, desk.targetWidth, desk.targetHeight)
                type.getMethod("setForcedDisplayDensityForUser", Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                    .invoke(service, displayId, desk.density, userId)
            }.onFailure { Log.w(desk.logTag, "Xiaomi display metrics pin failed display=$displayId", it) }
        }
        OperationLog.i(context, "DisplayBackend", "cleared inherited metrics display=$displayId")
    }

    private fun applyXiaomiDesktopDensity(displayId: Int, dpi: Int) {
        val service = desk.systemService("window", "android.view.IWindowManager")
        val userId = android.os.UserHandle::class.java.getMethod("myUserId").invoke(null) as Int
        Class.forName("android.view.IWindowManager").getMethod(
            "setForcedDisplayDensityForUser", Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
        ).invoke(service, displayId, dpi, userId)
        Log.i(desk.logTag, "Xiaomi desktop density applied display=$displayId dpi=$dpi")
    }

    internal fun configureDisplay(skipWindowing: Boolean = false) {
        val service = desk.systemService("window", "android.view.IWindowManager")
        val type = Class.forName("android.view.IWindowManager")
        // Width/height define portrait or landscape; an optional persisted
        // half-turn is then applied independently for that orientation.
        val rotation = displayRotationFor()
        if (!skipWindowing) {
            desk.desktopModeConfigurator.configureDisplay(desk.targetDisplayId, desk.forceFreeformSession)
        }
        runCatching {
            type.getMethod(
                "setIgnoreOrientationRequest",
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType
            ).invoke(service, desk.targetDisplayId, true)
        }.onSuccess {
            OperationLog.i(context, "Orientation", "ignore orientation request applied display=${desk.targetDisplayId}")
        }.onFailure {
            OperationLog.e(context, "Orientation", "orientation request lock failed display=${desk.targetDisplayId}", it)
            Log.e(desk.logTag, "orientation request lock failed", it)
        }
        runCatching {
            type.getMethod(
                "setFixedToUserRotation",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            ).invoke(service, desk.targetDisplayId, 2)
        }.onSuccess {
            OperationLog.i(context, "Orientation", "fixed rotation applied display=${desk.targetDisplayId} value=2")
        }.onFailure {
            OperationLog.e(context, "Orientation", "fixed rotation failed display=${desk.targetDisplayId}", it)
            Log.e(desk.logTag, "fixed rotation failed", it)
        }
        applyDisplayRotation(rotation, service, type)
        runCatching {
            type.getMethod("setShouldShowSystemDecors", Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
                .invoke(service, desk.targetDisplayId, desk.showSystemDecorations)
        }
        runCatching {
            type.getMethod("setDisplayImePolicy", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                // A headless Android Auto display has nowhere useful to draw
                // its local IME. Route it to the phone so Gboard (or the
                // selected phone IME) appears there while the focused editor
                // remains on the Dextop display.
                .invoke(service, desk.targetDisplayId, if (desk.autoOnlySession) 1 else 0)
        }
        Log.i(desk.logTag, "Dextop display configured display=${desk.targetDisplayId} rotation=$rotation")
    }

    internal fun applyDisplayRotation(
        rotation: Int,
        service: Any? = null,
        type: Class<*>? = null
    ) {
        if (desk.targetDisplayId < 0) return
        val windowService = service ?: desk.systemService("window", "android.view.IWindowManager")
        val windowType = type ?: Class.forName("android.view.IWindowManager")
        runCatching {
            val method = windowType.methods.first {
                it.name == "freezeDisplayRotation" && it.parameterTypes.size >= 2
            }
            val args = method.parameterTypes.mapIndexed { index, parameter ->
                when {
                    index == 0 -> desk.targetDisplayId
                    index == 1 -> rotation
                    parameter == String::class.java -> context.packageName
                    parameter == Boolean::class.javaPrimitiveType -> true
                    else -> null
                }
            }.toTypedArray()
            method.invoke(windowService, *args)
        }.onSuccess {
            OperationLog.i(
                context,
                "Orientation",
                "display rotation lock applied display=${desk.targetDisplayId} rotation=$rotation"
            )
        }.onFailure {
            OperationLog.e(context, "Orientation", "display rotation lock failed display=${desk.targetDisplayId}", it)
            Log.e(desk.logTag, "display rotation lock failed", it)
        }
    }

    private fun ensurePhoneControlOnDefaultDisplay() {
        runCatching {
            val intent = Intent(context, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            val options = ActivityOptions.makeBasic().setLaunchDisplayId(Display.DEFAULT_DISPLAY)
            context.startActivity(intent, options.toBundle())
            Log.i(desk.logTag, "phone control activity pinned to default display")
        }.onFailure { Log.e(desk.logTag, "unable to pin phone control activity", it) }
    }

    /**
     * Where the direct display renders while the desktop is extended: nobody looks at it, the
     * screens mirror their parts. Frames are dropped as they arrive.
     */

    internal fun directDisplaySurface(): Surface {
        desk.externalOutputs.extendSink?.takeIf { it.width == desk.targetWidth && it.height == desk.targetHeight }?.let { return it.surface }
        desk.externalOutputs.closeExtendSink()
        val sink = android.media.ImageReader.newInstance(desk.targetWidth, desk.targetHeight, android.graphics.PixelFormat.RGBA_8888, 2)
        sink.setOnImageAvailableListener({ reader -> runCatching { reader.acquireLatestImage()?.close() } }, Handler(context.mainLooper))
        desk.externalOutputs.extendSink = sink
        return sink.surface
    }

    internal fun releasePhoneOwnedDisplay(reason: String) {
        val owned = phoneOwnedDisplay ?: return
        phoneOwnedDisplay = null
        runCatching { owned.attachment.release() }
            .onFailure { Log.e(desk.logTag, "direct Plasma display release failed", it) }
        desk.externalOutputs.closeExtendSink()
        OperationLog.i(context, "DisplayBackend", "direct Plasma display released display=${owned.displayId} reason=$reason")
    }
}
