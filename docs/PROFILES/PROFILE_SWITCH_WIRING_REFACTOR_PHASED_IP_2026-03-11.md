# PROFILE_SWITCH_WIRING_REFACTOR_PHASED_IP_2026-03-11

## Purpose

Define a production-grade phased refactor plan to close profile switch-wiring
bugs and align profile import/export scope with runtime profile behavior.

This plan is focused on aircraft profile correctness first, then broader
portability coverage.

## 0) Metadata

- Title: Profile Switch Wiring and Scope Alignment Refactor
- Owner: XCPro Team
- Date: 2026-03-11
- Issue/PR: TBD
- Status: Draft

## 1) Scope

Problem statement:

1. Active profile switching is incomplete in map runtime wiring.
2. Some settings have profile-aware repositories but are not switched with
   profile changes.
3. Profile-scoped import filtering and delete cleanup do not include all
   intended aircraft settings.
4. Orientation snapshot payload is incomplete versus runtime settings model.

Why now:

1. Current behavior can silently persist/surface wrong aircraft settings.
2. This blocks reliable cross-device aircraft profile portability.

In scope:

1. Fix `setActiveProfileId` propagation in map runtime for profile-aware
   settings owners.
2. Expand profile-scoped snapshot/restore for immediate aircraft settings:
   - map style
   - snail trail
   - QNH manual preference
   - orientation extended fields
3. Align `PROFILE_SCOPED_SETTINGS` import filter and profile delete cleanup with
   the same settings set.
4. Add targeted tests and architecture-safe guard coverage.

Out of scope (this plan):

1. Raw waypoint and airspace file binaries in bundle JSON.
2. Full migration of waypoint and airspace manifest ownership to
   profile-scoped keys (planned separately in Phase 4 decision gate).
3. Replay pipeline behavior changes.

User-visible impact:

1. Switching aircraft profile will correctly switch map style/trail/QNH context.
2. Import/export with `Profile + aircraft settings` will restore a larger, more
   consistent aircraft settings set.
3. Orientation import/export will preserve all current General orientation
   settings fields.

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Profile list + active profile id | `ProfileStorage` + `ProfileRepository` | `StateFlow` in `ProfileRepository` | Any UI-side profile registry |
| Map style by profile | `MapStyleRepository` | repository methods + use case | ViewModel-local map style cache |
| Snail trail settings by profile | `MapTrailPreferences` | `settingsFlow` + use case methods | Duplicate trail profile maps in ViewModel |
| QNH manual by profile | `QnhPreferencesRepository` + `QnhRepositoryImpl` | `qnhState` / calibration state | Standalone QNH state stores outside repository |
| Orientation settings by profile | `MapOrientationSettingsRepository` | `settingsFlow` + profile read/write methods | Alternate orientation owner |
| Profile export snapshot sections | `AppProfileSettingsSnapshotProvider` | `ProfileSettingsSnapshot` | Runtime SSOT replacement JSON |
| Profile restore apply | `AppProfileSettingsRestoreApplier` | `ProfileSettingsRestoreResult` | UI-side settings mutation paths |
| Profile delete cleanup | `AppProfileScopedDataCleaner` | `clearProfileData(profileId)` | Ad-hoc per-feature cleanup in UI |

### 2.2 Dependency Direction

Must remain:

`UI -> domain/use case -> repository/data`

Modules/files touched (expected):

1. `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt`
2. `feature/map/src/main/java/com/example/xcpro/map/MapScreenUseCases.kt`
3. `app/src/main/java/com/example/xcpro/profiles/AppProfileSettingsSnapshotProvider.kt`
4. `app/src/main/java/com/example/xcpro/profiles/AppProfileSettingsRestoreApplier.kt`
5. `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileRepository.kt`
6. `app/src/main/java/com/example/xcpro/profiles/AppProfileScopedDataCleaner.kt`
7. related tests in `app/src/test/...` and `feature/map/src/test/...`

Boundary risk:

1. Avoid moving persistence/file logic into ViewModel.
2. Keep all profile scope writes at repository/adapters.

### 2.2A Boundary Moves

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Active profile propagation for map style/trail/QNH | Partial in `MapScreenViewModel` | Complete in `MapScreenViewModel` via existing use cases | Ensure profile switch is authoritative in map runtime | ViewModel profile-switch tests |
| Orientation extended field portability | Partial in snapshot/restore provider/applier | Full orientation payload in provider/applier | Prevent field loss on export/import | snapshot/restore round-trip tests |
| Scoped import/deletion parity | Partial in repository cleaner/filter | Expanded section/filter/cleaner parity | Prevent stale settings and partial scoped restores | import-scope + delete cleanup tests |

### 2.2B Bypass Removal Plan

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `MapScreenViewModel.setActiveProfileId` | Does not call map style/trail/QNH profile routing | Route through existing `MapStyleUseCase`, `MapTrailSettingsUseCase`, `QnhUseCase` | Phase 1 |
| `PROFILE_SCOPED_SETTINGS` filter | Omits intended section IDs | Expand filter set to explicit profile-scoped section contract | Phase 3 |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| Bundle export timestamp | Wall | metadata only |
| QNH captured-at metadata | Wall | persisted user calibration context |
| Runtime profile switching | n/a | state identity change, not time-driven |

