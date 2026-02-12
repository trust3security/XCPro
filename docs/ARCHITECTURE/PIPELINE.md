
# PIPELINE.md

Purpose: document the end-to-end live data pipeline (sensors -> fusion -> SSOT -> UI + audio),
plus how replay and parallel pipelines attach. Update this file whenever the wiring changes.

Diagram: `PIPELINE.svg`.

## Quick Map (Live)

Sensors
  -> SensorRegistry (Android listeners, timestamps)
  -> UnifiedSensorManager (StateFlow per sensor)
  -> FlightDataCalculatorEngine (fusion + metrics + audio)
  -> SensorFusionRepository.flightDataFlow
  -> VarioServiceManager
  -> FlightDataRepository (SSOT, Source gating)
  -> FlightDataUseCase
  -> MapScreenViewModel
     -> FlightDataUiAdapter (MapScreenObservers) -> convertToRealTimeFlightData -> FlightDataManager
     -> mapLocation (GPS) for map UI
  -> UI overlays + dfcards FlightDataViewModel (cards)

Audio taps the pipeline inside FlightDataCalculatorEngine:
  FlightDataCalculatorEngine -> VarioAudioController -> VarioAudioEngine

## 1) Sensor Ingestion (Live)

Entry point:
- `app/src/main/java/com/example/xcpro/service/VarioForegroundService.kt`
  - starts the background pipeline and keeps it alive.
- `feature/map/src/main/java/com/example/xcpro/vario/VarioServiceManager.kt`
  - orchestrates sensors, fusion repo, and repository updates.

Live sensor wiring:
- `feature/map/src/main/java/com/example/xcpro/sensors/SensorRegistry.kt`
  - Android LocationManager + SensorManager listeners.
  - Emits GPS, baro, compass, accel, and attitude samples.
  - Timestamps:
    - GPS uses `Location.elapsedRealtimeNanos` for monotonic time.
    - Baro/IMU use `SensorEvent.timestamp` for monotonic time.
    - Wall time comes from injected `Clock.nowWallMs()`.
- `feature/map/src/main/java/com/example/xcpro/sensors/UnifiedSensorManager.kt`
  - Exposes StateFlow per sensor (GPS, baro, compass, accel, raw accel, attitude).
  - Starts/stops sensors and publishes GPS status.

DI bindings:
- `feature/map/src/main/java/com/example/xcpro/di/WindSensorModule.kt`
  - Live sensor data source = `UnifiedSensorManager`.
  - Replay sensor data source = `ReplaySensorSource`.

## 2) Fusion + Metrics (Live)

Fusion entry:
- `feature/map/src/main/java/com/example/xcpro/sensors/SensorFusionRepositoryFactory.kt`
  - builds a `SensorFusionRepository` using a `SensorDataSource`.
- `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculator.kt`
  - thin wrapper around `FlightDataCalculatorEngine`.
- `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngine.kt`
  - owns fusion loops, filters, metrics use case, and audio controller.

Loops (two decoupled loops):
- `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngineLoops.kt`
  - High-rate baro + accel loop (50 Hz target):
    - `updateVarioFilter(baro, accel)`
    - updates vario filters, baro altitude, TE fusion inputs, and audio.
    - emits display frames on baro cadence (throttled).
  - GPS + compass loop (~10 Hz):
    - `updateGPSData(gps, compass)`
    - updates cached GPS, GPS vario, and emits a frame if baro is stale.

Filters and vario:
- `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataFilters.kt`
  - pressure Kalman + baro filter.
- `feature/map/src/main/java/com/example/xcpro/sensors/VarioSuite.kt`
  - optimized/legacy/raw/gps/complementary vario implementations.

Metrics use case:
- `feature/map/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt`
  - TE/netto, display smoothing, circling detection, LD, thermal metrics.
  - Owns deterministic windows and is testable without Android.

Mapping to SSOT model:
- `feature/map/src/main/java/com/example/xcpro/flightdata/FlightDisplayMapper.kt`
  - maps domain metrics + sensors to `CompleteFlightData`.
- `feature/map/src/main/java/com/example/xcpro/sensors/SensorData.kt`
  - defines `CompleteFlightData` (SSOT for calculated flight data).

Emission:
- `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataEmitter.kt`
  - builds `FlightDisplaySnapshot`, maps to `CompleteFlightData`,
    and publishes to `flightDataFlow`.

## 3) SSOT Repository + Source Gating

SSOT:
- `feature/map/src/main/java/com/example/xcpro/flightdata/FlightDataRepository.kt`
  - Holds the latest `CompleteFlightData`.
  - `Source` gate prevents live sensors from overriding replay.

