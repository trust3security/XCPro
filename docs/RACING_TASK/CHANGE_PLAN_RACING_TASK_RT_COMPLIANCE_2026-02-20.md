# CHANGE_PLAN_RACING_TASK_RT_COMPLIANCE_2026-02-20.md

## Purpose

Define a high-detail, architecture-compliant implementation plan to bring XCPro Racing Task behavior in line with `docs/RACING_TASK/*` and FAI Annex A guidance, while preserving MVVM + UDF + SSOT + deterministic replay constraints.

Read first:
1. `docs/ARCHITECTURE/ARCHITECTURE.md`
2. `docs/ARCHITECTURE/CODING_RULES.md`
3. `docs/ARCHITECTURE/PIPELINE.md`
4. `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`
5. `docs/ARCHITECTURE/CONTRIBUTING.md`
6. `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`
7. `docs/ARCHITECTURE/AGENT.md`
8. `docs/ARCHITECTURE/CHANGE_PLAN_TEMPLATE.md`
9. `docs/RACING_TASK/README.md`
10. `docs/RACING_TASK/racing_task_definition.md`
11. `docs/RACING_TASK/task_elements_and_geometry.md`
12. `docs/RACING_TASK/start_procedure.md`
13. `docs/RACING_TASK/turnpoints_and_observation_zones.md`
14. `docs/RACING_TASK/finish_procedure.md`
15. `docs/RACING_TASK/validation_algorithms.md`
16. `docs/RACING_TASK/task_creation_ui_spec.md`
17. `docs/RACING_TASK/task_json_schema_example.md`

## 0) Metadata

- Title: Racing Task Compliance + Canonical Model Consolidation
- Owner: XCPro Team
- Date: 2026-02-20
- Issue/PR: ARCH-20260220-RT-COMPLIANCE
- Status: Draft (implementation not started)
- Recheck update: 2026-02-20 (second-pass delta applied)
- Related decision: Item 7 from codebase review is **CHANGE**, not leave-as-is.
- Why item 7 is change-required:
  - Runtime is centered on `SimpleRacingTask` (`feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingTaskManager.kt`) while richer `RacingTask` (`feature/map/src/main/java/com/example/xcpro/tasks/racing/models/RacingTask.kt`) is not runtime-authoritative.
  - This split blocks reliable implementation of gate timing, altitude procedures, penalties/tolerances, and schema-level rule persistence.

## 0A) Coordination Notes

- Active umbrella refactor plan is in progress:
  - `docs/refactor/Map_Task_Maintainability_5of5_Refactor_Plan_2026-02-14.md`
- This RT compliance plan is a focused child plan and must not regress completed hardening gates from:
  - `docs/ARCHITECTURE/CHANGE_PLAN_MAP_TASK_SLICE_HARDENING_2026-02-18.md`

## 1) Scope

- Problem statement:
  - Racing task geometry exists, but competition-level RT rule semantics and a canonical runtime model are incomplete.
  - Current model split creates drift risk and blocks compliance-grade evolution.
- Why now:
  - RT docs have been defined in-repo and are implementation-ready.
  - Additional features built on current split will increase migration cost and defect risk.
- In scope:
  - Canonical RT data model for geometry + rule semantics.
  - Navigation/validation engine upgrades (start/turn/finish full behavior).
  - UI rules editing and validation feedback.
  - Persistence/import/export schema hardening.
  - Replay/live deterministic behavior guarantees.
  - Test net expansion and enforcement mapping.
- Out of scope:
  - Full competition scoring engine and official penalty-point scoring math.
  - Cloud sync or remote contest synchronization.
  - Non-RT task modes except compatibility touchpoints required by shared APIs.
- User-visible impact:
  - Correct RT structure and rules editor.
  - More accurate start/turn/finish detection.
  - Explicit warnings for near-miss/tolerance/closure/altitude constraints.
  - Better round-trip fidelity for task import/export.

## 1A) Current Compliance Gap Map

