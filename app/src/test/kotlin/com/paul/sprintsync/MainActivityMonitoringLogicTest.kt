package com.paul.sprintsync

import com.paul.sprintsync.feature.race.domain.SessionOperatingMode
import com.paul.sprintsync.feature.race.domain.SessionDeviceRole
import com.paul.sprintsync.feature.race.domain.SessionNetworkRole
import com.paul.sprintsync.feature.race.domain.SessionStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityMonitoringLogicTest {
    @Test
    fun `starts local capture when monitoring active resumed assigned and local capture is idle`() {
        val action = resolveLocalCaptureAction(
            monitoringActive = true,
            isAppResumed = true,
            shouldRunLocalCapture = true,
            isLocalMotionMonitoring = false,
            localCaptureStartPending = false,
        )

        assertEquals(LocalCaptureAction.START, action)
    }

    @Test
    fun `stops local capture when app pauses during monitoring`() {
        val action = resolveLocalCaptureAction(
            monitoringActive = true,
            isAppResumed = false,
            shouldRunLocalCapture = true,
            isLocalMotionMonitoring = true,
            localCaptureStartPending = false,
        )

        assertEquals(LocalCaptureAction.STOP, action)
    }

    @Test
    fun `stops local capture when local role becomes unassigned during monitoring`() {
        val action = resolveLocalCaptureAction(
            monitoringActive = true,
            isAppResumed = true,
            shouldRunLocalCapture = false,
            isLocalMotionMonitoring = true,
            localCaptureStartPending = false,
        )

        assertEquals(LocalCaptureAction.STOP, action)
    }

    @Test
    fun `keeps local capture unchanged when monitoring state is already satisfied`() {
        val action = resolveLocalCaptureAction(
            monitoringActive = true,
            isAppResumed = true,
            shouldRunLocalCapture = true,
            isLocalMotionMonitoring = true,
            localCaptureStartPending = false,
        )

        assertEquals(LocalCaptureAction.NONE, action)
    }

    @Test
    fun `timer refresh runs only during active in-progress resumed monitoring`() {
        assertTrue(
            shouldKeepTimerRefreshActive(
                monitoringActive = true,
                isAppResumed = true,
                hasStopSensor = false,
            ),
        )
        assertFalse(
            shouldKeepTimerRefreshActive(
                monitoringActive = true,
                isAppResumed = false,
                hasStopSensor = false,
            ),
        )
        assertFalse(
            shouldKeepTimerRefreshActive(
                monitoringActive = true,
                isAppResumed = true,
                hasStopSensor = true,
            ),
        )
    }

    @Test
    fun `does not start capture again while start is pending`() {
        val action = resolveLocalCaptureAction(
            monitoringActive = true,
            isAppResumed = true,
            shouldRunLocalCapture = true,
            isLocalMotionMonitoring = false,
            localCaptureStartPending = true,
        )

        assertEquals(LocalCaptureAction.NONE, action)
    }

    @Test
    fun `does not start local capture when user monitoring toggle is off`() {
        val action = resolveLocalCaptureAction(
            monitoringActive = true,
            isAppResumed = true,
            shouldRunLocalCapture = false,
            isLocalMotionMonitoring = false,
            localCaptureStartPending = false,
        )

        assertEquals(LocalCaptureAction.NONE, action)
    }

    @Test
    fun `stops local capture when user monitoring toggle is turned off during monitoring`() {
        val action = resolveLocalCaptureAction(
            monitoringActive = true,
            isAppResumed = true,
            shouldRunLocalCapture = false,
            isLocalMotionMonitoring = true,
            localCaptureStartPending = false,
        )

        assertEquals(LocalCaptureAction.STOP, action)
    }

    @Test
    fun `re-enabling user monitoring toggle allows local capture start when guards are met`() {
        val action = resolveLocalCaptureAction(
            monitoringActive = true,
            isAppResumed = true,
            shouldRunLocalCapture = true,
            isLocalMotionMonitoring = false,
            localCaptureStartPending = false,
        )

        assertEquals(LocalCaptureAction.START, action)
    }

    @Test
    fun `display host mode prefers landscape orientation`() {
        assertFalse(shouldUseLandscapeForMode(SessionOperatingMode.DISPLAY_HOST))
        assertFalse(shouldUseLandscapeForMode(SessionOperatingMode.SINGLE_DEVICE))
    }

    @Test
    fun `display host mode uses immersive fullscreen and other modes do not`() {
        assertTrue(shouldUseImmersiveModeForMode(SessionOperatingMode.DISPLAY_HOST))
        assertFalse(shouldUseImmersiveModeForMode(SessionOperatingMode.SINGLE_DEVICE))
    }

    @Test
    fun `timer display uses ss cc below one minute and no three-digit milliseconds`() {
        assertEquals("00.00", formatElapsedTimerDisplay(totalMillis = 0))
        assertEquals("01.67", formatElapsedTimerDisplay(totalMillis = 1_678))
        assertEquals("59.99", formatElapsedTimerDisplay(totalMillis = 59_999))
    }

    @Test
    fun `timer display prepends minutes from one minute onward with centiseconds`() {
        assertEquals("01:00.00", formatElapsedTimerDisplay(totalMillis = 60_000))
        assertEquals("02:05.43", formatElapsedTimerDisplay(totalMillis = 125_432))
    }

    @Test
    fun `split history renders ordered split labels with elapsed time`() {
        val history = buildSplitHistoryForTimeline(
            startedSensorNanos = 1_000_000_000L,
            splitSensorNanos = listOf(11_000_000_000L, 21_000_000_000L),
        )

        assertEquals(listOf("Split 1: 10.00", "Split 2: 20.00"), history)
    }

    @Test
    fun `applies live local camera facing update when local monitoring active`() {
        assertTrue(
            shouldApplyLiveLocalCameraFacingUpdate(
                isLocalMotionMonitoring = true,
                assignedDeviceId = "local-1",
                localDeviceId = "local-1",
            ),
        )
    }

    @Test
    fun `does not apply live local camera facing update when monitoring inactive`() {
        assertFalse(
            shouldApplyLiveLocalCameraFacingUpdate(
                isLocalMotionMonitoring = false,
                assignedDeviceId = "local-1",
                localDeviceId = "local-1",
            ),
        )
    }

    @Test
    fun `does not apply live local camera facing update for non local device`() {
        assertFalse(
            shouldApplyLiveLocalCameraFacingUpdate(
                isLocalMotionMonitoring = true,
                assignedDeviceId = "remote-1",
                localDeviceId = "local-1",
            ),
        )
    }

    @Test
    fun `display rows show READY for connected endpoints with no lap yet`() {
        val rows = buildDisplayLapRowsForConnectedDevices(
            connectedEndpointIds = linkedSetOf("ep-1"),
            deviceNamesByEndpointId = mapOf("ep-1" to "Pixel 7"),
            elapsedByEndpointId = emptyMap(),
            limitMillisByEndpointId = emptyMap(),
            gameModeEnabledByEndpointId = mapOf("ep-1" to false),
            hostStartSensorNanos = null,
            hostStopSensorNanos = null,
            monitoringActive = false,
            nowSensorNanos = 0L,
        )

        assertEquals(1, rows.size)
        assertEquals("Pixel 7", rows[0].deviceName)
        assertEquals("READY", rows[0].lapTimeLabel)
    }

    @Test
    fun `display rows show WAIT when endpoint has started but no final lap yet`() {
        val rows = buildDisplayLapRowsForConnectedDevices(
            connectedEndpointIds = linkedSetOf("ep-1"),
            deviceNamesByEndpointId = mapOf("ep-1" to "Pixel 7"),
            elapsedByEndpointId = emptyMap(),
            waitingEndpointIds = linkedSetOf("ep-1"),
            waitTextEnabledByEndpointId = mapOf("ep-1" to true),
            limitMillisByEndpointId = emptyMap(),
            gameModeEnabledByEndpointId = mapOf("ep-1" to false),
            hostStartSensorNanos = 1_000_000_000L,
            hostStopSensorNanos = null,
            monitoringActive = true,
            nowSensorNanos = 2_730_000_000L,
        )

        assertEquals(1, rows.size)
        assertEquals("WAIT", rows[0].lapTimeLabel)
        assertEquals(true, rows[0].isWaiting)
    }

    @Test
    fun `display rows show local timer when wait text mode is disabled`() {
        val rows = buildDisplayLapRowsForConnectedDevices(
            connectedEndpointIds = linkedSetOf("ep-1"),
            deviceNamesByEndpointId = mapOf("ep-1" to "Pixel 7"),
            elapsedByEndpointId = emptyMap(),
            waitingEndpointIds = linkedSetOf("ep-1"),
            waitingStartElapsedRealtimeNanosByEndpointId = mapOf("ep-1" to 1_000_000_000L),
            waitTextEnabledByEndpointId = mapOf("ep-1" to false),
            limitMillisByEndpointId = emptyMap(),
            gameModeEnabledByEndpointId = mapOf("ep-1" to false),
            hostStartSensorNanos = null,
            hostStopSensorNanos = null,
            monitoringActive = false,
            nowSensorNanos = 0L,
            nowDisplayElapsedRealtimeNanos = 2_500_000_000L,
        )

        assertEquals(1, rows.size)
        assertEquals(formatElapsedTimerDisplay(1_500L), rows[0].lapTimeLabel)
        assertEquals(false, rows[0].isWaiting)
    }

    @Test
    fun `display rows prefer final lap over WAIT marker`() {
        val rows = buildDisplayLapRowsForConnectedDevices(
            connectedEndpointIds = linkedSetOf("ep-1"),
            deviceNamesByEndpointId = mapOf("ep-1" to "Pixel 7"),
            elapsedByEndpointId = mapOf("ep-1" to 1_730_000_000L),
            waitingEndpointIds = linkedSetOf("ep-1"),
            limitMillisByEndpointId = emptyMap(),
            gameModeEnabledByEndpointId = mapOf("ep-1" to false),
            hostStartSensorNanos = 1_000_000_000L,
            hostStopSensorNanos = null,
            monitoringActive = true,
            nowSensorNanos = 2_730_000_000L,
        )

        assertEquals(1, rows.size)
        assertEquals(formatElapsedTimerDisplay(1_730L), rows[0].lapTimeLabel)
        assertEquals(false, rows[0].isWaiting)
    }

    @Test
    fun `display rows show formatted lap for connected endpoints with lap`() {
        val rows = buildDisplayLapRowsForConnectedDevices(
            connectedEndpointIds = linkedSetOf("ep-1"),
            deviceNamesByEndpointId = mapOf("ep-1" to "Pixel 7"),
            elapsedByEndpointId = mapOf("ep-1" to 1_730_000_000L),
            limitMillisByEndpointId = emptyMap(),
            gameModeEnabledByEndpointId = mapOf("ep-1" to false),
            hostStartSensorNanos = 1_000_000_000L,
            hostStopSensorNanos = null,
            monitoringActive = true,
            nowSensorNanos = 4_000_000_000L,
        )

        assertEquals(1, rows.size)
        assertEquals("Pixel 7", rows[0].deviceName)
        assertEquals(formatElapsedTimerDisplay(1_730L), rows[0].lapTimeLabel)
    }

    @Test
    fun `display rows include mixed connected devices with lap and READY`() {
        val rows = buildDisplayLapRowsForConnectedDevices(
            connectedEndpointIds = linkedSetOf("ep-1", "ep-2"),
            deviceNamesByEndpointId = mapOf("ep-1" to "Pixel 7", "ep-2" to "CPH2399"),
            elapsedByEndpointId = mapOf("ep-1" to 1_730_000_000L),
            limitMillisByEndpointId = emptyMap(),
            gameModeEnabledByEndpointId = mapOf("ep-1" to false, "ep-2" to false),
            hostStartSensorNanos = null,
            hostStopSensorNanos = null,
            monitoringActive = false,
            nowSensorNanos = 0L,
        )

        assertEquals(2, rows.size)
        assertEquals("Pixel 7", rows[0].deviceName)
        assertEquals(formatElapsedTimerDisplay(1_730L), rows[0].lapTimeLabel)
        assertEquals("CPH2399", rows[1].deviceName)
        assertEquals("READY", rows[1].lapTimeLabel)
    }

    @Test
    fun `display rows keep Pixel 7 endpoint first when connected`() {
        val rows = buildDisplayLapRowsForConnectedDevices(
            connectedEndpointIds = linkedSetOf("ep-2", "ep-1"),
            deviceNamesByEndpointId = mapOf("ep-1" to "Pixel 7", "ep-2" to "EML-L29"),
            elapsedByEndpointId = emptyMap(),
            limitMillisByEndpointId = emptyMap(),
            gameModeEnabledByEndpointId = mapOf("ep-1" to false, "ep-2" to false),
            hostStartSensorNanos = null,
            hostStopSensorNanos = null,
            monitoringActive = false,
            nowSensorNanos = 0L,
        )

        assertEquals(2, rows.size)
        assertEquals("Pixel 7", rows[0].deviceName)
        assertEquals("EML-L29", rows[1].deviceName)
    }

    @Test
    fun `display rows only include currently connected endpoints`() {
        val rows = buildDisplayLapRowsForConnectedDevices(
            connectedEndpointIds = linkedSetOf("ep-2"),
            deviceNamesByEndpointId = mapOf("ep-1" to "Pixel 7", "ep-2" to "CPH2399"),
            elapsedByEndpointId = mapOf(
                "ep-1" to 1_730_000_000L,
                "ep-2" to 1_770_000_000L,
            ),
            limitMillisByEndpointId = emptyMap(),
            gameModeEnabledByEndpointId = mapOf("ep-1" to false, "ep-2" to false),
            hostStartSensorNanos = null,
            hostStopSensorNanos = null,
            monitoringActive = false,
            nowSensorNanos = 0L,
        )

        assertEquals(1, rows.size)
        assertEquals("CPH2399", rows[0].deviceName)
        assertEquals(formatElapsedTimerDisplay(1_770L), rows[0].lapTimeLabel)
    }

    @Test
    fun `display rows show live timer during active run when endpoint has no final`() {
        val rows = buildDisplayLapRowsForConnectedDevices(
            connectedEndpointIds = linkedSetOf("ep-1"),
            deviceNamesByEndpointId = mapOf("ep-1" to "Pixel 7"),
            elapsedByEndpointId = emptyMap(),
            limitMillisByEndpointId = emptyMap(),
            gameModeEnabledByEndpointId = mapOf("ep-1" to false),
            hostStartSensorNanos = 1_000_000_000L,
            hostStopSensorNanos = null,
            monitoringActive = true,
            nowSensorNanos = 2_730_000_000L,
        )

        assertEquals(1, rows.size)
        assertEquals("Pixel 7", rows[0].deviceName)
        assertEquals(formatElapsedTimerDisplay(1_730L), rows[0].lapTimeLabel)
    }

    @Test
    fun `display rows keep endpoint final when live timer is available`() {
        val rows = buildDisplayLapRowsForConnectedDevices(
            connectedEndpointIds = linkedSetOf("ep-1"),
            deviceNamesByEndpointId = mapOf("ep-1" to "Pixel 7"),
            elapsedByEndpointId = mapOf("ep-1" to 1_730_000_000L),
            limitMillisByEndpointId = emptyMap(),
            gameModeEnabledByEndpointId = mapOf("ep-1" to false),
            hostStartSensorNanos = 1_000_000_000L,
            hostStopSensorNanos = null,
            monitoringActive = true,
            nowSensorNanos = 9_000_000_000L,
        )

        assertEquals(1, rows.size)
        assertEquals(formatElapsedTimerDisplay(1_730L), rows[0].lapTimeLabel)
    }

    @Test
    fun `display rows support mixed final and live timers`() {
        val rows = buildDisplayLapRowsForConnectedDevices(
            connectedEndpointIds = linkedSetOf("ep-1", "ep-2"),
            deviceNamesByEndpointId = mapOf("ep-1" to "Pixel 7", "ep-2" to "CPH2399"),
            elapsedByEndpointId = mapOf("ep-1" to 1_730_000_000L),
            limitMillisByEndpointId = emptyMap(),
            gameModeEnabledByEndpointId = mapOf("ep-1" to false, "ep-2" to false),
            hostStartSensorNanos = 1_000_000_000L,
            hostStopSensorNanos = null,
            monitoringActive = true,
            nowSensorNanos = 2_500_000_000L,
        )

        assertEquals(2, rows.size)
        assertEquals(formatElapsedTimerDisplay(1_730L), rows[0].lapTimeLabel)
        assertEquals(formatElapsedTimerDisplay(1_500L), rows[1].lapTimeLabel)
    }

    @Test
    fun `display rows keep running after host stop until endpoint final arrives`() {
        val rows = buildDisplayLapRowsForConnectedDevices(
            connectedEndpointIds = linkedSetOf("ep-1"),
            deviceNamesByEndpointId = mapOf("ep-1" to "Pixel 7"),
            elapsedByEndpointId = emptyMap(),
            limitMillisByEndpointId = emptyMap(),
            gameModeEnabledByEndpointId = mapOf("ep-1" to false),
            hostStartSensorNanos = 1_000_000_000L,
            hostStopSensorNanos = 2_000_000_000L,
            monitoringActive = false,
            nowSensorNanos = 3_000_000_000L,
        )

        assertEquals(1, rows.size)
        assertEquals(formatElapsedTimerDisplay(2_000L), rows[0].lapTimeLabel)
    }

    @Test
    fun `display role never runs local monitoring capture`() {
        assertFalse(
            shouldRunLocalMonitoring(
                mode = SessionOperatingMode.SINGLE_DEVICE,
                userMonitoringEnabled = true,
                localRole = SessionDeviceRole.DISPLAY,
            ),
        )
        assertFalse(
            shouldRunLocalMonitoring(
                mode = SessionOperatingMode.SINGLE_DEVICE,
                userMonitoringEnabled = true,
                localRole = SessionDeviceRole.CONTROLLER,
            ),
        )
        assertTrue(
            shouldRunLocalMonitoring(
                mode = SessionOperatingMode.SINGLE_DEVICE,
                userMonitoringEnabled = true,
                localRole = SessionDeviceRole.UNASSIGNED,
            ),
        )
    }

    @Test
    fun `passive display client mode only matches single-device client display role`() {
        assertTrue(
            shouldUsePassiveDisplayClientMode(
                mode = SessionOperatingMode.SINGLE_DEVICE,
                networkRole = SessionNetworkRole.CLIENT,
                localRole = SessionDeviceRole.DISPLAY,
            ),
        )
        assertFalse(
            shouldUsePassiveDisplayClientMode(
                mode = SessionOperatingMode.SINGLE_DEVICE,
                networkRole = SessionNetworkRole.CLIENT,
                localRole = SessionDeviceRole.CONTROLLER,
            ),
        )
        assertFalse(
            shouldUsePassiveDisplayClientMode(
                mode = SessionOperatingMode.SINGLE_DEVICE,
                networkRole = SessionNetworkRole.HOST,
                localRole = SessionDeviceRole.DISPLAY,
            ),
        )
    }

@Test
    fun `runtime startup action uses single-device action for single-device config`() {
        assertEquals(
            RuntimeStartupAction.START_SINGLE_DEVICE,
            resolveRuntimeStartupAction(
                com.paul.sprintsync.core.RuntimeDeviceConfig(
                    networkRole = com.paul.sprintsync.core.RuntimeNetworkRole.NONE,
                    operatingMode = com.paul.sprintsync.core.RuntimeOperatingMode.SINGLE_DEVICE,
                    profile = "default",
                    isControllerOnlyHost = false,
                ),
            ),
        )
    }

    @Test
    fun `controller startup uses monitoring stage`() {
        assertEquals(SessionStage.MONITORING, controllerInitialStage())
    }

@Test
    fun `runtime startup action uses display-host for host role`() {
        assertEquals(
            RuntimeStartupAction.START_DISPLAY_HOST,
            resolveRuntimeStartupAction(
                com.paul.sprintsync.core.RuntimeDeviceConfig(
                    networkRole = com.paul.sprintsync.core.RuntimeNetworkRole.HOST,
                    operatingMode = com.paul.sprintsync.core.RuntimeOperatingMode.SINGLE_DEVICE,
                    profile = "host_xiaomi",
                    isControllerOnlyHost = true,
                ),
            ),
        )
    }

@Test
    fun `runtime startup action uses controller for client role`() {
        assertEquals(
            RuntimeStartupAction.START_CONTROLLER,
            resolveRuntimeStartupAction(
                com.paul.sprintsync.core.RuntimeDeviceConfig(
                    networkRole = com.paul.sprintsync.core.RuntimeNetworkRole.CLIENT,
                    operatingMode = com.paul.sprintsync.core.RuntimeOperatingMode.SINGLE_DEVICE,
                    profile = "default",
                    isControllerOnlyHost = false,
                ),
            ),
        )
    }

    @Test
    fun `runtime startup action falls back to single-device when role is none`() {
        assertEquals(
            RuntimeStartupAction.START_SINGLE_DEVICE,
            resolveRuntimeStartupAction(
                com.paul.sprintsync.core.RuntimeDeviceConfig(
                    networkRole = com.paul.sprintsync.core.RuntimeNetworkRole.NONE,
                    operatingMode = com.paul.sprintsync.core.RuntimeOperatingMode.SINGLE_DEVICE,
                    profile = "default",
                    isControllerOnlyHost = false,
                ),
            ),
        )
    }

    @Test
    fun `controller endpoint name helper matches controller suffix`() {
        assertTrue(isControllerEndpointName("CPH2399 (Controller)"))
        assertFalse(isControllerEndpointName("Topaz"))
    }

    @Test
    fun `sensitivity percent maps to clamped inverted threshold`() {
        assertEquals(0.08, thresholdFromSensitivityPercent(0), 0.000001)
        assertEquals(0.001, thresholdFromSensitivityPercent(100), 0.000001)
        assertEquals(0.001, thresholdFromSensitivityPercent(150), 0.000001)
        assertEquals(0.08, thresholdFromSensitivityPercent(-10), 0.000001)
    }

    @Test
    fun `display rows mark over limit when elapsed exceeds game mode limit`() {
        val rows = buildDisplayLapRowsForConnectedDevices(
            connectedEndpointIds = linkedSetOf("ep-1", "ep-2"),
            deviceNamesByEndpointId = mapOf("ep-1" to "Pixel 7", "ep-2" to "CPH2399"),
            elapsedByEndpointId = mapOf(
                "ep-1" to 31_000_000_000L,
                "ep-2" to 15_000_000_000L,
            ),
            limitMillisByEndpointId = emptyMap(),
            gameModeEnabledByEndpointId = mapOf("ep-1" to true, "ep-2" to true),
            gameModeLimitMillisByEndpointId = mapOf("ep-1" to 30_000L, "ep-2" to 30_000L),
            hostStartSensorNanos = null,
            hostStopSensorNanos = null,
            monitoringActive = false,
            nowSensorNanos = 0L,
        )

        assertEquals(true, rows[0].isOverLimit)
        assertEquals(false, rows[1].isOverLimit)
        assertEquals("-1.000", rows[0].lapTimeLabel)
        assertEquals("15.000", rows[1].lapTimeLabel)
        assertEquals(null, rows[0].limitLabel)
    }

    @Test
    fun `display rows include game mode lives and use default limit countdown label`() {
        val rows = buildDisplayLapRowsForConnectedDevices(
            connectedEndpointIds = linkedSetOf("ep-1"),
            deviceNamesByEndpointId = mapOf("ep-1" to "Pixel 7"),
            elapsedByEndpointId = mapOf("ep-1" to 2_000_000_000L),
            limitMillisByEndpointId = emptyMap(),
            gameModeEnabledByEndpointId = mapOf("ep-1" to true),
            gameModeConfiguredLivesByEndpointId = mapOf("ep-1" to 8),
            gameModeCurrentLivesByEndpointId = mapOf("ep-1" to 6),
            hostStartSensorNanos = null,
            hostStopSensorNanos = null,
            monitoringActive = false,
            nowSensorNanos = 0L,
        )

        assertEquals(1, rows.size)
        assertTrue(rows[0].showLives)
        assertEquals(6, rows[0].currentLives)
        assertEquals(8, rows[0].maxLives)
        assertEquals("3.000", rows[0].lapTimeLabel)
        assertEquals(null, rows[0].limitLabel)
    }

    @Test
    fun `display rows show static limit countdown before timing starts`() {
        val rows = buildDisplayLapRowsForConnectedDevices(
            connectedEndpointIds = linkedSetOf("ep-1"),
            deviceNamesByEndpointId = mapOf("ep-1" to "Pixel 7"),
            elapsedByEndpointId = emptyMap(),
            limitMillisByEndpointId = emptyMap(),
            gameModeEnabledByEndpointId = mapOf("ep-1" to true),
            gameModeLimitMillisByEndpointId = mapOf("ep-1" to 5_000L),
            hostStartSensorNanos = null,
            hostStopSensorNanos = null,
            monitoringActive = false,
            nowSensorNanos = 0L,
        )

        assertEquals(1, rows.size)
        assertEquals("5.000", rows[0].lapTimeLabel)
        assertFalse(rows[0].isOverLimit)
        assertFalse(rows[0].isUnderLimit)
    }

    @Test
    fun `display rows show signed countdown when elapsed passes limit`() {
        val rows = buildDisplayLapRowsForConnectedDevices(
            connectedEndpointIds = linkedSetOf("ep-1"),
            deviceNamesByEndpointId = mapOf("ep-1" to "Pixel 7"),
            elapsedByEndpointId = mapOf("ep-1" to 5_350_000_000L),
            limitMillisByEndpointId = emptyMap(),
            gameModeEnabledByEndpointId = mapOf("ep-1" to true),
            gameModeLimitMillisByEndpointId = mapOf("ep-1" to 5_000L),
            hostStartSensorNanos = null,
            hostStopSensorNanos = null,
            monitoringActive = false,
            nowSensorNanos = 0L,
        )

        assertEquals(1, rows.size)
        assertEquals("-0.350", rows[0].lapTimeLabel)
        assertTrue(rows[0].isOverLimit)
        assertFalse(rows[0].isUnderLimit)
    }

    @Test
    fun `compute next game mode lives decrements once and floors at zero`() {
        assertEquals(
            4,
            computeNextGameModeLives(
                gameModeEnabled = true,
                currentLives = 5,
                maxLives = 10,
                elapsedNanos = 5_500_000_000L,
                limitMillis = 5_000L,
            ),
        )
        assertEquals(
            0,
            computeNextGameModeLives(
                gameModeEnabled = true,
                currentLives = 0,
                maxLives = 10,
                elapsedNanos = 9_000_000_000L,
                limitMillis = 5_000L,
            ),
        )
        assertEquals(
            5,
            computeNextGameModeLives(
                gameModeEnabled = true,
                currentLives = 5,
                maxLives = 10,
                elapsedNanos = 4_900_000_000L,
                limitMillis = 5_000L,
            ),
        )
        assertEquals(
            5,
            computeNextGameModeLives(
                gameModeEnabled = false,
                currentLives = 5,
                maxLives = 10,
                elapsedNanos = 9_000_000_000L,
                limitMillis = 5_000L,
            ),
        )
    }

    @Test
    fun `game mode auto limit increments run count without reducing before threshold`() {
        val advanced = advanceGameModeAutoLimit(
            currentRunCount = 3,
            everyRuns = 10,
            currentLimitMillis = 5_000L,
        )

        assertEquals(4, advanced.nextRunCount)
        assertEquals(5_000L, advanced.nextLimitMillis)
    }

    @Test
    fun `game mode auto limit reduces by configured millis at threshold and resets counter`() {
        val advanced = advanceGameModeAutoLimit(
            currentRunCount = 9,
            everyRuns = 10,
            currentLimitMillis = 5_000L,
            reductionMillis = 250L,
        )

        assertEquals(0, advanced.nextRunCount)
        assertEquals(4_750L, advanced.nextLimitMillis)
    }

    @Test
    fun `game mode auto limit clamps to minimum 100 ms`() {
        val advanced = advanceGameModeAutoLimit(
            currentRunCount = 0,
            everyRuns = 1,
            currentLimitMillis = 120L,
        )

        assertEquals(0, advanced.nextRunCount)
        assertEquals(100L, advanced.nextLimitMillis)
    }

    @Test
    fun `monitoring connection label uses configured tcp host and port when peers connected`() {
        assertEquals(
            "TCP (192.168.0.103:9000)",
            resolveMonitoringConnectionTypeLabel(
                hasPeers = true,
                hostIp = "192.168.0.103",
                hostPort = 9000,
            ),
        )
    }

    @Test
    fun `monitoring connection label hides tcp host when no peers connected`() {
        assertEquals(
            "-",
            resolveMonitoringConnectionTypeLabel(
                hasPeers = false,
                hostIp = "192.168.0.103",
                hostPort = 9000,
            ),
        )
    }

    @Test
    fun `reconnect delay uses bounded exponential backoff`() {
        assertEquals(500L, reconnectDelayMillis(attempt = 0))
        assertEquals(1000L, reconnectDelayMillis(attempt = 1))
        assertEquals(2000L, reconnectDelayMillis(attempt = 2))
        assertEquals(5000L, reconnectDelayMillis(attempt = 6))
        assertEquals(5000L, reconnectDelayMillis(attempt = 12))
    }

    @Test
    fun `effective auto ready delay defaults to two seconds when unset`() {
        assertEquals(2, effectiveAutoReadyDelaySeconds(configuredDelaySeconds = null))
    }

    @Test
    fun `effective auto ready delay supports manual and validates range`() {
        assertEquals(null, effectiveAutoReadyDelaySeconds(configuredDelaySeconds = 0))
        assertEquals(1, effectiveAutoReadyDelaySeconds(configuredDelaySeconds = 1))
        assertEquals(5, effectiveAutoReadyDelaySeconds(configuredDelaySeconds = 5))
        assertEquals(2, effectiveAutoReadyDelaySeconds(configuredDelaySeconds = 9))
    }

    @Test
    fun `little-endian dhcp gateway int converts to ipv4`() {
        assertEquals("10.173.42.224", ipv4FromLittleEndianInt(0xE02AAD0A.toInt()))
        assertEquals(null, ipv4FromLittleEndianInt(0))
    }
}
