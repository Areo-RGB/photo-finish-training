package com.paul.sprintsync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.paul.sprintsync.core.ui.components.MetricDisplay
import com.paul.sprintsync.core.ui.components.SectionHeader
import com.paul.sprintsync.core.ui.components.SprintSyncCard

@Composable
internal fun StatusCard(uiState: SprintSyncUiState) {
    SprintSyncCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionHeader("Session Status")
            MetricDisplay(label = "Stage", value = uiState.sessionSummary)
            MetricDisplay(label = "Network", value = uiState.networkSummary)
            MetricDisplay(label = "Motion", value = uiState.monitoringSummary)
            MetricDisplay(label = "Clock", value = uiState.clockSummary)
            uiState.lastNearbyEvent?.let { Text("Last Connection Event: $it") }
            if (!uiState.permissionGranted && uiState.deniedPermissions.isNotEmpty()) {
                Text(
                    "Missing permissions: ${uiState.deniedPermissions.joinToString()}",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
internal fun SensorDebugViewCard(uiState: SprintSyncUiState) {
    val fpsLabel = uiState.observedFps?.let { String.format("%.1f", it) } ?: "--.-"
    val targetSuffix = uiState.targetFpsUpper?.let { " · target $it" } ?: ""
    val rawScoreLabel = uiState.rawScore?.let { "%.4f".format(it) } ?: "-"
    val baselineLabel = uiState.baseline?.let { "%.4f".format(it) } ?: "-"
    val effectiveScoreLabel = uiState.effectiveScore?.let { "%.4f".format(it) } ?: "-"
    SprintSyncCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionHeader("Sensor Debug")
            uiState.lastSensorEvent?.let { Text("Last Sensor: $it") }
            Text("Camera: $fpsLabel fps · ${uiState.cameraFpsModeLabel}$targetSuffix")
            Text("Raw score: $rawScoreLabel")
            Text("Baseline: $baselineLabel")
            Text("Effective: $effectiveScoreLabel")
            MetricDisplay(label = "Frame Sensor Nanos", value = "${uiState.frameSensorNanos ?: "-"}")
            Text("Frames: ${uiState.processedFrameCount}/${uiState.streamFrameCount}")
            Text("Analyze every N frames: ${uiState.processEveryNFrames}")
        }
    }
}

@Composable
internal fun ConnectedCard(connectedEndpoints: Set<String>) {
    SprintSyncCard {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionHeader("Connected Devices")
            connectedEndpoints.forEach { endpointId ->
                Text(endpointId)
            }
        }
    }
}


@Composable
internal fun EventsCard(recentEvents: List<String>) {
    SprintSyncCard {
        Column {
            SectionHeader("Recent Events")
            Spacer(Modifier.height(8.dp))
            recentEvents.forEach { event ->
                Text(event, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
