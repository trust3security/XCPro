> NOTICE (2026-02-06): Task refactor plan is documented in $plan. Review before implementing task-related changes.

# TC AVG Parity Plan (XCSoar -> XCPro)

Goal: Make XCPro's "TC AVG" card match XCSoar behavior in **live** and **replay**.

Note: When working on this plan, always read and comply with `ARCHITECTURE.md`, `CODING_RULES.md`, `CONTRIBUTING.md`, and `README.md`.

## Parity Definition (XCSoar)
- TC Avg shows **current thermal average** while circling, and **last thermal average** when not circling.
- TC Avg is **invalid** until a valid thermal exists.
- A thermal is **valid for "last thermal"** only if:
  - duration >= 45 seconds, AND
  - gain > 0 meters.
- Circling detection:
  - turn rate >= 4 deg/s
  - 15s entry threshold, 10s exit threshold
  - only when **flying** is true
- Flying detection:
  - ~10 m/s for 10s, OR
  - AGL >= 300m

## Current XCPro Behavior (Summary)
- Circling thresholds and flying detection already match XCSoar.
- TC Avg is gated on `currentThermalValid`.
- **Missing parity**: XCPro accepts short/negative thermals as "last thermal".

## Gaps to Close
1) Add **XCSoar thermal validity gates** for last thermal:
   - duration >= 45s
   - gain > 0m
2) Ensure TC Avg is invalid until a qualifying thermal exists.
3) Preserve last valid thermal if a new short/negative thermal occurs.
4) Align **thermal start/end timing** with XCSoar's circling hysteresis:
   - Start time/altitude should be captured at **first turning** (before 15s confirmation).
   - End time/altitude should be captured at **first non-turning** (before 10s confirmation).

## Implementation Plan

### 1) ThermalTracker: last thermal gate
Files:
- `feature/map/src/main/java/com/example/xcpro/sensors/ThermalTracker.kt`
- `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataModels.kt`

Changes:
- Add constants:
  - `THERMAL_MIN_DURATION_SECONDS = 45.0`
  - `THERMAL_MIN_GAIN_METERS = 0.0` (strictly > 0)
- On **exit from circling**, only finalize `lastThermalInfo` if:
  - durationSeconds >= 45.0
  - gain > 0.0
- If invalid: do **not** overwrite the previous `lastThermalInfo`.

Rationale:
- XCSoar only finalizes "last thermal" for meaningful thermals.

### 1b) Thermal timing parity (turning hysteresis)
Files:
- `feature/map/src/main/java/com/example/xcpro/sensors/CirclingDetector.kt`
- `feature/map/src/main/java/com/example/xcpro/sensors/ThermalTracker.kt`
- `feature/map/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt`

Changes:
- Extend `CirclingDecision` to include `turning` (and optionally `turningStartTimeMs`).
- Track turning state in `ThermalTracker`:
  - On turning start: capture `startTime` and `startTeAltitude`.
  - When circling becomes true: keep the earlier start values (do not reset).
  - On turning end (first not-turning sample): capture `endTime` and `endTeAltitude`.
  - When circling becomes false: finalize last thermal using the stored end values
    (so the last thermal excludes the 10s exit delay).
  - While circling remains true (exit hysteresis), keep **current** thermal
    updating with the live timestamp/altitude to match XCSoar's temporary
    still circling" display behavior.

Rationale:
- XCSoar starts the thermal at the first turning sample and ends it at the
  first non-turning sample; the 15s/10s thresholds only confirm the mode.

### 2) Thermal validity semantics
File:
- `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataModels.kt`

Changes:
- Align `ThermalClimbInfo.isDefined()` with XCSoar:
  - Use duration > 0 as definition (not just timestamps).
- Add explicit flags in `ThermalTracker`:
  - `currentThermalValid`
  - `lastThermalValid`
  - `thermalAverageValid` (current OR last)

### 3) Metric wiring
Files:
- `feature/map/src/main/java/com/example/xcpro/sensors/FlightCalculationHelpers.kt`
- `feature/map/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt`

Changes:
- Map `currentThermalValid` to the **combined** validity:
  - true if current is defined or a valid last thermal exists.
- Keep `thermalAverageCurrent` as:
  - current thermal average while circling
  - last thermal average when not circling and valid

### 4) Card display behavior
File:
- `dfcards-library/src/main/java/com/example/dfcards/CardDataFormatter.kt`

Changes:
- No logic changes required if validity flags are updated.
- TC Avg remains gated on `currentThermalValid`.

### 5) Tests (lock parity)
Suggested new tests:
- `feature/map/src/test/java/com/example/xcpro/sensors/ThermalTrackerTest.kt`

Cases:
1) Circling < 45s, gain > 0 -> last thermal NOT updated.
2) Circling >= 45s, gain <= 0 -> last thermal NOT updated.
3) Circling >= 45s, gain > 0 -> last thermal updated.
4) Short/negative thermal does not overwrite previous valid last thermal.

### 6) Replay validation logs (optional)
File:
- `feature/map/src/main/java/com/example/xcpro/replay/ReplayPipeline.kt`

Change:
- Add log fields:
  - `currentThermalValid`
  - `thermalGainValid`
  - `isCircling`
  - `thermalAverageCircle`

Purpose:
- Verifies TC Avg behavior while replaying IGCs.

### 7) Documentation
Add note in:
- `docs/LevoVario/levo.md` or this file only

Note:
- "TC Avg matches XCSoar: current thermal average while circling; last thermal average only if >=45s and gain > 0."

## Acceptance Criteria
- TC Avg stays invalid until a qualifying thermal is completed.
- Short or negative thermals do not replace a prior valid last thermal.
- Behavior is identical in live and replay modes.

## Open Questions (if needed)
- Should min duration / gain be configurable in settings? (XCSoar uses constants)


