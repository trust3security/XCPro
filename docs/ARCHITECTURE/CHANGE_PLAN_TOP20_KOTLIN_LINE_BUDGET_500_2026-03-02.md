# CHANGE_PLAN_TOP20_KOTLIN_LINE_BUDGET_500_2026-03-02.md

## 0) Metadata

- Title: Top-20 Kotlin Hotspot Refactor and File Line-Budget Compliance (`<= 500`)
- Owner: XCPro Team
- Date: 2026-03-02
- Issue/PR: RULES-20260302-LINEBUDGET500
- Status: Draft

## 1) Scope

- Problem statement:
  - The largest Kotlin files are concentrated in map, ADS-B, OGN, forecast, replay, and test slices.
  - File size currently impairs maintainability, review quality, and change safety.
  - A hard file budget requirement is now requested: each target file must be `<= 500` lines.
- Why now:
  - Current top hotspots include multiple files above 800-2400 lines.
  - Large files increase architecture drift risk and slow deterministic regression verification.
- In scope:
  - The current top 20 largest `.kt` files in this repo snapshot (listed below).
  - Policy and guardrail updates required to make `<= 500` enforceable.
- Out of scope:
  - Feature behavior changes not required by refactor boundaries.
  - Product-scope UI redesign.
- User-visible impact:
  - None intended for behavior.
  - Minor runtime risk during migration; mitigated by phased tests and hard gates.

## 1A) Baseline Target Files

| # | File | Current Lines | Required Target |
|---|---|---:|---:|
| 1 | `feature/map/src/test/java/com/example/xcpro/adsb/AdsbTrafficRepositoryTest.kt` | 2478 | <= 500 |
| 2 | `feature/map/src/test/java/com/example/xcpro/map/MapScreenViewModelTest.kt` | 1203 | <= 500 |
| 3 | `feature/map/src/main/java/com/example/xcpro/ogn/OgnTrafficRepository.kt` | 1123 | <= 500 |
| 4 | `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManager.kt` | 1026 | <= 500 |
| 5 | `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficRepository.kt` | 1008 | <= 500 |
| 6 | `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContent.kt` | 860 | <= 500 |
| 7 | `feature/map/src/test/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCaseTest.kt` | 938 | <= 500 |
| 8 | `feature/map/src/test/java/com/example/xcpro/ogn/OgnThermalRepositoryTest.kt` | 844 | <= 500 |
| 9 | `feature/map/src/main/java/com/example/xcpro/map/ForecastRasterOverlay.kt` | 828 | <= 500 |
| 10 | `feature/map/src/main/java/com/example/xcpro/ogn/OgnThermalRepository.kt` | 760 | <= 500 |
| 11 | `feature/map/src/main/java/com/example/xcpro/replay/IgcReplayController.kt` | 692 | <= 500 |
| 12 | `feature/map/src/test/java/com/example/xcpro/forecast/ForecastOverlayRepositoryTest.kt` | 725 | <= 500 |
| 13 | `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/Settings-df.kt` | 716 | <= 500 |
| 14 | `feature/map/src/main/java/com/example/xcpro/forecast/ForecastOverlayRepository.kt` | 600 | <= 500 |
| 15 | `feature/map/src/main/java/com/example/xcpro/map/ui/ForecastOverlayBottomSheet.kt` | 626 | <= 500 |
| 16 | `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/WeatherSettingsScreen.kt` | 591 | <= 500 |
| 17 | `feature/map/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt` | 536 | <= 500 |
| 18 | `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/AdsbSettingsScreen.kt` | 460 | keep <= 500 |
| 19 | `dfcards-library/src/main/java/com/example/dfcards/dfcards/CardPreferences.kt` | 434 | keep <= 500 |
| 20 | `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/HawkVarioSettingsScreen.kt` | 563 | <= 500 |

## 1B) Recurring Repass x5 (Completed)

This plan is based on 5 recurring audit passes over the same 20-file set.

- Repass 1 (structural decomposition):
  - Collected per-file lines, function counts, and minimum split chunks (`ceil(lines/450)`).
  - Hotspots:
    - `AdsbTrafficRepositoryTest.kt`: 2478 lines, 55 functions, minimum 6 chunks.
    - `OgnTrafficRepository.kt`: 1123 lines, 46 functions.
    - `MapOverlayManager.kt`: 1026 lines, 51 functions.
    - `AdsbTrafficRepository.kt`: 1008 lines, 41 functions.
- Repass 2 (architecture and rules):
  - Ran `powershell -ExecutionPolicy Bypass -File scripts/ci/enforce_rules.ps1`.
  - Result: `Rule enforcement passed.` in current baseline.
  - Conclusion: this file-budget initiative must extend current guardrails, not replace them.
