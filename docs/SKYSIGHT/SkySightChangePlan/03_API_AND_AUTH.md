# API and auth contract (stub)

This document intentionally avoids hardcoding secrets. Do NOT embed API keys, usernames, or passwords in code.

## Known auth endpoint (from integration notes)
POST https://skysight.io/api/auth
Headers:
- Content-Type: application/json
- X-API-KEY: <partner key>

Body (example):
{
  "username": "<user email or username>",
  "password": "<password>"
}

TODO: Confirm response shape (examples)
Option A (token):
{ "token": "...", "expiresAt": 1234567890 }

Option B (cookie/session):
- Set-Cookie headers
- A short JSON body

Codex must:
- Implement the auth call with Retrofit/OkHttp.
- Capture the response safely (never log secrets).
- Store the resulting session in secure storage owned by the auth repository.
- Expose AuthState via StateFlow.

## Required API capabilities for XC Pro integration
To render raster tiles, we need:
1) A way to list available forecast layers/parameters for a region and/or subscription.
2) For a chosen parameter + time, a raster tile URL template suitable for MapLibre (XYZ/WMTS/TMS) OR a WMS endpoint convertible to tiles.
3) Optional: a legend/scale endpoint for the chosen parameter.
4) Optional: a point-query endpoint returning the numeric value at (lat, lon, time, parameter).

### Contract placeholders (fill in once confirmed)
GET /api/forecast/parameters
-> returns list of parameters, units, availability window, etc.

GET /api/forecast/tileTemplate?parameterId=...&time=...
-> returns:
{
  "urlTemplate": "https://.../{z}/{x}/{y}.png?...",
  "minZoom": 0,
  "maxZoom": 12,
  "attribution": "...",
  "requiresAuthHeader": true/false
}

GET /api/forecast/legend?parameterId=...
-> returns color stops + labels + unit

GET /api/forecast/value?parameterId=...&time=...&lat=...&lon=...
-> returns { "value": 3.2, "unit": "m/s", "validTime": ... }

## Critical integration detail: headers vs URL tokens
MapLibre raster tile fetch may not support per-source custom headers cleanly.

Preferred solutions (choose based on SkySight API support):
A) Signed URL / query-token tiles (no custom headers required by MapLibre).
B) Configure MapLibre/OkHttp globally to attach auth headers for SkySight tile domains only.
C) If neither works, use an approved gateway/proxy that injects headers (ensure ToS/permission).

