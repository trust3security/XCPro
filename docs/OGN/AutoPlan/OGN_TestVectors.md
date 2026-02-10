# OGN Test Vectors and Expected Parsing

This file provides concrete sample inputs and expected outputs for unit tests.
All examples are ASCII-only.

---

## 1. APRS (TNC2) sample: OGN aircraft beacon (uncompressed position)

Input line:

FLRDDDEAD>APRS,qAS,EDFO:/152345h4903.50N/07201.75W^110/064/A=002526 id0ADDDEAD -019fpm +0.6rot 1.3dB 2.0kHz gps4x5

Expected parse (YAML-ish):

srcCallsign: "FLRDDDEAD"
destCallsign: "APRS"
path:
  - "qAS"
  - "EDFO"

aprs:
  dataType: "/"
  timestampRaw: "152345h"   # do not depend on this for monotonic age
  lat: 49.0583333333       # 49 deg + (3.50 min / 60) = 49.058333...
  lon: -72.0291666667      # 72 deg + (1.75 min / 60) = 72.029166..., W -> negative
  symbolTable: "/"
  symbolCode: "^"
  courseDeg: 110.0
  speedKnots: 64.0
  speedMps: 32.9244        # 64 * 0.514444
  altitudeFt: 2526
  altitudeM: 769.9248      # 2526 * 0.3048

ogn:
  deviceIdHex: "DDDEAD"    # from id token (last 6 hex)
  climbFpm: -19.0
  climbMps: -0.09652       # -19 * 0.00508
  rot: 0.6
  signalDb: 1.3
  freqOffsetKhz: 2.0
  gpsToken: "gps4x5"

---

## 2. Device ID fallback: from callsign

Input line:

ICA484D20>APRS,TCPIP*,qAC,GLIDERN1:!4903.50N/07201.75W^110/064/A=001000

Expected:
- deviceIdHex = "484D20" (from callsign pattern ^[A-Z]{3}[0-9A-F]{6}$)

---

## 3. DDB JSON fixture (minimal)

Input JSON:

{
  "devices": [
    {
      "device_type": "F",
      "device_id": "0123BC",
      "aircraft_model": "LS-4",
      "registration": "X-0123",
      "cn": "23",
      "tracked": "Y",
      "identified": "Y",
      "aircraft_type": "1"
    }
  ]
}

Expected parsed model:
- devices[0].deviceType == "F"
- devices[0].deviceId == "0123BC"
- devices[0].tracked == true
- devices[0].identified == true

Privacy test fixture:
- same but tracked = "N" -> device must never be displayed

---

## 4. Subscription center update threshold test (behavior)

Given:
- active subscription center = (lat1, lon1)
- new camera center (camera idle) = (lat2, lon2)
- receiveRadiusKm = 300
- thresholdKm = 75

Expect:
- reconnect only when distance(center1, center2) >= 75 km
- no reconnect on zoom-only change when center is unchanged

Use a deterministic haversine implementation.

---

## 5. Viewport-only display behavior (rendering)

Given:
- received targets are already available from current 300 km subscription
- current viewport bounds represent about 100 km map extent
- targets:
  - T1 inside viewport
  - T2 outside viewport but inside 300 km subscription

Expect:
- overlay shows T1
- overlay does NOT show T2

When:
- user zooms out and viewport expands to include both T1 and T2

Expect:
- overlay shows both T1 and T2 without requiring reconnect (center unchanged)

When:
- user pans so new camera center is >= 75 km from active subscription center

Expect:
- subscription updates/reconnects on camera-idle (debounced)
- new in-view targets appear after new feed data arrives
