# XCPro Task-Aware Glide Card Plan

Date: 2026-03-12

## 0) Metadata

- Title: Task-aware glide card delivery plan
- Owner: XCPro maintainers (agent-authored plan)
- Issue/PR: TBD
- Status: Racing-task finish-glide MVP implemented for Phases 0-4; Phases 5-6 pending
- Progress note:
  - 2026-03-12: Exact Phase C seam pass completed:
    - the approved racing-task finish-glide seam is `TaskRepository.state` plus `TaskNavigationController.racingState` into a new `GlideTargetRepository`
    - `TaskManagerCoordinator` stays an internal task/navigation router and is not an approved dependency for card adapters or glide solver code
    - `MapScreenUtils.kt` remains adapter-only and must not choose finish targets or run final-glide math
    - current-leg `wpt_*`, home waypoint, and generic selected-target sources stay out of the first racing finish-glide release
  - 2026-03-12: Racing-task finish-glide MVP implemented:
    - exact seam is now live in code as `TaskRepository.state + TaskNavigationController.racingState -> GlideTargetRepository -> FinalGlideUseCase -> MapScreenObservers/MapScreenUtils adapter -> RealTimeFlightData`
    - `final_gld`, `arr_alt`, `req_alt`, and `arr_mc0` are now wired from solved finish-glide outputs
    - `ld_curr`, `polar_ld`, and `best_ld` remain flight-only
    - current-leg `wpt_*`, `task_*`, and `start_alt` remain out of scope and placeholder-only
    - current MVP validity depends on `RacingFinishCustomParams.minAltitudeMeters`; missing finish altitude invalidates finish-glide cards with `NO_FINISH_ALTITUDE`

Delivery rules:

- no churn
  - keep existing flight-only L/D cards stable
- no ad hoc semantics
  - one meaning per card, documented before implementation
- no card-side math
  - cards format only
- no semantic overload
  - measured L/D, required glide ratio, and arrival prediction stay separate
- no duplicate target owners
  - task, selected target, and final-glide solution each get one SSOT

Current MVP state:

- implemented now:
  - racing-task finish target resolution
  - finish-glide solution contract
  - `final_gld`, `arr_alt`, `req_alt`, and `arr_mc0`
- not implemented yet:
  - current-leg `wpt_*`
  - `task_spd`, `task_dist`, `start_alt`
  - AAT-specific finish glide
- important current limitation:
  - finish-glide cards are only valid when the racing finish defines `minAltitudeMeters`

## 1) Scope

- Problem statement:
  - XCPro now has a first racing-task finish-glide MVP, but only for finish-demand and finish-arrival cards.
  - `wpt_*`, `task_*`, and `start_alt` are still placeholders.
  - finish-glide validity currently depends on a racing finish altitude rule, because finish waypoint elevation is not yet preserved as a separate glide-target input.
- Why now:
  - the docs and next implementation phases need to reflect the delivered seam, not the pre-implementation placeholder state
  - the next glide-computer work still needs exact pilot-facing semantics before more fields or cards are added
- In scope:
  - exact semantics for required L/D, arrival height, altitude required, and MC0 arrival
  - card-source policy for racing-task flight
  - phased delivery plan from domain model to card release
  - tests and acceptance gates for a release-grade implementation
- Out of scope:
  - changing the meaning of `ld_curr`, `polar_ld`, or `best_ld`
  - AAT-specific final-glide behavior in the first task-aware release
  - full task-speed / start-alt competition metrics in the first task-aware release
- User-visible impact:
  - task-added flights will show distinct route-demand and arrival-prediction values
  - pilots will be able to distinguish:
    - how they are gliding now
    - what glide the route requires
    - what altitude the solver predicts at finish

## 2) Exact Card Semantics

### 2.1 Keep Existing Flight-Only Cards Unchanged

These must not change when a task is added:

| Card ID | Meaning | Owner |
|---|---|---|
| `ld_curr` | measured recent glide ratio from recent flight path | flight runtime |
| `polar_ld` | theoretical polar L/D at current speed | polar runtime |
| `best_ld` | best theoretical polar L/D | polar runtime |

