# SkySight Forecast API Contract Evidence Pack (MVP Unblock)

This document exists to **lock down the real SkySight API contract** needed to implement the XCPro MVP correctly (forecast overlays + parameter/time selection + legend + “exact value at point”).

**Reality check:** I could not find a public, authoritative SkySight forecast API spec (endpoints/params/JSON shapes/auth/rate limits). What *is* publicly confirmable is that:
- SkySight has an API used by third‑party devices/software (SkySight’s developer references “our API” and mentions LXNAV devices showing “all the parameters in our API”).  
  Source: https://groups.google.com/g/lxnav-user-group/c/M_3tExlYHzE
- SkySight operates tile services elsewhere (airspace server) using **WMTS / TMS / XYZ / TileJSON** conventions, which is relevant for MapLibre raster overlays.  
  Source: https://airspace.skysight.io/
- There is a public feedback statement that forecast times are “currently available from 08:00 to 20:00” (time semantics hint, but **not** a contract).  
  Source (snippet-indexed): https://feedback.skysight.io/posts/74/time-range-time-zone

Because the **missing items are behind authentication**, the only reliable path is to generate a small “evidence pack” (redacted) from real curl calls.

## Status update (2026-02-15): active discovery direction

Confirmed from real authenticated probing:
- `POST https://skysight.io/api/auth` works.
- Guessed forecast endpoints under `https://skysight.io/api/forecast/*` return `404` and are not the active contract.

Discovered from authenticated `https://xalps.skysight.io/secure/` and frontend bundles:
- Forecast tiles/updates are built from `https://edge.skysight.io/...`
  - `/{target}/{YYYYMMDD}/{HHmm}/{param}/{z}/{x}/{y}`
  - `/{target}/{YYYYMMDD}/{HHmm}/wind/{z}/{x}/{y}/{param}`
  - `/{target}/{YYYYMMDD}/{HHmm}/grid/{z}/{x}/{y}/{param}`
  - `/{target}/{YYYYMMDD}/{HHmm}/updated`
- Point values appear at `POST https://cf.skysight.io/point/{lat}/{lon}?region={region}` with body like `{"times":[...]}`.
- Parameter metadata appears in authenticated `/secure/` bootstrap payload (`const params = {...}`), and may not be exposed as a standalone `/api/forecast/parameters` endpoint.

This means Stage B evidence should prioritize `edge.skysight.io` + `cf.skysight.io`, and keep `/api/forecast/*` only as negative evidence.

---

## 0) What you need to produce (files)

Put these files (redacted) in the repo so Codex can implement without guessing:

```
docs/integrations/skysight/evidence/
  auth_success.txt
  auth_401.txt
  parameters_success.json
  parameters_401.txt
  tile_template_success.json
  tile_template_error.txt
  legend_success.json
  legend_error.txt
  value_success.json
  value_no_data.txt
  tile_sample_headers_ok.txt
  tile_sample_headers_fail.txt
  RATE_LIMIT_NOTES.md
```

**Notes**
- `*.txt` should contain **headers + body** (use `curl -i`).
- `*.json` should contain **body only** (clean JSON makes it easier to generate typed models).

---

## 1) Do NOT leak secrets (required redaction rules)

You must redact:
- passwords
- session tokens / JWTs
- refresh tokens
- cookies (`Set-Cookie` values)
- any query-token or signed URL token values

**But keep:**
- header *names* (e.g., `Set-Cookie`, `Authorization`, `X-API-KEY`)
- cookie *attributes* (e.g., `HttpOnly`, `Secure`, `SameSite`, `Path`, `Domain`, `Max-Age`)
- JSON *field names* + value *types*
- status codes and error bodies intact

Suggested redaction pattern:
- Replace token values with `<REDACTED>` but keep 1–2 chars prefix if helpful:
  - `Authorization: Bearer eyJ...` → `Authorization: Bearer <REDACTED>`
  - `Set-Cookie: session=abc...; HttpOnly; Secure` → `Set-Cookie: session=<REDACTED>; HttpOnly; Secure`

---

## 2) Environment setup (so your shell history stays clean)

### 2.1 Export creds in your shell (macOS/Linux)

```bash
export SKYSIGHT_USER='you@example.com'
export SKYSIGHT_PASS='your_password_here'
export SKYSIGHT_APP_KEY='YOUR_PARTNER_KEY_HERE'   # e.g. XCPRO (do NOT commit)
```

