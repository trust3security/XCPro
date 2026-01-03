# TC 30s Card Value (thermal_avg)

## Purpose
This document is a deep, code-accurate reference for the "TC 30s" card value
that appears in the flight data cards. It is written to give a new chat or
new developer enough context to safely change the metric, its formatting,
or its update behavior.

If you change any part of this pipeline, update this file and the tests
listed below.

## What "TC 30s" means in this codebase
TC 30s is the 30-second average of the "brutto" vario sample (thermal climb or
sink) computed by the fused sensor pipeline. It is not the same as:
- "TC AVG" (current circle lift rate) or
- "T AVG" (average over the entire thermal history).

The card shows:
- Primary value: the 30-second average (this doc).
- Secondary value: current thermal lift rate (from ThermalTracker).

## End-to-end data flow (card display)
1) Sensor fusion produces a fused frame:
   - File: `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculator.kt`
   - It emits frames via `FlightDataEmitter`.

2) The metrics use case calculates the 30s average:
   - File: `feature/map/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt`
   - `avgVarioSample = bruttoVario`
   - `bruttoAverage30s` comes from `FusionBlackboard.updateAveragesAndDisplay()`

3) The 30s average becomes the thermalAverage30s metric:
   - `thermalAverage30s = bruttoAverage30s.toFloat()`
   - File: `feature/map/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt`

4) Metrics are mapped into UI-facing models:
   - `FlightDisplayMapper` maps `thermalAverage30s` into `CompleteFlightData.thermalAverage`
   - File: `feature/map/src/main/java/com/example/xcpro/flightdata/FlightDisplayMapper.kt`

5) Map layer converts to RealTimeFlightData:
   - `convertToRealTimeFlightData()` copies `CompleteFlightData.thermalAverage` into
     `RealTimeFlightData.thermalAverage`
   - File: `feature/map/src/main/java/com/example/xcpro/MapScreenUtils.kt`

6) Card formatting and display:
   - Card id: `thermal_avg` (title "TC 30s")
   - Files:
     - `dfcards-library/src/main/java/com/example/dfcards/CardLibraryCatalog.kt`
     - `dfcards-library/src/main/java/com/example/dfcards/CardDataFormatter.kt`
     - `dfcards-library/src/main/java/com/example/dfcards/dfcards/CardStateRepositoryUpdates.kt`

## 30-second average algorithm (deep details)
### Sample source (bruttoVario)
`SensorFrontEnd` picks the brutto vario sample from a priority chain:
- TE vario (if true airspeed is valid and not GPS-ground fallback)
- Pressure vario (pressure altitude derivative)
- Baro vario (baro altitude derivative)
- GPS vario (GPS altitude derivative)

File: `feature/map/src/main/java/com/example/xcpro/sensors/domain/SensorFrontEnd.kt`

Notes:
- TE vario is only used when airspeed estimate is not GPS-ground.
- Derivatives are guarded and clamped for pressure/baro/GPS vario; TE vario
  uses the computed value from total-energy logic.

### Windowing and time semantics
The 30s average uses `FixedSampleAverageWindow` with capacity 30.
Samples are added using `addSamplesForElapsedSeconds()`:
- One sample per whole elapsed second.
- If updates are faster than 1 Hz, new values are not added until a full
  second has elapsed.
- If updates are slower than 1 Hz, the current value is duplicated once
  per elapsed second (time-weighted by whole seconds).

Files:
- `feature/map/src/main/java/com/example/xcpro/sensors/FixedSampleAverageWindow.kt`
- `feature/map/src/main/java/com/example/xcpro/sensors/WindowFill.kt`

### Reset conditions
The average windows reset when:
- Time moves backwards (replay seek or clock reset), or
- The circling state toggles (enter/exit circling).

File: `feature/map/src/main/java/com/example/xcpro/sensors/domain/FusionBlackboard.kt`

Implication: TC 30s is per-thermal segment and will "start fresh" when
circling state changes, and on time discontinuities.

### Validity
`bruttoAverage30sValid` is `bruttoAverage30s.isFinite()`.
This is mapped to `RealTimeFlightData.thermalAverageValid` but the card
formatting does not currently gate on this flag.

## Card formatting rules (TC 30s)
Card id: `thermal_avg`

Primary value:
- Uses `RealTimeFlightData.thermalAverage` (Float).
- Formatted with 1 decimal place and unit via `UnitsFormatter.verticalSpeed`.
- Values in (-0.05, +0.05) are clamped to zero and rendered without a sign.

Secondary value:
- Uses `RealTimeFlightData.currentThermalLiftRate` if `currentThermalValid`.
- Otherwise displays "---".
- This value comes from `ThermalTracker` (current or last thermal).

Files:
- `dfcards-library/src/main/java/com/example/dfcards/CardDataFormatter.kt`
- `feature/map/src/main/java/com/example/xcpro/sensors/ThermalTracker.kt`

## Color rules for TC 30s
`CardStateRepositoryUpdates.highlightColorFor()` drives the primary color:
- Zero or non-finite values -> no color override.
- Positive average -> green.
- Negative average -> red.
- If macCreadyRisk > 0 and (2 * avg) < risk -> force red (risk highlighting).

File: `dfcards-library/src/main/java/com/example/dfcards/dfcards/CardStateRepositoryUpdates.kt`

## Update cadence seen by the card
1) `FlightDataManager.cardFlightDataFlow` throttles and buckets UI values
   at about 12 Hz (UI frame target). TC 30s is not bucketed.
   - File: `feature/map/src/main/java/com/example/xcpro/map/FlightDataManager.kt`

2) Card state updates depend on visibility:
   - Visible cards update every 250 ms.
   - Background cards update every 1000 ms.
   - TC 30s is not in the fast-update set.
   - File: `dfcards-library/src/main/java/com/example/dfcards/dfcards/CardStateRepository.kt`

## Tests that define expected behavior
File: `feature/map/src/test/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCaseTest.kt`
- `tc30s_tracks_constant_climb`: average converges to constant climb.
- `tc30s_ignores_single_spike`: average resists a single spike.
- `tc30s_stable_on_qnh_change`: average stays stable across QNH change.

If you change the algorithm or sample source, update or add tests here.

## Related values (for context)
- TC AVG card:
  - Uses ThermalTracker current thermal lift rate (gain / duration).
  - `RealTimeFlightData.thermalAverageCircle`
  - Card id: `thermal_tc_avg`
- T AVG card:
  - Average of entire thermal history.
  - `RealTimeFlightData.thermalAverageTotal`
  - Card id: `thermal_t_avg`
- TC GAIN card:
  - Thermal gain (altitude gained in current or last thermal).
  - Card id: `thermal_tc_gain`

## Change checklist (for a new chat)
If you are touching TC 30s:
1) Confirm which sample you want to average (TE vs pressure vs baro vs GPS).
2) Update windowing logic in `FusionBlackboard` or `WindowFill` if needed.
3) Adjust `FlightMetricsConstants.AVERAGE_WINDOW_SECONDS` if window length changes.
4) Review formatting and zero threshold in `CardDataFormatter`.
5) Review color logic in `CardStateRepositoryUpdates`.
6) Update tests in `CalculateFlightMetricsUseCaseTest`.
7) Update this document.

## Known quirks / pitfalls
- The 30s window is effectively 1 Hz, regardless of faster sensor cadence.
- TC 30s resets on circling state changes (enter/exit thermal).
- `thermalAverageValid` is currently not used to gate the card display.
