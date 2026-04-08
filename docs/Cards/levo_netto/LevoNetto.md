
# Levo Netto (LEVO NETTO) - end to end flow, display, and XCSoar parity notes

Last updated: 2026-02-04

## What it is
- Levo Netto is XCPro's glide-netto output (air-mass vertical speed in cruise), separate from legacy netto.
- Computed from baseline vario (w_meas) minus still-air sink from the active glider polar (IAS-based).
- Uses IAS/TAS from the wind/airspeed pipeline; IAS is mandatory for sink lookup.
- Primary consumers: Levo Netto card, Levo Vario secondary line, and STF/Auto-MC (with validity gating).

## XCSoar parity behavior (always-visible value)
XCSoar always displays netto, even without wind/polar, by falling back to brutto when sink rate is unknown.
XCPro should follow the same display behavior:
- If wind and/or polar are missing, Levo Netto still renders a numeric value.
- In those cases, sink is treated as 0 (equivalent to brutto vario), so the display is still live.
- The `levoNettoValid` flag remains the truth for "glide-netto is valid" (straight flight + wind confidence + polar).

This keeps STF safe (it ignores invalid glide-netto) while letting the UI show a value at all times.

## Key card IDs and templates
- Card ID: "levo_netto" (title "LEVO NETTO").
- Card catalog aggregation: dfcards-library/src/main/java/com/example/dfcards/CardLibraryCatalog.kt
- Category-owned card definitions: dfcards-library/src/main/java/com/example/dfcards/CardLibraryPerformanceCatalog.kt
- Card IDs: dfcards-library/src/main/java/com/example/dfcards/CardId.kt + KnownCardId.kt

## Sensor -> domain pipeline
1) Wind + airspeed inputs
   - Wind fusion output: feature/map/src/main/java/com/example/xcpro/weather/wind/data/WindSensorFusionRepository.kt
   - WindState: feature/map/src/main/java/com/example/xcpro/weather/wind/model/WindState.kt
   - IAS/TAS estimate: feature/map/src/main/java/com/example/xcpro/sensors/domain/WindEstimator.kt

2) Core computation
   - CalculateFlightMetricsUseCase.execute():
     feature/map/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt
   - LevoNettoCalculator:
     feature/map/src/main/java/com/example/xcpro/sensors/domain/LevoNettoCalculator.kt
   - StillAirSinkProvider (polar sink):
     feature/map/src/main/java/com/example/xcpro/glider/StillAirSinkProvider.kt

3) Domain -> UI
   - FlightDisplayMapper -> CompleteFlightData:
     feature/map/src/main/java/com/example/xcpro/flightdata/FlightDisplayMapper.kt
   - convertToRealTimeFlightData():
     feature/map/src/main/java/com/example/xcpro/MapScreenUtils.kt
   - Card formatting:
     dfcards-library/src/main/java/com/example/dfcards/CardFormatSpec.kt

## Computation details (Levo Netto)
- Input vertical speed: baseline vario (pressure/baro/GPS fallback) from SensorFrontEnd.
- IAS used for polar sink lookup; TAS used for distance window timing when available.
- Distance window: 0.6 km (tau = window_m / max(TAS, min_speed)).
- Gating for *valid* glide-netto:
  - straight flight only (not circling/turning)
  - flying = true
  - wind confidence >= 0.1 and wind available
  - polar available
- `levoNettoValid` is true only when all gating criteria pass.

## XCSoar parity fallback (display value)
- Display value is always a number (no blank state):
  - If polar missing or IAS invalid: sink = 0.0 (netto = w_meas)
  - If wind missing: still show w_meas - sink (sink may be 0)
- This mirrors XCSoar:
  - sink_rate = 0 if not flying / no airspeed / no polar
  - netto = brutto - sink_rate, so it becomes brutto when sink is unknown.

## UI / card display (LEVO NETTO)
- Primary value: Levo Netto numeric (always shown).
- Secondary value (bottom, small text):
  - Wind footer when wind is available: "<speed><unit>/<dir>Deg"
    - Example: "8kt/287Deg"
    - Speed uses UnitsFormatter (unit label appended with no space).
    - Direction is 0..359, "Deg" suffix (capital D).
  - If wind is missing: show "NO WIND".
  - If polar is missing: show "NO POLAR" (takes precedence over wind footer).

This uses EnhancedFlightDataCard's secondary line (bottom-aligned small text).

## Levo Vario UI behavior
- Secondary line shows Levo Netto numeric (units stripped) even if invalid.
- When wind or polar is missing, the secondary label color is error red.
- Files:
  - feature/map/src/main/java/com/example/xcpro/map/ui/OverlayPanels.kt
  - feature/map/src/main/java/com/example/xcpro/map/ui/widgets/VariometerWidgetImpl.kt

## Formatting details
- Primary: UnitsFormatter.verticalSpeed (respect unit prefs).
- Wind footer speed: UnitsFormatter.speed; compact as "8kt" (no space).
- Direction: integer degrees, suffix "Deg".
- Use secondaryValue in CardFormatSpec for the footer string.

## Data fields (do not mix semantics)
- `levoNetto` (RealTimeFlightData): display value (always numeric).
- `levoNettoValid`: true only when glide-netto is valid.
- `levoNettoHasWind` / `levoNettoHasPolar`: availability flags for warnings.
- STF uses `levoNettoValid` to gate MC_eff; invalid values must not influence STF.

## Debug checklist
- Verify polar configured:
  - StillAirSinkProvider.sinkAtSpeed() returns non-null.
- Verify wind status:
  - WindState.isAvailable and confidence >= 0.1.
- Verify Levo Netto output:
  - FlightMetricsResult.levoNettoMs changes with vario.
  - levoNettoValid toggles with straight-flight + wind/polar.
- Verify card formatting:
  - Primary value always shows.
  - Footer shows "8kt/287Deg" when wind present.

## Related files
- LevoNettoCalculator: feature/map/src/main/java/com/example/xcpro/sensors/domain/LevoNettoCalculator.kt
- CalculateFlightMetricsUseCase: feature/map/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt
- CardFormatSpec: dfcards-library/src/main/java/com/example/dfcards/CardFormatSpec.kt
- EnhancedFlightDataCard: dfcards-library/src/main/java/com/example/dfcards/dfcards/EnhancedFlightDataCard.kt
- Wind formatting helpers: dfcards-library/src/main/java/com/example/dfcards/CardFormatSpec.kt

