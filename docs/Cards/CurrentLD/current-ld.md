# Current L/D Branch Truth And External Review Brief

This file is the shortest single-file brief to hand to an external reviewer.

It answers:

- what XCPro now shows to the pilot as `Current L/D`
- which raw glide metrics still exist underneath that visible card
- how wind, active polar, bugs, and ballast matter today
- what is still intentionally out of scope

## What XCPro ships today

XCPro now ships one fused pilot-facing `Current L/D` card plus two raw glide
metrics underneath it.

Visible pilot-facing card:

- `ld_curr`
  - title: `L/D CURR`
  - visible meaning: current effective glide ratio for the pilot
  - visible runtime fields: `pilotCurrentLD` and `pilotCurrentLDValid`

Raw metrics still kept internally and in DTOs:

- `currentLD`
  - raw recent measured over-ground glide ratio
- `currentLDAir`
  - raw recent measured through-air glide ratio from chosen true airspeed and
    TE vario

Selectable advanced card:

- `ld_vario`
  - title: `L/D VARIO`
  - visible meaning: raw measured through-air glide ratio
  - runtime fields: `currentLDAir` and `currentLDAirValid`

Separate theoretical polar metrics still exist:

- `polar_ld`
  - theoretical still-air L/D at current IAS
- `best_ld`
  - best theoretical still-air L/D from the active polar

`currentVsPolar` is still not implemented.

## What the visible `ld_curr` card means now

The visible `ld_curr` card is no longer a direct display of the old raw
ground-only `currentLD` metric.

It now means:

- current effective glide ratio for the pilot
- wind-aware when wind is valid and trustworthy
- zero-wind fallback when wind is unavailable, stale, or low-confidence
- protected against circling/turning/climbing geometry
- stabilized by the active polar seam without turning into a pure polar number

This is the one pilot-facing Current L/D number.

## What powers the visible `ld_curr` card now

New visible owner path:

1. `feature/map-runtime/src/main/java/com/example/xcpro/currentld/PilotCurrentLdCalculator.kt`
2. `feature/map-runtime/src/main/java/com/example/xcpro/currentld/PilotCurrentLdRepository.kt`
3. `feature/map/src/main/java/com/example/xcpro/map/FlightDataUiAdapter.kt`
4. `feature/map/src/main/java/com/example/xcpro/map/MapScreenObservers.kt`
5. `feature/map/src/main/java/com/example/xcpro/MapScreenUtils.kt`
6. `core/flight/src/main/java/com/example/xcpro/core/flight/RealTimeFlightData.kt`
7. `dfcards-library/src/main/java/com/example/dfcards/CardFormatSpec.kt`

High-level behavior:

- rolling matched-window estimator
- default live window: `20 s`
- minimum publish fill: `8 s`
- hold timeout during thermal/turn/climb: `20 s`
- short TE-gap active-polar sink support: `3 s` max contiguous gap

Matched-window math:

- `effectiveDistance = Σ(effectiveForwardSpeed_i * dt_i)`
- `heightLost = Σ(sink_i * dt_i)`
- `pilotCurrentLD = effectiveDistance / heightLost`

Preferred straight-flight sample math:

- `sink_i = max(-teVario_i, 0.15)`
- `windAlong_i = projected wind along glide direction when wind is valid`
- `windAlong_i = 0` when wind is not trustworthy
- `effectiveForwardSpeed_i = trueAirspeedMs_i + windAlong_i`

Direction priority:

1. active target/course bearing from `WaypointNavigationRepository`
2. smoothed recent straight-flight GPS track
3. freeze the last valid glide direction during thermalling/turning/climbing

The visible card does not average instantaneous L/D values.

## What the raw metrics still mean

### Raw over-ground metric

`currentLD/currentLDValid` still exist and still mean:

- recent measured over-ground glide ratio
- recent GPS-path displacement divided by barometric altitude loss

That raw metric is still owned by:

1. `feature/flight-runtime/src/main/java/com/example/xcpro/sensors/FlightCalculationHelpers.kt`
2. `feature/flight-runtime/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsRuntime.kt`
3. `feature/flight-runtime/src/main/java/com/example/xcpro/flightdata/FlightDisplayMapper.kt`
4. `feature/map/src/main/java/com/example/xcpro/MapScreenUtils.kt`

It remains available for diagnostics, replay verification, and degraded
fallback inside the fused pilot metric path.

### Raw through-air metric

`currentLDAir/currentLDAirValid` still exist and still mean:

- measured through-air glide ratio from chosen true airspeed and TE sink

Math:

- `currentLDAir = trueAirspeedMs / (-teVario)`

That raw metric remains the backing metric for:

- `ld_vario`

## How wind matters today

Wind is still a separate authoritative seam.

Owners:

- `feature/flight-runtime/src/main/java/com/example/xcpro/weather/wind/data/WindSensorFusionRepository.kt`
- `feature/flight-runtime/src/main/java/com/example/xcpro/weather/wind/model/WindState.kt`
- `feature/profile/src/main/java/com/example/xcpro/weather/wind/data/WindOverrideRepository.kt`

Wind now affects the visible `ld_curr` card through the fused pilot metric:

- if wind is valid/fresh/confident, it is projected along the glide direction
- if wind is missing, stale, or low-confidence, the metric stays valid and
  falls back to `windAlong = 0`

Wind is still not patched into the old raw `currentLD` helper.

## How bugs / ballast / active polar matter today

Active-polar owner path remains:

1. `feature/profile/src/main/java/com/example/xcpro/glider/GliderRepository.kt`
2. `feature/profile/src/main/java/com/example/xcpro/glider/PolarStillAirSinkProvider.kt`
3. `core/common/src/main/java/com/example/xcpro/glider/PolarCalculator.kt`
4. `core/common/src/main/java/com/example/xcpro/glider/GliderSpeedBounds.kt`

Operative setup concepts:

- `threePointPolar`
- effective model polar
- `bugsPercent`
- `waterBallastKg`
- IAS bounds

The visible fused `ld_curr` card may use the active polar seam only for:

- plausibility bounds
- stabilization
- tightly bounded short-gap sink support when TE is briefly missing

It does not:

- directly multiply the displayed ratio by bugs or ballast factors
- directly inject raw polar values into the displayed ratio as ad hoc terms
- change the raw `currentLD` or raw `currentLDAir` formulas

## What happens while thermalling

The visible `ld_curr` card does not recompute from circling geometry.

Instead:

- while you first enter a thermal/turn/climb:
  - freeze the last valid straight-flight glide direction
  - hold the last valid `pilotCurrentLD`
  - keep the visible card subtitle at `THERMAL`
- if the non-gliding state persists past the hold timeout:
  - publish no data
  - keep the visible card subtitle at `THERMAL`
- when straight glide resumes:
  - rebuild the rolling window from fresh eligible samples
  - publish again once minimum fill is reached

So the card avoids nonsense from circles and climbs.

## What XCPro is intentionally not doing

XCPro still keeps these concepts separate:

- fused visible pilot Current L/D
- raw over-ground measured glide ratio
- raw through-air measured glide ratio
- theoretical polar L/D
- final-glide / required-glide outputs

XCPro is still not shipping:

- `currentVsPolar`
- a pure polar efficiency percentage card
- a current L/D number computed directly from circling geometry

## Questions For External Review

1. Does the visible fused `ld_curr` meaning now align with what a pilot
   expects from a release-grade Current L/D card?
2. Is the fallback ladder clear enough:
   - fused wind
   - fused zero-wind
   - short-gap polar support
   - raw ground fallback
   - hold then no data?
3. Is the current separation still correct between:
   - visible `ld_curr`
   - raw `ld_vario`
   - `polar_ld`
   - `best_ld`
4. Should future work keep `currentVsPolar` separate from the visible Current
   L/D card?
