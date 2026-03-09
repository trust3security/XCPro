# CHANGE_PLAN_RACING_TASK_PHASE1_CANONICAL_MODEL_95PLUS_2026-03-07.md

## Purpose

Define a production-grade implementation plan for Racing Task Phase 1
(canonical model consolidation) that closes current SSOT drift and achieves
Phase 1 score `> 95/100`.

Read first:

1. `docs/ARCHITECTURE/ARCHITECTURE.md`
2. `docs/ARCHITECTURE/CODING_RULES.md`
3. `docs/ARCHITECTURE/PIPELINE.md`
4. `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`
5. `docs/RACING_TASK/CHANGE_PLAN_RACING_TASK_PRODUCTION_GRADE_PHASED_IP_2026-03-07.md`

## 0) Metadata

- Title: Racing Task Phase 1 Canonical Model 95+ Plan
- Owner: XCPro Team
- Date: 2026-03-07
- Issue/PR: ARCH-20260307-RT-P1-95PLUS
- Status: In progress
- Depends on: Phase 0 item 1 completed (CI guards + typed mutation contracts)

Execution update (2026-03-07):

- P1-C runtime cutover implemented in navigation path:
  - `TaskNavigationController` no longer calls `toSimpleRacingTask()`;
    it maps canonical `Task` through typed racing waypoint mapper.
- P1-D runtime cutover implemented in render path:
  - `TaskMapRenderRouter` no longer calls `toSimpleRacingTask()`;
    it renders from canonical-task mapped racing waypoints.
- P1-E partial implemented:
  - `TaskManagerCoordinator.switchToTaskType(...)` now uses full-task
    `initializeFromCoreTask(...)` handoff instead of waypoint-only transfer.
  - `TaskCoordinatorPersistenceBridge.applyEngineTaskToManager(...)` now
    hydrates managers from full canonical task + active leg.
  - `RacingTaskManager` and `AATTaskManager` gained `initializeFromCoreTask(...)`.
- P1-F partial implemented:
  - `enforceRules` now blocks:
    - runtime `toSimpleRacingTask()` bypass in nav/render routers,
    - waypoint-only hydrate calls in coordinator/persistence bridge.
- P1-E completed:
  - `RacingTaskManager` runtime authority moved to canonical `Task` state.
  - `SimpleRacingTask` is now a compatibility projection only in manager runtime.
  - `TaskManagerCoordinator` start-line crossing path now reads canonical task projection.
- Replay/runtime canonical cutover completed:
  - `RacingReplayTaskHelpers` now returns canonical `Task` and validates via
    `RacingTaskStructureRules` on projected racing waypoints.
  - `RacingReplayLogBuilder` now supports canonical `Task` input directly.
  - `TaskNavigationController` now calls navigation engine with canonical racing waypoint projection (no `SimpleRacingTask` construction).
- P1-F guard closure completed:
  - added `enforceRules` guards for:
    - replay helper canonical path (no `toSimpleRacingTask()` bypass),
    - coordinator canonical path (no `currentRacingTask` state authority access).
- Verification (post-cutover):
  - `python scripts/arch_gate.py`: PASS
  - `powershell -ExecutionPolicy Bypass -File scripts/ci/enforce_rules.ps1`: PASS
  - `./gradlew :feature:map:compileDebugKotlin`: PASS
  - `./gradlew :feature:map:testDebugUnitTest --tests "com.example.xcpro.tasks.TaskNavigationControllerTest" --tests "com.example.xcpro.tasks.TaskManagerCoordinatorTest" --tests "com.example.xcpro.tasks.TaskManagerCanonicalHydrateTest" --tests "com.example.xcpro.map.replay.RacingReplayLogBuilderTest" --tests "com.example.xcpro.map.RacingReplayTaskHelpersTest"`: PASS
  - `./gradlew enforceRules`: PASS
  - `./gradlew testDebugUnitTest`: PASS
  - `./gradlew assembleDebug`: PASS

