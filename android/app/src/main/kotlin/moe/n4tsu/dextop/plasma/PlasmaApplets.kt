package moe.n4tsu.dextop.plasma

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale

internal fun percentText(value: Float): String =
    java.text.NumberFormat.getPercentInstance().format(value.coerceIn(0f, 1f).toDouble())

/** Localised "September 2026" / "2026年9月" title. */
internal fun monthTitle(calendar: Calendar): String {
    val pattern = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMMMyyyy")
    return java.text.SimpleDateFormat(pattern, Locale.getDefault()).format(calendar.time)
}

/** Header used by every system-tray popup ("Audio Volume", "Networks", …). */
internal fun appletHeader(context: Context, palette: PlasmaPalette, title: String): View =
    plasmaLabel(context, title, palette.text, 15f, bold = true).apply {
        val density = context.resources.displayMetrics.density
        setPadding((8 * density).toInt(), (6 * density).toInt(), (8 * density).toInt(), (10 * density).toInt())
    }

internal class VolumeApplet(
    context: Context,
    palette: PlasmaPalette,
    volume: Float,
    muted: Boolean,
    onVolume: (Float) -> Unit,
    onToggleMute: () -> Unit,
    onOpenSettings: () -> Unit,
) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        val density = resources.displayMetrics.density
        setPadding((10 * density).toInt(), (8 * density).toInt(), (10 * density).toInt(), (10 * density).toInt())
        addView(appletHeader(context, palette, t("nativePlasmaAudioVolume")))
        val row = horizontalLayout(context)
        row.addView(
            PlasmaButton(context, palette, if (muted) Glyph.VOLUME_MUTED else Glyph.VOLUME, "") { onToggleMute() },
            LayoutParams((34 * density).toInt(), (34 * density).toInt()),
        )
        val percent = plasmaLabel(context, percentText(volume), palette.text, 13f)
        row.addView(LinearLayout(context).apply {
            orientation = VERTICAL
            addView(plasmaLabel(context, t("nativePlasmaDeviceSpeaker"), palette.text, 13f).apply {
                setPadding((10 * density).toInt(), 0, 0, 0)
            })
            addView(PlasmaSlider(context, palette, volume) { value, _ ->
                percent.text = percentText(value)
                onVolume(value)
            })
        }, LayoutParams(0, -2, 1f))
        row.addView(percent, LayoutParams((44 * density).toInt(), -2))
        addView(row, LayoutParams(-1, -2))
        addView(PlasmaButton(context, palette, Glyph.SETTINGS, t("nativePlasmaConfigureAudio"), flat = true) { onOpenSettings() },
            LayoutParams(-2, (32 * density).toInt()).apply { topMargin = (10 * density).toInt(); gravity = Gravity.END })
    }
}

internal class InfoApplet(
    context: Context,
    palette: PlasmaPalette,
    title: String,
    glyph: Glyph,
    headline: String,
    detail: String,
    level: Float? = null,
    actionLabel: String,
    onAction: () -> Unit,
) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        val density = resources.displayMetrics.density
        setPadding((10 * density).toInt(), (8 * density).toInt(), (10 * density).toInt(), (10 * density).toInt())
        addView(appletHeader(context, palette, title))
        val row = horizontalLayout(context)
        row.addView(View(context).apply {
            background = GlyphDrawable(glyph, palette.text, 1.4f).also { if (level != null) it.level01 = level }
        }, LayoutParams((40 * density).toInt(), (40 * density).toInt()))
        row.addView(LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding((12 * density).toInt(), 0, 0, 0)
            addView(plasmaLabel(context, headline, palette.text, 14f, bold = true))
            addView(plasmaLabel(context, detail, palette.inactiveText, 12f).apply { isSingleLine = false })
        }, LayoutParams(0, -2, 1f))
        addView(row, LayoutParams(-1, -2))
        if (level != null) {
            addView(ProgressBarView(context, palette, level), LayoutParams(-1, (8 * density).toInt()).apply {
                topMargin = (12 * density).toInt()
            })
        }
        addView(PlasmaButton(context, palette, Glyph.SETTINGS, actionLabel, flat = true) { onAction() },
            LayoutParams(-2, (32 * density).toInt()).apply { topMargin = (10 * density).toInt(); gravity = Gravity.END })
    }
}

