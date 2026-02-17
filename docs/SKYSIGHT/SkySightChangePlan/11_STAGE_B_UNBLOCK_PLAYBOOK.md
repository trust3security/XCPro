# Stage B Unblock Playbook: Get Real SkySight Forecast API Contract (XCPro)

## Why Stage B is blocked
You can’t implement a working SkySight forecast adapter (parameters → tile template → legend → point value) until you have **real** endpoint paths, required query params, auth rules, and real JSON shapes.

Right now:
- `POST /api/auth` is confirmed working.
- The guessed forecast paths (`/api/forecast/*`) returning **404** means any adapter you build would be guesswork.

So the job is simple: **capture the real contract** once, then Codex can implement the adapter deterministically.

---

## Deliverable: the “evidence pack” (what we need to produce)
Create these files (redacted) in your repo:

```
docs/integrations/skysight/evidence/
  auth_success.txt
  auth_401.txt
  parameters_success.json
  parameters_error_401.json
  tile_template_success.json
  tile_template_error_401.json
  legend_success.json
  legend_error_401_or_404.json
  value_success.json
  value_no_data.json
  tile_fetch_success.txt
  tile_fetch_forbidden.txt
  RATE_LIMIT_NOTES.md
```

Each `*.txt` should include:
- Request: method + full URL
- Request headers actually required (with secrets redacted)
- Status code
- Response headers that matter (cache-control, etag, x-ratelimit*, retry-after, content-type)
- Response body (if any)

Each `*.json` should be the **raw response body** exactly as returned.

**Redaction rules (do not destroy structure):**
- Replace tokens/keys with `REDACTED`
- Keep field names, nesting, and types intact
- Keep numeric ranges and example strings if they are not secrets

### Convergence add-on evidence (recommended now)

If convergence is in scope, also capture:

```
docs/integrations/skysight/evidence/
  convergence_tile_success.txt
  convergence_legend_success.json
  convergence_value_probe.json
```

Use parameter id `wblmaxmin` for convergence probes. If point response does not
contain a convergence value field, record that result in
`convergence_value_probe.json` and keep point-value support disabled in XCPro
until SkySight provides official field mapping.

---

## Fastest path to unblock (choose ONE)

### Option A (best): Ask SkySight for the partner contract
This is the cleanest and lowest-risk path.

Send SkySight support/partner contact this exact request:

**Subject:** XCPro integration — request forecast API contract + tile auth

**Body:**
- We have working `POST /api/auth` (API key header `X-API-KEY: XCPRO`).
- We need the *forecast overlay* API contract to implement in XCPro.

Please provide:
1) Endpoint list + examples for:
   - parameters list
   - tile template for XYZ tiles
   - legend
   - point value (lat/lon + time)
2) Required params for each (region/model/run/time/parameter/etc).
3) Response JSON schemas (or real example payloads).
4) Tile authorization strategy:
   - signed URL/query token vs headers/cookies
   - cache headers expectations
5) Time semantics (UTC vs local, valid forecast windows).
6) Entitlement + rate-limit behavior (status codes, headers, retry guidance).

If they can provide OpenAPI/Swagger or a Postman collection, that’s perfect.

**Why this works:** once you have official paths + sample responses, adapter work becomes mechanical.

---

### Option B (fast + definitive): Capture endpoints from SkySight Web App via DevTools
If you (or someone on your team) can log in to the SkySight web UI, you can extract the contract in 10–15 minutes.

#### Steps
1) Open SkySight in Chrome.
2) Open **DevTools → Network**.
3) Enable:
   - **Preserve log**
   - **Disable cache**
4) Filter requests by:
   - `api`
   - `forecast`
   - `tile`
   - `legend`
5) Perform actions that force each call:
   - Open a forecast layer list (forces *parameters*)
   - Toggle a layer on the map (forces *tileTemplate* and then tile GETs)
   - Open the legend panel (forces *legend*)
   - Click/long-press map for point info (forces *value*)
   - Change time step (forces time semantics to show up)

#### What to record
For each of those requests:
- Right click → **Copy → Copy as cURL**
- Save the cURL output (redact tokens/password)
- Copy the response body into the matching `*.json` evidence file

#### Tile auth proof (critical)
When tiles are loading, click a tile request in Network and answer:
- Does it include an `Authorization` header? (usually impossible for standard XYZ raster tile fetches unless fetched manually)
- Does it include cookies?
- Is there a `key=` / `token=` / `sig=` query param?

Create:
- `tile_fetch_success.txt` = one successful tile request (request+response headers)
- `tile_fetch_forbidden.txt` = same tile URL but without auth material (or after logout) showing the failure

**Outcome:** you’ll have the real paths + params + JSON shapes.

---

### Option C (works if you already use another SkySight client): Capture from SeeYou / LXNAV / XCSoar forks
SkySight already integrates with other soaring tools (e.g. Naviter SeeYou integration is documented; XCSoar forks claim SkySight integration). Use those as “known-good clients” to reveal the contract. citeturn18search17turn18search6turn18search8turn18search7

