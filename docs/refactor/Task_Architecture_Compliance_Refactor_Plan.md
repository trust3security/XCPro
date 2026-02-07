# Task Architecture Compliance Refactor Plan

Date: 2026-02-06
Status: Draft (plan only, no implementation yet)

## Purpose
Create a refactor plan that brings the task system into compliance with:
- `docs/ARCHITECTURE/ARCHITECTURE.md`
- `docs/ARCHITECTURE/CODING_RULES.md`
- `docs/02_Tasks/Task_Type_Separation.md`

This plan is required before any implementation work begins.

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
- Task managers depend on Android/UI types (Context, MapLibre, Compose state).
- Task state is duplicated between legacy managers and `TaskRepository`.
- Coordinator holds MapLibre references and performs UI actions.
- Map rendering and edit operations live in non-UI layers.
- Persistence is embedded in managers/coordinator rather than repositories.

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

## Implementation Phases

### Phase 0 - Baseline + Safety Net
- Inventory all call sites using TaskManagerCoordinator and task managers.
- Add or expand tests for current behavior:
  - Add/remove/reorder waypoint
  - Task type switch
  - AAT target updates
  - Racing distance calculations
- Gate: no production behavior changes.

### Phase 1 - Define Domain Contracts
- Define pure domain interfaces for task engines and persistence.
- Define minimal task engine state models (no UI types).
- Gate: compiles, no wiring changes yet.

### Phase 2 - Implement Pure Engines
- Create `AATTaskEngine` using existing AAT modules (geometry, navigation, validation).
- Create `RacingTaskEngine` using existing racing modules.
- Replace `mutableStateOf` with `StateFlow`.
- Gate: unit tests for engines pass.

### Phase 3 - Data Layer Extraction
- Add `AATTaskPersistence` and `RacingTaskPersistence` interfaces.
- Implement adapters using `AATTaskFileIO`, `RacingTaskPersistence`, `RacingTaskStorage`.
- Add `TaskTypeSettingsRepository` for storing last task type.
- Gate: engines load/save through injected repositories.

### Phase 4 - UI Rendering and Editing Layer
- Move MapLibre rendering into UI-only renderers for AAT and Racing.
- Move edit overlays and hit testing into UI-only controllers.
- Keep AAT and Racing renderers isolated.
- Gate: map rendering matches current behavior.

### Phase 5 - Coordinator Refactor
- Refactor `TaskManagerCoordinator` into a pure router (no MapLibre, no Context).
- Expose flows and domain operations only.
- Remove map instance storage and UI operations.
- Gate: UI is updated to call renderers, not coordinator.

### Phase 6 - ViewModel and UI Wiring
- Update `TaskSheetViewModel` and Map task UI to read from engine flows.
- Replace direct manager access in overlays and screens.
- Gate: UI matches current behavior.

### Phase 7 - Navigation + Replay Integration
- Update `TaskNavigationController` and replay code to use engine flows.
- Remove direct access to legacy manager internals.
- Gate: navigation behavior unchanged.

### Phase 8 - Cleanup + Enforcement
- Remove legacy MapLibre APIs from task managers.
- Add CI checks to prevent Android/UI imports in task domain packages.
- Update docs and remove obsolete code paths.
- Gate: architecture compliance verified.

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

## Files Likely to Change (Initial List)
- `feature/map/src/main/java/com/example/xcpro/tasks/TaskManagerCoordinator.kt`
- `feature/map/src/main/java/com/example/xcpro/tasks/TaskSheetViewModel.kt`
- `feature/map/src/main/java/com/example/xcpro/tasks/TaskRepository.kt`
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/AATTaskManager.kt`
- `feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingTaskManager.kt`
- `feature/map/src/main/java/com/example/xcpro/tasks/TaskMapOverlay.kt`
- `feature/map/src/main/java/com/example/xcpro/map/MapTaskScreenManager.kt`
- `feature/map/src/main/java/com/example/xcpro/tasks/TaskNavigationController.kt`

## Pre-Implementation Rule
No code changes should begin until this plan is reviewed and approved.