@SuppressLint("ViewConstructor")
private class ProgressBarView(context: Context, private val palette: PlasmaPalette, private val value: Float) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        val radius = height / 2f
        paint.color = PlasmaTheme.withAlpha(palette.text, 0x2A)
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, paint)
        paint.color = if (value < .15f) palette.negative else palette.accent
        canvas.drawRoundRect(0f, 0f, width * value.coerceIn(0f, 1f), height.toFloat(), radius, radius, paint)
    }
}

/** "Status and Notifications" overflow: hidden tray entries rendered as a list. */
internal class TrayOverflowApplet(
    context: Context,
    palette: PlasmaPalette,
    items: List<MenuItem>,
    dismiss: () -> Unit,
) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        val density = resources.displayMetrics.density
        setPadding((8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt())
        addView(appletHeader(context, palette, t("nativePlasmaStatusAndNotifications")))
        items.forEach { item ->
            addView(PlasmaRow(context, palette, item.glyph?.let { GlyphDrawable(it, palette.text, 1.5f) }, item.text,
                heightDp = 36f, iconDp = 18f, checked = item.checked) {
                dismiss(); item.action()
            }, LayoutParams(-1, -2))
        }
    }
}

/** Digital Clock popup: large date on the left, month view on the right. */
@SuppressLint("ViewConstructor")
internal class CalendarApplet(context: Context, private val palette: PlasmaPalette) : LinearLayout(context) {
    private val density = resources.displayMetrics.density
    private val shown = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }
    private val title = plasmaLabel(context, "", palette.text, 15f, bold = true)
    private val month = MonthView(context, palette, shown)

    init {
        orientation = HORIZONTAL
        setPadding((12 * density).toInt(), (10 * density).toInt(), (12 * density).toInt(), (12 * density).toInt())
        val now = Calendar.getInstance()
        val left = verticalLayout(context)
        left.addView(plasmaLabel(context, now.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault()) ?: "", palette.inactiveText, 14f))
        left.addView(plasmaLabel(context, now.get(Calendar.DAY_OF_MONTH).toString(), palette.text, 54f).apply {
            typeface = PlasmaTheme.sansLight
        })
        left.addView(plasmaLabel(context, monthTitle(now), palette.text, 14f))
        addView(left, LayoutParams((150 * density).toInt(), -1))
        addView(View(context).apply { setBackgroundColor(palette.separator) }, LayoutParams(1, -1).apply {
            rightMargin = (12 * density).toInt()
        })
        val right = verticalLayout(context)
        val header = horizontalLayout(context)
        header.addView(title, LayoutParams(0, -2, 1f))
        header.addView(PlasmaButton(context, palette, Glyph.BACK, "") { shift(-1) }, LayoutParams((30 * density).toInt(), (30 * density).toInt()))
        header.addView(PlasmaButton(context, palette, Glyph.HISTORY, "") {
            shown.timeInMillis = System.currentTimeMillis(); shown.set(Calendar.DAY_OF_MONTH, 1); refresh()
        }, LayoutParams((30 * density).toInt(), (30 * density).toInt()))
        header.addView(PlasmaButton(context, palette, Glyph.CHEVRON_RIGHT, "") { shift(1) }, LayoutParams((30 * density).toInt(), (30 * density).toInt()))
        right.addView(header, LayoutParams(-1, -2))
        right.addView(month, LayoutParams(-1, 0, 1f))
        addView(right, LayoutParams(0, -1, 1f))
        refresh()
    }

    private fun shift(months: Int) {
        shown.add(Calendar.MONTH, months)
        month.animate().alpha(0f).translationX(-months * 20 * density).setDuration(90).withEndAction {
            refresh()
            month.translationX = months * 20 * density
            month.animate().alpha(1f).translationX(0f).setDuration(PlasmaTheme.LONG_MS).setInterpolator(PlasmaTheme.outCubic).start()
        }.start()
    }

    private fun refresh() {
        title.text = monthTitle(shown)
        month.invalidate()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_DPAD_LEFT -> { shift(-1); return true }
                KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT -> { shift(1); return true }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}

