package moe.n4tsu.dextop

import android.app.Service
import android.app.ActivityOptions
import android.content.Intent
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.Surface
import android.view.Display
import kotlin.math.roundToInt

/** Certificate-verified in-process renderer for the Car Companion parked activity. */
class CardexRelayService : Service() {
    private val handler = Handler(Looper.getMainLooper(), ::handleMessage)
    private val messenger = Messenger(handler)
    private var client: Messenger? = null
    private var controller: AndroidAutoMirrorController? = null
    private var legacySession: AutoDisplaySession? = null
    private var surface: Surface? = null
    private var width = 0
    private var height = 0
    private var renderScale = 1f
    private var gracefulStopRequested = false
    private var directSessionOwned = false
    private var relayGeneration = 0L
    private var clientGeneration = 0L
    private var launchInFlight = false
    private var pendingStart: PendingStart? = null
    private var activeMode = "idle"
    private var clientBinder: IBinder? = null
    private var lastClientHeartbeat = 0L
    private var currentStatus = STATUS_IDLE
    private var currentStatusDetail = ""
    private val clientDeathRecipient = IBinder.DeathRecipient {
        handler.post { scheduleUnexpectedClientLoss("Car Companion process died") }
    }
    private val clientWatchdog = object : Runnable {
        override fun run() {
            if (!relaySessionActive) return
            val silence = SystemClock.elapsedRealtime() - lastClientHeartbeat
            if (silence >= CLIENT_HEARTBEAT_TIMEOUT_MS) {
                handleUnexpectedClientLoss("Android Auto connection heartbeat timed out")
            } else {
                handler.postDelayed(this, CLIENT_HEARTBEAT_CHECK_MS)
            }
        }
    }

    private data class PendingStart(
        val surface: Surface,
        val width: Int,
        val height: Int,
        val density: Int,
        val renderScale: Float,
    )

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onUnbind(intent: Intent?): Boolean {
        if (client == null) return false
        val disconnectedGeneration = clientGeneration
        // Parked Activity -> driving CarAppService handoff briefly unbinds the
        // old client. A new Surface may arrive immediately; only tear down if
        // nobody replaces it within the transition window.
        handler.postDelayed({
            if (clientGeneration == disconnectedGeneration) {
                if (!gracefulStopRequested &&
                    (surface != null || controller != null || legacySession?.ownsSession == true)
                ) {
                    CardexRecoveryReceiver.markInterrupted(this, "relay client disconnected")
                }
                stopRelay()
            }
        }, CLIENT_HANDOFF_GRACE_MS)
        return true
    }

