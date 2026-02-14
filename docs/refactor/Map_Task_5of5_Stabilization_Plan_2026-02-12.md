# Map_Task_5of5_Stabilization_Plan_2026-02-12.md

Superseded by `docs/refactor/Map_Task_5of5_Finalization_Plan_2026-02-13.md`.
Historical baseline record only.

## Purpose

Raise the map/task vertical slice from current "works but high churn" status to
stable release-grade quality with architecture-clean boundaries, single render
ownership, and deterministic regression protection.

Read first:

1. `docs/ARCHITECTURE/ARCHITECTURE.md`
2. `docs/ARCHITECTURE/CODING_RULES.md`
3. `docs/ARCHITECTURE/PIPELINE.md`
4. `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`

## 0) Metadata

- Title: Map/Task 5 of 5 Stabilization and Architecture Hardening
- Owner: XCPro Team
- Date: 2026-02-12
- Issue/PR: QUALITY-20260212-MAP-TASK-5OF5
- Status: Superseded (historical baseline; do not use as active execution contract)

## 1) Scope

- Problem statement:
  - The map/task flow has working behavior but remains costly to change safely.
  - Task rendering is triggered in multiple runtime locations.
  - UI/runtime files have high coupling and large blast radius.
  - Several tests in high-risk paths are still ignored.
- Why now:
  - New map/task feature work will amplify regression risk if ownership and
    boundaries are not tightened first.
- In scope:
  - Map/task rendering trigger ownership and routing.
  - UI to use-case boundary cleanup for map/task flows.
  - Re-enable and extend tests for map/task risky paths.
  - Maintainability decomposition of large map/task orchestration files.
  - Guardrail enforcement additions to keep the system from regressing.
- Out of scope:
  - UI redesign.
  - New task features.
  - Changes to unrelated sensor-fusion math.
- User-visible impact:
  - No intended behavior change.
  - Improved reliability and reduced regressions when toggling/editing task
    overlays and switching task type.

## Baseline (2026-02-12)

- Quality score snapshot for map/task vertical slice:
  - Overall quality: 2.9 / 5
  - Release readiness: 3.6 / 5
  - Architecture cleanliness: 2.6 / 5
  - Maintainability/change safety: 2.4 / 5
  - Test confidence on risky paths: 2.5 / 5
- Evidence:
  - Duplicate `TaskMapRenderRouter.plotCurrentTask(...)` callsites: 7
  - `EntryPointAccessors.fromApplication(...)` callsites in app/feature map: 3
  - Ignored tests in map/task-adjacent areas: 5
  - `feature/map` Kotlin files over 400 LOC: 38
  - High-risk hotspot files:
    - `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt`
    - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContent.kt`
    - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRoot.kt`
    - `feature/map/src/main/java/com/example/xcpro/map/MapInitializer.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManager.kt`
  - `feature/map/src/main/java/com/example/xcpro/tasks/TaskManagerCoordinator.kt`

## Progress Update (2026-02-12)

- Completed in current execution:
  - Introduced a single task-render sync entrypoint:
    - `TaskMapRenderRouter.syncTaskVisuals(...)`
  - Rewired map/task render callsites to that sync API:
    - `TaskMapOverlay`, `MapInitializer`, `MapOverlayManager`,
      `MapOverlayStack` (gesture and AAT FAB flows).
  - Removed direct task-manager state reads from map runtime effects:
    - `MapScreenRuntimeEffects` now consumes ViewModel-bound task type state.
  - Routed AAT gesture/edit operations through use-case + ViewModel callbacks:
    - `MapTasksUseCase` and `MapScreenViewModel` now expose task gesture/edit APIs.
  - Removed map root dependency bypass for task/service/replay managers:
    - `MapScreenRoot` no longer acquires these via `EntryPointAccessors`.
    - Runtime wiring now uses ViewModel/runtime use-case contracts.
  - Removed remaining non-root `EntryPointAccessors.fromApplication(...)` callsites:
    - `tasks/TaskManagerCompat.kt` now resolves via `EntryPoints.get(...)`.
    - `screens/navdrawer/Task.kt` now resolves via `EntryPoints.get(...)`.
    - Runtime code callsites now: 0.
  - Re-enabled previously ignored map/task tests and fixed deterministic execution:
    - `MapScreenViewModelTest`
    - `MapTaskScreenUiTest`
  - Completed hotspot decomposition gates for critical map/task files:
    - `MapScreenContent.kt` reduced to focused orchestration; overlays/debug/task/QNH hosts moved to `MapScreenContentOverlays.kt`.
    - `MapScreenViewModel.kt` reduced and split by responsibility with:
      - `MapScreenTrafficCoordinator.kt`
      - `MapScreenWaypointQnhCoordinator.kt`
      - `MapScreenFlowExtensions.kt`
      - `MapScreenViewModelMappers.kt`
    - Current hotspot LOC snapshot:
      - `MapScreenRoot.kt`: 392
      - `MapScreenContent.kt`: 337
      - `MapScreenViewModel.kt`: 418
      - `MapScreenScaffoldInputs.kt`: 304
  - Updated architecture docs for changed wiring:
    - `docs/ARCHITECTURE/PIPELINE.md`
    - `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md` active-plan pointer.
