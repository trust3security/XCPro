# Card Spec — Wind DIR (FROM)

Status: Draft spec
Owner: XCPro avionics team
Last updated: 2025-11-12

---

## Purpose
- Present horizontal wind direction the wind is coming FROM (deg True), stable and pilot-trustworthy during both cruise and thermalling.

## Sources & Math
- Phase 1: Regression-based wind estimate over a sliding window (see Implementation Plan). Direction = atan2(W_east, W_north) converted to FROM (add 180°) and normalized to [0,360).
- Phase 2: EKF `u,v` state → same conversion.

## Inputs (SI inside)
- Wind vector (u,v) or (speed, dir FROM True), plus `windConfidence` and `windIsStale`.

## Output Fields
- `windDirection` (deg True FROM)
- `windConfidence` [0..1]
- `windIsStale` (Boolean)

## UI/Formatting
- Title: “WIND DIR”.
- Value: integer degrees with degree symbol and “FROM” context in subtitle.
- Badge: CONF x% (e.g., CONF 82%) or STALE.
- Smoothing: circular EMA on angles with wrap handling (α≈0.2).

## Logic
- If `windConfidence < 0.4` or stale, display last good value with STALE.
- If speed < 2 kt, show “--°” and “NO WIND”.
- Optional setting: show Magnetic by applying local variation (future).

## Telemetry
- Log dir_true_from_raw, dir_smoothed, confidence, stale.

## Acceptance
- Straight-and-level: direction variance < 10° over 10 s in calm flow.
- Thermalling: direction remains near constant; does not track orbit heading.

## Wiring
- Domain → `CompleteFlightData.windDirection` (deg True FROM), `windConfidence`, `windIsStale`.
- Adapter → `RealTimeFlightData` same fields.
- Card: use `wind_dir` id (exists). Update formatter to show confidence badge.

