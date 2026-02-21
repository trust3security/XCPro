
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

OGN settings path:
- `feature/map/src/main/java/com/example/xcpro/ogn/OgnTrafficPreferencesRepository.kt`
  - SSOT for OGN overlay enabled + icon size + `showThermalsEnabled` + `showGliderTrailsEnabled` preferences.
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenUseCases.kt`
  - `OgnTrafficUseCase` exposes OGN settings, thermal-hotspot flows, and OGN glider trail segment flows.
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt`
  - Converts OGN settings, thermal-hotspot state, and OGN trail state to lifecycle-aware UI/runtime state.
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRoot.kt`
  - Pushes OGN overlay targets, thermal hotspots, and OGN trail segments into runtime overlay manager.
- `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManager.kt`
  - Applies icon size for OGN traffic overlays and owns OGN thermal + OGN glider-trail overlay runtime lifecycle.
- `feature/map/src/main/java/com/example/xcpro/map/OgnTrafficOverlay.kt`
  - Updates SymbolLayer `iconSize` dynamically from configured pixel size.
- `feature/map/src/main/java/com/example/xcpro/ogn/OgnThermalRepository.kt`
  - Derives session-lifetime thermal hotspots from OGN targets (in-memory until app restart).
  - Applies thermal metrics only on fresh OGN samples per target (`lastSeenMillis` monotonic freshness gate).
  - Prunes freshness-cache entries for absent targets after timeout to avoid unbounded session growth while preserving stale-present target protection.
  - Runs repository-side housekeeping timers so thermal continuity/missing finalization occurs even when upstream target lists are quiet.
- `feature/map/src/main/java/com/example/xcpro/map/OgnThermalOverlay.kt`
  - Renders color-coded thermal hotspots using snail-trail climb palette indexing.
- `feature/map/src/main/java/com/example/xcpro/ogn/OgnGliderTrailRepository.kt`
  - Derives per-glider OGN trail segments from fresh OGN target samples.
  - Owns sink/climb style mapping (color index + asymmetric width) and bounded in-memory retention.
  - Uses injected monotonic clock for deterministic retention housekeeping.
- `feature/map/src/main/java/com/example/xcpro/map/OgnGliderTrailOverlay.kt`
  - Renders line segments using precomputed OGN trail style properties from repository output.

OGN lifecycle/position semantics:
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenTrafficCoordinator.kt`
  - Streaming enable is driven by `allowSensorStart && mapVisible && ognOverlayEnabled`.
  - Query center updates are GPS-driven from `mapLocation` (user position), not camera center.
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapOverlayStack.kt`
  - Tap routing resolves traffic markers before wind callouts.
  - OGN marker taps are resolved before thermal and ADS-b taps.
  - Thermal hotspot taps are resolved before ADS-b taps.
  - Thermal hotspot taps route to thermal details selection when `showThermalsEnabled` is on.
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt`
  - Owns selected OGN/thermal/ADS-b selection state and enforces mutual exclusion across these details sheets.
- `feature/map/src/main/java/com/example/xcpro/ogn/OgnTrafficRepository.kt`
  - Uses APRS radius filtering and client-side haversine filtering at 150 km radius
    (300 km diameter contract around user position).
  - Client-side filtering is evaluated against latest requested GPS center so the
    300 km diameter policy stays user-centered between reconnects.
  - Socket subscription reconnects when requested center moves >= 20 km from
    active subscription center.
  - Connection state remains `CONNECTING` until server `logresp verified` or
    first valid traffic frame.
  - If no center is available yet, repository waits before opening the stream.
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRoot.kt`
  - OGN traffic overlay renders `emptyList()` when overlay preference is disabled.
  - Thermal overlay renders `emptyList()` unless `ognOverlayEnabled && showThermalsEnabled`.
  - OGN glider-trail overlay renders `emptyList()` unless `ognOverlayEnabled && showGliderTrailsEnabled`.
- `feature/map/src/main/java/com/example/xcpro/ogn/OgnMarkerDetailsSheet.kt`
  - Renders selected OGN target details in a `ModalBottomSheet`.
- `feature/map/src/main/java/com/example/xcpro/ogn/OgnThermalDetailsSheet.kt`
  - Renders selected thermal hotspot details in a partially-expandable `ModalBottomSheet`.
- `feature/map/src/main/java/com/example/xcpro/ogn/OgnThermalDetailsSheet.kt`
  - Renders selected thermal details in a half-sheet style `ModalBottomSheet`.

ADS-b settings path:
- `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficPreferencesRepository.kt`
  - SSOT for ADS-b overlay enabled + icon size + max distance + vertical above/below preferences.
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenUseCases.kt`
  - `AdsbTrafficUseCase` exposes ADS-b settings flows (`iconSizePx`, `maxDistanceKm`, `verticalAboveMeters`, `verticalBelowMeters`).
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt`
  - Converts ADS-b settings flows and ownship altitude into lifecycle-aware state for UI/runtime wiring.
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRoot.kt`
  - Pushes icon-size changes into overlay runtime controller.
