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
- Recheck update: 2026-02-22 (sixth-pass delta applied after code + plan re-audit)
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

## 1C) Missed Items from Third Recheck (Delta)

Third-pass audit (code + plan + `docs/RACING_TASK`) found additional misses still not represented enough in this plan:

1. Import hydration is still lossy and bypasses canonical task semantics.
   - `TaskSheetViewModel.importPersistedTask(...)` clears task and rebuilds via `addWaypoint(...)`, which reassigns roles by index and ignores canonical direct hydration.
   - `applyRacingObservationZone(...)` only patches turnpoint radius (`gateWidth`) for interior waypoints, leaving richer OZ/type metadata under-applied.
2. Persisted-task ID fallback still collapses identity.
   - `TaskPersistSerializer.toTask(...)` maps blank/null IDs to constant `"imported"`, creating collisions and weakening deterministic identity guarantees.
3. Deterministic fallback IDs are still weakly collision-resistant.
   - `TaskPersistenceAdapters.deterministicFallbackId(...)` uses 32-bit `hashCode()` suffix and omits rule metadata in fingerprint.
4. Start-line optimal crossing path has unit-contract drift.
   - `TaskManagerCoordinator.calculateOptimalStartLineCrossingPoint(...)` passes `wp.gateWidth` (km) to `RacingGeometryUtils.calculateOptimalLineCrossingPoint(...)`, whose internal destination calculations are meter-based.
5. Line crossing planner can miss sparse-fix valid intersections.
   - `RacingBoundaryCrossingPlanner.detectLineCrossing(...)` returns early unless both fixes are within line-radius before segment intersection is attempted.
6. Crossing timestamp interpolation is not nearest-second normalized.
   - `RacingBoundaryCrossingMath.interpolateTime(...)` truncates with `toLong()` while RT docs require nearest-second interpolation semantics.
7. PEV cadence constraints are still not explicitly modeled.
   - Missing explicit enforcement-ready fields/logic for max 3 presses, 30-second dedupe, and cylinder-start minimum 10-minute interval.
8. Finish procedure still lacks two mandatory policy outcomes.
   - Straight-in exemption for below-min-altitude finish-line crossings is not modeled.
   - Post-finish `land without delay` outcome/evidence tracking is not modeled.

## 1D) Missed Items from Fourth Recheck (Delta)

Fourth-pass audit found additional behavior and contract gaps:

1. Navigation transition logic still uses dual-trigger fallback paths that can bypass boundary interpolation.
   - `RacingNavigationEngine` triggers transitions via `crossing != null || inside/outside-change` checks for start/turn/finish.
   - When planner crossing is absent, timestamps fall back to raw fix time (`previousFix`/`currentFix`) instead of boundary-derived time.
2. Navigation event contract still lacks crossing geometry evidence.
   - `RacingNavigationEvent` carries only type/index/time and no crossing point or inside/outside anchors.
   - Downstream replay/UI must infer geometry independently, increasing drift risk.
3. Replay anchor generation still bypasses the crossing planner.
   - `RacingReplayAnchorBuilder` composes anchors from independent calculators and `lineCrossOffsetMeters(...)` heuristics, not from `RacingBoundaryCrossingPlanner` outputs.
4. Boundary planner rejects border-state transitions too aggressively.
   - `RacingBoundaryCrossingPlanner.isTransition(...)` exits when either side is `BORDER`, which can drop valid exact/tangent boundary cases.
5. Navigation event delivery can silently drop events under burst conditions.
   - `RacingNavigationStateStore` emits via `MutableSharedFlow(extraBufferCapacity = 1)` + `tryEmit`, so competition-significant events are not guaranteed delivery.

## 1E) Missed Items from Fifth Recheck (Delta)

Fifth-pass audit found additional contract-level misses:

1. RT validity is still decided by simplistic size checks in multiple runtime paths.
   - `TaskManagerCoordinator.isTaskValid()` delegates to `RacingTaskManager.isRacingTaskValid()`, which returns `_currentRacingTask.waypoints.size >= 2`.
   - `DefaultRacingTaskEngine.publish(...)` sets `isTaskValid` from `task.waypoints.size >= 2`.
   - These bypass the intended RT structure contract (Start + >=2 TP + Finish + profile/rule constraints).
2. `gateWidth` remains semantically overloaded and inconsistent for line geometry semantics.
   - `RacingWaypoint.gateWidth` represents mixed concepts (line length, cylinder radius, sector radius).
   - `RacingWaypoint.effectiveRadius` comments treat line value as half-width, while render/detector paths treat it as line length then divide by two (`StartLineDisplay`, `FinishLineDisplay`, `RacingZoneDetector.lineSectorRadiusKm`).
   - This creates high drift risk for validation, UI labels, and planner integration.
