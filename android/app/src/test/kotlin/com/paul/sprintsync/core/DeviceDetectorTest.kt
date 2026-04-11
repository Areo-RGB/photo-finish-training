package com.paul.sprintsync.core

import kotlin.test.Test
import kotlin.test.assertEquals

class DeviceDetectorTest {
    @Test
    fun `detect maps xiaomi tablet to host network-race profile`() {
        val config = DeviceDetector.detect(
            model = "2410CRP4CG",
            manufacturer = "Xiaomi",
        )

        assertEquals(RuntimeNetworkRole.HOST, config.networkRole)
        assertEquals(RuntimeOperatingMode.NETWORK_RACE, config.operatingMode)
        assertEquals("host_xiaomi", config.profile)
        assertEquals(true, config.isControllerOnlyHost)
    }

    @Test
    fun `detect maps oneplus to client network-race default profile`() {
        val config = DeviceDetector.detect(
            model = "CPH2399",
            manufacturer = "OnePlus",
        )

        assertEquals(RuntimeNetworkRole.CLIENT, config.networkRole)
        assertEquals(RuntimeOperatingMode.NETWORK_RACE, config.operatingMode)
        assertEquals("default", config.profile)
        assertEquals(false, config.isControllerOnlyHost)
    }

    @Test
    fun `detect maps unknown devices to single-device default profile`() {
        val config = DeviceDetector.detect(
            model = "SM-G991B",
            manufacturer = "Samsung",
        )

        assertEquals(RuntimeNetworkRole.NONE, config.networkRole)
        assertEquals(RuntimeOperatingMode.SINGLE_DEVICE, config.operatingMode)
        assertEquals("default", config.profile)
        assertEquals(false, config.isControllerOnlyHost)
    }
}
