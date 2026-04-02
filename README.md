# Sprint Sync Android (Kotlin)

This repository contains the Android-native Kotlin/Compose app.

Cross-platform app code lives in a separate companion repository in this workspace.

## Build and Install (Windows)

1. `npm run build:debug:apk`
2. `npm run install:debug:devices`
3. `npm run run:debug:devices`

## Flavor Matrix

| Device | Flavor | Role | TCP Host |
| --- | --- | --- | --- |
| Xiaomi Pad 7 | `xiaomiPadDisplay` | display / host | `192.168.0.103:9000` |
| Pixel 7 | `pixel7Single` | single / client | `192.168.0.103:9000` |
| OnePlus | `oneplusSingle` | controller / client | `192.168.0.103:9000` |
| Xiaomi Topaz | `topazSingle` | single / client | `192.168.0.103:9000` |
| Huawei EML-L29 | `emlL29Single` | single / client | `192.168.0.103:9000` |

Use `npm run build:flavor:apks` and `npm run install:flavor:devices` to install the role-specific debug APKs across attached devices.

## Native Test Command

`cd android && gradlew.bat :app:testXiaomiPadDisplayDebugUnitTest`

## Android Quality Commands

From `android/`:

- `./gradlew detekt`
- `./gradlew ktlintCheck`
- `./gradlew ktlintFormat`
- `./gradlew jacocoTestReport`
- `./gradlew dependencyUpdates`

## Notes

- This repository is Android-only.
- Device details are tracked in `device_specs.md`.