| Area | Requirement (`docs/RACING_TASK`) | Current state | Gap |
|---|---|---|---|
| RT minimum structure | Start + >=2 TP + Finish | Validation allows 2 points total (`feature/map/src/main/java/com/example/xcpro/tasks/domain/logic/TaskValidator.kt`) | Missing hard RT structure gate |
| Canonical model | One runtime-authoritative model | Runtime uses `SimpleRacingTask`; richer `RacingTask` is separate | Split SSOT and drift risk |
| Start gate open/close | Required rule fields and enforcement | No gate fields in core task model; no gate-time checks in nav engine | Missing |
| Pre-start altitude | Optional constraint + pre-start evidence | Not represented in active rules model; not evaluated | Missing |
| PEV | Rule parameters and optional enforcement path | No typed rule model in runtime; no evaluation path | Missing |
| Start tolerance 500m | Detect and flag possible penalty path | No explicit start tolerance outcome type | Missing |
| TP near miss 500m | Detect near miss and present policy outcome | No near-miss state/event type | Missing |
| Finish closing | Closing time and outlanding behavior | No closing-time rule evaluation in finish logic | Missing |
| Finish min altitude | Optional penalty-relevant constraint | Not evaluated in navigation validation events | Missing |
| Multiple starts | Detect and preserve candidates | Current runtime advances from first detected start only | Missing candidate set |
| Rules UI | Task-sheet-grade field editing | Mostly geometry/radius controls + static rules text (`feature/map/src/main/java/com/example/xcpro/tasks/RulesBTTabParameters.kt`) | Missing editor |
| Schema fidelity | JSON stores RT rules/metadata | `TaskPersistSerializer` is waypoint-centric and not RT-rule complete | Missing |
| Rule profile support | FAI strict vs XCPro extended compatibility mode | Validation allows non-FAI zones without explicit profile (`feature/map/src/main/java/com/example/xcpro/tasks/domain/logic/TaskValidator.kt`) | Missing profile gate |
| Steering point semantics | Optional steering point before finish | Multiple paths rewrite roles by index and collapse `OPTIONAL` -> `TURNPOINT` | Missing role preservation |
| Deterministic task identity | Stable IDs across import/init/replay | `UUID.randomUUID()` in racing initializer (`feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingTaskInitializer.kt`) | Missing deterministic ID policy |
| Start/finish direction override | Manual direction from task sheet | Start/finish line crossing bearings derived only from adjacent legs | Missing explicit override path |
| Altitude/speed rule inputs | Pre-start altitude and speed checks | `RacingNavigationFix` has no altitude/speed fields (`feature/map/src/main/java/com/example/xcpro/tasks/racing/navigation/RacingNavigationState.kt`) | Missing nav input fields |
| Replay parity with canonical model | Replay should use same canonical task contract | Replay helpers/log builder still consume `SimpleRacingTask` (`feature/map/src/main/java/com/example/xcpro/map/RacingReplayTaskHelpers.kt`) | Missing migration |

## 1B) Missed Items from Recheck (Delta)

Second-pass audit found additional implementation-critical misses not explicit in the first draft:

1. Role/index rewrite debt is broader than initially listed.
   - `DefaultRacingTaskEngine.normalizeWaypointsForRacing(...)` rewrites roles/types/radii by index.
   - `Task.toSimpleRacingTask()` mappers in both `RacingTaskCoreMappers.kt` and `TaskPersistenceAdapters.kt` also rewrite roles.
   - `RacingWaypointManager` mutates roles on add/remove/reorder by positional logic.
2. Replay and debug replay builders remain coupled to `SimpleRacingTask`.
   - `RacingReplayTaskHelpers.currentRacingTaskOrNull(...)` converts canonical task -> simple task.
   - `RacingReplayLogBuilder.build(...)` takes `SimpleRacingTask`.
3. Determinism policy gap remains in racing init path.
   - `RacingTaskInitializer` assigns random task IDs via UUID.
4. Start defaults are inconsistent across layers.
   - UI type inference defaults start to `START_CYLINDER` while mapper/engine defaults often use `START_LINE`.
5. RT validation currently has no explicit strict-profile boundary.
   - Non-FAI zone types remain accepted for racing without a declared compatibility profile.
6. Pilot-facing render feedback has a known correctness bug.
   - Racing marker role values are uppercase while style color expression checks lowercase, causing start/finish color mismatch.
7. Finish special-case rule is still unmodeled.
   - No contest boundary landing rule path (`finish when landing within contest site boundary, stop + 5 min`).

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Authoritative Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Task route geometry + roles | `TaskManagerCoordinator` task state | `TaskCoordinatorSnapshot.task` | Parallel geometry copies in UI managers |
| RT rule set (gates, altitude, PEV, tolerance, finish policy) | Core task model (`Task.rules`) | `Task` | Separate ad-hoc rule stores in Composables/ViewModels |
| Navigation runtime state | `RacingNavigationStateStore` | `StateFlow<RacingNavigationState>` + event flow | UI-local mutable navigation mirrors |
| Validation projection for sheet | `TaskRepository` | `StateFlow<TaskUiState>` | Additional hidden task-validation state outside repository |
| Serialization schema state | `TaskPersistSerializer` v2 | serialize/deserialize + adapters | Competing JSON formats without adapter layer |
| Legacy CUP compatibility mapping | `CupFormatUtils` + task file adapters | import/export use-cases | Per-feature custom CUP parser/writer forks |
| Rule profile (strict/compat) | `Task.rules.racing.profile` | canonical task rules | Hidden fallback behavior by implicit defaults |
| Deterministic task identity | canonical `Task.id` + deterministic derivation utility | task model + serializer | random UUID generation in runtime init paths |

### 2.2 Dependency Direction

Required:

`UI -> domain/usecase -> data/adapters`

- Boundary expectations:
  - No Compose/Android in domain logic.
  - No direct persistence/platform calls in ViewModels.
  - Navigation rule evaluators remain pure and testable.

### 2.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| RT rule semantics storage | Implicit/fragmented (`customParameters`, static UI text) | Typed core task rules (`Task.rules`) | Single source for runtime + persistence + UI | Model round-trip tests |
| Runtime nav input model | `SimpleRacingTask` shared broadly | Derived `RacingNavTask` projection from canonical task | Remove model split and drift | Mapper + engine tests |
| Start/finish tolerance outcomes | Implicit crossing logic only | Explicit rule outcome model in nav events/state | UI and replay need auditability | Event contract tests |
| RT structure validation | Generic `TaskValidator` min=2 | RT-specific structure contract | Align with RT docs | Validator tests |

### 2.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `feature/map/src/main/java/com/example/xcpro/tasks/TaskNavigationController.kt` | Runtime directly maps to `SimpleRacingTask` | Map canonical task + rules -> `RacingNavTask` | Phase 2 |
| `feature/map/src/main/java/com/example/xcpro/tasks/data/persistence/TaskPersistenceAdapters.kt` | Persists RT through simplified model | Persist canonical task/rules via versioned serializer | Phase 8 |
| `feature/map/src/main/java/com/example/xcpro/tasks/TaskSheetViewModel.kt` | `Any?` point-type bridge APIs | Typed RT rule/geometry update commands | Phase 7 |
| `feature/map/src/main/java/com/example/xcpro/tasks/racing/models/RacingTask.kt` | Separate rich model not runtime-owner | Deprecate then remove after migration | Phase 2/9 |
| `feature/map/src/main/java/com/example/xcpro/map/RacingReplayTaskHelpers.kt` | Replay converts canonical task to `SimpleRacingTask` | Replay consumes canonical->nav projection only | Phase 2 |
| `feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingTaskInitializer.kt` | Random UUID task IDs | Deterministic ID policy utility | Phase 2B |

### 2.3 Time Base Declaration

| Value | Time Base | Why |
|---|---|---|
| Live navigation fix time | Monotonic (`GPSData.timeForCalculationsMillis` via `RacingNavigationFixAdapter`) | Stable crossing sequencing and deterministic gating |
| Replay fix time | Replay timeline (IGC timestamps) | Deterministic replay validation |
| Gate open/close fields | Local wall-time definition + timezone; resolved to session timeline before evaluation | User-entered contest schedule semantics |
| Pre-start / finish altitude check timestamps | Same base as fix stream | Avoid cross-base ambiguity |
| Export filename timestamps | Wall time via injected `Clock` | Output naming only |

Explicitly forbidden:
- Monotonic vs wall arithmetic in rule checks
- Replay vs wall comparisons without explicit conversion adapter

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - Navigation/rule eval math: `Dispatchers.Default`
  - Import/export/persistence I/O: `Dispatchers.IO`
  - UI state/event emission: `Dispatchers.Main`
- Cadence:
  - Event-driven per incoming fix; no polling loops.
- Hot-path target:
  - <= 5 ms average per fix for RT nav evaluator on mid-range devices.

### 2.5 Replay Determinism

- Deterministic for same input: Yes (mandatory).
- Randomness: None.
- Replay/live divergence:
  - Live: uses live fix monotonic stream.
  - Replay: uses IGC timeline; same evaluator and rule logic; only source adapter differs.

### 2.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type | Guard File/Test |
|---|---|---|---|
| Reintroduced dual runtime models | ARCHITECTURE SSOT | enforceRules + architecture test | New enforcement rule + mapper tests |
| RT validates with <4 points | RT docs + domain contract | Unit test | `TaskValidatorRacingContractTest` |
| Gate times ignored | Time base + domain logic | Unit + replay test | `RacingStartGateValidationTest` |
| Pre-start altitude never checked | Domain logic correctness | Unit test | `RacingPreStartAltitudeTest` |
| Near miss logic absent | Validation contract | Unit test | `RacingTurnpointNearMissTest` |
| Finish close policy absent | Rule compliance | Unit test | `RacingFinishClosingPolicyTest` |
| ViewModel business logic creep | CODING_RULES ViewModel | enforceRules + review checklist | existing enforceRules extensions |
| Serializer drops RT rule fields | SSOT/persistence | Round-trip tests | `TaskPersistSerializerRacingRulesTest` |
| Replay non-determinism from rule state mutation | determinism | Replay twice test | `RacingReplayDeterminismRulesTest` |
| Random runtime task IDs reintroduced | Determinism + SSOT identity | enforceRules + unit test | `RacingTaskInitializerDeterministicIdTest` |
| Role-by-index rewrites destroy steering/optional semantics | SSOT ownership | Unit tests + enforceRules grep guard | `RacingRolePreservationMapperTest` |
| Nav fix lacks altitude/speed for rule checks | Domain correctness | Unit tests on fix adapter + engine | `RacingNavigationFixAdapterTest` |
| Strict-vs-extended rule ambiguity | Explicit policy requirement | Validator tests | `TaskValidatorRacingProfileTest` |
| Start default mismatch across UI/mapper | Consistency + change safety | Unit/UI tests | `RacingDefaultsConsistencyTest` |

## 3) Target Architecture (Before -> After)

Before:

`UI -> TaskSheetViewModel -> TaskManagerCoordinator -> SimpleRacingTask mapper -> RacingNavigationEngine`

After:

`UI -> TaskSheetViewModel -> TaskSheetUseCase -> TaskRepository (projection + validation)`

`TaskSheetCoordinatorUseCase -> TaskManagerCoordinator canonical Task (geometry + rules) -> RacingNavTaskMapper -> RacingNavigationEngine`

`TaskFilesUseCase/Serializer <-> canonical Task (with rules) <-> adapters (CUP/JSON)`

Key architecture result:
- One canonical task definition used for UI, navigation, and persistence.
- Navigation consumes a derived projection only (`RacingNavTask`), never a parallel authoritative model.

## 3A) Canonical RT Model Spec

Planned additions under `feature/map/src/main/java/com/example/xcpro/tasks/core/`:

- `Task` extension:
  - `metadata: TaskMetadata = TaskMetadata()`
  - `rules: TaskRules = TaskRules.None`
- `TaskMetadata`:
  - `taskName`, `taskDateLocal`, `timezoneId`, `contestSiteName`, `contestSiteLatLon`, `contestSiteElevationM`
- `TaskRules` sealed interface:
  - `None`
  - `Racing(RacingRules)`
- `RacingRules`:
  - `profile: RacingRuleProfile` (`FAI_STRICT`, `XC_PRO_EXTENDED`)
  - `start: RacingStartRules`
  - `turnpoint: RacingTurnpointRules`
  - `finish: RacingFinishRules`
  - `validation: RacingValidationRules`
  - `energy: RacingEnergyRules`

Planned rule details:
- `RacingStartRules`:
  - `startType` (`LINE`, `RING_LEGACY`, `CYLINDER_START_PEV`, `FAI_START_SECTOR_EXTENDED`)
  - `gateOpenLocal`, `gateCloseLocal`, `timezoneId`
  - `crossingDirectionDeg` (for line)
  - `preStartAltitudeConstraint`
  - `pev` (enabled/wait/window/retry limits)
- `RacingTurnpointRules`:
  - default cylinder radius 500 m
  - near miss tolerance policy (500 m)
- `RacingFinishRules`:
  - finish type (`RING`, `LINE`)
  - ring/line dimensions
  - line direction
  - min altitude constraint
  - closing time/policy
  - optional contest boundary polygon + finish-on-landing policy (`STOP_PLUS_5_MIN`)
- `RacingValidationRules`:
  - start tolerance radius
  - tp near miss radius
  - multiple start policy (`FIRST_FOR_NAV`, `SELECTABLE`, `BEST_SCORE_EXTERNAL`)
- `RacingEnergyRules` (optional):
  - max start altitude
  - max start groundspeed

## 3B) Navigation Outcome Model Spec

Extend `feature/map/src/main/java/com/example/xcpro/tasks/racing/navigation/`:

- State additions:
  - `startCandidates: List<StartCandidate>`
  - `activeStartIndex: Int?`
  - `preStartAltitudeSatisfied: Boolean`
  - `finishCloseOutcome: FinishCloseOutcome?`
  - `penaltyFlags: Set<RacingPenaltyFlag>`
  - `lastFixBeforeFinishClose: RacingNavigationFix?`