- `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManager.kt`
  - Applies and re-applies icon size for existing and recreated overlays.
- `feature/map/src/main/java/com/example/xcpro/map/AdsbTrafficOverlay.kt`
  - Updates SymbolLayer `iconSize` dynamically from configured pixel size.

ADS-b lifecycle/visibility semantics:
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt`
  - Map overlay targets come from the raw ADS-b repository stream with metadata merged opportunistically.
  - Map marker positions are not gated by metadata-enrichment latency.
- `feature/map/src/main/java/com/example/xcpro/adsb/metadata/data/AircraftMetadataRepositoryImpl.kt`
  - On-demand ICAO metadata upserts emit a metadata revision signal.
- `feature/map/src/main/java/com/example/xcpro/adsb/metadata/domain/AdsbMetadataEnrichmentUseCase.kt`
  - Target/icon enrichment and selected-target details recompute when metadata revision changes,
    so icon/category overrides refresh immediately after metadata persistence (no extra poll wait).
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenTrafficCoordinator.kt`
  - Streaming enable is driven by `allowSensorStart && mapVisible && adsbOverlayEnabled`.
  - When streaming turns on, center is seeded from current GPS position (camera fallback when GPS is unavailable).
  - Query-center and ownship-origin updates are GPS-driven from `mapLocation`.
  - Ownship altitude and ADS-b filter settings flows are forwarded to the ADS-b repository runtime.
  - Explicit ADS-b FAB off triggers immediate repository target clear.
- `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficRepository.kt`
  - Disabling streaming pauses polling without clearing last-known targets.
  - Polling is connectivity-aware via `AdsbNetworkAvailabilityPort`; retries pause while offline and resume on network restoration.
  - Poll retry + circuit-breaker state transitions are owned by `AdsbPollingHealthPolicy`.
  - Explicit clear path removes cached targets and resets displayed list.
  - Query center is used for fetch/radius filtering (configurable `1..100 km`, default `10 km`).
  - Ownship origin is used for displayed distance/bearing when available.
  - Ownship altitude is used for vertical above/below filtering with fail-open when altitude is unavailable.
- `feature/map/src/main/java/com/example/xcpro/adsb/data/AndroidAdsbNetworkAvailabilityAdapter.kt`
  - Android connectivity callback adapter bound to the ADS-B network-availability port.
  - Callback events are normalized by `AdsbNetworkAvailabilityTracker` (including fail-open registration fallback).
- `feature/map/src/main/java/com/example/xcpro/map/AdsbTrafficOverlay.kt`
  - Per-aircraft runtime interpolation smooths marker motion between provider samples.
  - Proximity color expression is distance-based with emergency override priority.
  - Interpolation is visual-only and does not mutate repository SSOT.
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRoot.kt`
  - ADS-b overlay renders `emptyList()` when overlay preference is disabled.

Forecast overlay (SkySight-backed) path:
- `feature/map/src/main/java/com/example/xcpro/forecast/ForecastPreferencesRepository.kt`
  - SSOT for forecast overlay preferences (`enabled`, `opacity`, `autoTimeEnabled`, `selectedPrimaryParameterId`, `selectedTimeUtcMs`, `selectedRegion`).
  - Optional secondary primary overlay prefs (`secondaryPrimaryOverlayEnabled`, `selectedSecondaryPrimaryParameterId`).
  - Wind-overlay prefs are separate (`windOverlayEnabled`, `selectedWindParameterId`, `windOverlayScale`, `windDisplayMode`).
- `feature/map/src/main/java/com/example/xcpro/di/ForecastModule.kt`
  - Binds forecast ports to `SkySightForecastProviderAdapter` in production runtime.
- `feature/map/src/main/java/com/example/xcpro/forecast/SkySightForecastProviderAdapter.kt`
  - Adapter for SkySight catalog/tile/legend/value contracts.
  - Resolves region-aware time slots, per-parameter tile URL formats, source-layer candidates, and point fields.
- `feature/map/src/main/java/com/example/xcpro/forecast/FakeForecastProviderAdapter.kt`
  - Test utility adapter for unit tests and local contract validation only.
- `feature/map/src/main/java/com/example/xcpro/forecast/ForecastOverlayRepository.kt`
  - Composes prefs + provider ports into overlay-ready state and point-query results.
  - Emits primary layer state, optional secondary-primary layer state, and optional wind-layer state independently.
  - Maintains last-good tile/legend with fatal-vs-warning error separation
    (primary tile failure is fatal; secondary-primary and wind-layer failures are warning-only).
- `feature/map/src/main/java/com/example/xcpro/forecast/ForecastOverlayViewModel.kt`
  - ViewModel-intent boundary for enable/time/opacity and long-press point query.
  - Non-wind parameter selection is a single multi-select intent (max 2) mapped to
    internal primary + optional secondary-primary preferences.
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContent.kt`
  - Collects forecast state, opens forecast sheet, dispatches long-press point queries
    (disabled during AAT edit mode), and renders callout/status.
  - Forecast sheet exposes one non-wind parameter list (max 2 selected) plus separate
    wind overlay controls.
