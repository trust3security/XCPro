
# PIPELINE.md

Purpose: document the end-to-end live data pipeline (sensors -> fusion -> SSOT -> UI + audio),
plus how replay and parallel pipelines attach. Update this file whenever the wiring changes.

Diagram: `PIPELINE.svg`.

## Startup Profile Bootstrap (2026-04-03)

- `app/src/main/java/com/example/xcpro/MainActivityScreen.kt`
  - gates normal app entry on profile hydration and active-profile availability.
  - routes to `ProfileSelectionScreen` whenever no active profile exists yet.
- `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileRepositoryHydrationCoordinator.kt`
  and `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileRepositoryBootstrapHelpers.kt`
  - hydrate stored profile state and legacy aliases.
  - restored private profile state from Android backup/device transfer is treated
    the same as any other valid stored profile state.
  - clean valid storage now remains empty instead of silently provisioning a
    default profile.
  - parse/read/bootstrap errors keep the explicit recovery path instead of
    reusing clean-install first-launch setup.
- `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileViewModel.kt`
  - derives `isFirstLaunchSetupRequired` from hydrated + empty profiles + no
    bootstrap error.
  - first-launch is therefore defined by hydrated state, not by APK reinstall.
- `feature/profile/src/main/java/com/example/xcpro/profiles/ui/ProfileSelectionContent.kt`
  and `feature/profile/src/main/java/com/example/xcpro/profiles/ui/ProfileFirstLaunchSetupCard.kt`
  - own the first-launch aircraft-type picker UI and keep `Load Profile File`
    available on clean install.
- `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileRepository.kt`
  and `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileRepositoryMutationCoordinator.kt`
  - own canonical first-launch completion through `completeFirstLaunch(...)`.
  - create `default-profile` named `Default` using the selected aircraft type
    and set it active atomically.
  - runtime collection and backup-sync coordination use the DI-owned
    `@ProfileRepositoryScope` from
    `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileRepositoryRuntimeModule.kt`
    rather than a constructor-created fallback scope.
  - delay managed backup sync until at least one real profile exists and an
    active profile ID is present, so pristine startup does not emit empty public
    backup artifacts.
  - app-private DataStore remains the authoritative startup profile source;
    public files under `Download/XCPro/profiles/` are export/debug artifacts only.

## Automated Quality Gates

These artifacts block architecture/timebase regressions:
- Aggregate Gradle gate: `./gradlew enforceRules` (includes `archGate`)
- Fast architecture gate: `./gradlew enforceArchitectureFast`
- Local gate script: `scripts/arch_gate.py`
- CI workflow: `.github/workflows/quality-gates.yml`
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
  -> FlightDataCalculatorEngine (fusion + metrics + audio-port wiring)
  -> SensorFusionRepository.flightDataFlow
  -> VarioServiceManager
  -> FlightDataRepository (SSOT, Source gating)
    -> LiveOwnshipSnapshotSource
    -> LiveFollowPilotUseCase
       -> LiveFollowSessionRepository
       -> LiveFollowPilotViewModel
       -> LiveFollowPilotScreen
       -> Flying-mode map status indicator
    -> LiveFollowWatchUseCase + WatchTrafficRepository
       -> LiveFollowWatchViewModel
       -> `livefollow/watch/{sessionId}` route validation + join
       -> handoff to `map`
       -> MapLiveFollowRuntimeLayer (render-only watched glider + watched task overlay)
  -> FlightDataUseCase
  -> MapScreenViewModel
     -> FlightDataUiAdapter (MapScreenObservers)
        + GlideComputationRepository.glide
        + WaypointNavigationRepository.waypointNavigation
        + PilotCurrentLdRepository.pilotCurrentLd
        + TaskPerformanceRepository.taskPerformance
        -> convertToRealTimeFlightData
        -> FlightDataManager
     -> mapLocation (GPS) for map UI
  -> UI overlays + dfcards FlightDataViewModel (cards)

Audio taps the pipeline inside FlightDataCalculatorEngine:
  FlightDataCalculatorEngine
    -> VarioAudioControllerPort (from `feature:flight-runtime`)
    -> VarioAudioController (variometer implementation)
    -> VarioAudioEngine

## 1) Sensor Ingestion (Live)

Entry point:
- `app/src/main/java/com/example/xcpro/service/VarioForegroundService.kt`
  - owns the foreground-service runtime scope for live sensor collection.
  - handles repeated `ACTION_ENSURE_RUNNING` start commands and passes the
    service-owned scope into `VarioServiceManager.start(...)`.
- `feature/map/src/main/java/com/example/xcpro/vario/VarioServiceManager.kt`
  - orchestrates sensors, fusion repo, and repository updates.
  - launches its long-lived collectors in the caller-owned service scope while
    keeping execution on the injected default dispatcher.
  - Production wiring is explicit through DI; mandatory IGC and WeGlide
    collaborators are not installed through silent constructor defaults.
- `feature/map/src/main/java/com/example/xcpro/map/VarioRuntimeControlPort.kt`
  - app-independent runtime control seam used by map and replay code.
  - implemented in `app` by the foreground-service runtime controller.

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
- `feature/flight-runtime/src/main/java/com/example/xcpro/sensors/SensorFusionRepositoryFactory.kt`
  - builds a `SensorFusionRepository` using a `SensorDataSource`.
  - selects source-aware airspeed feed (`@LiveSource` vs `@ReplaySource` `AirspeedDataSource`)
    and injects it into the fusion engine.
  - injects the shared runtime ports:
    - `StillAirSinkProvider`
    - `VarioAudioControllerFactory`
    - `HawkAudioVarioReadPort`
    - `ExternalInstrumentReadPort`
    - `TerrainElevationReadPort` from `:core:flight`
- `feature/flight-runtime/src/main/java/com/example/xcpro/sensors/FlightDataCalculator.kt`
  - thin wrapper around `FlightDataCalculatorEngine`.
- `feature/flight-runtime/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngine.kt`
  - owns fusion loops, filters, metrics use case, and the shared audio port.
  - caches latest external/replay airspeed sample from `AirspeedDataSource`.
  - caches the latest narrow external-instrument snapshot from
    `ExternalInstrumentReadPort`.
  - consumes HAWK audio vario samples through `HawkAudioVarioReadPort` rather
    than depending on the full HAWK repository.
  - keeps external-vs-phone arbitration in `feature:flight-runtime`; replay
    bypasses live external Bluetooth truth.
  - constructs `SimpleAglCalculator` with the injected shared `TerrainElevationReadPort`;
    the calculator no longer constructs terrain adapters directly.
- `feature/map/src/main/java/com/example/xcpro/terrain/TerrainElevationRepository.kt`
  - canonical live terrain implementation bound to `TerrainElevationReadPort`.
  - owns terrain source policy (`SRTM` offline-first, `Open-Meteo` fallback),
    cache lifecycle, retry/backoff, and rate-limited terrain diagnostics.
  - the shared read contract now lives in `:core:flight`, not in
    `dfcards-library`.
- `feature/map/src/main/java/com/example/xcpro/terrain/SrtmTerrainDataSource.kt`
  and `feature/map/src/main/java/com/example/xcpro/terrain/OpenMeteoTerrainDataSource.kt`
  - focused terrain source adapters used only by the repository.
  - `SrtmTerrainDatabase` and `OpenMeteoElevationApi` stay below this adapter
    seam and no longer define the cross-feature terrain contract.

Loops (two decoupled loops):
- `feature/flight-runtime/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngineLoops.kt`
  - High-rate baro + accel loop (50 Hz target):
    - `updateVarioFilter(baro, accel)`
    - updates vario filters, baro altitude, TE fusion inputs, and audio.
    - emits display frames on baro cadence (throttled).
  - GPS + compass loop (~10 Hz):
    - `updateGPSData(gps, compass)`
    - updates cached GPS, GPS vario, and emits a frame if baro is stale.

Filters and vario:
- `feature/flight-runtime/src/main/java/com/example/xcpro/sensors/FlightDataFilters.kt`
  - pressure Kalman + baro filter.
- `feature/flight-runtime/src/main/java/com/example/xcpro/sensors/VarioSuite.kt`
  - optimized/legacy/raw/gps/complementary vario implementations.

Metrics use case:
- `feature/flight-runtime/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt`
  - TE/netto, display smoothing, circling detection, LD, thermal metrics.
  - Flight-only polar-derived metrics now include:
    - measured glide ratio (`currentLD`)
    - measured through-air glide ratio (`currentLDAir`)
    - theoretical still-air glide ratio at current IAS (`polarLdCurrentSpeed`)
    - best still-air glide ratio from the active polar (`polarBestLd`)
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
  - The terrain replay guard remains outside the terrain read port/repository seam; replay policy
    is still decided in the metrics request path before AGL lookup is attempted.
  - Metrics execution is synchronized to serialize stateful domain windows/decisions across live emit paths.
  - Owns deterministic windows and is testable without Android.

Mapping to SSOT model:
- `feature/flight-runtime/src/main/java/com/example/xcpro/flightdata/FlightDisplayMapper.kt`
  - maps domain metrics + sensors to `CompleteFlightData`.
- `feature/map/src/main/java/com/example/xcpro/sensors/SensorData.kt`
  - defines `CompleteFlightData` (SSOT for calculated flight data).

Emission:
- `feature/flight-runtime/src/main/java/com/example/xcpro/sensors/FlightDataEmitter.kt`
  - builds `FlightDisplaySnapshot`, maps to `CompleteFlightData`,
    and publishes to `flightDataFlow`.
  - forwards cached external/replay airspeed sample into `FlightMetricsRequest`.

## 3) SSOT Repository + Source Gating

QNH terrain calibration:
- `feature/map/src/main/java/com/example/xcpro/qnh/CalibrateQnhUseCase.kt`
  - consumes `TerrainElevationReadPort` directly, using the same canonical terrain repository seam as live AGL.
  - uses terrain elevation plus `estimatedAglMeters` for `AUTO_TERRAIN` calibration, and falls back to GPS altitude when terrain is unavailable.
  - blocks calibration in replay mode before any terrain read so replay determinism remains outside the terrain repository.

SSOT:
- `feature/flight-runtime/src/main/java/com/example/xcpro/flightdata/FlightDataRepository.kt`
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
- `feature/flight-runtime/src/main/java/com/example/xcpro/sensors/FlightStateRepository.kt`
  - Live mode consumes stabilized airspeed fields from `CompleteFlightData` (`trueAirspeed`, `airspeedSource`, `tasValid`).
  - "Real airspeed" classification is fail-safe and trust-list based:
    only `WIND` and `SENSOR` source labels are treated as non-GPS real-airspeed inputs.
  - Live mode keeps the last GPS-backed flight-state input for a short monotonic
    grace window (`20s`) so transient GPS/comms dropouts do not immediately
    collapse `isFlying`/`onGround` back to neutral state; replay remains
    explicit and does not use this grace policy.

- IGC recorder SSOT wiring:
- `feature/map/src/main/java/com/example/xcpro/igc/usecase/IgcRecordingUseCase.kt`
  - Subscribes to `FlightStateSource.flightState`.
  - Subscribes to `FlightDataRepository.flightData` (LIVE source only) and maps samples to B-record lines in `Recording`/`Finalizing` phases.
  - On production startup, inspects persisted `Recording`/`Finalizing` snapshots and invokes `IgcRecoveryBootstrapUseCase` before restoring live session state; `Recording` snapshots resume existing live state, while `Finalizing` snapshots route terminal recovery through `IgcFlightLogRepository`.
  - `IgcRecoveryBootstrapUseCase` publishes typed startup recovery diagnostics through `IgcRecoveryDiagnosticsReporter` so operators can distinguish `resume`, `recovered`, `unsupported`, repository-classified failure, and bootstrap exception paths.
  - Production construction is explicit only: recording sink, recovery bootstrap,
    and flight-log collaborators are required inputs rather than `NoOp`
    constructor fallbacks.
  - Applies Phase 3 cadence/validity/dropout/fallback domain policies before emitting B lines.
  - Exposes `bRecordLines` stream (`SharedFlow<String>`) for diagnostics and regression validation.
  - Forwards each emitted B line to `IgcRecordingActionSink.onBRecord(sessionId, line, sampleWallTimeMs)` using active session state and sample UTC wall timestamp.
  - Enforces `IgcSessionStateMachine` transition contract, persists restart snapshots, and clears staged recovery artifacts after finalize success/failure terminal handling.