Live forwarder:
- `feature/map/src/main/java/com/example/xcpro/vario/VarioServiceManager.kt`
  - Collects `sensorFusionRepository.flightDataFlow` and updates repository.
  - Observes `LevoVarioPreferencesRepository` to push:
    - audio settings
    - MacCready settings
    - auto-MC flag

## 4) Use Case -> ViewModel

Use case:
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenUseCases.kt`
  - `FlightDataUseCase` exposes `FlightDataRepository.flightData`.

ViewModel:
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt`
  - `mapLocation` = `flightData.gps` (SSOT for map location).
  - Instantiates `FlightDataUiAdapter` (wraps `MapScreenObservers`) for data fan-out.

## 5) ViewModel -> UI (Map + Cards)

Observers:
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenObservers.kt` (wrapped by `FlightDataUiAdapter`)
  - Combines flight data + wind + flying state.
  - Converts `CompleteFlightData` to `RealTimeFlightData`.
  - Pushes to `FlightDataManager` and trail processor.

Mapping for cards:
- `feature/map/src/main/java/com/example/xcpro/MapScreenUtils.kt`
  - `convertToRealTimeFlightData(...)` maps SSOT to card-friendly model.

UI smoothing/bridging:
- `feature/map/src/main/java/com/example/xcpro/map/FlightDataManager.kt`
  - Buckets and throttles UI-facing values (needle vs numeric cadence).
  - Exposes `cardFlightDataFlow` and other overlay flows.

UI effects:
- `feature/map/src/main/java/com/example/xcpro/map/ui/effects/MapComposeEffects.kt`
  - Uses `liveFlightDataFlow` for orientation updates and replay map pose.
  - Calls `prepareCardsForProfile(...)` on profile/mode/size changes.
  - Binds `CardIngestionCoordinator` (single ingestion owner for card updates).

Map bindings:
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenBindings.kt`
  - Binds `mapLocation` into UI state.
  - Binds `ognIconSizePx` and `adsbIconSizePx` from settings for runtime overlay sizing.

OGN icon size settings path:
- `feature/map/src/main/java/com/example/xcpro/ogn/OgnTrafficPreferencesRepository.kt`
  - SSOT for OGN overlay enabled + icon size preferences.
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenUseCases.kt`
  - `OgnTrafficUseCase` exposes `iconSizePx` flow.
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt`
  - Converts `iconSizePx` to lifecycle-aware state for UI/runtime.
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRoot.kt`
  - Pushes icon-size changes into overlay runtime controller.
- `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManager.kt`
  - Applies and re-applies icon size for existing and recreated OGN overlays.
- `feature/map/src/main/java/com/example/xcpro/map/OgnTrafficOverlay.kt`
  - Updates SymbolLayer `iconSize` dynamically from configured pixel size.

ADS-b icon size settings path:
- `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficPreferencesRepository.kt`
  - SSOT for ADS-b overlay enabled + icon size preferences.
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenUseCases.kt`
  - `AdsbTrafficUseCase` exposes `iconSizePx` flow.
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt`
  - Converts `iconSizePx` to lifecycle-aware state for UI/runtime.
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRoot.kt`
  - Pushes icon-size changes into overlay runtime controller.
- `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManager.kt`
  - Applies and re-applies icon size for existing and recreated overlays.
- `feature/map/src/main/java/com/example/xcpro/map/AdsbTrafficOverlay.kt`
  - Updates SymbolLayer `iconSize` dynamically from configured pixel size.

ADS-b lifecycle/visibility semantics:
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt`
  - Streaming enable is driven by `allowSensorStart && mapVisible && adsbOverlayEnabled`.
  - When streaming turns on, center is seeded from current camera/GPS before enabling.
  - Center updates use immediate-first + sampled cadence (1.5s window) to avoid debounce starvation under continuous updates.
  - Ownship origin updates are pushed from live GPS into ADS-b repository for distance/bearing semantics.
  - Explicit ADS-b FAB off triggers immediate repository target clear.
- `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficRepository.kt`
  - Disabling streaming pauses polling without clearing last-known targets.
  - Explicit clear path removes cached targets and resets displayed list.
  - Query center is used for fetch/radius filtering; ownship origin is used for displayed distance/bearing when available.
  - Center/origin updates trigger immediate store reselection for cached targets.
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRoot.kt`
  - ADS-b overlay renders `emptyList()` when overlay preference is disabled.

## 5A) Cards (dfcards-library) sub-pipeline

Cards do not read sensors directly. They consume `RealTimeFlightData` produced by the app module.

Current wiring:
- `FlightDataRepository`
  -> `FlightDataUiAdapter` / `MapScreenObservers`
  -> `convertToRealTimeFlightData(...)`
  -> `FlightDataManager.cardFlightDataFlow`
  -> `CardIngestionCoordinator`
  -> `dfcards` `FlightDataViewModel.updateCardsWithLiveData(...)`
  -> `CardLibrary` / `CardContainer` / `EnhancedFlightDataCard`

Card configuration + hydration (current):
- `MapComposeEffects.prepareCardsForProfile(...)` binds profile/mode + container size (density).
- `CardContainer` calls `initializeCards(...)` and `ensureCardsExist(...)` when size/selection are ready.
- `CardContainer` reports safe size -> `MapScreenViewModel.updateSafeContainerSize(...)`
  -> `MapStateStore.safeContainerSize` -> `FlightDataUiAdapter`/`MapScreenObservers` sets `containerReady`.
- `MapScreenRoot` seeds a fallback safe size from screen metrics if CardContainer is delayed
  (`ensureSafeContainerFallback`).
- `MapScreenViewModel.cardHydrationReady = containerReady && liveDataReady`;
  `CardIngestionCoordinator` gates updates and consumes the buffered sample when ready.
- `CardContainer` injects `CardStrings` / `CardTimeFormatter`; `CardIngestionCoordinator` pushes units prefs.

Card cadence (current):
- `FlightDataManager.cardFlightDataFlow`: bucketed, unthrottled (near pass-through).
- dfcards tiers: FAST 80ms, PRIMARY 250ms, BACKGROUND 1000ms.
- Effective cadence: FAST 80ms, PRIMARY 250ms, BACKGROUND 1000ms.
- Owner: dfcards tiers (single cadence gate).

Card previews (FlightDataMgmt, current):
- No cardFlow collector; previews use `FlightDataManager.liveFlightDataFlow` (read-only) for CardsGrid/TemplateEditor.

