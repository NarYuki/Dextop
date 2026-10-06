package moe.n4tsu.dextop

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** Phone-side uinput trackpad, mirror and IME host for a driving session. */
class CarCompanionPhoneActivity : Activity(), SurfaceHolder.Callback {
    private lateinit var trackpad: View
    private lateinit var mirror: SurfaceView
    private lateinit var modeButton: TextView
    private lateinit var content: FrameLayout
    private lateinit var mirrorController: AndroidAutoMirrorController
    private var sourceDisplayId = -1
    private var mirrorVisible = false
    private val mirrorHandler = Handler(Looper.getMainLooper())
    private var mirrorAttachGeneration = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sourceDisplayId = MirrorService.activeDisplayId().takeIf { it >= 0 }
            ?: intent.getIntExtra(EXTRA_DISPLAY_ID, -1)
        if (sourceDisplayId < 0) return finish()

        mirrorController = AndroidAutoMirrorController(this, AndroidAutoMirrorActivity.SOURCE_AUTO).apply {
            val geometry = currentSourceGeometry(intent)
            this@CarCompanionPhoneActivity.sourceDisplayId = geometry[0]
            selectSourceDisplay(geometry[0], geometry[1], geometry[2], geometry[3])
        }
        trackpad = FrameLayout(this).apply {
            isClickable = true
            isFocusable = true
            background = panelBackground()
            setOnTouchListener { view, event ->
                MirrorService.dispatchCarCompanionPhoneTrackpad(view, event)
                true
            }
        }
        mirror = SurfaceView(this).apply {
            visibility = View.GONE
            // SurfaceView buffers normally live behind the Activity's View
            // hierarchy. The opaque phone controller window therefore hid a
            // correctly attached mirror behind the trackpad panel.
            setZOrderOnTop(true)
            holder.setFormat(PixelFormat.OPAQUE)
            holder.addCallback(this@CarCompanionPhoneActivity)
            setOnTouchListener { view, event ->
                MirrorService.dispatchCarCompanionPhoneMirror(view, event)
                true
            }
        }
        content = FrameLayout(this).apply {
            setPadding(dp(12), dp(12), dp(12), dp(8))
            addView(trackpad, FrameLayout.LayoutParams(-1, -1))
            addView(mirror, FrameLayout.LayoutParams(-1, -1))
        }
        modeButton = TextView(this).apply {
            text = "スマホにミラーリング"
            contentDescription = "Mirror the navigation display on this phone"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(0xFF292B30.toInt())
                setStroke(dp(1), 0xFF60636B.toInt())
                cornerRadius = dp(14).toFloat()
            }
            setOnClickListener { setMirrorVisible(!mirrorVisible) }
        }
        val bottomControls = FrameLayout(this).apply {
            addView(control("⌨", "Keyboard") { showPhoneIme() },
                FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER_VERTICAL or Gravity.START).apply {
                    leftMargin = dp(16)
                })
            addView(control("—", "Minimize") { moveTaskToBack(true) },
                FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER_VERTICAL or Gravity.END).apply {
                    rightMargin = dp(16)
                })
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(modeButton, LinearLayout.LayoutParams(-1, dp(48)).apply {
                marginStart = dp(16)
                marginEnd = dp(16)
                bottomMargin = dp(4)
            })
            addView(bottomControls, LinearLayout.LayoutParams(-1, dp(68)))
        })
        MirrorService.attachCarCompanionPhoneTrackpad(trackpad, nativeTouchpad = true)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val next = intent.getIntExtra(EXTRA_DISPLAY_ID, sourceDisplayId)
        val width = intent.getIntExtra(EXTRA_DISPLAY_WIDTH, 1)
        val height = intent.getIntExtra(EXTRA_DISPLAY_HEIGHT, 1)
        val density = intent.getIntExtra(EXTRA_DISPLAY_DENSITY, 160)
        if (next != sourceDisplayId) {
            sourceDisplayId = next
        }
        mirrorController.selectSourceDisplay(next, width, height, density)
        if (mirrorVisible) attachMirror("source display changed")
    }

    private fun setMirrorVisible(visible: Boolean) {
        if (mirrorVisible == visible) return
        val previousInput = if (mirrorVisible) mirror else trackpad
        MirrorService.detachCarCompanionPhoneTrackpad(previousInput)
        mirrorVisible = visible
        content.setPadding(
            if (visible) 0 else dp(12),
            if (visible) 0 else dp(12),
            if (visible) 0 else dp(12),
            if (visible) 0 else dp(8),
        )
        trackpad.visibility = if (visible) View.GONE else View.VISIBLE
        mirror.visibility = if (visible) View.VISIBLE else View.GONE
        if (visible) mirror.bringToFront()
        modeButton.text = if (visible) "トラックパッドに戻る" else "スマホにミラーリング"
        val nextInput = if (visible) mirror else trackpad
        MirrorService.attachCarCompanionPhoneTrackpad(
            nextInput,
            nativeTouchpad = !visible,
        )
        if (visible) {
            syncCurrentSource()
            attachMirrorWithPrivilegeRecovery("phone mirror enabled")
        } else {
            mirrorAttachGeneration += 1
            mirrorController.detachSurface()
        }
    }

    private fun attachMirrorWithPrivilegeRecovery(reason: String) {
        val generation = ++mirrorAttachGeneration
        if (mirrorController.privilegedAccessAvailable()) {
            attachMirror(reason)
            return
        }
        modeButton.text = "ミラーリングに接続中…"
        sendBroadcast(
            Intent("roro.stellar.intent.action.REQUEST_BINDER")
                .setPackage("roro.stellar.manager")
        )
        fun retry(attempt: Int) {
            if (!mirrorVisible || generation != mirrorAttachGeneration) return
            if (mirrorController.privilegedAccessAvailable()) {
                modeButton.text = "トラックパッドに戻る"
                syncCurrentSource()
                attachMirror("$reason after privilege recovery")
                return
            }
            if (attempt < 30) {
                if (attempt > 0 && attempt % 10 == 0) {
                    sendBroadcast(
                        Intent("roro.stellar.intent.action.REQUEST_BINDER")
                            .setPackage("roro.stellar.manager")
                    )
                }
                mirrorHandler.postDelayed({ retry(attempt + 1) }, 100L)
            } else {
                modeButton.text = "ミラーリングを再試行"
            }
        }
        retry(0)
    }

    private fun currentSourceGeometry(sourceIntent: Intent = intent): IntArray =
        MirrorService.activeDisplayGeometry() ?: intArrayOf(
            sourceIntent.getIntExtra(EXTRA_DISPLAY_ID, sourceDisplayId),
            sourceIntent.getIntExtra(EXTRA_DISPLAY_WIDTH, 1),
            sourceIntent.getIntExtra(EXTRA_DISPLAY_HEIGHT, 1),
            sourceIntent.getIntExtra(EXTRA_DISPLAY_DENSITY, 160),
        )

    private fun syncCurrentSource() {
        val geometry = currentSourceGeometry()
        if (geometry[0] < 0) return
        sourceDisplayId = geometry[0]
        mirrorController.selectSourceDisplay(geometry[0], geometry[1], geometry[2], geometry[3])
    }

    private fun attachMirror(reason: String) {
        if (!mirrorVisible || !mirror.holder.surface.isValid || mirror.width <= 0 || mirror.height <= 0) return
        mirrorController.attach(mirror, mirror.width, mirror.height, reason)
    }

    override fun surfaceCreated(holder: SurfaceHolder) = attachMirror("phone mirror created")

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) =
        attachMirror("phone mirror resized")

    override fun surfaceDestroyed(holder: SurfaceHolder) = mirrorController.detachSurface()

    override fun onDestroy() {
        mirrorAttachGeneration += 1
        mirrorHandler.removeCallbacksAndMessages(null)
        MirrorService.detachCarCompanionPhoneTrackpad(if (mirrorVisible) mirror else trackpad)
        mirrorController.stop()
        CardexRelayService.resetPhoneTrackpad()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun showPhoneIme() {
        val input = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        input.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
    }

    private fun panelBackground() = GradientDrawable().apply {
        setColor(0xFF17181B.toInt())
        setStroke(dp(1), 0xFF55575E.toInt())
        cornerRadius = dp(22).toFloat()
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
        const val EXTRA_DISPLAY_WIDTH = "moe.n4tsu.dextop.extra.CAR_COMPANION_DISPLAY_WIDTH"
        const val EXTRA_DISPLAY_HEIGHT = "moe.n4tsu.dextop.extra.CAR_COMPANION_DISPLAY_HEIGHT"
        const val EXTRA_DISPLAY_DENSITY = "moe.n4tsu.dextop.extra.CAR_COMPANION_DISPLAY_DENSITY"
    }
}