- `feature/igc/src/main/java/com/example/xcpro/igc/data/IgcRecordingRuntimeActionSink.kt`
  - Concrete Phase 4 sink bound in DI from `feature:igc`.
  - On `StartRecording`, assembles deterministic preamble (`A/H/I`) and feature-gated task declaration snapshot output.
  - Persists structured recovery metadata by `sessionId` through `IgcRecoveryMetadataStore` at recording start and updates first-fix wall time on the first valid forwarded B-record.
  - `HFDTEDATE` starts with deterministic fallback (`session-start UTC date`, `FF=01`) and is rewritten from first valid B-record sample UTC date when available.
  - Invalid/incomplete declaration snapshots are omitted and surfaced via deterministic diagnostic `L` line (`LXCPDECLARATION_OMITTED:<REASON>`).
  - Appends forwarded B lines and emits deduped `E` records (`FLT`/`TSK`/`SYS`) using monotonic dedupe/rate policy.
  - On `FinalizeRecording`, publishes a finalized `.IGC` file through `IgcFlightLogRepository`; publish success/failure is fed back into session transition completion.
- `feature/igc/src/main/java/com/example/xcpro/igc/data/IgcFlightLogRepository.kt`
  - Coordinates finalize and terminal recovery transactions for published `.IGC` files and keeps the explicit per-session idempotency cache.
  - Delegates staging artifact lifecycle to `IgcRecoveryStagingStore` and MediaStore/legacy final publish side effects to `IgcFlightLogPublishTransport`.
  - Keeps metadata merge, recovery branch ordering, and metadata-gated existing-finalized-file checks in the coordinator.
  - Uses `IgcRecoveryMetadataStore` as primary recovery identity authority; staged `.igc.tmp` header parsing is fallback/validation only for terminal recovery publish.
  - Classifies multiple finalized matches for one session as `DUPLICATE_SESSION_GUARD`.
  - Uses `IgcFileNamingPolicy` to enforce deterministic naming and per-day collision resolution.
- `feature/igc/src/main/java/com/example/xcpro/igc/data/IgcRecoveryStagingStore.kt`
  - Owns app-private staging file writes, reads, existence checks, and delete semantics under `files/igc/staging/`.
- `feature/igc/src/main/java/com/example/xcpro/igc/data/IgcFlightLogPublishTransport.kt`
  - Owns MediaStore Downloads and legacy filesystem publish branches and returns the finalized `IgcLogEntry` metadata back to the repository coordinator.
- `feature/igc/src/main/java/com/example/xcpro/igc/data/IgcRecoveryDownloadsLookup.kt`
  - Owns minimal persisted finalized-entry lookup for recovery and deliberately avoids the UI/file-list scan path.
- `feature/igc/src/main/java/com/example/xcpro/igc/data/IgcRecoveryFinalizedEntryResolver.kt`
  - Owns metadata-based existing-finalized-entry matching and pending-row cleanup for recovery sessions.
- `feature/igc/src/main/java/com/example/xcpro/igc/data/IgcDownloadsRepository.kt`
  - Owns finalized IGC files index (`StateFlow<List<IgcLogEntry>>`) and list/query/copy-out file operations for UI.
- `feature/igc/src/main/java/com/example/xcpro/igc/usecase/IgcFilesUseCase.kt`
  - Implements IGC Files search/sort/share/copy metadata action wiring.
- `feature/igc/src/main/java/com/example/xcpro/igc/ui/IgcFilesViewModel.kt`
  - State/event owner for IGC Files UX; no file I/O in ViewModel.
  - Replay-open action is delegated via injected `IgcReplayLauncher` port, preserving UI -> use-case boundary direction.
- `feature/map/src/main/java/com/example/xcpro/igc/data/IgcMetadataSources.kt`
  - `feature:map` adapter layer for app-owned metadata sources bound into `feature:igc` contracts.
  - Profile metadata source: active profile -> pilot/glider header fields.
  - Task declaration source: task SSOT snapshot at recording start -> C record mapping input.
  - Recorder metadata source: firmware/hardware/sensor-derived header inputs and altitude datum policy defaults.
- `feature/map/src/main/java/com/example/xcpro/vario/VarioServiceManager.kt`
  - Observes `IgcSessionStateMachine.Action`, dispatches lifecycle actions to `IgcRecordingActionSink`, and routes finalize publish success/failure back to `IgcRecordingUseCase` (`onFinalizeSucceeded` / `onFinalizeFailed`).
  - Production construction requires explicit `IgcRecordingUseCase`,
    `IgcRecordingActionSink`, and WeGlide prompt collaborators; disabled or
    test-only behavior must be chosen outside the production constructor path.

## 3A) LiveFollow Pilot + Watch Runtime

Authoritative owners:
- ownship flight truth stays in `FlightDataRepository`; `feature:livefollow` reads it only through `LiveOwnshipSnapshotSource`
- pilot task truth stays in `TaskManagerCoordinator.taskSnapshotFlow`; LiveFollow consumes it only through a narrow exported snapshot boundary
- session lifecycle and watch membership truth stay in `LiveFollowSessionRepository`
- watch source arbitration truth stays in `WatchTrafficRepository`
- map/task rendering stays on the map side; `feature:map` is not the LiveFollow owner

Pilot path:
- `feature/livefollow/src/main/java/com/example/xcpro/livefollow/data/ownship/FlightDataLiveOwnshipSnapshotSource.kt`
  - exports ownship snapshot data from the existing live flight SSOT for LiveFollow, including optional `aglMeters` when the flight SSOT carries a known AGL timestamp.
- `feature/livefollow/src/main/java/com/example/xcpro/livefollow/pilot/LiveFollowPilotUseCase.kt`
  - builds the pilot start request from the exported ownship snapshot plus optional callsign alias input.
- `feature/livefollow/src/main/java/com/example/xcpro/livefollow/data/task/LiveFollowTaskSnapshotSource.kt`
  and `feature/map/src/main/java/com/example/xcpro/livefollow/data/task/TaskCoordinatorLiveFollowTaskSnapshotSource.kt`
  - define the narrow LiveFollow task-export seam and map-owned adapter from `TaskManagerCoordinator.taskSnapshotFlow`.
  - export geometry-only watched-task inputs for the transport path and filter non-geometry churn so active-leg-only changes do not trigger task re-upload.
- `feature/livefollow/src/main/java/com/example/xcpro/livefollow/data/session/LiveFollowSessionRepository.kt`
  - remains the local session truth owner, carries explicit session transport availability from the session gateway boundary, enforces replay-safe or transport-unavailable command blocking before any gateway side effect, and uploads eligible ownship snapshots only while the active pilot session is live and side effects are allowed.
  - uploads the exported pilot task snapshot on session start and when task geometry changes; if the exported task becomes `null` while the pilot session is active, it routes an explicit clear signal through the task transport path.
  - task geometry is not attached to every position tick.
- `feature/livefollow/src/main/java/com/example/xcpro/livefollow/data/session/CurrentApiLiveFollowSessionGateway.kt`
  - current deployed-API transport adapter for `POST /api/v1/session/start`, `POST /api/v1/position`, `POST /api/v1/task/upsert`, and `POST /api/v1/session/end`.
  - stores `write_token` transport-locally and surfaces `session_id` plus `share_code` into the local session truth; `write_token` stays out of UI-facing state.
  - maps upload wire time from ownship `fixWallMs`, carries optional wire `agl_meters`, emits explicit task-clear payloads (`clear_task = true`) when no task is shared, dedupes unchanged task upsert/clear payloads transport-locally, keeps monotonic ordering client-local, and blocks replay-side writes.
- `feature/livefollow/src/main/java/com/example/xcpro/livefollow/data/session/UnavailableLiveFollowSessionGateway.kt`
  - retained explicit transport-limited unavailable adapter for environments where the backend transport is not bound.
  - exports explicit transport-unavailable state plus user-visible failure; it is not a silent fallback and it is not production backend behavior.
- `feature/livefollow/src/main/java/com/example/xcpro/livefollow/pilot/LiveFollowPilotViewModel.kt`
  and `feature/livefollow/src/main/java/com/example/xcpro/livefollow/pilot/LiveFollowPilotScreen.kt`
  - own pilot presentation state and the limited Start sharing / Stop sharing route shell.
- `feature/livefollow/src/main/java/com/example/xcpro/livefollow/pilot/LiveFollowPilotMapStatusHost.kt`
  - renders the compact Flying-mode status light and dropdown actions on the map using `LiveFollowPilotViewModel` state only.

Watch path:
- `app/src/main/java/com/example/xcpro/AppNavGraph.kt`
  and `feature/livefollow/src/main/java/com/example/xcpro/livefollow/LiveFollowRoutes.kt`
  - register `livefollow/pilot`, `livefollow/friends`, `livefollow/watch/{sessionId}`, `livefollow/watch/share`, and `livefollow/watch/share/{shareCode}`.
- `feature/map/src/main/java/com/example/xcpro/navdrawer/DrawerMenuSections.kt`
  - exposes the small app entry point for `Friends Flying`; the drawer is launch-only and does not own list/session/watch state.
- `feature/livefollow/src/main/java/com/example/xcpro/livefollow/data/friends/FriendsFlyingRepository.kt`
  - owns the active-pilot discovery list SSOT for the Friends Flying picker, keeps transport availability / last error client-local, and blocks refresh during replay.
- `feature/livefollow/src/main/java/com/example/xcpro/livefollow/data/friends/CurrentApiActivePilotsDataSource.kt`
  - fetches `GET /api/v1/live/active`, normalizes `share_code` for handoff, and maps the current-API list payload into repo-safe active-pilot summaries.
- `feature/livefollow/src/main/java/com/example/xcpro/livefollow/friends/FriendsFlyingUseCase.kt`
  - thin UI-to-repository seam for refresh orchestration.
- `feature/livefollow/src/main/java/com/example/xcpro/livefollow/friends/FriendsFlyingViewModel.kt`
  and `feature/livefollow/src/main/java/com/example/xcpro/livefollow/friends/FriendsFlyingScreen.kt`
  - own the bottom-sheet list UI, empty/loading/replay-block states, selected-row reflection from shared watch UI state, and item-tap handoff into the shared map-entry watch ViewModel.
- `feature/livefollow/src/main/java/com/example/xcpro/livefollow/watch/LiveFollowWatchEntryRoute.kt`
  - validates `sessionId`, calls `joinWatchSession(sessionId)` once, then hands off to the existing `map` route.
  - does not auto-leave on Composable disposal; leaving remains an explicit user action.
- `feature/livefollow/src/main/java/com/example/xcpro/livefollow/watch/LiveFollowWatchShareCodeScreen.kt`
  - owns the simple share-code watch input shell and routes valid entries to the share-code watch handoff path.
- `feature/livefollow/src/main/java/com/example/xcpro/livefollow/data/watch/WatchTrafficRepository.kt`
  - combines session state, typed OGN traffic candidates, and the direct-watch source behind the existing arbitration/state-machine seams.
  - current deployed-API slice keeps the direct-watch route keyed by the session-owned lookup (`session_id` or `share_code`), carries the watched task snapshot through the same watch SSOT, and does not resolve OGN-backed identity from server payloads.
- `feature/livefollow/src/main/java/com/example/xcpro/livefollow/data/watch/CurrentApiDirectWatchTrafficSource.kt`
  - polls `GET /api/v1/live/{session_id}` for legacy session-id watch routes and `GET /api/v1/live/share/{share_code}` for share-code watch routes, based on the session-owned watch lookup.
  - falls back from `latest` to `positions.last()` when needed, parses optional `agl_meters` into the watched direct sample, parses optional single-pilot task payload into the watched task snapshot, publishes `task = null` when the server returns no active task after a clear, keeps `canonicalIdentity` null, and derives freshness/stale behavior locally from XCPro clocks.
- `feature/livefollow/src/main/java/com/example/xcpro/livefollow/data/watch/UnavailableDirectWatchTrafficSource.kt`
  - retained explicit transport-limited unavailable adapter for the direct-watch feed.
  - exports explicit direct transport-unavailable state and keeps direct-source unavailability visible in watch state instead of simulating live direct traffic.
- `feature/livefollow/src/main/java/com/example/xcpro/livefollow/watch/LiveFollowWatchViewModel.kt`
  - maps session/watch truth plus explicit session/direct transport availability into watch UI state and render state only.
  - owns the currently selected watched-pilot hint during Friends Flying browse/switch flows so the compact top panel, bottom telemetry strip, and list highlight stay aligned until the underlying watch session catches up.
  - exposes optional watched `panelAglLabel` only from the true watched transport path; no UI-side AGL synthesis is allowed.

