# ADSB_CategoryIconMapping.md - OpenSky category to XCPro icon buckets

Version: 2026-02-21

Category is an ADS-B emitter bucket. It is often `0`/`1` (no info), which is normal.

OpenSky category docs:
https://openskynetwork.github.io/opensky-api/rest.html

Aircraft identity fields (registration/typecode/model) come from ICAO24 metadata:
See `ADSB_AircraftMetadata.md`.

## Current precedence (runtime)

Implemented in:
- `feature/map/src/main/java/com/example/xcpro/adsb/domain/AdsbAircraftClassResolver.kt`
- `feature/map/src/main/java/com/example/xcpro/adsb/ui/AdsbAircraftIconMapper.kt`

Resolution order:

1. Authoritative non-fixed-wing category gate:
   - Category `6` (heavy) and categories `8`, `9`, `10`, `11`, `12`, `14` are authoritative.
   - These categories are not overridden by conflicting fixed-wing metadata.
2. Metadata classification:
   - Typecode heuristics + ICAO aircraft class decode.
   - Helicopter ICAO class (`H*`) wins conflict resolution.
   - Weak fixed-wing typecode fallback does not override a stronger ICAO class.
3. ICAO24 hard override list for large icon:
   - Applied only after metadata classification and never above heavy classification.
4. OpenSky category fallback mapping.
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

## Unknown visual fallback contract

- Semantic classification remains `UNKNOWN` when category/metadata cannot classify.
- Map icon rendering for `UNKNOWN` uses a neutral fixed-wing asset:
  - drawable: `ic_adsb_plane_medium.png`
  - style id: `adsb_icon_unknown`
- This avoids question-mark icon churn while preserving unknown truth semantics for details/state.

## Helicopter typecode coverage

Runtime prefix list includes:

- `R22`, `R44`, `R66`, `EC`, `AW`, `H60`, `UH`
- `B06`, `B47`, `B407`, `B429`
- `A109`, `A119`, `A139`, `AS50`
- `H269`, `H500`
- `MI8`, `MI17`
- `S76`, `S92`

## Regression coverage

Validated by:

- `feature/map/src/test/java/com/example/xcpro/adsb/ui/AdsbAircraftIconMapperTest.kt`

Test coverage includes:

- Non-fixed-wing category authority against conflicting metadata.
- Helicopter-vs-fixed-wing conflict precedence.
- Common helicopter typecode prefixes (including digit-suffixed variants such as `B47G`).
- Heavy-category priority and ICAO24 override guardrails.
