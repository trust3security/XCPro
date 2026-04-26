# OGN_PROTOCOL_NOTES.md - XCPro OGN Integration (Authoritative)

Purpose:
Define protocol and runtime behavior currently implemented by XCPro OGN traffic.

## Endpoint And Transport

- Host: `aprs.glidernet.org`
- Port: `14580`
- Transport: TCP, line-oriented APRS-IS style feed
- Client callsign: per-install generated APRS callsign (`^[A-Z][A-Z0-9]{2,8}$`)
- App version token in login: `XCPro 0.1`

Login line format:
`user <client_callsign> pass <APRS_PASSCODE> vers XCPro 0.1 filter r/<lat>/<lon>/<radius_km>`

Notes:
- `<APRS_PASSCODE>` is generated in code from the active client callsign using APRS hash logic.
- Client callsign is persisted once and reused across app restarts.
- `<radius_km>` is kilometers.
- Configurable receive radius range: `20..300 km` (`150 km` default).
- Advanced auto-radius mode (optional) chooses effective radius from
  `40 / 80 / 150 / 220 km` buckets.

## Stream Gate And Center Source

Streaming is enabled only when all are true:
- `allowSensorStart`
- `mapVisible`
- `ognOverlayEnabled`
- `mapVisible` tracks screen lifecycle at `STARTED` level (not dropped on transient `ON_PAUSE`)
  to avoid unnecessary stop/start reconnect churn while returning from overlays/settings.

Center source:
- ownship GPS (`mapLocation`) only
- no camera-center fallback for OGN
- repository waits for center before connecting

## Filtering And Reconnect Policy

- Server filter radius: `radius_km` from settings (`20..300`, default `150`)
- Client haversine filter: `<= radius_km` around latest requested GPS center
  (fallback to active subscription center when requested center is unavailable)
- Reconnect when center moves `>= 20 km`
- Reconnect when effective receive radius changes
- Policy reconnects (center/radius changes) are immediate and bypass error backoff.
- Auto-radius source inputs:
  - map zoom level
  - ownship ground speed
  - ownship flying-state flag
- Auto-radius policy:
  - flight context has priority over zoom
  - bucket set: `40 / 80 / 150 / 220 km`
  - candidate dwell: `30_000 ms`
  - minimum apply interval: `60_000 ms`
- Stream state remains `CONNECTING` until `logresp ... verified` or first valid traffic frame
- Keepalive every `60_000 ms`
- Read timeout: `20_000 ms`
- Stall timeout: `120_000 ms`
- Inbound-only liveness authority:
  - inbound APRS/server lines update stream activity
  - outbound keepalive writes do not reset stream stall timer
- DDB refresh due-check also runs during active connected sessions (not reconnect-only).
- Reconnect backoff: `1_000 -> 2_000 -> ... -> 60_000 ms` max

## Target Lifecycle And Display

- Stale visual threshold: `60_000 ms`
- Eviction threshold: `120_000 ms`
- Overlay render cap: `500 targets`
- OGN glider trail render cap: newest `12,000` segments
- Overlay disabled behavior: UI renders `emptyList()` into OGN overlay runtime
- Trail overlay visibility gate: trails render only when both `ognOverlayEnabled` and
  `showSciaEnabled` are true

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
- timing behavior:
  - parser extracts source-time candidates from `/hhmmssh` and `@ddhhmmz` timestamp forms.
  - repository applies source-time anti-rewind policy before committing target position.
  - repository enforces timed-source lock for untimed frames and permits untimed
    fallback only after timed-source silence window (`30_000 ms`).
  - repository applies motion plausibility validation (distance/time speed gate).
    - source-time delta preferred; monotonic delta fallback used when source-time
      is missing.
  - dropped-frame diagnostics are published via OGN snapshot counters.

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
  - unambiguous same-hex typed fallback when the packet type disagrees with DDB type
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

## Display Update Mode (UI-Only)

Configured setting:
- `OGN display update mode`: `real_time` / `balanced` / `battery`

Behavior:
- Applies only to map overlay redraw cadence for OGN traffic/thermal/trail layers.
- Does not change OGN socket ingest, parsing, or repository publish semantics.

## Tests

- Parser coverage:
  - `feature/map/src/test/java/com/trust3/xcpro/ogn/OgnAprsLineParserTest.kt`
- Distance and viewport math:
  - `feature/map/src/test/java/com/trust3/xcpro/ogn/OgnSubscriptionPolicyTest.kt`
- Track stabilization:
  - `feature/map/src/test/java/com/trust3/xcpro/ogn/OgnTrackStabilizerTest.kt`
- Repository policy helpers:
  - `feature/map/src/test/java/com/trust3/xcpro/ogn/OgnTrafficRepositoryPolicyTest.kt`
- Repository connection and login identity:
  - `feature/map/src/test/java/com/trust3/xcpro/ogn/OgnTrafficRepositoryConnectionTest.kt`
  - `feature/map/src/test/java/com/trust3/xcpro/ogn/OgnClientCallsignTest.kt`
- DDB parser:
  - `feature/map/src/test/java/com/trust3/xcpro/ogn/OgnDdbJsonParserTest.kt`
- Preferences and VM center wiring:
  - `feature/map/src/test/java/com/trust3/xcpro/ogn/OgnTrafficPreferencesRepositoryTest.kt`
  - `feature/map/src/test/java/com/trust3/xcpro/map/MapScreenViewModelTest.kt`

## Reliability Hardening Plan (Implemented)

- `docs/OGN/CHANGE_PLAN_OGN_CONNECTIVITY_RELIABILITY_2026-03-01.md`