Map/task render handoff:
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapLiveFollowRuntimeLayer.kt`
  - consumes `LiveFollowWatchViewModel.uiState` as prepared render input only.
  - resolves single watched-aircraft overlay visibility, watched-task overlay visibility, and one-shot focus on the map side only after the selected watch target matches the live session-owned share code.
  - in Flying mode, also hosts the compact pilot status indicator by collecting `LiveFollowPilotViewModel.uiState`
    and routing start/stop commands back through that existing owner; dropdown visibility remains local UI state.
- `feature/map/src/main/java/com/example/xcpro/map/LiveFollowWatchAircraftOverlay.kt`
  - owns the read-only single watched-aircraft MapLibre source/layer runtime for Friends Flying observer mode, including the enlarged watched-glider icon scale while preserving track/heading rotation.
- `feature/map/src/main/java/com/example/xcpro/map/LiveFollowWatchTaskOverlay.kt`
  - owns the read-only single watched-pilot task MapLibre runtime for Friends Flying observer mode and renders turnpoint cylinders plus connecting legs.
- `feature/livefollow/src/main/java/com/example/xcpro/livefollow/watch/LiveFollowWatchEntryRoute.kt`
  - owns the compact watched-pilot top panel and bottom telemetry strip chrome rendered over the shared map host.
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenScaffoldInputs.kt`
  and `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContentRuntime.kt`
  - thread the map runtime access, watched-pilot focus callback, and Flying-mode overlay gate down to the LiveFollow runtime layer without moving task truth into `feature:livefollow`.

Rules:
- LiveFollow does not create a second ownship pipeline.
- Watch mode does not depend on ordinary OGN overlay preference state to stay visible.
- Current deployed-API slice uses start/end/position-upload/task-upsert/live-read HTTP endpoints; no WebSocket, multi-pilot spectator mode, FCM, or notification runtime is wired here.
- Watched task geometry is uploaded rarely from pilot task SSOT: on share start and on task-geometry change only, never on every position tick.
- Friends Flying watched-task rendering is single-pilot and read-only; it does not clone the full pilot map/task shell.

## 4) Use Case -> ViewModel

Use case:
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenUseCases.kt`
  - `FlightDataUseCase` exposes `FlightDataRepository.flightData`.
  - `MapReplayUseCase` now injects `GlideComputationRepository`.
  - It passes `glideComputationRepository.glide` into `FlightDataUiAdapter`.

ViewModel:
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt`
  - `mapLocation` = `flightData.gps` (SSOT for map location).
  - Instantiates `FlightDataUiAdapter` (wraps `MapScreenObservers`) for data fan-out.
  - Binds live thermalling runtime automation through
    `bindThermallingRuntimeWiring(...)`:
    `flightData.isCircling` + thermalling settings repository flow +
    thermal-mode visibility + replay-session state -> `ThermallingModeCoordinator` ->
    existing `setFlightMode(...)`, map zoom target actions, and a transient
    thermalling contrast-map override. Replay suppresses the thermalling runtime
    and clears only the transient thermalling style override.
  - Exposes a separate `feature:weglide`-owned WeGlide prompt flow for UI rendering,
    instead of threading the prompt through `MapUiState`.
- `feature/profile/src/main/java/com/example/xcpro/thermalling/ThermallingModePreferencesRepository.kt`
  - Profile-owned SSOT for thermalling automation settings consumed by both the
    thermalling settings UI and the live thermalling runtime binding in
    `MapScreenViewModel`.
- `feature/profile/src/main/java/com/example/xcpro/screens/navdrawer/ThermallingSettingsScreen.kt`
  - Profile owns the thermalling settings route shell, screen-local ViewModel,
    and shared settings content.
- `feature/profile/src/main/java/com/example/ui1/screens/ThermallingSettingsSubSheet.kt`
  - Profile owns the thermalling General Settings sub-sheet entrypoint used by
    the app-owned General Settings host.
- `feature/profile/src/main/java/com/example/xcpro/glider/GliderRepository.kt`
  - Profile-owned SSOT for glider configuration and profile-scoped glider
    persistence used by both the polar settings surface and live glide runtime.
- `feature/profile/src/main/java/com/example/xcpro/screens/navdrawer/PolarSettingsScreen.kt`
  - Profile owns the polar settings route shell, polar cards, and the
    settings-side `GliderViewModel` / `GliderUseCase`.
- `feature/flight-runtime/src/main/java/com/example/xcpro/glider/StillAirSinkProvider.kt`
  - Shared runtime sink/bounds port used by flight metrics and glide runtime.
  - Phase 4 contract: the port is IAS-based on the active release path.
- `feature/profile/src/main/java/com/example/xcpro/glider/PolarStillAirSinkProvider.kt`
  - Profile-owned implementation of the shared sink port backed by the glider
    repository and polar settings state.
  - Active release math honors the selected model polar or manual 3-point polar,
    plus the current bugs/ballast adjustments only; reference weight and user
    coefficients remain stored-only.
- `feature/profile/src/main/java/com/example/xcpro/screens/navdrawer/Layout.kt`
  - Profile owns the layout settings route shell plus the settings-side
    `LayoutViewModel` / `LayoutPreferencesUseCase` that wrap canonical
    `CardPreferences`.
  - Current scope is DF-card portrait layout only (`cardsAcrossPortrait`,
    `cardsAnchorPortrait`); map widget placement/sizing remains in the separate
    `MapWidgetLayout*` runtime seam.
- `feature/profile/src/main/java/com/example/xcpro/screens/navdrawer/ColorsScreen.kt`
  - Profile owns the colors settings route shell, `ColorsViewModel`, and the
    settings-side `ThemePreferencesUseCase` used for theme writes and custom
    color persistence.
- `feature/profile/src/main/java/com/example/xcpro/screens/navdrawer/HawkVarioSettingsScreenRuntime.kt`
  - Profile owns the HAWK settings route shell plus the
    `HawkVarioSettingsViewModel` / `HawkVarioSettingsUseCase` used for HAWK
    preference writes and live-preview consumption.
- `feature/profile/src/main/java/com/example/xcpro/screens/navdrawer/BluetoothVarioSettingsScreen.kt`
  - Profile owns the Bluetooth settings route shell plus the
    `BluetoothVarioSettingsViewModel` / `BluetoothVarioSettingsUseCase` used
    to map variometer-owned control state into UI and forward explicit
    permission-result, selection, connect, and disconnect intents.
  - The settings screen is a consumer only; it does not own Bluetooth
    permission truth, bonded-device enumeration, selected-device persistence,
    or runtime session lifecycle.
- `feature/variometer/src/main/java/com/example/xcpro/hawk/HawkVarioPreviewReadPort.kt`
  - Variometer owns the read-only HAWK preview contract
    (`HawkVarioPreviewReadPort`, `HawkVarioUiState`, `HawkConfidence`) shared
    by the profile-owned settings surface and the live HAWK runtime owner.
- `feature/variometer/src/main/java/com/example/xcpro/hawk/HawkVarioUseCase.kt`
  - Variometer owns the live HAWK runtime owner plus its repository/config
    stack and implements the variometer-owned `HawkVarioPreviewReadPort`.
- `feature/variometer/src/main/java/com/example/xcpro/variometer/bluetooth/lxnav/runtime/LxExternalRuntimeRepository.kt`
  - Variometer owns Bluetooth transport/parsing consumption plus the long-lived
    LXNAV external runtime snapshot owner.
  - Implements the narrow `ExternalInstrumentReadPort` bridge into
    `feature:flight-runtime`.
  - Only external `pressureAltitudeM` and `totalEnergyVarioMps` participate in
    fused truth in Phase 4; `airspeedKph` and device metadata remain
    variometer-local / diagnostics-only.
- `feature/variometer/src/main/java/com/example/xcpro/variometer/bluetooth/lxnav/control/LxBluetoothControlPort.kt`
  and `LxBluetoothControlUseCase.kt`
  - Variometer owns the Bluetooth settings/control seam over transport
    connection state, permission truth, bonded-device refresh, install-wide
    selected-device persistence, and explicit connect/disconnect delegation into
    `LxExternalRuntimeRepository`.
  - This seam derives UI/control state from the transport and selected-device
    owners; it is not a second Bluetooth session lifecycle source of truth.
- `feature/map/src/main/java/com/example/xcpro/ui/theme/ThemeViewModel.kt`
  - `feature:map` keeps the temporary app theme runtime read path over the
    profile-owned theme use case; the colors owner move does not leave writes
    in `feature:map`.
- `feature/map/src/main/java/com/example/xcpro/hawk/MapHawkRuntimeAdapters.kt`
  - `feature:map` keeps only the temporary HAWK sensor/source adapters that
    feed the variometer-owned runtime until Parent Phase 2B moves those
    upstream owners behind the future flight-runtime boundary.
  - Bluetooth LXNAV transport/parsing/runtime ownership does not land in
    `feature:map`; it crosses into fused truth only through the narrow
    `ExternalInstrumentReadPort` seam.
- `app/src/main/java/com/example/xcpro/appshell/settings/GeneralSettings*.kt`
  - `:app` remains host-only for the General Settings bottom-sheet registry.
  - Bluetooth logic in this phase is limited to surfacing the
    `BluetoothVarioSettingsScreen`; app does not own selected-device
    persistence, bonded-device enumeration, or connect/disconnect behavior.
- `feature/map/src/main/java/com/example/xcpro/map/widgets/MapWidgetLayoutViewModel.kt`
  - `feature:map` keeps the widget drag/resize runtime owner; the layout
    settings route move does not change widget runtime ownership.

## 5) ViewModel -> UI (Map + Cards)

