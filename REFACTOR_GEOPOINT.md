# Refactor Plan: Map-agnostic GPS + Map Location Selection

## Purpose
Remove MapLibre types from GPSData, and move replay/live location selection out of Compose into
ViewModel-owned flows. This aligns with ARCHITECTURE.md (SSOT + UI derived state rules) and
keeps map-specific types at the map boundary only.

## Current Findings
- GPSData is defined in `feature/map/.../SensorData.kt` and uses `org.maplibre.android.geometry.LatLng`.
- MapScreenRoot derives `suppressLiveGps`, `allowSensorStart`, and replay GPS (via
  `rememberReplayGpsLocation`) inside the UI layer.
- Map updates are driven both by GPSData (LocationAndPermissionEffects) and by RealTimeFlightData
  (FlightDataAndCardEffects), which duplicates decision logic in the UI.
- FlightDataRepository already exposes `activeSource` (LIVE/REPLAY), and IgcReplayController
  switches it during load/play/stop.

## Proposed Design
### 1) Introduce GeoPoint in core/common
- New value type: `GeoPoint(latitude: Double, longitude: Double)` in
  `core/common/src/main/java/com/example/xcpro/common/geo/GeoPoint.kt`.
- Add optional helpers:
  - `val isValid: Boolean` for bounds checks.
  - `val isZero: Boolean` for zero coordinate guard.

### 2) Make GPSData map-agnostic
- Update `GPSData` to use `val position: GeoPoint` (or similar) instead of `LatLng`.
- Remove MapLibre imports from sensors/domain code.
- Optional convenience accessors:
  - `val latitude get() = position.latitude`
  - `val longitude get() = position.longitude`

### 3) Map boundary adapters
- Add extension helpers in map module only, e.g.:
  - `fun GeoPoint.toLatLng(): LatLng`
  - `fun LatLng.toGeoPoint(): GeoPoint`
  - `fun GPSData.toLatLng(): LatLng`

### 4) Move replay/live location selection into ViewModel
Add flows to `MapScreenViewModel` (or a dedicated MapLocationSelector) so UI only renders:
- `val allowSensorStart: StateFlow<Boolean>`
  - Same logic as today: `selection == null || status == IDLE`.
- `val suppressLiveGps: StateFlow<Boolean>`
  - Same logic as today: `selection != null`.
- `val mapLocation: StateFlow<GPSData?>`
  - Combine `activeSource`, live GPS, and replay flight data:
    - If source == REPLAY, map RealTimeFlightData -> GPSData.
    - Else use live GPS flow.
- Helper (non-Compose) mapper:
  - `private fun toGpsData(sample: RealTimeFlightData?): GPSData?`.

### 5) UI cleanup
- Replace `rememberReplayGpsLocation` and UI-derived flags in MapScreenRoot with ViewModel flows.
- Remove `MapScreenReplayHelpers.kt` once ViewModel mapping is in place.

## Files to Update (Known Touch Points)
Core/common:
- `core/common/src/main/java/com/example/xcpro/common/geo/GeoPoint.kt` (new)

Sensors/domain:
- `feature/map/src/main/java/com/example/xcpro/sensors/SensorData.kt`
- `feature/map/src/main/java/com/example/xcpro/sensors/SensorRegistry.kt`
- `feature/map/src/main/java/com/example/xcpro/sensors/FlightCalculationHelpers.kt`
- `feature/map/src/main/java/com/example/xcpro/sensors/FlightStateRepository.kt`
- `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngine.kt`
- `feature/map/src/main/java/com/example/xcpro/weather/wind/data/WindSensorInputAdapter.kt`

Replay + logging:
- `feature/map/src/main/java/com/example/xcpro/replay/ReplaySensorSource.kt`
- `feature/map/src/main/java/com/example/xcpro/replay/IgcReplayController.kt`

Map + UI:
- `feature/map/src/main/java/com/example/xcpro/map/LocationManager.kt`
- `feature/map/src/main/java/com/example/xcpro/map/MapInitializer.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRoot.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenReplayHelpers.kt` (remove)
- `feature/map/src/main/java/com/example/xcpro/map/ui/OverlayPanels.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/task/MapTaskScreenUi.kt`
- `feature/map/src/main/java/com/example/xcpro/MapScreenUtils.kt`

Tests:
- `feature/map/src/test/java/com/example/xcpro/sensors/LevoVarioPipelineTest.kt`
- `feature/map/src/test/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCaseTest.kt`
- Any test that constructs GPSData with LatLng.

## Implementation Steps (Concrete)
1) Add `GeoPoint` in core/common and map adapters in feature/map.
2) Update `GPSData` to use GeoPoint and migrate constructors (SensorRegistry, ReplaySensorSource).
3) Replace `gps.latLng.*` access with `gps.position.*` or `gps.latitude/longitude` helpers.
4) Update wind/AGL/QNH logic to use GeoPoint.
5) Implement ViewModel flows for `allowSensorStart`, `suppressLiveGps`, and `mapLocation`.
6) Replace Compose-derived logic in MapScreenRoot; remove MapScreenReplayHelpers.
7) Update tests to use GeoPoint in GPSData.
8) Run unit tests; update any snapshot/expected values if needed.

## Risks / Edge Cases
- Replay loaded but not playing: if `activeSource == REPLAY` and no forwarded sample yet,
  `mapLocation` may be null. Decide whether to seed a location on load/seek.
- Map updates currently run from both GPSData and RealTimeFlightData; ensure the refactor
  leaves one clear map update path or explicitly documents both.
- Any downstream code that assumes `gps.latLng` exists will need updating.

## Validation
- `./gradlew :feature:map:testDebugUnitTest`
- Verify map location updates for live flight and replay play/pause/seek.
- Confirm no MapLibre imports remain in non-map sensor/domain files.

## Progress Log
- 2026-01-06: Plan created after codebase audit.
- 2026-01-06: Implemented GeoPoint + adapters, migrated GPSData to GeoPoint, and updated gps.latLng call sites.
- 2026-01-06: Moved replay/live location selection into MapScreenViewModel flows and removed Compose-derived replay GPS helper.
