# ADSB_TestIcon_Orientation.md
Use ONE PNG icon for all ADS-B aircraft to validate: (1) the layer draws correctly, (2) markers move without flicker, (3) icon rotation matches aircraft direction, (4) tap -> details works.

This is intentionally a *test-first* change. After orientation is verified, you can swap in per-type icons.

---

## 0) What you will add
- A single aircraft PNG `ic_adsb_aircraft_test.png` (provided in this packet).
- Register it once in the MapLibre style as `adsb_icon_test`.
- Use it for every ADS-B feature.
- Rotate it by OpenSky `true_track` (degrees clockwise from north; north=0°).  
  Reference: OpenSky REST docs.

---

## 1) Resource placement (Android)
Place the PNG in the same module that owns the MapLibre map overlay resources.

Most likely (based on existing map feature layout):
- `feature/map/src/main/res/drawable/ic_adsb_aircraft_test.png`

If your ADS-B overlay code lives in a different module, place it under that module’s `src/main/res/drawable/`.

---

## 2) MapLibre style registration (one-time per style load)
In your ADS-B overlay initialization (where you create the GeoJsonSource + SymbolLayer):

1. Load bitmap from resources:
```kotlin
val bmp = BitmapFactory.decodeResource(context.resources, R.drawable.ic_adsb_aircraft_test)
```

2. Add the image to the style (only once per style load):
```kotlin
private const val ADSB_ICON_TEST = "adsb_icon_test"

if (style.getImage(ADSB_ICON_TEST) == null) {
    // SDF=true allows tinting via iconColor if you later want it.
    style.addImage(ADSB_ICON_TEST, bmp, /* sdf = */ true)
}
```

Notes:
- Do not re-add on every update.
- If you don’t need tinting, sdf=false is fine.

---

## 3) SymbolLayer changes (rotate by track, no flicker)
Assumptions:
- You already have a persistent GeoJSON source and SymbolLayer for ADS-B.

Update your SymbolLayer properties:

### 3.1 Icon image
Use the single test icon for all aircraft:
```kotlin
iconImage(ADSB_ICON_TEST)
```

### 3.2 Rotation
You must rotate by a per-feature property `track_deg`:
- OpenSky `true_track` is in decimal degrees clockwise from north (north=0°).

```kotlin
private const val PROP_TRACK_DEG = "track_deg"

iconRotate(
  Expression.coalesce(
    Expression.get(PROP_TRACK_DEG),
    Expression.literal(0.0)
  )
)
iconRotationAlignment("map")
iconKeepUpright(false)
```

Why:
- `iconRotationAlignment("map")` keeps heading correct relative to map north.
- `iconKeepUpright(false)` prevents auto-flipping (you want true heading).

---

## 4) GeoJSON mapping changes (feature properties)
In your ADS-B Feature builder (GeoJSON mapper), set properties:

- `icao24` (string) – stable ID for selection
- `track_deg` (double) – from domain model `trackDeg` or null
- `speed_mps`, `alt_m`, `vr_mps`, `callsign`, `age_s` – whatever you already show in details

Example:
```kotlin
val f = Feature.fromGeometry(Point.fromLngLat(target.lon, target.lat))
f.id(target.id.raw)

f.addStringProperty("icao24", target.id.raw)
target.trackDeg?.let { f.addNumberProperty("track_deg", it) }
target.speedMps?.let { f.addNumberProperty("speed_mps", it) }
target.altitudeM?.let { f.addNumberProperty("alt_m", it) }
target.climbMps?.let { f.addNumberProperty("vr_mps", it) }
target.callsign?.let { f.addStringProperty("callsign", it) }
f.addNumberProperty("age_s", target.ageSec)
```

---

## 5) Tap -> details (selection)
If you already implemented marker tap for ADS-B, do nothing here.

If not, implement in the same router you use for OGN taps:

1. On map click, query rendered features on the ADS-B layer:
```kotlin
val hits = maplibreMap.queryRenderedFeatures(screenPoint, arrayOf(ADSB_LAYER_ID))
```

2. If present:
- read `icao24` property
- dispatch `OnAdsbTargetTapped(icao24)` into MapScreenViewModel
- open bottom sheet from UI state

---

## 6) How to verify correct orientation (practical)
You are checking: **icon nose direction == aircraft track direction**.

### 6.1 Preconditions
- Map is north-up (no map rotation) for easiest validation.
- The icon PNG must be drawn “nose up” (north) at rotation=0°.

### 6.2 Validation steps
1. Pick an aircraft moving clearly in one direction (fast mover is easiest).
2. In debug (or details sheet), display:
   - `trackDeg` (from OpenSky true_track)
   - (optional) computed bearing from the last two positions you received:
     - bearing(lastPoint -> currentPoint)
3. The icon should point in the same direction as `trackDeg` (within a few degrees).

### 6.3 If it’s consistently off by a fixed angle
The PNG is not oriented north-up. Fix with a constant offset:

```kotlin
private const val ICON_BEARING_OFFSET_DEG = 0.0 // adjust if needed

iconRotate(
  Expression.coalesce(
    Expression.add(Expression.get(PROP_TRACK_DEG), Expression.literal(ICON_BEARING_OFFSET_DEG)),
    Expression.literal(0.0)
  )
)
```

---

## 7) After the test passes
Once orientation is verified:
- replace `iconImage(ADSB_ICON_TEST)` with `iconImage(Expression.get("icon_id"))`
- map aircraft kind -> icon_id
- register multiple icons in style (your phase-2 icon plan)

