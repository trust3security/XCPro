# Task/AAT Ownership Release-Grade Phased IP

## 0) Metadata

- Title: Recover release-grade task/AAT ownership without band-aid state sharing
- Owner: XCPro Team
- Date: 2026-03-15
- Issue/PR: RULES-20260315-18 / TBD
- Status: Completed
- Execution rules:
  - This is an ownership-recovery track, not a cleanup track.
  - Fix authority seams before size cleanup.
  - Do not "solve" stale task state by widening `TaskRepository` scope alone.
  - Keep `feature:tasks` task-core-owned and `feature:map` / `feature:map-runtime` MapLibre-owned.
- Progress note:
  - 2026-03-15: plan created from deep seam/code pass findings.
  - 2026-03-15: Phase 1 landed. `TaskManagerCoordinator` now publishes `TaskRuntimeSnapshot`, and glide/IGC/map-runtime consumers read that seam instead of `TaskRepository.state`.
  - 2026-03-15: Phase 2 landed. `TaskSheetViewModel` now collects coordinator snapshots, `TaskRepository` is projection-only, and live AAT target param/lock now travel in the canonical task payload for task-sheet projection.
  - 2026-03-15: Phase 3 landed. AAT autosave and named-task persistence now use canonical JSON payload storage with legacy CUP/prefs read fallback only, and save/load/restore tests lock target metadata fidelity.
  - 2026-03-15: Phase 4 landed. Task managers are runtime mutation only, coordinator autosave now flows through `TaskCoordinatorPersistenceBridge` + `TaskEnginePersistenceService`, and task-navigation listener teardown is caller-scope bound.
  - 2026-03-15: Phase 5 landed. Task gesture/runtime ownership moved into `feature:map`, `TaskManagerCoordinator` stopped constructing MapLibre gesture handlers, and `feature:tasks` no longer carries the MapLibre dependency.
  - 2026-03-15: Phase 6 landed. `enforceRules` now guards against MapLibre imports/dependencies in `feature:tasks`, the architecture docs are synced, required verification passed, and deviation `RULES-20260315-18` is resolved.

## 1) Scope

- Problem statement:
  - Task runtime ownership is split across `TaskManagerCoordinator`, `TaskRepository`, task managers, `TaskSheetViewModel`, and persistence bridges.
  - Startup restore updates the coordinator/manager path, while `GlideTargetRepository` and `TaskRepositoryIgcTaskDeclarationSource` read `TaskRepository.state`.
  - AAT target state (`targetParam`, `targetLocked`, target coordinates) is duplicated across repository memory, manager state, serializer state, and ViewModel sync helpers.
  - `AATTaskManager` still constructs persistence/file I/O and writes prefs directly.
  - AAT MapLibre gesture/runtime ownership previously leaked into `feature:tasks`, which blurred the documented map/runtime module boundary.
  - `TaskNavigationController` registers coordinator listeners without an explicit lifetime contract.
- Why now:
  - These are release-relevant seams: stale restored task state, target metadata loss, and unclear persistence ownership.
- In scope:
  - Establish one canonical runtime read authority for active task state.
  - Demote `TaskRepository` from cross-feature authority to derived UI projection.
  - Collapse AAT target authority into the canonical task path.
  - Remove manager-owned persistence/file I/O from task managers.
  - Move retained AAT MapLibre edit/runtime ownership out of `feature:tasks`.
  - Make leg-change observation lifetime explicit.
- Out of scope:
  - New task features or task UI redesign.
  - Business-rule rewrites unrelated to ownership.
  - Broad map-shell cleanup outside the AAT runtime seam.
- User-visible impact:
  - No intended feature changes.
  - Expected correctness gains: restored tasks are available everywhere on first load, and AAT target state survives save/load/restore consistently.
- Rule class touched: Invariant

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Active task runtime snapshot (`taskType`, `task`, `activeLeg`) | `TaskManagerCoordinator` | read-only `StateFlow<TaskRuntimeSnapshot>` | `TaskRepository.state` as cross-feature authority, ViewModel mirrors |
| AAT target state (`targetParam`, `targetLocked`, target coordinates) | canonical task snapshot owned by `TaskManagerCoordinator` | waypoint custom params + target snapshots + named mutation methods | `TaskRepository.targetStateByKey`, `TaskSheetViewModel.syncAatTargetAt(...)`, manager-only prefs state |
| Task UI projection | `TaskRepository` or replacement projector | read-only `StateFlow<TaskUiState>` derived from coordinator snapshot | non-UI consumers treating the projector as authoritative |
| Task persistence/autosave/named-task content | `TaskEnginePersistenceService` and adapters | explicit save/load/restore APIs | manager-owned save side effects |

