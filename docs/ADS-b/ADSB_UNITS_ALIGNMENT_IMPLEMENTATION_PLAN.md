# ADS-B Units Alignment Implementation Plan

## 0) Metadata

- Title: ADS-B Marker Details Sheet Unit Preference Alignment
- Owner: XCPro Team
- Date: 2026-02-12
- Issue/PR: TBD
- Status: Implemented

## 0C) Implementation Outcome (2026-02-12)

- Completed code changes:
  - Added ADS-B details vertical-rate formatter contract:
    - `feature/map/src/main/java/com/example/xcpro/adsb/AdsbDetailsFormatter.kt`
    - `feature/map/src/main/java/com/example/xcpro/adsb/AdsbMarkerDetailsSheet.kt`
  - Split ADS-B query center vs ownship-origin semantics:
    - `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficStore.kt`
    - `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficRepository.kt`
    - `feature/map/src/main/java/com/example/xcpro/map/MapScreenUseCases.kt`
    - `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt`
  - Center update cadence hardening (immediate first + sampled stream):
    - `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt`
- Completed tests:
  - `feature/map/src/test/java/com/example/xcpro/adsb/AdsbDetailsFormatterTest.kt`
  - `feature/map/src/test/java/com/example/xcpro/adsb/AdsbTrafficStoreTest.kt`
  - `feature/map/src/test/java/com/example/xcpro/adsb/AdsbTrafficRepositoryTest.kt`
- Behavior now guaranteed:
  - ADS-B details vertical rate shows `ft/min` and integer precision for FPM.
  - ADS-B radius filtering remains query-center-based.
  - ADS-B distance/bearing semantics are ownship-referenced when ownship GPS is available.
  - Cached targets are reselected immediately when center/origin changes (no wait for next poll).

## 0A) Recheck Findings (Missed Items)

- Missed item 1:
  - Core behavior is already implemented in production code:
    - `AdsbMarkerDetailsSheet` uses `UnitsFormatter` + `unitsPreferences` for altitude/speed/vertical-rate/distance.
    - Evidence: `feature/map/src/main/java/com/example/xcpro/adsb/AdsbMarkerDetailsSheet.kt:80`.
  - Implication: this is primarily a contract-verification + regression-hardening task, not a large runtime rewrite.
- Missed item 2:
  - There is no focused test that locks ADS-B details sheet unit output against `General -> Units`.
  - Implication: regressions can reappear silently.
- Missed item 3:
  - `docs/ADS-b/ADSB.md` contains a stale note claiming details labels still use `Type`/`Category`.
  - Implication: docs and implementation drift; update docs in same change.
- Missed item 4:
  - `MapUiState` starts with default `UnitsPreferences()` before DataStore emits persisted prefs.
  - Evidence: `feature/map/src/main/java/com/example/xcpro/map/MapScreenContract.kt:9`.
  - Implication: possible short cold-start unit flicker; treat as optional follow-up unless observed in QA.
- Missed item 5:
  - Vertical-speed unit text can be inconsistent with the user-visible setting label:
    - Setting shows `ft/min`
    - Formatter suffix currently resolves to `ft` for `FEET_PER_MINUTE`
  - Evidence:
    - `dfcards-library/src/main/java/com/example/xcpro/common/units/UnitsPreferences.kt:35`
    - `dfcards-library/src/main/java/com/example/xcpro/common/units/UnitsFormatter.kt:42`
    - `feature/map/src/main/java/com/example/xcpro/adsb/AdsbMarkerDetailsSheet.kt:85`
  - Implication: conversion is correct, but label contract can still fail for ADS-B `Vertical Rate`.
- Missed item 6:
  - ADS-B details sheet does not define explicit precision policy per unit for `Vertical Rate`.
  - Current path uses `UnitsFormatter.verticalSpeed` defaults (1 decimal), while other app surfaces already special-case FPM as integer.
  - Evidence:
    - `feature/map/src/main/java/com/example/xcpro/adsb/AdsbMarkerDetailsSheet.kt:85`
    - `dfcards-library/src/main/java/com/example/dfcards/CardFormatSpec.kt:501`
  - Implication: user can see rate text that is technically converted but presentation-inconsistent with selected FPM convention.
