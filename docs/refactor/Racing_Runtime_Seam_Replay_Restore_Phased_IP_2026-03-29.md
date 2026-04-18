# Racing Runtime Seam Replay Restore Phased IP

## 0) Metadata

- Title: Repair racing runtime seam contract and replay restore without collapsing declared authorities
- Owner: XCPro Team
- Date: 2026-03-29
- Issue/PR: TBD
- Status: Completed
- Execution rules:
  - Keep the declared authority split from the existing ADRs.
  - `TaskManagerCoordinator.taskSnapshotFlow` remains the authoritative cross-feature task-runtime seam.
  - `TaskNavigationController.racingState` remains the authoritative racing-navigation runtime seam.
  - Do not hide the current bug by widening `TaskRepository` or moving navigation state into UI projection code.
  - Fix replay restore before widening consumer/UI changes.
  - Treat auto-advance-disabled divergence as an explicit contract decision, not an accidental side effect.
- Recommendation:
  - Preferred track: Option 2 from the seam review.
  - No new ADR is required if this plan preserves the current ADR ownership model.
  - Phase 1 should land replay lifecycle fencing and full restore correctness first.
  - 2026-03-29 seam-pass update:
    - Phase 1 must also cover replay failure cleanup, live-fix race windows, replay mode/`autoStopAfterFinish` restore, and restore-order safety around coordinator leg listeners.

## 1) Scope

- Problem statement:
  - Racing replay currently resets racing task progress to start state before replay, but restores only replay speed/cadence and a partial advance snapshot.
  - The replay path does not restore coordinator `activeLeg`, full `RacingNavigationState`, the captured advance phase (`START_*` vs `TURN_*`), replay mode, or `autoStopAfterFinish`.
  - Racing replay failure cleanup is weaker than demo replay cleanup, so a failed racing replay can leave replay-session state insufficiently reset.
  - The current replay start/terminal ordering can reopen live sensor flow before replay restore completes, which means live fixes can race the reset/restore path.
  - A naive restore through `TaskManagerCoordinator.setActiveLeg(...)` can trip the controller's manual-leg listener and overwrite/disarm restored navigation state unless restore is atomic.
  - A valid start can advance `racingState.currentLegIndex` while `TaskManagerCoordinator.taskSnapshotFlow.activeLeg` stays unchanged when auto-advance is disabled or start advance is not armed.
  - Some task UI surfaces currently render "current waypoint" from `activeLeg` only, even though the racing navigation seam may already be on the next leg.
  - `TaskSheetUseCase` and `TaskSheetViewModel` currently use `TaskUiState.stats.activeIndex` as the selected-leg/editor index for proximity and distance helpers, so Phase 2 must not silently redefine that field as the universal in-flight nav leg.
  - Racing replay also forces map shell state (`trackingLocation`, return-button visibility, initial-centering), but unlike demo replay it does not currently restore that shell state.
- Why now:
  - The replay gap is correctness-sensitive and can overwrite in-memory user progress.
  - The seam split is already documented by ADR, so the current behavior is now an architecture-contract problem, not just a UI quirk.
  - Future consumers are likely to regress by choosing the wrong seam unless the contract is made explicit in code and tests.
- In scope:
  - Racing replay snapshot contract, replay-start fencing, terminal restore behavior, and failure cleanup.
  - Explicit restore API for racing navigation runtime state and advance phase.
  - Replay controller setting restore for mode, cadence, speed, and `autoStopAfterFinish`.
  - Restore-order safety so replay restore does not get reinterpreted as a manual leg change.
  - Racing replay map shell state restoration or an explicit documented decision to defer it.
  - Consumer policy for "selected task leg" vs "navigation leg in flight".
  - Targeted task/map tests that lock the repaired seam behavior.
  - Documentation sync for task-runtime/replay ownership where needed.
- Out of scope:
  - Rewriting the racing navigation engine.
  - Changing start/turn/finish rules or replay cadence algorithms.
  - Collapsing `taskSnapshotFlow` and `racingState` into one owner.
  - AAT runtime changes.
- User-visible impact:
  - Racing replay returns to the pre-replay task progress, advance phase, and replay session settings after completion/cancel/failure.
  - Racing replay failure paths no longer leave the app in a half-replay state.
  - Racing replay should not visibly jump through a live-fix race window before restore completes.
  - Racing task surfaces stop implying the wrong "current turnpoint" when task selection state and in-flight navigation state differ.
- Rule class touched: Invariant

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Active task definition + selected task leg | `TaskManagerCoordinator` | `StateFlow<TaskRuntimeSnapshot>` | `TaskRepository.state` or `racingState` acting as task-definition authority |
| Racing navigation runtime (`status`, `currentLegIndex`, accepted start, finish outcome, last fix, event stream) | `TaskNavigationController` | `racingState` + `racingEvents` | `TaskManagerCoordinator` silently mirroring nav-only runtime fields without explicit contract |
| Racing replay restore bundle | `RacingReplaySnapshotController` in `feature:map` | internal capture/restore data only | ad hoc partial restore fields spread across coordinator/controller/replay controller |
| Racing replay session cleanup and terminal handoff ordering | replay orchestration path in `feature:map` + replay controller runtime | replay lifecycle methods/events only | event-only restore that leaves replay/live handoff ordering implicit |
| Racing replay map shell override state | replay snapshot helper in `feature:map` | internal snapshot only | racing replay mutating map shell state without a paired restore path |
| Task UI projection | `TaskRepository` / task-sheet projector | `StateFlow<TaskUiState>` | any non-UI consumer treating it as runtime authority |

