# Feature:Map Right-Sizing Master Plan

Execution contract:

- `docs/refactor/Feature_Map_Right_Sizing_Release_Grade_Phased_IP_2026-03-15.md`
- `docs/refactor/Feature_Map_Settings_Lane_Release_Grade_Phased_IP_2026-03-15.md`
- `docs/refactor/Feature_Map_Autonomous_Agent_Execution_Contract_2026-03-15.md`
- `docs/refactor/Feature_Map_Shell_Ergonomics_Release_Grade_Phased_IP_2026-03-16.md`

Autonomous execution rule:

- after each landed phase, run a focused seam/code pass unless the active plan
  already proves the next phase boundary is unchanged
- if that seam/code pass finds an in-scope fix for the landed phase or the
  next phase, fix it and continue automatically rather than stopping
- in Phase 4, treat the remaining `feature:map` files as a shell-hardening
  lane: classify them honestly, harden or delete residue, and do not force
  another extraction if the files are already correct shell owners

## 0) Metadata

- Title: Reduce `feature:map` from a catch-all module to a map shell
- Owner: Codex
- Date: 2026-03-15
- Issue/PR: TBD
- Status: Draft
- Progress note:
  - 2026-03-15: Phase 1A landed. The app shell now owns the General Settings
    host and `feature:map` only emits open intents for that surface.
  - 2026-03-15: Phase 1B landed. `ForecastSettingsScreen`,
    `WeatherSettingsScreen`, `WeatherSettingsSubSheet`, `UnitsSettingsScreen`,
    and `UnitsSettingsViewModel` are no longer owned by `feature:map`; weather
    sheet behavior tests moved to `feature:weather`.
  - 2026-03-15: Settings-lane Phase 1 landed. Thermalling settings UI/tests
    moved to `feature:profile`, while `feature:map` kept only thermalling
    runtime automation.
  - 2026-03-15: Settings-lane Phase 2 landed. Polar/glider settings UI,
    settings-side glider policy, glider DI bindings, and glider/polar tests
    moved to `feature:profile`.
  - 2026-03-15: Settings-lane Phase 3A landed. Layout settings UI/use-case/
    ViewModel and layout owner tests moved to `feature:profile`.
  - 2026-03-15: Settings-lane Phase 3B landed. Theme authority is singular
    under `ThemePreferencesRepository` / `ThemeProfileSettingsContributor`,
    generic color-picker UI moved to `core:ui`, and the colors screen/use-case/
    ViewModel moved to `feature:profile` while `feature:map` kept only the
    theme runtime read path.
  - 2026-03-15: Settings-lane Phase 4 landed. HAWK settings UI moved to
    `feature:profile`, the HAWK preview contract moved to `feature:variometer`,
    and the parent plan could start runtime extraction cleanly.
  - 2026-03-15: Parent Phase 2A landed. `feature:variometer` now owns the live
    HAWK runtime while `feature:map` keeps only temporary sensor/source
    adapters until Parent Phase 2B lands.
  - 2026-03-15: Parent Phase 2B seam pass found the runtime move must be split
    again: clean SSOT/contracts can move first, but the fusion engine still
    depends on profile/variometer runtime collaborators and replay shell owners
    that should not become direct `feature:flight-runtime` dependencies.
  - 2026-03-16: Full-plan seam pass tightened the remaining runtime sequence:
    the first `feature:flight-runtime` cut must move the wind-runtime owner set
    together, replay shell files remain explicit exclusions, orientation input
    extraction must name the root orientation files, and Phase 3 must start
    with `MapOverlayManager` plus the residual bridge load around
    `MapScreenViewModel`.
  - 2026-03-16: Parent Phase 2B.1 landed. `feature:flight-runtime` now owns
    the runtime foundations and shared wind contracts/models; `feature:map`
    keeps live sensor owners, replay shell, and DI composition.
  - 2026-03-16: Current workspace footprint after Parent Phase 2B.1 is
    `381` main Kotlin files / `38,521` main lines in `feature:map`, with
    `feature:flight-runtime` now at `28` main Kotlin files / `2,218` lines.
  - 2026-03-16: Parent Phase 2B.2B landed. `feature:flight-runtime` now owns
    the remaining fusion engine/runtime-only sensor pipeline owners plus the
    pure `vario/**` calculator set; `feature:map` keeps live sensor/device
    owners, replay shell/controllers, DI composition, and shell-facing bridge
    tests.
  - 2026-03-16: Parent Phase 2C seam lock corrected the orientation lane:
    pure orientation support owners can move first, but the orientation input
    adapter path still depends on `UnifiedSensorManager`,
    `MapFeatureFlags.allowHeadingWhileStationary`, and
    `MapOrientationSettings`, so the remaining extraction must be staged rather
    than treated as one direct move.
  - 2026-03-16: Parent Phase 2C.1 landed. `feature:flight-runtime` now owns
    the pure orientation support set, and `feature:map` is down to `337` main
    Kotlin files / `34,357` main lines while `feature:flight-runtime` is
    `77` files / `6,450` lines.
  - 2026-03-16: Parent Phase 2C.2 landed. `feature:flight-runtime` now owns
    the reusable orientation input assembly and its narrow input/policy
    contracts, while `feature:map` keeps only thin live-sensor and
    stationary-heading adapters plus the map-specific controller path.
    `feature:map` is `337` main Kotlin files / `33,969` main lines and
    `feature:flight-runtime` is `81` files / `6,875` lines.
  - 2026-03-16: Parent Phase 3 seam lock corrected the next runtime slice:
    `MapOverlayManager` is already a thin shell wrapper, `MapScreenViewModel`
    is mostly shell orchestration, and the real first runtime move is the
    visual/runtime primitive set (`BlueLocationOverlay`,
    `SailplaneIconBitmapFactory`, `MapScaleBarController`). `MapInitializer`
    and `SnailTrailManager` stay later because they still mix shell/use-case
    dependencies with runtime work.
  - 2026-03-16: Parent Phase 3 first runtime-owner slice landed.
    `feature:map-runtime` now owns `BlueLocationOverlay`,
    `SailplaneIconBitmapFactory`, `MapScaleBarController`, and the narrow
    `MapScaleBarRuntimeState` bridge; `feature:map` keeps only shell wiring and
    the `MapScreenState` implementation of that port.
  - 2026-03-16: Current workspace footprint after the first Parent Phase 3
    slice is `334` main Kotlin files / `33,364` main lines in `feature:map`,
    with `feature:map-runtime` now at `78` main Kotlin files / `7,055` lines.
  - 2026-03-16: Follow-up Parent Phase 3 seam lock found `MapInitializer`
    stays shell-owned for now because it still composes data loading,
    task-render sync, and bootstrap sequencing; the next clean move is the
    snail-trail runtime owner set behind a narrow shell-held runtime state
    port, while `DistanceCirclesOverlay` is delete/hardening residue.
  - 2026-03-16: Parent Phase 3 trail runtime slice landed. `feature:map-runtime`
    now owns `SnailTrailManager`, `SnailTrailOverlay`, the trail-domain runtime
    path, and the `SnailTrailRuntimeState` bridge; `feature:map` keeps only
    shell-held trail handles plus shell effect wiring.
  - 2026-03-16: Current workspace footprint after the Parent Phase 3 trail
    slice is `307` main Kotlin files / `31,250` main lines in `feature:map`,
    with `feature:map-runtime` now at `106` main Kotlin files / `9,180` lines.
  - 2026-03-16: Follow-up seam pass closed Parent Phase 3 as the live runtime
    burn-down lane. `MapInitializer` remains shell-owned because it still
    mixes bootstrap/data-loader responsibilities, while the dead
    `DistanceCirclesOverlay` path belonged in Parent Phase 4 hardening rather
    than another forced owner move.
  - 2026-03-16: Parent Phase 4 first hardening slice landed. The dead
    `DistanceCirclesOverlay` file, shell state/status residue, and stale
    bootstrap references were deleted; `MapInitializerDataLoader` now uses
    `AppLogger`; and `enforceRules` now guards against reintroducing the dead
    overlay path.
  - 2026-03-16: Parent Phase 4 second hardening slice landed. The touched
    shell files `MapGestureSetup`, `MapRuntimeController`, and
    `MapScreenSections` now use `AppLogger`, and `enforceRules` guards against
    raw `Log.*` drift in those files.
  - 2026-03-16: Follow-up seam pass confirmed `MapInitializer`,
    `MapOverlayStack`, and `MapScreenContentRuntime` are shell-owned steady
    state files, so the remaining work is closeout/proof rather than another
    owner extraction.
  - 2026-03-16: Parent Phase 4 closeout confirmed the shell-only target is
    reached for this program. `MapInitializer`, `MapOverlayStack`, and
    `MapScreenContentRuntime` remain shell/composition owners, while
    `MapGestureSetup`, `MapRuntimeController`, and `MapScreenSections` are the
    hardened shell adapter set. The older numeric target is now explicitly
    deferred to a future shell-ergonomics pass instead of being forced here.
  - 2026-03-16: Follow-on plan created:
    `Feature_Map_Shell_Ergonomics_Release_Grade_Phased_IP_2026-03-16.md`.
    That new program covers shell ergonomics in `map/ui`, shell API trimming,
    and seam-gated residual lanes for replay, flightdata, airspace, sensor,
    and task-rendering hotspots.
  - 2026-03-16: Current workspace footprint after the second Parent Phase 4
    slice is `306` main Kotlin files / `30,900` main lines in `feature:map`,
    with `feature:map-runtime` unchanged at `106` main Kotlin files / `9,180`
    lines.

