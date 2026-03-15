# Feature:Map Right-Sizing Release-Grade Phased IP

## Purpose

Release-grade phased execution contract for the `feature:map` right-sizing
program. This is the pragmatic path to the user's requested "genius-grade"
outcome: reduce `feature:map` by fixing ownership, not by gaming file counts.

Read first:

1. `docs/ARCHITECTURE/ARCHITECTURE.md`
2. `docs/ARCHITECTURE/CODING_RULES.md`
3. `docs/ARCHITECTURE/PIPELINE.md`
4. `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`
5. `docs/ARCHITECTURE/CONTRIBUTING.md`
6. `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`
7. `docs/ARCHITECTURE/AGENT.md`
8. `docs/refactor/Feature_Map_Right_Sizing_Master_Plan_2026-03-15.md`
9. `docs/refactor/Map_Screen_Shell_Ownership_Extraction_Plan_2026-03-14.md`
10. `docs/refactor/Feature_Map_Settings_Lane_Release_Grade_Phased_IP_2026-03-15.md`
11. `docs/refactor/Feature_Map_Autonomous_Agent_Execution_Contract_2026-03-15.md`

## 0) Metadata

- Title: Recover `feature:map` from residual bucket to shell-only owner
- Owner: Codex
- Date: 2026-03-15
- Issue/PR: TBD
- Status: Draft
- Execution rules:
  - This is an ownership-recovery track, not a file-splitting vanity track.
  - Follow phases in order. Do not skip to local cleanup because a file looks
    large.
  - No phase counts as complete unless it removes a real owner boundary from
    `feature:map`.
  - After each landed phase, run a focused seam/code pass unless the active
    plan already proves the next phase boundary is unchanged.
  - If that seam/code pass reveals an in-scope fix for the landed phase or the
    next phase, fix it and continue automatically; only stop when the plan is
    wrong or the fix would widen the seam.
  - Do not create `feature:flight-runtime` until the Phase 2 seam pass confirms
    the exact runtime contract and an ADR is ready.
  - Keep `feature:map` focused on shell composition, route registration seams,
    and map-specific UI wiring only.
  - Keep replay/time-base behavior unchanged while ownership moves.