- Repass 3 (timebase/determinism/concurrency):
  - Counted `nowMonoMs`, `nowWallMs`, loop/coroutine hotspots.
  - Highest determinism-sensitive files:
    - `OgnTrafficRepository.kt`
    - `AdsbTrafficRepository.kt`
    - `OgnThermalRepository.kt`
    - `IgcReplayController.kt`
- Repass 4 (test clustering and fixture strategy):
  - `AdsbTrafficRepositoryTest.kt`: 48 test methods, 7 helpers, clear `emergencyAudio` cluster (8 tests).
  - `MapScreenViewModelTest.kt`: 35 tests, 11 helpers, clustered by ADS-B/OGN/settings/state.
  - `CalculateFlightMetricsUseCaseTest.kt`: strong `te`, `wind`, and `tc30s` clusters.
  - `OgnThermalRepositoryTest.kt` and `ForecastOverlayRepositoryTest.kt` are scenario-cluster friendly for split.
- Repass 5 (slice/risk sequencing):
  - Critical-first execution required for:
    - `OgnTrafficRepository.kt`
    - `AdsbTrafficRepository.kt`
    - `OgnThermalRepository.kt`
  - UI and test files follow after runtime/data stabilization.
  - Two files already compliant remain in hold mode:
    - `AdsbSettingsScreen.kt`
    - `CardPreferences.kt`

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| ADS-B traffic state/snapshot | `AdsbTrafficRepository` | `StateFlow<AdsbTrafficSnapshot>` | UI/runtime mirror as authority |
| OGN traffic state/snapshot | `OgnTrafficRepository` | `StateFlow<OgnTrafficSnapshot>` | Overlay-owned target cache authority |
| OGN thermal hotspots | `OgnThermalRepository` | `StateFlow<List<OgnThermalHotspot>>` | UI-calculated hotspot lists |
| Forecast overlay state | `ForecastOverlayRepository` | `Flow<ForecastOverlayUiState>` | ViewModel-owned forecast truth |
| Replay session state | `IgcReplayController` | State/event flows | UI-owned replay state machine |
| UI settings state | corresponding ViewModels + prefs repos | `StateFlow` | duplicated mutable local state |

### 2.2 Dependency Direction

Required flow remains:

`UI -> domain -> data`

- Modules/files touched:
  - `feature/map` (adsb, ogn, map, replay, forecast, screens, tests)
  - `dfcards-library`
  - `docs/ARCHITECTURE`
  - `scripts/ci`
- Boundary risk:
  - High in runtime map classes and repositories if extraction accidentally leaks MapLibre/UI types or moves business policy to UI helpers.

### 2.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| OGN connection loop internals | `OgnTrafficRepository.kt` monolith | dedicated loop collaborator | reduce file size without SSOT change | OGN repository tests |
| ADS-B polling/retry orchestration internals | `AdsbTrafficRepository.kt` monolith | polling + network collaborators | isolate policy and improve testability | ADS-B repository tests |
| Overlay per-domain runtime apply logic | `MapOverlayManager.kt` monolith | runtime controllers per overlay domain | preserve single owner while reducing complexity | map runtime tests/manual |
| Forecast layer rendering branches | `ForecastRasterOverlay.kt` monolith | wind arrow/barb/expression helpers | isolate map style branches | forecast overlay tests |
| Replay session sequencing internals | `IgcReplayController.kt` monolith | replay session runner collaborators | deterministic replay safety | replay tests |

### 2.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| large UI composable files with mixed concerns | local helper sprawl in same file | split into UI-only section files with stable inputs | Phase 3 |
| repository files with parsing/policy/storage mixed | hidden intra-file coupling | explicit injected helpers inside same layer | Phase 2 |
| mega test classes with all scenarios | fixture drift and setup duplication | scenario-focused test files + shared fixtures | Phase 4 |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| ADS-B/O GN freshness windows | Monotonic | live validity windows and anti-jump behavior |
| Replay progress/session clock | Replay | deterministic IGC playback |
| Forecast auto-time selection | Wall | user-facing wall-time alignment |
| Thermal retention | Wall (retention), Monotonic (freshness) | policy already split by semantics |

Explicitly forbidden comparisons:

- Monotonic vs wall
- Replay vs wall

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - UI rendering/composable wiring: `Main`
  - domain policy/math: `Default`
  - network/file/replay I/O: `IO`
- Primary cadence/gating sensor:
  - unchanged from current pipeline (baro cadence for high-rate loop; replay cadence from emitter).
- Hot-path latency budget:
  - no regression vs current behavior; preserve existing audio/overlay timing contracts.

### 2.5 Replay Determinism

- Deterministic for same input: Yes (must remain yes).
- Randomness used: unchanged; any replay noise remains explicitly configured/seeded as currently implemented.
- Replay/live divergence rules:
  - unchanged; replay keeps IGC-time contract and no live wall-clock mixing.

