# Levo/Overlay Overheating Baseline (2026-02-24)

Context:
- User-reported issue: phone overheating while flying with app running.
- This document captures the current ranked hypothesis list from code inspection.

Important framing:
- This is a code-path risk ranking, not a measured thermal trace yet.
- Ranking confidence is high for architectural hotspots, medium for exact percentage split.

## Top 10 Suspected Contributors

1) Per-frame map display pipeline (highest suspected impact)
- Why hot:
  - Continuous `withFrameNanos` loop drives display updates.
  - Map pose render can trigger camera updates and marker source updates repeatedly.
  - This stresses CPU (logic) and GPU (map rendering) together.
- Evidence:
  - `feature/map/src/main/java/com/trust3/xcpro/map/ui/effects/MapComposeEffects.kt:146`
  - `feature/map/src/main/java/com/trust3/xcpro/map/LocationManager.kt:215`
  - `feature/map/src/main/java/com/trust3/xcpro/map/DisplayPoseRenderCoordinator.kt:49`
  - `feature/map/src/main/java/com/trust3/xcpro/map/MapTrackingCameraController.kt:53`
  - `feature/map/src/main/java/com/trust3/xcpro/map/MapPositionController.kt:44`
  - `feature/map/src/main/java/com/trust3/xcpro/map/BlueLocationOverlay.kt:110`

2) High-rate sensor ingestion (baro/IMU at GAME rate, GPS fast mode in flight)
- Why hot:
  - Barometer, linear accel, raw accel, and rotation vector are registered at `SENSOR_DELAY_GAME`.
  - GPS cadence can be pushed to 200 ms while flying.
- Evidence:
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/SensorRegistry.kt:47`
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/SensorRegistry.kt:285`
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/SensorRegistry.kt:300`
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/SensorRegistry.kt:314`
  - `feature/map/src/main/java/com/trust3/xcpro/vario/VarioServiceManager.kt:45`
  - `feature/map/src/main/java/com/trust3/xcpro/vario/GpsCadencePolicy.kt:11`

3) Levo fusion/filter loop cadence
- Why hot:
  - Vario loop is designed around high-rate baro updates (commented 50 Hz path).
  - Multiple filters, vario suite updates, and emit path run repeatedly.
