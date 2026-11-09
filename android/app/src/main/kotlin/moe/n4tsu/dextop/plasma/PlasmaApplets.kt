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

/** A Linux tray entry is separate from both ordinary windows and notifications. */
internal data class PlasmaLinuxTrayItem(val container: String, val key: String, val title: String,
    val icon: android.graphics.drawable.Drawable?)

/** "Status and Notifications" overflow, including live application tray icons. */
internal class TrayOverflowApplet(
    context: Context,
    private val palette: PlasmaPalette,
    private val items: List<MenuItem>,
    linuxItems: List<PlasmaLinuxTrayItem> = emptyList(),
    private val onLinuxAction: (PlasmaLinuxTrayItem, Int, Float, Float) -> Unit = { _, _, _, _ -> },
    private val dismiss: () -> Unit,
) : LinearLayout(context) {
    private val rows = LinearLayout(context).apply { orientation = VERTICAL }
    init {
        orientation = VERTICAL
        val density = resources.displayMetrics.density
        setPadding((8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt())
        addView(appletHeader(context, palette, t("nativePlasmaStatusAndNotifications")))
        addView(android.widget.ScrollView(context).apply { isFillViewport = false; addView(rows) }, LayoutParams(-1, -2))
        updateLinuxItems(linuxItems)
    }

    @SuppressLint("ClickableViewAccessibility")
    fun updateLinuxItems(linuxItems: List<PlasmaLinuxTrayItem>) {
        rows.removeAllViews()
        linuxItems.forEach { item ->
            lateinit var row: PlasmaRow
            fun invoke(button: Int, x: Float? = null, y: Float? = null) {
                val location = IntArray(2).also { row.getLocationOnScreen(it) }
                val rootX = x ?: (location[0] + row.width / 2f)
                val rootY = y ?: (location[1] + row.height / 2f)
                dismiss(); onLinuxAction(item, button, rootX, rootY)
            }
            row = PlasmaRow(context, palette, item.icon ?: GlyphDrawable(Glyph.APPS, palette.text), item.title,
                heightDp = 40f, iconDp = 24f, onRightClick = { x, y -> invoke(3, x, y) }) { invoke(1) }
            // Consume the entire secondary-button gesture so its release cannot also activate.
            var secondary = false
            row.setOnTouchListener { _, event ->
                when {
                    event.actionMasked == MotionEvent.ACTION_DOWN && event.isButtonPressed(MotionEvent.BUTTON_SECONDARY) -> { secondary = true; invoke(3, event.rawX, event.rawY); true }
                    secondary -> { if (event.actionMasked in listOf(MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL)) secondary = false; true }
                    else -> false
                }
            }
            rows.addView(row, LayoutParams(-1, -2))
        }
        if (linuxItems.isNotEmpty()) rows.addView(View(context).apply { setBackgroundColor(palette.separator) }, LayoutParams(-1, 1).apply { topMargin = 4; bottomMargin = 4 })
        items.forEach { item ->
            rows.addView(PlasmaRow(context, palette, item.glyph?.let { GlyphDrawable(it, palette.text, 1.5f) }, item.text,
                heightDp = 36f, iconDp = 18f, checked = item.checked) { dismiss(); item.action() }, LayoutParams(-1, -2))
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
 * Dextop Plasma's own settings: a window-like dialog with a category list (a row of tabs on
 * narrow screens) and pages of setting rows, each a title, a short explanation and a control.
 */
@SuppressLint("ViewConstructor")
internal class DesktopSettingsApplet(
    context: Context,
    private val palette: PlasmaPalette,
    settings: PlasmaSettings,
    wide: Boolean = true,
    initialPage: Int = 0,
    private val onPage: (Int) -> Unit = {},
    onClose: (() -> Unit)? = null,
    private val onChange: (PlasmaSettings) -> Unit,
) : LinearLayout(context) {
    private val density = resources.displayMetrics.density
    private var current = settings
    private fun dp(value: Float) = (value * density).toInt()
    private fun tr(key: String) =
        moe.n4tsu.dextop.InlineStrings.pick(resources.configuration.locales[0], key)

    private fun change(next: PlasmaSettings) {
        current = next
        onChange(next)
    }

    init {
        orientation = VERTICAL
        // Title bar.
        addView(horizontalLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18f), 0, dp(8f), 0)
            addView(plasmaLabel(context, t("nativePlasmaConfigureDesktop"), palette.text, 15f, bold = true),
                LayoutParams(0, -2, 1f))
            if (onClose != null) addView(SettingsIconButton(context, palette, Glyph.CLOSE, t("nativePlasmaClose"), onClose),
                LayoutParams(dp(36f), dp(36f)))
        }, LayoutParams(-1, dp(52f)))
        addView(View(context).apply { setBackgroundColor(palette.separator) }, LayoutParams(-1, 1))

        val pages = listOf(
            Triple(tr("uiAppearance"), Glyph.WALLPAPER, ::appearancePage),
            Triple(tr("uiTaskbar"), Glyph.DESKTOP, ::taskbarPage),
            Triple(tr("uiWorkspace"), Glyph.OVERVIEW, ::workspacePage),
        )
        val content = FrameLayout(context)
        val scroll = android.widget.ScrollView(context).apply {
            isVerticalScrollBarEnabled = true
            addView(content)
        }
        val items = ArrayList<SettingsNavItem>()
        fun select(index: Int) {
            items.forEachIndexed { i, item -> item.selectedItem = i == index }
            content.removeAllViews()
            content.addView(pages[index].third(), FrameLayout.LayoutParams(-1, -2))
            scroll.scrollTo(0, 0)
            onPage(index)
        }
        val navigation = LinearLayout(context).apply {
            orientation = if (wide) VERTICAL else HORIZONTAL
            setPadding(dp(8f), dp(8f), dp(8f), dp(8f))
        }
        pages.forEachIndexed { index, (title, glyph, _) ->
            val item = SettingsNavItem(context, palette, glyph, title, compact = !wide) { select(index) }
            items += item
            navigation.addView(item, if (wide) LayoutParams(-1, dp(40f)).apply { bottomMargin = dp(2f) }
                else LayoutParams(0, dp(40f), 1f).apply { marginEnd = dp(4f) })
        }
        if (wide) {
            addView(horizontalLayout(context).apply {
                addView(navigation, LayoutParams(dp(200f), -1))
                addView(View(context).apply { setBackgroundColor(palette.separator) }, LayoutParams(1, -1))
                addView(scroll, LayoutParams(0, -1, 1f))
            }, LayoutParams(-1, 0, 1f))
        } else {
            addView(navigation, LayoutParams(-1, -2))
            addView(View(context).apply { setBackgroundColor(palette.separator) }, LayoutParams(-1, 1))
            addView(scroll, LayoutParams(-1, 0, 1f))
        }
        select(initialPage.coerceIn(0, pages.lastIndex))
    }

    // region pages
    private fun page(title: String, vararg groups: View) = LinearLayout(context).apply {
        orientation = VERTICAL
        setPadding(dp(20f), dp(16f), dp(20f), dp(20f))
        addView(plasmaLabel(context, title, palette.text, 18f, bold = true),
            LayoutParams(-1, -2).apply { bottomMargin = dp(12f) })
        groups.forEach { addView(it, LayoutParams(-1, -2).apply { bottomMargin = dp(12f) }) }
    }

    /** Rows on one rounded card, with hairlines between them. */
    private fun group(vararg rows: View) = LinearLayout(context).apply {
        orientation = VERTICAL
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(palette.viewBackground)
            cornerRadius = 10 * density
            setStroke(1, palette.separator)
        }
        rows.forEachIndexed { index, row ->
            if (index > 0) addView(View(context).apply { setBackgroundColor(palette.separator) },
                LayoutParams(-1, 1).apply { marginStart = dp(14f); marginEnd = dp(14f) })
            addView(row, LayoutParams(-1, -2))
        }
    }

    /** Title and explanation on the left, [control] on the right (or below, when [below]). */
    private fun row(title: String, description: String?, control: View, below: Boolean = false,
                    onRowClick: (() -> Unit)? = null): View {
        val texts = LinearLayout(context).apply {
            orientation = VERTICAL
            addView(plasmaLabel(context, title, palette.text, 14f))
            if (description != null) addView(plasmaLabel(context, description, palette.inactiveText, 12f),
                LayoutParams(-1, -2).apply { topMargin = dp(2f) })
        }
        val layout = if (below) LinearLayout(context).apply {
            orientation = VERTICAL
            addView(texts, LayoutParams(-1, -2).apply { bottomMargin = dp(10f) })
            addView(control, LayoutParams(-2, -2))
        } else horizontalLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(texts, LayoutParams(0, -2, 1f).apply { marginEnd = dp(16f) })
            addView(control, LayoutParams(-2, -2))
        }
        layout.minimumHeight = dp(56f)
        layout.setPadding(dp(14f), dp(10f), dp(14f), dp(10f))
        if (onRowClick != null) layout.setOnClickListener { onRowClick() }
        return layout
    }

    private fun switchRow(title: String, description: String?, value: Boolean, apply: (Boolean) -> PlasmaSettings): View {
        val toggle = SettingsSwitch(context, palette, value) { change(apply(it)) }
        return row(title, description, toggle, onRowClick = { toggle.toggle() })
    }

    private fun segmentedRow(title: String, description: String?, options: List<Pair<String, String>>, selected: String,
                             apply: (String) -> PlasmaSettings): View =
        row(title, description, SettingsSegmented(context, palette, options, selected) { change(apply(it)) })

    private fun appearancePage(): View {
        val accents = listOf(0xFF3DAEE9, 0xFF9B59B6, 0xFFE93A9A, 0xFFE93D58, 0xFFE9643A, 0xFFEF973C, 0xFFE8CB2D,
            0xFFB6E521, 0xFF3DD425, 0xFF00D485, 0xFF00D3B8, 0xFF7F8C8D).map { it.toInt() }
        val swatches = ArrayList<AccentSwatch>()
        val accentRow = android.widget.GridLayout(context).apply { columnCount = 6 }
        accents.forEach { color ->
            val swatch = AccentSwatch(context, palette, color, color == current.accentColor) {
                swatches.forEach { s -> s.selectedSwatch = s.color == color }
                change(current.copy(accentColor = color))
            }
            swatches += swatch
            accentRow.addView(swatch, android.widget.GridLayout.LayoutParams().apply {
                width = dp(32f); height = dp(32f)
                rightMargin = dp(8f); bottomMargin = dp(6f)
            })
        }
        return page(tr("uiAppearance"),
            group(
                segmentedRow(t("nativePlasmaColorScheme"), null,
                    listOf("dark" to t("nativePlasmaBreezeDark"), "light" to t("nativePlasmaBreezeLight"),
                        "system" to t("nativePlasmaFollowSystem")), current.colorScheme) { current.copy(colorScheme = it) },
                row(t("nativePlasmaAccentColor"), null, accentRow, below = true),
                segmentedRow(t("nativePlasmaWallpaper"), null,
                    listOf("plasma" to t("nativePlasmaWallpaperPlasma"), "system" to t("nativePlasmaWallpaperAndroid")),
                    current.wallpaper) { current.copy(wallpaper = it) },
            ))
    }

    private fun taskbarPage(): View = page(tr("uiTaskbar"),
        group(
            switchRow(tr("uiFloatingPanel"),
                tr("uiLiftsThePanelOffTheScreenEdgeWhileNoWindowTouchesIt"),
                current.floatingPanel) { current.copy(floatingPanel = it) },
        ),
        group(
            switchRow(tr("uiShowTheMobileNetworkType"),
                tr("uiShowsLTE5GAndSoOnUnderTheSignalIcon"),
                current.trayNetworkType) { current.copy(trayNetworkType = it) },
            switchRow(tr("uiShowTheBatteryPercentage"),
                tr("uiShowsTheChargeAsAPercentageUnderTheBatteryIcon"),
                current.trayBatteryPercent) { current.copy(trayBatteryPercent = it) },
        ))

    private fun workspacePage(): View = page(tr("uiWorkspace"),
        group(
            row(t("nativePlasmaVirtualDesktops"), null,
                SettingsStepper(context, palette, current.virtualDesktops, 1, 6) { change(current.copy(virtualDesktops = it)) }),
            switchRow(tr("uiHotCorner"),
                tr("uiOpensTheOverviewWhenThePointerReachesTheTopLeftCorner"),
                current.hotCorner) { current.copy(hotCorner = it) },
            segmentedRow(t("nativePlasmaAnimationSpeed"), null,
                listOf("0" to t("nativePlasmaInstant"), "0.5" to "0.5×", "1" to "1×", "2" to "2×"),
                when {
                    current.animationSpeed <= 0f -> "0"
                    current.animationSpeed < .75f -> "0.5"
                    current.animationSpeed > 1.5f -> "2"
                    else -> "1"
                }) { current.copy(animationSpeed = it.toFloat()) },
        ))
    // endregion
}

