package moe.n4tsu.dextop

import android.media.AudioManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.ColorDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import kotlin.math.max

/** What [ControlCenter] needs from the session beyond the shared interfaces. */
internal interface ControlCenterHost : DesktopSession, OverlayMenu {
    val rootWindowParams: WindowManager.LayoutParams?
}

/** Volume and brightness sliders of the overlay menu. */
internal class ControlCenter(
    private val context: Context,
    private val desk: ControlCenterHost,
) {
    fun sliderRow(
        label: String,
        value: Int,
        maximum: Int,
        finished: (() -> Unit)? = null,
        changed: (Int) -> Unit,
    ): View =
        FrameLayout(context).apply {
            val levelIcon = LevelIconView(context, label == NativeStrings.text("nativeVolume")).apply {
                level = value.toFloat() / maximum.coerceAtLeast(1)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            addView(SeekBar(context).apply {
                max = maximum
                progress = value.coerceIn(0, maximum)
                splitTrack = false
                setPadding(0, 0, 0, 0)
                progressDrawable = controlCenterTrack()
                thumb = ColorDrawable(Color.TRANSPARENT)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(view: SeekBar?, progress: Int, fromUser: Boolean) {
                        levelIcon.level = progress.toFloat() / maximum.coerceAtLeast(1)
                        if (fromUser) changed(progress)
                    }

                    override fun onStartTrackingTouch(view: SeekBar?) = Unit
                    override fun onStopTrackingTouch(view: SeekBar?) { finished?.invoke() }
                })
            }, FrameLayout.LayoutParams(-1, desk.dp(54), Gravity.CENTER_VERTICAL))
            addView(
                levelIcon,
                FrameLayout.LayoutParams(desk.dp(40), desk.dp(32), Gravity.START or Gravity.CENTER_VERTICAL).apply {
                    leftMargin = desk.dp(10)
                })
            contentDescription = label
        }.also { it.layoutParams = LinearLayout.LayoutParams(-1, desk.dp(54)).apply { bottomMargin = desk.dp(8) } }

    private fun controlCenterTrack(): LayerDrawable {
        val background = GradientDrawable().apply {
            setColor(Color.rgb(92, 90, 97))
            cornerRadius = desk.dp(16).toFloat()
        }
        val progress = ClipDrawable(GradientDrawable().apply {
            setColor(Color.rgb(242, 240, 244))
            cornerRadius = desk.dp(16).toFloat()
        }, Gravity.START, ClipDrawable.HORIZONTAL)
        return LayerDrawable(arrayOf(background, progress)).apply {
            setId(0, android.R.id.background)
            setId(1, android.R.id.progress)
            setLayerHeight(0, desk.dp(54))
            setLayerHeight(1, desk.dp(54))
            setLayerGravity(0, Gravity.CENTER_VERTICAL)
            setLayerGravity(1, Gravity.CENTER_VERTICAL)
        }
    }

    fun systemVolume(): Int {
        val audio = context.getSystemService(AudioManager::class.java)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max
    }

    fun setSystemVolume(percent: Int) {
        val audio = context.getSystemService(AudioManager::class.java)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, max * percent / 100, 0)
    }

    fun currentBrightness(): Int {
        val explicit = desk.rootWindowParams?.screenBrightness ?: -1f
        if (explicit >= 0f) return (explicit * 100).toInt()
        return runCatching { Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) * 100 / 255 }
            .getOrDefault(50)
    }

    fun setOverlayBrightness(percent: Int) {
        val params = desk.rootWindowParams ?: return
        params.screenBrightness = percent.coerceIn(1, 100) / 100f
        desk.root?.let { desk.windowManager?.updateViewLayout(it, params) }
    }

    /**
     * OverlayDisplayAdapter treats the global display specification as a
     * request, so a stale vendor callback can leave the last overlay alive
     * after the first clear.  Always issue the empty/None request twice at
     * teardown, even when the setting is already empty.  Each pass is
     * independent so a transient failure cannot skip the second write.
     */
}