Observers:
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenObservers.kt` (wrapped by `FlightDataUiAdapter`)
  - Combines flight data + wind + flying state + upstream glide solution +
    upstream waypoint navigation snapshot.
  - Projects replay session state through a semantic selection-presence gate
    (`mapReplaySelectionActive()` -> `distinctUntilChanged`) before joining
    the main observer combine path.
  - Consumes `GlideComputationRepository.glide`; it no longer solves glide in
    the observer layer.
  - Consumes `WaypointNavigationRepository.waypointNavigation`; it does not
    compute waypoint route math in the observer layer.
  - Converts `CompleteFlightData` + upstream runtime snapshots to
    `RealTimeFlightData` from `:core:flight`.
  - Pushes to `FlightDataManager` and the `feature:map-runtime` trail
    processor.
  - Threads full trail settings into the trail runtime path so
    `TrailUpdateInput.windDriftEnabled` stays a domain-owned render-policy
    input instead of a UI-local decision.
  - Gates trail processing by trail settings (`TrailLength.OFF` resets trail processor and clears trail updates).

Final glide runtime contract:
- Durable invariants:
  - `feature/flight-runtime/src/main/java/com/example/xcpro/flightdata/FlightDataRepository.kt`
    remains the fused-flight-data SSOT.
  - `CompleteFlightData` stays limited to fused flight/runtime sample fields and
    must not absorb task-route or glide-derived state.
  - `TaskManagerCoordinator.taskSnapshotFlow` plus
    `TaskNavigationController.racingState` remain the authoritative task-runtime
    input seams for racing final glide.
  - `TaskRepository` remains task-sheet/UI projection only and is not a
    cross-feature runtime authority.
  - cards, formatters, and Composables may render glide outputs but must not own
    canonical route derivation or glide math.
- Current mainline implementation on `main` (2026-03-25):
  - `feature/map/src/main/java/com/example/xcpro/glide/GlideTargetRepository.kt`
    currently derives a finish-target snapshot in `feature:map` from
    `TaskManagerCoordinator.taskSnapshotFlow` plus
    `TaskNavigationController.racingState`.
  - `feature/map/src/main/java/com/example/xcpro/glide/FinalGlideUseCase.kt`
    previously solved from fused `CompleteFlightData`, `WindState`, and
    `GlideTargetSnapshot`.
  - `feature/map/src/main/java/com/example/xcpro/map/MapScreenObservers.kt`
    previously invoked `FinalGlideUseCase`, then mapped
    `CompleteFlightData` + `GlideSolution` to `RealTimeFlightData`.
  - Current map glide consumers on `main` still build a waypoint-center-based
    remaining route through `GlideRoutePoint` / `remainingWaypointsFrom(...)`.
- Current local branch implementation (`final-glide-route-runtime-migration`, Phase 4, 2026-03-25):
  - `feature/tasks/src/main/java/com/example/xcpro/tasks/navigation/NavigationRouteRepository.kt`
    is the canonical remaining-route seam and now supplies the route points used
    for glide.
  - `feature/map-runtime/src/main/java/com/example/xcpro/glide/GlideComputationRepository.kt`
    combines `FlightDataRepository.flightData`, `WindSensorFusionRepository.windState`,
    `TaskManagerCoordinator.taskSnapshotFlow`, and `NavigationRouteRepository.route`.
  - `feature/map-runtime/src/main/java/com/example/xcpro/glide/GlideTargetProjector.kt`
    is now the explicit runtime owner of finish-rule and glide-status mapping.
  - `feature/map-runtime/src/main/java/com/example/xcpro/glide/FinalGlideUseCase.kt`
    now owns final-glide math/policy in the non-UI runtime layer and consumes
    canonical task route points directly.
  - `feature/map/src/main/java/com/example/xcpro/map/MapScreenObservers.kt`
    now consumes upstream `GlideSolution` values only.
- Durable post-migration branch boundary (tracked in
  `docs/ARCHITECTURE/ADR_FINAL_GLIDE_RUNTIME_BOUNDARY_2026-03-25.md` and
  `docs/ARCHITECTURE/CHANGE_PLAN_FINAL_GLIDE_ROUTE_AND_RUNTIME_MIGRATION_2026-03-25.md`):
  - `feature:tasks` becomes the canonical owner of remaining racing-task route
    geometry via a `NavigationRouteSnapshot` / projector path that reuses the
    existing boundary planners and task runtime seams.
  - `feature:map-runtime` becomes the non-UI owner of derived final-glide
    computation, reusing existing solver math where practical.
  - `feature:map` remains a consumer/adapter only.
- Replay/live determinism:
  - live glide uses fused live samples from `FlightDataRepository`
  - replay glide uses replay-published fused samples through the same
    repository/source gate; no wall-clock-only or live-only inputs are allowed

Mapping for cards:
- `feature/map/src/main/java/com/example/xcpro/MapScreenUtils.kt`
  - `convertToRealTimeFlightData(...)` maps SSOT to the card-friendly
    `RealTimeFlightData` projection now owned by `:core:flight`.
  - Flight-only polar metrics mapped for cards:
    - `currentLD`
    - `currentLDAir`
    - `polarLdCurrentSpeed`
    - `polarBestLd`
    - `netto`
    - `levoNetto`
    - `speedToFlyIas`
  - Task-finish glide fields now mapped for cards:
    - `navAltitude`
    - `requiredGlideRatio`
    - `arrivalHeightM`
    - `requiredAltitudeM`
    - `arrivalHeightMc0M`
    - `taskFinishDistanceRemainingM`
    - `glideSolutionValid`
    - `glideDegraded`
    - `glideDegradedReason`
    - `glideInvalidReason`
  - Semantics:
    - raw `currentLD/currentLDValid` remain the measured over-ground glide metric
    - raw `currentLDAir/currentLDAirValid` remain the measured through-air glide metric
    - visible `ld_curr` now formats map-runtime fused `pilotCurrentLD/pilotCurrentLDValid`
    - `ld_vario` remains the measured through-air glide card
    - `polar_ld`, `best_ld` remain flight-only theoretical polar cards
    - `final_gld`, `arr_alt`, `req_alt`, `arr_mc0` are racing-task finish cards
    - glide outputs are `VALID`, `DEGRADED` (still-air assumption because no
      usable wind exists), or `INVALID`

Pilot Current L/D join:
- `feature/map-runtime/src/main/java/com/example/xcpro/currentld/PilotCurrentLdRepository.kt`
  - authoritative owner of the fused pilot-facing Current L/D metric.
  - combines `FlightDataRepository.flightData`, `WindSensorFusionRepository.windState`,
    `FlightStateSource.flightState`, `WaypointNavigationRepository.waypointNavigation`,
    and `StillAirSinkProvider`.
  - owns the replay-safe rolling matched window, short-gap polar support,
    zero-wind fallback, and thermal hold policy for:
    - `pilotCurrentLD`
    - `pilotCurrentLDValid`
    - `pilotCurrentLDSource`
- `feature/map/src/main/java/com/example/xcpro/MapScreenUtils.kt`
  - maps the fused snapshot into `RealTimeFlightData`.
- `dfcards-library/src/main/java/com/example/dfcards/CardFormatSpec.kt`
  - formats `ld_curr` from `pilotCurrentLD/pilotCurrentLDValid`.

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
  - Binds `ognIconSizePx` and `adsbIconSizePx` from settings as base runtime overlay size inputs.
  - Does not own viewport zoom; OGN zoom declutter remains runtime-derived from map camera callbacks.
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRootHelpers.kt`
  - Hydrates and persists draggable map widget offsets via `MapWidgetLayoutViewModel`
    (`SIDE_HAMBURGER`, `FLIGHT_MODE`, `SETTINGS_SHORTCUT`, `BALLAST`).
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapOverlayStack.kt`
  - Renders `SettingsShortcut` with existing widget gesture registry; drag is edit-mode
    only, tap delegates to an app-owned General Settings host callback through
    scaffold callback wiring.
- `feature/map/src/main/java/com/example/xcpro/navdrawer/DrawerMenuSections.kt`
  - Map drawer `Settings -> General` uses the same app-owned General Settings host
    callback when provided by `NavigationDrawer`; route navigation remains as a
    compatibility fallback.
- `app/src/main/java/com/example/xcpro/AppNavGraph.kt`
  - Owns the cross-feature General Settings sheet host for the map route.
  - Consumes `OPEN_GENERAL_SETTINGS_ON_MAP` and keeps `SettingsRoutes.GENERAL`
    as a compatibility shim that reopens the app-owned host on `map`.
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContentRuntimeSections.kt`
  - Retains map-owned auxiliary panel orchestration and renders the WeGlide prompt through
    `feature/weglide/src/main/java/com/example/xcpro/weglide/ui/WeGlideUploadPromptDialogHost.kt`.
  - Consumes the dedicated `MapAuxiliaryPanelsInputs` seam from the retained map shell
    rather than a long per-field prompt/QNH argument list.
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenScaffold.kt`
  - Retained drawer/loading shell only; forwards General Settings open intents but
    no longer hosts the General Settings sheet.
- `feature/map/src/main/java/com/example/xcpro/map/MapModalManager.kt`
  - Reduced to airspace-modal ownership only; General Settings is no longer map-modal state.
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenScaffoldContentHost.kt`
  - Small map-owned render seam that binds scaffold inputs plus owner-side prompt callbacks
    into `MapScreenContent` without spreading prompt ABI through the scaffold shell.
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenAuxiliaryPanelsInputs.kt`
  - Dedicated map-shell auxiliary input holder for WeGlide prompt and QNH dialog state/callbacks.
  - Keeps auxiliary-only signature churn localized near `MapScreenContentRuntime` and
    `MapScreenContentRuntimeSections`.

OGN settings path:
- `feature/traffic/src/main/java/com/example/xcpro/ogn/OgnTrafficPreferencesRepository.kt`
  - SSOT for OGN overlay enabled + icon size + receive radius (`20..300 km`, default `150`) + advanced auto-radius toggle + display update mode (`real_time` / `balanced` / `battery`) + `showSciaEnabled` +
    `showThermalsEnabled` + `thermalRetentionHours` + `hotspotsDisplayPercent` + ownship IDs
    (`ownFlarmHex`, `ownIcaoHex`) preferences.
- `feature/traffic/src/main/java/com/example/xcpro/ogn/OgnTrailSelectionPreferencesRepository.kt`
  - SSOT for selected OGN aircraft keys used by trail display filtering.
- `app/src/main/java/com/example/xcpro/XCProApplication.kt`
  - On fresh app process start, resets SCIA startup state to disabled (`showSciaEnabled = false`, selected trail aircraft = empty) so SCIA choices are session-local and must be re-enabled by user after restart.
- `feature/traffic/src/main/java/com/example/xcpro/map/TrafficMapApi.kt`
  - `OgnTrafficFacade` is the map-facing OGN contract consumed by the map shell.
- `feature/traffic/src/main/java/com/example/xcpro/map/MapTrafficUseCases.kt`
  - `OgnTrafficUseCase` implements `OgnTrafficFacade` and combines trail segments with selected OGN aircraft keys
    from trail-selection SSOT before ViewModel/UI collection.
  - Projects `SelectedOgnThermalContext` from selected thermal ID + hotspot SSOT + raw SCIA trail segments
    so selected-loop highlight, occupancy hull, and derived age/drift/duration/gain metrics remain
    business-owned instead of being recomputed in UI or overlay code.
- `feature/traffic/src/main/java/com/example/xcpro/ogn/OgnTrailSelectionViewModel.kt`
  - Observes OGN suppressed-key stream and prunes suppressed keys from persisted trail selections.
- `feature/traffic/src/main/java/com/example/xcpro/map/MapTrafficUseCases.kt`
  - `OgnTrafficUseCase` exposes OGN settings, thermal-hotspot flows, and OGN glider trail segment flows through `OgnTrafficFacade`.
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt`
  - Converts OGN settings, thermal-hotspot state, OGN trail state, and selected thermal context to
    lifecycle-aware UI/runtime state via `OgnTrafficFacade`.
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRoot.kt`
  - Pushes OGN overlay targets, thermal hotspots, and OGN trail segments into runtime overlay manager.
- `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManager.kt`
  - Owns OGN thermal + OGN glider-trail + selected-thermal-context overlay runtime lifecycle.
  - Owns zoom-aware OGN rendered icon sizing by combining base icon-size preference with runtime viewport zoom in the OGN delegate.
  - Applies display-update mode (`real_time` / `balanced` / `battery`) as map-render throttling only for OGN traffic/thermal/trail/selected-context overlays (ingest is unchanged).
  - Owns OGN/ADS-b traffic overlay creation on startup/style recreation (MapInitializer delegates traffic overlay construction to this runtime owner).
- `feature/map/src/main/java/com/example/xcpro/map/OgnTrafficOverlay.kt`
  - Updates SymbolLayer `iconSize` dynamically from configured pixel size.
  - Resolves wide-zoom label declutter from the current rendered icon size and
    applies it when authoring OGN top/bottom text feature properties.
  - Projects visible OGN targets to screen space and groups very close targets
    into render-only micro-cluster items before feature authoring.
  - Returns typed OGN hit results so grouped markers can zoom to expand instead
    of selecting a wrong aircraft.
- `feature/traffic/src/main/java/com/example/xcpro/ogn/OgnThermalRepository.kt`
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
- `feature/traffic/src/main/java/com/example/xcpro/map/OgnSelectedThermalOverlay.kt`
  - Renders selected-thermal context only: highlighted SCIA loop segments, a faint occupancy hull, start/latest markers,
    and a start-to-latest drift line while reusing the base hotspot dot as the core anchor.
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
  - `feature/traffic/src/main/java/com/example/xcpro/ogn/OgnTrafficRepositoryRuntimeNetworkWait.kt`
    plus `feature/traffic/src/main/java/com/example/xcpro/ogn/domain/OgnNetworkAvailabilityPort.kt`
    add the explicit OGN offline-wait seam; OGN pauses reconnect attempts while
    offline and resumes when connectivity returns instead of blind socket churn.
  - OGN runtime ownership is split intentionally:
    blocking socket reads and DDB refresh work run on the injected IO lane, while
    authoritative OGN state mutation and `OgnTrafficSnapshot` publication run on
    a dedicated writer lane inside `OgnTrafficRepositoryRuntime`.
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRoot.kt`
  - Wires a slim UI-only `MapTrafficUiBinding` for panels/sheets and a separate
    runtime-only traffic overlay input holder for hot overlay mutation.
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapTrafficOverlayRuntimeBindings.kt`
  - Owns the runtime-only traffic overlay input holder and the map-ready OGN/ADS-B
    overlay bootstrap config derived from current runtime state.
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapTrafficOverlayRuntimeCollectors.kt`
  - Owns direct flow collectors for OGN targets, thermals, glider trails,
    selected thermal context, target visuals, ADS-B targets, and icon/config flags.
  - Applies overlay enable gates at the runtime collector seam instead of via
    list-keyed Compose render-state adapters.
  - Owns collector-side request dedupe for hot OGN/ADS-B traffic and OGN target-visual
    requests using render-relevant signatures before calling the overlay manager runtime.
  - Forwards selected OGN target render inputs, including ownship coordinate,
    quantized overlay ownship altitude, altitude unit, and units preferences, into
    the map runtime target-visual path.
  - Forwards selected thermal context into the runtime selected-thermal overlay path.
- `feature/traffic/src/main/java/com/example/xcpro/map/MapOverlayManagerRuntimeOgnDelegate.kt`
  - Keeps OGN target visuals render-only and runtime-owned.
  - Renders the existing target ring + target line, and additionally renders an
    ownship-adjacent badge only when the selected OGN target is outside the
    current visible map bounds.
- `feature/traffic/src/main/java/com/example/xcpro/map/MapOverlayManagerRuntimeOgnDelegate.kt`
  - Keeps selected thermal highlight/hull/drift visuals render-only and runtime-owned.
- `feature/traffic/src/main/java/com/example/xcpro/ogn/OgnMarkerDetailsSheet.kt`
  - Renders selected OGN target details in a `ModalBottomSheet`.
- `feature/traffic/src/main/java/com/example/xcpro/ogn/OgnThermalDetailsSheet.kt`
  - Renders selected thermal details in a half-sheet style `ModalBottomSheet`.
  - Shows derived age, drift bearing/distance, duration, and altitude gain from `SelectedOgnThermalContext`
    alongside the existing climb and altitude metrics.

ADS-b settings path:
- `feature/traffic/src/main/java/com/example/xcpro/adsb/AdsbTrafficPreferencesRepository.kt`
  - SSOT for ADS-b overlay enabled + icon size + max distance + vertical above/below preferences.
  - SSOT for default-unknown-icon rollout controls (`defaultMediumUnknownIconEnabled`, rollback latch/reason).
  - SSOT for ADS-B EMERGENCY audio policy preferences (`emergencyAudioEnabled`, `emergencyAudioCooldownMs`).
