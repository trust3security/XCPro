# XCPro Current L/D Implementation Audit

Date: 2026-04-07

## Purpose

This note records the branch-truth implementation of XCPro's existing
over-ground Current L/D metric and the additive through-air metric that now
ships beside it.

This is an audit, not a proposal.

## Current user-facing cards

Over-ground measured card:

- Card ID: `ld_curr`
- Card title: `L/D CURR`

Through-air measured card:

- Card ID: `ld_vario`
- Card title: `L/D VARIO`

Shared card catalog owner:

- `dfcards-library/src/main/java/com/example/dfcards/CardLibraryPerformanceCatalog.kt`

Known card IDs:

- `dfcards-library/src/main/java/com/example/dfcards/KnownCardId.kt`
- `dfcards-library/src/main/java/com/example/dfcards/CardId.kt`

Current catalog descriptions:

- `ld_curr`
  - "Measured glide ratio over the recent flight path"
- `ld_vario`
  - "Measured through-air glide ratio from true airspeed and TE vario"

Those descriptions now distinguish measured over-ground versus measured
through-air behavior without implying theoretical polar performance.

## Current authoritative owner paths

Current over-ground owner path:

1. `feature/flight-runtime/.../FlightCalculationHelpers.kt`
   - `calculateCurrentLD(...)` computes the measured value
2. `feature/flight-runtime/.../CalculateFlightMetricsRuntime.kt`
   - sets `currentLDValid`
3. `feature/flight-runtime/.../FlightDisplayMapper.kt`
   - maps `currentLD` and `currentLDValid` into `CompleteFlightData`
4. `feature/map/.../MapScreenUtils.kt`
   - maps those fields into `RealTimeFlightData`
5. `dfcards-library/.../CardFormatSpec.kt`
   - formats the `ld_curr` card

Current through-air owner path:

1. `feature/flight-runtime/.../CurrentAirLdCalculator.kt`
   - computes the pure formula + validity gates
2. `feature/flight-runtime/.../CalculateFlightMetricsRuntime.kt`
   - feeds authoritative TAS / TE / straight-flight inputs and publishes
     `currentLDAir/currentLDAirValid`
3. `feature/flight-runtime/.../FlightDisplayMapper.kt`
   - maps `currentLDAir` and `currentLDAirValid` into `CompleteFlightData`
4. `feature/map/.../MapScreenUtils.kt`
   - maps those fields into `RealTimeFlightData`
5. `dfcards-library/.../CardFormatSpec.kt`
   - formats the `ld_vario` card

This still matches the architectural rule that the card layer formats only and
does not own metric math.

## Current seam split

Current over-ground measured-L/D seam:

`FlightCalculationHelpers.calculateCurrentLD(...) -> currentLD/currentLDValid -> ld_curr`

Current through-air measured-L/D seam:

`CurrentAirLdCalculator.calculateCurrentAirLd(...) -> currentLDAir/currentLDAirValid -> ld_vario`

Current wind seam:

`WindSensorFusionRepository.windState -> GlideComputationRepository / MapScreenObservers`

That split matters:

- `currentLD` is helper-owned measured glide state
- `currentLDAir` is runtime-owned air-data glide state
- wind is not stored in `CompleteFlightData`
- wind is joined later for glide solving and UI/display consumers

## What the current runtime actually computes

Today, `calculateCurrentLD(...)` uses:

- recent GPS position history to estimate distance traveled
- current altitude and previously stored altitude to estimate altitude lost

Current branch details:

- recompute interval: `5000 ms`
- movement gate:
  - distance traveled must exceed `10 m`
  - altitude lost must exceed `0.5 m`
- when a fresh value is computed, it is clamped into `5f..100f`
- otherwise the helper returns the held value

This makes the metric:

- recent
- measured
- path-based
- ground-track influenced

It does not make it a theoretical still-air L/D metric.

## What the additive air-relative runtime computes

Today, `currentLDAir` uses:

- chosen true airspeed from the authoritative runtime airspeed seam
- total-energy vario from the authoritative TE seam
- straight-flight gates from the existing circling / turning owner path

Current branch details:

- formula:
  - `currentLDAir = trueAirspeedMs / (-teVario)`
- no hold-last-value helper state
- invalid on GPS fallback airspeed
- invalid when TE is disabled or unavailable
- invalid while circling or turning
- invalid when the aircraft is not flying

This makes the metric:

- recent
- measured
- through-air
- non-polar
- explicitly separate from `ld_curr`

## What the metric explicitly does not use

Current XCPro `currentLD` does not use:

- `WindState`
- wind vector compensation
- TAS
- `StillAirSinkProvider`
- polar sink
- `polar_ld`
- `best_ld`
- `bugsPercent`
- `waterBallastKg`
- `referenceWeightKg`
- `userCoefficients`

