
# OpenSky.md -- GA ADS-B traffic around the user (XCPro / Android)

Goal: In XCPro, display **up to 30 GA aircraft** within **20 km** of the user's current location using **OpenSky Network** (internet polling). This document tells Codex exactly what to build: request format, polling, caching, rate-limit-safe behavior, and UI rules.

> **Important licensing note (don't ignore):** OpenSky's public data access is licensed primarily for **non-profit research/education/government**. Commercial/for-profit use requires written permission. Treat this as a hard constraint and get permission before shipping a commercial product using OpenSky.  
> See OpenSky Terms of Use. ^cite^turn0search1^turn0search16^

---

## 0) What OpenSky" provides

OpenSky exposes a REST endpoint that returns **live state vectors** (positions + velocities) for aircraft. You query a **bounding box** to restrict the area.  
Primary endpoint you will use:

- `GET https://opensky-network.org/api/states/all?lamin=...&lomin=...&lamax=...&lomax=...` ^cite^turn0search0^turn0search16^

---

## 1) Requirements (tight)

### Must have
- Show GA aircraft within **20 km** radius of user location.
- Show **max 30 aircraft** at once.
- Poll OpenSky with a bounding box that covers the 20 km radius.
- Rate-limit safe:
  - Respect OpenSky credit/rate-limit headers.
  - Back off properly on `429` with server-provided retry time.
- Cache:
  - In-memory store with expiry (default 60-120s).
  - Optional disk cache of last response for stale snapshot" on cold start.
- UX:
  - If internet down or rate-limited: show GA layer as unavailable/delayed, not broken".

### Not required (v1)
- No local ADS-B receiver (no GDL90).
- No history/trails.

---

## 2) Polling strategy (safe defaults)

### Poll interval
- Default: **10 seconds**.  
OpenSky docs note 10s resolution for anonymous users; 10s is also a conservative don't spam" interval. ^cite^turn0search0^turn0search16^

### Credits / rate limits
OpenSky uses **API credits** and returns headers such as:
- `X-Rate-Limit-Remaining`
- on 429: `X-Rate-Limit-Retry-After-Seconds` (wait exactly this many seconds) ^cite^turn0search0^turn0search7^

**Rule:** if response is `429`, STOP polling and sleep `retry-after` seconds, then resume at normal interval.

### Bounding-box cost
Credit cost for `/states/all` depends on query **area in square degrees**; smaller is cheaper. The docs describe credit cost tiers tied to area. ^cite^turn0search0^turn0search16^

With a 20 km radius, your bbox will be tiny, so you're in the cheap tier.

---

## 3) Bounding box math for a 20 km radius

OpenSky uses a bbox, not a radius. Convert (centerLat, centerLon, radiusKm) -> (lamin, lomin, lamax, lomax).

### Approx conversion
- `latDelta = radiusKm / 111.0`
- `lonDelta = radiusKm / (111.0 * cos(centerLatRadians))`

Then:
- `lamin = centerLat - latDelta`
- `lamax = centerLat + latDelta`
- `lomin = centerLon - lonDelta`
- `lomax = centerLon + lonDelta`

Clamp lat to [-90, +90], lon to [-180, +180].

### For radiusKm = 20
- `latDelta per-mille^ 0.18018deg`
- At Sydney lat (~ -33.9deg), `cos(lat) per-mille^ 0.83`, so `lonDelta per-mille^ 0.217deg`

This yields a small bbox and keeps OpenSky credit usage low.

---

## 4) Authentication (optional but recommended)

OpenSky supports authenticated requests (HTTP Basic Auth). Many community examples use username/password Basic Auth for higher limits. ^cite^turn0search4^turn0search0^

### Android approach
- Provide settings fields (optional):
  - `openskyUsername`
  - `openskyPassword`
- If provided, send HTTP Basic Auth.

### Security note
- Do **not** hardcode credentials.
- Store credentials in Android Keystore/Encrypted DataStore if you allow user entry.
- If no credentials, run anonymous.

---

## 5) Data mapping (OpenSky -> XCPro AircraftState)

OpenSky returns a JSON object with:
- a server timestamp
- a list of states representing aircraft.

Map each aircraft to your domain model:

- id: `icao24` (hex)
- callsign: string (optional)
- lat/lon
- altitude: `geo_altitude` (meters, nullable)
- speed: `velocity` (m/s)
- track: `true_track` (degrees)
- vertical_rate (m/s)
- last update time: now (or `time_position` if present)

If lat/lon is null -> ignore that aircraft.

---

## 6) Filtering: within 20 km" + max 30"

### Step 1: Server-side filter (bbox)
Always query the bbox derived from radius 20 km. That reduces payload.

### Step 2: Client-side exact radius
After parsing:
- compute distance from (centerLat, centerLon) to each aircraft (Haversine).
- keep only those with distance per-mille$ 20 km.

### Step 3: Max 30 aircraft
From remaining aircraft:
- sort by:
  1) freshest (most recent `time_position` or lastUpdate)
  2) nearest distance
