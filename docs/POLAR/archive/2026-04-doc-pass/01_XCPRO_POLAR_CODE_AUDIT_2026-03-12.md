# XCPro Polar Code Audit

Date: 2026-03-12

Status note:

- This document records the pre-task-aware-glide audit baseline.
- Since this audit, XCPro has added a racing-task finish-glide MVP:
  - `final_gld`
  - `arr_alt`
  - `req_alt`
  - `arr_mc0`
- For current task-aware glide status, use `06_XCPRO_TASK_AWARE_GLIDE_CARD_PLAN_2026-03-12.md`.

## Scope

This note answers two questions:

1. Where is the glider polar set in XCPro today?
2. What parts of the live flight pipeline already depend on it?

## Current SSOT and Ownership

Authoritative owners:

- Glider model catalog:
  - `core/common/src/main/java/com/trust3/xcpro/common/glider/GliderModels.kt`
- Active selected model, effective model, fallback state, and per-profile glider config:
  - `feature/profile/src/main/java/com/trust3/xcpro/glider/GliderRepository.kt`
- MacCready and Auto MC preferences:
  - `feature/profile/src/main/java/com/trust3/xcpro/vario/LevoVarioPreferencesRepository.kt`

Important split:

- Polar shape and glider configuration live in `GliderRepository`.
- MacCready does not live in `GliderRepository`; it lives in `LevoVarioPreferencesRepository`.

## Where Polar Is Defined

### 1. Built-in glider catalog

File:

- `core/common/src/main/java/com/trust3/xcpro/common/glider/GliderModels.kt`

Current built-in sources:

- Static best L/D and minimum sink metadata.
- Point-list polars.
- Light/heavy point lists for some gliders.
- Quadratic coefficient support.
- A built-in fallback glider: `defaultClubFallbackGliderModel()`.

Current catalog quality:

- A few gliders have usable polar data.
- Some catalog entries are placeholders with no usable polar.

### 2. User and profile state

File:

- `feature/profile/src/main/java/com/trust3/xcpro/glider/GliderRepository.kt`

Stored per profile:

- selected model id
- pilot + gear mass
- water ballast
- bugs percent
- IAS min/max overrides
- optional 3-point polar
- optional `userCoefficients`
- optional `referenceWeightKg`

Repository outputs:

- `selectedModel`
- `effectiveModel`
- `isFallbackPolarActive`
- `config`

Important behavior:

- If the selected model has no usable polar, the repository resolves a fallback club model.
- If a manual 3-point polar is present and valid, it is treated as a usable polar and can disable fallback.

### 3. Polar editing UI

Files:

- `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/PolarSettingsScreen.kt`
- `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/PolarAircraftSelectCard.kt`
- `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/PolarConfigCard.kt`
- `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/PolarThreePointPolarCard.kt`
- `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/PolarPreviewCard.kt`
- `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/PolarDetailsCard.kt`

UI capabilities today:

- select aircraft model
- set pilot + gear mass
- set water ballast
- set bugs percentage
- set IAS min/max bounds
- enter LX/Hawk style 3-point polar
- preview sink at a selected speed
- view static best L/D and speed-limit metadata

## How Polar Is Resolved at Runtime

### 1. Bounds and availability

File:

- `feature/profile/src/main/java/com/trust3/xcpro/glider/GliderSpeedBounds.kt`

Current priority:

1. manual 3-point polar
2. model point lists
3. model quadratic coefficients

Outputs:

- `hasPolar(...)`
- `resolvePolarRangeMs(...)`
- `resolveIasBoundsMs(...)`

### 2. Sink calculation

File:

- `feature/map/src/main/java/com/trust3/xcpro/glider/PolarCalculator.kt`

Current sink priority:

1. manual 3-point polar
2. light/heavy point-list interpolation using wing loading
3. single point-list interpolation
4. quadratic coefficients
5. generic hardcoded fallback parabola inside `PolarCalculator`

