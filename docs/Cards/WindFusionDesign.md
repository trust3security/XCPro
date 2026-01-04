# Wind Sensor Fusion Design (XCPro)

_This design must comply with our guardrails: see `ARCHITECTURE.md` (SSOT/UDF/DI rules), `CODING_RULES.md` (layering, flows, naming), and `CONTRIBUTING.md` (Definition of Done, testing, AI-NOTE intent). Keep this doc in sync when those files change._

Goal: make wind estimation a first-class fusion domain (own SSOT, buffers, and validation) instead of deriving wind from downstream flight snapshots. This mirrors XCSoar’s dedicated wind “computer” while fitting our Kotlin/DI/Flow stack.

## Current state
- `WindSensorFusionRepository` consumes normalized raw inputs (GPS + optional baro/heading/airspeed) via a `SensorDataSource` adapter.
- Replay/source gating is handled inside the wind repository by switching input flows when the active source changes.
- Airspeed input is optional and not wired yet; EKF outputs only when an independent airspeed stream is provided.

## Design principles
- **Single Source of Truth per domain:** Wind owns its own SSOT flow (`StateFlow<WindState>`). No other layer re-derives wind.
- **Own the inputs:** Consume normalized raw primitives (GPS track/groundspeed/timestamp, optional IAS/TAS, optional pressure altitude, optional attitude). Do not consume `CompleteFlightData`.
- **Buffer + validate inside wind:** Circling detection, EKF gating, blackout after manoeuvres, altitude/time windows, staleness expiry.
- **Immutable outputs only:** Expose read-only `StateFlow<WindState>`; internal state stays private.
- **Platform-free fusion:** No `Context`/Android classes inside the wind fusion repo; inject abstractions (sensor provider, clock).
- **Deterministic replay:** Use replay timestamps as the simulation clock; gate by active source (LIVE/REPLAY) and reject cross-source updates.
- **Test-first:** All math/use-cases pure and unit-testable; repo tested with fake sensor streams.

## Target architecture
```
SensorDataSource (raw flows)
   └─ WindSensorFusionRepository (SSOT)
        ├─ CirclingWindUseCase
        ├─ WindEkfUseCase
        ├─ WindStore (quality-weighted cache)
        └─ WindSelectorUseCase
             ↓
        StateFlow<WindState> (immutable)
             ↓
   Consumers: FlightDataCalculator (read-only for display/metrics),
              ViewModels/UI, logging, replay tools
```

### Components
- **WindSensorFusionRepository (new, @Singleton)**
  - Inputs: `StateFlow<GpsSample?>`, `StateFlow<PressureSample?>` (optional), `StateFlow<AirspeedSample?>` (optional), `StateFlow<AttitudeSample?>` (optional), clock, dispatcher.
  - Responsibilities: subscribe, gate by source (LIVE/REPLAY), coordinate use-cases, maintain `_windState`, expose `windState: StateFlow<WindState>`.
  - Threading: `Dispatchers.Default`; no IO inside.

- **CirclingWindUseCase**
  - Detect sustained circling (turn rate + groundspeed thresholds, hysteresis).
  - Produce wind estimate + quality from a full circle; reset on gaps/time-warp.

- **WindEkfUseCase**
  - Update step using true airspeed + groundspeed vector; blackout during high turn rate or g-load; quality ramp with sample count.
  - XCSoar gates EKF to "real" airspeed only (instrument/dynamic pressure); do not feed wind-derived TAS to avoid circularity.
  - Reference gates: requires flying + track/ground speed updates, TAS > 1 m/s, turn rate > ~20 deg/s or |g-load-1| > 0.3 triggers ~3s blackout; emit every N samples (XCSoar uses 10).

- **WindStore**
  - Slot measurements with altitude/time/quality weighting (1 km altitude band, ~1 h time decay, override rule).
  - Provide freshest non-stale estimate or return empty if stale.

- **WindSelectorUseCase**
  - Priority: EKF (if available) → Circling → External/manual (future) → None.
  - Compute headwind/crosswind from heading.

### Data contracts
- **Inputs (normalized, no Android types)**
  - `GpsSample(latLng, groundSpeedMs, trackRad, timestampMillis)`
  - `PressureSample(pressureHpa, altitudeMeters, timestampMillis)` (optional)
  - `AirspeedSample(trueMs, indicatedMs, timestampMillis, valid)` (optional)
  - `HeadingSample(headingDeg, timestampMillis)` (optional)
  - `Source` enum: LIVE | REPLAY
