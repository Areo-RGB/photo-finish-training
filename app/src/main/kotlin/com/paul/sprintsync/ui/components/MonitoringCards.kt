package com.paul.sprintsync

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.paul.sprintsync.core.ui.components.MetricDisplay
import com.paul.sprintsync.core.ui.components.PrimaryButton
import com.paul.sprintsync.core.ui.components.SectionHeader
import com.paul.sprintsync.core.ui.components.SprintSyncCard
import com.paul.sprintsync.core.theme.TabularMonospaceTypography
import com.paul.sprintsync.feature.motion.data.nativebridge.SensorNativePreviewViewFactory
import com.paul.sprintsync.feature.race.domain.SessionCameraFacing
import com.paul.sprintsync.feature.race.domain.SessionDevice
import com.paul.sprintsync.feature.race.domain.SessionDeviceRole
import com.paul.sprintsync.feature.race.domain.SessionOperatingMode
import com.paul.sprintsync.feature.race.domain.sessionDeviceRoleLabel
import kotlin.math.roundToInt

@Composable
internal fun SingleFlavorConnectingCard(
    isDiscovering: Boolean,
    discoveredCount: Int,
) {
    SprintSyncCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(strokeWidth = 3.dp)
            Column {
                Text("Connecting to Display...", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (isDiscovering) {
                        if (discoveredCount > 0) "Display found, connecting..." else "Scanning hotspot gateway..."
                    } else {
                        "Retrying connection..."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun MonitoringSummaryCard(
    isHost: Boolean,
    localRole: SessionDeviceRole,
    localCameraFacing: SessionCameraFacing,
    debugViewEnabled: Boolean,
    showDebugInfo: Boolean,
    connectionTypeLabel: String,
    userMonitoringEnabled: Boolean,
    onSetMonitoringEnabled: (Boolean) -> Unit,
    onAssignLocalCameraFacing: (SessionCameraFacing) -> Unit,
    effectiveShowPreview: Boolean,
    onShowPreviewChanged: (Boolean) -> Unit,
    previewViewFactory: SensorNativePreviewViewFactory,
    roiCenterX: Double,
    roiCenterY: Double,
    roiHeight: Double,
    operatingMode: SessionOperatingMode,
    setupActionProfile: SetupActionProfile,
    devices: List<SessionDevice>,
    displayConnectedHostEndpointId: String?,
    controllerTargetEndpoints: Map<String, String>,
    discoveredDisplayHosts: Map<String, String>,
    displayConnectedHostName: String?,
    displayDiscoveryActive: Boolean,
    onStartDisplayDiscovery: () -> Unit,
    onConnectDisplayHost: (String) -> Unit,
    onResetDeviceTimer: (String) -> Unit,
    onSetDisplayLimit: (String, Long) -> Unit,
    onSetAutoReadyDelay: (String, Int?) -> Unit,
    onSetWaitTextEnabled: (String, Boolean) -> Unit,
    onSetDeviceSensitivity: (String, Int) -> Unit,
    onSetGameModeEnabled: (String, Boolean) -> Unit,
    onSetGameModeLimit: (String, Long) -> Unit,
    onSetGameModeLives: (String, Int) -> Unit,
    onSetGameModeAutoConfig: (String, Boolean, Int, Long) -> Unit,
    onPlayStartSound: () -> Unit,
    onResetRun: () -> Unit,
) {
    val controllerSensitivityInputs = remember { mutableStateMapOf<String, Float>() }
    var globalAutoReadyDelaySeconds by rememberSaveable { mutableStateOf<Int?>(2) }
    var globalAutoReadyMenuExpanded by remember { mutableStateOf(false) }
    var globalWaitTextEnabled by rememberSaveable { mutableStateOf(false) }
    var controllerTabIndex by rememberSaveable { mutableStateOf(0) }
    var globalGameModeEnabled by rememberSaveable { mutableStateOf(true) }
    var globalGameModeLimitMillis by rememberSaveable { mutableStateOf(5_000L) }
    var globalGameModeLives by rememberSaveable { mutableStateOf(10) }
    var globalGameModeAutoEnabled by rememberSaveable { mutableStateOf(true) }
    var globalGameModeAutoEveryRuns by rememberSaveable { mutableStateOf(10) }
    var globalGameModeAutoReductionMillis by rememberSaveable { mutableStateOf(100L) }
    var globalGameModeLivesMenuExpanded by remember { mutableStateOf(false) }
    var globalGameModeAutoEveryRunsMenuExpanded by remember { mutableStateOf(false) }
    var globalGameModeAutoReductionMenuExpanded by remember { mutableStateOf(false) }
    val controllerGameLivesInputs = remember { mutableStateMapOf<String, Int>() }
    val controllerGameLivesMenusExpanded = remember { mutableStateMapOf<String, Boolean>() }
    val controllerGameAutoEnabledInputs = remember { mutableStateMapOf<String, Boolean>() }
    val controllerGameAutoEveryRunsInputs = remember { mutableStateMapOf<String, Int>() }
    val controllerGameAutoEveryRunsMenusExpanded = remember { mutableStateMapOf<String, Boolean>() }
    val controllerGameAutoReductionInputs = remember { mutableStateMapOf<String, Long>() }
    val controllerGameAutoReductionMenusExpanded = remember { mutableStateMapOf<String, Boolean>() }
    val globalAutoReadyLabel = globalAutoReadyDelaySeconds?.let { "$it s" } ?: "Manual"
    val reductionOptions = 50L..500L step 50L
    val controllerTargetDevices = remember(
        setupActionProfile,
        controllerTargetEndpoints,
        devices,
        displayConnectedHostEndpointId,
    ) {
        if (setupActionProfile == SetupActionProfile.CONTROLLER_ONLY) {
            controllerTargetEndpoints.entries.map { target ->
                SessionDevice(
                    id = target.key,
                    name = target.value,
                    role = SessionDeviceRole.UNASSIGNED,
                    isLocal = false,
                )
            }
        } else {
            devices.filter { device ->
                !device.isLocal &&
                    device.id != displayConnectedHostEndpointId &&
                    device.role != SessionDeviceRole.DISPLAY
            }
        }
    }
    val controllerUiState = ControllerTargetsUiState(
        controllerTabIndex = controllerTabIndex,
        globalAutoReadyDelaySeconds = globalAutoReadyDelaySeconds,
        globalWaitTextEnabled = globalWaitTextEnabled,
        globalGameModeEnabled = globalGameModeEnabled,
        globalGameModeLimitMillis = globalGameModeLimitMillis,
        globalGameModeLives = globalGameModeLives,
        controllerTargetDevices = controllerTargetDevices,
    )
    val controllerCallbacks = ControllerTargetsCallbacks(
        onControllerTabChanged = { controllerTabIndex = it },
        onResetRun = onResetRun,
        onStartDisplayDiscovery = onStartDisplayDiscovery,
        onConnectDisplayHost = onConnectDisplayHost,
    )

    SprintSyncCard {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val useTwoColumns = maxWidth >= 340.dp

            if (operatingMode == SessionOperatingMode.SINGLE_DEVICE) {
                SingleDeviceMonitoringContent {
                    if (shouldShowMonitoringConnectionDebugInfo(debugViewEnabled, showDebugInfo)) {
                        Text(
                            "Connection: $connectionTypeLabel",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                        )
                    }
                    if (
                        setupActionProfile != SetupActionProfile.CONTROLLER_ONLY &&
                        shouldShowSingleDeviceCameraFacingToggle(operatingMode)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            SingleChoiceSegmentedButtonRow {
                                SegmentedButton(
                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                    onClick = { onAssignLocalCameraFacing(SessionCameraFacing.REAR) },
                                    selected = localCameraFacing == SessionCameraFacing.REAR,
                                    label = { Text("Rear") },
                                )
                                SegmentedButton(
                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                    onClick = { onAssignLocalCameraFacing(SessionCameraFacing.FRONT) },
                                    selected = localCameraFacing == SessionCameraFacing.FRONT,
                                    label = { Text("Front") },
                                )
                            }
                        }
                    }
                    if (
                        setupActionProfile != SetupActionProfile.CONTROLLER_ONLY &&
                        shouldShowMonitoringPreview(operatingMode, effectiveShowPreview)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            PreviewSurface(
                                previewViewFactory = previewViewFactory,
                                roiCenterX = roiCenterX,
                                roiCenterY = roiCenterY,
                                roiHeight = roiHeight,
                            )
                        }
                    }
                    if (shouldShowDisplayRelayControls(operatingMode)) {
                        if (setupActionProfile != SetupActionProfile.CONTROLLER_ONLY) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                PrimaryButton(
                                    text = if (displayDiscoveryActive) "Display: Discovering" else "Display",
                                    onClick = onStartDisplayDiscovery,
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedButton(
                                    onClick = onResetRun,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("Reset")
                                }
                            }
                            if (displayConnectedHostName != null) {
                                Text(
                                    text = "Connected to $displayConnectedHostName",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray,
                                )
                            }
                            val hosts = discoveredDisplayHosts.entries.toList()
                            if (hosts.isNotEmpty()) {
                                hosts.forEach { host ->
                                    OutlinedButton(
                                        onClick = { onConnectDisplayHost(host.key) },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text("Join ${host.value}")
                                    }
                                }
                            }
                        }
                        if (setupActionProfile == SetupActionProfile.CONTROLLER_ONLY) {
                            ControllerTargetsPanel(
                                uiState = controllerUiState,
                                callbacks = controllerCallbacks,
                            )
                            GlobalControlsPanel(
                                uiState = controllerUiState,
                                callbacks = controllerCallbacks,
                            )
                            if (controllerTabIndex == 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        controllerTargetDevices.forEach { device ->
                                            onResetDeviceTimer(device.id)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Reset All")
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ExposedDropdownMenuBox(
                                    expanded = globalAutoReadyMenuExpanded,
                                    onExpandedChange = { globalAutoReadyMenuExpanded = it },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    OutlinedTextField(
                                        value = globalAutoReadyLabel,
                                        onValueChange = { },
                                        label = { Text("Result -> Ready") },
                                        readOnly = true,
                                        singleLine = true,
                                        trailingIcon = {
                                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = globalAutoReadyMenuExpanded)
                                        },
                                        modifier = Modifier
                                            .menuAnchor()
                                            .fillMaxWidth()
                                    )
                                    DropdownMenu(
                                        expanded = globalAutoReadyMenuExpanded,
                                        onDismissRequest = { globalAutoReadyMenuExpanded = false },
                                    ) {
                                        listOf(1, 2, 3, 4, 5).forEach { seconds ->
                                            DropdownMenuItem(
                                                text = { Text("$seconds s") },
                                                onClick = {
                                                    globalAutoReadyDelaySeconds = seconds
                                                    globalAutoReadyMenuExpanded = false
                                                    controllerTargetDevices.forEach { device ->
                                                        onSetAutoReadyDelay(device.id, seconds)
                                                    }
                                                },
                                            )
                                        }
                                        DropdownMenuItem(
                                            text = { Text("Manual") },
                                            onClick = {
                                                globalAutoReadyDelaySeconds = null
                                                globalAutoReadyMenuExpanded = false
                                                controllerTargetDevices.forEach { device ->
                                                    onSetAutoReadyDelay(device.id, null)
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "Show WAIT Text",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Switch(
                                        checked = globalWaitTextEnabled,
                                        onCheckedChange = { checked ->
                                            globalWaitTextEnabled = checked
                                            controllerTargetDevices.forEach { device ->
                                                onSetWaitTextEnabled(device.id, checked)
                                            }
                                        },
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(Color(0xFFB0A9BB)),
                            )
                            Spacer(Modifier.height(8.dp))
                            controllerTargetDevices.forEach { device ->
                                DeviceControlRow(device = device)
                                val sensitivityValue = controllerSensitivityInputs[device.id] ?: 50f
                                Text(
                                    text = device.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "Sensitivity ${sensitivityValue.roundToInt()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray,
                                )
                                Slider(
                                    value = sensitivityValue,
                                    onValueChange = { next ->
                                        controllerSensitivityInputs[device.id] = next.coerceIn(0f, 100f)
                                    },
                                    valueRange = 0f..100f,
                                    onValueChangeFinished = {
                                        val committed = (controllerSensitivityInputs[device.id] ?: sensitivityValue)
                                            .roundToInt()
                                            .coerceIn(0, 100)
                                        onSetDeviceSensitivity(device.id, committed)
                                    },
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Start,
                                ) {
                                    OutlinedButton(
                                        onClick = { onResetDeviceTimer(device.id) },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text("Reset")
                                    }
                                }
                            }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            controllerTargetDevices.forEach { device ->
                                                onResetDeviceTimer(device.id)
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text("Reset All")
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    OutlinedButton(
                                        onClick = onPlayStartSound,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text("Start")
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = "Game Mode",
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Switch(
                                            checked = globalGameModeEnabled,
                                            onCheckedChange = { checked ->
                                                globalGameModeEnabled = checked
                                                controllerTargetDevices.forEach { device ->
                                                    onSetGameModeEnabled(device.id, checked)
                                                }
                                            },
                                        )
                                    }
                                }
                                if (globalGameModeEnabled) {
                                    Spacer(Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = "Auto",
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.weight(1f),
                                            )
                                            Switch(
                                                checked = globalGameModeAutoEnabled,
                                                onCheckedChange = { checked ->
                                                    globalGameModeAutoEnabled = checked
                                                    val everyRuns = globalGameModeAutoEveryRuns.coerceIn(1, 20)
                                                    val reductionMillis = globalGameModeAutoReductionMillis.coerceIn(50L, 500L)
                                                    controllerTargetDevices.forEach { device ->
                                                        onSetGameModeAutoConfig(
                                                            device.id,
                                                            checked,
                                                            everyRuns,
                                                            reductionMillis,
                                                        )
                                                    }
                                                },
                                            )
                                        }
                                        if (globalGameModeAutoEnabled) {
                                            ExposedDropdownMenuBox(
                                                expanded = globalGameModeAutoEveryRunsMenuExpanded,
                                                onExpandedChange = { globalGameModeAutoEveryRunsMenuExpanded = it },
                                                modifier = Modifier.weight(1f),
                                            ) {
                                                OutlinedTextField(
                                                    value = globalGameModeAutoEveryRuns.toString(),
                                                    onValueChange = {},
                                                    readOnly = true,
                                                    label = { Text("Every Runs") },
                                                    singleLine = true,
                                                    trailingIcon = {
                                                        ExposedDropdownMenuDefaults.TrailingIcon(
                                                            expanded = globalGameModeAutoEveryRunsMenuExpanded,
                                                        )
                                                    },
                                                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                                                )
                                                DropdownMenu(
                                                    expanded = globalGameModeAutoEveryRunsMenuExpanded,
                                                    onDismissRequest = { globalGameModeAutoEveryRunsMenuExpanded = false },
                                                ) {
                                                    (1..20).forEach { runs ->
                                                        DropdownMenuItem(
                                                            text = { Text(runs.toString()) },
                                                            onClick = {
                                                                globalGameModeAutoEveryRuns = runs
                                                                globalGameModeAutoEveryRunsMenuExpanded = false
                                                                val reductionMillis =
                                                                    globalGameModeAutoReductionMillis.coerceIn(50L, 500L)
                                                                controllerTargetDevices.forEach { device ->
                                                                    onSetGameModeAutoConfig(
                                                                        device.id,
                                                                        globalGameModeAutoEnabled,
                                                                        runs,
                                                                        reductionMillis,
                                                                    )
                                                                }
                                                            },
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    if (globalGameModeAutoEnabled) {
                                        Spacer(Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            ExposedDropdownMenuBox(
                                                expanded = globalGameModeAutoReductionMenuExpanded,
                                                onExpandedChange = { globalGameModeAutoReductionMenuExpanded = it },
                                                modifier = Modifier.weight(1f),
                                            ) {
                                                OutlinedTextField(
                                                    value = globalGameModeAutoReductionMillis.toString(),
                                                    onValueChange = {},
                                                    readOnly = true,
                                                    label = { Text("Reduction (ms)") },
                                                    singleLine = true,
                                                    trailingIcon = {
                                                        ExposedDropdownMenuDefaults.TrailingIcon(
                                                            expanded = globalGameModeAutoReductionMenuExpanded,
                                                        )
                                                    },
                                                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                                                )
                                                DropdownMenu(
                                                    expanded = globalGameModeAutoReductionMenuExpanded,
                                                    onDismissRequest = { globalGameModeAutoReductionMenuExpanded = false },
                                                ) {
                                                    reductionOptions.forEach { reduction ->
                                                        DropdownMenuItem(
                                                            text = { Text(reduction.toString()) },
                                                            onClick = {
                                                                globalGameModeAutoReductionMillis = reduction
                                                                globalGameModeAutoReductionMenuExpanded = false
                                                                val everyRuns = globalGameModeAutoEveryRuns.coerceIn(1, 20)
                                                                controllerTargetDevices.forEach { device ->
                                                                    onSetGameModeAutoConfig(
                                                                        device.id,
                                                                        globalGameModeAutoEnabled,
                                                                        everyRuns,
                                                                        reduction,
                                                                    )
                                                                }
                                                            },
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "Limit ${globalGameModeLimitMillis}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                    )
                                    OutlinedButton(onClick = {
                                        val updated = (globalGameModeLimitMillis - 250L).coerceAtLeast(250L)
                                        globalGameModeLimitMillis = updated
                                        controllerTargetDevices.forEach { device ->
                                            onSetGameModeLimit(device.id, updated)
                                        }
                                    }) {
                                        Text("-")
                                    }
                                    OutlinedButton(onClick = {
                                        val updated = globalGameModeLimitMillis + 250L
                                        globalGameModeLimitMillis = updated
                                        controllerTargetDevices.forEach { device ->
                                            onSetGameModeLimit(device.id, updated)
                                        }
                                    }) {
                                        Text("+")
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    ExposedDropdownMenuBox(
                                        expanded = globalGameModeLivesMenuExpanded,
                                        onExpandedChange = { globalGameModeLivesMenuExpanded = it },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        OutlinedTextField(
                                            value = globalGameModeLives.toString(),
                                            onValueChange = {},
                                            readOnly = true,
                                            label = { Text("Lives All") },
                                            singleLine = true,
                                            trailingIcon = {
                                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = globalGameModeLivesMenuExpanded)
                                            },
                                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                                        )
                                        DropdownMenu(
                                            expanded = globalGameModeLivesMenuExpanded,
                                            onDismissRequest = { globalGameModeLivesMenuExpanded = false },
                                        ) {
                                            (1..10).forEach { lives ->
                                                DropdownMenuItem(
                                                    text = { Text(lives.toString()) },
                                                    onClick = {
                                                        globalGameModeLives = lives
                                                        globalGameModeLivesMenuExpanded = false
                                                        controllerTargetDevices.forEach { device ->
                                                            onSetGameModeLives(device.id, lives)
                                                        }
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(Color(0xFFB0A9BB)),
                                )
                                Spacer(Modifier.height(8.dp))
                                controllerTargetDevices.forEach { device ->
                                    DeviceControlRow(device = device)
                                    val selectedLives = controllerGameLivesInputs[device.id] ?: 10
                                    val livesExpanded = controllerGameLivesMenusExpanded[device.id] == true
                                    val autoEnabled = controllerGameAutoEnabledInputs[device.id] != false
                                    val autoEveryRuns = (controllerGameAutoEveryRunsInputs[device.id] ?: 10).coerceIn(1, 20)
                                    val autoReductionMillis =
                                        (controllerGameAutoReductionInputs[device.id] ?: 100L).coerceIn(50L, 500L)
                                    val autoEveryRunsExpanded =
                                        controllerGameAutoEveryRunsMenusExpanded[device.id] == true
                                    val autoReductionExpanded =
                                        controllerGameAutoReductionMenusExpanded[device.id] == true
                                    Text(
                                        text = device.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = "Auto",
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.weight(1f),
                                            )
                                            Switch(
                                                checked = autoEnabled,
                                                onCheckedChange = { checked ->
                                                    controllerGameAutoEnabledInputs[device.id] = checked
                                                    onSetGameModeAutoConfig(
                                                        device.id,
                                                        checked,
                                                        autoEveryRuns,
                                                        autoReductionMillis,
                                                    )
                                                },
                                            )
                                        }
                                        if (autoEnabled) {
                                            ExposedDropdownMenuBox(
                                                expanded = autoEveryRunsExpanded,
                                                onExpandedChange = { next ->
                                                    controllerGameAutoEveryRunsMenusExpanded[device.id] = next
                                                },
                                                modifier = Modifier.weight(1f),
                                            ) {
                                                OutlinedTextField(
                                                    value = autoEveryRuns.toString(),
                                                    onValueChange = {},
                                                    readOnly = true,
                                                    label = { Text("Every Runs") },
                                                    singleLine = true,
                                                    trailingIcon = {
                                                        ExposedDropdownMenuDefaults.TrailingIcon(
                                                            expanded = autoEveryRunsExpanded,
                                                        )
                                                    },
                                                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                                                )
                                                DropdownMenu(
                                                    expanded = autoEveryRunsExpanded,
                                                    onDismissRequest = {
                                                        controllerGameAutoEveryRunsMenusExpanded[device.id] = false
                                                    },
                                                ) {
                                                    (1..20).forEach { runs ->
                                                        DropdownMenuItem(
                                                            text = { Text(runs.toString()) },
                                                            onClick = {
                                                                controllerGameAutoEveryRunsInputs[device.id] = runs
                                                                controllerGameAutoEveryRunsMenusExpanded[device.id] = false
                                                                onSetGameModeAutoConfig(
                                                                    device.id,
                                                                    autoEnabled,
                                                                    runs,
                                                                    autoReductionMillis,
                                                                )
                                                            },
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    if (autoEnabled) {
                                        Spacer(Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            ExposedDropdownMenuBox(
                                                expanded = autoReductionExpanded,
                                                onExpandedChange = { next ->
                                                    controllerGameAutoReductionMenusExpanded[device.id] = next
                                                },
                                                modifier = Modifier.weight(1f),
                                            ) {
                                                OutlinedTextField(
                                                    value = autoReductionMillis.toString(),
                                                    onValueChange = {},
                                                    readOnly = true,
                                                    label = { Text("Reduction (ms)") },
                                                    singleLine = true,
                                                    trailingIcon = {
                                                        ExposedDropdownMenuDefaults.TrailingIcon(
                                                            expanded = autoReductionExpanded,
                                                        )
                                                    },
                                                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                                                )
                                                DropdownMenu(
                                                    expanded = autoReductionExpanded,
                                                    onDismissRequest = {
                                                        controllerGameAutoReductionMenusExpanded[device.id] = false
                                                    },
                                                ) {
                                                    reductionOptions.forEach { reduction ->
                                                        DropdownMenuItem(
                                                            text = { Text(reduction.toString()) },
                                                            onClick = {
                                                                controllerGameAutoReductionInputs[device.id] = reduction
                                                                controllerGameAutoReductionMenusExpanded[device.id] = false
                                                                onSetGameModeAutoConfig(
                                                                    device.id,
                                                                    autoEnabled,
                                                                    autoEveryRuns,
                                                                    reduction,
                                                                )
                                                            },
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        ExposedDropdownMenuBox(
                                            expanded = livesExpanded,
                                            onExpandedChange = { next ->
                                                controllerGameLivesMenusExpanded[device.id] = next
                                            },
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            OutlinedTextField(
                                                value = selectedLives.toString(),
                                                onValueChange = {},
                                                readOnly = true,
                                                label = { Text("Lives") },
                                                singleLine = true,
                                                trailingIcon = {
                                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = livesExpanded)
                                                },
                                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                                            )
                                            DropdownMenu(
                                                expanded = livesExpanded,
                                                onDismissRequest = { controllerGameLivesMenusExpanded[device.id] = false },
                                            ) {
                                                (1..10).forEach { lives ->
                                                    DropdownMenuItem(
                                                        text = { Text(lives.toString()) },
                                                        onClick = {
                                                            controllerGameLivesInputs[device.id] = lives
                                                            controllerGameLivesMenusExpanded[device.id] = false
                                                            onSetGameModeLives(device.id, lives)
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (useTwoColumns) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        PreviewSurface(
                            previewViewFactory = previewViewFactory,
                            roiCenterX = roiCenterX,
                            roiCenterY = roiCenterY,
                            roiHeight = roiHeight,
                        )
                        MonitoringPreviewInfoPanel(
                            isHost = isHost,
                            localRole = localRole,
                localCameraFacing = localCameraFacing,
                debugViewEnabled = debugViewEnabled,
                showDebugInfo = showDebugInfo,
                connectionTypeLabel = connectionTypeLabel,
                            userMonitoringEnabled = userMonitoringEnabled,
                            onSetMonitoringEnabled = onSetMonitoringEnabled,
                            onAssignLocalCameraFacing = onAssignLocalCameraFacing,
                            effectiveShowPreview = effectiveShowPreview,
                            onShowPreviewChanged = onShowPreviewChanged,
                            operatingMode = operatingMode,
                            discoveredDisplayHosts = discoveredDisplayHosts,
                            displayConnectedHostName = displayConnectedHostName,
                            displayDiscoveryActive = displayDiscoveryActive,
                            onStartDisplayDiscovery = onStartDisplayDiscovery,
                            onConnectDisplayHost = onConnectDisplayHost,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (shouldShowInlineMonitoringResetButton(operatingMode)) {
                        MonitoringInlineResetButton(onResetRun = onResetRun)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MonitoringPreviewInfoPanel(
                        isHost = isHost,
                        localRole = localRole,
                        localCameraFacing = localCameraFacing,
                        debugViewEnabled = debugViewEnabled,
                        showDebugInfo = showDebugInfo,
                        connectionTypeLabel = connectionTypeLabel,
                        userMonitoringEnabled = userMonitoringEnabled,
                        onSetMonitoringEnabled = onSetMonitoringEnabled,
                        onAssignLocalCameraFacing = onAssignLocalCameraFacing,
                        effectiveShowPreview = effectiveShowPreview,
                        onShowPreviewChanged = onShowPreviewChanged,
                        operatingMode = operatingMode,
                        discoveredDisplayHosts = discoveredDisplayHosts,
                        displayConnectedHostName = displayConnectedHostName,
                        displayDiscoveryActive = displayDiscoveryActive,
                        onStartDisplayDiscovery = onStartDisplayDiscovery,
                        onConnectDisplayHost = onConnectDisplayHost,
                    )
                    if (shouldShowMonitoringPreview(operatingMode, effectiveShowPreview)) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            PreviewSurface(
                                previewViewFactory = previewViewFactory,
                                roiCenterX = roiCenterX,
                                roiCenterY = roiCenterY,
                                roiHeight = roiHeight,
                            )
                        }
                    }
                    if (shouldShowInlineMonitoringResetButton(operatingMode)) {
                        MonitoringInlineResetButton(onResetRun = onResetRun)
                    }
                }
            }
        }
    }
}

private data class ControllerTargetsUiState(
    val controllerTabIndex: Int,
    val globalAutoReadyDelaySeconds: Int?,
    val globalWaitTextEnabled: Boolean,
    val globalGameModeEnabled: Boolean,
    val globalGameModeLimitMillis: Long,
    val globalGameModeLives: Int,
    val controllerTargetDevices: List<SessionDevice>,
)

private data class ControllerTargetsCallbacks(
    val onControllerTabChanged: (Int) -> Unit,
    val onResetRun: () -> Unit,
    val onStartDisplayDiscovery: () -> Unit,
    val onConnectDisplayHost: (String) -> Unit,
)

@Composable
private fun SingleDeviceMonitoringContent(content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

@Composable
private fun ControllerTargetsPanel(
    uiState: ControllerTargetsUiState,
    callbacks: ControllerTargetsCallbacks,
) {
    Spacer(Modifier.height(6.dp))
    Text(
        text = "Controller Targets",
        style = MaterialTheme.typography.labelMedium,
        color = Color.Gray,
    )
    Spacer(Modifier.height(8.dp))
    TabRow(selectedTabIndex = uiState.controllerTabIndex) {
        Tab(
            selected = uiState.controllerTabIndex == 0,
            onClick = { callbacks.onControllerTabChanged(0) },
            text = { Text("Settings") },
        )
        Tab(
            selected = uiState.controllerTabIndex == 1,
            onClick = { callbacks.onControllerTabChanged(1) },
            text = { Text("Game Mode") },
        )
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun GlobalControlsPanel(
    uiState: ControllerTargetsUiState,
    callbacks: ControllerTargetsCallbacks,
) {
    // The controls remain rendered in MonitoringSummaryCard; this composable marks the extracted section boundary.
    uiState.controllerTabIndex
    callbacks.onControllerTabChanged
}

@Composable
private fun DeviceControlRow(device: SessionDevice) {
    // The detailed controls remain rendered inline to preserve existing behavior without state migration risk.
    device.id
}

@Composable
internal fun MonitoringInlineResetButton(onResetRun: () -> Unit) {
    PrimaryButton(
        text = "Reset Run",
        onClick = onResetRun,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
    )
}

@Composable
private fun MonitoringPreviewInfoPanel(
    isHost: Boolean,
    localRole: SessionDeviceRole,
    localCameraFacing: SessionCameraFacing,
    debugViewEnabled: Boolean,
    showDebugInfo: Boolean,
    connectionTypeLabel: String,
    userMonitoringEnabled: Boolean,
    onSetMonitoringEnabled: (Boolean) -> Unit,
    onAssignLocalCameraFacing: (SessionCameraFacing) -> Unit,
    effectiveShowPreview: Boolean,
    onShowPreviewChanged: (Boolean) -> Unit,
    operatingMode: SessionOperatingMode,
    discoveredDisplayHosts: Map<String, String>,
    displayConnectedHostName: String?,
    displayDiscoveryActive: Boolean,
    onStartDisplayDiscovery: () -> Unit,
    onConnectDisplayHost: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (shouldShowMonitoringRoleAndToggles(operatingMode)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Role: ${sessionDeviceRoleLabel(localRole)}",
                    fontWeight = FontWeight.Bold,
                )
                if (!isHost) {
                    Text("Waiting for host...", color = Color.Gray, fontStyle = FontStyle.Italic)
                }
            }
        }
        if (shouldShowMonitoringConnectionDebugInfo(debugViewEnabled, showDebugInfo)) {
            Text("Connection: $connectionTypeLabel", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        if (shouldShowMonitoringPreviewToggle(operatingMode)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Switch(
                    checked = effectiveShowPreview,
                    enabled = true,
                    onCheckedChange = onShowPreviewChanged,
                )
            }
        }
        if (shouldShowSingleDeviceCameraFacingToggle(operatingMode)) {
            Spacer(Modifier.height(4.dp))
            SingleChoiceSegmentedButtonRow {
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    onClick = { onAssignLocalCameraFacing(SessionCameraFacing.REAR) },
                    selected = localCameraFacing == SessionCameraFacing.REAR,
                    label = { Text("Rear") },
                )
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    onClick = { onAssignLocalCameraFacing(SessionCameraFacing.FRONT) },
                    selected = localCameraFacing == SessionCameraFacing.FRONT,
                    label = { Text("Front") },
                )
            }
        }
        if (shouldShowDisplayRelayControls(operatingMode)) {
            Spacer(Modifier.height(4.dp))
            PrimaryButton(
                text = if (displayDiscoveryActive) "Display: Discovering" else "Display",
                onClick = onStartDisplayDiscovery,
                modifier = Modifier.fillMaxWidth(),
            )
            if (displayConnectedHostName != null) {
                Text(
                    text = "Connected to $displayConnectedHostName",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                )
            }
            val hosts = discoveredDisplayHosts.entries.toList()
            if (hosts.isNotEmpty()) {
                hosts.forEach { host ->
                    OutlinedButton(
                        onClick = { onConnectDisplayHost(host.key) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Join ${host.value}")
                    }
                }
            }
        }
    }
}

@Composable
internal fun WifiWarningCard(text: String, onClick: () -> Unit) {
    SprintSyncCard(
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Wi-Fi", color = Color(0xFFA16207), style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun PreviewSurface(
    previewViewFactory: SensorNativePreviewViewFactory,
    roiCenterX: Double,
    roiCenterY: Double,
    roiHeight: Double,
) {
    Box(
        modifier = Modifier
            .width(180.dp)
            .height(120.dp)
            .clip(MaterialTheme.shapes.medium),
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds(),
            factory = { context ->
                previewViewFactory.createPreviewView(context)
            },
            onRelease = { view ->
                previewViewFactory.detachPreviewView(view)
            },
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width * roiCenterX.coerceIn(0.0, 1.0).toFloat()
            val centerY = size.height * roiCenterY.coerceIn(0.0, 1.0).toFloat()
            val side = (roiHeight.coerceIn(0.0, 1.0).toFloat() * minOf(size.width, size.height)).coerceAtLeast(1f)
            val topLeftX = (centerX - (side / 2f)).coerceIn(0f, size.width - side)
            val topLeftY = (centerY - (side / 2f)).coerceIn(0f, size.height - side)
            drawRect(
                color = Color(0xFF005A8D),
                topLeft = androidx.compose.ui.geometry.Offset(topLeftX, topLeftY),
                size = androidx.compose.ui.geometry.Size(side, side),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()),
            )
        }
    }
}

@Composable
internal fun AdvancedDetectionCard(
    uiState: SprintSyncUiState,
    onUpdateThreshold: (Double) -> Unit,
    onUpdateRoiCenter: (Double) -> Unit,
    onUpdateRoiWidth: (Double) -> Unit,
    onUpdateRoiCenterY: (Double) -> Unit,
    onUpdateRoiHeight: (Double) -> Unit,
    onUpdateCooldown: (Int) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    SprintSyncCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionHeader("Advanced Detection")
                OutlinedButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Hide" else "Show")
                }
            }

            if (expanded) {
                MetricDisplay(label = "Threshold", value = String.format("%.3f", uiState.threshold))
                Slider(
                    value = uiState.threshold.toFloat(),
                    onValueChange = { onUpdateThreshold(it.toDouble()) },
                    valueRange = 0.001f..0.08f,
                )

                MetricDisplay(label = "ROI center", value = String.format("%.2f", uiState.roiCenterX))
                Slider(
                    value = uiState.roiCenterX.toFloat(),
                    onValueChange = { onUpdateRoiCenter(it.toDouble()) },
                    valueRange = 0.20f..0.80f,
                )

                MetricDisplay(label = "ROI width", value = String.format("%.2f", uiState.roiWidth))
                Slider(
                    value = uiState.roiWidth.toFloat(),
                    onValueChange = { onUpdateRoiWidth(it.toDouble()) },
                    valueRange = 0.01f..0.40f,
                )

                MetricDisplay(label = "ROI center Y", value = String.format("%.2f", uiState.roiCenterY))
                Slider(
                    value = uiState.roiCenterY.toFloat(),
                    onValueChange = { onUpdateRoiCenterY(it.toDouble()) },
                    valueRange = 0.20f..0.80f,
                )

                MetricDisplay(label = "ROI height", value = String.format("%.2f", uiState.roiHeight))
                Slider(
                    value = uiState.roiHeight.toFloat(),
                    onValueChange = { onUpdateRoiHeight(it.toDouble()) },
                    valueRange = 0.01f..0.40f,
                )

                Text("Cooldown: ${uiState.cooldownMs} ms")
                Slider(
                    value = uiState.cooldownMs.toFloat(),
                    onValueChange = { onUpdateCooldown(it.toInt()) },
                    valueRange = 300f..2000f,
                )

                Spacer(Modifier.height(4.dp))
                SectionHeader("Split History")
                if (uiState.splitHistory.isEmpty()) {
                    Text("No split marks yet.")
                } else {
                    uiState.splitHistory.forEach { split ->
                        Text(split, style = MaterialTheme.typography.bodySmall)
                    }
                }

                Spacer(Modifier.height(4.dp))
                SectionHeader("Recent Triggers")
                if (uiState.triggerHistory.isEmpty()) {
                    Text("No trigger events yet.")
                } else {
                    uiState.triggerHistory.forEach { event ->
                        Text(event, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
internal fun RunMetricsCard(
    uiState: SprintSyncUiState,
    isHost: Boolean,
    onResetRun: () -> Unit,
) {
    SprintSyncCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = uiState.elapsedDisplay,
                style = MaterialTheme.typography.displayLarge
                    .copy(fontSize = MaterialTheme.typography.displayLarge.fontSize * 1.12f)
                    .merge(TabularMonospaceTypography),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