3. Navigation controller lifecycle still lacks listener cleanup path.
   - `TaskNavigationController.bind(...)` registers `addLegChangeListener` once and sets `legChangeListenerAdded = true`, but has no corresponding remove/unbind path.
   - This risks stale callbacks across lifecycle edges and can silently change auto/manual advance behavior over time.

## 1F) Missed Items from Sixth Recheck (Delta)

Sixth-pass audit found additional architecture and compliance misses:

1. Task-type switch and restore handoff are still waypoint-only and bypass canonical task semantics.
   - `TaskManagerCoordinator.switchToTaskType(...)` migrates only `currentTask.waypoints` and rebuilds via `initializeFromGenericWaypoints(...)`.
   - `TaskCoordinatorPersistenceBridge.applyEngineTaskToManager(...)` also hydrates managers from `state.base.task.waypoints` only.
   - These paths drop task-level fidelity (ID/rules/metadata) and rely on reinitializers that can churn IDs.
2. RT structure validation still misses start/finish cardinality and ordering invariants.
   - `TaskValidator` checks only `any START`, `any FINISH`, and total minimum points (`minPoints = 2`).
   - No explicit guard currently enforces exactly one start, exactly one finish, start-first/finish-last ordering, and >=2 interior turnpoints.
3. FAI quadrant turnpoint transitions still lack planner-grade crossing interpolation.
   - `RacingNavigationEngine` sets `crossing = null` for `RacingTurnPointType.FAI_QUADRANT`.
   - Progression then advances only on `!insidePrevious && insideNow`, which skips intersection-derived crossing time for sparse-fix entries.
4. Racing course validation wiring remains effectively stubbed.
   - `RacingTaskManager.validateRacingCourse()` returns valid for any task with >=2 waypoints.
   - `RacingTaskValidator.validateCourseLineTouchesWaypoints(...)` is instantiated but not invoked in the manager/runtime path.
5. Replay preconditions still rely on `>=2` waypoint shortcuts instead of RT validity contract.
   - `RacingReplayTaskHelpers.currentRacingTaskOrNull(...)` gates on `<2` only.
   - `RacingReplayLogBuilder.build(...)` requires `waypoints.size >= 2`.
   - `MapScreenReplayCoordinator` surfaces the same `at least 2 waypoints` assumption in user-facing behavior.
6. Navigation fix-stream binding still lacks duplicate-collector guardrails.
   - `TaskNavigationController.bind(...)` launches `fixes.onEach { ... }.launchIn(scope)` per bind call.
   - No tracked bind job/cancellation contract exists to enforce one active collector.

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
| Task-type switch/restore fidelity | canonical `Task` handoff across coordinator + persistence bridge | full task snapshot transfer (`Task`) | waypoint-only handoff/rebuild paths that lose task-level semantics |
| RT task validity status | domain validator contract + repository projection | `TaskUiState.validation` + engine-consistent state | ad-hoc `waypoints.size >= 2` booleans in managers/engines |

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
| Persisted task hydration/import | `TaskSheetViewModel` clear + rebuild via `addWaypoint` | Canonical hydrate path (`setTask` + targets + rules) in domain/use-case boundary | Prevent role/type/rule loss from positional rebuild | Import fidelity + round-trip tests |
| Crossing evidence distribution | Navigation engine internal math + replay helper heuristics | Typed `RacingNavigationEvent` crossing evidence payload reused by replay/UI | Prevent geometry/time drift and duplicate crossing logic | Event payload + replay parity tests |
| RT validity decision wiring | manager/engine local booleans | validator-backed single validity contract | Prevent conflicting validity truth across layers | Consistency tests |
| RT geometric dimensions | overloaded `gateWidth` on waypoint | typed line/radius/sector dimension fields in rules/projection | Prevent unit/meaning drift | Dimension contract tests |
| Navigation leg listener lifecycle | ad-hoc registration in controller | explicit bind/unbind lifecycle contract | Prevent stale callbacks/leaks | Lifecycle tests |
| Task-type switch/restore handoff | waypoint-only rebuild via `initializeFromGenericWaypoints(...)` | canonical `Task` transfer preserving ID/rules/metadata | Prevent semantic loss on mode switch/restore | Switch/restore fidelity tests |
| Racing course validation wiring | manager-local `>=2` validity stub | validator/planner-backed course validation contract | Prevent false-positive "valid" output | Validation wiring tests |
| Replay entry gating for RT | `>=2` waypoint shortcuts in replay helpers | validator-backed RT validity precondition | Prevent replay on structurally invalid RT | Replay contract tests |

