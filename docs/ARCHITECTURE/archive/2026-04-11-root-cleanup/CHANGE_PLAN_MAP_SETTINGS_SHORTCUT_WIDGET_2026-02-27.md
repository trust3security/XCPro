# CHANGE_PLAN_MAP_SETTINGS_SHORTCUT_WIDGET_2026-02-27.md

## 0) Metadata

- Title: Draggable Map Settings Shortcut Widget (General entry)
- Owner: XCPro map/ui
- Date: 2026-02-27
- Issue/PR: TBD
- Status: Implemented (verification in progress)

## 1) Scope

- Problem statement:
  - Map users need faster access to `General` settings without opening and navigating the drawer section manually.
  - Existing map movable widgets support drag/edit mode with persisted offsets, but no dedicated settings shortcut exists.
- Why now:
  - Requested by user workflow and should align with current movable widget UX.
- In scope:
  - Add one map settings shortcut icon widget.
  - Make it draggable via existing UI edit mode (enabled by hamburger long-press).
  - Persist its position through existing `MapWidgetLayout*` SSOT path.
  - Open existing `General` screen (`settings` route) on tap.
- Out of scope:
  - New settings screens/routes.
  - Drawer architecture changes.
  - New modal framework.
- User-visible impact:
  - A settings icon appears on MapScreen and opens `General`.
  - In edit mode, users can reposition it like other movable map widgets.

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Settings widget offset | `MapWidgetLayoutRepository` | `MapWidgetLayoutViewModel.offsets` (`MapWidgetOffsets.settings`) | Any separate persisted settings-offset store |
| Settings widget drag display offset | Widget composable local state (`remember`) | transient UI-only | Treating local display offset as persisted authority |

### 2.2 Dependency Direction

Confirmed unchanged:

`UI -> domain/use-case -> data`

- Modules/files touched:
  - `feature/map` map UI widgets and widget layout files.
- Any boundary risk:
  - Low. Reuses existing repository/use-case/viewmodel path for widget offsets.

### 2.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Settings shortcut offset persistence | N/A | `MapWidgetLayoutRepository` | Keep one offset SSOT path for all draggable map widgets | Unit tests + compile wiring |
| General-open entrypoint from map overlay | Drawer section only | Drawer section + map settings shortcut callback | Faster access with no duplicate settings screen | UI interaction test |

### 2.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| N/A | N/A | N/A | N/A |

### 2.3 Time Base

No new time-dependent domain values introduced.

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| Settings shortcut offset | N/A | Static UI position persisted as floats |

Forbidden comparisons remain unchanged:
- Monotonic vs wall
- Replay vs wall

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - unchanged; layout persistence remains existing shared-preferences path in repository.
- Primary cadence/gating sensor:
  - N/A (UI interaction only).
- Hot-path latency budget:
  - keep tap/open and drag updates immediate on UI thread.

### 2.5 Replay Determinism

- Deterministic for same input: Yes (unchanged replay behavior).
- Randomness used: No.
- Replay/live divergence rules:
  - none added.

### 2.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| New ad-hoc offset state owner | SSOT rules in `ARCHITECTURE.md` | review + unit tests | `MapWidgetLayout*` tests |
| UI bypasses existing General route | MVVM/UDF + layering | review + UI interaction test | map widget gesture test |
| Gesture collision regression | map gesture pass-through contract | test + review | widget interaction test + `MapGestureRegistry` diff |

## 3) Data Flow (Before -> After)

Before:

`Hamburger tap -> drawer -> Settings section -> General item -> navigate("settings")`

After:

`Settings widget tap -> navigate("settings")`

and for positioning:

`Settings widget drag (edit mode) -> onOffsetChange -> MapWidgetLayoutViewModel.updateOffset -> MapWidgetLayoutUseCase.saveOffset -> MapWidgetLayoutRepository`

## 4) Implementation Phases

### Phase 0 - Baseline
- Goal:
  - Confirm current widget offset and drag/edit architecture.
- Files to change:
  - none.