### 2.1A State Contract

| State Item | Authoritative Owner | Allowed Mutators | Read Path | Derived From | Persistence Owner | Reset/Clear Conditions | Time Base | Required Tests |
|---|---|---|---|---|---|---|---|---|
| `TaskRuntimeSnapshot` | `TaskManagerCoordinator` | named coordinator mutations, restore/load bridge | projector, glide, IGC, map runtime | current task manager/engine state | `TaskEnginePersistenceService` | clear, task-type switch, restore/load | N/A | coordinator snapshot, restore, load tests |
| `TaskUiState` | task UI projector | none outside projector recompute | `TaskSheetViewModel` and task UI only | `TaskRuntimeSnapshot` | none; derived only | follows coordinator snapshot | N/A | projection compliance, VM tests |
| AAT target state | `TaskManagerCoordinator` canonical task snapshot | `setTargetParam`, `toggleTargetLock`, target drag/update intents | projector, render sync, serializer, persistence bridge | persisted custom params and task-edit intents | `TaskEnginePersistenceService` | clear, reorder/remove, switch, load/restore | N/A | import, drag/update, serializer fidelity, restore tests |
| Leg-change observation | `TaskManagerCoordinator` | active-leg changes only | `TaskNavigationController`, task sheet VM | coordinator active leg | none | clear, reset, switch, owner teardown | existing navigation contract | listener lifetime tests |

### 2.2 Dependency Direction

Confirm dependency flow remains:

`UI -> domain -> data`

- Modules/files touched:
  - `feature/tasks/src/main/java/com/example/xcpro/tasks/**`
  - `feature/map/src/main/java/com/example/xcpro/glide/**`
  - `feature/map/src/main/java/com/example/xcpro/igc/data/**`
  - `feature/map/src/main/java/com/example/xcpro/tasks/**`
  - task DI wiring in `app` and/or `feature:tasks`
- Any boundary risk:
  - accidental `feature:tasks -> feature:map` back-edge while moving AAT MapLibre code,
  - replacing one duplicate owner with another projector owner.

### 2.2A Reference Pattern Check

| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| `feature/map/src/main/java/com/example/xcpro/map/MapTrafficCoordinatorAdapters.kt` | read-only ports with explicit mutation methods | private mutable, public read-only port, named mutators only | task snapshot is a public cross-module contract, not an internal shell helper |
| `feature/map-runtime/src/main/java/com/example/xcpro/map/TaskRenderSyncCoordinator.kt` | one downstream side-effect owner over authoritative task state | single owner, event-driven sync, no ad hoc external writes | task authority also carries persistence-relevant payload, not just render signatures |

### 2.2B Boundary Moves

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Cross-feature task runtime read authority | `TaskRepository` plus coordinator properties/managers | `TaskManagerCoordinator` snapshot flow | restored and live task state must be identical for all consumers | coordinator/glide/IGC restore tests |
| Task UI projection | `TaskRepository` as de facto SSOT | `TaskRepository` as derived projector only | keep task UI shaping local without owning runtime authority | projection compliance + VM tests |
| AAT target lock/param/position authority | repository memory + manager task + VM sync helpers | canonical coordinator snapshot/custom-params path | remove hidden bidirectional sync and data loss | target roundtrip + import + restore tests |
| Task persistence/file I/O | task managers plus persistence service | `TaskEnginePersistenceService` and adapters only | one persistence owner | roundtrip + failure tests |
| AAT MapLibre edit/gesture runtime | retained classes in `feature:tasks` | `feature:map` / `feature:map-runtime` | keep MapLibre ownership with map runtime modules | compile checks + AAT gesture regressions |

