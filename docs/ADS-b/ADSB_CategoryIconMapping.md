# ADSB_CategoryIconMapping.md — OpenSky category → XCPro icon buckets
**v5: category is for ICONS only (not registration/model)**

OpenSky category is an ADS‑B emitter category bucket.
It is frequently 0/1 (no info). That is normal.

OpenSky docs:
https://openskynetwork.github.io/opensky-api/rest.html

Aircraft identification (registration/typecode/model) is via ICAO24 metadata DB:
See `ADSB_AircraftMetadata.md`.

## Icon mapping
2,3  -> PLANE_LIGHT
4,5,6 -> PLANE_LARGE
7 -> PLANE_LARGE (high performance)
8 -> HELICOPTER
9 -> GLIDER
10 -> BALLOON
11 -> PARACHUTIST
12 -> HANGGLIDER
14 -> DRONE
else -> UNKNOWN
