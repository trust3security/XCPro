# ADSB_AircraftMetadata.md — ICAO24 → registration/typecode/model (Room + WorkManager + CSV import)
**v5 AUTONOMOUS: required to display real aircraft identification**

This doc describes exactly how to implement the missing “ICAO gives aircraft type/model” feature.

---

## 0) Why this is required
OpenSky `/states/all` provides live state vectors (icao24, callsign, position, speed, altitude, track, vertical rate).  
It does NOT provide registration/typecode/model.

Therefore: to show registration/typecode/model like OpenSky’s map, XCPro must enrich using a metadata dataset keyed by `icao24`.

OpenSky API reference:
https://openskynetwork.github.io/opensky-api/rest.html

---

## 1) Metadata source
### 1.1 Dataset URL
Use OpenSky aircraft database CSV:
https://opensky-network.org/datasets/metadata/aircraftDatabase.csv

OpenSky data portal describes the aircraft database and that it is downloadable as CSV:
https://opensky-network.org/data

OpenSky aircraft database page points to the dataset:
https://opensky-network.org/data/aircraft

---

## 2) Data model (Room)
### 2.1 Entity
Store only what XCPro displays:

```kotlin
@Entity(tableName = "adsb_aircraft_metadata")
data class AircraftMetadataEntity(
  @PrimaryKey val icao24: String, // normalized lowercase

  val registration: String?,
  val typecode: String?,
  val model: String?,
  val manufacturerName: String?,
  val operator: String?,
  val operatorCallsign: String?,
  val icaoAircraftType: String?, // optional: ICAO Doc 8643 3-symbol description

  val updatedAtEpochMs: Long
)
```

Normalization:
- `icao24` = trim().lowercase()
- all strings: trim(), empty -> null

### 2.2 DAO (single IN query)
```kotlin
@Dao
interface AircraftMetadataDao {
  @Query("SELECT * FROM adsb_aircraft_metadata WHERE icao24 IN (:icao24s)")
  suspend fun getByIcao24s(icao24s: List<String>): List<AircraftMetadataEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertAll(rows: List<AircraftMetadataEntity>)
}
```

### 2.3 Database integration
If the app already has a Room database, add this entity + dao into it.
Else create a dedicated RoomDatabase for this table.

---

## 3) Repository contract (join-friendly)
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

data class MetadataSyncState(
  val ready: Boolean,
  val running: Boolean,
  val progressPct: Int?, // 0..100 if known
  val lastSuccessEpochMs: Long?,
  val lastError: String?
)

interface AircraftMetadataRepository {
  val syncState: Flow<MetadataSyncState>
  suspend fun ensureSyncScheduled()
  suspend fun getMetadataFor(icao24s: List<String>): Map<String, AircraftMetadata>
}
```

Rules:
- If not ready, `getMetadataFor` returns empty map.
- Never run one query per aircraft. Always query with `IN (:icao24s)`.

---

## 4) WorkManager sync (download + import)
### 4.1 Trigger
When user enables ADS‑B:
- call `repo.ensureSyncScheduled()`
- if DB is not ready, enqueue a one-time worker immediately.

Periodic refresh:
- re-run sync every 30–90 days (OpenSky says updates are irregular; snapshots exist).

### 4.2 Worker must be streaming (no OOM)
Steps:
1) Download CSV to temp file in cache.
2) Stream-parse CSV line-by-line.
3) Map header -> column index (case-insensitive).
4) Batch inserts into Room (e.g. 1000 rows per transaction).
5) Update `syncState` (running/progress/ready/error).

### 4.3 CSV parsing requirements (no corners)
The file is quoted CSV. Do NOT do `split(",")`.
Use a real CSV parser if possible.
If dependencies are not allowed, implement:
- quoted-field parsing
- commas inside quotes
- escaped quotes
- empty fields

Header-driven mapping is mandatory.

---

## 5) Joining metadata into UI
Pipeline on each ADS‑B refresh:
1) build targets list (max 30)
2) `icaoList = targets.map { it.icao24 }`
3) `metadata = repo.getMetadataFor(icaoList)`
4) attach `metadata[icao24]` into the UI model
5) Details sheet reads the attached metadata

---

## 6) Details sheet UI contract (MUST)
Show:

**Aircraft identification**
- Registration
- Typecode
- Model
- Optional: ICAO aircraft type description code

**Live state**
- ICAO24
- callsign
- altitude, speed, track, vertical rate

**Emitter category**
- label + raw integer

If metadata not ready:
- show sync status (downloading/importing/error)
- show “Metadata not available yet”

---

## 7) Tests
- Unit: CSV parser handles quoted commas and escaped quotes.
- Unit: header mapping works even if column order changes.
- Integration: import a tiny sample CSV into in-memory Room and confirm lookup by ICAO24 returns expected fields.