- Event additions:
  - `START_VALID`
  - `START_TOLERANCE_500M`
  - `START_REJECTED_GATE_CLOSED`
  - `TURNPOINT_ACHIEVED`
  - `TURNPOINT_NEAR_MISS_500M`
  - `FINISH_VALID`
  - `FINISH_BELOW_MIN_ALTITUDE`
  - `FINISH_CLOSED_OUTLANDED`
  - `FINISH_CONTEST_BOUNDARY_STOPPED`

Engine behavior targets:
- Start:
  - enforce gate open/close
  - evaluate line/ring/cylinder mode
  - optional pre-start altitude evidence check
  - capture tolerance starts separately
  - collect multiple valid starts (policy-driven)
- Turnpoints:
  - strict entry/intersection with interpolated time
  - near miss evaluation at 500 m outside OZ boundary
- Finish:
  - line direction or ring entry
  - min altitude checks
  - closing-time outlanding logic
  - contest-boundary landing special case (when configured)
  - first finish only

## 3D) Navigation Fix Contract Upgrade

`RacingNavigationFix` must be extended to carry rule-relevant observables:

- `altitudeMslMeters: Double?`
- `groundSpeedMs: Double?`
- `bearingDeg: Double?`
- existing `lat/lon/timestamp/accuracy`

Adapters to update:
- `feature/map/src/main/java/com/example/xcpro/map/RacingNavigationFixAdapter.kt`
- replay adapters that synthesize fixes

Reason:
- Pre-start altitude, finish min-altitude, and start-speed checks cannot be implemented correctly from lat/lon/time alone.

## 3C) Persistence and Schema Spec

`TaskPersistSerializer` version upgrade:
- Add `version` and `schema` marker.
- Persist `metadata` and `rules.racing`.
- Keep backward compatibility with existing payloads:
  - v1 payloads map to default `RacingRules`.
- Maintain deterministic field defaults.
- Preserve stable task IDs (never replace with random runtime IDs).

CUP strategy:
- CUP remains limited geometry transport.
- JSON remains full-fidelity SSOT payload.
- Import of CUP applies explicit RT defaults:
  - inferred start/finish role
  - default RT rules bundle

## 4) Implementation Phases

### Phase 0: Baseline + Lock Current Behavior

- Goal:
  - Freeze current behavior with baseline tests before large refactor.
- Files to add/update:
  - Add baseline tests under `feature/map/src/test/java/com/example/xcpro/tasks/racing/navigation/`
  - Add compatibility tests for existing JSON and CUP import/export.
- Tests:
  - Replay determinism baseline for current path.
  - Serializer backward-compatibility baseline.
- Exit criteria:
  - `enforceRules`, unit tests, assemble pass with no behavior changes.

### Phase 1: Canonical Model Scaffolding

- Goal:
  - Introduce typed RT metadata/rules model without behavior change.
- Files:
  - `feature/map/src/main/java/com/example/xcpro/tasks/core/Models.kt` (extend `Task`)
  - New core model files for `TaskMetadata` and `TaskRules`.
  - `feature/map/src/main/java/com/example/xcpro/tasks/TaskPersistSerializer.kt` (read/write scaffolding)
  - `feature/map/src/main/java/com/example/xcpro/tasks/TaskState.kt` (surface rule snapshot if needed)
  - `feature/map/src/main/java/com/example/xcpro/tasks/domain/logic/TaskValidator.kt` (add profile-aware validation scaffolding only)
- Tests:
  - Model default invariants.
  - Serializer round-trip for new fields.
  - Profile default consistency tests (`FAI_STRICT` default behavior).
- Exit criteria:
  - No runtime behavior change; canonical fields available end-to-end.

### Phase 2: Item 7 Consolidation (Model Unification)

- Goal:
  - Replace runtime reliance on `SimpleRacingTask` as authoritative model.
- Files:
  - `feature/map/src/main/java/com/example/xcpro/tasks/racing/navigation/` (new `RacingNavTask`, mapper)
  - `feature/map/src/main/java/com/example/xcpro/tasks/TaskNavigationController.kt`
  - `feature/map/src/main/java/com/example/xcpro/tasks/TaskMapRenderRouter.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/RacingReplayTaskHelpers.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/replay/RacingReplayLogBuilder.kt`
  - `feature/map/src/main/java/com/example/xcpro/tasks/data/persistence/TaskPersistenceAdapters.kt`
  - `feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingTaskCoreMappers.kt`
  - `feature/map/src/main/java/com/example/xcpro/tasks/domain/engine/DefaultRacingTaskEngine.kt`
  - `feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingWaypointManager.kt`
  - Deprecate `feature/map/src/main/java/com/example/xcpro/tasks/racing/models/RacingTask.kt`
