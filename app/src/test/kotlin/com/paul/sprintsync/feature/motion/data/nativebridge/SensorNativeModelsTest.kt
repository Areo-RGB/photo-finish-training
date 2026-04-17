package com.paul.sprintsync.feature.motion.data.nativebridge

import org.junit.Assert.assertEquals
import org.junit.Test

class SensorNativeModelsTest {
    @Test
    fun `fromMap falls back to defaults for new roi y fields when absent`() {
        val parsed = NativeMonitoringConfig.fromMap(
            mapOf(
                "threshold" to 0.01,
                "roiCenterX" to 0.6,
                "roiWidth" to 0.05,
                "cooldownMs" to 800,
                "processEveryNFrames" to 2,
                "cameraFacing" to "rear",
            ),
        )

        assertEquals(0.6, parsed.roiCenterX, 0.0)
        assertEquals(0.05, parsed.roiWidth, 0.0)
        assertEquals(0.5, parsed.roiCenterY, 0.0)
        assertEquals(0.03, parsed.roiHeight, 0.0)
    }

    @Test
    fun `fromMap parses explicit roi y fields`() {
        val parsed = NativeMonitoringConfig.fromMap(
            mapOf(
                "threshold" to 0.01,
                "roiCenterX" to 0.6,
                "roiWidth" to 0.05,
                "roiCenterY" to 0.72,
                "roiHeight" to 0.12,
                "cooldownMs" to 800,
                "processEveryNFrames" to 2,
                "cameraFacing" to "rear",
            ),
        )

        assertEquals(0.72, parsed.roiCenterY, 0.0)
        assertEquals(0.12, parsed.roiHeight, 0.0)
    }
}
