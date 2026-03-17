# Task Render and Camera Ownership Release-Grade Phased IP

## 0) Metadata

- Title: Recover task render invalidation and camera ownership without removing required task-fit UX
- Owner: XCPro Team
- Date: 2026-03-17
- Issue/PR: TBD
- Status: Draft
- Execution rules:
  - This is an ownership-correction track, not a cleanup track.
  - Land the `activeLeg` invalidation fix before moving camera-fit ownership.
  - Do not change manual leg-selection semantics in this track.
  - Keep `feature:tasks` task-core-owned and `feature:map` / `feature:map-runtime` MapLibre/camera-owned.
  - Preserve approved task-fit UX only through explicit triggers; do not keep implicit fit on generic redraw.
  - Do not mix selected-leg highlight work into this track unless Phase 0 proves it is required for parity.

## 1) Scope

- Problem statement:
  - Manual leg selection currently changes `activeLeg`, and `feature/map/src/main/java/com/example/xcpro/tasks/TaskMapOverlay.kt` includes that value in the task render signature even though the render snapshot does not use it.
  - `feature/map-runtime/src/main/java/com/example/xcpro/map/TaskRenderSyncCoordinator.kt` treats that signature as a full task sync trigger.
  - Generic Racing redraw still owns camera fit in `feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingTaskDisplay.kt`, so a non-render state change can redraw overlays and move the map.
  - Style refresh and overlay refresh can also replay that path, so the bug is broader than just next/prev turnpoint taps.
- Why now:
  - The current seam violates the existing camera ownership contract in `docs/ARCHITECTURE/ARCHITECTURE.md` and the runtime intent in `docs/ARCHITECTURE/PIPELINE.md`.
  - It causes user-visible camera jumps during task interactions and leaves no safe place to add selected-leg rendering later.
- In scope:
  - Remove `activeLeg` as a full task-render invalidator unless it becomes a real render input.
  - Move Racing task-fit behavior out of generic redraw and into an explicit map-runtime seam.
  - Preserve approved one-shot task-fit behavior for task actions that genuinely need it.
  - Add regression tests and map-runtime validation for the corrected behavior.
- Out of scope:
  - Changing `TaskNavigationController` manual leg semantics (`PENDING_START` vs `IN_PROGRESS`).
  - New selected-leg highlight visuals.
  - Task Route screen behavior.
  - AAT renderer redesign unrelated to render/camera ownership.
- User-visible impact:
  - Intended:
    - manual next/prev turnpoint no longer snaps or reorients the map
    - style reload / overlay refresh no longer refit the Racing task unexpectedly
  - Preserved:
    - approved one-shot fit when the user creates/imports/loads a task, if Phase 0 confirms that UX is required
- Rule class touched: Invariant

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Active task leg (`activeLeg`) | `TaskManagerCoordinator` | `TaskRuntimeSnapshot.activeLeg`, task UI projection, navigation/glide consumers | render signature fields that imply redraw when renderers do not consume selected-leg state |
| Task render snapshot | `MapTasksUseCase` | `TaskRenderSnapshot` | renderer-local task mirrors used as hidden authority |
| Task render invalidation signature | task map/runtime seam only, derived from real render inputs | `TaskStateSignature` or replacement derived key | including non-render state such as `activeLeg` unless the renderer explicitly consumes it |
| Camera pose and fit execution | map camera/runtime owner | map runtime command or explicit camera-fit seam | task renderer direct `animateCamera(...)` calls |
| One-shot task-fit request | explicit map-runtime command/effect owner | ephemeral command/effect only | implicit fit as a side effect of generic redraw |

### 2.1A State Contract

