package com.paul.sprintsync.feature.race.domain

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paul.sprintsync.core.models.LastRunResult
import com.paul.sprintsync.core.repositories.LocalRepository
import com.paul.sprintsync.core.services.NearbyEvent
import com.paul.sprintsync.core.services.SessionConnectionsManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

typealias RaceSessionLoadLastRun = suspend () -> LastRunResult?
typealias RaceSessionSaveLastRun = suspend (LastRunResult) -> Unit
typealias RaceSessionSendMessage = (endpointId: String, messageJson: String, onComplete: (Result<Unit>) -> Unit) -> Unit

data class SessionRaceTimeline(
    val hostStartSensorNanos: Long? = null,
    val hostStopSensorNanos: Long? = null,
    val hostSplitSensorNanos: List<Long> = emptyList(),
)

data class RaceSessionUiState(
    val stage: SessionStage = SessionStage.SETUP,
    val operatingMode: SessionOperatingMode = SessionOperatingMode.SINGLE_DEVICE,
    val networkRole: SessionNetworkRole = SessionNetworkRole.NONE,
    val deviceRole: SessionDeviceRole = SessionDeviceRole.UNASSIGNED,
    val monitoringActive: Boolean = false,
    val runId: String? = null,
    val timeline: SessionRaceTimeline = SessionRaceTimeline(),
    val latestCompletedTimeline: SessionRaceTimeline? = null,
    val devices: List<SessionDevice> = emptyList(),
    val lastError: String? = null,
    val lastEvent: String? = null,
)