## 1) Scope

- Problem statement:
  - `feature:map` is still too broad to be a stable long-term owner.
  - On 2026-03-15 the current production footprint is:
    - `459` main Kotlin files
    - `47,299` main Kotlin lines
    - `194` files / `18,171` lines under `map`
    - `77` files / `9,370` lines under `screens`
    - `48` files / `4,972` lines under `sensors`
    - `19` files / `2,757` lines under residual `tasks`
  - The biggest remaining files confirm the problem is mixed ownership, not only line count:
    - `screens/navdrawer/ThermallingSettingsScreen.kt` (`427`)
    - `screens/navdrawer/HawkVarioSettingsScreenRuntime.kt` (`412`)
    - `sensors/FlightCalculationHelpers.kt` (`409`)
    - `sensors/domain/CalculateFlightMetricsRuntime.kt` (`392`)
    - `weather/wind/data/WindSensorFusionRepository.kt` (`384`)
    - `OrientationDataSource.kt` (`382`)
    - `map/ui/MapScreenContentRuntime.kt` (`382`)
    - `map/ui/MapBottomSheetTabs.kt` (`373`)
- Why now:
  - The earlier split work was correct, but it left `feature:map` as the residual owner for too many unrelated concerns.
  - Continuing with local helper extractions inside `feature:map` will now yield churn faster than value.
