# ADR_CURRENT_LD_PILOT_FUSED_METRIC_2026-04-08

## Metadata

- Title: Visible `ld_curr` becomes a fused pilot-facing Current L/D metric
- Date: 2026-04-08
- Status: Accepted
- Owner: Codex
- Reviewers: XCPro maintainers
- Related issue/PR: N/A
- Related change plan: `docs/ARCHITECTURE/CHANGE_PLAN_CURRENT_LD_FUSED_2026-04-08.md`
- Supersedes: None
- Superseded by: N/A

## Context

- Problem:
  - the visible `ld_curr` card was still a coarse raw over-ground glide metric.
  - pilots had to mentally interpret separate raw ground vs raw air-data glide
    metrics instead of getting one operational Current L/D number.
- Why now:
  - product explicitly decided that the visible Current L/D card must become
    one fused pilot-facing number.
- Constraints:
  - raw `currentLD/currentLDValid` must stay available and unchanged in
    meaning.
  - raw `currentLDAir/currentLDAirValid` must stay available and unchanged in
    meaning.
  - wind ownership must remain in the wind seam.
  - bugs/ballast/active polar ownership must remain in the active polar seam.
  - replay determinism must be preserved.
- Existing rule/doc references:
  - `AGENTS.md`
  - `docs/ARCHITECTURE/ARCHITECTURE.md`
  - `docs/ARCHITECTURE/CODING_RULES.md`
  - `docs/ARCHITECTURE/PIPELINE.md`

## Decision

The visible `ld_curr` card now reads a new fused upstream metric:

- `pilotCurrentLD`
- `pilotCurrentLDValid`
- `pilotCurrentLDSource`

The new authoritative owner is:

- `feature/map-runtime/src/main/java/com/trust3/xcpro/currentld/PilotCurrentLdRepository.kt`

This owner lives in `feature:map-runtime` because visible Current L/D depends
on:

- fused flight data from `FlightDataRepository`
- wind from `WindSensorFusionRepository`
- active target/course bearing from `WaypointNavigationRepository`
- active polar support from `StillAirSinkProvider`

`CompleteFlightData` stays flight-data only. The fused pilot metric is not
stored there. It is joined later into the map/runtime UI pipeline and exposed
through `RealTimeFlightData`.

The visible card ID remains stable:

- `ld_curr`

But its visible meaning changes from:

- raw over-ground measured glide ratio

to:

- current effective pilot-facing glide ratio, wind-aware when wind is usable
  and zero-wind otherwise

Time-base and cadence policy:

- matched rolling window owner: `PilotCurrentLdCalculator`
- window length: `20_000 ms`
- minimum publish fill: `8_000 ms`
- hold timeout: `20_000 ms`
- short TE-gap active-polar sink support: `3_000 ms`
- all window/hold decisions use replay-safe sample time, not wall time

Concurrency/buffering policy:

- repository-local rolling buffer only
- no UI-owned or card-owned state
- no hidden global mutable state

## Alternatives Considered

| Option | Why Considered | Why Rejected / Not Chosen |
|---|---|---|
| Keep visible `ld_curr` as raw `currentLD` | Backward compatible | Did not satisfy product goal of one useful pilot-facing Current L/D number |
| Mutate `FlightCalculationHelpers.calculateCurrentLD(...)` to become wind-aware | Smaller code diff | Violates seam ownership, mixes wind into the raw ground metric, and destroys diagnostic separation |
| Put fused logic in cards/UI | Fastest visible change | Violates MVVM/UDF/SSOT layering and replay-safe ownership |
| Own fused metric in `feature:flight-runtime` | Closer to raw metrics | That layer does not own task/course direction; would push cross-feature route semantics into flight-data SSOT |

## Consequences

### Benefits
- pilots get one operational Current L/D number on the visible card
- wind can help the visible card without making wind a hard dependency
- raw ground and raw air metrics remain available for diagnostics and advanced use
- active polar remains authoritative for setup-dependent support logic

### Costs
- visible `ld_curr` meaning is now different from historical raw `currentLD`
- map/runtime pipeline gained a new repository and DTO join
- additional rolling-window and hold-state tests are required

### Risks
- overly strict gating can reduce data availability if inputs are noisy
- future work must avoid accidentally treating `pilotCurrentLD` as the raw
  ground metric in diagnostics or replay tools

## Validation

- Tests/evidence required:
  - rolling-window math
  - zero-wind fallback
  - wind projection effect
  - circling/turning/climbing hold
  - TE-gap polar support
  - raw metric isolation
  - card rewiring proof
- SLO or latency impact:
  - repository-local rolling math only; no new UI-thread hot-path ownership
- Rollout/monitoring notes:
  - keep `pilotCurrentLDSource` for tests/debugging

## Documentation Updates Required

- `ARCHITECTURE.md`: No change required
- `CODING_RULES.md`: No change required
- `PIPELINE.md`: Updated
- `CONTRIBUTING.md`: No change required
- `KNOWN_DEVIATIONS.md`: No change required

## Rollback / Exit Strategy

- What can be reverted independently:
  - visible `CardFormatSpec` rewiring of `ld_curr`
  - map-runtime fused repository wiring
- What would trigger rollback:
  - replay non-determinism
  - widespread no-data behavior in normal glide conditions
  - architecture drift into raw wind/polar helper mutation
- How this ADR is superseded or retired:
  - by a later ADR if XCPro deliberately redefines Current L/D again
