package moe.n4tsu.dextop

import android.content.ContentResolver
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Binder
import android.os.IBinder
import android.provider.Settings
import android.view.Display
import android.view.Surface
import android.view.SurfaceControl
import android.view.SurfaceView
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

internal interface VirtualDisplayBackend {
    fun currentDisplayIds(): Set<Int>
    fun overlayDisplayIds(): Set<Int>
    fun requestDisplay(width: Int, height: Int, density: Int, secure: Boolean, decorations: Boolean)
    fun findCreatedDisplay(previousIds: Set<Int>, excludedIds: Set<Int> = emptySet()): Display?
    fun clearRequest()
}

internal interface MirrorAttachBackend {
    val id: String
    fun isSupported(): Boolean
    fun attach(request: MirrorAttachRequest): MirrorAttachment
}

internal data class MirrorAttachRequest(
    val displayId: Int,
    val host: SurfaceView?,
    val destinationSurface: Surface,
    val hostWidth: Int,
    val hostHeight: Int,
    val contentWidth: Int,
    val contentHeight: Int,
    val contentDensity: Int
)

internal interface MirrorAttachment {
    fun update(request: MirrorAttachRequest): Boolean = false
    fun release()
}

internal class DisplayMirrorBackend(
    private val context: Context,
    private val resolver: ContentResolver,
    private val displayManager: DisplayManager,
    private val privilegedAccess: PrivilegedAccess,
    private val environment: DesktopEnvironment
) : VirtualDisplayBackend {
    private var attachment: MirrorAttachment? = null
    var activeStrategy: String? = null
        private set
    private val attachBackends: Map<String, MirrorAttachBackend> by lazy {
        listOf(
            WindowManagerMirrorBackend(privilegedAccess),
            SurfaceControlMirrorBackend(),
            VirtualDisplayMirrorBackend(context, privilegedAccess)
        )
            .associateBy { it.id }
    }

    override fun currentDisplayIds(): Set<Int> = displayManager.displays.mapTo(mutableSetOf()) { it.displayId }

    override fun overlayDisplayIds(): Set<Int> = displayManager.displays
        .filter { display ->
            runCatching {
                Display::class.java.getMethod("getType").invoke(display) as Int == 4
            }.getOrDefault(false)
        }
        .mapTo(mutableSetOf()) { it.displayId }

    override fun requestDisplay(width: Int, height: Int, density: Int, secure: Boolean, decorations: Boolean) {
        requestDisplay(width, height, density, secure, decorations, emptySet(), false)
    }

    fun requestDisplay(
        width: Int,
        height: Int,
        density: Int,
        secure: Boolean,
        decorations: Boolean,
        preserveSpecs: Set<String>,
        ownContentOnly: Boolean = false,
    ) {
        val flags = buildList {
            if (secure) add("secure")
            if (decorations) add("should_show_system_decorations")
            // Plasma supplies its own shell and must not ask the framework or
            // Samsung launcher to populate the display.  This is the overlay
            // display equivalent of YoukiDEX's independently-owned UI layer.
            if (ownContentOnly) add("own_content_only")
        }.joinToString(separator = ",", prefix = if (secure || decorations || ownContentOnly) "," else "")
        val spec = "${width}x$height/$density$flags"
        val current = Settings.Global.getString(resolver, DISPLAY_SPECIFICATION).orEmpty()
        // A phone and Auto session can legitimately request the same logical
        // size. Preserve one entry per independently-owned Auto request,
        // rather than preserving every identical string (which would leave a
        // stopped phone display alive forever).
        val preserveQuota = preserveSpecs.associateWith { 1 }.toMutableMap()
        val preserved = current.split(';').filter { entry ->
            val remaining = preserveQuota[entry] ?: return@filter false
            if (remaining <= 0) return@filter false
            preserveQuota[entry] = remaining - 1
            true
        }
        val requested = (preserved + spec).joinToString(";")
        check(Settings.Global.putString(resolver, DISPLAY_SPECIFICATION, requested)) { "Unable to request overlay display" }
        check(Settings.Global.getString(resolver, DISPLAY_SPECIFICATION) == requested) { "Overlay display request rejected" }
        OperationLog.i(context, "DisplayBackend", "created request strategy=overlay_settings spec=$requested")
    }

    override fun clearRequest() {
        clearRequestPreserving(emptySet())
    }

    fun clearRequestPreserving(preserveSpecs: Set<String>) {
        val current = Settings.Global.getString(resolver, DISPLAY_SPECIFICATION).orEmpty()
        val preserveQuota = preserveSpecs.associateWith { 1 }.toMutableMap()
        val preserved = current.split(';').filter { entry ->
            val remaining = preserveQuota[entry] ?: return@filter false
            if (remaining <= 0) return@filter false
            preserveQuota[entry] = remaining - 1
            true
        }
        check(Settings.Global.putString(resolver, DISPLAY_SPECIFICATION, preserved.joinToString(";"))) {
            "Unable to clear overlay display"
        }
    }

    override fun findCreatedDisplay(previousIds: Set<Int>, excludedIds: Set<Int>): Display? = displayManager.displays.firstOrNull {
        it.displayId != Display.DEFAULT_DISPLAY && it.displayId !in previousIds &&
            it.displayId !in excludedIds &&
            runCatching { Display::class.java.getMethod("getType").invoke(it) as Int == 4 }.getOrDefault(false)
    }

    fun attach(
        displayId: Int,
        host: SurfaceView,
        hostWidth: Int,
        hostHeight: Int,
        contentWidth: Int,
        contentHeight: Int,
        contentDensity: Int,
        strategyOverride: String? = null
    ): List<StrategyAttempt> {
        val request = MirrorAttachRequest(
            displayId, host, host.holder.surface, hostWidth, hostHeight,
            contentWidth, contentHeight, contentDensity
        )
        OperationLog.i(
            context,
            "DisplayBackend",
            "mirror attach host=${hostWidth}x$hostHeight content=${contentWidth}x$contentHeight/$contentDensity"
        )
        val strategies = strategyOverride?.let(::listOf) ?: environment.mirrorStrategies
        // Resizing/rebinding an existing recording VirtualDisplay keeps its
        // display id stable. Releasing it on every fold or laptop-pane change
        // leaves stale AOSP desktop desks behind until Shell refuses launches.
        if (activeStrategy == "virtual_display" && "virtual_display" in strategies &&
            attachment?.update(request) == true) {
            OperationLog.i(
                context,
                "DisplayBackend",
                "mirror strategy=virtual_display updated in place " +
                    "host=${hostWidth}x$hostHeight content=${contentWidth}x$contentHeight/$contentDensity"
            )
            return listOf(StrategyAttempt("virtual_display", true, "updated in place"))
        }
        releaseLayer()
        val attempts = mutableListOf<StrategyAttempt>()
        for (id in strategies) {
            val backend = attachBackends[id] ?: continue
            if (!backend.isSupported()) {
                attempts += StrategyAttempt(id, false, "API unavailable")
                continue
            }
            val result = runCatching { backend.attach(request) }
            if (result.isSuccess) {
                attachment = result.getOrThrow()
                activeStrategy = id
                attempts += StrategyAttempt(id, true, "attached")
                OperationLog.i(context, "DisplayBackend", "mirror strategy=$id success=true")
                return attempts
            }
            val error = result.exceptionOrNull()!!
            attempts += StrategyAttempt(id, false, error.message.orEmpty())
            OperationLog.w(context, "DisplayBackend", "mirror strategy=$id failed", error)
        }
        error("No compatible mirror backend: ${attempts.joinToString { "${it.strategy}=${it.detail}" }}")
    }

    fun attachSurface(
        displayId: Int,
        surface: Surface,
        hostWidth: Int,
        hostHeight: Int,
        contentWidth: Int,
        contentHeight: Int,
        contentDensity: Int
    ) {
        val request = MirrorAttachRequest(
            displayId, null, surface, hostWidth, hostHeight,
            contentWidth, contentHeight, contentDensity
        )
        releaseLayer()
        val backend = attachBackends.getValue("virtual_display")
        check(backend.isSupported()) { "Virtual display mirroring is unavailable" }
        attachment = backend.attach(request)
        activeStrategy = backend.id
        OperationLog.i(context, "DisplayBackend", "remote Surface mirror attached")
    }

    fun releaseLayer() {
        runCatching { attachment?.release() }
            .onFailure {
                OperationLog.w(context, "DisplayBackend", "mirror attachment release failed", it)
            }
        attachment = null
        activeStrategy = null
    }

    companion object { const val DISPLAY_SPECIFICATION = "overlay_display_devices" }
}

