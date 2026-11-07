package moe.n4tsu.dextop.linux

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class LinuxUiScaleTest {
    @Test fun appOverrideIsAbsoluteAndDefaultFollowsGlobal() {
        assertEquals(150, LinuxUiScale.resolve(150, null))
        assertEquals(125, LinuxUiScale.resolve(200, 125))
        assertEquals(100, LinuxUiScale.resolve(200, 100))
    }
    @Test fun fractionsAreLocaleIndependentAndKeepToolkitSettingsSeparate() {
        assertContains(LinuxUiScale.script(125), "QT_SCALE_FACTOR=1.25")
        assertContains(LinuxUiScale.script(250), "GDK_SCALE=2 GDK_DPI_SCALE=1.25")
        assertFalse(LinuxUiScale.script(100).contains("GDK_SCALE=1"))
        assertContains(LinuxUiScale.script(50), "QT_SCALE_FACTOR=0.5")
    }
    @Test fun rejectsInvalidScaleBeforeGeneratingShell() {
        for (value in listOf(-1, 0, 49, 301, Int.MAX_VALUE)) assertFailsWith<IllegalArgumentException> { LinuxUiScale.script(value) }
        val script = LinuxCatalog.launchScript(10, "client", uiScale = 175)
        assertContains(script, "DEXTOP_UI_SCALE=1.75")
    }
}
