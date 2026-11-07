package moe.n4tsu.dextop.linux

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import java.nio.file.Files

class LinuxSysStandInsTest {
    @Test fun removesDisappearedInterfaceAndKeepsCurrentCounters() {
        val root = Files.createTempDirectory("dextop-network").toFile()
        try {
            val counters = List(16) { "0" }.toMutableList().apply { this[0] = "42"; this[8] = "84" }
            LinuxSysStandIns.writeNetwork(root, mapOf("lo" to counters, "tun0" to counters))
            val failures = LinuxSysStandIns.writeNetwork(root, mapOf("lo" to counters, "tun1" to counters))
            assertTrue(failures.isEmpty())
            assertFalse(root.resolve("tun0").exists())
            assertEquals("42\n", root.resolve("lo/statistics/rx_bytes").readText())
            assertEquals("84\n", root.resolve("tun1/statistics/tx_bytes").readText())
        } finally { LinuxRootfsArchive.deleteTree(root) }
    }

    @Test fun failedCleanupDoesNotDiscardOrStopUpdatingCurrentCounters() {
        val root = Files.createTempDirectory("dextop-network-cleanup").toFile()
        try {
            val old = root.resolve("tun0/statistics").apply { mkdirs() }
            old.resolve("rx_bytes").writeText("1\n")
            val counters = List(16) { "0" }.toMutableList().apply { this[0] = "42" }
            val failures = LinuxSysStandIns.writeNetwork(root, mapOf("lo" to counters)) {
                throw SecurityException("cleanup denied")
            }
            assertEquals(1, failures.size)
            assertEquals("tun0", failures.single().first.name)
            assertTrue(failures.single().second is SecurityException)
            assertEquals("42\n", root.resolve("lo/statistics/rx_bytes").readText())
            assertTrue(old.resolve("rx_bytes").isFile)
            counters[0] = "99"
            LinuxSysStandIns.writeNetwork(root, mapOf("lo" to counters)) { throw SecurityException("cleanup denied") }
            assertEquals("99\n", root.resolve("lo/statistics/rx_bytes").readText())
        } finally { LinuxRootfsArchive.deleteTree(root) }
    }

    @Test fun parsesProcNetDev() {
        val text = """
            Inter-|   Receive                                                |  Transmit
             face |bytes    packets errs drop fifo frame compressed multicast|bytes    packets errs drop fifo colls carrier compressed
                lo: 6961001    7117    0    0    0     0          0         0  6961001    7117    0    0    0     0       0          0
             wlan0: 276063899  229157    0    0    0     0          0         0 13522042   61960    0    0    0     0       0          0
        """.trimIndent()
        val parsed = LinuxSysStandIns.parseNetDev(text)
        assertEquals(setOf("lo", "wlan0"), parsed.keys)
        assertEquals("276063899", parsed.getValue("wlan0")[0])
        assertEquals("13522042", parsed.getValue("wlan0")[8])
    }

    @Test fun parsesDumpsysBattery() {
        val text = "Current Battery Service state:\n  AC powered: false\n  USB powered: true\n  status: 2\n  health: 2\n" +
            "  present: true\n  level: 89\n  scale: 100\n  voltage: 4123\n  temperature: 312\n"
        val battery = LinuxSysStandIns.parseDumpsys(text)!!
        assertEquals(89, battery.level)
        assertEquals(2, battery.status)
        assertEquals(true, battery.plugged)
        assertEquals(312, battery.temperature)
        assertEquals(4123, battery.voltage)
        assertNull(LinuxSysStandIns.parseDumpsys("nothing here"))
    }
}
