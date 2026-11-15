package moe.n4tsu.dextop

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.SystemClock
import android.view.Display
import android.view.Surface
import android.view.SurfaceView
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.LinearLayout
import androidx.window.layout.FoldingFeature
import kotlin.math.abs

/** What [LaptopMode] needs from the session beyond the shared interfaces. */
internal interface LaptopModeHost : DesktopSession, OverlayMenu {
    val cast: CastController
    fun configForHostGeometry(base: MirrorService.Config, hostWidth: Int, hostHeight: Int, systemDensity: Int): MirrorService.Config
    val coverButtons: CoverBackButtons
    val externalOutputs: ExternalDisplays
    val gamepad: VirtualGamepad
    fun isCurrentFoldableCoverDisplay(): Boolean
    val keyboardDeck: KeyboardDeck
    var keyboardDeckStyle: MirrorService.KeyboardDeckStyle
    val laptopTheme: LaptopThemeSettings
    fun laptopTrackpadInputActive(): Boolean
    val lockScreen: LockScreenGuard
    val menuScrim: View?
    val menuWidgets: MenuWidgets
    val mirror: MirrorLink
    val overlayTextInputActive: Boolean
    val performanceHud: PerformanceHud?
    val physicalInput: PhysicalInput
    val posture: LaptopPosture
    val rawTouchscreen: RawTouchscreenBridge
    fun resizeActiveDisplay(next: MirrorService.Config, reason: String)
    fun setOverlayFocusable(focusable: Boolean)
    val swipeTyping: SwipeTyping
    val touchSurface: TouchSurface
    val virtualPointer: VirtualPointer
    /** Written here, not only read. */
    override var directTouch: Boolean
    /** Written here, not only read. */
    override var laptopModeActive: Boolean
}

