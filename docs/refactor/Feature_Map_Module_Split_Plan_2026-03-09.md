# Feature Map Module Split Plan (2026-03-09)

## 0) Metadata

- Title: `feature/map` phased module split for local build-speed improvement
- Owner: XCPro Team
- Date: 2026-03-09
- Issue/PR: TBD
- Status: Execution draft
- Quality target: `>= 97/100`

## 1) Scope

- Problem statement:
  - `feature/map` currently carries the majority of the repo's Android build surface.
  - Current production package volume inside `feature/map/src/main/java/com/trust3/xcpro`:
    - `tasks`: `220` files / `30853` lines
    - `map`: `249` files / `29106` lines
    - `screens`: `116` files / `15869` lines
    - `adsb`: `68` files / `6663` lines
    - `sensors`: `47` files / `4887` lines
    - `ogn`: `34` files / `4231` lines
    - `weather`: `32` files / `3043` lines
    - `igc`: `26` files / `2823` lines
    - `replay`: `21` files / `2312` lines
    - `forecast`: `21` files / `2939` lines
  - Total current production Kotlin footprint under `feature/map/src/main/java/com/trust3/xcpro`: `949` files / `113148` lines.
  - Current direct cross-slice coupling inside `feature:map`:
    - `map` + `screens` importing `replay` / `igc`: `58` imports
    - `map` importing `adsb` / `ogn`: `207` imports
    - `adsb` + `ogn` importing `map`: `3` imports
    - `map` + `screens` importing `weather` / `forecast`: `164` imports
    - `weather` + `forecast` importing `map` / `screens`: `1` import
    - `map` + `screens` importing `tasks`: `56` imports
    - `tasks` importing `map`: `7` imports
    - `igc` + `replay` importing `tasks`: `1` import
    - `igc` + `replay` importing `weather.wind`: `5` imports
- Why now:
  - Build speed is now a workflow constraint for feature work.
  - `feature/map` already contains multiple semi-independent subdomains with one-way imports into the map host.
  - The repo already has the main local Gradle caches enabled, so the next meaningful gain is reducing invalidation scope.
- In scope:
  - Define extraction order, target modules, contract modules, and validation gates.
  - Preserve current runtime behavior and pipeline contracts.
  - Reduce per-change compilation invalidation for task, traffic, weather, and replay work.
  - Make the plan measurable enough to stop or re-order if the ROI is weak.
- Out of scope:
  - Behavior changes in flight, traffic, task, or replay logic.
  - Rewriting MVVM/UDF/SSOT architecture.
  - Replacing Hilt, KSP, or Gradle plugins.
  - Splitting `sensors` in this plan. `sensors` remains owned where it is unless a later plan is created.
- User-visible impact:
  - No intentional runtime UX change.
  - Faster local compile/test loops once phases land.

## 2) Baseline and Success Metrics

### 2.1 Measurement Environment

- Machine:
  - Ryzen 7 8840HS
  - `16 GB` RAM
  - NVMe SSD
  - Windows
- Measurement mode:
  - warm local builds
  - `--build-cache --configuration-cache`
  - no `clean`
  - same laptop for before/after comparisons
- Repro commands:

```bash
./gradlew :feature:map:compileDebugKotlin --build-cache --configuration-cache --console=plain
./gradlew :feature:map:assembleDebug --build-cache --configuration-cache --console=plain
./gradlew :app:assembleDebug --build-cache --configuration-cache --console=plain
./gradlew :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.replay.ReplaySampleEmitterTest" --build-cache --configuration-cache --console=plain
```

### 2.2 Current Baseline

Pre-split baseline captured before Phase 1 implementation:

| Metric | Current Baseline | Notes |
|---|---|---|
| `:feature:map:compileDebugKotlin` | `3.83s` | warm local timing |
| `:feature:map:assembleDebug` | `32.82s` | warm local timing |
| `:app:assembleDebug` | `32.91s` | warm local timing |
| `ReplaySampleEmitterTest` targeted loop | `9.66s` | `:feature:map:testDebugUnitTest --tests "com.trust3.xcpro.replay.ReplaySampleEmitterTest"` |
| `feature:map` production file count | `949` | `.kt` files under `feature/map/src/main/java/com/trust3/xcpro` |

### 2.3 Phase 1 Measured Checkpoint

Measured after the implemented `feature:igc` extraction on the same laptop with warm local caches.

Method:
- no-op compile: rerun the same task with no source change
- edit compile: append a temporary comment to one Kotlin source file, run compile, restore the original file, rerun
- commands:

```bash
./gradlew :feature:igc:compileDebugKotlin --console=plain
./gradlew :feature:map:compileDebugKotlin --console=plain
```

Files used for the temporary edit:
- `feature/igc/src/main/java/com/trust3/xcpro/replay/IgcReplayUseCase.kt`
- `feature/map/src/main/java/com/trust3/xcpro/replay/IgcReplayController.kt`

| Metric | Measured Result | Notes |
|---|---|---|
| `:feature:igc:compileDebugKotlin` no-op | `1.0s` | warm local timing |
| `:feature:map:compileDebugKotlin` no-op | `1.1s` | warm local timing |
| `feature:igc` single-file edit compile | `3.1s` | replay/IGC edit inside extracted module |
| `feature:map` single-file edit compile | `10.4s` | comparable replay-runtime edit still inside map |
| `feature:igc` restore compile | `1.4s` | after restoring original file |
| `feature:map` restore compile | `5.3s` | after restoring original file |
| Relative improvement | `~3.3x faster` | `10.4s / 3.1s` for the edit compile path |

Phase 1 conclusion:
- the extraction already reduced replay/IGC edit-loop compile cost materially on this machine
- the main remaining build-speed problem is still the size of `feature:map`
- subsequent phases should measure module-local edit compile times, not only aggregate `assembleDebug`