### 2.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| file-size drift reintroduced | new line-budget rule | `enforceRules` static gate | `scripts/ci/enforce_rules.ps1` |
| rule not documented | architecture policy docs | docs update in same PR | `ARCHITECTURE.md`, `CODING_RULES.md` |
| split introduces boundary leaks | MVVM/UDF/SSOT rules | compile + unit + enforceRules | relevant module tests |
| replay logic regression | replay/timebase rules | deterministic replay tests | replay test slice |
| hidden behavior changes | change safety rules | scenario-preserving tests | per-file test plan below |

## 3) Data Flow (Before -> After)

Before:

`Monolithic file per domain area -> mixed concerns -> harder review and drift risk`

After:

`Facade/entry file (<=500) -> focused collaborators in same architecture layer -> unchanged SSOT/use-case/viewmodel/UI flow`

No ownership changes are permitted for authoritative state.

## 4) Implementation Phases

### Phase 0 - Policy and Gate Setup

- Goal:
  - Make `<= 500` a documented and enforced compliance rule.
- Files to change:
  - `docs/ARCHITECTURE/CODING_RULES.md`
  - `docs/ARCHITECTURE/ARCHITECTURE.md`
  - `scripts/ci/enforce_rules.ps1`
  - optional: `docs/ARCHITECTURE/PIPELINE.md` only if wiring changes
- Tests to add/update:
  - none; static gate updates only
- Exit criteria:
  - rule text present in docs
  - `enforceRules` fails when any target file exceeds 500
- Repass-miss updates (must close in this phase):
  - Missed: canonical docs still do not explicitly codify the new `<= 500` file rule.
  - Missed: `scripts/ci/enforce_rules.ps1` currently enforces selected per-file budgets but not this top-20 program as a single tracked scope.
  - Missed (comprehensive pass): no current `enforce_rules.ps1` line-budget coverage for these in-scope files:
    - `MapScreenContent.kt`
    - `Settings-df.kt`
    - `WeatherSettingsScreen.kt`
    - `HawkVarioSettingsScreen.kt`
    - `AdsbSettingsScreen.kt`
    - `CardPreferences.kt`
  - Correction: add explicit architecture/coding policy text + targeted top-20 guard entries in the same PR slice.
  - Correction: choose one enforcement model and implement it fully:
    - explicit `Assert-MaxLines` entries for all top-20 files, or
    - generic scoped scanner with allowlist + max thresholds.
  - Correction evidence: include exact file/line references for new policy text and rule checks.

#### Phase 0 Quality Scorecard (Must Be >=94/100)

| Dimension | Target |
|---|---:|
| Policy clarity in canonical docs | >= 94 |
| Gate enforceability (rule fails as designed) | >= 94 |
| Scope coverage for top-20 target set | >= 94 |
| Verification evidence completeness | >= 94 |
| **Overall weighted score** | **>= 94** |

### Phase 1 - Repository/Domain Hotspot Decomposition

- Production-grade target rating: **>=94/100**
  - Release rule: Phase 1 is not complete unless measured score is `>=94/100` on the scorecard below.
- Goal:
  - split high-risk production monoliths while preserving behavior, SSOT ownership, determinism, and dependency direction.
- Production-grade definition for this phase:
  - No behavior regressions in repository/domain/replay runtime.
  - No architecture drift (MVVM + UDF + SSOT + DI + timebase rules intact).
  - All modified files reach line-budget target while preserving public contracts.
- Target files:
  - #3 `OgnTrafficRepository.kt`
  - #4 `MapOverlayManager.kt`
  - #5 `AdsbTrafficRepository.kt`
  - #9 `ForecastRasterOverlay.kt`
  - #10 `OgnThermalRepository.kt`
  - #11 `IgcReplayController.kt`
  - #14 `ForecastOverlayRepository.kt`
  - #17 `CalculateFlightMetricsUseCase.kt`
- Execution order (critical first):
  1. `OgnTrafficRepository.kt`, `AdsbTrafficRepository.kt`
  2. `OgnThermalRepository.kt`, `IgcReplayController.kt`
  3. `MapOverlayManager.kt`, `ForecastRasterOverlay.kt`
  4. `ForecastOverlayRepository.kt`, `CalculateFlightMetricsUseCase.kt`
- Delivery constraints (to reach >=94/100 quality):
  - One primary runtime file per PR slice (plus extracted collaborators only).
  - PR size target for this phase: `<= 300 LOC` net change per slice where feasible.
  - No behavior edits mixed with structural extraction in the same commit group.
  - Keep public API signatures stable unless a dedicated migration sub-slice is documented.
- Mandatory compliance gates (hard stop if failed):
  - `python scripts/arch_gate.py`
  - `./gradlew enforceRules`
  - `./gradlew testDebugUnitTest`
  - `./gradlew assembleDebug`