### 2.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `feature/map/src/main/java/com/example/xcpro/tasks/TaskNavigationController.kt` | Runtime directly maps to `SimpleRacingTask` | Map canonical task + rules -> `RacingNavTask` | Phase 2 |
| `feature/map/src/main/java/com/example/xcpro/tasks/data/persistence/TaskPersistenceAdapters.kt` | Persists RT through simplified model | Persist canonical task/rules via versioned serializer | Phase 8 |
| `feature/map/src/main/java/com/example/xcpro/tasks/TaskSheetViewModel.kt` | `Any?` point-type bridge APIs | Typed RT rule/geometry update commands | Phase 7 |
| `feature/map/src/main/java/com/example/xcpro/tasks/TaskSheetViewModel.kt` (`importPersistedTask`) | Clear + `addWaypoint` reconstruction + best-effort OZ patching | Canonical import hydration command (preserve roles/types/rules/ids) | Phase 8 |
| `feature/map/src/main/java/com/example/xcpro/tasks/TaskManagerCoordinator.kt` (`switchToTaskType`) | Migrates only waypoint list between task types | Canonical task snapshot transfer preserving ID/rules/metadata | Phase 2 |
| `feature/map/src/main/java/com/example/xcpro/tasks/TaskCoordinatorPersistenceBridge.kt` (`applyEngineTaskToManager`) | Hydrates managers from `state.base.task.waypoints` only | Canonical `setTask`/snapshot handoff path | Phase 2 |
| `feature/map/src/main/java/com/example/xcpro/tasks/racing/models/RacingTask.kt` | Separate rich model not runtime-owner | Deprecate then remove after migration | Phase 2/9 |
| `feature/map/src/main/java/com/example/xcpro/map/RacingReplayTaskHelpers.kt` | Replay converts canonical task to `SimpleRacingTask` | Replay consumes canonical->nav projection only | Phase 2 |
| `feature/map/src/main/java/com/example/xcpro/map/replay/RacingReplayAnchorBuilder.kt` | Replay-specific boundary heuristics (`lineCrossOffsetMeters`, local calculators) | Planner/event-derived crossing evidence for anchors | Phase 5/9 |
| `feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingTaskInitializer.kt` | Random UUID task IDs | Deterministic ID policy utility | Phase 2B |
| `feature/map/src/main/java/com/example/xcpro/tasks/TaskManagerCoordinator.kt` + `feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingTaskManager.kt` | `isTaskValid` via `waypoints.size >= 2` | validator-backed validity projection | Phase 3 |
| `feature/map/src/main/java/com/example/xcpro/tasks/domain/engine/DefaultRacingTaskEngine.kt` | `isTaskValid` via `waypoints.size >= 2` | validator-backed engine validity mapping | Phase 3 |
| `feature/map/src/main/java/com/example/xcpro/tasks/TaskNavigationController.kt` | listener add without lifecycle remove | explicit unbind/dispose path | Phase 2/9 |
| `feature/map/src/main/java/com/example/xcpro/tasks/racing/navigation/RacingNavigationEngine.kt` (`FAI_QUADRANT` path) | No planner crossing (inside-change trigger only) | Planner-backed sector/quadrant crossing with interpolation | Phase 5 |
| `feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingTaskManager.kt` (`validateRacingCourse`) | course validation returns valid for any `>=2` task | validator-backed structural + geometry validation | Phase 3 |
| `feature/map/src/main/java/com/example/xcpro/map/RacingReplayTaskHelpers.kt` + `feature/map/src/main/java/com/example/xcpro/map/replay/RacingReplayLogBuilder.kt` | Replay entry checks rely on `>=2` only | Replay entry uses RT validator contract | Phase 9 |
| `feature/map/src/main/java/com/example/xcpro/tasks/TaskNavigationController.kt` (`bind`) | Unbounded repeat collectors on repeated bind | single-active-bind contract with tracked/cancelled job | Phase 9 |

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
| Import path silently drops RT semantics | SSOT + persistence/import contract | Unit/integration tests | `TaskSheetImportRacingFidelityTest` |
| Fallback ID collisions from 32-bit hash policy | Deterministic identity quality | Unit tests + enforceRules guard | `TaskDeterministicIdCollisionResistanceTest` |
| Start-line geometry unit drift (km vs m) | Domain geometry correctness | Unit test + API contract assertion | `RacingStartLineUnitContractTest` |
| Sparse-fix line crossing dropped by prefilter | RT crossing correctness | Boundary tests | `RacingLineCrossingSparseFixTest` |
| Crossing times not nearest-second | RT start/finish timing contract | Unit + replay tests | `RacingCrossingTimeNearestSecondTest` |
| PEV cadence constraints not represented | Start procedure compliance | Unit tests | `RacingPevCadenceRulesTest` |
| Finish straight-in/landing-delay outcomes missing | Finish procedure compliance | Unit tests | `RacingFinishStraightInExceptionTest`, `RacingPostFinishLandingDelayTest` |
| Transitions triggered by heuristic fallback path | RT crossing/time determinism | Unit + replay parity tests | `RacingNavigationTransitionEvidenceTest` |
| Event payload lacks crossing geometry evidence | SSOT/replay parity | Contract tests | `RacingNavigationEventCrossingPayloadTest` |
| Replay anchor builder bypasses planner outputs | Replay/live geometry parity | Replay parity tests | `RacingReplayAnchorPlannerParityTest` |
| Border-state transition suppression drops valid crossings | Boundary correctness | Boundary tests | `RacingBoundaryBorderTransitionTest` |
| SharedFlow tryEmit drops nav events | Event reliability | Unit/integration tests | `RacingNavigationStateStoreDeliveryTest` |
| RT validity bypass via size-only checks | RT structure/rule contract | Unit + integration tests | `RacingValidityContractConsistencyTest` |
| `gateWidth` meaning drift across line/cylinder/sector paths | Geometry contract correctness | Unit tests | `RacingDimensionSemanticContractTest` |
| Navigation controller listener not removed on lifecycle end | Lifecycle correctness | Unit/integration tests | `TaskNavigationControllerLifecycleTest` |
| Task-type switch/restore loses ID/rules/metadata | SSOT handoff contract | Unit/integration tests | `TaskTypeSwitchRestoreFidelityTest` |
| RT validator misses start/finish cardinality/order invariants | RT structure contract | Unit tests | `TaskValidatorRacingRoleCardinalityOrderTest` |
| FAI quadrant transitions skip planner interpolation | RT crossing-time correctness | Unit/replay tests | `RacingQuadrantCrossingInterpolationTest` |
| Racing course validation path is stubbed and disconnected | Validation correctness | Unit tests | `RacingCourseValidationWiringTest` |
| Replay starts from `>=2` shortcut instead of RT validity | RT validity parity | Unit/integration tests | `RacingReplayValidityPreconditionTest` |
| Repeated navigation bind creates duplicate fix collectors | Lifecycle/cadence correctness | Unit/integration tests | `TaskNavigationControllerBindIdempotencyTest` |

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

