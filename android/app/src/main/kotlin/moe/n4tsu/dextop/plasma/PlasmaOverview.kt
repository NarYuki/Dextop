package moe.n4tsu.dextop.plasma

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import kotlin.math.ceil
import kotlin.math.sqrt

internal interface OverviewHost {
    val palette: PlasmaPalette
    val settings: PlasmaSettings
    fun overviewWindows(): List<ManagedWindow>
    fun thumbnail(window: ManagedWindow): Bitmap?
    fun currentDesktop(): Int
    fun desktopCount(): Int
    fun desktopWindows(desktop: Int): List<ManagedWindow>
    fun displaySize(): Pair<Int, Int>
    fun activateFromOverview(window: ManagedWindow)
    fun closeFromOverview(window: ManagedWindow)
    fun switchDesktopFromOverview(index: Int)
    fun moveWindowToDesktop(window: ManagedWindow, desktop: Int)
    fun exitOverview()
    fun searchHost(): LauncherHost
}

/**
 * Plasma 6 Overview: blurred desktop, virtual-desktop strip, window grid and
 * a search field that doubles as KRunner.  Windows fly from their real
 * positions into the grid and back out when leaving.
 */
@SuppressLint("ViewConstructor")
internal class OverviewView(
    context: Context,
    private val host: OverviewHost,
    background: Bitmap?,
) : FrameLayout(context) {
    private val palette = host.palette
    private val density = resources.displayMetrics.density
    private fun dp(value: Float) = value * density
    private val backdrop = ImageView(context).apply {
        scaleType = ImageView.ScaleType.FIT_XY
        background?.let(::setImageBitmap)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setRenderEffect(RenderEffect.createBlurEffect(dp(24f), dp(24f), Shader.TileMode.CLAMP))
        }
    }
    private val canvasView = OverviewCanvas(context)
    val search = PlasmaSearchField(context, palette, t("nativePlasmaOverviewSearch"))
    private val searchResults = verticalLayout(context)
    private var entered = 0f
    private var enterStart = SystemClock.uptimeMillis()
    private var exiting = false
    private var onExited: (() -> Unit)? = null

    private data class Cell(val window: ManagedWindow, val target: RectF, val source: RectF, val thumbnail: Bitmap?)
    private val cells = ArrayList<Cell>()
    private val desktopRects = ArrayList<RectF>()
    private var hovered: Cell? = null
    private var dragging: Cell? = null
    private var dragPoint = android.graphics.PointF()
    private var downCell: Cell? = null
    private var downAt = 0L
    private var selected = -1

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        addView(backdrop, LayoutParams(-1, -1))
        addView(canvasView, LayoutParams(-1, -1))
        addView(search, LayoutParams(dp(420f).toInt(), dp(38f).toInt(), android.view.Gravity.CENTER_HORIZONTAL or android.view.Gravity.TOP).apply {
            topMargin = dp(118f).toInt()
        })
        addView(searchResults, LayoutParams(dp(480f).toInt(), -2, android.view.Gravity.CENTER_HORIZONTAL or android.view.Gravity.TOP).apply {
            topMargin = dp(164f).toInt()
        })
        searchResults.background = roundedBackground(palette.popup, dp(8f), palette.frameOutline, 1)
        searchResults.visibility = GONE
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = updateSearch(s?.toString().orEmpty())
        })
        search.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                (searchResults.getChildAt(0) as? PlasmaRow)?.activate()
                    ?: cells.firstOrNull { matches(it.window) }?.let { host.activateFromOverview(it.window) }
                true
            } else false
        }
        search.alpha = 0f
    }

    private fun matches(window: ManagedWindow): Boolean {
        val query = search.text?.toString()?.trim().orEmpty()
        return query.isEmpty() || window.title.contains(query, true) || window.label.contains(query, true)
    }

    private fun updateSearch(query: String) {
        searchResults.removeAllViews()
        val launcher = host.searchHost()
        if (query.isBlank()) {
            searchResults.visibility = GONE
        } else {
            PlasmaSearch.calculate(query)?.let { answer ->
                searchResults.addView(PlasmaRow(context, palette, GlyphDrawable(Glyph.TILE, palette.accent), answer,
                    subtitle = t("nativePlasmaCalculatorCopy"), heightDp = 40f) { launcher.copyToClipboard(answer); host.exitOverview() })
            }
            PlasmaSearch.apps(launcher, query).take(6).forEach { app ->
                searchResults.addView(PlasmaRow(context, palette, app.icon, app.label, subtitle = t("nativePlasmaApplication"), heightDp = 38f, iconDp = 26f) {
                    host.exitOverview()
                    launcher.launch(app.packageName)
                })
            }
            searchResults.visibility = if (searchResults.childCount > 0) VISIBLE else GONE
        }
        canvasView.invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutCells()
    }

    private fun layoutCells() {
        cells.clear()
        desktopRects.clear()
        if (width == 0) return
        val windows = host.overviewWindows()
        val top = dp(176f)
        val area = RectF(dp(48f), top, width - dp(48f), height - dp(56f))
        val count = windows.size
        if (count > 0) {
            val aspect = area.width() / area.height()
            val columns = ceil(sqrt(count * aspect.toDouble())).toInt().coerceIn(1, count)
            val rows = ceil(count / columns.toDouble()).toInt()
            val cellWidth = area.width() / columns
            val cellHeight = area.height() / rows
            windows.forEachIndexed { index, window ->
                val row = index / columns
                val inRow = if (row == rows - 1) count - row * columns else columns
                val column = index % columns
                val rowOffset = (columns - inRow) * cellWidth / 2f
                val slot = RectF(
                    area.left + rowOffset + column * cellWidth, area.top + row * cellHeight,
                    area.left + rowOffset + (column + 1) * cellWidth, area.top + (row + 1) * cellHeight,
                )
                slot.inset(dp(18f), dp(14f))
                slot.bottom -= dp(30f)
                val frame = RectF(window.frame)
                val scale = minOf(slot.width() / frame.width(), slot.height() / frame.height(), 1f)
                val target = RectF(
                    slot.centerX() - frame.width() * scale / 2f, slot.centerY() - frame.height() * scale / 2f,
                    slot.centerX() + frame.width() * scale / 2f, slot.centerY() + frame.height() * scale / 2f,
                )
                cells += Cell(window, target, RectF(frame), host.thumbnail(window))
            }
        }
        val (dw, dh) = host.displaySize()
        val stripHeight = dp(76f)
        val stripWidth = stripHeight * dw / dh
        val total = host.desktopCount() * (stripWidth + dp(12f)) - dp(12f)
        var x = width / 2f - total / 2f
        repeat(host.desktopCount()) {
            desktopRects += RectF(x, dp(18f), x + stripWidth, dp(18f) + stripHeight)
            x += stripWidth + dp(12f)
        }
    }

    /** Drops a closed window from the grid and reflows the remaining ones. */
    fun remove(window: ManagedWindow) {
        if (hovered?.window === window) hovered = null
        layoutCells()
        canvasView.invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        enterStart = SystemClock.uptimeMillis()
        search.animate().alpha(1f).setDuration(host.settings.duration(PlasmaTheme.VERY_LONG_MS)).start()
        backdrop.alpha = 0f
        backdrop.animate().alpha(1f).setDuration(host.settings.duration(PlasmaTheme.VERY_LONG_MS)).start()
        requestFocus()
    }

    fun exit(done: () -> Unit) {
        if (exiting) return
        exiting = true
        onExited = done
        enterStart = SystemClock.uptimeMillis()
        search.animate().alpha(0f).setDuration(host.settings.duration(PlasmaTheme.LONG_MS)).start()
        searchResults.visibility = GONE
        backdrop.animate().alpha(0f).setDuration(host.settings.duration(PlasmaTheme.VERY_LONG_MS)).start()
        canvasView.invalidate()
    }

    private fun cellAt(x: Float, y: Float) = cells.lastOrNull { it.target.contains(x, y) && matches(it.window) }

    private fun closeRect(cell: Cell) = RectF(cell.target.right - dp(26f), cell.target.top - dp(8f), cell.target.right + dp(4f), cell.target.top + dp(22f))

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (exiting) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downCell = cellAt(event.x, event.y)
                downAt = event.eventTime
                dragPoint.set(event.x, event.y)
                if (event.isButtonPressed(MotionEvent.BUTTON_TERTIARY)) {
                    downCell?.let { host.closeFromOverview(it.window) }
                    downCell = null
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val cell = downCell
                if (cell != null && dragging == null &&
                    (kotlin.math.abs(event.x - dragPoint.x) > dp(12f) || kotlin.math.abs(event.y - dragPoint.y) > dp(12f))
                ) dragging = cell
                if (dragging != null) {
                    dragPoint.set(event.x, event.y)
                    canvasView.invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                val drag = dragging
                dragging = null
                if (drag != null) {
                    val desktop = desktopRects.indexOfFirst { it.contains(event.x, event.y) }
                    if (desktop >= 0) {
                        host.moveWindowToDesktop(drag.window, desktop)
                        layoutCells()
                    }
                    canvasView.invalidate()
                    return true
                }
                val desktop = desktopRects.indexOfFirst { it.contains(event.x, event.y) }
                val cell = cellAt(event.x, event.y)
                when {
                    desktop >= 0 -> {
                        host.switchDesktopFromOverview(desktop)
                        layoutCells()
                        canvasView.invalidate()
                    }
                    cell != null && closeRect(cell).contains(event.x, event.y) -> host.closeFromOverview(cell.window)
                    cell != null && cell == downCell -> host.activateFromOverview(cell.window)
                    cell == null && downCell == null -> host.exitOverview()
                }
            }
        }
        return true
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_HOVER_MOVE || event.actionMasked == MotionEvent.ACTION_HOVER_ENTER) {
            val next = cellAt(event.x, event.y)
            if (next != hovered) {
                hovered = next
                canvasView.invalidate()
            }
        }
        return super.dispatchGenericMotionEvent(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val visible = cells.filter { matches(it.window) }
            when (event.keyCode) {
                KeyEvent.KEYCODE_ESCAPE -> { host.exitOverview(); return true }
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_TAB -> if (visible.isNotEmpty() && search.text.isNullOrEmpty()) {
                    selected = (selected + 1) % visible.size; hovered = visible[selected]; canvasView.invalidate(); return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> if (visible.isNotEmpty() && search.text.isNullOrEmpty()) {
                    selected = (selected - 1 + visible.size) % visible.size; hovered = visible[selected]; canvasView.invalidate(); return true
                }
                KeyEvent.KEYCODE_ENTER -> hovered?.let { host.activateFromOverview(it.window); return true }
            }
            if (!search.isFocused && event.unicodeChar > 0 && !event.isCtrlPressed && !event.isMetaPressed) {
                search.requestFocus()
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private inner class OverviewCanvas(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = PlasmaTheme.sans }
        private val rect = RectF()
        private val closeGlyph = GlyphDrawable(Glyph.CLOSE, 0xFFFFFFFF.toInt(), 2f)

        override fun onDraw(canvas: Canvas) {
            val duration = host.settings.duration(PlasmaTheme.VERY_LONG_MS).coerceAtLeast(1L)
            val raw = ((SystemClock.uptimeMillis() - enterStart).toFloat() / duration).coerceIn(0f, 1f)
            val t = PlasmaTheme.inOutCubic.getInterpolation(raw)
            entered = if (exiting) 1f - t else t
            paint.color = PlasmaTheme.withAlpha(0, (0x99 * entered).toInt())
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            drawDesktopStrip(canvas)
            cells.forEach { cell ->
                if (cell === dragging) return@forEach
                val visible = matches(cell.window)
                EffectsView.lerp(cell.source, cell.target, entered, rect)
                paint.alpha = if (visible) 255 else (255 * (1f - .7f * entered)).toInt()
                drawThumb(canvas, cell, rect)
                if (entered > .6f && visible) {
                    val labelAlpha = ((entered - .6f) / .4f * 255).toInt()
                    text.color = PlasmaTheme.withAlpha(0xFFFCFCFC.toInt(), labelAlpha)
                    text.textSize = 13 * density
                    val label = TextUtilsCompat.ellipsize(cell.window.title, text, rect.width() - 28 * density)
                    val labelWidth = text.measureText(label)
                    val iconBox = RectF(rect.centerX() - labelWidth / 2 - 24 * density, rect.bottom + 8 * density,
                        rect.centerX() - labelWidth / 2 - 6 * density, rect.bottom + 26 * density)
                    canvas.drawIcon(cell.window.icon, iconBox, labelAlpha)
                    canvas.drawText(label, rect.centerX() + 9 * density, rect.bottom + 22 * density, text)
                }
                if (cell === hovered && !exiting) {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 3 * density
                    paint.color = palette.accent
                    canvas.drawRoundRect(rect.left - 4 * density, rect.top - 4 * density, rect.right + 4 * density, rect.bottom + 4 * density, 8 * density, 8 * density, paint)
                    paint.style = Paint.Style.FILL
                    val close = closeRect(cell)
                    paint.color = palette.negative
                    canvas.drawOval(close, paint)
                    closeGlyph.setBounds(close.left.toInt(), close.top.toInt(), close.right.toInt(), close.bottom.toInt())
                    closeGlyph.draw(canvas)
                }
            }
            dragging?.let { cell ->
                val w = cell.target.width() * .6f
                val h = cell.target.height() * .6f
                rect.set(dragPoint.x - w / 2, dragPoint.y - h / 2, dragPoint.x + w / 2, dragPoint.y + h / 2)
                paint.alpha = 220
                drawThumb(canvas, cell, rect)
            }
            if (cells.isEmpty() && entered > .5f) {
                text.color = PlasmaTheme.withAlpha(0xFFFCFCFC.toInt(), (255 * entered).toInt())
                text.textSize = 16 * density
                canvas.drawText(t("nativePlasmaNoWindows"), width / 2f, height / 2f, text)
            }
            if (raw < 1f) postInvalidateOnAnimation() else if (exiting) {
                onExited?.invoke()
                onExited = null
            }
        }

        private fun drawThumb(canvas: Canvas, cell: Cell, box: RectF) {
            val bitmap = cell.thumbnail
            if (bitmap != null && !bitmap.isRecycled) {
                canvas.drawBitmap(bitmap, null, box, paint)
            } else {
                val alpha = paint.alpha
                paint.color = palette.windowBackground
                paint.alpha = alpha
                canvas.drawRoundRect(box, 6 * density, 6 * density, paint)
                val size = minOf(box.width(), box.height()) * .35f
                canvas.drawIcon(cell.window.icon, RectF(box.centerX() - size / 2, box.centerY() - size / 2, box.centerX() + size / 2, box.centerY() + size / 2), alpha)
            }
            paint.alpha = 255
        }

        private fun drawDesktopStrip(canvas: Canvas) {
            val offset = -(1f - entered) * 120 * density
            val (dw, dh) = host.displaySize()
            desktopRects.forEachIndexed { index, box ->
                rect.set(box)
                rect.offset(0f, offset)
                val current = index == host.currentDesktop()
                paint.style = Paint.Style.FILL
                paint.color = PlasmaTheme.withAlpha(if (current) palette.accent else 0xFF000000.toInt(), if (current) 0x55 else 0x66)
                canvas.drawRoundRect(rect, 6 * density, 6 * density, paint)
                val sx = rect.width() / dw
                val sy = rect.height() / dh
                host.desktopWindows(index).forEach { window ->
                    val mini = RectF(rect.left + window.frame.left * sx, rect.top + window.frame.top * sy,
                        rect.left + window.frame.right * sx, rect.top + window.frame.bottom * sy)
                    val thumb = window.thumbnail
                    if (thumb != null && !thumb.isRecycled) canvas.drawBitmap(thumb, null, mini, paint)
                    else {
                        paint.color = PlasmaTheme.withAlpha(palette.windowBackground, 0xDD)
                        canvas.drawRect(mini, paint)
                    }
                }
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = if (current) 2 * density else 1f
                paint.color = if (current) palette.accent else 0x66FFFFFF
                if (dragging != null && rect.contains(dragPoint.x, dragPoint.y)) {
                    paint.strokeWidth = 3 * density
                    paint.color = palette.accent
                }
                canvas.drawRoundRect(rect, 6 * density, 6 * density, paint)
                paint.style = Paint.Style.FILL
                text.textSize = 11.5f * density
                text.color = 0xFFFCFCFC.toInt()
                canvas.drawText(t("nativePlasmaDesktopN").format(index + 1), rect.centerX(), rect.bottom + 16 * density, text)
            }
        }
    }
}

/** Alt+Tab "Thumbnail Grid" switcher. */
@SuppressLint("ViewConstructor")
internal class TaskSwitcherView(
    context: Context,
    private val palette: PlasmaPalette,
    private val windows: List<ManagedWindow>,
    private val thumbnails: Map<Int, Bitmap?>,
) : View(context) {
    var index = if (windows.size > 1) 1 else 0
        set(value) { field = value; invalidate() }
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = PlasmaTheme.sans }
    private val cellWidth get() = 220 * density
    private val cellHeight get() = 170 * density

    fun desiredSize(maxWidth: Int): Pair<Int, Int> {
        val columns = ((maxWidth - 40 * density) / cellWidth).toInt().coerceIn(1, windows.size.coerceAtLeast(1))
        val rows = ceil(windows.size / columns.toDouble()).toInt().coerceAtLeast(1)
        return (columns * cellWidth + 24 * density).toInt() to (rows * cellHeight + 24 * density).toInt()
    }

    override fun onDraw(canvas: Canvas) {
        val box = RectF(0f, 0f, width.toFloat(), height.toFloat())
        paint.color = palette.popup
        canvas.drawRoundRect(box, 10 * density, 10 * density, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = palette.frameOutline
        canvas.drawRoundRect(box, 10 * density, 10 * density, paint)
        paint.style = Paint.Style.FILL
        val columns = ((width - 24 * density) / cellWidth).toInt().coerceAtLeast(1)
        windows.forEachIndexed { i, window ->
            val left = 12 * density + (i % columns) * cellWidth
            val top = 12 * density + (i / columns) * cellHeight
            val cell = RectF(left, top, left + cellWidth, top + cellHeight)
            if (i == index) {
                paint.color = PlasmaTheme.withAlpha(palette.accent, 0x44)
                canvas.drawRoundRect(cell, 6 * density, 6 * density, paint)
                paint.style = Paint.Style.STROKE
                paint.color = palette.accent
                canvas.drawRoundRect(cell, 6 * density, 6 * density, paint)
                paint.style = Paint.Style.FILL
            }
            val thumbArea = RectF(cell.left + 10 * density, cell.top + 10 * density, cell.right - 10 * density, cell.bottom - 36 * density)
            val frame = window.frame
            val scale = minOf(thumbArea.width() / frame.width(), thumbArea.height() / frame.height())
            val thumb = RectF(thumbArea.centerX() - frame.width() * scale / 2, thumbArea.centerY() - frame.height() * scale / 2,
                thumbArea.centerX() + frame.width() * scale / 2, thumbArea.centerY() + frame.height() * scale / 2)
            val bitmap = thumbnails[window.taskId]
            if (bitmap != null && !bitmap.isRecycled) canvas.drawBitmap(bitmap, null, thumb, paint)
            else {
                paint.color = palette.windowBackground
                canvas.drawRect(thumb, paint)
                val s = minOf(thumb.width(), thumb.height()) * .4f
                canvas.drawIcon(window.icon, RectF(thumb.centerX() - s / 2, thumb.centerY() - s / 2, thumb.centerX() + s / 2, thumb.centerY() + s / 2))
            }
            text.textSize = 12.5f * density
            text.color = palette.text
            val label = TextUtilsCompat.ellipsize(window.title, text, cell.width() - 40 * density)
            val labelWidth = text.measureText(label)
            canvas.drawIcon(window.icon, RectF(cell.centerX() - labelWidth / 2 - 22 * density, cell.bottom - 29 * density,
                cell.centerX() - labelWidth / 2 - 6 * density, cell.bottom - 13 * density))
            canvas.drawText(label, cell.centerX() + 8 * density, cell.bottom - 16 * density, text)
        }
    }

    fun selectedWindow(): ManagedWindow? = windows.getOrNull(index)
}

internal fun Rect.toRectF() = RectF(this)
