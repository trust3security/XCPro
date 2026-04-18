# CHANGE_PLAN_RAINVIEWER_DIRECT_OPEN_AND_WEATHER_TAB_SHEET_REMOVAL_2026-03-02.md

## 0) Metadata

- Title: RainViewer Bottom Tab Direct Open and Legacy Weather Sheet Removal
- Owner: XCPro Team
- Date: 2026-03-02
- Issue/PR: RAIN-20260302-04
- Status: Complete

## 1) Scope

- Problem statement:
  - The bottom-left `RainViewer` chip currently opens the map bottom-sheet tab panel first, then requires a secondary `More settings` action to reach main RainViewer settings.
  - Legacy weather controls remain in the bottom-sheet weather tab, creating duplicate entry points and dead/obsolete UI paths.
- Why now:
  - Product request is a direct single-tap path from map `RainViewer` chip to main RainViewer settings.
  - Removing obsolete sheet code reduces maintenance risk and behavior ambiguity.
- In scope:
  - Change `RainViewer` chip tap behavior to open main RainViewer settings directly (no intermediate bottom sheet).
  - Remove legacy weather-tab sheet content and unused callbacks/parameters tied to that path.
  - Keep RainViewer enabled-state green-border cue on chip.
  - Update tests for routing and removal.
- Out of scope:
  - RainViewer domain logic, frame/window policy, metadata fetch cadence, or overlay rendering behavior.
  - SkySight/OGN/Tab4 behavior beyond required signature cleanup.
- User-visible impact:
  - One tap on bottom-left `RainViewer` opens main RainViewer settings.
  - No weather controls appear inside map bottom-sheet tabs.

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Rain overlay enabled state | `WeatherOverlayPreferencesRepository` via `WeatherOverlayViewModel` | `overlayState.enabled` | UI-local persistence/state mirrors |
| Bottom-sheet visibility state | `MapScreenContent` UI state (`isBottomTabsSheetVisible`) | local compose state | repository/domain ownership |
| RainViewer settings route selection | `MapScreenScaffold` navigation callback (`onOpenWeatherSettingsFromTab`) | callback boundary | direct route logic in leaf composables |

### 2.2 Dependency Direction

`UI -> domain -> data` remains unchanged.

- Modules/files touched:
  - `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenContent.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapBottomSheetTabs.kt`
  - `feature/map/src/test/java/com/trust3/xcpro/map/ui/*`
  - this plan doc
- Any boundary risk:
  - Avoid moving navigation policy into reusable UI leaf components.
  - Keep settings navigation at scaffold/content boundary (existing pattern).

### 2.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| RainViewer tap routing (sheet open vs settings open) | `MapScreenContent` tab-selection branch | dedicated `onRainViewerSelected` callback path in map bottom tabs -> settings callback | enforce one-tap behavior and remove weather-tab coupling | unit test for chip presence/behavior + compile checks |
| Weather tab body controls in bottom sheet | `MapBottomSheetTabs` | removed | dead path removal | compile + UI tests |

### 2.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| Weather tab `More settings` button path in `WeatherTabContent` | two-step UI path to settings | direct RainViewer chip navigation | Phase 2-3 |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| RainViewer navigation action | N/A | routing-only, not time-derived |
| Bottom sheet visibility | N/A | UI state only |

Forbidden comparisons remain unchanged:
- Monotonic vs wall
- Replay vs wall

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - Main thread only for composable state and navigation callback dispatch.
- Primary cadence/gating sensor:
  - none (UI interaction only).
- Hot-path latency budget:
  - no new hot path; direct route should reduce tap path latency.

### 2.5 Replay Determinism

- Deterministic for same input: Yes (unchanged).
- Randomness used: No.
- Replay/live divergence rules: unchanged.

### 2.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| RainViewer still opens sheet instead of settings | MVVM/UDF UI behavior contract | unit test + review | `MapBottomSheetTabsTest` and/or `MapScreenContent` policy tests |
| Legacy weather sheet code not fully removed | maintainability/change safety | compile + review + dead-code cleanup | `MapBottomSheetTabs.kt` |
| Unused parameters remain and drift | clarity + SSOT | compile + unit | map UI tests + compiler |
| Architecture drift in nav boundary | ViewModel/UI boundary rules | review + enforceRules | changed UI files + `enforceRules` |

## 3) Data Flow (Before -> After)

Before:

`RainViewer chip tap -> MapScreenContent onTabSelected -> open bottom tab sheet -> Weather tab -> More settings -> onOpenWeatherSettingsFromTab -> SettingsRoutes.WEATHER_SETTINGS`

After:

`RainViewer chip tap -> MapBottomTabsLayer onRainViewerSelected -> MapScreenContent callback -> onOpenWeatherSettingsFromTab -> SettingsRoutes.WEATHER_SETTINGS`

Bottom-sheet flow remains for non-RainViewer tabs (`SkySight`, `Scia`, `Tab 4`) only.

## 4) Implementation Phases

### Phase 0 - Baseline and contract lock

- Goal:
  - lock scope and acceptance criteria before edits.
- Files to change:
  - this plan document.
- Tests to add/update:
  - none.
- Exit criteria:
  - plan accepted as production phased IP.

### Phase 1 - Tap-routing behavior change

- Goal:
  - route RainViewer bottom chip tap directly to `onOpenWeatherSettingsFromTab`, bypass bottom-sheet open.
- Files to change:
  - `MapScreenContent.kt`
- Tests to add/update:
  - map UI routing/policy tests verifying RainViewer tap path.
- Exit criteria:
  - RainViewer tap opens settings route callback directly.

### Phase 2 - Legacy weather sheet path removal

- Goal:
  - remove weather-tab body from bottom-sheet and related obsolete callbacks/props.
