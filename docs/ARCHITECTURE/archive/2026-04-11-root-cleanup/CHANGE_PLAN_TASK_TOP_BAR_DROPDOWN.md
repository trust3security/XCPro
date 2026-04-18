# CHANGE_PLAN_TASK_TOP_BAR_DROPDOWN.md

## Purpose

Plan the UI refactor that replaces the current task bottom sheet with a top-anchored task bar that expands downward on MapScreen, while preserving existing task domain behavior and architecture constraints.

Read first:

1. `docs/ARCHITECTURE/ARCHITECTURE.md`
2. `docs/ARCHITECTURE/CODING_RULES.md`
3. `docs/ARCHITECTURE/PIPELINE.md`
4. `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`

## 0) Metadata

- Title: Map Task UI Refactor - Bottom Sheet to Top Drop-Down Panel
- Owner: XCPro Team
- Date: 2026-02-11
- Issue/PR: RULES-20260211-02, RULES-20260211-03 (subset)
- Status: Active sub-plan (UI track)

### 0.0 Plan Linkage

- Historical parent compliance plan:
  `docs/refactor/archive/2026-04-doc-pass/Task_Architecture_Compliance_Refactor_Plan.md`
- This sub-plan is limited to UI container/state interactions.
- Parent plan owns full closure for all RULES-20260211-* architecture issues.

### 0.1 Locked UX Decisions

- Keep `EXPANDED_PARTIAL` in the state model; do not simplify to only collapsed/full.
- Top-panel visual and motion baseline must match current `SwipeableTaskBottomSheet` in first implementation pass (anchor/origin changes only):
  - Collapsed/minimized height: `120.dp`
  - Partial/half-expanded height: `400.dp`
  - Full-expanded height: `screenHeight * 0.95f`
  - Drag handle: centered, `40.dp x 4.dp`, vertical padding `8.dp`, on-surface color at `alpha = 0.3f`, rounded corners `2.dp`
  - Container style: `Card` with `MaterialTheme.colorScheme.surface`
  - Category UI: keep `PrimaryScrollableTabRow` and existing tab/category structure (Manage / Rules / Files / placeholders)
  - Gesture thresholds: keep `swipeThreshold = 50f` and dismiss threshold `150f` semantics, adapted for top-anchored direction
  - State snap behavior: keep nearest-state snapping and the existing `+/- 50f` visual state cutoffs
- Layering baseline:
  - Task top panel must render above map action FABs (current FAB stack reaches `zIndex(60f)`).
  - Modal overlays must render above task panel, or opening a modal must hide task panel first.

## 1) Scope

- Problem statement:
  The task UI currently uses a bottom-anchored sheet (`SwipeableTaskBottomSheet`) that conflicts with desired UX. We need a top bar that drops down while retaining existing manage/rules/files content and task behavior.
- Why now:
  Task workflows are increasingly centered around quick top-of-map interaction and reduced bottom occlusion.
- Pipeline impact expectation:
  No sensor/fusion/replay pipeline rewiring is expected; this is a task UI surface refactor.
- In scope:
  Replace task container presentation in MapScreen from bottom sheet to top drop-down panel.
  Preserve existing TaskSheetViewModel, TaskManagerCoordinator, and tab content logic.
  Preserve drawer entrypoint (`Add Task`) and minimized/collapsed task access behavior.
  Remove legacy `showTaskScreen` and dead manager-level `"task"` handling so no dead or no-op task UI state remains.
  Define and enforce back-press precedence between map modal overlays and task panel.
  Resolve overlap/z-index between new top panel and existing top UI widgets.
  Update tests and docs for the new interaction model.
- Out of scope:
  Task domain logic changes (Racing/AAT calculations, validation, persistence).
  Navigation algorithm changes.
  Replay pipeline changes.
  Broad UI redesign outside task panel area.
  Full architecture-compliance closure for RULES-20260211-* (handled by parent plan).