### 2.2C Bypass Removal Plan

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `feature/map/src/main/java/com/example/xcpro/glide/GlideTargetRepository.kt` | reads `TaskRepository.state` as task authority | derive from coordinator snapshot flow plus navigation state | Phase 1 |
| `feature/map/src/main/java/com/example/xcpro/igc/data/IgcMetadataSources.kt` | reads `TaskRepository.state` as task authority | derive from coordinator snapshot flow | Phase 1 |
| `feature/tasks/src/main/java/com/example/xcpro/tasks/TaskSheetViewModel.kt` | `sync()` mirrors coordinator state into repository and pushes AAT target state back | VM consumes projection flow only; mutations route through named use-case/coordinator intents | Phase 2 |
| `feature/tasks/src/main/java/com/example/xcpro/tasks/TaskCoordinatorPersistenceBridge.kt` | manager `getCoreTask()` path strips target custom params before persistence | bridge persists canonical snapshot or a canonical mapper output with full target payload | Phase 3-4 |
| `feature/tasks/src/main/java/com/example/xcpro/tasks/TaskNavigationController.kt` | registers a coordinator listener without explicit owner teardown | flow-based observation or removable registration bound to caller scope | Phase 4 |

### 2.2D File Ownership Plan

| File | New / Existing | Owner / Responsibility | Why Here | Why Not Another Layer/File | Split Needed? |
|---|---|---|---|---|---|
| `docs/refactor/Task_AAT_Ownership_Release_Grade_Phased_IP_2026-03-15.md` | New | remediation contract for this track | active phased plan belongs in `docs/refactor` | not a durable ADR by itself | No |
| `feature/tasks/src/main/java/com/example/xcpro/tasks/TaskManagerCoordinator.kt` | Existing | canonical runtime authority and named task mutations | coordinator already routes task-type-specific behavior | not UI, not persistence adapter, not map runtime | Yes, if snapshot publishing pushes file near budget |
| `feature/tasks/src/main/java/com/example/xcpro/tasks/TaskRuntimeSnapshot.kt` | New | read-only cross-module task snapshot contract | snapshot belongs with the owner module | not in `feature:map` or UI projector | No |
| `feature/tasks/src/main/java/com/example/xcpro/tasks/TaskRepository.kt` | Existing | task UI projection only | task UI model shaping stays in task feature | should not own cross-feature runtime state | Possible rename/split after demotion |
| `feature/tasks/src/main/java/com/example/xcpro/tasks/TaskSheetViewModel.kt` | Existing | screen state, intents, orchestration only | task sheet VM remains the screen owner | not a task authority or persistence owner | No |
| `feature/tasks/src/main/java/com/example/xcpro/tasks/TaskCoordinatorPersistenceBridge.kt` | Existing | coordinator-to-persistence bridge only | already sits between runtime owner and persistence service | not a manager or ViewModel concern | No |
| `feature/tasks/src/main/java/com/example/xcpro/tasks/TaskNavigationController.kt` | Existing | racing navigation orchestration over canonical task state | navigation controller already owns engine integration | listener lifetime should not hide in DI | No |
| `feature/tasks/src/main/java/com/example/xcpro/tasks/aat/AATTaskManager.kt` | Existing | AAT task mutation/orchestration only, no persistence/file I/O | manager remains the AAT mutation host for now | MapLibre runtime and persistence do not belong here | No |
| `feature/map/src/main/java/com/example/xcpro/glide/GlideTargetRepository.kt` | Existing | derived glide target projection | map feature owns glide consumer state | not authoritative task owner | No |
| `feature/map/src/main/java/com/example/xcpro/igc/data/IgcMetadataSources.kt` | Existing | derived IGC declaration snapshot | IGC data source owns declaration assembly | not authoritative task owner | No |
| `feature/map-runtime/src/main/java/com/example/xcpro/gestures/TaskGestureHandler.kt` | Existing | map-runtime-owned task gesture contract carrying `MapLibreMap` runtime context | the contract is only used by map gesture/runtime wiring | not task-core while it remains map-typed | No |
| `feature/map-runtime/src/main/java/com/example/xcpro/tasks/aat/gestures/AatGestureHandler.kt` | Existing | MapLibre gesture runtime for AAT edit interactions | active MapLibre gesture code belongs with map/runtime shell | not in task-core module | No |
| `feature/map-runtime/src/main/java/com/example/xcpro/tasks/aat/map/AATMapCoordinateConverter.kt` | Existing | MapLibre projection/tap conversion helper shared by AAT map interaction code | reused by map-owned AAT interaction helpers | not task-core and should not anchor a reverse dependency | No |
| `feature/tasks/src/main/java/com/example/xcpro/tasks/aat/interaction/AATEditModeManager.kt` | Existing | task edit-state and target-update policy only | task feature still owns edit-state semantics | map-typed overlay/hit-test helpers were removed in Phase 5 | No |