Key files:
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenObservers.kt`
- `feature/map/src/main/java/com/example/xcpro/MapScreenUtils.kt`
- `feature/map/src/main/java/com/example/xcpro/map/FlightDataManager.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/effects/MapComposeEffects.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapOverlayStack.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRoot.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRootHelpers.kt`
- `feature/map/src/main/java/com/example/xcpro/map/CardIngestionCoordinator.kt`
- `feature/map/src/main/java/com/example/xcpro/screens/flightdata/FlightDataMgmt.kt`
- `dfcards-library/src/main/java/com/example/dfcards/dfcards/FlightDataViewModel.kt`
- `dfcards-library/src/main/java/com/example/dfcards/dfcards/CardContainer.kt`
- `dfcards-library/src/main/java/com/example/dfcards/FlightDataSources.kt`

## 5B) Task Management Pipeline (Racing + AAT)

Target architecture flow:

Task UI (Compose)
  -> `TaskSheetViewModel` intents
  -> `TaskSheetUseCase` + `TaskSheetCoordinatorUseCase`
  -> task repository/coordinator authoritative state
  -> `TaskUiState` StateFlow
  -> Task UI render

Authoritative ownership:
- Task definition and active leg: task repository/coordinator owners.
- Zone entry policy and auto-advance policy: domain/use-case logic.
- Persistence: repository/persistence adapters (not ViewModel/UI).
- Map drawing side effects: runtime controllers invoked by use-case/viewmodel orchestration, not direct Composable manager calls.

Current persistence startup bridge (2026-02-11):
- `MapScreenViewModel` startup
  -> `MapTasksUseCase.loadSavedTasks()`
  -> `TaskManagerCoordinator.loadSavedTasks()` (suspend)
  -> `TaskEnginePersistenceService.restore()` for task type + autosaved engine state
  -> coordinator applies restored engine state into legacy managers for current UI compatibility.

Named task persistence bridge (2026-02-11):
- `TaskManagerCoordinator` named operations (`list/save/load/delete`) route to
  `TaskEnginePersistenceService` in DI runtime.
- Runtime coordinator construction is DI-only; compatibility access uses the DI singleton.

Task map rendering bridge (2026-02-11):
- `TaskManagerCoordinator` no longer stores a map instance.
- `TaskManagerCoordinator` AAT edit/target APIs are map-agnostic (no `MapLibreMap` parameters).
- UI/runtime map drawing routes through:
  - `feature/map/src/main/java/com/example/xcpro/tasks/TaskMapRenderRouter.kt`
  - `feature/map/src/main/java/com/example/xcpro/tasks/TaskMapOverlay.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/MapInitializer.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManager.kt`
- UI/runtime redraw for AAT edit transitions and drag updates is triggered from:
  - `feature/map/src/main/java/com/example/xcpro/map/MapGestureSetup.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/MapTaskIntegration.kt`
- Rendering is selected by current task type (RACING/AAT) in the UI runtime layer.
- `TaskMapRenderRouter` consumes `TaskManagerCoordinator.currentTask` snapshots and
  shared core->task mappers (Racing/AAT) instead of coordinator manager escape hatches.
- `RacingTaskManager` / `AATTaskManager` no longer expose MapLibre render/edit APIs;
  map rendering/editing flows are UI/runtime-only via renderers/controllers.

Task navigation/replay bridge (2026-02-11):
- `TaskNavigationController` and `MapScreenReplayCoordinator` consume
  coordinator core-task snapshots and shared mapping helpers for racing
  navigation/replay inputs.
- Navigation/replay code paths do not read `RacingTaskManager.currentRacingTask`
  directly.

Non-negotiable boundaries:
- Composables do not call task managers/coordinators directly for mutation or business queries.
- Composables do not read manager internals (`currentTask`, `currentLeg`, `currentAATTask`) as UI state.
- ViewModels expose task UI state and intents; they do not expose raw manager/controller handles.

## 6) Audio Pipeline

Audio control:
- `feature/map/src/main/java/com/example/xcpro/audio/VarioAudioController.kt`
  - Selects TE vario if valid, otherwise raw vario.
  - Silences audio when data is stale.

Engine:
- `feature/map/src/main/java/com/example/xcpro/audio/VarioAudioEngine.kt`
  - Tone generation + beep controller, responds to vertical speed updates.

Settings:
- `feature/map/src/main/java/com/example/xcpro/vario/LevoVarioPreferencesRepository.kt`
  - Provides `VarioAudioSettings` to both live and replay pipelines.

## 7) Parallel Pipelines (Wind + Flight State)

Wind fusion:
- `feature/map/src/main/java/com/example/xcpro/weather/wind/data/WindSensorFusionRepository.kt`
  - Consumes wind inputs derived from sensors/airspeed.
  - Switches live/replay inputs based on `FlightDataRepository.activeSource`.
  - `windState` feeds:
    - `FlightDataCalculatorEngine` (metrics)
    - `MapScreenObservers` (cards/wind UI, wrapped by `FlightDataUiAdapter`)

Flight state:
- `feature/map/src/main/java/com/example/xcpro/sensors/FlightStateRepository.kt`
  - Derives `FlyingState` from GPS/baro/airspeed.
  - Used by metrics and map observers.

## 8) Replay Pipeline (High-Level)

Replay sensors:
- `feature/map/src/main/java/com/example/xcpro/replay/ReplaySensorSource.kt`
  - `SensorDataSource` implementation for replay samples.

Replay pipeline:
- `feature/map/src/main/java/com/example/xcpro/replay/ReplayPipeline.kt`
  - Creates a replay `SensorFusionRepository` (isReplayMode = true).
  - Forwards fused `CompleteFlightData` into `FlightDataRepository` with Source.REPLAY.
  - Suspends/resumes live sensors via `VarioServiceManager`.

Controller:
- `feature/map/src/main/java/com/example/xcpro/replay/IgcReplayController.kt`
  - Owns session state, sample emission, and source gating.
  - Clears repository on stop to avoid stale UI data.

## 9) Time Base Rules (Enforced by Design)

- Live fusion uses monotonic timestamps for deltas/validity windows.
- Replay uses IGC timestamps as the simulation clock.
- Output timestamp:
  - Live: wall time for UI labels.
  - Replay: IGC time for UI.

Key references:
- `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngineLoops.kt`
- `feature/map/src/main/java/com/example/xcpro/sensors/SensorRegistry.kt`

## 10) Primary Files Index

Core pipeline:
- `feature/map/src/main/java/com/example/xcpro/sensors/UnifiedSensorManager.kt`
- `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngine.kt`
- `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngineLoops.kt`
- `feature/map/src/main/java/com/example/xcpro/flightdata/FlightDataRepository.kt`
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt`
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenObservers.kt`

Audio:
- `feature/map/src/main/java/com/example/xcpro/audio/VarioAudioController.kt`
- `feature/map/src/main/java/com/example/xcpro/audio/VarioAudioEngine.kt`

Replay:
- `feature/map/src/main/java/com/example/xcpro/replay/IgcReplayController.kt`
- `feature/map/src/main/java/com/example/xcpro/replay/ReplayPipeline.kt`

