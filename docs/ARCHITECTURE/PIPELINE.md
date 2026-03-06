
# PIPELINE.md

Purpose: document the end-to-end live data pipeline (sensors -> fusion -> SSOT -> UI + audio),
plus how replay and parallel pipelines attach. Update this file whenever the wiring changes.

Diagram: `PIPELINE.svg`.

## Automated Quality Gates

These artifacts block architecture/timebase regressions:
- Local gate script: `scripts/arch_gate.py`
- CI workflow: `.github/workflows/quality-gates.yml`
- Gradle rule gate: `./gradlew enforceRules`
- Map visual SLO contract:
  - `docs/MAPSCREEN/02_BASELINE_PROFILING_AND_SLO_MATRIX_2026-03-05.md`
  - `docs/MAPSCREEN/04_TEST_VALIDATION_AND_ROLLBACK_2026-03-05.md`

Gate intent:
- Block direct production calls to forbidden wall/system time APIs outside approved adapter files.
- Block known architecture drift patterns enforced by `enforceRules`.
- Require unit-test and assemble baselines for PR readiness.
- Require measurable SLO evidence for map interaction/overlay behavior changes.

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
  - selects source-aware airspeed feed (`@LiveSource` vs `@ReplaySource` `AirspeedDataSource`)
    and injects it into the fusion engine.
- `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculator.kt`
  - thin wrapper around `FlightDataCalculatorEngine`.
- `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngine.kt`
  - owns fusion loops, filters, metrics use case, and audio controller.
  - caches latest external/replay airspeed sample from `AirspeedDataSource`.

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
  - airspeed selection priority is now:
    `EXTERNAL/REPLAY (fresh+valid) -> stabilized WIND/GPS decision -> GPS_GROUND fallback`.
  - Live WIND vs GPS choice is stabilized by domain policy/controller:
    confidence hysteresis (enter/exit), minimum dwell, and transient grace.
  - WIND transient-grace hold is only allowed while a current wind candidate still exists;
    missing current wind candidate forces immediate fallback to GPS.
  - `tasValid`, source label, and TE gating are all derived from the same stabilized source output.
  - Source-transition telemetry includes decisions + transitions, with grace/dwell block events
    counted once per contiguous episode (not once per frame).
  - Replay requests explicitly disable online terrain fetch updates to keep replay deterministic
    and avoid network-driven memory pressure.
  - Metrics execution is synchronized to serialize stateful domain windows/decisions across live emit paths.
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
  - forwards cached external/replay airspeed sample into `FlightMetricsRequest`.

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
    - TE compensation enabled flag

Flight-state detector feed:
- `feature/map/src/main/java/com/example/xcpro/sensors/FlightStateRepository.kt`
  - Live mode consumes stabilized airspeed fields from `CompleteFlightData` (`trueAirspeed`, `airspeedSource`, `tasValid`).
  - "Real airspeed" classification is fail-safe and trust-list based:
    only `WIND` and `SENSOR` source labels are treated as non-GPS real-airspeed inputs.

## 4) Use Case -> ViewModel

Use case:
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenUseCases.kt`
  - `FlightDataUseCase` exposes `FlightDataRepository.flightData`.

ViewModel:
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt`
  - `mapLocation` = `flightData.gps` (SSOT for map location).
  - Instantiates `FlightDataUiAdapter` (wraps `MapScreenObservers`) for data fan-out.
  - Binds live thermalling runtime automation through
    `bindThermallingRuntimeWiring(...)`:
    `flightData.isCircling` + thermalling settings repository flow +
    thermal-mode visibility -> `ThermallingModeCoordinator` ->
    existing `setFlightMode(...)` and map zoom target actions.

## 5) ViewModel -> UI (Map + Cards)

