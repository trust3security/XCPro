# Wind Calculation (Phone Sensors)

This document explains how XC Pro calculates wind speed and direction when
only phone sensors are available (GPS, baro, compass/IMU, accelerometer).
It is intended for future AI agents and developers to understand the
wind pipeline and conventions.

Scope:
- Phone-only wind estimation (no external airspeed sensor).
- Wind vector conventions, selection, and UI outputs.
- Map display smoothing is UI-only and does not affect wind math.

Related code (primary):
- feature/map/src/main/java/com/example/xcpro/weather/wind/data/WindSensorFusionRepository.kt
- feature/map/src/main/java/com/example/xcpro/weather/wind/data/WindSensorInputAdapter.kt
- feature/map/src/main/java/com/example/xcpro/weather/wind/domain/CirclingWind.kt
- feature/map/src/main/java/com/example/xcpro/weather/wind/domain/WindStore.kt
- feature/map/src/main/java/com/example/xcpro/weather/wind/domain/WindMeasurementList.kt
- feature/map/src/main/java/com/example/xcpro/weather/wind/model/WindVector.kt
- feature/map/src/main/java/com/example/xcpro/sensors/CirclingDetector.kt
- feature/map/src/main/java/com/example/xcpro/map/MapScreenObservers.kt (wrapped by FlightDataUiAdapter)
- feature/map/src/main/java/com/example/xcpro/map/FlightDataManager.kt
- feature/map/src/main/java/com/example/xcpro/map/WindIndicatorState.kt
- feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt
- feature/variometer/src/main/java/com/example/ui1/UIVariometer.kt

## Data sources (phone-only)

Wind inputs are adapted from phone sensors:
- GPS: ground speed (m/s) and track (radians) from location bearing.
- Barometer: pressure altitude (m).
- Attitude or compass: heading (deg) when available.
- Accelerometer: g-load (used to reject samples during high G).
- Airspeed: optional (external); absent for phone-only.

Implementation:
- WindSensorInputAdapter converts SensorDataSource flows into GpsSample,
  PressureSample, HeadingSample, GLoadSample.
  - Heading: attitude heading when reliable; else compass if not unreliable; else null.
  - G-load: accel magnitude / g, smoothed (tau ~200 ms) when samples are reliable.
  - Pressure altitude uses standard atmosphere (QNH 1013.25), not calibrated QNH.
  - GPS samples are ignored if track or ground speed is non-finite, or if clockMillis
    is not strictly increasing (prevents duplicates).
  - Time bases:
    - Inputs carry timestampMillis (wall/replay) and clockMillis (monotonic or replay clock).
    - Wind math (deltas, freshness, staleness) uses clockMillis.
    - Manual/external wind overrides stay in wall time; never compare across bases.

Replay-specific notes:
- Replay emits baro at high rate (20 ms) but GPS at 1 Hz.
- WindSensorFusionRepository processes only new GPS clockMillis to avoid
  duplicate samples overwhelming the circling estimator.
- ReplaySampleEmitter derives GPS bearing and speed from the last GPS point
  (1 Hz) and reuses that for compass/heading between GPS ticks.
  This keeps circling detection and wind estimation consistent with live GPS.

Display-only note:
- Any map icon smoothing, heading gating, or visual interpolation happens
  after SSOT data is produced and does not feed back into wind calculation.

## Wind vector conventions

WindVector is expressed as the velocity of the airmass (direction TO).
Components:
- east, north (m/s).

Derived directions:
- directionToRad: atan2(east, north)
- directionFromRad: directionToRad + PI
- directionFromDeg: degrees(directionFromRad)

WindVector.fromSpeedAndBearing(speed, directionFromRad) expects a
meteorological bearing (direction FROM) and converts to east/north.

See WindVector.kt.

## Phone-only wind estimation: circling method

With only phone sensors, wind is computed from GPS track and ground speed
while circling. This is the primary (and often only) auto wind source.

Flow:
1) WindSensorFusionRepository observes live sensor inputs.
2) CirclingDetector uses GPS track history to decide if the glider is circling.
3) While circling, CirclingWind accumulates samples of:
   - trackRad (radians, GPS track)
   - groundSpeed (m/s)
4) When a full circle is detected, CirclingWind estimates wind.

Core estimation (from CirclingWind.kt):
- The maximum and minimum ground speeds around the circle are identified.
- Wind speed magnitude:
  windSpeed = (maxGroundSpeed - minGroundSpeed) / 2
- Wind direction (FROM):
  windFrom = track_at_maxGroundSpeed + PI