Phase 1 rescore (2026-03-07, evidence-based):

- Canonical SSOT ownership in runtime paths: 34/35
- Runtime cutover completeness (no authority leaks in runtime call paths): 24/25
- Determinism + stable identity behavior: 15/15
- Automated test depth and regression proof: 19/20
- Documentation and CI guard coverage: 5/5
- Total: **97/100** (Phase 1 gate `>95` met)

## 1) Scope

- Problem statement:
  - Racing runtime still uses `SimpleRacingTask` in critical paths
    (navigation, render routing, persistence adapters, manager state), so
    canonical `Task` is not the single runtime authority.
- Why now:
  - Phase 2+ (strict validation and rulebook behavior) requires one canonical
    task contract; otherwise semantics will diverge and replay correctness will
    be fragile.
- In scope:
  - Canonical RT model contract in core task model.
  - Runtime cutover from `SimpleRacingTask` authority to canonical `Task`.
  - Adapter-only containment of legacy/simple models.
  - Test and CI guardrails that block regression.
- Out of scope:
  - Full Phase 2 rule semantics (start/turn/finish policy completeness).
  - Contest scoring and ranking logic.
- User-visible impact:
  - No intended UX change; reliability and persistence consistency increase.

## 2) Phase 1 Score Contract (>95)

Phase 1 score formula:

- Canonical SSOT ownership in runtime paths: 35
- Runtime cutover completeness (no authority leaks): 25
- Determinism + stable identity behavior: 15
- Automated test depth and regression proof: 20
- Documentation and CI guard coverage: 5

Required minimum for release from Phase 1:

- Total `>= 95/100`
- No category below 90
- No P0/P1 issues in racing task slice

Hard fail conditions:

- `TaskNavigationController` or `TaskMapRenderRouter` still require
  `toSimpleRacingTask()` for runtime decisions.
- `TaskManagerCoordinator` task-type switch still preserves waypoints only.
- Racing manager state authority remains `SimpleRacingTask`.
- Missing CI guard for runtime `SimpleRacingTask` authority leak.

## 3) Architecture Contract

### 3.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| RT canonical definition (route + rules + profile metadata) | `Task` core model + typed RT payload | engine state + repository + VM snapshot | runtime `SimpleRacingTask` authority |
| RT runtime navigation input | canonical `Task` projection | navigation engine input | ad-hoc `toSimpleRacingTask()` in controller |
| RT render input | canonical `Task` projection | render router DTO/domain adapter | direct `SimpleRacingTask` in router |
| RT persisted identity | canonical `Task.id` deterministic policy | storage adapters | random/alternate IDs in runtime paths |

### 3.2 Dependency Direction

Required direction remains:

`UI -> domain -> data`

Boundary risk hotspots:

- `TaskManagerCoordinator`
- `TaskCoordinatorPersistenceBridge`
- `TaskNavigationController`
- `TaskMapRenderRouter`
- racing persistence/storage adapters

### 3.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| RT runtime state authority | `RacingTaskManager.SimpleRacingTask` | canonical `Task` state holder + typed RT payload | eliminate SSOT split | manager/engine parity tests |
| Navigation task input model | `SimpleRacingTask` | canonical RT navigation DTO from `Task` | deterministic one-source routing | navigation adapter tests |
| Render routing input model | `SimpleRacingTask` conversion in router | canonical render DTO from `Task` | avoid drift between view/runtime | render adapter tests |
| Persistence bridge hydrate path | waypoint-only manager init | full canonical task hydrate | preserve ID/rules metadata | restore/switch tests |

