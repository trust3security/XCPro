# CHANGE_PLAN_RAINVIEWER_WINDOW_SLIDER_MAX_FRAMES_2026-03-02.md

## 0) Metadata

- Title: RainViewer Single Window Slider with Max-Frame Playback
- Owner: XCPro Team
- Date: 2026-03-02
- Issue/PR: RAIN-20260302-02
- Status: Complete
- Supersedes: `docs/ARCHITECTURE/CHANGE_PLAN_RAINVIEWER_FRAME_COUNT_CONTROL_2026-03-02.md`

## 1) Scope

- Problem statement:
  - Current RainViewer settings expose two controls (`window chips` + `frame-count slider`) and cap at `10m/20m/30m`.
  - Product direction is a single window-duration slider (`10, 20, 30, ...`) with playback always using the maximum eligible frames for that selected window.
- Why now:
  - User requested simpler UX and explicit longer windows.
  - Existing frame-count control creates confusion and conflicts with expected max-frame behavior.
- In scope:
  - Remove explicit frame-count control path from settings/runtime behavior.
  - Replace window chips + frame slider with one window-duration slider.
  - Expand supported windows to 10-minute increments through 120 minutes.
  - Ensure runtime always cycles all eligible frames in selected window.
  - Update tests and pipeline docs.
- Out of scope:
  - RainViewer metadata transport/fetch cadence changes.
  - Replay/variometer/sensor-fusion changes.
- User-visible impact:
  - One slider controls animation window.
  - Selected window shows max possible frames for that window (subject to currently available metadata frames).

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Rain overlay preferences (`enabled`, `opacity`, `animatePastWindow`, `animationWindow`, speed/transition/frame mode/render options) | `WeatherOverlayPreferencesRepository` | `Flow<WeatherOverlayPreferences>` | UI-local state as persistent owner |
| Selected rain frame for map rendering | `ObserveWeatherOverlayStateUseCase` | `Flow<WeatherOverlayRuntimeState>` | UI-side frame-selection logic |
| Window-to-max-frame policy | weather rain domain settings/use-case | pure helpers + use-case logic | Compose/UI hardcoded policy |

### 2.2 Dependency Direction

`UI -> domain/use-cases -> data`