- take first 30.

---

## 7) Caching & expiry

### In-memory cache (mandatory)
Maintain:
- `Map<Icao24, AircraftState>`
- expiry rules:
  - stale threshold: 30-60s
  - expiry: 120s (remove if not updated)

### Disk cache (optional)
- Save last successful parsed list + timestamp.
- On startup, display it marked STALE SNAPSHOT" until live data arrives.

---

## 8) Rate-limit-safe state machine

States:
- DISABLED (GA layer off)
- ACTIVE (polling)
- BACKING_OFF (429 or repeated errors)
- ERROR (no network / persistent failures)

Transitions:
- ACTIVE -> BACKING_OFF on 429 (sleep retry-after)
- ACTIVE -> BACKING_OFF on repeated 5xx/timeouts (exponential backoff)
- BACKING_OFF -> ACTIVE after wait
- ACTIVE -> ERROR if no network, then retry periodically (slow)

Exponential backoff for non-429:
- 2s -> 4s -> 8s -> 16s -> cap 60s (+ jitter +/-20%)

---

## 9) Android implementation plan (Codex-ready)

### 9.1 Interfaces (reuse your OGN traffic abstraction)
Create `TrafficSource`:

```kotlin
interface TrafficSource {
    val name: String
    fun start(filter: GeoFilter): kotlinx.coroutines.flow.Flow<AircraftState>
    suspend fun stop()
}

data class GeoFilter(val lat: Double, val lon: Double, val radiusKm: Int)
```

Implement `OpenSkyTrafficSource`:
- Uses OkHttp/Ktor client
- Poll loop with `delay(pollIntervalMs)`
- Emits `AircraftState` updates into the common store

### 9.2 Repository + store
Reuse your traffic store from OGN (or a combined store):
- keys:
  - OpenSky: `icao24`
  - OGN: `deviceId`
- keep source tag on each entry

### 9.3 UI
- Toggle: GA (OpenSky)" on/off
- Display up to 30 aircraft markers
- Details sheet includes:
  - callsign (if present)
  - ICAO24
  - altitude, speed, track, vertical rate
  - age seconds
- Banner states:
  - GA delayed (rate limit)" when backing off
  - GA unavailable (no internet)" when offline

### 9.4 Permissions
- Location permission if you use user GPS for the 20 km center:
  - `ACCESS_FINE_LOCATION` (or coarse if acceptable)
- Internet permission:
  - `android.permission.INTERNET`

---

## 10) Testing contract (must be implemented)

### Unit tests
- bbox math:
  - given center+radius produce expected lamin/lomin/lamax/lomax ranges
- distance filter:
  - ensure within 20 km retained
- cap logic:
  - ensure max 30 and correct ordering

### Integration tests (fake HTTP)
- Provide a fake OpenSky JSON response and verify:
  - parser extracts aircraft correctly
  - null lat/lon ignored
  - backoff on 429 honors retry-after header

---

## 11) Operational notes

- Coverage is not perfect everywhere; empty results can be normal.
- Don't poll faster than needed; battery + limits matter.

---

## 12) Compliance & attribution

- You must comply with OpenSky's Terms of Use / Data License and provide attribution when using their data. ^cite^turn0search1^turn0search13^turn0search16^
- If XCPro is intended for commercial use, you likely need written permission from OpenSky. ^cite^turn0search1^turn0search16^


---

## 13) Commercial use reality check (read this before shipping)

OpenSky's public API and data access is not automatically OK to ship in a commercial app".
- Prototype / internal testing: often acceptable if aligned with their allowed-use scope.
- If XCPro is commercial (paid app, paid features, paid subscriptions, or business use), you likely need explicit permission / a commercial arrangement with OpenSky, or you should switch providers.

