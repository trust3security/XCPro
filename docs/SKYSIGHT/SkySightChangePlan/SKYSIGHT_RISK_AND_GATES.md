# SKYSIGHT_RISK_AND_GATES.md

This checklist prevents common “we built it but it doesn’t work” outcomes.

## 1) Legal / partner permission gate
If XCPro is commercial and you proxy/cache tiles, you likely need explicit permission.
SkySight ToS includes restrictions on commercial use and mirroring. Review with SkySight partner contact.
- https://skysight.io/tos

## 2) Auth UX model gate
Prefer partner linking/token exchange over collecting SkySight passwords (if supported).
If password login is used, store tokens securely and avoid logging.

## 3) Tile auth strategy gate (make-or-break)
Confirm:
- Are tiles accessible without auth (signed/query token)?
- Or do tiles require headers/cookies?
MapLibre header injection may be limited; if headers are required, plan the implementation early.

Evidence required:
- `tile_sample_headers_ok.txt`
- `tile_sample_headers_fail.txt`

## 4) Projection and tile scheme correctness
Confirm:
- XYZ vs TMS y-axis
- Web Mercator (EPSG:3857)
- tile size
- transparency/no-data rules

## 5) Legend correctness
SkySight may use dynamic legend scaling (by viewport/day). Confirm if legend is:
- fixed per parameter
- per time
- per viewport

## 6) Time semantics correctness
Confirm:
- accepted time input format
- timezone assumptions
- availability windows (e.g., local 08:00–20:00)
- “unavailable time” behavior (status code + body)

## 7) Rate limiting + performance
MapLibre requests many tiles; scrubbing time can amplify.
Mitigate by:
- debouncing time changes (apply on release / idle)
- backoff on 429 with Retry-After
- caching tile templates and legends

## 8) Entitlements and region behavior
Confirm how entitlement failures differ from “no data”:
- 403 vs 404 vs 200 with empty payload
Use `allowed_regions` as a UI/logic gate.

## 9) Mode interactions (task edit, replay)
Define:
- long-press behavior in task edit mode (default: disabled)
- replay mode behavior: freeze time vs follow replay clock vs user selection
- style reload behavior: overlay must reapply cleanly
