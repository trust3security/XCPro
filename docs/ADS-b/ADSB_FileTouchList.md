# ADSB_FileTouchList.md
**File-by-file checklist Codex must follow (no missed steps)**

> Status: Superseded on 2026-02-10 by `ADSB_ICAO_METADATA_IMPLEMENTATION_PLAN.md`.
> Keep this file for historical context only.

This is intentionally explicit. Codex must edit/add exactly these areas.

---

## A) Existing ADS‑B files (must update)
### 1) AdsbTrafficModels.kt
- Ensure `AdsbTarget` contains:
  - `icao24: String`
  - `category: Int?`
  - `verticalRateMps: Double?`
- Add metadata fields on UI model (or attach a `AircraftMetadata?` object):
  - registration
  - typecode
  - model
  - manufacturerName
  - operator / operatorCallsign
  - icaoAircraftType

### 2) OpenSkyProviderClient.kt
- Ensure request includes `extended=1`.
- Ensure bounding box is correct.
- Ensure timeouts/retries are sane.

### 3) OpenSkyStateVectorMapper.kt
- Parse `icao24` from index 0 as String.
- Normalize lowercase for storage.
- Parse category index 17 only if row.size > 17.
- Parse vertical_rate index 11.

### 4) ADS‑B overlay GeoJSON builder
- MUST set `Feature.id(icao24)`.
- MUST set property `icao24`.
- MUST set `icon_id` and `track_deg`.

### 5) AdsbMarkerDetailsSheet.kt
- Rename “Type” (category label) to “Emitter category”.
- Add “Aircraft identification” section:
  - registration/typecode/model (from metadata)
- Add sync status line when metadata missing.

### 6) ADS‑B refresh pipeline (ViewModel / reducer)
- After state vector parsing, query metadata repository by list of ICAO24s.
- Join metadata into UI state.

---

## B) New files to add (metadata system)
Create a new package (suggested) `...adsb.metadata`

### 1) Room layer
- AircraftMetadataEntity.kt
- AircraftMetadataDao.kt
- Database integration (existing DB migration or new DB class)

### 2) Repository layer
- AircraftMetadata.kt (domain)
- AircraftMetadataRepository.kt (interface)
- DefaultAircraftMetadataRepository.kt (impl)
- MetadataSyncState.kt

### 3) Sync / importer
- AircraftDatabaseCsvImporter.kt
- AircraftDatabaseSyncWorker.kt (WorkManager)
- DataStore keys file (MetadataStore.kt)

### 4) DI wiring
- Add bindings for repository, dao, database, worker factory if needed.

---

## C) Tests
- CSV parser unit tests (quoted fields)
- Room import + lookup integration test
- Mapping join test (targets + meta)

---

## D) Acceptance check (manual)
- Turn on ADS‑B → metadata sync starts.
- After sync completes, tap a known aircraft → registration/typecode/model visible.
- Emitter category shown separately.
