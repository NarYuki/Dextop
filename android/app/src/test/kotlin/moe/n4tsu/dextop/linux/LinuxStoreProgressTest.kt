package moe.n4tsu.dextop.linux

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LinuxStoreProgressTest {
    @Test fun downloadKeepsFractionalProgressAndStatus() {
        val progress = LinuxStoreProgress.apt("dlstatus:3:27.375:Downloading: editor")!!
        assertEquals(27.375, progress.percent)
        assertEquals("download", progress.phase)
        assertEquals("Downloading: editor", progress.line)
    }
    @Test fun installationUsesItsOwnReportedPercent() {
        val progress = LinuxStoreProgress.apt("pmstatus:editor:1.25:Unpacking editor")!!
        assertEquals(1.25, progress.percent)
        assertEquals("install", progress.phase)
    }
    @Test fun malformedOrNonNumericStatusDoesNotInventProgress() {
        listOf("Reading package lists...", "dlstatus:0:NaN:bad", "pmstatus:x:Infinity:bad", "dlstatus:3::unknown")
            .forEach { assertNull(LinuxStoreProgress.apt(it)) }
    }
    @Test fun progressIsClampedToItsProtocolRange() {
        assertEquals(100.0, LinuxStoreProgress.apt("pmstatus:x:100.001:Done")?.percent)
    }
}
