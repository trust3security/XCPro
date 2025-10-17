# Units Standardization Plan

Last updated: 2025-10-12  
Owner: XCPro avionics team  
Status: Draft execution roadmap (glider polar conversions deferred)

---

## Goals

- Prevent unit-conversion defects (e.g., misinterpreting m/s as knots).
- Keep *all* internal calculations in SI units (meters, m/s, m/s^2, hPa, deg C).
- Offer consistent, user-configurable display units via Settings > General > Units.
- Centralize conversion logic, formatters, and persistence for easier maintenance.

---

## Principles

1. **SI inside** - Core data classes, helpers, and filters pass typed SI values only. Conversions occur at sensor ingress, persistence egress, or UI formatting.
2. **Single source of truth** - `UnitsRepository` exposes `UnitsPreferences` as a flow; anything rendering to the user consumes this stream.
3. **Named constants** - Replace inline factors (1.852, 3.28084) with documented helpers to avoid silent drift.
4. **Explicit naming** - Field names include unit hints (e.g., `speedMs`, `altitudeM`). Logs and analytics append `_ms`, `_ftmin`, etc.
5. **Test every conversion** - Introduce regression tests that guard against factor regressions (round-trip and threshold cases).
6. **Non-breaking migration** - Existing SI consumers continue working while UI/telemetry adopt formatters. No glider polar changes in this phase.

---

## Scope Inventory

### 1. Sensor & Model Layer
- `SensorData.kt`  
  - `GPSData.speed`  **m/s** (ground speed), `altitude`  meters, `bearing`  degrees, `accuracy`  meters.  
  - `BaroData.pressureHPa`  hectopascals.  
  - `AccelData.verticalAcceleration`  m/s.
- `FlightDataModels.kt`  
  - `WindData.speed`  **m/s**, `VerticalSpeedPoint.verticalSpeed`  m/s, `LocationWithTime.groundSpeed`  m/s.
- `CompleteFlightData` (`SensorData.kt`)  
  - All stored in SI: `verticalSpeed` (m/s), `agl` (m), `windSpeed` (m/s), `netto` (m/s), etc.
- `RealTimeFlightData` (`dfcards-library/.../FlightDataSources.kt`)  
  - Mirrors SI values; `groundSpeed` now remains in m/s for downstream formatting.

### 2. Calculation Layer
- `FlightDataCalculator.kt`  
  - 50 Hz loop operates on meters/m/s; TE compensation uses m/s^2.  
  - `previousGPSSpeed` stored in m/s (first update currently zero-seeded).
- `FlightCalculationHelpers.kt`  
  - All helper math in SI.  
  - `calculateWindSpeed` returns wind in m/s.  
  - `calculateTotalEnergy` expects m/s inputs.  
  - `calculateSinkRate` assumes input m/s but converts using **1.852** (knots->km/h)  bug to fix (should be 3.6).  
  - `calculateCurrentLD` uses `Location.distanceTo` (meters) / altitude delta (meters) -> dimensionless.
- Library calculators (`dfcards-library/AirspeedCalculator.kt`) accept/return **knots**; adapters must convert from SI.

### 3. Vario & Audio
- `app/.../vario/*` and `dfcards-library/.../Modern3StateKalmanFilter.kt`  all state values and thresholds in m/s or meters.  
- `ComplementaryVarioFilter` assumes baro altitude meters, accel m/s^2.  
- `VarioAudioEngine` + `VarioFrequencyMapper` thresholds expressed in **m/s** (lift/sink), duty cycles dimensionless.  
- `VarioAudioSettings` default thresholds already SI.

### 4. UI Adapters & Screens
- `MapScreen.kt` adapts `CompleteFlightData` to `RealTimeFlightData`; converts ground speed to **knots** for legacy widgets.  
- Map orientation stack (`MapOrientationManager`, `MapOrientationPreferences`) stores speed thresholds in **knots** (`min_speed_threshold_kt`).  
- Nav drawer / dashboards share `RealTimeFlightData`; formatting currently ad-hoc (numbers often display raw SI with appended labels).  
- Vario audio settings screen shows thresholds in m/s; will need formatter hook once preferences exist.

