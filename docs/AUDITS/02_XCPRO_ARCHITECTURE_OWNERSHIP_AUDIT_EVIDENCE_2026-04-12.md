# XCPro Architecture Ownership Audit Evidence

Date: 2026-04-12

## Purpose

This note records the supporting branch-truth evidence for the XCPro
architecture ownership audit dated 2026-04-12.

This is an audit evidence note, not a proposal.

## Docs read

- `AGENTS.md`
- `docs/ARCHITECTURE/ARCHITECTURE.md`
- `docs/ARCHITECTURE/CODING_RULES.md`
- `docs/ARCHITECTURE/PIPELINE_INDEX.md`
- `docs/ARCHITECTURE/PIPELINE.md`
- `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`
- `docs/ARCHITECTURE/CONTRIBUTING.md`
- `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`

## ADRs and owner-boundary docs consulted

- `docs/ARCHITECTURE/ADR_TASK_RUNTIME_AUTHORITY_2026-03-15.md`
- `docs/ARCHITECTURE/ADR_FINAL_GLIDE_RUNTIME_BOUNDARY_2026-03-25.md`
- `docs/ARCHITECTURE/ADR_MAP_RUNTIME_BOUNDARY_TIGHTENING_2026-04-06.md`
- `docs/ARCHITECTURE/ADR_MAP_RUNTIME_TRAIL_OWNER_2026-03-16.md`
- `docs/ARCHITECTURE/ADR_FLIGHT_RUNTIME_BOUNDARY_2026-03-15.md`
- `docs/ARCHITECTURE/ADR_TASK_SYNC_READ_SEAM_2026-04-06.md`
- `docs/ARCHITECTURE/ADR_CURRENT_LD_PILOT_FUSED_METRIC_2026-04-08.md`
- `docs/ARCHITECTURE/ADR_FLIGHT_MGMT_ROUTE_PORT_2026-04-06.md`

## Commands executed

- `python scripts/arch_gate.py`
  - result: `ARCH GATE PASSED`
- `./gradlew enforceArchitectureFast`
  - result: pass
- `./gradlew :feature:map:compileDebugKotlin`
  - result: pass
- `./gradlew enforceRules`
  - result: pass
- `./gradlew testDebugUnitTest`
  - result: pass
- `./gradlew assembleDebug`
  - result: pass

## Positive evidence: contracts that are currently being followed

### Fused-flight-data SSOT is in the right place

- `AGENTS.md` states that `FlightDataRepository` is the fused-flight-data SSOT.
- `feature/flight-runtime/src/main/java/com/example/xcpro/flightdata/FlightDataRepository.kt`
  keeps a single `flightData` state flow and protects active source ownership so
  live and replay do not overwrite each other silently.

### Cross-feature task reads are using the documented seam

- `docs/ARCHITECTURE/CODING_RULES.md` says cross-feature task reads must use
  `TaskManagerCoordinator.taskSnapshotFlow` or `currentSnapshot()`.
- `feature/tasks/src/main/java/com/example/xcpro/tasks/TaskManagerCoordinator.kt:55`
  exposes `taskSnapshotFlow`.
- `feature/tasks/src/main/java/com/example/xcpro/tasks/TaskManagerCoordinator.kt:111`
  exposes `currentSnapshot()`.
- `feature/map/src/main/java/com/example/xcpro/igc/data/IgcMetadataSources.kt:48`
  reads task declaration data from `taskCoordinator.currentSnapshot().task`.

### Final glide runtime ownership is substantially aligned

- `AGENTS.md` says glide policy/math belongs in dedicated runtime/domain owners.
- `feature/map-runtime/src/main/java/com/example/xcpro/glide/GlideComputationRepository.kt`
  combines:
  - `FlightDataRepository.flightData`
  - `WindSensorFusionRepository.windState`
  - `TaskManagerCoordinator.taskSnapshotFlow`
  - `NavigationRouteRepository.route`
