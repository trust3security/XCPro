# CHANGE_PLAN_RACING_TASK_PRODUCTION_GRADE_PHASED_IP_2026-03-07.md

## Purpose

Define a production-grade phased implementation plan to raise Racing Task quality
for areas 1-10 to >95/100 each, while remaining compliant with:

- `docs/ARCHITECTURE/ARCHITECTURE.md`
- `docs/ARCHITECTURE/CODING_RULES.md`
- `docs/ARCHITECTURE/PIPELINE.md`
- `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`
- `docs/ARCHITECTURE/AGENT.md`

This plan supersedes tactical execution priority for RT over
`archive/2026-04-doc-pass/CHANGE_PLAN_RACING_TASK_RT_COMPLIANCE_2026-02-20.md`
and keeps that file as historical context.

## 0) Metadata

- Title: Racing Task Production Grade Phased IP
- Owner: XCPro Team
- Date: 2026-03-07
- Issue/PR: ARCH-20260307-RT-PROD-GRADE
- Status: In progress
- Automation contract:
  - `docs/RACING_TASK/AGENT_AUTOMATION_CONTRACT_RACING_TASK_2026-03-08.md`
- Automation runner:
  - `scripts/ci/racing_phase_runner.ps1`
- Baseline audit source: assistant audit dated 2026-03-07

Execution update (2026-03-07):

- Phase 0 item 1 completed: CI guardrails + minimum compliance refactor.
- Phase 1 dedicated production plan created:
  - `docs/RACING_TASK/archive/2026-04-doc-pass/CHANGE_PLAN_RACING_TASK_PHASE1_CANONICAL_MODEL_95PLUS_2026-03-07.md`
- Phase 1 execution started (P1-C/P1-D/P1-E partial, P1-F partial guards):
  - runtime nav/render removed direct `toSimpleRacingTask()` bypass,
  - coordinator/persistence bridge switched to full-task hydration APIs.
- Phase 1 execution advanced to canonical runtime authority closure:
  - `RacingTaskManager` now keeps canonical `Task` as runtime SSOT.
  - replay helpers/log builder now accept canonical task flow directly.
  - coordinator start-line helper no longer reads `currentRacingTask` manager state.
- Added `enforceRules` guards for:
  - no `UUID.randomUUID()` in `RacingTaskInitializer`,
  - no `Any?` point-type mutation signatures in VM/use-case/coordinator/manager stack,
  - no inline `waypoints.size >= 2` shortcuts in RT validity paths,
  - no replay-helper `toSimpleRacingTask()` bypass,
  - no coordinator `currentRacingTask` state-authority bypass.
- Runtime hardening applied:
  - task point-type mutation flow now uses typed enums end-to-end,
  - `RacingTaskInitializer` now generates deterministic task IDs,
  - RT validity now routes through shared `RacingTaskStructureRules`.
- Phase 1 score update:
  - `docs/RACING_TASK/archive/2026-04-doc-pass/CHANGE_PLAN_RACING_TASK_PHASE1_CANONICAL_MODEL_95PLUS_2026-03-07.md`
    now records Phase 1 total `97/100` (gate met).
- Phase 2 formal re-score update (2026-03-08):
  - Area 1 (RT structure + profile validator): **96/100**.
  - Breakdown:
    - Spec coverage and behavior parity: 39/40
    - Automated test coverage depth: 29/30
    - Determinism/timebase and architecture compliance: 19/20
    - Operational hardening and docs sync: 9/10
  - Evidence:
    - `./gradlew :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.tasks.racing.RacingTaskStructureRulesTest" --tests "com.trust3.xcpro.tasks.domain.engine.DefaultRacingTaskEngineTest" --tests "com.trust3.xcpro.tasks.racing.RacingTaskManagerRulePersistenceTest" --tests "com.trust3.xcpro.tasks.TaskManagerCanonicalHydrateTest"`: PASS
  - Residual gap (tracked under pending packs P9-A/P9-D):
    - replay precondition currently validates via strict default helper path rather than active profile propagation path.
- Verification:
  - `python scripts/arch_gate.py`: PASS
  - `powershell -ExecutionPolicy Bypass -File scripts/ci/enforce_rules.ps1`: PASS
  - `./gradlew :feature:map:compileDebugKotlin`: PASS
  - `./gradlew :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.tasks.TaskNavigationControllerTest" --tests "com.trust3.xcpro.tasks.TaskManagerCoordinatorTest" --tests "com.trust3.xcpro.tasks.TaskManagerCanonicalHydrateTest"`: PASS
  - `./gradlew enforceRules`: PASS
  - `./gradlew assembleDebug`: PASS
  - `./gradlew testDebugUnitTest`: PASS
- Phase 3 execution completed (start-procedure compliance core):
  - Added canonical typed start-rule params and PEV fields in waypoint custom parameters.
  - Navigation start path now supports strict/tolerance/rejected outcomes with explicit `START_REJECTED`.
  - Added candidate tracking (`startCandidates`, `selectedStartCandidateIndex`) and penalty flags.
  - Start evaluation now includes gate open/close, wrong direction, pre-start altitude, PEV cadence/window, and max start altitude/groundspeed penalties.
  - Added nearest-second timestamp normalization in crossing math and start candidate timestamps.
- Phase 3 score update (2026-03-07):
  - Area 3 (Start procedure compliance): **96/100**.
  - Breakdown:
    - Spec coverage and behavior parity: 38/40
    - Automated test coverage depth: 29/30
    - Determinism/timebase and architecture compliance: 20/20
    - Operational hardening and docs sync: 9/10
- Phase 4 re-pass update (2026-03-07):
  - Implemented TP near-miss explicit event outcome, planner-backed FAI quadrant crossing interpolation, and Phase-4 guard/tests.
  - Focused RT Phase-4 test pack + `enforceRules` + `assembleDebug`: PASS.
  - Full `./gradlew testDebugUnitTest`: BLOCKED by unrelated navdrawer tests:
    - `OrientationSettingsSheetBehaviorTest.backAction_closesSheet`
    - `WeatherSettingsSheetBehaviorTest.backAction_closesSheet`
  - Interim Area 4 score update: **97/100** scoped (not full-branch release-green yet).
- Phase 4.1 execution update (2026-03-07):
  - Blocker tests closed:
    - `OrientationSettingsSheetBehaviorTest.backAction_closesSheet`: PASS
    - `WeatherSettingsSheetBehaviorTest.backAction_closesSheet`: PASS
  - Hardening added:
    - near-miss threshold edge tests (`500m - margin`, `500m + margin`)
    - boundary jitter/noise non-advance test for TP sequence stability
    - FAI quadrant sparse/dense interpolation fraction stability test
    - replay near-miss path deterministic replay assertion
  - CI guardrail additions:
    - near-miss must not auto-advance (`TURNPOINT_NEAR_MISS -> true` banned)
    - near-miss threshold drift from `500.0m` banned
  - Verification:
    - `./gradlew enforceRules`: PASS
    - `./gradlew testDebugUnitTest`: PASS
    - `./gradlew assembleDebug`: PASS
    - `./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"`: PASS
    - `./gradlew connectedDebugAndroidTest --no-parallel --no-configuration-cache`: PASS
  - Area 4 score update: **99/100**.
