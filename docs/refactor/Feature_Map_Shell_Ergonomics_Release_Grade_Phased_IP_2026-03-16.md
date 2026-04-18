# Feature:Map Shell Ergonomics Release-Grade Phased IP

## 0) Metadata

- Title: Follow-on shell ergonomics and residual seam program for `feature:map`
- Owner: Codex
- Date: 2026-03-16
- Issue/PR: TBD
- Status: Draft
- Parent plans:
  - `docs/refactor/Feature_Map_Right_Sizing_Master_Plan_2026-03-15.md`
  - `docs/refactor/Feature_Map_Right_Sizing_Release_Grade_Phased_IP_2026-03-15.md`

## 1) Scope

- Problem statement:
  - the map right-sizing program is closed at the ownership boundary, but
    `feature:map` still carries a broad shell surface and misses the older
    numeric target
  - current steady-state footprint on 2026-03-16 is:
    - `306` main Kotlin files
    - `30,900` main Kotlin lines
  - the biggest remaining hotspots are now mostly shell/composition files or
    residual candidate seams, not obvious wrong-owner moves:
    - `map/ui/MapScreenContentRuntime.kt` (`382`)
    - `map/ui/MapBottomSheetTabs.kt` (`373`)
    - `screens/flightdata/HomeWaypointSelector.kt` (`374`)
    - `screens/flightdata/WaypointUIComponents.kt` (`343`)
    - `map/MapScreenViewModel.kt` (`332`)
    - `utils/AirspaceParser.kt` (`346`)
    - `sensors/SensorRegistry.kt` (`325`)
    - `igc/usecase/IgcRecordingUseCase.kt` (`315`)
    - `replay/IgcReplayControllerRuntime.kt` (`310`)
    - `map/MapScreenReplayCoordinator.kt` (`309`)
    - `tasks/aat/rendering/AATMapRenderer.kt` (`302`)
    - `navdrawer/DrawerMenuSections.kt` (`298`)
    - `map/ui/OverlayPanels.kt` (`296`)
    - `map/ui/MapOverlayStack.kt` (`293`)
- Why now:
  - the previous program correctly stopped when ownership was fixed
  - the next value is readability, testability, and shell ergonomics
  - any further owner moves now require fresh seam locks instead of automatic
    extraction
- In scope:
  - shell-only ergonomics inside the `map/ui` and map-shell lane
  - residual seam classification for replay, flightdata, airspace, sensor, and
    task-rendering pockets
  - CI/doc guard updates that prevent shell drift
- Out of scope:
  - reopening the closed ownership program
  - forced extractions to hit `<= 260` / `<= 28k`
  - large product or UX redesign
  - new modules without a dedicated seam pass and explicit boundary proof
- User-visible impact:
  - no intended behavior change
  - focus is change safety, reviewability, and shell clarity
- Rule class touched: Default

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Concern | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Map shell composition and screen wiring | `feature:map` | shell ViewModel, inputs, composable hosts | runtime/business owners reintroduced into `feature:map` |
| Map runtime, overlay runtime, gesture/runtime helpers | `feature:map-runtime` | runtime ports and runtime contracts | duplicate runtime implementations in `feature:map` |
| Feature settings/task/traffic/weather/profile state | existing owner modules | existing owner APIs | convenience wrappers in `feature:map` |
| Replay / flightdata / airspace / sensor residual seams | unchanged until seam-locked | current owner APIs only | speculative moves without contract proof |

### 2.1A State Contract

| State Item | Authoritative Owner | Allowed Mutators | Read Path | Derived From | Persistence Owner | Reset/Clear Conditions | Time Base | Required Tests |
|---|---|---|---|---|---|---|---|---|
| map shell UI state | existing `feature:map` shell ViewModels and input models | shell intents only | Compose shell files | existing owner flows and shell-local state | existing owners only | current screen lifecycle rules | unchanged | existing map screen tests |
| map runtime/render state | existing runtime owners | runtime managers/controllers only | shell ports and runtime adapters | existing runtime inputs | existing owners only | current map/style lifecycle rules | unchanged | existing runtime tests |
| residual seam classification metadata in this plan | plan-only documentation | plan edits only | this plan and parent plans | seam passes | none | replaced when follow-on phase lands | n/a | plan review |

