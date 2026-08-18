package moe.n4tsu.dextop

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.InputDevice
import java.lang.reflect.Method

internal data class ExternalDisplayState(
    val connected: Boolean,
    val displayIds: List<Int>,
    val names: List<String>
)

internal class ExternalDisplayDetector(context: Context) {
    private val manager = context.getSystemService(DisplayManager::class.java)

    fun snapshot(): ExternalDisplayState {
        val displays = manager.displays.filter(::isPhysicalExternalDisplay)
        return ExternalDisplayState(
            connected = displays.isNotEmpty(),
            displayIds = displays.map { it.displayId },
            names = displays.map { it.name }
        )
    }

    private fun isPhysicalExternalDisplay(display: Display): Boolean {
        if (display.displayId == Display.DEFAULT_DISPLAY) return false
        val type = runCatching {
            Display::class.java.getMethod("getType").invoke(display) as Int
        }.getOrDefault(0)
        // HDMI and Wi-Fi displays are physical external destinations. A vendor
        // may report another non-overlay/non-virtual type, in which case a
        // physical DisplayAddress is accepted as the secondary signal.
        if (type == 2 || type == 3) return true
        if (type == 4 || type == 5) return false
        val address = runCatching {
            Display::class.java.getMethod("getAddress").invoke(display)
        }.getOrNull()
        return address?.javaClass?.name?.endsWith("DisplayAddress\$Physical") == true
    }
}

internal class PhysicalInputRouter(
    private val context: Context,
    private val privilegedAccess: PrivilegedAccess
) {
    private val preferences = context.getSharedPreferences("dextop_input_routing", Context.MODE_PRIVATE)
    private val service by lazy {
        privilegedAccess.service("input", "android.hardware.input.IInputManager")
    }
    private val methods by lazy { discoverOperations(service.javaClass) }
    private val routedKeys = linkedSetOf<String>()
    private var routedDisplayUniqueId: String? = null

    fun routeConnectedDevices(display: Display, mouse: Boolean = true, keyboard: Boolean = true): Int {
        clearStaleAssociations()
        val uniqueId = displayUniqueId(display) ?: return 0
        routedDisplayUniqueId = uniqueId
        eligibleDevices(mouse, keyboard).forEach { device -> route(device, uniqueId) }
        persist()
        return routedKeys.size
    }

    fun refresh(display: Display, mouse: Boolean = true, keyboard: Boolean = true): Int {
        val uniqueId = routedDisplayUniqueId ?: displayUniqueId(display) ?: return routedKeys.size
        val eligible = eligibleDevices(mouse, keyboard)
        eligible.forEach { device ->
            val key = associationKey(device) ?: return@forEach
            if (key !in routedKeys && associate(device, uniqueId)) routedKeys += key
        }
        val present = eligible.mapNotNull(::associationKey).toSet()
        routedKeys.filter { it !in present }.toList().forEach { key ->
            removeAssociation(key)
            routedKeys -= key
        }
        persist()
        return routedKeys.size
    }

    fun apply(display: Display, mouse: Boolean, keyboard: Boolean): Int {
        restore()
        return routeConnectedDevices(display, mouse, keyboard)
    }

    fun isMouseRouted(display: Display): Boolean = isTypeRouted(display, mouse = true)

    fun isKeyboardRouted(display: Display): Boolean = isTypeRouted(display, mouse = false)

    private fun isTypeRouted(display: Display, mouse: Boolean): Boolean {
        val devices = eligibleDevices(mouse = mouse, keyboard = !mouse)
        if (devices.isEmpty()) return false
        return devices.any { associatedDisplayId(it) == display.displayId }
    }

    private fun associatedDisplayId(device: InputDevice): Int? = runCatching {
        InputDevice::class.java.getMethod("getAssociatedDisplayId").invoke(device) as Int
    }.getOrNull()

    fun restore() {
        val keys = (routedKeys + preferences.getStringSet(KEY_ASSOCIATIONS, emptySet()).orEmpty()).toSet()
        keys.forEach(::removeAssociation)
        routedKeys.clear()
        routedDisplayUniqueId = null
        preferences.edit().clear().apply()
    }

    private fun clearStaleAssociations() {
        preferences.getStringSet(KEY_ASSOCIATIONS, emptySet()).orEmpty().forEach(::removeAssociation)
        preferences.edit().clear().commit()
    }

    private fun eligibleDevices(mouse: Boolean, keyboard: Boolean): List<InputDevice> = InputDevice.getDeviceIds().toList().mapNotNull(InputDevice::getDevice)
        .filter { device ->
            device.id >= 0 && device.isExternal && device.descriptor.isNotBlank() &&
                (mouse && device.sources and InputDevice.SOURCE_MOUSE == InputDevice.SOURCE_MOUSE ||
                    keyboard && device.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC)
        }

    private fun displayUniqueId(display: Display): String? = runCatching {
        Display::class.java.getMethod("getUniqueId").invoke(display) as? String
    }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun route(device: InputDevice, displayUniqueId: String) {
        val key = associationKey(device) ?: return
        if (associate(device, displayUniqueId)) routedKeys += key
    }

    private fun associationKey(device: InputDevice): String? {
        val descriptor = device.descriptor.takeIf { it.isNotBlank() } ?: return null
        return "descriptor:$descriptor"
    }

    private fun associate(device: InputDevice, displayUniqueId: String): Boolean {
        val descriptor = device.descriptor.takeIf { it.isNotBlank() } ?: return false
        val operation = methods.associateByDescriptor ?: run {
            OperationLog.w(context, "InputRouting", "descriptor association API unavailable", null)
            return false
        }
        return runCatching {
            operation.invoke(service, descriptor, displayUniqueId)
            OperationLog.i(
                context,
                "InputRouting",
                "association requested device=${device.id} name=${device.name} display=$displayUniqueId"
            )
            true
        }.onFailure {
            OperationLog.w(context, "InputRouting", "physical input association failed device=${device.id}", it)
        }.getOrDefault(false)
    }

    private fun removeAssociation(key: String) {
        val descriptor = key.removePrefix("descriptor:")
        if (descriptor == key) return
        runCatching { methods.removeByDescriptor?.invoke(service, descriptor) }
            .onFailure { OperationLog.w(context, "InputRouting", "physical input restoration failed", it) }
    }

    private fun persist() {
        preferences.edit()
            .putStringSet(KEY_ASSOCIATIONS, routedKeys.toSet())
            .putString(KEY_DISPLAY, routedDisplayUniqueId)
            .commit()
    }

    private fun discoverOperations(implementation: Class<*>): Operations {
        val candidates = implementation.interfaces.flatMap { it.methods.asIterable() } + implementation.methods
        fun find(name: String, vararg parameters: Class<*>): Method? = candidates.firstOrNull {
            it.name == name && it.parameterTypes.contentEquals(parameters)
        }
        return Operations(
            associateByDescriptor = find(
                "addUniqueIdAssociationByDescriptor",
                String::class.java,
                String::class.java
            ),
            removeByDescriptor = find(
                "removeUniqueIdAssociationByDescriptor",
                String::class.java
            )
        )
    }

    private data class Operations(
        val associateByDescriptor: Method?,
        val removeByDescriptor: Method?
    )

    private companion object {
        const val KEY_ASSOCIATIONS = "active_associations"
        const val KEY_DISPLAY = "display_unique_id"
    }
}
