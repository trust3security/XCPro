
# Heading Up Orientation (MapScreen)

This document explains how map orientation works in XCPro with a focus on Heading Up. It is intended for future developers who need to tune or extend the behavior (sensor inputs, validity gates, smoothing, UI settings, or new modes).

Note: app/device orientation is locked to portrait in the manifest (`app/src/main/AndroidManifest.xml`). That is separate from map orientation.

See `docs/Orientation/Orientation.md` for shared architecture, data flow, and cross-mode behavior.

![Heading Up orientation pipeline](HeadingUp.svg)

## User-facing entry points

1) Settings > General > Orientation
   - Screen: `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/OrientationSettingsScreen.kt`
   - Entry in General settings grid: `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/Settings-df.kt`
   - Navigation route: `app/src/main/java/com/trust3/xcpro/AppNavGraph.kt` (`orientation_settings`)
   - Lets the user set separate modes for Cruise/Final Glide and Thermal/Circling, plus glider vertical offset.

2) Map screen compass widget
   - Toggle control: `feature/map/src/main/java/com/trust3/xcpro/map/ui/OverlayPanels.kt` and `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenSections.kt`
   - Cycles: NORTH_UP -> TRACK_UP -> HEADING_UP -> NORTH_UP
   - This updates the *active profile* (cruise or circling) in preferences via `MapOrientationManager.setOrientationMode`.

## High-level data flow (live)

```
UnifiedSensorManager (compass, attitude)        FlightDataManager (RealTimeFlightData)
           |                                                 |
           v                                                 v
OrientationDataSource  <---- updateFromFlightData() ---- MapOrientationManager
           |                                                 |
           v                                                 v
     OrientationSensorData                         OrientationData (mode, bearing, valid, source)
           |                                                 |
           +--------------------- MapScreenRoot collects ----+
                                     |
                                     v
                 MapComposeEffects + LocationManager / MapCameraEffects
                                     |
                                     v
                      MapLibre camera bearing + BlueLocationOverlay icon
```

Key files:
- Orientation contracts: `core/common/src/main/java/com/trust3/xcpro/common/orientation/OrientationContracts.kt`
- Orientation manager: `feature/map/src/main/java/com/trust3/xcpro/MapOrientationManager.kt`
- Orientation data source: `feature/map/src/main/java/com/trust3/xcpro/OrientationDataSource.kt`
- Heading resolver (HEADING_UP): `feature/map/src/main/java/com/trust3/xcpro/orientation/HeadingResolver.kt`
- Location / camera updates: `feature/map/src/main/java/com/trust3/xcpro/map/LocationManager.kt`
- Camera effects and clamping: `feature/map/src/main/java/com/trust3/xcpro/map/MapCameraManager.kt`
- Location jitter gate: `feature/map/src/main/java/com/trust3/xcpro/map/MapLocationFilter.kt`
- Icon rotation policy: `feature/map/src/main/java/com/trust3/xcpro/map/IconHeadingSmoother.kt`
- Icon rendering: `feature/map/src/main/java/com/trust3/xcpro/map/BlueLocationOverlay.kt`
- Orientation UI: `feature/map/src/main/java/com/trust3/xcpro/CompassWidget.kt`

## Orientation modes overview

`MapOrientationMode` enum: `NORTH_UP`, `TRACK_UP`, `HEADING_UP`.

- NORTH_UP: map bearing is always 0. Icon rotates to show track.
- TRACK_UP: map bearing follows GPS track. Icon shows drift angle (heading - track).
- HEADING_UP: map bearing follows aircraft heading. Icon points up.

## Heading Up behavior (core logic)

### 1) Orientation calculation (MapOrientationManager)
File: `feature/map/src/main/java/com/trust3/xcpro/MapOrientationManager.kt`

When `currentMode == HEADING_UP`:
- `calculateBearing()` uses `OrientationSensorData.headingSolution`.
- If the heading solution is valid, `bearingSource` is one of `COMPASS`, `WIND`, or `TRACK` and `isValid = true`.
- If invalid, the manager publishes the *last valid bearing* and sets:
  - `bearingSource = LAST_KNOWN`
  - `isValid = false`