### 2.2 Create output directory

```bash
mkdir -p docs/integrations/skysight/evidence
cd docs/integrations/skysight/evidence
```

---

## 3) Auth contract capture (required)

### 3.1 Success: POST /api/auth (headers + body)

This captures **whether auth is cookie-based and/or token-based**.

```bash
jq -n --arg u "$SKYSIGHT_USER" --arg p "$SKYSIGHT_PASS" '{username:$u, password:$p}' \
| curl -sS -i -X POST "https://skysight.io/api/auth" \
  -H "Content-Type: application/json" \
  -H "X-API-KEY: $SKYSIGHT_APP_KEY" \
  --data-binary @- \
  --cookie-jar cookies.txt \
  > auth_success.txt
```

What to look for in `auth_success.txt`:
- Any `Set-Cookie:` headers → session likely cookie-based.
- Any JSON token fields (e.g., `token`, `accessToken`, `jwt`, `expires`, etc.) → token-based or hybrid.

### 3.2 Failure: POST /api/auth with wrong password (headers + body)

```bash
jq -n --arg u "$SKYSIGHT_USER" --arg p "wrongpassword" '{username:$u, password:$p}' \
| curl -sS -i -X POST "https://skysight.io/api/auth" \
  -H "Content-Type: application/json" \
  -H "X-API-KEY: $SKYSIGHT_APP_KEY" \
  --data-binary @- \
  > auth_401.txt
```

Required outputs to confirm:
- status code for invalid creds (401 vs 403 vs 400)
- JSON error shape + message fields (for UI mapping)

---

## 4) Forecast endpoints capture (required)

> Important: the `/api/forecast/*` calls below are now legacy probes used to prove 404 assumptions.
> Use Section "4.6 Active endpoint capture" for the primary Stage B evidence.

### 4.1 Confirm auth method used after login

There are three typical patterns. The evidence pack must make it obvious which one SkySight uses:

1) Cookie session:
- Requests must include cookie set during login
- Use `--cookie cookies.txt`

2) Bearer token:
- Auth response includes token → subsequent calls require `Authorization: Bearer <token>`

3) Hybrid:
- Some endpoints use cookies, tiles use signed URLs, etc.

The commands below assume **cookie session** (because it’s easiest to demonstrate with curl).  
If your `auth_success.txt` indicates **Bearer token**, substitute:

```bash
-H "Authorization: Bearer <REDACTED>"
```

---

### 4.2 GET /api/forecast/parameters

#### Success (body only JSON)
```bash
curl -sS "https://skysight.io/api/forecast/parameters" \
  -H "X-API-KEY: $SKYSIGHT_APP_KEY" \
  --cookie cookies.txt \
  -o parameters_success.json
```

#### Unauthorized (headers + body)
```bash
curl -sS -i "https://skysight.io/api/forecast/parameters" \
  -H "X-API-KEY: $SKYSIGHT_APP_KEY" \
  -o parameters_401.txt
```

Minimum contract fields we need from `parameters_success.json`:
- parameter id (string)
- display name (string)
- category/group (string) (e.g., Thermal/Wind/Cloud)
- units (string)
- supported features flags:
  - hasLegend (bool)
  - supportsPointValue (bool)
  - supportsTiles (bool)
- time step info (minutes) OR list of valid times OR “model run” semantics

---

### 4.3 GET /api/forecast/tileTemplate

This endpoint usually needs query params (parameter + time + maybe model run + maybe region).

#### 4.3.1 First: call with no query to capture missing-param error shape
```bash
curl -sS -i "https://skysight.io/api/forecast/tileTemplate" \
  -H "X-API-KEY: $SKYSIGHT_APP_KEY" \
  --cookie cookies.txt \
  > tile_template_error.txt
```

#### 4.3.2 Then: success call (update query params once you know them)
**Replace the query params based on the error message or docs SkySight provides.** Example placeholders:
- `parameter=<id>`
- `time=<iso8601>`
- `tz=<IANA tz or offset>`
- `run=<modelRunId>`

```bash
curl -sS "https://skysight.io/api/forecast/tileTemplate?parameter=<PARAM_ID>&time=<ISO_TIME>" \
  -H "X-API-KEY: $SKYSIGHT_APP_KEY" \
  --cookie cookies.txt \
  -o tile_template_success.json
```