- Remaining for full 5/5 closure:
  - Continue under `docs/refactor/Map_Task_5of5_Finalization_Plan_2026-02-13.md`.

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Task definition and active leg | Task state owner (`TaskManagerCoordinator` and downstream task state flows) | Use-case + ViewModel state | UI-managed mirrors of task state |
| Task overlay render intent | Map runtime render coordinator (UI runtime layer) | Single render API (`syncTaskVisuals`) | Direct render calls from multiple Composables/managers |
| AAT edit mode UI flag | `MapScreenViewModel` | `StateFlow` in `MapScreenBindings` | Reads from manager internals directly in Composables |

### 2.2 Dependency Direction

Required flow remains:

`UI -> domain -> data`

- Modules/files touched:
  - `feature/map/src/main/java/com/example/xcpro/map/ui/*`
  - `feature/map/src/main/java/com/example/xcpro/map/*`
  - `feature/map/src/main/java/com/example/xcpro/tasks/*`
  - `app/src/androidTest/*` and `feature/map/src/test/*`
- Boundary risk:
  - UI side effects currently call task coordinator operations and render router
    directly in multiple locations.

### 2.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Task overlay clear+plot sequencing | `TaskMapOverlay`, `MapGestureSetup`, `MapTaskIntegration`, `MapInitializer`, `MapOverlayManager` | Dedicated map runtime task render coordinator | Single render ownership and ordering | Unit tests on coordinator + no duplicate direct render calls |
| Task-type/edit-mode side effects in UI | `MapScreenRuntimeEffects` reading `taskManager.taskType` | `MapScreenViewModel` state and intents | UDF compliance and testability | ViewModel tests + no direct manager read in composables |
| Root dependency acquisition | `MapScreenRoot` entry-point lookup of runtime managers | Injected use-case/runtime facade | Lower coupling and controlled dependencies | enforceRules guard + compile-time wiring |