### 3.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `TaskNavigationController.handleFix` | `currentTask.toSimpleRacingTask()` | `Task -> RacingNavTaskView` typed adapter | P1-C |
| `TaskNavigationController.syncManualLegChange` | `currentTask.toSimpleRacingTask()` | canonical task waypoint index lookup | P1-C |
| `TaskMapRenderRouter.plotCurrentTask` | `coreTask.toSimpleRacingTask()` | `Task -> RacingRenderTaskView` typed adapter | P1-D |
| `TaskCoordinatorPersistenceBridge.applyEngineTaskToManager` | `initializeFromGenericWaypoints(...)` | `hydrateFromCoreTask(task)` manager API | P1-E |
| `TaskManagerCoordinator.switchToTaskType` | waypoint-only transfer | full task transfer command | P1-E |

### 3.3 Time Base

No new time contracts introduced in Phase 1.

| Value | Time Base | Why |
|---|---|---|
| Navigation fix timestamp | replay/live source timestamp | preserve deterministic ordering |
| Task identity derivation | none (pure deterministic hash) | deterministic persistence identity |

Forbidden:

- introducing wall-clock time into RT domain/state model
- introducing random ID generation in runtime paths

### 3.4 Replay Determinism

- Deterministic for same input: Yes (mandatory)
- Randomness used: No
- Replay/live divergence rule: none introduced in Phase 1

### 3.5 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type | File/Test |
|---|---|---|---|
| `SimpleRacingTask` authority leaks into runtime routers/controllers | SSOT + dependency direction | `enforceRules` static grep | `scripts/ci/enforce_rules.ps1` |
| Reintroduction of waypoint-only switch/hydrate | SSOT + bypass removal | unit tests | `TaskManagerCoordinator*Test`, `TaskCoordinatorPersistenceBridge*Test` |
| RT model metadata lost in persistence roundtrip | SSOT + determinism | unit tests | `TaskPersistenceAdapters*Test`, `TaskPersistSerializer*Test` |
| task ID instability | determinism | unit tests + static guard | `RacingTaskInitializer*Test`, `enforce_rules.ps1` |

## 4) Data Flow (Before -> After)

Current:

`Task (core) -> toSimpleRacingTask() -> runtime decision/render/persistence`

Target Phase 1:

`Task (canonical) -> typed RT view adapters (domain boundaries only) -> decision/render/persistence`

No runtime authority write-back from simple/legacy models.

## 5) Implementation Phases

### P1-A Contract Freeze and Inventory

- Goal:
  - Freeze explicit canonical contract and migration inventory.
- Files:
  - `feature/map/src/main/java/com/example/xcpro/tasks/core/Models.kt`
  - `docs/RACING_TASK/CHANGE_PLAN_RACING_TASK_PRODUCTION_GRADE_PHASED_IP_2026-03-07.md`
  - this plan doc
- Tests:
  - add inventory assertion tests for current leaks (expected fail if helpful).
- Exit criteria:
  - migration matrix approved with file owners and completion order.

### P1-B Canonical RT Payload Model

- Goal:
  - Introduce typed RT payload in core model without behavior change.
- Files:
  - `feature/map/src/main/java/com/example/xcpro/tasks/core/Models.kt`
  - new core files for RT payload (for example: `RacingTaskRules.kt`, `TaskMetadata.kt`)
  - serializer/adapters: `TaskPersistSerializer.kt`, `TaskPersistenceAdapters.kt`
- Tests:
  - payload serialization/deserialization and defaulting tests.
- Exit criteria:
  - canonical `Task` can carry all RT runtime semantics needed for manager/nav/render/persistence.

### P1-C Navigation Cutover

- Goal:
  - remove `SimpleRacingTask` authority from navigation controller/engine boundary.
- Files:
  - `TaskNavigationController.kt`
  - `racing/navigation/RacingNavigationEngine.kt`
  - `racing/navigation/RacingNavigationEngineSupport.kt`
  - typed navigation mapper file (new).
- Tests:
  - deterministic navigation regression for same fix stream.
  - parity tests old-vs-new task projection.
- Exit criteria:
  - no `toSimpleRacingTask()` call in navigation control path.

### P1-D Render Cutover