Observers:
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenObservers.kt` (wrapped by `FlightDataUiAdapter`)
  - Combines flight data + wind + flying state.
  - Projects replay session state through a semantic selection-presence gate
    (`mapReplaySelectionActive()` -> `distinctUntilChanged`) before joining
    the main observer combine path.
  - Converts `CompleteFlightData` to `RealTimeFlightData`.
  - Pushes to `FlightDataManager` and trail processor.
  - Gates trail processing by trail settings (`TrailLength.OFF` resets trail processor and clears trail updates).

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
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRootHelpers.kt`
  - Hydrates and persists draggable map widget offsets via `MapWidgetLayoutViewModel`
    (`SIDE_HAMBURGER`, `FLIGHT_MODE`, `SETTINGS_SHORTCUT`, `BALLAST`).
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapOverlayStack.kt`
  - Renders `SettingsShortcut` with existing widget gesture registry; drag is edit-mode
    only, tap delegates to `MapModalManager.showGeneralSettingsModal()` through
    scaffold callback wiring.
- `feature/map/src/main/java/com/example/xcpro/navdrawer/DrawerMenuSections.kt`
  - Map drawer `Settings -> General` uses the same map-owned General modal callback
    when provided by `NavigationDrawer`; route navigation remains as compatibility fallback.

OGN settings path:
- `feature/map/src/main/java/com/example/xcpro/ogn/OgnTrafficPreferencesRepository.kt`
  - SSOT for OGN overlay enabled + icon size + receive radius (`20..300 km`, default `150`) + advanced auto-radius toggle + display update mode (`real_time` / `balanced` / `battery`) + `showSciaEnabled` +
    `showThermalsEnabled` + `thermalRetentionHours` + `hotspotsDisplayPercent` + ownship IDs
    (`ownFlarmHex`, `ownIcaoHex`) preferences.
- `feature/map/src/main/java/com/example/xcpro/ogn/OgnTrailSelectionPreferencesRepository.kt`
  - SSOT for selected OGN aircraft keys used by trail display filtering.
- `app/src/main/java/com/example/xcpro/XCProApplication.kt`
  - On fresh app process start, resets SCIA startup state to disabled (`showSciaEnabled = false`, selected trail aircraft = empty) so SCIA choices are session-local and must be re-enabled by user after restart.
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenUseCases.kt`
  - `OgnTrafficUseCase` combines trail segments with selected OGN aircraft keys
    from trail-selection SSOT before ViewModel/UI collection.
- `feature/map/src/main/java/com/example/xcpro/ogn/OgnTrailSelectionViewModel.kt`
  - Observes OGN suppressed-key stream and prunes suppressed keys from persisted trail selections.
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenUseCases.kt`
  - `OgnTrafficUseCase` exposes OGN settings, thermal-hotspot flows, and OGN glider trail segment flows.
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt`
  - Converts OGN settings, thermal-hotspot state, and OGN trail state to lifecycle-aware UI/runtime state.
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRoot.kt`
  - Pushes OGN overlay targets, thermal hotspots, and OGN trail segments into runtime overlay manager.
- `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManager.kt`
  - Applies icon size for OGN traffic overlays and owns OGN thermal + OGN glider-trail overlay runtime lifecycle.
  - Applies display-update mode (`real_time` / `balanced` / `battery`) as map-render throttling only for OGN traffic/thermal/trail overlays (ingest is unchanged).
  - Owns OGN/ADS-b traffic overlay creation on startup/style recreation (MapInitializer delegates traffic overlay construction to this runtime owner).
- `feature/map/src/main/java/com/example/xcpro/map/OgnTrafficOverlay.kt`
  - Updates SymbolLayer `iconSize` dynamically from configured pixel size.
- `feature/map/src/main/java/com/example/xcpro/ogn/OgnThermalRepository.kt`
  - Derives thermal hotspots from OGN targets and applies retention policy from
    `thermalRetentionHours` (`1h..23h` rolling window, `all day` until local midnight).
  - Applies strongest-first display share filtering from `hotspotsDisplayPercent` (`5..100`).
  - Applies thermal metrics only on fresh OGN samples per target (`lastSeenMillis` monotonic freshness gate).
  - Rejects fake climbs by requiring cumulative turn `>730` degrees before thermal confirmation.
  - Publishes one best hotspot per local area radius (best-climb winner) to reduce crowded duplicates.
  - Prunes freshness-cache entries for absent targets after timeout to avoid unbounded session growth while preserving stale-present target protection.
  - Runs repository-side housekeeping timers so thermal continuity/missing finalization occurs even when upstream target lists are quiet.
  - Consumes repository suppression keys and purges ownship-derived trackers/hotspots in-session.
- `feature/map/src/main/java/com/example/xcpro/map/OgnThermalOverlay.kt`
  - Renders color-coded thermal hotspots using snail-trail climb palette indexing.
- `feature/map/src/main/java/com/example/xcpro/ogn/OgnGliderTrailRepository.kt`
  - Derives per-glider OGN trail segments from fresh OGN target samples.
  - Owns sink/climb style mapping (color index + asymmetric width) and bounded in-memory retention.
  - Uses injected monotonic clock for deterministic retention housekeeping.
  - Consumes repository suppression keys and purges ownship-derived trail samples/segments in-session.
- `feature/map/src/main/java/com/example/xcpro/map/OgnGliderTrailOverlay.kt`
  - Renders line segments using precomputed OGN trail style properties from repository output.
  - Applies a map-side safety render cap (newest 12,000 segments) to bound allocation pressure.

OGN lifecycle/position semantics:
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenTrafficCoordinator.kt`
  - Streaming enable is driven by `allowSensorStart && mapVisible && ognOverlayEnabled`.
  - Query center updates are GPS-driven from `mapLocation` (user position), not camera center.
  - Pushes auto-radius context (`currentZoom`, ownship speed, ownship flying-state) into OGN traffic use-case.
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapOverlayStack.kt`
  - Tap routing resolves traffic markers before wind callouts.
  - OGN marker taps are resolved before thermal and ADS-b taps.
  - Thermal hotspot taps are resolved before ADS-b taps.
  - Thermal hotspot taps route to thermal details selection when `showThermalsEnabled` is on.
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt`
  - Owns selected OGN/thermal/ADS-b selection state and enforces mutual exclusion across these details sheets.
