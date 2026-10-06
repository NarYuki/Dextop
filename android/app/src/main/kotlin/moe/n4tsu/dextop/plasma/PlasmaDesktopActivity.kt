package moe.n4tsu.dextop.plasma

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Bundle
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

/**
 * The desktop layer of the Dextop window manager.
 *
 * It is started as a secondary HOME activity on the Dextop display so the
 * framework keeps it underneath every application task (tapping HOME never
 * raises it over windows).  It shows the wallpaper and forwards desktop
 * clicks to [PlasmaShell].
 */
class PlasmaDesktopActivity : Activity() {
    private var desktop: DesktopView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(null)
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        val view = DesktopView(this)
        desktop = view
        setContentView(view)
        // Samsung's PhoneWindow does not have a DecorView-backed insets
        // controller until the content view has been installed. Calling
        // Window.getInsetsController() before this point crashes during HOME
        // activity creation on the physical display.
        window.decorView.windowInsetsController?.let { controller ->
            controller.hide(android.view.WindowInsets.Type.systemBars())
            controller.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        applySettings()
        // Never survive on a display the running shell does not own (for
        // example the phone screen, if HOME was redirected there).
        if (PlasmaShell.current?.onDesktopActivityCreated(this) != true) finish()
    }

    override fun onDestroy() {
        PlasmaShell.current?.onDesktopActivityDestroyed(this)
        super.onDestroy()
    }

    internal fun applySettings() {
        val settings = PlasmaSettings.load(this)
        val showSystem = settings.wallpaper == "system"
        if (showSystem) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.BLACK))
        }
        desktop?.drawWallpaper = !showSystem
        desktop?.invalidate()
    }

    @Deprecated("Deprecated in Java")
    @SuppressLint("MissingSuperCall")
    override fun onBackPressed() = Unit

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        PlasmaShell.current?.onDesktopKey(event) == true || super.onKeyDown(keyCode, event)

    private class DesktopView(context: Context) : View(context) {
        var drawWallpaper = true
        private var wallpaper: Bitmap? = null
        private val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                PlasmaShell.current?.onDesktopClicked()
                return true
            }
            override fun onLongPress(e: MotionEvent) {
                PlasmaShell.current?.showDesktopMenu(e.rawX, e.rawY)
            }
        })

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            wallpaper?.recycle()
            wallpaper = if (w > 0 && h > 0) PlasmaWallpaper.render(w, h) else null
        }

        override fun onDraw(canvas: Canvas) {
            if (drawWallpaper) wallpaper?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN && event.isButtonPressed(MotionEvent.BUTTON_SECONDARY)) {
                PlasmaShell.current?.showDesktopMenu(event.rawX, event.rawY)
                return true
            }
            return detector.onTouchEvent(event)
        }
    }
}

/**
 * An original abstract wallpaper in the spirit of Plasma 6's defaults:
 * deep blue-violet gradient crossed by soft luminous ribbons.
 */
internal object PlasmaWallpaper {
    fun render(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val w = width.toFloat()
        val h = height.toFloat()
        val base = Paint().apply {
            shader = LinearGradient(0f, 0f, w, h, intArrayOf(0xFF0B1026.toInt(), 0xFF1B1F4B.toInt(), 0xFF2A1745.toInt()), floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w, h, base)
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(w * .72f, h * .35f, maxOf(w, h) * .55f, 0x553DAEE9, 0x003DAEE9, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w, h, glow)
        val ribbons = listOf(
            Triple(0x663DAEE9, .62f, .10f),
            Triple(0x559B59B6, .70f, .16f),
            Triple(0x4400D3B8, .78f, .07f),
            Triple(0x33E93A9A, .54f, .05f),
        )
        ribbons.forEachIndexed { index, (color, anchor, thickness) ->
            val path = Path()
            val y0 = h * anchor
            path.moveTo(-w * .05f, y0)
            path.cubicTo(w * .25f, y0 - h * (.28f + index * .04f), w * .55f, y0 + h * .22f, w * 1.05f, y0 - h * (.18f - index * .05f))
            path.lineTo(w * 1.05f, y0 - h * (.18f - index * .05f) + h * thickness)
            path.cubicTo(w * .55f, y0 + h * (.22f + thickness), w * .25f, y0 - h * (.28f + index * .04f) + h * thickness, -w * .05f, y0 + h * thickness)
            path.close()
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, y0 - h * .3f, w, y0 + h * .2f, intArrayOf(color and 0x00FFFFFF, color, color and 0x00FFFFFF), null, Shader.TileMode.CLAMP)
            }
            canvas.drawPath(path, paint)
        }
        val vignette = Paint().apply {
            shader = RadialGradient(w / 2f, h / 2f, maxOf(w, h) * .75f, 0x00000000, 0x66000000, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w, h, vignette)
        return bitmap
    }
}
