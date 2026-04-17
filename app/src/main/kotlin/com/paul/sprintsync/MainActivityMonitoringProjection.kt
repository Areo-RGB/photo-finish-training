package com.paul.sprintsync

import com.paul.sprintsync.feature.motion.domain.MotionDetectionConfig
import com.paul.sprintsync.feature.race.domain.SessionDevice
import com.paul.sprintsync.feature.race.domain.SessionDeviceRole
import com.paul.sprintsync.feature.race.domain.SessionNetworkRole
import com.paul.sprintsync.feature.race.domain.SessionOperatingMode
import com.paul.sprintsync.feature.race.domain.SessionRaceTimeline
import com.paul.sprintsync.feature.race.domain.SessionStage

internal data class MonitoringTriggerSnapshot(
    val triggerType: String,
    val triggerSensorNanos: Long,
    val score: Double,
)

internal data class MainActivityMonitoringProjectionInput(
    val mode: SessionOperatingMode,
    val networkRole: SessionNetworkRole,
    val stage: SessionStage,
    val monitoringActive: Boolean,
    val timeline: SessionRaceTimeline,
    val latestCompletedTimeline: SessionRaceTimeline?,
    val devices: List<SessionDevice>,
    val localRole: SessionDeviceRole,
    val isPassiveDisplayClient: Boolean,
    val userMonitoringEnabled: Boolean,
    val canStartMonitoring: Boolean,
    val connectedEndpoints: Set<String>,
    val currentNetworkRoleLabel: String,
    val displayConnectedHostEndpointId: String?,
    val displayConnectedHostName: String?,
    val displayDiscoveryActive: Boolean,
    val discoveredEndpoints: Map<String, String>,
    val controllerTargetEndpoints: Map<String, String>,
    val displayControllerEndpointIds: Set<String>,
    val displayHostDeviceNamesByEndpointId: Map<String, String>,
    val displayLatestLapByEndpointId: Map<String, Long>,
    val displayWaitingEndpointIds: Set<String>,
    val displayWaitingStartElapsedRealtimeNanosByEndpointId: Map<String, Long>,
    val displayWaitTextEnabledByEndpointId: Map<String, Boolean>,
    val displayLimitMillisByEndpointId: Map<String, Long>,
    val displayGameModeEnabledByEndpointId: Map<String, Boolean>,
    val displayGameModeLimitMillisByEndpointId: Map<String, Long>,
    val displayGameModeConfiguredLivesByEndpointId: Map<String, Int>,
    val displayGameModeCurrentLivesByEndpointId: Map<String, Int>,
    val connectedWifiSsid: String?,
    val targetMonitoringWifiSsid: String,
    val monitoringConfig: MotionDetectionConfig,
    val monitoringObservedFps: Double?,
    val monitoringTargetFpsUpper: Int?,
    val monitoringRawScore: Double?,
    val monitoringBaseline: Double?,
    val monitoringEffectiveScore: Double?,
    val monitoringLastFrameSensorNanos: Long?,
    val monitoringStreamFrameCount: Long,
    val monitoringProcessedFrameCount: Long,
    val monitoringIsActive: Boolean,
    val triggerHistory: List<MonitoringTriggerSnapshot>,
    val nowSensorNanos: Long,
    val nowDisplayElapsedRealtimeNanos: Long,
    val hostIpFallback: String,
    val hostPort: Int,
)

internal data class MainActivityMonitoringProjection(
    val stage: SessionStage,
    val operatingMode: SessionOperatingMode,
    val networkRole: SessionNetworkRole,
    val sessionSummary: String,
    val monitoringSummary: String,
    val userMonitoringEnabled: Boolean,
    val clockSummary: String,
    val startedSensorNanos: Long?,
    val stoppedSensorNanos: Long?,
    val devices: List<SessionDevice>,
    val canStartMonitoring: Boolean,
    val isHost: Boolean,
    val localRole: SessionDeviceRole,
    val monitoringConnectionTypeLabel: String,
    val hasConnectedPeers: Boolean,
    val wifiWarningText: String?,
    val runStatusLabel: String,
    val runMarksCount: Int,
    val elapsedDisplay: String,
    val threshold: Double,
    val roiCenterX: Double,
    val roiWidth: Double,
    val roiCenterY: Double,
    val roiHeight: Double,
    val cooldownMs: Int,
    val processEveryNFrames: Int,
    val observedFps: Double?,
    val cameraFpsModeLabel: String,
    val targetFpsUpper: Int?,
    val rawScore: Double?,
    val baseline: Double?,
    val effectiveScore: Double?,
    val frameSensorNanos: Long?,
    val streamFrameCount: Long,
    val processedFrameCount: Long,
    val triggerHistory: List<String>,
    val splitHistory: List<String>,
    val discoveredEndpoints: Map<String, String>,
    val connectedEndpoints: Set<String>,
    val networkSummary: String,
    val displayLapRows: List<DisplayLapRow>,
    val displayConnectedHostName: String?,
    val displayConnectedHostEndpointId: String?,
    val displayDiscoveryActive: Boolean,
    val controllerTargetEndpoints: Map<String, String>,
)