Tile contract fields we need:
- URL template (XYZ) and what substitutions it expects (`{z}/{x}/{y}` and/or query args)
- tile size (usually 256)
- minZoom, maxZoom
- attribution string(s)
- cache headers policy (may be in tile response headers, see Section 5)
- whether URL is already signed / contains token or needs headers

---

### 4.4 GET /api/forecast/legend

Same approach: capture error first, then success.

```bash
curl -sS -i "https://skysight.io/api/forecast/legend" \
  -H "X-API-KEY: $SKYSIGHT_APP_KEY" \
  --cookie cookies.txt \
  > legend_error.txt
```

Then (fill required params):
```bash
curl -sS "https://skysight.io/api/forecast/legend?parameter=<PARAM_ID>" \
  -H "X-API-KEY: $SKYSIGHT_APP_KEY" \
  --cookie cookies.txt \
  -o legend_success.json
```

Legend contract fields we need:
- color ramp stops (value range + color)
- units label (or reference to parameter units)
- no-data sentinel handling (transparent pixels, NaN, etc.)

---

### 4.5 GET /api/forecast/value (exact value at point)

Error first:
```bash
curl -sS -i "https://skysight.io/api/forecast/value" \
  -H "X-API-KEY: $SKYSIGHT_APP_KEY" \
  --cookie cookies.txt \
  > value_no_data.txt
```

Success (fill required params; typical: lat, lon, parameter, time):
```bash
curl -sS "https://skysight.io/api/forecast/value?lat=<LAT>&lon=<LON>&parameter=<PARAM_ID>&time=<ISO_TIME>" \
  -H "X-API-KEY: $SKYSIGHT_APP_KEY" \
  --cookie cookies.txt \
  -o value_success.json
```

Value contract fields we need:
- numeric value + units
- validTime returned (server’s truth)
- parameter id echoed
- error/no-data encoding (404 vs 204 vs 200 with null field)

---

### 4.6 Active endpoint capture (edge/cf path family)

#### 4.6.1 Parameters metadata (authenticated bootstrap)
Capture parameter catalog from authenticated SkySight `/secure/` page bootstrap object.

Evidence target:
- `parameters_success.json` as extracted `params` object (raw JSON object)

Minimum fields required:
- parameter id keys (for example `wstar_bsratio`, `hwcrit`, `pfdtot`)
- `name`, `unit`, `type`, `min`, `max`, `step`
- model availability metadata (if present)

#### 4.6.2 Tile template contract (edge)
Capture a proven template for the selected parameter/time:

```bash
curl -sS -I "https://edge.skysight.io/<TARGET>/<YYYYMMDD>/<HHmm>/<PARAM>/<z>/<x>/<y>" > tile_sample_headers_ok.txt
```

Also capture wind/grid variants if used by the selected parameter:

```bash
curl -sS -I "https://edge.skysight.io/<TARGET>/<YYYYMMDD>/<HHmm>/wind/<z>/<x>/<y>/<PARAM>" >> tile_sample_headers_ok.txt
curl -sS -I "https://edge.skysight.io/<TARGET>/<YYYYMMDD>/<HHmm>/grid/<z>/<x>/<y>/<PARAM>" >> tile_sample_headers_ok.txt
```

Create `tile_template_success.json` with the resolved template fields and zoom bounds used by XCPro.

#### 4.6.3 Point value (cf)

```bash
curl -sS -X POST "https://cf.skysight.io/point/<LAT>/<LON>?region=<REGION>" \
  -H "Content-Type: application/json; charset=UTF-8" \
  --data '{"times":["<YYYYMMDDHHmm>"]}' \
  -o value_success.json
```

Capture an invalid or out-of-range request to produce `value_no_data.txt`.

#### 4.6.4 Updated marker (optional but useful)

```bash
curl -sS "https://edge.skysight.io/<TARGET>/<YYYYMMDD>/<HHmm>/updated" -o edge_updated.json
```

Use this to confirm model-run/time freshness semantics.

---

## 5) Tile authentication strategy + caching headers (required)

From `tile_template_success.json`, extract a real tile URL and test it.

### 5.1 Fetch a tile WITHOUT cookies/headers (capture headers)
```bash
curl -sS -I "<TILE_URL_WITH_ZXY_FILLED>" > tile_sample_headers_fail.txt
```

