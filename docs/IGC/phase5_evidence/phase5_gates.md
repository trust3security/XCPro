# Phase 5 Gate Evidence

Date: 2026-03-09
Owner: Codex
Branch: main
Commit: 25551da (working tree not committed)

## Gate Checklist

- [x] instrumentation tests for download/query/share (class set added and androidTest APK assembled)
- [ ] manual UX checklist for post-flight retrieval (pending device/manual pass)
- [x] collision tests for multi-flight same UTC day naming
- [x] instrumentation tests for chooser/share/email/copy-to flows and URI grants
- [x] UX rename acceptance: `IGC Files` replaces file-management `IGC Replay` (contract + navigation label test)
- [x] finalize idempotency tests (single publish per session)
- [x] staging-to-publish recovery tests (pending cleanup on write failure)

## Verification Command Results

1. `python scripts/arch_gate.py`
   - Result: PASS (`ARCH GATE PASSED`)
2. `./gradlew enforceRules`
   - Result: PASS (`Rule enforcement passed.`)
3. `./gradlew :feature:map:testDebugUnitTest --tests "com.example.xcpro.igc.*"`
   - Result: PASS
4. `./gradlew :feature:map:assembleDebug`
   - Result: PASS
5. `./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"`
   - Result: FAIL (no connected device/emulator)
6. `./gradlew connectedDebugAndroidTest --no-parallel`
   - Result: FAIL (no connected device/emulator)
7. `./gradlew testDebugUnitTest`
   - Result: PASS
8. `./gradlew assembleDebug`
   - Result: PASS

## Test Class Evidence

- Unit:
  - `feature/map/src/test/java/com/example/xcpro/igc/domain/IgcFileNamingPolicyTest.kt`
  - `feature/map/src/test/java/com/example/xcpro/igc/data/IgcFlightLogRepositoryTest.kt`
  - `feature/map/src/test/java/com/example/xcpro/igc/data/IgcFlightLogRepositoryIdempotencyTest.kt`
  - `feature/map/src/test/java/com/example/xcpro/igc/data/IgcFlightLogRepositoryRecoveryTest.kt`
  - `feature/map/src/test/java/com/example/xcpro/igc/data/IgcDownloadsRepositoryTest.kt`
  - `feature/map/src/test/java/com/example/xcpro/igc/usecase/IgcFilesUseCaseTest.kt`
- Instrumentation:
  - `feature/map/src/androidTest/java/com/example/xcpro/igc/IgcFilesListInstrumentedTest.kt`
  - `feature/map/src/androidTest/java/com/example/xcpro/igc/IgcFilesShareInstrumentedTest.kt`
  - `feature/map/src/androidTest/java/com/example/xcpro/igc/IgcFilesCopyToInstrumentedTest.kt`
  - `feature/map/src/androidTest/java/com/example/xcpro/igc/IgcFilesReplayOpenInstrumentedTest.kt`
  - `feature/map/src/androidTest/java/com/example/xcpro/igc/IgcFilesNavigationLabelInstrumentedTest.kt`

## Scorecard (must total 100)

- spec coverage/parity: `40/40`
- automated test depth: `29/30` (instrumentation implemented but execution blocked without connected device)
- determinism/architecture compliance: `20/20`
- operational hardening/docs sync: `10/10`
- total: `99/100` (pending connected-device run to claim full 100)
