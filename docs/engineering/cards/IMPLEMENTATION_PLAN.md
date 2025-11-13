# Airspeed & Wind Cards — Implementation Plan

Last updated: 2025-11-12
Owner: XCPro avionics team
Scope: TAS, IAS, Wind DIR, Wind SPD cards and plumbing (Domain → ViewModel → UI), aligned with Units policy.

---

## Goals

- Deliver accurate, low-latency TAS/IAS plus robust Wind Speed/Direction for glider pilots.
- Expose clear quality/confidence and graceful degradation (freeze, stale badge).
- Keep internal SI units; format via UnitsFormatter per user prefs.
- Minimize churn: factor new logic into small domain use cases and adapt `FlightDataCalculator` wiring.

---

## Architecture (fit to current repo)

- Domain use cases (pure Kotlin):
  - `CalculateWindUseCase` — estimates horizontal wind vector (m/s, deg True FROM) with quality.
  - `CalculateAirspeedsUseCase` — computes `IAS` and `TAS` with quality and fallbacks.
- Data sources:
  - GNSS: ground speed, track, vertical speed.
  - Baro: pressure altitude, climb rate.
  - IMU: rotation vector (for turn rate gating), accel/gyro (optional for Phase 2).
  - Still-air polar (sink vs speed) and QNH/OAT (if available).
- Integration:
  - Inject use cases into `FlightDataCalculator` (keep as orchestrator for now) and emit into `CompleteFlightData` → adapter → `RealTimeFlightData` (add quality fields).

---

## Phases

### Phase 1 — Quick Uplift (1–2 sprints)

1) Wind (horizontal only) improvement
- Replace current “max-min GS / 2” heuristic with a sliding-window regression of the velocity triangle:
  - Solve W that minimizes || v_gps - (R_heading * v_air) - W || over a window (10–20 samples),
    with v_air constrained by polar-estimated IAS/TAS if no pitot.
  - Gate samples by turn rate (from rotation vector), GNSS accuracy, and speed (> 10–15 m/s).
  - Output: speed (m/s), direction True FROM (deg), confidence [0..1].

2) TAS/IAS
- With wind available: TAS = | v_gps - W | in earth frame.
- IAS = TAS * sqrt(ρ / ρ0) using baro-derived density (QNH, ISA temp or OAT if present).
- Without wind: estimate IAS from TE/polar (existing `estimateAirspeeds` logic) and derive TAS from density.
- Output qualities: `known`, `estimated`, `stale`.

3) Plumbing & UI
- Extend `CompleteFlightData` and `RealTimeFlightData` with quality fields:
  - `windConfidence: Float`, `windIsStale: Boolean`.
  - `tas: Double`, `ias: Double`, `airspeedQuality: String` ("KNOWN" | "EST" | "STALE").
- Cards read these fields and show badges per specs (see per-card docs).
- Units via `UnitsFormatter`.

4) Telemetry & Tests
- Log 5–10 Hz CSV: time, GS, track, W(u,v), |W|, dir, TAS, IAS, qualities.
- Add unit tests for density/IAS, TAS vector math, wind regression on synthetic tracks, and formatters.

### Phase 2 — EKF Wind/AHRS (optional, parallel track)

- Implement `HAWK` EKF (attitude, biases, u,v,w) running at IMU rate; keep Phase 1 as fallback.
- Wire EKF outputs into the same domain models; cards automatically benefit from higher stability.

---

## File/Code Changes (incremental)

- Domain (new):
  - `feature/wind/domain/CalculateWindUseCase.kt`
  - `feature/airspeed/domain/CalculateAirspeedsUseCase.kt`
- Data model:
  - Add fields to `CompleteFlightData` and `RealTimeFlightData` for TAS/IAS and qualities.
  - Keep `windSpeed`/`windDirection` but add `windConfidence` and `windIsStale`.
- Orchestrator:
  - `feature/map/.../FlightDataCalculator.kt` — replace `calculateWindSpeed(...)` call with injected use case; replace `estimateAirspeeds(...)` with domain use case and remove duplicate physics where possible.
- Cards:
  - `dfcards-library/.../CardLibraryCatalog.kt` — add `tas` and `ias` cards.
  - `dfcards-library/.../CardDataFormatter.kt` — implement formatters and badge text per per-card docs; update wind labels to use confidence.

---

## Data & Units

- Internal: SI (m/s, meters, hPa, °C). Directions in degrees True; UI may offer True/Mag.
- Display:
  - Speed units: user pref (kt/km/h) via `UnitsFormatter.speed`.
  - IAS/TAS labels show unit and quality badge.
  - Wind DIR shows integer degrees with “FROM”.

---

## Acceptance Criteria

- TAS/IAS:
  - In steady air and valid wind, TAS within ±2 kt vs reference replay; IAS within ±3 kt at 5–10k ft ISA.
  - Latency < 200 ms from new GNSS/baro sample to card update.
- Wind:
  - In straight-and-level (constant IAS), |W| variance over 10 s < 2 kt; direction variance < 10°.
  - During thermalling, wind direction doesn’t alias to orbit heading; confidence drops appropriately when unobservable.
- Degradation:
  - On GNSS dropout > 3 s, cards freeze and show STALE; recover automatically.

---

## Risks & Mitigations

- Underconstrained without IAS sensor → use polar/TE constraint and confidence gating; consider BLE pitot later.
- Magnetic disturbances → use rotation-vector only for turn gating; avoid raw mag in Phase 1 wind.
- Phone variability → parameterize window sizes and thresholds; expose per-device caps.

---

## Open Questions

1) Will we support BLE pitot/IAS or OAT sensors in the near term? If yes, we can promote IAS to "KNOWN" quality.
2) Wind direction reference: always True for cards, with optional Mag toggle elsewhere?
3) Confirm the glider polar source and units; can we calibrate in-app?
4) Are new `RealTimeFlightData` fields acceptable API changes for cards?

