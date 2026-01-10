# TC 30s (Thermal Climb 30s) - End-to-End Flow and Display Notes

This document traces the "TC 30s" value from sensors to UI and documents why the card can appear blank. It is intended as a durable map for future debugging.

---

## What TC 30s is in this codebase

- Definition: A rolling 30-second average of the brutto vario (TE/pressure/GPS depending on availability). It is not the "current thermal average" (TC AVG) or "total thermal average" (T AVG). Those come from a different tracker.
- Primary surface: Card id `thermal_avg` (title "TC 30s").

---

## Sensor -> Domain pipeline

### 1) Sensors and fusion

- Sensors: GPS + barometer + IMU (if available) are fused in `FlightDataCalculatorEngine`.
  - Baro/IMU loop: `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngineLoops.kt`
  - GPS loop: same file.

- Vario result (brutto source):
  - `updateVarioFilter()` runs `AdvancedBarometricFilter.processReading()` and produces a `ModernVarioResult`.
  - `FlightDataEmitter.emit()` passes that to the domain use case.

### 2) Domain calculation (TC 30s is computed here)

- Entry point: `CalculateFlightMetricsUseCase.execute()`
  - File: `feature/map/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt`

- Brutto vario selection:
  - `SensorFrontEnd.buildSnapshot()` picks TE -> Pressure -> Baro -> GPS in that order.
  - File: `feature/map/src/main/java/com/example/xcpro/sensors/domain/SensorFrontEnd.kt`

- TC 30s sample selection (for the 30-second average):
  - Uses TE vario when available, otherwise pressure-altitude vario (QNH-independent), then GPS vario, then brutto vario.
  - File: `feature/map/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt`

- 30s window:
  - `FusionBlackboard.updateAveragesAndDisplay()` owns the 30-second rolling windows.
  - Uses `FixedSampleAverageWindow(AVERAGE_WINDOW_SECONDS = 30)`.
  - Adds one sample per elapsed second via `addSamplesForElapsedSeconds()`.
  - Resets the window when circling state toggles or time moves backward.
  - Files:
    - `feature/map/src/main/java/com/example/xcpro/sensors/domain/FusionBlackboard.kt`
    - `feature/map/src/main/java/com/example/xcpro/sensors/FixedSampleAverageWindow.kt`
    - `feature/map/src/main/java/com/example/xcpro/sensors/WindowFill.kt`

- Output field:
  - `val thermalAvg30s = bruttoAverage30s.toFloat()`
  - Saved into `FlightMetricsResult.thermalAverage30s`.

---

## Domain -> UI models

### 3) CompleteFlightData (app-level SSOT)

- `FlightDisplayMapper` copies `thermalAverage30s` into `CompleteFlightData.thermalAverage`.
- File: `feature/map/src/main/java/com/example/xcpro/flightdata/FlightDisplayMapper.kt`

### 4) RealTimeFlightData (cards)

- `convertToRealTimeFlightData()` maps `CompleteFlightData.thermalAverage` -> `RealTimeFlightData.thermalAverage`.
- File: `feature/map/src/main/java/com/example/xcpro/MapScreenUtils.kt`

---

## UI / Cards path

### 5) Live data to card state

- `FlightDataRepository` emits `CompleteFlightData` to the UI.
- `MapScreenObservers` converts it and updates `FlightDataManager`.
  - File: `feature/map/src/main/java/com/example/xcpro/map/MapScreenObservers.kt`

- `FlightDataManager.cardFlightDataFlow` is collected in `MapComposeEffects`, which drives card updates:
  - `flightViewModel.updateCardsWithLiveData(displaySample)`
  - File: `feature/map/src/main/java/com/example/xcpro/map/ui/effects/MapComposeEffects.kt`

### 6) Card mapping

- Card ID: `thermal_avg` (title "TC 30s").
  - Definition: `dfcards-library/src/main/java/com/example/dfcards/CardLibraryCatalog.kt`

- Formatting logic:
  - `CardDataFormatter` uses `RealTimeFlightData.thermalAverage` for the primary value.
  - Secondary is current thermal lift rate if `currentThermalValid`, otherwise "---".
  - File: `dfcards-library/src/main/java/com/example/dfcards/CardDataFormatter.kt`

- Rendering:
  - `CardStateRepository.updateCardsWithLiveData()` maps values into `FlightData` objects.
  - `CardContainer` -> `EnhancedFlightDataCard` renders the card.
  - Files:
    - `dfcards-library/src/main/java/com/example/dfcards/dfcards/CardStateRepositoryUpdates.kt`
    - `dfcards-library/src/main/java/com/example/dfcards/dfcards/CardContainer.kt`
    - `dfcards-library/src/main/java/com/example/dfcards/dfcards/EnhancedFlightDataCard.kt`

