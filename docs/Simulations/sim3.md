
# Sim3 replay

## Overview
Sim3 is the "const climb" vario demo started by the SIM3 FAB on MapScreen.
It replays a fixed IGC asset with 1-second cadence, linear interpolation, and no noise.

## Source asset and config
- Asset: `app/src/main/assets/replay/vario-demo-const-120s.igc`
- MapScreen start: `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenReplayCoordinator.kt`
  - `VARIO_DEMO_SIM3_ASSET_PATH = replay/vario-demo-const-120s.igc`
  - `SIM3_STEP_MS = 1000`
  - `SIM3_REPLAY_SPEED_MULTIPLIER = 1.0`
  - Interpolation: `ReplayInterpolation.LINEAR`
  - Noise: pressure and GPS altitude sigma = 0, jitter = 0

## Climb rate (m/s -> ft/min -> kt)
Vario is computed from IGC altitude deltas:
`(altitude - prevAlt) / dtSeconds`, using pressure altitude if present, else GPS altitude.
This is implemented in `feature/map/src/main/java/com/trust3/xcpro/replay/IgcReplayMath.kt`.

The IGC file climbs from 1000 m to 1123 m over 120 s (12:00:00 to 12:02:00),
so the average climb is 1.025 m/s.

Typical steps and conversions:
- 1.0 m/s -> 196.9 ft/min -> 1.9 kt
- 1.025 m/s (average) -> 201.8 ft/min -> 2.0 kt
- 2.0 m/s (three brief steps) -> 393.7 ft/min -> 3.9 kt

Conversions are defined in:
`dfcards-library/src/main/java/com/trust3/xcpro/common/units/UnitsConverter.kt`

## Notes
- The vario displayed on MapScreen uses the units preference (m/s, ft/min, kt).
- Sim3 is intended as a steady climb baseline for UI and audio validation.

## Pipeline details (end-to-end)
This section captures the replay pipeline wiring so future agents can trace how SIM3 data
flows from the IGC asset to the MapScreen UI.

### 1) UI entry point -> replay config
- The SIM3 FAB triggers `MapScreenReplayCoordinator.onVarioDemoReplaySim3()`, which:
  - sets replay mode to `REALTIME_SIM`
  - configures 1s cadence, zero noise, linear interpolation
  - loads `replay/vario-demo-const-120s.igc` and starts playback
  - forces map tracking + recenters the map
- Source: `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenReplayCoordinator.kt`

### 2) Session preparation (densify + QNH)
- `prepareReplaySession(...)` densifies IGC points based on `ReplayInterpolation` and `ReplayMode`.
  - For SIM3: `LINEAR` + `REALTIME_SIM` -> `IgcReplayMath.densifyPoints(...)` using `baroStepMs`.
  - QNH is taken from the IGC metadata, defaulting to 1013.3 if missing.
- Source: `feature/map/src/main/java/com/trust3/xcpro/replay/ReplaySessionPrep.kt`

### 3) Playback loop + source gating
- `IgcReplayController.play()`:
  - sets `FlightDataRepository` active source to `REPLAY`
  - suspends live sensors (stops `VarioServiceManager`)
  - iterates points and calls `ReplaySampleEmitter.emitSample(...)`
  - delays based on IGC timestamp deltas and speed multiplier
- On stop/finish it clears replay data, switches active source back to `LIVE`,
  and resumes sensors to avoid stale replay values.
- Source: `feature/map/src/main/java/com/trust3/xcpro/replay/IgcReplayController.kt`

### 4) Sample emission (baro/GPS/compass + optional airspeed)
- `ReplaySampleEmitter.emitSample(...)`:
  - computes movement and heading from consecutive IGC points
  - converts altitude -> pressure (QNH), then emits a baro sample
  - emits GPS at `gpsStepMs` cadence (plus optional noise)
  - emits compass heading
  - emits airspeed if present in the IGC
  - computes `igcVario = (altitude - prevAlt) / dtSeconds`
- Source: `feature/map/src/main/java/com/trust3/xcpro/replay/ReplaySampleEmitter.kt`
  and `feature/map/src/main/java/com/trust3/xcpro/replay/IgcReplayMath.kt`

### 5) Replay mode semantics (REALTIME_SIM vs REFERENCE)
- In `REFERENCE` mode, the replay pipeline can inject a "real" vario value into the fusion
  engine via `updateReplayRealVario(...)`. SIM3 uses `REALTIME_SIM`, so this override is
  not active.
- When `replayRealVario` is present, `FlightDataEmitter` prefers it for metrics and display.
- Source: `feature/map/src/main/java/com/trust3/xcpro/replay/ReplaySampleEmitter.kt`,
  `feature/map/src/main/java/com/trust3/xcpro/sensors/FlightDataEmitter.kt`

### 6) Sensor fusion + replay time base
- `ReplayPipeline` builds a `SensorFusionRepository` with `isReplayMode = true`,
  using `ReplaySensorSource` as the input stream.
- In replay mode, the vario loop uses sensor timestamps (IGC time) as the calculation
  clock, so downstream estimators use the same simulation time base.
- Source: `feature/map/src/main/java/com/trust3/xcpro/replay/ReplayPipeline.kt`,
  `feature/map/src/main/java/com/trust3/xcpro/replay/ReplaySensorSource.kt`,
  `feature/map/src/main/java/com/trust3/xcpro/sensors/FlightDataCalculatorEngineLoops.kt`

### 7) Flight data -> UI
- `ReplayPipeline` forwards fused `CompleteFlightData` into `FlightDataRepository` with source `REPLAY`.
- `FlightDataRepository` gates updates by active source so live sensors do not override replay.
- `FlightDataUseCase` exposes `FlightDataRepository` to `MapScreenViewModel`.
- `FlightDataUiAdapter` and `FlightDataManager` build UI-friendly flows
  (display vario, needle vario, etc.).
- `OverlayPanels` formats display units for the variometer widget.
- Source: `feature/map/src/main/java/com/trust3/xcpro/replay/ReplayPipeline.kt`,
  `feature/map/src/main/java/com/trust3/xcpro/flightdata/FlightDataRepository.kt`,
  `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenUseCases.kt`,
  `feature/map/src/main/java/com/trust3/xcpro/map/FlightDataUiAdapter.kt`,
  `feature/map/src/main/java/com/trust3/xcpro/map/FlightDataManager.kt`,
  `feature/map/src/main/java/com/trust3/xcpro/map/ui/OverlayPanels.kt`

### 8) Finish ramp behavior (gentle return to zero)
- At replay finish, a short ramp can be emitted to taper the vario toward zero,
  then replay is cleaned up and control returns to live sensors.
- Source: `feature/map/src/main/java/com/trust3/xcpro/replay/ReplayFinishRamp.kt`,
  `feature/map/src/main/java/com/trust3/xcpro/replay/IgcReplayController.kt`

