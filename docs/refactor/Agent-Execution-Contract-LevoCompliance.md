> NOTICE (2026-02-06): Task refactor plan is documented in $plan. Review before implementing task-related changes.

# Agent-Execution-Contract-LevoCompliance.md

Status: Completed (2026-01-28)

Goal: Bring compliance scorecard to 5/5 in all categories using small,
reviewable steps. Each item should be checked off when complete and the
matching entry in KNOWN_DEVIATIONS.md should be updated or removed.

## Phase 1: Timebase (highest impact)
- [x] Add Clock interface with nowMonoMs() and nowWallMs().
- [x] Add Hilt binding for Clock and a FakeClock for tests.
- [x] Inject Clock into fusion pipeline constructors.
- [x] Replace System.currentTimeMillis/SystemClock usage in domain and fusion
      with injected Clock or explicit timestamps.
- [x] Update tests to use FakeClock; add determinism/timebase test.
- [x] Close deviation #1 in KNOWN_DEVIATIONS.md.

## Phase 2A: Dependency Injection
- [x] Provide SensorFusionRepository via Hilt (no manual construction).
- [x] Refactor VarioServiceManager to use injected SensorFusionRepository.
- [x] Add a small test/fake binding for manager construction.
- [x] Close deviation #2.

## Phase 2B: ViewModel purity
- [x] Move SharedPreferences access to a repository/use-case.
- [x] Remove Compose UI types from ViewModels (use simple data classes).
- [x] Ensure ViewModels depend on use-cases only.
- [x] Update ViewModel tests to use fakes.
- [x] Close deviation #3.

## Phase 3A: Lifecycle collection
- [x] Replace collectAsState() with collectAsStateWithLifecycle() in UI.
- [x] Ensure non-Compose collection uses repeatOnLifecycle or equivalent.
- [x] Close deviation #4.

## Phase 3B: Vendor neutrality and ASCII
- [x] Remove vendor strings (xcsoar/XCSoar) from production Kotlin source.
- [x] Replace non-ASCII characters in production Kotlin source.
- [x] Close deviations #5 and #6.

## Maintenance
- [x] Add owner and expiry dates to all remaining deviations (none open as of 2026-01-28).
- [x] Run preflight.bat before major refactors (last run 2026-01-28).

## Verification (2026-01-28)
- PASS: ./gradlew testDebugUnitTest (re-run after MapOverlayWidgetGesturesTest moved to Robolectric)
- PASS: ./gradlew enforceRules
- PASS: ./gradlew lintDebug
- PASS: ./gradlew assembleDebug
- PASS: powershell -File scripts/ci/enforce_rules.ps1
- PASS: preflight.bat