Rule:

- task state must not alter these card meanings

### 2.2 Define the New Glide Cards Precisely

Recommended task-aware glide card set:

| Card ID | Primary meaning | Primary target in racing-task mode | Notes |
|---|---|---|---|
| `final_gld` | required glide ratio to finish | task finish | keep existing card ID; make it real |
| `arr_alt` | predicted arrival height at finish using active MC, polar, wind, and reserve | task finish | positive means above reserve, negative means below reserve |
| `req_alt` | required current altitude to reach finish with reserve using active MC, polar, and wind | task finish | primary should be absolute required altitude; secondary can show surplus/deficit vs now |
| `arr_mc0` | predicted arrival height at finish with MC 0 | task finish | conservative comparison value |

Do not reinterpret these as current-leg cards.

Status:

- implemented for racing-task finish targets in the MVP slice

### 2.3 Keep Waypoint Cards for the Active Leg

These should stay current-leg or current-nav-target cards:

| Card ID | Meaning in racing-task mode |
|---|---|
| `wpt_dist` | distance to the active leg target |
| `wpt_brg` | bearing to the active leg target |
| `wpt_eta` | ETA to the active leg target |

This is the important split:

- `wpt_*` = current leg
- `final_gld` / `arr_alt` / `req_alt` / `arr_mc0` = task finish

### 2.4 Define the Math Semantics Explicitly

Required glide ratio:

- `final_gld` should represent route demand only:
  - horizontal distance remaining to finish
  - divided by height available above finish elevation plus configured reserve
- it must not embed current polar or current MC
- it must not be reused as measured L/D

Arrival height:

- `arr_alt` should represent predicted finish arrival height relative to configured reserve
- positive means the solver predicts arrival above reserve
- negative means additional height is still needed

Required altitude:

- `req_alt` should represent the total current altitude required to arrive with reserve
- recommended secondary:
  - surplus or deficit vs current nav altitude

MC0 arrival:

- `arr_mc0` should use the same target, reserve, and altitude source as `arr_alt`
- only MacCready changes to `0`

### 2.5 Invalid-State Contract

Cards must never invent values. Invalid states should be explicit:

- no task
- task not started
- no valid finish target
- no active polar
- invalid nav altitude
- impossible or invalid groundspeed solution
- missing wind if the chosen solver mode requires it

Recommended display behavior:

- show placeholder primary value
- show a short reason in secondary text:
  - `no task`
  - `prestart`
  - `no polar`
  - `no alt`
  - `invalid`

## 3) Architecture Contract

### 3.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Active task definition | `feature/tasks/.../TaskRepository.kt` | `state: StateFlow<TaskUiState>` | card-local task mirrors |
| Racing task navigation progress | `feature/tasks/.../TaskNavigationController.kt` and `RacingNavigationStateStore.kt` | racing state/events | card-side leg tracking |
| Active finish glide target | new `GlideTargetRepository` task-finish adapter | immutable `GlideTargetSnapshot` | UI-local finish-target copies |
| Final-glide solution to finish | new `FinalGlideUseCase` | immutable `GlideSolution` | formatter math, ViewModel math |
| Card-facing glide data | app adapter layer near `MapScreenUtils.kt` | extended card data contract | cards querying repositories or task managers |

### 3.2 Dependency Direction

Confirm dependency flow remains:

`UI -> domain -> data`

Likely files/modules touched when implemented:

- `feature/tasks/.../TaskRepository.kt`
- `feature/tasks/.../TaskNavigationController.kt`
- new glide-target and solver files in `feature/map/.../sensors/domain` or an adjacent domain package
- `feature/map/.../MapScreenUtils.kt`
- `dfcards-library/.../FlightDataSources.kt`
- `dfcards-library/.../CardFormatSpec.kt`

Boundary risks:

- task finish selection logic leaking into cards
- final-glide math landing in `MapScreenUtils.kt` or Compose
- duplicating finish-target state outside task/navigation adapters
- routing around task/navigation SSOT via `TaskManagerCoordinator`

### 3.2A Boundary Ownership

| Responsibility | Current Owner | Required Boundary | Why | Validation |
|---|---|---|---|---|
| task-finish target selection for glide cards | `GlideTargetRepository` adapter layer | keep target selection there | one target SSOT | implemented + target precedence tests |
| finish final-glide computation | `FinalGlideUseCase` | keep glide math there | domain math belongs in domain | implemented + unit tests |
| glide-card join | app adapter layer near `MapScreenUtils.kt` | keep join there | cards must stay formatting-only | implemented + adapter/formatter tests |

### 3.2B Bypass Removal Plan

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `CardFormatSpec.kt` `final_gld` | none; now adapter-backed | keep real finish required-glide value from adapter | Completed 2026-03-12 |
| `CardFormatSpec.kt` `arr_alt` / `req_alt` / `arr_mc0` | none; now adapter-backed | keep real finish arrival/required-altitude values from adapter | Completed 2026-03-12 |
| `CardFormatSpec.kt` `wpt_*` | placeholder values | real current-leg target data from adapter | Future Phase 5 |
| future temptation to reuse `currentLD` as task glide | semantic overload | keep `ld_curr` flight-only and add explicit finish glide fields | Ongoing guardrail |

### 3.2C Exact Phase C Seam Pass

Phase C in this plan means the exact join from racing task/navigation state into finish-glide domain inputs, and then from solved glide data into the card adapter.

Approved seam:

`TaskRepository.state + TaskNavigationController.racingState -> GlideTargetRepository -> FinalGlideUseCase -> app adapter near MapScreenUtils.kt -> RealTimeFlightData`

Exact owner rules from the seam pass:

| Responsibility | Approved owner/seam | Explicitly forbidden bypass | Why |
|---|---|---|---|
| task definition, finish waypoint, task target snapshots | `TaskRepository.state` / `TaskUiState` | direct card or solver reads from `TaskManagerCoordinator.currentTask` | keep task structure on the task SSOT path |
| racing start/progress/finish status | `TaskNavigationController.racingState` | direct card or solver reads from `TaskManagerCoordinator.currentLeg`, `getCurrentLegWaypoint()`, or leg listeners | keep navigation progress on the navigation SSOT path |
| prestart invalidation | `RacingNavigationState.status` | ad hoc "task has waypoints so cards are valid" checks | `prestart` must come from navigation state, not task existence |
| finish target snapshot for glide cards | `GlideTargetRepository` combining task + racing navigation state | ViewModel-local or card-local finish target selection | one finish target owner |
| card-facing finish-glide fields | adapter near `MapScreenUtils.kt` | formatter-side math or repository reads in `dfcards-library` | cards stay formatting-only |

Phase C exclusions confirmed by the seam pass:

- no direct `TaskManagerCoordinator` dependency in `FinalGlideUseCase`
- no direct `TaskManagerCoordinator` dependency in `MapScreenUtils.kt`
- no current-leg `wpt_*` work in the finish-glide slice
- no `HomeWaypointRepository` or generic selected-target fallback in the first racing finish-glide slice
- no AAT target reuse in the first racing finish-glide slice

### 3.3 Time Base

| Value | Time Base | Why |
|---|---|---|
| required glide ratio | same flight/task sample time as source snapshot | replay-safe and deterministic |
| arrival height | same flight/task sample time as source snapshot | stable solver output |
| ETA | derived from current sample and projected groundspeed | avoids wall-time drift |
| task finish target selection | racing navigation fix cadence | task replay determinism |

Explicitly forbidden:

- wall-time driven ETA logic in domain
- replay vs wall comparisons
- cards formatting "time since now" from wall time

### 3.4 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence rules:
  - same replay flight samples and same task navigation samples must yield the same finish target and same glide-card values

## 4) Data Flow

Historical gap at plan creation:

`task state -> task UI`

`flight snapshot -> RealTimeFlightData -> cards`

There was no finish-glide join.

Current implemented flow:

`TaskRepository + TaskNavigationController -> GlideTargetRepository (finish target)`

`flight snapshot + active polar + wind + finish target + reserve settings -> FinalGlideUseCase -> GlideSolution`

`GlideSolution + flight snapshot -> app card adapter -> RealTimeFlightData -> cards`

Current MVP limitation:

- finish-glide constraint currently comes from `RacingFinishCustomParams.minAltitudeMeters`
- if that finish altitude is missing, the glide solution stays invalid and the cards render `no alt`

Exact Phase C seam notes:

- `TaskRepository` contributes task structure only
- `TaskNavigationController` contributes racing status/progress only
- `GlideTargetRepository` is the first place these streams are allowed to join for finish-glide cards
- `MapScreenUtils.kt` is the first place glide solution is allowed to join card data
- `dfcards-library` remains pure formatting and card catalog code

## 5) Phased Plan

### Phase 0 - Semantic Freeze (implemented)

- Goal:
  - lock exact card meanings before implementation
- Files to change:
  - `docs/POLAR/*`
- Must decide now:
  - `ld_curr` remains measured only
  - `final_gld` becomes required glide ratio to finish
  - `arr_alt`, `req_alt`, and `arr_mc0` are new cards
  - `wpt_*` remain current-leg cards
- Exit criteria:
  - one written source of truth exists for these meanings
- Status:
  - completed on 2026-03-12

### Phase 1 - Domain Output Contract (implemented)

- Goal:
  - define the finish-glide solution contract before card wiring
- Files to change:
  - new glide-domain model files
  - `dfcards-library/.../FlightDataSources.kt`
- Recommended new card-facing fields:
  - `glideTargetKind`
  - `glideTargetLabel`
  - `requiredGlideRatio`
  - `arrivalHeightM`
  - `requiredAltitudeM`
  - `arrivalHeightMc0M`
  - `glideSolutionValid`
  - `glideInvalidReason`
  - `taskFinishDistanceRemainingM`
- Exit criteria:
  - no one adds one-off card fields outside this batch
- Status:
  - completed on 2026-03-12
  - `RealTimeFlightData` now carries nav altitude and finish-glide solution fields

### Phase 2 - Finish Target SSOT (implemented for racing finish target)

- Goal:
  - provide one authoritative finish target for racing-task glide cards
- Files to change:
  - new `GlideTargetRepository`
  - task adapters around `TaskRepository` and `TaskNavigationController`
- Policy:
  - in racing mode, finish-glide cards use task finish
  - before task start, finish-glide cards stay invalid with `prestart`
  - AAT is excluded from the first implementation phase
  - `TaskManagerCoordinator` is not an allowed dependency for this phase
- Exit criteria:
  - one finish target snapshot is available and replay-stable
- Status:
  - completed on 2026-03-12
  - current validity requires a racing finish altitude rule (`minAltitudeMeters`)

### Phase 3 - Finish Glide Solver MVP (implemented)

- Goal:
  - compute real finish glide values
- Files to change:
  - new `FinalGlideUseCase`
  - settings/state for reserve and altitude policy
- Seams locked by the exact pass:
  - input target comes only from `GlideTargetRepository`
  - input validity for `prestart` comes from `TaskNavigationController.racingState`
  - no target selection logic lives inside the solver
- Solver outputs required for first release:
  - `requiredGlideRatio`
  - `arrivalHeightM`
  - `requiredAltitudeM`
  - `arrivalHeightMc0M`
  - `valid`
  - `reasonIfInvalid`
- Exit criteria:
  - still-air, wind, reserve, and invalid-state cases are deterministic
- Status:
  - completed on 2026-03-12
  - current invalid states include `PRESTART`, `NO_FINISH_ALTITUDE`, `NO_ALTITUDE`, `NO_POLAR`, `INVALID_ROUTE`, `INVALID_SPEED`, and `FINISHED`