- User-visible impact:
  Task editor opens from top and expands downward instead of from bottom.
  Collapsed task state appears as a top bar and expands in place.
  Existing task tabs and actions continue to work.

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Task definition (waypoints, type, active leg) | `TaskManagerCoordinator` + task managers | existing APIs/flows | Any parallel task copy in Compose state |
| Task sheet UI content state (`TaskUiState`) | `TaskSheetViewModel` | `uiState: StateFlow<TaskUiState>` | Recomputed task business state in UI |
| Task panel visibility/collapse state | `MapTaskScreenManager` | single authoritative `StateFlow<TaskPanelState>` (with derived read-only helpers only) | Parallel `show*` booleans that can diverge from panel state |
| Map widget offsets | `MapWidgetLayoutViewModel` | `offsets` flow | Hard-coded compensating offsets in task panel code |

### 2.2 Dependency Direction

Dependency flow remains:

`UI -> domain -> data`

- Modules/files touched:
  `feature/map/src/main/java/com/trust3/xcpro/map/ui/task/*`
  `feature/map/src/main/java/com/trust3/xcpro/map/MapTaskScreenManager.kt`
  `feature/map/src/main/java/com/trust3/xcpro/tasks/*` (presentation container only)
  `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenContent.kt`
  `feature/map/src/main/java/com/trust3/xcpro/map/components/MapActionButtons.kt`
  tests under `feature/map/src/test/java/com/trust3/xcpro/map/ui/task/*`
- Boundary risk:
  Pulling task domain logic into new top panel composables while refactoring layout.
  Mitigation: keep container refactor isolated; re-use existing tab content composables and ViewModel.

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| Task panel expand/collapse animation timing | Monotonic (Compose frame clock, UI-only) | Pure visual behavior; no domain influence |

Non-time-dependent behavior:

- AAT edit camera fit occlusion is geometry-based and has no timebase dependency.

Explicitly forbidden comparisons:

- Monotonic vs wall
- Replay vs wall

### 2.4 Threading and Cadence

- Dispatcher ownership (`Main` / `Default` / `IO`):
  Main only for panel animation/visibility/rendering.
  Existing domain dispatchers remain unchanged.
- Primary cadence/gating sensor:
  None for this refactor; task panel cadence is UI-driven.
- Hot-path latency budget:
  Panel open/close should feel immediate (< 100 ms to first visible frame on typical devices).
- Enforcement note:
  No domain/fusion time logic changes are planned; `enforceRules` must continue to pass with no new timebase violations.

### 2.5 Replay Determinism

- Deterministic for same input: Yes (no replay logic changes).
- Randomness used: No.
- Replay/live divergence rules:
  No change; UI container move must not alter replay data, task advancement, or map source gating.

### 2.6 Task Panel State Machine

States:

- `HIDDEN`
- `COLLAPSED` (top bar visible, content hidden)
- `EXPANDED_PARTIAL` (drop-down opened to partial height)
- `EXPANDED_FULL` (drop-down opened to full height)

Decision lock:

- `EXPANDED_PARTIAL` is a required state and must remain in implementation and tests.

Transitions:

| From | Event | To | Owner |
|---|---|---|---|
| `HIDDEN` | Drawer `Add Task` selected | `EXPANDED_PARTIAL` | `MapTaskScreenManager` |
| `EXPANDED_PARTIAL` | Drawer `Add Task` selected | `HIDDEN` | `MapTaskScreenManager` |
| `EXPANDED_FULL` | Drawer `Add Task` selected | `HIDDEN` | `MapTaskScreenManager` |
| `COLLAPSED` | Drawer `Add Task` selected | `EXPANDED_PARTIAL` | `MapTaskScreenManager` |
| `HIDDEN` | Minimized/collapsed bar tapped (guard: waypoints > 0) | `EXPANDED_FULL` | `MapTaskScreenManager` |
| `EXPANDED_PARTIAL` | Expand gesture/button | `EXPANDED_FULL` | `MapTaskScreenManager` |
| `EXPANDED_FULL` | Collapse gesture/button (guard: waypoints > 0) | `COLLAPSED` | `MapTaskScreenManager` |
| `EXPANDED_FULL` | Collapse gesture/button (guard: waypoints = 0) | `HIDDEN` | `MapTaskScreenManager` |
| `EXPANDED_PARTIAL` | Dismiss gesture/button | `HIDDEN` | `MapTaskScreenManager` |
| `EXPANDED_FULL` | Dismiss gesture/button | `HIDDEN` | `MapTaskScreenManager` |
| `EXPANDED_PARTIAL` | Back | `HIDDEN` | `MapTaskScreenManager` |
| `EXPANDED_FULL` | Back | `HIDDEN` | `MapTaskScreenManager` |
| `COLLAPSED` | Expand tap (guard: waypoints > 0) | `EXPANDED_FULL` | `MapTaskScreenManager` |
| `COLLAPSED` | Close action / back | `HIDDEN` | `MapTaskScreenManager` |

