# XCPro Final Glide And Task Architecture Deep Dive

Date: 2026-03-25
Audience: ChatGPT or any engineer trying to understand how XCPro is currently wired for task-aware glide calculations
Scope: Current architecture from Android sensors through fused runtime state into task navigation, final-glide calculation, and UI cards, plus a recommended better implementation shape

## Why this document exists

XCPro already has a partial task-aware final-glide implementation, but the wiring is spread across several modules:

- live sensor ingestion and replay handling
- fused flight-data computation
- glider polar ownership
- task runtime and navigation state
- final-glide target derivation
- UI adapter and card ingestion

This document explains the current end-to-end path so an external reasoning model can:

- understand where the current numbers come from
- see which state owners are authoritative
- identify architectural weaknesses without proposing changes that violate XCPro's layering rules
- propose a better implementation that stays compatible with MVVM + UDF + SSOT

## Short answer

Today, XCPro does **not** calculate task final glide inside the main fused flight-data SSOT. Instead, it does this:

1. Sensors are fused into `CompleteFlightData`.
2. `FlightDataRepository` becomes the authoritative runtime SSOT for that fused flight sample.
3. The same fused sample is adapted into `RacingNavigationFix` and sent into task navigation.
4. `TaskManagerCoordinator.taskSnapshotFlow` and `TaskNavigationController.racingState` are combined into a `GlideTargetSnapshot`.
5. `MapScreenObservers` combines `CompleteFlightData`, wind, flying state, and `GlideTargetSnapshot`, then calls `FinalGlideUseCase`.
6. The returned `GlideSolution` is appended into `RealTimeFlightData` and pushed into card/UI pipelines.

That means the current final-glide result is a **derived UI/runtime-side product**, not part of the core flight-data SSOT.

This is workable for an MVP, but it is not the best long-term architecture.

## Real glider-task concepts that matter here

### What "flying around a task" means

For a racing task, the pilot is not simply flying to waypoint centers. The task is usually made of:

- a start boundary
- one or more turnpoint observation zones
- a finish boundary

The task is satisfied by crossing or entering those observation zones in order. In practice, a glide computer should think in terms of the **actual remaining route through valid task geometry**, not just straight lines between waypoint centers.

Why that matters:

- a finish cylinder should normally be reached at the cylinder edge, not the center
- a line finish is crossed at a line, not a point
- sectors and keyholes can change the optimal touch point significantly
- "task required altitude" and "task required L/D" should be based on the route around remaining task points, not a naive center-to-center shortcut

### What the pilot-facing values usually mean

Common glide-computer semantics:

- `required L/D`: glide ratio required from current position to the target or around the remaining task
- `arrival altitude`: predicted altitude surplus or deficit at the target after subtracting the altitude needed for the route
- `required altitude`: altitude needed now to complete the route under current assumptions
- `task arrival altitude`: same concept, but for the full remaining task rather than a single waypoint

Typical inputs:

- current navigation altitude
- task geometry / remaining distance
- active glider polar
- MacCready setting
- wind
- finish rule or reserve / safety arrival height
- sometimes bugs, ballast, and terrain

### Relevant external references

These references align with the expected glide-computer behavior:

- Naviter racing task article: a racing task is flown through task observation zones, and task final-glide navboxes calculate around all remaining task points  
  https://kb.naviter.com/en/kb/fly-racing-task-glider/
- Naviter navboxes reference: arrival altitude, required altitude, task arrival altitude, and task required altitude are navbox concepts driven by route, wind, MC, and polar  
  https://kb.naviter.com/en/kb/navboxes/
- FAI gliding sporting code annex: turnpoints are satisfied by entering an observation zone; cylinders and sectors are different geometric objects with different routing implications  
  https://fai.org/sites/default/files/sc3c_-_2020.pdf

## XCPro architecture constraints that matter

The repo-wide architecture rules from `AGENTS.md` and the architecture docs boil down to:

- preserve MVVM + UDF + SSOT
- do not move business logic into UI classes
- keep replay deterministic
- use injected clocks and explicit time sources
- respect dependency direction and module boundaries

For this topic, the most important consequences are:

