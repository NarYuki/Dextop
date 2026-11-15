package moe.n4tsu.dextop

import android.content.Context
import android.graphics.Color
import android.graphics.Point
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.util.Log
import android.view.Gravity
import android.view.Display
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import kotlin.math.roundToInt

/** What [ExternalDisplays] needs from the session beyond the shared interfaces. */
internal interface ExternalDisplaysHost : DesktopSession, OverlayMenu {
    fun activateTouchInput()
    fun applyHostDisplayOrientation(portrait: Boolean)
    val cursorView: CursorView?
    var cursorX: Float
    var cursorY: Float
    fun externalOutputDisplay(): Display?
    val mirror: MirrorLink
    val phoneGuard: PhoneGuard
    val physicalInput: PhysicalInput
    fun reattachExistingDisplay(width: Int, height: Int)
    val requestedPortrait: Boolean
    fun resizeActiveDisplay(next: MirrorService.Config, reason: String)
    val rootWindowParams: WindowManager.LayoutParams?
    fun setLaptopMode(enabled: Boolean, requestedStyle: MirrorService.KeyboardDeckStyle = MirrorService.KeyboardDeckStyle.LAPTOP)
    val surfaceCallback: SurfaceHolder.Callback
    fun surfaceCreated(holder: SurfaceHolder)
    fun updateCursorPosition(x: Float = cursorX, y: Float = cursorY)
    /** Written here, not only read. */
    override var surfaceView: SurfaceView?
}