- Goal:
  - remove `SimpleRacingTask` authority from render routing.
- Files:
  - `TaskMapRenderRouter.kt`
  - `racing/RacingTaskDisplay.kt` and relevant display DTO adapters.
- Tests:
  - render DTO parity tests and no-regression layer/source setup tests.
- Exit criteria:
  - no runtime render path depends on `SimpleRacingTask` as authority.

### P1-E Coordinator and Persistence Hydration Cutover

- Goal:
  - eliminate waypoint-only switch/hydrate and manager reconstitution drift.
- Files:
  - `TaskManagerCoordinator.kt`
  - `TaskCoordinatorPersistenceBridge.kt`
  - `racing/RacingTaskManager.kt`
  - `data/persistence/TaskPersistenceAdapters.kt`
  - new manager API for canonical hydrate (`hydrateFromCoreTask`).
- Tests:
  - task-type switch preserves full canonical task.
  - restore/load keeps IDs and RT payload identical.
- Exit criteria:
  - switch/restore paths are full-task and deterministic.

### P1-F CI Closure and Cleanup

- Goal:
  - enforce no-regression and contain legacy simple model to adapter boundaries.
- Files:
  - `scripts/ci/enforce_rules.ps1`
  - remove/relocate `RacingTaskCoreMappers.kt` and any residual runtime simple-model helper.
- Tests:
  - static guard tests + targeted unit suite.
- Exit criteria:
  - CI fails on runtime `SimpleRacingTask` authority reintroduction.
  - Phase 1 score recomputed `>=95`.

## 6) Test Plan

- Unit tests:
  - `Task` canonical payload roundtrip tests.
  - coordinator switch/restore fidelity tests.
  - navigation and render adapter parity tests.
  - deterministic ID stability and collision boundary tests.
- Replay/regression tests:
  - two-pass replay determinism for unchanged fixtures.
  - navigation event sequence parity under identical fix streams.
- Failure-mode tests:
  - missing optional RT payload defaults.
  - partial legacy payload migration.
- Boundary tests for removed bypasses:
  - explicit tests asserting no waypoint-only transfer in switch/hydrate.

Required commands:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

When relevant:

```bash
./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
```

## 7) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Hidden runtime dependency on simple model | behavior drift post-cutover | staged cutover with parity tests at each phase | XCPro Team |
| Serialization incompatibility | task import/export regressions | dual-read migration tests and schema versioning | XCPro Team |
| Navigation regressions after model boundary change | incorrect leg advances/events | fixed-fixture deterministic replay + event snapshot diff tests | XCPro Team |
| Large refactor blast radius | slower merges/review misses | short sub-phases with strict gate per sub-phase | XCPro Team |

## 8) Acceptance Gates

- No architecture/coding rule violations.
- Runtime canonical owner for RT is `Task` contract, not `SimpleRacingTask`.
- No waypoint-only switch/hydrate path in coordinator/persistence bridge.
- Deterministic replay behavior preserved on baseline fixtures.
- CI guards added for simple-model runtime authority leaks.
- Phase 1 rescore evidence shows `>95/100`.

## 9) Rollback Plan

- Revert independently by sub-phase:
  - P1-C navigation cutover,
  - P1-D render cutover,
  - P1-E coordinator/persistence cutover.
- Keep compatibility adapters until all sub-phases are green.
- If regression detected:
  - disable new adapter route via feature-flagged wiring switch,
  - restore previous adapter path for affected surface only,
  - retain tests and guards to prevent silent drift during rollback.

## 10) Exit Evidence Required

Before marking Phase 1 complete, attach:

- File list of removed runtime `SimpleRacingTask` authority callsites.
- Test report paths for:
  - coordinator switch fidelity,
  - persistence roundtrip fidelity,
  - navigation deterministic parity.
- `enforceRules` output proving new static guards pass.
- Phase 1 scorecard with category breakdown and total score.