- Missed item 7:
  - ADS-B details `Distance`/`bearingDegFromUser` semantics can diverge from true user position.
  - Current computation uses ADS-B repository active center (camera/GPS-driven), not an explicit pilot-position source.
  - Evidence:
    - `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficStore.kt:48`
    - `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficRepository.kt:254`
    - `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt:463`
  - Implication: distance may be accurate in selected units but referenced to the wrong origin in some map states.
- Missed item 8:
  - Center updates can be starved by the current debounce strategy.
  - `debounceWithImmediateFirst(1_500ms)` emits first immediately, then waits for an idle gap; continuous location/camera updates can suppress later emissions.
  - Evidence:
    - `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt:464`
    - `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt:682`
  - Implication: ADS-B center (and therefore `Distance` context) can go stale between polls.
- Missed item 9:
  - A single repository `center` currently serves two different responsibilities:
    - ADS-B polling/query bbox center
    - Distance/bearing origin used in details/emergency calculations
  - Evidence:
    - `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficRepository.kt:174`
    - `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficRepository.kt:254`
  - Implication: if the app wants camera-driven query area but ownship-driven distance semantics, current model cannot satisfy both simultaneously.
- Missed item 10:
  - `updateCenter()` updates snapshot center fields but does not immediately reselect/recompute `targets` from store.
  - Evidence:
    - `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficRepository.kt:129`
    - `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficRepository.kt:275`
  - Implication: target list and `Distance` values can lag until next loop iteration/poll.
- Missed item 11:
  - Plan originally omitted explicit documentation-sync obligations if ADS-B center ownership/wiring changes.
  - Implication: architecture docs could drift from runtime behavior unless `PIPELINE.md` is updated in the same change set.

## 0B) Open Defects To Resolve

- D1 (High): Vertical-rate display contract gap for `FEET_PER_MINUTE` (`ft/min` suffix and integer precision).
- D2 (High): Distance-origin ambiguity (ownship vs active ADS-B center).
- D3 (High): Center refresh starvation under continuous update streams.
- D4 (Medium): `updateCenter()` does not immediately reselect targets/distances.

## 1) Scope

- Problem statement:
  - When a user taps an ADS-B aircraft on the map, the bottom sheet must display numeric values using the same unit preferences selected in `General -> Units`.
  - This behavior must be deterministic and regression-resistant, not implicit.
- Why now:
  - ADS-B marker detail display is user-visible and safety-adjacent; inconsistent units are high-risk UX debt.
- In scope:
  - ADS-B marker details rows that depend on unit preferences:
    - Altitude
    - Speed
    - Vertical Rate
    - Distance
  - Vertical-rate unit suffix correctness (`ft/min` contract when selected).
  - Vertical-rate precision contract in ADS-B sheet (integer for FPM unless explicitly changed).
  - Unit formatting contract hardening (test coverage + explicit mapping path).
  - Distance-origin contract validation (must be explicit whether value is from user aircraft or active ADS-B center).
  - Center ownership split/strategy decision if distance and query-center requirements conflict.
  - Documentation update in ADS-B docs for the units contract.
- Out of scope:
  - ADS-B polling cadence, radius, filtering, target cap.
  - ADS-B icon artwork/size settings.
  - OGN rendering behavior.
  - Track angle and age formatting (not part of General Units preferences today).
