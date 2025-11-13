# Card Spec — TAS (True Airspeed)

Status: Draft spec
Owner: XCPro avionics team
Last updated: 2025-11-12

---

## Purpose
- Show the glider’s airspeed through the airmass (TAS) for navigation decisions and cross-country performance, independent of wind.

## Sources & Math
- Primary: TAS = | v_gps − W |, where v_gps is ground-velocity from GNSS and W is horizontal wind vector (earth frame).
- Density: not needed for TAS magnitude. Used only for IAS conversion.
- Fallback (no reliable W): estimate IAS via polar/TE method and convert to TAS using density ρ: TAS = IAS / sqrt(ρ/ρ0).

## Inputs (SI inside)
- GNSS: ground speed (m/s), track (deg True).
- Wind: `windSpeed` (m/s), `windDirection` (deg True FROM), `windConfidence` [0..1].
- Baro: `qnh` (hPa), pressure altitude (m). Optional OAT (°C).
- Polar: sink vs speed (m/s vs m/s) for IAS estimation when W missing.

## Output Fields
- `tas` (m/s) — required.
- `airspeedQuality` — "KNOWN" (vector-diff), "EST" (polar/density), "STALE".

## UI/Formatting
- Title: “TAS”.
- Value: formatted speed per Units prefs (kt/km/h). Example: “72 kt”.
- Badge (right-aligned): KNOWN | EST | STALE.
- Update rate: 5–10 Hz. Smooth with EMA (α≈0.25) and clamp step changes > 15 kt/s.

## Logic
- If `windConfidence ≥ 0.6` and GNSS moving, compute TAS by vector difference.
- Else use polar/density estimation. If inputs stale > 3 s, freeze and show STALE.

## Edge Cases
- Low speed (< 10 m/s): set quality to EST unless confidence high and straight flight.
- GNSS outage > 3 s: show last TAS with STALE.

## Telemetry
- Log TAS_raw, TAS_smoothed, quality, GS, W(u,v), confidence at 5–10 Hz.

## Acceptance
- Steady cruise, calm: TAS within ±2 kt vs replay reference; transitions < 200 ms.

## Wiring
- Domain: `CalculateAirspeedsUseCase` returns (tas, ias, quality).
- `FlightDataCalculator.kt`: store to `CompleteFlightData.trueAirspeed` and map to `RealTimeFlightData.tas`.
- Card: add `tas` card in `CardLibraryCatalog`, format via `UnitsFormatter.speed`.