/** A category in the settings list: icon and name, highlighted while hovered or selected. */
@SuppressLint("ViewConstructor")
private class SettingsNavItem(
    context: Context,
    private val palette: PlasmaPalette,
    glyph: Glyph,
    private val label: String,
    private val compact: Boolean,
    onPick: () -> Unit,
) : View(context) {
    private val density = resources.displayMetrics.density
    private val icon = GlyphDrawable(glyph, palette.text, 1.5f)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = PlasmaTheme.sans; textSize = 13.5f * density }
    private var hovered = false
    var selectedItem = false
        set(value) { field = value; invalidate() }

    init {
        contentDescription = label
        isFocusable = true
        setOnClickListener { onPick() }
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        val next = event.actionMasked != MotionEvent.ACTION_HOVER_EXIT
        if (next != hovered) { hovered = next; invalidate() }
        return super.onHoverEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        val box = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val radius = 8 * density
        if (selectedItem || hovered || isPressed) {
            paint.color = if (selectedItem) PlasmaTheme.withAlpha(palette.accent, 0x38) else palette.hover
            canvas.drawRoundRect(box, radius, radius, paint)
        }
        if (selectedItem && !compact) {
            paint.color = palette.accent
            canvas.drawRoundRect(RectF(0f, height * .25f, 3 * density, height * .75f), 2 * density, 2 * density, paint)
        }
        val iconSize = 18 * density
        val color = if (selectedItem) palette.accent else palette.text
        icon.color = color
        paint.color = color
        val textWidth = paint.measureText(label)
        val left = if (compact) ((width - iconSize - 8 * density - textWidth) / 2f).coerceAtLeast(6 * density) else 14 * density
        val top = (height - iconSize) / 2f
        icon.setBounds(left.toInt(), top.toInt(), (left + iconSize).toInt(), (top + iconSize).toInt())
        icon.draw(canvas)
        val textLeft = left + iconSize + 10 * density
        canvas.drawText(TextUtilsCompat.ellipsize(label, paint, width - textLeft - 6 * density), textLeft,
            height / 2f - (paint.descent() + paint.ascent()) / 2f, paint)
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        invalidate()
    }
}

