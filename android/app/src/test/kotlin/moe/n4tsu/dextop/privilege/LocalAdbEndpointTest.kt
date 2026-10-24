package moe.n4tsu.dextop.privilege

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalAdbEndpointTest {
    @Test fun discoveryCannotSelectAnotherPhoneOnTheSameNetwork() {
        val local = listOf("192.0.2.10", "fe80::1234%wlan0")
        assertTrue(LocalAdbEndpoint.accepts("192.0.2.10", local))
        assertFalse(LocalAdbEndpoint.accepts("192.0.2.20", local))
        assertTrue(LocalAdbEndpoint.accepts("fe80::1234%7", local))
        assertFalse(LocalAdbEndpoint.accepts(null, local))
        assertFalse(LocalAdbEndpoint.accepts("", local))
    }
    @Test fun loopbackIsLocalWithoutANetworkInterface() {
        assertTrue(LocalAdbEndpoint.accepts("127.0.0.1", emptyList()))
        assertTrue(LocalAdbEndpoint.accepts("::1", emptyList()))
        assertFalse(LocalAdbEndpoint.accepts("0.0.0.0", emptyList()))
    }
}
