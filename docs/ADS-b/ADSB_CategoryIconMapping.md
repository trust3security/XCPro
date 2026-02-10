# ADSB_CategoryIconMapping.md — OpenSky category → XCPro icon buckets
**v3: keep category mapping, but do not confuse it with aircraft model/type**

OpenSky category is retrieved from `/states/all` ONLY when request uses `extended=1`.  
Source: OpenSky REST docs.

OpenSky category values are ADS‑B emitter categories, and many aircraft report:
- 0 (no information)
- 1 (no category)

That is normal.

Sources:
- https://openskynetwork.github.io/opensky-api/rest.html

---

## 1) Supported categories (XCPro mapping)

We only map these to icons:

| OpenSky category | Meaning | XCPro Icon |
|---:|---|---|
| 2, 3 | Light / Small | Plane (light) |
| 4, 5, 6 | Large / High vortex / Heavy | Plane (large) |
| 7 | High performance | Plane (large) |
| 8 | Rotorcraft | Helicopter |
| 9 | Glider | Glider |
| 10 | Lighter-than-air | Balloon |
| 11 | Parachutist | Parachutist |
| 12 | Ultralight / hang/paraglider | Hang glider |
| 14 | UAV | Drone |
| else | unknown / surface / obstacle / missing | Unknown |

---

## 2) IMPORTANT: category != aircraft typecode/model
Even if category is unknown (0/1), you can still identify the aircraft via ICAO24 metadata:
- registration
- typecode
- model

See `ADSB_AircraftMetadata.md`.

---

## 3) Optional inference fallback (for category 0/1)
If category is 0/1/null:
- do not pretend it is “direct”
- optionally infer a **display bucket** from speed/altitude (label confidence=INFERRED)

(Keep inference deterministic and unit-tested.)

Recommended inference (best-effort):
- on_ground=true → UNKNOWN (don’t guess)
- missing speed → UNKNOWN
- speed <= 12 m/s and altitude > 100 m → BALLOON
- speed <= 70 m/s and altitude <= 2500 m → HELICOPTER
- speed >= 120 m/s → PLANE_LARGE
- else → PLANE_LIGHT
