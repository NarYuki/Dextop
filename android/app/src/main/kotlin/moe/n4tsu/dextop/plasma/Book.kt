@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)

package moe.n4tsu.dextop.plasma

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Build
import android.text.format.DateFormat
import android.view.View
import android.widget.FrameLayout
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.delay
import java.util.Date
import java.util.Locale

/**
 * Dextop Book: the compositor laid out like a Pixel laptop and built from Material 3 Expressive
 * (Jetpack Compose material3): the date and status along the top, apps in a floating toolbar at
 * the bottom centre, the launcher above it, and windows as rounded tonal surfaces. Colours are
 * the device's dynamic Material You scheme.
 */
internal class BookShell(
    service: Context,
    displayId: Int,
    displayWidth: Int,
    displayHeight: Int,
    densityDpi: Int,
    host: PlasmaShell.Host,
    fullscreenAppDisplays: Boolean = false,
    passiveShellFocus: Boolean = false,
    maxRateAppDisplays: Boolean = false,
    applicationOverlay: Boolean = false,
    privateAppDisplays: Boolean = false,
) : CompositedWindowShell(
    service, displayId, displayWidth, displayHeight, densityDpi, host,
    fullscreenAppDisplays, passiveShellFocus, maxRateAppDisplays,
    applicationOverlay, privateAppDisplays, autoMode = false, book = true,
)