- `feature/map/src/main/java/com/example/xcpro/ogn/OgnTrafficRepository.kt`
  - Uses APRS radius filtering and client-side haversine filtering with user-configurable
    radius (`20..300 km`, default `150`).
  - Optional auto-radius mode computes effective radius from flight context first and zoom second
    using coarse buckets (`40/80/150/220`) with dwell/cooldown gating before apply.
  - Applies ownship suppression by typed transport identity before publishing targets
    (`FLARM:HEX` / `ICAO:HEX` match only).
  - Exposes suppression diagnostics as canonical key set in snapshot (`suppressedTargetIds`).
  - Uses canonical typed keys internally for target cache identity and collision-safe selection paths.
  - Client-side filtering is evaluated against latest requested GPS center so the
    configured radius policy stays user-centered between reconnects.
  - Socket subscription reconnects when requested center moves >= 20 km from
    active subscription center or when receive radius changes.
  - Connection state remains `CONNECTING` until server `logresp verified` or
    first valid traffic frame.
  - If no center is available yet, repository waits before opening the stream.
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRoot.kt`
  - OGN traffic overlay renders `emptyList()` when overlay preference is disabled.
  - Thermal overlay renders `emptyList()` unless `ognOverlayEnabled && showThermalsEnabled`.
  - OGN glider-trail overlay renders `emptyList()` unless
    `ognOverlayEnabled && showSciaEnabled`; per-aircraft filtering is applied
    from selected OGN aircraft keys.
- `feature/map/src/main/java/com/example/xcpro/ogn/OgnMarkerDetailsSheet.kt`
  - Renders selected OGN target details in a `ModalBottomSheet`.
- `feature/map/src/main/java/com/example/xcpro/ogn/OgnThermalDetailsSheet.kt`
  - Renders selected thermal hotspot details in a partially-expandable `ModalBottomSheet`.
- `feature/map/src/main/java/com/example/xcpro/ogn/OgnThermalDetailsSheet.kt`
  - Renders selected thermal details in a half-sheet style `ModalBottomSheet`.

ADS-b settings path:
- `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficPreferencesRepository.kt`
  - SSOT for ADS-b overlay enabled + icon size + max distance + vertical above/below preferences.
  - SSOT for ADS-B EMERGENCY audio policy preferences (`emergencyAudioEnabled`, `emergencyAudioCooldownMs`).
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenUseCases.kt`
  - `AdsbTrafficUseCase` exposes ADS-b settings flows (`iconSizePx`, `maxDistanceKm`, `verticalAboveMeters`, `verticalBelowMeters`).
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt`
  - Converts ADS-b settings flows and ownship altitude into lifecycle-aware state for UI/runtime wiring.
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRoot.kt`
  - Pushes icon-size changes into overlay runtime controller.
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRootEffects.kt`
  - Applies visual-only ownship-altitude quantization (`2 m` default) before
    OGN/ADS-b runtime overlay updates to reduce high-frequency side-effect churn
    without mutating repository SSOT values.
- `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManager.kt`
  - Applies and re-applies icon size for existing and recreated overlays.
  - Is the single runtime owner for ADS-b/OGN traffic overlay recreation so persisted icon size is applied consistently on cold start.
- `feature/map/src/main/java/com/example/xcpro/map/AdsbTrafficOverlay.kt`
  - Updates SymbolLayer `iconSize` dynamically from configured pixel size.

ADS-b lifecycle/visibility semantics:
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt`
  - Map overlay targets come from the raw ADS-b repository stream with metadata merged opportunistically.
  - Map marker positions are not gated by metadata-enrichment latency.
  - Selected ADS-b details are sourced from raw ADS-b targets plus metadata; distance/bearing remain ownship-relative.
  - OGN traffic streams do not influence ADS-b details distance/bearing or ADS-b proximity color tiers.
