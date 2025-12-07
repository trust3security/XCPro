# Map Icon Smoothness (XCSoar-style) Implementation Plan

**Current date/context:** LocationManager.kt is at HEAD (no gating/clamp rewiring). Two new files are present but not wired:  
`feature/map/src/main/java/com/example/xcpro/map/MapLocationFilter.kt` (gating helper)  
`feature/map/src/main/java/com/example/xcpro/map/MapPositionController.kt` (single-owner move controller)

## Goals (match XCSoar)
1. One gate at 0.5 px for all location updates (GPS + replay).
2. Single owner updates both camera and icon together.
3. Offset averaging for glider screen bias (30 samples).
4. Bearing clamp: max 5 deg step per update for icon/camera rotation.
5. Stable cadence: icon and camera move only on accepted samples; replay optionally interpolates if needed.

## Files to touch
- `feature/map/src/main/java/com/example/xcpro/map/LocationManager.kt` (main integration)
- `feature/map/src/main/java/com/example/xcpro/map/MapLocationFilter.kt` (already present)
- `feature/map/src/main/java/com/example/xcpro/map/MapPositionController.kt` (already present)
- `feature/map/src/main/java/com/example/xcpro/map/config/MapFeatureFlags.kt` (threshold/window already added)
- Tests: optional new test for offset/bearing clamp (pure JVM) under `feature/map/src/test/java/com/example/xcpro/map/`

## Step-by-step
1) **Wire imports and fields in LocationManager**
   - Add fields:
     - `locationFilter = MapLocationFilter(Config(thresholdPx=MapFeatureFlags.locationJitterThresholdPx, historySize=MapFeatureFlags.locationOffsetHistorySize), MapLibreProjector())`
     - `positionController = MapPositionController(mapState, maxBearingStepDeg=5.0, offsetHistorySize=MapFeatureFlags.locationOffsetHistorySize)`

2) **Replace updateLocationFromGPS body**
   - Check map null; gate via `locationFilter.accept(latLng, map)`; return if false.
   - Set `currentUserLocation`.
   - Call `positionController.applyAcceptedSample(map, latLng, trackBearing=location.bearing, magneticHeading, orientationMode)`.
   - Keep initial centering and camera tracking calls afterward (but they should rely on same accepted point).

3) **Replay/live flight updates**
   - In `updateLocationFromFlightData`, same pattern:
     - gate -> set `currentUserLocation` -> `positionController.applyAcceptedSample(...)`.

4) **Remove/retire shouldRecentreLazy**
   - Camera movement is handled inside `applyAcceptedSample`; remove any remaining calls to `shouldRecentreLazy` and delete the helper.

5) **Offset averaging (optional)**
   - If glider offset is computed elsewhere, feed it into `positionController.rememberOffset()` and use `averagedOffset()` before applying to camera padding (if we bias the screen). If not yet used, can skip.

6) **Bearing clamp**
   - `MapPositionController` already clamps to 5 deg. Ensure icon rotation uses the clamped bearing (it does inside `applyAcceptedSample`).

7) **Tests**
   - Add a small unit test for `MapPositionController.clampBearingStep` and offset average (pure JVM).
   - Existing `MapLocationFilterTest` covers the gate.

## Validation
- Run `./gradlew testDebugUnitTest`.
- Quick grep to confirm only LocationManager calls `blueLocationOverlay.updateLocation`.

## State snapshot
- Untracked: `MapLocationFilter.kt`, `MapPositionController.kt` (keep).
- LocationManager.kt currently HEAD (needs integration above).
- MapFeatureFlags already includes jitter threshold & offset window.