- Determinism and regression gates (hard stop if failed):
  - replay-sensitive and repository-sensitive targeted tests run twice in the same environment and must be stable across runs.
  - no new flaky/ignored tests in touched scopes.
- Mandatory architecture checks per PR slice:
  - SSOT owner unchanged for ADS-B/OGN/forecast/replay domain state.
  - No direct time API usage in domain/fusion/replay logic.
  - No UI types imported into domain/repository collaborators.
  - No raw manager/controller escape hatches introduced.
- Required evidence for closeout:
  - Before/after line counts for each target file and extracted collaborators.
  - Tests added/updated list mapped to each split responsibility.
  - Verification command results with pass/fail status.
  - If any temporary non-compliance remains, time-boxed entry in `KNOWN_DEVIATIONS.md` (issue, owner, expiry).
- Exit criteria:
  - each target file <=500
  - extracted collaborators remain layer-correct and DI-injected where applicable
  - replay determinism and runtime behavior preserved by tests
  - all mandatory gates pass
- Repass-miss updates (must close in this phase):
  - Missed: earlier score scale mixed `/5` and `/100` conventions; now unified to `>=94/100`.
  - Missed: deterministic-sensitive files need explicit run-order lock; keep critical-first order as mandatory, not advisory.
  - Missed (comprehensive pass): highest runtime complexity/risk slices require explicit extraction boundaries:
    - `OgnTrafficRepository.kt` (46 functions, 8 `while` loops, 8 `launch`, heavy monotonic/wall handling)
    - `AdsbTrafficRepository.kt` (41 functions, multiple loop/launch paths, retry/circuit logic)
    - `OgnThermalRepository.kt` (33 functions, mixed monotonic+wall policy paths)
    - `IgcReplayController.kt` (replay session control + loop/coroutine sequencing)
  - Correction: every Phase 1 PR summary must include deterministic rerun evidence and explicit before/after API contract notes.
  - Missed: rollback trigger criteria were implicit.
  - Correction: define rollback trigger as any parity test failure, timebase drift, or failed repeat-run determinism check in touched runtime slices.

#### Phase 1 Quality Scorecard (Must Be >=94/100)

| Dimension | Target |
|---|---:|
| Architecture cleanliness (boundaries, SSOT, DI) | >= 94 |
| Determinism/timebase safety | >= 94 |
| Test confidence on touched runtime paths | >= 94 |
| Maintainability/line-budget compliance quality | >= 94 |
| Verification evidence completeness | >= 94 |
| **Overall weighted score** | **>= 94** |

### Phase 2 - UI/Screen Hotspot Decomposition

- Production-grade target rating: **>=94/100**
- Goal:
  - split large composable/screen files into focused UI-only sections.
- Target files:
  - #6, #13, #15, #16, #20
- Mandatory controls:
  - Composables remain render + intent only; no business/domain math migration.
  - All UI state collection remains lifecycle-aware.
  - Callback contracts from ViewModel/use-case remain unchanged.
- Mandatory verification:
  - `python scripts/arch_gate.py`
  - `./gradlew enforceRules`
  - `./gradlew testDebugUnitTest`
  - `./gradlew assembleDebug`
- Exit criteria:
  - no business logic moved into UI
  - lifecycle collection and intent flows unchanged
  - all target files <=500
  - phase scorecard >=94/100
- Repass-miss updates (must close in this phase):
  - Missed: baseline line counts can drift during parallel branch work.
  - Correction: capture fresh line-count snapshot at phase start and update phase table before any split.
  - Correction: include Compose preview/compile evidence for each split UI file group.
  - Missed (comprehensive pass): UI hotspot files in this phase are not yet guard-covered in `enforce_rules.ps1` and must be added before refactor closeout.
  - Missed: explicit module compile gate for UI-only refactors was not listed.
  - Correction: require `./gradlew :feature:map:compileDebugKotlin` in Phase 2 verification evidence.

#### Phase 2 Quality Scorecard (Must Be >=94/100)

| Dimension | Target |
|---|---:|
| UI boundary purity (render + intents only) | >= 94 |
| Lifecycle correctness | >= 94 |
| Regression confidence (UI state/callback behavior) | >= 94 |
| Maintainability/line-budget compliance quality | >= 94 |
| Verification evidence completeness | >= 94 |
| **Overall weighted score** | **>= 94** |

### Phase 3 - Test Hotspot Decomposition

- Production-grade target rating: **>=94/100**
- Goal:
  - split mega tests by scenario domain with shared fixtures.
- Target files:
  - #1, #2, #7, #8, #12
- Mandatory controls:
  - No assertion weakening, removal, or semantic drift.
  - Shared fixture extraction must not hide scenario intent.
  - Test method naming keeps behavior intent explicit.
