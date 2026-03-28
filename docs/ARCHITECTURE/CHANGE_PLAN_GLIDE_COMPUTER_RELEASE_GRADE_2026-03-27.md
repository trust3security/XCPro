# CHANGE_PLAN_GLIDE_COMPUTER_RELEASE_GRADE_2026-03-27

## Purpose

Make XCPro release-grade for core glide-computer metrics without violating the current architecture boundary:

- `feature:tasks` owns canonical, boundary-aware route geometry
- `feature:map-runtime` owns derived glide/navigation/task-performance computation
- `feature:map` is consumer/adaptor only
- `FlightDataRepository` / `CompleteFlightData` remain the fused-flight-data SSOT
- `TaskManagerCoordinator.taskSnapshotFlow` remains the authoritative cross-feature task runtime seam
- `TaskRepository` stays UI projection only

This plan is intentionally phased, low-risk, and checkpoint-oriented.

## Current baseline

The branch `final-glide-route-runtime-migration` already delivered the owner split needed for final glide:

- route authority is task-owned and boundary-aware
- glide computation is upstream and non-UI
- map UI is consumer only
- the old compatibility shim has been removed

That gives us the right substrate for the next work.

## Release-grade gaps this plan addresses

### Implemented but needs hardening
- IAS / TAS / GS pipeline
- current L/D / polar L/D / best L/D pipeline
- netto / netto-30s pipeline
- finish-glide / arrival-height / required-altitude pipeline

### Missing or not release-grade
- `WPT DIST`
- `WPT BRG`
- `WPT ETA`
- `TASK SPD`
- `TASK DIST`
- `TASK REMAIN DIST`
- `TASK REMAIN TIME`
- `START ALT`

### Architectural / correctness gaps
- card catalog still gets ahead of runtime contracts
- some cards use heuristic validity instead of explicit validity/source fields
- the IAS/TAS contract still needs to be made explicit end-to-end
- General Polar looks only partially implemented beyond the 3-point polar path
- release proof needs golden/replay-style confidence, not just compile/unit tests

## Recommended branch strategy

### If PR #42 is not merged yet
Create a stacked branch from `final-glide-route-runtime-migration`.

Suggested branch name:
- `feature/glide-computer-release-grade`

### If PR #42 has merged
Create the new branch from fresh `origin/main`.

Suggested branch name:
- `feature/glide-computer-release-grade`

## Milestone policy

For every phase:
1. run the implementation brief
2. run the PASS/FAIL review brief
3. only commit/tag after PASS
4. do not start the next phase from a dirty tree

Suggested tag pattern:
- `gc-phase0-pass`
- `gc-phase1-pass`
- `gc-phase2-pass`
- `gc-phase3-pass`
- `gc-phase4-pass`
- `gc-phase5-pass`

## Phase 0 — shipping guardrail + contract freeze

### Goal
Stop shipping fake or placeholder glide-computer instruments, and freeze the metric definitions before code grows further.

### Why first
This is the safest low-risk phase and prevents release confusion immediately.

### Scope
- add a durable metric contract matrix in-repo
- disable/hide placeholder cards from production card catalogs unless backed by implemented runtime seams
- keep already-implemented glide cards available
- no new runtime repositories yet

### In scope
- `dfcards-library/*` catalogs / registration
- docs for metric definitions and scope
- small tests proving placeholders are no longer offered in production

### Out of scope
- computing waypoint/task metrics
- changing final-glide math
- broad UI redesign

### Acceptance criteria
- unimplemented cards are not exposed in production card selection
- metric definitions are documented for:
  - IAS
  - TAS
  - GS
  - current L/D
  - polar L/D
  - best L/D
  - netto
  - netto 30s
  - WPT DIST / BRG / ETA
  - task speed / distance / remaining / start altitude
  - final glide / required L/D / arrival altitude / required altitude / MC0 arrival
- docs clearly distinguish “implemented now” vs “planned”
- no runtime ownership changes yet

### Validation
- focused card-catalog/unit tests
- `./gradlew enforceRules`
- relevant module unit tests for touched files

