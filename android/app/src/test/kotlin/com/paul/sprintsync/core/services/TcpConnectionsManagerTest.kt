package com.paul.sprintsync.core.services

import android.os.Looper
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class TcpConnectionsManagerTest {
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
    fun `client can connect and send message to host with configured tcp host`() {
        val port = ServerSocket(0).use { it.localPort }
        val host = TcpConnectionsManager(
            hostIp = "127.0.0.1",
            hostPort = port,
        )
        val client = TcpConnectionsManager(
            hostIp = "127.0.0.1",
            hostPort = port,
        )
        val payloadLatch = CountDownLatch(1)
        val clientConnected = CountDownLatch(1)
        var hostPayload: String? = null
        var clientEndpointId: String? = null

        host.setEventListener { event ->
            if (event is NearbyEvent.PayloadReceived) {
                hostPayload = event.message
                payloadLatch.countDown()
            }
        }
        client.setEventListener { event ->
            if (event is NearbyEvent.ConnectionResult && event.connected) {
                clientEndpointId = event.endpointId
                clientConnected.countDown()
            }
        }

        host.startHosting("svc", "host", NearbyTransportStrategy.POINT_TO_STAR) {
            assertTrue(it.isSuccess)
        }
        client.startDiscovery("svc", NearbyTransportStrategy.POINT_TO_STAR) {
            assertTrue(it.isSuccess)
        }
        client.requestConnection("127.0.0.1", "client") {
            assertTrue(it.isSuccess)
        }
        assertTrue(awaitWithMainLooper(clientConnected, timeoutMs = 5_000))

        client.sendMessage(clientEndpointId!!, """{"kind":"hello"}""") {
            assertTrue(it.isSuccess)
        }
        assertTrue(awaitWithMainLooper(payloadLatch, timeoutMs = 5_000))
        assertEquals("""{"kind":"hello"}""", hostPayload)

        client.stopAll()
        host.stopAll()
    }
}
