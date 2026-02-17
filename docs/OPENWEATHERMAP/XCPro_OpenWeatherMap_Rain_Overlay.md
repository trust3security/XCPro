# XCPro --- OpenWeatherMap Rain Overlay (Weather Maps 1.0)

## Objective

Integrate a live precipitation (rain) raster overlay into XCPro using
OpenWeatherMap Weather Maps 1.0. Users provide their own OpenWeatherMap
API key (BYO key model).

This implementation: - Uses MapLibre raster tiles - Stores API key
securely - Survives style reloads - Cleans up correctly on lifecycle
changes - Follows XCPro runtime overlay ownership rules

------------------------------------------------------------------------

## OpenWeatherMap Tile Endpoint

Base template:

https://tile.openweathermap.org/map/{layer}/{z}/{x}/{y}.png?appid={API_KEY}

Layer for precipitation: precipitation_new

Tile size: 256

------------------------------------------------------------------------

## Architecture Rules (Must Follow)

-   MapLibre objects (sources/layers) must live in runtime layer only.
-   ViewModels must NOT reference MapLibre types.
-   Overlay must be re-applied on style reload.
-   Overlay must clean up on map destroy.
-   API key must never be logged.

------------------------------------------------------------------------

## Phase 1 --- Preferences (SSOT)

Create WeatherOverlayPreferencesRepository:

Fields: - owm_precip_enabled: Boolean (default false) - owm_api_key:
String (default empty) - owm_precip_opacity: Float (default 0.45, clamp
0..1)

Store API key using EncryptedSharedPreferences or Keystore-backed
storage.

------------------------------------------------------------------------

## Phase 2 --- Runtime Overlay Class

Create:

feature/map/.../OwmPrecipOverlay.kt

Responsibilities: - Add/remove RasterSource - Add/remove RasterLayer -
Apply opacity - Re-apply on style reload - Cleanup safely

IDs: - Source: owm-precip-source - Layer: owm-precip-layer

Tile URL builder:
https://tile.openweathermap.org/map/precipitation_new/{z}/{x}/{y}.png?appid=\$apiKey

Layer ordering: Place below task/airspace/traffic overlays.

------------------------------------------------------------------------

## Phase 3 --- Wiring

### MapScreenState

Add: var owmPrecipOverlay: OwmPrecipOverlay? = null

### MapInitializer

Instantiate overlay after style load.

### MapOverlayManager

Add: fun updateOwmPrecipOverlay(config: OwmPrecipConfig)

Responsibilities: - Store latest config - Apply overlay if map
available - Reapply on style change

### MapLifecycleManager

On destroy: - overlay.cleanup() - set overlay to null

------------------------------------------------------------------------

## Phase 4 --- UI

Add settings:

-   Toggle: Rain Overlay
-   API Key input
-   Opacity slider (0--100%)

Bind preferences → ViewModel → MapOverlayManager update call.

------------------------------------------------------------------------

## Error Handling

-   Blank key = overlay disabled
-   Invalid key (401/429) = overlay may render blank; no crash
-   Clamp opacity 0..1
-   Never log full tile URLs

------------------------------------------------------------------------

## QA Checklist

-   Default state OFF
-   Enter key + enable → tiles visible
-   Disable → tiles removed
-   Change opacity → updates live
-   Switch base map style → overlay persists
-   Restart app → state restored

------------------------------------------------------------------------

## Deliverables

1.  Preferences repository (secure storage)
2.  OwmPrecipOverlay runtime class
3.  Wiring in MapScreenState, MapInitializer, MapOverlayManager
4.  Cleanup in MapLifecycleManager
5.  Settings UI
6.  Manual + unit validation

End of specification.
