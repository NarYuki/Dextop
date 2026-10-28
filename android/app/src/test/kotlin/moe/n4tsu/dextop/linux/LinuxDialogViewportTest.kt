package moe.n4tsu.dextop.linux

import kotlin.test.Test
import kotlin.test.assertEquals

class LinuxDialogViewportTest {
    @Test fun smallFixedGtkDialogIsNotMagnifiedToDesktopTaskBounds() {
        assertEquals(LinuxDialogViewport.Size(480, 200), LinuxDialogViewport.fit(480, 200, 1384, 895))
    }

    @Test fun oversizedDialogFitsWithoutChangingAspectRatio() {
        assertEquals(LinuxDialogViewport.Size(800, 600), LinuxDialogViewport.fit(1600, 1200, 1000, 600))
        assertEquals(LinuxDialogViewport.Size(600, 300), LinuxDialogViewport.fit(1600, 800, 600, 900))
    }

    @Test fun scaledButtonsUseClientCoordinatesAndOutsideClicksStayOutside() {
        assertEquals(800, LinuxDialogViewport.coordinate(400f, 800, 1600))
        assertEquals(600, LinuxDialogViewport.coordinate(300f, 600, 1200))
        assertEquals(-20, LinuxDialogViewport.coordinate(-10f, 800, 1600))
        assertEquals(1620, LinuxDialogViewport.coordinate(810f, 800, 1600))
    }

    @Test fun narrowTaskDoesNotForceAMinimumViewportLargerThanItself() {
        assertEquals(LinuxDialogViewport.Size(40, 20), LinuxDialogViewport.fit(480, 240, 40, 60))
        assertEquals(LinuxDialogViewport.Size(1, 1), LinuxDialogViewport.fit(0, 0, 0, 0))
    }
}