    override fun onDestroy() {
        stopRelay()
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun handleMessage(message: Message): Boolean {
        if (!CardCompanionCallerVerifier.isTrusted(this, message.sendingUid)) {
            OperationLog.w(
                this,
                "CarCompanion",
                "rejected relay caller uid=${message.sendingUid}",
                null,
            )
            runCatching {
                message.replyTo?.send(Message.obtain(null, MSG_STATUS).apply {
                    data = Bundle().apply {
                        putInt(KEY_STATUS, STATUS_ERROR)
                        putString(KEY_DETAIL, "Car Companion authentication failed")
                    }
                })
            }
            return true
        }
        when (message.what) {
            MSG_START -> {
                registerClient(message.replyTo)
                val data = message.data.apply { classLoader = Surface::class.java.classLoader }
                val nextSurface = data.getParcelable(KEY_SURFACE, Surface::class.java) ?: return true
                startRelay(
                    nextSurface,
                    data.getInt(KEY_WIDTH),
                    data.getInt(KEY_HEIGHT),
                    data.getInt(KEY_DENSITY, 160),
                    data.getFloat(KEY_RENDER_SCALE, 1f),
                )
            }
            MSG_HEARTBEAT -> registerClient(message.replyTo)
            MSG_TOUCH -> {
                MirrorService.hideCarCompanionCursor()
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
                ACTION_RECOVER -> recoverAndRestart()
                ACTION_PHONE_CONTROL -> openPhoneControl()
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

    private fun startRelay(
        nextSurface: Surface,
        nextWidth: Int,
        nextHeight: Int,
        density: Int,
        requestedScale: Float,
    ) {
        if (!nextSurface.isValid) {
            sendError(IllegalStateException("The Android Auto destination surface is unavailable."))
            return
        }
        if (nextWidth <= 0 || nextHeight <= 0) {
            sendError(IllegalStateException("Android Auto reported an invalid surface size: ${nextWidth}x$nextHeight."))
            return
        }
        if (launchInFlight) {
            val previous = surface
            surface = nextSurface
            destinationSurface = nextSurface
            width = nextWidth
            height = nextHeight
            renderScale = requestedScale.coerceIn(MIN_RENDER_SCALE, 1f)
            pendingStart = PendingStart(nextSurface, nextWidth, nextHeight, density, requestedScale)
            relayGeneration += 1
            if (previous !== nextSurface) previous?.release()
            sendStatus(
                STATUS_STARTING,
                "Android Auto replaced its Surface while the display was starting; switching to the newest Surface…",
            )
            OperationLog.i(this, "CarCompanion", "queued replacement Surface ${nextWidth}x$nextHeight while launch is in flight")
            return
        }
        val preferences = getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
        migrateCarCompanion21(preferences)
        val requestedCodec = preferences.getBoolean("flutter.android_auto_scrcpy_streaming", false)
        val requestedDirect = preferences.getBoolean("flutter.android_auto_hidden_display", false)
        // The former 2.0 direct mode now migrates to the stable decorated 2.1
        // path. Only an explicit legacy selection (both flags false) uses the
        // OverlayDisplay implementation.
        val requestedDecorated = requestedCodec || requestedDirect
        val normalizedScale = requestedScale.coerceIn(MIN_RENDER_SCALE, 1f)
        val (desktopWidth, desktopHeight) = scaledDesktopSize(nextWidth, nextHeight)
        val reusableCodec = requestedDecorated && controller != null && directSessionOwned &&
            kotlin.math.abs(renderScale - normalizedScale) < 0.001f
        if (reusableCodec) {
            val previousSurface = surface
            surface = nextSurface
            destinationSurface = nextSurface
            width = nextWidth
            height = nextHeight
            MirrorService.launch(
                this, desktopWidth, desktopHeight, density.coerceIn(80, 640),
                secure = false, decorations = true, autoOnly = true,
                autoSurface = nextSurface, forceFreeform = true,
            ) { result ->
                result.onSuccess { session ->
                    controller?.bindInputSource(
                        (session["displayId"] as Number).toInt(),
                        (session["width"] as Number).toInt(),
                        (session["height"] as Number).toInt(),
                        (session["density"] as Number).toInt(),
                    )
                    sendStatus(STATUS_RUNNING)
                }.onFailure(::sendError)
            }
            if (previousSurface !== nextSurface) previousSurface?.release()
            return
        }
        val reusable = !requestedDecorated && controller != null &&
                legacySession?.isActive == true &&
                kotlin.math.abs(renderScale - normalizedScale) < 0.001f
        if (reusable) {
            val previousSurface = surface
            surface = nextSurface
            destinationSurface = nextSurface
            width = nextWidth
            height = nextHeight
            legacySession?.resizeLogical(desktopWidth, desktopHeight, density.coerceIn(80, 640))
            controller?.reattachDestination(nextSurface, nextWidth, nextHeight)
            sendStatus(STATUS_RUNNING)
            if (previousSurface !== nextSurface) previousSurface?.release()
            OperationLog.i(this, "CarCompanion", "viewport resized ${nextWidth}x$nextHeight; desktop retained")
            return
        }
        controller?.stop()
        if (surface !== nextSurface) surface?.release()
        surface = nextSurface
        destinationSurface = nextSurface
        width = nextWidth
        height = nextHeight
        renderScale = requestedScale.coerceIn(MIN_RENDER_SCALE, 1f)
        val generation = ++relayGeneration
        relaySessionActive = true
        lastClientHeartbeat = SystemClock.elapsedRealtime()
        handler.removeCallbacks(clientWatchdog)
        handler.postDelayed(clientWatchdog, CLIENT_HEARTBEAT_CHECK_MS)
        sendStatus(STATUS_STARTING)
        gracefulStopRequested = false
        requestPrivilegedBinder()
        waitForPrivilegedAccess(nextSurface, nextWidth, nextHeight, density.coerceIn(80, 640), 0, generation)
    }

    private fun waitForPrivilegedAccess(
        nextSurface: Surface,
        nextWidth: Int,
        nextHeight: Int,
        density: Int,
        attempt: Int,
        generation: Long
    ) {
        if (generation != relayGeneration || surface !== nextSurface || !nextSurface.isValid) return
        if (!PrivilegedAccess("CardexRelayService").isAvailable()) {
            if (attempt < PRIVILEGED_ACCESS_MAX_ATTEMPTS) {
                if (attempt % 10 == 0) requestPrivilegedBinder()
                handler.postDelayed({
                    waitForPrivilegedAccess(nextSurface, nextWidth, nextHeight, density, attempt + 1, generation)
                }, PRIVILEGED_ACCESS_RETRY_MS)
            } else {
                sendError(IllegalStateException(NativeStrings.text("nativeShizukuUnavailable")))
            }
            return
        }
        val (desktopWidth, desktopHeight) = scaledDesktopSize(nextWidth, nextHeight)
        OperationLog.i(
            this,
            "CarCompanion",
            "relay display physical=${nextWidth}x$nextHeight scale=$renderScale desktop=${desktopWidth}x$desktopHeight/$density"
        )
        val preferences = getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
        migrateCarCompanion21(preferences)
        val codecStreaming = preferences.getBoolean("flutter.android_auto_scrcpy_streaming", false)
        val directDisplay = preferences.getBoolean("flutter.android_auto_hidden_display", false)
        if (codecStreaming || directDisplay) {
            activeMode = "decorated-direct"
            startCodecDisplay(nextSurface, desktopWidth, desktopHeight, density, generation)
        } else {
            activeMode = "overlay"
            startLegacyOverlay(
                nextSurface, nextWidth, nextHeight,
                desktopWidth, desktopHeight, density, generation,
            )
        }
    }

    /** Makes decorated direct display the stable default once per installation. */
    private fun migrateCarCompanion21(preferences: android.content.SharedPreferences) {
        if (preferences.getBoolean(PREF_CAR_COMPANION_21_MIGRATED, false)) return
        check(
            preferences.edit()
                .putBoolean("flutter.android_auto_hidden_display", true)
                .putBoolean("flutter.android_auto_scrcpy_streaming", true)
                .putBoolean(PREF_CAR_COMPANION_21_MIGRATED, true)
                .commit()
        ) { "Unable to migrate Car Companion to 2.1" }
        OperationLog.i(this, "CarCompanion", "migrated saved display method to stable 2.1")
    }

    private fun startDirectDisplay(
        nextSurface: Surface,
        desktopWidth: Int,
        desktopHeight: Int,
        density: Int,
        generation: Long,
    ) {
        if (launchInFlight) return
        launchInFlight = true
        // Ownership begins with the launch request. If Android Auto disappears
        // while creation is in flight, cleanup must still stop the session.
        directSessionOwned = true
        MirrorService.launch(
            this, desktopWidth, desktopHeight, density.coerceIn(80, 640),
            secure = false, decorations = false, autoOnly = true, autoSurface = nextSurface,
        ) { result ->
            launchInFlight = false
            if (generation != relayGeneration) {
                drainPendingStart()
                return@launch
            }
            result.onSuccess { session ->
                runCatching {
                    AndroidAutoMirrorController(this, AndroidAutoMirrorActivity.SOURCE_DEXTOP).also {
                        controller = it
                        it.bindInputSource(
                            (session["displayId"] as Number).toInt(),
                            (session["width"] as Number).toInt(),
                            (session["height"] as Number).toInt(),
                            (session["density"] as Number).toInt(),
                        )
                    }
                }.onSuccess {
                    sendStatus(STATUS_RUNNING)
                    scheduleDirectFrameProbe(generation, session["displayId"] as Number)
                }
                    .onFailure(::sendError)
            }.onFailure(::sendError)
            drainPendingStart()
        }
    }

    private fun startCodecDisplay(
        nextSurface: Surface,
        desktopWidth: Int,
        desktopHeight: Int,
        density: Int,
        generation: Long,
    ) {
        if (launchInFlight) return
        launchInFlight = true
        directSessionOwned = true
        MirrorService.launch(
            this, desktopWidth, desktopHeight, density.coerceIn(80, 640),
            secure = false, decorations = true, autoOnly = true,
            autoSurface = nextSurface, forceFreeform = true,
        ) { result ->
            launchInFlight = false
            if (generation != relayGeneration) {
                drainPendingStart()
                return@launch
            }
            result.onSuccess { session ->
                runCatching {
                    AndroidAutoMirrorController(this, AndroidAutoMirrorActivity.SOURCE_DEXTOP).also {
                        controller = it
                        it.bindInputSource(
                            (session["displayId"] as Number).toInt(),
                            (session["width"] as Number).toInt(),
                            (session["height"] as Number).toInt(),
                            (session["density"] as Number).toInt(),
                        )
                    }
                }.onSuccess {
                    OperationLog.i(
                        this,
                        "CarCompanion",
                        "Electron-style decorated direct display attached",
                    )
                    sendStatus(STATUS_RUNNING)
                    scheduleDirectFrameProbe(generation, session["displayId"] as Number)
                }.onFailure(::sendError)
            }.onFailure { error ->
                OperationLog.w(
                    this,
                    "CarCompanion",
                    "decorated direct display failed; falling back to overlay mirror",
                    error,
                )
                directSessionOwned = false
                handler.postDelayed({
                    if (generation == relayGeneration && surface === nextSurface && nextSurface.isValid) {
                        startLegacyOverlay(
                            nextSurface, width, height,
                            desktopWidth, desktopHeight, density, generation,
                        )
                    }
                }, DIRECT_FALLBACK_DELAY_MS)
            }
            drainPendingStart()
        }
    }

    private fun drainPendingStart() {
        if (launchInFlight) return
        val pending = pendingStart ?: return
        pendingStart = null
        if (!pending.surface.isValid || surface !== pending.surface) return
        startRelay(pending.surface, pending.width, pending.height, pending.density, pending.renderScale)
    }

    /**
     * PixelCopy returning SOURCE_NO_DATA is the only reliable signal exposed
     * for a Surface that has been accepted but never received a producer
     * frame. Do not judge pixel colour: a valid desktop may intentionally be
     * black. A successful copy proves that the direct display is producing.
     */
    private fun scheduleDirectFrameProbe(generation: Long, displayNumber: Number, attempt: Int = 0) {
        val expectedSurface = surface ?: return
        val displayId = displayNumber.toInt()
        handler.postDelayed({
            if (generation != relayGeneration || surface !== expectedSurface || !expectedSurface.isValid) return@postDelayed
            val displayExists = getSystemService(DisplayManager::class.java).getDisplay(displayId) != null
            if (!displayExists || !MirrorService.isActive()) {
                fallbackDirectToLegacy(
                    "Direct display health check failed: display=$displayId exists=$displayExists active=${MirrorService.isActive()}",
                    generation,
                )
                return@postDelayed
            }
            val sample = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
            runCatching {
                PixelCopy.request(expectedSurface, sample, { result ->
                    sample.recycle()
                    if (generation != relayGeneration || surface !== expectedSurface) return@request
                    if (result == PixelCopy.SUCCESS) {
                        OperationLog.i(this, "CarCompanion", "direct display frame verified display=$displayId attempt=$attempt")
                    } else if (attempt + 1 < DIRECT_FRAME_PROBE_ATTEMPTS) {
                        OperationLog.w(this, "CarCompanion", "direct display has no readable frame result=$result attempt=$attempt", null)
                        scheduleDirectFrameProbe(generation, displayId, attempt + 1)
                    } else {
                        fallbackDirectToLegacy(
                            "Direct display produced no frame after $DIRECT_FRAME_PROBE_ATTEMPTS checks (PixelCopy=$result)",
                            generation,
                        )
                    }
                }, handler)
            }.onFailure {
                sample.recycle()
                if (attempt + 1 < DIRECT_FRAME_PROBE_ATTEMPTS) {
                    scheduleDirectFrameProbe(generation, displayId, attempt + 1)
                } else {
                    fallbackDirectToLegacy("Direct display frame probe failed: ${it.message}", generation)
                }
            }
        }, if (attempt == 0) DIRECT_FRAME_PROBE_INITIAL_DELAY_MS else DIRECT_FRAME_PROBE_INTERVAL_MS)
    }

    private fun fallbackDirectToLegacy(reason: String, generation: Long) {
        if (generation != relayGeneration || activeMode == "overlay") return
        val activeSurface = surface ?: return
        if (!activeSurface.isValid) return
        OperationLog.w(this, "CarCompanion", "$reason; falling back to the stable overlay display", null)
        sendStatus(STATUS_STARTING, "$reason\nFalling back to the stable display method…")
        controller?.stop()
        controller = null
        MirrorService.stopActive()
        waitForDirectFallbackStop(activeSurface, generation, 0)
    }

    private fun waitForDirectFallbackStop(activeSurface: Surface, generation: Long, attempt: Int) {
        if (generation != relayGeneration || surface !== activeSurface || !activeSurface.isValid) return
        if (!MirrorService.isActive() && !MirrorService.isStopping()) {
            directSessionOwned = false
            activeMode = "overlay"
            val (desktopWidth, desktopHeight) = scaledDesktopSize(width, height)
            startLegacyOverlay(
                activeSurface, width, height, desktopWidth, desktopHeight,
                resources.displayMetrics.densityDpi.coerceIn(80, 640), generation,
            )
        } else if (attempt < DIRECT_FALLBACK_STOP_ATTEMPTS) {
            handler.postDelayed({
                waitForDirectFallbackStop(activeSurface, generation, attempt + 1)
            }, DIRECT_FALLBACK_STOP_RETRY_MS)
        } else {
            sendError(IllegalStateException("The failed direct display could not be stopped. ${diagnosticSnapshot()}"))
        }
    }

    private fun requestPrivilegedBinder() {
        sendBroadcast(
            Intent("roro.stellar.intent.action.REQUEST_BINDER")
                .setPackage("roro.stellar.manager")
        )
    }

    private fun startLegacyOverlay(
        nextSurface: Surface,
        nextWidth: Int,
        nextHeight: Int,
        desktopWidth: Int,
        desktopHeight: Int,
        density: Int,
        generation: Long,
    ) {
        directSessionOwned = false
        // A render-scale change is a new logical display, not merely a
        // Surface resize.  Overlay removal is asynchronous; creating the
        // next request before it completes lets DisplayManager hand us the
        // old display and makes the selected scale appear to do nothing.
        controller?.stop()
        controller = null
        val startFresh = {
            legacySession = AutoDisplaySession(this).also { session ->
            session.start(desktopWidth, desktopHeight, density, secure = false, decorations = false) { result ->
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
        val previousSession = legacySession
        if (previousSession?.ownsSession == true) {
            previousSession.stop {
                if (generation == relayGeneration && surface === nextSurface && nextSurface.isValid) startFresh()
            }
        } else {
            startFresh()
        }
    }

    private fun stopRelay() {
        relayGeneration += 1
        pendingStart = null
        launchInFlight = false
        activeMode = "idle"
        handler.removeCallbacks(clientWatchdog)
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
        relaySessionActive = false
        surface?.release()
        surface = null
        destinationSurface = null
        sendStatus(STATUS_IDLE)
        client = null
        unlinkClientDeathRecipient()
        // The normal display cleanup restores SystemUI. Repeat the navigation
        // restore after its bounded teardown window because some vendor
        // SystemUI builds reapply the old disable mask during disconnection.
        handler.postDelayed({
            if (!relaySessionActive) MirrorService.restorePhoneNavigation(this)
        }, POST_DISCONNECT_RESTORE_DELAY_MS)
    }

    private fun registerClient(next: Messenger?) {
        lastClientHeartbeat = SystemClock.elapsedRealtime()
        if (next == null) return
        client = next
        runCatching {
            next.send(Message.obtain(null, MSG_STATUS).apply {
                data = Bundle().apply {
                    putInt(KEY_STATUS, currentStatus)
                    putString(KEY_DETAIL, currentStatusDetail)
                }
            })
        }
        val nextBinder = next.binder
        if (clientBinder === nextBinder) return
        unlinkClientDeathRecipient()
        clientGeneration += 1
        clientBinder = nextBinder
        runCatching { nextBinder.linkToDeath(clientDeathRecipient, 0) }
            .onFailure { scheduleUnexpectedClientLoss("Car Companion binder was already dead") }
    }

    private fun unlinkClientDeathRecipient() {
        clientBinder?.let { binder -> runCatching { binder.unlinkToDeath(clientDeathRecipient, 0) } }
        clientBinder = null
    }

    private fun handleUnexpectedClientLoss(reason: String) {
        if (!relaySessionActive && surface == null && controller == null &&
            legacySession?.ownsSession != true && !MirrorService.isAutoOnlySessionActive()) return
        OperationLog.w(this, "CarCompanion", "$reason; stopping orphaned Android Auto session", null)
        CardexRecoveryReceiver.markInterrupted(this, reason)
        stopRelay()
    }

    private fun scheduleUnexpectedClientLoss(reason: String) {
        val disconnectedGeneration = clientGeneration
        handler.postDelayed({
            if (clientGeneration == disconnectedGeneration) handleUnexpectedClientLoss(reason)
        }, CLIENT_HANDOFF_GRACE_MS)
    }

    private fun reconnectSurface() {
        val activeSurface = surface ?: return
        if (!activeSurface.isValid || !MirrorService.isActive()) return
        // The direct display already owns this Surface. Reconnect now
        // re-submits that same surface through the normal start path rather
        // than creating a second recording display.
        startRelay(activeSurface, width, height, resources.displayMetrics.densityDpi, renderScale)
    }

    private fun recoverAndRestart() {
        val activeSurface = surface
        if (activeSurface == null || !activeSurface.isValid || width <= 0 || height <= 0) {
            sendError(IllegalStateException("The Android Auto display surface is no longer available. Close and reopen Car Companion."))
            return
        }
        val restartWidth = width
        val restartHeight = height
        val restartDensity = resources.displayMetrics.densityDpi
        val restartScale = renderScale
        sendStatus(STATUS_STARTING, "Stopping the remaining Dextop session…")
        MirrorService.stopActive()
        waitForRecoveryStop(activeSurface, restartWidth, restartHeight, restartDensity, restartScale, 0)
    }

    private fun waitForRecoveryStop(
        restartSurface: Surface,
        restartWidth: Int,
        restartHeight: Int,
        restartDensity: Int,
        restartScale: Float,
        attempt: Int,
    ) {
        if (!restartSurface.isValid || surface !== restartSurface) return
        if (!MirrorService.isActive() && !MirrorService.isStopping()) {
            getSharedPreferences(CardexRecoveryReceiver.PREFERENCES, MODE_PRIVATE).edit()
                .remove(CardexRecoveryReceiver.KEY_REPAIR_REQUIRED)
                .remove("cleanup_pending")
                .remove("cardex_interruption_reason")
                .apply()
            startRelay(restartSurface, restartWidth, restartHeight, restartDensity, restartScale)
        } else if (attempt < 50) {
            handler.postDelayed({
                waitForRecoveryStop(restartSurface, restartWidth, restartHeight, restartDensity, restartScale, attempt + 1)
            }, 100L)
        } else {
            sendError(IllegalStateException("The previous Dextop session could not be stopped. Open Dextop and tap Stop, then try again."))
        }
    }

    private fun openPhoneControl() {
        val activeController = controller ?: return
        val displayId = activeController.sourceDisplayId.takeIf { it >= 0 } ?: return
        val geometry = activeController.sourceGeometry()
        // The phone controller is attached to Dextop's existing uinput
        // touchpad. Its system pointer is rendered by the target display, so
        // the old Car Companion software-cursor overlay must not be added.
        MirrorService.hideCarCompanionCursor()
        val intent = Intent(this, CarCompanionPhoneActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(CarCompanionPhoneActivity.EXTRA_DISPLAY_ID, displayId)
            putExtra(CarCompanionPhoneActivity.EXTRA_DISPLAY_WIDTH, geometry[0])
            putExtra(CarCompanionPhoneActivity.EXTRA_DISPLAY_HEIGHT, geometry[1])
            putExtra(CarCompanionPhoneActivity.EXTRA_DISPLAY_DENSITY, geometry[2])
        }
        startActivity(
            intent,
            ActivityOptions.makeBasic().setLaunchDisplayId(Display.DEFAULT_DISPLAY).toBundle(),
        )
    }

    private fun sendError(error: Throwable) {
        relaySessionActive = false
        OperationLog.e(this, "CarCompanion", "relay failed", error)
        Log.e("DextopCarCompanion", "relay failed: ${error.message}", error)
        val reason = error.message?.takeIf { it.isNotBlank() } ?: "Unknown startup error"
        val diagnostics = diagnosticSnapshot()
        val action = when {
            MirrorService.isStopping() -> "Wait a few seconds, then select Recover and retry."
            MirrorService.isActive() -> "A Dextop session is still running. Select Recover and retry to stop it and start again."
            else -> "Select Recover and retry. If it fails again, open Dextop and check its operation log."
        }
        sendStatus(STATUS_ERROR, "Reason: $reason\nDiagnostics: $diagnostics\nAction: $action")
    }

    private fun diagnosticSnapshot(): String =
        "mode=$activeMode surface=${surface?.isValid == true} size=${width}x$height " +
            "generation=$relayGeneration launch=$launchInFlight active=${MirrorService.isActive()} " +
            "stopping=${MirrorService.isStopping()} privileged=${PrivilegedAccess("CardexRelayService").isAvailable()}"

    private fun sendStatus(status: Int, detail: String = "") {
        currentStatus = status
        currentStatusDetail = detail
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
        private var instance: CardexRelayService? = null
        @Volatile
        private var destinationSurface: Surface? = null
        @Volatile
        private var relaySessionActive = false

        /** Keep the CARDEX Surface valid across accessibility reconnects. */
        internal fun activeDestinationSurface(): Surface? =
            destinationSurface?.takeIf { it.isValid }

        /** True while Car Companion owns a relay display or is preparing one. */
        fun isRelaySessionActive(): Boolean = relaySessionActive

        /** The third Car Companion method: decorated, directly-owned VirtualDisplay. */
        fun isDecoratedDirectSessionActive(): Boolean =
            relaySessionActive && instance?.activeMode == "decorated-direct" &&
                MirrorService.isAutoOnlySessionActive()

        /** Stops both parked and driving relay ownership from the Dextop UI. */
        fun stopFromPhone() {
            val service = instance
            if (service != null) {
                service.handler.post {
                    if (instance === service) {
                        service.gracefulStopRequested = true
                        service.stopRelay()
                    }
                }
            } else if (MirrorService.isAutoOnlySessionActive()) {
                MirrorService.stopActive()
            }
        }

        /** Routes phone gestures to the controller owning the active Car Companion display. */
        fun dispatchPhoneTrackpadEvent(source: MotionEvent, viewWidth: Int, viewHeight: Int) {
            val service = instance ?: return
            val event = MotionEvent.obtain(source)
            service.handler.post {
                try {
                    if (instance === service) service.controller?.dispatchTrackpad(event, viewWidth, viewHeight)
                } finally {
                    event.recycle()
                }
            }
        }

        fun resetPhoneTrackpad() {
            val service = instance ?: return
            service.handler.post {
                if (instance === service) service.controller?.resetTrackpad()
                MirrorService.hideCarCompanionCursor()
            }
        }

        const val MSG_START = 1
        const val MSG_TOUCH = 2
        const val MSG_STOP = 3
        const val MSG_STATUS = 4
        const val MSG_ACTION = 5
        const val MSG_WORKSPACES = 6
        const val MSG_HEARTBEAT = 7
        const val KEY_SURFACE = "surface"
        const val KEY_WIDTH = "width"
        const val KEY_HEIGHT = "height"
        const val KEY_DENSITY = "density"
        const val KEY_RENDER_SCALE = "render_scale"
        const val KEY_EVENT = "event"
        const val KEY_STATUS = "status"
        const val KEY_DETAIL = "detail"
        const val KEY_ACTION = "action"
        const val KEY_WORKSPACES = "workspaces"
        const val KEY_GRACEFUL = "graceful"
        const val ACTION_RECONNECT = "reconnect"
        const val ACTION_RECOVER = "recover"
        const val ACTION_PHONE_CONTROL = "phone_control"
        const val ACTION_WORKSPACE_LIST = "workspace_list"
        const val ACTION_WORKSPACE_SAVE = "workspace_save"
        const val ACTION_WORKSPACE_OPEN = "workspace_open:"
        const val STATUS_IDLE = 0
        const val STATUS_STARTING = 1
        const val STATUS_RUNNING = 2
        const val STATUS_ERROR = 3
        private const val MIN_RENDER_SCALE = 0.50f
        private const val MAX_DESKTOP_EDGE = 4096
        private const val CLIENT_HANDOFF_GRACE_MS = 5_000L
        private const val CLIENT_HEARTBEAT_CHECK_MS = 2_000L
        private const val CLIENT_HEARTBEAT_TIMEOUT_MS = 10_000L
        private const val POST_DISCONNECT_RESTORE_DELAY_MS = 2_500L
        private const val DIRECT_FALLBACK_DELAY_MS = 350L
        private const val DIRECT_FRAME_PROBE_INITIAL_DELAY_MS = 1_500L
        private const val DIRECT_FRAME_PROBE_INTERVAL_MS = 1_000L
        private const val DIRECT_FRAME_PROBE_ATTEMPTS = 5
        private const val DIRECT_FALLBACK_STOP_RETRY_MS = 100L
        private const val DIRECT_FALLBACK_STOP_ATTEMPTS = 50
        private const val PREF_CAR_COMPANION_21_MIGRATED =
            "flutter.android_auto_21_default_migrated"
        private const val PRIVILEGED_ACCESS_RETRY_MS = 150L
        private const val PRIVILEGED_ACCESS_MAX_ATTEMPTS = 100
    }

    private fun scaledDesktopSize(physicalWidth: Int, physicalHeight: Int): Pair<Int, Int> {
        val inverse = 1f / renderScale
        val rawWidth = (physicalWidth * inverse).roundToInt().coerceAtLeast(1)
        val rawHeight = (physicalHeight * inverse).roundToInt().coerceAtLeast(1)
        val cap = MAX_DESKTOP_EDGE.toFloat() / maxOf(rawWidth, rawHeight).toFloat()
        return if (cap >= 1f) rawWidth to rawHeight else
            (rawWidth * cap).roundToInt().coerceAtLeast(1) to
                (rawHeight * cap).roundToInt().coerceAtLeast(1)
    }
}
