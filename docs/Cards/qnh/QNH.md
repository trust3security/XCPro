
# QNH Auto Calibration (Release-Grade Design)

This document defines how QNH auto-calibration should work in XC Pro.
It is written for future AI agents and developers to implement without ad-hoc logic.

## Goals
- Produce a correct QNH for the current location when the user taps Auto Cal.
- Work offline and online with a clear priority order.
- Follow MVVM + UDF + SSOT (no UI business logic).
- Avoid blocking calls and time-base violations.
- Provide deterministic behavior suitable for replay and tests.

## Definitions
- QNH: sea level pressure setting used for barometric altitude.
- MSL: mean sea level altitude.
- AGL: height above ground level (baro altitude minus terrain).

## Source Priority (Best to Worst)
1) Online METAR/ATIS QNH (if recent and close enough).
2) Offline sensor method: baro pressure + terrain elevation (SRTM).
3) Offline fallback: baro pressure + GPS altitude (only if terrain missing).
4) Standard atmosphere (1013.25 hPa) as a last resort.

## Architecture (Must Follow)
- UI emits intents only (no calibration logic).
- ViewModel forwards intent to a UseCase.
- UseCase orchestrates calibration and updates repository state.
- Repository is SSOT for QNH value and calibration state.
- Sensor pipeline reads QNH from repository (or is commanded by repository).
- No blocking calls in hot paths or domain logic (no runBlocking).
- Live time bases use monotonic time; replay uses IGC time only.

## Data Model (Suggested)
QnhValue:
- hpa: Double
- source: MANUAL | AUTO_TERRAIN | AUTO_GPS | METAR | STANDARD
- calibratedAtMillis: Long (monotonic for live, IGC time for replay)
- confidence: LOW | MEDIUM | HIGH

QnhCalibrationState:
- Idle
- Collecting(samplesCollected, samplesRequired)
- Succeeded(qnh: QnhValue)
- Failed(reason)
- TimedOut

Repository exposes:
- StateFlow<QnhValue>
- StateFlow<QnhCalibrationState>
- Functions:
  - requestAutoCalibration()
  - setManualQnh(hpa)
  - resetToStandard()

## Auto Cal Behavior (User Tap)
1) UI emits AutoCal intent.
2) ViewModel calls CalibrateQnhUseCase.
3) UseCase starts a calibration session:
   - If online provider available and valid, apply METAR QNH immediately.
   - Else compute using offline method (below).
4) On success: repository applies QNH and publishes new state.
5) On failure/timeout: repository stays unchanged and reports failure.

## Offline Calibration Algorithm (Sensor Only)
Use baro pressure plus known altitude to compute QNH via ISA.
Altitude source priority: terrain elevation (SRTM) then GPS altitude.

### Gating (must be true to collect a sample)
- Not in replay mode.
- GPS fix is valid (isHighAccuracy true).
- GPS horizontal accuracy <= 10 m (tunable).
- Speed <= 3 m/s (tunable; allow 5 m/s for looser gating).
- Baro sample is fresh (monotonic timestamp).
- If using GPS altitude, vertical accuracy must be reasonable.

### Sample collection
- Collect N samples (15 to 30) over 15 to 60 seconds.
- Each sample records:
  - pressure hPa
  - lat/lon
  - optional gps altitude
  - timestamp (monotonic)
- Compute QNH from each sample and aggregate using a trimmed mean or median.

### QNH computation
Given pressure at altitude, compute sea level pressure:
  QNH = P(h) / (T(h)/T0)^(g/(R * L))
Use ISA constants and lapse rate as in BarometricAltitudeCalculator.

### Validity checks
- Reject QNH outside [950, 1050] hPa.
- Reject step changes larger than a threshold unless user approves.
  (Example: 1.0 hPa if not explicitly requested.)
- If terrain is unavailable, fall back to GPS altitude with stricter gating.

### Apply and lock
- Apply QNH once computed; stop auto updates during flight.
- Manual QNH always overrides auto.

## Online Calibration (METAR/ATIS)
- Query nearest station within distance (e.g., 50 km).
- Require freshness (e.g., <= 60 minutes).
- If valid, use METAR QNH as source=METAR.
- If not valid, fall back to offline method.

## Replay Rules
- Auto Cal is disabled in replay mode.
- Use QNH from IGC metadata when available; else standard.

## Implementation Notes
- Current BarometricAltitudeCalculator uses runBlocking for terrain fetch.
  This must be removed for release-grade behavior.
- Terrain elevation should be fetched asynchronously in a repository or
  use case, then passed into calculation as data.
- The calibration session should be owned by a use case or repository,
  not by the UI.

## UI Guidance
- Show current QNH and source.
- Show calibration progress (samples collected).
- Show last calibration age and delta (baro vs GPS if available).
- Do not show raw sensor noise in UI.

## Testing
- Unit tests for QNH computation (ISA formula).
- Unit tests for gating and sample aggregation.
- Integration tests for UseCase success/failure/timeout paths.
- Replay tests to ensure no auto-calibration during replay.

## Suggested Files to Touch (when implementing)
- domain: CalibrateQnhUseCase
- data/repository: QnhRepository (SSOT)
- sensors: apply QNH in FlightDataCalculatorEngine
- ui: MapScreenViewModel intent and state mapping
- ui: Manual QNH dialog shows calibration status


