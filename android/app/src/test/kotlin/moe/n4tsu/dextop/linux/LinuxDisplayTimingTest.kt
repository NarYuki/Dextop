package moe.n4tsu.dextop.linux

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class LinuxDisplayTimingTest {
    @Test fun sessionRatesDriveBothModeClockAndFramePacing() {
        for (rate in listOf(60f, 90f, 120f, 119.88f, 144f)) {
            val config = LinuxDisplayConfiguration.create(1080, 1920, 160, rate)
            val script = LinuxCatalog.virtualDisplayTiming(config.width, config.height, rate)
            val values = script.lineSequence().first { it.startsWith("xrandr --newmode") }.split(Regex("\\s+"))
            val actual = values[3].toDouble() * 1_000_000 / values[7].toInt() / values[11].toInt()
            assertTrue(kotlin.math.abs(actual - rate) < .002)
            assertTrue(kotlin.math.abs(config.frameIntervalNanos * rate / 1_000_000_000.0 - 1) < .00001)
        }
    }

    @Test fun liveRotationSelectsOneModeAndPreservesSessionRate() {
        val config = LinuxDisplayConfiguration.create(1920, 1080, 240, 120f)
        val script = LinuxCatalog.updateDisplayScript(98, config, true)
        assertContains(script, "export DISPLAY=:98")
        assertContains(script, "--mode dextop_1920x1080_120")
        assertContains(script, "--newmode dextop_1080x1920_120")
        assertTrue(script.lineSequence().count { "--output screen --mode" in it } == 1)
        assertContains(script, "2>/dev/null || true")
    }

    @Test fun invalidSessionRatesUseSafeTiming() {
        for (rate in listOf(Float.NaN, Float.POSITIVE_INFINITY, 0f, -1f)) {
            assertTrue(LinuxDisplayConfiguration.create(640, 480, 160, rate).refreshMilliHz == 60000)
        }
    }
    @Test fun launchesConsumeInstalledConfigurationWithoutPerApplicationFlags() {
        for (wayland in listOf(false, true)) {
            val script = LinuxCatalog.launchScript(10, "application", wayland = wayland)
            assertTrue(script.indexOf(". /etc/dextop/session.env") < script.indexOf("sh -c \"\$cmd\""))
            assertTrue("export DEXTOP_XVFB_WSI=1" !in script)
        }
    }
    @Test fun modeClockUsesFixedLocaleAndRepresentsSixtyHz() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            for ((width, height) in listOf(640 to 480, 1080 to 1544, 1920 to 1080)) {
                val script = LinuxCatalog.virtualDisplayTiming(width, height)
                val line = script.lineSequence().first { it.trimStart().startsWith("xrandr --newmode") }
                val values = line.trim().split(Regex("\\s+"))
                val clock = values[3].toDouble() * 1_000_000
                val rate = clock / values[7].toInt() / values[11].toInt()
                assertTrue(kotlin.math.abs(rate - 60.0) < 0.001)
                assertContains(script, "--output screen --mode dextop_${width}x${height}_60")
            }
        } finally { Locale.setDefault(previous) }
    }

    @Test fun bothXvfbSessionsConfigureTimingBeforeClientsStart() {
        for (script in listOf(LinuxCatalog.appsScript(10, 1080, 1544, 160),
            LinuxCatalog.desktopScript(11, 1080, 1544, 160, "icewm"))) {
            assertContains(script, "-noreset")
            assertTrue("DEXTOP_XVFB_WSI" !in script)
            assertTrue(script.indexOf("xrandr --newmode") < script.indexOf("echo dextop-x-ready"))
        }
    }

    @Test fun applicationDisplayOffersLandscapeModesAndLeavesTheRequestedModeSelected() {
        for ((width, height) in listOf(1080 to 1544, 1920 to 1080, 1080 to 1920)) {
            val script = LinuxCatalog.appsScript(10, width, height, 160)
            assertContains(script, "--newmode dextop_1280x720_60")
            assertContains(script, "--newmode dextop_1920x1080_60")
            val selected = script.lineSequence().filter { "--output screen --mode" in it }.last()
            assertContains(selected, "--mode dextop_${width}x${height}_60")
        }
    }
}