### Phase 0 local branch status (2026-03-27)
- Production card selection hides `WPT DIST`, `WPT BRG`, `WPT ETA`, `TASK SPD`, `TASK DIST`, and `START ALT` until authoritative runtime seams exist.
- `TASK REMAIN DIST` and `TASK REMAIN TIME` are not currently cataloged.
- No runtime owner changes are part of this phase.

## Phase 1 — source/validity hardening + low-risk wins

### Goal
Make currently implemented cards explicit and trustworthy.

### Scope
- add explicit validity/source fields instead of heuristic formatter guesses
- wire IAS/TAS subtitle mapping from the authoritative `airspeedSource` field
- add `nettoAverage30sValid`
- add explicit validity for L/D fields
- explicitly defer `FINAL DIST` / `TASK FINISH DIST` to a later phase

### In scope
- `feature:flight-runtime` owner path for validity/source truth
- `FlightDisplayMapper`
- `CompleteFlightData` / `RealTimeFlightData` pass-through only
- `convertToRealTimeFlightData(...)`
- card formatter / card specs as consumers only
- tests for source/validity behavior

### Out of scope
- waypoint metrics
- task performance metrics
- general target-kind expansion
- `FINAL DIST`

### Frozen Phase 1 semantics
- `nettoAverage30sValid` = true only when the current 30-second averaging window still contains at least one authoritative valid netto sample from the owner path; finite or zero-filled averages alone do not make it valid.
- `currentLDValid` = true only after the measured L/D owner path has produced a non-zero glide ratio since the current runtime reset; the held value remains valid between recompute intervals and resets false when the helper resets.
- `polarLdCurrentSpeedValid` = true only when the active polar owner returns a finite positive still-air L/D at the current IAS sample.
- `polarBestLdValid` = true only when the active polar owner returns a finite positive best-L/D value.
- IAS/TAS subtitle mapping remains formatter-owned display logic, but it must derive from `airspeedSource`: `SENSOR` and `WIND` map to `EST`; `GPS` and unknown labels map to `GPS`.

### Acceptance criteria
- cards no longer infer validity with brittle heuristics like `> 1`
- IAS/TAS subtitle mapping uses the actual airspeed source contract, not `tasValid`
- `NETTO 30S` has an explicit validity path
- `FINAL DIST` is not part of this phase
- UI remains formatting only

### Validation
- focused flight-runtime/map/dfcards unit tests
- `./gradlew enforceRules`
- compile touched modules

## Phase 2 — waypoint navigation runtime seam

### Goal
Add release-grade next-waypoint metrics without breaking the owner split.

### Scope
Create a dedicated upstream runtime seam for waypoint navigation values:
- `WPT DIST`
- `WPT BRG`
- `WPT ETA`

### Recommended owner
- `feature:map-runtime`

### Recommended shape
- `WaypointNavigationSnapshot`
- `WaypointNavigationRepository` or equivalent

Inputs:
- `FlightDataRepository.flightData`
- `NavigationRouteRepository.route`
- wind if needed for ETA
- current timebase from fused/replay-safe runtime seams only

### Semantics
- distance and bearing are to the active target touch point / active target point on the canonical route, not naive waypoint center if the canonical route supplies a boundary-aware target
- ETA must be based on an explicit ground-speed/availability contract and validity rules
- no UI-local calculations

### Frozen Phase 2 semantics
- `WPT DIST` = distance from current GPS position to `NavigationRouteRepository.route.remainingWaypoints.first()`. This is boundary-aware because the task-owned route seam already resolves the current target point.
- `WPT BRG` = true bearing from current GPS position to that same authoritative route target.
- `WPT ETA` = local ETA clock time computed from the replay-safe sample wall timestamp plus `WPT DIST / current GPS ground speed`.
- `WPT DIST` and `WPT BRG` are valid only when the route seam is valid and the fused flight sample contains a finite GPS position.
- `WPT ETA` is valid only when `WPT DIST` is valid, the current GPS ground speed is finite and greater than `2.0 m/s`, and the fused/replay-safe sample wall timestamp is present.
- `WPT ETA` source is `GPS` ground speed in this phase. Wind-based ETA is explicitly deferred.
- `FINAL DIST` remains deferred to a later phase.

