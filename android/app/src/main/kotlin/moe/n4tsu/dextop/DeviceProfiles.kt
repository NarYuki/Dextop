package moe.n4tsu.dextop

/**
 * OSS extension point for device support. Keep a rule as narrow as possible.
 * Model/fingerprint-specific workarounds must never be added to a manufacturer-wide rule.
 */
internal object DeviceProfiles {
    val rules: List<DesktopEnvironmentRule> = listOf(
        DesktopEnvironmentRule(
            "samsung_trifold",
            DeviceMatch(manufacturers = setOf("samsung"), devices = setOf("q7mq"), minSdk = 36)
        ) {
            DesktopEnvironmentRegistry.samsungDex(autoResizeWithHostDisplay = true)
        },
        // Japanese carrier variants can expose a model identifier beginning
        // with SC, SCG, or SCV even when Build.MANUFACTURER is not reported as
        // Samsung. Resolve those identifiers to the Samsung profile before
        // the generic vendor rules; the prefix is intentionally limited to
        // the model field so unrelated device codenames are not reclassified.
        DesktopEnvironmentRule(
            "samsung_carrier_model",
            DeviceMatch(modelPrefixes = setOf("scv", "scg", "sc"))
        ) {
            DesktopEnvironmentRegistry.samsungDex()
        },
        DesktopEnvironmentRule("samsung_dex", DeviceMatch(manufacturers = setOf("samsung"))) {
            DesktopEnvironmentRegistry.samsungDex()
        },
        // Xperia Launcher starts SecondaryDisplayLauncher on the trusted
        // recording display, which pauses the mirror (verified on A401SO,
        // Android 16), and opens Plasma v2 apps as freeform windows on their
        // application displays at 60 Hz, and its keyboard closes whenever the
        // Plasma overlay takes focus. On Android 15 (verified on SO-51D) Plasma v2
        // cannot use accessibility overlays on the overlay display, and the
        // mirror keeps a stale transform after rotation. Otherwise the AOSP
        // profile applies.
        DesktopEnvironmentRule("sony_xperia", DeviceMatch(manufacturers = setOf("sony"))) { identity ->
            DesktopEnvironmentRegistry.aospFreeform(identity)
                .copy(
                    id = "sony_xperia",
                    untrustedMirrorDisplay = true,
                    fullscreenApplicationDisplays = true,
                    passivePlasmaShellFocus = true,
                    maxRateApplicationDisplays = true,
                    plasmaApplicationOverlay = identity.sdk <= 35,
                    rebuildMirrorAfterLiveResize = identity.sdk <= 35,
                    privateApplicationDisplays = true,
                    directPlasmaDisplay = identity.sdk <= 35,
                )
        },
        vendor("hyperos_miui", setOf("xiaomi", "redmi", "poco"), "nativeHyperosMiuiDesktop"),
        vendor("coloros_family", setOf("oppo", "realme", "oneplus"), "nativeColorosDesktop"),
        vendor("vivo_family", setOf("vivo", "iqoo"), "nativeOriginosFuntouchOsDesktop"),
        vendor("huawei_emui", setOf("huawei"), "nativeHuaweiEmuiDesktop"),
        vendor("honor_magicos", setOf("honor"), "nativeHonorMagicosDesktop"),
        vendor("motorola_lenovo", setOf("motorola", "lenovo"), "nativeMotorolaLenovoDesktop"),
        vendor("asus_zenui", setOf("asus", "rog"), "nativeAsusZenuiRogUiDesktop"),
        vendor("nothing_os", setOf("nothing"), "nativeNothingOsDesktop"),
        vendor("pixel_android", setOf("google"), "nativePixelAndroidDesktop")
    )

    private fun vendor(id: String, manufacturers: Set<String>, stringKey: String) =
        DesktopEnvironmentRule(id, DeviceMatch(manufacturers = manufacturers)) { identity ->
            DesktopEnvironmentRegistry.vendorFreeform(identity, id, stringKey)
        }
}
