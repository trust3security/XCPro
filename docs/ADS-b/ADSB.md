# ADSB.md — ADS‑B Internet Traffic in XCPro (OpenSky) — 20 km
**v3: uses ICAO24 to identify aircraft + optional metadata enrichment**

This doc is for an **autonomous Codex agent**. It must be implementable without user input.

---

## 0) The non-negotiables (read first)

### 0.1 ICAO24 is the identity key
OpenSky state vectors include **ICAO24**:
- In ADS‑B, each aircraft/transponder is identified by a unique **ICAO 24‑bit address** shown as a **6‑char hex string** (example `c0ffee`).  
  Source: OpenSky API docs.  
- In the `/states/all` response, `states[row][0]` is `icao24` and is described as a unique ICAO 24-bit transponder address in hex string form.  
  Source: OpenSky REST docs.

**XCPro MUST treat `icao24` as the stable primary key** for:
- marker identity (no flicker)
- tap → details
- metadata lookup (registration/model/type)

Sources:
- https://openskynetwork.github.io/opensky-api/ (ICAO 24-bit address as hex string)
- https://openskynetwork.github.io/opensky-api/rest.html (state vector index 0 = icao24)

### 0.2 Category is NOT model/type
OpenSky `category` is an ADS‑B emitter category bucket and is frequently 0/1 (no info).
Category is used for **icons**.
**Aircraft identification (registration/model/typecode)** requires **metadata enrichment** keyed by ICAO24.

See: `ADSB_AircraftMetadata.md`

### 0.3 OpenSky use constraints
OpenSky API is intended for research/non-commercial use; commercial usage requires permission.  
Source: OpenSky API docs.

---

## 1) API request — MUST include extended=1
You only get `category` when you request:

`GET https://opensky-network.org/api/states/all?lamin=...&lomin=...&lamax=...&lomax=...&extended=1`

OpenSky REST docs explicitly document:
- `/states/all`
- request param `icao24` filter
- request param `extended=1` to request category
- response index 0 = `icao24`
- response includes `true_track` and `vertical_rate`

Source:
https://openskynetwork.github.io/opensky-api/rest.html

---

## 2) State vector indexes (DO NOT GUESS)
Use constants exactly as per OpenSky docs:

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
private const val IDX_CATEGORY = 17 // only present when extended=1
```

---

## 3) Robust parsing (fixes Kotlin JSON numeric pitfalls)

OpenSky values often arrive as `Double` even for integer-looking fields.

```kotlin
private fun Any?.asIntOrNull(): Int? = (this as? Number)?.toInt()
private fun Any?.asDoubleOrNull(): Double? = (this as? Number)?.toDouble()
private fun Any?.asBoolOrNull(): Boolean? = this as? Boolean
private fun Any?.asStringOrNull(): String? = this as? String

private fun String.normalizeIcao24(): String = trim().lowercase()
private fun String.normalizeCallsign(): String = trim()
```

Parse:

```kotlin
val icao24 = (row[IDX_ICAO24] as String).normalizeIcao24()
val callsign = row.getOrNull(IDX_CALLSIGN).asStringOrNull()?.normalizeCallsign()?.ifBlank { null }

val category: Int? = if (row.size > IDX_CATEGORY) row[IDX_CATEGORY].asIntOrNull() else null
val onGround: Boolean? = row.getOrNull(IDX_ON_GROUND).asBoolOrNull()
val speedMps: Double? = row.getOrNull(IDX_VELOCITY_MPS).asDoubleOrNull()
val trackDeg: Double? = row.getOrNull(IDX_TRUE_TRACK_DEG).asDoubleOrNull()
val vrMps: Double? = row.getOrNull(IDX_VERT_RATE_MPS).asDoubleOrNull()
val geoAltM: Double? = row.getOrNull(IDX_GEO_ALT_M).asDoubleOrNull()
val baroAltM: Double? = row.getOrNull(IDX_BARO_ALT_M).asDoubleOrNull()
```

**Mandatory debug once per session:**
- `row.size` (17 vs 18)
- `icao24`
- `raw category`
- `trackDeg`, `vrMps`
This catches 99% of “type/category missing” issues.

---

## 4) Domain model MUST include ICAO24

```kotlin
data class AdsbTarget(
  val icao24: String,        // REQUIRED primary key
  val callsign: String?,
  val lat: Double,
  val lon: Double,
  val geoAltM: Double?,
  val baroAltM: Double?,
  val speedMps: Double?,
  val trackDeg: Double?,
  val verticalRateMps: Double?,
  val onGround: Boolean?,
  val category: Int?          // only with extended=1
)
```

---

## 5) Identification in UI (details sheet)
Tap an ADS‑B target → show:

### 5.1 Always show (live state)
- ICAO24 (hex string, uppercase for display if you like)
- Callsign (if present)
- Altitude (geo or baro)
- Speed
- Track
- Vertical rate (climb/descent) — OpenSky provides `vertical_rate` in m/s.

### 5.2 Show when available (metadata enrichment)
From local metadata DB keyed by ICAO24:
- registration (tail number)
- typecode (e.g. C208)
- model (e.g. CESSNA 208 Caravan)
- manufacturer name
- operator / operatorcallsign (if present)
- icaoaircrafttype (if present)

Implementation details are in `ADSB_AircraftMetadata.md`.

---

## 6) MapLibre identity (no flicker)
Your MapLibre GeoJSON features MUST use ICAO24 as the stable id:

```kotlin
val f = Feature.fromGeometry(Point.fromLngLat(lon, lat))
f.id(icao24) // critical
f.addStringProperty("icao24", icao24)
```

This ensures updates are applied to the same feature without destroying/recreating symbols.

---

## 7) Icons + category logic
Category mapping stays as documented in `ADSB_CategoryIconMapping.md`.
Even with correct implementation, many aircraft will report category 0/1 and you must handle that:
- show raw category in UI
- optionally infer a display bucket (see Category doc)
- metadata enrichment may still provide typecode/model even if category is missing

---

## 8) Acceptance criteria
1) Every ADS‑B target displays ICAO24 in the details sheet.
2) The same ICAO24 marker updates smoothly (no flicker).
3) If the metadata DB is ready and a record exists, the details sheet shows registration + typecode + model.
4) If no record exists, details sheet shows “Metadata not available” (but still shows ICAO24).
