package moe.n4tsu.dextop.plasma

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LinuxClientCaptionTest {
    @Test fun onlyBlankSteamCaptionCanStartTheFallbackDrag() {
        assertTrue(LinuxClientCaption.steam("steam", "Steam", 1080, 500f, 12f))
        assertTrue(LinuxClientCaption.steam("Steam", "Steam", 1920, 800f, 12f))
        assertFalse(LinuxClientCaption.steam("steam", "Steam", 1080, 100f, 12f))
        assertFalse(LinuxClientCaption.steam("steam", "Steam", 1080, 1000f, 12f))
        assertFalse(LinuxClientCaption.steam("steam", "Steam", 1080, 500f, 50f))
        assertFalse(LinuxClientCaption.steam("steam", "Sign in to Steam", 1080, 500f, 12f))
        assertFalse(LinuxClientCaption.steam("other", "Steam", 1080, 500f, 12f))
    }
}
