package com.paul.sprintsync.features.race_session

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