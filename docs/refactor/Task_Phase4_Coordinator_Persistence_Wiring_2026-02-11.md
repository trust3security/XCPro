# Task Phase 4 - Coordinator Persistence Wiring (2026-02-11)

## Scope
Interim migration step after Phase 3 extraction:
- route coordinator startup/named-task persistence through injected `TaskEnginePersistenceService`,
- keep existing manager/UI behavior intact as compatibility during phased migration.

This is preparatory work toward coordinator refactor items in
`docs/refactor/archive/2026-04-doc-pass/Task_Architecture_Compliance_Refactor_Plan.md` (Phase 5/6 track).

## Implemented

Updated coordinator persistence wiring:
- `feature/map/src/main/java/com/example/xcpro/tasks/TaskManagerCoordinator.kt`
  - Constructor now accepts injected:
    - `TaskEnginePersistenceService`
    - `RacingTaskEngine`
    - `AATTaskEngine`
  - `loadSavedTasks()` is now suspend and restores task type via `TaskEnginePersistenceService`.
  - Named task APIs now route through service contracts:
    - `getSavedTasks()`
    - `saveTask(taskName)`
    - `loadTask(taskName)`
    - `deleteTask(taskName)`
  - Added compatibility bridge helpers:
    - sync manager state into engines before persistence calls
    - apply engine state back to managers when needed

Updated DI construction:
- `app/src/main/java/com/example/xcpro/di/AppModule.kt`
  - `TaskManagerCoordinator` provider now injects service + engines + task managers.

Updated startup call path:
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenUseCases.kt`
  - `MapTasksUseCase.loadSavedTasks()` is suspend.
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt`
  - startup task restore now runs in `viewModelScope.launch`.

Updated task files import path:
- `feature/map/src/main/java/com/example/xcpro/tasks/TaskFilesUseCase.kt`
  - cup import now calls suspend coordinator `loadTask(taskName)`.

Updated compatibility helper:
- `feature/map/src/main/java/com/example/xcpro/tasks/TaskManagerCompat.kt`
  - uses `LaunchedEffect` to call suspend `loadSavedTasks()`.

Added/updated tests:
- `feature/map/src/test/java/com/example/xcpro/tasks/TaskManagerCoordinatorTest.kt`
  - verifies restore routing to persistence service and engine sync
  - verifies named save routing to persistence service

## Notes
- UI still reads legacy managers directly in this phase (known deviation remains open).
- Initial Phase 4 delivery focused on persistence/startup wiring; subsequent Phase 5 continuation removed coordinator map-instance ownership and map-typed AAT edit APIs.
- Runtime redraw for AAT edit/drag now occurs in UI runtime (`MapGestureSetup`, `MapTaskIntegration`) via `TaskMapRenderRouter`.
- Runtime DI path is now service-first for startup and named-task persistence.
- Non-DI/test-only coordinator construction no longer provides named-task persistence fallback.

## Phase Gate
- Persistence operations are routed through injected service contracts from coordinator paths.
- Targeted unit tests pass for coordinator routing.