Global back precedence contract (outside panel state machine):

1. `MapModalManager` handles back first when a modal is visible.
2. Task panel handles back second (per transitions above).
3. Map screen/system navigation handles back only when (1) and (2) do not consume it.

Implementation requirement:

- This precedence must be wired via explicit Compose `BackHandler` integration (currently not wired in map UI code paths).

### 2.7 UI Interaction Invariants

- Collapsed top bar visibility must remain deterministic:
  visible only when task has waypoints and panel state is not expanded.
- Empty-task behavior must remain usable:
  panel can open with zero waypoints and still exposes waypoint-add actions.
- Pointer/gesture isolation:
  taps/drags inside task panel must not leak to map gesture handlers underneath.
- Card-layer coexistence:
  top panel must not break card drag/edit interactions outside panel bounds.
- Modal vs panel precedence (visual + input):
  when any map modal is open, modal UI must be above the task panel, or the task panel must be force-hidden; mixed visible state where panel obscures modal is forbidden.

## 3) Data Flow (Before -> After)

Before:

```
Drawer "Add Task"
  -> MapTaskScreenManager.showTaskBottomSheet()
  -> MapTaskScreenUi.TaskBottomSheet()
  -> SwipeableTaskBottomSheet (bottom-anchored, draggable)
  -> ManageBTTabRouter / RulesBTTab / FilesBTTab
  -> TaskSheetViewModel / TaskManagerCoordinator
```

After:

```
Drawer "Add Task"
  -> MapTaskScreenManager.showTaskPanel()
  -> MapTaskScreenUi.TaskTopDropdownPanel()
  -> Top-anchored panel (collapsed bar + downward expansion)
  -> ManageBTTabRouter / RulesBTTab / FilesBTTab (reused)
  -> TaskSheetViewModel / TaskManagerCoordinator (unchanged)
```

Layer-correct authoritative flow (must remain unchanged):

```
TaskManagerCoordinator (state owner/SSOT facade)
  -> TaskSheetUseCase
  -> TaskSheetViewModel
  -> MapTaskScreenUi (top panel render only)
```

Legacy path cleanup scope:

- Remove dead `"task"` branch handling in `MapTaskScreenManager.handleNavigationTaskSelection`.
- This plan does not include deleting standalone app navigation route `"task"` in `AppNavGraph` unless separately approved.

## 4) Implementation Phases

Phase ownership:

- Phase 1: Map/Task UI maintainers
- Phase 2: Map/Task UI maintainers
- Phase 3: Map UI integration maintainers
- Phase 4: Map camera + AAT maintainers
- Phase 5: Documentation owner + feature owner

### Phase 1 - State Contract and Naming

- Goal:
  Introduce panel-state naming that supports top drop-down behavior without breaking existing callers.
- Owner: Map/Task UI maintainers (XCPro Team)
- Files to change:
  `feature/map/src/main/java/com/trust3/xcpro/map/MapTaskScreenManager.kt`
  `feature/map/src/main/java/com/trust3/xcpro/tasks/BottomSheetState.kt` (or replacement enum/file)
  `feature/map/src/main/java/com/trust3/xcpro/map/components/MapActionButtons.kt` (remove `showTaskScreen` dependency)
  `feature/map/src/main/java/com/trust3/xcpro/map/ui/task/MapTaskScreenUi.kt` (remove no-op search overlay hook)
  `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenScaffoldInputs.kt` (keep `add_task` entrypoint semantics aligned with new transition table)
- Tests to add/update:
  New/updated unit tests for state transitions (open, collapse, expand, dismiss).
- Exit criteria:
  Manager API exposes a single authoritative panel-state flow; any boolean visibility accessors are derived only.
  Compatibility shim for existing bottom-sheet method calls is defined (temporary) without creating duplicate state owners.
  State machine transitions are implemented exactly as specified in section 2.6.
  Legacy `showTaskScreen` state and `showTaskSearch()/hideTaskSearch()` APIs are removed.

### Phase 2 - Top Panel Container Implementation

