package com.paul.sprintsync

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paul.sprintsync.feature.race.domain.SessionDeviceRole
import com.paul.sprintsync.feature.race.domain.SessionOperatingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SprintSyncAppLayoutLogicTest {
    @Test
    fun `setup permission warning only shows when permissions missing and denied list is not empty`() {
        assertTrue(
            shouldShowSetupPermissionWarning(
                permissionGranted = false,
                deniedPermissions = listOf("android.permission.CAMERA"),
            ),
        )

        assertFalse(
            shouldShowSetupPermissionWarning(
                permissionGranted = true,
                deniedPermissions = listOf("android.permission.CAMERA"),
            ),
        )

        assertFalse(
            shouldShowSetupPermissionWarning(
                permissionGranted = false,
                deniedPermissions = emptyList(),
            ),
        )
    }

@Test
    fun `setup profile resolves from runtime device config`() {
        assertTrue(
            resolveSetupActionProfile(
                com.paul.sprintsync.core.RuntimeDeviceConfig(
                    networkRole = com.paul.sprintsync.core.RuntimeNetworkRole.NONE,
                    operatingMode = com.paul.sprintsync.core.RuntimeOperatingMode.SINGLE_DEVICE,
                    profile = "default",
                    isControllerOnlyHost = false,
                ),
            ) == SetupActionProfile.SINGLE_ONLY,
        )
        assertTrue(
            resolveSetupActionProfile(
                com.paul.sprintsync.core.RuntimeDeviceConfig(
                    networkRole = com.paul.sprintsync.core.RuntimeNetworkRole.HOST,
                    operatingMode = com.paul.sprintsync.core.RuntimeOperatingMode.SINGLE_DEVICE,
                    profile = "host_xiaomi",
                    isControllerOnlyHost = true,
                ),
            ) == SetupActionProfile.DISPLAY_ONLY,
        )
        assertEquals(
            SetupActionProfile.CONTROLLER_ONLY,
            resolveSetupActionProfile(
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
    fun `single flavor connecting card hides once endpoint is connected`() {
        assertTrue(
            shouldShowSingleFlavorConnectingCard(
                setupActionProfile = SetupActionProfile.SINGLE_ONLY,
                connectedEndpointCount = 0,
            ),
        )
        assertFalse(
            shouldShowSingleFlavorConnectingCard(
                setupActionProfile = SetupActionProfile.SINGLE_ONLY,
                connectedEndpointCount = 1,
            ),
        )
        assertFalse(
            shouldShowSingleFlavorConnectingCard(
                setupActionProfile = SetupActionProfile.DISPLAY_ONLY,
                connectedEndpointCount = 0,
            ),
        )
    }

    @Test
    fun `monitoring reset action shows for host once a run has started`() {
        assertTrue(
            shouldShowMonitoringResetAction(
                isHost = true,
                startedSensorNanos = 10L,
                stoppedSensorNanos = 20L,
            ),
        )

        assertFalse(
            shouldShowMonitoringResetAction(
                isHost = false,
                startedSensorNanos = 10L,
                stoppedSensorNanos = 20L,
            ),
        )

        assertTrue(
            shouldShowMonitoringResetAction(
                isHost = true,
                startedSensorNanos = 10L,
                stoppedSensorNanos = null,
            ),
        )

        assertFalse(
            shouldShowMonitoringResetAction(
                isHost = true,
                startedSensorNanos = null,
                stoppedSensorNanos = null,
            ),
        )
    }

    @Test
    fun `display relay controls only show in single device mode`() {
        assertTrue(shouldShowDisplayRelayControls(SessionOperatingMode.SINGLE_DEVICE))
        assertFalse(shouldShowDisplayRelayControls(SessionOperatingMode.DISPLAY_HOST))
    }

    @Test
    fun `single-device mode hides role and monitoring toggles`() {
        assertFalse(shouldShowMonitoringRoleAndToggles(SessionOperatingMode.SINGLE_DEVICE))
        assertTrue(shouldShowMonitoringRoleAndToggles(SessionOperatingMode.DISPLAY_HOST))
    }

    @Test
    fun `single-device mode shows local camera facing toggle`() {
        assertTrue(shouldShowSingleDeviceCameraFacingToggle(SessionOperatingMode.SINGLE_DEVICE))
        assertFalse(shouldShowSingleDeviceCameraFacingToggle(SessionOperatingMode.DISPLAY_HOST))
    }

    @Test
    fun `single-device mode can hide preview and shows preview switch`() {
        assertTrue(shouldShowMonitoringPreview(SessionOperatingMode.SINGLE_DEVICE, effectiveShowPreview = true))
        assertFalse(shouldShowMonitoringPreview(SessionOperatingMode.SINGLE_DEVICE, effectiveShowPreview = false))
        assertTrue(shouldShowMonitoringPreviewToggle(SessionOperatingMode.SINGLE_DEVICE))
        assertFalse(shouldShowMonitoringPreviewToggle(SessionOperatingMode.DISPLAY_HOST))
    }

    @Test
    fun `inline monitoring reset button shows for compact and wide monitoring modes`() {
        assertFalse(shouldShowInlineMonitoringResetButton(SessionOperatingMode.SINGLE_DEVICE))
        assertTrue(shouldShowInlineMonitoringResetButton(SessionOperatingMode.DISPLAY_HOST))
    }

    @Test
    fun `passive display client view only shows for monitoring single-device client display role`() {
        assertTrue(
            shouldShowPassiveDisplayClientView(
                stage = com.paul.sprintsync.feature.race.domain.SessionStage.MONITORING,
                operatingMode = SessionOperatingMode.SINGLE_DEVICE,
                networkRole = com.paul.sprintsync.feature.race.domain.SessionNetworkRole.CLIENT,
                localRole = SessionDeviceRole.DISPLAY,
            ),
        )
        assertFalse(
            shouldShowPassiveDisplayClientView(
                stage = com.paul.sprintsync.feature.race.domain.SessionStage.SETUP,
                operatingMode = SessionOperatingMode.SINGLE_DEVICE,
                networkRole = com.paul.sprintsync.feature.race.domain.SessionNetworkRole.CLIENT,
                localRole = SessionDeviceRole.DISPLAY,
            ),
        )
    }

    @Test
    fun `single-device mode hides run detail metrics and fps requires debug view access`() {
        assertFalse(shouldShowRunDetailMetrics(SessionOperatingMode.SINGLE_DEVICE))
        assertTrue(shouldShowRunDetailMetrics(SessionOperatingMode.DISPLAY_HOST))
        assertTrue(shouldShowCameraFpsInfo(debugViewEnabled = true, showDebugInfo = true))
        assertFalse(shouldShowCameraFpsInfo(debugViewEnabled = true, showDebugInfo = false))
        assertFalse(shouldShowCameraFpsInfo(debugViewEnabled = false, showDebugInfo = true))
    }

    @Test
    fun `debug controls and panel require debug view enabled`() {
        assertTrue(shouldShowDebugToggle(debugViewEnabled = true))
        assertFalse(shouldShowDebugToggle(debugViewEnabled = false))
        assertTrue(shouldShowDebugSection(debugViewEnabled = true, showDebugInfo = true))
        assertFalse(shouldShowDebugSection(debugViewEnabled = true, showDebugInfo = false))
        assertFalse(shouldShowDebugSection(debugViewEnabled = false, showDebugInfo = true))
        assertTrue(
            shouldShowMonitoringConnectionDebugInfo(
                debugViewEnabled = true,
                showDebugInfo = true,
            ),
        )
        assertFalse(
            shouldShowMonitoringConnectionDebugInfo(
                debugViewEnabled = false,
                showDebugInfo = true,
            ),
        )
    }

    @Test
    fun `display layout uses expected size tiers by row count`() {
        val one = displayLayoutSpecForCount(1)
        val two = displayLayoutSpecForCount(2)
        val three = displayLayoutSpecForCount(3)
        val many = displayLayoutSpecForCount(8)

        assertTrue(one.timeFont.value > two.timeFont.value)
        assertTrue(two.timeFont.value > three.timeFont.value)
        assertTrue(three.timeFont.value > many.timeFont.value)
        assertTrue(one.rowHeight > two.rowHeight)
        assertTrue(two.rowHeight > three.rowHeight)
        assertTrue(three.rowHeight > many.rowHeight)
    }

    @Test
    fun `display layout keeps a thin divider between cards`() {
        assertEquals(4.dp, displayLayoutSpecForCount(2).dividerWidth)
    }

    @Test
    fun `display host horizontal layout caps visible card slots`() {
        assertTrue(displayHorizontalVisibleCardSlots(1) == 1)
        assertTrue(displayHorizontalVisibleCardSlots(2) == 2)
        assertTrue(displayHorizontalVisibleCardSlots(3) == 3)
        assertTrue(displayHorizontalVisibleCardSlots(8) == 3)
    }

    @Test
    fun `display layout stacks exactly two cards vertically`() {
        assertFalse(shouldStackDisplayCardsVertically(1))
        assertTrue(shouldStackDisplayCardsVertically(2))
        assertFalse(shouldStackDisplayCardsVertically(3))
    }

    @Test
    fun `display time font clamp respects row height budget`() {
        val density = Density(1f)
        val clamped = clampDisplayTimeFont(
            base = 128.sp,
            rowHeight = 120.dp,
            rowContentWidth = 800.dp,
            maxLabelLength = 8,
            density = density,
        )
        assertTrue(clamped.value <= 93.6f)
    }

    @Test
    fun `display time font clamp also respects width budget`() {
        val density = Density(1f)
        val clamped = clampDisplayTimeFont(
            base = 140.sp,
            rowHeight = 320.dp,
            rowContentWidth = 330.dp,
            maxLabelLength = 8,
            density = density,
        )
        assertTrue(clamped.value <= 75f)
    }

    @Test
    fun `display time font clamp allows larger text for shorter timer labels`() {
        val density = Density(1f)
        val shortLabel = clampDisplayTimeFont(
            base = 184.sp,
            rowHeight = 320.dp,
            rowContentWidth = 500.dp,
            maxLabelLength = 5,
            density = density,
        )
        val longLabel = clampDisplayTimeFont(
            base = 184.sp,
            rowHeight = 320.dp,
            rowContentWidth = 500.dp,
            maxLabelLength = 8,
            density = density,
        )
        assertTrue(shortLabel.value > longLabel.value)
    }

    @Test
    fun `display label font clamp never drops below readable minimum`() {
        val density = Density(1f)
        val clamped = clampDisplayLabelFont(base = 26.sp, rowHeight = 40.dp, density = density)
        assertTrue(clamped.value >= 12f)
    }
}