- In scope:
  - Consolidate the active plan for shrinking `feature:map`.
  - Define the target steady-state for `feature:map`.
  - Sequence the next owner moves by ROI and architectural correctness.
- Out of scope:
  - Product redesign.
  - Big-bang rewrites.
  - Splitting code purely to reduce file count without improving ownership.
- User-visible impact:
  - No intended behavior change.
  - Main goals are change safety, compile isolation, and long-term maintainability.
- Rule class touched: Default

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Concern | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Map shell composition, route entrypoints, map-specific UI wiring | `feature:map` | screen/viewmodel/ui shell APIs | feature-specific settings/runtime code staying in `feature:map` after owner extraction |
| MapLibre runtime, overlays, render sync, gesture/runtime helpers | `feature:map-runtime` | runtime ports and runtime contracts | duplicate runtime implementations in `feature:map` |
| Task domain and task UI | `feature:tasks` | task use-cases, task UI, task runtime contracts | residual task authority or editor logic in `feature:map` |
| Traffic domain and traffic settings UI | `feature:traffic` | traffic facades and entry surfaces | ADS-B/OGN state or settings ownership in `feature:map` |
| Weather/forecast domain and weather settings UI | `feature:weather` / `feature:forecast` | weather/forecast facades and entry surfaces | weather settings and domain ownership in `feature:map` |
| Flight/sensor/orientation fusion and wind-fusion runtime | new `feature:flight-runtime` target module | runtime-facing use-cases/repositories/ports | non-map business/runtime fusion code staying map-owned |

