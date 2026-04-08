# XCPro Polar / Glide Computer Release Plan

Date: 2026-03-12

Status note:

- This is the broader release plan for polar and glide-computer work.
- Since this plan was written, XCPro has already completed the first racing-task finish-glide MVP slice:
  - live `final_gld`
  - live `arr_alt`
  - live `req_alt`
  - live `arr_mc0`
- That executed slice and the exact current state are tracked in `06_XCPRO_TASK_AWARE_GLIDE_CARD_PLAN_2026-03-12.md`.

## 0) Metadata

- Title: Polar, L/D, Final-Glide, and Card Wiring Release Plan
- Owner: XCPro maintainers (agent-authored plan)
- Issue/PR: TBD
- Status: Draft

Delivery principles:

- no churn
  - each phase changes one bounded responsibility area
- no ad hoc math
  - no final-glide or task math in cards, Compose, or formatters
- no semantic overload
  - `currentLD` remains measured L/D only
- no fake shipping state
  - placeholder cards must not be presented as if they are live features
- no duplicate SSOTs
  - task, target, polar, and glide solution each get one owner

## 1) Scope

- Problem statement:
  - XCPro already has a partial polar pipeline for sink lookup, netto, glide-netto, and speed-to-fly.
  - XCPro does not yet have a release-grade glide computer.
  - Several cards and presets expose final-glide and task card IDs that are still placeholders.
  - The current card feed contract does not contain waypoint, arrival, or task-glide fields.
- Why now:
  - The current state is good enough to justify a proper release plan, but not good enough to keep layering more ad hoc outputs onto cards and map UI.
  - The codebase already has real SSOTs for polar and task state; the missing piece is the controlled join.
- In scope:
  - release-grade phased plan for polar-derived metrics, final glide, target ownership, and card wiring
  - selected-target final glide first
  - racing-task integration after selected-target release is stable
  - explicit safety, testing, and rollback gates
- Out of scope:
  - full glider catalog rebuild
  - terrain-aware reach in the first selected-target release
  - broad task subsystem rewrite
  - broad dfcards refactor unrelated to polar/glide outputs
- User-visible impact:
  - clear separation between measured L/D, polar L/D, and required glide ratio
  - real final-glide outputs instead of placeholders
  - better confidence that cards and map values are consistent and replay-safe

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Active glider model and config | `feature/profile/.../GliderRepository.kt` | `selectedModel`, `effectiveModel`, `config`, `isFallbackPolarActive` | UI-local glider mirrors, card-local polar lookup |
| Derived polar performance (`bestLd`, `ldAtSpeed`, `minSink`, speed ranges) | new `GlidePolarProvider` or equivalent domain wrapper over `StillAirSinkProvider` | pure functions / immutable snapshot | re-deriving in cards, ViewModels, or Compose |
| Flight sample metrics (`currentLD`, netto, STF, wind, altitude) | existing flight runtime pipeline ending in `CompleteFlightData` | immutable runtime snapshot | separate UI-side recomputation |
| Task definition and AAT target parameters | `feature/tasks/.../TaskRepository.kt` | `state: StateFlow<TaskUiState>` | UI-local task copies |
| Racing task navigation progress | `feature/tasks/.../TaskNavigationController.kt` + `RacingNavigationStateStore.kt` | `racingState`, `racingEvents` | map or card logic maintaining parallel leg state |
| Active glide target for non-task and task use | new `GlideTargetRepository` | `StateFlow<GlideTargetSnapshot?>` | home-waypoint, map-selection, and task-target mirrors in UI |
| Final-glide solution | new `FinalGlideUseCase` | `Flow<GlideSolution>` or immutable snapshot state | card formatter math, Compose math, direct task-manager queries |
| Card-facing glide data | app adapter layer near `MapScreenUtils.kt` | extended `RealTimeFlightData` or dedicated immutable glide-card snapshot | formatting-time joins in `CardFormatSpec` |

### 2.2 Dependency Direction

Confirm dependency flow remains:

`UI -> domain -> data`

Likely modules/files touched across the full plan:

- `feature/profile/.../glider/*`
- `feature/map/.../glider/*`
- `feature/map/.../sensors/domain/*`
- `feature/map/.../MapScreenObservers.kt`
- `feature/map/.../MapScreenUtils.kt`
- `feature/tasks/.../*`
- `dfcards-library/.../*`

Boundary risks:

- cards reaching into task/navigation owners
- final-glide math ending up in ViewModel or Compose
- task target state being duplicated in a new glide-target owner without a clear adapter boundary

