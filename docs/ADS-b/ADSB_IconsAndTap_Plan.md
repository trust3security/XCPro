# ADSB_IconsAndTap_Plan.md — icons, rotation, tap, details
**v5: Feature.id = ICAO24 + show metadata fields**

## 1) Icons
28dp baseline icon files:
- ic_adsb_plane_light.png
- ic_adsb_plane_large.png
- ic_adsb_helicopter.png
- ic_adsb_glider.png
- ic_adsb_balloon.png
- ic_adsb_parachutist.png
- ic_adsb_hangglider.png
- ic_adsb_drone.png
- ic_adsb_unknown.png

## 2) GeoJSON Feature identity (no flicker)
MUST:
- `Feature.id = icao24`

Also include properties:
- `icao24`
- `icon_id`
- `track_deg`
- `category` (optional)

## 3) SymbolLayer rotation
```kotlin
iconImage(get("icon_id"))
iconRotate(coalesce(get("track_deg"), 0.0))
iconRotationAlignment("map")
iconKeepUpright(false)
```

## 4) Tap → details sheet
Tap extracts `icao24` (feature.id or property).
Look up the current target by `icao24`.
Details sheet MUST show:
- ICAO24 always
- live state (alt/speed/track/vertical rate)
- emitter category label+raw int
- metadata (registration/typecode/model) if available

Metadata implementation:
See `ADSB_AircraftMetadata.md`.
