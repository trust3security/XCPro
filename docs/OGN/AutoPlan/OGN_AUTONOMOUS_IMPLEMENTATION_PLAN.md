# OGN Autonomous Implementation Plan for XCPro (Android)

Last updated: 2026-02-09

This document is a complete, implementation-ready spec intended for an autonomous coding agent (Codex) to integrate Open Glider Network (OGN) live traffic into the XCPro Android application so nearby gliders appear on the map.

It is written to be executed without any follow-up questions.

---

## 0. Objective

Implement a new "glider traffic" overlay backed by OGN live data:

- Connect to OGN APRS feed over TCP (APRS-IS style).
- Subscribe to traffic within a fixed 300 km receive radius around the current map subscription center (camera center on camera-idle).
- Parse APRS lines into aircraft targets (lat/lon, altitude, course, speed, climb, etc).
- Join identity metadata from OGN DDB (registration, competition number, model, privacy flags).
- Display targets as map markers in the XCPro map UI.
- Keep everything lifecycle-scoped: connect only while the map screen is visible AND the overlay is enabled.
- Follow repository architecture rules: SSOT, MVVM, UDF, DI (Hilt), coroutines/Flow, injected clock.

---

## 1. Non-Negotiable XCPro Constraints (must comply)

Codex MUST follow these repo rules. They are not optional:

- UI -> domain -> data dependency direction.
- ViewModels depend on UseCases only.
- No persistence in ViewModels.
- All business logic uses injected clock/time source (no SystemClock/System.currentTimeMillis directly inside domain logic).
- No global mutable singletons; all long-lived components via DI.
- All UI flow collection lifecycle-aware.
- No non-ASCII characters in production Kotlin.
- Do not log user location in release builds.

Reference docs already in the repo:
- ARCHITECTURE.md
- CODING_RULES.md
- PIPELINE.md
- README_FOR_CODEX.md

This plan is compatible with those rules.

---

## 2. External Data Sources

### 2.1 OGN APRS TCP feed (live traffic)

Host and port:
- Host: `aprs.glidernet.org`
- Port: `14580` (filter-enabled port)

Important:
- There are multiple APRS servers behind DNS; connecting to the hostname is expected.
- Port 14580 is the APRS-IS "user-defined filter" port. If no filter is applied, you receive almost nothing beyond messages addressed to you.

Login / handshake (APRS-IS style):
- Client must send a single login line, terminated by newline:
  `user <mycall> pass <passcode> vers <appName> <appVer> filter <filters...>`

Receive-only connections:
- Use `pass -1` for receive-only (no transmit).

Resilience recommendation:
- Implement APRS-IS passcode generation anyway, and prefer a real passcode for the generated client callsign.
- If the server rejects `pass -1`, the connection will fail; having a real passcode avoids that failure mode.

APRS-IS passcode algorithm (standard):
- Input: base callsign only, uppercase, without SSID (strip anything after '-').
- Start hash = 0x73e2.
- XOR each pair of characters: first shifted left 8, second as-is.
- Final passcode = hash & 0x7fff.

Pseudo-code:

    hash = 0x73e2
    s = callsignUpperNoSsid
    for i in range(0, len(s), 2):
        hash ^= (ord(s[i]) << 8)
        if i+1 < len(s):
            hash ^= ord(s[i+1])
    passcode = hash & 0x7fff


Server-side filtering:
- Use a radius filter to limit traffic by distance from the current subscription center:
  `filter r/<lat>/<lon>/<radiusKm>`

Example connection line (300 km radius, receive-only):
- `user OGNXCPro01 pass -1 vers XCPro 0.0.0 filter r/-33.8688/151.2093/300`

Keepalive:
- APRS-IS servers commonly send `#` comment lines. Ignore any line starting with `#`.
- Implement an application-level keepalive timer: if no data is received for N seconds (ex: 120s), treat the connection as stalled and reconnect.

### 2.2 OGN DDB (device database)

Endpoint:
- Base: `https://ddb.glidernet.org`
- Download: `/download`
- JSON flag: `?j=1` (forces JSON)
- Optional: `&t=1` to include `aircraft_type` field.

Important fields (from the official ogn-ddb repo):
- device_type (F/I/O)
- device_id (6 hex)
- aircraft_model
- registration
- cn
- tracked (Y/N)
- identified (Y/N)
- aircraft_type (optional, present when t=1)

Caching:
- Download is large. Cache locally (disk) and refresh on an interval (ex: 24h).
- The UI must not block on DDB download. DDB is enrichment only.

Privacy handling:
- If a device is present in DDB and `tracked == 'N'`, do NOT display it.
- If `tracked == 'Y'` but `identified == 'N'`, display the marker but anonymize label (no reg/cn).

