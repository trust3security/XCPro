# REFACTOR_ARCH_COMPLIANCE.md

Owner: DFA
Status: Draft
Last updated: 2026-02-03

## Purpose
Bring the codebase into compliance with `docs/RULES/ARCHITECTURE.md` and
`docs/RULES/CODING_RULES.md`, with a focus on DI purity, SSOT ownership,
timebase correctness, and UI/VM isolation.

## Goals
- UI renders state only; no repositories, prefs, or file I/O in Compose.
- ViewModels depend on use-cases only; no Context, MapLibre, or prefs.
- Domain/fusion logic uses injected clocks only; no direct SystemClock or wall time.
- Single SSOT owners for waypoints, home waypoint, flight management, and theme prefs.
- Task domain decoupled from MapLibre/UI; rendering handled by UI adapters.

## Non-goals
- Redesign of UI layouts or visuals.
- Changes to fusion math, scoring algorithms, or replay semantics (behavior parity).
- New persistence formats beyond required refactor (reuse existing stores).

## Current deviations (evidence)
UI / DI violations:
- UI reads prefs directly (map widgets): `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenManagers.kt:73`
- UI-owned widget manager stores prefs directly: `feature/map/src/main/java/com/example/xcpro/map/ui/widgets/MapUIWidgetManager.kt:21`
- UI does file I/O + sharing directly (tasks):
  - `feature/map/src/main/java/com/example/xcpro/tasks/TaskFilesTab.kt:122`
  - `feature/map/src/main/java/com/example/xcpro/tasks/TaskBottomSheetComponents.kt:410`
  - `feature/map/src/main/java/com/example/xcpro/tasks/TaskFileOperations.kt:24`
  - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/tasks/TaskFilesSheetContent.kt:1`
  - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/tasks/TaskFileSection.kt:1`
- UI bypasses ViewModel and calls TaskManagerCoordinator directly:
  - `feature/map/src/main/java/com/example/xcpro/tasks/TaskBottomSheetComponents.kt:78`
  - `feature/map/src/main/java/com/example/xcpro/tasks/TaskFilesTab.kt:63`
  - `feature/map/src/main/java/com/example/xcpro/tasks/aat/AATManageContent.kt:29`
  - `feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingManageBTTab.kt:40`

ViewModel contract violations:
- MapScreenViewModel depends on Context + concrete managers (not use-cases only):
  `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt:61`
- TaskSheetViewModel depends on MapLibre: `feature/map/src/main/java/com/example/xcpro/tasks/TaskSheetViewModel.kt:29`
- IgcReplayViewModel depends on Uri (platform type): `feature/map/src/main/java/com/example/xcpro/replay/IgcReplayViewModel.kt:3`
- UseCase constructs repository directly (DI violation): `feature/map/src/main/java/com/example/xcpro/tasks/TaskSheetUseCase.kt:6`

Timebase violations (domain/fusion logic):
- AAT calculations use wall time: `feature/map/src/main/java/com/example/xcpro/tasks/aat/calculations/AATDistanceCalculator.kt:302`
- AAT interactive models use wall time: `feature/map/src/main/java/com/example/xcpro/tasks/aat/calculations/AATInteractiveModels.kt:11`
- AAT edit session uses wall time: `feature/map/src/main/java/com/example/xcpro/tasks/aat/map/AATEditModeState.kt:39`
- AAT map coordinate converter uses wall time: `feature/map/src/main/java/com/example/xcpro/tasks/aat/map/AATMapCoordinateConverter.kt:186`
- Orientation models default to wall time: `core/common/src/main/java/com/example/xcpro/common/orientation/OrientationContracts.kt:34`
- QNH usecase/repo uses wall time: `feature/map/src/main/java/com/example/xcpro/qnh/CalibrateQnhUseCase.kt:130`,
  `feature/map/src/main/java/com/example/xcpro/qnh/QnhRepositoryImpl.kt:77`
- Audio engine uses wall/mono time directly: `feature/map/src/main/java/com/example/xcpro/audio/VarioAudioEngine.kt:180`
- Orientation sensor source uses wall/mono time directly: `feature/map/src/main/java/com/example/xcpro/OrientationDataSource.kt:200`
- Ballast controller uses uptime directly: `feature/map/src/main/java/com/example/xcpro/map/ballast/BallastController.kt:31`
- Duplicate time abstraction: `feature/map/src/main/java/com/example/xcpro/orientation/OrientationClock.kt:1`

SSOT duplication:
- Two WaypointParser implementations: `feature/map/src/main/java/com/example/xcpro/utils/WaypointRepository.kt:32`
  and `feature/map/src/main/java/com/example/xcpro/screens/flightdata/WaypointParser.kt:12`
- Waypoint ownership split across multiple entry points:
  - `feature/map/src/main/java/com/example/xcpro/map/WaypointLoader.kt:15`
  - `feature/map/src/main/java/com/example/xcpro/flightdata/WaypointFilesRepository.kt:14`
  - `feature/map/src/main/java/com/example/xcpro/utils/WaypointRepository.kt:13`
