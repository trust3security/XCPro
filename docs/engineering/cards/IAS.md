# Card Spec — IAS (Indicated Airspeed)

Status: Draft spec
Owner: XCPro avionics team
Last updated: 2025-11-12

---

## Purpose
- Show airspeed referenced to sea-level standard density (pilot-familiar IAS) for speed-to-fly, Vne checks, and vario compensation.

## Sources & Math
- IAS = TAS * sqrt(ρ / ρ0), where ρ from baro (QNH + altitude, ISA temp or OAT if provided).
- TAS from `CalculateAirspeedsUseCase` (vector-diff with wind or polar/density fallback).

## Inputs (SI inside)
- TAS (m/s), pressure altitude (m), QNH (hPa), optional OAT (°C).

## Output Fields
- `ias` (m/s) — required.
- `airspeedQuality` — reuse TAS quality (KNOWN/EST/STALE) since IAS derives from TAS and density.

## UI/Formatting
- Title: “IAS”.
- Value: formatted per Units prefs (kt/km/h). Example: “58 kt”.
- Badge: KNOWN | EST | STALE.
- Optional tape coloring for Vfe/Vne ranges (future).

## Logic
- Prefer real OAT if available; else ISA temperature profile.
- If density inputs stale, propagate last IAS with STALE.

## Edge Cases
- Very high altitude: clamp density ratio to sane bounds to avoid spikes.
- On ground (speed < 3 m/s): show “--” with EST to avoid taxi noise.

## Telemetry
- Log IAS, TAS, density ratio, QNH, OAT source (ISA/Measured).

## Acceptance
- Replay at 5–10k ft ISA: IAS within ±3 kt relative to reference.

## Wiring
- Domain: `CalculateAirspeedsUseCase` outputs (tas, ias, quality).
- `FlightDataCalculator.kt`: store to `CompleteFlightData.indicatedAirspeed` and map to `RealTimeFlightData.ias`.
- Card: add `ias` card; format via `UnitsFormatter.speed`.

