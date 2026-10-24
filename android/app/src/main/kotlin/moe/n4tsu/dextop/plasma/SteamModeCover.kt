package moe.n4tsu.dextop.plasma

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.max

internal data class SteamModeProgress(
    val step: String, val percent: Int, val ready: Boolean, val exit: Int?, val tail: String,
    val stages: List<String> = emptyList(),
) {
    companion object {
        private val UPDATE = Regex("""Downloading update \((\d+) of (\d+) KB\)""")
        private val DOWNLOAD = Regex("""downloading Steam: (\S+) \((\d+)/(\d+)\)""")
        private val EXIT = Regex("""^== EXIT (-?\d+)""")
        fun parse(text: String): SteamModeProgress? {
            var step: String? = null
            var percent = -1
            var ready = false
            var exit: Int? = null
            val stages = mutableListOf<String>()
            for (line in text.lineSequence()) {
                when {
                    line.startsWith("== STEP ") -> {
                        val script = line.removePrefix("== STEP ").substringAfter(' ')
                        val download = DOWNLOAD.find(script)
                        step = if (download != null) t("nativeSteamModeStepDownload").format(download.groupValues[1]) else label(script)
                        if (download == null && stages.lastOrNull() != step) stages.add(step)
                        percent = download?.let { it.groupValues[2].toInt() * 100 / max(1, it.groupValues[3].toInt()) } ?: -1
                        ready = false
                        exit = null
                    }
                    line.startsWith("== READY") -> ready = true
                    line.startsWith("== EXIT ") -> exit = EXIT.find(line)?.groupValues?.get(1)?.toIntOrNull()
                    else -> UPDATE.find(line)?.let {
                        percent = (it.groupValues[1].toLong() * 100 / max(1L, it.groupValues[2].toLong())).toInt().coerceIn(0, 100)
                        step = t("nativeSteamModeStepUpdate").format(percent)
                    }
                }
            }
            if (step == null && exit == null && !ready) return null
            return SteamModeProgress(step ?: t("nativeSteamModeStarting"), percent, ready, exit,
                text.trimEnd().split('\n').takeLast(40).joinToString("\n"), stages.takeLast(6))
        }
        private fun label(script: String): String = when {
            script.startsWith("starting the display", true) -> t("nativeSteamModeStepDisplay")
            script.startsWith("checking the steam client", true) -> t("nativeSteamModeStepClient")
            script.startsWith("repairing the steam client", true) -> t("nativeSteamModeStepRepair")
            script.startsWith("starting system services", true) -> t("nativeSteamModeStepServices")
            script.startsWith("starting the steam client", true) -> t("nativeSteamModeStepSteam")
            else -> script
        }
    }
}

