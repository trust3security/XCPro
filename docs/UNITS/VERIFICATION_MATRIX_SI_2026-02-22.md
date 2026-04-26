# Verification Matrix: SI Compliance

Date: 2026-02-22
Status: In progress (Run 46 closed `enforce_rules` caveat with 5 recursive passes; full matrix rerun still pending)

## Required Repo Gates
- `./gradlew enforceRules`
- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`

## When Device/Emulator Available
- `./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"`

## Release/CI Verification
- `./gradlew connectedDebugAndroidTest --no-parallel`

## Latest Execution Evidence (Run 24)
- `./gradlew --no-daemon --no-configuration-cache :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.adsb.OpenSkyProviderClientTest" --tests "com.trust3.xcpro.adsb.AdsbTrafficRepositoryTest" --tests "com.trust3.xcpro.ogn.OgnTrafficRepositoryPolicyTest" --tests "com.trust3.xcpro.ogn.OgnTrafficRepositoryConnectionTest" --tests "com.trust3.xcpro.replay.ReplaySampleEmitterTest"` -> PASS
- `./gradlew --no-daemon --no-configuration-cache enforceRules testDebugUnitTest assembleDebug` -> PASS
- Instrumentation evidence (unchanged from Run 10):
  - `./gradlew --no-daemon --no-configuration-cache :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"` -> PASS (9 tests)
  - `./gradlew --no-daemon --no-configuration-cache connectedDebugAndroidTest --no-parallel` -> NOT COMPLETED (user-aborted in Run 10)

## Run 8 Note (Deep Re-pass Audit)
- Run 8 was a static deep re-pass/documentation update pass (grep/code audit) with no production code edits.
- A newer executable verification matrix is captured under Run 10.
- When implementing Run 8 backlog items (`#29-#34`), rerun the full command matrix and append new evidence here.

## Run 9 Note (Deep Re-pass Audit)
- Run 9 was a static deep re-pass/documentation update pass (grep/code audit) with no production code edits.
- A newer executable verification matrix is captured under Run 10.
- When implementing newly added Run 9 backlog items (`#35-#39`) alongside `#29-#34`, rerun the full command matrix and append new evidence here.

## Run 10 Note (Implementation + Verification)
- Run 10 executed production SI cleanup in AAT/task contracts and reran required verification commands.
- JVM/build gates are green.
- App instrumentation initially failed, then passed after uninstall/reinstall remediation.
- Full release/CI instrumentation matrix (`connectedDebugAndroidTest`) remains pending because the run was user-aborted to save time.

## Run 11 Note (Polar SI Migration Verification)
- Run 11 executed `#17` polar SI storage migration and reran focused glider tests plus required JVM/build gates.
- New migration tests validate:
  - legacy km/h persistence compatibility-read,
  - SI-only canonical write contract,
  - SI parity in polar interpolation and IAS bounds resolution.
- Full instrumentation matrix remains pending from Run 10 user-aborted state.

## Run 24 Note (`#13` Boundary Adapter Verification)
- Run 24 implemented and verified backlog `#13` boundary adapter coverage across ADS-B/OGN/replay.
- New coverage includes:
  - ADS-B bbox clamp/propagation and OpenSky HTTP serialization/auth header boundary tests.
  - OGN login filter payload precision and reconnect center-refresh socket-harness tests.
  - Replay IAS/TAS conversion, non-finite reset behavior, and racing replay speed/quantization tests.

## Run 34-36 Note (`#34/#28` Triple Re-check)
- Runs 34-36 were static deep re-pass/documentation update passes (no production code edits, no verification commands executed).
- Residual closeout scope remains:
  - `#28`, `#34`, `#40`, `#41`, `#42`.
- New adjacent residual identified:
  - `#43` (`AATGeometryGenerator` unused km compatibility wrappers).
- When implementing these residual items, rerun the full command matrix and append new evidence here.

## Run 37 Note (`#34/#28` Focused Re-check)
- Run 37 was a focused static re-check/documentation update pass (no production code edits, no verification commands executed).
- Residual scope is unchanged from Run 36:
  - `#28`, `#34`, `#40`, `#41`, `#42`, `#43`.
- No additional residual item was discovered in Run 37.

## Run 38 Note (`#28/#34/#40/#41/#42/#43` Implementation Closeout)
- Run 38 implemented and verified closeout residuals:
  - `#28`, `#34`, `#40`, `#41`, `#42`, `#43`.
