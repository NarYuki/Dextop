package moe.n4tsu.dextop

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.TypedValue
import android.util.Log
import android.view.Display
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.graphics.drawable.GradientDrawable
import android.graphics.Matrix
import kotlin.math.roundToInt

/**
 * Host used by Android Auto's parked-app activity projection.
 *
 * Android Auto supplies the Activity window with the head-unit display's
 * actual bounds. We keep that SurfaceView as the destination and create a
 * separate content-recording VirtualDisplay for it. The source is the active
 * Dextop overlay. The phone display is never used as an implicit fallback;
 * it remains available only for an explicit developer source selection.
 * No overlay-display setting is changed by this activity, so opening/closing
 * Android Auto cannot replace the Dextop session's display.
 */
class AndroidAutoMirrorActivity : Activity(), SurfaceHolder.Callback {
    companion object {
        const val EXTRA_SOURCE = "moe.n4tsu.dextop.extra.ANDROID_AUTO_SOURCE"
        const val SOURCE_DEXTOP = "dextop"
        const val SOURCE_PHONE = "phone"
        const val SOURCE_AUTO = "auto"

        private var activeInstance: AndroidAutoMirrorActivity? = null
        @Volatile private var autoOwnershipBusy = false

        fun isAutoConnected(): Boolean = activeInstance?.let { !it.isFinishing && !it.isDestroyed } == true

        /**
         * An Auto activity can remain alive on its menu after its overlay has
         * been removed. Expose the session as active only while the selected
         * overlay is still published by DisplayManager; otherwise Flutter and
         * the phone cleanup path must treat Auto as stopped.
         */
        fun isAutoSessionActive(): Boolean {
            val activity = activeInstance ?: return false
            if (!isAutoConnected()) return false
            val id = activity.autoSession.displayId
            return activity.autoSession.isActive && id >= 0 &&
                runCatching { activity.displayManager.getDisplay(id) != null }
                    .getOrDefault(false)
        }

        /** Includes display creation and asynchronous overlay teardown. */
        fun ownsAutoSession(): Boolean {
            val activity = activeInstance
            return autoOwnershipBusy ||
                (activity != null && isAutoConnected() && activity.autoSession.ownsSession)
        }

        internal fun setAutoOwnershipBusy(busy: Boolean) {
            autoOwnershipBusy = busy
        }

        fun autoOverlayDisplayIds(): Set<Int> {
            val activity = activeInstance ?: return emptySet()
            if (!isAutoConnected()) return emptySet()
            // During the short configure/launch window isActive is still
            // false, but the overlay already exists.  Treat that id as owned
            // as well so a phone-side start cannot attach to the Auto display
            // and collapse the two sessions into one.
            val id = activity.autoSession.displayId
            if (id < 0) return emptySet()
            return if (runCatching { activity.displayManager.getDisplay(id) != null }
                    .getOrDefault(false)) setOf(id) else emptySet()
        }

        fun autoOverlaySpecs(): Set<String> = activeInstance?.autoSession
            ?.requestedSpec
            ?.takeIf { it.isNotBlank() }
            ?.let(::setOf)
            ?: emptySet()

        fun autoSourceDisplayId(): Int = activeInstance?.autoSession?.displayId ?: Display.DEFAULT_DISPLAY

        fun stopAutoSessionFromPhone() {
            activeInstance?.returnToAutoMenu()
        }
    }

    private lateinit var surface: SurfaceView
    private lateinit var root: FrameLayout
    private lateinit var controller: AndroidAutoMirrorController
    private var menu: View? = null
    private var menuStatus: TextView? = null
    private var modeSelected = false
    private var orientationSyncPending = false
    private var headUnitCleanup: Runnable? = null
    private var activityStopped = false
    private val autoSession by lazy { AutoDisplaySession(this) }
    private val displayManager by lazy { getSystemService(DisplayManager::class.java) }
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit

        override fun onDisplayRemoved(displayId: Int) {
            if (controller.sourceDisplayId == displayId) surface.post { controller.stop() }
            if (autoSession.displayId == displayId) {
                // Do not leave a dead Auto owner in the shared journal. The
                // next Flutter status poll must see a menu/stopped state.
                autoSession.stop()
                if (modeSelected && controller.selectedSource() == SOURCE_AUTO) {
                    surface.post {
                        showAutoMenu(localized("Auto用ディスプレイが切断されました", "The Auto display was removed"))
                    }
                }
            }
        }

