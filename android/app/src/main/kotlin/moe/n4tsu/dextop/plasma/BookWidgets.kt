package moe.n4tsu.dextop.plasma

import android.app.ActivityOptions
import android.app.PendingIntent
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** What Book's panels and its built-in windows (Settings, Widgets) ask of the running desktop. */
internal interface BookHost {
    val controls: PlasmaSystemControls
    fun background(work: () -> Unit)
    fun openBookSettings(page: String? = null)
    fun openWidgetGallery()
    fun openSystemSettings(intent: Intent)
    fun closePanel()
    fun launch(packageName: String)
    fun leave(action: LeaveAction)
    fun settings(): PlasmaSettings
    fun applySettings(next: PlasmaSettings)
    fun refreshTray()
    fun apps(): List<PlasmaApp>
    fun appInfo(packageName: String)
    fun pinnedApps(): List<PanelTask>
    fun isPinned(packageName: String): Boolean
    fun setPinned(packageName: String, pinned: Boolean)
    fun openNotification(item: DextopNotification)
    fun dismissNotification(item: DextopNotification)
}

/** One cell area of the desktop grid: column and row from the top left, and the span. */
internal data class GridSpot(val column: Int, val row: Int, val columns: Int = 1, val rows: Int = 1) {
    fun overlaps(other: GridSpot) = column < other.column + other.columns && column + columns > other.column &&
        row < other.row + other.rows && row + rows > other.row
}

/** A shortcut on the desktop. */
internal data class DesktopShortcut(val packageName: String, val spot: GridSpot)

/** An Android app widget on the desktop. */
internal data class DesktopWidget(val appWidgetId: Int, val spot: GridSpot)

/**
 * Book's own desktop and options: shortcuts, app widgets and the taskbar and status-bar
 * switches. [listen] reports every change.
 */