- Goal:
  Replace `SwipeableTaskBottomSheet` container with a top drop-down container while reusing tab content, and keep visual/motion parity with current bottom-sheet values as the baseline.
- Owner: Map/Task UI maintainers (XCPro Team)
- Files to change:
  `feature/map/src/main/java/com/trust3/xcpro/map/ui/task/MapTaskScreenUi.kt`
  `feature/map/src/main/java/com/trust3/xcpro/tasks/SwipeableTaskBottomSheet.kt` (either refactor or replace with top-panel composable)
  `feature/map/src/main/java/com/trust3/xcpro/tasks/TaskSearchBarsOverlay.kt` (remove as obsolete, or keep only if still used elsewhere)
  optional new file: `feature/map/src/main/java/com/trust3/xcpro/tasks/TaskTopDropdownPanel.kt`
- Tests to add/update:
  `feature/map/src/test/java/com/trust3/xcpro/map/ui/task/MapTaskScreenUiTest.kt` visibility tags and behavior.
  Add tests for empty-task open state and add-waypoint affordance visibility.
  Add tests that panel gestures consume input within panel bounds only.
- Exit criteria:
  Task panel renders top-anchored, opens downward, and tab content remains functional.
  Top-panel heights/style/gesture tuning match section 0.1 baseline in first pass.
  Affected composables collect flows with lifecycle-aware APIs (`collectAsStateWithLifecycle`).

### Phase 3 - Map Overlay Integration and Collision Resolution

- Goal:
  Resolve conflicts with existing top UI components and gesture layering.
- Owner: Map UI integration maintainers (XCPro Team)
- Files to change:
  `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenScaffold.kt` (GPS banner spacing/insets if needed)
  `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapOverlayStack.kt` (z-index ordering if needed)
  `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenSections.kt` (card layer coexistence/inset handling if needed)
  `feature/map/src/main/java/com/trust3/xcpro/map/components/MapActionButtons.kt` (visibility/z-index assumptions tied to bottom sheet)
  `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenContent.kt`
  `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenRoot.kt` (explicit back handling integration)
  `feature/map/src/main/java/com/trust3/xcpro/map/MapModalManager.kt` (back consume integration point)
  `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenSideEffects.kt` (if back-handling effect routing is required)
- Tests to add/update:
  Compose tests for top bar visibility with GPS banner present and with active top widgets.
  Compose test for modal/panel precedence (modal visible while task panel requested).
- Exit criteria:
  No overlap regression with hamburger, flight mode menu, ballast panel, or GPS banner in common layouts.
  Explicit z-index/inset contract is documented in code comments and reflected in tests.
  Back action behavior is explicitly wired and matches the state machine in section 2.6.
  Back precedence matches the global contract in section 2.6.
  Modal/panel visual precedence invariant from section 2.7 is satisfied.

### Phase 4 - AAT Camera/Occlusion Adjustment

- Goal:
  Remove bottom-sheet-only camera-fit assumptions and align with top occlusion model.
- Owner: Map camera + AAT maintainers (XCPro Team)
- Files to change:
  `feature/map/src/main/java/com/trust3/xcpro/map/MapCameraManager.kt`
  `feature/map/src/main/java/com/trust3/xcpro/map/MapGestureSetup.kt` (if occlusion parameter plumbing changes)
- Tests to add/update:
  Unit test(s) for zoom-fit calculation inputs if available.
  Manual validation script for AAT edit entry/exit with top panel shown.
- Exit criteria:
  Bottom-specific API naming (`bottomSheetHeightPx`) is replaced by panel-agnostic occlusion naming and call sites are migrated.
  AAT edit mode camera fit remains usable and deterministic with panel at top.

### Phase 5 - Documentation and Cleanup

- Goal:
  Align docs and remove obsolete bottom-sheet terminology.
- Owner: Documentation owner + feature owner (XCPro Team)
- Files to change:
  `docs/ARCHITECTURE/PIPELINE.md` (if wiring/ownership terms changed)
  `docs/03_Features/Racing_Task_Navigation_EXISTING_UI.md`
  `docs/02_Tasks/AAT_PIN_DRAGGING_IMPLEMENTATION.md`
  any task UI docs that explicitly describe bottom sheet behavior
- Tests to add/update:
  None beyond previous phases.