@SuppressLint("ViewConstructor")
private class MonthView(context: Context, private val palette: PlasmaPalette, private val shown: Calendar) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = PlasmaTheme.sans }
    private val density = resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        val firstDay = Calendar.getInstance().firstDayOfWeek
        val names = DateFormatSymbols.getInstance().shortWeekdays
        val cellWidth = width / 7f
        val cellHeight = height / 7f
        paint.textSize = 11.5f * density
        paint.color = palette.inactiveText
        for (column in 0 until 7) {
            val day = (firstDay - 1 + column) % 7 + 1
            canvas.drawText(names[day].take(2), cellWidth * (column + .5f), cellHeight * .6f, paint)
        }
        val cursor = shown.clone() as Calendar
        val offset = (cursor.get(Calendar.DAY_OF_WEEK) - firstDay + 7) % 7
        cursor.add(Calendar.DAY_OF_MONTH, -offset)
        val today = Calendar.getInstance()
        for (row in 1..6) for (column in 0 until 7) {
            val cx = cellWidth * (column + .5f)
            val cy = cellHeight * (row + .5f)
            val inMonth = cursor.get(Calendar.MONTH) == shown.get(Calendar.MONTH)
            val isToday = cursor.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                cursor.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
            if (isToday) {
                paint.color = palette.accent
                canvas.drawRoundRect(cx - cellWidth * .42f, cy - cellHeight * .42f, cx + cellWidth * .42f, cy + cellHeight * .42f, 4 * density, 4 * density, paint)
            }
            paint.textSize = 13f * density
            paint.color = when {
                isToday -> palette.accentText
                inMonth -> palette.text
                else -> palette.disabledText
            }
            canvas.drawText(cursor.get(Calendar.DAY_OF_MONTH).toString(), cx, cy - (paint.descent() + paint.ascent()) / 2f, paint)
            cursor.add(Calendar.DAY_OF_MONTH, 1)
        }
    }
}

/**
 * Plasma 6 logout greeter: the screen dims, the session buttons fade in and a
 * countdown runs for the default action.
 */
