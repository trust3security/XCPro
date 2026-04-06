# ADR_TASK_RUNTIME_AUTHORITY_2026-03-15

## Metadata

- Title: Task runtime authority moves to `TaskManagerCoordinator` snapshot flow
- Date: 2026-03-15
- Status: Accepted
- Owner: XCPro Team
- Reviewers: XCPro Team
- Related issue/PR: RULES-20260315-18 / TBD
- Related change plans:
  - `docs/refactor/Task_AAT_Ownership_Release_Grade_Phased_IP_2026-03-15.md`
  - `docs/refactor/Map_Task_Runtime_Module_Right_Sizing_2026-03-15.md`
- Supersedes: None
- Superseded by: None

## Context

- Problem:
  - Active task runtime state was being read from multiple seams.
  - `TaskManagerCoordinator` owned the live task mutation path and startup restore path, while `GlideTargetRepository` and IGC declaration code read `TaskRepository.state`.
  - This allowed restored or loaded tasks to be invisible to non-UI consumers until task-sheet sync logic ran.
- Why now:
  - The drift was release-relevant because startup restore and named-task load could present stale or empty task state outside the task UI.
- Constraints:
  - Preserve MVVM + UDF + SSOT.
  - Avoid widening `TaskRepository` scope as a band-aid.
  - Keep `feature:tasks` task-core-owned and avoid new `feature:tasks -> feature:map` back-edges.
  - Keep existing task UI working while later phases demote projector authority.
- Existing rule/doc references:
  - `docs/ARCHITECTURE/ARCHITECTURE.md`
  - `docs/ARCHITECTURE/CODING_RULES.md`
  - `docs/ARCHITECTURE/PIPELINE.md`
  - `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`

## Decision

`TaskManagerCoordinator` is the canonical cross-feature runtime read owner for active task state. It exposes a read-only `StateFlow<TaskRuntimeSnapshot>` containing `taskType`, `task`, and `activeLeg`.

Required:
- ownership/boundary choice:
  - Cross-feature task runtime reads must use `TaskManagerCoordinator.taskSnapshotFlow`.
  - `TaskRepository` is no longer allowed to act as cross-feature task authority.
  - Task UI may keep using compatibility projection until Phase 2 removes the remaining sync role.
  - Task-content autosave and named persistence must flow through `TaskCoordinatorPersistenceBridge` and `TaskEnginePersistenceService`.
  - `RacingTaskManager` and `AATTaskManager` are runtime mutation hosts only and must not construct persistence/file-I/O collaborators.
  - `TaskNavigationController.bind(...)` owns listener registration only for the caller scope lifetime and must tear it down when that scope/job completes.
- dependency direction impact:
  - No new dependency direction is introduced.
  - `feature:map-runtime` consumes a read-only task snapshot contract from `feature:tasks`.
  - `feature:map` consumes the task gesture/runtime contract from `feature:map-runtime`
    and the AAT edit-mode read seam through `MapTasksUseCase`.
- API/module surface impact:
  - New cross-module API: `TaskRuntimeSnapshot`, `TaskManagerCoordinator.taskSnapshotFlow`,
    and `TaskManagerCoordinator.aatEditWaypointIndexFlow`.
  - `GlideTargetRepository`, IGC declaration sources, and `MapTasksUseCase` now read the snapshot seam.
- time-base/determinism impact:
  - None. The snapshot carries existing task state and does not introduce wall-clock ownership or replay variance.
- concurrency/buffering/cadence impact:
  - Read exposure uses `StateFlow` with immediate in-process publication on coordinator mutations and restore/load paths.
  - No extra buffering, debounce, or asynchronous mirror layer is introduced in this phase.

## Alternatives Considered

