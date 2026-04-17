# Graph Report - .  (2026-04-17)

## Corpus Check
- Corpus is ~32,761 words - fits in a single context window. You may not need a graph.

## Summary
- 659 nodes · 619 edges · 60 communities detected
- Extraction: 99% EXTRACTED · 1% INFERRED · 0% AMBIGUOUS · INFERRED: 4 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- [[_COMMUNITY_Community 0|Community 0]]
- [[_COMMUNITY_Community 1|Community 1]]
- [[_COMMUNITY_Community 2|Community 2]]
- [[_COMMUNITY_Community 3|Community 3]]
- [[_COMMUNITY_Community 4|Community 4]]
- [[_COMMUNITY_Community 5|Community 5]]
- [[_COMMUNITY_Community 6|Community 6]]
- [[_COMMUNITY_Community 7|Community 7]]
- [[_COMMUNITY_Community 8|Community 8]]
- [[_COMMUNITY_Community 9|Community 9]]
- [[_COMMUNITY_Community 10|Community 10]]
- [[_COMMUNITY_Community 11|Community 11]]
- [[_COMMUNITY_Community 12|Community 12]]
- [[_COMMUNITY_Community 13|Community 13]]
- [[_COMMUNITY_Community 14|Community 14]]
- [[_COMMUNITY_Community 15|Community 15]]
- [[_COMMUNITY_Community 16|Community 16]]
- [[_COMMUNITY_Community 17|Community 17]]
- [[_COMMUNITY_Community 18|Community 18]]
- [[_COMMUNITY_Community 19|Community 19]]
- [[_COMMUNITY_Community 20|Community 20]]
- [[_COMMUNITY_Community 21|Community 21]]
- [[_COMMUNITY_Community 22|Community 22]]
- [[_COMMUNITY_Community 23|Community 23]]
- [[_COMMUNITY_Community 24|Community 24]]
- [[_COMMUNITY_Community 25|Community 25]]
- [[_COMMUNITY_Community 26|Community 26]]
- [[_COMMUNITY_Community 27|Community 27]]
- [[_COMMUNITY_Community 28|Community 28]]
- [[_COMMUNITY_Community 29|Community 29]]
- [[_COMMUNITY_Community 30|Community 30]]
- [[_COMMUNITY_Community 31|Community 31]]
- [[_COMMUNITY_Community 32|Community 32]]
- [[_COMMUNITY_Community 33|Community 33]]
- [[_COMMUNITY_Community 34|Community 34]]
- [[_COMMUNITY_Community 35|Community 35]]
- [[_COMMUNITY_Community 36|Community 36]]
- [[_COMMUNITY_Community 37|Community 37]]
- [[_COMMUNITY_Community 38|Community 38]]
- [[_COMMUNITY_Community 39|Community 39]]
- [[_COMMUNITY_Community 40|Community 40]]
- [[_COMMUNITY_Community 41|Community 41]]
- [[_COMMUNITY_Community 42|Community 42]]
- [[_COMMUNITY_Community 43|Community 43]]
- [[_COMMUNITY_Community 44|Community 44]]
- [[_COMMUNITY_Community 45|Community 45]]
- [[_COMMUNITY_Community 46|Community 46]]
- [[_COMMUNITY_Community 47|Community 47]]
- [[_COMMUNITY_Community 48|Community 48]]
- [[_COMMUNITY_Community 49|Community 49]]
- [[_COMMUNITY_Community 50|Community 50]]
- [[_COMMUNITY_Community 51|Community 51]]
- [[_COMMUNITY_Community 52|Community 52]]
- [[_COMMUNITY_Community 53|Community 53]]
- [[_COMMUNITY_Community 54|Community 54]]
- [[_COMMUNITY_Community 55|Community 55]]
- [[_COMMUNITY_Community 56|Community 56]]
- [[_COMMUNITY_Community 57|Community 57]]
- [[_COMMUNITY_Community 58|Community 58]]
- [[_COMMUNITY_Community 59|Community 59]]

## God Nodes (most connected - your core abstractions)
1. `MainActivity` - 57 edges
2. `MainActivityMonitoringLogicTest` - 53 edges
3. `SensorNativeController` - 30 edges
4. `SprintSyncAppLayoutLogicTest` - 24 edges
5. `RaceSessionController` - 21 edges
6. `SensorNativeMathTest` - 21 edges
7. `TcpConnectionsManager` - 19 edges
8. `RaceSessionModelsTest` - 17 edges
9. `MotionDetectionController` - 13 edges
10. `SensorNativeCameraSession` - 11 edges

## Surprising Connections (you probably didn't know these)
- `fail()` --calls--> `Error`  [INFERRED]
  scripts\install-debug-apk.mjs → app\src\main\kotlin\com\paul\sprintsync\feature\motion\data\nativebridge\SensorNativeEvents.kt
- `fail()` --calls--> `Error`  [INFERRED]
  scripts\install-debug-device-flavors-adb-install-only.mjs → app\src\main\kotlin\com\paul\sprintsync\feature\motion\data\nativebridge\SensorNativeEvents.kt
- `fail()` --calls--> `Error`  [INFERRED]
  scripts\install-debug-device-flavors-adb.mjs → app\src\main\kotlin\com\paul\sprintsync\feature\motion\data\nativebridge\SensorNativeEvents.kt
- `fail()` --calls--> `Error`  [INFERRED]
  scripts\install-release-apk.mjs → app\src\main\kotlin\com\paul\sprintsync\feature\motion\data\nativebridge\SensorNativeEvents.kt

## Communities

### Community 0 - "Community 0"
Cohesion: 0.04
Nodes (1): MainActivity

### Community 1 - "Community 1"
Cohesion: 0.04
Nodes (1): MainActivityMonitoringLogicTest

### Community 2 - "Community 2"
Cohesion: 0.06
Nodes (1): SensorNativeController

### Community 3 - "Community 3"
Cohesion: 0.06
Nodes (13): SessionCameraFacing, SessionControlAction, SessionControlCommandMessage, SessionControllerIdentityMessage, SessionControllerTarget, SessionControllerTargetsMessage, SessionDevice, SessionDeviceRole (+5 more)

### Community 4 - "Community 4"
Cohesion: 0.07
Nodes (27): AssignCameraFacing, ConnectDisplayHost, MainAction, OpenWifiSettings, PlayStartSound, RequestPermissions, ResetDeviceTimer, ResetRun (+19 more)

### Community 5 - "Community 5"
Cohesion: 0.07
Nodes (4): GameModeAutoAdvance, LocalCaptureAction, PermissionScope, RuntimeStartupAction

### Community 6 - "Community 6"
Cohesion: 0.11
Nodes (22): fail(), fail(), forceStopPackages(), fail(), forceStopPackages(), isSuccessOutput(), listTrainingPackages(), parseListedPackages() (+14 more)

### Community 7 - "Community 7"
Cohesion: 0.07
Nodes (2): DisplayLayoutSpec, SetupActionProfile

### Community 8 - "Community 8"
Cohesion: 0.08
Nodes (1): SprintSyncAppLayoutLogicTest

### Community 9 - "Community 9"
Cohesion: 0.08
Nodes (3): RaceSessionController, RaceSessionUiState, SessionRaceTimeline

### Community 10 - "Community 10"
Cohesion: 0.09
Nodes (4): CameraBinding, CameraFacingSelection, SensorNativeCameraPolicy, SensorNativeCameraSession

### Community 11 - "Community 11"
Cohesion: 0.09
Nodes (1): SensorNativeMathTest

### Community 12 - "Community 12"
Cohesion: 0.1
Nodes (1): TcpConnectionsManager

### Community 13 - "Community 13"
Cohesion: 0.11
Nodes (1): RaceSessionModelsTest

### Community 14 - "Community 14"
Cohesion: 0.12
Nodes (5): NativeDetectionMath, NativeFpsObservation, RoiFrameDiffer, SensorNativeFpsMonitor, SensorOffsetSmoother

### Community 15 - "Community 15"
Cohesion: 0.13
Nodes (2): MotionDetectionController, MotionDetectionUiState

### Community 16 - "Community 16"
Cohesion: 0.13
Nodes (2): ControllerTargetsCallbacks, ControllerTargetsUiState

### Community 17 - "Community 17"
Cohesion: 0.18
Nodes (1): SessionConnectionsManager

### Community 18 - "Community 18"
Cohesion: 0.18
Nodes (5): NativeCameraFacing, NativeCameraFpsMode, NativeFrameStats, NativeMonitoringConfig, NativeTriggerEvent

### Community 19 - "Community 19"
Cohesion: 0.2
Nodes (1): RaceSessionControllerTest

### Community 20 - "Community 20"
Cohesion: 0.25
Nodes (3): MainActivityMonitoringProjection, MainActivityMonitoringProjectionInput, MonitoringTriggerSnapshot

### Community 21 - "Community 21"
Cohesion: 0.25
Nodes (2): AppUpdateChecker, UpdateInfo

### Community 22 - "Community 22"
Cohesion: 0.25
Nodes (7): ConnectionResult, EndpointDisconnected, EndpointFound, EndpointLost, Error, NearbyEvent, PayloadReceived

### Community 23 - "Community 23"
Cohesion: 0.25
Nodes (1): NsdServiceDiscovery

### Community 24 - "Community 24"
Cohesion: 0.25
Nodes (1): AppUpdateCheckerTest