### Phase 4 - Card Wiring Release (implemented)

- Goal:
  - make the cards real without changing their semantics in formatters
- Files to change:
  - `feature/map/.../MapScreenUtils.kt`
  - `dfcards-library/.../FlightDataSources.kt`
  - `dfcards-library/.../CardFormatSpec.kt`
  - `dfcards-library/.../CardLibraryCatalog.kt`
  - `dfcards-library/.../FlightTemplates.kt`
- Card release scope:
  - `final_gld`
  - `arr_alt`
  - `req_alt`
  - `arr_mc0`
- Seams locked by the exact pass:
  - `MapScreenUtils.kt` maps solved values only
  - `dfcards-library` does not query task/navigation owners
- Exit criteria:
  - cards format real values
  - invalid states render clearly
  - no task manager access exists in dfcards
- Status:
  - completed on 2026-03-12 for `final_gld`, `arr_alt`, `req_alt`, and `arr_mc0`

### Phase 5 - Current-Leg Card Release

- Goal:
  - wire `wpt_dist`, `wpt_brg`, and `wpt_eta` from the task/current-nav target path
- Important separation:
  - do not reuse finish target for `wpt_*`
  - do not reuse current leg target for finish-glide cards
- Exit criteria:
  - leg cards and finish-glide cards can coexist without ambiguity
- Status:
  - pending

### Phase 6 - Competition Extensions

- Goal:
  - handle `task_spd`, `task_dist`, `start_alt`, and AAT-specific variants only after the finish-glide cards are stable
- Exit criteria:
  - no placeholder-only competition card remains in shipped presets
- Status:
  - pending

## 6) Test Plan

- Unit tests:
  - required glide ratio math
  - arrival height with active MC
  - required altitude
  - MC0 arrival
  - invalid-state handling
  - target-source policy
- Replay/regression tests:
  - identical replay gives identical finish-glide cards
  - leg change does not corrupt finish-glide solution
  - prestart to started transition behaves deterministically
- Card/adapter tests:
  - `final_gld` formats `requiredGlideRatio`
  - `arr_alt` formats `arrivalHeightM`
  - `req_alt` formats `requiredAltitudeM`
  - `arr_mc0` formats `arrivalHeightMc0M`
  - invalid reason mapping
- Boundary tests:
  - `ld_curr` unchanged by task activation
  - `polar_ld` unchanged by task activation
  - `best_ld` unchanged by task activation
  - `wpt_*` use current leg, not finish
  - `final_gld` and arrival cards use finish, not current leg

## 7) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| pilots confuse route-demand and predicted-arrival values | high | freeze semantics before implementation | maintainers |
| `final_gld` overloaded with MC/polar behavior | high | define it as required glide ratio only | maintainers |
| finish and current-leg targets get mixed | high | separate `wpt_*` vs finish-glide cards in tests | maintainers |
| task activation changes `ld_curr` meaning | medium | add explicit regression tests | maintainers |
| AAT semantics leak into first racing release | medium | keep AAT out of Phase 2-4 | maintainers |

## 8) Acceptance Gates

- `ld_curr`, `polar_ld`, and `best_ld` remain flight-only
- `final_gld`, `arr_alt`, `req_alt`, and `arr_mc0` have one documented meaning
- cards contain no business logic
- finish-glide outputs are deterministic in replay
- `wpt_*` and finish-glide cards use different documented targets
- required checks pass for non-trivial implementation slices

## 9) Executed First Delivery Slice

Executed on 2026-03-12:

1. added the finish-glide domain output contract
2. added the finish target SSOT for racing tasks only
3. implemented `FinalGlideUseCase`
4. wired:
   - `final_gld`
   - `arr_alt`
   - `req_alt`
   - `arr_mc0`

Still intentionally excluded from that slice:

- `wpt_dist`
- `wpt_brg`
- `wpt_eta`
- `task_spd`
- `task_dist`
- `start_alt`
- AAT-specific logic
