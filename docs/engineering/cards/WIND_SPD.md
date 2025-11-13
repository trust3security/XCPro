# Card Spec — Wind SPD

Status: Draft spec
Owner: XCPro avionics team
Last updated: 2025-11-12

---

## Purpose
- Display horizontal wind speed magnitude, stable during manoeuvres; aids task speed planning and energy management.

## Sources & Math
- Phase 1: Speed = |W| from regression-based estimate over sliding window (see Plan).
- Phase 2: EKF `u,v` → |W|.

## Inputs (SI inside)
- `windSpeed` (m/s), `windConfidence` [0..1], `windIsStale`.

## UI/Formatting
- Title: “WIND SPD”.
- Value: formatted per Units prefs (kt/km/h). Example: “12 kt”.
- Badge: CONF x% (≥ 60% shows CALC, else EST); STALE on freeze.
- Update rate: 5–10 Hz; smooth with EMA (α≈0.25), clamp jumps > 10 kt/s.

## Logic
- If `windConfidence ≥ 0.6`, badge = CALC; else EST.
- If speed < 1 kt or stale, show “--” with NO WIND/STALE.

## Telemetry
- Log |W| raw, smoothed, confidence, window size.

## Acceptance
- Cruise, constant IAS: variance over 10 s < 2 kt.
- Crosswind legs: responds within 2–4 s to real change; no large spikes on speed-to-fly changes.

## Wiring
- Domain → `CompleteFlightData.windSpeed`, `windConfidence`, `windIsStale`.
- Adapter → `RealTimeFlightData` mapping.
- Card: uses existing `wind_spd`; update formatter to use confidence mapping and badges.