### 5. Persistence & Telemetry
- SharedPrefs / DataStore keys today mostly implicit SI (e.g., TE thresholds).  
- Debug logs in `FlightDataCalculator` log vertical speed without unit suffix; orientation logs reference knots.

These findings establish the baselines for refactoring and highlight the known SI vs knots exception (`RealTimeFlightData.groundSpeed` feeding UI/Map orientation). Further review is needed for downstream analytics/export once the units module lands.

---

## Execution Steps

1. **Audit current usage**  
   - Walk through each module above, annotating expected units.  
   - Log findings in a scratch doc or this file's appendix.  
   - [done] Initial pass complete  see *Scope Inventory* section.

2. **Create units module**  
   - New package `com.example.xcpro.common.units`.  
   - Value classes: `SpeedMs`, `AltitudeM`, `VerticalSpeedMs`, etc.  
   - `UnitsConverter`: static helpers (SI <-> user units).  
   - `UnitsFormatter`: produces display strings given `UnitsPreferences`.  
   - Add unit tests validating constants and round-trips. [done]  
   - [done] Value classes, converter, formatter, and initial tests committed.

3. **Units preferences infrastructure**  
   - Define `UnitsPreferences` data class (altitude, vertical speed, speed, distance, pressure, temperature). [done]  
   - Persist via DataStore (`UnitsPreferencesSerializer` or `PreferencesKeys`). [done]  
   - `UnitsRepository` exposes a `StateFlow<UnitsPreferences>` and update methods. [done]


4. **Settings UI**  
   - Add Units option in `SettingsScreen` (General nav drawer). [done]   
   - Implement `UnitsSettingsScreen` with segmented buttons/dropdowns for each measurement type. [done]   
   - Bind to repository flow; persist on selection (optimistic updates). [done]   
   - Provide contextual examples (e.g., sample climb rate preview) to reassure users. [done] 

5. **Refactor calculations and display**  
   - Replace raw doubles with value classes where practical (starting with helpers).  
   - Correct existing bugs (e.g., `calculateSinkRate` using 3.6 for km/h).   
   - Ensure TE/Netto keep SI internally; only format for UI.  
   - Route UI/telemetry through `UnitsFormatter` to honor preferences.
   - Current status: Map dashboard cards now format via `UnitsFormatter`; map overlays and orientation strings still pending.

6. **Testing & verification**  
   - Unit tests: conversion helpers, TE/Netto calculations at edge speeds, DataStore serialization.  
   - UI tests/previews confirming toggles update displayed units.  
   - Manual test matrix: switch units, restart app, verify persistence, monitor audio feedback thresholds.

7. **Documentation & rollout**  
   - Add `docs/engineering/units.md` with policy summary, conversion table, and usage examples.  
   - Update onboarding guides to mention SI-only rule and units module.  
   - Capture follow-up tasks (e.g., glider polar conversions, analytics updates) in backlog.

---

## Suggested Data Model

```kotlin
data class UnitsPreferences(
    val altitude: AltitudeUnit = AltitudeUnit.METERS,
    val verticalSpeed: VerticalSpeedUnit = VerticalSpeedUnit.METERS_PER_SECOND,
    val speed: SpeedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
    val distance: DistanceUnit = DistanceUnit.KILOMETERS,
    val pressure: PressureUnit = PressureUnit.HECTOPASCAL,
    val temperature: TemperatureUnit = TemperatureUnit.CELSIUS
)
```

Enum values include display labels, conversion factors, and optional abbreviations for UI (`AltitudeUnit.FEET.abbreviation = "ft"`).

---

## Open Questions / Future Work

- Should analytics/exported flight logs honor user units or remain SI? (Recommendation: SI + metadata flag.)
- How to expose diagnostics collector stats in desired units? (Likely separate dev-only setting.)
- Coordinate with upcoming glider polar work to reuse the same `UnitsRepository` once scheduled.

---

## Appendix: Conversion Constants (reference)

- `M_PER_FT = 0.3048` (exact)
- `KMH_PER_MS = 3.6`
- `KT_PER_MS = 1.943844`
- `FTMIN_PER_MS = 196.8504`
- `INHG_PER_HPA = 0.0295299830714`
- `F_PER_C = (C * 9/5) + 32`

Keep these constants in `UnitsConverter`; do not inline them elsewhere.