### 2.2A Boundary Moves

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Theoretical polar L/D derivation | partial and scattered, mostly absent | `GlidePolarProvider` | one consistent source for best L/D and L/D at speed | unit tests |
| Final-glide math | not implemented, placeholder only | `FinalGlideUseCase` | business logic belongs in domain, not cards/UI | unit + replay tests |
| Active glide target selection | scattered or missing | `GlideTargetRepository` | one target SSOT for selected waypoint, home, and task-backed targets | repository tests |
| Card final-glide rendering inputs | hardcoded placeholders in `CardFormatSpec` | app adapter built from domain outputs | cards should format only | adapter + formatter tests |

### 2.2B Bypass Removal Plan

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `dfcards/.../CardFormatSpec.kt` `final_gld`, `wpt_*`, `task_*` cases | hardcoded placeholders | real adapter-fed data plus explicit invalid states | Phase 4 and Phase 6 |
| `dfcards/.../FlightTemplates.kt` preset use of dead cards | presets imply functionality that is not implemented | remove, hide, or capability-gate unsupported cards | Phase 0 and Phase 4 |
| Implicit reuse of `currentLD` as glide demand | semantic ambiguity | explicit measured, polar, and required glide fields | Phase 1 |
| Future temptation for cards to query task/navigation state directly | cross-layer shortcut | join data upstream in observer or use-case layer | Phase 4 and Phase 6 |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| Flight-only polar metrics | live sample cadence in live mode, replay sample cadence in replay mode | must remain tied to the same flight sample that produced the source data |
| Final-glide solution | same cadence and sample time as the source flight snapshot | replay-safe and deterministic |
| ETA / arrival projection | derived from current solution sample, never wall time | avoids replay/live drift |
| Racing leg progression | live or replay navigation fix cadence | already driven by task navigation sample flow |

Explicitly forbidden comparisons:

- monotonic vs wall
- replay vs wall
- domain use of raw `System.currentTimeMillis`

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - pure math and solver work on `Default` or existing runtime compute path
  - persistence and task/home target loading remain on existing data paths
- Primary cadence/gating sensor:
  - existing `CompleteFlightData` cadence for flight-driven outputs
  - existing racing navigation fix cadence for task navigation state
- Hot-path latency budget:
  - no visible regression in current map/card update cadence
  - final-glide solve and card-adapter work should stay lightweight enough to run per sample without main-thread jank

### 2.5 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence rules:
  - same flight samples, same target selections, same wind inputs, and same settings must produce the same glide solution
  - replay must use replay-driven sample time and replay task-navigation updates

### 2.6 Enforcement Coverage

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| `currentLD` misused as final-glide value | architecture clarity / SSOT semantics | unit + review | metric mapping tests, card adapter tests |
| Cards compute business logic | layering rules | review + tests | `CardFormatSpec` tests remain pure-format only |
| Duplicate target ownership | SSOT ownership | unit + review | `GlideTargetRepository` tests |
| Wall-time leakage into solver | coding rules / replay determinism | enforceRules + review + tests | solver tests with injected time/sample model |
| Placeholder cards shipping as live outputs | user-facing correctness | unit + instrumentation/review | template/catalog/formatter tests |
| Racing task and glide target drift apart | SSOT ownership / replay determinism | replay + integration tests | task/glide join tests |

### 2.7 Visual UX SLO Contract

| Outcome | SLO ID | Baseline | Target | Validation/Test | Phase Gate |
|---|---|---|---|---|---|
| Existing live cards do not regress in freshness | FG-CARD-01 | current card cadence | no worse than current cadence | adapter tests + manual profiling | Phase 4 |
| Final-glide invalid state becomes visible promptly | FG-CARD-02 | N/A | visible on next processed display sample | integration test | Phase 4 |
| Repeated replay produces identical selected-target glide outputs | FG-REPLAY-01 | N/A | identical result for same replay input | replay test | Phase 3 |
| Repeated replay produces identical racing leg/finish glide outputs | FG-REPLAY-02 | N/A | identical result for same replay input | replay test | Phase 5 |

## 3) Data Flow (Before -> After)

Current:

`GliderRepository -> StillAirSinkProvider -> runtime metrics -> CompleteFlightData -> MapScreenUtils -> RealTimeFlightData -> cards`

Separate today:

`TaskRepository -> TaskNavigationController -> RacingNavigationStateStore`

Gap today:

- no single active glide target owner
- no final-glide solver join
- no card-feed fields for waypoint or arrival data

Planned target flow:

`GliderRepository -> GlidePolarProvider`

`TaskRepository + TaskNavigationController + HomeWaypointRepository + selected-target UI -> GlideTargetRepository`

`flight snapshot + wind + altitude + GlidePolarProvider + GlideTargetRepository -> FinalGlideUseCase -> GlideSolution`

`CompleteFlightData + GlideSolution -> app adapter -> RealTimeFlightData or GlideCardSnapshot -> cards + map UI`

## 4) Release Phases

### Phase 0 - Contract Freeze and Surface Cleanup

