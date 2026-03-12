# XCPro Polar Recommendations

Date: 2026-03-12

Status note:

- This recommendation note started from the pre-task-aware-glide state.
- Since then, XCPro has implemented the first racing-task finish-glide MVP:
  - `GlideTargetRepository`
  - `FinalGlideUseCase`
  - live `final_gld`, `arr_alt`, `req_alt`, and `arr_mc0`
- The remaining recommendations here still apply for later phases such as current-leg cards, selected-target generalization, task-performance cards, safety policy, and broader glide-computer coverage.
- For the current implemented task-aware glide status, use `06_XCPRO_TASK_AWARE_GLIDE_CARD_PLAN_2026-03-12.md`.

## Executive Summary

Authoritative delivery sequencing now lives in:

- `04_XCPRO_POLAR_RELEASE_PLAN_2026-03-12.md`

This document remains the recommendation and architecture note.

XCPro already has enough polar infrastructure to support:

- sink lookup
- netto
- glide-netto
- speed-to-fly

XCPro does not yet have a full glide-computer layer.

The next implementation goal should be:

- keep the current polar path
- add a dedicated final-glide and arrival-computation domain layer
- clearly separate measured L/D, polar L/D, and required L/D

## 1. Do Not Reuse Current L/D as Final Glide

Current `currentLD` is only a measured glide ratio over recent flight.

Keep it, but rename or document it as:

- `measuredLdCurrent`

Add separate outputs:

- `polarLdCurrentSpeed`
- `polarBestLd`
- `requiredGlideRatio`

Why:

- measured L/D answers "what just happened?"
- polar L/D answers "what should this glider do in still air?"
- required glide ratio answers "what does the route demand?"

## 2. Add a Dedicated Glide-Computer Domain Model

Recommended new SSOT split:

- `GliderRepository`
  - remains owner of active glider model, active polar, bugs, ballast, IAS bounds
- new glide-navigation owner
  - active target
  - target elevation
  - safety arrival height
  - altitude-source policy
  - final-glide mode settings
- new glide-computer use case
  - computes arrival, required altitude, required glide ratio, target wind component, MC0 arrival, and final-glide validity

Recommended domain output model:

```text
GlideSolution
- targetId
- valid
- reasonIfInvalid
- distanceRemainingM
- targetElevationM
- safetyArrivalHeightM
- currentNavAltitudeM
- headwindToTargetMs
- polarBestLd
- polarLdAtCurrentSpeed
- measuredLdCurrent
- requiredGlideRatio
- requiredAltitudeM
- arrivalHeightM
- arrivalHeightMc0M
- requiredSpeedToFlyMs
- onFinalGlide
```

## 3. Use One Consistent Active Polar Everywhere

The same active polar should drive:

- sink at speed
- netto
- glide-netto
- STF
- final glide
- reach calculations
- alternate sorting

Current good base:

- `StillAirSinkProvider`

Recommendation:

- extend this layer or add a higher-level `GlidePolarProvider` that can also expose:
  - sink
  - best L/D
  - L/D at speed
  - min sink speed
  - best L/D speed

## 4. Wire in a Real Final-Glide Solver

Minimum viable final-glide computation:

1. choose active target
2. compute distance and bearing to target
3. project wind along track
4. choose navigation altitude source
5. use active polar plus active MC
6. compute arrival height and required altitude
7. expose invalid states explicitly

Invalid states should include:

- no target
- no polar
- invalid altitude
- impossible groundspeed to target
- missing wind if the chosen mode requires wind

## 5. Reuse Existing Preferences Carefully

Already present:

- `macCready`
- `macCreadyRisk`
- `autoMcEnabled`

Recommendation:

- keep `macCready` as task or STF MC
- decide explicitly whether `macCreadyRisk` becomes:
  - safety MC
  - safety MC offset
  - STF risk factor

Do not leave this ambiguous. The semantics must be documented in code and UI.

## 6. Improve Polar Data Fidelity

Recommended improvements:

- consume `userCoefficients` or remove it from the model until it is real
- consume `referenceWeightKg` or remove it from the model until it is real
- replace the simple ballast penalty with a proper wing-loading shift when data allows
- replace the simple bugs multiplier with an explicit polar degradation model
- derive best L/D and minimum sink from the active polar, not only from static metadata
- validate 3-point entry more strictly
  - increasing speeds
  - finite sink values
  - realistic speed range

