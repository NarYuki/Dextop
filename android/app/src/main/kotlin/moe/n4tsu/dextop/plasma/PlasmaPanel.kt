package moe.n4tsu.dextop.plasma

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.text.format.DateFormat
import android.view.MotionEvent
import android.view.View
import java.util.Date

/** Everything the panel needs from the window manager. */
internal interface PanelHost {
    val palette: PlasmaPalette
    val settings: PlasmaSettings
    fun panelEntries(): List<PanelTask>
    fun currentDesktop(): Int
    fun desktopCount(): Int
    fun pagerWindows(desktop: Int): List<Pair<Rect, Boolean>>
    fun displaySize(): Pair<Int, Int>
    fun tray(): TrayState
    fun onLauncherClicked(anchor: RectF)
    fun onTaskClicked(entry: PanelTask, anchor: RectF)
    fun onTaskMiddleClicked(entry: PanelTask)
    fun onTaskContextMenu(entry: PanelTask, anchor: RectF)
    fun onTaskHover(entry: PanelTask?, anchor: RectF?)
    fun onDesktopClicked(index: Int)
    fun onDesktopScroll(delta: Int)
    fun onTrayClicked(item: TrayItem, anchor: RectF)
    fun onClockClicked(anchor: RectF)
    fun onShowDesktopClicked()
    fun onPanelContextMenu(rawX: Float, rawY: Float)
}

/** A task-manager slot: a pinned launcher, a running app, or both. */
internal data class PanelTask(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val windows: List<ManagedWindow>,
    val pinned: Boolean,
    val active: Boolean,
    val attention: Boolean = false,
)

internal enum class TrayItem { EXPAND, VOLUME, NETWORK, BATTERY }

internal data class TrayState(
    val volume: Float,
    val muted: Boolean,
    val battery: Float,
    val charging: Boolean,
    val networkConnected: Boolean,
)

/**
 * The Plasma 6 panel: floating by default, it "defloats" into a docked bar
 * whenever a window touches it, exactly like KWin + plasmashell.
 */