### 2.4 Phase Targets

These are pass/fail targets, not aspirational notes. If a phase misses its primary build target by more than `10%`, stop and re-evaluate before continuing to the next phase.

| Phase | Primary Objective | `:feature:map:assembleDebug` Target | `:app:assembleDebug` Target | Slice-Specific Target | `feature:map` File Count Target |
|---|---|---|---|---|---|
| Phase 0 | freeze baseline and graph | baseline only | baseline only | baseline only | `949` |
| Phase 1 | isolate replay/IGC UI, parser, files, and contracts | `<= 29s` | `<= 30s` | `feature:igc` replay/IGC edit compile `<= 4s` and at least `2.5x` faster than equivalent `feature:map` replay edit | contract-first extraction complete |
| Phase 2 | isolate traffic | `<= 25s` | `<= 27s` | `:feature:traffic:assembleDebug <= 18s`; `compileDebugKotlin` no-op `<= 1.5s`; single traffic-file edit compile `<= 5s`; representative traffic repo test loop `<= 14s` | `<= 790` |
| Phase 3 | isolate weather/forecast | `<= 22s` | `<= 24s` | `:feature:weather:assembleDebug <= 16s` | `<= 730` |
| Phase 4 | isolate tasks and remove map cycle | `<= 18s` | `<= 21s` | `:feature:tasks:testDebugUnitTest <= 16s` | `<= 510` |
| Phase 5 | leave map shell only | `<= 15s` | `<= 18s` | `:feature:map:assembleDebug <= 12s` after no-op route checks | `<= 425` |

### 2.5 Decision Gates

- Continue only if:
  - the current phase hits its coupling targets,
  - the current phase does not regress replay determinism,
  - the current phase keeps `app` as the composition root,
  - the current phase reduces `feature:map` surface materially.
- Stop and re-plan if:
  - a new feature-to-feature dependency would be required,
  - `feature:igc` would need a direct dependency on `feature:weather`,
  - `feature:tasks` still needs `com.trust3.xcpro.map.*` after contract extraction,
  - the phase misses its build target by `> 10%`,
  - runtime behavior changes are needed just to make the split compile.

## 3) Target Module Graph

### 3.1 Intended Graph

```
app
|- feature:map
|  |- feature:igc
|  |- feature:traffic
|  |- feature:weather
|  |- feature:tasks
|  \- core:task-contract
|- feature:profile
|- feature:variometer
\- existing core/shared modules
```

Optional only if Phase 1 cannot remove replay-to-weather coupling cleanly:

```
core:wind-contract
```

`app` remains the only composition root and the only place where feature modules are wired together.

### 3.2 Allowed Dependencies

| Module | Allowed Dependencies | Forbidden Dependencies | Owned Surface |
|---|---|---|---|
| `app` | all feature modules, existing core/shared modules | feature internals via package reach-around | Hilt aggregation, navigation graph, final wiring |
| `feature:map` | existing core/shared modules, `feature:igc`, `feature:traffic`, `feature:weather`, `feature:tasks`, `core:task-contract` | direct imports of extracted feature internals once moved | map host, MapLibre overlay runtime, selection/gesture runtime, composition shell |
| `feature:igc` | existing core/shared modules | `feature:map`, `feature:weather`, `feature:tasks` | `igc`, replay parser/models/UI, replay/file screens, IGC persistence/contracts |
| `feature:traffic` | existing core/shared modules | `feature:map`, `feature:weather`, `feature:tasks`, `feature:igc` | `adsb`, `ogn`, traffic settings/detail UI, traffic route entry points, traffic metadata sync |
| `feature:weather` | existing core/shared modules | `feature:map`, `feature:traffic`, `feature:tasks`, `feature:igc` | `forecast`, `weather`, weather settings/runtime UI |
| `feature:tasks` | existing core/shared modules, `core:task-contract` | `feature:map` internals, `feature:traffic`, `feature:weather`, `feature:igc` | task engines, coordinators, task UI |
| `core:task-contract` | existing core/shared modules only | all feature modules | task facade models and map-owned task ports |
| `core:wind-contract` if needed | existing core/shared modules only | all feature modules | replay wind/airspeed read interfaces only |

### 3.3 Hard Rules for the Graph

- No extracted feature module may depend on `feature:map`.
- `feature:map` may depend on extracted modules, but only through public APIs that are intentionally exported.
- `feature:igc` must not depend directly on `feature:weather`; use `core:wind-contract` or relocate the minimal replay wind contracts into a shared module first.
- `feature:tasks` must not depend directly on `MapTaskScreenManager`, `TaskRenderSnapshot`, or `TaskRenderSyncCoordinator`; those become ports/contracts.

## 4) Contract Inventory

These seams must be explicit before package moves. Existing contracts are reused when possible; new contracts are created only where the current code has illegal cross-slice knowledge.

