package moe.n4tsu.dextop

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView

/** Touch surface that feeds the shared uinput touchpad from a foldable cover display. */
class CoverTrackpadActivity : Activity() {
    companion object {
        @Volatile private var instance: CoverTrackpadActivity? = null

        fun finishActive() {
            instance?.let { activity ->
                activity.runOnUiThread { if (!activity.isFinishing) activity.finish() }
            }
        }
    }

    private lateinit var trackpadSurface: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )
        trackpadSurface = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(18, 18, 22))
            addView(TextView(this@CoverTrackpadActivity).apply {
                text = NativeStrings.text("nativeInputTrackpad")
                gravity = Gravity.CENTER
                textSize = 16f
                letterSpacing = .12f
                setTextColor(Color.rgb(180, 174, 190))
            }, FrameLayout.LayoutParams(-1, -1))
            setOnTouchListener { view, event ->
                MirrorService.dispatchCoverTrackpadEvent(view, event)
                true
            }
        }
        setContentView(trackpadSurface)
        trackpadSurface.post {
            if (!isFinishing) MirrorService.coverTrackpadActivityCreated(trackpadSurface)
        }
    }

    override fun onDestroy() {
        if (::trackpadSurface.isInitialized) {
            MirrorService.coverTrackpadActivityDestroyed(trackpadSurface)
        }
        if (instance === this) instance = null
        super.onDestroy()
    }
}