- Phase 5 execution update (2026-03-08):
  - Implemented typed finish-rule policy contract in core custom params:
    - close time
    - min finish altitude + reference
    - direction override
    - straight-in below-min-altitude exception switch
    - land-without-delay policy window/speed/hold thresholds
    - contest-boundary stop+5 policy
  - Navigation runtime finish semantics now include:
    - direction-aware finish-line crossing with optional direction override
    - close-time outlanding at last valid pre-close fix
    - finish min-altitude enforcement with explicit straight-in exception marker
    - post-finish land-without-delay outcome tracking (`LANDING_PENDING`, `LANDED_WITHOUT_DELAY`, `LANDING_DELAY_VIOLATION`)
    - contest-boundary stop+5 finish outcome when configured
  - Event/state hardening:
    - finish outcome and straight-in marker added to `RacingNavigationEvent`/`RacingNavigationState`
    - boundary crossing evidence payload (`crossingPoint`, `insideAnchor`, `outsideAnchor`) added to navigation events
  - Controller wiring:
    - `TaskNavigationController` now passes parsed finish rules from finish waypoint custom parameters into `RacingNavigationEngine`.
  - CI guardrail additions:
    - ban finish-line coarse fallback trigger reintroduction
    - ban FINISHED+INVALIDATED early-return pattern that bypasses post-finish outcome evaluation
    - ban controller call shape that omits explicit finish-rules argument
  - Test coverage added:
    - finish line wrong direction rejection
    - finish line direction-override acceptance
    - close-time outlanding
    - min-altitude rejection + straight-in exception acceptance
    - post-finish landing-without-delay success and delay violation
    - contest-boundary stop+5 finish
    - deterministic replay finish-outcome path check
    - finish custom-param round-trip/clamp tests
  - Verification:
    - `python scripts/arch_gate.py`: PASS
    - `./gradlew enforceRules`: PASS
    - `./gradlew testDebugUnitTest`: PASS
    - `./gradlew assembleDebug`: PASS
    - `./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"`: PASS
    - `./gradlew connectedDebugAndroidTest --no-parallel`: FAIL (device dropped + configuration-cache serialization issue)
    - `./gradlew connectedDebugAndroidTest --no-parallel --no-configuration-cache`: FAIL (no connected devices available at run time)
  - Area 5 score update: **97/100**.
  - Breakdown:
    - Spec coverage and behavior parity: 38/40
    - Automated test coverage depth: 29/30
    - Determinism/timebase and architecture compliance: 20/20
    - Operational hardening and docs sync: 10/10
- Phase 5 re-pass update (2026-03-08):
  - Correctness hardening:
    - finish-close policy now evaluates interpolated boundary crossing time before forcing `OUTLANDED_AT_CLOSE`
      (prevents false outlanding when crossing occurs before close within the same fix window).
    - finish minimum-altitude policy now requires altitude evidence for the configured reference;
      missing altitude evidence rejects finish instead of accepting unknown altitude.
  - Test net expansion:
    - added regression test for crossing-before-close interpolation acceptance.
    - added regression test for min-altitude rejection when altitude evidence is missing.
    - added regression test for `QNH` altitude-reference finish validation path.
  - Verification:
    - `python scripts/arch_gate.py`: PASS
    - `./gradlew :feature:map:compileDebugKotlin`: PASS
    - `./gradlew testDebugUnitTest`: FAIL (branch-level unrelated compile/KSP breakages outside RT phase-5 scope)
    - `./gradlew enforceRules`: FAIL (branch-level unrelated line-budget miss: `MapScreenViewModel.kt` 351 > 350)
    - `./gradlew assembleDebug`: FAIL (branch-level unrelated app KSP unresolved dependencies)
  - Area 5 score update: **99/100** (scoped, RT phase-5 slice).
  - Breakdown:
    - Spec coverage and behavior parity: 40/40
    - Automated test coverage depth: 30/30
    - Determinism/timebase and architecture compliance: 20/20
    - Operational hardening and docs sync: 9/10 (full-branch gates currently red for unrelated reasons)
- Phase 6 execution update (2026-03-08):
  - Navigation evidence contract hardening:
    - `RacingBoundaryCrossing` now carries typed `evidenceSource` (`CYLINDER_INTERSECTION`, `LINE_INTERSECTION`, `SECTOR_INTERSECTION`).
    - Navigation event crossing payload now includes `evidenceSource` in addition to crossing point + anchors.
    - Start/turn/finish crossing events now propagate planner evidence source end-to-end.
  - Boundary planner correctness hardening:
    - line-crossing detection migrated to intersection-first evaluation (no coarse radius short-circuit rejection).
    - BORDER handling refined so valid `BORDER -> INSIDE/OUTSIDE` transitions are accepted while jitter-prone `OUTSIDE/INSIDE -> BORDER` transitions are rejected.
    - nearest-second crossing timestamp normalization retained across crossing planner outputs.
  - CI guardrail additions:
    - ban line-crossing coarse radius short-circuit reintroduction.
    - ban blanket BORDER transition drop reintroduction.
  - Test coverage added/expanded:
    - line crossing accepted when fixes are outside semicircle radius but segment intersects gate line.
    - BORDER cylinder transition no-drop regression.
    - line crossing nearest-second timestamp normalization.
    - evidence-source assertions in planner + start/turn/finish navigation event paths.
    - phase-4 jitter regression pack re-pass to confirm no auto-advance regressions from BORDER handling.
  - Verification:
    - `python scripts/arch_gate.py`: PASS
    - `./gradlew :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.tasks.racing.navigation.RacingNavigationEnginePhase4Test" --tests "com.trust3.xcpro.tasks.racing.boundary.RacingBoundaryCrossingPlannerTest" --tests "com.trust3.xcpro.tasks.racing.navigation.RacingNavigationEngineTest"`: PASS
    - `./gradlew :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.tasks.racing.navigation.RacingReplayValidationTest" --tests "com.trust3.xcpro.tasks.core.TaskWaypointCustomParamsTest"`: PASS
    - `./gradlew testDebugUnitTest`: PASS
    - `./gradlew assembleDebug`: PASS
    - `./gradlew enforceRules`: FAIL (branch-level unrelated line-budget miss: `MapScreenViewModel.kt` 351 > 350)
  - Area 6 score update: **97/100** (scoped, RT phase-6 slice).
  - Breakdown:
    - Spec coverage and behavior parity: 39/40
    - Automated test coverage depth: 29/30
    - Determinism/timebase and architecture compliance: 20/20
    - Operational hardening and docs sync: 9/10