| Contract | Current Concrete Types | Target Owner | Phase | Reason |
|---|---|---|---|---|
| `IgcReplayUseCase` | `IgcReplayUseCase` | `feature:igc` | Phase 1 | map shell should launch replay through the use-case seam without replay-controller knowledge |
| `IgcDownloadsRepository` | `MediaStoreIgcDownloadsRepository` | `feature:igc` | Phase 1 | keeps IGC file list ownership in the extracted module |
| `IgcTaskDeclarationSource` | `TaskRepositoryIgcTaskDeclarationSource` | interface stays with `feature:igc`; implementation moves to `feature:tasks` later | Phase 1 then Phase 4 | removes direct `TaskRepository` reach-across |
| `ReplayAirspeedPort` | currently `ReplayAirspeedRepository` | `core:wind-contract` if needed | Phase 1 | removes direct replay dependency on `weather.wind.data` if replay runtime moves later |
| `ReplayWindStatePort` | currently `WindSensorFusionRepository` read access | `core:wind-contract` if needed | Phase 1 | `ReplayPipeline` needs read-only wind state without feature-to-feature dependency if runtime moves later |
| `AdsbTrafficFacade` | current `AdsbTrafficUseCase`, `AdsbTrafficRepository`, `AdsbTrafficPreferencesRepository`, metadata sync scheduler/repository | `feature:traffic` | Phase 2 | gives `feature:map` ADS-B state + commands without repository/DI imports |
| `OgnTrafficFacade` | current `OgnTrafficUseCase`, `OgnTrafficRepository`, `OgnTrafficPreferencesRepository`, `OgnThermalRepository`, `OgnGliderTrailRepository`, trail selection prefs | `feature:traffic` | Phase 2 | gives `feature:map` OGN targets/hotspots/trails + commands without repository/DI imports |
| `TrafficMapApi` | `AdsbTrafficUiModel`, `AdsbTrafficSnapshot`, `AdsbSelectedTargetDetails`, `OgnTrafficTarget`, `OgnTrafficSnapshot`, `OgnThermalHotspot`, `OgnGliderTrailSegment`, `OgnDisplayUpdateMode` | `feature:traffic` public API package | Phase 2 | map runtime may import only this stable render/selection model surface |
| `TrafficSettingsEntry` | `AdsbSettingsScreen`, `OgnSettingsScreen`, `HotspotsSettingsScreen`, traffic drawer sub-sheet wrappers, traffic route constants | `feature:traffic` entry surface; `app` keeps route registration only | Phase 2 | full-screen and in-map drawer traffic settings must move together |
| `OpenSkyCredentialsPort` | current `OpenSkyCredentialsRepository` direct `BuildConfig` access | interface in `feature:traffic`, implementation/config in `app` | Phase 2 | removes `feature:traffic -> feature:map` `BuildConfig` reach-around |
| `WeatherOverlayFacade` | `ForecastOverlayViewModel`, `WeatherOverlayViewModel`, weather settings use-cases | `feature:weather` | Phase 3 | lets `feature:map` consume weather state without importing weather internals |
| `TaskUiState` contract | existing `TaskUiState` | `core:task-contract` | Phase 3 / 4 | map shell renders task summary without full task internals |
| `TaskRenderPort` | current `TaskRenderSnapshot`, `TaskRenderSyncCoordinator`, render routers | `core:task-contract` | Phase 4 | breaks `tasks -> map` rendering dependency |
| `TaskScreenHostPort` | current `MapTaskScreenManager` usage | `core:task-contract` | Phase 4 | breaks `TaskTopDropdownPanel -> map` dependency |

Notes:

- `IgcTaskDeclarationSource` already exists and should be the main seam for IGC-to-task coupling. Do not add a second task declaration path.
- `ReplayAirspeedPort` and `ReplayWindStatePort` are mandatory only if a later phase moves replay runtime out of `feature:map`.
- `TaskRenderPort` must be map-owned and task-consumed, not the other way around.

## 5) Package Move Matrix

