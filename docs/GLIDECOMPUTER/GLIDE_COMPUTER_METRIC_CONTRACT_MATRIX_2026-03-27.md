# GLIDE_COMPUTER_METRIC_CONTRACT_MATRIX_2026-03-27

## Purpose

Freeze the semantics of the pilot-facing glide-computer metrics before implementation.
This is the contract Codex should follow unless a later accepted doc deliberately changes it.

## Status legend
- IMPLEMENTED = already backed by authoritative runtime data
- HARDEN = implemented but needs source/validity/semantic cleanup
- NEW = needs a new runtime seam before release
- HIDE = do not expose in production until implemented

## Core air-data / glide metrics

| Metric | Release meaning | Status | Authoritative owner | Notes |
|---|---|---|---|---|
| IAS | indicated airspeed from authoritative flight runtime sample | HARDEN | `FlightDataRepository` -> adapter | Card labels must use actual airspeed source/validity |
| TAS | true airspeed from authoritative flight runtime sample or explicit derived estimate | HARDEN | `FlightDataRepository` -> adapter | Must not be mislabeled from TAS-valid heuristics |
| GS | ground speed over ground from fused runtime sample | IMPLEMENTED | `FlightDataRepository` -> adapter | Straightforward |
| current L/D | recent measured glide ratio from runtime flight metrics, not final glide and not polar best L/D | HARDEN | `FlightDataRepository` -> adapter | Needs explicit validity, not formatter heuristics |
| polar L/D | theoretical L/D at current speed from active polar | HARDEN | polar owner path -> adapter | Needs explicit validity |
| best L/D | best still-air L/D from active polar | HARDEN | polar owner path -> adapter | Needs explicit validity |
| netto | compensated air-mass vertical speed from runtime flight metrics | HARDEN | flight runtime -> adapter | Must keep explicit validity |
| netto 30s | 30-second averaged netto using the same authoritative netto source/contract | HARDEN | flight runtime -> adapter | Add explicit validity |

## Finish-glide metrics

| Metric | Release meaning | Status | Authoritative owner | Notes |
|---|---|---|---|---|
| final glide / required L/D | glide ratio required from current position to the active finish target using canonical route + active policy | IMPLEMENTED | `GlideComputationRepository` | Finish-only today |
| arrival altitude | predicted finish altitude surplus/deficit using active MC and current policy | IMPLEMENTED | `GlideComputationRepository` | Keep explicit solve state |
| required altitude | altitude required now to complete the active finish route | IMPLEMENTED | `GlideComputationRepository` | Keep explicit solve state |
| arrival altitude MC0 | same as arrival altitude but with MC forced to zero | IMPLEMENTED | `GlideComputationRepository` | Keep explicit solve state |
| final distance | canonical remaining distance to finish target | HARDEN | `GlideComputationRepository` / route seam | Cheap add if data already present |

## Waypoint navigation metrics

| Metric | Release meaning | Status | Authoritative owner | Notes |
|---|---|---|---|---|
| WPT DIST | distance from current position to the active target point on the canonical route; boundary-aware when the route seam supplies a touch point / target point | NEW | `WaypointNavigationRepository` | Never compute in formatter |
| WPT BRG | bearing from current position to the active target point on the canonical route | NEW | `WaypointNavigationRepository` | Same target definition as WPT DIST |
| WPT ETA | estimated time of arrival at the active target point using explicit speed/availability rules | NEW | `WaypointNavigationRepository` | Validity must be explicit |

## Task-performance metrics

| Metric | Release meaning | Status | Authoritative owner | Notes |
|---|---|---|---|---|
| TASK SPD | achieved task speed since accepted task start | NEW | `TaskPerformanceRepository` | Must freeze semantics; no ambiguity |
| TASK DIST | covered task distance since accepted task start | NEW | `TaskPerformanceRepository` | Use canonical task-distance semantics |
| TASK REMAIN DIST | remaining task distance from canonical route seam | NEW | `TaskPerformanceRepository` or route seam projection | More useful than TASK DIST alone |
| TASK REMAIN TIME | estimated remaining task time using explicit policy | NEW | `TaskPerformanceRepository` | Document the speed basis |
| START ALT | nav altitude captured at accepted task start crossing | NEW | `TaskPerformanceRepository` | Must document altitude reference |

## General Polar contract

| Field / concept | Release meaning | Status | Owner | Notes |
|---|---|---|---|---|
| 3-point manual polar | authoritative user-supplied polar curve used by sink/LD/final-glide path | IMPLEMENTED | `GliderRepository -> PolarStillAirSinkProvider` | Must stay authoritative |
| reference weight | explicit weight reference affecting authoritative polar math, if exposed in UI | NEW/HARDEN | profile/polar path | Either wire it or do not expose as supported |
| user coefficients | explicit polynomial/coefficients affecting authoritative polar math, if exposed in UI | NEW/HARDEN | profile/polar path | Either wire it or do not expose as supported |
| bugs / ballast | explicit effect on authoritative polar math | HARDEN | profile/polar path | Current behavior is simplified; document or improve |

## Non-negotiable architectural rules

- `CompleteFlightData` remains flight-data only.
- `TaskManagerCoordinator.taskSnapshotFlow` remains the cross-feature task runtime seam.
- `TaskRepository` is not used as runtime authority for any glide-computer metric.
- `feature:map` and card formatters remain consumers only.
- replay/live determinism must be preserved.

## UI policy

Any card in the catalogs must satisfy one of these:
1. it is fully implemented with authoritative runtime data and explicit validity
2. it is hidden from production selection

No release should expose placeholder nav/task cards as if they are working instruments.