Explicitly forbidden comparisons:

1. Monotonic vs wall
2. Replay vs wall

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - snapshot/restore I/O: `IO`
  - map runtime state fan-out: `Main` + existing coroutine scopes
- Primary cadence/gating sensor: n/a for profile switching
- Hot-path latency budget:
  - profile switch -> state hydration propagation should remain bounded by
    existing flow/update path (no blocking disk on main thread)

### 2.5 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence rules:
  - no replay logic changes in this plan
  - profile snapshot coverage changes must not touch replay clocks/pipeline

### 2.6 Enforcement Coverage

| Risk | Rule Reference | Guard Type | File/Test |
|---|---|---|---|
| Incomplete active profile propagation | ARCHITECTURE ViewModel + SSOT rules | unit test | `MapScreenViewModelCoreStateTest` additions |
| Snapshot/restore payload drift | SSOT + portability contract | unit test | `AppProfileSettingsSnapshotProviderTest`, restore tests |
| Scoped import mismatch | import contract | unit test | `ProfileRepositoryBundleTest` / import-scope tests |
| Delete cleanup gaps | SSOT ownership cleanup | unit test | profile delete cascade tests |
| Architecture layering drift | MVVM/UDF/SSOT | static + gradle gate | `./gradlew enforceRules` |

### 2.7 Visual UX SLO Contract

This refactor changes map runtime setting hydration, not gesture pipeline logic.
Impacted SLO validation should still capture no regression on affected map state
apply paths:

| Outcome | SLO ID | Baseline | Target | Validation/Test | Phase Gate |
|---|---|---|---|---|---|
| No added flicker/reorder churn during profile switch | MS-UX-04 | current baseline artifacts | no degradation | manual profile-switch validation + map runtime checks | Phase 4 |
| Overlay/apply runtime remains bounded after switch | MS-ENG-01 | current baseline artifacts | no degradation | targeted runtime profiling when map changes included | Phase 4 |
| No duplicate profile-switch sync loops | MS-ENG-06 | current baseline artifacts | no degradation | unit + runtime log verification | Phase 4 |

## 3) Data Flow (Before -> After)

Before:

`ProfileUiState.activeProfileId -> MapScreenViewModel.setActiveProfileId -> units/orientation/glider/variometer only`

After:

`ProfileUiState.activeProfileId -> MapScreenViewModel.setActiveProfileId -> map style + units + orientation + glider + variometer + trail + qnh`

Bundle scope before:

`ProfileRepository(PROFILE_SCOPED_SETTINGS) -> limited section filter`

Bundle scope after:

`ProfileRepository(PROFILE_SCOPED_SETTINGS) -> explicit full aircraft section filter aligned with snapshot/apply/cleaner`

## 4) Implementation Phases

### Phase 0 - Baseline and Contract Freeze

Goal:

1. Lock current failing behaviors with tests before refactor.
2. Freeze section ownership list for this plan.

Files:

1. test files under `feature/map/src/test/...`
2. test files under `app/src/test/...`
3. this plan document

Tests to add/update:

1. `MapScreenViewModel` profile switch test coverage for map style, QNH, trail.
2. Snapshot/restore tests for orientation extended fields.

Exit criteria:

1. Baseline tests fail for known missing behavior (or are TODO-marked with clear intent).
2. Contract is documented and agreed.

### Phase 1 - Switch Wiring Fix (Runtime Correctness)

Goal:

1. Make active profile switching propagate to all existing profile-aware map
   settings owners.

Files:

1. `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt`
2. `feature/map/src/main/java/com/example/xcpro/map/MapScreenUseCases.kt` (only if signatures/wrappers needed)

Tests:

1. Extend `MapScreenViewModelCoreStateTest` for:
   - map style profile routing
   - trail profile routing
   - QNH profile routing

Exit criteria:

1. Switching active profile updates all target owners.
2. No regressions in existing units/orientation/glider/variometer switch tests.

### Phase 2 - Snapshot/Restore Completeness

Goal:

1. Export/import includes immediate missing aircraft settings and complete
   orientation payload.

Files:

1. `app/src/main/java/com/example/xcpro/profiles/ProfileSettingsSectionSnapshots.kt`
2. `app/src/main/java/com/example/xcpro/profiles/AppProfileSettingsSnapshotProvider.kt`
3. `app/src/main/java/com/example/xcpro/profiles/AppProfileSettingsRestoreApplier.kt`
4. `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileSettingsSnapshot.kt` (if section IDs/policy alignment required)

Tests:

1. Snapshot provider includes map style/trail/QNH + full orientation fields.
2. Restore applier round-trip for new fields.
3. Legacy bundle compatibility tests still pass.

Exit criteria:

