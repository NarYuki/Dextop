package moe.n4tsu.dextop.linux

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LinuxSysStandInsTest {
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