- `feature/map/src/main/java/com/example/xcpro/adsb/metadata/data/AircraftMetadataRepositoryImpl.kt`
  - On-demand ICAO metadata upserts emit a metadata revision signal.
- `feature/map/src/main/java/com/example/xcpro/adsb/metadata/domain/AdsbMetadataEnrichmentUseCase.kt`
  - Target/icon enrichment and selected-target details recompute when metadata revision changes,
    so icon/category overrides refresh immediately after metadata persistence (no extra poll wait).
- `feature/map/src/main/java/com/example/xcpro/di/AdsbNetworkModule.kt`
  - ADS-B live polling HTTP client and ADS-B metadata HTTP client are split:
    polling stays low-latency cadence-focused, metadata uses longer download-safe timeouts.
- `feature/map/src/main/java/com/example/xcpro/adsb/metadata/data/OpenSkyMetadataClient.kt`
  - Metadata CSV downloads are staged to a temp file first; importer callback runs after HTTP response closure
    to reduce socket-timeout pressure during long Room import work.
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenTrafficCoordinator.kt`
  - Streaming enable is driven by `allowSensorStart && mapVisible && adsbOverlayEnabled`.
  - When streaming turns on, center is seeded from current GPS position (camera fallback when GPS is unavailable).
  - Query-center and ownship-origin updates are GPS-driven from `mapLocation`; ownship origin is cleared when GPS becomes unavailable.
  - Ownship motion (`bearingDeg`/`speedMs`) is forwarded to ADS-B runtime so emergency geometry can use projected closest-approach checks.
  - Ownship motion forwarding is confidence-aware:
    - poor speed-accuracy suppresses motion input,
    - low-speed fixes keep speed but suppress heading track.
  - Ownship altitude and ADS-b filter settings flows are forwarded to the ADS-b repository runtime.
  - Explicit ADS-b FAB off triggers immediate repository target clear.
- `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficRepository.kt`
  - Disabling streaming pauses polling without clearing last-known targets.
  - Polling is connectivity-aware via `AdsbNetworkAvailabilityPort`; retries pause while offline and resume on network restoration.
  - While waiting offline, repository housekeeping ticks keep stale/expiry progression active (targets dim/expire on monotonic time even without new fetches).
  - Poll retry + circuit-breaker state transitions are owned by `AdsbPollingHealthPolicy`.
  - Explicit clear path removes cached targets and resets displayed list.
  - Query center is used for fetch/radius filtering (configurable `1..100 km`, default `10 km`).
  - Ownship origin is used for displayed distance/bearing when available.
  - Ownship reference is freshness-gated in runtime (stale ownship falls back to query-center reference).
  - Ownship reference sample time is forwarded into trend selection so closing/post-pass state can update on ownship movement between provider packets.
  - Ownship altitude is used for vertical above/below filtering with fail-open when altitude is unavailable.
  - Ownship motion vectors (track/speed) are used for projected CPA/TCPA emergency gating when available; explicit low-motion context disables geometry emergency escalation.
  - Evaluates EMERGENCY-only audio policy FSM in repository SSOT path (feature-flag + setting-gated),
    including cooldown anti-nuisance telemetry publication in `AdsbTrafficSnapshot`.
  - EMERGENCY audio rollout master/shadow gates are sourced from ADS-B preferences SSOT via rollout port wiring.
  - Emits one-shot EMERGENCY alert side effects only on FSM trigger transitions through
    `AdsbEmergencyAudioOutputPort` (master-flag gated; shadow mode never plays audio).
- `feature/map/src/main/java/com/example/xcpro/adsb/data/AndroidAdsbNetworkAvailabilityAdapter.kt`
  - Android connectivity callback adapter bound to the ADS-B network-availability port.
  - Callback events are normalized by `AdsbNetworkAvailabilityTracker` (including fail-open registration fallback).
- `feature/map/src/main/java/com/example/xcpro/map/AdsbTrafficOverlay.kt`
  - Per-aircraft runtime interpolation smooths marker motion between provider samples.
  - Proximity color expression consumes repository-authored `proximity_tier` (tier mapping only in map layer).
  - Tier policy is store-side and trend-aware: post-pass de-escalation uses fresh-sample gating (`red -> amber -> green`, `amber -> green`) only after closing history.
  - Interpolation is visual-only and does not mutate repository SSOT.
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRoot.kt`
  - ADS-b overlay renders `emptyList()` when overlay preference is disabled.