- `feature/traffic/src/main/java/com/example/xcpro/map/TrafficMapApi.kt`
  - `AdsbTrafficFacade` is the map-facing ADS-B contract consumed by the map shell.
- `feature/traffic/src/main/java/com/example/xcpro/map/MapTrafficUseCases.kt`
  - `AdsbTrafficUseCase` exposes ADS-b settings flows (`iconSizePx`, `maxDistanceKm`, `verticalAboveMeters`, `verticalBelowMeters`, default-unknown rollout effective enabled) through `AdsbTrafficFacade`.
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt`
  - Converts ADS-b settings flows and ownship altitude into lifecycle-aware state for UI/runtime wiring via `AdsbTrafficFacade`.
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRoot.kt`
  - Pushes icon-size and default-unknown rollout toggles into overlay runtime controller using runtime-owned traffic overlay collectors rather than root Compose list snapshots.
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModelStateBuilders.kt`
  - Derives a dedicated overlay-ownship-altitude state (`2 m` quantized and
    `distinctUntilChanged`) so OGN/ADS-B render paths do not wake on raw altitude jitter
    while repository and non-overlay consumers keep the unmodified ownship altitude SSOT.
- `feature/traffic/src/main/java/com/example/xcpro/map/MapOverlayManagerRuntimeTrafficDelegate.kt`
  - Owns runtime forwarding of map-interaction state into the ADS-B overlay runtime.
  - Keeps reduced-motion ownership runtime-side so Compose and ViewModels do not grow a
    second interaction-quality state holder.
- `feature/traffic/src/main/java/com/example/xcpro/map/AdsbTrafficOverlay.kt`
  - Owns ADS-B overlay-local animation timing, interaction reduced-motion behavior,
    and the effective interaction declutter policy used while pan/zoom interaction is active.
  - While reduced motion is active, emergency targets stay visible but flashing is disabled
    and the overlay-local animation frame loop stops after the current frame.
- `feature/traffic/src/main/java/com/example/xcpro/map/AdsbTrafficOverlayDiagnostics.kt`
  - Owns debug-only ADS-B overlay animation counters (`scheduled`, `rendered`, `skipped`,
    active animated target count, emergency animated target count, reduced-motion state)
    so the shell/runtime status path can attribute hot render work without creating a
    second ad hoc logging seam.
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
- `feature/traffic/src/main/java/com/example/xcpro/adsb/metadata/data/AircraftMetadataRepositoryImpl.kt`
  - On-demand ICAO metadata upserts emit a metadata revision signal.
  - Completed on-demand lookup batches also emit a lookup-progress revision signal, even when no rows were inserted.
- `feature/traffic/src/main/java/com/example/xcpro/adsb/metadata/domain/AdsbMetadataEnrichmentUseCase.kt`
  - Target/icon enrichment and selected-target details recompute when metadata revision or lookup-progress revision changes,
    so icon/category overrides refresh immediately after metadata persistence and unresolved ICAO batches can keep advancing without target churn.
- `feature/traffic/src/main/java/com/example/xcpro/di/AdsbNetworkModule.kt`
  - ADS-B live polling HTTP client and ADS-B metadata HTTP client are split:
    polling stays low-latency cadence-focused, metadata uses longer download-safe timeouts.
- `feature/traffic/src/main/java/com/example/xcpro/adsb/metadata/data/OpenSkyMetadataClient.kt`
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
- `feature/traffic/src/main/java/com/example/xcpro/adsb/AdsbTrafficRepository.kt`
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
  - Production emergency-audio settings/output/bootstrap wiring is explicit in DI;
    disabled/test fallback collaborators live only in test support.
  - EMERGENCY audio rollout master/shadow gates are sourced from ADS-B preferences SSOT via rollout port wiring.
  - Emits one-shot EMERGENCY alert side effects only on FSM trigger transitions through
    `AdsbEmergencyAudioOutputPort` (master-flag gated; shadow mode never plays audio).
- `feature/traffic/src/main/java/com/example/xcpro/adsb/data/AndroidAdsbNetworkAvailabilityAdapter.kt`
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
- `feature/forecast/src/main/java/com/example/xcpro/forecast/ForecastPreferencesRepository.kt`
  - SSOT for forecast overlay preferences (`enabled`, `opacity`, `autoTimeEnabled`, `selectedPrimaryParameterId`, `selectedTimeUtcMs`, `selectedRegion`).
  - Wind-overlay prefs are separate (`windOverlayEnabled`, `selectedWindParameterId`, `windOverlayScale`, `windDisplayMode`).
  - SkySight satellite prefs are SSOT-managed (`skySightSatelliteOverlayEnabled`, imagery/radar/lightning toggles, animation, history-frame count).
- `feature/forecast/src/main/java/com/example/xcpro/di/ForecastModule.kt`
  - Binds forecast ports to `SkySightForecastProviderAdapter` in production runtime.
- `feature/forecast/src/main/java/com/example/xcpro/forecast/SkySightForecastProviderAdapter.kt`
  - Adapter for SkySight catalog/tile/legend/value contracts.
  - Resolves region-aware time slots, per-parameter tile URL formats, source-layer candidates, and point fields.
- `feature/forecast/src/main/java/com/example/xcpro/forecast/FakeForecastProviderAdapter.kt`
  - Test utility adapter for unit tests and local contract validation only.
- `feature/forecast/src/main/java/com/example/xcpro/forecast/ForecastOverlayRepository.kt`
  - Composes prefs + provider ports into overlay-ready state and point-query results.
  - Emits one active non-wind layer state plus optional wind-layer state.
  - Satellite-only time-reference contract:
    - when forecast/wind overlays are off and SkySight satellite overlay is on,
      `selectedTimeUtcMs` is sourced from current wall time (injected clock),
      without forcing forecast catalog/time-slot resolution.
    - satellite renderer applies two-sided freshness clamp (near-live upper bound and history-window lower bound).
  - Maintains last-good tile/legend with fatal-vs-warning error separation
    (non-wind and wind tile hard-failures are fatal when no renderable tile is available).
- `feature/forecast/src/main/java/com/example/xcpro/forecast/ForecastOverlayViewModel.kt`
  - ViewModel-intent boundary for enable/time/opacity and long-press point query.
  - SkySight-tab non-wind parameter selection is single-select (primary only).
- `feature/forecast/src/main/java/com/example/xcpro/map/ui/MapForecastBottomTabContents.kt`
  - Shared SkySight bottom-tab content host consumed by the retained `feature:map` bottom-sheet shell.
  - Keeps forecast-owned controls composition out of `feature:map` while preserving the same tab host/routing.
- `feature/forecast/src/main/java/com/example/xcpro/map/ui/ForecastAuxiliaryHosts.kt`
  - Owns the forecast point-callout card, query-status chip, and wind-speed tap label hosts used by the retained map shell.
  - Keeps forecast-owned auxiliary presentation and formatting out of `feature:map` while the map shell still owns projection, placement, and dismissal orchestration.
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContentRuntime.kt`
  - Collects forecast and weather state, composes SkySight warning/error channels, and routes point-query/tap UI into the shared forecast-owned auxiliary hosts.
  - Applies RainViewer-vs-SkySight dual-rain arbitration: when RainViewer is enabled and SkySight non-wind parameter is `accrain`,
    SkySight primary rain rendering is suppressed while wind overlay remains independent.
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContentRuntimeEffects.kt`
  - Forwards forecast/wind/satellite runtime intent (`enabled`, tile/legend specs, display mode, animation, frame count, selected time)
    to map overlay runtime owner with loading-vs-clear transition policy.
- `feature/map-runtime/src/main/java/com/example/xcpro/map/MapOverlayManagerRuntimeForecastWeatherDelegate.kt`
  - Thin forecast/weather runtime coordinator behind `MapOverlayManagerRuntime`.
  - Forwards shell intents into dedicated rain, SkySight satellite, and forecast-raster runtime delegates.
- `feature/map-runtime/src/main/java/com/example/xcpro/map/MapOverlayManagerRuntimeForecastRasterDelegate.kt`
  - Runtime owner for forecast raster overlay lifecycle, style-reload reapplication, warning aggregation, and wind-arrow lookup.
  - Hosts forecast runtime overlay instances for one non-wind layer and optional wind layer.
- `feature/map-runtime/src/main/java/com/example/xcpro/map/MapOverlayManagerRuntimeSkySightSatelliteDelegate.kt`
  - Runtime owner for SkySight satellite config, style-reload reapply, runtime error flow, and contrast-icon side effects.
- `feature/map-runtime/src/main/java/com/example/xcpro/map/MapOverlayManagerRuntimeWeatherRainDelegate.kt`
  - Runtime owner for RainViewer apply state, deferred interaction cadence, runtime-owned post-interaction release flush consumption, and detach-safe deferred-config cleanup.
- `feature/map-runtime/src/main/java/com/example/xcpro/map/MapWeatherRainInteractionCadencePolicy.kt`
  - Weather-owned interaction cadence policy for rain apply throttling and transition overrides during map interaction.
- `feature/map-runtime/src/main/java/com/example/xcpro/map/ForecastRasterOverlay.kt`
  - MapLibre runtime controller for forecast vector layers.
  - Uses namespace-scoped layer/source IDs so multiple forecast overlays can render together.
  - Supports indexed-fill overlays and wind-point overlays with branch-specific layer cleanup.
  - Wind-point rendering supports `ARROW` and `BARB` display modes from forecast preferences SSOT.
- `feature/map-runtime/src/main/java/com/example/xcpro/map/SkySightSatelliteOverlay.kt`
  - MapLibre runtime controller for SkySight satellite imagery/radar/lightning layers.
  - Uses `satellite.skysight.io` tile templates with `date=YYYY/MM/DD/HHmm` contract and bounded history-frame loop (1-6, 10-minute steps).

Weather rain overlay path:
- `feature/weather/src/main/java/com/example/xcpro/weather/rain/WeatherOverlayPreferencesRepository.kt`
  - SSOT for weather rain overlay preferences (`enabled`, `opacity`, animation toggle, animation window, animation speed, transition quality, frame mode, render options).
- `feature/weather/src/main/java/com/example/xcpro/di/WeatherMetadataNetworkModule.kt`
  - Owns the RainViewer metadata `OkHttpClient` binding and weather-specific DI qualifiers.
- `feature/weather/src/main/java/com/example/xcpro/weather/rain/WeatherRadarMetadataRepository.kt`
  - RainViewer metadata fetch + parse (`weather-maps.json`) with runtime status/fallback handling.
- `feature/weather/src/main/java/com/example/xcpro/weather/rain/ObserveWeatherOverlayStateUseCase.kt`
  - Combines preferences + metadata into frame-based runtime state (`selectedFrame`, status, effective transition duration).
- `feature/weather/src/main/java/com/example/xcpro/weather/rain/WeatherOverlayViewModel.kt`
  - Weather-owned overlay state holder used by the retained map shell bindings.
- `feature/weather/src/main/java/com/example/xcpro/screens/navdrawer/WeatherSettingsViewModel.kt`
  - Owns weather settings state and actions for both the drawer route and in-map bottom-sheet host.
- `feature/weather/src/main/java/com/example/xcpro/weather/ui/WeatherSettingsContent.kt`
  - Shared weather controls content rendered by the retained map-shell entrypoints.
- `feature/weather/src/main/java/com/example/xcpro/map/ui/MapWeatherBottomTabContents.kt`
  - Shared RainViewer bottom-tab content host consumed by the retained `feature:map` bottom-sheet shell.
  - Keeps RainViewer tab-body composition in `feature:weather` while the map host continues to own tab selection and sheet visibility.
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapBottomSheetTabs.kt`
  - Map bottom tab host includes `RainViewer` as an in-map tab in the same `ModalBottomSheet` host used by SkySight/Scia/Map4.
  - Retains tab selection, sheet visibility, and the map-specific OGN/Map4 tabs.
  - Delegates RainViewer and SkySight tab-body content to shared hosts in `feature:weather` and `feature:forecast`; map remains visible behind the sheet.
- `feature/weather/src/main/java/com/example/xcpro/screens/navdrawer/WeatherSettingsScreen.kt`
  - Weather owns the drawer weather compatibility shell, its bottom-sheet host, and the shared RainViewer controls route used by app-shell settings entrypoints.
- `feature/forecast/src/main/java/com/example/xcpro/screens/navdrawer/ForecastSettingsContent.kt`
  - Shared SkySight settings content body owned by `feature:forecast`.
  - Keeps forecast settings UI edits and presentation changes inside the forecast leaf module.