- User-visible impact:
  - ADS-B details sheet consistently matches `General -> Units` choices.
  - Unit changes are reflected without app restart.

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| User unit preferences | `UnitsRepository` | `Flow<UnitsPreferences>` | UI-local preferences as authority |
| ADS-B live targets | `AdsbTrafficRepository` | `StateFlow<List<AdsbTrafficUiModel>>` | ViewModel/UI caches as authority |
| Selected ADS-B target id | `MapScreenViewModel` | `StateFlow<Icao24?>` | Overlay/runtime duplicate selection owner |
| Selected ADS-B details | `AdsbMetadataEnrichmentUseCase` + `MapScreenViewModel` | `StateFlow<AdsbSelectedTargetDetails?>` | UI-maintained duplicate details model |
| ADS-B details formatted strings (view concern) | ADS-B details formatter/presentation mapper (UI layer) | pure mapping function output | Ad-hoc per-row formatting logic scattered in Composable |

### 2.2 Dependency Direction

Required flow remains:

`UI -> domain/use-case -> data`

- Modules/files touched:
  - `feature/map/src/main/java/com/example/xcpro/adsb/AdsbMarkerDetailsSheet.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt` (only if a real propagation bug is found)
  - ADS-B and map unit tests
  - `docs/ADS-b/ADSB.md`
- Boundary risk:
  - Avoid pushing business logic into UI; only display formatting may live in UI/presentation helper.
  - Keep repositories/use-cases unchanged unless a strict bug requires it.

### 2.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| ADS-B detail row unit conversion/label assembly | `AdsbMarkerDetailsSheet` | `AdsbMarkerDetailsSheet` (unchanged for MVP) | Existing path is already layer-correct and unit-aware | Tests verify contract without architectural churn |

### 2.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| None required for MVP | N/A | N/A | N/A |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| ADS-B target age (`ageSec`) | Monotonic-derived (repository output) | Live freshness/staleness semantics |
| Metadata sync status timestamp (`lastSuccessWallMs`) | Wall | Human-readable sync info only |
| Unit preferences | N/A | Persistent settings, not time-derived |

Explicitly forbidden comparisons:

- Monotonic vs wall
- Replay vs wall

No new timebase arithmetic is introduced by this change.

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - No new dispatcher paths expected.
  - Formatting remains lightweight UI/presentation work.
- Primary cadence/gating source:
  - Existing flows (`unitsFlow`, `selectedAdsbTarget`) drive recomposition.
- Hot-path latency budget:
  - No measurable runtime impact; mapping is O(1) per selected target update.

### 2.5 Replay Determinism

- Deterministic for same input: Yes.
- Randomness used: No.
- Replay/live divergence rules:
  - No replay-path changes.
  - ADS-B is live-network data; this plan only affects presentation formatting.

### 2.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| UI unit regression to fixed SI text | CODING_RULES UI/state consistency | unit test | `feature/map/src/test/java/com/example/xcpro/adsb/AdsbMarkerDetailsSheetTest.kt` |
| Missing live update when preferences change | MVVM/UDF state propagation | unit test and/or Compose UI test | `feature/map/src/test/java/com/example/xcpro/adsb/AdsbMarkerDetailsSheetTest.kt` |
| Vertical-rate label/precision drift for FPM | UX contract consistency | unit test | `feature/map/src/test/java/com/example/xcpro/adsb/AdsbMarkerDetailsSheetTest.kt` |
| Distance shown with wrong origin semantics | Contract correctness | review + unit test | repository/store selection tests + docs contract |
| Center-update starvation from debounce logic | UX/data freshness | unit test | `MapScreenViewModel` flow timing test |
| Shared-center coupling blocks both query-center and ownship-distance correctness | Data-model contract | review + unit/integration tests | `AdsbTrafficRepository*Test` + docs |
| Center update does not trigger immediate distance recompute | UX freshness | unit/integration test | `AdsbTrafficRepository*Test` |
| Accidental architecture drift | ARCHITECTURE/CODING_RULES | `enforceRules` + review | `./gradlew enforceRules` |

## 3) Data Flow (Before -> After)

Before:

```
General Units UI
  -> UnitsSettingsViewModel
  -> UnitsSettingsUseCase
  -> UnitsRepository (DataStore SSOT)
  -> UnitsPreferencesUseCase.unitsFlow
  -> MapScreenViewModel.observeUnits
  -> MapUiState.unitsPreferences
  -> MapScreenContent
  -> AdsbMarkerDetailsSheet (inline row formatting)
```

