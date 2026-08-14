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
        DesktopEnvironmentRule("samsung_dex", DeviceMatch(manufacturers = setOf("samsung"))) {
            DesktopEnvironmentRegistry.samsungDex()
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
