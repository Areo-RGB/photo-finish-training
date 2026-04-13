package com.paul.sprintsync.sensor_native

sealed interface SensorNativeEvent {
    data class FrameStats(
        val stats: NativeFrameStats,
        val streamFrameCount: Long,
        val processedFrameCount: Long,
        val observedFps: Double?,
        val cameraFpsMode: NativeCameraFpsMode,
        val targetFpsUpper: Int?,
    ) : SensorNativeEvent

    data class Trigger(
        val trigger: NativeTriggerEvent,
    ) : SensorNativeEvent

    data class State(
        val state: String,
        val monitoring: Boolean,
    ) : SensorNativeEvent

    data class Diagnostic(
        val message: String,
    ) : SensorNativeEvent

    data class Error(
        val message: String,
    ) : SensorNativeEvent
}