/** A round icon button (the dialog's close button). */
@SuppressLint("ViewConstructor")
private class SettingsIconButton(
    context: Context,
    private val palette: PlasmaPalette,
    glyph: Glyph,
    description: String,
    onPick: () -> Unit,
) : View(context) {
    private val density = resources.displayMetrics.density
    private val icon = GlyphDrawable(glyph, palette.text, 1.6f)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var hovered = false

    init {
        contentDescription = description
        isFocusable = true
        setOnClickListener { onPick() }
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        val next = event.actionMasked != MotionEvent.ACTION_HOVER_EXIT
        if (next != hovered) { hovered = next; invalidate() }
        return super.onHoverEvent(event)
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (hovered || isPressed) {
            paint.color = if (isPressed) palette.pressed else palette.hover
            canvas.drawCircle(width / 2f, height / 2f, minOf(width, height) / 2f, paint)
        }
        val size = 16 * density
        icon.color = palette.text
        icon.setBounds(((width - size) / 2f).toInt(), ((height - size) / 2f).toInt(),
            ((width + size) / 2f).toInt(), ((height + size) / 2f).toInt())
        icon.draw(canvas)
    }
}

/** An on/off switch with a sliding thumb. */
@SuppressLint("ViewConstructor")
private class SettingsSwitch(
    context: Context,
    private val palette: PlasmaPalette,
    initial: Boolean,
    private val onToggle: (Boolean) -> Unit,
) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var checked = initial
    private var position = if (initial) 1f else 0f

    init {
        isFocusable = true
        setOnClickListener { toggle() }
    }

    fun toggle() {
        checked = !checked
        ValueAnimator.ofFloat(position, if (checked) 1f else 0f).apply {
            duration = PlasmaTheme.SHORT_MS
            interpolator = PlasmaTheme.outCubic
            addUpdateListener { position = it.animatedValue as Float; invalidate() }
            start()
        }
        onToggle(checked)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) =
        setMeasuredDimension((44 * density).toInt(), (24 * density).toInt())

    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat()
        val track = RectF(1f, 1f, width - 1f, h - 1f)
        paint.style = Paint.Style.FILL
        paint.color = PlasmaTheme.blend(PlasmaTheme.withAlpha(palette.text, 0x26), palette.accent, position)
        canvas.drawRoundRect(track, h / 2f, h / 2f, paint)
        val radius = h / 2f - 3 * density
        val cx = h / 2f + (width - h) * position
        paint.color = if (position > .5f) palette.accentText else palette.text
        canvas.drawCircle(cx, h / 2f, radius, paint)
    }
}

