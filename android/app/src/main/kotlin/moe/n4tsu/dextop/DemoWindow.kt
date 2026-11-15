package moe.n4tsu.dextop

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** What [DemoWindow] needs from the session beyond the shared interfaces. */
internal interface DemoWindowHost : DesktopSession, OverlayMenu {
    fun buildMenu(): LinearLayout
    val keyboardDeck: KeyboardDeck
    fun menuLayoutParams(): FrameLayout.LayoutParams
    var menuScrim: View?
    fun menuUsesLandscapeLayout(): Boolean
    fun removeWindow()
    var rootWindowParams: WindowManager.LayoutParams?
    /** Written here, not only read. */
    override var demoMode: Boolean
    /** Written here, not only read. */
    override var density: Int
    /** Written here, not only read. */
    override var laptopModeActive: Boolean
    /** Written here, not only read. */
    override var menu: LinearLayout?
    /** Written here, not only read. */
    override var root: TouchRoutingFrame?
    /** Written here, not only read. */
    override var targetHeight: Int
    /** Written here, not only read. */
    override var targetWidth: Int
}

/** Shows and hides the demo session window. */
internal class DemoWindow(
    private val context: Context,
    private val desk: DemoWindowHost,
) {
    internal var demoInfoView: TextView? = null

    internal fun showDemoWindow() {
        if (MirrorService.active) return
        hideDemoWindow()
        MirrorService.pendingDemo = false
        desk.demoMode = true
        val bounds = desk.windowManager?.currentWindowMetrics?.bounds
        desk.targetWidth = bounds?.width()?.takeIf { it > 0 } ?: context.resources.displayMetrics.widthPixels
        desk.targetHeight = bounds?.height()?.takeIf { it > 0 } ?: context.resources.displayMetrics.heightPixels
        desk.density = context.resources.displayMetrics.densityDpi
        val frame = TouchRoutingFrame(context).apply { setBackgroundColor(Color.TRANSPARENT) }
        val scrim = View(context).apply {
            setBackgroundColor(Color.argb(105, 0, 0, 0))
            setOnClickListener { hideDemoWindow() }
        }
        val controls = desk.buildMenu().apply { visibility = View.VISIBLE }
        val laptopDemo = MirrorService.pendingLaptopDemo
        desk.keyboardDeck.laptopPreviewThemeId = MirrorService.pendingLaptopPreviewThemeId
        MirrorService.pendingLaptopPreviewThemeId = null
        MirrorService.pendingLaptopDemo = false
        val info = TextView(context).apply {
            text = NativeStrings.text("nativeTheThreeFingerGestureIsAnEssential")
            textSize = 14f
            setTextColor(Color.rgb(230, 225, 229))
            setPadding(desk.dp(16), desk.dp(12), desk.dp(16), desk.dp(12))
            background = GradientDrawable().apply {
                setColor(Color.rgb(50, 47, 55))
                cornerRadius = desk.dp(16).toFloat()
                setStroke(desk.dp(1), Color.rgb(73, 69, 79))
            }
            elevation = desk.dp(14).toFloat()
        }
        // The laptop keyboard demo already occupies the interaction surface;
        // do not place the generic three-finger gesture hint over it.
        info.visibility = if (laptopDemo) View.GONE else View.VISIBLE
        frame.addView(scrim, FrameLayout.LayoutParams(-1, -1))
        if (laptopDemo) {
            val deck = desk.keyboardDeck.buildLaptopDeck().apply {
                alpha = 0f
                translationY = desk.dp(28).toFloat()
            }
            desk.laptopModeActive = true
            frame.addView(
                deck, FrameLayout.LayoutParams(
                    -1, (context.resources.displayMetrics.heightPixels * .5f).toInt(), Gravity.BOTTOM
                )
            )
            deck.animate().alpha(1f).translationY(0f).setDuration(320L).start()
        }
        // A laptop keyboard demo is a focused keyboard test surface. It must
        // not include the normal Dextop controls or the setup hint; those are
        // reserved for the initial setup/demo surface.
        if (!laptopDemo) {
            frame.addView(controls, desk.menuLayoutParams())
            frame.addView(
                info, if (desk.menuUsesLandscapeLayout()) {
                FrameLayout.LayoutParams(desk.dp(340), -2, Gravity.BOTTOM or Gravity.END).apply {
                    rightMargin = desk.dp(18)
                    bottomMargin = desk.dp(18)
                }
            } else {
                FrameLayout.LayoutParams(-1, -2, Gravity.TOP).apply {
                    leftMargin = desk.dp(12)
                    rightMargin = desk.dp(12)
                    topMargin = desk.dp(24)
                }
            })
        }
        if (!laptopDemo && desk.targetWidth < desk.targetHeight) {
            controls.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, _ ->
                val layout = info.layoutParams as FrameLayout.LayoutParams
                val wantedTop = bottom + desk.dp(12)
                if (layout.topMargin != wantedTop) {
                    layout.topMargin = wantedTop
                    info.layoutParams = layout
                }
            }
        }
        frame.setOnApplyWindowInsetsListener { _, insets ->
            if (laptopDemo) return@setOnApplyWindowInsetsListener insets
            val safe = insets.getInsetsIgnoringVisibility(
                WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout()
            )
            val layout = info.layoutParams as FrameLayout.LayoutParams
            if (desk.menuUsesLandscapeLayout()) {
                layout.rightMargin = desk.dp(18) + safe.right
                layout.bottomMargin = desk.dp(18) + safe.bottom
            } else {
                layout.leftMargin = desk.dp(12) + safe.left
                layout.rightMargin = desk.dp(12) + safe.right
            }
            info.layoutParams = layout
            insets
        }
        desk.root = frame
        desk.menu = controls
        desk.menuScrim = scrim
        demoInfoView = info
        val params = WindowManager.LayoutParams(
            -1,
            -1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            fitInsetsTypes = 0
            setFitInsetsIgnoringVisibility(true)
        }
        desk.rootWindowParams = params
        desk.windowManager?.addView(frame, params)
        frame.requestApplyInsets()
        if (!laptopDemo) {
            controls.alpha = 0f
            controls.scaleX = .94f
            controls.scaleY = .94f
            controls.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(240).start()
        }
    }

    internal fun hideDemoWindow() {
        if (!desk.demoMode) return
        desk.removeWindow()
        desk.laptopModeActive = false
        desk.demoMode = false
    }
}
