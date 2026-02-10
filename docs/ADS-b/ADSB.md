# ADSB.md — Live ADS‑B Internet Traffic in XCPro (OpenSky) — 15 km
**v5 (AUTONOMOUS): ICAO24 identity + aircraft metadata enrichment (registration/typecode/model)**

This document is written for an autonomous engineering agent (Codex).
It must be implementable end‑to‑end without asking the user questions.

---

## 0) What “identify the aircraft” means (definition)
There are **two** different “type” concepts:

### 0.1 Aircraft identification (what pilots actually mean by “what aircraft is that?”)
Using ICAO24 (the transponder address) as a key, display:
- **Registration** (tail number) — e.g. `VH‑DFV`
- **Typecode** (ICAO aircraft type designator) — e.g. `C208`
- **Model** — e.g. `CESSNA 208 Caravan`
- Optional: `icaoaircrafttype` (ICAO Doc 8643 3‑symbol aircraft description, e.g. `L1T`)

This requires **metadata enrichment**. `/states/all` alone does not provide these.

### 0.2 Emitter category (what OpenSky calls `category`)
OpenSky `/states/all?extended=1` can provide an ADS‑B **emitter category bucket** (light/small/rotorcraft/glider/etc).
Many aircraft report `category=0` or `1` (no info), which is normal.

**Rule:** In the UI, never label OpenSky `category` as “Aircraft Type”. Call it **Emitter category**.

OpenSky state vector + `icao24` + `category` reference:
https://openskynetwork.github.io/opensky-api/rest.html

---

## 1) Data sources
### 1.1 Live traffic (positions, speed, alt, track, climb rate)
Use OpenSky REST:
`GET https://opensky-network.org/api/states/all?lamin=...&lomin=...&lamax=...&lomax=...&extended=1`

Docs:
https://openskynetwork.github.io/opensky-api/rest.html

### 1.2 Aircraft metadata (registration / model / typecode) keyed by ICAO24
Use OpenSky Aircraft Database CSV dataset:
- Primary dataset URL:
  https://opensky-network.org/datasets/metadata/aircraftDatabase.csv
- OpenSky explains the aircraft database can be downloaded as CSV and is updated irregularly (snapshots may exist):
  https://opensky-network.org/data
- Aircraft database page points to the dataset:
  https://opensky-network.org/data/aircraft

This metadata is imported locally into a Room database and looked up by ICAO24.

---

## 2) Requirements (hard)
### 2.1 ICAO24 (identity) — MUST
- Parse `icao24` from OpenSky state vector index `0`
- Normalize lowercase for storage/lookup (display uppercase optionally)
- Use `icao24` as MapLibre `Feature.id` (prevents flicker, enables stable updates)

### 2.2 Metadata enrichment — MUST (this is the missing functionality)
- Implement a local metadata store keyed by ICAO24:
  - Room entity/table
  - DAO query by list of ICAOs (IN query)
  - Repository API
- Implement WorkManager sync:
  - downloads `aircraftDatabase.csv`
  - stream-parse + batch insert into Room
  - exposes readiness + progress + last error
- Join metadata into ADS‑B UI models and show in details sheet

### 2.3 UI requirements (stop confusing “type”)
In details sheet show sections:

**Aircraft identification**
- Registration
- Typecode
- Model
- (Optional) ICAO aircraft description code

**Live state**
- ICAO24
- callsign
- altitude / speed / track / vertical rate

**Emitter category**
- category label + raw integer (e.g. `Rotorcraft (8)` or `No category info (1)`)

If metadata DB is not ready:
- show “Metadata not available yet”
- show sync state (“Downloading…”, “Importing…”, “Last updated …”, “Error …”)

### 2.4 Runtime envelope (implemented as of 2026-02-10)
- Receive radius: 15 km around the active ADS-B center.
- Airborne gate: altitude must be >100 ft and speed must be >40 kt.
- Position source filter: FLARM-sourced rows (`position_source=3`) are ignored.
- Display cap: maximum 30 displayed aircraft.

Implementation note:
- Current details sheet labels still show `Type` and `Category`.
- A metadata UI pass should rename this to `Emitter category` to match this contract.

---

## 3) OpenSky /states/all parsing (indexes)
Use constants:

```kotlin
private const val IDX_ICAO24 = 0
private const val IDX_CALLSIGN = 1
private const val IDX_LON = 5
private const val IDX_LAT = 6
private const val IDX_BARO_ALT_M = 7
private const val IDX_ON_GROUND = 8
private const val IDX_VELOCITY_MPS = 9
private const val IDX_TRUE_TRACK_DEG = 10
private const val IDX_VERT_RATE_MPS = 11
private const val IDX_GEO_ALT_M = 13
private const val IDX_POSITION_SOURCE = 16
private const val IDX_CATEGORY = 17 // only when extended=1
```

Parse ICAO24 as string (do not convert to int):
- store lowercase
- trim whitespace

---

## 4) Implementation docs for Codex
Codex must follow these repo docs (already provided elsewhere):
- ARCHITECTURE.md
- CODING_RULES.md

Canonical implementation contract:
- `ADSB_ICAO_METADATA_IMPLEMENTATION_PLAN.md`

Supporting reference docs:
- `ADSB_AircraftMetadata.md`
- `ADSB_CategoryIconMapping.md`
- `ADSB_IconsAndTap_Plan.md`

Legacy docs (superseded; keep only for historical context):
- `ADSB_AUTONOMOUS_ENGINEER_IMPLEMENTATION_PLAN.md`
- `ADSB_FileTouchList.md`
- `ADSB_md_patch_aircraft_metadata_required.md`

---

## 5) Acceptance tests (high level)
1) Tap any aircraft: ICAO24 is always shown.
2) After metadata sync:
   - at least some aircraft show registration/typecode/model (when present in DB).
3) Metadata sync runs:
   - when ADS‑B enabled first time
   - does not block UI thread
   - survives app restarts
4) Markers do not flicker and update smoothly.
