package com.paul.sprintsync.features.race_session

import com.paul.sprintsync.core.models.LastRunResult
import com.paul.sprintsync.core.services.NearbyEvent
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RaceSessionControllerTest {
    private fun createController(
        savedRuns: MutableList<LastRunResult> = mutableListOf(),
        nowElapsedNanos: () -> Long = { 1_000_000_000L },
        loadedRun: LastRunResult? = null,
    ): RaceSessionController {
        return RaceSessionController(
            loadLastRun = { loadedRun },
            saveLastRun = { run -> savedRuns += run },
            sendMessage = { _, _, onComplete -> onComplete(Result.success(Unit)) },
            ioDispatcher = Dispatchers.Unconfined,
            nowElapsedNanos = nowElapsedNanos,
        )
    }

    @Test
    fun `start single device monitoring enters monitoring mode`() {
        val controller = createController()

        controller.startSingleDeviceMonitoring()

        val state = controller.uiState.value
        assertEquals(SessionOperatingMode.SINGLE_DEVICE, state.operatingMode)
        assertEquals(SessionStage.MONITORING, state.stage)
        assertTrue(state.monitoringActive)
        assertNotNull(state.runId)
    }

    @Test
    fun `stop single device monitoring returns to setup`() {
        val controller = createController()
        controller.startSingleDeviceMonitoring()

        controller.stopSingleDeviceMonitoring()

        val state = controller.uiState.value
        assertEquals(SessionStage.SETUP, state.stage)
        assertFalse(state.monitoringActive)
        assertEquals(null, state.runId)
    }

    @Test
    fun `local motion trigger captures start then stop and persists run`() {
        val savedRuns = mutableListOf<LastRunResult>()
        val controller = createController(savedRuns = savedRuns)
        controller.startSingleDeviceMonitoring()

        controller.onLocalMotionTrigger("start", splitIndex = 0, triggerSensorNanos = 100L)
        controller.onLocalMotionTrigger("stop", splitIndex = 0, triggerSensorNanos = 450L)

        val completed = controller.uiState.value.latestCompletedTimeline
        assertNotNull(completed)
        assertEquals(100L, completed.hostStartSensorNanos)
        assertEquals(450L, completed.hostStopSensorNanos)
        assertEquals(1, savedRuns.size)
        assertEquals(100L, savedRuns.single().startedSensorNanos)
        assertEquals(450L, savedRuns.single().stoppedSensorNanos)
    }

    @Test
    fun `reset run clears timeline and latest completed run`() {
        val controller = createController()
        controller.startSingleDeviceMonitoring()
        controller.onLocalMotionTrigger("start", splitIndex = 0, triggerSensorNanos = 200L)

        controller.resetRun()

        val state = controller.uiState.value
        assertEquals(null, state.timeline.hostStartSensorNanos)
        assertEquals(null, state.timeline.hostStopSensorNanos)
        assertEquals(null, state.latestCompletedTimeline)
    }

    @Test
    fun `estimate local sensor now applies local offset`() {
        val controller = createController(nowElapsedNanos = { 10_000L })

        controller.updateClockState(localSensorMinusElapsedNanos = 250L)

        assertEquals(10_250L, controller.estimateLocalSensorNanosNow())
    }

    @Test
    fun `set local device identity updates local device name`() {
        val controller = createController()

        controller.setLocalDeviceIdentity(deviceId = "dev-1", deviceName = "OnePlus")

        assertEquals("OnePlus", controller.localDeviceName())
        assertEquals(1, controller.totalDeviceCount())
    }

    @Test
    fun `start controller mode sets client display role`() {
        val controller = createController()

        controller.startControllerMode()

        val state = controller.uiState.value
        assertEquals(SessionOperatingMode.SINGLE_DEVICE, state.operatingMode)
        assertEquals(SessionNetworkRole.CLIENT, state.networkRole)
        assertEquals(SessionStage.MONITORING, state.stage)
        assertEquals(SessionDeviceRole.CONTROLLER, state.deviceRole)
        assertFalse(state.monitoringActive)
    }

    @Test
    fun `nearby connection event adds and removes non-local device`() {
        val controller = createController()

        controller.onNearbyEvent(
            NearbyEvent.ConnectionResult(
                endpointId = "ep-1",
                endpointName = "Lane 1",
                connected = true,
                statusCode = 0,
                statusMessage = null,
            ),
        )
        assertEquals(2, controller.totalDeviceCount())

        controller.onNearbyEvent(NearbyEvent.EndpointDisconnected(endpointId = "ep-1"))
        assertEquals(1, controller.totalDeviceCount())
    }
}