- Progress note:
  - 2026-03-15: baseline measured and master plan created from the current
    post-task-runtime map state.
  - 2026-03-15: this phased IP was created to convert that baseline into a
    release-grade execution contract with gates, stop rules, and acceptance
    evidence.
  - 2026-03-15: Phase 1A landed. The General Settings host/registry moved to
    `app`, `feature:map` now forwards open intents only, and the old
    `SettingsDfRuntime*.kt` host stack was deleted.
  - 2026-03-15: Phase 1B landed. Forecast/weather/units settings wrappers now
    resolve from their owner modules, `feature:map` no longer owns their
    duplicate wrapper files, and weather sheet behavior tests moved to
    `feature:weather`.
  - 2026-03-15: Settings-lane Phase 1 landed. Thermalling settings UI/tests
    moved to `feature:profile`, while `feature:map` kept only thermalling
    runtime automation.
  - 2026-03-15: Settings-lane Phase 2 landed. Polar/glider settings UI,
    settings-side glider policy, glider DI bindings, and glider/polar tests
    moved to `feature:profile`.
  - 2026-03-15: Settings-lane Phase 3A landed. Layout settings UI/use-case/
    ViewModel and layout owner tests moved to `feature:profile`.
  - 2026-03-15: Settings-lane Phase 3B landed. Theme authority is singular
    under the profile theme owner path, generic color-picker UI now lives in
    `core:ui`, and the colors screen/use-case/ViewModel moved to
    `feature:profile` while `feature:map` kept only the theme runtime read
    path.
  - 2026-03-15: Settings-lane Phase 4 landed. HAWK settings UI moved to
    `feature:profile`, the shared HAWK preview contract now lives in
    `feature:variometer`, and Parent Phase 2A later moved the live HAWK
    runtime owner there too.
  - 2026-03-15: Autonomous execution contract added so the remaining phases
    run one at a time with mandatory Gradle gates after every landed phase.
  - 2026-03-15: Parent Phase 2A landed. `feature:variometer` now owns the live
    HAWK runtime (`HawkVarioUseCase`, repository/config/engine stack, and
    runtime ports), while `feature:map` keeps only temporary sensor/source
    adapters until Parent Phase 2B removes them.
  - 2026-03-15: Parent Phase 2B seam pass found the flight-runtime cut still
    cannot stay as one code phase because:
    - `FlightDataCalculatorEngine` / `SensorFusionRepositoryFactory` still pull
      `StillAirSinkProvider` from `feature:profile` and
      `AudioFocusManager` / `VarioAudioController` from `feature:variometer`,
      so the future runtime module needs lightweight runtime ports first rather
      than direct dependencies on those UI-heavy feature modules.
    - `ReplayPipeline`, `ReplayPipelineFactory`, and
      `IgcReplayControllerRuntime` remain replay shell owners because they also
      depend on `VarioServiceManager`, `LevoVarioPreferencesRepository`,
      `Context`, and IGC load/config behavior.
    - the clean first move is the runtime foundations:
      `FlightDataRepository`, `FlightDisplayMapper`, `FlightStateRepository`,
      `WindSensorFusionRepository`, `ReplaySensorSource`,
      `ReplayAirspeedRepository`, and the raw-sensor/runtime contracts.
  - 2026-03-15: Current workspace footprint after Parent Phase 2A is
    `405` main Kotlin files / `40,669` main lines in `feature:map`, with
    `42` screen files / `4,797` screen lines remaining under
    `feature/map/src/main/java/com/example/xcpro/screens/**`.
  - 2026-03-15: Parent Phase 2 seam lock completed. The flight-runtime cut is
    now explicitly split because:
    - `FlightDataCalculatorEngine` still depends on the map-owned
      `HawkVarioRepository`, so a bulk runtime move would create a
      `feature:flight-runtime -> feature:map` back-edge.
    - `MapOrientationManager` is map-specific control/state and stays out of
      the flight-runtime move; only the sensor-facing orientation inputs are in
      scope for extraction.
    - `flightdata/**` and `replay/**` are mixed buckets; only the runtime SSOT
      owners move in Phase 2, while waypoint/settings/replay shell files stay
      outside the cut.
  - 2026-03-16: Full-plan seam pass found additional Phase 2 and Phase 3
    constraints that were under-specified:
    - `WindSensorFusionRepository` is not a safe solo move; Phase 2B.1 must
      treat the wind-runtime owner set together:
      `AirspeedDataSource`, `ExternalAirspeedRepository`,
      `ReplayAirspeedRepository`, `WindSensorInputs`,
      `WindSensorInputAdapter`, and `WindSensorFusionRepository`, while
      explicitly seam-locking `WindSelectionUseCase` and DI-backed
      `WindOverrideSource`.
    - `feature:flight-runtime` must not gain direct dependencies on the
      UI-heavy `feature:profile` or `feature:variometer` modules; lightweight
      runtime ports remain mandatory before moving
      `FlightDataCalculatorEngine` / `SensorFusionRepositoryFactory`.
    - replay shell files remain harder exclusions than the earlier draft
      implied: `ReplayPipeline`, `ReplayPipelineFactory`,
      `IgcReplayControllerRuntime`, `IgcReplayControllerRuntimeLoadAndConfig`,
      `IgcReplayControllerRuntimePlayback`, and `IgcReplayController` stay in
      the replay shell lane.
    - Phase 2C must name the root orientation files explicitly
      (`OrientationDataSource`, `OrientationDataSourceFactory`,
      `OrientationSensorSource`) because they are not confined to
      `orientation/**`.
    - Phase 3 is not just a generic `map/**` burn-down; the first shell/runtime
      bridge hotspots are `MapOverlayManager` and the residual bridge load
      around `MapScreenViewModel`.
  - 2026-03-16: Parent Phase 2B.1 seam lock found one more transitive
    dependency set that must move with the foundations:
    - the moved repositories/contracts still depend on pure sensor and
      flight-data models physically in `feature:map`
      (`SensorData.kt`, `FlyingState.kt`, `FlyingStateDetector.kt`,
      `FlightMetricsModels.kt`)
    - the wind foundations still depend on split wind contracts physically in
      `feature:profile` / `feature:map`
      (`WindOverrideSource`, `WindOverride`, `WindSource`, `WindVector`,
      `WindInputs.kt`, `WindState.kt`)
    - Phase 2B.1 therefore includes those pure contracts/models plus the wind
      helper/domain set required by `WindSensorFusionRepository`
      (`CirclingDetector`, `CirclingWind`, `WindMeasurementList`, `WindStore`,
      `WindSelectionUseCase`)
  - 2026-03-16: Parent Phase 2B.1 landed. `feature:flight-runtime` now owns
    the runtime foundations:
    - raw sensor contracts and sensor/flight-data runtime models
    - `FlightDataRepository` and `FlightDisplayMapper`
    - `FlightStateRepository` / `FlightStateSource`
    - `ReplaySensorSource`, `ReplayAirspeedRepository`,
      `ExternalAirspeedRepository`
    - the wind input/fusion owner set and its pure helper/domain contracts
    - the shared wind override/model contracts previously split across map and
      profile
    - `feature:map` keeps live sensor owners, replay shell orchestration, and
      DI composition; `feature:profile` keeps wind override persistence only
  - 2026-03-16: Current workspace footprint after Parent Phase 2B.1 is
    `381` main Kotlin files / `38,521` main lines in `feature:map`;
    `feature:flight-runtime` now owns `28` main Kotlin files / `2,218` lines.
  - 2026-03-16: Parent Phase 2B.2A landed. `feature:flight-runtime` now also
    owns the shared glider/audio/HAWK runtime contracts and models
    (`StillAirSinkProvider`, `SpeedBoundsMs`, `VarioAudioSettings`,
    `VarioAudioControllerPort` / `VarioAudioControllerFactory`,
    `HawkAudioVarioReadPort`), while `feature:profile` and
    `feature:variometer` keep the concrete implementations and `feature:map`
    consumes those ports without direct profile/variometer concrete
    dependencies in the remaining fusion entrypoints.
  - 2026-03-16: Parent Phase 2B.2B landed. `feature:flight-runtime` now owns
    the remaining fusion engine/runtime-only sensor pipeline set:
    `SensorFusionRepository`, `FlightDataCalculator`,
    `FlightDataCalculatorEngine`, `FlightDataCalculatorEngineLoops`,
    `FlightDataEmitter`, `FlightCalculationHelpers`, `VarioSuite`,
    `VarioDiagnosticsSample`, the `sensors/domain/**` runtime policies, and
    the pure `vario/**` calculator set. `feature:map` keeps live sensor/device
    owners, replay shell/controllers, DI composition, and shell-facing bridge
    tests.
  - 2026-03-16: Current workspace footprint after Parent Phase 2B.2B is
    `341` main Kotlin files / `34,482` main lines in `feature:map`;
    `feature:flight-runtime` is `73` main Kotlin files / `6,322` lines.
  - 2026-03-16: Parent Phase 2C seam lock found the orientation extraction must
    be split:
    - `OrientationDataSource` and `OrientationDataSourceFactory` still depend
      on the live sensor owner `UnifiedSensorManager`, so they cannot move as a
      direct runtime-owner cut without a new raw-orientation sensor port.
    - `OrientationDataSource` also depends on `MapFeatureFlags` from
      `feature:map-runtime`, so the stationary-heading debug override must be
      extracted behind a narrow runtime policy contract before the adapter can
      leave `feature:map`.
    - `OrientationEngine` is not a clean runtime move yet because it depends on
      `MapOrientationSettings` from `feature:profile`; Phase 2C therefore
      starts with pure orientation support owners only and defers controller/
      settings-coupled code until a later subphase.
    - `app` now carries a direct composition-root dependency on
      `feature:flight-runtime` because generated Hilt component sources import
      moved runtime owners directly.
  - 2026-03-16: Parent Phase 2C.1 landed. `feature:flight-runtime` now owns
    the pure orientation support set (`HeadingResolver`, `OrientationClock`,
    `OrientationMath`, and the clock binding module), while `feature:map`
    keeps `MapOrientationManager`, `OrientationEngine`, `HeadingJitterLogger`,
    and the live orientation data-source adapter path.
  - 2026-03-16: Current workspace footprint after Parent Phase 2C.1 is
    `337` main Kotlin files / `34,357` main lines in `feature:map`;
    `feature:flight-runtime` is `77` main Kotlin files / `6,450` lines.
  - 2026-03-16: Parent Phase 2C.2 landed. `feature:flight-runtime` now owns
    the reusable orientation input assembly (`OrientationSensorSource`,
    `OrientationDataSource`, `OrientationDataSourceFactory`, and the narrow
    orientation input/policy contracts), while `feature:map` keeps only thin
    adapters over `UnifiedSensorManager`, the stationary-heading policy, the
    map-specific `MapOrientationManager`, `OrientationEngine`, and
    `HeadingJitterLogger`.
  - 2026-03-16: Current workspace footprint after Parent Phase 2C.2 is
    `337` main Kotlin files / `33,969` main lines in `feature:map`;
    `feature:flight-runtime` is `81` main Kotlin files / `6,875` lines.
  - 2026-03-16: Parent Phase 3 seam lock corrected the first runtime burn-down
    slice:
    - `MapOverlayManager` is already a thin shell wrapper over
      `MapOverlayManagerRuntime`; spending a phase on that wrapper first would
      be churn, not an owner move.
    - `MapScreenViewModel` is now mostly shell orchestration; local cleanup
      there is lower value than moving the remaining MapLibre runtime owners.
    - the clean first Phase 3 owner move is the visual/runtime primitive set:
      `BlueLocationOverlay`, `SailplaneIconBitmapFactory`, and
      `MapScaleBarController`.
    - `MapInitializer` and `SnailTrailManager` stay for a later Phase 3 slice
      because they still mix shell/use-case dependencies with runtime work.
    - `DistanceCirclesOverlay` is dead map-layer runtime and should be treated
      as delete/hardening work, not a live runtime owner move.
  - 2026-03-16: Parent Phase 3 first runtime-owner slice landed.
    `feature:map-runtime` now owns the visual/runtime primitive set
    (`BlueLocationOverlay`, `SailplaneIconBitmapFactory`,
    `MapScaleBarController`, and the narrow `MapScaleBarRuntimeState`
    contract), while `feature:map` keeps shell wiring plus the
    `MapScreenState` runtime-handle implementation of that scale-bar port.
  - 2026-03-16: Current workspace footprint after the first Parent Phase 3
    slice is `334` main Kotlin files / `33,364` main lines in `feature:map`;
    `feature:map-runtime` is `78` main Kotlin files / `7,055` lines.
  - 2026-03-16: Parent Phase 3 seam lock after the visual/runtime primitive
    move found the next clean runtime owner set is the snail-trail cluster:
    - `MapInitializer` remains shell-owned because it still composes
      `MapInitializerDataLoader`, task render sync, orientation hooks, and
      map-style/bootstrap sequencing.
    - the next owner move is the trail runtime set
      (`SnailTrailManager`, `SnailTrailOverlay`, trail render helpers, and the
      `TrailProcessor` / `TrailUpdateResult` runtime path), coupled through a
      narrow shell-held trail runtime state port instead of `MapScreenState`.
    - `DistanceCirclesOverlay` remains dead runtime residue for a later
      hardening/delete slice, not the next live owner move.
  - 2026-03-16: Parent Phase 3 trail runtime slice landed. `feature:map-runtime`
    now owns the snail-trail runtime set:
    - `SnailTrailManager` / `SnailTrailOverlay`
    - trail render helpers and the trail-domain runtime path
      (`TrailProcessor`, `TrailUpdateInput`, `TrailUpdateResult`,
      `TrailRenderState`, `TrailTimeBase`)
    - the narrow `SnailTrailRuntimeState` bridge
    `feature:map` now keeps only shell-held trail handles via
    `MapScreenState` plus shell effect wiring and composition.
  - 2026-03-16: Current workspace footprint after the Parent Phase 3 trail
    slice is `307` main Kotlin files / `31,250` main lines in `feature:map`;
    `feature:map-runtime` is `106` main Kotlin files / `9,180` lines.
  - 2026-03-16: Follow-up seam pass after the trail slice closed Parent Phase
    3 as the live runtime burn-down lane:
    - `MapInitializer` remains shell-owned because it still mixes
      `MapInitializerDataLoader`, task-render sync, orientation hooks, and
      bootstrap sequencing.
    - no further clean runtime owner move remained without widening the seam
      into shell/bootstrap churn.
    - the correct next step was Parent Phase 4 hardening, starting with the
      dead `DistanceCirclesOverlay` residue.
  - 2026-03-16: Parent Phase 4 first hardening slice landed. The dead
    `DistanceCirclesOverlay` file, shell state/status residue, and stale
    bootstrap comments were removed; `MapInitializerDataLoader` switched to
    `AppLogger`; and `enforceRules` now guards against reintroducing the dead
    overlay path.
  - 2026-03-16: Parent Phase 4 second hardening slice landed. The remaining
    touched shell/runtime files `MapGestureSetup`, `MapRuntimeController`, and
    `MapScreenSections` now use `AppLogger`, and `enforceRules` now fails if
    raw `Log.*` calls are reintroduced in those hardened shell files.
  - 2026-03-16: Follow-up seam pass confirmed `MapInitializer`,
    `MapOverlayStack`, and `MapScreenContentRuntime` remain shell owners rather
    than hidden runtime owners; the correct closeout is honest shell-hardening
    and drift guards, not another forced extraction phase.
  - 2026-03-16: Parent Phase 4 closeout seam confirmed the remaining shell set
    is now explicit:
    - `MapInitializer` = shell bootstrap/composition owner
    - `MapOverlayStack` = shell overlay composition owner
    - `MapScreenContentRuntime` = shell screen-content composition owner
    - `MapGestureSetup`, `MapRuntimeController`, and `MapScreenSections` =
      hardened shell adapters
    The architecture target is met at the shell boundary even though the
    original numeric target is not; the numeric target is deferred rather than
    forced through churn.
  - 2026-03-16: Current workspace footprint after the second Parent Phase 4
    slice is `306` main Kotlin files / `30,900` main lines in `feature:map`;
    `feature:map-runtime` remains `106` main Kotlin files / `9,180` lines.

