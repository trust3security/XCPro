# ADR_FLIGHT_RUNTIME_BOUNDARY_2026-03-15

## Metadata

- Title: Create `feature:flight-runtime` for reusable flight sensor and wind runtime foundations
- Date: 2026-03-15
- Status: Accepted
- Owner: Codex
- Reviewers: TBD
- Related issue/PR: TBD
- Related change plan:
  - `docs/refactor/Feature_Map_Right_Sizing_Release_Grade_Phased_IP_2026-03-15.md`
  - `docs/refactor/Feature_Map_Right_Sizing_Master_Plan_2026-03-15.md`
  - `docs/refactor/Feature_Map_Autonomous_Agent_Execution_Contract_2026-03-15.md`
- Supersedes:
  - none
- Superseded by:
  - none

## Context

- Problem:
  - `feature:map` still owned reusable non-map runtime foundations:
    sensor data contracts/models, flight-data SSOT/runtime mapping,
    flight-state gating, replay sensor foundations, and wind input/fusion
    owners.
  - Those owners were mixed with replay shell, live sensor owners, and map UI
    runtime code, which kept `feature:map` as a residual bucket.
  - The runtime foundations also depended on shared wind contracts that were
    split across `feature:map` and `feature:profile`.
- Why now:
  - Parent Phase 2B.1 exists to remove the clean reusable foundations before
    the harder fusion-engine and orientation cuts.
- Constraints:
  - Do not create a `feature:flight-runtime -> feature:map` back-edge.
  - Do not create direct `feature:flight-runtime -> feature:profile` or
    `feature:flight-runtime -> feature:variometer` dependencies.
  - Keep replay shell controllers and live sensor owners out of this phase.
  - Keep replay determinism and time-base behavior unchanged.
- Existing rule/doc references:
  - `AGENTS.md`
  - `docs/ARCHITECTURE/ARCHITECTURE.md`
  - `docs/ARCHITECTURE/CODING_RULES.md`
  - `docs/ARCHITECTURE/PIPELINE.md`
  - `docs/LEVO/levo.md`
  - `docs/LEVO/levo-replay.md`
  - `docs/ARCHITECTURE/ADR_HAWK_RUNTIME_OWNER_2026-03-15.md`

## Decision

Create `feature:flight-runtime` as the owner of reusable flight-runtime
foundations only. Move the pure runtime contracts/models and repositories that
do not require direct dependencies on `feature:profile` or
`feature:variometer`, and keep replay shell, live sensor owners, and
map-specific orientation control outside the module for later phases.

Required:
- ownership/boundary choice:
  - `feature:flight-runtime` owns:
    - raw sensor contracts/models:
      `SensorData.kt`, `SensorDataSource.kt`
    - flight-data foundations:
      `FlightDataRepository`, `FlightDisplayMapper`,
      `FlightMetricsModels.kt`
    - flying-state foundations:
      `FlightStateRepository`, `FlightStateSource`,
      `FlyingState.kt`, `FlyingStateDetector.kt`
    - replay foundations:
      `ReplaySensorSource`, `ReplayAirspeedRepository`,
      `ExternalAirspeedRepository`
    - wind foundations:
      `AirspeedDataSource`, `WindSensorInputs`, `WindSensorInputAdapter`,
      `WindSensorFusionRepository`, `WindSelectionUseCase`,
      `CirclingWind`, `WindMeasurementList`, `WindStore`,
      `WindInputs.kt`, `WindState.kt`, `CirclingDetector`
    - shared wind contracts/models:
      `WindOverrideSource`, `WindOverride`, `WindSource`, `WindVector`,
      `LiveSource`, `ReplaySource`
    - shared glider/audio/HAWK runtime contracts introduced in Parent
      Phase `2B.2A`:
      `StillAirSinkProvider`, `SpeedBoundsMs`, `VarioAudioSettings`,
      `VarioAudioControllerPort`, `VarioAudioControllerFactory`,
      `HawkAudioVarioReadPort`
  - `feature:map` keeps:
    - `UnifiedSensorManager` and live sensor/device owners
    - replay shell/controllers:
      `ReplayPipeline`, `ReplayPipelineFactory`,
      `IgcReplayController*`, `ReplaySampleEmitter`
    - map-specific runtime/UI consumers and DI composition
    - map-specific orientation control (`MapOrientationManager`)
  - `feature:profile` keeps wind override persistence only.
- dependency direction impact:
  - `app -> feature:flight-runtime` at the composition root because generated
    Hilt component sources import moved runtime owners directly
  - `feature:map -> feature:flight-runtime`
  - `feature:profile -> feature:flight-runtime`
  - `feature:flight-runtime` has no dependency on `feature:map`,
    `feature:profile`, or `feature:variometer`.
- API/module surface impact:
  - The moved packages remain stable (`com.example.xcpro.sensors`,
    `com.example.xcpro.flightdata`, `com.example.xcpro.weather.wind.*`,
    `com.example.xcpro.replay`) so consumer imports do not churn.
  - Cross-module public surface is limited to the moved runtime foundations and
    shared contracts; replay shell and live sensor APIs remain outside.