### 5.2 Fetch the same tile WITH cookies (capture headers)
```bash
curl -sS -I "<TILE_URL_WITH_ZXY_FILLED>" \
  -H "X-API-KEY: $SKYSIGHT_APP_KEY" \
  --cookie cookies.txt \
  > tile_sample_headers_ok.txt
```

Interpretation:
- If **fail=401/403** but **ok=200**, tiles require auth headers/cookies.
- If **fail=200** and URL contains a token-ish query param, tiles are likely **signed/query-token URLs**.
- Record cache headers you see:
  - `Cache-Control`, `ETag`, `Last-Modified`, `Expires`, `Age`, `Vary`

These directly inform XCPro tile caching expectations and whether a proxy is required.

---

## 6) Time semantics capture (required)

You need to prove:
- what time format is accepted (ISO 8601? epoch? date+hour?)
- what timezone is assumed if no TZ specified
- what the valid forecast window is and how it fails

### 6.1 Record what the server expects
Often `tile_template_error.txt` and `value_no_data.txt` will explicitly tell you required formats.

### 6.2 Probe UTC vs local
Pick a single parameter/time and try:
- `time=2026-02-14T03:00:00Z`
- `time=2026-02-14T14:00:00+11:00`
- `time=2026-02-14T14:00:00` (no TZ)

Record:
- status code changes
- returned `validTime` changes
- whether “no data” occurs outside a local window

Also: there’s a public statement on SkySight Feedback that forecast times are “currently available from 08:00 to 20:00”, which is a clue you should verify in API behavior.  
Source (snippet-indexed): https://feedback.skysight.io/posts/74/time-range-time-zone

---

## 7) Rate-limit + entitlement behavior (required)

Create a short `RATE_LIMIT_NOTES.md`:

Minimum content:
- Does any endpoint return `429`?
- What response headers exist? (`Retry-After`, `X-RateLimit-*`)
- What do entitlement failures look like? (`402`? `403`? `404`?)
- Is “no data” represented differently from “not entitled”?

How to provoke:
- Hit `/api/forecast/value` 30–60 times quickly for the same point/time (be reasonable).
- Request a known out-of-region area (if coverage is regional).
- Request time far beyond the forecast range.

**Do not hammer the service excessively.**

---

## 8) Parameter metadata needs (required)

In `parameters_success.json`, ensure there is enough information to drive the XCPro UI without hardcoding:

Must have (or be derivable):
- grouping/category ordering
- display name localization (if supported)
- unit family
- which parameters support:
  - overlays/tiles
  - legend
  - exact value

If any of those are missing, add a note: XCPro will need a provider-side mapping table.

---

## 9) Product defaults decisions (write these down so Codex doesn’t guess)

These aren’t API contract items, but they’re required to finish the MVP without bikeshedding later.

Put final decisions in this file once you choose:

- Default parameter: `<PARAM_ID>`
- Default day/time:
  - Strategy A: “today at local 14:00”
  - Strategy B: “nearest available time >= now”
- Default opacity: `0.6` (example)
- Overlay z-order (top to bottom):
  1) aircraft/track labels
  2) task + airspace
  3) forecast overlay
  4) base map
- Long-press conflict behavior with task editing:
  - Option A: long-press queries forecast only when “Forecast Info” mode is enabled
  - Option B: long-press queries only when not in task-edit mode
  - Option C: use single-tap on info button instead

---

## 10) Contract summary template (fill after evidence pack exists)

Once evidence is captured, fill the following (and commit it):

### POST /api/auth
- Auth mechanism: cookie / bearer / hybrid
- Success status:
- Failure status:
- Response JSON schema:
- Cookies set:

### GET /api/forecast/parameters
- Auth required:
- Query params:
- Success schema:
- 401/403 schema:
- Notes:

### GET /api/forecast/tileTemplate
- Auth required:
- Required params:
- Success schema:
- Error schema:
- Tile URL format:
- Tile auth strategy:

### GET /api/forecast/legend
- Auth required:
- Required params:
- Success schema:
- Error/no-data schema:

### GET /api/forecast/value
- Auth required:
- Required params:
- Success schema:
- No-data behavior (status + body):
- Error behavior:

### Tiles (actual tile response)
- min/max zoom:
- tile size:
- content type:
- cache headers:
- retry guidance:
