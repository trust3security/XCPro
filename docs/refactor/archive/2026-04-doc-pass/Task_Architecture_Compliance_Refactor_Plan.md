# Task Architecture Compliance Refactor Plan

Date: 2026-02-11
Status: Completed (closed 2026-02-11)
Owner: XCPro Team

Historical tracking issues (from `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md` baseline):
- RULES-20260211-01
- RULES-20260211-02
- RULES-20260211-03
- RULES-20260211-05
- RULES-20260211-07

Linked sub-plans:
- `docs/ARCHITECTURE/archive/2026-04-11-root-cleanup/CHANGE_PLAN_TASK_TOP_BAR_DROPDOWN.md` (UI container migration only; primarily RULES-20260211-02/03 subset)
- `docs/refactor/UI_Domain_Boundary_Compliance_Plan.md` (parallel boundary cleanup for non-task modules)

## Purpose
Create a refactor plan that brings the task system into compliance with:
- `docs/ARCHITECTURE/ARCHITECTURE.md`
- `docs/ARCHITECTURE/CODING_RULES.md`
- `docs/02_Tasks/Task_Type_Separation.md`

This plan is the recorded phased sequence used to complete task architecture compliance work.

## Scope
- Task management (AAT and Racing) architecture compliance.
- Remove Android/UI dependencies from domain logic.
- Establish SSOT and unidirectional data flow for tasks.
- Move MapLibre rendering and edit interactions into UI-only layers.
- Keep Racing and AAT completely isolated (no shared logic).

## Non-Goals
- No new task features or UI redesign.
- No behavior changes unless required for correctness.
- No cross-contamination between Racing and AAT.
- No changes to unrelated modules.

## Current Problems (Observed)
- ViewModel contains task business geospatial math/policy.
- Task Composables directly mutate/read coordinator/manager state.
- Non-UI managers use Compose runtime state primitives.
- ViewModel helper construction bypasses DI/factory boundaries.

## Target Architecture (Compliance)
Allowed dependency direction: UI -> domain -> data.

### Domain (pure, testable)
- `AATTaskEngine` and `RacingTaskEngine` own task state via `StateFlow`.
- Engines depend on pure calculators/validators and repository interfaces only.
- No Android, no MapLibre, no Compose runtime, no SharedPreferences.

### Data (persistence)
- Task persistence lives behind interfaces (AAT and Racing separately).
- Implementations use existing file IO/prefs classes but are injected.
- Coordinator stores task type via a repository, not SharedPreferences.

### UI
- Map rendering uses UI-only renderers (`AATMapRenderer`, `RacingMapRenderer`).
- Edit interactions use UI-only controllers bound to `MapLibreMap`.
- ViewModels expose task UI state derived from domain engines.

### SSOT
- Task UI state is sourced from a single repository/engine flow.
- No manual sync from ViewModel to multiple sources.

## Deviation to Phase Mapping

| Issue | Finding | Planned Resolution Phase |
|---|---|---|
| RULES-20260211-01 | Business math in `TaskSheetViewModel` | Resolved 2026-02-11 (Phase 6) |
| RULES-20260211-02 | Composables mutate via `TaskManagerCoordinator` | Resolved 2026-02-11 (Phase 6 + top-bar sub-plan) |
| RULES-20260211-03 | Composables read manager internals as state | Resolved 2026-02-11 (Phase 6 + top-bar sub-plan) |
| RULES-20260211-04 | Coordinator constructs persistence/managers directly | Resolved 2026-02-11 (Phase 3 and Phase 5) |
| RULES-20260211-05 | Compose runtime state in non-UI managers | Resolved 2026-02-11 (Phase 2) |
| RULES-20260211-06 | Use-case wrappers leak raw handles | Resolved 2026-02-11 (Phase 6) |
| RULES-20260211-07 | ViewModel constructs helper directly | Resolved 2026-02-11 (Phase 6) |

## Execution Order (Authoritative)

1. Complete phases in numeric order unless a blocking issue is documented.
2. If a sub-plan conflicts with this plan, this plan wins.
3. Every merged phase must update `KNOWN_DEVIATIONS.md` status/scope.
4. Do not mark an issue resolved without tests plus rule-check pass.

## Implementation Phases

### Phase 0 - Baseline + Safety Net
- Owner: XCPro Team
- Inventory all call sites using TaskManagerCoordinator and task managers.
- Add or expand tests for current behavior:
  - Add/remove/reorder waypoint
  - Task type switch
  - AAT target updates
  - Racing distance calculations
- Gate: no production behavior changes.
- Execution artifact: `docs/refactor/Task_Phase0_Baseline_And_Safety_Net_2026-02-11.md`

### Phase 1 - Define Domain Contracts
- Owner: XCPro Team
- Define pure domain interfaces for task engines and persistence.
- Define minimal task engine state models (no UI types).
- Gate: compiles, no wiring changes yet.
- Execution artifact: `docs/refactor/Task_Phase1_Domain_Contracts_2026-02-11.md`

### Phase 2 - Implement Pure Engines
- Owner: XCPro Team
- Create `AATTaskEngine` using existing AAT modules (geometry, navigation, validation).
- Create `RacingTaskEngine` using existing racing modules.
- Replace `mutableStateOf` with `StateFlow`.
- Gate: unit tests for engines pass.
- 2026-02-11 progress:
  non-UI task manager state migrated from Compose runtime to `MutableStateFlow` (`RacingTaskManager`, `AATTaskManager`, `AATNavigationManager`, and `AATEditModeManager`).
- Execution artifact: `docs/refactor/Task_Phase2_Pure_Engines_2026-02-11.md`

### Phase 3 - Data Layer Extraction
- Owner: XCPro Team
- Add `AATTaskPersistence` and `RacingTaskPersistence` interfaces.
- Implement adapters using `AATTaskFileIO`, `RacingTaskPersistence`, `RacingTaskStorage`.
- Add `TaskTypeSettingsRepository` for storing last task type.
- Gate: engines load/save through injected repositories.
- Execution artifact: `docs/refactor/Task_Phase3_Data_Extraction_2026-02-11.md`

### Phase 4 - UI Rendering and Editing Layer
- Owner: XCPro Team
- Move MapLibre rendering into UI-only renderers for AAT and Racing.
- Move edit overlays and hit testing into UI-only controllers.
- Keep AAT and Racing renderers isolated.
- Gate: map rendering matches current behavior.
- 2026-02-11 completion:
  task map rendering/edit routing remains UI/runtime-only through
  `TaskMapRenderRouter`, `MapGestureSetup`, and `MapTaskIntegration`;
  coordinator APIs in this path are map-agnostic.

### Phase 5 - Coordinator Refactor
- Owner: XCPro Team
- Refactor `TaskManagerCoordinator` into a pure router (no MapLibre, no Context).
- Expose flows and domain operations only.
- Remove map instance storage and UI operations.
- Gate: UI is updated to call renderers, not coordinator.
- 2026-02-11 interim progress:
  coordinator startup and named-task persistence routes are wired through injected `TaskEnginePersistenceService` as the primary runtime path.
- 2026-02-11 interim progress:
  DI now injects `RacingTaskManager` and `AATTaskManager` into coordinator construction.
- 2026-02-11 interim progress:
  coordinator map-instance ownership removed (`setMapInstance/getMapInstance` removed);
  task map rendering now routes through UI/runtime `TaskMapRenderRouter`.
- 2026-02-11 interim progress:
  coordinator AAT edit/target APIs are map-agnostic (no `MapLibreMap` parameters);
  UI runtime redraw now routes through `MapGestureSetup` / `MapTaskIntegration` via `TaskMapRenderRouter`.
- 2026-02-11 completion:
  coordinator no longer constructs managers/prefs or owns `Context`; construction is DI-driven only and compatibility access resolves the DI singleton via Hilt entry point.
- Execution artifact (interim): `docs/refactor/Task_Phase4_Coordinator_Persistence_Wiring_2026-02-11.md`

### Phase 6 - ViewModel and UI Wiring
- Owner: XCPro Team
- Update `TaskSheetViewModel` and Map task UI to read from engine flows.
- Replace direct manager access in overlays and screens.
- Gate: UI matches current behavior.
- 2026-02-11 progress:
  proximity geospatial policy moved from `TaskSheetViewModel` into domain `TaskProximityEvaluator` (via `TaskSheetUseCase` and `TaskRepository`).
- 2026-02-11 progress:
  task container UIs (`TaskTopDropdownPanel`, `SwipeableTaskBottomSheet`, `TaskSearchBarsOverlay`) now emit intents through `TaskSheetViewModel`/callbacks instead of direct coordinator mutation.
- 2026-02-11 progress:
  manager-internal reads removed from scoped Phase 6 files (`MapTaskScreenUi`, `AATManageList`, `CommonTaskComponents`, `BottomSheetState`) in favor of `TaskUiState` and VM operations.
- 2026-02-11 progress:
  use-case handle leaks removed from `MapScreenUseCases`/`MapScreenViewModel`; replay wiring now uses use-case factory APIs and VM no longer publishes raw manager/controller handles.
