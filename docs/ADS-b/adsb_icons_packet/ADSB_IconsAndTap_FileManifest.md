# ADSB_IconsAndTap_FileManifest.md

This manifest assumes you already have the working ADS-B layer (provider + repo + overlay + FAB).
It lists the minimal edits to add type-based icons, heading rotation, and tap-to-details.

## A) New resources (required)
Add these vector drawables (or PNGs) under the map/feature module res/drawable:
- `ic_adsb_small_single_engine.xml`
- `ic_adsb_small_jet.xml`
- `ic_adsb_large_jet.xml`
- `ic_adsb_helicopter.xml`
- `ic_adsb_glider.xml`
- `ic_adsb_unknown.xml`

## B) UI model / mapping changes
Modify:
- `AdsbTrafficUiModel` (add `aircraftKind: AdsbAircraftKind`)
Add:
- `AdsbAircraftKind` enum (UI-level)
- `AdsbAircraftKindMapper` (pure function for mapping category+speed -> kind)

Modify:
- `AdsbGeoJsonMapper` (or equivalent):
  - add `icon_id` property
  - ensure `track_deg` is present and numeric
  - ensure `icao24` is present for tap selection

## C) Map overlay changes
Modify:
- `AdsbTrafficOverlay` (or equivalent):
  - register multiple style images via `Style.addImage` / `addImages`
  - update SymbolLayer `iconImage` to read from feature property (`Expression.get("icon_id")`)
  - update SymbolLayer rotation to use `track_deg`
  - ensure rotation alignment is `map`

## D) Tap handling + details UI
Modify:
- Map click handler (where OGN taps are handled, or central map click router):
  - query ADS-B layer features on tap
  - dispatch `OnAdsbTargetTapped(icao24)` to ViewModel

Modify:
- MapScreenViewModel:
  - keep `selectedAdsbId`
  - expose `selectedAdsbDetails`

Modify:
- `AdsbDetailsBottomSheet`:
  - show aircraftKind label and raw category if desired

## E) Tests
Add:
- `AdsbAircraftKindMapperTest`
- `AdsbGeoJsonMapperIconTest`
- Optional: ViewModel selection test with fake traffic list