- `feature/forecast/src/main/java/com/example/xcpro/screens/navdrawer/ForecastSettingsScreen.kt`
  - Forecast owns the SkySight drawer/local-subsheet compatibility shell and keeps route-level navigation fallback behavior with the forecast settings content body.
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapWeatherOverlayEffects.kt`
  - Collects `feature:weather` overlay state and forwards frame-based runtime updates (including transition duration) to overlay manager.
  - RainViewer enable state participates in SkySight non-wind rain arbitration through map runtime composition.
- `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManager.kt`
  - Thin shell adapter for overlay/runtime ownership.
  - Owns shell-specific overlay lifecycle/status collaborator construction and attaches those ports to `MapOverlayManagerRuntime`.
- `feature/map-runtime/src/main/java/com/example/xcpro/map/MapOverlayManagerRuntime.kt`
  - Runtime owner for overlay behavior behind the shell adapter, including the batched post-interaction deferred flush settle window for weather and traffic overlays.
  - No longer depends directly on `MapScreenState`; consumes runtime-side shell-port contracts plus leaf-owned runtime state adapters.
- `feature/map-runtime/src/main/java/com/example/xcpro/map/MapOverlayRuntimeInteractionDelegate.kt`
  - Runtime-owned interaction grace/deactivation helper used by the overlay runtime owner before the runtime-owned deferred release-flush batch runs.
- `feature/map-runtime/src/main/java/com/example/xcpro/map/MapOverlayManagerRuntimeBaseOpsDelegate.kt`
  - Runtime-owned overlay base-ops delegate for distance-circle toggles, airspace/waypoint refresh, and task-overlay refresh/clear behavior.
  - Consumes shell-supplied refresh closures from `MapOverlayManager.kt` so `:feature:map-runtime` does not depend back on shell-owned airspace/waypoint helpers.
- `feature/map-runtime/src/main/java/com/example/xcpro/map/MapOverlayRuntimeShellPorts.kt`
  - Runtime-facing contracts for shell-owned overlay lifecycle/status collaborators.
- `feature/traffic/src/main/java/com/example/xcpro/map/TrafficOverlayRuntimeState.kt`
  - Map-free cross-module traffic overlay handle seam consumed by `feature:map-runtime`.
  - Stays in `feature:traffic` because traffic owns the concrete overlay implementations; moving this seam into `feature:map-runtime` would create a module cycle.
- `feature/map-runtime/src/main/java/com/example/xcpro/map/MapOverlayRuntimeCounters.kt`
  - Runtime-side counters model shared between the overlay runtime owner and shell-owned status reporting.
- `feature/map-runtime/src/main/java/com/example/xcpro/map/WeatherRainOverlay.kt`
  - MapLibre raster source/layer runtime with per-frame cache and bounded cross-fade between frames to reduce animation stutter/blink when cycling.

## 5A) Cards (dfcards-library) sub-pipeline

Cards do not read sensors directly. They consume `RealTimeFlightData` from
`:core:flight`, produced by the app/map adapter path.

Current wiring:
- `FlightDataRepository`
  -> `GlideComputationRepository`
  -> `FlightDataUiAdapter` / `MapScreenObservers`
  -> `convertToRealTimeFlightData(...)`
  -> `FlightDataManager.cardFlightDataFlow`
  -> `CardIngestionCoordinator`
  -> `dfcards` `FlightDataViewModel.updateCardsWithLiveData(...)`
  -> `CardLibrary` / `CardContainer` / `EnhancedFlightDataCard`

Replay ownship map-runtime seam:
- `feature/map-runtime/src/main/java/com/example/xcpro/map/ReplayLocationFrame.kt`
  - runtime-owned replay ownship DTO used only by the map runtime layer.
- `feature/map/src/main/java/com/example/xcpro/map/ReplayLocationFrameMapper.kt`
  - shell-owned conversion from `RealTimeFlightData` to `ReplayLocationFrame`.
- runtime-facing map contracts in `feature:map-runtime` use app-owned
  `FlightMode`, not card-owned `FlightModeSelection`.

Current glide-computer production card scope:
- finish/arrival cards live now:
  - `final_gld`
  - `arr_alt`
  - `req_alt`
  - `arr_mc0`
- waypoint navigation cards live now:
  - `wpt_dist`
  - `wpt_brg`
  - `wpt_eta`
- task-performance cards live now:
  - `task_spd`
  - `task_dist`
  - `task_remain_dist`
  - `task_remain_time`
  - `start_alt`
- core glide/performance cards live now:
  - `ias`
  - `tas`
  - `ground_speed`
  - `ld_curr`
  - `ld_vario`
  - `polar_ld`
  - `best_ld`
  - `netto`
  - `netto_avg30`
  - `mc_speed`
- intentionally absent from the production catalogs:
  - standalone `final distance`
  - unsupported future target-kind / AAT glide cards
- visible-card note:
  - `ld_curr` is now the fused pilot-facing Current L/D card
  - raw `currentLD/currentLDValid` remain internal/runtime diagnostics and degraded fallback inputs
- current release limitation:
  - finish-glide validity currently requires a racing finish altitude rule
    (`RacingFinishCustomParams.minAltitudeMeters`)

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
- Map runtime-facing state contracts are now carried through dedicated top-level map-state
  types (`MapPoint`, `MapSize`, `CameraSnapshot`) in `feature/map-runtime`.
- `MapStateReader` / `MapStateActions` now live in `feature/map-runtime` as the
  runtime-facing map-state contracts, while `TrailSettings` remains outside that contract;
  shell/runtime trail wiring is threaded separately from `MapScreenViewModel.trailSettings`.
- `MapStateStore` owns the authoritative base map style plus transient runtime
  style overrides. `MapStateReader.mapStyleName` remains the effective runtime style,
  while forecast satellite and thermalling contrast are modeled as explicit overrides
  instead of UI-local remembered style swaps.
- `SnailTrailRuntimeState` now lives in `feature/map-runtime` as the narrow
  shell/runtime trail-handle contract implemented by `MapScreenState` in
  `feature:map`.
- `TaskRenderSnapshot`, `TaskRenderSyncCoordinator`, `MapFeatureFlags`, and map UI model
  types now live in `feature/map-runtime` as part of the retained shell/runtime split.
- `MapTasksUseCase` now lives in `feature:map` as the map-shell adapter over
  `TaskManagerCoordinator`, reusing the runtime-owned `TaskRenderSnapshot`
  model instead of making `feature:map-runtime` the shell task owner.
  Cross-feature shell reads stay on the coordinator snapshot seam through
  `TaskManagerCoordinator.taskSnapshotFlow` and
  `TaskManagerCoordinator.currentSnapshot()`.
- `DisplayPoseSnapshot` now lives in `feature/map-runtime` as the runtime-facing
  display-pose frame contract used by shell effects and trail/runtime consumers.
  It now carries explicit display-clock timebase metadata so trail/runtime
  consumers can reject mismatched live/replay timestamps safely.
- Camera/location/lifecycle Phase A now exposes explicit shell-facing runtime ports
  from `feature/map-runtime`:
  - `MapCameraRuntimePort`
  - `MapLocationRuntimePort`
  - `MapLifecycleRuntimePort`
  - `MapLocationPermissionRequester`
  Concrete owners still remain in `feature:map` until later owner-move phases.
- Camera/location/lifecycle Phase B now narrows the retained shell fan-out:
  `MapScreenManagers`, scaffold inputs/content, `MapOverlayStack`, and `MapViewHost`
  use the runtime ports plus the shell-local `MapLocationRenderFrameBinder` instead of
  passing concrete `MapCameraManager` / `LocationManager` through the live content path.
- Camera/location/lifecycle Phase C now moves the camera runtime owner:
  `MapCameraManager` and the shared `MapZoomConstraints` helper now live in
  `:feature:map-runtime`, while `feature:map` retains the shell-local
  `MapCameraSurfaceAdapter` and the `MapScreenManagers` construction site.
- Camera/location/lifecycle Phase D now moves the location/display-pose runtime owner:
  `LocationManager` and the runtime-owned display-pose/location helper cluster now
  live in `:feature:map-runtime`, while `feature:map` retains the shell-local
  `MapLocationOverlayAdapter`, `MapDisplayPoseSurfaceAdapter`,
  `MapLibreCameraControllerProvider`, `MapScreenSizeProvider`, and
  `MapCameraUpdateGateAdapter` bridges used by `MapScreenManagers`.
- `MapSensorsUseCase` remains in `feature:map` for now because it bridges
  map-side sensor start/stop requests through `VarioRuntimeControlPort` while
  reading live status, flight state, and flight-mode controls from
  `VarioServiceManager`.
- Camera/location/lifecycle Phase E now moves the lifecycle runtime owner:
  `MapLifecycleManager` lives in `:feature:map-runtime`, while `feature:map`
  retains the shell-local `MapLifecycleSurfaceAdapter`,
  `MapLocationRenderFrameBinderAdapter`, and `MapLifecycleEffects.kt` bridge.
- Map smoothness Phase 1 now keeps continuous follow motion policy inside
  `:feature:map-runtime` through `MapFollowCameraMotionPolicy`; the hot follow
  path in `MapTrackingCameraController` uses direct camera moves while discrete
  transitions remain outside that policy.
- Map smoothness Phase 2 now keeps hot orientation/location/zoom collection out
  of `MapScreenRoot`; `MapScreenHotPathBindings` and `MapScreenHotPathEffects`
  own the high-frequency collectors near the runtime boundary while
  `MapScreenRoot` stays a low-frequency assembler.
- Map smoothness Phase 3 now makes `BlueLocationOverlay` the sole owner of
  steady-state overlay no-op decisions; `MapPositionController` no longer
  forces `setBlueLocationVisible(true)` on every accepted frame.
- Map smoothness Phase 4 now makes render-frame sync event-driven: when
  `useRenderFrameSync` is enabled, `MapComposeEffects` stops owning the
  display-pose frame loop, `LocationManager` requests repaints from raw-fix /
  orientation updates and resume sync, and `RenderFrameSync` remains the single
  render callback owner that dispatches `onRenderFrame()`.
- Map smoothness Phase 5 now keeps hot map-host recomposition narrower:
  `MapScreenComposeAndLifecycleEffects` forwards `currentLocationFlow` and
  `orientationFlow` directly into `MapComposeEffects` collector-based runtime
  effects instead of collecting them into root Compose state, while
  `MapScreenContentRuntime`, `MapOverlayStack`, `MapLiveFollowRuntimeLayer`,
  and the traffic/task/action-button wrapper layers collect location/zoom only
  at the small UI/runtime seams that actually need those values. `MapViewHost`
  stays under the retained shell, but it is no longer driven by root
  `currentLocation` / `currentZoom` churn during steady-state movement.
- Map smoothness Phase 6 now makes render-sync repaint requests cadence-aware in
  `:feature:map-runtime`: `LocationManager` no longer calls `MapCameraController
  .triggerRepaint()` on every accepted orientation/fix burst. A dedicated
  `DisplayPoseRepaintGate` coalesces latest-wins repaint requests to the same
  live/replay cadence contract used by the Compose-owned display-pose loop,
  while exactness paths such as resume, ownship re-enable, and direct
  `onRenderFrame()` processing remain immediate.
- Map smoothness Phase 7 now tightens the remaining render-frame host path:
  `MapViewHost` binds the render-frame listener once per `MapView` instance
  through a dedicated host-binding controller instead of rebinding from every
  `AndroidView.update`, reuses the `MapScreenState.mapView` instance across
  transient host disposal so Compose host churn does not force a new
  `SurfaceView`-backed `MapView`, and leaves final `mapView` clearing to the
  lifecycle cleanup path in `MapLifecycleSurfaceAdapter`. `RenderFrameSync`
  still coalesces off-main pending `mapView.post { onRenderFrame() }`
  callbacks so repeated render-thread notifications cannot queue redundant
  main-thread work.
- Map smoothness Phase 8 now suppresses no-op live display frames in two
  runtime-owned stages. `MapComposeEffects` still owns the live
  `withFrameNanos` ticker when `useRenderFrameSync` is disabled, but it now
  asks `LocationManager.shouldDispatchLiveDisplayFrame()` before dispatching
  each live frame. That runtime gate derives its settle window from the active
  `DisplaySmoothingProfile` and reactivates on real pose/orientation/config
  changes, while `DisplayPoseRenderCoordinator` still compares the current
  rendered pose snapshot against the last rendered snapshot and skips
  materially unchanged frames before camera or blue-overlay mutation. The
  location portion of that late no-op check is screen-space based, using the
  current map surface meters-per-pixel plus the shared jitter threshold; only
  missing/invalid projection metrics fall back to a small meter floor. Both the
  pre-dispatch suppressions and late no-op skips are recorded in
  `MapRenderSurfaceDiagnostics`. The live ticker cadence remains `25 ms` while
  replay stays at `16.7 ms`; cadence ownership remains in
  `DisplayPoseRenderCadence`.
- `MapScreenTrailRuntimeEffects` now forwards display-pose snapshots to
  `SnailTrailManager` for both live and replay tail refresh by subscribing to
  the existing runtime-owned display-pose frame listener. The shell maps
  `DisplayPoseSnapshot.timeBase` to `TrailTimeBase`, while
  `SnailTrailManager` only applies tail refresh when the display-pose timebase
  matches the current trail render-state timebase. The trail shell no longer
  owns a parallel `withFrameNanos` cadence loop.

Card cadence (current):
- `FlightDataManager.cardFlightDataFlow`: bucketed, unthrottled (near pass-through).
- dfcards tiers: FAST 80ms, PRIMARY 250ms, BACKGROUND 1000ms.
- Effective cadence: FAST 80ms, PRIMARY 250ms, BACKGROUND 1000ms.
- Owner: dfcards tiers (single cadence gate).

Card previews (FlightDataMgmt, current):
- `FlightMgmt` consumes the narrow `FlightDataMgmtPort` route seam owned by
  `feature:map`.
- The port exposes `liveFlightDataFlow` for CardsGrid/TemplateEditor previews
  and delegates card hydration binding to the existing map-owned
  `CardIngestionCoordinator`.

Key files:
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenObservers.kt`
- `feature/map/src/main/java/com/example/xcpro/MapScreenUtils.kt`
- `feature/map/src/main/java/com/example/xcpro/map/FlightDataManager.kt`
- `feature/map/src/main/java/com/example/xcpro/map/FlightDataMgmtPort.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/effects/MapComposeEffects.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenHotPathBindings.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenHotPathEffects.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapOverlayStack.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRoot.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRootHelpers.kt`
- `feature/map-runtime/src/main/java/com/example/xcpro/map/BlueLocationOverlay.kt`
- `feature/map-runtime/src/main/java/com/example/xcpro/map/SailplaneIconBitmapFactory.kt`
- `feature/map-runtime/src/main/java/com/example/xcpro/map/MapScaleBarController.kt`
- `feature/map-runtime/src/main/java/com/example/xcpro/map/MapFollowCameraMotionPolicy.kt`
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
  -> `TaskManagerCoordinator` runtime authority + task UI projection
  -> `TaskUiState` StateFlow
  -> Task UI render