/** A row of mutually exclusive options; the chosen one is filled with the accent colour. */
@SuppressLint("ViewConstructor")
private class SettingsSegmented(
    context: Context,
    private val palette: PlasmaPalette,
    private val options: List<Pair<String, String>>,
    private var selected: String,
    private val onPick: (String) -> Unit,
) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = PlasmaTheme.sans
        textSize = 12.5f * density
        textAlign = Paint.Align.CENTER
    }
    private val pad = 14 * density
    private val widths = options.map { paint.measureText(it.second) + pad * 2 }
    private var hoveredIndex = -1

    init { isFocusable = true }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val wanted = widths.sum().toInt() + 2
        setMeasuredDimension(resolveSize(wanted, widthMeasureSpec), (32 * density).toInt())
    }

    /** Segments scale down together when the row is narrower than they want. */
    private fun bounds(): List<RectF> {
        val scale = ((width - 2f) / widths.sum()).coerceAtMost(1f)
        var x = 1f
        return widths.map { w -> RectF(x, 1f, x + w * scale, height - 1f).also { x += w * scale } }
    }

    private fun indexAt(x: Float) = bounds().indexOfFirst { x >= it.left && x < it.right }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        val next = if (event.actionMasked == MotionEvent.ACTION_HOVER_EXIT) -1 else indexAt(event.x)
        if (next != hoveredIndex) { hoveredIndex = next; invalidate() }
        return true
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            val index = indexAt(event.x)
            if (index >= 0 && event.y in 0f..height.toFloat() && options[index].first != selected) {
                selected = options[index].first
                invalidate()
                onPick(selected)
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val outer = RectF(1f, 1f, width - 1f, height - 1f)
        val radius = height / 2f
        paint.style = Paint.Style.FILL
        paint.color = PlasmaTheme.withAlpha(palette.text, 0x10)
        canvas.drawRoundRect(outer, radius, radius, paint)
        bounds().forEachIndexed { index, box ->
            val chosen = options[index].first == selected
            if (chosen || index == hoveredIndex) {
                paint.color = if (chosen) palette.accent else palette.hover
                val inner = RectF(box).apply { inset(2 * density, 2 * density) }
                canvas.drawRoundRect(inner, inner.height() / 2f, inner.height() / 2f, paint)
            }
            paint.color = if (chosen) palette.accentText else palette.text
            canvas.drawText(TextUtilsCompat.ellipsize(options[index].second, paint, box.width() - 8 * density),
                box.centerX(), box.centerY() - (paint.descent() + paint.ascent()) / 2f, paint)
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = palette.separator
        canvas.drawRoundRect(outer, radius, radius, paint)
    }
}

