package moe.n4tsu.dextop.linux

import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class LinuxBluetoothFilesTest {
    private fun write(bridge: LinuxBluetoothBridge, file: File, value: String) {
        val method = LinuxBluetoothBridge::class.java.getDeclaredMethod("write", File::class.java, String::class.java)
        method.isAccessible = true
        method.invoke(bridge, file, value)
    }

    @Test fun selectsAtMost32RequestsAndIgnoresOtherNames() {
        val directory = Files.createTempDirectory("dextop-bt-queue").toFile()
        val bridge = LinuxBluetoothBridge { emptyList() }
        try {
            repeat(100) { File(directory, "%032x.json".format(it)).writeText("{}") }
            File(directory, "invalid.json").writeText("{}")
            File(directory, "reply.result").writeText("{}")
            val batch = bridge.requestBatch(directory)
            assertEquals(32, batch.size)
            assertEquals(32, batch.map { it.name }.toSet().size)
            batch.forEach { assertEquals(true, it.name.matches(Regex("[a-f0-9]{32}[.]json"))) }
        } finally { bridge.close(); directory.deleteRecursively() }
    }

    @Test fun replacesOversizedGuestSnapshotWithoutReadingIt() {
        val directory = Files.createTempDirectory("dextop-bt-test").toFile()
        val bridge = LinuxBluetoothBridge { emptyList() }
        try {
            val file = File(directory, "snapshot.json")
            // Sparse: no multi-gigabyte allocation or write. Reading the previous
            // contents would exceed the JVM String/array limit.
            RandomAccessFile(file, "rw").use { it.setLength(Int.MAX_VALUE.toLong() + 1) }
            write(bridge, file, "safe snapshot")
            assertEquals("safe snapshot", file.readText())
        } finally { bridge.close(); directory.deleteRecursively() }
    }

    @Test fun replacesSymlinkInsteadOfReadingOrModifyingItsTarget() {
        val directory = Files.createTempDirectory("dextop-bt-test").toFile()
        val bridge = LinuxBluetoothBridge { emptyList() }
        try {
            val protected = File(directory, "protected").apply { writeText("KEEP") }
            val file = File(directory, "snapshot.json")
            Files.createSymbolicLink(file.toPath(), protected.toPath())
            write(bridge, file, "safe snapshot")
            assertEquals("KEEP", protected.readText())
            assertEquals("safe snapshot", file.readText())
            assertFalse(Files.isSymbolicLink(file.toPath()))
        } finally { bridge.close(); directory.deleteRecursively() }
    }
}