| State Item | Authoritative Owner | Allowed Mutators | Read Path | Derived From | Persistence Owner | Reset/Clear Conditions | Time Base | Required Tests |
|---|---|---|---|---|---|---|---|---|
| `activeLeg` | `TaskManagerCoordinator` | named leg mutations only | task snapshot, task UI, glide, navigation | current task manager state | task engine persistence path | clear, switch, restore/load, explicit leg change | N/A | coordinator, navigation, glide tests |
| `TaskRenderSnapshot` | `MapTasksUseCase` | none direct; derived only | `TaskRenderSyncCoordinator` and `TaskMapRenderRouter` | coordinator snapshot + edit-mode state | none; derived only | follows task snapshot changes | N/A | render snapshot + render sync tests |
| task render invalidation key | task map/runtime seam | none direct; derived only | `TaskRenderSyncCoordinator` | true render inputs only | none | follows render-input changes | N/A | active-leg-only no-sync test |
| one-shot task-fit request | explicit map-runtime command/effect owner selected in Phase 0 | approved task actions only | map runtime controller / camera-fit seam | user-approved task action | none | consume-once | event-driven only; telemetry uses monotonic | trigger-matrix tests |
| camera pose / camera fit execution | map camera/runtime owner | explicit camera command/path only | map runtime and UI effects | current pose + approved fit request | map state owner if persisted | map detach, user pan/zoom overrides, explicit resets | existing camera contract | map runtime tests |

### 2.2 Dependency Direction

Confirm dependency flow remains:

`UI -> domain/use-cases -> data`

- Modules/files touched:
  - `feature/map`
  - `feature/map-runtime`
  - `feature/tasks` only where it feeds render invalidation inputs
- Boundary risks:
  - accidentally moving camera policy back into task renderers
  - adding a new fit command that becomes a hidden state owner instead of a one-shot effect
  - keeping `activeLeg` in invalidation because of unverified parity assumptions

### 2.2A Reference Pattern Check

| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| `feature/map/src/main/java/com/example/xcpro/map/MapCommand.kt` | existing UI-only imperative map command seam | one-shot imperative map command instead of persistent Compose state | may need a task-fit command in addition to `SetStyle` |
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapRuntimeController.kt` | existing runtime owner for imperative map commands | queue/apply command only when map is ready | may need task-fit application instead of style-only command handling |
| `feature/map-runtime/src/main/java/com/example/xcpro/map/TaskRenderSyncCoordinator.kt` | single render sync owner already exists | one downstream owner for task redraw decisions | coordinator must stop implying camera fit |
| `docs/refactor/Task_AAT_Ownership_Release_Grade_Phased_IP_2026-03-15.md` | similar task/map ownership correction | keep `feature:tasks` task-core-owned and map behavior in map modules | this track also touches camera ownership explicitly |

### 2.2B Boundary Moves

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Full task redraw invalidation for manual leg changes | `TaskMapOverlay` signature including `activeLeg` | render signature derived from true render inputs only | leg selection is navigation state, not a redraw trigger today | render sync regression tests |
| Racing camera fit on redraw | `RacingTaskDisplay` | explicit map camera/runtime seam | renderer should draw only; camera should move only on explicit fit | renderer/runtime tests |
| Task-fit trigger policy | implicit "any Racing redraw may fit" | explicit trigger matrix owned by map runtime plan | preserve required fit UX without hidden side effects | trigger-matrix tests + smoke |
| Style refresh / overlay refresh camera behavior | full redraw path with implicit fit | redraw-only path unless an explicit fit request is active | style reload should rebuild visuals, not move the user | lifecycle/runtime tests |

### 2.2C Bypass Removal Plan

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `feature/map/src/main/java/com/example/xcpro/tasks/TaskMapOverlay.kt` | `activeLeg` included in full render signature | signature includes only true render inputs | Phase 1 |
| `feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingTaskDisplay.kt` | generic Racing redraw directly calls `animateCamera(...)` | redraw only; approved fit goes through explicit map-runtime path | Phase 2-3 |
| `feature/map/src/main/java/com/example/xcpro/map/MapOverlayRuntimeMapLifecycleDelegate.kt` | style/overlay refresh can replay implicit fit via redraw | redraw-only lifecycle sync | Phase 3 |
| manual leg selection path (`BottomSheetState` -> VM -> coordinator) | leg change indirectly causes full redraw and fit | leg change updates task/nav/glide only | Phase 1 |

### 2.2D File Ownership Plan

| File | New / Existing | Owner / Responsibility | Why Here | Why Not Another Layer/File | Split Needed? |
|---|---|---|---|---|---|
| `docs/refactor/Task_Render_Camera_Ownership_Release_Grade_Phased_IP_2026-03-17.md` | New | execution contract for this track | active phased plan belongs in `docs/refactor` | not a durable ADR by itself | No |
| `feature/map/src/main/java/com/example/xcpro/tasks/TaskMapOverlay.kt` | Existing | UI-derived task render signature inputs only | this is where task UI state currently feeds render sync | not a renderer, not a task authority | No |
| `feature/map-runtime/src/main/java/com/example/xcpro/map/TaskRenderSyncCoordinator.kt` | Existing | render invalidation and sync dispatch only | single render sync owner already exists here | not a camera owner | No |
| `feature/map-runtime/src/main/java/com/example/xcpro/map/MapTasksUseCase.kt` | Existing | render snapshot owner for map runtime | this is the canonical map-runtime task snapshot seam | not UI or renderer-specific | No |
| `feature/map/src/main/java/com/example/xcpro/tasks/TaskMapRenderRouter.kt` | Existing | task overlay routing only | router should decide renderer, not camera side effects | not a camera owner | No |
| `feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingTaskDisplay.kt` | Existing | Racing overlay drawing only after this track | current renderer already owns Racing drawing | camera side effects do not belong here | No |
| `feature/map/src/main/java/com/example/xcpro/map/MapCommand.kt` | Existing | UI-only imperative map commands | existing explicit map command seam | better than ad hoc callback state | No |
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapRuntimeController.kt` | Existing | applies one-shot imperative map commands when map is ready | current runtime command owner | preferable to renderer-owned camera calls | No |
| `feature/map-runtime/src/main/java/com/example/xcpro/map/TaskViewportPlanner.kt` | New, if needed | pure task-to-bounds/task-fit planning helper | keeps fit math out of renderer and out of UI state | not in `feature:tasks`; bounds planning is map-runtime concern | Maybe |
| `feature/map/src/test/java/com/example/xcpro/map/TaskRenderSyncCoordinatorTest.kt` | Existing | render invalidation contract tests | current render sync contract is tested here | not an integration-only concern | No |
| `feature/map/src/test/java/com/example/xcpro/map/ui/MapRuntimeControllerTest.kt` | Existing or New | imperative fit command behavior | command application belongs with runtime command owner | not a renderer test | Maybe |
| `feature/map/src/test/java/com/example/xcpro/tasks/racing/RacingTaskDisplayTest.kt` | New, if needed | renderer draw-only contract tests | lock "no direct camera fit on redraw" | not a coordinator test | Maybe |

