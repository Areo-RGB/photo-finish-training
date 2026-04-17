package com.paul.sprintsync.core.services

import android.content.Context
import android.net.wifi.WifiManager
import com.paul.sprintsync.ipv4FromLittleEndianInt

object GatewayResolver {
    fun resolveGatewayIp(context: Context): String? {
        val wifiManager = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return null
        val dhcpInfo = wifiManager.dhcpInfo ?: return null
        return ipv4FromLittleEndianInt(dhcpInfo.gateway)
    }
}
