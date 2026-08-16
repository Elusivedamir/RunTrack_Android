# RunTrack Android

RunTrack is a native Android fitness tracker implemented with Kotlin and Jetpack Compose.

## Current functional core

- Run / walk / bike workout lifecycle: start, pause, resume, finish, save and recovery after process loss.
- Foreground GPS tracking with monotonic-time duration accounting and route segmentation.
- Room persistence for workouts, route points, heart-rate samples and weather snapshots.
- Step metrics for supported devices, BLE Heart Rate Service support, Health Connect export.
- MapLibre + OpenStreetMap route display with a local Canvas fallback.
- Encrypted portable backup/restore and workout export.
- Offline Russian voice announcements generated during CI.

## Toolchain

- Kotlin 2.2.20
- Android Gradle Plugin 8.12.2
- Gradle 8.13 via the checked-in Gradle Wrapper
- Java 17
- compileSdk / targetSdk 36
- minSdk 26

## Build

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleRelease
```

Push builds on `main` produce a permanently signed release APK using GitHub Actions secrets and verify its certificate SHA-256; pull-request builds only assemble the unsigned release artifact.

## CI

`.github/workflows/build-apk.yml` runs on every push to `main` and checks Kotlin compilation, unit tests, Android instrumentation tests on an emulator, lint, debug/release APK assembly, offline voice assets and a runtime startup smoke test. Diagnostic logs and APK artifacts are uploaded for every run.

## Data safety

Workout completion is persisted transactionally. Recoverable in-progress sessions are not assumed to still be recording after process death and require an explicit user recovery decision. Android platform backup is disabled; portable backups are encrypted by the app.
