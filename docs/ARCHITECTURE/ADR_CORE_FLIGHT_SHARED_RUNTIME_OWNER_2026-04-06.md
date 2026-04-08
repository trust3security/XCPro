# ADR_CORE_FLIGHT_SHARED_RUNTIME_OWNER_2026-04-06

## Metadata

- Title: Move shared flight runtime contracts and math into `:core:flight`
- Date: 2026-04-06
- Status: Accepted
- Owner: Codex
- Reviewers: TBD
- Related issue/PR: TBD
- Related change plan:
  - `docs/refactor/XCPro_Architecture_Hardening_Release_Grade_Phased_IP_2026-04-06.md`
- Supersedes:
  - `docs/ARCHITECTURE/ADR_TERRAIN_READ_PORT_OWNER_2026-03-16.md`
- Superseded by:
  - none

## Context

- Problem:
  - `dfcards-library` owned multiple owner-neutral flight runtime contracts and
    pure math helpers that were consumed by non-UI modules.
  - `feature:flight-runtime` depended on `:dfcards-library` only to reach
    shared runtime types such as `RealTimeFlightData`,
    `TerrainElevationReadPort`, `SimpleAglCalculator`,
    `BarometricAltitudeCalculator`, and the vario/filter math set.
  - That created a runtime-to-UI ownership inversion and made later module
    cleanup mostly cosmetic.
  - `feature:map-runtime` also consumed runtime-facing data that should not be
    owned by card UI types.
- Why now:
  - This was the highest-leverage architecture leak in the current seam plan.
  - Later runtime tightening work could not be credible while shared runtime
    contracts still lived under a card/UI module.
- Constraints:
  - Keep replay determinism and existing math behavior unchanged.
  - Keep `FlightModeSelection` card-owned because it carries card/UI concerns.
  - Avoid package churn beyond what is required to establish the correct owner.
- Existing rule/doc references:
  - `AGENTS.md`
  - `docs/ARCHITECTURE/ARCHITECTURE.md`
  - `docs/ARCHITECTURE/CODING_RULES.md`
  - `docs/ARCHITECTURE/PIPELINE.md`
  - `docs/ARCHITECTURE/ADR_FLIGHT_RUNTIME_BOUNDARY_2026-03-15.md`

## Decision

Create `:core:flight` as the owner of shared, non-UI flight runtime contracts
and pure flight math used across multiple modules.

Required:
- ownership/boundary choice:
  - `:core:flight` now owns shared runtime contracts and pure math including:
    - `RealTimeFlightData`
    - `TerrainElevationReadPort`
    - `SimpleAglCalculator`
    - `BarometricAltitudeCalculator`
    - `BarometricAltitudeData`
    - `ConfidenceLevel`
    - the shared vario/filter support set:
      `AdaptiveNoiseTools`, `AdaptiveVarioConfig`,
      `VarioFilterDiagnostics`, `Modern3StateKalmanFilter`,
      `ComplementaryVarioFilter`, `KalmanFilter`,
      `AdvancedBarometricFilter`, and their result/value types
  - `dfcards-library` keeps card/UI-owned contracts and rendering concerns such
    as `FlightModeSelection`, card formatting, templates, and card state.
  - `feature:map-runtime` keeps runtime-owned map render contracts such as
    `ReplayLocationFrame`; it does not own the shared flight DTO surface.
- dependency direction impact:
  - `feature:flight-runtime -> :core:flight`
  - `dfcards-library -> :core:flight`
  - `feature:map -> :core:flight`
  - `feature:map-runtime -> :core:flight`
  - `feature:livefollow -> :core:flight`
  - `feature:flight-runtime` no longer depends on `:dfcards-library`.
- API/module surface impact:
  - shared runtime contracts are explicitly public from `:core:flight` rather
    than leaking from a UI/card module.
  - `RealTimeFlightData` remains the card-facing projection model, but its
    owner is now runtime-neutral.
  - `dfcards-library` becomes a consumer of the shared runtime surface instead
    of the owner.
- time-base/determinism impact:
  - no new wall-time reads or randomness are introduced.
  - replay timestamps continue to flow through `RealTimeFlightData` unchanged.
- concurrency/buffering/cadence impact:
  - this move does not add new long-lived owners or change runtime cadence.

## Alternatives Considered

| Option | Why Considered | Why Rejected / Not Chosen |
|---|---|---|
| Keep the shared contracts in `dfcards-library` and only narrow imports | smallest code churn | preserves the wrong ownership and keeps `feature:flight-runtime -> :dfcards-library` alive |
| Move the shared set into `feature:flight-runtime` | removes the UI dependency | would make card/UI consumers depend on a feature runtime owner for reusable DTOs and pure math |
| Split multiple tiny modules for DTOs, terrain, and filters | could minimize surface area | too much module churn for the current seam; `:core:flight` is the smallest credible neutral owner |

## Consequences

### Benefits
- Removes the worst runtime-to-UI dependency inversion in the repo.
- Makes the flight runtime boundary and terrain seam real instead of incidental.
- Gives `feature:map-runtime` and other consumers a neutral shared flight owner.
- Leaves `dfcards-library` focused on card/UI responsibilities.

### Costs
- Adds one more core module to maintain.
- Some existing imports changed to the new owner package.
- The earlier terrain-port ADR is no longer the steady-state owner record.

### Risks
- `:core:flight` could become a bucket if future work keeps dropping unrelated
  runtime owners into it.
- Future card-specific contracts could drift back into `:core:flight` unless
  review keeps the owner-neutral rule strict.

## Validation

- Tests/evidence required:
  - `./gradlew enforceRules`
  - `./gradlew testDebugUnitTest`
  - `./gradlew assembleDebug`
  - targeted compile/tests for `:core:flight`, `:feature:flight-runtime`,
    `:feature:map`, and `:feature:map-runtime`
- SLO or latency impact:
  - none expected; this is an ownership move with behavior parity
- Rollout/monitoring notes:
  - no staged rollout required

## Documentation Updates Required

- `ARCHITECTURE.md`:
  - no change required
- `CODING_RULES.md`:
  - no change required
- `PIPELINE.md`:
  - document `:core:flight` as the shared owner for `RealTimeFlightData`,
    terrain read contracts, and shared flight math
- `CONTRIBUTING.md`:
  - no change required
- `KNOWN_DEVIATIONS.md`:
  - no change required

## Rollback / Exit Strategy

- What can be reverted independently:
  - the new `:core:flight` module and the moved file set
  - the consumer dependency rewires
- What would trigger rollback:
  - compile instability, replay determinism regression, or a discovered new
    dependency inversion
- How this ADR is superseded or retired:
  - supersede it only if a later, narrower shared owner replaces `:core:flight`
    without reintroducing runtime-to-UI coupling