The manager throttles updates to ~15Hz via `sample(BEARING_UPDATE_THROTTLE_MS = 66)`.

Heading Up stale timeout (XCSoar parity):
- If no valid heading has been seen for 5 seconds, Heading Up resets to north.
- While within the 5s window, the last valid bearing is held and `isValid` remains false.

### 2) Heading inputs and smoothing (OrientationDataSource)
File: `feature/map/src/main/java/com/trust3/xcpro/OrientationDataSource.kt`

`OrientationDataSource` builds `OrientationSensorData` from:
- Flight data (`RealTimeFlightData`) for track, ground speed, wind, etc.
- Sensor flows (compass + attitude) for heading.

Heading pipeline details:
- The source can be **magnetometer** (`CompassData`) or **rotation vector attitude** (`AttitudeData`).
- Each source is filtered independently (separate compass and attitude filters).
- Device heading is ignored unless `isFlying == true` or ground speed >= min-speed threshold.
- A single **active heading source** is chosen:
  - Prefer rotation vector when reliable and stable.
  - Fall back to magnetometer only if no rotation-vector heading has been seen (compass-only devices).
- A small hysteresis window (`SOURCE_SWITCH_STABLE_MS = 500`) requires stability before switching.
- Heading is low-pass smoothed via `smoothBearingTransition()` with `SMOOTHING_FACTOR = 0.3`.
- Updates are rate-limited to 20Hz (`HEADING_UPDATE_INTERVAL_MS = 50`).
- Reliability is per-source; if no reliable updates arrive within
  `HEADING_STALE_THRESHOLD_MS = 1500`, that source is considered stale.

### 3) Heading resolver (HEADING_UP fallbacks)
File: `feature/map/src/main/java/com/trust3/xcpro/orientation/HeadingResolver.kt`

The resolver chooses a heading in priority order:
1) **Compass / attitude heading** when reliable (`BearingSource.COMPASS`).
2) **Wind-derived heading** when track is valid, speed >= min threshold, wind is present,
   and the aircraft is considered flying (`isFlying == true`) (`BearingSource.WIND`).
   - Uses air vector = ground track + wind (wind is stored as "from").
3) **Track fallback** when track is valid and speed >= min threshold (`BearingSource.TRACK`).
4) **Track value but invalid** when track exists but speed is below the threshold (isValid = false).
5) Otherwise, return no valid heading (`BearingSource.NONE`).

The min-speed gate uses the same threshold as Track Up (`MapOrientationPreferences.getMinSpeedThreshold()`), updated via `MapOrientationManager`.

### 4) Applying Heading Up to the map (tracking vs not tracking)

There are two different pipelines for applying bearing to the camera:

A) **Tracking mode (default when return button is not shown)**
- Path: `MapComposeEffects.LocationAndPermissionEffects` -> `LocationManager.updateLocationFromGPS()`.
- Camera bearing uses `orientationData.bearing` (heading solution).
- Location updates still respect the **jitter gate** (see below), but bearing can update
  even if a location update is rejected (bearing-only update).
- There is **no camera bearing clamp in this path**.

B) **Not tracking (user has panned; return button is shown)**
- Path: `MapCameraEffects.OrientationBearingEffect` -> `MapCameraManager.updateBearing()`.
- Bearing is clamped per update (`MAX_BEARING_STEP_DEG = 5`).
- Small changes are ignored (`bearingChanged` threshold ~2 degrees).

### 5) Icon orientation for Heading Up
File: `feature/map/src/main/java/com/trust3/xcpro/map/BlueLocationOverlay.kt`

For Heading Up:
- The map rotates to heading.
- The icon points **up** (rotation = `heading - mapBearing` = 0 in Heading Up).
- Drift is shown in **Track Up** (`heading - track`).
- `OrientationData` now carries `headingDeg/headingValid/headingSource` separately from map bearing.

### Icon rotation policy (visual-only)
- Icon rotation respects `headingValid` with a speed hysteresis gate derived from
  `MapOrientationPreferences.getMinSpeedThreshold()`.
- When heading is invalid or below the exit threshold, the icon holds the last stable
  heading or falls back to the current map bearing (keeps the icon pointing up).
