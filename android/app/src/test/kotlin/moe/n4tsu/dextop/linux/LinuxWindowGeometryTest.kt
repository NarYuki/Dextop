package moe.n4tsu.dextop.linux

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LinuxWindowGeometryTest {
    @Test fun resizeCommitPreservesHostPositionAndUsesCommittedResolution() {
        val geometry = LinuxWindowGeometry()
        geometry.report(1, 0, false, 0, 0, 800, 600)
        geometry.place(1, 640, 320, 1280, 720)
        assertEquals(LinuxWindowGeometry.Bounds(640, 320, 800, 600), geometry[1])
        assertEquals(LinuxWindowGeometry.Bounds(640, 320, 1280, 720),
            geometry.report(1, 0, false, 0, 0, 1280, 720))
    }

    @Test fun aDialogParentIsNotAnOffsetForItsOwnTopLevelPosition() {
        val geometry = LinuxWindowGeometry()
        geometry.place(1, 640, 320, 1280, 720)
        geometry.report(2, 1, false, 0, 0, 400, 300)
        geometry.place(2, 900, 500, 400, 300)
        assertEquals(LinuxWindowGeometry.Bounds(900, 500, 400, 300),
            geometry.report(2, 1, false, 0, 0, 400, 300))
        assertEquals(LinuxWindowGeometry.Bounds(910, 530, 200, 120),
            geometry.report(3, 2, true, 10, 30, 200, 120))
    }

    @Test fun nestedMenusFollowParentMovesIncludingNegativeOffsets() {
        val geometry = LinuxWindowGeometry()
        geometry.place(1, 640, 320, 800, 600)
        geometry.report(2, 1, true, 10, 30, 200, 120)
        geometry.report(3, 2, true, -180, 20, 180, 100)
        val moved = geometry.place(1, 100, 200, 800, 600)
        assertEquals(LinuxWindowGeometry.Bounds(110, 230, 200, 120), moved[2])
        assertEquals(LinuxWindowGeometry.Bounds(-70, 250, 180, 100), moved[3])
    }

    @Test fun repositionAndUnmapDoNotReuseTheOldMenuOffset() {
        val geometry = LinuxWindowGeometry()
        geometry.place(1, 600, 200, 800, 600)
        geometry.report(2, 1, true, 10, 30, 200, 120)
        geometry.report(2, 1, true, 100, 50, 220, 140)
        assertEquals(LinuxWindowGeometry.Bounds(700, 250, 220, 140), geometry[2])
        geometry.remove(2)
        assertNull(geometry[2])
        geometry.clear()
        assertNull(geometry[1])
    }
}
