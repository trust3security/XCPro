# XCPro General Polar + Current L/D Seam Audit

Date: 2026-04-07

## Purpose

Capture the current branch-truth for:

- what General Polar fields are operative in XCPro today
- where bugs and ballast actually affect runtime math
- what `currentLD` / `ld_curr` really means
- what `currentLDAir` / `ld_vario` now mean
- what inputs are required to calculate Current L/D in the current owner path

This is an audit note, not a future-state design doc.

## Short answer

- `ld_curr` is already implemented and shipped.
- `ld_curr` is not a polar-based metric.
- `ld_curr` is a recent measured glide ratio from recent flight path geometry.
- `ld_vario` is now also implemented and shipped as a separate through-air
  measured metric.
- bugs and ballast affect the active polar path, not `currentLD`.
- `referenceWeightKg` and `userCoefficients` are currently stored but deferred from authoritative runtime math.

## General Polar: active runtime authority

Authoritative owner path:

`GliderRepository -> PolarStillAirSinkProvider`

Relevant current files:

- `feature/profile/src/main/java/com/trust3/xcpro/glider/GliderRepository.kt`
- `feature/profile/src/main/java/com/trust3/xcpro/glider/PolarStillAirSinkProvider.kt`
- `core/common/src/main/java/com/trust3/xcpro/glider/PolarCalculator.kt`
- `core/common/src/main/java/com/trust3/xcpro/glider/GliderSpeedBounds.kt`

Current source priority:

1. manual `threePointPolar` when valid
2. selected model polar when usable
3. fallback model when needed

## What General Polar fields are operative today

These fields affect authoritative runtime polar math today:

- selected/effective glider model
- `threePointPolar`
- `bugsPercent`
- `waterBallastKg`
- `iasMinMs`
- `iasMaxMs`

What each one does:

- `threePointPolar`
  - overrides the selected model polar for sink, polar L/D, best L/D, and final-glide solves
- `bugsPercent`
  - increases sink through the active sink-adjustment path
- `waterBallastKg`
  - contributes to wing loading and also adds the current ballast penalty in the sink-adjustment path
- `iasMinMs` / `iasMaxMs`
  - bound the active IAS scan/range used by the polar metrics and glide solvers

Current code evidence:

- `PolarCalculator.sinkMs(...)` uses `threePointPolar` first, then model polar
- `PolarCalculator.applyAdjustments(...)` applies `bugsPercent` and `waterBallastKg`
- `GliderSpeedBoundsResolver.resolveIasBoundsMs(...)` applies `iasMinMs` and `iasMaxMs`

## What General Polar fields are not operative today

These fields are persisted/configured but not part of the authoritative runtime polar path:

- `referenceWeightKg`
- `userCoefficients`

Current branch-truth:

- they are carried in `GliderConfig`
- they are surfaced in `ActivePolarSnapshot` as configured flags
- they are explicitly excluded from the active runtime sink-provider contract
- existing tests confirm they do not currently change sink or IAS bounds

Also note:

- `ballastDrainMinutes` is ballast UI/control behavior, not polar math
- `hideBallastPill` is UI behavior, not polar math

## Where wind is kept

Wind is not part of the active General Polar config.

Current wind owner path:

- fused runtime wind SSOT:
  `feature/flight-runtime/.../WindSensorFusionRepository.kt`
- wind model:
  `feature/flight-runtime/.../weather/wind/model/WindState.kt`
- persisted manual override:
  `feature/profile/.../WindOverrideRepository.kt`
- transient external override:
  `feature/profile/.../WindOverrideRepository.kt`

Current consumers:

- `GlideComputationRepository`
- `MapScreenObservers`

Important branch detail:

- wind is sourced from `WindState`
- `CompleteFlightData` no longer carries wind

## Where bugs and ballast matter

In today's branch, bugs and ballast matter for polar-derived outputs, including:

- `polar_ld`
- `best_ld`
- `netto`
- `levo_netto`
- `mc_speed`
- final glide / arrival / required altitude solves

Reason:

- these paths all consume the shared IAS-based `StillAirSinkProvider`
- that provider reads `GliderRepository.effectiveModel` and `GliderRepository.config`
- sink and L/D math then flow through `PolarCalculator`

## What Current L/D is in XCPro

Current L/D in XCPro is:

- card ID: `ld_curr`
- title: `L/D CURR`
- runtime fields: `currentLD` and `currentLDValid`

Meaning:

- recent measured glide ratio over the recent flight path

It is not:

- polar L/D at the current airspeed
- best polar L/D
- required L/D to a target
- a final-glide metric
- a bugs/ballast-adjusted theoretical value

## What L/D VARIO is in XCPro

The additive air-relative metric in XCPro is:

- card ID: `ld_vario`
- title: `L/D VARIO`
- runtime fields: `currentLDAir` and `currentLDAirValid`

Meaning:

- measured through-air glide ratio from chosen true airspeed and TE vario

It is not:

- `ld_curr`
- `polar_ld`
- `best_ld`
- required glide
- final glide
- a current-vs-polar comparison metric

## What is required to calculate Current L/D today

The current helper path requires:

- recent GPS location history
- current GPS position
- current altitude sample
- sample time
- enough recent movement to produce a meaningful geometry sample

Current thresholds and behavior:

- recompute gate: 5 seconds
- bootstrap sentinel: first accepted sample returns `0f`
- distance requirement: `> 10 m`
- altitude-loss requirement: `> 0.5 m`
- output validity in runtime: finite and `> 0f`

Current owner path:

1. `FlightCalculationHelpers.calculateCurrentLD(...)`
2. `CalculateFlightMetricsRuntime`
3. `FlightDisplayMapper`
4. `MapScreenUtils`
5. `CardFormatSpec` formatting for `ld_curr`

Altitude source used today:

- `CalculateFlightMetricsRuntime` passes `baroAltitude` into `calculateCurrentLD(...)`

So the current metric is effectively:

`recent GPS ground distance / recent barometric altitude loss`

## Current seam split

Non-polar measured glide path:

`FlightCalculationHelpers.calculateCurrentLD(...) -> currentLD/currentLDValid -> ld_curr`

Polar-derived path:

`GliderRepository -> PolarStillAirSinkProvider -> polar L/D / best L/D / netto / STF / final glide`

This split is correct and should stay explicit.

## Why this matters for Current L/D

Current XCPro already separates:

- polar configuration and sink math
- wind ownership and glide/UI consumers
- measured current-L/D helper state

So if XCPro later decides that wind should belong in a Current L/D style
metric, that would be a deliberate semantic/product change.

It would not be:

- a simple clamp bug fix
- a General Polar change
- a formatter-only change

Open design question for later work:

- should wind stay outside `currentLD`
- or should XCPro add a second, explicitly air-referenced metric

## Audit findings

### 1. Clamp persistence bug in helper state

This bug was fixed in Phase 1.

Current branch truth:

- the helper now stores the same clamped `currentLD` value that it returns
- held values no longer leak an unclamped spike

### 2. Numerator and denominator are only loosely aligned

The current implementation mixes:

- ground distance to an older history point
- altitude loss since the last accepted altitude baseline

That still produces a useful recent measured glide ratio, but it is not a tightly windowed sample.

Implication:

- the metric is good as a practical recent-performance indicator
- it should not be described as a precise air-performance solver metric

### 3. Current-vs-polar remains intentionally separate

Current branch truth:

- XCPro now has two measured glide metrics:
  - `ld_curr`
  - `ld_vario`
- XCPro still does not implement a public current-vs-polar metric

Implication:

- bugs and ballast remain in the polar seam only
- measured metrics stay distinct from theoretical or comparison metrics

## Implementation guidance

If the product question is:

"Should bugs and ballast be included in Current L/D?"

Current branch answer:

- no, not for `ld_curr`

Reason:

- `ld_curr` is the measured recent glide ratio
- bugs and ballast belong in the theoretical active-polar path
- folding them into `currentLD` would change the meaning of the shipped metric

If the product wants a new metric such as:

- current efficiency versus active polar
- current achieved glide versus theoretical glide

then add a separate upstream metric instead of changing `ld_curr`.

Current branch note:

- the first additive metric now exists as `ld_vario`
- any future current-vs-polar metric should still be separate from both
  `ld_curr` and `ld_vario`

If the product question becomes:

"Should wind be included in Current L/D?"

Current branch answer is:

- not in today's `currentLD` contract

That decision remains open product work, not current branch truth.

## Recommended follow-up work

1. Keep `ld_curr` explicitly documented as non-polar in card and architecture docs.
2. Keep `ld_vario` explicitly documented as measured through-air, not polar.
3. Keep bugs and ballast in the active polar path only.
4. If needed, add a separate polar-aware comparison metric rather than mutating `currentLD` or `currentLDAir`.