internal fun buildMainActivityMonitoringProjection(
    input: MainActivityMonitoringProjectionInput,
): MainActivityMonitoringProjection {
    val timelineForUi = if (
        input.mode == SessionOperatingMode.SINGLE_DEVICE &&
        input.timeline.hostStartSensorNanos == null &&
        input.latestCompletedTimeline != null
    ) {
        input.latestCompletedTimeline
    } else {
        input.timeline
    }

    val monitoringSummary = if (input.monitoringIsActive) {
        "Monitoring"
    } else {
        "Idle"
    }
    val isHost = input.networkRole == SessionNetworkRole.HOST || input.mode == SessionOperatingMode.DISPLAY_HOST
    val liveConnectedEndpoints = when (input.mode) {
        SessionOperatingMode.SINGLE_DEVICE -> setOfNotNull(input.displayConnectedHostEndpointId)
        SessionOperatingMode.DISPLAY_HOST -> input.connectedEndpoints
    }
    val hasPeers = liveConnectedEndpoints.isNotEmpty()
    val wifiWarningText = when {
        isConnectedToExpectedWifi(input.connectedWifiSsid, input.targetMonitoringWifiSsid) -> null
        input.connectedWifiSsid == null -> "Connect to Wi-Fi \"${input.targetMonitoringWifiSsid}\"."
        else -> "Connected to \"${input.connectedWifiSsid}\". Use \"${input.targetMonitoringWifiSsid}\"."
    }
    val runStatusLabel = when {
        timelineForUi.hostStartSensorNanos == null -> "Ready"
        timelineForUi.hostStopSensorNanos != null -> "Finished"
        input.monitoringActive -> "Running"
        else -> "Armed"
    }
    val marksCount = timelineForUi.hostSplitSensorNanos.size + if (timelineForUi.hostStopSensorNanos != null) 1 else 0
    val elapsedDisplay = formatProjectionElapsedDisplay(
        startedSensorNanos = timelineForUi.hostStartSensorNanos,
        stoppedSensorNanos = timelineForUi.hostStopSensorNanos,
        monitoringActive = input.monitoringActive,
        nowSensorNanos = input.nowSensorNanos,
    )
    val cameraModeLabel = if (input.isPassiveDisplayClient) {
        "-"
    } else if (input.monitoringObservedFps == null) {
        "INIT"
    } else {
        "NORMAL"
    }
    val triggerHistory = if (input.isPassiveDisplayClient) {
        emptyList()
    } else {
        input.triggerHistory.map { trigger ->
            val roleLabel = when (trigger.triggerType.lowercase()) {
                "start" -> "START"
                "stop" -> "STOP"
                else -> trigger.triggerType.uppercase()
            }
            "$roleLabel at ${trigger.triggerSensorNanos}ns (score ${"%.4f".format(trigger.score)})"
        }
    }
    val splitHistory = buildSplitHistoryForTimeline(
        startedSensorNanos = timelineForUi.hostStartSensorNanos,
        splitSensorNanos = timelineForUi.hostSplitSensorNanos,
    )
    val displayEndpointIdsForRows = if (input.mode == SessionOperatingMode.DISPLAY_HOST) {
        input.connectedEndpoints.filterNot { endpointId ->
            input.displayControllerEndpointIds.contains(endpointId) ||
                isControllerEndpointName(input.displayHostDeviceNamesByEndpointId[endpointId])
        }.toSet()
    } else {
        input.connectedEndpoints
    }
    val displayLapRows = buildDisplayLapRowsForConnectedDevices(
        connectedEndpointIds = displayEndpointIdsForRows,
        deviceNamesByEndpointId = input.displayHostDeviceNamesByEndpointId,
        elapsedByEndpointId = input.displayLatestLapByEndpointId,
        waitingEndpointIds = input.displayWaitingEndpointIds,
        waitingStartElapsedRealtimeNanosByEndpointId = input.displayWaitingStartElapsedRealtimeNanosByEndpointId,
        waitTextEnabledByEndpointId = input.displayWaitTextEnabledByEndpointId,
        limitMillisByEndpointId = input.displayLimitMillisByEndpointId,
        gameModeEnabledByEndpointId = input.displayGameModeEnabledByEndpointId,
        gameModeLimitMillisByEndpointId = input.displayGameModeLimitMillisByEndpointId,
        gameModeConfiguredLivesByEndpointId = input.displayGameModeConfiguredLivesByEndpointId,
        gameModeCurrentLivesByEndpointId = input.displayGameModeCurrentLivesByEndpointId,
        hostStartSensorNanos = timelineForUi.hostStartSensorNanos,
        hostStopSensorNanos = timelineForUi.hostStopSensorNanos,
        monitoringActive = input.monitoringActive,
        nowSensorNanos = input.nowSensorNanos,
        nowDisplayElapsedRealtimeNanos = input.nowDisplayElapsedRealtimeNanos,
    )

    return MainActivityMonitoringProjection(
        stage = input.stage,
        operatingMode = input.mode,
        networkRole = input.networkRole,
        sessionSummary = input.stage.name.lowercase(),
        monitoringSummary = monitoringSummary,
        userMonitoringEnabled = input.userMonitoringEnabled,
        clockSummary = if (hasPeers) "Local authority" else "Standalone",
        startedSensorNanos = timelineForUi.hostStartSensorNanos,
        stoppedSensorNanos = timelineForUi.hostStopSensorNanos,
        devices = input.devices,
        canStartMonitoring = input.mode == SessionOperatingMode.SINGLE_DEVICE && input.canStartMonitoring,
        isHost = isHost,
        localRole = input.localRole,
        monitoringConnectionTypeLabel = resolveMonitoringConnectionTypeLabel(
            hasPeers = hasPeers,
            hostIp = input.displayConnectedHostEndpointId ?: input.hostIpFallback,
            hostPort = input.hostPort,
        ),
        hasConnectedPeers = hasPeers,
        wifiWarningText = wifiWarningText,
        runStatusLabel = runStatusLabel,
        runMarksCount = marksCount,
        elapsedDisplay = elapsedDisplay,
        threshold = input.monitoringConfig.threshold,
        roiCenterX = input.monitoringConfig.roiCenterX,
        roiWidth = input.monitoringConfig.roiWidth,
        roiCenterY = input.monitoringConfig.roiCenterY,
        roiHeight = input.monitoringConfig.roiHeight,
        cooldownMs = input.monitoringConfig.cooldownMs,
        processEveryNFrames = input.monitoringConfig.processEveryNFrames,
        observedFps = input.monitoringObservedFps,
        cameraFpsModeLabel = cameraModeLabel,
        targetFpsUpper = input.monitoringTargetFpsUpper,
        rawScore = input.monitoringRawScore,
        baseline = input.monitoringBaseline,
        effectiveScore = input.monitoringEffectiveScore,
        frameSensorNanos = input.monitoringLastFrameSensorNanos,
        streamFrameCount = input.monitoringStreamFrameCount,
        processedFrameCount = input.monitoringProcessedFrameCount,
        triggerHistory = triggerHistory,
        splitHistory = splitHistory,
        discoveredEndpoints = input.discoveredEndpoints,
        connectedEndpoints = liveConnectedEndpoints,
        networkSummary = "${input.currentNetworkRoleLabel} mode, ${liveConnectedEndpoints.size} connected",
        displayLapRows = displayLapRows,
        displayConnectedHostName = input.displayConnectedHostName,
        displayConnectedHostEndpointId = input.displayConnectedHostEndpointId,
        displayDiscoveryActive = input.displayDiscoveryActive,
        controllerTargetEndpoints = input.controllerTargetEndpoints,
    )
}

private fun formatProjectionElapsedDisplay(
    startedSensorNanos: Long?,
    stoppedSensorNanos: Long?,
    monitoringActive: Boolean,
    nowSensorNanos: Long,
): String {
    val started = startedSensorNanos ?: return "00.00"
    val terminal = stoppedSensorNanos ?: if (monitoringActive) {
        nowSensorNanos
    } else {
        started
    }
    val elapsedNanos = (terminal - started).coerceAtLeast(0L)
    val totalMillis = elapsedNanos / 1_000_000L
    return formatElapsedTimerDisplay(totalMillis)
}

private fun isConnectedToExpectedWifi(currentSsid: String?, expectedSsid: String): Boolean {
    val current = normalizeWifiSsid(currentSsid) ?: return false
    val expected = normalizeWifiSsid(expectedSsid) ?: return false
    return current == expected
}

private fun normalizeWifiSsid(rawSsid: String?): String? {
    val trimmed = rawSsid?.trim()?.trim('"').orEmpty()
    return trimmed.takeIf { it.isNotEmpty() && it != "<unknown ssid>" }
}
