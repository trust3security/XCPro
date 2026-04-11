# CHANGE_PLAN_MAP_TASK_SLICE_HARDENING_2026-02-18.md

## Purpose

Use this plan before implementing hardening work for the map/task slice.
Goal: close architecture drift and release-risk gaps found in deep audit passes.

Read first:

1. `ARCHITECTURE.md`
2. `CODING_RULES.md`
3. `PIPELINE.md`
4. `CODEBASE_CONTEXT_AND_INTENT.md`

## 0) Metadata

- Title: Map/Task Slice Hardening (Import Fidelity + SSOT Consistency + Release Safety)
- Owner: XCPro Team
- Date: 2026-02-18
- Issue/PR: ARCH-20260218-MAPTASK-HARDENING
- Status: Completed for task-files hardening slice (hardening implementation pass #1 through #7 landed)
- Last deep-pass refresh: 2026-02-18 (pass #10)

Implementation update (2026-02-18, pass #1):
- Implemented now:
  - URI-driven CUP import from selected document content in `TaskFilesUseCase`.
  - Shared CUP CSV parser/formatter with quoted-field support and metadata-row skip.
  - Explicit UTF-8 read/write across task file repository + racing/AAT storage paths.
  - Task-files import/export/share failure hardening in `TaskFilesViewModel`.
  - Files-tab apply-json safety path in `TaskFilesTab` + guarded import in `TaskSheetViewModel`.
  - Active-leg preservation and duplicate-id isolation in `TaskRepository` target memory.
  - Locked-target no-recompute behavior in `TaskRepository`.
  - AAT target-point autosave in `AATTaskManager.updateTargetPoint`.
  - Duplicate catch cleanup in `RacingMapRenderer`.
- Added tests:
  - `feature/map/src/test/java/com/example/xcpro/tasks/TaskFilesUseCaseCupImportTest.kt`
  - `feature/map/src/test/java/com/example/xcpro/tasks/domain/TaskRepositoryTargetStateTest.kt`
- Remaining high-risk gaps (still open):
  - rollout cleanup plan for residual legacy `cup_tasks` files after migration
  - share-flow instrumentation coverage for multi-document chooser path
  - device-level validation for multi-document share UX across target Android versions

Implementation update (2026-02-18, pass #2):
- Implemented now:
  - Canonical export/share path now serializes explicit task type + target snapshots from UI state.
  - `TaskPersistSerializer` now preserves/restores task id, custom radius/point type/custom params, OZ payload, and target state.
  - Sparse target snapshot handling fixed: serializer now resolves snapshots by `index` and `id` (with positional fallback for compatibility).
  - Legacy duplicate QR/file path retired by deleting `TaskQRGenerator.kt` and routing preview/share through canonical QR dialog flow.
- Added tests:
  - `feature/map/src/test/java/com/example/xcpro/tasks/TaskPersistSerializerFidelityTest.kt`
  - extended `feature/map/src/test/java/com/example/xcpro/tasks/TaskFilesUseCaseCupImportTest.kt`

Implementation update (2026-02-18, pass #3):
- Implemented now:
  - Single-intent share consolidation for task export/share:
    - `ShareRequest` now supports multi-document payloads.
    - `TaskFilesUseCase.shareTask(...)` now returns one canonical share request (instead of one event per file).
    - `TaskFilesShare.shareRequest(...)` now uses `ACTION_SEND_MULTIPLE` when needed.
  - CUP storage partitioning by task type with backward-compatible legacy fallback:
    - Racing now uses `filesDir/cup_tasks/racing`.
    - AAT now uses `filesDir/cup_tasks/aat`.
    - Legacy `filesDir/cup_tasks` remains readable for compatibility.
  - Deterministic fallback task IDs in persistence adapters:
    - removed random fallback IDs in `TaskPersistenceAdapters`.
    - fallback ID now derives deterministically from waypoint fingerprint.
- Added tests:
  - `feature/map/src/test/java/com/example/xcpro/tasks/TaskFilesUseCaseShareRequestTest.kt`
  - `feature/map/src/test/java/com/example/xcpro/tasks/TaskStoragePartitioningTest.kt`
  - `feature/map/src/test/java/com/example/xcpro/tasks/data/persistence/TaskPersistenceAdaptersDeterministicIdTest.kt`

Implementation update (2026-02-18, pass #4):
- Implemented now:
  - Failure-mode test-net expansion for task file persistence and share event paths.
  - Added explicit coverage for repository write/read failure handling (`TaskFilesRepository`).
  - Added explicit coverage for racing/AAT CUP storage write/read failure handling.
  - Added explicit coverage for `TaskFilesViewModel` share failure mapping (null request + thrown exception).
- Added tests:
  - `feature/map/src/test/java/com/example/xcpro/tasks/TaskFilesRepositoryFailureTest.kt`
  - `feature/map/src/test/java/com/example/xcpro/tasks/TaskStorageFailureModesTest.kt`
  - `feature/map/src/test/java/com/example/xcpro/tasks/TaskFilesViewModelFailureResilienceTest.kt`

Implementation update (2026-02-18, pass #5):
- Implemented now:
  - One-time best-effort legacy CUP migration policy:
    - racing storage migrates compatible files from `cup_tasks` -> `cup_tasks/racing`
    - AAT storage migrates compatible files from `cup_tasks` -> `cup_tasks/aat`
    - migration is bounded via per-storage migration flags in existing task prefs
  - Migration remains non-destructive on ambiguous collisions and keeps legacy fallback readable.
- Updated tests:
  - `feature/map/src/test/java/com/example/xcpro/tasks/TaskStoragePartitioningTest.kt` now validates legacy->scoped migration side effects
  - `feature/map/src/test/java/com/example/xcpro/tasks/TaskStorageFailureModesTest.kt` now clears migration prefs for deterministic isolation

Implementation update (2026-02-18, pass #6):
- Implemented now:
  - Added instrumentation coverage for canonical multi-document share dispatch:
    - `app/src/androidTest/java/com/example/xcpro/TaskFilesShareInstrumentedTest.kt`
  - Device-level validation confirms one chooser event wrapping a single `ACTION_SEND_MULTIPLE` payload with URI grant flags.
- Verification:
  - `./gradlew --% :app:connectedDebugAndroidTest --no-parallel -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true -Pandroid.testInstrumentationRunnerArguments.class=com.example.xcpro.TaskFilesShareInstrumentedTest`: PASS

Implementation update (2026-02-18, pass #7):
- Implemented now:
  - Added strict residual legacy cleanup policy after dual migration completion:
    - `feature/map/src/main/java/com/example/xcpro/tasks/LegacyCupStorageCleanupPolicy.kt`
  - Racing/AAT storage migrations now invoke cleanup policy after migration and on subsequent runs.
  - Policy behavior:
    - delete legacy duplicates that match scoped files,
    - archive unresolved legacy leftovers to `filesDir/cup_tasks/legacy_archive`,
    - mark cleanup complete only when no legacy root `.cup` files remain.
- Updated tests:
  - `feature/map/src/test/java/com/example/xcpro/tasks/TaskStoragePartitioningTest.kt` now validates residual conflict archival sequencing.
  - `feature/map/src/test/java/com/example/xcpro/tasks/TaskStorageFailureModesTest.kt` now clears cleanup policy prefs for deterministic isolation.
- Verification:
  - `./gradlew :feature:map:testDebugUnitTest --tests "com.example.xcpro.tasks.TaskStoragePartitioningTest" --tests "com.example.xcpro.tasks.TaskStorageFailureModesTest"`: PASS

Implementation update (2026-02-18, pass #12):
- Implemented now:
  - Closed follow-up contract ambiguity in coordinator distance helper:
    - `TaskManagerCoordinator.calculateTaskDistanceForTask(task)` now computes from the provided task waypoints via delegate segment-distance contract.
  - Closed task renderer exception logging debt:
    - removed `printStackTrace()` usage from task map render/edit paths,
    - replaced with bounded debug-only `Log.w` diagnostics (`BuildConfig.DEBUG` gated, no stack dumps).
- Updated tests:
  - `feature/map/src/test/java/com/example/xcpro/tasks/TaskManagerCoordinatorTest.kt` now verifies task-argument distance computation path.
- Verification:
  - `./gradlew :feature:map:testDebugUnitTest --tests "com.example.xcpro.tasks.TaskManagerCoordinatorTest"`: PASS
  - `./gradlew enforceRules testDebugUnitTest assembleDebug`: PASS

Implementation update (2026-02-18, pass #13):
- Implemented now:
  - Closed remaining task-repository projection drift:
    - `TaskRepository` now preserves explicit `TaskWaypoint.role` (no index-role rewrite),
    - observation-zone projection now honors persisted OZ metadata (`ozType`/`ozParams`) and custom point-type hints,
    - AAT target-capable points now include `OPTIONAL` role (not only `TURNPOINT`).
  - Closed task import sequencing churn:
    - `TaskSheetViewModel` import waypoint application no longer calls nested `mutate`/`sync` cycles per waypoint,
    - import now batches coordinator waypoint adds and commits a single final sync refresh.
- Added tests:
  - `feature/map/src/test/java/com/example/xcpro/tasks/domain/TaskRepositoryProjectionComplianceTest.kt`
  - extended `feature/map/src/test/java/com/example/xcpro/tasks/TaskSheetViewModelImportTest.kt`
- Verification:
  - `./gradlew :feature:map:testDebugUnitTest --tests "com.example.xcpro.tasks.TaskSheetViewModelImportTest" --tests "com.example.xcpro.tasks.domain.TaskRepositoryProjectionComplianceTest"`: PASS
  - `./gradlew enforceRules testDebugUnitTest assembleDebug`: PASS

Deep-pass refresh (2026-02-18, pass #11):
- Re-audit scope:
  - task/map runtime paths,
  - task-file hardening slice code + tests,
  - residual contract and logging debt from earlier findings.
- Newly confirmed residuals (non-blocking for task-files release gate):
  1. `P2` API contract ambiguity remains:
     - `TaskManagerCoordinator.calculateTaskDistanceForTask(task)` ignores the `task` argument and uses current delegate state only
       (`feature/map/src/main/java/com/example/xcpro/tasks/TaskManagerCoordinator.kt:207`).
     - No production callsites found; current usage is test-only.
  2. `P2` Runtime exception logging debt remains in task renderers:
     - `printStackTrace()` still appears in map/task rendering/overlay code paths:
       - `feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingMapRenderer.kt`
       - `feature/map/src/main/java/com/example/xcpro/tasks/aat/rendering/AATMapRenderer.kt`
       - `feature/map/src/main/java/com/example/xcpro/tasks/aat/rendering/AATTaskRenderer.kt`
       - `feature/map/src/main/java/com/example/xcpro/tasks/aat/interaction/AATEditOverlayRenderer.kt`
- Release impact assessment:
  - task-files hardening gate remains release-ready (`HIGH`) for import/export/share/migration/cleanup behavior.
  - residuals were targeted in follow-up implementation pass #12.

## 1) Scope

- Problem statement:
  - Task file import/export and task-state projection are inconsistent across layers; several risky paths are weakly tested.
  - Key findings:
    - `.cup` import in files tab resolves by display name into named-task persistence instead of the selected document URI (`feature/map/src/main/java/com/example/xcpro/tasks/TaskFilesUseCase.kt:29`).
    - Serializer claims full-fidelity but drops waypoint custom fields and uses default OZ params (`feature/map/src/main/java/com/example/xcpro/tasks/TaskPersistSerializer.kt:14`, `feature/map/src/main/java/com/example/xcpro/tasks/TaskPersistSerializer.kt:25`, `feature/map/src/main/java/com/example/xcpro/tasks/TaskPersistSerializer.kt:50`).
    - Repository domain projection ignores waypoint role/custom geometry and rewrites role by index (`feature/map/src/main/java/com/example/xcpro/tasks/TaskRepository.kt:86`).
    - JSON import success is emitted before apply/parse confirmation (`feature/map/src/main/java/com/example/xcpro/tasks/TaskFilesViewModel.kt:51`).
- Why now:
  - Current behavior can silently import wrong tasks, lose custom geometry/targets, and overstate release confidence.
- In scope:
  - Map/task import/export and task-state projection paths.
  - Risky map/task test coverage for these paths.
  - Removal or quarantine of stale legacy task UI/file paths.
- Out of scope:
  - New user-facing task features.
  - Non-task map overlays unrelated to task import/state.
- User-visible impact:
  - Correct task import target, stable cross-device sharing, and trustworthy task stats/geometry after import.

## 1A) Deep-Pass Delta (What Earlier Passes Missed)

Severity uses `P0` (release blocker), `P1` (high), `P2` (medium).

1. `P0` CUP import contract mismatch in Files tab.
   - `TaskFilesUseCase.importTaskFile` routes `.cup` import by display name into named-task persistence, not selected URI content (`feature/map/src/main/java/com/example/xcpro/tasks/TaskFilesUseCase.kt:29`).
   - Racing/AAT CUP parsers only parse quoted waypoint rows (`feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingTaskStorage.kt:183`, `feature/map/src/main/java/com/example/xcpro/tasks/aat/persistence/AATTaskFileIO.kt:240`), while `taskToCup` writes unquoted rows (`feature/map/src/main/java/com/example/xcpro/tasks/TaskFilesUseCase.kt:130`).

2. `P0` CSV/CUP robustness is unsafe for commas and quoting.
   - Parsers split by raw comma (`feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingTaskStorage.kt:185`, `feature/map/src/main/java/com/example/xcpro/tasks/aat/persistence/AATTaskFileIO.kt:241`) and are not CSV-quote aware.
   - Writers do not consistently quote/escape user text fields (`feature/map/src/main/java/com/example/xcpro/tasks/TaskFilesUseCase.kt:130`).

3. `P1` Locale-sensitive numeric formatting in task file writers.
   - Default-locale `String.format` is used for lat/lon serialization in CUP writers (`feature/map/src/main/java/com/example/xcpro/tasks/TaskFilesUseCase.kt:140`, `feature/map/src/main/java/com/example/xcpro/tasks/TaskFilesUseCase.kt:147`, `feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingTaskStorage.kt:150`, `feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingTaskStorage.kt:155`, `feature/map/src/main/java/com/example/xcpro/tasks/aat/persistence/AATTaskFileIO.kt:203`, `feature/map/src/main/java/com/example/xcpro/tasks/aat/persistence/AATTaskFileIO.kt:208`).

4. `P0` Main-thread I/O risk on export/share/import-adjacent paths.
   - ViewModel calls sync export/share use-case methods from `viewModelScope.launch` (`feature/map/src/main/java/com/example/xcpro/tasks/TaskFilesViewModel.kt:92`, `feature/map/src/main/java/com/example/xcpro/tasks/TaskFilesViewModel.kt:103`).
   - Repository methods performing resolver/file writes are non-suspending (`feature/map/src/main/java/com/example/xcpro/tasks/TaskFilesRepository.kt:82`, `feature/map/src/main/java/com/example/xcpro/tasks/TaskFilesRepository.kt:160`).

5. `P0` JSON import success and failure semantics are incorrect.
   - Success toast is emitted before JSON apply/parse outcome (`feature/map/src/main/java/com/example/xcpro/tasks/TaskFilesViewModel.kt:51`, `feature/map/src/main/java/com/example/xcpro/tasks/TaskFilesViewModel.kt:52`).
   - UI event handler directly calls `taskViewModel.importPersistedTask(event.json)` with no local failure guard (`feature/map/src/main/java/com/example/xcpro/tasks/TaskFilesTab.kt:88`, `feature/map/src/main/java/com/example/xcpro/tasks/TaskFilesTab.kt:89`).

6. `P0` Import/serializer fidelity gaps remain.
   - Serializer writes default OZ values instead of preserving waypoint custom OZ payload (`feature/map/src/main/java/com/example/xcpro/tasks/TaskPersistSerializer.kt:50`, `feature/map/src/main/java/com/example/xcpro/tasks/TaskPersistSerializer.kt:51`).
   - `toTask` forces task id `"imported"` and `allowsTarget = true` on all snapshots (`feature/map/src/main/java/com/example/xcpro/tasks/TaskPersistSerializer.kt:69`, `feature/map/src/main/java/com/example/xcpro/tasks/TaskPersistSerializer.kt:97`).
   - Import path adds waypoints through `SearchWaypoint` projection, dropping persisted role/custom fields before partial re-application (`feature/map/src/main/java/com/example/xcpro/tasks/TaskSheetViewModel.kt:245`, `feature/map/src/main/java/com/example/xcpro/tasks/TaskSheetViewModel.kt:248`).

7. `P1` Target lock semantics are effectively non-functional.
   - Lock control exists in UI (`feature/map/src/main/java/com/example/xcpro/tasks/aat/AATTargetControls.kt:59`) but lock state is not used to gate target recomputation in repository update flow (`feature/map/src/main/java/com/example/xcpro/tasks/TaskRepository.kt:131`, `feature/map/src/main/java/com/example/xcpro/tasks/TaskRepository.kt:136`).

8. `P1` Task-type persistence contamination remains.
   - Racing lists all `.cup` files from shared `cup_tasks` (`feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingTaskStorage.kt:56`, `feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingTaskStorage.kt:62`).
   - AAT list uses filename contains `AAT` heuristic in same directory (`feature/map/src/main/java/com/example/xcpro/tasks/aat/persistence/AATTaskFileIO.kt:82`).

9. `P1` Determinism drift from random fallback IDs across adapters.
   - Persistence adapters and file parsers still generate random fallback IDs (`feature/map/src/main/java/com/example/xcpro/tasks/data/persistence/TaskPersistenceAdapters.kt:178`, `feature/map/src/main/java/com/example/xcpro/tasks/data/persistence/TaskPersistenceAdapters.kt:279`, `feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingTaskStorage.kt:199`, `feature/map/src/main/java/com/example/xcpro/tasks/aat/persistence/AATTaskFileIO.kt:259`).

10. `P2` Legacy duplicate QR path still diverges from main import/share path.
   - Legacy QR serializer path omits targets (`feature/map/src/main/java/com/example/xcpro/tasks/TaskQRGenerator.kt:198`, `feature/map/src/main/java/com/example/xcpro/tasks/TaskQRGenerator.kt:201`).
   - Legacy QR share path starts chooser from IO dispatcher (`feature/map/src/main/java/com/example/xcpro/tasks/TaskQRGenerator.kt:220`, `feature/map/src/main/java/com/example/xcpro/tasks/TaskQRGenerator.kt:242`).

11. `P2` Share flow emits one chooser event per generated file.
   - `TaskFilesViewModel.shareTask` emits `TaskFilesEvent.Share` for each request (`feature/map/src/main/java/com/example/xcpro/tasks/TaskFilesViewModel.kt:108`, `feature/map/src/main/java/com/example/xcpro/tasks/TaskFilesViewModel.kt:109`), causing repeated chooser UX instead of a single combined intent.

12. `P2` Import sequencing does redundant nested sync cycles.
   - `TaskSheetViewModel.importPersistedTask` wraps import in `mutate` while `importWaypoints` calls `onAddWaypoint`, which itself calls `mutate` (`feature/map/src/main/java/com/example/xcpro/tasks/TaskSheetViewModel.kt:163`, `feature/map/src/main/java/com/example/xcpro/tasks/TaskSheetViewModel.kt:245`, `feature/map/src/main/java/com/example/xcpro/tasks/TaskSheetViewModel.kt:52`).

13. `P0` Racing/AAT CUP save->load compatibility is likely broken by own parser contract.
   - Writers emit a quoted task metadata row with empty lat/lon (`feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingTaskStorage.kt:142`, `feature/map/src/main/java/com/example/xcpro/tasks/aat/persistence/AATTaskFileIO.kt:195`).
   - Parsers accept any quoted non-header row and immediately parse coords (`feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingTaskStorage.kt:183`, `feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingTaskStorage.kt:189`, `feature/map/src/main/java/com/example/xcpro/tasks/aat/persistence/AATTaskFileIO.kt:240`, `feature/map/src/main/java/com/example/xcpro/tasks/aat/persistence/AATTaskFileIO.kt:245`), so metadata row can trigger parse failure.
   - Both parse methods swallow at function scope and return null/empty on exception (`feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingTaskStorage.kt:218`, `feature/map/src/main/java/com/example/xcpro/tasks/aat/persistence/AATTaskFileIO.kt:273`).

14. `P0` Canonical Files export/share paths still drop target snapshots.
   - `TaskFilesUseCase` serializes with `targets = emptyList()` for export and share (`feature/map/src/main/java/com/example/xcpro/tasks/TaskFilesUseCase.kt:55`, `feature/map/src/main/java/com/example/xcpro/tasks/TaskFilesUseCase.kt:82`), despite serializer supporting targets.

15. `P1` Risky file persistence paths are effectively untested.
   - No direct unit/instrumentation coverage found for `RacingTaskStorage.save/load CUP`, `AATTaskFileIO.save/load CUP`, or `TaskFilesUseCase` import/export/share paths.

16. `P2` Additional production logging debt in task/map runtime paths.
   - Widespread `printStackTrace()` and verbose logs in task map runtime/render code (`feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingMapRenderer.kt:123`, `feature/map/src/main/java/com/example/xcpro/tasks/aat/rendering/AATMapRenderer.kt:68`, `feature/map/src/main/java/com/example/xcpro/tasks/aat/interaction/AATEditOverlayRenderer.kt:34`, `feature/map/src/main/java/com/example/xcpro/tasks/racing/turnpoints/FinishLineDisplay.kt:21`).

17. `P2` API contract bug in coordinator distance helper.
   - `TaskManagerCoordinator.calculateTaskDistanceForTask(task)` ignores the passed `task` parameter and calculates from current delegate state only (`feature/map/src/main/java/com/example/xcpro/tasks/TaskManagerCoordinator.kt:207`).

18. `P0` Active-leg regression on target edits.
   - `TaskRepository.updateFrom` defaults `activeIndex` to `0` (`feature/map/src/main/java/com/example/xcpro/tasks/TaskRepository.kt:41`).
   - `setTargetParam` / `toggleTargetLock` / `setTargetLock` call `updateFrom` without passing current active leg (`feature/map/src/main/java/com/example/xcpro/tasks/TaskRepository.kt:213`, `feature/map/src/main/java/com/example/xcpro/tasks/TaskRepository.kt:222`, `feature/map/src/main/java/com/example/xcpro/tasks/TaskRepository.kt:230`).
   - `TaskSheetViewModel` target edit actions do not call full `sync()` after use-case mutation (`feature/map/src/main/java/com/example/xcpro/tasks/TaskSheetViewModel.kt:68`, `feature/map/src/main/java/com/example/xcpro/tasks/TaskSheetViewModel.kt:73`), so UI can observe index reset to `0`.

19. `P1` CUP latitude formatting mismatch for low-latitude waypoints.
   - Racing/AAT storage writers do not zero-pad latitude degrees (`feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingTaskStorage.kt:150`, `feature/map/src/main/java/com/example/xcpro/tasks/aat/persistence/AATTaskFileIO.kt:203`).
   - Parsers assume fixed two-digit latitude degrees (`feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingTaskStorage.kt:228`, `feature/map/src/main/java/com/example/xcpro/tasks/aat/persistence/AATTaskFileIO.kt:289`).

20. `P1` Main-thread blocking risk extends through named-task load chain.
   - Files import invokes `taskManager.loadTask` from UI coroutine (`feature/map/src/main/java/com/example/xcpro/tasks/TaskFilesUseCase.kt:29`).
   - Load chain traverses sync file I/O adapters without enforced dispatcher switch (`feature/map/src/main/java/com/example/xcpro/tasks/TaskManagerCoordinator.kt:236`, `feature/map/src/main/java/com/example/xcpro/tasks/TaskCoordinatorPersistenceBridge.kt:59`, `feature/map/src/main/java/com/example/xcpro/tasks/domain/persistence/TaskEnginePersistenceService.kt:53`, `feature/map/src/main/java/com/example/xcpro/tasks/data/persistence/TaskPersistenceAdapters.kt:83`, `feature/map/src/main/java/com/example/xcpro/tasks/data/persistence/TaskPersistenceAdapters.kt:118`).

21. `P1` Import exception handling can cancel event pipelines.
   - Files tab event collector calls `taskViewModel.importPersistedTask` directly (`feature/map/src/main/java/com/example/xcpro/tasks/TaskFilesTab.kt:88`, `feature/map/src/main/java/com/example/xcpro/tasks/TaskFilesTab.kt:89`).
   - `importPersistedTask` does not guard deserialize/toTask exceptions (`feature/map/src/main/java/com/example/xcpro/tasks/TaskSheetViewModel.kt:163`, `feature/map/src/main/java/com/example/xcpro/tasks/TaskSheetViewModel.kt:165`).
   - QR scan flow also invokes `onImportJson(decoded)` without local protection (`feature/map/src/main/java/com/example/xcpro/tasks/QrTaskDialogs.kt:55`).

22. `P2` Potential MediaStore pending-row leak on write failure.
   - `saveToDownloads` sets `IS_PENDING = 1` then returns `null` on `openOutputStream` failure without clearing/deleting the pending row (`feature/map/src/main/java/com/example/xcpro/tasks/TaskFilesRepository.kt:98`, `feature/map/src/main/java/com/example/xcpro/tasks/TaskFilesRepository.kt:105`).

23. `P2` Encoding determinism not explicit in task file writes.
   - Multiple write paths rely on platform default charset (`feature/map/src/main/java/com/example/xcpro/tasks/TaskFilesRepository.kt:104`, `feature/map/src/main/java/com/example/xcpro/tasks/TaskFilesRepository.kt:121`, `feature/map/src/main/java/com/example/xcpro/tasks/TaskFilesRepository.kt:163`, `feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingTaskStorage.kt:85`, `feature/map/src/main/java/com/example/xcpro/tasks/aat/persistence/AATTaskFileIO.kt:113`).

24. `P2` Dead/duplicate preview+QR path still present.
   - `TaskPreviewContent` appears unreferenced (definition-only hit) while wiring legacy QR dialog + task files event handling (`feature/map/src/main/java/com/example/xcpro/tasks/TaskBottomSheetComponents.kt:51`).

25. `P2` Duplicate exception catch in racing map renderer.
   - Duplicate `catch (e: Exception)` blocks in course-line render path (`feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingMapRenderer.kt:278`, `feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingMapRenderer.kt:280`).

26. `P2` Target memory map lifecycle is unbounded.
   - `targetStateById` accumulates by waypoint id (`feature/map/src/main/java/com/example/xcpro/tasks/TaskRepository.kt:33`) with no pruning when tasks are cleared/replaced/waypoints removed.

27. `P1` Export/share flows have unguarded exception paths.
   - `TaskFilesViewModel` calls use-case import/export/share operations without `runCatching` protection (`feature/map/src/main/java/com/example/xcpro/tasks/TaskFilesViewModel.kt:49`, `feature/map/src/main/java/com/example/xcpro/tasks/TaskFilesViewModel.kt:93`, `feature/map/src/main/java/com/example/xcpro/tasks/TaskFilesViewModel.kt:104`).
   - Use-case export/share path performs direct file writes that can throw (`feature/map/src/main/java/com/example/xcpro/tasks/TaskFilesUseCase.kt:59`, `feature/map/src/main/java/com/example/xcpro/tasks/TaskFilesUseCase.kt:60`, `feature/map/src/main/java/com/example/xcpro/tasks/TaskFilesUseCase.kt:86`, `feature/map/src/main/java/com/example/xcpro/tasks/TaskFilesUseCase.kt:89`).
   - Repository write methods do raw stream/file writes without local failure mapping (`feature/map/src/main/java/com/example/xcpro/tasks/TaskFilesRepository.kt:104`, `feature/map/src/main/java/com/example/xcpro/tasks/TaskFilesRepository.kt:121`, `feature/map/src/main/java/com/example/xcpro/tasks/TaskFilesRepository.kt:163`).

28. `P1` AAT target-point mutations are not autosaved.
   - `AATTaskManager.updateTargetPoint` mutates `_currentAATTask` but does not call `saveAATTask()` (`feature/map/src/main/java/com/example/xcpro/tasks/aat/AATTaskManager.kt:338`, `feature/map/src/main/java/com/example/xcpro/tasks/aat/AATTaskManager.kt:342`).
   - Target updates are triggered from normal UI flows (`feature/map/src/main/java/com/example/xcpro/tasks/TaskSheetViewModel.kt:241`), so restart/recovery can lose edited target points.

29. `P1` Target memory aliases repeated waypoint IDs across distinct legs.
   - Target state is keyed only by waypoint id (`feature/map/src/main/java/com/example/xcpro/tasks/TaskRepository.kt:33`, `feature/map/src/main/java/com/example/xcpro/tasks/TaskRepository.kt:105`, `feature/map/src/main/java/com/example/xcpro/tasks/TaskRepository.kt:212`).
   - Add-waypoint flows preserve source waypoint ids (duplicates allowed) (`feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingWaypointManager.kt:28`, `feature/map/src/main/java/com/example/xcpro/tasks/aat/waypoints/AATWaypointMutationSupport.kt:140`).
   - Editing one repeated waypoint can overwrite target/lock state for another repeated occurrence.

30. `P1` Canonical CUP exporter role encoding mismatches current parsers.
   - `TaskFilesUseCase.taskToCup` encodes `code` as numeric index and role via `style` (`feature/map/src/main/java/com/example/xcpro/tasks/TaskFilesUseCase.kt:115`, `feature/map/src/main/java/com/example/xcpro/tasks/TaskFilesUseCase.kt:120`, `feature/map/src/main/java/com/example/xcpro/tasks/TaskFilesUseCase.kt:130`).
   - Racing/AAT parsers derive role from `code` string (`START`/`FINISH`) and ignore `style` (`feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingTaskStorage.kt:193`, `feature/map/src/main/java/com/example/xcpro/tasks/aat/persistence/AATTaskFileIO.kt:249`).
   - Exported CUP role fidelity will degrade on parser-driven re-import.

31. `P2` Charset determinism is not explicit on task file reads.
   - URI text read path relies on platform-default charset (`feature/map/src/main/java/com/example/xcpro/tasks/TaskFilesRepository.kt:76`).
   - Racing/AAT CUP file reads rely on default charset (`feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingTaskStorage.kt:102`, `feature/map/src/main/java/com/example/xcpro/tasks/aat/persistence/AATTaskFileIO.kt:135`).

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Active task graph (waypoints, roles, custom params) | `TaskManagerCoordinator` + active manager/engine | `TaskCoordinatorSnapshot.task`, `MapTasksUseCase.taskRenderSnapshot()` | Recomputing role/shape defaults from index in downstream layers |
| UI task projection (stats, targets, validation, advance state) | `TaskRepository` | `TaskSheetUseCase.state` | Parallel projections in composables or legacy managers |
| Task file document content | `TaskFilesRepository` | URI-based read/query APIs | Name-only indirect loads that ignore selected URI |
| Named/autosave persistence | `TaskEnginePersistenceService` + adapters | save/load/list by task type | Alternate storage paths not synchronized with engines |
| Full-fidelity share/import payload | `TaskPersistSerializer` (until replaced) | JSON serialize/deserialize APIs | Legacy JSON formats that omit custom params/targets |

### 2.2 Dependency Direction

Confirm dependency flow remains:

`UI -> domain -> data`

- Modules/files touched:
  - `feature/map/.../tasks/*` (files use case/repository/viewmodels/serializer/repository/domain adapters)
  - `feature/map/.../map/ui/task/*` (task panel wiring)
  - `feature/map/src/test/.../tasks/*`
- Any boundary risk:
  - Legacy manager APIs and serializer shortcuts currently blur domain vs data responsibilities.

### 2.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| `.cup` import resolution from files tab | `TaskFilesUseCase` via `taskManager.loadTask(name)` | URI-driven parser + explicit import adapter | Avoid wrong-file import and name collisions | Unit tests with duplicate filenames and selected URI |
| Role/OZ projection for stats | `TaskRepository` index/default-based synthesis | Mapper that reads `TaskWaypoint.role/custom*` first | Keep SSOT fidelity and avoid geometry drift | Envelope/proximity tests with custom roles/OZ |
| JSON import success signaling | `TaskFilesViewModel` optimistic event emission | Post-apply result from import use case | Prevent false success toasts | ViewModel test for invalid JSON path |

### 2.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `feature/map/src/main/java/com/example/xcpro/tasks/TaskFilesUseCase.kt:29` | File import bypasses document URI and loads named persisted task | Parse/import from selected `DocumentRef` content | Phase 1-2 |
| `feature/map/src/main/java/com/example/xcpro/tasks/TaskFilesTab.kt:89` | UI directly applies JSON without local failure path | ViewModel/use-case returns explicit apply result | Phase 3 |
| `feature/map/src/main/java/com/example/xcpro/tasks/TaskQRGenerator.kt:201` | Legacy QR export serializes with empty targets | Route all QR flows through `QrTaskDialogs` stateful path | Phase 3 |
| `feature/map/src/main/java/com/example/xcpro/tasks/TaskSheetViewModel.kt:180` | `Any?` point-type APIs hide type safety at boundaries | Typed sealed/enum command models per task type | Phase 2-3 |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| Export/share filename timestamp | Wall (`Clock.nowWallMs()`) | Human-readable file naming |
| Downloads modified label | Wall (MediaStore metadata) | UI display metadata only |
| AAT analysis timestamp (`calculatedAt`) | Wall via injected clock or remove | Avoid direct `LocalDateTime.now()` in domain-like logic |

Explicitly forbidden comparisons:

- Monotonic vs wall
- Replay vs wall

### 2.4 Threading and Cadence

- Dispatcher ownership (`Main` / `Default` / `IO`):
  - File I/O and parse on `IO`/`Default`; state/event emission on `Main`.
- Primary cadence/gating sensor:
  - Event-driven (file import/share actions), no fixed cadence.
- Hot-path latency budget:
  - Import parse/apply under 200ms for small payloads; no UI thread blocking.

### 2.5 Replay Determinism

- Deterministic for same input: Yes (for task import/state projection)
- Randomness used: No
- Replay/live divergence rules:
  - Import/export logic must be replay-agnostic and pure from same payload.

### 2.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| URI-selected file ignored during import | UDF + boundary adapter discipline | Unit test | `TaskFilesUseCaseImportTest` |
| Serializer drops custom fields/OZ/targets | SSOT fidelity | Unit round-trip tests | `TaskPersistSerializerRoundTripTest` |
| Role/geometry rewritten by index | SSOT + no hidden transformation | Unit tests | `TaskRepositoryProjectionTest` |
| Success toast emitted before apply success | UDF correctness | ViewModel test | `TaskFilesViewModelImportEventsTest` |
| Legacy duplicate QR/UI paths diverge | Maintainability/change safety | enforceRules + review checklist | rule addition + `MapTaskScreenUi` review |
| `Any?` task point type APIs hide invalid states | Type safety across domain boundary | Unit tests + static review | typed command tests |
| Export/share path exceptions crash flows | UDF + error-as-data policy | ViewModel/repository tests | `TaskFilesViewModelFailureResilienceTest` |
| AAT target edits lost after restart | SSOT + persistence correctness | Engine/manager persistence test | `AATTargetAutosavePersistenceTest` |
| Repeated waypoint IDs alias target state | SSOT isolation | repository unit test | `TaskRepositoryDuplicateIdIsolationTest` |
| CUP exporter/parser role mismatch | change safety + import fidelity | CUP contract test | `TaskCupRoleContractTest` |
| Charset-dependent reads | determinism/portability | read/write charset contract tests | `TaskFileCharsetDeterminismTest` |

### 2.7 Strict Compliance Matrix (Deep-Pass Refresh)

| Finding | Rule Reference | Required Action | Gate |
|---|---|---|---|
| Files-tab CUP import bypasses selected URI | ARCHITECTURE 1, 5B; CODING_RULES 8, 9 | Replace name-based load with URI-content parser/import adapter | Unit tests + review |
| CSV parsing/writing is not quote-safe | ARCHITECTURE 14 (change safety) | Add robust CSV quoting/escaping and parser contract tests | Unit tests |
| Main-thread I/O on export/share | ARCHITECTURE 4; CODING_RULES 6, 7 | Move file I/O to suspend + `Dispatchers.IO`; keep VM main-safe | Unit tests + strict review |
| Optimistic JSON import success | ARCHITECTURE 1 (UDF correctness) | Emit success only after apply result and parse validation | ViewModel tests |
| Import drops role/custom fidelity | ARCHITECTURE 5B; SSOT | Preserve persisted role/custom fields end-to-end | Round-trip tests |
| Target lock no-op semantics | ARCHITECTURE intent + domain policy location | Enforce lock behavior in repository/domain update flow | Domain tests |
| Shared cup folder type contamination | ARCHITECTURE 14; maintainability | Partition storage by type or explicit metadata marker | Unit tests |
| Random fallback IDs in persistence adapters | ARCHITECTURE determinism | Use deterministic fallback id policy across adapters | Determinism tests |
| Legacy QR path divergence | CODING_RULES 15A | Remove/retire duplicate path or route through canonical serializer flow | Compile + tests |
| Multi-chooser share event flow | UDF + UX/event correctness | Emit one combined share intent or explicit multi-share UX | ViewModel/UI tests |
| Nested import sync churn | ARCHITECTURE 14 (change safety) | Batch import mutations and run one sync/apply commit | Unit tests |
| Racing/AAT CUP self-parse incompatibility | ARCHITECTURE 14 (change safety) | Align writer/parser row contracts and skip metadata rows safely | Round-trip tests |
| Files export/share drops targets | SSOT fidelity | Serialize real target snapshots from state owner | Round-trip tests |
| Risky file persistence has no direct tests | ARCHITECTURE 12 | Add direct save/load/import/export coverage | Unit/instrumentation tests |
| Task runtime log noise/stack traces | CODING_RULES 13 | Gate/remove debug logs and stack traces in production paths | Review + lint/static checks |
| Coordinator helper ignores API argument | Maintainability/change safety | Fix or remove misleading API contract | Unit tests |
| Active leg resets on target mutation | SSOT/UDF correctness | Preserve active leg through repository target edits | Unit tests |
| Latitude zero-padding parse drift | Change safety + file contract | Standardize writer to fixed DDMM format and round-trip tests | Unit tests |
| Import exception kills event collector | UDF/event reliability | Guard deserialize/apply in VM/use-case and emit failure events | ViewModel/UI tests |
| MediaStore pending leak | Data correctness | Ensure pending cleanup on failures | Repository tests |
| Implicit charset in file writes | Determinism | Force UTF-8 in all task file read/write paths | Unit tests/review |
| Dead duplicate preview/QR path | Maintainability | Remove or fully rewire to canonical path | Compile + tests |
| Unbounded target state memory | Maintainability | Prune `targetStateById` on task lifecycle events | Unit tests |
| Export/share exception paths can crash UI flows | UDF + error handling rules | Guard VM/use-case boundaries and map failures to events | ViewModel/repository tests |
| AAT target edits not persisted | SSOT persistence correctness | Persist target edits on mutation path | Unit tests |
| Repeated waypoint ids alias target memory | SSOT isolation | Key memory by stable task index/instance identity and prune safely | Repository tests |
| CUP role encoding mismatch (code vs style) | File contract safety | Align writer role semantics with parser contract (or parse style) | CUP contract tests |
| Implicit charset on reads | Determinism | Use explicit UTF-8 for reads as well as writes | Unit tests/review |

## 3) Data Flow (Before -> After)

Current (problematic):

```
Downloads URI -> TaskFilesUseCase(import) -> displayName -> TaskManagerCoordinator.loadTask(name)
                                                    |
                                                    -> may load wrong persisted task by name

Task -> TaskPersistSerializer(default OZ/custom omissions) -> QR/JSON
TaskSheetViewModel -> TaskRepository(index/default role/OZ projection) -> UI stats/targets
```

Target:

```
Downloads URI -> TaskFilesRepository(read URI content) -> Import Parser/Mapper
-> TaskSheetCoordinatorUseCase apply -> TaskRepository projection (role/custom-aware) -> UI

Task + targets + custom params -> fidelity serializer -> QR/JSON -> deterministic round-trip
```

## 4) Implementation Phases

- Phase 0 - Baseline + safety net
  - Goal: lock current risky behavior with failing/expected tests before refactor.
  - Files to change: tests only (`feature/map/src/test/.../tasks/*`).
  - Tests to add/update: import routing, serializer fidelity, repository projection.
  - Exit criteria: tests reproduce current gaps and guard regressions.

- Phase 0A - Deep-pass blockers (new)
  - Goal: lock newly discovered blockers before any implementation refactor.
  - Files to change: tests only.
  - Tests to add/update:
    - CSV quote/escape parser tests for racing + AAT CUP.
    - JSON import event-order + invalid payload tests (`FilesBTTab`/`TaskFilesViewModel`).
    - target-lock behavior tests in `TaskRepository`.
    - deterministic ID fallback tests for persistence adapters.
  - Exit criteria: each P0/P1 issue has a failing red test before code fixes begin.

- Phase 1 - Pure logic fixes
  - Goal: implement deterministic URI-driven import and full-fidelity serialization mapping.
  - Files to change:
    - `feature/map/src/main/java/com/example/xcpro/tasks/TaskFilesUseCase.kt`
    - `feature/map/src/main/java/com/example/xcpro/tasks/TaskPersistSerializer.kt`
    - mapper helpers under `tasks/core` as needed.
  - Tests to add/update: serializer round-trip, invalid JSON handling, `.cup` parsing/import mapping.
  - Exit criteria: no lossy round-trip for custom role/radius/point-type/targets.

- Phase 1A - Import correctness and thread safety (new)
  - Goal: make Files-tab import URI-correct and main-thread safe.
  - Files to change:
    - `feature/map/src/main/java/com/example/xcpro/tasks/TaskFilesUseCase.kt`
    - `feature/map/src/main/java/com/example/xcpro/tasks/TaskFilesRepository.kt`
    - `feature/map/src/main/java/com/example/xcpro/tasks/TaskFilesViewModel.kt`
    - `feature/map/src/main/java/com/example/xcpro/tasks/TaskFilesTab.kt`
  - Exit criteria:
    - `.cup` import uses selected document content.
    - no blocking file I/O on Main.
    - no success toast before parse/apply success.

- Phase 2 - Repository / SSOT wiring
  - Goal: make `TaskRepository` projection consume explicit role/custom params, not index/default synthesis.
  - Files to change:
    - `feature/map/src/main/java/com/example/xcpro/tasks/TaskRepository.kt`
    - duplicate mapper sites (`tasks/racing/RacingTaskCoreMappers.kt`, `tasks/data/persistence/TaskPersistenceAdapters.kt`, `tasks/aat/AATTaskCoreMappers.kt`).
  - Tests to add/update: envelope/proximity/validation with non-default roles and OZ settings.
  - Exit criteria: one authoritative mapping behavior shared across runtime + persistence.

- Phase 3 - ViewModel + UI wiring
  - Goal: remove optimistic success paths and unify active QR/task file UI paths.
  - Files to change:
    - `feature/map/src/main/java/com/example/xcpro/tasks/TaskFilesViewModel.kt`
    - `feature/map/src/main/java/com/example/xcpro/tasks/TaskFilesTab.kt`
    - `feature/map/src/main/java/com/example/xcpro/tasks/TaskQRGenerator.kt` (remove/retire)
    - `feature/map/src/main/java/com/example/xcpro/tasks/TaskBottomSheetComponents.kt` (retire or wire)
  - Tests to add/update: event ordering and failure messaging tests.
  - Exit criteria: success messaging only after apply success; no stale parallel QR path.

- Phase 4 - Hardening + cleanup
  - Goal: reduce release-risk noise and dead-path drift.
  - Files to change:
    - logging hot paths (`FinishLineDisplay`, `VariometerWidgetImpl`, plus dead duplicate widget files)
    - add enforceRules coverage for new constraints.
  - Tests to add/update: smoke checks for map/task panel flows; maintain determinism checks.
  - Exit criteria: required checks pass; residual risks documented.

- Phase 5 - Legacy path retirement (new)
  - Goal: remove duplicate QR/file flows that diverge from canonical behavior.
  - Files to change:
    - `feature/map/src/main/java/com/example/xcpro/tasks/TaskQRGenerator.kt` (retire or route to canonical path)
    - related callsites that still reference legacy signatures.
  - Exit criteria:
    - single canonical serializer/import path.
    - no background-thread `startActivity` calls in task share paths.

## 5) Test Plan

- Unit tests:
  - `TaskFilesUseCaseImportTest` for URI-vs-name correctness.
  - `TaskPersistSerializerRoundTripTest` including `customRadius`, `customPointType`, OZ params, targets.
  - `TaskRepositoryProjectionTest` for role/custom-aware stats/targets.
  - `TaskFilesViewModelImportEventsTest` for message ordering and failure paths.
  - `TaskCupCsvContractTest` for quoted/unquoted rows and comma-containing fields.
  - `TaskRepositoryTargetLockTest` to validate lock semantics.
  - `TaskPersistenceDeterministicIdTest` for stable fallback IDs.
  - `RacingTaskStorageCupRoundTripTest` and `AATTaskFileIORoundTripTest` (header row + metadata row + coordinate parse contract).
  - `TaskFilesUseCaseExportShareFidelityTest` (targets preserved in `.xcp.json` export/share payloads).
  - `TaskManagerCoordinatorDistanceContractTest` (parameter usage contract for `calculateTaskDistanceForTask`).
  - `TaskRepositoryActiveLegRetentionTest` (target edit must not reset active leg).
  - `TaskFilesRepositoryPendingCleanupTest` (pending row clear on failure path).
  - `TaskFilesImportErrorHandlingTest` (invalid JSON must emit failure and keep collectors alive).
  - `TaskRepositoryTargetStatePruneTest` (removed waypoints/task clear prune stale target memory).
  - `TaskFilesViewModelFailureResilienceTest` (use-case export/share/import exceptions become user events, not crashes).
  - `AATTargetAutosavePersistenceTest` (target-point edits survive manager persistence restart path).
  - `TaskRepositoryDuplicateIdIsolationTest` (repeated waypoint IDs do not share target/lock state).
  - `TaskCupRoleContractTest` (exported role semantics remain recoverable by parser contract).
  - `TaskFileCharsetDeterminismTest` (UTF-8 read/write contract across URI + file paths).
- Replay/regression tests:
  - Ensure task import payload yields deterministic state across repeated runs.
- UI/instrumentation tests (if needed):
  - Files tab import flow (valid/invalid JSON, `.cup` selection).
- Degraded/failure-mode tests:
  - Corrupt JSON, unreadable URI, duplicate filenames, empty targets.
- Boundary tests for removed bypasses:
  - Verify no path calls named-task load for selected Downloads file import.
  - Verify no duplicate QR serializer path bypasses canonical targets state.

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Optional when relevant:

```bash
./gradlew connectedDebugAndroidTest
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Import behavior change breaks existing user workflows | Medium | Keep legacy path behind temporary feature flag during migration tests | XCPro Team |
| Mapper unification alters persisted compatibility | High | Backward-compat read adapter + golden fixtures | XCPro Team |
| Dead code removal causes hidden callsite breaks | Medium | repo-wide callsite grep + compile/test gates | XCPro Team |
| Increased strictness exposes additional latent issues late | Medium | Phase-gated rollout with baseline tests first | XCPro Team |

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`
- No duplicate SSOT ownership introduced
- Time base handling is explicit in code and tests
- Replay behavior remains deterministic
- `KNOWN_DEVIATIONS.md` updated only if explicitly approved (issue, owner, expiry)
- Risky map/task import/export paths have direct unit coverage

## 7A) Current Gate Status (2026-02-18, after implementation pass #13)

- Required command checks rerun:
  - `./gradlew :feature:map:testDebugUnitTest --tests "com.example.xcpro.tasks.TaskManagerCoordinatorTest"`: PASS
  - `./gradlew :feature:map:testDebugUnitTest --tests "com.example.xcpro.tasks.TaskStoragePartitioningTest" --tests "com.example.xcpro.tasks.TaskStorageFailureModesTest"`: PASS
  - `./gradlew --% :app:connectedDebugAndroidTest --no-parallel -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true -Pandroid.testInstrumentationRunnerArguments.class=com.example.xcpro.TaskFilesShareInstrumentedTest`: PASS
  - `./gradlew connectedDebugAndroidTest --no-parallel`: PASS
  - `./gradlew enforceRules`: PASS
  - `./gradlew testDebugUnitTest`: PASS
  - `./gradlew assembleDebug`: PASS
  - `./gradlew enforceRules testDebugUnitTest assembleDebug`: PASS
- Additional migration/cleanup behavior validated in pass #5 and pass #7:
  - legacy files are moved into scoped directories on first storage interaction,
  - residual legacy conflicts are archived out of shared legacy root after both migrations complete.
- Release readiness for map/task files path: `HIGH` (core hardening + migration + residual cleanup policy + connected multi-share validation complete).
- Test confidence on risky map/task file paths: `HIGH` (includes migration behavior, cleanup policy behavior, failure-mode paths, deterministic IDs, share consolidation, serializer/import fidelity, connected multi-share validation, and post-pass-11 contract/logging cleanup).
- Follow-up backlog after pass #11 deep check: closed in implementation pass #12.

## 8) Rollback Plan

- What can be reverted independently:
  - Serializer fidelity changes.
  - URI import path wiring.
  - UI event-ordering and legacy path cleanup.
- Recovery steps if regression is detected:
  - Re-enable prior import path via temporary flag.
  - Restore previous serializer adapter while keeping new tests for diagnosis.
  - Re-run `enforceRules`, `testDebugUnitTest`, and targeted task import tests.
