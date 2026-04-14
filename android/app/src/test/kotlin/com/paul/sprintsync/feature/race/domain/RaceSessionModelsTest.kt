package com.paul.sprintsync.feature.race.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RaceSessionModelsTest {
    @Test
    fun `lap result message round-trips elapsed nanos`() {
        val original = SessionLapResultMessage(
            senderDeviceName = "OnePlus",
            elapsedNanos = 1_234_567_890L,
        )

        val parsed = SessionLapResultMessage.tryParse(original.toJsonString())

        assertNotNull(parsed)
        assertEquals("OnePlus", parsed.senderDeviceName)
        assertEquals(1_234_567_890L, parsed.elapsedNanos)
    }

    @Test
    fun `lap result message rejects non-positive elapsed nanos`() {
        val invalid = """
            {
              "type": "lap_result",
              "senderDeviceName": "Timer",
              "elapsedNanos": 0
            }
        """.trimIndent()

        assertNull(SessionLapResultMessage.tryParse(invalid))
    }

    @Test
    fun `lap started message round-trips sender`() {
        val original = SessionLapStartedMessage(senderDeviceName = "Pixel 7")

        val parsed = SessionLapStartedMessage.tryParse(original.toJsonString())

        assertNotNull(parsed)
        assertEquals("Pixel 7", parsed.senderDeviceName)
    }

    @Test
    fun `lap started message rejects empty sender`() {
        val invalid = """
            {
              "type": "lap_started",
              "senderDeviceName": ""
            }
        """.trimIndent()

        assertNull(SessionLapStartedMessage.tryParse(invalid))
    }

    @Test
    fun `control command accepts display limit with positive millis`() {
        val raw = SessionControlCommandMessage(
            action = SessionControlAction.SET_DISPLAY_LIMIT,
            targetEndpointId = "ep-1",
            senderDeviceName = "Controller",
            limitMillis = 1_500L,
            sensitivityPercent = null,
        ).toJsonString()

        val parsed = SessionControlCommandMessage.tryParse(raw)

        assertNotNull(parsed)
        assertEquals(SessionControlAction.SET_DISPLAY_LIMIT, parsed.action)
        assertEquals(1_500L, parsed.limitMillis)
    }

    @Test
    fun `control command accepts auto ready delay seconds and manual mode`() {
        val delayedRaw = SessionControlCommandMessage(
            action = SessionControlAction.SET_AUTO_READY_DELAY,
            targetEndpointId = "ep-1",
            senderDeviceName = "Controller",
            limitMillis = null,
            sensitivityPercent = null,
            autoReadyDelaySeconds = 2,
        ).toJsonString()
        val delayedParsed = SessionControlCommandMessage.tryParse(delayedRaw)

        assertNotNull(delayedParsed)
        assertEquals(SessionControlAction.SET_AUTO_READY_DELAY, delayedParsed.action)
        assertEquals(2, delayedParsed.autoReadyDelaySeconds)

        val manualRaw = SessionControlCommandMessage(
            action = SessionControlAction.SET_AUTO_READY_DELAY,
            targetEndpointId = "ep-1",
            senderDeviceName = "Controller",
            limitMillis = null,
            sensitivityPercent = null,
            autoReadyDelaySeconds = null,
        ).toJsonString()
        val manualParsed = SessionControlCommandMessage.tryParse(manualRaw)

        assertNotNull(manualParsed)
        assertEquals(SessionControlAction.SET_AUTO_READY_DELAY, manualParsed.action)
        assertEquals(null, manualParsed.autoReadyDelaySeconds)
    }

    @Test
    fun `control command rejects invalid auto ready delay seconds`() {
        val invalid = """
            {
              "type": "control_command",
              "action": "set_auto_ready_delay",
              "targetEndpointId": "ep-1",
              "senderDeviceName": "Controller",
              "autoReadyDelaySeconds": 8
            }
        """.trimIndent()

        assertNull(SessionControlCommandMessage.tryParse(invalid))
    }

    @Test
    fun `control command accepts wait text mode toggle`() {
        val raw = SessionControlCommandMessage(
            action = SessionControlAction.SET_WAIT_TEXT_MODE,
            targetEndpointId = "ep-1",
            senderDeviceName = "Controller",
            limitMillis = null,
            sensitivityPercent = null,
            autoReadyDelaySeconds = null,
            waitTextEnabled = false,
        ).toJsonString()

        val parsed = SessionControlCommandMessage.tryParse(raw)

        assertNotNull(parsed)
        assertEquals(SessionControlAction.SET_WAIT_TEXT_MODE, parsed.action)
        assertEquals(false, parsed.waitTextEnabled)
    }

    @Test
    fun `control command rejects wait text mode without payload`() {
        val invalid = """
            {
              "type": "control_command",
              "action": "set_wait_text_mode",
              "targetEndpointId": "ep-1",
              "senderDeviceName": "Controller"
            }
        """.trimIndent()

        assertNull(SessionControlCommandMessage.tryParse(invalid))
    }

    @Test
    fun `control command rejects invalid sensitivity`() {
        val invalid = """
            {
              "type": "control_command",
              "action": "set_motion_sensitivity",
              "targetEndpointId": "ep-1",
              "senderDeviceName": "Controller",
              "sensitivityPercent": 101
            }
        """.trimIndent()

        assertNull(SessionControlCommandMessage.tryParse(invalid))
    }

    @Test
    fun `control command accepts game mode fields`() {
        val enabledRaw = SessionControlCommandMessage(
            action = SessionControlAction.SET_GAME_MODE_ENABLED,
            targetEndpointId = "ep-1",
            senderDeviceName = "Controller",
            limitMillis = null,
            sensitivityPercent = null,
            gameModeEnabled = true,
        ).toJsonString()
        val enabledParsed = SessionControlCommandMessage.tryParse(enabledRaw)
        assertNotNull(enabledParsed)
        assertEquals(SessionControlAction.SET_GAME_MODE_ENABLED, enabledParsed.action)
        assertEquals(true, enabledParsed.gameModeEnabled)

        val limitRaw = SessionControlCommandMessage(
            action = SessionControlAction.SET_GAME_MODE_LIMIT,
            targetEndpointId = "ep-1",
            senderDeviceName = "Controller",
            limitMillis = null,
            sensitivityPercent = null,
            gameModeLimitMillis = 5_000L,
        ).toJsonString()
        val limitParsed = SessionControlCommandMessage.tryParse(limitRaw)
        assertNotNull(limitParsed)
        assertEquals(SessionControlAction.SET_GAME_MODE_LIMIT, limitParsed.action)
        assertEquals(5_000L, limitParsed.gameModeLimitMillis)

        val livesRaw = SessionControlCommandMessage(
            action = SessionControlAction.SET_GAME_MODE_LIVES,
            targetEndpointId = "ep-1",
            senderDeviceName = "Controller",
            limitMillis = null,
            sensitivityPercent = null,
            gameModeLives = 7,
        ).toJsonString()
        val livesParsed = SessionControlCommandMessage.tryParse(livesRaw)
        assertNotNull(livesParsed)
        assertEquals(SessionControlAction.SET_GAME_MODE_LIVES, livesParsed.action)
        assertEquals(7, livesParsed.gameModeLives)
    }

    @Test
    fun `control command rejects invalid game mode payloads`() {
        val missingEnabled = """
            {
              "type": "control_command",
              "action": "set_game_mode_enabled",
              "targetEndpointId": "ep-1",
              "senderDeviceName": "Controller"
            }
        """.trimIndent()
        assertNull(SessionControlCommandMessage.tryParse(missingEnabled))

        val invalidLimit = """
            {
              "type": "control_command",
              "action": "set_game_mode_limit",
              "targetEndpointId": "ep-1",
              "senderDeviceName": "Controller",
              "gameModeLimitMillis": 0
            }
        """.trimIndent()
        assertNull(SessionControlCommandMessage.tryParse(invalidLimit))

        val invalidLives = """
            {
              "type": "control_command",
              "action": "set_game_mode_lives",
              "targetEndpointId": "ep-1",
              "senderDeviceName": "Controller",
              "gameModeLives": 11
            }
        """.trimIndent()
        assertNull(SessionControlCommandMessage.tryParse(invalidLives))
    }

    @Test
    fun `controller targets message round-trips targets`() {
        val original = SessionControllerTargetsMessage(
            senderDeviceName = "Host",
            targets = listOf(
                SessionControllerTarget(endpointId = "ep-1", deviceName = "Lane 1"),
                SessionControllerTarget(endpointId = "ep-2", deviceName = "Lane 2"),
            ),
        )

        val parsed = SessionControllerTargetsMessage.tryParse(original.toJsonString())

        assertNotNull(parsed)
        assertEquals("Host", parsed.senderDeviceName)
        assertEquals(2, parsed.targets.size)
        assertEquals("ep-1", parsed.targets[0].endpointId)
    }

    @Test
    fun `controller identity message round-trips sender name`() {
        val original = SessionControllerIdentityMessage(senderDeviceName = "Controller")

        val parsed = SessionControllerIdentityMessage.tryParse(original.toJsonString())

        assertNotNull(parsed)
        assertEquals("Controller", parsed.senderDeviceName)
    }

    @Test
    fun `device role label supports remaining roles`() {
        assertEquals("Unassigned", sessionDeviceRoleLabel(SessionDeviceRole.UNASSIGNED))
        assertEquals("Controller", sessionDeviceRoleLabel(SessionDeviceRole.CONTROLLER))
        assertEquals("Display", sessionDeviceRoleLabel(SessionDeviceRole.DISPLAY))
        assertTrue(sessionDeviceRoleFromName("split") == null)
    }
}
