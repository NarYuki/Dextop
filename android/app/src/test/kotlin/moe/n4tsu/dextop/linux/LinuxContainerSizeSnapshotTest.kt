package moe.n4tsu.dextop.linux

import java.nio.file.Files
import java.util.ArrayDeque
import java.util.concurrent.Executor
import kotlin.test.Test
import kotlin.test.assertEquals

class LinuxContainerSizeSnapshotTest {
    @Test fun serviceRestartUsesLastMeasurementInsteadOfInstallationSize() {
        val directory = Files.createTempDirectory("dextop-size").toFile()
        try {
            val tasks = ArrayDeque<Runnable>()
            fun cache() = LinuxContainerSizes(Executor { tasks.add(it) }, { 350L }, {},
                restore = { _, fallback -> LinuxContainerSizeSnapshot.read(directory, fallback) },
                persist = { _, bytes -> LinuxContainerSizeSnapshot.write(directory, bytes) })
            val first = cache()
            first.request("c1", 100L)
            tasks.removeFirst().run()
            first.close()
            val restarted = cache()
            assertEquals(350L, restarted.read("c1", 100L))
            assertEquals(0, tasks.size)
            restarted.close()
        } finally { directory.listFiles()?.forEach { it.delete() }; directory.delete() }
    }

    @Test fun damagedSnapshotFallsBackAndSuccessfulWritesReplaceEarlierMeasurements() {
        val directory = Files.createTempDirectory("dextop-size").toFile()
        try {
            assertEquals(100L, LinuxContainerSizeSnapshot.read(directory, 100L))
            LinuxContainerSizeSnapshot.write(directory, 200L)
            LinuxContainerSizeSnapshot.write(directory, 300L)
            assertEquals(300L, LinuxContainerSizeSnapshot.read(directory, 100L))
            directory.resolve("last-size-bytes").writeText("broken")
            assertEquals(100L, LinuxContainerSizeSnapshot.read(directory, 100L))
        } finally { directory.listFiles()?.forEach { it.delete() }; directory.delete() }
    }
}