## 1) Scope

- Problem statement:
  - `feature:map` is still too broad to be a stable long-term owner.
  - Current production footprint on 2026-03-15:
    - `459` main Kotlin files
    - `47,299` main Kotlin lines
  - Largest remaining ownership buckets:
    - `map`: `194` files / `18,171` lines
    - `screens`: `77` files / `9,370` lines
    - `sensors`: `48` files / `4,972` lines
    - residual `tasks`: `19` files / `2,757` lines
  - Largest remaining files confirm the problem is mixed ownership rather than
    one bad hotspot:
    - `screens/navdrawer/ThermallingSettingsScreen.kt` (`427`)
    - `screens/navdrawer/HawkVarioSettingsScreenRuntime.kt` (`412`)
    - `sensors/FlightCalculationHelpers.kt` (`409`)
    - `sensors/domain/CalculateFlightMetricsRuntime.kt` (`392`)
    - `weather/wind/data/WindSensorFusionRepository.kt` (`384`)
    - `OrientationDataSource.kt` (`382`)
    - `map/ui/MapScreenContentRuntime.kt` (`382`)
    - `map/ui/MapBottomSheetTabs.kt` (`373`)
- Why now:
  - The task/AAT ownership program already removed one major map-adjacent drift
    path.
  - Continuing with local helper churn inside `feature:map` will now create more
    churn than value.
  - The remaining size problem is broad ownership, so the next professional move
    is a phased owner extraction plan.
- In scope:
  - Define the shell-only target state for `feature:map`.
  - Sequence the remaining owner moves by ROI and architectural correctness.
  - Add measurable size and boundary targets for each phase.
  - Define required seam passes, proof, and hardening steps.
- Out of scope:
  - Product redesign.
  - Big-bang rewrites.
  - File splitting that does not improve ownership.
  - New features justified only by this refactor track.
- User-visible impact:
  - No intended product behavior change.
  - Expected gains are change safety, compile isolation, and clearer ownership.
- Rule class touched: Invariant

## 2) Current Contracts To Preserve

- `feature:map` remains the screen entry shell for the live map experience until
  owner moves are completed.
- Replay behavior remains deterministic for the same inputs.
- Map runtime/render behavior stays owned by map runtime boundaries, not moved
  into UI or app convenience layers.
- Feature-specific business logic remains with its owning feature and does not
  get re-centralized into a new shell helper.
- Route registration may remain in `app` or map shell seams, but screen-local
  state and logic must move with their owning feature.
- General Settings now has one app-owned host with two supported entry modes:
  - map-launched host open requests from `feature:map`
  - `SettingsRoutes.GENERAL` compatibility requests in
    `app/src/main/java/com/example/xcpro/AppNavGraph.kt`

## 3) Ownership Model

Target steady-state after this program:

- `feature:map`
  - shell composition
  - route entrypoints
  - map-specific UI wiring
  - thin adapters over runtime and feature owners

- `feature:map-runtime`
  - MapLibre/runtime/render/overlay owners
  - gesture/runtime contracts
  - map interaction helpers that are runtime, not shell

- owner feature modules
  - settings screens and settings-local ViewModels
  - feature-specific domain logic and repositories
  - task, traffic, weather, forecast, profile, and vario ownership already
    defined elsewhere

- target new `feature:flight-runtime`
  - sensor/orientation/fusion runtime
  - wind-fusion runtime
  - flight metric runtime calculations

Implementation shape requirement:

- `feature:map` should end as a shell, not a residual catch-all.
- Any new module must be justified by runtime ownership, not by file count
  aesthetics.
- Each move must keep dependency direction explicit and testable.
- `feature/map/src/main/java/com/example/xcpro/screens/**` is not one phase
  lane. Treat it as four separate ownership buckets:
  - cross-feature general-settings host and registry
  - true owner-module settings wrappers
  - non-settings flows such as `FlightMgmt`
  - separate map/task/diagnostic surfaces such as `TaskRouteScreen`,
    diagnostics, and map controls
- Reuse owner-module settings content that already exists in
  `feature:profile`, `feature:traffic`, `feature:forecast`,
  `feature:weather`, `feature:weglide`, and `feature:igc`.
  Do not rewrite those screens just to satisfy the move.

## 4) Architecture Contract

### 4.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Map shell UI state and route composition | `feature:map` shell ViewModels and UI hosts | shell APIs and UI state flows | feature-specific settings or runtime state parked in `feature:map` |
| Map runtime/render/overlay state | `feature:map-runtime` owners | runtime ports and runtime contracts | duplicate runtime owners in `feature:map` |
| Feature-specific settings screens and screen-local state | owner feature module or app route registrar | feature entrypoints | settings screens continuing to live in `feature:map/screens` |
| Sensor/orientation/fusion runtime state | target `feature:flight-runtime` owners | runtime-facing ports | sensor/fusion repositories and calculations staying map-owned |
| Wind-fusion runtime state | target `feature:flight-runtime` owners | runtime-facing ports | `feature:map/weather/wind/data/**` remaining shell-owned |

### 4.1A State Contract

| State Item | Authoritative Owner | Allowed Mutators | Read Path | Derived From | Persistence Owner | Reset/Clear Conditions | Time Base | Required Tests |
|---|---|---|---|---|---|---|---|---|
| Map shell state | `feature:map` screen VM and shell hosts | shell intents only | Compose/UI shell | existing feature facades and shell-local UI state | existing owners only | existing screen lifecycle rules | unchanged | map shell tests |
| Map runtime/render state | `feature:map-runtime` owners | runtime controllers/managers only | map shell ports | runtime inputs and render contracts | existing owners only | map/style/lifecycle rules | unchanged | map-runtime tests |
| Feature settings screen state | owner feature module | owner screen/viewmodel intents | owner feature entrypoints | owner repositories and use-cases | existing owners only | owner lifecycle rules | unchanged | moved screen tests |
| Sensor/orientation/fusion runtime | target `feature:flight-runtime` | fusion use-cases and repositories only | consumed through narrow runtime ports | sensor/device/replay inputs | existing owners only | current runtime lifecycle | monotonic/replay/wall unchanged by extraction | fusion and replay tests |

### 4.2 Dependency Direction

Confirm dependency flow remains:

`app -> feature:map -> feature:map-runtime + owner feature modules`

And inside each feature:

`UI -> domain -> data`

- Modules/files touched:
  - `feature:map`
  - `feature:map-runtime`
  - owner feature modules for moved screens
  - target new `feature:flight-runtime`
  - `app` route registration seams where needed
- Boundary risks:
  - replacing one `feature:map` catch-all with a new runtime catch-all
  - creating `feature:map-runtime -> feature:map` back-edges
  - pushing Android-heavy runtime code into `core:*` instead of a feature
    runtime boundary

### 4.2A Reference Pattern Check

| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| `docs/refactor/Feature_Map_Right_Sizing_Master_Plan_2026-03-15.md` | current baseline, targets, and owner buckets for the same problem | keep the owner-first phase ordering and numeric file-count gates | this phased IP adds mandatory proof, stop rules, and release gates |
| `docs/refactor/Map_Screen_Shell_Ownership_Extraction_Plan_2026-03-14.md` | recent successful map-shell ownership reduction in the same module | phase-by-phase seam locks, ownership tables, and explicit shell-vs-runtime boundaries | this program is broader and crosses module boundaries, not one seam only |
| `docs/refactor/Task_AAT_Ownership_Release_Grade_Phased_IP_2026-03-15.md` | release-grade ownership recovery plan with hard gates | use the same release-grade format and enforcement mindset | this plan targets module right-sizing, not a single SSOT seam |

