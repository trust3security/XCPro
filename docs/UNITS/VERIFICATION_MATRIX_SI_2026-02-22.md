# Verification Matrix: SI Compliance

Date: 2026-02-22
Status: Required

## Required Repo Gates
- `./gradlew enforceRules`
- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`

## When Device/Emulator Available
- `./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"`

## Release/CI Verification
- `./gradlew connectedDebugAndroidTest --no-parallel`

## SI-Specific Verification

1. Unit Contract Tests
- Conversion in/out for km, NM, mi, km/h, kt, mph.
- Round-trip SI conversion tolerances.

2. Task/AAT/Racing Logic Tests
- Distance threshold behavior around boundaries.
- Area inclusion/exclusion tests using known points.
- Segment and total distance invariants with fixed fixtures.
- Explicit `AATTaskQuickValidationEngine` tests for both `validateStart` and `validateFinish` meter-threshold behavior.

3. Boundary Adapter Tests
- ADS-B bbox/radius boundary conversion checks.
- OGN subscription/filter conversion checks.
- Replay IAS/TAS km/h ingestion to m/s internal flow checks.
- Replay movement snapshot contract checks (`distanceMeters` is meters, not speed).

4. Static Sweep Checks
- Search for internal `distanceKm` and `kmh` usage in non-boundary domain paths.
- Search for meter thresholds compared against km return values.
- Search for meter-labeled fields assigned from speed (`*Meters = *Ms`) in replay/domain paths.
- Search for `AATMathUtils.calculateDistance(...)` values being compared directly against `lineLength`/`radius` without km->m conversion.

## Compliance Sign-Off Checklist
- No unresolved P0 risks.
- SI-only internal contracts documented and enforced.
- Tests passing at required levels.
- Final re-pass logged as Compliant.