### Acceptance criteria
- `WPT DIST`, `WPT BRG`, and `WPT ETA` render real values in-card
- values derive from the canonical route seam, not duplicated route math
- production selection re-enables only the implemented waypoint cards
- tests cover:
  - no-task / invalid
  - active waypoint on a simple point
  - boundary-aware target point
  - determinism in replay/live-safe conditions

### Validation
- focused map-runtime tests
- touched map formatter tests
- `./gradlew enforceRules`
- compile touched modules

### Phase 2 local branch status (2026-03-27)
- `WaypointNavigationRepository` in `feature:map-runtime` owns `WPT DIST`, `WPT BRG`, and `WPT ETA`.
- It consumes `FlightDataRepository.flightData` and the task-owned `NavigationRouteRepository.route` seam without reclaiming route ownership.
- `feature:map` and `dfcards-library` remain pass-through/formatting consumers only.
- Task-performance metrics stay deferred to Phase 3.

## Phase 3 — task performance runtime seam

### Goal
Add release-grade task-performance cards upstream of UI.

### Scope
Create a dedicated upstream runtime seam for:
- `TASK SPD`
- `TASK DIST`
- `TASK REMAIN DIST`
- `TASK REMAIN TIME`
- `START ALT`

### Recommended owner
- `feature:map-runtime`

### Recommended shape
- `TaskPerformanceSnapshot`
- `TaskPerformanceRepository` or equivalent

### Required semantics to freeze
- `TASK SPD` = achieved average task speed since accepted task start, using `TaskNavigationController.racingState.acceptedStartTimestampMillis` and the current task sample time; after finish it freezes on the finish crossing time.
- `TASK DIST` = covered task distance since accepted task start, computed as accepted-start reference route distance minus the authoritative current remaining route distance, clamped into `[0, start-reference-distance]`.
- `TASK REMAIN DIST` = authoritative remaining task distance from the canonical route seam.
- `TASK REMAIN TIME` = authoritative remaining task distance divided by achieved task speed, valid only when achieved task speed is finite and greater than `2.0 m/s`.
- `START ALT` = altitude from the authoritative accepted start sample using the task start altitude reference; `QNH` falls back to same-sample `MSL` only if the accepted start sample has no QNH altitude.

### Acceptance criteria
- all listed metrics are computed upstream and mapped by the adapter only
- no formatter-local task math
- start altitude semantics are explicit and tested
- task speed semantics are explicit and tested
- no `TaskRepository` runtime reads

### Validation
- focused map-runtime tests
- task/runtime integration tests where available
- `./gradlew enforceRules`
- compile touched modules

### Phase 3 local branch status (2026-03-27)
- `TaskPerformanceRepository` in `feature:map-runtime` now owns `TASK SPD`, `TASK DIST`, `TASK REMAIN DIST`, `TASK REMAIN TIME`, and `START ALT`.
- It consumes `FlightDataRepository.flightData`, `TaskManagerCoordinator.taskSnapshotFlow`, `NavigationRouteRepository.route`, and `TaskNavigationController.racingState` without reclaiming route ownership.
- `feature:map` remains adapter-only and `dfcards-library` remains formatting-only.
- Competition cards are re-enabled only for these five authoritative task-performance metrics.

## Phase 4 — glide/polar contract hardening

### Goal
Close the correctness gaps that undermine release trust even if the cards exist.

### Scope
- make the IAS/TAS contract explicit end-to-end at `StillAirSinkProvider` and downstream consumers
- make glide states explicit (`VALID`, degraded, invalid) instead of ad hoc nulling
- decide the fate of General Polar fields beyond the 3-point polar:
  - either wire them into the authoritative solver path
  - or remove/hide/document them as unsupported
- tighten finish/glide policy ownership if needed

