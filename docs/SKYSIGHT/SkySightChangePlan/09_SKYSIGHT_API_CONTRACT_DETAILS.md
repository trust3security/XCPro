# SkySight API Contract Details (Confirmed vs Missing) — XCPro Integration

This document **does not** pretend to invent SkySight endpoints that are not publicly documented.  
It captures:

1) what we can **actually confirm** today,  
2) what is still **unknown / must be obtained from SkySight or by running authenticated curl calls**, and  
3) the **exact capture steps** needed to turn the unknowns into a concrete API contract that Codex can implement against.

---

## 1) What we can confirm right now

### 1.1 Auth entrypoint exists (partner-style header key + username/password)
You provided an auth call of the form:

```bash
curl -v \
  -d '{"username": "<EMAIL>", "password": "<PASSWORD>"}' \
  -H "Content-Type: application/json" \
  -H "X-API-KEY: <SKYSIGHT_API_KEY>" \
  "https://skysight.io/api/auth"
```

So we can treat the following as confirmed for XCPro integration:

- **Base host:** `skysight.io`
- **Auth endpoint:** `POST /api/auth`
- **Auth request body:** JSON with `username`, `password`
- **Auth request headers:** at least `Content-Type: application/json`, and an **API key header** `X-API-KEY`

> **Still unknown (must capture):** the auth **response JSON shape** (token name, expiry, refresh behavior), and whether subsequent requests need `Authorization: Bearer <token>` or some other session/cookie mechanism.

### 1.2 SkySight has stated they “do have an API” for raster/weather overlays (but it is not publicly documented)
SkySight’s public feedback board contains a request about “API to access raster data” and (per the search snippet/indexed content) indicates SkySight does have an API, typically handled via direct contact rather than a public spec.

**Implication for XCPro:** assume the API contract is **partner/private**, and we must obtain the exact endpoints + shapes directly from SkySight or from authenticated calls.

### 1.3 SkySight (separately) runs standard tile services for some map layers
SkySight hosts a TileServer instance (`airspace.skysight.io`) that explicitly provides tiles via:

- **OGC WMTS**
- **OSGEO TMS**
- **XYZ “slippy map” URLs**
- **TileJSON metadata**

This is **not** proof that the SkySight *forecast* layers use the same infrastructure, but it strongly suggests SkySight is comfortable delivering data using industry-standard web map tiling conventions.

### 1.4 SkySight also operates a satellite/radar tile product with explicit “tile quotas” (HighSight)
HighSight (operated by “SkySight Weather Pty Ltd”) markets a “standard XYZ tile API” and publishes plan quotas in “tiles per month”.

**Implication:** SkySight/adjacent services likely enforce **entitlements** and may expose rate/usage behavior as:
- request throttling (HTTP 429),
- plan/entitlement errors (HTTP 402/403),
- quota headers or structured error payloads.

Again: **this is a clue, not confirmation** for the SkySight forecast API.

---

## 2) The 5 missing contract items (what we still need before Codex can implement)

You listed the exact missing items correctly. Here they are in “contract” form.

### 2.1 Parameter catalog / product catalog endpoint
We need the endpoint(s) that answer:

- What *products/layers* exist? (thermal strength, cu clouds, wind, rain, etc.)
- For each product:
  - id / key used in requests
  - units
  - default and supported altitude/pressure levels (if applicable)
  - supported regions / model domains
  - time step resolution (e.g., 15 min, 1 hr)
  - min/max value range + no-data semantics
  - availability windows (e.g., “today + 5 days”, but exact)

**Deliverable:** a stable JSON schema for a “list layers” response.

### 2.2 Tile template endpoint (and any style controls)
We need the tile URL template(s), including:

- the full template path (XYZ, WMTS, or a custom template)
- the required query params (time, run, parameter, altitude/level, region, smoothing, palette/style)
- tile format (`png`, `webp`, `pbf`, `mvt`, etc.)
- cache policy headers and whether tiles are cacheable per-user or shared

**Deliverable:** the exact tile request that returns a tile image for a known z/x/y/time/param.

### 2.3 Legend endpoint
We need a legend (color ramp) for each raster layer or for each “style”.

Key requirements:

- legend format: JSON vs image vs embedded metadata
- breaks/thresholds (list of stops)
- units and labels
- “no-data” / transparency rules
- whether legend is time-dependent or static per parameter

**Deliverable:** real legend payload examples + a schema.

### 2.4 Point-value endpoint (single location query)
We need an endpoint that can answer:
> “At lat/lon X,Y at time T (and altitude/level L), what is the forecast value for parameter P?”

Key requirements:

- coordinate format (lat/lon WGS84 vs projected)
- altitude/level semantics (meters MSL, AGL, FL, pressure)
- time semantics (UTC? local? forecast offset?)
- response shape (value, units, quality flags, source model, run time)

**Deliverable:** real JSON response examples.

### 2.5 Entitlement / rate limit behavior + error codes
We need to know:

- what happens when:
  - token expires
  - API key is invalid
  - plan doesn’t include a layer
  - quota exceeded (daily/monthly)
  - too many requests
- what status codes are used (401/403/429/etc.)
- whether there are `X-RateLimit-*` headers
- retry/backoff semantics

**Deliverable:** a table of errors + example payloads/headers.

---

## 3) How to capture the missing details (the “curl evidence pack”)

### 3.1 Capture auth response (this unlocks everything else)