@SuppressLint("ViewConstructor")
internal class LeaveScreen(
    context: Context,
    private val palette: PlasmaPalette,
    private val defaultAction: LeaveAction,
    private val onChoose: (LeaveAction?) -> Unit,
) : FrameLayout(context) {
    private val density = resources.displayMetrics.density
    private var remaining = 30
    private val countdown = plasmaLabel(context, "", 0xFFFCFCFC.toInt(), 14f)
    private val tick = object : Runnable {
        override fun run() {
            remaining -= 1
            if (remaining <= 0) {
                onChoose(defaultAction)
                return
            }
            updateCountdown()
            postDelayed(this, 1_000L)
        }
    }
    private val buttons = ArrayList<Pair<LeaveAction, PlasmaButton>>()
    private var focused = 0

    init {
        setBackgroundColor(0xB3000000.toInt())
        isFocusable = true
        isFocusableInTouchMode = true
        val column = verticalLayout(context).apply { gravity = Gravity.CENTER_HORIZONTAL }
        val dark = PlasmaTheme.breezeDark.copy(accent = palette.accent, hover = palette.hover, pressed = palette.pressed)
        column.addView(AvatarView(context, dark, android.os.Build.MODEL.firstOrNull()?.uppercaseChar() ?: 'D'),
            LinearLayout.LayoutParams((84 * density).toInt(), (84 * density).toInt()))
        column.addView(plasmaLabel(context, android.os.Build.MODEL, 0xFFFCFCFC.toInt(), 20f).apply {
            setPadding(0, (12 * density).toInt(), 0, (28 * density).toInt())
        })
        val row = horizontalLayout(context)
        listOf(
            LeaveAction.SLEEP to (Glyph.SLEEP to t("nativePlasmaSleep")),
            LeaveAction.RESTART to (Glyph.RESTART to t("nativePlasmaRestart")),
            LeaveAction.SHUTDOWN to (Glyph.POWER to t("nativePlasmaShutDown")),
            LeaveAction.LOGOUT to (Glyph.LOGOUT to t("nativePlasmaLogOut")),
        ).forEach { (action, pair) ->
            val button = PlasmaButton(context, dark, pair.first, pair.second, flat = true, vertical = true) { onChoose(action) }
            buttons += action to button
            row.addView(button, LinearLayout.LayoutParams(-2, -2).apply {
                leftMargin = (8 * density).toInt(); rightMargin = (8 * density).toInt()
            })
        }
        column.addView(row)
        column.addView(countdown.apply { setPadding(0, (26 * density).toInt(), 0, (14 * density).toInt()) })
        column.addView(PlasmaButton(context, dark, null, t("nativeCancel"), flat = false) { onChoose(null) })
        addView(column, LayoutParams(-2, -2, Gravity.CENTER))
        focused = buttons.indexOfFirst { it.first == defaultAction }.coerceAtLeast(0)
        buttons[focused].second.focusedButton = true
        updateCountdown()
    }

    private fun updateCountdown() {
        val label = buttons.firstOrNull { it.first == defaultAction }?.let {
            when (defaultAction) {
                LeaveAction.SHUTDOWN -> t("nativePlasmaShuttingDownIn")
                LeaveAction.RESTART -> t("nativePlasmaRestartingIn")
                LeaveAction.LOGOUT -> t("nativePlasmaLoggingOutIn")
                else -> t("nativePlasmaSleepingIn")
            }
        } ?: ""
        countdown.text = label.format(remaining)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        alpha = 0f
        animate().alpha(1f).setDuration(PlasmaTheme.LONG_MS).start()
        postDelayed(tick, 1_000L)
        requestFocus()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return true
        when (event.keyCode) {
            KeyEvent.KEYCODE_ESCAPE -> onChoose(null)
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_TAB -> {
                buttons[focused].second.focusedButton = false
                val delta = if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1
                focused = (focused + delta + buttons.size) % buttons.size
                buttons[focused].second.focusedButton = true
                removeCallbacks(tick)
            }
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_SPACE -> buttons[focused].second.click()
        }
        return true
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) onChoose(null)
        return true
    }
}

/**
 * A compact "Configure Desktop and Wallpaper" / Quick Settings page with
 * the options that matter for the Dextop window manager.
 */