internal object BookStore {
    private const val FILE = "dextop_book"
    private fun prefs(context: Context): SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun listen(context: Context, listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs(context).registerOnSharedPreferenceChangeListener(listener)

    fun unlisten(context: Context, listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs(context).unregisterOnSharedPreferenceChangeListener(listener)

    fun flag(context: Context, key: String, default: Boolean) = prefs(context).getBoolean(key, default)
    fun setFlag(context: Context, key: String, value: Boolean) = prefs(context).edit().putBoolean(key, value).apply()

    const val ALWAYS_SHOW_TASKBAR = "always_show_taskbar"
    const val DESKTOP_ICONS = "desktop_icons"
    const val LIVE_ACTIVITY = "live_activity"
    const val NOTIFICATION_ICONS = "notification_icons"
    /** Experimental: the Linux container's ~/Desktop on the desktop. */
    const val LINUX_DESKTOP_FOLDER = "linux_desktop_folder"

    private fun desktopCache(context: Context) = context.getSharedPreferences("book_desktop_cache", Context.MODE_PRIVATE)
    private fun desktopCacheMode(context: Context) = moe.n4tsu.dextop.linux.LinuxExecutionMode.read(context)

    /** Names and types only; contents stay in Linux. Grid positions live in grid_shortcuts. */
    fun cachedLinuxDesktop(context: Context, container: String? = null): Pair<String, List<Pair<String, Boolean>>>? {
        val prefs = desktopCache(context)
        val mode = desktopCacheMode(context)
        val id = container ?: prefs.getString("$mode.active", null) ?: return null
        val files = prefs.getString("$mode.$id", null)?.let(BookDesktopFiles::parse) ?: return null
        return id to files
    }

    fun cacheLinuxDesktop(context: Context, container: String, files: List<Pair<String, Boolean>>) {
        val prefs = desktopCache(context)
        val mode = desktopCacheMode(context)
        val encoded = BookDesktopFiles.encode(files)
        if (prefs.getString("$mode.$container", null) == encoded && prefs.getString("$mode.active", null) == container) return
        prefs.edit().putString("$mode.$container", encoded).putString("$mode.active", container).apply()
    }

    /** Called only after an authoritative container listing, never on connection failure. */
    fun pruneLinuxDesktopCache(context: Context, containers: Set<String>) {
        val prefs = desktopCache(context)
        val prefix = "${desktopCacheMode(context)}."
        val obsolete = prefs.all.keys.filter { it.startsWith(prefix) && it != "${prefix}active" && it.removePrefix(prefix) !in containers }
        val active = prefs.getString("${prefix}active", null)
        if (obsolete.isEmpty() && (active == null || active in containers)) return
        val edit = prefs.edit()
        obsolete.forEach(edit::remove)
        if (active !in containers) edit.remove("${prefix}active")
        edit.apply()
    }

    /** The external screen's scale in percent; 0 follows its physical size. */
    fun displayScale(context: Context): Int = prefs(context).getInt("display_scale", 0)
    fun setDisplayScale(context: Context, percent: Int) = prefs(context).edit().putInt("display_scale", percent).commit()

    private fun spot(text: String): GridSpot? = text.split(',').mapNotNull { it.toIntOrNull() }.takeIf { it.size == 4 }
        ?.let { GridSpot(it[0], it[1], it[2], it[3]) }

    private fun text(spot: GridSpot) = "${spot.column},${spot.row},${spot.columns},${spot.rows}"

    fun shortcuts(context: Context): List<DesktopShortcut> =
        prefs(context).getString("grid_shortcuts", "").orEmpty().split(';').mapNotNull { entry ->
            val parts = entry.split('@')
            DesktopShortcut(parts[0].takeIf { it.isNotBlank() } ?: return@mapNotNull null, spot(parts.getOrNull(1).orEmpty()) ?: return@mapNotNull null)
        }

    fun saveShortcuts(context: Context, list: List<DesktopShortcut>) = prefs(context).edit()
        .putString("grid_shortcuts", list.joinToString(";") { "${it.packageName}@${text(it.spot)}" }).apply()

    fun widgets(context: Context): List<DesktopWidget> =
        prefs(context).getString("grid_widgets", "").orEmpty().split(';').mapNotNull { entry ->
            val parts = entry.split('@')
            DesktopWidget(parts[0].toIntOrNull() ?: return@mapNotNull null, spot(parts.getOrNull(1).orEmpty()) ?: return@mapNotNull null)
        }

    fun saveWidgets(context: Context, list: List<DesktopWidget>) = prefs(context).edit()
        .putString("grid_widgets", list.joinToString(";") { "${it.appWidgetId}@${text(it.spot)}" }).apply()

    /** Every taken cell area except the item [except] (a package name or a widget id). */
    fun taken(context: Context, except: Any? = null): List<GridSpot> =
        shortcuts(context).filter { it.packageName != except }.map { it.spot } +
            widgets(context).filter { it.appWidgetId != except }.map { it.spot }

    /** [wanted] if it is free and inside the grid, else the nearest free spot of its size, or null. */
    fun freeSpot(wanted: GridSpot, taken: List<GridSpot>, columns: Int, rows: Int): GridSpot? {
        fun fits(s: GridSpot) = s.column >= 0 && s.row >= 0 && s.column + s.columns <= columns && s.row + s.rows <= rows &&
            taken.none { it.overlaps(s) }
        if (fits(wanted)) return wanted
        val candidates = (0..rows - wanted.rows).flatMap { r -> (0..columns - wanted.columns).map { c -> wanted.copy(column = c, row = r) } }
        return candidates.filter(::fits).minByOrNull { s ->
            val dc = s.column - wanted.column
            val dr = s.row - wanted.row
            dc * dc + dr * dr
        }
    }

    fun placeShortcut(context: Context, packageName: String, spot: GridSpot) =
        saveShortcuts(context, shortcuts(context).filter { it.packageName != packageName } + DesktopShortcut(packageName, spot))

    fun removeShortcut(context: Context, packageName: String) =
        saveShortcuts(context, shortcuts(context).filter { it.packageName != packageName })

    fun placeWidget(context: Context, appWidgetId: Int, spot: GridSpot) =
        saveWidgets(context, widgets(context).filter { it.appWidgetId != appWidgetId } + DesktopWidget(appWidgetId, spot))

    fun removeWidget(context: Context, appWidgetId: Int) =
        saveWidgets(context, widgets(context).filter { it.appWidgetId != appWidgetId })
}

/**
 * Android app widgets on Book's desktop: binds providers (Dextop is granted binding through
 * the privileged shell), creates their views and opens what they launch on the Dextop display.
 */
internal class BookAppWidgets(private val context: Context, private val displayId: Int, private val log: (String) -> Unit) {
    private val manager: AppWidgetManager = AppWidgetManager.getInstance(context)
    private val host = AppWidgetHost(context, HOST_ID)
    private val views = HashMap<Int, AppWidgetHostView>()

    fun start() = runCatching { host.startListening() }.onFailure { log("widget host: ${it.message}") }

    fun stop() {
        runCatching { host.stopListening() }
        views.clear()
    }

    fun info(appWidgetId: Int): AppWidgetProviderInfo? = runCatching { manager.getAppWidgetInfo(appWidgetId) }.getOrNull()

    fun providers(): List<AppWidgetProviderInfo> = runCatching { manager.installedProviders }.getOrDefault(emptyList())

    fun label(info: AppWidgetProviderInfo): String = runCatching { info.loadLabel(context.packageManager) }.getOrNull() ?: info.provider.className

    fun preview(info: AppWidgetProviderInfo): Drawable? =
        runCatching { info.loadPreviewImage(context, 0) }.getOrNull() ?: runCatching { info.loadIcon(context, 0) }.getOrNull()

    fun appLabel(info: AppWidgetProviderInfo): String = runCatching {
        context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(info.provider.packageName, 0)).toString()
    }.getOrDefault(info.provider.packageName)

    fun appIcon(info: AppWidgetProviderInfo): Drawable? =
        runCatching { context.packageManager.getApplicationIcon(info.provider.packageName) }.getOrNull()

    /** Allocates and binds a widget of [info]; null when Android refuses the binding. */
    fun bind(info: AppWidgetProviderInfo): Int? {
        val id = host.allocateAppWidgetId()
        val bound = runCatching { manager.bindAppWidgetIdIfAllowed(id, info.provider) }.getOrDefault(false)
        if (!bound) {
            host.deleteAppWidgetId(id)
            log("widget bind refused for ${info.provider}")
            return null
        }
        return id
    }

    fun delete(appWidgetId: Int) {
        views.remove(appWidgetId)
        runCatching { host.deleteAppWidgetId(appWidgetId) }
    }

    /** The widget's view, created once and kept while Book runs. */
    fun view(appWidgetId: Int): AppWidgetHostView? {
        views[appWidgetId]?.let { return it }
        val info = info(appWidgetId) ?: return null
        val view = runCatching { host.createView(context, appWidgetId, info) }.getOrNull() ?: return null
        launchOnDesktop(view)
        views[appWidgetId] = view
        return view
    }

    fun resize(appWidgetId: Int, widthDp: Float, heightDp: Float) {
        val view = views[appWidgetId] ?: return
        runCatching { view.updateAppWidgetSize(Bundle(), listOf(SizeF(widthDp, heightDp))) }
    }

    /** Whether the widget needs its configuration screen before it shows anything useful. */
    fun needsConfiguration(info: AppWidgetProviderInfo): Boolean =
        info.configure != null && info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL == 0

    fun canReconfigure(info: AppWidgetProviderInfo): Boolean =
        info.configure != null && info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE != 0

    /** Opens the widget's configuration screen on the Dextop display (it becomes a Book window). */
    fun configure(appWidgetId: Int) {
        runCatching {
            val sender = AppWidgetHost::class.java.getMethod("getIntentSenderForConfigureActivity", Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType).invoke(host, appWidgetId, 0) as android.content.IntentSender
            context.startIntentSender(sender, null, Intent.FLAG_ACTIVITY_NEW_TASK, Intent.FLAG_ACTIVITY_NEW_TASK, 0,
                ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle())
        }.onFailure { log("widget configure failed: ${it.message}") }
    }

    /**
     * Activities a widget starts open on the Dextop display, where the compositor turns them into
     * windows; Android's default would start them on the phone.
     */
    private fun launchOnDesktop(view: AppWidgetHostView) {
        runCatching {
            val handlerType = Class.forName("android.widget.RemoteViews\$InteractionHandler")
            val handler = java.lang.reflect.Proxy.newProxyInstance(handlerType.classLoader, arrayOf(handlerType)) { _, method, args ->
                if (method.name != "onInteraction") return@newProxyInstance null
                val target = args[0] as View
                val pending = args[1] as PendingIntent
                val response = args[2]
                val launch = runCatching {
                    response.javaClass.getMethod("getLaunchOptions", View::class.java).invoke(response, target) as android.util.Pair<*, *>
                }.getOrNull()
                val fillIn = launch?.first as? Intent
                val options = (launch?.second as? ActivityOptions ?: ActivityOptions.makeBasic()).setLaunchDisplayId(displayId)
                runCatching {
                    pending.send(context, 0, fillIn, null, null, null, options.toBundle())
                    true
                }.getOrDefault(false)
            }
            AppWidgetHostView::class.java.getMethod("setInteractionHandler", handlerType).invoke(view, handler)
        }.onFailure { log("widget launch handler unavailable: ${it.message}") }
    }

    companion object {
        const val HOST_ID = 0x0B00C
    }
}

/** Live data the status bar and the media panel show. Compose state, written on the main thread. */
internal object BookLive {
    var media by mutableStateOf<PlasmaSystemControls.MediaInfo?>(null)
    var mediaIcon by mutableStateOf<Drawable?>(null)
    /** The playing item's artwork (album cover), from its media session. */
    var mediaArt by mutableStateOf<android.graphics.Bitmap?>(null)
    var battery by mutableStateOf(1f)
    var charging by mutableStateOf(false)
}

/** The application context, for formatting outside a composition. */
internal object BookContext {
    lateinit var app: Context
}

/** Ticks once a second (or minute), for clocks. */
@Composable
internal fun rememberNow(everyMs: Long = 1_000L): Date {
    val now by produceState(Date()) {
        while (true) {
            value = Date()
            delay(everyMs - System.currentTimeMillis() % everyMs)
        }
    }
    return now
}

/** A month of days, today circled. */
@Composable
internal fun MonthGrid(month: Calendar, modifier: Modifier = Modifier, onDay: ((Calendar) -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    val today = Calendar.getInstance()
    val first = (month.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
    val firstDay = first.firstDayOfWeek
    val lead = (first.get(Calendar.DAY_OF_WEEK) - firstDay + 7) % 7
    val days = first.getActualMaximum(Calendar.DAY_OF_MONTH)
    val names = java.text.DateFormatSymbols.getInstance(Locale.getDefault()).shortWeekdays
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            (0 until 7).forEach { index ->
                val day = (firstDay - 1 + index) % 7 + 1
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Text(names[day].take(1), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                }
            }
        }
        (0 until (lead + days + 6) / 7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                (0 until 7).forEach { index ->
                    val day = week * 7 + index - lead + 1
                    val isToday = day == today.get(Calendar.DAY_OF_MONTH) && month.get(Calendar.MONTH) == today.get(Calendar.MONTH) &&
                        month.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(if (isToday) colors.primary else Color.Transparent)
                            .then(if (day in 1..days && onDay != null) Modifier.clickable {
                                onDay((month.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, day) })
                            } else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (day in 1..days) Text(day.toString(), style = MaterialTheme.typography.bodyLarge,
                            color = if (isToday) colors.onPrimary else colors.onSurface)
                    }
                }
            }
        }
    }
}