Authoritative ownership:
- Cross-feature task definition and active leg: `TaskManagerCoordinator.taskSnapshotFlow`.
- Racing start arm/mode authority: `TaskManagerCoordinator.racingAdvanceSnapshotFlow`.
  `Disarmed` means racing start crossings are ignored entirely: the task stays in
  `PENDING_START`, no `START` or `START_REJECTED` event is emitted, no start is
  credited, and the active leg does not advance until the pilot re-arms and
  makes a new valid start crossing.
- Current final-glide route seam:
  `feature:tasks` now also exposes
  `feature/tasks/src/main/java/com/example/xcpro/tasks/navigation/NavigationRouteRepository.kt`
  as an additive remaining-route read seam derived from
  `TaskManagerCoordinator.taskSnapshotFlow` plus
  `TaskNavigationController.racingState`.
  `feature:map-runtime` now consumes that boundary-aware route seam for glide
  computation. `GlideTargetProjector` is the explicit runtime owner of the
  current racing finish-rule and glide-status projection. No `feature:map`
  compatibility shim remains in the production path; `GlideComputationRepository`
  is the only active glide owner consumed by the map shell.
- Current task-performance seam:
  `feature:map-runtime` now also exposes
  `feature/map-runtime/src/main/java/com/example/xcpro/taskperformance/TaskPerformanceRepository.kt`
  as the non-UI task-performance owner. It consumes
  `FlightDataRepository.flightData`,
  `TaskManagerCoordinator.taskSnapshotFlow`,
  task-owned `NavigationRouteRepository.route`, and
  `TaskNavigationController.racingState`.
  It reads task-owned credited-boundary runtime state (`creditedStart`,
  `creditedFinish`) for exact task distance/speed truth, while tolerance or
  near-miss candidates remain advisory only and do not advance task progress.
  It feeds the map/cards adapter path only; it does not reclaim route ownership
  and it does not expand `CompleteFlightData`.
- Task sheet UI state: `TaskSheetViewModel` collects coordinator snapshots, `TaskSheetUseCase` projects coordinator-owned racing advance state for racing tasks and keeps sheet-local advance policy only for AAT, and `TaskRepository` projects the resulting `TaskUiState`. `TaskUiState.stats.activeIndex` remains the selected-leg/editor seam, not the universal racing in-flight leg. `TaskRepository.state` is not the cross-feature runtime authority.
- Racing minimized top-of-map task surface: `feature/tasks/src/main/java/com/example/xcpro/tasks/TaskFlightSurfaceUseCase.kt` is the dedicated in-flight projector for the minimized indicator path. It combines `TaskManagerCoordinator.taskSnapshotFlow` plus `TaskNavigationController.racingState`; racing tasks render nav-leg progress there, while task sheet/editor surfaces keep coordinator selected-leg semantics.
- Zone entry policy and auto-advance policy: domain/use-case logic backed by
  explicit planner crossing evidence; sampled inside-fix shortcuts are not a
  valid achievement path for racing start/turnpoint/finish progression.
- Persistence: repository/persistence adapters (not ViewModel/UI).
- Map drawing side effects: runtime controllers invoked by use-case/viewmodel orchestration, not direct Composable manager calls.

Module ownership after compile-speed extraction (2026-03-12):
- `feature:tasks` now owns the task SSOT/core/runtime slice plus the task editor UI atoms:
  - `TaskSheetViewModel`
  - `TaskSheetCoordinatorUseCase`
  - `TaskManagerCoordinator`
  - `TaskNavigationController`
  - `RacingTaskManager` / `AATTaskManager`
  - task domain, persistence, navigation, and non-render racing/AAT engines
  - task rules editors and task-type switching UI
  - reusable waypoint search field
  - racing/AAT waypoint list editors
  - racing/AAT point-type selector UI
  - shared task panel helper UI (`TaskCategory`, files tab, minimized rows, QR dialog, preview helpers)
  - shared task panel category host for retained map wrappers
- `feature:map` retains the task compatibility and MapLibre shell:
  - app task route wrappers
  - `TaskMapRenderRouter`
  - `TaskRenderSyncCoordinator` from `feature/map-runtime`
  - map task overlays, bottom-sheet/task wrapper shells, and MapLibre render/edit surfaces
  - wrapper hosts such as `TaskTopDropdownPanel`, `SwipeableTaskBottomSheet`, `RacingManageBTTab`, and `AATManageContent`
- `app` still owns the singleton task graph providers and now depends directly on `feature:tasks`.

