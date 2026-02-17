# OGN_PROTOCOL_NOTES.md — XCPro OGN Integration (Authoritative)

## Purpose
This document defines the exact protocol, parsing rules, assumptions,
privacy handling, and test vectors for Open Glider Network (OGN) traffic
ingestion in XCPro.

This file is the single source of truth for OGN protocol behavior.

---

## Connection Endpoint
- Host: aprs.glidernet.org
- Port: 14580 (filtered APRS-IS feed)
- Transport: TCP (line-oriented text)

Login format (receive-only):
user OGNXC1 pass <APRS_PASSCODE> vers XCPro 0.1 filter r/<lat>/<lon>/150

---

## Filtering Strategy
- Center source: ownship/user GPS position
- Server-side: radius filter r/<lat>/<lon>/150
- Client-side: haversine distance <= 150 km
- Stale warning: 60 seconds (visual fade)
- Eviction: 120 seconds

---

## Parsing Rules (Minimum Viable)
Accepted packet type: OGN aircraft beacons (APRS TNC2 format).

Example test vector:
FLRDDDEAD>APRS,qAS,EDER:/114500h5029.86N/00956.98E'342/049/A=005524 id0ADDDEAD -454fpm -1.1rot

From packets extract:
- id (from idXXXX token or source callsign)
- latitude / longitude
- course / speed (if present)
- altitude from A= (feet → meters)
- climb rate from fpm → m/s (if present)

Drop packets with:
- invalid or missing lat/lon
- no stable identifier

---

## Unit Conversions
- knots → m/s = knots × 0.514444
- feet → meters = feet × 0.3048
- fpm → m/s = fpm × 0.00508

---

## Privacy Handling
- If callsign is missing or privacy-restricted, show anonymized ID only.
- Do not persist raw traffic history.
- Display informational-only disclaimer.

---

## Required Tests
- Parse canonical OGN example line correctly.
- Haversine distance thresholds: 149.9 / 150.0 / 150.1 km.
- Stale eviction timing.
- Duplicate ID update replaces prior target.

---

## Assumptions
- Receive-only connection (no uplink).
- Conservative parsing; unknown fields ignored.
- OGN traffic disabled by default in replay mode.