- Mandatory verification:
  - run affected test slices twice; results must match.
  - no new flaky/ignored tests.
- Exit criteria:
  - deterministic tests remain stable
  - each test file <=500
  - phase scorecard >=94/100
- Repass-miss updates (must close in this phase):
  - Missed: scenario-cluster intent can be lost when splitting mega tests.
  - Missed (comprehensive pass): test cluster inventory must drive split topology:
    - ADS-B repo tests: 48 tests, 7 helpers, explicit `emergencyAudio` cluster (8 tests)
    - MapScreenViewModel tests: 35 tests, 11 helpers, ADS-B/OGN/settings/state clusters
    - Metrics tests: `te`/`wind`/`tc30s` clusters
  - Correction: preserve cluster naming by suite (for example polling/network/emergencyAudio for ADS-B tests).
  - Correction: every moved test must map to an original test ID/name in a migration checklist artifact.
  - Missed: deterministic repeat-run criterion lacked explicit run-count standard.
  - Correction: each touched test suite must pass two consecutive runs with identical pass/fail outcome and no newly flaky cases.

#### Phase 3 Quality Scorecard (Must Be >=94/100)

| Dimension | Target |
|---|---:|
| Scenario coverage parity | >= 94 |
| Determinism and repeat-run stability | >= 94 |
| Fixture quality and readability | >= 94 |
| Maintainability/line-budget compliance quality | >= 94 |
| Verification evidence completeness | >= 94 |
| **Overall weighted score** | **>= 94** |

### Phase 4 - Guard Hold for Already-Compliant Targets

- Production-grade target rating: **>=94/100**
- Goal:
  - keep #18 and #19 below 500 and prevent growth.
- Target files:
  - #18, #19
- Mandatory controls:
  - add/extend line-budget checks in rule gate for these files.
  - no behavior change accepted in this phase.
- Exit criteria:
  - unchanged behavior
  - explicit gate coverage added
  - phase scorecard >=94/100
- Repass-miss updates (must close in this phase):
  - Missed: explicit `Assert-MaxLines` coverage for `AdsbSettingsScreen.kt` and `CardPreferences.kt` is not yet present in `enforce_rules.ps1`.
  - Missed (comprehensive pass): hold-file protection should include two additional UI hold candidates if they are not in active phase scope closures:
    - `MapScreenContent.kt`
    - `Settings-df.kt`
  - Correction: add these two guard entries and verify failure mode by temporary threshold breach in a local dry run.
  - Correction evidence: include exact rule lines and gate pass output.
  - Missed: guard budgets for these hold files were not explicitly stated in phase text.
  - Correction: lock these files at `<= 500` via named rule checks in `enforce_rules.ps1`.

#### Phase 4 Quality Scorecard (Must Be >=94/100)

| Dimension | Target |
|---|---:|
| Regression avoidance | >= 94 |
| Gate coverage quality | >= 94 |
| Maintainability/line-budget compliance quality | >= 94 |
| Verification evidence completeness | >= 94 |
| **Overall weighted score** | **>= 94** |

### Phase 5 - Hardening and Closeout

- Production-grade target rating: **>=94/100**
- Goal:
  - finalize docs and verification evidence.
- Mandatory controls:
  - docs sync complete for any wiring/rule changes.
  - no unresolved compliance caveats in touched scope.
  - quality rescore included with evidence and residual risks.
- Exit criteria:
  - required checks pass
  - no new deviations required
  - phase scorecard >=94/100
- Repass-miss updates (must close in this phase):
  - Missed: contract location mismatch occurred (auto contract was initially created outside requested path).
  - Correction: keep `docs/UNITS/AGENT_EXECUTION_CONTRACT_TOP20_LINE_BUDGET_2026-03-02.md` as the execution anchor and cross-link from final summary.
  - Missed (comprehensive pass): final closeout did not require a fresh post-implementation top-20 snapshot.
  - Correction: require final snapshot command output (size + lines) and explicit comparison against Section 1A baseline.
  - Correction: final closeout report must include a "misses found and fixed" section with phase-by-phase evidence.
  - Missed: final closeout checklist did not explicitly require Phase 0 score reporting.
  - Correction: final report must include score + evidence table for Phases 0 through 5 (all >=94/100).

#### Phase 5 Quality Scorecard (Must Be >=94/100)

| Dimension | Target |
|---|---:|
| Documentation fidelity and traceability | >= 94 |
| Verification completeness | >= 94 |
| Release-readiness confidence | >= 94 |
| Residual-risk closure quality | >= 94 |
| **Overall weighted score** | **>= 94** |

## 4A) Per-File Production Refactor Plan

### 1) `AdsbTrafficRepositoryTest.kt` (2478)

