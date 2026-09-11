package moe.n4tsu.dextop

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.TextView

/** Phone-side uinput trackpad and IME host for a driving Car Companion session. */
class CarCompanionPhoneActivity : Activity() {
    private lateinit var trackpad: View
    private var sourceDisplayId = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sourceDisplayId = intent.getIntExtra(EXTRA_DISPLAY_ID, -1)
        if (sourceDisplayId < 0) return finish()
        trackpad = FrameLayout(this).apply {
            isClickable = true
            isFocusable = true
            background = GradientDrawable().apply {
                setColor(0xFF17181B.toInt())
                setStroke(dp(1), 0xFF55575E.toInt())
                cornerRadius = dp(22).toFloat()
            }
            setOnTouchListener { view, event ->
                CardexRelayService.dispatchPhoneTrackpadEvent(event, view.width, view.height)
                true
            }
        }
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(trackpad, FrameLayout.LayoutParams(-1, -1).apply {
                leftMargin = dp(12)
                rightMargin = dp(12)
                topMargin = dp(12)
                bottomMargin = dp(76)
            })
            addView(control("⌨", "Keyboard") { showPhoneIme() },
                FrameLayout.LayoutParams(dp(56), dp(56), Gravity.BOTTOM or Gravity.START).apply {
                    leftMargin = dp(16)
                    bottomMargin = dp(10)
                })
            addView(control("—", "Minimize") { moveTaskToBack(true) },
                FrameLayout.LayoutParams(dp(56), dp(56), Gravity.BOTTOM or Gravity.END).apply {
                    rightMargin = dp(16)
                    bottomMargin = dp(10)
                })
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val next = intent.getIntExtra(EXTRA_DISPLAY_ID, sourceDisplayId)
        sourceDisplayId = next
    }

    override fun onDestroy() {
        CardexRelayService.resetPhoneTrackpad()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun showPhoneIme() {
        val input = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        input.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()

    private fun control(icon: String, description: String, action: () -> Unit) = TextView(this).apply {
        text = icon
        contentDescription = description
        textSize = 25f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xFF35373D.toInt())
            setStroke(dp(1), 0xFF60636B.toInt())
        }
        elevation = dp(5).toFloat()
        setOnClickListener { action() }
    }

    companion object {
        const val EXTRA_DISPLAY_ID = "moe.n4tsu.dextop.extra.CAR_COMPANION_DISPLAY_ID"
    }
}