Unknown devices:
- Devices not in DDB may still appear on OGN. Display them as "Unknown" with no identifying labels (OK to show raw device id or callsign if present, but prefer the short hex id).

---

## 3. Data Formats

### 3.1 APRS line format (TNC2)

Each TCP line is typically a TNC2-style APRS packet:

`SRC>DEST,PATH1,PATH2:INFO`

Examples:
- `FLRDDDEAD>APRS,qAS,EDFO:/152345h4903.50N/07201.75W^110/064/A=002526 id0ADDDEAD -019fpm +0.6rot 1.3dB 2.0kHz gps4x5`

Rules:
- Split at first `:` to separate header and info.
- Header split:
  - src = substring before `>`
  - dest+path = substring after `>` split by `,`
- Ignore lines that do not match this structure.

### 3.2 APRS position reports to support

Support these position data type identifiers:
- `!` and `=` : position without timestamp
- `/` and `@` : position with timestamp

Do NOT implement the full APRS spec. Only implement what is needed for OGN traffic:
- Uncompressed lat/lon positions (ddmm.hhN / dddmm.hhE).
- Compressed positions (base91) as a best-effort fallback.
- Course/speed extension `CCC/SSS` (degrees / knots).
- Altitude extension `/A=xxxxxx` (feet).

Any packet not recognized should be ignored safely.

### 3.2A Compressed position decoding (base91) (optional but recommended)

OGN traffic is often uncompressed, but some APRS senders use compressed position encoding.
If you implement it, implement only the specific variant used in position reports:

Format (no timestamp):
- Data type: `!` or `=`
- Symbol table id: 1 char (`/` or `\\`)
- Latitude: 4 base91 chars (YYYY)
- Longitude: 4 base91 chars (XXXX)
- Symbol code: 1 char
- Then: 3 chars `csT` (compressed course/speed or range/altitude) and comment

Format (with timestamp):
- Data type: `/` or `@`
- Timestamp: 7 chars (DHM or HMS forms)
- Then the same compressed block as above

Base91 value:
- For each char c: v = ord(c) - 33  (valid range 0..90)

Decode lat/lon:
- y = v1*91^3 + v2*91^2 + v3*91 + v4
- x = v1*91^3 + v2*91^2 + v3*91 + v4
- lat = 90.0 - (y / 380926.0)
- lon = -180.0 + (x / 190463.0)

Notes:
- Reject if any base91 char is outside `!`..`{`.
- This produces degrees with about 1/1000 minute resolution.
- If you are uncertain, you can skip compressed decoding and still meet MVP needs (uncompressed is enough for OGN in most regions).


### 3.3 Uncompressed lat/lon decoding

Latitude string: `ddmm.hhN` (8 chars)
Longitude string: `dddmm.hhE` (9 chars)

Conversion:
- decimal_degrees = deg + (minutes / 60.0)
- minutes = mm.hh (whole minutes + hundredths)
- Apply sign:
  - Lat: N positive, S negative
  - Lon: E positive, W negative

### 3.4 Course/speed decoding (optional but recommended)

Course/speed extension pattern: `CCC/SSS`
- Course: 001-360 degrees (0/000 can mean unknown)
- Speed: knots

Convert speed:
- m/s = knots * 0.514444

### 3.5 Altitude decoding (optional but recommended)

Look for substring `/A=xxxxxx` (six digits), representing altitude in feet.

Convert:
- meters = feet * 0.3048

### 3.6 OGN-specific comment tokens (best-effort enrichment)

OGN uses extra tokens in the comment field, separated by spaces.

Common tokens:
- `id0ADDDEAD`  (OGN device id token; last 6 hex chars are the device id)
- `-019fpm`     (vertical speed in feet per minute)
- `+0.6rot`     (rate of turn; unit depends on sender; treat as deg/s for display)
- `1.3dB`       (signal)
- `2.0kHz`      (frequency offset)
- `gps4x5`      (GPS quality indicator; parse as ints if possible)

Parsing rule:
- Split comment by spaces.
- For each token:
  - If it matches `^id[0-9A-Fa-f]{8}$`, extract device id = last 6 hex.
  - Else parse as a generic (value + unit) token and store into a map for debug display.

Vertical speed conversion:
- fpm -> m/s via (fpm * 0.00508)

If the id token is missing:
- If src callsign matches `^[A-Z]{3}[0-9A-F]{6}$`, use last 6 hex as device id.
- Otherwise fall back to src callsign as the stable target id.

---

## 4. Architecture and Component Design

This is the required dependency layering for this feature:

UI (map overlay + toggle)
  -> GliderTrafficUseCase (domain orchestrator)
     -> GliderTrafficRepository (SSOT traffic store)
        -> OgnAprsTcpClient (network ingest)
        -> OgnAprsParser (parsing)
        -> OgnDdbRepository (identity cache)