Dimension normalization policy:
- Canonical rules should represent line length and circle/sector radii as separate typed fields.
- `gateWidth` remains adapter-only compatibility input until migration completes.
- No new runtime logic may infer line semantics from overloaded `gateWidth` alone.

Planned rule details:
- `RacingStartRules`:
  - `startType` (`LINE`, `RING_LEGACY`, `CYLINDER_START_PEV`, `FAI_START_SECTOR_EXTENDED`)
  - `gateOpenLocal`, `gateCloseLocal`, `timezoneId`
  - `crossingDirectionDeg` (for line)
  - `preStartAltitudeConstraint`
  - `pev` (enabled/wait/window/retry limits, `maxPressesPerLaunch = 3`, `dedupeSeconds = 30`, `minIntervalMinutes = 10` for cylinder-start mode)
- `RacingTurnpointRules`:
  - default cylinder radius 500 m
  - near miss tolerance policy (500 m)
- `RacingFinishRules`:
  - finish type (`RING`, `LINE`)
  - ring/line dimensions
  - line direction
  - min altitude constraint
  - straight-in exemption policy for finish-line below-min-altitude cases
  - post-finish landing policy (`LAND_WITHOUT_DELAY`) + evidence window
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
  - crossing evidence payload:
    - `crossingPoint`
    - `insideAnchor`
    - `outsideAnchor`
    - `detectionSource` (`PLANNER_INTERSECTION`, `HEURISTIC_FALLBACK`, `MANUAL`)
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
  - nearest-second normalized crossing timestamps
  - PEV cadence semantics (max count, dedupe window, interval guards)
  - collect multiple valid starts (policy-driven)
- Turnpoints:
  - strict entry/intersection with interpolated time
  - near miss evaluation at 500 m outside OZ boundary
- Finish:
  - line direction or ring entry
  - min altitude checks
  - straight-in exception handling for finish-line altitude rule
  - closing-time outlanding logic
  - post-finish `land without delay` outcome tracking
  - contest-boundary landing special case (when configured)
  - first finish only
- Transition source policy:
  - planner intersection evidence is primary for scoring-grade transitions.
  - heuristic inside/outside fallback is explicit, policy-gated, and marked in event source metadata.
  - fallback path must never silently replace planner-grade timestamp when planner evidence exists.

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
  - `feature/map/src/main/java/com/example/xcpro/tasks/TaskCoordinatorPersistenceBridge.kt`
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
  - Task-type switch/restore fidelity tests (preserve `Task.id` and task-level semantics across manager/engine handoff).
