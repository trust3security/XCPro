# ADSB_IconsAndTap_Plan.md — Map icons, rotation, tap details
**v3: includes ICAO24 identity + metadata display**

---

## 1) Icons (28dp baseline)
Buckets (files):
- ic_adsb_plane_light.png
- ic_adsb_plane_large.png
- ic_adsb_helicopter.png
- ic_adsb_glider.png
- ic_adsb_balloon.png
- ic_adsb_parachutist.png
- ic_adsb_hangglider.png
- ic_adsb_drone.png
- ic_adsb_unknown.png

---

## 2) Register images once per style load
Register each drawable as a MapLibre style image id, e.g.:
- adsb_icon_plane_light
- adsb_icon_plane_large
- adsb_icon_helicopter
- adsb_icon_glider
- adsb_icon_balloon
- adsb_icon_parachutist
- adsb_icon_hangglider
- adsb_icon_drone
- adsb_icon_unknown

---

## 3) GeoJSON feature properties (mandatory)
Each Feature MUST include:
- id = ICAO24 (Feature.id)
- properties:
  - icao24: String
  - callsign: String? (optional)
  - icon_id: String (style image id)
  - track_deg: Double? (OpenSky true_track)
  - raw_category: Int? (optional debug)

Example:

```kotlin
val f = Feature.fromGeometry(Point.fromLngLat(lon, lat))
f.id(icao24)
f.addStringProperty("icao24", icao24)
callsign?.let { f.addStringProperty("callsign", it) }
f.addStringProperty("icon_id", iconId)
trackDeg?.let { f.addNumberProperty("track_deg", it) }
category?.let { f.addNumberProperty("raw_category", it) }
```

---

## 4) SymbolLayer (rotation)
```kotlin
iconImage(get("icon_id"))
iconRotate(coalesce(get("track_deg"), 0.0))
iconRotationAlignment("map")
iconKeepUpright(false)
```

OpenSky defines `true_track` as degrees clockwise from north.  
Source: OpenSky REST docs.

---

## 5) Tap handling → details sheet (must show ICAO)
On tap:
- queryRenderedFeatures(point, arrayOf(ADSB_LAYER_ID))
- extract `icao24` property (or feature.id)
- look up the current `AdsbTarget` in memory by ICAO24
- open bottom sheet

Details sheet must show:
### Live state (always)
- ICAO24
- callsign (if present)
- altitude, speed, track, vertical rate

### Metadata (if available)
Pull from local metadata repository keyed by ICAO24:
- registration
- typecode
- model
- manufacturer name
- operator/operatorcallsign
- icaoaircrafttype

If metadata is missing, show: “Metadata not available”.

Implementation of metadata repository is in `ADSB_AircraftMetadata.md`.