### 2.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRoot.kt` | EntryPoint pulls `TaskManagerCoordinator`/service/replay directly | Injected facade or ViewModel-provided contracts | Phase 2 |
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRuntimeEffects.kt` | Reads `taskManager.taskType` directly | ViewModel `StateFlow` + intents | Phase 2 |
| `feature/map/src/main/java/com/example/xcpro/map/MapGestureSetup.kt` | Mutates task manager + direct plot calls in Composable | Intent callback to ViewModel + runtime coordinator sync | Phase 1/2 |
| `feature/map/src/main/java/com/example/xcpro/map/MapTaskIntegration.kt` | Composable exit button mutates manager + direct plot call | ViewModel intent + runtime coordinator | Phase 1/2 |
| `feature/map/src/main/java/com/example/xcpro/tasks/TaskMapOverlay.kt` | Clears/removes layers and replots directly | Runtime coordinator API | Phase 1 |
| `feature/map/src/main/java/com/example/xcpro/map/MapInitializer.kt` | Direct saved-task plotting | Runtime coordinator initial sync | Phase 1 |
| `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManager.kt` | Direct saved-task plotting | Runtime coordinator initial sync | Phase 1 |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| Task render trigger ordering | Event-driven UI runtime (no wall-time math) | Deterministic sequencing by state transitions |
| Display-pose map updates | Existing live/replay rules from pipeline | Preserve existing deterministic map pose behavior |
| Task persistence timestamps | Existing injected clock path | Prevent wall-time leakage in domain |

Explicitly forbidden comparisons:

- Monotonic vs wall
- Replay vs wall

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - Main: MapLibre style/layer operations, Compose rendering, UI events.
  - Default: Geometry calculations and pure policy evaluation.
  - IO: Persistence and file operations.
- Primary cadence/gating sensor:
  - Task overlay updates are state transition driven, not frame-loop driven.
- Hot-path latency budget:
  - Task overlay refresh after user action: <= 100 ms typical on debug devices.

### 2.5 Replay Determinism

- Deterministic for same input: Yes (must remain yes).
- Randomness used: No.
- Replay/live divergence rules:
  - Replay follows IGC timestamps and must keep identical task/nav transitions
    across repeated runs for same replay file and settings.

### 2.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| Direct task manager reads in Composables | `ARCHITECTURE.md` 5B, `CODING_RULES.md` 8 | enforceRules pattern + UI tests | `build-logic/src/main/kotlin/...`, `feature/map/src/test/...` |
| Multi-owner render plotting | SSOT/UDF rules | Unit + integration tests | New runtime render coordinator tests |
| EntryPoint dependency leakage in UI root | Dependency direction, VM/use-case boundaries | enforceRules + review checklist | `MapScreenRoot.kt` |
| Flaky high-risk coverage due ignored tests | Testing expectations | Re-enable tests + deterministic fakes | `MapTaskScreenUiTest.kt`, `MapScreenViewModelTest.kt` |

## 3) Data Flow (Before -> After)

Before:

```
Task state changes -> several UI/runtime callsites clear/plot -> Map SDK
```

After:

```
Task UI intent -> ViewModel -> Task use-case/coordinator state
    -> runtime render coordinator (single owner)
        -> Map SDK clear/plot
```

## 4) Implementation Phases

### Phase 0 - Baseline + Guardrails
- Goal:
  - Freeze baseline behavior and prevent new boundary leaks while refactor starts.
- Files to change:
  - enforceRules ruleset files (build logic).
  - Test fakes/helpers for map/task runtime surfaces.
- Tests to add/update:
  - Add failing-first tests for duplicate plotting order and task-type switch
    overlay cleanup.
  - Convert existing ignored tests to pending with explicit blocker list.
- Exit criteria:
  - New guardrails merged.
  - Baseline test matrix documented with pass/ignore reasons.

### Phase 1 - Single Task Render Owner
- Goal:
  - Introduce one runtime owner for task overlay synchronization.
- Files to change:
  - `feature/map/src/main/java/com/example/xcpro/tasks/TaskMapOverlay.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/MapInitializer.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManager.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/MapGestureSetup.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/MapTaskIntegration.kt`
  - New `feature/map/src/main/java/com/example/xcpro/map/runtime/TaskRenderCoordinator.kt` (or equivalent).
- Tests to add/update:
  - Coordinator unit tests: initial load, task type switch, AAT edit toggle,
    drag update.
  - Integration test: map-ready + saved task renders exactly once.
- Exit criteria:
  - Only one path performs clear+plot.
  - Direct `TaskMapRenderRouter.plotCurrentTask` callsites reduced to owner only.

### Phase 2 - UI Boundary Hardening
- Goal:
  - Remove direct manager/controller reads/mutations from map Composables and
    root dependency acquisition bypasses.
- Files to change:
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRoot.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRuntimeEffects.kt`
  - `feature/map/src/main/java/com/example/xcpro/di/MapUseCaseEntryPoint.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/MapScreenUseCases.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt`
- Tests to add/update:
  - ViewModel tests for task-type/edit-mode side effects.
  - UI tests validating intent-only wiring for task interactions.
- Exit criteria:
  - `MapScreenRoot` no longer retrieves task/service/replay managers directly
    from app entry point.
  - Composables consume ViewModel state and callbacks only.

