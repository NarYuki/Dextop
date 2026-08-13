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
    fun requestDisplay(width: Int, height: Int, density: Int, secure: Boolean, decorations: Boolean)
    fun findCreatedDisplay(previousIds: Set<Int>): Display?
    fun clearRequest()
}

internal interface MirrorAttachBackend {
    val id: String
    fun isSupported(): Boolean
    fun attach(request: MirrorAttachRequest): MirrorAttachment
}

internal data class MirrorAttachRequest(
    val displayId: Int,
    val host: SurfaceView,
    val hostWidth: Int,
    val hostHeight: Int,
    val contentWidth: Int,
    val contentHeight: Int
)

internal interface MirrorAttachment {
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
    private val attachBackends: Map<String, MirrorAttachBackend> by lazy {
        listOf(
            WindowManagerMirrorBackend(privilegedAccess),
            SurfaceControlMirrorBackend(),
            VirtualDisplayMirrorBackend(privilegedAccess)
        )
            .associateBy { it.id }
    }

    override fun currentDisplayIds(): Set<Int> = displayManager.displays.mapTo(mutableSetOf()) { it.displayId }

    override fun requestDisplay(width: Int, height: Int, density: Int, secure: Boolean, decorations: Boolean) {
        val flags = buildList {
            if (secure) add("secure")
            if (decorations) add("should_show_system_decorations")
        }.joinToString(separator = ",", prefix = if (secure || decorations) "," else "")
        val requested = "${width}x$height/$density$flags"
        check(Settings.Global.putString(resolver, DISPLAY_SPECIFICATION, requested)) { "Unable to request overlay display" }
        check(Settings.Global.getString(resolver, DISPLAY_SPECIFICATION) == requested) { "Overlay display request rejected" }
        OperationLog.i(context, "DisplayBackend", "created request strategy=overlay_settings spec=$requested")
    }

    override fun clearRequest() {
        check(Settings.Global.putString(resolver, DISPLAY_SPECIFICATION, "")) { "Unable to clear overlay display" }
    }

    override fun findCreatedDisplay(previousIds: Set<Int>): Display? = displayManager.displays.firstOrNull {
        it.displayId != Display.DEFAULT_DISPLAY && it.displayId !in previousIds &&
            runCatching { Display::class.java.getMethod("getType").invoke(it) as Int == 4 }.getOrDefault(false)
    }

    fun attach(
        displayId: Int,
        host: SurfaceView,
        hostWidth: Int,
        hostHeight: Int,
        contentWidth: Int,
        contentHeight: Int,
        strategyOverride: String? = null
    ): List<StrategyAttempt> {
        releaseLayer()
        val attempts = mutableListOf<StrategyAttempt>()
        val request = MirrorAttachRequest(displayId, host, hostWidth, hostHeight, contentWidth, contentHeight)
        val strategies = strategyOverride?.let(::listOf) ?: environment.mirrorStrategies
        for (id in strategies) {
            val backend = attachBackends[id] ?: continue
            if (!backend.isSupported()) {
                attempts += StrategyAttempt(id, false, "API unavailable")
                continue
            }
            val result = runCatching { backend.attach(request) }
            if (result.isSuccess) {
                attachment = result.getOrThrow()
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

    fun releaseLayer() {
        runCatching { attachment?.release() }
        attachment = null
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
    val parent = SurfaceView::class.java.getMethod("getSurfaceControl").invoke(request.host) as SurfaceControl
    val transaction = SurfaceControl.Transaction().reparent(layer, parent).setLayer(layer, 1)
    SurfaceControl.Transaction::class.java.getMethod(
        "setMatrix", SurfaceControl::class.java, Float::class.javaPrimitiveType,
        Float::class.javaPrimitiveType, Float::class.javaPrimitiveType, Float::class.javaPrimitiveType
    ).invoke(
        transaction, layer,
        request.hostWidth.toFloat() / request.contentWidth, 0f,
        0f, request.hostHeight.toFloat() / request.contentHeight
    )
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
private class VirtualDisplayMirrorBackend(private val privilegedAccess: PrivilegedAccess) : MirrorAttachBackend {
    override val id = "virtual_display"

    private val platform by lazy { VirtualDisplayPlatform.inspect() }

    override fun isSupported(): Boolean = runCatching { platform }.isSuccess

    override fun attach(request: MirrorAttachRequest): MirrorAttachment {
        val surface = request.host.holder.surface
        check(surface.isValid) { "The destination surface is unavailable" }
        val service = privilegedAccess.service("display", VirtualDisplayPlatform.MANAGER_INTERFACE)
        return platform.open(service, request, surface)
    }
}

private class VirtualDisplayPlatform private constructor(
    private val configurationType: Class<*>,
    private val builderType: Class<*>,
    private val callbackType: Class<*>,
    private val createOperation: Method,
    private val releaseOperation: Method
) {
    fun open(service: Any, request: MirrorAttachRequest, surface: Surface): MirrorAttachment {
        val descriptor = createDescriptor(request, surface)
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
        return ManagedVirtualDisplay(service, releaseOperation, callback)
    }

    private fun createDescriptor(request: MirrorAttachRequest, surface: Surface): Any {
        val constructor = builderType.constructors.singleOrNull { candidate ->
            candidate.parameterTypes.contentEquals(
                arrayOf(String::class.java, Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            )
        } ?: error("No compatible display configuration constructor")
        val builder = constructor.newInstance(
            "DextopSurface-${request.displayId}", request.hostWidth, request.hostHeight, 160
        )
        val properties = listOf(
            BuilderProperty("setSurface", Surface::class.java, surface),
            BuilderProperty("setDisplayIdToMirror", Int::class.javaPrimitiveType!!, request.displayId),
            BuilderProperty("setFlags", Int::class.javaPrimitiveType!!, 1 or 16 or 1024)
        )
        properties.forEach { property ->
            builderType.getMethod(property.name, property.type).invoke(builder, property.value)
        }
        return builderType.getMethod("build").invoke(builder)
    }

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
            return VirtualDisplayPlatform(configuration, builder, callback, creation, release)
        }
    }
}

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
    private val callback: Any
) : MirrorAttachment {
    private var released = false

    override fun release() {
        if (released) return
        released = true
        releaseOperation.invoke(service, callback)
    }
}
