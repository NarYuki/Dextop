package moe.n4tsu.dextop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeviceMatchTest {
    private val pixel = DeviceIdentity("Google", "google", "Pixel 9", "tokay", "tokay", "google/tokay/build", 35)

    @Test fun normalizesAndMatchesAllSpecifiedFields() {
        val match = DeviceMatch(
            manufacturers = setOf("google"), models = setOf("pixel 9"),
            devices = setOf("tokay"), fingerprintPrefixes = setOf("google/tokay"),
            minSdk = 35, maxSdk = 35
        )
        assertTrue(match.matches(pixel))
    }

    @Test fun modelOverrideDoesNotLeakToOtherModels() {
        val match = DeviceMatch(manufacturers = setOf("google"), models = setOf("pixel 9"))
        assertFalse(match.matches(pixel.copy(model = "Pixel 8")))
        assertFalse(match.matches(pixel.copy(manufacturer = "Samsung", model = "Pixel 9")))
    }

    @Test fun sdkRangeIsIsolated() {
        val match = DeviceMatch(manufacturers = setOf("google"), minSdk = 35, maxSdk = 35)
        assertFalse(match.matches(pixel.copy(sdk = 34)))
        assertFalse(match.matches(pixel.copy(sdk = 36)))
    }

    @Test fun legacyPixelProfileRestoresAospFlagsAndOlderMirrorOrder() {
        val environment = DesktopEnvironmentRegistry.legacyPixelAosp(pixel.copy(sdk = 36))
        assertEquals(DesktopStartupMode.COMPAT_WINDOWING, environment.startupMode)
        assertTrue(environment.configureFreeformWindowing)
        assertEquals("1", environment.temporaryGlobalSettings["enable_freeform_support"])
        assertEquals("1", environment.temporaryGlobalSettings["force_resizable_activities"])
        assertEquals("window_manager", environment.mirrorStrategies.first())
    }

    @Test fun modernPixelProfileForcesWindowingFlagsOnVirtualDisplay() {
        val environment = DesktopEnvironmentRegistry.resolve(pixel.copy(sdk = 37))
        assertEquals(DesktopStartupMode.SYSTEM_MANAGED, environment.startupMode)
        assertFalse(environment.configureFreeformWindowing)
        assertEquals("virtual_display", environment.mirrorStrategies.first())
        assertEquals("1", environment.temporaryGlobalSettings["force_resizable_activities"])
        assertEquals("1", environment.temporaryGlobalSettings["force_desktop_mode_on_external_displays"])
        assertEquals(null, environment.temporaryGlobalSettings["override_desktop_experience_features"])
        assertEquals(null, environment.temporaryGlobalSettings["override_desktop_mode_features"])
        assertEquals(null, environment.temporaryGlobalSettings["enable_non_resizable_multi_window"])
        assertEquals(null, environment.temporaryGlobalSettings["force_desktop_mode_on_secondary_displays"])
        assertEquals(null, environment.temporaryGlobalSettings["enable_freeform_support"])
    }

    @Test fun galaxyTriFoldUsesNarrowAutoResizeProfile() {
        val triFold = DeviceIdentity(
            "samsung", "samsung", "SM-F968N", "q7mq", "q7mqksx",
            "samsung/q7mqksx/q7mq:16/build", 36
        )
        val environment = DesktopEnvironmentRegistry.resolve(triFold)
        assertEquals("samsung_dex", environment.id)
        assertTrue(environment.autoResizeWithHostDisplay)

        val fold7 = triFold.copy(model = "SM-F966Q", device = "q7q", product = "q7qjpnw")
        assertFalse(DesktopEnvironmentRegistry.resolve(fold7).autoResizeWithHostDisplay)
    }

    @Test fun regionalSamsungCarrierModelPrefixesUseSamsungProfile() {
        listOf("SC-56F", "SCG39", "SCV46").forEach { model ->
            val identity = DeviceIdentity(
                "unknown", "unknown", model, model, model,
                "unknown/$model/build", 36
            )
            val environment = DesktopEnvironmentRegistry.resolve(identity)
            assertEquals("samsung_dex", environment.id)
            assertTrue(environment.platformManaged)
        }
    }
}
