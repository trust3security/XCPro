# ADR_MAP_RUNTIME_TRAIL_OWNER_2026-03-16.md

## Metadata

- Title: Move snail-trail runtime ownership to `feature:map-runtime`
- Date: 2026-03-16
- Status: Accepted
- Owner: Codex
- Reviewers: TBD
- Related issue/PR: TBD
- Related change plan:
  - `docs/refactor/Feature_Map_Right_Sizing_Release_Grade_Phased_IP_2026-03-15.md`
  - `docs/refactor/Feature_Map_Autonomous_Agent_Execution_Contract_2026-03-15.md`
- Supersedes:
- Superseded by:

## Context

- Problem:
  - `feature:map` still owned the snail-trail render/runtime set even after
    the first Parent Phase 3 visual/runtime primitive move.
  - That left runtime render logic, trail time-base handling, and trail
    overlay lifecycle in the shell module.
- Why now:
  - Parent Phase 3 is the map-runtime burn-down lane.
  - A focused seam pass showed `MapInitializer` remains shell-owned for now,
    but the snail-trail cluster is a coherent runtime owner set.
- Constraints:
  - `feature:map-runtime` must not depend back on `feature:map`.
  - `feature:map` must keep shell-held MapLibre handles and effect wiring.
  - Trail time-base behavior must stay unchanged across live and replay.
- Existing rule/doc references:
  - `AGENTS.md`
  - `docs/ARCHITECTURE/ARCHITECTURE.md`
  - `docs/ARCHITECTURE/CODING_RULES.md`
  - `docs/ARCHITECTURE/PIPELINE.md`

## Decision

The snail-trail runtime set belongs to `feature:map-runtime`.

Required boundary choice:
- `feature:map-runtime` owns:
  - `SnailTrailManager`
  - `SnailTrailOverlay`
  - trail render helpers and trail domain/runtime files under
    `com.example.xcpro.map.trail/**`
  - `TrailProcessor`, `TrailUpdateInput`, `TrailUpdateResult`,
    `TrailRenderState`, and `TrailTimeBase`
  - `SnailTrailRuntimeState` as the narrow shell/runtime bridge
- `feature:map` owns:
  - `MapScreenState` as the shell-held implementation of
    `SnailTrailRuntimeState`
  - shell effect wiring and composition sites that call the moved runtime set

Dependency direction impact:
- `feature:map` depends on `feature:map-runtime` for the trail runtime owners.
- `feature:map-runtime` depends on `feature:flight-runtime` for
  `CompleteFlightData` and live-wind policy inputs consumed by `TrailProcessor`.
- No `feature:map-runtime -> feature:map` back-edge is allowed; shell-held
  state is exposed only through `SnailTrailRuntimeState`.

API/module surface impact:
- The trail runtime contract is now explicit across modules:
  - `SnailTrailRuntimeState`
  - `TrailProcessor`
  - `TrailUpdateInput`
  - `TrailUpdateResult`
  - `TrailRenderState`
  - `TrailTimeBase`
- Owner tests for the moved trail runtime set move to `feature:map-runtime`;
  shell tests that only mock `SnailTrailManager` stay in `feature:map`.

Time-base/determinism impact:
- None by design. `TrailProcessor` keeps the existing live monotonic / live
  wall / replay IGC time-base handling unchanged.

Concurrency/buffering/cadence impact:
- None by design. The move does not add a new cadence gate or buffering layer;
  `MapScreenObservers` and `MapScreenRuntimeEffects` still drive the same trail
  update cadence and display-pose callbacks.

## Alternatives Considered

| Option | Why Considered | Why Rejected / Not Chosen |
|---|---|---|
| Keep the trail runtime set in `feature:map` until `MapInitializer` is split | smallest code diff | leaves a large runtime owner set in the shell and blocks Phase 3 progress on a clean seam |
| Move only `SnailTrailOverlay` and leave `TrailProcessor` / result models in `feature:map` | looks like a smaller surface | creates a split runtime owner set and a `feature:map-runtime -> feature:map` back-edge through trail contracts |
| Move `MapInitializer` first | visible hotspot | seam pass showed it still mixes bootstrap/data-loader shell responsibilities and is not the next clean owner move |

## Consequences

### Benefits
- `feature:map` loses a large, coherent runtime render cluster.
- Trail lifecycle and time-base behavior now live with other map runtime owners.
- The shell/runtime boundary is explicit through `SnailTrailRuntimeState`.

### Costs
- `feature:map-runtime` gains more public trail contract surface.
- `feature:map-runtime` now depends on `feature:flight-runtime` for trail
  runtime inputs.

### Risks
- The trail bridge port could regrow if future changes push shell concerns back
  into runtime.

## Validation

- Tests/evidence required:
  - `./gradlew enforceRules`
  - `./gradlew testDebugUnitTest`
  - `./gradlew assembleDebug`
- SLO or latency impact:
  - none expected; trail cadence and render-path behavior are unchanged
- Rollout/monitoring notes:
  - continue Parent Phase 3 seam locks after this landed runtime slice

## Documentation Updates Required

- `ARCHITECTURE.md`:
  - none
- `CODING_RULES.md`:
  - none
- `PIPELINE.md`:
  - update trail runtime ownership and bridge bullets
- `CONTRIBUTING.md`:
  - none
- `KNOWN_DEVIATIONS.md`:
  - none

## Rollback / Exit Strategy

- What can be reverted independently:
  - this trail runtime owner move only
- What would trigger rollback:
  - build/test regression or a discovered `feature:map-runtime -> feature:map`
    back-edge through trail contracts
- How this ADR is superseded or retired:
  - superseded only if a later module boundary replaces the current
    `feature:map` / `feature:map-runtime` split