- Tests:
  - Mapper invariants and no-loss geometry checks.
  - Navigation engine parity tests against baseline for unchanged scenarios.
  - Steering-point/`OPTIONAL` role preservation tests across mapper/persistence/replay paths.
- Exit criteria:
  - Canonical task is runtime-owner; projection-only nav DTO in use.
  - No role-by-index rewrite in RT runtime mappers.

### Phase 2B: Deterministic Identity and Defaults

- Goal:
  - Remove runtime randomness and align RT defaults across UI/engine/mappers.
- Files:
  - `feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingTaskInitializer.kt`
  - `feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingWaypointListItems.kt`
  - `feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingTaskCoreMappers.kt`
  - `feature/map/src/main/java/com/example/xcpro/tasks/data/persistence/TaskPersistenceAdapters.kt`
- Tests:
  - Deterministic ID generation tests (no `UUID.randomUUID` in RT init path).
  - Start/finish/turn default consistency tests.
- Exit criteria:
  - Deterministic IDs used for RT task initialization when no external ID is provided.
  - Default start behavior is consistent across UI and engine layers.

### Phase 3: RT Structural Validation Contract

- Goal:
  - Enforce Start + >=2 TP + Finish and key geometry constraints.
- Files:
  - `feature/map/src/main/java/com/example/xcpro/tasks/domain/logic/TaskValidator.kt`
  - `feature/map/src/main/java/com/example/xcpro/tasks/TaskRepository.kt`
  - UI validation rendering files in `feature/map/src/main/java/com/example/xcpro/tasks/`
- Tests:
  - New RT validator tests for structure and role sequencing.
  - 45-degree leg-angle warning tests (warning-level, non-blocking by default).
- Exit criteria:
  - Invalid RT structures cannot be marked valid.

### Phase 4: Start Procedure Compliance

- Goal:
  - Implement gate-time, start type, tolerance, pre-start altitude, and PEV-ready paths.
- Files:
  - `feature/map/src/main/java/com/example/xcpro/tasks/racing/navigation/RacingNavigationEngine.kt`
  - `feature/map/src/main/java/com/example/xcpro/tasks/racing/navigation/RacingNavigationState.kt`
  - `feature/map/src/main/java/com/example/xcpro/tasks/racing/navigation/RacingNavigationEvent.kt`
  - `feature/map/src/main/java/com/example/xcpro/tasks/racing/navigation/RacingZoneDetector.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/RacingNavigationFixAdapter.kt`
  - Add start-rule evaluator helper files in same package.
- Tests:
  - Gate open/close accept/reject cases.
  - Start line direction enforcement.
  - Manual direction-override enforcement cases.
  - Ring leave-circle and cylinder start behavior.
  - 500 m tolerance start outcome.
  - Pre-start altitude evidence checks.
  - Start-speed check using 8-second before/after fix windows.
- Exit criteria:
  - Start outcomes are explicit and policy-aware.
  - Altitude/speed checks are implemented from navigation fix inputs, not inferred indirectly.

### Phase 5: Turnpoint Strict + Near-Miss Logic

- Goal:
  - Full strict TP achievement plus near-miss reporting.
- Files:
  - `feature/map/src/main/java/com/example/xcpro/tasks/racing/navigation/`
  - `feature/map/src/main/java/com/example/xcpro/tasks/racing/boundary/`
- Tests:
  - Segment intersection and boundary interpolation tests.
  - Near-miss 500 m tests.
  - Sequence enforcement tests.
- Exit criteria:
  - TP progress exposes strict achievement and near-miss flags.

### Phase 6: Finish Procedure Compliance

- Goal:
  - Add finish line/ring direction rules, min-altitude checks, closure policy.
- Files:
  - `feature/map/src/main/java/com/example/xcpro/tasks/racing/navigation/RacingNavigationEngine.kt`
  - finish evaluator helpers
  - state/event model files
  - optional contest-boundary geometry helper in tasks domain
- Tests:
  - Finish line direction pass/fail.
  - Finish ring entry timing interpolation.
  - Min-altitude warnings/penalty flags.
  - Finish close outlanding at last fix before close.
  - Contest-boundary landing special case (`stop + 5 min`) when boundary is configured.
- Exit criteria:
  - Finish completion semantics match RT docs in app behavior.

### Phase 7: Task Sheet UI + Typed Commands

- Goal:
  - Replace static RT rules panel with editable RT rules and typed ViewModel commands.
