# Track Up Orientation (MapScreen)

This document explains how map orientation works in XCPro with a focus on Track Up. It is intended for future developers who need to tune or extend the behavior (smoothing, validity gates, UI settings, or new modes).

Note: app/device orientation is locked to portrait in the manifest (`app/src/main/AndroidManifest.xml`). That is separate from map orientation.

See `docs/Orientation/Orientation.md` for shared architecture, data flow, and cross-mode behavior.

![Track Up orientation pipeline](TrackUp.svg)

## User-facing entry points

1) Settings > General > Orientation
   - Screen: `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/OrientationSettingsScreen.kt`
   - Entry in General settings grid: `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/Settings-df.kt`
   - Navigation route: `app/src/main/java/com/example/xcpro/AppNavGraph.kt` (`orientation_settings`)
   - Lets the user set separate modes for Cruise/Final Glide and Thermal/Circling, plus glider vertical offset.

2) Map screen compass widget
   - Toggle control: `feature/map/src/main/java/com/example/xcpro/map/ui/OverlayPanels.kt` and `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenSections.kt`
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
- Orientation contracts: `core/common/src/main/java/com/example/xcpro/common/orientation/OrientationContracts.kt`
- Orientation manager: `feature/map/src/main/java/com/example/xcpro/MapOrientationManager.kt`
- Orientation data source: `feature/map/src/main/java/com/example/xcpro/OrientationDataSource.kt`
- Heading resolver (HEADING_UP): `feature/map/src/main/java/com/example/xcpro/orientation/HeadingResolver.kt`
- Location / camera updates: `feature/map/src/main/java/com/example/xcpro/map/LocationManager.kt`
- Camera effects and clamping: `feature/map/src/main/java/com/example/xcpro/map/MapCameraManager.kt`
- Camera bearing resolver (legacy helper; currently unused in tracking path): `feature/map/src/main/java/com/example/xcpro/map/CameraBearingResolver.kt`
- Location jitter gate: `feature/map/src/main/java/com/example/xcpro/map/MapLocationFilter.kt`
- Icon rotation policy: `feature/map/src/main/java/com/example/xcpro/map/IconHeadingSmoother.kt`
- Icon rendering: `feature/map/src/main/java/com/example/xcpro/map/BlueLocationOverlay.kt`
- Orientation UI: `feature/map/src/main/java/com/example/xcpro/CompassWidget.kt`

## Orientation modes overview

`MapOrientationMode` enum: `NORTH_UP`, `TRACK_UP`, `HEADING_UP`.

- NORTH_UP: map bearing is always 0. Icon rotates to show track.
- TRACK_UP: map bearing follows GPS track. Icon shows drift angle (heading - track).
- HEADING_UP: map bearing follows sensor heading (compass/derived). Icon points up.

## Track Up behavior (core logic)

### 1) Orientation calculation (MapOrientationManager)
File: `feature/map/src/main/java/com/example/xcpro/MapOrientationManager.kt`

When `currentMode == TRACK_UP`:
- `calculateBearing()` uses `OrientationSensorData.track` (GPS track) as the bearing.
- Validity gate:
  - `sensorData.isGPSValid` must be true, and
  - `sensorData.groundSpeed >= minSpeedForTrackMs`.
- If valid, `bearingSource = TRACK` and `isValid = true`.
- If invalid, the manager publishes the *last valid bearing* and sets:
  - `bearingSource = LAST_KNOWN`
  - `isValid` stays true while the last track is still "fresh" (see stale timeout below)

The manager throttles updates to ~15Hz via `sample(BEARING_UPDATE_THROTTLE_MS = 66)`.

Track stale timeout (XCSoar parity):
- For `TRACK_UP`, if no valid track has been seen for ~10 seconds, the bearing is forced to 0
  and `bearingSource = NONE` with `isValid = false`. This mirrors XCSoar's `track_available` expiry.

### 2) Track Up data inputs (OrientationDataSource)
File: `feature/map/src/main/java/com/example/xcpro/OrientationDataSource.kt`

`OrientationDataSource` builds `OrientationSensorData` from:
- Flight data (`RealTimeFlightData`) for track, ground speed, wind, etc.
- Sensor flows (compass + attitude) for heading.

Important details:
- Heading is low-pass smoothed via `smoothBearingTransition()` with `SMOOTHING_FACTOR = 0.3`.
- Heading reliability times out after `HEADING_STALE_THRESHOLD_MS = 1500`.
- A min-speed threshold (default 2 m/s) is stored in prefs but *used in meters/second*.

### 3) Applying Track Up to the map (tracking vs not tracking)

There are two different pipelines for applying bearing to the camera:

A) **Tracking mode (default when return button is not shown)**
- Path: `MapComposeEffects.LocationAndPermissionEffects` -> `LocationManager.updateLocationFromGPS()`.
- Camera bearing now uses `orientationData.bearing` (MapOrientationManager output).
- Location updates still respect the **jitter gate** (see below), but bearing can update
  even if a location update is rejected (bearing-only update).
