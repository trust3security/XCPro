# ADR_RUNTIME_SCOPE_OWNERSHIP_2026-04-06

## Metadata

- Title: Long-lived runtime owners receive named owner scopes instead of creating hidden scopes
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
  - several long-lived runtime owners were creating `CoroutineScope(...)`
    internally or inside DI providers.
  - that made lifetime ownership implicit in `TaskManagerCoordinator`,
    sensor-fusion wiring, livefollow runtime repositories, and
    `XcAccountRepository`.
  - `MapOrientationManager` also exposed a public convenience constructor path
    that silently self-owned a long-lived main-thread scope.
- Why now:
  - Phase 2 of the active architecture hardening plan standardizes runtime
    lifetime ownership before deeper module and ViewModel refactors.
- Constraints:
  - keep runtime behavior unchanged.
  - avoid a repo-wide scope-framework rewrite.
  - keep replay and task determinism unchanged.
- Existing rule/doc references:
  - `AGENTS.md`
  - `docs/ARCHITECTURE/ARCHITECTURE.md`
  - `docs/ARCHITECTURE/CODING_RULES.md`
  - `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`
  - `docs/refactor/XCPro_Architecture_Hardening_Release_Grade_Phased_IP_2026-04-06.md`

## Decision

Long-lived runtime owners must receive an explicit named owner scope from DI or
their composition root instead of creating hidden scopes locally.

Required:
- ownership/boundary choice:
  - `app` owns the task coordinator runtime scope via `@TaskCoordinatorScope`.
  - the map sensor runtime owns the shared sensor scope via
    `@SensorRuntimeScope`, consumed by `UnifiedSensorManager` and
    `SensorFusionRepository`.
  - the livefollow data lane owns a shared `@LiveFollowRuntimeScope`.
  - the account SSOT owns a separate `@XcAccountScope`.
  - `MapOrientationManager` requires caller-provided scope ownership.
- dependency direction impact:
  - ownership stays at the composition layer; runtime classes no longer invent
    their own owner scope.
  - feature modules consume qualified scopes but do not depend upward on app
    implementation details.
- API/module surface impact:
  - `TaskManagerCoordinator` constructor now requires `coordinatorScope`.
  - `UnifiedSensorManager` constructor now requires `scope`.
  - `XcAccountRepository` constructor now requires `scope`.
  - `MapOrientationManager` no longer exposes a self-owned default scope path.
- time-base/determinism impact:
  - none; only scope ownership changes.
  - task persistence, replay, and live data flows keep the same time sources.
- concurrency/buffering/cadence impact:
  - no intended change to launch cadence or buffering.
  - sharing named supervisor scopes across a runtime lane keeps sibling failure
    isolation while making ownership explicit.

## Alternatives Considered

| Option | Why Considered | Why Rejected / Not Chosen |
|---|---|---|
| Leave current inline `CoroutineScope(...)` creation in place | smallest diff | preserves hidden runtime ownership and provider-level lifetime drift |
| Introduce one repo-wide global application scope for everything | simple | collapses unrelated runtime owners into one bucket and weakens reviewability |
| Add explicit owner scopes only to some classes and keep provider-local helpers elsewhere | narrowest immediate churn | leaves the same hidden-scope pattern active in adjacent runtime owners |

## Consequences

### Benefits
- Long-lived runtime owners now advertise who owns their lifetime.
- DI construction sites are reviewable and grepable instead of hiding scope
  policy inside constructors or ad hoc helpers.
- The map and livefollow lanes now use stable named owner scopes.

### Costs
- A few constructors and tests need explicit scope parameters.
- The repo gains several runtime-scope qualifiers.

### Risks
- A named scope could become too broad if unrelated owners are added to it
  without review.
- Some remaining self-owned scopes still exist outside this Phase 2 slice and
  must be handled separately.

## Validation

- Tests/evidence required:
  - `./gradlew enforceRules`
  - targeted compile and unit tests for `app`, `feature:tasks`, `feature:map`,
    and `feature:livefollow`
- SLO or latency impact:
  - none expected
- Rollout/monitoring notes:
  - no staged rollout required; this is lifetime ownership hardening only

## Documentation Updates Required

- `ARCHITECTURE.md`:
  - no change required
- `CODING_RULES.md`:
  - no change required
- `PIPELINE.md`:
  - no change required
- `CONTRIBUTING.md`:
  - no change required
- `KNOWN_DEVIATIONS.md`:
  - no change required

## Rollback / Exit Strategy

- What can be reverted independently:
  - each qualifier/provider pair
  - constructor parameter additions for the touched runtime owners
- What would trigger rollback:
  - unexpected startup/runtime regressions caused by owner-scope rewiring
- How this ADR is superseded or retired:
  - supersede it if the repo later standardizes on a broader runtime-host model
    with explicit shutdown hooks across all long-lived owners