/** Startup shows human-readable stages; raw diagnostics are reserved for a failed session. */
@SuppressLint("ViewConstructor")
internal class SteamModeCover(
    context: Context, icon: Drawable?,
    private val onRestart: () -> Unit, private val onClose: () -> Unit,
) : FrameLayout(context) {
    private val density = resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).toInt()
    private fun text(size: Float, color: Int) = TextView(context).apply {
        textSize = size; setTextColor(color); gravity = Gravity.CENTER
    }
    private val title = text(22f, WHITE).apply { text = t("nativeSteamModeTitle"); setTypeface(typeface, Typeface.BOLD) }
    private val step = text(15f, SOFT).apply { text = t("nativeSteamModeStarting") }
    private val bar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
        max = 100; isIndeterminate = true
        progressTintList = ColorStateList.valueOf(0xFF1A9FFF.toInt())
        indeterminateTintList = progressTintList
    }
    private val detail = text(11f, SOFT).apply { typeface = Typeface.MONOSPACE; gravity = Gravity.START; setTextIsSelectable(true) }
    private val detailFrame = ScrollView(context).apply {
        visibility = GONE
        setPadding(dp(14), dp(10), dp(14), dp(10))
        addView(detail)
    }
    private fun button(label: String, action: () -> Unit) = text(14f, WHITE).apply {
        text = label; setPadding(dp(20), dp(12), dp(20), dp(12))
        background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(0xFF234560.toInt()) }
        isFocusable = true; setOnClickListener { action() }
    }
    private val buttons = LinearLayout(context).apply {
        visibility = GONE; gravity = Gravity.CENTER
        addView(button(t("nativeSteamModeRestart")) { onRestart() })
        addView(button(t("nativeSteamModeClose")) { onClose() }, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(12) })
    }
    var showing = true
        private set
    var ended = false
        private set
    init {
        isClickable = true
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFF0B1118.toInt(), 0xFF16202D.toInt(), 0xFF1B2838.toInt()))
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(24), dp(32), dp(24))
        }
        val emblem = FrameLayout(context).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF204B70.toInt(), 0xFF172D42.toInt())).apply {
                cornerRadius = dp(32).toFloat()
                setStroke(dp(1), 0xFF365B78.toInt())
            }
            addView(ImageView(context).apply {
                setImageDrawable(icon ?: GlyphDrawable(Glyph.WINDOW, WHITE))
                scaleType = ImageView.ScaleType.FIT_CENTER
            }, LayoutParams(dp(64), dp(64), Gravity.CENTER))
        }
        column.addView(emblem, LinearLayout.LayoutParams(dp(112), dp(112)))
        fun row(view: View, width: Int = -1, height: Int = -2, margin: Int = 10) {
            column.addView(view, LinearLayout.LayoutParams(width, height).apply { topMargin = dp(margin) })
        }
        title.letterSpacing = .035f
        row(title, margin = 24)
        row(step, margin = 10)
        row(bar, dp(220), dp(3), 24)
        row(detailFrame, height = dp(160), margin = 20)
        row(buttons, margin = 20)
        addView(ScrollView(context).apply {
            isFillViewport = true
            clipToPadding = false
            addView(column, FrameLayout.LayoutParams(-1, -2))
        }, LayoutParams(-1, -1))
        addView(button("×") { onClose() }.apply {
            contentDescription = t("nativeSteamModeClose")
            background = null
            textSize = 24f
        }, LayoutParams(dp(56), dp(56), Gravity.TOP or Gravity.END).apply {
            topMargin = dp(8); rightMargin = dp(8)
        })
    }

    fun update(progress: SteamModeProgress) {
        if (ended || !showing) return
        step.text = progress.step
        bar.isIndeterminate = progress.percent < 0
        if (progress.percent >= 0) bar.progress = progress.percent
        detailFrame.visibility = GONE
    }
    fun restart() {
        ended = false
        step.text = t("nativeSteamModeStarting"); bar.isIndeterminate = true
        bar.visibility = VISIBLE
        detail.text = ""; detailFrame.visibility = GONE; buttons.visibility = GONE; show()
    }
    fun stopping() { if (!ended) { show(); step.text = t("nativeSteamModeStepStopping"); bar.isIndeterminate = true } }
    fun fail(message: String, log: String) {
        ended = true; show(); step.text = message
        bar.visibility = GONE
        detail.text = log.ifBlank { message }; detailFrame.visibility = VISIBLE; buttons.visibility = VISIBLE
        detailFrame.post { detailFrame.fullScroll(FOCUS_DOWN) }
    }
    fun show() {
        if (showing && visibility == VISIBLE && alpha == 1f) return
        showing = true; animate().cancel(); visibility = VISIBLE
        animate().alpha(1f).setDuration(PlasmaTheme.SHORT_MS).setInterpolator(PlasmaTheme.outCubic).start()
    }
    fun dismiss() {
        if (!showing) return
        showing = false; animate().cancel()
        animate().alpha(0f).setDuration(120L).setInterpolator(PlasmaTheme.outCubic)
            .withEndAction { if (!showing) visibility = GONE }.start()
    }
    private companion object {
        const val WHITE = 0xFFFFFFFF.toInt()
        const val SOFT = 0xFFC7D5E0.toInt()
        const val DIM = 0xFF8F98A0.toInt()
    }
}
