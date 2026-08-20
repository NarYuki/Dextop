package moe.n4tsu.dextop

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.content.DialogInterface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.mediarouter.app.MediaRouteChooserDialogFragment
import androidx.mediarouter.app.MediaRouteButton
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext

/**
 * Hosts the Google Cast route chooser outside the accessibility overlay.
 * This deliberately uses CAF discovery rather than Android's Miracast settings.
 */
class DextopCastRouteActivity : AppCompatActivity() {
    private lateinit var routeButton: MediaRouteButton

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

        routeButton = MediaRouteButton(this).apply {
            contentDescription = NativeStrings.text("nativeCast")
        }
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            addView(
                routeButton,
                FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER)
            )
        }
        setContentView(root, ViewGroup.LayoutParams(-1, -1))

        // CastContext owns discovery, reconnection, and the Cast-only route filter.
        CastContext.getSharedInstance(this)
        CastButtonFactory.setUpMediaRouteButton(applicationContext, routeButton)
        routeButton.post { showRouteChooser() }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        routeButton.post { showRouteChooser() }
    }

    private fun showRouteChooser() {
        if (supportFragmentManager.findFragmentByTag(ROUTE_CHOOSER_TAG) != null) return
        ClosingRouteChooserDialogFragment().apply {
            routeSelector = routeButton.routeSelector
        }.show(supportFragmentManager, ROUTE_CHOOSER_TAG)
    }

    override fun onDestroy() {
        super.onDestroy()
        onClosed?.invoke()
        onClosed = null
    }

    class ClosingRouteChooserDialogFragment : MediaRouteChooserDialogFragment() {
        override fun onDismiss(dialog: DialogInterface) {
            super.onDismiss(dialog)
            activity?.finish()
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val ROUTE_CHOOSER_TAG = "dextop_google_cast_routes"
        private var onClosed: (() -> Unit)? = null

        fun setOnClosed(callback: () -> Unit) {
            onClosed = callback
        }
    }
}