## 7. Add Final-Glide UI Outputs

Recommended pilot-facing values:

- arrival height
- arrival height at MC 0
- required altitude
- required glide ratio
- headwind or tailwind to target
- required speed to arrive at reserve
- final-glide valid or invalid state
- target-reach traffic-light status

Good map-specific additions:

- final-glide indicator to active task finish
- final-glide indicator to selected airport
- reach ring or shaded reachable area

## 7A. Card Wiring Advice

What can be wired immediately with the current card architecture:

- `ld_curr`
  - already live, but it is measured L/D only
- `polar_ld`
  - now live as theoretical L/D at current IAS
- `best_ld`
  - now live as best theoretical glide ratio from the active polar
- `netto`
  - already live
- `netto_avg30`
  - already live
- `levo_netto`
  - already live
- `mc_speed`
  - already live and already uses STF outputs

What should be added as new card fields once the domain outputs exist:

- `polar_ld`
  - theoretical L/D at current speed
- `best_ld`
  - best glide ratio from active polar
- `req_gr`
  - required glide ratio to active target
- `arr_alt`
  - arrival height
- `arr_mc0`
  - arrival height at MC 0
- `req_alt`
  - required altitude
- `goal_wind`
  - along-track headwind or tailwind to target

What should not be done:

- do not calculate final-glide values inside `CardFormatSpec`
- do not let cards query task managers directly
- do not reinterpret `currentLD` as final glide

Recommended integration point:

- keep `CardFormatSpec` as pure formatting only
- extend the app-side adapter layer that builds `RealTimeFlightData`, or add a dedicated card-side glide snapshot that is populated from a combined use case
- perform the join in the observer or use-case layer, not in cards or Compose

Current hard constraint:

- `RealTimeFlightData` does not yet contain waypoint distance, bearing, ETA, arrival height, required glide ratio, or task-state fields
- therefore `final_gld`, `wpt_*`, and `task_*` cards cannot be wired correctly without first extending the card-feed contract

Near-term practical recommendation:

- keep `final_gld` and task cards out of shipped presets until they have real backing data
- use `mc_speed`, `ld_curr`, `polar_ld`, `best_ld`, `netto`, and `levo_netto` as the current live polar-related card set

## 8. Suggested Architecture Placement

Keep the architecture split explicit:

- UI
  - renders final-glide outputs and warnings
- ViewModel
  - selects target and combines use-case outputs
- domain
  - owns all final-glide math and policy
- data
  - provides glider config, wind state, target data, terrain data, and settings

Do not put final-glide formulas in Compose, cards, or map rendering code.

## 9. Suggested Implementation Order

This section is conceptual only.

For release sequencing, phase gates, and rollback, use:

- `04_XCPRO_POLAR_RELEASE_PLAN_2026-03-12.md`

### Phase 1: Clarify metrics

- keep measured L/D
- add theoretical polar L/D
- add required glide ratio

### Phase 2: Add target-aware final glide

- airport or selected waypoint first
- arrival height
- required altitude
- final-glide validity
- live `final_gld`, `wpt_dist`, `wpt_brg`, and `wpt_eta` card backing data

### Phase 3: Add task-aware outputs

- task finish arrival height
- task MC0 arrival
- required speed to consume surplus height
- live `task_spd`, `task_dist`, and `start_alt` card backing data

### Phase 4: Add safety and reach

- safety arrival height
- safety MC or risk mode
- landable reach and alternate sorting

### Phase 5: Hardening

- deterministic unit tests
- replay tests
- terrain and wind edge-case tests

## 10. Tests Needed

Unit tests:

- polar best L/D derivation
- L/D at current speed
- arrival height in still air
- headwind and tailwind effects
- safety-height handling
- invalid-state handling
- MC 0 and safety MC comparisons

Replay tests:

- same replay input gives same arrival output
- target changes do not corrupt state
- final-glide status transitions are stable

## 11. Recommended Near-Term Deliverable

Best next practical deliverable:

- add a domain-level `FinalGlideUseCase`
- feed it from active polar, wind, altitude, and selected target
- publish:
  - `arrivalHeightM`
  - `requiredAltitudeM`
  - `requiredGlideRatio`
  - `headwindToTargetMs`
  - `finalGlideValid`

That will move XCPro from "polar-aware vario/STF" to the first real step of a glider computer.