```bash
# 1) authenticate
AUTH_JSON=$(curl -sS \
  -X POST "https://skysight.io/api/auth" \
  -H "Content-Type: application/json" \
  -H "X-API-KEY: <SKYSIGHT_API_KEY>" \
  -d '{"username":"<EMAIL>","password":"<PASSWORD>"}')

echo "$AUTH_JSON" | jq .
```

Save the *entire* response JSON (redact secrets later if needed).  
From this, we must extract:

- access token field name (e.g., `token`, `access_token`)
- expiry field (e.g., `expires_in`, `exp`)
- refresh token behavior (if any)

### 3.2 Find the “API index” or product catalog endpoint
Try these **discovery calls** (they may 404 — that’s fine; we just need to learn reality):

```bash
# Substitute based on auth response:
#   - if bearer token: export TOKEN=...
#   - if cookie-based session: use -c cookiejar.txt -b cookiejar.txt

export TOKEN="<ACCESS_TOKEN>"

common_headers=(
  -H "X-API-KEY: <SKYSIGHT_API_KEY>"
  -H "Authorization: Bearer $TOKEN"
)

# candidates (you run these and keep the results)
curl -sS -D headers.txt "${common_headers[@]}" "https://skysight.io/api" | head
curl -sS -D headers.txt "${common_headers[@]}" "https://skysight.io/api/v1" | head
curl -sS -D headers.txt "${common_headers[@]}" "https://skysight.io/api/parameters" | jq .
curl -sS -D headers.txt "${common_headers[@]}" "https://skysight.io/api/layers" | jq .
curl -sS -D headers.txt "${common_headers[@]}" "https://skysight.io/api/products" | jq .
curl -sS -D headers.txt "${common_headers[@]}" "https://skysight.io/api/catalog" | jq .
```

> **If SkySight provides docs:** the above usually reveals an “index” endpoint, OpenAPI route, or at minimum an informative error payload.

### 3.3 Capture tile template + tile auth strategy
Once you find any endpoint that returns a tile template, confirm auth style:

1) request a tile **without** auth header  
2) request with `Authorization: Bearer`  
3) request with token in query param (if supported)  
4) compare responses + headers

Example pattern:

```bash
TILE_URL="<PASTE REAL TEMPLATE HERE WITH z/x/y substituted>"

# no auth
curl -v "$TILE_URL" -o /tmp/tile-noauth.png

# auth header
curl -v "${common_headers[@]}" "$TILE_URL" -o /tmp/tile-auth.png
```

**Decide tile auth strategy** based on outcomes:

- **If header-auth works:** great — simplest in XCPro.
- **If only signed URLs/query tokens work:** XCPro must request signed URLs from an API endpoint before map rendering.
- **If tiles are public but gated at catalog layer:** XCPro must enforce entitlements client-side + server-side.

### 3.4 Capture legend + point value
Once parameter ids are known, you can test “legend” and “point” endpoints.

Candidates to try:

```bash
curl -sS "${common_headers[@]}" "https://skysight.io/api/legend?layer=<LAYER_ID>" | jq .
curl -sS "${common_headers[@]}" "https://skysight.io/api/point?lat=-33.86&lon=151.21&time=<ISO>&layer=<LAYER_ID>" | jq .
```

If you find different path naming, adjust.

### 3.5 Capture rate limit / entitlement behavior
Deliberately provoke:

- invalid token
- expired token (wait past expiry)
- request a layer you *know* you aren’t entitled to
- request many tiles quickly (or loop point requests)

Capture **both headers and body**:

```bash
curl -sS -D - "${common_headers[@]}" "<URL>" -o /dev/null
```

Record:

- status code
- `WWW-Authenticate`
- `Retry-After`
- any `X-RateLimit-*` headers
- JSON error payload fields (`code`, `message`, `details`, etc.)

---

## 4) Output format we need for Codex

When you’ve run the capture steps above, the “evidence pack” should contain:

1) `auth_response.json`  
2) `layers.json` (or whatever the catalog endpoint returns)  
3) `tile_template.json` (or equivalent)  
4) `legend_<layer>.json` for 2–3 representative layers  
5) `point_<layer>.json` for 2–3 representative layers  
6) `error_samples.md` with raw headers + payloads for 401/403/429/etc.

Then we can produce a **final** “SkySight API Contract” doc that is 100% grounded and implementable.

---

## 5) Interim recommendation (so XCPro work can progress now)

While contract capture is happening, XCPro integration should proceed with:

- An abstraction layer: `SkySightClient` interface with methods:
  - `login()`
  - `listLayers()`
  - `getTileTemplate(layer, time, level)`
  - `getLegend(layer)`
  - `getPointValue(layer, lat, lon, time, level)`
- A “mock mode” using fixtures (`*.json` captured above) so UI wiring can be built without blocking.
- Token/key storage + refresh handling designed for:
  - bearer token w/ expiry
  - optional refresh token
  - graceful 401 recovery

---

## 6) Sources (public clues only)

These sources do **not** contain the full private SkySight forecast API contract, but they are relevant signals:

- SkySight feedback board entry about “API to access raster data” (SkySight has indicated they have an API):  
  https://feedback.skysight.io/posts/35/api-to-access-raster-data

- SkySight airspace TileServer instance (shows WMTS/TMS/XYZ + TileJSON distribution patterns):  
  https://airspace.skysight.io/

- HighSight product marketing (standard XYZ tiles + tile quotas, operated by SkySight Weather Pty Ltd):  
  https://highsight.dev/