### Community 25 - "Community 25"
Cohesion: 0.29
Nodes (1): LocalRepository

### Community 26 - "Community 26"
Cohesion: 0.29
Nodes (1): MainActivityMonitoringProjectionTest

### Community 27 - "Community 27"
Cohesion: 0.57
Nodes (6): forceStopPackages(), isSuccessOutput(), listTrainingPackages(), parseListedPackages(), run(), uninstallLegacyPackages()

### Community 28 - "Community 28"
Cohesion: 0.33
Nodes (2): MotionCameraFacing, MotionDetectionConfig

### Community 29 - "Community 29"
Cohesion: 0.33
Nodes (1): TcpConnectionsManagerDiscoveryTest

### Community 30 - "Community 30"
Cohesion: 0.4
Nodes (0): 

### Community 31 - "Community 31"
Cohesion: 0.4
Nodes (1): DisplayArrowDirection

### Community 32 - "Community 32"
Cohesion: 0.4
Nodes (0): 

### Community 33 - "Community 33"
Cohesion: 0.4
Nodes (1): DeviceDetectorTest

### Community 34 - "Community 34"
Cohesion: 0.5
Nodes (1): DeviceDetector

### Community 35 - "Community 35"
Cohesion: 0.5
Nodes (3): RuntimeDeviceConfig, RuntimeNetworkRole, RuntimeOperatingMode

### Community 36 - "Community 36"
Cohesion: 0.5
Nodes (1): LastRunResult

### Community 37 - "Community 37"
Cohesion: 0.5
Nodes (1): SensorNativePreviewPlatformView

### Community 38 - "Community 38"
Cohesion: 0.5
Nodes (1): SensorNativePreviewViewFactory

### Community 39 - "Community 39"
Cohesion: 0.5
Nodes (1): TcpConnectionsManagerIdentityTest

### Community 40 - "Community 40"
Cohesion: 0.5
Nodes (1): TcpConnectionsManagerTest

### Community 41 - "Community 41"
Cohesion: 0.5
Nodes (1): SensorNativeControllerPreviewTimingTest

### Community 42 - "Community 42"
Cohesion: 0.5
Nodes (1): SensorNativeModelsTest

### Community 43 - "Community 43"
Cohesion: 0.5
Nodes (1): MotionDetectionModelsTest

### Community 44 - "Community 44"
Cohesion: 0.67
Nodes (2): NearbyRole, NearbyTransportStrategy

### Community 45 - "Community 45"
Cohesion: 0.67
Nodes (1): GatewayResolver

### Community 46 - "Community 46"
Cohesion: 0.67
Nodes (0): 

### Community 47 - "Community 47"
Cohesion: 0.67
Nodes (1): CardHighlightIntent

### Community 48 - "Community 48"
Cohesion: 0.67
Nodes (2): DisplayLapRow, SprintSyncUiState

### Community 49 - "Community 49"
Cohesion: 0.67
Nodes (1): GatewayResolverTest

### Community 50 - "Community 50"
Cohesion: 1.0
Nodes (0): 

### Community 51 - "Community 51"
Cohesion: 1.0
Nodes (0): 

### Community 52 - "Community 52"
Cohesion: 1.0
Nodes (0): 

### Community 53 - "Community 53"
Cohesion: 1.0
Nodes (0): 

### Community 54 - "Community 54"
Cohesion: 1.0
Nodes (0): 

### Community 55 - "Community 55"
Cohesion: 1.0
Nodes (0): 

### Community 56 - "Community 56"
Cohesion: 1.0
Nodes (0): 

### Community 57 - "Community 57"
Cohesion: 1.0
Nodes (0): 

### Community 58 - "Community 58"
Cohesion: 1.0
Nodes (0): 

### Community 59 - "Community 59"
Cohesion: 1.0
Nodes (0): 

## Knowledge Gaps
- **79 isolated node(s):** `MainAction`, `RequestPermissions`, `StartSingleDevice`, `StartDisplayHost`, `StartDisplayDiscovery` (+74 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **Thin community `Community 50`** (2 nodes): `SprintSyncApp.kt`, `SprintSyncApp()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 51`** (2 nodes): `Theme.kt`, `SprintSyncTheme()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 52`** (2 nodes): `Headers.kt`, `SectionHeader()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 53`** (2 nodes): `MetricDisplay.kt`, `MetricDisplay()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 54`** (1 nodes): `build.gradle.kts`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 55`** (1 nodes): `settings.gradle.kts`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 56`** (1 nodes): `build.gradle.kts`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 57`** (1 nodes): `Color.kt`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 58`** (1 nodes): `Shape.kt`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 59`** (1 nodes): `Type.kt`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.