package moe.n4tsu.dextop

import android.content.Context
import android.graphics.Rect
import android.util.Log
import android.view.Surface
import android.view.View
import android.view.WindowManager

/** What [MirrorLink] needs from the session beyond the shared interfaces. */
internal interface MirrorLinkHost : DesktopSession, OverlayMenu {
    var autoDestinationSurface: Surface?
    var autoOwnedDisplay: OwnedVirtualDisplay?
    fun closeExtendSink()
    fun directDisplaySurface(): Surface
    val displayBackend: DisplayMirrorBackend
    var displayCreationInProgress: Boolean
    fun displayGeometrySnapshot(reason: String, hostWidth: Int? = null, hostHeight: Int? = null): String
    val phoneOwnedDisplay: OwnedVirtualDisplay?
    val phoneRegion: Rect?
    fun stopRawMouseReader()
    val virtualPointer: VirtualPointer
}

/** Mirrors the desktop display onto the phone surface and refreshes that mirror when geometry changes. */
internal class MirrorLink(
    private val context: Context,
    private val desk: MirrorLinkHost,
) {
    internal val useLegacyPixelMirrorProfile by lazy { PixelMirrorFallback.shouldUse(context) }

    internal var mirrorDisplayId = -1

    internal fun restoreSoftwareCursorAfterMirrorFailure(reason: String) {
        desk.virtualPointer.stopVirtualMouse()
        if (!desk.directTouch) {
            desk.desktopCursor.cursorView?.apply {
                visibility = View.VISIBLE
                bringToFront()
                update(
                    (desk.desktopCursor.cursorX / desk.targetWidth.coerceAtLeast(1)).coerceIn(0f, 1f),
                    (desk.desktopCursor.cursorY / desk.targetHeight.coerceAtLeast(1)).coerceIn(0f, 1f)
                )
            }
        } else {
            desk.desktopCursor.cursorView?.visibility = View.GONE
        }
        desk.desktopCursor.updateVirtualCursorVisibility()
        OperationLog.w(
            context,
            "InputRouting",
            "software cursor restored after mirror failure reason=$reason"
        )
    }

    /**
     * A compact runtime snapshot is attached to the session log whenever the
     * host surface or logical display changes. This is intentionally separate
     * from Logcat so a shared diagnostic report contains the geometry that was
     * actually used for coordinate conversion.
     */

    internal var mirrorHostWidth = 0

    internal var mirrorHostHeight = 0

    internal var mirrorRefreshGeneration = 0

    internal fun releaseMirror() {
        mirrorRefreshGeneration += 1
        desk.stopRawMouseReader()
        desk.displayBackend.releaseLayer()
        // The direct display is the desktop: keep it (and its tasks) while
        // the host Surface is gone; attachMirror() rebinds the next Surface.
        runCatching { desk.autoOwnedDisplay?.attachment?.release() }
        desk.autoOwnedDisplay = null
        desk.autoDestinationSurface = null
        mirrorDisplayId = -1
        mirrorHostWidth = 0
        mirrorHostHeight = 0
        desk.displayCreationInProgress = false
    }

    internal fun attachMirror(
        hostWidth: Int,
        hostHeight: Int,
        strategyOverride: String? = null,
        rebuild: Boolean = false,
    ) {
        val host = desk.surfaceView ?: error(NativeStrings.text("nativeMirrorSurfaceUnavailable"))
        desk.phoneOwnedDisplay?.let { owned ->
            if (desk.phoneRegion != null) {
                // Extended: the display renders off screen and the phone mirrors its part,
                // as the external screen does.
                check(owned.resize(desk.directDisplaySurface(), desk.targetWidth, desk.targetHeight, desk.density)) {
                    moe.n4tsu.dextop.localText("uiUnableToMoveTheDirectDextopDisplayOffScreen")
                }
                OperationLog.i(context, "DisplayBackend", "direct Plasma display off screen for extended desktop " +
                    "desktop=${desk.targetWidth}x${desk.targetHeight} phone=${desk.phoneRegion}")
                return@let
            }
            desk.closeExtendSink()
            val surface = host.holder.surface
            check(surface.isValid) { NativeStrings.text("nativeMirrorSurfaceUnavailable") }
            check(owned.resize(surface, desk.targetWidth, desk.targetHeight, desk.density)) {
                moe.n4tsu.dextop.localText("uiUnableToBindTheDirectDextopDisplayToTheHostSurface")
            }
            mirrorHostWidth = hostWidth
            mirrorHostHeight = hostHeight
            OperationLog.i(
                context,
                "DisplayBackend",
                "direct Plasma display bound host=${hostWidth}x$hostHeight " +
                    "desktop=${desk.targetWidth}x${desk.targetHeight}/${desk.density}"
            )
            return
        }
        desk.displayBackend.attach(
            desk.targetDisplayId,
            host,
            hostWidth,
            hostHeight,
            desk.targetWidth,
            desk.targetHeight,
            desk.density,
            strategyOverride,
            rebuild,
            crop = desk.phoneRegion,
        )
        mirrorHostWidth = hostWidth
        mirrorHostHeight = hostHeight
    }

    internal fun configuredMirrorStrategyOverride(): String? {
        // A persisted legacy Pixel profile must be allowed to walk its older
        // backend order even when the UI still contains the default
        // "virtual_display" selection from a previous build.
        if (useLegacyPixelMirrorProfile) return null
        val configuredBackend = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
            .getString("flutter.mirror_backend", "virtual_display") ?: "virtual_display"
        return configuredBackend.takeUnless { it == "auto" }
    }

    /**
     * One UI can replace transition/SystemUI layers when recents, fold state, or
     * the host surface changes. Recreating only the mirror attachment keeps the
     * desktop tasks and physical-input routing alive while acquiring that new
     * layer tree.
     */

    internal fun scheduleMirrorRefresh(
        reason: String,
        width: Int? = null,
        height: Int? = null,
        forceVirtualDisplay: Boolean = false,
        /** Recreate the recording display; see DesktopEnvironment.rebuildMirrorAfterLiveResize. */
        rebuild: Boolean = false,
    ) {
        // A content-recording VirtualDisplay follows changes on its mirrored
        // display without being recreated. Releasing/recreating it in response
        // to DisplayListener or configuration callbacks races Samsung
        // WindowManager/SystemUI: the old display is removed while windows are
        // already being attached to the replacement. In particular this is
        // triggered when MainActivity is opened on the desktop display.
        // A destroyed Surface is handled separately by surfaceDestroyed /
        // surfaceCreated, and an actual profile change goes through start().
        // A recording VirtualDisplay can follow content changes without being
        // recreated, but its destination Surface still has to be rebound when
        // the host window changes geometry. Foldables commonly create the
        // service window in portrait and rotate it to landscape a moment
        // later; skipping that update leaves the virtual display attached to
        // the old portrait buffer and produces a black/offset desktop.
        val hostGeometryChanged = width != null && height != null &&
                (width != mirrorHostWidth || height != mirrorHostHeight)
        if (desk.displayBackend.activeStrategy == "virtual_display" &&
            !forceVirtualDisplay && !hostGeometryChanged
        ) {
            OperationLog.i(
                context,
                "DisplayBackend",
                "mirror refresh skipped strategy=virtual_display reason=$reason"
            )
            return
        }
        val generation = ++mirrorRefreshGeneration
        desk.root?.postDelayed({
            if (!MirrorService.active || desk.targetDisplayId < 0 || generation != mirrorRefreshGeneration) {
                return@postDelayed
            }
            val host = desk.surfaceView ?: return@postDelayed
            val nextWidth: Int = width?.takeIf { it > 0 } ?: host.width
            val nextHeight: Int = height?.takeIf { it > 0 } ?: host.height
            if (nextWidth <= 0 || nextHeight <= 0 || !host.holder.surface.isValid) {
                return@postDelayed
            }
            val strategyOverride = configuredMirrorStrategyOverride()
            runCatching {
                attachMirror(nextWidth, nextHeight, strategyOverride, rebuild)
                mirrorDisplayId = desk.targetDisplayId
            }.onSuccess {
                OperationLog.i(
                    context,
                    "DisplayBackend",
                    "mirror refreshed reason=$reason host=${nextWidth}x$nextHeight " +
                            "content=${desk.targetWidth}x${desk.targetHeight}/${desk.density} " +
                            desk.displayGeometrySnapshot("mirror_refresh_completed", nextWidth, nextHeight)
                )
            }.onFailure { error ->
                mirrorDisplayId = -1
                restoreSoftwareCursorAfterMirrorFailure(reason)
                OperationLog.e(context, "DisplayBackend", "mirror refresh failed reason=$reason", error)
                Log.e(desk.logTag, "mirror refresh failed reason=$reason", error)
            }
        }, 180)
    }
}
