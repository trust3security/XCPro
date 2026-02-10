# ADSB_AUTONOMOUS_ENGINEER_IMPLEMENTATION_PLAN.md
**Full autonomous plan: implement ICAO24-based aircraft identification in XCPro**

> Status: Superseded on 2026-02-10 by `ADSB_ICAO_METADATA_IMPLEMENTATION_PLAN.md`.
> Keep this file for historical context only.

This plan assumes ADS‑B markers already display and update and that you have:
- `icao24` stored on AdsbTarget
- category parsing in place
- a details sheet

Goal: Add real aircraft identification (registration/typecode/model) using a local metadata DB keyed by ICAO24.

References:
- Live state vector /states/all: https://openskynetwork.github.io/opensky-api/rest.html
- Aircraft database CSV: https://opensky-network.org/datasets/metadata/aircraftDatabase.csv
- Aircraft DB described as downloadable: https://opensky-network.org/data
- Aircraft DB page: https://opensky-network.org/data/aircraft

---

## Phase 0 — Repo scan (Codex does this first)
1) Locate existing DI (Hilt/Koin) and existing Room database usage.
2) Locate any existing WorkManager usage and patterns.
3) Locate existing “traffic overlays” architecture:
   - OGN implementation patterns (for stable IDs, map updates, details sheet)
4) Locate existing network stack (OkHttp/Retrofit).

Deliverable: a short note in code comments or internal doc describing:
- where to plug new DB + worker
- where to surface sync state to UI

---

## Phase 1 — Add metadata domain + data layers
### 1.1 Add domain model
Create `AircraftMetadata` domain object and `AircraftMetadataRepository` interface (see ADSB_AircraftMetadata.md).

### 1.2 Add Room entity + DAO
- Add `AircraftMetadataEntity`
- Add `AircraftMetadataDao`
- Add migration if you have an existing DB schema.

Implementation rules:
- key is `icao24` lowercase
- store only display fields
- provide IN-query for list of ICAOs

### 1.3 Add DataStore sync state
Implement DataStore keys:
- `metadata_ready: Boolean`
- `last_success_epoch_ms: Long`
- `last_error: String?`

Also store `current_worker_state` if you want more UI detail.

---

## Phase 2 — CSV importer (streaming, header-driven, correct)
Create `AircraftDatabaseCsvImporter`:
- Input: `File` or `InputStream`
- Output: batches of `AircraftMetadataEntity`
- Requirements:
  - read header row, map columnName -> index
  - parse quoted CSV (commas inside quotes, escaped quotes)
  - normalize strings (trim, empty->null)
  - skip malformed lines (log and continue)

Batching:
- collect 1000 rows
- upsert in one transaction
- clear list, continue

---

## Phase 3 — WorkManager sync worker
Create `AircraftDatabaseSyncWorker : CoroutineWorker`

Worker steps:
1) Set syncState.running=true (DataStore)
2) Download dataset:
   - GET https://opensky-network.org/datasets/metadata/aircraftDatabase.csv
   - stream to temp file in cache directory
   - show progress if Content-Length available
3) Import:
   - open file
   - call importer to upsert to DB
   - update progress by rows if possible
4) On success:
   - metadata_ready=true
   - last_success_epoch_ms=now
   - last_error=null
5) On failure:
   - metadata_ready stays false (unless previously true)
   - last_error=exception message

Constraints:
- Network required
- No main-thread work
- resilient to app restart

Retry:
- exponential backoff
- limit retries to avoid battery drain

---

## Phase 4 — Repository join into ADS‑B UI
Modify the ADS‑B refresh pipeline:

1) After fetching/parsing OpenSky targets:
   - `val icaoList = targets.take(30).map { it.icao24 }`
2) Query metadata:
   - `val meta = repo.getMetadataFor(icaoList)`
3) Build UI models:
   - attach meta fields into UI model
4) Ensure this join runs off-main thread.

Optimization:
- Add a small LRU cache in repository keyed by icao24.
- Still do DB IN-query per refresh, but merge cache first to reduce DB.

---

## Phase 5 — Details sheet changes (make it obvious)
Update details sheet UI:

### 5.1 Aircraft identification block
If meta present:
- Registration
- Typecode
- Model
- Manufacturer
- Operator

Else:
- “Metadata not available yet”
- Show sync status line (downloading/importing/error/last updated)

### 5.2 Live state block
Always show:
- ICAO24 (display uppercase)
- callsign
- altitude/speed/track/verticalRate

### 5.3 Emitter category block
Show:
- category label + raw int

Rename any “Type” field that currently shows category to “Emitter category”.

---

## Phase 6 — Tests
### Unit tests
- CSV parser: quoted fields, commas, escaped quotes
- header mapping: reorder columns
- normalize: lowercase icao24, trim strings

### Integration tests
- In-memory Room: import 10-line CSV sample
- query 3 ICAOs: ensure registration/typecode/model returned

### UI sanity test
- With metadata ready: sheet shows registration/typecode/model
- Without metadata: sheet shows “Metadata not available yet”

---

## Phase 7 — Acceptance
1) ICAO24 always displayed.
2) At least some aircraft show registration/typecode/model after sync.
3) No flicker on map (Feature.id = icao24).
4) No ANRs (import runs in worker, streaming).
5) Sync survives process death.