- Rotation is smoothed with a time-based clamp and a small deadband to prevent jitter.
- This is UI-only and does not alter SSOT or sensor pipelines.

## XCSoar parity notes (Heading Up)

These notes summarize how XCSoar handles Heading Up and where XCPro currently differs.
Sources are the XCSoar repo under `C:\Users\Asus\AndroidStudioProjects\XCSoar`.

1) Heading Up uses attitude heading
- XCSoar sets screen angle to `basic.attitude.heading` when `heading_available` is true,
  otherwise it falls back to north.
- File: `src/MapWindow/GlueMapWindowDisplayMode.cpp` (`UpdateScreenAngle`).

2) Heading validity timeout is longer
- `heading_available` expires after 5 seconds without updates.
- File: `src/NMEA/Attitude.cpp` (`AttitudeState::Expire`).

3) XCSoar computes heading from wind+track when no compass is available
- If no compass heading exists, XCSoar derives heading from track + wind when flying,
  otherwise it uses track. This is written into `basic.attitude.heading`.
- File: `src/Computer/BasicComputer.cpp` (`ComputeHeading`).
- Track is only updated when moving (> 2 m/s): `src/Device/Parser.cpp` (`RMC`) and
  `src/NMEA/Info.hpp` (`MovementDetected`).

4) Icon behavior aligned
- XCSoar draws aircraft rotation as `basic.attitude.heading - screen_angle`.
  That means Heading Up -> icon points up, Track Up -> icon shows drift.
- File: `src/MapWindow/MapWindowRender.cpp`.
- XCPro now matches this convention (Heading Up icon points up; Track Up shows drift).

5) Android internal sensor heading is not wired in XCSoar C++
- `OnRotationSensor` and `OnMagneticFieldSensor` are TODO in
  `src/Device/AndroidSensors.cpp`, so Heading Up on Android often relies on
  external heading or the track-based fallback.

6) XCPro stability and parity adjustments
- Device heading is ignored when not flying or below min-speed threshold.
- If a rotation-vector heading has ever been seen, compass fallback is disabled
  to avoid source fighting (AHRS-first behavior).

7) No screen-angle clamp in XCSoar
- XCSoar applies heading/track directly to the screen angle (no rate clamp).
- The only map-level jitter gate is SetLocationLazy (0.5 px location threshold).
- XCPro already keeps tracking-mode camera updates unclamped; adding a clamp
  would be a deliberate divergence from XCSoar parity.

## XCSoar parity implementation (items 1-3)

Status: Implemented 2026-01-09.

### 1) Heading Up stale timeout (5s -> fall back to north)

Implementation:
- Added `HEADING_STALE_TIMEOUT_MS = 5000` in `MapOrientationManager`.
- Track `lastValidHeadingTime` separately from `lastValidTrackTime`.
  - Updated only when `currentMode == HEADING_UP` and `headingSolution.isValid == true`.
- In `updateOrientation()`:
  - If Heading Up is invalid and `now - lastValidHeadingTime > HEADING_STALE_TIMEOUT_MS`,
    set `finalBearing = 0.0`, `finalSource = BearingSource.NONE`, `finalValid = false`.
  - Else keep `lastValidBearing` with `BearingSource.LAST_KNOWN`.
- Track Up stale timeout remains 10s.

Files:
- `feature/map/src/main/java/com/trust3/xcpro/MapOrientationManager.kt`

### 2) Align movement gate to 2 m/s (XCSoar threshold)

Implementation:
- Default `minSpeedThreshold` is now **2.0 m/s** in `MapOrientationPreferences`.
- Migration bumps stored values that equal the old default (2 kt in m/s) to 2.0 m/s.
- Threshold is used for:
  - Track Up validity gate (`MapOrientationManager`).
  - Heading Up track/wind fallback (`HeadingResolver`).

Files:
- `feature/map/src/main/java/com/trust3/xcpro/MapOrientationPreferences.kt`

### 3) Gate wind-derived heading by "isFlying"

