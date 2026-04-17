package com.paul.sprintsync.core.services

import org.junit.Assert.assertEquals
import org.junit.Test

class TcpConnectionsManagerIdentityTest {
    @Test
    fun `tcp endpoint identity uses host ip only`() {
        assertEquals("10.173.42.82", tcpEndpointIdFromHostAddress("10.173.42.82"))
    }

    @Test
    fun `tcp endpoint identity falls back to unknown when host missing`() {
        assertEquals("unknown", tcpEndpointIdFromHostAddress(null))
        assertEquals("unknown", tcpEndpointIdFromHostAddress(""))
    }
}