### 4.1 Domain models

Create domain models (no Android/map types):

- GliderTrafficTarget
  - id: String (stable unique key)
  - deviceId: String? (6 hex, uppercase) if known
  - srcCallsign: String
  - lat: Double
  - lon: Double
  - altitudeM: Double?
  - courseDeg: Double?
  - groundSpeedMps: Double?
  - climbMps: Double?
  - lastUpdateMonoMs: Long
  - identity: GliderTrafficIdentity? (from DDB)
  - raw: optional struct/map for debug

- GliderTrafficIdentity
  - registration: String?
  - cn: String?
  - aircraftModel: String?
  - tracked: Boolean?
  - identified: Boolean?
  - aircraftTypeCode: Int? (optional)

- GliderTrafficSnapshot
  - targets: List<GliderTrafficTarget>
  - connectionState: ConnectionState (Disconnected/Connecting/Connected/Error)
  - lastError: String? (sanitized; no raw location)

### 4.2 Repository (SSOT)

Responsibilities:
- Own the authoritative list of currently-known targets.
- Merge new beacons into existing targets by id.
- Evict stale targets (ex: no update for 120 seconds).
- Apply privacy rules (tracked/identified) when identity is known.
- Expose a StateFlow<GliderTrafficSnapshot>.

DO NOT:
- Depend on UI or map types.
- Log locations in release.

### 4.3 UseCase (orchestrator + lifecycle)

Responsibilities:
- Provide an API for the ViewModel to start/stop the feature without depending on data layer.
- Translate UI intents (enabled toggle, map camera state) into subscription and display specs.
- Ensure the repository's network ingest runs only when:
  - map screen is visible
  - overlay enabled
  - map center location is known (lat/lon)

Implementation guidance:
- Provide:
  - `fun setMapVisible(isVisible: Boolean)`
  - `fun setEnabled(enabled: Boolean)`
  - `fun setCameraViewport(centerLat: Double, centerLon: Double, northLat: Double, southLat: Double, eastLon: Double, westLon: Double, zoomLevel: Double)` (driven from map camera-idle events)
- UseCase computes:
  - a fixed receive `SubscriptionSpec(center, radiusKm = 300)` for repository ingest
  - a viewport `DisplaySpec(bounds)` for UI rendering

### 4.4 Network ingest (TCP client)

Requirements:
- Use a cancellable coroutine running on Dispatchers.IO.
- Connect to `aprs.glidernet.org:14580`.
- Send login line with filter `r/lat/lon/radiusKm`.
- Read lines using BufferedReader.readLine() in a loop.
- On IOException / EOF:
  - close socket
  - update connectionState Error
  - exponential backoff reconnect (ex: 1s, 2s, 4s, ... max 60s)
- On cancellation:
  - close socket, stop immediately

Filter update strategy (must be stable):
- Do not reconnect on every camera frame or tiny pan.
- Use camera-idle + debounce + move-threshold:
  - Process camera updates only when camera becomes idle (or equivalent map callback).
  - Debounce updates by ~1-2 seconds to avoid reconnect storms.
  - Reconnect only if center moved >= 75 km OR enabled toggled OR no active subscription exists.
  - Zoom-only changes must update display filtering immediately, but should not force reconnect unless center threshold is crossed.

### 4.5 DDB repository (identity cache)

Responsibilities:
- Download JSON in background, cache to disk.
- Provide lookup by deviceId (6 hex).
- Return null if unknown.
- Provide lastUpdateWallMs for diagnostics.

Implementation guidance:
- Use HttpURLConnection or existing OkHttp stack.
- URL to use:
  - `https://ddb.glidernet.org/download/?j=1&t=1`
- Refresh interval: 24 hours (config constant).
- Cache file in app internal storage.

Unit testing:
- Parse a tiny JSON fixture to validate model mapping.

---

## 5. Map Integration (UI Layer)

The UI implementation will depend on how XCPro currently renders the map (MapLibre stack, overlay stack, etc).

Codex MUST do this discovery step in the real repo:
1. Find Map screen implementation.
   - Search for MapScreenViewModel
   - Search for map overlay stack/controller
2. Find how existing overlays are added (terrain, wind, etc).
3. Add a new overlay controlled by a boolean flag and driven by a Flow of targets.

### 5.1 Overlay behavior

- When overlay enabled: show traffic markers.
- Markers update in near-real-time as new packets arrive.
- Hide markers that are stale (>120s without update).
- Display only targets inside the current map viewport bounds.
- Receive radius is fixed at 300 km around the active subscription center.
- If user zooms to a ~100 km map extent, only gliders within those visible bounds are shown.
- If user zooms out, more in-view targets are shown, limited to currently received data (<=300 km from subscription center).
- If user pans far enough that new area is outside current receive coverage, resubscribe on camera-idle and then display new targets as data arrives.