### 2.1A State Contract

| State Item | Authoritative Owner | Allowed Mutators | Read Path | Derived From | Persistence Owner | Reset/Clear Conditions | Time Base | Required Tests |
|---|---|---|---|---|---|---|---|---|
| Map screen shell UI state | `feature:map` ViewModels and shell UI hosts | map-shell intents only | Compose/UI shell | existing feature facades and shell-local UI state | existing owners only | existing screen lifecycle rules | unchanged | existing map screen tests |
| Map runtime/render state | `feature:map-runtime` owners | runtime controllers/managers only | shell ports from `feature:map` | existing runtime inputs | existing owners only | map/style/lifecycle rules | unchanged | existing map-runtime tests |
| Sensor/orientation/fusion runtime state | target `feature:flight-runtime` owners | sensor/fusion use-cases and repositories only | consumed by map shell/runtime through narrow ports | sensor/device inputs and existing fusion logic | existing owners only | existing sensor/runtime lifecycle rules | unchanged monotonic/replay/wall contracts | existing fusion/runtime tests moved with owners |
| Feature-specific settings screen state | owner feature module or app shell | owner screen/viewmodel intents | owner feature UI entrypoints | owner settings repositories/use-cases | existing owners only | existing lifecycle rules | unchanged | owner feature tests |

### 2.2 Dependency Direction

Target dependency flow:

`app -> feature:map -> feature:map-runtime + owner feature modules`

`feature:map` must trend toward shell-only ownership.

- Modules/files touched by this plan:
  - `feature:map`
  - `feature:map-runtime`
  - `feature:tasks`
  - `feature:traffic`
  - `feature:weather`
  - `feature:forecast`
  - new `feature:flight-runtime` target module
- Boundary risks:
  - adding more one-off helpers in `feature:map` instead of moving the owner
  - creating new cross-feature back-edges while extracting settings/runtime code
  - moving Android-heavy runtime code into `core:*` instead of a feature/runtime module

### 2.2A Reference Pattern Check

| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| `docs/refactor/Feature_Map_Compile_Speed_Release_Grade_Phased_Plan_2026-03-12.md` | current detailed execution history for `feature:map` reduction | keep phased owner moves and stop rules | this master plan is smaller and resets stale counts |
| `docs/refactor/Feature_Map_Module_Split_Plan_2026-03-09.md` | earlier target graph and file-count targets | keep the “map becomes shell” end-state | update targets to the current post-split footprint |

### 2.2B Boundary Moves

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Feature-specific settings and navdrawer screens still living in `feature:map` | `feature:map/screens` | owner modules or `app` shell entry registration | settings screens are not map-shell ownership | compile + owner-module tests |
| Sensor/orientation/wind-fusion runtime and flight-metric calculations | `feature:map/sensors`, `feature:map/orientation`, `feature:map/weather/wind/data` | new `feature:flight-runtime` | this is business/runtime code, not map-shell code | compile + moved tests + replay/timebase checks |
| Remaining heavy non-UI map runtime owners | `feature:map` | `feature:map-runtime` | keeps `feature:map` as shell/composition only | compile + runtime tests |
| Residual task shells/helpers that are still pure task ownership | `feature:map/tasks` and wrappers | `feature:tasks` where possible | finish the owner move and reduce shell drag | task + map compile/tests |

### 2.2C Bypass Removal Plan

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `feature/map/src/main/java/com/trust3/xcpro/screens/**` | owner feature screens are still hosted in `feature:map` | move screens to owner modules; keep only app/map route registration seams | Phase 1 |
| `feature/map/src/main/java/com/trust3/xcpro/sensors/**` and `weather/wind/data/**` | non-map runtime/fusion code is map-owned | move behind `feature:flight-runtime` ports | Phase 2 |
| residual runtime owners inside `feature/map/src/main/java/com/trust3/xcpro/map/**` | runtime code still compiled with the shell | move remaining runtime owners to `feature:map-runtime` | Phase 3 |

### 2.2D File Ownership Plan