- Evidence:
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/FlightDataCalculatorEngine.kt:147`
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/FlightDataCalculatorEngineLoops.kt:44`
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/FlightDataCalculatorEngine.kt:55`

4) Vario audio synthesis path
- Why hot:
  - Audio loop runs continuously while active.
  - Tone generation computes sample buffers and writes to `AudioTrack` in stream mode.
- Evidence:
  - `feature/map/src/main/java/com/trust3/xcpro/audio/VarioAudioController.kt:44`
  - `feature/map/src/main/java/com/trust3/xcpro/audio/VarioBeepController.kt:25`
  - `feature/map/src/main/java/com/trust3/xcpro/audio/VarioToneGenerator.kt:36`
  - `feature/map/src/main/java/com/trust3/xcpro/audio/VarioToneGenerator.kt:196`

5) OGN overlay rendering in `REAL_TIME` mode
- Why hot:
  - OGN real-time mode uses `renderIntervalMs = 0`, effectively unthrottled render requests.
  - Traffic, thermal, and trail overlays can all rebuild GeoJSON feature sets.
- Evidence:
  - `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnDisplayUpdateMode.kt:13`
  - `feature/map/src/main/java/com/trust3/xcpro/map/MapOverlayManager.kt:373`
  - `feature/map/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlay.kt:102`
  - `feature/map/src/main/java/com/trust3/xcpro/map/OgnThermalOverlay.kt:64`
  - `feature/map/src/main/java/com/trust3/xcpro/map/OgnGliderTrailOverlay.kt:58`

6) ADS-B display interpolation frame loop
- Why hot:
  - Uses Choreographer callbacks while animations are active.
  - Rebuilds and pushes traffic feature collection during frame loop.
- Evidence:
  - `feature/map/src/main/java/com/trust3/xcpro/map/AdsbTrafficOverlay.kt:57`
  - `feature/map/src/main/java/com/trust3/xcpro/map/AdsbTrafficOverlay.kt:254`
  - `feature/map/src/main/java/com/trust3/xcpro/map/AdsbDisplayMotionSmoother.kt:62`

7) Snail trail rendering pipeline
- Why hot:
  - Trail rendering builds segment feature arrays and updates multiple map sources.
  - Replay + display-frame sync paths can increase update frequency.
- Evidence:
  - `feature/map/src/main/java/com/trust3/xcpro/map/trail/SnailTrailManager.kt:95`
  - `feature/map/src/main/java/com/trust3/xcpro/map/trail/SnailTrailOverlay.kt:243`
  - `feature/map/src/main/java/com/trust3/xcpro/map/trail/SnailTrailOverlay.kt:291`

8) Weather rain overlay animation and transition loop
- Why hot:
  - Uses periodic animation ticker and metadata refresh loop.
  - Cross-fade transition updates layer opacity over time.
- Evidence:
  - `feature/map/src/main/java/com/trust3/xcpro/weather/rain/ObserveWeatherOverlayStateUseCase.kt:42`
  - `feature/map/src/main/java/com/trust3/xcpro/weather/rain/ObserveWeatherOverlayStateUseCase.kt:70`
  - `feature/map/src/main/java/com/trust3/xcpro/weather/rain/WeatherOverlaySettings.kt:16`
  - `feature/map/src/main/java/com/trust3/xcpro/map/WeatherRainOverlay.kt:118`

9) SkySight satellite/radar/lightning animation
- Why hot:
  - Animation loop toggles frame opacities repeatedly.
  - Rebuild path adds/removes multiple sources/layers by frame count.
- Evidence:
  - `feature/map/src/main/java/com/trust3/xcpro/map/SkySightSatelliteOverlay.kt:44`
  - `feature/map/src/main/java/com/trust3/xcpro/map/SkySightSatelliteOverlay.kt:90`
  - `feature/map/src/main/java/com/trust3/xcpro/map/SkySightSatelliteOverlay.kt:205`
  - `feature/map/src/main/java/com/trust3/xcpro/map/SkySightSatelliteOverlay.kt:328`

10) High-cadence cards + variometer drawing load
- Why hot:
  - Card subsystem updates fast tier at 80 ms and primary tier at 250 ms.
  - Variometer canvas builds paint objects in draw path.
- Evidence:
  - `dfcards-library/src/main/java/com/example/dfcards/dfcards/CardStateRepository.kt:36`
  - `dfcards-library/src/main/java/com/example/dfcards/dfcards/CardStateRepositoryUpdates.kt:36`
  - `feature/variometer/src/main/java/com/example/ui1/UIVariometer.kt:268`
  - `feature/variometer/src/main/java/com/example/ui1/UIVariometer.kt:404`

## Default-State Caveat

Some heavy overlays are preference-gated and default to off:
- OGN overlay default off:
  - `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnTrafficPreferencesRepository.kt:39`
- ADS-B overlay default off:
  - `feature/map/src/main/java/com/trust3/xcpro/adsb/AdsbTrafficPreferencesRepository.kt:33`
- Weather rain overlay default off:
  - `feature/map/src/main/java/com/trust3/xcpro/weather/rain/WeatherOverlayPreferencesRepository.kt:124`

Implication:
- If user flies mostly with base vario + map only, contributors 1 to 4 dominate.
- If overlays are enabled (especially OGN real-time + ADS-B + weather animation), thermal risk rises quickly.

## Most Likely Dominant Overheat Path For Typical Flight

Likely dominant chain:
1. High-rate sensors feed fusion continuously.
2. Display frame loop keeps map update/render active.
3. Camera/overlay marker updates and draw work run repeatedly.
4. Audio synthesis runs continuously when vario active.

This chain combines:
- CPU (sensor + fusion + app logic),
- GPU (map and overlay rendering),
- DSP/audio path (tone generation),
which is a classic thermal escalation pattern on mobile devices.
