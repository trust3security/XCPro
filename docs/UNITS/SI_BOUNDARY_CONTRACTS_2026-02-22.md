# SI Boundary Contracts

Date: 2026-02-22
Status: Updated after Re-pass #7

## Contract Rule
Internal logic must use SI. Any non-SI representation is allowed only at explicit boundaries and must convert immediately.

## Current Boundary Inventory

### Common Units Layer
- `dfcards-library/.../Measurements.kt`: SI value classes (good).
- `dfcards-library/.../UnitsPreferences.kt`: UI conversion boundary (good).
- `core/common/.../UnitsRepository.kt`: preference persistence only (good).

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
