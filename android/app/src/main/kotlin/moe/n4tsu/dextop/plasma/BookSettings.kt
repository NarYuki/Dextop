@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.n4tsu.dextop.plasma

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** A settings row, typed for [BookGroup]. */
private fun row(content: @Composable (Shape) -> Unit) = content

/** A search field in Book's pill style. */
@Composable
internal fun SearchPill(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    TextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        shape = CircleShape,
        placeholder = { Text(placeholder) },
        leadingIcon = { Sym("search", colors.onSurfaceVariant, 22.dp) },
        trailingIcon = if (value.isNotEmpty()) ({ BookIconButton("close", { onChange("") }) }) else null,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = colors.surfaceContainerHighest,
            unfocusedContainerColor = colors.surfaceContainerHighest,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

/** A navigation entry of a left pane: icon in a tonal circle, title and summary. */
@Composable
internal fun NavCard(symbol: String, title: String, summary: String?, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    var hovered by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(when {
                selected -> colors.secondaryContainer
                hovered -> colors.surfaceContainerHighest
                else -> colors.surfaceContainerHigh
            })
            .pointerButtons(onHover = { hovered = it })
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(if (selected) colors.primary else colors.primaryContainer),
            contentAlignment = Alignment.Center) {
            Sym(symbol, if (selected) colors.onPrimary else colors.onPrimaryContainer, 22.dp, filled = selected)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (summary != null) Text(summary, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Dextop Book's settings, a window of Book itself: Book's own options (wallpaper and style,
 * taskbar, status bar, desktop, widgets, windows) by category, with a search field.
 */
internal class BookSettingsContent(
    private val context: Context,
    private val host: BookHost,
    private val widgets: BookAppWidgets,
    private val removeWidget: (Int) -> Unit,
    private val linuxInstalled: () -> Boolean,
) {
    var page by mutableStateOf("style")
    private var query by mutableStateOf("")
    private var tick by mutableIntStateOf(0)

    /** The store changed (from the desktop or here). */
    fun changed() { tick++ }

    private class Page(val id: String, val symbol: String, val title: String, val summary: String)

    private val pages get() = listOf(
        Page("style", "palette", tr("壁紙とスタイル", "Wallpaper & style"), tr("壁紙、ダークモード", "Wallpaper, dark theme")),
        Page("taskbar", "dock_to_bottom", tr("タスクバー", "Taskbar"), tr("表示、固定したアプリ", "Visibility, pinned apps")),
        Page("statusbar", "toolbar", tr("ステータスバー", "Status bar"), tr("ライブ アクティビティ、アイコン", "Live activity, icons")),
        Page("desktop", "grid_view", tr("デスクトップ", "Desktop"), tr("アイコン、ショートカット", "Icons, shortcuts")),
        Page("widgets", "widgets", tr("ウィジェット", "Widgets"), tr("追加、削除", "Add, remove")),
        Page("windows", "web_asset", tr("ウィンドウ", "Windows"), tr("ホットコーナー、仮想デスクトップ、アニメーション", "Hot corner, virtual desktops, animations")),
        Page("experimental", "interests", tr("試験運用中の機能", "Experimental"), tr("Linux のデスクトップ フォルダ", "Linux desktop folder")),
        Page("about", "info", tr("Dextop Book について", "About Dextop Book"), tr("バージョン", "Version")),
    )

    private val searchIndex get() = listOf(
        "style" to tr("壁紙", "Wallpaper"), "style" to tr("ダークモード", "Dark theme"), "style" to tr("表示サイズ", "Display size"),
        "taskbar" to tr("タスクバーを常に表示", "Always show taskbar"), "taskbar" to tr("固定したアプリ", "Pinned apps"),
        "statusbar" to tr("ライブ アクティビティ", "Live activity"), "statusbar" to tr("通知アイコン", "Notification icons"),
        "statusbar" to tr("電池残量（%）", "Battery percentage"),
        "desktop" to tr("デスクトップ アイコン", "Desktop icons"), "desktop" to tr("ショートカット", "Shortcuts"),
        "widgets" to tr("ウィジェットを追加", "Add widgets"),
        "windows" to tr("ホットコーナー", "Hot corner"), "windows" to tr("仮想デスクトップ", "Virtual desktops"),
        "windows" to tr("アニメーション", "Animations"), "about" to tr("バージョン", "Version"),
        "experimental" to tr("Linux のデスクトップ フォルダ", "Linux desktop folder"),
    )

    private val settings get() = host.settings()
    private fun apply(next: PlasmaSettings) { host.applySettings(next); tick++ }

    @Composable
    fun Content() {
        @Suppress("UNUSED_EXPRESSION") tick
        val colors = MaterialTheme.colorScheme
        Row(Modifier.fillMaxSize().background(colors.surfaceContainerLow)) {
            Column(Modifier.width(340.dp).fillMaxHeight().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchPill(query, { query = it }, tr("設定を検索", "Search settings"))
                Spacer(Modifier.height(4.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(pages, key = { it.id }) { p ->
                        NavCard(p.symbol, p.title, p.summary, query.isEmpty() && page == p.id) { page = p.id; query = "" }
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight().padding(top = 16.dp, end = 16.dp, bottom = 16.dp)
                .clip(RoundedCornerShape(24.dp)).background(colors.surface)) {
                AnimatedContent(if (query.isNotEmpty()) "search" else page, transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "page") { shown ->
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp)) {
                        if (shown == "search") SearchResults() else {
                            Text(pages.firstOrNull { it.id == shown }?.title.orEmpty(), style = MaterialTheme.typography.headlineSmall,
                                color = colors.onSurface, modifier = Modifier.padding(bottom = 20.dp))
                            when (shown) {
                                "style" -> StylePage()
                                "taskbar" -> TaskbarPage()
                                "statusbar" -> StatusBarPage()
                                "desktop" -> DesktopPage()
                                "widgets" -> WidgetsPage()
                                "windows" -> WindowsPage()
                                "experimental" -> ExperimentalPage()
                                else -> AboutPage()
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun SearchResults() {
        val needle = query.trim().lowercase()
        val hits = searchIndex.filter { (_, name) -> name.lowercase().contains(needle) } +
            pages.filter { it.title.lowercase().contains(needle) }.map { it.id to it.title }
        Text(tr("検索結果", "Search results"), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 20.dp))
        if (hits.isEmpty()) {
            Text(tr("一致する設定はありません", "No matching settings"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            return
        }
        BookGroup(rows = hits.distinct().map { (id, name) ->
            val p = pages.first { it.id == id }
            row { shape -> BookListItem(name, p.title, p.symbol, shape, onClick = { page = id; query = "" }) }
        })
    }

    @Composable
    private fun Section(title: String) = Text(title, style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))

    @Composable
    private fun StylePage() {
        val colors = MaterialTheme.colorScheme
        val dark = BookTheme.dark(context, settings)
        val preview = remember(dark) { BookTheme.wallpaper(640, 360, dark) }
        Section(tr("壁紙", "Wallpaper"))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(bottom = 24.dp)) {
            WallpaperChoice(Modifier.weight(1f), "Dextop Book", preview, settings.wallpaper != "system") {
                apply(settings.copy(wallpaper = "plasma"))
            }
            WallpaperChoice(Modifier.weight(1f), tr("スマートフォンの壁紙", "Phone wallpaper"), null, settings.wallpaper == "system") {
                apply(settings.copy(wallpaper = "system"))
            }
        }
        Section(tr("表示サイズ", "Display size"))
        val scale = BookStore.displayScale(context)
        val scales = listOf(0 to tr("自動", "Auto"), 100 to "100%", 125 to "125%", 150 to "150%", 175 to "175%", 200 to "200%")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            scales.forEachIndexed { index, (value, label) ->
                SegmentedButton(selected = scale == value, onClick = {
                    BookStore.setDisplayScale(context, value)
                    tick++
                    moe.n4tsu.dextop.MirrorService.displayScaleChanged()
                }, shape = SegmentedButtonDefaults.itemShape(index, scales.size)) { Text(label) }
            }
        }
        Text(tr("外部ディスプレイでの文字やウィンドウの大きさ。自動はモニターの実際の大きさから決めます（96 dpi で 100%）。",
            "How large text and windows are on the external screen. Auto follows the monitor's physical size (96 dpi is 100 %)."),
            style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, bottom = 20.dp))
        Section(tr("ダークモード", "Dark theme"))
        val options = listOf("system" to tr("システムに合わせる", "Match system"), "light" to tr("ライト", "Light"), "dark" to tr("ダーク", "Dark"))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
            options.forEachIndexed { index, (value, label) ->
                SegmentedButton(selected = settings.colorScheme == value, onClick = { apply(settings.copy(colorScheme = value)) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size)) { Text(label) }
            }
        }
        Text(tr("色は Android の壁紙の色（Material You）から作られます。", "Colours come from your Android wallpaper (Material You)."),
            style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp))
    }

    @Composable
    private fun WallpaperChoice(modifier: Modifier, title: String, preview: android.graphics.Bitmap?, selected: Boolean, onClick: () -> Unit) {
        val colors = MaterialTheme.colorScheme
        Column(modifier.clip(RoundedCornerShape(24.dp)).clickable(onClick = onClick)) {
            Box(Modifier.fillMaxWidth().aspectRatio(16 / 9f).clip(RoundedCornerShape(24.dp))
                .border(if (selected) 4.dp else 0.dp, if (selected) colors.primary else Color.Transparent, RoundedCornerShape(24.dp))
                .background(colors.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                if (preview != null) Image(preview.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Sym("smartphone", colors.onSurfaceVariant, 48.dp)
                if (selected) Box(Modifier.align(Alignment.TopEnd).padding(10.dp).size(28.dp).clip(CircleShape).background(colors.primary),
                    contentAlignment = Alignment.Center) { Sym("check", colors.onPrimary, 18.dp) }
            }
            Text(title, style = MaterialTheme.typography.titleSmall, color = colors.onSurface, modifier = Modifier.padding(12.dp))
        }
    }

    @Composable
    private fun TaskbarPage() {
        BookGroup(null, listOf(
            { shape -> BookSwitchItem(tr("タスクバーを常に表示", "Always show taskbar"),
                BookStore.flag(context, BookStore.ALWAYS_SHOW_TASKBAR, true), { BookStore.setFlag(context, BookStore.ALWAYS_SHOW_TASKBAR, it); tick++ },
                shape, summary = tr("オフにすると、ウィンドウと重なるときに隠れ、画面の下端で表示されます",
                    "When off, it hides while a window overlaps it and comes back at the bottom edge"), symbol = "dock_to_bottom") },
        ))
        val pinned = host.pinnedApps()
        Section(tr("固定したアプリ", "Pinned apps"))
        if (pinned.isEmpty()) Text(tr("ランチャーからアプリをタスクバーへドラッグすると固定できます", "Drag apps from the launcher onto the taskbar to pin them"),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp))
        else BookGroup(null, pinned.map { app ->
            row { shape -> AppRow(app.icon, app.label, shape) { BookIconButton("close", { host.setPinned(app.packageName, false); tick++ }) } }
        })
    }

    @Composable
    private fun AppRow(icon: android.graphics.drawable.Drawable?, label: String, shape: Shape, trailing: @Composable () -> Unit) {
        Row(Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            DrawableIcon(icon, 36.dp)
            Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            trailing()
        }
    }

    @Composable
    private fun StatusBarPage() {
        BookGroup(null, listOf(
            { shape -> BookSwitchItem(tr("ライブ アクティビティ", "Live activity"), BookStore.flag(context, BookStore.LIVE_ACTIVITY, true),
                { BookStore.setFlag(context, BookStore.LIVE_ACTIVITY, it); tick++ }, shape,
                summary = tr("再生中のメディアをステータスバーに表示", "Shows what's playing in the status bar"), symbol = "graphic_eq") },
            { shape -> BookSwitchItem(tr("通知アイコン", "Notification icons"), BookStore.flag(context, BookStore.NOTIFICATION_ICONS, true),
                { BookStore.setFlag(context, BookStore.NOTIFICATION_ICONS, it); tick++ }, shape,
                summary = tr("通知のあるアプリを時計の横に表示", "Shows apps with notifications next to the clock"), symbol = "notifications") },
            { shape -> BookSwitchItem(tr("電池残量（%）", "Battery percentage"), settings.trayBatteryPercent,
                { apply(settings.copy(trayBatteryPercent = it)) }, shape, symbol = "battery_full") },
        ))
    }

    @Composable
    private fun DesktopPage() {
        val apps = host.apps().associateBy { it.packageName }
        BookGroup(null, listOf(
            { shape -> BookSwitchItem(tr("デスクトップ アイコンを表示", "Show desktop icons"), BookStore.flag(context, BookStore.DESKTOP_ICONS, true),
                { BookStore.setFlag(context, BookStore.DESKTOP_ICONS, it); tick++ }, shape,
                summary = tr("ランチャーからアプリをデスクトップへドラッグすると追加できます", "Drag apps from the launcher onto the desktop to add them"),
                symbol = "grid_view") },
        ))
        val shortcuts = BookStore.shortcuts(context)
        Section(tr("ショートカット", "Shortcuts"))
        if (shortcuts.isEmpty()) Text(tr("デスクトップにショートカットはありません", "No shortcuts on the desktop"),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp))
        else BookGroup(null, shortcuts.map { shortcut ->
            row { shape ->
                AppRow(apps[shortcut.packageName]?.icon, apps[shortcut.packageName]?.label ?: shortcut.packageName, shape) {
                    BookIconButton("delete", { BookStore.removeShortcut(context, shortcut.packageName); tick++ })
                }
            }
        })
    }

    @Composable
    private fun WidgetsPage() {
        val placed = BookStore.widgets(context)
        BookGroup(null, buildList<@Composable (Shape) -> Unit> {
            add { shape -> BookListItem(tr("ウィジェットを追加", "Add widgets"), tr("Android アプリのウィジェットを選ぶ", "Pick an Android app widget"),
                "add_circle", shape, onClick = { host.openWidgetGallery() }) }
            placed.forEach { widget ->
                val info = widgets.info(widget.appWidgetId)
                add { shape ->
                    AppRow(info?.let(widgets::appIcon), info?.let(widgets::label) ?: tr("使用できないウィジェット", "Unavailable widget"), shape) {
                        BookIconButton("delete", { removeWidget(widget.appWidgetId); tick++ })
                    }
                }
            }
        })
    }

    @Composable
    private fun WindowsPage() {
        BookGroup(null, listOf(
            { shape -> BookSwitchItem(tr("ホットコーナー", "Hot corner"), settings.hotCorner, { apply(settings.copy(hotCorner = it)) }, shape,
                summary = tr("左上の角でウィンドウの概要を開く", "The top-left corner opens the overview"), symbol = "open_with") },
            { shape -> BookListItem(tr("仮想デスクトップ", "Virtual desktops"), trf("{0} 個", "{0}", settings.virtualDesktops), "select_all", shape) {
                BookIconButton("remove", { apply(settings.copy(virtualDesktops = (settings.virtualDesktops - 1).coerceAtLeast(1))) })
                BookIconButton("add", { apply(settings.copy(virtualDesktops = (settings.virtualDesktops + 1).coerceAtMost(6))) })
            } },
            { shape -> BookSwitchItem(tr("アニメーション", "Animations"), settings.animationSpeed > 0f,
                { apply(settings.copy(animationSpeed = if (it) 1f else 0f)) }, shape, symbol = "air") },
        ))
    }

    @Composable
    private fun ExperimentalPage() {
        val installed = linuxInstalled()
        Text(tr("これらの機能は開発中で、動作が変わったり不安定だったりすることがあります。",
            "These features are in development and may change or misbehave."),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, bottom = 16.dp))
        BookGroup(null, listOf(
            { shape -> BookSwitchItem(tr("Linux のデスクトップ フォルダ", "Linux desktop folder"),
                installed && BookStore.flag(context, BookStore.LINUX_DESKTOP_FOLDER, false),
                { BookStore.setFlag(context, BookStore.LINUX_DESKTOP_FOLDER, it); tick++ }, shape,
                summary = if (installed) tr("Linux コンテナの ~/Desktop をデスクトップに表示し、フォルダを作成・名前変更・削除できます",
                    "Shows the Linux container's ~/Desktop on the desktop, where folders can be made, renamed and removed")
                else tr("Linux コンテナをインストールすると使えます", "Available once a Linux container is installed"),
                symbol = "folder", enabled = installed) },
        ))
    }

    @Composable
    private fun AboutPage() {
        val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() }
        BookGroup(null, listOf(
            { shape -> BookListItem(tr("Dextop のバージョン", "Dextop version"), version ?: "—", "description", shape) },
            { shape -> BookListItem(tr("デバイス", "Device"), "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}", "smartphone", shape) },
        ))
    }
}

/**
 * Book's widget picker, a window of Book itself: Android app widgets by app on the left, their
 * previews on the right. A widget is added with a click, or dragged straight onto the desktop.
 */
internal class BookWidgetPicker(
    private val widgets: BookAppWidgets,
    private val add: (AppWidgetProviderInfo) -> Unit,
    private val drag: BookDragSource,
) {
    private var app by mutableStateOf<String?>(null)
    private var query by mutableStateOf("")

    @Composable
    fun Content() {
        val colors = MaterialTheme.colorScheme
        val providers = remember { widgets.providers().sortedBy { widgets.appLabel(it).lowercase() } }
        val apps = remember(providers) { providers.groupBy { it.provider.packageName } }
        Row(Modifier.fillMaxSize().background(colors.surfaceContainerLow)) {
            Column(Modifier.width(320.dp).fillMaxHeight().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchPill(query, { query = it }, tr("ウィジェットを検索", "Search widgets"))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        NavCard("star", tr("すべて", "All"), trf("{0} 個のウィジェット", "{0} widgets", providers.size), app == null) { app = null }
                    }
                    items(apps.keys.toList(), key = { it }) { pkg ->
                        val first = apps.getValue(pkg).first()
                        AppNav(widgets.appIcon(first), widgets.appLabel(first), apps.getValue(pkg).size, app == pkg) { app = pkg }
                    }
                }
            }
            val shown = providers.filter { (app == null || it.provider.packageName == app) &&
                (query.isBlank() || widgets.label(it).contains(query, true) || widgets.appLabel(it).contains(query, true)) }
            Box(Modifier.weight(1f).fillMaxHeight().padding(top = 16.dp, end = 16.dp, bottom = 16.dp)
                .clip(RoundedCornerShape(24.dp)).background(colors.surface)) {
                if (shown.isEmpty()) Text(tr("ウィジェットが見つかりません", "No widgets found"), color = colors.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center))
                LazyVerticalGrid(GridCells.Adaptive(220.dp), Modifier.fillMaxSize().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(shown, key = { it.provider.flattenToString() }) { info -> WidgetCard(info) }
                }
            }
        }
    }

    @Composable
    private fun AppNav(icon: android.graphics.drawable.Drawable?, label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
        val colors = MaterialTheme.colorScheme
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(if (selected) colors.secondaryContainer else colors.surfaceContainerHigh)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            DrawableIcon(icon, 36.dp)
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleSmall, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(trf("{0} 個のウィジェット", "{0} widgets", count), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
    }

    @Composable
    private fun WidgetCard(info: AppWidgetProviderInfo) {
        val colors = MaterialTheme.colorScheme
        val preview = remember(info) { widgets.preview(info) }
        var hovered by remember { mutableStateOf(false) }
        Column(
            Modifier.clip(RoundedCornerShape(24.dp))
                .background(if (hovered) colors.surfaceContainerHighest else colors.surfaceContainerHigh)
                .pointerButtons(onHover = { hovered = it })
                .then(drag.modifier(BookDragItem.NewWidget(info, preview)) { add(info) })
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                DrawableIcon(preview, 140.dp, modifier = Modifier.clip(RoundedCornerShape(16.dp)))
            }
            Text(widgets.label(info), style = MaterialTheme.typography.titleSmall, color = colors.onSurface, maxLines = 2,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
            Text(widgets.appLabel(info), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1)
        }
    }
}
