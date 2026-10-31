package moe.n4tsu.dextop.linux

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.PixelCopy
import android.view.TextureView
import android.graphics.SurfaceTexture
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import moe.n4tsu.dextop.plasma.LinuxPointerForwarder
import moe.n4tsu.dextop.plasma.LinuxPointerState
import kotlin.math.roundToInt

/**
 * One Linux application window as an Android task. Its surface shows the X window, which is
 * kept at this window's place and size on the screen, so menus open where they belong.
 */
internal open class LinuxWindowActivity : Activity() {
    companion object {
        const val EXTRA_CONTAINER = "container"
        const val EXTRA_DESKTOP = "desktop"
        const val EXTRA_LABEL = "label"
        const val EXTRA_XID = "xid"
        /** PNG of the application's icon, shown for the task instead of Dextop's. */
        const val EXTRA_ICON = "icon"
        /** Keys Android keeps (navigation and volume). */
        private val SYSTEM_KEYS = setOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_APP_SWITCH, KeyEvent.KEYCODE_POWER)
        private const val KEY_BACKSPACE = 14
        private const val KEY_ENTER = 28
    }

    var container = ""
        private set
    /** The X window this activity shows (0 while it waits for one). */
    val shownXid get() = xid
    private var xid = 0
    private var closedByLinux = false
    private lateinit var root: FrameLayout
    private lateinit var content: LinuxSurface
    private lateinit var status: LinearLayout
    private lateinit var statusIcon: android.widget.ImageView
    private lateinit var statusTitle: TextView
    private lateinit var statusProgress: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var statusDetail: TextView
    private lateinit var statusAction: TextView
    /** What the launch is doing now, shown with the time it has taken so far. */
    private var phase = ""
    private var phaseSince = 0L
    private val tick = object : Runnable {
        override fun run() {
            if (status.visibility != View.VISIBLE || statusProgress.visibility != View.VISIBLE) return
            val seconds = (android.os.SystemClock.elapsedRealtime() - phaseSince) / 1000
            statusText.text = if (seconds >= 2) "$phase  ${seconds}s" else phase
            root.postDelayed(this, 1000)
        }
    }
    private val popupViews = HashMap<Int, LinuxTexture>()
    private val popupBounds = HashMap<Int, Rect>()
    /** Open dialogs, bottom to top, with the size each asked for. */
    private val dialogViews = LinkedHashMap<Int, LinuxTexture>()
    private val dialogRequests = HashMap<Int, Rect>()
    private val dialogPlaced = HashMap<Int, Rect>()
    /** Light grey veil right under the topmost dialog; it keeps touches from the windows below. */
    private lateinit var scrim: View
    private val lastPlace = Rect()
    /** A main window whose application keeps a size of its own, shown scaled to fit (see [actualSize]). */
    private var scaled = false
    /** The differing size the application reported last; the same one twice means it keeps it. */
    private var kept: android.util.Size? = null
    /** When this window was last resized (by the user or Android), so a client request does not undo it. */
    private var resizedAt = 0L
    private var taskRequest: android.util.Size? = null
    /** The X window this activity shows, with its size as the application last reported it. */
    private var bound: LinuxAndroidWindows.Info? = null
    private var capturePollBusy = false
    private var captureSuppressed = false
    private var releaseShortcutDown = false
    private var mousePresent = false
    private var captureTarget = 0
    private var captureMotionLogged = false
    private val captureMotion = LinuxRelativeMotion()
    private val captureScroll = LinuxPointerState()
    private val capturePoll = object : Runnable {
        override fun run() {
            if (isDestroyed || !hasWindowFocus()) return
            // DeX can move a task without changing the size of its view hierarchy.
            place()
            if (!capturePollBusy && xid != 0 && LinuxContainerClient.service() != null) {
                capturePollBusy = true
                val target = dialogViews.keys.lastOrNull() ?: xid
                LinuxContainerClient.call(this@LinuxWindowActivity,
                    { it.pointerCaptureRequested(container, target) },
                    { requested ->
                        capturePollBusy = false
                        if (!isDestroyed && hasWindowFocus() && target == (dialogViews.keys.lastOrNull() ?: xid)) {
                            if (!requested) captureSuppressed = false
                            if (requested && !captureSuppressed && mousePresent) {
                                if (captureTarget != target) releaseCapture()
                                captureTarget = target
                                content.requestFocus()
                                if (!content.hasPointerCapture()) content.requestPointerCapture()
                            } else if (!requested) releaseCapture()
                        }
                    }, { capturePollBusy = false })
            }
            root.postDelayed(this, 100)
        }
    }

    private fun releaseCapture() {
        if (::content.isInitialized && content.hasPointerCapture()) content.releasePointerCapture()
        val linux = LinuxContainerClient.service()
        LinuxPointerForwarder.cancel(captureScroll) { button ->
            linux?.runCatching { relativePointer(container, captureTarget, 2, 0, 0, button) }
        }
        captureMotion.reset()
        captureMotionLogged = false
        captureScroll.buttons = 0
    }
    private class Attachment(val surface: android.view.Surface, var attempts: Int = 0)
    private val attachments = java.util.concurrent.ConcurrentHashMap<Int, Attachment>()

    private val input get() = "$container#apps"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!trusted) { finish(); return }
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setDimAmount(0f)
        container = intent.getStringExtra(EXTRA_CONTAINER).orEmpty()
        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        content = LinuxSurface(this) { xid }
        root.addView(content, FrameLayout.LayoutParams(-1, -1))
        content.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> place() }
        scrim = View(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            visibility = View.GONE
            isClickable = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        root.addView(scrim, FrameLayout.LayoutParams(-1, -1))
        // The launch screen: the application, what is happening now and its latest output.
        status = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(0x14, 0x16, 0x1a))
            setPadding(dp(32), dp(24), dp(32), dp(24))
            statusIcon = android.widget.ImageView(context).apply { visibility = View.GONE }
            addView(statusIcon, LinearLayout.LayoutParams(dp(72), dp(72)).apply { bottomMargin = dp(16) })
            statusTitle = TextView(context).apply {
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            }
            addView(statusTitle)
            statusProgress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                isIndeterminate = true
            }
            addView(statusProgress, LinearLayout.LayoutParams(dp(240), dp(16)).apply { topMargin = dp(16) })
            statusText = TextView(context).apply {
                setTextColor(Color.rgb(0xd6, 0xda, 0xe0))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                gravity = Gravity.CENTER
            }
            addView(statusText, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(10) })
            statusDetail = TextView(context).apply {
                setTextColor(Color.rgb(0x8f, 0x96, 0xa0))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = android.graphics.Typeface.MONOSPACE
                gravity = Gravity.CENTER
                maxLines = 3
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            addView(statusDetail, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            statusAction = TextView(context).apply {
                setTextColor(Color.rgb(0x9c, 0xca, 0xff))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setPadding(dp(16), dp(10), dp(16), dp(10))
                text = text("キャンセル", "Cancel")
                setOnClickListener { finishAndRemoveTask() }
            }
            addView(statusAction, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(16) })
        }
        root.addView(status, FrameLayout.LayoutParams(-1, -1))
        root.addView(ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_edit)
            alpha = .55f
            setBackgroundColor(0x66000000)
            contentDescription = text("キーボード", "Keyboard")
            setOnClickListener { toggleKeyboard() }
        }, FrameLayout.LayoutParams(dp(44), dp(44), Gravity.BOTTOM or Gravity.END).apply {
            setMargins(0, 0, dp(12), dp(12))
        })
        setContentView(root)
        // Android draws apps edge to edge; the X window stays clear of the bars, the cutout and the keyboard.
        root.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars() or
                android.view.WindowInsets.Type.displayCutout() or android.view.WindowInsets.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        root.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            // A resized window first offers the application all of its room again.
            if (floating) {
                bound?.bounds?.let { windowSized(it.width(), it.height()) }
            } else if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                resizedAt = android.os.SystemClock.uptimeMillis()
                unscale()
            }
            place()
        }

        launchDesktop = intent.getStringExtra(EXTRA_DESKTOP)
        launchLabel = intent.getStringExtra(EXTRA_LABEL)
        launchIcon = intent.getByteArrayExtra(EXTRA_ICON)?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        statusTitle.text = launchLabel.orEmpty()
        launchIcon?.let { statusIcon.setImageBitmap(it); statusIcon.visibility = View.VISIBLE }
        setPhase(text("準備しています", "Preparing"))
        // The task is the application from the start: its name and icon, not Dextop's.
        launchLabel?.let {
            @Suppress("DEPRECATION")
            setTaskDescription(ActivityManager.TaskDescription(it, launchIcon))
        }
        val requested = savedInstanceState?.getInt(EXTRA_XID, 0)
            ?.takeIf { it != 0 } ?: intent.getIntExtra(EXTRA_XID, 0)
        val desktop = intent.getStringExtra(EXTRA_DESKTOP)
        when {
            requested != 0 -> LinuxAndroidWindows.restore(this, container, requested)
            desktop != null && savedInstanceState == null -> LinuxAndroidWindows.startApplication(this, container, desktop)
            else -> finish()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(EXTRA_XID, xid)
        super.onSaveInstanceState(outState)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    /** Whether whoever opened this window may (only Dextop's launchers may open the exported one). */
    protected open val trusted get() = true

    /** Transient windows retain client size even when hosted inside a launcher APK's task. */
    protected open val floating get() = bound?.let { it.auxiliary || it.fixedSize } == true

    /** The desktop entry, name and icon this window was opened for. */
    var launchDesktop: String? = null
        private set
    var launchLabel: String? = null
        private set
    var launchIcon: Bitmap? = null
        private set
    /** When this window began waiting for its application's first window (elapsed realtime). */
    var waitingSince = 0L

    /** Takes over the X window [info]; called once the application maps it. */
    fun bind(info: LinuxAndroidWindows.Info) {
        // One surface shows one X window: let go of a window shown before.
        if (xid != 0 && xid != info.xid) {
            attachments.remove(xid)
            LinuxContainerClient.service()?.runCatching { detachWindow(container, xid) }
        }
        bound = info
        xid = info.xid
        if (info.desktopId == null && launchDesktop != null) {
            info.desktopId = launchDesktop
            info.appLabel = info.appLabel ?: launchLabel
            info.appIcon = info.appIcon ?: launchIcon
        }
        // Keep the window identity even if Android recreates this task.
        intent.putExtra(EXTRA_XID, xid)
        LinuxAndroidWindows.attached(this, container, xid)
        describe(info)
        setPhase(text("ウィンドウを接続しています", "Connecting the window"))
        if (floating) {
            // Android may impose minimum freeform task bounds. The viewport must still
            // retain the client's size instead of magnifying a non-resizable GTK dialog.
            windowSized(info.bounds.width(), info.bounds.height())
        }
        content.surfaceReady()?.let { attach(it, xid) }
        place()
        if (hasWindowFocus()) {
            LinuxContainerClient.service()?.runCatching { activateWindow(container, xid) }
            root.removeCallbacks(capturePoll)
            root.post(capturePoll)
        }
    }

    /**
     * The X window's actual size. An application that keeps a fixed size is shown at that
     * size in the middle of this window instead of being stretched to fill it.
     */
    fun windowSized(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val availableWidth = root.width - root.paddingLeft - root.paddingRight
        val availableHeight = root.height - root.paddingTop - root.paddingBottom
        if (availableWidth <= 0 || availableHeight <= 0) return
        // The surface keeps the application's pixels; SurfaceFlinger scales them to the view,
        // which has the same aspect ratio, so the picture fills the window without distortion.
        content.holder.setFixedSize(width, height)
        val params = content.layoutParams as FrameLayout.LayoutParams
        val fitted = LinuxDialogViewport.fit(width, height, availableWidth, availableHeight)
        val wantWidth = fitted.width
        val wantHeight = fitted.height
        if (params.width == wantWidth && params.height == wantHeight) return
        params.width = wantWidth
        params.height = wantHeight
        params.gravity = Gravity.CENTER
        content.layoutParams = params
    }

    /** Back to filling the window at its own resolution. */
    private fun unscale() {
        scaled = false
        kept = null
        content.holder.setSizeFromLayout()
        val params = content.layoutParams as FrameLayout.LayoutParams
        if (params.width != -1 || params.height != -1) {
            params.width = -1
            params.height = -1
            content.layoutParams = params
        }
    }

    /**
     * The application asked for a size of its own: with privileged access the Android window
     * is resized so its content area is exactly that, once per size and never right after the
     * user resized the window. Android-mode Linux cannot resize tasks and keeps the window.
     */
    private fun requestTaskSize(width: Int, height: Int) {
        if (!isInMultiWindowMode || !LinuxExecutionMode.privilegeAllowed(this)) return
        if (android.os.SystemClock.uptimeMillis() - resizedAt < 1000) return
        val wanted = android.util.Size(width, height)
        if (taskRequest == wanted) return
        val availableWidth = root.width - root.paddingLeft - root.paddingRight
        val availableHeight = root.height - root.paddingTop - root.paddingBottom
        if (availableWidth <= 0 || availableHeight <= 0 || (availableWidth == width && availableHeight == height)) return
        taskRequest = wanted
        val task = windowManager.currentWindowMetrics.bounds
        val screen = windowManager.maximumWindowMetrics.bounds
        val target = Rect(task.left, task.top, task.right + width - availableWidth, task.bottom + height - availableHeight)
        if (target.width() > screen.width() || target.height() > screen.height()) return
        target.offset(minOf(0, screen.right - target.right), minOf(0, screen.bottom - target.bottom))
        val id = taskId
        Thread({
            runCatching {
                moe.n4tsu.dextop.PrivilegedAccess("DextopLinuxWindow").execute("/system/bin/am", "task", "resize",
                    id.toString(), target.left.toString(), target.top.toString(), target.right.toString(), target.bottom.toString())
            }
        }, "DextopLinuxTaskResize").start()
    }

    private val reconcileSize = Runnable {
        lastPlace.setEmpty()
        place()
    }

    /**
     * The application asked to be [width] x [height]. A main window follows its Android
     * window, so stale application requests are coalesced before restoring its size.
     */
    fun clientRequested(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        if (floating) {
            windowSized(width, height)
            return
        }
        requestTaskSize(width, height)
        // Configure notifications can lag behind an Android resize. Repeated stale
        // sizes do not mean a client is fixed-size: that inference shrank the surface
        // back and left black margins. Dialogs have their own sizing path.
        root.removeCallbacks(reconcileSize)
        root.postDelayed(reconcileSize, 100)
    }

    /**
     * The X window's actual size, as the display reports it. A size other than the one asked
     * for came from the application (its own request), and is undone like one.
     */
    fun actualSize(width: Int, height: Int) {
        if (lastPlace.isEmpty) return
        if (width == lastPlace.width() && height == lastPlace.height()) {
            if (scaled) unscale()
            kept = null
            return
        }
        // The same other size twice, after the window was offered again: the application
        // keeps its own resolution. It is not changed; it is scaled to fit instead.
        val size = android.util.Size(width, height)
        if (!floating && kept == size) {
            scaled = true
            windowSized(width, height)
            return
        }
        kept = size
        clientRequested(width, height)
    }

    /** Names the task after the application (DeX shows it on the window), with its icon. */
    fun describe(info: LinuxAndroidWindows.Info) {
        val label = info.appLabel ?: launchLabel ?: info.title.ifBlank { info.wmClass.ifBlank { "Linux" } }
        @Suppress("DEPRECATION")
        setTaskDescription(ActivityManager.TaskDescription(label, info.appIcon ?: launchIcon ?: info.icon))
    }

    /** A small picture of the window, for choosing among an application's sessions. */
    fun capture(done: (Bitmap?) -> Unit) {
        val width = content.width
        val height = content.height
        if (width <= 0 || height <= 0 || content.surfaceReady() == null) return done(null)
        val scale = minOf(1f, 480f / width)
        val bitmap = Bitmap.createBitmap((width * scale).roundToInt().coerceAtLeast(1),
            (height * scale).roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        runCatching {
            PixelCopy.request(content, bitmap, { result ->
                if (result == PixelCopy.SUCCESS) done(bitmap)
                else { bitmap.recycle(); done(null) }
            }, android.os.Handler(mainLooper))
        }.onFailure { bitmap.recycle(); done(null) }
    }

    /** Brings this window's task to the front; it may be a launcher's task rather than Dextop's. */
    fun bringToFront(): Boolean = runCatching {
        getSystemService(ActivityManager::class.java).moveTaskToFront(taskId, 0)
        true
    }.getOrDefault(false)

    private fun text(ja: String, en: String) = moe.n4tsu.dextop.localText(ja, en)

    /** The launch's current step ("Starting the container"…), timed while it lasts. */
    fun setPhase(value: String) {
        if (value == phase) return
        phase = value
        phaseSince = android.os.SystemClock.elapsedRealtime()
        statusText.text = value
        root.removeCallbacks(tick)
        root.postDelayed(tick, 1000)
    }

    /** The application's latest output while it starts. */
    fun setDetail(line: String) {
        statusDetail.text = line
    }

    fun showError(message: String) {
        status.visibility = View.VISIBLE
        statusProgress.visibility = View.GONE
        root.removeCallbacks(tick)
        statusText.text = message.lineSequence().firstOrNull().orEmpty()
        statusDetail.maxLines = 12
        statusDetail.text = message.lineSequence().drop(1).filter { it.isNotBlank() }.toList().takeLast(12).joinToString("\n")
        statusAction.text = text("閉じる", "Close")
    }

    fun closedByLinux() {
        closedByLinux = true
        finishAndRemoveTask()
    }

    /** Where this window's content is on the screen, in X screen coordinates. */
    fun screenBounds(): Rect {
        val location = IntArray(2)
        content.getLocationOnScreen(location)
        return Rect(location[0], location[1], location[0] + content.width, location[1] + content.height)
    }

    /** Keeps the X window exactly under this window's content. */
    private fun place() {
        if (xid == 0 || content.width <= 0 || content.height <= 0) return
        // A GTK dialog can reject configure sizes. Do not turn its letterboxed/scaled
        // viewport into another client-size request (or race its pending layout).
        if (floating && content.isLayoutRequested) return
        // A scaled window still offers the application the whole window, which it may accept later.
        val bounds = if (scaled) Rect(screenBounds()).also { content ->
            val location = IntArray(2)
            root.getLocationOnScreen(location)
            content.set(location[0] + root.paddingLeft, location[1] + root.paddingTop,
                location[0] + root.width - root.paddingRight, location[1] + root.height - root.paddingBottom)
        } else screenBounds()
        if (bounds == lastPlace) return
        lastPlace.set(bounds)
        LinuxContainerClient.service()?.runCatching {
            val client = bound?.bounds
            val width = if (floating) client?.width()?.coerceAtLeast(1) ?: bounds.width() else bounds.width()
            val height = if (floating) client?.height()?.coerceAtLeast(1) ?: bounds.height() else bounds.height()
            configureWindow(container, xid, bounds.left, bounds.top, width, height)
        }
        // Slow clients may still be committing the previous size. Never infer a fixed-size
        // main window from a timeout and shrink the Android content back to that stale size.
        dialogRequests.forEach { (dialog, rect) -> fitDialog(dialog, rect) }
        popupBounds.forEach { (popup, rect) -> placePopup(popup, rect) }
    }

    private fun attach(surface: android.view.Surface?, target: Int) {
        if (surface == null || !surface.isValid || target == 0 || isDestroyed) return
        val request = Attachment(surface)
        attachments[target] = request
        fun attempt() {
            if (attachments[target] !== request || !surface.isValid || isDestroyed) return
            request.attempts++
            fun failed(message: String) {
                if (attachments[target] !== request || isDestroyed || !surface.isValid) return
                if (request.attempts < 6) root.postDelayed({ attempt() }, (100L shl (request.attempts - 1)))
                else {
                    android.util.Log.e("DextopLinuxWindow", "Attach failed container=$container xid=$target: $message")
                    if (target == xid) showError(text("ウィンドウを表示できませんでした", "Unable to display the window") + "\n$message")
                }
            }
            LinuxContainerClient.call(this, {
                attachments[target] === request && surface.isValid && !isDestroyed && it.attachWindow(container, target, surface)
            }, { attached ->
                if (attachments[target] !== request || isDestroyed) return@call
                if (target != xid) android.util.Log.i("DextopLinuxWindow", "attached 0x${target.toString(16)}: $attached")
                if (!attached) failed("attachWindow returned false")
                else if (target == xid) {
                    root.removeCallbacks(tick)
                    status.visibility = View.GONE
                }
            }) { failed(it.message ?: it.toString()) }
        }
        attempt()
    }

    /**
     * Where this window's X window is on the X screen. It normally sits under this window's
     * content, but an application may move itself (Wine does), and its popups come in X
     * screen coordinates.
     */
    private fun xOrigin(): android.graphics.Point {
        val x = if (LinuxAppSession.isWayland(xid)) screenBounds()
            else bound?.bounds?.takeIf { !it.isEmpty } ?: screenBounds()
        return android.graphics.Point(x.left, x.top)
    }

    /** [bounds] on the X screen, moved to where this window shows its X window on Android's. */
    private fun toScreen(bounds: Rect): Rect {
        val origin = xOrigin()
        val content = screenBounds()
        val client = bound?.bounds
        val scaleX = content.width().toDouble() / (client?.width()?.takeIf { it > 0 } ?: content.width().coerceAtLeast(1))
        val scaleY = content.height().toDouble() / (client?.height()?.takeIf { it > 0 } ?: content.height().coerceAtLeast(1))
        return Rect(content.left + ((bounds.left - origin.x) * scaleX).roundToInt(),
            content.top + ((bounds.top - origin.y) * scaleY).roundToInt(),
            content.left + ((bounds.right - origin.x) * scaleX).roundToInt(),
            content.top + ((bounds.bottom - origin.y) * scaleY).roundToInt())
    }

    /** Popups shown as windows of their own over the screen (the others sit inside this window). */
    private val popupOverlays = HashSet<Int>()

    /**
     * A menu, tooltip or drop-down list: an undecorated window of its own at the popup's exact
     * place and size on the screen, so it is never clipped by this window. X places windows in
     * screen coordinates, the same as Android's. Without permission to draw over other apps it
     * is shown inside this window instead.
     */
    fun showPopup(popup: Int, bounds: Rect) {
        if (popupViews.containsKey(popup)) return placePopup(popup, bounds)
        val view = LinuxTexture(this) { popup }
        popupViews[popup] = view
        popupBounds[popup] = Rect(bounds)
        val overlay = android.provider.Settings.canDrawOverlays(this) &&
            runCatching { windowManager.addView(view, popupParams(toScreen(bounds))) }.isSuccess
        android.util.Log.i("DextopLinuxWindow", "popup 0x${popup.toString(16)} x=$bounds origin=${xOrigin()} " +
            "content=${screenBounds()} overlay=$overlay")
        if (overlay) {
            popupOverlays += popup
        } else {
            root.addView(view, FrameLayout.LayoutParams(1, 1))
            view.bringToFront()
            placePopup(popup, bounds)
        }
    }

    private fun popupParams(bounds: Rect) = android.view.WindowManager.LayoutParams(
        bounds.width().coerceAtLeast(1), bounds.height().coerceAtLeast(1),
        android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        // Menus with shadows and rounded corners are partly transparent.
        android.graphics.PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = bounds.left
        y = bounds.top
        title = "Linux popup"
    }

    /** Opens a dialog of this application over it, at most as large as this window allows. */
    fun showDialog(dialog: Int, requested: Rect) {
        if (dialogViews.containsKey(dialog)) return fitDialog(dialog, requested)
        val view = LinuxTexture(this) { dialog }
        root.addView(view, FrameLayout.LayoutParams(1, 1))
        dialogViews[dialog] = view
        fitDialog(dialog, requested)
        restack()
        view.alpha = 0f
        view.scaleX = .94f; view.scaleY = .94f
        view.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180).start()
        LinuxContainerClient.service()?.runCatching { activateWindow(container, dialog) }
    }

    /** Centres the dialog in this window, shrunk to leave a margin when it asks for more room. */
    fun fitDialog(dialog: Int, requested: Rect) {
        dialogRequests[dialog] = Rect(requested)
        val view = dialogViews[dialog] ?: return
        val area = screenBounds()
        if (area.width() <= 0 || area.height() <= 0) return
        val margin = dp(24)
        val maxWidth = (area.width() - margin * 2).coerceAtLeast(dp(120))
        val maxHeight = (area.height() - margin * 2).coerceAtLeast(dp(120))
        val width = (if (requested.width() > 0) requested.width() else area.width() * 7 / 10).coerceIn(1, maxWidth)
        val height = (if (requested.height() > 0) requested.height() else area.height() * 7 / 10).coerceIn(1, maxHeight)
        val left = (area.width() - width) / 2
        val top = (area.height() - height) / 2
        val params = view.layoutParams as FrameLayout.LayoutParams
        params.width = width
        params.height = height
        params.leftMargin = left
        params.topMargin = top
        params.gravity = Gravity.TOP or Gravity.START
        view.layoutParams = params
        val placed = Rect(area.left + left, area.top + top, area.left + left + width, area.top + top + height)
        if (dialogPlaced[dialog] == placed) return
        dialogPlaced[dialog] = placed
        LinuxContainerClient.service()?.runCatching { configureWindow(container, dialog, placed.left, placed.top, width, height) }
    }

    fun closeDialog(dialog: Int) {
        attachments.remove(dialog)
        dialogRequests.remove(dialog)
        dialogPlaced.remove(dialog)
        val view = dialogViews.remove(dialog) ?: return
        LinuxContainerClient.service()?.runCatching { detachWindow(container, dialog) }
        view.animate().alpha(0f).scaleX(.96f).scaleY(.96f).setDuration(140).withEndAction { root.removeView(view) }.start()
        restack()
        val next = dialogViews.keys.lastOrNull() ?: xid
        if (next != 0) LinuxContainerClient.service()?.runCatching { activateWindow(container, next) }
    }

    /** Puts the veil right under the topmost dialog, and menus above everything. */
    private fun restack() {
        val top = dialogViews.values.lastOrNull()
        if (top == null) {
            if (scrim.visibility == View.VISIBLE) scrim.animate().alpha(0f).setDuration(140).withEndAction { scrim.visibility = View.GONE }.start()
        } else {
            root.removeView(scrim)
            root.addView(scrim, root.indexOfChild(top), FrameLayout.LayoutParams(-1, -1))
            if (scrim.visibility != View.VISIBLE) {
                scrim.alpha = 0f
                scrim.visibility = View.VISIBLE
            }
            scrim.animate().alpha(1f).setDuration(160).start()
        }
        popupViews.values.forEach(View::bringToFront)
    }

    fun placePopup(popup: Int, bounds: Rect) {
        popupBounds[popup] = Rect(bounds)
        val view = popupViews[popup] ?: return
        if (popup in popupOverlays) {
            runCatching { windowManager.updateViewLayout(view, popupParams(toScreen(bounds))) }
            return
        }
        val placed = toScreen(bounds)
        val rootOrigin = IntArray(2).also(root::getLocationOnScreen)
        val params = view.layoutParams as FrameLayout.LayoutParams
        params.width = placed.width().coerceAtLeast(1)
        params.height = placed.height().coerceAtLeast(1)
        params.leftMargin = placed.left - rootOrigin[0] - root.paddingLeft
        params.topMargin = placed.top - rootOrigin[1] - root.paddingTop
        params.gravity = Gravity.TOP or Gravity.START
        view.layoutParams = params
    }

    fun removePopup(popup: Int) {
        attachments.remove(popup)
        popupBounds.remove(popup)
        popupViews.remove(popup)?.let { view ->
            LinuxContainerClient.service()?.runCatching { detachWindow(container, popup) }
            if (popupOverlays.remove(popup)) runCatching { windowManager.removeViewImmediate(view) }
            else root.removeView(view)
        }
    }

    private val clipboardChanged = android.content.ClipboardManager.OnPrimaryClipChangedListener {
        LinuxClipboard.toLinux(this, container)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        val clipboard = getSystemService(android.content.ClipboardManager::class.java)
        if (!hasFocus) {
            // Keys held while focus moves away would stay down in Linux.
            releaseKeys()
            releasePointers()
            root.removeCallbacks(capturePoll)
            releaseCapture()
            clipboard?.removePrimaryClipChangedListener(clipboardChanged)
            return
        }
        if (xid == 0) return
        LinuxAndroidWindows.focused(this)
        LinuxContainerClient.service()?.runCatching { activateWindow(container, xid) }
        content.requestFocus()
        root.removeCallbacks(capturePoll)
        root.post(capturePoll)
        captureMetaKey()
        LinuxClipboard.toLinux(this, container)
        clipboard?.addPrimaryClipChangedListener(clipboardChanged)
    }

    /** Asks DeX to pass the Meta (Windows) key and its shortcuts to this window. */
    private fun captureMetaKey() {
        runCatching {
            val type = Class.forName("com.samsung.android.view.SemWindowManager")
            val manager = type.getMethod("getInstance").invoke(null)
            type.getMethod("requestMetaKeyEvent", android.content.ComponentName::class.java, Boolean::class.javaPrimitiveType)
                .invoke(manager, componentName, true)
        }
    }

    /** evdev codes pressed in Linux and not released yet. */
    private val pressedKeys = LinkedHashSet<Int>()

    private fun releaseKeys() {
        if (pressedKeys.isEmpty()) return
        val linux = LinuxContainerClient.service()
        pressedKeys.toList().asReversed().forEach { code -> linux?.runCatching { key(input, code, false) } }
        pressedKeys.clear()
    }

    /**
     * Sends a key to Linux as the keyboard's own key (its scan code), so shortcuts, modifiers
     * and key repeat behave as on a Linux desktop. Returns false for keys Android keeps.
     */
    fun forwardKey(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_FORWARD_DEL && releaseShortcutDown && event.action == KeyEvent.ACTION_UP) {
            releaseShortcutDown = false
            return true
        }
        if (event.keyCode == KeyEvent.KEYCODE_FORWARD_DEL && event.isCtrlPressed && event.isAltPressed) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                releaseShortcutDown = true
                captureSuppressed = true
                releaseCapture()
                releasePointers()
                releaseKeys()
                LinuxContainerClient.service()?.runCatching { activateWindow(container, 0) }
            }
            return true
        }
        if (xid == 0 || event.keyCode in SYSTEM_KEYS) return false
        val linux = LinuxContainerClient.service() ?: return false
        val down = event.action == KeyEvent.ACTION_DOWN
        if (event.action == KeyEvent.ACTION_MULTIPLE) {
            if (event.characters.isNullOrEmpty()) return false
            linux.runCatching { text(input, event.characters) }
            return true
        }
        val paste = (event.isCtrlPressed && event.keyCode == KeyEvent.KEYCODE_V) ||
            (event.isShiftPressed && event.keyCode == KeyEvent.KEYCODE_INSERT)
        if (down && paste && event.repeatCount == 0) LinuxClipboard.toLinux(this, container)
        val code = LinuxKeymap.evdev(event)
        when {
            code != null -> {
                if (down) pressedKeys += code else pressedKeys -= code
                linux.runCatching { key(input, code, down) }
            }
            down && event.unicodeChar > 0 -> linux.runCatching { text(input, String(Character.toChars(event.unicodeChar))) }
            else -> return false
        }
        return true
    }

    override fun onBackPressed() {
        // Back leaves the window open, like any other application in the background.
        moveTaskToBack(true)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        LinuxAndroidPads.dispatch(this, container, event) || forwardKey(event) || super.dispatchKeyEvent(event)

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
        LinuxAndroidPads.dispatch(this, container, event) || super.dispatchGenericMotionEvent(event)

    override fun onPause() {
        root.removeCallbacks(capturePoll)
        releaseCapture()
        releaseKeys()
        releasePointers()
        super.onPause()
    }

    private fun toggleKeyboard() {
        content.requestFocus()
        getSystemService(InputMethodManager::class.java)?.toggleSoftInput(0, 0)
    }

    override fun onDestroy() {
        if (!trusted) return super.onDestroy()
        attachments.clear()
        root.removeCallbacks(reconcileSize)
        root.removeCallbacks(capturePoll)
        releaseCapture()
        root.removeCallbacks(tick)
        LinuxAndroidWindows.gone(this)
        popupViews.keys.toList().forEach(::removePopup)
        dialogViews.keys.forEach { dialog -> LinuxContainerClient.service()?.runCatching { detachWindow(container, dialog) } }
        if (xid != 0) LinuxContainerClient.service()?.runCatching {
            detachWindow(container, xid)
            // Closing the task closes the window; one the application closed itself is gone already.
            if (isFinishing && !closedByLinux) closeWindow(container, xid)
        }
        super.onDestroy()
    }

    /**
     * Pointer and wheel input for one X window, in that window's own coordinates, handled as
     * in the Plasma desktop: touchpad swipes and two fingers scroll, pinches zoom, smooth
     * wheels add up to whole steps, and a touch presses only once it moves or lifts.
     */
    private val pointerInputs = java.util.Collections.newSetFromMap(java.util.WeakHashMap<PointerInput, Boolean>())

    private fun releasePointers() { pointerInputs.forEach { it.cancel() } }

    private inner class PointerInput(private val target: () -> Int, private val viewport: View) {
        private val state = LinuxPointerState()
        private var lastPointerX = 0
        private var lastPointerY = 0

        init { pointerInputs += this }

        fun cancel() {
            LinuxPointerForwarder.cancel(state) { button ->
                LinuxContainerClient.service()?.runCatching {
                    windowPointer(container, target(), 2, lastPointerX, lastPointerY, button)
                }
            }
        }

        private fun forward(event: MotionEvent) {
            if (event.isFromSource(InputDevice.SOURCE_MOUSE)) {
                mousePresent = true
                // A deliberate click resumes a capture released with Ctrl+Alt+Delete.
                if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_BUTTON_PRESS)
                    captureSuppressed = false
            }
            val id = target()
            if (id == 0) return
            val client = LinuxAndroidWindows.windowBounds(container, id)
            lastPointerX = LinuxDialogViewport.coordinate(event.x, viewport.width, client?.width() ?: viewport.width)
            lastPointerY = LinuxDialogViewport.coordinate(event.y, viewport.height, client?.height() ?: viewport.height)
            val linux = LinuxContainerClient.service() ?: return
            LinuxPointerForwarder.forward(event, lastPointerX, lastPointerY, state,
                resources.displayMetrics.density,
                send = { action, x, y, button -> linux.windowPointer(container, id, action, x, y, button) },
                scroll = { dx, dy -> linux.scroll(input, dx, dy) },
                key = { code, down -> linux.key(input, code, down) },
            )
        }

        fun touch(event: MotionEvent): Boolean {
            forward(event)
            return true
        }

        fun generic(event: MotionEvent): Boolean = when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE, MotionEvent.ACTION_SCROLL,
            MotionEvent.ACTION_BUTTON_PRESS, MotionEvent.ACTION_BUTTON_RELEASE -> { forward(event); true }
            else -> false
        }
    }

    /** Main output goes straight to SurfaceFlinger, avoiding TextureView's HWUI copy. */
    @SuppressLint("ViewConstructor")
    private inner class LinuxSurface(context: Context, private val target: () -> Int) :
        SurfaceView(context), SurfaceHolder.Callback {
        private var surface: Surface? = null
        private val pointer = PointerInput(target, this)

        init {
            holder.addCallback(this)
            isFocusable = true
            isFocusableInTouchMode = true
        }

        fun surfaceReady(): Surface? = surface?.takeIf { it.isValid }

        override fun surfaceCreated(holder: SurfaceHolder) {
            surface = holder.surface
            requestDisplayRate()
            attach(surface, target())
            place()
        }

        // The holder owns this Surface. Keep its producer attached during a resize.
        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            requestDisplayRate()
            place()
        }

        private fun requestDisplayRate() {
            val rate = display?.refreshRate ?: return
            if (rate.isFinite() && rate > 0) surface?.runCatching {
                setFrameRate(rate, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT, Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS)
            }
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            val id = target()
            attachments.remove(id)
            if (id != 0) LinuxContainerClient.service()?.runCatching { detachWindow(container, id) }
            surface = null
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) requestFocus()
            return pointer.touch(event)
        }

        override fun onGenericMotionEvent(event: MotionEvent) = pointer.generic(event) || super.onGenericMotionEvent(event)

        override fun onPointerCaptureChange(hasCapture: Boolean) {
            super.onPointerCaptureChange(hasCapture)
            if (hasCapture) releasePointers() else releaseCapture()
        }

        override fun onCapturedPointerEvent(event: MotionEvent): Boolean {
            if (!hasWindowFocus() || captureSuppressed || captureTarget == 0) { releaseCapture(); return true }
            val linux = LinuxContainerClient.service() ?: return true
            if (!captureMotionLogged && (event.actionMasked == MotionEvent.ACTION_MOVE || event.actionMasked == MotionEvent.ACTION_HOVER_MOVE)) {
                captureMotionLogged = true
                android.util.Log.i("DextopLinuxWindow", "captured motion target=$captureTarget source=${event.source} " +
                    "xy=${event.x},${event.y} relative=${event.getAxisValue(MotionEvent.AXIS_RELATIVE_X)},${event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y)}")
            }
            LinuxPointerForwarder.captured(event, captureScroll, captureMotion,
                send = { action, dx, dy, button -> linux.relativePointer(container, captureTarget, action, dx, dy, button) },
                scroll = { dx, dy -> linux.scroll(input, dx, dy) })
            return true
        }

        override fun onCheckIsTextEditor() = true

        override fun dispatchKeyEventPreIme(event: KeyEvent): Boolean {
            val physical = event.deviceId != android.view.KeyCharacterMap.VIRTUAL_KEYBOARD && event.device?.isVirtual == false
            return (physical && forwardKey(event)) || super.dispatchKeyEventPreIme(event)
        }

        override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
            outAttrs.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
            return object : BaseInputConnection(this, false) {
                override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
                    if (text.toString() == "\n") tap(KEY_ENTER)
                    else LinuxContainerClient.service()?.runCatching { text(input, text.toString()) }
                    return true
                }

                override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                    repeat(beforeLength.coerceAtMost(64)) { tap(KEY_BACKSPACE) }
                    return true
                }

                override fun sendKeyEvent(event: KeyEvent): Boolean = forwardKey(event)

                override fun performEditorAction(actionCode: Int): Boolean { tap(KEY_ENTER); return true }
            }
        }

        private fun tap(code: Int) {
            LinuxContainerClient.service()?.runCatching { key(input, code, true); key(input, code, false) }
        }
    }

    /**
     * A dialog or menu drawn inside the view hierarchy, so it stacks above the main window and
     * the veil like any other view.
     */
    @SuppressLint("ViewConstructor")
    private inner class LinuxTexture(context: Context, private val target: () -> Int) :
        TextureView(context), TextureView.SurfaceTextureListener {
        private var surface: Surface? = null
        private val pointer = PointerInput(target, this)

        init {
            surfaceTextureListener = this
            // Popups and dialogs with shadows or rounded corners are partly transparent.
            isOpaque = false
        }

        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
            surface = Surface(texture)
            attach(surface, target())
        }

        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
            // The producer stays attached while the Android texture changes size.
        }

        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
            attachments.remove(target())
            LinuxContainerClient.service()?.runCatching { detachWindow(container, target()) }
            surface?.release()
            surface = null
            return true
        }

        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent) = pointer.touch(event)

        override fun onGenericMotionEvent(event: MotionEvent) = pointer.generic(event) || super.onGenericMotionEvent(event)
    }
}

/** A Linux dialog shown on its own (its parent window is gone), as a floating window. */
internal class LinuxDialogActivity : LinuxWindowActivity() {
    override val floating = true
}

/**
 * A Linux window opened by a launcher APK's Window activity, inside that launcher's task:
 * Android and DeX then show the Linux application's name and icon for it.
 */
internal class LinuxHostedWindowActivity : LinuxWindowActivity() {
    override val trusted get() = LinuxShortcutInstaller.trustedHost(this, callingActivity?.packageName,
        intent.getStringExtra(EXTRA_CONTAINER), intent.getStringExtra(EXTRA_DESKTOP))
}