How:
- Use an HTTPS proxy tool (Charles / Proxyman / mitmproxy) on a test device.
- Install the proxy certificate if required.
- Turn on SkySight overlay in the client and capture the network calls.

This can be harder due to certificate pinning in some apps — if you hit that, fall back to Option A or B.

---

## Minimal “probe harness” you can run once you learn the endpoints
Once you discover the real forecast paths from A or B, you can lock them into a repeatable curl harness.

Create `scripts/skysight_probe.sh` (example skeleton):

```bash
#!/usr/bin/env bash
set -euo pipefail

BASE_URL="https://skysight.io"
API_KEY="XCPRO"               # rotate if this was posted publicly
USER="REDACTED"
PASS="REDACTED"
OUT_DIR="docs/integrations/skysight/evidence"
mkdir -p "$OUT_DIR"

# 1) Auth
curl -sS -D "$OUT_DIR/auth_success_headers.txt" \
  -o "$OUT_DIR/auth_success_body.json" \
  -H "Content-Type: application/json" \
  -H "X-API-KEY: $API_KEY" \
  -d "{\"username\":\"$USER\",\"password\":\"$PASS\"}" \
  "$BASE_URL/api/auth"

# Extract token/key (example; adapt to real response field)
SESSION_KEY=$(jq -r '.key // empty' "$OUT_DIR/auth_success_body.json")

# 2) Parameters (REPLACE PATH + PARAMS once confirmed)
curl -sS -D "$OUT_DIR/parameters_headers.txt" \
  -o "$OUT_DIR/parameters_success.json" \
  -H "X-API-KEY: $API_KEY" \
  -H "Authorization: Bearer $SESSION_KEY" \
  "$BASE_URL/REPLACE_WITH_REAL_PARAMETERS_PATH?REPLACE_WITH_PARAMS"

# 3) Tile template
curl -sS -D "$OUT_DIR/tile_template_headers.txt" \
  -o "$OUT_DIR/tile_template_success.json" \
  -H "X-API-KEY: $API_KEY" \
  -H "Authorization: Bearer $SESSION_KEY" \
  "$BASE_URL/REPLACE_WITH_REAL_TILE_TEMPLATE_PATH?REPLACE_WITH_PARAMS"

# 4) Legend
curl -sS -D "$OUT_DIR/legend_headers.txt" \
  -o "$OUT_DIR/legend_success.json" \
  -H "X-API-KEY: $API_KEY" \
  -H "Authorization: Bearer $SESSION_KEY" \
  "$BASE_URL/REPLACE_WITH_REAL_LEGEND_PATH?REPLACE_WITH_PARAMS"

# 5) Point value
curl -sS -D "$OUT_DIR/value_headers.txt" \
  -o "$OUT_DIR/value_success.json" \
  -H "X-API-KEY: $API_KEY" \
  -H "Authorization: Bearer $SESSION_KEY" \
  "$BASE_URL/REPLACE_WITH_REAL_VALUE_PATH?lat=...&lon=...&time=...&param=..."
```

Notes:
- Don’t hardcode personal creds in git.
- If SkySight uses cookies instead of bearer tokens, store the cookie jar: `-c cookies.txt -b cookies.txt`.

---

## How to answer the 6 “unknowns” quickly

### 1) Endpoint list + required params
From DevTools, for each request:
- copy full URL
- copy query string
- note any required headers/cookies

### 2) Real response JSON shapes
Save raw response bodies and don’t hand-edit formatting.

### 3) Tile auth strategy
Prove it with tile fetch tests:
- direct tile URL in a private browser (no cookies)
- curl with/without headers

### 4) Time semantics
Do 3 tests and save responses:
- a valid time (known available)
- a time just outside availability window
- an invalid format (to see error payload)

### 5) Entitlement + rate limit
Do controlled failures:
- call forecast endpoint without auth → capture 401
- call with wrong/expired session → capture 401/403
- spam a harmless endpoint until rate-limit triggers (only if SkySight allows this; otherwise ask support) and record headers

### 6) Parameter metadata
The parameters response should ideally include:
- stable parameter id
- name
- units
- category
- supported render types (tiles, legend, point query)

If it doesn’t, that metadata must come from another endpoint or a static mapping file (SkySight needs to provide).

---

## Practical advice (so you don’t burn time)
- **Don’t implement Stage B adapter until you have at least one real “happy path” for parameters → tileTemplate → tiles → legend → value.**
- **Implement Track A now** (fake provider + overlay runtime). It de-risks your map/UI plumbing so Stage B becomes only “swap provider.”
- Use a dedicated SkySight integration account and rotate any keys you’ve shared publicly.

---

## If you paste me ONE HAR export, I can map it to the evidence pack
If you export a DevTools HAR (after exercising parameters/tileTemplate/legend/value), we can:
- extract real endpoints + params
- extract JSON bodies
- identify tile auth strategy
- generate the evidence files and a concrete adapter contract

(Just redact session tokens and credentials first.)