### 2.2E Module and API Surface

| Contract / API | Owner | Consumers | Visibility | Why Needed | Compatibility / Removal Plan |
|---|---|---|---|---|---|
| `TaskRuntimeSnapshot` + read-only snapshot flow | `feature:tasks` coordinator | task UI projector, glide, IGC, map runtime | public cross-module | one canonical task read contract | keep `currentTask` / `taskType` accessors temporarily, then narrow |
| projector input contract from coordinator snapshot to task UI state | `feature:tasks` projector | `TaskSheetViewModel`, task UI | internal/public as needed | lets task UI keep a tailored `TaskUiState` without owning runtime authority | may stay permanently; rename if `TaskRepository` becomes misleading |
| canonical persistence input carrying full AAT target metadata | `feature:tasks` persistence bridge/service boundary | persistence service and adapters | internal | prevents target payload loss on save/load/restore | remove legacy manager-sync adapter path after parity is locked |

### 2.2F Scope Ownership and Lifetime

| Scope / Owner | Why It Exists | Dispatcher | Cancellation Trigger | Why Not Caller-Owned / Existing Scope |
|---|---|---|---|---|
| `TaskManagerCoordinator` runtime scope | startup restore, autosave/persistence bridge work | `IO` | coordinator/DI owner teardown | cross-screen task runtime work must outlive a single ViewModel |
| `TaskNavigationController.bind(...)` caller scope | collect navigation fixes and bind navigation lifetime to the caller | caller-owned | returned `Job` / caller scope cancellation | controller must not become a hidden long-lived lifecycle host |
| AAT map edit/gesture runtime scope | tie MapLibre edit handling to map lifecycle only | `Main` | map detach / screen teardown | edit runtime must not outlive the map shell |

### 2.2G Compatibility Shim Inventory

| Shim / Bridge | Owner | Reason | Target Replacement | Removal Trigger | Test Coverage |
|---|---|---|---|---|---|
| `TaskRepository` as temporary projector over coordinator snapshot | `feature:tasks` | keep task UI stable while non-UI consumers migrate | explicit projector name/type or slimmer repository role | all non-UI consumers read coordinator snapshot directly | projection compliance + VM tests |
| `TaskCoordinatorPersistenceBridge` | `feature:tasks` | transition managers toward one persistence owner without big-bang engine rewrite | direct canonical snapshot persistence path | managers no longer own persistence/file I/O | save/load/restore/failure tests |
| legacy `currentTask` / `taskType` accessors | `TaskManagerCoordinator` | compatibility for existing task callsites while snapshot flow is introduced | consumers read snapshot or named methods only | cross-feature raw accessor use is gone | coordinator and consumer tests |

### 2.2H Canonical Formula / Policy Owner

| Formula / Constant / Policy | Canonical Owner File | Reused By | Why This Owner Is Canonical | Temporary Duplicates Allowed? |
|---|---|---|---|---|
| AAT target custom-parameter encoding (`targetLat`, `targetLon`, `targetParam`, `targetLocked`) | `feature/tasks/src/main/java/com/example/xcpro/tasks/core/TaskWaypointCustomParams.kt` | coordinator mapping, serializer, persistence bridge, managers | typed custom-parameter contract already exists here and should remain the only payload authority | No |

### 2.2I Stateless Object / Singleton Boundary

No new Kotlin `object` or singleton-like holder is planned in this track.

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| IGC declaration `capturedAtUtcMs` | Wall | recorder/declaration metadata is a real capture timestamp |
| named-task file timestamps / export metadata | Wall | persistence metadata and filenames are wall-clock artifacts only |
| navigation transition timing already owned by racing navigation path | existing monotonic/replay contract, unchanged | this plan must not alter racing navigation time semantics |

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - `Main`: MapLibre edit/render shell and ViewModel/UI updates.
  - `Default`: pure projection/validation recompute where needed.
  - `IO`: restore, save/load, autosave, prefs/file persistence.
- Primary cadence/gating sensor:
  - event-driven task mutations and restore/load events only; no new polling loops.
- Hot-path latency budget:
  - restored or mutated task state should reach projector/glide/IGC consumers within a single owner update cycle.

### 2.4A Logging and Observability Contract

