package moe.n4tsu.dextop

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import android.view.MotionEvent
import android.view.Surface

/** Signature-protected in-process renderer for the CARDEX parked activity. */
class CardexRelayService : Service() {
    private val handler = Handler(Looper.getMainLooper(), ::handleMessage)
    private val messenger = Messenger(handler)
    private var client: Messenger? = null
    private var controller: AndroidAutoMirrorController? = null
    private var legacySession: AutoDisplaySession? = null
    private var surface: Surface? = null
    private var width = 0
    private var height = 0
    private var gracefulStopRequested = false
    private var directSessionOwned = false

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onUnbind(intent: Intent?): Boolean {
        if (!gracefulStopRequested && (surface != null || controller != null || legacySession?.ownsSession == true)) {
            CardexRecoveryReceiver.markInterrupted(this, "relay client unbound")
        }
        stopRelay()
        return false
    }

    override fun onDestroy() {
        stopRelay()
        super.onDestroy()
    }

    private fun handleMessage(message: Message): Boolean {
        when (message.what) {
            MSG_START -> {
                client = message.replyTo
                val data = message.data.apply { classLoader = Surface::class.java.classLoader }
                val nextSurface = data.getParcelable(KEY_SURFACE, Surface::class.java) ?: return true
                startRelay(nextSurface, data.getInt(KEY_WIDTH), data.getInt(KEY_HEIGHT), data.getInt(KEY_DENSITY, 160))
            }
            MSG_TOUCH -> {
                message.data.classLoader = MotionEvent::class.java.classLoader
                message.data.getParcelable(KEY_EVENT, MotionEvent::class.java)?.let { event ->
                    controller?.dispatchTouch(event, width, height)
                    event.recycle()
                }
            }
            MSG_STOP -> {
                gracefulStopRequested = message.data.getBoolean(KEY_GRACEFUL, true)
                if (!gracefulStopRequested) {
                    CardexRecoveryReceiver.markInterrupted(this, "Dextop Car Companion activity interrupted")
                }
                stopRelay()
            }
            MSG_ACTION -> when (message.data.getString(KEY_ACTION)) {
                ACTION_RECONNECT -> reconnectSurface()
                ACTION_WORKSPACE_LIST -> sendWorkspaces()
                ACTION_WORKSPACE_SAVE -> {
                    val error = MirrorService.saveCardexWorkspace()
                    sendWorkspaces(error.orEmpty())
                }
                else -> message.data.getString(KEY_ACTION)?.takeIf { it.startsWith(ACTION_WORKSPACE_OPEN) }
                    ?.removePrefix(ACTION_WORKSPACE_OPEN)?.let {
                        MirrorService.launchCardexWorkspace(it)
                    }
            }
        }
        return true
    }

    private fun startRelay(nextSurface: Surface, nextWidth: Int, nextHeight: Int, density: Int) {
        if (!nextSurface.isValid || nextWidth <= 0 || nextHeight <= 0) return
        controller?.stop()
        if (surface !== nextSurface) surface?.release()
        surface = nextSurface
        destinationSurface = nextSurface
        width = nextWidth
        height = nextHeight
        sendStatus(STATUS_STARTING)
        gracefulStopRequested = false
        requestPrivilegedBinder()
        waitForPrivilegedAccess(nextSurface, nextWidth, nextHeight, density.coerceIn(80, 640), 0)
    }

    private fun waitForPrivilegedAccess(
        nextSurface: Surface,
        nextWidth: Int,
        nextHeight: Int,
        density: Int,
        attempt: Int
    ) {
        if (surface !== nextSurface || !nextSurface.isValid) return
        if (!PrivilegedAccess("CardexRelayService").isAvailable()) {
            if (attempt < 20) {
                handler.postDelayed({
                    waitForPrivilegedAccess(nextSurface, nextWidth, nextHeight, density, attempt + 1)
                }, 150L)
            } else {
                sendError(IllegalStateException(NativeStrings.text("nativeShizukuUnavailable")))
            }
            return
        }
        val hiddenDisplay = getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
            .getBoolean("flutter.android_auto_hidden_display", false)
        if (!hiddenDisplay) {
            startLegacyOverlay(nextSurface, nextWidth, nextHeight, density)
            return
        }
        MirrorService.launch(
            this, nextWidth, nextHeight, density.coerceIn(80, 640),
            secure = false, decorations = false, autoOnly = true, autoSurface = nextSurface
        ) { result ->
            result.onSuccess { session ->
                directSessionOwned = true
                val activeSurface = surface
                if (activeSurface == null || !activeSurface.isValid) {
                    stopRelay()
                    return@onSuccess
                }
                runCatching {
                    AndroidAutoMirrorController(this, AndroidAutoMirrorActivity.SOURCE_DEXTOP).also {
                        controller = it
                        it.bindInputSource(
                            (session["displayId"] as Number).toInt(),
                            (session["width"] as Number).toInt(),
                            (session["height"] as Number).toInt(),
                            (session["density"] as Number).toInt()
                        )
                    }
                }.onSuccess { sendStatus(STATUS_RUNNING) }
                    .onFailure { sendError(it) }
            }.onFailure(::sendError)
        }
    }