- Exit criteria:
  Docs reflect actual UI behavior and no stale bottom-sheet assumptions remain.
  Temporary compatibility shims/deprecated bottom-sheet naming are removed or explicitly tracked with a time-boxed follow-up.

## 5) Test Plan

- Unit tests:
  `MapTaskScreenManager` transition tests for open/collapse/expand/dismiss behavior.
  Any extracted panel-state reducer tests if introduced.
  If any time-dependent animation/state helper is introduced outside Compose defaults, test with `TestDispatcher` and fake time source.
- Replay/regression tests:
  Run existing replay tests; verify no change in task/replay behavior.
  Determinism gate: replay the same IGC twice and verify identical task/navigation outputs (existing replay harness or new focused test).
- UI/instrumentation tests (if needed):
  Add dedicated `MapTaskScreenManager` unit test file (new) covering full transition matrix including `Add Task` from all states.
  Update `MapTaskScreenUiTest` tags/expectations for top panel.
  Add test for collapsed top bar showing when task exists and panel is hidden.
  Add test for collapsed top bar hidden when task has zero waypoints.
  Add test for drawer `Add Task` opening top panel.
  Add regression test/assertion that legacy `"task"` path and `showTaskScreen` API do not exist/reappear.
  Add tests for the full panel state machine transition matrix in section 2.6.
  Add back-navigation behavior test (back closes panel before leaving map screen).
  Add back precedence test: modal open + back should close modal before task panel.
  Add test that `BackHandler` precedence is active in-map (modal > panel > system).
- Degraded/failure-mode tests:
  Small screen + large font scale layout.
  GPS banner visible while task panel opens.
  User-customized top widget offsets (hamburger/flight mode/ballast) with panel open.
  Card edit mode and drag interactions while panel is collapsed/expanded.
  AAT edit mode entry/exit while panel is expanded/collapsed.

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
| Top panel overlaps GPS banner/top widgets | High UX regression | Define explicit top insets and z-index contract; test on small screens | XCPro Team |
| Gesture interception regressions with map interactions | High input regression | Keep gesture handler unchanged; limit panel pointer handling to panel bounds | XCPro Team |
| Hidden coupling to bottom-sheet semantics in existing code | Medium runtime bugs | Keep temporary compatibility API and migrate callsites incrementally | XCPro Team |
| AAT camera zoom fit still assumes bottom occlusion | Medium usability regression | Update occlusion model and revalidate AAT edit flow | XCPro Team |
| Test fragility due renamed tags/components | Low | Update tags once and keep stable test IDs for top panel and collapsed bar | XCPro Team |

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`.
- No duplicate SSOT ownership introduced.
- Task panel state has a single authoritative owner (no diverging boolean + enum panel state).
- Task domain logic remains outside UI layer.
- Compose flow collection in affected UI remains lifecycle-aware (`collectAsStateWithLifecycle`).
- No UI framework types or persistence access added to ViewModels.
- `./gradlew enforceRules` passes with no new timebase/DI/ViewModel purity/lifecycle violations.
- Replay behavior remains deterministic and unchanged.
- Global back precedence is deterministic and documented (modal > task panel > system).
- Global back precedence is implemented (not doc-only), with explicit `BackHandler` wiring.
- Top panel works on common phone sizes without blocking core top controls.
- No stale dead-state hooks remain (`showTaskScreen`, orphaned no-op search overlay paths).
- Dead legacy manager branch for `"task"` item is removed from `MapTaskScreenManager`.
- Panel/map gesture isolation is validated (no accidental map tap/drag from panel interaction).
- Card interactions remain functional outside panel bounds.
- Modal/panel visual precedence invariant is met.
- `KNOWN_DEVIATIONS.md` updated only if explicitly approved (issue, owner, expiry).

## 8) Rollback Plan

- What can be reverted independently:
  Revert panel container and `MapTaskScreenUi` to bottom sheet while retaining task content logic.
  Revert manager naming changes via compatibility methods.
  Revert docs after code rollback.
- Recovery steps if regression is detected:
  1. Re-enable prior bottom-sheet container path.
  2. Re-run required checks (`enforceRules`, unit tests, assemble).
  3. Validate critical task flows manually (Add Task, edit waypoints, minimized/collapsed behavior).
  4. Re-open plan with narrowed scope and re-apply in smaller phases.