internal object BookTheme {
    fun dark(context: Context, settings: PlasmaSettings): Boolean = when (settings.colorScheme) {
        "light" -> false
        "dark" -> true
        else -> context.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    /** The device's dynamic scheme (Material You), or the baseline one before Android 12. */
    fun scheme(context: Context, dark: Boolean): ColorScheme =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else if (dark) darkColorScheme() else lightColorScheme()

    /** The same scheme in the roles the compositor's own views (windows, menus, applets) draw with. */
    fun palette(context: Context, settings: PlasmaSettings): PlasmaPalette {
        val dark = dark(context, settings)
        val c = scheme(context, dark)
        return PlasmaPalette(
            dark = dark,
            windowBackground = c.surfaceContainer.toArgb(),
            viewBackground = c.surface.toArgb(),
            headerActive = c.surfaceContainerHigh.toArgb(),
            headerInactive = c.surfaceContainer.toArgb(),
            text = c.onSurface.toArgb(),
            inactiveText = c.onSurfaceVariant.toArgb(),
            disabledText = c.outline.toArgb(),
            accent = c.primary.toArgb(),
            accentText = c.onPrimary.toArgb(),
            negative = c.error.toArgb(),
            positive = if (dark) 0xFF6DD58C.toInt() else 0xFF146C2E.toInt(),
            neutral = c.tertiary.toArgb(),
            panel = c.surfaceContainerHigh.toArgb(),
            popup = c.surfaceContainer.toArgb(),
            separator = c.outlineVariant.toArgb(),
            hover = PlasmaTheme.withAlpha(c.onSurface.toArgb(), 0x14),
            pressed = PlasmaTheme.withAlpha(c.primary.toArgb(), 0x29),
            frameOutline = PlasmaTheme.withAlpha(c.outlineVariant.toArgb(), 0x80),
            shadow = 0x8C000000.toInt(),
        )
    }

    /** Deep navy with a prism of light sweeping across, after the Pixel laptop wallpaper. */
    fun wallpaper(width: Int, height: Int, dark: Boolean): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val w = width.toFloat()
        val h = height.toFloat()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, w, h,
            if (dark) intArrayOf(0xFF0A1530.toInt(), 0xFF0B1E4A.toInt(), 0xFF02040C.toInt())
            else intArrayOf(0xFFD7E3FF.toInt(), 0xFFAFC6FF.toInt(), 0xFFE8EDFA.toInt()),
            floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        // The band of light: a broad arc from the lower left to the upper right.
        val band = Path().apply {
            moveTo(-w * .05f, h * 1.05f)
            cubicTo(w * .25f, h * .78f, w * .48f, h * .45f, w * .66f, -h * .05f)
            lineTo(w * .74f, -h * .05f)
            cubicTo(w * .58f, h * .5f, w * .36f, h * .86f, w * .08f, h * 1.05f)
            close()
        }
        paint.shader = LinearGradient(w * .2f, h * .9f, w * .72f, 0f,
            intArrayOf(0x002B6BFF, 0xCC2B6BFF.toInt(), 0xE600C2D1.toInt(), 0xE6FFE15A.toInt(), 0xE6FF7A3D.toInt(), 0xCCC04BFF.toInt(), 0x00C04BFF),
            floatArrayOf(0f, .18f, .38f, .55f, .7f, .86f, 1f), Shader.TileMode.CLAMP)
        paint.maskFilter = BlurMaskFilter((w * .018f).coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
        canvas.drawPath(band, paint)
        paint.maskFilter = null
        // A bright core along its inner edge.
        val core = Path().apply {
            moveTo(w * .02f, h * 1.05f)
            cubicTo(w * .3f, h * .8f, w * .5f, h * .48f, w * .7f, -h * .05f)
        }
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = w * .004f
        paint.color = 0x99FFFFFF.toInt()
        paint.maskFilter = BlurMaskFilter((w * .004f).coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
        canvas.drawPath(core, paint)
        paint.maskFilter = null
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(w * .7f, h * .1f, w * .45f, intArrayOf(0x33FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        return bitmap
    }
}

/** Compose needs a lifecycle and saved state; the compositor's overlay has no activity to lend them. */
private class BookOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry
    override val viewModelStore = ViewModelStore()

    init {
        saved.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}

/**
 * The Compose half of Dextop Book: the status bar and the dock live in the compositor's root,
 * the launcher in its popup layer. The shell calls [refresh] whenever tasks or status change.
 */
internal class BookUi(
    private val context: Context,
    private val panel: PanelHost,
    private val launcher: LauncherHost,
) {
    private val owner = BookOwner()
    private val density = context.resources.displayMetrics.density
    private var tasks by mutableStateOf(emptyList<PanelTask>())
    private var tray by mutableStateOf(panel.tray())
    private var notifications by mutableIntStateOf(0)
    private var shown by mutableStateOf(true)
    private var scheme by mutableStateOf(BookTheme.scheme(context, panel.palette.dark))
    var launcherOpen by mutableStateOf(false)
    /** Where each dock icon is on screen, for minimize animations. */
    private val iconRects = HashMap<String, RectF>()
    private var launcherRect: RectF? = null

    val topInset: Int get() = (TOP_BAR.value * density).toInt()
    val bottomInset: Int get() = ((DOCK + DOCK_MARGIN * 2).value * density).toInt()

    private var statusBar: View? = null
    private var dockHolder: FrameLayout? = null

    fun install(root: FrameLayout) {
        root.setViewTreeLifecycleOwner(owner)
        root.setViewTreeSavedStateRegistryOwner(owner)
        root.setViewTreeViewModelStoreOwner(owner)
        statusBar = compose { StatusBar() }.also {
            root.addView(it, FrameLayout.LayoutParams(-1, topInset, android.view.Gravity.TOP or android.view.Gravity.START))
        }
        // The dock is centred within its screen; the holder is that screen's width.
        dockHolder = FrameLayout(context).also { holder ->
            holder.addView(compose { Dock() }, FrameLayout.LayoutParams(-2, -2,
                android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL))
            root.addView(holder, FrameLayout.LayoutParams(-1, -2, android.view.Gravity.BOTTOM or android.view.Gravity.START))
        }
        refresh()
    }

    /** On an extended desktop: the bars and the dock belong to [output] (the first screen). */
    fun place(output: android.graphics.Rect) {
        listOfNotNull(statusBar, dockHolder).forEach { view ->
            (view.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                params.width = output.width()
                params.leftMargin = output.left
                view.layoutParams = params
            }
        }
    }

    fun destroy() = owner.destroy()

    fun refresh() {
        tasks = panel.panelEntries()
        tray = panel.tray()
        notifications = NotificationCenter.count()
    }

    fun recolor() {
        scheme = BookTheme.scheme(context, panel.palette.dark)
    }

    /** Hidden while a Linux app is full screen. */
    fun setVisible(value: Boolean) {
        shown = value
    }

    fun iconRect(packageName: String): RectF? = iconRects[packageName]?.let(::RectF)
    fun launcherRect(): RectF? = launcherRect?.let(::RectF)

    fun launcherView(): View = compose { Launcher() }

    private fun compose(content: @Composable () -> Unit) = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setContent { MaterialExpressiveTheme(colorScheme = scheme, motionScheme = MotionScheme.expressive()) { content() } }
    }

    /** Screen bounds of a composable (the compositor works in display coordinates). */
    @Composable
    private fun onScreen(update: (RectF) -> Unit): Modifier {
        val view = LocalView.current
        return Modifier.onGloballyPositioned { coordinates: LayoutCoordinates ->
            val screen = IntArray(2).also(view::getLocationOnScreen)
            val window = IntArray(2).also(view::getLocationInWindow)
            val b = coordinates.boundsInWindow()
            update(RectF(b.left + screen[0] - window[0], b.top + screen[1] - window[1],
                b.right + screen[0] - window[0], b.bottom + screen[1] - window[1]))
        }
    }

    // region status bar

    @Composable
    private fun StatusBar() {
        if (!shown) return
        val now by produceState(Date()) {
            while (true) {
                value = Date()
                delay(1_000L - System.currentTimeMillis() % 1_000L)
            }
        }
        // Over the wallpaper: light text with a soft shadow.
        val style = MaterialTheme.typography.labelLarge.copy(color = Color.White,
            shadow = Shadow(Color(0x80000000), Offset(0f, 1f), 4f))
        var clock = RectF()
        var status = RectF()
        Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.clip(CircleShape)
                    .then(onScreen { clock = it })
                    .combinedClickable { panel.onClockClicked(clock) }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(DateFormat.getTimeFormat(context).format(now), style = style)
                Text(DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMMMd"), now).toString(), style = style)
            }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.clip(CircleShape)
                    .then(onScreen { status = it })
                    .combinedClickable { panel.onTrayClicked(TrayItem.EXPAND, status) }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (notifications > 0) GlyphIcon(Glyph.NOTIFICATIONS, Color.White, 16.dp)
                GlyphIcon(tray.networkGlyph, Color.White, 16.dp, tray.networkLevel)
                GlyphIcon(if (tray.charging) Glyph.BATTERY_CHARGING else Glyph.BATTERY, Color.White, 16.dp, tray.battery)
                Text(percentText(tray.battery), style = style)
            }
        }
    }
    // endregion

    // region dock

    @Composable
    private fun Dock() {
        if (!shown) return
        HorizontalFloatingToolbar(expanded = true, modifier = Modifier.padding(DOCK_MARGIN)) {
            LauncherButton()
            VerticalDivider(Modifier.height(28.dp).padding(horizontal = 6.dp))
            tasks.forEach { entry -> DockItem(entry) }
        }
    }

    @Composable
    private fun LauncherButton() {
        val colors = MaterialTheme.colorScheme
        // The launcher's button turns into the cookie shape while it is open.
        val shape = if (launcherOpen) MaterialShapes.Cookie9Sided.toShape() else CircleShape
        val scale by animateFloatAsState(if (launcherOpen) 1.08f else 1f, MaterialTheme.motionScheme.fastSpatialSpec())
        var anchor = RectF()
        Box(
            Modifier.size(48.dp).scale(scale).clip(shape).background(colors.primary)
                .then(onScreen { anchor = it; launcherRect = it })
                .combinedClickable { panel.onLauncherClicked(anchor) },
            contentAlignment = Alignment.Center,
        ) { GlyphIcon(Glyph.APPS, colors.onPrimary, 24.dp) }
    }

    @Composable
    private fun DockItem(entry: PanelTask) {
        val colors = MaterialTheme.colorScheme
        val running = entry.windows.isNotEmpty()
        var anchor = RectF()
        val indicator by animateDpAsState(when {
            entry.active -> 16.dp
            running -> 6.dp
            else -> 0.dp
        }, MaterialTheme.motionScheme.fastSpatialSpec())
        val scale by animateFloatAsState(if (entry.active) 1f else .94f, MaterialTheme.motionScheme.fastSpatialSpec())
        Column(Modifier.padding(horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(48.dp).scale(scale)
                    .clip(if (entry.active) MaterialShapes.Cookie9Sided.toShape() else RoundedCornerShape(16.dp))
                    .background(if (entry.active) colors.secondaryContainer else Color.Transparent)
                    .then(onScreen { anchor = it; iconRects[entry.packageName] = it })
                    .pointerButtons(
                        onSecondary = { panel.onTaskContextMenu(entry, anchor) },
                        onTertiary = { panel.onTaskMiddleClicked(entry) },
                        onHover = { inside -> panel.onTaskHover(if (inside) entry else null, if (inside) anchor else null) },
                    )
                    .combinedClickable(
                        onLongClick = { panel.onTaskContextMenu(entry, anchor) },
                        onClick = { panel.onTaskClicked(entry, anchor) },
                    ),
                contentAlignment = Alignment.Center,
            ) { DrawableIcon(entry.icon, 36.dp, if (running || entry.pinned) 255 else 160) }
            Box(Modifier.padding(top = 2.dp).height(3.dp).width(indicator).clip(CircleShape)
                .background(if (entry.active) colors.primary else colors.onSurfaceVariant))
        }
    }
    // endregion

    // region launcher

    @Composable
    private fun Launcher() {
        val colors = MaterialTheme.colorScheme
        var query by remember { mutableStateOf("") }
        val focus = remember { FocusRequester() }
        val apps = remember(query) {
            if (query.isBlank()) launcher.apps().sortedBy { it.label.lowercase() } else PlasmaSearch.apps(launcher, query)
        }
        fun open(app: PlasmaApp) {
            launcher.dismissPopup()
            launcher.launch(app.packageName)
        }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        Surface(Modifier.fillMaxSize(), color = Color.Transparent) {
            Column(Modifier.fillMaxSize().padding(20.dp)) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    singleLine = true,
                    shape = CircleShape,
                    placeholder = { Text(t("nativePlasmaSearch")) },
                    leadingIcon = { GlyphIcon(Glyph.SEARCH, colors.onSurfaceVariant, 22.dp) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { apps.firstOrNull()?.let(::open) }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = colors.surfaceContainerHighest,
                        unfocusedContainerColor = colors.surfaceContainerHighest,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
                Text(
                    if (query.isBlank()) t("nativePlasmaAllApplications") else t("nativePlasmaSearch"),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 8.dp),
                )
                LazyVerticalGrid(GridCells.Adaptive(96.dp), Modifier.fillMaxWidth().weight(1f)) {
                    items(apps, key = { it.packageName }) { app -> AppCell(app) { open(app) } }
                }
            }
        }
    }

