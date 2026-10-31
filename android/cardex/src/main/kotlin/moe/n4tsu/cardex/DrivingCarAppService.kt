package moe.n4tsu.cardex

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.view.MotionEvent
import androidx.car.app.AppManager
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.ScreenManager
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Template
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.navigation.model.MessageInfo
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.validation.HostValidator
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.LifecycleEventObserver

/** Driving-safe projection host. Full input remains on the phone controller. */
class DrivingCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    override fun onCreateSession(): Session = DrivingSession()
}

private class DrivingSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen = DrivingScreen(carContext)
}

private class DrivingScreen(context: androidx.car.app.CarContext) : Screen(context), SurfaceCallback {
    /** Dextop starts as soon as Android Auto opens the app; Stop on the strip ends it. */
    private var desktopRequested = true
    private var relay: Messenger? = null
    private var container: SurfaceContainer? = null
    private var visibleArea: android.graphics.Rect? = null
    private var stableArea: android.graphics.Rect? = null
    private var startedSurface: android.view.Surface? = null
    private var startedWidth = 0
    private var startedHeight = 0
    private var startedDpi = 0
    private var renderScale = 1f
    private var splitPlacement = ""
    private var panDrag = false
    /** Dextop Auto's setting for the drag-mode button; the menu button is required by the template. */
    private var showDragButton = true
    private var panGestureActive = false
    private val panIdle = Runnable { if (!panDrag) sendPan(0f, 0f, release = true) }
    private var relayStatus = STATUS_IDLE
    private var errorDetail = ""
    private var connected = false
    private var relayBound = false
    private var relayReconnectAttempts = 0
    private val relayHandler = Handler(Looper.getMainLooper())
    private val startupTimeout = Runnable {
        if (desktopRequested && relayStatus != STATUS_RUNNING && relayStatus != STATUS_ERROR) {
            showError(
                when {
                    !connected -> "Dextop Car Companion could not connect to the Dextop relay service. Open Dextop and confirm that Stellar or Shizuku is available."
                    container == null -> "Android Auto did not provide a destination surface. Return to the launcher and open Dextop・Driving again."
                    else -> "Dextop did not finish starting within ${STARTUP_TIMEOUT_MS / 1_000} seconds. Open Dextop and check its operation log."
                }
            )
        }
    }
    private val statusHandler = Handler(Looper.getMainLooper()) { message ->
        if (message.what == MSG_STATUS) {
            val previousStatus = relayStatus
            val previousDetail = errorDetail
            val previousPlacement = splitPlacement
            relayStatus = message.data.getInt(KEY_STATUS, STATUS_IDLE)
            errorDetail = message.data.getString(KEY_DETAIL).orEmpty()
            splitPlacement = message.data.getString("split_placement").orEmpty()
            val previousDragButton = showDragButton
            showDragButton = message.data.getBoolean("show_drag_button", showDragButton)
            if (!showDragButton && panDrag) { sendPan(0f, 0f, release = true); panDrag = false }
            if (relayStatus == STATUS_RUNNING || relayStatus == STATUS_ERROR) {
                relayHandler.removeCallbacks(startupTimeout)
            } else if (relayStatus == STATUS_STARTING && errorDetail != previousDetail) {
                // Dextop reported a new startup step (reconnecting its access, restarting the
                // display): it is still working, so the wait starts again from this step.
                scheduleStartupTimeout()
            }
            // Android Auto counts every changed template as a step and closes the app after five:
            // startup progress keeps one fixed template, and only a changed error is redrawn.
            if (previousStatus != relayStatus || previousPlacement != splitPlacement ||
                previousDragButton != showDragButton ||
                (splitPlacement.isBlank() && relayStatus == STATUS_ERROR && previousDetail != errorDetail)) invalidate()
        }
        true
    }
    private val incoming = Messenger(statusHandler)
    private val heartbeat = object : Runnable {
        override fun run() {
            relay?.takeIf { desktopRequested }?.let { target ->
                runCatching { target.send(Message.obtain(null, MSG_HEARTBEAT).apply { replyTo = incoming }) }
            }
            if (relayBound) relayHandler.postDelayed(this, HEARTBEAT_INTERVAL_MS)
        }
    }
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder == null) {
                showError("Dextop returned an empty relay connection. Reopen Dextop and try again.")
                return
            }
            relay = Messenger(binder)
            connected = true
            relayReconnectAttempts = 0
            startedSurface = null
            relayHandler.removeCallbacks(heartbeat)
            heartbeat.run()
            if (desktopRequested) container?.let(::startRelay)
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            relay = null
            connected = false
            startedSurface = null
            relayHandler.removeCallbacks(heartbeat)
            if (desktopRequested) showError("The connection to Dextop was lost. The Dextop process may have stopped or crashed.")
        }

        override fun onBindingDied(name: ComponentName?) {
            if (relayBound) runCatching { carContext.unbindService(this) }
            relay = null
            connected = false
            relayBound = false
            startedSurface = null
            relayHandler.removeCallbacks(heartbeat)
            if (desktopRequested) showError("The Dextop relay connection died and must be reconnected.")
            if (++relayReconnectAttempts <= 3) relayHandler.postDelayed({
                if (desktopRequested && lifecycle.currentState != androidx.lifecycle.Lifecycle.State.DESTROYED) {
                    relayStatus = STATUS_STARTING
                    scheduleStartupTimeout()
                    bindRelay()
                }
            }, 500)
        }

        override fun onNullBinding(name: ComponentName?) {
            if (relayBound) runCatching { carContext.unbindService(this) }
            relay = null
            connected = false
            relayBound = false
            if (desktopRequested) showError("The installed Dextop build does not provide a usable Car Companion relay service.")
        }
    }

    init {
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(this)
        bindRelay()
        scheduleStartupTimeout()
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_DESTROY) {
                sendPan(0f, 0f, release = true)
                relayHandler.removeCallbacks(panIdle)
            }
            if (event == androidx.lifecycle.Lifecycle.Event.ON_DESTROY && relayBound) {
                relayHandler.removeCallbacks(heartbeat)
                relayHandler.removeCallbacks(startupTimeout)
                runCatching { carContext.unbindService(connection) }
                relayBound = false
                relay = null
            }
        })
    }

    override fun onGetTemplate(): Template {
        if (splitPlacement.isNotBlank()) {
            val state = org.json.JSONObject(splitPlacement)
            val items = ItemList.Builder()
            fun option(label: String, action: String, icon: String = "") {
                val row = Row.Builder().setTitle(label).setOnClickListener {
                    sendAction("split:$action")
                }
                if (icon.isNotEmpty()) runCatching {
                    val bytes = android.util.Base64.decode(icon, android.util.Base64.DEFAULT)
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let {
                        row.setImage(CarIcon.Builder(IconCompat.createWithBitmap(it)).build(), Row.IMAGE_TYPE_SMALL)
                    }
                }
                items.addItem(row.build())
            }
            if (state.optString("side").isEmpty()) {
                option("Left", "left")
                option("Right", "right")
            } else {
                val apps = state.optJSONArray("apps") ?: org.json.JSONArray()
                for (i in 0 until apps.length()) {
                    val app = apps.getJSONObject(i)
                    option(app.getString("label"), "app:${app.getString("package")}", app.optString("icon"))
                }
                if (state.optInt("page") > 0) option("▲ Previous page", "previous")
                if (state.optInt("page") + 1 < state.optInt("pages")) option("▼ Next page", "next")
            }
            option("Cancel", "cancel")
            return ListTemplate.Builder().setTitle(
                if (state.optString("side").isEmpty()) "Place app: Left or Right?"
                else "Other app (${state.optInt("page") + 1}/${state.optInt("pages")})"
            ).setSingleList(items.build()).build()
        }
        // One NavigationTemplate from start to finish. Android Auto counts every change of template
        // as a step and closes the app after five, so starting, errors and the stopped state are a
        // message inside the same template; updating it is a refresh, not a step.
        fun icon(resource: Int) = CarIcon.Builder(IconCompat.createWithResource(carContext, resource)).build()
        val strip = ActionStrip.Builder()
        when {
            !desktopRequested -> strip.addAction(Action.Builder().setIcon(icon(android.R.drawable.ic_media_play))
                .setOnClickListener {
                    desktopRequested = true
                    relayStatus = STATUS_STARTING
                    errorDetail = ""
                    scheduleStartupTimeout()
                    if (!relayBound) bindRelay()
                    invalidate()
                    container?.let(::startRelay)
                }.build())
            relayStatus == STATUS_ERROR -> {
                strip.addAction(Action.Builder().setIcon(icon(android.R.drawable.ic_popup_sync)).setOnClickListener {
                    // As Reconnect: the surface is sent again, so Dextop restarts even without a display yet.
                    relayStatus = STATUS_STARTING
                    errorDetail = ""
                    startedSurface = null
                    scheduleStartupTimeout()
                    invalidate()
                    if (relay == null) bindRelay()
                    container?.takeIf { it.surface?.isValid == true }?.let(::startRelay) ?: requestSurface()
                }.build())
                strip.addAction(Action.Builder().setIcon(icon(android.R.drawable.ic_media_pause))
                    .setOnClickListener { stopDextop() }.build())
            }
            else -> strip.addAction(Action.Builder().setIcon(icon(android.R.drawable.ic_menu_manage))
                .setOnClickListener { openQuickMenu() }.build())
        }
        val message = when {
            !desktopRequested -> MessageInfo.Builder("Dextop is stopped").setText("Select ▶ to start Dextop Auto.").build()
            relayStatus == STATUS_ERROR -> MessageInfo.Builder("Dextop startup error").setText(
                errorDetail.ifBlank { "Dextop could not be started." }.let { if (it.length > 240) it.take(239) + "…" else it } +
                    "\nSelect ⟳ to recover and retry.").build()
            relayStatus != STATUS_RUNNING -> MessageInfo.Builder("Starting Dextop Auto…").build()
            else -> null
        }
        return NavigationTemplate.Builder()
            .setActionStrip(strip.build())
            .apply { message?.let(::setNavigationInfo) }
            .setMapActionStrip(ActionStrip.Builder().addAction(Action.PAN).apply {
                // PAN must stay for scroll callbacks; it is not drawn on touch screens.
                if (showDragButton && relayStatus == STATUS_RUNNING) addAction(Action.Builder().setIcon(icon(
                    if (panDrag) android.R.drawable.ic_menu_edit else android.R.drawable.ic_menu_sort_by_size))
                    .setOnClickListener {
                        sendPan(0f, 0f, release = true)
                        panDrag = !panDrag
                        invalidate()
                    }.build())
            }.build())
            .setPanModeListener { enabled -> if (!enabled) endDragMode() }
            .build()
    }

    override fun onVisibleAreaChanged(area: android.graphics.Rect) {
        visibleArea=android.graphics.Rect(area);sendVisibleArea()
    }
    override fun onStableAreaChanged(area: android.graphics.Rect) {
        stableArea=android.graphics.Rect(area);sendVisibleArea()
    }
    /**
     * The template's only controls are the action strips in the right-hand column, but the host
     * reports a rectangle that also trims the top and left around them. Keep the full height and
     * avoid just the side columns, so the car UI is not squeezed into the middle of the screen.
     */
    private fun sendVisibleArea() {
        val c=container ?: return
        if(c.width<=0 || c.height<=0)return
        val area=android.graphics.Rect(0,0,c.width,c.height)
        for(rect in listOfNotNull(visibleArea,stableArea)) {
            if(rect.isEmpty)continue
            if(rect.right<area.right)area.right=rect.right
            if(rect.left>c.width*.15f && rect.left>area.left)area.left=rect.left
        }
        android.util.Log.i("DextopDriving","surface=${c.width}x${c.height} visible=$visibleArea stable=$stableArea used=$area")
        if(area.width()<c.width/2)area.set(0,0,c.width,c.height)
        runCatching { relay?.send(Message.obtain(null,10).apply {
            data=Bundle().apply {
                putFloat("left",area.left.toFloat()/c.width);putFloat("top",area.top.toFloat()/c.height)
                putFloat("right",area.right.toFloat()/c.width);putFloat("bottom",area.bottom.toFloat()/c.height)
            }
        }) }
    }

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        android.util.Log.i("DextopCarSurface", "available ${surfaceContainer.width}x${surfaceContainer.height} valid=${surfaceContainer.surface?.isValid}")
        container = surfaceContainer
        if (desktopRequested) startRelay(surfaceContainer)
        sendVisibleArea()
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        android.util.Log.i("DextopCarSurface", "destroyed ${surfaceContainer.width}x${surfaceContainer.height}")
        endDragMode()
        if (container === surfaceContainer || container?.surface?.isValid != true) {
            container = null
            visibleArea = null
            stableArea = null
            startedSurface = null
            startedWidth = 0
            startedHeight = 0
            startedDpi = 0
            if (desktopRequested) relayHandler.postDelayed({
                if (desktopRequested && container == null && splitPlacement.isBlank()) {
                    showError("The Android Auto destination surface was disconnected. Reopen Dextop・Driving or select Recover and retry.")
                }
            }, SURFACE_LOSS_GRACE_MS)
        }
    }

    private var panAnchorX = 0.5f
    private var panAnchorY = 0.5f
    private fun requestSurface() {
        startedSurface = null
        container = null
        relayStatus = STATUS_STARTING
        errorDetail = "Waiting for Android Auto to provide a new display surface…"
        scheduleStartupTimeout()
        val manager = carContext.getCarService(AppManager::class.java)
        manager.setSurfaceCallback(null)
        manager.setSurfaceCallback(this)
        invalidate()
        android.util.Log.i("DextopCarSurface", "requested replacement surface from Android Auto")
    }

    private fun endDragMode() {
        sendPan(0f, 0f, release = true)
        if (panDrag) {
            panDrag = false
            invalidate()
        }
    }

    override fun onClick(x: Float, y: Float) {
        if (!x.isFinite() || !y.isFinite()) return
        val wasDragging = panDrag && panGestureActive
        sendPan(0f, 0f, release = true)
        container?.let {
            panAnchorX = (x / it.width.coerceAtLeast(1)).coerceIn(0f, 1f)
            panAnchorY = (y / it.height.coerceAtLeast(1)).coerceIn(0f, 1f)
        }
        // In drag mode a tap chooses the pickup point. The next tap drops;
        // sending an additional click here used to activate whatever was below.
        android.util.Log.d("DextopCarInput", "tap drag=$panDrag dropping=$wasDragging connected=${relay != null}")
        if (!panDrag && !wasDragging) sendCookedTouch(x, y)
    }

    override fun onScroll(distanceX: Float, distanceY: Float) {
        if (!distanceX.isFinite() || !distanceY.isFinite()) return
        sendPan(-distanceX, -distanceY)
    }

    /** Ends a swipe with the host's fling velocity (pixels per second), as normalised Surface units. */
    override fun onFling(velocityX: Float, velocityY: Float) {
        val surface = container ?: return
        if (panDrag || !velocityX.isFinite() || !velocityY.isFinite() || surface.width <= 0 || surface.height <= 0) return
        relayHandler.removeCallbacks(panIdle)
        panGestureActive = false
        runCatching { relay?.send(Message.obtain(null, 11).apply {
            data = Bundle().apply {
                putFloat("vx", velocityX / surface.width); putFloat("vy", velocityY / surface.height)
            }
        }) }
    }

    /** Pinch and double tap; a negative focus means the host has no focal point. */
    override fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) {
        val surface = container ?: return
        if (!scaleFactor.isFinite() || scaleFactor <= 0f || surface.width <= 0 || surface.height <= 0) return
        sendPan(0f, 0f, release = true)
        runCatching { relay?.send(Message.obtain(null, 12).apply {
            data = Bundle().apply {
                putFloat("x", if (focusX < 0f) -1f else focusX / surface.width)
                putFloat("y", if (focusY < 0f) -1f else focusY / surface.height)
                putFloat("factor", scaleFactor)
                // The last tap, so that tap-then-pinch zooms around the tapped point.
                putFloat("anchor_x", panAnchorX); putFloat("anchor_y", panAnchorY)
            }
        }) }
    }

    private fun sendPan(dx: Float, dy: Float, release: Boolean = false) {
        relayHandler.removeCallbacks(panIdle)
        val surface = container
        if (!release && (surface == null || surface.width <= 0 || surface.height <= 0)) return
        runCatching { relay?.send(Message.obtain(null, 9).apply {
            data = Bundle().apply {
                putFloat("dx", dx / (surface?.width ?: 1).coerceAtLeast(1))
                putFloat("dy", dy / (surface?.height ?: 1).coerceAtLeast(1))
                putFloat("anchor_x", panAnchorX); putFloat("anchor_y", panAnchorY)
                putBoolean("drag", panDrag); putBoolean("release", release)
            }
        }) }
        if (release) panGestureActive = false
        else if (dx != 0f || dy != 0f) {
            panGestureActive = true
            // The host sends no end of a swipe. A pause is not a lift: Dextop ends the swipe on a
            // fling, a tap or a long gap, so this only clears a swipe that was abandoned.
            if (panDrag) {
                panAnchorX = (panAnchorX + dx / (surface?.width ?: 1).coerceAtLeast(1)).coerceIn(0f, 1f)
                panAnchorY = (panAnchorY + dy / (surface?.height ?: 1).coerceAtLeast(1)).coerceIn(0f, 1f)
            } else relayHandler.postDelayed(panIdle, 1_500L)
        }
    }

    private fun startRelay(value: SurfaceContainer) {
        val target = relay ?: run {
            errorDetail = "Waiting for the Dextop relay service…"
            if (relayStatus != STATUS_STARTING && relayStatus != STATUS_ERROR) {
                relayStatus = STATUS_STARTING
                scheduleStartupTimeout()
            }
            invalidate()
            return
        }
        val surface = value.surface ?: run {
            showError("Android Auto provided no destination surface.")
            return
        }
        if (!surface.isValid || value.width <= 0 || value.height <= 0) {
            showError("Android Auto provided an invalid destination surface (${value.width} × ${value.height}).")
            return
        }
        if (startedSurface === surface && startedWidth == value.width &&
            startedHeight == value.height && startedDpi == value.dpi) return
        startedSurface = surface
        startedWidth = value.width
        startedHeight = value.height
        startedDpi = value.dpi
        // A replacement Surface for a running session is handed over silently: going back to
        // "Starting" would show its message card, which changes the visible area, which makes
        // Android Auto send yet another Surface.
        if (relayStatus != STATUS_RUNNING && relayStatus != STATUS_STARTING) {
            relayStatus = STATUS_STARTING
            errorDetail = ""
            scheduleStartupTimeout()
        }
        runCatching { target.send(Message.obtain(null, MSG_START).apply {
            replyTo = incoming
            data = Bundle().apply {
                putParcelable(KEY_SURFACE, surface)
                putInt(KEY_WIDTH, value.width)
                putInt(KEY_HEIGHT, value.height)
                putInt(KEY_DENSITY, value.dpi)
                putFloat(KEY_RENDER_SCALE, renderScale)
            }
        }) }.onFailure { showError("The start request could not be sent to Dextop: ${it.message ?: it.javaClass.simpleName}") }
        sendVisibleArea()
    }

    private fun bindRelay() {
        if (relayBound || relay != null) return
        val target = DextopTarget.find(carContext.packageManager)
        if (target == null) {
            if (desktopRequested) showError("An official Dextop installation with a supported signing certificate is required.")
            return
        }
        val intent = DextopTarget.relayIntent(target)
        val available = carContext.packageManager.resolveService(intent, 0) != null
        if (!available) {
            if (desktopRequested) showError("This Dextop build does not include the Car Companion relay service. Update Dextop and try again.")
            return
        }
        relayBound = runCatching {
            carContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }.getOrElse {
            if (desktopRequested) showError("Dextop could not be bound: ${it.message ?: it.javaClass.simpleName}")
            false
        }
        if (!relayBound && desktopRequested) showError("Android refused the connection to the Dextop relay service.")
    }

    private fun scheduleStartupTimeout() {
        relayHandler.removeCallbacks(startupTimeout)
        relayHandler.postDelayed(startupTimeout, STARTUP_TIMEOUT_MS)
    }

    private fun showError(detail: String) {
        if (!desktopRequested) return
        relayHandler.removeCallbacks(startupTimeout)
        relayStatus = STATUS_ERROR
        errorDetail = detail
        invalidate()
    }

    private fun openQuickMenu() {
        carContext.getCarService(ScreenManager::class.java).push(
            DrivingQuickMenu(
                carContext,
                surfaceInfo = {
                    val value = container
                    if (value == null) "Waiting for display"
                    else "${(value.width / renderScale).toInt()} × ${(value.height / renderScale).toInt()}  •  ${(renderScale * 100).toInt()}%"
                },
                onReconnect = {
                    startedSurface = null
                    container?.let(::startRelay)
                },
                onRestart = {
                    if (container?.surface?.isValid == true && relayStatus == STATUS_RUNNING) sendAction("recover")
                    else {
                        desktopRequested = true
                        startedSurface = null
                        container?.takeIf { it.surface?.isValid == true }?.let(::startRelay) ?: requestSurface()
                    }
                },
                onScale = {
                    val scales = floatArrayOf(1f, .9f, .8f, .7f, .6f, .5f)
                    val index = scales.indexOfFirst { kotlin.math.abs(it - renderScale) < .001f }
                    renderScale = scales[(index + 1).mod(scales.size)]
                    startedSurface = null
                    container?.let(::startRelay)
                },
                onPhoneControl = { sendAction("phone_control") },
                onStop = {
                    stopDextop()
                    carContext.getCarService(ScreenManager::class.java).pop()
                },
            )
        )
    }

    private fun stopDextop() {
        relay?.send(Message.obtain(null, MSG_STOP).apply {
            data = Bundle().apply { putBoolean(KEY_GRACEFUL, true) }
        })
        desktopRequested = false
        relayStatus = STATUS_IDLE
        errorDetail = ""
        startedSurface = null
        relayHandler.removeCallbacks(startupTimeout)
        invalidate()
    }

    private fun sendAction(action: String) {
        val target = relay ?: run {
            if (desktopRequested) showError("The Dextop relay is disconnected.")
            return
        }
        runCatching { target.send(Message.obtain(null, MSG_ACTION).apply {
            data = Bundle().apply { putString(KEY_ACTION, action) }
        }) }.onFailure { if (desktopRequested) showError("The request to Dextop failed: ${it.message ?: it.javaClass.simpleName}") }
    }

    private fun sendCookedTouch(x: Float, y: Float) {
        val now = android.os.SystemClock.uptimeMillis()
        listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
            val event = MotionEvent.obtain(now, now, action, x, y, 0)
            event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            relay?.send(Message.obtain(null, MSG_TOUCH).apply {
                data = Bundle().apply { putParcelable(KEY_EVENT, event) }
            })
            event.recycle()
        }
    }

    companion object {
        private const val MSG_START = 1
        private const val MSG_TOUCH = 2
        private const val MSG_STOP = 3
        private const val MSG_STATUS = 4
        private const val MSG_ACTION = 5
        private const val MSG_HEARTBEAT = 7
        private const val KEY_SURFACE = "surface"
        private const val KEY_WIDTH = "width"
        private const val KEY_HEIGHT = "height"
        private const val KEY_DENSITY = "density"
        private const val KEY_RENDER_SCALE = "render_scale"
        private const val KEY_EVENT = "event"
        private const val KEY_STATUS = "status"
        private const val KEY_DETAIL = "detail"
        private const val KEY_ACTION = "action"
        private const val KEY_GRACEFUL = "graceful"
        private const val STATUS_IDLE = 0
        private const val STATUS_STARTING = 1
        private const val STATUS_RUNNING = 2
        private const val STATUS_ERROR = 3
        private const val HEARTBEAT_INTERVAL_MS = 2_000L
        private const val STARTUP_TIMEOUT_MS = 25_000L
        private const val SURFACE_LOSS_GRACE_MS = 2_500L
    }
}

private class DrivingQuickMenu(
    context: androidx.car.app.CarContext,
    private val surfaceInfo: () -> String,
    private val onReconnect: () -> Unit,
    private val onRestart: () -> Unit,
    private val onScale: () -> Unit,
    private val onPhoneControl: () -> Unit,
    private val onStop: () -> Unit,
) : Screen(context) {
    override fun onGetTemplate(): Template {
        fun row(title: String, text: String? = null, action: () -> Unit): Row =
            Row.Builder().setTitle(title).apply {
                text?.let(::addText)
                setOnClickListener {
                    action()
                    invalidate()
                }
            }.build()
        val items = ItemList.Builder()
            .addItem(row("Resolution / scale", surfaceInfo(), onScale))
            .addItem(row("Reconnect", "Reconnect the current display surface", onReconnect))
            .addItem(row("Restart desktop", "Recreate the display if it is black or unresponsive", onRestart))
            .addItem(row("Phone controls", "Open trackpad and keyboard", onPhoneControl))
            .addItem(row("Stop Dextop", "End the Android Auto session", onStop))
            .build()
        return ListTemplate.Builder()
            .setTitle("Dextop・Driving")
            .setHeaderAction(Action.BACK)
            .setSingleList(items)
            .build()
    }
}