- Plan:
  - Split by scenario families:
    - `AdsbTrafficRepositoryPollingResilienceTest.kt`
    - `AdsbTrafficRepositoryNetworkTransitionsTest.kt`
    - `AdsbTrafficRepositoryFilteringAndSelectionTest.kt`
    - `AdsbTrafficRepositoryEmergencyAudioTest.kt`
    - `AdsbTrafficRepositoryDeterminismTest.kt`
    - `AdsbTrafficRepositoryTestFixtures.kt`
- Compliance target:
  - each new test file <= 450 lines.
- Verification:
  - all existing ADS-B behavior assertions preserved.

### 2) `MapScreenViewModelTest.kt` (1203)

- Plan:
  - Split into:
    - core map state tests
    - ADS-B selection/detail tests
    - OGN/thermal mutual-exclusion tests
    - settings bootstrap/default tests
    - shared fixture/builders file
- Compliance target:
  - each file <= 450 lines.
- Verification:
  - state transition and selection behavior unchanged.

### 3) `OgnTrafficRepository.kt` (1123)

- Plan:
  - Keep interface + thin orchestrator in `OgnTrafficRepository.kt`.
  - Extract collaborators:
    - `OgnTrafficConnectionLoop.kt`
    - `OgnTrafficTargetStore.kt`
    - `OgnOwnshipFilterPolicy.kt`
    - `OgnAutoReceiveRadiusPolicy.kt`
    - `OgnTrafficProtocolParser.kt`
- Compliance target:
  - `OgnTrafficRepository.kt` <= 350; each collaborator <= 450.
- Verification:
  - same snapshot semantics, radius behavior, suppression behavior.

### 4) `MapOverlayManager.kt` (1026)

- Plan:
  - Convert to runtime facade and extract domain-specific runtime controllers:
    - traffic overlays
    - forecast overlay
    - SkySight satellite overlay
    - weather rain overlay
    - OGN render scheduling helper
- Compliance target:
  - `MapOverlayManager.kt` <= 350.
- Verification:
  - map style reload/reapply behavior unchanged.

### 5) `AdsbTrafficRepository.kt` (1008)

- Plan:
  - Keep repository API + coordinator.
  - Extract:
    - loop runner / network wait handling
    - poll-delay adapter (wrapping policy)
    - target-store publish helper
    - emergency-audio trigger helper
- Compliance target:
  - repository file <= 350.
- Verification:
  - polling cadence, circuit-breaker, emergency audio semantics unchanged.

### 6) `MapScreenContent.kt` (860)

- Plan:
  - Split into UI-only sections:
    - root orchestration
    - overlays/panels
    - bottom-sheet sections
    - tap label/format helpers
- Compliance target:
  - root file <= 300.
- Verification:
  - Compose state collection and callback contracts unchanged.

### 7) `CalculateFlightMetricsUseCaseTest.kt` (938)

- Plan:
  - Split by domain behavior:
    - TE source selection + fallback
    - wind hysteresis/dwell telemetry
    - tc30s and smoothing behavior
    - reset/edge-case behavior
    - shared fixtures
- Compliance target:
  - each file <= 450.
- Verification:
  - deterministic outputs and counter assertions unchanged.

### 8) `OgnThermalRepositoryTest.kt` (844)

- Plan:
  - Split by behavior group:
    - detection and tracker confirmation
    - finalization and housekeeping
    - retention/day-window policy
    - dedupe/display percent winner policy
    - shared fixtures
- Compliance target:
  - each file <= 450.
- Verification:
  - thermal confirmation and pruning behavior unchanged.

### 9) `ForecastRasterOverlay.kt` (828)

- Plan:
  - Keep facade in original file.
  - Extract:
    - source/layer lifecycle controller
    - wind arrow renderer
    - wind barb renderer
    - expression/icon factory helpers
- Compliance target:
  - original file <= 300.
- Verification:
  - same rendered layers, same cleanup behavior.

### 10) `OgnThermalRepository.kt` (760)

- Plan:
  - Keep repository orchestration in original file.
  - Extract:
    - tracker update engine
    - retention policy helper
    - area winner selector
    - housekeeping scheduler
- Compliance target:
  - original file <= 350.
- Verification:
  - freshness gates, retention windows, suppression purge unchanged.

### 11) `IgcReplayController.kt` (692)

- Plan:
  - Keep controller API in original file.
  - Extract:
    - replay session state machine
    - replay play-loop runner
    - session loader + validation helper
    - interpolation helper
- Compliance target:
  - original file <= 350.
- Verification:
  - replay start/pause/stop/seek determinism unchanged.

### 12) `ForecastOverlayRepositoryTest.kt` (725)

- Plan:
  - Split by scenario:
    - selection/time-slot resolution
    - tile + legend error semantics
    - wind overlay combination behavior
    - point-query behavior
    - shared fixtures/fakes