/** Switches the session in and out of laptop mode and between keyboard deck styles. */
internal class LaptopMode(
    private val context: Context,
    private val desk: LaptopModeHost,
) {
    internal var laptopBaseConfig: MirrorService.Config? = null

    /** Full-height host Surface measured in physical view pixels before the deck is added. */
    internal var laptopFullHostHeightPx = 0

    internal var laptopHostUniqueId: String? = null

    internal fun scheduleLaptopModeReevaluation(reason: String) {
        if (!MirrorService.active || desk.lockScreen.suspendedForLockScreen || desk.autoOnlySession) return
        val generation = ++desk.posture.laptopPostureReevaluationGeneration
        android.os.Handler(context.mainLooper).postDelayed({
            if (generation != desk.posture.laptopPostureReevaluationGeneration ||
                !MirrorService.active || desk.lockScreen.suspendedForLockScreen
            ) return@postDelayed
            if (!isLaptopAutoDetectionEnabled()) return@postDelayed
            desk.posture.refreshFoldingApiState("$reason settled", force = true)
            val angle = desk.posture.filteredHingeAngle ?: desk.posture.hingeAngle
            if (angle != null) {
                desk.posture.updateLaptopModeForHinge(angle)
            } else {
                desk.posture.updateLaptopModeFromCurrentPosture(reason)
            }
        }, 240L)
    }

    internal fun setLaptopMode(
        enabled: Boolean,
        requestedStyle: MirrorService.KeyboardDeckStyle = MirrorService.KeyboardDeckStyle.LAPTOP,
    ) {
        // Laptop decks belong to the phone screen, which an Auto-only session does not use.
        if (enabled && desk.autoOnlySession) return
        if (enabled && !desk.demoMode &&
            (requestedStyle == MirrorService.KeyboardDeckStyle.GAMEPAD || requestedStyle == MirrorService.KeyboardDeckStyle.GAMEBOY) &&
            !desk.gamepad.isExperimentalGamepadEnabled()
        ) return
        if (enabled && desk.laptopModeActive && desk.keyboardDeckStyle != requestedStyle) {
            switchActiveKeyboardStyle(requestedStyle)
            return
        }
        if (enabled == desk.laptopModeActive) {
            if (enabled) {
                if (requestedStyle == MirrorService.KeyboardDeckStyle.GAMEPAD ||
                    requestedStyle == MirrorService.KeyboardDeckStyle.GAMEBOY
                ) {
                    desk.keyboardDeck.stopLaptopHardwareKeyboard()
                    desk.gamepad.setVirtualGamepadVisible(true)
                } else {
                    desk.gamepad.setVirtualGamepadVisible(false)
                    desk.keyboardDeck.startLaptopHardwareKeyboard()
                }
                desk.virtualPointer.startVirtualMouse()
                desk.rawTouchscreen.startRawTouchscreenReaderIfEligible()
                desk.coverButtons.syncCoverInputForKeyboardStyle()
            }
            return
        }
        if (enabled && !desk.demoMode && !desk.externalOutputs.externalInputDeck && requestedStyle == MirrorService.KeyboardDeckStyle.LAPTOP &&
            !isLaptopCapableDevice()
        ) {
            OperationLog.i(
                context,
                "LaptopMode",
                "ignored request on a non-foldable phone-sized display"
            )
            return
        }
        if (enabled) desk.keyboardDeckStyle = requestedStyle
        val frame = desk.root ?: return
        val content = desk.keyboardDeck.laptopContent ?: return
        val surface = desk.surfaceView ?: return
        // Keep the full-screen logical profile so leaving laptop mode restores
        // the user's original resolution instead of the half-height pane.
        val restoreConfig = if (!enabled) laptopBaseConfig else null
        if (enabled && laptopBaseConfig == null && !desk.externalOutputs.externalInputDeck) {
            laptopBaseConfig = MirrorService.Config(
                desk.targetWidth,
                desk.targetHeight,
                desk.density,
                desk.secureDisplay,
                desk.showSystemDecorations
            )
        }
        if (enabled && laptopFullHostHeightPx <= 0) {
            // targetHeight/laptopBaseConfig are logical desktop pixels and can
            // be much smaller when workspace magnification is enabled. Keep
            // this baseline in the same physical View coordinate space as
            // surface.height so a magnified upper pane is never mistaken for
            // the restored full-height host.
            laptopFullHostHeightPx = maxOf(surface.height, content.height)
        }
        desk.laptopModeActive = enabled
        if (!enabled) desk.posture.laptopAutoActivated = false
        laptopHostUniqueId = if (enabled) desk.deviceDisplays.defaultDisplayUniqueId() else null
        if (!enabled) {
            desk.posture.laptopHostMismatchSince = 0L
            desk.posture.laptopModeEvaluationGeneration += 1
        }
        if (enabled) {
            // The lower laptop pane contains real Android Views (keys,
            // candidates, FN/MENU). Bypassing the hierarchy and dispatching
            // every event straight to SurfaceView makes all of them inert.
            desk.root?.routeTouchesToSurface = false
            if (requestedStyle == MirrorService.KeyboardDeckStyle.GAMEPAD ||
                requestedStyle == MirrorService.KeyboardDeckStyle.GAMEBOY
            ) {
                desk.keyboardDeck.stopLaptopHardwareKeyboard()
                desk.gamepad.setVirtualGamepadVisible(true)
            } else {
                desk.gamepad.setVirtualGamepadVisible(false)
                desk.keyboardDeck.startLaptopHardwareKeyboard()
            }
            desk.virtualPointer.startVirtualMouse()
            desk.rawTouchscreen.startRawTouchscreenReaderIfEligible()
            val deck = desk.keyboardDeck.buildLaptopDeck().apply {
                alpha = 0f
                translationY = desk.dp(28).toFloat()
            }
            content.addView(deck, LinearLayout.LayoutParams(-1, 0, desk.keyboardDeck.keyboardDeckWeight()))
            deck.post {
                deck.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setInterpolator(PathInterpolator(.22f, 1f, .36f, 1f))
                    .setDuration(360L)
                    .start()
            }
            desk.desktopCursor.cursorView?.contentHeightFraction = desk.keyboardDeck.desktopPaneFraction()
            desk.menuScrim?.bringToFront()
            desk.menu?.bringToFront()
            desk.performanceHud?.bringToFront()
        } else {
            desk.swipeTyping.restoreDextopSwipeIme()
            // Style is disabled from inside the visible operation overlay.
            // Routing directly to the desktop here bypasses that still-open
            // overlay (including its close button and edge recognizer), which
            // makes the whole screen appear frozen. Keep hierarchy routing
            // until toggleMenu() actually closes the overlay.
            desk.root?.routeTouchesToSurface = !desk.desktopCursor.isMenuOverlayVisible()
            desk.keyboardDeck.finishAllLaptopKeyPresses()
            // The raw reader also owns the normal full-screen pointer surface.
            // Keep it alive when leaving the deck unless the restored surface
            // is direct-touch, in which case the physical touchscreen must
            // continue to behave as a touchscreen instead of a touchpad.
            if (desk.directTouch) desk.rawTouchscreen.stopRawTouchscreenReader("laptop_mode_disabled_direct_touch")
            desk.keyboardDeck.stopLaptopHardwareKeyboard()
            desk.gamepad.setVirtualGamepadVisible(false)
            // In tap/direct-touch mode the pointer belongs only to the
            // laptop trackpad while the deck is visible. Remove it as soon
            // as the deck is closed so Android cannot retain its pointer
            // focus for the next app on the phone surface.
            if (desk.directTouch) {
                desk.touchSurface.cancelDesktopTouchStream()
                desk.virtualPointer.stopVirtualMouse()
            }
            val deck = desk.keyboardDeck.laptopDeck
            desk.keyboardDeck.laptopDeck = null
            desk.laptopTheme.hideLaptopLanguagePopup()
            desk.keyboardDeck.laptopTrackpadView = null
            desk.keyboardDeck.laptopFnButton = null
            desk.keyboardDeck.laptopMenuButton = null
            deck?.animate()
                ?.cancel()
            deck?.animate()
                ?.alpha(0f)
                ?.translationY(desk.dp(28).toFloat())
                ?.setInterpolator(PathInterpolator(.55f, 0f, .78f, 0f))
                ?.setDuration(300L)
                ?.withEndAction {
                    if (!desk.laptopModeActive) runCatching { content.removeView(deck) }
                }
                ?.start()
            desk.desktopCursor.cursorView?.contentHeightFraction = 1f
            // Do not leave the native reader using its laptop-only trackpad
            // bounds while waiting for the asynchronous full-screen layout.
            // The visible overlay intentionally publishes PROFILE_DISABLED;
            // closing it will publish the normal full-screen pointer profile.
            desk.privilegedInput.refreshPrivilegedInputConfig("laptop_mode_disabled_immediate")
        }
        surface.requestLayout()
        frame.requestLayout()
        // The external screen keeps its own size; only a foldable's desktop shrinks to the upper half.
        if (!desk.externalOutputs.externalInputDeck) applyLaptopGeometryWhenLaidOut(enabled, baseOverride = restoreConfig)
        if (!enabled) {
            laptopBaseConfig = null
            laptopFullHostHeightPx = 0
        }
        desk.coverButtons.syncCoverInputForKeyboardStyle()
    }

    private fun switchActiveKeyboardStyle(style: MirrorService.KeyboardDeckStyle) {
        if (!desk.laptopModeActive || desk.keyboardDeckStyle == style) return
        val content = desk.keyboardDeck.laptopContent ?: return
        desk.keyboardDeck.finishAllLaptopKeyPresses()
        if (style == MirrorService.KeyboardDeckStyle.GAMEPAD || style == MirrorService.KeyboardDeckStyle.GAMEBOY) {
            desk.keyboardDeck.stopLaptopHardwareKeyboard()
        } else {
            desk.gamepad.setVirtualGamepadVisible(false)
            desk.keyboardDeck.startLaptopHardwareKeyboard()
        }
        desk.laptopTheme.hideLaptopLanguagePopup()
        desk.swipeTyping.hideLaptopSwipeCandidates()
        desk.keyboardDeck.laptopDeck?.animate()?.cancel()
        desk.keyboardDeck.laptopDeck?.let(content::removeView)
        desk.keyboardDeck.laptopDeck = null
        desk.keyboardDeck.laptopTrackpadView = null
        desk.keyboardDeck.laptopFnButton = null
        desk.keyboardDeck.laptopMenuButton = null
        desk.keyboardDeckStyle = style
        val deck = desk.keyboardDeck.buildLaptopDeck().apply {
            alpha = 0f
            translationY = desk.dp(20).toFloat()
        }
        content.addView(deck, LinearLayout.LayoutParams(-1, 0, desk.keyboardDeck.keyboardDeckWeight()))
        desk.desktopCursor.cursorView?.contentHeightFraction = desk.keyboardDeck.desktopPaneFraction()
        deck.animate()
            .alpha(1f)
            .translationY(0f)
            .setInterpolator(PathInterpolator(.22f, 1f, .36f, 1f))
            .setDuration(280L)
            .start()
        content.requestLayout()
        desk.surfaceView?.requestLayout()
        desk.coverButtons.syncCoverInputForKeyboardStyle()
        applyLaptopGeometryWhenLaidOut(true)
        OperationLog.i(context, "KeyboardStyle", "switched to ${style.name.lowercase()}")
    }

    internal fun leaveLaptopModeOnCoverDisplay(): Boolean {
        if (!desk.laptopModeActive || isDebugLaptopModeForced()) return false
        val originalHost = laptopHostUniqueId ?: return false
        val currentHost = desk.deviceDisplays.defaultDisplayUniqueId() ?: return false
        if (originalHost == currentHost || !hasStableLaptopHostMismatch()) return false
        desk.posture.laptopManualOverride = false
        desk.posture.laptopAutoSuppressedByUser = false
        desk.posture.laptopAutoActivated = false
        OperationLog.i(
            context,
            "LaptopMode",
            "cover display detected; removing keyboard and restoring full display geometry"
        )
        setLaptopMode(false)
        return true
    }

    /**
     * Fold/unfold transitions can publish a temporary default display while
     * the new panel is being attached. Do not tear down the laptop deck until
     * the host identity has remained different for a full handoff window.
     */

    internal fun hasStableLaptopHostMismatch(): Boolean {
        val original = laptopHostUniqueId ?: return false
        val current = desk.deviceDisplays.defaultDisplayUniqueId() ?: return false
        if (current == original) {
            desk.posture.laptopHostMismatchSince = 0L
            return false
        }
        val now = SystemClock.uptimeMillis()
        if (desk.posture.laptopHostMismatchSince == 0L) {
            desk.posture.laptopHostMismatchSince = now
            return false
        }
        return now - desk.posture.laptopHostMismatchSince >= desk.posture.laptopHostMismatchDebounceMs
    }

    internal fun laptopHostIsFullHeight(): Boolean {
        val fullHeight = laptopFullHostHeightPx
        val hostHeight = desk.surfaceView?.height ?: return false
        if (fullHeight <= 0 || hostHeight <= 0) return false
        return hostHeight >= (fullHeight * 0.78f).toInt()
    }

    internal fun applyFlutterLaptopModeSetting(enabled: Boolean) {
        // The deck belongs to the external screen now; the hinge and the setting do not move it.
        if (desk.externalOutputs.externalInputDeck) return
        if (!enabled) {
            if (desk.posture.laptopAutoActivated && !desk.posture.laptopManualOverride) setLaptopMode(false)
            return
        }
        desk.posture.hingeAngle?.let(desk.posture::updateLaptopModeForHinge)
    }

    internal fun applyLaptopGeometryWhenLaidOut(
        enabled: Boolean,
        attempt: Int = 0,
        baseOverride: MirrorService.Config? = null,
        preserveMenu: Boolean = false,
    ) {
        val content = desk.keyboardDeck.laptopContent ?: return
        val surface = desk.surfaceView ?: return
        content.postDelayed({
            if (desk.laptopModeActive != enabled) return@postDelayed
            if (!MirrorService.active) {
                if (attempt < 20) applyLaptopGeometryWhenLaidOut(
                    enabled, attempt + 1, baseOverride, preserveMenu
                )
                return@postDelayed
            }
            val expectedHeight = if (enabled) {
                (content.height * desk.keyboardDeck.desktopPaneFraction()).toInt()
            } else content.height
            if ((surface.width <= 0 || kotlin.math.abs(surface.height - expectedHeight) > 2) &&
                attempt < 20
            ) {
                applyLaptopGeometryWhenLaidOut(enabled, attempt + 1, baseOverride, preserveMenu)
                return@postDelayed
            }
            val reason = if (enabled) "laptop mode enabled" else "laptop mode disabled"
            // While the laptop deck is visible the desktop must always match
            // the measured upper pane. A custom/recovered profile would
            // otherwise be letterboxed inside that pane.
            val next = desk.configForHostGeometry(
                baseOverride ?: MirrorService.Config(desk.targetWidth, desk.targetHeight, desk.density, desk.secureDisplay, desk.showSystemDecorations),
                surface.width,
                surface.height,
                context.resources.configuration.densityDpi
            )
            desk.resizeActiveDisplay(next, "$reason after measured layout")
            if (!enabled) {
                // Restoring only the virtual-display metrics is insufficient
                // on One UI/Pixel Launcher: WallpaperService can retain the
                // former upper-pane dimensions until the next reboot.
                desk.sessionJournal.restoreWallpaperDimensions()
            }
            desk.mirror.scheduleMirrorRefresh(
                "$reason; resize VirtualDisplay output to pane",
                surface.width,
                surface.height,
                forceVirtualDisplay = true
            )
            if (enabled) desk.keyboardDeck.refreshLaptopVirtualDisplayAfterLayout()
            desk.privilegedInput.refreshPrivilegedInputConfig("laptop_layout_measured")
            OperationLog.i(
                context,
                "LaptopMode",
                "$reason host=${surface.width}x${surface.height} forcedToPane=true"
            )
            if (!preserveMenu) desk.menuPrimary?.let(desk::showMainMenu)
        }, if (attempt == 0) 0 else 32)
    }

    internal fun isDebugLaptopModeForced(): Boolean =
        MirrorService.DEBUG_FORCE_LAPTOP_MODE &&
                context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0

    /**
     * Keep the special-size Fold8 orientation handling isolated from the
     * normal-size Fold family. The regional model suffix is not sufficient on
     * its own (carrier models use SCG/SC identifiers in Japan), so all known
     * retail identifiers are matched after removing punctuation. This also
     * allows Samsung's longer SKU strings (for example SM-F971BZ...) to match
     * their regional base model.
     *
     * Fold8 (special-size / h8q):
     *   SM-F971B (global), SM-F971U/U1 (US), SM-F971W (Canada),
     *   SM-F9710 (China), SM-F971N (Korea), SM-F971Q (Japan SIM-free),
     *   SM-F971Z (SoftBank), SM-F971C (Rakuten), SCG41 (au), SC-57G (Docomo)
     * Fold8 Ultra (normal-size / q8q):
     *   SM-F976B (global), SM-F976U/U1 (US), SM-F976W (Canada),
     *   SM-F9760 (China), SM-F976N (Korea), SM-F976Q (Japan SIM-free),
     *   SM-F976Z (SoftBank), SM-F976C (Rakuten), SCG39 (au), SC-56G (Docomo)
     */

    internal fun isLaptopCapableDevice(): Boolean {
        if (context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
                .getBoolean("flutter.experimental_force_laptop_mode", false)) return true
        if (desk.deviceDisplays.isFoldableDevice()) return true
        if (context.resources.configuration.smallestScreenWidthDp >= 600) return true
        val display = context.getSystemService(DisplayManager::class.java)
            .getDisplay(Display.DEFAULT_DISPLAY) ?: return false
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        display.getRealMetrics(metrics)
        val densityDpi = metrics.densityDpi.takeIf { it > 0 } ?: 160
        val minDp = minOf(metrics.widthPixels, metrics.heightPixels) * 160f / densityDpi
        return minDp >= 600f
    }

    internal fun isBlackBerryModeEnabled(): Boolean =
        context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
            .getBoolean("flutter.experimental_blackberry_mode", false)

    internal fun isBlackBerryModeAvailable(): Boolean {
        return isBlackBerryModeEnabled()
    }

    internal fun isLaptopStyleAvailable(): Boolean = desk.demoMode ||
            (isLaptopCapableDevice() && !desk.isCurrentFoldableCoverDisplay())

    internal fun isBlackBerryStyleAvailable(): Boolean = desk.demoMode ||
            isBlackBerryModeAvailable() ||
            (desk.laptopModeActive && desk.keyboardDeckStyle == MirrorService.KeyboardDeckStyle.BLACKBERRY)

    internal fun hasKeyboardStyleChoice(): Boolean {
        val availableStyleCount = listOf(
            isLaptopStyleAvailable(),
            isBlackBerryStyleAvailable(),
            desk.gamepad.isGamepadStyleAvailable(),
        ).count { it }
        return availableStyleCount >= 2
    }

    internal fun showKeyboardStyleMenu(panel: LinearLayout) {
        // A style chooser is valid only when both destinations are genuinely
        // available. In particular, returning from the BlackBerry height
        // editor on a phone must not expose Laptop mode through a route that
        // was absent from the root overlay.
        if (!hasKeyboardStyleChoice()) {
            desk.showMainMenu(panel)
            return
        }
        desk.cast.stopCastRouteDiscovery()
        desk.menuWidgets.animateMenuResize(panel)
        panel.removeAllViews()
        panel.addView(desk.menuWidgets.menuTitle(
            NativeStrings.text("nativeKeyboardStyle"),
            NativeStrings.text("nativeReturn"),
        ) { desk.showMainMenu(panel) })
        if (isLaptopStyleAvailable()) {
            panel.addView(desk.menuWidgets.actionButton(
                R.drawable.ic_keyboard,
                NativeStrings.text("nativeLaptopMode") +
                        if (desk.laptopModeActive && desk.keyboardDeckStyle == MirrorService.KeyboardDeckStyle.LAPTOP) "  ✓" else "",
            ) {
                if (desk.demoMode) {
                    desk.demoExplanation(NativeStrings.text("nativeLaptopModeDescription"))
                } else {
                    toggleKeyboardStyle(MirrorService.KeyboardDeckStyle.LAPTOP)
                    desk.showMainMenu(panel)
                }
            })
        }
        if (isBlackBerryStyleAvailable()) {
            val blackBerryAction = desk.menuWidgets.actionButton(
                R.drawable.ic_keyboard,
                NativeStrings.text("nativeBlackBerryMode") +
                        if (desk.laptopModeActive && desk.keyboardDeckStyle == MirrorService.KeyboardDeckStyle.BLACKBERRY) "  ✓" else "",
            ) {
                if (desk.demoMode) {
                    desk.demoExplanation(NativeStrings.text("nativeBlackBerryModeDescription"))
                } else {
                    toggleKeyboardStyle(MirrorService.KeyboardDeckStyle.BLACKBERRY)
                    desk.showMainMenu(panel)
                }
            }
            panel.addView(desk.keyboardDeck.blackBerryActionRow(blackBerryAction) {
                desk.keyboardDeck.showBlackBerryLayoutEditor(panel)
            })
        }
        if (desk.gamepad.isGamepadStyleAvailable()) {
            val gamepadAction = desk.menuWidgets.actionButton(
                R.drawable.ic_gamepad,
                NativeStrings.text("nativeVirtualGamepad") +
                        if (desk.laptopModeActive && (
                            desk.keyboardDeckStyle == MirrorService.KeyboardDeckStyle.GAMEPAD ||
                                    desk.keyboardDeckStyle == MirrorService.KeyboardDeckStyle.GAMEBOY
                            )) "  ✓" else "",
            ) {
                if (desk.demoMode) {
                    desk.demoExplanation(NativeStrings.text("nativeVirtualGamepadDescription"))
                } else {
                    toggleKeyboardStyle(MirrorService.KeyboardDeckStyle.GAMEPAD)
                    desk.showMainMenu(panel)
                }
            }
            panel.addView(desk.gamepad.gamepadActionRow(gamepadAction) {
                desk.gamepad.showGamepadLayoutEditor(panel)
            })
        }
        desk.scheduleMenuHeightUpdate()
    }

    internal fun toggleKeyboardStyle(style: MirrorService.KeyboardDeckStyle) {
        if (desk.demoMode) {
            desk.demoExplanation(NativeStrings.text(
                if (style == MirrorService.KeyboardDeckStyle.LAPTOP) {
                    "nativeLaptopModeDescription"
                } else if (style == MirrorService.KeyboardDeckStyle.GAMEPAD ||
                    style == MirrorService.KeyboardDeckStyle.GAMEBOY
                ) {
                    "nativeVirtualGamepadDescription"
                } else {
                    "nativeBlackBerryModeDescription"
                }
            ))
            return
        }
        if (style == MirrorService.KeyboardDeckStyle.BLACKBERRY && !isBlackBerryModeAvailable() &&
            !(desk.laptopModeActive && desk.keyboardDeckStyle == MirrorService.KeyboardDeckStyle.BLACKBERRY)
        ) return
        if ((style == MirrorService.KeyboardDeckStyle.GAMEPAD || style == MirrorService.KeyboardDeckStyle.GAMEBOY) &&
            !desk.gamepad.isGamepadStyleAvailable() &&
            !(desk.laptopModeActive && (
                desk.keyboardDeckStyle == MirrorService.KeyboardDeckStyle.GAMEPAD ||
                        desk.keyboardDeckStyle == MirrorService.KeyboardDeckStyle.GAMEBOY
                ))
        ) return
        val disable = desk.laptopModeActive && desk.keyboardDeckStyle == style
        if (disable) {
            desk.posture.laptopAutoSuppressedByUser = style == MirrorService.KeyboardDeckStyle.LAPTOP
            desk.posture.laptopManualOverride = false
            desk.posture.pendingLaptopMode = null
            desk.posture.pendingLaptopModeSince = 0L
            desk.posture.laptopModeEvaluationGeneration += 1
            desk.posture.laptopAutoActivated = false
            setLaptopMode(false)
            OperationLog.i(context, "KeyboardStyle", "overlay disabled ${style.name.lowercase()}")
            return
        }
        desk.posture.laptopAutoSuppressedByUser = false
        // Laptop and BlackBerry are both manually persistent keyboard modes.
        // In particular, a subsequent fold-posture callback must not dismiss
        // or replace a BlackBerry deck that the user explicitly selected.
        desk.posture.laptopManualOverride = style == MirrorService.KeyboardDeckStyle.LAPTOP ||
                style == MirrorService.KeyboardDeckStyle.BLACKBERRY
        desk.posture.laptopAutoActivated = false
        // Invalidate a posture decision that may already be waiting for its
        // debounce timer when the user makes the manual selection.
        desk.posture.pendingLaptopMode = null
        desk.posture.pendingLaptopModeSince = 0L
        desk.posture.laptopModeEvaluationGeneration += 1
        setLaptopMode(true, style)
        OperationLog.i(context, "KeyboardStyle", "overlay enabled ${style.name.lowercase()}")
    }

    /**
     * The setup overlay must show the same destination menu as the live
     * cover-display control, without attempting a vendor device-state change.
     */

    internal fun activateLaptopTrackpad() {
        if (desk.overlayTextInputActive) return
        desk.surfaceView?.releasePointerCapture()
        desk.setOverlayFocusable(false)
        desk.physicalInput.physicalMouseActive = false
        // Cursor mode may have been selected while the laptop deck was
        // already visible. Conversely, tap mode on the phone surface must
        // not prevent the laptop trackpad from attaching its system pointer.
        // Connect lazily on the first trackpad touch so a mode change never
        // races uinput registration.
        desk.virtualPointer.startVirtualMouse()
        desk.desktopCursor.updateVirtualCursorVisibility()
        OperationLog.i(
            context,
            "InputRouting",
            "laptop trackpad claimed input directTouch=${desk.directTouch} " +
                    "pointerProfile=${desk.virtualPointer.activeVirtualPointerProfile()} " +
                    "pointerReady=${desk.laptopTrackpadInputActive()}"
        )
        if (!desk.directTouch && !desk.virtualPointer.virtualMouseInputActive()) {
            desk.desktopCursor.updateCursorPosition()
        }
    }

    internal fun isLaptopAutoDetectionEnabled(): Boolean {
        val preferences = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
        // Automatic laptop mode is opt-in. Once the user changes the switch,
        // the explicit preference always wins.
        return if (preferences.contains("flutter.foldable_laptop_mode")) {
            preferences.getBoolean("flutter.foldable_laptop_mode", false)
        } else {
            false
        }
    }

    /**
     * The foldable upper pane is often landscape even when the device is held
     * vertically. Use the Dextop orientation selection for auto detection,
     * rather than the measured pane width, so posture changes do not cause a
     * laptop deck flicker. Landscape remains available through the overlay's
     * explicit manual action.
     */

    internal fun isLaptopAutoOrientationEligible(): Boolean {
        if (!desk.deviceDisplays.isFoldableDevice()) return false
        return when (desk.posture.laptopFoldProfile()) {
            LaptopFoldProfile.FOLD8 -> {
                // Fold8 laptop posture is the top/bottom (horizontal hinge)
                // layout. Keep its portrait-only automatic gate, but do not
                // reject the session when One UI reports a transient vertical
                // hinge orientation during the panel transition.
                desk.requestedPortrait
            }

            LaptopFoldProfile.STANDARD_FOLDABLE -> {
                // On the normal-size Fold family the usable top/bottom
                // laptop layout is reached after rotating the phone 90° from
                // its natural portrait orientation. In that posture the
                // FoldingFeature hinge runs horizontally, so automatic mode
                // is intentionally landscape-only. Manual overlay activation
                // still works in portrait for testing and recovery.
                !desk.requestedPortrait && desk.posture.foldingApiLaptopPosture != false
            }
        }
    }
}
