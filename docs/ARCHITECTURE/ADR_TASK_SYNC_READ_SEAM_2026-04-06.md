# ADR_TASK_SYNC_READ_SEAM_2026-04-06

## Metadata

- Title: Synchronous task reads use `TaskManagerCoordinator.currentSnapshot()`, not raw task or leg accessors
- Date: 2026-04-06
- Status: Accepted
- Owner: Codex
- Reviewers: TBD
- Related issue/PR: TBD
- Related change plan:
  - `docs/refactor/XCPro_Architecture_Hardening_Release_Grade_Phased_IP_2026-04-06.md`
- Supersedes:
  - none
- Superseded by:
  - none

## Context

- Problem:
  - `TaskManagerCoordinator.taskSnapshotFlow` was already the canonical
    cross-feature task-runtime seam, but the coordinator still exposed public
    raw accessors for `currentTask` and `currentLeg`.
  - That left a bypass around the approved snapshot boundary and kept
    `TaskNavigationController` plus a few replay/IGC helpers split between two
    read shapes.
- Why now:
  - Phase 4 of the architecture hardening plan is the right point to finish the
    task-read seam cleanup after the runtime-scope and fallback-wiring phases.
- Constraints:
  - Keep task runtime behavior unchanged.
  - Preserve replay determinism and racing replay restore behavior.
  - Avoid a wide consumer migration; most production callers are already on the
    snapshot seam.
- Existing rule/doc references:
  - `AGENTS.md`
  - `docs/ARCHITECTURE/ARCHITECTURE.md`
  - `docs/ARCHITECTURE/CODING_RULES.md`
  - `docs/ARCHITECTURE/PIPELINE.md`
  - `docs/ARCHITECTURE/ADR_TASK_RUNTIME_AUTHORITY_2026-03-15.md`

## Decision

Public synchronous task reads now go through
`TaskManagerCoordinator.currentSnapshot()`, which is a narrow wrapper around the
canonical `taskSnapshotFlow`.

Required:
- ownership/boundary choice:
  - `TaskManagerCoordinator.taskSnapshotFlow` remains the authoritative
    cross-feature task-runtime seam.
  - `TaskManagerCoordinator.currentSnapshot()` is the only approved
    synchronous read wrapper for callers that cannot collect the flow.
  - Raw coordinator accessors for `currentTask`, `currentLeg`,
    `getCurrentLegWaypoint()`, and `getActiveLeg()` are removed from the public
    surface.
- dependency direction impact:
  - none; consumers still depend on `feature:tasks` only.
- API/module surface impact:
  - new public synchronous helper: `TaskManagerCoordinator.currentSnapshot()`.
  - map/runtime/IGC helpers read the same snapshot shape for both async and
    sync access.
- time-base/determinism impact:
  - none; the synchronous helper reads the already-published snapshot state.
- concurrency/buffering/cadence impact:
  - none; no extra buffering or mirror state is introduced.

## Alternatives Considered

| Option | Why Considered | Why Rejected / Not Chosen |
|---|---|---|
| Keep `currentTask` and `currentLeg` public | smallest diff | preserves the boundary bypass the architecture already disallows |
| Force every synchronous caller to read `taskSnapshotFlow.value` directly | simple | duplicates read-shape knowledge across consumers instead of giving one narrow coordinator-owned sync seam |
| Replace the `StateFlow` seam with synchronous getters only | would reduce flow API surface | breaks the canonical reactive seam and weakens downstream composition |

## Consequences

### Benefits
- Cross-feature task reads now have one runtime shape in both synchronous and
  reactive paths.
- The coordinator no longer exposes raw task/leg state as parallel public API.
- Replay, map, and IGC helpers are aligned with the documented task-runtime
  contract.

### Costs
- A few callsites and tests need to switch to `currentSnapshot()`.

### Risks
- New coordinator mutation paths could still drift if they forget to publish the
  snapshot before synchronous consumers read it.

## Validation

- Tests/evidence required:
  - `./gradlew enforceRules`
  - targeted `feature:tasks` and `feature:map` unit tests
  - touched-module compile
- SLO or latency impact:
  - none expected
- Rollout/monitoring notes:
  - no staged rollout required; this is read-seam hardening only

## Documentation Updates Required

- `ARCHITECTURE.md`:
  - no change required
- `CODING_RULES.md`:
  - no change required
- `PIPELINE.md`:
  - update the replay/task seam notes to mention `currentSnapshot()`
- `CONTRIBUTING.md`:
  - no change required
- `KNOWN_DEVIATIONS.md`:
  - no change required

## Rollback / Exit Strategy

- What can be reverted independently:
  - the `currentSnapshot()` helper
  - each consumer migration
- What would trigger rollback:
  - task/replay regressions caused by incorrect snapshot publication
- How this ADR is superseded or retired:
  - supersede it only if a later ADR changes the canonical task runtime owner or
    the approved synchronous read shape
