package moe.n4tsu.dextop

import android.content.Context
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.Handler
import android.util.Log
import android.view.Display
import android.view.MotionEvent
import android.view.Surface
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import kotlin.math.roundToInt

/** What [HostDisplayFollower] needs from the session beyond the shared interfaces. */
internal interface HostDisplayFollowerHost : DesktopSession, OverlayMenu {
    fun applyHostDisplayOrientation(portrait: Boolean)
    val coverDisplayController: CoverDisplaySessionController
    val displayFactory: DisplayFactory
    fun displayGeometrySnapshot(reason: String, hostWidth: Int? = null, hostHeight: Int? = null): String
    val externalOutputs: ExternalDisplays
    val keyboardDeck: KeyboardDeck
    val laptopModeSwitch: LaptopMode
    val launcher: DesktopLauncher
    val lockScreen: LockScreenGuard
    fun menuLayoutParams(): FrameLayout.LayoutParams
    fun menuUsesLandscapeLayout(): Boolean
    val mirror: MirrorLink
    val phoneGuard: PhoneGuard
    val posture: LaptopPosture
    val rawTouchscreen: RawTouchscreenBridge
    fun scheduleCoverDisplayLifecycleCheck(reason: String)
    fun systemService(name: String, interfaceName: String): Any
    val touchSurface: TouchSurface
    val virtualPointer: VirtualPointer
    val workspaces: Workspaces
    /** Written here, not only read. */
    override var density: Int
    /** Written here, not only read. */
    override var targetHeight: Int
    /** Written here, not only read. */
    override var targetWidth: Int
}