- Compliance target:
  - each file <= 450.
- Verification:
  - failure-mode expectations unchanged.

### 13) `Settings-df.kt` (716)

- Plan:
  - Split into:
    - screen shell and nav handlers
    - category list items
    - per-subsheet content files
    - action row helpers
- Compliance target:
  - original file <= 300.
- Verification:
  - route transitions and settings invocation unchanged.

### 14) `ForecastOverlayRepository.kt` (600)

- Plan:
  - Keep repository facade.
  - Extract:
    - selection resolver
    - auto-time ticker helper
    - message/error normalization helper
    - ui-state assembly helper
- Compliance target:
  - original file <= 320.
- Verification:
  - state composition, retries, warning/error separation unchanged.

### 15) `ForecastOverlayBottomSheet.kt` (626)

- Plan:
  - Split into:
    - container + state callbacks
    - controls sections
    - query status/callout card
    - format helper file
- Compliance target:
  - original file <= 300.
- Verification:
  - control binding behavior and sheet events unchanged.

### 16) `WeatherSettingsScreen.kt` (591)

- Plan:
  - Split into:
    - screen + content composition
    - card sections
    - formatting/summary helpers
    - attribution/open-link helper
- Compliance target:
  - original file <= 300.
- Verification:
  - settings writes and status labels unchanged.

### 17) `CalculateFlightMetricsUseCase.kt` (536)

- Plan:
  - Keep top-level use case and public API in original file.
  - Extract:
    - airspeed source resolver helper
    - display smoothing helper
    - request/result models file if needed
- Compliance target:
  - original file <= 350.
- Verification:
  - TE/netto, smoothing, and counter semantics unchanged.

### 18) `AdsbSettingsScreen.kt` (460, already compliant)

- Plan:
  - Keep behavior stable.
  - Optional extraction of slider conversion helpers to prevent future growth.
- Compliance target:
  - remain <= 500.
- Verification:
  - settings conversion behavior unchanged.

### 19) `CardPreferences.kt` (434, already compliant)

- Plan:
  - Keep behavior stable.
  - Optional extraction of serialization/codec helpers if growth continues.
- Compliance target:
  - remain <= 500.
- Verification:
  - DataStore schema/keys unchanged.

### 20) `HawkVarioSettingsScreen.kt` (563)

- Plan:
  - Split into:
    - main screen composition
    - preview card
    - needle tuning card
    - formatting and config helpers
- Compliance target:
  - original file <= 300.
- Verification:
  - visual behavior and slider actions unchanged.

## 4B) Per-File Phased IP Matrix (Repass x5 Updated)

Legend:
- `IP-0`: baseline lock (no behavior change, capture contracts/tests)
- `IP-1`: decomposition split
- `IP-2`: wiring and ownership hardening
- `IP-3`: verification and regression lock
- `IP-4`: compliance closeout (`<= 500`)

