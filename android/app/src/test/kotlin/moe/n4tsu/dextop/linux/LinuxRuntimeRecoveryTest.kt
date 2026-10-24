package moe.n4tsu.dextop.linux

import kotlin.test.*

class LinuxRuntimeRecoveryTest {
    @Test fun identifiesDeletedContainerByExactRootArgument() {
        val root = LinuxPaths.containers.absolutePath + "/cDeleted123/rootfs"
        assertEquals(root, LinuxRuntime.rootOf(LinuxRuntime.Entry(10, 1, listOf("/old/libproot.so", "-r", root))))
        for (args in listOf(listOf("libproot.so", "-r", "/other/app/rootfs"), listOf("sh", "-c", root),
            listOf("libproot.so", "-r", root + "/other"), listOf("libproot.so", "-r", LinuxPaths.containers.absolutePath + "/../rootfs"))) {
            assertNull(LinuxRuntime.rootOf(LinuxRuntime.Entry(11, 1, args)))
        }
    }
    @Test fun killsDescendantsBeforeTheirTracerAndExcludesOtherSessions() {
        val entries = listOf(LinuxRuntime.Entry(10, 1, emptyList()), LinuxRuntime.Entry(11, 10, emptyList()),
            LinuxRuntime.Entry(12, 11, emptyList()), LinuxRuntime.Entry(14, 1, emptyList(), tracer = 10), LinuxRuntime.Entry(20, 1, emptyList()))
        assertEquals(listOf(12, 14, 11, 10), LinuxRuntime.descendants(entries, setOf(10)).map { it.pid })
    }
}
