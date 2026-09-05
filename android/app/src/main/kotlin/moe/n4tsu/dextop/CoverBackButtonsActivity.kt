package moe.n4tsu.dextop

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import moe.n4tsu.dextop.input.PrivilegedInputProtocol

/** Full-screen four-button controller shown on a foldable's physical cover. */
class CoverBackButtonsActivity : Activity() {
    companion object {
        @Volatile
        private var instance: CoverBackButtonsActivity? = null

        fun finishActive() {
            instance?.let { activity ->
                activity.runOnUiThread {
                    if (!activity.isFinishing) activity.finish()
                }
            }
        }
    }

    private val pressedCodes = linkedSetOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this
        window.setStatusBarColor(Color.BLACK)
        window.setNavigationBarColor(Color.BLACK)
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(18), dp(18), dp(18))
            setBackgroundColor(Color.rgb(18, 18, 22))
        }
        root.addView(TextView(this).apply {
            text = NativeStrings.text("nativeBackButtons")
            gravity = Gravity.CENTER
            textSize = 16f
            letterSpacing = .08f
            setTextColor(Color.rgb(226, 220, 232))
        }, LinearLayout.LayoutParams(-1, dp(44)))

        val grid = GridLayout(this).apply {
            columnCount = 2
            rowCount = 2
            alignmentMode = GridLayout.ALIGN_BOUNDS
            useDefaultMargins = false
        }
        addButton(grid, "L", PrivilegedInputProtocol.GAMEPAD_BUTTON_L, 0, 0)
        addButton(grid, "R", PrivilegedInputProtocol.GAMEPAD_BUTTON_R, 1, 0)
        addButton(grid, "L2", PrivilegedInputProtocol.GAMEPAD_BUTTON_L2, 0, 1)
        addButton(grid, "R2", PrivilegedInputProtocol.GAMEPAD_BUTTON_R2, 1, 1)
        root.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        MirrorService.coverBackButtonsActivityCreated()
    }

    override fun onPause() {
        releaseButtons()
        super.onPause()
    }

    override fun onDestroy() {
        releaseButtons()
        if (instance === this) instance = null
        MirrorService.coverBackButtonsActivityDestroyed()
        super.onDestroy()
    }

    private fun addButton(
        parent: GridLayout,
        label: String,
        code: Int,
        column: Int,
        row: Int,
    ) {
        val button = TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            textSize = if (label.length == 1) 30f else 22f
            setTextColor(Color.rgb(239, 234, 245))
            background = buttonBackground(false)
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN,
                    MotionEvent.ACTION_POINTER_DOWN -> {
                        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                            pressedCodes.add(code)
                            MirrorService.injectCoverBackButton(code, true)
                            view.background = buttonBackground(true)
                        }
                    }
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> {
                        if (pressedCodes.remove(code)) {
                            MirrorService.injectCoverBackButton(code, false)
                            view.background = buttonBackground(false)
                        }
                    }
                }
                true
            }
        }
        parent.addView(button, GridLayout.LayoutParams().apply {
            width = 0
            height = 0
            columnSpec = GridLayout.spec(column, 1, 1f)
            rowSpec = GridLayout.spec(row, 1, 1f)
            setMargins(dp(7), dp(7), dp(7), dp(7))
        })
    }

    private fun releaseButtons() {
        pressedCodes.toList().forEach { code ->
            MirrorService.injectCoverBackButton(code, false)
        }
        pressedCodes.clear()
    }

    private fun buttonBackground(selected: Boolean): GradientDrawable =
        GradientDrawable().apply {
            setColor(if (selected) Color.rgb(91, 71, 120) else Color.rgb(36, 34, 43))
            setStroke(dp(2), Color.rgb(114, 99, 135))
            cornerRadius = dp(20).toFloat()
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