### Phase 3 - Maintainability Decomposition
- Goal:
  - Reduce change blast radius by splitting orchestration hotspots.
- Files to change:
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContent.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRoot.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenScaffoldInputs.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt`
- Tests to add/update:
  - Snapshot/contract tests for extracted presenters/assemblers.
- Exit criteria:
  - `MapScreenRoot.kt`, `MapScreenContent.kt`, and `MapScreenViewModel.kt`
    each reduced below 450 LOC or split into clearly bounded collaborators.
  - `MapScreenScaffoldInputs` replaced by grouped sub-contracts with explicit
    ownership.

### Phase 4 - Test Confidence and Determinism Closure
- Goal:
  - Eliminate ignored test debt on high-risk map/task paths and lock behavior.
- Files to change:
  - `feature/map/src/test/java/com/example/xcpro/map/MapScreenViewModelTest.kt`
  - `feature/map/src/test/java/com/example/xcpro/map/ui/task/MapTaskScreenUiTest.kt`
  - Additional map/task integration tests in `app/src/androidTest`.
- Tests to add/update:
  - Re-enable currently ignored map/task tests by replacing runtime-coupled
    dependencies with deterministic fakes.
  - Add replay determinism assertion for task nav transitions when relevant.
- Exit criteria:
  - Zero ignored tests in map/task critical path.
  - Stable reruns with no nondeterministic failures across two consecutive runs.

### Phase 5 - Documentation and Final Verification
- Goal:
  - Align docs and verification evidence with final architecture.
- Files to change:
  - `docs/ARCHITECTURE/PIPELINE.md` (if wiring changes).
  - `docs/ARCHITECTURE/ARCHITECTURE.md` or `CODING_RULES.md` (if new rules).
  - `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md` only if explicitly approved.
  - `docs/ADS-b/Agent-Execution-Contract.md`.
- Tests to add/update:
  - N/A (documentation only) plus full mandatory verification.
- Exit criteria:
  - Required checks pass.
  - Autonomous execution contract references final plan and acceptance gates.

## 5) Test Plan

- Unit tests:
  - Task render coordinator sequencing and idempotence.
  - ViewModel intent/state transitions for task mode and edit mode.
- Replay/regression tests:
  - Same replay input produces same task transition outputs.
- UI/instrumentation tests:
  - Task overlay toggle/edit flows on device/emulator.
- Degraded/failure-mode tests:
  - Map not ready, task empty, style reload, overlay disabled.
- Boundary tests for removed bypasses:
  - Fail if Composables directly call task manager mutation APIs.

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Optional when relevant:

```bash
./gradlew connectedDebugAndroidTest
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Hidden behavior dependency on duplicate render calls | Medium/high regression | Add golden sequencing tests before removal | XCPro Team |
| Map SDK lifecycle edge cases while consolidating rendering | High | Keep owner API narrow; test map-ready/map-null/style reload paths | XCPro Team |
| Refactor drift reintroducing bypasses | Medium | enforceRules additions + review checklist | XCPro Team |
| Test flakiness from runtime dependencies | Medium | deterministic fakes and explicit test clocks | XCPro Team |

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`.
- No duplicate render ownership for task overlays.
- No direct manager mutation/query from map Composables for task business paths.
- No root UI dependency bypass for task/service/replay managers.
- Zero ignored tests in map/task critical path.
- Required verification commands pass locally.

Scorecard criteria for "5 / 5":

- Overall quality:
  - All acceptance gates pass and no P0/P1 findings in code review.
- Release readiness:
  - `enforceRules`, unit tests, assemble, and connected tests all green.
- Architecture cleanliness:
  - Boundary-bypass removal table fully closed.
- Maintainability/change safety:
  - Hotspot decomposition gates met; render ownership singular.
- Test confidence:
  - Ignored-test debt cleared and deterministic rerun stability confirmed.

## 8) Rollback Plan

- What can be reverted independently:
  - Render coordinator introduction can be reverted without touching task
    persistence and business logic.
  - UI boundary hardening can be reverted by feature flag at facade level if
    needed.
- Recovery steps if regression is detected:
  1. Disable new render coordinator path behind runtime flag.
  2. Restore previous render routing while keeping new tests for diagnosis.
  3. Patch failing edge case and re-enable consolidated path.

## 9) Follow-On ROI Hardening Backlog (2026-02-12)

ROI definition used here:
- High ROI = large reduction in regression risk/change blast-radius per unit of engineering effort.

### Workstream A - Single trigger owner for task render sync (High ROI, Completed 2026-02-12)

- Original observation (before fix):
  - Task render sync is API-centralized, but trigger calls are still spread across:
    - `feature/map/src/main/java/com/example/xcpro/map/MapInitializer.kt`
    - `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManager.kt`
    - `feature/map/src/main/java/com/example/xcpro/map/ui/MapOverlayStack.kt`
    - `feature/map/src/main/java/com/example/xcpro/tasks/TaskMapOverlay.kt`
- Implemented fix:
  - Added `feature/map/src/main/java/com/example/xcpro/map/TaskRenderSyncCoordinator.kt`.
  - Rewired trigger callsites to coordinator events:
    - `TaskMapOverlay` -> `onTaskStateChanged(signature)`
    - `MapOverlayStack` gesture/AAT edit callbacks -> `requestTaskRenderSync()`
    - `MapInitializer` -> `onMapReady(map)`
    - `MapOverlayManager` style/overlay refresh and clear paths -> coordinator methods
  - Added state-signature dedupe + pending-map handling in coordinator.
  - Added unit coverage:
    - `feature/map/src/test/java/com/example/xcpro/map/TaskRenderSyncCoordinatorTest.kt`
- Validation outcome:
  - Direct production `syncTaskVisuals(...)` callsites are now reduced to coordinator ownership:
    - `TaskRenderSyncCoordinator` default renderer callback (`TaskMapRenderRouter::syncTaskVisuals`)
  - No other production direct calls remain.

### Workstream B - Decompose remaining high-complexity hotspots (Medium ROI, Completed 2026-02-12)

- Implemented fix:
  - `MapInitializer` responsibilities decomposed into dedicated collaborators:
    - `feature/map/src/main/java/com/example/xcpro/map/MapScaleBarController.kt`
    - `feature/map/src/main/java/com/example/xcpro/map/MapInitializerDataLoader.kt`
  - `TaskManagerCoordinator` persistence and engine-sync orchestration extracted to:
    - `feature/map/src/main/java/com/example/xcpro/tasks/TaskCoordinatorPersistenceBridge.kt`
  - Existing runtime behavior preserved while reducing coordinator/initializer responsibility density.
- Validation outcome:
  - Current hotspot LOC snapshot after split:
    - `feature/map/src/main/java/com/example/xcpro/map/MapInitializer.kt` -> 240 LOC
    - `feature/map/src/main/java/com/example/xcpro/tasks/TaskManagerCoordinator.kt` -> 331 LOC
  - Refactored classes compile and pass required verification gates.

### Workstream C - Eliminate remaining runtime entrypoint lookups entirely (Medium ROI, Completed 2026-02-12)

- Implemented fix:
  - Replaced `TaskManagerCompat` entrypoint lookup with Hilt ViewModel host:
    - `TaskManagerCoordinatorHostViewModel` in `TaskManagerCompat.kt`
  - Replaced Task navdrawer entrypoint lookup with Hilt-injected ViewModel:
    - `TaskScreenUseCasesViewModel`
  - Removed obsolete entrypoint contract:
    - deleted `feature/map/src/main/java/com/example/xcpro/di/MapUseCaseEntryPoint.kt`
- Validation outcome:
  - Runtime production callsites:
    - `EntryPointAccessors.fromApplication(...)` -> 0
    - `EntryPoints.get(...)` -> 0
  - Dependency ownership now routes via Hilt-injected ViewModel/use-case contracts.

### Verification gates for backlog execution

- Required:
  - `./gradlew enforceRules`
  - `./gradlew testDebugUnitTest`
  - `./gradlew assembleDebug`
- On attached emulator/device:
  - `./gradlew connectedDebugAndroidTest --no-parallel`