No logging changes are planned in this track.

### 2.5 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No new randomness
- Replay/live divergence rules:
  - replay continues to use replay-owned timing/events only,
  - ownership refactors must not change replay output for identical task and fix sequences.

### 2.5A Error and Degraded-State Contract

| Condition | Category | Owner | User-Visible Behavior | Retry / Fallback Policy | Test Coverage |
|---|---|---|---|---|---|
| startup restore loads a task before any task UI is created | Recoverable | `TaskManagerCoordinator` + persistence bridge | restored task is immediately available to all task consumers | no fallback mirror path; snapshot must be canonical | coordinator restore + glide/IGC restore tests |
| named/autosave save fails | Degraded | `TaskEnginePersistenceService` | explicit failure result; task remains in memory | no silent manager retry loops | persistence failure tests |
| IGC declaration encounters invalid waypoint coordinates | User Action | IGC declaration source | current invalid/absent result only | keep current invalid/absent contract | `IgcTaskDeclarationSourceTest` |
| map detaches during AAT edit/runtime move | Degraded | map runtime owner | edit preview is cleared without corrupting canonical task state | clear transient preview only | AAT gesture/runtime lifecycle tests |

### 2.5B Identity and Model Creation Strategy

| Entity / Value | Created By | ID / Time Source | Deterministic Required? | Why This Boundary Owns Creation |
|---|---|---|---|---|
| fallback IGC declaration task ID (`TASK-$sessionId`) | `TaskRepositoryIgcTaskDeclarationSource` | existing `sessionId` input | Yes | declaration source owns recorder-session-derived fallback metadata |

### 2.5C No-Op / Test Wiring Contract

| Class / Boundary | NoOp / Convenience Path | Production Allowed? | Safe Degraded Behavior | Visibility / Guardrail |
|---|---|---|---|---|
| `TaskManagerCoordinator` | nullable engines/persistence service constructor parameters | Temporary compatibility only | owner still functions for in-memory task mutation paths | cover explicit no-persistence behavior in tests until cleanup lands |

### 2.6 Enforcement Coverage

| Risk | Rule Reference | Guard Type | File/Test |
|---|---|---|---|
| `TaskRepository` re-expands as cross-feature SSOT | `ARCHITECTURE.md` "Authoritative State Contract" | enforceRules + review + targeted tests | scoped ownership guard, `GlideTargetRepositoryTest`, `IgcTaskDeclarationSourceTest` |
| startup restore updates coordinator but not downstream consumers | authoritative state + restore rules | unit tests | coordinator restore tests, glide/IGC restore tests |
| AAT target metadata is lost on save/load/restore | authoritative state + persistence ownership rules | unit tests + review | `TaskPersistSerializerFidelityTest`, new roundtrip tests, import tests |
| manager-owned persistence/file I/O returns | persistence ownership rules | enforceRules + review | task manager scans, targeted persistence tests |
| retained or duplicate AAT MapLibre runtime code drifts back into `feature:tasks` | MapLibre/runtime ownership rule | enforceRules + compile review | `feature/tasks/build.gradle.kts`, scoped import scan, Phase 5 map/runtime tests |
| coordinator listener lifetime remains implicit | scope ownership rules | unit tests + review | `TaskNavigationControllerTest` and new lifetime tests |

### 2.7 Visual UX SLO Contract

| Outcome | SLO ID | Baseline | Target | Validation/Test | Phase Gate |
|---|---|---|---|---|---|
| Restored task appears consistently across map/task consumers on first open | TASK-UX-01 | task sheet path currently acts as an accidental refresh trigger | restored task is available before task sheet creation and stays consistent | restore tests plus manual cold-start smoke | Phase 1-2 |
| AAT edit enter/drag/exit behavior remains stable while runtime ownership moves | TASK-UX-02 | current behavior is accepted but owned by the wrong module | no visible regression in edit overlay, drag commit, and exit behavior | AAT gesture tests plus manual map edit smoke | Phase 5 |

## 3) Data Flow (Before -> After)

Before:

```text
Task UI intent -> TaskSheetViewModel -> TaskManagerCoordinator mutations
              -> TaskRepository.updateFrom(...) via manual sync()

Startup restore / named-task load -> TaskCoordinatorPersistenceBridge
                                  -> task managers / task engines
                                  -> TaskRepository not updated unless task sheet sync() later runs

Glide / IGC -> TaskRepository.state

AAT target edits/import -> repository target memory <-> VM sync helpers <-> manager / serializer / bridge

Persistence -> manager save side effects + TaskEnginePersistenceService
```

