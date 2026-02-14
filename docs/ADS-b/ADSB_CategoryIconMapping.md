# ADSB_CategoryIconMapping.md - OpenSky category to XCPro icon buckets

Version: 2026-02-14

Category is an ADS-B emitter bucket. It is often `0`/`1` (no info), which is normal.

OpenSky category docs:
https://openskynetwork.github.io/opensky-api/rest.html

Aircraft identity fields (registration/typecode/model) come from ICAO24 metadata:
See `ADSB_AircraftMetadata.md`.

## Current precedence (runtime)

1. Heavy category (`6`) -> heavy icon.
2. Metadata-driven classification:
   - Typecode heuristics first.
   - ICAO aircraft class decode second.
3. ICAO24 hard override list (only for non-heavy results).
4. OpenSky category fallback.
5. Unknown.

## OpenSky category fallback mapping

- `2`, `3` -> `PLANE_LIGHT`
- `4` -> `PLANE_MEDIUM`
- `5`, `7` -> `PLANE_LARGE`
- `6` -> `PLANE_HEAVY`
- `8` -> `HELICOPTER`
- `9` -> `GLIDER`
- `10` -> `BALLOON`
- `11` -> `PARACHUTIST`
- `12` -> `HANGGLIDER`
- `14` -> `DRONE`
- else -> `UNKNOWN`
