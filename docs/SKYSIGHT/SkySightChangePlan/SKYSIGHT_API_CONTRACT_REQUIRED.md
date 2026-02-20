# SKYSIGHT_API_CONTRACT_REQUIRED.md

This file defines the **minimum confirmed SkySight API contract** required to implement the SkySight provider adapter for XCPro’s Forecast Overlay MVP.

Status update (2026-02-18):
- Required evidence artifacts exist under docs/integrations/skysight/evidence/.
- The SkySight forecast adapter is implemented and wired in production DI.
- Keep this contract as the gate checklist for future contract or endpoint refresh work.

## Hard rule
**Do not implement the real SkySight forecast adapter until the evidence pack exists in the repo.**  
Until then, implement **Track A (FakeForecastProvider + overlay runtime)** only.

## Minimum required confirmations (MVP unblock)

You must have real evidence for:

1. **Auth response contract** from real calls  
   - Full `POST /api/auth` success and failure responses (headers + body)  
   - Confirm whether auth is **token**, **cookie**, or **hybrid**.

2. **Required auth on forecast endpoints**  
   - Exact header/cookie rules after login  
   - Whether tile URLs require headers/cookies or are signed/query-token URLs.

3. **Confirmed endpoint list with real working examples** for:
   - “parameters”
   - “tileTemplate”
   - “legend”
   - “value”

   ⚠️ If guessed `/api/forecast/*` returns 404, you must find the real base path first.

4. **Sample JSON** for each endpoint  
   - At least one **success** response  
   - At least one **error/no-data** response (401/403/404 or “no data” equivalent)

5. **Time semantics**
   - Input format (epoch vs ISO 8601)  
   - Timezone rules (UTC/local, required tz param?)  
   - Valid forecast windows and “unavailable time” behavior.

6. **Parameter metadata**
   - Parameter IDs
   - Units
   - Categories / ordering
   - Which support legend / point query / tiles

7. **Tile details**
   - min/max zoom
   - tile size
   - attribution
   - URL template fields
   - cache headers

8. **Rate-limit + entitlement behavior**
   - status codes and headers (e.g., 429 + Retry-After)
   - entitlement failures (403/402/404?) and expected error bodies
   - retry guidance

9. **Product defaults decisions**
   - initial parameter/time behavior
   - overlay z-order
   - long-press conflict behavior

10. **Evidence files committed (redacted)**
   - auth_success.txt, auth_401.txt
   - parameters_success.json
   - tile_template_success.json
   - legend_success.json
   - value_success.json
   - tile header/auth proofs
   - RATE_LIMIT_NOTES.md

## Evidence pack location (required)
Commit the evidence files under:

`docs/integrations/skysight/evidence/`

See `SKYSIGHT_EVIDENCE_CAPTURE.md` for steps.