@SuppressLint("ViewConstructor")
internal class PlasmaPanelView(
    context: Context,
    private val host: PanelHost,
) : View(context) {
    private sealed class Slot(val rect: RectF) {
        class Launcher(rect: RectF) : Slot(rect)
        class Pager(rect: RectF) : Slot(rect)
        class Task(rect: RectF, val entry: PanelTask) : Slot(rect)
        class Tray(rect: RectF, val item: TrayItem) : Slot(rect)
        class Clock(rect: RectF) : Slot(rect)
        class ShowDesktop(rect: RectF) : Slot(rect)
    }

    private val density = resources.displayMetrics.density
    private fun dp(value: Float) = value * density

    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = PlasmaTheme.sans }
    private val pagerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val slots = ArrayList<Slot>()
    private val launcherGlyph = GlyphDrawable(Glyph.KDE_LAUNCHER, 0)
    private val trayGlyphs = HashMap<TrayItem, GlyphDrawable>()
    private val showDesktopGlyph = GlyphDrawable(Glyph.SHOW_DESKTOP, 0, 1.4f)
    private var hoveredSlot: Slot? = null
    private var pressedSlot: Slot? = null
    private val hoverFade = HashMap<Any, Float>()
    private var hoverAnimator: ValueAnimator? = null
    private var launchBounce: Pair<String, Long>? = null
    private val rect = RectF()

    /** 0 = docked edge-to-edge, 1 = floating with margins and rounded corners. */
    var floatFraction = 1f
        set(value) { field = value; invalidate() }

    /** Height of the bar itself, independent of floating margins. */
    val thickness: Int get() = dp(44f).toInt()
    val floatMargin: Int get() = dp(8f).toInt()

    init {
        setOnHoverListener { _, event ->
            val slot = if (event.actionMasked == MotionEvent.ACTION_HOVER_EXIT) null else slotAt(event.x, event.y)
            if (slot?.key() != hoveredSlot?.key()) {
                hoveredSlot = slot
                animateHover()
                val task = slot as? Slot.Task
                host.onTaskHover(task?.entry, task?.rect?.let(::toRaw))
            }
            false
        }
        setOnGenericMotionListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_SCROLL) {
                val slot = slotAt(event.x, event.y)
                val delta = if (event.getAxisValue(MotionEvent.AXIS_VSCROLL) > 0) -1 else 1
                if (slot is Slot.Pager) host.onDesktopScroll(delta)
                true
            } else false
        }
    }

    private fun Slot.key(): Any = when (this) {
        is Slot.Task -> "task:" + entry.packageName
        is Slot.Tray -> "tray:" + item
        else -> javaClass.simpleName
    }

    fun bounceLaunch(packageName: String) {
        launchBounce = packageName to SystemClock.uptimeMillis()
        postInvalidateOnAnimation()
    }

    private fun animateHover() {
        hoverAnimator?.cancel()
        val target = hoveredSlot?.key()
        val start = HashMap(hoverFade)
        if (target != null && target !in start) start[target] = 0f
        hoverAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = host.settings.duration(PlasmaTheme.LONG_MS).coerceAtLeast(1L)
            interpolator = PlasmaTheme.outCubic
            addUpdateListener {
                val t = it.animatedValue as Float
                start.forEach { (key, from) ->
                    val to = if (key == target) 1f else 0f
                    hoverFade[key] = from + (to - from) * t
                }
                invalidate()
            }
            start()
        }
    }

    private fun toRaw(local: RectF): RectF {
        val location = IntArray(2)
        getLocationOnScreen(location)
        return RectF(local).apply { offset(location[0].toFloat(), location[1].toFloat()) }
    }

    /** Bar rectangle inside this view for the current float animation state. */
    private fun barRect(): RectF {
        val margin = floatMargin * floatFraction
        return RectF(margin, margin, width - margin, height - margin)
    }

    private fun layoutSlots(bar: RectF) {
        slots.clear()
        val size = bar.height()
        var x = bar.left + dp(4f)
        slots += Slot.Launcher(RectF(x, bar.top, x + size, bar.bottom))
        x += size + dp(2f)
        val pagerWidth = if (host.desktopCount() > 1) {
            val (dw, dh) = host.displaySize()
            val cell = (size - dp(14f)) * dw / dh.coerceAtLeast(1)
            host.desktopCount() * (cell + dp(3f)) + dp(8f)
        } else 0f
        if (pagerWidth > 0f) {
            slots += Slot.Pager(RectF(x, bar.top, x + pagerWidth, bar.bottom))
            x += pagerWidth + dp(4f)
        }
        // Right side is laid out from the edge inward.
        var right = bar.right - dp(2f)
        val showDesktopWidth = dp(10f)
        slots += Slot.ShowDesktop(RectF(right - showDesktopWidth, bar.top, right, bar.bottom))
        right -= showDesktopWidth + dp(4f)
        textPaint.textSize = dp(12.5f)
        val clockWidth = textPaint.measureText("00:00") + dp(28f)
        slots += Slot.Clock(RectF(right - clockWidth, bar.top, right, bar.bottom))
        right -= clockWidth
        val trayCell = dp(30f)
        val tray = listOf(TrayItem.BATTERY, TrayItem.NETWORK, TrayItem.VOLUME, TrayItem.EXPAND)
        tray.forEach { item ->
            slots += Slot.Tray(RectF(right - trayCell, bar.top, right, bar.bottom), item)
            right -= trayCell
        }
        right -= dp(6f)
        val entries = host.panelEntries()
        val cell = size + dp(4f)
        val maxCells = ((right - x) / cell).toInt().coerceAtLeast(0)
        entries.take(maxCells).forEach { entry ->
            slots += Slot.Task(RectF(x, bar.top, x + cell, bar.bottom), entry)
            x += cell
        }
    }

    override fun onDraw(canvas: Canvas) {
        val palette = host.palette
        val bar = barRect()
        val radius = dp(10f) * floatFraction
        panelPaint.color = palette.panel
        canvas.drawRoundRect(bar, radius, radius, panelPaint)
        outlinePaint.color = palette.frameOutline
        if (floatFraction > 0f) {
            canvas.drawRoundRect(bar, radius, radius, outlinePaint)
        } else {
            outlinePaint.color = palette.separator
            canvas.drawLine(0f, .5f, width.toFloat(), .5f, outlinePaint)
        }
        layoutSlots(bar)
        slots.forEach { slot -> drawSlot(canvas, slot, palette) }
        if (launchBounce != null) postInvalidateOnAnimation()
    }

    private fun drawHover(canvas: Canvas, slot: Slot, palette: PlasmaPalette, inset: Float = dp(3f)) {
        val fade = hoverFade[slot.key()] ?: 0f
        val pressed = pressedSlot?.key() == slot.key()
        if (fade <= 0f && !pressed) return
        highlightPaint.color = if (pressed) palette.pressed else PlasmaTheme.withAlpha(palette.hover, (0x33 * fade).toInt())
        rect.set(slot.rect)
        rect.inset(inset, inset)
        canvas.drawRoundRect(rect, dp(4f), dp(4f), highlightPaint)
        if (!pressed && fade > 0f) {
            outlinePaint.color = PlasmaTheme.withAlpha(palette.accent, (0x99 * fade).toInt())
            canvas.drawRoundRect(rect, dp(4f), dp(4f), outlinePaint)
        }
    }

    private fun drawSlot(canvas: Canvas, slot: Slot, palette: PlasmaPalette) {
        when (slot) {
            is Slot.Launcher -> {
                drawHover(canvas, slot, palette)
                launcherGlyph.color = palette.text
                rect.set(slot.rect); rect.inset(dp(10f), dp(10f))
                launcherGlyph.setBounds(rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt())
                launcherGlyph.draw(canvas)
            }
            is Slot.Pager -> drawPager(canvas, slot, palette)
            is Slot.Task -> drawTask(canvas, slot, palette)
            is Slot.Tray -> {
                drawHover(canvas, slot, palette, dp(6f))
                val tray = host.tray()
                val glyph = trayGlyphs.getOrPut(slot.item) {
                    GlyphDrawable(
                        when (slot.item) {
                            TrayItem.EXPAND -> Glyph.CHEVRON_UP
                            TrayItem.VOLUME -> Glyph.VOLUME
                            TrayItem.NETWORK -> Glyph.NETWORK
                            TrayItem.BATTERY -> Glyph.BATTERY
                        },
                        0,
                        1.5f,
                    )
                }
                glyph.color = palette.text
                glyph.level01 = when (slot.item) {
                    TrayItem.BATTERY -> {
                        glyph.color = if (tray.battery < .15f && !tray.charging) palette.negative else palette.text
                        tray.battery
                    }
                    TrayItem.VOLUME -> if (tray.muted) 0f else tray.volume
                    else -> 1f
                }
                rect.set(slot.rect); rect.inset(dp(7f), (slot.rect.height() - dp(16f)) / 2f)
                glyph.setBounds(rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt())
                glyph.draw(canvas)
                if (slot.item == TrayItem.NETWORK && !tray.networkConnected) {
                    indicatorPaint.color = palette.negative
                    canvas.drawCircle(rect.right - dp(1f), rect.bottom - dp(2f), dp(3f), indicatorPaint)
                }
            }
            is Slot.Clock -> {
                drawHover(canvas, slot, palette)
                val now = Date()
                val time = DateFormat.getTimeFormat(context).format(now)
                val date = DateFormat.getMediumDateFormat(context).format(now)
                textPaint.color = palette.text
                textPaint.typeface = PlasmaTheme.sans
                textPaint.textSize = dp(12.5f)
                canvas.drawText(time, slot.rect.centerX(), slot.rect.centerY() - dp(2f), textPaint)
                textPaint.textSize = dp(10f)
                textPaint.color = palette.inactiveText
                canvas.drawText(date, slot.rect.centerX(), slot.rect.centerY() + dp(11f), textPaint)
            }
            is Slot.ShowDesktop -> {
                drawHover(canvas, slot, palette, 0f)
                outlinePaint.color = palette.separator
                canvas.drawLine(slot.rect.left, slot.rect.top + dp(8f), slot.rect.left, slot.rect.bottom - dp(8f), outlinePaint)
            }
        }
    }

    private fun drawPager(canvas: Canvas, slot: Slot.Pager, palette: PlasmaPalette) {
        val count = host.desktopCount()
        val (dw, dh) = host.displaySize()
        val cellHeight = slot.rect.height() - dp(14f)
        val cellWidth = cellHeight * dw / dh.coerceAtLeast(1)
        var x = slot.rect.left + dp(4f)
        val top = slot.rect.top + dp(7f)
        for (index in 0 until count) {
            rect.set(x, top, x + cellWidth, top + cellHeight)
            val current = index == host.currentDesktop()
            pagerPaint.style = Paint.Style.FILL
            pagerPaint.color = if (current) PlasmaTheme.withAlpha(palette.accent, 0x55) else PlasmaTheme.withAlpha(palette.text, 0x14)
            canvas.drawRoundRect(rect, dp(2f), dp(2f), pagerPaint)
            pagerPaint.style = Paint.Style.STROKE
            pagerPaint.strokeWidth = 1f
            pagerPaint.color = if (current) palette.accent else palette.separator
            canvas.drawRoundRect(rect, dp(2f), dp(2f), pagerPaint)
            val scaleX = cellWidth / dw
            val scaleY = cellHeight / dh
            host.pagerWindows(index).forEach { (frame, active) ->
                val mini = RectF(
                    rect.left + frame.left * scaleX, rect.top + frame.top * scaleY,
                    rect.left + frame.right * scaleX, rect.top + frame.bottom * scaleY,
                )
                pagerPaint.style = Paint.Style.FILL
                pagerPaint.color = if (active) PlasmaTheme.withAlpha(palette.text, 0x88) else PlasmaTheme.withAlpha(palette.text, 0x44)
                canvas.drawRect(mini, pagerPaint)
                pagerPaint.style = Paint.Style.STROKE
                pagerPaint.color = palette.panel
                canvas.drawRect(mini, pagerPaint)
            }
            x += cellWidth + dp(3f)
        }
    }

    private fun drawTask(canvas: Canvas, slot: Slot.Task, palette: PlasmaPalette) {
        val entry = slot.entry
        val running = entry.windows.isNotEmpty()
        val fade = hoverFade[slot.key()] ?: 0f
        rect.set(slot.rect)
        rect.inset(dp(2f), dp(3f))
        if (entry.active || fade > 0f) {
            highlightPaint.color = if (entry.active) PlasmaTheme.withAlpha(palette.accent, 0x3A + (0x20 * fade).toInt())
            else PlasmaTheme.withAlpha(palette.hover, (0x33 * fade).toInt())
            canvas.drawRoundRect(rect, dp(4f), dp(4f), highlightPaint)
            if (entry.active || fade > .01f) {
                outlinePaint.color = PlasmaTheme.withAlpha(palette.accent, if (entry.active) 0xAA else (0x88 * fade).toInt())
                canvas.drawRoundRect(rect, dp(4f), dp(4f), outlinePaint)
            }
        }
        var iconRect = RectF(slot.rect)
        iconRect.inset((slot.rect.width() - dp(28f)) / 2f, (slot.rect.height() - dp(28f)) / 2f)
        launchBounce?.let { (pkg, started) ->
            if (pkg == entry.packageName) {
                val elapsed = SystemClock.uptimeMillis() - started
                if (elapsed > 900) {
                    launchBounce = null
                } else {
                    val offset = -kotlin.math.abs(kotlin.math.sin(elapsed / 900.0 * Math.PI * 2)).toFloat() * dp(5f)
                    iconRect = RectF(iconRect).apply { offset(0f, offset) }
                }
            }
        }
        canvas.drawIcon(entry.icon, iconRect, if (running || entry.pinned) 255 else 160)
        if (running) {
            // Plasma draws one short line per window, below the icon.
            indicatorPaint.color = if (entry.active) palette.accent else palette.inactiveText
            val count = entry.windows.size.coerceAtMost(3)
            val lineWidth = if (entry.active) dp(14f) else dp(6f)
            val gap = dp(3f)
            val total = count * lineWidth + (count - 1) * gap
            var lx = slot.rect.centerX() - total / 2f
            val ly = slot.rect.bottom - dp(4.5f)
            repeat(count) {
                canvas.drawRoundRect(lx, ly, lx + lineWidth, ly + dp(2.5f), dp(1.2f), dp(1.2f), indicatorPaint)
                lx += lineWidth + gap
            }
            if (entry.windows.all { it.minimized }) {
                indicatorPaint.color = PlasmaTheme.withAlpha(palette.panel, 0x66)
                canvas.drawRoundRect(iconRect, dp(4f), dp(4f), indicatorPaint)
            }
        }
    }

    private fun slotAt(x: Float, y: Float): Slot? = slots.lastOrNull { it.rect.contains(x, y) }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedSlot = slotAt(event.x, event.y)
                invalidate()
                if (event.isButtonPressed(MotionEvent.BUTTON_SECONDARY)) {
                    val slot = pressedSlot
                    pressedSlot = null
                    if (slot is Slot.Task) host.onTaskContextMenu(slot.entry, toRaw(slot.rect))
                    else host.onPanelContextMenu(event.rawX, event.rawY)
                    return true
                }
                if (event.isButtonPressed(MotionEvent.BUTTON_TERTIARY)) {
                    (pressedSlot as? Slot.Task)?.let { host.onTaskMiddleClicked(it.entry) }
                    pressedSlot = null
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                val slot = pressedSlot
                pressedSlot = null
                invalidate()
                if (slot == null || slotAt(event.x, event.y)?.key() != slot.key()) return true
                val longPress = event.eventTime - event.downTime > 550
                when (slot) {
                    is Slot.Launcher -> host.onLauncherClicked(toRaw(slot.rect))
                    is Slot.Pager -> {
                        val count = host.desktopCount()
                        val index = ((event.x - slot.rect.left) / slot.rect.width() * count).toInt().coerceIn(0, count - 1)
                        host.onDesktopClicked(index)
                    }
                    is Slot.Task -> if (longPress) host.onTaskContextMenu(slot.entry, toRaw(slot.rect))
                        else host.onTaskClicked(slot.entry, toRaw(slot.rect))
                    is Slot.Tray -> host.onTrayClicked(slot.item, toRaw(slot.rect))
                    is Slot.Clock -> host.onClockClicked(toRaw(slot.rect))
                    is Slot.ShowDesktop -> host.onShowDesktopClicked()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                pressedSlot = null
                invalidate()
            }
        }
        return true
    }

    /** Screen rectangle of the task-manager icon for a package, used by minimize animations. */
    fun taskIconRect(packageName: String): RectF? =
        slots.firstOrNull { it is Slot.Task && it.entry.packageName == packageName }?.rect?.let(::toRaw)

    fun launcherRect(): RectF? = slots.firstOrNull { it is Slot.Launcher }?.rect?.let(::toRaw)
}