Current adjustments:

- a simple bugs multiplier
- a simple ballast penalty

Important limitation:

- `referenceWeightKg` and `userCoefficients` exist in config storage but are not currently consumed by `PolarCalculator`.

## Runtime Consumers of Polar

### 1. Common sink provider

File:

- `feature/map/src/main/java/com/trust3/xcpro/glider/StillAirSinkProvider.kt`

This is the runtime entrypoint used by the flight pipeline:

- `sinkAtSpeed(airspeedMs)`
- `iasBoundsMs()`

### 2. Legacy netto

File:

- `feature/map/src/main/java/com/trust3/xcpro/sensors/FlightCalculationHelpers.kt`

Current behavior:

- `calculateNetto(...)` calls `sinkProvider.sinkAtSpeed(...)`.
- If no valid polar path exists, netto is marked invalid.

### 3. Levo glide-netto

File:

- `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/LevoNettoCalculator.kt`

Current behavior:

- Uses wind-derived airspeed plus polar sink.
- Uses IAS bounds from the active polar.
- Produces `hasPolar` and confidence flags.

### 4. Speed-to-fly

File:

- `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/SpeedToFlyCalculator.kt`

Current behavior:

- Uses IAS bounds from the active polar.
- Samples sink at candidate speeds through `StillAirSinkProvider`.
- Uses MacCready plus glide-netto.
- In `FINAL_GLIDE` flight mode it disables glide-netto correction, but it still does not compute destination-aware final glide.

### 5. Flight metrics integration

File:

- `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/CalculateFlightMetricsRuntime.kt`

Current outputs already carried in flight data:

- `netto`
- `levoNettoMs`
- `speedToFlyIasMs`
- `speedToFlyDeltaMs`
- `speedToFlyHasPolar`

## Card Pipeline Pass

### 1. Card feed already carries several polar-derived fields

Files:

- `feature/map/src/main/java/com/trust3/xcpro/MapScreenUtils.kt`
- `dfcards-library/src/main/java/com/example/dfcards/FlightDataSources.kt`

Already mapped into `RealTimeFlightData`:

- `currentLD`
- `netto`
- `nettoAverage30s`
- `levoNetto`
- `levoNettoHasPolar`
- `macCready`
- `speedToFlyIas`
- `speedToFlyDelta`
- `speedToFlyValid`
- `speedToFlyHasPolar`

This means the card pipeline is already capable of showing several polar-related values with no architecture change.

### 2. Cards that are already truly wired

Files:

- `dfcards-library/src/main/java/com/example/dfcards/CardFormatSpec.kt`
- `dfcards-library/src/main/java/com/example/dfcards/CardLibraryCatalog.kt`

Currently live:

- `ld_curr`
  - shows `currentLD`
- `polar_ld`
  - shows theoretical still-air L/D at the current IAS from the active polar
- `best_ld`
  - shows best theoretical still-air L/D from the active polar
- `netto`
  - shows legacy netto
- `netto_avg30`
  - shows 30 s netto average
- `levo_netto`
  - shows glide-netto with `no wind` and `no polar` states
- `mc_speed`
  - shows speed-to-fly IAS plus delta and AUTO or MAN source

### 3. Cards that are still placeholders

Files:

- `dfcards-library/src/main/java/com/example/dfcards/CardFormatSpec.kt`
- `dfcards-library/src/main/java/com/example/dfcards/FlightTemplates.kt`

Currently placeholder-only:

- `wpt_dist`
- `wpt_brg`
- `final_gld`
- `wpt_eta`
- `task_spd`
- `task_dist`
- `start_alt`

Important detail:

- `final_gld` exists in the catalog and in preset templates, but the formatter still returns `--:1` with `no waypoint`.
- The same is true for several waypoint and competition cards.
- Built-in templates were updated to stop centering these placeholder-only cards in the shipped presets.

### 3A. The card data contract itself is still missing nav/final-glide fields

File:

- `dfcards-library/src/main/java/com/example/dfcards/FlightDataSources.kt`

Important structural finding:

- `RealTimeFlightData` already carries flight-only polar outputs such as:
  - `currentLD`
  - `netto`
  - `nettoAverage30s`
  - `levoNetto`
  - `speedToFlyIas`
- `RealTimeFlightData` does not currently carry:
  - active waypoint distance
  - active waypoint bearing
  - ETA to waypoint
  - arrival height
  - required glide ratio
  - required altitude
  - task-speed or task-distance state

That means `final_gld`, `wpt_dist`, `wpt_brg`, `wpt_eta`, `task_spd`, `task_dist`, and `start_alt` are not just waiting on formatter work.

They are blocked one layer earlier by the current card-feed contract.

### 4. What this means for polar and final glide

Current card readiness by category:

- flight-only polar metrics
  - already card-ready
- target-aware final-glide metrics
  - not wired
- task-aware competition metrics
  - mostly not wired

This is a data-availability issue, not a card-rendering limitation.

More precisely:

- flight-only polar values are already present in card data
- flight-only polar values now include measured L/D, theoretical polar L/D at current speed, and best L/D
- target-aware and task-aware values are absent from `RealTimeFlightData`
- card formatters therefore have nothing correct to render for final-glide and task cards

### 5. Architecture-safe wiring path for cards

Flight-only values can continue to flow through:

`CompleteFlightData -> convertToRealTimeFlightData(...) -> FlightDataManager.cardFlightDataFlow -> CardIngestionCoordinator -> dfcards`

This path is appropriate for:

- measured L/D
- theoretical polar L/D at current speed
- best L/D
- min sink
- speed-to-fly
- netto

Target or task values should not be invented in card formatters.

They should come from a proper combined domain/use-case layer that joins:

- flight SSOT
- task or selected-target SSOT
- wind state
- glide-computer policy

Only then should the app adapter expose those results to cards.

In practice this also means:

- `RealTimeFlightData` must be extended, or
- a dedicated card-facing glide snapshot must be added alongside it

## What XCPro Calls L/D Today

File:

- `feature/map/src/main/java/com/trust3/xcpro/sensors/FlightCalculationHelpers.kt`

Current `currentLD` is:

- measured glide ratio from recent distance travelled and altitude lost
- not a theoretical polar L/D
- not required glide ratio to destination
- not a final-glide arrival calculation

This distinction matters. XCPro currently has only the retrospective, measured glide ratio.

## What Is Missing Today

Missing nav/glide-computer outputs:

- arrival height to waypoint
- arrival height to task finish
- required altitude
- required glide ratio
- arrival at MC 0
- required MacCready to goal
- required speed to arrive at reserve height
- headwind or tailwind component to active target
- final-glide validity and failure reason
- terrain-aware glide reach based on the active polar

Missing modeling details:

- no clear difference between measured L/D, polar L/D, and required L/D
- no target-aware final-glide solver
- no dedicated safety altitude setting for glide computer outputs
- no dedicated safety MC or MC-offset model
- no real polar degradation model beyond a simple sink multiplier
- dormant fields: `referenceWeightKg`, `userCoefficients`
- preset card templates reference final-glide and task cards that are not backed by live values yet

## Bottom Line

XCPro already has a real polar path, but it is only partial.

Today the app supports:

- polar storage
- fallback polar handling
- sink interpolation
- derived theoretical polar L/D at current speed
- derived best L/D from the active polar
- netto
- glide-netto
- speed-to-fly
- live cards for `ld_curr`, `polar_ld`, `best_ld`, `netto`, `levo_netto`, and `mc_speed`
- shipped built-in presets that now avoid placeholder-only final-glide/task cards

Today the app does not yet support:

- a complete glider-computer style final glide system
- arrival-height calculations
- required glide/altitude calculations
- task or airport final-glide decision support
- live waypoint or final-glide card values behind `final_gld` and related navigation cards