- there should be one authoritative owner for active task runtime state
- there should be one authoritative owner for active glider polar state
- task-aware glide math should live in a domain owner, not in card formatting or UI-only glue
- derived card values should come from upstream domain snapshots, not from scattered per-card logic

## Current end-to-end wiring

### High-level flow

```text
Android service
  -> sensor manager / registry
  -> sensor fusion engine
  -> CompleteFlightData
  -> FlightDataRepository (SSOT)

FlightDataRepository + task runtime
  -> task navigation state
  -> glide target snapshot

CompleteFlightData + wind + glide target
  -> FinalGlideUseCase
  -> GlideSolution
  -> RealTimeFlightData
  -> FlightDataManager
  -> card ingestion
  -> UI cards
```

### Concrete file path

```text
VarioForegroundService
  -> VarioServiceManager
  -> UnifiedSensorManager / SensorRegistry
  -> SensorFusionRepository (FlightDataCalculatorEngine)
  -> FlightDataEmitter + CalculateFlightMetricsUseCase
  -> CompleteFlightData
  -> FlightDataRepository

FlightDataRepository.flightData
  -> RacingNavigationFixAdapter
  -> TaskNavigationController
  -> RacingNavigationState

TaskManagerCoordinator.taskSnapshotFlow + RacingNavigationState
  -> GlideTargetRepository
  -> GlideTargetSnapshot

CompleteFlightData + WindState + GlideTargetSnapshot
  -> FinalGlideUseCase
  -> GlideSolution
  -> convertToRealTimeFlightData(...)
  -> RealTimeFlightData
  -> FlightDataManager.cardFlightDataFlow
  -> CardIngestionCoordinator
  -> dfcards / UI
```

## Ownership map

### 1. Sensor ingestion and service lifetime

Primary owners:

- `app/src/main/java/com/example/xcpro/service/VarioForegroundService.kt`
- `feature/map/src/main/java/com/example/xcpro/vario/VarioServiceManager.kt`
- `feature/map/src/main/java/com/example/xcpro/sensors/UnifiedSensorManager.kt`
- `feature/map/src/main/java/com/example/xcpro/sensors/SensorRegistry.kt`

Responsibilities:

- keep the live sensor pipeline alive in the foreground service
- start and stop GPS, barometer, compass, rotation vector, and acceleration sensors
- expose raw sensor callbacks without owning business logic
- feed live sensor samples into sensor fusion

Important details:

- `SensorRegistry` uses monotonic timestamps where available:
  - GPS uses `Location.elapsedRealtimeNanos`
  - Android sensor events use `SensorEvent.timestamp`
- wall time is still attached for UI/output purposes
- this is good for deterministic replay and timebase correctness

### 2. Fused flight-data SSOT

Primary owners:

- `feature/flight-runtime/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngine.kt`
- `feature/flight-runtime/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngineLoops.kt`
- `feature/flight-runtime/src/main/java/com/example/xcpro/sensors/FlightDataEmitter.kt`
- `feature/flight-runtime/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsRuntime.kt`
- `feature/flight-runtime/src/main/java/com/example/xcpro/sensors/SensorData.kt`
- `feature/flight-runtime/src/main/java/com/example/xcpro/flightdata/FlightDataRepository.kt`

Responsibilities:

- fuse raw GPS, baro, IMU, compass, terrain, and optional airspeed inputs
- maintain the live/replay fused sample
- compute navigation altitude and performance metrics
- publish `CompleteFlightData`
- expose the authoritative current flight sample via `FlightDataRepository`

Important details:

- `FlightDataCalculatorEngine` runs two decoupled loops:
  - high-rate baro + IMU loop for fast vario and baro-driven updates
  - slower GPS + compass loop for navigation-grade updates and fallback emission
- `FlightDataEmitter` builds a `FlightMetricsRequest`, executes `CalculateFlightMetricsUseCase`, maps the result to display-ready flight data, and publishes `CompleteFlightData`
- `FlightDataRepository` is explicitly documented as the latest `CompleteFlightData` single source of truth

### 3. Navigation altitude and current glide-related metrics

Primary owners:

- `feature/flight-runtime/src/main/java/com/example/xcpro/sensors/domain/SensorFrontEnd.kt`
- `feature/flight-runtime/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsRuntime.kt`
- `feature/flight-runtime/src/main/java/com/example/xcpro/sensors/FlightCalculationHelpers.kt`

Current behavior:

- `navAltitude` is chosen from QNH-calibrated baro altitude when available and enabled, else GPS altitude
- the runtime computes:
  - `currentLD`
  - `polarLdCurrentSpeed`
  - `polarBestLd`
  - `speedToFlyIas`
  - auto MacCready
  - netto / TE-related metrics

Important distinction:

- `currentLD` is a **measured recent glide ratio**
- it is **not** the same thing as final glide or task required L/D

### 4. Active glider polar ownership

Primary owners:

- `feature/profile/src/main/java/com/example/xcpro/glider/GliderRepository.kt`
- `feature/profile/src/main/java/com/example/xcpro/glider/PolarStillAirSinkProvider.kt`
- `feature/flight-runtime/src/main/java/com/example/xcpro/glider/StillAirSinkProvider.kt`
- `core/common/src/main/java/com/example/xcpro/common/glider/ActivePolarModels.kt`

Responsibilities:

- own the selected glider / active general polar
- expose still-air sink lookup, L/D lookup, and IAS bounds to runtime consumers

This part of the architecture is relatively good already:

```text
GliderRepository
  -> PolarStillAirSinkProvider
  -> used by flight metrics, STF, and final-glide solver
```

That means final-glide math already depends on an injected compute seam instead of poking repository state directly.

### 5. Task runtime authority

Primary owners:

- `feature/tasks/src/main/java/com/example/xcpro/tasks/TaskManagerCoordinator.kt`
- `feature/tasks/src/main/java/com/example/xcpro/tasks/TaskRuntimeSnapshot.kt`
- `feature/tasks/src/main/java/com/example/xcpro/tasks/TaskRepository.kt`
- `docs/ARCHITECTURE/ADR_TASK_RUNTIME_AUTHORITY_2026-03-15.md`

Authoritative rule:

- `TaskManagerCoordinator.taskSnapshotFlow` is the canonical cross-feature runtime read owner
- `TaskRepository` is a compatibility projection for task UI and is **not** the cross-feature SSOT

This is important. Any better final-glide implementation should continue to read active task runtime from `TaskManagerCoordinator`, not from `TaskRepository`.

### 6. Task navigation state

Primary owners:

- `feature/tasks/src/main/java/com/example/xcpro/tasks/TaskNavigationController.kt`
- `feature/tasks/src/main/java/com/example/xcpro/tasks/racing/navigation/RacingNavigationState.kt`
- `feature/map/src/main/java/com/example/xcpro/map/RacingNavigationFixAdapter.kt`
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenReplayCoordinator.kt`

Responsibilities:

- adapt `CompleteFlightData` into a navigation fix
- feed racing-task state machine / navigation controller
- track current leg, start/finish status, and finish outcomes

Important detail:

- task navigation is not driven directly from raw GPS callbacks
- it is driven from the fused runtime sample in `FlightDataRepository`

That is a sound architectural choice because task navigation sees the same timebase and altitude context as the rest of the app.

### 7. Glide target derivation

Primary owners:

- `feature/map/src/main/java/com/example/xcpro/glide/GlideTargetRepository.kt`
- `feature/tasks/src/main/java/com/example/xcpro/tasks/core/TaskWaypointCustomParams.kt`

Responsibilities:

- combine task runtime and racing navigation state
- decide whether there is a valid finish-glide target
- expose remaining waypoints and finish-altitude rule

Current target model:

- `GlideTargetKind.TASK_FINISH`
- `GlideTargetSnapshot`
  - remaining waypoints
  - finish constraint
  - validity / invalid reason

Current limitations:

- only racing-task finish is supported
- pre-start is invalid
- finished is invalid
- finish min altitude is currently mandatory
- no generic current waypoint target
- no alternate / home / landable target
- no AAT target or current-leg target support

### 8. Final-glide solve

Primary owner:

- `feature/map/src/main/java/com/example/xcpro/glide/FinalGlideUseCase.kt`

Inputs:

- `CompleteFlightData`
- `WindState`
- `GlideTargetSnapshot`
- optional reserve meters

Outputs:

- `requiredGlideRatio`
- `arrivalHeightMeters`
- `requiredAltitudeMeters`
- `arrivalHeightMc0Meters`
- `distanceRemainingMeters`
- validity / invalid reason

Current algorithm:

1. Validate target, position, altitude, polar, and finish rule.
2. If finish rule uses QNH, require calibrated QNH altitude.
3. Build a route from current GPS position through the remaining waypoint list.
4. For each leg:
   - compute bearing
   - resolve headwind from current wind vector
   - scan IAS across polar bounds in 0.5 m/s steps
   - choose the speed minimizing `(sink + MC) / groundspeed`
   - accumulate altitude loss as `distance * sink / groundspeed`
5. Compute:
   - arrival height with current MC
   - required altitude with current MC
   - arrival height with MC 0
   - required glide ratio from total distance and available height

This is a legitimate first-pass final-glide solver, but it is still only an MVP.

### 9. UI join and cards

Primary owners:

- `feature/map/src/main/java/com/example/xcpro/map/MapScreenObservers.kt`
- `feature/map/src/main/java/com/example/xcpro/MapScreenUtils.kt`
- `feature/map/src/main/java/com/example/xcpro/map/FlightDataManager.kt`
- `feature/map/src/main/java/com/example/xcpro/map/CardIngestionCoordinator.kt`
- `dfcards-library/src/main/java/com/example/dfcards/FlightDataSources.kt`
- `dfcards-library/src/main/java/com/example/dfcards/CardLibraryNavigationCatalog.kt`
- `dfcards-library/src/main/java/com/example/dfcards/CardFormatSpec.kt`

Responsibilities:

- combine long-lived runtime flows
- call `FinalGlideUseCase`
- append glide values into `RealTimeFlightData`
- bucket/publish card data
- render or format final-glide cards

Critical architectural fact:

- `MapScreenObservers` is where final-glide currently runs
- `convertToRealTimeFlightData(...)` simply maps the computed result into UI-facing fields
- cards are formatting/presentation only; they do not own glide math

This is better than card-local logic, but the solver still lives in a UI-adjacent observer rather than in a dedicated domain-derived-state owner.

## Where each displayed value comes from today

### Final glide / required L/D

Card field:

- `requiredGlideRatio`

Source path:

```text
task snapshot + racing navigation state
  -> GlideTargetSnapshot
CompleteFlightData + WindState + GlideTargetSnapshot
  -> FinalGlideUseCase.requiredGlideRatio
  -> RealTimeFlightData.requiredGlideRatio
  -> final_gld card
```

Meaning today:

- distance remaining divided by available height above the finish altitude constraint
- based on the current route produced from current position and remaining waypoint list

### Arrival altitude

Card field:

- `arrivalHeightM`

Source path:

```text
FinalGlideUseCase.arrivalHeightMeters
  -> RealTimeFlightData.arrivalHeightM
  -> arr_alt card
```

Meaning today:

- `navAltitude - finishRequiredAltitude - activeMcAltitudeLoss`

This is the predicted altitude surplus or deficit at finish using the active MacCready setting.

### Required altitude

Card field:

- `requiredAltitudeM`

Source path:

```text
FinalGlideUseCase.requiredAltitudeMeters
  -> RealTimeFlightData.requiredAltitudeM
  -> req_alt card
```

Meaning today:

- `finishRequiredAltitude + activeMcAltitudeLoss`

This is the altitude XCPro believes you need now to finish, given:

- the current target route
- the active MC
- the current wind
- the active glider polar

### Arrival altitude at MC 0

Card field:

- `arrivalHeightMc0M`

Source path:

```text
FinalGlideUseCase.arrivalHeightMc0Meters
  -> RealTimeFlightData.arrivalHeightMc0M
  -> arr_mc0 card