    @Composable
    private fun AppCell(app: PlasmaApp, open: () -> Unit) {
        var anchor = RectF()
        fun menu() {
            val pinned = launcher.isPinned(app.packageName)
            launcher.showMenu(listOf(
                MenuItem(app.label, header = true),
                MenuItem(t(if (pinned) "nativePlasmaUnpinFromTaskManager" else "nativePlasmaPinToTaskManager"), Glyph.PIN) {
                    launcher.setPinned(app.packageName, !pinned)
                    refresh()
                },
                MenuItem(t("nativePlasmaAppInfo"), Glyph.INFO) { launcher.openAppInfo(app.packageName) },
            ), anchor.centerX(), anchor.centerY())
        }
        Column(
            Modifier.padding(4.dp).clip(RoundedCornerShape(20.dp))
                .then(onScreen { anchor = it })
                .pointerButtons(onSecondary = ::menu)
                .combinedClickable(onLongClick = ::menu, onClick = open)
                .padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DrawableIcon(app.icon, 48.dp)
            Text(
                app.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
        }
    }
    // endregion

    // region drawing helpers

    /** Right and middle mouse buttons and hover, which plain clicks do not report. */
    private fun Modifier.pointerButtons(
        onSecondary: () -> Unit = {},
        onTertiary: () -> Unit = {},
        onHover: ((Boolean) -> Unit)? = null,
    ) = pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                when (event.type) {
                    PointerEventType.Press -> when {
                        event.buttons.isSecondaryPressed -> { event.changes.forEach { it.consume() }; onSecondary() }
                        event.buttons.isTertiaryPressed -> { event.changes.forEach { it.consume() }; onTertiary() }
                    }
                    PointerEventType.Enter -> onHover?.invoke(true)
                    PointerEventType.Exit -> onHover?.invoke(false)
                }
            }
        }
    }

    @Composable
    private fun DrawableIcon(drawable: Drawable?, size: Dp, alpha: Int = 255) {
        androidx.compose.foundation.Canvas(Modifier.size(size)) {
            val d = drawable ?: return@Canvas
            drawIntoCanvas { canvas ->
                d.setBounds(0, 0, this.size.width.toInt(), this.size.height.toInt())
                d.alpha = alpha
                d.draw(canvas.nativeCanvas)
                d.alpha = 255
            }
        }
    }

    @Composable
    private fun GlyphIcon(glyph: Glyph, tint: Color, size: Dp, level: Float = 1f) {
        val drawable = remember(glyph) { GlyphDrawable(glyph, 0, 1.6f, true) }
        androidx.compose.foundation.Canvas(Modifier.size(size)) {
            drawable.color = tint.toArgb()
            drawable.level01 = level
            drawIntoCanvas { canvas ->
                drawable.setBounds(0, 0, this.size.width.toInt(), this.size.height.toInt())
                drawable.draw(canvas.nativeCanvas)
            }
        }
    }
    // endregion

    private companion object {
        val TOP_BAR = 32.dp
        val DOCK = 64.dp
        val DOCK_MARGIN = 8.dp
    }
}
