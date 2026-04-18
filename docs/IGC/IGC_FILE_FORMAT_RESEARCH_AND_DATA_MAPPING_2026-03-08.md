# IGC_FILE_FORMAT_RESEARCH_AND_DATA_MAPPING_2026-03-08.md

## Purpose

Document authoritative IGC file-format research and map those requirements to XCPro data sources,
so implementation can produce downloadable post-flight IGC files with production-grade behavior.

## Authoritative Sources

1. FAI IGC Technical Specification (Dec 2024 with AL9)
   - https://www.fai.org/sites/default/files/2025-04/igc_specification_dec_2024_with_al9.pdf
2. FAI IGC Approved Flight Recorders technical specification page
   - https://www.fai.org/documentgroup/igc-approved-flight-recorders-technical-specification
3. FAI CIVL Section 7H (2025) - Integrity and Validation of GNSS Flight Records
   - https://www.fai.org/sites/default/files/civl/documents/s7h.pdf

## Research Summary: What Is In An IGC File

IGC is a line-record format. The first character of each line identifies the record type.

Core records from FAI Appendix A:

- `A` record: recorder/manufacturer and recorder identity.
- `H` record: header metadata (date, pilot/glider metadata, recorder and sensor metadata).
- `I`/`J` records: extension layout definitions for extra fields in `B`/`K` records.
- `B` record: time-stamped fixes (UTC `HHMMSS`, lat/lon, fix validity, pressure altitude, GNSS altitude).
- `C` record: task/declaration data (optional for basic logging, required for declared tasks).
- `E` record: event markers.
- `F` record: satellite constellation information (optional but useful for validation).
- `K` record: extension data for time-stamped records defined by `J` (less commonly used in phone loggers).
- `L` record: free-text comments.
- `G` record: security signature block for integrity protection in approved recorder workflows.

From CIVL Section 7H (2025):

- Devices must produce one complete flight file per flight and allow download post-flight.
- For non-IGC-approved sources, accepted records are constrained and validity checks focus on
  file-level (`A/H/I`) and fix-level (`B`) correctness.

## Minimum Production Record Set For XCPro

`MVP-safe set` (non-IGC-approved smartphone logger):

- Mandatory in XCPro v1: `A`, `HFDTE`, `B`
- Strongly recommended in XCPro v1: `I` (if extensions used), `E`, `L`
- Feature-gated in XCPro v2: `C` declaration export, `F` satellites
- Not available without approved security stack: `G` signed-record proof

## XCPro Current Data Mapping

| IGC content | XCPro source | Current status | Gap |
|---|---|---|---|
| UTC fix time (`B`) | `GPSData.timestamp` from `SensorRegistry`/`UnifiedSensorManager` | Available | Need recorder-side UTC formatter and rollover handling tests |
| Lat/lon (`B`) | `GPSData.position` | Available | Need strict bounds + encoding formatter |
| GNSS altitude (`B`) | `GPSData.altitude` | Available | Need missing-fix policy |
| Pressure altitude (`B`) | `CompleteFlightData.pressureAltitude` or baro-derived fallback | Available | Need explicit policy if baro unavailable |
| Fix validity flag (`B`) | GPS accuracy + validity policy | Partial | Need domain rule for `A`/`V` decision |
| IAS/TAS extension (`I` + `B`) | `CompleteFlightData.indicatedAirspeed` / `trueAirspeed` | Available | Need extension-field formatter and unit conversion contract |
| Flight date (`HFDTE`) | injected `Clock` + session start UTC | Available | Need date rollover tests |
| Pilot/glider headers (`H`) | profile data (`feature/profile`) | Partial | Need canonical mapping and required-field policy |
| Recorder metadata headers (`H`) | `BuildConfig`, app version, device identity abstraction | Partial | Need stable recorder identity policy |
| Task declaration (`C`) | task domain (`TaskRepository`, racing task models) | Partial | Need declaration snapshot timing + schema mapper |
| Events (`E`) | flight/task events (`FlightStateRepository`, racing navigation events) | Partial | Need event taxonomy and dedupe policy |
| Satellite records (`F`) | no SSOT satellite-count stream currently exposed | Missing | Add optional GNSS constellation adapter |
| Security signature (`G`) | no IGC signing stack | Missing | Needs external signing strategy; out of MVP |

## Existing XCPro Components To Reuse

Replay and parser foundations:

- `feature/map/src/main/java/com/trust3/xcpro/replay/IgcParser.kt`
- `feature/map/src/main/java/com/trust3/xcpro/replay/IgcReplayController.kt`
- `feature/map/src/main/java/com/trust3/xcpro/replay/ReplaySampleEmitter.kt`

Sensor/flight SSOT and lifecycle:

- `feature/map/src/main/java/com/trust3/xcpro/sensors/SensorData.kt`
- `feature/map/src/main/java/com/trust3/xcpro/flightdata/FlightDataRepository.kt`
- `feature/map/src/main/java/com/trust3/xcpro/sensors/FlightStateRepository.kt`
- `app/src/main/java/com/trust3/xcpro/service/VarioForegroundService.kt`

Download/export patterns:

- `feature/map/src/main/java/com/trust3/xcpro/tasks/TaskFilesRepository.kt`
- `feature/profile/src/main/java/com/trust3/xcpro/profiles/ProfileBackupSink.kt`

## Key Compliance and Product Decisions

1. XCPro should explicitly label generated files as non-IGC-approved source output unless
   approved security and recorder requirements are satisfied.
2. Use injected clock/time abstractions in recorder domain logic; no direct wall-time calls.
3. Keep one flight per file and finalize/publish file only after session close.
4. Treat `G` signature as an optional phase with a separate security/integration contract.
5. Keep all internal calculations SI; convert only at IGC adapter boundaries.

## Gaps That Must Be Closed For Production Grade

- Canonical recorder state machine (arm/start/stop/finalize/recover).
- Deterministic line formatter/writer with strict spec conformance tests.
- Durable in-progress session recovery after process death.
- Download index/query/share UX for post-flight retrieval.
- Validation suite (round-trip parse checks, malformed-line guards, large-flight performance).
- Optional: signed integrity layer (`G`) and compliance mode separation.

## Output Contract For End Users

After every completed flight:

1. XCPro stores exactly one finalized `.IGC` file in Downloads under an XCPro-managed path.
2. User can list, open, and share/download the file from inside the app.
3. File is parseable by XCPro `IgcParser` and external IGC viewers.
4. App clearly marks whether file is non-approved trace output or approved/signature-backed output.
