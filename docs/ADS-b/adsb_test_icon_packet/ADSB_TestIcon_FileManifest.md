# ADSB_TestIcon_FileManifest.md
Minimal set of changes so Codex can implement the single-icon orientation test.

This assumes ADS-B traffic already works (markers appear and move).

---

## 1) Add resource
Copy the provided PNG into your map feature resources:

- `feature/map/src/main/res/drawable/ic_adsb_aircraft_test.png`

(If the ADS-B overlay is in another module, use that module's `src/main/res/drawable/`.)

---

## 2) Update MapLibre overlay
Modify your ADS-B overlay class (same file that creates the GeoJsonSource + SymbolLayer):

### A) Add style image registration (once per style load)
- Add image name constant: `adsb_icon_test`
- Load bitmap from `R.drawable.ic_adsb_aircraft_test`
- Add with `style.addImage("adsb_icon_test", bmp, sdf=true)`

### B) Update SymbolLayer properties
- `iconImage("adsb_icon_test")`
- `iconRotate(coalesce(get("track_deg"), 0.0))`
- `iconRotationAlignment("map")`
- `iconKeepUpright(false)`
- keep existing overlap/placement settings

---

## 3) Update GeoJSON mapping
Modify your ADS-B GeoJSON mapper (Feature builder):

- ensure each Feature has:
  - ID = `icao24`
  - property `icao24` (string)
  - property `track_deg` (double) from `trackDeg` when present
- keep existing properties for details

---

## 4) Tap handling (if not already present)
- Map click: `queryRenderedFeatures(point, arrayOf(ADSB_LAYER_ID))`
- Extract `icao24` property and notify ViewModel
- Show bottom sheet using selected target from current list

---

## 5) Tests (optional but recommended)
- Unit test GeoJSON mapping includes `track_deg` for a target with `trackDeg != null`
- Unit test rotation expression uses 0 when `track_deg` absent (if you have expression helper tests)

