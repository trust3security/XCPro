# Task Phase 2 - Pure Engines (2026-02-11)

## Scope
Phase 2 goal from `docs/refactor/archive/2026-04-doc-pass/Task_Architecture_Compliance_Refactor_Plan.md`:
- create pure `AATTaskEngine` and `RacingTaskEngine`,
- back state with `StateFlow`,
- add engine-level unit tests.

## Implemented

Added pure Racing engine:
- `feature/map/src/main/java/com/example/xcpro/tasks/domain/engine/DefaultRacingTaskEngine.kt`
  - Implements `RacingTaskEngine`
  - Uses `StateFlow` SSOT (`RacingTaskEngineState`)
  - Uses existing Racing geometry/path modules (`RacingTaskCalculator`, `RacingGeometryUtils`)
  - Supports set/add/remove/reorder/active-leg/clear and distance calculations

Added pure AAT engine:
- `feature/map/src/main/java/com/example/xcpro/tasks/domain/engine/DefaultAATTaskEngine.kt`
  - Implements `AATTaskEngine`
  - Uses `StateFlow` SSOT (`AATTaskEngineState`)
  - Uses existing AAT geometry/validation modules (`AATGeometryGenerator`, `AATValidationBridge`)
  - Supports set/add/remove/reorder/active-leg/clear, target update, area update, and parameter update

Added unit tests:
- `feature/map/src/test/java/com/example/xcpro/tasks/domain/engine/DefaultRacingTaskEngineTest.kt`
- `feature/map/src/test/java/com/example/xcpro/tasks/domain/engine/DefaultAATTaskEngineTest.kt`

## Notes
- This phase intentionally does not rewire existing UI/coordinator call paths yet.
- Legacy managers remain in place; new engines are compile- and test-validated for next-phase wiring.

## Phase Gate
- Engine unit tests pass.
