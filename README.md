# Sprint Sync Android (Kotlin)

This repository contains the Android-native Kotlin/Compose app.

Cross-platform app code lives in a separate companion repository in this workspace.

## Build and Install (Windows)

1. `npm run build:debug:apk`
2. `npm run install:debug:devices`
3. `npm run run:debug:devices`

## Runtime Device Roles (Single APK)

| Device | Detection Rule | Role | TCP Host |
| --- | --- | --- | --- |
| Xiaomi Pad 7 | model `2410CRP4CG` + manufacturer `Xiaomi` | display / host | `192.168.0.103:9000` |
| OnePlus | model `CPH2399` + manufacturer `OnePlus` | controller / client | `192.168.0.103:9000` |
| Any other device | fallback | single-device default | `192.168.0.103:9000` |

All devices install the same debug package (`training.variant`). Runtime role is selected by `DeviceDetector`.

## Native Test Command

`cd android && gradlew.bat :app:testDebugUnitTest`

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
