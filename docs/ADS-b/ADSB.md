# ADSB.md — Internet ADS‑B traffic (20 km) in XCPro MapLibre map

Goal: Add an **internet-sourced ADS‑B traffic layer** to XCPro so glider pilots can see nearby aircraft on the MapLibre map.

This doc is written for an autonomous engineering agent (Codex). It must be implementable end‑to‑end without asking a human questions.

---

## 0) Hard gate: license + shipping reality (do not skip)

OpenSky’s terms grant access for **non-profit research/education/government** use only; **for-profit use requires written permission**.

- If XCPro is commercial (paid app, paid features, paid subscriptions, or business use), the implementation can exist behind a feature flag but must not be shipped without written permission.

Authoritative: https://opensky-network.org/about/terms-of-use

---

## 1) Mandatory repo reading order before code edits

1. `ARCHITECTURE.md` (MVVM+UDF+SSOT, DI rules, timebase rules)
2. `CODING_RULES.md` (day-to-day enforcement)
3. `PIPELINE.md` (how MapScreen is wired)
4. Existing OGN traffic implementation (use it as the overlay + wiring template)
5. This file (ADSB.md)

---

## 2) Feature requirements (tight and testable)

### Must have (v1)
1. Separate MapScreen FAB toggle: **"ADS‑B"** (vendor-neutral; do not show "OpenSky" in production strings).
2. Show aircraft within **20 km** of the **current user position** (SSOT location; do not read sensors in UI).
3. Display **max 30** aircraft at once (nearest + freshest).
4. Each aircraft marker updates **in place** (no flicker): update GeoJSON source only.
5. Tap marker -> bottom sheet with:
   - ICAO24
   - callsign (if available)
   - altitude (meters)
   - speed (m/s or km/h based on existing units prefs)
   - track/heading
   - vertical rate
   - age seconds / last seen
6. Stale + expiry behavior:
   - stale threshold default **60 s**
   - expiry default **120 s**
7. Poll only while MapScreen is visible (lifecycle STARTED) AND ADS‑B enabled.
8. Robust networking:
   - timeouts
   - retry/backoff
   - handle rate-limit responses correctly
9. Safety disclaimer in UI:
   > Informational only. Not for collision avoidance or separation.

### Nice-to-have (v2)
- Local trails (last N points) as LineString overlay
- Aircraft-category icons (if provider supports it)
- Debug panel (highly recommended; see below)

---

## 3) Data provider (OpenSky) — exact API behavior

### 3.1 Live states endpoint
Use OpenSky REST API endpoint:
- `GET https://opensky-network.org/api/states/all?lamin=...&lomin=...&lamax=...&lomax=...&extended=1`

The API supports querying by a bounding box via `lamin/lomin/lamax/lomax`, and `extended=1` to request aircraft category.

Docs: https://openskynetwork.github.io/opensky-api/rest.html

### 3.2 State vector format (array-of-arrays)
OpenSky’s `states` is an array-of-arrays where each index has a defined meaning. Implement an index-mapper as a single source of truth.

Fields required for v1 (indexes from OpenSky docs):
- 0 `icao24` (string)
- 1 `callsign` (string, may be padded; trim)
- 3 `time_position` (sec, nullable)
- 4 `last_contact` (sec)
- 5 `longitude` (deg, nullable)
- 6 `latitude` (deg, nullable)
- 7 `baro_altitude` (m, nullable)
- 9 `velocity` (m/s, nullable)
- 10 `true_track` (deg, nullable)
- 11 `vertical_rate` (m/s, nullable)
- 13 `geo_altitude` (m, nullable)
- 16 `position_source` (int)
  - 0 = ADS‑B
  - 1 = ASTERIX
  - 2 = MLAT
  - 3 = FLARM
- 17 `category` (int) — only present when `extended=1`

Docs: https://openskynetwork.github.io/opensky-api/rest.html

### 3.3 Auth (critical: OAuth2 client credentials)
OpenSky now supports OAuth2 client credentials. It is **required for all accounts created since mid‑March 2025**.

Token endpoint:
- `POST https://auth.opensky-network.org/auth/realms/opensky-network/protocol/openid-connect/token`
- form-encoded:
  - `grant_type=client_credentials`
  - `client_id=...`
  - `client_secret=...`

Include:
- `Authorization: Bearer <token>`

Token expiry:
- token expires after **~30 minutes**; refresh when expired or on 401.

Docs: https://openskynetwork.github.io/opensky-api/rest.html

**Mobile reality:** do not embed a shared `client_secret` in a shipped Android app. It will leak.