| File | New / Existing | Owner / Responsibility | Why Here | Why Not Another Layer/File | Split Needed? |
|---|---|---|---|---|---|
| `docs/refactor/Feature_Map_Right_Sizing_Master_Plan_2026-03-15.md` | New | active right-sizing contract for `feature:map` | consolidate the fragmented map-size plans | planning belongs in docs, not code comments | No |
| `feature/map/src/main/java/com/trust3/xcpro/screens/**` | Existing | owner-screen inventory to reduce in Phase 1 | this is the largest non-runtime non-shell block left in `feature:map` | not a map runtime concern | Yes |
| `feature/map/src/main/java/com/trust3/xcpro/sensors/**` | Existing | target Phase 2 extraction set | biggest remaining non-map business/runtime logic block | not shell UI and not MapLibre runtime | Yes |
| `feature/map/src/main/java/com/trust3/xcpro/orientation/**` and `weather/wind/data/**` | Existing | target Phase 2 extraction set | tightly coupled to flight/sensor fusion | should move with the fusion owner, not independently | Yes |
| `feature/map/src/main/java/com/trust3/xcpro/map/**` | Existing | must end as shell-only plus map-specific adapters | core map package is still too mixed | not all of it belongs in `feature:map-runtime`; shell remains here | Yes |

### 2.2E Module and API Surface

| Contract / API | Owner | Consumers | Visibility | Why Needed | Compatibility / Removal Plan |
|---|---|---|---|---|---|
| `feature:flight-runtime` read/write ports for sensor/orientation/fusion runtime | target new module | `feature:map`, possibly other runtime consumers | public cross-module | move non-map runtime logic out of `feature:map` without leaking internals | define in Phase 2 seam pass; avoid premature public surface |
| owner-module settings entry surfaces | owner feature module | `app` and `feature:map` shell | public cross-module | removes `feature:map` ownership of unrelated settings screens | keep app as final route registrar |

### 2.2F Scope Ownership and Lifetime

| Scope / Owner | Why It Exists | Dispatcher | Cancellation Trigger | Why Not Caller-Owned / Existing Scope |
|---|---|---|---|---|
| existing feature/runtime scopes only | this plan should not add new long-lived scopes casually | unchanged | unchanged | module right-sizing should move owners, not invent new lifetime models |

### 2.2G Compatibility Shim Inventory

| Shim / Bridge | Owner | Reason | Target Replacement | Removal Trigger | Test Coverage |
|---|---|---|---|---|---|
| owner-module route wrappers left in `feature:map` | `feature:map` temporarily | keep app and drawer wiring stable while ownership moves | owner-module entry surfaces | remove once screens/routes are fully owner-owned | compile + route smoke |
| shell adapters over runtime owners | `feature:map` | keep `feature:map` public shell stable | direct shell-to-runtime contract once stable | remove only if they stop adding value | existing compile + runtime tests |

### 2.2H Canonical Formula / Policy Owner

| Formula / Constant / Policy | Canonical Owner File | Reused By | Why This Owner Is Canonical | Temporary Duplicates Allowed? |
|---|---|---|---|---|
| flight-metric and wind-fusion calculations | current `sensors/domain` and `weather/wind/data` owners, then `feature:flight-runtime` | map shell/runtime and any replay/fusion consumers | these are non-UI business/runtime policies | No |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| sensor/fusion cadence | Monotonic | runtime sensor processing stays unchanged |
| replay-derived fusion inputs | Replay | replay determinism must not drift during extraction |
| persisted settings timestamps if any | Wall | existing persistence semantics stay unchanged |

Explicitly forbidden comparisons remain:

- Monotonic vs wall
- Replay vs wall

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - unchanged in the plan; right-sizing must preserve current owners
- Primary cadence/gating sensor:
  - existing runtime cadence only
- Hot-path latency budget:
- Phase 2 and Phase 3 must not add shell indirection on hot runtime paths

### 2.5 Replay Determinism

- Deterministic for same input: Yes, must remain yes
- Randomness used: No new randomness allowed
- Replay/live divergence rules:
  - unchanged; module moves must preserve current replay contracts

### 2.5A Error and Degraded-State Contract

