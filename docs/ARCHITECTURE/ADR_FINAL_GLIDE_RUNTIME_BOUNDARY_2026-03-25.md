# ADR_FINAL_GLIDE_RUNTIME_BOUNDARY_2026-03-25

## Metadata

- Title: Final glide uses fused flight SSOT, task-owned canonical route, and a non-UI glide runtime owner
- Date: 2026-03-25
- Status: Accepted; implemented locally through Phase 4 and full local proof passed
- Owner: XCPro Team
- Reviewers: XCPro Team
- Related issue/PR: TBD
- Related change plan:
  - `docs/ARCHITECTURE/CHANGE_PLAN_FINAL_GLIDE_ROUTE_AND_RUNTIME_MIGRATION_2026-03-25.md`
- Supersedes:
  - none
- Superseded by:
  - none

## Context

- Problem:
  - Final glide consumes both fused flight samples and active task runtime
    state, which makes it easy to drift into the wrong owner.
  - The current implementation on `main` still derives the finish target in
    `feature:map` and executes glide solving from `MapScreenObservers`, which is
    too UI-adjacent for a durable cross-feature runtime boundary.
  - The current remaining-route path is simplified to waypoint centers too
    early, so cylinders, finish lines, sectors, and similar observation zones
    cannot be represented canonically in the glide route.
- Why now:
  - Repo-level guidance is being tightened for hard multi-phase work, and final
    glide is a cross-feature seam that should not depend on prompt history or
    convenience ownership.
  - `feature:map-runtime` already exists as a non-UI runtime module and already
    depends on `feature:flight-runtime` plus `feature:tasks`, so the migration
    does not require a brand-new top-level module.
- Constraints:
  - Preserve MVVM + UDF + SSOT.
  - Keep `FlightDataRepository` as the flight-data SSOT.
  - Keep `CompleteFlightData` flight-data only.
  - Keep replay deterministic and avoid new wall-clock dependencies.
  - Reuse existing task runtime + boundary helpers instead of duplicating route
    math in `feature:map`, cards, or Composables.
  - Prefer additive, low-risk migration over a big-bang rewrite.
- Existing rule/doc references:
  - `AGENTS.md`
  - `docs/ARCHITECTURE/ARCHITECTURE.md`
  - `docs/ARCHITECTURE/CODING_RULES.md`
  - `docs/ARCHITECTURE/PIPELINE.md`
  - `docs/ARCHITECTURE/ADR_TASK_RUNTIME_AUTHORITY_2026-03-15.md`
  - `docs/ARCHITECTURE/ADR_FLIGHT_RUNTIME_BOUNDARY_2026-03-15.md`

## Implementation State

- Historical mainline implementation on `main`:
  - `GlideTargetRepository` derives the finish target in `feature:map`.
  - `MapScreenObservers` invokes `FinalGlideUseCase` from the map shell.
  - Remaining-route projection falls back to waypoint centers.
- Current local branch state (`final-glide-route-runtime-migration`, Phase 4, 2026-03-25):
  - `NavigationRouteRepository` in `feature:tasks` owns the boundary-aware
    remaining-route seam.
  - `GlideComputationRepository`, `GlideTargetProjector`, and
    `FinalGlideUseCase` in `feature:map-runtime` own final-glide computation
    and glide-policy projection.
  - `feature:map` is consumer/adapter only for active glide output.
- Durable target after cleanup:
  - the local Phase 4 owner set now matches the durable boundary
  - `GlideTargetRepository` is removed; no map-owned compatibility glide owner remains
  - if finish-rule export needs to grow beyond the current narrow adapter, that
    growth must happen as a task-owned contract rather than by expanding
    map-runtime into a second route owner

## Decision

Final glide remains a derived runtime projection assembled from the existing
flight and task authorities. It does not become new stored state in
`CompleteFlightData`, `TaskRepository`, or UI-owned models.

Required durable boundary:

- flight-data authority:
  - `FlightDataRepository` remains the authoritative fused-flight-data SSOT.
  - `CompleteFlightData` remains flight-data only and must not absorb
    task-route or glide-derived state.
- task-runtime authority:
  - `TaskManagerCoordinator.taskSnapshotFlow` remains the authoritative
    cross-feature read seam for active task definition and active leg.
  - `TaskNavigationController.racingState` remains the authoritative racing
    navigation runtime seam.
  - `TaskRepository` remains task-sheet/UI projection only and must not become a
    cross-feature runtime authority.
- canonical route authority:
  - Canonical remaining-route projection belongs with task runtime / boundary
    owners in `feature:tasks`.
  - Canonical racing-task route geometry must reuse existing task boundary
    planners/helpers where available and must not be reduced to waypoint-center
    routing when a boundary-aware touchpoint can be computed.