- Home waypoint persistence duplicated: `core/common/src/main/java/com/example/xcpro/common/waypoint/WaypointModels.kt:61`
  and `core/common/src/main/java/com/example/xcpro/common/waypoint/HomeWaypointRepository.kt:1`
- Theme prefs split across multiple repositories and direct prefs use.
- Airspace persistence split across multiple owners:
  - `feature/map/src/main/java/com/example/xcpro/utils/AirspacePrefs.kt`
  - `feature/map/src/main/java/com/example/xcpro/utils/AirspaceIO.kt`
  - `feature/map/src/main/java/com/example/xcpro/utils/AirspaceRepository.kt`
  - `feature/map/src/main/java/com/example/xcpro/ConfigurationRepository.kt`
  - `feature/map/src/main/java/com/example/xcpro/utils/DFUtils.kt`
- Task persistence split across multiple owners:
  - `feature/map/src/main/java/com/example/xcpro/tasks/TaskCoordinatorPersistence.kt`
  - `feature/map/src/main/java/com/example/xcpro/tasks/TaskFileOperations.kt`
  - `feature/map/src/main/java/com/example/xcpro/tasks/aat/persistence/AATTaskFileIO.kt`
  - `feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingTaskStorage.kt`

Global mutable singletons/caches:
- Feature flags: `feature/map/src/main/java/com/example/xcpro/map/config/MapFeatureFlags.kt:10`,
  `feature/map/src/main/java/com/example/xcpro/tasks/TaskFeatureFlags.kt:6`
- Static caches: `feature/map/src/main/java/com/example/xcpro/utils/AirspaceRepository.kt:39`,
  `feature/map/src/main/java/com/example/xcpro/ConfigurationRepository.kt:135`
- Global logger uses static mutable state: `core/common/src/main/java/com/example/xcpro/core/common/logging/AppLogger.kt:12`

## Plan

### Phase 0 - Inventory + Tests (baseline)
Deliverables:
- Confirm all violations above still exist (rg sweep).
- Add baseline unit tests for any logic touched by refactor (timebase, task math).
- Record any unavoidable exceptions in `docs/RULES/KNOWN_DEVIATIONS.md` (with owner/expiry).

Gate: `./gradlew testDebugUnitTest`

### Phase 1 - Preferences + UI isolation
Deliverables:
- Introduce repositories/use-cases for:
  - Color/theme prefs
  - FlightMgmt prefs
  - Recent waypoints
  - Home waypoint persistence
  - Airspace selection + classes
  - Layout/card layout prefs
- Replace UI SharedPreferences/file I/O with injected repositories and ViewModel state.
- Remove or migrate duplicate `app/FlightMgmtScreen.kt` (keep a single UI).
 - Remove direct repository creation in Compose screens (inject via ViewModels).

Gate: `./gradlew testDebugUnitTest`

### Phase 2 - ViewModel purity
Deliverables:
- Remove `Context` from MapScreenViewModel; inject a WaypointRepository/UseCase.
- Remove MapLibre dependency from TaskSheetViewModel; move map plotting to UI adapter.
- Ensure ViewModels depend on use-cases only and expose immutable StateFlow.
 - Remove Uri from ViewModel APIs (wrap in domain models or pass via use-case).
 - Remove use-case internal repository construction (inject dependencies).

Gate: `./gradlew testDebugUnitTest` + `./gradlew lintDebug`

### Phase 3 - Timebase compliance
Deliverables:
- Replace all domain/fusion SystemClock/System.currentTimeMillis usage with injected Clock.
- Add fake clock tests for time-dependent logic (AAT interactive, QNH, orientation).
- Ensure wall time is UI/output only.
 - Remove duplicate OrientationClock; use core/time/Clock.

Gate: `./gradlew testDebugUnitTest` + `./gradlew lintDebug`

### Phase 4 - Task architecture decoupling
Deliverables:
- Split task domain (pure models/calcs) from MapLibre rendering adapters.
- Move MapLibre logic behind UI-side adapters; keep TaskManagerCoordinator pure.
- Make persistence a repository with injected storage, not within task managers.

Gate: `./gradlew testDebugUnitTest` + `./gradlew lintDebug`

### Phase 5 - SSOT cleanup + global state
Deliverables:
- Remove duplicate parsers and duplicate persistence paths.
- Replace global mutable flags/caches with injected config or scoped caches.
- Update docs to reflect SSOT ownership and dependency direction.
 - Ensure AppLogger and other globals comply or are explicitly documented as deviations.

Gate: `./gradlew testDebugUnitTest` + `./gradlew lintDebug` + `./gradlew assembleDebug`

## Acceptance Criteria
- No UI accesses SharedPreferences or file I/O directly.
- No ViewModel depends on Android framework types or MapLibre.
- Domain/fusion code uses injected Clock only.
- Each persisted data set has exactly one repository owner (SSOT).
- All required checks pass; any exceptions are documented in KNOWN_DEVIATIONS.md.

## Required Checks
- `./gradlew testDebugUnitTest`
- `./gradlew lintDebug`
- `./gradlew assembleDebug`

## Notes
- Preserve behavior parity; refactor for compliance, not feature change.
- If a change risks user-visible behavior, add a unit test first to lock it.
