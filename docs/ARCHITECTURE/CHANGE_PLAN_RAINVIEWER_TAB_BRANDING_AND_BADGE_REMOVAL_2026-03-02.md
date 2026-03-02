# CHANGE_PLAN_RAINVIEWER_TAB_BRANDING_AND_BADGE_REMOVAL_2026-03-02.md

## 0) Metadata

- Title: RainViewer Bottom Tab Branding, Enabled Border Signal, and Rain Live Badge Removal
- Owner: XCPro Team
- Date: 2026-03-02
- Issue/PR: RAIN-20260302-03
- Status: Complete

## 1) Scope

- Problem statement:
  - The map bottom tab still shows `Weather` instead of the product name `RainViewer`.
  - The enabled state for rain overlay is not obvious at a glance on the bottom tab strip.
  - A top-right `Rain Live` confidence chip is currently shown and should be removed per product request.
- Why now:
  - Product direction is to simplify weather overlay affordances and keep one clear control surface.
- In scope:
  - Rename bottom weather tab/button label to `RainViewer`.
  - Keep weather tab content naming aligned to `RainViewer`.
  - Add a very thin green border to the RainViewer tab chip when overlay is enabled.
  - Remove top-right `Rain Live` confidence chip from map content.
  - Add/update tests for label and visual policy wiring.
- Out of scope:
  - Weather metadata fetching, frame selection policy, or animation logic changes.
  - Replay, sensor fusion, variometer, or forecast/SkySight behavior changes.
- User-visible impact:
  - Bottom tab reads `RainViewer`.
  - RainViewer tab has a thin green border when enabled.
  - Top-right `Rain Live` badge no longer appears.

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Rain overlay enabled state | `WeatherOverlayPreferencesRepository` | `WeatherOverlayViewModel.overlayState.enabled` | Compose-local state as persistent owner |
| Bottom tab selected state | `MapScreenContent` UI state | `selectedBottomTabName` | repository/domain ownership |
| Rain map confidence status | weather domain runtime state | `WeatherOverlayRuntimeState` | UI-only recomputation of status policy |

### 2.2 Dependency Direction

`UI -> domain/use-cases -> data` remains unchanged.

- Modules/files touched:
  - `feature/map/src/main/java/com/example/xcpro/map/ui/*`
  - `feature/map/src/test/java/com/example/xcpro/map/ui/*`
  - this plan doc
- Boundary risk:
  - Avoid introducing business logic into Compose while adding visual state cue.

### 2.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| None | N/A | N/A | UI-only presentation update | UI tests + compile checks |

### 2.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| None | N/A | N/A | N/A |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| Rain overlay enabled state | N/A (boolean preference state) | not time-derived |
| Rain confidence chip visibility | Existing runtime status (wall-derived upstream) | this change removes the UI surface only |

Forbidden comparisons remain unchanged:
- Monotonic vs wall
- Replay vs wall

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - Main thread Compose rendering only for this change.
- Primary cadence/gating sensor:
  - None changed.
- Hot-path latency budget:
  - No additional hot-path work introduced.

### 2.5 Replay Determinism

- Deterministic for same input: Yes (unchanged).
- Randomness used: No.
- Replay/live divergence rules: unchanged.

### 2.6 Enforcement Coverage

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| Label regresses to `Weather` in tab strip | UI rendering policy | unit UI test | `MapBottomSheetTabsTest` |
| Enabled border cue lost | UI state rendering | unit UI/policy test | `MapBottomSheetTabsTest` |
| Top-right rain chip reintroduced accidentally | UI layering | review + compile gate | `MapScreenContent.kt` |
| Architecture drift from UI-only scope | ARCHITECTURE/CODING_RULES | enforceRules + unit + assemble | Gradle gates |

## 3) Data Flow (Before -> After)

Before:

`WeatherOverlayPreferencesRepository -> WeatherOverlayViewModel.overlayState.enabled -> MapBottomTabsLayer + WeatherMapConfidenceChip`

After:

`WeatherOverlayPreferencesRepository -> WeatherOverlayViewModel.overlayState.enabled -> MapBottomTabsLayer (RainViewer label + enabled border cue)`

(`WeatherMapConfidenceChip` remains as reusable UI component but is removed from map top-right composition path.)

## 4) Implementation Phases

### Phase 0 - Plan lock and baseline

- Goal:
  - Document scope, contracts, and gates before edits.
- Files to change:
  - this plan file
- Tests to add/update:
  - none
- Exit criteria:
  - plan status `In progress` and phase checklist defined

### Phase 1 - RainViewer branding in map bottom tab surfaces

- Goal:
  - Rename bottom tab label and weather tab header text to `RainViewer`.
- Files to change:
  - `MapBottomSheetTabs.kt`
- Tests to add/update:
  - `MapBottomSheetTabsTest`