### 4.2B Boundary Moves

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Feature-specific settings/navdrawer screens | `feature:map/screens` | owner modules or app route registration shell | settings screens are not map-shell ownership | compile + route smoke + moved tests |
| Sensor/orientation/wind-fusion runtime and flight calculations | `feature:map/sensors`, `orientation`, `weather/wind/data`, `flightdata` | target `feature:flight-runtime` | runtime/business logic is not map shell ownership | compile + fusion tests + replay/timebase tests |
| Remaining heavy non-UI map runtime owners | `feature:map` | `feature:map-runtime` | keep `feature:map` shell-only | compile + runtime tests |
| Residual task wrappers/helpers still living in `feature:map` | `feature:map` residual task package | `feature:tasks` or `feature:map-runtime` as appropriate | remove owner drag from map shell | compile + task/map tests |

### 4.2C Bypass Removal Plan

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `feature/map/src/main/java/com/example/xcpro/screens/**` | non-map owner screens still compiled as part of the map shell | move screens to owner modules and leave only route registration seams | Phase 1 |
| `feature/map/src/main/java/com/example/xcpro/sensors/**` and related runtime packages | non-map runtime/fusion code is map-owned | move behind `feature:flight-runtime` ports | Phase 2 |
| residual heavy runtime owners in `feature/map/src/main/java/com/example/xcpro/map/**` | runtime code still compiled with the shell | move remaining runtime owners to `feature:map-runtime` | Phase 3 |
| `feature:map` growth after owner moves | shell becomes a new residual bucket again | enforceRules drift guards + docs/ADR sync | Phase 4 |

### 4.2D File Ownership Plan

| File | New / Existing | Owner / Responsibility | Why Here | Why Not Another Layer/File | Split Needed? |
|---|---|---|---|---|---|
| `docs/refactor/Feature_Map_Right_Sizing_Release_Grade_Phased_IP_2026-03-15.md` | New | release-grade execution contract for the right-sizing track | active phased plan belongs in `docs/refactor` | not a durable ADR by itself | No |
| `docs/refactor/Feature_Map_Right_Sizing_Master_Plan_2026-03-15.md` | Existing | baseline summary and strategy pointer | already holds the measured baseline | keep as overview, not the active execution contract | No |
| `feature/map/src/main/java/com/example/xcpro/screens/**` | Existing | Phase 1 screen inventory to reduce | largest non-shell UI/settings block left in `feature:map` | not runtime and not shell-only | Yes |
| `feature/map/src/main/java/com/example/xcpro/sensors/**` | Existing | Phase 2 runtime extraction set | largest non-map business/runtime block left | not shell UI and not MapLibre runtime | Yes |
| `feature/map/src/main/java/com/example/xcpro/orientation/**` and `weather/wind/data/**` | Existing | Phase 2 runtime extraction set | tightly coupled to flight/sensor fusion | should move with fusion owner, not alone | Yes |
| `feature/map/src/main/java/com/example/xcpro/map/**` | Existing | must end as shell-only plus map-specific adapters | core map package is still too mixed | some stays shell-owned, heavy runtime does not | Yes |
| `feature/map-runtime/src/main/java/**` | Existing | Phase 3 runtime landing zone | existing map runtime owner | better than leaving runtime in shell | Possible |
| `feature/flight-runtime/**` | New target module | sensor/orientation/fusion runtime owner | Phase 2 needs a runtime home with explicit ownership | not `core:*` because this is feature/runtime logic | Yes |
| `scripts/ci/enforce_rules.ps1` | Existing | drift guards after boundary moves | current repo enforcement lives here | required to prevent regression | No |

### 4.2E Module and API Surface

| Contract / API | Owner | Consumers | Visibility | Why Needed | Compatibility / Removal Plan |
|---|---|---|---|---|---|
| owner-module settings entry surfaces | owner feature module | `app` and `feature:map` route registrars | public cross-module | removes `feature:map` ownership of unrelated settings screens | keep `app` as final route registrar where appropriate |
| `feature:flight-runtime` runtime ports | target new module | `feature:map`, `feature:map-runtime`, possible replay/runtime consumers | public cross-module | extracts non-map fusion/runtime owners cleanly | define only after Phase 2 seam pass and ADR |
| narrowed shell-to-runtime adapters | `feature:map` or `feature:map-runtime` as appropriate | map shell and runtime only | internal or minimal public | keeps shell thin while runtime owners move | remove temporary wrappers if they stop adding value |

### 4.2F Scope Ownership and Lifetime

| Scope / Owner | Why It Exists | Dispatcher | Cancellation Trigger | Why Not Caller-Owned / Existing Scope |
|---|---|---|---|---|
| Existing feature/runtime scopes only | this program moves owners; it should not invent new lifetime models casually | unchanged | unchanged | module right-sizing must preserve proven runtime lifetimes |

### 4.2G Compatibility Shim Inventory

| Shim / Bridge | Owner | Reason | Target Replacement | Removal Trigger | Test Coverage |
|---|---|---|---|---|---|
| route registration wrappers left in `feature:map` | `feature:map` temporarily | keep app and drawer wiring stable while screens move | owner-module entry surfaces | remove once routes are fully owner-owned | compile + route smoke |
| shell adapters over runtime owners | `feature:map` temporarily | keep shell stable while runtime owners move | direct shell-to-runtime contracts once stable | remove only if they stop adding value | compile + runtime tests |

### 4.2H Canonical Formula / Policy Owner

| Formula / Constant / Policy | Canonical Owner File | Reused By | Why This Owner Is Canonical | Temporary Duplicates Allowed? |
|---|---|---|---|---|
| flight metrics, sensor fusion, and wind-fusion policy | current `sensors/domain` and `weather/wind/data` owners, then `feature:flight-runtime` | map shell/runtime and replay/fusion consumers | these are non-UI runtime policies | No |

### 4.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| sensor and fusion cadence | Monotonic | runtime sensor processing remains unchanged |
| replay-derived runtime inputs | Replay | replay determinism must not drift during extraction |
| persisted settings timestamps | Wall | existing persistence semantics stay unchanged |

Explicitly forbidden comparisons:

- Monotonic vs wall
- Replay vs wall

### 4.4 Threading and Cadence

- Dispatcher ownership:
  - unchanged by default; right-sizing must preserve current owners
- Primary cadence/gating sensor:
  - existing runtime cadence only
- Hot-path latency budget:
  - Phase 2 and Phase 3 must not add shell indirection to hot runtime paths

### 4.5 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No new randomness allowed
- Replay/live divergence rules:
  - unchanged; owner moves must preserve current replay contracts

### 4.5A Error and Degraded-State Contract

| Condition | Category | Owner | User-Visible Behavior | Retry / Fallback Policy | Test Coverage |
|---|---|---|---|---|---|
| route not found after screen move | Terminal during build/test or user action if wiring regresses | build wiring + route registration owner | screen is unreachable if broken | block phase exit; do not ship | compile + route smoke |
| missing runtime binding during module move | Terminal during build/test | build + DI wiring | build fails | fix before phase exit | compile + unit tests |
| replay/runtime drift after fusion extraction | Degraded | runtime owner | behavior mismatch on replay/live parity | block phase exit | replay regression tests |

### 4.6 Enforcement Coverage

| Risk | Rule Reference | Guard Type | File/Test |
|---|---|---|---|
| `feature:map` continues to own unrelated settings screens | module boundary + ownership defaults | review + enforceRules follow-on guard | Phase 1 guards and moved screen tests |
| sensor/fusion business logic remains map-owned | business logic out of shell + module boundaries | review + compile + unit tests | Phase 2 extraction tests |
| `feature:map-runtime` gains a back-edge to `feature:map` | dependency direction | compile + review | Phase 3 runtime move gates |
| `feature:map` regrows after extraction | AGENTS ownership defaults | enforceRules + review | Phase 4 guards in `scripts/ci/enforce_rules.ps1` |

### 4.7 Visual UX SLO Contract

| Outcome | SLO ID | Baseline | Target | Validation/Test | Phase Gate |
|---|---|---|---|---|---|
| map behavior remains unchanged while owners move | existing impacted `MS-UX-*` / `MS-ENG-*` only where runtime changes | current accepted behavior | no regression | existing map evidence + targeted smoke | Phases 1-3 |
| replay and sensor-derived overlays remain stable after fusion extraction | `MS-ENG-10` and impacted replay/runtime evidence | current accepted behavior | no owner-induced cadence drift | replay/runtime evidence + targeted smoke | Phase 2 |