After:

```text
Task UI intent -> ViewModel / use-case -> TaskManagerCoordinator named mutations
                -> canonical TaskRuntimeSnapshot

TaskRuntimeSnapshot -> task UI projector
                        -> GlideTargetRepository
                        -> IGC declaration source
                        -> TaskRenderSyncCoordinator / map runtime
                        -> TaskEnginePersistenceService bridge

AAT target edits/import -> named coordinator mutations -> canonical target payload

Persistence -> TaskEnginePersistenceService / adapters only
            -> restore/load updates coordinator snapshot directly
```

## 4) Implementation Phases

### Phase 0 - Baseline and contract lock

- Goal:
  - Freeze seam inventory and lock release-relevant test expectations.
- Files to change:
  - plan/deviation docs and targeted tests only
- Ownership/file split changes in this phase:
  - none in production code
- Tests to add/update:
  - restore without task sheet creation,
  - glide target after restore,
  - IGC task declaration after restore,
  - AAT target roundtrip,
  - task-navigation listener lifetime.
- Exit criteria:
  - seam list and phase order are locked with named tests/files.

### Phase 1 - Coordinator authority contract

- Status:
  - Completed 2026-03-15.

- Goal:
  - Introduce a canonical coordinator snapshot flow and move non-UI consumers to it first.
- Files to change:
  - `TaskManagerCoordinator.kt`
  - `TaskRuntimeSnapshot.kt`
  - `GlideTargetRepository.kt`
  - `IgcMetadataSources.kt`
  - related tests
- Ownership/file split changes in this phase:
  - `TaskManagerCoordinator` becomes the explicit runtime read owner for cross-feature task consumers.
- Tests to add/update:
  - coordinator snapshot restore/load tests
  - `GlideTargetRepositoryTest`
  - `IgcTaskDeclarationSourceTest`
- Exit criteria:
  - restored or loaded tasks are visible to glide and IGC without opening the task sheet,
  - no scoped non-UI consumer reads `TaskRepository.state`.

### Phase 2 - UI projection demotion and sync removal

- Status:
  - Completed 2026-03-15.

- Goal:
  - Remove manual bidirectional sync and make the repository a pure projection path for task UI.
- Files to change:
  - `TaskRepository.kt`
  - `TaskSheetViewModel.kt`
  - `TaskSheetUseCase.kt`
  - `TaskSheetCoordinatorUseCase.kt`
  - task UI tests
- Ownership/file split changes in this phase:
  - `TaskSheetViewModel` stops being a task authority sync host.
  - `TaskRepository` becomes a projector derived from coordinator state only.
- Tests to add/update:
  - `TaskRepositoryProjectionComplianceTest`
  - task sheet VM state/intent tests
- Exit criteria:
  - `TaskSheetViewModel.sync()` authority role is removed,
  - task UI still renders from derived `TaskUiState`.

### Phase 3 - AAT target authority consolidation

- Status:
  - Completed 2026-03-15.

- Goal:
  - Move AAT target lock/param/position to the canonical task payload and remove duplicated state semantics.
- Files to change:
  - `feature/tasks/src/main/java/com/example/xcpro/tasks/data/persistence/AATCanonicalTaskStorage.kt`
  - `feature/tasks/src/main/java/com/example/xcpro/tasks/data/persistence/TaskPersistenceAdapters.kt`
  - `TaskPersistSerializer.kt`
  - `feature/tasks/src/main/java/com/example/xcpro/tasks/aat/AATTaskCoreMappers.kt`
  - `feature/tasks/src/test/java/com/example/xcpro/tasks/data/persistence/AATCanonicalTaskStorageTest.kt`
  - coordinator / hydrate / serializer fidelity tests
- Ownership/file split changes in this phase:
  - canonical task payload is now the only AAT persistence payload for service-backed autosave and named save/load.
  - legacy AAT CUP/prefs storage remains read fallback only behind `AATCanonicalTaskStorage`; task managers must not own persistence paths.
- Tests to add/update:
  - `AATCanonicalTaskStorageTest`
  - `TaskPersistSerializerFidelityTest`
  - `TaskManagerCoordinatorTest`
  - `TaskManagerCanonicalHydrateTest`