1. New snapshot sections are exported and restored correctly.
2. Orientation auto-reset and smoothing fields survive round-trip.

### Phase 3 - Scoped Import and Delete Cleanup Parity

Goal:

1. Keep import scope filter, restore applier, and delete cleanup aligned.

Files:

1. `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileRepository.kt`
2. `app/src/main/java/com/example/xcpro/profiles/AppProfileScopedDataCleaner.kt`

Tests:

1. `PROFILE_SCOPED_SETTINGS` imports include exactly intended aircraft sections.
2. Profile delete clears newly included scoped settings.

Exit criteria:

1. No stale profile-scoped records after delete.
2. Import-scope behavior matches documented section contract.

### Phase 4 - Waypoint/Airspace Decision Gate (Refactor Split)

Goal:

1. Decide whether waypoint/airspace manifest state remains global for now or is
   promoted to profile-scoped ownership.

Decision options:

1. Short-term production-safe: keep global, explicitly excluded from
   `PROFILE_SCOPED_SETTINGS`, document as intentional.
2. Full aircraft parity: migrate to profile-scoped manifest keys and add
   snapshot/restore/cleanup coverage.

Files (if option 2 selected):

1. `feature/map/src/main/java/com/example/xcpro/ConfigurationRepository.kt`
2. `feature/map/src/main/java/com/example/xcpro/flightdata/WaypointFilesRepository.kt`
3. `feature/map/src/main/java/com/example/xcpro/utils/AirspacePrefs.kt`
4. `feature/map/src/main/java/com/example/xcpro/utils/AirspaceIO.kt`
5. `feature/map/src/main/java/com/example/xcpro/utils/AirspaceRepository.kt`

Exit criteria:

1. Decision is explicit in docs and tests.
2. No ambiguous "profile-scoped by expectation but global in storage" behavior.

### Phase 5 - Production Verification and Rollout

Goal:

1. Validate architecture, regression safety, and release readiness.

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Targeted checks:

```bash
./gradlew :feature:map:testDebugUnitTest --tests "com.example.xcpro.map.MapScreenViewModelCoreStateTest"
./gradlew :app:testDebugUnitTest --tests "com.example.xcpro.profiles.*"
./gradlew :feature:profile:testDebugUnitTest
```

Optional when environment is available:

```bash
./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
```

Exit criteria:

1. All checks pass.
2. Manual profile switch and import/export smoke tests pass.
3. No architecture-rule drift.

## 5) Test Plan

Unit tests:

1. `MapScreenViewModelCoreStateTest` profile switch propagation coverage.
2. `AppProfileSettingsSnapshotProviderTest` section completeness and field-level
   assertions.
3. Restore applier tests for orientation extended fields + new sections.
4. `ProfileRepository` bundle import-scope tests.
5. Profile delete cascade tests for cleaner parity.

Replay/regression tests:

1. Confirm replay-related suites remain unchanged and passing.

UI/instrumentation tests (if relevant):

1. Profile switch smoke around map settings panel and quick actions.

Degraded/failure-mode tests:

1. Import with partial section failures reports exact failed section IDs.
2. Strict import mode fails when selected section restore fails.

Boundary tests for removed bypasses:

1. Verify profile switch updates map style/trail/QNH without direct repository
   calls from UI.

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Cross-profile bleed remains in one subsystem | Wrong aircraft setup at runtime | subsystem-by-subsystem switch tests | XCPro Team |
| Import scope includes wrong sections | Unexpected global setting overwrite | explicit section whitelist tests | XCPro Team |
| Delete cleanup overreaches | Deletes data that should remain global | cleaner tests + explicit section policy | XCPro Team |
| Orientation payload compatibility break | Older bundles fail or drop fields | backward-compatible parsing defaults | XCPro Team |
| Map runtime regressions from additional profile routing | UX jitter/flicker | SLO no-regression checks on profile switch flows | XCPro Team |

## 7) Acceptance Gates

1. No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`.
2. Profile switch wiring is complete for all in-scope profile-aware owners.
3. `PROFILE_SCOPED_SETTINGS` sections match snapshot/restore/cleanup contract.
4. Orientation export/import preserves all runtime fields.
5. Replay determinism is unaffected.
6. Required verification commands pass.

## 8) Rollback Plan

What can be reverted independently:

1. Phase 1 switch wiring changes (single ViewModel/use-case path).
2. Phase 2 snapshot/restore section additions.
3. Phase 3 scoped filter/cleaner alignment.

Recovery steps if regression is detected:

1. Revert latest phase only (keep prior passing phases).
2. Restore previous section filter while retaining runtime switch fix.
3. Re-run targeted tests + full required checks.

## Recommended Execution Order

1. Execute Phase 1 first. It fixes active runtime correctness immediately and
   reduces user-facing risk.
2. Execute Phase 2 and Phase 3 together in one PR if test coverage is complete.
3. Treat waypoint/airspace as a separate explicit decision (Phase 4), not an
   implicit side effect in switch-wiring work.
