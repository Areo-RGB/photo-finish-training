package com.paul.sprintsync

import com.paul.sprintsync.feature.race.domain.SessionCameraFacing

sealed interface MainAction {
    data object RequestPermissions : MainAction
    data object StartSingleDevice : MainAction
    data object StartDisplayHost : MainAction
    data object StartDisplayDiscovery : MainAction
    data class ConnectDisplayHost(val endpointId: String) : MainAction
    data class ResetDeviceTimer(val endpointId: String) : MainAction
    data class SetDisplayLimit(val endpointId: String, val limitMillis: Long) : MainAction
    data class SetAutoReadyDelay(val endpointId: String, val autoReadyDelaySeconds: Int?) : MainAction
    data class SetWaitTextEnabled(val endpointId: String, val enabled: Boolean) : MainAction
    data class SetDeviceSensitivity(val endpointId: String, val sensitivityPercent: Int) : MainAction
    data class SetGameModeEnabled(val endpointId: String, val enabled: Boolean) : MainAction
    data class SetGameModeLimit(val endpointId: String, val limitMillis: Long) : MainAction
    data class SetGameModeLives(val endpointId: String, val lives: Int) : MainAction
    data class SetGameModeAutoConfig(
        val endpointId: String,
        val enabled: Boolean,
        val everyRuns: Int,
    ) : MainAction
    data class SetMonitoringEnabled(val enabled: Boolean) : MainAction
    data object StopMonitoring : MainAction
    data object ResetRun : MainAction
    data class AssignCameraFacing(val deviceId: String, val facing: SessionCameraFacing) : MainAction
    data class UpdateThreshold(val value: Double) : MainAction
    data class UpdateRoiCenter(val value: Double) : MainAction
    data class UpdateRoiWidth(val value: Double) : MainAction
    data class UpdateCooldown(val value: Int) : MainAction
    data object OpenWifiSettings : MainAction
}
