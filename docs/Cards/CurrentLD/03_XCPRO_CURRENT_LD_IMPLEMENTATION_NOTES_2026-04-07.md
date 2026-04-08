# XCPro Current L/D Option Analysis

Date: 2026-04-07

## Purpose

This note no longer decides whether XCPro should split the concepts.

That additive split now exists in branch truth:

- `ld_curr`
  - recent measured over-ground glide ratio
- `ld_vario`
  - measured through-air glide ratio from chosen true airspeed and TE vario

This note now records what that means for later product choices.

## Current branch baseline

Today XCPro ships:

- card ID `ld_curr`
- runtime fields `currentLD/currentLDValid`
- a recent measured metric based on GPS path distance over barometric altitude
  loss
- no explicit wind input
- card ID `ld_vario`
- runtime fields `currentLDAir/currentLDAirValid`
- a through-air measured metric based on chosen true airspeed over TE sink
- no direct polar comparison metric yet

Any future choice should preserve the distinction between:

- current measured glide performance
- polar L/D
- best L/D
- required/final-glide metrics

## Option A: Keep `ld_curr` as recent measured over-ground glide ratio

### Pilot meaning

- "How is my recent glide going over the ground right now?"

### Inputs required

- recent GPS path or groundspeed-derived distance
- altitude loss
- a recent time or distance window

### Wind role

- wind affects the result indirectly through the ground path
- no explicit `WindState` input is required

### Compatibility impact

- lowest risk
- keeps the meaning of the shipped card
- no card ID migration

### Architecture impact

- no owner move required
- local hardening in the existing helper/runtime path is sufficient

### Confusion risk

- moderate
- some pilots may read `L/D` as aerodynamic efficiency and not realize that the
  current number is ground-result oriented

## Option B: Redefine `ld_curr` as wind-aware or through-air

### Pilot meaning

- "How efficient is the aircraft through the air right now?"

### Inputs required

- air-reference contract such as TAS or another authoritative airspeed signal
- possibly explicit wind compensation if TAS is not directly authoritative
- altitude loss or sink over a matched recent window

### Wind role

- yes, wind becomes part of the derivation or compensation path

### Compatibility impact

- high risk
- changes the meaning of an existing shipped card
- breaks continuity with prior logs, expectations, and screenshots

### Architecture impact

- larger slice
- would touch the current L/D owner path, documentation, and test contract
- likely needs stronger source/validity wording around TAS and wind

### Confusion risk

- high unless renamed carefully
- can drift too close to `polar_ld` if the product wording is not precise

## Option C: Keep `ld_curr` and add a second through-air metric

### Pilot meaning

- `ld_curr` keeps the over-ground recent-performance meaning
- a new metric carries the air-referenced or wind-aware meaning

### Inputs required

- existing `currentLD` path remains unchanged
- the new metric would require a dedicated TAS / wind / sink contract

### Wind role

- `ld_curr`: no explicit wind input
- new metric: yes, explicit wind or air-reference contract as required

### Compatibility impact

- safest additive path
- preserves existing card semantics
- now implemented in branch as `ld_vario`

### Architecture impact

- additive new upstream metric
- avoids mutating the existing `currentLD` contract in place
- keeps measured over-ground and measured through-air ownership separate

### Confusion risk

- lower than Option B if naming is strong
- higher surface area because pilots will see two glide-efficiency style
  numbers

## Current branch status

Current branch truth is now closest to Option C:

- `ld_curr` remains the over-ground measured card
- `ld_vario` is now the additive through-air measured card
- `currentVsPolar` is still not implemented
- bugs and ballast still belong only to the active polar path

## What should stay true under any option

- `ld_curr` should remain runtime-owned, not card-owned
- `polar_ld` should remain theoretical still-air L/D from the active polar
- `best_ld` should remain the best still-air L/D from the active polar
- required/final-glide metrics should remain navigation/glide-solver outputs
- bugs and ballast should remain part of the active polar path, not silently
  mixed into a measured glide metric

## External-review focus

The main product question is not:

- "Can wind influence the number?"

The real question is:

- "How should XCPro explain and present two different measured glide ratios?"

That decision now determines:

- whether current labels are clear enough
- whether `ld_vario` should stay separate from any future current-vs-polar
  metric
- whether future product work should add a third comparison metric instead of
  overloading either measured metric
