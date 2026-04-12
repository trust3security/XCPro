# CHANGE_PLAN_RAINVIEWER_FRAME_COUNT_CONTROL_2026-03-02.md

## 0) Metadata

- Title: RainViewer Animated Window Frame Count Control
- Owner: XCPro Team
- Date: 2026-03-02
- Issue/PR: RAIN-20260302-01
- Status: Complete

## 1) Scope

- Problem statement:
  - RainViewer cycle mode currently animates all eligible frames in the selected window (`10m`, `20m`, `30m`) with no user cap.
  - Users cannot choose shorter loops (for readability/perf) while keeping cycle mode enabled.
  - Current map Weather bottom sheet toggle only enables/disables cycle; advanced frame-loop control is missing from the RainViewer settings surface.
- Why now:
  - User request for explicit frame-count control by window.
  - Repass confirmed no existing preference or UI control for this contract.
- In scope:
  - Add per-window frame-count preference for RainViewer cycle mode (`10m`, `20m`, `30m`).
  - Add RainViewer settings slider in `WeatherSettingsScreen` to configure frame count for the currently selected window.
  - Add domain/runtime selection logic to apply user frame-count cap deterministically.
  - Add/adjust tests for preferences, use-case/viewmodel wiring, selection behavior, and policy helpers.
  - Update pipeline docs to include the new preference contract.
- Out of scope:
  - Changing RainViewer metadata fetch cadence or source.
  - New map bottom-sheet controls beyond existing toggle.
  - Replay/fusion/variometer pipeline changes.
- User-visible impact:
  - In RainViewer settings, users can tune cycle frame count for each animation window.
  - Cycle mode can run shorter loops while preserving deterministic frame ordering.

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| RainViewer per-window cycle frame count preferences | `WeatherOverlayPreferencesRepository` | `Flow<WeatherOverlayPreferences>` | UI-local remembered copies as source of truth |
| Active animation window selection | `WeatherOverlayPreferencesRepository` | `Flow<WeatherOverlayPreferences>` | separate VM-owned persistent state |
| Runtime selected weather radar frame | `ObserveWeatherOverlayStateUseCase` | `Flow<WeatherOverlayRuntimeState>` | UI-side frame selection logic |

### 2.2 Dependency Direction

Dependency flow remains:

`UI -> domain/use-cases -> data`