### 2.2E Module and API Surface

| Contract / API | Owner | Consumers | Visibility | Why Needed | Compatibility / Removal Plan |
|---|---|---|---|---|---|
| `TaskStateSignature` reduced to real render inputs | `feature:map-runtime` | task map UI + render sync owner | internal/public as currently required | stop treating `activeLeg` as a redraw cause | no compatibility shim needed |
| explicit task-fit command/effect (preferred: `MapCommand.FitCurrentTask` or equivalent) | `feature:map` map runtime seam | map VM/runtime owner and command applier | narrowest possible | preserve approved fit UX without renderer side effects | finalize exact shape in Phase 0 |
| optional `TaskViewportPlanner` helper | `feature:map-runtime` | runtime command/camera-fit path only | internal | isolate bounds/fit planning from renderer code | no shim if introduced focused and single-purpose |

### 2.2F Phase 0 Trigger Matrix and Preferred Seam

#### Phase 0 Findings

- Live task building on the map currently adds geometry through repeated `taskViewModel.onAddWaypoint(...)` calls, including the Racing and AAT manage flows. This means waypoint-add is part of the active build UX, not just a persistence/import path.
- The wired `PersistentWaypointSearchBar` does not expose a separate map `goto` action before adding a waypoint. If fit is removed from every add path, a newly added off-screen waypoint can become invisible with no compensating affordance.
- `TaskManagerCoordinator.loadTask(...)` exists as a valid named-task load seam even though the current live `MapScreen` pass did not show an obvious direct caller. The plan should still define its fit behavior now because it is an explicit user intent seam.
- Startup restore from `MapScreenViewModel.loadSavedTasksOnInit(...)` is automatic recovery, not an explicit "show me this task now" action. It should not move the live map by default.
- `TaskManagerCoordinator.switchToTaskType(...)` preserves task geometry and changes task rules/type semantics. That is not itself a viewport request.

