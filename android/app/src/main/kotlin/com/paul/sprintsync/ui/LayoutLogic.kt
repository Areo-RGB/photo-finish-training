package com.paul.sprintsync

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paul.sprintsync.feature.race.domain.SessionDeviceRole
import com.paul.sprintsync.feature.race.domain.SessionNetworkRole
import com.paul.sprintsync.feature.race.domain.SessionOperatingMode
import com.paul.sprintsync.feature.race.domain.SessionStage

internal fun shouldShowSetupPermissionWarning(permissionGranted: Boolean, deniedPermissions: List<String>): Boolean =
    !permissionGranted && deniedPermissions.isNotEmpty()

internal fun shouldShowUpdateDownloadingOverlay(updateDownloading: Boolean): Boolean = updateDownloading

internal fun formatAppVersionLabel(versionName: String, versionCode: Int): String =
    "v$versionName ($versionCode)"

enum class SetupActionProfile {
    SINGLE_ONLY,
    DISPLAY_ONLY,
    CONTROLLER_ONLY,
}

internal fun resolveSetupActionProfile(
    runtimeDeviceConfig: com.paul.sprintsync.core.RuntimeDeviceConfig,
): SetupActionProfile {
    return when (runtimeDeviceConfig.networkRole) {
        com.paul.sprintsync.core.RuntimeNetworkRole.HOST -> SetupActionProfile.DISPLAY_ONLY
        com.paul.sprintsync.core.RuntimeNetworkRole.CLIENT -> SetupActionProfile.CONTROLLER_ONLY
        com.paul.sprintsync.core.RuntimeNetworkRole.NONE -> SetupActionProfile.SINGLE_ONLY
    }
}

internal fun shouldShowMonitoringResetAction(
    isHost: Boolean,
    startedSensorNanos: Long?,
    stoppedSensorNanos: Long?,
): Boolean = isHost && startedSensorNanos != null

internal fun shouldShowDisplayRelayControls(mode: SessionOperatingMode): Boolean =
    mode == SessionOperatingMode.SINGLE_DEVICE

internal fun shouldShowMonitoringRoleAndToggles(mode: SessionOperatingMode): Boolean =
    mode != SessionOperatingMode.SINGLE_DEVICE

internal fun shouldShowSingleDeviceCameraFacingToggle(mode: SessionOperatingMode): Boolean =
    mode == SessionOperatingMode.SINGLE_DEVICE

internal fun shouldShowDebugToggle(debugViewEnabled: Boolean): Boolean = debugViewEnabled
internal fun shouldShowDebugSection(debugViewEnabled: Boolean, showDebugInfo: Boolean): Boolean =
    debugViewEnabled && showDebugInfo

internal fun shouldShowMonitoringConnectionDebugInfo(
    debugViewEnabled: Boolean,
    showDebugInfo: Boolean,
): Boolean = shouldShowDebugSection(debugViewEnabled, showDebugInfo)

internal fun shouldShowSingleFlavorConnectingCard(
    setupActionProfile: SetupActionProfile,
    connectedEndpointCount: Int,
): Boolean = (setupActionProfile == SetupActionProfile.SINGLE_ONLY || setupActionProfile == SetupActionProfile.CONTROLLER_ONLY) &&
    connectedEndpointCount == 0

internal fun shouldShowMonitoringPreview(mode: SessionOperatingMode, effectiveShowPreview: Boolean): Boolean =
    effectiveShowPreview

internal fun shouldShowMonitoringPreviewToggle(mode: SessionOperatingMode): Boolean =
    mode != SessionOperatingMode.DISPLAY_HOST

internal fun shouldShowInlineMonitoringResetButton(mode: SessionOperatingMode): Boolean =
    mode != SessionOperatingMode.SINGLE_DEVICE

internal fun shouldShowRunDetailMetrics(mode: SessionOperatingMode): Boolean =
    mode != SessionOperatingMode.SINGLE_DEVICE

