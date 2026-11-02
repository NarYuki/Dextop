@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.n4tsu.dextop.plasma

import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import moe.n4tsu.dextop.InlineStrings
import moe.n4tsu.dextop.localText

/** Book's text, written in place as a Japanese/English pair (see InlineStrings). */
internal fun tr(ja: String, en: String) = localText(ja, en)

/** [tr] with {0}, {1}… filled in. */
internal fun trf(ja: String, en: String, vararg args: Any?) = InlineStrings.format(localText(ja, en), *args)

/** A Material Symbol (Rounded) by name, drawn from the paths in [MaterialSymbols]. */
@Composable
internal fun Sym(name: String, tint: Color, size: Dp = 24.dp, filled: Boolean = false, modifier: Modifier = Modifier) {
    val path = remember(name, filled) { GlyphPainter.symbolPath(name, filled) }
    val paint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG) }
    Canvas(modifier.size(size)) {
        val p = path ?: return@Canvas
        paint.color = tint.toArgb()
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            native.save()
            native.scale(this.size.width / 24f, this.size.height / 24f)
            native.drawPath(p, paint)
            native.restore()
        }
    }
}

/** A Material Symbol on a tinted circle, as a Drawable (desktop files and folders). */
internal class SymbolDrawable(private val name: String, private val tint: Int, private val container: Int) : android.graphics.drawable.Drawable() {
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    override fun draw(canvas: android.graphics.Canvas) {
        val b = bounds
        paint.color = container
        canvas.drawRoundRect(android.graphics.RectF(b), b.width() * .28f, b.height() * .28f, paint)
        val path = GlyphPainter.symbolPath(name, true) ?: return
        val size = b.width() * .62f
        canvas.save()
        canvas.translate(b.exactCenterX() - size / 2f, b.exactCenterY() - size / 2f)
        canvas.scale(size / 24f, size / 24f)
        paint.color = tint
        canvas.drawPath(path, paint)
        canvas.restore()
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
}

/** An app icon (or any drawable). */
@Composable
internal fun DrawableIcon(drawable: Drawable?, size: Dp, alpha: Int = 255, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val d = drawable ?: return@Canvas
        drawIntoCanvas { canvas ->
            // Previews keep their proportions, centred; icons are square anyway.
            val iw = d.intrinsicWidth
            val ih = d.intrinsicHeight
            val w = this.size.width
            val h = this.size.height
            if (iw > 0 && ih > 0 && iw != ih) {
                val scale = minOf(w / iw, h / ih)
                val dw = iw * scale
                val dh = ih * scale
                d.setBounds(((w - dw) / 2).toInt(), ((h - dh) / 2).toInt(), ((w + dw) / 2).toInt(), ((h + dh) / 2).toInt())
            } else d.setBounds(0, 0, w.toInt(), h.toInt())
            d.alpha = alpha
            d.draw(canvas.nativeCanvas)
            d.alpha = 255
        }
    }
}

/** Screen bounds of a composable (the compositor works in display coordinates). */
@Composable
internal fun Modifier.onScreen(update: (RectF) -> Unit): Modifier {
    val view = LocalView.current
    return this.onGloballyPositioned { coordinates ->
        val screen = IntArray(2).also(view::getLocationOnScreen)
        val window = IntArray(2).also(view::getLocationInWindow)
        val b = coordinates.boundsInWindow()
        update(RectF(b.left + screen[0] - window[0], b.top + screen[1] - window[1],
            b.right + screen[0] - window[0], b.bottom + screen[1] - window[1]))
    }
}

/** Right and middle mouse buttons and hover, which plain clicks do not report. */
internal fun Modifier.pointerButtons(
    onSecondary: (() -> Unit)? = null,
    onTertiary: (() -> Unit)? = null,
    onHover: ((Boolean) -> Unit)? = null,
) = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            when (event.type) {
                // Only a button with a handler takes the press; others reach the content.
                PointerEventType.Press -> when {
                    event.buttons.isSecondaryPressed && onSecondary != null -> { event.changes.forEach { it.consume() }; onSecondary() }
                    event.buttons.isTertiaryPressed && onTertiary != null -> { event.changes.forEach { it.consume() }; onTertiary() }
                }
                PointerEventType.Enter -> onHover?.invoke(true)
                PointerEventType.Exit -> onHover?.invoke(false)
            }
        }
    }
}

