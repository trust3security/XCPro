# SI Boundary Contracts

Date: 2026-02-22
Status: Updated after Re-pass #8

## Contract Rule
Internal logic must use SI. Any non-SI representation is allowed only at explicit boundaries and must convert immediately.

## Rationale (XCPro)
- FAI task geometry is unit-sensitive (start/finish/turnpoint boundary logic).
- Common competition constraints (for example 500 m cylinders, >= 3 km finish) amplify conversion mistakes.
- Sensor and flight-control paths (GPS, baro altitude, accelerometer, vario, STF, wind) are SI-first.
- Mixed-unit internals create hard-to-diagnose defects that can look almost correct while failing scoring and safety logic.

## Current Boundary Inventory

### Common Units Layer
- `dfcards-library/.../Measurements.kt`: SI value classes (good).
- `dfcards-library/.../UnitsPreferences.kt`: UI conversion boundary (good).
- `core/common/.../UnitsRepository.kt`: preference persistence only (good).
- Re-pass #8 scope note: some map/task UI paths still bypass this boundary by hard-coding distance labels.

### Flight/Sensors
- Internal variables and outputs are SI (`*Ms`, `*Meters`, `qnhHpa`) (good).
- MacCready and STF operate in m/s internally (good).

### ADS-B
- API/query radius enters as km for bbox/filter.
- Internal target distance and filters use meters.
- Boundary conversion exists and is mostly explicit.

### OGN
- Subscription/filter boundaries use km.
- Internal target distance exposed as meters.
- Boundary conversion exists and is mostly explicit.

### Replay
- IGC IAS/TAS extension values ingested as km/h.
- Converted to m/s in emitter before fusion pipeline.
- Re-pass #6 defect: `ReplayRuntimeInterpolator` assigns `MovementSnapshot.distanceMeters` from `speedMs` (m/s), violating the internal meters contract used by `ReplayHeadingResolver`.

### Task/AAT/Racing
- Multiple internal contracts still in km/km/h.
- AAT contains active km-vs-meter mismatch bugs in optimizer/validator/area logic.
- Re-pass #7 scope update: `AATTaskQuickValidationEngine.validateFinish` also compares km distance output to meter thresholds.
- Re-pass #8 scope update: task UI distance output paths still hard-code `km` labels instead of using `UnitsFormatter` with selected distance unit.
- Domain engines partially convert to SI at boundaries, but legacy manager paths remain mixed.

### Glider Polar Domain
- Polar source models and limits are km/h-based (`PolarPoint.kmh`, `SpeedLimits.*Kmh`).
- Runtime sink provider takes m/s but repeatedly converts to km/h internally.
- This is a non-SI internal contract unless treated as strict data-source boundary.

## Target Contract (Post-Migration)
- Domain and manager internals: SI-only.
- UI and import/export adapters: explicit conversion boundaries.
- Method names must encode units in parameter/return names.

## Allowed Exceptions
- Third-party protocol fields and externally defined file formats.
- User-facing formatting APIs.
- Any exception must be documented with owner and expiry.