private class WindowManagerMirrorBackend(private val privilegedAccess: PrivilegedAccess) : MirrorAttachBackend {
    override val id = "window_manager"
    override fun isSupported() = runCatching {
        Class.forName("android.view.IWindowManager").getMethod("mirrorDisplay", Int::class.javaPrimitiveType, SurfaceControl::class.java)
    }.isSuccess
    override fun attach(request: MirrorAttachRequest): MirrorAttachment {
        val layer = SurfaceControl::class.java.getConstructor().newInstance()
        val service = privilegedAccess.service("window", "android.view.IWindowManager")
        val mirrored = Class.forName("android.view.IWindowManager")
            .getMethod("mirrorDisplay", Int::class.javaPrimitiveType, SurfaceControl::class.java)
            .invoke(service, request.displayId, layer) as Boolean
        check(mirrored) { "IWindowManager rejected mirror" }
        return attachLayer(layer, request)
    }
}

private class SurfaceControlMirrorBackend : MirrorAttachBackend {
    override val id = "surface_control"
    override fun isSupported() = runCatching {
        SurfaceControl::class.java.getDeclaredMethod("mirrorDisplay", Int::class.javaPrimitiveType)
    }.isSuccess
    override fun attach(request: MirrorAttachRequest): MirrorAttachment {
        val layer = SurfaceControl::class.java
        .getDeclaredMethod("mirrorDisplay", Int::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(null, request.displayId) as SurfaceControl
        return attachLayer(layer, request)
    }
}

private fun attachLayer(layer: SurfaceControl, request: MirrorAttachRequest): MirrorAttachment {
    val host = checkNotNull(request.host) { "A SurfaceView host is required for layer mirroring" }
    val parent = SurfaceView::class.java.getMethod("getSurfaceControl").invoke(host) as SurfaceControl
    val transaction = SurfaceControl.Transaction().reparent(layer, parent).setLayer(layer, 1)
    // Preserve the Auto desktop aspect ratio and keep every edge visible.
    // The Auto tile can be nearly square while the phone is ultrawide, so
    // center-cropping would cut away a large part of the desktop.
    val scale = minOf(
        request.hostWidth.toFloat() / request.contentWidth,
        request.hostHeight.toFloat() / request.contentHeight,
    )
    val offsetX = (request.hostWidth - request.contentWidth * scale) / 2f
    val offsetY = (request.hostHeight - request.contentHeight * scale) / 2f
    SurfaceControl.Transaction::class.java.getMethod(
        "setMatrix", SurfaceControl::class.java, Float::class.javaPrimitiveType,
        Float::class.javaPrimitiveType, Float::class.javaPrimitiveType, Float::class.javaPrimitiveType
    ).invoke(
        transaction, layer,
        scale, 0f, 0f, scale
    )
    transaction.setPosition(layer, offsetX, offsetY)
    SurfaceControl.Transaction::class.java.getMethod(
        "setWindowCrop", SurfaceControl::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType
    ).invoke(transaction, layer, request.contentWidth, request.contentHeight)
    SurfaceControl.Transaction::class.java.getMethod("show", SurfaceControl::class.java).invoke(transaction, layer)
    transaction.apply()
    return object : MirrorAttachment {
        override fun release() {
            runCatching {
                val removal = SurfaceControl.Transaction()
                SurfaceControl.Transaction::class.java.getMethod("remove", SurfaceControl::class.java)
                    .invoke(removal, layer)
                removal.apply()
            }
            layer.release()
        }
    }
}

/**
 * Mirrors a display through DisplayManager's virtual-display pipeline. This is
 * deliberately independent from the SurfaceControl implementations above so a
 * vendor can select it without changing working devices.
 */
private class VirtualDisplayMirrorBackend(
    private val context: Context,
    private val privilegedAccess: PrivilegedAccess,
) : MirrorAttachBackend {
    override val id = "virtual_display"

    private val platform by lazy { VirtualDisplayPlatform.inspect() }

    override fun isSupported(): Boolean = runCatching { platform }.isSuccess

    override fun attach(request: MirrorAttachRequest): MirrorAttachment {
        val surface = request.destinationSurface
        check(surface.isValid) { "The destination surface is unavailable" }
        val service = privilegedAccess.service("display", VirtualDisplayPlatform.MANAGER_INTERFACE)
        return platform.open(
            service,
            request,
            surface,
            requestMaximumRefreshRate = DisplayEnvironmentSettings(context)
                .maximumMirrorRefreshRateEnabled(),
        )
    }
}

internal data class OwnedVirtualDisplay(
    val displayId: Int,
    val attachment: MirrorAttachment
) {
    /**
     * Updates the real Auto-owned virtual display without going through a
     * phone SurfaceView.  This is used when CARDEX raises its render scale:
     * the destination panel is unchanged but the desktop's logical buffer
     * must grow so desktop controls become smaller.
     */
    fun resize(surface: Surface, width: Int, height: Int, density: Int): Boolean =
        attachment.update(
            MirrorAttachRequest(
                displayId = displayId,
                host = null,
                destinationSurface = surface,
                hostWidth = width,
                hostHeight = height,
                contentWidth = width,
                contentHeight = height,
                contentDensity = density
            )
        )
}

internal class VirtualDisplayPlatform private constructor(
    private val configurationType: Class<*>,
    private val builderType: Class<*>,
    private val callbackType: Class<*>,
    private val createOperation: Method,
    private val releaseOperation: Method,
    private val resizeOperation: Method?,
    private val surfaceOperation: Method?
) {
    fun open(
        service: Any,
        request: MirrorAttachRequest,
        surface: Surface,
        requestMaximumRefreshRate: Boolean = false,
    ): MirrorAttachment {
        val descriptor = createDescriptor(request, surface, requestMaximumRefreshRate)
        val callback = createCallback()
        val bindings = mapOf<Class<*>, Any>(
            configurationType to descriptor,
            callbackType to callback
        )
        val arguments = createOperation.parameterTypes.map { parameter ->
            bindings.entries.firstOrNull { parameter.isAssignableFrom(it.key) }?.value
                ?: defaultArgument(parameter)
        }.toTypedArray()
        val displayId = (createOperation.invoke(service, *arguments) as? Number)?.toInt() ?: -1
        check(displayId >= 0) { "The virtual display request was declined" }
        return ManagedVirtualDisplay(
            service,
            releaseOperation,
            resizeOperation,
            surfaceOperation,
            callback,
            request.hostWidth.takeIf { it > 0 } ?: request.contentWidth,
            request.hostHeight.takeIf { it > 0 } ?: request.contentHeight,
            request.contentWidth,
            request.contentHeight,
            request.contentDensity
        )
    }

    /**
     * Creates a real, app-owned desktop display whose buffer is written
     * directly to the CARDEX surface. Unlike OverlayDisplayAdapter this does
     * not create a draggable "Overlay #" window on the phone display.
     */
    fun openOwned(
        context: Context,
        service: Any,
        surface: Surface,
        width: Int,
        height: Int,
        density: Int,
        decorations: Boolean,
        configureBeforePublish: (Int) -> Unit = {},
    ): OwnedVirtualDisplay {
        check(surface.isValid) { "The Dextop Car Companion destination surface is unavailable" }
        val constructor = builderType.constructors.single { candidate ->
            candidate.parameterTypes.contentEquals(
                arrayOf(String::class.java, Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            )
        }
        val builder = constructor.newInstance("Dextop Auto", width, height, density)
        builderType.getMethod("setSurface", Surface::class.java).invoke(builder, surface)
        // Electron-style direct display. Prefer the fully capable public,
        // trusted and decorated configuration, then progressively remove
        // optional capabilities for vendor firmware that rejects a flag.
        val flagLadder = if (decorations) {
            intArrayOf(
                FLAG_PUBLIC or FLAG_PRESENTATION or FLAG_OWN_CONTENT_ONLY or
                    FLAG_SUPPORTS_TOUCH or FLAG_SHOW_SYSTEM_DECORATIONS or FLAG_TRUSTED,
                FLAG_PUBLIC or FLAG_PRESENTATION or FLAG_OWN_CONTENT_ONLY or
                    FLAG_SHOW_SYSTEM_DECORATIONS or FLAG_TRUSTED,
                FLAG_PUBLIC or FLAG_PRESENTATION or FLAG_OWN_CONTENT_ONLY or FLAG_TRUSTED,
                FLAG_PUBLIC or FLAG_PRESENTATION or FLAG_OWN_CONTENT_ONLY,
            )
        } else {
            intArrayOf(2 or 8 or 64 or 1024)
        }
        var lastFailure: Throwable? = null
        flagLadder.forEach { flags ->
            val callback = createCallback()
            try {
                builderType.getMethod("setFlags", Int::class.javaPrimitiveType).invoke(builder, flags)
                val descriptor = builderType.getMethod("build").invoke(builder)
                val bindings = mapOf<Class<*>, Any>(configurationType to descriptor, callbackType to callback)
                val arguments = createOperation.parameterTypes.map { parameter ->
                    bindings.entries.firstOrNull { parameter.isAssignableFrom(it.key) }?.value
                        ?: defaultArgument(parameter)
                }.toTypedArray()
                val displayId = (createOperation.invoke(service, *arguments) as? Number)?.toInt() ?: -1
                if (displayId >= 0) {
                    try {
                        // This deliberately happens before openOwned returns.
                        // DeX observes a new display immediately; configuring
                        // freeform later leaves a race where its first task is
                        // permanently attached as fullscreen.
                        configureBeforePublish(displayId)
                    } catch (error: Throwable) {
                        runCatching { releaseOperation.invoke(service, callback) }
                        throw error
                    }
                    OperationLog.i(context, "CarCompanion", "direct display created flags=0x${flags.toString(16)}")
                    return OwnedVirtualDisplay(
                        displayId,
                        ManagedVirtualDisplay(
                            service, releaseOperation, resizeOperation, surfaceOperation, callback,
                            width, height, width, height, density
                        )
                    )
                }
            } catch (error: Throwable) {
                lastFailure = error
                OperationLog.w(context, "CarCompanion", "direct display flags rejected 0x${flags.toString(16)}", error)
            }
        }
        throw IllegalStateException("The direct Auto display request was declined", lastFailure)
    }

    private fun createDescriptor(
        request: MirrorAttachRequest,
        surface: Surface,
        requestMaximumRefreshRate: Boolean,
    ): Any {
        val constructor = builderType.constructors.singleOrNull { candidate ->
            candidate.parameterTypes.contentEquals(
                arrayOf(String::class.java, Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            )
        } ?: error("No compatible display configuration constructor")
        // VirtualDisplay's buffer is written directly into the host
        // SurfaceView.  Using the logical overlay size here leaves Android's
        // Surface pipeline free to center/crop a custom profile (for example
        // a 16:9 profile in a 4:3 DeX host), which is what caused the small or
        // cropped mirror.  Keep the overlay's logical metrics in
        // MirrorAttachRequest.contentWidth/contentHeight, but size the output
        // buffer to the measured host surface so the mirror always fills it.
        val outputWidth = request.hostWidth.takeIf { it > 0 } ?: request.contentWidth
        val outputHeight = request.hostHeight.takeIf { it > 0 } ?: request.contentHeight
        val builder = constructor.newInstance(
            "DextopSurface-${request.displayId}",
            outputWidth,
            outputHeight,
            request.contentDensity
        )
        val properties = listOf(
            BuilderProperty("setSurface", Surface::class.java, surface),
            BuilderProperty("setDisplayIdToMirror", Int::class.javaPrimitiveType!!, request.displayId),
            BuilderProperty("setFlags", Int::class.javaPrimitiveType!!, 1 or 16 or 1024)
        )
        properties.forEach { property ->
            builderType.getMethod(property.name, property.type).invoke(builder, property.value)
        }
        if (requestMaximumRefreshRate) {
            hostRefreshRate(request.host)?.let { refreshRate ->
                // Added in Android 14. Reflection preserves the existing
                // automatic-rate behaviour on older or vendor-modified builds.
                runCatching {
                    builderType.getMethod(
                        "setRequestedRefreshRate",
                        Float::class.javaPrimitiveType,
                    ).invoke(builder, refreshRate)
                }
            }
        }
        return builderType.getMethod("build").invoke(builder)
    }

    private fun hostRefreshRate(host: SurfaceView?): Float? = runCatching {
        val display = host?.display ?: return null
        val mode = display.mode
        chooseMirrorRefreshRate(
            display.supportedModes
            .filter {
                it.physicalWidth == mode.physicalWidth &&
                    it.physicalHeight == mode.physicalHeight
            }
            .map { it.refreshRate }
        )
    }.getOrNull()

    private fun createCallback(): Any {
        val handler = DisplayLifecycleCallback(Binder())
        return Proxy.newProxyInstance(callbackType.classLoader, arrayOf(callbackType), handler)
    }

    private fun defaultArgument(type: Class<*>): Any? = when {
        type == String::class.java -> "com.android.shell"
        IBinder::class.java.isAssignableFrom(type) -> null
        !type.isPrimitive -> null
        type == Boolean::class.javaPrimitiveType -> false
        type == Byte::class.javaPrimitiveType -> 0.toByte()
        type == Char::class.javaPrimitiveType -> '\u0000'
        type == Short::class.javaPrimitiveType -> 0.toShort()
        type == Int::class.javaPrimitiveType -> 0
        type == Long::class.javaPrimitiveType -> 0L
        type == Float::class.javaPrimitiveType -> 0f
        type == Double::class.javaPrimitiveType -> 0.0
        else -> null
    }

    private data class BuilderProperty(val name: String, val type: Class<*>, val value: Any)

    companion object {
        const val MANAGER_INTERFACE = "android.hardware.display.IDisplayManager"
        private const val FLAG_PUBLIC = 1
        private const val FLAG_PRESENTATION = 1 shl 1
        private const val FLAG_OWN_CONTENT_ONLY = 1 shl 3
        private const val FLAG_SUPPORTS_TOUCH = 1 shl 6
        private const val FLAG_SHOW_SYSTEM_DECORATIONS = 1 shl 9
        private const val FLAG_TRUSTED = 1 shl 10

        fun inspect(): VirtualDisplayPlatform {
            val manager = Class.forName(MANAGER_INTERFACE)
            val configuration = Class.forName("android.hardware.display.VirtualDisplayConfig")
            val builder = Class.forName("android.hardware.display.VirtualDisplayConfig\$Builder")
            val callback = Class.forName("android.hardware.display.IVirtualDisplayCallback")
            val creation = manager.methods
                .filter { it.returnType == Int::class.javaPrimitiveType }
                .singleOrNull { method ->
                    method.name == "createVirtualDisplay" &&
                        method.parameterTypes.any { configuration.isAssignableFrom(it) } &&
                        method.parameterTypes.any { callback.isAssignableFrom(it) }
                } ?: error("No compatible display creation operation")
            val release = manager.methods.singleOrNull { method ->
                method.name == "releaseVirtualDisplay" &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0].isAssignableFrom(callback)
            } ?: error("No compatible display release operation")
            val resize = manager.methods.firstOrNull { method ->
                method.name == "resizeVirtualDisplay" &&
                    method.parameterTypes.firstOrNull()?.isAssignableFrom(callback) == true
            }
            val setSurface = manager.methods.firstOrNull { method ->
                method.name == "setVirtualDisplaySurface" &&
                    method.parameterTypes.firstOrNull()?.isAssignableFrom(callback) == true
            }
            return VirtualDisplayPlatform(
                configuration, builder, callback, creation, release, resize, setSurface
            )
        }
    }
}

/** Highest finite positive refresh rate offered by Android for the host mode. */
internal fun chooseMirrorRefreshRate(rates: List<Float>): Float? =
    rates.filter { it.isFinite() && it > 0f }.maxOrNull()

private class DisplayLifecycleCallback(private val binder: Binder) : InvocationHandler {
    override fun invoke(proxy: Any, method: Method, arguments: Array<out Any?>?): Any? = when {
        method.name == "asBinder" -> binder
        method.name == "toString" -> "DextopDisplayLifecycle"
        method.name == "hashCode" -> System.identityHashCode(proxy)
        method.name == "equals" -> proxy === arguments?.firstOrNull()
        method.returnType == Boolean::class.javaPrimitiveType -> false
        method.returnType == Int::class.javaPrimitiveType -> 0
        method.returnType == Long::class.javaPrimitiveType -> 0L
        else -> null
    }
}

private class ManagedVirtualDisplay(
    private val service: Any,
    private val releaseOperation: Method,
    private val resizeOperation: Method?,
    private val surfaceOperation: Method?,
    private val callback: Any,
    initialOutputWidth: Int,
    initialOutputHeight: Int,
    initialContentWidth: Int,
    initialContentHeight: Int,
    initialContentDensity: Int
) : MirrorAttachment {
    private var released = false
    private var outputWidth = initialOutputWidth
    private var outputHeight = initialOutputHeight
    private var contentWidth = initialContentWidth
    private var contentHeight = initialContentHeight
    private var contentDensity = initialContentDensity

    override fun update(request: MirrorAttachRequest): Boolean = runCatching {
        val resize = resizeOperation ?: return false
        val setSurface = surfaceOperation ?: return false
        val nextWidth = request.hostWidth.takeIf { it > 0 } ?: request.contentWidth
        val nextHeight = request.hostHeight.takeIf { it > 0 } ?: request.contentHeight
        val logicalSizeChanged = request.contentWidth != contentWidth ||
            request.contentHeight != contentHeight
        val densityChanged = request.contentDensity != contentDensity
        // The source overlay's logical size is part of the VirtualDisplay
        // configuration even when the destination Surface keeps the same
        // pixel dimensions. Without this resize, switching from a
        // letterboxed profile to a full-size profile leaves the old source
        // crop/scale and its black bars in place. A density-only edit is
        // intentionally excluded: WMS applies that metric and resizing the
        // recording for DPI alone can make Samsung DeX rebuild its taskbar.
        if (nextWidth != outputWidth || nextHeight != outputHeight || logicalSizeChanged) {
            val resizeArgs = resize.parameterTypes.mapIndexed { index, type ->
                when {
                    index == 0 -> callback
                    type == Int::class.javaPrimitiveType && index == 1 -> nextWidth
                    type == Int::class.javaPrimitiveType && index == 2 -> nextHeight
                    type == Int::class.javaPrimitiveType -> request.contentDensity
                    else -> null
                }
            }.toTypedArray()
            resize.invoke(service, *resizeArgs)
            outputWidth = nextWidth
            outputHeight = nextHeight
        }
        contentWidth = request.contentWidth
        contentHeight = request.contentHeight
        if (densityChanged) contentDensity = request.contentDensity
        setSurface.let { operation ->
            val args = operation.parameterTypes.mapIndexed { index, type ->
                when {
                    index == 0 -> callback
                    Surface::class.java.isAssignableFrom(type) -> request.destinationSurface
                    else -> null
                }
            }.toTypedArray()
            operation.invoke(service, *args)
        }
        true
    }.getOrDefault(false)

    override fun release() {
        if (released) return
        released = true
        releaseOperation.invoke(service, callback)
    }
}