| Current Package / File Group | Count | Destination | Phase | Notes |
|---|---|---|---|---|
| `com.trust3.xcpro.igc.**` | `26` files | `feature:igc` except app-owned metadata adapters and recording runtime bridge inputs | Phase 1 | moved domain, data, file UI/use-cases, and contracts; kept `IgcMetadataSources.kt` and `IgcRecordingUseCase.kt` in `feature:map` for now |
| replay parser / models / UI / contracts | subset of `com.trust3.xcpro.replay.**` | `feature:igc` | Phase 1 | moved `IgcParser`, replay models/state/use-case/VM, interpolator/math/noise helpers, and matching tests |
| replay runtime backend | remaining `com.trust3.xcpro.replay.**` | keep in `feature:map` in Phase 1 | Phase 1 | keep `IgcReplayController*`, `ReplayPipeline*`, `ReplaySensorSource`, `ReplaySampleEmitter`, `ReplaySessionPrep`, and runtime emission wiring map-owned |
| `com.trust3.xcpro.screens.replay.*` | `3` files | `feature:igc` | Phase 1 | includes `IgcFilesScreen.kt` and `IgcReplayScreen.kt` |
| `com.trust3.xcpro.adsb.**` | `68` files | `feature:traffic` | Phase 2 | includes metadata DB/sync, emergency-audio logic, and ADS-B icon assets |
| `com.trust3.xcpro.ogn.**` | `34` files | `feature:traffic` | Phase 2 | includes trails, thermals, preferences, and OGN icon assets |
| `screens/navdrawer/AdsbSettings*` | `4` files | `feature:traffic` | Phase 2 | traffic settings UI |
| `screens/navdrawer/OgnSettings*` | `4` files | `feature:traffic` | Phase 2 | traffic settings UI |
| `screens/navdrawer/HotspotsSettings*` | `4` files | `feature:traffic` | Phase 2 | OGN hotspot settings UI |
| traffic route/sub-sheet entry points (`SettingsRoutes`, `AppNavGraph`, `SettingsDfRuntime*` traffic wrappers) | `route constants + wrapper surfaces` | `feature:traffic` entry surface; `app` keeps route registration only | Phase 2 | full-screen routes and drawer sub-sheets must move together |
| traffic DI modules (`AdsbMetadataModule.kt`, `AdsbNetworkModule.kt`, `OgnThermalModule.kt`, traffic bindings from `MapBindingsModule.kt`) | `5` module units | `feature:traffic` | Phase 2 | `feature:map` keeps only facade injection and map runtime adapters |
| traffic map runtime adapters (`MapScreenTrafficCoordinator`, `MapOverlayManager`, `AdsbTrafficOverlay`, `OgnTrafficOverlay`, traffic selection/binding files`) | `map-owned runtime set` | keep in `feature:map` | Phase 2 | MapLibre rendering, tap routing, and map gesture ownership stay with the map shell |
| `com.trust3.xcpro.forecast.**` | `21` files | `feature:weather` | Phase 3 | forecast repositories and overlay logic |
| `com.trust3.xcpro.weather.**` | `32` files | `feature:weather` | Phase 3 | weather repositories, rain UI, wind-related UI; exclude any contract code moved to shared/core |
| `screens/navdrawer/ForecastSettings*` | `3` files | `feature:weather` | Phase 3 | forecast settings UI |
| `screens/navdrawer/WeatherSettings*` | `4` files | `feature:weather` | Phase 3 | weather settings UI |
| `com.trust3.xcpro.tasks.**` | `220` files | `feature:tasks` | Phase 4 | move only after map contracts exist |
| `screens/navdrawer/Task.kt` | `1` file | `feature:tasks` | Phase 4 | task screen shell |
| `screens/navdrawer/TaskScreenUseCasesViewModel.kt` | `1` file | `feature:tasks` | Phase 4 | task screen VM |
| `screens/navdrawer/tasks/*` | `6` files | `feature:tasks` | Phase 4 | task file bottom sheet UI |
| `deleted map replay helper package` | keep in `feature:map` initially | `feature:map` until Phase 4 / 5 | Phase 4 / 5 | `legacy map replay route helper` is map/task support, not core replay runtime |
| `com.trust3.xcpro.sensors.**` | keep in `feature:map` | no move in this plan | N/A | avoid expanding this refactor beyond build-speed target |

Hold-back files that must not be blindly moved with `tasks`:

- `tasks/TaskMapOverlay.kt`
- `tasks/TaskMapRenderRouter.kt`
- `tasks/TaskTopDropdownPanel.kt`
- `tasks/racing/RacingMapRenderer.kt`
- `tasks/aat/interaction/AATEditOverlayRenderer.kt`
- `tasks/aat/rendering/AATTaskRenderer.kt`
- `tasks/aat/rendering/AATMapRenderer.kt`

These files prove the current `tasks -> map` dependency. They either move after contracts exist or remain map-owned adapters.

## 6) Architecture Contract

### 6.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Live flight data | `FlightDataRepository` | `Flow` / `StateFlow` | UI-local mirrors in extracted feature modules |
| Task state | `TaskRepository` / `TaskManagerCoordinator` | `TaskUiState`, task snapshots | map-host copies of task authority |
| ADS-B traffic state | `AdsbTrafficRepository` | traffic snapshot/state flows | map-host-owned traffic mirrors |
| OGN traffic/hotspots/trails | `OgnTrafficRepository`, `OgnThermalRepository`, `OgnGliderTrailRepository` | repository flows | duplicate overlay-owned truth |
| Weather/forecast state | weather + forecast preference/repository owners | feature state/use-case flows | map-host-owned weather truth |
| IGC file index / recording state | `IgcDownloadsRepository`, `IgcRecordingUseCase`, replay owners | file list + recording/replay flows | app/map-level duplicate recording state |

### 6.2 Dependency Direction

Required dependency flow remains:

`UI -> domain -> data`

- Modules/files touched:
  - new Gradle modules under `feature/`
  - `core:task-contract`
  - optional `core:wind-contract`
  - existing `app`, `feature/map`, and extracted package roots
- Boundary risks already observed:
  - `map` and `tasks` currently have two-way knowledge
  - `igc` / `replay` currently know `weather.wind` and one task declaration path
  - `map` / `screens` directly import traffic, weather, replay, and task types

### 6.3 Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| IGC files + replay UI/parser/contracts | `feature:map` (`igc`, selected `replay`, `screens/replay`) | `feature:igc` | low reverse coupling; immediate compile isolation for replay/import work | module assemble + replay/unit tests |
| IGC recording runtime bridge + replay execution backend | `feature:map` | remains `feature:map` in Phase 1 | keeps sensor/flightdata/weather dependencies out of the first extraction and avoids a feature cycle | map compile + replay runtime tests |
| Traffic repositories/use-cases, metadata sync DB/network, settings screens, and traffic route entry surfaces | `feature:map` (`adsb`, `ogn`, related settings screens, traffic DI modules, traffic route wrappers`) | `feature:traffic` | large isolated subdomain with clear API seams and only three current reverse map imports | module assemble + compile/edit benchmark + traffic tests + route compile |
| Map traffic overlays, map selection runtime, and traffic coordinator glue | `feature:map` | remains `feature:map` in Phase 2 | MapLibre rendering, tap routing, and map-specific gesture/selection state must stay map-owned while consuming traffic APIs only | map compile + targeted overlay/selection tests |
| Forecast/weather repositories and settings/runtime UI | `feature:map` (`forecast`, `weather`, weather-related screens) | `feature:weather` | mostly one-way dependency into map host | module assemble + weather tests |
| Task engines/coordinator/UI | `feature:map` (`tasks`, task-related screens) | `feature:tasks` + `core:task-contract` | largest domain slice; highest compile win after cycle removal | module assemble + task tests |
| Map screen host/runtime shell | `feature:map` | remains `feature:map` | keeps map composition root stable while other features become dependencies | app assemble + map shell tests |

### 6.4 Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Count | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|---|
| `map` / `screens` importing `replay` / `igc` types | `58` | map host knows replay internals | `feature:igc` public APIs and screens only | Phase 1 |
| `igc` / `replay` importing `weather.wind.*` | `5` | replay knows weather implementation classes | `core:wind-contract` or relocated replay-wind contracts | Phase 1 |
| `igc` / `replay` importing task code | `1` | IGC declaration path knows task implementation | keep `IgcTaskDeclarationSource` as the only contract | Phase 1 / 4 |
| `map` importing traffic types directly | `207` | map shell couples to ADS-B/OGN internals | `AdsbTrafficFacade`, `OgnTrafficFacade`, and `TrafficMapApi` only | Phase 2 |
| `adsb` + `ogn` importing `map` | `3` | traffic code reaches back for resources/config (`map.R`, `map.BuildConfig`) | traffic-owned drawables/resources + `OpenSkyCredentialsPort` | Phase 2 |
| app/navdrawer traffic screen ownership | `full-screen + drawer entry surfaces` | `AppNavGraph` and `SettingsDfRuntime*` know traffic screen implementations directly | `TrafficSettingsEntry` composables and traffic-owned route constants; `app` keeps registration only | Phase 2 |
| `map` / `screens` importing weather/forecast types | `164` | map shell couples to weather internals | weather facade APIs and UI models | Phase 3 |
| `weather` + `forecast` importing `map` / `screens` | `1` | weather settings still anchored to navdrawer owner | move settings screen ownership | Phase 3 |
| `map` / `screens` importing task types directly | `56` | map host knows task internals | `core:task-contract` UI models + task facades | Phase 4 |
| `tasks` importing `map` | `7` | task layer knows map runtime classes | map-owned render/edit/screen ports | Phase 4 |

### 6.5 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| Live sensor fusion timestamps | Monotonic | unchanged live-flight contract |
| Replay timestamps | Replay | unchanged deterministic replay contract |
| UI labels / persisted wall timestamps | Wall | output-only timebase remains unchanged |

Explicitly forbidden comparisons:

- monotonic vs wall
- replay vs wall

### 6.6 Threading and Cadence

- Dispatcher ownership:
  - `Main`: UI rendering, map-shell collection
  - `Default`: domain math, task/routing logic, replay transforms
  - `IO`: file/network/persistence
- Primary cadence/gating sensor:
  - unchanged; sensor fusion remains baro/GPS driven as documented in `PIPELINE.md`
- Runtime latency budget:
  - unchanged for flight behavior
- Build latency budget:
  - task-only, traffic-only, weather-only, and replay-only edits should stop forcing broad `feature:map` recompilation

### 6.7 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence rules:
  - unchanged; extraction must not alter replay timestamp authority or source gating

### 6.8 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type | File/Test |
|---|---|---|---|
| Module cycles introduced during extraction | `ARCHITECTURE.md` dependency direction | Gradle dependency graph + review | phase-specific build files |
| SSOT duplication in extracted features | `ARCHITECTURE.md` SSOT rules | unit tests + review | moved repository/use-case tests |
| Replay timebase regression | `ARCHITECTURE.md` timebase rules | replay/unit tests | `replay` / `igc` test suites |
| Task-to-map bypass leaks reintroduced | `CODING_RULES.md` task UDF boundaries | `enforceRules` + tests | task rule scripts + task tests |
| Traffic/weather map host coupling remains internal-type based | dependency direction + UI rules | review + compile boundary | facade contracts and module API reviews |
| BuildConfig / resource reach-around remains after split | module boundary rules | compile review + targeted tests | `AdsbAircraftIcon.kt`, `OgnAircraftIcon.kt`, task renderers |

### 6.9 Visual UX SLO Contract

| Outcome | SLO ID | Baseline | Target | Validation/Test | Phase Gate |
|---|---|---|---|---|---|
| No runtime map interaction regression during extraction | impacted `MS-UX-*` / `MS-ENG-*` only when map runtime code changes | current baseline artifacts | unchanged or better | existing MapScreen evidence flow | any phase touching map runtime behavior |

## 7) Data Flow (Before -> After)

Current:

`Sensors / traffic / weather / replay / tasks -> repositories/use-cases inside feature:map -> MapScreenViewModel / other VMs -> UI`

Target:

`Sensors -> core/shared repositories`

`Traffic repositories/use-cases in feature:traffic -> map shell VM/UI`

`Weather repositories/use-cases in feature:weather -> map shell VM/UI`

`Task repositories/use-cases in feature:tasks -> map shell VM/UI via core:task-contract`

`IGC/replay repositories/use-cases in feature:igc -> map shell VM/UI`

`Map shell in feature:map -> compose host/runtime only`

## 8) Delivery Plan

### 8.1 Phase Budget Table

| Phase | Expected PR Count | Timebox | Preferred Max PR Size | Owner | Stop Condition |
|---|---|---|---|---|---|
| Phase 0 | `1` | `0.5 - 1 day` | docs only | XCPro Team | cannot reproduce baseline and coupling counts |
| Phase 1 | `2` | `2 days` | `<= 1200` changed lines per PR | XCPro Team | `feature:igc` would require `feature:weather` dependency |
| Phase 2 | `2 - 3` | `2 - 3 days` | `<= 1500` changed lines per PR | XCPro Team | traffic still imports `map` after config/resource split |
| Phase 3 | `2` | `2 days` | `<= 1500` changed lines per PR | XCPro Team | weather split cannot remove navdrawer ownership cleanly |
| Phase 4 | `3 - 4` | `4 - 5 days` | `<= 1500` changed lines per PR | XCPro Team | task render contracts keep expanding instead of shrinking |
| Phase 5 | `1 - 2` | `1 - 2 days` | `<= 1000` changed lines per PR | XCPro Team | map shell still owns extracted feature settings or editors |

Rule:

- If a PR grows past the preferred size, split it before review. Large module-split PRs are where dependency mistakes hide.

### 8.2 Phase 0 - Baseline and Contract Inventory

- Goal:
  - freeze current compile timings, package counts, and import-cycle candidates
  - define shared contract modules before moving code
- Deliverables:
  - this plan approved
  - one baseline timing artifact committed to the PR description
  - dependency graph screenshots or command output attached to the PR
- Required artifacts:
  - baseline for the four commands in section `2.1`
  - coupling counts from section `1`
  - explicit decision on whether `core:wind-contract` is required
- Exit criteria:
  - target module list is approved
  - cycle-breaking contracts are named explicitly
  - no hidden `feature -> feature` dependency is left unresolved
- Rollback point:
  - docs only

### 8.3 Phase 1 - Extract `feature:igc`

- Goal:
  - move `igc` + `replay` packages and replay/file screens into `feature:igc`
  - keep map shell depending on public APIs only
- Concrete move set:
  - `com.trust3.xcpro.igc.**`
  - `com.trust3.xcpro.replay.**`
  - `com.trust3.xcpro.screens.replay.*`
- DI / Hilt work:
  - move `IgcBindingsModule.kt` into `feature:igc`
  - split replay-specific providers out of `WindSensorModule.kt` if needed
  - do not leave replay bindings in `feature:map`
  - keep `app` as the place that wires any task-backed `IgcTaskDeclarationSource`
- Android surface checklist:
  - move replay/file screen routes away from `feature:map`
  - keep `deleted map replay route helper` in `feature:map` for now
  - verify no `feature:igc` source imports `com.trust3.xcpro.weather.wind.*`
- Primary verification:
  - `:feature:igc:assembleDebug`
  - `:feature:igc:testDebugUnitTest --tests "com.trust3.xcpro.replay.ReplaySampleEmitterTest"`
  - `:feature:igc:testDebugUnitTest --tests "com.trust3.xcpro.replay.IgcReplayLevoNettoValidationTest"`
  - `:feature:igc:testDebugUnitTest --tests "com.trust3.xcpro.igc.domain.IgcSessionStateMachineTest"`
- Exit criteria:
  - `feature:igc` compiles and owns `igc` / `replay`
  - `map` / `screens` direct imports to `igc` / `replay` are `0`
  - replay timebase/determinism tests pass unchanged
  - replay-to-weather coupling is removed or isolated behind `core:wind-contract`
- Rollback point:
  - revert the `feature:igc` introduction and app wiring only

### 8.4 Phase 2 - Extract `feature:traffic`

- Goal:
  - move `adsb` + `ogn` plus traffic details/settings UI into `feature:traffic`
  - expose exact map-facing traffic APIs instead of repository/package reach-through
  - keep MapLibre overlays, tap routing, and map-specific selection runtime in `feature:map`
- Concrete move set:
  - `com.trust3.xcpro.adsb.**`
  - `com.trust3.xcpro.ogn.**`
  - `AdsbSettings*`, `OgnSettings*`, `HotspotsSettings*`
  - traffic DI modules: `AdsbMetadataModule.kt`, `AdsbNetworkModule.kt`, `OgnThermalModule.kt`, and traffic bindings currently inside `MapBindingsModule.kt`
  - traffic route entry surfaces currently spread across `SettingsRoutes.kt`, `AppNavGraph.kt`, and `SettingsDfRuntime*`
- Public API contract for the phase:
  - export `AdsbTrafficFacade` with the current ADS-B command/state surface used by `MapScreenTrafficCoordinator`
  - export `OgnTrafficFacade` with the current OGN command/state surface used by `MapScreenTrafficCoordinator`
  - expose only `TrafficMapApi` models to `MapScreenBindings`, `MapScreenViewModel`, `MapOverlayManager`, and traffic selection helpers
  - export `TrafficSettingsEntry` composables/routes so both `AppNavGraph` and the map drawer consume traffic-owned entry points
- DI / Hilt work:
  - move `AdsbBindingsModule` out of `MapBindingsModule.kt`
  - move `OgnBindingsModule` out of `MapBindingsModule.kt`
  - move `OgnThermalModule.kt`, `AdsbNetworkModule.kt`, `AdsbMetadataModule.kt`
  - bind `OpenSkyCredentialsPort` from `app` into `feature:traffic` so credentials/config stop reading `feature:map` `BuildConfig`
  - keep only facade injection and map-owned runtime adapters in `feature:map`
- Android surface checklist:
  - move traffic icon drawables/resources with `AdsbAircraftIcon.kt` and `OgnAircraftIcon.kt`; no `com.trust3.xcpro.map.R` imports remain
  - replace `com.trust3.xcpro.map.BuildConfig` usage in `OpenSkyCredentialsRepository.kt` with `OpenSkyCredentialsPort`
  - remove traffic route constants from `feature:map` `SettingsRoutes.kt`; `ADSB`, `OGN`, and `HOTSPOTS` routes become traffic-owned entry points or app-local registrations
  - move both full-screen and drawer traffic settings entry points together:
    - `AppNavGraph` full-screen destinations
    - `SettingsDfRuntimeRouteSubSheets.kt` / `SettingsDfRuntimeSheets.kt` traffic sheet content
    - `SettingsDfRuntimeSubSheets.kt` and `SettingsDfRuntimeCategoryGrid.kt` traffic branch hooks stay map-owned but invoke traffic-owned entries only
  - keep `MapScreenTrafficCoordinator`, `MapOverlayManager`, `AdsbTrafficOverlay`, `OgnTrafficOverlay`, and map selection state in `feature:map`
- Benchmark gate for the phase:
  - `:feature:traffic:assembleDebug <= 18s`
  - `:feature:traffic:compileDebugKotlin` no-op `<= 1.5s`
  - single-file edit in `adsb` or `ogn` followed by `:feature:traffic:compileDebugKotlin <= 5s`
  - targeted repo test loop `<= 14s` using `AdsbTrafficRepositoryEmergencyOutputTest` and one OGN runtime/repository test such as `OgnThermalRepositoryTestRuntime`
- Primary verification:
  - `:feature:traffic:assembleDebug`
  - representative ADS-B tests such as `AdsbTrafficRepositoryEmergencyOutputTest` and `AdsbEmergencyAudioReplayDeterminismTest`
  - representative OGN tests such as `OgnAprsLineParserTest` and `OgnThermalRepositoryTestRuntime`
- Exit criteria:
  - `feature:traffic` compiles
  - `map -> traffic` direct imports go from `207` to `0` direct internal imports; remaining imports are only through the explicit `TrafficMapApi` / facade surface
  - `traffic -> map` direct imports go from `3` to `0`
  - `AppNavGraph` and map drawer traffic entry points come from `feature:traffic`, not `feature:map`
  - `MapScreenBindings` / map traffic selection code no longer import repositories or DI modules; they consume facade outputs only
- Stop and re-plan if:
  - any traffic code still needs `feature:map` resources or `BuildConfig` after the resource/config split
  - `MapOverlayManager` or `MapScreenTrafficCoordinator` still need repository access instead of facade commands/state
  - drawer and full-screen traffic settings ownership diverge into separate implementations
- Rollback point:
  - revert only the `feature:traffic` introduction and traffic screen route moves

### 8.5 Phase 3 - Extract `feature:weather`

- Goal:
  - move `forecast`, `weather`, and weather-specific settings/runtime UI out of `feature:map`
- Concrete move set:
  - `com.trust3.xcpro.forecast.**`
  - `com.trust3.xcpro.weather.**`
  - `ForecastSettings*`
  - `WeatherSettings*`
- DI / Hilt work:
  - move `ForecastNetworkModule.kt` and `ForecastModule.kt`
  - decide final owner of wind/replay shared contracts before completing the phase
  - keep `app` as the place that connects weather facade outputs to the map shell
- Android surface checklist:
  - move weather settings route ownership out of `screens/navdrawer`
  - ensure `WeatherSettingsContentHost` and related sheet ownership live with the weather module
  - confirm map shell only consumes weather facade state after the move
- Primary verification:
  - `:feature:weather:assembleDebug`
  - `WeatherSettingsUseCaseTest`
  - `WeatherSettingsViewModelTest`
  - `ForecastAuthRepositoryTest`
  - targeted overlay regression tests in `feature:map` for weather delegates
- Exit criteria:
  - `feature:weather` compiles
  - `map` / `screens` direct imports to weather internals go from `164` to `0`
  - weather reverse imports to `map` / `screens` go from `1` to `0`
- Rollback point:
  - revert only the `feature:weather` introduction and settings route moves

### 8.6 Phase 4 - Extract `feature:tasks`

- Goal:
  - move `tasks` and task-specific UI into `feature:tasks`
  - create `core:task-contract` to break the current map/task cycle
- Preconditions:
  - `core:task-contract` exists
  - map-owned task rendering ports are defined
  - task screen host port is defined
- Concrete move set:
  - `com.trust3.xcpro.tasks.**`
  - task navdrawer screens and bottom-sheet files
  - task DI modules after contract extraction
- DI / Hilt work:
  - move `TaskPersistenceModule.kt`
  - move `TaskNavigationModule.kt`
  - move `TaskRepositoryIgcTaskDeclarationSource` implementation ownership here if it still exists
  - bind task contracts in `app`, not in `feature:map`
- Android surface checklist:
  - remove `com.trust3.xcpro.map.BuildConfig` imports from:
    - `RacingMapRenderer.kt`
    - `AATEditOverlayRenderer.kt`
    - `AATTaskRenderer.kt`
    - `AATMapRenderer.kt`
  - remove task ownership of `MapTaskScreenManager`, `TaskRenderSnapshot`, and `TaskRenderSyncCoordinator`
- Primary verification:
  - `:feature:tasks:testDebugUnitTest`
  - representative task-domain tests such as:
    - `TaskAdvanceStateTest`
    - `TaskRepositoryProjectionComplianceTest`
    - `DefaultRacingTaskEngineTest`
    - `DefaultAATTaskEngineTest`
    - `RacingReplayValidationTest`
- Exit criteria:
  - `feature:tasks` compiles and owns task domain/UI
  - task imports of `com.trust3.xcpro.map.*` go from `7` to `0`
  - map imports of task internals go from `56` to contract-only
- Rollback point:
  - revert only task module introduction and contract wiring from the current phase

### 8.7 Phase 5 - Slim `feature:map` to Map Shell

- Goal:
  - leave `feature:map` as the map host, runtime controllers, and composition root
  - remove leftover feature-specific settings and adapters now owned elsewhere
- Concrete cleanup:
  - keep map runtime, overlays, route hosting, and shell-specific UI
  - remove any moved feature settings screens still sitting in navdrawer
  - leave `feature:map` with shell-only public API
- Exit criteria:
  - `feature:map` production footprint is `<= 425` Kotlin files
  - feature edits in tasks/traffic/weather/replay do not trigger broad map-shell recompilation
  - map shell has no direct imports of extracted feature internals
- Rollback point:
  - revert only shell cleanup if needed; prior extracted modules remain intact

## 9) Android Surface Checklist

These items are mandatory because Android module splits usually fail in resources, manifests, navigation, and generated config rather than in the business logic itself.

| Surface | Current Hot Spot | Required Action |
|---|---|---|
| Manifest ownership | `feature/map/src/main/AndroidManifest.xml` | each new Android feature module must own its own manifest entries as needed |
| BuildConfig leakage | `OpenSkyCredentialsRepository.kt`, `RacingMapRenderer.kt`, `AATEditOverlayRenderer.kt`, `AATTaskRenderer.kt`, `AATMapRenderer.kt` | replace `map.BuildConfig` reach-around with module-local config or injected ports |
| Resource leakage | `AdsbAircraftIcon.kt`, `OgnAircraftIcon.kt` import `map.R` | move shared drawables/resources or introduce a shell-owned icon contract |
| Navigation routing | `SettingsDfRuntimeRouteSubSheets.kt`, `SettingsDfRuntimeSubSheets.kt`, `SettingsDfRuntimeSheets.kt` | move feature-specific sheet ownership to the extracted module, keep route registration in `app` or shell |
| Hilt aggregation | current bindings spread through `feature:map` | keep aggregation in `app`; move bindings with feature ownership |
| Unit/instrumentation manifests | existing tests under `feature/map/src/test` and `src/androidTest` | move tests with feature modules and add per-module test manifests only where needed |

## 10) Cycle Burn-Down Gates

| Phase | Coupling to Eliminate | Baseline | Target | How to Prove It |
|---|---|---|---|---|
| Phase 1 | `map` / `screens` -> `igc` / `replay` imports | `58` | `0` | import scan + module compile |
| Phase 1 | `igc` / `replay` -> `weather.wind` imports | `5` | `0` direct feature import | import scan + contract review |
| Phase 1 | `igc` / `replay` -> task imports | `1` | `1` contract-only path via `IgcTaskDeclarationSource` | source review |
| Phase 2 | `map` -> traffic imports | `207` | `0` direct internal imports | import scan + module compile |
| Phase 2 | traffic -> `map` imports | `3` | `0` | import scan |
| Phase 3 | `map` / `screens` -> weather/forecast imports | `164` | `0` direct internal imports | import scan + module compile |
| Phase 3 | weather/forecast -> `map` / `screens` imports | `1` | `0` | import scan |
| Phase 4 | `map` / `screens` -> tasks imports | `56` | contract-only | import scan + API review |
| Phase 4 | tasks -> `map` imports | `7` | `0` | import scan |
| Phase 5 | leftover feature settings inside `feature:map` navdrawer | present | `0` | file ownership review |

## 11) Verification and Evidence

### 11.1 Minimum Commands Per Phase

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Run when relevant and device/emulator is available:

```bash
./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
```

### 11.2 No-Behavior-Change Evidence

Each phase PR must attach:

- before/after timings for the metrics in section `2`
- proof that moved unit tests now run from the new module
- proof that old tests were removed only after equivalent new ownership exists
- proof that replay determinism is unchanged for any phase touching replay
- proof that no new global mutable state was introduced
- proof that `PIPELINE.md` stayed unchanged unless actual runtime pipeline wiring changed

### 11.3 Suggested Representative Test Sets

| Phase | Required Representative Tests |
|---|---|
| Phase 1 | `ReplaySampleEmitterTest`, `IgcReplayLevoNettoValidationTest`, `IgcSessionStateMachineTest`, `IgcFlightLogRepositoryTest` |
| Phase 2 | `AdsbEmergencyAudioReplayDeterminismTest`, `AdsbTrafficRepositoryEmergencyOutputTest`, `OgnAprsLineParserTest`, `OgnThermalRepositoryTestRuntime` |
| Phase 3 | `WeatherSettingsUseCaseTest`, `WeatherSettingsViewModelTest`, `ForecastAuthRepositoryTest`, `MapOverlayManagerRuntimeForecastWeatherDelegateWeatherRainTest` |
| Phase 4 | `TaskAdvanceStateTest`, `TaskRepositoryProjectionComplianceTest`, `DefaultRacingTaskEngineTest`, `DefaultAATTaskEngineTest`, `RacingReplayValidationTest` |
| Phase 5 | map-shell integration tests and route-host regression tests touching extracted feature entry points |

## 12) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Hidden module cycles block extraction | phase stalls | add contract modules before moving code; stop on any new feature-to-feature dependency | XCPro Team |
| `feature:igc` depends on weather runtime classes | illegal graph | introduce `core:wind-contract` or relocate the minimal replay wind contracts before moving code | XCPro Team |
| Large task slice spills into too many phases | delayed build-speed benefit | keep task extraction last, but make its contract work explicit in Phase 3/4 | XCPro Team |
| Map runtime regressions during UI movement | user-visible behavior drift | require SLO evidence for any runtime-touching phase | XCPro Team |
| Build speed does not improve enough after early phases | weak ROI | compare against section `2.3`; re-order remaining phases if needed | XCPro Team |
| DI graph churn causes wiring breakage | compile/runtime failures | move Hilt modules with each feature and keep app as composition root | XCPro Team |
| Resource and BuildConfig reach-around remains hidden until late | compile failures during split | use the Android surface checklist in section `9` as a hard gate, not a cleanup note | XCPro Team |

## 13) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`
- No duplicate SSOT ownership introduced
- Time base handling remains explicit and unchanged
- Replay behavior remains deterministic
- Any phase touching map/overlay/runtime behavior must attach impacted SLO evidence
- No extracted feature depends on `feature:map` internals
- No phase continues if its measured target is missed by more than `10%`

## 14) Rollback Plan

### 14.1 Independent Revert Units

- each extracted feature module phase
- contract-module introductions
- app dependency wiring changes
- navdrawer/screen ownership moves

### 14.2 Phase Rollback Boundaries

| Phase | Safe Revert Boundary | Keep Intact |
|---|---|---|
| Phase 0 | docs only | everything |
| Phase 1 | `feature:igc` module + app wiring | baseline build cleanup already landed |
| Phase 2 | `feature:traffic` module + route moves | `feature:igc` if already green |
| Phase 3 | `feature:weather` module + route moves | `feature:igc`, `feature:traffic` if already green |
| Phase 4 | `feature:tasks` module + `core:task-contract` wiring from current phase | prior extracted modules |
| Phase 5 | shell cleanup only | all extracted modules |

### 14.3 Recovery Steps if Regression Is Detected

- revert the current phase only
- keep prior extracted modules intact if their gates are already green
- restore app/module dependency edges from the previous passing phase
- preserve baseline timing artifacts so regression analysis is comparable