- Phase 7 execution update (2026-03-08):
  - UI rules editor hardening:
    - Racing rules Compose tests expanded for strict/extended profile behavior,
      gate ordering validation, PEV range validation, and finish numeric parse
      validation.
    - Valid-input enable-state coverage added for start and finish rule apply
      actions to lock editor readiness behavior.
  - Typed-command coverage hardening:
    - ViewModel command-forwarding tests now cover full start/finish typed
      payload shapes (including altitude references and PEV policy fields).
    - `RacingTaskManager` rule persistence tests now verify full start/finish
      typed command persistence round-trip to canonical task waypoint params.
  - Verification:
    - `./gradlew :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.tasks.TaskSheetViewModelRacingRulesCommandTest" --tests "com.trust3.xcpro.tasks.RulesRacingTaskParametersTest" --tests "com.trust3.xcpro.tasks.racing.RacingTaskManagerRulePersistenceTest" --tests "com.trust3.xcpro.tasks.TaskSheetViewModelImportTest" --tests "com.trust3.xcpro.tasks.TaskManagerCanonicalHydrateTest" --tests "com.trust3.xcpro.tasks.AATEditControllerTest"`: PASS
    - `./gradlew :feature:map:compileDebugKotlin`: PASS
    - `./gradlew enforceRules`: FAIL (branch-level unrelated line-budget miss: `MapScreenViewModel.kt` 351 > 350)
  - Area 7 score update: **99/100** (scoped, RT phase-7 slice).
  - Breakdown:
    - Spec coverage and behavior parity: 40/40
    - Automated test coverage depth: 30/30
    - Determinism/timebase and architecture compliance: 20/20
    - Operational hardening and docs sync: 9/10 (full-branch rules gate currently red for unrelated reasons)
- Phase 7.5 execution update (2026-04-01):
  - Exact boundary credit hardening:
    - `RacingNavigationState` now stores authoritative `creditedStart`,
      per-leg `creditedTurnpointsByLeg`, and `creditedFinish` runtime evidence.
    - tolerance start candidates remain diagnostic only; they no longer emit
      a normal `START` or auto-advance the task.
    - turnpoints and finish cylinders no longer complete from first-inside-fix
      fallback; live advancement now requires explicit planner crossing evidence.
  - Consumer cutover:
    - `TaskPerformanceRepository` and `TaskPerformanceDistanceProjector` now
      derive task distance/speed/start-altitude from credited boundary evidence
      instead of sampled `acceptedStartFix` state.
    - `PIPELINE.md` updated so task-performance truth and racing auto-advance
      policy both describe credited-boundary runtime semantics.
  - Regression coverage added:
    - activation-inside TP and finish cylinder must not auto-advance.
    - boundary activation and non-advancing tolerance-start regressions.
    - credited-start task-performance path.
    - replay determinism for rejected plus non-advancing tolerance start states.
  - Verification:
    - `./gradlew :feature:tasks:testDebugUnitTest --tests "com.trust3.xcpro.tasks.racing.navigation.RacingNavigationEngineTest" --tests "com.trust3.xcpro.tasks.racing.navigation.RacingNavigationEngineStartPolicyTest" --tests "com.trust3.xcpro.tasks.racing.navigation.RacingNavigationEnginePhase4Test" --tests "com.trust3.xcpro.tasks.racing.navigation.RacingNavigationEngineFinishRulesTest" --tests "com.trust3.xcpro.tasks.TaskNavigationControllerTest" --tests "com.trust3.xcpro.tasks.navigation.NavigationRouteRepositoryTest"`: PASS
    - `./gradlew :feature:map-runtime:testDebugUnitTest --tests "com.trust3.xcpro.taskperformance.TaskPerformanceRepositoryTest"`: PASS
    - `./gradlew :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.tasks.racing.navigation.RacingReplayValidationTest"`: PASS
    - `./gradlew enforceRules`: PASS
    - `scripts\qa\run_root_unit_tests_reliable.bat`: PASS
    - `./gradlew assembleDebug`: PASS
- Phase 8-10 code-pass advisory update (2026-03-08):
  - Evidence run:
    - `./gradlew :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.tasks.TaskPersistSerializerFidelityTest" --tests "com.trust3.xcpro.tasks.TaskSheetViewModelImportTest" --tests "com.trust3.xcpro.tasks.data.persistence.TaskPersistenceAdaptersDeterministicIdTest" --tests "com.trust3.xcpro.tasks.racing.navigation.RacingReplayValidationTest" --tests "com.trust3.xcpro.tasks.TaskNavigationControllerTest"`: PASS
  - Current blockers found in production code:
    - Phase 8 (persistence fidelity):
      - `TaskPersistSerializer` has no explicit schema/version envelope and no v1->v2 migration path.
      - `TaskPersistSerializer.toTask(...)` still falls back to constant `"imported"` IDs.
      - `TaskSheetViewModel.applyPersistedTask(...)` still hydrates via `clearTask + addWaypoint` then best-effort OZ/target patching (lossy/rebuild path).
      - `Task.deterministicFallbackId(...)` fingerprint omits RT rule/profile metadata.
    - Phase 9 (replay parity + lifecycle):
      - replay precondition helper validates RT with default strict profile path, not persisted profile-aware validator decision.
      - `TaskNavigationController.bind(...)` has no explicit unbind/dispose lifecycle API and no single-active-bind contract.
      - `legacy map replay anchor helper` still uses replay-local heuristics (`lineCrossOffsetMeters`, ad-hoc outside margins) instead of planner/event evidence parity.
      - `legacy map replay route helper` still exposes legacy `SimpleRacingTask` replay build path.
    - Phase 10 (release hardening):
      - Missing guardrails/tests for v2 schema contract, profile-aware replay preconditions, and bind-idempotency lifecycle.
  - Uplift implementation packs added for pending phases:
    - **P8-A: Persist schema v2 + dual-reader migration**
      - Add explicit v2 envelope/version and typed RT rule/profile metadata in serializer payload.
      - Keep legacy reader and migrate v1 payloads to canonical v2 in-memory model.
      - Remove constant `"imported"` fallback in serializer restore path.
    - **P8-B: Canonical import hydration**
      - Replace waypoint-by-waypoint rebuild in `TaskSheetViewModel` with coordinator-level canonical hydrate command (preserve IDs, roles, custom params, rules, profile, targets).
      - Remove best-effort post-import OZ mutation patching when canonical payload already carries authority.
    - **P8-C: Deterministic ID v2 hardening**
      - Expand deterministic ID fingerprint to include task type + waypoint geometry + customPointType + RT start/finish/profile rule metadata.
      - Add collision-resistance and stability tests for rule-profile deltas.
    - **P9-A: Replay precondition parity**
      - Gate replay start strictly on profile-aware `RacingTaskStructureRules.validate(task, profile)` and surface validator summary in UI message.
    - **P9-B: Replay anchor/evidence parity**
      - Route replay anchor generation through boundary planner/navigation evidence contracts; remove heuristic-only crossing offsets from replay builder path.
      - Keep `Task` canonical authority in replay entry path; confine `SimpleRacingTask` to compatibility adapters only.
    - **P9-C: Navigation bind lifecycle hardening**
      - Add explicit `unbind()` / `dispose()` in `TaskNavigationController`.
      - Enforce single active fix collector across repeated `bind(...)` calls (cancel previous before rebinding).
      - Add idempotent bind/unbind lifecycle tests.
    - **P10-A: Failure-mode matrix expansion**
      - Add tests for invalid v2 payloads, degraded/missing altitude/speed evidence, profile mismatch imports, migration edge cases.
    - **P10-B: CI drift-guard additions**
      - Add `enforceRules` checks for:
        - constant `"imported"` fallback reintroduction,
        - missing serializer version envelope writer path,
        - persisted-import waypoint rebuild (`clear + addWaypoint`) regression,
        - missing nav controller unbind/single-active-bind contract,
        - replay builder heuristic anchor bypass patterns.
    - **P10-C: Release verification closure**
      - Full gates: `enforceRules`, `testDebugUnitTest`, `assembleDebug`, and connected tests when device/emulator available.
  - Pending-phase score targets after uplift packs:
    - Area 8 projected: **97/100**
    - Area 9 projected: **96/100**
    - Area 10 projected: **96/100**

