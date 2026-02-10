# ADSB_FileManifest.md
Files to create/modify for the ADS‑B (internet) traffic layer.

This list is written for Codex. Paths are indicative; match the repo’s conventions.

## 1) New files (feature/map module)
Create under a new namespace similar to OGN:

### Domain
- `.../traffic/adsb/domain/model/Icao24.kt`
- `.../traffic/adsb/domain/model/AdsbTarget.kt`
- `.../traffic/adsb/domain/model/AdsbTrafficUiModel.kt`
- `.../traffic/adsb/domain/model/AdsbConnectionState.kt`
- `.../traffic/adsb/domain/model/GeoFilter.kt` (if not reused)
- `.../traffic/adsb/domain/usecase/ObserveAdsbTrafficUseCase.kt`
- `.../traffic/adsb/domain/usecase/ToggleAdsbTrafficUseCase.kt` (optional)

### Data
- `.../traffic/adsb/data/provider/AdsbProviderClient.kt`
- `.../traffic/adsb/data/provider/OpenSkyProviderClient.kt`
- `.../traffic/adsb/data/provider/OpenSkyModels.kt`
- `.../traffic/adsb/data/provider/OpenSkyStateVectorMapper.kt`
- `.../traffic/adsb/data/provider/OpenSkyAuthModels.kt` (token response)
- `.../traffic/adsb/data/auth/OpenSkyTokenRepository.kt`
- `.../traffic/adsb/data/repository/AdsbTrafficRepository.kt`
- `.../traffic/adsb/data/repository/AdsbTrafficRepositoryImpl.kt`
- `.../traffic/adsb/data/store/AdsbTrafficStore.kt`
- `.../traffic/adsb/data/math/Haversine.kt`
- `.../traffic/adsb/data/math/BBox.kt`

### UI/Map overlay
- `.../traffic/adsb/ui/map/AdsbTrafficOverlay.kt`
- `.../traffic/adsb/ui/map/AdsbGeoJsonMapper.kt` (ui model -> FeatureCollection)
- `.../traffic/adsb/ui/AdsbMarkerDetailsSheet.kt` (bottom sheet UI)
- `.../traffic/adsb/ui/AdsbFab.kt` (separate FAB)

### Settings (optional)
- `.../traffic/adsb/data/settings/AdsbSettingsRepository.kt`
- `.../traffic/adsb/ui/AdsbSettingsScreen.kt` (credentials entry, advanced)

## 2) Existing files to modify
These are the most likely integration points; confirm exact names in repo:
- `MapScreenViewModel`:
  - add flows for adsb traffic + connection state + toggle
  - combine with existing map state flows
- Map overlay stack / overlay manager:
  - register `AdsbTrafficOverlay`
  - ensure overlay is created on style load and re-created on style change
- MapScreen UI:
  - add separate ADS‑B FAB
  - route toggle intent to ViewModel

## 3) DI bindings (Hilt)
Add a DI module binding:
- `AdsbProviderClient` -> `OpenSkyProviderClient`
- `AdsbTrafficRepository` -> `AdsbTrafficRepositoryImpl`
- `OpenSkyTokenRepository` -> impl

Inject a Clock into repository/store.

## 4) Tests
Create pure JVM unit tests:
- `HaversineTest`
- `BBoxTest`
- `OpenSkyStateVectorMapperTest`
- `AdsbTrafficStoreTest`
- `AdsbTrafficRepositoryTest` (with fake provider + fake clock)