class RaceSessionController(
    private val loadLastRun: RaceSessionLoadLastRun,
    private val saveLastRun: RaceSessionSaveLastRun,
    private val sendMessage: RaceSessionSendMessage,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    companion object {
        private const val DEFAULT_LOCAL_DEVICE_ID = "local-device"
        private const val DEFAULT_LOCAL_DEVICE_NAME = "This Device"
    }

    constructor(
        localRepository: LocalRepository,
        connectionsManager: SessionConnectionsManager,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) : this(
        loadLastRun = { localRepository.loadLastRun() },
        saveLastRun = { run -> localRepository.saveLastRun(run) },
        sendMessage = { endpointId, messageJson, onComplete ->
            connectionsManager.sendMessage(endpointId, messageJson, onComplete)
        },
        ioDispatcher = ioDispatcher,
    )

    private val _uiState = MutableStateFlow(
        RaceSessionUiState(
            devices = listOf(
                SessionDevice(
                    id = DEFAULT_LOCAL_DEVICE_ID,
                    name = DEFAULT_LOCAL_DEVICE_NAME,
                    role = SessionDeviceRole.UNASSIGNED,
                    isLocal = true,
                ),
            ),
        ),
    )
    val uiState: StateFlow<RaceSessionUiState> = _uiState.asStateFlow()

    private var localDeviceId = DEFAULT_LOCAL_DEVICE_ID

    init {
        viewModelScope.launch(ioDispatcher) {
            val persisted = loadLastRun() ?: return@launch
            val persistedTimeline = SessionRaceTimeline(
                hostStartSensorNanos = persisted.startedSensorNanos,
                hostStopSensorNanos = persisted.stoppedSensorNanos,
            )
            _uiState.value = _uiState.value.copy(latestCompletedTimeline = persistedTimeline)
        }
    }

    fun setLocalDeviceIdentity(deviceId: String, deviceName: String) {
        if (deviceId.isBlank() || deviceName.isBlank()) {
            return
        }
        localDeviceId = deviceId
        val currentLocal = localDeviceFromState()
        val local = currentLocal.copy(
            id = deviceId,
            name = deviceName,
            isLocal = true,
        )
        _uiState.value = _uiState.value.copy(
            devices = ensureLocalDevice(local, _uiState.value.devices),
            deviceRole = local.role,
        )
    }

    fun startSingleDeviceMonitoring() {
        val local = localDeviceFromState().copy(
            role = SessionDeviceRole.UNASSIGNED,
            cameraFacing = SessionCameraFacing.FRONT,
            isLocal = true,
        )
        _uiState.value = _uiState.value.copy(
            operatingMode = SessionOperatingMode.SINGLE_DEVICE,
            networkRole = SessionNetworkRole.NONE,
            stage = SessionStage.MONITORING,
            monitoringActive = true,
            runId = UUID.randomUUID().toString(),
            timeline = SessionRaceTimeline(),
            latestCompletedTimeline = null,
            devices = ensureLocalDevice(local, _uiState.value.devices.filter { it.isLocal }),
            deviceRole = local.role,
            lastError = null,
            lastEvent = "single_device_started",
        )
    }

    fun startControllerMode() {
        val local = localDeviceFromState().copy(
            role = SessionDeviceRole.CONTROLLER,
            isLocal = true,
        )
        _uiState.value = _uiState.value.copy(
            operatingMode = SessionOperatingMode.SINGLE_DEVICE,
            networkRole = SessionNetworkRole.CLIENT,
            stage = SessionStage.MONITORING,
            monitoringActive = false,
            runId = null,
            timeline = SessionRaceTimeline(),
            latestCompletedTimeline = null,
            devices = ensureLocalDevice(local, _uiState.value.devices),
            deviceRole = local.role,
            lastError = null,
            lastEvent = "controller_started",
        )
    }

    fun stopSingleDeviceMonitoring() {
        val local = localDeviceFromState().copy(
            role = SessionDeviceRole.UNASSIGNED,
            isLocal = true,
        )
        _uiState.value = _uiState.value.copy(
            operatingMode = SessionOperatingMode.SINGLE_DEVICE,
            networkRole = SessionNetworkRole.NONE,
            stage = SessionStage.SETUP,
            monitoringActive = false,
            runId = null,
            timeline = SessionRaceTimeline(),
            latestCompletedTimeline = null,
            devices = ensureLocalDevice(local, _uiState.value.devices.filter { it.isLocal }),
            deviceRole = local.role,
            lastError = null,
            lastEvent = "single_device_stopped",
        )
    }

    fun startDisplayHostMode() {
        val local = localDeviceFromState().copy(
            role = SessionDeviceRole.DISPLAY,
            isLocal = true,
        )
        _uiState.value = _uiState.value.copy(
            operatingMode = SessionOperatingMode.DISPLAY_HOST,
            networkRole = SessionNetworkRole.HOST,
            stage = SessionStage.MONITORING,
            monitoringActive = false,
            runId = null,
            timeline = SessionRaceTimeline(),
            latestCompletedTimeline = null,
            devices = ensureLocalDevice(local, _uiState.value.devices.filter { it.isLocal }),
            deviceRole = local.role,
            lastError = null,
            lastEvent = "display_host_started",
        )
    }

    fun stopDisplayHostMode() {
        val local = localDeviceFromState().copy(
            role = SessionDeviceRole.UNASSIGNED,
            isLocal = true,
        )
        _uiState.value = _uiState.value.copy(
            stage = SessionStage.SETUP,
            monitoringActive = false,
            runId = null,
            timeline = SessionRaceTimeline(),
            latestCompletedTimeline = null,
            devices = ensureLocalDevice(local, _uiState.value.devices.filter { it.isLocal }),
            deviceRole = local.role,
            lastError = null,
            lastEvent = "display_host_stopped",
        )
    }

    fun onNearbyEvent(event: NearbyEvent) {
        when (event) {
            is NearbyEvent.EndpointFound -> {
                _uiState.value = _uiState.value.copy(lastEvent = "endpoint_found")
            }
            is NearbyEvent.EndpointLost -> {
                val nextDevices = _uiState.value.devices.filter { it.isLocal || it.id != event.endpointId }
                _uiState.value = _uiState.value.copy(
                    devices = ensureLocalDevice(localDeviceFromState(), nextDevices),
                    lastEvent = "endpoint_lost",
                )
            }
            is NearbyEvent.ConnectionResult -> {
                if (event.connected) {
                    val endpointName = event.endpointName?.trim().orEmpty().ifEmpty { event.endpointId }
                    val remote = SessionDevice(
                        id = event.endpointId,
                        name = endpointName,
                        role = SessionDeviceRole.UNASSIGNED,
                        isLocal = false,
                    )
                    val withoutEndpoint = _uiState.value.devices.filterNot { !it.isLocal && it.id == event.endpointId }
                    _uiState.value = _uiState.value.copy(
                        devices = ensureLocalDevice(localDeviceFromState(), withoutEndpoint + remote),
                        lastError = null,
                        lastEvent = "connection_result",
                    )
                } else {
                    val nextDevices = _uiState.value.devices.filter { it.isLocal || it.id != event.endpointId }
                    _uiState.value = _uiState.value.copy(
                        devices = ensureLocalDevice(localDeviceFromState(), nextDevices),
                        lastError = event.statusMessage ?: "Connection failed",
                        lastEvent = "connection_result",
                    )
                }
            }
            is NearbyEvent.EndpointDisconnected -> {
                val nextDevices = _uiState.value.devices.filter { it.isLocal || it.id != event.endpointId }
                _uiState.value = _uiState.value.copy(
                    devices = ensureLocalDevice(localDeviceFromState(), nextDevices),
                    lastEvent = "endpoint_disconnected",
                )
            }
            is NearbyEvent.PayloadReceived -> {
                _uiState.value = _uiState.value.copy(lastEvent = "payload_received")
            }
            is NearbyEvent.Error -> {
                _uiState.value = _uiState.value.copy(lastError = event.message, lastEvent = "error")
            }
        }
    }

    fun assignCameraFacing(deviceId: String, facing: SessionCameraFacing) {
        val nextDevices = _uiState.value.devices.map { existing ->
            if (existing.id == deviceId) {
                existing.copy(cameraFacing = facing)
            } else {
                existing
            }
        }
        _uiState.value = _uiState.value.copy(
            devices = nextDevices,
            deviceRole = localDeviceRole(),
        )
    }

    fun resetRun() {
        val nextRunId = if (_uiState.value.monitoringActive) UUID.randomUUID().toString() else null
        _uiState.value = _uiState.value.copy(
            timeline = SessionRaceTimeline(),
            latestCompletedTimeline = null,
            runId = nextRunId,
            lastEvent = "run_reset",
        )
    }

    fun onLocalMotionTrigger(triggerType: String, splitIndex: Int, triggerElapsedRealtimeNanos: Long) {
        if (!_uiState.value.monitoringActive) {
            return
        }
        handleSingleDeviceTrigger(triggerElapsedRealtimeNanos)
    }

    fun totalDeviceCount(): Int {
        return _uiState.value.devices.size
    }

    fun canStartMonitoring(): Boolean {
        return _uiState.value.operatingMode == SessionOperatingMode.SINGLE_DEVICE &&
            _uiState.value.networkRole != SessionNetworkRole.CLIENT &&
            !_uiState.value.monitoringActive
    }

    fun localDeviceRole(): SessionDeviceRole {
        return localDeviceFromState().role
    }

    fun localCameraFacing(): SessionCameraFacing {
        return localDeviceFromState().cameraFacing
    }

    fun localDeviceName(): String {
        return localDeviceFromState().name
    }

    private fun handleSingleDeviceTrigger(triggerElapsedRealtimeNanos: Long) {
        val current = _uiState.value.timeline
        if (current.hostStartSensorNanos == null) {
            _uiState.value = _uiState.value.copy(
                timeline = current.copy(hostStartSensorNanos = triggerElapsedRealtimeNanos),
                runId = UUID.randomUUID().toString(),
                lastEvent = "single_device_start",
            )
            return
        }
        if (current.hostStopSensorNanos != null) {
            return
        }
        if (triggerElapsedRealtimeNanos <= current.hostStartSensorNanos) {
            return
        }
        val completed = current.copy(hostStopSensorNanos = triggerElapsedRealtimeNanos)
        maybePersistCompletedRun(completed)
        _uiState.value = _uiState.value.copy(
            timeline = SessionRaceTimeline(),
            latestCompletedTimeline = completed,
            runId = UUID.randomUUID().toString(),
            lastEvent = "single_device_stop",
        )
    }

    private fun maybePersistCompletedRun(timeline: SessionRaceTimeline) {
        val started = timeline.hostStartSensorNanos ?: return
        val stopped = timeline.hostStopSensorNanos ?: return
        if (stopped <= started) {
            return
        }
        val run = LastRunResult(
            startedSensorNanos = started,
            stoppedSensorNanos = stopped,
        )
        viewModelScope.launch(ioDispatcher) {
            saveLastRun(run)
        }
    }

    private fun ensureLocalDevice(local: SessionDevice, current: List<SessionDevice>): List<SessionDevice> {
        val withoutLocal = current.filterNot { it.id == local.id || it.isLocal }
        return withoutLocal + local.copy(isLocal = true)
    }

    private fun localDeviceFromState(): SessionDevice {
        return _uiState.value.devices.firstOrNull { it.id == localDeviceId || it.isLocal }
            ?: SessionDevice(
                id = localDeviceId,
                name = DEFAULT_LOCAL_DEVICE_NAME,
                role = SessionDeviceRole.UNASSIGNED,
                isLocal = true,
            )
    }

    private fun pruneOrphanedNonLocalDevices(
        devices: List<SessionDevice>,
        connectedEndpoints: Set<String>,
    ): List<SessionDevice> {
        return devices.filter { device ->
            device.isLocal || connectedEndpoints.contains(device.id)
        }
    }
}
