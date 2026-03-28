# GLIDE_COMPUTER_METRIC_CONTRACT_MATRIX_2026-03-27

## Purpose

Record the final release semantics of the pilot-facing glide-computer metrics.
This is the shipped contract unless a later accepted doc deliberately changes it.

## Status legend
- IMPLEMENTED = already backed by authoritative runtime data
- HARDEN = implemented but needs source/validity/semantic cleanup
- NEW = needs a new runtime seam before release
- HIDE = do not expose in production until implemented
- DEFER = stored for future work but intentionally not part of the active release contract

## Core air-data / glide metrics

| Metric | Release meaning | Status | Authoritative owner | Notes |
|---|---|---|---|---|
| IAS | indicated airspeed from authoritative flight runtime sample | IMPLEMENTED | `FlightDataRepository` -> adapter | Card labels use the actual airspeed source/validity contract |
| TAS | true airspeed from authoritative flight runtime sample or explicit derived estimate | IMPLEMENTED | `FlightDataRepository` -> adapter | Labels and validity use the actual TAS source contract, not heuristics |
| GS | ground speed over ground from fused runtime sample | IMPLEMENTED | `FlightDataRepository` -> adapter | Straightforward |
| current L/D | recent measured glide ratio from runtime flight metrics, not final glide and not polar best L/D | IMPLEMENTED | `FlightDataRepository` -> adapter | Explicit valid/invalid only; no degraded state in the active contract |
| polar L/D | theoretical still-air L/D at the current IAS sample from the active polar path | IMPLEMENTED | polar owner path -> adapter | Explicit valid/invalid only; no degraded state in the active contract |
| best L/D | best still-air L/D from the active polar path across the active IAS bounds | IMPLEMENTED | polar owner path -> adapter | Explicit valid/invalid only; no degraded state in the active contract |
| netto | compensated air-mass vertical speed from runtime flight metrics | IMPLEMENTED | flight runtime -> adapter | Explicit valid/invalid only; no degraded state in the active contract |
| netto 30s | 30-second averaged netto using the same authoritative netto source/contract | IMPLEMENTED | flight runtime -> adapter | Explicit valid/invalid only; no degraded state in the active contract |

## Finish-glide metrics

| Metric | Release meaning | Status | Authoritative owner | Notes |
|---|---|---|---|---|
| final glide / required L/D | glide ratio required from current position to the active finish target using canonical route + active policy | IMPLEMENTED | `GlideComputationRepository` | Explicit valid/degraded/invalid state; degraded = still-air assumption when no usable wind exists |
| arrival altitude | predicted finish altitude surplus/deficit using active MC and current policy | IMPLEMENTED | `GlideComputationRepository` | Shares the same explicit solve state as final glide |
| required altitude | altitude required now to complete the active finish route | IMPLEMENTED | `GlideComputationRepository` | Shares the same explicit solve state as final glide |
| arrival altitude MC0 | same as arrival altitude but with MC forced to zero | IMPLEMENTED | `GlideComputationRepository` | Shares the same explicit solve state as final glide |
| final distance | canonical remaining distance to finish target | IMPLEMENTED | `GlideComputationRepository` / route seam | Authoritative runtime metric exists, but no standalone production card ships in this plan |

## Waypoint navigation metrics

| Metric | Release meaning | Status | Authoritative owner | Notes |
|---|---|---|---|---|
| WPT DIST | distance from current GPS position to the active target point on the canonical route; boundary-aware when the route seam supplies a touch point / target point | IMPLEMENTED | `WaypointNavigationRepository` consuming `NavigationRouteRepository.route` | Valid only when the route seam is valid and a fused GPS position exists |
| WPT BRG | true bearing from current GPS position to the active target point on the canonical route | IMPLEMENTED | `WaypointNavigationRepository` consuming `NavigationRouteRepository.route` | Same target definition and base validity as WPT DIST |
| WPT ETA | local ETA clock time for the active target point using current GPS ground speed and replay-safe sample wall time | IMPLEMENTED | `WaypointNavigationRepository` consuming `NavigationRouteRepository.route` | Valid only when WPT DIST is valid, speed is > 2.0 m/s, and sample wall time exists |

## Task-performance metrics

| Metric | Release meaning | Status | Authoritative owner | Notes |
|---|---|---|---|---|
| TASK SPD | achieved average task speed since the accepted task start crossing, using accepted start timestamp to current task sample time; after finish, use finish crossing time instead of live wall time | IMPLEMENTED | `TaskPerformanceRepository` consuming `TaskNavigationController.racingState` | Invalid before start or when accepted start truth is missing |
| TASK DIST | covered task distance since accepted task start, computed as accepted-start reference route distance minus authoritative remaining route distance | IMPLEMENTED | `TaskPerformanceRepository` consuming `NavigationRouteRepository.route` plus task-owned start projection | Invalid before start or when accepted start truth is missing |
| TASK REMAIN DIST | canonical remaining task distance from current GPS position across the authoritative remaining route seam | IMPLEMENTED | `TaskPerformanceRepository` consuming `NavigationRouteRepository.route` | Invalid before start or when route or GPS truth is unavailable; zero after finish |
| TASK REMAIN TIME | remaining task time = `TASK REMAIN DIST / TASK SPD`, using achieved average task speed only when it is finite and `> 2.0 m/s` | IMPLEMENTED | `TaskPerformanceRepository` | Invalid before start, when remaining distance is invalid, or when achieved task speed is unavailable or non-credible |
| START ALT | accepted task-start altitude from the authoritative accepted start sample using the task start altitude reference; `QNH` falls back to same-sample `MSL` only when QNH altitude is absent | IMPLEMENTED | `TaskPerformanceRepository` consuming `TaskNavigationController.racingState` | Invalid before start, when accepted start sample is missing, or when the required altitude sample is unavailable |

## General Polar contract

| Field / concept | Release meaning | Status | Owner | Notes |
|---|---|---|---|---|
| 3-point manual polar | authoritative user-supplied polar curve used by the IAS-based sink/LD/final-glide path | IMPLEMENTED | `GliderRepository -> PolarStillAirSinkProvider` | Highest-priority polar source on the active runtime path |
| reference weight | historical/stored configuration only; not part of authoritative runtime math in the release contract | DEFER | profile/polar path | Hidden from the active release UI until a tested runtime math contract exists |
| user coefficients | historical/stored configuration only; not part of authoritative runtime math in the release contract | DEFER | profile/polar path | Not exposed as a supported control in the active release contract |
| bugs / ballast | simplified authoritative sink modifiers applied on top of the active polar path | IMPLEMENTED | profile/polar path | `bugsPercent` scales sink; `waterBallastKg` affects wing-loading interpolation and adds the current ballast penalty |

## Non-negotiable architectural rules

- `CompleteFlightData` remains flight-data only.
- `TaskManagerCoordinator.taskSnapshotFlow` remains the cross-feature task runtime seam.
- `TaskRepository` is not used as runtime authority for any glide-computer metric.
- `feature:map` and card formatters remain consumers only.
- replay/live determinism must be preserved.

## UI policy

Any pilot-facing glide-computer metric must satisfy one of these:
1. it is fully implemented with authoritative runtime data and explicit validity
2. it is intentionally kept out of the production catalogs / production selection

No release should expose placeholder nav/task/glide cards as if they are working instruments.
