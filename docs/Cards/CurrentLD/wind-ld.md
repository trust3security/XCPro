# Wind And Current L/D Release-Grade Decision Brief

This file is the wind-specific handoff brief for external review.

Use it when the product question is:

- "how should wind be part of Current L/D?"
- "what is the professional release-grade way to start?"
- "how do we avoid a churn-heavy or ad hoc implementation?"

## Branch truth first

Current XCPro already ships two different measured glide metrics:

- `ld_curr`
  - title: `L/D CURR`
  - meaning: recent measured over-ground glide ratio
  - owner path:
    1. `feature/flight-runtime/src/main/java/com/example/xcpro/sensors/FlightCalculationHelpers.kt`
    2. `feature/flight-runtime/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsRuntime.kt`
    3. `feature/flight-runtime/src/main/java/com/example/xcpro/flightdata/FlightDisplayMapper.kt`
    4. `feature/map/src/main/java/com/example/xcpro/MapScreenUtils.kt`
    5. `dfcards-library/src/main/java/com/example/dfcards/CardFormatSpec.kt`
  - branch math:
    - recent GPS-path displacement
    - divided by barometric altitude loss
- `ld_vario`
  - title: `L/D VARIO`
  - meaning: measured through-air glide ratio
  - owner path:
    1. `feature/flight-runtime/src/main/java/com/example/xcpro/sensors/domain/CurrentAirLdCalculator.kt`
    2. `feature/flight-runtime/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsRuntime.kt`
    3. `feature/flight-runtime/src/main/java/com/example/xcpro/flightdata/FlightDisplayMapper.kt`
    4. `feature/map/src/main/java/com/example/xcpro/MapScreenUtils.kt`
    5. `dfcards-library/src/main/java/com/example/dfcards/CardFormatSpec.kt`
  - branch math:
    - `currentLDAir = trueAirspeedMs / (-teVario)`

Wind, bugs, ballast, and active-polar math are still separate seams:

- wind owner:
  - `feature/flight-runtime/src/main/java/com/example/xcpro/weather/wind/data/WindSensorFusionRepository.kt`
  - `feature/flight-runtime/src/main/java/com/example/xcpro/weather/wind/model/WindState.kt`
- active-polar owner:
  - `feature/profile/src/main/java/com/example/xcpro/glider/GliderRepository.kt`
  - `feature/profile/src/main/java/com/example/xcpro/glider/PolarStillAirSinkProvider.kt`
  - `core/common/src/main/java/com/example/xcpro/glider/PolarCalculator.kt`
  - `core/common/src/main/java/com/example/xcpro/glider/GliderSpeedBounds.kt`

## What `ld_curr` does not use today

Current `ld_curr` does not directly use:

- wind speed
- wind direction
- raw `WindState`
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

So "add wind into Current L/D" is not a small bug fix.
It is a product-semantic change.

## What "add wind into Current L/D" can mean

This phrase can mean two different things:

### Option 1: redefine shipped `ld_curr`

This would mean:

- the existing `L/D CURR` card stops being over-ground measured glide ratio
- wind or air-relative logic gets folded into the shipped card
- the existing owner path changes meaning in place

### Option 2: keep `ld_curr` and add a separate wind-aware concept

This would mean:

- `ld_curr` stays the measured over-ground recent glide ratio
- wind-aware or air-relative behavior stays additive
- the existing measured ground metric and card semantics remain stable

These options are not equivalent.

## Direct comparison

| Topic | Redefine `ld_curr` | Keep `ld_curr`, add separate wind-aware concept |
|---|---|---|
| Pilot meaning | changes shipped meaning in place | preserves existing meaning |
| Backward compatibility | high risk | low risk |
| Review churn | high | lower |
| Architecture churn | high because seams get mixed | lower because seams stay separate |
| Replay/test burden | higher because existing expected outputs change | lower because existing outputs remain stable |
| Risk of confusion | high unless labels and docs all change | lower if metrics stay distinct |
| Fit with current branch | poor | strong |
| Release-grade recommendation | no | yes |

## Release-grade recommendation

The professional no-churn starting point is:

- do not patch raw wind into `FlightCalculationHelpers.calculateCurrentLD(...)`
- do not silently change the meaning of shipped `ld_curr`
- do not mix wind ownership into the old helper-owned ground metric
- keep `ld_curr` as the measured over-ground metric
- keep wind-aware behavior additive and upstream-owned

In current branch terms, the release-grade starting point is:

- use `ld_curr` for measured over-ground glide ratio
- use `ld_vario` for measured through-air glide ratio
- if product still wants a third concept, define it explicitly instead of
  mutating either metric in place

## Why raw wind speed and direction are not enough

Wind speed and direction matter, but they are not sufficient by themselves for
a professional Current L/D metric.

By themselves they do not define:

- whether the pilot wants an over-ground metric or a through-air metric
- whether the metric should be measured or polar-comparison based
- whether the metric should react to vertical air movement
- whether stale or low-confidence wind should invalidate the result
- whether GPS fallback should be allowed

So a release-grade design should not start from:

- "just subtract wind from groundspeed"
- "just add wind speed and direction into `currentLD`"
- "just reuse the polar and call that Current L/D"

## Where wind, bugs, ballast, and polar belong today

### Wind

Wind is already owned separately.

It currently affects:

- wind display
- wind-aware chosen airspeed selection
- glide/final-glide consumers
- sometimes `ld_vario` indirectly, because `ld_vario` uses the chosen
  authoritative airspeed seam

It does not directly affect:

- `ld_curr`

### Bugs and ballast

Bugs and ballast are already part of the active-polar seam.

Active runtime fields include:

- `bugsPercent`
- `waterBallastKg`
- `threePointPolar`
- effective model polar
- `iasMinMs`
- `iasMaxMs`

They currently affect:

- `sinkAtSpeed(...)`
- `ldAtSpeed(...)`
- `bestLd()`
- final-glide and still-air glide computations

They do not directly affect:

- `ld_curr`
- `ld_vario`

### What this means

If the goal is:

- "Current L/D should change because wind changes"
- "Current L/D should change because bugs or ballast changed"
- "Current L/D should show how well I am doing versus my glider polar"

then the likely target concept is not current branch `ld_curr`.

It is either:

- through-air measured glide ratio
- or current performance versus active polar
- or both

## Professional / no-churn rules

If Codex is later asked to implement this professionally, the implementation
should follow these rules:

- do not semantically mutate shipped `ld_curr` without an explicit product
  decision
- do not pull raw `WindState` math into `FlightCalculationHelpers`
- do not pull bugs, ballast, or active-polar math into `currentLD`
- do not compute metric math in cards or UI formatting
- do not add per-screen wind correction logic
- keep all new metric ownership upstream in runtime/domain seams
- preserve replay determinism and explicit validity/no-data behavior
- keep measured metrics separate from theoretical polar metrics

## What a more real wind-aware glide metric needs

If the product still wants a more "real" Current L/D, the minimum release-grade
inputs are closer to:

- chosen non-GPS airspeed source
- `trueAirspeedMs`
- `tasValid`
- `teVario`
- flying state
- straight-flight gates
- explicit no-data behavior when air-data or wind-quality contracts are not
  authoritative

What is not sufficient on its own:

- raw wind speed
- raw wind direction
- GPS groundspeed alone
- polar L/D alone
- bugs/ballast config alone

## Testing strategy for a real glide simulation

If XCPro later builds a realistic glide validation harness, the harness should:

- simulate A-to-B glide geometry
- simulate barometric altitude decay
- simulate airspeed and TE availability
- simulate wind
- apply chosen glider profile, bugs, ballast, and active polar to the flight
  model
- run the result through the normal replay/runtime path

The assertions should stay split by concept:

- `ld_curr`
  - measured over-ground glide from resulting path geometry
- `ld_vario`
  - measured through-air glide from chosen airspeed and TE sink
- `polar_ld` / `best_ld`
  - theoretical still-air polar outputs
- future current-vs-polar metric
  - only if product explicitly adds it

The harness should not pretend that current branch `ld_curr` already reads
wind, bugs, ballast, or polar directly.

## Questions for ChatGPT Pro

Please answer these from the branch truth above:

1. Should XCPro keep `ld_curr` exactly as the measured over-ground metric?
2. If wind must influence pilot-facing glide efficiency, should that stay
   additive rather than mutating `ld_curr`?
3. Is the current split between `ld_curr` and `ld_vario` already the correct
   release-grade foundation?
4. If product still wants a more "real" Current L/D, should that be defined as:
   - over-ground measured glide ratio
   - through-air measured glide ratio
   - current performance versus active polar
   - or a deliberate pair of separate metrics?
5. For a realistic glide simulation harness, what should XCPro assert
   separately for:
   - over-ground measured glide
   - through-air measured glide
   - active-polar prediction
   - any future current-vs-polar comparison?
