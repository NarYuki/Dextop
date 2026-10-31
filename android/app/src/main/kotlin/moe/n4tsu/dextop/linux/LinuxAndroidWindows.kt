package moe.n4tsu.dextop.linux

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.util.Log
import moe.n4tsu.dextop.MirrorService
import org.json.JSONObject

/**
 * Linux application windows shown as ordinary Android windows: every top-level X window
 * gets a task of its own ([LinuxWindowActivity]), drawn from the container's application
 * display the same way Plasma v2 draws its Linux windows.
 *
 * Each window carries the application it belongs to (its desktop entry, name and icon),
 * so Android and DeX show the application rather than Dextop, and the Linux tab can list
 * an application's open windows. Used whenever no Plasma v2 desktop runs; with one
 * running, applications open there.
 */
internal object LinuxAndroidWindows : LinuxContainerClient.Listener {
    private const val TAG = "DextopLinuxAndroid"

    class Info(val container: String, val xid: Int) {
        var title = ""
        var wmClass = ""
        var icon: Bitmap? = null
        val bounds = Rect()
        var transientFor = 0
        var auxiliary = false
        var fixedSize = false
        /** The desktop entry this window belongs to, with its name and icon, when known. */
        var desktopId: String? = null
        var appLabel: String? = null
        var appIcon: Bitmap? = null
    }

    /** An open application window, as the Linux tab lists it. */
    data class Session(val container: String, val desktopId: String?, val xid: Int, val title: String)

    private data class Key(val container: String, val xid: Int)

    private val windows = LinkedHashMap<Key, Info>()
    private val hosts = HashMap<Key, LinuxWindowActivity>()
    private val popups = HashMap<Key, LinuxWindowActivity>()
    /** Dialogs (windows with a parent) shown inside their parent's Android window. */
    private val dialogs = HashMap<Key, LinuxWindowActivity>()
    /** Activities opened for a launch and still waiting for the application's first window. */
    private val waiting = ArrayList<LinuxWindowActivity>()
    private val restoring = HashMap<Key, LinuxWindowActivity>()
    private val startupClasses = java.util.concurrent.ConcurrentHashMap<String, Map<String, String>>()
    private var listening = false
    private var focused: LinuxWindowActivity? = null