```

Meaning today:

- same route, same wind, same polar, but computed with MacCready forced to zero

### Around a set task

Today, "around the set task" really means:

- current GPS position
- then straight segments through `GlideTargetSnapshot.remainingWaypoints`
- where each remaining waypoint is currently represented by waypoint latitude/longitude

This is the most important limitation in the current implementation.

## The biggest architectural and mathematical gaps

### 1. Final glide is computed too far downstream

Current location:

- `MapScreenObservers`

Why this is not ideal:

- it is a UI/runtime observer seam
- it combines many unrelated concerns
- it makes final-glide results look like screen-derived presentation state rather than canonical navigation-derived state
- it makes it harder for non-map consumers to reuse the same authoritative result

Better shape:

- move task-aware glide computation into a dedicated derived-state owner upstream of UI adapters

### 2. The route is centerpoint-based, not boundary-aware

This is the most important correctness issue.

Current behavior:

- `GlideTargetRepository` exposes remaining waypoint centers
- `FinalGlideUseCase.buildRoute(...)` uses those waypoint centers directly

But XCPro already has task geometry code that understands better routing concepts:

- `feature/tasks/src/main/java/com/example/xcpro/tasks/racing/RacingTaskCalculator.kt`
- `feature/tasks/src/main/java/com/example/xcpro/tasks/racing/boundary/*`

Examples already present in the codebase:

- optimal touch points for cylinders
- finish-cylinder edge entry
- start-line touch logic
- task-type-specific geometry helpers

Why this matters:

- a racing-task final-glide computer should calculate around the **actual remaining task geometry**
- centerpoint routing will systematically overstate or distort remaining distance for cylinders, finish cylinders, sectors, keyholes, and line finishes

This is the single strongest reason the current solver should be reworked.

### 3. There is no canonical route snapshot for navigation consumers

Current state:

- task navigation has state
- task geometry has calculators
- glide target has a reduced snapshot
- final glide rebuilds its own route ad hoc

That means XCPro lacks one explicit canonical object such as:

- `NavigationRouteSnapshot`
- or `GlideComputationSnapshot`

That missing object is the root cause of duplication and drift.

### 4. Finish-only support is too narrow

Current supported target:

- racing task finish only

Missing target types:

- current leg / next waypoint
- full remaining task using actual OZ geometry
- AAT target
- selected waypoint
- home / alternate / landable
- explicit glide target independent of task

If XCPro wants a real glide computer rather than just finish-glide cards, the target model must be generalized.

### 5. Finish altitude policy is incomplete

Current finish rule input:

- `RacingFinishCustomParams.minAltitudeMeters`
- altitude reference `MSL` or `QNH`

Missing policy concepts:

- configurable safety arrival height
- explicit reserve owner
- field elevation + finish reserve if task finish altitude is absent
- separate final-glide MC versus cruise MC if desired
- degraded but usable behavior when finish rules are incomplete

Right now the system hard-fails with `NO_FINISH_ALTITUDE` when finish min altitude is absent. That is safe for an MVP, but too rigid for a mature glide computer.

### 6. Airspeed contract ambiguity exists at the sink-provider seam

There is a known repo-level concern already called out in:

- `docs/POLAR/07_XCPRO_GENERAL_POLAR_CARD_NAV_CHANGE_PLAN_2026-03-17.md`

Issue:

- `StillAirSinkProvider` wording suggests true airspeed in places
- runtime consumers, including `FinalGlideUseCase`, currently scan and query it using IAS-like values

Why this matters:

- final glide, STF, netto, and polar lookups must all agree on the airspeed contract
- if the airspeed contract is ambiguous, future "fixes" can silently make one subsystem more wrong while making another more right

### 7. Wind handling is route-simple, not route-rich

Current behavior:

- one current wind vector is projected onto each leg as a headwind

This is acceptable for an MVP, but missing:

- wind uncertainty propagation
- per-leg route forecast strategy
- fallback / degraded behavior when wind confidence is weak
- optional "show both with-wind and still-air" behavior

### 8. The data model has at least one small smell

`GlideTargetSnapshot` can be `valid = true` while keeping `invalidReason = NO_TASK`.

This is not the biggest problem, but it signals that the model could be cleaner:

- either `invalidReason` should be nullable
- or it should only be populated for invalid states

## What XCPro already has that should be reused

A better implementation should not start from zero. These existing seams are worth preserving:

### Good existing owners

- `FlightDataRepository` for authoritative fused flight sample
- `TaskManagerCoordinator.taskSnapshotFlow` for active runtime task authority
- `GliderRepository -> PolarStillAirSinkProvider` for active general polar ownership

### Good existing domain seams

- `FinalGlideUseCase` as a domain-friendly solver shell
- task geometry calculators in `feature/tasks/racing/*`
- boundary-related logic in `feature/tasks/racing/boundary/*`

### Good existing UI seam

- `convertToRealTimeFlightData(...)` as the adapter from domain/runtime values into card-facing data

The problem is not that XCPro lacks all the pieces. The problem is that the current pieces are not yet assembled behind one canonical navigation/glide state owner.

## Recommended better architecture

### Design goal

The better design is:

- one authoritative task runtime owner
- one authoritative route projection owner
- one authoritative glide-computation owner
- UI only maps and displays those results

### Proposed target shape

Add a small family of domain snapshots:

```text
TaskManagerCoordinator.taskSnapshotFlow
  + RacingNavigationState
  + task geometry calculators
    -> NavigationRouteSnapshot

NavigationRouteSnapshot
  + CompleteFlightData
  + WindState
  + active polar seam
  + glide policy settings
    -> GlideComputationSnapshot

GlideComputationSnapshot
  -> UI adapters / cards / overlays / banners
```

### 1. Introduce `NavigationRouteSnapshot`

This should become the canonical route object for glide and nav consumers.

It should own:

- target kind
- current leg index
- remaining route legs
- true route points to be flown
- observation-zone-derived touch points or boundary entry points
- finish rule / finish policy
- validity / degraded reason
- labels for current and final targets

For racing tasks, it should reuse the task geometry code that already knows:

- cylinder edge touch points
- finish cylinder entry points
- start/finish line logic
- sector/keyhole geometry

This removes the current centerpoint simplification.

### 2. Move final-glide execution out of `MapScreenObservers`

`MapScreenObservers` should not be the long-term owner of navigation math.

Instead:

- create a dedicated repository or use case that combines:
  - `FlightDataRepository.flightData`
  - wind state
  - `NavigationRouteSnapshot`
  - active glide policy
- publish a read-only `StateFlow<GlideComputationSnapshot>`

Good candidate names:

- `NavigationRouteRepository`
- `GlideComputationRepository`
- `NavigationCardUseCase`
- `TaskPerformanceCardUseCase`

This aligns with the repo's existing planning documents, which already suggest upstream domain owners such as `NavigationTargetRepository`, `TaskPerformanceCardUseCase`, and `MapCardDataJoiner`.

### 3. Separate route projection from glide policy

Do not hide finish reserve, safety height, or altitude-reference decisions inside the route object.

Keep these separate:

- route geometry and remaining path
- altitude policy
- solver configuration

For example:

```text
NavigationRouteSnapshot
GlidePolicySnapshot
CompleteFlightData
WindState
  -> FinalGlideUseCase
```

Where `GlidePolicySnapshot` might include:

- finish reserve height
- safety arrival height
- altitude reference expectations
- whether degraded solve is allowed without explicit finish min altitude
- whether to use current MC, separate final-glide MC, or MC 0 comparison output

### 4. Generalize the target model

Replace finish-only targeting with a more complete target abstraction:

- `TASK_FINISH`
- `TASK_CURRENT_LEG`
- `WAYPOINT`
- `HOME`
- `ALTERNATE`
- `AAT_TARGET`
- `FREE_TARGET`

That allows the same glide-computation engine to support:

- current navboxes
- future waypoint and task cards
- overlays
- final-glide banners
- arrival computations for non-task targets

### 5. Keep `CompleteFlightData` clean

I would **not** stuff all task final-glide outputs directly into `CompleteFlightData`.

Reason:

- `CompleteFlightData` is the fused flight sample SSOT
- task-aware route and target state belong to a different domain axis
- mixing them makes the flight sample depend on task/navigation state ownership

A cleaner split is:

- `CompleteFlightData`: fused aircraft state
- `NavigationRouteSnapshot`: active route/target state
- `GlideComputationSnapshot`: task-aware glide outputs

Then one adapter can join them for UI needs.

### 6. Make degraded states explicit instead of hard-nulling everything

A mature glide computer should surface more than just valid/invalid.

Suggested solve states:

- `VALID`
- `DEGRADED_NO_QNH`
- `DEGRADED_NO_FINISH_RULE`
- `DEGRADED_NO_POLAR`
- `INVALID_NO_POSITION`
- `INVALID_NO_ROUTE`

This lets XCPro show something useful when possible while still being explicit about reduced confidence.

### 7. Unify task cards and glide cards through one card-data joiner

The local planning docs already point in the right direction:

- one upstream owner computes navigation/task/glide values
- one joiner maps them into `RealTimeFlightData`
- cards remain formatting only

That is the correct direction. It avoids a future where:

- final glide is solved in one place
- waypoint distance in another
- task distance in a third
- each with slightly different route assumptions

## Recommended implementation roadmap

### Phase 1. Route authority cleanup

Implement:

- `NavigationRouteSnapshot`
- route projection from `TaskManagerCoordinator.taskSnapshotFlow` + `RacingNavigationState`
- racing-task route built from actual task geometry, not waypoint centers

Acceptance criteria:

- remaining route distance and final-glide route use the same geometric interpretation
- finish-cylinder routing reaches boundary entry, not center
- the route owner is reusable outside the map screen

### Phase 2. Glide owner cleanup

Implement:

- `GlideComputationRepository` or equivalent derived-state owner
- move solver execution out of `MapScreenObservers`
- publish read-only state flow for glide outputs

Acceptance criteria:

- cards, overlays, and other consumers can all reuse the same glide result
- UI layer only maps state, it does not own glide math orchestration

### Phase 3. Policy cleanup

Implement:

- explicit `GlidePolicySnapshot`
- safety reserve / safety arrival height owner
- degraded behavior rules
- explicit airspeed contract at `StillAirSinkProvider`

Acceptance criteria:

- no ambiguity around IAS/TAS contract
- no hidden reserve assumptions
- invalid vs degraded states are clearly separated

### Phase 4. Target generalization

Implement:

- current-leg target
- waypoint target
- AAT target
- home/alternate target

Acceptance criteria:

- the same glide engine supports more than just racing-task finish

## Recommended prompts or constraints for an external model

If ChatGPT is asked to propose code changes, it should be told these repo-specific facts:

- `FlightDataRepository` is the authoritative fused-flight-data SSOT
- `TaskManagerCoordinator.taskSnapshotFlow` is the authoritative cross-feature task runtime source
- `TaskRepository` is projection/compatibility state, not runtime authority
- `GliderRepository -> PolarStillAirSinkProvider` is the active general-polar owner path
- replay determinism and injected time sources are non-negotiable
- task-aware glide math should not move into cards, composables, or miscellaneous UI adapters
- current final glide is implemented in `MapScreenObservers`, but that is an expedient location, not the ideal end-state
- racing-task geometry code already exists and should be reused for route projection

## Concrete "better way" summary

If I had to reduce the recommendation to one sentence:

> Keep fused aircraft state, task route state, and glide-computation state as three separate authoritative layers, and compute final glide from a canonical boundary-aware remaining route rather than from waypoint centers inside a map observer.

More concretely:

- keep `CompleteFlightData` as aircraft-state SSOT
- derive `NavigationRouteSnapshot` from task runtime + task navigation + geometry owners
- derive `GlideComputationSnapshot` from route + flight sample + wind + polar + explicit policy
- join those snapshots once for cards/UI

That gives XCPro:

- more correct task routing
- cleaner ownership
- easier reuse
- fewer silent mismatches between task distance, route geometry, and glide outputs

## Repo files most relevant to this topic

### Architecture and local planning docs

- `docs/ARCHITECTURE/ARCHITECTURE.md`
- `docs/ARCHITECTURE/CODING_RULES.md`
- `docs/ARCHITECTURE/PIPELINE.md`
- `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`
- `docs/ARCHITECTURE/CONTRIBUTING.md`
- `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`
- `docs/ARCHITECTURE/ADR_TASK_RUNTIME_AUTHORITY_2026-03-15.md`
- `docs/POLAR/02_GLIDER_COMPUTER_POLAR_RESEARCH_2026-03-12.md`
- `docs/POLAR/07_XCPRO_GENERAL_POLAR_CARD_NAV_CHANGE_PLAN_2026-03-17.md`
- `docs/RACING_TASK/racing_task_definition.md`
- `docs/02_Tasks/racingtask.md`

### Runtime and sensor pipeline

- `app/src/main/java/com/example/xcpro/service/VarioForegroundService.kt`
- `feature/map/src/main/java/com/example/xcpro/vario/VarioServiceManager.kt`
- `feature/map/src/main/java/com/example/xcpro/sensors/UnifiedSensorManager.kt`
- `feature/map/src/main/java/com/example/xcpro/sensors/SensorRegistry.kt`
- `feature/flight-runtime/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngine.kt`
- `feature/flight-runtime/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngineLoops.kt`
- `feature/flight-runtime/src/main/java/com/example/xcpro/sensors/FlightDataEmitter.kt`
- `feature/flight-runtime/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsRuntime.kt`
- `feature/flight-runtime/src/main/java/com/example/xcpro/flightdata/FlightDataRepository.kt`

### Polar ownership

- `feature/profile/src/main/java/com/example/xcpro/glider/GliderRepository.kt`
- `feature/profile/src/main/java/com/example/xcpro/glider/PolarStillAirSinkProvider.kt`
- `feature/flight-runtime/src/main/java/com/example/xcpro/glider/StillAirSinkProvider.kt`

### Task runtime and geometry

- `feature/tasks/src/main/java/com/example/xcpro/tasks/TaskManagerCoordinator.kt`
- `feature/tasks/src/main/java/com/example/xcpro/tasks/TaskRuntimeSnapshot.kt`
- `feature/tasks/src/main/java/com/example/xcpro/tasks/TaskNavigationController.kt`
- `feature/tasks/src/main/java/com/example/xcpro/tasks/racing/navigation/RacingNavigationState.kt`
- `feature/tasks/src/main/java/com/example/xcpro/tasks/racing/RacingTaskCalculator.kt`
- `feature/tasks/src/main/java/com/example/xcpro/tasks/racing/boundary/RacingBoundaryCrossingPlanner.kt`
- `feature/tasks/src/main/java/com/example/xcpro/tasks/core/TaskWaypointCustomParams.kt`

### Glide target, solve, and UI mapping

- `feature/map/src/main/java/com/example/xcpro/glide/GlideTargetRepository.kt`
- `feature/map/src/main/java/com/example/xcpro/glide/FinalGlideUseCase.kt`
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenObservers.kt`
- `feature/map/src/main/java/com/example/xcpro/MapScreenUtils.kt`
- `feature/map/src/main/java/com/example/xcpro/map/FlightDataManager.kt`
- `feature/map/src/main/java/com/example/xcpro/map/CardIngestionCoordinator.kt`
- `dfcards-library/src/main/java/com/example/dfcards/FlightDataSources.kt`
- `dfcards-library/src/main/java/com/example/dfcards/CardLibraryNavigationCatalog.kt`
- `dfcards-library/src/main/java/com/example/dfcards/CardFormatSpec.kt`

## Final assessment

XCPro is already close to a solid architecture here, but it is not finished.

The good news:

- sensor fusion, task runtime authority, and polar ownership already have sensible SSOT seams
- final-glide cards are not doing their own math
- there is already local planning documentation pointing toward a better architecture

The current weak point:

- the final-glide route is simplified too aggressively and computed too late in the pipeline

If XCPro fixes only one thing first, it should be this:

- make the remaining task route canonical and boundary-aware

If XCPro fixes the second thing next, it should be this:

- move glide computation out of `MapScreenObservers` into a dedicated derived-state owner

Those two changes would improve both correctness and architecture without fighting the repo's existing design direction.
