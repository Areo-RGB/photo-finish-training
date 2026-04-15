package com.paul.sprintsync

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.paul.sprintsync.core.repositories.LocalRepository
import com.paul.sprintsync.core.services.NearbyEvent
import com.paul.sprintsync.core.services.NearbyTransportStrategy
import com.paul.sprintsync.core.services.SessionConnectionsManager
import com.paul.sprintsync.core.services.TcpConnectionsManager
import com.paul.sprintsync.core.services.GatewayResolver
import com.paul.sprintsync.core.services.NsdServiceDiscovery
import com.paul.sprintsync.core.DeviceDetector
import com.paul.sprintsync.core.RuntimeDeviceConfig
import com.paul.sprintsync.core.RuntimeNetworkRole
import com.paul.sprintsync.feature.motion.domain.MotionCameraFacing
import com.paul.sprintsync.feature.motion.domain.MotionDetectionController
import com.paul.sprintsync.feature.race.domain.RaceSessionController
import com.paul.sprintsync.feature.race.domain.SessionCameraFacing
import com.paul.sprintsync.feature.race.domain.SessionControlAction
import com.paul.sprintsync.feature.race.domain.SessionControlCommandMessage
import com.paul.sprintsync.feature.race.domain.SessionControllerIdentityMessage
import com.paul.sprintsync.feature.race.domain.SessionControllerTarget
import com.paul.sprintsync.feature.race.domain.SessionControllerTargetsMessage
import com.paul.sprintsync.feature.race.domain.SessionDeviceRole
import com.paul.sprintsync.feature.race.domain.SessionLapStartedMessage
import com.paul.sprintsync.feature.race.domain.SessionLapResultMessage
import com.paul.sprintsync.feature.race.domain.SessionNetworkRole
import com.paul.sprintsync.feature.race.domain.SessionOperatingMode
import com.paul.sprintsync.feature.race.domain.SessionStage
import com.paul.sprintsync.feature.motion.data.nativebridge.SensorNativeController
import com.paul.sprintsync.feature.motion.data.nativebridge.SensorNativeEvent
import com.paul.sprintsync.feature.motion.data.nativebridge.SensorNativePreviewViewFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity(), ActivityCompat.OnRequestPermissionsResultCallback {
    companion object {
        private const val DEFAULT_SERVICE_ID = "training.variant.nearby"
        private const val PERMISSIONS_REQUEST_CODE = 7301
        private const val CONTROLLER_SUMMARY_SYNC_INTERVAL_MS = 100L
        private const val TIMER_REFRESH_INTERVAL_MS = 100L
        private const val DISPLAY_TIMER_REFRESH_INTERVAL_MS = 33L
        private const val TAG = "SprintSyncRuntime"
        private const val MAX_PENDING_LAPS = 100
        private const val MANUAL_AUTO_READY_DELAY_SECONDS = 0
        private const val DEFAULT_GAME_MODE_LIMIT_MILLIS = 5_000L
        private const val DEFAULT_GAME_MODE_LIVES = 10
        private const val DEFAULT_GAME_MODE_AUTO_EVERY_RUNS = 10
        private const val GAME_MODE_AUTO_LIMIT_REDUCTION_MILLIS = 100L
        private const val MIN_GAME_MODE_LIMIT_MILLIS = 100L
        private const val TARGET_MONITORING_WIFI_SSID = "TP-Link_86CA_5G"
    }

    private lateinit var sensorNativeController: SensorNativeController
    private lateinit var connectionsManager: SessionConnectionsManager
    private lateinit var motionDetectionController: MotionDetectionController
    private lateinit var raceSessionController: RaceSessionController
    private lateinit var previewViewFactory: SensorNativePreviewViewFactory
    private val uiState = mutableStateOf(SprintSyncUiState())
    private var pendingPermissionAction: (() -> Unit)? = null
    private var timerRefreshJob: Job? = null
    private var controllerSummarySyncJob: Job? = null
    @Volatile
    private var controllerSummarySyncPending: Boolean = false
    private var isAppResumed: Boolean = false
    private var localCaptureStartPending: Boolean = false
    private var userMonitoringEnabled: Boolean = true
    private var displayDiscoveryActive: Boolean = false
    private var displayConnectedHostEndpointId: String? = null
    private var displayConnectedHostName: String? = null
    private val displayDiscoveredHosts = linkedMapOf<String, String>()
    private val controllerTargetDeviceNamesByEndpointId = linkedMapOf<String, String>()
    private val displayControllerEndpointIds = linkedSetOf<String>()
    private val displayHostDeviceNamesByEndpointId = linkedMapOf<String, String>()
    private val displayLatestLapByEndpointId = linkedMapOf<String, Long>()
    private val displayWaitingEndpointIds = linkedSetOf<String>()
    private val displayWaitingStartElapsedRealtimeNanosByEndpointId = linkedMapOf<String, Long>()
    private val displayWaitTextEnabledByEndpointId = linkedMapOf<String, Boolean>()
    private val displayLimitMillisByEndpointId = linkedMapOf<String, Long>()
    private val displayGameModeEnabledByEndpointId = linkedMapOf<String, Boolean>()
    private val displayGameModeLimitMillisByEndpointId = linkedMapOf<String, Long>()
    private val displayGameModeConfiguredLivesByEndpointId = linkedMapOf<String, Int>()
    private val displayGameModeCurrentLivesByEndpointId = linkedMapOf<String, Int>()
    private val displayGameModeAutoEnabledByEndpointId = linkedMapOf<String, Boolean>()
    private val displayGameModeAutoEveryRunsByEndpointId = linkedMapOf<String, Int>()
    private val displayGameModeAutoRunCountByEndpointId = linkedMapOf<String, Int>()
    private val displayAutoReadyDelaySecondsByEndpointId = linkedMapOf<String, Int>()
    private val displayAutoReadyResetJobsByEndpointId = linkedMapOf<String, Job>()
    private var lastRelayedStartSensorNanos: Long? = null
    private var lastRelayedStopSensorNanos: Long? = null
    private var displayReconnectionPending: Boolean = false
    private val pendingLapStartedMessages = ArrayDeque<SessionLapStartedMessage>()
    private val pendingLapResults = ArrayDeque<SessionLapResultMessage>()
    private var pendingPermissionScope: PermissionScope = PermissionScope.NETWORK_ONLY
    private var autoDisplayReconnectJob: Job? = null
    private var autoDisplayReconnectAttempts: Int = 0
    private val runtimeDeviceConfig: RuntimeDeviceConfig = DeviceDetector.detectCurrentDevice()
    private val setupActionProfile: SetupActionProfile = resolveSetupActionProfile(runtimeDeviceConfig)

    private enum class PermissionScope {
        NETWORK_ONLY,
        CAMERA_AND_NETWORK,
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        sensorNativeController = SensorNativeController(this)
        val localRepository = LocalRepository(this)
        val nsdDiscovery = NsdServiceDiscovery(this)
        connectionsManager = TcpConnectionsManager(
            hostPort = BuildConfig.TCP_HOST_PORT,
            nsdDiscovery = nsdDiscovery,
            resolveGatewayIp = { GatewayResolver.resolveGatewayIp(this) },
            fallbackHostIp = BuildConfig.TCP_HOST_IP,
        )
        motionDetectionController = MotionDetectionController(
            localRepository = localRepository,
            sensorNativeController = sensorNativeController,
        )
        previewViewFactory = SensorNativePreviewViewFactory(sensorNativeController)
        raceSessionController = RaceSessionController(
            loadLastRun = { localRepository.loadLastRun() },
            saveLastRun = { run -> localRepository.saveLastRun(run) },
            sendMessage = { endpointId, payload, onComplete ->
                connectionsManager.sendMessage(endpointId, payload, onComplete)
            },
        )
        raceSessionController.setLocalDeviceIdentity(localDeviceId(), localEndpointName())
        sensorNativeController.setEventListener(::onSensorEvent)
        connectionsManager.setEventListener(::onNearbyEvent)

        val denied = deniedPermissions(PermissionScope.NETWORK_ONLY)
        updateUiState {
            copy(
                permissionGranted = denied.isEmpty(),
                deniedPermissions = denied,
                networkSummary = "Ready",
            )
        }

        setContent {
            com.paul.sprintsync.core.theme.SprintSyncTheme {
                SprintSyncApp(
                    uiState = uiState.value,
                    debugViewEnabled = BuildConfig.ENABLE_DEBUG_VIEW,
                    previewViewFactory = previewViewFactory,
                    setupActionProfile = setupActionProfile,
                    runtimeDeviceConfig = runtimeDeviceConfig,
                    onAction = ::onMainAction,
                )
            }
        }
        maybeAutoStartRuntimeMode()
    }

    private fun onMainAction(action: MainAction) {
        when (action) {
            MainAction.RequestPermissions -> {
                if (uiState.value.setupBusy) return
                setSetupBusy(true)
                requestPermissionsIfNeeded(PermissionScope.NETWORK_ONLY) {
                    setSetupBusy(false)
                }
            }
            MainAction.StartSingleDevice -> {
                if (setupActionProfile == SetupActionProfile.CONTROLLER_ONLY) {
                    startControllerMode(enableAutoDisplayReconnect = true)
                } else {
                    startSingleDeviceMode(enableAutoDisplayReconnect = true)
                }
            }
            MainAction.StartDisplayHost -> startDisplayHostMode()
            MainAction.StartDisplayDiscovery -> startDisplayDiscovery(errorPrefix = "display discovery")
            is MainAction.ConnectDisplayHost -> {
                try {
                    connectionsManager.requestConnection(
                        endpointId = action.endpointId,
                        endpointName = localEndpointName(),
                    ) { result ->
                        result.exceptionOrNull()?.let { error ->
                            appendEvent("display connect error: ${error.localizedMessage ?: "unknown"}")
                        }
                        syncControllerSummaries()
                    }
                } catch (error: Throwable) {
                    appendEvent("display connect error: ${error.localizedMessage ?: "unknown"}")
                    syncControllerSummaries()
                }
            }
            is MainAction.ResetDeviceTimer -> {
                sendControllerCommandToDisplayHost(
                    action = SessionControlAction.RESET_TIMER,
                    targetEndpointId = action.endpointId,
                    limitMillis = null,
                    sensitivityPercent = null,
                    autoReadyDelaySeconds = null,
                    waitTextEnabled = null,
                    gameModeEnabled = null,
                    gameModeLimitMillis = null,
                    gameModeLives = null,
                )
            }
            is MainAction.SetDisplayLimit -> {
                sendControllerCommandToDisplayHost(
                    action = SessionControlAction.SET_DISPLAY_LIMIT,
                    targetEndpointId = action.endpointId,
                    limitMillis = action.limitMillis,
                    sensitivityPercent = null,
                    autoReadyDelaySeconds = null,
                    waitTextEnabled = null,
                    gameModeEnabled = null,
                    gameModeLimitMillis = null,
                    gameModeLives = null,
                )
            }
            is MainAction.SetAutoReadyDelay -> {
                sendControllerCommandToDisplayHost(
                    action = SessionControlAction.SET_AUTO_READY_DELAY,
                    targetEndpointId = action.endpointId,
                    limitMillis = null,
                    sensitivityPercent = null,
                    autoReadyDelaySeconds = action.autoReadyDelaySeconds,
                    waitTextEnabled = null,
                    gameModeEnabled = null,
                    gameModeLimitMillis = null,
                    gameModeLives = null,
                )
            }
            is MainAction.SetWaitTextEnabled -> {
                sendControllerCommandToDisplayHost(
                    action = SessionControlAction.SET_WAIT_TEXT_MODE,
                    targetEndpointId = action.endpointId,
                    limitMillis = null,
                    sensitivityPercent = null,
                    autoReadyDelaySeconds = null,
                    waitTextEnabled = action.enabled,
                    gameModeEnabled = null,
                    gameModeLimitMillis = null,
                    gameModeLives = null,
                )
            }
            is MainAction.SetDeviceSensitivity -> {
                sendControllerCommandToDisplayHost(
                    action = SessionControlAction.SET_MOTION_SENSITIVITY,
                    targetEndpointId = action.endpointId,
                    limitMillis = null,
                    sensitivityPercent = action.sensitivityPercent,
                    autoReadyDelaySeconds = null,
                    waitTextEnabled = null,
                    gameModeEnabled = null,
                    gameModeLimitMillis = null,
                    gameModeLives = null,
                )
            }
            is MainAction.SetGameModeEnabled -> {
                sendControllerCommandToDisplayHost(
                    action = SessionControlAction.SET_GAME_MODE_ENABLED,
                    targetEndpointId = action.endpointId,
                    limitMillis = null,
                    sensitivityPercent = null,
                    autoReadyDelaySeconds = null,
                    waitTextEnabled = null,
                    gameModeEnabled = action.enabled,
                    gameModeLimitMillis = null,
                    gameModeLives = null,
                )
            }
            is MainAction.SetGameModeLimit -> {
                sendControllerCommandToDisplayHost(
                    action = SessionControlAction.SET_GAME_MODE_LIMIT,
                    targetEndpointId = action.endpointId,
                    limitMillis = null,
                    sensitivityPercent = null,
                    autoReadyDelaySeconds = null,
                    waitTextEnabled = null,
                    gameModeEnabled = null,
                    gameModeLimitMillis = action.limitMillis,
                    gameModeLives = null,
                )
            }
            is MainAction.SetGameModeLives -> {
                sendControllerCommandToDisplayHost(
                    action = SessionControlAction.SET_GAME_MODE_LIVES,
                    targetEndpointId = action.endpointId,
                    limitMillis = null,
                    sensitivityPercent = null,
                    autoReadyDelaySeconds = null,
                    waitTextEnabled = null,
                    gameModeEnabled = null,
                    gameModeLimitMillis = null,
                    gameModeLives = action.lives,
                )
            }
            is MainAction.SetGameModeAutoConfig -> {
                val everyRuns = action.everyRuns.coerceIn(1, 20)
                sendControllerCommandToDisplayHost(
                    action = SessionControlAction.SET_GAME_MODE_AUTO_CONFIG,
                    targetEndpointId = action.endpointId,
                    limitMillis = null,
                    sensitivityPercent = null,
                    autoReadyDelaySeconds = null,
                    waitTextEnabled = null,
                    gameModeEnabled = null,
                    gameModeLimitMillis = null,
                    gameModeLives = null,
                    gameModeAutoEnabled = action.enabled,
                    gameModeAutoEveryRuns = everyRuns,
                )
            }
            is MainAction.SetMonitoringEnabled -> {
                userMonitoringEnabled = action.enabled
                if (!action.enabled) {
                    localCaptureStartPending = false
                    motionDetectionController.stopMonitoring()
                }
                syncControllerSummaries()
            }
            MainAction.StopMonitoring -> {
                logRuntimeDiagnostic("stopMonitoring requested")
                when (uiState.value.operatingMode) {
                    SessionOperatingMode.SINGLE_DEVICE -> {
                        raceSessionController.stopSingleDeviceMonitoring()
                        stopAutoDisplayReconnectLoop()
                        connectionsManager.stopAll()
                        clearDisplayRelayReconnectionState()
                        displayDiscoveryActive = false
                        displayConnectedHostEndpointId = null
                        displayConnectedHostName = null
                        displayDiscoveredHosts.clear()
                        controllerTargetDeviceNamesByEndpointId.clear()
                    }
                    SessionOperatingMode.DISPLAY_HOST -> {
                        raceSessionController.stopDisplayHostMode()
                        stopAutoDisplayReconnectLoop()
                        connectionsManager.stopAll()
                        clearDisplayRelayReconnectionState()
                        clearDisplayHostLapState()
                        controllerTargetDeviceNamesByEndpointId.clear()
                    }
                }
                syncControllerSummaries()
            }
            MainAction.ResetRun -> {
                raceSessionController.resetRun()
                if (uiState.value.operatingMode == SessionOperatingMode.DISPLAY_HOST) {
                    displayGameModeAutoRunCountByEndpointId.keys.toList().forEach { endpointId ->
                        displayGameModeAutoRunCountByEndpointId[endpointId] = 0
                    }
                }
                syncControllerSummaries()
            }
            is MainAction.AssignCameraFacing -> {
                raceSessionController.assignCameraFacing(action.deviceId, action.facing)
                if (
                    shouldApplyLiveLocalCameraFacingUpdate(
                        isLocalMotionMonitoring = motionDetectionController.uiState.value.monitoring,
                        assignedDeviceId = action.deviceId,
                        localDeviceId = localDeviceId(),
                    )
                ) {
                    applyLocalMonitoringConfigFromSession()
                }
                syncControllerSummaries()
            }
            is MainAction.UpdateThreshold -> {
                motionDetectionController.updateThreshold(action.value)
                syncControllerSummaries()
            }
            is MainAction.UpdateRoiCenter -> {
                motionDetectionController.updateRoiCenter(action.value)
                syncControllerSummaries()
            }
            is MainAction.UpdateRoiWidth -> {
                motionDetectionController.updateRoiWidth(action.value)
                syncControllerSummaries()
            }
            is MainAction.UpdateCooldown -> {
                motionDetectionController.updateCooldown(action.value)
                syncControllerSummaries()
            }
            MainAction.OpenWifiSettings -> {
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            }
        }
    }

    override fun onPause() {
        isAppResumed = false
        stopControllerSummarySyncLoop(flushPending = true)
        stopTimerRefreshLoop()
        logRuntimeDiagnostic("host paused")
        sensorNativeController.onHostPaused()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        isAppResumed = true
        logRuntimeDiagnostic("host resumed")
        sensorNativeController.onHostResumed()
        startControllerSummarySyncLoop()
        syncControllerSummaries()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) {
            return
        }
        applySystemUiForMode(raceSessionController.uiState.value.operatingMode)
    }

    override fun onDestroy() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        stopControllerSummarySyncLoop(flushPending = false)
        stopTimerRefreshLoop()
        stopAutoDisplayReconnectLoop()
        cancelAllDisplayAutoReadyResets()
        connectionsManager.stopAll()
        connectionsManager.setEventListener(null)
        sensorNativeController.setEventListener(null)
        sensorNativeController.dispose()
        super.onDestroy()
    }

    private fun requestPermissionsIfNeeded(scope: PermissionScope, onGranted: () -> Unit) {
        val denied = deniedPermissions(scope)
        if (denied.isEmpty()) {
            updateUiState { copy(permissionGranted = true, deniedPermissions = emptyList()) }
            onGranted()
            return
        }
        pendingPermissionScope = scope
        pendingPermissionAction = onGranted
        ActivityCompat.requestPermissions(this, denied.toTypedArray(), PERMISSIONS_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != PERMISSIONS_REQUEST_CODE) {
            return
        }
        val denied = deniedPermissions(pendingPermissionScope)
        val granted = denied.isEmpty()
        updateUiState {
            copy(
                permissionGranted = granted,
                deniedPermissions = denied,
            )
        }
        if (granted) {
            pendingPermissionAction?.invoke()
        } else {
            setSetupBusy(false)
            appendEvent("permissions denied: ${denied.joinToString()}")
        }
        pendingPermissionAction = null
        pendingPermissionScope = PermissionScope.NETWORK_ONLY
    }

    private fun setSetupBusy(busy: Boolean) {
        updateUiState { copy(setupBusy = busy) }
    }

    private fun maybeAutoStartRuntimeMode() {
        when (resolveRuntimeStartupAction(runtimeDeviceConfig)) {
            RuntimeStartupAction.START_DISPLAY_HOST -> {
                lifecycleScope.launch {
                    delay(250)
                    startDisplayHostMode()
                }
            }
            RuntimeStartupAction.START_CONTROLLER -> {
                lifecycleScope.launch {
                    delay(250)
                    startControllerMode(enableAutoDisplayReconnect = true)
                }
            }
            RuntimeStartupAction.START_SINGLE_DEVICE -> {
                lifecycleScope.launch {
                    delay(250)
                    startSingleDeviceMode(enableAutoDisplayReconnect = true)
                }
            }
        }
    }

    private fun startSingleDeviceMode(enableAutoDisplayReconnect: Boolean) {
        if (uiState.value.setupBusy) return
        setSetupBusy(true)
        requestPermissionsIfNeeded(PermissionScope.CAMERA_AND_NETWORK) {
            clearDisplayRelayReconnectionState()
            connectionsManager.stopAll()
            displayDiscoveryActive = false
            displayConnectedHostEndpointId = null
            displayConnectedHostName = null
            displayDiscoveredHosts.clear()
            controllerTargetDeviceNamesByEndpointId.clear()
            lastRelayedStartSensorNanos = null
            lastRelayedStopSensorNanos = null
            raceSessionController.startSingleDeviceMonitoring()
            userMonitoringEnabled = true
            if (enableAutoDisplayReconnect) {
                displayReconnectionPending = true
                startAutoDisplayReconnectLoop()
            } else {
                stopAutoDisplayReconnectLoop()
            }
            setSetupBusy(false)
            syncControllerSummaries()
        }
    }

    private fun startControllerMode(enableAutoDisplayReconnect: Boolean) {
        if (uiState.value.setupBusy) return
        setSetupBusy(true)
        requestPermissionsIfNeeded(PermissionScope.NETWORK_ONLY) {
            clearDisplayRelayReconnectionState()
            connectionsManager.stopAll()
            displayDiscoveryActive = false
            displayConnectedHostEndpointId = null
            displayConnectedHostName = null
            displayDiscoveredHosts.clear()
            controllerTargetDeviceNamesByEndpointId.clear()
            raceSessionController.startControllerMode()
            userMonitoringEnabled = false
            if (enableAutoDisplayReconnect) {
                displayReconnectionPending = true
                startAutoDisplayReconnectLoop()
            } else {
                stopAutoDisplayReconnectLoop()
            }
            setSetupBusy(false)
            syncControllerSummaries()
        }
    }

    private fun startDisplayHostMode() {
        if (uiState.value.setupBusy) return
        setSetupBusy(true)
        requestPermissionsIfNeeded(PermissionScope.NETWORK_ONLY) {
            stopAutoDisplayReconnectLoop()
            clearDisplayRelayReconnectionState()
            raceSessionController.startDisplayHostMode()
            clearDisplayHostLapState()
            displayDiscoveryActive = false
            displayConnectedHostEndpointId = null
            displayConnectedHostName = null
            displayDiscoveredHosts.clear()
            controllerTargetDeviceNamesByEndpointId.clear()
            try {
                connectionsManager.startHosting(
                    serviceId = DEFAULT_SERVICE_ID,
                    endpointName = localEndpointName(),
                    strategy = NearbyTransportStrategy.POINT_TO_STAR,
                ) { result ->
                    result.exceptionOrNull()?.let { error ->
                        appendEvent("display host error: ${error.localizedMessage ?: "unknown"}")
                    }
                    setSetupBusy(false)
                    syncControllerSummaries()
                }
            } catch (error: Throwable) {
                appendEvent("display host error: ${error.localizedMessage ?: "unknown"}")
                setSetupBusy(false)
                syncControllerSummaries()
            }
        }
    }

    private fun startDisplayDiscovery(errorPrefix: String) {
        requestPermissionsIfNeeded(PermissionScope.NETWORK_ONLY) {
            displayDiscoveryActive = true
            displayDiscoveredHosts.clear()
            try {
                connectionsManager.startDiscovery(
                    serviceId = DEFAULT_SERVICE_ID,
                    strategy = NearbyTransportStrategy.POINT_TO_STAR,
                ) { result ->
                    result.exceptionOrNull()?.let { error ->
                        appendEvent("$errorPrefix error: ${error.localizedMessage ?: "unknown"}")
                        displayDiscoveryActive = false
                    }
                    syncControllerSummaries()
                }
            } catch (error: Throwable) {
                displayDiscoveryActive = false
                appendEvent("$errorPrefix error: ${error.localizedMessage ?: "unknown"}")
                syncControllerSummaries()
            }
        }
    }

    private fun startAutoDisplayReconnectLoop() {
        if (autoDisplayReconnectJob?.isActive == true) return
        autoDisplayReconnectJob = lifecycleScope.launch {
            while (isActive) {
                if (raceSessionController.uiState.value.operatingMode != SessionOperatingMode.SINGLE_DEVICE) {
                    break
                }
                if (displayConnectedHostEndpointId == null) {
                    displayReconnectionPending = true
                    startDisplayDiscovery(errorPrefix = "reconnect discovery")
                    val nextDelay = reconnectDelayMillis(autoDisplayReconnectAttempts)
                    autoDisplayReconnectAttempts += 1
                    delay(nextDelay)
                } else {
                    autoDisplayReconnectAttempts = 0
                    delay(1000)
                }
            }
        }
    }

    private fun stopAutoDisplayReconnectLoop() {
        autoDisplayReconnectJob?.cancel()
        autoDisplayReconnectJob = null
        autoDisplayReconnectAttempts = 0
    }

    private fun onNearbyEvent(event: NearbyEvent) {
        when (uiState.value.operatingMode) {
            SessionOperatingMode.SINGLE_DEVICE -> {
                when (event) {
                    is NearbyEvent.EndpointFound -> {
                        displayDiscoveredHosts[event.endpointId] = event.endpointName
                        if (setupActionProfile == SetupActionProfile.CONTROLLER_ONLY) {
                            raceSessionController.onNearbyEvent(event)
                        }
                        // Auto-connect if not connected or if reconnection is pending
                        if (displayConnectedHostEndpointId == null || displayReconnectionPending) {
                            try {
                                connectionsManager.requestConnection(
                                    endpointId = event.endpointId,
                                    endpointName = localEndpointName(),
                                ) { result ->
                                    result.exceptionOrNull()?.let { error ->
                                        appendEvent(
                                            "auto-display-connect error: ${error.localizedMessage ?: "unknown"}",
                                        )
                                    }
                                }
                            } catch (error: Throwable) {
                                appendEvent("auto-display-connect error: ${error.localizedMessage ?: "unknown"}")
                            }
                        }
                    }
                    is NearbyEvent.EndpointLost -> {
                        displayDiscoveredHosts.remove(event.endpointId)
                        if (setupActionProfile == SetupActionProfile.CONTROLLER_ONLY) {
                            raceSessionController.onNearbyEvent(event)
                        }
                    }
                    is NearbyEvent.ConnectionResult -> {
                        if (event.connected) {
                            displayConnectedHostEndpointId = event.endpointId
                            displayConnectedHostName = event.endpointName ?: displayDiscoveredHosts[event.endpointId]
                            displayDiscoveryActive = false
                            autoDisplayReconnectAttempts = 0
                            // Clear reconnection flag and flush any pending laps
                            if (displayReconnectionPending) {
                                displayReconnectionPending = false
                                flushPendingLapResults()
                            }
                            if (setupActionProfile == SetupActionProfile.CONTROLLER_ONLY) {
                                val payload = SessionControllerIdentityMessage(
                                    senderDeviceName = localEndpointName(),
                                ).toJsonString()
                                connectionsManager.sendMessage(event.endpointId, payload) { result ->
                                    result.exceptionOrNull()?.let { error ->
                                        appendEvent("controller identity error: ${error.localizedMessage ?: "unknown"}")
                                    }
                                }
                            }
                            startAutoDisplayReconnectLoop()
                        } else if (displayConnectedHostEndpointId == event.endpointId) {
                            displayConnectedHostEndpointId = null
                            displayConnectedHostName = null
                            controllerTargetDeviceNamesByEndpointId.clear()
                            displayReconnectionPending = true
                            startAutoDisplayReconnectLoop()
                        }
                        if (setupActionProfile == SetupActionProfile.CONTROLLER_ONLY) {
                            raceSessionController.onNearbyEvent(event)
                        }
                    }
                    is NearbyEvent.EndpointDisconnected -> {
                        if (displayConnectedHostEndpointId == event.endpointId) {
                            displayConnectedHostEndpointId = null
                            displayConnectedHostName = null
                            controllerTargetDeviceNamesByEndpointId.clear()
                            displayReconnectionPending = true
                            startAutoDisplayReconnectLoop()
                        }
                        if (setupActionProfile == SetupActionProfile.CONTROLLER_ONLY) {
                            raceSessionController.onNearbyEvent(event)
                        }
                    }
                    is NearbyEvent.PayloadReceived -> {
                        SessionControlCommandMessage.tryParse(event.message)?.let { command ->
                            when (command.action) {
                                SessionControlAction.RESET_TIMER -> {
                                    raceSessionController.resetRun()
                                    appendEvent("remote reset from ${command.senderDeviceName}")
                                }
                                SessionControlAction.SET_MOTION_SENSITIVITY -> {
                                    val sensitivityPercent = command.sensitivityPercent
                                    if (sensitivityPercent != null) {
                                        val threshold = thresholdFromSensitivityPercent(sensitivityPercent)
                                        motionDetectionController.updateThreshold(threshold)
                                        appendEvent(
                                            "remote sensitivity ${sensitivityPercent}% from ${command.senderDeviceName}",
                                        )
                                        syncControllerSummaries()
                                    } else {
                                        appendEvent("remote sensitivity ignored: invalid payload")
                                    }
                                }
                                SessionControlAction.SET_DISPLAY_LIMIT -> Unit
                                SessionControlAction.SET_AUTO_READY_DELAY -> Unit
                                SessionControlAction.SET_WAIT_TEXT_MODE -> Unit
                                SessionControlAction.SET_GAME_MODE_ENABLED -> Unit
                                SessionControlAction.SET_GAME_MODE_LIMIT -> Unit
                                SessionControlAction.SET_GAME_MODE_LIVES -> Unit
                                SessionControlAction.SET_GAME_MODE_AUTO_CONFIG -> Unit
                            }
                        }
                        SessionControllerTargetsMessage.tryParse(event.message)?.let { snapshot ->
                            val hostEndpoint = displayConnectedHostEndpointId
                            if (hostEndpoint != null && event.endpointId == hostEndpoint) {
                                controllerTargetDeviceNamesByEndpointId.clear()
                                snapshot.targets.forEach { target ->
                                    controllerTargetDeviceNamesByEndpointId[target.endpointId] = target.deviceName
                                }
                            }
                        }
                        if (setupActionProfile == SetupActionProfile.CONTROLLER_ONLY) {
                            raceSessionController.onNearbyEvent(event)
                        }
                    }
                    is NearbyEvent.Error -> Unit
                }
            }
            SessionOperatingMode.DISPLAY_HOST -> {
                when (event) {
                    is NearbyEvent.EndpointFound -> {
                        if (event.endpointName.isNotBlank()) {
                            displayHostDeviceNamesByEndpointId[event.endpointId] = event.endpointName
                        }
                    }
                    is NearbyEvent.EndpointLost -> {
                        displayHostDeviceNamesByEndpointId.remove(event.endpointId)
                        displayControllerEndpointIds.remove(event.endpointId)
                        displayLatestLapByEndpointId.remove(event.endpointId)
                        displayWaitingEndpointIds.remove(event.endpointId)
                        displayWaitingStartElapsedRealtimeNanosByEndpointId.remove(event.endpointId)
                        displayWaitTextEnabledByEndpointId.remove(event.endpointId)
                        displayLimitMillisByEndpointId.remove(event.endpointId)
                        displayGameModeEnabledByEndpointId.remove(event.endpointId)
                        displayGameModeLimitMillisByEndpointId.remove(event.endpointId)
                        displayGameModeConfiguredLivesByEndpointId.remove(event.endpointId)
                        displayGameModeCurrentLivesByEndpointId.remove(event.endpointId)
                        displayGameModeAutoEnabledByEndpointId.remove(event.endpointId)
                        displayGameModeAutoEveryRunsByEndpointId.remove(event.endpointId)
                        displayGameModeAutoRunCountByEndpointId.remove(event.endpointId)
                        broadcastControllerTargetsSnapshotToConnectedEndpoints()
                    }
                    is NearbyEvent.ConnectionResult -> {
                        if (event.connected) {
                            val endpointName = event.endpointName?.trim().orEmpty()
                            if (endpointName.isNotEmpty()) {
                                displayHostDeviceNamesByEndpointId[event.endpointId] = endpointName
                            }
                        } else {
                            displayHostDeviceNamesByEndpointId.remove(event.endpointId)
                            displayControllerEndpointIds.remove(event.endpointId)
                            displayLatestLapByEndpointId.remove(event.endpointId)
                            displayWaitingEndpointIds.remove(event.endpointId)
                            displayWaitingStartElapsedRealtimeNanosByEndpointId.remove(event.endpointId)
                            displayWaitTextEnabledByEndpointId.remove(event.endpointId)
                            displayLimitMillisByEndpointId.remove(event.endpointId)
                            displayGameModeEnabledByEndpointId.remove(event.endpointId)
                            displayGameModeLimitMillisByEndpointId.remove(event.endpointId)
                            displayGameModeConfiguredLivesByEndpointId.remove(event.endpointId)
                            displayGameModeCurrentLivesByEndpointId.remove(event.endpointId)
                            displayGameModeAutoEnabledByEndpointId.remove(event.endpointId)
                            displayGameModeAutoEveryRunsByEndpointId.remove(event.endpointId)
                            displayGameModeAutoRunCountByEndpointId.remove(event.endpointId)
                        }
                        broadcastControllerTargetsSnapshotToConnectedEndpoints()
                    }
                    is NearbyEvent.EndpointDisconnected -> {
                        displayHostDeviceNamesByEndpointId.remove(event.endpointId)
                        displayControllerEndpointIds.remove(event.endpointId)
                        displayLatestLapByEndpointId.remove(event.endpointId)
                        displayWaitingEndpointIds.remove(event.endpointId)
                        displayWaitingStartElapsedRealtimeNanosByEndpointId.remove(event.endpointId)
                        displayWaitTextEnabledByEndpointId.remove(event.endpointId)
                        displayLimitMillisByEndpointId.remove(event.endpointId)
                        displayGameModeEnabledByEndpointId.remove(event.endpointId)
                        displayGameModeLimitMillisByEndpointId.remove(event.endpointId)
                        displayGameModeConfiguredLivesByEndpointId.remove(event.endpointId)
                        displayGameModeCurrentLivesByEndpointId.remove(event.endpointId)
                        displayGameModeAutoEnabledByEndpointId.remove(event.endpointId)
                        displayGameModeAutoEveryRunsByEndpointId.remove(event.endpointId)
                        displayGameModeAutoRunCountByEndpointId.remove(event.endpointId)
                        displayAutoReadyDelaySecondsByEndpointId.remove(event.endpointId)
                        cancelDisplayAutoReadyReset(event.endpointId)
                        broadcastControllerTargetsSnapshotToConnectedEndpoints()
                    }
                    is NearbyEvent.PayloadReceived -> {
                        var handledControllerIdentity = false
                        var handledControl = false
                        SessionControllerIdentityMessage.tryParse(event.message)?.let { identity ->
                            handledControllerIdentity = true
                            displayControllerEndpointIds.add(event.endpointId)
                            if (identity.senderDeviceName.isNotBlank()) {
                                displayHostDeviceNamesByEndpointId[event.endpointId] = identity.senderDeviceName
                            }
                            broadcastControllerTargetsSnapshotToConnectedEndpoints()
                        }
                        if (!handledControllerIdentity) {
                            SessionControlCommandMessage.tryParse(event.message)?.let { command ->
                            handledControl = true
                            when (command.action) {
                                SessionControlAction.RESET_TIMER -> {
                                    cancelDisplayAutoReadyReset(command.targetEndpointId)
                                    displayLatestLapByEndpointId.remove(command.targetEndpointId)
                                    displayWaitingEndpointIds.remove(command.targetEndpointId)
                                    displayWaitingStartElapsedRealtimeNanosByEndpointId.remove(command.targetEndpointId)
                                    displayGameModeAutoRunCountByEndpointId[command.targetEndpointId] = 0
                                    if (connectionsManager.connectedEndpoints().contains(command.targetEndpointId)) {
                                        connectionsManager.sendMessage(command.targetEndpointId, command.toJsonString()) { result ->
                                            result.exceptionOrNull()?.let { error ->
                                                appendEvent("reset route error: ${error.localizedMessage ?: "unknown"}")
                                            }
                                        }
                                    } else {
                                        appendEvent("reset route skipped: target not connected")
                                    }
                                }
                                SessionControlAction.SET_DISPLAY_LIMIT -> {
                                    val limitMillis = command.limitMillis
                                    if (limitMillis != null && limitMillis > 0L) {
                                        displayLimitMillisByEndpointId[command.targetEndpointId] = limitMillis
                                    } else {
                                        displayLimitMillisByEndpointId.remove(command.targetEndpointId)
                                    }
                                }
                                SessionControlAction.SET_AUTO_READY_DELAY -> {
                                    val configuredDelaySeconds = command.autoReadyDelaySeconds
                                    displayAutoReadyDelaySecondsByEndpointId[command.targetEndpointId] =
                                        configuredDelaySeconds ?: MANUAL_AUTO_READY_DELAY_SECONDS
                                    cancelDisplayAutoReadyReset(command.targetEndpointId)
                                }
                                SessionControlAction.SET_WAIT_TEXT_MODE -> {
                                    val waitTextEnabled = command.waitTextEnabled
                                    if (waitTextEnabled != null) {
                                        displayWaitTextEnabledByEndpointId[command.targetEndpointId] = waitTextEnabled
                                    }
                                }
                                SessionControlAction.SET_MOTION_SENSITIVITY -> {
                                    val sensitivityPercent = command.sensitivityPercent
                                    if (
                                        sensitivityPercent != null &&
                                        connectionsManager.connectedEndpoints().contains(command.targetEndpointId)
                                    ) {
                                        connectionsManager.sendMessage(command.targetEndpointId, command.toJsonString()) { result ->
                                            result.exceptionOrNull()?.let { error ->
                                                appendEvent("sensitivity route error: ${error.localizedMessage ?: "unknown"}")
                                            }
                                        }
                                    } else {
                                        appendEvent("sensitivity route skipped: target not connected")
                                    }
                                }
                                SessionControlAction.SET_GAME_MODE_ENABLED -> {
                                    val enabled = command.gameModeEnabled == true
                                    displayGameModeEnabledByEndpointId[command.targetEndpointId] = enabled
                                    if (enabled) {
                                        if (!displayGameModeLimitMillisByEndpointId.containsKey(command.targetEndpointId)) {
                                            displayGameModeLimitMillisByEndpointId[command.targetEndpointId] =
                                                DEFAULT_GAME_MODE_LIMIT_MILLIS
                                        }
                                        val configuredLives =
                                            (displayGameModeConfiguredLivesByEndpointId[command.targetEndpointId]
                                                ?: DEFAULT_GAME_MODE_LIVES).coerceIn(1, 10)
                                        displayGameModeConfiguredLivesByEndpointId[command.targetEndpointId] =
                                            configuredLives
                                        if (!displayGameModeCurrentLivesByEndpointId.containsKey(command.targetEndpointId)) {
                                            displayGameModeCurrentLivesByEndpointId[command.targetEndpointId] =
                                                configuredLives
                                        }
                                    } else {
                                        displayGameModeAutoRunCountByEndpointId[command.targetEndpointId] = 0
                                    }
                                }
                                SessionControlAction.SET_GAME_MODE_LIMIT -> {
                                    val gameModeLimitMillis = command.gameModeLimitMillis
                                    if (gameModeLimitMillis != null && gameModeLimitMillis > 0L) {
                                        displayGameModeLimitMillisByEndpointId[command.targetEndpointId] =
                                            gameModeLimitMillis
                                    }
                                }
                                SessionControlAction.SET_GAME_MODE_LIVES -> {
                                    val gameModeLives = command.gameModeLives?.coerceIn(1, 10)
                                    if (gameModeLives != null) {
                                        displayGameModeConfiguredLivesByEndpointId[command.targetEndpointId] =
                                            gameModeLives
                                        displayGameModeCurrentLivesByEndpointId[command.targetEndpointId] =
                                            gameModeLives
                                    }
                                }
                                SessionControlAction.SET_GAME_MODE_AUTO_CONFIG -> {
                                    val autoEnabled = command.gameModeAutoEnabled == true
                                    val everyRuns = (command.gameModeAutoEveryRuns ?: DEFAULT_GAME_MODE_AUTO_EVERY_RUNS)
                                        .coerceIn(1, 20)
                                    displayGameModeAutoEnabledByEndpointId[command.targetEndpointId] = autoEnabled
                                    displayGameModeAutoEveryRunsByEndpointId[command.targetEndpointId] = everyRuns
                                    displayGameModeAutoRunCountByEndpointId[command.targetEndpointId] = 0
                                }
                            }
                        }
                        }
                        if (!handledControllerIdentity && !handledControl) {
                            var handledLapStarted = false
                            SessionLapStartedMessage.tryParse(event.message)?.let { started ->
                                handledLapStarted = true
                                val senderDeviceName = started.senderDeviceName.trim()
                                if (senderDeviceName.isNotEmpty()) {
                                    displayHostDeviceNamesByEndpointId[event.endpointId] = senderDeviceName
                                }
                                displayLatestLapByEndpointId.remove(event.endpointId)
                                displayWaitingEndpointIds.add(event.endpointId)
                                displayWaitingStartElapsedRealtimeNanosByEndpointId[event.endpointId] =
                                    SystemClock.elapsedRealtimeNanos()
                                cancelDisplayAutoReadyReset(event.endpointId)
                            }
                            if (!handledLapStarted) {
                                SessionLapResultMessage.tryParse(event.message)?.let { result ->
                                    val senderDeviceName = result.senderDeviceName.trim()
                                    if (senderDeviceName.isNotEmpty()) {
                                        displayHostDeviceNamesByEndpointId[event.endpointId] = senderDeviceName
                                    }
                                    displayWaitingEndpointIds.remove(event.endpointId)
                                    displayWaitingStartElapsedRealtimeNanosByEndpointId.remove(event.endpointId)
                                    displayLatestLapByEndpointId[event.endpointId] = result.elapsedNanos
                                    applyGameModeAutoLimitProgressIfNeeded(endpointId = event.endpointId)
                                    applyGameModeLifePenaltyIfNeeded(
                                        endpointId = event.endpointId,
                                        elapsedNanos = result.elapsedNanos,
                                    )
                                    scheduleDisplayAutoReadyReset(event.endpointId)
                                }
                            }
                        }
                        broadcastControllerTargetsSnapshotToConnectedEndpoints()
                    }
                    is NearbyEvent.Error,
                    -> Unit
                }
            }
        }

        val type = when (event) {
            is NearbyEvent.EndpointFound -> "endpoint_found"
            is NearbyEvent.EndpointLost -> "endpoint_lost"
            is NearbyEvent.ConnectionResult -> "connection_result"
            is NearbyEvent.EndpointDisconnected -> "endpoint_disconnected"
            is NearbyEvent.PayloadReceived -> "payload_received"
            is NearbyEvent.Error -> "error"
        }
        val connectedCount = connectionsManager.connectedEndpoints().size
        val role = connectionsManager.currentRole().name.lowercase()
        updateUiState {
            copy(
                networkSummary = "$role mode, $connectedCount connected",
                lastNearbyEvent = type,
            )
        }
        syncControllerSummaries()
        appendEvent("transport:$type")
    }

    private fun onSensorEvent(event: SensorNativeEvent) {
        if (event is SensorNativeEvent.State || event is SensorNativeEvent.Error) {
            localCaptureStartPending = false
        }
        motionDetectionController.handleSensorEvent(event)
        if (event is SensorNativeEvent.Trigger) {
            raceSessionController.onLocalMotionTrigger(
                triggerType = event.trigger.triggerType,
                splitIndex = 0,
                triggerElapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos(),
            )
        }
        val type = when (event) {
            is SensorNativeEvent.FrameStats -> "native_frame_stats"
            is SensorNativeEvent.Trigger -> "native_trigger"
            is SensorNativeEvent.State -> "native_state"
            is SensorNativeEvent.Diagnostic -> "native_diagnostic"
            is SensorNativeEvent.Error -> "native_error"
        }
        updateUiState { copy(lastSensorEvent = type) }
        controllerSummarySyncPending = true
        appendEvent("sensor:$type")
    }

    private fun firstConnectedEndpointId(): String? {
        return connectionsManager.connectedEndpoints().firstOrNull()
    }

    private fun syncControllerSummaries() {
        val raceState = raceSessionController.uiState.value
        val motionBefore = motionDetectionController.uiState.value
        val mode = raceState.operatingMode
        val localRole = raceSessionController.localDeviceRole()
        val isPassiveDisplayClient = shouldUsePassiveDisplayClientMode(
            mode = mode,
            networkRole = raceState.networkRole,
            localRole = localRole,
        )
        applyRequestedOrientationForMode(mode)
        val shouldRunLocalCapture = shouldRunLocalMonitoring()

        when (
            resolveLocalCaptureAction(
                monitoringActive = raceState.monitoringActive &&
                    mode != SessionOperatingMode.DISPLAY_HOST &&
                    !isPassiveDisplayClient,
                isAppResumed = isAppResumed,
                shouldRunLocalCapture = shouldRunLocalCapture,
                isLocalMotionMonitoring = motionBefore.monitoring,
                localCaptureStartPending = localCaptureStartPending,
            )
        ) {
            LocalCaptureAction.START -> {
                localCaptureStartPending = true
                logRuntimeDiagnostic(
                    "local capture start: role=${raceSessionController.localDeviceRole().name} stage=${raceState.stage.name}",
                )
                applyLocalMonitoringConfigFromSession()
                motionDetectionController.startMonitoring()
            }

            LocalCaptureAction.STOP -> {
                localCaptureStartPending = false
                logRuntimeDiagnostic(
                    "local capture stop: role=${raceSessionController.localDeviceRole().name} stage=${raceState.stage.name}",
                )
                motionDetectionController.stopMonitoring()
            }

            LocalCaptureAction.NONE -> Unit
        }

        val hasDisplayLocalWaitingTimers =
            mode == SessionOperatingMode.DISPLAY_HOST &&
                displayWaitingEndpointIds.any { endpointId ->
                    displayLatestLapByEndpointId[endpointId] == null &&
                        displayWaitTextEnabledByEndpointId[endpointId] == false &&
                        displayWaitingStartElapsedRealtimeNanosByEndpointId[endpointId] != null
                }

        if (
            shouldKeepTimerRefreshActive(
                monitoringActive = raceState.monitoringActive &&
                    mode != SessionOperatingMode.DISPLAY_HOST &&
                    !isPassiveDisplayClient,
                isAppResumed = isAppResumed,
                hasStopSensor = raceState.timeline.hostStopSensorNanos != null,
            ) || hasDisplayLocalWaitingTimers
        ) {
            startTimerRefreshLoop()
        } else {
            stopTimerRefreshLoop()
        }

        val motionState = motionDetectionController.uiState.value

        val monitoringSummary = if (motionState.monitoring) {
            "Monitoring"
        } else {
            "Idle"
        }
        val isHost = raceState.networkRole == SessionNetworkRole.HOST || mode == SessionOperatingMode.DISPLAY_HOST
        val liveConnectedEndpoints = when (mode) {
            SessionOperatingMode.SINGLE_DEVICE -> setOfNotNull(displayConnectedHostEndpointId)
            SessionOperatingMode.DISPLAY_HOST -> connectionsManager.connectedEndpoints()
        }
        val hasPeers = liveConnectedEndpoints.isNotEmpty()
        val timelineForUi = if (
            mode == SessionOperatingMode.SINGLE_DEVICE &&
            raceState.timeline.hostStartSensorNanos == null &&
            raceState.latestCompletedTimeline != null
        ) {
            raceState.latestCompletedTimeline
        } else {
            raceState.timeline
        }
        if (mode == SessionOperatingMode.SINGLE_DEVICE) {
            val activeStartNanos = raceState.timeline.hostStartSensorNanos
            val completed = raceState.latestCompletedTimeline
            val completedStopNanos = completed?.hostStopSensorNanos
            val completedStartNanos = completed?.hostStartSensorNanos
            val hostEndpoint = displayConnectedHostEndpointId
            if (activeStartNanos != null && activeStartNanos != lastRelayedStartSensorNanos) {
                val startedMessage = SessionLapStartedMessage(
                    senderDeviceName = localEndpointName(),
                )

                if (hostEndpoint != null) {
                    connectionsManager.sendMessage(hostEndpoint, startedMessage.toJsonString()) { result ->
                        result.exceptionOrNull()?.let { error ->
                            appendEvent("lap start relay error: ${error.localizedMessage ?: "unknown"}")
                        }
                    }
                    lastRelayedStartSensorNanos = activeStartNanos
                } else if (displayReconnectionPending) {
                    if (pendingLapStartedMessages.size >= MAX_PENDING_LAPS) {
                        pendingLapStartedMessages.removeFirst()
                    }
                    pendingLapStartedMessages.addLast(startedMessage)
                    lastRelayedStartSensorNanos = activeStartNanos
                }
            }
            if (
                completedStartNanos != null &&
                completedStopNanos != null &&
                completedStopNanos != lastRelayedStopSensorNanos
            ) {
                val elapsedNanos = completedStopNanos - completedStartNanos
                val lapMessage = SessionLapResultMessage(
                    senderDeviceName = localEndpointName(),
                    elapsedNanos = elapsedNanos,
                )

                if (hostEndpoint != null) {
                    // Connected - send immediately
                    connectionsManager.sendMessage(hostEndpoint, lapMessage.toJsonString()) { result ->
                        result.exceptionOrNull()?.let { error ->
                            appendEvent("lap relay error: ${error.localizedMessage ?: "unknown"}")
                        }
                    }
                    lastRelayedStopSensorNanos = completedStopNanos
                } else if (displayReconnectionPending) {
                    // Disconnected but trying to reconnect - cache for later
                    if (pendingLapResults.size >= MAX_PENDING_LAPS) {
                        pendingLapResults.removeFirst() // Drop oldest to make room
                    }
                    pendingLapResults.addLast(lapMessage)
                    lastRelayedStopSensorNanos = completedStopNanos
                }
                // If disconnected and NOT trying to reconnect, don't cache (original behavior)
            }
        }

        val connectedWifiSsid = currentConnectedWifiSsid()
        val wifiWarningText = when {
            isConnectedToExpectedWifi(connectedWifiSsid, TARGET_MONITORING_WIFI_SSID) -> null
            connectedWifiSsid == null -> "Connect to Wi-Fi \"$TARGET_MONITORING_WIFI_SSID\"."
            else -> "Connected to \"$connectedWifiSsid\". Use \"$TARGET_MONITORING_WIFI_SSID\"."
        }

        val runStatusLabel = when {
            timelineForUi.hostStartSensorNanos == null -> "Ready"
            timelineForUi.hostStopSensorNanos != null -> "Finished"
            raceState.monitoringActive -> "Running"
            else -> "Armed"
        }
        val marksCount = timelineForUi.hostSplitSensorNanos.size + if (timelineForUi.hostStopSensorNanos != null) 1 else 0

        val elapsedDisplay = formatElapsedDisplay(
            startedSensorNanos = timelineForUi.hostStartSensorNanos,
            stoppedSensorNanos = timelineForUi.hostStopSensorNanos,
            monitoringActive = raceState.monitoringActive,
        )

        val cameraModeLabel = if (isPassiveDisplayClient) {
            "-"
        } else if (motionState.observedFps == null) {
            "INIT"
        } else {
            "NORMAL"
        }
        val triggerHistory = if (isPassiveDisplayClient) {
            emptyList()
        } else {
            motionState.triggerHistory.map { trigger ->
                val roleLabel = when (trigger.triggerType.lowercase()) {
                    "start" -> "START"
                    "stop" -> "STOP"
                    else -> trigger.triggerType.uppercase()
                }
                "$roleLabel at ${trigger.triggerSensorNanos}ns (score ${"%.4f".format(trigger.score)})"
            }
        }
        val splitHistory = buildSplitHistoryForTimeline(
            startedSensorNanos = timelineForUi.hostStartSensorNanos,
            splitSensorNanos = timelineForUi.hostSplitSensorNanos,
        )

        val clockSummary = if (hasPeers) "Local authority" else "Standalone"
        val displayEndpointIdsForRows = if (mode == SessionOperatingMode.DISPLAY_HOST) {
            connectionsManager.connectedEndpoints().filterNot { endpointId ->
                displayControllerEndpointIds.contains(endpointId) ||
                    isControllerEndpointName(displayHostDeviceNamesByEndpointId[endpointId])
            }.toSet()
        } else {
            connectionsManager.connectedEndpoints()
        }
        val displayLapRows = buildDisplayLapRowsForConnectedDevices(
            connectedEndpointIds = displayEndpointIdsForRows,
            deviceNamesByEndpointId = displayHostDeviceNamesByEndpointId,
            elapsedByEndpointId = displayLatestLapByEndpointId,
            waitingEndpointIds = displayWaitingEndpointIds,
            waitingStartElapsedRealtimeNanosByEndpointId = displayWaitingStartElapsedRealtimeNanosByEndpointId,
            waitTextEnabledByEndpointId = displayWaitTextEnabledByEndpointId,
            limitMillisByEndpointId = displayLimitMillisByEndpointId,
            gameModeEnabledByEndpointId = displayGameModeEnabledByEndpointId,
            gameModeLimitMillisByEndpointId = displayGameModeLimitMillisByEndpointId,
            gameModeConfiguredLivesByEndpointId = displayGameModeConfiguredLivesByEndpointId,
            gameModeCurrentLivesByEndpointId = displayGameModeCurrentLivesByEndpointId,
            hostStartSensorNanos = timelineForUi.hostStartSensorNanos,
            hostStopSensorNanos = timelineForUi.hostStopSensorNanos,
            monitoringActive = raceState.monitoringActive,
            nowSensorNanos = SystemClock.elapsedRealtimeNanos(),
            nowDisplayElapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos(),
        )
        updateUiState {
            copy(
                stage = raceState.stage,
                operatingMode = mode,
                networkRole = raceState.networkRole,
                sessionSummary = raceState.stage.name.lowercase(),
                monitoringSummary = monitoringSummary,
                userMonitoringEnabled = userMonitoringEnabled,
                clockSummary = clockSummary,
                startedSensorNanos = timelineForUi.hostStartSensorNanos,
                stoppedSensorNanos = timelineForUi.hostStopSensorNanos,
                devices = raceState.devices,
                canStartMonitoring = mode == SessionOperatingMode.SINGLE_DEVICE && raceSessionController.canStartMonitoring(),
                isHost = isHost,
                localRole = localRole,
                monitoringConnectionTypeLabel = resolveMonitoringConnectionTypeLabel(
                    hasPeers = hasPeers,
                    hostIp = displayConnectedHostEndpointId ?: BuildConfig.TCP_HOST_IP,
                    hostPort = BuildConfig.TCP_HOST_PORT,
                ),
                hasConnectedPeers = hasPeers,
                wifiWarningText = wifiWarningText,
                runStatusLabel = runStatusLabel,
                runMarksCount = marksCount,
                elapsedDisplay = elapsedDisplay,
                threshold = motionState.config.threshold,
                roiCenterX = motionState.config.roiCenterX,
                roiWidth = motionState.config.roiWidth,
                cooldownMs = motionState.config.cooldownMs,
                processEveryNFrames = motionState.config.processEveryNFrames,
                observedFps = motionState.observedFps,
                cameraFpsModeLabel = cameraModeLabel,
                targetFpsUpper = motionState.targetFpsUpper,
                rawScore = motionState.rawScore,
                baseline = motionState.baseline,
                effectiveScore = motionState.effectiveScore,
                frameSensorNanos = motionState.lastFrameSensorNanos,
                streamFrameCount = motionState.streamFrameCount,
                processedFrameCount = motionState.processedFrameCount,
                triggerHistory = triggerHistory,
                splitHistory = splitHistory,
                discoveredEndpoints = displayDiscoveredHosts.toMap(),
                connectedEndpoints = liveConnectedEndpoints,
                networkSummary = "${connectionsManager.currentRole().name.lowercase()} mode, ${liveConnectedEndpoints.size} connected",
                displayLapRows = displayLapRows,
                displayConnectedHostName = displayConnectedHostName,
                displayConnectedHostEndpointId = displayConnectedHostEndpointId,
                displayDiscoveryActive = displayDiscoveryActive,
                controllerTargetEndpoints = controllerTargetDeviceNamesByEndpointId.toMap(),
            )
        }
    }

    private fun appendEvent(message: String) {
        val previous = uiState.value.recentEvents
        val updated = (listOf(message) + previous).take(10)
        updateUiState { copy(recentEvents = updated) }
    }

    private fun deniedPermissions(scope: PermissionScope): List<String> {
        return requiredPermissions(scope).filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requiredPermissions(scope: PermissionScope): List<String> {
        val permissions = mutableListOf<String>()
        if (scope == PermissionScope.CAMERA_AND_NETWORK) {
            permissions += Manifest.permission.CAMERA
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.NEARBY_WIFI_DEVICES
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions += Manifest.permission.BLUETOOTH_ADVERTISE
            permissions += Manifest.permission.BLUETOOTH_CONNECT
            permissions += Manifest.permission.BLUETOOTH_SCAN
        }
        return permissions.distinct()
    }

    private fun localEndpointName(): String {
        val model = Build.MODEL?.trim().orEmpty()
        val baseName = when {
            model.isNotEmpty() -> model
            Build.DEVICE?.trim().orEmpty().isNotEmpty() -> Build.DEVICE.trim()
            else -> "Android Device"
        }
        return if (setupActionProfile == SetupActionProfile.CONTROLLER_ONLY) {
            if (baseName.contains("controller", ignoreCase = true)) {
                baseName
            } else {
                "$baseName (Controller)"
            }
        } else {
            baseName
        }
    }

    private fun localDeviceId(): String {
        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            ?.trim()
            .orEmpty()
        if (androidId.isNotEmpty()) {
            return "android-$androidId"
        }
        return "local-${Build.DEVICE.orEmpty()}"
    }

    private fun shouldRunLocalMonitoring(): Boolean {
        return shouldRunLocalMonitoring(
            mode = raceSessionController.uiState.value.operatingMode,
            userMonitoringEnabled = userMonitoringEnabled,
            localRole = raceSessionController.localDeviceRole(),
        )
    }

    private fun applyLocalMonitoringConfigFromSession() {
        val current = motionDetectionController.uiState.value.config
        val cameraFacing = when (raceSessionController.localCameraFacing()) {
            SessionCameraFacing.FRONT -> MotionCameraFacing.FRONT
            SessionCameraFacing.REAR -> MotionCameraFacing.REAR
        }
        val next = current.copy(
            cameraFacing = cameraFacing,
        )
        motionDetectionController.updateConfig(next)
    }

    private fun formatElapsedDisplay(
        startedSensorNanos: Long?,
        stoppedSensorNanos: Long?,
        monitoringActive: Boolean,
    ): String {
        val started = startedSensorNanos ?: return "00.00"
        val terminal = stoppedSensorNanos ?: if (monitoringActive) {
            SystemClock.elapsedRealtimeNanos()
        } else {
            started
        }
        val elapsedNanos = (terminal - started).coerceAtLeast(0L)
        val totalMillis = elapsedNanos / 1_000_000L
        return formatElapsedTimerDisplay(totalMillis)
    }

    private fun formatElapsedDuration(durationNanos: Long): String {
        val totalMillis = (durationNanos / 1_000_000L).coerceAtLeast(0L)
        return formatElapsedTimerDisplay(totalMillis)
    }

    private fun updateUiState(update: SprintSyncUiState.() -> SprintSyncUiState) {
        uiState.value = uiState.value.update()
    }

    private fun startControllerSummarySyncLoop() {
        if (controllerSummarySyncJob?.isActive == true) {
            return
        }
        controllerSummarySyncJob = lifecycleScope.launch {
            try {
                while (isActive) {
                    if (controllerSummarySyncPending) {
                        controllerSummarySyncPending = false
                        syncControllerSummaries()
                    }
                    delay(CONTROLLER_SUMMARY_SYNC_INTERVAL_MS)
                }
            } finally {
                controllerSummarySyncJob = null
            }
        }
    }

    private fun stopControllerSummarySyncLoop(flushPending: Boolean) {
        controllerSummarySyncJob?.cancel()
        controllerSummarySyncJob = null
        if (flushPending && controllerSummarySyncPending) {
            controllerSummarySyncPending = false
            syncControllerSummaries()
        }
    }

    private fun startTimerRefreshLoop() {
        if (timerRefreshJob?.isActive == true) {
            return
        }
        logRuntimeDiagnostic("timer refresh loop started")
        timerRefreshJob = lifecycleScope.launch {
            try {
                while (isActive) {
                    val raceState = raceSessionController.uiState.value
                    val mode = uiState.value.operatingMode
                    val isDisplayRole = mode == SessionOperatingMode.DISPLAY_HOST ||
                        raceState.deviceRole == SessionDeviceRole.DISPLAY
                    val connectedEndpointIds = connectionsManager.connectedEndpoints()
                    val hasPendingDisplayFinals = connectedEndpointIds.isNotEmpty() &&
                        connectedEndpointIds.any { endpointId ->
                            displayLatestLapByEndpointId[endpointId] == null
                        }
                    val hasDisplayLocalWaitingTimers = isDisplayRole &&
                        connectedEndpointIds.any { endpointId ->
                            displayWaitingEndpointIds.contains(endpointId) &&
                                displayLatestLapByEndpointId[endpointId] == null &&
                                displayWaitTextEnabledByEndpointId[endpointId] == false &&
                                displayWaitingStartElapsedRealtimeNanosByEndpointId[endpointId] != null
                        }
                    val shouldRefreshForDisplayRows = hasDisplayLocalWaitingTimers ||
                        (
                            isDisplayRole &&
                                raceState.timeline.hostStartSensorNanos != null &&
                                hasPendingDisplayFinals
                            )
                    if (!isAppResumed || (!raceState.monitoringActive && !shouldRefreshForDisplayRows)) {
                        break
                    }
                    if ((raceState.timeline.hostStartSensorNanos != null &&
                            raceState.timeline.hostStopSensorNanos == null) ||
                        shouldRefreshForDisplayRows
                    ) {
                        syncControllerSummaries()
                    }
                    val refreshDelayMillis = if (hasDisplayLocalWaitingTimers) {
                        DISPLAY_TIMER_REFRESH_INTERVAL_MS
                    } else {
                        TIMER_REFRESH_INTERVAL_MS
                    }
                    delay(refreshDelayMillis)
                }
            } finally {
                logRuntimeDiagnostic("timer refresh loop stopped")
                timerRefreshJob = null
            }
        }
    }

    private fun stopTimerRefreshLoop() {
        timerRefreshJob?.cancel()
        timerRefreshJob = null
    }

    private fun applyRequestedOrientationForMode(mode: SessionOperatingMode) {
        val targetOrientation = requestedOrientationForMode(mode)
        if (requestedOrientation != targetOrientation) {
            requestedOrientation = targetOrientation
        }
        applySystemUiForMode(mode)
    }

    private fun applySystemUiForMode(mode: SessionOperatingMode) {
        val immersive = shouldUseImmersiveModeForMode(mode)
        WindowCompat.setDecorFitsSystemWindows(window, !immersive)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (immersive) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun flushPendingLapResults() {
        val hostEndpoint = displayConnectedHostEndpointId ?: return

        while (pendingLapStartedMessages.isNotEmpty()) {
            val startedMessage = pendingLapStartedMessages.removeFirst()
            connectionsManager.sendMessage(hostEndpoint, startedMessage.toJsonString()) { result ->
                if (result.isFailure) {
                    pendingLapStartedMessages.addFirst(startedMessage)
                }
            }
        }

        while (pendingLapResults.isNotEmpty()) {
            val lapMessage = pendingLapResults.removeFirst()
            connectionsManager.sendMessage(hostEndpoint, lapMessage.toJsonString()) { result ->
                if (result.isFailure) {
                    // Re-queue at front if send fails, will retry on next flush
                    pendingLapResults.addFirst(lapMessage)
                }
            }
        }
    }

    private fun sendControllerCommandToDisplayHost(
        action: SessionControlAction,
        targetEndpointId: String,
        limitMillis: Long?,
        sensitivityPercent: Int?,
        autoReadyDelaySeconds: Int?,
        waitTextEnabled: Boolean?,
        gameModeEnabled: Boolean?,
        gameModeLimitMillis: Long?,
        gameModeLives: Int?,
        gameModeAutoEnabled: Boolean? = null,
        gameModeAutoEveryRuns: Int? = null,
    ) {
        val hostEndpoint = displayConnectedHostEndpointId
        if (hostEndpoint.isNullOrBlank()) {
            appendEvent("controller not connected to display host")
            return
        }
        val payload = SessionControlCommandMessage(
            action = action,
            targetEndpointId = targetEndpointId,
            senderDeviceName = localEndpointName(),
            limitMillis = limitMillis,
            sensitivityPercent = sensitivityPercent,
            autoReadyDelaySeconds = autoReadyDelaySeconds,
            waitTextEnabled = waitTextEnabled,
            gameModeEnabled = gameModeEnabled,
            gameModeLimitMillis = gameModeLimitMillis,
            gameModeLives = gameModeLives,
            gameModeAutoEnabled = gameModeAutoEnabled,
            gameModeAutoEveryRuns = gameModeAutoEveryRuns,
        ).toJsonString()
        connectionsManager.sendMessage(hostEndpoint, payload) { result ->
            result.exceptionOrNull()?.let { error ->
                appendEvent("controller command error: ${error.localizedMessage ?: "unknown"}")
            }
        }
    }

    private fun applyGameModeLifePenaltyIfNeeded(endpointId: String, elapsedNanos: Long) {
        if (displayGameModeEnabledByEndpointId[endpointId] != true) {
            return
        }
        val limitMillis = displayGameModeLimitMillisByEndpointId[endpointId] ?: DEFAULT_GAME_MODE_LIMIT_MILLIS
        val elapsedMillis = (elapsedNanos / 1_000_000L).coerceAtLeast(0L)
        if (elapsedMillis <= limitMillis) {
            return
        }
        val configuredLives = (displayGameModeConfiguredLivesByEndpointId[endpointId] ?: DEFAULT_GAME_MODE_LIVES)
            .coerceIn(1, 10)
        val currentLives = (displayGameModeCurrentLivesByEndpointId[endpointId] ?: configuredLives)
            .coerceIn(0, configuredLives)
        displayGameModeConfiguredLivesByEndpointId[endpointId] = configuredLives
        displayGameModeCurrentLivesByEndpointId[endpointId] = computeNextGameModeLives(
            gameModeEnabled = true,
            currentLives = currentLives,
            maxLives = configuredLives,
            elapsedNanos = elapsedNanos,
            limitMillis = limitMillis,
        )
    }

    private fun applyGameModeAutoLimitProgressIfNeeded(endpointId: String) {
        if (displayGameModeEnabledByEndpointId[endpointId] != true) {
            return
        }
        if (displayGameModeAutoEnabledByEndpointId[endpointId] != true) {
            return
        }
        val currentLimitMillis = displayGameModeLimitMillisByEndpointId[endpointId] ?: DEFAULT_GAME_MODE_LIMIT_MILLIS
        val currentRunCount = displayGameModeAutoRunCountByEndpointId[endpointId] ?: 0
        val everyRuns = displayGameModeAutoEveryRunsByEndpointId[endpointId] ?: DEFAULT_GAME_MODE_AUTO_EVERY_RUNS
        val advanced = advanceGameModeAutoLimit(
            currentRunCount = currentRunCount,
            everyRuns = everyRuns,
            currentLimitMillis = currentLimitMillis,
            reductionMillis = GAME_MODE_AUTO_LIMIT_REDUCTION_MILLIS,
            minLimitMillis = MIN_GAME_MODE_LIMIT_MILLIS,
        )
        displayGameModeAutoRunCountByEndpointId[endpointId] = advanced.nextRunCount
        displayGameModeLimitMillisByEndpointId[endpointId] = advanced.nextLimitMillis
    }

    private fun broadcastControllerTargetsSnapshotToConnectedEndpoints() {
        if (uiState.value.operatingMode != SessionOperatingMode.DISPLAY_HOST) {
            return
        }
        val connectedEndpoints = connectionsManager.connectedEndpoints()
        if (connectedEndpoints.isEmpty()) {
            return
        }
        connectedEndpoints.forEach { endpointId ->
            if (!displayControllerEndpointIds.contains(endpointId)) {
                return@forEach
            }
            val targets = connectedEndpoints
                .asSequence()
                .filter { candidateEndpoint -> candidateEndpoint != endpointId }
                .filter { candidateEndpoint -> !displayControllerEndpointIds.contains(candidateEndpoint) }
                .filter { candidateEndpoint ->
                    val candidateName = displayHostDeviceNamesByEndpointId[candidateEndpoint].orEmpty()
                    !candidateName.contains("controller", ignoreCase = true)
                }
                .map { candidateEndpoint ->
                    SessionControllerTarget(
                        endpointId = candidateEndpoint,
                        deviceName = displayHostDeviceNamesByEndpointId[candidateEndpoint]
                            ?.takeIf { it.isNotBlank() }
                            ?: candidateEndpoint,
                    )
                }
                .toList()
            val payload = SessionControllerTargetsMessage(
                senderDeviceName = localEndpointName(),
                targets = targets,
            ).toJsonString()
            connectionsManager.sendMessage(endpointId, payload) { result ->
                result.exceptionOrNull()?.let { error ->
                    appendEvent("target snapshot error: ${error.localizedMessage ?: "unknown"}")
                }
            }
        }
    }

    private fun scheduleDisplayAutoReadyReset(endpointId: String) {
        cancelDisplayAutoReadyReset(endpointId)
        val configuredDelaySeconds = displayAutoReadyDelaySecondsByEndpointId[endpointId]
        val effectiveDelaySeconds = effectiveAutoReadyDelaySeconds(configuredDelaySeconds)
            ?: return
        val delayMillis = effectiveDelaySeconds * 1_000L
        val job = lifecycleScope.launch {
            try {
                delay(delayMillis)
                if (raceSessionController.uiState.value.operatingMode != SessionOperatingMode.DISPLAY_HOST) {
                    return@launch
                }
                if (!connectionsManager.connectedEndpoints().contains(endpointId)) {
                    return@launch
                }
                displayLatestLapByEndpointId.remove(endpointId)
                displayWaitingEndpointIds.remove(endpointId)
                displayWaitingStartElapsedRealtimeNanosByEndpointId.remove(endpointId)
                val resetPayload = SessionControlCommandMessage(
                    action = SessionControlAction.RESET_TIMER,
                    targetEndpointId = endpointId,
                    senderDeviceName = localEndpointName(),
                    limitMillis = null,
                    sensitivityPercent = null,
                    autoReadyDelaySeconds = null,
                    waitTextEnabled = null,
                ).toJsonString()
                connectionsManager.sendMessage(endpointId, resetPayload) { result ->
                    result.exceptionOrNull()?.let { error ->
                        appendEvent("auto ready reset error: ${error.localizedMessage ?: "unknown"}")
                    }
                }
                broadcastControllerTargetsSnapshotToConnectedEndpoints()
                syncControllerSummaries()
            } finally {
                displayAutoReadyResetJobsByEndpointId.remove(endpointId)
            }
        }
        displayAutoReadyResetJobsByEndpointId[endpointId] = job
    }

    private fun cancelDisplayAutoReadyReset(endpointId: String) {
        displayAutoReadyResetJobsByEndpointId.remove(endpointId)?.cancel()
    }

    private fun cancelAllDisplayAutoReadyResets() {
        displayAutoReadyResetJobsByEndpointId.values.forEach { job ->
            job.cancel()
        }
        displayAutoReadyResetJobsByEndpointId.clear()
    }

    private fun clearDisplayRelayReconnectionState() {
        displayReconnectionPending = false
        pendingLapStartedMessages.clear()
        pendingLapResults.clear()
    }

    private fun clearDisplayHostLapState() {
        cancelAllDisplayAutoReadyResets()
        displayControllerEndpointIds.clear()
        displayHostDeviceNamesByEndpointId.clear()
        displayLatestLapByEndpointId.clear()
        displayWaitingEndpointIds.clear()
        displayWaitingStartElapsedRealtimeNanosByEndpointId.clear()
        displayWaitTextEnabledByEndpointId.clear()
        displayLimitMillisByEndpointId.clear()
        displayGameModeEnabledByEndpointId.clear()
        displayGameModeLimitMillisByEndpointId.clear()
        displayGameModeConfiguredLivesByEndpointId.clear()
        displayGameModeCurrentLivesByEndpointId.clear()
        displayGameModeAutoEnabledByEndpointId.clear()
        displayGameModeAutoEveryRunsByEndpointId.clear()
        displayGameModeAutoRunCountByEndpointId.clear()
        displayAutoReadyDelaySecondsByEndpointId.clear()
    }

    private fun logRuntimeDiagnostic(message: String) {
        Log.d(TAG, "diag: $message")
    }

    @Suppress("DEPRECATION")
    private fun currentConnectedWifiSsid(): String? {
        val wifiManager = ContextCompat.getSystemService(applicationContext, WifiManager::class.java)
            ?: return null
        if (!wifiManager.isWifiEnabled) {
            return null
        }
        return normalizeWifiSsid(wifiManager.connectionInfo?.ssid)
    }

    private fun normalizeWifiSsid(rawSsid: String?): String? {
        val value = rawSsid?.trim().orEmpty()
        if (value.isEmpty() || value.equals("<unknown ssid>", ignoreCase = true)) {
            return null
        }
        return if (value.length >= 2 && value.first() == '"' && value.last() == '"') {
            value.substring(1, value.length - 1).trim().ifEmpty { null }
        } else {
            value
        }
    }

    private fun isConnectedToExpectedWifi(currentSsid: String?, expectedSsid: String): Boolean {
        val current = normalizeWifiSsid(currentSsid) ?: return false
        val expected = normalizeWifiSsid(expectedSsid) ?: return false
        return current == expected
    }
}

internal enum class LocalCaptureAction {
    START,
    STOP,
    NONE,
}

internal enum class RuntimeStartupAction {
    START_DISPLAY_HOST,
    START_CONTROLLER,
    START_SINGLE_DEVICE,
}

internal fun resolveRuntimeStartupAction(runtimeDeviceConfig: RuntimeDeviceConfig): RuntimeStartupAction {
    return when (runtimeDeviceConfig.networkRole) {
        RuntimeNetworkRole.HOST -> RuntimeStartupAction.START_DISPLAY_HOST
        RuntimeNetworkRole.CLIENT -> RuntimeStartupAction.START_CONTROLLER
        RuntimeNetworkRole.NONE -> RuntimeStartupAction.START_SINGLE_DEVICE
    }
}

internal fun resolveMonitoringConnectionTypeLabel(hasPeers: Boolean, hostIp: String, hostPort: Int): String {
    if (!hasPeers) {
        return "-"
    }
    return "TCP (${hostIp.trim()}:${hostPort})"
}

internal fun reconnectDelayMillis(attempt: Int): Long {
    val clamped = attempt.coerceAtLeast(0).coerceAtMost(6)
    val delay = 500L shl clamped
    return delay.coerceAtMost(5_000L)
}

internal fun effectiveAutoReadyDelaySeconds(configuredDelaySeconds: Int?): Int? {
    return when {
        configuredDelaySeconds == null -> 2
        configuredDelaySeconds == 0 -> null
        configuredDelaySeconds in 1..5 -> configuredDelaySeconds
        else -> 2
    }
}

internal fun ipv4FromLittleEndianInt(value: Int): String? {
    if (value == 0) {
        return null
    }
    val b1 = value and 0xFF
    val b2 = (value shr 8) and 0xFF
    val b3 = (value shr 16) and 0xFF
    val b4 = (value shr 24) and 0xFF
    return "$b1.$b2.$b3.$b4"
}

internal fun resolveLocalCaptureAction(
    monitoringActive: Boolean,
    isAppResumed: Boolean,
    shouldRunLocalCapture: Boolean,
    isLocalMotionMonitoring: Boolean,
    localCaptureStartPending: Boolean,
): LocalCaptureAction {
    if (
        monitoringActive &&
        isAppResumed &&
        shouldRunLocalCapture &&
        !isLocalMotionMonitoring &&
        !localCaptureStartPending
    ) {
        return LocalCaptureAction.START
    }
    if (
        (isLocalMotionMonitoring || localCaptureStartPending) &&
        (!monitoringActive || !isAppResumed || !shouldRunLocalCapture)
    ) {
        return LocalCaptureAction.STOP
    }
    return LocalCaptureAction.NONE
}

internal fun shouldKeepTimerRefreshActive(
    monitoringActive: Boolean,
    isAppResumed: Boolean,
    hasStopSensor: Boolean,
): Boolean {
    return monitoringActive && isAppResumed && !hasStopSensor
}

internal fun shouldUseLandscapeForMode(mode: SessionOperatingMode): Boolean = false

internal fun shouldUseImmersiveModeForMode(mode: SessionOperatingMode): Boolean =
    mode == SessionOperatingMode.DISPLAY_HOST

internal fun shouldApplyLiveLocalCameraFacingUpdate(
    isLocalMotionMonitoring: Boolean,
    assignedDeviceId: String,
    localDeviceId: String,
): Boolean {
    return isLocalMotionMonitoring && assignedDeviceId == localDeviceId
}

internal fun shouldUsePassiveDisplayClientMode(
    mode: SessionOperatingMode,
    networkRole: SessionNetworkRole,
    localRole: SessionDeviceRole,
): Boolean {
    return mode == SessionOperatingMode.SINGLE_DEVICE &&
        networkRole == SessionNetworkRole.CLIENT &&
        localRole == SessionDeviceRole.DISPLAY
}

internal fun shouldRunLocalMonitoring(
    mode: SessionOperatingMode,
    userMonitoringEnabled: Boolean,
    localRole: SessionDeviceRole,
): Boolean {
    if (
        mode == SessionOperatingMode.DISPLAY_HOST ||
        localRole == SessionDeviceRole.DISPLAY ||
        localRole == SessionDeviceRole.CONTROLLER
    ) {
        return false
    }
    return userMonitoringEnabled
}

internal fun controllerInitialStage(): SessionStage = SessionStage.MONITORING

internal fun isControllerEndpointName(deviceName: String?): Boolean {
    return deviceName?.contains("controller", ignoreCase = true) == true
}

internal fun thresholdFromSensitivityPercent(sensitivityPercent: Int): Double {
    val clampedPercent = sensitivityPercent.coerceIn(0, 100)
    val fraction = clampedPercent / 100.0
    val threshold = 0.08 - (0.079 * fraction)
    return threshold.coerceIn(0.001, 0.08)
}

internal fun buildDisplayLapRowsForConnectedDevices(
    connectedEndpointIds: Set<String>,
    deviceNamesByEndpointId: Map<String, String>,
    elapsedByEndpointId: Map<String, Long>,
    waitingEndpointIds: Set<String> = emptySet(),
    waitingStartElapsedRealtimeNanosByEndpointId: Map<String, Long> = emptyMap(),
    waitTextEnabledByEndpointId: Map<String, Boolean> = emptyMap(),
    limitMillisByEndpointId: Map<String, Long>,
    gameModeEnabledByEndpointId: Map<String, Boolean> = emptyMap(),
    gameModeLimitMillisByEndpointId: Map<String, Long> = emptyMap(),
    gameModeConfiguredLivesByEndpointId: Map<String, Int> = emptyMap(),
    gameModeCurrentLivesByEndpointId: Map<String, Int> = emptyMap(),
    hostStartSensorNanos: Long?,
    hostStopSensorNanos: Long?,
    monitoringActive: Boolean,
    nowSensorNanos: Long,
    nowDisplayElapsedRealtimeNanos: Long = nowSensorNanos,
): List<DisplayLapRow> {
    val shouldShowLiveElapsed = hostStartSensorNanos != null &&
        (monitoringActive || hostStopSensorNanos != null)
    val liveElapsedNanos = if (shouldShowLiveElapsed) {
        (nowSensorNanos - (hostStartSensorNanos ?: 0L)).coerceAtLeast(0L)
    } else {
        null
    }
    return connectedEndpointIds.map { endpointId ->
        val deviceName = deviceNamesByEndpointId[endpointId]?.takeIf { it.isNotBlank() } ?: endpointId
        val endpointFinalNanos = elapsedByEndpointId[endpointId]
        val endpointIsWaiting = endpointFinalNanos == null && waitingEndpointIds.contains(endpointId)
        val waitTextEnabled = waitTextEnabledByEndpointId[endpointId] != false
        val waitingStartElapsedRealtimeNanos = waitingStartElapsedRealtimeNanosByEndpointId[endpointId]
        val localDisplayTimerNanos = if (
            endpointIsWaiting &&
            !waitTextEnabled &&
            waitingStartElapsedRealtimeNanos != null
        ) {
            (nowDisplayElapsedRealtimeNanos - waitingStartElapsedRealtimeNanos).coerceAtLeast(0L)
        } else {
            null
        }
        val elapsedNanos = endpointFinalNanos ?: localDisplayTimerNanos ?: if (endpointIsWaiting) null else liveElapsedNanos
        val lapTimeLabel = when {
            elapsedNanos != null -> {
                val totalMillis = (elapsedNanos / 1_000_000L).coerceAtLeast(0L)
                formatElapsedTimerDisplay(totalMillis)
            }
            endpointIsWaiting -> "WAIT"
            else -> "READY"
        }
        val shouldPulseWait = endpointIsWaiting && waitTextEnabled
        val configuredDisplayLimitMillis = limitMillisByEndpointId[endpointId]
        val gameModeEnabled = gameModeEnabledByEndpointId[endpointId] == true
        val gameModeLimitMillis = gameModeLimitMillisByEndpointId[endpointId] ?: 5_000L
        val effectiveLimitMillis = configuredDisplayLimitMillis
            ?: if (gameModeEnabled) gameModeLimitMillis else null
        val limitNanos = effectiveLimitMillis?.times(1_000_000L)
        val isOverLimit = limitNanos != null && elapsedNanos != null && elapsedNanos > limitNanos
        val isUnderLimit = limitNanos != null && elapsedNanos != null && elapsedNanos <= limitNanos
        val maxLives = (gameModeConfiguredLivesByEndpointId[endpointId] ?: 10).coerceIn(1, 10)
        val currentLives = (gameModeCurrentLivesByEndpointId[endpointId] ?: maxLives).coerceIn(0, maxLives)
        DisplayLapRow(
            deviceName = deviceName,
            lapTimeLabel = lapTimeLabel,
            limitLabel = effectiveLimitMillis?.let(::formatDisplayLimitLabel),
            isOverLimit = isOverLimit,
            isUnderLimit = isUnderLimit,
            isWaiting = shouldPulseWait,
            showLives = gameModeEnabled,
            currentLives = currentLives,
            maxLives = maxLives,
        )
    }
}

internal fun formatDisplayLimitLabel(limitMillis: Long): String = "Limit ${limitMillis} ms"

internal fun computeNextGameModeLives(
    gameModeEnabled: Boolean,
    currentLives: Int,
    maxLives: Int,
    elapsedNanos: Long,
    limitMillis: Long,
): Int {
    if (!gameModeEnabled) {
        return currentLives.coerceIn(0, maxLives.coerceAtLeast(0))
    }
    val clampedMaxLives = maxLives.coerceIn(1, 10)
    val clampedCurrentLives = currentLives.coerceIn(0, clampedMaxLives)
    val elapsedMillis = (elapsedNanos / 1_000_000L).coerceAtLeast(0L)
    if (elapsedMillis <= limitMillis.coerceAtLeast(1L)) {
        return clampedCurrentLives
    }
    return (clampedCurrentLives - 1).coerceAtLeast(0)
}

internal data class GameModeAutoAdvance(
    val nextRunCount: Int,
    val nextLimitMillis: Long,
)

internal fun advanceGameModeAutoLimit(
    currentRunCount: Int,
    everyRuns: Int,
    currentLimitMillis: Long,
    reductionMillis: Long = 100L,
    minLimitMillis: Long = 100L,
): GameModeAutoAdvance {
    val clampedEveryRuns = everyRuns.coerceIn(1, 20)
    val clampedLimitMillis = currentLimitMillis.coerceAtLeast(minLimitMillis)
    val nextCount = currentRunCount.coerceAtLeast(0) + 1
    if (nextCount < clampedEveryRuns) {
        return GameModeAutoAdvance(
            nextRunCount = nextCount,
            nextLimitMillis = clampedLimitMillis,
        )
    }
    return GameModeAutoAdvance(
        nextRunCount = 0,
        nextLimitMillis = (clampedLimitMillis - reductionMillis).coerceAtLeast(minLimitMillis),
    )
}

internal fun formatElapsedTimerDisplay(totalMillis: Long): String {
    val clamped = totalMillis.coerceAtLeast(0L)
    val totalSeconds = clamped / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    val centiseconds = (clamped % 1_000L) / 10L
    return if (minutes > 0L) {
        String.format("%02d:%02d.%02d", minutes, seconds, centiseconds)
    } else {
        String.format("%02d.%02d", seconds, centiseconds)
    }
}

internal fun buildSplitHistoryForTimeline(
    startedSensorNanos: Long?,
    splitSensorNanos: List<Long>,
): List<String> {
    val started = startedSensorNanos ?: return emptyList()
    return splitSensorNanos.mapIndexedNotNull { index, splitSensor ->
        if (splitSensor <= started) {
            null
        } else {
            val elapsedMillis = (splitSensor - started) / 1_000_000L
            "Split ${index + 1}: ${formatElapsedTimerDisplay(elapsedMillis)}"
        }
    }
}

internal fun requestedOrientationForMode(mode: SessionOperatingMode): Int = if (shouldUseLandscapeForMode(mode)) {
    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
} else {
    ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
}