- 2026-02-11 progress:
  task manage/QR/point-selector composable APIs now consume `TaskUiState` + `TaskSheetViewModel`
  instead of `TaskManagerCoordinator` (top panel/sheet, Manage tab router, Racing/AAT manage content,
  waypoint selectors/lists, QR dialogs); racing distance UI now queries ViewModel/use-case bridges.
- 2026-02-11 progress:
  racing selector distance decision logic (optimal start-line vs segment distance) moved out of
  selector composables and into `TaskSheetViewModel`/`TaskSheetCoordinatorUseCase` APIs; selectors
  now render pre-resolved distance UI data.

### Phase 7 - Navigation + Replay Integration
- Owner: XCPro Team
- Update `TaskNavigationController` and replay code to use engine flows.
- Remove direct access to legacy manager internals.
- Gate: navigation behavior unchanged.
- 2026-02-11 progress:
  `TaskNavigationController` and `MapScreenReplayCoordinator` no longer read
  `RacingTaskManager.currentRacingTask` directly; both now consume coordinator
  core task snapshots via shared task-to-racing mapping.

### Phase 8 - Cleanup + Enforcement
- Owner: XCPro Team
- Remove legacy MapLibre APIs from task managers.
- Add CI checks to prevent Android/UI imports in task domain packages.
- Update docs and remove obsolete code paths.
- Gate: architecture compliance verified.
- 2026-02-11 progress:
  removed coordinator manager escape hatches (`getRacingTaskManager` / `getAATTaskManager`);
  task map rendering now consumes coordinator core-task snapshots via shared
  racing/AAT task mappers; `enforceRules` now blocks reintroduction of these APIs.
- 2026-02-11 progress:
  removed manager-level MapLibre render/edit APIs from `RacingTaskManager` and
  `AATTaskManager`; manager leg setters are now map-agnostic and `enforceRules`
  now blocks MapLibre imports and legacy map API reintroduction in these managers,
  plus Android/UI imports under `tasks/domain`.
- 2026-02-11 progress:
  `enforceRules` now blocks `TaskManagerCoordinator` type leakage in scoped task
  composable surfaces that are required to be `TaskUiState`/ViewModel-driven.
- 2026-02-11 progress:
  removed dead coordinator helper methods (`getTaskSpecificWaypoint`,
  deprecated `haversineDistance`) after migration of selector/distance consumers
  to `TaskUiState` + ViewModel/use-case APIs.
- 2026-02-11 progress:
  `enforceRules` now blocks direct coordinator-instance call patterns
  (`taskManager.` / `taskCoordinator.` / `coordinator.`) in scoped task
  composable surfaces, preventing UI-side bypass of ViewModel/use-case intent paths.
- 2026-02-11 progress:
  removed unreferenced legacy task overlay/persistence surfaces
  (`AATTaskMapOverlay`, `RacingTaskMapOverlay`,
  legacy `screens/overlays/MapOverlayManager`, and `TaskCoordinatorPersistence`).

## Testing Strategy
- Unit tests for engines, calculators, and validators.
- ViewModel tests for UI state transitions.
- Integration tests for task navigation and map overlay wiring.
- Replay tests if applicable.

## Migration Strategy
- Add a feature flag to switch between legacy and new engine paths.
- Migrate one UI surface at a time (overlay first, then task screens).
- Keep behavior parity during each phase.

## Definition of Done
- Task domain is Android-free and MapLibre-free.
- Single SSOT for task state.
- All UI rendering is isolated to UI layer.
- All tests pass and required checks are green.
- Docs and execution contract updated to reference this plan.
- All RULES-20260211-* entries are either resolved or explicitly re-scoped with owner/expiry.

## Closure
- Closed on: 2026-02-11
- Outcome:
  - All RULES-20260211-* items are resolved in `KNOWN_DEVIATIONS.md`.
  - Required verification commands pass on this branch:
    - `./gradlew enforceRules`
    - `./gradlew testDebugUnitTest`
    - `./gradlew assembleDebug`

## Files Likely to Change (Initial List)
- `feature/map/src/main/java/com/example/xcpro/tasks/TaskManagerCoordinator.kt`
- `feature/map/src/main/java/com/example/xcpro/tasks/TaskSheetViewModel.kt`
- `feature/map/src/main/java/com/example/xcpro/tasks/TaskRepository.kt`
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/AATTaskManager.kt`
- `feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingTaskManager.kt`
- `feature/map/src/main/java/com/example/xcpro/tasks/TaskMapOverlay.kt`
- `feature/map/src/main/java/com/example/xcpro/map/MapTaskScreenManager.kt`
- `feature/map/src/main/java/com/example/xcpro/tasks/TaskNavigationController.kt`

## Implementation Rule
Code changes must follow this phased plan unless a newer approved plan explicitly replaces it.