    /**
     * Opens [desktopId] of [container] as a new session: in the Plasma desktop when one runs,
     * otherwise as an Android window named [label] with [icon].
     */
    fun launch(context: Context, container: String, desktopId: String, label: String? = null, icon: Bitmap? = null) {
        if (MirrorService.launchLinuxAppInPlasma(container, desktopId)) return
        appContext = context.applicationContext
        listen()
        // With the application's launcher installed, its window lives in the launcher's task.
        val host = LinuxShortcutInstaller.hostPackage(context, container, desktopId)
        val intent = if (host != null) hostedIntent(host, 0, icon) else {
            LinuxShortcutInstaller.upgradeIfOld(context, container, desktopId)
            Intent(context, LinuxWindowActivity::class.java)
                .putExtra(LinuxWindowActivity.EXTRA_CONTAINER, container)
                .putExtra(LinuxWindowActivity.EXTRA_DESKTOP, desktopId)
                .putExtra(LinuxWindowActivity.EXTRA_LABEL, label)
                .putExtra(LinuxWindowActivity.EXTRA_ICON, icon?.let(::png))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                    (if (context is Activity) 0 else Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        context.startActivity(intent)
        LinuxSessionService.start(context)
    }

    /**
     * Opens a new task rooted in launcher [host]'s Window activity, which opens
     * [LinuxHostedWindowActivity] on top for its application ([xid] 0 launches it).
     */
    private fun hostedIntent(host: String, xid: Int, icon: Bitmap?) = Intent()
        .setClassName(host, LinuxShortcutInstaller.WINDOW_ACTIVITY)
        .putExtra(LinuxWindowActivity.EXTRA_XID, xid)
        .putExtra(LinuxWindowActivity.EXTRA_ICON, icon?.let(::png))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)

    private fun png(bitmap: Bitmap): ByteArray = java.io.ByteArrayOutputStream()
        .also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

    private fun listen() {
        if (listening) return
        listening = true
        LinuxContainerClient.addListener(this)
    }

    fun info(container: String, xid: Int): Info? = windows[Key(container, xid)]

    /** Reattaches a recreated task without launching another application process. */
    fun restore(host: LinuxWindowActivity, container: String, xid: Int) {
        appContext = host.applicationContext
        listen()
        info(container, xid)?.let { host.bind(it); return }
        val key = Key(container, xid)
        restoring[key] = host
        val metrics = host.windowManager.maximumWindowMetrics.bounds
        LinuxContainerClient.connect(host)
        LinuxContainerClient.call(host, { linux ->
            // An existing session replays its windows to the reconnected client.
            linux.startApps(container, metrics.width(), metrics.height(), host.resources.displayMetrics.densityDpi)
        }, {}) { error ->
            restoring.remove(key)
            host.showError(error.message ?: error.toString())
        }
    }

    /** Starts the application display at the size of the screen and launches the entry. */
    fun startApplication(host: LinuxWindowActivity, container: String, desktopId: String) {
        appContext = host.applicationContext
        listen()
        host.waitingSince = android.os.SystemClock.elapsedRealtime()
        waiting += host
        host.window.decorView.postDelayed({
            if (host in waiting) host.showError(moe.n4tsu.dextop.localText("アプリのウィンドウがまだ開いていません。起動ログを確認してください", "The application has not opened a window yet. Check its launch log"))
        }, 30_000)
        val metrics = host.windowManager.maximumWindowMetrics.bounds
        val dpi = host.resources.displayMetrics.densityDpi
        val refresh = host.display?.refreshRate ?: 60f
        fun phase(ja: String, en: String) = host.runOnUiThread {
            host.setPhase(moe.n4tsu.dextop.localText(ja, en))
        }
        phase("Linux サービスに接続しています", "Connecting to the Linux service")
        LinuxContainerClient.connect(host)
        val clicked = android.os.SystemClock.uptimeMillis()
        LinuxContainerClient.call(host, { linux ->
            val queued = android.os.SystemClock.uptimeMillis()
            phase("コンテナとアプリ表示を起動しています", "Starting the container and its display")
            linux.startApps(container, metrics.width(), metrics.height(), dpi)
            val started = android.os.SystemClock.uptimeMillis()
            linux.updateDisplay(container, true, metrics.width(), metrics.height(), dpi, refresh)
            android.util.Log.i("DextopLaunch", "android $desktopId queue=${queued - clicked}ms startApps=${started - queued}ms " +
                "updateDisplay=${android.os.SystemClock.uptimeMillis() - started}ms")
            host.launchLabel?.let { label ->
                host.runOnUiThread { host.setPhase(moe.n4tsu.dextop.InlineStrings.format(moe.n4tsu.dextop.localText("{0} を起動しています", "Starting {0}"), label)) }
            } ?: phase("アプリを起動しています", "Starting the app")
            LinuxTheme.apply(host.applicationContext, linux, container)
            linux.launchApp(container, desktopId)
            phase("ウィンドウが開くのを待っています", "Waiting for its window")
        }, {}) { error ->
            waiting -= host
            host.showError(error.message ?: error.toString())
        }
        LinuxSessionService.start(host)
    }

    fun attached(host: LinuxWindowActivity, container: String, xid: Int) {
        hosts[Key(container, xid)] = host
        LinuxSessionService.changed()
    }

    fun gone(host: LinuxWindowActivity) {
        waiting -= host
        restoring.entries.removeAll { it.value === host }
        hosts.entries.removeAll { it.value === host }
        popups.entries.removeAll { it.value === host }
        dialogs.entries.removeAll { it.value === host }
        if (focused === host) focused = null
        LinuxSessionService.changed()
    }

    fun focused(host: LinuxWindowActivity) { focused = host }

    /** Committed client pixels, which can differ from the Android viewport for fixed-size dialogs. */
    fun windowBounds(container: String, xid: Int): Rect? = windows[Key(container, xid)]?.bounds?.let(::Rect)

    // region sessions

    /** Open application windows (not dialogs), oldest first. */
    fun sessions(): List<Session> = hosts.entries.mapNotNull { (key, _) ->
        val info = windows[key] ?: return@mapNotNull null
        Session(key.container, info.desktopId, key.xid, info.title.ifBlank { info.appLabel ?: info.wmClass })
    }

    /** Names of the running applications, one per application. */
    fun runningApps(): List<String> = hosts.keys.mapNotNull { windows[it] }
        .map { it.appLabel ?: it.title.ifBlank { it.wmClass.ifBlank { "Linux" } } }
        .distinct()

    /** Whether any Linux window is open or about to open. */
    fun busy(): Boolean = hosts.isNotEmpty() || waiting.isNotEmpty() || restoring.isNotEmpty()

    /** The icon of an open application named [label], for its notifications. */
    fun iconFor(container: String, label: String): Bitmap? = hosts.keys.mapNotNull { windows[it] }
        .firstOrNull { it.container == container && it.appLabel.equals(label, ignoreCase = true) }?.appIcon

    fun capture(container: String, xid: Int, done: (Bitmap?) -> Unit) {
        hosts[Key(container, xid)]?.capture(done) ?: done(null)
    }

    fun focus(container: String, xid: Int): Boolean =
        hosts[Key(container, xid)]?.bringToFront() ?: false

    /** Asks every window of [desktopId] to close, as its close button would. */
    fun closeApp(container: String, desktopId: String) {
        hosts.keys.filter { it.container == container && windows[it]?.desktopId == desktopId }.forEach { key ->
            LinuxContainerClient.service()?.runCatching { closeWindow(container, key.xid) }
        }
    }
    // endregion

    /** Text a Linux application copied, for Android's clipboard (Plasma shares its own). */
    override fun onClipboard(containerId: String, text: String) {
        if (MirrorService.plasmaDesktopRunning) return
        appContext?.let { LinuxClipboard.toAndroid(it, text) }
    }

    override fun onWindowEvent(containerId: String, event: JSONObject) {
        if (MirrorService.plasmaDesktopRunning) return
        val xid = event.optInt("xid")
        val key = Key(containerId, xid)
        val popup = event.optBoolean("popup")
        val bounds = Rect(event.optInt("x"), event.optInt("y"),
            event.optInt("x") + event.optInt("w"), event.optInt("y") + event.optInt("h"))
        when (event.optString("type")) {
            "launch-start" -> {
                val desktop = event.optString("desktop")
                startupClasses[containerId] = startupClasses[containerId].orEmpty() + (desktop to event.optString("startupWMClass"))
            }
            "map" -> map(containerId, xid, event, bounds)
            "configure" -> when {
                popup -> { windows[key]?.bounds?.set(bounds); popups[key]?.placePopup(xid, bounds) }
                dialogs.containsKey(key) -> { windows[key]?.bounds?.set(bounds); dialogs[key]?.fitDialog(xid, bounds) }
                else -> {
                    windows[key]?.bounds?.set(bounds)
                    windows[key]?.fixedSize = event.optBoolean("fixedSize")
                    hosts[key]?.actualSize(bounds.width(), bounds.height())
                }
            }
            // The application asks for a size of its own: a dialog gets it, a main window
            // keeps its Android window's size unless the application insists.
            "client-configure" -> when {
                popup -> popups[key]?.placePopup(xid, bounds)
                dialogs.containsKey(key) -> dialogs[key]?.fitDialog(xid, bounds)
                else -> hosts[key]?.clientRequested(bounds.width(), bounds.height())
            }
            "unmap" -> when {
                popup -> { windows.remove(key); popups.remove(key)?.removePopup(xid) }
                dialogs.containsKey(key) -> { windows.remove(key); dialogs.remove(key)?.closeDialog(xid) }
                else -> {
                    windows.remove(key)
                    hosts.remove(key)?.closedByLinux()
                    LinuxSessionService.changed()
                }
            }
            "title" -> windows[key]?.let { info ->
                info.title = event.optString("title").ifBlank { info.title }
                reidentify(info)
                hosts[key]?.describe(info)
            }
            "class" -> windows[key]?.let { info ->
                info.wmClass = event.optString("class")
                adopt(info)
                reidentify(info)
                hosts[key]?.describe(info)
            }
            "icon" -> windows[key]?.let { info ->
                info.icon = decodeIcon(event.optString("icon")) ?: info.icon
                hosts[key]?.describe(info)
            }
            "launch-status" -> waitingFor(containerId, event).forEach { it.setDetail(event.optString("line")) }
            // The command ended without an error. An application that opens no new window for
            // it (a single-instance one handing the launch to its running instance, which may
            // reuse a window) leaves its launch window waiting: after a grace period it gives
            // way to the application's window.
            "launch-exit" -> if (event.optInt("code") == 0) waitingFor(containerId, event).forEach { host ->
                host.setPhase(moe.n4tsu.dextop.localText("既存のウィンドウを確認しています", "Looking for its existing window"))
                host.window.decorView.postDelayed({ if (host in waiting) handOff(host) }, HANDOFF_GRACE_MS)
            }
            // An application's failure reaches only the windows waiting for it.
            "error" -> {
                val desktop = event.optString("desktop").ifEmpty { null }
                waiting.filter { it.container == containerId && (desktop == null || it.launchDesktop == desktop) }
                    .forEach { it.showError(event.optString("message")) }
            }
            "reset" -> {
                restoring.entries.filter { it.key.container == containerId }.map { it.value }.forEach { it.closedByLinux() }
                windows.keys.removeAll { it.container == containerId }
                hosts.entries.filter { it.key.container == containerId }.forEach { it.value.closedByLinux() }
            }
        }
    }

    /**
     * A window the application opened on its own (a new document, a second window) belongs to
     * the application that already has a window of the same class.
     */
    private fun adopt(info: Info) {
        if (info.desktopId != null || info.wmClass.isBlank()) return
        val sibling = hosts.keys.mapNotNull { windows[it] }.firstOrNull {
            it.container == info.container && it.desktopId != null && it.wmClass.equals(info.wmClass, ignoreCase = true)
        } ?: return
        info.desktopId = sibling.desktopId
        info.appLabel = sibling.appLabel
        info.appIcon = sibling.appIcon
    }

    private fun map(container: String, xid: Int, event: JSONObject, bounds: Rect) {
        val key = Key(container, xid)
        if (windows.containsKey(key) && hosts.containsKey(key)) return
        // Invisible X11 helper windows are not application UI.
        if (event.optBoolean("popup") && bounds.width() <= 1 && bounds.height() <= 1) return
        val info = Info(container, xid).apply {
            title = event.optString("title")
            wmClass = event.optString("class")
            icon = decodeIcon(event.optString("icon"))
            transientFor = event.optInt("transientFor")
            auxiliary = event.optBoolean("popup") || event.optBoolean("dialog") || transientFor != 0
            fixedSize = event.optBoolean("fixedSize")
            this.bounds.set(bounds)
        }
        windows[key] = info
        restoring.remove(key)?.let { it.bind(info); return }
        // Menus, tooltips and drop-down lists float over their window at their own place.
        if (event.optBoolean("popup")) return showPopup(container, xid, bounds, info.transientFor)
        // A dialog (file chooser, preferences…) opens as a window of its own, at its own size,
        // over its parent.
        val parent = info.transientFor.takeIf { it != 0 }?.let { Key(container, it) }
        if (info.auxiliary) {
            // Its window is gone (for example the task was closed and the application asks
            // whether to save): it opens on its own, at its own size, never stretched.
            windows[parent]?.let { owner ->
                info.desktopId = owner.desktopId; info.appLabel = owner.appLabel; info.appIcon = owner.appIcon
            }
            adopt(info)
            open(container, xid, dialog = true)
            return
        }
        // A launch takes its application's first window: the one whose class (or title) names
        // the application. A window another running application opened stays with that one,
        // so a launch never takes over a different application's window.
        val candidates = waiting.filter { it.container == container }
        adopt(info)
        val scored = candidates.map { it to affinity(info, it.launchDesktop, it.launchLabel) }
        val ownerScore = affinity(info, info.desktopId, info.appLabel)
        val strong = scored.filter { it.second >= 2 && (info.desktopId == null ||
            it.first.launchDesktop == info.desktopId || it.second > ownerScore) }.maxByOrNull { it.second }?.first
        if (strong != null) return take(strong, info)
        // A window no running application claims (its class names nothing, as Proton's
        // steam_app_0) belongs to a launch still waiting for its first window, the oldest
        // first; windows of applications already open were adopted above.
        if (info.desktopId == null) {
            val now = android.os.SystemClock.elapsedRealtime()
            val launch = candidates.filter { now - it.waitingSince < UNCLAIMED_WINDOW_MS }.minByOrNull { it.waitingSince }
            if (launch != null) return take(launch, info)
        }
        // Further top-level windows (new documents) open as tasks of their own.
        open(container, xid, dialog = false)
    }

    private fun waitingFor(container: String, event: JSONObject): List<LinuxWindowActivity> {
        val desktop = event.optString("desktop").ifEmpty { null }
        return waiting.filter { it.container == container && (desktop == null || it.launchDesktop == desktop) }
    }

    /** How long after its start a launch takes a window no application claims. */
    private const val UNCLAIMED_WINDOW_MS = 90_000L

    /** How long a launch whose command ended may still receive its window. */
    private const val HANDOFF_GRACE_MS = 4_000L

    /**
     * A launch whose command ended without opening a window: the application opened it in
     * one of its existing windows. That window comes to the front and the launch window
     * closes; without one, the launch window says so instead of waiting forever.
     */
    private fun handOff(host: LinuxWindowActivity) {
        val existing = hosts.entries.filter { it.key.container == host.container }
            .mapNotNull { (key, window) -> windows[key]?.let { info -> Triple(window, info, affinity(info, host.launchDesktop, host.launchLabel)) } }
            .filter { it.third >= 2 }
            .maxWithOrNull(compareBy({ it.third }, { it.first === focused }))
        if (existing == null) {
            // A launcher may exit after forwarding/forking while its real process is
            // still starting. Four seconds without a window is not a launch failure.
            if (android.os.SystemClock.elapsedRealtime() - host.waitingSince < 30_000) {
                host.window.decorView.postDelayed({ if (host in waiting) handOff(host) }, 1_000)
                return
            }
            waiting -= host
            host.showError(host.launchLabel?.let {
                moe.n4tsu.dextop.InlineStrings.format(moe.n4tsu.dextop.localText("{0} は終了しました。ウィンドウは開かれませんでした",
                    "{0} ended without opening a window"), it)
            } ?: moe.n4tsu.dextop.localText("アプリは終了しました。ウィンドウは開かれませんでした", "The app ended without opening a window"))
            return
        }
        waiting -= host
        val (window, info, score) = existing
        if (score >= 3 && score > affinity(info, info.desktopId, info.appLabel) && info.desktopId != host.launchDesktop) {
            info.desktopId = host.launchDesktop
            info.appLabel = host.launchLabel
            info.appIcon = host.launchIcon ?: info.appIcon
            window.describe(info)
        }
        window.bringToFront()
        host.finishAndRemoveTask()
    }

    /**
     * A window that turned into another application (LibreOffice replaces an empty document
     * with the module just started) belongs to that application's launch from now on, and
     * the launch stops waiting for a window of its own.
     */
    private fun reidentify(info: Info) {
        val own = affinity(info, info.desktopId, info.appLabel)
        val launch = waiting.filter { it.container == info.container }
            .map { it to affinity(info, it.launchDesktop, it.launchLabel) }
            .filter { it.second >= 2 && it.second > own }
            .maxByOrNull { it.second }?.first ?: return
        val host = hosts[Key(info.container, info.xid)] ?: return
        waiting -= launch
        info.desktopId = launch.launchDesktop
        info.appLabel = launch.launchLabel
        info.appIcon = launch.launchIcon ?: info.appIcon
        host.describe(info)
        host.bringToFront()
        launch.finishAndRemoveTask()
    }

    private fun take(host: LinuxWindowActivity, info: Info) {
        waiting -= host
        info.desktopId = host.launchDesktop
        info.appLabel = host.launchLabel
        info.appIcon = host.launchIcon
        host.bind(info)
    }


    /**
     * How surely [window] belongs to the application [desktopId] named [label], from its
     * class and its title, each 3 when it is the application's name, 2 when the words of one
     * contain the other's (a title such as "Untitled 1 - LibreOffice Writer"), 1 when they
     * share a word. The stronger of the two decides, the other breaks ties (LibreOffice's
     * modules share one class).
     */
    private fun affinity(window: Info, desktopId: String?, label: String?): Double {
        return LinuxWindowIdentity.score(window.wmClass, window.title, desktopId, label,
            startupClasses[window.container]?.get(desktopId).orEmpty())
    }

    var appContext: Context? = null

    /**
     * Starts the Android window for [xid]: from a visible window of the same container when
     * there is one, otherwise from the application (Dextop may draw over other apps, which
     * lets it start activities from the background).
     */
    private fun open(container: String, xid: Int, dialog: Boolean) {
        val opener: Context = hosts.values.firstOrNull { it.container == container && it.hasWindowFocus() }
            ?: focused ?: hosts.values.lastOrNull() ?: appContext ?: run {
                Log.w(TAG, "No context to open 0x${xid.toString(16)} from")
                return
            }
        val info = windows[Key(container, xid)]
        // Honour a client's requested size for ordinary windows too. Otherwise
        // Android's default desktop task bounds immediately resize e.g. a
        // 960x540 game into a much larger render surface.
        val options = if (info != null) dialogBounds(container, info)?.let { (display, rect) ->
            android.app.ActivityOptions.makeBasic().setLaunchDisplayId(display).setLaunchBounds(rect).toBundle()
        } else null
        // In the application's launcher task when it has one, so it carries its name and icon.
        val host = info?.desktopId?.let { LinuxShortcutInstaller.hostPackage(opener, container, it) }
        if (host != null) {
            opener.startActivity(hostedIntent(host, xid, info.appIcon), options)
            return
        }
        opener.startActivity(Intent(opener, LinuxWindowActivity::class.java)
            .putExtra(LinuxWindowActivity.EXTRA_CONTAINER, container)
            .putExtra(LinuxWindowActivity.EXTRA_XID, xid)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                (if (opener is Activity) 0 else Intent.FLAG_ACTIVITY_NEW_TASK)), options)
    }

