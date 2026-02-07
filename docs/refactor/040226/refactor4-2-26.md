> NOTICE (2026-02-06): Task refactor plan is documented in $plan. Review before implementing task-related changes.

# Refactor Plan + Compliance Report (2026-02-04)

Scope: bring current codebase into compliance with `docs/RULES/ARCHITECTURE.md`
and `docs/RULES/CODING_RULES.md` for the three targeted areas:
1) MapScreenViewModel refactor into use-case wrappers.
2) TaskSheetViewModel + IgcReplayViewModel cleanup (remove UI types/controllers from VMs).
3) FlightDataViewModel + TaskFilesUseCase DI/timebase fixes in one pass.

This plan is written to be executable as a sequence of small PRs.

---

## 0) Compliance Report Snapshot

### Automated Checks
- `pwsh scripts/ci/enforce_rules.ps1`: PASS

Notes: The script only enforces a subset of the rules (timebase in selected paths,
DI heuristic for managers, VM SharedPreferences/UI imports, collectAsState, vendor
strings, ASCII hygiene). Architecture violations remain outside its coverage.

### Manual Findings (Non-Compliant)
Status update: All findings in sections A-G are addressed as of 2026-02-04.
See "Implementation Status" below for the concrete changes.

#### A) ViewModel purity and dependency direction
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt`
  - Injects platform `Context` and performs IO via `WaypointLoader.load(appContext)`.
  - Depends on `TaskManagerCoordinator`, `VarioServiceManager`,
    `LevoVarioPreferencesRepository`, `CardPreferences`,
    `MapOrientationManagerFactory`, `FlightDataManagerFactory`.
  - Violates: "ViewModels depend on use-cases only", "no platform APIs / IO in VMs".

- `feature/map/src/main/java/com/example/xcpro/tasks/TaskSheetViewModel.kt`
  - Holds `MapLibreMap` (UI type) and calls map APIs directly.
  - Depends on `TaskManagerCoordinator` directly.
  - Constructs `TaskSheetUseCase()` internally (no DI).
  - Violates: "no UI types in VMs", "dependencies injected", "use-cases only".

- `feature/map/src/main/java/com/example/xcpro/replay/IgcReplayViewModel.kt`
  - Depends on `IgcReplayController` directly (not a use case).
  - Uses `android.util.Log` inside VM (platform API).
  - Violates: "VMs depend on use-cases only", "no platform APIs in VMs".

- `dfcards-library/src/main/java/com/example/dfcards/dfcards/FlightDataViewModel.kt`
  - Constructs `DefaultClockProvider` and `FlightCardsUseCase` internally.
  - Violates: "dependencies injected", "no new inside VMs".

#### B) Timebase / domain purity
- `feature/map/src/main/java/com/example/xcpro/tasks/TaskFilesUseCase.kt`
  - Uses `Date()` for filenames (wall time). If UseCase is domain-level, this
    should use an injected Clock.
  - Violates: "domain/use-case logic must use injected time source".

#### C) UI layer imports data/sensor models
- `feature/map/src/main/java/com/example/xcpro/map/ui/**`
  - UI imports `com.example.xcpro.sensors.GPSData` / `GpsStatus`.
  - Violates: "UI code never imports data repositories/models directly".

#### D) Un-gated println logging in production
- `feature/map/src/main/java/com/example/xcpro/tasks/racing/**`
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/**`
- `dfcards-library/src/main/java/com/example/dfcards/dfcards/**`
  - Extensive `println` in production paths, some with location data.
  - Violates: "no logs in tight loops" and "no location logs in release".

#### E) Mutable global feature flags
- `feature/map/src/main/java/com/example/xcpro/map/config/MapFeatureFlags.kt`
- `feature/map/src/main/java/com/example/xcpro/tasks/TaskFeatureFlags.kt`
  - Mutable singleton state via `object`.
  - Violates: "no hidden singletons holding mutable state".

#### F) Replay parser uses implicit wall time
- `feature/map/src/main/java/com/example/xcpro/replay/IgcParser.kt`
  - Defaults to `LocalDate.now(UTC)` when HFDTE missing.
  - Violates: "domain logic should use injected time sources".

#### G) AAT edit flow uses SystemClock directly
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/**`
  - Uses `SystemClock.elapsedRealtime()` for edit session timing and tap timestamps.
  - Violates: "domain/use-case logic must use injected time source".

### Current Exceptions
See `docs/RULES/KNOWN_DEVIATIONS.md` for tracked exceptions:
RULES-20260204-01 through RULES-20260204-09.

---

## 0A) Implementation Status (2026-02-04)

Completed:
- MapScreenViewModel now depends on use-cases only; introduced MapScreen use-case wrappers.
- Map UI now uses UI-safe models (MapLocationUiModel, GpsStatusUiModel) mapped in ViewModel.
- TaskSheetViewModel uses TaskSheetCoordinatorUseCase + TaskSheetUseCase; no UI map types.
- IgcReplayViewModel depends on IgcReplayUseCase; IgcParser injected with Clock.
- FlightDataViewModel uses injected factory; TaskFilesUseCase uses injected Clock.
- Feature flags are injected @Singleton classes (no mutable object singletons).
- AAT edit flow uses injected Clock (tap timestamps, edit sessions, overlays).
- println removed from production sources; remaining examples are documentation only.

Pending:
- Re-run required test suite and update verification timestamps in KNOWN_DEVIATIONS.md.

---

## 1) Target End State (Compliance Goals)

- ViewModels depend on use-cases only.
- ViewModels do not reference platform or UI types.
- All dependencies are injected (Hilt or factory injected via DI).
- Use-cases/domain use injected time sources; no `Date()`/`SystemClock` directly.
- UI controllers/managers live in UI layer or behind use-case boundaries.

---

## 2) Refactor Plan (Three Focused Workstreams)

### Workstream 1: MapScreenViewModel -> Use-Case Wrappers

Goal: Remove platform/API/service/repository dependencies from MapScreenViewModel.

#### Proposed Use-Case Facades
Create thin use-case wrappers that hide non-domain dependencies while preserving behavior:

- `MapWaypointsUseCase`
  - Inputs: none
  - Outputs: `suspend fun loadWaypoints(): Result<List<Waypoint>>`
  - Internals: wraps `WaypointLoader` + `Context` in a repository or data source.

- `MapSensorsUseCase`
  - Outputs: `gpsStatusFlow`, `flightStateFlow`
  - Commands: `setFlightMode(FlightMode)`
  - Internals: wraps `VarioServiceManager` and exposes only flows/commands.

- `MapVarioPrefsUseCase`
  - Output: `showWindSpeedOnVario` flow (or full config flow)
  - Internals: wraps `LevoVarioPreferencesRepository`.

- `MapTasksUseCase`
  - Commands: `loadSavedTasks()`, `setFlightMode(...)`, etc.
  - Internals: wraps `TaskManagerCoordinator` and `TaskNavigationController`.

- `MapUiControllersUseCase` (or `MapUiCoordinatorUseCase`)
  - Output: accessors for `FlightDataManager` and `MapOrientationManager` via
    an interface (expose flows and commands only).
  - Internals: owns creation (factories), but ViewModel depends on the use case.

- `MapCardPreferencesUseCase`
  - Output: `CardPreferences` via an abstraction interface for VM.
  - Internals: wraps `CardPreferences` and provides required operations.

#### MapScreenViewModel Changes
- Remove `Context`, `WaypointLoader`, `VarioServiceManager`,
  `LevoVarioPreferencesRepository`, `TaskManagerCoordinator`,
  `TaskNavigationController`, `CardPreferences`,
  `FlightDataManagerFactory`, `MapOrientationManagerFactory` from VM constructor.
- Replace with use-case facades above.
- Move waypoint loading to `MapWaypointsUseCase`.
- Provide `gpsStatusFlow` and `flightStateFlow` from `MapSensorsUseCase`.
- Provide `showWindSpeedOnVario` from `MapVarioPrefsUseCase`.
- Move `taskManager` interactions into `MapTasksUseCase`.

#### DI / Wiring
- Add Hilt modules to bind new use-cases.
- Keep existing managers/repositories internal to use-cases.
- Ensure ViewModel receives only use-cases.

#### Acceptance Criteria
- No `android.content.Context` in VM.
- No direct injection of `TaskManagerCoordinator`, `VarioServiceManager`,
  `LevoVarioPreferencesRepository`, `CardPreferences`, or factories in the VM constructor.
- No file IO in VM.

Status (2026-02-04):
- Implemented MapScreen use-cases and updated MapScreenViewModel constructor.
- Waypoint load, sensors, tasks, replay, feature flags, and card prefs now flow through use-cases.

---

### Workstream 2: TaskSheetViewModel + IgcReplayViewModel Cleanup

#### TaskSheetViewModel
Goals:
- Remove `MapLibreMap` and any UI/runtime map references from VM.
- Remove direct `TaskManagerCoordinator` dependency.
- Inject `TaskSheetUseCase` instead of constructing it.

Plan:
- Create `TaskSheetUseCase` interface and implementation that wraps
  `TaskManagerCoordinator` and domain validation.
- Use events to request UI map updates (e.g., `TaskUiEvent.PlotOnMap`),
  handled in the Composable or a Map controller.
- Move map plotting into a UI controller (e.g., `TaskMapOverlayController`)
  owned by the UI layer.

Acceptance Criteria:
- VM has no `MapLibreMap` field.
- VM constructor uses only use-case dependencies.
- No direct calls to map APIs inside VM.

Status (2026-02-04):
- TaskSheetViewModel now uses TaskSheetCoordinatorUseCase + TaskSheetUseCase.
- Map plotting moved to TaskMapOverlay (UI layer).

#### IgcReplayViewModel
Goals:
- Replace `IgcReplayController` with an `IgcReplayUseCase`.
- Remove `android.util.Log` from VM (move to use-case or repository if needed).

Plan:
- Introduce `IgcReplayUseCase` wrapping controller and exposing flows + commands.
- Replace logging with either:
  - Use-case internal debug logging (guarded), or
  - A logger abstraction injected into use-case (not the VM).

Acceptance Criteria:
- VM depends only on `IgcReplayUseCase`.
- No `android.util.Log` import in VM.

Status (2026-02-04):
- IgcReplayUseCase added and injected into VM.
- IgcParser now injected with Clock; IgcReplayController updated to pass parser.

---

### Workstream 3: FlightDataViewModel + TaskFilesUseCase

#### FlightDataViewModel (dfcards-library)
Goals:
- Remove internal construction of `DefaultClockProvider` and `FlightCardsUseCase`.
- Inject dependencies via DI or factory.

Plan:
- Add a Hilt module or a factory to provide `Clock`, `CoroutineDispatcher`,
  and `FlightCardsUseCase` for this VM.
- Update VM constructor to receive `Clock` and `FlightCardsUseCase` directly.
- Remove default constructors (or keep defaults only for previews/tests with
  explicit comments and restricted scope).

Acceptance Criteria:
- No `DefaultClockProvider()` created in VM.
- No `FlightCardsUseCase(...)` created in VM.

Status (2026-02-04):
- FlightCardsUseCaseFactory injected; VM uses factory and injected dispatcher.

#### TaskFilesUseCase
Goals:
- Replace `Date()` usage with injected time source.

Plan:
- Inject `Clock` (or `TimeProvider`) into `TaskFilesUseCase`.
- Use `clock.nowWallMs()` and format with `SimpleDateFormat` via `Date(ms)`.
- Ensure deterministic tests by injecting a fake clock.

Acceptance Criteria:
- No `Date()` without injected time source in `TaskFilesUseCase`.
- Time comes from injected clock.

Status (2026-02-04):
- TaskFilesUseCase now uses injected Clock for filename timestamps.

---

### Workstream 4: UI/Data Boundary + Logging + Globals

#### UI uses data/sensor models
Goals:
- Remove direct `sensors.*` model imports from UI layer.

Plan:
- Introduce UI-safe models (e.g., `MapGpsUiModel`, `GpsStatusUiModel`) in a UI
  package or ViewModel state.
- Map from domain/data models to UI models in ViewModel or use-case.
- Update UI composables to consume UI models only.

Acceptance Criteria:
- No `com.example.xcpro.sensors.*` imports under `map/ui/**`.

#### println logging
Goals:
- Remove println from production paths.

Plan:
- Replace with `AppLogger` (debug-gated) or remove entirely.
- Ensure location data is not logged in release builds.

Status:
- Completed 2026-02-04: removed println statements from production sources.

Acceptance Criteria:
- No `println` in production Kotlin sources.

#### Mutable global feature flags
Goals:
- Remove mutable singleton state (`object` with `var`).

Plan:
- Replace with injected configuration (Hilt) or a `FeatureFlagsRepository` with
  explicit state and test overrides.
- If test-only overrides are required, use injected debug/test providers.

Acceptance Criteria:
- Feature flags are not mutable `object` singletons.

Status (2026-02-04):
- MapFeatureFlags and TaskFeatureFlags are injected @Singleton classes.

#### IgcParser wall-time default
Goals:
- Avoid implicit wall time in replay parser.

Plan:
- Inject a Clock or provide a default date from caller (e.g., replay controller).
- Use `clock.nowWallMs()` and derive date only in higher layer if needed.

Acceptance Criteria:
- IgcParser does not call `LocalDate.now()` directly.

Status (2026-02-04):
- IgcParser injected with Clock; default date derived from injected wall time.

#### AAT edit mode timebase
Goals:
- Remove direct `SystemClock` usage from AAT edit timing.

Plan:
- Inject `Clock` (or monotonic time provider) into AAT edit state and map tap helpers.
- Thread the time provider through Compose entry points.

Acceptance Criteria:
- No `SystemClock.elapsedRealtime()` references in AAT edit/map code.

Status (2026-02-04):
- AAT edit sessions, tap timestamps, and overlays now use injected Clock.

---

## 3) Testing Plan

Minimum:
- `./gradlew enforceRules`
- `./gradlew testDebugUnitTest`
- `./gradlew lintDebug`
- `./gradlew assembleDebug`

Additional tests to add (where missing):
- Unit tests for new use-case wrappers (MapWaypointsUseCase, MapSensorsUseCase).
- VM tests for state transitions (MapScreenViewModel, TaskSheetViewModel, IgcReplayViewModel).
- TaskFilesUseCase unit test validating timestamp format using fake clock.

---

## 4) Risks and Mitigations

Risk: Behavior change in MapScreenViewModel during use-case extraction.
- Mitigation: Add VM tests for key behaviors (waypoint load, map state updates,
  flight mode changes, replay gating).

Risk: UI update timing for TaskSheetViewModel after removing MapLibreMap.
- Mitigation: Introduce UI events and ensure map updates are triggered in
  the Composable/controller at the same points as before.

Risk: dfcards-library DI complexity.
- Mitigation: Provide a simple factory binding in the app module if Hilt
  cannot inject into library-level VM directly.

---

## 5) PR Breakdown (Recommended)

PR 1: MapScreenViewModel refactor
- Add use-case wrappers + DI.
- Remove platform/services from VM.
- Add tests for VM flows.

PR 2: TaskSheetViewModel + IgcReplayViewModel cleanup
- Introduce TaskSheetUseCase abstraction.
- Remove MapLibreMap from VM and use events.
- Introduce IgcReplayUseCase.
- Add VM tests.

PR 3: FlightDataViewModel + TaskFilesUseCase
- Inject dependencies into FlightDataViewModel.
- Inject Clock into TaskFilesUseCase.
- Add unit tests for timestamping behavior.

---

## 6) Definition of Done (This Plan)

- All three workstreams implemented.
- `docs/RULES/KNOWN_DEVIATIONS.md` entries resolved or updated.
- All required checks pass.
- No ViewModel depends on platform APIs, UI types, or repositories directly.
- All time sources in use-cases are injected.