#### Explicit Fit Trigger Matrix

| Trigger | Explicit Fit? | Reason |
|---|---|---|
| waypoint add while building a task | Yes | preserve current task-building visibility because the wired add flow has no separate `goto` affordance |
| persisted-task import | Yes | explicit "show this imported task" user action |
| named task load | Yes | explicit "show this saved task" user action when surfaced |
| task-type switch | No | semantic/rules change only; geometry already exists |
| startup restore | No | automatic recovery should not snap the live map |
| style refresh | No | redraw-only lifecycle event |
| overlay refresh / saved-task replot | No | redraw-only lifecycle event |
| manual next/prev TP or `setActiveLeg(...)` | No | target/navigation change only; should not move camera |

#### Preferred Explicit Fit Seam

- `feature:tasks` should not emit `MapCommand` directly.
- Preferred request path:
  - `TaskSheetViewModel` emits a task-local UI effect such as `RequestFitCurrentTask` only for approved explicit task actions.
  - A map-owned host in `feature:map` collects that effect and translates it into `MapCommand.FitCurrentTask`.
  - `MapRuntimeController` applies the command when the map is ready.
- Why this seam:
  - it preserves module direction and keeps map-command ownership in `feature:map`
  - it reuses the existing imperative map-command path instead of introducing a new camera owner
  - it keeps startup restore and redraw lifecycle paths redraw-only because they never emit the explicit fit effect

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| task-fit request and redraw behavior | event-driven only | no time policy should control correctness |
| any task-fit latency / redraw telemetry | Monotonic | performance/SLO evidence only |
| replay task redraw behavior | existing replay contract unchanged | this track must not introduce replay-only timing differences |
| wall time | none in behavior logic | no wall-time dependency is needed for this track |

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - `Main`: map/runtime command application and MapLibre camera updates
  - `Default` / `IO`: none newly required for correctness
- Primary cadence/gating sensor:
  - event-driven task actions only; no polling or timer loops
- Hot-path latency budget:
  - manual leg selection should not trigger full redraw or camera fit
  - approved task-fit should remain a one-shot runtime action only

### 2.5 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence rules:
  - redraw/focus policy must remain deterministic for the same task action sequence
  - replay must not gain wall-time-driven fit behavior

### 2.6 Enforcement Coverage

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| `activeLeg` still triggers full redraw | `ARCHITECTURE.md` authoritative state contract | unit test + review | `TaskRenderSyncCoordinatorTest` |
| renderer still owns camera movement | `ARCHITECTURE.md` responsibility ownership matrix | targeted test + review | runtime controller / renderer tests |
| style reload or overlay refresh still refits task | map runtime ownership rules | integration/runtime test | lifecycle/runtime tests |
| required fit UX is lost on load/import/create | plan contract + UX SLO gate | trigger-matrix tests + smoke | task action tests and manual smoke |
| manual leg semantics drift while fixing render ownership | existing navigation contract | unit tests | `TaskNavigationControllerTest`, glide tests |

### 2.7 Visual UX SLO Contract

| Outcome | SLO ID | Baseline | Target | Validation/Test | Phase Gate |
|---|---|---|---|---|---|
| Manual task interactions no longer snap or reorient the map unexpectedly | `MS-UX-01` | current bug can snap camera during task interaction | no camera jump from manual next/prev TP in mixed-load map use | runtime smoke + impacted SLO evidence | Phase 3 |
| Redraw lifecycle paths no longer cause extra map stabilization churn | `MS-UX-06` | style reload / overlay refresh can replay Racing fit | redraw bursts stay within current startup/stability contract | lifecycle/runtime tests + evidence if impacted | Phase 3 |
| Generic task redraw is a redraw only, not a hidden camera operation | `MS-ENG-01` | redraw currently includes fit in Racing path | overlay redraw cost and side effects stay bounded | targeted runtime metrics/tests | Phase 2-3 |