- `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManager.kt`
  - Runtime owner for forecast raster overlay lifecycle and style-reload reapplication.
  - Hosts three runtime overlay instances: primary forecast layer + optional secondary-primary layer + optional wind overlay layer.
- `feature/map/src/main/java/com/example/xcpro/map/ForecastRasterOverlay.kt`
  - MapLibre runtime controller for forecast vector layers.
  - Uses namespace-scoped layer/source IDs so multiple forecast overlays can render together.
  - Supports indexed-fill overlays and wind-point overlays with branch-specific layer cleanup.
  - Wind-point rendering supports `ARROW` and `BARB` display modes from forecast preferences SSOT.

Weather rain overlay path:
- `feature/map/src/main/java/com/example/xcpro/weather/rain/WeatherOverlayPreferencesRepository.kt`
  - SSOT for weather rain overlay preferences (`enabled`, `opacity`, animation toggle, animation window, animation speed, transition quality, frame mode, render options).
- `feature/map/src/main/java/com/example/xcpro/weather/rain/WeatherRadarMetadataRepository.kt`
  - RainViewer metadata fetch + parse (`weather-maps.json`) with runtime status/fallback handling.
- `feature/map/src/main/java/com/example/xcpro/weather/rain/ObserveWeatherOverlayStateUseCase.kt`
  - Combines preferences + metadata into frame-based runtime state (`selectedFrame`, status, effective transition duration).
- `feature/map/src/main/java/com/example/xcpro/weather/rain/WeatherOverlayViewModel.kt`
  - Map-side weather overlay state for runtime binding.
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapWeatherOverlayEffects.kt`
  - Collects weather overlay state and forwards frame-based runtime updates (including transition duration) to overlay manager.
- `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManager.kt`
  - Owns weather overlay runtime config/status and reapply behavior on map-ready/style change.
- `feature/map/src/main/java/com/example/xcpro/map/WeatherRainOverlay.kt`
  - MapLibre raster source/layer runtime with per-frame cache and bounded cross-fade between frames to reduce animation stutter/blink when cycling.

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

Task map rendering bridge (2026-02-12):
- `TaskManagerCoordinator` no longer stores a map instance.
- `TaskManagerCoordinator` AAT edit/target APIs are map-agnostic (no `MapLibreMap` parameters).
- Single trigger owner for map task redraw:
  - `feature/map/src/main/java/com/example/xcpro/map/TaskRenderSyncCoordinator.kt`
  - Trigger sources emit coordinator events from:
    - `feature/map/src/main/java/com/example/xcpro/tasks/TaskMapOverlay.kt` (task state changes)
    - `feature/map/src/main/java/com/example/xcpro/map/MapInitializer.kt` (map ready)
    - `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManager.kt` (style/overlay refresh)
    - `feature/map/src/main/java/com/example/xcpro/map/ui/MapOverlayStack.kt` (AAT edit + gesture mutations)
- Coordinator owns the only runtime call path to:
  - `TaskMapRenderRouter.syncTaskVisuals(...)`
  - This API owns clear + orphan cleanup + conditional replot sequencing.
- Rendering is selected by current task type (RACING/AAT) in the UI runtime layer.
- `TaskMapRenderRouter` consumes `TaskRenderSnapshot` from `MapTasksUseCase`
  (`taskRenderSnapshot()`) and shared core->task mappers (Racing/AAT) instead
  of coordinator manager escape hatches.
- `RacingTaskManager` / `AATTaskManager` no longer expose MapLibre render/edit APIs;
  map rendering/editing flows are UI/runtime-only via renderers/controllers.
- `MapInitializer` orchestration is split into focused runtime collaborators:
  - `MapScaleBarController` (scale bar lifecycle/zoom constraints)
  - `MapInitializerDataLoader` (airspace/waypoint bootstrap and refresh)
  - `MapStyleUrlResolver` (canonical style-name -> URL resolution for runtime style paths)
- `MapInitializer.setupMapStyle(...)` uses bounded style-load wait with fallback init to avoid startup hangs.
- `MapRuntimeController` applies style commands with map-generation/request-token guards so stale callbacks do not mutate active overlays.
- `MapScreenViewModel` now exposes task type and task gesture/edit operations through
  `MapTasksUseCase`, and map runtime effects consume ViewModel-bound task type state
  instead of reading coordinator state directly in Composables.
- Runtime UI/composable dependency lookup no longer uses entrypoint accessors:
  - `TaskManagerCoordinator` is provided via `TaskManagerCoordinatorHostViewModel`.
  - Task navdrawer airspace use-case access is provided via `TaskScreenUseCasesViewModel`.

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