## 5) Data Flow (Before -> After)

Current:

```text
feature:map
  -> screens + settings
  -> sensor/orientation/fusion runtime
  -> map shell UI
  -> map runtime
  -> residual feature wrappers
```

Target:

```text
app
  -> feature:map (shell, route composition, map-only UI wiring)
  -> feature:map-runtime (MapLibre/runtime/render/overlay owners)
  -> feature:flight-runtime (sensor/orientation/fusion owners)
  -> owner feature modules (tasks/traffic/weather/forecast/profile/variometer/igc)
```

## 6) Implementation Phases

### Phase 0 - Freeze, Baseline, And Stop Rule

- Goal:
  - freeze the current `feature:map` baseline and prohibit ad hoc cleanup churn
- Files to change:
  - planning docs only
- Ownership/file split changes in this phase:
  - none in production code
- Tests to add/update:
  - none
- Exit criteria:
  - current baseline is explicit as `459` main files / `47,299` main lines
  - no new `feature:map` cleanup work starts without a seam pass and owner move
  - this phased IP is the active execution contract

### Phase 1 - General Settings Host Extraction And Screen Ownership Sweep

- Goal:
  - remove cross-feature settings aggregation from `feature:map`
  - move remaining true owner wrappers out or delete them where owner-module
    content already exists
- Required seam lock before editing:
  - preserve or intentionally replace the dual-entry general-settings model
    currently split between app routes and map-local sub-sheets
  - classify every `screens/**` file into one of these buckets before moving it:
    - general-settings host/registry
    - owner-module settings wrapper
    - non-settings flow
    - map-specific surface
  - confirm that `FlightMgmt`, `TaskRouteScreen`, diagnostics, and overlays are
    explicitly out of scope for this phase