/** − value + for a small whole number. */
@SuppressLint("ViewConstructor")
private class SettingsStepper(
    context: Context,
    private val palette: PlasmaPalette,
    initial: Int,
    private val min: Int,
    private val max: Int,
    private val onValue: (Int) -> Unit,
) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = PlasmaTheme.sans
        textAlign = Paint.Align.CENTER
    }
    private var value = initial.coerceIn(min, max)
    private var hoveredSide = 0

    init { isFocusable = true }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) =
        setMeasuredDimension((112 * density).toInt(), (32 * density).toInt())

    private fun side(x: Float) = when {
        x < height -> -1
        x > width - height -> 1
        else -> 0
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        val next = if (event.actionMasked == MotionEvent.ACTION_HOVER_EXIT) 0 else side(event.x)
        if (next != hoveredSide) { hoveredSide = next; invalidate() }
        return true
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            val next = (value + side(event.x)).coerceIn(min, max)
            if (next != value) {
                value = next
                invalidate()
                onValue(value)
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat()
        listOf(-1 to h / 2f, 1 to width - h / 2f).forEach { (direction, cx) ->
            val enabled = if (direction < 0) value > min else value < max
            paint.style = Paint.Style.FILL
            paint.color = if (hoveredSide == direction && enabled) palette.hover else PlasmaTheme.withAlpha(palette.text, 0x10)
            canvas.drawCircle(cx, h / 2f, h / 2f - 1f, paint)
            paint.color = if (enabled) palette.text else palette.disabledText
            paint.strokeWidth = 1.6f * density
            paint.style = Paint.Style.STROKE
            val arm = 5 * density
            canvas.drawLine(cx - arm, h / 2f, cx + arm, h / 2f, paint)
            if (direction > 0) canvas.drawLine(cx, h / 2f - arm, cx, h / 2f + arm, paint)
        }
        paint.style = Paint.Style.FILL
        paint.color = palette.text
        paint.textSize = 15f * density
        canvas.drawText(value.toString(), width / 2f, h / 2f - (paint.descent() + paint.ascent()) / 2f, paint)
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
    init {
        contentDescription = String.format("#%06X", color and 0xFFFFFF)
        isFocusable = true
        isClickable = true
        setOnClickListener { onPick() }
    }
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
        if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
        return true
    }
}