- WindVector is built from (windSpeed, windFrom).

Quality and validity gates:
- Requires a full circle and a minimum number of samples.
- Rejects if sample spacing is too wide or wind speed is too large.
- Quality is derived from residual error and number of circles.

Notes:
- This method depends on sustained circling; straight flight does not
  generate new wind in phone-only mode.
- CirclingDetector specifics:
  - Turn rate threshold: 4 deg/s (smoothed, low-pass alpha 0.3).
  - Enter circling after 15 s of sustained turning; exit after 10 s.
  - Time-warp detection resets the detector.

## EKF method (not used in phone-only)

WindEkfUseCase requires true airspeed (AirspeedSample.trueMs).
If airspeed is missing or invalid, EKF updates are rejected.
Therefore, with phone-only sensors, EKF does not produce wind.
When airspeed is present, EKF gating mirrors XCSoar:
- TAS must be valid and >= 10 m/s (default takeoff speed).
- Reject duplicates or time warps in GPS or airspeed timestamps.
- Reject when circling; apply a 3 s blackout after circling/turning events.
- Reject when turn rate exceeds 20 deg/s.
- Reject when |g-load - 1.0| > 0.3 and the g-load sample is fresh (<= 500 ms).
- EKF emits a wind sample every 10 updates; quality rises with sample count
  (1,2,3,4 at 30/120/600 samples).
- EKF samples are suppressed for 5 s after the last circling wind update.
- EKF also requires airspeed samples to include a valid clockMillis (monotonic/replay).
  Samples without a clock are dropped to match XCSoar-style gating.

## Wind store and selection

Wind estimates are stored and weighted over time and altitude:
- WindStore stores measurements and calls WindMeasurementList.getWeightedWind.
- Weighting factors include quality, altitude delta, and time age.
- Measurements older than 1 hour are ignored by weighting.
- WindStore only re-evaluates when a new measurement arrives or altitude changes
  by more than 100 m.
- Circling wind measurements are stored like any other measurements and are
  weighted over time/altitude to smooth across multiple circles.
- Override measurements use quality=6 to take precedence in weighting.

Selection rules (WindSelectionUseCase):
- Auto wind is used if it is newer than manual wind.
- Else external wind is used (if available).
- Else manual wind.
- Else auto (fallback).

With phone-only sensors, the auto source is usually CIRCLING.

Staleness:
- Auto wind is only selected if updated within the last 1 hour.
- If no selection is made and the last update is older than 1 hour,
  wind state is marked stale.
  - Auto freshness is based on clockMillis; manual/external timestamps remain wall time.

## Headwind and crosswind

When a wind vector is selected, WindSensorFusionRepository computes
headwind and crosswind relative to heading:

Given:
- headingRad = radians(headingDeg)
- wind vector components (east, north)

Compute:
- dot = wind.east * sin(headingRad) + wind.north * cos(headingRad)
- headwind = -dot
- crosswind = wind.east * cos(headingRad) - wind.north * sin(headingRad)

Heading source:
- Attitude heading (if reliable) or compass (if not unreliable),
  otherwise GPS track.

## UI outputs

FlightDataUiAdapter / MapScreenObservers applies wind state to RealTimeFlightData:
- windSpeed: vector.speed (m/s)
- windDirection: directionFromDeg normalized to [0, 360)
- windQuality: quality (1-5)
- windSource: enum name (e.g., CIRCLING)
- windHeadwind, windCrosswind
- windAgeSeconds

If no valid wind is available or stale, UI shows "NO WIND".

## Wind arrow UI plumbing (screen-relative)

The variometer wind arrow is driven by a two-stage UI flow to avoid
derived state being cached inside Compose:

1) FlightDataManager.windIndicatorStateFlow
   - Holds the last known wind-from direction (degrees) and validity.
   - Updates only when wind is valid (quality > 0 and speed > 0.5 m/s).
   - When wind becomes invalid, it keeps the last direction but marks isValid=false.
   - Source: feature/map/src/main/java/com/example/xcpro/map/FlightDataManager.kt

2) MapScreenViewModel.windArrowState
   - Combines WindIndicatorState with orientationFlow.
   - Produces screen-relative direction: windFromDeg - mapBearing.
   - Source: feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt

UI rendering (UIVariometer):
- Takes windDirectionScreenDeg (already screen-relative) and windIsValid.
- Green triangle when valid; red triangle when invalid (stale/unknown).
- When no direction has ever been computed, it defaults to 0 deg (top-center).
