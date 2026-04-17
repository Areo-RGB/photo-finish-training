package com.paul.sprintsync.core.services

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GatewayResolverTest {
    @Test
    fun `resolveGatewayIp returns null when WifiManager is unavailable`() {
        val appContext = mockk<Context>(relaxed = true)
        val context = mockk<Context>(relaxed = true)
        every { context.applicationContext } returns appContext
        every { appContext.getSystemService(Context.WIFI_SERVICE) } returns null

        assertNull(GatewayResolver.resolveGatewayIp(context))
    }
}