- Files to change:
  - `MapBottomSheetTabs.kt`
  - callsites/signatures in map UI files as needed.
- Tests to add/update:
  - update existing `MapBottomSheetTabsTest` for revised API/surface.
- Exit criteria:
  - no weather-tab sheet content remains; no `More settings` weather-tab path remains.

### Phase 3 - Dead code cleanup and hardening

- Goal:
  - remove unused imports/constants/functions/parameters created by legacy weather sheet removal.
  - preserve RainViewer enabled green border behavior.
- Files to change:
  - `MapBottomSheetTabs.kt`
  - any affected callsite or test files.
- Tests to add/update:
  - border cue regression test remains green.
  - no stale references to removed weather-tab APIs.
- Exit criteria:
  - clean compile with no dead-path references.

### Phase 4 - Verification and closure

- Goal:
  - run phased builds and full required verification; finalize plan evidence.
- Files to change:
  - this plan doc (status and evidence).
- Tests to add/update:
  - targeted map tests if regressions appear.
- Exit criteria:
  - all required checks pass and plan marked complete.

## 4A) Production Closure Evidence

1. Phase 0 - Baseline and contract lock
   - Completed.
   - Evidence:
     - This plan captured direct-open routing scope plus legacy weather-sheet removal boundaries before implementation.
2. Phase 1 - Tap-routing behavior change
   - Completed.
   - Evidence:
     - `MapBottomTabsLayer` now has dedicated `onRainViewerSelected` callback path.
     - `MapScreenContent` uses `onRainViewerSelected` to open settings directly and skip sheet-visible state.
3. Phase 2 - Legacy weather sheet path removal
   - Completed.
   - Evidence:
     - Removed old weather-tab controls from `MapBottomSheetTabs` (`Show rain overlay`, opacity slider, cycle toggle, `More settings` path).
     - Removed weather tab enum entry and weather-sheet callback parameters from `MapBottomTabsLayer`.
4. Phase 3 - Dead code cleanup and hardening
   - Completed.
   - Evidence:
     - Removed dead imports/constants/functions tied to weather sheet body in map bottom tabs.
     - Kept RainViewer enabled green-border policy (`resolveRainViewerBorderColor`).
     - Updated `MapBottomSheetTabsTest` to assert no legacy weather-sheet controls remain.
5. Phase 4 - Verification and closure
   - Completed.
   - Evidence:
     - `./gradlew :feature:map:compileDebugKotlin` passed.
     - `./gradlew :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.map.ui.MapBottomSheetTabsTest"` passed via `.\test-safe.bat` (Windows file-lock-safe wrapper).
     - `python scripts/arch_gate.py` passed.
     - `./gradlew enforceRules` passed.
     - `./gradlew testDebugUnitTest` passed via `.\test-safe.bat testDebugUnitTest` after transient file-lock cleanup failures in standard invocation.
     - `./gradlew assembleDebug` passed.

## 4B) Phase Production Status

| Phase | Status | Production Grade Notes |
|---|---|---|
| Phase 0 - Baseline and contract lock | Complete | Scope and architecture boundaries fixed before code edits |
| Phase 1 - Tap-routing behavior change | Complete | RainViewer now uses one-tap direct settings route |
| Phase 2 - Legacy weather sheet path removal | Complete | Old weather-tab sheet UI and two-step path removed |
| Phase 3 - Dead code cleanup and hardening | Complete | Removed obsolete weather-sheet plumbing while preserving enabled-border cue |
| Phase 4 - Verification and closure | Complete | Required verification completed (with documented Windows lock-safe test rerun) |

## 5) Test Plan

- Unit tests:
  - RainViewer tap path test (direct settings open behavior).
  - Bottom-sheet tab tests for remaining tabs (`SkySight`, `Scia`, `Tab 4`) unaffected.
  - RainViewer enabled-border policy regression test.
- Replay/regression tests:
  - Not applicable (no replay logic changes).
- UI/instrumentation tests (if needed):
  - optional compose interaction test for RainViewer chip tap-to-action behavior.
- Degraded/failure-mode tests:
  - Task panel visible/blocked scenarios should not regress non-RainViewer tab behavior.
- Boundary tests for removed bypasses:
  - ensure no weather tab `More settings` UI path remains.

Phased basic build after each implementation phase:

```bash
./gradlew :feature:map:compileDebugKotlin
```

Required checks:

```bash
python scripts/arch_gate.py
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| RainViewer tap still opens sheet due callback wiring regression | Medium | dedicated `onRainViewerSelected` callback + compose test coverage | XCPro Team |
| Removing weather sheet breaks function signatures across callsites | Medium | phase-by-phase compile gate and targeted tests | XCPro Team |
| Border cue regresses while removing weather controls | Low | keep dedicated border policy helper test | XCPro Team |
| User loses quick weather toggle/opacity from sheet | Low/intentional | direct to canonical settings path; document in release note if needed | XCPro Team |

## 7) Acceptance Gates

- RainViewer bottom-left tap opens main RainViewer settings directly.
- No legacy weather-tab sheet content remains.
- RainViewer enabled green border remains intact.
- No architecture rule violations.
- No duplicate SSOT ownership introduced.
- Required checks pass.
- `KNOWN_DEVIATIONS.md` unchanged.

## 8) Rollback Plan

- What can be reverted independently:
  - Phase 1 routing change can be reverted without restoring old weather tab body immediately.
  - Phase 2/3 cleanup can be reverted independently if UI regressions are found.
- Recovery steps if regression is detected:
  1. Revert most recent failing phase.
  2. Re-run `:feature:map:compileDebugKotlin` and targeted map tests.
  3. Re-apply with narrower diff and explicit regression test first.