Current persistence startup bridge (2026-02-11):
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModelLifecycle.kt`
  - owns the stateless map-screen startup/teardown orchestration invoked by
    `MapScreenViewModel`, without creating a second long-lived screen owner.
  - startup path includes:
  -> `MapTasksUseCase.loadSavedTasks()`
  -> `TaskManagerCoordinator.loadSavedTasks()` (suspend)
  -> `TaskEnginePersistenceService.restore()` for task type + autosaved engine state
  -> coordinator applies restored engine state into legacy managers for current UI compatibility
  -> coordinator publishes `TaskRuntimeSnapshot` to cross-feature consumers (`MapTasksUseCase`, glide, IGC).

Named task persistence bridge (2026-02-11):
- `TaskManagerCoordinator` named operations (`list/save/load/delete`) route to
  `TaskEnginePersistenceService` in DI runtime.
- Task-content autosave ownership (2026-03-15, Phase 4):
  - `TaskManagerCoordinator` task-content mutations
  -> `TaskCoordinatorPersistenceBridge.syncAndAutosave(...)`
  -> `TaskEnginePersistenceService.autosaveEngines()`
  -> racing/AAT persistence adapters.
- `RacingTaskManager` and `AATTaskManager` are mutation-only and do not construct persistence/file-I/O collaborators.
- Runtime coordinator construction is DI-only; compatibility access uses the DI singleton.

Task map rendering bridge (2026-02-12):
- `TaskManagerCoordinator` no longer stores a map instance.
- `TaskManagerCoordinator` AAT edit/target APIs are map-agnostic (no `MapLibreMap` parameters).
- Single trigger owner for map task redraw:
  - `feature/map-runtime/src/main/java/com/example/xcpro/map/TaskRenderSyncCoordinator.kt`
  - Trigger sources emit coordinator events from:
    - `feature/map/src/main/java/com/example/xcpro/tasks/TaskMapOverlay.kt` (task state changes)
    - `feature/map/src/main/java/com/example/xcpro/map/MapInitializer.kt` (map ready)
    - `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManager.kt` (style/overlay refresh)
    - `feature/map/src/main/java/com/example/xcpro/map/ui/MapOverlayStack.kt` (AAT edit + gesture commit mutations)
- Coordinator owns the only runtime call path to:
  - `TaskMapRenderRouter.syncTaskVisuals(...)`
  - This API owns clear + orphan cleanup + conditional replot sequencing.
- Manual active-leg changes no longer participate in the task render-state signature.
  `TaskMapOverlay` emits only real render inputs, so next/prev turnpoint updates
  task/nav/glide consumers without forcing a generic redraw.
- AAT drag split (2026-03-05):
  - gesture move path publishes preview-only target updates through
    `TaskRenderSyncCoordinator.previewAatTargetPoint(...)` ->
    `TaskMapRenderRouter.previewAatTargetPoint(...)` ->
    `AATTaskRenderer.previewTargetPointAndTaskLine(...)` upsert-only target-pin + task-line updates
    (no full task clear/replot per move).
  - gesture end commits canonical target mutation and triggers one full
    `TaskMapRenderRouter.syncTaskVisuals(...)` pass.
- Explicit task-fit path (2026-03-17):
  - `TaskSheetViewModel` emits one-shot `TaskSheetViewportEffect.RequestFitCurrentTask`
    only for approved explicit task actions (waypoint add while building, persisted-task import,
    and named-task load when surfaced).
  - `feature:map` collects that task-local effect and translates it to
    `MapCommand.FitCurrentTask`.
  - `MapRuntimeController` applies the command through `MapCameraManager.fitTaskViewport(...)`.
  - Generic Racing redraw no longer fits or recenters the camera; `RacingTaskDisplay`
    is draw-only and camera fit is explicit runtime behavior.
- Rendering is selected by current task type (RACING/AAT) in the UI runtime layer.
- `TaskMapRenderRouter` consumes `TaskRenderSnapshot` from `MapTasksUseCase`
  (`taskRenderSnapshot()`) and shared core->task mappers (Racing/AAT) instead
  of coordinator manager escape hatches.
- `RacingTaskManager` / `AATTaskManager` no longer expose MapLibre render/edit APIs;
  map rendering/editing flows are UI/runtime-only via renderers/controllers.
- `TaskNavigationController.bind(...)` listener lifetime is caller-scope owned;
  canceling the returned job or caller scope unregisters the coordinator listener.
- `MapInitializer` orchestration is split into focused runtime collaborators:
  - `MapScaleBarController` in `feature:map-runtime`
    (scale bar lifecycle/zoom constraints)
  - `MapInitializerDataLoader` (airspace/waypoint bootstrap and refresh)
  - `MapStyleUrlResolver` (canonical style-name -> URL resolution for runtime style paths)
  - `MapOverlayManager` (traffic overlay creation owner for OGN/ADS-b on startup and style transitions)
- `MapInitializer.setupInitialPosition(...)` and camera-idle callbacks are the startup/runtime viewport-zoom producers for traffic declutter:
  - ADS-B receives viewport zoom through `setAdsbViewportZoom(...)`.
  - OGN receives viewport zoom through `setOgnViewportZoom(...)`, and OGN effective icon size remains delegate-owned render-only state.
- `MapInitializer.setupInitialPosition(...)`, camera-move callbacks, and camera-idle callbacks are also the viewport-projection invalidation producers for display-only traffic declutter:
  - `MapOverlayManagerRuntime.invalidateTrafficProjection(...)` fans out to the OGN and ADS-B runtime delegates.
  - Those delegates recompute screen-space display offsets locally in `feature:traffic`; authoritative aircraft lat/lon stay unchanged in repositories/ViewModels.
  - Projection-only rerenders reuse a dedicated interaction-aware invalidation floor (`120 ms` base, `250 ms` during active pan/rotate) so crowded traffic stays aligned without matching every camera-move callback.
  - After interaction deactivation grace expires, `MapOverlayManagerRuntime` batches any deferred weather/OGN/ADS-B release work behind one short settle window before the traffic front-order reconcile runs.
- `MapInitializer.setupOverlays(...)` and camera-idle callbacks also push the blue ownship overlay viewport snapshot:
  - `BlueLocationOverlay` receives `MapCameraViewportMetrics` plus the current map `distancePerPixel` snapshot.
  - `BlueLocationOverlay` remains the sole owner of visible-radius band resolution and rendered ownship icon size.
- `MapInitializer.setupMapStyle(...)` uses bounded style-load wait with fallback init to avoid startup hangs.
- `MapRuntimeController` applies style commands with map-generation/request-token guards so stale callbacks do not mutate active overlays.
- Parent Phase 3 visual/runtime primitive ownership now also lives in
  `feature:map-runtime`:
  - `BlueLocationOverlay`
  - `SailplaneIconBitmapFactory`
  - `MapScaleBarController`
  - `MapScaleBarRuntimeState` is the narrow shell/runtime bridge implemented by
    `MapScreenState` in `feature:map`
- Parent Phase 3 trail runtime ownership now also lives in
  `feature:map-runtime`:
  - `SnailTrailManager`
  - `SnailTrailOverlay`
  - trail render helpers plus the trail-domain runtime path
    (`TrailProcessor`, `TrailUpdateInput`, `TrailUpdateResult`,
    `TrailRenderState`, `TrailTimeBase`)
  - `TrailProcessor` owns adaptive live sample cadence, live wind smoothing,
    and trail rerender invalidation for circling/drift changes; shell code only
    forwards authoritative inputs and render settings
  - `SnailTrailRuntimeState` is the narrow shell/runtime bridge implemented by
    `MapScreenState` in `feature:map`
- `MapScreenViewModel` delegates task gesture creation and AAT edit forwarding to
  `feature/map/src/main/java/com/example/xcpro/map/MapScreenTaskShellCoordinator.kt`,
  which consumes the map-shell-owned `MapTasksUseCase`.
- Map runtime effects consume ViewModel-bound task type and AAT edit-mode state
  instead of reading coordinator state directly in Composables.
- Task gesture/runtime ownership now splits cleanly between the map shell and `feature:map-runtime`:
  - `feature/map-runtime/src/main/java/com/example/xcpro/gestures/TaskGestureHandler.kt`
    carries the reusable `MapLibreMap`-typed gesture contract.
  - `feature/map-runtime/src/main/java/com/example/xcpro/gestures/TaskGestureHandlerFactory.kt`,
    `feature/map-runtime/src/main/java/com/example/xcpro/tasks/aat/gestures/AatGestureHandler.kt`,
    and `feature/map-runtime/src/main/java/com/example/xcpro/tasks/aat/map/AATMapCoordinateConverter.kt`
    are the runtime owners for task gesture creation, AAT drag/hit-test behavior, and coordinate conversion.
  - `MapScreenTaskShellCoordinator.createTaskGestureHandler(...)` is the map-shell creation owner and uses `MapTasksUseCase` task snapshots.
  - `TaskManagerCoordinator` no longer constructs MapLibre gesture handlers.
- `MapTasksUseCase` task read helpers now derive from the coordinator snapshot
  seam (`TaskManagerCoordinator.taskSnapshotFlow` /
  `TaskManagerCoordinator.currentSnapshot()`), and AAT edit-mode shell reads
  derive from `TaskManagerCoordinator.aatEditWaypointIndexFlow`.
  `MapTasksUseCase` exposes snapshot-based shell reads
  (`currentRuntimeSnapshot()` and `taskRenderSnapshot()`) rather than
  piecemeal task-only helpers.
  `MapTasksUseCase` now lives in `feature:map`; `TaskRenderSnapshot` remains in
  `feature:map-runtime` as the runtime-facing task render model.
- `TaskSheetViewModel` no longer calls manual `sync()` after task mutations. It collects the coordinator snapshot flow and routes AAT target param/lock edits through coordinator-owned mutations.
- AAT service-backed autosave and named-task persistence now run through a canonical JSON store in
  `feature/tasks/src/main/java/com/example/xcpro/tasks/data/persistence/AATCanonicalTaskStorage.kt`.
  Legacy AAT CUP/prefs storage remains read fallback only behind that adapter; task managers no longer own AAT persistence paths.
- Runtime UI/composable dependency lookup no longer uses entrypoint accessors:
  - `TaskManagerCoordinator` is provided via `TaskManagerCoordinatorHostViewModel`.
  - Task navdrawer airspace use-case access is provided via `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/TaskScreenUseCasesViewModel.kt`.

Task navigation/replay bridge (2026-02-11):
- `TaskNavigationController` and `MapScreenReplayCoordinator` consume
  coordinator core-task snapshots and shared mapping helpers for racing
  navigation/replay inputs.
- Navigation/replay code paths do not read `RacingTaskManager.currentRacingTask`
  directly.
- Racing replay restore completeness is owned by
  `feature/map/src/main/java/com/example/xcpro/map/MapReplaySnapshotControllers.kt`:
  the racing replay bundle now captures/restores coordinator selected leg,
  full `RacingNavigationState`, full `RacingAdvanceState.Snapshot`, replay
  mode/cadence/speed/`autoStopAfterFinish`, and racing replay map-shell
  overrides.
- `TaskNavigationController.restoreReplaySnapshot(...)` is the atomic
  task-runtime restore entrypoint for racing replay. Replay restore must not be
  reconstructed by separately mutating coordinator leg, advance mode/armed
  state, and nav state from the map layer.
- `MapScreenReplayCoordinator` fences racing-fix ingress during replay
  start/terminal restore and restores the captured racing replay bundle once on
  start failure or terminal replay completion/cancel/failure after replay
  cleanup.
- `feature:map` replay/task helpers consume the approved task-definition seam
  (`TaskManagerCoordinator.taskSnapshotFlow`) plus the narrow synchronous
  wrapper `TaskManagerCoordinator.currentSnapshot()` for replay-task
  validation, replay snapshot capture, and racing event labelling; map replay
  code does not read coordinator `currentTask`/`currentLeg` as cross-feature
  state.

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
- `feature/profile/src/main/java/com/example/xcpro/vario/LevoVarioPreferencesRepository.kt`
  - Provides `VarioAudioSettings` to both live and replay pipelines.
  - Stores canonical lift/sink start thresholds and converts legacy raw keys
    only on read/import.

## 7) Parallel Pipelines (Wind + Flight State)

Flight runtime foundations (2026-03-16):
- `app` now depends directly on `feature:flight-runtime` at the composition
  root because generated Hilt component sources import moved runtime owners
  directly.
- `:core:flight` now owns the shared non-UI flight DTO and pure flight math
  surface consumed by both runtime and card modules:
  - `RealTimeFlightData`
  - `TerrainElevationReadPort`
  - `SimpleAglCalculator`
  - `BarometricAltitudeCalculator`
  - shared baro/vario filter value and math types
- `feature:flight-runtime` now owns the reusable runtime foundations:
  - sensor contracts/models
  - `FlightDataRepository` / `FlightDisplayMapper`
  - `FlightStateRepository`
  - `WindSensorFusionRepository` and its pure wind helper/domain contracts
  - `ReplaySensorSource`, `ReplayAirspeedRepository`,
    `ExternalAirspeedRepository`
  - shared wind override/model contracts (`WindOverrideSource`,
    `WindOverride`, `WindSource`, `WindVector`)
  - shared glider/audio/HAWK runtime contracts:
    - `StillAirSinkProvider`
    - `SpeedBoundsMs`
    - `VarioAudioSettings`
    - `VarioAudioControllerPort` / `VarioAudioControllerFactory`
    - `HawkAudioVarioReadPort`
  - pure orientation support owners:
    - `HeadingResolver`
    - `OrientationClock`
    - `OrientationMath`
  - reusable orientation input assembly owners:
    - `OrientationSensorSource`
    - `OrientationDataSource`
    - `OrientationDataSourceFactory`
    - narrow orientation sensor/policy contracts
- `feature:map` still owns live sensor/device owners, replay shell/controllers,
  DI composition, thin live-orientation adapters, and the map-specific
  orientation controller path (`MapOrientationManager`, `OrientationEngine`,
  `HeadingJitterLogger`) for those runtime foundations.
- `dfcards-library` consumes `:core:flight` but no longer owns shared flight
  runtime DTOs, terrain seams, or filter math.
- `feature:profile` keeps the concrete still-air sink implementation.
- `feature:variometer` keeps the concrete audio engine/controller and HAWK
  repository implementations behind the shared runtime ports.

Wind fusion:
- `feature/flight-runtime/src/main/java/com/example/xcpro/weather/wind/data/WindSensorFusionRepository.kt`
  - Consumes wind inputs derived from sensors/airspeed.
  - Switches live/replay inputs based on `FlightDataRepository.activeSource`.
  - `windState` feeds:
    - `FlightDataCalculatorEngine` (metrics)
    - `MapScreenObservers` (cards/wind UI, wrapped by `FlightDataUiAdapter`)

Flight state:
- `feature/flight-runtime/src/main/java/com/example/xcpro/sensors/FlightStateRepository.kt`
  - Derives `FlyingState` from GPS/baro/airspeed and SSOT AGL from `FlightDataRepository.flightData`.
  - Does not perform independent terrain-network fetches; AGL authority remains in fusion output.
  - Used by metrics and map observers.

## 8) Replay Pipeline (High-Level)

Replay/UI contract layer:
- `feature/igc/src/main/java/com/example/xcpro/replay/IgcParser.kt`
  - Parses IGC logs into deterministic replay points/metadata.
- `feature/igc/src/main/java/com/example/xcpro/replay/IgcReplayUseCase.kt`
  - Thin replay/session facade for UI; depends only on `IgcReplayControllerPort`.
- `feature/igc/src/main/java/com/example/xcpro/replay/IgcReplayViewModel.kt`
  - Replay screen state/event owner; no runtime wiring.
- `feature/igc/src/main/java/com/example/xcpro/screens/replay/IgcReplayScreen.kt`
  - Replay file picker / speed / timeline UI.

Replay sensors:
- `feature/flight-runtime/src/main/java/com/example/xcpro/replay/ReplaySensorSource.kt`
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
  - Suspends and resumes live sensors through `VarioRuntimeControlPort`, which
    routes those requests back to `VarioForegroundService`.
- `feature/map/src/main/java/com/example/xcpro/map/replay/SyntheticThermalReplayLogBuilder.kt`
  - builds deterministic in-memory thermal `IgcLog` fixtures for snail-trail validation.
  - keeps replay input standard `IgcLog` data; sub-second behavior stays in replay cadence/densification, not parser/file semantics.

Controller:
- `feature/map/src/main/java/com/example/xcpro/replay/IgcReplayController.kt`
  - Implements `IgcReplayControllerPort` for `feature:igc`.
  - Owns runtime session state, sample emission, and source gating.
  - Clears repository on stop to avoid stale UI data.
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenReplayCoordinator.kt`
  - debug replay entrypoint owner for asset-based demo replay, racing replay, and synthetic thermal replay loaded through `loadLog(...)`.
  - keeps synthetic thermal validation mode scoped to `THR` / `THN`, reseeks the final replay frame on completion so the finished thermal stays inspectable, and clears that mode before any later replay launch.
- `feature/map-runtime/src/main/java/com/example/xcpro/map/trail/domain/TrailProcessor.kt`
  - keeps normal live/reference replay retention unchanged, but switches synthetic validation replay to a larger replay-only trail budget so the full thermal remains visible during `THR` / `THN` inspection.

## 9) Time Base Rules (Enforced by Design)

- Live fusion uses monotonic timestamps for deltas/validity windows.
- Replay uses IGC timestamps as the simulation clock.
- Output timestamp:
  - Live: wall time for UI labels.
  - Replay: IGC time for UI.

Key references:
- `feature/flight-runtime/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngineLoops.kt`
- `feature/map/src/main/java/com/example/xcpro/sensors/SensorRegistry.kt`

## 10) Primary Files Index

Core pipeline:
- `feature/map/src/main/java/com/example/xcpro/sensors/UnifiedSensorManager.kt`
- `feature/flight-runtime/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngine.kt`
- `feature/flight-runtime/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngineLoops.kt`
- `feature/flight-runtime/src/main/java/com/example/xcpro/flightdata/FlightDataRepository.kt`
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt`
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenObservers.kt`

Audio:
- `feature/variometer/src/main/java/com/example/xcpro/audio/VarioAudioController.kt`
- `feature/variometer/src/main/java/com/example/xcpro/audio/VarioAudioEngine.kt`

Replay:
- `feature/igc/src/main/java/com/example/xcpro/replay/IgcParser.kt`
- `feature/igc/src/main/java/com/example/xcpro/replay/IgcReplayUseCase.kt`
- `feature/igc/src/main/java/com/example/xcpro/screens/replay/IgcReplayScreen.kt`
- `feature/igc/src/main/java/com/example/xcpro/igc/usecase/IgcFilesUseCase.kt`
- `feature/igc/src/main/java/com/example/xcpro/igc/ui/IgcFilesViewModel.kt`
- `feature/map/src/main/java/com/example/xcpro/replay/IgcReplayController.kt`
- `feature/map/src/main/java/com/example/xcpro/replay/ReplayPipeline.kt`