- glide computation authority:
  - Derived glide computation belongs in a non-UI runtime/domain owner.
  - `feature:map-runtime` is the preferred current host for that owner because
    it already depends on `feature:flight-runtime` and `feature:tasks`.
  - `feature:map` remains a consumer/adapter only. It may map derived glide
    outputs into `RealTimeFlightData`, but it must not remain the long-term
    owner of canonical route derivation or glide orchestration.
  - `GlideTargetProjector` is the explicit runtime owner of current
    finish-rule and glide-status projection.
  - It may read `TaskRuntimeSnapshot` only for the current racing finish rule
    and fallback finish label; it must not derive remaining-route geometry or
    expand into general task policy ownership.
- migration discipline:
  - Existing `feature:map` glide classes were allowed temporarily as
    compatibility glue during phased rollout, but they are not part of the
    durable target owner set and the Phase 4 branch state removes that shim.
  - The rollout must be additive and phased:
    1. extract route contract with behavior parity
    2. switch route projection to boundary-aware canonical geometry
    3. move glide computation to the non-UI runtime owner
    4. remove compatibility glue
- time base / determinism:
  - Final glide does not introduce a new time base.
  - Live mode consumes fused live samples published through
    `FlightDataRepository`.
  - Replay mode consumes replay-published fused samples through the same source
    gate and task/runtime seams.
  - No wall-clock `now()` calls, randomness, or live-only side effects are
    allowed in route projection or glide solving.
- concurrency / cadence:
  - Glide remains recomputable from published runtime flows.
  - New derived repositories may be introduced only as read-only orchestration
    owners; they must not become a second persisted SSOT.

## Alternatives Considered

| Option | Why Considered | Why Rejected / Not Chosen |
|---|---|---|
| Keep `GlideTargetRepository` + `FinalGlideUseCase` owned in `feature:map` | Lowest immediate plumbing cost | Preserves UI-adjacent ownership and keeps canonical route derivation outside task runtime owners |
| Add task-route or glide-result fields to `CompleteFlightData` | Looks convenient for map/cards because one model fans out widely | Mixes authorities, expands flight SSOT beyond flight data, and makes replay/task evolution harder |
| Read task state from `TaskRepository` or task UI projection models | Historically convenient for task UI work | `TaskRepository` is explicitly a task-sheet/UI projector, not the cross-feature runtime owner |
| Create a brand-new glide module | Could isolate the feature | Unnecessary churn because `feature:map-runtime` already provides the right non-UI runtime dependency surface |
| Big-bang rewrite of target, route, solver, cards, and UI adapters | One-shot cleanup | Too risky for a cross-feature runtime path that already has live/replay consumers |

## Consequences

### Benefits
- Flight-data and task-runtime authorities stay explicit.
- Canonical remaining-route geometry can become correct for cylinders, lines,
  sectors, and other observation-zone boundaries.
- Glide computation moves out of UI-adjacent orchestration without introducing a
  second persisted SSOT.
- Replay continues to use the same fused/runtime seams as live.

### Costs
- Migration is multi-phase and temporarily kept compatibility glue during
  rollout before the final cleanup step.
- Some tests will move from `feature:map` to `feature:tasks` /
  `feature:map-runtime` as ownership changes.
- The call graph becomes more explicit, which means more types and adapters.

### Risks
- Route projection could regress if boundary-aware geometry is switched before a
  parity contract exists.
- Cross-module API churn could spread if the move is attempted as one large
  rename instead of an additive sequence.
- Temporary compatibility glue could outlive its purpose if cleanup is not made
  an explicit final phase.

## Validation

- Phase-gated proof:
  - parity route-contract tests before changing solver wiring
  - boundary-aware route regression tests before consumer switch
  - repository/observer tests proving `MapScreenObservers` no longer owns glide
    solving
- Full local proof before merge:
  - `./gradlew enforceRules`
  - `./gradlew testDebugUnitTest`
  - `./gradlew assembleDebug`
- Suggested focused tests:
  - `:feature:tasks:testDebugUnitTest`
  - `:feature:map-runtime:testDebugUnitTest`
  - `:feature:map:testDebugUnitTest`
- Rollout / monitoring notes:
  - Keep root `AGENTS.md` short and durable.
  - Keep `PIPELINE.md` truthful about current mainline wiring and explicit about
    the approved migration target while rollout is in progress.

## Documentation Updates Required

- `AGENTS.md`:
  - keep guardrails short and durable
- `PIPELINE.md`:
  - document current mainline wiring versus the post-phase-4 branch boundary
- `CHANGE_PLAN_FINAL_GLIDE_ROUTE_AND_RUNTIME_MIGRATION_2026-03-25.md`:
  - track phased rollout, acceptance gates, and cleanup order
- `KNOWN_DEVIATIONS.md`:
  - no change required unless rollout leaves an approved temporary exception

## Rollback / Exit Strategy

- What can be reverted independently:
  - route-contract extraction
  - boundary-aware route switch
  - glide computation owner move
  - compatibility cleanup
- What would trigger rollback:
  - route correctness regressions
  - replay determinism regressions
  - architecture-sensitive dependency drift
- How this ADR is superseded or retired:
  - supersede it only if a later ADR deliberately changes the final-glide owner
    set or moves canonical route/glide outputs into another explicit,
    non-duplicated contract