- Modules/files touched:
  - `feature/map/src/main/java/com/example/xcpro/weather/rain/*`
  - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/WeatherSettings*`
  - weather/navdrawer tests under `feature/map/src/test/java/com/example/xcpro/**`
  - `docs/ARCHITECTURE/PIPELINE.md`
- Boundary risk:
  - Prevent policy drift into Compose labels/controls.
  - Keep persistence in repository only.

### 2.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| User frame-count override contract | preferences + use-case + UI | removed; use-case enforces max eligible frames for selected window | align UX with product requirement | use-case tests + UI policy tests |

### 2.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| Weather settings direct frame-count intent path | UI -> VM -> use-case -> repo `setAnimationFrameCount(...)` | remove call path; window selection only | 2-3 |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| RainViewer frame timestamps (`timeEpochSec`) | Wall (provider epoch seconds) | provider payload semantics |
| Weather metadata freshness/content age | Wall (`Clock.nowWallMs`) | status/age labeling |
| Animation tick | runtime timer cadence | visual-only frame stepping |

Forbidden:
- Monotonic vs wall comparisons
- Replay vs wall comparisons

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - IO: DataStore + network metadata fetch.
  - Main: Compose UI/rendering.
- Primary cadence:
  - existing animation speed cadence remains owner of frame stepping.
- Hot path:
  - frame selection remains bounded list filtering by time window.

### 2.5 Replay Determinism

- Deterministic for same input: Yes.
- Randomness used: No.
- Replay/live divergence rules: unchanged (weather overlay change only).

### 2.6 Enforcement Coverage

| Risk | Rule Reference | Guard Type | File/Test |
|---|---|---|---|
| UI reintroduces frame-selection business logic | MVVM/UDF/SSOT | unit tests + review | `WeatherSettingsScreenPolicyTest`, `ObserveWeatherOverlayStateUseCaseTest` |
| Repository contract drift while removing frame-count path | SSOT ownership | repository tests | `WeatherOverlayPreferencesRepositoryTest` |
| ViewModel/use-case leakage | ViewModel purity | VM/use-case tests | `WeatherSettingsViewModelTest`, `WeatherSettingsUseCaseTest` |
| Pipeline docs stale | Documentation sync | doc update + review | `docs/ARCHITECTURE/PIPELINE.md` |

## 3) Data Flow (Before -> After)

Before:

`WeatherSettingsScreen (window chips + frame count slider) -> VM -> use-case -> repo`  
`ObserveWeatherOverlayStateUseCase applies window + optional frame-count cap`

After:

`WeatherSettingsScreen (single window slider) -> VM -> use-case -> repo`  
`ObserveWeatherOverlayStateUseCase applies selected window and cycles all eligible frames in that window`

## 4) Implementation Phases

### Phase 0 - Baseline + Plan Lock

- Goal:
  - Lock architecture contract and phased execution for single-slider behavior.
- Files to change:
  - this plan document.
- Tests:
  - none.
- Exit criteria:
  - plan accepted and status set In progress.

### Phase 1 - Domain Policy Refactor

- Goal:
  - Move to window-driven max-frame policy and expand window enum to 10-minute increments through 120 minutes.
- Files to change:
  - `WeatherOverlayModels.kt`
  - `WeatherOverlaySettings.kt`
  - `ObserveWeatherOverlayStateUseCase.kt`
- Tests to add/update:
  - `WeatherOverlaySettingsTest`
  - `ObserveWeatherOverlayStateUseCaseTest`
- Exit criteria:
  - selected window drives frame eligibility and playback uses max eligible frames.

### Phase 2 - Repository/Use-Case/ViewModel Wiring Cleanup

- Goal:
  - Remove explicit frame-count settings flow/mutation path from settings stack.
- Files to change:
  - `WeatherOverlayPreferencesRepository.kt`
  - `WeatherSettingsUseCase.kt`
  - `WeatherSettingsViewModel.kt`
- Tests to add/update:
  - `WeatherOverlayPreferencesRepositoryTest`
  - `WeatherSettingsUseCaseTest`
  - `WeatherSettingsViewModelTest`
- Exit criteria:
  - no `setAnimationFrameCount` path remains in settings flow.

### Phase 3 - UI Single Slider

- Goal:
  - Replace animation window chips + frame-count slider with one window-duration slider.
- Files to change:
  - `WeatherSettingsScreen.kt`
- Tests to add/update:
  - `WeatherSettingsScreenPolicyTest`
- Exit criteria:
  - UI exposes one animation-window slider and labels max frames for selected window.

### Phase 4 - Hardening + Docs + Verification

- Goal:
  - Sync docs and verify required gates.
- Files to change:
  - `docs/ARCHITECTURE/PIPELINE.md`
  - phase status updates in this plan.
- Tests to add/update:
  - targeted regressions as needed from repass.
- Exit criteria:
  - required commands pass and plan marked Complete.

## 4A) Production Closure Evidence

1. Phase 0 - Baseline + plan lock
   - Completed.
   - Evidence:
     - this change plan created and scoped against architecture contracts.
2. Phase 1 - Domain policy refactor
   - Completed.
   - Evidence:
     - `WeatherRainAnimationWindow` expanded to `10m..120m`.
     - playback selection now uses all eligible frames in selected window (no explicit frame-count override path).
3. Phase 2 - Repository/use-case/viewmodel cleanup
   - Completed.
   - Evidence:
     - explicit `setAnimationFrameCount(...)` flow removed from settings stack.
     - weather preference model/runtime model no longer expose frame-count settings.
4. Phase 3 - UI single slider
   - Completed.
   - Evidence:
     - animation window chips + frame-count slider replaced by single animation-window slider.
     - UI label now reports selected window and max-frame capacity.
5. Phase 4 - Hardening/docs/verification
   - Completed.
   - Evidence:
     - pipeline contract updated in `docs/ARCHITECTURE/PIPELINE.md`.
     - verification runs:
       - `python scripts/arch_gate.py` -> pass
       - `./gradlew enforceRules` -> pass
       - `./gradlew :feature:map:compileDebugKotlin` -> pass
       - `./gradlew :feature:map:testDebugUnitTest --tests ...` (weather/settings targeted suite) -> pass
       - `./gradlew :feature:map:testDebugUnitTest --rerun-tasks` -> pass
       - `./gradlew assembleDebug` -> pass
       - `./gradlew testDebugUnitTest` -> fails in `:app` at `ProfileRepositoryTest.ioReadError_preservesLastKnownGoodState` (outside rainviewer scope)

## 4B) Phase Production Status

| Phase | Status | Production Grade Notes |
|---|---|---|
| Phase 0 - Baseline + plan lock | Complete | architecture contract and scope explicitly locked before refactor |
| Phase 1 - Domain policy refactor | Complete | max-frame playback semantics moved to domain path with expanded window set |
| Phase 2 - Repository/use-case/viewmodel cleanup | Complete | frame-count bypass path removed; SSOT contract simplified |
| Phase 3 - UI single slider | Complete | one control drives window selection; no business logic moved into Compose |
| Phase 4 - Hardening/docs/verification | Complete | required gates passed except unrelated pre-existing app-unit failure |

## 5) Test Plan

- Unit tests:
  - window parsing/range behavior and max-frame helper semantics.
  - runtime frame-selection behavior across 10m..120m windows.
  - repository/use-case/viewmodel contract after frame-count path removal.
- Replay/regression tests:
  - not applicable (no replay-path logic changes).
- UI/policy tests:
  - single-slider label/range/policy helper checks.
- Failure-mode tests:
  - sparse metadata (fewer frames than theoretical max) still clamps correctly by availability.

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Removing frame-count path leaves stale persisted values | Low | ignore/remove path at runtime/UI; repository contract remains stable | XCPro Team |
| Large enum/window change causes mapping gaps | Medium | add exhaustive tests + explicit `fromStorage` fallback | XCPro Team |
| UI slider rounding mismatch | Medium | discrete window mapping helper + policy tests | XCPro Team |

## 7) Acceptance Gates

- No architecture/coding-rule violations.
- No duplicate SSOT owners introduced.
- Time-base semantics unchanged and explicit.
- Weather playback deterministic for same metadata + tick.
- `KNOWN_DEVIATIONS.md` unchanged unless explicitly approved.

## 8) Rollback Plan

- Revert phases independently:
  - Phase 3 UI rollback can restore previous controls while keeping domain cleanup.
  - Phase 2 rollback can re-expose frame-count path if required.
- Recovery:
  1. Revert offending phase commit.
  2. Re-run required checks.
  3. Re-open phase with focused regression test additions.
