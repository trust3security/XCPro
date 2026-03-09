# Phase 6 Gates

## Purpose

Gate evidence for the recovery release-grade workstream.

## Required Gates

### Architecture Gate

- Command: `python scripts/arch_gate.py`
- Result: PASS (`ARCH GATE PASSED`)

### Rule Enforcement

- Command: `./gradlew --no-daemon --no-configuration-cache "-Pksp.incremental=false" enforceRules`
- Result: PASS

### Unit Test Gate

- Command: `./gradlew --no-daemon --no-configuration-cache "-Pksp.incremental=false" testDebugUnitTest`
- Result: PASS

### Assemble Gate

- Command: `./gradlew --no-daemon --no-configuration-cache "-Pksp.incremental=false" assembleDebug`
- Result: PASS

## Recovery-Focused Gates

### Recovery Metadata and Repository Focus

- Command: `./gradlew --no-daemon --no-configuration-cache "-Pksp.incremental=false" :feature:igc:testDebugUnitTest --tests "com.example.xcpro.igc.data.IgcRecoveryMetadataStoreTest" --tests "com.example.xcpro.igc.data.IgcRecordingRuntimeActionSinkTest" --tests "com.example.xcpro.igc.data.IgcFlightLogRepositoryTest" --tests "com.example.xcpro.igc.data.IgcFlightLogRepositoryIdempotencyTest" --tests "com.example.xcpro.igc.data.IgcFlightLogRepositoryRecoveryTest"`
- Result: PASS

### Kill Matrix Focus

- Command: `./gradlew --no-daemon --no-configuration-cache "-Pksp.incremental=false" :feature:igc:testDebugUnitTest --tests "com.example.xcpro.igc.data.IgcFlightLogRepositoryRecoveryKillPointTest" :feature:map:testDebugUnitTest --tests "com.example.xcpro.igc.usecase.IgcRecoveryBootstrapUseCaseTest" --tests "com.example.xcpro.igc.usecase.IgcRecordingUseCaseTest"`
- Result: PASS

### Diagnostics Focus

- Command: `./gradlew --no-daemon --no-configuration-cache "-Pksp.incremental=false" :feature:map:testDebugUnitTest --tests "com.example.xcpro.igc.usecase.IgcRecoveryBootstrapUseCaseTest"`
- Result: PASS
- Command: `./gradlew --no-daemon --no-configuration-cache "-Pksp.incremental=false" :app:testDebugUnitTest --tests "com.example.xcpro.igc.AppIgcRecoveryDiagnosticsReporterTest"`
- Result: PASS

## Device Gate

- Discovery command: `adb devices -l`
- Effective target: `R5CT2084XHN` (`SM-S908E`)
- Note: a stale unauthorized emulator entry appeared during setup; the run was pinned to the physical device via `ANDROID_SERIAL` and was not blocked by that stale target.

- Command: `$env:ANDROID_SERIAL="R5CT2084XHN"; ./gradlew --no-daemon --no-configuration-cache "-Pksp.incremental=false" "-Pandroid.testInstrumentationRunnerArguments.class=com.example.xcpro.igc.IgcRecoveryRestartInstrumentedTest" :feature:igc:connectedDebugAndroidTest --no-parallel`
- Result: PASS

## Full Multi-Module Connected Gate

- Attempt 1:
  - Target: `R5CT2084XHN` (`SM-S908E`)
  - Command: `$env:ANDROID_SERIAL="R5CT2084XHN"; ./gradlew --no-daemon --no-configuration-cache "-Pksp.incremental=false" connectedDebugAndroidTest --no-parallel`
  - Result: transient FAIL after the device dropped off `adb`
- Fix applied:
  - moved share-helper instrumentation coverage from `feature:map` into the owning `feature:igc` module
  - new path: `feature/igc/src/androidTest/java/com/example/xcpro/screens/replay/IgcFilesShareIntentsInstrumentedTest.kt`
- Attempt 2:
  - Target: `emulator-5554` (`codex_recovery_api36`)
  - Command: `$env:ANDROID_SERIAL="emulator-5554"; ./gradlew --no-daemon --no-configuration-cache "-Pksp.incremental=false" connectedDebugAndroidTest --no-parallel`
  - Result: PASS

## Release Status

- Recovery slice gates: green
- Full repo connected suite: green
- Remaining non-gate issue: branch/worktree hygiene is still open