- Verification commands executed in this run:
  - `./gradlew :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.tasks.aat.AATTaskQuickValidationEngineUnitsTest" --no-daemon --no-configuration-cache` -> PASS
  - `./gradlew enforceRules --no-daemon --no-configuration-cache` -> PASS
  - `./gradlew testDebugUnitTest --no-daemon --no-configuration-cache` -> PASS
  - `./gradlew assembleDebug --no-daemon --no-configuration-cache` -> PASS
- Full release/CI instrumentation matrix remains pending (unchanged deferred state).

## Run 39-43 Note (`#18` Five-Pass Re-check)
- Runs 39-43 were static code/plan re-check passes for compatibility-wrapper cut item `#18`.
- No verification commands were executed in runs 39-43 (doc-only update cycle).
- Latest executable evidence remains Run 38 plus earlier required command history.

## Run 44 Note (`#18` Implementation Closeout Verification)
- Run 44 implemented and verified backlog `#18` changes.
- Focused verification command:
  - `./gradlew :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.tasks.TaskSheetViewModelImportTest" --tests "com.trust3.xcpro.tasks.AATCoordinatorDelegateTest" --tests "com.trust3.xcpro.tasks.core.TaskWaypointRadiusContractTest" --tests "com.trust3.xcpro.tasks.aat.AATInteractiveTurnpointManagerValidationTest" --tests "com.trust3.xcpro.tasks.aat.interaction.AATEditGeometryValidatorTest" --tests "com.trust3.xcpro.tasks.aat.areas.AreaCalculatorUnitsTest" --tests "com.trust3.xcpro.tasks.aat.AATTaskDisplayGeometryBuilderUnitsTest" --tests "com.trust3.xcpro.tasks.racing.RacingGeometryUtilsTest"` -> PASS
- Repo-gate status:
  - `./scripts/ci/enforce_rules.ps1` -> FAIL (pre-existing unrelated rule hit in `TaskManagerCompat.kt` and existing script no-files handling), so full-script green status is still pending outside this tranche.
- Targeted static checks for new `#18` guard patterns were executed with `rg` and returned no matches.

## Run 45 Note (`#12` Fixture-Matrix Closure Verification)
- Run 45 added and verified the remaining racing/AAT fixture-matrix distance invariants for backlog `#12`.
- Focused verification command:
  - `./gradlew :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.tasks.aat.calculations.AATDistanceCalculatorUnitsTest" --tests "com.trust3.xcpro.tasks.racing.RacingGeometryUtilsTest" --tests "com.trust3.xcpro.tasks.TaskManagerCoordinatorTest"` -> PASS
- Repo-gate status remains unchanged from Run 44 (`enforce_rules` still blocked by pre-existing unrelated issues).

## Run 46 Note (Remaining Non-`#12` Caveat Closure)
- Run 46 closed the static-rule caveat previously blocking `enforce_rules`:
  - removed false-positive rule hit for `TaskManagerCompat.kt` in composable boundary scan,
  - fixed ripgrep no-file abort behavior in `scripts/ci/enforce_rules.ps1`.
- Verification commands:
  - `./scripts/ci/enforce_rules.ps1` -> PASS.
  - recursive `./scripts/ci/enforce_rules.ps1` x5 -> PASS (`exit=0` all runs).
  - `./gradlew --no-configuration-cache enforceRules` -> PASS.
- Additional note:
  - `./gradlew enforceRules` (configuration cache enabled) still fails due existing configuration-cache policy issues in root build config (`where powershell` / `where pwsh`), unrelated to SI/rule regressions.

## SI-Specific Verification

1. Unit Contract Tests
- Conversion in/out for km, NM, mi, km/h, kt, mph.
- Round-trip SI conversion tolerances.
- UI boundary tests for distance output paths (distance circles + task distance labels) across `KILOMETERS`, `NAUTICAL_MILES`, and `STATUTE_MILES`.

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
- Search production UI for hard-coded distance-unit strings (`\" km\"`, `\" m\"`) that bypass `UnitsFormatter`/`UnitsPreferences`.

## Compliance Sign-Off Checklist
- No unresolved P0 risks.
- SI-only internal contracts documented and enforced.
- Tests passing at required levels.
- Final re-pass logged with current residual status and verification evidence.