    private fun requestPrivilegedBinder() {
        sendBroadcast(
            Intent("roro.stellar.intent.action.REQUEST_BINDER")
                .setPackage("roro.stellar.manager")
        )
    }

    private fun startLegacyOverlay(nextSurface: Surface, nextWidth: Int, nextHeight: Int, density: Int) {
        directSessionOwned = false
        legacySession?.stop()
        legacySession = AutoDisplaySession(this).also { session ->
            session.start(nextWidth, nextHeight, density, secure = false, decorations = false) { result ->
                result.onSuccess { displayId ->
                    runCatching {
                        AndroidAutoMirrorController(this, AndroidAutoMirrorActivity.SOURCE_AUTO).also {
                            controller = it
                            it.selectSourceDisplay(displayId)
                            it.attach(nextSurface, nextWidth, nextHeight, "Dextop Car Companion legacy overlay")
                        }
                    }.onSuccess { sendStatus(STATUS_RUNNING) }
                        .onFailure(::sendError)
                }.onFailure(::sendError)
            }
        }
    }

    private fun stopRelay() {
        controller?.stop()
        controller = null
        legacySession?.stop()
        legacySession = null
        if (CardCompanionSessionPolicy.shouldStopDirectSession(
                directSessionOwned,
                MirrorService.isAutoOnlySessionActive()
            )) {
            MirrorService.stopActive()
        }
        directSessionOwned = false
        surface?.release()
        surface = null
        destinationSurface = null
        sendStatus(STATUS_IDLE)
    }

    private fun reconnectSurface() {
        val activeSurface = surface ?: return
        if (!activeSurface.isValid || !MirrorService.isActive()) return
        // The direct display already owns this Surface. Reconnect now
        // re-submits that same surface through the normal start path rather
        // than creating a second recording display.
        startRelay(activeSurface, width, height, resources.displayMetrics.densityDpi)
    }

    private fun sendError(error: Throwable) {
        OperationLog.e(this, "CarCompanion", "relay failed", error)
        Log.e("DextopCarCompanion", "relay failed: ${error.message}", error)
        sendStatus(STATUS_ERROR, error.message.orEmpty())
    }

    private fun sendStatus(status: Int, detail: String = "") {
        runCatching {
            client?.send(Message.obtain(null, MSG_STATUS).apply {
                data = Bundle().apply {
                    putInt(KEY_STATUS, status)
                    putString(KEY_DETAIL, detail)
                }
            })
        }
    }

    private fun sendWorkspaces(error: String = "") {
        runCatching {
            client?.send(Message.obtain(null, MSG_WORKSPACES).apply {
                data = Bundle().apply {
                    putString(KEY_WORKSPACES, MirrorService.cardexWorkspaces())
                    putString(KEY_DETAIL, error)
                }
            })
        }
    }

    companion object {
        @Volatile
        private var destinationSurface: Surface? = null

        /** Keep the CARDEX Surface valid across accessibility reconnects. */
        internal fun activeDestinationSurface(): Surface? =
            destinationSurface?.takeIf { it.isValid }

        const val MSG_START = 1
        const val MSG_TOUCH = 2
        const val MSG_STOP = 3
        const val MSG_STATUS = 4
        const val MSG_ACTION = 5
        const val MSG_WORKSPACES = 6
        const val KEY_SURFACE = "surface"
        const val KEY_WIDTH = "width"
        const val KEY_HEIGHT = "height"
        const val KEY_DENSITY = "density"
        const val KEY_EVENT = "event"
        const val KEY_STATUS = "status"
        const val KEY_DETAIL = "detail"
        const val KEY_ACTION = "action"
        const val KEY_WORKSPACES = "workspaces"
        const val KEY_GRACEFUL = "graceful"
        const val ACTION_RECONNECT = "reconnect"
        const val ACTION_WORKSPACE_LIST = "workspace_list"
        const val ACTION_WORKSPACE_SAVE = "workspace_save"
        const val ACTION_WORKSPACE_OPEN = "workspace_open:"
        const val STATUS_IDLE = 0
        const val STATUS_STARTING = 1
        const val STATUS_RUNNING = 2
        const val STATUS_ERROR = 3
    }
}