Therefore implement these modes (in priority order):
1. Anonymous mode (no auth) — works but heavily limited.
2. User-supplied API client credentials (advanced users) stored locally in encrypted storage.
3. (Optional v2) Backend relay (server holds secret; phone uses your API).

### 3.4 Rate limits / credits
OpenSky uses credits for endpoints like `/states/all`.

- Header `X-Rate-Limit-Remaining` indicates remaining credits.
- On rate limit exceeded, response is `429 Too Many Requests` with header `X-Rate-Limit-Retry-After-Seconds`.

Credit cost for `/states/all` depends on bbox square degrees:
- 0–25 sq deg => 1 credit/request
- larger boxes cost more.

Docs: https://openskynetwork.github.io/opensky-api/rest.html

**Implementation rule:** on `429`, sleep exactly the retry-after seconds, then resume polling.

---

## 4) Architecture in XCPro (must follow repo rules)

### 4.1 Layering
Follow the repo’s invariant:

UI -> domain -> data

- UI (Map overlay + FAB) renders state only.
- ViewModel depends on UseCases only.
- UseCase depends on Repositories only.
- Repository owns SSOT and provider polling.
- No Android framework types in domain.

### 4.2 Time base
Do NOT use wall time for staleness/expiry logic.

- Use injected monotonic clock (`nowMonoMs()`) when receiving data.
- Store `receivedMonoMs` per target and compute age from that.

(Any wall time from OpenSky like `last_contact` is debug-only, never used for validity windows.)

---

## 5) Domain model contract

Create `AdsbTarget`:

```kotlin
@JvmInline value class Icao24(val raw: String) // lowercase hex

data class AdsbTarget(
    val id: Icao24,
    val callsign: String?,
    val lat: Double,
    val lon: Double,
    val altitudeM: Double?,
    val speedMps: Double?,
    val trackDeg: Double?,
    val climbMps: Double?,
    val positionSource: Int?, // 0 ADS-B, 3 FLARM, etc
    val category: Int?,       // only if extended=1
    val receivedMonoMs: Long  // injected Clock
)
```

UI model:

```kotlin
data class AdsbTrafficUiModel(
    val id: Icao24,
    val callsign: String?,
    val lat: Double,
    val lon: Double,
    val altitudeM: Double?,
    val speedMps: Double?,
    val trackDeg: Double?,
    val climbMps: Double?,
    val ageSec: Int,
    val isStale: Boolean,
    val distanceMeters: Double,
    val bearingDegFromUser: Double
)
```

Connection state:

```kotlin
sealed interface AdsbConnectionState {
    data object Disabled : AdsbConnectionState
    data object Active : AdsbConnectionState
    data class BackingOff(val retryAfterSec: Int) : AdsbConnectionState
    data class Error(val message: String) : AdsbConnectionState
}
```

---

## 6) Data layer (provider + polling)

### 6.1 Provider interface (swappable)
```kotlin
interface AdsbProviderClient {
    suspend fun fetchStates(bbox: BBox, auth: AdsbAuth?): ProviderResult
}

data class BBox(val lamin: Double, val lomin: Double, val lamax: Double, val lomax: Double)

sealed interface ProviderResult {
    data class Success(val response: OpenSkyResponse) : ProviderResult
    data class RateLimited(val retryAfterSec: Int) : ProviderResult
    data class HttpError(val code: Int, val message: String) : ProviderResult
    data class NetworkError(val message: String) : ProviderResult
}
```

### 6.2 Concrete OpenSky client
Implement with existing XCPro networking stack (Retrofit/OkHttp per repo norm).

- Base URL: `https://opensky-network.org/api/`
- Endpoint: `/states/all`
- Always request `extended=1` (more metadata; safe).
- Always request bbox derived from last known user location.

Auth header:
- If OAuth token present: `Authorization: Bearer ...`
- Else if legacy basic: `Authorization: Basic ...` (optional)
- Else none.

### 6.3 OAuth2 token manager
Implement `OpenSkyTokenRepository`:

- Stores client_id/client_secret (if user provides) in encrypted storage.
- Fetches token via POST to token endpoint.
- Keeps token + expiryMonoMs.
- Provides `getValidTokenOrNull()`.

Token expiry:
- If token older than 25 minutes, refresh proactively.
- If any request returns 401, force refresh and retry once.

---

## 7) Repository (SSOT) + store

### 7.1 SSOT store
Use in-memory SSOT similar to OGN store:

- `MutableStateFlow<Map<Icao24, AdsbTarget>>`
- upsert by id
- purge expired every 10s while active

