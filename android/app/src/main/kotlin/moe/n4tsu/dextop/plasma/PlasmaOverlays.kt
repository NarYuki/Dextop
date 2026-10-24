package moe.n4tsu.dextop.plasma

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import java.util.concurrent.Executors

/**
 * Adds and moves accessibility overlay windows on the Dextop display.
 *
 * Every Plasma surface (panel, popups, decorations, effects) is a separate
 * TYPE_ACCESSIBILITY_OVERLAY window so it receives input injected into the
 * virtual display and stacks above application tasks. [windowType] can be
 * TYPE_APPLICATION_OVERLAY where accessibility overlays are unavailable.
 */
internal class OverlayLayer(
    val context: Context,
    private val tag: String,
    private val windowType: Int = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
    private val displayOverlayService: android.accessibilityservice.AccessibilityService? = null,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val attached = HashMap<View, WindowManager.LayoutParams>()

    private data class HostedOverlay(
        val host: android.view.SurfaceControlViewHost,
        val surface: android.view.SurfaceControlViewHost.SurfacePackage,
        var placed: Boolean = false,
    )
    private val hosted = HashMap<View, HostedOverlay>()
    private val attachHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val attachWithResult by lazy {
        android.accessibilityservice.AccessibilityService::class.java.methods.firstOrNull {
            it.name == "attachAccessibilityOverlayToDisplay" && it.parameterTypes.size == 4
        }
    }

    private fun placeHosted(view: View, overlay: HostedOverlay, attempt: Int = 0) {
        if (hosted[view] !== overlay || overlay.placed || !overlay.surface.surfaceControl.isValid) return
        val service = displayOverlayService ?: return
        val displayId = context.display?.displayId ?: return
        val answered = java.util.concurrent.atomic.AtomicBoolean()
        fun finish(result: Int?) {
            if (!answered.compareAndSet(false, true) || hosted[view] !== overlay) return
            if (result == 0) {
                overlay.placed = true
                view.invalidate()
                Log.i(tag, "Auto overlay attached display=$displayId attempt=$attempt")
                return
            }
            if (attempt < 7) {
                attachHandler.postDelayed({ placeHosted(view, overlay, attempt + 1) },
                    (50L shl attempt).coerceAtMost(1_000L))
            } else if (result != null) {
                Log.e(tag, "Auto overlay attachment failed display=$displayId result=$result")
            }
        }
        try {
            val callbackMethod = attachWithResult
            if (callbackMethod != null) {
                callbackMethod.invoke(service, displayId, overlay.surface.surfaceControl,
                    java.util.concurrent.Executor { attachHandler.post(it) },
                    java.util.function.IntConsumer { result -> finish(result) })
                attachHandler.postDelayed({ finish(null) }, 750L)
            } else {
                service.attachAccessibilityOverlayToDisplay(displayId, overlay.surface.surfaceControl)
                // The older API returns before WMS attaches the surface and reports no result.
                // Repeating the same attachment is safe once the display container exists.
                finish(null)
            }
        } catch (failure: Throwable) {
            Log.w(tag, "Auto overlay attach attempt=$attempt display=$displayId failed", failure)
            finish(1)
        }
    }

    val density: Float get() = context.resources.displayMetrics.density

    fun dp(value: Float): Int = (value * density + .5f).toInt()
    fun dp(value: Int): Int = dp(value.toFloat())

    fun isAttached(view: View) = attached.containsKey(view)

    fun show(
        view: View,
        bounds: Rect,
        touchable: Boolean = true,
        focusable: Boolean = false,
        watchOutside: Boolean = false,
        title: String = "Dextop Plasma",
    ) {
        val params = attached[view] ?: WindowManager.LayoutParams(
            bounds.width(),
            bounds.height(),
            windowType,
            0,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.title = title
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            fitInsetsTypes = 0
            setFitInsetsIgnoringVisibility(true)
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        }
        params.x = bounds.left
        params.y = bounds.top
        params.width = bounds.width().coerceAtLeast(1)
        params.height = bounds.height().coerceAtLeast(1)
        var flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        if (!focusable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        if (!touchable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        if (watchOutside) flags = flags or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        if (focusable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        params.flags = flags
        // Auto is composed in the display's accessibility overlay container,
        // above Samsung's system-decoration container (including DexTaskbarWindow).
        // WindowManager's accessibility window type alone is insufficient on One UI.
        if (android.os.Build.VERSION.SDK_INT >= 34 && displayOverlayService != null) {
            val existing = hosted[view]
            if (existing != null) {
                existing.host.relayout(params.width, params.height)
                android.view.SurfaceControl.Transaction().use { transaction ->
                    transaction.setPosition(existing.surface.surfaceControl, bounds.left.toFloat(), bounds.top.toFloat()).apply()
                }
            } else {
                @Suppress("DEPRECATION")
                val host = android.view.SurfaceControlViewHost(context, context.display!!, android.os.Binder())
                try {
                    host.setView(view, params.width, params.height)
                    val surface = requireNotNull(host.surfacePackage)
                    android.view.SurfaceControl.Transaction().use { transaction ->
                        transaction.setLayer(surface.surfaceControl, 1)
                            .setPosition(surface.surfaceControl, bounds.left.toFloat(), bounds.top.toFloat())
                            .setVisibility(surface.surfaceControl, true).apply()
                    }
                    val overlay = HostedOverlay(host, surface)
                    hosted[view] = overlay
                    // Display creation and WMS's accessibility-overlay container are asynchronous.
                    attachHandler.post { placeHosted(view, overlay) }
                } catch (failure: Throwable) {
                    host.release()
                    throw failure
                }
            }
            attached[view] = params
            return
        }
        runCatching {
            if (attached.containsKey(view)) {
                windowManager.updateViewLayout(view, params)
            } else {
                windowManager.addView(view, params)
                attached[view] = params
            }
        }.onFailure { Log.w(tag, "overlay ${params.title} update failed", it) }
    }

    fun hide(view: View?) {
        if (view == null || attached.remove(view) == null) return
        hosted.remove(view)?.let { overlay ->
            android.view.SurfaceControl.Transaction().use { it.reparent(overlay.surface.surfaceControl, null).apply() }
            overlay.host.release()
            overlay.surface.release()
            return
        }
        runCatching { windowManager.removeViewImmediate(view) }
            .onFailure { Log.w(tag, "overlay removal failed", it) }
    }

    /** Re-adds a window so it stacks above every other Plasma overlay. */
    fun raise(view: View) {
        val params = attached[view] ?: return
        hosted[view]?.let { overlay ->
            android.view.SurfaceControl.Transaction().use { it.setLayer(overlay.surface.surfaceControl, 1).apply() }
            return
        }
        runCatching {
            windowManager.removeViewImmediate(view)
            windowManager.addView(view, params)
        }
    }

    fun bounds(view: View): Rect? = attached[view]?.let { Rect(it.x, it.y, it.x + it.width, it.y + it.height) }

    fun clear() {
        attached.keys.toList().forEach(::hide)
    }
}

/** A launchable application shown by Kickoff, KRunner and the task manager. */
internal data class PlasmaApp(
    val packageName: String,
    val label: String,
    val icon: Drawable,
    val category: Int,
    val startupWMClass: String = "",
)

internal class PlasmaAppRepository(private val context: Context) {
    @Volatile var apps: List<PlasmaApp> = emptyList()
        private set
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "DextopPlasmaApps") }
    private val iconCache = HashMap<String, Drawable>()

    fun refresh(done: () -> Unit) {
        executor.execute {
            val manager = context.packageManager
            val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val loaded = manager.queryIntentActivities(query, PackageManager.MATCH_ALL)
                .asSequence()
                .filter { it.activityInfo.packageName != context.packageName }
                .distinctBy { it.activityInfo.packageName }
                .map {
                    PlasmaApp(
                        it.activityInfo.packageName,
                        it.loadLabel(manager).toString(),
                        it.loadIcon(manager),
                        it.activityInfo.applicationInfo.category,
                    )
                }
                .sortedBy { it.label.lowercase() }
                .toList()
            synchronized(iconCache) { loaded.forEach { iconCache[it.packageName] = it.icon } }
            apps = loaded
            android.os.Handler(android.os.Looper.getMainLooper()).post(done)
        }
    }

    fun find(packageName: String): PlasmaApp? = apps.firstOrNull { it.packageName == packageName }

    fun icon(packageName: String): Drawable? = synchronized(iconCache) {
        iconCache[packageName] ?: runCatching {
            context.packageManager.getApplicationIcon(packageName)
        }.getOrNull()?.also { iconCache[packageName] = it }
    }

    fun label(packageName: String): String = find(packageName)?.label ?: runCatching {
        val manager = context.packageManager
        manager.getApplicationLabel(manager.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    fun shutdown() = executor.shutdownNow()

    companion object {
        /** Kickoff sidebar categories derived from ApplicationInfo.category. */
        val categories = listOf(
            ApplicationInfo.CATEGORY_GAME,
            ApplicationInfo.CATEGORY_AUDIO,
            ApplicationInfo.CATEGORY_VIDEO,
            ApplicationInfo.CATEGORY_IMAGE,
            ApplicationInfo.CATEGORY_SOCIAL,
            ApplicationInfo.CATEGORY_NEWS,
            ApplicationInfo.CATEGORY_MAPS,
            ApplicationInfo.CATEGORY_PRODUCTIVITY,
            ApplicationInfo.CATEGORY_ACCESSIBILITY,
            ApplicationInfo.CATEGORY_UNDEFINED,
        )
    }
}

internal fun roundedBackground(color: Int, radius: Float, strokeColor: Int = 0, strokeWidth: Int = 0) =
    GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
        if (strokeWidth > 0) setStroke(strokeWidth, strokeColor)
    }

internal fun plasmaLabel(context: Context, text: String, color: Int, sizeSp: Float, bold: Boolean = false) =
    TextView(context).apply {
        this.text = text
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        typeface = if (bold) PlasmaTheme.sansMedium else PlasmaTheme.sans
        isSingleLine = true
        ellipsize = android.text.TextUtils.TruncateAt.END
        includeFontPadding = false
    }

/** Draws a drawable centred inside a rectangle while preserving aspect ratio. */
internal fun Canvas.drawIcon(icon: Drawable?, box: RectF, alpha: Int = 255) {
    icon ?: return
    val size = minOf(box.width(), box.height())
    val left = box.centerX() - size / 2f
    val top = box.centerY() - size / 2f
    icon.setBounds(left.toInt(), top.toInt(), (left + size).toInt(), (top + size).toInt())
    icon.alpha = alpha
    icon.draw(this)
    icon.alpha = 255
}

internal fun Paint.textWidth(text: String): Float = measureText(text)

internal fun View.hoverAware(onHover: (Boolean) -> Unit) {
    setOnHoverListener { _, event ->
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_HOVER_ENTER -> onHover(true)
            android.view.MotionEvent.ACTION_HOVER_EXIT -> onHover(false)
        }
        false
    }
}