    /**
     * Where a dialog's own window goes: the dialog's size (plus the window's title bar),
     * centred over its parent's window and kept on its display. Null without a parent window
     * to centre on.
     */
    private fun dialogBounds(container: String, info: Info): Pair<Int, Rect>? {
        val parent = hosts[Key(container, info.transientFor)]
            ?: focused?.takeIf { it.container == container }
            ?: return null
        val display = parent.display ?: return null
        val frame = parent.windowManager.currentWindowMetrics.bounds
        val content = parent.screenBounds()
        val screen = parent.windowManager.maximumWindowMetrics.bounds
        // The title bar and frame around the content, as the parent's window has them.
        val extraWidth = (frame.width() - content.width()).coerceIn(0, frame.width() / 4)
        val extraHeight = (content.top - frame.top).coerceAtLeast(0) + (frame.bottom - content.bottom).coerceAtLeast(0)
        val width = (info.bounds.width() + extraWidth).coerceIn(1, screen.width())
        val height = (info.bounds.height() + extraHeight).coerceIn(1, screen.height())
        val left = (frame.centerX() - width / 2).coerceIn(screen.left, (screen.right - width).coerceAtLeast(screen.left))
        val top = (frame.centerY() - height / 2).coerceIn(screen.top, (screen.bottom - height).coerceAtLeast(screen.top))
        return display.displayId to Rect(left, top, left + width, top + height)
    }

    /** Menus and tooltips are drawn inside the window they belong to. */
    private fun showPopup(container: String, xid: Int, bounds: Rect, transientFor: Int) {
        val over = hosts.entries.filter { (key, _) ->
            key.container == container && windows[key]?.bounds?.contains(bounds.left, bounds.top) == true
        }.map { it.value }
        val host = hosts[Key(container, transientFor)] ?: dialogs[Key(container, transientFor)]
            ?: popups[Key(container, transientFor)]
            ?: focused?.takeIf { it.container == container && it in over }
            ?: over.lastOrNull()
            ?: focused?.takeIf { it.container == container }
            // A popup placed by where its application thinks it is still opens over it.
            ?: hosts.values.lastOrNull { it.container == container }
        Log.i(TAG, "popup 0x${xid.toString(16)} $bounds over=${over.size} host=${host?.let { "0x" + it.shownXid.toString(16) }}")
        host ?: return
        popups[Key(container, xid)] = host
        host.showPopup(xid, bounds)
    }

    private fun decodeIcon(encoded: String): Bitmap? = runCatching {
        if (encoded.isEmpty() || encoded.length > 256 * 1024) return null
        val bytes = android.util.Base64.decode(encoded, android.util.Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}