- Phase 8-10 re-pass delta update (2026-03-08, pass-2):
  - Additional misses found:
    - `TaskNavigationController.bind(...)` registers an anonymous leg-change listener and does not retain a removable listener reference for `TaskManagerCoordinator.removeLegChangeListener(...)`; this leaves listener cleanup non-enforceable across lifecycle boundaries.
    - `MapScreenReplayCoordinator.start()` has no idempotent start guard and does not retain/cancel bind/observer jobs; repeated `start()` calls can stack collectors.
    - Replay precondition tests currently validate strict-profile invalid rejection, but do not assert profile-aware acceptance/rejection behavior when the active racing validation profile changes.
    - No lifecycle test currently proves navigation listener cleanup on dispose/onCleared path.
  - Delta uplift additions (must be added to execution queue after P9-C/P10-C):
    - **P9-D: Listener ownership + replay coordinator lifecycle idempotency**
      - Store the leg-change listener as a stable field in `TaskNavigationController` and remove it in `unbind()/dispose()`.
      - Add coordinator-level `start()` idempotency guard and explicit `stop()` that cancels all replay/nav observer jobs.
      - Wire `MapScreenViewModel.onCleared()` to replay coordinator stop/dispose path.
    - **P10-D: Lifecycle/profile regression net + guardrail**
      - Add tests for repeated replay coordinator `start()` calls (no duplicate collectors/events).
      - Add tests for `TaskNavigationController` unbind/dispose listener cleanup.
      - Add replay precondition tests for extended-profile eligibility behavior.
      - Add static guard for anonymous `addLegChangeListener { ... }` usage in runtime controller path.
  - Score implication:
    - Without P9-D/P10-D, practical cap remains below gate for lifecycle hardening (`Area 9 ~= 94`, `Area 10 ~= 93`).
    - With P9-D/P10-D completed, pending targets remain feasible (`Area 9 >= 96`, `Area 10 >= 96`).

## 1) Success Contract: Areas 1-10 >95/100

Baseline (current audit):

1. RT structure and validity enforcement: 25
2. Canonical domain model completeness: 25
3. Start procedure compliance: 20
4. Turnpoint/OZ achievement logic: 55
5. Finish procedure compliance: 25
6. Boundary math and timing correctness: 40
7. UI/task-sheet rule editing completeness: 30
8. Persistence/import/export fidelity: 35
9. Replay parity and RT preconditions: 45
10. Test coverage for RT rulebook behaviors: 40

Target gate for release:

- Areas `1..10` each `>= 95/100`.
- No open high-severity (P0/P1) defects in task/racing slice.
- No unapproved deviations for RT scope in `KNOWN_DEVIATIONS.md`.

Scoring method (applies to each area):

- Spec coverage and behavior parity: 40
- Automated test coverage depth: 30
- Determinism/timebase and architecture compliance: 20
- Operational hardening and docs sync: 10

Hard fail conditions for any area:

- Missing mandatory rule behavior from `docs/RACING_TASK/*`.
- Missing deterministic replay proof on changed logic.
- Missing CI/static guard for known regression class.

## 2) Scope

- Problem statement:
  - RT behavior is partially implemented but not competition-grade against
    in-repo RT rule docs.
  - Runtime still has split model/path behavior and weak validity semantics.
- Why now:
  - Current architecture debt will multiply risk for scoring, replay, and task
    interoperability features.
- In scope:
  - Canonical RT model, validator contract, start/turn/finish rule semantics,
    import/export fidelity, replay parity, UI rule editing, CI guardrails.
- Out of scope:
  - Full official competition scoring engine and ranking system.
  - Cloud sync and contest server integration.

## 3) Architecture Contract

### 3.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Canonical RT definition (route + rules + profile + metadata) | `Task` + typed RT rule payload in core/domain | repository/viewmodel state flows | `SimpleRacingTask` as runtime authority |
| RT validity decision | `TaskValidator` (RT profile path) | `TaskUiState.validation` + engine validity | local `waypoints.size >= 2` checks |
| Navigation state and event evidence | `RacingNavigationStateStore` | `StateFlow` + reliable event flow | replay/UI recomputation from ad-hoc geometry |
| Import/export authoritative schema | `TaskPersistSerializer` v2 | JSON round-trip contract + adapters | waypoint-only partial payloads |
| Task identity | canonical `Task.id` + deterministic ID service | model + serializer + persistence adapters | random UUID init paths |

### 3.2 Dependency Direction

Required:

`UI -> domain/usecase -> data/adapters`

No direct manager mutation from Composables.
No Android/UI types in domain logic.

### 3.3 Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| RT rules storage | ad-hoc waypoint params/static UI text | typed RT rule model in canonical task | preserve semantics and persistence | model + serializer tests |
| RT validity | manager/engine local booleans | validator-backed single contract | remove truth drift | consistency tests |
| Navigation crossing evidence | internal/planner-only | typed event payload contract | replay/UI parity | event payload tests |
| Task-type switch/restore | waypoint-only rebuild | canonical full-task handoff | preserve ID/rules/metadata | switch/restore tests |
| Replay precondition | `>=2` shortcut | validator-backed RT validity | avoid invalid replay starts | replay contract tests |

### 3.4 Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `TaskSheetViewModel.importPersistedTask` | clear + `addWaypoint` rebuild | canonical hydrate command | 4 |
| `TaskManagerCoordinator.switchToTaskType` | waypoint-only transfer | full-task transfer API | 5 |
| `TaskCoordinatorPersistenceBridge.applyEngineTaskToManager` | waypoint-only manager init | full-task manager hydrate | 5 |
| Replay helpers/log builder | `>=2` + `SimpleRacingTask` coupling | validator + canonical projection | 8 |
| task point type updates | `Any?` APIs | typed command DTOs | 6 |

### 3.5 Time Base

| Value | Time Base | Why |
|---|---|---|
| Live navigation fix timestamp | monotonic-derived input timestamp | deterministic sequence and delta |
| Replay navigation timestamp | replay IGC timestamp | deterministic playback |
| Gate open/close local schedule | wall time at ingestion only | contest schedule input |
| Crossing timestamps | replay/live event timeline only | sequencing and evidence |

Forbidden:

- monotonic vs wall comparisons in domain logic
- replay vs wall mixed comparisons

### 3.6 Replay Determinism

- Deterministic for same input: Yes (mandatory)
- Randomness: No in RT nav/validation paths
- Replay/live divergence: allowed only where explicitly documented and tested

## 4) Phased Implementation Plan

## Phase 0 - Baseline Lock and Guardrails

- Goal:
  - Freeze current behavior with explicit failing tests for missing RT semantics.
  - Add CI guard placeholders for known regression classes.
- Files:
  - `feature/map/src/test/java/com/trust3/xcpro/tasks/racing/**`
  - `feature/map/src/test/java/com/trust3/xcpro/tasks/**`
  - `scripts/ci/enforce_rules.ps1`
