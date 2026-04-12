package com.paul.sprintsync.core.services

import android.os.Looper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class TcpConnectionsManagerDiscoveryTest {
    private fun awaitWithMainLooper(latch: CountDownLatch, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (latch.await(50, TimeUnit.MILLISECONDS)) {
                return true
            }
            shadowOf(Looper.getMainLooper()).idle()
        }
        return latch.count == 0L
    }

    @Test
    fun `startDiscovery falls back to gateway IP when NSD unavailable`() {
        val manager = TcpConnectionsManager(
            hostPort = 9_999,
            resolveGatewayIp = { "10.0.0.1" },
        )
        val latch = CountDownLatch(1)
        var endpointFound: NearbyEvent.EndpointFound? = null

        manager.setEventListener { event ->
            if (event is NearbyEvent.EndpointFound) {
                endpointFound = event
                latch.countDown()
            }
        }

        manager.startDiscovery("svc", NearbyTransportStrategy.POINT_TO_STAR) {
            assertTrue(it.isSuccess)
        }

        assertTrue(awaitWithMainLooper(latch, timeoutMs = 2_000))
        assertEquals("10.0.0.1", endpointFound?.endpointId)
        assertEquals("10.0.0.1", endpointFound?.endpointName)
        manager.stopAll()
    }

    @Test
    fun `startDiscovery falls back to configured host IP when gateway unavailable`() {
        val manager = TcpConnectionsManager(
            hostPort = 9_999,
            resolveGatewayIp = { null },
            fallbackHostIp = "192.168.1.22",
        )
        val latch = CountDownLatch(1)
        var endpointFound: NearbyEvent.EndpointFound? = null

        manager.setEventListener { event ->
            if (event is NearbyEvent.EndpointFound) {
                endpointFound = event
                latch.countDown()
            }
        }

        manager.startDiscovery("svc", NearbyTransportStrategy.POINT_TO_STAR) {
            assertTrue(it.isSuccess)
        }

        assertTrue(awaitWithMainLooper(latch, timeoutMs = 2_000))
        assertEquals("192.168.1.22", endpointFound?.endpointId)
        assertEquals("192.168.1.22", endpointFound?.endpointName)
        manager.stopAll()
    }

    @Test
    fun `startDiscovery emits error when all discovery fallbacks are unavailable`() {
        val manager = TcpConnectionsManager(hostPort = 9_999)
        val latch = CountDownLatch(1)
        var error: NearbyEvent.Error? = null

        manager.setEventListener { event ->
            if (event is NearbyEvent.Error) {
                error = event
                latch.countDown()
            }
        }

        manager.startDiscovery("svc", NearbyTransportStrategy.POINT_TO_STAR) {
            assertTrue(it.isSuccess)
        }

        assertTrue(awaitWithMainLooper(latch, timeoutMs = 2_000))
        assertEquals("No host IP: NSD unavailable, DHCP gateway null, no fallback", error?.message)
        manager.stopAll()
    }
}