- Files:
  - `feature/map/src/main/java/com/example/xcpro/tasks/RulesBTTabParameters.kt`
  - `feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingManageBTTab.kt`
  - `feature/map/src/main/java/com/example/xcpro/tasks/racing/ui/RacingStartPointSelector.kt`
  - `feature/map/src/main/java/com/example/xcpro/tasks/racing/ui/RacingFinishPointSelector.kt`
  - `feature/map/src/main/java/com/example/xcpro/tasks/TaskSheetViewModel.kt`
  - `feature/map/src/main/java/com/example/xcpro/tasks/TaskSheetCoordinatorUseCase.kt`
  - `feature/map/src/main/java/com/example/xcpro/tasks/TaskManagerCoordinator.kt`
- Required refactor:
  - Remove `Any?` update paths; use typed command models.
- Tests:
  - ViewModel command mapping tests.
  - Compose tests for rules fields and validation messaging.
  - UI validation tests for strict profile constraints (`>=2 TP`, finish ring min 3km, PEV field ranges, gate open <= close).
- Exit criteria:
  - RT rule editing is possible from UI and persisted in canonical model.

### Phase 8: Import/Export and Backward Compatibility

- Goal:
  - Schema-v2 full fidelity for RT rules and robust compatibility.
- Files:
  - `feature/map/src/main/java/com/example/xcpro/tasks/TaskPersistSerializer.kt`
  - `feature/map/src/main/java/com/example/xcpro/tasks/TaskFilesUseCase.kt`
  - `feature/map/src/main/java/com/example/xcpro/tasks/CupFormatUtils.kt`
  - `feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingTaskStorage.kt`
  - `feature/map/src/main/java/com/example/xcpro/tasks/data/persistence/TaskPersistenceAdapters.kt`
- Tests:
  - v1 -> v2 migration tests.
  - v2 round-trip fidelity tests for all RT rules.
  - CUP import default-rule hydration tests.
- Exit criteria:
  - No data loss for RT rules in JSON; CUP remains intentionally reduced-fidelity with explicit defaults.

### Phase 9: Hardening, Docs, and Cleanup

- Goal:
  - Complete docs sync, remove dead model, and verify release readiness.
- Files:
  - `docs/ARCHITECTURE/PIPELINE.md` (if wiring changed)
  - `docs/RACING_TASK/task_json_schema_example.md` (schema v2)
  - `docs/RACING_TASK/task_creation_ui_spec.md` (actual UI behavior)
  - `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md` (only if exception approved)
  - `feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingMapRenderer.kt` (role-color expression consistency fix if not already addressed earlier)
  - `scripts/ci/enforce_rules.ps1` (add deterministic-ID and role-rewrite guardrails when feasible)
  - Remove/deprecate old model files once fully unused.
- Tests:
  - Full required checks + relevant instrumentation.
- Exit criteria:
  - No architectural drift, no dead-path model split, documentation updated.

## 5) Detailed Coding Work Breakdown

### 5.1 New/Updated Domain Types

- Add typed RT rule classes and constraints in core models.
- Keep all defaults explicit and deterministic.
- Enforce invariants with constructor `require(...)` and targeted tests.

### 5.2 Navigation Evaluator Refactor

- Split `RacingNavigationEngine` into small pure evaluators:
  - `StartEvaluator`
  - `TurnpointEvaluator`
  - `FinishEvaluator`
  - `PenaltyAndToleranceEvaluator`
- Keep `RacingBoundaryCrossingPlanner` as geometry primitive layer.
- Preserve interpolation semantics from existing boundary planner.

### 5.3 ViewModel and Use-Case Command Typing

- Introduce command DTOs:
  - `UpdateRacingStartRulesCommand`
  - `UpdateRacingFinishRulesCommand`
  - `UpdateRacingValidationRulesCommand`
- Replace `Any?`-typed mutation methods.
- Keep ViewModel free from policy math; only intent mapping.

### 5.4 Persistence Versioning

- Add explicit payload version field.
- Implement strict parser with defaulting rules for missing fields.
- Add migration adapter for older payloads.

### 5.5 Model Removal Sequence

1. Mark `racing/models/RacingTask.kt` deprecated.
2. Remove all runtime callsites.
3. Keep compatibility adapter only if still needed for external import.
4. Remove file once no references remain.

### 5.6 Deterministic Identity Policy

- No random UUID generation in task runtime initialization for RT.
- Deterministic fallback ID must be based on stable waypoint fingerprint when external ID is absent.
- Add explicit tests for repeatability across identical imports/rebuilds.

### 5.7 Rule Profile Strategy

- Add explicit profile flag (`FAI_STRICT` vs `XC_PRO_EXTENDED`).
- `FAI_STRICT`:
  - enforce FAI RT structure and zone constraints.
  - disable non-FAI geometries unless explicitly mapped as compatibility import.