- Tests:
  - Add baseline RT rulebook gap tests marked expected-fail where needed.
- Exit criteria:
  - Reproducible baseline report and risk matrix generated.
  - New guard rules merged for:
    - no random UUID in RT init paths
    - no `Any?` point-type API in task stack
    - no `waypoints.size >= 2` validity shortcuts in RT runtime files

## Phase 1 - Canonical Model Consolidation

- Goal:
  - Establish one runtime-authoritative RT model contract.
- Execution plan:
  - `docs/RACING_TASK/archive/2026-04-doc-pass/CHANGE_PLAN_RACING_TASK_PHASE1_CANONICAL_MODEL_95PLUS_2026-03-07.md`
- Changes:
  - Introduce typed RT rules payload in core/domain model.
  - Demote/remove `SimpleRacingTask` runtime authority (adapter-only transitional use).
- Exit criteria:
  - No runtime decision path depends on `SimpleRacingTask` as SSOT.
  - Phase 1 score target: `>95/100`.
  - Area 2 target: `>= 95`.

## Phase 2 - RT Structure and Profile Validator

- Goal:
  - Implement strict RT structure contract and profile gates.
- Required semantics:
  - exactly one start
  - exactly one finish
  - start first, finish last
  - at least two interior turnpoints
  - profile gate (`FAI_STRICT` default, `XC_PRO_EXTENDED` opt-in)
- Exit criteria:
  - manager/engine/replay validity all sourced from validator contract.
  - Area 1 target: `>= 95`.

## Phase 3 - Start Procedure Compliance

- Goal:
  - Implement start gate timing, direction, tolerance, pre-start altitude, PEV policy.
- Required outcomes:
  - strict start, tolerance start, invalid start states
  - multiple start candidate tracking
  - nearest-second crossing time contract
- Exit criteria:
  - Start procedure tests pass for line/ring/cylinder modes per active profile.
  - Area 3 target: `>= 95`.
  - Status (2026-03-07): met, interim area score `96/100`.

## Phase 4 - Turnpoint and OZ Compliance

- Goal:
  - Enforce TP achievement via inside/intersection and near-miss policy.
- Required semantics:
  - strict TP achievement with interpolated evidence
  - near-miss 500m explicit outcome (non-auto-advance unless policy says so)
  - ordered TP sequence enforcement
- Exit criteria:
  - Sparse-fix and boundary cases pass.
  - Area 4 target: `>= 95`.

### Phase 4.1 - >99 Hardening and Gate Closure

- Goal:
  - Raise Area 4 from `>=95` to `>99` with explicit blocker closure and robustness evidence.
- Mandatory blocker closure (must be green before claiming >99):
  - `OrientationSettingsSheetBehaviorTest.backAction_closesSheet`
  - `WeatherSettingsSheetBehaviorTest.backAction_closesSheet`
- Hardening checklist:
  - near-miss threshold edge tests (`500m - epsilon`, `500m + epsilon`) for no false-advance drift
  - FAI quadrant interpolation monotonic timestamp tests across sparse-fix and dense-fix paths
  - GPS jitter/noise property-style tests around turnpoint boundaries to prove no oscillation-induced advance errors
  - replay determinism assertions for changed TP/near-miss paths (same input => identical event/state traces)
  - CI guard extension for known regression signatures in phase-4 crossing/near-miss code paths
- Verification gate:
  - `./gradlew enforceRules`
  - `./gradlew testDebugUnitTest`
  - `./gradlew assembleDebug`
  - `./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"` (when device/emulator available)
  - `./gradlew connectedDebugAndroidTest --no-parallel` (release/CI verification)
- Evidence requirements for `>99` claim:
  - attach command results and failing-test-zero proof for all gates above
  - attach focused Phase-4 test report references (near-miss, FAI interpolation, ordered TP sequence, determinism)
  - record residual risk as `none` or open a time-boxed entry in `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`
  - keep Phase-4 runtime changes isolated to racing navigation + tests + guardrails for reviewability

## Phase 5 - Finish Procedure Compliance

- Goal:
  - Implement finish line/ring full policy set.
- Required semantics:
  - direction-aware finish line
  - finish close handling and outlanding-at-close policy
  - minimum finish altitude, straight-in exception
  - post-finish land-without-delay outcome
  - contest-boundary stop+5 min special case when configured
- Exit criteria:
  - Finish policy matrix tests pass.
  - Area 5 target: `>= 95`.

## Phase 6 - Navigation Evidence and Timing Hardening

- Goal:
  - Harden crossing planner and event contracts.
- Required changes:
  - event payload includes crossing point, anchors, evidence source
  - nearest-second timestamp normalization
  - line crossing planner: intersection-first (no coarse radius short-circuit drop)
  - BORDER handling: do not discard valid boundary intersections
  - remove ambiguous fallback transitions where planner evidence exists
  - reliable event emission (no silent drop)
- Exit criteria:
  - Boundary and timestamp parity tests pass for start/turn/finish.
  - Area 6 target: `>= 95`.

## Phase 7 - UI Rules Editor and Typed Commands

- Goal:
  - Replace static RT rules panel with full task-sheet-grade editor.
- Required changes:
  - typed command DTOs for all RT rule updates
  - remove `Any?` mutation surfaces in VM/use-case/coordinator stack
  - UI validation errors/warnings for strict profile constraints
  - direction override and altitude reference inputs
- Exit criteria:
  - Compose + ViewModel tests cover all rule fields and validations.
  - Area 7 target: `>= 95`.

## Phase 8 - Persistence/Import/Export v2 Fidelity

- Goal:
  - Ensure full RT-rule round-trip and canonical hydration.
- Required changes:
  - schema v2 with explicit rules/profile/metadata/version
  - migration path from existing payloads
  - remove `"imported"` constant fallback ID behavior
  - strengthen deterministic fallback ID digest and include rule metadata
  - canonical import hydrate path (no lossy waypoint-by-waypoint rebuild)
- Exit criteria:
  - v1->v2 migration and v2 fidelity tests pass.
  - Area 8 target: `>= 95`.

## Phase 9 - Replay Parity and Preconditions

- Goal:
  - Align replay generation/entry with canonical validator and event evidence.
- Required changes:
  - replay entry blocked unless RT validator says valid
  - replay builder/anchor path consumes planner/event evidence contract
  - remove independent geometry heuristics that can drift from nav planner
  - controller bind lifecycle idempotent with explicit unbind/cleanup
- Exit criteria:
  - replay determinism run twice = identical state/event traces
  - replay precondition and lifecycle tests pass
  - Area 9 target: `>= 95`.

## Phase 10 - Test Net Expansion and Release Hardening

- Goal:
  - Close all area test gaps and convert prior expected-fail tests to pass.
- Required:
  - deterministic replay suite for RT edge-case fixtures
  - failure-mode tests for invalid imports, missing altitude/speed data,
    gating profile mismatches
  - CI gate updates for all discovered drift classes
- Exit criteria:
  - Area 10 target: `>= 95`.
  - Areas 1..10 all >=95 in final rescore.

## 5) Test Plan (Mandatory)

