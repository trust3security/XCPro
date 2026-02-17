# ADSB_CategoryIconMapping.md - OpenSky category to XCPro icon buckets

Version: 2026-02-17

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

## Deep-dive findings (2026-02-17)

Current runtime behavior has verified defects that can map helicopters to airplane icons:

1. Rotorcraft category is not authoritative.
   - Only heavy (`6`) is hard-locked before metadata.
   - Category `8` can still be overridden by metadata heuristics.
2. Typecode currently wins over ICAO aircraft class in most conflicts.
   - If typecode classifies as fixed-wing and ICAO class says helicopter (`H*`), fixed-wing often wins.
3. Helicopter typecode prefix coverage is too narrow.
   - Current list: `R22`, `R44`, `R66`, `EC`, `AW`, `H60`, `UH`.
4. Generic fixed-wing fallback is broad.
   - Many helicopter typecodes with digits are captured by fixed-wing fallback and assigned plane icons.
5. Additional mapper defect:
   - `MEDIUM_FIXED_WING_TYPECODE_PREFIXES` duplicates twin-prop prefixes, so that branch is effectively dead.

Measured impact from a 2026-02-17 run against OpenSky `aircraftDatabase.csv`:

- Rows with helicopter ICAO class (`icaoaircrafttype` starts with `H`): `5484`
- Rows classified as non-helicopter by current runtime mapper logic: `3065` (~55.9%)
- Common missed helicopter typecodes include:
  - `B06`, `AS50`, `H269`, `B407`, `H500`, `A139`, `B47G`, `A109`, `MI8`, `S76`, `B429`

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

## Recommended correction order

1. Make non-fixed-wing categories authoritative for icon family (`8`, `9`, `10`, `11`, `12`, `14`).
2. Change conflict policy so helicopter ICAO class cannot be overridden by weak fixed-wing typecode fallback.
3. Expand helicopter typecode recognition and reduce ambiguous fixed-wing fallback.
4. Add mapper regression tests for category/class/typecode conflicts and common helicopter typecodes.