Treat this as a **go/no-go** constraint early, not after you've built the feature. ^cite^turn0search1^turn0search16^

---

## 14) OpenSky response format (critical: `states` is array-of-arrays)

OpenSky returns JSON like:

```json
{
  "time": 1710000000,
  "states": [
    ["a1b2c3","CALL123","Country",1710000000,1710000000,150.9,-33.9,12000.0,false,85.0,180.0,2.0,null,12300.0,"7000",false,0]
  ]
}
```

The `states` field is a **list of arrays**, where each index has a fixed meaning. Your parser must map indexes to fields according to the official OpenSky docs for state vectors. ^cite^turn0search0^turn0search16^

### Mandatory parsing rules
- If `states` is missing or empty: treat as no traffic".
- Ignore any state where latitude or longitude is null.
- Trim callsign (it may be padded with spaces).
- Store speed as m/s (OpenSky velocity is m/s), altitude in meters.

### Index map (use OpenSky's official mapping)
Implement a single source of truth:
- `OpenSkyStateVectorMapper` with constants for each index you use.
- Unit test it against known sample JSON.

(Do not guess" indexes. Use the published mapping in the docs.) ^cite^turn0search0^turn0search16^

---

## 15) Data validation & edge cases

Apply these filters after parsing:
- Ignore invalid coordinates:
  - lat not in [-90, +90]
  - lon not in [-180, +180]
  - (0,0) if you want to be strict (optional)
- If any numeric field is NaN/Inf: ignore that field (or drop the aircraft if it affects position).
- If speed/track is null: don't rotate marker; show --" for speed/track.

---

## 16) Credit-aware polling guardrails

Even with a small bbox, you can burn credits if:
- polling runs in background
- multiple poll loops run by accident

Rules:
- Poll only while GA layer screen is in STARTED (or feature is explicitly enabled in foreground).
- Enforce a singleton polling loop (one per app process).
- If `X-Rate-Limit-Remaining` is available:
  - if remaining < 10: automatically slow polling to 30-60s until remaining increases.
- On `429`:
  - wait exactly `X-Rate-Limit-Retry-After-Seconds` then resume at normal interval. ^cite^turn0search7^turn0search0^

---

## 17) Distance calculation spec (Haversine) + tests

Distance must be computed using the Haversine formula (great-circle distance).
- Inputs: lat/lon in degrees
- Output: meters (Double)

Unit tests must include at least:
- distance(A, A) == 0
- a known pair with approximate expected distance (within tolerance), e.g.:
  - (-33.8688, 151.2093) *" (-33.8731, 151.2060) ~ few hundred meters (tolerance +/-50m)
- Verify that aircraft exactly at 20 km is included; 20.1 km excluded.

---

## 18) Optional GA filtering rules (avoid airliners)

If you want GA only":
- Prefer OpenSky category if present; filter allowed categories.
- If category missing, optional heuristics:
  - exclude very high altitudes (e.g. > 12,000 m)
  - exclude very high speeds (e.g. > 200 m/s)

Keep these heuristics disabled by default in v1.

---

## 19) UI rules to prevent flicker & dropouts

- Stable identity: `icao24` is the marker key.
- Do not rebuild all markers each poll; update positions in-place.
- Keep aircraft in memory up to expiry (120s) even if one poll misses it.
- If >30 aircraft within 20 km:
  - keep selected" aircraft pinned in display set if tapped (unless expired), then fill remaining slots by nearest/freshest.

---

## 20) GA debug panel (mandatory)

Add an in-app OpenSky Debug" panel (developer toggle) showing:
- Poll state: DISABLED / ACTIVE / BACKING_OFF / ERROR
- Last poll time
- Last HTTP status
- `X-Rate-Limit-Remaining` (if present)
- Retry-after seconds (if backing off)
- Counts:
  - returned by bbox
  - within 20 km
  - displayed (capped to 30)
- Last error summary (no stack traces in UI; logcat only)

This makes field support possible when users report no traffic".

---

## 21) Provider abstraction (future-proof)

Even if you only use OpenSky today, implement:

```kotlin
interface GaProviderClient {
    suspend fun fetchStates(bbox: BBox, auth: Auth?): OpenSkyResponse // or generic
}
```

So you can later add `AdsbLolProviderClient` without rewriting repositories/UI.




