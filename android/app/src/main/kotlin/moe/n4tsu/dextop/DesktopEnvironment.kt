package moe.n4tsu.dextop

import android.os.Build

internal data class DeviceIdentity(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val device: String,
    val product: String,
    val fingerprint: String,
    val sdk: Int
) {
    companion object {
        fun current() = DeviceIdentity(
            Build.MANUFACTURER, Build.BRAND, Build.MODEL, Build.DEVICE,
            Build.PRODUCT, Build.FINGERPRINT, Build.VERSION.SDK_INT
        )
    }
}

internal data class DeviceMatch(
    val manufacturers: Set<String> = emptySet(),
    val models: Set<String> = emptySet(),
    /** Match regional model identifiers whose suffix varies by carrier. */
    val modelPrefixes: Set<String> = emptySet(),
    val devices: Set<String> = emptySet(),
    val fingerprintPrefixes: Set<String> = emptySet(),
    val minSdk: Int = 29,
    val maxSdk: Int = Int.MAX_VALUE
) {
    fun matches(identity: DeviceIdentity): Boolean {
        fun String.normalized() = lowercase().trim()
        if (identity.sdk !in minSdk..maxSdk) return false
        if (manufacturers.isNotEmpty() && manufacturers.none { it.normalized() == identity.manufacturer.normalized() }) return false
        if (models.isNotEmpty() && models.none { it.normalized() == identity.model.normalized() }) return false
        if (modelPrefixes.isNotEmpty() && modelPrefixes.none {
                identity.model.normalized().startsWith(it.normalized())
            }) return false
        if (devices.isNotEmpty() && devices.none { it.normalized() == identity.device.normalized() }) return false
        if (fingerprintPrefixes.isNotEmpty() && fingerprintPrefixes.none {
                identity.fingerprint.normalized().startsWith(it)
            }) return false
        return true
    }
}

internal data class DesktopEnvironment(
    val id: String,
    val displayName: String,
    val platformManaged: Boolean,
    val temporaryGlobalSettings: Map<String, String>,
    val configureFreeformWindowing: Boolean,
    val supportsLaunchBounds: Boolean = true,
    /** Recreate a device-resolution session when a foldable host changes size. */
    val autoResizeWithHostDisplay: Boolean = false,
    /** Ordered, device-local strategies. Never change the global order for one model. */
    val displayCreationStrategies: List<String> = listOf("overlay_settings"),
    val mirrorStrategies: List<String> = listOf("virtual_display", "window_manager", "surface_control"),
    val windowingStrategies: List<String> = listOf("wm", "activity_task_manager")
)

internal data class DesktopEnvironmentRule(
    val id: String,
    val match: DeviceMatch,
    val create: (DeviceIdentity) -> DesktopEnvironment
)

/**
 * Device-specific changes belong in a narrow rule. A model/fingerprint override
 * cannot affect another device. Runtime capability probes remain authoritative.
 */
internal object DesktopEnvironmentRegistry {
    fun current(): DesktopEnvironment = resolve(DeviceIdentity.current())

    fun resolve(identity: DeviceIdentity): DesktopEnvironment {
        val rule = DeviceProfiles.rules.firstOrNull { it.match.matches(identity) }
        return rule?.create?.invoke(identity) ?: aospFreeform(identity)
    }

    internal fun vendorFreeform(identity: DeviceIdentity, id: String, stringKey: String) =
        aospFreeform(identity).copy(id = id, displayName = NativeStrings.text(stringKey))

    internal fun samsungDex(autoResizeWithHostDisplay: Boolean = false) = DesktopEnvironment(
        "samsung_dex", "Samsung DeX", true, emptyMap(), false,
        autoResizeWithHostDisplay = autoResizeWithHostDisplay
    )

    internal fun aospFreeform(identity: DeviceIdentity): DesktopEnvironment {
        val settings = linkedMapOf(
            "enable_freeform_support" to "1",
            "force_resizable_activities" to "1"
        )
        // Still honored as a compatibility signal by Pixel's projected-display
        // policy even when the Android 16+ desktop-first path is available.
        settings["force_desktop_mode_on_external_displays"] = "1"
        return DesktopEnvironment(
            "android_freeform", NativeStrings.text("nativeAndroidDesktopFreeform"),
            false, settings, true
        )
    }
}
