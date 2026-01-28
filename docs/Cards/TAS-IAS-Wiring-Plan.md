# TAS/IAS Wiring Plan (Wind EKF Parity)

## Goal
Feed real airspeed (IAS/TAS) into the wind EKF and flying-state gate so live wind uses the same
rules as XCSoar. Phone-only TAS estimates must not be used as EKF input.

## XCSoar reference (behavior to mirror)
- EKF only runs when:
  - flight.flying is true
  - airspeed_available and airspeed_real are true
  - true_airspeed > VTakeoff (polar fallback 10 m/s)
  - ground speed and airspeed have updated since last EKF update
- Airspeed can be provided by device drivers (IAS or TAS). If only one is provided,
  the other is derived using altitude and air density. airspeed_real remains true.
- Airspeed validity expires after a timeout (30 s) and airspeed_real is cleared.
- IGC replay feeds IAS/TAS when present (B-record extensions).

## Status (Jan 2026)
Implemented:
- Replay IAS/TAS parsing and emission to ReplayAirspeedRepository.
- Wind EKF updated-sample gating (rejects duplicate timestamps/time warps).
- EKF minTrueAirspeed fallback uses 10 m/s.

Not yet implemented:
- Live airspeed ingest path feeding ExternalAirspeedRepository.
- IAS/TAS conversion/normalization for live ingest.
- Airspeed staleness timeout (e.g., 30 s) for live samples.
- Polar-based VTakeoff wiring (still fallback only).
- Tests for airspeed conversion + EKF gates + replay parsing.

## XCPro current state
- Airspeed flow exists: AirspeedSample(trueMs, indicatedMs, timestampMillis, clockMillis, valid)
  with ExternalAirspeedRepository (live) and ReplayAirspeedRepository (replay).
- clockMillis must be monotonic (or replay clock); if unknown, set 0 so EKF drops updates.
- Live source does not feed ExternalAirspeedRepository yet.
- Replay parser reads IAS/TAS B-record extensions and ReplaySampleEmitter emits airspeed.
- Wind EKF gates on AirspeedSample.valid, rejects duplicate timestamps/time warps, and uses
  the 10 m/s minTrueAirspeed fallback.

## Implementation steps (proposed)
### 1) Live airspeed ingest (real source)
- Define an airspeed ingest path that updates ExternalAirspeedRepository.
  Options:
  - BLE/NMEA/USB device parser (preferred, matches XCSoar drivers).
  - Temporary manual/debug injector (for testing only).
- Ensure inputs are normalized to m/s and timestamped with sample time.
- Mark AirspeedSample.valid = true only for real instrument airspeed.

Suggested files:
- feature/map/.../weather/wind/data/ExternalAirspeedRepository.kt
- (new) feature/map/.../sensors/AirspeedIngestor.kt (or device-specific parser)
- feature/map/.../vario/VarioServiceManager.kt (start/stop ingest alongside sensors)

### 2) IAS/TAS normalization and conversion
- If device provides IAS only, compute TAS using altitude (pressure or GPS).
- If device provides TAS only, compute IAS using altitude.
- If altitude unavailable, fall back to setting both to the provided value
  (same behavior as XCSoar ProvideBothAirspeeds fallback).

Suggested helpers:
- Reuse dfcards-library AirspeedCalculator or add a small AirDensityRatio utility.

### 3) Staleness and updated-sample gating
- Enforce an airspeed freshness window (XCSoar uses ~30 s):
  - Option A: repository emits valid=false when stale
  - Option B: WindEkfUseCase rejects stale samples by comparing timestamps
- Add updated-sample gating in WindEkfUseCase (mirror Validity.Modified):
  - Track last GPS and airspeed timestamps; skip if unchanged

Suggested files:
- feature/map/.../weather/wind/domain/WindEkfUseCase.kt
- feature/map/.../weather/wind/data/ExternalAirspeedRepository.kt (if handling staleness there)

### 4) VTakeoff gate for EKF
- Wire VTakeoff (polar) into WindEkfUseCase. Until polar wiring exists,
  use 10 m/s fallback (XCSoar default).
- Keep flying-state detector and EKF gates consistent.

Suggested files:
- feature/map/.../weather/wind/domain/WindEkfUseCase.kt
- feature/map/.../glider (add VTakeoff accessor or config)

### 5) Replay IAS/TAS wiring (done)
- IgcParser reads B-record extensions for IAS/TAS if present.
- ReplaySampleEmitter emits AirspeedSample into ReplayAirspeedRepository.
- Reset airspeed repo on replay stop/seek (already handled by replay resets).

Suggested files:
- feature/map/.../replay/IgcParser.kt
- feature/map/.../replay/ReplaySampleEmitter.kt
- feature/map/.../weather/wind/data/ReplayAirspeedRepository.kt
- feature/map/.../replay/IgcReplayController.kt

### 6) Tests
- Unit: airspeed conversion (IAS<->TAS), stale gating, updated-sample gating.
- Wind EKF: verify no output without valid airspeed, output after stride when valid.
- Replay: parse IAS/TAS and emit correct AirspeedSample.

## Open questions
- Which live airspeed source are we targeting first (BLE vario, USB, NMEA)?
- Do we want a user-visible indicator for airspeed source (instrument vs estimate)?
- What staleness window should be used for live airspeed (30 s per XCSoar)?

## Non-goals for this plan
- Phone-only TAS estimation (already covered in docs/TASImplementation.md)
- UI changes to cards/templates unless needed for source visibility
