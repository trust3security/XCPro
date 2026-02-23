# OGN_PROTOCOL_NOTES.md - XCPro OGN Integration (Authoritative)

Purpose:
Define protocol and runtime behavior currently implemented by XCPro OGN traffic.

## Endpoint And Transport

- Host: `aprs.glidernet.org`
- Port: `14580`
- Transport: TCP, line-oriented APRS-IS style feed
- Client callsign: `OGNXC1`
- App version token in login: `XCPro 0.1`

Login line format:
`user OGNXC1 pass <APRS_PASSCODE> vers XCPro 0.1 filter r/<lat>/<lon>/150`

Notes:
- `<APRS_PASSCODE>` is generated in code from `OGNXC1` using APRS hash logic.
- Radius `150` is kilometers (300 km diameter contract).

## Stream Gate And Center Source

Streaming is enabled only when all are true:
- `allowSensorStart`
- `mapVisible`
- `ognOverlayEnabled`

Center source:
- ownship GPS (`mapLocation`) only
- no camera-center fallback for OGN
- repository waits for center before connecting

## Filtering And Reconnect Policy

- Server filter radius: `150 km`
- Client haversine filter: `<= 150 km` around latest requested GPS center
  (fallback to active subscription center when requested center is unavailable)
- Reconnect when center moves `>= 20 km`
- Stream state remains `CONNECTING` until `logresp ... verified` or first valid traffic frame
- Keepalive every `60_000 ms`
- Read timeout: `20_000 ms`
- Stall timeout: `120_000 ms`
- Reconnect backoff: `1_000 -> 2_000 -> ... -> 60_000 ms` max

## Target Lifecycle And Display

- Stale visual threshold: `60_000 ms`
- Eviction threshold: `120_000 ms`
- Overlay render cap: `500 targets`
- Overlay disabled behavior: UI renders `emptyList()` into OGN overlay runtime

## Parsing Rules Implemented

Accepted lines:
- non-empty and not starting with `#`
- valid APRS header/payload split by `:`
- payload type in `!`, `=`, `/`, `@`
- destination `OGNSDR` is ignored

Extracted fields:
- source callsign and destination
- latitude/longitude (APRS uncompressed position)
- course/speed token (`ddd/sss`) when present
  - `000` course is treated as unknown
  - `001..360` accepted
  - `>360` rejected
- altitude from `/A=xxxxxx` feet to meters
- vertical speed from `fpm` token to m/s
- signal from `dB` token
- device id from `idXXXXXX` / `idXXYYYYYY` or callsign fallback pattern (`AAA123456` -> `123456`)
- typed `idXXYYYYYY` aircraft type decode from `XX` (`STttttaa` -> `tttt`) used when DDB type is unavailable
- address type (`FLARM` / `ICAO` / `UNKNOWN`) from:
  - typed `idXXYYYYYY` low bits when explicit type byte is present
  - callsign prefix fallback (`FLR*` -> FLARM, `ICA*` -> ICAO) when id token has no type byte
  - unknown fallback otherwise
- canonical transport key (`FLARM:HEX` / `ICAO:HEX` / `UNK:HEX` / fallback `ID:*`) for repository identity paths

Drop conditions:
- invalid coordinates
- unparseable/unsupported payload formats
- malformed 7-hex `id` token (`idXXXXXXX`) is rejected

`id` token acceptance:
- accepted: exactly 6 hex (`idYYYYYY`) or 8 hex (`idXXYYYYYY`)
- rejected: any other length/non-hex form

## DDB Enrichment And Privacy

- DDB source: `https://ddb.glidernet.org/download/?j=1&t=1`
- Refresh cadence:
  - load cache from disk if available
  - refresh due every 24h (checked hourly while running)
- Identity/type precedence:
  - DDB identity is primary
  - typed APRS id aircraft type is fallback when DDB aircraft type is missing
- DDB lookup precedence:
  - typed lookup (`device_type + device_id`) when parsed target type is known
  - unknown-safe fallback for untyped targets
- Label resolution:
  - DDB competition number -> DDB registration -> fallback id/callsign
- If DDB reports `tracked == false`, target is removed from output list.

## Ownship Suppression

Configured settings:
- `Own FLARM ID` (6-hex, uppercase normalized)
- `Own ICAO24` (6-hex, uppercase normalized)

Match policy:
- FLARM target matches only own FLARM setting.
- ICAO target matches only own ICAO setting.
- UNKNOWN target type does not match by default.

Behavior:
- Match is applied in `OgnTrafficRepository` before publishing target list.
- Suppressed target canonical keys are emitted in snapshot diagnostics.
- OGN trail and thermal repositories consume suppression keys and purge matching artifacts in-session.

## Tests

- Parser coverage:
  - `feature/map/src/test/java/com/example/xcpro/ogn/OgnAprsLineParserTest.kt`
- Distance and viewport math:
  - `feature/map/src/test/java/com/example/xcpro/ogn/OgnSubscriptionPolicyTest.kt`
- Track stabilization:
  - `feature/map/src/test/java/com/example/xcpro/ogn/OgnTrackStabilizerTest.kt`
- Repository policy helpers:
  - `feature/map/src/test/java/com/example/xcpro/ogn/OgnTrafficRepositoryPolicyTest.kt`
- DDB parser:
  - `feature/map/src/test/java/com/example/xcpro/ogn/OgnDdbJsonParserTest.kt`
- Preferences and VM center wiring:
  - `feature/map/src/test/java/com/example/xcpro/ogn/OgnTrafficPreferencesRepositoryTest.kt`
  - `feature/map/src/test/java/com/example/xcpro/map/MapScreenViewModelTest.kt`
