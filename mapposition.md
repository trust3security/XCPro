# MapPosition

This document explains how the map and the glider (aircraft) position are updated.
It is focused on display behavior (UI only), not the sensor fusion pipeline.

## Design goals
- Smooth visual motion between GPS/replay fixes.
- Respect SSOT and UDF (no sensor access in ViewModels, no duplicated state).
- Correct time base handling for live vs replay.
- Avoid location logging in release builds.

## Source of truth
- The only authoritative flight data comes from `FlightDataRepository`.
- The map consumes GPS fixes through `MapScreenViewModel.mapLocation`, which reads
  `FlightDataRepository.flightData` and exposes `GPSData?` to the UI.
- Replay/live source gating happens inside the repository and is honored by the UI.

## High level flow
1) Sensors / replay -> `FlightDataRepository` (SSOT) -> `MapScreenViewModel.mapLocation`.
2) Compose effects call `LocationManager.updateLocationFromGPS(...)` for live fixes
   or `updateLocationFromFlightData(...)` for replay fixes.
3) Each fix is converted into a `DisplayPoseSmoother.RawFix` and pushed into the
   display smoother. This does not modify sensor data; it is visual only.
4) A frame loop (`withFrameNanos`) calls `LocationManager.onDisplayFrame()` at
   display cadence (30-60 Hz) to compute a smoothed display pose and apply it.

## Display smoothing
- `DisplayPoseSmoother` performs:
  - short dead reckoning between fixes,
  - low-pass smoothing for position and track,
  - accuracy-aware damping (including bearing accuracy when available),
  - display-only outlier clamping (limits sudden GNSS jumps without freezing),
  - long-gap re-anchoring so foreground resume/doze recovery snaps to the latest
    fresh fix instead of walking from a stale pre-background pose.
- Smoothing is applied only to the UI marker/camera. Sensor fusion remains unchanged.

### Live smoothing profiles
- The display smoother supports profiles to trade precision vs jitter:
  - `SMOOTH` (current default)
  - `RESPONSIVE` (lower smoothing constants, faster response)
- Profile selection is UI-only state owned by `MapStateStore`.

### Adaptive smoothing (live)
- An adaptive policy scales the active profile by speed + GPS accuracy.
- Higher speed + good accuracy -> lower smoothing (less lag).
- Poor accuracy -> more smoothing and shorter prediction (less overshoot).
- This is visual-only; raw fixes and navigation remain untouched.

### Replay raw pose mode (visual parity)
- During TAS replay, we optionally render the marker using the raw replay fix
  (no smoothing/prediction) to align UI with navigation events.
- This is UI-only and controlled via `MapStateStore` display pose mode.
- Default remains smoothed; raw replay is gated by a feature flag and replay session.

## Time base rules (live vs replay)
- Live: use monotonic time when available (`GPSData.monotonicTimestampMillis`).
  If monotonic is missing, use wall time consistently.
- Replay: use IGC timestamps as the simulation clock.
- `LocationManager.DisplayClock` maintains a display-time clock in the same
  time base as the most recent fix and advances replay time using the session
  speed multiplier.

## Live GPS cadence (sensor layer)
- Live GPS is requested at a slow cadence by default (1 Hz).
- When the flight state reports `isFlying == true`, cadence switches to fast
  (5 Hz) to reduce visible jumps at high speed.
- Cadence changes are driven by `VarioServiceManager` only; UI never touches sensors.
- Replay is unaffected (live sensors are stopped during replay).

## Marker (glider icon) updates
- The marker is updated every display frame using the smoothed pose.
- Heading/rotation uses the latest `OrientationData` from `MapOrientationManager`.
- Track bearing is still clamped in `MapPositionController` to prevent abrupt jumps.
- Icon rotation is computed by `IconHeadingSmoother` (heading-valid gate, speed hysteresis,
  time-based clamp, and deadband).
- Bearing accuracy (when present) increases the icon deadband and reduces max turn rate only
  when using track-based targets (heading invalid). Heading-valid rotation ignores GPS bearing
  accuracy to avoid penalizing good compass/attitude data.
- Replay fixes currently provide no bearing/speed accuracy; null values keep default behavior.

### Icon rotation guardrails
- Visual-only smoothing for the icon; no SSOT mutation.
- No sensor pipeline changes and no ViewModel changes.
- Uses display clock time base (live monotonic or replay clock).
- No new polling loops; only the existing frame ticker.
- No new location logs in release builds.

### Display guardrails (jitter prevention)
- Outlier clamp: if a fix jumps unrealistically far in a short time, the target is
  clamped along its direction using accuracy/speed-derived limits.
- Long-gap re-anchor: if the next accepted fix arrives after a large timestamp gap,
  the smoother resets visual continuity and treats that fix as the new anchor.