internal fun shouldShowCameraFpsInfo(debugViewEnabled: Boolean, showDebugInfo: Boolean): Boolean =
    shouldShowDebugSection(debugViewEnabled, showDebugInfo)

internal fun shouldShowPassiveDisplayClientView(
    stage: SessionStage,
    operatingMode: SessionOperatingMode,
    networkRole: SessionNetworkRole,
    localRole: SessionDeviceRole,
): Boolean {
    return stage == SessionStage.MONITORING &&
        operatingMode == SessionOperatingMode.SINGLE_DEVICE &&
        networkRole == SessionNetworkRole.CLIENT &&
        localRole == SessionDeviceRole.DISPLAY
}

internal data class DisplayLayoutSpec(
    val rowHeight: Dp,
    val minRowHeight: Dp,
    val dividerWidth: Dp,
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    val timeFont: TextUnit,
    val deviceFont: TextUnit,
)

internal fun displayLayoutSpecForCount(count: Int): DisplayLayoutSpec {
    return when {
        count <= 1 -> DisplayLayoutSpec(
            rowHeight = 420.dp,
            minRowHeight = 300.dp,
            dividerWidth = 4.dp,
            horizontalPadding = 16.dp,
            verticalPadding = 18.dp,
            timeFont = 232.sp,
            deviceFont = 26.sp,
        )
        count == 2 -> DisplayLayoutSpec(
            rowHeight = 330.dp,
            minRowHeight = 230.dp,
            dividerWidth = 4.dp,
            horizontalPadding = 14.dp,
            verticalPadding = 16.dp,
            timeFont = 184.sp,
            deviceFont = 22.sp,
        )
        count in 3..4 -> DisplayLayoutSpec(
            rowHeight = 245.dp,
            minRowHeight = 170.dp,
            dividerWidth = 4.dp,
            horizontalPadding = 12.dp,
            verticalPadding = 12.dp,
            timeFont = 136.sp,
            deviceFont = 18.sp,
        )
        else -> DisplayLayoutSpec(
            rowHeight = 182.dp,
            minRowHeight = 130.dp,
            dividerWidth = 4.dp,
            horizontalPadding = 10.dp,
            verticalPadding = 8.dp,
            timeFont = 96.sp,
            deviceFont = 15.sp,
        )
    }
}

internal fun displayHorizontalVisibleCardSlots(count: Int): Int = when {
    count <= 1 -> 1
    count == 2 -> 2
    else -> 3
}

internal fun shouldStackDisplayCardsVertically(count: Int): Boolean = count == 2

internal fun clampDisplayTimeFont(
    base: TextUnit,
    rowHeight: Dp,
    rowContentWidth: Dp,
    maxLabelLength: Int,
    density: androidx.compose.ui.unit.Density,
): TextUnit {
    val maxByHeight = with(density) { (rowHeight * 0.78f).toSp() }
    val maxChars = maxLabelLength.coerceAtLeast(5).toFloat()
    val widthFactor = 0.55f // Inter ExtraBold digits are narrower than the previous budget assumed.
    val maxByWidth = with(density) { (rowContentWidth / (maxChars * widthFactor)).toSp() }
    return minOf(base.value, maxByHeight.value, maxByWidth.value).sp
}

internal fun clampDisplayLabelFont(base: TextUnit, rowHeight: Dp, density: androidx.compose.ui.unit.Density): TextUnit {
    val maxByHeight = with(density) { (rowHeight * 0.16f).toSp() }
    val minReadable = 12.sp
    val clamped = minOf(base.value, maxByHeight.value).sp
    return maxOf(clamped.value, minReadable.value).sp
}

internal fun formatDurationNanos(nanos: Long): String {
    val totalMillis = (nanos / 1_000_000L).coerceAtLeast(0L)
    return formatElapsedTimerDisplay(totalMillis)
}