So wind, polar, and General Polar configuration are not part of the current
owner contract for `ld_curr`.

Current XCPro `currentLDAir` also does not use:

- raw `WindState`
- direct wind-vector subtraction inside the metric owner
- `StillAirSinkProvider`
- `polar_ld`
- `best_ld`
- `bugsPercent`
- `waterBallastKg`
- `referenceWeightKg`
- `userCoefficients`

So `ld_vario` is also a measured metric, not a polar-comparison metric.

## Where wind actually lives today

Wind is kept in a separate owner path:

- fused runtime wind state:
  `feature/flight-runtime/.../WindSensorFusionRepository.kt`
- wind model:
  `feature/flight-runtime/.../weather/wind/model/WindState.kt`
- persisted manual override:
  `feature/profile/.../WindOverrideRepository.kt`
- transient external override:
  `feature/profile/.../WindOverrideRepository.kt`

Current branch detail:

- `WindSensorFusionRepository` owns authoritative runtime `windState`
- manual wind is persisted and replayed through the override source
- external wind is transient and not persisted
- `GlideComputationRepository` reads `windState` for glide solving
- `MapScreenObservers` applies `windState` into `RealTimeFlightData` display
  fields
- `MapScreenUtils` explicitly documents that wind is sourced from `WindState`
  only and that `CompleteFlightData` no longer carries wind

That means any future change that pulls wind into `currentLD` would be a
deliberate semantic redesign, not a small bug fix.

## Validity contracts

Current validity is owned upstream in `CalculateFlightMetricsRuntime.kt`, not
in the card formatter.

Current `ld_curr` contract:

- `currentLDValid = calculatedLD.isFinite() && calculatedLD > 0f`

Current `ld_vario` contract:

- valid only when:
  - aircraft is flying
  - `tasValid == true`
  - chosen airspeed source is not GPS fallback
  - `trueAirspeedMs` is finite and `> 5`
  - `teVario` is finite
  - `teSinkMs = -teVario` is finite and `> 0.15`
  - not circling
  - not turning
- otherwise:
  - `currentLDAir = 0f`
  - `currentLDAirValid = false`

The card formatter respects those explicit owner flags:

- `ld_curr`
  - valid -> show `<value>:1` with secondary `LIVE`
  - invalid -> show `--:1` with secondary `NO DATA`
- `ld_vario`
  - valid -> show `<value>:1` with secondary `LIVE`
  - invalid -> show `--:1` with secondary `NO DATA`

That is important because the card layer is not inventing its own local
heuristics.

## Current model wiring

Current shared DTO field names:

- `CompleteFlightData.currentLD`
- `CompleteFlightData.currentLDValid`
- `CompleteFlightData.currentLDAir`
- `CompleteFlightData.currentLDAirValid`
- `RealTimeFlightData.currentLD`
- `RealTimeFlightData.currentLDValid`
- `RealTimeFlightData.currentLDAir`
- `RealTimeFlightData.currentLDAirValid`

Current pipeline docs already describe this as a measured glide ratio:

- `docs/ARCHITECTURE/PIPELINE.md`
- `docs/ARCHITECTURE/GLIDE_COMPUTER_METRIC_CONTRACT_MATRIX_2026-03-27.md`

## Current implementation notes worth separating from semantics

- The helper clamp/storage bug in `calculateCurrentLD(...)` was fixed in Phase
  1 and is no longer a live branch bug.
- `currentLD` still uses a coarse ground-distance versus barometric-loss
  sample and is intentionally distinct from the newer air-relative metric.
- `currentVsPolar` is still not implemented.

## Current template exposure

The shipped presets already use the existing card:

- `Cross Country` includes `ld_curr`
- `Performance` includes `ld_curr`

The new air-relative card is registered and selectable, but it is not added to
the shipped presets by default in this branch.

Source:

- `dfcards-library/src/main/java/com/example/dfcards/FlightTemplates.kt`

## What this metric is not

Current XCPro `currentLD` is not:

- `polar_ld`
- `best_ld`
- `final_gld`
- waypoint required glide ratio
- task required glide ratio

Current XCPro `currentLDAir` is also not:

- `polar_ld`
- `best_ld`
- `final_gld`
- a required glide ratio
- a current-vs-polar performance percentage

So the branch now exposes two measured glide metrics plus separate theoretical
polar and final-glide seams.

## Audit conclusion

Branch truth today is:

- XCPro ships `ld_curr` as the recent measured over-ground glide ratio
- XCPro also ships `ld_vario` as a separate through-air measured glide ratio
- `ld_curr` remains closer to a measured glide ratio over ground than to a
  strict air-referenced lift-to-drag ratio
- `ld_vario` uses chosen true airspeed and TE vario, not raw wind or active
  polar math
- `currentVsPolar` is still deferred

That distinction should be preserved in docs and any later hardening work.