- Exit criteria:
  - `targetParam`, `targetLocked`, and target coordinates survive named-task save/load, autosave, and startup restore,
  - import writes one owner path only.

### Phase 4 - Persistence ownership and lifetime cleanup

- Status:
  - Completed 2026-03-15.
- Goal:
  - Remove manager-owned file I/O/persistence side effects and make listener lifetime explicit.
- Files to change:
  - `AATTaskManager.kt`
  - `RacingTaskManager.kt`
  - `TaskCoordinatorPersistenceBridge.kt`
  - task persistence adapters / DI wiring
  - `TaskNavigationController.kt`
  - task DI files in `feature:tasks` and/or `app`
- Ownership/file split changes in this phase:
  - task managers become mutation/orchestration owners only.
  - persistence lives only behind the persistence service/adapters.
  - listener lifetime becomes caller-owned or explicitly removable.
- Tests to add/update:
  - named/autosave roundtrip tests
  - persistence failure-mode tests
  - `TaskNavigationControllerTest` plus lifetime tests
- Exit criteria:
  - no production manager constructs file I/O or writes prefs directly,
  - coordinator listeners no longer leak across ViewModel/runtime lifetimes.

### Phase 5 - AAT MapLibre runtime boundary cleanup

- Status:
  - Completed 2026-03-15.
- Goal:
  - Finish the ownership split by consolidating live AAT MapLibre gesture/edit/runtime code under map/map-runtime ownership and removing duplicate task-side runtime helpers.
- Files to change:
  - `TaskManagerCoordinator.kt`
  - `MapScreenViewModel.kt`
  - `feature/map-runtime/src/main/java/com/example/xcpro/gestures/TaskGestureHandler.kt`
  - `feature/map-runtime/src/main/java/com/example/xcpro/gestures/TaskGestureHandlerFactory.kt`
  - `feature/map-runtime/src/main/java/com/example/xcpro/tasks/aat/gestures/AatGestureHandler.kt`
  - `feature/map-runtime/src/main/java/com/example/xcpro/tasks/aat/map/AATMapCoordinateConverter.kt`
  - `AATEditModeManager.kt`
  - `MapTasksUseCase.kt`
  - `feature/tasks/build.gradle.kts`
  - runtime/map tests
- Ownership/file split changes in this phase:
  - `TaskManagerCoordinator` stops constructing MapLibre gesture handlers.
  - Map shell/runtime modules own gesture creation, `MapLibreMap` context, tap/hit-test conversion, drag preview, and render-sync wiring.
  - `feature:tasks` keeps task-core state, editor UI, and map-agnostic edit-state only.
  - `AATEditModeManager` is split so only task edit-state remains in `feature:tasks`; dead or map-typed overlay helpers are removed or re-homed.
  - duplicate AAT interaction paths are collapsed to one production map/runtime owner path before the `feature:tasks` MapLibre dependency is removed.
- Tests to add/update:
  - equivalent hit-test and drag-commit coverage for the map-owned AAT gesture path
  - `TaskGestureHandlerFactoryTest`
  - targeted map task integration tests
  - detach/style-reload teardown coverage for the retained AAT edit runtime path
- Exit criteria:
  - no production `MapLibreMap`-typed AAT gesture/runtime owner remains in `feature:tasks`,
  - `TaskManagerCoordinator` no longer constructs task gesture handlers,
  - duplicate AAT map interaction ownership is removed,
  - `feature/tasks/build.gradle.kts` no longer carries the MapLibre dependency,
  - AAT edit UX parity holds.

### Phase 6 - Hardening, docs, and release gate

- Status:
  - Completed 2026-03-15.
- Goal:
  - Lock the new boundaries with automation, doc sync, and full release-grade verification.
- Files to change:
  - `scripts/ci/enforce_rules.ps1`
  - `docs/ARCHITECTURE/PIPELINE.md` if wiring changed materially
  - ADR file for the durable ownership decision
  - plan/deviation status docs
- Ownership/file split changes in this phase:
  - none beyond enforcement and documentation
- Tests to add/update:
  - scoped guards for repository authority misuse and retained MapLibre ownership
- Exit criteria:
  - required commands pass,
  - docs reflect the new owner path,
  - deviation `RULES-20260315-18` can be removed.

## 5) Test Plan

