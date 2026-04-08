# ADR_TERRAIN_READ_PORT_OWNER_2026-03-16

## Metadata

- Title: Shared terrain read port lives in `dfcards-library`, with a canonical terrain repository in `feature:map` and replay-safe live AGL wiring
- Date: 2026-03-16
- Status: Superseded
- Owner: Codex
- Reviewers: TBD
- Related issue/PR: RULES-20260316-18
- Related change plan:
  - `docs/refactor/Terrain_Elevation_Ownership_Release_Grade_Phased_IP_2026-03-16.md`
- Related ADR:
  - none
- Supersedes:
  - none
- Superseded by:
  - `docs/ARCHITECTURE/ADR_CORE_FLIGHT_SHARED_RUNTIME_OWNER_2026-04-06.md`

## Context

- Problem:
  - `SimpleAglCalculator` lived in `dfcards-library` but directly constructed `OpenMeteoElevationApi`,
    which made the calculator own Android `Context`, network adapter construction, and part of the terrain seam.
  - Live AGL wiring was spread across `SensorFusionRepositoryFactory`, `FlightDataCalculator`, and
    `FlightDataCalculatorEngine`, so changing only the calculator constructor would not fix the real owner path.
  - QNH still consumes a separate terrain provider seam and does not yet share the AGL contract.
- Why now:
  - Phase 1 of the terrain/elevation ownership plan required a narrow, replay-safe first cut that removes
    Android ownership from the calculator without forcing QNH migration too early.
- Constraints:
  - Keep replay deterministic; replay must not start online terrain fetches through this change.
  - Do not invent a new module for Phase 1.
  - Do not migrate QNH in the same slice.
- Existing rule/doc references:
  - `AGENTS.md`
  - `docs/ARCHITECTURE/ARCHITECTURE.md`
  - `docs/ARCHITECTURE/CODING_RULES.md`
  - `docs/ARCHITECTURE/PIPELINE.md`
  - `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`

## Decision

The terrain lane now uses one shared read contract in `dfcards-library`, and live AGL consumes that
contract through the existing factory -> wrapper -> engine seam bound to a canonical repository in
`feature:map`.

Required:
- ownership/boundary choice:
  - `dfcards-library` owns `TerrainElevationReadPort` because `SimpleAglCalculator` already lives there
    and both `feature:flight-runtime` and `feature:map` already depend on that lower owner.
  - `SimpleAglCalculator` is the AGL calculation owner only; cache/retry/backoff and terrain-management
    helpers moved out of the calculator, and it no longer constructs Android/network adapters directly.
  - `feature:map` owns the canonical live implementation:
    `TerrainElevationRepository`, `SrtmTerrainDataSource`, `OpenMeteoTerrainDataSource`,
    `TerrainElevationResultCache`, and the DI binding for the shared read port.
  - `feature:flight-runtime` owns the live AGL construction seam through
    `SensorFusionRepositoryFactory` -> `FlightDataCalculator` -> `FlightDataCalculatorEngine`.
  - `feature:map` QNH now consumes the same shared read contract directly:
    `CalibrateQnhUseCase` depends on `TerrainElevationReadPort`, and `QnhModule` no longer owns a
    QNH-specific terrain binding.
- dependency direction impact:
  - no new upward dependency from `dfcards-library` is introduced.
  - `feature:flight-runtime` depends only on the shared read port, not on a concrete terrain implementation.
- API/module surface impact:
  - new cross-module API surface is `TerrainElevationReadPort`.
  - `TerrainElevationDataSource` remains map-owned internal app-side data wiring, not a second shared port.
  - no new shared write/state surface is introduced.
- time-base/determinism impact:
  - replay keeps the existing no-online-terrain gate in the metrics request path.
  - the repository does not own replay decisions; replay policy remains outside the terrain port/repository seam.
- concurrency/buffering/cadence impact:
  - no hidden runtime owner or standalone background scope is introduced in the terrain data implementation.
  - AGL cadence remains owned by the existing fusion/runtime flow.

## Alternatives Considered

| Option | Why Considered | Why Rejected / Not Chosen |
|---|---|---|
| Put the shared port in `feature:flight-runtime` | looks closer to the AGL consumer | `SimpleAglCalculator` already lives in `dfcards-library`, so that would invert the graph or force a premature move |
| Reuse `SrtmTerrainElevationProvider` as the shared implementation | smallest code change | it would centralize the wrong owner because that seam still belongs to the old QNH-only path |
| Migrate QNH in Phase 1 too | would converge consumers faster | widens the slice, lacks dedicated QNH terrain tests, and risks hiding replay-safety regressions |

## Consequences

### Benefits
- `SimpleAglCalculator` is now calculation-focused instead of owning Android/network/cache terrain work.
- Live AGL now reaches one canonical repository with explicit source policy, cache lifecycle, retry/backoff,
  and `AppLogger`-gated diagnostics.
- The live AGL constructor seam is explicit and testable without inventing a new module or widening into QNH migration.

### Costs
- `feature:map` still owns the canonical terrain repository implementation and QNH consumer wiring,
  so future terrain work still needs module-boundary discipline.

### Risks
- Future changes could reintroduce a QNH-specific terrain seam instead of reusing `TerrainElevationReadPort`.
- Future changes could wrongly route replay policy into the terrain port unless the current guard remains explicit.

## Validation

- Tests/evidence required:
  - `dfcards-library/src/test/java/com/example/dfcards/dfcards/calculations/SimpleAglCalculatorTest.kt`
  - `feature/flight-runtime/src/test/java/com/example/xcpro/sensors/FlightCalculationHelpersTest.kt`
  - `feature/flight-runtime/src/test/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCaseQnhReplayTestRuntime.kt`
  - `feature/map/src/test/java/com/example/xcpro/qnh/CalibrateQnhUseCaseTest.kt`
  - `feature/map/src/test/java/com/example/xcpro/terrain/TerrainElevationRepositoryTest.kt`
  - standard AGENTS gates
- SLO or latency impact:
  - none expected in Phase 1; this is a constructor seam and boundary cleanup
- Rollout/monitoring notes:
  - no staged rollout required; Phase 1 is internal architecture work

## Documentation Updates Required

- `ARCHITECTURE.md`:
  - no change required
- `CODING_RULES.md`:
  - no change required
- `PIPELINE.md`:
  - update the fusion section to document the shared terrain read port, canonical repository owner, and replay guard location
- `CONTRIBUTING.md`:
  - no change required
- `KNOWN_DEVIATIONS.md`:
  - resolve the terrain deviation once QNH also consumes the shared read port and replay proof remains intact

## Rollback / Exit Strategy

- What can be reverted independently:
  - the new shared read port and its DI binding
  - the `SimpleAglCalculator` constructor change
  - the flight-runtime constructor rewiring
  - the repository/data-source binding if the live terrain policy proves unsafe
- What would trigger rollback:
  - AGL regressions or replay determinism regressions tied to the new seam
- How this ADR is superseded or retired:
  - keep this ADR as the durable record of the shared terrain seam unless a later terrain module extraction or policy rewrite replaces it