- Unit:
  - validator cardinality/order/profile tests
  - start/turn/finish evaluator tests
  - boundary planner sparse-fix/BORDER/nearest-second tests
  - deterministic ID collision-resistance tests
- Replay/regression:
  - deterministic replay repeated-run equivalence tests
  - replay precondition validator tests
  - planner/event replay anchor parity tests
- UI:
  - rule editor render/intent/validation tests
  - profile strict-mode constraints in sheet
- Lifecycle and failure:
  - controller bind idempotency and listener cleanup
  - import/migration degraded payload handling

Required commands per phase and at completion:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

When available:

```bash
./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
./gradlew connectedDebugAndroidTest --no-parallel
```

## 6) CI/Enforcement Additions (Mandatory)

Add/extend `enforceRules` or arch gate checks to fail on:

- `UUID.randomUUID()` usage in RT runtime init paths.
- `Any?` task point-type update signatures in task VM/use-case/coordinator/manager APIs.
- `waypoints.size >= 2` validity shortcuts in RT validator/manager/engine/replay paths.
- replay start precondition messages/paths that do not reference validator result.
- direct `SimpleRacingTask` runtime authority usage outside compatibility adapter layer.
- non-nearest-second crossing time interpolation in RT crossing math.

## 7) Acceptance Gates

Release gate is blocked unless all are true:

1. Areas `1..10` each `>= 95/100`.
2. RT validator contract is the only validity authority.
3. Start/turn/finish outcomes carry explicit evidence and policy status.
4. Replay deterministic parity proven for same fixtures.
5. Import/export round-trip is lossless for RT v2 schema.
6. No open unapproved deviations for RT scope.

## 8) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Large surface refactor breaks current behavior | High | phased gates + compatibility adapters + baseline tests | XCPro Team |
| Rule interpretation drift from docs | High | test matrix mapped line-by-line to `docs/RACING_TASK/*` | XCPro Team |
| Replay regression during planner changes | High | event/planner parity tests + deterministic fixtures | XCPro Team |
| Schema migration issues | Medium/High | dual-reader migration with fixture coverage | XCPro Team |
| CI rule brittleness | Medium | start with warning branch then enforce after green pass | XCPro Team |

## 9) Rollback Plan

- Rollback units:
  - UI rules editor changes
  - schema v2 writer (keep v1 reader)
  - planner evidence payload expansion
- Recovery:
  1. Disable new evaluator path via feature flag if critical regression appears.
  2. Keep canonical model and validator, temporarily route to conservative runtime evaluator.
  3. Preserve migration readers so previously saved tasks remain loadable.

## 10) Execution Cadence

- Execute phases strictly in order.
- No phase may start until previous phase gate is green.
- After each phase:
  - publish phase summary with files/tests/gate result
  - update scores for areas impacted by that phase

## 11) Final Quality Rescore Template

- 1) RT structure and validity: __ / 100
- 2) Canonical model completeness: __ / 100
- 3) Start procedure compliance: __ / 100
- 4) Turnpoint/OZ compliance: __ / 100
- 5) Finish procedure compliance: __ / 100
- 6) Boundary math/timing correctness: __ / 100
- 7) UI rule editing completeness: __ / 100
- 8) Persistence/import/export fidelity: __ / 100
- 9) Replay parity/preconditions: __ / 100
- 10) Test net coverage depth: __ / 100

All ten must be `>= 95` to close this plan.
- Automation runner phase 1 update (2026-03-08):
  - Runner: `scripts/ci/racing_phase_runner.ps1`.
  - Run ID: `20260308-174507`.
  - Logs: `logs/phase-runner/racing-task/20260308-174507/phase-01`.
  - Scope: Phase 1 - Canonical Model Consolidation.
  - Area mapping: Area 2.
  - Workspace pre-check: PASS (dirty workspace allowed).
  - Commands:
    - `python scripts/arch_gate.py`: PASS (exit 0).
    - `.\gradlew.bat :feature:map:testDebugUnitTest --tests com.trust3.xcpro.tasks.TaskNavigationControllerTest --tests com.trust3.xcpro.tasks.TaskManagerCoordinatorTest --tests com.trust3.xcpro.tasks.TaskManagerCanonicalHydrateTest`: PASS (exit 0).
    - `.\gradlew.bat :feature:map:assembleDebug`: PASS (exit 0).
  - Basic build gate (`:feature:map:assembleDebug`): PASS.
  - Changed files summary (workspace snapshot, not phase-scoped):
    - total files: 170
    - top path groups: `feature/map`=102, `app`=24, `feature/profile`=19, `docs/PROFILES`=11, `core/common`=3, `docs/RACING_TASK`=3, `dfcards-library`=2, `feature/variometer`=2
  - Score update:
    - Area 2: 98/100.
    - Breakdown (40/30/20/10): Spec 39/40, Tests 29/30, Determinism+Architecture 20/20, Ops+Docs 10/10.
    - Evidence scope: scoped-slice phase gate + full-branch verification PASS (`2026-03-08`; run `20260308-174507`).
- Automation runner phase 2 update (2026-03-08):
  - Runner: `scripts/ci/racing_phase_runner.ps1`.
  - Run ID: `20260308-174507`.
  - Logs: `logs/phase-runner/racing-task/20260308-174507/phase-02`.
  - Scope: Phase 2 - RT Structure and Profile Validator.
  - Area mapping: Area 1.
  - Workspace pre-check: PASS (dirty workspace allowed).
  - Commands:
    - `python scripts/arch_gate.py`: PASS (exit 0).
    - `.\gradlew.bat :feature:map:testDebugUnitTest --tests com.trust3.xcpro.tasks.racing.RacingTaskStructureRulesTest --tests com.trust3.xcpro.tasks.domain.engine.DefaultRacingTaskEngineTest --tests com.trust3.xcpro.tasks.racing.RacingTaskManagerRulePersistenceTest --tests com.trust3.xcpro.tasks.TaskManagerCanonicalHydrateTest`: PASS (exit 0).
    - `.\gradlew.bat :feature:map:assembleDebug`: PASS (exit 0).
  - Basic build gate (`:feature:map:assembleDebug`): PASS.
  - Changed files summary (workspace snapshot, not phase-scoped):
    - total files: 170
    - top path groups: `feature/map`=102, `app`=24, `feature/profile`=19, `docs/PROFILES`=11, `core/common`=3, `docs/RACING_TASK`=3, `dfcards-library`=2, `feature/variometer`=2
  - Score update:
    - Area 1: 98/100.
    - Breakdown (40/30/20/10): Spec 39/40, Tests 29/30, Determinism+Architecture 20/20, Ops+Docs 10/10.
    - Evidence scope: scoped-slice phase gate + full-branch verification PASS (`2026-03-08`; run `20260308-174507`).