| Option | Why Considered | Why Rejected / Not Chosen |
|---|---|---|
| Make `TaskRepository` `@Singleton` and keep it authoritative | Fastest-looking way to remove instance drift | Keeps the wrong owner, preserves manual sync failure modes, and hardens duplicated SSOT |
| Keep current split and add more sync calls | Minimal code churn | Hidden bidirectional sync was already the failure mode; more sync points make restore/load correctness weaker |
| Publish task runtime from engines/managers directly | Closer to raw task-type state | Cross-feature consumers need one task-type-neutral seam; coordinator already owns restore/load routing |

## Consequences

### Benefits
- Restored and loaded task state becomes visible to non-UI consumers without task-sheet initialization.
- Cross-feature task reads now have one explicit owner.
- Phase 2 can demote `TaskRepository` without breaking map/runtime consumers.
- Persistence side effects now have one owner path instead of hidden manager writes.
- Navigation listener lifetime is explicit instead of leaking into the coordinator.

### Costs
- `TaskManagerCoordinator` gains snapshot publication responsibility.
- Compatibility accessors existed temporarily; follow-on
  `ADR_TASK_SYNC_READ_SEAM_2026-04-06.md` removes the remaining public raw
  task/leg accessors in favor of `currentSnapshot()`.
- The coordinator bridge now also owns autosave triggering for task-content mutations.

### Risks
- Snapshot updates could be missed if new coordinator mutation paths are added without publishing.
- Phase 1 does not yet remove UI-side projector authority or AAT target duplication.
- Follow-on task changes must keep using the coordinator snapshot seam and the map-owned gesture/runtime boundary.

## Validation

- Tests/evidence required:
  - `TaskManagerCoordinatorTest`
  - `GlideTargetRepositoryTest`
  - `IgcTaskDeclarationSourceTest`
  - `MapTasksUseCase` consumers verified through map unit tests
  - `./gradlew enforceRules`
  - `./gradlew testDebugUnitTest`
  - `./gradlew assembleDebug`
- SLO or latency impact:
  - No material runtime latency change expected; updates remain in-memory `StateFlow` publications.
- Rollout/monitoring notes:
  - 2026-03-15: Phase 3 aligned AAT service-backed autosave and named persistence onto a canonical JSON task payload with legacy read fallback only.
  - 2026-03-15: Phase 4 removed production manager-owned persistence side effects, moved autosave triggering to the coordinator bridge/service path, and bound task-navigation listener lifetime to caller scope cancellation.
  - 2026-03-15: Phase 5 moved the task gesture contract, AAT gesture runtime, and AAT map coordinate conversion into `feature:map`, removed task-side MapLibre helpers, and dropped the `feature:tasks` MapLibre dependency.
  - 2026-03-15: Phase 6 added CI guards for MapLibre drift in `feature:tasks`, synced the architecture docs, passed the required verification gates, and resolved deviation `RULES-20260315-18`.
  - 2026-03-15: Follow-on module right-sizing moved the reusable task gesture contract, AAT gesture runtime, and AAT coordinate conversion into `feature:map-runtime`; `feature:map` retained only the shell creation/wiring path.
  - 2026-03-15: Follow-on map-shell extraction moved task gesture creation and AAT edit-mode derivation into `MapScreenTaskShellCoordinator`; `MapScreenViewModel` no longer owns a duplicate AAT edit-mode flag.

## Documentation Updates Required

- `ARCHITECTURE.md`: No rule change required.
- `CODING_RULES.md`: No rule change required.
- `PIPELINE.md`: Document the coordinator snapshot seam and current task startup/read path.
- `CONTRIBUTING.md`: No change required.
- `KNOWN_DEVIATIONS.md`: Record the deviation as resolved.

## Rollback / Exit Strategy

- What can be reverted independently:
  - The snapshot contract and each consumer rewire can be reverted independently if Phase 1 regressions surface.
- What would trigger rollback:
  - Non-UI task consumers regress on restore/load visibility or task updates.
- How this ADR is superseded or retired:
  - Supersede only if a later accepted ADR moves canonical task runtime authority to another explicit non-duplicated owner without reintroducing mirrored authority.
