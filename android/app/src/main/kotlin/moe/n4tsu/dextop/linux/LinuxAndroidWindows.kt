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
 * Used whenever no Plasma v2 desktop runs; with one running, applications open there.
 */
internal object LinuxAndroidWindows : LinuxContainerClient.Listener {
    private const val TAG = "DextopLinuxAndroid"

    class Info(val container: String, val xid: Int) {
        var title = ""
        var wmClass = ""
        var icon: Bitmap? = null
        val bounds = Rect()
        var transientFor = 0
    }

    private data class Key(val container: String, val xid: Int)

    private val windows = LinkedHashMap<Key, Info>()
    private val hosts = HashMap<Key, LinuxWindowActivity>()
    private val popups = HashMap<Key, LinuxWindowActivity>()
    /** Dialogs (windows with a parent) shown inside their parent's Android window. */
    private val dialogs = HashMap<Key, LinuxWindowActivity>()
    /** Activities opened for a launch and still waiting for the application's first window. */
    private val waiting = ArrayList<LinuxWindowActivity>()
    private val restoring = HashMap<Key, LinuxWindowActivity>()
    private var listening = false
    private var focused: LinuxWindowActivity? = null

    /** Opens [desktopId] of [container]: in the Plasma desktop when one runs, otherwise as Android windows. */
    fun launch(context: Context, container: String, desktopId: String, label: String? = null) {
        if (MirrorService.launchLinuxAppInPlasma(container, desktopId)) return
        appContext = context.applicationContext
        listen()
        val intent = Intent(context, LinuxWindowActivity::class.java)
            .putExtra(LinuxWindowActivity.EXTRA_CONTAINER, container)
            .putExtra(LinuxWindowActivity.EXTRA_DESKTOP, desktopId)
            .putExtra(LinuxWindowActivity.EXTRA_LABEL, label)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                (if (context is Activity) 0 else Intent.FLAG_ACTIVITY_NEW_TASK))
        context.startActivity(intent)
    }

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
        waiting += host
        val metrics = host.windowManager.maximumWindowMetrics.bounds
        val dpi = host.resources.displayMetrics.densityDpi
        val refresh = host.display?.refreshRate ?: 60f
        LinuxContainerClient.connect(host)
        LinuxContainerClient.call(host, { linux ->
            linux.startApps(container, metrics.width(), metrics.height(), dpi)
            linux.updateDisplay(container, true, metrics.width(), metrics.height(), dpi, refresh)
            linux.launchApp(container, desktopId)
        }, {}) { error ->
            waiting -= host
            host.showError(error.message ?: error.toString())
        }
    }

    fun attached(host: LinuxWindowActivity, container: String, xid: Int) {
        hosts[Key(container, xid)] = host
    }

    fun gone(host: LinuxWindowActivity) {
        waiting -= host
        restoring.entries.removeAll { it.value === host }
        hosts.entries.removeAll { it.value === host }
        popups.entries.removeAll { it.value === host }
        dialogs.entries.removeAll { it.value === host }
        if (focused === host) focused = null
    }

    fun focused(host: LinuxWindowActivity) { focused = host }

    override fun onWindowEvent(containerId: String, event: JSONObject) {
        if (MirrorService.plasmaDesktopRunning) return
        val xid = event.optInt("xid")
        val key = Key(containerId, xid)
        val popup = event.optBoolean("popup")
        val bounds = Rect(event.optInt("x"), event.optInt("y"),
            event.optInt("x") + event.optInt("w"), event.optInt("y") + event.optInt("h"))
        when (event.optString("type")) {
            "map" -> if (popup) showPopup(containerId, xid, bounds, event.optInt("transientFor")) else map(containerId, xid, event, bounds)
            "configure", "client-configure" -> when {
                popup -> popups[key]?.placePopup(xid, bounds)
                dialogs.containsKey(key) -> { windows[key]?.bounds?.set(bounds); dialogs[key]?.fitDialog(xid, bounds) }
                else -> {
                    windows[key]?.bounds?.set(bounds)
                    hosts[key]?.windowSized(bounds.width(), bounds.height())
                }
            }
            "unmap" -> when {
                popup -> popups.remove(key)?.removePopup(xid)
                dialogs.containsKey(key) -> { windows.remove(key); dialogs.remove(key)?.closeDialog(xid) }
                else -> {
                    windows.remove(key)
                    hosts.remove(key)?.closedByLinux()
                }
            }
            "title" -> windows[key]?.let { info ->
                info.title = event.optString("title").ifBlank { info.title }
                hosts[key]?.describe(info)
            }
            "class" -> windows[key]?.let { it.wmClass = event.optString("class") }
            "icon" -> windows[key]?.let { info ->
                info.icon = decodeIcon(event.optString("icon")) ?: info.icon
                hosts[key]?.describe(info)
            }
            "error" -> waiting.filter { it.container == containerId }.forEach { it.showError(event.optString("message")) }
            "reset" -> {
                restoring.entries.filter { it.key.container == containerId }.map { it.value }.forEach { it.closedByLinux() }
                windows.keys.removeAll { it.container == containerId }
                hosts.entries.filter { it.key.container == containerId }.forEach { it.value.closedByLinux() }
            }
        }
    }

    private fun map(container: String, xid: Int, event: JSONObject, bounds: Rect) {
        val key = Key(container, xid)
        if (windows.containsKey(key) && hosts.containsKey(key)) return
        val info = Info(container, xid).apply {
            title = event.optString("title")
            wmClass = event.optString("class")
            icon = decodeIcon(event.optString("icon"))
            transientFor = event.optInt("transientFor")
            this.bounds.set(bounds)
        }
        windows[key] = info
        restoring.remove(key)?.let { it.bind(info); return }
        // A dialog (file chooser, preferences…) opens over its parent, sized to fit it.
        val parent = info.transientFor.takeIf { it != 0 }?.let { Key(container, it) }
        val parentHost = parent?.let { hosts[it] ?: dialogs[it] }
        if (parentHost != null) {
            dialogs[key] = parentHost
            parentHost.showDialog(xid, bounds)
            return
        }
        if (parent != null) {
            // Its window is gone (for example the task was closed and the application asks
            // whether to save): it opens on its own, at its own size, never stretched.
            open(container, xid, dialog = true)
            return
        }
        // The launch's own activity takes the application's first window.
        val waitingHost = waiting.firstOrNull { it.container == container }
        if (waitingHost != null) {
            waiting -= waitingHost
            waitingHost.bind(info)
            return
        }
        // Further top-level windows (new documents) open as tasks of their own.
        open(container, xid, dialog = false)
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
        val type = if (dialog) LinuxDialogActivity::class.java else LinuxWindowActivity::class.java
        opener.startActivity(Intent(opener, type)
            .putExtra(LinuxWindowActivity.EXTRA_CONTAINER, container)
            .putExtra(LinuxWindowActivity.EXTRA_XID, xid)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                (if (opener is Activity) 0 else Intent.FLAG_ACTIVITY_NEW_TASK)))
    }

    /** Menus and tooltips are drawn inside the window they belong to. */
    private fun showPopup(container: String, xid: Int, bounds: Rect, transientFor: Int) {
        val host = hosts[Key(container, transientFor)] ?: dialogs[Key(container, transientFor)]
            ?: hosts.values.firstOrNull { it.container == container && it.screenBounds().contains(bounds.left, bounds.top) }
            ?: focused?.takeIf { it.container == container }
            ?: return
        popups[Key(container, xid)] = host
        host.showPopup(xid, bounds)
    }

    private fun decodeIcon(encoded: String): Bitmap? = runCatching {
        if (encoded.isEmpty() || encoded.length > 256 * 1024) return null
        val bytes = android.util.Base64.decode(encoded, android.util.Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}