- Exit criteria:
  - bottom tab and sheet content show `RainViewer`

### Phase 2 - Enabled border cue

- Goal:
  - Add thin green border around RainViewer tab chip when overlay is enabled.
- Files to change:
  - `MapBottomSheetTabs.kt`
- Tests to add/update:
  - `MapBottomSheetTabsTest` (enabled/disabled border assertions via semantics or direct policy helper)
- Exit criteria:
  - weather enabled state visibly signaled by green thin chip border

### Phase 3 - Remove top-right Rain Live badge

- Goal:
  - Remove `WeatherMapConfidenceChip` from `MapScreenContent` top-right composition.
- Files to change:
  - `MapScreenContent.kt`
- Tests to add/update:
  - none required unless an existing test references this composition path
- Exit criteria:
  - no top-right rain confidence chip rendered from map content

### Phase 4 - Hardening and verification

- Goal:
  - Repass, update this plan evidence, and run required verification checks.
- Files to change:
  - this plan file (status + evidence)
- Tests to add/update:
  - targeted map UI tests as needed
- Exit criteria:
  - phased compile checks complete and required gates pass

## 4A) Production Closure Evidence

1. Phase 0 - Plan lock and baseline
   - Completed.
   - Evidence:
     - This plan established SSOT, dependency, timebase, and verification gates before edits.
2. Phase 1 - RainViewer branding
   - Completed.
   - Evidence:
     - `MapBottomTab.WEATHER` label updated from `Weather` to `RainViewer`.
     - Weather tab sheet header text updated to `RainViewer`.
3. Phase 2 - Enabled border cue
   - Completed.
   - Evidence:
     - Thin `1.dp` border applied to bottom tab chips.
     - RainViewer chip uses green border when weather overlay is enabled via `resolveBottomTabBorderColor(...)`.
     - Phase compile gate: `./gradlew :feature:map:compileDebugKotlin` passed.
4. Phase 3 - Top-right badge removal
   - Completed.
   - Evidence:
     - `WeatherMapConfidenceChip(...)` removed from `MapScreenContent` top-right composition.
     - Phase compile gate: `./gradlew :feature:map:compileDebugKotlin` passed.
5. Phase 4 - Hardening and verification
   - Completed.
   - Evidence:
     - Tests updated in `MapBottomSheetTabsTest` for RainViewer label and border policy helper.
     - Phase compile gate: `./gradlew :feature:map:compileDebugKotlin` passed.
     - Full required gates:
       - `python scripts/arch_gate.py` -> pass
       - `./gradlew enforceRules` -> pass
       - `./gradlew testDebugUnitTest` -> pass
       - `./gradlew assembleDebug` -> pass

## 4B) Phase Production Status

| Phase | Status | Production Grade Notes |
|---|---|---|
| Phase 0 - Plan lock and baseline | Complete | Contract and acceptance gates were fixed before implementation |
| Phase 1 - RainViewer branding | Complete | User-visible naming updated consistently on map bottom tab surfaces |
| Phase 2 - Enabled border cue | Complete | Visual enabled signal is deterministic and covered by policy test |
| Phase 3 - Top-right badge removal | Complete | Rain status chip removed from map composition without runtime coupling changes |
| Phase 4 - Hardening and verification | Complete | Required quality gates passed and phase evidence recorded |

## 5) Test Plan

- Unit tests:
  - map bottom tabs compose tests for `RainViewer` label presence.
  - map bottom tabs tests for enabled visual cue policy (green border) and disabled fallback.
- Replay/regression tests:
  - not applicable (no replay logic changes).
- UI/instrumentation tests:
  - not required for this UI text/style change unless requested.
- Degraded/failure-mode tests:
  - none (presentation-only change).
- Boundary tests for removed bypasses:
  - none.

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Phased basic checks:

```bash
./gradlew :feature:map:compileDebugKotlin
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Green border contrast too weak in some themes | Low | Use explicit strong green tone and thin 1dp stroke | XCPro Team |
| Removing chip reduces quick status visibility | Low | Keep overlay toggle + settings status surfaces available | XCPro Team |
| Test fragility on visual assertions | Medium | Prefer deterministic tags/properties over pixel comparisons | XCPro Team |

## 7) Acceptance Gates

- No architecture violations from `ARCHITECTURE.md` and `CODING_RULES.md`.
- No duplicate SSOT ownership introduced.
- No domain/data logic moved into UI.
- Required checks pass.
- `KNOWN_DEVIATIONS.md` unchanged.

## 8) Rollback Plan

- What can be reverted independently:
  - Phase 3 (badge removal) can be reverted without affecting tab branding/border.
  - Phase 2 (border cue) can be reverted independently if styling issues are found.
- Recovery steps:
  1. Revert offending phase change.
  2. Re-run required checks.
  3. Reopen with focused test and UX criteria.
