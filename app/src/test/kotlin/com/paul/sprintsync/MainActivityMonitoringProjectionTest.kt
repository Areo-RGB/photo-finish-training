package com.paul.sprintsync

import com.paul.sprintsync.feature.motion.domain.MotionDetectionConfig
import com.paul.sprintsync.feature.race.domain.SessionDevice
import com.paul.sprintsync.feature.race.domain.SessionDeviceRole
import com.paul.sprintsync.feature.race.domain.SessionNetworkRole
import com.paul.sprintsync.feature.race.domain.SessionOperatingMode
import com.paul.sprintsync.feature.race.domain.SessionRaceTimeline
import com.paul.sprintsync.feature.race.domain.SessionStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityMonitoringProjectionTest {
    @Test
    fun `single device projection without host keeps connected endpoints empty`() {
        val projection = buildMainActivityMonitoringProjection(
            input = baseInput(
                mode = SessionOperatingMode.SINGLE_DEVICE,
                displayConnectedHostEndpointId = null,
                connectedEndpoints = setOf("remote-1"),
            ),
        )

        assertTrue(projection.connectedEndpoints.isEmpty())
        assertEquals("tcp mode, 0 connected", projection.networkSummary)
    }

    @Test
    fun `single device projection with connected host exposes one endpoint`() {
        val projection = buildMainActivityMonitoringProjection(
            input = baseInput(
                mode = SessionOperatingMode.SINGLE_DEVICE,
                displayConnectedHostEndpointId = "host-1",
                connectedEndpoints = setOf("host-1", "remote-1"),
            ),
        )

        assertEquals(setOf("host-1"), projection.connectedEndpoints)
        assertEquals("tcp mode, 1 connected", projection.networkSummary)
    }

    @Test
    fun `display host projection shows wait row then completed row`() {
        val waiting = buildMainActivityMonitoringProjection(
            input = baseInput(
                mode = SessionOperatingMode.DISPLAY_HOST,
                connectedEndpoints = setOf("runner-1"),
                displayHostDeviceNamesByEndpointId = mapOf("runner-1" to "Pixel 7"),
                displayWaitingEndpointIds = setOf("runner-1"),
                displayWaitTextEnabledByEndpointId = mapOf("runner-1" to true),
                displayGameModeEnabledByEndpointId = mapOf("runner-1" to false),
                monitoringActive = true,
                timeline = SessionRaceTimeline(hostStartSensorNanos = 1_000_000_000L),
                nowSensorNanos = 2_000_000_000L,
                nowDisplayElapsedRealtimeNanos = 2_000_000_000L,
            ),
        )
        assertEquals(1, waiting.displayLapRows.size)
        assertEquals("WAIT", waiting.displayLapRows.first().lapTimeLabel)
        assertTrue(waiting.displayLapRows.first().isWaiting)

        val completed = buildMainActivityMonitoringProjection(
            input = baseInput(
                mode = SessionOperatingMode.DISPLAY_HOST,
                connectedEndpoints = setOf("runner-1"),
                displayHostDeviceNamesByEndpointId = mapOf("runner-1" to "Pixel 7"),
                displayLatestLapByEndpointId = mapOf("runner-1" to 1_500_000_000L),
                displayGameModeEnabledByEndpointId = mapOf("runner-1" to false),
                monitoringActive = false,
                timeline = SessionRaceTimeline(
                    hostStartSensorNanos = 1_000_000_000L,
                    hostStopSensorNanos = 2_500_000_000L,
                ),
            ),
        )
        assertEquals(1, completed.displayLapRows.size)
        assertEquals("01.50", completed.displayLapRows.first().lapTimeLabel)
        assertFalse(completed.displayLapRows.first().isWaiting)
    }

    @Test
    fun `passive display client projection suppresses trigger history and camera mode`() {
        val projection = buildMainActivityMonitoringProjection(
            input = baseInput(
                mode = SessionOperatingMode.SINGLE_DEVICE,
                networkRole = SessionNetworkRole.CLIENT,
                localRole = SessionDeviceRole.DISPLAY,
                isPassiveDisplayClient = true,
                monitoringObservedFps = 30.0,
                triggerHistory = listOf(
                    MonitoringTriggerSnapshot(
                        triggerType = "start",
                        triggerSensorNanos = 1_000_000_000L,
                        score = 0.12,
                    ),
                ),
            ),
        )

        assertEquals("-", projection.cameraFpsModeLabel)
        assertTrue(projection.triggerHistory.isEmpty())
    }

    private fun baseInput(
        mode: SessionOperatingMode = SessionOperatingMode.SINGLE_DEVICE,
        networkRole: SessionNetworkRole = SessionNetworkRole.NONE,
        stage: SessionStage = SessionStage.MONITORING,
        monitoringActive: Boolean = false,
        timeline: SessionRaceTimeline = SessionRaceTimeline(),
        latestCompletedTimeline: SessionRaceTimeline? = null,
        localRole: SessionDeviceRole = SessionDeviceRole.UNASSIGNED,
        isPassiveDisplayClient: Boolean = false,
        connectedEndpoints: Set<String> = emptySet(),
        displayConnectedHostEndpointId: String? = null,
        displayHostDeviceNamesByEndpointId: Map<String, String> = emptyMap(),
        displayLatestLapByEndpointId: Map<String, Long> = emptyMap(),
        displayWaitingEndpointIds: Set<String> = emptySet(),
        displayWaitTextEnabledByEndpointId: Map<String, Boolean> = emptyMap(),
        displayGameModeEnabledByEndpointId: Map<String, Boolean> = emptyMap(),
        nowSensorNanos: Long = 0L,
        nowDisplayElapsedRealtimeNanos: Long = nowSensorNanos,
        monitoringObservedFps: Double? = null,
        triggerHistory: List<MonitoringTriggerSnapshot> = emptyList(),
    ): MainActivityMonitoringProjectionInput {
        return MainActivityMonitoringProjectionInput(
            mode = mode,
            networkRole = networkRole,
            stage = stage,
            monitoringActive = monitoringActive,
            timeline = timeline,
            latestCompletedTimeline = latestCompletedTimeline,
            devices = listOf(
                SessionDevice(
                    id = "local-1",
                    name = "Local",
                    role = localRole,
                    isLocal = true,
                ),
            ),
            localRole = localRole,
            isPassiveDisplayClient = isPassiveDisplayClient,
            userMonitoringEnabled = true,
            canStartMonitoring = true,
            connectedEndpoints = connectedEndpoints,
            currentNetworkRoleLabel = "tcp",
            displayConnectedHostEndpointId = displayConnectedHostEndpointId,
            displayConnectedHostName = null,
            displayDiscoveryActive = false,
            discoveredEndpoints = emptyMap(),
            controllerTargetEndpoints = emptyMap(),
            displayControllerEndpointIds = emptySet(),
            displayHostDeviceNamesByEndpointId = displayHostDeviceNamesByEndpointId,
            displayLatestLapByEndpointId = displayLatestLapByEndpointId,
            displayWaitingEndpointIds = displayWaitingEndpointIds,
            displayWaitingStartElapsedRealtimeNanosByEndpointId = emptyMap(),
            displayWaitTextEnabledByEndpointId = displayWaitTextEnabledByEndpointId,
            displayLimitMillisByEndpointId = emptyMap(),
            displayGameModeEnabledByEndpointId = displayGameModeEnabledByEndpointId,
            displayGameModeLimitMillisByEndpointId = emptyMap(),
            displayGameModeConfiguredLivesByEndpointId = emptyMap(),
            displayGameModeCurrentLivesByEndpointId = emptyMap(),
            connectedWifiSsid = null,
            targetMonitoringWifiSsid = "TP-Link_86CA_5G",
            monitoringConfig = MotionDetectionConfig.defaults(),
            monitoringObservedFps = monitoringObservedFps,
            monitoringTargetFpsUpper = null,
            monitoringRawScore = null,
            monitoringBaseline = null,
            monitoringEffectiveScore = null,
            monitoringLastFrameSensorNanos = null,
            monitoringStreamFrameCount = 0L,
            monitoringProcessedFrameCount = 0L,
            monitoringIsActive = monitoringObservedFps != null,
            triggerHistory = triggerHistory,
            nowSensorNanos = nowSensorNanos,
            nowDisplayElapsedRealtimeNanos = nowDisplayElapsedRealtimeNanos,
            hostIpFallback = "192.168.0.1",
            hostPort = 4545,
        )
    }
}
