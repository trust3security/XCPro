
# OGN.md -- Open Glider Network live traffic in XCPro (Android)

Goal: Add an OGN (Open Glider Network) live-traffic map to XCPro that shows nearby gliders/aircraft in real time, enriched with OGN Device Database (DDB) details, and designed so Codex can implement end-to-end without asking you questions.

This plan assumes:
- Android app (Kotlin), Jetpack Compose UI, MVVM + UDF/SSOT patterns.
- Coroutines + Flow.
- Map UI: Google Maps Compose (recommended) or Mapbox (swapable via an interface).
- Phone connects directly to OGN APRS TCP feed first. (Optional backend relay" section included.)

---

## 0) Product requirements (tight and testable)

### Must have (v1)
1. Live traffic stream from OGN/APRS (TCP).
2. Server-side geographic filter: show aircraft within radius of map center (or device position).
3. Parse positions into AircraftState objects.
4. Resolve device IDs to registration/CN/type using OGN DDB JSON (cached).
5. Map screen:
   - markers for aircraft
   - heading/track indicator
   - stale fade/grey (e.g., >60s)
   - remove expired (e.g., >120s)
6. Tap marker -> details sheet (reg, CN, type, altitude, speed, last seen).
7. Settings:
   - radius km (default 250)
   - expiry seconds (default 120)
   - stale threshold seconds (default 60)
   - update/throttle interval for UI (e.g., 2-5 Hz)
   - optionally: follow me" / follow map center"
8. Robustness:
   - auto-reconnect
   - backoff with jitter
   - socket timeouts
   - battery-safe: stop streaming when screen not visible (lifecycle).

### Nice-to-have (v2)
- Marker clustering
- Trails (last N minutes)
- Aircraft type icons (glider/tow/para/heli/unknown)
- Only gliders" filter
- My club area" presets
- Offline DDB cache warm-up.

---

## 1) Architecture (clean, swappable, review-proof)

### Modules / packages
- `ogn/`
  - `data/`
  - `domain/`
  - `ui/`
- Keep everything behind interfaces so you can replace direct APRS" with backend relay" later.

### Data flow
OGN socket -> parsed `AircraftUpdate` -> reducer updates in-memory store -> `StateFlow<List<AircraftViewItem>>` -> UI markers.

### Single source of truth (SSOT)
- `OgnTrafficStore` holds a `MutableStateFlow<Map<DeviceId, AircraftState>>`
- `OgnViewModel` exposes `StateFlow<OgnUiState>`

---

## 2) Data model (domain)

Create these in `ogn/domain/model/`:

```kotlin
@JvmInline value class DeviceId(val raw: String) // e.g., FLARM hex or OGN device id

data class AircraftState(
    val id: DeviceId,
    val lat: Double,
    val lon: Double,
    val altM: Double?,          // altitude meters (if provided)
    val trackDeg: Double?,      // course/heading
    val groundSpeedMps: Double?,
    val climbMps: Double?,      // if available or derived
    val lastUpdateEpochMs: Long,
    val source: String,         // "OGN-APRS"
    val flags: Set<AircraftFlag> = emptySet()
)

enum class AircraftFlag { STALE, EXPIRED, PRIVACY }

data class DdbEntry(
    val id: DeviceId,
    val registration: String?,  // e.g. VH-XXX
    val cn: String?,            // contest number
    val aircraftType: String?,  // model/type if present
    val tracked: Boolean?,      // from DDB
    val identified: Boolean?    // from DDB
)

data class AircraftViewItem(
    val id: DeviceId,
    val lat: Double,
    val lon: Double,
    val altM: Double?,
    val trackDeg: Double?,
    val speedMps: Double?,
    val climbMps: Double?,
    val labelPrimary: String,   // CN or reg or id
    val labelSecondary: String, // type or status
    val ageSec: Int,
    val isStale: Boolean
)
```

---

## 3) APRS / OGN TCP client (data)

### Interface
`ogn/data/aprs/OgnAprsClient.kt`

```kotlin
interface OgnAprsClient {
    suspend fun connect()
    suspend fun disconnect()
    fun updates(): Flow<String> // raw APRS lines
    suspend fun setGeoFilter(centerLat: Double, centerLon: Double, radiusKm: Int)
}
```

### Implementation
`OgnAprsTcpClient`:
- Uses `Socket(host, port)` and `BufferedReader` loop.
- Emits lines via `callbackFlow`.
- Reconnect logic inside a wrapper `OgnAprsSession` or in repository layer.

**Config**
```kotlin
data class OgnConnectionConfig(
    val host: String = "aprs.glidernet.org",
    val port: Int = 14580,
    val callsign: String = "XCPro",   // settable
    val passcode: String? = null,     // optional; many feeds accept -1 for read-only
    val appVersion: String = BuildConfig.VERSION_NAME,
    val socketReadTimeoutMs: Int = 20_000
)
```

**Login line**
APRS-IS typically expects a login line similar to:
`user <CALLSIGN> pass <PASS> vers <APP> <VER>`

Implement it, but allow config override if needed.

### OGN / APRS session details (practical)
- Direct feed: aprs.glidernet.org:14580 (APRS-IS style TCP).
- Read-only login commonly uses passcode -1.
- You can include filter on the login line, for example:
  `user <CALLSIGN> pass -1 vers XCPro <VER> filter r/<lat>/<lon>/<km>`
- Server comment / keepalive lines start with '#'; ignore them in the parser.
- Send a periodic keepalive line starting with '#' (e.g. "# keepalive") every 30-60s if idle.
- OGN extends APRS by adding fields in the comment section; parse only what you need.

### Filter handling
Implement server-side filter command string:
- Range filter example pattern: `filter r/<lat>/<lon>/<km>`
- Only update filter if center moved more than e.g. 10-20km to reduce churn.
- Reconnect after filter update if server doesn't support mid-session changes reliably (configurable behavior).

---

## 4) APRS message parsing (data -> domain)

Create `ogn/data/aprs/AprsParser.kt`:

```kotlin
interface AprsParser {
    fun parse(line: String, nowEpochMs: Long): AircraftState? // null if irrelevant/unparseable
}
```

Implementation notes:
- Keep parser tolerant. APRS is messy.
- You only need position reports relevant to OGN aircraft.
- Extract:
  - device id (from source callsign / address field)
  - lat/lon (APRS format variants: compressed/uncompressed)
  - track/speed if present
  - altitude if present (often in feet in APRS; convert to meters)
- If you can't parse some fields, still output lat/lon + timestamp.

**Unit tests are mandatory**:
- Provide a `test/resources/aprs_samples.txt` with real-ish lines (sanitized).
- Tests validate parsing correctness and edge cases.

---

## 5) DDB (device database) enrichment

### Interface
`ogn/data/ddb/DdbRepository.kt`

```kotlin
interface DdbRepository {
    suspend fun refreshIfNeeded()
    fun lookup(id: DeviceId): DdbEntry?
}
```

### Storage strategy
- Download DDB JSON and cache it in:
  - `Room` table (best) OR
  - simple file cache in app storage + in-memory map (v1).
- DDB download endpoint (JSON): `https://ddb.glidernet.org/download`
- Refresh cadence:
  - at app start
  - then every 24h (configurable)
  - manual Refresh DDB" button in settings.

### JSON parsing
- Use Kotlinx Serialization or Moshi.
- Convert to `Map<DeviceId, DdbEntry>`.

**Privacy**
- If DDB says not tracked/identified, you may:
  - still display but hide reg/CN (show anonymized) OR
  - respect a strict don't show" rule depending on your policy.
Decide policy in code as a config flag:
- `respectDdbPrivacy: Boolean = true`

---

## 6) Traffic store + reducer

`ogn/domain/store/OgnTrafficStore.kt`

```kotlin
class OgnTrafficStore(
    private val staleSec: Int,
    private val expireSec: Int,
    private val clock: Clock = SystemClockClock()
) {
    private val _states = MutableStateFlow<Map<DeviceId, AircraftState>>(emptyMap())
    val states: StateFlow<Map<DeviceId, AircraftState>> = _states.asStateFlow()

    fun onUpdate(update: AircraftState) { /* reduce */ }
    fun purgeExpired() { /* remove old */ }
}
```

Implement:
- `onUpdate`: replace entry by id (keep latest).
- `purgeExpired`: remove where `(now - lastUpdate) > expireSec`.

Run purge on a ticker every 5-10 seconds while streaming.

---

## 7) Repository layer (connect + parse + store + enrich)

`ogn/domain/OgnTrafficRepository.kt`

```kotlin
interface OgnTrafficRepository {
    val aircraft: StateFlow<List<AircraftViewItem>>
    suspend fun start(centerLat: Double, centerLon: Double, radiusKm: Int)
    suspend fun stop()
    suspend fun updateFilter(centerLat: Double, centerLon: Double, radiusKm: Int)
}
```

Implementation `OgnTrafficRepositoryImpl`:
- Owns:
  - `OgnAprsClient`
  - `AprsParser`
  - `OgnTrafficStore`
  - `DdbRepository`
- Pipeline:
  1. `ddb.refreshIfNeeded()`
  2. `client.connect()`
  3. `client.setGeoFilter(...)`
  4. Collect `client.updates()`:
     - parse line -> AircraftState?
     - if non-null -> store.onUpdate
  5. Combine store.states with DDB lookup to produce `List<AircraftViewItem>`, sorted (e.g., nearest or freshest).
  6. Throttle UI emissions: `sample(200.ms)` or `debounce` as appropriate.

Add reconnect with backoff:
- On exception: disconnect, wait backoff, reconnect.

---

## 8) UI layer (Compose + map)

### Screen
`ogn/ui/OgnMapScreen.kt`
- Google Maps Compose:
  - `GoogleMap(...)`
  - `Marker` per aircraft item
- Marker rotation: use `trackDeg`
- Stale style: change alpha, label suffix (stale)"
- Bottom sheet:
  - `ModalBottomSheet` with details.

### ViewModel
`ogn/ui/OgnViewModel.kt`:
- Exposes:
  - `uiState: StateFlow<OgnUiState>`
  - events: `OnMapMoved(center, radius)`, `OnFollowMe`, `OnStart`, `OnStop`, `OnMarkerTapped(id)`

`OgnUiState` includes:
- `aircraft: List<AircraftViewItem>`
- `connectionStatus: Connected/Connecting/Disconnected/Error`
- `filter: center/radius`
- `selected: AircraftViewItem?`

### Lifecycle
Start streaming when screen enters `STARTED`, stop when `STOPPED`.
- `repeatOnLifecycle(Lifecycle.State.STARTED) { repo.start(...) }`

---

## 9) Settings + persistence

`ogn/domain/settings/OgnSettings.kt`
- Use DataStore Preferences.
- Keys:
  - radiusKm
  - staleSec
  - expireSec
  - respectDdbPrivacy
  - followMode: `DEVICE` or `MAP`

Expose a simple settings UI.

### General > FLARM (UI style)
- Add a "FLARM" section under General settings.
- UI styling: match the Polar header layout and arrow colors (copy Polar header and arrow styles).
- Fields (user-editable):
  - Registration (name/letters)
  - Competition ID (alphanumeric)
  - FLARM ID (alphanumeric/hex)

---

## 10) Testing contract (Codex must do this)

### Unit tests
- `AprsParserTest`:
  - parse valid line -> expected lat/lon
  - parse altitude conversion
  - ignore irrelevant lines
- `OgnTrafficStoreTest`:
  - update replaces
  - purge removes expired
- `DdbRepositoryTest`:
  - JSON parse
  - lookup works

### Integration test (optional)
- Fake client emits sample APRS lines
- Repo produces expected `AircraftViewItem`

---

## 11) Backend relay" option (recommended for production)

If you want reliability + lower battery + easier parsing:
- Run a small server (Fly.io / VPS) that:
  - connects to OGN APRS once
  - parses and enriches with DDB
  - serves a websocket endpoint: `wss://yourserver/traffic?lat=...&lon=...&r=...`
- Android connects to your websocket and draws markers.

Keep the same domain model; just swap `OgnAprsClient` implementation.

---

## 12) Implementation steps for Codex (do not ask questions)

### Phase A -- Core plumbing
1. Add `ogn/` package structure.
2. Implement domain models.
3. Implement `OgnTrafficStore` + tests.

### Phase B -- APRS client + parser
4. Implement `OgnAprsTcpClient` (callbackFlow + reconnect wrapper).
5. Implement `AprsParser` minimal for lat/lon + timestamp.
6. Add parser unit tests with sample lines.

### Phase C -- DDB
7. Implement `DdbRepository` with file cache + JSON parse.
8. Add tests.

### Phase D -- Repository
9. Implement `OgnTrafficRepositoryImpl` end-to-end.
10. Add fake client integration test.

### Phase E -- UI
11. Add OGN Map screen with markers + bottom sheet.
12. Add lifecycle start/stop.
13. Add settings screen.

### Phase F -- Hardening
14. Add throttling, backoff, stale/expire visuals.
15. Add clustering if marker count large.

---

## 13) Acceptance checklist

- [ ] With GPS on, opening OGN map shows aircraft within radius.
- [ ] Markers update smoothly without jank.
- [ ] Aircraft disappear after expiry seconds.
- [ ] DDB enrichment shows reg/CN/type where available.
- [ ] App doesn't keep socket open when screen not visible.
- [ ] All tests pass: `./gradlew testDebugUnitTest lintDebug assembleDebug`

---

## 14) Notes / guardrails
- Do NOT store or redistribute OGN data beyond allowed windows if you later add history/trails.
- Parser must be tolerant; dropping unknown formats is fine in v1 as long as common ones work.
- Keep everything behind interfaces to allow swapping in a backend relay later.



---

## 15) Data use, privacy, and compliance (mandatory)

- Respect OGN Device Database (DDB) privacy flags at all times.
- If `identified == false` or `tracked == false`:
  - Do NOT display registration or CN.
  - Fall back to anonymized ID label.
- Do NOT persist, replay, or redistribute live traffic beyond permitted windows.
- Display an in-app disclaimer:
  > OGN traffic is informational only and must not be used for collision avoidance or separation."

---

## 16) Networking hardening & connection state machine

### Connection states
- `DISCONNECTED`
- `CONNECTING`
- `STREAMING`
- `BACKING_OFF`
- `ERROR`

### Backoff policy
- Exponential backoff: 1s -> 2s -> 4s -> 8s -> 16s -> max 60s
- Add random jitter (+/-20%)
- Reset backoff after a stable streaming period (e.g. 60s)

### Socket rules
- Enable TCP keepalive.
- Read timeout: 20s.
- Treat prolonged silence as a soft failure -> reconnect.

---

## 17) Filter update rules (battery + server safe)

- Only update server-side filter if:
  - Map center moved > 20 km, OR
  - Radius changed.
- Debounce filter updates by 1-2 seconds while user pans.
- Follow modes:
  - `FOLLOW_DEVICE`: center = GPS position
  - `FOLLOW_MAP`: center = map camera
- Never update filters continuously while camera is animating.

---

## 18) Rendering & performance contract (non-negotiable)

- Parse APRS at full rate.
- UI emission rate: **max 2-5 Hz**.
- Marker updates must be diff-based:
  - Update position/rotation/alpha only.
  - Do NOT recreate markers every frame.
- Hard cap displayed aircraft (e.g. 500).
- Enable clustering by default when count > 50.

---

## 19) Labeling & identity rules

Primary label (in priority order):
1. Contest Number (CN)
2. Registration
3. Short anonymized ID (last 4-6 chars)

Secondary label:
- Aircraft type/model if known
- Else Unknown"

Labels must be stable across updates to avoid flicker.

---

## 20) Units & normalization rules

- Altitude:
  - APRS feet -> convert to meters internally.
- Speed:
  - Knots -> convert to m/s internally.
- Track:
  - Treat as true degrees unless explicitly stated otherwise.
- All domain models use SI units internally.

---

## 21) DDB caching & refresh rules

- Cache DDB with:
  - version (if provided)
  - last-updated timestamp
- Refresh if cache age > 24h.
- On refresh failure:
  - Keep old cache.
  - Flag DDB status as stale" in UI/logs.

---

## 22) Backend relay recommendation (forward-looking)

Direct APRS from Android is acceptable for prototype use.

For production stability:
- One backend connection to OGN APRS.
- Server parses + enriches + filters.
- Android connects via WebSocket:
  `wss://<server>/ogn?lat=...&lon=...&r=...`

Domain models and UI remain unchanged; only data source swaps.

---

## 23) Final acceptance criteria (expanded)

- [ ] Privacy rules enforced and tested.
- [ ] Connection survives network dropouts without user action.
- [ ] UI remains smooth with 100+ aircraft visible.
- [ ] Battery drain acceptable (no background socket).
- [ ] All unit tests + lint + assemble pass.

---

## 24) OGN Uplink (Phone Tracking)

Goal: optionally publish the user's phone position to OGN while the app is active.

### 24.1 Requirements
- Opt-in only (default OFF). Show clear UI state when transmitting.
- Transmit only while app is visible (STARTED/RESUMED). No background socket by default.
- Do not transmit if GPS fix is stale or accuracy is poor (define thresholds).
- Respect privacy: allow "anonymous" mode and suppress any user identifiers if enabled.
- De-duplication: if the pilot flies with a real FLARM device, allow a user-provided
  "Own FLARM ID" to avoid showing duplicate aircraft (phone + FLARM).

### 24.2 Data source (SSOT)
- Use FlightDataRepository (via FlightDataUseCase) for location and track.
- Do not read sensors directly in UI or ViewModel.
- Use wall time only for APRS timestamp formatting (UTC). Domain logic still uses injected clocks.

### 24.3 Protocol outline (APRS-IS)
- Same TCP connection style as downlink (APRS-IS).
- Login line includes callsign, passcode, app version (pass -1 allowed for read-only; uplink may require real passcode).
- Send periodic keepalive lines starting with '#'.

### 24.4 Packet content (minimal)
- UTC time, latitude, longitude.
- Track (deg), speed (knots), altitude (feet) if available.
- OGN "idXXYYYYYY" field in the comment section (aircraft type + device ID).
- Keep comment fields minimal; do not add vendor strings in production UI text.

### 24.4A Registration / Competition ID (status messages)
- Registration (Reg=) and Competition ID (ID=) should be sent in a low-rate APRS status
  message, not on every position update.
- Status format is documented by OGN and marked as "new / not fully established" and
  subject to change, so treat it as optional and rate-limit it.
- Recommended: send on change and then no more than every few minutes while active.

### 24.5 Cadence
- Target 5-20 seconds per position update (configurable).
- Backoff if no fix or if the app is idle.

### 24.6 Safety / UX
- Add a clear disclaimer: informational only, not for separation.
- Provide a visible TX indicator and a quick OFF control.

### 24.7 Own FLARM ID (de-duplication)
- Settings: "Own FLARM ID (hex)" field (e.g. ABC123).
- Downlink: if incoming OGN ID matches Own FLARM ID, treat it as ownship and hide/merge
  from traffic list to avoid a duplicate marker.
- Uplink: when Own FLARM ID is set, default to suppress phone transmission unless the user
  explicitly enables "Transmit anyway".


---

## 27) XCPro integration checklist (add these before you ship)

This section captures Android/XCPro-specific realities that commonly break OGN implementations in the field.

### 27.1 Android networking constraints (TCP / cleartext pitfalls)
- OGN APRS is a plain TCP text feed (not HTTPS).
- Use `java.net.Socket` directly (IO dispatcher). Do NOT route this through an HTTP client stack.
- If you later add any cleartext HTTP endpoints, ensure Android Network Security Config is set appropriately.
- Add diagnostics to detect connected but no data" (see debug panel).

### 27.2 Permissions & lifecycle (don't get killed by Android)
- Permissions:
  - `ACCESS_FINE_LOCATION` (only if using FOLLOW_DEVICE / center on me")
- Lifecycle rule (default):
  - Stream only while the OGN screen is in `STARTED` (or `RESUMED`).
  - On leaving the screen, cancel collectors and close the socket.
- Do NOT keep a background socket by default.
  - If you ever choose to stream in background, you must implement a Foreground Service + notification, otherwise Android will kill it.

### 27.3 Callsign / login behavior (make it configurable)
- Many APRS-IS style feeds expect a login line.
- Make these settings configurable (with safe defaults):
  - `callsign` (default: `XCPro`)
  - `passcode` (optional; allow read-only default like `-1` if needed)
  - `vers` fields should include XCPro name + version for server friendliness.
- Log the server greeting + any auth/denial messages to help troubleshoot.

### 27.4 Debug panel (mandatory for supportability)
Add an in-app OGN Debug" panel (hidden behind a toggle or developer setting) showing:
- Connection state: DISCONNECTED / CONNECTING / STREAMING / BACKING_OFF / ERROR
- Current filter string (exact)
- Map center + radius currently applied
- Last line received timestamp + seconds since last packet"
- Packets/sec (rolling average, e.g. 10s window)
- Aircraft counts:
  - total in store
  - stale
  - expired (purged last interval)
- DDB status:
  - cache age (hours)
  - last refresh time + success/failure
- Last error (exception message + stack trace in logs only)

This debug screen is what you use when someone says it shows nothing at Keepit".

### 27.5 Map rendering performance rules (Compose reality)
- Do NOT recreate all markers every emission.
- Use stable keys by `DeviceId` and update marker properties:
  - position
  - rotation (track)
  - alpha (stale)
- Throttle UI updates to 2-5 Hz maximum (already required).
- Enable clustering when aircraft count > 50 to avoid jank.
- Cap displayed aircraft (e.g. 500) and prefer freshest + nearest" when capping.

### 27.6 Data alignment with XCPro (avoid incorrect helpfulness")
- Optionally display **ownship** (XCPro GPS position) as a separate marker.
- Do NOT wind-correct" other aircraft:
  - OGN reports ground tracks/speeds; applying XCPro wind to others will make them wrong.
- Keep OGN traffic purely situational; do not mix into glide/arrival calculations.

### 27.7 Field failure modes (handle them explicitly)
OGN will sometimes show nothing for reasons that are not bugs". Your UI must differentiate:
- No nearby receivers / no traffic:
  - Show empty state: No traffic in range" (not an error)
- No internet:
  - Show DISCONNECTED + No data connection"
- Connected but silent feed:
  - After read-timeout/silence threshold, transition to BACKING_OFF and reconnect
- DDB missing/unavailable:
  - Show anonymized IDs; mark DDB status stale/unavailable" in debug panel
- Stale snapshot:
  - If you decide to keep last-known markers, visually mark them as stale and do not present as live




