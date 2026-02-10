# ADSB_TestVectors.md
Minimal deterministic fixtures for unit tests.

## 1) OpenSky /states/all response (extended=1)
This is a synthetic response that matches OpenSky’s documented array-of-arrays format.
Use it to verify index mapping and nullable handling.

```json
{
  "time": 1710000000,
  "states": [
    [
      "abc123", "CALL123 ", "Australia",
      1710000000, 1710000001,
      151.2000, -33.8600,
      1200.0,
      false,
      80.0,  270.0,  1.5,
      null,
      1250.0,
      "7000",
      false,
      0,
      2
    ],
    [
      "def456", null, "Australia",
      null, 1710000002,
      151.2500, -33.9000,
      null,
      false,
      null, null, null,
      null,
      null,
      null,
      false,
      3,
      0
    ]
  ]
}
```

Expected parsing:
- State #1:
  - id = "abc123"
  - callsign = "CALL123" (trim)
  - lat = -33.8600
  - lon = 151.2000
  - altitudeM = geo_altitude=1250.0 (prefer geo over baro)
  - speedMps = 80.0
  - trackDeg = 270.0
  - climbMps = 1.5
  - position_source = 0 (ADS‑B)
  - category = 2
- State #2:
  - id = "def456"
  - lat/lon present
  - altitude null
  - position_source = 3 (FLARM) => should be dropped if ADS‑B-only filter enabled

## 2) Distance filter fixtures
Use a fixed origin:
- origin = (-33.8688, 151.2093) Sydney CBD
Test points:
- inside:
  - (-33.8731, 151.2060) distance ~ few hundred meters => include
- near-edge (approx):
  - Use a point computed by your bbox math with radius 20km and then verify Haversine <= 20000
- outside:
  - Add ~0.20 deg latitude away and assert excluded (tolerance)

## 3) Rate limit handling fixture
Simulate provider returning:
- 429 with `X-Rate-Limit-Retry-After-Seconds: 120`
Expected:
- repository enters BackingOff(120)
- no polls for ~120s (in test use virtual time)
- resumes polling after delay