| Condition | Category | Owner | User-Visible Behavior | Retry / Fallback Policy | Test Coverage |
|---|---|---|---|---|---|
| missing bindings during module move | Terminal during build/test | build + DI wiring | build fails; do not ship | fix before phase exit | compile/test gates |
| settings route mismatch after move | User Action | owner module + app route wiring | screen unreachable or wrong surface | block phase exit | route smoke + compile |

### 2.6 Enforcement Coverage

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| `feature:map` continues to own unrelated settings screens | module boundary + ownership defaults | review + enforceRules follow-on guard | future Phase 1 guards |
| sensor/fusion business logic remains map-owned | business logic out of UI/shell + module boundaries | review + compile + unit tests | Phase 2 extraction tests |
| runtime back-edge from `feature:map-runtime` to `feature:map` | dependency direction | compile + review | Phase 3 runtime move gates |

### 2.7 Visual UX SLO Contract

| Outcome | SLO ID | Baseline | Target | Validation/Test | Phase Gate |
|---|---|---|---|---|---|
| map behavior remains unchanged while ownership moves | existing impacted `MS-UX-*` / `MS-ENG-*` only where runtime changes | current accepted behavior | no regression | existing map evidence + smoke where runtime moves occur | runtime-moving phases only |

## 3) Data Flow (Before -> After)

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

## 4) Implementation Phases

### Phase 0 - Freeze and Baseline

- Goal:
  - stop ad hoc `feature:map` churn and baseline the current size/owner map
- Files to change:
  - this plan only
- Ownership/file split changes in this phase:
  - none in production code
- Tests to add/update:
  - none
- Exit criteria:
  - current `feature:map` footprint is frozen as `459` files / `47,299` lines
  - next phases are ordered by ownership ROI, not just file count

### Phase 1 - Screen Ownership Sweep

- Goal:
  - extract the cross-feature general-settings host from `feature:map`
  - then remove remaining true feature-specific settings/navdrawer screens
- Current phase note:
  - host extraction is complete; remaining Phase 1 work is the follow-on screen
    ownership sweep outside `FlightMgmt`, task routes, and diagnostics
  - the clean owner-wrapper cut is complete for forecast, weather, units, and
    thermalling;
    remaining Phase 1 work is the mixed-owner map-local settings lane only
  - the active detailed contract for that remaining lane is
    `docs/refactor/Feature_Map_Settings_Lane_Release_Grade_Phased_IP_2026-03-15.md`
  - that settings lane is now complete; the next active slice is Phase 2
    flight-runtime extraction after an ADR-backed seam pass
- Files to change:
  - `app/src/main/java/com/trust3/xcpro/appshell/settings/**`
  - app/map route registration seams
  - owner modules for moved screens
- Ownership/file split changes in this phase:
  - `feature:map` stops owning the cross-feature general-settings registry
  - `feature:map` then stops owning non-map settings surfaces
  - owner modules own their own settings screens and screen-specific ViewModels
- Tests to add/update:
  - general-settings host policy and modal/route parity tests
  - route smoke
  - owner-module screen tests moved with screens
- Exit criteria:
  - the general-settings host is no longer map-owned
  - `screens` package is materially smaller
  - `feature:map` no longer owns clearly non-map settings surfaces
  - target after Phase 1: `<= 390` main files

### Phase 2 - Flight Runtime Extraction

- Goal:
  - move flight-runtime owners out of `feature:map` without creating a new
    back-edge or dragging mixed shell files into the module
- Phase ordering note:
  - Parent Phase 2 is explicitly split:
    1. HAWK runtime owner extraction
    2. `feature:flight-runtime` foundations move
    3. shared glider/audio/HAWK runtime-port extraction
    4. fusion engine move after those ports land
    5. orientation input extraction