### Implemented Phase 4 decisions
- `StillAirSinkProvider` is now explicitly IAS-based for the active release path; flight metrics and final glide consume the same IAS contract.
- `threePointPolar` remains the authoritative manual override for sink, polar L/D, best L/D, and final-glide solves because those paths all consume the same upstream sink provider.
- `bugsPercent` and `waterBallastKg` remain the only active General Polar modifiers in authoritative runtime math for this release slice.
- `referenceWeightKg` and `userCoefficients` remain stored for future work only; they are explicitly deferred from the active release contract instead of being presented as authoritative controls.
- final-glide outputs now distinguish:
  - `INVALID` when the route/altitude/polar solve cannot be trusted
  - `DEGRADED` when the solve falls back to a still-air assumption because no usable wind vector exists
  - `VALID` when the solve runs with a usable wind vector
- `currentLD`, `polarLdCurrentSpeed`, `polarBestLd`, `netto`, and `nettoAverage30s` keep explicit valid/invalid owner semantics only; this phase does not invent a degraded state for those flight-runtime outputs.

### Acceptance criteria
- no ambiguity around IAS vs TAS at sink/polar/final-glide seams
- degraded vs invalid states are explicit
- General Polar fields exposed to users are either:
  - truly active in authoritative calculations
  - or clearly not exposed as supported controls
- tests prove manual polar changes affect glide outputs in the intended path

### Validation
- focused profile/map-runtime/flight-runtime tests
- replay-safe determinism checks
- `./gradlew enforceRules`
- compile touched modules

## Phase 5 — release proof + doc finalization

### Goal
Finish with a release-grade proof bundle and final branch-truth docs.

### Scope
- final docs sync
- full local proof
- confirm every shipped glide-computer card is either implemented with an authoritative contract or intentionally absent from production selection
- keep unsupported future metrics uncataloged instead of relying on placeholder filters

### Implemented Phase 5 decisions
- production glide-computer cards now ship only when the authoritative runtime path and validity/source semantics exist end to end:
  - core glide/performance: `ias`, `tas`, `ground_speed`, `ld_curr`, `polar_ld`, `best_ld`, `netto`, `netto_avg30`, `mc_speed`
  - finish/arrival + waypoint navigation: `final_gld`, `arr_alt`, `req_alt`, `arr_mc0`, `wpt_dist`, `wpt_brg`, `wpt_eta`
  - task performance: `task_spd`, `task_dist`, `task_remain_dist`, `task_remain_time`, `start_alt`
- unsupported or deferred glide-computer metrics stay uncataloged instead of being exposed behind placeholder cards or a production hidden-card filter.
- `FINAL DIST` remains authoritative runtime data only in this plan; no standalone production card ships for it.
- route authority remains in `feature:tasks`; glide, waypoint-navigation, and task-performance owners remain upstream and non-UI; `feature:map` remains consumer-only.

### Minimum proof
- `./gradlew enforceRules`
- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`

### Recommended additional proof
- replay/golden tests for:
  - no-task
  - prestart
  - started / in-task
  - near-boundary waypoint navigation
  - finished task
  - no-QNH / no-polar / degraded states
  - manual polar change affecting glide outputs

### Acceptance criteria
- every shipped glide-computer card is implemented with authoritative runtime data and explicit validity/source semantics or is intentionally absent from production selection
- docs reflect the final durable architecture and metric semantics
- full proof passes on the current branch state

## Useful extras you should include if time allows

These are not scope creep; they materially improve the product.

### High-value additions
- `FINAL DIST` card from existing finish-distance data
- source/quality badges for TAS, wind, netto, and glide validity
- golden tests using known reference logs

### Things to avoid
- adding task/glide fields to `CompleteFlightData`
- moving calculations into `feature:map` or card formatters
- using `TaskRepository` as runtime authority
- broad target-kind generalization before the current-card set is release-grade
- starting AAT / home / alternate work in this plan

## Stop-and-fix rule

If any phase fails validation or PASS/FAIL review:
- stop
- fix only that phase
- rerun the same review
- do not continue to the next phase from a dirty or failing tree
