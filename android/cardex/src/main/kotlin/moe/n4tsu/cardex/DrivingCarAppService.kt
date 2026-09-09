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
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.validation.HostValidator

/** Driving-safe projection host. Full input remains on the phone controller. */
class DrivingCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    override fun onCreateSession(): Session = DrivingSession()
}

private class DrivingSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen = DrivingScreen(carContext)
}

private class DrivingScreen(context: androidx.car.app.CarContext) : Screen(context), SurfaceCallback {
    private var relay: Messenger? = null
    private var container: SurfaceContainer? = null
    private var connected = false
    private val incoming = Messenger(Handler(Looper.getMainLooper()) { message ->
        if (message.what == MSG_STATUS && message.data.getInt(KEY_STATUS) == STATUS_RUNNING) {
            sendAction("phone_control")
        }
        true
    })
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            relay = binder?.let(::Messenger)
            connected = true
            container?.let(::startRelay)
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            relay = null
            connected = false
        }
    }

    init {
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(this)
        carContext.bindService(
            Intent().setComponent(ComponentName(DEXTOP_PACKAGE, RELAY_SERVICE)),
            connection,
            Context.BIND_AUTO_CREATE,
        )
    }

    override fun onGetTemplate(): NavigationTemplate {
        val phoneControl = Action.Builder().setTitle("Phone control").setOnClickListener {
            sendAction("phone_control")
        }.build()
        return NavigationTemplate.Builder()
            .setActionStrip(ActionStrip.Builder().addAction(phoneControl).build())
            .setMapActionStrip(ActionStrip.Builder().addAction(phoneControl).build())
            .build()
    }

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        container = surfaceContainer
        startRelay(surfaceContainer)
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        if (container === surfaceContainer) container = null
    }

    override fun onClick(x: Float, y: Float) = sendCookedTouch(x, y)

    private fun startRelay(value: SurfaceContainer) {
        val target = relay ?: return
        val surface = value.surface ?: return
        if (!surface.isValid || value.width <= 0 || value.height <= 0) return
        target.send(Message.obtain(null, MSG_START).apply {
            replyTo = incoming
            data = Bundle().apply {
                putParcelable(KEY_SURFACE, surface)
                putInt(KEY_WIDTH, value.width)
                putInt(KEY_HEIGHT, value.height)
                putInt(KEY_DENSITY, value.dpi)
                putFloat(KEY_RENDER_SCALE, 1f)
            }
        })
    }

    private fun sendAction(action: String) {
        relay?.send(Message.obtain(null, MSG_ACTION).apply {
            data = Bundle().apply { putString(KEY_ACTION, action) }
        })
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
        private const val MSG_STATUS = 4
        private const val MSG_ACTION = 5
        private const val KEY_SURFACE = "surface"
        private const val KEY_WIDTH = "width"
        private const val KEY_HEIGHT = "height"
        private const val KEY_DENSITY = "density"
        private const val KEY_RENDER_SCALE = "render_scale"
        private const val KEY_EVENT = "event"
        private const val KEY_STATUS = "status"
        private const val KEY_ACTION = "action"
        private const val STATUS_RUNNING = 2
    }
}