### 2.2 Dependency Direction

Target dependency flow remains:

`app -> feature:map -> feature:map-runtime + owner feature modules`

This follow-on plan must not create new back-edges or move owner logic back
into `feature:map`.

### 2.2A Reference Pattern Check

| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenRootStateBindings.kt` | already splits shell binding responsibility cleanly | focused binding/state helper files instead of one broad host | new splits may target bottom-sheet or overlay composition instead of root state |
| `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenContentRuntimeSections.kt` | already groups shell runtime composition into narrower sections | split by composition responsibility with no new owner state | follow-on may create more section-level files if a shell file still mixes multiple panels |
| `feature/map/src/main/java/com/trust3/xcpro/map/FlightDataUiAdapter.kt` | keeps conversion/orchestration outside the main ViewModel | narrow adapter/helper extraction from broad shell files | only when it reduces shell API breadth without creating a hidden owner |

### 2.2B Boundary Moves

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| broad shell composition inside monolithic map UI files | mixed shell files in `feature:map` | narrower shell files in `feature:map` | improve ergonomics without changing ownership | compose/unit tests + compile proof |
| residual replay/flightdata/airspace/sensor candidates | unchanged | seam-locked later only if a clean new owner exists | avoid speculative churn | dedicated seam pass required before any move |

### 2.2C Bypass Removal Plan

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| raw `Log.*` in remaining shell hotspots discovered during shell ergonomics | direct platform logging in shell files | `AppLogger` or explicit DEBUG-only justified edge | Phase 1 / Phase 4 as encountered |
| broad shell-to-ViewModel fan-out where a grouped shell binder already exists | direct multi-call shell wiring | narrower shell input/binding helpers | Phase 1 / Phase 2 |

### 2.2D File Ownership Plan

| File | New / Existing | Owner / Responsibility | Why Here | Why Not Another Layer/File | Split Needed? |
|---|---|---|---|---|---|
| `docs/refactor/Feature_Map_Shell_Ergonomics_Release_Grade_Phased_IP_2026-03-16.md` | New | execution contract for the new program | this is a new refactor track | parent plan should stay summary-level | no |
| `docs/refactor/Feature_Map_Right_Sizing_Master_Plan_2026-03-15.md` | Existing | parent program status and pointer to the follow-on plan | keeps the program history canonical | follow-on details do not belong inline in the old plan | no |
| `docs/refactor/Feature_Map_Autonomous_Agent_Execution_Contract_2026-03-15.md` | Existing | handoff note from closed program to new follow-on plan | avoids autonomous confusion | a new contract is not needed until implementation starts | no |
| shell hotspot files under `feature/map/src/main/java/com/trust3/xcpro/map/ui/**` | Existing | shell-only composition and binding | these are already shell owners | no new module move should happen without a seam lock | maybe, phase-dependent |

### 2.2E Module and API Surface

| Contract / API | Owner | Consumers | Visibility | Why Needed | Compatibility / Removal Plan |
|---|---|---|---|---|---|
| no new cross-module contract by default | n/a | n/a | n/a | follow-on starts as shell ergonomics, not API expansion | if a later seam creates a new contract, add it in that phase plan before coding |

### 2.2F Scope Ownership and Lifetime

No new long-lived scope is planned by default. Any new scope in this follow-on
program requires a dedicated seam lock and explicit plan update.

### 2.2G Compatibility Shim Inventory

| Shim / Bridge | Owner | Reason | Target Replacement | Removal Trigger | Test Coverage |
|---|---|---|---|---|---|
| existing shell input/binding adapter files | `feature:map` shell | they keep shell complexity out of core runtime owners | replace only when a narrower shell helper is clearly better | phase-specific shell ergonomics slice | current map screen tests |

### 2.2H Canonical Formula / Policy Owner

This follow-on plan does not intentionally move formula or policy owners. If a
phase touches math/constants/policy, that phase must be split out and updated
here first.

### 2.2I Stateless Object / Singleton Boundary

No new singleton/object owner is planned by default. New objects are allowed
only for stateless shell helpers and must remain non-authoritative.

### 2.3 Time Base

No time-base changes are planned by default.

If a later phase touches replay cadence, sensor cadence, or timer-driven shell
behavior, it must cite:

- `core/time/src/main/java/com/trust3/xcpro/core/time/Clock.kt`
- `app/src/main/java/com/trust3/xcpro/di/TimeModule.kt`

### 2.4 Threading and Cadence

- Dispatcher ownership: unchanged unless a dedicated seam phase says otherwise
- Primary cadence/gating sensor: unchanged
- Hot-path latency budget: unchanged

### 2.4A Logging and Observability Contract

| Boundary / Callsite | Logger Path | Sensitive Data Risk | Gating / Redaction | Temporary Removal Plan |
|---|---|---|---|---|
| map shell hotspots touched in this program | `AppLogger` by default | low unless replay/location payloads are printed | DEBUG-only justification if platform `Log.*` remains | remove or normalize in the same slice |

### 2.5 Replay Determinism

- Deterministic for same input: unchanged
- Randomness used: unchanged
- Replay/live divergence rules: unchanged

Replay lanes are seam-gated follow-on candidates, not default implementation
targets for this plan.

### 2.6 Enforcement Coverage

| Risk | Rule Reference | Guard Type | File/Test |
|---|---|---|---|
| shell regrows into mixed owner files | AGENTS ownership defaults | `enforceRules` + phase seam lock | `scripts/ci/enforce_rules.ps1` + phase docs |
| shell cleanup turns into fake owner move | AGENT phased execution + architecture rules | phase stop rule + review | this plan + parent plans |
| residual seam moves happen without proof | dependency-direction / SSOT rules | mandatory seam pass before each candidate lane | this plan |

## 3) Data Flow (Before -> After)

Current and target follow-on flow:

```text
Owner feature/runtime flows -> existing shell adapters/bindings -> MapScreenViewModel / shell inputs -> map/ui composables
```

After this program, the intended difference is narrower shell files and clearer
review boundaries, not a new runtime architecture.

## 4) Implementation Phases

### Phase 0 - Residual Seam Baseline

- Goal:
  - classify the remaining biggest files as:
    - shell ergonomics
    - candidate owner move
    - leave-as-is
- Files to change:
  - this plan
  - parent plan references if the order changes
- Ownership/file split changes in this phase:
  - none
- Tests to add/update:
  - none
- Exit criteria:
  - every top hotspot has a lane assignment before implementation

### Phase 1 - Map UI Shell Ergonomics

- Goal:
  - reduce the heaviest shell-composition files without changing ownership
- Primary targets:
  - `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenContentRuntime.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapBottomSheetTabs.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/ui/OverlayPanels.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapOverlayStack.kt`
- Ownership/file split changes in this phase:
  - split broad shell files into narrower shell section/binding files only
  - do not create a new owner or new cross-module API
- Tests to add/update:
  - existing compose/unit tests that cover map shell behavior
  - new shell section tests only if the split creates isolated behavior worth locking
- Exit criteria:
  - touched shell files are smaller and more focused
  - behavior is unchanged
  - no new state owner appears

### Phase 2 - Shell API and Binding Ergonomics

- Goal:
  - reduce broad shell fan-out around the screen/viewmodel boundary
- Primary targets:
  - `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenViewModel.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenScaffoldInputs.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenBindingGroups.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenRootHelpers.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenRuntimeEffects.kt`
- Ownership/file split changes in this phase:
  - extract narrower shell binders/adapters/helpers only when they reduce API breadth
  - do not move business logic into the ViewModel or UI
- Tests to add/update:
  - viewmodel/shell binding tests where signatures or grouping change
- Exit criteria:
  - shell APIs are easier to review and trace
  - `MapScreenViewModel` does not gain new mixed responsibilities

### Phase 3 - Residual Candidate Lanes (Seam-Gated)

- Goal:
  - inspect non-shell hotspots and only implement clean moves if a seam lock proves the owner
- Landed seam decisions so far:
  - 2026-03-16 replay lane seam pass: leave replay in place for now.
    The current replay runtime is split between:
    - map-shell integration owners in `feature:map`
      (`MapScreenReplayCoordinator`, replay snapshot controllers, replay
      display-pose/map-state behavior)
    - replay engine owners also still in `feature:map`
      (`IgcReplayController`, `IgcReplayControllerRuntime`, `ReplayPipeline`)
    There is no clean move today because the engine still depends on:
    - `VarioServiceManager`
    - `WindSensorFusionRepository`
    - `SensorFusionRepositoryFactory`
    - `LevoVarioPreferencesRepository`
    - Android/asset/document IGC loading via `Context`
    - concrete map-shell consumers that still take `IgcReplayController`
      instead of a narrower port
    A future move requires a dedicated seam that:
    - separates replay engine runtime from map-shell snapshot/display-pose logic
    - replaces concrete map consumers with `IgcReplayControllerPort` or a
      narrower replay-shell port
    - defines the owning module for Android/IGC loading separately from the
      flight/sensor replay engine
- Candidate lanes:
  - flightdata UI lane:
    - `screens/flightdata/HomeWaypointSelector.kt`
    - `screens/flightdata/WaypointUIComponents.kt`
  - replay lane:
    - `replay/IgcReplayControllerRuntime.kt`
    - `map/MapScreenReplayCoordinator.kt`
  - airspace/sensor utility lane:
    - `utils/AirspaceParser.kt`
    - `sensors/SensorRegistry.kt`
  - residual task-rendering lane:
    - `tasks/aat/rendering/AATMapRenderer.kt`
    - `gestures/CustomMapGestures.kt`
- Ownership/file split changes in this phase:
  - none by default
  - any move requires a dedicated seam subphase and parent doc update first
- Tests to add/update:
  - lane-specific only after the owner is proven
- Exit criteria:
  - either:
    - a clean next owner move is identified and queued as a separate plan, or
    - the lane is explicitly marked "stay put" with rationale

### Phase 4 - Hardening and Closeout

- Goal:
  - prevent shell drift after the ergonomics program
- Files to change:
  - `scripts/ci/enforce_rules.ps1`
  - active refactor docs if the final boundary/closeout changes
- Ownership/file split changes in this phase:
  - add follow-on guards only where a phase proved a new drift risk
- Tests to add/update:
  - `enforceRules` coverage
- Exit criteria:
  - final shell ergonomics result is documented
  - any deferred candidate lane is called out explicitly
  - required checks pass for the final code-bearing slice

## 5) Test Plan

- Unit tests:
  - shell section tests where new extracted shell helpers carry isolated behavior
- Replay/regression tests:
  - only if Phase 3 opens the replay lane
- UI/instrumentation tests:
  - only if a shell split changes a user-interaction surface enough to warrant smoke coverage
- Degraded/failure-mode tests:
  - logging and shell fallbacks only where touched
- Boundary tests for removed bypasses:
  - any new shell binder/helper split must keep owner logic out of UI

Required checks for each code-bearing phase:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

## 6) Acceptance

This follow-on plan is complete only when:

- shell ergonomics phases land without violating ownership boundaries
- any candidate owner move is either promoted into its own seam-locked plan or
  explicitly deferred
- no implementation is justified by line count alone
- phase summaries include an evidence-based quality rescore per
  `docs/ARCHITECTURE/AGENT.md`

## 7) Stop Rules

- stop if a phase starts inventing new owners or new modules without a seam pass
- stop if a proposed split only lowers line count but keeps the same mixed file
  responsibilities
- stop if replay/sensor/task rendering changes would alter determinism without
  dedicated proof
- stop if the only reason to proceed is to chase the deferred numeric target
