## Plan: Static Host, Local Timing, Xiaomi Server Flavor

Port communication to a static-host model with local timing authority only, and update flavored APK outputs so Xiaomi Pad is the explicit server build. Cross-device clock sync is removed from runtime behavior.

**Steps**
1. Lock final scope and role mapping.
- Keep TCP communication for discovery, connect, disconnect, and payload messaging.
- Remove cross-device clock sync from active runtime logic.
- Define server role mapping: Xiaomi Pad flavor is host/server; phone flavors are client/controller as configured.
- Depends on: none.

2. Enforce static-host connection wiring.
- Update MainActivity connection manager setup to use static BuildConfig host values as the sole target source.
- Remove dynamic gateway-based host resolution from active connection path.
- Depends on: Step 1.

3. Enforce local timing authority.
- Remove or disable clock-sync-gated trigger logic in MainActivity so local start/stop/finish are never blocked by remote sync state.
- Remove cross-device clock-sync orchestration from RaceSessionController while keeping session messaging intact.
- Depends on: Step 2.

4. Simplify service and transport APIs.
- Remove or deprecate sendClockSyncPayload and sync-event handling paths in SessionConnectionsManager, TcpConnectionsManager, and NearbyEvents if no longer required.
- Remove SessionClockSyncBinaryCodec and related references after call-site cleanup.
- Keep frame transport and connection lifecycle behavior unchanged.
- Depends on: Step 3.

5. Update flavor configuration with Xiaomi as server.
- In build.gradle flavor definitions, explicitly set Xiaomi Pad flavor AUTO_START_ROLE to host/server semantics.
- Keep oneplus flavor as controller if still desired; keep other flavors as client/single based on device matrix.
- Ensure each participating flavor has static TCP host fields for deployment consistency.
- Depends on: Step 2.

6. Update flavored APK build/install pipeline.
- Update package.json scripts so build:flavor:apks produces the required flavor APK set for current devices, including Xiaomi server flavor first-class.
- Update install-debug-device-flavors-adb script mappings and expected APK candidates to match the final flavor set.
- Add a concise flavor matrix in README (device model to flavor to role) to avoid mismatched installs.
- Parallel with: Step 5.

7. Rebaseline tests for local-only timing and flavor role expectations.
- Replace clock-sync-focused tests with local timing authority tests in RaceSessionControllerTest.
- Add regression tests for static-host discovery/connection and point-to-point busy behavior.
- Add configuration checks that Xiaomi flavor resolves to host/server startup behavior.
- Depends on: Steps 4, 5, and 6.

8. Validate and sign off.
- Build flavored APKs and run unit tests/lint.
- Manual multi-device test: Xiaomi Pad starts as host/server, client/controller devices connect, local timing controls start/stop/finish.
- Verify no remaining runtime dependency on remote clock sync.
- Depends on: Step 7.

**Payload messages to add**
1. SessionDeviceConfigUpdateMessage
- type: device_config_update
- direction: host/server -> target client device
- fields: targetStableDeviceId (String), sensitivity (Int 1..100)
- purpose: push remote sensitivity updates to a specific client device.

2. SessionDeviceTelemetryMessage
- type: device_telemetry
- direction: client device -> host/server
- fields: stableDeviceId (String), role (SessionDeviceRole), sensitivity (Int 1..100), latencyMs (Int?), clockSynced (Boolean), timestampMillis (Long)
- purpose: publish client runtime status so host can show live device state and health.

3. Keep existing payloads already in target (do not re-add)
- snapshot
- trigger_request
- trigger_refinement
- timeline_snapshot
- session_trigger
- device_identity
- lap_result
- control_command
- controller_targets
- controller_identity

**Relevant files**
- /mnt/c/Users/paul/projects/photo-finish-training/android/app/build.gradle.kts — flavor role fields and static host config per flavor.
- /mnt/c/Users/paul/projects/photo-finish-training/android/app/src/main/kotlin/com/paul/sprintsync/MainActivity.kt — static host wiring, startup role flow, local timing authority.
- /mnt/c/Users/paul/projects/photo-finish-training/android/app/src/main/kotlin/com/paul/sprintsync/features/race_session/RaceSessionController.kt — remove cross-device clock sync orchestration.
- /mnt/c/Users/paul/projects/photo-finish-training/android/app/src/main/kotlin/com/paul/sprintsync/core/services/SessionConnectionsManager.kt — remove sync-specific API surface.
- /mnt/c/Users/paul/projects/photo-finish-training/android/app/src/main/kotlin/com/paul/sprintsync/core/services/TcpConnectionsManager.kt — remove sync-specific transport path; preserve messaging transport.
- /mnt/c/Users/paul/projects/photo-finish-training/android/app/src/main/kotlin/com/paul/sprintsync/core/services/NearbyEvents.kt — clean sync-only event variants if unused.
- /mnt/c/Users/paul/projects/photo-finish-training/android/app/src/main/kotlin/com/paul/sprintsync/features/race_session/SessionClockSyncBinaryCodec.kt — remove if unused.
- /mnt/c/Users/paul/projects/photo-finish-training/package.json — flavored APK build scripts.
- /mnt/c/Users/paul/projects/photo-finish-training/scripts/install-debug-device-flavors-adb.mjs — device model to flavor APK mapping/install flow.
- /mnt/c/Users/paul/projects/photo-finish-training/README.md — flavor role/install matrix documentation.
- /mnt/c/Users/paul/projects/photo-finish-training/android/app/src/test/kotlin/com/paul/sprintsync/features/race_session/RaceSessionControllerTest.kt — local timing tests.
- /mnt/c/Users/paul/projects/photo-finish-training/android/app/src/test/kotlin/com/paul/sprintsync/core/services/TcpConnectionsManagerIdentityTest.kt — transport behavior tests.

**Verification**
1. From /mnt/c/Users/paul/projects/photo-finish-training/android run ./gradlew :app:assembleXiaomiPadDisplayDebug :app:assembleTopazSingleDebug :app:assembleEmlL29SingleDebug :app:assembleOneplusSingleDebug.
2. From /mnt/c/Users/paul/projects/photo-finish-training/android run ./gradlew :app:testDebugUnitTest.
3. From /mnt/c/Users/paul/projects/photo-finish-training/android run ./gradlew :app:lintDebug.
4. Run npm run build:flavor:apks and npm run install:flavor:devices from repo root, verify Xiaomi device receives xiaomiPadDisplay APK.
5. Manual runtime check: Xiaomi auto-starts host/server role; clients connect; local start/stop/finish works regardless of sync state.

**Decisions**
- Static host only for TCP targeting.
- Local timing authority only; no cross-device clock synchronization dependency.
- Xiaomi Pad flavor is the required server/host build.
- Flavored APK scripts and install mapping are in-scope and must be updated with role-aware flavor outputs.

**Further Considerations**
1. If AUTO_START_ROLE uses display as the canonical host role in code, preserve compatibility while documenting display equals host/server.
2. If device inventory changes, keep flavor matrix and install script matcher list updated in the same change set.