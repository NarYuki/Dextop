package moe.n4tsu.dextop.linux

import kotlin.test.Test
import kotlin.test.assertEquals

class LinuxWindowViewportTest {
    @Test fun widescreenGameOnPortraitHostKeepsAspectAndPointerAlignment() {
        val viewport = LinuxWindowViewport(1280, 720, 1080, 2340, true)
        assertEquals(1080f, viewport.width)
        assertEquals(607.5f, viewport.height)
        assertEquals(640, viewport.bufferX(540f))
        assertEquals(360, viewport.bufferY(1170f))
        assertEquals(0, viewport.bufferY(viewport.top))
        assertEquals(719, viewport.bufferY(viewport.top + viewport.height))
    }

    @Test fun resolutionChangeRecomputesTheInverseTransform() {
        val oldMode = LinuxWindowViewport(1280, 720, 2340, 1080, true)
        val newMode = LinuxWindowViewport(1024, 768, 2340, 1080, true)
        assertEquals(640, oldMode.bufferX(1170f))
        assertEquals(512, newMode.bufferX(1170f))
        assertEquals(0, newMode.bufferX(0f))
        assertEquals(1023, newMode.bufferX(2340f))
        assertEquals(384, newMode.bufferY(540f))
    }

    @Test fun ordinaryWindowsUseTheirWholeContentArea() {
        val viewport = LinuxWindowViewport(800, 600, 400, 200, false)
        assertEquals(0f, viewport.left)
        assertEquals(0f, viewport.top)
        assertEquals(400, viewport.bufferX(200f))
        assertEquals(300, viewport.bufferY(100f))
    }
}