- Goal:
  - freeze terminology and delivery boundaries before implementation
  - stop advertising placeholder functionality as if it were complete
- Files to change:
  - `docs/POLAR/*`
  - `dfcards-library/.../FlightTemplates.kt`
  - optionally `dfcards-library/.../CardLibraryCatalog.kt` if capability metadata is added
- Tests to add/update:
  - template/catalog smoke tests for unsupported cards
  - documentation references updated
- Exit criteria:
  - one written plan is the authoritative sequence
  - presets do not prioritize dead final-glide/task cards unless explicitly marked unavailable
- Current status:
  - implemented on 2026-03-12 for built-in templates
- Churn guardrails:
  - no new glide math
  - no task refactor
  - no card-data contract expansion yet

### Phase 1 - Polar Metric Foundation

- Goal:
  - add one authoritative domain source for theoretical polar outputs
  - separate measured L/D from polar-derived L/D
- Files to change:
  - `feature/map/.../glider/StillAirSinkProvider.kt`
  - new `feature/map/.../glider/GlidePolarProvider.kt` or equivalent
  - `feature/map/.../glider/PolarCalculator.kt`
  - `feature/map/.../sensors/domain/CalculateFlightMetricsRuntime.kt`
  - runtime snapshot models as needed
- Tests to add/update:
  - best L/D derivation
  - L/D at current speed
  - min-sink derivation
  - fallback, bugs, and ballast regression tests
  - no regression in netto and STF paths
- Exit criteria:
  - runtime can expose:
    - `measuredLdCurrent`
    - `polarLdCurrentSpeed`
    - `polarBestLd`
  - existing `currentLD` behavior remains stable until renamed or re-mapped deliberately
- Current status:
  - implemented on 2026-03-12 for runtime, adapters, and live `polar_ld` / `best_ld` cards
- Churn guardrails:
  - no target-aware logic
  - no final-glide cards
  - no task-state join

### Phase 2 - Active Glide Target SSOT

- Goal:
  - add one authoritative active target owner for glide calculations
  - define explicit target source types and precedence
- Files to change:
  - new `GlideTargetRepository` and glide-target models
  - adapters from:
    - `core/common/.../HomeWaypointRepository.kt`
    - `feature/tasks/.../TaskRepository.kt`
    - `feature/tasks/.../TaskNavigationController.kt`
  - map or settings target-selection entrypoints as needed
- Tests to add/update:
  - target source precedence tests
  - no duplicate state tests
  - replay-stable target switching tests
- Exit criteria:
  - one active target snapshot exists for glide logic
  - target source is explicit:
    - none
    - selected waypoint or landable
    - home waypoint
    - racing current leg or finish
    - future AAT target
- Churn guardrails:
  - no glide math in repository
  - no UI-local mirrors of active target
  - no card wiring yet

### Phase 3 - Selected-Target Final Glide MVP

- Goal:
  - implement the first release-grade final-glide solver for a selected target
- Files to change:
  - new `FinalGlideUseCase` and supporting domain models
  - observer/join layer near `MapScreenObservers.kt`
  - settings/state models for safety arrival height and altitude policy
- Tests to add/update:
  - still-air arrival height
  - required altitude
  - required glide ratio
  - headwind and tailwind effects
  - invalid-state handling
  - MC 0 comparison if included in MVP
  - replay determinism tests
- Exit criteria:
  - solver publishes at least:
    - `arrivalHeightM`
    - `requiredAltitudeM`
    - `requiredGlideRatio`
    - `headwindToTargetMs`
    - `valid`
    - `reasonIfInvalid`
  - solver is pure, deterministic, and not card-dependent
- Churn guardrails:
  - no task-speed or start-alt features
  - no AAT-specific target behavior
  - no card-library business logic

### Phase 4 - Card Contract and Selected-Target Card Release

- Goal:
  - wire the selected-target glide solution into cards cleanly
- Files to change:
  - `dfcards-library/.../FlightDataSources.kt`
  - `feature/map/.../MapScreenUtils.kt`
  - `feature/map/.../map/FlightDataManager.kt` only if feed cadence or buffering needs extension
  - `dfcards-library/.../CardFormatSpec.kt`
  - `dfcards-library/.../CardLibraryCatalog.kt`
  - `dfcards-library/.../FlightTemplates.kt`
- Tests to add/update:
  - adapter mapping tests
  - card formatting tests for valid and invalid states
  - preset/template capability tests
  - UI/instrumentation tests if card availability changes are pilot-visible
- Exit criteria:
  - selected-target release can show real values for:
    - `polar_ld`
    - `best_ld`
    - `req_gr`
    - `arr_alt`
    - `goal_wind`
  - `final_gld`, `wpt_dist`, `wpt_brg`, and `wpt_eta` are either real or intentionally absent from shipped presets
