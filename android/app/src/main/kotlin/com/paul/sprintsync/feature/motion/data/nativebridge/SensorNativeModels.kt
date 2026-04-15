package com.paul.sprintsync.feature.motion.data.nativebridge

import kotlin.math.max
import kotlin.math.min

enum class NativeCameraFacing(val wireName: String) {
    REAR("rear"),
    FRONT("front"),
}

enum class NativeCameraFpsMode(val wireName: String) {
    NORMAL("normal"),
    HS120("hs120"),
}

data class NativeMonitoringConfig(
    val threshold: Double,
    val roiCenterX: Double,
    val roiWidth: Double,
    val roiCenterY: Double,
    val roiHeight: Double,
    val cooldownMs: Int,
    val processEveryNFrames: Int,
    val cameraFacing: NativeCameraFacing,
) {
    companion object {
        fun defaults(): NativeMonitoringConfig {
            return NativeMonitoringConfig(
                threshold = 0.006,
                roiCenterX = 0.5,
                roiWidth = 0.03,
                roiCenterY = 0.5,
                roiHeight = 0.03,
                cooldownMs = 900,
                processEveryNFrames = 1,
                cameraFacing = NativeCameraFacing.REAR,
            )
        }

        fun fromMap(raw: Any?): NativeMonitoringConfig {
            if (raw !is Map<*, *>) {
                return defaults()
            }
            val defaults = defaults()
            return NativeMonitoringConfig(
                threshold = clampDouble(
                    (raw["threshold"] as? Number)?.toDouble() ?: defaults.threshold,
                    0.001,
                    0.08,
                ),
                roiCenterX = clampDouble(
                    (raw["roiCenterX"] as? Number)?.toDouble() ?: defaults.roiCenterX,
                    0.20,
                    0.80,
                ),
                roiWidth = clampDouble(
                    (raw["roiWidth"] as? Number)?.toDouble() ?: defaults.roiWidth,
                    0.01,
                    0.40,
                ),
                roiCenterY = clampDouble(
                    (raw["roiCenterY"] as? Number)?.toDouble() ?: defaults.roiCenterY,
                    0.20,
                    0.80,
                ),
                roiHeight = clampDouble(
                    (raw["roiHeight"] as? Number)?.toDouble() ?: defaults.roiHeight,
                    0.01,
                    0.40,
                ),
                cooldownMs = clampInt(
                    (raw["cooldownMs"] as? Number)?.toInt() ?: defaults.cooldownMs,
                    300,
                    2000,
                ),
                processEveryNFrames = clampInt(
                    (raw["processEveryNFrames"] as? Number)?.toInt()
                        ?: defaults.processEveryNFrames,
                    1,
                    5,
                ),
                cameraFacing = nativeCameraFacingFromWire(
                    raw["cameraFacing"]?.toString(),
                ) ?: defaults.cameraFacing,
            )
        }
    }
}

data class NativeTriggerEvent(
    val triggerSensorNanos: Long,
    val score: Double,
    val triggerType: String,
    val splitIndex: Int,
)

data class NativeFrameStats(
    val rawScore: Double,
    val baseline: Double,
    val effectiveScore: Double,
    val frameSensorNanos: Long,
    val triggerEvent: NativeTriggerEvent?,
)

private fun clampDouble(value: Double, minValue: Double, maxValue: Double): Double {
    return min(max(value, minValue), maxValue)
}

private fun clampInt(value: Int, minValue: Int, maxValue: Int): Int {
    return min(max(value, minValue), maxValue)
}

internal fun nativeCameraFacingFromWire(value: String?): NativeCameraFacing? {
    return NativeCameraFacing.values().firstOrNull { it.wireName == value }
}
