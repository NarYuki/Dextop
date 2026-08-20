package moe.n4tsu.dextop

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext

/**
 * Hosts the Google Cast route chooser outside the accessibility overlay.
 * This deliberately uses CAF discovery rather than Android's Miracast settings.
 */
class DextopCastRouteActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        if (BuildConfig.CAST_RECEIVER_APP_ID.isBlank()) {
            setContentView(TextView(this).apply {
                text = "Google Cast Receiver App ID is not configured"
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.rgb(28, 27, 31))
                setPadding(dp(24), dp(24), dp(24), dp(24))
                setOnClickListener { finish() }
            })
            return
        }

        val button = MediaRouteButton(this).apply {
            contentDescription = NativeStrings.text("nativeCast")
        }
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            addView(
                button,
                FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER)
            )
        }
        setContentView(root, ViewGroup.LayoutParams(-1, -1))

        // CastContext owns discovery, reconnection, and the Cast-only route filter.
        CastContext.getSharedInstance(this)
        CastButtonFactory.setUpMediaRouteButton(applicationContext, button)
        button.post { button.performClick() }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
