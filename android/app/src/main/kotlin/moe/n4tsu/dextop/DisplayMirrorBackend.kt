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
    val contentHeight: Int,
    val contentDensity: Int,
    /**
     * The overlay display may contain FLAG_SECURE windows (Secure Folder,
     * BiometricPrompt, payment apps, etc.).  The recording VirtualDisplay
     * must carry the same security contract or SurfaceFlinger blanks those
     * windows before they reach the host SurfaceView.
     */
    val secure: Boolean = false,
    /** Keep system-auth UI eligible on the recording display when requested. */
    val decorations: Boolean = false
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
            VirtualDisplayMirrorBackend(privilegedAccess, context)
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
        contentDensity: Int,
        secure: Boolean = false,
        decorations: Boolean = false,
        strategyOverride: String? = null
    ): List<StrategyAttempt> {
        val request = MirrorAttachRequest(
            displayId, host, hostWidth, hostHeight,
            contentWidth, contentHeight, contentDensity,
            secure = secure,
            decorations = decorations
        )
        val strategies = strategyOverride?.let(::listOf) ?: environment.mirrorStrategies
        // Resizing/rebinding an existing recording VirtualDisplay keeps its
        // display id stable. Releasing it on every fold or laptop-pane change
        // leaves stale AOSP desktop desks behind until Shell refuses launches.
        if (activeStrategy == "virtual_display" && "virtual_display" in strategies &&
            attachment?.update(request) == true) {
            OperationLog.i(context, "DisplayBackend", "mirror strategy=virtual_display updated in place")
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

    fun releaseLayer() {
        runCatching { attachment?.release() }
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
private class VirtualDisplayMirrorBackend(
    private val privilegedAccess: PrivilegedAccess,
    private val context: Context
) : MirrorAttachBackend {
    override val id = "virtual_display"

    private val platform by lazy { VirtualDisplayPlatform.inspect() }

    override fun isSupported(): Boolean = runCatching { platform }.isSuccess

    override fun attach(request: MirrorAttachRequest): MirrorAttachment {
        val surface = request.host.holder.surface
        check(surface.isValid) { "The destination surface is unavailable" }
        val service = privilegedAccess.service("display", VirtualDisplayPlatform.MANAGER_INTERFACE)
        OperationLog.i(
            context,
            "DisplaySecurity",
            "virtual mirror flags secure=${request.secure} trusted=true " +
                "canShowWithKeyguard=true decorations=${request.decorations}"
        )
        return runCatching {
            platform.open(service, request, surface)
        }.getOrElse { error ->
            // Some vendor display services reject the hidden SECURE flag even
            // when the caller is running through a privileged provider. Keep
            // the session usable in that case, while making the limitation
            // explicit in the operation log. Protected windows will remain
            // unavailable until the device grants secure-display access.
            if (request.secure && isSecurityFailure(error)) {
                OperationLog.w(
                    context,
                    "DisplayBackend",
                    "secure VirtualDisplay rejected; retrying non-secure mirror",
                    error
                )
                platform.open(service, request.copy(secure = false), surface)
            } else {
                throw error
            }
        }
    }

    private fun isSecurityFailure(error: Throwable): Boolean {
        var current: Throwable? = error
        repeat(4) {
            if (current is SecurityException) return true
            current = current?.cause
        }
        return false
    }
}

private class VirtualDisplayPlatform private constructor(
    private val configurationType: Class<*>,
    private val builderType: Class<*>,
    private val callbackType: Class<*>,
    private val createOperation: Method,
    private val releaseOperation: Method,
    private val resizeOperation: Method?,
    private val surfaceOperation: Method?
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
        return ManagedVirtualDisplay(
            service, releaseOperation, resizeOperation, surfaceOperation, callback
        )
    }

    private fun createDescriptor(request: MirrorAttachRequest, surface: Surface): Any {
        val constructor = builderType.constructors.singleOrNull { candidate ->
            candidate.parameterTypes.contentEquals(
                arrayOf(String::class.java, Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            )
        } ?: error("No compatible display configuration constructor")
        val builder = constructor.newInstance(
            "DextopSurface-${request.displayId}",
            request.contentWidth,
            request.contentHeight,
            request.contentDensity
        )
        // A protected window is blanked on a non-secure virtual display.  The
        // trusted/keyguard flags are also required for SystemUI biometric and
        // Secure Folder authentication surfaces to remain attached while the
        // target display is active. These are the platform constants kept
        // numeric because the trusted flags are hidden from the public SDK.
        val flags = VIRTUAL_DISPLAY_FLAG_PUBLIC or
            VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR or
            VIRTUAL_DISPLAY_FLAG_CAN_SHOW_WITH_INSECURE_KEYGUARD or
            VIRTUAL_DISPLAY_FLAG_TRUSTED or
            (if (request.secure) VIRTUAL_DISPLAY_FLAG_SECURE else 0) or
            (if (request.decorations) VIRTUAL_DISPLAY_FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS else 0)
        val properties = listOf(
            BuilderProperty("setSurface", Surface::class.java, surface),
            BuilderProperty("setDisplayIdToMirror", Int::class.javaPrimitiveType!!, request.displayId),
            BuilderProperty("setFlags", Int::class.javaPrimitiveType!!, flags)
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

        private const val VIRTUAL_DISPLAY_FLAG_PUBLIC = 1
        private const val VIRTUAL_DISPLAY_FLAG_SECURE = 4
        private const val VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR = 16
        private const val VIRTUAL_DISPLAY_FLAG_CAN_SHOW_WITH_INSECURE_KEYGUARD = 32
        private const val VIRTUAL_DISPLAY_FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS = 512
        private const val VIRTUAL_DISPLAY_FLAG_TRUSTED = 1024

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
    private val callback: Any
) : MirrorAttachment {
    private var released = false

    override fun update(request: MirrorAttachRequest): Boolean = runCatching {
        val resize = resizeOperation ?: return false
        val resizeArgs = resize.parameterTypes.mapIndexed { index, type ->
            when {
                index == 0 -> callback
                type == Int::class.javaPrimitiveType && index == 1 -> request.contentWidth
                type == Int::class.javaPrimitiveType && index == 2 -> request.contentHeight
                type == Int::class.javaPrimitiveType -> request.contentDensity
                else -> null
            }
        }.toTypedArray()
        resize.invoke(service, *resizeArgs)
        surfaceOperation?.let { operation ->
            val args = operation.parameterTypes.mapIndexed { index, type ->
                when {
                    index == 0 -> callback
                    Surface::class.java.isAssignableFrom(type) -> request.host.holder.surface
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