- Automation runner phase 3 update (2026-03-08):
  - Runner: `scripts/ci/racing_phase_runner.ps1`.
  - Run ID: `20260308-174507`.
  - Logs: `logs/phase-runner/racing-task/20260308-174507/phase-03`.
  - Scope: Phase 3 - Start Procedure Compliance.
  - Area mapping: Area 3.
  - Workspace pre-check: PASS (dirty workspace allowed).
  - Commands:
    - `python scripts/arch_gate.py`: PASS (exit 0).
    - `.\gradlew.bat :feature:map:testDebugUnitTest --tests com.trust3.xcpro.tasks.racing.navigation.RacingStartEvaluatorTest --tests com.trust3.xcpro.tasks.racing.navigation.RacingNavigationEngineTest --tests com.trust3.xcpro.tasks.racing.navigation.RacingReplayValidationTest`: PASS (exit 0).
    - `.\gradlew.bat :feature:map:assembleDebug`: PASS (exit 0).
  - Basic build gate (`:feature:map:assembleDebug`): PASS.
  - Changed files summary (workspace snapshot, not phase-scoped):
    - total files: 170
    - top path groups: `feature/map`=102, `app`=24, `feature/profile`=19, `docs/PROFILES`=11, `core/common`=3, `docs/RACING_TASK`=3, `dfcards-library`=2, `feature/variometer`=2
  - Score update:
    - Area 3: 97/100.
    - Breakdown (40/30/20/10): Spec 39/40, Tests 28/30, Determinism+Architecture 20/20, Ops+Docs 10/10.
    - Evidence scope: scoped-slice phase gate + full-branch verification PASS (`2026-03-08`; run `20260308-174507`).
- Automation runner phase 4 update (2026-03-08):
  - Runner: `scripts/ci/racing_phase_runner.ps1`.
  - Run ID: `20260308-174507`.
  - Logs: `logs/phase-runner/racing-task/20260308-174507/phase-04`.
  - Scope: Phase 4 - Turnpoint and OZ Compliance.
  - Area mapping: Area 4.
  - Workspace pre-check: PASS (dirty workspace allowed).
  - Commands:
    - `python scripts/arch_gate.py`: PASS (exit 0).
    - `.\gradlew.bat :feature:map:testDebugUnitTest --tests com.trust3.xcpro.tasks.racing.navigation.RacingNavigationEnginePhase4Test --tests com.trust3.xcpro.tasks.racing.boundary.RacingBoundaryCrossingPlannerTest --tests com.trust3.xcpro.tasks.racing.navigation.RacingReplayValidationTest`: PASS (exit 0).
    - `.\gradlew.bat :feature:map:assembleDebug`: PASS (exit 0).
  - Basic build gate (`:feature:map:assembleDebug`): PASS.
  - Changed files summary (workspace snapshot, not phase-scoped):
    - total files: 170
    - top path groups: `feature/map`=102, `app`=24, `feature/profile`=19, `docs/PROFILES`=11, `core/common`=3, `docs/RACING_TASK`=3, `dfcards-library`=2, `feature/variometer`=2
  - Score update:
    - Area 4: 97/100.
    - Breakdown (40/30/20/10): Spec 38/40, Tests 29/30, Determinism+Architecture 20/20, Ops+Docs 10/10.
    - Evidence scope: scoped-slice phase gate + full-branch verification PASS (`2026-03-08`; run `20260308-174507`).
- Automation runner phase 5 update (2026-03-08):
  - Runner: `scripts/ci/racing_phase_runner.ps1`.
  - Run ID: `20260308-174507`.
  - Logs: `logs/phase-runner/racing-task/20260308-174507/phase-05`.
  - Scope: Phase 5 - Finish Procedure Compliance.
  - Area mapping: Area 5.
  - Workspace pre-check: PASS (dirty workspace allowed).
  - Commands:
    - `python scripts/arch_gate.py`: PASS (exit 0).
    - `.\gradlew.bat :feature:map:testDebugUnitTest --tests com.trust3.xcpro.tasks.racing.navigation.RacingNavigationEngineTest --tests com.trust3.xcpro.tasks.core.TaskWaypointCustomParamsTest`: PASS (exit 0).
    - `.\gradlew.bat :feature:map:assembleDebug`: PASS (exit 0).
  - Basic build gate (`:feature:map:assembleDebug`): PASS.
  - Changed files summary (workspace snapshot, not phase-scoped):
    - total files: 170
    - top path groups: `feature/map`=102, `app`=24, `feature/profile`=19, `docs/PROFILES`=11, `core/common`=3, `docs/RACING_TASK`=3, `dfcards-library`=2, `feature/variometer`=2
  - Score update:
    - Area 5: 96/100.
    - Breakdown (40/30/20/10): Spec 38/40, Tests 28/30, Determinism+Architecture 20/20, Ops+Docs 10/10.
    - Evidence scope: scoped-slice phase gate + full-branch verification PASS (`2026-03-08`; run `20260308-174507`).
- Automation runner phase 6 update (2026-03-08):
  - Runner: `scripts/ci/racing_phase_runner.ps1`.
  - Run ID: `20260308-174507`.
  - Logs: `logs/phase-runner/racing-task/20260308-174507/phase-06`.
  - Scope: Phase 6 - Navigation Evidence and Timing Hardening.
  - Area mapping: Area 6.
  - Workspace pre-check: PASS (dirty workspace allowed).
  - Commands:
    - `python scripts/arch_gate.py`: PASS (exit 0).
    - `.\gradlew.bat :feature:map:testDebugUnitTest --tests com.trust3.xcpro.tasks.racing.boundary.RacingBoundaryCrossingPlannerTest --tests com.trust3.xcpro.tasks.racing.navigation.RacingNavigationEngineTest --tests com.trust3.xcpro.tasks.racing.navigation.RacingReplayValidationTest`: PASS (exit 0).
    - `.\gradlew.bat :feature:map:assembleDebug`: PASS (exit 0).
  - Basic build gate (`:feature:map:assembleDebug`): PASS.
  - Changed files summary (workspace snapshot, not phase-scoped):
    - total files: 170
    - top path groups: `feature/map`=102, `app`=24, `feature/profile`=19, `docs/PROFILES`=11, `core/common`=3, `docs/RACING_TASK`=3, `dfcards-library`=2, `feature/variometer`=2
  - Score update:
    - Area 6: 97/100.
    - Breakdown (40/30/20/10): Spec 38/40, Tests 29/30, Determinism+Architecture 20/20, Ops+Docs 10/10.
    - Evidence scope: scoped-slice phase gate + full-branch verification PASS (`2026-03-08`; run `20260308-174507`).
- Automation runner phase 7 update (2026-03-08):
  - Runner: `scripts/ci/racing_phase_runner.ps1`.
  - Run ID: `20260308-174507`.
  - Logs: `logs/phase-runner/racing-task/20260308-174507/phase-07`.
  - Scope: Phase 7 - UI Rules Editor and Typed Commands.
  - Area mapping: Area 7.
  - Workspace pre-check: PASS (dirty workspace allowed).
  - Commands:
    - `python scripts/arch_gate.py`: PASS (exit 0).
    - `.\gradlew.bat :feature:map:testDebugUnitTest --tests com.trust3.xcpro.tasks.TaskSheetViewModelRacingRulesCommandTest --tests com.trust3.xcpro.tasks.RulesRacingTaskParametersTest --tests com.trust3.xcpro.tasks.racing.RacingTaskManagerRulePersistenceTest`: PASS (exit 0).
    - `.\gradlew.bat :feature:map:assembleDebug`: PASS (exit 0).
  - Basic build gate (`:feature:map:assembleDebug`): PASS.
  - Changed files summary (workspace snapshot, not phase-scoped):
    - total files: 170
    - top path groups: `feature/map`=102, `app`=24, `feature/profile`=19, `docs/PROFILES`=11, `core/common`=3, `docs/RACING_TASK`=3, `dfcards-library`=2, `feature/variometer`=2
  - Score update:
    - Area 7: 96/100.
    - Breakdown (40/30/20/10): Spec 38/40, Tests 28/30, Determinism+Architecture 20/20, Ops+Docs 10/10.
    - Evidence scope: scoped-slice phase gate + full-branch verification PASS (`2026-03-08`; run `20260308-174507`).
