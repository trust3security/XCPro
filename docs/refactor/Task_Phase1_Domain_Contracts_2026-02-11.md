# Task Phase 1 - Domain Contracts (2026-02-11)

## Scope
Phase 1 goal from `docs/refactor/archive/2026-04-doc-pass/Task_Architecture_Compliance_Refactor_Plan.md`:
- define pure domain interfaces for task engines and persistence,
- define minimal engine state models,
- do this with no runtime wiring changes.

## Implemented

Added pure task-engine contracts and state models:
- `feature/map/src/main/java/com/example/xcpro/tasks/domain/engine/TaskEngineContracts.kt`
  - `TaskEngineState`
  - `RacingTaskEngineState`
  - `AATTaskEngineState`
  - `TaskEngine<S>`
  - `RacingTaskEngine`
  - `AATTaskEngine`

Added persistence/settings contracts:
- `feature/map/src/main/java/com/example/xcpro/tasks/domain/persistence/TaskPersistenceContracts.kt`
  - `TaskTypeSettingsRepository`
  - `RacingTaskPersistencePort`
  - `AATTaskPersistencePort`

## Notes
- No task runtime wiring was changed in this phase.
- Existing coordinator/manager behavior is unchanged.
- `Port` suffix is used for persistence contracts to avoid name collision with existing concrete `RacingTaskPersistence` class.

## Phase Gate
- Compiles with no wiring changes.