Forecast overlay (SkySight-backed) path:
- `feature/map/src/main/java/com/example/xcpro/forecast/ForecastPreferencesRepository.kt`
  - SSOT for forecast overlay preferences (`enabled`, `opacity`, `autoTimeEnabled`, `selectedPrimaryParameterId`, `selectedTimeUtcMs`, `selectedRegion`).
  - Wind-overlay prefs are separate (`windOverlayEnabled`, `selectedWindParameterId`, `windOverlayScale`, `windDisplayMode`).
  - SkySight satellite prefs are SSOT-managed (`skySightSatelliteOverlayEnabled`, imagery/radar/lightning toggles, animation, history-frame count).
- `feature/map/src/main/java/com/example/xcpro/di/ForecastModule.kt`
  - Binds forecast ports to `SkySightForecastProviderAdapter` in production runtime.
- `feature/map/src/main/java/com/example/xcpro/forecast/SkySightForecastProviderAdapter.kt`
  - Adapter for SkySight catalog/tile/legend/value contracts.
  - Resolves region-aware time slots, per-parameter tile URL formats, source-layer candidates, and point fields.
- `feature/map/src/main/java/com/example/xcpro/forecast/FakeForecastProviderAdapter.kt`
  - Test utility adapter for unit tests and local contract validation only.
- `feature/map/src/main/java/com/example/xcpro/forecast/ForecastOverlayRepository.kt`
  - Composes prefs + provider ports into overlay-ready state and point-query results.
  - Emits one active non-wind layer state plus optional wind-layer state.
  - Satellite-only time-reference contract:
    - when forecast/wind overlays are off and SkySight satellite overlay is on,
      `selectedTimeUtcMs` is sourced from current wall time (injected clock),
      without forcing forecast catalog/time-slot resolution.
    - satellite renderer applies two-sided freshness clamp (near-live upper bound and history-window lower bound).
  - Maintains last-good tile/legend with fatal-vs-warning error separation
    (non-wind and wind tile hard-failures are fatal when no renderable tile is available).
- `feature/map/src/main/java/com/example/xcpro/forecast/ForecastOverlayViewModel.kt`
  - ViewModel-intent boundary for enable/time/opacity and long-press point query.
  - SkySight-tab non-wind parameter selection is single-select (primary only).
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContentRuntime.kt`
  - Collects forecast and weather state, composes SkySight warning/error channels, and renders point-query callout/status.
  - Applies RainViewer-vs-SkySight dual-rain arbitration: when RainViewer is enabled and SkySight non-wind parameter is `accrain`,
    SkySight primary rain rendering is suppressed while wind overlay remains independent.
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContentRuntimeEffects.kt`
  - Forwards forecast/wind/satellite runtime intent (`enabled`, tile/legend specs, display mode, animation, frame count, selected time)
    to map overlay runtime owner with loading-vs-clear transition policy.
- `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManagerRuntimeForecastWeatherDelegate.kt`
  - Runtime owner for forecast raster overlay lifecycle and style-reload reapplication.
  - Hosts forecast runtime overlay instances for one non-wind layer and optional wind layer.
  - Owns SkySight satellite runtime config and style-reload reapply path.
  - Forecast apply/reapply paths are failure-isolated and surfaced via runtime warning channel.
- `feature/map/src/main/java/com/example/xcpro/map/ForecastRasterOverlay.kt`
  - MapLibre runtime controller for forecast vector layers.
  - Uses namespace-scoped layer/source IDs so multiple forecast overlays can render together.
  - Supports indexed-fill overlays and wind-point overlays with branch-specific layer cleanup.
  - Wind-point rendering supports `ARROW` and `BARB` display modes from forecast preferences SSOT.
- `feature/map/src/main/java/com/example/xcpro/map/SkySightSatelliteOverlay.kt`
  - MapLibre runtime controller for SkySight satellite imagery/radar/lightning layers.
  - Uses `satellite.skysight.io` tile templates with `date=YYYY/MM/DD/HHmm` contract and bounded history-frame loop (1-6, 10-minute steps).