- There is **no camera bearing clamp in this path**.

B) **Not tracking (user has panned; return button is shown)**
- Path: `MapCameraEffects.OrientationBearingEffect` -> `MapCameraManager.updateBearing()`.
- Bearing is clamped per update (`MAX_BEARING_STEP_DEG = 5`).
- Small changes are ignored (`bearingChanged` threshold ~2 degrees).

### 4) Icon orientation for Track Up
File: `feature/map/src/main/java/com/example/xcpro/map/BlueLocationOverlay.kt`

For Track Up:
- The icon shows **drift**: `heading - track` (XCSoar convention).
- The map rotates beneath it (screen angle = track).
- `OrientationData.headingDeg` is used to compute icon rotation against map bearing.

### Icon rotation policy (visual-only)
- Drift is shown only when `headingValid` is true and speed is above the enter threshold.
- When heading is invalid or below the exit threshold, the icon holds the last stable
  heading or falls back to track-based rotation to avoid jitter (no icon change).
- Rotation uses a time-based clamp and a small deadband for micro-rotations.

## XCSoar parity notes (Track Up)

These notes summarize how XCSoar handles Track Up and where XCPro currently differs.
Sources are the XCSoar repo under `C:\\Users\\Asus\\AndroidStudioProjects\\XCSoar`.

1) Track updates are gated by movement (speed)
- XCSoar only accepts track updates when moving (> 2 m/s).
- Files: `src/Device/Parser.cpp` (`NMEAParser::RMC`), `src/NMEA/Info.hpp` (`MovementDetected`).
 - XCPro defaults to 2 m/s to match XCSoar's movement gate.

2) Track expires if it stops updating
- `track_available` expires after ~10 seconds of no updates.
- When Track Up has no valid track, XCSoar rotates to 0 degrees (north).
- Files: `src/NMEA/Info.cpp` (`Expire`), `src/MapWindow/GlueMapWindowDisplayMode.cpp` (`UpdateScreenAngle`).

3) Rotation is not tied to the position jitter gate
- XCSoar uses `SetLocationLazy` to gate position updates at 0.5 px, but
  screen rotation is computed independently from track validity.
- File: `src/MapWindow/GlueMapWindowDisplayMode.cpp`.

## Smoothing and gating components (important knobs)

These components interact to create or reduce jumpiness:

1) Orientation update throttle
- `MapOrientationManager.BEARING_UPDATE_THROTTLE_MS = 66` (~15Hz)
- File: `feature/map/src/main/java/com/example/xcpro/MapOrientationManager.kt`

2) Heading smoothing (HEADING_UP only)
- `OrientationDataSource.SMOOTHING_FACTOR = 0.3`
- File: `feature/map/src/main/java/com/example/xcpro/OrientationDataSource.kt`

3) GPS motion gate (location jitter)
- `MapLocationFilter` rejects location updates if screen movement < `thresholdPx`.
- Defaults: `MapFeatureFlags.locationJitterThresholdPx = 0.5f` and history size 30.
- File: `feature/map/src/main/java/com/example/xcpro/map/MapLocationFilter.kt`

4) Track-bearing clamp (display)
- `MapPositionController.clampBearingStep()` limits the displayed track to 5 deg/step.
- File: `feature/map/src/main/java/com/example/xcpro/map/MapPositionController.kt`

5) Icon rotation clamp (visual)
- `IconHeadingSmoother` applies a time-based max angular velocity and deadband.
- File: `feature/map/src/main/java/com/example/xcpro/map/IconHeadingSmoother.kt`

6) Camera bearing clamp (only when NOT tracking)
- `MapCameraManager.updateBearing()` limits rotation step to 5 deg/step.
- File: `feature/map/src/main/java/com/example/xcpro/map/MapCameraManager.kt`

7) Compass widget animation
- `CompassWidget` uses `animateFloatAsState` with 300ms tween.
- This is visual only and does not affect the map.

8) User override (temporary freeze)
- `MapOrientationManager.onUserInteraction()` freezes bearing updates for 10s.
- Triggered when the map is moved or rotated (see `MapInitializer` listeners).

9) Track stale timeout (XCSoar parity)
- `MapOrientationManager` forces Track Up bearing to 0 after ~10s without a valid track.
- File: `feature/map/src/main/java/com/example/xcpro/MapOrientationManager.kt`

## Why Track Up can feel jumpy live (current behavior)

These are the main contributors in the current code path:

1) **No camera clamp while tracking**
- `MapCameraManager` clamps rotation, but that path is disabled when tracking.
- `MapPositionController` clamps the icon, not the camera.

2) **GPS update cadence is coarse**
- Track updates at the GPS sampling rate, not at the orientation manager's throttled cadence.
- This can produce larger step changes between updates.