- Modules/files touched:
  - `feature/map/src/main/java/com/example/xcpro/weather/rain/*`
  - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/WeatherSettings*`
  - weather-related tests under `feature/map/src/test/java/com/example/xcpro/...`
  - `docs/ARCHITECTURE/PIPELINE.md`
- Boundary risk:
  - Avoid placing frame-selection policy in Compose UI.
  - Avoid adding persistence logic in ViewModel.

### 2.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Cycle frame-count policy by window | implicit "all eligible frames" in use-case | explicit user-configured cap resolved in use-case from repository-backed prefs | expose user control without violating SSOT/UDF | use-case tests |

### 2.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| N/A | N/A | N/A | N/A |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| RainViewer frame timestamps (`timeEpochSec`) | Wall (epoch seconds from provider payload) | Provider contract payload semantics |
| Animation tick index | Wall/UI runtime timer (coroutine delay cadence) | visual frame stepping only |
| Metadata freshness/content age | Wall (injected `Clock.nowWallMs`) | status labeling and staleness policy |

Explicitly forbidden comparisons:

- Monotonic vs wall
- Replay vs wall

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - Preference persistence and metadata/network: `IO`
  - Compose rendering: `Main`
- Primary cadence/gating sensor:
  - Existing weather animation tick (`animationSpeed.frameIntervalMs`) remains owner.
- Hot-path latency budget:
  - Frame cap application is list slicing only (O(n) bounded by small candidate list).

### 2.5 Replay Determinism

- Deterministic for same input: Yes.
- Randomness used: No.
- Replay/live divergence rules:
  - No replay-specific behavior added.
  - Change only affects weather overlay frame selection policy via explicit preference.

### 2.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| Frame-selection logic drifts into UI | MVVM + UDF + SSOT | unit tests + review | `ObserveWeatherOverlayStateUseCaseTest`, `WeatherSettingsScreenPolicyTest` |
| Preference persistence regression | SSOT repository ownership | repo tests | `WeatherOverlayPreferencesRepositoryTest` |
| ViewModel starts owning persistence rules | ViewModel purity rule | unit tests + review | `WeatherSettingsViewModelTest`, `WeatherSettingsUseCaseTest` |
| Pipeline docs drift | Documentation sync rule | review + doc update | `docs/ARCHITECTURE/PIPELINE.md` |

## 3) Data Flow (Before -> After)

Before:

`WeatherSettingsScreen toggle/chips -> WeatherSettingsViewModel -> WeatherOverlayPreferencesRepository`  
`ObserveWeatherOverlayStateUseCase -> selects all eligible window frames`

After:

`WeatherSettingsScreen slider (per selected window frame count) -> WeatherSettingsViewModel -> WeatherOverlayPreferencesRepository (per-window counts)`  
`ObserveWeatherOverlayStateUseCase -> applies per-window frame cap -> deterministic playback index selection`

## 4) Implementation Phases

### Phase 0 - Baseline and Repass Lock

- Goal:
  - Confirm current frame-selection path and add/prepare tests for new frame-count contract.
- Files to change:
  - Test files only.
- Tests to add/update:
  - Use-case tests for user-capped frame subsets in `10m/20m/30m` windows.
- Exit criteria:
  - Existing behavior preserved where cap is `Auto` and no regressions in baseline tests.

### Phase 1 - Domain and Preference Model

- Goal:
  - Add per-window frame-count preference model and deterministic clamp helpers.
- Files to change:
  - `WeatherOverlayModels.kt`
  - `WeatherOverlaySettings.kt`
  - `WeatherOverlayPreferencesRepository.kt`
  - `ObserveWeatherOverlayStateUseCase.kt`
- Tests to add/update:
  - `WeatherOverlaySettingsTest`
  - `WeatherOverlayPreferencesRepositoryTest`
  - `ObserveWeatherOverlayStateUseCaseTest`
- Exit criteria:
  - Runtime frame selection uses configured per-window cap.
  - `Auto` retains all eligible frames.

### Phase 2 - ViewModel and Use-Case Wiring

- Goal:
  - Expose per-window frame-count settings through settings use-case/viewmodel.
- Files to change:
  - `WeatherSettingsUseCase.kt`
  - `WeatherSettingsViewModel.kt`
- Tests to add/update:
  - `WeatherSettingsUseCaseTest`
  - `WeatherSettingsViewModelTest`
- Exit criteria:
  - UI can read and mutate per-window frame-count preferences through existing architecture path.

### Phase 3 - RainViewer Settings UI

- Goal:
  - Add frame-count slider control in RainViewer settings card.
- Files to change:
  - `WeatherSettingsScreen.kt`
- Tests to add/update:
  - `WeatherSettingsScreenPolicyTest` helper/policy assertions for slider labels/range semantics.
- Exit criteria:
  - Slider controls current animation window frame count.
  - UI remains presentation-only (no business frame-selection logic).

### Phase 4 - Hardening and Documentation Sync

- Goal:
  - Validate gates and update pipeline contract text.
- Files to change:
  - `docs/ARCHITECTURE/PIPELINE.md`
- Tests to add/update:
  - None beyond prior phases.
- Exit criteria:
  - Required verification commands pass or failures are documented with exact causes.
  - Pipeline docs match implemented weather preference contract.

## 4A) Production Closure Evidence

Phase-by-phase closure evidence:

1. Phase 0 - Baseline and repass lock
   - Completed.
   - Evidence:
     - flow/contract repass recorded and phased plan authored in this file.
     - targeted selection regressions captured in:
       - `ObserveWeatherOverlayStateUseCaseTest.invoke_limitsTenMinuteWindowToConfiguredFrameCount`
       - `ObserveWeatherOverlayStateUseCaseTest.invoke_limitsThirtyMinuteWindowToConfiguredFrameCountAfterQualityPolicy`
2. Phase 1 - Domain and preference model
   - Completed.
   - Evidence:
     - per-window frame-count model + clamps:
       - `feature/map/.../WeatherOverlaySettings.kt`
       - `feature/map/.../WeatherOverlayModels.kt`
     - repository SSOT persistence keys + API:
       - `feature/map/.../WeatherOverlayPreferencesRepository.kt`
     - runtime selection policy application:
       - `feature/map/.../ObserveWeatherOverlayStateUseCase.kt`
     - tests:
       - `WeatherOverlaySettingsTest`
       - `WeatherOverlayPreferencesRepositoryTest`
       - `ObserveWeatherOverlayStateUseCaseTest`
3. Phase 2 - ViewModel and use-case wiring
   - Completed.
   - Evidence:
     - use-case projection + setter:
       - `feature/map/.../WeatherSettingsUseCase.kt`
     - viewmodel state + intent:
       - `feature/map/.../WeatherSettingsViewModel.kt`
     - tests:
       - `WeatherSettingsUseCaseTest`
       - `WeatherSettingsViewModelTest`
4. Phase 3 - RainViewer settings UI
   - Completed.
   - Evidence:
     - slider + labels + persistence intent wiring:
       - `feature/map/.../WeatherSettingsScreen.kt`
     - UI policy helper test:
       - `WeatherSettingsScreenPolicyTest.weatherAnimationFrameCountLabel_formatsAutoAndPlural`
5. Phase 4 - Hardening and documentation sync
   - Completed.
   - Evidence:
     - pipeline contract sync:
       - `docs/ARCHITECTURE/PIPELINE.md`
     - required verification commands:
       - `python scripts/arch_gate.py` -> pass
       - `./gradlew enforceRules` -> pass
       - `./gradlew testDebugUnitTest` -> pass
       - `./gradlew assembleDebug` -> pass

Post-implementation re-pass closure:
- Fixed missed production gap where explicit `30m` frame-count selections were being reduced by auto quality filtering.
- Final behavior:
  - `Auto` retains quality-policy filtering.
  - Explicit frame counts bypass auto drop logic and are respected (still clamped safely by availability/window bounds).
- Regression lock:
  - `ObserveWeatherOverlayStateUseCaseTest.invoke_respectsExplicitThirtyMinuteFrameCountWithoutAutoQualityDrop`

## 4B) Phase Production Status

| Phase | Status | Production Grade Notes |
|---|---|---|
| Phase 0 - Baseline and repass lock | Complete | regression locks added for capped window selection semantics |
| Phase 1 - Domain and preference model | Complete | SSOT-owned per-window preference model with clamp policy and deterministic runtime usage |
| Phase 2 - ViewModel and use-case wiring | Complete | UI intents route through use-case to repository; no boundary drift |
| Phase 3 - RainViewer settings UI | Complete | slider contract implemented in RainViewer modal path with policy helper tests |
| Phase 4 - Hardening and documentation sync | Complete | required verification commands passed; pipeline docs updated |

## 5) Test Plan

- Unit tests:
  - Weather frame-count clamp and playback-selection behavior.
  - Settings use-case/viewmodel pass-through for new preference.
- Replay/regression tests:
  - Not required (feature does not alter replay domain pipeline).
- UI/instrumentation tests:
  - Policy/helper tests for RainViewer settings slider semantics.
- Degraded/failure-mode tests:
  - Frame cap larger than available candidate frames clamps safely.
  - `Auto` mode behavior remains stable.
- Boundary tests for removed bypasses:
  - N/A.

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

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| User confusion between `Auto` and fixed count | Medium | explicit UI copy and label for `Auto` | XCPro Team |
| Over-constraining frames creates jumpy loop | Medium | default remains `Auto`; counts clamp safely to available window frames | XCPro Team |
| DataStore compatibility drift | Low | additive keys only; preserve defaults and existing keys | XCPro Team |
| Logic duplication between UI and domain | Medium | keep selection policy in use-case; UI only stores preference | XCPro Team |

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`.
- No duplicate SSOT ownership introduced.
- Time base handling remains explicit and unchanged for domain/fusion boundaries.
- Replay behavior remains deterministic.
- `KNOWN_DEVIATIONS.md` unchanged unless explicit approved exception is needed.

## 8) Rollback Plan

- What can be reverted independently:
  - UI slider wiring can be reverted without changing existing toggle/window/speed flows.
  - Preference keys can remain unused safely if domain logic rollback is needed.
- Recovery steps if regression is detected:
  1. Disable usage of frame-count cap in `ObserveWeatherOverlayStateUseCase` (fallback to current all-frames behavior).
  2. Hide slider UI while preserving stored values.
  3. Re-run weather unit test suite and required gradle checks.