After:

```
General Units UI
  -> UnitsRepository (SSOT)
  -> UnitsPreferencesUseCase.unitsFlow
  -> MapScreenViewModel.observeUnits
  -> MapUiState.unitsPreferences
  -> MapScreenContent
  -> AdsbMarkerDetailsSheet (unchanged formatting path, test-locked)
```

Notes:
- No owner changes for ADS-B target data or settings persistence.
- Primary path is contract hardening plus tests/docs; center/selection fixes are applied only if failing tests prove defects.

## 4) Implementation Phases

### Phase 1: Contract Baseline and Gap Lock

- Goal:
  - Confirm exact ADS-B detail fields that must follow General Units.
  - Freeze acceptance rows: altitude/speed/vertical rate/distance.
- Files to change:
  - `docs/ADS-b/ADSB.md`
  - `docs/ADS-b/ADSB_UNITS_ALIGNMENT_IMPLEMENTATION_PLAN.md`
  - `docs/ARCHITECTURE/PIPELINE.md` (mandatory if center/data-flow wiring changes)
- Tests to add/update:
  - None in this phase.
- Exit criteria:
  - Contract is explicit in docs and aligned with AGENTS architecture rules.

### Phase 2: Regression Lock

- Goal:
  - Add focused regression tests first; keep production path unchanged unless a failing test proves a defect.
- Files to change:
  - New: `feature/map/src/test/java/com/example/xcpro/adsb/AdsbMarkerDetailsSheetTest.kt`
- Optional files to change (only if tests expose defect):
  - `feature/map/src/main/java/com/example/xcpro/adsb/AdsbMarkerDetailsSheet.kt`
  - `feature/map/src/main/java/com/example/xcpro/adsb/AdsbDetailsFormatter.kt` (ADS-B-local presentation policy)
  - `dfcards-library/src/main/java/com/example/xcpro/common/units/UnitsPreferences.kt` (only if global unit-label fix is selected)
- Tests to add/update:
  - Add sheet-focused tests for metric and non-metric unit permutations.
- Exit criteria:
  - Tests prove sheet values follow `UnitsPreferences`.

### Phase 3: Regression Tests

- Goal:
  - Fix only defects proven by Phase 2 tests (if any), then extend regression coverage.
- Files to change:
  - Update: `feature/map/src/test/java/com/example/xcpro/adsb/AdsbMarkerDetailsSheetTest.kt`
  - Optional update: `feature/map/src/test/java/com/example/xcpro/map/MapScreenViewModelTest.kt` for units/cadence assertions.
  - Optional update: `feature/map/src/test/java/com/example/xcpro/adsb/AdsbTrafficRepositoryTest.kt`
  - Optional production updates if tests fail:
    - `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt`
    - `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficRepository.kt`
    - `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficStore.kt`
- Tests to add/update:
  - Metric baseline assertions.
  - Feet/knots/fpm/NM assertions.
  - Explicit assertion that `Vertical Rate` suffix matches `ft/min` when `FEET_PER_MINUTE` is selected.
  - Explicit assertion that `Vertical Rate` uses integer precision for `FEET_PER_MINUTE`.
  - Placeholder behavior for null source values.
  - Preference-change recomposition behavior (if covered with Compose test).
  - Center-update cadence test to verify center still refreshes under continuous location updates.
  - Distance-origin semantics tests (user-position contract vs active-center contract, whichever is approved).
  - Repository test: `updateCenter()` should (or should not) reselect immediately, according to approved contract.
  - Repository/model test: query-center and distance-origin coupling behavior is explicit and deterministic.
- Exit criteria:
  - New tests pass locally and fail when mapper contract is broken.

### Phase 4: Verification and Release Gate

- Goal:
  - Run required checks and manual QA matrix for ADS-B details units.