/** A round icon button: tonal while [selected], plain otherwise. */
@Composable
internal fun BookIconButton(
    symbol: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    selected: Boolean = false,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    container: Color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
) {
    var hovered by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Box(
        modifier.size(size).clip(CircleShape)
            .background(if (hovered && !selected) colors.onSurface.copy(alpha = .08f) else container)
            .pointerButtons(onHover = { hovered = it })
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Sym(symbol, if (selected) colors.onPrimary else tint, size * .55f, filled = selected) }
}

/**
 * The large pill slider of Book's quick settings: a thick track whose filled part carries the
 * icon. Changes are reported while dragging; [onCommit] once more when the finger lifts.
 */
@Composable
internal fun BookSlider(
    value: Float,
    symbol: String,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onCommit: (Float) -> Unit = onChange,
) {
    val colors = MaterialTheme.colorScheme
    var local by remember { mutableFloatStateOf(value) }
    var dragging by remember { mutableStateOf(false) }
    val shown = (if (dragging) local else value).coerceIn(0f, 1f)
    var widthPx by remember { mutableFloatStateOf(1f) }
    val latestChange by androidx.compose.runtime.rememberUpdatedState(onChange)
    val latestCommit by androidx.compose.runtime.rememberUpdatedState(onCommit)
    BoxWithConstraints(
        modifier.height(48.dp).clip(RoundedCornerShape(24.dp)).background(colors.surfaceContainerHighest)
            .onGloballyPositioned { widthPx = it.size.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    dragging = true
                    local = (down.position.x / widthPx).coerceIn(0f, 1f)
                    latestChange(local)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        local = (change.position.x / widthPx).coerceIn(0f, 1f)
                        latestChange(local)
                    }
                    dragging = false
                    latestCommit(local)
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        // The filled part never gets narrower than its round end around the icon.
        val filled = maxWidth * shown
        Box(Modifier.height(48.dp).width(if (filled < 48.dp) 48.dp else filled).clip(RoundedCornerShape(24.dp))
            .background(colors.primary))
        Sym(symbol, colors.onPrimary, 20.dp, filled = true, modifier = Modifier.padding(start = 14.dp))
    }
}

/** One row of a settings list: icon, title and summary, and an optional trailing control. */
@Composable
internal fun BookListItem(
    title: String,
    summary: String? = null,
    symbol: String? = null,
    shape: Shape = RoundedCornerShape(4.dp),
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    var hovered by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (hovered && onClick != null && enabled) colors.surfaceContainerHighest else colors.surfaceContainerHigh)
            .pointerButtons(onHover = { hovered = it })
            .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = if (summary == null) 18.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (symbol != null) Sym(symbol, if (enabled) colors.onSurfaceVariant else colors.outline, 22.dp)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium,
                color = if (enabled) colors.onSurface else colors.outline, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (summary != null) Text(summary, style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke(this)
    }
}

/** A grouped list: the first and last rows carry the group's large corners. */
@Composable
internal fun BookGroup(title: String? = null, rows: List<@Composable (Shape) -> Unit>) {
    if (rows.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (title != null) Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
        rows.forEachIndexed { index, row ->
            val top = if (index == 0) 20.dp else 4.dp
            val bottom = if (index == rows.lastIndex) 20.dp else 4.dp
            row(RoundedCornerShape(top, top, bottom, bottom))
        }
    }
}

/** A list row with a switch at its end; the whole row toggles. */
@Composable
internal fun BookSwitchItem(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    shape: Shape,
    summary: String? = null,
    symbol: String? = null,
    enabled: Boolean = true,
) = BookListItem(title, summary, symbol, shape, enabled, onClick = { onChange(!checked) }) {
    Switch(checked = checked, onCheckedChange = if (enabled) onChange else null, enabled = enabled)
}

/** A small label over the wallpaper (desktop icons, widget captions). */
@Composable
internal fun WallpaperText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge.copy(
            color = Color.White,
            shadow = androidx.compose.ui.graphics.Shadow(Color(0xA0000000), androidx.compose.ui.geometry.Offset(0f, 1f), 6f),
        ),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = modifier.width(96.dp),
    )
}

/**
 * A floating surface takes every pointer event inside it, background included: nothing under
 * a panel, menu or Book window reacts to a click, scroll or hover on it.
 */
internal fun Modifier.blockPointer() = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Final)
            event.changes.forEach { if (!it.isConsumed) it.consume() }
        }
    }
}
