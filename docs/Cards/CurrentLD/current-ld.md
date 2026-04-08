# Current L/D Branch Truth And External Review Brief

This file is the shortest single-file brief to hand to an external reviewer.

It answers:

- what XCPro ships today
- what `ld_curr` actually uses
- what wind / bugs / ballast / polar affect today
- what is still missing if the product goal is a more "real-world" current
  glide-efficiency metric

## What XCPro ships today

XCPro ships two different measured glide cards:

- `ld_curr`
  - title: `L/D CURR`
  - meaning: recent measured over-ground glide ratio
  - runtime fields: `currentLD` and `currentLDValid`
- `ld_vario`
  - title: `L/D VARIO`
  - meaning: measured through-air glide ratio from chosen true airspeed and
    TE vario
  - runtime fields: `currentLDAir` and `currentLDAirValid`

XCPro also ships separate theoretical polar metrics:

- `polar_ld`
  - theoretical still-air L/D at current IAS
- `best_ld`
  - best theoretical still-air L/D from the active polar

And it ships separate glide-solver / required-glide outputs for final-glide
and arrival calculations.

## What `ld_curr` uses today

`ld_curr` is still the old measured over-ground metric.

Owner path:

1. `feature/flight-runtime/src/main/java/com/example/xcpro/sensors/FlightCalculationHelpers.kt`
2. `feature/flight-runtime/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsRuntime.kt`
3. `feature/flight-runtime/src/main/java/com/example/xcpro/flightdata/FlightDisplayMapper.kt`
4. `feature/map/src/main/java/com/example/xcpro/MapScreenUtils.kt`
5. `dfcards-library/src/main/java/com/example/dfcards/CardFormatSpec.kt`

Current branch math:

- recent GPS-path displacement
- divided by barometric altitude loss

Branch-truth details:

- recompute interval: `5000 ms`
- requires:
  - distance traveled `> 10 m`
  - altitude lost `> 0.5 m`
- helper stores and returns a clamped value in `5f..100f`
- validity is:
  - `currentLDValid = calculatedLD.isFinite() && calculatedLD > 0f`

This means `ld_curr` is:

- measured
- recent
- over-ground
- path-based
- not theoretical

## What `ld_curr` does NOT use today

Current `ld_curr` does not directly use:

- wind speed
- wind direction
- `WindState`
- IAS
- TAS
- `teVario`
- `PolarStillAirSinkProvider`
- `polar_ld`
- `best_ld`
- `bugsPercent`
- `waterBallastKg`
- `threePointPolar`
- `referenceWeightKg`
- `userCoefficients`
- final-glide / required-glide math

So the current displayed `ld_curr` value is not a polar-aware or
configuration-aware performance metric.

## What `ld_vario` uses today

`ld_vario` is a separate additive measured air-data metric.

Owner path:

1. `feature/flight-runtime/src/main/java/com/example/xcpro/sensors/domain/CurrentAirLdCalculator.kt`
2. `feature/flight-runtime/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsRuntime.kt`
3. `feature/flight-runtime/src/main/java/com/example/xcpro/flightdata/FlightDisplayMapper.kt`
4. `feature/map/src/main/java/com/example/xcpro/MapScreenUtils.kt`
5. `dfcards-library/src/main/java/com/example/dfcards/CardFormatSpec.kt`

Current branch math:

- `currentLDAir = trueAirspeedMs / (-teVario)`

Current validity gates:

- aircraft is flying
- `tasValid == true`
- chosen airspeed source is not GPS fallback
- `trueAirspeedMs` is finite and `> 5`
- `teVario` is finite
- `teSinkMs = -teVario` is finite and `> 0.15`
- not circling
- not turning

This means `ld_vario` is:

- measured
- recent
- through-air
- air-data based
- still non-polar

It also does not directly use bugs, ballast, or active-polar math.

## Where wind lives today

Wind is a separate seam.

Authoritative owner:

- `feature/flight-runtime/src/main/java/com/example/xcpro/weather/wind/data/WindSensorFusionRepository.kt`

Model:

- `feature/flight-runtime/src/main/java/com/example/xcpro/weather/wind/model/WindState.kt`

Overrides:

- `feature/profile/src/main/java/com/example/xcpro/weather/wind/data/WindOverrideRepository.kt`

What wind affects today:

- wind-aware chosen airspeed selection
- glide solving / final-glide consumers
- map/UI wind display fields
- sometimes `ld_vario` indirectly, because `ld_vario` uses the already chosen
  authoritative airspeed seam

What wind does not affect today:

- `ld_curr` directly

So if the product wants wind to be part of "Current L/D", that is a semantic
change, not a bug fix.

## Where bugs / ballast / polar live today

Active-polar owner path:

1. `feature/profile/src/main/java/com/example/xcpro/glider/GliderRepository.kt`
2. `feature/profile/src/main/java/com/example/xcpro/glider/PolarStillAirSinkProvider.kt`
3. `core/common/src/main/java/com/example/xcpro/glider/PolarCalculator.kt`
4. `core/common/src/main/java/com/example/xcpro/glider/GliderSpeedBounds.kt`

Active runtime configuration fields:

- `threePointPolar`
- effective model polar
- `bugsPercent`
- `waterBallastKg`
- `iasMinMs`
- `iasMaxMs`

Stored but currently deferred from authoritative runtime polar math:

- `referenceWeightKg`
- `userCoefficients`

What bugs / ballast / polar affect today:

- `sinkAtSpeed(...)`
- `ldAtSpeed(...)`
- `bestLd()`
- final-glide and other still-air glide computations

What they do not affect today:

- `ld_curr`
- `ld_vario`

So if the goal is "Current L/D should react to bugs, ballast, and active
polar," that is not the current metric contract. That would be a new metric or
an explicit redesign.

## What this means for a more "real" Current L/D

There are three different concepts and they should stay separate:

- measured over-ground glide ratio
  - current XCPro `ld_curr`
- measured through-air glide ratio
  - current XCPro `ld_vario`
- current performance versus active polar
  - not implemented yet

If the product goal is a more real-world test harness, that is reasonable.
But the harness should not pretend that current `ld_curr` already uses bugs,
ballast, wind, and polar internally, because it does not.

The realistic way to test current glide behavior would be:

- simulate a glide path from A to B with:
  - start height
  - distance
  - wind
  - chosen glider profile
  - bugs
  - ballast
  - active polar
- run that through replay/runtime
- observe:
  - `ld_curr`
  - `ld_vario`
  - `polar_ld`
  - `best_ld`
  - final-glide outputs

In that setup:

- wind, bugs, ballast, and polar matter because they shape the simulated flight
  path and sink behavior
- but `ld_curr` still remains a measured over-ground output from the resulting
  path

So the missing product question is not "does XCPro already use those inputs in
Current L/D?"

The real question is:

- should XCPro keep `ld_curr` as measured over-ground glide ratio
- and add a separate current-vs-polar or glide-efficiency metric for the pilot
  concept you actually want?

## Questions For ChatGPT Pro

Please answer these from the branch truth above:

1. Should XCPro keep `ld_curr` exactly as a measured over-ground glide ratio?
2. Should a more "real" pilot-facing efficiency metric be additive rather than
   changing `ld_curr`?
3. If the product wants wind / bugs / ballast / polar to matter directly, is
   the correct concept:
   - a through-air measured metric
   - a current-vs-polar metric
   - or both?
4. What is the best pilot-facing distinction between:
   - `ld_curr`
   - `ld_vario`
   - `polar_ld`
   - `best_ld`
   - any future current-vs-polar metric?
5. For a realistic validation harness, what should be asserted separately for:
   - over-ground measured glide
   - through-air measured glide
   - polar prediction
   - current-vs-polar comparison?
