
# Netto 30s (NETTO 30S) - end to end flow and display notes

Last updated: 2026-01-30

## What it is
- Netto = air mass vertical movement = brutto vario + still-air sink rate from the glider polar.
- Netto 30s = rolling 30-second average of netto samples (one sample per elapsed second).
- Primary card id: netto_avg30 (title "NETTO 30S"). The single-sample netto card id is netto.

## Key card IDs and templates
- Card IDs: "netto_avg30", "netto".
- Card definitions: dfcards-library/src/main/java/com/example/dfcards/CardLibraryCatalog.kt
- Card ID constants: dfcards-library/src/main/java/com/example/dfcards/CardId.kt and KnownCardId.kt
- Default Thermal template includes netto_avg30: dfcards-library/src/main/java/com/example/dfcards/FlightTemplates.kt

## Sensor -> domain pipeline
1) Sensors and fusion loops
   - Baro/IMU loop: feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngineLoops.kt
   - GPS loop: same file

2) Emit and calculate metrics
   - FlightDataEmitter.emit(): feature/map/src/main/java/com/example/xcpro/sensors/FlightDataEmitter.kt
   - CalculateFlightMetricsUseCase.execute(): feature/map/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt

3) Brutto vario selection (input to netto)
   - SensorFrontEnd.buildSnapshot() picks TE -> Pressure -> Baro -> GPS
   - File: feature/map/src/main/java/com/example/xcpro/sensors/domain/SensorFrontEnd.kt

4) Netto computation (single-sample)
   - FlightCalculationHelpers.calculateNetto() returns (value, valid)
   - File: feature/map/src/main/java/com/example/xcpro/sensors/FlightCalculationHelpers.kt
   - Uses StillAirSinkProvider.sinkAtSpeed() (polar/glider config)
     - File: feature/map/src/main/java/com/example/xcpro/glider/StillAirSinkProvider.kt
   - Speed selection uses TAS when available, otherwise recent TAS/GND or a fallback.
   - If no motion evidence or no polar, returns brutto and sets valid=false.

5) Netto sample for the 30s window
   - FusionBlackboard.resolveNettoSampleValue() uses last valid netto as fallback.
   - File: feature/map/src/main/java/com/example/xcpro/sensors/domain/FusionBlackboard.kt

6) 30-second window for NETTO 30S
   - FusionBlackboard.updateAveragesAndDisplay() uses FixedSampleAverageWindow(30).
   - Adds one sample per elapsed second (addSamplesForElapsedSeconds()).
   - Resets on time going backwards or circling-state toggle.
   - Non-finite samples are treated as 0.0 to keep the window moving.
   - Files:
     - feature/map/src/main/java/com/example/xcpro/sensors/domain/FusionBlackboard.kt
     - feature/map/src/main/java/com/example/xcpro/sensors/FixedSampleAverageWindow.kt
     - feature/map/src/main/java/com/example/xcpro/sensors/WindowFill.kt

7) Display netto smoothing
   - FusionBlackboard also keeps a 5s TimedAverageWindow for displayNettoRaw.
   - CalculateFlightMetricsUseCase smooths displayNettoRaw with DisplayVarioSmoother.

8) Output fields from the domain
   - FlightMetricsResult.nettoAverage30s
   - FlightMetricsResult.displayNetto
   - FlightMetricsResult.netto
   - FlightMetricsResult.nettoValid
   - File: feature/map/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt

## Domain -> UI models
- FlightDisplayMapper copies nettoAverage30s, displayNetto, netto, nettoValid into CompleteFlightData.
  - File: feature/map/src/main/java/com/example/xcpro/flightdata/FlightDisplayMapper.kt
- convertToRealTimeFlightData() maps those fields into RealTimeFlightData.
  - File: feature/map/src/main/java/com/example/xcpro/MapScreenUtils.kt

## UI / cards path
1) SensorFusionRepository.flightDataFlow -> VarioServiceManager -> FlightDataRepository
   - Files:
     - feature/map/src/main/java/com/example/xcpro/sensors/SensorFusionRepository.kt
     - feature/map/src/main/java/com/example/xcpro/vario/VarioServiceManager.kt
     - feature/map/src/main/java/com/example/xcpro/flightdata/FlightDataRepository.kt

2) FlightDataUiAdapter / MapScreenObservers converts CompleteFlightData to RealTimeFlightData
   - File: feature/map/src/main/java/com/example/xcpro/map/MapScreenObservers.kt (wrapped by FlightDataUiAdapter)

3) FlightDataManager buffers and buckets data for cards
   - File: feature/map/src/main/java/com/example/xcpro/map/FlightDataManager.kt

4) CardIngestionCoordinator pushes card data into FlightDataViewModel
   - File: feature/map/src/main/java/com/example/xcpro/map/CardIngestionCoordinator.kt

5) Card formatting
   - CardDataFormatter and CardFormatSpec
   - File: dfcards-library/src/main/java/com/example/dfcards/CardFormatSpec.kt
   - NETTO_AVG30 uses liveData.nettoAverage30s and label "NETTO".
   - NETTO uses liveData.displayNetto and label "NETTO" or "NO POLAR" based on nettoValid.

## Formatting details
- Vertical speed formatting comes from UnitsFormatter.
- Decimals: 0 for ft/min, 1 for other units.
- Files:
  - dfcards-library/src/main/java/com/example/dfcards/CardFormatSpec.kt
  - dfcards-library/src/main/java/com/example/xcpro/common/units/UnitsFormatter.kt

## Common "blank" or stale cases
- liveData is null (CardDataFormatter returns placeholder).
- Card ID mismatch (must be "netto_avg30" for NETTO 30S).
- Cards not hydrated (container size not ready or cardsReady false).
- FlightDataRepository gating set to REPLAY while expecting LIVE data.
- Sensor pipeline not running (no updates from VarioServiceManager/SensorFusionRepository).
- Netto validity warmup: nettoValid stays false for the first 20 seconds after speed+polar become available.

## Quick debug checklist
- Confirm polar configured:
  - StillAirSinkProvider.sinkAtSpeed() returns non-null.
- Confirm domain outputs:
  - FlightMetricsResult.nettoAverage30s and nettoValid
- Confirm mapping:
  - CompleteFlightData.nettoAverage30s
  - RealTimeFlightData.nettoAverage30s
- Confirm cards are updating:
  - CardIngestionCoordinator -> FlightDataViewModel.updateCardsWithLiveData()

## Related fields (avoid mixing)
- NETTO 30S: RealTimeFlightData.nettoAverage30s -> card id "netto_avg30"
- NETTO (live): RealTimeFlightData.displayNetto + nettoValid -> card id "netto"