### 2.1A State Contract

| State Item | Authoritative Owner | Allowed Mutators | Read Path | Derived From | Persistence Owner | Reset/Clear Conditions | Time Base | Required Tests |
|---|---|---|---|---|---|---|---|---|
| `TaskRuntimeSnapshot.taskType/task/activeLeg` | `TaskManagerCoordinator` | named coordinator mutations, task load/restore, explicit replay restore path | task UI, map task shell, LiveFollow, IGC declaration, route/glide task payload | racing/AAT managers | existing task persistence path only | clear task, task-type switch, replay terminal restore, named-task load/restore | N/A | coordinator snapshot tests, replay restore tests |
| `RacingNavigationState` | `TaskNavigationController` | fix ingestion, manual leg sync, explicit replay restore path | route, glide/task-performance inputs, racing event logger, racing-aware UI projection | navigation engine + advance state + task/fix inputs | none | task clear, explicit reset, replay pre-start reset, replay terminal restore | replay/live event time already owned by nav path | controller tests, replay restore tests, route/glide regressions |
| `RacingAdvanceState.Snapshot` including `armState` | `TaskNavigationController` | mode/arm changes, explicit replay restore path | replay snapshot controller, task UI command projection | advance-state owner only | none | explicit reset, replay pre-start reset, replay terminal restore | N/A | controller snapshot/restore tests |
| Racing replay snapshot bundle | `RacingReplaySnapshotController` | capture on replay start, restore on terminal event or start failure | internal map replay flow only | coordinator snapshot + nav snapshot + replay controller settings + map shell replay overrides | none | cleared after successful restore | N/A | replay snapshot controller tests, map replay coordinator tests |
| replay session mode / `autoStopAfterFinish` override state | replay snapshot helper + replay controller runtime | replay coordinator + replay runtime only | replay start/terminal cleanup path | replay-controller config state | none | replay stop/reset/terminal restore | N/A | replay snapshot/controller tests |
| racing replay map shell state (`isTrackingLocation`, return/recenter button state, initial center, saved camera`) | replay snapshot helper | replay coordinator only | map replay flow only | map shell state before replay start | none | replay terminal restore | N/A | replay coordinator tests |
| Racing task UI model for in-flight current-leg display | task UI use-case/projector layer | none outside projector recompute | task UI only | `TaskRuntimeSnapshot` plus racing runtime seam when needed | none | follows source seams | N/A | UI/use-case tests |

### 2.1B Phase 2 Surface Contract

| Surface | User Concept Shown | Source Seam | Why | Must Not Happen |
|---|---|---|---|---|
| Expanded task sheet / manage tabs / task editor | selected task leg for editing and task mutation | `TaskManagerCoordinator.taskSnapshotFlow.activeLeg` through `TaskSheetCoordinatorUseCase` -> `TaskSheetUseCase` -> `TaskRepository` | this is an editor/selection surface | do not reinterpret editor selection as live nav progress |
| `TaskSheetViewModel.onLocationUpdate(...)` and `distanceToActiveWaypointMeters(...)` | selected task leg used by the sheet's current helpers | `TaskUiState.stats.activeIndex` derived from coordinator selected leg | these helpers are already coupled to sheet semantics | do not switch these helpers to `racingState.currentLegIndex` as a side effect of fixing the map indicator |
| Top-of-map minimized indicator for racing tasks (`MapTaskScreenUi.TaskMinimizedIndicatorOverlay`) | current navigation leg in flight | dedicated racing flight-surface projector combining `TaskRuntimeSnapshot` plus `TaskNavigationController.racingState` | this is the pilot-facing in-flight task surface the user expects to auto-move after valid start/turnpoint events | do not read `TaskUiState.stats.activeIndex` directly for racing in-flight display |
| Top-of-map minimized indicator for non-racing / AAT tasks | selected task leg | existing coordinator/task-sheet projection | no separate racing nav seam applies | do not invent a fake second owner for non-racing |
| Minimized-indicator prev/next taps | explicit manual leg change | existing `TaskManagerCoordinator.setActiveLeg(...)` mutation path | manual leg selection already has explicit coordinator/controller behavior | do not move manual mutation policy into the Composable |
| Route / glide / task-performance consumers | in-flight task progress and route state | unchanged dual-seam combine (`taskSnapshotFlow` + `racingState`) | these are already runtime surfaces with the correct owners | Phase 2 is not a route/glide rewrite |
| IGC declaration / LiveFollow / task export paths | selected task leg in the task snapshot | `TaskManagerCoordinator.taskSnapshotFlow` | these are task-definition/export surfaces, not cockpit status widgets | do not switch cross-feature exporters to racing nav state |

### 2.2 Dependency Direction

Confirm dependency flow remains:

`UI -> domain -> data`

- Modules/files touched:
  - `feature/tasks/src/main/java/com/trust3/xcpro/tasks/**`
  - `feature/map/src/main/java/com/trust3/xcpro/map/**`
  - `feature/map-runtime/src/main/java/com/trust3/xcpro/taskperformance/**`
  - tests under `feature/tasks` and `feature/map`
- Any boundary risk:
  - accidentally making `feature:map` the owner of task runtime instead of replay orchestration only
  - leaking `TaskNavigationController` directly into Composables/ViewModels instead of exposing a use-case/projector seam
  - re-introducing a second task-runtime authority by adding another combined cache

### 2.2A Reference Pattern Check

| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| `docs/refactor/Task_AAT_Ownership_Release_Grade_Phased_IP_2026-03-15.md` | same family of task-runtime authority repair | owner-first state contract, explicit forbidden duplicates, phased tests before cleanup | this plan keeps the existing ADR split instead of moving all reads to one seam |
| `feature/map/src/main/java/com/trust3/xcpro/map/MapReplaySnapshotControllers.kt` | already owns replay capture/restore helpers | keep replay snapshot logic behind a focused controller instead of scattering restore code | widen capture/restore payload to full racing task/nav state |
| `feature/tasks/src/main/java/com/trust3/xcpro/tasks/navigation/NavigationRouteRepository.kt` | already combines task snapshot and racing nav state without inventing new authority | derive consumer outputs from both seams when needed | task UI projector may need a similar combine step for racing-only in-flight displays |
| `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenReplayCoordinator.kt` | already owns replay lifecycle orchestration | fix replay sequencing here instead of scattering replay cleanup across consumers | racing replay may need stricter terminal cleanup than the current event-only restore path |

### 2.2B Boundary Moves

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Replay restore completeness for racing task/nav state | ad hoc partial restore in map replay snapshot helper | explicit replay snapshot bundle owner in `RacingReplaySnapshotController` | one bounded owner should capture and restore both declared seams atomically | replay start/complete/cancel/failure tests |
| Racing replay failure cleanup and replay/live handoff ordering | implicit controller runtime ordering + event observer restore | explicit replay lifecycle fence owned by replay coordinator/runtime seam | avoid live-fix ingress and half-clean replay failure state | replay lifecycle tests |
| Racing advance phase restore | implicit `isArmed`-only restore | explicit controller restore of full advance snapshot | preserves `START_*` vs `TURN_*` phase correctly | controller snapshot/restore tests |
| Replay mode and `autoStopAfterFinish` restore | replay runtime mutable config with no racing snapshot owner | replay snapshot helper owns capture/restore of racing replay overrides | avoid leaking replay config after racing replay | replay snapshot/controller tests |
| Racing replay map shell override restore | ad hoc racing replay UI mutation with no restore | replay snapshot helper | racing replay should not permanently force map shell state | replay coordinator tests |
| Racing task UI "current leg" semantics | implicit `activeLeg`-only interpretation | explicit projector policy: selected leg vs navigation leg | stop accidental misuse of the wrong seam in UI | task UI/use-case tests |

### 2.2C Bypass Removal Plan

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `feature/map/src/main/java/com/trust3/xcpro/map/MapReplaySnapshotControllers.kt` | restores only `mode` and `isArmed` | full replay snapshot restore API (`activeLeg`, `RacingNavigationState`, full advance snapshot, replay mode/speed/cadence, `autoStopAfterFinish`, racing replay map shell state) | Phase 1 |
| `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenReplayCoordinator.kt` | manual replay reset with no complete terminal restore contract | replay lifecycle calls focused capture/reset/cleanup/terminal-restore API only | Phase 1 |
| replay controller terminal event path | `Completed`/`Cancelled` emitted after live sensors may already resume | restore/cleanup ordering that fences replay/live handoff explicitly | Phase 1 |
| replay restore through coordinator leg mutation | `setActiveLeg(...)` can trigger controller manual-leg listener | atomic/suppressed restore path for coordinator leg + nav state | Phase 1 |
| racing task UI/minimized indicator path | renders current leg from `TaskUiState.stats.activeIndex` only | explicit racing UI projector that chooses task-selection leg or nav leg by contract | Phase 2 |
| any future cross-feature read of racing progress from `TaskRepository.state` | UI projection treated as runtime authority | `TaskManagerCoordinator.taskSnapshotFlow` and `TaskNavigationController.racingState` only | Phase 2-3 |

### 2.2D File Ownership Plan

| File | New / Existing | Owner / Responsibility | Why Here | Why Not Another Layer/File | Split Needed? |
|---|---|---|---|---|---|
| `docs/refactor/Racing_Runtime_Seam_Replay_Restore_Phased_IP_2026-03-29.md` | New | execution contract for this seam repair | active phased plan belongs in `docs/refactor` | not a durable ADR by itself | No |
| `feature/map/src/main/java/com/trust3/xcpro/map/MapReplaySnapshotControllers.kt` | Existing | racing replay snapshot capture/restore, including replay overrides and map shell state | replay orchestration already lives in map feature | not task-runtime owner, not UI | No |
| `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenReplayCoordinator.kt` | Existing | replay start/cleanup/terminal lifecycle orchestration only | map feature owns replay orchestration | not the place to own task/nav state models | No |
| `feature/map/src/main/java/com/trust3/xcpro/replay/IgcReplayControllerRuntime.kt` | Existing | replay cleanup/terminal event ordering and stop semantics | replay runtime already owns session/source reset behavior | not a task-runtime owner, but Phase 1 may need narrow cleanup-order changes here | No |
| `feature/map/src/main/java/com/trust3/xcpro/replay/IgcReplayControllerRuntimePlayback.kt` | Existing | replay completion ordering and reset-after-finish sequencing | finish/reset ordering already lives here | not a task or UI file | No |
| `feature/map/src/main/java/com/trust3/xcpro/replay/IgcReplayControllerRuntimeLoadAndConfig.kt` | Existing | replay mode/`autoStopAfterFinish` config accessors | replay config lives here already | not task-owned | No |
| `feature/tasks/src/main/java/com/trust3/xcpro/tasks/TaskNavigationController.kt` | Existing | racing navigation snapshot/restore API and fix-driven orchestration | already owns `racingState` and advance state | not a map concern, not UI | No |
| `feature/tasks/src/main/java/com/trust3/xcpro/tasks/racing/navigation/RacingNavigationStateStore.kt` | Existing | storage/reset/restore of racing navigation state | state store should own state replacement mechanics | not coordinator-owned task snapshot | No |
| `feature/tasks/src/main/java/com/trust3/xcpro/tasks/TaskManagerCoordinator.kt` | Existing | selected task leg restore target only; task-runtime authority remains unchanged | coordinator already owns `TaskRuntimeSnapshot` | should not absorb nav-only runtime | No |
| `feature/tasks/src/main/java/com/trust3/xcpro/tasks/TaskSheetCoordinatorUseCase.kt` | Existing | coordinator/task-sheet read seam; may expose racing runtime input to a separate flight-surface projector | task sheet should consume use-case outputs, not raw controller | not in Composable or ViewModel | No |
| `feature/tasks/src/main/java/com/trust3/xcpro/tasks/TaskSheetUseCase.kt` | Existing | selected-leg editor/task-sheet projection only | task-sheet/editor semantics already live here | not the place to redefine all task UI state as in-flight nav status | No |
| `feature/tasks/src/main/java/com/trust3/xcpro/tasks/TaskFlightSurfaceUseCase.kt` | New | racing flight-surface projector for the minimized/top-map indicator | Phase 2 needs a dedicated projector that can combine coordinator snapshot and racing nav state without mutating sheet/editor semantics | not in `TaskRepository`, not in Composables, not in replay/map orchestration code | No |
| `feature/tasks/src/main/java/com/trust3/xcpro/tasks/TaskFlightSurfaceViewModel.kt` | New or existing split if needed | exposes the minimized-indicator UI model to map UI | keeps map UI from reading raw seams or overloading `TaskSheetViewModel.uiState` | not in `MapTaskScreenUi`, and not by broadening `TaskSheetViewModel` if that makes it mixed-responsibility | Maybe |
| `feature/map/src/main/java/com/trust3/xcpro/map/ui/task/MapTaskScreenUi.kt` | Existing | rendering only | must consume a clarified UI model, not decide seam policy itself | not a domain/projector owner | No |
| `feature/tasks/src/test/java/com/trust3/xcpro/tasks/TaskNavigationControllerTest.kt` | Existing | controller snapshot/restore and seam regression tests | controller behavior belongs here | not a map test | No |
| `feature/map/src/test/java/com/trust3/xcpro/map/MapScreenReplayCoordinatorTest.kt` | New | replay start/terminal restore behavior | map replay orchestration needs direct tests | no existing file covers it | No |
| `feature/map/src/test/java/com/trust3/xcpro/map/RacingReplaySnapshotControllerTest.kt` | New | snapshot capture/restore coverage | focused replay snapshot semantics deserve direct tests | too specific for broader coordinator test | No |
| `feature/map/src/test/java/com/trust3/xcpro/map/ui/task/MapTaskScreenUiTest.kt` | Existing | rendering regression for current-leg display contract | UI surface already has tests | projector policy still needs visible rendering proof | No |

### 2.2E Module and API Surface

| Contract / API | Owner | Consumers | Visibility | Why Needed | Compatibility / Removal Plan |
|---|---|---|---|---|---|
| full racing replay snapshot contract | `feature:map` replay helper | map replay coordinator only | internal | one atomic restore bundle for replay | keep internal; no cross-module exposure |
| full racing navigation restore API | `TaskNavigationController` | replay snapshot helper/tests only | internal/public as needed within module boundary | restore nav state and advance phase explicitly | keep narrow; avoid general mutation leakage upward |
| replay-mode / `autoStopAfterFinish` readback and restore API | replay runtime | replay snapshot helper/tests only | internal/public as needed within module boundary | racing replay needs to restore the replay controller's own temporary overrides | keep narrow; no broad replay-config surface expansion |
| optional racing task UI combined projection model | `feature:tasks` use-case/projector | task sheet / map task UI | internal/public as needed | make UI seam choice explicit instead of ad hoc | keep task-UI-only; do not promote as cross-feature authority |

### 2.2F Scope Ownership and Lifetime

| Scope / Owner | Why It Exists | Dispatcher | Cancellation Trigger | Why Not Caller-Owned / Existing Scope |
|---|---|---|---|---|
| `MapScreenReplayCoordinator` scope | own replay lifecycle and terminal restore observation | existing map replay scope | map screen/replay owner teardown | replay orchestration already lives here |
| `TaskNavigationController.bind(...)` caller scope | collect fix flow and keep navigation runtime bound to owner lifecycle | caller-owned | returned `Job` / owner scope cancellation | controller must not become a hidden lifecycle host |
| replay/live handoff fence | replay lifecycle owner (`MapScreenReplayCoordinator` + replay runtime) | existing replay scope/runtime | replay start/terminal completion only | start/terminal ordering must stay explicit and not depend on downstream UI collectors |

### 2.2G Compatibility Shim Inventory

| Shim / Bridge | Owner | Reason | Target Replacement | Removal Trigger | Test Coverage |
|---|---|---|---|---|---|
| dual-seam combine in route/glide consumers | route/glide repositories | valid existing architecture split | keep | none; this is not a temporary shim | existing route/glide tests |
| `TaskUiState.stats.activeIndex` as selected-leg projection | task UI projector | keep task selection state visible even when nav seam differs | explicit racing UI contract, not blind "current task progress" use | once UI naming/usage is explicit | task UI projection/render tests |
| racing replay map shell override restore | replay snapshot helper | keep Phase 1 scoped while racing replay already mutates shell state | may merge with broader replay UI snapshot helper later | once racing replay reuses a common replay UI snapshot path or equivalent focused restore | replay coordinator tests |

### 2.2H Canonical Formula / Policy Owner

| Formula / Constant / Policy | Canonical Owner File | Reused By | Why This Owner Is Canonical | Temporary Duplicates Allowed? |
|---|---|---|---|---|
| racing task cross-feature task authority | `TaskManagerCoordinator` + `TaskRuntimeSnapshot` | task UI, LiveFollow, IGC, map runtime | ADR-declared active task seam | No |
| racing navigation runtime authority | `TaskNavigationController` + `RacingNavigationState` | route, glide/task-performance, replay logging, racing-aware UI projection | ADR-declared nav seam | No |
| racing replay restore completeness policy | `MapReplaySnapshotControllers.kt` | replay coordinator only | replay helper should own replay bundle semantics | No |
| replay/live handoff cleanup ordering | replay runtime + replay coordinator contract | replay lifecycle only | replay controller owns source/session cleanup; coordinator owns racing replay restore timing | No |

### 2.2I Stateless Object / Singleton Boundary

No new singleton owner is planned in this track.

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| `RacingNavigationState.lastTransitionTimeMillis` and accepted-start timestamps | existing replay/live nav contract, unchanged | this plan restores values; it must not reinterpret them |
| replay session speed/cadence config | N/A configuration state | not a timebase-bearing data item |
| replay mode / `autoStopAfterFinish` overrides | N/A configuration state | temporary session overrides only |
| replay terminal restore sequencing | owner event order only | restore is triggered by terminal replay events and runtime cleanup order, not a time comparison |

Explicitly forbidden comparisons:

- replay event time vs wall time
- wall time-based reconstruction of racing nav phase during restore

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - `Main`: map UI/render interactions and replay orchestration callbacks as currently wired.
  - `Default`/existing controller path: pure state projection when needed.
  - No new long-lived background polling loop is allowed in this track.
- Primary cadence/gating sensor:
  - event-driven only: replay start, replay terminal event, task mutation, nav fix event.
- Hot-path latency budget:
  - replay terminal restore should complete within a single event cycle and must not wait for a later UI sync pass.

### 2.4A Logging and Observability Contract

No new production logging is planned. Temporary diagnostics, if needed during implementation, must be removed before completion.

### 2.5 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No new randomness
- Replay/live divergence rules:
  - replay keeps using the replay controller/session model already in place
  - this plan may change restore behavior around replay sessions, but must not change nav decisions for the same replay fix sequence
  - replay start resets to start-of-task state intentionally for the replay session only; terminal restore must restore pre-session runtime state exactly
  - replay failure/cancel/complete paths must converge to the same post-session live-ready state for racing replay
  - no live fix may mutate the racing reset/restore state in an uncontrolled gap between replay reset and replay ownership, or between replay terminal event and restore completion

### 2.5A Error and Degraded-State Contract

| Condition | Category | Owner | User-Visible Behavior | Retry / Fallback Policy | Test Coverage |
|---|---|---|---|---|---|
| racing replay start fails after snapshot capture | Recoverable | replay coordinator + snapshot helper | replay fails; pre-replay task/nav state is restored | restore immediately, no stale reset state left behind | replay start-failure test |
| racing replay runtime fails mid-session | Recoverable | replay runtime + replay coordinator + snapshot helper | replay fails; replay session is fully cleaned up and pre-replay state is restored | explicit cleanup before final restore, no half-replay state | failure-cleanup + restore tests |
| racing replay completes/cancels/fails after mutating task/nav state | Recoverable | replay coordinator + snapshot helper | replay ends; pre-replay task/nav state is restored | restore exactly once on terminal event after replay cleanup fence | complete/cancel/failure tests |
| captured snapshot is missing or partially populated | Degraded | snapshot helper | replay ends without additional corruption; state should not be further reset | narrow guard, explicit tests, no silent field drop | snapshot helper tests |
| replay restore re-enters controller manual-leg path | Degraded / correctness risk | controller + replay snapshot helper | no user-visible false disarm or leg overwrite | restore atomically or under suppression | controller/replay restore tests |
| racing replay shell state is overridden during replay | Recoverable | replay snapshot helper | map shell returns to the pre-replay state after replay | restore paired shell state on terminal event | replay coordinator tests |
| selected task leg and nav leg intentionally differ because auto-advance is disabled/manual | User Action / expected mode behavior | coordinator + navigation controller | UI must reflect the right concept for the surface | no forced synchronization; projector chooses the right seam | controller + UI projection tests |

### 2.5B Identity and Model Creation Strategy

| Entity / Value | Created By | ID / Time Source | Deterministic Required? | Why This Boundary Owns Creation |
|---|---|---|---|---|
| racing replay snapshot bundle | replay snapshot helper | no new external ID; captured in-memory state only | Yes | replay helper owns session-local capture/restore |

### 2.5C No-Op / Test Wiring Contract

No new `NoOp` production paths are planned.

### 2.6 Enforcement Coverage

| Risk | Rule Reference | Guard Type | File/Test |
|---|---|---|---|
| replay restores only partial racing task/nav state | authoritative state + replay determinism | unit tests + review | new replay snapshot/controller tests |
| replay failure path leaves replay runtime partially active | authoritative state + replay determinism | unit tests + review | replay coordinator/runtime tests |
| live-fix race window mutates racing state before/after replay restore | authoritative state + replay determinism | unit tests + review | replay coordinator tests |
| `armState` is lost during restore | authoritative state contract | unit tests | `TaskNavigationControllerTest` + snapshot helper tests |
| replay mode or `autoStopAfterFinish` leak after racing replay | authoritative state + replay determinism | unit tests | replay snapshot/controller tests |
| restore path triggers controller manual-leg listener and corrupts nav restore | authoritative state contract | unit tests | controller/replay restore tests |
| UI continues to treat `activeLeg` as universal in-flight progress | responsibility ownership + SSOT contract | unit tests + review | task-sheet / map-task UI tests |
| a third task-runtime cache is introduced while fixing consumers | authoritative state contract | review + targeted ownership tests | code review + scoped tests |
| coordinator absorbs nav-only runtime fields without ADR | module/API governance | review | plan compliance review |

### 2.7 Visual UX SLO Contract

| Outcome | SLO ID | Baseline | Target | Validation/Test | Phase Gate |
|---|---|---|---|---|---|
| Racing replay returns to the same task progress the user had before replay | TASK-UX-RT-01 | currently resets task/nav progress after replay | exact pre-replay progress is restored on complete/cancel/failure | replay coordinator tests + manual replay smoke | Phase 1 |
| Racing replay does not leave replay session state or shell controls stuck after failure/cancel | TASK-UX-RT-01B | current failure/cancel paths can leave replay-specific state behind | replay exits to the same live-ready shell/session state regardless of terminal path | replay coordinator tests + manual replay smoke | Phase 1 |
| Racing task surfaces no longer imply the wrong current waypoint in flight | TASK-UX-RT-02 | some surfaces use selected leg as if it were nav leg | racing UI semantics are explicit and tested | task-sheet/UI tests + manual smoke | Phase 2 |

## 3) Data Flow (Before -> After)

Before:

```text
Racing replay start
  -> RacingReplaySnapshotController.captureIfNeeded()
  -> TaskNavigationController.resetNavigationState()
  -> TaskManagerCoordinator.setActiveLeg(0)
  -> replay controller stop/reset may resume live sensors before replay log owns the stream
  -> replay runs and mutates racingState/task selection
  -> terminal replay event
  -> restore replay speed/cadence + mode/isArmed only
  -> replay failure path may not fully clean replay runtime/session state

Task UI
  -> TaskManagerCoordinator.taskSnapshotFlow
  -> TaskRepository / TaskUiState
  -> activeLeg rendered as current waypoint

Route / glide / task performance
  -> TaskManagerCoordinator.taskSnapshotFlow + TaskNavigationController.racingState
  -> boundary-aware route/progress
```

After:

```text
Racing replay start
  -> RacingReplaySnapshotController.capture(full coordinator + nav + advance + replay settings + shell overrides)
  -> explicit replay reset to start-of-task state under replay/live handoff fence
  -> replay runs
  -> replay cleanup reaches one live-ready terminal state
  -> terminal replay event or start failure
  -> RacingReplaySnapshotController.restore(full pre-replay state exactly once, without controller manual-leg interference)

Task UI
  -> TaskManagerCoordinator.taskSnapshotFlow
  -> optional racing UI projector combines with racingState when the surface needs in-flight nav progress
  -> UI renders selected leg or nav leg by explicit contract

Route / glide / task performance
  -> unchanged dual-seam combine using task snapshot + racingState
```

## 4) Implementation Phases

### Phase 0 - Baseline and Contract Lock

- Status update:
  - Completed on 2026-03-29.
  - Focused replay/controller regression tests were added to lock the pre-fix failure modes before the restore implementation landed.
- Goal:
  - Lock current intended authority split before changing code.
  - Add or update tests that reproduce the replay restore gap, the replay failure/cleanup gap, the live-fix race windows, and the allowed disabled/manual divergence.
- Files to change:
  - `feature/tasks/src/test/java/com/trust3/xcpro/tasks/TaskNavigationControllerTest.kt`
  - `feature/map/src/test/java/com/trust3/xcpro/map/MapScreenReplayCoordinatorTest.kt` (new)
  - `feature/map/src/test/java/com/trust3/xcpro/map/RacingReplaySnapshotControllerTest.kt` (new)
- Ownership/file split changes in this phase:
  - none; tests only
- Tests to add/update:
  - replay start-failure restores pre-replay task/nav state
  - replay complete/cancel/failure restores pre-replay task/nav state
  - racing replay failure returns replay runtime/session state to live-ready cleanup
  - racing replay restore does not get overwritten by controller leg-change listener side effects
  - no uncontrolled live-fix ingress around replay start/terminal restore windows
  - controller snapshot/restore preserves full `armState`
  - auto-advance disabled path still allows nav state to move without forcing coordinator leg change
- Exit criteria:
  - tests clearly distinguish intended dual-seam behavior from the current replay bug

### Phase 1 - Replay Snapshot and Restore Correctness

- Status update:
  - Completed on 2026-03-29.
  - The replay/runtime fix landed in commit `3a33121` together with the Phase 0 replay/controller regression coverage.
- Goal:
  - make racing replay capture and restore the full pre-replay runtime state
  - preserve coordinator `activeLeg`, full `RacingNavigationState`, full advance snapshot, replay mode, `autoStopAfterFinish`, and racing replay shell overrides
  - fence replay/live handoff so live fixes cannot mutate reset/restore state in an uncontrolled gap
  - ensure failure cleanup converges to the same live-ready state as complete/cancel
- Files to change:
  - `feature/map/src/main/java/com/trust3/xcpro/map/MapReplaySnapshotControllers.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenReplayCoordinator.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/replay/IgcReplayControllerRuntime.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/replay/IgcReplayControllerRuntimePlayback.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/replay/IgcReplayControllerRuntimeLoadAndConfig.kt`
  - `feature/tasks/src/main/java/com/trust3/xcpro/tasks/TaskNavigationController.kt`
  - `feature/tasks/src/main/java/com/trust3/xcpro/tasks/racing/navigation/RacingNavigationStateStore.kt`
- Ownership/file split changes in this phase:
  - replay snapshot helper becomes the single owner of replay capture/restore completeness for racing replay
  - replay coordinator/runtime gain explicit replay/live handoff ordering responsibility
  - controller/state store gain explicit restore entrypoints only; no new UI-facing authority
- Tests to add/update:
  - terminal replay restore returns task selection leg and nav leg to pre-replay values
  - advance phase restore returns `START_*` vs `TURN_*` correctly
  - replay mode and `autoStopAfterFinish` restore correctly after racing replay
  - failed racing replay performs full replay cleanup before final restore
  - restore happens once per replay session
  - restore path does not trigger false manual-leg handling
  - racing replay map shell state returns to pre-replay values if this scope lands in Phase 1
- Exit criteria:
  - replay no longer leaves the app in a reset-to-start or half-replay state after replay ends
  - racing replay terminal paths converge to one live-ready post-session state
  - no new task-runtime owner is introduced

### Phase 1A - 5-Minute Implementation Note

- Scope lock:
  - Racing replay map-shell restore stays in Phase 1 because `MapScreenReplayCoordinator` already forces tracking/return-button/initial-centering state on replay start, and Phase 1 is the replay-session correctness slice.
  - Phase 1 does not change task UI seam policy; that remains Phase 2.
- Preferred owner shape:
  - `RacingReplaySnapshotController` owns one full in-memory racing replay bundle:
    - selected task leg
    - full `RacingNavigationState`
    - full `RacingAdvanceState.Snapshot`
    - replay mode, cadence, speed, and `autoStopAfterFinish`
    - racing replay map-shell overrides
  - `MapScreenReplayCoordinator` owns replay lifecycle sequencing only.
  - `TaskNavigationController` owns atomic restore of nav state plus advance snapshot; it must not push replay-restore policy up into UI/task-sheet layers.
- Preferred restore shape:
  - Add one explicit replay-restore entrypoint on `TaskNavigationController` that restores:
    - full `RacingNavigationState`
    - full `RacingAdvanceState.Snapshot`
    - coordinator selected leg under restore suppression
  - Do not restore by separately calling `setAdvanceMode(...)`, `setAdvanceArmed(...)`, and `TaskManagerCoordinator.setActiveLeg(...)` from the map layer without a suppression guard.
  - The restore path must suppress the manual-leg listener while replay restore reapplies the selected leg.
- Preferred replay lifecycle ordering:
  1. capture the full racing replay bundle once
  2. stop/cleanup any prior replay session without leaving racing replay in a partially reset live state
  3. reset racing nav/task state for the replay session and apply replay overrides
  4. run replay
  5. on `Completed` / `Cancelled` / `Failed`, perform replay controller cleanup fence first
  6. restore replay config + map shell + task/nav bundle exactly once
  7. clear the captured bundle and drop `racingReplayActive`
  - No live-fix-driven racing-state mutation is allowed in the gap between steps 3 and 4 or between steps 5 and 6.
- Test ownership lock:
  - `TaskNavigationControllerTest`:
    - full `RacingAdvanceState.Snapshot` restore
    - replay restore does not trigger false manual-leg sync/disarm
  - `RacingReplaySnapshotControllerTest`:
    - capture/restore of the full racing replay bundle
    - restore-once / clear-after-restore semantics
  - `MapScreenReplayCoordinatorTest`:
    - start failure restores pre-replay state
    - complete/cancel/failure converge to one live-ready post-session state
    - replay cleanup fence runs before final racing restore
    - no uncontrolled live-fix ingress around replay start/terminal restore
- Coding stop condition:
  - If Phase 1 cannot keep replay cleanup fencing inside replay/runtime owners without leaking replay policy into task UI or task-sheet projection, stop and update the plan before coding further.

### Phase 2 - Consumer Policy Cleanup

- Status update:
  - Completed on 2026-03-29 in commit `61f5ac4`.
  - The minimized top-of-map racing indicator now uses an explicit flight-surface projector while task sheet/editor semantics remain on coordinator-selected leg.
- Goal:
  - remove ambiguous UI assumptions about `activeLeg` vs racing nav leg
  - keep editor/selection semantics separate from in-flight nav-progress semantics
- Files to change:
  - `feature/tasks/src/main/java/com/trust3/xcpro/tasks/TaskSheetCoordinatorUseCase.kt`
  - `feature/tasks/src/main/java/com/trust3/xcpro/tasks/TaskFlightSurfaceUseCase.kt` (new)
  - `feature/tasks/src/main/java/com/trust3/xcpro/tasks/TaskFlightSurfaceViewModel.kt` (new or split from existing VM if the final shape stays clean)
  - `feature/map/src/main/java/com/trust3/xcpro/map/ui/task/MapTaskScreenUi.kt`
  - `feature/map/src/test/java/com/trust3/xcpro/map/ui/task/MapTaskScreenUiTest.kt`
  - task-flight-surface / task-sheet tests under `feature/tasks/src/test/java/com/trust3/xcpro/tasks/*`
- Ownership/file split changes in this phase:
  - `TaskUiState.stats.activeIndex` stays the selected-leg/editor contract
  - a dedicated racing flight-surface projector becomes the owner of minimized-indicator "current TP in flight" semantics
  - Composables still render only; they must not choose authority at the UI layer
- Tests to add/update:
  - task sheet/editor surfaces still render selected leg from coordinator state
  - racing minimized indicator renders nav leg from the dedicated flight-surface projector
  - manual prev/next taps on the minimized indicator remain explicit leg mutations
  - non-racing/AAT paths remain unchanged
- Exit criteria:
  - no UI surface is implicitly using `activeLeg` as if it were universal in-flight nav progress
  - the top-of-map racing indicator advances with the nav seam without redefining editor selection semantics

### Phase 3 - Docs Sync and Hardening

- Status update:
  - Completed on 2026-03-29.
  - `docs/ARCHITECTURE/PIPELINE.md` now documents the repaired replay restore contract and the dedicated racing flight-surface seam.
  - PR-ready verification passed via `scripts/qa/run_change_verification.bat -Profile pr-ready`.
- Goal:
  - align architecture-facing docs and lock verification expectations
- Files to change:
  - `docs/ARCHITECTURE/PIPELINE.md` if replay/task wiring changes are documented there
  - this phased IP status updates
  - any narrow feature doc that describes racing replay restore behavior
- Ownership/file split changes in this phase:
  - none
- Tests to add/update:
  - final regression sweep for replay restore and racing task UI semantics
- Exit criteria:
  - docs reflect the repaired seam contract
  - verification evidence is recorded in the plan

## 5) Test Plan

- Unit tests:
  - `TaskNavigationControllerTest`
  - new `RacingReplaySnapshotControllerTest`
  - new `MapScreenReplayCoordinatorTest`
  - task-sheet/map-task UI regression tests
- Replay/regression tests:
  - replay start -> complete restores pre-session state
  - replay start -> cancel restores pre-session state
  - replay start -> failure restores pre-session state
  - replay start -> failure fully cleans replay session/source state
  - replay complete/cancel do not leak replay mode or `autoStopAfterFinish`
  - replay restore does not get clobbered by live-fix ingress or manual-leg listener side effects
  - repeated replay sessions do not leak prior snapshot state
- Integration-sensitive checks:
  - `NavigationRouteRepositoryTest`
  - `TaskPerformanceRepositoryTest`
  - `GlideComputationRepositoryTest`
  - any racing replay validation tests impacted by changed restore behavior

## 6) Verification Plan

- Smallest sufficient first:
  - `./gradlew :feature:tasks:testDebugUnitTest --tests "com.trust3.xcpro.tasks.TaskNavigationControllerTest"`
  - `./gradlew :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.map.MapScreenReplayCoordinatorTest" --tests "com.trust3.xcpro.map.RacingReplaySnapshotControllerTest" --tests "com.trust3.xcpro.map.ui.task.MapTaskScreenUiTest"`
- Add focused replay validation when Phase 1 code lands:
  - `./gradlew :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.tasks.racing.navigation.RacingReplayValidationTest"`
- Slice-complete proof:
  - `./gradlew enforceRules`
  - `./gradlew testDebugUnitTest`
  - `./gradlew assembleDebug`
- Recorded execution:
  - 2026-03-29: `scripts/qa/run_change_verification.bat -Profile pr-ready` passed
  - 2026-03-29: `./gradlew :feature:tasks:testDebugUnitTest` passed
  - 2026-03-29: `./gradlew :feature:map:testDebugUnitTest` passed
  - 2026-03-29: `./gradlew enforceRules` passed in earlier focused Phase 1/2 verification as well as the final `pr-ready` sweep
- If replay/UI behavior changes are substantial on device:
  - run the smallest relevant connected test lane or manual replay smoke with evidence

## 7) Advice

- Best implementation order:
  1. Fix replay lifecycle fencing and cleanup first.
  2. Then fix replay snapshot/restore completeness.
  3. Add tests that lock the allowed dual-seam behavior and restore ordering.
  4. Only then clean up UI/consumer semantics.
- Best architecture stance:
  - keep the ADR-declared split
  - do not collapse task snapshot and racing nav state into one owner in this change
- Phase 1 is larger than originally stated:
  - it is a replay-session correctness slice, not just a task/nav snapshot slice
- 2026-03-29 targeted seam/code-pass result for Phase 2:
  - `TaskSheetUseCase` / `TaskSheetViewModel` currently use `TaskUiState.stats.activeIndex` as the selected-leg/editor index; repurposing that field globally would couple the map indicator fix to editor/proximity behavior and is not the smallest safe change.
  - The best Phase 2 shape is a dedicated racing flight-surface projector for the top-of-map minimized indicator, while task sheet/editor surfaces keep coordinator-selected-leg semantics.
  - Write that choice into the projector/use-case contract, not the Composable.