- Prediction is gated by speed + speed accuracy; poor speed accuracy disables
  dead reckoning to avoid wobble while stationary or walking.
- Bearing accuracy scales prediction horizon to reduce over-shoot on noisy headings.
- Steady-state rendered-frame suppression is screen-space based: the render coordinator
  skips frames only when ownship movement stays below the current jitter threshold in
  pixels. If map projection metrics are unavailable, it falls back to a small meter floor.

## Camera updates
- The camera is updated only when tracking is enabled and the return button is not shown.
- Updates are gated by:
  - minimum time interval (`CAMERA_MIN_UPDATE_INTERVAL_MS`),
  - minimum screen movement (`MapLocationFilter` threshold),
  - significant bearing change (`CAMERA_BEARING_EPS_DEG`).
- Camera updates use `animateCamera` with a short duration (`CAMERA_ANIMATION_MS`).
- After each camera update, the gate resets to the current location to prevent
  immediate redundant updates caused by bearing changes.

## Initial centering
- The first valid pose triggers an initial camera center at a sensible zoom.
- After initial centering, user zoom is preserved.

## Key files
- `feature/map/src/main/java/com/example/xcpro/map/LocationManager.kt`
- `feature/map/src/main/java/com/example/xcpro/map/DisplayPoseSmoother.kt`
- `feature/map/src/main/java/com/example/xcpro/map/MapPositionController.kt`
- `feature/map/src/main/java/com/example/xcpro/map/IconHeadingSmoother.kt`
- `feature/map/src/main/java/com/example/xcpro/map/MapLocationFilter.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/effects/MapComposeEffects.kt`
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt`

## Compliance notes
- No MapLibre types appear in ViewModels.
- Display smoothing is UI-only and does not alter SSOT data.
- Location logs are debug-only (`BuildConfig.DEBUG`).
- Time base rules are preserved for live and replay.


## Sequence (ASCII)

```
Live sensors / Replay
        |
        v
FlightDataRepository (SSOT)
        |
        v
MapScreenViewModel.mapLocation (GPSData?)
        |
        v
LocationManager.updateLocationFromGPS / updateLocationFromFlightData
        |
        v
DisplayPoseSmoother (visual-only)
        |
        v   (frame ticker @ 30-60 Hz)
LocationManager.onDisplayFrame()
        |\
        | \-> MapPositionController.updateOverlay (marker)
        |
         \-> MapPositionController.updateCamera (gated + animated)
        v
MapLibre MapView
```

## Flow Table

| Stage | Owner | Input | Output | Notes |
|---|---|---|---|---|
| Sensor/Replay | Data sources | GNSS/IGC | `CompleteFlightData` | Live or replay feeds | 
| SSOT | `FlightDataRepository` | `CompleteFlightData` | `StateFlow<CompleteFlightData?>` | Source gating (LIVE/REPLAY) |
| ViewModel | `MapScreenViewModel` | `flightData` | `StateFlow<GPSData?>` | No sensor access |
| UI Effects | `MapComposeEffects` | `GPSData?` | Raw fixes pushed | `LaunchedEffect` only |
| Smoother | `DisplayPoseSmoother` | Raw fixes | Display pose | Dead-reckon + low-pass |
| Frame Loop | `LocationManager` | Display pose | Overlay + Camera updates | Gated + animated |

## Tuning (Display Smoothness)

Use these knobs to tune feel without breaking SSOT or fusion:

- `DisplayPoseSmoother` constants:
  - `POS_SMOOTH_MS`, `HEADING_SMOOTH_MS`: lower = more responsive, higher = smoother.
  - `DEAD_RECKON_LIMIT_MS`: limit for forward prediction.
  - `minSpeedForHeadingMs`: heading smoothing gate at low speed (derived from
    `MapOrientationPreferences.getMinSpeedThreshold()`).
- `LocationManager` camera gating:
  - `CAMERA_MIN_UPDATE_INTERVAL_MS`: minimum time between camera updates.
  - `CAMERA_ANIMATION_MS`: duration for camera easing.
  - `CAMERA_BEARING_EPS_DEG`: bearing change required to trigger camera updates.
- `MapLocationFilter` threshold:
  - `MapFeatureFlags.locationJitterThresholdPx`: higher = more stability, lower = more responsive.
  - The same threshold is reused by steady-state ownship rendered-frame no-op suppression.

Guidelines:
- Start by tuning `POS_SMOOTH_MS` and `CAMERA_ANIMATION_MS` together.
- If motion lags, reduce `POS_SMOOTH_MS` or `CAMERA_MIN_UPDATE_INTERVAL_MS`.
- If motion jitters, increase `locationJitterThresholdPx` or `POS_SMOOTH_MS`.