@SuppressLint("ViewConstructor")
internal class DesktopSettingsApplet(
    context: Context,
    palette: PlasmaPalette,
    settings: PlasmaSettings,
    onChange: (PlasmaSettings) -> Unit,
) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        val density = resources.displayMetrics.density
        setPadding((12 * density).toInt(), (10 * density).toInt(), (12 * density).toInt(), (12 * density).toInt())
        addView(appletHeader(context, palette, t("nativePlasmaConfigureDesktop")))
        var current = settings
        fun section(title: String) = addView(plasmaLabel(context, title, palette.inactiveText, 12f, bold = true).apply {
            setPadding((6 * density).toInt(), (10 * density).toInt(), 0, (4 * density).toInt())
        })
        fun choices(options: List<Pair<String, String>>, selected: String, apply: (String) -> PlasmaSettings) {
            val row = horizontalLayout(context)
            val chips = ArrayList<ChoiceChip>()
            options.forEach { (value, label) ->
                val chip = ChoiceChip(context, palette, label, value == selected) {
                    chips.forEach { c -> c.selectedChip = c.value == value }
                    current = apply(value)
                    onChange(current)
                }
                chip.value = value
                chips += chip
                row.addView(chip, LayoutParams(0, (32 * density).toInt(), 1f).apply { rightMargin = (4 * density).toInt() })
            }
            addView(row, LayoutParams(-1, -2))
        }
        section(t("nativePlasmaColorScheme"))
        choices(listOf("dark" to t("nativePlasmaBreezeDark"), "light" to t("nativePlasmaBreezeLight"), "system" to t("nativePlasmaFollowSystem")), settings.colorScheme) {
            current.copy(colorScheme = it)
        }
        section(t("nativePlasmaAccentColor"))
        val accents = listOf(0xFF3DAEE9, 0xFF9B59B6, 0xFFE93A9A, 0xFFE93D58, 0xFFE9643A, 0xFFEF973C, 0xFFE8CB2D, 0xFFB6E521, 0xFF3DD425, 0xFF00D485, 0xFF00D3B8, 0xFF7F8C8D)
            .map { it.toInt() }
        val accentRow = horizontalLayout(context)
        val swatches = ArrayList<AccentSwatch>()
        accents.forEach { color ->
            val swatch = AccentSwatch(context, palette, color, color == settings.accentColor) {
                swatches.forEach { s -> s.selectedSwatch = s.color == color }
                current = current.copy(accentColor = color)
                onChange(current)
            }
            swatches += swatch
            accentRow.addView(swatch, LayoutParams((28 * density).toInt(), (28 * density).toInt()).apply { rightMargin = (6 * density).toInt() })
        }
        addView(accentRow)
        section(t("nativePlasmaWallpaper"))
        choices(listOf("plasma" to t("nativePlasmaWallpaperPlasma"), "system" to t("nativePlasmaWallpaperAndroid")), settings.wallpaper) {
            current.copy(wallpaper = it)
        }
        section(t("nativePlasmaPanel"))
        choices(listOf("float" to t("nativePlasmaFloating"), "dock" to t("nativePlasmaDocked")), if (settings.floatingPanel) "float" else "dock") {
            current.copy(floatingPanel = it == "float")
        }
        section(t("nativePlasmaVirtualDesktops"))
        choices((1..4).map { it.toString() to it.toString() }, settings.virtualDesktops.coerceAtMost(4).toString()) {
            current.copy(virtualDesktops = it.toInt())
        }
        section(t("nativePlasmaAnimationSpeed"))
        choices(listOf("0" to t("nativePlasmaInstant"), "0.5" to "0.5×", "1" to "1×", "2" to "2×"),
            when { settings.animationSpeed <= 0f -> "0"; settings.animationSpeed < .75f -> "0.5"; settings.animationSpeed > 1.5f -> "2"; else -> "1" }) {
            current.copy(animationSpeed = it.toFloat())
        }
    }
}

@SuppressLint("ViewConstructor")
private class ChoiceChip(
    context: Context,
    private val palette: PlasmaPalette,
    private val label: String,
    initial: Boolean,
    private val onPick: () -> Unit,
) : View(context) {
    var value = ""
    var selectedChip = initial
        set(v) { field = v; invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = PlasmaTheme.sans }
    private val density = resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        val box = RectF(1f, 1f, width - 1f, height - 1f)
        paint.style = Paint.Style.FILL
        paint.color = if (selectedChip) PlasmaTheme.withAlpha(palette.accent, 0x44) else PlasmaTheme.withAlpha(palette.text, 0x10)
        canvas.drawRoundRect(box, 4 * density, 4 * density, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = if (selectedChip) palette.accent else palette.separator
        canvas.drawRoundRect(box, 4 * density, 4 * density, paint)
        paint.style = Paint.Style.FILL
        paint.color = palette.text
        paint.textSize = 12.5f * density
        canvas.drawText(TextUtilsCompat.ellipsize(label, paint, width - 8 * density), width / 2f, height / 2f - (paint.descent() + paint.ascent()) / 2f, paint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) onPick()
        return true
    }
}

@SuppressLint("ViewConstructor")
private class AccentSwatch(
    context: Context,
    private val palette: PlasmaPalette,
    val color: Int,
    initial: Boolean,
    private val onPick: () -> Unit,
) : View(context) {
    var selectedSwatch = initial
        set(v) { field = v; animateScale() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var scale = if (initial) 1f else .8f

    private fun animateScale() {
        ValueAnimator.ofFloat(scale, if (selectedSwatch) 1f else .8f).apply {
            duration = PlasmaTheme.LONG_MS
            interpolator = PlasmaTheme.outCubic
            addUpdateListener { scale = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val radius = width / 2f * scale
        paint.style = Paint.Style.FILL
        paint.color = color
        canvas.drawCircle(width / 2f, height / 2f, radius - 3f, paint)
        if (selectedSwatch) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = palette.text
            canvas.drawCircle(width / 2f, height / 2f, radius - 1f, paint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) onPick()
        return true
    }
}