- Exit criteria:
  - Canonical task is runtime-owner; projection-only nav DTO in use.
  - No role-by-index rewrite in RT runtime mappers.
  - Navigation controller listener lifecycle has explicit bind/unbind semantics (no orphan listener registrations).
  - Switch/restore paths no longer migrate waypoint lists in isolation; canonical `Task` handoff preserves ID/rules/metadata.

### Phase 2B: Deterministic Identity and Defaults

- Goal:
  - Remove runtime randomness, harden fallback identity quality, and align RT defaults across UI/engine/mappers.
- Files:
  - `feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingTaskInitializer.kt`
  - `feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingWaypointListItems.kt`
  - `feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingTaskCoreMappers.kt`
  - `feature/map/src/main/java/com/example/xcpro/tasks/data/persistence/TaskPersistenceAdapters.kt`
  - `feature/map/src/main/java/com/example/xcpro/tasks/TaskPersistSerializer.kt`
- Tests:
  - Deterministic ID generation tests (no `UUID.randomUUID` in RT init path).
  - Collision-resistance tests for fallback IDs.
  - Imported-task ID fallback tests (no constant `"imported"` collapse).
  - Start/finish/turn default consistency tests.
- Exit criteria:
  - Deterministic IDs used for RT task initialization when no external ID is provided.
  - Fallback deterministic IDs are collision-resistant enough for practical imports and include rule fingerprint inputs.
  - Default start behavior is consistent across UI and engine layers.

### Phase 3: RT Structural Validation Contract

- Goal:
  - Enforce exactly one Start, >=2 interior TPs, one Finish, start-first/finish-last ordering, and key geometry constraints; make validator-backed validity the only runtime truth.
- Files:
  - `feature/map/src/main/java/com/example/xcpro/tasks/domain/logic/TaskValidator.kt`
  - `feature/map/src/main/java/com/example/xcpro/tasks/TaskRepository.kt`
  - `feature/map/src/main/java/com/example/xcpro/tasks/TaskManagerCoordinator.kt`
  - `feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingTaskManager.kt`
  - `feature/map/src/main/java/com/example/xcpro/tasks/domain/engine/DefaultRacingTaskEngine.kt`
  - UI validation rendering files in `feature/map/src/main/java/com/example/xcpro/tasks/`
- Tests:
  - New RT validator tests for structure and role sequencing.
  - Start/finish cardinality and ordering tests (exactly one each, fixed boundary positions).
  - Validity consistency tests across manager/engine/repository projections.
  - Racing course validation wiring test (manager path delegates to validator contract, not `>=2` shortcut).
  - 45-degree leg-angle warning tests (warning-level, non-blocking by default).
- Exit criteria:
  - Invalid RT structures cannot be marked valid.
  - No `waypoints.size >= 2` validity shortcuts remain in RT runtime decision paths.
  - `validateRacingCourse` path is validator-backed and cannot return valid solely from waypoint count.

### Phase 4: Start Procedure Compliance

- Goal:
  - Implement gate-time, start type, tolerance, pre-start altitude, and PEV-complete cadence semantics.
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
  - PEV cadence tests: max 3 presses, 30-second dedupe, 10-minute minimum interval (cylinder-start mode).
- Exit criteria:
  - Start outcomes are explicit and policy-aware.
  - Crossing times are nearest-second normalized.
  - Altitude/speed checks are implemented from navigation fix inputs, not inferred indirectly.

### Phase 5: Turnpoint Strict + Near-Miss Logic

- Goal:
  - Full strict TP achievement plus near-miss reporting, boundary math hardening for sparse-fix crossings (including FAI quadrant interpolation), and explicit transition-source handling.
- Files:
  - `feature/map/src/main/java/com/example/xcpro/tasks/racing/navigation/`
  - `feature/map/src/main/java/com/example/xcpro/tasks/racing/boundary/`
- Tests:
  - Segment intersection and boundary interpolation tests (including nearest-second timestamp normalization).
  - FAI quadrant crossing tests with planner-derived intersection time under sparse-fix sampling.
  - Sparse-fix line crossing tests (intersection-first path, with explicit noise guards).
  - Border/tangent crossing tests where one or both fixes classify as BORDER.
  - Transition-source tests that validate planner evidence precedence over heuristic fallback.
  - Near-miss 500 m tests.
  - Sequence enforcement tests.