Weather rain overlay path:
- `feature/map/src/main/java/com/example/xcpro/weather/rain/WeatherOverlayPreferencesRepository.kt`
  - SSOT for weather rain overlay preferences (`enabled`, `opacity`, animation toggle, animation window, animation speed, transition quality, frame mode, render options).
- `feature/map/src/main/java/com/example/xcpro/weather/rain/WeatherRadarMetadataRepository.kt`
  - RainViewer metadata fetch + parse (`weather-maps.json`) with runtime status/fallback handling.
- `feature/map/src/main/java/com/example/xcpro/weather/rain/ObserveWeatherOverlayStateUseCase.kt`
  - Combines preferences + metadata into frame-based runtime state (`selectedFrame`, status, effective transition duration).
- `feature/map/src/main/java/com/example/xcpro/weather/rain/WeatherOverlayViewModel.kt`
  - Map-side weather overlay state for runtime binding.
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapBottomSheetTabs.kt`
  - Map bottom tab host includes `RainViewer` as an in-map tab in the same `ModalBottomSheet` host used by SkySight/Scia/Map4.
  - Rain tab content reuses the shared weather settings content path; map remains visible behind the sheet.
- `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/WeatherSettingsScreenRuntime.kt`
  - Drawer weather route remains available and hosts the same shared weather controls content for compatibility entry.
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapWeatherOverlayEffects.kt`
  - Collects weather overlay state and forwards frame-based runtime updates (including transition duration) to overlay manager.
  - RainViewer enable state participates in SkySight non-wind rain arbitration through map runtime composition.
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
    - `feature/map/src/main/java/com/example/xcpro/map/ui/MapOverlayStack.kt` (AAT edit + gesture commit mutations)
- Coordinator owns the only runtime call path to:
  - `TaskMapRenderRouter.syncTaskVisuals(...)`
  - This API owns clear + orphan cleanup + conditional replot sequencing.
- AAT drag split (2026-03-05):
  - gesture move path publishes preview-only target updates through
    `TaskRenderSyncCoordinator.previewAatTargetPoint(...)` ->
    `TaskMapRenderRouter.previewAatTargetPoint(...)` ->
    `AATTaskRenderer.previewTargetPointAndTaskLine(...)` upsert-only target-pin + task-line updates
    (no full task clear/replot per move).
  - gesture end commits canonical target mutation and triggers one full
    `TaskMapRenderRouter.syncTaskVisuals(...)` pass.
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
  - `MapOverlayManager` (traffic overlay creation owner for OGN/ADS-b on startup and style transitions)
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
- `feature/variometer/src/main/java/com/example/xcpro/audio/VarioAudioController.kt`
  - Selects TE vario if valid, otherwise raw vario.
  - Silences audio when data is stale.

Engine:
- `feature/variometer/src/main/java/com/example/xcpro/audio/VarioAudioEngine.kt`
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
  - Derives `FlyingState` from GPS/baro/airspeed and SSOT AGL from `FlightDataRepository.flightData`.
  - Does not perform independent terrain-network fetches; AGL authority remains in fusion output.
  - Used by metrics and map observers.

## 8) Replay Pipeline (High-Level)

Replay sensors:
- `feature/map/src/main/java/com/example/xcpro/replay/ReplaySensorSource.kt`
  - `SensorDataSource` implementation for replay samples.
- `feature/map/src/main/java/com/example/xcpro/replay/ReplaySampleEmitter.kt`
  - emits replay airspeed samples (IAS/TAS) into `ReplayAirspeedRepository`.
  - when only IAS or only TAS is present in IGC extensions, reconstructs the missing
    component using altitude + QNH-aware density ratio.

Replay pipeline:
- `feature/map/src/main/java/com/example/xcpro/replay/ReplayPipeline.kt`
  - Creates a replay `SensorFusionRepository` (isReplayMode = true).
  - Forwards fused `CompleteFlightData` into `FlightDataRepository` with Source.REPLAY.
  - Observes `LevoVarioPreferencesRepository` and pushes replay audio settings and TE compensation enabled flag.
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
- `feature/variometer/src/main/java/com/example/xcpro/audio/VarioAudioController.kt`
- `feature/variometer/src/main/java/com/example/xcpro/audio/VarioAudioEngine.kt`

Replay:
- `feature/map/src/main/java/com/example/xcpro/replay/IgcReplayController.kt`
- `feature/map/src/main/java/com/example/xcpro/replay/ReplayPipeline.kt`