### 7) Parity adjustments (Jan 2026)

- Averaging: non-finite samples are treated as 0.0 so the 30s window keeps moving instead of freezing.
  - File: `feature/map/src/main/java/com/example/xcpro/sensors/domain/FusionBlackboard.kt`
- Sampling: TC 30s uses a QNH-independent pressure-altitude vario when TE is not available.
  - Files:
    - `feature/map/src/main/java/com/example/xcpro/sensors/domain/SensorFrontEnd.kt`
    - `feature/map/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt`
- Time rounding: elapsed seconds are rounded (not floored), and long forward time jumps reset the window.
  - File: `feature/map/src/main/java/com/example/xcpro/sensors/WindowFill.kt`
- Coloring: TC 30s now colors directly from the average value without a deadband. Zero can still be colored if risk logic triggers.
  - File: `dfcards-library/src/main/java/com/example/dfcards/dfcards/CardStateRepositoryUpdates.kt`
- Formatting: TC 30s uses 0 decimals for ft/min and 1 decimal for other vertical speed units.
  - File: `dfcards-library/src/main/java/com/example/dfcards/CardDataFormatter.kt`

---

## Why TC 30s can appear "not displaying"

Important: The TC 30s card only shows a value when `RealTimeFlightData` reaches the card system and the value is finite. There is no separate UI gate for TC 30s. The "blank" state happens when data does not arrive or the value is non-finite.

### Primary causes seen in code

1) No live data reaching cards
   - Cards update only after `cardHydrationReady == true` (container size + first live sample).
   - If the map container never reports size, or the flight data flow is null, TC 30s stays `--`.
   - Path to check: `MapScreenObservers` -> `FlightDataManager` -> `MapComposeEffects`.

2) Active source gating (replay vs live)
   - `FlightDataRepository` ignores updates from non-active sources.
   - If the active source is stuck on `REPLAY`, live sensor updates are dropped and cards show `--`.
   - File: `feature/map/src/main/java/com/example/xcpro/flightdata/FlightDataRepository.kt`

3) Non-finite TC 30s value
   - The card uses `avgValue.isFinite()`; if TC 30s is NaN/inf, it renders the placeholder.
   - This can happen if no finite brutto vario samples are produced (startup, sensor dropout, time jumps).
   - Window logic lives in `FusionBlackboard.updateAveragesAndDisplay()`.

4) Wrong card ID in templates or saved preferences
   - The TC 30s card ID must be `thermal_avg`.
   - Old templates or saved layouts with legacy IDs will not map to a real card definition and will not render data.
   - Card lookup happens in `CardStateRepositoryLayout.createCardsFromTemplate()`.

---

## Debug checklist (quick answers)

- Is TC 30s computed?
  - Enable `LOG_THERMAL_METRICS` in `FlightDataConstants` and check log line:
    `Thermal metrics: TC30=...` in `FlightDataEmitter`.

- Is the data reaching cards?
  - Verify `flightDataRepository.flightData` is non-null and `cardHydrationReady` becomes true.

- Is the card ID correct?
  - Ensure the template or layout uses `thermal_avg` (not `tc30`, `thermal30`, etc.).

---

## Related fields (avoid mixing)

- TC 30s (this doc): `RealTimeFlightData.thermalAverage` -> card id `thermal_avg`.
- TC AVG (current thermal): `RealTimeFlightData.thermalAverageCircle` -> card id `thermal_tc_avg`.
- T AVG (total average): `RealTimeFlightData.thermalAverageTotal` -> card id `thermal_t_avg`.

---

## File map (quick navigation)

- Averages / TC 30s:
  - `feature/map/src/main/java/com/example/xcpro/sensors/domain/FusionBlackboard.kt`
  - `feature/map/src/main/java/com/example/xcpro/sensors/FixedSampleAverageWindow.kt`
  - `feature/map/src/main/java/com/example/xcpro/sensors/WindowFill.kt`

- Domain to UI mapping:
  - `feature/map/src/main/java/com/example/xcpro/flightdata/FlightDisplayMapper.kt`
  - `feature/map/src/main/java/com/example/xcpro/MapScreenUtils.kt`

- Cards:
  - `dfcards-library/src/main/java/com/example/dfcards/CardLibraryCatalog.kt`
  - `dfcards-library/src/main/java/com/example/dfcards/CardDataFormatter.kt`
  - `dfcards-library/src/main/java/com/example/dfcards/dfcards/CardStateRepositoryUpdates.kt`

---

## Summary

- TC 30s is computed from brutto vario via a 30-second rolling window in `FusionBlackboard`.
- The value flows cleanly into `CompleteFlightData` and `RealTimeFlightData` and is displayed by the card id `thermal_avg`.
- If it appears blank, the most likely causes are no live data reaching the card system, active source gating, non-finite vario, or a wrong card ID.