| # | File | IP-0 | IP-1 | IP-2 | IP-3 | IP-4 |
|---|---|---|---|---|---|---|
| 1 | `AdsbTrafficRepositoryTest.kt` | freeze all 48 current scenarios | split into 5 scenario files + fixtures | centralize fake builders/time setup | rerun ADS-B test slice | all test files <=450, zero assertion loss |
| 2 | `MapScreenViewModelTest.kt` | freeze current state-transition expectations | split by map core, ADS-B, OGN/thermal, settings | move helper builders to shared fixture file | rerun ViewModel test slice | each file <=450, no state regression |
| 3 | `OgnTrafficRepository.kt` | freeze snapshot/radius/suppression semantics | extract loop/store/policy/parser collaborators | keep repository as SSOT orchestrator only | rerun OGN repository tests | facade <=350, collaborators <=450 |
| 4 | `MapOverlayManager.kt` | freeze style-reload and overlay ownership behavior | extract forecast/satellite/weather/traffic runtime controllers | keep single top-level runtime owner path | rerun map overlay tests/manual style switch | manager <=350, no overlay lifecycle drift |
| 5 | `AdsbTrafficRepository.kt` | freeze polling/circuit/emergency-audio behavior | extract polling/network/store/audio helpers | keep repository API unchanged and deterministic | rerun ADS-B repo + telemetry tests | repository <=350, helpers <=450 |
| 6 | `MapScreenContent.kt` | freeze callback and composable state contract | split root, panels, sheets, label helpers | keep UI-only logic, no business migration | compose preview/interaction test rerun | root <=300, sections <=450 |
| 7 | `CalculateFlightMetricsUseCaseTest.kt` | freeze TE/wind/tc30s expected outputs | split by TE/wind/tc30s/reset clusters | extract shared request builders/fakes | rerun metrics test slice | each file <=450, determinism retained |
| 8 | `OgnThermalRepositoryTest.kt` | freeze thermal confirm/finalize/retention behavior | split by detection/finalization/retention/dedupe | centralize target sample builders | rerun thermal repo tests | each file <=450, same scenario coverage |
| 9 | `ForecastRasterOverlay.kt` | freeze rendered layer IDs and cleanup behavior | split source/layer lifecycle vs wind renderers | keep facade as runtime owner | rerun forecast overlay tests/manual map checks | facade <=300, helpers <=450 |
| 10 | `OgnThermalRepository.kt` | freeze hotspot lifecycle and retention semantics | extract tracker/retention/winner/scheduler helpers | keep SSOT publication path in repository | rerun OGN thermal tests | repository <=350, helpers <=450 |
| 11 | `IgcReplayController.kt` | freeze replay start/pause/seek/stop behavior | extract state machine/loop/session loader helpers | keep public controller API stable | rerun replay controller tests | controller <=350, deterministic replay preserved |
| 12 | `ForecastOverlayRepositoryTest.kt` | freeze selection/error/query expectations | split by selection, tile errors, wind, point query | add shared fake provider fixtures | rerun forecast repo tests | each file <=450 |
| 13 | `Settings-df.kt` | freeze route and sheet-open behavior | split shell, category list, subsheets, actions | keep navigation callbacks unchanged | rerun settings/navdrawer tests | root <=300, each section <=450 |
| 14 | `ForecastOverlayRepository.kt` | freeze state composition and retry semantics | extract selection resolver + message normalizer + ticker | keep repository as single state composer | rerun forecast repository tests | repository <=320 |
| 15 | `ForecastOverlayBottomSheet.kt` | freeze control callbacks and status rendering | split container, controls, callout/status, formatters | keep viewmodel intent flow unchanged | rerun compose tests for sheet controls | root <=300 |
| 16 | `WeatherSettingsScreen.kt` | freeze weather settings toggles/status copy behavior | split content cards and formatter helpers | keep attribution path intact | rerun weather settings tests | root <=300 |
| 17 | `CalculateFlightMetricsUseCase.kt` | freeze TE/netto/smoothing counters contract | extract airspeed resolver + smoothing helpers + models | keep use-case entrypoint pure and stable | rerun metrics + replay-sensitive tests | use-case <=350 |
| 18 | `AdsbSettingsScreen.kt` | freeze existing conversion behavior | optional helper extraction only if needed | no architecture movement | rerun ADS-B settings tests | remain <=500 |
| 19 | `CardPreferences.kt` | freeze preference key/schema behavior | optional codec/helper extraction only if needed | keep DataStore ownership unchanged | rerun dfcards preference tests | remain <=500 |
| 20 | `HawkVarioSettingsScreen.kt` | freeze preview/tuning UI semantics | split main screen, cards, format/config helpers | keep viewmodel contract unchanged | rerun Hawk settings compose tests | root <=300 |

## 5) Test Plan

- Unit tests:
  - preserve existing test scenarios; move only structure, not assertions.
- Replay/regression tests:
  - replay controller and metrics determinism slices must be rerun.
- UI/instrumentation tests:
  - settings/navdrawer and map content interaction slices as relevant.
- Degraded/failure-mode tests:
  - ADS-B retry/offline/circuit tests, forecast error handling tests, OGN retention/housekeeping tests.
- Boundary tests for removed bypasses:
  - compile-time and enforceRules checks for no boundary drift.

Required checks:

```bash
python scripts/arch_gate.py
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

When relevant:

```bash
./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
./gradlew connectedDebugAndroidTest --no-parallel
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| hidden behavior drift during extraction | High | move code with behavior lock tests first | module owner |
| boundary leakage across layers | High | enforceRules + focused review checklist | module owner |
| replay determinism regression | High | replay deterministic tests in same phase | replay owner |
| incomplete line-budget enforcement | Medium | docs + enforce_rules updates in Phase 0 | architecture owner |
| merge conflicts due broad file touch | Medium | split by phase and submit small PR slices | XCPro Team |

## 7) Acceptance Gates

- New rule is documented in architecture docs (`<= 500` policy).
- `enforceRules` includes line-budget checks for this plan scope.
- All 20 target files are `<= 500` lines at closeout.
- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`.
- No duplicate SSOT ownership introduced.
- Replay behavior remains deterministic.
- `KNOWN_DEVIATIONS.md` only updated if explicitly approved.

## 8) Rollback Plan

- Independent rollback units:
  - policy/gate updates
  - repository-domain splits
  - UI file splits
  - test decomposition
- Recovery steps if regression is detected:
  1. Revert only the failing phase slice.
  2. Restore previous file topology for affected domain.
  3. Keep passing policy/gate changes unless root cause is gate bug.
  4. Re-run required checks and re-stage the slice with narrower scope.