/** Shows the desktop on a connected external display: mirrored, extended or with the phone as an input deck. */
internal class ExternalDisplays(
    private val context: Context,
    private val desk: ExternalDisplaysHost,
) {
    internal val externalDisplayDetector by lazy { ExternalDisplayDetector(context) }

    internal var physicalExternalDisplayConnected = false

    // Keep the last physical-display inventory independently from input
    // routing.  Samsung leaves physical mouse routing disabled, but its
    // external panels still participate in the display-topology transaction.
    // Once DisplayManager reports removal the panel is no longer returned by
    // snapshot(), so this is the only reliable way to identify that callback
    // as an external-display disconnect.
    @Volatile
    internal var knownPhysicalExternalDisplayIds: Set<Int> = emptySet()

    internal var externalOutput: MirrorService.ExternalOutput? = null

    internal val externalBackend by lazy {
        DisplayMirrorBackend(context, context.contentResolver, context.getSystemService(DisplayManager::class.java),
            desk.privilegedAccess, desk.desktopEnvironment)
    }

    /** In "extend": the parts of the desktop the phone and the external screen show. */
    internal var phoneRegion: Rect? = null

    private var externalRegion: Rect? = null

    internal var externalCursor: CursorView? = null

    /** The part of the desktop (in its own pixels) that [view] shows. */
    internal fun regionFor(view: View?): RectF {
        val output = externalOutput
        val region = when {
            output == null -> null
            view === output.surface -> externalRegion
            else -> phoneRegion
        }
        return region?.let(::RectF) ?: RectF(0f, 0f, desk.targetWidth.toFloat(), desk.targetHeight.toFloat())
    }

    /** Desktop logical density for a screen's real one (as for a host Surface). */
    private fun desktopDensityFor(screenDpi: Int) = (160 + screenDpi / 160f * 24).toInt().coerceIn(160, 320)

    /**
     * The desktop density for an external screen: the scale chosen in Book's settings, else from
     * the screen's physical size as a desktop OS does (96 dpi is 100 %, in 25 % steps), else from
     * Android's density for it. Android reports a high density for most monitors (213 for a
     * 31-inch 1440p one), which made every window too large.
     */

    private fun externalDensityFor(metrics: android.util.DisplayMetrics): Int {
        val chosen = moe.n4tsu.dextop.plasma.BookStore.displayScale(context)
        if (chosen > 0) return (160 * chosen / 100).coerceIn(120, 480)
        val physical = (metrics.xdpi + metrics.ydpi) / 2f
        if (physical in 50f..400f) {
            val scale = ((physical / 96f) * 4).roundToInt().coerceAtLeast(4) / 4f
            return (160 * scale).roundToInt()
        }
        return desktopDensityFor(metrics.densityDpi)
    }

    /** Applies a changed display scale to the desktop on the external screen. */
    internal fun reapplyExternalScale() {
        val output = externalOutput?.takeIf { it.mode == "external" } ?: return
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(output.displayId) ?: return
        val metrics = android.util.DisplayMetrics().also {
            @Suppress("DEPRECATION") display.getRealMetrics(it)
        }
        runCatching {
            desk.resizeActiveDisplay(MirrorService.Config(metrics.widthPixels and -2, metrics.heightPixels and -2,
                externalDensityFor(metrics), desk.secureDisplay, desk.showSystemDecorations), "display scale")
        }.onFailure { OperationLog.e(context, "ExternalOutput", "display scale change failed", it) }
    }

    /** The user's choice from the app: "off", "external", "mirror" or "extend". */
    fun setExternalDisplay(mode: String): String {
        require(mode in setOf("off", "external", "mirror", "extend"))
        if (!MirrorService.active || desk.autoOnlySession || desk.targetDisplayId < 0 || desk.surfaceView == null) return "no_session"
        // The next session (or the next time the screen is plugged in) starts the same way.
        context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE).edit()
            .putString("flutter.external_display_mode", mode).apply()
        if (mode == "extend" && externalOutput?.mode == "extend") {
            rearrangeExtended()
            return "ok"
        }
        if (mode == "external" && externalOutput?.mode == "external") {
            externalOutput?.let(::applyExternalPhoneRole)
            return "ok"
        }
        externalOutput?.let { hideExternalOutput("user") }
        if (mode == "off") return "ok"
        val display = desk.externalOutputDisplay() ?: return "no_display"
        return if (showExternalOutput(display, mode)) "ok" else "failed"
    }

    private fun showExternalOutput(display: Display, mode: String): Boolean {
        val phone = desk.surfaceView ?: return false
        return runCatching {
            val context = context.createDisplayContext(display)
            val manager = context.getSystemService(WindowManager::class.java)
            val surface = SurfaceView(context).apply {
                // Physical mice on the external screen act on the desktop like the phone's surface.
                setOnGenericMotionListener { _, event -> desk.physicalInput.handlePhysicalMouseEvent(event, this) }
                setOnHoverListener { _, event -> desk.physicalInput.handlePhysicalMouseEvent(event, this) }
            }
            val frame = FrameLayout(context).apply {
                setBackgroundColor(Color.BLACK)
                addView(surface, FrameLayout.LayoutParams(-1, -1))
            }
            val params = WindowManager.LayoutParams(
                -1, -1,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.OPAQUE,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                fitInsetsTypes = 0
                title = "Dextop External"
            }
            val output = MirrorService.ExternalOutput(mode, display.displayId, manager, frame, surface, phone,
                MirrorService.Config(desk.targetWidth, desk.targetHeight, desk.density, desk.secureDisplay, desk.showSystemDecorations),
                ::attachExternalMirror, { externalBackend.releaseLayer() })
            externalOutput = output
            val metrics = android.util.DisplayMetrics().also {
                @Suppress("DEPRECATION") display.getRealMetrics(it)
            }
            if (mode == "external") {
                // The external window becomes the host Surface; the phone's stops driving the desktop.
                phone.holder.removeCallback(desk.surfaceCallback)
                desk.mirror.releaseMirror()
                desk.surfaceView = surface
                surface.holder.addCallback(desk.surfaceCallback)
            } else {
                surface.holder.addCallback(output)
            }
            if (mode == "extend") {
                arrangeExtended(output, metrics)
                externalCursor = CursorView(context).also { cursor ->
                    cursor.visibility = desk.cursorView?.visibility ?: View.GONE
                    frame.addView(cursor, FrameLayout.LayoutParams(-1, -1))
                }
            }
            manager.addView(frame, params)
            desk.updateCursorPosition()
            when (mode) {
                // The desktop takes the external screen's size and pixel density.
                "external" -> {
                    runCatching {
                        desk.resizeActiveDisplay(MirrorService.Config(metrics.widthPixels and -2, metrics.heightPixels and -2,
                            externalDensityFor(metrics), desk.secureDisplay, desk.showSystemDecorations), "external display")
                    }.onFailure { OperationLog.e(context, "ExternalOutput", "external resize failed", it) }
                    applyExternalPhoneRole(output)
                }
                "extend" -> {
                    runCatching {
                        desk.resizeActiveDisplay(extendedDesktopConfig(), "extended desktop")
                    }.onFailure { OperationLog.e(context, "ExternalOutput", "extended resize failed", it) }
                    // The phone shows only its part now.
                    desk.mirror.releaseMirror()
                    desk.surfaceView?.takeIf { it.width > 0 }?.let { desk.reattachExistingDisplay(it.width, it.height) }
                    applyDesktopOutputs()
                }
            }
            OperationLog.i(context, "ExternalOutput", "desktop $mode on display=${display.displayId}")
        }.onFailure { error ->
            OperationLog.e(context, "ExternalOutput", "external display output failed", error)
            hideExternalOutput("failed")
        }.isSuccess
    }

    /** Mirror and extend: the external window shows the desktop, or its part of it. */
    internal fun attachExternalMirror() {
        val output = externalOutput?.takeIf { it.mode == "mirror" || it.mode == "extend" } ?: return
        val surface = output.surface
        if (!surface.holder.surface.isValid || surface.width <= 0 || desk.targetDisplayId < 0) return
        runCatching {
            externalBackend.attach(desk.targetDisplayId, surface, surface.width, surface.height,
                desk.targetWidth, desk.targetHeight, desk.density, desk.mirror.configuredMirrorStrategyOverride(), rebuild = true,
                crop = if (output.mode == "extend") externalRegion else null)
        }.onFailure { OperationLog.e(context, "ExternalOutput", "external mirror failed", it) }
    }

    internal fun externalDisplayPreference(key: String, fallback: String): String =
        context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE).getString("flutter.$key", fallback) ?: fallback

    /** A screen's size in the desktop at its "scale" percentage (100 = [base] as is). */
    private fun extendedScreenSize(base: Point, screen: Int): Point {
        val percent = (externalDisplayPreference("external_display_scale_$screen", "100").toIntOrNull() ?: 100)
            .coerceIn(MirrorService.EXTENDED_SCALES.first(), MirrorService.EXTENDED_SCALES.last())
        return Point((base.x * 100f / percent).roundToInt() and -2, (base.y * 100f / percent).roundToInt() and -2)
    }

    /**
     * Places each screen's part of the desktop, as arranged in the app: the external screen
     * touches one side of the phone's ("external_display_position": left/right/top/bottom),
     * shifted along it by "external_display_along" desktop pixels. Each part is as large as
     * its screen at its own scale, so both screens show the desktop at their own size; the
     * desktop is the box around both (any corner outside them shows nothing).
     */

    private fun arrangeExtended(output: MirrorService.ExternalOutput, metrics: android.util.DisplayMetrics) {
        val phone = extendedScreenSize(Point(output.phoneConfig.width, output.phoneConfig.height), 1)
        // At 100 % the external screen looks as it does when it shows the desktop alone.
        val externalDensity = externalDensityFor(metrics).toFloat()
        val external = extendedScreenSize(Point(
            (metrics.widthPixels * output.phoneConfig.density / externalDensity).roundToInt(),
            (metrics.heightPixels * output.phoneConfig.density / externalDensity).roundToInt()), 2)
        val side = externalDisplayPreference("external_display_position", "right")
        val vertical = side == "top" || side == "bottom"
        // At least a quarter of the shorter shared edge stays in contact, so the pointer can cross.
        val contact = if (vertical) minOf(phone.x, external.x) / 4 else minOf(phone.y, external.y) / 4
        val along = (externalDisplayPreference("external_display_along", "0").toIntOrNull() ?: 0).coerceIn(
            -(if (vertical) external.x else external.y) + contact,
            (if (vertical) phone.x else phone.y) - contact)
        val externalRect = when (side) {
            "left" -> Rect(-external.x, along, 0, along + external.y)
            "top" -> Rect(along, -external.y, along + external.x, 0)
            "bottom" -> Rect(along, phone.y, along + external.x, phone.y + external.y)
            else -> Rect(phone.x, along, phone.x + external.x, along + external.y)
        }
        val phoneRect = Rect(0, 0, phone.x, phone.y)
        val dx = -minOf(0, externalRect.left)
        val dy = -minOf(0, externalRect.top)
        phoneRect.offset(dx, dy)
        externalRect.offset(dx, dy)
        phoneRegion = phoneRect
        externalRegion = externalRect
    }

    /** The extended desktop: the box around both screens' parts. */
    private fun extendedDesktopConfig(): MirrorService.Config {
        val phone = phoneRegion ?: return MirrorService.Config(desk.targetWidth, desk.targetHeight, desk.density, desk.secureDisplay, desk.showSystemDecorations)
        val external = externalRegion ?: phone
        val box = Rect(phone).apply { union(external) }
        return MirrorService.Config(box.right and -2, box.bottom and -2, externalOutput?.phoneConfig?.density ?: desk.density,
            desk.secureDisplay, desk.showSystemDecorations)
    }

    /** The arrangement, a scale or the main display changed while extended. */
    private fun rearrangeExtended() {
        val output = externalOutput?.takeIf { it.mode == "extend" } ?: return
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(output.displayId) ?: return
        val metrics = android.util.DisplayMetrics().also {
            @Suppress("DEPRECATION") display.getRealMetrics(it)
        }
        arrangeExtended(output, metrics)
        runCatching { desk.resizeActiveDisplay(extendedDesktopConfig(), "extended desktop rearranged") }
            .onFailure { OperationLog.e(context, "ExternalOutput", "extended rearrange resize failed", it) }
        desk.mirror.releaseMirror()
        desk.surfaceView?.takeIf { it.width > 0 }?.let { desk.reattachExistingDisplay(it.width, it.height) }
        attachExternalMirror()
        applyDesktopOutputs()
        desk.updateCursorPosition()
        OperationLog.i(context, "ExternalOutput", "extended desktop rearranged phone=$phoneRegion external=$externalRegion")
    }

    /**
     * The extended desktop for the app's arrangement editor, in place of Android's topology
     * (which these phones do not apply to the pointer): one entry per screen, in desktop pixels.
     */

    fun extendedArrangement(): Map<String, Any>? {
        val output = externalOutput?.takeIf { it.mode == "extend" } ?: return null
        val phone = phoneRegion ?: return null
        val external = externalRegion ?: return null
        val manager = context.getSystemService(DisplayManager::class.java)
        val phoneMain = externalDisplayPreference("external_display_main", "2") == "1"
        fun screen(screen: Int, displayId: Int, region: Rect, primary: Boolean): Map<String, Any> {
            val display = manager.getDisplay(displayId)
            val metrics = android.util.DisplayMetrics().also {
                @Suppress("DEPRECATION") display?.getRealMetrics(it)
            }
            return linkedMapOf(
                "id" to displayId,
                "name" to (display?.name ?: moe.n4tsu.dextop.InlineStrings.format(moe.n4tsu.dextop.localText("uiDisplay0"), displayId)),
                "x" to region.left.toDouble(),
                "y" to region.top.toDouble(),
                "widthDp" to region.width().toDouble(),
                "heightDp" to region.height().toDouble(),
                "widthPx" to metrics.widthPixels,
                "heightPx" to metrics.heightPixels,
                "densityDpi" to metrics.densityDpi,
                "activeModeId" to (display?.mode?.modeId ?: -1),
                "refreshRate" to (display?.refreshRate?.toDouble() ?: 0.0),
                // The phone's own resolution belongs to Dextop's resolution settings.
                "supportedModes" to if (screen == 1) emptyList() else display?.supportedModes?.map { mode ->
                    linkedMapOf<String, Any>("id" to mode.modeId, "width" to mode.physicalWidth,
                        "height" to mode.physicalHeight, "refreshRate" to mode.refreshRate.toDouble())
                }.orEmpty(),
                "primary" to primary,
                "dextopOverlay" to false,
                "extendedScreen" to screen,
                "scalePercent" to (externalDisplayPreference("external_display_scale_$screen", "100").toIntOrNull() ?: 100),
                "scalePercents" to MirrorService.EXTENDED_SCALES,
            )
        }
        return linkedMapOf(
            "supported" to true,
            "extended" to true,
            "displays" to listOf(
                screen(1, Display.DEFAULT_DISPLAY, phone, phoneMain),
                screen(2, output.displayId, external, !phoneMain),
            ),
        )
    }

    /** Takes positions from the arrangement editor: where the external screen touches the phone's. */
    fun arrangeExtendedFromEditor(positions: Map<*, *>): Boolean {
        val output = externalOutput?.takeIf { it.mode == "extend" } ?: return false
        val phone = phoneRegion ?: return false
        val external = externalRegion ?: return false
        fun position(id: Int): PointF? = (positions["$id"] as? Map<*, *>)?.let {
            PointF((it["x"] as? Number)?.toFloat() ?: return null, (it["y"] as? Number)?.toFloat() ?: return null)
        }
        val p = position(Display.DEFAULT_DISPLAY) ?: return false
        val e = position(output.displayId) ?: return false
        val x = (e.x - p.x).roundToInt()
        val y = (e.y - p.y).roundToInt()
        val (side, along) = when {
            x >= phone.width() - 1 -> "right" to y
            x + external.width() <= 1 -> "left" to y
            y >= phone.height() - 1 -> "bottom" to x
            else -> "top" to x
        }
        context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE).edit()
            .putString("flutter.external_display_position", side)
            .putString("flutter.external_display_along", along.toString())
            .apply()
        rearrangeExtended()
        return true
    }

    /** A screen's scale (percent, 100 = recommended) on the extended desktop. */
    fun setExtendedScale(displayId: Int, percent: Int): Boolean {
        val output = externalOutput?.takeIf { it.mode == "extend" } ?: return false
        val screen = when (displayId) {
            Display.DEFAULT_DISPLAY -> 1
            output.displayId -> 2
            else -> return false
        }
        context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE).edit()
            .putString("flutter.external_display_scale_$screen",
                percent.coerceIn(MirrorService.EXTENDED_SCALES.first(), MirrorService.EXTENDED_SCALES.last()).toString())
            .apply()
        rearrangeExtended()
        return true
    }

    /** Tells Dextop's window manager where each screen is: the main one (its taskbar) first. */
    internal fun applyDesktopOutputs() {
        val phoneMain = externalDisplayPreference("external_display_main", "2") == "1"
        val outputs = if (phoneMain) listOfNotNull(phoneRegion, externalRegion) else listOfNotNull(externalRegion, phoneRegion)
        desk.plasmaShell?.setOutputs(outputs)
    }

    private fun hideExternalOutput(reason: String) {
        val output = externalOutput ?: return
        externalOutput = null
        output.surface.holder.removeCallback(output)
        externalBackend.releaseLayer()
        if (output.mode == "external") {
            output.surface.holder.removeCallback(desk.surfaceCallback)
            desk.mirror.releaseMirror()
        }
        runCatching { output.windowManager.removeView(output.frame) }
        externalCursor = null
        if (output.mode == "external") releaseExternalPhoneRole(output)
        if (output.mode == "external") {
            if (desk.surfaceView === output.surface) desk.surfaceView = output.phoneSurface
            runCatching { desk.resizeActiveDisplay(output.phoneConfig, "external display removed") }
            val phone = output.phoneSurface
            phone.holder.addCallback(desk.surfaceCallback)
            if (phone.holder.surface.isValid) desk.surfaceCreated(phone.holder)
        }
        if (output.mode == "extend") {
            phoneRegion = null
            externalRegion = null
            desk.mirror.releaseMirror()
            runCatching { desk.resizeActiveDisplay(output.phoneConfig, "extended desktop ended") }
                .onFailure { OperationLog.e(context, "ExternalOutput", "extended desktop shrink failed", it) }
            desk.plasmaShell?.setOutputs(emptyList())
            desk.surfaceView?.takeIf { it.width > 0 }?.let { desk.reattachExistingDisplay(it.width, it.height) }
            desk.cursorX = desk.cursorX.coerceIn(0f, desk.targetWidth - 1f)
            desk.cursorY = desk.cursorY.coerceIn(0f, desk.targetHeight - 1f)
            desk.updateCursorPosition()
        }
        OperationLog.i(context, "ExternalOutput", "external output ${output.mode} ended reason=$reason")
    }
    // endregion

    /**
     * "External display only": the phone either stays a phone (Dextop's window on it shrinks
     * away, rotation and navigation return to Android) or becomes the desktop's trackpad and
     * keyboard (the laptop deck over the whole phone screen), as chosen in the app
     * ("external_display_phone": "phone" / "touchpad").
     */

    internal var externalPhoneRole: String? = null

    /** The laptop deck is shown for the external screen, not as a foldable's laptop half. */
    internal var externalInputDeck = false

    private fun applyExternalPhoneRole(output: MirrorService.ExternalOutput) {
        val role = externalDisplayPreference("external_display_phone", "phone")
        if (role == externalPhoneRole) return
        releaseExternalPhoneRole(output)
        externalPhoneRole = role
        output.phoneSurface.visibility = View.GONE
        if (role == "phone") {
            setPhoneFreeForExternalOutput(true)
        } else {
            externalInputDeck = true
            desk.setLaptopMode(true, MirrorService.KeyboardDeckStyle.LAPTOP)
        }
        OperationLog.i(context, "ExternalOutput", "phone role while external only: $role")
    }

    private fun releaseExternalPhoneRole(output: MirrorService.ExternalOutput) {
        when (externalPhoneRole) {
            "phone" -> setPhoneFreeForExternalOutput(false)
            "touchpad" -> if (desk.laptopModeActive) desk.setLaptopMode(false)
        }
        externalInputDeck = false
        externalPhoneRole = null
        output.phoneSurface.visibility = View.VISIBLE
    }

    /** Shrinks Dextop's phone window to nothing and gives the phone back to Android, or undoes it. */
    private fun setPhoneFreeForExternalOutput(free: Boolean) {
        val frame = desk.root ?: return
        val params = desk.rootWindowParams ?: return
        val untouchable = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        if (free) {
            params.width = 1
            params.height = 1
            params.flags = (params.flags or untouchable) and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON.inv()
        } else {
            params.width = -1
            params.height = -1
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            if (context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
                    .getBoolean("flutter.keep_awake_during_session", true)
            ) params.flags = params.flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        }
        runCatching { desk.windowManager?.updateViewLayout(frame, params) }
            .onFailure { OperationLog.w(context, "ExternalOutput", "phone window update failed free=$free", it) }
        desk.phoneGuard.setPhoneNavigationDisabled(!free)
        if (free) {
            desk.phoneGuard.releasePhoneRotation()
            MainActivity.restoreOrientation()
        } else {
            desk.applyHostDisplayOrientation(desk.requestedPortrait)
            desk.phoneGuard.forcePhoneRotation(desk.requestedPortrait, force = true)
        }
    }

    /** The external screen last chosen in the app, tried once per connected screen. */
    internal var externalAutoAttemptedFor = -1

    /** Shows the desktop on a connected external screen as last chosen ("extend", "mirror", "external"). */
    internal fun restoreExternalDisplayMode(reason: String) {
        if (!MirrorService.active || desk.autoOnlySession || externalOutput != null || desk.targetDisplayId < 0 || desk.surfaceView == null) return
        val display = desk.externalOutputDisplay() ?: run { externalAutoAttemptedFor = -1; return }
        if (display.displayId == externalAutoAttemptedFor) return
        val mode = externalDisplayPreference("external_display_mode", "off")
        if (mode !in setOf("extend", "mirror", "external")) return
        externalAutoAttemptedFor = display.displayId
        val shown = showExternalOutput(display, mode)
        revealPhoneWindow()
        OperationLog.i(context, "ExternalOutput", "restored $mode on display=${display.displayId} shown=$shown reason=$reason")
    }

    /** The phone window was hidden while the session started for an external screen only. */
    internal var externalStartPending = false

    internal fun revealPhoneWindow() {
        if (!externalStartPending) return
        externalStartPending = false
        val frame = desk.root ?: return
        val params = desk.rootWindowParams ?: return
        params.alpha = 1f
        // The phone role keeps the window untouchable (see setPhoneFreeForExternalOutput).
        if (externalPhoneRole != "phone") params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        runCatching { desk.windowManager?.updateViewLayout(frame, params) }
    }

    internal fun refreshExternalDisplayState() {
        desk.root?.post {
            externalOutput?.let { output ->
                if (context.getSystemService(DisplayManager::class.java).getDisplay(output.displayId) == null) hideExternalOutput("unplugged")
            }
            if (externalOutput == null && MirrorService.active) {
                if (desk.externalOutputDisplay() == null) externalAutoAttemptedFor = -1
                else desk.root?.postDelayed({ restoreExternalDisplayMode("display_connected") }, 800)
            }
            val externalState = externalDisplayDetector.snapshot()
            knownPhysicalExternalDisplayIds = externalState.displayIds.toSet()
            val connected = desk.physicalInput.physicalInputRoutingSupported && externalState.connected
            if (connected == physicalExternalDisplayConnected) return@post
            physicalExternalDisplayConnected = connected
            val display = context.getSystemService(DisplayManager::class.java).getDisplay(desk.targetDisplayId)
            if (connected && MirrorService.active && display != null) {
                runCatching {
                    desk.physicalInput.physicalInputRouter.apply(display, desk.physicalInput.routePhysicalMice, desk.physicalInput.routePhysicalKeyboardToDextop)
                }.onFailure { OperationLog.w(context, "InputRouting", "external display routing failed", it) }
                if (desk.physicalInput.routePhysicalMouseToDextop) desk.physicalInput.startRawMouseReader()
            } else {
                runCatching { desk.physicalInput.physicalInputRouter.restore() }
                    .onFailure {
                        OperationLog.w(
                            context,
                            "InputRouting",
                            "external display disconnect restoration failed",
                            it
                        )
                    }
                desk.physicalInput.stopRawMouseReader()
                desk.activateTouchInput()
                desk.physicalInput.mouseActuallyRouted = false
                desk.physicalInput.keyboardActuallyRouted = false
                // Unplugging must not hand the mice back to the focused window display.
                if (MirrorService.active && display != null && desk.physicalInput.routePhysicalMice) desk.physicalInput.refreshPhysicalInputState()
            }
            desk.physicalInput.refreshActualRoutingState(display)
            desk.menuPrimary?.let(desk::showMainMenu)
            Log.i(desk.logTag, "physical external display connected=$connected")
        }
    }

    internal var extendSink: android.media.ImageReader? = null

    internal fun closeExtendSink() {
        val sink = extendSink ?: return
        extendSink = null
        runCatching { sink.close() }
    }
}