Implementation:
- Added `isFlying: Boolean` to `HeadingResolverInput`.
- `OrientationDataSource` collects `FlightStateSource.flightState` and forwards `isFlying`.
- `HeadingResolver` only uses wind-derived heading when:
  - `isFlying == true`, **and**
  - `groundSpeedMs >= minTrackSpeedMs`, **and**
  - wind speed is available (> 0.1 m/s).
- When not flying, wind-derived heading is skipped and the resolver falls back to track.

Wiring:
- `VarioServiceManager.flightStateSource` is exposed.
- `MapScreenViewModel` passes it into `MapOrientationManager`.
- `MapOrientationManager` passes it to `OrientationDataSource`.

Files:
- `feature/map/src/main/java/com/trust3/xcpro/orientation/HeadingResolver.kt`
- `feature/map/src/main/java/com/trust3/xcpro/OrientationDataSource.kt`
- `feature/map/src/main/java/com/trust3/xcpro/MapOrientationManager.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenViewModel.kt`
- `feature/map/src/main/java/com/trust3/xcpro/vario/VarioServiceManager.kt`

### Acceptance criteria (all 3 items)
- Heading Up resets to north after ~5s of no valid heading.
- Track and heading fallback only update when speed >= 2 m/s by default.
- Wind-derived heading only influences Heading Up when `isFlying == true`.
- No regressions in Track Up (10s stale timeout remains).

## Smoothing and gating components (important knobs)

1) Orientation update throttle
- `MapOrientationManager.BEARING_UPDATE_THROTTLE_MS = 66` (~15Hz)

2) Heading smoothing (HEADING_UP only)
- `OrientationDataSource.SMOOTHING_FACTOR = 0.3`

3) Heading update rate and stale timeout
- `HEADING_UPDATE_INTERVAL_MS = 50` (20Hz cap)
- `HEADING_STALE_THRESHOLD_MS = 1500`

4) Heading Up stale timeout (map rotation)
- `MapOrientationManager.HEADING_STALE_TIMEOUT_MS = 5000`

5) Heading source gating (stability)
- Device heading is ignored unless `isFlying == true` or ground speed >= min-speed threshold.
- If a rotation-vector heading has ever been seen, compass fallback is disabled (AHRS-first).
- Debug option: `MapFeatureFlags.allowHeadingWhileStationary` can bypass the gate for bench testing (default false).

6) GPS motion gate (location jitter)
- `MapLocationFilter` rejects location updates if screen movement < `thresholdPx`.
- Defaults: `MapFeatureFlags.locationJitterThresholdPx = 0.5f` and history size 30.

7) Track bearing clamp (display)
- `MapPositionController.clampBearingStep()` limits the displayed track to 5 deg/step.

8) Icon rotation clamp (visual)
- `IconHeadingSmoother` applies a time-based max angular velocity and deadband.

9) Camera bearing clamp (only when NOT tracking)
- `MapCameraManager.updateBearing()` limits rotation step to 5 deg/step.

10) User override (temporary freeze)
- `MapOrientationManager.onUserInteraction()` freezes bearing updates for 10s.
- Triggered when the map is moved or rotated (see `MapInitializer` listeners).

11) Min-speed threshold for track fallback
- Preferences: `MapOrientationPreferences.getMinSpeedThreshold()` (default 2 m/s).

## Why Heading Up can feel wrong or jumpy (current behavior)

1) **Uncompensated magnetometer heading**
- `SensorRegistry` computes heading directly from magnetic field X/Y without tilt compensation.
- If the device is not level, heading can be significantly wrong.

2) **Source switching when reliability flaps**
- If one source repeatedly becomes stale and recovers, the active source can still switch.
- The 500 ms hysteresis reduces this, but stale sensors can still cause changes.

3) **No camera clamp while tracking**
- In tracking mode, camera bearing updates are applied directly (no step clamp).
- This matches XCSoar; adding a clamp would intentionally diverge.

4) **Low-speed track fallback can be noisy**
- If compass is unreliable and track is just above the min-speed threshold, the heading
  becomes track-derived and can jitter with GPS noise.

5) **Wind-based heading depends on wind quality**
- HeadingResolver uses wind when `windSpeed > 0.1` and `isFlying == true`, but does not check
  `windQuality` or `windAgeSeconds`, so stale wind can distort heading.