- Files to change:
  - `app/src/main/java/com/example/xcpro/appshell/settings/**`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenScaffold.kt`
  - `app/src/main/java/com/example/xcpro/AppNavGraph.kt`
  - owner-module sheet wrappers or entry surfaces where still required
  - only the specific remaining map-owned settings wrappers that survive the
    seam lock
- Ownership/file split changes in this phase:
  - `feature:map` stops owning the cross-feature general-settings host and
    sub-sheet registry
  - owner modules remain the owners of settings content and screen-local
    ViewModels
  - direct-route registration and local modal hosting become one explicit
    shell/app concern instead of an accidental map-owned concern
- Tests to add/update:
  - `GeneralSettingsScreenPolicyTest`
  - map-open general-settings signal and route smoke
  - existing sheet-behavior tests such as weather sheet behavior
  - route smoke
  - owner-module screen tests moved with screens
  - compile proof for route registration seams
- Landed sub-slice:
  - `app` now owns the General Settings host and
    `OPEN_GENERAL_SETTINGS_ON_MAP` handling
  - `feature:map` no longer hosts General Settings and `MapModalManager` is
    airspace-only
  - deleted map-owned host files:
    - `SettingsDfRuntime.kt`
    - `SettingsDfRuntimeModels.kt`
    - `SettingsDfRuntimeCategoryGrid.kt`
    - `SettingsDfRuntimeCategoryItems.kt`
    - `SettingsDfRuntimeSubSheets.kt`
    - `SettingsDfRuntimeRouteSubSheets.kt`
  - owner-wrapper cleanup now landed for:
    - `ForecastSettingsScreen`
    - `WeatherSettingsScreen`
    - `WeatherSettingsSubSheet`
    - `UnitsSettingsScreen`
    - `UnitsSettingsViewModel`
    - `ThermallingSettingsScreen`
    - `ThermallingSettingsSubSheet`
    - `ThermallingSettingsViewModel`
    - `ThermallingSettingsUseCase`
    - `ThermallingSettingsUiState`
    - `PolarSettingsScreen`
    - `GliderViewModel`
    - `GliderUseCase`
    - `PolarCalculator`
    - `StillAirSinkProvider`
  - weather sheet behavior tests now live with the owner screen in
    `feature:weather`
  - thermalling screen/viewmodel/content tests now live with the owner screen
    in `feature:profile`
  - glider/polar repository and math contract tests now live with the owner
    implementation in `feature:profile`
  - follow-on execution contract for the remaining mixed-owner settings lane:
    `docs/refactor/Feature_Map_Settings_Lane_Release_Grade_Phased_IP_2026-03-15.md`
  - that settings lane is now complete; the next active slice is Phase 2
    flight-runtime extraction after an ADR-backed seam pass
- Exit criteria:
  - `feature:map` no longer owns the cross-feature general-settings host
  - remaining settings content in `feature:map` is either map-owned by design or
    explicitly deferred to a later phase
  - `FlightMgmt`, `TaskRouteScreen`, diagnostics, and overlays are not mixed
    into the same change lane
  - `screens` package is materially smaller
  - target after Phase 1: `<= 390` main files

### Phase 2 - Flight Runtime Extraction

- Phase ordering note:
  - Phase 2 is now split into bounded subphases. Do not attempt a one-shot
    `feature:flight-runtime` move.

#### Phase 2A - HAWK Runtime Owner Extraction

- Goal:
  - move the live HAWK runtime owner out of `feature:map` so the later
    flight-runtime module does not depend back on map code
- Required seam lock before editing:
  - identify the minimum HAWK runtime contract required by the owner module
  - keep settings preview/read APIs stable while moving the runtime owner
  - preserve one live HAWK runtime owner
- Files to change:
  - `feature/map/src/main/java/com/example/xcpro/hawk/**`
  - `feature/variometer/src/main/java/com/example/xcpro/hawk/**`
  - map-side DI adapters/bindings for any temporary runtime ports
  - HAWK tests in `feature:map` and `feature:variometer`
- Ownership/file split changes in this phase:
  - `feature:variometer` becomes the live HAWK runtime owner
  - `feature:map` supplies temporary sensor/source adapters only until the
    flight-runtime module lands
  - `feature:map` no longer owns HAWK runtime classes
- Tests to add/update:
  - HAWK engine/repository/use-case tests in `feature:variometer`
  - map adapter/binding proof if temporary ports are introduced
- Exit criteria:
  - `feature:map` no longer owns `HawkVarioRepository`,
    `HawkConfigRepository`, `HawkVarioUseCase`, or the HAWK engine helpers
  - the later flight-runtime extraction no longer requires a dependency
    back-edge into `feature:map`
- Landed result:
  - `feature:variometer` now owns `HawkVarioUseCase`, `HawkVarioRepository`,
    `HawkConfigRepository`, `HawkVarioEngine`, `HawkConfig`, `HawkOutput`,
    and the HAWK engine helper classes plus the runtime-port contracts
  - `feature:map` now owns only `MapHawkSensorStreamAdapter`,
    `MapHawkActiveSourceAdapter`, and their DI binding
  - HAWK engine/repository tests moved to `feature:variometer`
  - the Parent Phase 2B flight-runtime move no longer requires a HAWK
    dependency back-edge into `feature:map`

#### Phase 2B.1 - Flight Runtime Foundations

- Goal:
  - create `feature:flight-runtime` with only the clean runtime SSOT owners and
    contracts that do not require direct dependencies on `feature:profile` or
    `feature:variometer`
- Required seam lock before editing:
  - exact contract for raw sensor inputs, replay sensor inputs, airspeed
    inputs, flight-data SSOT, flying-state SSOT, and the wind-runtime owner set
  - exact ownership for `WindSelectionUseCase` and DI-backed
    `WindOverrideSource`: either move with the wind-runtime owner set or stay
    behind narrow read-side ports before the module cut
  - replay/time-base proof for moved runtime-only owners
  - DI plan that keeps replay shell and map shell out of the new module
- Files to change:
  - `feature/map/src/main/java/com/example/xcpro/sensors/SensorData.kt`
  - `feature/map/src/main/java/com/example/xcpro/sensors/SensorDataSource.kt`
  - `feature/map/src/main/java/com/example/xcpro/sensors/FlightStateSource.kt`
  - `feature/map/src/main/java/com/example/xcpro/sensors/FlightStateRepository.kt`
  - `feature/map/src/main/java/com/example/xcpro/sensors/CirclingDetector.kt`
  - `feature/map/src/main/java/com/example/xcpro/sensors/domain/FlyingState.kt`
  - `feature/map/src/main/java/com/example/xcpro/sensors/domain/FlyingStateDetector.kt`
  - `feature/map/src/main/java/com/example/xcpro/sensors/domain/FlightMetricsModels.kt`
  - `feature/map/src/main/java/com/example/xcpro/weather/wind/data/AirspeedDataSource.kt`
  - `feature/map/src/main/java/com/example/xcpro/weather/wind/data/ExternalAirspeedRepository.kt`
  - `feature/map/src/main/java/com/example/xcpro/weather/wind/data/WindSensorInputs.kt`
  - `feature/map/src/main/java/com/example/xcpro/weather/wind/data/WindSensorInputAdapter.kt`
  - `feature/map/src/main/java/com/example/xcpro/weather/wind/data/WindSensorFusionRepository.kt`
  - `feature/map/src/main/java/com/example/xcpro/weather/wind/data/ReplayAirspeedRepository.kt`
  - `feature/map/src/main/java/com/example/xcpro/weather/wind/domain/CirclingWind.kt`
  - `feature/map/src/main/java/com/example/xcpro/weather/wind/domain/WindMeasurementList.kt`
  - `feature/map/src/main/java/com/example/xcpro/weather/wind/domain/WindStore.kt`
  - `feature/map/src/main/java/com/example/xcpro/weather/wind/domain/WindSelectionUseCase.kt`
  - `feature/map/src/main/java/com/example/xcpro/weather/wind/model/WindInputs.kt`
  - `feature/map/src/main/java/com/example/xcpro/weather/wind/model/WindState.kt`
  - `feature/map/src/main/java/com/example/xcpro/flightdata/FlightDataRepository.kt`
  - `feature/map/src/main/java/com/example/xcpro/flightdata/FlightDisplayMapper.kt`
  - `feature/map/src/main/java/com/example/xcpro/replay/ReplaySensorSource.kt`
  - `feature/profile/src/main/java/com/example/xcpro/weather/wind/data/WindOverrideSource.kt`
  - `feature/profile/src/main/java/com/example/xcpro/weather/wind/model/WindOverride.kt`
  - `feature/profile/src/main/java/com/example/xcpro/weather/wind/model/WindSource.kt`
  - `feature/profile/src/main/java/com/example/xcpro/weather/wind/model/WindVector.kt`
  - target new `feature:flight-runtime`
- Ownership/file split changes in this phase:
  - `feature:flight-runtime` owns the raw-sensor contracts, sensor/flight-data
    runtime models, replay sensor and replay airspeed inputs, flight-data
    SSOT/runtime mapping, flying-state SSOT, and the wind-runtime
    input/fusion owner set plus its pure helper/domain contracts
  - `feature:map` keeps replay shell orchestration, waypoint/settings files,
    and temporary adapters into the moved runtime owners
  - `feature:profile` keeps wind override persistence only and reads/writes the
    moved wind override contracts via a dependency on `feature:flight-runtime`
- Explicit exclusions:
  - `FlightDataCalculator`, `FlightDataCalculatorEngine`,
    `SensorFusionRepositoryFactory`
  - `ReplayPipeline`, `ReplayPipelineFactory`,
    `IgcReplayControllerRuntime`, `IgcReplayControllerRuntimeLoadAndConfig`,
    `IgcReplayControllerRuntimePlayback`, `IgcReplayController`
  - `WaypointsViewModel`, `WaypointFilesUseCase`, `WaypointFilesRepository`
  - `FlightMgmtPreferencesUseCase`, `FlightMgmtPreferencesViewModel`
- Tests to add/update:
  - moved wind/flying-state/runtime repository tests
  - replay sensor/airspeed support tests
  - runtime contract boundary tests
- Exit criteria:
  - `feature:flight-runtime` exists and owns the clean runtime foundations
  - `feature:map` no longer owns the moved SSOT/contracts listed above
- Landed result:
  - `feature:flight-runtime` owns the moved runtime foundations and their
    owner tests
  - `feature:profile` depends on `feature:flight-runtime` for shared wind
    contracts/models instead of owning those contracts directly
  - `feature:map` depends on `feature:flight-runtime` for the moved SSOT and
    runtime foundations while keeping replay shell and live sensor owners

#### Phase 2B.2A - Shared Runtime Port And Model Extraction

- Goal:
  - extract the shared glider/audio/HAWK runtime contracts out of owner
    feature modules so the later fusion-engine move does not create
    `feature:flight-runtime -> feature:profile` or
    `feature:flight-runtime -> feature:variometer` dependencies
- Required seam lock before editing:
  - confirm the sink/glider contract is pure and can move without dragging the
    profile glider implementation with it
  - confirm `VarioAudioSettings` is a shared runtime model rather than a
    variometer-only implementation detail
  - define a narrow HAWK runtime read port for audio vario samples instead of
    depending on the full repository/UI state
- Files to change:
  - `feature/flight-runtime/src/main/java/com/example/xcpro/glider/**`
  - `feature/flight-runtime/src/main/java/com/example/xcpro/audio/**`
  - `feature/flight-runtime/src/main/java/com/example/xcpro/hawk/**`
  - `feature/profile/src/main/java/com/example/xcpro/glider/**`
  - `feature/variometer/src/main/java/com/example/xcpro/audio/**`
  - `feature/variometer/src/main/java/com/example/xcpro/hawk/**`
  - `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculator.kt`
  - `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngine.kt`
  - `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngineLoops.kt`
  - `feature/map/src/main/java/com/example/xcpro/sensors/SensorFusionRepositoryFactory.kt`
  - `feature/map/src/main/java/com/example/xcpro/sensors/SensorFusionRepository.kt`
- Ownership/file split changes in this phase:
  - `feature:flight-runtime` owns the shared runtime contracts/models only:
    `StillAirSinkProvider`, `SpeedBoundsMs`, `VarioAudioSettings`, the
    audio-controller port/factory, and the narrow HAWK audio-read port
  - `feature:profile` keeps the concrete still-air sink implementation
  - `feature:variometer` keeps the concrete audio/HAWK runtime implementations
    and binds them to the shared ports
  - `feature:map` rewires the remaining fusion owners to consume the new shared
    ports, but the fusion engine itself stays map-owned until `2B.2B`
- Tests to add/update:
  - shared-port boundary tests
  - compile/runtime contract tests for the rewired fusion entrypoints
- Exit criteria:
  - `feature:flight-runtime` exposes the shared glider/audio/HAWK runtime
    contracts without direct dependencies on `feature:profile` or
    `feature:variometer`
  - the remaining map-owned fusion engine no longer depends directly on
    profile/variometer concrete owners
- Landed result:
  - `feature:flight-runtime` owns the shared glider/audio/HAWK runtime
    contracts and models
  - `feature:profile` keeps `PolarStillAirSinkProvider` as the concrete sink
    implementation behind the shared `StillAirSinkProvider` port
  - `feature:variometer` binds the concrete audio controller and HAWK
    repository to `VarioAudioControllerFactory` and `HawkAudioVarioReadPort`
  - `feature:map` still owns the fusion engine, but it now depends only on the
    shared runtime ports instead of concrete profile/variometer owners

#### Phase 2B.2B - Fusion Engine Move

- Goal:
  - move the remaining sensor-fusion engine owners after `2B.2A` lands and the
    runtime ports are already in place
- Required seam lock before editing:
  - replay/time-base proof for moved fusion owners
  - dependency review confirming the moved engine consumes only
    `feature:flight-runtime` contracts plus Android/runtime dependencies
  - confirm the public `SensorFusionRepository` contract moves with
    `VarioDiagnosticsSample` and the remaining runtime-only helper/model set
    instead of leaving map-owned back-edges
- Files to change:
  - `feature/map/src/main/java/com/example/xcpro/sensors/SensorFusionRepository.kt`
  - `feature/map/src/main/java/com/example/xcpro/sensors/VarioDiagnosticsSample.kt`
  - `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculator.kt`
  - `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngine.kt`
  - `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngineLoops.kt`
  - `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataEmitter.kt`
  - `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataEmissionState` via
    `FlightDataEmitter.kt`
  - `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataConstants.kt`
  - `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataFilters.kt`
  - `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataModels.kt`
  - `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataReplayLogging.kt`
  - `feature/map/src/main/java/com/example/xcpro/sensors/FlightCalculationHelpers.kt`
  - `feature/map/src/main/java/com/example/xcpro/sensors/VarioSuite.kt`
  - `feature/map/src/main/java/com/example/xcpro/sensors/ThermalTracker.kt`
  - `feature/map/src/main/java/com/example/xcpro/sensors/TimedAverageWindow.kt`
  - `feature/map/src/main/java/com/example/xcpro/sensors/FixedSampleAverageWindow.kt`
  - `feature/map/src/main/java/com/example/xcpro/sensors/WindowFill.kt`
  - `feature/map/src/main/java/com/example/xcpro/sensors/DisplayVarioSmoother.kt`
  - `feature/map/src/main/java/com/example/xcpro/sensors/NeedleVarioDynamics.kt`
  - `feature/map/src/main/java/com/example/xcpro/sensors/PressureKalmanFilter.kt`
  - `feature/map/src/main/java/com/example/xcpro/vario/IVarioCalculator.kt`
  - `feature/map/src/main/java/com/example/xcpro/vario/OptimizedKalmanVario.kt`
  - `feature/map/src/main/java/com/example/xcpro/vario/LegacyKalmanVario.kt`
  - `feature/map/src/main/java/com/example/xcpro/vario/RawBaroVario.kt`
  - `feature/map/src/main/java/com/example/xcpro/vario/GPSVario.kt`
  - `feature/map/src/main/java/com/example/xcpro/vario/ComplementaryVario.kt`
  - `feature/map/src/main/java/com/example/xcpro/sensors/SensorFusionRepositoryFactory.kt`
  - `feature/map/src/main/java/com/example/xcpro/sensors/domain/**`
  - target `feature:flight-runtime`
- Ownership/file split changes in this phase:
  - `feature:flight-runtime` owns the fusion engine, metric calculators,
    terrain-aware helpers, and the remaining runtime-only sensor pipeline code
  - map shell/runtime read the moved fusion owners through the ports landed in
    `2B.2A`
  - explicit exclusions:
    - `WaypointsViewModel`, `WaypointFilesUseCase`, `WaypointFilesRepository`
    - `FlightMgmtPreferencesUseCase`, `FlightMgmtPreferencesViewModel`
    - live sensor/device owners:
      `UnifiedSensorManager`, `SensorRegistry`, `SensorStatus`, `GpsStatus*`,
      `OrientationProcessor`
    - replay session/UI shell controllers
    - `ReplayPipeline`, `ReplayPipelineFactory`,
      `IgcReplayControllerRuntime`, `IgcReplayController`
- Tests to add/update:
  - moved sensor/fusion tests
  - runtime port boundary tests
  - runtime contract boundary tests
- Exit criteria:
  - `feature:map` no longer owns the core flight/sensor fusion logic moved by
    this subphase
  - ADR exists for `feature:flight-runtime`
- Landed result:
  - `feature:flight-runtime` owns the fusion engine, runtime-only sensor
    helpers, `sensors/domain/**` policies, and the pure `vario/**`
    calculators together with their owner tests
  - `feature:map` keeps live sensor/device owners, replay shell/controllers,
    DI composition, and shell-facing bridge tests such as
    `LevoVarioPipelineTest`
  - module verification passed for:
    - `:feature:flight-runtime:testDebugUnitTest`
    - `:feature:map:testDebugUnitTest`

#### Phase 2C - Orientation Input Extraction

- Goal:
  - move reusable sensor-derived orientation input logic out of `feature:map`
    without moving the map-specific orientation controller
- Required seam lock before editing:
  - preserve `MapOrientationManager` as the map-owned control/state surface
  - keep sensor/time-base behavior unchanged

#### Phase 2C.1 - Pure Orientation Support Extraction

- Goal:
  - move pure orientation support owners that have no direct dependency on live
    sensor owners, map-runtime flags, or profile settings
- Files to change:
  - pure reusable owners under
    `feature/map/src/main/java/com/example/xcpro/orientation/**` that are safe
    to expose cross-module:
    `HeadingResolver.kt`, `OrientationClock.kt`, and orientation math helpers
  - the Hilt binding module for the moved orientation clock
  - target `feature:flight-runtime`
- Ownership/file split changes in this subphase:
  - `feature:flight-runtime` owns heading resolution, clock, and pure
    orientation math helpers
  - `feature:map` keeps `MapOrientationManager`, `OrientationEngine`,
    `HeadingJitterLogger`, and the live orientation data-source adapter path
- Tests to add/update:
  - moved heading-resolver and orientation-math tests
  - compile proof for map consumers of the moved support owners
- Exit criteria:
  - `feature:map` no longer owns the pure orientation support set
  - no new `feature:flight-runtime -> feature:profile` or
    `feature:flight-runtime -> feature:map-runtime` dependency is introduced
- Landed result:
  - `feature:flight-runtime` owns `HeadingResolver`, `OrientationClock`,
    `OrientationMath`, and the Hilt clock binding module, together with the
    moved heading-resolver and orientation-math tests
  - `feature:map` compiles against those owners without regaining direct
    time-API usage or a second orientation support owner

#### Phase 2C.2 - Orientation Input Adapter Split

- Goal:
  - move the reusable orientation input assembly path only after introducing
    narrow runtime ports for live orientation sensor input and the stationary-
    heading override policy
- Required seam lock before editing:
  - define a raw-orientation sensor port so `OrientationDataSource` no longer
    depends directly on `UnifiedSensorManager`
  - replace `MapFeatureFlags.allowHeadingWhileStationary` with a narrow runtime
    policy contract owned outside `feature:map-runtime`
  - keep `MapOrientationManager` and map-specific lifecycle/UI control in
    `feature:map`
  - keep `OrientationEngine` out of scope until its settings dependency on
    `MapOrientationSettings` is decoupled from `feature:profile`
- Files to change:
  - `feature/map/src/main/java/com/example/xcpro/OrientationDataSource.kt`
  - `feature/map/src/main/java/com/example/xcpro/OrientationDataSourceFactory.kt`
  - `feature/map/src/main/java/com/example/xcpro/OrientationSensorSource.kt`
  - new raw-orientation sensor/runtime policy contracts in
    `feature:flight-runtime`
  - temporary map-owned adapters over `UnifiedSensorManager` and the
    stationary-heading policy
- Ownership/file split changes in this subphase:
  - `feature:flight-runtime` owns the reusable orientation input assembly
    contract and implementation
  - `feature:map` keeps only adapters over live sensor owners plus
    `MapOrientationManager`
- Tests to add/update:
  - moved orientation data-source tests
  - map controller boundary tests
- Exit criteria:
  - `feature:map` keeps only the map orientation controller and shell-facing
    runtime bindings
  - target after Phase 2: `<= 320` main files
- Landed result:
  - `feature:flight-runtime` owns `OrientationSensorSource`,
    `OrientationDataSource`, `OrientationDataSourceFactory`, and the narrow
    orientation sensor/policy contracts that feed the reusable orientation
    input assembly
  - `feature:map` owns only the thin `UnifiedSensorManager` and
    stationary-heading adapters plus the map-specific
    `MapOrientationManager`, `OrientationEngine`, and `HeadingJitterLogger`
  - map test runtime wiring composes the moved factory through those thin
    adapters without restoring a second orientation input owner

### Phase 3 - Finish Map Runtime Burn-Down

- Goal:
  - move remaining heavy non-UI map runtime owners from `feature:map` to
    `feature:map-runtime`
- Required seam lock before editing:
  - which `map/**` files are true shell owners and which are hidden runtime
    owners
  - do not start with `MapOverlayManager` wrapper cleanup if the runtime owner
    is already in `feature:map-runtime`
  - first seam targets must prefer real runtime owners still compiled in
    `feature:map`, starting with the visual/runtime primitive set
    (`BlueLocationOverlay`, `SailplaneIconBitmapFactory`,
    `MapScaleBarController`) before broader shell bridges
  - after the visual/runtime primitive slice lands, the next clean runtime
    owner set is the snail-trail cluster rather than `MapInitializer`
  - after the snail-trail slice lands, re-seam-lock `MapInitializer` bootstrap
    and remaining dead runtime residue instead of assuming they are clean moves
  - proof that runtime moves do not create `feature:map-runtime -> feature:map`
    back-edges
- Files to change:
  - residual runtime owners under `feature/map/src/main/java/com/example/xcpro/map/**`
  - `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManager.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt`
  - `feature:map-runtime`
- Ownership/file split changes in this phase:
  - `feature:map` becomes shell/composition/adapters only
  - first landed slice in this phase should move the visual/runtime primitive
    set to `feature:map-runtime` while leaving shell-owned composition and
    loader/use-case seams in `feature:map`
  - `MapScreenViewModel` stays shell-owned, but any residual runtime bridge or
    orchestration helpers must be extracted off the shell path instead of
    widening the ViewModel further
  - `feature:map-runtime` becomes the heavy runtime owner
- Tests to add/update:
  - runtime-owner tests moved with files
  - shell compile tests
  - targeted map interaction smoke
- Exit criteria:
  - `feature:map` no longer contains heavy runtime managers/delegates by
    default
  - target after Phase 3: `<= 260` main files
- Landed result so far:
  - `feature:map-runtime` owns `BlueLocationOverlay`,
    `SailplaneIconBitmapFactory`, `MapScaleBarController`, and the narrow
    `MapScaleBarRuntimeState` port
  - `feature:map-runtime` also owns the snail-trail runtime set plus the
    `SnailTrailRuntimeState` bridge and moved trail owner tests
  - `feature:map` keeps the shell/runtime-handle implementation in
    `MapScreenState` plus shell-owned composition and runtime wiring
  - Parent Phase 3 is closed as the live runtime burn-down lane after the
    follow-up seam pass confirmed no further clean runtime owner move remained
    without widening into shell/bootstrap churn

### Phase 4 - Shell Hardening, Drift Guards, And Closeout

- Goal:
  - prevent `feature:map` from regrowing into a catch-all and prove release
    readiness
- Files to change:
  - `scripts/ci/enforce_rules.ps1`
  - active architecture docs and ADRs if boundaries changed
  - remaining shell files only after a focused seam lock, starting with:
    - `feature/map/src/main/java/com/example/xcpro/map/MapInitializer.kt`
    - `feature/map/src/main/java/com/example/xcpro/map/MapGestureSetup.kt`
    - `feature/map/src/main/java/com/example/xcpro/map/ui/MapRuntimeController.kt`
    - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenSections.kt`
    - `feature/map/src/main/java/com/example/xcpro/map/ui/MapOverlayStack.kt`
    - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContentRuntime.kt`
- Ownership/file split changes in this phase:
  - add module drift guards for the extracted boundaries
  - finalize shell-only steady-state rules
  - do not force another runtime extraction when the remaining files are
    shell-owned; Phase 4 is hardening, deletion, and proof only
- Tests to add/update:
  - enforceRules coverage
  - any final boundary lock tests needed by moved seams
- Exit criteria:
  - `feature:map` steady-state is explicit and guarded
  - remaining shell files are explicitly classified as `shell`, `dead`, or
    follow-on work; do not leave them as ambiguous residue
  - target end-state:
    - `<= 260` main files
    - `<= 28k` main lines
  - evidence-based quality rescore is published per `docs/ARCHITECTURE/AGENT.md`
  - release-grade required checks pass
  - if shell-only ownership is achieved before the original numeric target,
    record that honestly in the plan instead of forcing churn to hit the number
- Landed result so far:
  - the dead `DistanceCirclesOverlay` map-layer file is removed
  - `MapScreenState`, `MapLifecycleSurfaceAdapter`, and
    `MapOverlayManagerRuntimeStatus` no longer carry dead overlay residue
  - `MapInitializer` no longer documents or references the deleted path
  - `MapInitializerDataLoader` uses `AppLogger` for failure logging
  - `MapGestureSetup`, `MapRuntimeController`, and `MapScreenSections` now use
    `AppLogger` instead of raw `Log.*`
  - the remaining shell files are explicitly classified:
    - `MapInitializer` = shell bootstrap/composition
    - `MapOverlayStack` = shell overlay composition
    - `MapScreenContentRuntime` = shell content composition
    - `MapGestureSetup` / `MapRuntimeController` / `MapScreenSections` =
      hardened shell adapters
  - `enforceRules` fails if the dead distance-circles overlay path is
    reintroduced
  - `enforceRules` fails if raw `Log.*` calls are reintroduced in the hardened
    shell files
  - Parent Phase 4 is considered architecturally complete at the shell
    boundary; the numeric `<= 260` / `<= 28k` target is deferred to a future
    shell-ergonomics program rather than forced by wrong-owner moves

## 7) Test Plan

- Unit tests:
  - move tests with owners in Phases 1-3
- Replay/regression tests:
  - rerun affected replay/fusion tests in Phase 2
- UI/instrumentation tests:
  - route smoke for moved settings screens
  - targeted map interaction smoke when runtime owners move
- Degraded/failure-mode tests:
  - missing route or binding cases should fail build/test and block phase exit
- Boundary tests for removed bypasses:
  - map shell no longer imports owner-module internals directly
  - runtime modules do not back-edge into `feature:map`
- Change-type coverage matrix:

| Change Type | Required Proof | Planned Evidence |
|---|---|---|
| Ownership move / bypass removal / API boundary | Boundary lock tests | moved screen tests, runtime contract tests, compile proof |
| Time-base / replay / cadence | Fake clock or deterministic repeat-run regression | Phase 2 replay/fusion evidence |
| Persistence / settings / restore | Round-trip / route smoke / integration proof | moved screen route smoke and owner tests |
| UI interaction / lifecycle | UI or targeted instrumentation/manual smoke | impacted map interaction smoke in Phases 1-3 |
| Performance-sensitive path | SLO artifact or compile/runtime evidence | targeted map/replay/runtime evidence where touched |

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

When relevant:

```bash
./gradlew connectedDebugAndroidTest
```

## 8) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| local shell cleanup resumes instead of owner moves | High | Phase 0 stop rule and phased seam locks | Codex |
| `feature:flight-runtime` becomes another god-module | High | keep it limited to fusion/wind/runtime owners only; keep map orientation control, waypoint/settings files, and replay shell out of scope | Codex |
| a bulk Phase 2 move creates a `feature:flight-runtime -> feature:map` back-edge through HAWK | High | land Phase 2A first and do not start module creation until the HAWK runtime owner is out of `feature:map` | Codex |
| `feature:flight-runtime` gains direct dependencies on UI-heavy `feature:profile` or `feature:variometer` | High | extract lightweight runtime ports before moving `FlightDataCalculatorEngine` / `SensorFusionRepositoryFactory` | Codex |
| wind fusion is moved without its input/runtime owner set and creates hidden split authority | High | treat wind runtime as one Phase 2B.1 owner set and seam-lock `WindSelectionUseCase` / `WindOverrideSource` first | Codex |
| screen moves turn into route churn | Medium | keep `app` or shell route registration narrow and move only owner screens | Codex |
| dual-entry general settings drifts during Phase 1 | High | treat app routes and local sub-sheets as one contract and update both in one slice | Codex |
| non-settings screens get swept into the settings lane by path alone | High | explicit bucket classification before edits; exclude `FlightMgmt`, `TaskRouteScreen`, diagnostics, overlays | Codex |
| package drift under `com.example.ui1.screens` hides true ownership | Medium | use owner matrix and file-path review, not package name alone, when deciding moves | Codex |
| runtime moves create `feature:map-runtime -> feature:map` back-edges | High | explicit contract review before every Phase 3 move | Codex |
| file-count target chasing overrides architecture | High | accept only owner-removal wins; reject cosmetic splits | Codex |

## 8A) ADR / Durable Decision Record

- ADR required: Yes
- ADR file:
  - required when `feature:flight-runtime` is approved for implementation
- Decision summary:
  - the durable decision is that `feature:map` ends as a shell-only owner and
    sensor/orientation/fusion runtime does not remain map-owned
- Why this belongs in an ADR instead of plan notes:
  - a new runtime module and final shell boundary are long-lived architecture
    decisions, not temporary implementation notes

## 9) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` or `CODING_RULES.md`
- No duplicate SSOT ownership introduced
- Every phase removes a real owner boundary from `feature:map`
- Time-base handling stays explicit where runtime code moves
- Replay behavior remains deterministic
- New module/API boundaries are ADR-backed when required
- `feature:map` ends as a shell target, not a residual bucket
- Phase 1 preserves one explicit general-settings policy across both app routes
  and map-local modal entry
- Final quality rescore is evidence-based and target scores are:
  - Architecture cleanliness: `>= 4.6 / 5`
  - Maintainability / change safety: `>= 4.6 / 5`
  - Test confidence on risky paths: `>= 4.4 / 5`
  - Overall map slice quality: `>= 4.5 / 5`
  - Release readiness: `>= 4.5 / 5`

## 10) Rollback Plan

- What can be reverted independently:
  - each phase independently
- Recovery steps if regression is detected:
  - revert the current phase only
  - keep prior validated owner moves intact
  - rerun the required checks

## 11) Recommendation

The shortest professional path is:

1. Stop `feature:map` cleanup churn unless it removes a real owner boundary.
2. Sweep `screens/**` first because it is the largest low-risk non-map block.
3. Extract sensor/orientation/fusion next because that is the biggest remaining
   non-map runtime/business block.
4. Finish the `feature:map-runtime` burn-down only after the first two wins so
   `feature:map` ends as a shell instead of a residual bucket.

That is the release-grade implementation path to the "genius-grade" end state.
