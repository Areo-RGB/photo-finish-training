package com.paul.sprintsync

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paul.sprintsync.core.ui.components.SecondaryButton
import com.paul.sprintsync.feature.motion.data.nativebridge.SensorNativePreviewViewFactory
import com.paul.sprintsync.feature.race.domain.SessionCameraFacing
import com.paul.sprintsync.feature.race.domain.SessionDeviceRole
import com.paul.sprintsync.feature.race.domain.SessionOperatingMode
import com.paul.sprintsync.feature.race.domain.SessionStage
import androidx.compose.foundation.shape.RoundedCornerShape

@Composable
fun SprintSyncApp(
    uiState: SprintSyncUiState,
    debugViewEnabled: Boolean,
    previewViewFactory: SensorNativePreviewViewFactory,
    setupActionProfile: SetupActionProfile = SetupActionProfile.SINGLE_ONLY,
    runtimeDeviceConfig: com.paul.sprintsync.core.RuntimeDeviceConfig =
        com.paul.sprintsync.core.RuntimeDeviceConfig(),
    onAction: (MainAction) -> Unit,
) {
    var showPreview by rememberSaveable { mutableStateOf(true) }
    var showDebugInfo by rememberSaveable { mutableStateOf(false) }
    val effectiveShowPreview = showPreview
    val localDevice = uiState.devices.firstOrNull { it.isLocal }
    val isHostXiaomiProfile = runtimeDeviceConfig.profile.equals("host_xiaomi", ignoreCase = true)
    val isDisplayHostMode =
        uiState.stage == SessionStage.MONITORING &&
            uiState.operatingMode == SessionOperatingMode.DISPLAY_HOST
    val isPassiveDisplayClientMode = shouldShowPassiveDisplayClientView(
        stage = uiState.stage,
        operatingMode = uiState.operatingMode,
        networkRole = uiState.networkRole,
        localRole = uiState.localRole,
    )
    val isFullscreenDisplayResultsMode = isDisplayHostMode || isPassiveDisplayClientMode
    val contentHorizontalPadding = when {
        isFullscreenDisplayResultsMode -> 0.dp
        isHostXiaomiProfile -> 6.dp
        else -> 16.dp
    }
    val contentVerticalPadding = if (isFullscreenDisplayResultsMode) 0.dp else 12.dp

    Scaffold(
        topBar = {},
    ) { paddingValues ->
        val scaffoldPadding = if (isFullscreenDisplayResultsMode) PaddingValues(0.dp) else paddingValues
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isFullscreenDisplayResultsMode) Color.Black else Color.Transparent)
                .padding(scaffoldPadding),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = contentHorizontalPadding,
                        vertical = contentVerticalPadding,
                    ),
                verticalArrangement = Arrangement.spacedBy(if (isFullscreenDisplayResultsMode) 0.dp else 12.dp),
            ) {
            item {
                if (!isDisplayHostMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = when {
                                uiState.operatingMode == SessionOperatingMode.DISPLAY_HOST -> "Display Monitor"
                                setupActionProfile == SetupActionProfile.CONTROLLER_ONLY &&
                                    uiState.operatingMode == SessionOperatingMode.SINGLE_DEVICE -> "Controller"
                                uiState.operatingMode == SessionOperatingMode.SINGLE_DEVICE -> "Single Device"
                                uiState.stage == SessionStage.SETUP -> "Setup Session"
                                else -> "Monitoring"
                            },
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (
                                uiState.stage == SessionStage.MONITORING &&
                                uiState.isHost
                            ) {
                                SecondaryButton(text = "Stop", onClick = { onAction(MainAction.StopMonitoring) })
                            }
                            if (shouldShowDebugToggle(debugViewEnabled)) {
                                TextButton(onClick = { showDebugInfo = !showDebugInfo }) {
                                    Text(if (showDebugInfo) "Debug On" else "Debug Off")
                                }
                            }
                        }
                    }
                }
            }

            if (
                shouldShowDebugSection(debugViewEnabled, showDebugInfo) &&
                uiState.stage != SessionStage.MONITORING &&
                !isDisplayHostMode
            ) {
                item {
                    StatusCard(uiState)
                }
            }

            if (uiState.wifiWarningText != null && !isDisplayHostMode) {
                item {
                    WifiWarningCard(
                        text = uiState.wifiWarningText,
                        onClick = { onAction(MainAction.OpenWifiSettings) },
                    )
                }
            }

            when (uiState.stage) {
                SessionStage.SETUP -> {
                    if (shouldShowSetupPermissionWarning(uiState.permissionGranted, uiState.deniedPermissions)) {
                        item {
                            PermissionWarningCard(uiState.deniedPermissions)
                        }
                    }
                    item {
                        SetupActionsCard(
                            setupActionProfile = setupActionProfile,
                            permissionGranted = uiState.permissionGranted,
                            setupBusy = uiState.setupBusy,
                            onRequestPermissions = { onAction(MainAction.RequestPermissions) },
                            onStartSingleDevice = { onAction(MainAction.StartSingleDevice) },
                            onStartDisplayHost = { onAction(MainAction.StartDisplayHost) },
                        )
                    }
                    item {
                        ConnectedDevicesListCard(
                            devices = uiState.devices,
                            showDebugInfo = showDebugInfo,
                        )
                    }
                }

                SessionStage.MONITORING -> {
                    if (uiState.operatingMode == SessionOperatingMode.DISPLAY_HOST || isPassiveDisplayClientMode) {
                        item {
                            DisplayResultsCard(
                                rows = uiState.displayLapRows,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillParentMaxHeight(),
                            )
                        }
                    } else {
                        if (
                            shouldShowSingleFlavorConnectingCard(
                                setupActionProfile = setupActionProfile,
                                connectedEndpointCount = uiState.connectedEndpoints.size,
                            )
                        ) {
                            item {
                                SingleFlavorConnectingCard(
                                    isDiscovering = uiState.displayDiscoveryActive,
                                    discoveredCount = uiState.discoveredEndpoints.size,
                                )
                            }
                        }
                        if (setupActionProfile != SetupActionProfile.CONTROLLER_ONLY) {
                            item {
                                RunMetricsCard(
                                    uiState = uiState,
                                    isHost = uiState.isHost,
                                    onResetRun = { onAction(MainAction.ResetRun) },
                                )
                            }
                        }
                        item {
                            MonitoringSummaryCard(
                                isHost = uiState.isHost,
                                localRole = uiState.localRole,
                                localCameraFacing = localDevice?.cameraFacing ?: SessionCameraFacing.REAR,
                                debugViewEnabled = debugViewEnabled,
                                showDebugInfo = showDebugInfo,
                                connectionTypeLabel = uiState.monitoringConnectionTypeLabel,
                                userMonitoringEnabled = uiState.userMonitoringEnabled,
                                onSetMonitoringEnabled = { enabled ->
                                    onAction(MainAction.SetMonitoringEnabled(enabled))
                                },
                                onAssignLocalCameraFacing = { facing ->
                                    localDevice?.let { device ->
                                        onAction(MainAction.AssignCameraFacing(device.id, facing))
                                    }
                                },
                                effectiveShowPreview = effectiveShowPreview,
                                onShowPreviewChanged = { showPreview = it },
                                previewViewFactory = previewViewFactory,
                                roiCenterX = uiState.roiCenterX,
                                operatingMode = uiState.operatingMode,
                                setupActionProfile = setupActionProfile,
                                devices = uiState.devices,
                                displayConnectedHostEndpointId = uiState.displayConnectedHostEndpointId,
                                controllerTargetEndpoints = uiState.controllerTargetEndpoints,
                                discoveredDisplayHosts = uiState.discoveredEndpoints,
                                displayConnectedHostName = uiState.displayConnectedHostName,
                                displayDiscoveryActive = uiState.displayDiscoveryActive,
                                onStartDisplayDiscovery = { onAction(MainAction.StartDisplayDiscovery) },
                                onConnectDisplayHost = { endpointId ->
                                    onAction(MainAction.ConnectDisplayHost(endpointId))
                                },
                                onResetDeviceTimer = { endpointId ->
                                    onAction(MainAction.ResetDeviceTimer(endpointId))
                                },
                                onSetDisplayLimit = { endpointId, limitMillis ->
                                    onAction(MainAction.SetDisplayLimit(endpointId, limitMillis))
                                },
                                onSetAutoReadyDelay = { endpointId, autoReadyDelaySeconds ->
                                    onAction(MainAction.SetAutoReadyDelay(endpointId, autoReadyDelaySeconds))
                                },
                                onSetWaitTextEnabled = { endpointId, enabled ->
                                    onAction(MainAction.SetWaitTextEnabled(endpointId, enabled))
                                },
                                onSetDeviceSensitivity = { endpointId, sensitivityPercent ->
                                    onAction(MainAction.SetDeviceSensitivity(endpointId, sensitivityPercent))
                                },
                                onSetGameModeEnabled = { endpointId, enabled ->
                                    onAction(MainAction.SetGameModeEnabled(endpointId, enabled))
                                },
                                onSetGameModeLimit = { endpointId, limitMillis ->
                                    onAction(MainAction.SetGameModeLimit(endpointId, limitMillis))
                                },
                                onSetGameModeLives = { endpointId, lives ->
                                    onAction(MainAction.SetGameModeLives(endpointId, lives))
                                },
                                onResetRun = { onAction(MainAction.ResetRun) },
                            )
                        }
                        if (showDebugInfo && setupActionProfile != SetupActionProfile.CONTROLLER_ONLY) {
                            item {
                                AdvancedDetectionCard(
                                    uiState = uiState,
                                    onUpdateThreshold = { onAction(MainAction.UpdateThreshold(it)) },
                                    onUpdateRoiCenter = { onAction(MainAction.UpdateRoiCenter(it)) },
                                    onUpdateRoiWidth = { onAction(MainAction.UpdateRoiWidth(it)) },
                                    onUpdateCooldown = { onAction(MainAction.UpdateCooldown(it)) },
                                )
                            }
                        }
                    }
                }
            }

            if (shouldShowDebugSection(debugViewEnabled, showDebugInfo) && !isDisplayHostMode) {
                item {
                    SensorDebugViewCard(uiState)
                }
            }

            if (shouldShowDebugSection(debugViewEnabled, showDebugInfo) && uiState.connectedEndpoints.isNotEmpty()) {
                item {
                    ConnectedCard(uiState.connectedEndpoints)
                }
            }

            if (shouldShowDebugSection(debugViewEnabled, showDebugInfo) && uiState.recentEvents.isNotEmpty()) {
                item {
                    EventsCard(uiState.recentEvents)
                }
            }
            }

            if (isDisplayHostMode) {
                OutlinedButton(
                    onClick = { onAction(MainAction.StopMonitoring) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 20.dp, end = 20.dp),
                    border = BorderStroke(2.5.dp, Color.Black),
                    shape = RoundedCornerShape(50.dp),
                ) {
                    Text(
                        text = "STOP",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                        ),
                        color = Color.Black,
                    )
                }
            }
        }
    }
}