        override fun onDisplayChanged(displayId: Int) {
            if (modeSelected && controller.sourceDisplayId == displayId) {
                surface.post { attachIfReady("source display changed") }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            finish()
            return
        }
        activeInstance = this
        window.setDimAmount(0f)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Android Auto must never silently fall back to the phone display.
        // Without an explicit source, the parked activity starts in the
        // Dextop/Auto-virtual-display flow and waits for the menu selection.
        controller = AndroidAutoMirrorController(
            this,
            intent.getStringExtra(EXTRA_SOURCE) ?: SOURCE_AUTO
        )
        surface = SurfaceView(this).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true
            holder.addCallback(this@AndroidAutoMirrorActivity)
            setOnTouchListener { _, event ->
                controller.dispatchTouch(event, width, height)
            }
        }
        root = FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            addView(surface, FrameLayout.LayoutParams(-1, -1))
        }
        setContentView(root)
        surface.visibility = View.GONE
        displayManager.registerDisplayListener(displayListener, surface.handler)
        showAutoMenu()
    }

    override fun onResume() {
        super.onResume()
        activityStopped = false
        headUnitCleanup?.let { surface.removeCallbacks(it) }
        headUnitCleanup = null
        if (modeSelected) surface.post { attachIfReady("activity resumed") }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        headUnitCleanup?.let { surface.removeCallbacks(it) }
        headUnitCleanup = null
        surface.post { attachIfReady("surface created") }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (modeSelected && width > 0 && height > 0) {
            alignPhoneMirrorIfNeeded(width, height)
            attachIfReady("head-unit surface changed")
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        controller.detachSurface()
        if (modeSelected) {
            val cleanup = Runnable {
                headUnitCleanup = null
                if (modeSelected && !surface.holder.surface.isValid) {
                    stopForHeadUnitDisconnect()
                }
            }
            headUnitCleanup = cleanup
            surface.postDelayed(cleanup, 600L)
        }
    }

    override fun onDestroy() {
        runCatching { displayManager.unregisterDisplayListener(displayListener) }
        controller.stop()
        autoSession.stop()
        if (activeInstance === this) activeInstance = null
        super.onDestroy()
    }

    override fun onStop() {
        super.onStop()
        activityStopped = true
        if (modeSelected || autoSession.ownsSession) {
            val cleanup = Runnable {
                headUnitCleanup = null
                // DHU and real head units may retain a valid Surface after the
                // parked Activity has been stopped. Activity lifecycle is the
                // authoritative disconnect signal; onResume cancels this grace
                // period for short system transitions.
                if (activityStopped) {
                    stopForHeadUnitDisconnect()
                }
            }
            headUnitCleanup?.let { surface.removeCallbacks(it) }
            headUnitCleanup = cleanup
            surface.postDelayed(cleanup, 700L)
        }
    }

    private fun stopForHeadUnitDisconnect() {
        modeSelected = false
        orientationSyncPending = false
        controller.stop()
        autoSession.stop()
        OperationLog.i(this, "AndroidAuto", "head-unit disconnected; Auto overlay cleaned up")
    }

    private fun attachIfReady(reason: String, orientationSettled: Boolean = false) {
        if (!modeSelected || !surface.holder.surface.isValid ||
            surface.width <= 0 || surface.height <= 0) return
        if (!orientationSettled && controller.selectedSource() == SOURCE_DEXTOP && autoOrientationMatchingEnabled()) {
            // Phone rotation recreates/resizes its overlay. Do not attach the
            // Auto surface to the old aspect ratio during that handoff.
            if (!orientationSyncPending) {
                orientationSyncPending = true
                alignPhoneMirrorIfNeeded(surface.width, surface.height)
                surface.postDelayed({
                    orientationSyncPending = false
                    if (modeSelected) attachIfReady("orientation synchronized", orientationSettled = true)
                }, 220L)
            }
            return
        }
        controller.attach(surface, surface.width, surface.height, reason)
    }

    /**
     * Android Auto launches this activity as a full-screen parked app.  The
     * first screen is intentionally a Dextop-owned menu instead of an
     * immediate phone screenshot, so the user can choose the source and can
     * explicitly create an Auto-sized Dextop session.
     */
    private fun showAutoMenu(message: String? = null) {
        modeSelected = false
        controller.detachSurface()
        surface.visibility = View.GONE
        menu?.let { root.removeView(it) }
        OperationLog.i(
            this,
            "AndroidAuto",
            "Auto menu displayed active=${MirrorService.isActive()} source=${controller.selectedSource()}"
        )

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(Color.rgb(10, 10, 12))
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), dp(24), dp(28), dp(24))
            background = rounded(Color.rgb(29, 28, 34), dp(24), Color.rgb(68, 65, 78), dp(1))
        }
        scroll.addView(panel, FrameLayout.LayoutParams(-1, -2))

        panel.addView(label("Dextop", 28f, Color.WHITE, true), LinearLayout.LayoutParams(-1, -2))
        panel.addView(
            label(localized("Android Auto", "Android Auto"), 16f, Color.rgb(193, 187, 207), false),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) }
        )
        panel.addView(
            label(
                localized(
                    "Android Autoで表示するDextopの方法を選択してください。",
                    "Choose how Dextop should appear in Android Auto."
                ),
                15f,
                Color.rgb(211, 207, 218),
                false
            ),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18); bottomMargin = dp(18) }
        )

        val status = label(
            message ?: if (MirrorService.isActive()) {
                localized("準備できました", "Ready")
            } else {
                localized("表示方法を選択してください", "Select a display mode")
            },
            15f,
            Color.rgb(215, 190, 255),
            true
        ).apply {
            setPadding(dp(14), dp(11), dp(14), dp(11))
            background = rounded(Color.rgb(48, 42, 58), dp(12), Color.rgb(106, 88, 130), dp(1))
        }
        menuStatus = status
        panel.addView(status, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) })

        addModeButton(
            panel,
            localized("Dextopをミラーリング", "Mirror Dextop"),
            localized(
                "起動中のDextopデスクトップをそのまま表示します。",
                "Show the currently running Dextop desktop."
            ),
            SOURCE_DEXTOP
        )
        addModeButton(
            panel,
            localized("Auto用仮想ディスプレイを作成", "Create an Auto virtual display"),
            localized(
                "車載画面の解像度に合わせてDextopを準備して表示します。",
                "Prepare Dextop using the head-unit size and display it."
            ),
            SOURCE_AUTO
        )
        panel.addView(
            label(
                localized(
                    "選択後も端末の戻る操作でこのメニューへ戻れます。",
                    "Use the Android Auto back action to return to this menu."
                ),
                13f,
                Color.rgb(166, 160, 174),
                false
            ),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
        )

        menu = scroll
        root.addView(
            scroll,
            FrameLayout.LayoutParams(-1, -2).apply {
                gravity = android.view.Gravity.CENTER
                leftMargin = dp(24)
                rightMargin = dp(24)
                topMargin = dp(18)
                bottomMargin = dp(18)
            }
        )
    }

    private fun addModeButton(parent: LinearLayout, title: String, detail: String, source: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            isFocusable = true
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = rounded(Color.rgb(45, 43, 52), dp(14), Color.rgb(82, 78, 91), dp(1))
            setOnClickListener { selectMode(source) }
        }
        row.addView(label(title, 17f, Color.WHITE, true), LinearLayout.LayoutParams(-1, -2))
        row.addView(
            label(detail, 13f, Color.rgb(190, 185, 199), false),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) }
        )
        parent.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(11) })
    }

    private fun selectMode(source: String) {
        if (source == SOURCE_DEXTOP && !MirrorService.isActive()) {
            menuStatus?.text = localized(
                "起動中のDextopがありません。Auto用仮想ディスプレイを選択してください。",
                "No Dextop session is running. Choose the Auto virtual display option."
            )
            return
        }
        // Auto always owns its own overlay display. In particular, do not
        // fall through to the phone-side mirror when a normal Dextop session
        // is already running: the two sessions are intentionally independent.
        if (source == SOURCE_AUTO) {
            if (MirrorService.ownsPhoneSession()) {
                menuStatus?.text = localized(
                    "端末側のDextopを終了してからAuto用ディスプレイを開始してください。",
                    "Stop the phone Dextop session before starting the Auto display."
                )
                return
            }
            menuStatus?.text = localized("準備しています…", "Preparing…")
            val (width, height) = hostSize()
            val dpi = resources.displayMetrics.densityDpi.coerceAtLeast(160)
            // Keep the normal overlay undecorated. System decorations are a
            // HOME-launch fallback only; enabling them here changes the
            // display contract and breaks some head units.
            autoSession.start(width, height, dpi, secure = false, decorations = false) { result ->
                runOnUiThread {
                    result.onSuccess {
                        controller.selectSource(SOURCE_AUTO)
                        startProjection()
                    }.onFailure { error ->
                        menuStatus?.text = localized(
                            "Dextopを準備できませんでした: ${error.message ?: "不明なエラー"}",
                            "Dextop could not be prepared: ${error.message ?: "unknown error"}"
                        )
                    }
                }
            }
            return
        }
        if (source == SOURCE_DEXTOP && autoOrientationMatchingEnabled()) {
            val (width, height) = hostSize()
            MirrorService.alignPhoneMirrorToAndroidAuto(width, height)
        }
        controller.selectSource(source)
        startProjection()
    }

    private fun startProjection() {
        modeSelected = true
        menu?.let { root.removeView(it) }
        menu = null
        surface.visibility = View.VISIBLE
        surface.post {
            surface.requestFocus()
            alignPhoneMirrorIfNeeded(surface.width, surface.height)
            attachIfReady("Auto display mode selected")
        }
    }

    private fun alignPhoneMirrorIfNeeded(width: Int, height: Int) {
        if (!modeSelected || controller.selectedSource() != SOURCE_DEXTOP ||
            !autoOrientationMatchingEnabled() || width <= 0 || height <= 0) return
        MirrorService.alignPhoneMirrorToAndroidAuto(width, height)
    }

    override fun onBackPressed() {
        if (modeSelected) {
            returnToAutoMenu()
        } else {
            super.onBackPressed()
        }
    }

    private fun returnToAutoMenu() {
        modeSelected = false
        orientationSyncPending = false
        controller.stop()
        autoSession.stop()
        showAutoMenu()
    }

    private fun autoOrientationMatchingEnabled(): Boolean =
        getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
            .getBoolean("flutter.android_auto_match_phone_orientation", true)

    private fun hostSize(): Pair<Int, Int> {
        val bounds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds
        } else {
            android.graphics.Rect(0, 0, resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
        }
        return bounds.width().coerceAtLeast(320) to bounds.height().coerceAtLeast(240)
    }

    private fun label(text: String, sizeSp: Float, color: Int, bold: Boolean): TextView =
        TextView(this).apply {
            this.text = text
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            typeface = android.graphics.Typeface.create("sans", if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            includeFontPadding = true
        }

    private fun rounded(fill: Int, radius: Int, stroke: Int, strokeWidth: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = radius.toFloat()
            setStroke(strokeWidth, stroke)
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt().coerceAtLeast(1)

    private fun localized(japanese: String, english: String): String =
        if (resources.configuration.locales[0].language.equals("ja", ignoreCase = true)) japanese else english
}

/**
 * Owns the overlay used exclusively by Android Auto.  This is deliberately
 * independent from MirrorService: the phone-side Dextop session can therefore
 * be started or stopped while Auto keeps its own display alive.
 */
internal class AutoDisplaySession(private val activity: Context) {
    private val displayManager = activity.getSystemService(DisplayManager::class.java)
    private val privilegedAccess = PrivilegedAccess("DextopAndroidAutoSession")
    private val environment = DesktopEnvironmentRegistry.current()
    private val backend = DisplayMirrorBackend(
        activity,
        activity.contentResolver,
        displayManager,
        privilegedAccess,
        environment
    )
    private val handler = Handler(Looper.getMainLooper())
    private val sessionJournal = SessionJournal(activity)
    var requestedSpec: String? = null
        private set
    private var desktopConfigurator: DesktopModeConfigurator? = null
    private var completion: ((Result<Int>) -> Unit)? = null
    private var stopCompletion: (() -> Unit)? = null
    private var creating = false
    private var stopping = false
    private var homeDecorationRetryUsed = false

    val ownsSession: Boolean
        get() = creating || isActive || stopping || requestedSpec != null

    var displayId: Int = -1
        private set
    var isActive: Boolean = false
        private set

    fun start(
        width: Int,
        height: Int,
        density: Int,
        secure: Boolean,
        decorations: Boolean,
        callback: (Result<Int>) -> Unit
    ) {
        if (isActive && displayId >= 0) {
            callback(Result.success(displayId))
            return
        }
        if (creating) {
            callback(Result.failure(IllegalStateException("Android Auto display creation is already in progress")))
            return
        }
        if (stopping) {
            callback(Result.failure(IllegalStateException("Android Auto display cleanup is still in progress")))
            return
        }
        if (!privilegedAccess.isAvailable()) {
            callback(Result.failure(IllegalStateException(NativeStrings.text("nativeShizukuUnavailable"))))
            return
        }
        creating = true
        AndroidAutoMirrorActivity.setAutoOwnershipBusy(true)
        homeDecorationRetryUsed = false
        completion = callback
        val existing = backend.currentDisplayIds()
        val effectiveDecorations = decorations || shouldUsePersistedSystemDecorations()
        val spec = displaySpec(width, height, density, secure, effectiveDecorations)
        requestedSpec = spec
        runCatching {
            // An interrupted older build may have left several copies of the
            // exact Auto request behind. Remove those before taking the
            // recovery snapshot; otherwise stopping this new session would
            // faithfully restore the stale duplicates and grow the display
            // list again. A live phone owner is never modified.
            val before = Settings.Global.getString(
                activity.contentResolver,
                DisplayMirrorBackend.DISPLAY_SPECIFICATION
            ).orEmpty()
            if (!MirrorService.ownsOverlaySpec(spec)) {
                val sanitized = before.split(';')
                    .filter { it.isNotBlank() && it != spec }
                    .joinToString(";")
                if (sanitized != before) {
                    check(Settings.Global.putString(
                        activity.contentResolver,
                        DisplayMirrorBackend.DISPLAY_SPECIFICATION,
                        sanitized
                    )) { "Unable to remove stale Android Auto overlay requests" }
                    OperationLog.i(activity, "AndroidAuto", "removed stale Auto overlay entries before start")
                }
            }
            sessionJournal.preparing(width, height, density, effectiveDecorations)
            activity.getSharedPreferences("dextop_cleanup_state", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("cleanup_pending", true)
                .putBoolean("paused_by_user", false)
                .putLong("started_at", System.currentTimeMillis())
                .commit()
            // Do not clear the global request here.  A phone-side session may
            // already own another overlay; append the Auto entry instead.
            val current = Settings.Global.getString(
                activity.contentResolver,
                DisplayMirrorBackend.DISPLAY_SPECIFICATION
            ).orEmpty()
            // Keep the Auto-owned entry first. MirrorService preserves this
            // entry when it creates/resizes the phone display, which gives us
            // deterministic ownership even if both sessions use identical
            // width/height/density flags.
            val entries = current.split(';').filter { it.isNotBlank() }
            // Keep one Auto entry and at most one existing identical entry
            // (the phone session). Older builds could append the same Auto
            // spec repeatedly, which is what produced dozens of overlays.
            val existingIdentical = MirrorService.ownsOverlaySpec(spec)
            val otherEntries = entries.filterNot { it == spec }
            val requested = buildList {
                add(spec)
                if (existingIdentical) add(spec)
                addAll(otherEntries)
            }.joinToString(";")
            check(Settings.Global.putString(
                activity.contentResolver,
                DisplayMirrorBackend.DISPLAY_SPECIFICATION,
                requested
            )) { "Unable to request Android Auto overlay display" }
            OperationLog.i(activity, "AndroidAuto", "requested independent overlay spec=$spec")
            // Keep a separate owner for the Auto display even when a phone
            // Dextop session is already running. The journal is shared by
            // design, so the last owner to stop restores the original
            // environment after both displays are gone.
            desktopConfigurator = DesktopModeConfigurator(
                activity,
                activity.contentResolver,
                privilegedAccess,
                environment,
                sessionJournal
            ).also { it.applyForCurrentDevice() }
            waitForDisplay(existing, width, height, density, secure, effectiveDecorations, 0)
        }.onFailure { fail(it) }
    }

    fun stop(onStopped: (() -> Unit)? = null) {
        if (onStopped != null) {
            val previous = stopCompletion
            stopCompletion = if (previous == null) onStopped else {
                {
                    previous()
                    onStopped()
                }
            }
        }
        if (stopping) return
        if (!ownsSession && sessionJournal.snapshot()["transactionOpen"] != true) {
            stopCompletion?.also { completion ->
                stopCompletion = null
                completion()
            }
            return
        }
        stopping = true
        val oldId = displayId
        isActive = false
        creating = false
        completion = null
        handler.removeCallbacksAndMessages(null)
        val spec = requestedSpec
        if (oldId >= 0) {
            OperationLog.i(activity, "AndroidAuto", "stopping independent overlay display=$oldId")
        }
        if (!spec.isNullOrBlank()) {
            // Auto inserts its owned entry first. Always remove that entry;
            // removing the last identical entry can leave the Auto overlay
            // alive when the phone session is stopping at the same time.
            runCatching { removeSpec(spec, removeFirst = true) }
                .onFailure { OperationLog.w(activity, "AndroidAuto", "unable to remove Auto overlay request", it) }
            verifyOverlayRemoval(spec) { finishStop() }
        } else {
            finishStop()
        }
        reapplyTopologyForRemainingDisplays()
    }

    private fun finishStop() {
        if (!stopping) return
        val phoneStillOwnsEnvironment = MirrorService.ownsPhoneSession()
        displayId = -1
        requestedSpec = null
        if (!phoneStillOwnsEnvironment) {
            desktopConfigurator?.let { configurator ->
                runCatching { configurator.restore() }
                    .onFailure {
                        OperationLog.w(
                            activity,
                            "AndroidAuto",
                            "Auto desktop settings restore failed",
                            it
                        )
                    }
            }
            runCatching { sessionJournal.restoreSystemSettings() }
                .onFailure {
                    OperationLog.w(
                        activity,
                        "AndroidAuto",
                        "Auto session journal restore failed",
                        it
                    )
                }
            sessionJournal.clear()
            activity.getSharedPreferences("dextop_cleanup_state", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("cleanup_pending", false)
                .putBoolean("paused_by_user", false)
                .remove("paused_workspace")
                .putLong("verified_at", System.currentTimeMillis())
                .apply()
        }
        desktopConfigurator = null
        stopping = false
        AndroidAutoMirrorActivity.setAutoOwnershipBusy(false)
        reapplyTopologyForRemainingDisplays()
        stopCompletion?.also { completion ->
            stopCompletion = null
            completion()
        }
    }

    private fun waitForDisplay(
        existing: Set<Int>,
        width: Int,
        height: Int,
        density: Int,
        secure: Boolean,
        decorations: Boolean,
        attempt: Int
    ) {
        if (!creating) return
        // Never claim the phone session's display.  Both sessions may use the
        // same resolution/specification, so display-id ownership—not size—is
        // the only reliable discriminator while the overlay is being added.
        val excluded = MirrorService.phoneOverlayDisplayIds()
        val display = backend.findCreatedDisplay(existing, excluded)
        if (display == null) {
            if (attempt < 60) {
                handler.postDelayed({
                    waitForDisplay(existing, width, height, density, secure, decorations, attempt + 1)
                }, 100L)
            } else {
                fail(IllegalStateException("Android Auto overlay display creation timed out"))
            }
            return
        }
        runCatching {
            displayId = display.displayId
            desktopConfigurator?.configureDisplay(displayId)
            if (!launchHome(displayId)) {
                if (!decorations && retryHomeWithSystemDecorations(width, height, density, secure)) return
                error("The Android Auto Dextop HOME activity could not be launched")
            }
            isActive = true
            creating = false
            sessionJournal.running(displayId)
            reapplyTopologyForRemainingDisplays()
            complete(Result.success(displayId))
            OperationLog.i(activity, "AndroidAuto", "independent overlay ready display=$displayId ${width}x$height/$density")
        }.onFailure { fail(it) }
    }

    private fun launchHome(id: Int): Boolean = runCatching {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            // A phone-side HOME task may already exist. MULTIPLE_TASK keeps
            // this launch attached to the Auto overlay instead of reusing or
            // moving the phone's HOME task when both sessions run together.
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            )
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(id)
        activity.startActivity(intent, options.toBundle())
    }.onFailure { OperationLog.e(activity, "AndroidAuto", "Auto HOME launch failed display=$id", it) }
        .isSuccess

    private fun fail(error: Throwable) {
        creating = false
        displayId = -1
        isActive = false
        val spec = requestedSpec
        stopping = true
        val phoneStillOwnsEnvironment = MirrorService.ownsPhoneSession()
        if (!spec.isNullOrBlank()) {
            runCatching { removeSpec(spec, removeFirst = true) }
            verifyOverlayRemoval(spec) { finishFailureCleanup(error) }
            return
        }
        finishFailureCleanup(error)
    }

    private fun finishFailureCleanup(error: Throwable) {
        requestedSpec = null
        displayId = -1
        val phoneStillOwnsEnvironment = MirrorService.ownsPhoneSession()
        if (!phoneStillOwnsEnvironment) {
            desktopConfigurator?.let { runCatching { it.restore() } }
            runCatching { sessionJournal.restoreSystemSettings() }
            sessionJournal.clear()
            activity.getSharedPreferences("dextop_cleanup_state", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("cleanup_pending", false)
                .putBoolean("paused_by_user", false)
                .remove("paused_workspace")
                .putLong("verified_at", System.currentTimeMillis())
                .apply()
        }
        desktopConfigurator = null
        stopping = false
        AndroidAutoMirrorActivity.setAutoOwnershipBusy(false)
        complete(Result.failure(error))
        OperationLog.e(activity, "AndroidAuto", "independent display creation failed", error)
        reapplyTopologyForRemainingDisplays()
    }

    private fun complete(result: Result<Int>) {
        val callback = completion ?: return
        completion = null
        callback(result)
    }

    private fun firmwareIdentity(): String = Build.FINGERPRINT.ifBlank {
        listOf(Build.DISPLAY, Build.VERSION.INCREMENTAL, Build.VERSION.SECURITY_PATCH)
            .joinToString("/")
    }

    private fun shouldUsePersistedSystemDecorations(): Boolean {
        if (!environment.platformManaged) return false
        val preferences = activity.getSharedPreferences("dextop_home_launch_recovery", Context.MODE_PRIVATE)
        val stored = preferences.getString("firmware_fingerprint", null) ?: return false
        if (stored == firmwareIdentity()) return true
        preferences.edit().remove("firmware_fingerprint").apply()
        OperationLog.i(activity, "AndroidAuto", "firmware changed; retrying Auto HOME without decorations")
        return false
    }

    private fun rememberSystemDecorationsForFirmware() {
        if (!environment.platformManaged) return
        activity.getSharedPreferences("dextop_home_launch_recovery", Context.MODE_PRIVATE)
            .edit()
            .putString("firmware_fingerprint", firmwareIdentity())
            .apply()
    }

    /** Rebuild only after an actual Samsung HOME launch rejection. */
    private fun retryHomeWithSystemDecorations(
        width: Int,
        height: Int,
        density: Int,
        secure: Boolean
    ): Boolean {
        if (!environment.platformManaged || homeDecorationRetryUsed) return false
        homeDecorationRetryUsed = true
        rememberSystemDecorationsForFirmware()
        val previousSpec = requestedSpec ?: return false
        val previousId = displayId
        OperationLog.w(
            activity,
            "AndroidAuto",
            "Auto HOME launch denied display=$previousId; retrying with system decorations"
        )
        displayId = -1
        runCatching { removeSpec(previousSpec, removeFirst = true) }
            .onFailure {
                fail(it)
                return true
            }
        handler.postDelayed({
            if (!creating || stopping) return@postDelayed
            runCatching {
                val existing = backend.currentDisplayIds()
                val decoratedSpec = displaySpec(width, height, density, secure, decorations = true)
                requestedSpec = decoratedSpec
                sessionJournal.preparing(width, height, density, decorations = true)
                val entries = Settings.Global.getString(
                    activity.contentResolver,
                    DisplayMirrorBackend.DISPLAY_SPECIFICATION
                ).orEmpty().split(';').filter { it.isNotBlank() && it != decoratedSpec }
                val requested = (listOf(decoratedSpec) + entries).joinToString(";")
                check(Settings.Global.putString(
                    activity.contentResolver,
                    DisplayMirrorBackend.DISPLAY_SPECIFICATION,
                    requested
                )) { "Unable to request decorated Android Auto overlay display" }
                waitForDisplay(existing, width, height, density, secure, decorations = true, attempt = 0)
            }.onFailure { fail(it) }
        }, 450L)
        return true
    }

    private fun displaySpec(width: Int, height: Int, density: Int, secure: Boolean, decorations: Boolean): String {
        val flags = buildList {
            if (secure) add("secure")
            if (decorations) add("should_show_system_decorations")
        }
        return "${width.coerceAtLeast(320)}x${height.coerceAtLeast(240)}/${density.coerceIn(80, 640)}" +
            flags.joinToString(separator = ",", prefix = if (flags.isEmpty()) "" else ",")
    }

    private fun removeSpec(spec: String, removeFirst: Boolean = false) {
        val current = Settings.Global.getString(
            activity.contentResolver,
            DisplayMirrorBackend.DISPLAY_SPECIFICATION
        ).orEmpty()
        val entries = current.split(';').filter { it.isNotBlank() }.toMutableList()
        val index = if (removeFirst) {
            entries.indexOfFirst { it == spec }
        } else {
            entries.indexOfLast { it == spec }
        }
        if (index >= 0) entries.removeAt(index)
        check(Settings.Global.putString(
            activity.contentResolver,
            DisplayMirrorBackend.DISPLAY_SPECIFICATION,
            entries.joinToString(";")
        )) { "Unable to remove Android Auto overlay display" }
    }

    private fun verifyOverlayRemoval(spec: String, attempt: Int = 0, finished: () -> Unit = {}) {
        handler.postDelayed({
            runCatching {
                val current = Settings.Global.getString(
                    activity.contentResolver,
                    DisplayMirrorBackend.DISPLAY_SPECIFICATION
                ).orEmpty()
                val matching = current.split(';').count { it.isNotBlank() && it == spec }
                // The phone session may start or finish while Android applies
                // the setting. Re-read ownership on every pass so cleanup
                // never removes the phone's surviving identical entry.
                val expected = if (MirrorService.ownsOverlaySpec(spec)) 1 else 0
                if (matching > expected) {
                    repeat(matching - expected) { removeSpec(spec, removeFirst = true) }
                    val remaining = Settings.Global.getString(
                        activity.contentResolver,
                        DisplayMirrorBackend.DISPLAY_SPECIFICATION
                    ).orEmpty().split(';').count { it.isNotBlank() && it == spec }
                    OperationLog.i(
                        activity,
                        "AndroidAuto",
                        "verified Auto overlay cleanup remaining=$remaining expected=$expected"
                    )
                    if (remaining > expected && attempt < 5) {
                        verifyOverlayRemoval(spec, attempt + 1, finished)
                        return@runCatching
                    }
                }
                finished()
            }.onFailure {
                OperationLog.w(activity, "AndroidAuto", "overlay cleanup verification failed", it)
                if (attempt < 5) verifyOverlayRemoval(spec, attempt + 1, finished) else finished()
            }
        }, 250L)
    }

    private fun reapplyTopologyForRemainingDisplays() {
        runCatching {
            val ids = buildSet {
                MirrorService.topologyOverlayDisplayId().takeIf { it >= 0 }?.let(::add)
                AndroidAutoMirrorActivity.autoOverlayDisplayIds().forEach(::add)
            }
            if (ids.isNotEmpty()) {
                DisplayEnvironmentSettings(activity).activateTopologyForOverlays(ids)
            } else {
                DisplayEnvironmentSettings(activity).restoreTopology()
            }
        }.onFailure {
            OperationLog.w(activity, "DisplayTopology", "Auto topology refresh skipped", it)
        }
    }
}

/** Owns the car-side recording VirtualDisplay without touching Dextop's host. */
internal class AndroidAutoMirrorController(
    private val context: Context,
    initialSource: String?
) {
    private val logTag = "DextopAndroidAuto"
    private val displayManager = context.getSystemService(DisplayManager::class.java)
    private val privilegedAccess = PrivilegedAccess(logTag)
    private val backend = DisplayMirrorBackend(
        context,
        context.contentResolver,
        displayManager,
        privilegedAccess,
        DesktopEnvironmentRegistry.current()
    )
    private val inputDispatcher = InputDispatcher(privilegedAccess) { _, displayId, accepted, error ->
        if (!accepted) {
            OperationLog.w(
                context,
                "AndroidAuto",
                "touch injection rejected display=$displayId",
                error
            )
        }
    }

    var sourceDisplayId: Int = -1
        private set
    private var sourceWidth = 0
    private var sourceHeight = 0
    private var sourceDensity = 160
    private var hostWidth = 0
    private var hostHeight = 0
    private var attachedSurface: SurfaceView? = null
    private var requestedSource: String? = initialSource
    private var explicitSourceDisplayId: Int? = null

    fun selectSource(source: String) {
        requestedSource = source
        explicitSourceDisplayId = null
        OperationLog.i(context, "AndroidAuto", "source selected=$source")
    }

    fun selectSourceDisplay(displayId: Int) {
        requestedSource = AndroidAutoMirrorActivity.SOURCE_AUTO
        explicitSourceDisplayId = displayId
        OperationLog.i(context, "AndroidAuto", "explicit Auto source selected display=$displayId")
    }

    fun selectedSource(): String = requestedSource ?: AndroidAutoMirrorActivity.SOURCE_AUTO

    /** Uses an already-rendering direct CARDEX display for input only. */
    fun bindInputSource(displayId: Int, width: Int, height: Int, density: Int) {
        backend.releaseLayer()
        sourceDisplayId = displayId
        sourceWidth = width.coerceAtLeast(1)
        sourceHeight = height.coerceAtLeast(1)
        sourceDensity = density.coerceAtLeast(1)
        hostWidth = sourceWidth
        hostHeight = sourceHeight
        attachedSurface = null
        OperationLog.i(
            context,
            "AndroidAuto",
            "input bound to direct Auto display=$displayId ${sourceWidth}x$sourceHeight/$sourceDensity"
        )
    }

    fun attach(host: SurfaceView, width: Int, height: Int, reason: String) {
        attach(host, host.holder.surface, width, height, reason)
    }

    fun attach(surface: android.view.Surface, width: Int, height: Int, reason: String) {
        attach(null, surface, width, height, reason)
    }

    private fun attach(
        host: SurfaceView?,
        destinationSurface: android.view.Surface,
        width: Int,
        height: Int,
        reason: String
    ) {
        val source = resolveSourceDisplay() ?: run {
            OperationLog.w(context, "AndroidAuto", "no source display available reason=$reason", null)
            return
        }
        val metrics = DisplayMetrics()
        source.getRealMetrics(metrics)
        sourceDisplayId = source.displayId
        sourceWidth = metrics.widthPixels.takeIf { it > 0 } ?: source.width
        sourceHeight = metrics.heightPixels.takeIf { it > 0 } ?: source.height
        sourceDensity = metrics.densityDpi.takeIf { it > 0 } ?: 160
        hostWidth = width
        hostHeight = height
        attachedSurface = host
        if (!privilegedAccess.isAvailable()) {
            OperationLog.w(context, "AndroidAuto", "privileged display access unavailable", null)
            return
        }
        runCatching {
            if (host != null) {
                backend.attach(
                    sourceDisplayId, host, width, height,
                    sourceWidth.coerceAtLeast(1), sourceHeight.coerceAtLeast(1),
                    sourceDensity, strategyOverride = "virtual_display"
                )
            } else {
                backend.attachSurface(
                    sourceDisplayId, destinationSurface, width, height,
                    sourceWidth.coerceAtLeast(1), sourceHeight.coerceAtLeast(1), sourceDensity
                )
            }
        }.onSuccess {
            OperationLog.i(
                context,
                "AndroidAuto",
                "mirror attached reason=$reason source=$sourceDisplayId " +
                    "content=${sourceWidth}x$sourceHeight/$sourceDensity host=${width}x$height"
            )
        }.onFailure {
            OperationLog.e(context, "AndroidAuto", "mirror attach failed reason=$reason", it)
            Log.e(logTag, "Android Auto mirror attach failed", it)
        }
    }

    fun detachSurface() {
        attachedSurface = null
        backend.releaseLayer()
    }

    fun stop() {
        backend.releaseLayer()
        attachedSurface = null
        sourceDisplayId = -1
    }

    fun dispatchTouch(event: MotionEvent, width: Int, height: Int): Boolean {
        val displayId = sourceDisplayId
        if (displayId < 0 || width <= 0 || height <= 0 || sourceWidth <= 0 || sourceHeight <= 0) {
            return true
        }
        val copy = MotionEvent.obtain(event)
        try {
            val matrix = Matrix().apply {
                setScale(sourceWidth.toFloat() / width, sourceHeight.toFloat() / height)
            }
            copy.transform(matrix)
            inputDispatcher.send(copy, displayId)
            // The car surface is the sole input target. Do not let the
            // Activity dispatch the unscaled event a second time if the
            // privileged injection is temporarily rejected.
            return true
        } catch (error: Throwable) {
            OperationLog.w(context, "AndroidAuto", "touch injection failed display=$displayId", error)
            return true
        } finally {
            copy.recycle()
        }
    }

    private fun resolveSourceDisplay(): Display? {
        val explicit = requestedSource?.lowercase()
        val preferred = explicitSourceDisplayId ?: when (explicit) {
            AndroidAutoMirrorActivity.SOURCE_PHONE -> Display.DEFAULT_DISPLAY
            AndroidAutoMirrorActivity.SOURCE_DEXTOP -> MirrorService.androidAutoSourceDisplayId()
            AndroidAutoMirrorActivity.SOURCE_AUTO -> AndroidAutoMirrorActivity.autoSourceDisplayId()
            else -> MirrorService.androidAutoSourceDisplayId()
        }
        OperationLog.i(
            context,
            "AndroidAuto",
            "resolve source=${explicit ?: AndroidAutoMirrorActivity.SOURCE_AUTO} " +
                "preferred=$preferred active=${MirrorService.isActive()}"
        )
        // The Auto path is intentionally strict: a missing Dextop overlay is
        // a preparation error, not permission to mirror the phone screen.
        // SOURCE_PHONE remains available only for an explicit developer
        // intent and is no longer exposed in the Auto menu.
        if (explicit != AndroidAutoMirrorActivity.SOURCE_PHONE &&
            preferred == Display.DEFAULT_DISPLAY) {
            OperationLog.w(
                context,
                "AndroidAuto",
                "Dextop Auto source is unavailable; phone display fallback disabled",
                null
            )
            return null
        }
        val display = displayManager.getDisplay(preferred) ?: return null
        if (explicit != AndroidAutoMirrorActivity.SOURCE_PHONE) {
            val type = runCatching {
                Display::class.java.getMethod("getType").invoke(display) as Int
            }.getOrDefault(-1)
            if (type != 4) {
                OperationLog.w(
                    context,
                    "AndroidAuto",
                    "Dextop Auto source id=$preferred is not an overlay display type=$type",
                    null
                )
                return null
            }
        }
        return display
    }
}