- Unit tests:
  - `TaskManagerCoordinatorTest`
  - `TaskRepositoryProjectionComplianceTest`
  - `GlideTargetRepositoryTest`
  - `IgcTaskDeclarationSourceTest`
  - `TaskNavigationControllerTest`
  - `TaskPersistSerializerFidelityTest`
  - new coordinator snapshot and persistence-bridge roundtrip tests
- Replay/regression tests:
  - `RacingReplayValidationTest`
- UI/instrumentation tests (if needed):
  - targeted map/task or AAT edit instrumentation if runtime move changes visible edit flow
- Degraded/failure-mode tests:
  - persistence save/load failure handling
  - restore with empty or invalid task payload
  - map detach during AAT edit/runtime teardown
- Boundary tests for removed bypasses:
  - restore without opening task sheet
  - glide and IGC consumers update from coordinator snapshot only

| Change Type | Required Proof | Planned Evidence |
|---|---|---|
| Business rule / policy | Unit tests + regression cases | target/serializer/import parity tests |
| Time-base / replay / cadence | deterministic repeat-run tests | replay/navigation parity where ownership changes touch outputs |
| Persistence / settings / restore | Round-trip / restore / migration tests | named/autosave roundtrip, startup restore, failure-mode tests |
| Ownership move / bypass removal / API boundary | Boundary lock tests | glide/IGC restore tests, projection compliance, listener lifetime tests, scoped guards |
| UI interaction / lifecycle | UI or instrumentation coverage | targeted AAT edit/runtime lifecycle tests and manual smoke |

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Optional when relevant:

```bash
./gradlew connectedDebugAndroidTest --no-parallel
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Snapshot contract is added but legacy accessors remain the de facto authority | High | migrate glide/IGC first, then projector/UI, then add scoped guard | XCPro Team |
| Demoting `TaskRepository` breaks task UI assumptions | High | keep repository as a derived projector first; do not remove task UI shaping in the same phase | XCPro Team |
| AAT target consolidation changes edit behavior | High | lock serializer/import/drag/update behavior with tests before removing sync helpers | XCPro Team |
| Persistence cleanup regresses named-task or autosave flows | High | isolate manager persistence removal behind roundtrip and failure-mode tests | XCPro Team |
| Moving AAT MapLibre runtime leaves duplicate interaction owners or a hidden map-typed contract in `feature:tasks` | High | move gesture creation and converter ownership with the map shell/runtime path, split `TaskGestureHandler` if needed, and delete dead task-side MapLibre helpers before dropping the dependency | XCPro Team |

## 6A) ADR / Durable Decision Record

- ADR required: Yes
- ADR file:
  - `docs/ARCHITECTURE/ADR_TASK_RUNTIME_AUTHORITY_2026-03-15.md`
- Decision summary:
  - `TaskManagerCoordinator` owns the canonical task runtime snapshot.
  - task UI projection is derived, not authoritative.
  - persistence is owned only by `TaskEnginePersistenceService` and adapters.
  - MapLibre AAT edit/runtime ownership belongs in `feature:map` / `feature:map-runtime`.
- Why this belongs in an ADR instead of plan notes:
  - it changes long-lived cross-module ownership and the durable split between task-core and map runtime modules.

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`
- No duplicate SSOT ownership introduced
- Replay behavior remains deterministic
- Error/degraded-state behavior is explicit and tested where behavior changed
- Ownership/boundary/public API decisions are captured in an ADR
- Restored task state is visible to glide and IGC without task-sheet initialization
- `targetParam`, `targetLocked`, and target coordinates survive save/load/autosave/startup restore
- No production task manager constructs persistence/file I/O directly
- No active AAT MapLibre edit/runtime owner remains in `feature:tasks` unless a new approved deviation explicitly scopes it
- Leg-change observation has explicit owner-bound lifetime in scoped files

## 8) Rollback Plan

- What can be reverted independently:
  - Phase 1 consumer rewiring, Phase 2 projector cleanup, Phase 3 target-state consolidation, Phase 4 persistence cleanup, and Phase 5 runtime moves can each be reverted independently.
- Recovery steps if regression is detected:
  1. Revert only the most recent phase that changed behavior.
  2. Keep passing tests/guards from earlier phases where they still apply.
  3. Restore the previous owner contract for the reverted seam only.
  4. Re-run required gates before retrying with a narrower slice.
