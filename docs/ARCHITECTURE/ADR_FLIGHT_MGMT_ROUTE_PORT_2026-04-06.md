# ADR_FLIGHT_MGMT_ROUTE_PORT_2026-04-06

## Metadata

- Title: Flight management route consumes a narrow map-owned port, not `FlightDataManager`
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
  - `app` route composition was reaching through `MapScreenViewModel.runtimeDependencies`
    to pass the concrete `FlightDataManager` into `FlightMgmt`.
  - That made the app shell depend on a map-runtime collaborator through a
    ViewModel escape hatch and left `MapScreenRuntimeDependencies` effectively
    public across the module boundary.
- Why now:
  - Phase 1 of the active architecture hardening plan removes the highest-value
    boundary leak first without destabilizing internal map-shell composition.
- Constraints:
  - Keep current flight-management behavior unchanged.
  - Do not widen the route API beyond what `FlightMgmt` actually needs.
  - Keep grouped runtime wiring inside the map shell for now; this slice is not
    the full `MapScreenViewModel` split.
- Existing rule/doc references:
  - `AGENTS.md`
  - `docs/ARCHITECTURE/ARCHITECTURE.md`
  - `docs/ARCHITECTURE/CODING_RULES.md`
  - `docs/ARCHITECTURE/PIPELINE.md`
  - `docs/refactor/XCPro_Architecture_Hardening_Release_Grade_Phased_IP_2026-04-06.md`

## Decision

The flight-management route now consumes a narrow map-owned `FlightDataMgmtPort`
instead of the concrete `FlightDataManager`.

Required:
- ownership/boundary choice:
  - `feature:map` owns the route contract `FlightDataMgmtPort`.
  - `MapScreenViewModel` exposes the narrow route contract as a public screen
    seam.
  - `MapScreenRuntimeDependencies` remains an internal map-shell grouping only.
  - `FlightMgmt` consumes the narrow port and no longer takes `FlightDataManager`
    directly.
- dependency direction impact:
  - dependency direction remains `app -> feature:map`.
  - `app` no longer reaches through a ViewModel into map-runtime collaborators.
  - grouped runtime collaborators remain internal to the map shell.
- API/module surface impact:
  - new public route contract:
    - `FlightDataMgmtPort.liveFlightDataFlow`
    - `FlightDataMgmtPort.bindCards(...)`
  - `MapScreenRuntimeDependencies` is no longer part of the cross-module route
    surface.
- time-base/determinism impact:
  - none; `FlightDataManager` remains the underlying live-preview owner for this
    route and no replay or time-source behavior changes.
- concurrency/buffering/cadence impact:
  - none; the route still consumes the existing `liveFlightDataFlow`, and card
    hydration remains owned by the existing `CardIngestionCoordinator`.

## Alternatives Considered

| Option | Why Considered | Why Rejected / Not Chosen |
|---|---|---|
| Keep passing `FlightDataManager` from `AppNavGraph` | smallest code diff | preserves the wrong boundary and keeps the runtime bundle effectively public |
| Expose all of `MapScreenRuntimeDependencies` as the route contract | already existed | remains a service-locator-style bundle rather than a route-specific seam |
| Move `FlightMgmt` fully under the map shell in the same slice | would further reduce route wiring | too wide for Phase 1 and mixes seam hardening with broader owner moves |

## Consequences

### Benefits
- `app` no longer depends on a concrete runtime/controller handle through
  `MapScreenViewModel`.
- `MapScreenRuntimeDependencies` can be kept internal to the map shell.
- The flight-management route now has an explicit, reviewable contract.

### Costs
- `MapScreenViewModel` exposes one additional narrow public route port.
- The map module gains one small route-contract type.

### Risks
- Future route needs could try to grow `FlightDataMgmtPort` into a second
  runtime bundle if not reviewed tightly.
- Internal map-shell grouped wiring could be mistaken for acceptable future
  public API unless Phase 1 boundaries remain enforced.

## Validation

- Tests/evidence required:
  - targeted `FlightMgmt` compile/path verification
  - `./gradlew enforceRules`
  - touched-module compile and relevant unit tests
- SLO or latency impact:
  - none expected
- Rollout/monitoring notes:
  - no staged rollout required; this is an internal route boundary hardening
    change

## Documentation Updates Required

- `ARCHITECTURE.md`:
  - no change required
- `CODING_RULES.md`:
  - no change required
- `PIPELINE.md`:
  - update the card preview / flight-management route note to mention
    `FlightDataMgmtPort`
- `CONTRIBUTING.md`:
  - no change required
- `KNOWN_DEVIATIONS.md`:
  - no change required

## Rollback / Exit Strategy

- What can be reverted independently:
  - the `FlightDataMgmtPort` contract
  - the `AppNavGraph` route rewiring
  - the `FlightMgmt` constructor change
- What would trigger rollback:
  - route regression in flight-management previews or card hydration
- How this ADR is superseded or retired:
  - supersede it when the broader map-shell boundary cleanup replaces this
    route seam with a final steady-state screen contract
