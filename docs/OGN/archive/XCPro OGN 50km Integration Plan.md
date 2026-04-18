# XCPro OGN Integration (Current Implemented State)

Note:
This file keeps its legacy name for history. The 50 km plan is obsolete.
Current code uses a 150 km radius (300 km diameter) around ownship GPS.

## Implemented Architecture

- Repository:
  - `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnTrafficRepository.kt`
- Parser:
  - `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnAprsLineParser.kt`
- DDB enrichment:
  - `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnDdbRepository.kt`
- Use case:
  - `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenUseCases.kt` (`OgnTrafficUseCase`)
- ViewModel/traffic coordination:
  - `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenViewModel.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenTrafficCoordinator.kt`
- Runtime overlay:
  - `feature/map/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlay.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/MapOverlayManager.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenRootEffects.kt`
- User preferences:
  - `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnTrafficPreferencesRepository.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/OgnSettingsScreen.kt`

## Behavior Contract In Code

- Stream toggle:
  - enabled when `allowSensorStart && mapVisible && ognOverlayEnabled`.
- Center:
  - updated from ownship GPS (`mapLocation`).
- Radius:
  - APRS login filter radius 150 km.
  - client-side haversine filter radius 150 km.
- Filter recenter threshold:
  - reconnect when center moves >= 20 km.
- Stale behavior:
  - visual stale threshold 60 seconds.
  - stale eviction 120 seconds.
- Render behavior:
  - max 500 targets.
  - viewport culling against visible map bounds.
  - icon is `R.drawable.ic_adsb_glider`.
  - overlay disabled renders `emptyList()`.

## Current UX

Implemented:
- OGN overlay toggle on map action buttons.
- OGN icon-size setting.
- Debug panel (debug builds) with connection/center/radius/target counts.

Not implemented:
- OGN marker details bottom sheet.
- user-adjustable OGN radius.
- OGN uplink/transmit.

## Tests Covering Implementation

- `feature/map/src/test/java/com/trust3/xcpro/ogn/OgnAprsLineParserTest.kt`
- `feature/map/src/test/java/com/trust3/xcpro/ogn/OgnSubscriptionPolicyTest.kt`
- `feature/map/src/test/java/com/trust3/xcpro/ogn/OgnTrackStabilizerTest.kt`
- `feature/map/src/test/java/com/trust3/xcpro/ogn/OgnDdbJsonParserTest.kt`
- `feature/map/src/test/java/com/trust3/xcpro/ogn/OgnTrafficPreferencesRepositoryTest.kt`
- `feature/map/src/test/java/com/trust3/xcpro/map/MapScreenViewModelTest.kt`

## Source Of Truth

- `docs/ARCHITECTURE/PIPELINE.md`
- `docs/OGN/OGN_PROTOCOL_NOTES.md`
- `docs/OGN/OGN.md`