Expiry/stale:
- stale: ageSec >= 60
- expiry: ageSec >= 120

### 7.2 Polling rules
- Poll interval default: 10 seconds.
- Never run more than one poll loop (singleton within Map feature scope).
- Only poll when ADS‑B toggle enabled AND MapScreen is STARTED.

### 7.3 Rate-limit + backoff state machine
On each poll:
- If ProviderResult.RateLimited(retryAfter):
  - set connection state = BackingOff(retryAfter)
  - delay(retryAfter seconds)
  - continue polling
- If transient network error:
  - exponential backoff: 2s, 4s, 8s, 16s, 32s, cap 60s (+/- 20% jitter)
  - set connection state = Error(...) while backing off
- On success:
  - set connection state = Active
  - reset backoff

---

## 8) Filtering (20 km) + cap (30)

### 8.1 BBox computation
Compute bbox around user location for 20 km:

- `latDelta = radiusKm / 111.0`
- `lonDelta = radiusKm / (111.0 * cos(latRadians))`

Clamp lat [-90, +90], lon [-180, +180].

### 8.2 Exact distance filter
After parsing targets:
- compute Haversine distance
- keep <= 20_000m

Then cap:
- Sort by:
  1) freshest (smallest ageSec)
  2) nearest distance
- Take first 30.

### 8.3 De-dup with OGN
If OpenSky `position_source == 3` (FLARM) drop it by default, to avoid duplicating your OGN layer.
(Keep ADS‑B gliders: they are `position_source == 0`.)

---

## 9) MapLibre overlay (move markers without flicker)

Implement an overlay like the OGN overlay:
- GeoJSON source: `adsb-traffic-source`
- Symbol layer: `adsb-traffic-layer`
- Optional text layer for callsign/altitude

Update rule:
- Create source + layers once on style load
- On updates, call `GeoJsonSource.setGeoJson(FeatureCollection)` only

Rotation:
- Use icon rotation expression reading property `trackDeg` from each feature.

Trails (optional v2):
- Second GeoJSON source + LineLayer.
- Build LineString per aircraft from local ring buffer.

---

## 10) MapScreen wiring (separate FAB)

### 10.1 ViewModel wiring
In `MapScreenViewModel`:
- add `showAdsbTraffic: StateFlow<Boolean>`
- add `adsbTraffic: StateFlow<List<AdsbTrafficUiModel>>`
- add `adsbConnectionState: StateFlow<AdsbConnectionState>`

ViewModel depends only on use cases.

### 10.2 Lifecycle
Start polling when:
- MapScreen is STARTED
- toggle is ON
- user location available

Stop polling when:
- screen stops OR toggle off

### 10.3 FAB UX
- Add a separate FAB in MapScreen for ADS‑B toggle.
- Do not mention provider brand in UI strings.

---

## 11) Debug panel (strongly recommended)
Add a developer/debug panel similar to OGN:
- state: Disabled/Active/BackingOff/Error
- last HTTP status
- last poll time
- remaining credits (if header present)
- aircraft counts: fetched / within 20km / displayed
- seconds since last successful fetch

---

## 12) Testing contract (must be implemented)

### 12.1 Unit tests (pure JVM)
- bbox math at lat=0, lat=45, lat=80
- Haversine threshold (19.9 km include, 20.0 include, 20.1 exclude)
- state vector index mapping
- store upsert + purge logic (fake Clock)
- 429 handling honors retry-after header
- backoff behavior on repeated failures

### 12.2 Integration tests
- fake provider returns deterministic JSON
- repository emits expected UI models (cap=30, sorted correctly)
- overlay converter produces stable feature IDs

---

## 13) Acceptance checklist
- [ ] Toggle ON shows traffic within 20 km when internet available.
- [ ] Aircraft markers move smoothly (no flicker).
- [ ] Markers rotate with track when available.
- [ ] Tap shows details sheet.
- [ ] After 60s without updates, aircraft becomes stale visual.
- [ ] After 120s without updates, aircraft disappears.
- [ ] Leaving MapScreen stops polling (no background network loop).
- [ ] 429 behavior backs off correctly and recovers.
- [ ] All unit tests pass; lint/detekt pass; enforceRules pass.

---

## 14) Notes: what NOT to do
- Do not call OpenSky `/tracks/all` for every aircraft (credit blowup).
- Do not use wall time for staleness/expiry logic.
- Do not rebuild layers/sources per update (flicker).
- Do not push traffic into flight-performance math or collision logic.

