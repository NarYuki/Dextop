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
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
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
    private var desktopRequested = false
    private var relay: Messenger? = null
    private var container: SurfaceContainer? = null
    private var startedSurface: android.view.Surface? = null
    private var startedWidth = 0
    private var startedHeight = 0
    private var startedDpi = 0
    private var renderScale = 1f
    private var relayStatus = STATUS_IDLE
    private var errorDetail = ""
    private var connected = false
    private var relayBound = false
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
            relayStatus = message.data.getInt(KEY_STATUS, STATUS_IDLE)
            errorDetail = message.data.getString(KEY_DETAIL).orEmpty()
            if (relayStatus == STATUS_RUNNING || relayStatus == STATUS_ERROR) {
                relayHandler.removeCallbacks(startupTimeout)
            }
            invalidate()
            if (relayStatus == STATUS_RUNNING) sendAction("phone_control")
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
            relayHandler.removeCallbacks(heartbeat)
            heartbeat.run()
            if (desktopRequested) container?.let(::startRelay)
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            relay = null
            connected = false
            relayBound = false
            if (desktopRequested) showError("The connection to Dextop was lost. The Dextop process may have stopped or crashed.")
        }

        override fun onBindingDied(name: ComponentName?) {
            relay = null
            connected = false
            relayBound = false
            if (desktopRequested) showError("The Dextop relay connection died and must be reconnected.")
        }

        override fun onNullBinding(name: ComponentName?) {
            relay = null
            connected = false
            relayBound = false
            if (desktopRequested) showError("The installed Dextop build does not provide a usable Car Companion relay service.")
        }
    }

    init {
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(this)
        bindRelay()
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
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
        if (desktopRequested && relayStatus == STATUS_ERROR) {
            return MessageTemplate.Builder(errorDetail.ifBlank { "Dextop could not be started." })
                .setTitle("Dextop startup error")
                .addAction(Action.Builder().setTitle("Recover and retry").setOnClickListener {
                    relayStatus = STATUS_STARTING
                    errorDetail = "Stopping the remaining session…"
                    scheduleStartupTimeout()
                    invalidate()
                    sendAction("recover")
                }.build())
                .addAction(Action.Builder().setTitle("Stop").setOnClickListener { stopDextop() }.build())
                .build()
        }
        if (desktopRequested && relayStatus != STATUS_RUNNING) {
            return MessageTemplate.Builder(errorDetail.ifBlank {
                if (connected) "Waiting for the Android Auto display and Dextop service…"
                else "Connecting to Dextop Car Companion…"
            })
                .setTitle("Starting Dextop・Driving")
                .addAction(Action.Builder().setTitle("Stop").setOnClickListener { stopDextop() }.build())
                .build()
        }
        if (!desktopRequested) {
            val start = Action.Builder()
                .setTitle("Start Dextop")
                .setOnClickListener {
                    desktopRequested = true
                    relayStatus = STATUS_STARTING
                    errorDetail = if (relayBound) "Waiting for the Android Auto destination surface…" else "Connecting to the Dextop relay service…"
                    scheduleStartupTimeout()
                    if (!relayBound) bindRelay()
                    invalidate()
                    container?.let(::startRelay)
                }
                .build()
            return MessageTemplate.Builder(
                "Use the phone trackpad for scrolling, swiping, and keyboard input while driving."
            )
                .setTitle("Dextop・Driving")
                .addAction(start)
                .build()
        }
        // NavigationTemplate supplies the Android Auto Surface after Start.
        // A framework action is required by the template but consumes no
        // custom-title allowance on restrictive hosts.
        val menu = Action.Builder()
            .setIcon(CarIcon.Builder(
                IconCompat.createWithResource(carContext, android.R.drawable.ic_menu_manage)
            ).build())
            .setOnClickListener { openQuickMenu() }
            .build()
        return NavigationTemplate.Builder()
            .setActionStrip(ActionStrip.Builder().addAction(menu).build())
            .build()
    }

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        container = surfaceContainer
        if (desktopRequested) startRelay(surfaceContainer)
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        if (container === surfaceContainer) {
            container = null
            startedSurface = null
            startedWidth = 0
            startedHeight = 0
            startedDpi = 0
            if (desktopRequested) relayHandler.postDelayed({
                if (desktopRequested && container == null) {
                    showError("The Android Auto destination surface was disconnected. Reopen Dextop・Driving or select Recover and retry.")
                }
            }, SURFACE_LOSS_GRACE_MS)
        }
    }

    override fun onClick(x: Float, y: Float) = sendCookedTouch(x, y)

    private fun startRelay(value: SurfaceContainer) {
        val target = relay ?: run {
            errorDetail = "Waiting for the Dextop relay service…"
            scheduleStartupTimeout()
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
        relayStatus = STATUS_STARTING
        errorDetail = "Starting the Dextop session…"
        scheduleStartupTimeout()
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
    }

    private fun bindRelay() {
        if (relayBound || relay != null) return
        val installed = runCatching { carContext.packageManager.getPackageInfo(DEXTOP_PACKAGE, 0) }.isSuccess
        if (!installed) {
            if (desktopRequested) showError("Dextop is not installed on this phone.")
            return
        }
        val intent = Intent().setComponent(ComponentName(DEXTOP_PACKAGE, RELAY_SERVICE))
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
        errorDetail = "Reason: $detail\nAction: Select Recover and retry. If it fails again, open Dextop and check its operation log."
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
                onRestart = { sendAction("recover") },
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
            relay?.send(Message.obtain(null, MSG_TOUCH).apply {
                data = Bundle().apply { putParcelable(KEY_EVENT, event) }
            })
            event.recycle()
        }
    }

    companion object {
        private const val DEXTOP_PACKAGE = "moe.n4tsu.dextop"
        private const val RELAY_SERVICE = "moe.n4tsu.dextop.CardexRelayService"
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
        private const val STARTUP_TIMEOUT_MS = 12_000L
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
