package moe.n4tsu.dextop

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.FrameLayout
import android.widget.TextView

/** Phone mirror and full-touch controller for the active Car Companion display. */
class CarCompanionPhoneActivity : Activity(), SurfaceHolder.Callback {
    private lateinit var surface: SurfaceView
    private lateinit var controller: AndroidAutoMirrorController
    private var sourceDisplayId = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sourceDisplayId = intent.getIntExtra(EXTRA_DISPLAY_ID, -1)
        if (sourceDisplayId < 0) return finish()
        controller = AndroidAutoMirrorController(this, AndroidAutoMirrorActivity.SOURCE_AUTO).apply {
            selectSourceDisplay(sourceDisplayId)
        }
        surface = SurfaceView(this).apply {
            holder.addCallback(this@CarCompanionPhoneActivity)
            setOnTouchListener { _, event -> controller.dispatchTouch(event, width, height) }
        }
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(surface, FrameLayout.LayoutParams(-1, -1))
            addView(TextView(this@CarCompanionPhoneActivity).apply {
                text = "—"
                textSize = 24f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setBackgroundColor(0xB0202124.toInt())
                contentDescription = "Minimize"
                setOnClickListener { moveTaskToBack(true) }
            }, FrameLayout.LayoutParams(dp(56), dp(44), Gravity.TOP or Gravity.END).apply {
                topMargin = dp(16)
                marginEnd = dp(16)
            })
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val next = intent.getIntExtra(EXTRA_DISPLAY_ID, sourceDisplayId)
        if (next != sourceDisplayId) {
            sourceDisplayId = next
            controller.selectSourceDisplay(next)
            attach("display changed")
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) = attach("phone control created")
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = attach("phone control resized")
    override fun surfaceDestroyed(holder: SurfaceHolder) = controller.detachSurface()
    override fun onDestroy() { controller.stop(); super.onDestroy() }

    private fun attach(reason: String) {
        if (surface.holder.surface.isValid && surface.width > 0 && surface.height > 0) {
            controller.attach(surface, surface.width, surface.height, reason)
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()

    companion object {
        const val EXTRA_DISPLAY_ID = "moe.n4tsu.dextop.extra.CAR_COMPANION_DISPLAY_ID"
    }
}