/** Follows the phone display: resizes the desktop when the host geometry changes and reapplies topology. */
internal class HostDisplayFollower(
    private val context: Context,
    private val desk: HostDisplayFollowerHost,
) {
    internal fun isWorkspaceMagnificationActive(): Boolean {
        val value = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
            .all["flutter.desktop_workspace_magnification_percent"]
        return (value as? Number)?.toInt()?.let { it > 100 } == true
    }

    internal val internalRefreshRateController by lazy {
        InternalRefreshRateController(context, desk.sessionJournal)
    }

    private var refreshRateReapplyGeneration = 0

    private var topologyReapplyGeneration = 0

    internal val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            desk.posture.refreshFoldingApiState("display_added", force = true)
            desk.scheduleCoverDisplayLifecycleCheck("display_added_$displayId")
            if (MirrorService.active) OperationLog.i(
                context,
                "DisplayGeometry",
                desk.displayGeometrySnapshot("display_added_$displayId")
            )
            if (MirrorService.active) refreshMenuGeometryAfterDisplayChange()
            desk.externalOutputs.refreshExternalDisplayState()
            scheduleInternal120HzReapply(requireExternalDisplay = true)
            scheduleTopologyReapplyAfterReconnect()
            desk.laptopModeSwitch.scheduleLaptopModeReevaluation("display_added")
            if (desk.keyboardDeck.laptopKeyboardRequested) {
                desk.keyboardDeck.scheduleLaptopKeyboardReadyCheck(desk.keyboardDeck.laptopKeyboardGeneration, 0)
            }
            if (MirrorService.active && displayId == Display.DEFAULT_DISPLAY) {
                scheduleHostDisplayReconfiguration("default display added")
            }
        }

        override fun onDisplayRemoved(displayId: Int) {
            val removedPhysicalExternalDisplay = displayId in desk.externalOutputs.knownPhysicalExternalDisplayIds
            desk.posture.refreshFoldingApiState("display_removed", force = true)
            desk.scheduleCoverDisplayLifecycleCheck("display_removed_$displayId")
            if (MirrorService.active) OperationLog.i(
                context,
                "DisplayGeometry",
                desk.displayGeometrySnapshot("display_removed_$displayId")
            )
            if (MirrorService.active) refreshMenuGeometryAfterDisplayChange()
            desk.externalOutputs.refreshExternalDisplayState()
            if (removedPhysicalExternalDisplay) {
                scheduleExternalDisplayDisconnectTopologyReconcile(displayId)
            }
            scheduleInternal120HzReapply(requireExternalDisplay = false)
            desk.laptopModeSwitch.scheduleLaptopModeReevaluation("display_removed")
            if (displayId == desk.keyboardDeck.laptopKeyboardDisplayId) {
                desk.keyboardDeck.finishAllLaptopKeyPresses()
                desk.keyboardDeck.clearLaptopKeyboardPublication("associated_display_removed")
            }
            if (MirrorService.active && displayId == Display.DEFAULT_DISPLAY) {
                scheduleHostDisplayReconfiguration("default display removed")
            }
        }

        override fun onDisplayChanged(displayId: Int) {
            // Preserve the edge walk before refreshing a changed source display.
            desk.posture.refreshFoldingApiState("display_changed", force = true)
            desk.scheduleCoverDisplayLifecycleCheck("display_changed_$displayId")
            if (MirrorService.active) OperationLog.i(
                context,
                "DisplayGeometry",
                desk.displayGeometrySnapshot("display_changed_$displayId")
            )
            if (MirrorService.active) refreshMenuGeometryAfterDisplayChange()
            desk.externalOutputs.refreshExternalDisplayState()
            scheduleInternal120HzReapply(requireExternalDisplay = true)
            desk.laptopModeSwitch.scheduleLaptopModeReevaluation("display_changed")
            if (desk.keyboardDeck.laptopKeyboardRequested && displayId == desk.targetDisplayId) {
                desk.keyboardDeck.scheduleLaptopKeyboardReadyCheck(desk.keyboardDeck.laptopKeyboardGeneration, 0)
            }
            if (MirrorService.active && displayId == Display.DEFAULT_DISPLAY) {
                desk.rawTouchscreen.scheduleRawTouchscreenTopologyRefresh("default_display_changed")
                desk.laptopModeSwitch.leaveLaptopModeOnCoverDisplay()
                scheduleHostDisplayReconfiguration("default display changed")
            } else if (MirrorService.active && displayId == desk.targetDisplayId && desk.mirror.mirrorDisplayId >= 0) {
                desk.mirror.scheduleMirrorRefresh("source display changed")
            }
        }
    }

    internal fun refreshMenuGeometryAfterDisplayChange() {
        val panel = desk.menu ?: return
        val frame = desk.root ?: return
        frame.post {
            if ((!MirrorService.active && !desk.demoMode) || desk.menu !== panel) return@post
            val landscape = desk.menuUsesLandscapeLayout()
            // The outer menu is resized after rotation, but each ScrollView
            // must also discard the width/weight selected when it was built.
            // Keeping those old child parameters creates a blank strip beside
            // the controls after switching between portrait and landscape.
            if (!landscape && desk.workspaces.workspaceExpanded && panel.childCount > 1) {
                desk.workspaces.workspaceExpanded = false
                while (panel.childCount > 1) panel.removeViewAt(panel.childCount - 1)
                desk.menuPrimary?.let(desk.workspaces::showWorkspaceMenu)
            }
            for (index in 0 until panel.childCount) {
                val scroll = panel.getChildAt(index) as? ScrollView ?: continue
                scroll.layoutParams = LinearLayout.LayoutParams(
                    if (landscape) desk.dp(340) else 0,
                    -1,
                    if (landscape) 0f else 1f
                )
            }
            panel.layoutParams = desk.menuLayoutParams()
            panel.requestLayout()
            desk.scheduleMenuHeightUpdate()
        }
    }

    private fun scheduleInternal120HzReapply(requireExternalDisplay: Boolean) {
        if (!MirrorService.active) return
        if (requireExternalDisplay && !desk.externalOutputs.externalDisplayDetector.snapshot().connected) return
        val generation = ++refreshRateReapplyGeneration
        android.os.Handler(context.mainLooper).postDelayed({
            if (generation != refreshRateReapplyGeneration || !MirrorService.active) return@postDelayed
            if (requireExternalDisplay && !desk.externalOutputs.externalDisplayDetector.snapshot().connected) {
                return@postDelayed
            }
            runCatching { internalRefreshRateController.applyIfEnabled() }
                .onFailure { Log.e(desk.logTag, "120 Hz reapply after display change failed", it) }
        }, 650)
    }

    internal fun scheduleTopologyReapplyAfterReconnect() {
        if (!MirrorService.active) return
        val generation = ++topologyReapplyGeneration
        android.os.Handler(context.mainLooper).postDelayed({
            if (generation != topologyReapplyGeneration || !MirrorService.active) return@postDelayed
            activateTopologyForIndependentDisplays("display_reconnect")
        }, 750)
    }

    /**
     * A topology write includes every currently connected physical panel.  On
     * unplug, the removed panel vanishes from DisplayManager before this
     * callback arrives, so merely refreshing the input routes leaves a stale
     * node in the system topology.  Rebuild from the surviving displays after
     * the adapter has settled; do not stop the phone-side Dextop session.
     */

    private fun scheduleExternalDisplayDisconnectTopologyReconcile(removedDisplayId: Int) {
        if (!MirrorService.active || desk.stopping || removedDisplayId == desk.mirror.mirrorDisplayId) return
        val generation = ++topologyReapplyGeneration
        Handler(context.mainLooper).postDelayed({
            if (generation != topologyReapplyGeneration || !MirrorService.active || desk.stopping) return@postDelayed
            val manager = context.getSystemService(DisplayManager::class.java)
            if (manager.getDisplay(removedDisplayId) != null) return@postDelayed
            if (desk.mirror.mirrorDisplayId < 0 || manager.getDisplay(desk.mirror.mirrorDisplayId) == null) return@postDelayed
            activateTopologyForIndependentDisplays("external_display_removed")
            OperationLog.i(
                context,
                "DisplayTopology",
                "external display removed id=$removedDisplayId; rebuilt topology from surviving displays"
            )
        }, 300L)
    }

    /**
     * Foldable vendors do not all deliver a hinge sensor event for the panel
     * hand-off.  Samsung can instead publish only a display/configuration
     * change, and its folding API may briefly report an empty feature list
     * while the new panel is attached.  Re-evaluate after that churn settles
     * so opening a session flat and then entering flex posture is handled the
     * same as starting the session half-open.
     */

    internal val desktopWallpaperController by lazy(LazyThreadSafetyMode.NONE) {
        DesktopWallpaperController(context.applicationContext)
    }

    internal var hostReconfigurationGeneration = 0

    internal val hostDisplayMonitorHandler by lazy { android.os.Handler(context.mainLooper) }

    private var observedHostWidth = 0

    private var observedHostHeight = 0

    private var observedHostDensity = 0

    private val hostDisplayMonitor = object : Runnable {
        override fun run() {
            if (!MirrorService.active || desk.lockScreen.suspendedForLockScreen) return
            // WindowManager folding callbacks are not delivered consistently
            // during Samsung panel hand-off. Poll the posture while a session
            // is active so a flat/half-open transition is still observed even
            // when neither a display nor a hinge event is emitted.
            if (desk.laptopModeSwitch.isLaptopAutoDetectionEnabled()) {
                desk.posture.refreshFoldingApiState("posture_monitor", force = true)
                desk.posture.updateLaptopModeFromCurrentPosture("posture monitor")
            }
            if (desk.laptopModeActive &&
                !desk.laptopModeSwitch.isDebugLaptopModeForced() &&
                desk.laptopModeSwitch.laptopHostUniqueId != null &&
                desk.deviceDisplays.defaultDisplayUniqueId()?.let { it != desk.laptopModeSwitch.laptopHostUniqueId } == true &&
                desk.laptopModeSwitch.hasStableLaptopHostMismatch() &&
                !desk.deviceDisplays.isFoldableMainDisplay()
            ) {
                // A different internal panel became the default display. This
                // is only a cover-display signal when the new default is
                // actually the smaller panel. Fold8 can publish a temporary
                // unique-id change while the large panel is being re-laid out;
                // dismissing the deck for that transient event made the
                // keyboard appear briefly and then disappear.
                desk.posture.laptopManualOverride = false
                desk.laptopModeSwitch.setLaptopMode(false)
                hostDisplayMonitorHandler.postDelayed(this, MirrorService.HOST_DISPLAY_MONITOR_INTERVAL_MS)
                return
            }
            val bounds = desk.windowManager?.currentWindowMetrics?.bounds
            val host = desk.surfaceView
            val width = host?.width?.takeIf { it > 0 } ?: bounds?.width() ?: 0
            val height = host?.height?.takeIf { it > 0 } ?: bounds?.height() ?: 0
            val hostDensity = context.resources.configuration.densityDpi
            if (width >= 480 && height >= 480) {
                val changed = observedHostWidth > 0 &&
                        (width != observedHostWidth || height != observedHostHeight ||
                                hostDensity != observedHostDensity)
                observedHostWidth = width
                observedHostHeight = height
                observedHostDensity = hostDensity
                if (shouldFollowHostDisplay() && hostSizeDiffersFromTarget(width, height)) {
                    scheduleHostDisplayReconfiguration(
                        "periodic host geometry check", width, height, hostDensity
                    )
                } else if (changed && desk.mirror.mirrorDisplayId >= 0) {
                    desk.mirror.scheduleMirrorRefresh("periodic host geometry check", width, height)
                }
            }
            hostDisplayMonitorHandler.postDelayed(this, MirrorService.HOST_DISPLAY_MONITOR_INTERVAL_MS)
        }
    }

    internal fun activateTopologyForIndependentDisplays(reason: String) {
        runCatching {
            DisplayEnvironmentSettings(context).activateTopologyForOverlays(buildSet {
                if (desk.mirror.mirrorDisplayId >= 0) add(desk.mirror.mirrorDisplayId)
                addAll(AndroidAutoMirrorActivity.autoOverlayDisplayIds())
                addAll(desk.coverDisplayController.ownedDisplayIds())
            })
        }.onFailure {
            OperationLog.w(context, "DisplayTopology", "topology activation skipped reason=$reason", it)
        }
    }

    internal fun shouldFollowHostDisplay(): Boolean {
        // The car display has its own size; folding or rotating the phone must not resize it.
        if (desk.autoOnlySession) return false
        // Nor does the external screen while it alone shows the desktop.
        if (desk.externalOutputs.externalOutput?.mode == "external") return false
        val preferences = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
        val selectedDeviceResolution =
            preferences.getString("flutter.selected_resolution_id", "device") == "device"
        return desk.laptopModeActive || selectedDeviceResolution &&
                (desk.deviceDisplays.isFoldableDevice() || preferences.getBoolean("flutter.foldable_auto", false) ||
                        desk.desktopEnvironment.autoResizeWithHostDisplay)
    }

    internal fun hostSizeDiffersFromTarget(width: Int, height: Int): Boolean {
        val hostLong = maxOf(width, height)
        val hostShort = minOf(width, height)
        val targetLong = maxOf(desk.targetWidth, desk.targetHeight)
        val targetShort = minOf(desk.targetWidth, desk.targetHeight)
        return hostLong != targetLong || hostShort != targetShort
    }

    /**
     * Fold state callbacks vary by vendor: some send Configuration, some only
     * resize the accessibility Surface, and others only change display metrics.
     * All three paths converge here and are debounced until the panel geometry
     * has settled. Custom profiles never enter this path.
     */

    internal fun scheduleHostDisplayReconfiguration(
        reason: String,
        width: Int? = null,
        height: Int? = null,
        densityDpi: Int? = null
    ) {
        if (!shouldFollowHostDisplay()) return
        if (desk.externalOutputs.externalOutput?.mode == "external" || desk.externalOutputs.externalOutput?.mode == "extend") return
        val generation = ++hostReconfigurationGeneration
        desk.root?.postDelayed({
            if (!MirrorService.active || desk.lockScreen.suspendedForLockScreen || generation != hostReconfigurationGeneration ||
                !shouldFollowHostDisplay()
            ) return@postDelayed
            val bounds = desk.windowManager?.currentWindowMetrics?.bounds
            // During laptop mode the window metrics still describe the whole
            // unfolded panel, while the mirror Surface is split to the upper
            // pane. A default-display callback must not feed that full-panel
            // size back into the logical display or it immediately undoes the
            // pane resize and makes the keyboard appear to disappear. The
            // measured Surface is the source of truth while the deck is live.
            val host = desk.surfaceView
            val measuredWidth = if (desk.laptopModeActive && (host?.width ?: 0) > 0) {
                host!!.width
            } else {
                width?.takeIf { it > 0 } ?: bounds?.width() ?: return@postDelayed
            }
            val measuredHeight = if (desk.laptopModeActive && (host?.height ?: 0) > 0) {
                host!!.height
            } else {
                height?.takeIf { it > 0 } ?: bounds?.height() ?: return@postDelayed
            }
            if (measuredWidth < 480 || measuredHeight < 480) return@postDelayed

            val systemDensity = densityDpi ?: context.resources.configuration.densityDpi
            val next = configForHostGeometry(
                MirrorService.Config(desk.targetWidth, desk.targetHeight, desk.density, desk.secureDisplay, desk.showSystemDecorations),
                measuredWidth,
                measuredHeight,
                systemDensity
            )
            if (next.width == desk.targetWidth && next.height == desk.targetHeight && next.density == desk.density) {
                return@postDelayed
            }
            OperationLog.i(
                context,
                "DisplayBackend",
                "host reconfiguration reason=$reason " +
                        "${desk.targetWidth}x${desk.targetHeight}/${desk.density} -> ${next.width}x${next.height}/${next.density}"
            )
            resizeActiveDisplay(next, reason)
        }, 320)
    }

    /** Changes the logical size without destroying the virtual display or its tasks. */
    internal fun resizeActiveDisplay(next: MirrorService.Config, reason: String) {
        if (desk.targetDisplayId < 0) return
        desk.touchSurface.cancelDesktopTouchStream()
        val oldWidth = desk.targetWidth.coerceAtLeast(1)
        val oldHeight = desk.targetHeight.coerceAtLeast(1)
        val normalizedCursorX = desk.desktopCursor.cursorX / oldWidth
        val normalizedCursorY = desk.desktopCursor.cursorY / oldHeight
        val sizeChanged = next.width != desk.targetWidth || next.height != desk.targetHeight
        val densityChanged = next.density != desk.density
        if (!sizeChanged && !densityChanged) return
        OperationLog.i(
            context,
            "DisplayBackend",
            "live metric change reason=$reason sizeChanged=$sizeChanged densityChanged=$densityChanged " +
                    "from=${desk.targetWidth}x${desk.targetHeight}/${desk.density} to=${next.width}x${next.height}/${next.density}"
        )
        val service = desk.systemService("window", "android.view.IWindowManager")
        val type = Class.forName("android.view.IWindowManager")
        try {
            val userId = android.os.UserHandle::class.java
                .getMethod("myUserId").invoke(null) as Int
            // A DPI-only edit must not look like a full display replacement to
            // Samsung DeX.  Re-sending the same forced size followed by a
            // rotation/windowing reconfiguration makes One UI's taskbar tear
            // down its desktop window.  Update only the metric that changed.
            if (sizeChanged && !(desk.autoOnlySession && desk.displayFactory.autoOwnedDisplay != null) && desk.displayFactory.phoneOwnedDisplay == null) {
                type.getMethod(
                    "setForcedDisplaySize",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                ).invoke(service, desk.targetDisplayId, next.width, next.height)
            }
            if (densityChanged || desk.desktopEnvironment.id == MirrorService.XIAOMI_PROFILE) {
                type.getMethod(
                    "setForcedDisplayDensityForUser",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                ).invoke(service, desk.targetDisplayId, next.density, userId)
            }
        } catch (error: Throwable) {
            OperationLog.e(context, "DisplayBackend", "live resize failed reason=$reason", error)
            Log.e(desk.logTag, "live display resize failed reason=$reason", error)
            throw error
        }
        desk.targetWidth = next.width
        desk.targetHeight = next.height
        desk.density = next.density
        if (densityChanged && desk.plasmaShell is moe.n4tsu.dextop.plasma.CompositedWindowShell) {
            // Plasma and its application displays keep the density they were started with
            // (the external screen alone would show the desktop at the phone's scale): start it
            // again at the new one; its windows are handed back to the display and adopted.
            desk.root?.post {
                if (!MirrorService.active || desk.plasmaShell == null) return@post
                desk.launcher.stopPlasmaShell(restoreWindows = true)
                desk.launcher.applyWindowManagerSelection("desktop density changed")
                if (desk.externalOutputs.externalOutput?.mode == "extend") desk.externalOutputs.applyDesktopOutputs()
            }
        }
        desk.externalOutputs.attachExternalMirror()
        if (sizeChanged) {
            synchronizeDesktopWallpaperDimensions(desk.targetWidth, desk.targetHeight, reason)
            desk.plasmaShell?.onDisplayResized(desk.targetWidth, desk.targetHeight)
        }
        desk.displayFactory.phoneOwnedDisplay?.takeIf { sizeChanged || densityChanged }?.let { owned ->
            val surface = if (desk.externalOutputs.phoneRegion != null) desk.displayFactory.directDisplaySurface()
                else desk.surfaceView?.holder?.surface?.takeIf { it.isValid }
            // A host Surface still being created (the external screen's window just added, or
            // the phone's coming back) binds at the new size when it arrives (attachMirror);
            // the forced size below must still change now.
            val updated = surface == null || owned.resize(surface, desk.targetWidth, desk.targetHeight, desk.density)
            check(updated) { moe.n4tsu.dextop.localText("uiUnableToResizeTheDirectDextopDisplay") }
            OperationLog.i(
                context,
                "DisplayBackend",
                "direct Plasma display resized applied=$updated desktop=${desk.targetWidth}x${desk.targetHeight}/${desk.density}"
            )
            if (desk.desktopEnvironment.id == MirrorService.XIAOMI_PROFILE) {
                // HyperOS falls back to the phone's portrait size once the
                // override is cleared, which shrinks the pointer viewport to a
                // strip of the desktop. Pin the new size as at creation.
                type.getMethod("setForcedDisplaySize", Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                    .invoke(service, desk.targetDisplayId, desk.targetWidth, desk.targetHeight)
            } else {
                type.getMethod("clearForcedDisplaySize", Int::class.javaPrimitiveType)
                    .invoke(service, desk.targetDisplayId)
            }
        }
        if (desk.autoOnlySession && sizeChanged) {
            val destination = desk.displayFactory.autoDestinationSurface
            val (outputWidth, outputHeight) = desk.displayFactory.autoDestinationSize()
            val updated = destination?.takeIf { it.isValid }?.let {
                desk.displayFactory.autoOwnedDisplay?.resize(it, outputWidth, outputHeight, desk.density)
            } == true
            OperationLog.i(
                context,
                "CarCompanion",
                "Auto-owned display buffer resize applied=$updated desktop=${desk.targetWidth}x${desk.targetHeight}/${desk.density}"
            )
            if (!updated) {
                throw IllegalStateException(moe.n4tsu.dextop.localText("uiUnableToResizeTheAndroidAutoDesktopBuffer"))
            }
            desk.displayFactory.applyAutoLogicalSize()
        }
        if (sizeChanged) {
            // A live size change can cross the natural-orientation boundary.
            // WMS also drops projected-display freeform policy on some Pixel
            // builds, so synchronize rotation and desktop policy after a size
            // change.  DPI-only edits deliberately skip this path so Samsung
            // DeX's taskbar is not forced through an unnecessary rebuild.
            // Headless Car Companion sessions have no phone-side host. Their
            // logical desktop may resize for the connected car display, but
            // that must never rotate or otherwise alter the phone UI.
            if (!desk.autoOnlySession && desk.externalOutputs.externalPhoneRole != "phone") {
                desk.applyHostDisplayOrientation(desk.requestedPortrait)
                desk.phoneGuard.forcePhoneRotation(desk.requestedPortrait)
            }
            desk.displayFactory.configureDisplay()
        }
        desk.desktopCursor.cursorX = (normalizedCursorX * desk.targetWidth).coerceIn(0f, desk.targetWidth - 1f)
        desk.desktopCursor.cursorY = (normalizedCursorY * desk.targetHeight).coerceIn(0f, desk.targetHeight - 1f)
        desk.desktopCursor.updateCursorPosition()
        desk.surfaceView?.let { surface ->
            if (surface.width > 0 && surface.height > 0) {
                // Rotation-driven Surface replacement reattaches the mirror in
                // surfaceCreated(). Do not create extra recording displays here.
                // A logical size change can leave the host Surface geometry
                // unchanged (the old letterboxed profile and the new full
                // profile use the same panel). Force the VirtualDisplay
                // attachment update in that case so its source crop/scale is
                // rebuilt instead of retaining the old black bars and offset.
                desk.mirror.scheduleMirrorRefresh(
                    "$reason; live logical resize",
                    surface.width,
                    surface.height,
                    forceVirtualDisplay = sizeChanged,
                    rebuild = sizeChanged && desk.desktopEnvironment.rebuildMirrorAfterLiveResize,
                )
            }
        }
        scheduleTopologyReapplyAfterReconnect()
        OperationLog.i(
            context,
            "DisplayBackend",
            "live resized display=${desk.targetDisplayId} to ${next.width}x${next.height}/${next.density}; " +
                    "tasks retained " + desk.displayGeometrySnapshot("live_resize_applied")
        )
        if (desk.desktopEnvironment.id == MirrorService.XIAOMI_PROFILE && sizeChanged &&
            desk.virtualPointer.virtualPointerRegisteredProfile == "touchpad" && desk.virtualPointer.virtualMouseProcessAlive()) {
            desk.virtualPointer.virtualMouseReady = false
            desk.privilegedInputClient.setOutputReady(false)
            desk.virtualPointer.startVirtualMouse()
        }
    }

    internal fun synchronizeDesktopWallpaperDimensions(
        width: Int,
        height: Int,
        reason: String,
    ) {
        val displayId = desk.targetDisplayId
        desktopWallpaperController.synchronize(displayId, width, height, reason) {
            MirrorService.active && desk.targetDisplayId == displayId
        }
    }

    internal fun configForHostGeometry(
        base: MirrorService.Config,
        hostWidth: Int,
        hostHeight: Int,
        systemDensity: Int
    ): MirrorService.Config {
        val automaticDensity = (160 + systemDensity / 160f * 24)
            .toInt().coerceIn(160, 320)
        if (desk.laptopModeActive) {
            // The keyboard deck only reduces the height of the host Surface.
            // Keep the logical scale selected when the desktop was started
            // (including workspace magnification and custom DPI) instead of
            // replacing it with the physical panel metrics. Replacing both
            // size and density here makes One UI detach the desktop taskbar
            // and its task stack, leaving only WallpaperService visible.
            val logicalScale = base.width.toFloat() / hostWidth.coerceAtLeast(1)
            val paneWidth = base.width.coerceIn(480, 7680) and -2
            val paneHeight = (hostHeight * logicalScale)
                .roundToInt()
                .coerceIn(480, 7680) and -2
            return base.copy(
                width = paneWidth,
                height = paneHeight,
                density = base.density.coerceIn(72, 960)
            )
        }
        // A magnified device profile already has a matching physical overlay
        // mode. Do not replace only its logical metrics during a Fold panel
        // hand-off; the overlay adapter cannot resize its physical mode in
        // place, and the two geometries would diverge again.
        // Do not infer magnification only from a DPI difference: when
        // "apply scale to windows" is enabled the requested behavior is to
        // retain the original DPI, so density can legitimately equal the
        // automatic value even though width and height are magnified.
        if (isWorkspaceMagnificationActive() || base.density != automaticDensity) {
            return base
        }
        // An unscaled device profile can continue to follow the complete host
        // panel geometry as before.
        val portrait = base.height > base.width
        val hostLong = maxOf(hostWidth, hostHeight)
        val hostShort = minOf(hostWidth, hostHeight)
        return base.copy(
            // The panel size is dynamic; the selected device orientation is
            // preserved separately by requestedPortrait.
            width = if (portrait) hostShort else hostLong,
            height = if (portrait) hostLong else hostShort,
            density = automaticDensity
        )
    }

    internal fun startHostDisplayMonitor() {
        stopHostDisplayMonitor()
        observedHostWidth = 0
        observedHostHeight = 0
        observedHostDensity = 0
        hostDisplayMonitorHandler.post(hostDisplayMonitor)
    }

    internal fun stopHostDisplayMonitor() {
        hostDisplayMonitorHandler.removeCallbacks(hostDisplayMonitor)
    }

    /**
     * Pointer profiles use EventHub as their primary physical input stream.
     * Direct touch deliberately remains on Android MotionEvent so framework
     * transforms, accessibility semantics, and application touch behavior are
     * unchanged. The raw reader is bound from the MotionEvent's exact
     * EventHub capabilities; no MotionEvent device identity, model, or vendor
     * device name is assumed.
     */
}