- `XC_PRO_EXTENDED`:
  - allow current extra geometries (keyhole/quadrant/start-sector) with explicit labeling.
- UI must show active profile and constraints.

### 5.8 Direction and Altitude Data Plumbing

- Start/finish direction must support explicit override fields; fall back to leg-derived values only when unset.
- Navigation fix contract must carry altitude and speed observables.
- Altitude reference handling (`MSL`/`QNH`) must use explicit conversion adapters and documented fallback behavior.

## 6) Test Plan

- Unit tests:
  - Rule model invariants.
  - Validator contract tests.
  - Start/TP/Finish evaluator tests.
  - Serializer compatibility tests.
  - Deterministic ID tests (no random runtime IDs).
  - Profile gate tests (`FAI_STRICT` vs `XC_PRO_EXTENDED`).
  - Role preservation tests for steering/optional waypoints.
- Replay/regression tests:
  - Deterministic replay twice with identical event/state trace.
  - Replay tests for gate-close and near-miss scenarios.
  - Replay-builder parity tests after removal of `SimpleRacingTask` coupling.
- UI tests:
  - Rules editor rendering and intent dispatch.
  - Validation error/warning presentation.
  - Default consistency checks (start type defaults match mapper/engine).
- Failure/degraded mode tests:
  - Missing timezone defaults.
  - Invalid imported rule payloads.
  - Unsupported PEV data availability path.
  - Missing altitude/speed in fixes (graceful degradation behavior).
- Boundary tests:
  - Line crossing directional edge cases.
  - Ring enter/leave with sparse fixes.
  - Finish close at exact boundary time.
  - Contest boundary stop detection (`stop + 5 min`) when configured.

## 7) Acceptance Gates

- No rule violations against `ARCHITECTURE.md` and `CODING_RULES.md`.
- Item 7 resolved: no dual authoritative RT runtime models.
- RT validator enforces Start + >=2 TP + Finish.
- Start/turn/finish outcomes explicitly encode strict/tolerance/near-miss/closure states.
- Replay remains deterministic for identical input.
- `TaskPersistSerializer` round-trips RT rules without silent data loss.
- No random UUID-based RT task IDs in runtime initialization paths.
- No role-by-index rewrites in RT runtime that erase explicit steering/optional semantics.
- Navigation fix contract carries required altitude/speed data for RT rule checks.
- Active rule profile is explicit and tested (`FAI_STRICT` default, `XC_PRO_EXTENDED` opt-in).
- `KNOWN_DEVIATIONS.md` unchanged unless explicit approved exception is required.

## 8) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Large refactor churn across task stack | Medium/High | Phase gates + parity tests each phase | XCPro Team |
| Behavior regressions while consolidating models | High | Baseline lock tests + incremental mapper replacement | XCPro Team |
| Timebase mistakes for gate times | High | Explicit conversion adapter + dedicated timebase tests | XCPro Team |
| UI complexity growth in rules tab | Medium | Typed commands + sectioned composables + UI tests | XCPro Team |
| Legacy import incompatibility | Medium | Versioned serializer + migration adapters + fixtures | XCPro Team |
| Default mismatch across UI/engine causes silent behavior drift | Medium | Add explicit default contract tests and profile default docs | XCPro Team |
| Deterministic identity regressions due random IDs | High | Ban random IDs in RT runtime + enforceRules guard + tests | XCPro Team |
| Steering-point semantics lost by index rewrites | High | Remove rewrite paths and add role-preservation tests | XCPro Team |

## 9) Rollback Plan

- Independent rollback units:
  - Phase 7 UI changes can be reverted independently of engine.
  - Serializer v2 can be behind feature flag until stable.
  - Evaluator refactor can keep old engine path toggled during rollout.
- Recovery steps:
  1. Disable new RT rules evaluation via feature flag if severe regression appears.
  2. Fall back to prior navigation evaluator while preserving canonical model fields.
  3. Keep schema reader backward-compatible in both directions during transition window.

## 10) Required Verification Commands

Minimum required:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

When device/emulator is available:

```bash
./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
```

Release/CI instrumentation:

```bash
./gradlew connectedDebugAndroidTest --no-parallel
```

## 11) Post-Implementation Quality Rescore Template (Mandatory)

- Architecture cleanliness: __ / 5
- Maintainability/change safety: __ / 5
- Test confidence on risky paths: __ / 5
- Overall map/task slice quality: __ / 5
- Release readiness (task slice): __ / 5

Each score must include:
- Evidence (files + tests + rules enforced)
- Remaining risks
- Why score is below 4.0 (if applicable)
