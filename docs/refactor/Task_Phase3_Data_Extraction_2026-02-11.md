# Task Phase 3 - Data Layer Extraction (2026-02-11)

## Scope
Phase 3 goal from `docs/refactor/archive/2026-04-doc-pass/Task_Architecture_Compliance_Refactor_Plan.md`:
- add task persistence interfaces,
- implement adapters using existing racing/AAT storage components,
- add task-type settings repository,
- ensure engines can load/save through injected repositories.

## Implemented

Added domain persistence service:
- `feature/map/src/main/java/com/trust3/xcpro/tasks/domain/persistence/TaskEnginePersistenceService.kt`
  - Restores task type + autosaved tasks into engines
  - Supports autosave and named task load/save/delete by task type

Added persistence adapters and mapping layer:
- `feature/map/src/main/java/com/trust3/xcpro/tasks/data/persistence/TaskPersistenceAdapters.kt`
  - `SharedPrefsTaskTypeSettingsRepository`
  - `RacingTaskPersistenceAdapter` (wraps existing racing persistence/storage stack)
  - `AATTaskPersistenceAdapter` (wraps existing `AATTaskFileIO`)
  - Core `Task` <-> legacy racing/AAT model mapping helpers

Updated contracts:
- `feature/map/src/main/java/com/trust3/xcpro/tasks/domain/persistence/TaskPersistenceContracts.kt`
  - `RacingTaskPersistence`
  - `AATTaskPersistence`
  - `TaskTypeSettingsRepository`

Added DI wiring:
- `feature/map/src/main/java/com/trust3/xcpro/di/TaskPersistenceModule.kt`
  - Binds persistence adapters to domain interfaces
  - Provides singleton `RacingTaskEngine` and `AATTaskEngine`

Added unit tests:
- `feature/map/src/test/java/com/trust3/xcpro/tasks/domain/persistence/TaskEnginePersistenceServiceTest.kt`

## Notes
- Existing coordinator/UI call paths remain unchanged in this phase.
- This phase establishes injected persistence plumbing for engine-based flows.

## Phase Gate
- Engines load/save via injected persistence contracts and tests pass.