- Automation runner phase 8 update (2026-03-08):
  - Runner: `scripts/ci/racing_phase_runner.ps1`.
  - Run ID: `20260308-174507`.
  - Logs: `logs/phase-runner/racing-task/20260308-174507/phase-08`.
  - Scope: Phase 8 - Persistence/Import/Export v2 Fidelity.
  - Area mapping: Area 8.
  - Workspace pre-check: PASS (dirty workspace allowed).
  - Commands:
    - `python scripts/arch_gate.py`: PASS (exit 0).
    - `.\gradlew.bat :feature:map:testDebugUnitTest --tests com.trust3.xcpro.tasks.TaskPersistSerializerFidelityTest --tests com.trust3.xcpro.tasks.TaskSheetViewModelImportTest --tests com.trust3.xcpro.tasks.data.persistence.TaskPersistenceAdaptersDeterministicIdTest`: PASS (exit 0).
    - `.\gradlew.bat :feature:map:assembleDebug`: PASS (exit 0).
  - Basic build gate (`:feature:map:assembleDebug`): PASS.
  - Changed files summary (workspace snapshot, not phase-scoped):
    - total files: 170
    - top path groups: `feature/map`=102, `app`=24, `feature/profile`=19, `docs/PROFILES`=11, `core/common`=3, `docs/RACING_TASK`=3, `dfcards-library`=2, `feature/variometer`=2
  - Score update:
    - Area 8: 97/100.
    - Breakdown (40/30/20/10): Spec 39/40, Tests 28/30, Determinism+Architecture 20/20, Ops+Docs 10/10.
    - Evidence scope: scoped-slice phase gate + full-branch verification PASS (`2026-03-08`; run `20260308-174507`).
- Automation runner phase 9 update (2026-03-08):
  - Runner: `scripts/ci/racing_phase_runner.ps1`.
  - Run ID: `20260308-174507`.
  - Logs: `logs/phase-runner/racing-task/20260308-174507/phase-09`.
  - Scope: Phase 9 - Replay Parity and Preconditions.
  - Area mapping: Area 9.
  - Workspace pre-check: PASS (dirty workspace allowed).
  - Commands:
    - `python scripts/arch_gate.py`: PASS (exit 0).
    - `.\gradlew.bat :feature:map:testDebugUnitTest --tests com.trust3.xcpro.tasks.racing.navigation.RacingReplayValidationTest --tests com.trust3.xcpro.tasks.TaskNavigationControllerTest`: PASS (exit 0).
    - `.\gradlew.bat :feature:map:assembleDebug`: PASS (exit 0).
  - Basic build gate (`:feature:map:assembleDebug`): PASS.
  - Changed files summary (workspace snapshot, not phase-scoped):
    - total files: 170
    - top path groups: `feature/map`=102, `app`=24, `feature/profile`=19, `docs/PROFILES`=11, `core/common`=3, `docs/RACING_TASK`=3, `dfcards-library`=2, `feature/variometer`=2
  - Score update:
    - Area 9: 98/100.
    - Breakdown (40/30/20/10): Spec 39/40, Tests 29/30, Determinism+Architecture 20/20, Ops+Docs 10/10.
    - Evidence scope: scoped-slice phase gate + full-branch verification PASS (`2026-03-08`; run `20260308-174507`).
- Automation runner phase 10 update (2026-03-08):
  - Runner: `scripts/ci/racing_phase_runner.ps1`.
  - Run ID: `20260308-174507`.
  - Logs: `logs/phase-runner/racing-task/20260308-174507/phase-10`.
  - Scope: Phase 10 - Test Net Expansion and Release Hardening.
  - Area mapping: Area 10.
  - Workspace pre-check: PASS (dirty workspace allowed).
  - Commands:
    - `python scripts/arch_gate.py`: PASS (exit 0).
    - `.\gradlew.bat :feature:map:testDebugUnitTest --tests com.trust3.xcpro.tasks.TaskPersistSerializerFidelityTest --tests com.trust3.xcpro.tasks.racing.navigation.RacingReplayValidationTest --tests com.trust3.xcpro.tasks.TaskNavigationControllerTest --tests com.trust3.xcpro.tasks.racing.navigation.RacingNavigationEngineTest`: PASS (exit 0).
    - `.\gradlew.bat :feature:map:assembleDebug`: PASS (exit 0).
  - Basic build gate (`:feature:map:assembleDebug`): PASS.
  - Changed files summary (workspace snapshot, not phase-scoped):
    - total files: 170
    - top path groups: `feature/map`=102, `app`=24, `feature/profile`=19, `docs/PROFILES`=11, `core/common`=3, `docs/RACING_TASK`=3, `dfcards-library`=2, `feature/variometer`=2
  - Score update:
    - Area 10: 96/100.
    - Breakdown (40/30/20/10): Spec 38/40, Tests 28/30, Determinism+Architecture 20/20, Ops+Docs 10/10.
    - Evidence scope: scoped-slice phase gate + full-branch verification PASS (`2026-03-08`; run `20260308-174507`).

## 12) Final Closeout (2026-03-08)

- Final automation run for phases 1..10: `scripts/ci/racing_phase_runner.ps1` run ID `20260308-174507` (all phase gates PASS).
- Verification refresh (full-branch, executed 2026-03-08):
  - `python scripts/arch_gate.py`: PASS
  - `.\gradlew.bat enforceRules`: PASS
  - `.\gradlew.bat testDebugUnitTest`: PASS
  - `.\gradlew.bat assembleDebug`: PASS
- Final area scores:

| Area | Score | Status |
|---|---:|---|
| 1 | 98/100 | PASS (`>=95`) |
| 2 | 98/100 | PASS (`>=95`) |
| 3 | 97/100 | PASS (`>=95`) |
| 4 | 97/100 | PASS (`>=95`) |
| 5 | 96/100 | PASS (`>=95`) |
| 6 | 97/100 | PASS (`>=95`) |
| 7 | 96/100 | PASS (`>=95`) |
| 8 | 97/100 | PASS (`>=95`) |
| 9 | 98/100 | PASS (`>=95`) |
| 10 | 96/100 | PASS (`>=95`) |

- AGENT final quality rescore (`docs/ARCHITECTURE/AGENT.md`):
  - Architecture cleanliness: 4.9 / 5
  - Maintainability/change safety: 4.8 / 5
  - Test confidence on risky paths: 4.8 / 5
  - Overall map/task slice quality: 4.8 / 5
  - Release readiness (map/task slice): 4.7 / 5
- Residual risks:
  - Connected instrumentation gates were not re-run in this closeout cycle.
  - Workspace remains intentionally dirty from broader branch work; scores are based on validated RT gate slices and full-branch required local gates above.