- Exit criteria:
  - TP progress exposes strict achievement and near-miss flags.
  - Line/start/finish crossing planner no longer drops valid sparse-fix intersections by radius prefilter.
  - BORDER-classified edge cases do not suppress valid crossings when geometric intersection exists.
  - `FAI_QUADRANT` transitions use planner-backed evidence/timestamp path (no inside-change-only fallback).

### Phase 6: Finish Procedure Compliance

- Goal:
  - Add finish line/ring direction rules, min-altitude checks (with straight-in exception), closure policy, and post-finish landing semantics.
- Files:
  - `feature/map/src/main/java/com/example/xcpro/tasks/racing/navigation/RacingNavigationEngine.kt`
  - finish evaluator helpers
  - state/event model files
  - optional contest-boundary geometry helper in tasks domain
- Tests:
  - Finish line direction pass/fail.
  - Finish ring entry timing interpolation.
  - Min-altitude warnings/penalty flags.
  - Straight-in finish-line exception cases for below-min-altitude handling.
  - Finish close outlanding at last fix before close.
  - Post-finish `land without delay` outcome/evidence tests.
  - Contest-boundary landing special case (`stop + 5 min`) when boundary is configured.
- Exit criteria:
  - Finish completion semantics match RT docs in app behavior (including straight-in and landing-delay rules).

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
  - Schema-v2 full fidelity for RT rules and robust compatibility, including canonical import hydration.
- Files:
  - `feature/map/src/main/java/com/example/xcpro/tasks/TaskPersistSerializer.kt`
  - `feature/map/src/main/java/com/example/xcpro/tasks/TaskFilesUseCase.kt`
  - `feature/map/src/main/java/com/example/xcpro/tasks/CupFormatUtils.kt`
  - `feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingTaskStorage.kt`
  - `feature/map/src/main/java/com/example/xcpro/tasks/data/persistence/TaskPersistenceAdapters.kt`
  - `feature/map/src/main/java/com/example/xcpro/tasks/TaskSheetViewModel.kt`
  - `feature/map/src/main/java/com/example/xcpro/tasks/TaskSheetCoordinatorUseCase.kt`
  - `feature/map/src/main/java/com/example/xcpro/tasks/TaskManagerCoordinator.kt`
- Tests:
  - v1 -> v2 migration tests.
  - v2 round-trip fidelity tests for all RT rules.
  - Persisted-task import fidelity tests (role/type/rule/ID preservation without positional rebuild).
  - CUP import default-rule hydration tests.
- Exit criteria:
  - No data loss for RT rules in JSON; CUP remains intentionally reduced-fidelity with explicit defaults.
  - Task-sheet import path hydrates canonical task directly (no lossy `addWaypoint` reconstruction).

### Phase 9: Hardening, Docs, and Cleanup

- Goal:
  - Complete docs sync, remove dead model, and verify release readiness.
- Files:
  - `docs/ARCHITECTURE/PIPELINE.md` (if wiring changed)
  - `docs/RACING_TASK/task_json_schema_example.md` (schema v2)
  - `docs/RACING_TASK/task_creation_ui_spec.md` (actual UI behavior)
  - `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md` (only if exception approved)
  - `feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingMapRenderer.kt` (role-color expression consistency fix if not already addressed earlier)
  - `feature/map/src/main/java/com/example/xcpro/map/replay/RacingReplayAnchorBuilder.kt` (remove planner-bypass anchor heuristics once parity path lands)
  - `feature/map/src/main/java/com/example/xcpro/map/RacingReplayTaskHelpers.kt` (replace `>=2` gate with validator-backed RT validity precondition)
  - `feature/map/src/main/java/com/example/xcpro/map/replay/RacingReplayLogBuilder.kt` (align replay preconditions with RT structure contract)
  - `feature/map/src/main/java/com/example/xcpro/tasks/TaskNavigationController.kt` (lifecycle-safe listener cleanup)
  - `scripts/ci/enforce_rules.ps1` (add deterministic-ID and role-rewrite guardrails when feasible)
  - Remove/deprecate old model files once fully unused.
- Tests:
  - Full required checks + relevant instrumentation.
  - Replay precondition contract tests and bind-idempotency lifecycle tests.
- Exit criteria:
  - No architectural drift, no dead-path model split, documentation updated.

## 5) Detailed Coding Work Breakdown

### 5.1 New/Updated Domain Types

- Add typed RT rule classes and constraints in core models.
- Keep all defaults explicit and deterministic.
- Enforce invariants with constructor `require(...)` and targeted tests.
- Add explicit dimension types/fields for line length vs radius semantics (no overloaded geometry meaning at canonical layer).

### 5.2 Navigation Evaluator Refactor

