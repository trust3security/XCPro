# ADR_MAP_FLIGHT_MODE_OWNERSHIP_2026-04-12.md

## Metadata

- Title: Map Flight-Mode Ownership Cleanup
- Date: 2026-04-12
- Status: Accepted
- Owner: XCPro Team
- Reviewers: XCPro Team
- Related issue/PR: None
- Related change plan: `docs/refactor/Map_Flight_Mode_Ownership_Cleanup_Phased_IP_2026-04-12.md`
- Supersedes: None
- Superseded by: None

## Context

- Problem:
  `feature:map` had split ownership for flight-mode visibility and fallback behavior.
  `FlightDataManager`, thermalling runtime wiring, and Compose/UI consumers all
  participated in deciding the current map mode. That broke SSOT and made
  replay/runtime suppression harder to reason about.
- Why now:
  Flight-mode visibility was already persisted in dfcards, but map-side runtime
  behavior still depended on manager-owned helpers and UI fallback logic.
- Constraints:
  Keep dfcards as the persisted visibility owner, do not add a new repository or
  direct `CardPreferences` visibility reader in `feature:map`, do not add new
  long-lived scopes, and preserve current behavior parity.
- Existing rule/doc references:
  `AGENTS.md`, `ARCHITECTURE.md`, `CODING_RULES.md`, `PIPELINE.md`

## Decision

Map-side flight-mode resolution is owned by `MapStateStore` and orchestrated
only by `MapScreenViewModel`.

- ownership/boundary choice:
  - dfcards remains the persistence/visibility owner through
    `FlightDataViewModel`, `FlightProfileStore`, and `CardPreferences`
  - `MapStateStore` owns:
    - requested flight mode
    - transient runtime flight-mode override
    - effective flight mode
    - effective flight-mode source
    - visible map flight modes
  - `FlightDataManager` is no longer an owner of visibility/fallback policy
- dependency direction impact:
  - `feature:map` reads dfcards visibility state through the existing
    `CardIngestionCoordinator` bridge only
  - UI/Compose/gesture consumers read prepared map mode state from
    `MapScreenViewModel` / `MapStateStore`, not from `FlightDataManager`
- API/module surface impact:
  - add pure `MapFlightModePolicy`
  - expose map-owned requested/runtime/effective/visible mode state from
    `MapScreenViewModel`
  - keep `MapStateStore.currentMode` as the compatibility read path for the
    effective mode
- time-base/determinism impact:
  - no wall-clock or persistence reads were added to mode resolution
  - replay suppression clears only the transient runtime override lane, so the
    requested mode remains deterministic and sticky
- concurrency/buffering/cadence impact:
  - no new scope
  - use existing `viewModelScope` and `CardIngestionCoordinator` scope
  - `CardIngestionCoordinator` adds one combined visibility/profile collection job

## Alternatives Considered

| Option | Why Considered | Why Rejected / Not Chosen |
|---|---|---|
| Keep visibility/fallback in `FlightDataManager` | Lowest short-term edit count | Leaves a manager as a second runtime owner and keeps UI/runtime bypasses alive |
| Read `CardPreferences` directly in `feature:map` | Direct access to visibility values | Violates module/ownership rules and duplicates dfcards persistence authority |
| Let thermalling runtime keep mutating requested mode | Existing behavior path | Blurs user intent with runtime automation and breaks sticky requested-mode semantics |

## Consequences

### Benefits
- Single map-side runtime owner for mode resolution
- Clear separation between user-requested mode and transient runtime override
- UI no longer owns fallback policy
- Replay suppression is narrower and deterministic

### Costs
- More explicit bridge/wiring between dfcards and map runtime
- Temporary effective-mode sink remains in `FlightDataManager` for card/display plumbing

### Risks
- Consumers that still assume requested mode == effective mode can regress if not rewired
- Future work must remove the remaining `FlightDataManager` effective-mode sink when
  no downstream consumer needs it

## Validation

- Tests/evidence required:
  - `MapFlightModePolicyTest`
  - `MapScreenViewModelCoreStateTest`
  - `CardIngestionCoordinatorTest`
  - `ThermallingModeRuntimeWiringTest`
  - `:feature:map:compileDebugKotlin`
  - `:feature:map:testDebugUnitTest`
- SLO or latency impact:
  None expected; mode resolution is in-memory and synchronous.
- Rollout/monitoring notes:
  Watch for regressions in map flight-mode menu behavior, thermalling auto-mode
  transitions, and card preparation after profile visibility changes.

## Documentation Updates Required

- `ARCHITECTURE.md`: No change required
- `CODING_RULES.md`: No change required
- `PIPELINE.md`: Updated
- `CONTRIBUTING.md`: No change required
- `KNOWN_DEVIATIONS.md`: No change required

## Rollback / Exit Strategy

- What can be reverted independently:
  The pure policy and map-owned mode state plumbing can be reverted without
  touching dfcards persistence ownership.
- What would trigger rollback:
  Runtime regressions in flight-mode switching, thermalling automation, or
  replay suppression that cannot be corrected within the map owner path.
- How this ADR is superseded or retired:
  Supersede it with a later ADR if the remaining `FlightDataManager`
  compatibility sink is removed or if map mode ownership moves again.