- Files to change:
  - HAWK runtime owners under `feature/map/src/main/java/com/trust3/xcpro/hawk/**`
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/**`
  - runtime-only owners in `feature/map/src/main/java/com/trust3/xcpro/weather/wind/data/**`
  - runtime-only owners in `feature/map/src/main/java/com/trust3/xcpro/flightdata/**`
  - reusable orientation input owners in `feature/map/src/main/java/com/trust3/xcpro/orientation/**`
  - target new `feature:flight-runtime`
- Ownership/file split changes in this phase:
  - `feature:variometer` becomes the HAWK runtime owner
  - `feature:flight-runtime` first owns raw-sensor contracts, flight-data and
    flying-state SSOT/runtime mapping, replay sensor/airspeed support, and the
    wind-runtime owner set (`AirspeedDataSource`,
    `ExternalAirspeedRepository`, `ReplayAirspeedRepository`,
    `WindSensorInputs`, `WindSensorInputAdapter`,
    `WindSensorFusionRepository`) with an explicit seam for
    `WindSelectionUseCase` / `WindOverrideSource`
  - the first `2B.2` slice extracts the shared glider/audio/HAWK contracts and
    models (`StillAirSinkProvider`, `SpeedBoundsMs`, `VarioAudioSettings`, the
    audio-controller port/factory, and a narrow HAWK audio-read port)
  - the fusion engine only moves after those lightweight runtime ports exist,
    so the new module does not depend directly on the UI-heavy
    `feature:profile` / `feature:variometer` modules
  - the engine move also has to carry its remaining runtime-only helper set:
    `SensorFusionRepository`, `VarioDiagnosticsSample`, `FlightDataEmitter`,
    `FlightDataFilters`, `FlightDataModels`, `FlightDataReplayLogging`,
    `FlightCalculationHelpers`, `VarioSuite`, `ThermalTracker`,
    `TimedAverageWindow`, `FixedSampleAverageWindow`, `WindowFill`,
    `DisplayVarioSmoother`, `NeedleVarioDynamics`, `PressureKalmanFilter`,
    the pure `vario/**` calculator set (`IVarioCalculator`,
    `OptimizedKalmanVario`, `LegacyKalmanVario`, `RawBaroVario`, `GPSVario`,
    `ComplementaryVario`), and the `sensors/domain/**` runtime policies
  - explicit exclusions for the engine move remain:
    `UnifiedSensorManager`, `SensorRegistry`, `SensorStatus`, `GpsStatus*`,
    and `OrientationProcessor`
  - `feature:map` keeps map-specific orientation control, replay shell
    controllers (`ReplayPipeline`, `ReplayPipelineFactory`,
    `IgcReplayControllerRuntime*`, `IgcReplayController`), and remaining shell
    adapters
- Tests to add/update:
  - moved sensor/fusion/wind tests
  - HAWK runtime tests moved with the owner
  - replay/timebase regression tests where relevant
- Exit criteria:
  - `feature:map` no longer owns flight/sensor fusion logic
  - target after Phase 2: `<= 320` main files
- Landed so far:
  - Phase 2A complete: `feature:variometer` owns the live HAWK runtime and
    `feature:map` keeps only temporary source adapters
  - Phase 2B.1 complete: `feature:flight-runtime` owns the runtime foundation
    slice and the shared wind contracts/models; replay shell and live sensor
    owners remain in `feature:map`
  - Phase 2B.2A complete: `feature:flight-runtime` now owns the shared
    glider/audio/HAWK runtime contracts and models, while `feature:profile`
    and `feature:variometer` keep the concrete implementations behind those
    ports; the remaining fusion engine is still map-owned for Phase 2B.2B
  - Phase 2B.2B complete: `feature:flight-runtime` now owns the fusion engine,
    runtime-only sensor helpers, `sensors/domain/**` policies, and the pure
    `vario/**` calculator set; `feature:map` keeps only live sensor/device
    owners, replay shell/controllers, DI composition, and shell-facing bridge
    tests

### Phase 3 - Finish Map Runtime Burn-Down

- Goal:
  - move remaining heavy non-UI map runtime owners from `feature:map` to `feature:map-runtime`
- Files to change:
  - residual `feature/map/src/main/java/com/trust3/xcpro/map/**` runtime owners
  - start with real runtime owners still compiled in `feature:map`, which is
    why the first landed slice moved the visual/runtime primitive set
    (`BlueLocationOverlay`, `SailplaneIconBitmapFactory`,
    `MapScaleBarController`) before broader shell/runtime bridges
  - the next landed slice moved the snail-trail runtime owner set rather than
    `MapInitializer` bootstrap code; re-seam-lock the remaining bootstrap and
    dead-runtime residue after that
  - `feature:map-runtime`
- Ownership/file split changes in this phase:
  - `feature:map` becomes shell/composition/adapters only
  - `MapScreenViewModel` remains shell-owned, but residual runtime bridge
    helpers must be extracted out of the shell path instead of expanding the
    ViewModel further
  - `feature:map-runtime` becomes the only heavy runtime owner
- Tests to add/update:
  - runtime-owner tests moved with files
  - shell compile tests
- Exit criteria:
  - `feature:map` no longer contains heavy runtime managers/delegates by default
  - target after Phase 3: `<= 260` main files

### Phase 4 - Shell Hardening and Drift Guards

- Goal:
  - prevent `feature:map` from growing back into a catch-all
- Files to change:
  - `scripts/ci/enforce_rules.ps1`
  - active map shell docs if boundaries change
- Ownership/file split changes in this phase:
  - add module drift guards for the extracted ownership boundaries
- Tests to add/update:
  - enforceRules coverage only
- Exit criteria:
  - `feature:map` steady-state is explicit and guarded
  - target end-state:
    - `<= 260` main files
    - `<= 28k` main lines

## 5) Test Plan

- Unit tests:
  - move tests with owners in Phases 1-3
- Replay/regression tests:
  - rerun affected replay/fusion tests in Phase 2
- UI/instrumentation tests (if needed):
  - route smoke for moved settings screens
  - targeted map interaction smoke when runtime owners move
- Boundary tests for removed bypasses:
  - map shell no longer imports owner-module internals directly

Required checks for implementation phases:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| More local shell refactors happen instead of owner moves | High | Phase 0 stop rule: no more helper churn without boundary payoff | Codex |
| New `feature:flight-runtime` becomes another god-module | High | keep it limited to runtime-only fusion/wind/orientation-input owners; keep HAWK runtime in `feature:variometer` and keep map orientation control in `feature:map` | Codex |
| Bulk Phase 2 move creates a `feature:flight-runtime -> feature:map` back-edge through HAWK runtime | High | extract HAWK runtime owner first; do not start the module move until HAWK is out of `feature:map` | Codex |
| `feature:flight-runtime` gains direct dependencies on UI-heavy `feature:profile` or `feature:variometer` | High | require lightweight runtime ports before moving `FlightDataCalculatorEngine` / `SensorFusionRepositoryFactory` | Codex |
| Wind fusion moves without its input/runtime owner set and creates hidden split authority | High | treat wind runtime as one Phase 2 owner set and seam-lock `WindSelectionUseCase` / `WindOverrideSource` first | Codex |
| Screen moves drift into route churn | Medium | keep `app` as route registrar and move only owner screens | Codex |
| Runtime moves create `feature:map-runtime -> feature:map` back-edges | High | explicit contract review before each Phase 3 move | Codex |

## 6A) ADR / Durable Decision Record

- ADR required: Yes, before `feature:flight-runtime` is created
- ADR file:
  - `docs/ARCHITECTURE/ADR_FLIGHT_RUNTIME_BOUNDARY_2026-03-15.md`
- Decision summary:
  - the remaining runtime extraction is now explicitly split because HAWK,
    orientation control, and mixed `flightdata` / `replay` files do not belong
    in one bulk module move
- Why this belongs in an ADR instead of plan notes:
  - the new module boundary should be ADR-backed once implementation starts

## 7) Acceptance Gates

- `feature:map` is treated as a shell target, not a catch-all owner
- no phase is executed just to reduce file count
- every phase removes a real owner boundary from `feature:map`
- replay/timebase behavior remains unchanged where touched

## 8) Rollback Plan

- What can be reverted independently:
  - each phase independently
- Recovery steps if regression is detected:
  - revert the current phase only
  - keep previous owner moves intact
  - rerun the required checks

## 9) Recommendation

The professional move is:

1. Stop doing local cleanliness refactors inside `feature:map` unless they remove a real owner boundary.
2. Sweep `screens/**` first because it is a large, low-risk ownership win.
3. Extract sensor/orientation/fusion next because that is the biggest non-map business/runtime block left.
4. Finish the `feature:map-runtime` burn-down only after those two moves, so `feature:map` ends as a shell instead of a residual bucket.

This is the shortest path to a materially smaller and safer `feature:map`.
