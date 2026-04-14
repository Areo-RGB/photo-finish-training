package com.paul.sprintsync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.paul.sprintsync.core.ui.components.PrimaryButton
import com.paul.sprintsync.core.ui.components.SectionHeader
import com.paul.sprintsync.core.ui.components.SprintSyncCard
import com.paul.sprintsync.feature.race.domain.SessionDevice

@Composable
internal fun PermissionWarningCard(deniedPermissions: List<String>) {
    SprintSyncCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionHeader("Permissions Needed")
            Text(
                text = "Grant permissions to host or join TCP-connected devices.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = deniedPermissions.joinToString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
internal fun SetupActionsCard(
    setupActionProfile: SetupActionProfile,
    permissionGranted: Boolean,
    setupBusy: Boolean,
    onRequestPermissions: () -> Unit,
    onStartSingleDevice: () -> Unit,
    onStartDisplayHost: () -> Unit,
) {
    val setupActionsEnabled = !setupBusy
    val showSingleAction = setupActionProfile != SetupActionProfile.DISPLAY_ONLY
    val showDisplayAction = setupActionProfile == SetupActionProfile.DISPLAY_ONLY
    val singleActionLabel = if (setupActionProfile == SetupActionProfile.CONTROLLER_ONLY) {
        "Controller"
    } else {
        "Single Device"
    }

    SprintSyncCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader("Network Connection")
            if (!permissionGranted) {
                PrimaryButton(
                    text = "Grant Permissions",
                    onClick = onRequestPermissions,
                    enabled = setupActionsEnabled,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (showSingleAction) {
                PrimaryButton(
                    text = singleActionLabel,
                    onClick = onStartSingleDevice,
                    enabled = setupActionsEnabled,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (showDisplayAction) {
                PrimaryButton(
                    text = "Display",
                    onClick = onStartDisplayHost,
                    enabled = setupActionsEnabled,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (!permissionGranted) {
                Text(
                    text = "Camera and network permissions are required.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                )
            }
        }
    }
}


@Composable
internal fun ConnectedDevicesListCard(devices: List<SessionDevice>, showDebugInfo: Boolean) {
    SprintSyncCard {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionHeader("Connected Devices")
            devices.forEach { device ->
                Text(if (device.isLocal) "${device.name} (Local)" else device.name)
                if (showDebugInfo) {
                    Text(device.id, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

