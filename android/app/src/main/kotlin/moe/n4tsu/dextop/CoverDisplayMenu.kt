package moe.n4tsu.dextop

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.view.Display
import android.widget.LinearLayout
import android.widget.Toast

/** What [CoverDisplayMenu] needs from the session beyond the shared interfaces. */
internal interface CoverDisplayMenuHost : DesktopSession, OverlayMenu {
    val cast: CastController
    val gamepad: VirtualGamepad
    val hostFollower: HostDisplayFollower
    fun internalDisplays(manager: DisplayManager): List<Display>
    fun isFoldableDevice(): Boolean
    fun isFoldableMainDisplay(): Boolean
    val menuWidgets: MenuWidgets
    val posture: LaptopPosture
}

/** The menu and lifecycle checks for running the session on a foldable cover display. */
internal class CoverDisplayMenu(
    private val context: Context,
    private val desk: CoverDisplayMenuHost,
) {
    internal val coverDisplayController by lazy { CoverDisplaySessionController(context) }

    private var coverLifecycleCheckGeneration = 0

    internal fun scheduleCoverDisplayLifecycleCheck(reason: String) {
        val coverState = coverDisplayController.state()
        if (!coverState.desktopActive && !coverState.backButtonsActive) return
        val generation = ++coverLifecycleCheckGeneration
        Handler(context.mainLooper).postDelayed({
            if (generation != coverLifecycleCheckGeneration) return@postDelayed
            desk.posture.refreshFoldingApiState("cover_lifecycle", force = true)
            coverDisplayController.reconcileLifecycle(reason, coverDisplayLifecycleToken())
        }, 300L)
    }

    internal fun coverDisplayLifecycleToken(): String {
        val manager = context.getSystemService(DisplayManager::class.java)
        val display = manager.getDisplay(Display.DEFAULT_DISPLAY)
        val mode = display?.mode
        val internalIds = desk.internalDisplays(manager).map { it.displayId }.sorted()
        return buildString {
            append(display?.name.orEmpty())
            append('|').append(mode?.physicalWidth ?: 0)
            append('x').append(mode?.physicalHeight ?: 0)
            append("|internal=").append(internalIds.joinToString(","))
            append("|foldable=").append(desk.isFoldableDevice())
            append("|half=").append(desk.posture.foldingApiLaptopPosture)
        }
    }

    internal fun isCoverDisplayFeatureEnabled(): Boolean {
        val preferences = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
        return if (preferences.contains("flutter.cover_display_enabled")) {
            preferences.getBoolean("flutter.cover_display_enabled", false)
        } else if (preferences.contains("flutter.experimental_cover_display")) {
            // Preserve an explicit selection made before this became a formal
            // Foldable feature.
            preferences.getBoolean("flutter.experimental_cover_display", false)
        } else {
            // The feature is enabled by default on supported foldables.
            true
        }
    }

    internal fun isCurrentFoldableCoverDisplay(): Boolean {
        if (!desk.isFoldableDevice()) return false
        val displays = desk.internalDisplays(context.getSystemService(DisplayManager::class.java))
        return displays.size >= 2 && !desk.isFoldableMainDisplay()
    }

    internal fun showCoverDisplayMenu(panel: LinearLayout) {
        desk.cast.stopCastRouteDiscovery()
        desk.menuWidgets.animateMenuResize(panel)
        panel.removeAllViews()
        panel.addView(desk.menuWidgets.menuTitle(
            NativeStrings.text("nativeCoverDisplay"),
            NativeStrings.text("nativeReturn"),
        ) { desk.showMainMenu(panel) })

        val state = coverDisplayController.state()
        panel.addView(desk.menuWidgets.actionButton(
            R.drawable.ic_smartphone,
            NativeStrings.text(
                if (state.androidVisible) "nativeStopCoverAndroid" else "nativeOpenCoverAndroid",
            ),
        ) {
            coverDisplayController.setAndroidVisible(!state.androidVisible) { result ->
                result.onFailure { showCoverDisplayFailure(it) }
                showCoverDisplayMenu(panel)
            }
        })
        panel.addView(desk.menuWidgets.actionButton(
            R.drawable.ic_monitor,
            NativeStrings.text(
                if (state.desktopActive) "nativeStopCoverDextop" else "nativeStartCoverDextop",
            ),
        ) {
            if (state.desktopActive) {
                coverDisplayController.stopDesktop { result ->
                    result.onFailure { showCoverDisplayFailure(it) }
                    showCoverDisplayMenu(panel)
                }
            } else {
                coverDisplayController.startDesktop(
                    desk.targetWidth,
                    desk.targetHeight,
                    desk.density,
                    desk.secureDisplay,
                    ::coverDisplayLifecycleToken,
                ) { result ->
                    result.onSuccess {
                        desk.hostFollower.activateTopologyForIndependentDisplays("cover_started")
                    }.onFailure { showCoverDisplayFailure(it) }
                    showCoverDisplayMenu(panel)
                }
            }
        })
        if (!desk.gamepad.isGamepadDeckActive()) {
            panel.addView(desk.menuWidgets.actionButton(
                R.drawable.ic_mouse,
                NativeStrings.text("nativeInputTrackpad") + if (state.trackpadActive) "  ✓" else "",
            ) {
                if (state.trackpadActive) {
                    coverDisplayController.stopTrackpadMode { result ->
                        result.onFailure { showCoverDisplayFailure(it) }
                        showCoverDisplayMenu(panel)
                    }
                } else {
                    coverDisplayController.startTrackpadMode(::coverDisplayLifecycleToken) { result ->
                        result.onSuccess { desk.hostFollower.activateTopologyForIndependentDisplays("cover_trackpad_started") }
                            .onFailure { showCoverDisplayFailure(it) }
                        showCoverDisplayMenu(panel)
                    }
                }
            })
        }
        desk.scheduleMenuHeightUpdate()
    }

    internal fun showCoverDisplayDemoMenu(panel: LinearLayout) {
        desk.cast.stopCastRouteDiscovery()
        desk.menuWidgets.animateMenuResize(panel)
        panel.removeAllViews()
        panel.addView(desk.menuWidgets.menuTitle(
            NativeStrings.text("nativeCoverDisplay"),
            NativeStrings.text("nativeReturn"),
        ) { desk.showMainMenu(panel) })
        panel.addView(desk.menuWidgets.menuHint(NativeStrings.text("nativeCoverDisplayDescription")))
        panel.addView(desk.menuWidgets.actionButton(
            R.drawable.ic_smartphone,
            NativeStrings.text("nativeOpenCoverAndroid"),
        ) {
            desk.demoExplanation(NativeStrings.text("nativeCoverAndroidDescription"))
        })
        panel.addView(desk.menuWidgets.actionButton(
            R.drawable.ic_monitor,
            NativeStrings.text("nativeStartCoverDextop"),
        ) {
            desk.demoExplanation(NativeStrings.text("nativeCoverDextopDescription"))
        })
        if (desk.gamepad.isExperimentalGamepadEnabled()) {
            panel.addView(desk.menuWidgets.actionButton(
                R.drawable.ic_gamepad,
                NativeStrings.text("nativeStartBackButtons"),
            ) {
                desk.demoExplanation(NativeStrings.text("nativeBackButtonsDescription"))
            })
        }
        desk.scheduleMenuHeightUpdate()
    }

    internal fun showCoverDisplayFailure(error: Throwable) {
        OperationLog.e(context, "CoverDisplay", "session update failed", error)
        Toast.makeText(
            context,
            error.message ?: NativeStrings.text("nativeCoverDisplayFailed"),
            Toast.LENGTH_LONG,
        ).show()
    }
}