## 3) Data Flow (Before -> After)

Before:

```text
Task UI next/prev TP
  -> TaskSheetViewModel.onSetActiveLeg(...)
  -> TaskManagerCoordinator.setActiveLeg(...)
  -> TaskMapOverlay builds signature including activeLeg
  -> TaskRenderSyncCoordinator full sync
  -> TaskMapRenderRouter syncTaskVisuals(...)
  -> RacingTaskDisplay.plotRacingOnMap(...)
  -> RacingTaskDisplay.centerMapOnRacingTask(...)
  -> map.animateCamera(...)
```

After:

```text
Manual next/prev TP
  -> TaskSheetViewModel.onSetActiveLeg(...)
  -> TaskManagerCoordinator.setActiveLeg(...)
  -> task/nav/glide consumers update
  -> no full task redraw unless render inputs changed

Task geometry/style change
  -> TaskRenderSyncCoordinator redraw
  -> TaskMapRenderRouter redraw only
  -> renderer draws only

Approved task-fit action
  -> explicit task-fit command/effect
  -> map runtime command/camera-fit seam
  -> camera owner fits task once
```

## 4) Implementation Phases

### Phase 0 - Narrow seam/code re-pass and trigger matrix lock

- Goal:
  - Confirm the exact task-fit trigger matrix before code changes.
  - Re-pass only the seams that determine explicit fit policy:
    - create task
    - import persisted task
    - named task load
    - task-type switch
    - startup restore
    - style refresh / overlay refresh
    - manual leg next/prev
- Files to change:
  - plan/doc updates only unless the seam pass finds a blocking mismatch
- Tests to add/update:
  - none yet; baseline matrix first
- Exit criteria:
  - one written yes/no matrix for every candidate trigger
  - preferred explicit fit seam selected
  - no unresolved ownership ambiguity around the new fit request path
- Completion note:
  - Completed 2026-03-17 by tracing task creation, import, named load, task-type switch, startup restore, manual leg, and lifecycle redraw seams.
  - Locked trigger matrix:
    - Yes: waypoint add while building a task, persisted-task import, named-task load when surfaced
    - No: task-type switch, startup restore, style refresh, overlay refresh, manual next/prev TP
  - Preferred seam:
    - task-local fit request effect in `TaskSheetViewModel`
    - map-owned translation to `MapCommand.FitCurrentTask`
    - application in `MapRuntimeController`

### Phase 1 - Active-leg invalidation correction

- Goal:
  - Remove `activeLeg` from full redraw invalidation unless selected-leg state becomes a real render input in the same phase.
- Files to change:
  - `feature/map/src/main/java/com/example/xcpro/tasks/TaskMapOverlay.kt`
  - `feature/map-runtime/src/main/java/com/example/xcpro/map/TaskRenderSyncCoordinator.kt`
  - `feature/map/src/test/java/com/example/xcpro/map/TaskRenderSyncCoordinatorTest.kt`
  - optionally `feature/map-runtime/src/main/java/com/example/xcpro/map/MapTasksUseCase.kt` if snapshot/signature alignment needs cleanup
- Tests to add/update:
  - active-leg-only change does not trigger full sync
  - repeated identical task state still dedupes
  - manual leg semantics remain unchanged in existing task/glide/navigation tests
- Exit criteria:
  - manual leg change no longer forces full task redraw
  - no render payload consumer is left depending on removed `activeLeg` invalidation

### Phase 2 - Explicit task-fit command/path

- Goal:
  - Introduce one explicit, one-shot task-fit request seam owned by map runtime, using the Phase 0 trigger matrix.
- Files to change:
  - preferred path:
    - `feature/map/src/main/java/com/example/xcpro/map/MapCommand.kt`
    - `feature/map/src/main/java/com/example/xcpro/map/ui/MapRuntimeController.kt`
  - optional focused helper:
    - `feature/map-runtime/src/main/java/com/example/xcpro/map/TaskViewportPlanner.kt`
  - whichever task-action owner must emit the explicit fit request for approved triggers
