package com.paul.sprintsync.feature.motion.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MotionDetectionModelsTest {
    @Test
    fun `fromJsonString falls back to defaults for new roi y fields when absent`() {
        val legacyJson = """
            {
              "threshold": 0.01,
              "roiCenterX": 0.6,
              "roiWidth": 0.05,
              "cooldownMs": 800,
              "processEveryNFrames": 2,
              "cameraFacing": "rear"
            }
        """.trimIndent()

        val parsed = MotionDetectionConfig.fromJsonString(legacyJson)

        assertEquals(0.6, parsed.roiCenterX, 0.0)
        assertEquals(0.05, parsed.roiWidth, 0.0)
        assertEquals(0.5, parsed.roiCenterY, 0.0)
        assertEquals(0.03, parsed.roiHeight, 0.0)
    }

    @Test
    fun `toJsonString and fromJsonString round-trip roi y fields`() {
        val original = MotionDetectionConfig.defaults().copy(
            roiCenterY = 0.72,
            roiHeight = 0.12,
        )

        val parsed = MotionDetectionConfig.fromJsonString(original.toJsonString())

        assertEquals(original.roiCenterY, parsed.roiCenterY, 0.0)
        assertEquals(original.roiHeight, parsed.roiHeight, 0.0)
    }
}