- This is consistent with the current final-glide ADR direction and avoids
  moving final-glide solving into map UI or cards.

### Broad raw logging drift is already an acknowledged exception

- `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md` has an active logging-deviation entry.
- That issue remains a risk, but it is already tracked and time-boxed rather
  than hidden.

### Temporary hotspot exception is already tracked

- `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md` also carries a temporary
  line-budget exception for `TaskSheetViewModel.kt`.
- That file is still a maintainability hotspot, but the overage itself is not a
  newly hidden rule break.

## Evidence for concrete findings

### 1. Compose effect owns non-UI orchestration

File:

- `feature/map/src/main/java/com/example/xcpro/map/ui/effects/MapComposeEffects.kt`

Evidence:

- lines 112-118:
  - calls `flightDataManager.loadVisibleModes(...)`
  - checks `isCurrentModeVisible(...)`
  - computes fallback mode
  - dispatches `onModeChange(fallback)`
- lines 140-146:
  - updates `flightDataManager` and triggers profile-card preparation inside the
    effect path

Why it matters:

- The effect is not rendering-only. It owns profile/configuration orchestration
  and fallback policy.

### 2. Map UI manager reaches into DataStore-backed preferences

Files:

- `feature/map/src/main/java/com/example/xcpro/map/FlightDataManager.kt`
- `dfcards-library/src/main/java/com/example/dfcards/dfcards/CardPreferences.kt`

Evidence:

- `CardPreferences.kt:21`
  - declares `Context.dataStore`
- `CardPreferences.kt:60-79`
  - persists template data through `context.dataStore.edit`
- `FlightDataManager.kt:275-283`
  - `loadVisibleModes(...)` reads profile visibility state directly from
    `cardPreferences`

Why it matters:

- `FlightDataManager` is not only holding map/card UI state. It is reaching into
  persistence-owned preference data to drive UI mode policy.

### 3. Trail runtime owner is directly constructed from feature:map

Files:

- `feature/map/src/main/java/com/example/xcpro/map/FlightDataUiAdapter.kt`
- `feature/map-runtime/src/main/java/com/example/xcpro/map/trail/domain/TrailProcessor.kt`

Evidence:

- `FlightDataUiAdapter.kt:46-65`
  - creates `MapScreenObservers(...)`
  - passes `trailProcessor = TrailProcessor()`
- `TrailProcessor.kt:13-15`
  - comment says it owns trail point storage and transforms sensor data into
    renderable trail state

Why it matters:

- This is a stateful runtime owner being instantiated in the map shell rather
  than provided by an explicit runtime seam.

### 4. Live flight-time display uses wall time instead of the explicit live calculation time

Files:

- `feature/map/src/main/java/com/example/xcpro/map/MapScreenObservers.kt`
- `feature/map/src/main/java/com/example/xcpro/sensors/SensorRegistry.kt`
- `feature/flight-runtime/src/main/java/com/example/xcpro/sensors/SensorData.kt`
- `feature/flight-runtime/src/main/java/com/example/xcpro/sensors/FlightDataEmitter.kt`

Evidence:

- `SensorRegistry.kt:77-86`
  - GPS data carries both `timestamp = location.time` and
    `monotonicTimestampMillis = elapsedRealtimeNanos / 1_000_000`
- `SensorData.kt:27-28`
  - `GPSData.timeForCalculationsMillis` prefers monotonic time when available
- `FlightDataEmitter.kt:81`
  - domain/runtime metrics use `gps.timeForCalculationsMillis`
- `FlightDataEmitter.kt:126-127`
  - `CompleteFlightData.timestamp` is wall time for live UI and IGC time for
    replay UI
- `MapScreenObservers.kt:157-166`
  - derives elapsed flight time from `data.gps?.timestamp ?: data.timestamp`

Why it matters:

- Domain/runtime already knows the correct calculation time seam. The map shell
  falls back to the wrong live source when computing elapsed flight time.