3) **Track noise at valid speeds**
- Track Up still uses GPS track (not a low-pass filtered bearing), so noisy GPS track can
  produce visible rotation jitter at moderate speeds.

4) **Position jitter gate still affects camera target**
- The jitter gate still suppresses *position* updates, so when the gate finally accepts a
  sample the camera target can jump (bearing updates are now decoupled).

## Tuning and extension guide (recommended hotspots)

If you are looking to reduce jumpiness or change Track Up behavior, these are the main levers:

1) **Apply camera bearing smoothing while tracking**
- Option: clamp camera bearing in `MapPositionController.applyAcceptedSample()` similar to the icon clamp.
- Option: reuse `MapCameraManager.updateBearing()` even while tracking.
- Files: `feature/map/src/main/java/com/example/xcpro/map/MapPositionController.kt`, `feature/map/src/main/java/com/example/xcpro/map/MapCameraManager.kt`

2) **Use `orientationData.bearing` instead of raw GPS track for the camera**
- `orientationData` already applies validity gating and last-known behavior.
- Implemented: camera bearing now uses `orientationData.bearing` in tracking mode.

3) **Introduce explicit bearing low-pass for Track Up**
- Right now, Track Up has no smoothing on bearing.
- You can port the `smoothBearingTransition()` concept from `OrientationDataSource` or use `DisplayPoseSmoother`.
- `DisplayPoseSmoother` exists but is unused: `feature/map/src/main/java/com/example/xcpro/map/DisplayPoseSmoother.kt`.

4) **Tune min-speed threshold**
- Preferences: `MapOrientationPreferences.setMinSpeedThreshold()`
- Default is 2 knots (stored internally as m/s).
- No UI currently exposes this; you could add a slider in `OrientationSettingsScreen`.

5) **Relax or tighten the location jitter gate**
- `MapFeatureFlags.locationJitterThresholdPx` (default 0.5 px).
- Smaller threshold = more frequent updates (smoother but more noise).
- Larger threshold = fewer updates (less noise but more jumpiness).

6) **Respect `orientationData.isValid` in LocationManager**
- If invalid, hold the last bearing or skip camera updates.
- This directly addresses low-speed wobble.

7) **Make bearing smoothing configurable**
- `MapOrientationPreferences.KEY_BEARING_SMOOTHING` exists but is currently unused.
- Hook it into the camera or orientation path to allow toggling.

8) **Auto-reset settings are defined but unused**
- `MapOrientationPreferences.KEY_AUTO_RESET_*` exists, but `MapOrientationManager` uses a hardcoded 10s override.
- Consider wiring preferences into the override logic.

9) **Parity change: decouple bearing updates from the location jitter gate**
- Implemented: when `MapLocationFilter.accept(...)` is false, the location is not moved,
  but the camera bearing still updates using `orientationData.bearing` while tracking.
- UX effect: fewer "stuck then jump" rotations at low speed.
- File: `feature/map/src/main/java/com/example/xcpro/map/LocationManager.kt`

## Adding a new orientation mode (checklist)

1) Enum and data contract
- Add mode to `MapOrientationMode` in `core/common/.../OrientationContracts.kt`.

2) Behavior implementation
- Update `MapOrientationManager.calculateBearing()`.
- Update `CameraBearingResolver.resolveCameraBearing()` (legacy helper; only used if you rewire the camera path).
- Update `BlueLocationOverlay.updateLocation()` rotation logic.
- If needed, update `AircraftIconOverlay` (if used).

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
- `LocationManager` (location updates, replay/live logging)
- `MapCameraManager` (camera bearing updates)
- `MapLocationFilter` (accepted/rejected samples)
- `CompassWidget` (UI-level bearing animation)

Tip: while tuning Track Up, compare these signals:
- `OrientationData.bearing` and `isValid`
- `GPSData.bearing` (raw track)
- Camera bearing (`MapLibreMap.cameraPosition.bearing`)
- Whether updates are blocked by the location filter

## Important internal settings (defaults)

From `MapOrientationPreferences` (`map_orientation_prefs`):
- Default orientation: `TRACK_UP`
- Min speed threshold: 2.0 m/s (stored as m/s)
- Glider screen percent: 35 (approx 65% from top)
- Bearing smoothing toggle: defined but unused
- Auto reset toggle/timeout: defined but unused

From `MapOrientationManager`:
- User override timeout: 10 seconds
- Bearing update throttle: 66 ms

From `MapCameraManager` and `MapPositionController`:
- Max bearing step per update: 5 degrees

## Summary for future tuning

If Track Up is still jumpy in live mode, the primary remaining issue is the lack of camera bearing smoothing/clamping while tracking. Track Up now uses `orientationData.bearing` and updates bearing even when the position gate rejects a sample, so focus future tuning on camera smoothing and GPS noise handling. The position jitter gate can still cause target jumps; consider adjusting its threshold or adding bearing smoothing if needed.