- Churn guardrails:
  - cards remain formatting-only
  - no direct task-manager access from dfcards
  - `task_spd`, `task_dist`, and `start_alt` stay out until their own domain inputs exist

### Phase 5 - Racing Task Integration

- Goal:
  - promote racing-task navigation to a real glide-target source and release task-backed final glide
- Files to change:
  - task-to-glide adapters around:
    - `feature/tasks/.../TaskRepository.kt`
    - `feature/tasks/.../TaskNavigationController.kt`
    - `feature/tasks/.../racing/navigation/RacingNavigationStateStore.kt`
  - map observer join layer
  - card adapter and task-aware UI only where data is real
- Tests to add/update:
  - current-leg target tests
  - finish target tests
  - replay leg-advance determinism
  - invalid-state transitions during leg changes
- Exit criteria:
  - racing current leg and finish can be used as active glide targets
  - `wpt_*` and `final_gld` work for racing-task navigation without card-side math
- Churn guardrails:
  - AAT remains out of scope
  - `task_spd` and `start_alt` stay disabled unless separately implemented and tested

### Phase 6 - Competition Metrics, Safety, and AAT

- Goal:
  - finish the glider-computer slice with explicit safety semantics and competition-aware outputs
- Files to change:
  - final-glide policy/settings models
  - task-performance calculation layer
  - AAT target integration
  - card adapter and task cards where data becomes real
- Tests to add/update:
  - `macCreadyRisk` semantics tests
  - safety-height and safety-MC tests
  - AAT target movement and lock-state tests
  - `task_spd`, `task_dist`, and `start_alt` tests
  - terrain and degraded-wind edge cases if reach is included
- Exit criteria:
  - semantics of safety reserve and `macCreadyRisk` are explicit
  - competition cards are real, not placeholders
  - AAT target-aware glide behavior is deterministic
- Churn guardrails:
  - do not fold unrelated task UX refactors into this phase
  - only ship cards whose upstream data contract and solver behavior are fully tested

## 5) Test Plan

- Unit tests:
  - polar best L/D and L/D-at-speed derivation
  - final-glide still-air and wind cases
  - invalid-state handling
  - target precedence and target switching
  - safety-reserve handling
- Replay/regression tests:
  - identical replay input produces identical glide output
  - racing leg changes and finish transitions stay stable
  - selected-target switching does not corrupt solver state
- UI/instrumentation tests:
  - card visibility and unavailable-state rendering
  - map/panel final-glide status rendering where pilot-facing
- Degraded/failure-mode tests:
  - no target
  - no polar
  - invalid altitude
  - stale or missing wind when required by mode
  - target source disappears during flight
- Boundary tests for removed bypasses:
  - `CardFormatSpec` remains pure formatting
  - cards do not query task managers or repositories directly

Required checks for non-trivial implementation phases:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

When relevant:

```bash
./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
./gradlew connectedDebugAndroidTest --no-parallel
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Semantic confusion between measured, polar, and required glide values | pilots and developers read wrong value | split names and test mappings in Phase 1 | maintainers |
| Cards become the place where glide logic accumulates | architecture drift and replay bugs | keep all joins upstream and test `CardFormatSpec` as formatting-only | maintainers |
| Target SSOT duplicates existing task/home state | state drift and hard-to-replay bugs | add one explicit `GlideTargetRepository` with adapters only | maintainers |
| Shipping placeholder cards in presets | pilot trust regression | clean presets in Phase 0 and gate unsupported cards | maintainers |
| Racing integration leaks task logic into map/cards | cross-module coupling | use task adapters and keep task owners intact | maintainers |
| `macCreadyRisk` stays ambiguous | unsafe or inconsistent behavior | defer safety semantics to Phase 6 and document before shipping | maintainers |

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`
- No duplicate SSOT ownership introduced
- No final-glide formulas in Compose, cards, or ViewModels
- Time base is explicit and replay-safe
- Placeholder cards are not shipped as if they are complete features
- Required checks pass for each non-trivial implementation phase
- `KNOWN_DEVIATIONS.md` updated only if a temporary exception is explicitly approved

Release gates by slice:

- Selected-target release:
  - Phases 0 through 4 complete
- Racing-task release:
  - Phases 0 through 5 complete
- Full competition and safety release:
  - Phases 0 through 6 complete

## 8) Rollback Plan

- What can be reverted independently:
  - Phase 1 polar metric additions
  - Phase 2 target owner
  - Phase 3 solver
  - Phase 4 card contract and card formatting
  - Phase 5 racing-task integration
  - Phase 6 competition and safety layer
- Recovery steps if regression is detected:
  - disable the newest phase behind feature flags or capability gates if used
  - remove unsupported cards from shipped presets first
  - fall back to existing measured L/D, netto, Levo netto, and STF card set
  - revert phase-local adapter wiring before reverting core polar ownership
