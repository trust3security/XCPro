# ADSB_AircraftMetadata.md — Identify aircraft using ICAO24 (registration, model, typecode)
**This is the missing piece for “what aircraft is that?”**

OpenSky state vectors provide ICAO24 + callsign + kinematics, but NOT full airframe metadata.  
To show registration/type/model like the OpenSky map UI, XCPro must **enrich** using an aircraft metadata database keyed by ICAO24.

---

## 0) Data sources (authoritative)

### 0.1 OpenSky aircraft database dataset (CSV)
OpenSky provides an aircraft database and states:
- “The database as a whole can be downloaded in .csv format.”  
Source: OpenSky Network Data page.

OpenSky also states:
- “The aircraft database is unlicensed and does not fall under our terms of use… offered ‘as is’.”  
Source: OpenSky Aircraft Database page.

Dataset example:
- `https://opensky-network.org/datasets/metadata/aircraftDatabase.csv`

A typical header includes fields like:
`icao24, registration, manufacturericao, manufacturername, model, typecode, serialnumber, linenumber, icaoaircrafttype, operator, operatorcallsign, ...`  
Source: dataset listing/snippet.

Sources:
- https://opensky-network.org/data (aircraft DB downloadable as CSV)
- https://opensky-network.org/data/aircraft (aircraft DB unlicensed, “as is”)
- https://opensky-network.org/datasets/metadata/aircraftDatabase.csv

---

## 1) What XCPro should show from metadata
When available, show:

- **registration** (tail number) — e.g. VH-DFV
- **typecode** — e.g. C208
- **model** — e.g. CESSNA 208 Caravan
- **manufacturername** — e.g. Cessna
- **operator** / **operatorcallsign** (if present)
- **icaoaircrafttype** (if present, sometimes values like L1T)

---

## 2) Architecture (no corners)

### 2.1 New components
Create:

1) `AircraftMetadataEntity` (Room)
2) `AircraftMetadataDao`
3) `AircraftMetadataRepository`
4) `AircraftDatabaseSyncWorker` (WorkManager)
5) `AircraftMetadataCache` (small in-memory LRU)

### 2.2 Data flow
1) ADS‑B poll produces `List<AdsbTarget>` (each has ICAO24).
2) Compute `icao24Set` (max 30).
3) Query metadata DB:
   - `SELECT ... WHERE icao24 IN (:icaoList)`
4) Join metadata onto UI model.
5) Tap details sheet uses ICAO24 to display metadata.

---

## 3) Database schema (Room)

### 3.1 Entity
Keep only fields XCPro needs:

```kotlin
@Entity(tableName = "adsb_aircraft_metadata")
data class AircraftMetadataEntity(
  @PrimaryKey val icao24: String,
  val registration: String?,
  val typecode: String?,
  val model: String?,
  val manufacturerName: String?,
  val operator: String?,
  val operatorCallsign: String?,
  val icaoAircraftType: String?,
  val updatedAtEpochMs: Long
)
```

Normalize:
- store `icao24` lowercase
- trim strings
- store empty strings as null

### 3.2 DAO
```kotlin
@Dao
interface AircraftMetadataDao {
  @Query("SELECT * FROM adsb_aircraft_metadata WHERE icao24 IN (:icao24s)")
  suspend fun getByIcao24s(icao24s: List<String>): List<AircraftMetadataEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertAll(rows: List<AircraftMetadataEntity>)
}
```

---

## 4) Sync / Download strategy

### 4.1 Trigger
- When user enables ADS‑B for the first time:
  - enqueue a one‑time WorkManager job to download + import aircraft database.
- Then:
  - refresh every 30–90 days (your call; OpenSky says updates are irregular).

### 4.2 Worker responsibilities
`AircraftDatabaseSyncWorker` does:

1) Download CSV from OpenSky dataset URL.
2) Stream-parse CSV (do not load all in memory).
3) Insert into Room in batches (e.g., 1k rows/transaction).
4) Store sync metadata in DataStore:
   - lastSuccessEpochMs
   - sourceUrl
   - optional file hash/etag if provided

### 4.3 CSV parsing (do not half-parse)
CSV is quoted and may contain commas in quoted fields.
Use a real CSV parser OR implement a correct quoted-field parser.

Recommended options:
- Apache Commons CSV (reliable)
- Kotlin CSV (if already in project)

If adding deps is not allowed, Codex must implement:
- quoted strings with escaped quotes
- comma separators inside quotes

### 4.4 Column mapping (future proof)
Do not hardcode column positions.
Read the header row and map name → index (case-insensitive).

Required columns:
- icao24
- registration
- manufacturername
- model
- typecode
- icaoaircrafttype
- operator
- operatorcallsign

If some are missing, import what exists.

---

## 5) Repository API (what other code calls)

```kotlin
data class AircraftMetadata(
  val icao24: String,
  val registration: String?,
  val typecode: String?,
  val model: String?,
  val manufacturerName: String?,
  val operator: String?,
  val operatorCallsign: String?,
  val icaoAircraftType: String?
)

interface AircraftMetadataRepository {
  suspend fun getMetadataFor(icao24s: List<String>): Map<String, AircraftMetadata>
  val isDatabaseReady: Flow<Boolean>
}
```

Repository behaviour:
- If DB not ready, return empty map (UI shows “Metadata not available”).
- Always normalize keys to lowercase.

---

## 6) UI contract (details sheet)
When user taps an aircraft:

Always show:
- ICAO24
- callsign (if present)
- altitude/speed/track/verticalRate
- raw category and mapped icon type (direct/inferred)

If metadata exists show:
- registration
- typecode
- model
- manufacturer
- operator/operatorcallsign
- icaoaircrafttype

---

## 7) Tests (must-have)

### 7.1 Unit tests
- `normalizeIcao24()` lowercases and trims.
- CSV parser handles:
  - quoted commas
  - empty fields
  - header order changes
- DAO query returns correct rows for list of ICAOs.

### 7.2 Integration test (happy path)
- import a small sample CSV (10 lines) into an in-memory Room DB
- request metadata for 3 ICAOs
- assert joined UI shows registration/typecode/model.

---

## 8) Why this is the correct fix
- ICAO24 is the identity key per OpenSky docs.
- `/states/all` does NOT include registration/type/model.
- OpenSky aircraft database dataset provides exactly that metadata keyed by ICAO24.