- Split `RacingNavigationEngine` into small pure evaluators:
  - `StartEvaluator`
  - `TurnpointEvaluator`
  - `FinishEvaluator`
  - `PenaltyAndToleranceEvaluator`
- Keep `RacingBoundaryCrossingPlanner` as geometry primitive layer.
- Replace truncating interpolation with nearest-second normalized crossing timestamps where RT timing semantics require it.
- Make line-crossing evaluation intersection-first, then apply policy/noise guards (instead of dropping by coarse prefilter alone).
- Add a planner-backed FAI quadrant crossing path (no `crossing = null` branch) so sparse-fix entries keep interpolation semantics.
- Remove implicit `crossing || insideChange` ambiguity by making transition evidence source explicit and testable.
- Propagate crossing evidence through event contracts so replay/UI consume SSOT event geometry.

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
- Remove constant `"imported"` identity fallback; retain provided IDs or use deterministic derivation policy.

### 5.5 Model Removal Sequence

1. Mark `racing/models/RacingTask.kt` deprecated.
2. Remove all runtime callsites.
3. Keep compatibility adapter only if still needed for external import.
4. Remove file once no references remain.

### 5.6 Deterministic Identity Policy

- No random UUID generation in task runtime initialization for RT.
- Deterministic fallback ID must be based on stable waypoint fingerprint when external ID is absent.
- Replace 32-bit hash fallback with stronger deterministic digest and include rule metadata in fingerprint input.
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

### 5.9 Switch/Replay Validity Contracts

- Remove waypoint-only transfer in task-type switch/restore flows; hand off canonical `Task` snapshots.
- Ensure manager restore paths do not regenerate task IDs when task identity already exists.
- Align replay entry conditions with RT validator outputs (not waypoint-count shortcuts).
- Add explicit single-active-bind contract in `TaskNavigationController` to prevent duplicate fix collectors.

## 6) Test Plan

- Unit tests:
  - Rule model invariants.
  - Validator contract tests.
  - Start/finish cardinality and ordering tests (exactly one start, exactly one finish, start-first/finish-last, >=2 interior TPs).
  - Validity consistency tests (validator result equals manager/engine-reported validity).
  - Task-type switch/restore fidelity tests (ID + task-level semantics preserved across coordinator/persistence handoffs).
  - Dimension semantic tests (line length, line half-width derivation, cylinder/sector radii consistency).
  - Start/TP/Finish evaluator tests.
  - FAI quadrant planner interpolation tests (sparse-fix entry cases).
  - Transition source precedence tests (planner vs heuristic).
  - Navigation event payload tests (crossing point/anchors/source).
  - Serializer compatibility tests.
  - Deterministic ID tests (no random runtime IDs).
  - Deterministic ID collision-resistance tests.
  - Persisted import fidelity tests (roles/types/rules/IDs preserved).
  - Profile gate tests (`FAI_STRICT` vs `XC_PRO_EXTENDED`).
  - Role preservation tests for steering/optional waypoints.
  - Start-line unit contract tests (km input vs meter geometry conversions explicit and tested).
  - Crossing timestamp nearest-second tests.
  - Border-state crossing acceptance tests.
  - PEV cadence tests (max presses/dedupe/min interval).
  - Finish straight-in exception and post-finish landing-delay tests.
  - Racing course validation wiring tests (`RacingTaskManager.validateRacingCourse` delegates to validator contract).
- Replay/regression tests:
  - Deterministic replay twice with identical event/state trace.
  - Replay precondition tests that enforce RT validator-backed validity (no `>=2` shortcut entry).
  - Replay tests for gate-close and near-miss scenarios.
  - Replay tests that assert nearest-second crossing event time parity for start/finish.
  - Replay anchor parity tests using event/planner-derived crossing evidence (no independent line-offset heuristics).
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
  - Navigation controller rebind/dispose lifecycle tests (no duplicate/stale leg listeners).
  - TaskNavigationController bind idempotency tests (single active fix collector across repeated bind calls).
- Boundary tests:
  - Line crossing directional edge cases.
  - Ring enter/leave with sparse fixes.
  - Line crossing sparse-fix segment intersection cases (including one endpoint outside radius).
  - BORDER classification cases with valid segment intersection.
  - Finish close at exact boundary time.
  - Contest boundary stop detection (`stop + 5 min`) when configured.

## 7) Acceptance Gates