### 5. TaskRepository owns too much business logic for a UI projection seam

File:

- `feature/tasks/src/main/java/com/example/xcpro/tasks/TaskRepository.kt`

Evidence:

- lines 27-30:
  - comment declares it a "Task-sheet UI projector"
- lines 45-48:
  - converts waypoints to domain points and validates
- lines 84-141:
  - resolves OZs, target state, and AAT target movement
- lines 144-190:
  - computes envelope and geodesic distance math

Why it matters:

- Even if it is not acting as a cross-feature authority today, it is holding
  domain logic beyond a narrow projection role.

### 6. TaskManagerCoordinator remains a multi-role hotspot

File:

- `feature/tasks/src/main/java/com/example/xcpro/tasks/TaskManagerCoordinator.kt`

Evidence:

- lines 35-67:
  - constructor and fields include task engines, managers, snapshot flows,
    racing advance state, persistence bridge, handlers, and coordinator scope
- lines 141-149:
  - publishes snapshot and persistence together on mutation
- lines 341-375:
  - also owns racing advance state publication and restoration

Why it matters:

- The coordinator is still carrying enough mixed behavior to remain a hotspot,
  even though the runtime read seam itself is correct.

### 7. MapScreenObservers is another mixed-responsibility hotspot

File:

- `feature/map/src/main/java/com/example/xcpro/map/MapScreenObservers.kt`

Evidence:

- lines 72-151:
  - combines 11 inputs through custom tuple chaining
- lines 168-181:
  - converts data to `RealTimeFlightData` and pushes it to `FlightDataManager`
- lines 183-209:
  - owns trail processing and trail update publication
- lines 230-264:
  - also owns replay toast/debug event handling
- lines 266-299:
  - also performs wind validity/display enrichment

Why it matters:

- This class is acting as a projection adapter, trail bridge, replay-effect
  handler, and wind-display policy layer all at once.

### 8. TaskSheetViewModel comment conflicts with documented owner contract

File:

- `feature/tasks/src/main/java/com/example/xcpro/tasks/TaskSheetViewModel.kt`

Evidence:

- lines 29-32:
  - comment says the ViewModel maintains "a domain TaskRepository for
    validation/stats"

Why it matters:

- The repo docs say `TaskRepository` is UI projection only. The behavior is
  mostly acceptable; the label is not.

### 9. Validation bridge silently generates IDs

File:

- `feature/tasks/src/main/java/com/example/xcpro/tasks/aat/validation/AATValidationBridge.kt`

Evidence:

- lines 138-156:
  - `convertToAATTask(...)` sets
    `id = task.id.ifEmpty { UUID.randomUUID().toString() }`

Why it matters:

- Validation conversion is no longer a pure bridge when it silently creates new
  identity.

### 10. Compliant behavior under a misleading class name

File:

- `feature/map/src/main/java/com/example/xcpro/igc/data/IgcMetadataSources.kt`

Evidence:

- lines 41-48:
  - `TaskRepositoryIgcTaskDeclarationSource` actually depends on
    `TaskManagerCoordinator`
  - reads `taskCoordinator.currentSnapshot().task`

Why it matters:

- This one is behaviorally compliant and should not be treated as a runtime
  authority violation. The problem is naming clarity.

## Additional maintainability observations

- Many production files are below the repo-wide hard `<= 500` line cap but still
  sit near hotspot size pressure.
- The repo passes automated rule gates, which means the remaining drift is
  mostly in owner-placement quality and mixed responsibilities rather than in
  obvious hard-rule violations.

## Bottom line

The current branch is stronger on explicit SSOT/runtime seams than the hotspot
files first suggest.

The remaining failures are concentrated:

- map UI/configuration ownership leakage
- map runtime-owner construction leakage
- one real timebase misuse in flight-time derivation
- task/map hotspots whose mixed responsibilities still make future drift too easy