- **Output**
  - `WindState`:
    - `vector: WindVector?` (north/east m/s)
    - `headwindMs: Double`, `crosswindMs: Double`
    - `source: WindSource` (EKF, CIRCLING, EXTERNAL, MANUAL, NONE)
    - `quality: Int`
    - `lastUpdatedMillis: Long`
    - `stale: Boolean`

### Replay and source gating
- Repository tracks `activeSource`; drops samples from mismatched sources.
- Replay uses the sample timestamp as “simulation time” for derivations and staleness; avoids wall-clock drift.
- On source switch, reset buffers (EKF, circling detector, store) to prevent cross-contamination.

### Staleness and quality
- Mark stale if `now - lastUpdatedMillis > 1h` (configurable).
- Publish empty `WindState` when stale or when inputs drop out.
- Quality from EKF sample count; circling quality from circle count + heading span; store applies time/altitude decay.

### Error handling
- Invalid/NaN inputs are rejected per-use-case; do not emit partial wind.
- Use Result types or nullable outputs; no exceptions in hot paths.

### Testing strategy
- Unit: circling detection, EKF update, store weighting, selector priority, staleness expiry, blackout logic.
- Integration: fake sensor streams for LIVE and REPLAY, asserting deterministic outputs for known tracks and airspeeds.
- Concurrency: cancellation and source-switch reset tests.

## Migration plan
1) Add data models (`GpsSample`, `PressureSample`, `AirspeedSample`, `HeadingSample`, `WindState`) in `feature/map/src/main/java/com/example/xcpro/weather/wind/model`.
2) Extract current wind math from `WindRepository` into use-cases: circling, EKF, store, selector (pure Kotlin, no Android).
3) Create `WindSensorFusionRepository`:
   - Subscribe directly to sensor flows (`SensorDataSource` or a thin `SensorFrontEnd` adapter), not `CompleteFlightData`.
   - Implement source gating, staleness, and resets.
   - Expose `windState: StateFlow<WindState>`.
4) Update consumers:
   - `FlightDataCalculator` reads `windState` (read-only) for display/metrics.
   - UI/ViewModels observe `windState` instead of wind fields in `CompleteFlightData`.
5) Deprecate old `WindRepository` and remove wind derivation from `FlightDataRepository` consumers.
6) Add tests (unit + integration with fake sensor streams) and replay determinism checks.
7) Cleanup: delete `WindRepository` and any duplicate wind fields/paths once all consumers are on `windState`; ensure only one wind SSOT remains.

## Notes from XCSoar parity
- XCSoar keeps wind separate: circling + EKF + store, owning raw inputs and publishing a chosen wind plus source and quality. We replicate the ownership pattern but modernize with DI, coroutines, and immutable flows.

## Done definition
- One wind SSOT (`WindSensorFusionRepository`) fed by sensor primitives, not by `CompleteFlightData`.
- Immutable `StateFlow<WindState>` used everywhere; no other wind SSOT exists.
- Platform concerns abstracted; wind fusion code is pure Kotlin + coroutines.
- Replay deterministic; source switches reset buffers cleanly.
- Tests cover math, gating, staleness, and replay. 

## Quick adoption checklist
1) Models: add `GpsSample`, `PressureSample`, `AirspeedSample`, `HeadingSample`, `WindState`, `WindSource`.
2) Domain use-cases: implement circling detector, EKF step, store/weighting, selector (pure Kotlin).
3) Repository: build `WindSensorFusionRepository` that subscribes to sensor flows (not `CompleteFlightData`), gates by source, handles staleness/reset, exposes `windState`.
4) DI: bind repo + sensor provider + dispatcher in Hilt; inject where needed.
5) Consumers: switch `FlightDataCalculator` and UI/ViewModels to `windState`; stop using wind fields from `CompleteFlightData`.
6) Tests: unit (circling/EKF/store/selector) + integration (live/replay fake streams); verify deterministic replay and staleness.
7) Cleanup: remove legacy `WindRepository` and any wind derivation from flight data consumers; delete redundant wind fields if unused to restore single-SSOT.