- No rule violations against `ARCHITECTURE.md` and `CODING_RULES.md`.
- Item 7 resolved: no dual authoritative RT runtime models.
- RT validator enforces exactly one Start, >=2 interior TPs, one Finish, and start-first/finish-last ordering.
- Start/turn/finish outcomes explicitly encode strict/tolerance/near-miss/closure states.
- Replay remains deterministic for identical input.
- `TaskPersistSerializer` round-trips RT rules without silent data loss.
- Task import path preserves role/type/rule/ID fidelity (no lossy rebuild via `addWaypoint`).
- Task-type switch/restore flows preserve canonical task-level semantics (ID/rules/metadata), not waypoint-only rebuilds.
- No random UUID-based RT task IDs in runtime initialization paths.
- Deterministic fallback IDs are collision-resistant and include rule metadata in derivation input.
- No role-by-index rewrites in RT runtime that erase explicit steering/optional semantics.
- Crossing timestamps used for start/finish semantics are nearest-second normalized.
- Start-line optimal crossing uses explicit unit-safe meter contract.
- Sparse-fix valid line crossings are not dropped by coarse prefilter.
- FAI quadrant transitions produce planner-grade crossing evidence/timestamp (not inside-change-only detection).
- Transition events expose crossing geometry evidence and explicit detection source.
- Planner-grade transition evidence is preferred and fallback transitions are explicitly marked.
- Navigation event delivery path does not silently drop competition-relevant events.
- RT validity state is validator-backed and consistent across manager/engine/repository.
- Racing course validation path is validator-backed and cannot pass purely from `>=2` waypoint count.
- Replay preconditions use validator-backed RT validity, not `>=2` waypoint shortcuts.
- RT line geometry semantics are unambiguous (line length vs half-width derivation) across model/detector/rendering.
- Navigation controller listener registration is lifecycle-safe and does not leak stale callbacks.
- Navigation fix-stream binding is lifecycle-safe and idempotent (no duplicate collectors after repeated bind).
- Navigation fix contract carries required altitude/speed data for RT rule checks.
- PEV cadence constraints (max 3, 30-second dedupe, 10-minute interval where applicable) are explicit and tested.
- Finish straight-in exception and post-finish landing-delay outcomes are explicit and tested.
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
| Lossy task import reconstruction in task sheet | High | Canonical hydrate command + import fidelity tests | XCPro Team |
| Default mismatch across UI/engine causes silent behavior drift | Medium | Add explicit default contract tests and profile default docs | XCPro Team |
| Deterministic identity regressions due random IDs | High | Ban random IDs in RT runtime + enforceRules guard + tests | XCPro Team |
| Deterministic fallback ID collisions from weak hash | High | Stronger deterministic digest + collision tests | XCPro Team |
| Steering-point semantics lost by index rewrites | High | Remove rewrite paths and add role-preservation tests | XCPro Team |
| Start-line unit mismatch in optimal crossing path | Medium/High | Explicit unit contract + unit tests | XCPro Team |
| Sparse-fix crossings missed by planner prefilter | High | Intersection-first boundary evaluation + sparse-fix tests | XCPro Team |
| BORDER classification suppresses valid intersections | Medium/High | Border-aware transition policy + boundary tests | XCPro Team |
| Missing nearest-second normalization causes timing drift | High | Nearest-second rounding policy + replay parity tests | XCPro Team |
| Heuristic fallback path bypasses interpolated evidence | High | Transition-source precedence policy + contract tests | XCPro Team |
| Replay anchor builder geometry diverges from nav planner | High | Planner/event-driven anchor generation + parity tests | XCPro Team |
| Nav event flow may drop events under load | Medium/High | Reliable emission strategy + delivery tests | XCPro Team |
| RT validity drift from size-only shortcuts | High | Remove shortcuts + consistency tests + enforceRules guard | XCPro Team |
| Task-type switch/restore drops task-level semantics | High | Canonical task handoff + switch/restore fidelity tests | XCPro Team |
| Validator cardinality/order holes allow invalid RT layouts | High | Explicit cardinality/order contract + validator tests | XCPro Team |
| FAI quadrant transitions skip interpolation under sparse fixes | Medium/High | Planner-backed quadrant crossing path + replay parity tests | XCPro Team |
| Replay can start on structurally invalid RT via `>=2` shortcut | Medium/High | Validator-backed replay precondition + replay contract tests | XCPro Team |
| Repeated bind creates duplicate navigation collectors | Medium | Bind idempotency contract + lifecycle tests | XCPro Team |
| Overloaded `gateWidth` semantics cause line/radius drift | High | Typed dimensions + adapter migration + semantic tests | XCPro Team |
| Nav controller listener lifecycle leaks | Medium/High | Explicit unbind cleanup + lifecycle tests | XCPro Team |
| PEV cadence rules incompletely modeled | Medium | Typed cadence policy + dedicated tests | XCPro Team |
| Finish straight-in/landing-delay semantics omitted | High | Explicit finish outcomes + policy tests | XCPro Team |

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