- Files to change:
  - No production code expected.
- Tests to add/update:
  - None.
- Exit criteria:
  - Required commands pass and QA matrix is signed off.

## 5) Test Plan

- Unit tests:
  - ADS-B details sheet contract tests (metric + imperial/custom sets).
  - Vertical-rate unit-suffix contract test (`ft/min`).
  - Vertical-rate FPM precision contract test (no decimal fraction).
  - Null/placeholder handling.
  - Continuous-update cadence test for ADS-B center updates.
  - Distance-origin semantics tests.
  - `updateCenter()` reselection behavior test.
  - Shared-center contract test (query-center vs distance-origin).
- Replay/regression tests:
  - Not required for ADS-B live data logic (no replay path change).
- UI/instrumentation tests (if needed):
  - Compose unit test for sheet text update when `unitsPreferences` changes.
- Degraded/failure-mode tests:
  - Missing speed/altitude/climb values show `--` and do not crash.
- Boundary tests for removed bypasses:
  - Not required in MVP (no bypass removal planned).

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Optional when relevant:

```bash
./gradlew connectedDebugAndroidTest
```

Manual QA matrix (minimum):
- Open ADS-B details with Units = metric and verify altitude/speed/vertical rate/distance labels and magnitudes.
- Change Units to imperial/mixed, return to map, retap target, verify conversions.
- While details sheet open, change units and confirm displayed text updates on return/recompose path.
- Pan map away from user/aircraft and verify distance-origin behavior matches the documented contract.
- Keep GPS updating continuously for >30s and verify ADS-B center continues refreshing (no starvation).
- Move ownship while ADS-B targets remain visible and verify `Distance` updates without waiting a full poll interval.

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Existing behavior already mostly correct; unnecessary refactor could introduce regressions | Medium | Keep scope minimal, tests/docs first, code change only on failing proof | XCPro Team |
| Formatting differences (decimals/sign) create UI churn | Low | Preserve current `UnitsFormatter` defaults and only centralize usage | XCPro Team |
| Global unit-label correction (`ft` -> `ft/min`) could affect compact card layouts | Medium | Prefer ADS-B-local label fix first, or run targeted UI regression if model-level fix is chosen | XCPro Team |
| FPM precision correction in ADS-B could diverge from existing sheet snapshots/tests | Low | Lock expected precision in tests and update snapshot baselines once | XCPro Team |
| Distance source ambiguity (user vs ADS-B center) causes pilot confusion | High | Define contract explicitly and enforce in tests/docs | XCPro Team |
| Debounce starvation keeps center stale under continuous updates | High | Replace with sampling/throttle strategy validated by timing tests | XCPro Team |
| Single-center coupling causes correctness tradeoff (query area vs ownship semantics) | High | Split center responsibilities or explicitly lock one behavior and document it | XCPro Team |
| Delayed reselection after center change causes stale map/details values | Medium | Recompute from store on center update or prove acceptable via contract | XCPro Team |
| Overreach into ADS-B runtime/pipeline code | Medium | Explicit out-of-scope lock; no repository polling or overlay logic changes | XCPro Team |

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`.
- No duplicate SSOT ownership introduced.
- Unit-dependent ADS-B detail rows match `General -> Units` preferences.
- Distance-origin semantics are explicitly documented and verified.
- ADS-B center-update cadence remains fresh under continuous updates.
- `docs/ARCHITECTURE/PIPELINE.md` updated in same PR if center/data-flow wiring is changed.
- Existing ADS-B tap-selection and metadata detail behavior remains intact.
- `KNOWN_DEVIATIONS.md` unchanged unless explicitly approved.

## 8) Rollback Plan

- What can be reverted independently:
  - ADS-B details sheet test additions.
  - Added ADS-B details tests.
  - ADS-B docs updates.
- Recovery steps if regression is detected:
  1. Revert the latest ADS-B details code commit (if any).
  2. Keep tests/docs and fix forward with minimal patch.