### 5.2 GeoJSON strategy (recommended)

If the map stack supports MapLibre GeoJSON sources:
- Build a FeatureCollection for targets.
- Each Feature has:
  - geometry: Point(lon, lat)
  - properties:
    - id
    - label (cn or registration or short id)
    - courseDeg (for rotation)
    - altitudeM
    - climbMps
- Style:
  - simplest: circle layer for points + symbol layer for text labels.

Avoid images if possible. Use circles/text first, then icons later.

### 5.3 Marker selection / details (nice-to-have)

If XCPro already supports tapping features:
- On marker tap, show a bottom sheet/card with:
  - label (cn/registration)
  - altitude (m)
  - speed (km/h or kts)
  - climb (m/s)
  - last update age
  - model (if known)

This is optional for MVP but recommended.

---

## 6. UI and Settings

Minimum UI:
- Add a toggle in map overlay menu: "Glider traffic" (avoid vendor branding in user strings).
- Show receive policy text in overlay/settings: "Live receive radius: 300 km (fixed for MVP)".
- Add a small debug section (dev build) with:
  - connection state
  - active subscription center (lat/lon rounded)
  - receive radius (300 km)
  - current viewport bounds
  - last reconnect time / backoff
  - DDB cache age

Persistence:
- Store enabled toggle using the app's existing settings mechanism (likely DataStore).
- ViewModel MUST NOT directly use SharedPreferences.

---

## 7. Testing Plan (must be implemented)

Unit tests (no Android framework):

1. APRS parser tests
   - Parse the provided sample beacon.
   - Assert lat/lon conversion.
   - Assert course/speed parsing.
   - Assert altitude conversion.
   - Assert deviceId extraction from id token.

2. Comment token parser tests
   - `-019fpm` -> climbMps negative
   - `id0ADDDEAD` -> deviceId DDDEAD

3. DDB JSON parsing tests
   - Parse a minimal JSON fixture with 1 device and validate fields.

4. Repository update + eviction tests
   - Feed 2 beacons for same id -> state updates (no duplicates).
   - Advance fake clock -> stale targets removed.

5. Privacy rules tests
   - If DDB says tracked=N -> target excluded.
   - If tracked=Y identified=N -> label anonymized.

6. Viewport and subscription policy tests
   - Given received targets inside 300 km, only targets inside current viewport are emitted to overlay.
   - Zoom change with unchanged center updates shown target set without forcing reconnect.
   - Pan where center shift >= 75 km triggers subscription update/reconnect.

Determinism:
- Use fake clock/time source in tests.
- Use deterministic distance math for move-threshold tests.
- No wall time dependencies in domain logic.

---

## 8. Acceptance Criteria

Done means:

- With overlay enabled, glider markers appear on the map within ~10 seconds in an area known to have OGN coverage.
- Markers move as traffic updates.
- Targets disappear after being stale.
- If map is zoomed to about 100 km extent, only gliders inside visible map bounds are rendered.
- Zooming out reveals more targets in-view, limited to the current 300 km receive subscription.
- Panning far enough updates subscription and populates new area after reconnect/data arrival.
- DDB enrichment shows registration/cn when identified.
- tracked=N devices never appear.
- All unit tests pass.
- `./gradlew enforceRules` passes.
- No architecture violations, no new deviations.

---

## 9. Implementation Checklist (Codex execution order)

1. Read mandatory repo docs in order (README_FOR_CODEX.md).
2. Create domain models + repository interface.
3. Implement APRS parser with unit tests.
4. Implement TCP client and repository ingest loop with fake client tests.
5. Implement DDB repository + parsing tests.
6. Wire UseCase to drive repository subscription.
7. Integrate with MapScreenViewModel + UI toggle.
8. Add map overlay rendering (GeoJSON).
9. Add debug panel / diagnostics.
10. Run full test + enforce rules pipeline.

---

## Appendix A: Recommended Constants

- RECEIVE_RADIUS_KM = 300
- STALE_TARGET_MS = 120_000
- FILTER_UPDATE_MIN_MOVE_KM = 75
- FILTER_UPDATE_DEBOUNCE_MS = 2_000
- TCP_READ_TIMEOUT_MS = 180_000
- RECONNECT_BACKOFF_START_MS = 1_000
- RECONNECT_BACKOFF_MAX_MS = 60_000
- DDB_REFRESH_INTERVAL_MS = 24 * 60 * 60 * 1000

---

## Appendix B: Notes on Safety and Data Usage

OGN data usage rules exist. XCPro should:
- Only request what it needs (radius filter).
- Avoid storing or sharing OGN traffic data without user action.
- Avoid logs containing precise lat/lon.
