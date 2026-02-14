# Risks, legal, and compliance notes

## 1) Terms and licensing risk
SkySight's website terms include limitations such as:
- no "reverse engineer"
- no "mirror" materials on another server

This matters for:
- building a tile proxy that caches tiles
- bulk downloading forecasts/tiles for offline use
- scraping web endpoints

Mitigation:
- Get written permission / partner agreement for the intended API usage and any caching/proxying.
- Prefer server-provided HTTP caching headers rather than aggressive client-side caching.
- Keep any gateway/proxy strictly pass-through unless explicitly allowed.

## 2) Auth / credential security
- Never log credentials or tokens.
- Use secure storage for tokens.
- Provide explicit logout that clears tokens.

## 3) Privacy risk
Map point queries can reveal user interest areas. Treat requests as sensitive:
- avoid verbose analytics
- avoid logging precise lat/lon in release builds

## 4) Map tile delivery constraints
MapLibre raster sources may not support custom per-request headers easily.
If SkySight requires auth headers for tiles:
- prefer signed tile URLs or query-token based access
- otherwise configure global HTTP stack for SkySight domains only
- last resort: an approved gateway that injects headers (permission required)

## 5) Product ambiguity
There is a big difference between:
- "show forecast overlays"
vs
- "rebuild the SkySight tool suite (skew-T, windgrams, route forecast, wave x-section)"

Do not commit to the latter unless the API and product scope explicitly require it.