- Tests to add/update:
  - identify current widget gesture tests.
- Exit criteria:
  - baseline behavior documented in this plan.

### Phase 1 - Widget layout SSOT extension
- Goal:
  - Add settings widget offset to existing offset model + persistence.
- Files to change:
  - `MapWidgetLayoutModels.kt`
  - `MapWidgetLayoutRepository.kt`
  - `MapWidgetLayoutUseCase.kt`
  - `MapWidgetLayoutViewModel.kt`
  - `MapScreenRootHelpers.kt`
- Tests to add/update:
  - `MapWidgetLayoutUseCase` tests.
- Exit criteria:
  - settings offset persists/loads via same SSOT path as other widgets.

### Phase 2 - UI widget implementation and wiring
- Goal:
  - Add settings shortcut widget and wire through scaffold/content/overlay stack.
- Files to change:
  - `MapUIWidgets.kt`
  - new settings widget impl file
  - `MapOverlayStack.kt`
  - `OverlayActions.kt`
  - `MapScreenScaffoldInputs.kt`
  - `MapScreenScaffold.kt`
  - `MapScreenContent.kt`
- Tests to add/update:
  - widget interaction test for tap callback.
- Exit criteria:
  - settings icon appears and is draggable only in edit mode.

### Phase 3 - Navigation and gesture contract hardening
- Goal:
  - Route settings tap to existing `settings` screen and keep gesture target registry explicit.
- Files to change:
  - `MapGestureRegion.kt`
  - `WidgetSupport.kt`
  - scaffold/nav callback wiring files.
- Tests to add/update:
  - map widget interaction tests.
- Exit criteria:
  - tap opens existing General route; no duplicate UI routes/owners.

### Phase 4 - Docs and verification
- Goal:
  - Sync architecture docs and run required checks.
- Files to change:
  - `docs/ARCHITECTURE/PIPELINE.md`
- Tests to add/update:
  - none beyond prior phases.
- Exit criteria:
  - required verification commands pass.

## 5) Test Plan

- Unit tests:
  - `MapWidgetLayoutUseCase` settings offset defaults/load behavior.
- Replay/regression tests:
  - N/A (feature does not change replay path).
- UI/instrumentation tests:
  - existing map widget gesture compose test extended for settings icon tap.
- Degraded/failure-mode tests:
  - route not duplicated; tap callback only.
- Boundary tests for removed bypasses:
  - N/A.

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Offset model change breaks existing widget persistence | Medium | Keep backward-compatible key reads and only add new key | XCPro map/ui |
| Tap handling conflicts with map gestures | Medium | Register dedicated gesture region and keep z-order consistent | XCPro map/ui |
| Navigation drift from existing General entry | Low | Reuse existing `settings` route only | XCPro map/ui |

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`
- No duplicate SSOT ownership introduced
- Time base handling remains unchanged and explicit
- Replay behavior remains deterministic
- `KNOWN_DEVIATIONS.md` unchanged unless explicitly approved

## 8) Rollback Plan

- Revert settings widget files/wiring independently.
- Keep existing hamburger and drawer navigation unchanged.
- Remove added settings offset key usage if regression is detected.

## 9) Execution Notes (2026-02-27)

- Phase status:
  - Phase 1 complete
  - Phase 2 complete
  - Phase 3 complete
  - Phase 4 complete (with one environment blocker noted below)
- Verification results:
  - `./gradlew --no-daemon enforceRules` PASS
  - `./gradlew --no-daemon assembleDebug` PASS
  - Focused tests PASS:
    - `:feature:map:testDebugUnitTest --tests com.trust3.xcpro.map.widgets.MapWidgetLayoutUseCaseTest`
    - `:app:testDebugUnitTest --tests com.trust3.xcpro.MapOverlayWidgetGesturesTest`
  - `./gradlew --no-daemon testDebugUnitTest` BLOCKED by external concurrent Gradle run in another session repeatedly locking:
    - `feature/map/build/test-results/testDebugUnitTest/binary/output.bin`
