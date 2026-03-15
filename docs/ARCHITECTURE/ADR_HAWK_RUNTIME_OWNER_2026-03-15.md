# ADR_HAWK_RUNTIME_OWNER_2026-03-15

## Metadata

- Title: Move the live HAWK runtime owner to feature:variometer
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
  - Parent Phase 2 needed a future `feature:flight-runtime` module for
    reusable sensor/fusion/wind owners, but `FlightDataCalculatorEngine` still
    depended on the HAWK runtime path while that runtime lived in
    `feature:map`.
  - A direct flight-runtime move would therefore create a
    `feature:flight-runtime -> feature:map` back-edge through
    `HawkVarioRepository`.
  - The HAWK settings move had already made the preview contract
    variometer-owned, so leaving the live runtime in `feature:map` would keep
    the HAWK ownership story split across modules.
- Why now:
  - Parent Phase 2A exists only to remove this blocker before the
    flight-runtime module is created.
- Constraints:
  - Keep one live HAWK runtime owner.
  - Keep replay/live cadence unchanged.
  - Avoid widening scope into the full flight-runtime extraction.
  - Do not move upstream sensor/source SSOT owners yet.
- Existing rule/doc references:
  - `AGENTS.md`
  - `docs/ARCHITECTURE/ARCHITECTURE.md`
  - `docs/ARCHITECTURE/CODING_RULES.md`
  - `docs/ARCHITECTURE/PIPELINE.md`
  - `docs/ARCHITECTURE/ADR_HAWK_VARIO_PREVIEW_READ_PORT_2026-03-15.md`

## Decision

The live HAWK runtime owner moves to `feature:variometer`. `feature:map`
keeps only temporary adapters from the current sensor/source owners into
variometer-owned runtime ports until Parent Phase 2B lands.

Required:
- ownership/boundary choice:
  - `feature:variometer` owns `HawkVarioUseCase`, `HawkVarioRepository`,
    `HawkConfigRepository`, `HawkVarioEngine`, `HawkConfig`, `HawkOutput`,
    `AdaptiveAccelTrust`, `BaroQc`, `RollingVarianceWindow`, and the new
    `HawkSensorStreamPort` / `HawkActiveSourcePort` runtime interfaces.
  - `feature:map` owns only `MapHawkSensorStreamAdapter`,
    `MapHawkActiveSourceAdapter`, and their DI binding module.
  - The preview port remains variometer-owned and is implemented by the same
    variometer-owned `HawkVarioUseCase`.
- dependency direction impact:
  - `feature:map` depends on variometer-owned interfaces; `feature:variometer`
    does not depend on `feature:map`.
  - Parent Phase 2B can now create `feature:flight-runtime` without a HAWK
    back-edge into `feature:map`.
- API/module surface impact:
  - New public runtime interfaces are limited to `HawkSensorStreamPort`,
    `HawkActiveSourcePort`, `HawkBaroSample`, `HawkAccelSample`, and
    `HawkRuntimeSource`.
  - Package names stay stable in this phase to avoid route/import churn.
- time-base/determinism impact:
  - HAWK cadence remains driven by the same monotonic-clock runtime path.
  - No new wall-clock or randomness is introduced.
- concurrency/buffering/cadence impact:
  - No second HAWK owner is introduced.
  - The existing runtime cadence stays in `HawkVarioUseCase`; adapters are
    transform-only and do not add owner-scoped loops.

## Alternatives Considered

| Option | Why Considered | Why Rejected / Not Chosen |
|---|---|---|
| Leave HAWK runtime in `feature:map` until the full flight-runtime move | smallest immediate change | blocks Parent Phase 2B with a map back-edge |
| Move HAWK runtime into the future `feature:flight-runtime` directly | looks aligned with runtime extraction | widens the slice and entangles HAWK with unresolved mixed owners in `sensors/**`, `flightdata/**`, and `orientation/**` |
| Duplicate HAWK runtime ownership temporarily | would simplify some dependencies locally | violates SSOT and cadence-owner rules |

## Consequences

### Benefits
- The live HAWK runtime owner is singular and no longer map-owned.
- Parent Phase 2B is unblocked from a dependency-direction standpoint.
- The preview-port boundary and runtime-owner boundary now live in the same
  module.

### Costs
- Temporary map-backed adapter files and DI bindings exist until Parent Phase
  2B removes them.
- HAWK runtime tests move out of `feature:map` and must be kept green in
  `feature:variometer`.

### Risks
- The temporary adapters could become long-lived by accident if Parent Phase 2B
  stalls.
- Future edits could drift HAWK runtime files back into `feature:map` unless CI
  guards remain active.

## Validation

- Tests/evidence required:
  - `feature/variometer/src/test/java/com/example/xcpro/hawk/HawkVarioEngineTest.kt`
  - `feature/variometer/src/test/java/com/example/xcpro/hawk/HawkVarioRepositoryTest.kt`
  - `feature:map:compileDebugKotlin`
  - standard AGENTS verification gates
- SLO or latency impact:
  - none expected; the runtime cadence path is unchanged
- Rollout/monitoring notes:
  - no staged rollout required; this is an internal ownership move

## Documentation Updates Required

- `ARCHITECTURE.md`:
  - no change required
- `CODING_RULES.md`:
  - no change required
- `PIPELINE.md`:
  - update HAWK runtime ownership and the temporary map adapter note
- `CONTRIBUTING.md`:
  - no change required
- `KNOWN_DEVIATIONS.md`:
  - no change required

## Rollback / Exit Strategy

- What can be reverted independently:
  - the variometer-owned HAWK runtime move
  - the temporary map-backed adapter bindings
- What would trigger rollback:
  - HAWK runtime regressions or unexpected replay/live cadence changes
- How this ADR is superseded or retired:
  - supersede it when Parent Phase 2B removes the temporary map adapters and
    the final upstream sensor/source ports are in place