- time-base/determinism impact:
  - Live sensor cadence remains monotonic-based.
  - Replay sensor support remains driven by replay timestamps/IGC time.
  - No new wall-time comparisons or randomness are introduced.
- concurrency/buffering/cadence impact:
  - The moved repositories keep their existing dispatcher/scope ownership.
  - No second SSOT is introduced for flight data, flying state, or wind state.

## Alternatives Considered

| Option | Why Considered | Why Rejected / Not Chosen |
|---|---|---|
| Keep all runtime foundations in `feature:map` until the fusion-engine move | smallest immediate diff | keeps reusable non-map runtime ownership in the shell and delays the clean cut |
| Move the fusion engine and foundations together | would finish more code in one phase | still required direct dependencies on `feature:profile` and `feature:variometer` runtime collaborators |
| Put the foundations into `core:*` | would avoid a new feature module | these owners are feature/runtime-specific, not general-purpose core contracts |

## Consequences

### Benefits
- `feature:map` loses a real non-map runtime owner set.
- Shared wind contracts stop being split across `feature:map` and
  `feature:profile`.
- Parent Phase 2B.2 can now focus on runtime-port extraction and fusion-engine
  movement instead of foundation cleanup.
- Parent Phase `2B.2A` keeps the later fusion-engine move narrow by landing the
  shared glider/audio/HAWK contracts before the engine leaves `feature:map`.
- Parent Phase 2B.2 is explicitly split:
  - `2B.2A` extracts shared glider/audio/HAWK runtime contracts/models
  - `2B.2B` moves the remaining fusion engine after those ports land
- Parent Phase `2B.2B` is now landed:
  - `feature:flight-runtime` owns the fusion engine/runtime-only sensor
    pipeline set plus the pure `vario/**` calculators
  - `feature:map` keeps only live sensor/device owners, replay shell,
    DI composition, and shell-facing bridge tests for that seam
- Parent Phase `2C` is explicitly staged after the seam lock:
  - `2C.1` moves only pure orientation support owners that do not depend on
    live sensor owners, `MapFeatureFlags`, or `MapOrientationSettings`
  - `2C.1` is now landed:
    - `feature:flight-runtime` owns `HeadingResolver`, `OrientationClock`,
      `OrientationMath`, and the Hilt clock binding module
    - `feature:map` keeps `MapOrientationManager`, `OrientationEngine`,
      `HeadingJitterLogger`, and the live orientation data-source adapter path
  - `2C.2` can move the reusable orientation input adapter only after narrow
    ports exist for raw orientation sensor input and stationary-heading policy
  - `MapOrientationManager` and `OrientationEngine` remain outside the runtime
    module until their map/profile control dependencies are decoupled
  - `2C.2` is now landed:
    - `feature:flight-runtime` owns `OrientationSensorSource`,
      `OrientationDataSource`, `OrientationDataSourceFactory`, and the narrow
      orientation input/policy contracts
    - `feature:map` keeps only thin adapters over `UnifiedSensorManager` and
      the stationary-heading policy plus the map-specific
      `MapOrientationManager`, `OrientationEngine`, and
      `HeadingJitterLogger`

### Costs
- New module wiring and dependency edges are required.
- `app` now carries a direct composition-root dependency on
  `feature:flight-runtime` in addition to consumer feature dependencies.
- Some pure contract classes become public cross-module API because they are now
  consumed from `feature:map` and `feature:profile`.
- The later fusion-engine move now depends on a small contract-first subphase
  rather than one larger code move.
- A few runtime-domain types became public or partially public
  (`FlightCalculationHelpers`, `CalculateFlightMetricsUseCase`,
  `WindEstimator`) so cross-module replay and bridge tests can use the moved
  runtime owner without recreating duplicate test-only seams.

### Risks
- `feature:flight-runtime` could become a second residual bucket if later
  phases keep adding unrelated owners.
- Replay shell code could drift into the new module if Phase 2B.2 is not kept
  narrow.

## Validation

- Tests/evidence required:
  - `:feature:flight-runtime:testDebugUnitTest`
  - `:feature:flight-runtime:compileDebugKotlin`
  - standard AGENTS verification gates
- SLO or latency impact:
  - none expected; this phase preserves runtime cadence and replay timing
- Rollout/monitoring notes:
  - no staged rollout required; this is an internal ownership move

## Documentation Updates Required

- `ARCHITECTURE.md`:
  - no change required
- `CODING_RULES.md`:
  - no change required
- `PIPELINE.md`:
  - update runtime ownership paths for flight-data, flight-state, wind fusion,
    and replay foundations
- `CONTRIBUTING.md`:
  - no change required
- `KNOWN_DEVIATIONS.md`:
  - no change required

## Rollback / Exit Strategy

- What can be reverted independently:
  - the `feature:flight-runtime` module creation and the foundation file moves
- What would trigger rollback:
  - build instability, replay determinism regressions, or unexpected new module
    back-edges
- How this ADR is superseded or retired:
  - supersede it when Parent Phase 2B.2 and 2C finish the remaining runtime
    extraction and the final steady-state boundary is simpler