- Tests to add/update:
  - approved trigger actions emit fit exactly once
  - non-approved triggers do not emit fit
  - map-not-ready path safely queues or drops the request according to the selected command contract
- Exit criteria:
  - task-fit exists only as an explicit runtime request
  - no persistent map/task state is introduced to support fit

### Phase 3 - Renderer camera removal and lifecycle hardening

- Goal:
  - Remove renderer-owned camera fit and ensure redraw lifecycle paths do not move the camera unless an explicit fit request is active.
- Files to change:
  - `feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingTaskDisplay.kt`
  - `feature/map/src/main/java/com/example/xcpro/tasks/TaskMapRenderRouter.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/MapOverlayRuntimeMapLifecycleDelegate.kt`
  - `feature/map-runtime/src/main/java/com/example/xcpro/map/MapOverlayManagerRuntimeBaseOpsDelegate.kt`
  - targeted runtime/controller tests
- Tests to add/update:
  - generic Racing redraw does not animate or move camera
  - style refresh / overlay refresh redraws task visuals without task fit
  - approved task-fit path still works
- Exit criteria:
  - no direct task-renderer `animateCamera(...)` ownership remains in the generic redraw path
  - redraw-only paths are behaviorally separated from fit paths

### Phase 4 - Docs, verification, and rollback proof

- Goal:
  - Sync docs, run required checks, and attach map-runtime evidence for impacted SLOs.
- Files to change:
  - `docs/ARCHITECTURE/PIPELINE.md` if the final fit-command/runtime path changes documented map wiring
  - this plan doc with completion notes
- Tests to add/update:
  - none beyond Phase 1-3 coverage unless a doc-driven contract gap is found
- Exit criteria:
  - required verification passes
  - impacted SLO evidence is attached or a deviation is explicitly recorded
  - rollback path is documented commit-by-commit

## 5) Test Plan

- Unit tests:
  - `TaskRenderSyncCoordinatorTest` active-leg-only regression coverage
  - runtime controller / fit command tests
  - renderer draw-only contract tests if direct camera calls are removed
- Replay/regression tests:
  - confirm no replay-specific redraw or fit drift if replay exercises task overlays
- UI/instrumentation tests (if needed):
  - map task interaction smoke for manual next/prev TP
  - approved load/import/create fit smoke
- Degraded/failure-mode tests:
  - map not ready when explicit fit command arrives
  - style reload while task exists does not refit implicitly
- Boundary tests for removed bypasses:
  - renderer no longer owns camera movement
  - render invalidation no longer keys on non-render state

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

When relevant:

```bash
./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
```

Map/runtime evidence:

- attach impacted `MS-UX-01`, `MS-UX-06`, and `MS-ENG-01` evidence if the final runtime path changes those scenarios materially

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| removing renderer fit also removes required task-load convenience | user-visible regression on create/import/load | Phase 0 trigger matrix; explicit fit only for approved actions | XCPro Team |
| active-leg invalidation fix hides a renderer dependency we did not find | stale visual parity or silent selection dependency | Phase 1 regression tests before deeper camera work | XCPro Team |
| new fit command becomes a hidden state owner or persistent queue | ownership drift | keep it one-shot only and applied by existing runtime command seam | XCPro Team |
| style refresh still moves the camera through another path | bug survives | explicit lifecycle tests in Phase 3 | XCPro Team |

## 7) Docs and Governance

- `PIPELINE.md` update:
  - Yes, if the final implementation introduces an explicit task-fit command/runtime path or changes the documented camera-fit owner.
- ADR required:
  - No by default.
  - Re-evaluate only if Phase 0 selects a new durable public cross-module camera/task-fit contract beyond the existing map command seam.
- `KNOWN_DEVIATIONS.md` entry:
  - Not expected.
  - Required only if impacted mandatory MapScreen SLOs miss and the change still needs to land.

## 8) Recommended Next Step

1. Do the narrow seam/code re-pass from Phase 0 and write the explicit yes/no task-fit trigger matrix.
2. Implement Phase 1 only after that matrix is locked.
3. Do not start renderer camera removal until the explicit fit path is selected and tested.
