# Current L/D Review Questions

Date: 2026-04-07

## Purpose

This note is written for external review.

The goal is to let a reviewer answer how XCPro should present and evolve its
measured glide metrics without reverse-engineering the codebase from scratch.

## Current XCPro branch truth

- XCPro already ships `Current L/D` as card `ld_curr`
- card title: `L/D CURR`
- runtime fields: `currentLD` and `currentLDValid`
- current metric meaning:
  - recent GPS-path distance over barometric altitude loss
- XCPro also ships `L/D VARIO` as card `ld_vario`
- runtime fields: `currentLDAir` and `currentLDAirValid`
- additive air-relative metric meaning:
  - chosen true airspeed divided by TE sink
- current metric does not explicitly use:
  - `WindState`
  - TAS
  - polar sink
  - bugs or ballast
- `ld_vario` does not explicitly use:
  - raw `WindState`
  - polar sink
  - bugs or ballast
  - any current-vs-polar comparison path

So current XCPro now exposes:

- `ld_curr`
  - recent measured path-performance metric over ground
- `ld_vario`
  - recent measured through-air metric from TAS and TE vario

Neither is a polar or final-glide metric.

## Wind owner path in XCPro

Wind is already owned separately from `currentLD`.

Current owner path:

- fused wind SSOT:
  `feature/flight-runtime/.../WindSensorFusionRepository.kt`
- wind model:
  `feature/flight-runtime/.../weather/wind/model/WindState.kt`
- persisted manual override:
  `feature/profile/.../WindOverrideRepository.kt`
- transient external override:
  `feature/profile/.../WindOverrideRepository.kt`
- glide consumers:
  `feature/map-runtime/.../GlideComputationRepository.kt`
- UI/card display join:
  `feature/map/.../MapScreenObservers.kt`

Important branch detail:

- `CompleteFlightData` no longer carries wind
- wind is joined later from `WindState`

## Glossary

- Measured over-ground glide ratio:
  recent ground distance divided by altitude loss
- Measured through-air glide ratio:
  recent air distance divided by altitude loss
- Polar L/D:
  theoretical still-air glide ratio from the active polar
- Best L/D:
  best theoretical still-air glide ratio from the active polar
- Required L/D:
  navigation demand to reach a target with available height

## Open product decisions

- Should XCPro's `Current L/D` remain an over-ground measured metric?
- Is the new additive split between `ld_curr` and `ld_vario` clear enough?
- Should `ld_vario` keep its current label or use different pilot-facing
  wording?
- If XCPro later adds a current-vs-polar metric, how should that remain
  distinct from both measured metrics?

## Questions to answer

1. In mature glider-computer practice, is XCPro's current split coherent:
   - `ld_curr` as over-ground measured glide ratio
   - `ld_vario` as through-air measured glide ratio?
2. Is `L/D VARIO` the right pilot-facing label for the additive air-relative
   metric, or should XCPro use different wording?
3. How should XCPro clearly distinguish:
   - `currentLD`
   - `currentLDAir`
   - `polar_ld`
   - `best_ld`
   - required/final-glide metrics
4. If XCPro later adds a third glide-efficiency style metric, should that be:
   - a current-vs-polar metric
   - an explicit efficiency/comparison metric
   - something else

## Desired output from review

An external review is most useful if it gives:

- a recommended pilot-facing explanation of `ld_curr` versus `ld_vario`
- whether the current labels are clear enough
- whether wind should stay implicit in the chosen-airspeed seam or surface more
  explicitly
- whether XCPro should stop at the current two-metric split or add a later
  comparison metric
- suggested naming that reduces confusion with:
  - `polar_ld`
  - `best_ld`
  - required/final-glide metrics