## Jitter diagnosis (stationary test)

If the device is stationary on a table and Heading Up still jumps, the cause is almost
always the **heading sensor pipeline**, not GPS track or wind.

Recommended debug steps:
1) Run with `adb logcat -s JITTER`.
2) Keep the phone flat and stationary in HEADING_UP.
3) When jumps occur, inspect the log fields:
   - `compass` vs `att` headings (if they disagree by large angles, inputs are fighting).
   - `src` and `input` (if input=ATTITUDE but src=COMPASS, the pipeline is mixing sources).
   - `track=0.0 gs=0.00` (confirms no track-based fallback).

The current jitter logs show that compass and rotation-vector headings can be both
reliable but disagreeing, which causes sudden jumps when the shared filtered heading
is pulled between them.

See `docs/Orientation/HeadingUpRefactor.md` for the implementation plan to remove
source fighting without breaking architecture.

## Tuning and extension guide (recommended hotspots)

If you are looking to improve Heading Up stability or parity, these are the main levers:

1) **Use a tilt-compensated heading source**
- Prefer rotation vector heading over raw magnetometer, or compute azimuth from
  accelerometer + magnetometer (SensorManager.getRotationMatrix / getOrientation).
- This is the biggest correctness win for non-level mounts.
- Files: `feature/map/src/main/java/com/trust3/xcpro/sensors/SensorRegistry.kt`,
  `feature/map/src/main/java/com/trust3/xcpro/sensors/OrientationProcessor.kt`.

2) **Pick a primary heading source (avoid source fighting)**
- If rotation vector is available and fresh, ignore magnetometer updates.
- Alternatively, fuse them and expose a single "attitude heading" stream.

3) **Gate wind-based heading by quality/age**
- Use `windQuality` and `windAgeSeconds` from `RealTimeFlightData` to avoid stale wind.
- File: `feature/map/src/main/java/com/trust3/xcpro/OrientationDataSource.kt`.

4) **Heading stale timeout (aligned)**
- Heading Up now uses a 5s stale timeout (XCSoar parity).
- Adjust only if you want faster reset or longer hold.

5) **Optional: add camera smoothing while tracking**
- If live Heading Up is still jumpy, clamp or smooth camera bearing in tracking mode.
- Trade-off: you add lag vs true heading.

6) **Expose min-speed threshold in UI**
- It is stored in preferences but not exposed in `OrientationSettingsScreen`.

## Adding a new orientation mode (checklist)

1) Enum and data contract
- Add mode to `MapOrientationMode` in `core/common/.../OrientationContracts.kt`.

2) Behavior implementation
- Update `MapOrientationManager.calculateBearing()`.
- Update `HeadingResolver` (if heading-derived behavior is needed).
- Update `BlueLocationOverlay.updateLocation()` rotation logic.

3) UI wiring
- Add mode to `OrientationSettingsScreen` list and descriptions.
- Update compass toggle cycle in `MapScreenSections` and `CompassPanel`.
- Update `CompassWidget` badges if needed.

4) Tests and migration
- Add tests or update `MapOrientationPreferencesTest` if defaults change.
- Consider migration in `MapOrientationPreferences.migrateLegacyOrientationMode()`.

## Debugging aids

Relevant log tags:
- `MapOrientationManager` (orientation updates and mode changes)
- `OrientationDataSource` (sensor-derived heading and track snapshots)
- `HeadingResolver` (source selection)
- `LocationManager` (location updates, replay/live logging)
- `MapCameraManager` (camera bearing updates)
- `MapLocationFilter` (accepted/rejected samples)
- `CompassWidget` (UI-level bearing animation)

Tip: while tuning Heading Up, compare these signals:
- `OrientationData.bearing` and `isValid`
- Raw compass heading and rotation vector heading (if available)
- GPS track and ground speed
- Camera bearing (`MapLibreMap.cameraPosition.bearing`)

## Summary for future tuning

Heading Up is only as good as the heading source. The current implementation is functional but can be wrong when the device is tilted and can jitter when compass and rotation vector sources disagree. If Heading Up feels unstable in live use, prioritize a tilt-compensated heading source and a single authoritative heading stream before adding camera smoothing.

