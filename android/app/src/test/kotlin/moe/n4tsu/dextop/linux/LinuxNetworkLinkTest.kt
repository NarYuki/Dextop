package moe.n4tsu.dextop.linux

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LinuxNetworkLinkTest {
    @Test fun wifiScanKeepsRealAccessPointsAndHandlesAndroidAgeAndRssiColumns() {
        val result = LinuxWifiScan.parse("""
            BSSID Frequency RSSI Age(sec) SSID Flags
            02:00:00:00:00:01 2427 -87(0:-89/1:-91) >1000.0 Example network [WEP][ESS]
            02:00:00:00:00:02 2442 -60(0:-60/1:-61) 2.5 Example network [RSN-SAE-CCMP-128][ESS]
            02:00:00:00:00:03 2452 -88 2.0    [WPA2-PSK-CCMP-128][ESS]
        """.trimIndent())
        assertEquals(2, result.size)
        assertEquals("02:00:00:00:00:02", result.first().bssid)
        assertEquals("Example network", result.first().ssid)
        assertTrue(result.first().strength > result.last().strength)
    }

    @Test fun mobileSignalUsesTheDataSimAndPrimaryRadio() {
        val registry = """
            Phone Id=0
            mSignalStrength=SignalStrength:{mLte=CellSignalStrengthLte: level=1 parameters=0,mNr=Invalid,primary=CellSignalStrengthLte}
            Phone Id=1
            mSignalStrength=SignalStrength:{mLte=CellSignalStrengthLte: level=2 parameters=0,mNr=CellSignalStrengthNr: level=4 parameters=0,primary=CellSignalStrengthNr}
        """.trimIndent()
        assertEquals(0.25f, LinuxMobileSignal.level(registry, 0))
        assertEquals(1f, LinuxMobileSignal.level(registry, 1))
        assertEquals(null, LinuxMobileSignal.level(registry, -1))
        assertEquals(null, LinuxMobileSignal.level("permission denied", 1))
    }

    @Test fun customDnsAcceptsOnlyNumericUnicastAddresses() {
        for (ip in listOf("1.1.1.1", "192.168.1.1", "2606:4700:4700::1111", "127.0.0.1")) LinuxNetworkLink.validateDnsAddress(ip)
        for (ip in listOf("example.com", "123", "1.2.3", "256.1.1.1", "1.1.1.1\nsearch evil", "::", "0.0.0.0", "224.0.0.1", "fe80::1", "::1%lo")) {
            assertFailsWith<IllegalArgumentException>(ip) { LinuxNetworkLink.validateDnsAddress(ip) }
        }
    }
    @Test fun resolverFollowsAndroidWithoutInventingAFallback() {
        assertEquals("nameserver 192.0.2.1\n", LinuxNetworkLink.resolver(listOf("192.0.2.1", "192.0.2.1")))
        assertEquals(null, LinuxNetworkLink.resolver(emptyList()))
        assertFailsWith<IllegalArgumentException> { LinuxNetworkLink.resolver(listOf("example.com")) }
    }
    @Test fun exposesTheActualPolicyTableGateway() {
        val text = LinuxNetworkLink.describe("wlan0", 45, 1500, listOf("192.0.2.2" to 24), listOf("192.0.2.1"))
        assertTrue(text.contains("if wlan0 45 1500\n"))
        assertTrue(text.contains("addr 192.0.2.2 24\n"))
        assertTrue(text.endsWith("gw 192.0.2.1\n"))
    }
    @Test fun disconnectionDoesNotInventAnOnlineLink() {
        assertEquals("", LinuxNetworkLink.describe("", 2, 1500, emptyList(), emptyList()))
    }
    @Test fun rejectsInvalidPrefixAndNonNumericAddress() {
        for (address in listOf("192.0.2.2", "example.com")) assertFailsWith<IllegalArgumentException> {
            LinuxNetworkLink.describe("wlan0", 45, 1500, listOf(address to 129), emptyList())
        }
    }
}
