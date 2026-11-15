package moe.n4tsu.dextop

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.SessionManagerListener

/** What [CastController] needs from the session beyond the shared interfaces. */
internal interface CastControllerHost : DesktopSession, OverlayMenu

/** Finds Cast receivers, shows the route menu and runs the Cast session for the desktop. */
internal class CastController(
    private val context: Context,
    private val desk: CastControllerHost,
) {
    private var castMediaRouter: MediaRouter? = null

    private var castRouteCallback: MediaRouter.Callback? = null

    private var castSessionListener: SessionManagerListener<CastSession>? = null

    private var castCompatibilityStreamer: CastCompatibilityStreamer? = null

    fun openCastPicker() {
        desk.menuPrimary?.let(::showCastRouteMenu)
    }

    private fun showCastRouteMenu(panel: LinearLayout) {
        stopCastRouteDiscovery()
        desk.animateMenuResize(panel)
        panel.removeAllViews()
        panel.addView(
            desk.menuTitle(
                NativeStrings.text("nativeCast"),
                NativeStrings.text("nativeReturn")
            ) { desk.showMainMenu(panel) })

        val selector = runCatching {
            // Initialize CAF, but do not create a detached MediaRouteButton from
            // the accessibility-service context. That path requires an Activity
            // theme and throws IllegalArgumentException on Samsung builds.
            val castContext = CastContext.getSharedInstance(context)
            val castMode = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
                .getString("flutter.cast_mode", "simple") ?: "simple"
            val receiverAppId = if (castMode == "receiver") {
                BuildConfig.CAST_RECEIVER_APP_ID
            } else {
                CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID
            }
            // CastContext is process-global and OptionsProvider is evaluated only
            // once. Without updating it here, changing the mode after the first
            // Cast scan leaves discovery pinned to the previous receiver ID.
            castContext.setReceiverApplicationId(receiverAppId)
            val category = if (castMode == "receiver") {
                CastMediaControlIntent.categoryForCast(
                    BuildConfig.CAST_RECEIVER_APP_ID,
                    listOf(DextopCastProtocol.NAMESPACE)
                )
            } else {
                CastMediaControlIntent.categoryForCast(
                    CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID
                )
            }
            OperationLog.i(
                context,
                "Cast",
                "creating route selector mode=$castMode receiverAppId=$receiverAppId"
            )
            MediaRouteSelector.Builder()
                .addControlCategory(category)
                .build()
        }.getOrElse { error ->
            OperationLog.e(context, "Cast", "unable to create Google Cast route selector", error)
            panel.addView(desk.menuHint(NativeStrings.text("nativeCastUnavailable")))
            return
        }
        val router = MediaRouter.getInstance(context)
        castMediaRouter = router
        val sessionManager = CastContext.getSharedInstance(context).sessionManager
        lateinit var renderRoutes: () -> Unit
        lateinit var routeCallback: MediaRouter.Callback
        var scanning = true
        var scanGeneration = 0L

        fun finishCastConnection(session: CastSession) {
            val castMode = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
                .getString("flutter.cast_mode", "simple") ?: "simple"
            if (castMode == "receiver") {
                runCatching {
                    session.sendMessage(
                        DextopCastProtocol.NAMESPACE,
                        "{\"type\":\"status\",\"text\":\"Dextop connected\"}"
                    )
                }
            } else {
                runCatching {
                    castCompatibilityStreamer?.stop()
                    val streamer = CastCompatibilityStreamer(
                        context,
                        desk.privilegedAccess,
                        desk.targetDisplayId,
                        desk.targetWidth,
                        desk.targetHeight,
                        desk.density
                    )
                    castCompatibilityStreamer = streamer
                    val streamUrl = streamer.start()
                    val media = MediaInfo.Builder(streamUrl)
                        .setStreamType(MediaInfo.STREAM_TYPE_LIVE)
                        .setContentType("video/mp4")
                        .build()
                    val request = MediaLoadRequestData.Builder()
                        .setMediaInfo(media)
                        .setAutoplay(true)
                        .build()
                    session.remoteMediaClient?.load(request)
                        ?: error(moe.n4tsu.dextop.localText("uiDefaultMediaReceiverMediaClientIsUnavailable"))
                    OperationLog.i(context, "Cast", "compatibility video load requested url=$streamUrl")
                    // The accessibility control panel must not remain in the
                    // active render/input path while a second recording
                    // VirtualDisplay starts consuming the desktop. Close it
                    // automatically instead of requiring another gesture.
                    desk.menu?.postDelayed({
                        if (desk.menu?.visibility == View.VISIBLE) desk.toggleMenu()
                    }, 120L)
                }.onFailure { error ->
                    castCompatibilityStreamer?.stop()
                    castCompatibilityStreamer = null
                    OperationLog.e(context, "Cast", "unable to start compatibility video", error)
                }
            }
            OperationLog.i(context, "Cast", "Cast session connected receiver=${session.castDevice?.friendlyName}")
        }

        castSessionListener?.let { sessionManager.removeSessionManagerListener(it, CastSession::class.java) }
        val sessionListener = object : SessionManagerListener<CastSession> {
            override fun onSessionStarted(session: CastSession, sessionId: String) {
                finishCastConnection(session)
                renderRoutes()
            }

            override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
                finishCastConnection(session)
                renderRoutes()
            }

            override fun onSessionStartFailed(session: CastSession, error: Int) {
                OperationLog.e(context, "Cast", "Cast session start failed code=$error")
                panel.addView(desk.menuHint("${NativeStrings.text("nativeCastUnavailable")} ($error)"))
                desk.scheduleMenuHeightUpdate()
            }

            override fun onSessionEnded(session: CastSession, error: Int) {
                OperationLog.i(context, "Cast", "Cast session ended code=$error")
                renderRoutes()
            }

            override fun onSessionEnding(session: CastSession) = Unit
            override fun onSessionResumeFailed(session: CastSession, error: Int) = Unit
            override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
            override fun onSessionStarting(session: CastSession) = Unit
            override fun onSessionSuspended(session: CastSession, reason: Int) = Unit
        }
        castSessionListener = sessionListener
        sessionManager.addSessionManagerListener(sessionListener, CastSession::class.java)

        renderRoutes = renderRoutes@{
            if (desk.menuPrimary !== panel || castMediaRouter !== router) return@renderRoutes
            while (panel.childCount > 1) panel.removeViewAt(1)
            val routes = router.routes.filter {
                !it.isDefault && it.isEnabled && it.matchesSelector(selector)
            }
            OperationLog.i(
                context,
                "Cast",
                "route menu refresh total=${router.routes.size} eligible=${routes.size} " +
                        "routes=${routes.joinToString { it.name.toString() }}"
            )
            val activeSession = sessionManager.currentCastSession
            val activeDeviceName = activeSession?.castDevice?.friendlyName
            val scanRow = FrameLayout(context).apply {
                val scanButton = desk.actionButton(
                    R.drawable.ic_reload,
                    NativeStrings.text("nativeScanAgain")
                ) {
                    scanning = true
                    val generation = ++scanGeneration
                    renderRoutes()
                    router.removeCallback(routeCallback)
                    router.addCallback(
                        selector,
                        routeCallback,
                        MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN
                    )
                    OperationLog.i(context, "Cast", "manual active scan requested")
                    postDelayed({
                        if (generation == scanGeneration) {
                            scanning = false
                            renderRoutes()
                        }
                    }, 2_500L)
                }
                addView(scanButton, FrameLayout.LayoutParams(-1, desk.dp(50)))
                if (scanning) {
                    addView(ProgressBar(context).apply {
                        isIndeterminate = true
                        contentDescription = NativeStrings.text("nativeScanning")
                    }, FrameLayout.LayoutParams(desk.dp(24), desk.dp(24), Gravity.CENTER_VERTICAL or Gravity.END).apply {
                        rightMargin = desk.dp(16)
                    })
                }
            }
            panel.addView(scanRow, LinearLayout.LayoutParams(-1, desk.dp(58)).apply {
                bottomMargin = desk.dp(2)
            })
            if (routes.isEmpty()) {
                panel.addView(
                    desk.menuHint(
                        if (scanning) {
                            NativeStrings.text("nativeScanning")
                        } else {
                            NativeStrings.text("nativeNoCastDevices")
                        }
                    )
                )
            } else {
                routes.forEach { route ->
                    val isActiveRoute = activeSession != null &&
                            (router.selectedRoute.id == route.id || activeDeviceName == route.name.toString())
                    val label = if (isActiveRoute) {
                        "✓ ${route.name}  ·  ${NativeStrings.text("nativeCasting")}"
                    } else {
                        route.name.toString()
                    }
                    panel.addView(desk.actionButton(R.drawable.ic_cast, label) {
                        if (activeSession != null) return@actionButton
                        runCatching { router.selectRoute(route) }
                            .onSuccess {
                                OperationLog.i(context, "Cast", "selected receiver=${route.name}")
                                // Keep discovery alive until CAF confirms that
                                // the receiver application has actually started.
                                while (panel.childCount > 1) panel.removeViewAt(1)
                                panel.addView(desk.menuHint("${NativeStrings.text("nativeCast")}…"))
                                desk.scheduleMenuHeightUpdate()
                                panel.postDelayed({
                                    if (desk.menuPrimary === panel &&
                                        CastContext.getSharedInstance(context).sessionManager.currentCastSession == null
                                    ) {
                                        OperationLog.e(
                                            context,
                                            "Cast",
                                            "Cast receiver launch timed out route=${route.name}"
                                        )
                                        showCastRouteMenu(panel)
                                    }
                                }, 15_000L)
                            }
                            .onFailure { error ->
                                OperationLog.e(context, "Cast", "unable to select receiver=${route.name}", error)
                            }
                    }.apply {
                        isEnabled = activeSession == null
                        alpha = if (activeSession == null) 1f else 0.45f
                    })
                }
            }
            if (activeSession != null) {
                panel.addView(
                    desk.actionButton(
                        R.drawable.ic_stop,
                        NativeStrings.text("nativeStopCasting")
                    ) {
                        endCastSession("user")
                        renderRoutes()
                    })
            }
            desk.scheduleMenuHeightUpdate()
        }

        routeCallback = object : MediaRouter.Callback() {
            override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) = renderRoutes()
            override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) = renderRoutes()
            override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) = renderRoutes()
        }
        castRouteCallback = routeCallback
        router.addCallback(selector, routeCallback, MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN)
        renderRoutes()
        // Google Play services publishes mDNS results asynchronously and some
        // Samsung MediaRouter builds do not deliver the first provider-change
        // callback to a service-owned router. Refresh after both discovery
        // windows so the overlay cannot remain stuck on the initial empty list.
        panel.postDelayed({ renderRoutes() }, 500L)
        panel.postDelayed({
            scanning = false
            renderRoutes()
        }, 1_500L)
    }

    fun stopCastRouteDiscovery() {
        val router = castMediaRouter
        val callback = castRouteCallback
        if (router != null && callback != null) router.removeCallback(callback)
        castMediaRouter = null
        castRouteCallback = null
        castSessionListener?.let { listener ->
            runCatching {
                CastContext.getSharedInstance(context).sessionManager
                    .removeSessionManagerListener(listener, CastSession::class.java)
            }
        }
        castSessionListener = null
    }

    fun endCastSession(reason: String) {
        castCompatibilityStreamer?.stop()
        castCompatibilityStreamer = null
        runCatching {
            val castContext = CastContext.getSharedInstance(context)
            val hadSession = castContext.sessionManager.currentCastSession != null
            castContext.sessionManager.endCurrentSession(true)
            castMediaRouter?.unselect(MediaRouter.UNSELECT_REASON_STOPPED)
            OperationLog.i(context, "Cast", "Cast stop requested reason=$reason active=$hadSession")
        }.onFailure { error ->
            OperationLog.e(context, "Cast", "unable to stop Cast reason=$reason", error)
        }
    }
}
